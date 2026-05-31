package works.mees.dinghy.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.compose.ui.graphics.toArgb
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.views.ThemeableView

/**
 * The live line graph (D-11) — a classic-Views custom-`Canvas` `View`, the HIGH-churn render
 * primitive (ADR 0001 routes the temperature graph to Views: ~2x lower p95 frame time on the real
 * flox than the Compose equivalent). It draws the bounded [RingBuffer] snapshot (D-12) and is the
 * Views half of the "shared render" split — the Compose [ProgressRing] is the low-churn other half.
 * Phase 4 Print Status consumes it; Phase 5 Temperature EXTENDS it into the full multi-series graph.
 *
 * Theming (D-06): implements [ThemeableView]. The [GraphViewHost] PUSHES the active tokens via
 * [applyTokens] (sets each pre-allocated `Paint`'s color from the tokens, then `invalidate()`), so a
 * dark/light/custom flip recolors the Canvas graph with NO view recreation — the same instance just
 * repaints. The line color is `accent.toArgb()`; no raw hex literal lives here (THEME-01).
 *
 * Allocation-free draw (Pitfall 4): ONE reusable [Path] (`rewind()` each draw — never `Path()` in
 * `onDraw`), ONE pre-allocated stroke `Paint` and ONE fill `Paint`. `onDraw` walks the sanitized
 * working copy and maps it to the canvas with the reused path — no per-frame allocation, the GC-churn
 * tail-latency trap on the Adreno-320 floor.
 *
 * Motion (D-13): [setData] is the ONLY repaint trigger, called when a new THROTTLED sample arrives
 * (~2-4 Hz), NOT every Choreographer frame. There is no animation loop.
 *
 * Input-edge contract (Pitfall 4 — pinned HERE so every later consumer inherits it): [setData] runs
 * the snapshot through [sanitize] ONCE, producing the bounded finite working copy that `onDraw`
 * draws. See [sanitize] for the exact empty / 1-point / constant-series / NaN-Infinity / downsample
 * rules. `onDraw` therefore never sees a non-finite value, never divides by a zero range, and never
 * draws more vertices than the View is pixels wide.
 */
class GraphView(context: Context) : View(context), ThemeableView {

    /** The single reusable line path — rewound each draw, never reallocated (Pitfall 4). */
    private val linePath = Path()

    /** The single reusable filled-area path (translucent fill under the line). */
    private val areaPath = Path()

    /** Pre-allocated stroke paint; color pushed from tokens via [applyTokens]. */
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density // dp() density idiom (ViewsBenchScene)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    /** Pre-allocated translucent fill paint for the area under the line; color from tokens. */
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * The sanitized, downsample-capped, finite working copy `onDraw` reads. Produced ONCE per
     * throttled sample in [setData] so the UI-thread copy stays bounded and `onDraw` allocation-free.
     */
    private var data: FloatArray = FloatArray(0)

    /**
     * Push the active tokens (D-06): recolor the pre-allocated paints from role tokens and repaint.
     * No raw color literal — line is `accent`, fill is the same hue at low alpha (the `.g-area` tint).
     */
    override fun applyTokens(t: ThemeTokens) {
        val accentArgb = t.accent.toArgb()
        linePaint.color = accentArgb
        // Translucent fill under the line — the accent hue at a low alpha (cheap single-fill, Pitfall 4).
        fillPaint.color = accentArgb
        fillPaint.alpha = FILL_ALPHA
        invalidate()
    }

    /**
     * Hand the View a new ring-buffer snapshot. Runs [sanitize] ONCE (cap to pixel width + drop
     * NaN/Infinity) so `onDraw` is allocation-free and bounded, then repaints. Called only on a new
     * throttled (~2-4 Hz) sample — D-13, NOT per Choreographer frame.
     */
    fun setData(snapshot: FloatArray) {
        // width may be 0 before layout; fall back so an early sample is still capped to *something*.
        val cap = if (width > 0) width else DEFAULT_PIXEL_CAP
        data = sanitize(snapshot, cap)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val pts = data
        val n = pts.size
        if (n == 0) return // empty → draw nothing (input-edge contract)

        val w = width.toFloat()
        val h = height.toFloat()

        // Value range for the y-mapping; guard a zero-height (constant) series against div-by-zero.
        var minV = pts[0]
        var maxV = pts[0]
        for (i in 1 until n) {
            val v = pts[i]
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }
        val range = maxV - minV
        // value → y: high values near the top. A zero range centers the flat line cleanly.
        fun yOf(v: Float): Float =
            if (range <= 0f) h * 0.5f else h - ((v - minV) / range) * h

        if (n == 1) {
            // 1-point → a single dot (no line needs ≥2 points). Centered horizontally.
            canvas.drawPoint(w * 0.5f, yOf(pts[0]), linePaint)
            return
        }

        val dx = w / (n - 1)

        // Reuse the single paths — rewind, never allocate (Pitfall 4).
        linePath.rewind()
        areaPath.rewind()
        areaPath.moveTo(0f, h) // start the fill at the bottom-left

        for (i in 0 until n) {
            val x = dx * i
            val y = yOf(pts[i])
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            areaPath.lineTo(x, y)
        }
        areaPath.lineTo((n - 1) * dx, h) // close the fill down to the bottom-right
        areaPath.close()

        canvas.drawPath(areaPath, fillPaint) // single cheap filled area (Pitfall 4)
        canvas.drawPath(linePath, linePaint)
    }

    companion object {
        /** Translucent fill alpha for the `.g-area` tint under the line (0-255). */
        private const val FILL_ALPHA = 40

        /** Fallback horizontal cap before the View has been laid out (`width == 0`). */
        private const val DEFAULT_PIXEL_CAP = 256

        /**
         * The pure, Canvas-free sanitize + downsample helper (testable host-side — see
         * `GraphDownsampleTest`). It pins the input-edge contract every later consumer inherits:
         *
         *  - drops every `NaN`/`Infinity` sample BEFORE drawing (the draw path only sees finite values);
         *  - caps the rendered vertex count to [pixelWidth] (never more vertices than horizontal pixels),
         *    so the UI-thread working copy stays bounded regardless of the buffer length;
         *  - an empty input (or one that is all non-finite) returns an EMPTY array (→ draw nothing);
         *  - a constant-value (zero-range) series survives intact — the cap/filter introduce no NaN
         *    (the zero-range guard lives in the y-mapping, not here).
         *
         * The cap uses uniform stride sampling (`floor(i * stride)`), keeping the newest and oldest
         * endpoints and an evenly-spaced subset between — cheap, allocation-bounded, deterministic.
         *
         * @param snapshot   oldest→newest samples from [RingBuffer.snapshot].
         * @param pixelWidth the horizontal pixel budget (the cap). Must be ≥ 1 to render anything.
         * @return a bounded, finite `FloatArray` of length ≤ [pixelWidth].
         */
        fun sanitize(snapshot: FloatArray, pixelWidth: Int): FloatArray {
            if (snapshot.isEmpty() || pixelWidth < 1) return FloatArray(0)

            // 1) Filter out non-finite samples (NaN / ±Infinity) — keep finite values in order.
            var finiteCount = 0
            for (v in snapshot) if (v.isFinite()) finiteCount++
            if (finiteCount == 0) return FloatArray(0)

            val finite: FloatArray
            if (finiteCount == snapshot.size) {
                finite = snapshot
            } else {
                finite = FloatArray(finiteCount)
                var w = 0
                for (v in snapshot) if (v.isFinite()) finite[w++] = v
            }

            // 2) Cap to the pixel width via uniform stride downsample. Already within budget → as-is.
            if (finite.size <= pixelWidth) return finite

            val out = FloatArray(pixelWidth)
            // Spread pixelWidth picks across [0, finite.size-1] inclusive (keeps both endpoints).
            val denom = (pixelWidth - 1).coerceAtLeast(1)
            val span = (finite.size - 1).toFloat()
            for (i in 0 until pixelWidth) {
                val src = Math.round(i * span / denom).toInt().coerceIn(0, finite.size - 1)
                out[i] = finite[src]
            }
            return out
        }
    }
}
