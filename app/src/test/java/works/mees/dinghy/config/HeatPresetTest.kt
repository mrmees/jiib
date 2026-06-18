package works.mees.dinghy.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeatPresetTest {

    @Test
    fun extruderTemp_readsExtruderKey_orNull() {
        assertEquals(200, HeatPreset("a", "Med", mapOf("extruder" to 200, "heater_bed" to 60)).extruderTemp)
        assertNull(HeatPreset("b", "BedOnly", mapOf("heater_bed" to 60)).extruderTemp)
    }

    @Test
    fun sortedForDisplay_byExtruderAsc_nullsLast_thenNameCaseInsensitive() {
        val high = HeatPreset("h", "High", mapOf("extruder" to 230))
        val low = HeatPreset("l", "Low", mapOf("extruder" to 150))
        val med = HeatPreset("m", "Medium", mapOf("extruder" to 200))
        val noExtA = HeatPreset("z", "alpha", mapOf("heater_bed" to 60))
        val noExtB = HeatPreset("y", "Beta", mapOf("heater_bed" to 70))
        val sorted = listOf(high, noExtB, med, noExtA, low).sortedForDisplay()
        assertEquals(listOf("Low", "Medium", "High", "alpha", "Beta"), sorted.map { it.name })
    }

    @Test
    fun defaultHeatPresets_areLowMedHigh_extruderAndBedOnly_uniqueIds() {
        val d = defaultHeatPresets()
        assertEquals(listOf("Low", "Medium", "High"), d.map { it.name })
        assertEquals(mapOf("extruder" to 150, "heater_bed" to 50), d[0].setpoints)
        assertEquals(mapOf("extruder" to 200, "heater_bed" to 65), d[1].setpoints)
        assertEquals(mapOf("extruder" to 230, "heater_bed" to 90), d[2].setpoints)
        assertEquals(3, d.map { it.id }.toSet().size)
    }
}
