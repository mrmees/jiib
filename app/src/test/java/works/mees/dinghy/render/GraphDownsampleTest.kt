package works.mees.dinghy.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-pure proof of the [GraphView.sanitize] input-edge + downsample contract (Pitfall 4). These
 * are the behaviors EVERY later live surface (Phase 4 Print Status, Phase 5 Temperature) inherits,
 * so they are pinned down HERE with pure Float/array assertions — no Robolectric, no Canvas. The
 * helper is extracted as a pure function precisely so this can run on the JVM without a View.
 */
class GraphDownsampleTest {

    /** A snapshot longer than the pixel width is capped to ≤ pixelWidth rendered vertices. */
    @Test
    fun longerThanPixelWidth_isCappedToPixelWidth() {
        val snapshot = FloatArray(1000) { it.toFloat() }
        val out = GraphView.sanitize(snapshot, pixelWidth = 100)
        assertEquals("capped to exactly the pixel budget", 100, out.size)
        // Endpoints preserved by the uniform-stride downsample.
        assertEquals(0f, out.first(), 0f)
        assertEquals(999f, out.last(), 0f)
    }

    /** A snapshot already within budget is returned untouched (no needless work). */
    @Test
    fun shorterThanPixelWidth_isReturnedIntact() {
        val snapshot = floatArrayOf(10f, 20f, 30f)
        val out = GraphView.sanitize(snapshot, pixelWidth = 100)
        assertEquals(3, out.size)
        assertEquals(10f, out[0], 0f)
        assertEquals(20f, out[1], 0f)
        assertEquals(30f, out[2], 0f)
    }

    /** NaN and ±Infinity samples are discarded BEFORE the draw path ever sees them. */
    @Test
    fun nanAndInfinity_areFilteredOut() {
        val snapshot = floatArrayOf(
            1f, Float.NaN, 2f, Float.POSITIVE_INFINITY, 3f, Float.NEGATIVE_INFINITY, 4f,
        )
        val out = GraphView.sanitize(snapshot, pixelWidth = 100)
        assertEquals("only the four finite samples survive", 4, out.size)
        out.forEach { assertTrue("no non-finite value escapes sanitize", it.isFinite()) }
        assertEquals(floatArrayOf(1f, 2f, 3f, 4f).toList(), out.toList())
    }

    /** An all-non-finite snapshot collapses to empty (→ the View draws nothing, no crash). */
    @Test
    fun allNonFinite_returnsEmpty() {
        val snapshot = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        val out = GraphView.sanitize(snapshot, pixelWidth = 100)
        assertEquals(0, out.size)
    }

    /** An empty input returns empty. */
    @Test
    fun emptyInput_returnsEmpty() {
        val out = GraphView.sanitize(FloatArray(0), pixelWidth = 100)
        assertEquals(0, out.size)
    }

    /** A non-positive pixel budget renders nothing (defensive; can't divide by zero width). */
    @Test
    fun nonPositivePixelWidth_returnsEmpty() {
        val out = GraphView.sanitize(floatArrayOf(1f, 2f, 3f), pixelWidth = 0)
        assertEquals(0, out.size)
    }

    /** A constant-value series survives intact — the cap/filter introduce no NaN on a zero range. */
    @Test
    fun constantSeries_survivesIntact_noNaN() {
        val snapshot = FloatArray(50) { 42f }
        val out = GraphView.sanitize(snapshot, pixelWidth = 100)
        assertEquals(50, out.size)
        out.forEach {
            assertEquals(42f, it, 0f)
            assertTrue(it.isFinite())
        }
    }

    /** A constant series LONGER than the budget is still capped, still all the constant, still finite. */
    @Test
    fun constantSeriesOverBudget_isCapped_stillConstantAndFinite() {
        val snapshot = FloatArray(500) { 7f }
        val out = GraphView.sanitize(snapshot, pixelWidth = 80)
        assertEquals(80, out.size)
        out.forEach {
            assertEquals(7f, it, 0f)
            assertTrue(it.isFinite())
        }
    }
}
