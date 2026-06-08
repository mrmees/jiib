// RED scaffold (Wave 0, 19-02) — turns GREEN in Wave 1 (the output parser/gate plan).
package works.mees.dinghy.outputs

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Wave-0 RED scaffold (19-02) — turned GREEN by the Wave-1 output-discovery/gate plan.
 *
 * Anchors the parser to the REAL Moonraker captures (mock-vs-reality LAW): settings keys are
 * LOWERCASED (`fan_generic filter_fan`), objects.list is case-PRESERVED (`fan_generic FILTER_fan`),
 * servo `.value` is PWM (not angle), led/neopixel carry `color_data` arrays. The gate whitelists
 * fan_generic / led / neopixel / dotstar / pca* / servo / output_pin / pwm_tool and EXCLUDES
 * extruder/heater_bed/fan/temperature_sensor/stepper_*.
 *
 * HARD RULE [[dinghy-wave0-red-scaffold-compile]]: every body here is a typed `fail(...)` and
 * references NO unbuilt symbol (OutputsGate / OutputDescriptor / parseOutputs do not exist yet),
 * because Gradle compiles the WHOLE test sourceset before `--tests` filters — a scaffold that
 * referenced unbuilt symbols would brick every per-wave run in the phase. Wave 1 replaces each
 * `fail()` with the real assertion against `parseOutputs(settings, objectsList)`.
 */
class OutputsGateTest {

    /** Load a Task-1 capture fixture from the test classpath (`app/src/test/resources/outputs/`). */
    private fun fixture(name: String): String =
        this::class.java.classLoader
            ?.getResourceAsStream("outputs/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("fixture not on classpath: outputs/$name")

    /** Proves the real captures resolve from test resources (the link the whole gate test rides on). */
    @Test
    fun realCapturesResolveOnClasspath() {
        val cfg = fixture("configfile_settings_e5p.json")
        val list = fixture("objects_list_e5p.json")
        assertNotNull(cfg)
        assertNotNull(list)
        // The settings view is lowercased; objects.list preserves case — the case-recovery source.
        assertTrue("settings lowercased", cfg.contains("fan_generic filter_fan"))
        assertTrue("objects.list case-preserved", list.contains("fan_generic FILTER_fan"))
    }

    @Test
    fun whitelistFiltersStdOutputs() {
        // Wave 1: parseOutputs(e5p settings, e5p list) yields the 9 whitelisted outputs only
        // (fan_generic/led/neopixel/servo×2/output_pin×4), no excluded sections.
        fail("not implemented — Wave 1")
    }

    @Test
    fun recoversCaseFromObjectsList() {
        // Wave 1: a descriptor's objectKey is the CASE-PRESERVED objects.list name
        // (`fan_generic FILTER_fan`), recovered by matching the lowercased settings key.
        fail("not implemented — Wave 1")
    }

    @Test
    fun commandNameIsBareSectionName() {
        // Wave 1 (HIGH-1): `fan_generic FILTER_fan` → commandName `FILTER_fan` (bare section name,
        // NO family prefix) — the name SET_FAN_SPEED/SET_PIN/SET_LED actually take on the wire.
        fail("not implemented — Wave 1")
    }

    @Test
    fun emptyHidesTile() {
        // Wave 1 (VALIDATION SC): a printer with ZERO whitelisted outputs → parseOutputs is empty
        // → the Outputs tile/section is hidden (no empty shell).
        fail("not implemented — Wave 1")
    }

    @Test
    fun pwmDetection() {
        // Wave 1: an output_pin descriptor branches digital vs PWM on the settings `pwm` boolean
        // (always a real bool in the defaulted settings view) — digital → On/Off, pwm → 0..100%.
        fail("not implemented — Wave 1")
    }

    @Test
    fun servoAngleMaxFromConfig() {
        // Wave 1: a servo descriptor carries maximum_servo_angle (maxDeg) read from its settings
        // section, so the angle scrubber clamps to the real per-servo ceiling.
        fail("not implemented — Wave 1")
    }

    @Test
    fun excludedSectionsNeverAppear() {
        // Wave 1: extruder/heater_bed/fan/temperature_sensor/stepper_* (real negatives present in
        // the E5P capture) NEVER produce an output descriptor.
        fail("not implemented — Wave 1")
    }
}
