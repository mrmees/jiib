package works.mees.jiib.net

import kotlinx.coroutines.TimeoutCancellationException
import works.mees.jiib.auth.AuthException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

/** Failure category returned by [classifyProbeFailure]. */
enum class ProbeFailure { Timeout, Refused, Unauthorized, Certificate, Unknown }

/**
 * Pure, side-effect-free classifier: maps a probe failure (a [Throwable] and/or an HTTP status
 * code) to a [ProbeFailure] bucket.
 *
 * Priority order:
 * 1. HTTP 401/403 → [ProbeFailure.Unauthorized]
 * 2. [AuthException] carrying [ConnectionError.AuthRequired] → [ProbeFailure.Unauthorized]
 * 3. [RpcError] that [classifyIdentifyError] maps to [ConnectionError.AuthRequired]
 *    → [ProbeFailure.Unauthorized]
 * 4. [RpcConnectionException] carrying [ConnectionError.AuthRequired]
 *    → [ProbeFailure.Unauthorized]
 * 5. [RpcConnectionException] carrying [ConnectionError.Timeout] → [ProbeFailure.Timeout]
 * 6. [MoonrakerSocket.isTlsTrustFailure] → [ProbeFailure.Certificate]
 * 7. Timeout exception types ([TimeoutCancellationException], [SocketTimeoutException],
 *    [TimeoutException]) → [ProbeFailure.Timeout]
 * 8. [ConnectException] or message contains "refused" → [ProbeFailure.Refused]
 * 9. [SSLException] or message contains "cert"/"trust" → [ProbeFailure.Certificate]
 * 10. Else → [ProbeFailure.Unknown]
 */
fun classifyProbeFailure(t: Throwable?, httpStatus: Int?): ProbeFailure {
    // 1. HTTP status takes priority over the exception type.
    if (httpStatus == 401 || httpStatus == 403) return ProbeFailure.Unauthorized

    // 2. Auth-layer exception (REST 401 path).
    if (t is AuthException && t.reason == ConnectionError.AuthRequired) {
        return ProbeFailure.Unauthorized
    }

    // 3. Identify RPC error classified as auth-required.
    if (t is RpcError && classifyIdentifyError(t.code, t.message) == ConnectionError.AuthRequired) {
        return ProbeFailure.Unauthorized
    }

    // 4 & 5. Typed connection exception from the dispatch layer.
    if (t is RpcConnectionException) {
        return when (t.reason) {
            ConnectionError.AuthRequired -> ProbeFailure.Unauthorized
            ConnectionError.Timeout -> ProbeFailure.Timeout
            else -> ProbeFailure.Unknown
        }
    }

    // 6. TLS/cert trust failure (SSLHandshakeException / SSLPeerUnverifiedException, wrapped or direct).
    if (t != null && MoonrakerSocket.isTlsTrustFailure(t)) return ProbeFailure.Certificate

    val msg = t?.message?.lowercase().orEmpty()

    return when {
        // 7. Timeout exception types.
        t is TimeoutCancellationException || t is SocketTimeoutException || t is TimeoutException ->
            ProbeFailure.Timeout

        // 8. Connection refused.
        t is ConnectException || "refused" in msg -> ProbeFailure.Refused

        // 9. SSL / cert / trust failures not caught by isTlsTrustFailure above.
        t is SSLException || "cert" in msg || "trust" in msg -> ProbeFailure.Certificate

        // 10. No recognizable shape.
        else -> ProbeFailure.Unknown
    }
}
