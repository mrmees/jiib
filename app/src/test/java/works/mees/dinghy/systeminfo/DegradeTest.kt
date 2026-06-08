// RED scaffold (Phase 20 Wave 0 / Plan 20-01) — turns GREEN in Plan 02 (parser/degrade logic).
package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import works.mees.dinghy.net.MoonrakerJson
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (20-01) — turned GREEN by Plan 02 (parser/degrade logic).
 *
 * SYS-04. Graceful degradation: a missing/blank/null field renders "—", the labeled row stays
 * present, and the page NEVER crashes on sparse or older Moonraker (RESEARCH Pitfalls 2 & 4).
 *
 * REAL-SHAPE traps this pins, all exercised by the live fixtures:
 *   - `cpu_info.model` == "" on the RockPro64 (empty string, NOT null) -> "—".
 *   - `throttled_state` == null on the RockPro64 (key present, value JsonNull) -> temp fallback, no crash.
 *   - missing keys (an older/sparser Moonraker) -> "—", no exception.
 *
 * Production symbols referenced (NOT YET BUILT → RED): `SystemInfo.from(...)` + the degrade
 * helpers in `works.mees.dinghy.systeminfo` — Plan 02 introduces them.
 */
class DegradeTest {

    private fun fixture(name: String): JsonObject {
        val res = javaClass.getResource("/fixtures/$name")
            ?: error("fixture /fixtures/$name missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    @Test
    fun emptyModelString_rendersDash() {
        // idiom (a): E3 model is the empty string "" — degrade-to-"—" must treat blank as missing.
        val cpu = fixture("system_info_e3.json")["system_info"]!!.jsonObject["cpu_info"]!!.jsonObject
        assertTrue("E3 model is blank \"\"", cpu["model"].toString() == "\"\"")
        // Behavior pending Plan 02: SystemInfo.from(...).model renders "—" for the blank string.
        assumeTrue("Plan 02 implements works.mees.dinghy.systeminfo.SystemInfo.from(...) degrade logic", false)
    }

    @Test
    fun nullThrottledState_noCrash() {
        // idiom (a): E3 throttled_state is explicit JsonNull (RESEARCH Pitfall 2) — must NOT parse-crash.
        val procStats = fixture("proc_stats_e3.json")
        assertTrue("E3 carries throttled_state key", procStats.containsKey("throttled_state"))
        assertTrue("E3 throttled_state value is null", procStats["throttled_state"].toString() == "null")
        // Behavior pending Plan 02: parsing E3 proc_stats yields no throttle data -> temp fallback, no crash.
        assumeTrue("Plan 02 implements works.mees.dinghy.systeminfo proc_stats degrade logic", false)
    }

    @Test
    fun missingKeys_rendersDash() {
        // A sparse/older Moonraker omits keys entirely -> every labeled row still renders "—", no throw.
        assumeTrue("Plan 02 implements works.mees.dinghy.systeminfo.SystemInfo.from(...) degrade logic", false)
    }
}
