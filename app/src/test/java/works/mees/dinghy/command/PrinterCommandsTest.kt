package works.mees.dinghy.command

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    // --- Phase-16 Z-babystep + SD reset builders (GREEN — landed in 16-03) ------------------------

    /**
     * `setGcodeOffsetZAdjust(±step)` emits `SET_GCODE_OFFSET Z_ADJUST=<value> MOVE=1` with the SIGNED
     * delta: Compress (nozzle closer) = NEGATIVE, Expand (nozzle further) = POSITIVE. The step is one of
     * the fixed set {0.02, 0.05, 0.10, 0.15, 0.20} — canonicalize against the set, NEVER free-text (V5).
     */
    @Test
    fun setGcodeOffsetZAdjust_signedDelta_exactString() {
        assertEquals(
            "SET_GCODE_OFFSET Z_ADJUST=0.05 MOVE=1",
            PrinterCommands.setGcodeOffsetZAdjust(0.05),
        )
        assertEquals(
            "SET_GCODE_OFFSET Z_ADJUST=-0.05 MOVE=1",
            PrinterCommands.setGcodeOffsetZAdjust(-0.05),
        )
        // Locale.US trailing-zero strip: 0.10 → "0.1", and the negative compress direction.
        assertEquals(
            "SET_GCODE_OFFSET Z_ADJUST=0.1 MOVE=1",
            PrinterCommands.setGcodeOffsetZAdjust(0.10),
        )
        assertEquals(
            "SET_GCODE_OFFSET Z_ADJUST=-0.1 MOVE=1",
            PrinterCommands.setGcodeOffsetZAdjust(-0.10),
        )
    }

    @Test
    fun babystepSteps_areTheFixedCycle() {
        assertEquals(listOf(0.02, 0.05, 0.10, 0.15, 0.20), PrinterCommands.BABYSTEP_STEPS)
    }

    @Test
    fun setGcodeOffsetZAdjust_offGridStep_snapsToNearestMember() {
        // 0.037 is nearest 0.05 (|.037-.05|=.013 < |.037-.02|=.017) — never emits free-text 0.037.
        assertEquals(
            "SET_GCODE_OFFSET Z_ADJUST=0.05 MOVE=1",
            PrinterCommands.setGcodeOffsetZAdjust(0.037),
        )
        // Sign preserved while snapping.
        assertEquals(
            "SET_GCODE_OFFSET Z_ADJUST=-0.05 MOVE=1",
            PrinterCommands.setGcodeOffsetZAdjust(-0.037),
        )
    }

    @Test
    fun setGcodeOffsetZAdjust_invalidInput_canonicalizesToValidMember() {
        // 0.0 / NaN / out-of-range never emit a raw/garbage Z_ADJUST — always a BABYSTEP_STEPS member.
        for (out in listOf(
            PrinterCommands.setGcodeOffsetZAdjust(0.0),
            PrinterCommands.setGcodeOffsetZAdjust(Double.NaN),
            PrinterCommands.setGcodeOffsetZAdjust(999.0),
        )) {
            assertTrue("must start SET_GCODE_OFFSET Z_ADJUST=", out.startsWith("SET_GCODE_OFFSET Z_ADJUST="))
            assertTrue("must end MOVE=1", out.endsWith("MOVE=1"))
            val zAdjust = out.removePrefix("SET_GCODE_OFFSET Z_ADJUST=").removeSuffix(" MOVE=1")
            val magnitude = zAdjust.removePrefix("-").toDouble()
            assertTrue(
                "$zAdjust magnitude must be a fixed BABYSTEP_STEPS member, was $magnitude",
                PrinterCommands.BABYSTEP_STEPS.any { kotlin.math.abs(it - magnitude) < 1e-9 },
            )
        }
    }

    @Test
    fun sdcardResetFile_constIsExactLiteral() {
        assertEquals("SDCARD_RESET_FILE", PrinterCommands.SDCARD_RESET_FILE)
    }

    // --- Phase-17 Fine-Tune builders (RED — implemented in 17-02) ---------------------------------
    //
    // [[dinghy-wave0-red-scaffold-compile]]: the new PrinterCommands builders (speedFactor / flowFactor /
    // setVelocityLimit / setPressureAdvance / setFan / setRetraction) do NOT exist yet — they land in 17-02.
    // These stubs MUST compile today, so they reference ONLY symbols that exist now and carry the EXACT
    // target gcode string in the fail() message. 17-02 converts each fail() to a real assertEquals.

    @Test
    fun speedFactor_clampsAndFormats() {
        // Target (17-02): PrinterCommands.speedFactor(105) == "M220 S105"; clamp pct to 25..300.
        //   speedFactor(105) -> "M220 S105"; speedFactor(9999) -> "M220 S300"; speedFactor(0) -> "M220 S25"
        fail("RED — 17-02: speedFactor(105)==\"M220 S105\"; clamp 25..300 (9999->S300, 0->S25)")
    }

    @Test
    fun flowFactor_clampsAndFormats() {
        // Target (17-02): PrinterCommands.flowFactor(100) == "M221 S100"; clamp pct to 50..150.
        //   flowFactor(100) -> "M221 S100"; flowFactor(9999) -> "M221 S150"; flowFactor(0) -> "M221 S50"
        fail("RED — 17-02: flowFactor(100)==\"M221 S100\"; clamp 50..150 (9999->S150, 0->S50)")
    }

    @Test
    fun setVelocityLimit_singleField_velocity() {
        // Target (17-02): one field per call. setVelocityLimit(velocity=200.0) -> "SET_VELOCITY_LIMIT VELOCITY=200"
        fail("RED — 17-02: setVelocityLimit(velocity=200.0)==\"SET_VELOCITY_LIMIT VELOCITY=200\" (one field per call)")
    }

    @Test
    fun setVelocityLimit_singleField_accel() {
        // Target (17-02): setVelocityLimit(accel=3000.0) -> "SET_VELOCITY_LIMIT ACCEL=3000"
        fail("RED — 17-02: setVelocityLimit(accel=3000.0)==\"SET_VELOCITY_LIMIT ACCEL=3000\"")
    }

    @Test
    fun setVelocityLimit_singleField_minCruiseRatio_clamped() {
        // Target (17-02): setVelocityLimit(minCruiseRatio=...) -> "SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO=<v>",
        //   ratio clamped 0.0..1.0 (e.g. 1.5 -> MINIMUM_CRUISE_RATIO=1, -0.5 -> MINIMUM_CRUISE_RATIO=0).
        fail("RED — 17-02: setVelocityLimit(minCruiseRatio=...) clamps ratio 0.0..1.0; \"SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO=<v>\"")
    }

    @Test
    fun setVelocityLimit_singleField_squareCornerVelocity() {
        // Target (17-02): setVelocityLimit(scv=5.0) -> "SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY=5"
        fail("RED — 17-02: setVelocityLimit(scv=5.0)==\"SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY=5\"")
    }

    @Test
    fun setVelocityLimit_minCruiseRatio_percentDisplayRatioWire() {
        // REVIEW #9 — DISPLAY-vs-WIRE: a live ratio 0.5 DISPLAYS as 50%; one +tap of the fixed 5-percentage-
        // point step adds 0.05 to the RATIO -> the wire string is MINIMUM_CRUISE_RATIO=0.55 (ratio on the
        // wire, percent on screen). The wire value for a +tap from 0.5 is EXACTLY 0.55.
        fail("RED — 17-02: +tap from 0.5 -> \"SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO=0.55\" (ratio wire / percent screen)")
    }

    @Test
    fun setPressureAdvance_advance_upTo3dp() {
        // Target (17-02): setPressureAdvance(advance=0.045) -> "SET_PRESSURE_ADVANCE ADVANCE=0.045" (up to 3dp)
        fail("RED — 17-02: setPressureAdvance(advance=0.045)==\"SET_PRESSURE_ADVANCE ADVANCE=0.045\" (3dp)")
    }

    @Test
    fun setPressureAdvance_smoothTime_2dp() {
        // Target (17-02): setPressureAdvance(smoothTime=0.04) -> "SET_PRESSURE_ADVANCE SMOOTH_TIME=0.04" (2dp)
        fail("RED — 17-02: setPressureAdvance(smoothTime=0.04)==\"SET_PRESSURE_ADVANCE SMOOTH_TIME=0.04\" (2dp)")
    }

    @Test
    fun setFan_pctToPwm() {
        // Target (17-02): part-fan percent -> 0..255 PWM, computed from the DISPLAYED % each tap (no
        // rounding accumulation): setFan(60) -> "M106 S153"; setFan(100) -> "M106 S255"; setFan(0) -> "M106 S0".
        fail("RED — 17-02: setFan(60)==\"M106 S153\", setFan(100)==\"M106 S255\", setFan(0)==\"M106 S0\" (pct->0..255 each tap)")
    }

    @Test
    fun setRetraction_fourFields() {
        // Target (17-02): setRetraction(length, speed, extraLength, unretractSpeed) ->
        //   "SET_RETRACTION RETRACT_LENGTH=<l> RETRACT_SPEED=<s> UNRETRACT_EXTRA_LENGTH=<e> UNRETRACT_SPEED=<u>"
        //   with NO Z_HOP field anywhere in the string.
        fail("RED — 17-02: setRetraction(...) == \"SET_RETRACTION RETRACT_LENGTH= RETRACT_SPEED= UNRETRACT_EXTRA_LENGTH= UNRETRACT_SPEED=\" — NO Z_HOP")
    }

    @Test
    fun localeUS_noCommaInDouble() {
        // Target (17-02): every Double-formatting builder uses Locale.US so a value like 0.05 NEVER emits a
        // comma (0,05) into the gcode — assert no ',' reaches any of the new SET_* strings.
        fail("RED — 17-02: no comma in any formatted Double (Locale.US) — e.g. ADVANCE=0.05 never \"0,05\"")
    }

    // --- scriptParams serialization ---------------------------------------------------------------

    @Test
    fun scriptParams_serializesToScriptObject() {
        val el = PrinterCommands.scriptParams("M84")
        assertEquals("{\"script\":\"M84\"}", MoonrakerJson.encodeToString(JsonObject.serializer(), el as JsonObject))
        assertEquals(JsonPrimitive("M84"), el["script"])
    }
}
