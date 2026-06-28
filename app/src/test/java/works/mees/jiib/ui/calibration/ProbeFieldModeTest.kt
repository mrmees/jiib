package works.mees.jiib.ui.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.jiib.calibration.ApplyBabystepVm
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

    @Test fun `an orphan active session (no tracked owner) is adopted as Z_OFFSET`() {
        // Entering while manual_probe is already running (external start / app restart mid-session):
        // state Active but no owner → adopt as Z_OFFSET so the user gets controls + an exit, not just Accept.
        val adopted = orphanSessionAdoption(ProbePageState.Active, activeSessionTool = null)
        assertEquals(ProbeTool.Z_OFFSET, adopted)
        // After adoption the Field morphs to the control rows and the foot offers Abort (the exit the
        // user previously lacked) — the end-to-end fix for the "stuck with only Accept" bug.
        assertEquals(ProbeFieldMode.Z_CONTROL_ROWS, probeFieldMode(adopted, ProbePageState.Active))
        assertEquals(ProbeFootMode.ABORT, probeFootMode(adopted, ProbePageState.Active, anySessionActive = true))
    }

    @Test fun `tracked, eddy, or non-active sessions are not adopted`() {
        assertNull(orphanSessionAdoption(ProbePageState.Active, ProbeTool.Z_OFFSET))     // user-started Z
        assertNull(orphanSessionAdoption(ProbePageState.Active, ProbeTool.EDDY_CALIBRATE)) // user-started eddy
        assertNull(orphanSessionAdoption(ProbePageState.Idle, null))                     // no live session
        assertNull(orphanSessionAdoption(ProbePageState.Accepted, null))                 // already accepted
    }

    @Test fun `babystep morph engages only when selected + active + no session`() {
        assertEquals(
            ProbeFieldMode.BABYSTEP_CONTROL_ROWS,
            probeFieldMode(null, ProbePageState.Idle, ProbeTool.APPLY_BABYSTEP, babystepActive = true),
        )
        // not active → list
        assertEquals(
            ProbeFieldMode.TOOL_LIST,
            probeFieldMode(null, ProbePageState.Idle, ProbeTool.APPLY_BABYSTEP, babystepActive = false),
        )
        // different tool selected → list
        assertEquals(
            ProbeFieldMode.TOOL_LIST,
            probeFieldMode(null, ProbePageState.Idle, ProbeTool.PROBE_TEST, babystepActive = true),
        )
    }

    @Test fun `a live Z-Offset session wins over babystep active`() {
        // Even with babystepActive true, an active Z session shows the Z control rows.
        assertEquals(
            ProbeFieldMode.Z_CONTROL_ROWS,
            probeFieldMode(ProbeTool.Z_OFFSET, ProbePageState.Active, ProbeTool.APPLY_BABYSTEP, babystepActive = true),
        )
    }

    @Test fun `an orphan-owned session never shows babystep rows`() {
        // activeSessionTool != null (a tracked/eddy session) blocks the babystep branch.
        assertEquals(
            ProbeFieldMode.TOOL_LIST,
            probeFieldMode(ProbeTool.EDDY_CALIBRATE, ProbePageState.Active, ProbeTool.APPLY_BABYSTEP, babystepActive = true),
        )
    }

    @Test fun `a null-owner ACTIVE session (orphan, pre-adoption frame) never shows babystep rows`() {
        // First frame before LaunchedEffect adopts an external manual_probe session: owner still null but
        // zState already Active. The babystep branch must yield (a real session is mid-flight).
        assertEquals(
            ProbeFieldMode.TOOL_LIST,
            probeFieldMode(null, ProbePageState.Active, ProbeTool.APPLY_BABYSTEP, babystepActive = true),
        )
    }

    @Test fun `clear is enabled only for a non-zero live babystep`() {
        assertEquals(false, babystepClearEnabled(null))
        assertEquals(false, babystepClearEnabled(0.0))
        assertEquals(true, babystepClearEnabled(-0.06))
        assertEquals(true, babystepClearEnabled(0.10))
    }

    @Test fun `save is enabled only when canApply and not printing`() {
        val ready = ApplyBabystepVm(savedOffset = 2.04, liveBabystep = -0.06, newOffset = 2.10, canApply = true)
        val zero = ApplyBabystepVm(savedOffset = 2.04, liveBabystep = 0.0, newOffset = 2.04, canApply = false)
        assertEquals(true, babystepSaveEnabled(ready, isPrinting = false))
        assertEquals(false, babystepSaveEnabled(ready, isPrinting = true))   // print restart guard
        assertEquals(false, babystepSaveEnabled(zero, isPrinting = false))   // nothing to bake
    }
}
