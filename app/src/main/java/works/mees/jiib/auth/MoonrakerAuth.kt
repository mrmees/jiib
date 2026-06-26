package works.mees.jiib.auth

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import works.mees.jiib.net.ConnectionError
import works.mees.jiib.net.JsonRpcMethods
import works.mees.jiib.net.MoonrakerJson
import works.mees.jiib.net.classifyIdentifyError
import works.mees.jiib.state.ConnectionState
import java.io.IOException

/**
 * The optional Moonraker auth layer (CONN-02), mock-testable per D-06.
 *
 * Responsibilities (all exercised by mock tests only — the test-bed printer is OPEN/trusted-client):
 * - [fetchOneshotToken] issues `GET /access/oneshot_token` through an INJECTABLE [okhttp3.Call.Factory]
 *   (the real path passes the shared `OkHttpClient`, which IS a `Call.Factory`; tests pass a fake that
 *   returns canned `Response`s — no network). Returns the base32 token (5 s TTL / single-use, so call it
 *   immediately before each connect when a key is configured).
 * - [buildAuthedWsUrl] appends `?token=<token>` to the ws URL.
 * - [xApiKeyHeader] / the internal request builder set `X-Api-Key` on REST when a key is configured;
 *   nothing when not (D-06 default open path).
 * - [mapAuthFailure] turns an [AuthException] (e.g. REST 401) into a [ConnectionState.Error]; a REST 401
 *   maps to [ConnectionError.AuthRequired].
 * - [mapIdentifyError] DELEGATES identify JSON-RPC errors to [classifyIdentifyError] (Plan 01): an
 *   unauthorized shape → AuthRequired; a clearly non-auth method error → Protocol/ServerError; an
 *   unrecognized identify error still defaults to AuthRequired defensively (A5).
 *
 * SECURITY (T-02-06 / T-02-07, RESEARCH V7): the api key and the full `?token=` URL are NEVER logged.
 * This class carries no `Log`/`println` of either; [toString] is overridden to redact, and [redactWsUrl]
 * strips a `?token=` query before any URL is surfaced.
 */
class MoonrakerAuth(
    private val callFactory: Call.Factory,
    private val httpBase: String,
    private val apiKey: String?,
) {

    /** True when a key is configured (auth path engaged); false on the D-06 open path. */
    val isKeyed: Boolean get() = !apiKey.isNullOrBlank()

    /**
     * Fetch a oneshot websocket token via the injected [callFactory] (`GET /access/oneshot_token`),
     * carrying `X-Api-Key` when keyed. Returns the base32 token string.
     *
     * @throws AuthException with [ConnectionError.AuthRequired] on HTTP 401, or
     *   [ConnectionError.NetworkUnavailable] on transport failure / malformed body — never an
     *   un-typed escape.
     */
    fun fetchOneshotToken(): String {
        val request = Request.Builder()
            .url("$httpBase/access/oneshot_token")
            .get()
            .apply { xApiKeyHeader()?.let { header(HEADER_API_KEY, it) } }
            .build()

        val body = try {
            callFactory.newCall(request).execute().use { resp ->
                if (resp.code == 401) {
                    throw AuthException(ConnectionError.AuthRequired, "oneshot_token: HTTP 401")
                }
                if (!resp.isSuccessful) {
                    throw AuthException(
                        ConnectionError.ServerError(resp.code, "oneshot_token HTTP ${resp.code}"),
                        "oneshot_token: HTTP ${resp.code}",
                    )
                }
                resp.body?.string()
            }
        } catch (e: IOException) {
            // R7 (26.5-07 codex review): an https token fetch against an untrusted cert raises
            // SSLHandshakeException (an IOException) — classify it as TlsTrustFailure so the
            // cert UX fires instead of a misleading "network unavailable".
            if (works.mees.jiib.net.MoonrakerSocket.isTlsTrustFailure(e)) {
                throw AuthException(ConnectionError.TlsTrustFailure, "oneshot_token: ${e.message}")
            }
            // Transport failure (DNS/host down/refused) — typed, never an escaping IOException.
            throw AuthException(ConnectionError.NetworkUnavailable, "oneshot_token: ${e.message}")
        }

        val token = runCatching {
            MoonrakerJson.parseToJsonElement(body.orEmpty()).jsonObject["result"]?.jsonPrimitive?.content
        }.getOrNull()

        return token ?: throw AuthException(
            ConnectionError.ParseError("oneshot_token: no base32 token in response"),
            "oneshot_token: malformed body",
        )
    }

    /** Append `?token=<token>` to [baseWsUrl] (the ws auth path). */
    fun buildAuthedWsUrl(baseWsUrl: String, token: String): String {
        // OkHttp's HttpUrl only parses http/https; map the ws scheme to http for query-building,
        // then restore it. (ws://host:port/path?token= == http://host:port/path?token= structurally.)
        val isSecure = baseWsUrl.startsWith("wss://")
        val httpForm = baseWsUrl
            .replaceFirst("wss://", "https://")
            .replaceFirst("ws://", "http://")
        val built = httpForm.toHttpUrl().newBuilder()
            .addQueryParameter("token", token)
            .build()
            .toString()
        return if (isSecure) {
            built.replaceFirst("https://", "wss://")
        } else {
            built.replaceFirst("http://", "ws://")
        }
    }

    /** The `X-Api-Key` header value to apply to REST requests, or null on the open path. */
    fun xApiKeyHeader(): String? = apiKey?.takeIf { it.isNotBlank() }

    /** Turn a thrown [AuthException] into the connection-state error to surface (no key in the state). */
    fun mapAuthFailure(e: AuthException): ConnectionState.Error = ConnectionState.Error(e.reason)

    // Redact secrets in any accidental string surface (never log key/token).
    override fun toString(): String = "MoonrakerAuth(httpBase=$httpBase, keyed=$isKeyed)"

    companion object {
        const val HEADER_API_KEY = "X-Api-Key"

        /** The JSON-RPC method/REST path for the oneshot token. */
        const val ONESHOT_TOKEN_METHOD = JsonRpcMethods.ONESHOT_TOKEN

        /**
         * Classify a `server.connection.identify` JSON-RPC error into a [ConnectionState.Error],
         * delegating the typing to [classifyIdentifyError] (Plan 01): unauthorized shapes → AuthRequired,
         * clearly non-auth method errors → Protocol/ServerError, unrecognized → AuthRequired (A5).
         */
        fun mapIdentifyError(code: Int?, message: String?): ConnectionState.Error =
            ConnectionState.Error(classifyIdentifyError(code, message))

        /**
         * Strip a `?token=...` query from a ws URL so it can be safely surfaced/logged (T-02-06). The
         * token is 5 s/single-use by design, but it must never reach a log or a logged URL regardless.
         */
        fun redactWsUrl(url: String): String =
            url.replace(Regex("([?&]token=)[^&]*"), "$1<redacted>")
    }
}

/**
 * A typed auth/transport failure raised by [MoonrakerAuth]. Carries a [ConnectionError] reason so the
 * session layer surfaces it as a typed [ConnectionState.Error] without leaking the key or a stack trace.
 * Plain [Exception] (not a CancellationException) so it fails — never cancels — any awaiting work.
 */
class AuthException(
    val reason: ConnectionError,
    override val message: String,
) : Exception(message)
