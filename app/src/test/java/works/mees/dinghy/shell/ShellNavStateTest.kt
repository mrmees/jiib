package works.mees.dinghy.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.ui.route.NavDest
import works.mees.dinghy.ui.route.knownNavDests
import works.mees.dinghy.ui.shell.ShellNavState

/**
 * Host-pure proof of [ShellNavState] behavior after the Phase-24-03 migration to Navigation-Compose.
 *
 * NOTE: The hand-rolled `navigateTo`/`goBack`/`dest`/`backStack` router has been REMOVED from
 * [ShellNavState] — Navigation-Compose's [NavHost] now owns the drill-down back-stack. These tests
 * now verify the in-screen sub-nav state that [ShellNavState] still carries:
 *   - [ShellNavState.applyEntryReset] clears per-dest sub-nav on entry
 *   - [ShellNavState.resetTransient] clears camera/scan/prefilter transient state
 *   - [ShellNavState.startDest] seed is stored and readable
 *   - The three IA destinations (AppSettings/PrinterSettings/About) are distinct [NavDest] members
 *     (task 7.1: old Settings + Devices routes retired; replaced by AppSettings + PrinterSettings)
 */
class ShellNavStateTest {

    /**
     * The three post-split IA destinations (AppSettings, PrinterSettings, About) are DISTINCT
     * [NavDest] values in [knownNavDests]. Old Settings + Devices were removed in task 7.1.
     */
    @Test
    fun ia_destinations_are_distinct_known_dests() {
        val ia = listOf(NavDest.AppSettings, NavDest.PrinterSettings, NavDest.About)

        // They are three DISTINCT values (no two tiles collide on one dest).
        assertEquals("the IA dests must be distinct", 3, ia.toSet().size)

        // All three are in knownNavDests after the task-7.1 cleanup.
        for (dest in ia) {
            assertTrue("$dest must be in knownNavDests", dest in knownNavDests)
        }
    }

    /** applyEntryReset on Macros clears macroShowSystem and macroPopupFor. */
    @Test
    fun applyEntryReset_macros_clears_sub_nav() {
        val nav = ShellNavState()
        nav.macroShowSystem = true
        // macroPopupFor is null (no macro VMs in host tests); state is the toggle.
        nav.applyEntryReset(NavDest.Macros)
        assertFalse("Macros entry should reset macroShowSystem to false", nav.macroShowSystem)
        assertNull("Macros entry should clear macroPopupFor", nav.macroPopupFor)
    }

    /**
     * applyEntryReset on CalibrationHub is a no-op in the body (calibrationRoutine field was removed
     * in Phase 27 — the NavHost back-stack is now the single source of truth, D-07). The call must
     * not crash and must not perturb other sub-nav state.
     */
    @Test
    fun applyEntryReset_calibrationHub_noop_nocrash() {
        val nav = ShellNavState()
        nav.macroShowSystem = true
        // Verify calling applyEntryReset on CalibrationHub does not crash and does not clear macroShowSystem.
        nav.applyEntryReset(NavDest.CalibrationHub)
        assertTrue("CalibrationHub entry must not clear macroShowSystem", nav.macroShowSystem)
        assertNull("CalibrationHub entry must not set macroPopupFor", nav.macroPopupFor)
    }

    /**
     * applyEntryReset on a dest that has no sub-nav is a no-op (does not crash).
     * Note: fineTuneGroup was removed in 26-02 (Fine-Tune is now a flat single-screen, no sub-nav).
     * Note: calibrationRoutine was removed in 27-02 (NavHost back-stack owns calibration sub-nav, D-07).
     */
    @Test
    fun applyEntryReset_other_dest_noop() {
        val nav = ShellNavState()
        nav.macroShowSystem = true
        // Move has no applyEntryReset side-effects — macroShowSystem unchanged.
        nav.applyEntryReset(NavDest.Move)
        assertTrue("Move entry must not clear macroShowSystem", nav.macroShowSystem)
        assertNull("Move entry must not set macroPopupFor", nav.macroPopupFor)
    }

    /** resetTransient clears scanActive, macroPopupFor, and spoolPrefilter but NOT macroShowSystem. */
    @Test
    fun resetTransient_clears_transient_state_only() {
        val nav = ShellNavState()
        nav.scanActive = true
        nav.spoolPrefilter = works.mees.dinghy.ui.spool.SpoolPrefilterSeed(listOf("PLA"), emptyList())
        nav.macroShowSystem = true

        nav.resetTransient()

        assertFalse("resetTransient must clear scanActive", nav.scanActive)
        assertNull("resetTransient must clear macroPopupFor", nav.macroPopupFor)
        assertNull("resetTransient must clear spoolPrefilter", nav.spoolPrefilter)
        // Non-transient state is PRESERVED (G-A1 — user returns to their sub-nav state after recovery).
        // NOTE: calibrationRoutine was removed in Phase 27 — the NavHost back-stack owns that state.
        assertTrue("resetTransient must NOT clear macroShowSystem", nav.macroShowSystem)
    }

    /** startDest seed is stored and readable by AppShell. */
    @Test
    fun startDest_seed_is_stored() {
        val nav = ShellNavState(startDest = NavDest.FineTune)
        assertEquals("startDest must be stored", NavDest.FineTune, nav.startDest)
    }

    /** startDest is null when not seeded (release / gate-off path). */
    @Test
    fun startDest_null_when_not_seeded() {
        val nav = ShellNavState()
        assertNull("startDest must be null when not seeded (release default)", nav.startDest)
    }
}
