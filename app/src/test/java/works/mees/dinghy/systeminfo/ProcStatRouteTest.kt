package works.mees.dinghy.systeminfo

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import works.mees.dinghy.net.JsonRpcClient
import works.mees.dinghy.net.MoonrakerJson

/**
 * SYS-02 spine route (20-03 Task 1). Drives the real [JsonRpcClient.dispatch] over a
 * `notify_proc_stat_update` envelope wrapped around the live-captured push body
 * (`/fixtures/notify_proc_stat_push_e5.json`) and asserts:
 *  - the frame surfaces on [JsonRpcClient.procStatUpdates] carrying `cpu_temp` + `system_cpu_usage` +
 *    `system_memory`, and
 *  - [ProcStatLive.fromPush] on the emitted object yields non-null cpuTemp/load/mem.
 *
 * This is the new flow's own gate (the golden test in SpoolmanNotifyRouterTest pins the no-regression
 * "never reaches activeSpoolSet" half); together they cover Phase 20's coupled-edit landmine #1.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProcStatRouteTest {

    /** The captured push body (params[0]) — the same fixture ProcStatPushTest walks. */
    private fun pushBody(): JsonObject {
        val res = javaClass.getResource("/fixtures/notify_proc_stat_push_e5.json")
            ?: error("fixture /fixtures/notify_proc_stat_push_e5.json missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    /** Wrap the push body as the real wire notify envelope: `{method, params:[<body>]}`. */
    private fun notifyEnvelope(body: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", "notify_proc_stat_update")
        putJsonArray("params") { add(body) }
    }

    @Test
    fun dispatchedProcStatFrameSurfacesAndParses() = runTest {
        val client = JsonRpcClient()
        val seen = mutableListOf<JsonObject>()
        // Subscribe BEFORE dispatch (the SharedFlow has no replay).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            client.procStatUpdates.collect { seen += it }
        }
        advanceUntilIdle()

        client.dispatch(notifyEnvelope(pushBody()).toString())
        advanceUntilIdle()

        assertEquals("the proc-stat frame must surface on procStatUpdates", 1, seen.size)
        val emitted = seen.single()
        // The emitted object is the raw params[0] host-telemetry body.
        assertNotNull("emitted payload carries cpu_temp", emitted["cpu_temp"])
        assertNotNull("emitted payload carries system_cpu_usage", emitted["system_cpu_usage"])
        assertNotNull("emitted payload carries system_memory", emitted["system_memory"])

        // The holder runs ProcStatLive.fromPush on exactly this object — it must parse to live telemetry.
        val live = ProcStatLive.fromPush(emitted)
        assertNotNull("fromPush yields cpuTemp", live.cpuTemp)
        assertNotNull("fromPush yields cpuLoadPercent", live.cpuLoadPercent)
        assertNotNull("fromPush yields memUsedKb", live.memUsedKb)
    }
}
