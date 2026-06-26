package works.mees.jiib.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.jiib.spool.SpoolmanFilament
import works.mees.jiib.spool.SpoolmanVendor
import works.mees.jiib.ui.spool.spoolRowText

class SpoolRowTextTest {

    @Test
    fun allThreeFields_joinedWithSlash() {
        val f = SpoolmanFilament(name = "Galaxy Black", material = "PLA", vendor = SpoolmanVendor(name = "Hatchbox"))
        assertEquals("Galaxy Black / PLA / Hatchbox", spoolRowText(f))
    }

    @Test
    fun missingFields_dropOut() {
        assertEquals("PLA / Hatchbox", spoolRowText(SpoolmanFilament(material = "PLA", vendor = SpoolmanVendor(name = "Hatchbox"))))
        assertEquals("Galaxy Black", spoolRowText(SpoolmanFilament(name = "Galaxy Black")))
    }

    @Test
    fun blankFields_areTreatedAsAbsent() {
        val f = SpoolmanFilament(name = "  ", material = "PETG", vendor = SpoolmanVendor(name = ""))
        assertEquals("PETG", spoolRowText(f))
    }

    @Test
    fun noUsableFields_returnsNull() {
        assertNull(spoolRowText(null))
        assertNull(spoolRowText(SpoolmanFilament()))
        assertNull(spoolRowText(SpoolmanFilament(name = "   ")))
    }
}
