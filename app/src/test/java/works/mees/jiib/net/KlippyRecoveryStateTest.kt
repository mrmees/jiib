package works.mees.jiib.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.PrinterStateStore

/**
 * D-03 regression (NEW): when Klipper restarts on the still-open socket (captured
 * `notify_klippy_disconnected` → `notify_klippy_ready`), the re-handshake must surface a
 * `ConnectionState.Syncing` THEN `ConnectionState.Connected` transition — exactly as the INITIAL
 * `connectAndServe` does (emit(Syncing) at socket-open, emit(Connected) after the subscribe seed) — so
 * the shell/notification dims to "Syncing…" while the subscription is being rebuilt and returns to
 * Connected when it lands. The current re-handshake is a SILENT `runCatching { runHandshake() }` that
 * emits NEITHER (the D-03 defect): the user sees a frozen-but-"Connected" screen during the rebuild.
 *
 * False-pass guard: the assertion is made on a FRESH connection-state collector started AFTER the initial
 * connect's own Connecting→Syncing→Connected sequence has already completed (we await the initial
 * `first { Connected }`, THEN begin recording). So the initial connect's transitions cannot satisfy the
 * assertion — only the RECOVERY emitting Syncing→Connected can.
 *
 * WAVE-0 COLOR: RED against current production (the re-handshake emits no connection-state transitions).
 * Wave 1 / plan 13-02 wraps the re-handshake with emit(Syncing) → … → emit(Connected) to turn it GREEN.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KlippyRecoveryStateTest {

    @Test
    fun klippyRestart_emitsSyncingThenConnected_onAfreshCollectorPastInitialConnect() =
        runTest(UnconfinedTestDispatcher()) {
            val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
            val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
            val harness = SessionTestHarness()
            val session = MoonrakerSession(store, rpc, harness.socketEvents())

            val run = launch { session.run() }

            // 1. Await the INITIAL connect completing. Everything up to and incl. this Connected is the
            //    initial-connect sequence and must NOT count toward the recovery assertion.
            session.connectionState.first { it is ConnectionState.Connected }

            // 2. Start a FRESH collector NOW (after the initial Connected), recording only what follows.
            val recovery = mutableListOf<ConnectionState>()
            val collector = launch {
                session.connectionState.collect { recovery += it }
            }
            runCurrent()
            // Drop the StateFlow's replayed current value (the initial Connected) so only post-restart
            // transitions remain — the initial connect can no longer satisfy the assertion.
            recovery.clear()

            // 3. Drive the captured klippy restart on the SAME live socket (drop → ready). Use
            //    runCurrent() (NOT advanceUntilIdle) between drop and ready: the drop arms a 30s
            //    bounded-escalation watchdog (RECOVERY_WINDOW), and advanceUntilIdle would advance
            //    virtual time PAST it — firing a spurious escalate-reconnect before the ready can land
            //    the recovery. runCurrent only drains tasks due NOW, leaving the watchdog pending so the
            //    ready cancels it. This recovery is the happy path (klippy up), so the re-handshake
            //    succeeds and the session quiesces — the bounded withTimeout below is a deadman, not a
            //    real wait.
            harness.injectKlippyDrop()
            runCurrent()
            harness.injectKlippyReady()
            runCurrent()
            withTimeout(5_000) { session.connectionState.first { it is ConnectionState.Connected } }

            // 4. The RECOVERY must emit Syncing, THEN Connected — in that order — on the fresh collector.
            val syncingIdx = recovery.indexOfFirst { it is ConnectionState.Syncing }
            val connectedIdx = recovery.indexOfLast { it is ConnectionState.Connected }
            assertTrue(
                "re-handshake must emit ConnectionState.Syncing (D-03) — observed sequence = $recovery",
                syncingIdx >= 0,
            )
            assertTrue(
                "re-handshake must emit ConnectionState.Connected AFTER Syncing (D-03) — observed = $recovery",
                connectedIdx > syncingIdx,
            )

            collector.cancelAndJoin()
            run.cancelAndJoin()
        }
}
