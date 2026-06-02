package works.mees.dinghy.command

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
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
