package works.mees.jiib.ui.move

/** Bed motion extent in mm (from toolhead.axis_minimum/axis_maximum). May have negative mins. */
data class BedExtent(val xMin: Double, val xMax: Double, val yMin: Double, val yMax: Double) {
    val width: Double get() = xMax - xMin
    val height: Double get() = yMax - yMin
}

/** A screen rectangle in px. */
data class BedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class ScreenPoint(val x: Float, val y: Float)

/** Aspect-locked (no-stretch) bed rectangle centered inside a [boxW] x [boxH] px box. */
fun bedFitRect(bed: BedExtent, boxW: Float, boxH: Float): BedRect {
    val bedAspect = (bed.width / bed.height).toFloat()
    val boxAspect = boxW / boxH
    val (w, h) = if (boxAspect > bedAspect) {
        val hh = boxH
        val ww = hh * bedAspect
        ww to hh
    } else {
        val ww = boxW
        val hh = ww / bedAspect
        ww to hh
    }
    val left = (boxW - w) / 2f
    val top = (boxH - h) / 2f
    return BedRect(left, top, left + w, top + h)
}

/** Bed mm -> screen px. Bed +Y maps to screen-up (smaller y). */
fun bedToScreen(bed: BedExtent, rect: BedRect, x: Double, y: Double): ScreenPoint {
    val fx = ((x - bed.xMin) / bed.width).toFloat().coerceIn(0f, 1f)
    val fy = ((y - bed.yMin) / bed.height).toFloat().coerceIn(0f, 1f)
    return ScreenPoint(
        x = rect.left + fx * rect.width,
        y = rect.bottom - fy * rect.height,
    )
}

/** Screen px -> bed mm (inverse of [bedToScreen]); clamps to the bed extent. */
fun screenToBed(bed: BedExtent, rect: BedRect, px: Float, py: Float): Pair<Double, Double> {
    val fx = ((px - rect.left) / rect.width).coerceIn(0f, 1f)
    val fy = ((rect.bottom - py) / rect.height).coerceIn(0f, 1f)
    return (bed.xMin + fx * bed.width) to (bed.yMin + fy * bed.height)
}
