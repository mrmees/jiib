package works.mees.dinghy.designsystem.components

import org.junit.Assert.fail
import org.junit.Test

/**
 * RED scaffold for FillMeter fraction clamping (Wave 0 / 23-01).
 *
 * FillMeter is a horizontal read-only fill bar (e.g. spool remaining weight). It accepts a
 * caller-supplied `fraction: Float` but must clamp it internally to 0f..1f so an out-of-range
 * value (negative remaining, or weight calculation rounding over 100%) never causes visual
 * overflow or a Compose `fillMaxWidth(fraction)` crash (fraction > 1f panics Compose).
 *
 * (23-PATTERNS.md §FillMeter API shape: `val clamped = fraction.coerceIn(0f, 1f)`)
 *
 * The bodies are fail() — 23-05 builds FillMeter and extracts a pure `clampFraction(f: Float)`
 * helper the tests can call without Compose, then turns these GREEN.
 *
 * ⚠ Wave-0 compile contract: NO references to `FillMeter` composable or any Compose symbol that
 * does not exist yet. Only JUnit4 + Kotlin standard imports are used.
 */
class FillMeterTest {

    /**
     * A fraction below 0f (e.g. -0.5f, representing a data error) must clamp to 0f.
     * A Compose `fillMaxWidth(-0.5f)` would throw an IllegalArgumentException.
     */
    @Test
    fun fraction_clamps_below_zero_to_zero() {
        fail("RED: implemented in 23-05 — clampFraction(-0.5f) == 0f")
    }

    /**
     * A fraction above 1f (e.g. 1.1f from rounding in weight-remaining calculation) must clamp
     * to 1f. Compose `fillMaxWidth(1.1f)` would throw an IllegalArgumentException.
     */
    @Test
    fun fraction_clamps_above_one_to_one() {
        fail("RED: implemented in 23-05 — clampFraction(1.1f) == 1f")
    }

    /**
     * A fraction within [0f, 1f] (e.g. 0.74f = 74% remaining) is returned unchanged.
     */
    @Test
    fun fraction_in_range_unchanged() {
        fail("RED: implemented in 23-05 — clampFraction(0.74f) == 0.74f")
    }
}
