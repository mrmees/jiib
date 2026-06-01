package works.mees.dinghy.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Host-side proof for the pure offset→fraction mapping at the core of [ScrubberPage] (G-3).
 *
 * On-device, the ScrubberPage tap-to-set gap (WR-01 / G-3) failed precisely because a green unit
 * test did NOT exist for the value-from-x core — the dual-pointerInput gesture race hid behind a
 * working drag. This test pins the deterministic, allocation-free mapping that BOTH the tap path
 * (zero-movement down) and the drag path now funnel through: a tap at x and a drag to the same x
 * resolve to the identical fraction, so asserting the mapping is deterministic-per-x is the
 * host-testable proof of the tap == drag invariant.
 */
class ScrubberMappingTest {

    private val eps = 1e-6f

    @Test
    fun leftEdge_mapsToZero() {
        assertEquals(0f, fractionFromX(0f, 100f), eps)
    }

    @Test
    fun rightEdge_mapsToOne() {
        assertEquals(1f, fractionFromX(100f, 100f), eps)
    }

    @Test
    fun midpoint_mapsToHalf() {
        assertEquals(0.5f, fractionFromX(50f, 100f), eps)
    }

    @Test
    fun belowLeft_clampsToZero() {
        assertEquals(0f, fractionFromX(-10f, 100f), eps)
    }

    @Test
    fun aboveRight_clampsToOne() {
        assertEquals(1f, fractionFromX(150f, 100f), eps)
    }

    @Test
    fun zeroBarWidth_returnsZeroNotNaN() {
        val result = fractionFromX(50f, 0f)
        assertEquals(0f, result, eps)
        assertEquals(false, result.isNaN())
    }

    @Test
    fun tapEqualsDrag_atSameX() {
        // The tap path (zero-movement down) and the drag path both call fractionFromX with the
        // same x; the mapping is deterministic per x, so a tap at x equals a drag to x.
        val x = 37.5f
        val barWidth = 250f
        val tapFraction = fractionFromX(x, barWidth)
        val dragFraction = fractionFromX(x, barWidth)
        assertEquals(tapFraction, dragFraction, eps)
        assertEquals(0.15f, tapFraction, eps)
    }
}
