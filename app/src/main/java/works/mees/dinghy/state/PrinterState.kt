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

    /**
     * Retained `temperature_sensor <name>` → temperature (°C) map, for the Standby glance metric
     * (Phase 16). Accumulated across partial diffs — update-on-present, RETAIN-on-absent: a
     * `notify_status_update` that omits a sensor object keeps that sensor's prior value rather than
     * dropping it. The preferred glance sensor is derived from THIS map by a pure stable selector
     * (`selectGlanceSensor`), never from the raw current diff — that is the partial-diff-flip fix.
     */
    val temperatureSensors: Map<String, Double> = emptyMap(),

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

    /**
     * `gcode_move.homing_origin[2]` — the applied Z offset (live babystep / `SET_GCODE_OFFSET`),
     * surfaced for the Print-Status babystep applied-offset readout (Phase 16 / SC-5). Null until
     * first reported (or when a short/garbage `homing_origin` array is received — null-safe walk).
     */
    val gcodeZOffset: Double? = null,

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

    /** `pause_resume.is_paused` — state-confirmation truth for pause/resume controls. */
    val pauseResumePaused: Boolean = false,

    /**
     * Staleness marker (D-03). True when the socket has dropped and the values above are the
     * last-known reading (shown dimmed, not blanked). Cleared on the post-reconnect resync (D-04).
     */
    val stale: Boolean = false,

    /** Always-observable connection lifecycle alongside the data (CONN-06). */
    val connection: ConnectionState = ConnectionState.Disconnected,

    // --- Phase-9 calibration live objects (CALIB-02..05) -----------------------------------------
    // Each is NULLABLE (null = the object was never present in a diff/snapshot) and surfaced verbatim
    // from the structured `notify_status_update` object, NOT console parsing (RESEARCH Pattern 1). The
    // reducer walks are null-safe — a garbage/missing field is SKIPPED (retained), never fatal.

    /** `screws_tilt_adjust` (CALIB-02 / D-03): per-screw turn results + error flag. Null until first seen. */
    val screwsTilt: ScrewsTiltObject? = null,

    /**
     * `z_tilt.applied` (CALIB-03 / Pitfall 2): SUCCESS flag — null = never run, false = running OR failed,
     * true = converged. NEVER infer "failed" from false alone (failure = the dispatcher's RpcError).
     */
    val zTiltApplied: Boolean? = null,

    /** `quad_gantry_level.applied` (CALIB-03 / D-02): same tri-aware success flag as [zTiltApplied]. */
    val qglApplied: Boolean? = null,

    /** `bed_mesh` (CALIB-04 / D-07): interpolated + probed grids, extents, active profile, saved profiles. */
    val bedMesh: BedMeshObject? = null,

    /** `manual_probe` (CALIB-05 / D-01): the interactive Z-calibrate session state. Null when never opened. */
    val manualProbe: ManualProbeObject? = null,
)

/**
 * `screws_tilt_adjust` live object (CALIB-02). [results] is keyed `screw1`..`screwN` (1-based index — join
 * to the `[screws_tilt_adjust]` config by index for labels, Pitfall 1). [error] is true if MAX_DEVIATION was
 * exceeded; [maxDeviation] is the MAX_DEVIATION arg float or null (it is null even after a run on the E5 —
 * done-detection keys off `error==false` + populated [results], NOT [maxDeviation]; 09-01 surprise #2).
 */
data class ScrewsTiltObject(
    val error: Boolean = false,
    val maxDeviation: Double? = null,
    /** screwN -> per-screw turn result. */
    val results: Map<String, ScrewResult> = emptyMap(),
)

/**
 * One screw's turn result. [adjust] is a CLOCK STRING `"MM:SS"` (09-01 surprise #1 — NOT a float; e.g.
 * `"00:07"`), [sign] is `"CW"`/`"CCW"`, [isBase] marks the reference screw (`adjust=="00:00"`). The
 * worst-screw math (parsing the clock string) is the 09-03 parser's job — the reducer carries it verbatim.
 */
data class ScrewResult(
    val z: Double? = null,
    val sign: String? = null,
    val adjust: String? = null,
    val isBase: Boolean = false,
)

/**
 * `bed_mesh` live object (CALIB-04). [profileName] is `""` when no mesh is ACTIVE (empty-state, Pitfall 4 —
 * SEPARATE from [profileNames] saved-list non-emptiness). [meshMin]/[meshMax] are JSON ARRAYS `[x,y]`
 * (Python tuple → array; 09-01 surprise #4), surfaced as `List<Double>`. [meshMatrix] (interpolated) and
 * [probedMatrix] (raw dots) are arrays-of-arrays. [profileNames] are the KEYS of the `profiles` dict.
 */
data class BedMeshObject(
    val profileName: String = "",
    val meshMin: List<Double>? = null,
    val meshMax: List<Double>? = null,
    val probedMatrix: List<List<Double>>? = null,
    val meshMatrix: List<List<Double>>? = null,
    val profileNames: List<String> = emptyList(),
)

/**
 * `manual_probe` live object (CALIB-05 / D-01). [isActive] drives the page's enable/disable (Pattern 3);
 * [zPosition] is the current bracketed Z; [zPositionLower]/[zPositionUpper] are the nudge brackets. All
 * nullable (the structured object reports null bounds before they're set; the CONSOLE line can read
 * `??????` — that `??????→null` parse is the 09-06 parser's job, not the reducer's).
 */
data class ManualProbeObject(
    val isActive: Boolean = false,
    val zPosition: Double? = null,
    val zPositionLower: Double? = null,
    val zPositionUpper: Double? = null,
)

/**
 * The `[screws_tilt_adjust]` CONFIG (D-04/D-06) — read ONCE at handshake from `configfile.settings`,
 * exposed via [PrinterStateStore.screwsTiltConfig]. Distinct from the live [ScrewsTiltObject] (turn
 * results): this carries the static screw COORDS + LABELS so the guided loop can map the live results'
 * 1-based `screwN` keys to a coordinate + name (Pitfall 1). Generic N-screw (no hardcoded count, D-04).
 */
data class ScrewConfig(
    /** Ordered 1-based screws (index 0 = `screw1`). */
    val screws: List<Screw> = emptyList(),
    /** `screw_thread` (e.g. `"CCW-M4"`) — informational; the live object already carries the turn math. */
    val screwThread: String? = null,
)

/** One configured leveling screw: its bed `[x, y]` coordinate + optional display label. */
data class Screw(
    val x: Double,
    val y: Double,
    val name: String? = null,
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
