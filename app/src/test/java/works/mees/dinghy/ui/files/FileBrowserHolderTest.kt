package works.mees.dinghy.ui.files

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson
import works.mees.dinghy.state.FileBrowserRowKind
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserHolderTest {

    private class FakeFileBrowserClient : FileBrowserClient {
        val directoryRequests = mutableListOf<String?>()
        val metadataRequests = mutableListOf<String>()
        val starts = mutableListOf<String>()
        val deletes = mutableListOf<String>()
        var directoryResult: JsonElement? = directoryJson
        var metadataResult: JsonElement? = metadataJson

        override suspend fun getDirectory(path: String?, extended: Boolean): JsonElement? {
            directoryRequests += path
            return directoryResult
        }

        override suspend fun getMetadata(filename: String): JsonElement? {
            metadataRequests += filename
            return metadataResult
        }

        override suspend fun getThumbnails(filename: String): JsonElement? = null

        override fun deleteFile(rootPrefixedPath: String) {
            deletes += rootPrefixedPath
        }

        override fun startPrint(filename: String) {
            starts += filename
        }

        override fun pausePrint() = Unit
        override fun resumePrint() = Unit
        override fun cancelPrint() = Unit
    }

    @Test
    fun loadRootAppliesModelSortingAndPathForms() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()

        holder.loadRoot()

        assertEquals("gcodes", holder.state.value.directory.directoryPath)
        assertEquals(listOf("folder", "new.gcode", "old.gcode"), holder.state.value.directory.rows.map { it.name })
        val file = holder.state.value.directory.rows[1]
        assertEquals(FileBrowserRowKind.File, file.kind)
        assertEquals("new.gcode", file.relativeFilename)
        assertEquals("gcodes/new.gcode", file.rootPrefixedPath)
    }

    @Test
    fun enterFolderUsesUpRowSemanticsNotBackNavigation() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeFileBrowserClient()
        val holder = holder(client)

        holder.loadRoot()
        holder.enterFolder(holder.state.value.directory.rows.first { it.kind == FileBrowserRowKind.Directory })

        assertEquals(listOf("gcodes", "gcodes/folder"), client.directoryRequests)
        assertNotNull(holder.state.value.directory.upRow)
        assertEquals("gcodes", holder.state.value.directory.upRow!!.directoryPath)
    }

    @Test
    fun selectedFilePreviewFetchesOnceAndFailureDoesNotBlockStart() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeFileBrowserClient()
        client.metadataResult = null
        val holder = holder(client)

        holder.loadRoot()
        val file = holder.state.value.directory.rows.first { it.kind == FileBrowserRowKind.File }
        holder.selectFile(file)
        holder.selectFile(file)

        assertEquals(listOf("new.gcode"), client.metadataRequests)
        assertNull(holder.state.value.selectedPreview)

        holder.requestStartSelected()
        assertEquals(listOf("new.gcode"), client.starts)
        assertEquals(FileBrowserPendingAction.Starting("new.gcode"), holder.state.value.pendingAction)
    }

    @Test
    fun startPendingClearsOnlyFromMatchingPrinterState() = runTest(UnconfinedTestDispatcher()) {
        val printer = MutableStateFlow(PrinterState())
        val client = FakeFileBrowserClient()
        val holder = holder(client, printer)

        holder.loadRoot()
        holder.selectFile(holder.state.value.directory.rows.first { it.name == "new.gcode" })
        holder.requestStartSelected()
        assertEquals(FileBrowserPendingAction.Starting("new.gcode"), holder.state.value.pendingAction)

        printer.value = PrinterState(printState = PrintState.Printing, printFilename = "other.gcode")
        runCurrent()
        assertEquals("wrong filename does not clear pending", FileBrowserPendingAction.Starting("new.gcode"), holder.state.value.pendingAction)

        printer.value = PrinterState(printState = PrintState.Printing, printFilename = "new.gcode")
        runCurrent()
        assertNull(holder.state.value.pendingAction)
    }

    @Test
    fun deleteIsBlockedWhilePrintingOrPaused() = runTest(UnconfinedTestDispatcher()) {
        val printer = MutableStateFlow(PrinterState(printState = PrintState.Printing))
        val client = FakeFileBrowserClient()
        val holder = holder(client, printer)

        holder.loadRoot()
        holder.selectFile(holder.state.value.directory.rows.first { it.name == "new.gcode" })
        holder.requestDeleteSelected()

        assertTrue(client.deletes.isEmpty())
        assertEquals("Cannot delete while a print is active.", holder.state.value.error)

        printer.value = PrinterState(printState = PrintState.Paused)
        holder.requestDeleteSelected()
        assertTrue(client.deletes.isEmpty())
    }

    @Test
    fun successfulDeleteRemovesRowClearsSelectionAndStaysInFolder() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeFileBrowserClient()
        val holder = holder(client)

        holder.loadRoot()
        holder.selectFile(holder.state.value.directory.rows.first { it.name == "new.gcode" })
        holder.requestDeleteSelected()

        assertEquals(listOf("gcodes/new.gcode"), client.deletes)
        assertEquals("gcodes", holder.state.value.directory.directoryPath)
        assertTrue(holder.state.value.directory.rows.none { it.name == "new.gcode" })
        assertNull(holder.state.value.selectedFile)
        assertNull(holder.state.value.selectedPreview)
    }

    private fun TestScope.holder(
        client: FakeFileBrowserClient = FakeFileBrowserClient(),
        printerState: MutableStateFlow<PrinterState> = MutableStateFlow(PrinterState()),
    ) = FileBrowserHolder(backgroundScope, client, printerState)

    private companion object {
        val directoryJson: JsonElement = MoonrakerJson.parseToJsonElement(
            """
            {
              "dirs": [{ "dirname": "folder", "modified": 10.0 }],
              "files": [
                { "filename": "old.gcode", "modified": 1.0, "size": 1 },
                { "filename": "new.gcode", "modified": 2.0, "size": 2 }
              ]
            }
            """.trimIndent(),
        )
        val metadataJson: JsonElement = MoonrakerJson.parseToJsonElement(
            """{ "estimated_time": 120, "thumbnails": [{ "width": 300, "relative_path": ".thumbs/new.png" }] }""",
        )
    }
}
