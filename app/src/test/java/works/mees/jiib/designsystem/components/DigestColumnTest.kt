package works.mees.jiib.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestColumnTest {

    // Synthetic height model: total = scale * 100 per "row" — linear in scale.
    private fun linearRows(n: Int): (Float) -> Float = { scale -> n * 100f * scale }

    @Test
    fun fits_naturally_when_room() {
        val fit = digestFit(availablePx = 500f, totalHeightAt = linearRows(4), minScale = 0.6f)
        assertEquals(DigestFit.Natural, fit)
    }

    @Test
    fun shrinks_when_tight_but_above_the_floor() {
        val fit = digestFit(availablePx = 320f, totalHeightAt = linearRows(4), minScale = 0.6f)
        assertTrue(fit is DigestFit.Shrunk)
        val scale = (fit as DigestFit.Shrunk).scale
        // 4 rows * 100 * scale <= 320  →  first passing 0.05 step from 1.0 downward is 0.80
        assertEquals(0.80f, scale, 0.001f)
    }

    @Test
    fun scrolls_when_even_the_floor_does_not_fit() {
        val fit = digestFit(availablePx = 100f, totalHeightAt = linearRows(4), minScale = 0.6f)
        assertEquals(DigestFit.Scroll, fit)
    }

    @Test
    fun floor_scale_itself_still_counts_as_shrunk() {
        // available exactly at the floor's requirement: 4 * 100 * 0.6 = 240
        val fit = digestFit(availablePx = 240f, totalHeightAt = linearRows(4), minScale = 0.6f)
        assertEquals(DigestFit.Shrunk(0.6f), fit)
    }

    @Test
    fun non_linear_model_with_clamped_floors_is_honored() {
        // rows that stop shrinking (clamped at floor) below scale 0.8: height = max(scale, 0.8) * 400
        val clamped: (Float) -> Float = { scale -> maxOf(scale, 0.8f) * 400f }
        // available 320 = 0.8 * 400 → reachable exactly at the clamp
        assertEquals(DigestFit.Shrunk(0.8f), digestFit(320f, clamped, minScale = 0.6f))
        // available 300 < 320 → no scale in [0.6, 1.0] fits → scroll
        assertEquals(DigestFit.Scroll, digestFit(300f, clamped, minScale = 0.6f))
    }
}
