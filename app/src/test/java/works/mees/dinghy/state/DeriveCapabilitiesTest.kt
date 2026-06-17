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
        assertTrue("pause_resume", "pause_resume" in full)
        assertTrue("toolhead", "toolhead" in full)
        assertTrue("heater_bed (present on full)", "heater_bed" in full)
        assertTrue("extruder", "extruder" in full)
    }

    @Test
    fun stepperEnableIsSubscribedWhenPresent() {
        val set = deriveSubscribeSet(listOf("webhooks", "toolhead", "extruder", "stepper_enable"))
        assertTrue("stepper_enable must be subscribed when the printer defines it", "stepper_enable" in set)
    }

    @Test
    fun stepperEnableAbsentIsNotSubscribed() {
        val set = deriveSubscribeSet(listOf("webhooks", "toolhead", "extruder"))
        assertFalse("never subscribe an object the printer lacks (A3)", "stepper_enable" in set)
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

    // --- Phase-5: case-insensitive macro presence (EXTR-02 / D-10 / Pitfall 2) ---

    @Test
    fun hasMacroIgnoreCaseMatchesLowercasedMoonrakerName() {
        // Moonraker reports macro object names LOWERCASE; a case-sensitive == would miss this.
        val caps = deriveCapabilities(listOf("gcode_macro load_filament"))
        assertTrue("LOAD_FILAMENT matches load_filament", caps.hasMacroIgnoreCase("LOAD_FILAMENT"))
    }

    @Test
    fun hasMacroIgnoreCaseFalseWhenAbsent() {
        assertFalse("no macros -> no match", deriveCapabilities(emptyList()).hasMacroIgnoreCase("LOAD_FILAMENT"))
    }

    // --- Phase-6: generic predicate surface for the command registry (D-09) ---

    @Test
    fun rawObjectNamesAreRetainedAndHasObjectWorks() {
        val raw = listOf("webhooks", "quad_gantry_level", "gcode_macro LOAD_FILAMENT")
        val caps = deriveCapabilities(raw)

        assertEquals("Capabilities must retain the exact raw objects.list names", raw.toSet(), caps.objects)
        assertTrue("hasObject gates object-present predicates", caps.hasObject("quad_gantry_level"))
        assertTrue("raw macro object name is retained with its prefix", caps.hasObject("gcode_macro LOAD_FILAMENT"))
        assertFalse("hasObject is exact and does not case-fold object names", caps.hasObject("QUAD_GANTRY_LEVEL"))
        assertTrue("existing macro convenience helper stays available", caps.hasMacroIgnoreCase("LOAD_FILAMENT"))
    }

    // --- Phase-9: calibration objects join the subscribe superset, intersected with detected (A3) ---

    private val calibrationObjects =
        listOf("screws_tilt_adjust", "z_tilt", "quad_gantry_level", "bed_mesh", "manual_probe", "probe")

    @Test
    fun subscribeSetIncludesCalibrationObjectsWhenDetected() {
        // A printer that exposes every calibration object subscribes to all of them.
        val objects = minimal + calibrationObjects
        val set = deriveSubscribeSet(objects)
        for (obj in calibrationObjects) {
            assertTrue("$obj subscribed when present", obj in set)
        }
    }

    @Test
    fun subscribeSetOmitsCalibrationObjectsWhenAbsent() {
        // The minimal printer has NONE of the calibration objects — A3: none are subscribed.
        val set = deriveSubscribeSet(minimal)
        for (obj in calibrationObjects) {
            assertFalse("$obj omitted when absent (A3)", obj in set)
        }
    }

    @Test
    fun subscribeSetIncludesOnlyTheDetectedCalibrationObjects() {
        // A probe-less single-Z printer: bed_mesh + screws_tilt_adjust present, z_tilt/probe/etc absent.
        val objects = minimal + listOf("bed_mesh", "screws_tilt_adjust")
        val set = deriveSubscribeSet(objects)
        assertTrue("bed_mesh subscribed", "bed_mesh" in set)
        assertTrue("screws_tilt_adjust subscribed", "screws_tilt_adjust" in set)
        assertFalse("z_tilt omitted (absent)", "z_tilt" in set)
        assertFalse("quad_gantry_level omitted (absent)", "quad_gantry_level" in set)
        assertFalse("manual_probe omitted (absent)", "manual_probe" in set)
        assertFalse("probe omitted (absent)", "probe" in set)
    }

    // --- Phase-16: temperature_sensor objects join the dynamic subscribe set (Standby glance) ---

    @Test
    fun subscribeSetIncludesTemperatureSensorObjects() {
        val objects = minimal + listOf("temperature_sensor mcu", "temperature_sensor chamber")
        val set = deriveSubscribeSet(objects)
        assertTrue("temperature_sensor mcu subscribed", "temperature_sensor mcu" in set)
        assertTrue("temperature_sensor chamber subscribed", "temperature_sensor chamber" in set)
    }

    // --- Extrude rework: filament-runout sensors join the dynamic subscribe set ---

    @Test
    fun subscribeIncludesFilamentSensors() {
        val subset = deriveSubscribeSet(listOf("toolhead", "filament_switch_sensor Runout", "extruder"))
        assertTrue("filament_switch_sensor Runout" in subset)
    }

    @Test
    fun liveComponentsAreRetainedAndHasComponentWorks() {
        val caps = deriveCapabilities(
            objects = listOf("webhooks", "toolhead"),
            components = setOf("webcam", "spoolman", "history", "job_queue", "update_manager"),
        )

        assertEquals(
            "Capabilities must retain server.info.components exactly",
            setOf("webcam", "spoolman", "history", "job_queue", "update_manager"),
            caps.components,
        )
        assertTrue("hasComponent gates component-present predicates", caps.hasComponent("spoolman"))
        assertTrue("history component is queryable", caps.hasComponent("history"))
        assertFalse("hasComponent is exact and does not fabricate missing components", caps.hasComponent("power"))
    }
}
