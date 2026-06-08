// RED scaffold (Phase 20 Wave 0 / Plan 20-01) — turns GREEN in Plan 02 (ProcStatLive.fromPush).
package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import works.mees.dinghy.net.MoonrakerJson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (20-01) — turned GREEN by Plan 02 (`ProcStatLive.fromPush(...)`).
 *
 * SYS-02. Walks the REAL captured `notify_proc_stat_update` params[0] frame committed as
 * `/fixtures/notify_proc_stat_push_e5.json` (captured live 2026-06-08 on the E5 wire).
 *
 * REAL-SHAPE CONTRACT this test pins (RESEARCH Pitfall 1 — the single most important finding):
 *   - The PUSH frame carries cpu_temp, system_cpu_usage, system_memory, moonraker_stats,
 *     network, websocket_connections.
 *   - The PUSH frame OMITS `throttled_state` AND `system_uptime` — those exist ONLY in the
 *     one-shot machine.proc_stats QUERY result. The push parser MUST tolerate their absence
 *     and must NOT source throttle/uptime from here.
 *
 * Production symbol referenced (NOT YET BUILT → RED): `ProcStatLive.fromPush(jsonObject)` in
 * `works.mees.dinghy.systeminfo` — Plan 02 introduces it.
 */
class ProcStatPushTest {

    private fun pushFixture(): JsonObject {
        val res = javaClass.getResource("/fixtures/notify_proc_stat_push_e5.json")
            ?: error("fixture /fixtures/notify_proc_stat_push_e5.json missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    @Test
    fun pushParsesCpuTempLoadMem() {
        // Fixture-shape assertion: the live keys the push parser will read are present — idiom (a).
        val push = pushFixture()
        assertTrue("push carries cpu_temp", push.containsKey("cpu_temp"))
        assertTrue("push carries system_cpu_usage", push.containsKey("system_cpu_usage"))
        assertTrue("push carries system_memory", push.containsKey("system_memory"))
        // Behavior pending Plan 02: ProcStatLive.fromPush(push) -> cpuTemp=64.757, load(cpu)=29.31, mem used/avail.
        assumeTrue("Plan 02 implements works.mees.dinghy.systeminfo.ProcStatLive.fromPush(...)", false)
    }

    @Test
    fun pushParserDoesNotRequireThrottleOrUptime() {
        // idiom (a): the live push genuinely OMITS both — the parser must not depend on them.
        val push = pushFixture()
        assertFalse("push MUST NOT carry throttled_state", push.containsKey("throttled_state"))
        assertFalse("push MUST NOT carry system_uptime", push.containsKey("system_uptime"))
        // Behavior pending Plan 02: ProcStatLive.fromPush(push) succeeds with throttle/uptime sourced elsewhere.
        assumeTrue("Plan 02 implements works.mees.dinghy.systeminfo.ProcStatLive.fromPush(...)", false)
    }
}
