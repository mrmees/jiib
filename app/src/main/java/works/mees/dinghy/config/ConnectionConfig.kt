package works.mees.dinghy.config

/**
 * Immutable, user-entered Moonraker connection (CONN-01, D-04) — the persisted runtime config that
 * replaces the static [DevConfig] as the connection SOURCE once a user saves a connection in Settings.
 *
 * The URL shapes mirror [DevConfig] EXACTLY (`http://host:port`, `ws://host:port/websocket`) so the
 * spine connects exactly where the Phase-1 cleartext smoke probe proved cleartext works. The default
 * port is Moonraker's conventional `7125`.
 *
 * SECURITY (T-04-01-I, mirroring DevConfig.kt:18 / MoonrakerAuth redaction): [toString] redacts
 * [apiKey] to `***` so the key NEVER reaches a log line, the FGS notification, or a crash dump.
 */
data class ConnectionConfig(
    val host: String,
    val port: Int = 7125,
    val apiKey: String? = null,
) {
    /** REST base, e.g. `http://192.168.1.50:7125` (cleartext per D-10/D-11) — mirrors DevConfig.kt:34. */
    val httpBase: String get() = "http://$host:$port"

    /** WebSocket URL, e.g. `ws://192.168.1.50:7125/websocket` — mirrors DevConfig.kt:37. */
    val wsUrl: String get() = "ws://$host:$port/websocket"

    /** Redacts the API key (T-04-01-I) — never let the key reach a log line. */
    override fun toString(): String =
        "ConnectionConfig(host=$host, port=$port, apiKey=${if (apiKey != null) "***" else "null"})"
}
