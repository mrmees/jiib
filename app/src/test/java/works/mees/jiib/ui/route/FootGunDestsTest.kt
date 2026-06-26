package works.mees.jiib.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Explicit membership test for [FOOT_GUN_DESTS] (D-04, D-17, Phase 27).
 *
 * Per D-17: FOOT_GUN_DESTS must contain ALL SIX CalibrationXxx members (hub + five routines) so
 * pop-to-root on print start covers the WHOLE calibration sub-tree — not just the hub. A print
 * starting while the user is in any routine (Probe / BedMesh / ScrewsTilt / ZTilt / Qgl) will
 * pop them to WaterfallHome.
 *
 * Pure JVM — no NavHost, no NavController, no Android runtime.
 */
class FootGunDestsTest {

    @Test
    fun footGunDests_containsExactlyEightMembers() {
        assertEquals("FOOT_GUN_DESTS must have exactly 8 members (Move + Extrude + 6 CalibrationXxx)",
            8, FOOT_GUN_DESTS.size)
    }

    @Test fun move_isInFootGunDests()                { assertTrue(NavDest.Move in FOOT_GUN_DESTS) }
    @Test fun extrude_isInFootGunDests()             { assertTrue(NavDest.Extrude in FOOT_GUN_DESTS) }
    @Test fun calibrationHub_isInFootGunDests()      { assertTrue(NavDest.CalibrationHub in FOOT_GUN_DESTS) }
    @Test fun calibrationProbe_isInFootGunDests()    { assertTrue(NavDest.CalibrationProbe in FOOT_GUN_DESTS) }
    @Test fun calibrationBedMesh_isInFootGunDests()  { assertTrue(NavDest.CalibrationBedMesh in FOOT_GUN_DESTS) }
    @Test fun calibrationScrewsTilt_isInFootGunDests() { assertTrue(NavDest.CalibrationScrewsTilt in FOOT_GUN_DESTS) }
    @Test fun calibrationZTilt_isInFootGunDests()    { assertTrue(NavDest.CalibrationZTilt in FOOT_GUN_DESTS) }
    @Test fun calibrationQgl_isInFootGunDests()      { assertTrue(NavDest.CalibrationQgl in FOOT_GUN_DESTS) }

    /** Valid mid-print destinations must NOT be in FOOT_GUN_DESTS. */
    @Test
    fun midPrintDests_notInFootGunDests() {
        val midPrint = listOf(
            NavDest.WaterfallHome,
            NavDest.Temperature,
            NavDest.Files,
            NavDest.Macros,
            NavDest.Console,
            NavDest.FineTune,
            NavDest.Webcam,
            NavDest.Spool,
            NavDest.Outputs,
            NavDest.SystemInfo,
            NavDest.Theme,
            // Phase 28 D-01/D-06: System cluster is mid-print reachable — never foot-gun
            NavDest.System,
            // Settings-split routes (task 7.1): all mid-print reachable via System hub
            NavDest.AppSettings,
            NavDest.PrinterSettings,
            NavDest.ManagePrinters,
        )
        for (dest in midPrint) {
            assertTrue("$dest must NOT be in FOOT_GUN_DESTS", dest !in FOOT_GUN_DESTS)
        }
    }
}
