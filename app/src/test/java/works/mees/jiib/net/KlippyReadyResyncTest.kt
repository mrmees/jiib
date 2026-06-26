package works.mees.jiib.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterStateStore

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
            runCurrent()
            assertEquals(PrintState.Printing, store.printerState.value.printState)

            // --- The captured klippy-down window (FIRMWARE_RESTART / SAVE_CONFIG on the same socket) ---
            // The drop clears subscriptionActive: the subscription is gone, diffs no longer push. Use
            // runCurrent (NOT advanceUntilIdle): the drop arms a 30s escalate-watchdog and advancing
            // virtual time past it would fire a spurious reconnect before the ready lands the recovery.
            harness.injectKlippyDrop()
            runCurrent()

            // While DOWN, an injected diff is DROPPED by the gate inside inject() — it must NOT reach
            // the store. (Reset the probe to Standby first so we can detect a leak.)
            fake.inject(
                """{"jsonrpc":"2.0","method":"notify_status_update",""" +
                    """"params":[{"print_stats":{"state":"standby"}},124.0]}""",
            )
            runCurrent()
            // The drop-window diff was gated out, so the store still shows the last-known Printing.
            assertEquals(
                "a status diff injected while unsubscribed must be DROPPED by the inject() gate",
                PrintState.Printing,
                store.printerState.value.printState,
            )

            // klippy comes back: the session must re-run the handshake on the SAME socket and the
            // re-subscribe must SUCCEED (klippy is up), flipping subscriptionActive back TRUE. The
            // re-handshake's fake auto-replies synchronously, so runCurrent drains it and quiesces the
            // session (no permanently-failing harness here — klippyDown was never set).
            harness.injectKlippyReady()
            runCurrent()

            // The resumed-diff probe: a post-restart notify_status_update injected through the gated
            // inject(). It reaches the store ONLY if a real re-subscribe happened (gate reopened).
            fake.inject(printingStatusDiff)
            runCurrent()

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

            // Drive the captured restart sequence on the same socket (drop → ready). runCurrent (NOT
            // advanceUntilIdle) between drop and ready: the drop's 30s watchdog must NOT fire before the
            // ready lands the recovery. klippy comes back up (klippyDown unset), so the re-handshake's
            // synchronous auto-replies drain under runCurrent and the session quiesces.
            harness.injectKlippyDrop()
            runCurrent()
            harness.injectKlippyReady()
            runCurrent()

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
            // runCurrent (NOT advanceUntilIdle): the drop arms the 30s watchdog; advancing past it would
            // muddy WHICH lever (the ready-driven re-handshake failure vs the watchdog timeout) escalated.
            harness.klippyDown = true
            harness.injectKlippyDrop()
            runCurrent()

            // The restart-complete signal triggers the re-handshake, whose subscribe now fails (503). The
            // self-heal must close the socket (cancel()→onFailure→Closed) and the supervisor reconnects.
            // runCurrent drains the synchronous failure → escalateReconnect → close → Closed and lets the
            // supervisor enter its backoff wait. The reconnect itself is gated behind that backoff delay.
            harness.injectKlippyReady()
            runCurrent()

            // CLEAR the down window BEFORE advancing time, so the supervisor's backoff-then-reconnect
            // re-enters connectAndServe against a klippy that is now UP — the fresh socket's handshake
            // SUCCEEDS and the session QUIESCES (Connected) instead of looping a permanently-failing
            // reconnect. THIS is what makes the test terminate deterministically: without it,
            // advanceUntilIdle would chase an endless down-window reconnect loop and hang.
            harness.klippyDown = false

            // Advance PAST the supervisor's max backoff so the reconnect fires, then drain the fresh
            // handshake. advanceTimeBy + runCurrent (a BOUNDED step), never an open-ended advanceUntilIdle.
            // The deadman withTimeout makes a still-stuck session FAIL fast rather than hang the JVM.
            advanceTimeBy(60_000)
            runCurrent()
            withTimeout(5_000) { session.connectionState.first { it is ConnectionState.Connected } }

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
