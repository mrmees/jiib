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
 *   - The four IA destinations (Devices/Theme/Settings/About) remain distinct [NavDest] members
 */
class ShellNavStateTest {

    /**
     * The four IA destinations (Printers=[NavDest.Devices], Theme, Settings, About) are four
     * DISTINCT [NavDest] values in [knownNavDests] — the rename from "Printers" → [NavDest.Devices]
     * is lexical; no dangling reference exists.
     */
    @Test
    fun four_ia_destinations_are_distinct_known_dests() {
        val ia = listOf(NavDest.Devices, NavDest.Theme, NavDest.Settings, NavDest.About)

        // They are four DISTINCT values (no two tiles collide on one dest).
        assertEquals("the four IA dests must be distinct", 4, ia.toSet().size)

        // All four remain in knownNavDests after the Phase-24-03 migration.
        for (dest in ia) {
            assertTrue("$dest must remain in knownNavDests", dest in knownNavDests)
        }
    }

    /** The Devices→Printers rename is lexical: NavDest.Devices is the single kept constant. */
    @Test
    fun devices_rename_no_dangling_ref() {
        // NavDest.Devices remains a known dest (the drawer tile relabel "Printers" does not orphan it).
        assertTrue(
            "NavDest.Devices must remain in knownNavDests (the rename is the drawer LABEL only)",
            NavDest.Devices in knownNavDests,
        )
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

    /** applyEntryReset on Calibration clears calibrationRoutine. */
    @Test
    fun applyEntryReset_calibration_clears_sub_nav() {
        val nav = ShellNavState()
        nav.calibrationRoutine = works.mees.dinghy.calibration.CalibrationRoutine.BED_MESH
        nav.applyEntryReset(NavDest.Calibration)
        assertNull("Calibration entry should reset calibrationRoutine to null", nav.calibrationRoutine)
    }

    /** applyEntryReset on FineTune clears fineTuneGroup. */
    @Test
    fun applyEntryReset_finetune_clears_sub_nav() {
        val nav = ShellNavState()
        nav.fineTuneGroup = works.mees.dinghy.ui.finetune.FineTuneGroup.MOTION
        nav.applyEntryReset(NavDest.FineTune)
        assertNull("FineTune entry should reset fineTuneGroup to null", nav.fineTuneGroup)
    }

    /** applyEntryReset on a dest that has no sub-nav is a no-op (does not crash). */
    @Test
    fun applyEntryReset_other_dest_noop() {
        val nav = ShellNavState()
        nav.macroShowSystem = true
        nav.calibrationRoutine = works.mees.dinghy.calibration.CalibrationRoutine.BED_MESH
        nav.fineTuneGroup = works.mees.dinghy.ui.finetune.FineTuneGroup.MOTION
        // Move has no applyEntryReset side-effects — other sub-nav unchanged.
        nav.applyEntryReset(NavDest.Move)
        assertTrue("Move entry must not clear macroShowSystem", nav.macroShowSystem)
        assertEquals("Move entry must not clear calibrationRoutine",
            works.mees.dinghy.calibration.CalibrationRoutine.BED_MESH, nav.calibrationRoutine)
        assertEquals("Move entry must not clear fineTuneGroup",
            works.mees.dinghy.ui.finetune.FineTuneGroup.MOTION, nav.fineTuneGroup)
    }

    /** resetTransient clears scanActive, macroPopupFor, and spoolPrefilter but not calibrationRoutine. */
    @Test
    fun resetTransient_clears_transient_state_only() {
        val nav = ShellNavState()
        nav.scanActive = true
        nav.spoolPrefilter = works.mees.dinghy.ui.spool.SpoolPrefilterSeed(listOf("PLA"), emptyList())
        nav.calibrationRoutine = works.mees.dinghy.calibration.CalibrationRoutine.BED_MESH
        nav.macroShowSystem = true
        nav.fineTuneGroup = works.mees.dinghy.ui.finetune.FineTuneGroup.MOTION

        nav.resetTransient()

        assertFalse("resetTransient must clear scanActive", nav.scanActive)
        assertNull("resetTransient must clear macroPopupFor", nav.macroPopupFor)
        assertNull("resetTransient must clear spoolPrefilter", nav.spoolPrefilter)
        // Non-transient state is PRESERVED (G-A1 — user returns to their sub-nav state after recovery).
        assertEquals("resetTransient must NOT clear calibrationRoutine",
            works.mees.dinghy.calibration.CalibrationRoutine.BED_MESH, nav.calibrationRoutine)
        assertTrue("resetTransient must NOT clear macroShowSystem", nav.macroShowSystem)
        assertEquals("resetTransient must NOT clear fineTuneGroup",
            works.mees.dinghy.ui.finetune.FineTuneGroup.MOTION, nav.fineTuneGroup)
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
