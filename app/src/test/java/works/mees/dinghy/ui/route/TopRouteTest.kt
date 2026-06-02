package works.mees.dinghy.ui.route

import org.junit.Assert.assertEquals
import works.mees.dinghy.net.ConnectionError
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.KlippyState
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import org.junit.Test

/**
 * Pure-derivation proof for the SINGLE top-level routing authority (review HIGH #2). Routing keys
 * off the Klippy/print lifecycle ONLY (D-05/D-06); the socket [ConnectionState] is chrome and must
 * NEVER move the route — the load-bearing `socketStateDoesNotRoute` case proves a transient reconnect
 * can't bounce the user off the home screen. All host-side, no Compose, no I/O.
 */
class TopRouteTest {

    @Test
    fun noConfig_routesToConnect_regardlessOfState() {
        assertEquals(TopRoute.Connect, derive(cfgPresent = false, s = PrinterState()))
        assertEquals(
            TopRoute.Connect,
            derive(
                cfgPresent = false,
                s = PrinterState(klippyState = KlippyState.Ready, printState = PrintState.Printing),
            ),
        )
    }

    @Test
    fun klippyStartup_routesToSplash() {
        assertEquals(
            TopRoute.Splash,
            derive(cfgPresent = true, s = PrinterState(klippyState = KlippyState.Startup)),
        )
    }

    @Test
    fun klippyErrorShutdownDisconnected_routeToSplash() {
        for (k in listOf(KlippyState.Error, KlippyState.Shutdown, KlippyState.Disconnected)) {
            assertEquals(
                "klippyState=$k must route to Splash",
                TopRoute.Splash,
                derive(cfgPresent = true, s = PrinterState(klippyState = k)),
            )
        }
    }

    @Test
    fun klippyReadyPrinting_routesToShellPrintStatus() {
        assertEquals(
            TopRoute.Shell(Dest.PrintStatus),
            derive(
                cfgPresent = true,
                s = PrinterState(klippyState = KlippyState.Ready, printState = PrintState.Printing),
            ),
        )
    }

    @Test
    fun klippyReadyIdle_routesToSameShellPrintStatusSurface() {
        assertEquals(
            TopRoute.Shell(Dest.PrintStatus),
            derive(
                cfgPresent = true,
                s = PrinterState(klippyState = KlippyState.Ready, printState = PrintState.Standby),
            ),
        )
    }

    @Test
    fun filesIsAShellDestination() {
        assertEquals(Dest.Files, Dest.valueOf("Files"))
    }

    @Test
    fun socketStateDoesNotRoute_changingOnlyConnectionKeepsRouteIdentical() {
        val base = PrinterState(klippyState = KlippyState.Ready, printState = PrintState.Standby)
        val connected = base.copy(connection = ConnectionState.Connected)
        val reconnecting = base.copy(connection = ConnectionState.Connecting)
        val errored = base.copy(connection = ConnectionState.Error(ConnectionError.NetworkUnavailable))

        val r = derive(cfgPresent = true, s = connected)
        assertEquals(r, derive(cfgPresent = true, s = reconnecting))
        assertEquals(r, derive(cfgPresent = true, s = errored))
        assertEquals("a socket flap must not bounce off PrintStatus", TopRoute.Shell(Dest.PrintStatus), r)
    }
}
