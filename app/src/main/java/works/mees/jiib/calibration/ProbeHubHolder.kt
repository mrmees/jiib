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
 * Thin toolkit-agnostic holder for the Probe sub-hub. Mirrors [CalibrationHubHolder] exactly:
 * ctor(scope, store, showUnsupportedTools), a [MutableStateFlow] exposed read-only via [tools],
 * and an `init { scope.launch { collect } }`. Plain Kotlin — NO Compose annotations (ADR-0001)
 * — so it is host-unit-testable.
 *
 * Re-derives [probeToolSupport] off [PrinterStateStore.capabilities] (and [showUnsupportedTools])
 * on every change; when capabilities refresh on a reconnect (STATE-02) the hub ordering updates live.
 *
 * @param scope the lifecycle scope the collect runs on (the UI host supplies it).
 * @param store the already-assembled spine; the holder CONSUMES `capabilities`, never opens a session.
 * @param showUnsupportedTools app-level setting flow; when false, unsupported tools are hidden.
 */
class ProbeHubHolder(
    scope: CoroutineScope,
    store: PrinterStateStore,
    showUnsupportedTools: Flow<Boolean>,
) {
    private val _tools = MutableStateFlow(
        probeToolSupport(store.capabilities.value, showUnsupported = false)
    )
    /** Probe tools visible in the hub, supported-first ordered, re-derived on caps or flag change. */
    val tools: StateFlow<List<ProbeToolEntry>> = _tools.asStateFlow()

    init {
        scope.launch {
            combine(store.capabilities, showUnsupportedTools) { caps, show ->
                probeToolSupport(caps, show)
            }.collect { entries ->
                _tools.value = entries
            }
        }
    }
}
