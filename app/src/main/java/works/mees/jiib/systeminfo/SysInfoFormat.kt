package works.mees.jiib.systeminfo

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

// ── New formatters for the device-browser Focus detail (Task 8) ───────────────────────────────────

/**
 * MCU clock Hz → "168 MHz" (÷1_000_000, integer). "—" on null or zero.
 * Klipper `mcu_freq` is reported as a Long Hz.
 */
fun formatClock(hz: Long?): String {
    val v = hz ?: return DASH
    return "${v / 1_000_000} MHz"
}

/**
 * MCU load fraction → integer percent, e.g. 0.0731 → "7%".
 * Klipper `mcu_awake` is a fraction of the stat interval (0..1+). "—" on null.
 */
fun formatLoad(awake: Float?): String {
    val v = awake ?: return DASH
    return "%.0f%%".format(v * 100)
}

/**
 * MCU bandwidth → "↑1.0 KB ↓2.0 KB" style (one decimal each).
 * "—" if both sides are null; each side independently degrades to "—" if null.
 * Klipper reports bytes (not kB) for write/read counters.
 */
fun formatBytes(write: Long?, read: Long?): String {
    if (write == null && read == null) return DASH
    val up = if (write != null) "↑${"%.1f".format(write / 1024.0)} KB" else "↑$DASH"
    val down = if (read != null) "↓${"%.1f".format(read / 1024.0)} KB" else "↓$DASH"
    return "$up $down"
}

// ── Shared helpers used by DeviceFocus.kt (HostDetail) ──────────────────────────────────────────

/**
 * "CPU model · N cores" — degrades each side independently; "—" when neither is present.
 * Shared implementation used by [works.mees.jiib.ui.systeminfo.DeviceFocus] (HostDetail).
 */
internal fun cpuValue(identity: SystemInfo?): String {
    val model = identity?.cpuDesc?.takeIf { it.isNotBlank() } ?: identity?.processor?.takeIf { it.isNotBlank() }
    val cores = identity?.cpuCount
    return when {
        model != null && cores != null -> "$model · ${formatCores(cores)} cores"
        model != null -> model
        cores != null -> "${formatCores(cores)} cores"
        else -> DASH
    }
}

/**
 * "Debian GNU/Linux 12 (bookworm)" — name + version; "—" when the name is absent.
 * Shared implementation used by [works.mees.jiib.ui.systeminfo.DeviceFocus] (HostDetail).
 */
internal fun distroValue(identity: SystemInfo?): String {
    val name = identity?.distroName?.takeIf { it.isNotBlank() } ?: return DASH
    val version = identity?.distroVersion?.takeIf { it.isNotBlank() }
    return if (version != null && version !in name) "$name $version" else name
}

/** A nullable/blank String → its value or the degrade dash (SYS-04). */
internal fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: DASH

// ── Scrollable plain-text detail line-builders (Task 9 UAT) ──────────────────────────────────────
//
// Pure functions — no Compose, no I/O — host-unit-testable.
// Each builder emits one String per conceptual "line"; blank lines are omitted.
// Per-field degrade: a null/missing segment is dropped from its line; if a whole line has no
// data it is either omitted or rendered as "—".

/**
 * Returns the ordered plain-text lines for the **host SBC** Focus detail block.
 *
 * Line contract (owner UAT spec):
 *  1. `<cpu desc/processor> · <N> cores · <total RAM>`  (each segment independently optional)
 *  2. `<distroName [version]> · kernel <kernel>`         (omit "kernel …" if null)
 *  3. `Up <uptime>`                                      (omitted if null)
 *  4. `CPU <load> · <temp> · mem <used / total>`         (each segment dropped if null)
 *  5. `Klipper <ver> · Moonraker <ver>`                  (whole line omitted if both null)
 *  6+ Each throttle condition string on its own line     (omitted when [throttle] is empty)
 *
 * @param host     The host device data.
 * @param throttle Decoded throttle condition labels (empty = off-Pi / nothing to show).
 */
fun hostDetailLines(host: HostDevice, throttle: List<String>): List<String> {
    val id = host.identity
    val lines = mutableListOf<String>()

    // Line 1: cpu · cores · total RAM
    val cpuSeg = id?.let { info ->
        val model = info.cpuDesc?.takeIf { it.isNotBlank() } ?: info.processor?.takeIf { it.isNotBlank() }
        val cores = info.cpuCount
        when {
            model != null && cores != null -> "$model · ${formatCores(cores)} cores"
            model != null -> model
            cores != null -> "${formatCores(cores)} cores"
            else -> null
        }
    }
    val ramSeg = id?.totalMemoryKb?.let { formatGb(it) }
    val cpuLine = listOfNotNull(cpuSeg, ramSeg).joinToString(" · ")
    if (cpuLine.isNotBlank()) lines += cpuLine

    // Line 2: distro · kernel <ver>
    val distro = distroValue(id).takeIf { it != DASH }
    val kernelSeg = id?.kernel?.takeIf { it.isNotBlank() }?.let { "kernel $it" }
    val distroLine = listOfNotNull(distro, kernelSeg).joinToString(" · ")
    if (distroLine.isNotBlank()) lines += distroLine else lines += DASH

    // Line 3: uptime
    val uptime = formatUptime(host.procStats?.systemUptimeSeconds).takeIf { it != DASH }
    if (uptime != null) lines += "Up $uptime"

    // Line 4: CPU load · temp · mem used/total
    val loadSeg = formatCpuLoad(host.live?.cpuLoadPercent).takeIf { it != DASH }?.let { "CPU $it" }
    val tempSeg = formatTemp(host.live?.cpuTemp ?: host.procStats?.cpuTemp).takeIf { it != DASH }
    val memSeg = formatMemoryUsedOverTotal(host.live?.memUsedKb, host.live?.memTotalKb).takeIf { it != DASH }?.let { "mem $it" }
    val liveLine = listOfNotNull(loadSeg, tempSeg, memSeg).joinToString(" · ")
    if (liveLine.isNotBlank()) lines += liveLine

    // Line 5: Klipper/Moonraker versions
    val klipperSeg = host.klipperVersion?.takeIf { it.isNotBlank() }?.let { "Klipper $it" }
    val moonrakerSeg = host.moonrakerVersion?.takeIf { it.isNotBlank() }?.let { "Moonraker $it" }
    val versionLine = listOfNotNull(klipperSeg, moonrakerSeg).joinToString(" · ")
    if (versionLine.isNotBlank()) lines += versionLine

    // Lines 6+: throttle conditions (Pi only; empty = omitted)
    lines += throttle

    return lines
}

/**
 * Returns the ordered plain-text lines for a **MCU** Focus detail block.
 *
 * Line contract (owner UAT spec):
 *  1. `Firmware <firmwareVersion>`   (omit if null)
 *  2. `<chip> · <clock MHz>`         (each segment dropped if null; "—" if both absent)
 *  3. `<interfaceDesc>`              (omit if null)
 *  4. `Load <mcuAwake%>`             (omit if null)
 *  5. `<↑write KB ↓read KB> · <retransmits> retransmits`  (each part degrades independently)
 */
fun mcuDetailLines(mcu: McuDevice): List<String> {
    val lines = mutableListOf<String>()

    // Line 1: firmware
    val fw = mcu.firmwareVersion?.takeIf { it.isNotBlank() }
    if (fw != null) lines += "Firmware $fw"

    // Line 2: chip · clock
    val chipSeg = mcu.chip?.takeIf { it.isNotBlank() }
    val clockSeg = formatClock(mcu.clockHz).takeIf { it != DASH }
    val chipLine = listOfNotNull(chipSeg, clockSeg).joinToString(" · ")
    if (chipLine.isNotBlank()) lines += chipLine else lines += DASH

    // Line 3: interface
    val iface = mcu.interfaceDesc?.takeIf { it.isNotBlank() }
    if (iface != null) lines += iface

    // Line 4: load
    val load = formatLoad(mcu.mcuAwake).takeIf { it != DASH }
    if (load != null) lines += "Load $load"

    // Line 5: bandwidth · retransmits
    val bwSeg = formatBytes(mcu.bytesWrite, mcu.bytesRead).takeIf { it != DASH }
    val retranSeg = mcu.bytesRetransmit?.let { "${it} retransmits" }
    val bwLine = listOfNotNull(bwSeg, retranSeg).joinToString(" · ")
    if (bwLine.isNotBlank()) lines += bwLine

    return lines
}
