package works.mees.jiib.render

/**
 * Pure, Android-free isometric projection for the bed-mesh 3D wireframe (ViewMode.ISO_WIREFRAME).
 * Host-testable (no Canvas/Context). [BedMeshHeatmapView] calls [project] once per cache build, then
 * [fitTransform] to scale the normalized lattice into the Focus box. Front bed edge (row 0 = minY)
 * maps to the BOTTOM of the box (front-at-bottom, matching the overhead heatmap). Height and color
 * share one mapping ([BedMeshHeatmapView.deviationToRamp]) so they always agree.
 */
object IsoProjection {
    private const val COS = 0.8660254037844387  // cos 30°
    private const val SIN = 0.5                  // sin 30°
    const val EPS = 1e-9

    /** Normalized (pre-fit) projection: one entry per vertex, row-major index `j*cols + i`. */
    class Projected(
        val isoX: DoubleArray, val isoY: DoubleArray, val frac: DoubleArray,
        val rows: Int, val cols: Int,
    )

    /** Uniform fit: screen coord = `iso * scale + d`. */
    class Fit(val scale: Double, val dx: Double, val dy: Double)

    fun project(
        z: List<List<Double>>,
        minX: Double, maxX: Double, minY: Double, maxY: Double,
        loZ: Double, hiZ: Double, heightAmp: Double,
    ): Projected {
        val rows = z.size
        val cols = if (rows == 0) 0 else z[0].size
        val n = rows * cols
        val ix = DoubleArray(n); val iy = DoubleArray(n); val fr = DoubleArray(n)
        val cx = (minX + maxX) / 2.0
        val cy = (minY + maxY) / 2.0
        val halfSpan = (maxOf(maxX - minX, maxY - minY) / 2.0).let { if (it <= EPS) 0.5 else it }
        for (j in 0 until rows) {
            val y = if (rows == 1) cy else lerp(minY, maxY, j.toDouble() / (rows - 1))
            val gyView = (cy - y) / halfSpan   // INVERTED Y: minY → +gyView → BOTTOM of box
            val rowVals = z[j]
            for (i in 0 until cols) {
                val x = if (cols == 1) cx else lerp(minX, maxX, i.toDouble() / (cols - 1))
                val gx = (x - cx) / halfSpan
                val zv = rowVals.getOrElse(i) { loZ }
                val f = BedMeshHeatmapView.deviationToRamp(zv, loZ, hiZ)
                val hNorm = f - 0.5
                val idx = j * cols + i
                ix[idx] = (gx - gyView) * COS
                iy[idx] = (gx + gyView) * SIN - hNorm * heightAmp
                fr[idx] = f
            }
        }
        return Projected(ix, iy, fr, rows, cols)
    }

    fun fitTransform(isoX: DoubleArray, isoY: DoubleArray, w: Double, h: Double, inset: Double): Fit {
        if (isoX.isEmpty()) return Fit(1.0, w / 2.0, h / 2.0)
        var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
        for (v in isoX) { if (v < minX) minX = v; if (v > maxX) maxX = v }
        for (v in isoY) { if (v < minY) minY = v; if (v > maxY) maxY = v }
        val availW = (w - 2 * inset).coerceAtLeast(1.0)
        val availH = (h - 2 * inset).coerceAtLeast(1.0)
        val bboxW = maxX - minX
        val bboxH = maxY - minY
        val sx = if (bboxW <= EPS) Double.POSITIVE_INFINITY else availW / bboxW
        val sy = if (bboxH <= EPS) Double.POSITIVE_INFINITY else availH / bboxH
        val scale = listOf(sx, sy).filter { it.isFinite() }.minOrNull() ?: 1.0
        val cxBox = (minX + maxX) / 2.0
        val cyBox = (minY + maxY) / 2.0
        return Fit(scale, w / 2.0 - cxBox * scale, h / 2.0 - cyBox * scale)
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
}
