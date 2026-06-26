package works.mees.jiib.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import works.mees.jiib.outputs.OutputDescriptor
import works.mees.jiib.systeminfo.ProcStatQuery
import works.mees.jiib.systeminfo.SystemInfo
import works.mees.jiib.ui.console.ConsoleLine
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Assembles the public [PrinterState] [StateFlow] from the spine's flows with TWO distinct emission
 * planes (review MEDIUM, STATE-03):
 *
 * - **High-rate plane (sampled / conflated to ~2-4 Hz):** numeric status that changes constantly —
 *   heater temperatures/power and toolhead/gcode position/progress. A burst within one [sampleMillis]
 *   window collapses to its latest value (latest-wins); intermediate samples are dropped. This is the
 *   only plane that is throttled.
 * - **Control plane (IMMEDIATE, never sampled):** [ConnectionState], [KlippyState], the [stale]/auth
 *   markers, and `print_stats.state` (print lifecycle) transitions. These propagate with no sampling
 *   delay so a klippy-shutdown / disconnect / print-start is never hidden behind a sample tick.
 *
 * The `gcode_response` line stream is a SEPARATE, un-throttled [SharedFlow] (Console/Phase 7 must see
 * every line) — it is NOT conflated here.
 *
 * On a socket drop the last-known values are RETAINED and [stale] flips true (D-03, [markStale]); only a
 * post-reconnect [seed] (`objects.query` snapshot) overwrites them and clears [stale] (D-04).
 *
 * Threading: status diffs are reduced synchronously on the calling (socket-collecting) coroutine; the
 * sampled flush runs on a single coroutine launched in [scope]. A control-plane write publishes
 * immediately AND captures any pending high-rate state so an immediate emission never loses a numeric
 * update.
 */
class PrinterStateStore(
    scope: CoroutineScope,
    private val sampleMillis: Long = DEFAULT_SAMPLE_MS,
) {
    // The authoritative accumulator: always the freshest fully-reduced state, mutated synchronously.
    @Volatile
    private var accumulator: PrinterState = PrinterState()

    private val pendingHighRate = AtomicBoolean(false)

    private val _printerState = MutableStateFlow(accumulator)
    /** The public single-source-of-truth state (high-rate conflated, control-plane immediate). */
    val printerState: StateFlow<PrinterState> = _printerState.asStateFlow()

    private val _capabilities = MutableStateFlow(Capabilities())
    /** Re-derived capabilities, exposed alongside state and refreshed each reconnect (STATE-02). */
    val capabilities: StateFlow<Capabilities> = _capabilities.asStateFlow()

    private val _gcodeResponses = MutableSharedFlow<String>(extraBufferCapacity = GCODE_BUFFER)
    /** The SEPARATE, un-throttled gcode-response line stream (no conflation; bounded buffer). */
    val gcodeResponses: SharedFlow<String> = _gcodeResponses.asSharedFlow()

    // ---- One-shot handshake reads (05-03) -------------------------------------------------------
    // These three are STATIC/capability-like reads landed ONCE per handshake (temperature_store +
    // configfile), NOT the throttled hot path. They are StateFlows (not @Volatile var) so holders
    // OBSERVE them and react the instant the one-shot read lands — "graph full immediately on connect"
    // and the real min-temp hint are driven by the data arriving, not by a later notify_status_update
    // diff (review: don't make connect-time fullness depend on a status diff). Each is written exactly
    // once at handshake and is best-effort (left at its null/empty default if the read fails).

    private val _minExtrudeTemp = MutableStateFlow<Float?>(null)
    /** `configfile.settings.extruder.min_extrude_temp` (EXTR-04 hint text); null if unreadable. */
    val minExtrudeTemp: StateFlow<Float?> = _minExtrudeTemp.asStateFlow()

    private val _maxExtrudeDistance = MutableStateFlow<Float?>(null)
    /** `configfile.settings.extruder.max_extrude_only_distance` (Extrude ceiling); null if unreadable. */
    val maxExtrudeDistance: StateFlow<Float?> = _maxExtrudeDistance.asStateFlow()

    private val _maxExtrudeVelocity = MutableStateFlow<Float?>(null)
    /** One-shot handshake read of `extruder.max_extrude_only_velocity` (mm/s). Null = unknown. */
    val maxExtrudeVelocity: StateFlow<Float?> = _maxExtrudeVelocity.asStateFlow()

    private val _probeZOffset = MutableStateFlow<Float?>(null)
    /** `configfile.settings.probe.z_offset` (the saved probe calibration — shown idle on Probe-Calibrate);
     *  null if the printer has no `[probe]` section (probe-less, Z_ENDSTOP_CALIBRATE) or the read fails. */
    val probeZOffset: StateFlow<Float?> = _probeZOffset.asStateFlow()

    private val _temperatureBackfill = MutableStateFlow<Map<String, FloatArray>>(emptyMap())
    /** Per-sensor `server.temperature_store` history (oldest→newest), seeds the graph on connect (G-1). */
    val temperatureBackfill: StateFlow<Map<String, FloatArray>> = _temperatureBackfill.asStateFlow()

    private val _heaterLimits = MutableStateFlow<Map<String, HeaterLimits>>(emptyMap())
    /** Per-heater static `configfile` min/max temp (one-shot at handshake, NOT the throttled hot path). */
    val heaterLimits: StateFlow<Map<String, HeaterLimits>> = _heaterLimits.asStateFlow()

    private val _systemInfo = MutableStateFlow<SystemInfo?>(null)
    /**
     * `machine.system_info` static host identity (SYS-01, Phase 20) — one-shot per handshake, NOT the
     * throttled hot path. Null until/unless the read lands; carries forward to late collectors. The
     * SystemInfoHolder forwards this off the SpineHandle.
     */
    val systemInfo: StateFlow<SystemInfo?> = _systemInfo.asStateFlow()

    private val _hostname = MutableStateFlow<String?>(null)
    /** `printer.info` hostname (2026-06-15) — one-shot per handshake, NOT the hot path. Null until read. */
    val hostname: StateFlow<String?> = _hostname.asStateFlow()

    private val _procStatQuery = MutableStateFlow<ProcStatQuery?>(null)
    /**
     * `machine.proc_stats` query result (SYS-02/03, Phase 20) — the ONLY source of throttled_state +
     * system_uptime (the 1 Hz push omits both). One-shot per handshake, NOT the throttled hot path.
     */
    val procStatQuery: StateFlow<ProcStatQuery?> = _procStatQuery.asStateFlow()

    private val _consoleBackfill = MutableStateFlow<List<ConsoleLine>>(emptyList())
    /**
     * `server.gcode_store` console-history snapshot (CONS-02 / D-02). Written on EVERY (re)connect and on
     * the `notify_klippy_ready` re-handshake; each write REPLACES the prior snapshot (Mainsail-parity
     * Option A) so disconnect-window lines in the server's still-growing buffer are recovered without any
     * append/dedup logic. A StateFlow (not SharedFlow) so the ConsoleHolder observes deterministic
     * on-connect fullness — the snapshot is the authoritative truth, not a transient event.
     */
    val consoleBackfill: StateFlow<List<ConsoleLine>> = _consoleBackfill.asStateFlow()

    private val _consoleBackfillFailed = MutableStateFlow(false)
    /**
     * True when the `server.gcode_store` read FAILED on the most recent (re)handshake (08-04, CONS-02 /
     * D-02). The handshake wraps that read in `runCatching` and otherwise leaves [consoleBackfill] at its
     * empty default, which is indistinguishable from a genuinely quiet console — this flag disambiguates
     * so the ConsoleScreen can surface the "History unavailable" notice instead of the "Console is quiet"
     * empty state. Reset to `false` on each successful backfill REPLACE (a later reconnect that succeeds
     * clears a prior failure).
     */
    val consoleBackfillFailed: StateFlow<Boolean> = _consoleBackfillFailed.asStateFlow()

    private val _macroBodies = MutableStateFlow<Map<String, String>>(emptyMap())
    /**
     * Macro-name (LOWERCASED, Moonraker's convention) → gcode body string, extracted from the SAME
     * one-shot `configfile` query that reads the extruder config (Pitfall 3 — no duplicate query).
     * Feeds the MACRO-02 parameter parser; null/empty when unreadable (degrades gracefully).
     */
    val macroBodies: StateFlow<Map<String, String>> = _macroBodies.asStateFlow()

    private val _macroDescriptions = MutableStateFlow<Map<String, String>>(emptyMap())
    /**
     * Macro-name (LOWERCASED) → Klipper `description:` docstring, from the SAME one-shot `configfile`
     * query (Pitfall 3 — no extra request). Only macros that declared a non-blank description appear.
     * Parallel to [macroBodies]; the MacroHolder combines both. Empty when unreadable/absent.
     */
    val macroDescriptions: StateFlow<Map<String, String>> = _macroDescriptions.asStateFlow()

    private val _screwsTiltConfig = MutableStateFlow<ScrewConfig?>(null)
    /**
     * The `[screws_tilt_adjust]` config (screw coords + names, D-04/D-06) read ONCE at handshake from the
     * SINGLE existing `configfile` query (Pitfall 3 — no duplicate query), modeled EXACTLY on the
     * [minExtrudeTemp] one-shot seam. Holders COMBINE it with the live `screws_tilt_adjust.results` to map
     * 1-based `screwN` results to labels (09-04). Best-effort: null when the section is absent/unreadable;
     * the screws-tilt page degrades to an index-labeled list (D-06 fallback). A StateFlow (not @Volatile)
     * so holders react the instant the read lands. Per RESEARCH Open-Q1 the 09-01 capture RESOLVED that
     * `screws_tilt_adjust.results` PERSISTS post-run, so 09-04 can read results via a post-hoc one-shot
     * `objects/query?screws_tilt_adjust` on the dispatch-completion edge if a subscribe diff is missed —
     * that belt-and-braces hook is the holder's (09-04) concern; the config one-shot here is its companion.
     */
    val screwsTiltConfig: StateFlow<ScrewConfig?> = _screwsTiltConfig.asStateFlow()

    private val _outputDescriptors = MutableStateFlow<List<OutputDescriptor>>(emptyList())
    /**
     * The discovered controllable outputs (Phase 19, SC-1) — the result of
     * [works.mees.jiib.outputs.OutputsGate.parseOutputs] over the SINGLE existing one-shot `configfile`
     * query (Pitfall 3 — no duplicate query), modeled on the [screwsTiltConfig] seam. Re-emitted on EVERY
     * (re)handshake (deterministic on-connect fullness, NOT the throttled hot path). CLEARED to empty on the
     * configfile read-FAILURE path AND before each re-read (clear-on-switch, T-19-04-04) so a printer switch
     * or a failed read NEVER leaves stale hardware controls visible. The D-10 drawer-tile gate
     * ([works.mees.jiib.di.AppContainer.outputsPresent]) derives off non-empty-ness here.
     */
    val outputDescriptors: StateFlow<List<OutputDescriptor>> = _outputDescriptors.asStateFlow()

    // ---- Phase-17 Fine-Tune reset baselines (TUNE-05 / D-16) ------------------------------------
    // Read ONCE at handshake from `configfile.settings.<section>` via the SAME existing one-shot
    // configfile query (Pitfall 3 — no extra query), modeled on the minExtrudeTemp seam. Long-press
    // reset re-sends the normal setter with these baselines so reset targets the PRINTER's config, not
    // a Jiib default. All nullable (default null) — when a baseline is null (e.g. firmware_retraction
    // on the dev printers, or any absent config section) the holder's reset for that tuner is a NO-OP
    // (REVIEW #3, enforced in 17-05); never dispatch a bare/invalid command.
    //
    // NOTE — three tuners deliberately have NO baseline StateFlow:
    //   • Speed % / Flow %  → reset is the protocol-neutral `M220 S100` / `M221 S100` (D-16); there is
    //     no config baseline to read (they are pure live overrides), handled in the holder/UI.
    //   • Part-cooling fan  → Klipper `[fan]` has NO persistent configured speed (only pin/PWM config);
    //     `configfile.settings.fan.*` carries no target. So the part-fan tile has NO reset affordance
    //     (RESEARCH A2 / Open-Q1) — owner confirms at UAT. There is intentionally no baselineFan* here.

    private val _baselineMaxVelocity = MutableStateFlow<Double?>(null)
    /** `configfile.settings.printer.max_velocity` reset baseline (D-16); null if unreadable. */
    val baselineMaxVelocity: StateFlow<Double?> = _baselineMaxVelocity.asStateFlow()

    private val _baselineMaxAccel = MutableStateFlow<Double?>(null)
    /** `configfile.settings.printer.max_accel` reset baseline (D-16); null if unreadable. */
    val baselineMaxAccel: StateFlow<Double?> = _baselineMaxAccel.asStateFlow()

    private val _baselineMinCruise = MutableStateFlow<Double?>(null)
    /** `configfile.settings.printer.minimum_cruise_ratio` reset baseline (RAW ratio, D-16); null if unreadable. */
    val baselineMinCruise: StateFlow<Double?> = _baselineMinCruise.asStateFlow()

    private val _baselineScv = MutableStateFlow<Double?>(null)
    /** `configfile.settings.printer.square_corner_velocity` reset baseline (D-16); null if unreadable. */
    val baselineScv: StateFlow<Double?> = _baselineScv.asStateFlow()

    private val _baselinePressureAdvance = MutableStateFlow<Double?>(null)
    /** `configfile.settings.extruder.pressure_advance` reset baseline (D-16); null if unreadable. */
    val baselinePressureAdvance: StateFlow<Double?> = _baselinePressureAdvance.asStateFlow()

    private val _baselineSmoothTime = MutableStateFlow<Double?>(null)
    /**
     * Smooth-time reset baseline (D-16) — read from the CONFIG key
     * `configfile.settings.extruder.pressure_advance_smooth_time` ⚠, NOT the live STATUS field
     * `smooth_time` (Pitfall 2 — config key ≠ status field). Null if unreadable.
     */
    val baselineSmoothTime: StateFlow<Double?> = _baselineSmoothTime.asStateFlow()

    private val _baselineRetractLength = MutableStateFlow<Double?>(null)
    /** `configfile.settings.firmware_retraction.retract_length` baseline (build-blind; null on dev printers). */
    val baselineRetractLength: StateFlow<Double?> = _baselineRetractLength.asStateFlow()

    private val _baselineRetractSpeed = MutableStateFlow<Double?>(null)
    /** `configfile.settings.firmware_retraction.retract_speed` baseline (build-blind; null on dev printers). */
    val baselineRetractSpeed: StateFlow<Double?> = _baselineRetractSpeed.asStateFlow()

    private val _baselineUnretractExtraLength = MutableStateFlow<Double?>(null)
    /** `configfile.settings.firmware_retraction.unretract_extra_length` baseline (build-blind; null on dev). */
    val baselineUnretractExtraLength: StateFlow<Double?> = _baselineUnretractExtraLength.asStateFlow()

    private val _baselineUnretractSpeed = MutableStateFlow<Double?>(null)
    /** `configfile.settings.firmware_retraction.unretract_speed` baseline (build-blind; null on dev printers). */
    val baselineUnretractSpeed: StateFlow<Double?> = _baselineUnretractSpeed.asStateFlow()

    init {
        // Sampled flush: every sampleMillis, publish the accumulator IF a high-rate update is pending.
        scope.launch {
            while (isActive) {
                delay(sampleMillis)
                if (pendingHighRate.compareAndSet(true, false)) {
                    _printerState.value = accumulator
                }
            }
        }
    }

    /**
     * Seed/overwrite the data state from an `objects.query` snapshot (D-04). Preserves the current
     * [ConnectionState] (the session owns it) but CLEARS the [stale] marker — fresh truth replaces any
     * retained last-known values. Published immediately (a resync is a control-plane event).
     */
    fun seed(snapshot: PrinterState) {
        val conn = accumulator.connection
        accumulator = snapshot.copy(connection = conn, stale = false)
        pendingHighRate.set(false)
        _printerState.value = accumulator
    }

    /**
     * Apply a `notify_status_update` diff. The fully-reduced state always updates the accumulator; if the
     * diff touched a CONTROL-PLANE field (`print_stats`, `webhooks`, or `toolhead.homed_axes`) it
     * publishes immediately, otherwise it is left for the next sampled flush (high-rate only).
     */
    fun onStatusDiff(diff: JsonObject) {
        accumulator = reduceDiff(accumulator, diff)
        if (touchesControlPlane(diff)) {
            pendingHighRate.set(false)
            _printerState.value = accumulator
        } else {
            pendingHighRate.set(true)
        }
    }

    /** Fold a `notify_klippy_*` method into [KlippyState] — control-plane, IMMEDIATE. */
    fun onKlippyMethod(method: String) {
        accumulator = applyKlippyMethod(accumulator, method)
        pendingHighRate.set(false)
        _printerState.value = accumulator
    }

    /** Set the connection lifecycle — control-plane, IMMEDIATE (CONN-06). */
    fun setConnectionState(state: ConnectionState) {
        accumulator = accumulator.copy(connection = state)
        pendingHighRate.set(false)
        _printerState.value = accumulator
    }

    /**
     * Mark the state stale on a socket drop (D-03): set [stale] true and the (typically Disconnected /
     * Error) connection state, RETAINING all last-known values. Control-plane, IMMEDIATE.
     */
    fun markStale(connection: ConnectionState) {
        accumulator = accumulator.copy(stale = true, connection = connection)
        pendingHighRate.set(false)
        _printerState.value = accumulator
    }

    /** Push a gcode-response line to the un-throttled stream (never conflated). */
    fun onGcodeLine(line: String) {
        _gcodeResponses.tryEmit(line)
    }

    /** Publish freshly-derived capabilities (re-run on every reconnect, STATE-02). */
    fun setCapabilities(capabilities: Capabilities) {
        _capabilities.value = capabilities
    }

    /** One-shot at handshake: the min-extrude-temp hint (05-03). NOT the throttled hot path. */
    fun setMinExtrudeTemp(value: Float?) {
        _minExtrudeTemp.value = value
    }

    /** One-shot at handshake: the max-extrude-only-distance ceiling (05-03). NOT the throttled hot path. */
    fun setMaxExtrudeDistance(value: Float?) {
        _maxExtrudeDistance.value = value
    }

    fun setMaxExtrudeVelocity(v: Float?) { _maxExtrudeVelocity.value = v }

    /** One-shot at handshake: the saved probe `z_offset` (09-07 Probe-Calibrate idle readout). */
    fun setProbeZOffset(value: Float?) {
        _probeZOffset.value = value
    }

    /** One-shot at handshake: per-sensor temperature_store backfill (05-03). NOT the throttled hot path. */
    fun setTemperatureBackfill(backfill: Map<String, FloatArray>) {
        _temperatureBackfill.value = backfill
    }

    /** One-shot at handshake: per-heater configfile min/max temp. Mirrors [setTemperatureBackfill]. */
    fun setHeaterLimits(limits: Map<String, HeaterLimits>) {
        _heaterLimits.value = limits
    }

    /** One-shot at (re)handshake: `machine.system_info` host identity (SYS-01). NOT the throttled hot path. */
    fun setSystemInfo(info: SystemInfo) {
        _systemInfo.value = info
    }

    /** One-shot at (re)handshake: the `printer.info` hostname (used to seed an un-named profile's name). */
    fun setHostname(value: String?) {
        _hostname.value = value
    }

    /** One-shot at (re)handshake: `machine.proc_stats` throttle+uptime (SYS-02/03). NOT the throttled hot path. */
    fun setProcStatQuery(query: ProcStatQuery) {
        _procStatQuery.value = query
    }

    /**
     * One-shot at (re)handshake: REPLACE the console-history backfill snapshot from `server.gcode_store`
     * (08-04, CONS-02 / D-02). A fresh snapshot supersedes prior contents — this is the Mainsail-parity
     * disconnect-window recovery. NOT the throttled hot path.
     */
    fun setGcodeBackfill(lines: List<ConsoleLine>) {
        _consoleBackfill.value = lines
        // A successful read clears any prior failure (a later reconnect that succeeds recovers).
        _consoleBackfillFailed.value = false
    }

    /**
     * One-shot at (re)handshake: flag that the `server.gcode_store` read FAILED. Called from the
     * handshake's `runCatching` failure branch so the ConsoleScreen surfaces "History unavailable"
     * instead of the "Console is quiet" empty state (08-04, CONS-02 / D-02 — WR-04).
     */
    fun setGcodeBackfillFailed() {
        _consoleBackfillFailed.value = true
    }

    /**
     * One-shot at (re)handshake: macro-name (lowercased) → gcode body map, from the single `configfile`
     * query (08-04, MACRO-02). NOT the throttled hot path.
     */
    fun setMacroBodies(bodies: Map<String, String>) {
        _macroBodies.value = bodies
    }

    /** One-shot at (re)handshake: macro-name (lowercased) → description map, from the configfile query. */
    fun setMacroDescriptions(descriptions: Map<String, String>) {
        _macroDescriptions.value = descriptions
    }

    /**
     * One-shot at (re)handshake: the `[screws_tilt_adjust]` config (screw coords + names, D-04/D-06), from
     * the single `configfile` query (Pitfall 3). Null when the section is absent/unreadable (best-effort).
     * NOT the throttled hot path (09-02 spine seam; the session wires the read in 09-04).
     */
    fun setScrewsTiltConfig(config: ScrewConfig?) {
        _screwsTiltConfig.value = config
    }

    /**
     * One-shot at (re)handshake: the discovered controllable outputs (Phase 19, SC-1) from the single
     * `configfile` query (Pitfall 3). Called with the parsed list on success, and with `emptyList()` on the
     * read-FAILURE path AND before each re-read (clear-on-switch, T-19-04-04). NOT the throttled hot path.
     */
    fun setOutputDescriptors(descriptors: List<OutputDescriptor>) {
        _outputDescriptors.value = descriptors
    }

    // ---- Phase-17 Fine-Tune reset-baseline setters (TUNE-05 / D-16) -----------------------------
    // One-shot at handshake from the SAME configfile query (Pitfall 3). NOT the throttled hot path.

    /** One-shot at handshake: max-velocity reset baseline (`printer.max_velocity`). */
    fun setBaselineMaxVelocity(value: Double?) { _baselineMaxVelocity.value = value }

    /** One-shot at handshake: max-accel reset baseline (`printer.max_accel`). */
    fun setBaselineMaxAccel(value: Double?) { _baselineMaxAccel.value = value }

    /** One-shot at handshake: min-cruise-ratio reset baseline (`printer.minimum_cruise_ratio`, RAW ratio). */
    fun setBaselineMinCruise(value: Double?) { _baselineMinCruise.value = value }

    /** One-shot at handshake: square-corner-velocity reset baseline (`printer.square_corner_velocity`). */
    fun setBaselineScv(value: Double?) { _baselineScv.value = value }

    /** One-shot at handshake: pressure-advance reset baseline (`extruder.pressure_advance`). */
    fun setBaselinePressureAdvance(value: Double?) { _baselinePressureAdvance.value = value }

    /**
     * One-shot at handshake: smooth-time reset baseline — read from the CONFIG key
     * `extruder.pressure_advance_smooth_time` ⚠ (NOT the status field `smooth_time`, Pitfall 2).
     */
    fun setBaselineSmoothTime(value: Double?) { _baselineSmoothTime.value = value }

    /** One-shot at handshake: FW-retraction retract_length baseline (build-blind; null on dev printers). */
    fun setBaselineRetractLength(value: Double?) { _baselineRetractLength.value = value }

    /** One-shot at handshake: FW-retraction retract_speed baseline (build-blind; null on dev printers). */
    fun setBaselineRetractSpeed(value: Double?) { _baselineRetractSpeed.value = value }

    /** One-shot at handshake: FW-retraction unretract_extra_length baseline (build-blind; null on dev). */
    fun setBaselineUnretractExtraLength(value: Double?) { _baselineUnretractExtraLength.value = value }

    /** One-shot at handshake: FW-retraction unretract_speed baseline (build-blind; null on dev printers). */
    fun setBaselineUnretractSpeed(value: Double?) { _baselineUnretractSpeed.value = value }

    // ---- internals ------------------------------------------------------------------------------

    /** A diff touches the control plane if it carries a discrete lifecycle field that must be immediate. */
    private fun touchesControlPlane(diff: JsonObject): Boolean {
        if (diff.containsKey("print_stats")) return true
        if (diff.containsKey("webhooks")) return true
        val toolhead = diff["toolhead"] as? JsonObject
        if (toolhead != null && toolhead.containsKey("homed_axes")) return true
        if (diff.containsKey("stepper_enable")) return true
        return false
    }

    companion object {
        /** ~4 Hz default sampling cadence for the high-rate plane (STATE-03). */
        const val DEFAULT_SAMPLE_MS = 250L
        private const val GCODE_BUFFER = 256
    }
}
