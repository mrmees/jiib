package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.state.KlippyState
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

/**
 * Wave-2 (16-02) — the GREEN classifier contract turned over from the 16-01 RED scaffold.
 *
 * Encodes the Phase-16 classifier CONTRACT:
 *
 *  1. The classifier maps all 6 raw [PrintState] values to exactly 4 modes:
 *       Printing            -> Printing
 *       Paused              -> Paused
 *       Complete/Cancelled/Error -> Terminal(kind)
 *       Standby             -> Standby
 *  2. CRITICAL behavior change vs `derivePrintStatusControls`: `Standby` STAYS `Standby` even when a
 *     stale `printFilename` / last job exists. The old derive-controls path rendered TERMINAL controls
 *     on a leftover restart filename — the classifier MUST NOT copy that branch.
 *  3. The classifier reads ONLY `printState` — a klippy shutdown/error must NOT manufacture a Terminal
 *     mode (klippy lifecycle is a separate axis; the classifier is print-state-only).
 *  4. Babystep gating during the early first-layer window:
 *       - null `currentLayer`            -> babystep HIDDEN (no time fallback)
 *       - `currentLayer <= threshold`    -> babystep SHOWN
 *       - `currentLayer > threshold`     -> babystep HIDDEN
 *  5. The babystep step cycle is the fixed ordered set `.02 -> .05 -> .10 -> .15 -> .20` (and wraps).
 */
class PrintStatusModeTest {

    private val printing = PrinterState(printState = PrintState.Printing, printFilename = "cube.gcode")
    private val paused = PrinterState(printState = PrintState.Paused, printFilename = "cube.gcode")
    private val complete = PrinterState(printState = PrintState.Complete, printFilename = "cube.gcode")
    private val cancelled = PrinterState(printState = PrintState.Cancelled, printFilename = "cube.gcode")
    private val errored = PrinterState(printState = PrintState.Error, printFilename = "failed.gcode")
    private val standbyStaleName = PrinterState(printState = PrintState.Standby, printFilename = "leftover.gcode")
    private val standbyClean = PrinterState(printState = PrintState.Standby, printFilename = "")

    @Test
    fun printing_mapsToPrintingMode() {
        assertEquals(PrintStatusMode.Printing, classifyPrintStatus(printing))
    }

    @Test
    fun paused_mapsToPausedMode() {
        assertEquals(PrintStatusMode.Paused, classifyPrintStatus(paused))
    }

    @Test
    fun complete_cancelled_error_allMapToTerminalMode() {
        assertEquals(PrintStatusMode.Terminal(TerminalKind.Complete), classifyPrintStatus(complete))
        assertEquals(PrintStatusMode.Terminal(TerminalKind.Cancelled), classifyPrintStatus(cancelled))
        assertEquals(PrintStatusMode.Terminal(TerminalKind.Error), classifyPrintStatus(errored))
    }

    @Test
    fun standbyWithStaleFilename_staysStandby_notTerminal() {
        // CRITICAL behavior change: a leftover printFilename must NOT flip Standby to a terminal mode.
        assertEquals(PrintStatusMode.Standby, classifyPrintStatus(standbyStaleName))
    }

    @Test
    fun standbyClean_mapsToStandbyMode() {
        assertEquals(PrintStatusMode.Standby, classifyPrintStatus(standbyClean))
    }

    @Test
    fun klippyShutdownDoesNotProduceTerminalMode_classifierReadsPrintStateOnly() {
        // A Standby printState with klippy shutdown/error still classifies on printState alone.
        val standbyKlippyShutdown = standbyClean.copy(klippyState = KlippyState.Shutdown)
        val printingKlippyError = printing.copy(klippyState = KlippyState.Error)
        assertEquals(PrintStatusMode.Standby, classifyPrintStatus(standbyKlippyShutdown))
        assertEquals(PrintStatusMode.Printing, classifyPrintStatus(printingKlippyError))
    }

    @Test
    fun babystep_hiddenWhenCurrentLayerNull_noTimeFallback() {
        assertFalse(babystepVisible(settingEnabled = true, currentLayer = null, layerThreshold = 5))
    }

    @Test
    fun babystep_shownWhenCurrentLayerWithinThreshold() {
        assertTrue(babystepVisible(settingEnabled = true, currentLayer = 3, layerThreshold = 5))
        // Boundary: exactly at the threshold is still shown.
        assertTrue(babystepVisible(settingEnabled = true, currentLayer = 5, layerThreshold = 5))
        // The app setting being off hides it regardless.
        assertFalse(babystepVisible(settingEnabled = false, currentLayer = 3, layerThreshold = 5))
    }

    @Test
    fun babystep_hiddenWhenCurrentLayerPastThreshold() {
        assertFalse(babystepVisible(settingEnabled = true, currentLayer = 6, layerThreshold = 5))
        assertFalse(babystepVisible(settingEnabled = true, currentLayer = 99, layerThreshold = 5))
    }

    @Test
    fun babystepStepCycle_isFixedOrderedSet() {
        val expected = listOf(0.02, 0.05, 0.10, 0.15, 0.20)
        assertEquals(expected, PrinterCommands.BABYSTEP_STEPS)
        // The cycle advances through each member and wraps back to the first.
        assertEquals(0.05, nextBabystepStep(0.02), 1e-9)
        assertEquals(0.10, nextBabystepStep(0.05), 1e-9)
        assertEquals(0.15, nextBabystepStep(0.10), 1e-9)
        assertEquals(0.20, nextBabystepStep(0.15), 1e-9)
        assertEquals(0.02, nextBabystepStep(0.20), 1e-9)
    }
}
