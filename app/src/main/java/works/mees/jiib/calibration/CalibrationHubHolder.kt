package works.mees.jiib.calibration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import works.mees.jiib.state.PrinterStateStore

/**
 * Thin toolkit-agnostic holder for the Calibration hub (CALIB-01 / D-14). Mirrors the
 * [works.mees.jiib.ui.extrude.ExtrudeHolder] template: ctor(scope, store), a `MutableStateFlow`
 * exposed read-only, an `init { scope.launch { collect } }`, a pure transform. Plain Kotlin — NO
 * Compose annotations (ADR-0001) — so it is host-unit-testable.
 *
 * It re-derives [calibrationSupport] off [PrinterStateStore.capabilities] (and the
 * [showUnsupportedTools] app flag) on every change, so when capabilities are re-derived on a
 * reconnect (STATE-02) the hub's supported/hidden ordering refreshes live.
 *
 * When [showUnsupportedTools] emits `false` (the default), only supported routines are shown.
 * When it emits `true`, all five routines render (greyed if unsupported — legacy behaviour).
 *
 * @param scope the lifecycle scope the collect runs on (the UI host supplies it).
 * @param store the already-assembled spine; the holder CONSUMES `capabilities`, never opens a session.
 * @param showUnsupportedTools app-level setting flow; when false, unsupported routines are hidden.
 */
class CalibrationHubHolder(
    scope: CoroutineScope,
    store: PrinterStateStore,
    showUnsupportedTools: Flow<Boolean>,
) {
    private val _routines = MutableStateFlow(
        calibrationSupport(store.capabilities.value, showUnsupported = false)
    )
    /** Routines visible in the hub, supported-first ordered, re-derived on caps or flag change. */
    val routines: StateFlow<List<RoutineEntry>> = _routines.asStateFlow()

    init {
        scope.launch {
            combine(store.capabilities, showUnsupportedTools) { caps, show ->
                calibrationSupport(caps, show)
            }.collect { entries ->
                _routines.value = entries
            }
        }
    }
}
