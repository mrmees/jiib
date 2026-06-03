package works.mees.dinghy.calibration

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure manual-probe parse + page-state (CALIB-05 / D-01). Mirrors the project's pure-parser
 * discipline ([works.mees.dinghy.ui.console.parseGcodeStore]): NO I/O, NO coroutines, NO Compose,
 * `runCatching`/null-returning walks, never `!!`. Host-tested off-hardware
 * ([works.mees.dinghy.calibration.ManualProbeStateTest]).
 *
 * REAL-SHAPE CONTRACT (09-01, captured mid-session on the Ender 5 Plus):
 *  - The gcode_response line from real hardware is
 *      `"// Z position: ?????? --> 7.624 <-- ??????"`
 *    — the bounds can be the literal `??????` (UNKNOWN before they're set). [parseZPosition] MUST
 *    treat `??????` as null, NEVER assume three floats. The `// ` console prefix is optional.
 *  - A malformed / non-matching line returns null.
 *  - The page state is driven by `manual_probe.is_active` (active → live Z-jog page; false → idle).
 */

/**
 * One parsed `Z position` bracket. [current] is the live probed Z (always present when the line
 * matched); [lower]/[upper] are the nudge brackets — NULL when the wire carried `??????` (unknown).
 */
data class ZPositionBracket(
    val lower: Double?,
    val current: Double,
    val upper: Double?,
)

/**
 * The real Klipper manual-probe console line:
 *   `"// Z position: <lower> --> <current> <-- <upper>"`
 * where any of the three may be `??????` (only `current` is guaranteed numeric for a real bracket).
 * The leading `// ` console prefix is optional. Captures are walked through [boundOrNull] so a
 * `??????` (or any non-numeric) bound becomes null rather than a fabricated float.
 */
private val Z_POSITION_REGEX =
    Regex("""Z position:\s*(\S+)\s*-->\s*(\S+)\s*<--\s*(\S+)""")

/** A capture group → Double, or null for `??????` / any non-numeric token. */
private fun boundOrNull(token: String): Double? =
    if (token == "??????") null else token.toDoubleOrNull()

/**
 * Pure `// Z position:` parse. Returns the bracket, or null when the line does not match OR the
 * `current` value is not a real number (a bracket with an unknown CURRENT is not actionable).
 */
fun parseZPosition(line: String): ZPositionBracket? = runCatching {
    val m = Z_POSITION_REGEX.find(line) ?: return@runCatching null
    val (lowerRaw, currentRaw, upperRaw) = m.destructured
    val current = currentRaw.toDoubleOrNull() ?: return@runCatching null
    ZPositionBracket(
        lower = boundOrNull(lowerRaw),
        current = current,
        upper = boundOrNull(upperRaw),
    )
}.getOrNull()

/**
 * Pure page-state derive (Pattern 3): is the manual-probe session live? Accepts the OUTER
 * `{"manual_probe": {...}}` object; reads `manual_probe.is_active`. Absent/malformed → false (idle).
 */
fun manualProbeActive(manualProbe: JsonObject): Boolean = runCatching {
    manualProbe["manual_probe"]?.jsonObject
        ?.get("is_active")?.jsonPrimitive?.booleanOrNull ?: false
}.getOrDefault(false)
