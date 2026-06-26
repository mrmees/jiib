package works.mees.jiib.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JsonRpcClient correctness proofs (02-RESEARCH § Pattern 2 + review HIGH #1):
 * - STATE-05: id-correlation under an interleaved notify_status_update (the adversarial fixture),
 * - {error,id} fails the matching deferred with a typed RpcError,
 * - notify_gcode_response routes to a SEPARATE flow from notify_status_update,
 * - close-fails-all-pending: a socket close while a request is in flight fails it (no hang),
 * - per-request timeout fires in virtual time and clears the pending entry.
 *
 * The client is driven directly: a FakeWebSocket backs the RpcConnection (so request() sends go
 * through the live socket contract), and inbound frames are fed via dispatch().
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JsonRpcClientTest {

    /** A connection over a FakeWebSocket whose listener is inert (we drive dispatch ourselves). */
    private fun connection(): Pair<RpcConnection, FakeWebSocket> {
        val fake = FakeWebSocket(object : WebSocketListener() {})
        return RpcConnection(fake) to fake
    }

    @Test
    fun `request resolves by id under an interleaved notification (STATE-05)`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = JsonRpcClient()
            val (conn, fake) = connection()
            client.bind(conn)

            // Observe the status flow concurrently (replay=0, so subscribe before dispatch).
            val statusSeen = async { client.statusUpdates.first() }

            // Fire the query; it suspends awaiting the id-matched reply.
            val reply = async { client.request("printer.objects.query") }
            runCurrent()

            // The client sent a frame carrying an id — capture it to echo the reply.
            assertEquals(1, fake.sentFrames.size)
            val sentId = MoonrakerJson.parseToJsonElement(fake.sentFrames[0])
                .jsonObject["id"]!!.jsonPrimitive.content

            // Interleave a notify_status_update BEFORE the reply — it must NOT satisfy the request.
            client.dispatch(
                """{"jsonrpc":"2.0","method":"notify_status_update",
                   "params":[{"heater_bed":{"temperature":58.9}},100050.0]}""",
            )
            runCurrent()
            assertTrue("request must NOT resolve from a notification", reply.isActive)

            // Now the id-matched reply.
            client.dispatch(
                """{"jsonrpc":"2.0","result":{"eventtime":100051.0,
                   "status":{"heater_bed":{"temperature":59.0}}},"id":$sentId}""",
            )
            runCurrent()

            val result = reply.await().jsonObject
            assertEquals(
                59.0,
                result["status"]!!.jsonObject["heater_bed"]!!.jsonObject["temperature"]!!
                    .jsonPrimitive.double,
                0.0001,
            )
            // The interleaved notification routed to the status flow.
            val diff: JsonObject = statusSeen.await()
            assertTrue(diff.containsKey("heater_bed"))
        }

    @Test
    fun `error response fails the deferred with a typed RpcError`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = JsonRpcClient()
            val (conn, fake) = connection()
            client.bind(conn)

            val reply = async { runCatching { client.request("server.connection.identify") } }
            runCurrent()
            val sentId = MoonrakerJson.parseToJsonElement(fake.sentFrames[0])
                .jsonObject["id"]!!.jsonPrimitive.content

            client.dispatch(
                """{"jsonrpc":"2.0","error":{"code":-32602,"message":"Unauthorized"},"id":$sentId}""",
            )
            runCurrent()

            val ex = reply.await().exceptionOrNull()
            assertTrue("expected RpcError, got $ex", ex is RpcError)
            ex as RpcError
            assertEquals(-32602, ex.code)
            assertEquals("Unauthorized", ex.message)
            // And it classifies to AuthRequired (the CONN-02 surface).
            assertEquals(ConnectionError.AuthRequired, classifyIdentifyError(ex.code, ex.message))
        }

    @Test
    fun `error with a garbage code yields null code and classifies AuthRequired not ServerError`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = JsonRpcClient()
            val (conn, fake) = connection()
            client.bind(conn)

            val reply = async { runCatching { client.request("server.connection.identify") } }
            runCurrent()
            val sentId = MoonrakerJson.parseToJsonElement(fake.sentFrames[0])
                .jsonObject["id"]!!.jsonPrimitive.content

            // A non-integer/garbage wire code must NOT coerce to 0 (which classifyIdentifyError would
            // mis-type as ServerError(0) → retry-churn). It must parse to null → A5 AuthRequired (WR-05).
            client.dispatch(
                """{"jsonrpc":"2.0","error":{"code":"not-a-number","message":"weird"},"id":$sentId}""",
            )
            runCurrent()

            val ex = reply.await().exceptionOrNull()
            assertTrue("expected RpcError, got $ex", ex is RpcError)
            ex as RpcError
            assertNull("garbage wire code must parse to null, not 0", ex.code)
            assertEquals(ConnectionError.AuthRequired, classifyIdentifyError(ex.code, ex.message))
        }

    @Test
    fun `gcode_response routes to a separate flow from status`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = JsonRpcClient()
            val gcodeSeen = async { client.gcodeResponses.first() }

            client.dispatch(
                """{"jsonrpc":"2.0","method":"notify_gcode_response","params":["// echo: hello"]}""",
            )
            runCurrent()

            assertEquals("// echo: hello", gcodeSeen.await())
        }

    @Test
    fun `klippy_shutdown with no params routes without crashing`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = JsonRpcClient()
            val klippySeen = async { client.klippyEvents.first() }

            client.dispatch("""{"jsonrpc":"2.0","method":"notify_klippy_shutdown"}""")
            runCurrent()

            assertEquals(JsonRpcMethods.NOTIFY_KLIPPY_SHUTDOWN, klippySeen.await())
        }

    @Test
    fun `close fails all pending requests with a typed cause (review HIGH #1)`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = JsonRpcClient()
            val (conn, _) = connection()
            client.bind(conn)

            val reply = async { runCatching { client.request("printer.objects.query") } }
            runCurrent()
            assertTrue(reply.isActive)

            // Socket dies → close-fails-all-pending. The suspended request must FAIL, not hang.
            client.close(ConnectionError.NetworkUnavailable)
            runCurrent()

            val ex = reply.await().exceptionOrNull()
            assertTrue("expected RpcConnectionException, got $ex", ex is RpcConnectionException)
            assertEquals(ConnectionError.NetworkUnavailable, (ex as RpcConnectionException).reason)
        }

    @Test
    fun `request without a reply fails on its per-request timeout in virtual time`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = JsonRpcClient(defaultTimeoutMs = 5_000L)
            val (conn, _) = connection()
            client.bind(conn)

            val reply = async { runCatching { client.request("printer.objects.query") } }
            runCurrent()
            assertTrue(reply.isActive)

            // No reply ever arrives — advance past the deadline.
            advanceTimeBy(5_001L)
            runCurrent()

            val ex = reply.await().exceptionOrNull()
            assertTrue("expected timeout RpcConnectionException, got $ex", ex is RpcConnectionException)
            assertTrue((ex as RpcConnectionException).message.contains("timed out"))
        }

    @Test
    fun `request with no active connection fails fast`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = JsonRpcClient()
            // No bind() → no connection.
            val ex = runCatching { client.request("printer.objects.query") }.exceptionOrNull()
            assertTrue(ex is RpcConnectionException)
            assertEquals(ConnectionError.NetworkUnavailable, (ex as RpcConnectionException).reason)
        }

    @Test
    fun `malformed frame is dropped not fatal`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = JsonRpcClient()
            // Should not throw.
            client.dispatch("not json at all {{{")
            client.dispatch("""{"jsonrpc":"2.0"}""") // no id, no method
            runCurrent()
            // No assertion needed beyond "did not throw"; reaching here is the proof.
            assertTrue(true)
        }
}
