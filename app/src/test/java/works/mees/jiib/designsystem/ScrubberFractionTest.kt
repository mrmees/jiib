package works.mees.jiib.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubberFractionTest {
    @Test
    fun fractionFromY_bottomIsZero_topIsOne() {
        assertEquals(0f, fractionFromY(100f, 100f), 0.0001f)
        assertEquals(1f, fractionFromY(0f, 100f), 0.0001f)
        assertEquals(0.5f, fractionFromY(50f, 100f), 0.0001f)
    }

    @Test
    fun fractionFromY_clampsOutOfBounds() {
        assertEquals(1f, fractionFromY(-20f, 100f), 0.0001f)
        assertEquals(0f, fractionFromY(140f, 100f), 0.0001f)
    }
}
