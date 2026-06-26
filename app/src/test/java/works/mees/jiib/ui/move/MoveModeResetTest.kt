package works.mees.jiib.ui.move

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveModeResetTest {
    @Test fun bookmark_resets_to_touchmove_when_unhomed() {
        assertEquals(MoveMode.TouchMove, moveModeAfterHomedChange(MoveMode.Bookmark("Front Left"), allHomed = false))
    }

    @Test fun savedialog_resets_to_touchmove_when_unhomed() {
        assertEquals(MoveMode.TouchMove, moveModeAfterHomedChange(MoveMode.SaveDialog, allHomed = false))
    }

    @Test fun bookmark_kept_when_homed() {
        val m = MoveMode.Bookmark("Front Left")
        assertEquals(m, moveModeAfterHomedChange(m, allHomed = true))
    }

    @Test fun savedialog_kept_when_homed() {
        assertEquals(MoveMode.SaveDialog, moveModeAfterHomedChange(MoveMode.SaveDialog, allHomed = true))
    }

    @Test fun motion_and_endstop_modes_never_reset_even_when_unhomed() {
        // Motion + Endstop modes are out of scope for this reset (pre-existing behavior). Reset must not touch them.
        assertEquals(MoveMode.TouchMove, moveModeAfterHomedChange(MoveMode.TouchMove, allHomed = false))
        assertEquals(MoveMode.Microstep, moveModeAfterHomedChange(MoveMode.Microstep, allHomed = false))
        assertEquals(MoveMode.XY, moveModeAfterHomedChange(MoveMode.XY, allHomed = false))
        assertEquals(MoveMode.Z, moveModeAfterHomedChange(MoveMode.Z, allHomed = false))
        assertEquals(MoveMode.Endstops, moveModeAfterHomedChange(MoveMode.Endstops, allHomed = false))
    }
}
