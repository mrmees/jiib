package works.mees.jiib.calibration

/**
 * Pure PROBE_ACCURACY result parser (no I/O, no coroutines, no Compose).
 *
 * Mirrors the project's pure-parser discipline ([parseZPosition]): `runCatching`/`getOrNull`,
 * never `!!`, null-returning on mismatch. HOST-TESTED ([ProbeAccuracyTest]).
 *
 * REAL-SHAPE CONTRACT:
 *  - Real Klipper streams emit per-sample progress lines FIRST, then a single summary:
 *      `probe accuracy results: maximum 2.012500, minimum 2.000000, range 0.012500,
 *       average 2.005000, median 2.005000, standard deviation 0.003536`
 *    The `// ` console prefix is optional. Values may be negative (e.g. Z-offset calibration).
 *  - Progress lines (`probe at … is z=…`) and any other non-matching text return null.
 *  - Malformed / partial result lines return null.
 */

/**
 * Parsed summary from a PROBE_ACCURACY run. All values are in the same unit as the probe Z
 * output (typically mm).
 */
data class ProbeAccuracyResult(
    val maximum: Double,
    val minimum: Double,
    val range: Double,
    val average: Double,
    val median: Double,
    val stdDev: Double,
)

/**
 * Regex that FINDS the `probe accuracy results:` summary line anywhere in the input, tolerating
 * the optional `// ` console prefix. Each of the six named fields is captured as a signed decimal.
 */
private val PROBE_ACCURACY_REGEX = Regex(
    """probe accuracy results:\s+""" +
    """maximum\s+(-?[\d.]+),\s+""" +
    """minimum\s+(-?[\d.]+),\s+""" +
    """range\s+(-?[\d.]+),\s+""" +
    """average\s+(-?[\d.]+),\s+""" +
    """median\s+(-?[\d.]+),\s+""" +
    """standard deviation\s+(-?[\d.]+)"""
)

/**
 * Pure parse of a single console line. Returns the six-field summary when the line contains the
 * `probe accuracy results:` pattern; returns null for progress lines, non-matching content, or
 * any value that fails Double conversion.
 */
fun parseProbeAccuracy(line: String): ProbeAccuracyResult? = runCatching {
    val m = PROBE_ACCURACY_REGEX.find(line) ?: return@runCatching null
    val (maximum, minimum, range, average, median, stdDev) = m.destructured
    ProbeAccuracyResult(
        maximum = maximum.toDoubleOrNull() ?: return@runCatching null,
        minimum = minimum.toDoubleOrNull() ?: return@runCatching null,
        range = range.toDoubleOrNull() ?: return@runCatching null,
        average = average.toDoubleOrNull() ?: return@runCatching null,
        median = median.toDoubleOrNull() ?: return@runCatching null,
        stdDev = stdDev.toDoubleOrNull() ?: return@runCatching null,
    )
}.getOrNull()
