package works.mees.jiib.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeaterNamingTest {
    @Test fun displayNames() {
        assertEquals("Nozzle", heaterDisplayName("extruder"))
        assertEquals("Nozzle 1", heaterDisplayName("extruder1"))
        assertEquals("Bed", heaterDisplayName("heater_bed"))
        assertEquals("Chamber", heaterDisplayName("heater_generic chamber"))
        assertEquals("Exhaust", heaterDisplayName("temperature_fan exhaust"))
    }

    @Test fun enumerate_ordersExtruderBedGenericFan_withLimits() {
        val limits = mapOf(
            "heater_generic chamber" to HeaterLimits(0.0, 120.0),
            "extruder" to HeaterLimits(0.0, 300.0),
            "heater_bed" to HeaterLimits(0.0, 130.0),
        )
        val fans = listOf("temperature_fan exhaust")
        val out = enumerateSettableHeaters(limits, fans)
        assertEquals(listOf("extruder", "heater_bed", "heater_generic chamber", "temperature_fan exhaust"), out.map { it.objectName })
        assertEquals(listOf("Nozzle", "Bed", "Chamber", "Exhaust"), out.map { it.displayName })
        assertEquals(300, out[0].maxTemp)            // from limits
        assertNull(out[3].maxTemp)                   // fan limits not parsed → null (global clamp applies)
    }

    @Test fun enumerate_whenNothingKnown_fallsBackToExtruderAndBed() {
        // Never-connected printer: no heaterLimits, no fans → design fallback is extruder + bed.
        val out = enumerateSettableHeaters(emptyMap(), emptyList())
        assertEquals(listOf("extruder", "heater_bed"), out.map { it.objectName })
        assertEquals(listOf("Nozzle", "Bed"), out.map { it.displayName })
    }
}
