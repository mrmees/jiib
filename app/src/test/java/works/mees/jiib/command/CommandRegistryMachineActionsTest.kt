package works.mees.jiib.command

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandRegistryMachineActionsTest {
    @Test fun reboot_hasMachineRebootMethod() {
        assertEquals("machine.reboot", CommandRegistry.machineReboot.method)
        assertEquals("machine_reboot", CommandRegistry.machineReboot.dispatchKey(Unit))
    }

    @Test fun shutdown_hasMachineShutdownMethod() {
        assertEquals("machine.shutdown", CommandRegistry.machineShutdown.method)
        assertEquals("machine_shutdown", CommandRegistry.machineShutdown.dispatchKey(Unit))
    }

    @Test fun restartService_putsServiceParam() {
        val spec = CommandRegistry.restartService
        assertEquals("machine.services.restart", spec.method)
        val params = spec.params(ServiceRestartArgs("moonraker")) as JsonObject
        assertEquals("moonraker", params["service"]!!.jsonPrimitive.content)
        assertEquals("services_restart_moonraker", spec.dispatchKey(ServiceRestartArgs("moonraker")))
    }
}
