package works.mees.dinghy.systeminfo

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Pure value formatters for the System Information page (SYS-03 display contract / SYS-04 degrade).
 * NO I/O, NO Compose — host-unit-testable. Every memory field across both endpoints is in kB
 * (`memory_units` == "kB").
 *
 * Each formatter returns the em dash [DASH] ("—") on null/missing input — the SYS-04 degrade
 * contract: the labeled row stays present, the value reads "—". All number formatting uses
 * [Locale.US] (project convention — see the PrinterCommands gcode builders).
 */

/** The degrade placeholder rendered for any null/missing value. */
const val DASH: String = "—"

private const val KB_PER_MB = 1024.0
private const val KB_PER_GB = 1024.0 * 1024.0

/** kB → "7.6 GB" (÷1024², one decimal). The Total RAM row locks GB. "—" on null. */
fun formatGb(totalKb: Long?): String {
    val kb = totalKb ?: return DASH
    return String.format(Locale.US, "%.1f GB", kb / KB_PER_GB)
}

/**
 * Auto-scaled "used / total" memory row: each side rendered in MB when < 1 GB, else GB.
 * e.g. 761312 kB / 8007452 kB → "744 MB / 7.6 GB". "—" if either side is null.
 */
fun formatMemoryUsedOverTotal(usedKb: Long?, totalKb: Long?): String {
    if (usedKb == null || totalKb == null) return DASH
    return "${autoScaleKb(usedKb)} / ${autoScaleKb(totalKb)}"
}

/** kB → "744 MB" (< 1 GB, whole MB) or "7.6 GB" (>= 1 GB, one decimal). */
private fun autoScaleKb(kb: Long): String =
    if (kb >= KB_PER_GB) {
        String.format(Locale.US, "%.1f GB", kb / KB_PER_GB)
    } else {
        "${(kb / KB_PER_MB).roundToLong()} MB"
    }

/**
 * HOST uptime → compact "2d 3h 14m", DROPPING leading-zero units from the left.
 *  - days > 0 → "2d 3h 14m" ; under a day → "3h 14m" (no "0d") ; under an hour → "14m".
 *  - "—" on null. Source = proc_stats.system_uptime (seconds).
 */
fun formatUptime(seconds: Double?): String {
    val s = seconds ?: return DASH
    if (s < 0) return DASH
    val total = s.toLong()
    val days = total / 86_400
    val hours = (total % 86_400) / 3_600
    val minutes = (total % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/** CPU load → integer percent, e.g. 29.31 → "29%". "—" on null. */
fun formatCpuLoad(pct: Float?): String {
    val p = pct ?: return DASH
    return "${p.roundToInt()}%"
}

/** CPU temp → whole °C, e.g. 64.757 → "65°C". "—" on null. */
fun formatTemp(c: Float?): String {
    val t = c ?: return DASH
    return "${t.roundToInt()}°C"
}

/** Integer core count, "—" on null. */
fun formatCores(count: Int?): String = count?.toString() ?: DASH
