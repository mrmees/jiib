package works.mees.dinghy.outputs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-1 (19-04): the Wave-0 RED scaffold (19-02) turned GREEN against the REAL Moonraker captures.
 *
 * Anchored to live server shape (mock-vs-reality LAW): settings keys are LOWERCASED
 * (`fan_generic filter_fan`), `objects.list` is case-PRESERVED (`fan_generic FILTER_fan`,
 * `neopixel expanderPixel`), servo `.value` is PWM (not angle). The gate whitelists fan_generic / led /
 * neopixel / dotstar / pca* / servo / output_pin / pwm_tool and EXCLUDES extruder/heater_bed/fan/
 * temperature_sensor/stepper_*.
 *
 * E5P fixture exposes exactly NINE whitelisted outputs (1 fan_generic, 1 led, 1 neopixel, 2 servo,
 * 4 output_pin); E3P is the sparse single-output_pin case.
 */
class OutputsGateTest {

    /** Load a 19-02 capture fixture from the test classpath (`app/src/test/resources/outputs/`). */
    private fun fixture(name: String): String =
        this::class.java.classLoader
            ?.getResourceAsStream("outputs/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("fixture not on classpath: outputs/$name")

    private fun settings(name: String): JsonObject =
        Json.parseToJsonElement(fixture(name)) as JsonObject

    /** `objects.list` is a JSON array of strings; parse it to a Set<String> (the case-preserved source). */
    private fun objectsList(name: String): Set<String> =
        Json.parseToJsonElement(fixture(name)).jsonArray
            .map { (it as JsonPrimitive).content }
            .toSet()

    private fun e5p(): List<OutputDescriptor> =
        OutputsGate.parseOutputs(
            settings("configfile_settings_e5p.json"),
            objectsList("objects_list_e5p.json"),
        )

    /** Proves the real captures resolve from test resources (the link the whole gate test rides on). */
    @Test
    fun realCapturesResolveOnClasspath() {
        val cfg = fixture("configfile_settings_e5p.json")
        val list = fixture("objects_list_e5p.json")
        assertNotNull(cfg)
        assertNotNull(list)
        assertTrue("settings lowercased", cfg.contains("fan_generic filter_fan"))
        assertTrue("objects.list case-preserved", list.contains("fan_generic FILTER_fan"))
    }

    @Test
    fun whitelistFiltersStdOutputs() {
        val outputs = e5p()
        // Exactly the 9 whitelisted outputs (1 fan_generic, 1 led, 1 neopixel, 2 servo, 4 output_pin).
        assertEquals("E5P exposes exactly 9 whitelisted outputs", 9, outputs.size)
        val byFamily = outputs.groupBy { it.family }
        assertEquals(1, byFamily["fan_generic"]?.size)
        assertEquals(1, byFamily["led"]?.size)
        assertEquals(1, byFamily["neopixel"]?.size)
        assertEquals(2, byFamily["servo"]?.size)
        assertEquals(4, byFamily["output_pin"]?.size)
    }

    @Test
    fun recoversCaseFromObjectsList() {
        // The lowercased settings key `fan_generic filter_fan` recovers the case-PRESERVED objects.list
        // name as the objectKey (state-map key). Same for `neopixel expanderPixel`.
        val keys = e5p().map { it.objectKey }.toSet()
        assertTrue("objectKey is case-preserved FILTER_fan", "fan_generic FILTER_fan" in keys)
        assertTrue("objectKey is case-preserved expanderPixel", "neopixel expanderPixel" in keys)
        assertFalse("the lowercased settings key is NOT used as objectKey", "fan_generic filter_fan" in keys)
    }

    @Test
    fun commandNameIsBareSectionName() {
        // HIGH-1: `fan_generic FILTER_fan` → commandName `FILTER_fan` (bare, case-preserved, NO family prefix).
        val fan = e5p().single { it.objectKey == "fan_generic FILTER_fan" }
        assertEquals("FILTER_fan", fan.commandName)
        assertFalse("commandName carries no family prefix", fan.commandName.contains(' '))
        // And every descriptor's commandName is the objectKey with its family prefix stripped.
        e5p().forEach { d ->
            assertEquals(d.objectKey.substringAfter(' '), d.commandName)
        }
    }

    @Test
    fun emptyHidesTile() {
        // A printer with ZERO whitelisted outputs → empty list → the Outputs tile is hidden (D-10). Build a
        // settings object whose only sections are excluded families (no whitelist member present).
        val onlyExcluded = Json.parseToJsonElement(
            """{"extruder":{"min_extrude_temp":170},"heater_bed":{},"fan":{},"temperature_sensor x":{}}""",
        ) as JsonObject
        val result = OutputsGate.parseOutputs(onlyExcluded, setOf("extruder", "heater_bed", "fan"))
        assertTrue("no whitelisted outputs → empty", result.isEmpty())
        assertFalse("hasAnyOutput is false → tile hidden", OutputsGate.hasAnyOutput(result))
    }

    @Test
    fun pwmDetection() {
        // output_pin branches digital vs PWM on the settings `pwm` boolean. In the E5P capture:
        //   bed_safety_switch/virtual_pause → pwm=false (digital On/Off); mosfet2/mosfet3 → pwm=true (0..100%).
        val pins = e5p().filter { it.family == "output_pin" }.associateBy { it.commandName }
        assertEquals(false, pins["bed_safety_switch"]?.pwm)
        assertEquals(false, pins["virtual_pause"]?.pwm)
        assertEquals(true, pins["mosfet2"]?.pwm)
        assertEquals(true, pins["mosfet3"]?.pwm)
    }

    @Test
    fun servoAngleMaxFromConfig() {
        // Both servos carry maximum_servo_angle 180 in the capture; an absent key defaults to 180.
        val servos = e5p().filter { it.family == "servo" }
        assertEquals(2, servos.size)
        servos.forEach { assertEquals(180f, it.servoAngleMax, 0.001f) }

        // Absent maximum_servo_angle → default 180.
        val noAngle = OutputsGate.parseOutputs(
            Json.parseToJsonElement("""{"servo bare":{}}""") as JsonObject,
            setOf("servo bare"),
        ).single()
        assertEquals(OutputDescriptor.DEFAULT_SERVO_ANGLE_MAX, noAngle.servoAngleMax, 0.001f)
    }

    @Test
    fun excludedSectionsNeverAppear() {
        // Real negatives present in the E5P capture must NEVER produce a descriptor.
        val keys = e5p().map { it.objectKey }
        assertFalse(keys.any { it == "extruder" })
        assertFalse(keys.any { it == "heater_bed" })
        assertFalse(keys.any { it == "fan" })
        assertFalse(keys.any { it.startsWith("temperature_sensor") })
        assertFalse(keys.any { it.startsWith("heater_fan") })
        assertFalse(keys.any { it.startsWith("tmc2209") })
        assertFalse(keys.any { it.startsWith("filament_") })
    }

    @Test
    fun prettyNameTransform() {
        val byKey = e5p().associateBy { it.objectKey }
        assertEquals("Bed Safety Switch", byKey["output_pin bed_safety_switch"]?.prettyName)
        // No camelCase split (RESEARCH Open Q3).
        assertEquals("Expanderpixel", byKey["neopixel expanderPixel"]?.prettyName)
        assertEquals("Filter Fan", byKey["fan_generic FILTER_fan"]?.prettyName)
    }

    @Test
    fun sortedAlphabeticallyByPrettyName() {
        val names = e5p().map { it.prettyName }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun ledChannelCapabilityFromRealFixtures() {
        // GAP-B: per-LED channel capability is derived from configfile.settings.
        val byKey = e5p().associateBy { it.objectKey }

        // `led chamber_light` has ONLY white_pin (no red/green/blue) → white/brightness-only.
        val chamber = byKey["led chamber_light"] ?: error("led chamber_light missing")
        assertFalse("white-only LED has no RGB capability", chamber.ledHasRgb)
        assertTrue("white-only LED has the white channel", chamber.ledHasWhite)

        // `neopixel expanderPixel` color_order ["GRB"] → RGB-capable, no white.
        val expander = byKey["neopixel expanderPixel"] ?: error("neopixel expanderPixel missing")
        assertTrue("GRB neopixel is RGB-capable", expander.ledHasRgb)
        assertFalse("GRB neopixel has no white channel", expander.ledHasWhite)
    }

    @Test
    fun ledCapabilityDerivationCases() {
        // [led] with red/green/blue pins → RGB; adding white_pin → RGBW (both flags).
        val rgb = OutputsGate.parseOutputs(
            Json.parseToJsonElement("""{"led rgb_strip":{"red_pin":"a","green_pin":"b","blue_pin":"c"}}""") as JsonObject,
            setOf("led rgb_strip"),
        ).single()
        assertTrue(rgb.ledHasRgb)
        assertFalse(rgb.ledHasWhite)

        val rgbw = OutputsGate.parseOutputs(
            Json.parseToJsonElement("""{"led rgbw_strip":{"red_pin":"a","green_pin":"b","blue_pin":"c","white_pin":"d"}}""") as JsonObject,
            setOf("led rgbw_strip"),
        ).single()
        assertTrue(rgbw.ledHasRgb)
        assertTrue(rgbw.ledHasWhite)

        // color_order containing W (e.g. "RGBW") → RGB AND white.
        val neoRgbw = OutputsGate.parseOutputs(
            Json.parseToJsonElement("""{"neopixel rgbw_pixel":{"color_order":["GRBW"]}}""") as JsonObject,
            setOf("neopixel rgbw_pixel"),
        ).single()
        assertTrue(neoRgbw.ledHasRgb)
        assertTrue(neoRgbw.ledHasWhite)

        // pure "W" color_order → white-only.
        val whiteOnly = OutputsGate.parseOutputs(
            Json.parseToJsonElement("""{"neopixel w_pixel":{"color_order":["W"]}}""") as JsonObject,
            setOf("neopixel w_pixel"),
        ).single()
        assertFalse(whiteOnly.ledHasRgb)
        assertTrue(whiteOnly.ledHasWhite)

        // pca9533 (fixed 4-channel RGBW driver, no pins / no color_order) → both flags (Codex SS-1).
        val pca9533 = OutputsGate.parseOutputs(
            Json.parseToJsonElement("""{"pca9533 leds":{}}""") as JsonObject,
            setOf("pca9533 leds"),
        ).single()
        assertTrue(pca9533.ledHasRgb)
        assertTrue(pca9533.ledHasWhite)

        // pca9632 with no color_order → Klipper defaults RGBW (Codex SS-1).
        val pca9632 = OutputsGate.parseOutputs(
            Json.parseToJsonElement("""{"pca9632 leds":{}}""") as JsonObject,
            setOf("pca9632 leds"),
        ).single()
        assertTrue(pca9632.ledHasRgb)
        assertTrue(pca9632.ledHasWhite)

        // Non-LED family → both flags default false.
        val fan = e5p().single { it.family == "fan_generic" }
        assertFalse(fan.ledHasRgb)
        assertFalse(fan.ledHasWhite)
    }

    @Test
    fun sparseFixtureSingleOutputPin() {
        // E3P is the sparse case: a single virtual output_pin (`output_pin ignore_m600`).
        val e3p = OutputsGate.parseOutputs(
            settings("configfile_settings_e3p.json"),
            objectsList("objects_list_e3p.json"),
        )
        assertEquals(1, e3p.size)
        assertEquals("output_pin ignore_m600", e3p.single().objectKey)
        assertEquals("ignore_m600", e3p.single().commandName)
        assertFalse("digital virtual pin → not pwm", e3p.single().pwm)
    }
}
