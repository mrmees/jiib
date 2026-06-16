package works.mees.dinghy.ui.printstatus

import kotlin.math.roundToInt

/** Integer print-% for the header (`PRINTING · NN%`), clamped 0..100. */
fun progressPercent(progress: Double): Int =
    (progress.coerceIn(0.0, 1.0) * 100).roundToInt()

/**
 * Condensed layer/height line, e.g. `"1.2/55mm · 5/220 layers"`. Graceful degrade:
 *  - [objectHeight] null → `"1.2mm"` (current Z only)
 *  - [currentZ] null → height clause is `"—"`
 *  - layers null (either) → the layers clause is dropped entirely (never `"—/— layers"`)
 *  - everything null → `"—"`.
 */
fun formatLayerHeight(
    currentZ: Double?,
    objectHeight: Double?,
    currentLayer: Int?,
    totalLayer: Int?,
): String {
    // Round to 1 decimal, but drop a trailing ".0" so a whole number reads "55" not "55.0".
    fun mm(v: Double): String {
        val r = (v * 10).roundToInt() / 10.0
        return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
    }
    val height = when {
        currentZ == null -> "—"
        objectHeight != null -> "${mm(currentZ)}/${mm(objectHeight)}mm"
        else -> "${mm(currentZ)}mm"
    }
    val layers = if (
        currentLayer != null && currentLayer > 0 &&
        totalLayer != null && totalLayer > 0
    ) "$currentLayer/$totalLayer layers" else null
    return if (layers != null) "$height · $layers" else height
}

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
