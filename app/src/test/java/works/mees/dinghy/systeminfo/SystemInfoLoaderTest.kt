package works.mees.dinghy.systeminfo

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SystemInfoLoaderTest {
    private fun el(s: String) = Json.parseToJsonElement(s)

    @Test fun loadVersions_pullsBothCalls() = runTest {
        val loader = SystemInfoLoader(
            queryObjects = { error("unused") },
            queryPrinterInfo = { el("""{"software_version":"vK"}""") },
            queryServerInfo = { el("""{"moonraker_version":"vM"}""") },
        )
        assertEquals(Versions("vK", "vM"), loader.loadVersions())
    }

    @Test fun loadMcuDevices_emptyWhenNoMcuObjects_noQuery() = runTest {
        var queried = false
        val loader = SystemInfoLoader(
            queryObjects = { queried = true; el("{}") },
            queryPrinterInfo = { el("{}") }, queryServerInfo = { el("{}") },
        )
        assertEquals(emptyList<McuDevice>(), loader.loadMcuDevices(setOf("extruder", "toolhead")))
        assertFalse(queried)
    }

    @Test fun loadMcuDevices_queriesNamesPlusConfigfile() = runTest {
        var captured: Set<String>? = null
        val loader = SystemInfoLoader(
            queryObjects = { names ->
                captured = names
                el("""{"status":{"mcu":{"mcu_version":"v1","last_stats":{"mcu_awake":0.1}}}}""")
            },
            queryPrinterInfo = { el("{}") }, queryServerInfo = { el("{}") },
        )
        val devices = loader.loadMcuDevices(setOf("mcu", "heater_bed"))
        assertEquals(setOf("mcu", "configfile"), captured)
        assertEquals("v1", devices.single().firmwareVersion)
    }

    @Test fun refreshStats_requeriesMcuObjectsOnly_mergesStats() = runTest {
        var captured: Set<String>? = null
        val loader = SystemInfoLoader(
            queryObjects = { names -> captured = names; el("""{"status":{"mcu":{"last_stats":{"mcu_awake":0.9}}}}""") },
            queryPrinterInfo = { el("{}") }, queryServerInfo = { el("{}") },
        )
        val existing = listOf(McuDevice(key = "mcu", displayName = "Mainboard", chip = "stm32"))
        val merged = loader.refreshStats(setOf("mcu", "extruder"), existing)
        assertEquals(setOf("mcu"), captured)          // configfile NOT re-queried on refresh
        assertEquals("stm32", merged[0].chip)
        assertEquals(0.9f, merged[0].mcuAwake)
    }
}
