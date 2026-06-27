package works.mees.jiib.ui.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.ConnectionError
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.KlippyState
import works.mees.jiib.state.PrinterState

/**
 * Unit coverage for [computeBrandMode] — the launch-only black/white brand-splash gate. The cold-start
 * defaults of [PrinterState] are Disconnected connection + Disconnected klippy, which the recovery
 * router maps to Unreachable; the brand gate must still light up during the initial connect (Codex spec
 * review #2) and must drop out on connect, on grace expiry, or on a hard fault.
 */
class BrandModeTest {
    private val cold = PrinterState()

    @Test fun `cold start, not yet connected, within grace, no fault -- brand shows`() {
        assertTrue(computeBrandMode(everConnected = false, graceExpired = false, state = cold))
    }

    @Test fun `connecting state within grace -- brand shows`() {
        assertTrue(
            computeBrandMode(false, false, cold.copy(connection = ConnectionState.Connecting))
        )
    }

    @Test fun `once connected -- brand never shows again`() {
        assertFalse(computeBrandMode(everConnected = true, graceExpired = false, state = cold))
    }

    @Test fun `grace expired -- brand stops, recovery takes over`() {
        assertFalse(computeBrandMode(everConnected = false, graceExpired = true, state = cold))
    }

    @Test fun `connection error -- hard fault, brand suppressed`() {
        val errored = cold.copy(connection = ConnectionState.Error(ConnectionError.NetworkUnavailable))
        assertFalse(computeBrandMode(false, false, errored))
    }

    @Test fun `klippy shutdown -- hard fault, brand suppressed`() {
        assertFalse(computeBrandMode(false, false, cold.copy(klippyState = KlippyState.Shutdown)))
    }

    @Test fun `klippy error -- hard fault, brand suppressed`() {
        assertFalse(computeBrandMode(false, false, cold.copy(klippyState = KlippyState.Error)))
    }
}
