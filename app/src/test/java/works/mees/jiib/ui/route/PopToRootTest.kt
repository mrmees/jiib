package works.mees.jiib.ui.route

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-04 foot-gun pop-to-root coverage via the PURE [shouldPopToRoot] predicate (FIX-8).
 *
 * This test is in `src/test` (JVM, NO Android runtime) because [shouldPopToRoot] is a PURE
 * Kotlin predicate — it checks membership in [FOOT_GUN_DESTS] with no NavHost, no
 * TestNavHostController, no navigation-testing dependency, no Compose, and no I/O.
 *
 * Per [[dinghy-wave0-red-scaffold-compile.md]]: a test that only compiles but cannot run is
 * unverified. This test runs on the JVM and actually exercises the predicate (not a stub).
 *
 * NO imports from `androidx.navigation.testing`, `TestNavController`, or `NavController` —
 * grep the file for these and it returns zero (FIX-8 compliance).
 */
class PopToRootTest {

    // ---------------------------------------------------------------------------
    // Foot-gun destinations → shouldPopToRoot = true
    // ---------------------------------------------------------------------------

    @Test
    fun move_isFootGun_returnsTrue() {
        assertTrue(shouldPopToRoot(current = NavDest.Move, printActive = true))
        assertTrue(shouldPopToRoot(current = NavDest.Move, printActive = false))
    }

    @Test
    fun extrude_isFootGun_returnsTrue() {
        assertTrue(shouldPopToRoot(current = NavDest.Extrude, printActive = true))
        assertTrue(shouldPopToRoot(current = NavDest.Extrude, printActive = false))
    }

    // D-07 (Phase 27): Calibration split to 6 sub-routes — all are foot-gun (D-17).
    @Test
    fun calibrationHub_isFootGun_returnsTrue() {
        assertTrue(shouldPopToRoot(current = NavDest.CalibrationHub, printActive = true))
        assertTrue(shouldPopToRoot(current = NavDest.CalibrationHub, printActive = false))
    }

    @Test
    fun calibrationProbe_isFootGun_returnsTrue() {
        assertTrue(shouldPopToRoot(current = NavDest.CalibrationProbe, printActive = true))
        assertTrue(shouldPopToRoot(current = NavDest.CalibrationProbe, printActive = false))
    }

    @Test
    fun calibrationBedMesh_isFootGun_returnsTrue() {
        assertTrue(shouldPopToRoot(current = NavDest.CalibrationBedMesh, printActive = true))
        assertTrue(shouldPopToRoot(current = NavDest.CalibrationBedMesh, printActive = false))
    }

    @Test
    fun calibrationScrewsTilt_isFootGun_returnsTrue() {
        assertTrue(shouldPopToRoot(current = NavDest.CalibrationScrewsTilt, printActive = true))
        assertTrue(shouldPopToRoot(current = NavDest.CalibrationScrewsTilt, printActive = false))
    }

    @Test
    fun calibrationZTilt_isFootGun_returnsTrue() {
        assertTrue(shouldPopToRoot(current = NavDest.CalibrationZTilt, printActive = true))
        assertTrue(shouldPopToRoot(current = NavDest.CalibrationZTilt, printActive = false))
    }

    @Test
    fun calibrationQgl_isFootGun_returnsTrue() {
        assertTrue(shouldPopToRoot(current = NavDest.CalibrationQgl, printActive = true))
        assertTrue(shouldPopToRoot(current = NavDest.CalibrationQgl, printActive = false))
    }

    // ---------------------------------------------------------------------------
    // Valid mid-print destinations → shouldPopToRoot = false
    // ---------------------------------------------------------------------------

    @Test
    fun temperature_notFootGun_returnsFalse() {
        assertFalse(shouldPopToRoot(current = NavDest.Temperature, printActive = true))
        assertFalse(shouldPopToRoot(current = NavDest.Temperature, printActive = false))
    }

    @Test
    fun macros_notFootGun_returnsFalse() {
        assertFalse(shouldPopToRoot(current = NavDest.Macros, printActive = true))
        assertFalse(shouldPopToRoot(current = NavDest.Macros, printActive = false))
    }

    @Test
    fun fineTune_notFootGun_returnsFalse() {
        assertFalse(shouldPopToRoot(current = NavDest.FineTune, printActive = true))
        assertFalse(shouldPopToRoot(current = NavDest.FineTune, printActive = false))
    }

    @Test
    fun console_notFootGun_returnsFalse() {
        assertFalse(shouldPopToRoot(current = NavDest.Console, printActive = true))
        assertFalse(shouldPopToRoot(current = NavDest.Console, printActive = false))
    }

    @Test
    fun webcam_notFootGun_returnsFalse() {
        assertFalse(shouldPopToRoot(current = NavDest.Webcam, printActive = true))
        assertFalse(shouldPopToRoot(current = NavDest.Webcam, printActive = false))
    }

    @Test
    fun waterfallHome_notFootGun_returnsFalse() {
        assertFalse(shouldPopToRoot(current = NavDest.WaterfallHome, printActive = true))
        assertFalse(shouldPopToRoot(current = NavDest.WaterfallHome, printActive = false))
    }

    // ---------------------------------------------------------------------------
    // null current → shouldPopToRoot = false
    // ---------------------------------------------------------------------------

    @Test
    fun nullCurrent_returnsFalse() {
        assertFalse("null current (at root) must not pop", shouldPopToRoot(current = null, printActive = true))
        assertFalse("null current (at root) must not pop", shouldPopToRoot(current = null, printActive = false))
    }

    // ---------------------------------------------------------------------------
    // FOOT_GUN_DESTS set membership sanity check
    // ---------------------------------------------------------------------------

    // D-07 (Phase 27): FOOT_GUN_DESTS expanded from 3 to 8 members (Move + Extrude + 6 CalibrationXxx).
    // Task 13: further expanded to 14 members (+ ProbeHub + 5 ProbeXxx tool dests).
    @Test
    fun footGunDests_containsExactlyFourteenMembers() {
        assertEquals(14, FOOT_GUN_DESTS.size)
        assertTrue(NavDest.Move                  in FOOT_GUN_DESTS)
        assertTrue(NavDest.Extrude               in FOOT_GUN_DESTS)
        assertTrue(NavDest.CalibrationHub        in FOOT_GUN_DESTS)
        assertTrue(NavDest.CalibrationProbe      in FOOT_GUN_DESTS)
        assertTrue(NavDest.CalibrationBedMesh    in FOOT_GUN_DESTS)
        assertTrue(NavDest.CalibrationScrewsTilt in FOOT_GUN_DESTS)
        assertTrue(NavDest.CalibrationZTilt      in FOOT_GUN_DESTS)
        assertTrue(NavDest.CalibrationQgl        in FOOT_GUN_DESTS)
        assertTrue(NavDest.ProbeHub              in FOOT_GUN_DESTS)
        assertTrue(NavDest.ProbeTest             in FOOT_GUN_DESTS)
        assertTrue(NavDest.ProbeApplyBabystep    in FOOT_GUN_DESTS)
        assertTrue(NavDest.ProbeEddyCalibrate    in FOOT_GUN_DESTS)
        assertTrue(NavDest.ProbeEddyTap          in FOOT_GUN_DESTS)
        assertTrue(NavDest.ProbeEddyDriveCurrent in FOOT_GUN_DESTS)
    }

    // Not importing assertEquals from JUnit Assert because it's the plain comparison flavor
    private fun assertEquals(expected: Int, actual: Int) {
        assertTrue("Expected $expected but was $actual", expected == actual)
    }
}
