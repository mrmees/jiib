package works.mees.dinghy.ui.printstatus

import org.junit.Assert.fail
import org.junit.Test
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

/**
 * Wave-0 RED scaffold (16-01) — turned GREEN by 16-02 (`classifyPrintStatus` / `PrintStatusMode`
 * + the babystep-gating helpers).
 *
 * Encodes the Phase-16 classifier CONTRACT so every later wave has an automated gate:
 *
 *  1. The classifier maps all 6 raw [PrintState] values to exactly 4 modes:
 *       Printing            -> Printing
 *       Paused              -> Paused
 *       Complete/Cancelled/Error -> Terminal
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
 *  5. The babystep step cycle is the fixed ordered set `.02 -> .05 -> .10 -> .15 -> .20`.
 *
 * RED discipline ([[dinghy-wave0-red-scaffold-compile]]): these bodies do NOT reference the not-yet-built
 * `classifyPrintStatus`, `PrintStatusMode`, or any babystep helper (they land in 16-02). The fixtures
 * are built with already-existing symbols ([PrinterState], [PrintState]) so the whole test sourceset
 * compiles day-one; each case `fail(...)`s until 16-02 turns it GREEN.
 */
class PrintStatusModeTest {

    // Reference the existing fixture symbols so the imports are load-bearing (compile proof) — the
    // builder style is identical to PrintStatusControlModelTest.
    private val printing = PrinterState(printState = PrintState.Printing, printFilename = "cube.gcode")
    private val paused = PrinterState(printState = PrintState.Paused, printFilename = "cube.gcode")
    private val complete = PrinterState(printState = PrintState.Complete, printFilename = "cube.gcode")
    private val cancelled = PrinterState(printState = PrintState.Cancelled, printFilename = "cube.gcode")
    private val errored = PrinterState(printState = PrintState.Error, printFilename = "failed.gcode")
    private val standbyStaleName = PrinterState(printState = PrintState.Standby, printFilename = "leftover.gcode")
    private val standbyClean = PrinterState(printState = PrintState.Standby, printFilename = "")

    @Test
    fun printing_mapsToPrintingMode() {
        // EXPECT (16-02): classifyPrintStatus(printing).mode == PrintStatusMode.Printing
        fail("not yet implemented — Wave 2 16-02 classifyPrintStatus")
    }

    @Test
    fun paused_mapsToPausedMode() {
        // EXPECT (16-02): classifyPrintStatus(paused).mode == PrintStatusMode.Paused
        fail("not yet implemented — Wave 2 16-02 classifyPrintStatus")
    }

    @Test
    fun complete_cancelled_error_allMapToTerminalMode() {
        // EXPECT (16-02): each of complete/cancelled/errored classifies to PrintStatusMode.Terminal
        fail("not yet implemented — Wave 2 16-02 classifyPrintStatus")
    }

    @Test
    fun standbyWithStaleFilename_staysStandby_notTerminal() {
        // EXPECT (16-02): classifyPrintStatus(standbyStaleName).mode == PrintStatusMode.Standby
        // CRITICAL behavior change: a leftover printFilename must NOT flip Standby to a terminal mode
        // (do NOT copy derivePrintStatusControls' restart-on-stale-filename branch).
        fail("not yet implemented — Wave 2 16-02 classifyPrintStatus (Standby-stays-Standby)")
    }

    @Test
    fun standbyClean_mapsToStandbyMode() {
        // EXPECT (16-02): classifyPrintStatus(standbyClean).mode == PrintStatusMode.Standby
        fail("not yet implemented — Wave 2 16-02 classifyPrintStatus")
    }

    @Test
    fun klippyShutdownDoesNotProduceTerminalMode_classifierReadsPrintStateOnly() {
        // EXPECT (16-02): a Standby/Printing printState with klippy shutdown/error still classifies on
        // printState alone — the classifier must NOT manufacture Terminal from klippy lifecycle.
        fail("not yet implemented — Wave 2 16-02 classifyPrintStatus (print-state-only)")
    }

    @Test
    fun babystep_hiddenWhenCurrentLayerNull_noTimeFallback() {
        // EXPECT (16-02): currentLayer == null -> babystep control HIDDEN (no time-based fallback).
        val noLayer = printing.copy(currentLayer = null)
        require(noLayer.currentLayer == null) // fixture sanity, keeps the symbol load-bearing
        fail("not yet implemented — Wave 2 16-02 babystep gating")
    }

    @Test
    fun babystep_shownWhenCurrentLayerWithinThreshold() {
        // EXPECT (16-02): currentLayer <= threshold -> babystep SHOWN.
        val earlyLayer = printing.copy(currentLayer = 1)
        require(earlyLayer.currentLayer == 1)
        fail("not yet implemented — Wave 2 16-02 babystep gating")
    }

    @Test
    fun babystep_hiddenWhenCurrentLayerPastThreshold() {
        // EXPECT (16-02): currentLayer > threshold -> babystep HIDDEN.
        val lateLayer = printing.copy(currentLayer = 99)
        require(lateLayer.currentLayer == 99)
        fail("not yet implemented — Wave 2 16-02 babystep gating")
    }

    @Test
    fun babystepStepCycle_isFixedOrderedSet() {
        // EXPECT (16-02): the babystep step cycle is exactly .02 -> .05 -> .10 -> .15 -> .20 (and wraps).
        val expected = listOf(0.02, 0.05, 0.10, 0.15, 0.20)
        require(expected.size == 5)
        fail("not yet implemented — Wave 2 16-02 babystep step cycle ($expected)")
    }
}
