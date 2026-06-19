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

    @Test fun `loadedSpoolTemps maps nozzle bed color, label is the material type`() {
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
        assertEquals("PLA", r.label) // material (type), not the color name
    }

    @Test fun `loadedSpoolTemps label falls back to name then default when no material`() {
        val nameOnly = SpoolmanSpool(id = 1, filament = SpoolmanFilament(name = "Galaxy Black", settingsExtruderTemp = 240))
        assertEquals("Galaxy Black", loadedSpoolTemps(nameOnly, "Loaded filament")!!.label)
        val neither = SpoolmanSpool(id = 1, filament = SpoolmanFilament(settingsBedTemp = 60))
        assertEquals("Loaded filament", loadedSpoolTemps(neither, "Loaded filament")!!.label)
    }

    @Test fun `heatSummary sorts by key and joins values`() {
        assertEquals("210/60", heatSummary(mapOf("heater_bed" to 60, "extruder" to 210)))
        assertEquals("", heatSummary(emptyMap()))
    }

    private val caps = works.mees.dinghy.state.Capabilities(
        objects = setOf("extruder", "heater_bed"),
    )
    private val noBed = works.mees.dinghy.state.Capabilities(objects = setOf("extruder"))
    private val presets = listOf(
        works.mees.dinghy.config.HeatPreset("p1", "PLA", mapOf("extruder" to 200, "heater_bed" to 60)),
        works.mees.dinghy.config.HeatPreset("p2", "Bed only", mapOf("heater_bed" to 50)),
    )

    @Test fun `OFF pinned top, spool second, presets follow`() {
        val spool = LoadedSpoolTemps(210, 60, "#fff", "Galaxy")
        val rows = buildHeatersRows(presets, spool, HeatScope.Full, "extruder", caps)
        assertEquals(listOf("heat_off", "heat_spool", "preset_p1", "preset_p2"), rows.map { it.key })
        assertEquals(works.mees.dinghy.designsystem.icons.DinghyIcons.HeatersOff, rows[0].icon)
        assertEquals(works.mees.dinghy.designsystem.icons.DinghyIcons.SpoolFilament, rows[1].icon)
        assertEquals("#fff", rows[1].tintHex)
    }

    @Test fun `Full OFF turns off all, Extruder OFF sets active heater to 0`() {
        val full = buildHeatersRows(emptyList(), null, HeatScope.Full, "extruder", caps)
        assertEquals(HeatDispatch.TurnOffAll, full[0].dispatch)
        val ext = buildHeatersRows(emptyList(), null, HeatScope.ExtruderOnly, "extruder1", caps)
        assertEquals(HeatDispatch.ApplyPreset(mapOf("extruder1" to 0), "heat_off"), ext[0].dispatch)
    }

    @Test fun `Full preset drops heaters the printer lacks`() {
        val rows = buildHeatersRows(presets, null, HeatScope.Full, "extruder", noBed)
        // p1 keeps only extruder; p2 (bed only) is dropped entirely on a bedless printer.
        val p1 = rows.first { it.key == "preset_p1" }.dispatch as HeatDispatch.ApplyPreset
        assertEquals(mapOf("extruder" to 200), p1.setpoints)
        assertEquals(null, rows.firstOrNull { it.key == "preset_p2" })
    }

    @Test fun `ExtruderOnly preset maps to active heater, drops no-extruder presets`() {
        val rows = buildHeatersRows(presets, null, HeatScope.ExtruderOnly, "extruder1", caps)
        val p1 = rows.first { it.key == "preset_p1" }.dispatch as HeatDispatch.ApplyPreset
        assertEquals(mapOf("extruder1" to 200), p1.setpoints)
        assertEquals(null, rows.firstOrNull { it.key == "preset_p2" }) // p2 has no extruder temp
    }

    @Test fun `Full spool applies nozzle and bed, ExtruderOnly nozzle only`() {
        val spool = LoadedSpoolTemps(210, 60, null, "Galaxy")
        val full = (buildHeatersRows(emptyList(), spool, HeatScope.Full, "extruder", caps)
            .first { it.key == "heat_spool" }.dispatch as HeatDispatch.ApplyPreset)
        assertEquals(mapOf("extruder" to 210, "heater_bed" to 60), full.setpoints)
        val ext = (buildHeatersRows(emptyList(), spool, HeatScope.ExtruderOnly, "extruder1", caps)
            .first { it.key == "heat_spool" }.dispatch as HeatDispatch.ApplyPreset)
        assertEquals(mapOf("extruder1" to 210), ext.setpoints)
    }

    @Test fun `bed-only spool shown under Full, hidden under ExtruderOnly`() {
        val bedOnly = LoadedSpoolTemps(null, 60, null, "Galaxy")
        val full = buildHeatersRows(emptyList(), bedOnly, HeatScope.Full, "extruder", caps)
        assertEquals(mapOf("heater_bed" to 60), (full.first { it.key == "heat_spool" }.dispatch as HeatDispatch.ApplyPreset).setpoints)
        val ext = buildHeatersRows(emptyList(), bedOnly, HeatScope.ExtruderOnly, "extruder", caps)
        assertNull(ext.firstOrNull { it.key == "heat_spool" })
    }

    @Test fun `Full spool drops bed on a bedless printer`() {
        val spool = LoadedSpoolTemps(210, 60, null, "Galaxy")
        val rows = buildHeatersRows(emptyList(), spool, HeatScope.Full, "extruder", noBed)
        // No heater_bed in capabilities → bed setpoint dropped, nozzle kept (no invalid bed gcode).
        val spoolDispatch = rows.first { it.key == "heat_spool" }.dispatch as HeatDispatch.ApplyPreset
        assertEquals(mapOf("extruder" to 210), spoolDispatch.setpoints)
    }
}
