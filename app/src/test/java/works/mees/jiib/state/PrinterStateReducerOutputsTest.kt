package works.mees.jiib.state

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase-19 (19-04) output live-value reduction (SC-1/SC-3). Proves the [PrinterState.outputs] reducer
 * stores RAW values (no scaling — Pitfall 1) keyed by the FULL objectKey, uses UPDATE-ON-PRESENT merge (an
 * absent field RETAINS prior, never clobbered), keeps color_data index 0 on a partial update, and that
 * heater_generic is SINGLE-SOURCED through [PrinterState.heaters] (NOT duplicated/contradicted in outputs).
 */
class PrinterStateReducerOutputsTest {

    private fun status(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun fanGenericSpeedReducedRawByObjectKey() {
        val s = reduceSnapshot(status("""{"fan_generic FILTER_fan":{"speed":0.5}}"""))
        // RAW 0..1 — NO /100 scaling in the reducer; keyed by the FULL objectKey.
        assertEquals(0.5, s.outputs["fan_generic FILTER_fan"]!!.speed!!, 0.0001)
    }

    @Test
    fun servoAndPinValueReducedRaw() {
        val s = reduceSnapshot(
            status("""{"servo camera_servo":{"value":0.75},"output_pin mosfet2":{"value":1.0}}"""),
        )
        assertEquals(0.75, s.outputs["servo camera_servo"]!!.value!!, 0.0001)
        assertEquals(1.0, s.outputs["output_pin mosfet2"]!!.value!!, 0.0001)
    }

    @Test
    fun partialDiffUpdatesOnlyPresentFieldRetainsOthers() {
        // Seed a fan with a speed, then apply a diff for the SAME object that carries NO speed (a partial,
        // unrelated field). The prior speed must SURVIVE (update-on-present, never clobbered — SC-3).
        val seeded = reduceSnapshot(status("""{"fan_generic FILTER_fan":{"speed":0.4}}"""))
        val merged = reduceDiff(seeded, status("""{"fan_generic FILTER_fan":{"rpm":1200}}"""))
        assertEquals("absent speed retains prior", 0.4, merged.outputs["fan_generic FILTER_fan"]!!.speed!!, 0.0001)
    }

    @Test
    fun absentObjectRetainsPriorValue() {
        // A diff that omits the output object entirely must RETAIN the prior value (never dropped/clobbered).
        val seeded = reduceSnapshot(status("""{"output_pin mosfet2":{"value":0.3}}"""))
        val merged = reduceDiff(seeded, status("""{"extruder":{"temperature":210}}"""))
        assertEquals(0.3, merged.outputs["output_pin mosfet2"]!!.value!!, 0.0001)
    }

    @Test
    fun colorDataPartialUpdateKeepsIndexZero() {
        val seeded = reduceSnapshot(
            status("""{"led chamber_light":{"color_data":[[0.9,0.3,0.0,0.0]]}}"""),
        )
        // A later diff that does NOT carry color_data must keep index 0 (retained).
        val merged = reduceDiff(seeded, status("""{"led chamber_light":{}}"""))
        val cd = merged.outputs["led chamber_light"]!!.colorData!!
        assertEquals(listOf(0.9, 0.3, 0.0, 0.0), cd[0])
        // A diff that DOES carry color_data replaces it (whole-array semantics for color_data).
        val updated = reduceDiff(seeded, status("""{"led chamber_light":{"color_data":[[0.1,0.2,0.3,0.4]]}}"""))
        assertEquals(listOf(0.1, 0.2, 0.3, 0.4), updated.outputs["led chamber_light"]!!.colorData!![0])
    }

    @Test
    fun heaterGenericFlowsThroughHeatersNotOutputs() {
        // heater_generic divergence guard (MEDIUM review fix): its current temp/target are observable via the
        // single existing heaters map, and it is NOT duplicated/contradicted in the outputs map.
        val s = reduceSnapshot(
            status("""{"heater_generic chamber_heater":{"temperature":42.0,"target":50.0}}"""),
        )
        assertEquals(42.0, s.heaters["heater_generic chamber_heater"]!!.temperature, 0.0001)
        assertEquals(50.0, s.heaters["heater_generic chamber_heater"]!!.target, 0.0001)
        assertNull("heater_generic not duplicated in outputs", s.outputs["heater_generic chamber_heater"])
    }

    @Test
    fun multipleFamiliesCoexistByKey() {
        val s = reduceSnapshot(
            status(
                """{"fan_generic FILTER_fan":{"speed":0.5},""" +
                    """"servo camera_servo":{"value":0.5},""" +
                    """"led chamber_light":{"color_data":[[1.0,1.0,1.0,1.0]]}}""",
            ),
        )
        assertEquals(3, s.outputs.size)
        assertTrue("fan_generic FILTER_fan" in s.outputs)
        assertTrue("servo camera_servo" in s.outputs)
        assertTrue("led chamber_light" in s.outputs)
    }
}
