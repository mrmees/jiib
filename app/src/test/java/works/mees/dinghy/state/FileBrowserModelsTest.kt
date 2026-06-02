package works.mees.dinghy.state

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson

class FileBrowserModelsTest {

    @Test
    fun parsesRootDirectoryAndKeepsPathFormsDistinct() {
        val directory = parseFileBrowserDirectory(rootDirectoryJson(), directoryPath = "gcodes")

        assertEquals("gcodes", directory.directoryPath)
        assertEquals("", directory.displayPath)
        assertNull("root directory has no Up row", directory.upRow)

        val firstFolder = directory.rows[0]
        assertEquals(FileBrowserRowKind.Directory, firstFolder.kind)
        assertEquals("calibration", firstFolder.name)
        assertEquals("gcodes/calibration", firstFolder.directoryPath)

        val firstFile = directory.rows.first { it.kind == FileBrowserRowKind.File }
        assertEquals("new cube.gcode", firstFile.name)
        assertEquals("new cube.gcode", firstFile.relativeFilename)
        assertEquals("gcodes/new cube.gcode", firstFile.rootPrefixedPath)
        assertEquals("new cube.gcode", FileBrowserPaths.relativeFilename(firstFile.rootPrefixedPath!!))
        assertEquals("gcodes/new cube.gcode", FileBrowserPaths.rootPrefixedPath(firstFile.relativeFilename!!))
        assertEquals("gcodes", FileBrowserPaths.directoryPath(null))
    }

    @Test
    fun subdirectoryHasUpRowAndRelativeFilenames() {
        val directory = parseFileBrowserDirectory(subdirectoryJson(), directoryPath = "gcodes/calibration")

        val up = directory.upRow
        assertNotNull(up)
        assertEquals(FileBrowserRowKind.Up, up!!.kind)
        assertEquals("gcodes", up.directoryPath)
        assertEquals("calibration", directory.displayPath)

        val file = directory.rows.single { it.kind == FileBrowserRowKind.File }
        assertEquals("calibration/probe test.gcode", file.relativeFilename)
        assertEquals("gcodes/calibration/probe test.gcode", file.rootPrefixedPath)
        assertEquals("gcodes/calibration", FileBrowserPaths.directoryPath("calibration"))
    }

    @Test
    fun foldersGroupAboveFilesAndFilesSortRecentFirstWithUnknownLast() {
        val rows = parseFileBrowserDirectory(rootDirectoryJson(), directoryPath = "gcodes").rows

        assertEquals(
            listOf(
                "dir:calibration",
                "dir:older-folder",
                "file:new cube.gcode",
                "file:older cube.gcode",
                "file:unknown cube.gcode",
            ),
            rows.map { "${it.kind.prefix}:${it.name}" },
        )
    }

    @Test
    fun emptyAndMalformedDirectoriesDegradeToSafeRows() {
        val empty = parseFileBrowserDirectory("""{ "dirs": [], "files": [] }""".asJson(), "gcodes")
        assertTrue(empty.rows.isEmpty())

        val malformed = parseFileBrowserDirectory(
            """{
              "dirs": [null, { "dirname": 7 }, { "dirname": "valid" }],
              "files": [42, { "filename": true }, { "filename": "ok.gcode", "modified": "bad", "size": "bad" }]
            }""".asJson(),
            "gcodes",
        )

        assertEquals(listOf("valid", "ok.gcode"), malformed.rows.map { it.name })
        val file = malformed.rows.single { it.kind == FileBrowserRowKind.File }
        assertNull(file.modifiedEpochSeconds)
        assertNull(file.sizeBytes)
    }

    @Test
    fun androidBackIsNotModeledAsFolderNavigation() {
        val directory = parseFileBrowserDirectory(subdirectoryJson(), directoryPath = "gcodes/calibration")

        assertNotNull("folder ascent is an explicit Up row", directory.upRow)
        assertTrue("rows do not contain an Android Back pseudo-row", directory.rows.none { it.name.equals("Back", true) })
    }

    private fun rootDirectoryJson() = """
        {
          "dirs": [
            { "dirname": "older-folder", "modified": 100.0 },
            { "dirname": "calibration", "modified": 200.0 }
          ],
          "files": [
            { "filename": "older cube.gcode", "modified": 100.0, "size": 20 },
            { "filename": "unknown cube.gcode", "size": 10 },
            {
              "filename": "new cube.gcode",
              "modified": 200.0,
              "size": 30,
              "thumbnails": [
                { "width": 32, "relative_path": ".thumbs/new-32x32.png" },
                { "width": 300, "relative_path": ".thumbs/new-300x300.png" }
              ]
            }
          ]
        }
    """.trimIndent().asJson()

    private fun subdirectoryJson() = """
        {
          "dirs": [],
          "files": [
            { "filename": "probe test.gcode", "modified": 50.0, "size": 5 }
          ]
        }
    """.trimIndent().asJson()

    private fun String.asJson() = MoonrakerJson.parseToJsonElement(this).jsonObject
}
