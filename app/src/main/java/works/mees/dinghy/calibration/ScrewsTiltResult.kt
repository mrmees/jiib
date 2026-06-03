package works.mees.dinghy.calibration

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/**
 * Pure `screws_tilt_adjust` result parser (CALIB-02 / D-03). Mirrors the project's pure-parser
 * discipline ([works.mees.dinghy.ui.console.parseGcodeStore]): NO I/O, NO coroutines, NO Compose,
 * `runCatching`/`getOrDefault`/null-returning walks, never `!!`. Same input → same output, so it is
 * fully host-testable off-hardware ([works.mees.dinghy.calibration.ScrewsTiltResultTest]).
 *
 * REAL-SHAPE CONTRACT (09-01 surprises, captured live on the Ender 5 Plus):
 *  - `results` is keyed `screw1..screwN` (1-BASED loop index), NOT the screw's name (Pitfall 1).
 *  - Each `screwN` = `{z: Double, sign: "CW"|"CCW", adjust: String, is_base: Boolean}`.
 *  - **`adjust` is a CLOCK STRING `"HH:MM"`** (full turns : clock-minutes; e.g. `"00:07"`), carried
 *    and displayed VERBATIM — we parse the clock to compare/format, never `parseDouble` it across
 *    the colon (Klipper already did the turn math; we only read it).
 *  - The base screw (`is_base == true`, `adjust == "00:00"`) is the reference — EXCLUDED from
 *    worst-screw selection.
 *  - `results["screwN"]` joins to config `screwN_name` BY 1-BASED INDEX.
 *
 * WORST-SCREW RANK: the worst non-base screw is the one whose probed `z` deviates most from the base
 * reference plane — i.e. max `|z|` among non-base screws (the base sits at the reference; the screw
 * that needs the biggest physical correction is the one furthest off). The clock turn (`adjust`) is
 * the user-facing instruction Klipper computed for that screw, displayed verbatim.
 */

/** Per-screw display row: the live turn result joined to its config name (1-based index). */
data class ScrewTurn(
    /** The live results key (`screw1..screwN`, 1-based). */
    val key: String,
    /** The 1-based index parsed out of [key] (`screw3` → 3). */
    val index: Int,
    /** Config `screwN_name` joined by index, or null if the config has no label. */
    val name: String?,
    /** Probed Z height for this screw (the deviation-from-base magnitude drives worst-screw rank). */
    val z: Double,
    /** `"CW"`/`"CCW"` carried verbatim. */
    val sign: String?,
    /** The `"HH:MM"` clock string carried verbatim (the user's turn instruction). */
    val adjust: String,
    /** Parsed clock magnitude in total minutes (`"01:15"` → 75); used for display/formatting only. */
    val adjustSeconds: Int,
    /** Convenience degrees view of the turn for the UI-SPEC second column (see [clockToDegrees]). */
    val degrees: Double,
    /** True when this screw's |z| is within the D-03 tolerance band (`<= IN_TOL_MM`). */
    val isInTol: Boolean,
    /** True for the base/reference screw (`is_base == true`). */
    val isBase: Boolean,
)

/** The guided screws-tilt loop view-model: per-screw rows + worst screw + the "X of N" pair. */
data class GuidedLoopState(
    val screws: List<ScrewTurn> = emptyList(),
    val worstScrew: ScrewTurn? = null,
    val inToleranceCount: Int = 0,
    val totalScrews: Int = 0,
    val error: Boolean = false,
)

/**
 * D-03 in-tolerance threshold (mm of Z deviation). A non-base screw whose |z| is within this band of
 * the reference is "good enough" for the "X of N in tolerance" readout; the base screw is always in
 * tolerance. The worst screw is still surfaced regardless of this count. Klipper's SCREWS_TILT does
 * not expose a per-screw boolean, so we derive it from the probed Z magnitude.
 */
private const val IN_TOL_MM = 0.05

/**
 * Parse a `"HH:MM"` Klipper screws clock string to total clock-minutes (full-turns * 60 + minutes).
 * Returns 0 on any malformed shape (no colon, non-numeric) so a bad row degrades gracefully rather
 * than throwing. (`"01:15"` = 1 full turn + 15 min → 75; `"00:07"` → 7.)
 */
fun clockToSeconds(adjust: String?): Int = runCatching {
    val parts = (adjust ?: return@runCatching 0).split(":")
    if (parts.size != 2) return@runCatching 0
    val turns = parts[0].trim().toInt()
    val minutes = parts[1].trim().toInt()
    turns * 60 + minutes
}.getOrDefault(0)

/**
 * Pure formatting helper: a screws-tilt `"HH:MM"` clock turn → degrees of screw rotation for the
 * UI-SPEC row's second column. Klipper encodes a full screw turn as 60 clock-minutes (a clock face);
 * so degrees = total-minutes / 60 * 360 = total-minutes * 6.
 */
fun clockToDegrees(adjust: String?): Double = clockToSeconds(adjust) * 6.0

/**
 * Pure `screws_tilt_adjust` parser. Accepts the OUTER live/snapshot object (the same
 * `{"screws_tilt_adjust": {...}}` shape the reducer and fixtures carry) plus the OUTER config object
 * (`{"screws_tilt_adjust": {"screw1":[x,y], "screw1_name":..., ...}}`). Returns a [GuidedLoopState];
 * a malformed shape degrades to an empty state (never throws).
 */
fun parseScrewsTilt(results: JsonObject, config: JsonObject): GuidedLoopState = runCatching {
    val sta = results["screws_tilt_adjust"]?.jsonObject ?: return@runCatching GuidedLoopState()
    val error = sta["error"]?.jsonPrimitive?.booleanOrNull ?: false
    val resultMap = sta["results"]?.jsonObject ?: return@runCatching GuidedLoopState(error = error)
    val cfg = config["screws_tilt_adjust"]?.jsonObject

    val screws = resultMap.entries.mapNotNull { (key, el) ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val index = key.removePrefix("screw").toIntOrNull() ?: return@mapNotNull null
        val z = obj["z"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val sign = obj["sign"]?.jsonPrimitive?.contentOrNull
        val adjust = obj["adjust"]?.jsonPrimitive?.contentOrNull ?: "00:00"
        val isBase = obj["is_base"]?.jsonPrimitive?.booleanOrNull ?: false
        val name = cfg?.get("${key}_name")?.jsonPrimitive?.contentOrNull
        ScrewTurn(
            key = key,
            index = index,
            name = name,
            z = z,
            sign = sign,
            adjust = adjust,
            adjustSeconds = clockToSeconds(adjust),
            degrees = clockToDegrees(adjust),
            isInTol = isBase || abs(z) <= IN_TOL_MM,
            isBase = isBase,
        )
    }.sortedBy { it.index }

    // Worst = the max |z| among NON-base screws (the screw whose probed plane deviates most from the
    // reference needs the biggest physical correction). Null when every non-base screw is in tolerance.
    val worst = screws
        .filter { !it.isBase && !it.isInTol }
        .maxByOrNull { abs(it.z) }

    GuidedLoopState(
        screws = screws,
        worstScrew = worst,
        inToleranceCount = screws.count { it.isInTol },
        totalScrews = screws.size,
        error = error,
    )
}.getOrDefault(GuidedLoopState())
