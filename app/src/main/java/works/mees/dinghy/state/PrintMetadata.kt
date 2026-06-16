package works.mees.dinghy.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * PURE gcode-file metadata model + mappers for the Status home Inc 2 (260601-sip). Turns a confirmed
 * `server.files.metadata` reply into the small set of fields the Print Status screen derives its
 * currently-dashed cells from — host-testable with no I/O, no coroutines, no socket, mirroring the
 * [parseTemperatureStore] pure-module shape.
 *
 * **Catalog fidelity (docs/moonraker-capabilities.md § "File metadata"):** the catalog-confirmed
 * fields read here are `layer_count`, `object_height`, `estimated_time`, `thumbnails[]`
 * (`{width,height,size,relative_path}`), and `filament_colors[]` (the per-extruder color array — also
 * catalog-confirmed, moonraker-capabilities.md § "File metadata", and already proven parseable in
 * [parseFilePreviewMetadata]). Anything NOT in that doc is NOT invented; a missing/garbage field yields
 * `null` (or an empty list for the array) for THAT field (never `!!`), so the cell degrades to a dashed
 * value / the ring keeps Benchy / the spool glyph draws empty.
 * `progress` is NOT a metadata field — it comes from the LIVE `virtual_sdcard` /
 * `display_status` reduced into [PrinterState.progress]; the screen multiplies it against
 * [estimatedTime].
 */
data class PrintMetadata(
    /** `layer_count` — total layers (reliable, from the slicer file); null when absent. */
    val layerCount: Int?,
    /** `object_height` (mm) — final print height context; null when absent (E5 sample omits it). */
    val objectHeight: Double?,
    /** `estimated_time` (s) — slicer file estimate, the ETA source; null when absent. */
    val estimatedTime: Double?,
    /** `layer_height` (mm) — slicer layer pitch; null when absent. Used to derive the current layer
     *  from print height when `print_stats.info.current_layer` is null. */
    val layerHeight: Double? = null,
    /** `first_layer_height` (mm) — slicer first-layer pitch; null when absent (then [layerHeight] is
     *  assumed for layer 1). */
    val firstLayerHeight: Double? = null,
    /** `relative_path` of the LARGEST thumbnail by width (typically the 300×300); null if none. */
    val largestThumbRelPath: String?,
    /**
     * `filament_colors[]` — per-extruder `#hex` colors from the catalog-confirmed `filament_colors`
     * key (docs/moonraker-capabilities.md), empty when absent. Threaded onto the LIVE active-file
     * metadata in 18.3-01 so the color-reactive spool glyph has a gcode color FALLBACK (D-07) when no
     * Spoolman active spool is known. Null-safe by construction (missing/garbage → empty list).
     */
    val filamentColors: List<String> = emptyList(),
)

data class FilePreviewMetadata(
    val filename: String,
    val sizeBytes: Long?,
    val modifiedEpochSeconds: Double?,
    val estimatedTime: Double?,
    val filamentTotal: Double?,
    val filamentWeightTotal: Double?,
    val layerCount: Int?,
    val objectHeight: Double?,
    val largestThumbRelPath: String?,
    /** `filament_type[]` — per-extruder material families (multi-material), empty when absent. */
    val filamentType: List<String> = emptyList(),
    /** `filament_name[]` — per-extruder filament names, empty when absent. */
    val filamentName: List<String> = emptyList(),
    /** `filament_colors[]` — per-extruder `#hex` colors, empty when absent. */
    val filamentColors: List<String> = emptyList(),
    /** `filament_weights[]` — per-extruder used weights (g), empty when absent. */
    val filamentWeights: List<Double> = emptyList(),
) {
    fun thumbnailUrl(httpBase: String): String? =
        largestThumbRelPath?.let { thumbnailUrl(httpBase, filename, it) }
}

/**
 * Map a `server.files.metadata` [result] object to a [PrintMetadata]. Every walk is null-safe
 * (`as?`/`orNull`, NEVER `!!`); an entirely empty object yields all-null fields. The thumbnail pick
 * iterates `thumbnails[]` and chooses the entry with the greatest `width`, reading its `relative_path`
 * — an absent/empty array yields a null [PrintMetadata.largestThumbRelPath]. `filament_colors[]` is read
 * via the tolerant [stringArray] helper (missing/garbage → empty list). No field outside the
 * catalog-confirmed keys is read.
 */
fun parsePrintMetadata(result: JsonObject): PrintMetadata {
    val layerCount = runCatching { result["layer_count"]?.jsonPrimitive?.intOrNull }.getOrNull()
    val objectHeight = runCatching { result["object_height"]?.jsonPrimitive?.doubleOrNull }.getOrNull()
    val estimatedTime = runCatching { result["estimated_time"]?.jsonPrimitive?.doubleOrNull }.getOrNull()
    val layerHeight = runCatching { result["layer_height"]?.jsonPrimitive?.doubleOrNull }.getOrNull()
    val firstLayerHeight = runCatching { result["first_layer_height"]?.jsonPrimitive?.doubleOrNull }.getOrNull()

    return PrintMetadata(
        layerCount = layerCount,
        objectHeight = objectHeight,
        estimatedTime = estimatedTime,
        layerHeight = layerHeight,
        firstLayerHeight = firstLayerHeight,
        largestThumbRelPath = largestThumbRelPath(result),
        filamentColors = stringArray(result, "filament_colors"),
    )
}

fun parseFilePreviewMetadata(filename: String, result: JsonObject): FilePreviewMetadata {
    val printMetadata = parsePrintMetadata(result)
    return FilePreviewMetadata(
        filename = filename,
        sizeBytes = runCatching { result["size"]?.jsonPrimitive?.content?.toLongOrNull() }.getOrNull(),
        modifiedEpochSeconds = runCatching { result["modified"]?.jsonPrimitive?.doubleOrNull }.getOrNull(),
        estimatedTime = printMetadata.estimatedTime,
        filamentTotal = runCatching { result["filament_total"]?.jsonPrimitive?.doubleOrNull }.getOrNull(),
        filamentWeightTotal = runCatching { result["filament_weight_total"]?.jsonPrimitive?.doubleOrNull }.getOrNull(),
        layerCount = printMetadata.layerCount,
        objectHeight = printMetadata.objectHeight,
        largestThumbRelPath = printMetadata.largestThumbRelPath,
        filamentType = stringArray(result, "filament_type"),
        filamentName = stringArray(result, "filament_name"),
        filamentColors = stringArray(result, "filament_colors"),
        filamentWeights = doubleArray(result, "filament_weights"),
    )
}

/**
 * Lift a string `JsonArray` from [result] under [key] to a `List<String>`, null-safe: a missing,
 * non-array, or garbage value (or a non-string entry) yields an empty list, never throws — mirroring
 * [largestThumbRelPath]'s tolerant array walk. Used for the multi-material `filament_*[]` arrays.
 */
private fun stringArray(result: JsonObject, key: String): List<String> =
    runCatching {
        (result[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
    }.getOrNull().orEmpty()

/** Double variant of [stringArray] for `filament_weights[]`; same null-safety contract. */
private fun doubleArray(result: JsonObject, key: String): List<Double> =
    runCatching {
        (result[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.doubleOrNull }
    }.getOrNull().orEmpty()

/**
 * The SHARED "largest thumbnail by width" pick (260601-th9 Inc 3 factored this out of
 * [parsePrintMetadata] so [parseLastJob] reuses it byte-identically). Walks [metadata]`["thumbnails"]`,
 * chooses the entry with the greatest `width`, and returns its `relative_path`. Fully null-safe:
 * a null/absent/empty/garbage array → null; never `!!`. Callers pass the object whose `thumbnails[]`
 * they want — the gcode-file metadata object (`server.files.metadata` result, or a history job's
 * `metadata`), which may itself be null for a deleted file.
 */
internal fun largestThumbRelPath(metadata: JsonObject?): String? {
    val thumbs = metadata?.get("thumbnails") as? JsonArray ?: return null
    var best: JsonObject? = null
    var bestWidth = Int.MIN_VALUE
    for (element in thumbs) {
        val obj = element as? JsonObject ?: continue
        val width = runCatching { obj["width"]?.jsonPrimitive?.intOrNull }.getOrNull() ?: continue
        if (width > bestWidth) {
            bestWidth = width
            best = obj
        }
    }
    return runCatching { best?.get("relative_path")?.jsonPrimitive?.content }.getOrNull()
}

/**
 * Pick the SMALLEST (least-wide) thumbnail's relative path — the cheap variant for dense list rows
 * where the slot is ~48dp, so we never decode the 300x300 PNG just to draw a tiny cell. Same
 * null-tolerance contract as [largestThumbRelPath]. The big Focus preview still uses the largest.
 */
internal fun smallestThumbRelPath(metadata: JsonObject?): String? {
    val thumbs = metadata?.get("thumbnails") as? JsonArray ?: return null
    var best: JsonObject? = null
    var bestWidth = Int.MAX_VALUE
    for (element in thumbs) {
        val obj = element as? JsonObject ?: continue
        val width = runCatching { obj["width"]?.jsonPrimitive?.intOrNull }.getOrNull() ?: continue
        if (width < bestWidth) {
            bestWidth = width
            best = obj
        }
    }
    return runCatching { best?.get("relative_path")?.jsonPrimitive?.content }.getOrNull()
}

/**
 * Build the absolute thumbnail URL for a gcode file's [relPath] (docs/moonraker-capabilities.md
 * § "Thumbnail URL construction"):
 *
 *   `<httpBase>/server/files/gcodes/<dir>/<relPath>`
 *
 * where `<dir>` is the directory portion of [gcodeFilename] ("" for a root file → the dir segment is
 * omitted entirely, no extra slash). Each path segment is URL-encoded (filenames have spaces) while
 * the `/` separators in [relPath] (e.g. `.thumbs/<name>-300x300.png`) are preserved.
 */
fun thumbnailUrl(httpBase: String, gcodeFilename: String, relPath: String): String {
    fun encodeSegment(seg: String): String =
        URLEncoder.encode(seg, "UTF-8").replace("+", "%20")

    fun encodePath(path: String): String =
        path.split('/').joinToString("/") { encodeSegment(it) }

    val dir = gcodeFilename.substringBeforeLast('/', "")
    val encodedRel = encodePath(relPath)
    val tail = if (dir.isBlank()) encodedRel else "${encodePath(dir)}/$encodedRel"
    return "$httpBase/server/files/gcodes/$tail"
}
