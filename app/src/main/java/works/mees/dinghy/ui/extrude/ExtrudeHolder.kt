package works.mees.dinghy.ui.extrude

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import works.mees.dinghy.command.CommandMap
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Toolkit-agnostic holder for the Extrude panel (EXTR-01..04). It transforms the store's
 * ALREADY-throttled [PrinterStateStore.printerState] — COMBINED with the two one-shot handshake
 * StateFlows landed by 05-03 ([PrinterStateStore.minExtrudeTemp] /
 * [PrinterStateStore.maxExtrudeDistance]) — into one consumable [ExtrudeVm] for the Compose
 * `ExtrudeScreen`.
 *
 * ## The live cold-extrude safety gate (EXTR-04 / D-07 / T-05-07-Safety)
 * [ExtrudeVm.canExtrude] is the PER-TOOL live `extruder.can_extrude` boolean of the currently
 * [activeTool], read off `state.heaters[activeTool].canExtrude`. It defaults FAIL-SAFE false: a
 * missing extruder, or an extruder that never reported the flag, reads as "cannot extrude" so the
 * screen disables Extrude/Retract rather than risk a cold extrude. This per-tool LIVE boolean is the
 * authoritative safety check — NOT the static [minExtrudeTemp] number (which is only hint text).
 *
 * ## Deterministic one-shot reads (review determinism fix, 05-03)
 * [minExtrudeTemp] (the real hint number, e.g. "Heat nozzle to 170°C to extrude") and
 * [maxExtrudeDistance] (the distance-button ceiling) are COMBINED in — not read as a snapshot — so the
 * vm updates the instant those one-shot handshake reads land, never contingent on a later
 * `notify_status_update` diff. Both are PRIMARY-extruder values (single-extruder-accurate); the per-tool
 * gate above is the real safety check. Null = unknown → the UI shows the generic hint / keeps the 100 mm
 * default ceiling.
 *
 * ## No second throttle (mirrors MoveHolder / PrintStatusHolder)
 * The holder consumes the store's conflated flow directly (the store samples the high-rate plane at
 * `DEFAULT_SAMPLE_MS = 250`). It adds NO `sample`/`debounce`/`delay` of its own.
 *
 * Plain Kotlin (no Compose annotations) so it is host-unit-testable.
 *
 * @param scope the lifecycle scope the combine collect runs on (the UI host supplies it).
 * @param store the already-assembled Phase-2/5 spine; the holder CONSUMES it, never opens a session.
 */
class ExtrudeHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
) {
    /**
     * The active tool's heater object name (the per-tool gate reads its `can_extrude`). Default
     * "extruder" (the primary); switched by [setActiveTool] when the multi-tool selector is used.
     */
    @Volatile
    private var activeTool: String = "extruder"

    private val _vm = MutableStateFlow(ExtrudeVm())
    /** The resolved Extrude panel view-model (live cold-extrude gate + tools + macro presence + hints). */
    val vm: StateFlow<ExtrudeVm> = _vm.asStateFlow()

    init {
        // COMBINE the throttled state with the two one-shot StateFlows so the hint/limit are
        // deterministic the instant those reads land (NOT dependent on a later status diff).
        scope.launch {
            combine(
                store.printerState,
                store.minExtrudeTemp,
                store.maxExtrudeDistance,
            ) { state, minTemp, maxDist ->
                buildVm(state, store.capabilities.value, minTemp, maxDist)
            }.collect { _vm.value = it }
        }
    }

    /** Switch the active tool the per-tool can_extrude gate reads (the screen calls this on tool select). */
    fun setActiveTool(toolHeaterName: String) {
        activeTool = toolHeaterName
        // Re-resolve immediately off the latest known state so the gate reflects the new tool at once.
        _vm.value = buildVm(
            store.printerState.value,
            store.capabilities.value,
            store.minExtrudeTemp.value,
            store.maxExtrudeDistance.value,
        )
    }

    private fun buildVm(
        state: PrinterState,
        caps: Capabilities,
        minTemp: Float?,
        maxDist: Float?,
    ): ExtrudeVm {
        // The active extruder's live readings (per-tool). Absent extruder → fail-safe defaults.
        val heater = state.heaters[activeTool]
        val canExtrude = heater?.canExtrude ?: false

        // Tool list / selector visibility from the derived extruder count (D-09).
        val tools = (0 until caps.extruderCount).map { "T$it" }
        val showToolSelector = caps.extruderCount > 1

        return ExtrudeVm(
            canExtrude = canExtrude,
            minExtrudeTemp = minTemp,
            maxExtrudeDistance = maxDist,
            tools = tools,
            showToolSelector = showToolSelector,
            // Case-insensitive macro presence — Moonraker lowercases macro names (Pitfall 2 / D-10).
            // Macro names come from CommandMap (R4) — the single fork-and-edit customization point.
            hasLoadMacro = caps.hasMacroIgnoreCase(CommandMap.loadFilament.macro),
            hasUnloadMacro = caps.hasMacroIgnoreCase(CommandMap.unloadFilament.macro),
            // Live nozzle temp/target for the in-field temp button; the active heater the temp set targets.
            nozzleTemp = heater?.temperature ?: 0.0,
            nozzleTarget = heater?.target ?: 0.0,
            activeHeater = activeTool,
        )
    }
}

/**
 * The Extrude panel view-model:
 *  - [canExtrude] the LIVE per-tool cold-extrude safety gate (fail-safe false — EXTR-04/D-07).
 *  - [minExtrudeTemp] the real min-extrude-temp for the disabled-reason hint (null = unknown → generic).
 *  - [maxExtrudeDistance] the `max_extrude_only_distance` ceiling (null = unknown → 100 mm default).
 *  - [tools] / [showToolSelector] the multi-extruder T0/T1… selector gating (D-09; selector hidden on 1).
 *  - [hasLoadMacro] / [hasUnloadMacro] case-insensitive LOAD/UNLOAD_FILAMENT presence (D-10) — drives
 *    dispatch-vs-informational-popup, NOT button visibility (load/unload always show).
 */
data class ExtrudeVm(
    val canExtrude: Boolean = false,
    val minExtrudeTemp: Float? = null,
    val maxExtrudeDistance: Float? = null,
    val tools: List<String> = emptyList(),
    val showToolSelector: Boolean = false,
    val hasLoadMacro: Boolean = false,
    val hasUnloadMacro: Boolean = false,
    /** Live current temperature of the [activeHeater] (°C) — the in-field nozzle-temp button readout. */
    val nozzleTemp: Double = 0.0,
    /** Live target temperature of the [activeHeater] (°C) — seeds the temp numpad's initial value. */
    val nozzleTarget: Double = 0.0,
    /** The active extruder's heater object name (`extruder`, `extruder1`, …) the temp set targets. */
    val activeHeater: String = "extruder",
)
