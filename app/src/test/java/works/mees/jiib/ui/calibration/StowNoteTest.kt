package works.mees.jiib.ui.calibration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.calibration.ProbePageState

class StowNoteTest {
    @Test fun `stow note shows only in Active before the first head move`() {
        assertTrue(showStowNote(ProbePageState.Active, probeMoved = false))
        assertFalse(showStowNote(ProbePageState.Active, probeMoved = true))
        assertFalse(showStowNote(ProbePageState.Idle, probeMoved = false))
        assertFalse(showStowNote(ProbePageState.Accepted, probeMoved = false))
    }
}
