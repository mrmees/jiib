package works.mees.jiib.ui.move

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveAvailabilityTest {
    private fun rows(x: Boolean, y: Boolean, z: Boolean): MoveRowAvailability =
        moveRowAvailability(xHomed = x, yHomed = y, zHomed = z)

    @Test fun homeAll_alwaysShown() {
        assertTrue(rows(true, true, true).homeAll)
        assertTrue(rows(false, false, false).homeAll)
    }
    @Test fun homeXY_onlyWhenXyUnhomed() {
        assertTrue(rows(false, true, true).homeXY)
        assertFalse(rows(true, true, true).homeXY)
    }
    @Test fun homeZ_onlyWhenZUnhomed() {
        assertTrue(rows(true, true, false).homeZ)
        assertFalse(rows(true, true, true).homeZ)
    }
    @Test fun touchMove_onlyWhenAllHomed() {
        assertTrue(rows(true, true, true).touchMove)
        assertFalse(rows(true, true, false).touchMove)
    }
    @Test fun xy_requiresXY_z_requiresZ() {
        assertTrue(rows(true, true, false).xy)
        assertFalse(rows(true, false, false).xy)
        assertTrue(rows(false, false, true).z)
    }
    @Test fun microstep_anyHomed() {
        assertTrue(rows(false, false, true).microstep)
        assertFalse(rows(false, false, false).microstep)
    }
}
