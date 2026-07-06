package works.mees.jiib.ui.temperature

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import works.mees.jiib.render.RingBuffer
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.HeaterLimits
import works.mees.jiib.state.HeaterState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore
import works.mees.jiib.ui.settings.TraceStylePrefs

/**
 * Toolkit-agnostic holder for the Temperature panel (TEMP-01/04). It transforms the store's
 * ALREADY-throttled [PrinterStateStore.printerState] (plus the one-shot
 * [PrinterStateStore.temperatureBackfill] StateFlow landed at handshake by 05-03) into the three
 * shapes the Compose `TemperatureScreen` consumes:
 *
 *  1. [series] — one bounded [RingBuffer] snapshot per DRAWN sensor (trace order), feeding the 05-04
 *     multi-trace GraphView. Each ring is seeded oldest→newest from the backfill on its first
 *     emission, then each `printerState` tick appends that sensor's live current temperature.
 *  2. [setpoints] — each drawn sensor's live target (or `null` when the target ≤ 0 / off), feeding the
 *     per-trace dashed setpoint line in 05-04. Mirrors the PrintStatusHolder heaterCell off-rule.
 *  3. [legend] — a [SensorReadout] per drawn sensor (name/label/current/target) for the Focus region.
 *
 * ## Monitored-set resolution (DYNAMIC, capability-authoritative, uncapped)
 * The monitored set = ALL heaters (always) ∪ the user-selected `temperature_sensor` objects, in display
 * order: heaters first then sensors, each group alphabetical by object name. Heaters come from
 * [PrinterStateStore.capabilities] (falling back to live `state.heaters.keys` when caps is empty);
 * sensors come from [selectedSensors] (seeded from persisted prefs, toggled via [setSensorSelected]).
 * The set is DYNAMIC: it changes when capabilities arrive OR the user toggles a sensor, and the
 * per-trace rings are rebuilt (and the backfill re-seeded) on change. Heaters are adjustable and carry
 * a setpoint; selected sensors are read-only (`isAdjustable = false`, `target = null`) and read their
 * live value from [PrinterState.temperatureSensors]. The old ≤3-trace cap is RETIRED.
 *
 * ## No second throttle (Phase-4 lesson, mirrors PrintStatusHolder / MoveHolder)
 * The holder consumes the store's conflated flow directly (the store samples the high-rate plane at
 * `DEFAULT_SAMPLE_MS = 250`). It adds NO `sample`/`debounce`/`delay` of its own.
 *
 * Plain Kotlin (no Compose annotations) so it is host-unit-testable; mirrors the [PrinterState] /
 * [PrinterStateStore] StateFlow discipline (the only new piece is the per-sensor RingBuffers).
 *
 * @param scope the lifecycle scope the collectors run on (the UI host supplies it).
 * @param store the already-assembled Phase-2/3 spine; the holder CONSUMES it, never opens a session.
 * @param traceStylePrefs optional DataStore persistence for per-sensor trace color + visibility
 *   (D-14, Plan 26-03). When null (tests without a DataStore) the holder uses in-memory-only maps;
 *   production passes the process-lifetime [works.mees.jiib.di.AppContainer]-held instance so
 *   colors/visibility survive process restarts. All writes route through the process-lifetime
 *   `writeScope` (supplied by AppContainer via [setTraceColor]/[setTraceVisibility] callers);
 *   the holder itself ONLY reads prefs here (seeding) and updates in-memory state — callers
 *   are responsible for routing writes to the `writeScope`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TemperatureHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
    traceStylePrefs: TraceStylePrefs? = null,
    // The active printer profile id (per-printer trace-config scoping). When provided alongside
    // [traceStylePrefs], trace colors/visibility/selection seed from that profile and re-seed on switch.
    activeProfileId: Flow<String?>? = null,
) {
    /**
     * The monitored object names (= drawn traces), in display order: all heaters first (alphabetical),
     * then the user-selected `temperature_sensor` objects (alphabetical). DYNAMIC — recomputed by the
     * combine collector whenever capabilities/state/selection change; the rings are rebuilt on change.
     */
    @Volatile
    private var drawn: List<String> = emptyList()

    /** The subset of [drawn] that are heaters (adjustable, have a setpoint). Tracked for source routing. */
    @Volatile
    private var heaterSet: Set<String> = emptySet()

    /** One rolling-window ring per monitored sensor (parallel to [drawn]); rebuilt when [drawn] changes. */
    private var rings: List<RingBuffer> = emptyList()

    /**
     * Guard so the one-shot backfill seeds each ring exactly once even if its StateFlow re-emits.
     * Reset to false whenever the monitored set changes (new rings) so the backfill can re-seed.
     */
    @Volatile
    private var seeded = false

    private val _series = MutableStateFlow<List<FloatArray>>(emptyList())
    /** One bounded snapshot per drawn trace (oldest→newest), in trace order — feeds the multi-trace graph. */
    val series: StateFlow<List<FloatArray>> = _series.asStateFlow()

    private val _setpoints = MutableStateFlow<List<Float?>>(emptyList())
    /** Each drawn sensor's live target (null when off / target ≤ 0) — feeds the dashed setpoint line. */
    val setpoints: StateFlow<List<Float?>> = _setpoints.asStateFlow()

    private val _legend = MutableStateFlow<List<SensorReadout>>(emptyList())
    /** Per-drawn-sensor current/target readout for the Focus region. */
    val legend: StateFlow<List<SensorReadout>> = _legend.asStateFlow()

    private val _yRange = MutableStateFlow(DEFAULT_GRAPH_RANGE)
    /**
     * The DYNAMIC graph Y-range (05 UI tweak) — fits all drawn sensor history + active setpoints with
     * absolute padding, bounds rounded outward to a nice step, clamped to the heater envelope. Recomputed
     * each publish; feeds the GraphView (replaces the old fixed 0..350). See [computeGraphYRange].
     */
    val yRange: StateFlow<ClosedFloatingPointRange<Float>> = _yRange.asStateFlow()

    /** Per-heater min/max temperature envelope, forwarded from the store (configfile handshake, Task 2/3). */
    val heaterLimits: StateFlow<Map<String, HeaterLimits>> = store.heaterLimits

    // ---- Selected temperature_sensor objects (monitored-set rework, Task 6) ------------------------

    private val _selectedSensors = MutableStateFlow<Set<String>>(emptySet())
    /**
     * The user-selected `temperature_sensor <name>` object names that join the monitored set on top of
     * the always-monitored heaters. Seeded from [TraceStylePrefs.selectedSensors] (Task 4) and updated
     * in-memory by [setSensorSelected]; the CALLER also persists via `AppContainer.setSensorSelected`.
     */
    val selectedSensors: StateFlow<Set<String>> = _selectedSensors.asStateFlow()

    /** In-memory update; the CALLER also persists via AppContainer.setSensorSelected (writeScope). */
    fun setSensorSelected(sensorName: String, selected: Boolean) {
        _selectedSensors.value =
            if (selected) _selectedSensors.value + sensorName else _selectedSensors.value - sensorName
    }

    // ---- D-14: per-sensor trace color + visibility (Plan 26-03) ------------------------------------

    private val _traceColors = MutableStateFlow<Map<String, Color>>(emptyMap())
    /**
     * Per-sensor trace color, keyed by Moonraker object name (e.g. "extruder"). An absent entry
     * means the sensor uses its default [works.mees.jiib.theme.SeriesColor.seriesColor] token.
     * Seeded from [works.mees.jiib.ui.settings.TraceStylePrefs] at construction (Task 2);
     * updated by [setTraceColor].
     */
    val traceColors: StateFlow<Map<String, Color>> = _traceColors.asStateFlow()

    private val _traceVisibility = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    /**
     * Per-sensor trace visibility, keyed by Moonraker object name. An absent entry means the
     * sensor IS visible (absent-means-visible convention). Updated by [setTraceVisibility].
     */
    val traceVisibility: StateFlow<Map<String, Boolean>> = _traceVisibility.asStateFlow()

    /**
     * Update the in-memory trace color for a sensor (D-14). The CALLER is responsible for also
     * persisting via [works.mees.jiib.di.AppContainer.setTraceColor], which routes through the
     * process-lifetime `writeScope` — never `rememberCoroutineScope()` (P14 write-scope trap).
     */
    fun setTraceColor(sensorName: String, color: Color) {
        _traceColors.value = _traceColors.value + (sensorName to color)
    }

    /**
     * Update the in-memory trace visibility for a sensor (D-14). The CALLER is responsible for also
     * persisting via [works.mees.jiib.di.AppContainer.setTraceVisibility], which routes through
     * the process-lifetime `writeScope` — never `rememberCoroutineScope()`.
     */
    fun setTraceVisibility(sensorName: String, visible: Boolean) {
        _traceVisibility.value = _traceVisibility.value + (sensorName to visible)
    }

    init {
        // Seed trace colors + visibility from DataStore (D-14) on first emission, if prefs provided.
        // We collect the prefs Flows into the holder's in-memory maps so the UI always reads from
        // the StateFlows (no direct DataStore access from the screen). The seed runs once on first
        // emission; subsequent prefs updates (e.g. from another source) also reflect automatically.
        // SCOPED to the active printer profile (per-printer trace config — pre-merge review fix
        // 2026-06-14). flatMapLatest on the active profile id re-seeds when the user switches printers;
        // a null profile clears to defaults. Without a profile id (tests/in-memory) there is no seeding.
        if (traceStylePrefs != null && activeProfileId != null) {
            scope.launch {
                activeProfileId.flatMapLatest { pid ->
                    if (pid == null) flowOf(emptyMap()) else traceStylePrefs.traceColors(pid)
                }.collect { persisted -> _traceColors.value = persisted.mapValues { (_, argb) -> Color(argb) } }
            }
            scope.launch {
                activeProfileId.flatMapLatest { pid ->
                    if (pid == null) flowOf(emptyMap<String, Boolean>()) else traceStylePrefs.traceVisibility(pid)
                }.collect { _traceVisibility.value = it }
            }
            scope.launch {
                activeProfileId.flatMapLatest { pid ->
                    if (pid == null) flowOf(emptySet<String>()) else traceStylePrefs.selectedSensors(pid)
                }.collect { _selectedSensors.value = it }
            }
        }
        // Visibility collector: rescale the Y-range whenever a trace is hidden/shown so a hidden trace
        // stops dragging the axis bounds immediately — even when the graph is frozen (disconnected) and
        // no new sample is arriving to re-run publishSeries. Recomputes from the last-published series.
        scope.launch {
            _traceVisibility.collect { recomputeYRange() }
        }

        // Backfill collector: seed each CURRENT ring oldest→newest the instant the one-shot read lands
        // (05-03). It does NOT resolve the monitored set — that lives only in the combine collector
        // below. It seeds ONCE per ring set (guarded by [seeded], reset on set change) so a re-emission
        // does not re-prepend, yet a NEW set (sensor toggle / late caps) gets re-seeded.
        scope.launch {
            store.temperatureBackfill.collect { backfill ->
                if (backfill.isEmpty() || seeded || rings.isEmpty()) return@collect
                drawn.forEachIndexed { i, name ->
                    backfill[name]?.forEach { rings[i].push(it) }
                }
                seeded = true
                publishSeries()
            }
        }

        // Live collector: combine the store's ALREADY-throttled state + capabilities + selection so the
        // monitored set is DYNAMIC. NO second sample/debounce/delay (Phase-4 lesson). When the resolved
        // set changes (caps arrive, sensor toggled), rebuild the rings and allow the backfill to re-seed.
        scope.launch {
            combine(store.printerState, store.capabilities, _selectedSensors) { state, caps, sel ->
                Triple(state, caps, sel)
            }.collect { (state, caps, sel) ->
                val resolved = resolveMonitored(state, caps, sel)
                if (resolved != drawn) {
                    drawn = resolved
                    heaterSet =
                        (if (caps.heaters.isNotEmpty()) caps.heaters else state.heaters.keys.toList()).toSet()
                    rings = resolved.map { RingBuffer() }
                    // Seed the new rings IMMEDIATELY from the one-shot backfill the store already holds —
                    // don't wait for a re-emit, or a mid-session sensor add starts with empty graph
                    // history (pre-merge review fix 2026-06-14). If backfill hasn't landed yet, leave
                    // `seeded=false` so the backfill collector seeds when it arrives.
                    val backfill = store.temperatureBackfill.value
                    if (backfill.isNotEmpty()) {
                        drawn.forEachIndexed { i, name -> backfill[name]?.forEach { rings[i].push(it) } }
                        seeded = true
                    } else {
                        seeded = false
                    }
                }
                // Set legend + setpoints BEFORE publishSeries so the dynamic yRange (computed in
                // publishSeries) sees this tick's active setpoints, not the previous tick's.
                _legend.value = drawn.map { name -> readout(state, name) }
                _setpoints.value = drawn.map { name -> setpointOf(state, name) }
                if (rings.isNotEmpty()) {
                    var pushed = false
                    drawn.forEachIndexed { i, name ->
                        liveValue(state, name)?.let { rings[i].push(it); pushed = true }
                    }
                    // Only republish when a real sample landed — a caps-only emission (state still empty)
                    // must NOT push a phantom 0 (old behavior: the live collector ran only on printerState).
                    if (pushed) publishSeries()
                }
            }
        }
    }

    /**
     * The monitored object names: all heaters first (capabilities-authoritative, falling back to live
     * state keys when caps is empty), then the user-[selected] `temperature_sensor` objects. Each group
     * sorted alphabetically by object name. Uncapped (the old ≤3-trace limit is retired).
     */
    private fun resolveMonitored(state: PrinterState, caps: Capabilities, selected: Set<String>): List<String> {
        val heaterNames =
            (if (caps.heaters.isNotEmpty()) caps.heaters else state.heaters.keys.toList()).sorted()
        // Filter selected sensors to objects that actually exist on THIS printer (pre-merge review fix):
        // a stale selection (sensor removed from config, or a cross-printer carryover) must not render as
        // a ghost 0° row. Only filter once caps are known — an empty caps.objects (pre-handshake) keeps
        // the selection intact so rows don't flicker out before capabilities land.
        val sensorNames =
            (if (caps.objects.isNotEmpty()) selected.filter { it in caps.objects } else selected).sorted()
        return heaterNames + sensorNames
    }

    private fun publishSeries() {
        val snaps = rings.map { it.snapshot() }
        _series.value = snaps
        recomputeYRange(snaps)
    }

    /**
     * Recompute the dynamic graph Y-range from the currently-VISIBLE traces only (bug fix): a trace
     * hidden via [setTraceVisibility] must not drag the axis bounds, since its line isn't drawn. The
     * visible filter mirrors the screen's `VisibleTraces` (absent-in-map = visible). Called both from
     * [publishSeries] (each new sample) and from the visibility collector below (so toggling
     * visibility rescales the axis immediately, even while the graph is frozen/disconnected).
     *
     * @param snaps the ring snapshots, parallel to [drawn]/[setpoints]; defaults to the last published
     *   [series] so the visibility collector can rescale without a fresh sample.
     */
    private fun recomputeYRange(snaps: List<FloatArray> = _series.value) {
        val vis = _traceVisibility.value
        val sp = _setpoints.value
        val keep = drawn.indices.filter { vis[drawn[it]] ?: true }
        _yRange.value = computeGraphYRange(
            keep.mapNotNull { snaps.getOrNull(it) },
            keep.map { sp.getOrNull(it) },
        )
    }

    /**
     * Live value for a monitored object: heater → its temperature; sensor → its temperatureSensors
     * entry. Returns null when the object has no live reading YET (absent from the map) so the live
     * collector skips it — a caps-before-state emission must not push a phantom 0 (the old live
     * collector ran only on printerState). A present-but-cold heater (0.0) still pushes 0.
     */
    private fun liveValue(state: PrinterState, name: String): Float? =
        if (name in heaterSet) state.heaters[name]?.temperature?.toFloat()
        else state.temperatureSensors[name]?.toFloat()

    /**
     * A heater's live setpoint, or null when off / target ≤ 0 (PrintStatusHolder rule). Sensors are not
     * adjustable and always return null.
     */
    private fun setpointOf(state: PrinterState, name: String): Float? {
        if (name !in heaterSet) return null
        val h: HeaterState = state.heaters[name] ?: return null
        return if (h.target > 0.0) h.target.toFloat() else null
    }

    private fun readout(state: PrinterState, name: String): SensorReadout {
        // isAdjustable = heater (has a setpoint); selected temperature_sensor entries are read-only.
        val adjustable = name in heaterSet
        val current =
            if (adjustable) (state.heaters[name]?.temperature ?: 0.0) else (state.temperatureSensors[name] ?: 0.0)
        val target = if (adjustable) (state.heaters[name]?.target?.takeIf { it > 0.0 }) else null
        return SensorReadout(
            name = name, label = label(name), current = current, target = target, isAdjustable = adjustable,
        )
    }
}

/**
 * Title-cases a raw token: lowercases, splits on whitespace + underscore, capitalizes each word.
 * e.g. `mcu_temp` → "Mcu Temp", `chamber` → "Chamber". Internal so the same-package
 * TemperatureScreen sensor picker can reuse it (DRY).
 */
internal fun titleCase(raw: String): String =
    raw.lowercase()
        .split(Regex("[\\s_]+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/**
 * Short Title-Case label for a heater object name (owner 2026-06-17 — was UPPERCASE):
 * - `extruder` → "Nozzle"; `extruder1`/`extruder2` → "Nozzle 1"/… ; `heater_bed` → "Bed";
 * - `heater_generic <name>` / `temperature_sensor <name>` → title-cased name; else → title-cased.
 */
private fun label(objectName: String): String = when {
    objectName == "extruder" -> "Nozzle"
    objectName.startsWith("extruder") -> "Nozzle ${objectName.removePrefix("extruder")}"
    objectName == "heater_bed" -> "Bed"
    objectName.startsWith("heater_generic ") -> titleCase(objectName.removePrefix("heater_generic "))
    objectName.startsWith("temperature_sensor ") -> titleCase(objectName.removePrefix("temperature_sensor "))
    else -> titleCase(objectName)
}

/**
 * A single drawn-sensor readout for the Temperature Focus legend: its Moonraker object [name], a short
 * uppercase [label], the live [current] temperature, an optional [target] (null when the heater is
 * off / has no setpoint), and [isAdjustable] (D-11: true for heaters, false for read-only
 * temperature_sensor entries — future; v1 all drawn sensors are heaters so always true).
 */
data class SensorReadout(
    val name: String,
    val label: String,
    val current: Double,
    val target: Double?,
    val isAdjustable: Boolean = true,
)

/** Absolute Y padding (°C) added below the min and above the max before rounding (05 UI tweak). */
private const val GRAPH_PAD = 5f

/** Bounds are rounded OUTWARD to this step (°C) so the axis + labels don't twitch per sample. */
private const val GRAPH_STEP = 5f

/** Hard envelope the dynamic range clamps into (the setHeater 0..350 clamp domain). */
private const val GRAPH_FLOOR = 0f
private const val GRAPH_CEIL = 350f

/** Range shown before any data lands — a calm low band, not the full 0..350. */
val DEFAULT_GRAPH_RANGE: ClosedFloatingPointRange<Float> = 0f..40f

/**
 * Pure, host-testable dynamic Y-range for the Temperature graph (05 UI tweak). Fits ALL finite sensor
 * history samples plus every ACTIVE setpoint, applies an ABSOLUTE [GRAPH_PAD] above/below (the absolute
 * pad is the anti-noise mechanism — it guarantees a non-collapsing span even at dead-steady, so we need
 * no separate min-span floor), rounds the bounds OUTWARD to [GRAPH_STEP] (stops per-sample axis twitch),
 * and clamps into the [GRAPH_FLOOR]..[GRAPH_CEIL] heater envelope. Empty input → [DEFAULT_GRAPH_RANGE].
 */
internal fun computeGraphYRange(
    series: List<FloatArray>,
    setpoints: List<Float?>,
): ClosedFloatingPointRange<Float> {
    var min = Float.POSITIVE_INFINITY
    var max = Float.NEGATIVE_INFINITY
    for (s in series) for (v in s) if (v.isFinite()) {
        if (v < min) min = v
        if (v > max) max = v
    }
    for (sp in setpoints) if (sp != null && sp.isFinite()) {
        if (sp < min) min = sp
        if (sp > max) max = sp
    }
    if (min == Float.POSITIVE_INFINITY) return DEFAULT_GRAPH_RANGE // no finite data yet

    var lo = floor((min - GRAPH_PAD) / GRAPH_STEP) * GRAPH_STEP
    var hi = ceil((max + GRAPH_PAD) / GRAPH_STEP) * GRAPH_STEP
    lo = lo.coerceAtLeast(GRAPH_FLOOR)
    hi = hi.coerceAtMost(GRAPH_CEIL)
    if (hi - lo < GRAPH_STEP) hi = (lo + GRAPH_STEP).coerceAtMost(GRAPH_CEIL) // never degenerate
    return lo..hi
}

/**
 * The adjust scrubber's range for a heater: 0 (= off) up to the configured `max_temp`, falling back to
 * the global [works.mees.jiib.command.PrinterCommands.MAX_TEMP_C] clamp when limits are unknown.
 * `min_temp` is informational only — the floor stays 0 so the scrubber can reach off.
 */
fun heaterScrubberRange(limits: works.mees.jiib.state.HeaterLimits?): ClosedFloatingPointRange<Float> {
    val global = works.mees.jiib.command.PrinterCommands.MAX_TEMP_C.toDouble()
    // Guard against a garbled configfile max_temp (NaN / ≤0): an inverted/empty range would make the
    // Scrubber's `value.coerceIn(start, end)` THROW (pre-merge review fix). Fall back to the global clamp.
    val raw = limits?.maxTemp
    val max = (if (raw != null && raw.isFinite() && raw > 0.0) raw else global).toFloat()
    return 0f..max
}
