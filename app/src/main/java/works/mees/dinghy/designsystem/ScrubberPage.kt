package works.mees.dinghy.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import kotlin.math.roundToInt

/**
 * The single-setting scrubber/stepper page (PRIM-01) — the keyboard-free replacement for an
 * alphanumeric keypad that EVERY numeric setpoint (heater targets, jog distances, filament
 * weights, fan %) reuses. Per docs/ui_design/CLAUDE.md + LAYOUT.md ("No alphanumeric keyboard in
 * printer controls") there is NO `TextField`/`BasicTextField`/`KeyboardType` here: a value is set
 * ONLY by dragging the full-height fill-bar or tapping the `±[step]` steppers. The live value is
 * rendered in [GeistMono] (tabular numerals) so digits don't jitter as they change.
 *
 * Built on [ScreenScaffold]: the Field holds the fill-bar scrubber (drag to set within [range]),
 * the gutter holds the decrement/increment steppers plus the Cancel/Apply actions on the shared
 * grid.
 *
 * ## Cancel/dismiss intent contract (single, fixed — panels MUST NOT reinterpret per-screen)
 * Apply is [Intent.Go] (green). Cancel/dismiss is [Intent.Neutral] BY DEFAULT, because backing
 * out of a numeric edit without applying is a non-destructive dismiss — not a destructive act, so
 * it is NOT red. [Intent.Danger] (red) is reserved for the rare destructive-revert case (e.g.
 * discarding a value the printer is already using) and is opt-in ONLY via [destructiveDismiss];
 * the default stays neutral so panels can't turn an ordinary back-out red by guesswork.
 *
 * Static styling only (D-13): no looping animation; the fill simply tracks the value.
 *
 * @param label             the setting's name ("Nozzle", "Move Z", "Fan").
 * @param value             the current value (caller-owned; this page is stateless re: commit).
 * @param range             the allowed closed range; drag + step are clamped to it.
 * @param step              the increment/decrement applied by the steppers.
 * @param unit              optional unit suffix shown after the value ("°C", "mm", "%").
 * @param onValueChange     called continuously as the user scrubs/steps (live preview).
 * @param onCancel          called when the user dismisses without applying.
 * @param onApply           called with the final value when the user commits.
 * @param destructiveDismiss opt-in: when true, Cancel is [Intent.Danger] (destructive-revert);
 *                          default false keeps it [Intent.Neutral].
 */
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

@Composable
fun ScrubberPage(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String = "",
    onValueChange: (Float) -> Unit,
    onCancel: () -> Unit,
    onApply: (Float) -> Unit,
    modifier: Modifier = Modifier,
    destructiveDismiss: Boolean = false,
) {
    val t = LocalTokens.current
    // Local working value seeded from [value]; the caller commits on Apply. Keyed to [value]/[range]
    // (BUG-05) so that if the caller's value or allowed range changes while this page is shown, the
    // working state re-initializes from the new input rather than going stale and applying a wrong
    // number on Apply.
    var working by remember(value, range) { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    var barWidthPx by remember { mutableFloatStateOf(0f) }

    fun set(next: Float) {
        val clamped = next.coerceIn(range.start, range.endInclusive)
        working = clamped
        onValueChange(clamped)
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

    ScreenScaffold(
        // Opaque scrim: the scrubber is a full-screen single-setting page shown OVER another screen,
        // so its root must paint t.bg or the page behind bleeds through (mirrors the ConfirmGuard 03-08
        // opaque-scrim fix). ScreenScaffold itself is transparent by contract (it's the base layer on
        // normal routes); overlay callers own the fill.
        modifier = modifier.fillMaxSize().background(t.bg),
        field = {
            // Full-height fill-bar scrubber (hifi.css .fillbar): drag/tap anywhere to set.
            Box(
                Modifier
                    .fillMaxSize()
                    // G-2: share the gutter button group's horizontal inset (16.dp) so the bar and
                    // its own −/+/Cancel/Apply group present one width/left-edge. Vertical 24.dp
                    // keeps the scaffold's breathing room.
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(t.rCard))
                        .background(t.surface2)
                        .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCard))
                        .onSizeChanged { barWidthPx = it.width.toFloat() }
                        // Single coordinated gesture detector (WR-01 / G-3). ONE pointerInput, ONE
                        // pointer consumer — no second detector to race the stream against.
                        // detectDragGestures is wrong here: it needs touch-slop movement before
                        // onDragStart, so a zero-movement TAP is swallowed (the on-device G-3 bug).
                        // awaitEachGesture re-arms per gesture: set the value immediately from the
                        // DOWN position (this is what makes a pure tap register), then track each
                        // still-pressed move. Both the down-set and the move-set funnel through the
                        // SAME pure mapping (setFromX → fractionFromX), so tap at x == drag to x.
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
                            }
                        },
                ) {
                    // The accent-tinted fill tracks the value (left-anchored).
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
        },
        gutter = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Stepper row (hifi.css .adjrow): ±step, keyboard-free numeric entry.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedControl(
                        label = "−",
                        onClick = { set(working - step) },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Neutral,
                    )
                    OutlinedControl(
                        label = "+",
                        onClick = { set(working + step) },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Neutral,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedControl(
                        label = "Cancel",
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        intent = if (destructiveDismiss) Intent.Danger else Intent.Neutral,
                    )
                    OutlinedControl(
                        label = "Apply",
                        onClick = { onApply(working) },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Go,
                    )
                }
            }
        },
    )
}
