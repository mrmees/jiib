package works.mees.dinghy.state

import works.mees.dinghy.net.ConnectionError

/**
 * Toolkit-agnostic, immutable single-source-of-truth printer state — the public contract the
 * connection spine (Wave 2/3) produces as a `StateFlow` and that BOTH Compose and classic Views
 * later consume (ADR 0001 hybrid). This is the HEADLESS spine: deliberately a PLAIN Kotlin
 * `data class` with NO Compose stability annotations — the Compose-stability wrapper, if any,
 * lives in the UI phase, not here.
 *
 * Diff-merge semantics (STATE-01, Pitfall 1): the reducer (Wave 2) seeds this from the
 * `objects.subscribe`/`objects.query` snapshot, then deep-MERGES each `notify_status_update` diff —
 * it never replaces. On a socket drop the last-known values are RETAINED and [stale] flips true
 * (D-03); the post-reconnect `objects.query` overwrites them with truth and clears [stale] (D-04).
 *
 * Fields mirror the v1 subscribe set (02-RESEARCH § "Code Examples"): heater/extruder temps+targets,
 * toolhead position/homed axes, gcode_move factors, print_stats lifecycle, and progress.
 */
data class PrinterState(
    /** Klipper host lifecycle (STATE-04) — first-class because it drives routing in Phase 3. */
    val klippyState: KlippyState = KlippyState.Disconnected,

    /** Print-job lifecycle (`print_stats.state`) — distinct axis from [klippyState] (STATE-04). */
    val printState: PrintState = PrintState.Standby,

    /** Per-heater readings keyed by object name (`heater_bed`, `extruder`, `extruder1`, ...). */
    val heaters: Map<String, HeaterState> = emptyMap(),

    /** Toolhead position `[x, y, z, e]` (mm); null until first snapshot. */
    val toolheadPosition: List<Double>? = null,

    /** Homed axes string from `toolhead.homed_axes` (e.g. "xyz", "" when none). */
    val homedAxes: String = "",

    /** `gcode_move.speed_factor` (1.0 = 100%). */
    val speedFactor: Double = 1.0,

    /** `gcode_move.extrude_factor` (1.0 = 100%). */
    val extrudeFactor: Double = 1.0,

    /** Currently printing filename from `print_stats.filename` (empty when idle). */
    val printFilename: String = "",

    /** Print progress 0.0..1.0 from `virtual_sdcard.progress` / `display_status.progress`. */
    val progress: Double = 0.0,

    /**
     * Staleness marker (D-03). True when the socket has dropped and the values above are the
     * last-known reading (shown dimmed, not blanked). Cleared on the post-reconnect resync (D-04).
     */
    val stale: Boolean = false,

    /** Always-observable connection lifecycle alongside the data (CONN-06). */
    val connection: ConnectionState = ConnectionState.Disconnected,
)

/** A single heater's live readings (current temp, target, and heater power 0.0..1.0). */
data class HeaterState(
    val temperature: Double = 0.0,
    val target: Double = 0.0,
    val power: Double = 0.0,
)

/**
 * Klipper HOST lifecycle (STATE-04) — from `server.info.klippy_state` / `webhooks.state`, kept live
 * by `notify_klippy_ready` / `notify_klippy_shutdown` / `notify_klippy_disconnected`.
 */
enum class KlippyState { Disconnected, Startup, Ready, Error, Shutdown }

/** Print-job lifecycle from `print_stats.state` (STATE-04 / drives job-status routing later). */
enum class PrintState { Standby, Printing, Paused, Complete, Error, Cancelled }

/**
 * Connection lifecycle (CONN-06). FIVE states with a deliberate split between socket-open and
 * fully-usable (review HIGH on Connected timing, CONN-06):
 *
 * - [Connecting] — attempting to open the socket.
 * - [Syncing] — socket is OPEN but the `identify → list → query → subscribe` resync has NOT yet
 *   completed; consumers must NOT treat state as fresh/subscribed here.
 * - [Connected] — resync done AND subscribed; state is fresh and live diffs are flowing.
 * - [Disconnected] — no socket (idle or dropped); last-known values may still be shown stale (D-03).
 * - [Error] — a typed failure; [Error.reason] is the [ConnectionError] model (CONN-02/D-06).
 */
sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data object Syncing : ConnectionState
    data object Connected : ConnectionState
    data object Disconnected : ConnectionState
    data class Error(val reason: ConnectionError) : ConnectionState
}
