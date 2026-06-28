package works.mees.jiib.command

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.JsonRpcMethods

/**
 * D-11 guard: registry gcode entries must wrap PrinterCommands byte-identically instead of
 * re-templating script strings. Existing PrinterCommandsTest owns exact literal output; this suite
 * proves the planned registry delegates to those builders for every current Phase-5 action.
 */
class CommandRegistryGcodeTest {

    @Test
    fun gcodeRegistryWrapsPrinterCommandsByteIdentically() {
        assertRegistryScript(
            CommandRegistry.setHeater,
            SetHeaterArgs("extruder", 9999),
            PrinterCommands.setHeater("extruder", 9999),
        )
        assertRegistryScript(
            CommandRegistry.jog,
            JogArgs(axis = "X", mm = 9999.0, feedMmMin = 99_999),
            PrinterCommands.jog("X", 9999.0, 99_999),
        )
        assertRegistryScript(
            CommandRegistry.overrideJog,
            JogArgs(axis = "Y", mm = -10.0, feedMmMin = 3000),
            PrinterCommands.overrideJog("Y", -10.0, 3000),
        )
        assertRegistryScript(
            CommandRegistry.forceMove,
            ForceMoveArgs(axis = "Z", mm = 9999.0, velocityMmS = 500),
            PrinterCommands.forceMove("Z", 9999.0, 500),
        )
        assertRegistryScript(CommandRegistry.homeAll, Unit, PrinterCommands.homeAll())
        assertRegistryScript(CommandRegistry.homeXY, Unit, PrinterCommands.homeXY())
        assertRegistryScript(CommandRegistry.homeAxis, HomeAxisArgs("Z"), PrinterCommands.homeAxis("Z"))
        assertRegistryScript(
            CommandRegistry.extrude,
            ExtrudeArgs(mm = -500.0, feedMmMin = 99_999),
            PrinterCommands.extrude(-500.0, 99_999),
        )
        assertRegistryScript(CommandRegistry.selectTool, SelectToolArgs(2), PrinterCommands.selectTool(2))
        assertRegistryScript(CommandRegistry.loadFilament, Unit, PrinterCommands.loadFilament())
        assertRegistryScript(CommandRegistry.unloadFilament, Unit, PrinterCommands.unloadFilament())
        assertRegistryScript(CommandRegistry.cooldown, Unit, PrinterCommands.COOLDOWN)
        assertRegistryScript(CommandRegistry.disableSteppers, Unit, PrinterCommands.DISABLE_STEPPERS)
    }

    @Test
    fun registryDispatchKeysMatchExistingUiBusyKeys() {
        assertEquals("set_extruder", CommandRegistry.setHeater.dispatchKey(SetHeaterArgs("extruder", 215)))
        assertEquals("set_temp", CommandRegistry.setHeater.dispatchKey(SetHeaterArgs("extruder", 215, key = "set_temp")))
        assertEquals("cooldown", CommandRegistry.cooldown.dispatchKey(Unit))

        assertEquals("jog_X", CommandRegistry.jog.dispatchKey(JogArgs("X", 10.0, 3000)))
        assertEquals("jog_X", CommandRegistry.forceMove.dispatchKey(ForceMoveArgs("X", 10.0, 50)))
        assertEquals("home_xy", CommandRegistry.homeXY.dispatchKey(Unit))
        assertEquals("home_Z", CommandRegistry.homeAxis.dispatchKey(HomeAxisArgs("Z")))
        assertEquals("home_all", CommandRegistry.homeAll.dispatchKey(Unit))
        assertEquals("disable_steppers", CommandRegistry.disableSteppers.dispatchKey(Unit))

        assertEquals("extrude", CommandRegistry.extrude.dispatchKey(ExtrudeArgs(5.0, 300)))
        assertEquals("retract", CommandRegistry.extrude.dispatchKey(ExtrudeArgs(-5.0, 300)))
        assertEquals("tool_1", CommandRegistry.selectTool.dispatchKey(SelectToolArgs(1)))
        assertEquals("load", CommandRegistry.loadFilament.dispatchKey(Unit))
        assertEquals("unload", CommandRegistry.unloadFilament.dispatchKey(Unit))
        assertEquals("estop", CommandRegistry.emergencyStop.dispatchKey(Unit))
    }

    // --- Phase-17 Fine-Tune specs (GREEN — implemented in 17-02) ----------------------------------
    //
    // Every new command is registered in CommandRegistry.all, gated on its owning printer object, with a
    // distinct dispatchKey per motion-limit field (Pitfall 4). Wire output is owned by PrinterCommandsTest.

    @Test
    fun speedFactorAndFlowFactor_inAll_withGcodeMoveAvailability() {
        assertTrue(CommandRegistry.speedFactor in CommandRegistry.all)
        assertTrue(CommandRegistry.flowFactor in CommandRegistry.all)
        assertEquals(AvailabilityPredicate.ObjectPresent("gcode_move"), CommandRegistry.speedFactor.availability)
        assertEquals(AvailabilityPredicate.ObjectPresent("gcode_move"), CommandRegistry.flowFactor.availability)
        // Byte-identical delegation to the pure builders.
        assertRegistryScript(CommandRegistry.speedFactor, SpeedFactorArgs(105), PrinterCommands.speedFactor(105))
        assertRegistryScript(CommandRegistry.flowFactor, FlowFactorArgs(101), PrinterCommands.flowFactor(101))
    }

    @Test
    fun setVelocityLimit_perField_inAll_withToolheadAvailability() {
        assertTrue(CommandRegistry.setVelocityLimit in CommandRegistry.all)
        assertEquals(AvailabilityPredicate.ObjectPresent("toolhead"), CommandRegistry.setVelocityLimit.availability)
        // One spec serves all four fields; each delegates to the matching builder arg.
        assertRegistryScript(
            CommandRegistry.setVelocityLimit,
            VelocityLimitArgs(VelocityLimitArgs.VELOCITY, 250.0),
            PrinterCommands.setVelocityLimit(velocity = 250.0),
        )
        assertRegistryScript(
            CommandRegistry.setVelocityLimit,
            VelocityLimitArgs(VelocityLimitArgs.ACCEL, 3000.0),
            PrinterCommands.setVelocityLimit(accel = 3000.0),
        )
        assertRegistryScript(
            CommandRegistry.setVelocityLimit,
            VelocityLimitArgs(VelocityLimitArgs.MIN_CRUISE_RATIO, 0.55),
            PrinterCommands.setVelocityLimit(minCruiseRatio = 0.55),
        )
        assertRegistryScript(
            CommandRegistry.setVelocityLimit,
            VelocityLimitArgs(VelocityLimitArgs.SCV, 8.0),
            PrinterCommands.setVelocityLimit(scv = 8.0),
        )
    }

    @Test
    fun setPressureAdvance_inAll_withExtruderAvailability() {
        assertTrue(CommandRegistry.setPressureAdvance in CommandRegistry.all)
        assertEquals(AvailabilityPredicate.ObjectPresent("extruder"), CommandRegistry.setPressureAdvance.availability)
        assertRegistryScript(
            CommandRegistry.setPressureAdvance,
            PressureAdvanceArgs(PressureAdvanceArgs.ADVANCE, 0.045),
            PrinterCommands.setPressureAdvance(advance = 0.045),
        )
        assertRegistryScript(
            CommandRegistry.setPressureAdvance,
            PressureAdvanceArgs(PressureAdvanceArgs.SMOOTH_TIME, 0.04),
            PrinterCommands.setPressureAdvance(smoothTime = 0.04),
        )
    }

    @Test
    fun setFan_inAll_withFanAvailability() {
        assertTrue(CommandRegistry.setFan in CommandRegistry.all)
        assertEquals(AvailabilityPredicate.ObjectPresent("fan"), CommandRegistry.setFan.availability)
        assertRegistryScript(CommandRegistry.setFan, FanArgs(60), PrinterCommands.setFan(60))
    }

    @Test
    fun setRetraction_inAll_withFirmwareRetractionAvailability() {
        assertTrue(CommandRegistry.setRetraction in CommandRegistry.all)
        assertEquals(
            AvailabilityPredicate.ObjectPresent("firmware_retraction"),
            CommandRegistry.setRetraction.availability,
        )
        assertRegistryScript(
            CommandRegistry.setRetraction,
            RetractionArgs(retractLength = 0.5, unretractExtraLength = 0.0, retractSpeed = 35, unretractSpeed = 35),
            PrinterCommands.setRetraction(0.5, 0.0, 35, 35),
        )
    }

    @Test
    fun motionLimitSpecs_useDistinctDispatchKeysPerField() {
        // Pitfall 4: the four motion-limit fields must NOT share a single dispatchKey — each is DISTINCT
        // (set_vel_<field>) so an in-flight velocity tweak never busy-locks an accel tweak by key-collision.
        val keys = listOf(
            CommandRegistry.setVelocityLimit.dispatchKey(VelocityLimitArgs(VelocityLimitArgs.VELOCITY, 250.0)),
            CommandRegistry.setVelocityLimit.dispatchKey(VelocityLimitArgs(VelocityLimitArgs.ACCEL, 3000.0)),
            CommandRegistry.setVelocityLimit.dispatchKey(VelocityLimitArgs(VelocityLimitArgs.MIN_CRUISE_RATIO, 0.55)),
            CommandRegistry.setVelocityLimit.dispatchKey(VelocityLimitArgs(VelocityLimitArgs.SCV, 8.0)),
        )
        assertEquals("set_vel_velocity", keys[0])
        assertEquals("set_vel_accel", keys[1])
        assertEquals("set_vel_minCruiseRatio", keys[2])
        assertEquals("set_vel_scv", keys[3])
        assertEquals("all four motion-limit dispatchKeys must be distinct", 4, keys.toSet().size)
        // Pressure-advance fields are likewise distinctly keyed.
        assertEquals(
            "set_pa_advance",
            CommandRegistry.setPressureAdvance.dispatchKey(PressureAdvanceArgs(PressureAdvanceArgs.ADVANCE, 0.045)),
        )
        assertEquals(
            "set_pa_smoothTime",
            CommandRegistry.setPressureAdvance.dispatchKey(PressureAdvanceArgs(PressureAdvanceArgs.SMOOTH_TIME, 0.04)),
        )
    }

    // --- Phase-19 generic-output specs (GREEN — implemented in 19-03) -----------------------------
    //
    // HIGH-2: every output family is dispatched through a registered CommandSpec (NOT a raw GCODE_SCRIPT
    // string), each delegating BYTE-IDENTICALLY to its PrinterCommands builder (D-11 drift contract).

    @Test
    fun outputSpecs_inAll() {
        assertTrue(CommandRegistry.setGenericFan in CommandRegistry.all)
        assertTrue(CommandRegistry.setLed in CommandRegistry.all)
        assertTrue(CommandRegistry.setServo in CommandRegistry.all)
        assertTrue(CommandRegistry.setOutputPin in CommandRegistry.all)
    }

    @Test
    fun setGenericFan_wrapsBuilderByteIdentically_andBareName() {
        assertRegistryScript(
            CommandRegistry.setGenericFan,
            SetGenericFanArgs("FILTER_fan", 50),
            PrinterCommands.setGenericFan("FILTER_fan", 50),
        )
        // HIGH-1 wire-guard: the produced script must NOT carry the family prefix.
        val script = CommandRegistry.setGenericFan.params(SetGenericFanArgs("FILTER_fan", 50))!!
            .jsonObject["script"]!!.jsonPrimitive.content
        assertTrue("registry fan script leaked the family prefix", !script.contains("fan_generic"))
    }

    @Test
    fun setLed_wrapsBuilderByteIdentically_colorAndOff() {
        assertRegistryScript(
            CommandRegistry.setLed,
            SetLedArgs("chamber_light", 1f, 0.5f, 0f),
            PrinterCommands.setLed("chamber_light", 1f, 0.5f, 0f),
        )
        // D-12 Off (all-zero incl. WHITE).
        assertRegistryScript(
            CommandRegistry.setLed,
            SetLedArgs("chamber_light", 0f, 0f, 0f, 0f),
            PrinterCommands.setLed("chamber_light", 0f, 0f, 0f, 0f),
        )
    }

    @Test
    fun setServo_wrapsBuilderByteIdentically_angleAndDisable() {
        assertRegistryScript(
            CommandRegistry.setServo,
            SetServoArgs("camera_servo", deg = 90, maxDeg = 180),
            PrinterCommands.setServoAngle("camera_servo", 90, 180),
        )
        assertRegistryScript(
            CommandRegistry.setServo,
            SetServoArgs("camera_servo", disable = true),
            PrinterCommands.setServoDisable("camera_servo"),
        )
    }

    @Test
    fun setOutputPin_wrapsBuilderByteIdentically_pwmAndDigital() {
        assertRegistryScript(
            CommandRegistry.setOutputPin,
            SetOutputPinArgs("mosfet2", pwm = true, pct = 50),
            PrinterCommands.setPinPwm("mosfet2", 50),
        )
        assertRegistryScript(
            CommandRegistry.setOutputPin,
            SetOutputPinArgs("mosfet2", pwm = false, on = true),
            PrinterCommands.setPinDigital("mosfet2", true),
        )
    }

    @Test
    fun outputSpecs_useDistinctPerOutputDispatchKeys() {
        assertEquals("set_output_fan_FILTER_fan", CommandRegistry.setGenericFan.dispatchKey(SetGenericFanArgs("FILTER_fan", 50)))
        assertEquals("set_output_led_chamber_light", CommandRegistry.setLed.dispatchKey(SetLedArgs("chamber_light", 0f, 0f, 0f)))
        assertEquals("set_output_servo_camera_servo", CommandRegistry.setServo.dispatchKey(SetServoArgs("camera_servo")))
        assertEquals("set_output_pin_mosfet2", CommandRegistry.setOutputPin.dispatchKey(SetOutputPinArgs("mosfet2", pwm = false)))
    }

    @Test
    fun applyHeatPreset_wrapsPrinterCommandsByteIdentically() {
        val setpoints = mapOf("extruder" to 200, "heater_bed" to 60, "temperature_fan exhaust" to 40)
        assertRegistryScript(
            CommandRegistry.applyHeatPreset,
            ApplyHeatPresetArgs(setpoints, key = "preset_x"),
            PrinterCommands.applyHeatPreset(setpoints),
        )
    }

    @Test
    fun setTemperatureFan_wrapsPrinterCommandsByteIdentically() {
        assertRegistryScript(
            CommandRegistry.setTemperatureFan,
            SetTemperatureFanArgs("exhaust", 45),
            PrinterCommands.setTemperatureFanTarget("exhaust", 45),
        )
    }

    @Test
    fun `babystepClear zeroes the live gcode Z offset`() {
        assertRegistryScript(CommandRegistry.babystepClear, Unit, PrinterCommands.SET_GCODE_OFFSET_CLEAR)
        assertEquals("babystep_clear", CommandRegistry.babystepClear.dispatchKey(Unit))
        assertEquals(
            AvailabilityPredicate.ObjectPresent("gcode_move"),
            CommandRegistry.babystepClear.availability,
        )
    }

    private fun <P> assertRegistryScript(
        spec: CommandSpec<P>,
        args: P,
        expectedScript: String,
    ) {
        assertEquals("registry gcode commands must dispatch through gcode.script", JsonRpcMethods.GCODE_SCRIPT, spec.method)
        val params = spec.params(args)!!.jsonObject
        // Fenced commands get a trailing \nM400 appended by the gcode() builder (D-11: body is still
        // byte-identical to the PrinterCommands builder; M400 is an orthogonal fence suffix).
        val expected = if (spec.fence) "$expectedScript\nM400" else expectedScript
        assertEquals(expected, params["script"]!!.jsonPrimitive.content)
    }
}
