package works.mees.dinghy.net

/**
 * Typed connection / RPC error model — the public failure contract Wave 2/3 surface through
 * `ConnectionState.Error(reason)`.
 *
 * This REFINES the earlier blanket rule "any identify error → AuthRequired" (review MEDIUM).
 * Clearly-unauthorized identify shapes still map to [AuthRequired]; clearly-non-auth method/param
 * errors map to [ProtocolError]; server-side failures to [ServerError]; and only an
 * UNRECOGNIZABLE identify error falls back to [AuthRequired] defensively (A5 is MEDIUM-confidence) —
 * as the explicit LAST branch, not the default for everything.
 */
sealed interface ConnectionError {

    /**
     * Credentials are missing or invalid (Moonraker `-32602 "Unauthorized"` on identify, REST 401,
     * or an untrusted client IP). Surface CONN-02/D-06.
     */
    data object AuthRequired : ConnectionError

    /** The transport is unreachable (socket open/connect failed, DNS/host down, Wi-Fi drop). */
    data object NetworkUnavailable : ConnectionError

    /**
     * A request was sent and accepted by the transport but no reply arrived within the per-request
     * deadline — distinct from [NetworkUnavailable] (a true send/connection failure). For a
     * long-running gcode (Z-home/probe, bed mesh, filament load/unload macro) this is EXPECTED, not
     * an error: the printer is still executing the gcode and will reply when it completes. The
     * dispatcher uses this reason to surface a calm "still running" message instead of a false
     * "command could not be sent" (G4).
     */
    data object Timeout : ConnectionError

    /** A JSON-RPC method/param error that is NOT an auth failure (e.g. a bad request shape). */
    data class ProtocolError(val code: Int, val message: String) : ConnectionError

    /** A server-side failure reported by Moonraker (Klipper/host error surfaced over RPC). */
    data class ServerError(val code: Int, val message: String) : ConnectionError

    /** A frame could not be parsed / a payload was malformed (hostile-JSON defense). */
    data class ParseError(val detail: String) : ConnectionError
}

/**
 * Exception thrown by the dispatch/handshake layer (Wave 2) to fail a pending
 * `CompletableDeferred` when an RPC returns an error envelope. Carries the raw JSON-RPC
 * `code`/`message` so callers can re-classify via [classifyIdentifyError] or build a
 * [ConnectionError.ServerError]/[ConnectionError.ProtocolError].
 */
class RpcError(
    // Nullable: an absent or unparseable wire `code` is `null`, NOT a coerced `0`. A `0` would be
    // mis-classified by [classifyIdentifyError] as ServerError(0) and provoke retry-churn; `null`
    // routes to the safe A5 AuthRequired fallback instead (WR-05).
    val code: Int?,
    override val message: String,
) : Exception("JSON-RPC error ${code ?: "?"}: $message")

/**
 * Pure classifier mapping an identify error response to a typed [ConnectionError]
 * (02-RESEARCH § "Auth handshake", A5). No I/O, no side effects.
 *
 * Mapping:
 * - code `-32602` with an "Unauthorized" message, OR a code-less error whose message contains
 *   "Unauthorized" → [ConnectionError.AuthRequired] (the canonical auth-required signal over the
 *   socket). The substring test is GATED to those auth shapes — a code-bearing error is classified
 *   by code, never by a stray "unauthorized" substring (CR-01).
 * - a NON-auth `-32602` (param error that is clearly not an auth failure) → [ConnectionError.ProtocolError].
 * - any other recognizable JSON-RPC method error → [ConnectionError.ProtocolError].
 * - a server-range failure → [ConnectionError.ServerError].
 * - an unknown / sentinel identify error with no recognizable shape → [ConnectionError.AuthRequired]
 *   defensively (A5, explicit LAST branch — NOT the blanket rule).
 */
fun classifyIdentifyError(code: Int?, message: String?): ConnectionError {
    val msg = message.orEmpty()
    val saysUnauthorized = msg.contains("Unauthorized", ignoreCase = true)

    // 1. Canonical auth signal: the -32602 "Unauthorized", OR an unauthorized message with no code.
    //    The substring test is GATED to the actual auth shapes (code == -32602, or a code-less error) —
    //    NOT applied as a blanket override ahead of the code-based branches (CR-01). A code-bearing
    //    server error whose message merely contains "unauthorized" must classify by code, below.
    if ((code == JsonRpcMethods.CODE_INVALID_PARAMS && saysUnauthorized) ||
        (code == null && saysUnauthorized)) {
        return ConnectionError.AuthRequired
    }

    // 2. A non-auth invalid-params error (e.g. a genuine param/shape problem, duplicate identify).
    if (code == JsonRpcMethods.CODE_INVALID_PARAMS) {
        return ConnectionError.ProtocolError(code, msg)
    }

    // 3. Other JSON-RPC method errors: param/protocol range vs. server range.
    if (code != null) {
        // JSON-RPC reserves -32700..-32600 for protocol-level errors; anything else is server-side.
        return if (code in -32700..-32600) {
            ConnectionError.ProtocolError(code, msg)
        } else {
            ConnectionError.ServerError(code, msg)
        }
    }

    // 4. Unknown / sentinel identify error with no recognizable shape — defensive A5 fallback.
    return ConnectionError.AuthRequired
}
