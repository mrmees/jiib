package works.mees.dinghy.ui.extrude

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.state.reduceDiff
import works.mees.dinghy.state.reduceSnapshot

/**
 * Filament-runout-sensor live-state reduction (Task 3, Extrude rework). Mirrors the outputs/
 * temperature_sensor reducer pipeline: subscribe `filament_switch_sensor`/`filament_motion_sensor`,
 * reduce `enabled`/`filament_detected` into [works.mees.dinghy.state.PrinterState.filamentSensors]
 * with UPDATE-ON-PRESENT merge (an absent field RETAINS prior).
 */
class ExtrudeSensorReducerTest {

    private fun status(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun switchSensorReducesEnabledAndDetected() {
        val s = reduceSnapshot(
            status("""{"filament_switch_sensor Runout":{"enabled":true,"filament_detected":false}}"""),
        )
        val sensor = s.filamentSensors["filament_switch_sensor Runout"]!!
        assertTrue(sensor.enabled)
        assertEquals(false, sensor.filamentDetected)
    }

    @Test
    fun partialDiffRetainsPriorFields() {
        val seeded = reduceSnapshot(
            status("""{"filament_motion_sensor Encoder":{"enabled":true,"filament_detected":true}}"""),
        )
        val merged = reduceDiff(
            seeded,
            status("""{"filament_motion_sensor Encoder":{"enabled":false}}"""),
        )
        val sensor = merged.filamentSensors["filament_motion_sensor Encoder"]!!
        assertFalse(sensor.enabled) // present field updates
        assertEquals(true, sensor.filamentDetected) // absent field retained
    }
}
