package works.mees.jiib.state

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.MoonrakerJson

/**
 * Host tests for the PURE [parseTemperatureStore] mapper (TEMP-04, first history-endpoint use).
 *
 * Contract proven here, off-hardware (the REAL `server.temperature_store` shape is exercised live on the
 * Ender 5 Plus during 05-08 UAT — keep the fixtures faithful to the documented shape: an object keyed by
 * sensor name, each value carrying a `temperatures` FIFO array with index 0 = OLDEST).
 */
class TemperatureStoreBackfillTest {

    private fun parse(json: String) = MoonrakerJson.parseToJsonElement(json).jsonObject

    @Test
    fun `maps requested sensors oldest-first preserving order`() {
        val result = parse(
            """{ "extruder": { "temperatures": [21.0, 22.0, 23.0] },
                "heater_bed": { "temperatures": [60.0, 61.0] } }""",
        )

        val series = parseTemperatureStore(result, sensors = setOf("extruder", "heater_bed"))

        assertArrayEquals(floatArrayOf(21.0f, 22.0f, 23.0f), series["extruder"]!!, 0f)
        assertArrayEquals(floatArrayOf(60.0f, 61.0f), series["heater_bed"]!!, 0f)
        assertEquals(2, series.size)
    }

    @Test
    fun `omits a requested sensor absent from the response (never fabricates)`() {
        val result = parse("""{ "extruder": { "temperatures": [200.0] } }""")

        val series = parseTemperatureStore(result, sensors = setOf("extruder", "heater_bed"))

        assertTrue("extruder" in series)
        assertFalse("absent sensor must not be fabricated as an empty array", "heater_bed" in series)
    }

    @Test
    fun `ignores a present sensor that was not requested`() {
        val result = parse(
            """{ "extruder": { "temperatures": [200.0] },
                "temperature_sensor mcu": { "temperatures": [40.0, 41.0] } }""",
        )

        val series = parseTemperatureStore(result, sensors = setOf("extruder"))

        assertEquals(setOf("extruder"), series.keys.toSet())
    }

    @Test
    fun `omits a sensor present but missing its temperatures array`() {
        val result = parse(
            """{ "extruder": { "targets": [0.0], "powers": [0.0] } }""",
        )

        val series = parseTemperatureStore(result, sensors = setOf("extruder"))

        assertTrue("no temperatures array -> omitted, never an empty array", series.isEmpty())
    }

    @Test
    fun `skips non-finite or garbage samples like the reducer`() {
        val result = parse(
            """{ "extruder": { "temperatures": [21.0, "oops", 23.0] } }""",
        )

        val series = parseTemperatureStore(result, sensors = setOf("extruder"))

        assertArrayEquals(floatArrayOf(21.0f, 23.0f), series["extruder"]!!, 0f)
    }
}
