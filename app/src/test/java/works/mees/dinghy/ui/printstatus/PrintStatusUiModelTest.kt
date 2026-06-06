package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

/**
 * The 16-06 host gate: the pure [uiModel] mode→layout/control derivation. Covers each mode's gutter
 * set, the curated Standby launcher order (incl. the spool/macros conditionals + always-last flexible
 * Drawer), the shortcut-vs-babystep Field row pick, and the Terminal error-line flag.
 */
class PrintStatusUiModelTest {

    private fun standby() = uiModel(PrintStatusMode.Standby, PrinterState(printState = PrintState.Standby))

    // ---- Standby ----------------------------------------------------------------------------------

    @Test
    fun standby_gutterIsPreheatPlusInertPower_noEStop() {
        val m = standby()
        assertEquals(listOf("Preheat", "Power"), m.gutter.map { it.label })
        assertEquals(PrintStatusControlAction.Preheat, m.gutter[0].tapAction)
        assertEquals(PrintStatusControlAction.Power, m.gutter[1].tapAction)
        // Power is inert (D-04) — rendered but disabled.
        assertFalse(m.gutter[1].enabled)
        // No E-Stop in Standby.
        assertNull(m.gutter.firstOrNull { it.tapAction == PrintStatusControlAction.EmergencyStop })
    }

    @Test
    fun standby_launcherIsTheFixedCuratedOrder_drawerAlwaysLast() {
        val m = standby() // no spool, no bookmarked macros
        assertEquals(
            listOf(
                LauncherDest.Files,
                LauncherDest.Temperature,
                LauncherDest.Move,
                LauncherDest.Extrude,
                LauncherDest.Calibration,
                LauncherDest.Console,
                LauncherDest.Drawer,
            ),
            m.launcherDests,
        )
        // Drawer is ALWAYS the last (flexible/growing) tile.
        assertEquals(LauncherDest.Drawer, m.launcherDests.last())
    }

    @Test
    fun standby_launcherIncludesSpoolWhenPresentAndMacrosWhenBookmarked_inFixedPositions() {
        val m = uiModel(
            PrintStatusMode.Standby,
            PrinterState(printState = PrintState.Standby),
            spoolmanPresent = true,
            hasBookmarkedMacros = true,
        )
        assertEquals(
            listOf(
                LauncherDest.Files,
                LauncherDest.Temperature,
                LauncherDest.Move,
                LauncherDest.Extrude,
                LauncherDest.Calibration,
                LauncherDest.Spool,
                LauncherDest.Macros,
                LauncherDest.Console,
                LauncherDest.Drawer,
            ),
            m.launcherDests,
        )
    }

    @Test
    fun standby_hasNoActiveFieldRow_andNoErrorArea() {
        val m = standby()
        assertEquals(PrintStatusFieldRow.None, m.activeRow)
        assertFalse(m.showErrorLines)
    }

    // ---- Printing ---------------------------------------------------------------------------------

    @Test
    fun printing_gutterIsPauseCancelEStop_andShortcutRowByDefault() {
        val m = uiModel(
            PrintStatusMode.Printing,
            PrinterState(printState = PrintState.Printing, printFilename = "cube.gcode"),
        )
        // Tune (flexible shortcut, disabled stub) + Pause + Stop (E-Stop) — the 16-02 set.
        assertEquals(listOf("Tune", "Pause", "Stop"), m.gutter.map { it.label })
        assertEquals(PrintStatusControlAction.PausePrint, m.gutter[1].tapAction)
        assertEquals(PrintStatusControlAction.EmergencyStop, m.gutter[2].tapAction)
        // No launcher mid-print; shortcut row is active (babystep window closed).
        assertTrue(m.launcherDests.isEmpty())
        assertEquals(PrintStatusFieldRow.Shortcut, m.activeRow)
    }

    @Test
    fun printing_babystepReplacesTheShortcutRowWhenInWindow() {
        val m = uiModel(
            PrintStatusMode.Printing,
            PrinterState(printState = PrintState.Printing, printFilename = "cube.gcode"),
            babystepVisible = true,
        )
        assertEquals(PrintStatusFieldRow.Babystep, m.activeRow)
    }

    // ---- Paused -----------------------------------------------------------------------------------

    @Test
    fun paused_gutterIsResumeCancel_noEStop() {
        val m = uiModel(
            PrintStatusMode.Paused,
            PrinterState(printState = PrintState.Paused, printFilename = "cube.gcode"),
        )
        assertEquals(PrintStatusControlAction.ResumePrint, m.gutter[1].tapAction)
        assertEquals("Cancel", m.gutter[2].label)
        assertNull(m.gutter.firstOrNull { it.tapAction == PrintStatusControlAction.EmergencyStop })
    }

    @Test
    fun paused_babystepReplacementStillAppliesInWindow() {
        val m = uiModel(
            PrintStatusMode.Paused,
            PrinterState(printState = PrintState.Paused, printFilename = "cube.gcode"),
            babystepVisible = true,
        )
        assertEquals(PrintStatusFieldRow.Babystep, m.activeRow)
    }

    // ---- Terminal ---------------------------------------------------------------------------------

    @Test
    fun terminalError_flagsTheErrorLineAreaShown() {
        val m = uiModel(
            PrintStatusMode.Terminal(TerminalKind.Error),
            PrinterState(printState = PrintState.Error, printFilename = "failed.gcode"),
        )
        assertTrue(m.showErrorLines)
        assertEquals(listOf("Dismiss", "Reprint"), m.gutter.map { it.label })
    }

    @Test
    fun terminalCompleteAndCancelled_hideTheErrorLineArea() {
        val complete = uiModel(
            PrintStatusMode.Terminal(TerminalKind.Complete),
            PrinterState(printState = PrintState.Complete, printFilename = "cube.gcode"),
        )
        val cancelled = uiModel(
            PrintStatusMode.Terminal(TerminalKind.Cancelled),
            PrinterState(printState = PrintState.Cancelled, printFilename = "cube.gcode"),
        )
        assertFalse(complete.showErrorLines)
        assertFalse(cancelled.showErrorLines)
    }
}
