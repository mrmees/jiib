package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.R
import works.mees.dinghy.state.LastJob
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

class PrintStatusControlModelTest {

    @Test
    fun printing_mapsToPausePlusExplicitCancel() {
        // R1 foot migration (sketch-001): Printing foot = Pause · Cancel. No Tune stub, no Stop —
        // the AppShell FloatingEStop owns the e-stop, Cancel is an explicit button (hold retired).
        val model = derivePrintStatusControls(PrinterState(printState = PrintState.Printing, printFilename = "cube.gcode"))

        assertControls(model, R.string.printstatus_foot_pause, R.string.printstatus_foot_cancel)
        assertEquals(PrintStatusControlAction.PausePrint, model.controls[0].tapAction)
        assertEquals(PrintStatusControlAction.GracefulCancel, model.controls[1].tapAction)
        assertTrue(model.controls.all { it.enabled })
    }

    @Test
    fun paused_mapsToResumePlusExplicitCancel() {
        val model = derivePrintStatusControls(PrinterState(printState = PrintState.Paused, printFilename = "cube.gcode"))

        assertControls(model, R.string.printstatus_foot_resume, R.string.printstatus_foot_cancel)
        assertEquals(PrintStatusControlAction.ResumePrint, model.controls[0].tapAction)
        assertEquals(PrintStatusControlAction.GracefulCancel, model.controls[1].tapAction)
    }

    @Test
    fun completeWithCurrentFilename_mapsToDismissReprint() {
        val model = derivePrintStatusControls(PrinterState(printState = PrintState.Complete, printFilename = "cube.gcode"))

        assertControls(model, R.string.printstatus_foot_dismiss, R.string.printstatus_foot_reprint)
        assertEquals("cube.gcode", model.restartFilename)
        assertEquals(PrintStatusControlAction.Dismiss, model.controls[0].tapAction)
        assertEquals(PrintStatusControlAction.RestartPrint, model.controls[1].tapAction)
    }

    @Test
    fun errorWithCurrentFilename_mapsToDismissReprint() {
        val model = derivePrintStatusControls(PrinterState(printState = PrintState.Error, printFilename = "failed.gcode"))

        assertControls(model, R.string.printstatus_foot_dismiss, R.string.printstatus_foot_reprint)
        assertEquals("failed.gcode", model.restartFilename)
    }

    @Test
    fun cancelledWithCurrentFilename_mapsToDismissReprint() {
        val model = derivePrintStatusControls(PrinterState(printState = PrintState.Cancelled, printFilename = "cancelled.gcode"))

        assertControls(model, R.string.printstatus_foot_dismiss, R.string.printstatus_foot_reprint)
        assertEquals("cancelled.gcode", model.restartFilename)
    }

    @Test
    fun terminalStateUsesLastJobFilenameWhenCurrentFilenameIsCleared() {
        val model = derivePrintStatusControls(
            state = PrinterState(printState = PrintState.Complete, printFilename = ""),
            lastJob = lastJob("history/benchy.gcode"),
        )

        assertControls(model, R.string.printstatus_foot_dismiss, R.string.printstatus_foot_reprint)
        assertEquals("history/benchy.gcode", model.restartFilename)
    }

    @Test
    fun terminalStateWithoutAnyFilename_disablesReprint() {
        val model = derivePrintStatusControls(PrinterState(printState = PrintState.Complete, printFilename = ""))

        assertControls(model, R.string.printstatus_foot_dismiss, R.string.printstatus_foot_reprint)
        assertNull(model.restartFilename)
        assertFalse(model.controls[1].enabled)
        assertNull(model.controls[1].tapAction)
    }

    @Test
    fun standby_hasNoModelDerivedFootControls() {
        // R1: the Standby foot bar (Preheat · System) is hand-built in PrintStatusStandbyField; the
        // old model-derived Preheat/Power gutter set is retired. A leftover last-job filename still
        // resolves (the launcher Files tile uses it) but produces NO terminal controls.
        val withLastJob = derivePrintStatusControls(
            state = PrinterState(printState = PrintState.Standby, printFilename = ""),
            lastJob = lastJob("history/benchy.gcode"),
        )
        val bare = derivePrintStatusControls(PrinterState(printState = PrintState.Standby, printFilename = ""))

        assertTrue(withLastJob.controls.isEmpty())
        assertEquals("history/benchy.gcode", withLastJob.restartFilename)
        assertTrue(bare.controls.isEmpty())
        assertNull(bare.restartFilename)
    }

    @Test
    fun pendingActionLabelsAreStateSpecific() {
        assertEquals(
            R.string.printstatus_foot_pausing,
            derivePrintStatusControls(
                state = PrinterState(printState = PrintState.Printing, printFilename = "cube.gcode"),
                pendingAction = PrintStatusPendingAction.Pause,
            ).controls[0].labelRes,
        )
        assertEquals(
            R.string.printstatus_foot_resuming,
            derivePrintStatusControls(
                state = PrinterState(printState = PrintState.Paused, printFilename = "cube.gcode"),
                pendingAction = PrintStatusPendingAction.Resume,
            ).controls[0].labelRes,
        )
        // Pending Cancel lands on the CANCEL button (not the Pause/Resume slot — the old combined
        // hold-to-cancel label is retired with the explicit Cancel button).
        assertEquals(
            R.string.printstatus_foot_cancelling,
            derivePrintStatusControls(
                state = PrinterState(printState = PrintState.Printing, printFilename = "cube.gcode"),
                pendingAction = PrintStatusPendingAction.Cancel,
            ).controls[1].labelRes,
        )
        assertEquals(
            R.string.printstatus_foot_cancelling,
            derivePrintStatusControls(
                state = PrinterState(printState = PrintState.Paused, printFilename = "cube.gcode"),
                pendingAction = PrintStatusPendingAction.Cancel,
            ).controls[1].labelRes,
        )
        assertEquals(
            R.string.printstatus_foot_reprinting,
            derivePrintStatusControls(
                state = PrinterState(printState = PrintState.Complete, printFilename = "cube.gcode"),
                pendingAction = PrintStatusPendingAction.Restart("cube.gcode"),
            ).controls[1].labelRes,
        )
    }

    @Test
    fun pendingPauseClearsOnlyWhenPrinterReportsPausedOrNoLongerPrinting() {
        assertEquals(
            PrintStatusPendingAction.Pause,
            clearPrintStatusPendingAction(
                PrintStatusPendingAction.Pause,
                PrinterState(printState = PrintState.Printing),
            ),
        )
        assertNull(clearPrintStatusPendingAction(PrintStatusPendingAction.Pause, PrinterState(printState = PrintState.Paused)))
        assertNull(clearPrintStatusPendingAction(PrintStatusPendingAction.Pause, PrinterState(printState = PrintState.Complete)))
    }

    @Test
    fun pendingResumeClearsOnlyWhenPrinterReportsPrintingOrNoLongerPaused() {
        assertEquals(
            PrintStatusPendingAction.Resume,
            clearPrintStatusPendingAction(
                PrintStatusPendingAction.Resume,
                PrinterState(printState = PrintState.Paused),
            ),
        )
        assertNull(clearPrintStatusPendingAction(PrintStatusPendingAction.Resume, PrinterState(printState = PrintState.Printing)))
        assertNull(clearPrintStatusPendingAction(PrintStatusPendingAction.Resume, PrinterState(printState = PrintState.Cancelled)))
    }

    @Test
    fun pendingCancelClearsWhenPrinterLeavesActiveStates() {
        assertEquals(
            PrintStatusPendingAction.Cancel,
            clearPrintStatusPendingAction(
                PrintStatusPendingAction.Cancel,
                PrinterState(printState = PrintState.Printing),
            ),
        )
        assertNull(clearPrintStatusPendingAction(PrintStatusPendingAction.Cancel, PrinterState(printState = PrintState.Cancelled)))
        assertNull(clearPrintStatusPendingAction(PrintStatusPendingAction.Cancel, PrinterState(printState = PrintState.Standby)))
    }

    @Test
    fun pendingRestartClearsOnlyWhenMatchingFilenameBecomesActive() {
        val pending = PrintStatusPendingAction.Restart("cube.gcode")

        assertEquals(
            pending,
            clearPrintStatusPendingAction(
                pending,
                PrinterState(printState = PrintState.Printing, printFilename = "other.gcode"),
            ),
        )
        assertNull(
            clearPrintStatusPendingAction(
                pending,
                PrinterState(printState = PrintState.Printing, printFilename = "cube.gcode"),
            ),
        )
        assertNull(
            clearPrintStatusPendingAction(
                pending,
                PrinterState(printState = PrintState.Paused, printFilename = "cube.gcode"),
            ),
        )
    }

    @Test
    fun dispatchFailureClearsOnlyTheMatchingPendingAction() {
        // The un-wedge path (Codex W-03): a failed command's key clears ITS pending action so the
        // foot bar un-dims; an unrelated failure key leaves the debounce armed.
        assertNull(clearPendingOnDispatchFailure(PrintStatusPendingAction.Pause, "pause_print"))
        assertNull(clearPendingOnDispatchFailure(PrintStatusPendingAction.Resume, "resume_print"))
        assertNull(clearPendingOnDispatchFailure(PrintStatusPendingAction.Cancel, "cancel_print"))
        assertNull(
            clearPendingOnDispatchFailure(
                PrintStatusPendingAction.Restart("cube.gcode"),
                "start_print_cube.gcode",
            ),
        )

        // Unrelated key → pending survives.
        assertEquals(
            PrintStatusPendingAction.Pause,
            clearPendingOnDispatchFailure(PrintStatusPendingAction.Pause, "babystep_z"),
        )
        // A DIFFERENT file's failed start does not clear this Restart.
        val pending = PrintStatusPendingAction.Restart("cube.gcode")
        assertEquals(pending, clearPendingOnDispatchFailure(pending, "start_print_other.gcode"))
        // Null stays null.
        assertNull(clearPendingOnDispatchFailure(null, "pause_print"))
    }

    private fun assertControls(model: PrintStatusControlModel, vararg labelResIds: Int) {
        assertEquals(labelResIds.toList(), model.controls.map { it.labelRes })
    }

    private fun lastJob(filename: String): LastJob =
        LastJob(
            filename = filename,
            status = "completed",
            printDuration = 60.0,
            totalDuration = 70.0,
            filamentUsed = 120.0,
            exists = true,
            endTime = null,
            estimatedTime = null,
            filamentWeightTotal = null,
            largestThumbRelPath = null,
            slicer = null,
            slicerVersion = null,
            filamentType = null,
            filamentName = null,
            filamentColor = null,
        )
}
