package works.mees.dinghy.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import works.mees.dinghy.calibration.BedMeshModel
import works.mees.dinghy.theme.OklchRamp
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.seriesColor
import works.mees.dinghy.theme.views.ThemeableView

/**
 * The bed-mesh overhead heatmap (CALIB-04 / D-07) — a classic-Views custom-`Canvas` `View`, a
 * deliberate SIBLING of [GraphView] (NOT a fork): GraphView is a 1-D line primitive, this is a 2-D
 * red↔blue heatmap. It reuses GraphView's allocation-free discipline VERBATIM (ADR-0001 high-churn
 * Views exception; the 50×50 interpolated fill is the Adreno-320 fill-rate worst case):
 *  - every [Paint]/[RectF] is pre-allocated in init — `onDraw` NEVER `new`s (the GC-churn trap,
 *    Pitfall 4); the per-cell color is computed and set on the ONE reused [cellPaint] with no
 *    allocation;
 *  - it implements [ThemeableView] — the [BedMeshHeatmapHost] PUSHES the active tokens via
 *    [applyTokens] (recolor + `invalidate()`), so a dark/light/custom flip recolors the ramp/dots
 *    with NO view recreation;
 *  - the repaint trigger is the imperative [setMesh] called on a NEW sample (D-13) — there is NO
 *    animation loop (no `ValueAnimator` / `postInvalidateOnAnimation`), honoring the CLAUDE.md motion
 *    rule (the Adreno-320 floor cannot spare a continuous-fill loop);
 *  - NO raw hex literal lives here (THEME-01) — the ramp endpoints come from role tokens (see below).
 *
 * ## The color ramp (UI-SPEC §4 / D-11) — THEME-DERIVED (15.2-06)
 * The interpolated `mesh_matrix` paints an overhead grid using a perceptually-uniform OKLCH SEQUENTIAL
 * ramp that now FOLLOWS THE THEME: LOW = the active theme's first DATA-POOL color (`seriesColor(1)` —
 * mode-aware, == `pool[0]` in Colorful; the same data-pool source the temp-graph series read), HIGH =
 * the theme ACCENT (`seriesColor(0)`). The pool→accent gradient keeps the height scale OFF pure
 * red/green so a tall spot reads as "tall," NOT as a "FAILED" spot — height is its own sub-system,
 * distinct from the status red/amber/green language. Both endpoints are token-only (THEME-01); no raw
 * hex, no status color. The 32-stop ramp is (re-)baked in [applyTokens] from the CURRENT tokens into
 * [rampStops] via [OklchRamp.themedRampStops], so a dark/light/custom OR palette-mode flip RE-TINTS the
 * heatmap with no view recreation. `onDraw` only INDEXES + lerps between adjacent baked stops (NO OKLCH
 * math per cell per frame — the Adreno-320 fill-rate floor cannot spare a 20-iter gamut search per cell,
 * RESEARCH Pitfall 5; the 32 OKLCH conversions run once per `applyTokens`, never per frame). The
 * `probed_matrix` raw dots draw on top in `--text-3` at low opacity, their radius SHRINKING with grid
 * density (D-08, 3×3 large → 50×50 tiny) so a dense probe set doesn't smear.
 *
 * ## Scale modes (UI-SPEC §4 — pure color-mapping, no re-probe)
 * The [setMesh] `scaleMode` selects the saturation ENDPOINTS the deviations map against — it only
 * changes which Z value reaches full red/full blue, never re-probes:
 *  - [ScaleMode.RELATIVE] — endpoints = the measured min/max (always one 100%-blue + one 100%-red cell).
 *  - [ScaleMode.PLATE] — symmetric about z = 0; the larger |extreme| sets full saturation BOTH ways.
 *  - [ScaleMode.PM_010]/[PM_025]/[PM_050]/[PM_100] — fixed ±0.10/±0.25/±0.50/±1.00 mm deviation from 0.
 *
 * The mapping math ([deviationToRamp]) is pure and host-testable ([works.mees.dinghy.render.BedMeshScaleModeTest]).
 */
class BedMeshHeatmapView(context: Context) : View(context), ThemeableView {

    /** The fixed list of color-scale modes (UI-SPEC §4 / D-09), in cycle order. */
    enum class ScaleMode {
        RELATIVE,
        PLATE,
        PM_010,
        PM_025,
        PM_050,
        PM_100,
    }

    /** Pre-allocated cell paint — its color is RE-SET (no allocation) per cell in onDraw. */
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Pre-allocated probe-dot paint — color/alpha pushed from `--text-3` in applyTokens. */
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Pre-allocated empty-state border paint (faint outline when there is no mesh). */
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }

    /** The single reusable cell rect — re-set per cell, never reallocated (Pitfall 4). */
    private val cellRect = RectF()

    /**
     * The baked OKLCH sequential ramp (D-11): 32 opaque ARGB stops, pool(LOW)→accent(HIGH), OFF the
     * status red/amber/green language. (Re-)baked in [applyTokens] from the CURRENT tokens via
     * [OklchRamp.themedRampStops] (LOW = `seriesColor(1)` data-pool color, HIGH = accent), so the ramp
     * RE-TINTS on every theme / palette-mode change. Seeded here at construction with the locked viridis
     * sequence ([OklchRamp.bedMeshRampStops]) purely as a pre-`applyTokens` placeholder — the host
     * always pushes tokens in its `update` block before the first paint, so the placeholder is never the
     * painted ramp in practice. The 20-iter OKLCH gamut math runs exactly 32 times per `applyTokens`,
     * never per cell per frame (RESEARCH Pitfall 5 — the Adreno-320 fill floor cannot spare a gamut
     * search per cell). `onDraw` only INDEXES this array + [lerpArgb]s between adjacent stops.
     */
    private var rampStops: IntArray = OklchRamp.bedMeshRampStops().toIntArray()

    /** Current heatmap model (the interpolated grid + probe dots) and active scale mode. */
    private var model: BedMeshModel = BedMeshModel()
    private var scaleMode: ScaleMode = ScaleMode.RELATIVE

    /**
     * Push the active tokens (D-06): RE-BAKE the height ramp from the CURRENT tokens so it follows the
     * theme — LOW = `seriesColor(1)` (the first data-pool color; mode-aware, == `pool[0]` in Colorful),
     * HIGH = `seriesColor(0)` (the accent). The 32 OKLCH conversions run here (once per token push), so a
     * dark/light/custom OR palette-mode flip re-tints the heatmap with no view recreation; `onDraw` stays
     * allocation-free (index + lerp only). Also pushes the surrounding CHROME from role tokens —
     * dots=`--text-3` at low opacity (D-08), the empty-state outline=`--outline`. All endpoints are
     * token-derived (THEME-01); no raw hex, no status color.
     */
    override fun applyTokens(t: ThemeTokens) {
        // pool→accent gradient: LOW = first data-pool color (mode-aware), HIGH = accent.
        rampStops = OklchRamp.themedRampStops(
            lowArgb = t.seriesColor(1).toArgb(),
            highArgb = t.seriesColor(0).toArgb(),
        )
        dotPaint.color = t.text3.toArgb()
        dotPaint.alpha = DOT_ALPHA
        emptyPaint.color = t.outline.toArgb()
        invalidate()
    }

    /**
     * Hand the View a new mesh sample + scale mode (D-13 repaint trigger; NOT an animation loop). The
     * heavy work (per-cell color) happens lazily in `onDraw` against the reused paints, so this is just
     * a field swap + `invalidate()`.
     */
    fun setMesh(model: BedMeshModel, scaleMode: ScaleMode) {
        this.model = model
        this.scaleMode = scaleMode
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val grid = model.meshMatrix

        // Empty-state (Pitfall 4): no interpolated mesh → faint outline only (the screen overlays copy).
        if (grid.isEmpty() || grid[0].isEmpty()) {
            canvas.drawRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, emptyPaint)
            return
        }

        val rows = grid.size
        val cols = grid[0].size

        // Saturation endpoints for the active scale mode (pure; no allocation in the hot loop below).
        val (loZ, hiZ) = endpoints(grid, scaleMode)
        val span = (hiZ - loZ)

        // Heatmap fill: one reused RectF + one reused Paint, color re-set per cell. Klipper orders the
        // matrix Y-ASCENDING — row 0 is mesh_min Y (the FRONT/near bed edge). For an overhead view as
        // you face the printer (front at the BOTTOM, rear at the TOP — the Mainsail/Fluidd convention),
        // row 0 must draw at the BOTTOM, so the row index is flipped vertically. Columns map left→right
        // unchanged (col 0 = min X = left). No allocation in the loop.
        val cellW = w / cols
        val cellH = h / rows
        for (r in 0 until rows) {
            val rowVals = grid[r]
            val top = (rows - 1 - r) * cellH
            for (c in 0 until cols) {
                val z = rowVals.getOrElse(c) { loZ }
                val frac = if (span <= 0.0) 0.5 else ((z - loZ) / span).coerceIn(0.0, 1.0)
                cellPaint.color = rampColor(frac.toFloat())
                val left = c * cellW
                cellRect.set(left, top, left + cellW, top + cellH)
                canvas.drawRect(cellRect, cellPaint)
            }
        }

        // Faint probe dots (D-08) — radius shrinks with density (3×3 large → 50×50 tiny). Drawn over
        // the fill in --text-3 low-opacity. Probe rows map onto the same overhead extent as the mesh.
        val probed = model.probedMatrix
        if (probed.isNotEmpty() && probed[0].isNotEmpty()) {
            val pRows = probed.size
            val pCols = probed[0].size
            // Density-scaled radius: bounded by the smaller cell half-extent, scaled down as the grid grows.
            val baseR = max(cellW, cellH)
            val radius = (baseR * DOT_RADIUS_FRAC / max(pRows, pCols)).coerceAtLeast(MIN_DOT_PX)
            for (r in 0 until pRows) {
                // Same vertical flip as the fill: probe row 0 = front → bottom of the overhead view.
                val cy = (pRows - 1 - r + 0.5f) * (h / pRows)
                for (c in 0 until pCols) {
                    val cx = (c + 0.5f) * (w / pCols)
                    canvas.drawCircle(cx, cy, radius, dotPaint)
                }
            }
        }
    }

    /**
     * The [loZ, hiZ] saturation endpoints for [mode] over [grid]. RELATIVE = measured min/max; PLATE =
     * symmetric ±max(|min|,|max|) about 0; the fixed PM_* modes = ±bound about 0. Pure (no Canvas).
     */
    private fun endpoints(grid: List<List<Double>>, mode: ScaleMode): Pair<Double, Double> = when (mode) {
        ScaleMode.RELATIVE -> {
            var lo = Double.POSITIVE_INFINITY
            var hi = Double.NEGATIVE_INFINITY
            for (row in grid) for (z in row) {
                if (z < lo) lo = z
                if (z > hi) hi = z
            }
            if (lo.isInfinite() || hi.isInfinite()) 0.0 to 0.0 else lo to hi
        }
        ScaleMode.PLATE -> {
            var m = 0.0
            for (row in grid) for (z in row) m = max(m, abs(z))
            -m to m
        }
        ScaleMode.PM_010 -> -0.10 to 0.10
        ScaleMode.PM_025 -> -0.25 to 0.25
        ScaleMode.PM_050 -> -0.50 to 0.50
        ScaleMode.PM_100 -> -1.00 to 1.00
    }

    /**
     * Look up the baked OKLCH ramp for a normalized [frac] in 0..1: 0 = LOW (the data-pool color) …
     * 1 = HIGH (the accent), interpolated in OKLCH between. Pure index + [lerpArgb] between the two
     * adjacent baked stops — NO OKLCH math (that ran once per `applyTokens` into [rampStops]; RESEARCH
     * Pitfall 5). Returns a packed ARGB int (no Color object allocation).
     */
    private fun rampColor(frac: Float): Int {
        val n = rampStops.size
        if (n == 1) return rampStops[0]
        val pos = frac.coerceIn(0f, 1f) * (n - 1)
        val i = pos.toInt().coerceIn(0, n - 2)
        return lerpArgb(rampStops[i], rampStops[i + 1], pos - i)
    }

    companion object {
        /** Channel mask for packed-ARGB extraction (8-bit, a bit op — NOT a color literal). */
        private const val CH = 255

        /** Opaque-black placeholder for the pre-`applyTokens` ramp fields (never painted — see field doc). */
        private const val OPAQUE_BLACK = CH shl 24

        /** Probe-dot opacity (0-255) — "faint" per D-08. */
        private const val DOT_ALPHA = 90

        /** Probe-dot radius as a fraction of a mesh cell's larger extent (pre density scaling). */
        private const val DOT_RADIUS_FRAC = 0.9f

        /** Minimum probe-dot radius (px) so a dense grid's dots never vanish entirely. */
        private const val MIN_DOT_PX = 1.5f

        /**
         * Pure deviation→ramp-fraction mapping (host-testable). Maps a Z [deviation] to a 0..1 ramp
         * fraction given the [loZ]/[hiZ] saturation endpoints (0 = full blue/LOW, 1 = full red/HIGH,
         * 0.5 = neutral). A degenerate (≤0) span centers at 0.5. Exposed so the scale-mode math is
         * proven without a Canvas.
         */
        fun deviationToRamp(deviation: Double, loZ: Double, hiZ: Double): Double {
            val span = hiZ - loZ
            return if (span <= 0.0) 0.5 else ((deviation - loZ) / span).coerceIn(0.0, 1.0)
        }

        /** Packed-ARGB linear interpolation (no Color allocation) — `a` at t=0, `b` at t=1. */
        fun lerpArgb(a: Int, b: Int, t: Float): Int {
            val tt = t.coerceIn(0f, 1f)
            val ar = (a ushr 16) and CH
            val ag = (a ushr 8) and CH
            val ab = a and CH
            val br = (b ushr 16) and CH
            val bg = (b ushr 8) and CH
            val bb = b and CH
            val r = (ar + (br - ar) * tt).toInt()
            val g = (ag + (bg - ag) * tt).toInt()
            val bl = (ab + (bb - ab) * tt).toInt()
            return OPAQUE_BLACK or (r shl 16) or (g shl 8) or bl
        }
    }
}
