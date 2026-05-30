package works.mees.dinghy.connection

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import works.mees.dinghy.net.JsonRpcClient
import works.mees.dinghy.net.MoonrakerSession
import works.mees.dinghy.net.MoonrakerSocket
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrinterStateStore
import java.util.concurrent.TimeUnit

/**
 * On-device live proof of the REAL spine (phase gate, D-06 no-auth path). NOT a reuse/edit of the
 * throwaway [works.mees.dinghy.smoke.CleartextMoonrakerSmokeTest] — only its harness shape is mirrored
 * (AndroidJUnit4, @RunWith, instrumentation-arg host/port with placeholder defaults).
 *
 * Drives the actual [MoonrakerSession] + [PrinterStateStore] against the live Ender 5 Plus over cleartext:
 * connects, runs the resync handshake (identify → objects.list → query → subscribe), asserts a live
 * `notify_status_update` updates [PrinterStateStore.printerState], and asserts [ConnectionState] reaches
 * [ConnectionState.Connected] ONLY after [ConnectionState.Syncing] (review HIGH #3 — resync complete).
 *
 * Host/port come from instrumentation args so no printer IP is baked into the committed test:
 *   connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.moonrakerHost=<live-ip> \
 *     -Pandroid.testInstrumentationRunnerArguments.moonrakerPort=7125
 */
@RunWith(AndroidJUnit4::class)
class LiveSocketReconnectTest {

    private companion object {
        const val DEFAULT_HOST = "192.168.1.50" // PLACEHOLDER — overridden via the moonrakerHost arg.
        const val DEFAULT_PORT = "7125"
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val UPDATE_TIMEOUT_MS = 20_000L
    }

    private fun arg(key: String, default: String): String =
        InstrumentationRegistry.getArguments().getString(key) ?: default

    private val host: String get() = arg("moonrakerHost", DEFAULT_HOST)
    private val port: String get() = arg("moonrakerPort", DEFAULT_PORT)
    private val wsUrl: String get() = "ws://$host:$port/websocket"

    @Test
    fun liveConnect_completesResyncHandshake_andReachesConnectedOnlyAfterSyncing() = runBlocking {
        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // websocket: no read timeout
            .build()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = PrinterStateStore(scope = scope)
        val rpc = JsonRpcClient()
        val session = MoonrakerSession(
            store = store,
            rpc = rpc,
            // No-auth path (D-06): open the real socket directly, no token.
            socketEvents = { _ -> MoonrakerSocket.real(client = client, wsUrl = wsUrl).events() },
        )

        // Observe the connection-state sequence with ONE collector (NOT two racing atomics): record the
        // ordered transitions and assert Syncing precedes Connected by index. Started UNDISPATCHED so the
        // collector is subscribed before run() drives the first transition. The previous two-collector +
        // atomic scheme was racy — the main coroutine's own `first { Connected }` could return and read
        // the atomics before this observer coroutine had processed the Connected emission, failing
        // "Connected must follow Syncing" even though the spine emits Syncing → Connected correctly.
        val observed = java.util.concurrent.CopyOnWriteArrayList<ConnectionState>()
        val observer = scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            session.connectionState.collect { observed.add(it) }
        }

        val run = scope.launch { session.run() }
        try {
            // 1. Reach Connected (only emitted after the subscribe seed lands — review HIGH #3).
            withTimeout(CONNECT_TIMEOUT_MS) {
                session.connectionState.first { it is ConnectionState.Connected }
            }
            // Let the recording collector catch up to the Connected emission (avoid a cross-coroutine
            // read race), then assert ordering on the SINGLE recorded sequence.
            withTimeout(2_000L) {
                while (observed.none { it is ConnectionState.Connected }) delay(10)
            }
            val firstSyncing = observed.indexOfFirst { it is ConnectionState.Syncing }
            val firstConnected = observed.indexOfFirst { it is ConnectionState.Connected }
            assertTrue("Syncing must be observed before Connected (resync gating)", firstSyncing >= 0)
            assertTrue("Connected must follow Syncing (resync gating)", firstConnected > firstSyncing)

            // 2. The query snapshot must have seeded state (a bed temperature is present on the Ender 5 Plus).
            val seeded = store.printerState.value
            assertTrue(
                "objects.query snapshot must seed PrinterState (heaters non-empty)",
                seeded.heaters.isNotEmpty(),
            )

            // 3. A live notify_status_update must update PrinterState (subscribe forces an immediate
            //    snapshot; an idle printer still pushes the subscribed object). Assert the state is fresh
            //    (not stale) while connected.
            withTimeout(UPDATE_TIMEOUT_MS) {
                store.printerState.first { it.connection is ConnectionState.Connected && !it.stale }
            }
            assertTrue("connected state must not be stale", !store.printerState.value.stale)
        } finally {
            observer.cancel()
            run.cancel()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            client.dispatcher.executorService.shutdown()
        }
    }
}
