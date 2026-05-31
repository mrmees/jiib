package works.mees.dinghy.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import works.mees.dinghy.theme.compose.LocalTokens

/**
 * The print-progress ring (D-11) — a single Compose `Canvas` arc, the LOW-churn render primitive
 * (one value, one redraw). It is the Compose half of the "shared render" split: the ring lives in
 * Compose (cheap, themeable inline via [LocalTokens]); the high-churn line graph lives in classic
 * Views ([GraphView], ADR 0001). Phase 4 Print Status consumes this directly.
 *
 * Motion (D-13): there is intentionally NO animation here — no `animate*AsState`, no
 * `rememberInfiniteTransition`, no tween. The arc redraws ONLY when [progress] changes, and
 * [progress] arrives already throttled (~2-4 Hz) from the caller's `StateFlow`. On the Adreno-320
 * floor a per-update sweep tween would burn fill rate we cannot spare; the value-driven redraw
 * carries the signal. Glow, if added, is a STATIC shadow layer — never a looping sheen.
 *
 * Sacred square (LAYOUT.md NON-NEGOTIABLE 2): the ring CONTENT is wrapped in `aspectRatio(1f)` so
 * the arc is always a true circle centered in its cell — we make the ring square, never the region.
 *
 * Tokens (THEME-01): the track reads `surface2` and the arc reads `accent` via [LocalTokens] — no
 * raw color literal ever appears here, so a dark/light/custom theme swap recolors the ring for free.
 * Stroke width is a fraction of `size.minDimension` (ratio-only sizing, LAYOUT.md NON-NEGOTIABLE 3)
 * so it scales with the cell rather than a hardcoded px.
 *
 * Edge tolerance: [progress] is coerced into `0f..1f` and a `NaN` is treated as `0f`, so an
 * out-of-range or malformed value can never produce a malformed sweep — it just clamps.
 *
 * @param progress fraction complete in `0f..1f` (out-of-range/NaN is clamped, never crashes).
 * @param modifier caller layout; the `aspectRatio(1f)` square is applied on top of it.
 */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    // Sanitize: NaN → 0f, then clamp to the legal sweep range. No malformed arc can escape.
    val safe = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    Canvas(modifier.aspectRatio(1f)) {
        // Ratio-only stroke: a fraction of the smaller dimension, never a hardcoded px.
        val strokeWidth = size.minDimension * STROKE_FRACTION
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        // Track: full 360° in surface2 (the well the arc rides on).
        drawArc(
            color = t.surface2,
            startAngle = START_ANGLE,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke,
        )
        // Progress arc: accent, from 12-o'clock sweeping clockwise by the completed fraction.
        drawArc(
            color = t.accent,
            startAngle = START_ANGLE,
            sweepAngle = 360f * safe,
            useCenter = false,
            style = stroke,
        )
    }
}

/** 12-o'clock origin so progress fills clockwise from the top. */
private const val START_ANGLE = -90f

/** Ring stroke as a fraction of the cell's smaller dimension (mirrors hifi.css `.ring` proportion). */
private const val STROKE_FRACTION = 0.06f
