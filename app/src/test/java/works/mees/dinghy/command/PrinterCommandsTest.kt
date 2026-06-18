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

    // --- Phase-17 Fine-Tune builders (GREEN — landed in 17-02) ------------------------------------
    //
    // Each live-adjust gcode string is built clamp-before-format, Locale.US, with display-vs-wire scaling
    // correct (M220/M221 percent; SET_VELOCITY_LIMIT incl. MINIMUM_CRUISE_RATIO ratio-on-wire; M106 0..255;
    // SET_PRESSURE_ADVANCE / SET_RETRACTION exact params, no Z_HOP).

    @Test
    fun speedFactor_clampsAndFormats() {
        assertEquals("M220 S105", PrinterCommands.speedFactor(105))
        assertEquals("M220 S300", PrinterCommands.speedFactor(9999)) // clamp to SPEED_PCT_MAX
        assertEquals("M220 S25", PrinterCommands.speedFactor(0)) // clamp to SPEED_PCT_MIN
    }

    @Test
    fun flowFactor_clampsAndFormats() {
        assertEquals("M221 S100", PrinterCommands.flowFactor(100))
        assertEquals("M221 S101", PrinterCommands.flowFactor(101))
        assertEquals("M221 S150", PrinterCommands.flowFactor(9999)) // clamp to FLOW_PCT_MAX
        assertEquals("M221 S50", PrinterCommands.flowFactor(0)) // clamp to FLOW_PCT_MIN
    }

    @Test
    fun setVelocityLimit_singleField_velocity() {
        assertEquals(
            "SET_VELOCITY_LIMIT VELOCITY=250",
            PrinterCommands.setVelocityLimit(velocity = 250.0),
        )
        assertEquals(
            "SET_VELOCITY_LIMIT VELOCITY=200",
            PrinterCommands.setVelocityLimit(velocity = 200.0),
        )
    }

    @Test
    fun setVelocityLimit_singleField_accel() {
        assertEquals(
            "SET_VELOCITY_LIMIT ACCEL=3000",
            PrinterCommands.setVelocityLimit(accel = 3000.0),
        )
    }

    @Test
    fun setVelocityLimit_singleField_minCruiseRatio_clamped() {
        assertEquals(
            "SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO=1",
            PrinterCommands.setVelocityLimit(minCruiseRatio = 1.5), // clamp to 1.0
        )
        assertEquals(
            "SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO=0",
            PrinterCommands.setVelocityLimit(minCruiseRatio = -0.5), // clamp to 0.0
        )
    }

    @Test
    fun setVelocityLimit_singleField_squareCornerVelocity() {
        assertEquals(
            "SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY=5",
            PrinterCommands.setVelocityLimit(scv = 5.0),
        )
        assertEquals(
            "SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY=8",
            PrinterCommands.setVelocityLimit(scv = 8.0),
        )
    }

    @Test
    fun setVelocityLimit_minCruiseRatio_percentDisplayRatioWire() {
        // REVIEW #9 — DISPLAY-vs-WIRE: a live ratio 0.5 DISPLAYS as 50%; one +tap of the fixed 5-percentage-
        // point step adds 0.05 to the RATIO -> the wire string is MINIMUM_CRUISE_RATIO=0.55 (ratio on the
        // wire, percent on screen). The builder receives the ratio and formats it verbatim.
        assertEquals(
            "SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO=0.55",
            PrinterCommands.setVelocityLimit(minCruiseRatio = 0.55),
        )
    }

    @Test
    fun setPressureAdvance_advance_upTo3dp() {
        assertEquals(
            "SET_PRESSURE_ADVANCE ADVANCE=0.045",
            PrinterCommands.setPressureAdvance(advance = 0.045),
        )
        // No EXTRUDER= param (single-extruder v1, D-09).
        assertTrue(!PrinterCommands.setPressureAdvance(advance = 0.045).contains("EXTRUDER"))
    }

    @Test
    fun setPressureAdvance_smoothTime_2dp() {
        assertEquals(
            "SET_PRESSURE_ADVANCE SMOOTH_TIME=0.04",
            PrinterCommands.setPressureAdvance(smoothTime = 0.04),
        )
    }

    @Test
    fun setFan_pctToPwm() {
        // round(pct/100*255), computed from the displayed % (no accumulation). Clamp 0..100.
        assertEquals("M106 S153", PrinterCommands.setFan(60))
        assertEquals("M106 S255", PrinterCommands.setFan(100))
        assertEquals("M106 S0", PrinterCommands.setFan(0))
    }

    @Test
    fun setRetraction_fourFields() {
        assertEquals(
            "SET_RETRACTION RETRACT_LENGTH=0.5 RETRACT_SPEED=35 UNRETRACT_EXTRA_LENGTH=0 UNRETRACT_SPEED=35",
            PrinterCommands.setRetraction(
                retractLength = 0.5,
                unretractExtraLength = 0.0,
                retractSpeed = 35,
                unretractSpeed = 35,
            ),
        )
        // NO Z_HOP field anywhere (it does not exist — confirmed).
        assertTrue(
            !PrinterCommands.setRetraction(0.5, 0.0, 35, 35).contains("Z_HOP"),
        )
    }

    @Test
    fun localeUS_noCommaInDouble() {
        // Every Double-formatting builder uses Locale.US so a value like 0.05 NEVER emits a comma (0,05).
        val strings = listOf(
            PrinterCommands.setVelocityLimit(minCruiseRatio = 0.55),
            PrinterCommands.setVelocityLimit(scv = 8.0),
            PrinterCommands.setPressureAdvance(advance = 0.05),
            PrinterCommands.setPressureAdvance(smoothTime = 0.04),
            PrinterCommands.setRetraction(0.5, -0.5, 35, 35),
        )
        for (s in strings) {
            assertTrue("no comma in formatted Double: $s", !s.contains(','))
        }
    }

    // --- 17-07 clamp AUTHORITY: pure clamps + builders delegate (byte-identical output) -------------

    @Test
    fun clampAuthority_pureClamps() {
        // Speed range is 25..300, so 151 is IN range (the plan's "==MAX" example was a typo).
        assertEquals(PrinterCommands.SPEED_PCT_MAX, PrinterCommands.clampSpeedPct(9999))
        assertEquals(151, PrinterCommands.clampSpeedPct(151))
        assertEquals(PrinterCommands.SPEED_PCT_MIN, PrinterCommands.clampSpeedPct(10))
        assertEquals(120, PrinterCommands.clampSpeedPct(120))

        assertEquals(PrinterCommands.FLOW_PCT_MAX, PrinterCommands.clampFlowPct(151))
        assertEquals(PrinterCommands.FLOW_PCT_MIN, PrinterCommands.clampFlowPct(40))
        assertEquals(120, PrinterCommands.clampFlowPct(120))

        assertEquals(PrinterCommands.VEL_MAX, PrinterCommands.clampVelocity(2000.0), 0.0)
        assertEquals(PrinterCommands.ACCEL_MAX, PrinterCommands.clampAccel(60000.0), 0.0)
        assertEquals(PrinterCommands.SCV_MAX, PrinterCommands.clampScv(50.0), 0.0)
        assertEquals(PrinterCommands.PA_MAX, PrinterCommands.clampPressureAdvance(2.0), 0.0)
        assertEquals(PrinterCommands.SMOOTH_MAX, PrinterCommands.clampSmoothTime(0.5), 0.0)
    }

    @Test
    fun clampAuthority_buildersUnchangedAtCap() {
        // The builders now delegate to the SAME clamp funcs — output must stay byte-identical to before.
        assertEquals("M221 S150", PrinterCommands.flowFactor(151))
        assertEquals("M220 S300", PrinterCommands.speedFactor(310))
        assertTrue(PrinterCommands.setVelocityLimit(accel = 60000.0).contains("ACCEL=50000"))
        assertTrue(PrinterCommands.setVelocityLimit(velocity = 2000.0).contains("VELOCITY=1000"))
        assertTrue(PrinterCommands.setPressureAdvance(advance = 2.0).contains("ADVANCE=1"))
        assertTrue(PrinterCommands.setPressureAdvance(smoothTime = 0.5).contains("SMOOTH_TIME=0.2"))
    }

    // --- scriptParams serialization ---------------------------------------------------------------

    @Test
    fun scriptParams_serializesToScriptObject() {
        val el = PrinterCommands.scriptParams("M84")
        assertEquals("{\"script\":\"M84\"}", MoonrakerJson.encodeToString(JsonObject.serializer(), el as JsonObject))
        assertEquals(JsonPrimitive("M84"), el["script"])
    }

    // --- Heat Presets: setTemperatureFanTarget + applyHeatPreset ----------------------------------

    @Test
    fun setTemperatureFanTarget_clampsAndFormats() {
        assertEquals("SET_TEMPERATURE_FAN_TARGET FAN=exhaust TARGET=45", PrinterCommands.setTemperatureFanTarget("exhaust", 45))
        assertEquals("SET_TEMPERATURE_FAN_TARGET FAN=exhaust TARGET=350", PrinterCommands.setTemperatureFanTarget("exhaust", 9999))
        assertEquals("SET_TEMPERATURE_FAN_TARGET FAN=exhaust TARGET=0", PrinterCommands.setTemperatureFanTarget("exhaust", -5))
    }

    @Test
    fun applyHeatPreset_buildsClampedMultilineScript_byHeaterType() {
        val script = PrinterCommands.applyHeatPreset(
            mapOf(
                "extruder" to 200,
                "heater_bed" to 60,
                "heater_generic chamber" to 50,
                "temperature_fan exhaust" to 40,
            ),
        )
        // Sorted by object name for determinism: extruder, heater_bed, heater_generic chamber, temperature_fan exhaust
        assertEquals(
            listOf(
                "SET_HEATER_TEMPERATURE HEATER=extruder TARGET=200",
                "SET_HEATER_TEMPERATURE HEATER=heater_bed TARGET=60",
                "SET_HEATER_TEMPERATURE HEATER=chamber TARGET=50",
                "SET_TEMPERATURE_FAN_TARGET FAN=exhaust TARGET=40",
            ).joinToString("\n"),
            script,
        )
    }

    @Test
    fun applyHeatPreset_emptyMap_isEmptyString() {
        assertEquals("", PrinterCommands.applyHeatPreset(emptyMap()))
    }

    @Test
    fun applyHeatPreset_zeroIsARealTarget() {
        assertEquals("SET_HEATER_TEMPERATURE HEATER=extruder TARGET=0", PrinterCommands.applyHeatPreset(mapOf("extruder" to 0)))
    }
}
