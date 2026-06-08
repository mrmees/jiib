// GREEN (Plan 20-02) — live assertions against the captured notify_proc_stat_update push frame.
package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import works.mees.dinghy.net.MoonrakerJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * SYS-02. Walks the REAL captured `notify_proc_stat_update` params[0] frame committed as
 * `/fixtures/notify_proc_stat_push_e5.json` (captured live 2026-06-08 on the E5 wire).
 *
 * REAL-SHAPE CONTRACT this test pins (RESEARCH Pitfall 1 — the single most important finding):
 *   - The PUSH frame carries cpu_temp, system_cpu_usage, system_memory, moonraker_stats, etc.
 *   - The PUSH frame OMITS `throttled_state` AND `system_uptime` — those exist ONLY in the
 *     one-shot machine.proc_stats QUERY result. The push parser MUST tolerate their absence
 *     and must NOT source throttle/uptime from here.
 */
class ProcStatPushTest {

    private fun pushFixture(): JsonObject {
        val res = javaClass.getResource("/fixtures/notify_proc_stat_push_e5.json")
            ?: error("fixture /fixtures/notify_proc_stat_push_e5.json missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    @Test
    fun pushParsesCpuTempLoadMem() {
        val live = ProcStatLive.fromPush(pushFixture())
        assertEquals(64.757f, live.cpuTemp!!, 0.001f)
        assertEquals(29.31f, live.cpuLoadPercent!!, 0.001f)
        assertEquals(761312L, live.memUsedKb)
        assertEquals(8007452L, live.memTotalKb)
        assertEquals(7246140L, live.memAvailableKb)
    }

    @Test
    fun pushParserDoesNotRequireThrottleOrUptime() {
        val push = pushFixture()
        // The live push genuinely OMITS both — the parser must not depend on them.
        assertFalse("push MUST NOT carry throttled_state", push.containsKey("throttled_state"))
        assertFalse("push MUST NOT carry system_uptime", push.containsKey("system_uptime"))
        // fromPush succeeds anyway — throttle/uptime are sourced from the query, not here.
        val live = ProcStatLive.fromPush(push)
        assertEquals(64.757f, live.cpuTemp!!, 0.001f)
    }
}
