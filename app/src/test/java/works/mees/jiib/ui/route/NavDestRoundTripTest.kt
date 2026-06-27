package works.mees.jiib.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.ui.shell.parseStartDest

/**
 * Serialization round-trip coverage for all [NavDest] members, with explicit coverage for the
 * six calibration sub-routes added in Phase 27 (D-07).
 *
 * Uses `simpleName` round-trip via [parseStartDest] (the same mechanism that `StartDestMappingTest`
 * exercises generically for all [knownNavDests]). This test adds EXPLICIT assertions so that if a
 * calibration dest is accidentally omitted from [knownNavDests] or mislabelled, this test fails
 * with a named assertion rather than a silent omission.
 *
 * Pure JVM — no NavHost, no NavController, no Android runtime.
 */
class NavDestRoundTripTest {

    // ---------------------------------------------------------------------------
    // Original non-calibration dests (smoke — these worked before D-07)
    // ---------------------------------------------------------------------------

    @Test fun waterfallHome_roundTrips()  { assertRoundTrip(NavDest.WaterfallHome) }
    @Test fun temperature_roundTrips()   { assertRoundTrip(NavDest.Temperature) }
    @Test fun move_roundTrips()          { assertRoundTrip(NavDest.Move) }
    @Test fun extrude_roundTrips()       { assertRoundTrip(NavDest.Extrude) }
    @Test fun files_roundTrips()         { assertRoundTrip(NavDest.Files) }
    @Test fun macros_roundTrips()        { assertRoundTrip(NavDest.Macros) }
    @Test fun console_roundTrips()       { assertRoundTrip(NavDest.Console) }
    @Test fun fineTune_roundTrips()      { assertRoundTrip(NavDest.FineTune) }
    @Test fun webcam_roundTrips()        { assertRoundTrip(NavDest.Webcam) }
    @Test fun spool_roundTrips()         { assertRoundTrip(NavDest.Spool) }
    @Test fun outputs_roundTrips()       { assertRoundTrip(NavDest.Outputs) }
    @Test fun systemInfo_roundTrips()    { assertRoundTrip(NavDest.SystemInfo) }
    @Test fun theme_roundTrips()         { assertRoundTrip(NavDest.Theme) }

    // ---------------------------------------------------------------------------
    // Six calibration sub-routes (Phase 27, D-07) — explicit coverage
    // ---------------------------------------------------------------------------

    @Test fun calibrationHub_roundTrips()         { assertRoundTrip(NavDest.CalibrationHub) }
    @Test fun calibrationProbe_roundTrips()       { assertRoundTrip(NavDest.CalibrationProbe) }
    @Test fun calibrationBedMesh_roundTrips()     { assertRoundTrip(NavDest.CalibrationBedMesh) }
    @Test fun calibrationScrewsTilt_roundTrips()  { assertRoundTrip(NavDest.CalibrationScrewsTilt) }
    @Test fun calibrationZTilt_roundTrips()       { assertRoundTrip(NavDest.CalibrationZTilt) }
    @Test fun calibrationQgl_roundTrips()         { assertRoundTrip(NavDest.CalibrationQgl) }

    // ---------------------------------------------------------------------------
    // System page route (Phase 28, D-01)
    // ---------------------------------------------------------------------------

    /** NavDest.System must round-trip via parseStartDest (D-01, Phase 28). */
    @Test fun system_roundTrips()  { assertRoundTrip(NavDest.System) }

    /** NavDest.System must be in knownNavDests (count rises to 23). */
    @Test
    fun system_isInKnownNavDests() {
        assertTrue("NavDest.System must be in knownNavDests",
            NavDest.System in knownNavDests)
    }

    /** NavDest.System must NOT be in FOOT_GUN_DESTS — mid-print reachable (D-06). */
    @Test
    fun system_isNotInFootGunDests() {
        assertTrue("NavDest.System must NOT be in FOOT_GUN_DESTS (mid-print reachable, D-06)",
            NavDest.System !in FOOT_GUN_DESTS)
    }

    // ---------------------------------------------------------------------------
    // Settings-split routes (task 2.1)
    // ---------------------------------------------------------------------------

    @Test fun appSettings_roundTrips()     { assertRoundTrip(NavDest.AppSettings) }
    @Test fun printerSettings_roundTrips() { assertRoundTrip(NavDest.PrinterSettings) }
    @Test fun managePrinters_roundTrips()  { assertRoundTrip(NavDest.ManagePrinters) }

    // ---------------------------------------------------------------------------
    // All knownNavDests round-trip (completeness guard)
    // ---------------------------------------------------------------------------

    @Test
    fun allKnownNavDests_roundTrip() {
        for (dest in knownNavDests) {
            assertRoundTrip(dest)
        }
    }

    // ---------------------------------------------------------------------------
    // Probe sub-hub routes (Task 13)
    // ---------------------------------------------------------------------------

    @Test fun probeHub_roundTrips()              { assertRoundTrip(NavDest.ProbeHub) }
    @Test fun probeTest_roundTrips()             { assertRoundTrip(NavDest.ProbeTest) }
    @Test fun probeApplyBabystep_roundTrips()    { assertRoundTrip(NavDest.ProbeApplyBabystep) }
    @Test fun probeEddyCalibrate_roundTrips()    { assertRoundTrip(NavDest.ProbeEddyCalibrate) }
    @Test fun probeEddyTap_roundTrips()          { assertRoundTrip(NavDest.ProbeEddyTap) }
    @Test fun probeEddyDriveCurrent_roundTrips() { assertRoundTrip(NavDest.ProbeEddyDriveCurrent) }

    @Test
    fun probeHub_isInKnownNavDests() {
        assertTrue("NavDest.ProbeHub must be in knownNavDests", NavDest.ProbeHub in knownNavDests)
    }

    @Test
    fun probeToolDests_allInKnownNavDests() {
        val probeDests = listOf(
            NavDest.ProbeTest,
            NavDest.ProbeApplyBabystep,
            NavDest.ProbeEddyCalibrate,
            NavDest.ProbeEddyTap,
            NavDest.ProbeEddyDriveCurrent,
        )
        for (dest in probeDests) {
            assertTrue("$dest must be in knownNavDests", dest in knownNavDests)
        }
    }

    /** ProbeTool.toNavDest() must cover every ProbeTool entry exhaustively. */
    @Test
    fun probeToolToNavDest_coversAllEntries() {
        val mapped = works.mees.jiib.calibration.ProbeTool.entries.map { it.toNavDest() }.toSet()
        assertEquals("All 6 ProbeTool entries must map to a distinct NavDest", 6, mapped.size)
    }

    @Test
    fun probeToolToNavDest_zOffset_mapsToCalibrationProbe() {
        assertEquals(NavDest.CalibrationProbe,
            works.mees.jiib.calibration.ProbeTool.Z_OFFSET.toNavDest())
    }

    @Test
    fun probeToolToNavDest_probeTest_mapsToProbeTest() {
        assertEquals(NavDest.ProbeTest,
            works.mees.jiib.calibration.ProbeTool.PROBE_TEST.toNavDest())
    }

    @Test
    fun probeToolToNavDest_applyBabystep_mapsToProbeApplyBabystep() {
        assertEquals(NavDest.ProbeApplyBabystep,
            works.mees.jiib.calibration.ProbeTool.APPLY_BABYSTEP.toNavDest())
    }

    @Test
    fun probeToolToNavDest_eddyCalibrate_mapsToProbeEddyCalibrate() {
        assertEquals(NavDest.ProbeEddyCalibrate,
            works.mees.jiib.calibration.ProbeTool.EDDY_CALIBRATE.toNavDest())
    }

    @Test
    fun probeToolToNavDest_eddyTap_mapsToProbeEddyTap() {
        assertEquals(NavDest.ProbeEddyTap,
            works.mees.jiib.calibration.ProbeTool.EDDY_TAP.toNavDest())
    }

    @Test
    fun probeToolToNavDest_eddyDriveCurrent_mapsToProbeEddyDriveCurrent() {
        assertEquals(NavDest.ProbeEddyDriveCurrent,
            works.mees.jiib.calibration.ProbeTool.EDDY_DRIVE_CURRENT.toNavDest())
    }

    // ---------------------------------------------------------------------------
    // D-07 CalibrationRoutine → NavDest mapping helper
    // ---------------------------------------------------------------------------

    @Test
    fun calibrationRoutineToNavDest_coversAllRoutines() {
        // Verify the mapping helper covers every CalibrationRoutine
        val mapped = works.mees.jiib.calibration.CalibrationRoutine.values().map { it.toNavDest() }.toSet()
        assertEquals("All 5 CalibrationRoutines must map to a NavDest", 5, mapped.size)
    }

    @Test
    fun calibrationRoutineToNavDest_probe_mapsToProbeRoute() {
        assertEquals(NavDest.CalibrationProbe,
            works.mees.jiib.calibration.CalibrationRoutine.PROBE_CALIBRATE.toNavDest())
    }

    @Test
    fun calibrationRoutineToNavDest_bedMesh_mapsToBedMeshRoute() {
        assertEquals(NavDest.CalibrationBedMesh,
            works.mees.jiib.calibration.CalibrationRoutine.BED_MESH.toNavDest())
    }

    @Test
    fun calibrationRoutineToNavDest_screwsTilt_mapsToscrewsTiltRoute() {
        assertEquals(NavDest.CalibrationScrewsTilt,
            works.mees.jiib.calibration.CalibrationRoutine.SCREWS_TILT.toNavDest())
    }

    @Test
    fun calibrationRoutineToNavDest_zTilt_mapsToZTiltRoute() {
        assertEquals(NavDest.CalibrationZTilt,
            works.mees.jiib.calibration.CalibrationRoutine.Z_TILT.toNavDest())
    }

    @Test
    fun calibrationRoutineToNavDest_qgl_mapsToQglRoute() {
        assertEquals(NavDest.CalibrationQgl,
            works.mees.jiib.calibration.CalibrationRoutine.QUAD_GANTRY_LEVEL.toNavDest())
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun assertRoundTrip(dest: NavDest) {
        val name = dest::class.simpleName
        assertEquals("$dest must round-trip via parseStartDest", dest, parseStartDest(name))
    }
}
