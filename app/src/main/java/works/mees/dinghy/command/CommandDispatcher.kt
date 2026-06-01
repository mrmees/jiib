package works.mees.dinghy.command

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import works.mees.dinghy.net.JsonRpcClient
import works.mees.dinghy.net.RpcConnectionException

/**
 * Event surfaced by [CommandDispatcher] for the host to render (PRIM-04 toast). Only a redacted,
 * human-readable failure message ever crosses this seam — NEVER the API key or a `?token=` URL.
 */
sealed interface DispatchEvent {
    /** A wrapped action failed (transport error or dispatcher timeout). Toast as `Severity.Error`. */
    data class Failure(val key: String, val message: String) : DispatchEvent
}

/**
 * The shared command-dispatch wrapper (PRIM-05, D-18). Every Moonraker *action* tap goes through
 * here so the whole shell gets, uniformly and for free:
 *
 * - **debounce** — a second tap of the same `key` within [debounceMs] of the last *accepted* one is
 *   dropped, so a stray double-tap of a destructive control (e-stop) cannot re-issue it (T-04-02-R);
 * - **in-flight/busy guard** — while a `key` is running it is in [inFlight]; re-tapping it is a no-op
 *   and the host can disable the control off that StateFlow (T-04-02-R);
 * - **timeout** — the wrapped call is bounded by [withTimeout]([timeoutMs]); a dropped packet times
 *   out instead of hanging forever, and the key is removed in `finally` so a stuck call cannot
 *   permanently disable a control (T-04-02-DoS).
 *
 * This is ONLY the UI-affordance layer. It does NOT reimplement transport: [JsonRpcClient.request]
 * already does id-correlation, its own per-request `withTimeout`, and no-connection fail-fast. The
 * dispatcher's outer [withTimeout] is a belt-and-braces UI deadline (e.g. covering a call that the
 * transport would not otherwise cut), and is what the host-side test drives against a hung stub.
 *
 * Constructed in production with a [JsonRpcClient] (the secondary constructor binds `rpc::request`);
 * host tests inject a substitutable [request] lambda and a virtual-clock [timeSource].
 */
class CommandDispatcher(
    private val request: suspend (method: String, params: JsonElement?, timeoutMs: Long) -> JsonElement,
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val timeSource: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    /** Production wiring: wrap a live [JsonRpcClient.request]. */
    constructor(
        rpc: JsonRpcClient,
        scope: CoroutineScope,
        debounceMs: Long = DEFAULT_DEBOUNCE_MS,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        timeSource: () -> Long = { System.nanoTime() / 1_000_000L },
    ) : this(
        request = { method, params, t -> rpc.request(method, params, t) },
        scope = scope,
        debounceMs = debounceMs,
        timeoutMs = timeoutMs,
        timeSource = timeSource,
    )

    private val _inFlight = MutableStateFlow<Set<String>>(emptySet())
    /** Keys with a wrapped call currently running — drive control busy/disabled state off this. */
    val inFlight: StateFlow<Set<String>> = _inFlight.asStateFlow()

    private val _events = MutableSharedFlow<DispatchEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /** Failure events for the host to toast. Collect with `collectAsStateWithLifecycle`-driven scope. */
    val events: SharedFlow<DispatchEvent> = _events.asSharedFlow()

    /** Last-accepted-dispatch timestamp per key, for the debounce window. */
    private val lastAccepted = mutableMapOf<String, Long>()

    /**
     * Issue an action [method] under [key]. No-op if [key] is in-flight (busy) or within the
     * debounce window of the last accepted dispatch for that key; otherwise marks [key] in-flight
     * and launches the wrapped call, removing the key (and toasting any typed failure) on completion.
     */
    fun dispatch(key: String, method: String, params: JsonElement? = null) {
        // Busy guard — a running key is never re-entered.
        if (key in _inFlight.value) return

        // Debounce — drop a re-tap within the window of the last accepted dispatch.
        val now = timeSource()
        val prev = lastAccepted[key]
        if (prev != null && now - prev < debounceMs) return
        lastAccepted[key] = now

        _inFlight.update { it + key }
        scope.launch {
            try {
                withTimeout(timeoutMs) { request(method, params, timeoutMs) }
            } catch (e: RpcConnectionException) {
                // Typed transport failure (no connection / send failure / transport timeout).
                _events.tryEmit(DispatchEvent.Failure(key, "$method failed: command could not be sent"))
            } catch (e: TimeoutCancellationException) {
                // The dispatcher's own UI deadline fired.
                _events.tryEmit(DispatchEvent.Failure(key, "$method timed out"))
            } finally {
                _inFlight.update { it - key }
            }
        }
    }

    companion object {
        /** Default tap-debounce window (ms). */
        const val DEFAULT_DEBOUNCE_MS = 400L

        /** Default UI action deadline (ms) — matches the transport default. */
        const val DEFAULT_TIMEOUT_MS = 10_000L

        private const val EVENT_BUFFER = 16
    }
}
