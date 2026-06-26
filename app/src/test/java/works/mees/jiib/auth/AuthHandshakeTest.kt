package works.mees.jiib.auth

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.ConnectionError
import works.mees.jiib.net.GoldenFixtures
import works.mees.jiib.state.ConnectionState
import java.io.IOException

/**
 * Mock-only auth tests (D-06). Drive [MoonrakerAuth] with a FAKE [okhttp3.Call.Factory] that returns
 * canned [Response]s — no network. Proves: `?token=` appended from a fake-returned token, `X-Api-Key`
 * set when keyed (none when not), a fake REST-401 → [ConnectionError.AuthRequired], identify `-32602
 * "Unauthorized"` → [ConnectionError.AuthRequired], a clearly non-auth identify error → typed
 * Protocol/Server (not blanket AuthRequired), and that no log path emits the key/token.
 */
class AuthHandshakeTest {

    private companion object {
        const val HTTP_BASE = "http://printer.test:7125"
        const val WS_URL = "ws://printer.test:7125/websocket"
        const val API_KEY = "SECRETKEY1234567890"
        const val FAKE_TOKEN = "APDBEGHUTBUD6SOAYBPF3KE5BRMO7YSL"
    }

    /** A fake Call.Factory that synthesizes a canned Response for whatever request it is handed. */
    private class FakeCallFactory(
        private val responder: (Request) -> Response,
    ) : Call.Factory {
        /** Captures the last request actually issued so tests can assert headers. */
        var lastRequest: Request? = null
            private set

        override fun newCall(request: Request): Call = object : Call {
            override fun request(): Request = request
            override fun execute(): Response {
                lastRequest = request
                return responder(request)
            }
            override fun enqueue(responseCallback: Callback) = throw UnsupportedOperationException()
            override fun cancel() = Unit
            override fun isExecuted(): Boolean = false
            override fun isCanceled(): Boolean = false
            override fun timeout(): okio.Timeout = okio.Timeout.NONE
            override fun clone(): Call = this
        }
    }

    private fun response(request: Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 401) "Unauthorized" else "OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    @Test
    fun fetchOneshotToken_returnsBase32AndBuildsTokenUrl() {
        val factory = FakeCallFactory { req ->
            response(req, 200, """{"result":"$FAKE_TOKEN"}""")
        }
        val auth = MoonrakerAuth(callFactory = factory, httpBase = HTTP_BASE, apiKey = API_KEY)

        val token = auth.fetchOneshotToken()
        assertEquals(FAKE_TOKEN, token)

        val url = auth.buildAuthedWsUrl(WS_URL, token)
        assertTrue("ws URL must carry ?token=", url.contains("?token=$FAKE_TOKEN"))
    }

    @Test
    fun restRequest_carriesXApiKeyWhenKeyed() {
        val factory = FakeCallFactory { req -> response(req, 200, """{"result":"$FAKE_TOKEN"}""") }
        val auth = MoonrakerAuth(callFactory = factory, httpBase = HTTP_BASE, apiKey = API_KEY)

        auth.fetchOneshotToken()

        assertEquals(API_KEY, factory.lastRequest?.header(MoonrakerAuth.HEADER_API_KEY))
    }

    @Test
    fun restRequest_noXApiKeyWhenOpen() {
        val factory = FakeCallFactory { req -> response(req, 200, """{"result":"$FAKE_TOKEN"}""") }
        val auth = MoonrakerAuth(callFactory = factory, httpBase = HTTP_BASE, apiKey = null)

        auth.fetchOneshotToken()

        assertNull(factory.lastRequest?.header(MoonrakerAuth.HEADER_API_KEY))
    }

    @Test
    fun rest401_mapsToAuthRequired() {
        val factory = FakeCallFactory { req ->
            response(req, 401, """{"error":{"code":401,"message":"Unauthorized"}}""")
        }
        val auth = MoonrakerAuth(callFactory = factory, httpBase = HTTP_BASE, apiKey = API_KEY)

        // No exception escapes; the typed failure is surfaced.
        val result = runCatching { auth.fetchOneshotToken() }
        assertTrue("401 must not throw an unchecked/escaping exception (typed AuthException)", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is AuthException && ex.reason == ConnectionError.AuthRequired)

        val state = auth.mapAuthFailure(ex as AuthException)
        assertEquals(ConnectionState.Error(ConnectionError.AuthRequired), state)
    }

    @Test
    fun identifyMinus32602Unauthorized_mapsToAuthRequired() {
        val frame = GoldenFixtures.frames("adversarial_auth_identify_error.json").first()
        val errObj = works.mees.jiib.net.MoonrakerJson
            .parseToJsonElement(frame).jsonObject["error"]!!.jsonObject
        val codeInt = errObj["code"]!!.jsonPrimitive.int
        val message = errObj["message"]!!.jsonPrimitive.content

        val state = MoonrakerAuth.mapIdentifyError(codeInt, message)
        assertEquals(ConnectionState.Error(ConnectionError.AuthRequired), state)
    }

    @Test
    fun identifyNonAuthError_mapsToProtocolOrServer_notAuthRequired() {
        // A clearly non-auth method error (e.g. method not found) must NOT be swallowed as AuthRequired.
        val protocol = MoonrakerAuth.mapIdentifyError(-32601, "Method not found")
        val reason = protocol.reason
        assertTrue(
            "non-auth identify error must be typed Protocol/Server, not AuthRequired",
            reason is ConnectionError.ProtocolError || reason is ConnectionError.ServerError,
        )
        assertFalse(reason == ConnectionError.AuthRequired)
    }

    @Test
    fun networkFailureDuringTokenFetch_mapsToNetworkUnavailable() {
        val factory = FakeCallFactory { throw IOException("connection refused") }
        val auth = MoonrakerAuth(callFactory = factory, httpBase = HTTP_BASE, apiKey = API_KEY)

        val result = runCatching { auth.fetchOneshotToken() }
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is AuthException && ex.reason == ConnectionError.NetworkUnavailable)
    }

    @Test
    fun secretsNeverAppearInToString_orRedaction() {
        val factory = FakeCallFactory { req -> response(req, 200, """{"result":"$FAKE_TOKEN"}""") }
        val auth = MoonrakerAuth(callFactory = factory, httpBase = HTTP_BASE, apiKey = API_KEY)
        val token = auth.fetchOneshotToken()
        val redacted = MoonrakerAuth.redactWsUrl(auth.buildAuthedWsUrl(WS_URL, token))

        assertFalse("redacted ws URL must not contain the raw token", redacted.contains(FAKE_TOKEN))
        assertFalse("auth.toString must not leak the api key", auth.toString().contains(API_KEY))
    }
}
