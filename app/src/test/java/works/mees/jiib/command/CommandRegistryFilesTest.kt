package works.mees.jiib.command

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.JsonRpcMethods

class CommandRegistryFilesTest {

    @Test
    fun phase7RuntimeCommandsAreRegisteredOnceAndReferenceOnlyRowsStayUnregistered() {
        val ids = CommandRegistry.all.map { it.catalogId }
        val expected = setOf(
            "MR-server.files.get_directory",
            "MR-server.files.thumbnails",
            "MR-server.files.delete_file",
            "MR-printer.print.start",
            "MR-printer.print.pause",
            "MR-printer.print.resume",
            "MR-printer.print.cancel",
        )

        for (id in expected) {
            assertEquals("$id registered exactly once", 1, ids.count { it == id })
        }
        assertFalse("server.files.roots stays reference-only", "MR-server.files.roots" in ids)
        assertFalse("server.files.list stays reference-only", "MR-server.files.list" in ids)
    }

    @Test
    fun directoryBrowseParamsPreservePathAndExtendedSemantics() {
        assertEquals(JsonRpcMethods.FILES_GET_DIRECTORY, CommandRegistry.filesGetDirectory.method)
        assertEquals("files_browse_gcodes/folder", CommandRegistry.filesGetDirectory.dispatchKey(FileDirectoryArgs("gcodes/folder")))
        assertEquals(
            AvailabilityPredicate.ComponentPresent("file_manager"),
            CommandRegistry.filesGetDirectory.availability,
        )

        val root = CommandRegistry.filesGetDirectory.params(FileDirectoryArgs(path = null, extended = true))!!.jsonObject
        assertFalse("root browse omits path", "path" in root)
        assertEquals(true, root["extended"]!!.jsonPrimitive.boolean)

        val subdir = CommandRegistry.filesGetDirectory.params(FileDirectoryArgs(path = "gcodes/sub", extended = false))!!.jsonObject
        assertEquals("gcodes/sub", subdir.string("path"))
        assertEquals(false, subdir["extended"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun filePreviewAndMutationParamsKeepMoonrakerPathFormsDistinct() {
        assertEquals(JsonRpcMethods.FILES_THUMBNAILS, CommandRegistry.filesThumbnails.method)
        assertEquals(JsonRpcMethods.FILES_DELETE_FILE, CommandRegistry.filesDelete.method)
        assertEquals(JsonRpcMethods.PRINT_START, CommandRegistry.printStart.method)

        val thumbnail = CommandRegistry.filesThumbnails.params(FileNameArgs("folder/cube.gcode"))!!.jsonObject
        assertEquals("folder/cube.gcode", thumbnail.string("filename"))

        val delete = CommandRegistry.filesDelete.params(FileDeleteArgs("gcodes/folder/cube.gcode"))!!.jsonObject
        assertEquals("gcodes/folder/cube.gcode", delete.string("path"))

        val start = CommandRegistry.printStart.params(PrintStartArgs("folder/cube.gcode"))!!.jsonObject
        assertEquals("folder/cube.gcode", start.string("filename"))
        assertFalse("start filename is relative, not root-prefixed", start.string("filename").startsWith("gcodes/"))
    }

    @Test
    fun printControlsUsePauseResumeGateAndEmptyParams() {
        val controls = listOf(
            CommandRegistry.printPause to "pause_print",
            CommandRegistry.printResume to "resume_print",
            CommandRegistry.printCancel to "cancel_print",
        )

        for ((spec, key) in controls) {
            assertEquals(AvailabilityPredicate.ObjectPresent("pause_resume"), spec.availability)
            assertEquals(key, spec.dispatchKey(Unit))
            assertNull("${spec.catalogId} has no params", spec.params(Unit))
            assertTrue(
                "${spec.catalogId} acceptance is state-confirmed",
                spec.semantics.acceptance.contains("state", ignoreCase = true),
            )
        }
        assertEquals(JsonRpcMethods.PRINT_PAUSE, CommandRegistry.printPause.method)
        assertEquals(JsonRpcMethods.PRINT_RESUME, CommandRegistry.printResume.method)
        assertEquals(JsonRpcMethods.PRINT_CANCEL, CommandRegistry.printCancel.method)
    }

    @Test
    fun printStartDocumentsStateConfirmedAcceptance() {
        assertEquals(AvailabilityPredicate.ObjectPresent("virtual_sdcard"), CommandRegistry.printStart.availability)
        assertEquals("start_print_folder/cube.gcode", CommandRegistry.printStart.dispatchKey(PrintStartArgs("folder/cube.gcode")))
        assertTrue(CommandRegistry.printStart.semantics.acceptance.contains("print_stats", ignoreCase = true))
        assertTrue(CommandRegistry.printStart.semantics.acceptance.contains("virtual_sdcard", ignoreCase = true))
    }

    private fun JsonObject.string(key: String): String {
        val element = this[key]
        assertNotNull("Missing $key in $this", element)
        return element!!.jsonPrimitive.content
    }
}
