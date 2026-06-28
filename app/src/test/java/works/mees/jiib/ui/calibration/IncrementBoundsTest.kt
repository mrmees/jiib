package works.mees.jiib.ui.calibration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementBoundsTest {
    private val steps = listOf(0.01, 0.05, 0.1, 0.5)

    @Test fun `increment up disabled only at the largest step`() {
        assertTrue(incrementUpEnabled(0.01, steps))
        assertTrue(incrementUpEnabled(0.1, steps))
        assertFalse(incrementUpEnabled(0.5, steps))
    }

    @Test fun `increment down disabled only at the smallest step`() {
        assertTrue(incrementDownEnabled(0.5, steps))
        assertTrue(incrementDownEnabled(0.05, steps))
        assertFalse(incrementDownEnabled(0.01, steps))
    }

    @Test fun `unknown step treated as index 0 - up enabled, down disabled`() {
        assertTrue(incrementUpEnabled(99.0, steps))
        assertFalse(incrementDownEnabled(99.0, steps))
    }
}
