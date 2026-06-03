package works.mees.dinghy.command

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson

/**
 * ASVS V5 / RESEARCH §5: prove every [PrinterCommands] builder produces the EXACT bounded gcode string
 * the dispatch layer ships, that numerics are clamped BEFORE formatting (T-05-02-T), that mode-changing
 * moves are wrapped in SAVE/RESTORE_GCODE_STATE (T-05-02-Safety), and that [PrinterCommands.scriptParams]
 * serializes to the `{"script": <gcode>}` payload. All host-side — no I/O, no device.
 */
class PrinterCommandsTest {

    // --- exact strings ----------------------------------------------------------------------------

    @Test
    fun setHeater_exactString() {
        assertEquals(
            "SET_HEATER_TEMPERATURE HEATER=extruder TARGET=200",
            PrinterCommands.setHeater("extruder", 200),
        )
        assertEquals(
            "SET_HEATER_TEMPERATURE HEATER=heater_bed TARGET=60",
            PrinterCommands.setHeater("heater_bed", 60),
        )
    }

    @Test
    fun applyPreset_twoClampedLines() {
        assertEquals(
            "SET_HEATER_TEMPERATURE HEATER=extruder TARGET=240\n" +
                "SET_HEATER_TEMPERATURE HEATER=heater_bed TARGET=80",
            PrinterCommands.applyPreset(240, 80),
        )
    }

    @Test
    fun jog_exactSaveRestoreBody() {
        assertEquals(
            "SAVE_GCODE_STATE NAME=dd_jog\nG91\nG1 X10.0 F3000\nRESTORE_GCODE_STATE NAME=dd_jog",
            PrinterCommands.jog("X", 10.0, 3000),
        )
    }

    @Test
    fun homeXY_isXYNotG28() {
        assertEquals("G28 X Y", PrinterCommands.homeXY())
        // Guard against the D-03 trap: homeXY must NOT home Z.
        assertEquals("G28", PrinterCommands.homeAll())
        assertEquals("G28 Z", PrinterCommands.homeAxis("Z"))
    }

    @Test
    fun overrideJog_prependsKinematicPosition() {
        val out = PrinterCommands.overrideJog("X", 10.0, 3000)
        assertTrue(out.startsWith("SET_KINEMATIC_POSITION X=0 Y=0 Z=0\n"))
        assertEquals(
            "SET_KINEMATIC_POSITION X=0 Y=0 Z=0\n" +
                "SAVE_GCODE_STATE NAME=dd_jog\nG91\nG1 X10.0 F3000\nRESTORE_GCODE_STATE NAME=dd_jog",
            out,
        )
    }

    @Test
    fun extrude_retractIsNegative_andWrapped() {
        val out = PrinterCommands.extrude(-5.0, 300)
        assertTrue(out.contains("G1 E-5.0 F300"))
        assertTrue(out.startsWith("SAVE_GCODE_STATE NAME=dd_ext\nM83\n"))
        assertTrue(out.endsWith("RESTORE_GCODE_STATE NAME=dd_ext"))
    }

    @Test
    fun constants_areExact() {
        assertEquals("TURN_OFF_HEATERS", PrinterCommands.COOLDOWN)
        assertEquals("M84", PrinterCommands.DISABLE_STEPPERS)
        assertEquals("LOAD_FILAMENT", PrinterCommands.loadFilament())
        assertEquals("UNLOAD_FILAMENT", PrinterCommands.unloadFilament())
        assertEquals("T0", PrinterCommands.selectTool(0))
    }

    @Test
    fun materialPresets_areFixedSet() {
        val names = PrinterCommands.MATERIAL_PRESETS.map { it.name }
        assertEquals(listOf("PLA", "PETG", "ABS", "TPU"), names)
        val pla = PrinterCommands.MATERIAL_PRESETS.first { it.name == "PLA" }
        assertEquals(200, pla.nozzle)
        assertEquals(60, pla.bed)
    }

    // --- clamping (ASVS V5) -----------------------------------------------------------------------

    @Test
    fun setHeater_clampsTarget() {
        assertEquals(
            "SET_HEATER_TEMPERATURE HEATER=extruder TARGET=350",
            PrinterCommands.setHeater("extruder", 9999),
        )
        assertEquals(
            "SET_HEATER_TEMPERATURE HEATER=extruder TARGET=0",
            PrinterCommands.setHeater("extruder", -50),
        )
    }

    @Test
    fun extrude_clampsMagnitude() {
        assertTrue(PrinterCommands.extrude(500.0, 300).contains("G1 E100.0 F300"))
        assertTrue(PrinterCommands.extrude(-500.0, 300).contains("G1 E-100.0 F300"))
    }

    @Test
    fun jog_clampsDistanceAndFeed() {
        assertTrue(PrinterCommands.jog("X", 9999.0, 99999).contains("G1 X200.0 F30000"))
        assertTrue(PrinterCommands.jog("X", 10.0, 0).contains("F1"))
    }

    @Test
    fun extrude_clampsFeed() {
        assertTrue(PrinterCommands.extrude(5.0, 99999).contains("F6000"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun jog_rejectsNonMotionAxis() {
        PrinterCommands.jog("E", 10.0, 3000)
    }

    // --- Phase-9 calibration builders -------------------------------------------------------------

    @Test
    fun calibrationConstants_areExact() {
        assertEquals("SCREWS_TILT_CALCULATE", PrinterCommands.SCREWS_TILT_CALCULATE)
        assertEquals("Z_TILT_ADJUST", PrinterCommands.Z_TILT_ADJUST)
        assertEquals("QUAD_GANTRY_LEVEL", PrinterCommands.QUAD_GANTRY_LEVEL)
        // BED_MESH_CALIBRATE is BARE — no METHOD/ADAPTIVE injected (RESEARCH Open-Q2).
        assertEquals("BED_MESH_CALIBRATE", PrinterCommands.BED_MESH_CALIBRATE)
        assertEquals("PROBE_CALIBRATE", PrinterCommands.PROBE_CALIBRATE)
        assertEquals("Z_ENDSTOP_CALIBRATE", PrinterCommands.Z_ENDSTOP_CALIBRATE)
        assertEquals("ACCEPT", PrinterCommands.ACCEPT)
        assertEquals("ABORT", PrinterCommands.ABORT)
        assertEquals("SAVE_CONFIG", PrinterCommands.SAVE_CONFIG)
    }

    @Test
    fun testZ_exactStringAndClampsToBound() {
        assertEquals("TESTZ Z=0.05", PrinterCommands.testZ(0.05))
        assertEquals("TESTZ Z=-0.1", PrinterCommands.testZ(-0.1))
        // Clamp-before-format (ASVS V5 / T-09-02-01): an absurd value never reaches the string.
        assertTrue(PrinterCommands.testZ(999.0).contains("Z=${PrinterCommands.MAX_TESTZ_MM}"))
        assertTrue(PrinterCommands.testZ(-999.0).contains("Z=-${PrinterCommands.MAX_TESTZ_MM}"))
    }

    @Test
    fun bedMeshProfileSave_exactString() {
        assertEquals(
            "BED_MESH_PROFILE SAVE=26.06.02_14.05",
            PrinterCommands.bedMeshProfileSave("26.06.02_14.05"),
        )
    }

    // --- scriptParams serialization ---------------------------------------------------------------

    @Test
    fun scriptParams_serializesToScriptObject() {
        val el = PrinterCommands.scriptParams("M84")
        assertEquals("{\"script\":\"M84\"}", MoonrakerJson.encodeToString(JsonObject.serializer(), el as JsonObject))
        assertEquals(JsonPrimitive("M84"), el["script"])
    }
}
