package works.mees.jiib.net

import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RpcConnection lifecycle contract (review HIGH #4): send is bound to the live socket.
 *
 * - A connection can only be obtained over a live `okhttp3.WebSocket` (here a [FakeWebSocket]) —
 *   there is no public no-arg constructor, so a "pre-open" send is unrepresentable.
 * - send after close() throws a clean typed exception (no NPE, no silent send).
 */
class RpcConnectionTest {

    /** A bare FakeWebSocket whose listener does nothing — we only need the WebSocket surface. */
    private fun fakeSocket(): FakeWebSocket =
        FakeWebSocket(object : WebSocketListener() {})

    @Test
    fun `send forwards to the live socket while open`() {
        val socket = fakeSocket()
        val conn = RpcConnection(socket)

        assertTrue(conn.isOpen)
        conn.send("""{"id":1}""")

        assertEquals(listOf("""{"id":1}"""), socket.sentFrames)
    }

    @Test
    fun `send after close throws cleanly and does not reach the socket`() {
        val socket = fakeSocket()
        val conn = RpcConnection(socket)

        conn.close(ConnectionError.NetworkUnavailable)
        assertFalse(conn.isOpen)

        val ex = assertThrows(IllegalStateException::class.java) {
            conn.send("""{"id":2}""")
        }
        assertTrue(ex.message!!.contains("after close"))
        // The frame must NOT have been sent (no silent send).
        assertTrue(socket.sentFrames.isEmpty())
    }

    @Test
    fun `close cancels the underlying socket and is idempotent`() {
        val socket = fakeSocket()
        val conn = RpcConnection(socket)

        conn.close()
        assertTrue("close() must cancel the underlying socket (no leak)", socket.closed)

        // Second close is a no-op (does not throw).
        conn.close(ConnectionError.NetworkUnavailable)
        assertFalse(conn.isOpen)
    }
}
