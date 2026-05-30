package works.mees.dinghy.state

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.GoldenFixtures

/**
 * STATE-02 / A3 / A4: prove [deriveCapabilities] and [deriveSubscribeSet] are pure and gate correctly
 * on a FULL printer (golden objects.list, falling back to fallback_*) vs a hand-authored MINIMAL one
 * (no heater_bed, single extruder). powerDevices is always empty (A4); the subscribe set is the v1
 * superset intersected with detected objects (A3) — it never names an object the printer lacks.
 */
class DeriveCapabilitiesTest {

    /** Load the full objects.list (live golden if present, else synthetic fallback) as a string list. */
    private fun goldenObjects(): List<String> =
        GoldenFixtures.resolve("objects_list.json")
            .jsonObject["result"]!!.jsonObject["objects"]!!.jsonArray
            .map { it.jsonPrimitive.content }

    /** Minimal printer: single extruder, NO heater_bed, NO chamber/second extruder. */
    private val minimal = listOf(
        "webhooks", "configfile", "mcu", "gcode_move", "toolhead",
        "extruder", "fan", "print_stats", "virtual_sdcard", "display_status",
        "gcode_macro START_PRINT",
    )

    @Test
    fun fullPrinterDerivesFullCapabilities() {
        val caps = deriveCapabilities(goldenObjects())

        assertTrue("full printer has a bed", caps.hasBed)
        assertTrue("at least one extruder", caps.extruderCount >= 1)
        assertTrue("macros detected", caps.macros.isNotEmpty())
        assertTrue("heaters detected", caps.heaters.isNotEmpty())
        assertTrue("heater_bed among heaters", "heater_bed" in caps.heaters)
        // Macros have the `gcode_macro ` prefix stripped.
        assertTrue("macro names are stripped of prefix", caps.macros.none { it.startsWith("gcode_macro ") })
        // A4: powerDevices is always empty in Phase 2.
        assertTrue("powerDevices empty (A4)", caps.powerDevices.isEmpty())
    }

    @Test
    fun minimalPrinterGatesDownCapabilities() {
        val caps = deriveCapabilities(minimal)

        assertFalse("no bed on minimal printer", caps.hasBed)
        assertEquals("single extruder", 1, caps.extruderCount)
        assertFalse("heater_bed not in heaters", "heater_bed" in caps.heaters)
        assertTrue("powerDevices empty (A4)", caps.powerDevices.isEmpty())
    }

    @Test
    fun subscribeSetIntersectsSupersetWithDetectedObjects() {
        val full = deriveSubscribeSet(goldenObjects())
        // Core objects present on a full printer ARE subscribed.
        assertTrue("webhooks", "webhooks" in full)
        assertTrue("print_stats", "print_stats" in full)
        assertTrue("toolhead", "toolhead" in full)
        assertTrue("heater_bed (present on full)", "heater_bed" in full)
        assertTrue("extruder", "extruder" in full)
    }

    @Test
    fun subscribeSetOmitsMissingObjectsOnMinimalPrinter() {
        val set = deriveSubscribeSet(minimal)
        // A3: never subscribe to an object the printer does not define.
        assertFalse("heater_bed omitted (not on minimal printer)", "heater_bed" in set)
        // But core objects that ARE present are still subscribed.
        assertTrue("extruder still subscribed", "extruder" in set)
        assertTrue("toolhead still subscribed", "toolhead" in set)
    }

    @Test
    fun derivationIsPureAndIdempotent() {
        val objs = goldenObjects()
        assertEquals(deriveCapabilities(objs), deriveCapabilities(objs))
        assertEquals(deriveSubscribeSet(objs), deriveSubscribeSet(objs))
    }
}
