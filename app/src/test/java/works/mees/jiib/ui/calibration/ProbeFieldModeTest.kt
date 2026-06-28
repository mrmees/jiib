package works.mees.jiib.ui.calibration

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.calibration.ProbePageState
import works.mees.jiib.calibration.ProbeTool

class ProbeFieldModeTest {

    @Test fun `field shows control rows only when Z-Offset session is Active`() {
        assertEquals(ProbeFieldMode.Z_CONTROL_ROWS, probeFieldMode(ProbeTool.Z_OFFSET, ProbePageState.Active))
    }

    @Test fun `field stays tool list while Z-Offset is Idle or Accepted`() {
        assertEquals(ProbeFieldMode.TOOL_LIST, probeFieldMode(ProbeTool.Z_OFFSET, ProbePageState.Idle))
        assertEquals(ProbeFieldMode.TOOL_LIST, probeFieldMode(ProbeTool.Z_OFFSET, ProbePageState.Accepted))
    }

    @Test fun `an Eddy or null-owner session never trips the Z morph`() {
        assertEquals(ProbeFieldMode.TOOL_LIST, probeFieldMode(ProbeTool.EDDY_CALIBRATE, ProbePageState.Active))
        assertEquals(ProbeFieldMode.TOOL_LIST, probeFieldMode(null, ProbePageState.Active))
    }

    @Test fun `foot shows Abort during Z Active, empty for other sessions, Back otherwise`() {
        assertEquals(ProbeFootMode.ABORT,
            probeFootMode(ProbeTool.Z_OFFSET, ProbePageState.Active, anySessionActive = true))
        assertEquals(ProbeFootMode.NONE,
            probeFootMode(ProbeTool.EDDY_CALIBRATE, ProbePageState.Active, anySessionActive = true))
        assertEquals(ProbeFootMode.NONE,
            probeFootMode(ProbeTool.Z_OFFSET, ProbePageState.Idle, anySessionActive = true))
        assertEquals(ProbeFootMode.BACK,
            probeFootMode(null, ProbePageState.Idle, anySessionActive = false))
    }
}
