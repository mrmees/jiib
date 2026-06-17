package works.mees.dinghy.command

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SetFilamentSensorCommandTest {

    @Test
    fun builderFormatsEnableDisable() {
        assertEquals("SET_FILAMENT_SENSOR SENSOR=Runout ENABLE=1", PrinterCommands.setFilamentSensor("Runout", true))
        assertEquals("SET_FILAMENT_SENSOR SENSOR=Runout ENABLE=0", PrinterCommands.setFilamentSensor("Runout", false))
    }

    @Test
    fun registryWrapsBuilderByteIdentically() {
        val spec = CommandRegistry.setFilamentSensor
        val script = spec.params(SetFilamentSensorArgs("encoder_sensor", false))
            ?.let { (it as kotlinx.serialization.json.JsonObject)["script"]?.jsonPrimitive?.content }
        assertEquals(PrinterCommands.setFilamentSensor("encoder_sensor", false), script)
    }

    @Test
    fun dispatchKeyIsPerSensor() {
        assertEquals("set_filament_sensor_encoder_sensor",
            CommandRegistry.setFilamentSensor.dispatchKey(SetFilamentSensorArgs("encoder_sensor", true)))
    }

    @Test
    fun registeredInAll() {
        assert(CommandRegistry.all.contains(CommandRegistry.setFilamentSensor))
    }
}
