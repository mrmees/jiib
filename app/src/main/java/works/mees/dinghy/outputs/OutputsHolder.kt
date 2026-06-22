package works.mees.dinghy.outputs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import works.mees.dinghy.state.OutputLiveValue
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * One row of the Outputs list (SC-1) — the typed view-model the [OutputsScreen] and detail pages consume.
 *
 * The four display facets are DISTINCT named fields (19-05 review MEDIUM), never one overloaded `value`,
 * so the screen reads each unambiguously:
 *  - [displayValue] — the human-readable live readout ("45%", "On", "210°C", a brightness %), or `null`
 *    when the value is ABSENT (not yet reported) OR UNREADABLE (servo `.value` is PWM, not an angle — SC-3).
 *    A null [displayValue] hides ONLY the value; the row stays present + tappable.
 *  - [swatchColor] — the LED data-color as packed ARGB (the THEME-01 carve-out: filament/LED color is DATA),
 *    or `null` for non-LED families.
 *  - [isSettable] — `false` for a read-only (static_value) pin (value-only, no control), `true` otherwise.
 *  - [busy] — a dispatch to THIS output is in-flight / its optimistic state-flip has not landed yet.
 */
data class OutputRowVm(
    val descriptor: OutputDescriptor,
    val displayValue: String?,
    val swatchColor: Long?,
    val isSettable: Boolean,
    val busy: Boolean,
    val ledChannels: List<Float>? = null,
)

/**
 * One outstanding optimistic dispatch the per-output busy lock waits on (17-07). [objectKey] keys the
 * row it belongs to (NOT the whole list — busy is per-output, never global). [target] is the CLAMPED wire
 * value (0..1 / a PWM duty / a digital 0|1) the live status must reach before the lock releases; for a
 * servo it is unused (servo is TIMEOUT-ONLY — its angle can't be confirmed from the live PWM `value`).
 * [seq] is a monotonic arm id so a `MutableStateFlow` always re-emits an equal-content re-arm and the
 * seq-guarded timeout backstop has a stable token to match on (mirrors FineTune's PendingStateFlip).
 */
data class OutputPending(
    val objectKey: String,
    val target: Double,
    val targetChannels: List<Double>? = null,
    val seq: Long = 0L,
)

/**
 * Toolkit-agnostic holder for the Outputs list (SC-1/SC-3). It COMBINEs the store's
 * [PrinterStateStore.outputDescriptors] with the throttled [PrinterStateStore.printerState] into a
 * `StateFlow<List<OutputRowVm>>`, alpha-sorted by `prettyName`.
 *
 * ## Display scaling lives here (RESEARCH Pitfall 1 / FineTune precedent)
 * The reducer stores RAW wire values (0..1 speeds/values, raw color_data); [buildRow] is the ONLY place
 * the 0..1 → 0..100% conversion (and digital On/Off / temp °C / LED brightness%+swatch) happens. Nothing
 * reads a raw ratio into a row.
 *
 * ## heater_generic is single-sourced via `heaters` (19-04 MEDIUM decision)
 * A heater_generic row reads its current temperature from [PrinterState.heaters] (the one existing heater
 * source), NOT from [PrinterState.outputs] — the two must never diverge. Every other family reads from
 * [PrinterState.outputs].
 *
 * ## Per-output busy lock with per-family reached() (17-07 / review MEDIUM)
 * The screen arms an optimistic state-flip per ±dispatch via [markPending] (keyed by the FULL objectKey),
 * fed the SAME clamped wire value the command sends ([PrinterCommands.outputPctToWire] et al.) so target
 * and wire never disagree. [reached] confirms from live status PER FAMILY — fan `.speed`, pin/pwm `.value`,
 * heater `.target`, LED `color_data[0]` brightness all confirm; a **servo never confirms** (its `.value` is
 * PWM, not the commanded angle) so it relies on the seq-guarded TIMEOUT backstop alone. A dispatch
 * failure/timeout clears the lock via [clearPending].
 *
 * Plain Kotlin (no Compose annotations) — host-unit-testable (ADR-0001).
 *
 * @param scope the lifecycle scope the combine collect runs on (the UI host supplies it).
 * @param store the already-assembled spine; the holder CONSUMES it, never opens a session.
 */
class OutputsHolder(
    private val scope: CoroutineScope,
    private val store: PrinterStateStore,
) {
    /** Monotonic arm id — pre-incremented on each [markPending] so equal-content re-arms are distinct. */
    private var pendingSeq: Long = 0L

    /** Per-objectKey self-clear timers for the currently-armed pending flips (one bounded timer each). */
    private val timeoutJobs: MutableMap<String, Job> = mutableMapOf()

    /** The last throttled printer state the combine collector observed (skip-arm/reached() agree on it). */
    private var lastObservedState: PrinterState? = null

    /** The outstanding per-output optimistic flips the busy lock waits on, keyed by objectKey. */
    private val _pending = MutableStateFlow<Map<String, OutputPending>>(emptyMap())

    private val _rows = MutableStateFlow<List<OutputRowVm>>(emptyList())
    /** The resolved Outputs row list (display-scaled, typed VM, per-output busy), alpha-sorted. */
    val rows: StateFlow<List<OutputRowVm>> = _rows.asStateFlow()

    init {
        scope.launch {
            combine(
                store.outputDescriptors,
                store.printerState,
                _pending,
            ) { descriptors, state, pending ->
                lastObservedState = state
                // Clear any pending flip whose live value has reached the target (per family).
                val stillPending = pending.filterValues { p ->
                    val descriptor = descriptors.firstOrNull { it.objectKey == p.objectKey }
                    // Keep the flip only while it is NOT yet reached (servo's reached() is always false →
                    // it stays pending until the seq-guarded timeout backstop drops it).
                    descriptor != null && !reached(descriptor, state, p)
                }
                if (stillPending.size != pending.size) {
                    // One or more real flips landed before the timeout — cancel those backstops + publish.
                    (pending.keys - stillPending.keys).forEach { timeoutJobs.remove(it)?.cancel() }
                    _pending.value = stillPending
                }
                descriptors
                    .map { buildRow(it, state, stillPending.containsKey(it.objectKey)) }
                    .sortedBy { it.descriptor.prettyName }
            }.collect { _rows.value = it }
        }
    }

    /**
     * Arm an optimistic per-output state-flip (17-07). [clampedWireTarget] MUST be the value the wire will
     * actually carry (computed via [works.mees.dinghy.command.PrinterCommands.outputPctToWire] or the
     * matching clamp helper) so the optimistic target and the dispatched command can never disagree —
     * a mismatch is exactly what wedged the Fine-Tune busy lock. Schedules a seq-guarded bounded self-clear
     * so an unreachable flip (notably a servo, which can never confirm) always releases.
     */
    fun markPending(objectKey: String, clampedWireTarget: Double, targetChannels: List<Double>? = null) {
        timeoutJobs.remove(objectKey)?.cancel()
        val armed = OutputPending(objectKey, clampedWireTarget, targetChannels, seq = ++pendingSeq)
        _pending.value = _pending.value + (objectKey to armed)
        timeoutJobs[objectKey] = scope.launch {
            delay(PENDING_TIMEOUT_MS)
            // Clear ONLY if the live pending flip is still the EXACT one this timer armed (seq match).
            if (_pending.value[objectKey]?.seq == armed.seq) {
                _pending.value = _pending.value - objectKey
                timeoutJobs.remove(objectKey)
            }
        }
    }

    /** Clear an output's pending flip on a dispatch failure/timeout (the screen calls this off Failure). */
    fun clearPending(objectKey: String) {
        timeoutJobs.remove(objectKey)?.cancel()
        _pending.value = _pending.value - objectKey
    }

    /**
     * True once the live status for [pending].objectKey reaches the dispatched target — PER FAMILY.
     *
     * fan_generic → `.speed`; output_pin (digital or PWM) / pwm_tool → `.value`; led/neopixel/etc →
     * `color_data[0]` brightness; heater_generic → `heaters[objectKey].target`. **servo always returns
     * false** — its live `.value` is the PWM duty, NOT the commanded angle, so an angle command can never
     * be observed as reached; the servo's pending lock releases ONLY via the seq-guarded timeout backstop.
     */
    private fun reached(descriptor: OutputDescriptor, state: PrinterState, pending: OutputPending): Boolean {
        if (descriptor.family == FAMILY_SERVO) return false // timeout-only (value is PWM, not angle).
        if (descriptor.family in LED_FAMILIES) {
            val live = state.outputs[descriptor.objectKey]?.colorData?.getOrNull(0) ?: return false
            val tgt = pending.targetChannels
            return if (tgt != null) {
                // Full r/g/b/w compare: a hue/saturation/white change can keep the same MAX brightness,
                // so comparing only maxOrNull() would clear the optimistic flip prematurely.
                (0 until maxOf(tgt.size, live.size)).all { i ->
                    abs((live.getOrNull(i) ?: 0.0) - (tgt.getOrNull(i) ?: 0.0)) < REACHED_EPSILON
                }
            } else {
                val cur = live.maxOrNull() ?: return false
                abs(cur - pending.target) < REACHED_EPSILON
            }
        }
        val current: Double? = when (descriptor.family) {
            FAMILY_FAN -> state.outputs[descriptor.objectKey]?.speed
            FAMILY_HEATER -> state.heaters[descriptor.objectKey]?.target
            else -> state.outputs[descriptor.objectKey]?.value // output_pin / pwm_tool
        }
        return current != null && abs(current - pending.target) < REACHED_EPSILON
    }

    /** Build one typed row VM — display scaling, swatch, settable + busy — for [descriptor]. */
    private fun buildRow(descriptor: OutputDescriptor, state: PrinterState, busy: Boolean): OutputRowVm {
        val live = state.outputs[descriptor.objectKey]
        var swatch: Long? = null
        val displayValue: String? = when {
            // A read-only (static_value) pin is surfaced but never commandable (SC-3).
            descriptor.family == FAMILY_SERVO ->
                // Servo `.value` is PWM, not an angle → HIDE (do NOT back-compute degrees, SC-3).
                null

            descriptor.family == FAMILY_FAN ->
                live?.speed?.let { "${pct(it)}%" }

            descriptor.family == FAMILY_HEATER ->
                // Single-source: heater_generic current temp from `heaters`, never `outputs`.
                state.heaters[descriptor.objectKey]?.temperature?.let { "${it.roundToInt()}°C" }

            descriptor.family in LED_FAMILIES -> {
                val rgbw = live?.colorData?.getOrNull(0)
                if (rgbw != null) {
                    // GAP-B: a white-only LED ([r,g,b,w] = [0,0,0,w]) must NOT pack as black — surface a
                    // grey/white swatch scaled by the WHITE component so a lit white-only LED reads bright.
                    swatch = if (!descriptor.ledHasRgb && descriptor.ledHasWhite) {
                        val w = rgbw.getOrElse(3) { 0.0 }
                        packArgb(listOf(w, w, w))
                    } else {
                        packArgb(rgbw)
                    }
                }
                // Brightness = the brightest channel of the strip's first pixel (0..1 → %). For a white-only
                // LED the white component IS the max channel (r=g=b=0), so this stays correct.
                rgbw?.maxOrNull()?.let { "${pct(it)}%" }
            }

            // output_pin (digital On/Off vs PWM %) and pwm_tool (%).
            !descriptor.pwm && descriptor.family == FAMILY_OUTPUT_PIN ->
                live?.value?.let { if (it >= 0.5) "On" else "Off" }

            else -> // PWM output_pin + pwm_tool.
                live?.value?.let { "${pct(it)}%" }
        }

        // Raw LED channels (r,g,b,w 0..1) for the slider UI to seed H/S/V + White from real state;
        // null for non-LED families. Declared at buildRow scope so the constructor below can see it.
        val ledChannels: List<Float>? = if (descriptor.family in LED_FAMILIES) {
            live?.colorData?.getOrNull(0)?.map { it.toFloat() }
        } else null
        return OutputRowVm(
            descriptor = descriptor,
            displayValue = displayValue,
            swatchColor = swatch,
            isSettable = !descriptor.readOnly,
            busy = busy,
            ledChannels = ledChannels,
        )
    }

    /** Round a raw 0..1 value to a whole-number display percent. */
    private fun pct(raw: Double): Int = (raw * 100).roundToInt()

    /** Pack an `[r,g,b(,w)]` 0..1 channel list into opaque packed ARGB (THEME-01 LED data-color carve-out). */
    private fun packArgb(rgbw: List<Double>): Long {
        val r = ((rgbw.getOrElse(0) { 0.0 }).coerceIn(0.0, 1.0) * 255).roundToInt()
        val g = ((rgbw.getOrElse(1) { 0.0 }).coerceIn(0.0, 1.0) * 255).roundToInt()
        val b = ((rgbw.getOrElse(2) { 0.0 }).coerceIn(0.0, 1.0) * 255).roundToInt()
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    companion object {
        /** Settings family tokens (first space-delimited word of the object key). */
        const val FAMILY_FAN = "fan_generic"
        const val FAMILY_HEATER = "heater_generic"
        const val FAMILY_SERVO = "servo"
        const val FAMILY_OUTPUT_PIN = "output_pin"
        const val FAMILY_PWM_TOOL = "pwm_tool"

        /** Every LED-ish family whose live value is `color_data` (brightness + swatch). */
        val LED_FAMILIES = setOf("led", "neopixel", "dotstar", "pca9533", "pca9632")

        /**
         * Per-output "reached" epsilon on the 0..1 wire scale (17-07). One display-percent step (0.01) is
         * the wire grid `outputPctToWire` produces; half a step (0.005) absorbs float round-trip jitter
         * while staying an order of magnitude below a single percent so a one-step nudge only reads as
         * reached once the live value actually equals the clamped command.
         */
        const val REACHED_EPSILON = 0.005

        /**
         * Bounded self-clear backstop for an UNREACHABLE armed flip (17-07) — notably a servo, which can
         * never confirm its angle from the live PWM value. Generous; only catches the pathological
         * never-reached case so the per-output busy lock can never wedge forever. `internal` so the test
         * can advance past it.
         */
        internal const val PENDING_TIMEOUT_MS = 8_000L
    }
}
