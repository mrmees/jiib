package works.mees.dinghy.command

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import works.mees.dinghy.net.JsonRpcMethods

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
            CommandRegistry.applyPreset,
            ApplyPresetArgs(nozzle = 240, bed = 80),
            PrinterCommands.applyPreset(240, 80),
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
        assertEquals(
            "preset_PLA",
            CommandRegistry.applyPreset.dispatchKey(ApplyPresetArgs(nozzle = 200, bed = 60, key = "preset_PLA")),
        )
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

    // --- Phase-17 Fine-Tune specs (RED — implemented in 17-02) ------------------------------------
    //
    // [[dinghy-wave0-red-scaffold-compile]]: the new CommandRegistry specs (speedFactor / flowFactor /
    // setVelocityLimit×fields / setPressureAdvance / setFan / setRetraction) do NOT exist yet — they land
    // in 17-02. These stubs compile against ONLY existing symbols and carry the exact membership +
    // availability target in the fail() message. 17-02 converts them to real assertions.

    @Test
    fun speedFactorAndFlowFactor_inAll_withGcodeMoveAvailability() {
        // Target (17-02): CommandRegistry.speedFactor and .flowFactor are in CommandRegistry.all, each with
        //   availability == AvailabilityPredicate.ObjectPresent("gcode_move").
        fail("RED — 17-02: speedFactor + flowFactor in .all, availability=ObjectPresent(\"gcode_move\")")
    }

    @Test
    fun setVelocityLimit_perField_inAll_withToolheadAvailability() {
        // Target (17-02): each motion-limit spec (velocity/accel/minCruiseRatio/scv) is in CommandRegistry.all
        //   with availability == AvailabilityPredicate.ObjectPresent("toolhead").
        fail("RED — 17-02: setVelocityLimit (×fields) in .all, availability=ObjectPresent(\"toolhead\")")
    }

    @Test
    fun setPressureAdvance_inAll_withExtruderAvailability() {
        // Target (17-02): CommandRegistry.setPressureAdvance in .all, availability=ObjectPresent("extruder").
        fail("RED — 17-02: setPressureAdvance in .all, availability=ObjectPresent(\"extruder\")")
    }

    @Test
    fun setFan_inAll_withFanAvailability() {
        // Target (17-02): CommandRegistry.setFan in .all, availability=ObjectPresent("fan").
        fail("RED — 17-02: setFan in .all, availability=ObjectPresent(\"fan\")")
    }

    @Test
    fun setRetraction_inAll_withFirmwareRetractionAvailability() {
        // Target (17-02): CommandRegistry.setRetraction in .all, availability=ObjectPresent("firmware_retraction").
        fail("RED — 17-02: setRetraction in .all, availability=ObjectPresent(\"firmware_retraction\")")
    }

    @Test
    fun motionLimitSpecs_useDistinctDispatchKeysPerField() {
        // Pitfall 4 (17-02): the four motion-limit fields must NOT share a single "set_vel_limit" dispatchKey —
        //   each field's dispatchKey is DISTINCT (e.g. "set_vel_velocity", "set_vel_accel", "set_vel_minCruiseRatio",
        //   "set_vel_scv") so an in-flight velocity tweak never busy-locks an accel tweak by key-collision.
        fail("RED — 17-02: motion-limit specs use DISTINCT per-field dispatchKeys (not a shared set_vel_limit)")
    }

    private fun <P> assertRegistryScript(
        spec: CommandSpec<P>,
        args: P,
        expectedScript: String,
    ) {
        assertEquals("registry gcode commands must dispatch through gcode.script", JsonRpcMethods.GCODE_SCRIPT, spec.method)
        val params = spec.params(args)!!.jsonObject
        assertEquals(expectedScript, params["script"]!!.jsonPrimitive.content)
    }
}
