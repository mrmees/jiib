package works.mees.dinghy.ui.files

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.state.FileBrowserRow
import works.mees.dinghy.state.FileBrowserRowKind

private fun fileRow(name: String, size: Long?, modified: Double?): FileBrowserRow =
    FileBrowserRow(
        kind = FileBrowserRowKind.File,
        name = name,
        stableId = name,
        relativeFilename = name,
        sizeBytes = size,
        modifiedEpochSeconds = modified,
    )

class FilesSortTest {
    private val a = fileRow("a.gcode", size = 100, modified = 10.0)
    private val b = fileRow("b.gcode", size = 300, modified = 30.0)
    private val c = fileRow("c.gcode", size = 200, modified = 20.0)
    private val rows = listOf(a, b, c)

    @Test
    fun `date descending is newest first`() {
        val out = sortFileRows(rows, FileSortField.Date, ascending = false)
        assertEquals(listOf(b, c, a), out)
    }

    @Test
    fun `date ascending is oldest first`() {
        val out = sortFileRows(rows, FileSortField.Date, ascending = true)
        assertEquals(listOf(a, c, b), out)
    }

    @Test
    fun `size descending is largest first`() {
        val out = sortFileRows(rows, FileSortField.Size, ascending = false)
        assertEquals(listOf(b, c, a), out)
    }

    @Test
    fun `size ascending is smallest first`() {
        val out = sortFileRows(rows, FileSortField.Size, ascending = true)
        assertEquals(listOf(a, c, b), out)
    }

    @Test
    fun `null size sinks to bottom when descending`() {
        val nullSize = fileRow("z.gcode", size = null, modified = 5.0)
        val out = sortFileRows(rows + nullSize, FileSortField.Size, ascending = false)
        assertEquals(nullSize, out.last())
    }

    @Test
    fun `Size default direction is largest first`() {
        assertEquals(false, FileSortField.Size.defaultAscending)
    }

    @Test
    fun `Date default direction is newest first`() {
        assertEquals(false, FileSortField.Date.defaultAscending)
    }
}
