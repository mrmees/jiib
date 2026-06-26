package works.mees.jiib.state

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Static per-heater temperature limits read ONCE from `configfile.settings.<heater>` at handshake
 * (mirrors the [parseTemperatureStore] backfill discipline — pure, null-safe, host-testable). Either
 * bound may be null when the printer's config omits it; the UI falls back to the global
 * [works.mees.jiib.command.PrinterCommands.MAX_TEMP_C] clamp when [maxTemp] is null.
 */
@Immutable
data class HeaterLimits(
    val minTemp: Double? = null,
    val maxTemp: Double? = null,
)

/**
 * Map a `configfile.settings` [settings] object → per-heater [HeaterLimits] keyed by the FULL heater
 * object name (`extruder`, `extruder1`, `heater_bed`, `heater_generic <name>`) — the same keys the
 * monitored set / `temperature_store` backfill use. House rule (mirrors [parseTemperatureStore]):
 * every walk is null-safe (`as?`/`?.`/`orNull`), NEVER `!!`. A section with NO usable numeric temp
 * field (both min and max absent/garbled) is OMITTED, never fabricated.
 */
fun parseHeaterLimits(settings: JsonObject): Map<String, HeaterLimits> {
    val out = LinkedHashMap<String, HeaterLimits>()
    for ((key, value) in settings) {
        if (!isHeaterSection(key)) continue
        val section = value as? JsonObject ?: continue
        val min = section.numberOrNull("min_temp")
        val max = section.numberOrNull("max_temp")
        if (min == null && max == null) continue // no usable temp field — omit
        out[key] = HeaterLimits(minTemp = min, maxTemp = max)
    }
    return out
}

private fun isHeaterSection(name: String): Boolean =
    name == "heater_bed" ||
        name == "extruder" ||
        (name.startsWith("extruder") && name.removePrefix("extruder").all { it.isDigit() } &&
            name != "extruder") ||
        name.startsWith("heater_generic ")

private fun JsonObject.numberOrNull(key: String): Double? =
    runCatching { this[key]?.jsonPrimitive?.doubleOrNull }.getOrNull()
