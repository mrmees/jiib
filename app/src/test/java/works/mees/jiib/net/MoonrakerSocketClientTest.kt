package works.mees.jiib.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure config assertion (no socket, no network) that the shared OkHttp client carries a websocket
 * keepalive (G-B1a). This is the unit-level backstop for the on-device mid-print freeze the 13-04
 * live UAT caught: a half-open WiFi drop is silent unless OkHttp PINGs the peer, so `defaultClient()`
 * MUST set a non-zero `pingInterval` — otherwise `onFailure`/[SocketEvent.Closed] never fires and the
 * reconnect supervisor never runs (the feed freezes indefinitely).
 *
 * The unit suite previously missed this because [WebSocketFactory]'s `FakeWebSocket` synthesizes a
 * `Closed` on cancel while a real OkHttp socket never does without keepalive (the 4th mock-vs-reality
 * strike). Pinning `pingIntervalMillis > 0` here closes that gap at the config layer.
 */
class MoonrakerSocketClientTest {

    @Test
    fun defaultClient_hasNonZeroPingInterval() {
        val client = MoonrakerSocket.defaultClient()
        assertTrue(
            "defaultClient() must set a non-zero websocket keepalive (pingInterval) so a half-open " +
                "drop is detected; pingIntervalMillis was ${client.pingIntervalMillis}",
            client.pingIntervalMillis > 0,
        )
    }

    @Test
    fun defaultClient_pingIntervalMatchesTheDeclaredConstant() {
        val client = MoonrakerSocket.defaultClient()
        assertEquals(
            MoonrakerSocket.PING_INTERVAL_MS.toInt(),
            client.pingIntervalMillis,
        )
    }
}
