package works.mees.dinghy.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * GREEN tests for the [clampFraction] helper extracted from [FillMeter] (23-05).
 *
 * FillMeter is a horizontal read-only fill bar (e.g. spool remaining weight). It accepts a
 * caller-supplied `fraction: Float` but must clamp it internally to 0f..1f so an out-of-range
 * value (negative remaining, or weight calculation rounding over 100%) never causes visual
 * overflow or a Compose `fillMaxWidth(fraction)` crash (fraction > 1f panics Compose).
 *
 * (23-PATTERNS.md §FillMeter API shape: `val clamped = fraction.coerceIn(0f, 1f)`)
 *
 * These tests assert the pure [clampFraction] helper, which has no Compose dependency and
 * can be exercised in a plain JUnit4 host test.
 */
class FillMeterTest {

    /**
     * A fraction below 0f (e.g. -0.5f, representing a data error) must clamp to 0f.
     * A Compose `fillMaxWidth(-0.5f)` would throw an IllegalArgumentException.
     */
    @Test
    fun fraction_clamps_below_zero_to_zero() {
        assertEquals(
            "clampFraction(-0.5f) should return 0f",
            0f,
            clampFraction(-0.5f),
            0f,
        )
    }

    /**
     * A fraction above 1f (e.g. 1.1f from rounding in weight-remaining calculation) must clamp
     * to 1f. Compose `fillMaxWidth(1.1f)` would throw an IllegalArgumentException.
     */
    @Test
    fun fraction_clamps_above_one_to_one() {
        assertEquals(
            "clampFraction(1.1f) should return 1f",
            1f,
            clampFraction(1.1f),
            0f,
        )
    }

    /**
     * A fraction within [0f, 1f] (e.g. 0.74f = 74% remaining) is returned unchanged.
     */
    @Test
    fun fraction_in_range_unchanged() {
        assertEquals(
            "clampFraction(0.74f) should return 0.74f unchanged",
            0.74f,
            clampFraction(0.74f),
            0.0001f,
        )
    }
}
