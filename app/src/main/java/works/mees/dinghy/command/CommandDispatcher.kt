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
import works.mees.dinghy.net.ConnectionError
import works.mees.dinghy.net.JsonRpcClient
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.net.RpcConnectionException
import works.mees.dinghy.net.RpcError

/**
 * Event surfaced by [CommandDispatcher] for the host to render (PRIM-04 toast). Only a redacted,
 * human-readable failure message ever crosses this seam — NEVER the API key or a `?token=` URL.
 */
sealed interface DispatchEvent {
    /**
     * A wrapped action failed. Toast as `Severity.Error`. Covers a transport error
     * ([RpcConnectionException]), a dispatcher timeout, AND a server-side gcode rejection
     * ([RpcError]) — e.g. an out-of-range move or a failing macro. For an [RpcError] the message is
     * the printer's own rejection text so the user sees WHY the command was refused.
     *
     * NOTE (G4): a [RpcConnectionException] carrying [ConnectionError.Timeout] is NOT a transport
     * failure — a long-running but valid gcode (Z-home/probe, mesh, load/unload macro) simply hasn't
     * replied yet. That branch yields a BENIGN "taking longer than expected — still running" message,
     * not the alarming "command could not be sent" reserved for a genuine no-connection/send failure
     * ([ConnectionError.NetworkUnavailable]). Both messages are composed from the non-secret
     * `method`/`key` only — never `e.message` (T-05-11-01).
     */
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

    private val _rejectedKey = MutableSharedFlow<String>(
        extraBufferCapacity = REJECT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /**
     * R10 (26.5-03): emits the `key` of each INTENTIONALLY-dropped dispatch — in-flight re-tap OR
     * a re-tap inside the debounce window. UI controls collect this to render a brief one-shot
     * visible rejection signal ("heard you, still settling") instead of nothing. This is FEEDBACK
     * ONLY: the busy/debounce guard semantics and timing are unchanged (they protect the printer
     * and SBC — T-26.5-07). Emission uses [MutableSharedFlow.tryEmit] with a DROP_OLDEST buffer so
     * it can never suspend or block [dispatch].
     */
    val rejectedKey: SharedFlow<String> = _rejectedKey.asSharedFlow()

    /**
     * Last-accepted-dispatch timestamp per key, for the debounce window. Uses
     * [java.util.concurrent.ConcurrentHashMap] so reads/writes are safe if [dispatch] is ever
     * called from a non-main thread (e.g. future interface expansions in SessionControl).
     */
    private val lastAccepted = java.util.concurrent.ConcurrentHashMap<String, Long>()

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

        // Per-command timeout: gcode.script's reply is gated on the gcode COMPLETING (homing/probe,
        // bed mesh, filament load/unload macros routinely run tens of seconds), so it gets the long
        // GCODE_TIMEOUT_MS. Every other method (emergency_stop, queries) replies promptly and keeps
        // the short default. Both the inner request() deadline AND the outer withTimeout use the same
        // perCmdTimeout so they no longer race at 10s (G4).
        val perCmdTimeout = if (method == JsonRpcMethods.GCODE_SCRIPT) GCODE_TIMEOUT_MS else timeoutMs

        _inFlight.update { it + key }
        scope.launch {
            try {
                withTimeout(perCmdTimeout) { request(method, params, perCmdTimeout) }
            } catch (e: RpcConnectionException) {
                // Disambiguate a slow-but-valid gcode (request-await Timeout — frame WAS sent and
                // accepted, reply just hasn't arrived) from a genuine transport failure (no
                // connection / send failure). Both messages are built ONLY from the non-secret
                // `method` + a fixed string — NEVER `e.message`, which on a send failure can carry a
                // `?token=` URL (T-05-11-01, guarded by failureMessageNeverEmbedsApiKeyOrToken).
                val message = when (e.reason) {
                    is ConnectionError.Timeout ->
                        "$method is taking longer than expected — still running"
                    else ->
                        "$method failed: command could not be sent"
                }
                _events.tryEmit(DispatchEvent.Failure(key, message))
            } catch (e: RpcError) {
                // Server-side gcode rejection (out-of-range move, failing macro, heater fault). The
                // printer returned a JSON-RPC error envelope; JsonRpcClient completed the deferred
                // with RpcError. This MUST be a non-fatal toast, never an uncaught crash (G1). Surface
                // the printer's own rejection text so the user sees WHY it was refused; this message is
                // gcode-rejection text and never carries a credential, but the key (an action id) and
                // method name are non-secret, consistent with the transport/timeout branches above.
                _events.tryEmit(DispatchEvent.Failure(key, e.message ?: method))
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

        /**
         * Action deadline (ms) for `printer.gcode.script`. Unlike instant calls, gcode.script's
         * JSON-RPC reply arrives only when the gcode COMPLETES — Klipper homing+probe, bed-mesh
         * calibration, and filament load/unload macros routinely run tens of seconds, far past the
         * 10s [DEFAULT_TIMEOUT_MS]. 120s is a generous ceiling that still bounds a truly-wedged call
         * so the key is always removed in `finally` and a control can never be permanently disabled
         * (T-05-11-02 / T-04-02-DoS preserved). A slow-but-valid gcode finishing under this ceiling
         * no longer trips a false "command could not be sent" toast (G4).
         */
        const val GCODE_TIMEOUT_MS = 120_000L

        private const val EVENT_BUFFER = 16

        /** Buffer for [rejectedKey] — rapid-tap bursts are small; DROP_OLDEST keeps tryEmit lossy-safe. */
        private const val REJECT_BUFFER = 4
    }
}
