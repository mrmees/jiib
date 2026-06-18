package works.mees.dinghy.ui.move

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One endstop's resolved state. [name] is the raw Moonraker key — in practice the stepper that
 *  owns the endstop, e.g. "stepper_x", "stepper_z", "stepper_z1" (verified live 2026-06-18);
 *  [triggered] is true when the reported value is "TRIGGERED" (case-insensitive). */
data class EndstopStatus(val name: String, val triggered: Boolean)

private val CANONICAL_ORDER = listOf("x", "y", "z")

/** Strip Klipper's `stepper_` prefix so ordering/labels key off the bare axis ("stepper_x" -> "x",
 *  "stepper_z1" -> "z1"); keys without the prefix (e.g. "probe") pass through unchanged. */
private fun axisCore(key: String): String = key.removePrefix("stepper_")

/**
 * Parse a `printer.query_endstops.status` result object into ordered [EndstopStatus] rows.
 * Moonraker keys each endstop by its owning stepper (`stepper_x`, `stepper_z`, `stepper_z1`, …).
 * Order: x, y, z first by the bare axis letter (so `stepper_x` sorts ahead of `stepper_z`), then
 * any remaining keys alphabetically (so `probe`/exotic endstops render in a stable position).
 * A non-object element returns an empty list.
 */
fun parseEndstops(result: JsonElement): List<EndstopStatus> {
    val obj = result as? JsonObject ?: return emptyList()
    val ordered = obj.keys.sortedWith(
        compareBy(
            { CANONICAL_ORDER.indexOf(axisCore(it).take(1)).let { i -> if (i == -1) Int.MAX_VALUE else i } },
            { it },
        ),
    )
    return ordered.map { key ->
        val value = (obj[key] as? JsonPrimitive)?.content ?: ""
        EndstopStatus(name = key, triggered = value.equals("TRIGGERED", ignoreCase = true))
    }
}

/** Human label for an endstop key: the `stepper_` prefix is dropped, then a short axis token is
 *  upper-cased ("stepper_x" -> "X", "stepper_z1" -> "Z1") and a longer word is capitalized
 *  ("probe" -> "Probe"). */
fun endstopLabel(name: String): String {
    val core = axisCore(name)
    return when {
        core.isEmpty() -> name
        core.length <= 2 -> core.uppercase()
        else -> core.replaceFirstChar { it.uppercase() }
    }
}
