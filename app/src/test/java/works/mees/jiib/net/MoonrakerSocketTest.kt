package works.mees.jiib.net

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * MoonrakerSocket bridge proof (02-RESEARCH § Pattern 1) against the injected [FakeWebSocket]:
 * - the cold callbackFlow emits Open(connection) then Frame(text) for replayed golden frames,
 * - the emitted connection's send is captured by the fake (sends go through the live socket),
 * - cancelling the collecting scope runs awaitClose, invalidating the connection and cancelling the
 *   fake socket (no-leak proof).
 *
 * Turbine is intentionally avoided to keep the test-dep surface minimal; collection is driven
 * manually so the FakeWebSocket can be driven synchronously between assertions.
 */
class MoonrakerSocketTest {

    private val request = Request.Builder().url("http://localhost/websocket").build()

    /** A factory seam that captures the listener callbackFlow installs and returns a FakeWebSocket. */
    private class CapturingFactory : WebSocketFactory {
        val fake = AtomicReference<FakeWebSocket?>(null)
        override fun open(
            request: Request,
            listener: okhttp3.WebSocketListener,
        ): okhttp3.WebSocket {
            val f = FakeWebSocket(listener, request)
            fake.set(f)
            return f
        }
    }

    @Test
    fun `open then frame emit through the injected fake`() = runTest(UnconfinedTestDispatcher()) {
        val factory = CapturingFactory()
        val socket = MoonrakerSocket(factory, request)

        val events = mutableListOf<SocketEvent>()
        val job = socket.events().onEach { events += it }.launchIn(backgroundScope)

        // Let the flow install the listener + call factory.open.
        testScheduler.advanceUntilIdle()
        val fake = requireNotNull(factory.fake.get()) { "factory.open was never invoked" }

        // Drive onOpen, then a golden frame.
        fake.open()
        val frame = """{"jsonrpc":"2.0","result":{"eventtime":1.0},"id":7}"""
        fake.replay(listOf(frame))
        testScheduler.advanceUntilIdle()

        assertTrue("first event must be Open", events[0] is SocketEvent.Open)
        val open = events[0] as SocketEvent.Open
        assertTrue(open.connection.isOpen)
        assertEquals(SocketEvent.Frame(frame), events[1])

        // send via the emitted connection is captured by the fake (goes through the live socket).
        open.connection.send("""{"id":8,"method":"ping"}""")
        assertEquals(listOf("""{"id":8,"method":"ping"}"""), fake.sentFrames)

        job.cancel()
    }

    @Test
    fun `cancelling the scope runs awaitClose and invalidates the connection`() = runTest(UnconfinedTestDispatcher()) {
        val factory = CapturingFactory()
        val socket = MoonrakerSocket(factory, request)

        val capturedConn = AtomicReference<RpcConnection?>(null)
        val job = socket.events()
            .onEach { if (it is SocketEvent.Open) capturedConn.set(it.connection) }
            .launchIn(backgroundScope)

        testScheduler.advanceUntilIdle()
        val fake = requireNotNull(factory.fake.get())
        fake.open()
        testScheduler.advanceUntilIdle()

        val conn = requireNotNull(capturedConn.get())
        assertTrue(conn.isOpen)

        // Cancel the collecting scope → awaitClose → connection invalidated + socket cancelled.
        job.cancel()
        testScheduler.advanceUntilIdle()

        assertFalse("awaitClose must invalidate the connection", conn.isOpen)
        assertTrue("awaitClose must cancel the underlying socket (no leak)", fake.closed)
    }

    @Test
    fun `onFailure emits Closed with a typed cause and invalidates the connection`() = runTest(UnconfinedTestDispatcher()) {
        val factory = CapturingFactory()
        val socket = MoonrakerSocket(factory, request)

        val events = mutableListOf<SocketEvent>()
        val capturedConn = AtomicReference<RpcConnection?>(null)
        val job = socket.events()
            .onEach {
                events += it
                if (it is SocketEvent.Open) capturedConn.set(it.connection)
            }
            .launchIn(backgroundScope)

        testScheduler.advanceUntilIdle()
        val fake = requireNotNull(factory.fake.get())
        fake.open()
        fake.simulateFailure(java.io.IOException("socket died"))
        testScheduler.advanceUntilIdle()

        val closed = events.filterIsInstance<SocketEvent.Closed>().single()
        assertEquals(ConnectionError.NetworkUnavailable, closed.cause)
        assertFalse(requireNotNull(capturedConn.get()).isOpen)

        job.cancel()
    }
}
