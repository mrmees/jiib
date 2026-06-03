package works.mees.dinghy.calibration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

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
 * [works.mees.dinghy.ui.extrude.ExtrudeHolder] / [TiltHolder] template: ctor(scope, store), a
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
 * serves both). The screen dispatches the gated [works.mees.dinghy.command.CommandRegistry] spec.
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

    init {
        scope.launch {
            combine(store.printerState, store.capabilities, _bracket) { state, caps, bracket ->
                buildVm(state, caps, bracket)
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
                        _vm.value = buildVm(store.printerState.value, store.capabilities.value, _bracket.value)
                    }
                }
            }
        }
    }

    private fun buildVm(state: PrinterState, caps: Capabilities, bracket: ZPositionBracket?): ProbeCalibrateVm {
        val probe = state.manualProbe
        val isActive = probe?.isActive == true

        val pageState: ProbePageState = if (isActive) {
            sawActive = true
            // Track the live Z so an Accept (is_active→false) can capture it as the offset.
            lastLiveZ = probe?.zPosition
            ProbePageState.Active
        } else {
            if (sawActive) {
                // The session just ended — capture the last live Z as the offset (idempotent).
                if (capturedOffset == null) capturedOffset = lastLiveZ
                // A finished session has no live bracket; clear it so it doesn't linger.
                if (_bracket.value != null) _bracket.value = null
                ProbePageState.Accepted
            } else {
                ProbePageState.Idle
            }
        }

        return ProbeCalibrateVm(
            state = pageState,
            zPosition = if (isActive) probe?.zPosition else null,
            bracket = if (isActive) bracket else null,
            startCommand = probeCalibrateGate(caps),
            capturedOffset = if (pageState == ProbePageState.Accepted) capturedOffset else null,
            errorText = latestError,
        )
    }
}

/**
 * The Probe-Calibrate page view-model:
 *  - [state] the [ProbePageState] driving the Focus + the state-adaptive gutter / control enablement.
 *  - [zPosition] the LIVE `manual_probe.z_position` hero (only while Active; null otherwise).
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
    val errorText: String? = null,
)
