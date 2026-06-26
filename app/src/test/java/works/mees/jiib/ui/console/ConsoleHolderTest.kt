package works.mees.jiib.ui.console

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (08-01) — turned GREEN by 08-05 Task 1 (`ConsoleHolder`).
 *
 * REQ-CONS-02. The holder collects the ALREADY-WIRED raw line stream (`gcodeResponses`) and the
 * one-shot backfill snapshot (`consoleBackfill`). D-02: a backfill snapshot REPLACES prior content
 * (disconnect-window recovery, Mainsail parity); live appends accrue after. The exposed state is the
 * RAW (unfiltered) list (D-04 — filtering is the view's job). The bounded ring evicts at the ~1000 cap.
 *
 * Production symbols referenced (NOT YET BUILT → RED): [ConsoleHolder] + its `state: StateFlow`, and
 * a stub source exposing `gcodeResponses: SharedFlow<String>` + `consoleBackfill: SharedFlow<List<ConsoleLine>>`.
 */
class ConsoleHolderTest {

    /** Minimal fake of the seam the holder collects from (no live I/O). */
    private class FakeConsoleSource {
        val gcodeResponsesFlow = MutableSharedFlow<String>(extraBufferCapacity = 4096)
        val consoleBackfillFlow = MutableSharedFlow<List<ConsoleLine>>(extraBufferCapacity = 8)
        val gcodeResponses: SharedFlow<String> get() = gcodeResponsesFlow.asSharedFlow()
        val consoleBackfill: SharedFlow<List<ConsoleLine>> get() = consoleBackfillFlow.asSharedFlow()
    }

    private fun line(msg: String) = ConsoleLine(rawMessage = msg, severity = ConsoleSeverity.classify(msg), timeEpoch = null)

    @Test
    fun liveAppend_classifiesAndAccrues() = runTest {
        val src = FakeConsoleSource()
        val holder = ConsoleHolder(this, src.gcodeResponses, src.consoleBackfill)
        src.gcodeResponsesFlow.emit("M104 S200")
        src.gcodeResponsesFlow.emit("// External Power OFF")
        advanceUntilIdle()
        val lines = holder.state.value
        assertEquals(listOf("M104 S200", "// External Power OFF"), lines.map { it.rawMessage })
        assertEquals(ConsoleSeverity.WARNING, lines.last().severity)
    }

    @Test
    fun backfillSnapshot_replacesPriorContent_thenLiveAppends() = runTest {
        val src = FakeConsoleSource()
        val holder = ConsoleHolder(this, src.gcodeResponses, src.consoleBackfill)
        src.gcodeResponsesFlow.emit("stale before reconnect")
        advanceUntilIdle()
        // A reconnect backfill REPLACES the prior content (D-02 disconnect-window recovery).
        src.consoleBackfillFlow.emit(listOf(line("// Klipper state: Ready"), line("Done printing file")))
        advanceUntilIdle()
        assertEquals(listOf("// Klipper state: Ready", "Done printing file"), holder.state.value.map { it.rawMessage })
        // Live lines accrue AFTER the backfill.
        src.gcodeResponsesFlow.emit("M140 S60")
        advanceUntilIdle()
        assertEquals("M140 S60", holder.state.value.last().rawMessage)
    }

    @Test
    fun ringEvictsAtCap() = runTest {
        val src = FakeConsoleSource()
        val holder = ConsoleHolder(this, src.gcodeResponses, src.consoleBackfill)
        repeat(1200) { src.gcodeResponsesFlow.emit("line $it") }
        advanceUntilIdle()
        assertTrue("bounded ring must cap near 1000", holder.state.value.size <= 1000)
        // The newest line survives; the oldest is evicted.
        assertEquals("line 1199", holder.state.value.last().rawMessage)
    }

    @Test
    fun exposedStateIsRawUnfiltered_d04() = runTest {
        val src = FakeConsoleSource()
        val holder = ConsoleHolder(this, src.gcodeResponses, src.consoleBackfill)
        // A temperature-report line that the Hide-temperatures filter WOULD hide must still be present
        // in the holder's RAW state (the holder never filters — that is the view's job).
        src.gcodeResponsesFlow.emit("ok T:210.0 /210.0 B:60.0 /60.0")
        advanceUntilIdle()
        assertEquals(1, holder.state.value.size)
    }
}
