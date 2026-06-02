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

    /**
     * Human-readable Klippy/Moonraker reason for the current non-ready state, from
     * `webhooks.state_message` (e.g. "Klipper reports: SHUTDOWN — MCU error"). Null when none is
     * known (or after recovery to Ready). The Splash (SHELL-05/04-05) surfaces this verbatim so the
     * trapped user sees the REAL reason, not just an enum-derived label (review #8). Server-provided
     * text rendered as plain text only — never interpreted (T-04-05-I).
     */
    val klippyStateMessage: String? = null,

    /** Print-job lifecycle (`print_stats.state`) — distinct axis from [klippyState] (STATE-04). */
    val printState: PrintState = PrintState.Standby,

    /** Per-heater readings keyed by object name (`heater_bed`, `extruder`, `extruder1`, ...). */
    val heaters: Map<String, HeaterState> = emptyMap(),

    /** Toolhead position `[x, y, z, e]` (mm); null until first snapshot. */
    val toolheadPosition: List<Double>? = null,

    /**
     * `gcode_move.gcode_position` `[X, Y, Z, E]` (mm) — the offsets-stripped, USER-FACING coordinates
     * the Move panel displays/jogs against (MOVE-04 / RESEARCH Pitfall 1). NOT [toolheadPosition]
     * (which is raw kinematic position including offsets). Null until first snapshot.
     */
    val gcodePosition: List<Double>? = null,

    /** Homed axes string from `toolhead.homed_axes` (e.g. "xyz", "" when none). */
    val homedAxes: String = "",

    /** `gcode_move.speed_factor` (1.0 = 100%). */
    val speedFactor: Double = 1.0,

    /** `gcode_move.extrude_factor` (1.0 = 100%). */
    val extrudeFactor: Double = 1.0,

    /** Currently printing filename from `print_stats.filename` (empty when idle). */
    val printFilename: String = "",

    /** `print_stats.print_duration` (s) — actual extruding time; the ELAPSED readout (catalog). */
    val printDuration: Double = 0.0,

    /** `print_stats.total_duration` (s) — wall time incl. heating/pauses (catalog). */
    val totalDuration: Double = 0.0,

    /** `print_stats.filament_used` (mm) — filament extruded this job so far (catalog). */
    val filamentUsed: Double = 0.0,

    /**
     * `print_stats.info.current_layer` — NULLABLE: null when idle/standby or when the slicer never
     * called `SET_PRINT_STATS_INFO` (confirmed both ways in docs/moonraker-capabilities.md). The UI
     * falls back to `floor((Z − first_layer_height)/layer_height)+1` or shows "—"; never fabricates 0.
     */
    val currentLayer: Int? = null,

    /** `print_stats.info.total_layer` — NULLABLE (same caveat as [currentLayer]); else metadata layer_count. */
    val totalLayer: Int? = null,

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

    /**
     * `extruder.can_extrude` — extruder objects only; true when the current temp ≥ `min_extrude_temp`
     * (EXTR-04 cold-extrude gate / D-07). Non-extruder heaters leave the default `false`. Defaults
     * false as a FAIL-SAFE: a printer that never reports `can_extrude` reads as "cannot extrude",
     * disabling the extrude control rather than risking a cold extrude (T-05-01-Safety).
     */
    val canExtrude: Boolean = false,
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
