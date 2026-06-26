package works.mees.jiib.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import works.mees.jiib.net.ConnectionError
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.KlippyState
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import org.junit.Test

/**
 * Pure-derivation proof for the SINGLE top-level routing authority (review HIGH #2). Arm order is
 * load-bearing (Codex-reviewed, 13-05 Task 3; re-ordered 2026-06-15): first-run Connect wins, then a
 * REAL connection fault (socket RECONNECT — the deliberate D-05 departure, Matthew 2026-06-03) routes
 * the recovery Splash, then ONLY klippy Disconnected/Startup route Splash (host not up yet). All
 * host-side, no Compose, no I/O.
 *
 * NOTE: with a LIVE connection, [KlippyState.Error] and [KlippyState.Shutdown] now fall through to the
 * home Shell (the skeleton renders the fault — owner decision 2026-06-15), NOT a hard Splash override.
 * The happy-path cases below pin `connection = ConnectionState.Connected` explicitly (the default is
 * Disconnected, which routes Splash via the connection arm).
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
    fun klippyDisconnected_routesToSplash() {
        assertEquals(
            "klippyState=Disconnected must route to Splash",
            TopRoute.Splash,
            derive(cfgPresent = true, s = PrinterState(klippyState = KlippyState.Disconnected, connection = connected)),
        )
    }

    @Test
    fun klippyErrorOrShutdown_connectedAndConfig_routeToShell() {
        for (k in listOf(KlippyState.Error, KlippyState.Shutdown)) {
            assertEquals(
                "klippyState=$k with a live connection must reach the home Shell",
                TopRoute.Shell,
                derive(cfgPresent = true, s = PrinterState(klippyState = k, connection = connected)),
            )
        }
    }

    @Test
    fun klippyErrorOrShutdown_butDisconnected_routeToSplash() {
        for (k in listOf(KlippyState.Error, KlippyState.Shutdown)) {
            assertEquals(
                "klippyState=$k must still go to Splash when the socket is down",
                TopRoute.Splash,
                derive(cfgPresent = true, s = PrinterState(klippyState = k, connection = ConnectionState.Disconnected)),
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
