package works.mees.dinghy.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import works.mees.dinghy.net.ConnectionError
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.KlippyState
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import org.junit.Test

/**
 * Pure-derivation proof for the SINGLE top-level routing authority (review HIGH #2). Arm order is
 * load-bearing (Codex-reviewed, 13-05 Task 3): first-run Connect wins, then the klippy gate, then —
 * NEW in 13-05 — a socket RECONNECT routes the recovery Splash too (the deliberate D-05 departure,
 * Matthew 2026-06-03; safe because Task 2 hoisted the shell nav state so the splash can't bounce the
 * user). All host-side, no Compose, no I/O.
 *
 * NOTE: a "Ready Shell" requires BOTH klippy Ready AND connection Connected — so the happy-path cases
 * below pin `connection = ConnectionState.Connected` explicitly (the default is Disconnected, which now
 * routes Splash).
 */
class TopRouteTest {

    private val connected = ConnectionState.Connected

    @Test
    fun noConfig_routesToConnect_regardlessOfState() {
        assertEquals(TopRoute.Connect, derive(cfgPresent = false, s = PrinterState()))
        assertEquals(
            TopRoute.Connect,
            derive(
                cfgPresent = false,
                s = PrinterState(
                    klippyState = KlippyState.Ready,
                    printState = PrintState.Printing,
                    connection = connected,
                ),
            ),
        )
    }

    @Test
    fun klippyStartup_routesToSplash() {
        assertEquals(
            TopRoute.Splash,
            derive(
                cfgPresent = true,
                s = PrinterState(klippyState = KlippyState.Startup, connection = connected),
            ),
        )
    }

    @Test
    fun klippyErrorShutdownDisconnected_routeToSplash() {
        for (k in listOf(KlippyState.Error, KlippyState.Shutdown, KlippyState.Disconnected)) {
            assertEquals(
                "klippyState=$k must route to Splash",
                TopRoute.Splash,
                derive(cfgPresent = true, s = PrinterState(klippyState = k, connection = connected)),
            )
        }
    }

    @Test
    fun klippyReadyPrinting_andConnected_routesToShell() {
        assertEquals(
            TopRoute.Shell,
            derive(
                cfgPresent = true,
                s = PrinterState(
                    klippyState = KlippyState.Ready,
                    printState = PrintState.Printing,
                    connection = connected,
                ),
            ),
        )
    }

    @Test
    fun klippyReadyIdle_andConnected_routesToShell() {
        assertEquals(
            TopRoute.Shell,
            derive(
                cfgPresent = true,
                s = PrinterState(
                    klippyState = KlippyState.Ready,
                    printState = PrintState.Standby,
                    connection = connected,
                ),
            ),
        )
    }

    @Test
    fun filesIsAKnownNavDest() {
        assertTrue(NavDest.Files in knownNavDests)
    }

    /**
     * The 13-05 G-B1b departure: with config present and klippy Ready, a socket that is NOT Connected
     * (mid-reconnect: Connecting/Syncing/Disconnected/Error) now routes the full recovery Splash. This
     * is the load-bearing new arm — a silent mid-print WiFi drop is no longer invisible.
     */
    @Test
    fun socketReconnecting_withConfigAndKlippyReady_routesToSplash() {
        val base = PrinterState(klippyState = KlippyState.Ready, printState = PrintState.Printing)
        val reconnectingStates = listOf(
            ConnectionState.Connecting,
            ConnectionState.Syncing,
            ConnectionState.Disconnected,
            ConnectionState.Error(ConnectionError.NetworkUnavailable),
        )
        for (c in reconnectingStates) {
            assertEquals(
                "connection=$c (not Connected) must route to the recovery Splash",
                TopRoute.Splash,
                derive(cfgPresent = true, s = base.copy(connection = c)),
            )
        }
    }

    /**
     * Arm ORDER proof: first-run Connect WINS even when the socket is also reconnecting (Connect must
     * not be trapped behind the new Syncing-splash arm).
     */
    @Test
    fun noConfig_winsOverSocketReconnect() {
        assertEquals(
            TopRoute.Connect,
            derive(
                cfgPresent = false,
                s = PrinterState(klippyState = KlippyState.Ready, connection = ConnectionState.Syncing),
            ),
        )
    }
}
