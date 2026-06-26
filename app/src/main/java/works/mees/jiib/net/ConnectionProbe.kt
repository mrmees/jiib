package works.mees.jiib.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import works.mees.jiib.auth.MoonrakerAuth
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.IdentifyArgs
import works.mees.jiib.command.request
import works.mees.jiib.config.ConnectionConfig
import works.mees.jiib.config.ConnectionUrls
import works.mees.jiib.config.buildConnectionUrls
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Outcome of one transport leg in a connection probe. */
data class TransportResult(val ok: Boolean, val failure: ProbeFailure? = null)

/** Result of probing both HTTP and WebSocket legs for a given [ConnectionConfig]. */
data class ProbeResult(
    val httpUrl: String,
    val wsUrl: String,
    val http: TransportResult,
    val ws: TransportResult,
)

/**
 * Dual HTTP + WebSocket connection probe, with auth-exercising legs.
 *
 * The two legs run CONCURRENTLY inside a [coroutineScope]. Each leg is wrapped by [runLeg] which:
 *  - applies a [perLegTimeoutMs] deadline via [withTimeout],
 *  - converts [TimeoutCancellationException] to [ProbeFailure.Timeout],
 *  - rethrows every other [CancellationException] (caller cancellation propagates),
 *  - maps non-cancel exceptions through [classifyProbeFailure].
 *
 * Construct with injected lambdas for unit testing, or call [real] for production.
 */
class ConnectionProbe(
    private val httpLeg: suspend (ConnectionConfig, ConnectionUrls) -> TransportResult,
    private val wsLeg: suspend (ConnectionConfig, ConnectionUrls) -> TransportResult,
    private val perLegTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    /**
     * Probe both transports and return a [ProbeResult]. Always returns a result (never throws) unless
     * the caller's coroutine is cancelled with a non-timeout [CancellationException].
     */
    suspend fun probe(config: ConnectionConfig): ProbeResult = coroutineScope {
        val urls = buildConnectionUrls(config.host, config.port, config.advancedUrl, config.useSecure)
        val httpD = async { runLeg { httpLeg(config, urls) } }
        val wsD = async { runLeg { wsLeg(config, urls) } }
        ProbeResult(urls.httpBase, urls.wsUrl, httpD.await(), wsD.await())
    }

    private suspend fun runLeg(block: suspend () -> TransportResult): TransportResult =
        try {
            withTimeout(perLegTimeoutMs) { block() }
        } catch (e: TimeoutCancellationException) {
            TransportResult(ok = false, failure = ProbeFailure.Timeout)
        } catch (e: CancellationException) {
            // Non-timeout cancellation = caller cancelled; propagate so the whole probe aborts.
            throw e
        } catch (t: Throwable) {
            TransportResult(ok = false, failure = classifyProbeFailure(t, null))
        }

    companion object {
        /** Per-leg probe timeout (ms). Both legs get this independent deadline. */
        const val DEFAULT_TIMEOUT_MS = 5_000L

        private const val CLIENT_NAME = "jiib"
        private const val CLIENT_VERSION = "0.1.0"
        private const val CLIENT_URL = "https://github.com/mrmees/jiib"

        /**
         * Build the production [ConnectionProbe] backed by a real OkHttp client.
         *
         * The [sharedClient] is [MoonrakerSocket.defaultClient] which has `readTimeout(0)` — suitable
         * for websockets but will hang on REST. The HTTP leg derives a finite-timeout posture via
         * `newBuilder()`, sharing the pool/TLS config of the parent client.
         *
         * @param socketFactory injectable for testing the real-leg wiring without real sockets.
         */
        fun real(
            sharedClient: OkHttpClient,
            socketFactory: (OkHttpClient, String) -> MoonrakerSocket =
                { client, wsUrl -> MoonrakerSocket.real(client = client, wsUrl = wsUrl) },
        ): ConnectionProbe {
            val restClient = sharedClient.newBuilder()
                .callTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
            return ConnectionProbe(
                httpLeg = { config, urls -> realHttpLeg(restClient, config, urls) },
                wsLeg = { config, urls -> realWsLeg(sharedClient, config, urls, socketFactory) },
            )
        }

        /**
         * HTTP leg: GET `$httpBase/server/info` with optional `X-Api-Key`; ok iff 200.
         *
         * `/server/info` is a Moonraker-protected endpoint (unlike `/access/info` which is reachable
         * by unauthorized clients), so a 401/403 response signals an auth failure.
         */
        private suspend fun realHttpLeg(
            client: OkHttpClient,
            config: ConnectionConfig,
            urls: ConnectionUrls,
        ): TransportResult = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${urls.httpBase}/server/info")
                .get()
                .apply {
                    config.apiKey?.takeIf { it.isNotBlank() }?.let {
                        header(MoonrakerAuth.HEADER_API_KEY, it)
                    }
                }
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.code == 200) {
                        TransportResult(ok = true)
                    } else {
                        TransportResult(ok = false, failure = classifyProbeFailure(null, resp.code))
                    }
                }
            } catch (e: IOException) {
                TransportResult(ok = false, failure = classifyProbeFailure(e, null))
            }
        }

        /**
         * WebSocket leg: opens a one-shot Moonraker socket, sends `identify` (with auth), then
         * `server.info`; ok iff both RPC calls succeed. Tears down the socket in `finally`.
         *
         * Mirrors the auth flow in [works.mees.jiib.service.MoonrakerService]: when an API key is
         * set, fetches a oneshot token via [MoonrakerAuth.fetchOneshotToken] and appends it to the
         * ws URL via [MoonrakerAuth.buildAuthedWsUrl].
         */
        private suspend fun realWsLeg(
            client: OkHttpClient,
            config: ConnectionConfig,
            urls: ConnectionUrls,
            socketFactory: (OkHttpClient, String) -> MoonrakerSocket,
        ): TransportResult = coroutineScope {
            val auth = MoonrakerAuth(client, urls.httpBase, config.apiKey)
            val wsUrl = if (auth.isKeyed) {
                val token = withContext(Dispatchers.IO) { auth.fetchOneshotToken() }
                auth.buildAuthedWsUrl(urls.wsUrl, token)
            } else {
                urls.wsUrl
            }

            val rpc = JsonRpcClient(defaultTimeoutMs = DEFAULT_TIMEOUT_MS)
            val opened = CompletableDeferred<Unit>()
            var liveConnection: RpcConnection? = null

            val collector = launch {
                socketFactory(client, wsUrl).events().collect { event ->
                    when (event) {
                        is SocketEvent.Open -> {
                            liveConnection = event.connection
                            rpc.bind(event.connection)
                            if (!opened.isCompleted) opened.complete(Unit)
                        }
                        is SocketEvent.Frame -> rpc.dispatch(event.text)
                        is SocketEvent.Closed -> {
                            val cause = event.cause ?: ConnectionError.NetworkUnavailable
                            if (!opened.isCompleted) {
                                opened.completeExceptionally(
                                    RpcConnectionException(cause, "probe socket closed before open"),
                                )
                            }
                            rpc.close(cause)
                        }
                    }
                }
                // Flow completed without emitting Open — report failure.
                if (!opened.isCompleted) {
                    opened.completeExceptionally(
                        RpcConnectionException(
                            ConnectionError.NetworkUnavailable,
                            "probe socket flow completed before open",
                        ),
                    )
                }
            }

            try {
                opened.await()
                rpc.request(
                    CommandRegistry.identify,
                    IdentifyArgs(
                        clientName = CLIENT_NAME,
                        version = CLIENT_VERSION,
                        url = CLIENT_URL,
                        apiKey = auth.xApiKeyHeader(),
                    ),
                    timeoutMs = DEFAULT_TIMEOUT_MS,
                )
                rpc.request(CommandRegistry.serverInfo, Unit, timeoutMs = DEFAULT_TIMEOUT_MS)
                TransportResult(ok = true)
            } finally {
                withContext(NonCancellable) {
                    liveConnection?.close()
                    rpc.close(ConnectionError.NetworkUnavailable)
                    collector.cancelAndJoin()
                }
            }
        }
    }
}
