package works.mees.dinghy.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
 * **Catalog fidelity (docs/moonraker-capabilities.md § "File metadata"):** ONLY the four confirmed
 * fields are read — `layer_count`, `object_height`, `estimated_time`, and `thumbnails[]`
 * (`{width,height,size,relative_path}`). Anything NOT in that doc is NOT invented; a missing/garbage
 * field yields `null` for THAT field (never `!!`), so the cell degrades to a dashed value / the ring
 * keeps Benchy. `progress` is NOT a metadata field — it comes from the LIVE `virtual_sdcard` /
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
    /** `relative_path` of the LARGEST thumbnail by width (typically the 300×300); null if none. */
    val largestThumbRelPath: String?,
)

/**
 * Map a `server.files.metadata` [result] object to a [PrintMetadata]. Every walk is null-safe
 * (`as?`/`orNull`, NEVER `!!`); an entirely empty object yields all-null fields. The thumbnail pick
 * iterates `thumbnails[]` and chooses the entry with the greatest `width`, reading its `relative_path`
 * — an absent/empty array yields a null [PrintMetadata.largestThumbRelPath]. No field outside the four
 * catalog-confirmed keys is read.
 */
fun parsePrintMetadata(result: JsonObject): PrintMetadata {
    val layerCount = runCatching { result["layer_count"]?.jsonPrimitive?.intOrNull }.getOrNull()
    val objectHeight = runCatching { result["object_height"]?.jsonPrimitive?.doubleOrNull }.getOrNull()
    val estimatedTime = runCatching { result["estimated_time"]?.jsonPrimitive?.doubleOrNull }.getOrNull()

    val largestThumbRelPath = run {
        val thumbs = result["thumbnails"] as? JsonArray ?: return@run null
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
        runCatching { best?.get("relative_path")?.jsonPrimitive?.content }.getOrNull()
    }

    return PrintMetadata(
        layerCount = layerCount,
        objectHeight = objectHeight,
        estimatedTime = estimatedTime,
        largestThumbRelPath = largestThumbRelPath,
    )
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
