package works.mees.dinghy.ui.heatpresets

import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.config.HeatPreset
import works.mees.dinghy.state.SettableHeater

/** One heater step in the wizard: identity + label + clamp bounds + the prefilled text value. */
data class WizardHeaterField(
    val objectName: String,
    val displayName: String,
    val minTemp: Int,
    val maxTemp: Int,
    val initialValue: String,
)

/**
 * Build the ordered heater fields for the wizard against the CURRENT live [heaters]. On edit, prefill
 * from [saved]'s setpoints (blank for heaters the saved preset omits OR that are new since it was made);
 * heaters no longer in config simply don't appear → the preset is rebuilt fresh against current config.
 */
fun reconcileWizardFields(saved: HeatPreset?, heaters: List<SettableHeater>): List<WizardHeaterField> =
    heaters.map { h ->
        WizardHeaterField(
            objectName = h.objectName,
            displayName = h.displayName,
            minTemp = h.minTemp ?: PrinterCommands.MIN_TEMP_C,
            maxTemp = h.maxTemp ?: PrinterCommands.MAX_TEMP_C,
            initialValue = saved?.setpoints?.get(h.objectName)?.toString() ?: "",
        )
    }

/**
 * Build a [HeatPreset] from wizard input. [rawValues] maps object name → the user's text. A blank/
 * unparseable value OMITS that heater (skip); any parsed integer (including 0 = off) is KEPT, clamped
 * to that heater's [minTemp]..[maxTemp]. [name] is trimmed.
 *
 * Special case: a parsed value of `0` bypasses the minimum clamp — it is an explicit "turn off"
 * command and must not be clamped up to [SettableHeater.minTemp].
 */
fun buildPresetFromInput(
    id: String,
    name: String,
    rawValues: Map<String, String>,
    heaters: List<SettableHeater>,
): HeatPreset {
    val byName = heaters.associateBy { it.objectName }
    val setpoints = buildMap {
        for ((obj, raw) in rawValues) {
            val parsed = raw.trim().toIntOrNull() ?: continue   // blank/garbage → skip (omit heater)
            if (parsed == 0) { put(obj, 0); continue }          // 0 = explicit OFF — bypass the min clamp
            val h = byName[obj]
            val lo = h?.minTemp ?: PrinterCommands.MIN_TEMP_C
            val hi = h?.maxTemp ?: PrinterCommands.MAX_TEMP_C
            put(obj, parsed.coerceIn(lo, hi))
        }
    }
    return HeatPreset(id = id, name = name.trim(), setpoints = setpoints)
}
