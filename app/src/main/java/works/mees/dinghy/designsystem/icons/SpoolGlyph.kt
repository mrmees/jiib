package works.mees.dinghy.designsystem.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import works.mees.dinghy.R

/**
 * The single render seam for the color-reactive spool (18.3 D-02..D-05/D-08) — a two-layer glyph that
 * composites a token-tinted **body** (the [R.drawable.spool] flanges, NOT color-reactive — D-02) under a
 * runtime-colored **filament band** drawn in a Compose [Canvas]. This is the ONE place the spool's
 * wound-filament color is rendered; every surface (launcher tile, drawer tile, mid-print shortcut, the
 * SpoolScreen empty-state + detail pane) routes through it in plan 03.
 *
 * ## Why a dedicated composable (not [DinghyIconView])
 * [DinghyIconView]'s drawable branch is a single FLAT `tint: Color` (DinghyIconView.kt:63-69). A two-stop
 * runtime gradient (D-04) is beyond that contract; rather than widen the shared view's `tint` to a `Brush`
 * (rejected — RESEARCH anti-pattern), the band lives here. The body still reuses the exact flat-tint
 * `Icon(painterResource(...), tint = …)` idiom for the neutral flanges.
 *
 * ## The band carve-out (THEME-01, D-10)
 * The band is the ONE element that renders a LITERAL filament color instead of a role token — the
 * sanctioned "filament color is DATA, not chrome" exception (docs/ui_design/THEMING.md). The true hex is
 * NEVER clamped via `brandTint` (D-05 — that would lie about the color). Legibility comes from a thin
 * neutral [keyline] stroke so the band edge reads against any background even when the fill ≈ surface
 * (white PLA on light, black on dark). The body, the keyline, and all surrounding chrome stay token-routed.
 * This mirrors `ColorWheel.kt`'s literal-hue dot + token-outline precedent on the same Adreno-320 budget.
 *
 * ## Empty spool (D-03)
 * An empty [swatches] list draws the body ONLY — the band is OMITTED entirely (no dead-token placeholder).
 * The PRESENCE/ABSENCE of the band IS the "filament loaded" signal.
 *
 * ## Static (Adreno-320 floor)
 * The band/keyline draw once per recomposition — no animation (root CLAUDE.md). The brush is built inside
 * the `DrawScope` (cheap; one instance per surface — no `remember`-cache needed at these sizes).
 *
 * @param swatches resolved filament colors. 0 = empty spool (D-03), 1 = solid (D-08), 2+ = two-stop
 *   gradient across the FIRST TWO only (D-04/D-09 index [0..1]). Caller resolves these (Spoolman→gcode→empty).
 * @param bodyTint the neutral role token for the spool body/flanges (D-02 — body NOT reactive).
 * @param keyline the neutral role token for the band edge stroke (D-05 legibility framing).
 * @param sizeDp the icon-box size (use the established `fsSp(baseSp, t.fs).dp` scale at call sites).
 * @param contentDescription a11y label (or null to mark the glyph decorative).
 */
@Composable
fun SpoolGlyph(
    swatches: List<Color>,
    bodyTint: Color,
    keyline: Color,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(modifier.size(sizeDp)) {
        // (1) Body/flanges — reuses the existing flat-tint Icon contract for the neutral, NON-reactive
        // body (D-02). Pinned to the SAME box as the band Canvas so the two layers share one coordinate
        // frame (Pitfall 3); the band region is empty negative space in the vector, owned by the Canvas.
        Icon(
            painter = painterResource(R.drawable.spool),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            tint = bodyTint,
        )

        // (2) Band — the runtime filament color over the documented band rectangle (D-04/D-08), or
        // nothing at all when empty (D-03). Geometry is derived entirely from `size` so it tracks the
        // body across every sizeDp (ColorWheel idiom).
        Canvas(Modifier.fillMaxSize()) {
            when (val render = bandRenderFor(swatches)) {
                is BandRender.Empty -> Unit // D-03: band OMITTED — draw nothing in the band region.
                is BandRender.Solid -> drawBand(SolidColor(render.color), keyline)
                is BandRender.Gradient -> {
                    val rect = bandRect(size)
                    // Pitfall 4: EXPLICIT start/end bound to the band rectangle. A default-span brush
                    // would stretch the two-stop transition across the whole DrawScope and wash the
                    // gradient out. Horizontal flange-to-flange (RESEARCH Q3) reads naturally side-view.
                    val brush = Brush.linearGradient(
                        0f to render.start,
                        1f to render.end,
                        start = Offset(rect.left, rect.center.y),
                        end = Offset(rect.right, rect.center.y),
                    )
                    drawBand(brush, keyline)
                }
            }
        }
    }
}

/**
 * The wound-filament band rectangle, as the EXACT fixed fractions of the icon box that `spool.xml` left as
 * empty negative space (left 0.34 / right 0.66 / top 0.18 / bottom 0.82 — see the drawable header). Pinning
 * to these identical fractions makes the Canvas band register seam-free with the vector body's gap
 * (Pitfall 3). Derived from `size` so it tracks any `sizeDp`.
 */
private fun bandRect(size: Size): Rect = Rect(
    left = size.width * BAND_LEFT_FRACTION,
    top = size.height * BAND_TOP_FRACTION,
    right = size.width * BAND_RIGHT_FRACTION,
    bottom = size.height * BAND_BOTTOM_FRACTION,
)

/**
 * Fill the band rectangle with [brush], then stroke its edge with the neutral [keyline] so the band reads
 * against any background even when the fill ≈ surface (D-05). The keyline width scales with the box so it
 * holds at launcher-tile size without dominating (RESEARCH Q3 ~1–1.5dp-equivalent).
 */
private fun DrawScope.drawBand(brush: Brush, keyline: Color) {
    val rect = bandRect(size)
    val path = Path().apply { addRect(rect) }
    drawPath(path, brush)
    drawPath(path, color = keyline, style = Stroke(width = size.minDimension * KEYLINE_FRACTION))
}

/** Band-rectangle fractions of the icon box — MUST match `res/drawable/spool.xml`'s documented gap. */
private const val BAND_LEFT_FRACTION = 0.34f
private const val BAND_RIGHT_FRACTION = 0.66f
private const val BAND_TOP_FRACTION = 0.18f
private const val BAND_BOTTOM_FRACTION = 0.82f

/** Keyline stroke width as a fraction of the box's smaller dimension (~1.3dp at a 40dp tile). */
private const val KEYLINE_FRACTION = 0.033f

/**
 * The pure swatch-count → band-render decision (D-03/D-04/D-08/D-09), factored out of the Compose
 * `Canvas` so it is host-testable without a Compose harness. See [SpoolGlyphTest].
 */
internal sealed interface BandRender {
    /** No swatches → the band is omitted entirely (D-03 empty spool). */
    data object Empty : BandRender

    /** Exactly one swatch → a flat solid fill (D-08). */
    data class Solid(val color: Color) : BandRender

    /** Two or more swatches → a two-stop gradient across the FIRST TWO only (D-04/D-09). */
    data class Gradient(val start: Color, val end: Color) : BandRender
}

/**
 * Decide how the band renders for a resolved [swatches] list:
 *   - empty     → [BandRender.Empty]                 (D-03)
 *   - size == 1 → [BandRender.Solid]                 (D-08)
 *   - size >= 2 → [BandRender.Gradient] of `[0]`,`[1]` (D-04/D-09 — first two only, ignores the rest)
 */
internal fun bandRenderFor(swatches: List<Color>): BandRender = when (swatches.size) {
    0 -> BandRender.Empty
    1 -> BandRender.Solid(swatches[0])
    else -> BandRender.Gradient(swatches[0], swatches[1])
}
