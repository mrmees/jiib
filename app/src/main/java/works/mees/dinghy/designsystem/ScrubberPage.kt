package works.mees.dinghy.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import kotlin.math.roundToInt

/**
 * The pure offset→fraction mapping at the heart of the fill-bar scrubber (G-3). Maps a horizontal
 * touch position [x] (px, relative to the bar's left edge) over a measured [barWidthPx] to a
 * `0f..1f` fraction of the bar. BOTH the zero-movement tap (down) and the drag funnel through this
 * single function, so a tap at x and a drag to the same x produce an identical value (no second
 * gesture detector to race the pointer stream — the WR-01 fix). Clamped to `0..1`; an un-measured
 * (`<= 0`) bar width returns `0f` rather than dividing by zero / yielding NaN.
 *
 * `internal` (not a local lambda) so [ScrubberMappingTest] can prove the mapping host-side — the
 * coverage gap that let the original on-device tap-swallow slip past green tests.
 */
internal fun fractionFromX(x: Float, barWidthPx: Float): Float {
    if (barWidthPx <= 0f) return 0f
    return (x / barWidthPx).coerceIn(0f, 1f)
}

/**
 * The pure settle-decision at the heart of the OnSettle dispatch contract (HIGH-3 / T-19-06-02). Given a
 * sequence of pointer phases for ONE gesture — any number of `move` frames bracketed by a single `up` — it
 * returns the number of times the value should be dispatched. The contract: dispatch fires ONCE per
 * gesture-END (`up`), NEVER on a `move` frame, so dragging across N positions yields exactly ONE dispatch.
 *
 * Extracted as a pure function (not inlined in the gesture loop) so [works.mees.dinghy.designsystem
 * .ScrubberPage]'s "one call per gesture-end, not per scrub frame" guarantee is proven host-side without a
 * full Compose/Robolectric harness — the coverage seam the plan asks for.
 */
enum class ScrubPhase { DOWN, MOVE, UP }

fun settleDispatchCount(phases: List<ScrubPhase>): Int = phases.count { it == ScrubPhase.UP }

/**
 * The scrubber's gutter-action contract (HIGH-3). The page renders ONE of two action grammars; the value
 * never changes how it's scrubbed, only how/when it commits.
 *
 * - [ApplyCancel] — the original two-button commit grammar: drag/step a working value, then COMMIT it on
 *   Apply ([Intent.Go], green) or back out on Cancel ([Intent.Neutral] by default; [Intent.Danger] only when
 *   [destructiveDismiss] — the rare destructive-revert). This is the DEFAULT (Fine-Tune / Temperature etc.).
 * - [OnSettle] — the immediate-dispatch grammar (Phase-19 outputs, SC-2): NO Apply button. The value is
 *   dispatched via [onSettle] EXACTLY ONCE when a gesture ends (pointer-up) or a ± stepper is tapped — never
 *   on every intermediate scrub frame (Adreno-320 budget; mirrors [ColorWheel.onSettle]). The gutter is the
 *   ± stepper row + a single [onBack] Back button ([Intent.Neutral]) — OR, when [OnSettle.onOff] is supplied,
 *   a two-button `[Off | Back]` row.
 */
sealed interface ScrubberActions {
    data class ApplyCancel(
        val onCancel: () -> Unit,
        val onApply: (Float) -> Unit,
        val destructiveDismiss: Boolean = false,
    ) : ScrubberActions

    /**
     * @param onSettle  dispatched ONCE per gesture-end / stepper tap (never per scrub frame).
     * @param onBack    the neutral Back exit.
     * @param onOff      OPTIONAL Off action (GAP-A fix, 19-09). When non-null the gutter renders an Off button
     *                  [Intent.Danger] (button-intent law: red = stop/cancel) IN THE GUTTER on the shared grid
     *                  beside Back — it does NOT float over the scrubber value/track. When null the gutter keeps
     *                  the original single full-width Back (no behavior change for callers that don't want an Off).
     * @param offLabel  the Off button's label (callers always pass it when [onOff] is set; defaults to "Off").
     */
    data class OnSettle(
        val onSettle: (Float) -> Unit,
        val onBack: () -> Unit,
        val onOff: (() -> Unit)? = null,
        val offLabel: String? = null,
    ) : ScrubberActions
}

/**
 * The embeddable gesture+state core of the scrubber — extracted from [ScrubberPage] so it can be
 * hosted INSIDE a `DetailCard`/Focus without painting a full-screen `ScreenScaffold` background.
 *
 * ## What lives here (P19 build-once rule — SC-3)
 *  - `working` state seeded via `remember(value, range)` — the build-once seed. Re-seeds ONLY when
 *    `value` or `range` change (e.g. a live state flip), NEVER on every recompose. Do NOT wrap the
 *    call site in `key(output.currentPct)` — that forces a rebuild mid-drag (the fa97efb regression).
 *  - `set`/`setFromX`/`settle` functions (the gesture + mapping logic).
 *  - The `awaitEachGesture` single-pointer detector (WR-01 / G-3) — ONE `pointerInput`, ONE consumer.
 *  - The left-anchored fill Box + label/value text overlay.
 *  - The ± stepper row (keyboard-free; each stepper tap is a settle in OnSettle mode).
 *
 * ## What does NOT live here
 * There is NO `ScreenScaffold`, NO `background(t.bg)`, NO Apply/Cancel/Off/Back foot buttons.
 * Those are the thin wrapper [ScrubberPage]'s responsibility so existing full-screen callers are
 * UNCHANGED.
 *
 * ## OnSettle mode
 * In [ScrubberActions.OnSettle] mode `settle()` dispatches ONCE on gesture-end (pointer-up) or
 * stepper tap — never per scrub frame. In [ScrubberActions.ApplyCancel] mode `settle()` is a no-op;
 * commit happens in the [ScrubberPage] wrapper on Apply.
 *
 * @param label         the setting's name shown inside the fill-bar.
 * @param value         the current value (caller-owned; this control is stateless re: commit).
 * @param range         the allowed closed range; drag + step are clamped to it.
 * @param step          the increment/decrement applied by the steppers.
 * @param actions       the gutter-action mode — forwarded verbatim to the settle logic.
 * @param unit          optional unit suffix shown after the value ("°C", "mm", "%").
 * @param uDp           the unit U height cap (UAT-3); the fill-bar track is constrained to
 *                      `heightIn(max = uDp)` so it never grows beyond a single unit even when
 *                      the host column has more vertical space. Callers derive this from
 *                      `rememberUnitGrid(minOf(maxWidth, maxHeight)).uDp`.
 * @param onValueChange called continuously as the user scrubs/steps (live preview, no dispatch).
 * @param modifier      caller-supplied modifier; typically `Modifier.fillMaxWidth()` for inline use.
 */
@Composable
fun ScrubberControl(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    actions: ScrubberActions,
    unit: String = "",
    uDp: Dp = 80.dp,
    onValueChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    // Build-once seed (P19 SC-3): seeded only when value or range changes, NEVER on every recompose.
    // CRITICAL: do NOT wrap the call site in key(output.currentPct) — that forces a rebuild mid-drag.
    //
    // CR-04 (26-rev): the long-lived awaitEachGesture handler below restarts ONLY when (range, step)
    // change — a recomposition with new captured values does NOT refresh a running pointerInput block.
    // Two hardenings keep the stale closure harmless:
    //  1. `working` lives in ONE stable MutableFloatState for the whole composition lifetime; the
    //     build-once re-seed writes INTO that same object instead of replacing it, so a live echo
    //     updating [value] never strands the running gesture handler writing to a dead state object
    //     (the fa97efb "value-not-sticking" class resurfacing through inline-Focus hosting).
    //  2. `actions`/`onValueChange` route through rememberUpdatedState so the handler always
    //     dispatches via the CURRENT lambdas, never the ones captured when the handler started.
    val workingState = remember { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    remember(value, range) { workingState.floatValue = value.coerceIn(range.start, range.endInclusive) }
    var working by workingState
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    val currentActions by rememberUpdatedState(actions)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    fun set(next: Float) {
        val clamped = next.coerceIn(range.start, range.endInclusive)
        working = clamped
        currentOnValueChange(clamped)
    }

    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val fraction = ((working - range.start) / span).coerceIn(0f, 1f)
    val display = if (working == working.roundToInt().toFloat()) {
        working.roundToInt().toString()
    } else {
        // one decimal for fractional steps; GeistMono keeps the advance uniform
        ((working * 10f).roundToInt() / 10f).toString()
    }

    // Single value write for the gesture path: route x through the testable pure mapping.
    fun setFromX(x: Float) {
        if (barWidthPx <= 0f) return
        set(range.start + fractionFromX(x, barWidthPx) * span)
    }

    // HIGH-3 settle: in OnSettle mode the value dispatches ONCE per gesture-end (pointer-up) or
    // stepper tap — never per scrub frame. In ApplyCancel mode this is a no-op (commit on Apply).
    // The settle-vs-frame decision is proven host-side by settleDispatchCount (OutputScrubberSettleTest).
    fun settle() {
        // CR-04: read the CURRENT actions — a stale capture could settle through a previous
        // output's dispatch closure (command misdirection).
        (currentActions as? ScrubberActions.OnSettle)?.onSettle?.invoke(working)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Fill-bar scrubber (hifi.css .fillbar): drag/tap anywhere to set.
        // UAT-3: track height capped at ≤1U via heightIn(max = uDp) — the caller supplies uDp
        // from rememberUnitGrid so the bar is always exactly one unit tall, not a Focus-filling block.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = uDp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(t.rCard))
                    .background(t.surface2)
                    .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCard))
                    .onSizeChanged { barWidthPx = it.width.toFloat() }
                    // Single coordinated gesture detector (WR-01 / G-3). ONE pointerInput, ONE pointer
                    // consumer — no second detector to race the stream. awaitEachGesture re-arms per
                    // gesture: set value immediately from DOWN (tap registers), then track moves.
                    // Both down-set and move-set funnel through the same pure mapping (fractionFromX).
                    .pointerInput(range, step) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            setFromX(down.position.x)
                            down.consume()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    if (change.pressed) {
                                        setFromX(change.position.x)
                                        change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                            // Gesture END (last pointer up) — settle-dispatch ONCE (HIGH-3). A tap and a
                            // drag-then-release both reach here exactly once, so OnSettle fires once per
                            // gesture, never per move (the per-frame-spam threat T-19-06-02).
                            settle()
                        }
                    },
            ) {
                // The accent-tinted fill tracks the value (left-anchored — TopStart default).
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(t.accentSoft),
                )
                // Live value, centered, in tabular GeistMono.
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = fsSp(20f, t.fs).sp,
                    )
                    Text(
                        text = if (unit.isEmpty()) display else "$display$unit",
                        color = t.text,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = fsSp(56f, t.fs).sp,
                    )
                }
            }
        }
        // Stepper row (hifi.css .adjrow): ±step, keyboard-free numeric entry. In OnSettle mode a
        // stepper tap is itself a settle (it ends a discrete adjustment), so it dispatches once.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedControl(
                label = "−",
                onClick = { set(working - step); settle() },
                modifier = Modifier.weight(1f),
                intent = Intent.Neutral,
            )
            OutlinedControl(
                label = "+",
                onClick = { set(working + step); settle() },
                modifier = Modifier.weight(1f),
                intent = Intent.Neutral,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The legacy full-screen `ScrubberPage` composable was DELETED in the 2026-06-12
// normalization wide pass (R9 — the sketch-004 ringed-thumb scrubber is THE
// scrubber; the fill-bar style is deprecated). `ScrubberControl` + the pure
// gesture helpers above remain LIVE (Outputs) until the structural slate
// migrates them to the 004 style.
// ─────────────────────────────────────────────────────────────────────────────
