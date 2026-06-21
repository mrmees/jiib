package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionAndActionsTest {
    @Test fun parsesKlipperAndMoonrakerVersions() {
        val pi = Json.parseToJsonElement("""{"software_version":"v0.12.0-145-gabc","hostname":"e5"}""")
        val si = Json.parseToJsonElement("""{"moonraker_version":"v0.9.3-1-gdef","klippy_connected":true}""")
        assertEquals("v0.12.0-145-gabc", parseKlipperVersion(pi))
        assertEquals("v0.9.3-1-gdef", parseMoonrakerVersion(si))
        assertEquals(null, parseKlipperVersion(null))
        assertEquals(null, parseMoonrakerVersion(Json.parseToJsonElement("""{}""")))
    }

    @Test fun availability_defaultEnabledWhenUnknown() {
        val a = hostActionAvailability(SystemInfo())   // no provider, no services
        assertTrue(a.canReboot); assertTrue(a.canShutdown); assertTrue(a.canRestartMoonraker)
        assertNull(a.powerDisabledReason)
        assertNull(a.moonrakerDisabledReason)
    }

    @Test fun availability_providerNoneDisablesPower() {
        val a = hostActionAvailability(SystemInfo(provider = "none"))
        assertFalse(a.canReboot); assertFalse(a.canShutdown)
        assertNotNull(a.powerDisabledReason)
        assertTrue(a.powerDisabledReason!!.isNotBlank())
    }

    @Test fun availability_supervisordDisablesPower() {
        // A container/supervisord host can't reboot the metal (Moonraker machine API).
        val a = hostActionAvailability(SystemInfo(provider = "supervisord_cli"))
        assertFalse(a.canReboot); assertFalse(a.canShutdown)
        assertNotNull(a.powerDisabledReason)
    }

    @Test fun availability_systemdEnablesPower() {
        assertTrue(hostActionAvailability(SystemInfo(provider = "systemd_dbus")).canReboot)
        assertTrue(hostActionAvailability(SystemInfo(provider = "systemd_cli")).canShutdown)
    }

    @Test fun availability_servicesWithoutMoonrakerDisablesRestart() {
        val a = hostActionAvailability(SystemInfo(provider = "systemd_dbus", availableServices = listOf("klipper")))
        assertTrue(a.canReboot)
        assertFalse(a.canRestartMoonraker)
        assertNotNull(a.moonrakerDisabledReason)
        assertTrue(a.moonrakerDisabledReason!!.isNotBlank())
        assertNull(a.powerDisabledReason)
    }

    @Test fun availability_nullIdentityAllEnabled() {
        val a = hostActionAvailability(null)
        assertTrue(a.canReboot); assertTrue(a.canShutdown); assertTrue(a.canRestartMoonraker)
    }

    @Test fun availability_emptySupervisordVariantDisablesPower() {
        // supervisord (bare, no suffix) must also disable power
        val a = hostActionAvailability(SystemInfo(provider = "supervisord"))
        assertFalse(a.canReboot); assertFalse(a.canShutdown)
    }

    @Test fun availability_reasonNullWhenEnabled() {
        val a = hostActionAvailability(SystemInfo(provider = "systemd_dbus", availableServices = listOf("klipper", "moonraker")))
        assertTrue(a.canReboot); assertTrue(a.canRestartMoonraker)
        assertNull(a.powerDisabledReason)
        assertNull(a.moonrakerDisabledReason)
    }
}
