// GREEN (Plan 20-02) — live assertions against the both-SBC system_info fixtures.
package works.mees.jiib.systeminfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import works.mees.jiib.net.MoonrakerJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SYS-01. Walks the REAL `machine.system_info.result.system_info` shape committed as
 * `/fixtures/system_info_e5.json` (RPi4) and `/fixtures/system_info_e3.json` (RockPro64),
 * captured live 2026-06-08 from both printers.
 *
 * REAL-SHAPE CONTRACT this test pins (the mock-vs-reality traps — 20-RESEARCH):
 *   - `cpu_info.model` is "Raspberry Pi 4 Model B Rev 1.4" on the Pi but the EMPTY STRING ""
 *     on the RockPro64 (NOT null, NOT absent) → degrade to null (UI "—").
 *   - `kernel_version` lives UNDER `distribution`, NOT at the top level of system_info (Pitfall 3).
 *   - `cpu_info.cpu_count` is the integer core count (4 on E5, 6 on E3).
 *   - memory is in kB (`memory_units` == "kB").
 */
class SystemInfoParseTest {

    private fun loadFixture(path: String): JsonObject {
        val res = javaClass.getResource(path)
            ?: error("fixture $path missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    // Explicit literal getResource("/fixtures/system_info_*") call-sites (key-link to the fixtures).
    private fun systemInfoE5(): JsonObject = loadFixture("/fixtures/system_info_e5.json")
    private fun systemInfoE3(): JsonObject = loadFixture("/fixtures/system_info_e3.json")

    @Test
    fun e5_parsesModelAndCpuCount() {
        val info = SystemInfo.from(systemInfoE5())
        assertEquals("Raspberry Pi 4 Model B Rev 1.4", info.model)
        assertEquals(4, info.cpuCount)
        assertEquals(8007452L, info.totalMemoryKb)
        assertEquals("aarch64", info.processor)
        assertEquals("Debian GNU/Linux 12 (bookworm)", info.distroName)
    }

    @Test
    fun e3_emptyModelDegrades() {
        // The RockPro64 model is the empty string "" — degrade-to-null (UI "—").
        val info = SystemInfo.from(systemInfoE3())
        assertNull("blank model string is treated as missing", info.model)
        assertEquals(6, info.cpuCount)
        assertEquals("Armbian 25.11.2 noble", info.distroName)
        assertEquals("6.18.10-current-rockchip64", info.kernel)
    }

    @Test
    fun kernelComesFromDistributionNotTopLevel() {
        // kernel_version is UNDER distribution (Pitfall 3) — NOT a system_info top-level key.
        val sysInfo = systemInfoE5()["system_info"]!!.jsonObject
        assertTrue("kernel_version is NOT a top-level system_info key", !sysInfo.containsKey("kernel_version"))
        val info = SystemInfo.from(systemInfoE5())
        assertEquals("6.12.87+rpt-rpi-v8", info.kernel)
    }

    @Test
    fun parsesProviderAndAvailableServices() {
        val result = Json.parseToJsonElement(
            """{"system_info":{"provider":"systemd_dbus","available_services":["klipper","moonraker"]}}""",
        ).jsonObject
        val info = SystemInfo.from(result)
        assertEquals("systemd_dbus", info.provider)
        assertEquals(listOf("klipper", "moonraker"), info.availableServices)
    }
}
