package works.mees.dinghy.state

import kotlinx.serialization.json.jsonObject
import works.mees.dinghy.net.MoonrakerJson
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        assertContentEquals(floatArrayOf(21.0f, 22.0f, 23.0f), series["extruder"])
        assertContentEquals(floatArrayOf(60.0f, 61.0f), series["heater_bed"])
        assertEquals(2, series.size)
    }

    @Test
    fun `omits a requested sensor absent from the response (never fabricates)`() {
        val result = parse("""{ "extruder": { "temperatures": [200.0] } }""")

        val series = parseTemperatureStore(result, sensors = setOf("extruder", "heater_bed"))

        assertTrue("extruder" in series)
        assertFalse("heater_bed" in series, "absent sensor must not be fabricated as an empty array")
    }

    @Test
    fun `ignores a present sensor that was not requested`() {
        val result = parse(
            """{ "extruder": { "temperatures": [200.0] },
                "temperature_sensor mcu": { "temperatures": [40.0, 41.0] } }""",
        )

        val series = parseTemperatureStore(result, sensors = setOf("extruder"))

        assertEquals(setOf("extruder"), series.keys)
    }

    @Test
    fun `omits a sensor present but missing its temperatures array`() {
        val result = parse(
            """{ "extruder": { "targets": [0.0], "powers": [0.0] } }""",
        )

        val series = parseTemperatureStore(result, sensors = setOf("extruder"))

        assertTrue(series.isEmpty(), "no temperatures array -> omitted, never an empty array")
    }

    @Test
    fun `skips non-finite or garbage samples like the reducer`() {
        val result = parse(
            """{ "extruder": { "temperatures": [21.0, "oops", 23.0] } }""",
        )

        val series = parseTemperatureStore(result, sensors = setOf("extruder"))

        assertContentEquals(floatArrayOf(21.0f, 23.0f), series["extruder"])
    }
}
