package works.mees.dinghy.spool

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import works.mees.dinghy.net.GoldenFixtures
import works.mees.dinghy.net.MoonrakerJson

/**
 * RED scaffold (SPOOL-04/08) — Spoolman notify router fan-out.
 *
 * Wave-0 compile-safety: pure `fail()` bodies, no reference to the unbuilt router (the two
 * `MutableSharedFlow`s + `when(method)` arms land in Wave 1's `JsonRpcClient.dispatch()`).
 *
 * Golden: `spoolman-live-ender5-notify.json` — its `notifications` array interleaves nine
 * `notify_proc_stat_update` frames around two `notify_active_spool_set` frames (spool_id 3 then 5),
 * every frame's `params` a 1-ELEMENT array.
 *
 * Target assertions (Wave 1 turns these green):
 *  - `notify_active_spool_set` routes to the active-spool flow, reading `params[0].spool_id` (the
 *    1-element-array extractor), emitting 3 then 5.
 *  - `notify_spoolman_status_changed` routes to the status flow reading `params[0].spoolman_connected`.
 *  - Unrelated `notify_proc_stat_update` frames are IGNORED (fall through `else -> Unit`).
 *  - The fake's [FakeMoonrakerSpoolmanSession] injectors produce the same 1-element-array shape.
 */
class SpoolmanNotifyRouterTest {

    private val session = FakeMoonrakerSpoolmanSession()

    @Test
    fun routesActiveSpoolSetFromOneElementParamsArray() {
        // Confirm the golden's notify shape is the 1-element-array contract the router must read.
        val notifications = MoonrakerJson.parseToJsonElement(GoldenFixtures.raw("spoolman-live-ender5-notify.json"))
            .jsonObject["notifications"]!!
            .jsonArray
        assertNotNull("notify golden must carry a notifications array", notifications)
        // Sanity on the fake (compile-safe collaborator use): its injector is also a 1-element array.
        val frame = session.injectActiveSpoolSet(5)
        assertEquals(1, frame["params"]!!.jsonArray.size)
        fail("RED: notify_active_spool_set router (params[0].spool_id fan-out) not yet implemented")
    }

    @Test
    fun routesSpoolmanStatusChanged() {
        fail("RED: notify_spoolman_status_changed router not yet implemented")
    }

    @Test
    fun ignoresUnrelatedProcStatNotifications() {
        fail("RED: unrelated notify_proc_stat_update must be ignored — not yet implemented")
    }
}
