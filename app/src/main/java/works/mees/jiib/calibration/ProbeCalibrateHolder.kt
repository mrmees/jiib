package works.mees.jiib.calibration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import works.mees.jiib.command.DispatchEvent
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore

/** The ONE interactive calibration page's lifecycle (CALIB-05 / D-01), driven by `manual_probe.is_active`. */
enum class ProbePageState {
    /** No live session — show the "Start, then lower the nozzle" idle Focus. */
    Idle,

    /** A live manual-probe session — the jog pad + Accept/Abort are enabled; Back is suppressed. */
    Active,

    /** The session ended via Accept — the captured offset is held; offer the amber Save (SAVE_CONFIG). */
    Accepted,
}

/** The keys whose dispatcher [DispatchEvent.Failure] events belong to THIS page (fold into errorText). */
private val PROBE_PAGE_KEYS = setOf("probe_calibrate", "z_endstop_calibrate", "testz", "accept", "abort", "save_config")

/**
 * Toolkit-agnostic holder for the Probe-Calibrate page (CALIB-05 / D-01 — the ONE interactive/stateful
 * calibration page: a manual-probe Z-calibrate session). Mirrors the
 * [works.mees.jiib.ui.extrude.ExtrudeHolder] / [TiltHolder] template: ctor(scope, store), a
 * `MutableStateFlow` exposed as a read-only [StateFlow], `init { scope.launch { … } }` collects, and NO
 * second throttle (the store conflates the high-rate plane at 250 ms). Plain Kotlin — NO Compose
 * annotations (ADR-0001) — so it is host-unit-testable ([ProbeCalibrateHolderTest]).
 *
 * ## Page state off `manual_probe.is_active` (Pattern 3)
 * [ProbeCalibrateVm.state] is derived from the live `manual_probe` object:
 *  - never opened (is_active false, no prior Active) → [ProbePageState.Idle];
 *  - is_active true → [ProbePageState.Active] (the live Z-jog session);
 *  - is_active flips back to false AFTER an Active session → [ProbePageState.Accepted] (the user
 *    Accepted; the last live `z_position` is captured as the offset to persist with SAVE_CONFIG).
 *
 * ## The Z-position bracket comes off the raw gcode stream, NOT printerState (Phase-8 D-04)
 * The `// Z position: <lower> --> <current> <-- <upper>` console line (where the bounds can be the
 * literal `??????` → null, the 09-01 real-shape contract) is NOT a `manual_probe` field — it arrives
 * only on the un-throttled [PrinterStateStore.gcodeResponses] SharedFlow (the same raw stream the
 * Console taps). The holder collects that stream, runs each line through [parseZPosition], and keeps the
 * latest parseable bracket in a `MutableStateFlow` that `combine`s into the vm. The bracket clears when
 * the session ends so a stale bracket never lingers on the Idle/Accepted page.
 *
 * ## Start command via the probe-present gate (A3)
 * [ProbeCalibrateVm.startCommand] resolves through [probeCalibrateGate]: a printer with a `probe` object
 * uses `PROBE_CALIBRATE`; a probe-less printer falls back to `Z_ENDSTOP_CALIBRATE` (so the SAME page
 * serves both). The screen dispatches the gated [works.mees.jiib.command.CommandRegistry] spec.
 *
 * @param scope  the lifecycle scope the collects run on (the UI host supplies it).
 * @param store  the already-assembled Phase-2/9 spine; the holder CONSUMES it, never opens a session.
 * @param events the live session's dispatcher event stream (Failure → [ProbeCalibrateVm.errorText]).
 */
class ProbeCalibrateHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
    events: SharedFlow<DispatchEvent>? = null,
) {
    private val _vm = MutableStateFlow(ProbeCalibrateVm())
    /** The resolved Probe-Calibrate page view-model (Idle/Active/Accepted + live Z + bracket + start cmd). */
    val vm: StateFlow<ProbeCalibrateVm> = _vm.asStateFlow()

    /** The latest parseable `// Z position:` bracket off the raw gcode stream (null = none / stale-cleared). */
    private val _bracket = MutableStateFlow<ZPositionBracket?>(null)

    /** True once an Active session has been seen — distinguishes "never opened" (Idle) from "ended" (Accepted). */
    @Volatile
    private var sawActive: Boolean = false

    /** The last live `z_position` while Active — captured as the offset when the session Accepts. */
    @Volatile
    private var lastLiveZ: Double? = null

    @Volatile
    private var capturedOffset: Double? = null

    @Volatile
    private var latestError: String? = null

    /** Set by [markAborted] on the Abort tap — the NEXT is_active→false transition returns to Idle (no
     *  captured offset / no Save) instead of Accepted. ABORT and ACCEPT both flip is_active false, so the
     *  user's intent is the only thing that distinguishes them. */
    @Volatile
    private var aborting: Boolean = false

    init {
        scope.launch {
            combine(store.printerState, store.capabilities, _bracket, store.probeZOffset) {
                state, caps, bracket, savedZ ->
                buildVm(state, caps, bracket, savedZ)
            }.collect { _vm.value = it }
        }

        // The Z-position bracket is NOT a printerState field — collect the un-throttled gcode stream
        // and keep the latest parseable bracket. parseZPosition maps ?????? bounds → null (09-01).
        scope.launch {
            store.gcodeResponses.collect { line ->
                parseZPosition(line)?.let { _bracket.value = it }
            }
        }

        // Fold dispatcher Failure events for THIS page's keys into the redacted error text (T-09-06-05).
        if (events != null) {
            scope.launch {
                events.collect { event ->
                    if (event is DispatchEvent.Failure && event.key in PROBE_PAGE_KEYS) {
                        latestError = event.message
                        _vm.value = buildVm(
                            store.printerState.value,
                            store.capabilities.value,
                            _bracket.value,
                            store.probeZOffset.value,
                        )
                    }
                }
            }
        }
    }

    /**
     * Fresh-instance reset (mirrors [works.mees.jiib.calibration.TiltHolder.reset] — the calibration
     * screens' `onEnter` seam). This holder is per-session and long-lived, so its `sawActive` /
     * `capturedOffset` latches SURVIVE leaving and re-entering the page. Without clearing them, a
     * returning user sees the PREVIOUS round's captured offset rendered as a stale "Accepted" page
     * instead of a clean Idle (the same fresh-instance issue fixed on Screws-Tilt). Clears the session
     * latches + folded error + bracket and re-resolves the vm against the live state — a genuinely-active
     * session re-derives Active on the next combine tick (sawActive is re-set in [buildVm]).
     */
    /**
     * Flag that the live session is being ABORTED (the user tapped Abort). The next is_active→false
     * transition returns to a fresh Idle (Start/Back) rather than Accepted — an aborted run must NOT offer
     * Save of a discarded measurement. No vm rebuild here: is_active is still true at the tap, so the
     * transition is resolved in [buildVm] when the printer reports the session closed.
     */
    fun markAborted() {
        aborting = true
    }

    fun reset() {
        sawActive = false
        lastLiveZ = null
        capturedOffset = null
        latestError = null
        aborting = false
        _bracket.value = null
        _vm.value = buildVm(
            store.printerState.value,
            store.capabilities.value,
            _bracket.value,
            store.probeZOffset.value,
        )
    }

    private fun buildVm(
        state: PrinterState,
        caps: Capabilities,
        bracket: ZPositionBracket?,
        savedZOffset: Float?,
    ): ProbeCalibrateVm {
        val probe = state.manualProbe
        val isActive = probe?.isActive == true

        val pageState: ProbePageState = if (isActive) {
            sawActive = true
            // Track the live Z from the MACRO FEEDBACK (`// Z position:` bracket current), NOT
            // `manual_probe.z_position` — the status field diverges from what the console reports (e.g.
            // status 0.001 while the console shows 4.8). Retain the prior value if no bracket has parsed
            // yet so an Accept always captures the last reported feedback Z.
            lastLiveZ = bracket?.current ?: lastLiveZ
            ProbePageState.Active
        } else {
            if (sawActive && aborting) {
                // The user ABORTED — return to a fresh Idle (Start/Back), discard the measurement so the
                // gutter never offers Save of an aborted run. Clears the session latches like reset().
                sawActive = false
                aborting = false
                lastLiveZ = null
                capturedOffset = null
                if (_bracket.value != null) _bracket.value = null
                ProbePageState.Idle
            } else if (sawActive) {
                // Accepted — capture the last live Z as the offset (idempotent).
                if (capturedOffset == null) capturedOffset = lastLiveZ
                // A finished session has no live bracket; clear it so it doesn't linger.
                if (_bracket.value != null) _bracket.value = null
                ProbePageState.Accepted
            } else {
                ProbePageState.Idle
            }
        }

        // Homed gate (D-13) — homed_axes is lowercase; all three present = safe to start the routine.
        // PROBE_CALIBRATE (the klicky macro) raises "Must Home X, Y and Z Axis First!" otherwise.
        val homed = state.homedAxes
        val homedGate = 'x' in homed && 'y' in homed && 'z' in homed

        return ProbeCalibrateVm(
            state = pageState,
            // The live hero is the macro-feedback current Z (the `// Z position:` bracket), NOT the
            // divergent `manual_probe.z_position` status field.
            zPosition = if (isActive) bracket?.current else null,
            bracket = if (isActive) bracket else null,
            startCommand = probeCalibrateGate(caps),
            capturedOffset = if (pageState == ProbePageState.Accepted) capturedOffset else null,
            savedZOffset = savedZOffset?.toDouble(),
            homedGate = homedGate,
            errorText = latestError,
        )
    }
}

/**
 * The Probe-Calibrate page view-model:
 *  - [state] the [ProbePageState] driving the Focus + the state-adaptive gutter / control enablement.
 *  - [zPosition] the LIVE hero = the macro-feedback `// Z position:` bracket current (only while Active;
 *    null otherwise). NOT `manual_probe.z_position` — that status field diverges from the console value.
 *  - [bracket] the latest parsed `// Z position:` bracket off the gcode stream (null bounds tolerated).
 *  - [startCommand] the gated Z-calibrate command name ([probeCalibrateGate]: PROBE_CALIBRATE vs
 *    Z_ENDSTOP_CALIBRATE) — so a probe-less printer gets the right Start.
 *  - [capturedOffset] the Z offset captured when the session Accepted (only in [ProbePageState.Accepted]).
 *  - [errorText] the latest dispatcher Failure (the printer's redacted RpcError text), or null.
 */
data class ProbeCalibrateVm(
    val state: ProbePageState = ProbePageState.Idle,
    val zPosition: Double? = null,
    val bracket: ZPositionBracket? = null,
    val startCommand: String = "PROBE_CALIBRATE",
    val capturedOffset: Double? = null,
    /** The saved probe `z_offset` from config — shown as the idle "current Z offset"; null if probe-less. */
    val savedZOffset: Double? = null,
    /** All of X/Y/Z homed — Start is gated on this (D-13); false → offer the inline Home All pre-flight. */
    val homedGate: Boolean = false,
    val errorText: String? = null,
)
