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
 * Host-side proof for [PrintMetadataHolder]: one-shot-per-filename gcode-metadata fetch off the live
 * `printerState`. Drives a real `MutableStateFlow<PrinterState>` under the `runTest` virtual clock
 * with an [UnconfinedTestDispatcher] so the holder's `collect` runs eagerly; a counting injected
 * `fetch` lambda returns a faithful catalog-shaped metadata `JsonElement` parsed via [MoonrakerJson].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrintMetadataHolderTest {

    /** A faithful Ender-5 metadata reply (estimated_time 2191, layer_count 50, 300px largest thumb). */
    private val metadataJson: JsonElement = MoonrakerJson.parseToJsonElement(
        """
        {
          "estimated_time": 2191,
          "layer_count": 50,
          "thumbnails": [
            { "width": 32,  "height": 32,  "size": 1024,  "relative_path": ".thumbs/b-32x32.png" },
            { "width": 300, "height": 300, "size": 40960, "relative_path": ".thumbs/b-300x300.png" }
          ]
        }
        """.trimIndent(),
    )

    private fun printing(filename: String): PrinterState =
        PrinterState(printState = PrintState.Printing, printFilename = filename)

    @Test
    fun fetchesOncePerFilename_acrossRepeatedEmissions() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val flow = MutableStateFlow(PrinterState())
        val holder = PrintMetadataHolder(backgroundScope, flow) { calls++; metadataJson }

        repeat(5) { flow.value = printing("a.gcode"); runCurrent() }

        assertEquals("fetch fires exactly once per filename", 1, calls)
        assertEquals(50, holder.metadata.value!!.layerCount)
        assertEquals(2191.0, holder.metadata.value!!.estimatedTime!!, 0.0)
    }

    @Test
    fun reFetchesOnFilenameChange() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val flow = MutableStateFlow(PrinterState())
        val holder = PrintMetadataHolder(backgroundScope, flow) { calls++; metadataJson }

        flow.value = printing("a.gcode"); runCurrent()
        flow.value = printing("b.gcode"); runCurrent()

        assertEquals("a filename change re-keys and re-fetches", 2, calls)
    }

    @Test
    fun clearsOnIdle_thenReFetchesSameFile() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val flow = MutableStateFlow(PrinterState())
        val holder = PrintMetadataHolder(backgroundScope, flow) { calls++; metadataJson }

        flow.value = printing("a.gcode"); runCurrent()
        assertEquals(1, calls)

        // Go idle (standby, blank active filename) → metadata clears, key resets.
        flow.value = PrinterState(printState = PrintState.Standby, printFilename = ""); runCurrent()
        assertNull(holder.metadata.value)

        // The SAME file printing again re-fetches (key was reset on idle).
        flow.value = printing("a.gcode"); runCurrent()
        assertEquals(2, calls)
        assertEquals(50, holder.metadata.value!!.layerCount)
    }

    @Test
    fun retainsMetadataThroughTerminal_clearsOnStandby() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val flow = MutableStateFlow(PrinterState())
        val holder = PrintMetadataHolder(backgroundScope, flow) { calls++; metadataJson }

        flow.value = printing("a.gcode"); runCurrent()
        assertEquals(1, calls)

        // Print ends → Cancelled (same filename). Metadata MUST survive for the Terminal hero/stats.
        flow.value = PrinterState(printState = PrintState.Cancelled, printFilename = "a.gcode"); runCurrent()
        assertEquals("no re-fetch on terminal (same key)", 1, calls)
        assertEquals(50, holder.metadata.value!!.layerCount)

        // Only Standby (Dismiss/new session) clears it.
        flow.value = PrinterState(printState = PrintState.Standby, printFilename = ""); runCurrent()
        assertNull(holder.metadata.value)
    }

    @Test
    fun nullFetch_metadataStaysNull_noCrash() = runTest(UnconfinedTestDispatcher()) {
        val flow = MutableStateFlow(PrinterState())
        val holder = PrintMetadataHolder(backgroundScope, flow) { null }

        flow.value = printing("a.gcode"); runCurrent()

        assertNull(holder.metadata.value)
    }
}
