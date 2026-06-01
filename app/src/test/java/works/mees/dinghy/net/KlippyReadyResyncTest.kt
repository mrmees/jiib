package works.mees.dinghy.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrinterStateStore

/**
 * G2 (HIGH) + G3 (MED), Phase-5 verification: after Klipper emits `notify_klippy_ready` over the
 * EXISTING (still-open) Moonraker websocket — what it does after a FIRMWARE_RESTART / printer.cfg
 * reload — the session must re-run the FULL handshake: re-objects/subscribe (restoring the lost
 * subscription so temps/positions resume — G2) AND re-run BOTH one-shot reads (temperature_store
 * backfill + the configfile min_extrude_temp / max_extrude_only_distance query) so a config edit is
 * reflected (G3).
 *
 * Faithful-mock discipline (the project's mock-vs-reality lesson): the test injects a GENUINE no-id
 * `notify_klippy_ready` notification frame the way the live server sends it (routed through the real
 * [JsonRpcClient.dispatch] → klippyEvents path), not a synthetic shortcut, so the test exercises the
 * real push path. A test that re-handshakes would FAIL against the pre-fix session (which never
 * re-handshakes) — this is the regression guard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KlippyReadyResyncTest {

    private fun methodsOf(frames: List<String>): List<String> =
        frames.mapNotNull {
            runCatching { MoonrakerJson.parseToJsonElement(it).jsonObject["method"]?.jsonPrimitive?.content }
                .getOrNull()
        }

    /** Count outbound `objects.query` frames whose `params.objects` keys are exactly `{configfile}`. */
    private fun configfileQueryCount(frames: List<String>): Int =
        frames.count { raw ->
            runCatching {
                val obj = MoonrakerJson.parseToJsonElement(raw).jsonObject
                val method = obj["method"]?.jsonPrimitive?.content
                if (method != JsonRpcMethods.OBJECTS_QUERY) return@runCatching false
                val keys = obj["params"]?.jsonObject?.get("objects")?.jsonObject?.keys
                keys == setOf("configfile")
            }.getOrDefault(false)
        }

    private fun count(frames: List<String>, method: String): Int =
        methodsOf(frames).count { it == method }

    @Test
    fun klippyReadyOnLiveSocket_reRunsFullHandshake_resubscribeAndOneShotReads() =
        runTest(UnconfinedTestDispatcher()) {
            val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
            val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
            val harness = SessionTestHarness()
            val session = MoonrakerSession(store, rpc, harness.socketEvents())

            val run = launch { session.run() }
            // Initial handshake completes → Connected.
            session.connectionState.first { it is ConnectionState.Connected }

            val fake = harness.current.get()!!
            val before = fake.sentFrames.toList()
            val subscribeBefore = count(before, JsonRpcMethods.OBJECTS_SUBSCRIBE)
            val tempStoreBefore = count(before, JsonRpcMethods.TEMPERATURE_STORE)
            val configfileBefore = configfileQueryCount(before)

            // Sanity: the initial handshake issued exactly one of each.
            assertEquals(1, subscribeBefore)
            assertEquals(1, tempStoreBefore)
            assertEquals(1, configfileBefore)

            // Inject the UNSOLICITED no-id notify_klippy_ready frame Klipper sends after a
            // FIRMWARE_RESTART on the SAME open socket — exactly as the live server emits it.
            fake.inject("""{"jsonrpc":"2.0","method":"notify_klippy_ready"}""")
            advanceUntilIdle()

            val after = fake.sentFrames.toList()
            // The FULL handshake re-ran on the same socket: each of these strictly increased.
            assertTrue(
                "notify_klippy_ready must re-issue objects.subscribe (G2: restore the lost subscription)",
                count(after, JsonRpcMethods.OBJECTS_SUBSCRIBE) > subscribeBefore,
            )
            assertTrue(
                "notify_klippy_ready must re-run the temperature_store backfill (full re-handshake)",
                count(after, JsonRpcMethods.TEMPERATURE_STORE) > tempStoreBefore,
            )
            assertTrue(
                "notify_klippy_ready must re-run the configfile one-shot read (G3: stale config refresh)",
                configfileQueryCount(after) > configfileBefore,
            )

            run.cancelAndJoin()
        }

    @Test
    fun klippyReadyRefreshesStaleConfig_changedMinExtrudeTempPropagates() =
        runTest(UnconfinedTestDispatcher()) {
            val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
            val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
            val harness = SessionTestHarness()
            val session = MoonrakerSession(store, rpc, harness.socketEvents())

            val run = launch { session.run() }
            session.connectionState.first { it is ConnectionState.Connected }

            // Initial handshake read the default min_extrude_temp (170.0).
            assertEquals(170.0f, store.minExtrudeTemp.value)

            // Simulate a printer.cfg edit: the configfile read AFTER the klippy restart returns 220.0.
            harness.configfileResultJson = """
                {"eventtime":100003.0,"status":{"configfile":{"settings":{"extruder":{
                  "min_extrude_temp":220.0,"max_extrude_only_distance":50.0}}}}}
            """.trimIndent()

            val fake = harness.current.get()!!
            fake.inject("""{"jsonrpc":"2.0","method":"notify_klippy_ready"}""")
            advanceUntilIdle()

            assertEquals(
                "the re-handshake must refresh the stale min_extrude_temp from the reloaded config (G3)",
                220.0f,
                store.minExtrudeTemp.value,
            )

            run.cancelAndJoin()
        }
}
