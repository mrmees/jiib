package works.mees.jiib.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.fsSp
import works.mees.jiib.theme.seriesColor
import works.mees.jiib.theme.views.ThemeableView
import works.mees.jiib.theme.views.typeface

/**
 * The live line graph (D-11) — a classic-Views custom-`Canvas` `View`, the HIGH-churn render
 * primitive (ADR 0001 routes the temperature graph to Views: ~2x lower p95 frame time on the real
 * flox than the Compose equivalent). It draws up to [MAX_TRACES] bounded sensor series (D-12) on ONE
 * shared axis, and is the Views half of the "shared render" split — the Compose [ProgressRing] is
 * the low-churn other half. Phase 4 Print Status consumes it as a single-trace sparkline; Phase 5
 * Temperature (05-04) EXTENDS it IN PLACE to the multi-trace graph (D-05 — the primitive is never
 * forked; the allocation-free contract and the perf gate stay anchored to this ONE View).
 *
 * Multi-trace (05-04 / D-05): up to [MAX_TRACES] traces (nozzle/bed/chamber) draw on one shared
 * X time-window and one FIXED shared Y-range. Each trace owns a pre-allocated stroke [Path] + [Paint]
 * (one [linePaths]/[linePaints] slot); [onDraw] `rewind()`s and rebuilds each per draw — never a
 * `Path()`/`Paint()` in `onDraw` (Pitfall 4 — the GC-churn tail-latency trap on the Adreno-320 floor).
 *
 * Fixed Y-range (Phase-4 gap G-1 fix): the OLD per-frame window min/max auto-range made a steady
 * temperature noise-fill the whole panel (and a lone sample render mid-screen). It is REPLACED by a
 * FIXED shared [yRange] defaulting to [DEFAULT_Y_MIN]..[DEFAULT_Y_MAX] (0..350 °C) — covering the
 * `setHeater` 0..350 clamp ceiling so a legal nozzle target never clips off-screen (a 0..300 default
 * would; Codex finding). All traces map against this one range; steady temps now sit calmly.
 *
 * Setpoint line (D-04): per trace an OPTIONAL current-target horizontal DASHED line (see
 * [setSetpoints]) draws at that target's y-mapped position in the trace's color. The
 * [DashPathEffect] is built ONCE in init and held on [setpointPaint] — never allocated in `onDraw`.
 * Historical target stepping is intentionally NOT rendered (a deliberate fill-budget narrowing — the
 * contract is a CURRENT-setpoint line, not a target series).
 *
 * Theming (D-05/D-06): implements [ThemeableView]. The [GraphViewHost] PUSHES the active tokens via
 * [applyTokens], which recolors each trace from the accent-led N-series rule
 * [seriesColor][ThemeTokens.seriesColor] — trace `i` = `seriesColor(i)`, so trace 0 (nozzle) is ACCENT
 * in every mode, trace 1 = pool[0], trace 2 = pool[1] (supersedes the Phase-15 `pool[i % size]` binding)
 * — then `invalidate()`s, so a dark/light/custom flip recolors all traces with NO view recreation.
 * No raw hex literal lives here (THEME-01).
 *
 * Allocation-free draw (Pitfall 4): [MAX_TRACES] reusable [Path]s + [Paint]s, ONE fill [Path]/[Paint]
 * (primary trace only — fill cost is the Adreno-320 fill-rate suspect, so it is bounded to one trace),
 * and ONE pre-allocated dashed [setpointPaint]. `onDraw` walks the sanitized working copies and maps
 * them to the canvas with the reused paths — no per-frame allocation.
 *
 * Motion (D-13): [setData] is the repaint trigger, called when a new THROTTLED sample arrives
 * (~2-4 Hz), NOT every Choreographer frame. There is no animation loop.
 *
 * Input-edge contract (Pitfall 4 — pinned HERE so every later consumer inherits it): [setData] runs
 * EACH series through [sanitize] ONCE, producing the bounded finite working copies `onDraw` draws.
 * See [sanitize] for the exact empty / 1-point / constant-series / NaN-Infinity / downsample rules.
 * `onDraw` therefore never sees a non-finite value and never draws more vertices than pixels wide.
 */
class GraphView(context: Context) : View(context), ThemeableView {

    /** Build one styled stroke paint (extracted so [ensureTraceCapacity] reuses it when the pool grows). */
    private fun newLinePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density // dp() density idiom (ViewsBenchScene)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    /**
     * Per-trace stroke paths, rewound each draw, never reallocated PER DRAW. Pre-sized to [MAX_TRACES]
     * (the common nozzle/bed/chamber case) and GROWN by [ensureTraceCapacity] only when a sample carries
     * more traces (a user added monitored sensors) — growth is a rare add-time event, never per frame, so
     * the allocation-free draw contract (Pitfall 4) holds.
     */
    private var linePaths: Array<Path> = Array(MAX_TRACES) { Path() }

    /** Per-trace stroke paints (parallel to [linePaths]); colors pushed from tokens in [applyTokens]. */
    private var linePaints: Array<Paint> = Array(MAX_TRACES) { newLinePaint() }

    /**
     * Grow the per-trace path/paint pools to at least [n] slots. No-op when already large enough, so it
     * allocates ONLY when the monitored-trace count rises past the current capacity (Pitfall 4: never
     * per draw). Newly-added paints are colored immediately from the current tokens + overrides.
     */
    private fun ensureTraceCapacity(n: Int) {
        if (n <= linePaints.size) return
        val oldPaints = linePaints
        val oldPaths = linePaths
        linePaints = Array(n) { i -> if (i < oldPaints.size) oldPaints[i] else newLinePaint() }
        linePaths = Array(n) { i -> if (i < oldPaths.size) oldPaths[i] else Path() }
        reapplyOverrides() // color the freshly-allocated paints from the current tokens + overrides
    }

    /** The single reusable filled-area path (translucent fill under the PRIMARY trace only). */
    private val areaPath = Path()

    /** Pre-allocated translucent fill paint for the area under the primary trace; color from tokens. */
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * Pre-allocated DASHED paint for the per-trace current-setpoint line (D-04). The
     * [DashPathEffect] is built ONCE here in init — NEVER in `onDraw` (Pitfall 4). Its color is set
     * to the drawn trace's color per line just before drawing.
     */
    private val setpointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(SETPOINT_DASH_ON, SETPOINT_DASH_OFF), 0f)
    }

    /** A reusable path for the horizontal setpoint line (rewound per line; never reallocated). */
    private val setpointPath = Path()

    /** Display density, cached once (avoids re-reading metrics per draw). */
    private val density = resources.displayMetrics.density

    /**
     * Pre-allocated paint for the optional min/max Y-axis value labels ([showAxisLabels]). GeistMono
     * (tabular numerals), RIGHT-aligned so the labels hug the graph's right edge and stay clear of the
     * Focus|Field center seam. Color is pushed from the muted [ThemeTokens.text3] in [applyTokens].
     */
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = JiibType.statValue.baseSp * density // default; re-set --fs-scaled in applyTokens
        textAlign = Paint.Align.RIGHT
        typeface = JiibType.statValue.typeface(context)
    }

    /**
     * The sanitized, downsample-capped, finite working copies `onDraw` reads — one per trace.
     * Produced ONCE per throttled sample in [setData] so the UI-thread copies stay bounded and
     * `onDraw` allocation-free. Index 0 is the primary trace (the one that gets the area fill).
     */
    private var series: List<FloatArray> = emptyList()

    /**
     * Trace draw order (indices into [series]), highest current value FIRST so the LOWEST trace draws
     * LAST (on top) — its fill + line win the shared lower band (05 UI tweak: bed-over-nozzle when the
     * bed is cooler, and vice-versa). Computed ONCE per sample in [setData] (never in `onDraw`).
     */
    private var drawOrder: IntArray = IntArray(0)

    /**
     * Per-trace current setpoint (target) value; `null` = no setpoint line for that trace (D-04).
     * Aligned by index with [series]. Set via [setSetpoints].
     */
    private var setpoints: List<Float?> = emptyList()

    /**
     * The FIXED shared Y-range every trace maps against (Phase-4 G-1 fix). Defaults to
     * [DEFAULT_Y_MIN]..[DEFAULT_Y_MAX] (0..350 °C) — covers the `setHeater` clamp ceiling so a legal
     * target never clips. Settable (the Temperature panel may narrow it). A degenerate (≤0) range is
     * guarded in [onDraw] (centers the trace) so this can never divide by zero.
     */
    var yRange: ClosedFloatingPointRange<Float> = DEFAULT_Y_MIN..DEFAULT_Y_MAX
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /**
     * Whether to paint the translucent `.g-area` fill under the PRIMARY trace (canonical aesthetic:
     * `hifi.css .plot .g-area{ opacity:.16 }` ≈ [FILL_ALPHA]/255). Defaults `true` — the design
     * contract. This is the ISOLATION LEVER for the fill-rate gate (T-03-08 / D-06 05-08): a
     * near-full-region translucent fill is the Adreno-320 fill-rate suspect, so the perf scene can
     * flip it off to ATTRIBUTE the cost (fill-on vs fill-off) on the real device. Only ONE trace is
     * ever filled (Pitfall 4 — bound fill cost). Production leaves it `true`. Setting it `invalidate()`s.
     */
    var drawArea: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /**
     * Whether to draw the min/max Y-axis value labels (the current [yRange] bounds) at the right edge —
     * max top-right, min bottom-right. Default `false` so the small Print Status sparkline stays
     * label-free; the full Temperature graph turns it on. Setting it `invalidate()`s.
     */
    var showAxisLabels: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /**
     * The last [ThemeTokens] applied — used by [applyTokens] to short-circuit the full paint update +
     * `invalidate()` on every recomposition when tokens are UNCHANGED (D-12 / AndroidView interop
     * hygiene). A dark→light or custom theme swap creates a new [ThemeTokens] instance with different
     * [Color] / `pool` values → structural `==` returns `false` → recolor fires correctly (Pitfall 6).
     * `null` on construction forces the first call through unconditionally.
     */
    private var lastTokens: ThemeTokens? = null

    /**
     * Per-trace chosen color overrides (D-14): index-aligned with the series passed to [setData].
     * A `null` entry means "fall back to [seriesColor][ThemeTokens.seriesColor](i)" for that trace.
     * Applied on top of the token-derived defaults in [applyTokens] and recomputed in
     * [setTraceColorOverrides]. Stored for the "override-wins after token change" recompute.
     */
    private var lastOverrides: List<Int?> = emptyList()

    /**
     * Set per-trace drawn colors (D-14 — the graph draws the CHOSEN color). Equality-guarded:
     * returns early when [overrides] is structurally equal to the last-applied list (preserves
     * the Phase-22 D-12 factory-runs-once / push-equals-skip discipline). On change, recomputes
     * each `linePaints[i].color` = override ?: `seriesColor(i)` (override-wins over the token
     * default) and `invalidate()`s. The setpoint dashes already read `linePaints[t].color` at
     * draw time (GraphView.kt:350), so they inherit the override for free.
     *
     * The [overrides] list is index-aligned with the visible-trace series pushed via [setData]
     * (the screen builds ONE aligned model filtering series + setpoints + colors together so that
     * `overrides[i]` belongs to the same sensor as `series[i]` — finding 4 / D-14 alignment rule).
     *
     * @param overrides ARGB Int per trace (index-aligned with [setData] series); `null` → token default.
     */
    fun setTraceColorOverrides(overrides: List<Int?>) {
        // Equality guard — same structure: no paint churn, no invalidate (Phase-22 D-12 discipline).
        if (overrides == lastOverrides) return
        lastOverrides = overrides
        reapplyOverrides()
        invalidate()
    }

    /**
     * Recompute each `linePaints[i].color` applying the current [lastOverrides] on top of the
     * token-derived `seriesColor(i)` default. Called from both [applyTokens] (tokens changed, need
     * to re-layer overrides) and [setTraceColorOverrides] (overrides changed). The fill paint's color
     * is also recomputed from whatever index 0's final color is.
     */
    private fun reapplyOverrides() {
        val t = lastTokens ?: return // no tokens yet — will be re-called when tokens first arrive
        for (i in linePaints.indices) {
            val override = lastOverrides.getOrNull(i)
            linePaints[i].color = override ?: t.seriesColor(i).toArgb()
        }
        // Translucent fill under the primary trace — use index 0's final (possibly overridden) color.
        fillPaint.color = linePaints[0].color
        fillPaint.alpha = FILL_ALPHA
    }

    /**
     * Push the active tokens (D-05/D-06): recolor each pre-allocated trace paint from the accent-led
     * N-series rule [seriesColor][ThemeTokens.seriesColor] and repaint. No raw color literal — trace `i`
     * reads `t.seriesColor(i)`, so trace 0 (the primary/nozzle channel) is ACCENT in every palette mode,
     * trace 1 = pool[0] (bed), trace 2 = pool[1] (chamber), wrapping infinitely past the pool (D-09).
     * This gives each sensor a STABLE color by canonical order (same sensor = same color everywhere; this
     * index MUST match the Print-Status nozzle readout and the Temperature legend trace index). The fill
     * takes the PRIMARY trace's color (`seriesColor(0)` = accent) at low alpha.
     *
     * D-06 supersession: this replaces the Phase-15 `pool[i % size]` trace binding (and its `pool[0]`
     * fill) — the lead trace is now accent, not pool[0], so it stays put across a mode switch and shares
     * one hue with both the Print-Status nozzle readout and the Temperature legend trace-0 (supersedes
     * Phase-15 D-2/D-13). `seriesColor` carries its own empty-pool guard (falls back to accent).
     *
     * D-12 guard: returns early (no paint update, no `invalidate()`) when [t] is structurally equal to
     * the last-applied tokens — `ThemeTokens` is an `@Immutable data class` whose generated `equals()`
     * covers EVERY field including `pool: List<Color>`, so the guard is correct.
     *
     * D-14 override-wins: after updating token-derived defaults, [reapplyOverrides] re-layers any
     * active [lastOverrides] on top, so a theme swap does not reset user-chosen trace colors.
     */
    override fun applyTokens(t: ThemeTokens) {
        if (t == lastTokens) return
        lastTokens = t
        // Set token-derived defaults first, then re-layer any active overrides on top (D-14).
        for (i in linePaints.indices) {
            linePaints[i].color = t.seriesColor(i).toArgb()
        }
        fillPaint.color = t.seriesColor(0).toArgb()
        fillPaint.alpha = FILL_ALPHA
        // D-14: re-apply any active per-trace color overrides OVER the just-set token defaults.
        // reapplyOverrides() re-reads lastTokens (now updated above) and lastOverrides.
        reapplyOverrides()
        labelPaint.color = t.text3.toArgb() // muted axis-label color (THEME-01)
        labelPaint.textSize = fsSp(JiibType.statValue.baseSp, t.fs) * density // statValue role — --fs-scaled
        invalidate()
    }

    /**
     * Single-trace back-compat entry (Phase-4 Print Status sparkline). Delegates to the list form
     * with one series; no setpoint line is drawn for the sparkline.
     */
    fun setData(snapshot: FloatArray) {
        setData(listOf(snapshot))
    }

    /**
     * Hand the View N new ring-buffer snapshots (one per trace, index 0 = primary). Runs [sanitize]
     * ONCE on EACH series (cap to pixel width + drop NaN/Infinity) so `onDraw` is allocation-free and
     * bounded, then repaints. Called only on a new throttled (~2-4 Hz) sample — D-13, NOT per frame.
     * The path/paint pools GROW to the series count via [ensureTraceCapacity] (no fixed cap — the
     * monitored set is unbounded; the user manages graph load via per-sensor visibility).
     */
    fun setData(series: List<FloatArray>) {
        // width may be 0 before layout; fall back so an early sample is still capped to *something*.
        val cap = if (width > 0) width else DEFAULT_PIXEL_CAP
        ensureTraceCapacity(series.size) // grow the pools if this sample carries more traces than before
        val bounded = ArrayList<FloatArray>(series.size)
        for (i in series.indices) {
            bounded.add(sanitize(series[i], cap))
        }
        this.series = bounded
        this.drawOrder = orderByLastValueDesc(bounded)
        invalidate()
    }

    /**
     * Order trace indices by their latest (rightmost) finite value, DESCENDING — highest first. Drawing
     * in this order means the lowest-value trace is painted last (on top). Small insertion sort (≤3
     * traces); allocates one [IntArray] per sample in [setData] — never touched in `onDraw` (Pitfall 4).
     */
    private fun orderByLastValueDesc(series: List<FloatArray>): IntArray {
        val idx = IntArray(series.size) { it }
        fun key(i: Int): Float {
            val p = series[i]
            return if (p.isEmpty()) Float.NEGATIVE_INFINITY else p[p.size - 1]
        }
        for (a in 1 until idx.size) {
            val cur = idx[a]
            val k = key(cur)
            var b = a - 1
            while (b >= 0 && key(idx[b]) < k) {
                idx[b + 1] = idx[b]
                b--
            }
            idx[b + 1] = cur
        }
        return idx
    }

    /**
     * Set the per-trace current setpoint (target) values for the dashed setpoint line (D-04). Aligned
     * by index with the series passed to [setData]; a `null` (or absent) entry draws no line for that
     * trace. Repaints.
     */
    fun setSetpoints(setpoints: List<Float?>) {
        this.setpoints = setpoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val all = series

        val w = width.toFloat()
        val h = height.toFloat()

        // FIXED shared Y-range (G-1 fix) — map all traces against it; guard a degenerate range.
        val minV = yRange.start
        val maxV = yRange.endInclusive
        val range = maxV - minV
        // value → y: high values near the top. A non-positive range centers cleanly (no div-by-zero).
        fun yOf(v: Float): Float =
            if (range <= 0f) h * 0.5f else (h - ((v - minV) / range) * h).coerceIn(0f, h)

        // Draw highest-value trace FIRST so the lowest sits on top (D-?: 05 UI tweak). Each trace fills
        // to the baseline in its OWN translucent color, then strokes its line — so the cooler trace's
        // fill/line wins the shared lower band. (Per-trace fill is extra Adreno-320 fill-rate vs the old
        // single-trace fill — re-gated on flox; `drawArea` still toggles the whole fill set off.)
        val order = drawOrder
        for (oi in order.indices) {
            val t = order[oi]
            if (t >= all.size || t >= linePaints.size) continue // defensive (pools grow with the set)
            val pts = all[t]
            val n = pts.size
            if (n == 0) continue // empty trace → draw nothing for it (input-edge contract)

            val linePaint = linePaints[t]

            if (n == 1) {
                // 1-point → a single dot (a line needs ≥2 points). Centered horizontally.
                canvas.drawPoint(w * 0.5f, yOf(pts[0]), linePaint)
                continue
            }

            val dx = w / (n - 1)

            // Reuse this trace's single path — rewind, never allocate (Pitfall 4).
            val linePath = linePaths[t]
            linePath.rewind()

            if (drawArea) {
                areaPath.rewind()
                areaPath.moveTo(0f, h) // start the fill at the bottom-left
            }

            for (i in 0 until n) {
                val x = dx * i
                val y = yOf(pts[i])
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                if (drawArea) areaPath.lineTo(x, y)
            }

            if (drawArea) {
                areaPath.lineTo((n - 1) * dx, h) // close the fill down to the bottom-right
                areaPath.close()
                fillPaint.color = linePaint.color // this trace's hue...
                fillPaint.alpha = FILL_ALPHA      // ...at the canonical low alpha
                canvas.drawPath(areaPath, fillPaint)
            }
            canvas.drawPath(linePath, linePaint)
        }

        // Per-trace dashed current-setpoint line (D-04) — drawn over the traces in each trace's color.
        for (t in setpoints.indices) {
            if (t >= linePaints.size) break // setpoints are index-aligned with the (grown) trace pools
            val target = setpoints[t] ?: continue
            if (!target.isFinite()) continue
            val y = yOf(target)
            setpointPaint.color = linePaints[t].color
            setpointPath.rewind()
            setpointPath.moveTo(0f, y)
            setpointPath.lineTo(w, y)
            canvas.drawPath(setpointPath, setpointPaint)
        }

        // Optional Y-axis value labels (05 UI tweak): the current [yRange] bounds, RIGHT-aligned to hug
        // the graph's right edge (clear of the Focus|Field center seam). Max top, min bottom. Two cheap
        // drawText calls — the dynamic range is computed upstream (TemperatureHolder), the View just paints.
        if (showAxisLabels) {
            val pad = LABEL_PAD * density
            canvas.drawText("${maxV.roundToInt()}°", w - pad, labelPaint.textSize + pad, labelPaint)
            canvas.drawText("${minV.roundToInt()}°", w - pad, h - pad, labelPaint)
        }
    }

    companion object {
        /**
         * INITIAL per-trace pool size (the common nozzle/bed/chamber case) — NOT a hard cap. The pools
         * grow past this via [ensureTraceCapacity] when the user adds monitored sensors; the monitored
         * set is unbounded and graph load is managed per-sensor via visibility (no fixed trace limit).
         */
        const val MAX_TRACES = 3

        /** Default fixed Y-range floor (°C). */
        const val DEFAULT_Y_MIN = 0f

        /**
         * Default fixed Y-range ceiling (°C) — covers the `setHeater` 0..350 clamp ceiling so a legal
         * nozzle target never clips off-screen (a 0..300 default would; Codex finding / G-1 fix).
         */
        const val DEFAULT_Y_MAX = 350f

        /** Translucent fill alpha for the `.g-area` tint under the primary trace (0-255). */
        private const val FILL_ALPHA = 40

        /** Dash on/off lengths for the setpoint line (px). */
        private const val SETPOINT_DASH_ON = 8f
        private const val SETPOINT_DASH_OFF = 6f

        /** Y-axis label inset from the graph edge (dp-equivalent; scaled by density). */
        private const val LABEL_PAD = 6f

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
