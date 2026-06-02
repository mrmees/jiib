package works.mees.dinghy.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * PURE print-history model + mapper for the idle Status home Inc 3 (260601-th9). Turns the confirmed
 * `server.history.list?limit=1&order=desc` reply into the small "last completed job" the idle Status
 * field renders — host-testable, no I/O / coroutines / socket, mirroring [parsePrintMetadata].
 *
 * **Catalog fidelity (docs/moonraker-capabilities.md § "Print history"):** ONLY the confirmed top-level
 * job fields (`filename`, `status`, `print_duration`, `total_duration`, `filament_used`, `exists`) and
 * the SAME `metadata` shape already modeled in [PrintMetadata] (`estimated_time`, `filament_weight_total`,
 * `thumbnails[]`) are read. Nothing outside that doc is invented; every walk is null-safe (`as?`/`orNull`,
 * NEVER `!!`) so a deleted file (`exists:false` / absent metadata) or a garbage field degrades that field
 * to null and the card still renders its text stats — it never crashes.
 */
data class LastJob(
    /** `filename` — gcode path (dir/ + file); the thumbnail-URL key. */
    val filename: String,
    /** `status` verbatim (completed|cancelled|error|klippy_shutdown|interrupted|server_exit|in_progress). */
    val status: String,
    /** `print_duration` (s) — actual extruding time (ELAPSED). */
    val printDuration: Double,
    /** `total_duration` (s) — wall time incl. heating/pauses (TOTAL). */
    val totalDuration: Double,
    /** `filament_used` (mm) — filament extruded this job. */
    val filamentUsed: Double,
    /** `exists` — false when the source gcode was deleted (thumbnail may 404 → omit it). */
    val exists: Boolean,
    /** `metadata.estimated_time` (s) — slicer estimate; null when metadata absent/partial. */
    val estimatedTime: Double?,
    /** `metadata.filament_weight_total` (g); null when metadata absent/partial. */
    val filamentWeightTotal: Double?,
    /** `relative_path` of the largest `metadata.thumbnails[]` by width; null when none. */
    val largestThumbRelPath: String?,
)

/**
 * Map a `server.history.list` [result] object to the most-recent [LastJob], or null when there is no
 * history to show:
 *  - `count == 0` (or `jobs` absent/empty) → null (the empty-state signal — the field shows file_copy_off).
 *  - otherwise → `jobs[0]` mapped to a [LastJob]; top-level text fields always populate, the
 *    metadata-derived fields (est/weight/thumbnail) read null when `metadata` is absent/partial (a
 *    deleted file → `exists:false`). Every walk is null-safe; a single garbage field yields null for
 *    THAT field only and never throws.
 */
fun parseLastJob(result: JsonObject): LastJob? {
    val count = runCatching { result["count"]?.jsonPrimitive?.intOrNull }.getOrNull()
    if (count == 0) return null

    val jobs = result["jobs"] as? JsonArray ?: return null
    val job = jobs.firstOrNull() as? JsonObject ?: return null

    val metadata = job["metadata"] as? JsonObject

    return LastJob(
        filename = runCatching { job["filename"]?.jsonPrimitive?.content }.getOrNull().orEmpty(),
        status = runCatching { job["status"]?.jsonPrimitive?.content }.getOrNull().orEmpty(),
        printDuration = runCatching { job["print_duration"]?.jsonPrimitive?.doubleOrNull }.getOrNull() ?: 0.0,
        totalDuration = runCatching { job["total_duration"]?.jsonPrimitive?.doubleOrNull }.getOrNull() ?: 0.0,
        filamentUsed = runCatching { job["filament_used"]?.jsonPrimitive?.doubleOrNull }.getOrNull() ?: 0.0,
        exists = runCatching { job["exists"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() }.getOrNull() ?: false,
        estimatedTime = runCatching { metadata?.get("estimated_time")?.jsonPrimitive?.doubleOrNull }.getOrNull(),
        filamentWeightTotal = runCatching { metadata?.get("filament_weight_total")?.jsonPrimitive?.doubleOrNull }.getOrNull(),
        largestThumbRelPath = largestThumbRelPath(metadata),
    )
}
