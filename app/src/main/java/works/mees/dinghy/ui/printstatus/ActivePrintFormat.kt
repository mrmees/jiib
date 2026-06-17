package works.mees.dinghy.ui.printstatus

import kotlin.math.roundToInt
import works.mees.dinghy.state.HeaterState

/** Integer print-% for the header (`PRINTING · NN%`), clamped 0..100. */
fun progressPercent(progress: Double): Int =
    (progress.coerceIn(0.0, 1.0) * 100).roundToInt()

/**
 * Derive the current layer from the print height when the slicer didn't emit
 * `print_stats.info.current_layer` (it is null). Mirrors Fluidd/Mainsail:
 *   `floor((z − firstLayerHeight) / layerHeight) + 1`, clamped to `[1, totalLayer]`.
 * Returns null when the inputs needed aren't available ([currentZ]/[layerHeight] missing or
 * [layerHeight] ≤ 0). [firstLayerHeight] falls back to [layerHeight] when absent.
 */
fun deriveCurrentLayer(
    currentZ: Double?,
    firstLayerHeight: Double?,
    layerHeight: Double?,
    totalLayer: Int?,
): Int? {
    if (currentZ == null || layerHeight == null || layerHeight <= 0.0) return null
    val flh = firstLayerHeight ?: layerHeight
    val raw = kotlin.math.floor((currentZ - flh) / layerHeight).toInt() + 1
    val low = raw.coerceAtLeast(1)
    val boundedTotal = totalLayer?.takeIf { it > 0 }
    return if (boundedTotal != null) low.coerceAtMost(boundedTotal) else low
}

/** The print filename's basename (leading directory stripped); extension retained. */
fun printFileBasename(filename: String): String = filename.substringAfterLast('/')

/** Compact print duration: `1h05m` (≥ 1h, minutes zero-padded), `45m` (≥ 1m), `30s` (< 1m). Negatives → 0. */
fun formatPrintDuration(seconds: Double): String {
    val s = seconds.coerceAtLeast(0.0).toInt()
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 -> "${h}h${m.toString().padStart(2, '0')}m"
        m > 0 -> "${m}m"
        else -> "${s % 60}s"
    }
}

/** `elapsed / estimate` print-time line; no/zero slicer estimate → elapsed alone. */
fun formatPrintVsEstimate(printDuration: Double, estimatedTime: Double?): String =
    if (estimatedTime != null && estimatedTime > 0.0) {
        "${formatPrintDuration(printDuration)} / ${formatPrintDuration(estimatedTime)}"
    } else {
        formatPrintDuration(printDuration)
    }

/**
 * All configured heaters (active OR cold — owner ruling: users must see a cold hot end), current temp
 * only, canonical order, `·`-joined: `"Extruder 230 · Bed 75"`. Empty map → "".
 */
fun formatHeatersLine(heaters: Map<String, HeaterState>): String =
    orderedHeaterKeys(heaters).mapNotNull { key ->
        heaters[key]?.let { "${prettyHeaterLabel(key)} ${it.temperature.roundToInt()}" }
    }.joinToString(" · ")

/**
 * Z-height line, ALWAYS 2 decimals so the value never changes width as Z climbs (no resize jitter):
 * `"1.20 / 55.00 mm"`, `"1.20 mm"` (no total height), `"—"` (no current Z).
 */
fun formatZHeight(currentZ: Double?, objectHeight: Double?): String {
    fun mm(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)
    return when {
        currentZ == null -> "—"
        objectHeight != null -> "${mm(currentZ)} / ${mm(objectHeight)} mm"
        else -> "${mm(currentZ)} mm"
    }
}

/**
 * Filament used / total in METERS, 1 decimal: `"5.2 / 12.3 m"`. Total null/≤0 → used alone (`"5.2 m"`).
 * Inputs are millimetres (live `print_stats.filament_used` and slicer `filament_total`).
 */
fun formatFilament(usedMm: Double, totalMm: Double?): String {
    fun m(v: Double): String = String.format(java.util.Locale.US, "%.1f", v / 1000.0)
    return if (totalMm != null && totalMm > 0.0) "${m(usedMm)} / ${m(totalMm)} m" else "${m(usedMm)} m"
}

/** Layers line: `"5 / 220 layers"`, or null when either bound is missing/≤ 0 (caller drops the line). */
fun formatLayersLine(currentLayer: Int?, totalLayer: Int?): String? =
    if (currentLayer != null && currentLayer > 0 && totalLayer != null && totalLayer > 0) {
        "$currentLayer / $totalLayer layers"
    } else {
        null
    }
