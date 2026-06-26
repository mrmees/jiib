package works.mees.jiib.state

import androidx.compose.runtime.Immutable

/** A heater the user can set a target on, with a friendly label and (optional) config limits. */
@Immutable
data class SettableHeater(
    val objectName: String,
    val displayName: String,
    val minTemp: Int?,
    val maxTemp: Int?,
)

/**
 * Friendly label for a Moonraker heater object name (matches TemperatureHolder.label):
 * `extruder`→"Nozzle", `extruder1`→"Nozzle 1", `heater_bed`→"Bed",
 * `heater_generic <n>`/`temperature_fan <n>`→Title-Cased bare name; else Title-Cased.
 */
fun heaterDisplayName(objectName: String): String = when {
    objectName == "extruder" -> "Nozzle"
    objectName.startsWith("extruder") && objectName.removePrefix("extruder").all { it.isDigit() } ->
        "Nozzle ${objectName.removePrefix("extruder")}"
    objectName == "heater_bed" -> "Bed"
    objectName.startsWith("heater_generic ") -> titleCase(objectName.removePrefix("heater_generic "))
    objectName.startsWith("temperature_fan ") -> titleCase(objectName.removePrefix("temperature_fan "))
    else -> titleCase(objectName)
}

/**
 * Ordered settable heaters for the Heat Preset wizard: extruder(s) → bed → heater_generic (alpha) →
 * temperature_fan (alpha). Heaters + their limits come from [heaterLimits] (parsed configfile); fans
 * come from [temperatureFans] with null limits (their config limits are not parsed in v1 → the global
 * 0..350 clamp applies at send time).
 */
fun enumerateSettableHeaters(
    heaterLimits: Map<String, HeaterLimits>,
    temperatureFans: List<String>,
): List<SettableHeater> {
    fun rank(name: String): Int = when {
        name == "extruder" || (name.startsWith("extruder") && name.removePrefix("extruder").all { it.isDigit() }) -> 0
        name == "heater_bed" -> 1
        name.startsWith("heater_generic ") -> 2
        else -> 3
    }
    val heaters = heaterLimits.keys
        .sortedWith(compareBy({ rank(it) }, { it }))
        .map { name ->
            val lim = heaterLimits[name]
            SettableHeater(name, heaterDisplayName(name), lim?.minTemp?.toInt(), lim?.maxTemp?.toInt())
        }
    val fans = temperatureFans.sorted()
        .map { SettableHeater(it, heaterDisplayName(it), minTemp = null, maxTemp = null) }
    val all = heaters + fans
    // Fallback for a never-connected printer (no config reported yet): the design requires the wizard
    // still offer the two universal heaters so a new printer can build a real preset, not name-only.
    return all.ifEmpty {
        listOf(
            SettableHeater("extruder", heaterDisplayName("extruder"), null, null),
            SettableHeater("heater_bed", heaterDisplayName("heater_bed"), null, null),
        )
    }
}

private fun titleCase(raw: String): String =
    raw.lowercase().split(Regex("[\\s_]+")).filter { it.isNotEmpty() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
