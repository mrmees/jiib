package works.mees.jiib.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Raw per-macro config carrier extracted from `configfile.settings` (the single one-shot configfile
 * query — Pitfall 3, no extra request). [gcode] is the macro body (used for heuristic param parsing);
 * [description] is the Klipper `description:` docstring shown in the Macros Focus, or null.
 */
data class MacroConfigEntry(
    val gcode: String,
    val description: String?,
)

/**
 * PURE: `configfile.settings` (+ optional raw `configfile.config`) JsonObject → lowercased-macro-name →
 * [MacroConfigEntry]. Total — a missing or garbage field is skipped, never thrown (the house "a bad
 * field is skipped, never fatal" rule). A `gcode_macro` section with no usable `gcode` body is skipped
 * entirely (matches the prior inline behaviour, so the existing macroBodies map / HandshakeTest are
 * unaffected). Moonraker lowercases settings keys, so the section name is already lowercase; the
 * returned key is that lowercased name.
 *
 * Description priority (spec): `settings[section].description` → `config[section].description` → null.
 * `config` preserves the RAW (un-lowercased) section name, so the fallback match is case-insensitive.
 */
internal fun extractMacroConfigs(settings: JsonObject, config: JsonObject? = null): Map<String, MacroConfigEntry> =
    settings.entries.mapNotNull { (key, value) ->
        if (!key.startsWith("gcode_macro ")) return@mapNotNull null
        val obj = value as? JsonObject ?: return@mapNotNull null
        val gcode = obj.gcodeBody() ?: return@mapNotNull null
        val name = key.removePrefix("gcode_macro ").lowercase()
        val description = obj.descriptionOrNull() ?: config?.let { configDescriptionFor(it, name) }
        name to MacroConfigEntry(gcode = gcode, description = description)
    }.toMap()

/** A macro section's `gcode` body — normally a newline-joined string; tolerate an array by joining. */
private fun JsonObject.gcodeBody(): String? = runCatching {
    when (val g = this["gcode"]) {
        is JsonArray -> g.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("\n")
        is JsonPrimitive -> g.contentOrNull
        else -> null
    }
}.getOrNull()

/** Non-blank `description` field of a macro section, or null. */
private fun JsonObject.descriptionOrNull(): String? =
    (this["description"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

/** Description for [lowerName] from the raw `config` map, matching the `gcode_macro <name>` section case-insensitively. */
private fun configDescriptionFor(config: JsonObject, lowerName: String): String? {
    val section = config.entries.firstOrNull {
        it.key.startsWith("gcode_macro ", ignoreCase = true) &&
            it.key.substring("gcode_macro ".length).equals(lowerName, ignoreCase = true)
    }?.value as? JsonObject
    return section?.descriptionOrNull()
}
