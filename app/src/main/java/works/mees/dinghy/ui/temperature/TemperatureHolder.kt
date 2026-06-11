package works.mees.dinghy.ui.temperature

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import works.mees.dinghy.render.RingBuffer
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore
import works.mees.dinghy.ui.settings.TraceStylePrefs

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
 * ## Drawn-sensor resolution (deterministic, capability-authoritative, 3-trace cap)
 * The DRAWN sensors are resolved ONCE from [PrinterStateStore.capabilities] in trace order:
 *  1. nozzle — the `extruder` heater, else the first `extruder`-prefixed heater (multi-tool naming);
 *  2. bed — `heater_bed`;
 *  3. chamber — the first `heater_generic *` entry.
 * Up to THREE traces are drawn (palette nozzle=heat / bed=accent / chamber=violet, mockup §9). ANY
 * additional `heater_generic` / `temperature_sensor` entries beyond these three are INTENTIONALLY
 * omitted in v1 (the 3-trace token palette / fill budget). A richer printer therefore shows its first
 * chamber-class heater but not a fourth heater — documented truncation, not an accidental gap.
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
 *   production passes the process-lifetime [works.mees.dinghy.di.AppContainer]-held instance so
 *   colors/visibility survive process restarts. All writes route through the process-lifetime
 *   `writeScope` (supplied by AppContainer via [setTraceColor]/[setTraceVisibility] callers);
 *   the holder itself ONLY reads prefs here (seeding) and updates in-memory state — callers
 *   are responsible for routing writes to the `writeScope`.
 */
class TemperatureHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
    traceStylePrefs: TraceStylePrefs? = null,
) {
    /** The drawn-sensor object names in trace order (nozzle → bed → chamber), resolved once on first state. */
    @Volatile
    private var drawn: List<String> = emptyList()

    /** One rolling-window ring per drawn sensor (parallel to [drawn]); built lazily when [drawn] resolves. */
    private var rings: List<RingBuffer> = emptyList()

    /** Guard so the one-shot backfill seeds each ring exactly once even if its StateFlow re-emits. */
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

    // ---- D-14: per-sensor trace color + visibility (Plan 26-03) ------------------------------------

    private val _traceColors = MutableStateFlow<Map<String, Color>>(emptyMap())
    /**
     * Per-sensor trace color, keyed by Moonraker object name (e.g. "extruder"). An absent entry
     * means the sensor uses its default [works.mees.dinghy.theme.SeriesColor.seriesColor] token.
     * Seeded from [works.mees.dinghy.ui.settings.TraceStylePrefs] at construction (Task 2);
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
     * persisting via [works.mees.dinghy.di.AppContainer.setTraceColor], which routes through the
     * process-lifetime `writeScope` — never `rememberCoroutineScope()` (P14 write-scope trap).
     */
    fun setTraceColor(sensorName: String, color: Color) {
        _traceColors.value = _traceColors.value + (sensorName to color)
    }

    /**
     * Update the in-memory trace visibility for a sensor (D-14). The CALLER is responsible for also
     * persisting via [works.mees.dinghy.di.AppContainer.setTraceVisibility], which routes through
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
        if (traceStylePrefs != null) {
            scope.launch {
                traceStylePrefs.traceColors.collect { persisted ->
                    // Convert ARGB Int map → Compose Color map
                    _traceColors.value = persisted.mapValues { (_, argb) -> Color(argb) }
                }
            }
            scope.launch {
                traceStylePrefs.traceVisibility.collect { persisted ->
                    _traceVisibility.value = persisted
                }
            }
        }
        // Backfill collector: seed each ring oldest→newest the instant the one-shot read lands (05-03).
        // Seeding ONCE (guarded) so a re-emission of the StateFlow does not re-prepend the history; this
        // makes the graph full deterministically on connect, not contingent on a later status diff.
        scope.launch {
            store.temperatureBackfill.collect { backfill ->
                if (backfill.isEmpty() || seeded) return@collect
                ensureResolved(store.printerState.value, store.capabilities.value)
                if (rings.isEmpty()) return@collect // no drawn sensors resolved yet — try again next emit
                drawn.forEachIndexed { i, name ->
                    backfill[name]?.forEach { rings[i].push(it) }
                }
                seeded = true
                publishSeries()
            }
        }

        // Live collector: consume the store's ALREADY-throttled flow — NO second sample/debounce/delay.
        scope.launch {
            store.printerState.collect { state ->
                ensureResolved(state, store.capabilities.value)
                // Set legend + setpoints BEFORE publishSeries so the dynamic yRange (computed in
                // publishSeries) sees this tick's active setpoints, not the previous tick's.
                _legend.value = drawn.map { name -> readout(state, name) }
                _setpoints.value = drawn.map { name -> setpointOf(state, name) }
                if (rings.isNotEmpty()) {
                    drawn.forEachIndexed { i, name ->
                        rings[i].push(state.heaters[name]?.temperature?.toFloat() ?: 0f)
                    }
                    publishSeries()
                }
            }
        }
    }

    /** Resolve the drawn sensors + build one ring each, ONCE, as soon as capabilities/state name a heater. */
    private fun ensureResolved(state: PrinterState, caps: Capabilities) {
        if (drawn.isNotEmpty()) return
        val resolved = resolveDrawn(state, caps)
        if (resolved.isEmpty()) return
        drawn = resolved
        rings = resolved.map { RingBuffer() }
    }

    private fun publishSeries() {
        val snaps = rings.map { it.snapshot() }
        _series.value = snaps
        _yRange.value = computeGraphYRange(snaps, _setpoints.value)
    }

    /** A target of 0 (or less) means "off, no setpoint" — surface as null (PrintStatusHolder rule). */
    private fun setpointOf(state: PrinterState, name: String): Float? {
        val h: HeaterState = state.heaters[name] ?: return null
        return if (h.target > 0.0) h.target.toFloat() else null
    }

    private fun readout(state: PrinterState, name: String): SensorReadout {
        val h: HeaterState = state.heaters[name] ?: HeaterState()
        val target = if (h.target > 0.0) h.target else null
        // D-11: isAdjustable = true for all v1 drawn sensors (all are heaters); future read-only
        // temperature_sensor entries would set this to false once they join the drawn set.
        return SensorReadout(name = name, label = label(name), current = h.temperature, target = target,
            isAdjustable = true)
    }

    /**
     * The deterministic drawn-sensor list (≤3, trace order). Capabilities is authoritative for "what
     * exists"; we fall back to the live state keys when capabilities is empty (test/seed-only paths).
     */
    private fun resolveDrawn(state: PrinterState, caps: Capabilities): List<String> {
        val names = if (caps.heaters.isNotEmpty()) caps.heaters else state.heaters.keys.toList()
        val out = ArrayList<String>(MAX_TRACES)
        // 1. nozzle: exact `extruder`, else first `extruder`-prefixed.
        (names.firstOrNull { it == "extruder" } ?: names.firstOrNull { it.startsWith("extruder") })
            ?.let { out.add(it) }
        // 2. bed.
        names.firstOrNull { it == "heater_bed" }?.let { out.add(it) }
        // 3. chamber: the FIRST heater_generic (additional generics truncated — 3-trace cap).
        names.firstOrNull { it.startsWith("heater_generic ") }?.let { out.add(it) }
        return out.take(MAX_TRACES)
    }

    private companion object {
        /** Token palette ceiling (heat/accent/violet) — the multi-trace graph draws at most 3 sensors. */
        const val MAX_TRACES = 3
    }
}

/**
 * Short uppercase label for a heater object name (mirrors PrintStatusScreen.label):
 * - `extruder` → "NOZZLE"; `extruder1`/`extruder2` → "NOZZLE 1"/… ; `heater_bed` → "BED";
 * - `heater_generic <name>` → uppercased name; anything else → uppercased verbatim.
 */
private fun label(objectName: String): String = when {
    objectName == "extruder" -> "NOZZLE"
    objectName.startsWith("extruder") -> "NOZZLE ${objectName.removePrefix("extruder")}"
    objectName == "heater_bed" -> "BED"
    objectName.startsWith("heater_generic ") -> objectName.removePrefix("heater_generic ").uppercase()
    else -> objectName.uppercase()
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
