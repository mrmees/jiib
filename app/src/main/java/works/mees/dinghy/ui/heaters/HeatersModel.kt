package works.mees.dinghy.ui.heaters

import works.mees.dinghy.config.HeatPreset
import works.mees.dinghy.config.sortedForDisplay
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.state.Capabilities

/** Which heaters a Heaters-list selection is allowed to touch. */
enum class HeatScope { Full, ExtruderOnly }

/** Heater setpoints pulled from the currently loaded Spoolman spool (the dynamic row source). */
data class LoadedSpoolTemps(
    val nozzle: Int?,
    val bed: Int?,
    val colorHex: String?,
    val label: String,
)

/** A ready-to-fire dispatch for one Heaters row. */
sealed interface HeatDispatch {
    data object TurnOffAll : HeatDispatch
    data class ApplyPreset(val setpoints: Map<String, Int>, val key: String) : HeatDispatch
}

/** One row in the Heaters list — display data + its pre-built, capability-filtered dispatch. */
data class HeatersRow(
    val key: String,
    val icon: DinghyIcon,
    val label: String,
    val summary: String?,
    val tintHex: String?,
    val dispatch: HeatDispatch,
)

/**
 * Build [LoadedSpoolTemps] from the active spool, or null when there is no spool / no usable temp.
 * [fallbackLabel] is the localized "Loaded filament" string supplied by the caller (kept out of this
 * pure function so it stays JVM-testable).
 */
fun loadedSpoolTemps(spool: SpoolmanSpool?, fallbackLabel: String): LoadedSpoolTemps? {
    val f = spool?.filament ?: return null
    if (f.settingsExtruderTemp == null && f.settingsBedTemp == null) return null
    return LoadedSpoolTemps(
        nozzle = f.settingsExtruderTemp,
        bed = f.settingsBedTemp,
        colorHex = f.colorSwatches.firstOrNull(),
        label = f.name ?: f.material ?: fallbackLabel,
    )
}

/** Trailing summary for a setpoint map — values sorted by object name, joined by `/` (matches presetSummary). */
fun heatSummary(setpoints: Map<String, Int>): String =
    setpoints.entries.sortedBy { it.key }.joinToString("/") { it.value.toString() }

/**
 * Build the unified Heaters list: OFF pinned top, the loaded-spool row second (when present),
 * then user presets sorted by extruder temp. Every row's dispatch is already capability-filtered
 * and heater-correct for [scope]; rows whose applicable setpoints are empty are omitted (no dead rows).
 *
 * @param extruderObject the heater targeted under [HeatScope.ExtruderOnly] — the ACTIVE tool
 *   (e.g. "extruder1"), not literally "extruder".
 */
fun buildHeatersRows(
    presets: List<HeatPreset>,
    loadedSpool: LoadedSpoolTemps?,
    scope: HeatScope,
    extruderObject: String,
    capabilities: Capabilities,
): List<HeatersRow> {
    val rows = mutableListOf<HeatersRow>()

    // 1. OFF (pinned top, always present).
    rows += HeatersRow(
        key = "heat_off",
        icon = DinghyIcons.HeatersOff,
        label = "OFF",
        summary = null,
        tintHex = null,
        dispatch = when (scope) {
            HeatScope.Full -> HeatDispatch.TurnOffAll
            HeatScope.ExtruderOnly -> HeatDispatch.ApplyPreset(mapOf(extruderObject to 0), "heat_off")
        },
    )

    // 2. Loaded spool (pinned second, when it yields a non-empty applicable map).
    loadedSpool?.let { sp ->
        val map = spoolSetpoints(sp, scope, extruderObject, capabilities)
        if (map.isNotEmpty()) {
            rows += HeatersRow(
                key = "heat_spool",
                icon = DinghyIcons.SpoolFilament,
                label = sp.label,
                summary = heatSummary(map),
                tintHex = sp.colorHex,
                dispatch = HeatDispatch.ApplyPreset(map, "heat_spool"),
            )
        }
    }

    // 3. User presets (sorted), omitting any whose applicable map is empty.
    presets.sortedForDisplay().forEach { preset ->
        val map = presetSetpoints(preset, scope, extruderObject, capabilities)
        if (map.isNotEmpty()) {
            rows += HeatersRow(
                key = "preset_${preset.id}",
                icon = DinghyIcons.LauncherTemperature,
                label = preset.name,
                summary = heatSummary(map),
                tintHex = null,
                dispatch = HeatDispatch.ApplyPreset(map, "preset_${preset.id}"),
            )
        }
    }

    return rows
}

private fun spoolSetpoints(sp: LoadedSpoolTemps, scope: HeatScope, extruderObject: String, caps: Capabilities): Map<String, Int> =
    when (scope) {
        // ExtruderOnly: the active heater is already validated by the screen/VM; skip capability gating.
        HeatScope.ExtruderOnly ->
            if (sp.nozzle != null) mapOf(extruderObject to sp.nozzle) else emptyMap()
        HeatScope.Full -> buildMap {
            if (sp.nozzle != null && caps.hasObject("extruder")) put("extruder", sp.nozzle)
            if (sp.bed != null && caps.hasObject("heater_bed")) put("heater_bed", sp.bed)
        }
    }

private fun presetSetpoints(preset: HeatPreset, scope: HeatScope, extruderObject: String, caps: Capabilities): Map<String, Int> =
    when (scope) {
        // ExtruderOnly: the active heater is already validated by the screen/VM; skip capability gating.
        HeatScope.ExtruderOnly ->
            preset.extruderTemp?.let { mapOf(extruderObject to it) } ?: emptyMap()
        HeatScope.Full -> preset.setpoints.filterKeys { caps.hasObject(it) }
    }
