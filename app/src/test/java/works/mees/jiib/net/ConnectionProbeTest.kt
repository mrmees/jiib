package works.mees.jiib.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import works.mees.jiib.config.ConnectionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConnectionProbeTest {
    @Test fun runsBothLegsAndDerivesUrls() = runTest {
        val probe = ConnectionProbe(
            httpLeg = { _, _ -> TransportResult(ok = true) },
            wsLeg = { _, _ -> TransportResult(ok = false, failure = ProbeFailure.Unauthorized) },
        )
        val r = probe.probe(ConnectionConfig(host = "10.0.0.5", port = 7125))
        assertEquals("http://10.0.0.5:7125", r.httpUrl)
        assertEquals("ws://10.0.0.5:7125/websocket", r.wsUrl)
        assertTrue(r.http.ok)
        assertFalse(r.ws.ok)
        assertEquals(ProbeFailure.Unauthorized, r.ws.failure)
    }

    @Test fun aThrowingLegBecomesClassifiedFailure() = runTest {
        val probe = ConnectionProbe(
            httpLeg = { _, _ -> throw java.net.ConnectException("Connection refused") },
            wsLeg = { _, _ -> TransportResult(ok = true) },
        )
        val r = probe.probe(ConnectionConfig(host = "h", port = 7125))
        assertFalse(r.http.ok)
        assertEquals(ProbeFailure.Refused, r.http.failure)
    }

    @Test fun timeoutBecomesClassifiedFailure() = runTest {
        val probe = ConnectionProbe(
            httpLeg = { _, _ ->
                delay(Long.MAX_VALUE)
                TransportResult(ok = true)
            },
            wsLeg = { _, _ -> TransportResult(ok = true) },
            perLegTimeoutMs = 1,
        )
        val r = probe.probe(ConnectionConfig(host = "h", port = 7125))
        assertFalse(r.http.ok)
        assertEquals(ProbeFailure.Timeout, r.http.failure)
    }

    @Test fun nonTimeoutCancellationRethrows() = runTest {
        val probe = ConnectionProbe(
            httpLeg = { _, _ -> throw CancellationException("caller cancelled") },
            wsLeg = { _, _ -> TransportResult(ok = true) },
        )
        try {
            probe.probe(ConnectionConfig(host = "h", port = 7125))
            fail("probe() must rethrow non-timeout CancellationException")
        } catch (e: CancellationException) {
            assertEquals("caller cancelled", e.message)
        }
    }
}
