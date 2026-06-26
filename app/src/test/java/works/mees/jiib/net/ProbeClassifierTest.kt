package works.mees.jiib.net

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Test

class ProbeClassifierTest {
    @Test fun unauthorizedFromStatus() =
        assertEquals(ProbeFailure.Unauthorized, classifyProbeFailure(null, 401))
    @Test fun timeout() =
        assertEquals(ProbeFailure.Timeout, classifyProbeFailure(SocketTimeoutException(), null))
    @Test fun coroutineTimeout() = runTest {
        val timeout = try {
            withTimeout(1) { delay(Long.MAX_VALUE) }
            throw AssertionError("expected timeout")
        } catch (e: TimeoutCancellationException) {
            e
        }
        assertEquals(ProbeFailure.Timeout, classifyProbeFailure(timeout, null))
    }
    @Test fun refused() =
        assertEquals(ProbeFailure.Refused, classifyProbeFailure(ConnectException("Connection refused"), null))
    @Test fun cert() =
        assertEquals(ProbeFailure.Certificate, classifyProbeFailure(SSLHandshakeException("bad cert"), null))
    @Test fun identifyUnauthorizedRpcError() =
        assertEquals(ProbeFailure.Unauthorized, classifyProbeFailure(RpcError(-32602, "Unauthorized"), null))
    @Test fun unknown() =
        assertEquals(ProbeFailure.Unknown, classifyProbeFailure(RuntimeException("?"), null))
}
