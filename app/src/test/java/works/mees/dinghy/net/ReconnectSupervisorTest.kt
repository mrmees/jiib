package works.mees.dinghy.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.auth.MoonrakerAuth
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrinterStateStore
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * The reconnect supervisor (D-01/D-02, review MEDIUM gentle auth): uncapped backoff grows and retries
 * forever for NETWORK failures; [MoonrakerSession.requestReconnectNow] cancels the pending backoff delay
 * mid-wait; an AuthRequired result enters a quiescent state that does NOT churn token-fetches and resumes
 * only on requestReconnectNow().
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectSupervisorTest {

    private fun store(scope: kotlinx.coroutines.CoroutineScope) =
        PrinterStateStore(scope = scope, sampleMillis = 250L)

    @Test
    fun networkFailure_retriesForever_withBackoff() = runTest(UnconfinedTestDispatcher()) {
        val store = store(backgroundScope)
        val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
        val harness = SessionTestHarness()
        val session = MoonrakerSession(
            store, rpc, harness.socketEvents(),
            backoffBase = 100.milliseconds,
            rng = Random(1),
        )

        // Every freshly opened socket immediately FAILS (network-down simulation).
        harness.failOnOpen = true

        val run = launch { session.run() }
        // Let several attempts elapse across backoff windows.
        advanceTimeBy(5_000L)
        runCurrent()

        assertTrue("supervisor must keep retrying (multiple socket opens)", harness.opens >= 3)
        run.cancelAndJoin()
    }

    @Test
    fun requestReconnectNow_cancelsPendingBackoff() = runTest(UnconfinedTestDispatcher()) {
        val store = store(backgroundScope)
        val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
        val harness = SessionTestHarness()
        val session = MoonrakerSession(
            store, rpc, harness.socketEvents(),
            backoffBase = 10_000.milliseconds, // a LONG backoff so the immediate trigger is unambiguous
            rng = Random(1),
        )
        harness.failOnOpen = true

        val run = launch { session.run() }
        advanceTimeBy(50L); runCurrent()
        val opensBefore = harness.opens
        // We are now parked in a long backoff. Firing reconnectNow must trigger another attempt without
        // waiting out the 10 s delay.
        session.requestReconnectNow()
        advanceTimeBy(50L); runCurrent()
        assertTrue("requestReconnectNow must fire an attempt before the long backoff elapses",
            harness.opens > opensBefore)

        run.cancelAndJoin()
    }

    /**
     * D-07b (13-01, SC-2) regression LOCK: a mid-print socket DEATH (onClosing/onFailure) must drive a
     * full reconnect, and after the reconnect's handshake completes the live diff stream must resync the
     * running print back to Printing. The socket-death → reconnect path is already correct (RESEARCH §
     * Current reconnect behavior); this test LOCKS it so a future change can't silently break it. It
     * passes against current production — it is the regression lock, not a RED fix-driver.
     */
    @Test
    fun midPrintSocketDeath_reconnects_andResyncsPrintStateToPrinting() = runTest(UnconfinedTestDispatcher()) {
        val store = store(backgroundScope)
        val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
        val harness = SessionTestHarness()
        val session = MoonrakerSession(
            store, rpc, harness.socketEvents(),
            backoffBase = 10.milliseconds,
            rng = Random(1),
        )

        val run = launch { session.run() }
        session.connectionState.first { it is ConnectionState.Connected }
        val opensBefore = harness.opens

        // A print is running on the live socket (diff passes the now-open subscription gate).
        harness.current.get()!!.inject(
            """{"jsonrpc":"2.0","method":"notify_status_update",""" +
                """"params":[{"print_stats":{"state":"printing"}},123.0]}""",
        )
        advanceUntilIdle()
        assertEquals(
            works.mees.dinghy.state.PrintState.Printing,
            store.printerState.value.printState,
        )

        // The socket dies mid-print (server close / transport drop).
        harness.current.get()!!.driveClosing()
        advanceUntilIdle()

        // The supervisor reconnects (a new socket opened) and re-runs the handshake → Connected.
        session.connectionState.first { it is ConnectionState.Connected }
        assertTrue("a mid-print socket death must trigger a reconnect", harness.opens > opensBefore)

        // The running print resyncs over the new, live subscription.
        harness.current.get()!!.inject(
            """{"jsonrpc":"2.0","method":"notify_status_update",""" +
                """"params":[{"print_stats":{"state":"printing"}},124.0]}""",
        )
        advanceUntilIdle()
        assertEquals(
            "after reconnect the live diff stream must resync printState to Printing (D-07b)",
            works.mees.dinghy.state.PrintState.Printing,
            store.printerState.value.printState,
        )

        run.cancelAndJoin()
    }

    @Test
    fun authRequired_quiesces_doesNotChurn_thenResumesOnReconnectNow() = runTest(UnconfinedTestDispatcher()) {
        val store = store(backgroundScope)
        val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
        val harness = SessionTestHarness()
        // Identify replies with -32602 Unauthorized → AuthRequired.
        harness.identifyErrorFrame = GoldenFixtures.frames("adversarial_auth_identify_error.json").first()

        val session = MoonrakerSession(
            store, rpc, harness.socketEvents(),
            backoffBase = 50.milliseconds,
            rng = Random(1),
        )

        val run = launch { session.run() }

        session.connectionState.first { it is ConnectionState.Error }
        assertEquals(
            ConnectionState.Error(ConnectionError.AuthRequired),
            session.connectionState.value,
        )

        val opensAtQuiesce = harness.opens
        // Quiescent: even after a long time, NO new connect attempts (no churn against a bad key).
        advanceTimeBy(10_000L); runCurrent()
        assertEquals("AuthRequired must NOT churn reconnects", opensAtQuiesce, harness.opens)

        // Now fix auth and resume on demand → a new attempt is made (which now succeeds).
        harness.identifyErrorFrame = null
        session.requestReconnectNow()
        advanceUntilIdle()
        session.connectionState.first { it is ConnectionState.Connected }
        assertTrue("requestReconnectNow resumes from quiescence", harness.opens > opensAtQuiesce)

        run.cancelAndJoin()
    }
}
