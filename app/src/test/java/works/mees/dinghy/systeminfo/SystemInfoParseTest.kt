// RED scaffold (Phase 20 Wave 0 / Plan 20-01) — turns GREEN in Plan 02 (SystemInfo.from).
package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import works.mees.dinghy.net.MoonrakerJson
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (20-01) — turned GREEN by Plan 02 (`SystemInfo.from(...)`).
 *
 * SYS-01. Walks the REAL `machine.system_info.result.system_info` shape committed as
 * `/fixtures/system_info_e5.json` (RPi4) and `/fixtures/system_info_e3.json` (RockPro64),
 * captured live 2026-06-08 from both printers.
 *
 * REAL-SHAPE CONTRACT this test pins (the mock-vs-reality traps — 20-RESEARCH):
 *   - `cpu_info.model` is "Raspberry Pi 4 Model B Rev 1.4" on the Pi but the EMPTY STRING ""
 *     on the RockPro64 (NOT null, NOT absent) → degrade to "—".
 *   - `kernel_version` lives UNDER `distribution`, NOT at the top level of system_info
 *     (RESEARCH Pitfall 3). A top-level read yields nothing.
 *   - `cpu_info.cpu_count` is the integer core count (4 on E5, 6 on E3).
 *   - memory is in kB (`memory_units` == "kB").
 *
 * Production symbol referenced (NOT YET BUILT → RED): `SystemInfo.from(jsonObject)` in
 * `works.mees.dinghy.systeminfo` — Plan 02 introduces it.
 */
class SystemInfoParseTest {

    private fun systemInfoFixture(name: String): JsonObject {
        val res = javaClass.getResource("/fixtures/$name")
            ?: error("fixture /fixtures/$name missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    @Test
    fun e5_parsesModelAndCpuCount() {
        // Fixture-shape assertion (proves the resource loads + carries the expected keys) — idiom (a).
        val root = systemInfoFixture("system_info_e5.json")
        val cpu = root["system_info"]!!.jsonObject["cpu_info"]!!.jsonObject
        assertTrue("E5 cpu_info has model + cpu_count", cpu.containsKey("model") && cpu.containsKey("cpu_count"))
        // Behavior assertion pending Plan 02: SystemInfo.from(root).model == "Raspberry Pi 4 Model B Rev 1.4"
        // and SystemInfo.from(root).cpuCount == 4.
        assumeTrue("Plan 02 implements works.mees.dinghy.systeminfo.SystemInfo.from(...)", false)
    }

    @Test
    fun e3_emptyModelDegrades() {
        // The RockPro64 model is the empty string "" (RESEARCH Pitfall 4) — degrade-to-"—".
        val root = systemInfoFixture("system_info_e3.json")
        val model = root["system_info"]!!.jsonObject["cpu_info"]!!.jsonObject["model"].toString()
        assertTrue("E3 model is the empty string \"\", not null/absent", model == "\"\"")
        // Behavior pending Plan 02: SystemInfo.from(root).model renders "—" (blank string treated as missing).
        assumeTrue("Plan 02 implements works.mees.dinghy.systeminfo.SystemInfo.from(...)", false)
    }

    @Test
    fun kernelComesFromDistributionNotTopLevel() {
        // kernel_version is UNDER distribution (RESEARCH Pitfall 3) — NOT system_info top-level.
        val root = systemInfoFixture("system_info_e5.json")
        val sysInfo = root["system_info"]!!.jsonObject
        assertTrue("kernel_version is NOT a top-level system_info key", !sysInfo.containsKey("kernel_version"))
        assertTrue(
            "kernel_version lives under distribution",
            sysInfo["distribution"]!!.jsonObject.containsKey("kernel_version")
        )
        // Behavior pending Plan 02: SystemInfo.from(root).kernel == "6.12.87+rpt-rpi-v8".
        assumeTrue("Plan 02 implements works.mees.dinghy.systeminfo.SystemInfo.from(...)", false)
    }
}
