package works.mees.dinghy.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

    // F3: the wheel is a COMFORTABLE control, not a half-screen monster. It is sized to a bounded box and
    // CENTERED inside the caller's slot (which may be full-width) so it no longer dominates the editor page
    // nor steals the column's vertical scroll across its whole footprint.
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Box(Modifier.size(WHEEL_SIZE).aspectRatio(1f)) {
        Canvas(
            Modifier
                .fillMaxSize()
                // ONE coordinated gesture detector (WR-01 / ScrubberPage): set the hue from the DOWN
                // position (so a pure tap lands a seed), track each still-pressed move via onHandleMove
                // (cheap — NO regen), and regenerate+retheme on pointer-UP via onSettle ONLY (D-07).
                //
                // F3 (scroll-theft): the gesture only CLAIMS the pointer when the DOWN lands inside the hue
                // RING annulus (between the inner hole and the outer edge). A touch in the center hole or
                // outside the ring is left unconsumed so the surrounding `verticalScroll` Column still
                // scrolls — the user can scroll PAST the wheel. Only a touch that clearly starts on the ring
                // is owned by the wheel.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (!onRing(down.position, w, h)) {
                            // Not on the ring band → don't consume; let the parent scroll own this gesture.
                            return@awaitEachGesture
                        }
                        var current = hueAt(down.position, w, h)
                        onHandleMove(current)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed && change.positionChanged()) {
                                    current = hueAt(change.position, w, h)
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
            //
            // F2 angle convention: Compose `Brush.sweepGradient` starts at 3-o'clock (0°, +x) and sweeps
            // CLOCKWISE. Screen-space y grows DOWNWARD, so the standard `atan2(dy, dx)` already increases
            // clockwise — meaning hue == the raw screen angle with NO rotation offset. The handle must use
            // the SAME mapping as the ring it sits on: hue 0 → 3 o'clock, 90 → 6 o'clock, 180 → 9 o'clock,
            // 270 → 12 o'clock. (The previous `hue - 90` rotated the handle a quarter-turn off the ring.)
            val rad = Math.toRadians(hue.toDouble())
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
      }
    }
}

/**
 * The bounded wheel size (F3). A comfortable touch control — large enough that the hue ring band is a
 * generous gloved-finger target (the band is ~30% of the radius on each side) but small enough that the
 * wheel no longer dominates the editor page or swallows the column's vertical scroll across half the
 * screen. The wheel is centered in its (possibly full-width) slot.
 */
private val WHEEL_SIZE = 200.dp

/**
 * F3 scroll-theft gate: true when [pos] lands inside the hue RING annulus — i.e. between the inner hole
 * edge and the outer edge, using the SAME `outerR`/`thickness`/`ringR` geometry the Canvas draws with.
 * A generous half-thickness tolerance on each side keeps the band finger-friendly. Touches in the center
 * hole or outside the wheel return false → the gesture is NOT consumed → the parent scroll keeps it.
 */
private fun onRing(pos: Offset, w: Float, h: Float): Boolean {
    val cx = w / 2f
    val cy = h / 2f
    val outerR = min(cx, cy)
    val thickness = outerR * 0.30f
    val ringR = outerR - thickness / 2f
    val dx = pos.x - cx
    val dy = pos.y - cy
    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
    // Accept the full ring stroke plus a half-thickness pad inward and outward (a forgiving touch band).
    val inner = ringR - thickness
    val outer = ringR + thickness
    return dist in inner..outer
}

/** Build the cached hue sweep-gradient (0..360 in 30° stops). Memoized so it is NOT recomputed per drag frame. */
@Composable
private fun rememberHueSweep(): Brush =
    androidx.compose.runtime.remember {
        val stops = (0..12).map { i -> (i / 12f) to Color.hsv((i * 30f) % 360f, 1f, 1f) }
        Brush.sweepGradient(*stops.toTypedArray())
    }

/**
 * The hue (0..360) at a touch position, measured as the angle from center using the SAME convention as
 * the [Brush.sweepGradient] ring and the handle render (F2): 0° at 3-o'clock (+x), increasing CLOCKWISE.
 * Because screen-space y grows downward, raw `atan2(dy, dx)` already sweeps clockwise — so there is NO
 * rotation offset. 3 o'clock → hue 0, 6 o'clock → 90, 9 o'clock → 180, 12 o'clock → 270.
 */
private fun hueAt(pos: Offset, w: Float, h: Float): Float {
    val cx = w / 2f
    val cy = h / 2f
    val dx = pos.x - cx
    val dy = pos.y - cy
    var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (deg < 0f) deg += 360f
    return deg % 360f
}
