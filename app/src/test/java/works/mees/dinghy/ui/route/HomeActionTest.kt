package works.mees.dinghy.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-06 order + D-08 hide-rule coverage for [buildIdleActions].
 *
 * Tests the capability cases (all present, all absent, each individual gate) and the exact v1 D-06
 * destination order when all capabilities are present. Note: Macros is UNCONDITIONAL (no gate) — it
 * is the only entry point to the Macros screen, so it appears in every case.
 *
 * Pure JVM (no Compose, no Android runtime, no I/O) — mirrors [TopRouteTest] test style.
 */
class HomeActionTest {

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Extract the [NavDest] from each [HomeAction.Destination] in a list. */
    private fun List<HomeAction>.destinations(): List<NavDest> =
        filterIsInstance<HomeAction.Destination>().map { it.dest }

    /** Extract the destination set (for membership checks). */
    private fun List<HomeAction>.destSet(): Set<NavDest> = destinations().toSet()

    // ---------------------------------------------------------------------------
    // D-08: Individual capability gates
    // ---------------------------------------------------------------------------

    @Test
    fun spoolmanAbsent_hidesSpool() {
        val actions = buildIdleActions(
            spoolmanPresent = false,
            outputsPresent  = true,
            webcamEnabled   = true,
        )
        assertFalse("Spool must be absent when spoolman=false",
            NavDest.Spool in actions.destSet())
    }

    @Test
    fun spoolmanPresent_showsSpool() {
        val actions = buildIdleActions(
            spoolmanPresent = true,
            outputsPresent  = false,
            webcamEnabled   = false,
        )
        assertTrue("Spool must be present when spoolman=true",
            NavDest.Spool in actions.destSet())
    }

    @Test
    fun macrosAlwaysShown_evenWithoutBookmarks() {
        // The Macros row is unconditional — it is the ONLY entry point to the Macros screen, so it
        // must show even with zero bookmarked macros (the bookmark gate was removed).
        val actions = buildIdleActions(
            spoolmanPresent = false,
            outputsPresent  = false,
            webcamEnabled   = false,
        )
        assertTrue("Macros must be present even with no bookmarks",
            NavDest.Macros in actions.destSet())
    }

    @Test
    fun macrosShown_withAllCapabilitiesPresent() {
        val actions = buildIdleActions(
            spoolmanPresent = true,
            outputsPresent  = true,
            webcamEnabled   = true,
        )
        assertTrue("Macros must be present",
            NavDest.Macros in actions.destSet())
    }

    @Test
    fun outputsAbsent_hidesOutputs() {
        val actions = buildIdleActions(
            spoolmanPresent = true,
            outputsPresent  = false,
            webcamEnabled   = true,
        )
        assertFalse("Outputs must be absent when outputs=false",
            NavDest.Outputs in actions.destSet())
    }

    @Test
    fun outputsPresent_showsOutputs() {
        val actions = buildIdleActions(
            spoolmanPresent = false,
            outputsPresent  = true,
            webcamEnabled   = false,
        )
        assertTrue("Outputs must be present when outputs=true",
            NavDest.Outputs in actions.destSet())
    }

    @Test
    fun webcamDisabled_hidesWebcam() {
        val actions = buildIdleActions(
            spoolmanPresent = true,
            outputsPresent  = true,
            webcamEnabled   = false,
        )
        assertFalse("Webcam must be absent when webcam=false",
            NavDest.Webcam in actions.destSet())
    }

    @Test
    fun webcamEnabled_showsWebcam() {
        val actions = buildIdleActions(
            spoolmanPresent = false,
            outputsPresent  = false,
            webcamEnabled   = true,
        )
        assertTrue("Webcam must be present when webcam=true",
            NavDest.Webcam in actions.destSet())
    }

    // ---------------------------------------------------------------------------
    // All-present / all-absent
    // ---------------------------------------------------------------------------

    @Test
    fun allCapabilitiesPresent_fullIdleList() {
        val actions = buildIdleActions(
            spoolmanPresent = true,
            outputsPresent  = true,
            webcamEnabled   = true,
        )
        val dests = actions.destinations()
        // 28-03 (D-05): Temperature + Console + Fine-Tune rehomed to idle list (always-present).
        // Full list = 8 original + 3 new unconditional rows = 11.
        assertEquals("Full list must have 11 destination rows", 11, dests.size)
        assertTrue(NavDest.Spool          in dests)
        assertTrue(NavDest.Files          in dests)
        assertTrue(NavDest.Move           in dests)
        assertTrue(NavDest.Extrude        in dests)
        assertTrue(NavDest.Macros         in dests)
        assertTrue(NavDest.CalibrationHub in dests)
        assertTrue(NavDest.Temperature    in dests)
        assertTrue(NavDest.Console        in dests)
        assertTrue(NavDest.FineTune       in dests)
        assertTrue(NavDest.Outputs        in dests)
        assertTrue(NavDest.Webcam         in dests)
    }

    @Test
    fun allCapabilitiesAbsent_minimalIdleList() {
        val actions = buildIdleActions(
            spoolmanPresent = false,
            outputsPresent  = false,
            webcamEnabled   = false,
        )
        val dests = actions.destinations()
        // Always-present: Files, Move, Extrude, Macros, Calibration + Temperature, Console, Fine-Tune
        // (D-05 / D-08 — 8 rows; Macros is now unconditional, no bookmark gate).
        assertEquals("Minimal list must have 8 destination rows", 8, dests.size)
        assertTrue(NavDest.Files          in dests)
        assertTrue(NavDest.Move           in dests)
        assertTrue(NavDest.Extrude        in dests)
        assertTrue(NavDest.Macros         in dests)
        assertTrue(NavDest.CalibrationHub in dests)
        assertTrue(NavDest.Temperature    in dests)
        assertTrue(NavDest.Console        in dests)
        assertTrue(NavDest.FineTune       in dests)
        // None of the capability-gated rows:
        assertFalse(NavDest.Spool   in dests)
        assertFalse(NavDest.Outputs in dests)
        assertFalse(NavDest.Webcam  in dests)
    }

    // ---------------------------------------------------------------------------
    // D-06 order
    // ---------------------------------------------------------------------------

    /**
     * The v1 idle list order is LOCKED by the owner (D-05/D-06):
     * `Spool → Files → Move → Extrude → Macros → Calibration → Temperature → Console → Fine-Tune → Outputs → Webcam`
     *
     * 28-03 adds Temperature, Console, Fine-Tune after Calibration and before Outputs (D-05).
     * This test asserts the exact 11-row sequence when all capabilities are present.
     */
    @Test
    fun v1OrderIsPreserved() {
        val actions = buildIdleActions(
            spoolmanPresent = true,
            outputsPresent  = true,
            webcamEnabled   = true,
        )
        val dests = actions.destinations()

        assertEquals(
            "D-05/D-06 order must be Spool → Files → Move → Extrude → Macros → Calibration → Temperature → Console → Fine-Tune → Outputs → Webcam",
            listOf(
                NavDest.Spool,
                NavDest.Files,
                NavDest.Move,
                NavDest.Extrude,
                NavDest.Macros,
                NavDest.CalibrationHub,
                NavDest.Temperature,
                NavDest.Console,
                NavDest.FineTune,
                NavDest.Outputs,
                NavDest.Webcam,
            ),
            dests,
        )
    }

    // ---------------------------------------------------------------------------
    // Icon law: no raw ligature strings inside buildIdleActions
    // (structural check — every Destination must have a non-null icon)
    // ---------------------------------------------------------------------------

    @Test
    fun allDestinations_haveNonNullIcon() {
        val actions = buildIdleActions(
            spoolmanPresent = true,
            outputsPresent  = true,
            webcamEnabled   = true,
        )
        for (action in actions) {
            if (action is HomeAction.Destination) {
                // DinghyIcon is a data class — non-null by construction, but this explicitly
                // documents the icon-law contract in the test suite.
                assertTrue("Icon for ${action.dest} must not be the empty string alternate",
                    action.icon.alternate.isNotBlank())
            }
        }
    }
}
