// GREEN (Plan 20-02) — live degrade assertions: blank model, null throttle, missing keys never crash.
package works.mees.jiib.systeminfo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import works.mees.jiib.net.MoonrakerJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SYS-04. Graceful degradation: a missing/blank/null field surfaces null on the model (UI "—"), the
 * page NEVER crashes on sparse or older Moonraker (RESEARCH Pitfalls 2 & 4).
 *
 * REAL-SHAPE traps this pins, all exercised by the live fixtures:
 *   - `cpu_info.model` == "" on the RockPro64 (empty string, NOT null) -> null.
 *   - `throttled_state` == null on the RockPro64 (key present, value JsonNull) -> throttledState null,
 *     no crash, health-chip falls back to temp.
 *   - `proc_stats.result` throttled_state is an object on the Pi -> non-null {bits:0, flags:[]}.
 *   - missing keys (an older/sparser Moonraker) -> all-null model, no exception.
 */
class DegradeTest {

    private fun fixture(name: String): JsonObject {
        val res = javaClass.getResource("/fixtures/$name")
            ?: error("fixture /fixtures/$name missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    @Test
    fun emptyModelString_rendersDash() {
        // E3 model is the empty string "" — blank treated as missing → null (UI maps null → "—").
        val info = SystemInfo.from(fixture("system_info_e3.json"))
        assertNull("blank model degrades to null", info.model)
    }

    @Test
    fun nullThrottledState_noCrash() {
        // E3 throttled_state is explicit JsonNull (Pitfall 2) — must NOT parse-crash → null.
        val query = ProcStatQuery.from(fixture("proc_stats_e3.json"))
        assertNull("JsonNull throttled_state yields no throttle data", query.throttledState)
        assertEquals(53.333f, query.cpuTemp!!, 0.001f)
        assertEquals(1178981.142290853, query.systemUptimeSeconds!!, 0.01)
    }

    @Test
    fun e5ThrottledState_parsesObject() {
        // E5 throttled_state is an object {bits:0, flags:[]} — non-null, with the live cpu_temp/uptime.
        val query = ProcStatQuery.from(fixture("proc_stats_e5.json"))
        assertNotNull("Pi throttled_state parses to an object", query.throttledState)
        assertEquals(0, query.throttledState!!.bits)
        assertEquals(emptyList<String>(), query.throttledState!!.flags)
        assertEquals(64.757f, query.cpuTemp!!, 0.001f)
        assertEquals(312773.755726872, query.systemUptimeSeconds!!, 0.01)
    }

    @Test
    fun missingKeys_rendersDash() {
        // A sparse/older Moonraker omits keys entirely -> all-null model, no throw.
        val empty = buildJsonObject { }
        val info = SystemInfo.from(empty)
        assertNull(info.model)
        assertNull(info.cpuCount)
        assertNull(info.kernel)
        assertNull(info.totalMemoryKb)

        // Likewise an empty proc_stats result and a null payload never throw.
        val query = ProcStatQuery.from(empty)
        assertNull(query.throttledState)
        assertNull(query.cpuTemp)

        val live = ProcStatLive.fromPush(empty)
        assertNull(live.cpuTemp)
        assertNull(live.memUsedKb)

        // Null inputs are tolerated too.
        assertNull(SystemInfo.from(null).model)
        assertNull(ProcStatQuery.from(null).cpuTemp)
        assertNull(ProcStatLive.fromPush(null).cpuTemp)
    }
}
