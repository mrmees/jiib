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
 * ctor(scope, store), a `MutableStateFlow` exposed as a read-only [StateFlow], an `init { scope.launch
 * { ... } }` collect, and NO second throttle (the store conflates the high-rate plane at 250 ms). Plain
 * Kotlin — NO Compose annotations (ADR-0001) — so it is host-unit-testable.
 *
 * ## ONE screen, both routines (D-02 shared code path)
 * The holder is PARAMETERIZED by which object's `applied` flag it watches via [applied]: pass
 * `{ it.zTiltApplied }` for Z-tilt, `{ it.qglApplied }` for QGL. The screen + holder code is identical;
 * only the selector + dispatch differ — so QGL (built blind, D-02) rides the exact same path.
 *
 * ## Done / failed detection (Pitfall 2 — never a bare gcode ack)
 * The [TiltVm.state] is the canonical [tiltState] state machine folded from:
 *  - a [dispatched] flag (set by [markDispatched] when the screen fires Run);
 *  - the live `applied` flag ([applied] off [PrinterStateStore.printerState]);
 *  - a [failed] flag folded from [works.mees.dinghy.command.CommandDispatcher.events]
 *    [DispatchEvent.Failure] for THIS routine's [dispatchKey].
 *
 * LOAD-BEARING (Pitfall 2): Done is `applied == true`; Failed is ONLY a dispatcher Failure — `applied
 * == false` is BOTH "running" and "post-run-not-converged" and NEVER implies Failed on its own.
 *
 * @param scope       the lifecycle scope the collect runs on (the UI host supplies it).
 * @param store       the already-assembled Phase-2/9 spine; the holder CONSUMES it, never opens a session.
 * @param applied     selects which live flag to watch — `{ it.zTiltApplied }` or `{ it.qglApplied }`.
 * @param events      the live session's dispatcher event stream (Failure → [TiltState.Failed]).
 * @param dispatchKey the dispatch key the Run uses (`z_tilt_adjust` / `quad_gantry_level`) — only THIS
 *                    routine's failures flip the page to Failed.
 */
class TiltHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
    private val applied: (PrinterState) -> Boolean?,
    events: SharedFlow<DispatchEvent>? = null,
    private val dispatchKey: String? = null,
) {
    private val _vm = MutableStateFlow(TiltVm())
    /** The resolved Z-tilt/QGL page view-model (state machine + homed gate + latest error). */
    val vm: StateFlow<TiltVm> = _vm.asStateFlow()

    @Volatile
    private var dispatched: Boolean = false

    @Volatile
    private var failed: Boolean = false

    @Volatile
    private var latestError: String? = null

    init {
        scope.launch {
            store.printerState.collect { state ->
                _vm.value = buildVm(state)
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

    /**
     * Mark the routine as dispatched (the screen calls this when Run fires). A clean re-run resets the
     * stale failure so a successful retry doesn't keep showing the old rejection.
     */
    fun markDispatched() {
        dispatched = true
        failed = false
        latestError = null
        _vm.value = buildVm(store.printerState.value)
    }

    private fun buildVm(state: PrinterState): TiltVm {
        val isApplied = applied(state) == true

        // A clean converge clears any stale failure (so a successful retry isn't masked by an old toast).
        if (isApplied) {
            failed = false
            latestError = null
        }

        // homed gate (D-13) — homed_axes is lowercase (RESEARCH §3); all three present = ready to run.
        val homed = state.homedAxes
        val homedGate = 'x' in homed && 'y' in homed && 'z' in homed

        return TiltVm(
            state = tiltState(dispatched = dispatched, applied = isApplied, failed = failed),
            homedGate = homedGate,
            errorText = latestError,
        )
    }
}

/**
 * The Z-tilt/QGL page view-model:
 *  - [state] the [TiltState] driving the Focus overlay (Idle → `question_exchange`; Done/Running →
 *    `data_table`) and the Run-button enablement.
 *  - [homedGate] true when all of X/Y/Z are homed — `Run` is gated on this (D-13); false → offer the
 *    inline blue Home pre-flight.
 *  - [errorText] the latest dispatcher Failure (the printer's redacted RpcError text), or null.
 */
data class TiltVm(
    val state: TiltState = TiltState.Idle,
    val homedGate: Boolean = false,
    val errorText: String? = null,
)
