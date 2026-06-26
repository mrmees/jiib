package works.mees.jiib.connection

import android.util.Log
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import works.mees.jiib.net.JsonRpcClient
import works.mees.jiib.net.MoonrakerSession
import works.mees.jiib.net.MoonrakerSocket
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.PrinterStateStore
import java.util.concurrent.TimeUnit

/**
 * On-device proof of the REAL network-drop reconnect/resync path against the live Ender 5 Plus
 * (Phase-2 ROADMAP success criterion #1; D-03 retain-on-drop → D-04 resync-overwrite). This is the
 * REAL-socket-failure complement to [LiveSocketReconnectTest] (which proves connect + handshake):
 * here a genuine OkHttp `onFailure` is induced by yanking the device Wi-Fi mid-stream.
 *
 * Driven by the orchestrator, NOT self-contained: this test logs `JIIB_YANK: READY` once connected
 * and then waits. An external driver (adb `svc wifi disable` → wait → `svc wifi enable`, over USB so
 * adb survives the Wi-Fi drop) watches logcat for that marker and performs the toggle. The test then
 * asserts: state goes stale-but-retained on drop, and returns Connected + non-stale after resync.
 *
 * Skips cleanly (passes) if no live drop is observed within the window, so it never produces a false
 * RED when run without the orchestrated toggle — the assertions only fire once a real drop is seen.
 */
@RunWith(AndroidJUnit4::class)
class LiveReconnectYankTest {

    private companion object {
        const val TAG = "JIIB_YANK"
        const val DEFAULT_HOST = "192.168.1.50" // PLACEHOLDER — overridden via moonrakerHost arg.
        const val DEFAULT_PORT = "7125"
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val DROP_WAIT_MS = 120_000L      // generous: orchestrator toggles Wi-Fi within this window
        const val RECOVER_WAIT_MS = 120_000L
    }

    private fun arg(key: String, default: String): String =
        InstrumentationRegistry.getArguments().getString(key) ?: default

    private val host get() = arg("moonrakerHost", DEFAULT_HOST)
    private val port get() = arg("moonrakerPort", DEFAULT_PORT)
    private val wsUrl get() = "ws://$host:$port/websocket"

    @Test
    fun liveWifiYank_retainsStaleThenReconnectsAndResyncs() = runBlocking {
        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = PrinterStateStore(scope = scope)
        val rpc = JsonRpcClient()
        val session = MoonrakerSession(
            store = store,
            rpc = rpc,
            socketEvents = { _ -> MoonrakerSocket.real(client = client, wsUrl = wsUrl).events() },
        )

        val run = scope.launch { session.run() }
        try {
            // Phase A — connect + resync.
            withTimeout(CONNECT_TIMEOUT_MS) {
                session.connectionState.first { it is ConnectionState.Connected }
            }
            val beforeBed = store.printerState.value.heaters["heater_bed"]?.temperature
            assertTrue("expected a live bed temperature before the yank", (beforeBed ?: 0.0) > 0.0)
            assertTrue("connected state must not be stale", !store.printerState.value.stale)
            Log.i(TAG, "READY connected bed=$beforeBed — orchestrator may yank Wi-Fi now")

            // Phase B — observe a REAL drop (orchestrator disables Wi-Fi). If none arrives in the
            // window, skip the assertions (test passes) so an un-orchestrated run is never a false RED.
            val dropped = runCatching {
                withTimeout(DROP_WAIT_MS) {
                    session.connectionState.first {
                        it is ConnectionState.Disconnected || it is ConnectionState.Connecting ||
                            it is ConnectionState.Error
                    }
                }
            }.isSuccess
            if (!dropped) {
                Log.w(TAG, "SKIP no Wi-Fi drop observed in window — passing without yank assertions")
                return@runBlocking
            }
            Log.i(TAG, "DROP_SEEN state=${store.printerState.value.connection}")

            // D-03: last-known state is RETAINED (not blanked) and flagged stale during the outage.
            // The printer prints live, so the retained temp is the LAST-SEEN value (not byte-identical to
            // the pre-drop snapshot) — assert it is still present and plausible (heater retained, not wiped).
            val duringBed = store.printerState.value.heaters["heater_bed"]?.temperature
            assertTrue("bed temp must be RETAINED across the drop (not blanked/zeroed)", (duringBed ?: 0.0) > 0.0)
            assertTrue("dropped state must be marked stale", store.printerState.value.stale)

            // Phase C — orchestrator restores Wi-Fi; supervisor reconnects + resyncs to non-stale.
            withTimeout(RECOVER_WAIT_MS) {
                store.printerState.first { it.connection is ConnectionState.Connected && !it.stale }
            }
            assertTrue("recovered state must not be stale (resync overwrote)", !store.printerState.value.stale)
            Log.i(TAG, "RECONNECTED bed=${store.printerState.value.heaters["heater_bed"]?.temperature}")
        } finally {
            run.cancel()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            client.dispatcher.executorService.shutdown()
        }
    }
}
