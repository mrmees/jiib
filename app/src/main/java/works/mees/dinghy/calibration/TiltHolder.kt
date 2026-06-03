package works.mees.dinghy.calibration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Toolkit-agnostic holder for the Z-tilt / QGL page (CALIB-03 / D-02 — ONE shared automatic-flow code
 * path). Mirrors the [works.mees.dinghy.ui.extrude.ExtrudeHolder] / [ScrewsTiltHolder] template:
 * ctor(scope, store), a `MutableStateFlow` exposed as a read-only [StateFlow], `init { scope.launch
 * { ... } }` collects, and NO second throttle. Plain Kotlin — NO Compose annotations (ADR-0001) — so it
 * is host-unit-testable.
 *
 * ## No persisted "applied" (owner decision, on-device UAT)
 * The printer has no reliable persistent "applied" flag to read: `z_tilt.applied` /
 * `quad_gantry_level.applied` stays true for the rest of the Klipper session after one run, so a
 * freshly-loaded page would show "Done" before the user ran anything. So this holder is LOAD-scoped:
 * [reset] (called on every page entry) returns it to Idle; [markDispatched] (Run) flips [TiltVm.ran];
 * the actual result is the per-stepper adjustments parsed from the un-throttled gcode stream (the
 * "Making the following Z adjustments:" block). The SCREEN derives Running vs Done from [TiltVm.ran]
 * plus the dispatcher in-flight set (the screen owns the dispatcher), and folds a dispatcher Failure
 * into Failed.
 *
 * ## ONE screen, both routines (D-02 shared code path)
 * Z-tilt and QGL ride the identical holder/screen; only the dispatched command + [dispatchKey] differ.
 * Both emit the same "Making the following Z adjustments:" block, so the same parser serves both.
 *
 * @param scope       the lifecycle scope the collects run on (the UI host supplies it).
 * @param store       the already-assembled Phase-2/9 spine; CONSUMED, never opens a session.
 * @param events      the live session's dispatcher event stream (Failure → [TiltVm.failed]/[TiltVm.errorText]).
 * @param dispatchKey the run's dispatch key (`z_tilt_adjust` / `quad_gantry_level`) — only THIS
 *                    routine's failures flip the page to Failed.
 */
class TiltHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
    events: SharedFlow<DispatchEvent>? = null,
    private val dispatchKey: String? = null,
) {
    private val _vm = MutableStateFlow(TiltVm())
    /** The resolved Z-tilt/QGL page view-model (load-scoped run facts + homed gate + adjustments). */
    val vm: StateFlow<TiltVm> = _vm.asStateFlow()

    @Volatile private var ran: Boolean = false
    @Volatile private var failed: Boolean = false
    @Volatile private var latestError: String? = null
    @Volatile private var collecting: Boolean = false
    @Volatile private var adjustments: List<ZAdjustment> = emptyList()

    init {
        scope.launch {
            store.printerState.collect { _vm.value = buildVm(it) }
        }

        // Parse the per-stepper adjustments off the un-throttled gcode stream (the same raw stream the
        // Console taps) — the run's REAL result. Only while a run is active this load (ran). A fresh
        // "Making the following Z adjustments:" header resets the block, so we always reflect the LAST
        // iteration's adjustments; any non-adjustment line ends the block.
        scope.launch {
            store.gcodeResponses.collect { message ->
                if (!ran) return@collect
                // The whole block arrives as ONE gcode_response with embedded newlines (real E5 shape:
                // "// Making the following Z adjustments:\n// stepper_z = ...\n// stepper_z1 = ..."), so
                // split on newlines and feed each sub-line to the per-line parser. This also covers the
                // line-at-a-time delivery some setups use.
                for (sub in message.split('\n')) {
                    when {
                        isZAdjustHeader(sub) -> {
                            collecting = true
                            adjustments = emptyList()
                        }
                        collecting -> {
                            val adj = parseZAdjustment(sub)
                            if (adj != null) adjustments = adjustments + adj else collecting = false
                        }
                    }
                }
                _vm.value = buildVm(store.printerState.value)
            }
        }

        // Fold dispatcher Failure events for THIS routine's key into Failed + the redacted error text.
        if (events != null) {
            scope.launch {
                events.collect { event ->
                    if (event is DispatchEvent.Failure && (dispatchKey == null || event.key == dispatchKey)) {
                        failed = true
                        latestError = event.message
                        _vm.value = buildVm(store.printerState.value)
                    }
                }
            }
        }
    }

    /** Run fired this load: a fresh run, clearing any prior result/failure. */
    fun markDispatched() {
        ran = true
        failed = false
        latestError = null
        collecting = false
        adjustments = emptyList()
        _vm.value = buildVm(store.printerState.value)
    }

    /** Reset to Idle — called on every page entry so a prior run never persists across loads. */
    fun reset() {
        ran = false
        failed = false
        latestError = null
        collecting = false
        adjustments = emptyList()
        _vm.value = buildVm(store.printerState.value)
    }

    private fun buildVm(state: PrinterState): TiltVm {
        // homed gate (D-13) — homed_axes is lowercase (RESEARCH §3); all three present = ready to run.
        val homed = state.homedAxes
        val homedGate = 'x' in homed && 'y' in homed && 'z' in homed
        return TiltVm(
            ran = ran,
            adjustments = adjustments,
            homedGate = homedGate,
            failed = failed,
            errorText = latestError,
        )
    }
}

/**
 * The Z-tilt/QGL page view-model (LOAD-scoped):
 *  - [ran] Run was dispatched on this load (reset on entry). Idle until set; then the screen reads the
 *    dispatcher in-flight set to tell Running from Done.
 *  - [adjustments] the per-stepper Z adjustments parsed from the run's console output (may be empty).
 *  - [homedGate] all of X/Y/Z homed — Run is gated on this (D-13); false → the gutter offers Home All.
 *  - [failed] / [errorText] a dispatcher Failure for this routine (the printer's redacted RpcError text).
 */
data class TiltVm(
    val ran: Boolean = false,
    val adjustments: List<ZAdjustment> = emptyList(),
    val homedGate: Boolean = false,
    val failed: Boolean = false,
    val errorText: String? = null,
)
