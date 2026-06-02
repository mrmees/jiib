package works.mees.dinghy.ui.printstatus

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

/**
 * Host-side proof for [LastJobHolder]: one-shot-ON-IDLE `server.history.list` fetch off the live
 * `printerState`. Drives a real `MutableStateFlow<PrinterState>` under the `runTest` virtual clock with
 * an [UnconfinedTestDispatcher] so the holder's `collect` runs eagerly; a counting injected `fetch`
 * lambda returns a faithful catalog-shaped history `JsonElement` parsed via [MoonrakerJson].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LastJobHolderTest {

    /** A faithful server.history.list reply (count 137, one completed job with metadata). */
    private val historyJson: JsonElement = MoonrakerJson.parseToJsonElement(
        """
        {
          "count": 137,
          "jobs": [
            {
              "filename": "miata/airbox-bracket.gcode",
              "status": "completed",
              "print_duration": 7012.4,
              "total_duration": 7191.0,
              "filament_used": 5749.86,
              "exists": true,
              "metadata": {
                "estimated_time": 7000,
                "filament_weight_total": 17.15,
                "thumbnails": [
                  { "width": 300, "height": 300, "size": 40960, "relative_path": ".thumbs/b-300x300.png" }
                ]
              }
            }
          ]
        }
        """.trimIndent(),
    )

    private val emptyHistoryJson: JsonElement =
        MoonrakerJson.parseToJsonElement("""{ "count": 0, "jobs": [] }""")

    private fun idle(): PrinterState = PrinterState(printState = PrintState.Standby)
    private fun printing(): PrinterState =
        PrinterState(printState = PrintState.Printing, printFilename = "a.gcode")

    @Test
    fun initialIdleEmission_fetchesOnce_lastJobPopulated() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val flow = MutableStateFlow(idle())
        val holder = LastJobHolder(backgroundScope, flow) { calls++; historyJson }

        runCurrent()

        assertEquals("initial idle connect → fetch fires once", 1, calls)
        assertEquals("miata/airbox-bracket.gcode", holder.lastJob.value!!.filename)
        assertEquals("completed", holder.lastJob.value!!.status)
    }

    @Test
    fun printingToComplete_refreshesExactlyOnce() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        // Start PRINTING so the first emission does NOT fetch.
        val flow = MutableStateFlow(printing())
        val holder = LastJobHolder(backgroundScope, flow) { calls++; historyJson }
        runCurrent()
        assertEquals("no fetch while printing", 0, calls)

        // Print completes → enters not-printing → exactly one refresh fetch.
        flow.value = PrinterState(printState = PrintState.Complete); runCurrent()
        assertEquals("printing→complete edge refreshes once", 1, calls)
    }

    @Test
    fun repeatedIdleEmissions_noExtraFetch_noPoll() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val flow = MutableStateFlow(idle())
        val holder = LastJobHolder(backgroundScope, flow) { calls++; historyJson }

        repeat(5) { flow.value = idle(); runCurrent() }

        assertEquals("a steady idle stream never re-fetches (no poll)", 1, calls)
    }

    @Test
    fun whilePrinting_zeroFetches() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val flow = MutableStateFlow(printing())
        val holder = LastJobHolder(backgroundScope, flow) { calls++; historyJson }

        repeat(5) { flow.value = printing(); runCurrent() }

        assertEquals("never fetches while printing", 0, calls)
    }

    @Test
    fun countZeroResult_lastJobNull_emptyState() = runTest(UnconfinedTestDispatcher()) {
        val flow = MutableStateFlow(idle())
        val holder = LastJobHolder(backgroundScope, flow) { emptyHistoryJson }

        runCurrent()

        assertNull("count==0 → null empty-state signal", holder.lastJob.value)
    }

    @Test
    fun nullFetch_priorValueRetained_noCrash() = runTest(UnconfinedTestDispatcher()) {
        var first = true
        // Start printing so the first idle transition is a real edge; first idle fetch returns the job,
        // a later idle fetch returns null and must NOT clobber the prior value.
        val flow = MutableStateFlow(printing())
        val holder = LastJobHolder(backgroundScope, flow) {
            if (first) { first = false; historyJson } else null
        }
        runCurrent()

        // print 1 completes → fetch #1 returns the job.
        flow.value = PrinterState(printState = PrintState.Complete); runCurrent()
        assertEquals("miata/airbox-bracket.gcode", holder.lastJob.value!!.filename)

        // print 2 then completes → fetch #2 returns null → prior value retained, no crash.
        flow.value = printing(); runCurrent()
        flow.value = PrinterState(printState = PrintState.Complete); runCurrent()
        assertEquals("null fetch retains the prior card", "miata/airbox-bracket.gcode", holder.lastJob.value!!.filename)
    }
}
