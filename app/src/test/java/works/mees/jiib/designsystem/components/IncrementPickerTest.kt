package works.mees.jiib.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Host tests for [formatStep] — the pure formatting function extracted from [IncrementPicker].
 *
 * These tests encode the contract from 26-01 behavior block:
 *  - Whole numbers drop the .0 suffix and get a ± prefix.
 *  - Decimal values strip trailing zeros and get a ± prefix.
 *  - No locale grouping separators (all values fit on one screen at any size).
 *
 * RED phase: tests are RED with the Task-1 placeholder body (returns "") and flip GREEN
 * once Task 2 replaces the body with the real implementation.
 */
class IncrementPickerTest {

    /** 5.0 is a whole number — should render as "±5", not "±5.0". */
    @Test
    fun formatStep_wholeNumber_5() {
        assertEquals("±5", formatStep(5.0))
    }

    /** 0.001 is a small decimal with no trailing zeros — should render exactly as "±0.001". */
    @Test
    fun formatStep_smallDecimal_pa() {
        assertEquals("±0.001", formatStep(0.001))
    }

    /** 10.0 is a whole number — should render as "±10", not "±10.0". */
    @Test
    fun formatStep_wholeNumber_10() {
        assertEquals("±10", formatStep(10.0))
    }

    /** 0.05 has a trailing zero after stripping — should render as "±0.05", not "±0.050". */
    @Test
    fun formatStep_decimalNoTrailingZero() {
        assertEquals("±0.05", formatStep(0.05))
    }
}
