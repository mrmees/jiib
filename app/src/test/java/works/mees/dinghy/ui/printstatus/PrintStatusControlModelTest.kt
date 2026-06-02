package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.state.LastJob
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

class PrintStatusControlModelTest {

    @Test
    fun printing_mapsToTunePauseStopWithGracefulCancelHoldAndAccessibilityAction() {
        val model = derivePrintStatusControls(PrinterState(printState = PrintState.Printing, printFilename = "cube.gcode"))

        assertControls(model, "Tune", "Pause", "Stop")
        assertFalse(model.controls[0].enabled)
        assertEquals(PrintStatusControlAction.PausePrint, model.controls[1].tapAction)
        assertEquals(PrintStatusControlAction.GracefulCancel, model.controls[1].holdAction)
        assertEquals(PrintStatusControlAction.GracefulCancel, model.controls[1].accessibilityAction)
        assertEquals(PrintStatusControlAction.EmergencyStop, model.controls[2].tapAction)
    }

    @Test
    fun paused_mapsToTuneResumeStopWithGracefulCancelHoldAndAccessibilityAction() {
        val model = derivePrintStatusControls(PrinterState(printState = PrintState.Paused, printFilename = "cube.gcode"))

        assertControls(model, "Tune", "Resume", "Stop")
        assertFalse(model.controls[0].enabled)
        assertEquals(PrintStatusControlAction.ResumePrint, model.controls[1].tapAction)
        assertEquals(PrintStatusControlAction.GracefulCancel, model.controls[1].holdAction)
        assertEquals(PrintStatusControlAction.GracefulCancel, model.controls[1].accessibilityAction)
    }

    @Test
    fun completeWithCurrentFilename_mapsToFilesRestartStop() {
        val model = derivePrintStatusControls(PrinterState(printState = PrintState.Complete, printFilename = "cube.gcode"))

        assertControls(model, "Files", "Restart print", "Stop")
        assertEquals("cube.gcode", model.restartFilename)
        assertEquals(PrintStatusControlAction.OpenFiles, model.controls[0].tapAction)
        assertEquals(PrintStatusControlAction.RestartPrint, model.controls[1].tapAction)
    }

    @Test
    fun errorWithCurrentFilename_mapsToFilesRestartStop() {
        val model = derivePrintStatusControls(PrinterState(printState = PrintState.Error, printFilename = "failed.gcode"))

        assertControls(model, "Files", "Restart print", "Stop")
        assertEquals("failed.gcode", model.restartFilename)
    }

    @Test
    fun cancelledWithCurrentFilename_mapsToFilesRestartStop() {
        val model = derivePrintStatusControls(PrinterState(printState = PrintState.Cancelled, printFilename = "cancelled.gcode"))

        assertControls(model, "Files", "Restart print", "Stop")
        assertEquals("cancelled.gcode", model.restartFilename)
    }

    @Test
    fun terminalStateUsesLastJobFilenameWhenCurrentFilenameIsCleared() {
        val model = derivePrintStatusControls(
            state = PrinterState(printState = PrintState.Complete, printFilename = ""),
            lastJob = lastJob("history/benchy.gcode"),
        )

        assertControls(model, "Files", "Restart print", "Stop")
        assertEquals("history/benchy.gcode", model.restartFilename)
    }

    @Test
    fun terminalStateWithoutAnyFilename_hidesRestart() {
        val model = derivePrintStatusControls(PrinterState(printState = PrintState.Complete, printFilename = ""))

        assertControls(model, "Files", "Restart print", "Stop")
        assertNull(model.restartFilename)
        assertFalse(model.controls[1].enabled)
        assertNull(model.controls[1].tapAction)
    }

    @Test
    fun standbyDoesNotExposeRestart() {
        val model = derivePrintStatusControls(
            state = PrinterState(printState = PrintState.Standby, printFilename = ""),
            lastJob = lastJob("history/benchy.gcode"),
        )

        assertControls(model, "Tune", "Pause", "Stop")
        assertNull(model.restartFilename)
        assertFalse(model.controls[1].enabled)
    }

    @Test
    fun pendingActionLabelsAreStateSpecific() {
        assertEquals(
            "Pausing",
            derivePrintStatusControls(
                state = PrinterState(printState = PrintState.Printing, printFilename = "cube.gcode"),
                pendingAction = PrintStatusPendingAction.Pause,
            ).controls[1].label,
        )
        assertEquals(
            "Resuming",
            derivePrintStatusControls(
                state = PrinterState(printState = PrintState.Paused, printFilename = "cube.gcode"),
                pendingAction = PrintStatusPendingAction.Resume,
            ).controls[1].label,
        )
        assertEquals(
            "Cancelling",
            derivePrintStatusControls(
                state = PrinterState(printState = PrintState.Printing, printFilename = "cube.gcode"),
                pendingAction = PrintStatusPendingAction.Cancel,
            ).controls[1].label,
        )
        assertEquals(
            "Restarting",
            derivePrintStatusControls(
                state = PrinterState(printState = PrintState.Complete, printFilename = "cube.gcode"),
                pendingAction = PrintStatusPendingAction.Restart("cube.gcode"),
            ).controls[1].label,
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

    private fun assertControls(model: PrintStatusControlModel, left: String, center: String, right: String) {
        assertEquals(listOf(left, center, right), model.controls.map { it.label })
        assertTrue(model.controls.size == 3)
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
