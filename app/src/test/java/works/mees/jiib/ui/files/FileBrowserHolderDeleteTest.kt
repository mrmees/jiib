package works.mees.jiib.ui.files

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.MoonrakerJson
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState

/**
 * Host-side proof for the D-15 HOLDER gate (CALIB-06). The OLD `requestDeleteSelected()` rejected ALL
 * deletes during any print with a bare `if (printState == Printing || Paused)` block — so even the
 * relaxed FilesScreen gate would be invisible (the holder silently blocked everything). The fix routes
 * the SAME host-tested `deleteAllowed` predicate the screen uses: during a print only the
 * currently-printing file (`print_stats.filename`) is rejected; every other idle file proceeds to
 * `client.deleteFile`; idle → any delete proceeds.
 *
 * PATH-FORM CONTRACT: the gate compares the RELATIVE, no-leading-"gcodes/" form
 * (`FileBrowserRow.relativeFilename`) against `print_stats.filename`; the API delete still takes the
 * `gcodes/`-prefixed `rootPrefixedPath`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserHolderDeleteTest {

    private class FakeFileBrowserClient : FileBrowserClient {
        val deletes = mutableListOf<String>()
        override suspend fun getDirectory(path: String?, extended: Boolean): JsonElement? = directoryJson
        override suspend fun getMetadata(filename: String): JsonElement? = null
        override suspend fun getThumbnails(filename: String): JsonElement? = null
        override fun deleteFile(rootPrefixedPath: String) { deletes += rootPrefixedPath }
        override fun startPrint(filename: String) = Unit
        override fun pausePrint() = Unit
        override fun resumePrint() = Unit
        override fun cancelPrint() = Unit
    }

    @Test
    fun matchingFileIsRejectedDuringPrint() = runTest(UnconfinedTestDispatcher()) {
        val printer = MutableStateFlow(PrinterState(printState = PrintState.Printing, printFilename = "new.gcode"))
        val client = FakeFileBrowserClient()
        val holder = holder(client, printer)

        holder.loadRoot()
        holder.selectFile(holder.state.value.directory.rows.first { it.name == "new.gcode" })
        holder.requestDeleteSelected()

        assertTrue("the actively-printing file is undeletable", client.deletes.isEmpty())
        assertEquals("Cannot delete the file that is currently printing.", holder.state.value.error)
    }

    @Test
    fun differentIdleFileProceedsDuringPrint() = runTest(UnconfinedTestDispatcher()) {
        // A DIFFERENT file is being printed → "old.gcode" stays deletable mid-print (the D-15 relaxation).
        val printer = MutableStateFlow(PrinterState(printState = PrintState.Printing, printFilename = "new.gcode"))
        val client = FakeFileBrowserClient()
        val holder = holder(client, printer)

        holder.loadRoot()
        holder.selectFile(holder.state.value.directory.rows.first { it.name == "old.gcode" })
        holder.requestDeleteSelected()

        // The API delete uses the gcodes-prefixed path even though the gate matched the relative form.
        assertEquals(listOf("gcodes/old.gcode"), client.deletes)
    }

    @Test
    fun anyDeleteProceedsWhenIdle() = runTest(UnconfinedTestDispatcher()) {
        val printer = MutableStateFlow(PrinterState(printState = PrintState.Standby, printFilename = ""))
        val client = FakeFileBrowserClient()
        val holder = holder(client, printer)

        holder.loadRoot()
        holder.selectFile(holder.state.value.directory.rows.first { it.name == "new.gcode" })
        holder.requestDeleteSelected()

        assertEquals(listOf("gcodes/new.gcode"), client.deletes)
    }

    private fun TestScope.holder(
        client: FakeFileBrowserClient,
        printerState: MutableStateFlow<PrinterState>,
    ) = FileBrowserHolder(backgroundScope, client, printerState)

    private companion object {
        val directoryJson: JsonElement = MoonrakerJson.parseToJsonElement(
            """
            {
              "dirs": [],
              "files": [
                { "filename": "old.gcode", "modified": 1.0, "size": 1 },
                { "filename": "new.gcode", "modified": 2.0, "size": 2 }
              ]
            }
            """.trimIndent(),
        )
    }
}
