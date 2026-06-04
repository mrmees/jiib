package works.mees.dinghy.spool

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import works.mees.dinghy.net.GoldenFixtures
import works.mees.dinghy.net.JsonRpcClient
import works.mees.dinghy.net.MoonrakerJson

/**
 * Spoolman notify router fan-out (SPOOL-04/08) — GREEN as of plan 11-04.
 *
 * Drives the real [JsonRpcClient.dispatch] over frames lifted from the live
 * `spoolman-live-ender5-notify.json` golden (its `notifications` array interleaves ten
 * `notify_proc_stat_update` frames around two `notify_active_spool_set` frames — spool_id 3 then 5 —
 * every frame's `params` a 1-ELEMENT array) and the fake's status-changed injector, asserting:
 *  - `notify_active_spool_set` routes to [JsonRpcClient.activeSpoolSet] reading `params[0].spool_id`;
 *  - `notify_spoolman_status_changed` routes to [JsonRpcClient.spoolmanStatusChanged] reading
 *    `params[0].spoolman_connected`;
 *  - unrelated `notify_proc_stat_update` frames are IGNORED (fall through `else -> Unit`) — the
 *    active-spool flow only ever sees the two spool ids, never a proc-stat frame.
 *
 * The bounded SharedFlows have no replay, so a collector is subscribed in [backgroundScope] BEFORE the
 * frames are dispatched (with [advanceUntilIdle] flushing the virtual-time scheduler between).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SpoolmanNotifyRouterTest {

    private val session = FakeMoonrakerSpoolmanSession()

    /** The live golden's `notifications` array, in order (proc_stat frames around the two spool-set frames). */
    private fun goldenNotifications() =
        MoonrakerJson.parseToJsonElement(GoldenFixtures.raw("spoolman-live-ender5-notify.json"))
            .jsonObject["notifications"]!!
            .jsonArray

    @Test
    fun routesActiveSpoolSetFromOneElementParamsArray() = runTest {
        // Sanity: the golden's notify shape is the 1-element-array contract the router reads.
        assertNotNull("notify golden must carry a notifications array", goldenNotifications())
        val frame = session.injectActiveSpoolSet(5)
        assertEquals(1, frame["params"]!!.jsonArray.size)

        val client = JsonRpcClient()
        val collected = mutableListOf<Int?>()
        // Subscribe BEFORE dispatch (no replay) so both spool-set emissions are observed in order.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            client.activeSpoolSet.collect { params ->
                collected += params["spool_id"]?.jsonPrimitive?.intOrNull
            }
        }
        advanceUntilIdle()

        // Dispatch the WHOLE golden notify sequence (the two spool-set frames buried among proc_stat noise).
        for (notif in goldenNotifications()) {
            client.dispatch(notif.toString())
        }
        advanceUntilIdle()

        // Router must have routed BOTH active-spool-set frames in order (3 then 5) and ONLY those —
        // the nine proc_stat frames never reach this flow.
        assertEquals("router must read params[0].spool_id for both frames in order", listOf(3, 5), collected)
    }

    @Test
    fun routesSpoolmanStatusChanged() = runTest {
        val client = JsonRpcClient()
        val collected = mutableListOf<Boolean?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            client.spoolmanStatusChanged.collect { params ->
                collected += params["spoolman_connected"]?.jsonPrimitive?.booleanOrNull
            }
        }
        advanceUntilIdle()

        // The status-changed push carries params[0].spoolman_connected (the fake replicates the live shape).
        val frame = session.injectSpoolmanStatusChanged(connected = true)
        assertEquals(1, frame["params"]!!.jsonArray.size)
        client.dispatch(frame.toString())
        advanceUntilIdle()

        assertEquals("status-changed must route params[0].spoolman_connected", listOf(true), collected)
    }

    @Test
    fun ignoresUnrelatedProcStatNotifications() = runTest {
        val client = JsonRpcClient()
        val seen = mutableListOf<Int?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            client.activeSpoolSet.collect { params ->
                seen += params["spool_id"]?.jsonPrimitive?.intOrNull
            }
        }
        advanceUntilIdle()

        // Dispatch ONLY the proc_stat frames from the golden — none must reach the active-spool flow.
        val procStatFrames = goldenNotifications().filter {
            it.jsonObject["method"]?.jsonPrimitive?.content == "notify_proc_stat_update"
        }
        assertEquals("golden must carry the ten proc_stat frames", 10, procStatFrames.size)
        for (notif in procStatFrames) {
            client.dispatch(notif.toString())
        }
        advanceUntilIdle()

        assertEquals("unrelated notify_proc_stat_update must be ignored (else -> Unit)", emptyList<Int?>(), seen)
    }
}
