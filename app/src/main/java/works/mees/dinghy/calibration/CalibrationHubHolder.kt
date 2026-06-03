package works.mees.dinghy.calibration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import works.mees.dinghy.state.PrinterStateStore

/**
 * Thin toolkit-agnostic holder for the Calibration hub (CALIB-01 / D-14). Mirrors the
 * [works.mees.dinghy.ui.extrude.ExtrudeHolder] template: ctor(scope, store), a `MutableStateFlow`
 * exposed read-only, an `init { scope.launch { collect } }`, a pure transform. Plain Kotlin — NO
 * Compose annotations (ADR-0001) — so it is host-unit-testable.
 *
 * It re-derives [calibrationSupport] off [PrinterStateStore.capabilities] on every change, so when
 * capabilities are re-derived on a reconnect (STATE-02) the hub's supported/greyed ordering refreshes
 * live. The list is ALL FIVE routines (UI-SPEC §1 owner override) — supported first (accent), greyed
 * (unsupported) sorted last but STILL tappable — never omitting a routine.
 *
 * @param scope the lifecycle scope the collect runs on (the UI host supplies it).
 * @param store the already-assembled spine; the holder CONSUMES `capabilities`, never opens a session.
 */
class CalibrationHubHolder(
    scope: CoroutineScope,
    store: PrinterStateStore,
) {
    private val _routines = MutableStateFlow(calibrationSupport(store.capabilities.value))
    /** ALL five routines, supported-first ordered, re-derived on every capabilities change. */
    val routines: StateFlow<List<RoutineEntry>> = _routines.asStateFlow()

    init {
        scope.launch {
            store.capabilities.collect { caps ->
                _routines.value = calibrationSupport(caps)
            }
        }
    }
}
