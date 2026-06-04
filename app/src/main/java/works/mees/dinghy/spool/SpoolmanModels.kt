package works.mees.dinghy.spool

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import works.mees.dinghy.net.MoonrakerJson

/**
 * Null-safe, headless model of the Spoolman inventory wire types (SPOOL-02; D-08).
 *
 * EVERY field is untrusted and OPTIONAL — Spoolman omits nulls entirely from its JSON (the live
 * goldens prove this: spool 6 carries no `location`, spool 3 no `first_used`, filament 1 no
 * `settings_extruder_temp`), so every wire field is nullable-or-defaulted and NEVER fabricated,
 * mirroring [works.mees.dinghy.state.Webcam]'s tolerant `@Serializable`/`@SerialName` discipline and
 * the shared lenient [MoonrakerJson] (`ignoreUnknownKeys`). A malformed row drops via `mapNotNull` in
 * the parser; it never throws.
 *
 * The snake_case wire keys are mapped via [SerialName]; unknown keys are ignored by [MoonrakerJson].
 */

/** A single pending Spoolman usage report awaiting flush to the server. */
@Serializable
data class PendingSpoolmanReport(
    @SerialName("spool_id") val spoolId: Int = 0,
    @SerialName("filament_used") val filamentUsedMm: Double = 0.0,
)

/**
 * The `server.spoolman.status` reply shape — connection state + the active spool id + any pending
 * usage reports. `spoolman_connected` is the only reliably-present field; `spool_id` is null when no
 * spool is active (D-13 clear), and `pending_reports` defaults empty (the live golden has `[]`).
 */
@Serializable
data class SpoolmanStatus(
    @SerialName("spoolman_connected") val spoolmanConnected: Boolean = false,
    @SerialName("spool_id") val activeSpoolId: Int? = null,
    @SerialName("pending_reports") val pendingReports: List<PendingSpoolmanReport> = emptyList(),
) {
    /** True when Spoolman has un-flushed usage reports queued (a card-surfaced "pending" state). */
    val hasPendingReports: Boolean get() = pendingReports.isNotEmpty()
}

/** The vendor/manufacturer of a filament. Both fields are optional on the wire. */
@Serializable
data class SpoolmanVendor(
    val id: Int? = null,
    val name: String? = null,
)

/**
 * A filament definition (the material/color/temps a [SpoolmanSpool] is wound with). Every field is
 * optional; D-08 color normalization + multi-color split live here as computed `val`s (the
 * `safeRotation`/`hasSnapshot` precedent on [works.mees.dinghy.state.Webcam]).
 */
@Serializable
data class SpoolmanFilament(
    val id: Int? = null,
    val name: String? = null,
    val material: String? = null,
    @SerialName("color_hex") val colorHex: String? = null,
    @SerialName("multi_color_hexes") val multiColorHexes: String? = null,
    @SerialName("multi_color_direction") val multiColorDirection: String? = null,
    val vendor: SpoolmanVendor? = null,
    @SerialName("settings_extruder_temp") val settingsExtruderTemp: Int? = null,
    @SerialName("settings_bed_temp") val settingsBedTemp: Int? = null,
    @SerialName("spool_weight") val spoolWeight: Double? = null,
    val extra: Map<String, String> = emptyMap(),
) {
    /**
     * The display-normalized single color (D-08): accept with/without a leading `#`, uppercase, and
     * require exactly 6 or 8 hex digits after normalization — otherwise null (the neutral
     * unknown-color marker). `"ff0000"` → `"#FF0000"`; `"#ff0000"` → `"#FF0000"`; `"ff0000ff"` →
     * `"#FF0000FF"`; `"xyz"`/`""`/`"12345"` → null. Never throws.
     */
    val normalizedColorHex: String? get() = normalizeColorHex(colorHex)

    /**
     * The split swatch list (D-08): when [multiColorHexes] is present (comma-separated), split and
     * normalize each color, dropping invalid entries; otherwise fall back to the single
     * [normalizedColorHex] (a one-element list, or empty when that too is invalid/absent).
     */
    val colorSwatches: List<String>
        get() {
            val multi = multiColorHexes
            if (!multi.isNullOrBlank()) {
                val parts = multi.split(',').mapNotNull { normalizeColorHex(it) }
                if (parts.isNotEmpty()) return parts
            }
            return listOfNotNull(normalizedColorHex)
        }
}

/**
 * A physical spool of [filament] with remaining/used weight & length, optional location and lot. The
 * top-level numeric/string fields are all optional (Spoolman omits them when unset). `extra` is a
 * `Map<String,String>` (Spoolman stores per-key JSON-encoded strings) defaulted empty; an invalid
 * inner-JSON value never breaks construction — it is only parsed on demand via [extraJson].
 */
@Serializable
data class SpoolmanSpool(
    val id: Int = 0,
    val filament: SpoolmanFilament? = null,
    @SerialName("remaining_weight") val remainingWeight: Double? = null,
    @SerialName("remaining_length") val remainingLength: Double? = null,
    @SerialName("used_weight") val usedWeight: Double? = null,
    @SerialName("used_length") val usedLength: Double? = null,
    val location: String? = null,
    @SerialName("lot_nr") val lotNumber: String? = null,
    val archived: Boolean = false,
    val extra: Map<String, String> = emptyMap(),
    @SerialName("initial_weight") val initialWeight: Double? = null,
    @SerialName("registered") val registered: String? = null,
    @SerialName("spool_weight") val spoolWeight: Double? = null,
) {
    /**
     * The empty-spool (tare) weight Spoolman subtracts from a measured GROSS weight to get remaining
     * filament: the spool's own `spool_weight` if set, else the filament's default. Null = not configured.
     */
    val effectiveSpoolWeight: Double? get() = spoolWeight ?: filament?.spoolWeight
    /**
     * The original (full) spool weight for the "remaining / original g" detail line: Spoolman's
     * [initialWeight] when present, else reconstructed as remaining + used. Null only when neither is known.
     */
    val originalWeight: Double?
        get() = initialWeight ?: run {
            val r = remainingWeight
            val u = usedWeight
            if (r != null && u != null) r + u else null
        }
    /**
     * Safely parse the JSON-encoded value Spoolman stores under [key] in `extra` (each value is a
     * JSON string, e.g. `"\"dried\""` or `"true"`). Returns null when the key is absent OR the stored
     * value is not valid JSON — never throws, so one garbage `extra` entry can't break the spool.
     */
    fun extraJson(key: String): JsonElement? {
        val raw = extra[key] ?: return null
        return runCatching { MoonrakerJson.parseToJsonElement(raw) }.getOrNull()
    }
}

/**
 * D-08 defensive color normalization (free function so the parser/tests can call it directly).
 * Accepts a hex color with or without a leading `#`, trims whitespace, uppercases, and requires
 * EXACTLY 6 or 8 hex digits after stripping the `#`. Anything else (empty, wrong length, non-hex)
 * yields null — the caller renders a neutral unknown-color marker. Never throws.
 */
fun normalizeColorHex(raw: String?): String? {
    val trimmed = raw?.trim()?.removePrefix("#") ?: return null
    if (trimmed.length != 6 && trimmed.length != 8) return null
    if (!trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return "#" + trimmed.uppercase()
}
