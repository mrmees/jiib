package works.mees.jiib.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.PrinterStateStore

/**
 * CONN-06 + review HIGH #3: the five-state ConnectionState is always observable; Connecting → Syncing →
 * Connected is the connect sequence and Connected is NEVER emitted before the subscribe seed lands; a
 * drop retains state + sets the stale marker (D-03), never blanks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionStateTest {

    @Test
    fun connectSequence_reachesConnectedOnlyAfterSyncing() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
        val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
        val harness = SessionTestHarness()
        val session = MoonrakerSession(store, rpc, harness.socketEvents())

        val observed = mutableListOf<ConnectionState>()
        val watcher = launch { session.connectionState.toList(observed) }

        val run = launch { session.run() }
        session.connectionState.first { it is ConnectionState.Connected }
        runCurrent()

        // The sequence must include Syncing BEFORE Connected, and Connecting before Syncing.
        val idxConnecting = observed.indexOfFirst { it is ConnectionState.Connecting }
        val idxSyncing = observed.indexOfFirst { it is ConnectionState.Syncing }
        val idxConnected = observed.indexOfFirst { it is ConnectionState.Connected }
        assertTrue("Connecting observed", idxConnecting >= 0)
        assertTrue("Syncing observed", idxSyncing >= 0)
        assertTrue("Connected observed", idxConnected >= 0)
        assertTrue("Connecting precedes Syncing", idxConnecting < idxSyncing)
        assertTrue("Syncing precedes Connected (review HIGH #3)", idxSyncing < idxConnected)

        watcher.cancel()
        run.cancelAndJoin()
    }

    @Test
    fun socketDrop_retainsState_setsStale_notBlank() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
        val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
        val harness = SessionTestHarness()
        val session = MoonrakerSession(store, rpc, harness.socketEvents())

        val run = launch { session.run() }
        session.connectionState.first { it is ConnectionState.Connected }

        // Seeded snapshot temp present.
        val tempBeforeDrop = store.printerState.value.heaters["heater_bed"]?.temperature
        assertEquals(23.8, tempBeforeDrop)

        // Yank the socket.
        harness.current.get()!!.simulateFailure(java.io.IOException("wifi yank"))
        session.connectionState.first { it is ConnectionState.Disconnected }
        runCurrent()

        // State retained (NOT blanked) + stale marker set (D-03).
        assertEquals("temp retained after drop", 23.8, store.printerState.value.heaters["heater_bed"]?.temperature)
        assertTrue("stale marker set on drop", store.printerState.value.stale)

        run.cancelAndJoin()
    }
}
