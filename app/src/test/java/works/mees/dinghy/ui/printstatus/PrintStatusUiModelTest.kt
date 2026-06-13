package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.R
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

/**
 * The 16-06 host gate: the pure [uiModel] mode→layout/control derivation. Covers each mode's foot
 * set (R1 gutter→foot migration), the curated Standby launcher order (incl. the spool/macros
 * conditionals), the shortcut-vs-babystep Field row pick, and the Terminal error-line flag.
 */
class PrintStatusUiModelTest {

    private fun standby() = uiModel(PrintStatusMode.Standby, PrinterState(printState = PrintState.Standby))

    // ---- Standby ----------------------------------------------------------------------------------

    @Test
    fun standby_hasNoModelDerivedFoot() {
        // R1: the Standby foot bar (Preheat · System) is hand-built in PrintStatusStandbyField —
        // the model derives an EMPTY foot set for Standby.
        val m = standby()
        assertTrue(m.foot.isEmpty())
    }

    @Test
    fun standby_launcherIsTheFixedCuratedOrder_consoleAlwaysLast() {
        val m = standby() // no spool, no bookmarked macros
        assertEquals(
            listOf(
                LauncherDest.Files,
                LauncherDest.Temperature,
                LauncherDest.Move,
                LauncherDest.Extrude,
                LauncherDest.Calibration,
                LauncherDest.Console,
            ),
            m.launcherDests,
        )
        // Console is the last tile in the base case (no spool, no macros).
        assertEquals(LauncherDest.Console, m.launcherDests.last())
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
    fun printing_footIsPauseCancel_andShortcutRowByDefault() {
        val m = uiModel(
            PrintStatusMode.Printing,
            PrinterState(printState = PrintState.Printing, printFilename = "cube.gcode"),
        )
        // R1 foot set (sketch-001): Pause · Cancel. E-stop is the AppShell FloatingEStop, not a foot button.
        assertEquals(
            listOf(R.string.printstatus_foot_pause, R.string.printstatus_foot_cancel),
            m.foot.map { it.labelRes },
        )
        assertEquals(PrintStatusControlAction.PausePrint, m.foot[0].tapAction)
        assertEquals(PrintStatusControlAction.GracefulCancel, m.foot[1].tapAction)
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
    fun paused_footIsResumeCancel() {
        val m = uiModel(
            PrintStatusMode.Paused,
            PrinterState(printState = PrintState.Paused, printFilename = "cube.gcode"),
        )
        assertEquals(
            listOf(R.string.printstatus_foot_resume, R.string.printstatus_foot_cancel),
            m.foot.map { it.labelRes },
        )
        assertEquals(PrintStatusControlAction.ResumePrint, m.foot[0].tapAction)
        assertEquals(PrintStatusControlAction.GracefulCancel, m.foot[1].tapAction)
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
        assertEquals(
            listOf(R.string.printstatus_foot_dismiss, R.string.printstatus_foot_reprint),
            m.foot.map { it.labelRes },
        )
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
