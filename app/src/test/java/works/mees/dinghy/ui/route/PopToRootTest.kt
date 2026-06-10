package works.mees.dinghy.ui.route

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

    @Test
    fun calibration_isFootGun_returnsTrue() {
        assertTrue(shouldPopToRoot(current = NavDest.Calibration, printActive = true))
        assertTrue(shouldPopToRoot(current = NavDest.Calibration, printActive = false))
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

    @Test
    fun footGunDests_containsExactlyThreeMembers() {
        assertEquals(3, FOOT_GUN_DESTS.size)
        assertTrue(NavDest.Move        in FOOT_GUN_DESTS)
        assertTrue(NavDest.Extrude     in FOOT_GUN_DESTS)
        assertTrue(NavDest.Calibration in FOOT_GUN_DESTS)
    }

    // Not importing assertEquals from JUnit Assert because it's the plain comparison flavor
    private fun assertEquals(expected: Int, actual: Int) {
        assertTrue("Expected $expected but was $actual", expected == actual)
    }
}
