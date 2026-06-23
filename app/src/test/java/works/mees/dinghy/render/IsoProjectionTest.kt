package works.mees.dinghy.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsoProjectionTest {
    private val amp = 0.6

    /** front bed edge (row 0 = minY) must land BELOW the rear edge (larger canvas-Y). */
    @Test
    fun front_edge_projects_below_rear_edge() {
        // flat mesh → all frac 0.5 → pure geometry. 2 rows × 2 cols over a square bed.
        val z = listOf(listOf(0.0, 0.0), listOf(0.0, 0.0))
        val p = IsoProjection.project(z, 0.0, 200.0, 0.0, 200.0, -1.0, 1.0, amp)
        // column i=0: front vertex (j=0) vs rear vertex (j=1)
        val front = p.isoY[0 * p.cols + 0]
        val rear = p.isoY[1 * p.cols + 0]
        assertTrue("front (minY) must be lower on screen (greater isoY) than rear: front=$front rear=$rear",
            front > rear)
    }

    /** non-square bed must NOT project the same as a square bed (aspect is honored, not squared off). */
    @Test
    fun aspect_is_honored() {
        val z = listOf(listOf(0.0, 0.0), listOf(0.0, 0.0))
        val wide = IsoProjection.project(z, 0.0, 300.0, 0.0, 150.0, -1.0, 1.0, amp)
        val square = IsoProjection.project(z, 0.0, 150.0, 0.0, 150.0, -1.0, 1.0, amp)
        var differs = false
        for (k in wide.isoX.indices) if (kotlin.math.abs(wide.isoX[k] - square.isoX[k]) > 1e-9) differs = true
        assertTrue("non-square bed projection must differ from square", differs)
    }

    /** frac==0.5 (midplane) contributes ZERO height; a peak rises (smaller isoY). */
    @Test
    fun midplane_is_zero_height_and_peaks_rise() {
        val flat = IsoProjection.project(
            listOf(listOf(0.0, 0.0), listOf(0.0, 0.0)), 0.0, 200.0, 0.0, 200.0, -1.0, 1.0, amp)
        // same grid, but vertex (j=1,i=1) is a full peak (z=hiZ → frac 1.0).
        val peaked = IsoProjection.project(
            listOf(listOf(0.0, 0.0), listOf(0.0, 1.0)), 0.0, 200.0, 0.0, 200.0, -1.0, 1.0, amp)
        val idx = 1 * flat.cols + 1
        // midplane vertices identical between the two projections everywhere EXCEPT the peak.
        assertEquals("flat vertex 0 unchanged", flat.isoY[0], peaked.isoY[0], 1e-9)
        assertTrue("peak rises: peaked isoY < flat isoY at peak vertex",
            peaked.isoY[idx] < flat.isoY[idx])
    }

    /** degenerate grids and bboxes must not divide by zero / produce NaN. */
    @Test
    fun degenerate_inputs_are_safe() {
        val single = IsoProjection.project(listOf(listOf(0.0)), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, amp)
        assertTrue(single.isoX.all { it.isFinite() } && single.isoY.all { it.isFinite() })
        val fit = IsoProjection.fitTransform(single.isoX, single.isoY, 100.0, 100.0, 4.0)
        assertTrue("scale finite", fit.scale.isFinite() && fit.scale > 0.0)
        assertTrue("offsets finite", fit.dx.isFinite() && fit.dy.isFinite())
    }

    /** fitTransform centers a normal lattice inside the box and stays within it. */
    @Test
    fun fit_centers_within_box() {
        val p = IsoProjection.project(
            listOf(listOf(0.0, 0.0), listOf(0.0, 0.0)), 0.0, 200.0, 0.0, 200.0, -1.0, 1.0, amp)
        val fit = IsoProjection.fitTransform(p.isoX, p.isoY, 100.0, 100.0, 4.0)
        for (k in p.isoX.indices) {
            val sx = p.isoX[k] * fit.scale + fit.dx
            val sy = p.isoY[k] * fit.scale + fit.dy
            assertTrue("x in box: $sx", sx in -0.001..100.001)
            assertTrue("y in box: $sy", sy in -0.001..100.001)
        }
    }
}
