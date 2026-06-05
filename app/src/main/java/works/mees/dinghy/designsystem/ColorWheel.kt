package works.mees.dinghy.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import works.mees.dinghy.theme.compose.LocalTokens
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The touch hue-ring seed picker (D-06/D-07) — the NEW Compose `Canvas` control this phase introduces.
 * No wheel exists in the repo; its gesture mirrors [ScrubberPage]'s WR-01 single-`awaitEachGesture`
 * pattern and its render obeys the Adreno-320 fill-rate floor.
 *
 * ## Settle-not-stream regeneration (D-07 — the load-bearing perf rule)
 * The generator normalizes lightness/chroma to the cusp, so the control primarily picks **hue**
 * (COLOR-SYSTEM §2/§5). During a drag the handle moves freely and ONLY [onHandleMove] fires (cheap —
 * repaint the handle, NO palette regen / no color math). The palette regenerates + rethemes the whole
 * app on pointer-UP via [onSettle] (D-07). One coordinated `awaitEachGesture` — never a second detector
 * to race — exactly like ScrubberPage.
 *
 * ## Cached sweep-gradient ring (T-15-06-01 — protect the floor)
 * The hue ring is drawn from a SINGLE pre-built [Brush.sweepGradient] cached across drag frames; only
 * the small handle repaints per move. The ring is NEVER redrawn arc-by-arc per drag frame (that would
 * jank the Adreno-320). Compose memoizes the gradient brush across recompositions; the per-move state
 * that changes is only the handle angle, so the gradient stroke is re-issued but never recomputed.
 *
 * ## Carve-out (precedented)
 * The ring + handle render their LITERAL hue color — the same data-color carve-out as the old
 * `AccentSwatch` (a swatch IS the value being chosen, not chrome). All chrome (the handle outline ring,
 * the backing) routes through [LocalTokens].
 *
 * The wheel is a SACRED CIRCLE: `aspect-ratio(1f)`, sized by its cell's constraining dimension — it
 * must render circular, never elliptical.
 *
 * @param hue the current hue 0..360 (drives the handle position).
 * @param onHandleMove fired on EACH move with the in-progress hue — cheap, handle-only repaint (NO regen).
 * @param onSettle fired on pointer-UP ONLY with the settled hue — the app rethemes here (D-07).
 */
@Composable
fun ColorWheel(
    hue: Float,
    onHandleMove: (Float) -> Unit,
    onSettle: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // The pre-built hue sweep-gradient (CONFIRMED Codex finding): cached across drag frames, never
    // recomputed per move. 13 stops (0..360 in 30° steps) read as a continuous hue ring.
    val ringBrush = rememberHueSweep()

    Box(modifier.aspectRatio(1f)) {
        Canvas(
            Modifier
                .fillMaxSize()
                // ONE coordinated gesture detector (WR-01 / ScrubberPage): set the hue from the DOWN
                // position (so a pure tap lands a seed), track each still-pressed move via onHandleMove
                // (cheap — NO regen), and regenerate+retheme on pointer-UP via onSettle ONLY (D-07).
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var current = hueAt(down.position, size.width.toFloat(), size.height.toFloat())
                        onHandleMove(current)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed && change.positionChanged()) {
                                    current = hueAt(
                                        change.position,
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                    )
                                    onHandleMove(current)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        // Pointer UP: settle → regenerate + retheme the whole app ONCE (D-07).
                        onSettle(current)
                    }
                },
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = min(cx, cy)
            val thickness = outerR * 0.30f
            val ringR = outerR - thickness / 2f

            // The cached hue ring — ONE sweep-gradient stroke, reused across drag frames (T-15-06-01).
            drawCircle(
                brush = ringBrush,
                radius = ringR,
                center = Offset(cx, cy),
                style = Stroke(width = thickness),
            )

            // The handle: a literal-hue dot at the current angle (the data-color carve-out), ringed by a
            // token-chromed outline (THEME-01). The handle hit area is ≥64dp via the overlaid Box below.
            val rad = Math.toRadians((hue - 90f).toDouble())
            val hx = cx + (ringR * cos(rad)).toFloat()
            val hy = cy + (ringR * sin(rad)).toFloat()
            val handleR = thickness * 0.62f
            drawCircle(color = Color.hsv(hue % 360f, 1f, 1f), radius = handleR, center = Offset(hx, hy))
            // 2dp outline (chromed) + a static edge-glow ring — no looping animation (Adreno-320 floor).
            drawCircle(
                color = t.outline,
                radius = handleR,
                center = Offset(hx, hy),
                style = Stroke(width = with(this) { 2.dp.toPx() }),
            )
        }

        // A ≥64dp invisible touch-floor anchor so even a tiny rendered handle stays gloved-finger
        // friendly (UI-02). The gesture itself reads the whole Canvas, so this is a sizing guarantee.
        Box(Modifier.size(64.dp))
    }
}

/** Build the cached hue sweep-gradient (0..360 in 30° stops). Memoized so it is NOT recomputed per drag frame. */
@Composable
private fun rememberHueSweep(): Brush =
    androidx.compose.runtime.remember {
        val stops = (0..12).map { i -> (i / 12f) to Color.hsv((i * 30f) % 360f, 1f, 1f) }
        Brush.sweepGradient(*stops.toTypedArray())
    }

/** The hue (0..360) at a touch position, measured as the angle from center (12-o'clock = 0°, clockwise). */
private fun hueAt(pos: Offset, w: Float, h: Float): Float {
    val cx = w / 2f
    val cy = h / 2f
    val dx = pos.x - cx
    val dy = pos.y - cy
    // atan2 with the +90° rotation so 12-o'clock reads as hue 0 (matches the handle render).
    var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
    if (deg < 0f) deg += 360f
    return deg % 360f
}
