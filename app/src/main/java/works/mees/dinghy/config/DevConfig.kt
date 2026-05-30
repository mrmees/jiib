package works.mees.dinghy.config

import works.mees.dinghy.BuildConfig

/**
 * D-05 — static / dev connection config.
 *
 * Reads the Moonraker host/port/optional-API-key from [BuildConfig] fields, which the build
 * populates from a GITIGNORED `local.properties` (`moonraker.host` / `moonraker.port` /
 * `moonraker.apiKey`) with safe placeholder defaults so a fresh clone still compiles.
 *
 * This is the Phase-2 stand-in for the user-facing connection screen (CONN-01), which lands in
 * Phase 3 with UI + DataStore. The URL shapes mirror the Phase-1 cleartext smoke probe
 * (`http://host:port`, `ws://host:port/websocket`) so the real spine connects exactly where the
 * probe proved cleartext works.
 *
 * SECURITY (T-02-01): the API key is build-time config; never log [apiKey] or a full `?token=` URL.
 */
object DevConfig {

    /** Moonraker host (IP or hostname), e.g. `192.168.1.50`. */
    val host: String = BuildConfig.MOONRAKER_HOST

    /** Moonraker port; defaults to Moonraker's conventional 7125 when unset/unparseable. */
    val port: Int = BuildConfig.MOONRAKER_PORT.toIntOrNull() ?: 7125

    /**
     * Optional API key. `null` when blank (the test-bed printer is OPEN / trusted-client, D-06),
     * non-null when a key is configured so the auth path (oneshot-token + `X-Api-Key`) engages.
     */
    val apiKey: String? = BuildConfig.MOONRAKER_API_KEY.takeIf { it.isNotBlank() }

    /** REST base, e.g. `http://192.168.1.50:7125` (cleartext per D-10/D-11). */
    val httpBase: String get() = "http://$host:$port"

    /** WebSocket URL, e.g. `ws://192.168.1.50:7125/websocket`. */
    val wsUrl: String get() = "ws://$host:$port/websocket"
}
