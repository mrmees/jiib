package works.mees.dinghy.webcam

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.request
import works.mees.dinghy.net.JsonRpcClient
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.net.MoonrakerJson
import works.mees.dinghy.net.MoonrakerSession
import works.mees.dinghy.net.SessionTestHarness
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrinterStateStore

/**
 * CAM-01 / cadence (T-10-07) — the cadence-contract Rule 3 regression guard for the webcam enumeration,
 * mirroring the Phase-13 handshake-edge regression pattern
 * ([works.mees.dinghy.net.KlippyReadyResyncTest] over [SessionTestHarness] / FakeWebSocket).
 *
 * REPLACES the plan-10-01 compiling runtime-RED scaffold now that the production wiring exists
 * (CommandRegistry.webcamsList + WebcamsHolder fired off the session's connectionState handshake edge +
 * SpineHandle.webcams). Asserts PUBLIC OBSERVABLE BEHAVIOR — the harness's `server.webcams.list` request
 * hit-count and the captured outbound `objects.subscribe` frame — NEVER the private `V1_SUBSCRIBE_CORE`
 * constant. The seam is the [SessionTestHarness.webcamsListRequests] counter (added in 10-01).
 *
 * The holder under test is the SAME one MoonrakerService wires (`WebcamsHolder(scope, session
 * .connectionState) { rpc.request(CommandRegistry.webcamsList, Unit) }`), driven by the SAME real session
 * + fake transport — so the once-per-handshake-edge contract is proven end-to-end, not on a stub.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebcamEnumerationCadenceTest {

    @Test
    fun enumerationFiresExactlyOncePerHandshakeEdge_zeroOnSubsequentTicks_notInSubscribeFrame() =
        runTest(UnconfinedTestDispatcher()) {
            val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
            val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
            val harness = SessionTestHarness()
            val session = MoonrakerSession(store, rpc, harness.socketEvents())

            // The production wiring verbatim: the holder fires the one-shot off the handshake edge (the
            // rising edge into Connected), fetching via the captured session rpc — the metadata/lastJob seam.
            val holder = WebcamsHolder(backgroundScope, session.connectionState) {
                runCatching { rpc.request(CommandRegistry.webcamsList, Unit) }.getOrNull()
            }

            val run = launch { session.run() }

            // --- ONE handshake edge: the initial connect reaches Connected exactly once. ---
            session.connectionState.first { it is ConnectionState.Connected }
            runCurrent()

            // (a) The enumeration fired EXACTLY ONCE for that edge (public seam = the request hit-count),
            // and the holder is populated from the canned {webcams:[...]} reply (two cams in the fixture).
            assertEquals(
                "server.webcams.list must be requested exactly ONCE per handshake edge",
                1,
                harness.webcamsListRequests,
            )
            assertEquals(
                "the holder must be populated from the one-shot enumeration reply (2 cams in the fixture)",
                2,
                holder.webcams.value.size,
            )

            // (c) The post-handshake objects.subscribe frame carries NO webcam objects — the enumeration
            // is a one-shot server.webcams.list read, NOT part of the object subscription. Inspect the
            // captured outbound subscribe frame's params.objects keys (PUBLIC behavior, not the constant).
            val subscribeObjects = subscribeObjectKeys(harness)
            assertTrue(
                "the client must emit an objects.subscribe frame after the handshake",
                subscribeObjects != null,
            )
            assertTrue(
                "the objects.subscribe frame must carry NO webcam objects (webcams is a one-shot read, " +
                    "not a subscribe): saw $subscribeObjects",
                subscribeObjects!!.none { it.contains("webcam", ignoreCase = true) },
            )

            // (b) Advancing virtual time / firing subsequent ticks must NOT re-fetch — the read is
            // edge-driven, NOT wall-clock-timer-driven. The count stays put at 1.
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(
                "advancing virtual time must NOT re-fetch the enumeration (it is edge-driven, not a poll)",
                1,
                harness.webcamsListRequests,
            )

            // --- A SECOND handshake edge: the in-session re-handshake after notify_klippy_ready
            // re-emits Syncing → Connected (the FIRMWARE_RESTART / SAVE_CONFIG reload path). The holder
            // re-fires the one-shot, bumping the hit-count to EXACTLY 2 (one-per-edge, metadata/lastJob
            // precedent) — re-enumerated, NOT polled.
            harness.injectKlippyDrop()
            runCurrent()
            harness.injectKlippyReady()
            runCurrent()
            session.connectionState.first { it is ConnectionState.Connected }
            runCurrent()

            assertEquals(
                "a SECOND handshake edge (klippy_ready re-handshake) must re-fire the enumeration ONCE " +
                    "more (hit-count == 2) — one-per-edge, the metadata/lastJob precedent",
                2,
                harness.webcamsListRequests,
            )

            // And still no drift into a poll on the steady stream after the second edge.
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(
                "no re-fetch on subsequent ticks after the second edge either (still edge-driven)",
                2,
                harness.webcamsListRequests,
            )

            run.cancelAndJoin()
        }

    /**
     * The `params.objects` keys of the most-recent outbound `objects.subscribe` frame the session sent,
     * or null if none was sent. PUBLIC behavior — reads the captured outbound frames, never a spine constant.
     */
    private fun subscribeObjectKeys(harness: SessionTestHarness): Set<String>? {
        val frames = harness.current.get()?.sentFrames?.toList() ?: return null
        val subscribe = frames.lastOrNull { raw ->
            runCatching {
                MoonrakerJson.parseToJsonElement(raw).jsonObject["method"]?.jsonPrimitive?.content
            }.getOrNull() == JsonRpcMethods.OBJECTS_SUBSCRIBE
        } ?: return null
        return runCatching {
            (MoonrakerJson.parseToJsonElement(subscribe).jsonObject["params"] as? JsonObject)
                ?.get("objects")?.jsonObject?.keys
        }.getOrNull()
    }
}
