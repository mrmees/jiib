package works.mees.dinghy.ui.heaters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.spool.SpoolmanFilament
import works.mees.dinghy.spool.SpoolmanSpool

class HeatersModelTest {
    @Test fun `loadedSpoolTemps null when no spool`() {
        assertNull(loadedSpoolTemps(null, "Loaded filament"))
    }

    @Test fun `loadedSpoolTemps null when spool has no temps`() {
        val spool = SpoolmanSpool(id = 1, filament = SpoolmanFilament(name = "PLA"))
        assertNull(loadedSpoolTemps(spool, "Loaded filament"))
    }

    @Test fun `loadedSpoolTemps maps nozzle bed color label`() {
        val spool = SpoolmanSpool(
            id = 1,
            filament = SpoolmanFilament(
                name = "Galaxy Black", material = "PLA",
                colorHex = "112233", settingsExtruderTemp = 210, settingsBedTemp = 60,
            ),
        )
        val r = loadedSpoolTemps(spool, "Loaded filament")!!
        assertEquals(210, r.nozzle)
        assertEquals(60, r.bed)
        assertEquals("#112233", r.colorHex)
        assertEquals("Galaxy Black", r.label)
    }

    @Test fun `loadedSpoolTemps label falls back to material then default`() {
        val matOnly = SpoolmanSpool(id = 1, filament = SpoolmanFilament(material = "PETG", settingsExtruderTemp = 240))
        assertEquals("PETG", loadedSpoolTemps(matOnly, "Loaded filament")!!.label)
        val neither = SpoolmanSpool(id = 1, filament = SpoolmanFilament(settingsBedTemp = 60))
        assertEquals("Loaded filament", loadedSpoolTemps(neither, "Loaded filament")!!.label)
    }

    @Test fun `heatSummary sorts by key and joins values`() {
        assertEquals("210/60", heatSummary(mapOf("heater_bed" to 60, "extruder" to 210)))
        assertEquals("", heatSummary(emptyMap()))
    }
}
