package works.mees.jiib.state

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.MoonrakerJson

class HeaterLimitsTest {

    private fun settings(json: String) = MoonrakerJson.parseToJsonElement(json).jsonObject

    @Test
    fun `maps min and max for heater sections`() {
        val s = settings(
            """
            {
              "extruder": {"min_temp": 0, "max_temp": 300, "pressure_advance": 0.05},
              "heater_bed": {"min_temp": 0, "max_temp": 120},
              "heater_generic chamber": {"min_temp": 0, "max_temp": 80}
            }
            """.trimIndent(),
        )
        val limits = parseHeaterLimits(s)
        assertEquals(300.0, limits["extruder"]!!.maxTemp!!, 0.0001)
        assertEquals(120.0, limits["heater_bed"]!!.maxTemp!!, 0.0001)
        assertEquals(80.0, limits["heater_generic chamber"]!!.maxTemp!!, 0.0001)
        assertEquals(0.0, limits["extruder"]!!.minTemp!!, 0.0001)
    }

    @Test
    fun `ignores non-heater sections`() {
        val s = settings(
            """
            {
              "printer": {"max_velocity": 300, "max_accel": 3000},
              "probe": {"z_offset": 1.2},
              "extruder": {"max_temp": 250}
            }
            """.trimIndent(),
        )
        val limits = parseHeaterLimits(s)
        assertEquals(setOf("extruder"), limits.keys)
    }

    @Test
    fun `omits a heater section that carries no temp fields`() {
        val s = settings("""{ "heater_bed": {"sensor_type": "EPCOS 100K"} }""")
        val limits = parseHeaterLimits(s)
        assertTrue("no temp fields -> omitted", limits.isEmpty())
    }

    @Test
    fun `present min absent max yields null max`() {
        val s = settings("""{ "extruder": {"min_temp": 10} }""")
        val limits = parseHeaterLimits(s)
        assertEquals(10.0, limits["extruder"]!!.minTemp!!, 0.0001)
        assertNull(limits["extruder"]!!.maxTemp)
    }

    @Test
    fun `skips garbled non-numeric temp`() {
        val s = settings("""{ "extruder": {"max_temp": "hot"} }""")
        val limits = parseHeaterLimits(s)
        // garbled max -> null max; section omitted because no usable temp field remains
        assertTrue(limits.isEmpty())
    }
}
