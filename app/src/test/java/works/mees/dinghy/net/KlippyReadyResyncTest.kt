package works.mees.dinghy.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterStateStore

/**
 * G2 (HIGH), Phase-5/13 verification — the D-10 KEYSTONE regression. After Klipper emits the captured
 * `notify_klippy_disconnected` → [gap] → `notify_klippy_ready` sequence over the EXISTING (still-open)
 * Moonraker websocket — what it does after a FIRMWARE_RESTART / SAVE_CONFIG reload — the session must
 * re-run the FULL handshake (re-objects/subscribe) so temps/positions RESUME.
 *
 * THE HARDENING (vs the mock that lied): the old version of this test asserted only that subscribe/
 * temp/configfile FRAME COUNTS increased after `notify_klippy_ready` — never that the server accepted
 * them and DIFFS RESUMED. The fake auto-replied success unconditionally and `inject()` delivered any
 * frame straight to the store, so a "resumed diffs" test built on raw inject() passed even with the fix
 * ABSENT. Now: the captured klippy-drop clears `subscriptionActive` (the FakeWebSocket gate), and a
 * post-restart `notify_status_update` injected through that GATED inject() can ONLY reach the store if
 * the session genuinely RE-SUBSCRIBED (flipping the flag back true). We assert the resumed diff lands —
 * proving the subscription is LIVE again, not merely that a subscribe frame left the client.
 *
 * Faithful-mock discipline (the project's mock-vs-reality lesson): the captured drop/ready signals are
 * injected verbatim (the way the live server sends them, routed through the real JsonRpcClient.dispatch
 * → klippyEvents path), and the post-restart status diff passes the SAME subscription gate the real
 * server enforces. The test exercises the real push path with NO test-only un-gated delivery.
 *
 * WAVE-0 COLOR: RED against the current production session (it never emits Connected→Syncing nor
 * recovers a working subscription on this path — that is the bug Wave 1 / plan 13-02 fixes). The test
 * compiles and is the regression guard the fix turns GREEN.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KlippyReadyResyncTest {

    /** A post-restart status diff carrying `print_stats.state=printing` (the resumed-diff probe). */
    private val printingStatusDiff =
        """{"jsonrpc":"2.0","method":"notify_status_update",""" +
            """"params":[{"print_stats":{"state":"printing"}},123.0]}"""

    @Test
    fun klippyRestart_resumesLiveDiffs_onlyViaRealReSubscribe() =
        runTest(UnconfinedTestDispatcher()) {
            val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
            val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
            val harness = SessionTestHarness()
            val session = MoonrakerSession(store, rpc, harness.socketEvents())

            val run = launch { session.run() }
            // Initial handshake completes → Connected. The successful initial objects.subscribe reply
            // flipped subscriptionActive TRUE (so the live diff stream is gated open).
            session.connectionState.first { it is ConnectionState.Connected }

            val fake = harness.current.get()!!

            // Sanity: while subscribed, a status diff reaches the store (the gate is OPEN).
            fake.inject(printingStatusDiff)
            advanceUntilIdle()
            assertEquals(PrintState.Printing, store.printerState.value.printState)

            // --- The captured klippy-down window (FIRMWARE_RESTART / SAVE_CONFIG on the same socket) ---
            // The drop clears subscriptionActive: the subscription is gone, diffs no longer push.
            harness.injectKlippyDrop()
            advanceUntilIdle()

            // While DOWN, an injected diff is DROPPED by the gate inside inject() — it must NOT reach
            // the store. (Reset the probe to Standby first so we can detect a leak.)
            fake.inject(
                """{"jsonrpc":"2.0","method":"notify_status_update",""" +
                    """"params":[{"print_stats":{"state":"standby"}},124.0]}""",
            )
            advanceUntilIdle()
            // The drop-window diff was gated out, so the store still shows the last-known Printing.
            assertEquals(
                "a status diff injected while unsubscribed must be DROPPED by the inject() gate",
                PrintState.Printing,
                store.printerState.value.printState,
            )

            // klippy comes back: the session must re-run the handshake on the SAME socket and the
            // re-subscribe must SUCCEED (klippy is up), flipping subscriptionActive back TRUE.
            harness.injectKlippyReady()
            advanceUntilIdle()

            // The resumed-diff probe: a post-restart notify_status_update injected through the gated
            // inject(). It reaches the store ONLY if a real re-subscribe happened (gate reopened).
            fake.inject(printingStatusDiff)
            advanceUntilIdle()

            assertEquals(
                "after a real re-subscribe the resumed status diff must reach the store (the live " +
                    "subscription is restored, not merely a subscribe frame sent)",
                PrintState.Printing,
                store.printerState.value.printState,
            )

            run.cancelAndJoin()
        }

    /**
     * G3 (config freshness) companion: the re-handshake re-reads configfile, so a printer.cfg edit
     * (changed min_extrude_temp) propagates after the restart. Asserts STORE content (not frame counts).
     * RED-until-13-02 if production does not re-read configfile on this path.
     */
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

            // Drive the captured restart sequence on the same socket (drop → ready).
            harness.injectKlippyDrop()
            advanceUntilIdle()
            harness.injectKlippyReady()
            advanceUntilIdle()

            assertEquals(
                "the re-handshake must refresh the stale min_extrude_temp from the reloaded config (G3)",
                220.0f,
                store.minExtrudeTemp.value,
            )

            run.cancelAndJoin()
        }

    /**
     * D-10 self-heal escalation: when a re-handshake's objects.subscribe is REJECTED in the klippy-down
     * window, the swallow-forever path must NOT win — the session must escalate to a FULL socket
     * reconnect, proven by BOTH (a) harness.opens incrementing (a NEW socket opened) AND (b) a SECOND
     * full handshake (identify + objects.subscribe) appearing on that fresh socket, i.e. connectAndServe
     * was genuinely RE-ENTERED. This exercises the FakeWebSocket.cancel()→onFailure→SocketEvent.Closed
     * lever end-to-end: a simulated re-handshake failure drives a fresh connectAndServe re-entry, not
     * merely a close() call.
     *
     * WAVE-0 COLOR: RED against current production — the re-handshake is `runCatching { runHandshake() }`
     * which SWALLOWS the failure (no socket close, no reconnect). Wave 1 / 13-02 makes it escalate.
     */
    @Test
    fun klippyDownReSubscribeRejected_escalatesToFullReconnect_reEntersConnectAndServe() =
        runTest(UnconfinedTestDispatcher()) {
            val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
            val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
            val harness = SessionTestHarness()
            val session = MoonrakerSession(store, rpc, harness.socketEvents())

            val run = launch { session.run() }
            session.connectionState.first { it is ConnectionState.Connected }

            val opensAfterInitialConnect = harness.opens
            val firstSocket = harness.current.get()!!

            // Enter the klippy-down window so the re-handshake's objects.subscribe is REJECTED (real 503).
            harness.klippyDown = true
            harness.injectKlippyDrop()
            advanceUntilIdle()

            // The restart-complete signal triggers the re-handshake, whose subscribe now fails. The
            // self-heal must close the socket (cancel()→onFailure→Closed) and the supervisor reconnects.
            harness.injectKlippyReady()
            advanceUntilIdle()

            // Let the new socket's handshake run; clear the down window so the reconnect can succeed.
            harness.klippyDown = false
            advanceUntilIdle()

            // (a) A NEW socket was opened (escalation to a full reconnect, not a silent swallow).
            assertTrue(
                "a rejected re-subscribe in the klippy-down window must escalate to a full reconnect " +
                    "(harness.opens must increment) — current production swallows it forever",
                harness.opens > opensAfterInitialConnect,
            )

            // (b) The reconnect genuinely RE-ENTERED connectAndServe: a second full handshake
            // (identify + objects.subscribe) appears on the FRESH socket, not merely close() being called.
            val freshSocket = harness.current.get()!!
            assertTrue("the reconnect must open a genuinely new socket", freshSocket !== firstSocket)
            val freshMethods = methodsOf(freshSocket.sentFrames.toList())
            assertTrue(
                "the reconnect must replay the FULL handshake on the new socket (identify present)",
                freshMethods.contains(JsonRpcMethods.IDENTIFY),
            )
            assertTrue(
                "the reconnect must replay the FULL handshake on the new socket (objects.subscribe present)",
                freshMethods.contains(JsonRpcMethods.OBJECTS_SUBSCRIBE),
            )

            run.cancelAndJoin()
        }

    private fun methodsOf(frames: List<String>): List<String> =
        frames.mapNotNull {
            runCatching {
                MoonrakerJson.parseToJsonElement(it)
                    .let { e -> (e as? kotlinx.serialization.json.JsonObject) }
                    ?.get("method")
                    ?.let { m -> (m as? kotlinx.serialization.json.JsonPrimitive)?.content }
            }.getOrNull()
        }
}
