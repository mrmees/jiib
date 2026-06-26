package works.mees.jiib.config

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Immutable pair of resolved Moonraker URLs — the HTTP REST base and the WebSocket endpoint.
 *
 * Produced by [buildConnectionUrls]; consumed by [ConnectionConfig.httpBase] and
 * [ConnectionConfig.wsUrl]. Keeping this as a value type lets us build and cache both URLs in one
 * call rather than computing each getter independently.
 */
data class ConnectionUrls(val httpBase: String, val wsUrl: String)

/**
 * Result of normalizing a raw host-field entry (what the user typed into the "Host" box in the
 * connection editor). Two outcomes: a clean host + optional port override, or a rejection with a
 * human-readable reason.
 */
sealed interface HostResult {
    data class Clean(val host: String, val portOverride: Int? = null) : HostResult
    data class Rejected(val reason: String) : HostResult
}

private val SCHEME_RE = Regex("^([A-Za-z][A-Za-z0-9+.-]*):", RegexOption.IGNORE_CASE)
private val PORT_RANGE = 1..65535

/**
 * Normalize a raw host-field value into a [HostResult].
 *
 * Accepts:
 *  - Plain hostnames / IPv4 addresses (e.g. `printer.local`, `192.168.1.50`)
 *  - `host:port` shorthand (e.g. `192.168.1.50:7130`)
 *  - Bracketed IPv6 with or without port (e.g. `[::1]`, `[::1]:7125`)
 *
 * Rejects with a reason string:
 *  - Blank input
 *  - Any scheme (`http://`, `ws://`, …) — redirect user to the Advanced URL field
 *  - Paths, query strings, fragments in the host field
 *  - User-info (`user@host`)
 *  - Unclosed IPv6 brackets
 *  - Bare (unbracketed) IPv6 addresses
 *  - Out-of-range port numbers
 */
fun normalizeHost(raw: String): HostResult {
    val s = raw.trim()
    if (s.isEmpty()) return HostResult.Rejected("Enter a host or IP")

    // Reject any scheme prefix (http://, ws://, https://, etc.) or bare scheme (http:)
    if ("://" in s || SCHEME_RE.matches(s)) {
        return HostResult.Rejected("Enter only the host here; put full URLs in Advanced")
    }

    // Reject paths, query strings, fragments in the host field
    if (s.any { it == '/' || it == '?' || it == '#' }) {
        return HostResult.Rejected("Enter only the host here; remove paths, query, or fragment")
    }

    // Reject user-info credentials
    if ('@' in s) {
        return HostResult.Rejected("Enter only the host; remove credentials")
    }

    // Bracketed IPv6: [addr] or [addr]:port
    if (s.startsWith("[")) {
        val close = s.indexOf(']')
        if (close < 0) return HostResult.Rejected("Close the IPv6 bracket")
        val host = s.substring(0, close + 1)
        if (host.length <= 2) return HostResult.Rejected("Enter an IPv6 address in brackets")
        val rest = s.substring(close + 1)
        return when {
            rest.isEmpty() -> HostResult.Clean(host)
            rest.startsWith(":") -> parsePort(rest.drop(1))?.let { HostResult.Clean(host, it) }
                ?: HostResult.Rejected("Port must be 1-65535")
            else -> HostResult.Rejected("Use [IPv6] or [IPv6]:port")
        }
    }

    // Bare IPv6 — multiple colons without brackets → reject
    val colonCount = s.count { it == ':' }
    if (colonCount >= 2) return HostResult.Rejected("Wrap IPv6 addresses in [brackets]")

    // host:port shorthand
    if (colonCount == 1) {
        val host = s.substringBefore(':').trim()
        val portText = s.substringAfter(':').trim()
        if (host.isEmpty()) return HostResult.Rejected("Enter a host or IP")
        val parsedPort = parsePort(portText) ?: return HostResult.Rejected("Port must be 1-65535")
        return HostResult.Clean(host, parsedPort)
    }

    // Plain hostname or IPv4 address
    return HostResult.Clean(s)
}

private fun parsePort(text: String): Int? =
    text.toIntOrNull()?.takeIf { it in PORT_RANGE }

/**
 * Build the [ConnectionUrls] pair for a given [ConnectionConfig].
 *
 * Precedence:
 * 1. **advancedUrl present and non-blank** — parse via OkHttp [toHttpUrlOrNull] (after mapping
 *    `ws/wss` → `http/https` so OkHttp accepts it). Collapse default ports, strip trailing
 *    `/websocket` from the base, reject query/fragment/scheme-only/invalid inputs.
 * 2. **Plain mode** — assemble `scheme://host:port` and `ws-scheme://host:port/websocket` directly,
 *    preserving [useSecure] for the legacy `wss/https` posture.
 */
fun buildConnectionUrls(host: String, port: Int, advancedUrl: String?, useSecure: Boolean): ConnectionUrls {
    val adv = advancedUrl?.trim().orEmpty()
    if (adv.isNotEmpty()) {
        return buildAdvancedConnectionUrls(adv)
    }
    val httpScheme = if (useSecure) "https" else "http"
    val wsScheme = if (useSecure) "wss" else "ws"
    return ConnectionUrls(
        httpBase = "$httpScheme://$host:$port",
        wsUrl = "$wsScheme://$host:$port/websocket",
    )
}

private fun buildAdvancedConnectionUrls(raw: String): ConnectionUrls {
    // Reject raw `#` or `?` before parsing — belt-and-suspenders since OkHttp may silently
    // strip fragments in some builds.
    if ('?' in raw) throw IllegalArgumentException("Advanced URL must not include a query string")
    if ('#' in raw) throw IllegalArgumentException("Advanced URL must not include a fragment")

    val match = SCHEME_RE.find(raw)
        ?: throw IllegalArgumentException("Advanced URL must start with http://, https://, ws://, or wss://")

    val rawScheme = match.groupValues[1].lowercase()
    val httpScheme = when (rawScheme) {
        "http", "https" -> rawScheme
        "ws" -> "http"
        "wss" -> "https"
        else -> throw IllegalArgumentException("Unsupported Advanced URL scheme: $rawScheme")
    }

    // Suffix is everything after the scheme colon (e.g. "//host/path" or "//" for scheme-only)
    val suffix = raw.substring(match.value.length)
    if (suffix.isBlank() || suffix == "//") {
        throw IllegalArgumentException("Advanced URL must include a host")
    }

    val parsed = "$httpScheme:$suffix".toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Invalid Advanced URL")

    // Redundant safety check (raw string guards above are primary)
    if (parsed.query != null) {
        throw IllegalArgumentException("Advanced URL must not include a query string")
    }

    // Strip trailing slash(es) and a terminal /websocket segment from the path
    val basePath = parsed.encodedPath
        .trimEnd('/')
        .removeSuffix("/websocket")
        .ifBlank { "/" }

    val httpBaseUrl = parsed.newBuilder()
        .scheme(httpScheme)
        .encodedPath(basePath)
        .query(null)
        .fragment(null)
        .build()
        .toString()
        .trimEnd('/')

    val wsScheme = if (httpScheme == "https") "wss" else "ws"
    val wsBase = httpBaseUrl.replaceFirst("$httpScheme://", "$wsScheme://")
    return ConnectionUrls(httpBase = httpBaseUrl, wsUrl = "$wsBase/websocket")
}
