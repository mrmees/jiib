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
    val layers = if (currentLayer != null && totalLayer != null) "$currentLayer/$totalLayer layers" else null
    return if (layers != null) "$height · $layers" else height
}

/** The print filename's basename (leading directory stripped); extension retained. */
fun printFileBasename(filename: String): String = filename.substringAfterLast('/')
