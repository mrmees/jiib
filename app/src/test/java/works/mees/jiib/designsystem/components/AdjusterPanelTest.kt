package works.mees.jiib.designsystem.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host tests for [shouldShowBaseline] — the pure predicate extracted from [AdjusterPanel].
 *
 * These tests encode the contract from 26-01 behavior block:
 *  - Returns true only when both value and baseline are non-null AND they differ at [decimalPrecision].
 *  - Returns false when either is null.
 *  - Uses rounding to [decimalPrecision] decimal places for the comparison (avoids floating-point noise).
 *
 * RED phase: tests are RED with the Task-1 placeholder body (returns false) and flip GREEN
 * once Task 3 replaces the body with the real implementation.
 */
class AdjusterPanelTest {

    /** Value differs from baseline — should show the baseline label. */
    @Test
    fun shouldShowBaseline_valueDiffers_true() {
        assertTrue(
            "105.0 vs 100.0 at 0dp should show baseline",
            shouldShowBaseline(105.0, 100.0, 0),
        )
    }

    /** Value equals baseline — should NOT show the baseline label. */
    @Test
    fun shouldShowBaseline_valueEqualsBaseline_false() {
        assertFalse(
            "100.0 vs 100.0 at 0dp should not show baseline",
            shouldShowBaseline(100.0, 100.0, 0),
        )
    }

    /** Null value — should NOT show the baseline label. */
    @Test
    fun shouldShowBaseline_nullValue_false() {
        assertFalse(
            "null value should not show baseline",
            shouldShowBaseline(null, 100.0, 0),
        )
    }

    /** Null baseline — should NOT show the baseline label. */
    @Test
    fun shouldShowBaseline_nullBaseline_false() {
        assertFalse(
            "null baseline should not show baseline",
            shouldShowBaseline(100.0, null, 0),
        )
    }

    /**
     * Values that are equal when rounded to 3 decimal places — should NOT show the baseline label.
     * 0.2001 rounded to 3dp = 0.200; 0.200 rounded to 3dp = 0.200 — they are equal.
     */
    @Test
    fun shouldShowBaseline_roundsEqualAt3dp_false() {
        assertFalse(
            "0.2001 vs 0.200 at 3dp should NOT show baseline (rounds equal)",
            shouldShowBaseline(0.2001, 0.200, 3),
        )
    }

    /**
     * Values that differ even when rounded to 3 decimal places — should show the baseline label.
     * 0.205 rounded to 3dp = 0.205; 0.200 rounded to 3dp = 0.200 — they differ.
     */
    @Test
    fun shouldShowBaseline_differsAt3dp_true() {
        assertTrue(
            "0.205 vs 0.200 at 3dp should show baseline (differs when rounded)",
            shouldShowBaseline(0.205, 0.200, 3),
        )
    }
}
