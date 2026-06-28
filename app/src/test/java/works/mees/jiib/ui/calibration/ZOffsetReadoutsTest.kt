package works.mees.jiib.ui.calibration

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.calibration.ProbeCalibrateVm
import works.mees.jiib.calibration.ProbePageState

class ZOffsetReadoutsTest {

    @Test fun `active readouts show negated saved, delta-from-saved current Z, and step`() {
        val vm = ProbeCalibrateVm(
            state = ProbePageState.Active,
            savedZOffset = 2.04,
            zPosition = 2.10,   // live macro-feedback Z
        )
        val r = zOffsetActiveReadouts(vm, step = 0.05)
        assertEquals("-2.040", r.saved)       // fmtZOffset(-saved), 3 decimals
        assertEquals("0.060", r.currentZ)      // fmtZOffset(zPosition - saved)
        assertEquals("0.05", r.increment)      // fmtStep(step) — trailing .0 dropped, 0.05 kept
    }

    @Test fun `missing values render the em-dash placeholder, not a crash`() {
        val vm = ProbeCalibrateVm(state = ProbePageState.Active, savedZOffset = null, zPosition = null)
        val r = zOffsetActiveReadouts(vm, step = 0.05)
        assertEquals("—", r.saved)
        assertEquals("—", r.currentZ)
    }
}
