package works.mees.dinghy.designsystem.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.fractionFromX
import works.mees.dinghy.designsystem.fractionFromY
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/** Axis orientation for the [Scrubber] component. */
enum class ScrubberOrientation { Horizontal, Vertical }

/**
 * THE canonical drag-adjust scrubber — the sketch-004 ringed-thumb style (R9, owner, 2026-06-12;
 * law: docs/ui_design/COMPONENTS.md §7 "Scrubber"). Supersedes the legacy fill-bar style
 * (`ScrubberControl` / `LedBrightnessControl` visuals, both deleted in the structural slate).
 *
 * ## Anatomy (the §7b dp table: track 6 / thumb 34 / ring 5 / touch 74)
 *  - Header row: [name] (Geist bold) start · live value + dim [unit] (GeistMono bold) end.
 *  - Track: 6dp pill — filled side `accent`, remainder `surface3`, LEFT-anchored fill.
 *  - Thumb: 34dp `surface` knob with a 5dp `accent` ring; pressed = `accentSoft` halo
 *    (the sketch's 10dp press ring). The whole gesture row is the enlarged touch target
 *    (74dp tall, capped at 1U — UAT-3).
 *  - Ends row: min/max labels under the track (GeistMono, 15sp floor).
 *  - ± stepper row below (keyboard-free discrete adjust; each tap settles — R5 accent).
 *
 * Vertical orientation ([ScrubberOrientation.Vertical]): renders ONLY the gesture track (no
 * header/ends/stepper — the caller owns labelling). Fill is BOTTOM-anchored (fraction 0 = bottom,
 * fraction 1 = top), matching the Y-inverted [fractionFromY] mapping. The gesture box fills the
 * height the caller provides and is [TOUCH_TARGET] wide.
 *
 * ## Snapping
 * Every set — tap, drag frame, stepper — snaps to [step] then clamps to [range]
 * (`round((v-start)/step)*step + start`), matching the 004 sketch's `Math.round(v/step)*step`.
 *
 * ## Build-once / update-in-place (the `fa97efb` lesson + COMPONENTS.md §7 impl rule)
 * The dragged element is built ONCE; drag updates mutate state that is read ONLY in the draw
 * phase (track fill + halo via `drawBehind`) and the layout phase (thumb via `offset {}` lambda)
 * — NO recomposition of the gesture node per move frame (Adreno-320 budget). Only the value Text
 * recomposes as the number changes. Do NOT wrap call sites in `key(value)` — the internal
 * `working` state is re-seeded IN PLACE via `remember(value, range)` (P19 SC-3 / CR-04):
 *  1. `working` lives in ONE stable MutableFloatState for the composition lifetime, so a live
 *     echo updating [value] never strands the running gesture handler on a dead state object.
 *  2. [onSettle]/[onValueChange] route through [rememberUpdatedState] so the long-lived
 *     `awaitEachGesture` handler always dispatches via the CURRENT lambdas.
 *
 * ## Settle contract (HIGH-3 / T-19-06-02)
 * [onSettle] fires EXACTLY ONCE per gesture-end (pointer-up) or stepper tap — never per scrub
 * frame (proven host-side by `settleDispatchCount`). [onValueChange] fires per frame for live
 * preview only — callers must NOT dispatch from it.
 *
 * @param name          the setting's display name (header start). Empty string = value-only header.
 * @param value         the current caller-owned value; re-seeds `working` in place when it changes.
 * @param range         the allowed closed range; drag + steps are clamped to it.
 * @param step          the snap increment (drag snaps to it; the ± steppers add/subtract it).
 * @param uDp           one unit U — caps the gesture row height (UAT-3: track + thumb ≤ 1U).
 * @param orientation   [ScrubberOrientation.Horizontal] (default) or [ScrubberOrientation.Vertical].
 *                      Vertical renders the gesture track only (no header/ends/stepper).
 * @param onSettle      dispatched ONCE per gesture-end / stepper tap with the settled value.
 * @param unit          optional unit suffix ("%", "°", "°C") shown dim after the value and on the
 *                      min/max end labels.
 * @param enabled       false gates the gesture, steppers, and settle (busy lock — callers may
 *                      instead gate inside [onSettle] to keep the drag live while busy).
 * @param onValueChange live per-frame preview callback (no dispatch).
 */
@Composable
fun Scrubber(
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    uDp: Dp,
    onSettle: (Float) -> Unit,
    modifier: Modifier = Modifier,
    orientation: ScrubberOrientation = ScrubberOrientation.Horizontal,
    bare: Boolean = false,
    unit: String = "",
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit = {},
) {
    val t = LocalTokens.current
    val density = LocalDensity.current

    // Build-once seed (P19 SC-3 / CR-04): one stable state object, re-seeded IN PLACE.
    val workingState = remember { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    remember(value, range) { workingState.floatValue = value.coerceIn(range.start, range.endInclusive) }
    var working by workingState
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    // Pressed → accentSoft halo. Read ONLY in drawBehind (draw-phase invalidation, no recompose).
    var pressed by remember { mutableStateOf(false) }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnSettle by rememberUpdatedState(onSettle)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f

    // Snap-to-step then clamp (004 law). A degenerate step (<= 0) just clamps.
    fun snap(next: Float): Float {
        val snapped = if (step > 0f) {
            range.start + ((next - range.start) / step).roundToInt() * step
        } else {
            next
        }
        return snapped.coerceIn(range.start, range.endInclusive)
    }

    fun set(next: Float) {
        if (!currentEnabled) return
        val snapped = snap(next)
        working = snapped
        currentOnValueChange(snapped)
    }

    fun setFromX(x: Float) {
        if (trackWidthPx <= 0f) return
        set(range.start + fractionFromX(x, trackWidthPx) * span)
    }

    fun setFromY(y: Float) {
        if (trackHeightPx <= 0f) return
        set(range.start + fractionFromY(y, trackHeightPx) * span)
    }

    fun settle() {
        if (currentEnabled) currentOnSettle(working)
    }

    // Integer display when the value sits on a whole number, one decimal otherwise (the legacy
    // ScrubberControl convention — covers the %, °, °C output ranges without a decimals param).
    fun fmt(v: Float): String =
        if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else ((v * 10f).roundToInt() / 10f).toString()

    // Geometry (COMPONENTS.md §7b): track 6 / thumb visible 34 / ring 5 / touch 74 — row capped ≤1U.
    val rowHeight = if (uDp < TOUCH_TARGET) uDp else TOUCH_TARGET
    val thumbRadiusPx = with(density) { (THUMB_VISIBLE / 2).toPx() }
    val haloRadiusPx = with(density) { (THUMB_VISIBLE / 2 + HALO_RING).toPx() }
    val trackHalfPx = with(density) { (TRACK_HEIGHT / 2).toPx() }

    // The shared gesture box — orientation-dependent branches are inside.
    // Horizontal: fillMaxWidth × rowHeight; fill LEFT-anchored; thumb CenterStart + x-offset.
    // Vertical:   fillMaxHeight × rowHeight (used as width); fill BOTTOM-anchored; thumb BottomCenter + negative-y-offset.
    val gestureBoxModifier = when (orientation) {
        ScrubberOrientation.Horizontal -> Modifier
            .fillMaxWidth()
            .height(rowHeight)
        ScrubberOrientation.Vertical -> Modifier
            .fillMaxHeight()
            .width(rowHeight)
    }

    val gestureBox: @Composable () -> Unit = {
        Box(
            gestureBoxModifier
                .onSizeChanged {
                    trackWidthPx = it.width.toFloat()
                    trackHeightPx = it.height.toFloat()
                }
                // One coordinated detector (WR-01 / G-3): tap-set on DOWN, track moves, settle on UP.
                .pointerInput(range, step, orientation) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        if (orientation == ScrubberOrientation.Horizontal) {
                            setFromX(down.position.x)
                        } else {
                            setFromY(down.position.y)
                        }
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    if (orientation == ScrubberOrientation.Horizontal) {
                                        setFromX(change.position.x)
                                    } else {
                                        setFromY(change.position.y)
                                    }
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        pressed = false
                        // Gesture END — settle-dispatch ONCE (HIGH-3), never per move frame.
                        settle()
                    }
                }
                .drawBehind {
                    val fraction = ((working - range.start) / span).coerceIn(0f, 1f)
                    val pill = CornerRadius(trackHalfPx, trackHalfPx)
                    if (orientation == ScrubberOrientation.Horizontal) {
                        val w = size.width
                        val cy = size.height / 2f
                        val fillEnd = fraction * w
                        // Remainder track (surface3) then the LEFT-anchored accent fill.
                        drawRoundRect(
                            color = t.surface3,
                            topLeft = Offset(0f, cy - trackHalfPx),
                            size = Size(w, trackHalfPx * 2),
                            cornerRadius = pill,
                        )
                        if (fillEnd > 0f) {
                            drawRoundRect(
                                color = t.accent,
                                topLeft = Offset(0f, cy - trackHalfPx),
                                size = Size(fillEnd, trackHalfPx * 2),
                                cornerRadius = pill,
                            )
                        }
                        // Press halo BEHIND the thumb (sketch :active 10dp accentSoft ring).
                        if (pressed) {
                            drawCircle(
                                color = t.accentSoft,
                                radius = haloRadiusPx,
                                center = Offset(fillEnd.coerceIn(thumbRadiusPx, maxOf(thumbRadiusPx, w - thumbRadiusPx)), cy),
                            )
                        }
                    } else {
                        // Vertical: track runs full height; fill is BOTTOM-anchored.
                        val h = size.height
                        val cx = size.width / 2f
                        val fillLen = fraction * h
                        // Remainder track (surface3) — full height column.
                        drawRoundRect(
                            color = t.surface3,
                            topLeft = Offset(cx - trackHalfPx, 0f),
                            size = Size(trackHalfPx * 2, h),
                            cornerRadius = pill,
                        )
                        if (fillLen > 0f) {
                            // Accent fill BOTTOM-anchored (fraction 0 = bottom, 1 = top).
                            drawRoundRect(
                                color = t.accent,
                                topLeft = Offset(cx - trackHalfPx, h - fillLen),
                                size = Size(trackHalfPx * 2, fillLen),
                                cornerRadius = pill,
                            )
                        }
                        // Press halo BEHIND the thumb.
                        if (pressed) {
                            val haloY = (h - fillLen).coerceIn(
                                thumbRadiusPx,
                                maxOf(thumbRadiusPx, h - thumbRadiusPx),
                            )
                            drawCircle(
                                color = t.accentSoft,
                                radius = haloRadiusPx,
                                center = Offset(cx, haloY),
                            )
                        }
                    }
                },
        ) {
            if (orientation == ScrubberOrientation.Horizontal) {
                // Ringed thumb — 34dp surface knob + 5dp accent ring. Positioned in the layout phase
                // (offset lambda reads the drag state — no recomposition per move).
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            val fraction = ((workingState.floatValue - range.start) / span).coerceIn(0f, 1f)
                            val center = (fraction * trackWidthPx)
                                .coerceIn(thumbRadiusPx, maxOf(thumbRadiusPx, trackWidthPx - thumbRadiusPx))
                            IntOffset((center - thumbRadiusPx).roundToInt(), 0)
                        }
                        .size(THUMB_VISIBLE)
                        .shadow(3.dp, CircleShape)
                        .background(t.surface, CircleShape)
                        .border(BorderStroke(THUMB_RING, t.accent), CircleShape),
                )
            } else {
                // Vertical thumb — BottomCenter anchor, negative-Y offset so fraction 1 = top.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset {
                            val fr = ((workingState.floatValue - range.start) / span).coerceIn(0f, 1f)
                            val center = (fr * trackHeightPx)
                                .coerceIn(thumbRadiusPx, maxOf(thumbRadiusPx, trackHeightPx - thumbRadiusPx))
                            IntOffset(0, -(center - thumbRadiusPx).roundToInt())
                        }
                        .size(THUMB_VISIBLE)
                        .shadow(3.dp, CircleShape)
                        .background(t.surface, CircleShape)
                        .border(BorderStroke(THUMB_RING, t.accent), CircleShape),
                )
            }
        }
    }

    if (orientation == ScrubberOrientation.Horizontal) {
        if (bare) {
            // Bare mode: track only — no header, no ends row, no ± steppers. The caller owns all
            // labelling. The gesture box is self-sizing (fillMaxWidth × rowHeight) so it just emits
            // directly inside the caller's layout without any extra wrapping.
            Box(modifier) { gestureBox() }
        } else {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Header: name start · live value + dim unit end (sketch .row1).
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (name.isNotEmpty()) {
                        Text(
                            text = name,
                            color = t.text,
                            fontFamily = Geist,
                            fontWeight = FontWeight.Bold,
                            fontSize = fsSp(20f, t.fs).sp,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = fmt(working),
                            color = t.text,
                            fontFamily = GeistMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = fsSp(40f, t.fs).sp,
                        )
                        if (unit.isNotEmpty()) {
                            Text(
                                text = unit,
                                color = t.text3,
                                fontFamily = GeistMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = fsSp(22f, t.fs).sp,
                            )
                        }
                    }
                }

                // Gesture row = the enlarged touch target (74dp, ≤1U). Track + fill + halo painted in the
                // DRAW phase; thumb placed in the LAYOUT phase — the dragged element never recomposes.
                gestureBox()

                // Ends row: min/max under the track (sketch .ends; 15sp ramp floor).
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = fmt(range.start) + unit,
                        color = t.text3,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = fsSp(15f, t.fs).sp,
                    )
                    Text(
                        text = fmt(range.endInclusive) + unit,
                        color = t.text3,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = fsSp(15f, t.fs).sp,
                    )
                }

                // ± stepper row — discrete adjust; each tap is its own settle (ends a discrete gesture).
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedControl(
                        label = "−",
                        onClick = { set(working - step); settle() },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent, // R5: setting adjustment = accent (neutral retired)
                    )
                    OutlinedControl(
                        label = "+",
                        onClick = { set(working + step); settle() },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent, // R5: setting adjustment = accent (neutral retired)
                    )
                }
            }
        }
    } else {
        // Vertical: only the gesture track — caller owns labels. Centered horizontally in
        // whatever width the caller allots.
        Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            gestureBox()
        }
    }
}

// COMPONENTS.md §7b dp table: Scrubber track / thumb visible / thumb ring / touch target = 6/34/5/74.
private val TRACK_HEIGHT = 6.dp
private val THUMB_VISIBLE = 34.dp
private val THUMB_RING = 5.dp
private val TOUCH_TARGET = 74.dp
private val HALO_RING = 10.dp
