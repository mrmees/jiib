package works.mees.dinghy.state

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson

/**
 * R-CDX-1/R-CDX-7: stepper_enable → motorsEnabled. Motion steppers only (extruder steppers excluded);
 * retain-on-absent merge; null until first reported.
 */
class PrinterStateReducerMotorsTest {

    private fun diff(json: String) = MoonrakerJson.parseToJsonElement(json).jsonObject

    @Test
    fun motorsEnabledDefaultsToNull() {
        assertNull(PrinterState().motorsEnabled)
    }

    @Test
    fun anyMotionStepperEnabledSetsTrue() {
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "stepper_enable": { "steppers": { "stepper_x": false, "stepper_y": true, "stepper_z": false } } }"""),
        )
        assertEquals(true, s.motorsEnabled)
    }

    @Test
    fun allMotionSteppersDisabledSetsFalse() {
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "stepper_enable": { "steppers": { "stepper_x": false, "stepper_y": false, "stepper_z": false } } }"""),
        )
        assertEquals(false, s.motorsEnabled)
    }

    @Test
    fun extruderSteppersAreIgnored() {
        // Only extruder steppers enabled → motion motors are OFF.
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "stepper_enable": { "steppers": { "stepper_x": false, "extruder": true, "extruder1": true } } }"""),
        )
        assertEquals(false, s.motorsEnabled)
    }

    @Test
    fun extraZSteppersCount() {
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "stepper_enable": { "steppers": { "stepper_x": false, "stepper_z1": true } } }"""),
        )
        assertEquals(true, s.motorsEnabled)
    }

    @Test
    fun absentStepperEnableRetainsPriorValue() {
        val enabled = reduceDiff(
            PrinterState(),
            diff("""{ "stepper_enable": { "steppers": { "stepper_x": true } } }"""),
        )
        // A later, unrelated diff must NOT clear motorsEnabled (retain-on-absent).
        val after = reduceDiff(enabled, diff("""{ "heater_bed": { "temperature": 41.2 } }"""))
        assertEquals(true, after.motorsEnabled)
    }
}
