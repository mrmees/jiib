package works.mees.dinghy.state

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

    private val _temperatureBackfill = MutableStateFlow<Map<String, FloatArray>>(emptyMap())
    /** Per-sensor `server.temperature_store` history (oldest→newest), seeds the graph on connect (G-1). */
    val temperatureBackfill: StateFlow<Map<String, FloatArray>> = _temperatureBackfill.asStateFlow()

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

    /** One-shot at handshake: per-sensor temperature_store backfill (05-03). NOT the throttled hot path. */
    fun setTemperatureBackfill(backfill: Map<String, FloatArray>) {
        _temperatureBackfill.value = backfill
    }

    // ---- internals ------------------------------------------------------------------------------

    /** A diff touches the control plane if it carries a discrete lifecycle field that must be immediate. */
    private fun touchesControlPlane(diff: JsonObject): Boolean {
        if (diff.containsKey("print_stats")) return true
        if (diff.containsKey("webhooks")) return true
        val toolhead = diff["toolhead"] as? JsonObject
        if (toolhead != null && toolhead.containsKey("homed_axes")) return true
        return false
    }

    companion object {
        /** ~4 Hz default sampling cadence for the high-rate plane (STATE-03). */
        const val DEFAULT_SAMPLE_MS = 250L
        private const val GCODE_BUFFER = 256
    }
}
