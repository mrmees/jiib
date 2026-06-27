package works.mees.jiib.calibration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import works.mees.jiib.state.PrinterStateStore

/**
 * Toolkit-agnostic holder for the Probe-Test page (a PROBE_ACCURACY run), driven by
 * [works.mees.jiib.state.PrinterState.probeLastQuery] and
 * [works.mees.jiib.state.PrinterState.probeLastZ] from the printer state, plus
 * [parseProbeAccuracy] from the raw gcode stream.
 *
 * Mirrors [ProbeCalibrateHolder]: ctor(scope, store), a [MutableStateFlow] exposed read-only as a
 * [StateFlow], `init { scope.launch { combine(...).collect{} } }` for printerState-derived fields,
 * and a SECOND `scope.launch { store.gcodeResponses.collect { ... } }` for the accuracy summary
 * line (same pattern ProbeCalibrateHolder uses for `parseZPosition`).
 *
 * Plain Kotlin — NO Compose annotations (ADR-0001) — so it is host-unit-testable
 * ([ProbeTestHolderTest]).
 *
 * @param scope the lifecycle scope the collects run on (the UI host supplies it).
 * @param store the already-assembled printer state store; the holder CONSUMES it.
 */
class ProbeTestHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
) {
    /** Latest parsed PROBE_ACCURACY summary from the raw gcode stream; null until a run completes. */
    private val _accuracy = MutableStateFlow<ProbeAccuracyResult?>(null)

    private val _vm = MutableStateFlow(ProbeTestVm())

    /** The resolved Probe-Test page view-model. */
    val vm: StateFlow<ProbeTestVm> = _vm.asStateFlow()

    init {
        scope.launch {
            combine(store.printerState, _accuracy) { state, accuracy ->
                ProbeTestVm(
                    triggered = state.probeLastQuery,
                    lastZ = state.probeLastZ,
                    accuracy = accuracy,
                )
            }.collect { _vm.value = it }
        }

        // Collect the un-throttled gcode stream and keep the latest parseable accuracy summary.
        // Progress lines (`probe at … is z=…`) return null from parseProbeAccuracy and are ignored.
        scope.launch {
            store.gcodeResponses.collect { line ->
                parseProbeAccuracy(line)?.let { _accuracy.value = it }
            }
        }
    }
}

/**
 * Probe-Test page view-model:
 *  - [triggered] whether the probe fired on the last query (`probe.last_query`); null = no data yet.
 *  - [lastZ] the last probed Z result from Klipper (`probe.last_z_result`); null = no data yet.
 *  - [accuracy] the latest parsed PROBE_ACCURACY summary from the gcode stream; null = no run yet.
 *
 * `samples` (the count sent to PROBE_ACCURACY) is UI state held by the screen, NOT by this holder.
 */
data class ProbeTestVm(
    val triggered: Boolean? = null,
    val lastZ: Double? = null,
    val accuracy: ProbeAccuracyResult? = null,
)
