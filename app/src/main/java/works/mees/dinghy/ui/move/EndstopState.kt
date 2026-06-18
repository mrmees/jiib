package works.mees.dinghy.ui.move

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One endstop's resolved state. [name] is the raw Moonraker key (e.g. "x", "probe");
 *  [triggered] is true when the reported value is "TRIGGERED" (case-insensitive). */
data class EndstopStatus(val name: String, val triggered: Boolean)

private val CANONICAL_ORDER = listOf("x", "y", "z")

/**
 * Parse a `printer.query_endstops/status` result object into ordered [EndstopStatus] rows.
 * Order: x, y, z first (when present), then any remaining keys alphabetically (so `probe` and
 * exotic endstops render in a stable position). A non-object element returns an empty list.
 */
fun parseEndstops(result: JsonElement): List<EndstopStatus> {
    val obj = result as? JsonObject ?: return emptyList()
    val ordered = obj.keys.sortedWith(
        compareBy(
            { CANONICAL_ORDER.indexOf(it).let { i -> if (i == -1) Int.MAX_VALUE else i } },
            { it },
        ),
    )
    return ordered.map { key ->
        val value = (obj[key] as? JsonPrimitive)?.content ?: ""
        EndstopStatus(name = key, triggered = value.equals("TRIGGERED", ignoreCase = true))
    }
}

/** Human label for an endstop key: X/Y/Z upper-cased, others capitalized ("probe" -> "Probe"). */
fun endstopLabel(name: String): String =
    if (name in CANONICAL_ORDER) name.uppercase()
    else name.replaceFirstChar { it.uppercase() }
