package works.mees.jiib.ui.calibration

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.calibration.ProbeCalibrateVm
import works.mees.jiib.calibration.ProbePageState

class ZOffsetReadoutsTest {

    @Test fun `negated readouts - current=-zPos, saved=-saved, difference=saved-zPos, step`() {
        val vm = ProbeCalibrateVm(state = ProbePageState.Active, savedZOffset = 2.04, zPosition = 2.10)
        val r = zOffsetActiveReadouts(vm, step = 0.05)
        assertEquals("-2.100", r.currentOffset)
        assertEquals("-2.040", r.saved)
        assertEquals("-0.060", r.difference)   // fmtZOffset(2.04 - 2.10)
        assertEquals("0.05", r.stepSize)
    }

    @Test fun `missing values render em-dash`() {
        val vm = ProbeCalibrateVm(state = ProbePageState.Active, savedZOffset = null, zPosition = null)
        val r = zOffsetActiveReadouts(vm, step = 0.05)
        assertEquals("—", r.currentOffset)
        assertEquals("—", r.saved)
        assertEquals("—", r.difference)
    }
}
