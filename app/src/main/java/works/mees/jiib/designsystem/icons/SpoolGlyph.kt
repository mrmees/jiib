package works.mees.jiib.designsystem.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import works.mees.jiib.R

/**
 * The single render seam for the color-reactive spool (18.3 D-02..D-05/D-08), built on the owner's
 * front-view source art (`img/spool.svg`). It composites two layers in one coordinate frame:
 *
 *   1. the **filament spiral** (the wound coil) — drawn FIRST/behind, stroked with the runtime filament
 *      color (or a two-stop gradient for multi-color), or OMITTED entirely when empty; and
 *   2. the **spool body disc** ([R.drawable.spool], with three evenOdd window holes) — drawn on TOP,
 *      flat-tinted with a neutral role token. The disc occludes the spiral except through its windows +
 *      center, so the filament color reads exactly where the real spool shows its wound filament.
 *
 * This is the ONE place the spool's filament color is rendered; every surface (launcher tile, drawer
 * tile, mid-print shortcut, the SpoolScreen empty-state + detail pane) routes through it (plan 04).
 *
 * ## Why a dedicated composable (not [JiibIconView])
 * [JiibIconView]'s drawable branch is a single FLAT `tint: Color`. The reactive spiral needs a runtime
 * gradient (D-04), which is beyond a flat tint — so the spiral is drawn directly in a Compose [Canvas]
 * via the parsed [SPIRAL_PATH_DATA]. The body still reuses the flat-tint `Icon(painterResource(...))`
 * idiom for the neutral disc.
 *
 * ## The spiral carve-out (THEME-01, D-10)
 * The spiral is the ONE element that renders a LITERAL filament color instead of a role token — the
 * sanctioned "filament color is DATA, not chrome" exception (docs/ui_design/THEMING.md). The true hex is
 * NEVER clamped via `brandTint` (D-05). Legibility comes from a thin neutral [keyline] stroke under the
 * coil so its edge reads against any background even when the fill ≈ surface (white PLA on light, black
 * on dark). The body, the keyline, and all surrounding chrome stay token-routed.
 *
 * ## Empty spool (D-03)
 * An empty [swatches] list draws the body disc ONLY — the spiral is OMITTED (the windows show through to
 * the background). The PRESENCE/ABSENCE of the spiral IS the "filament loaded" signal.
 *
 * ## Static (Adreno-320 floor)
 * The spiral/body draw once per recomposition — no animation (root CLAUDE.md). The parsed path is
 * `remember`ed; the gradient Brush is `remember(render)`ed so it is rebuilt only when the filament color
 * changes, not on every recomposition (D-03/P2 allocation fix, Plan 22-03).
 *
 * @param swatches resolved filament colors. 0 = empty spool (D-03), 1 = solid (D-08), 2+ = two-stop
 *   gradient across the FIRST TWO only (D-04/D-09 index [0..1]). Caller resolves these (Spoolman→gcode→empty).
 * @param bodyTint the neutral role token for the spool body disc (D-02 — body NOT reactive).
 * @param keyline the neutral role token for the coil edge stroke (D-05 legibility framing).
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
    // Parse the source-art spiral once (512-space). PathParser handles the M/Q commands verbatim.
    val spiralPath = remember { PathParser().parsePathString(SPIRAL_PATH_DATA).toPath() }
    val spiralBounds = remember(spiralPath) { spiralPath.getBounds() }

    // Resolve the render variant once per recomposition (pure function — no Compose state).
    val render = spiralRenderFor(swatches)

    // Cache the gradient Brush at composable scope, keyed on the SpiralRender instance (D-03/P2).
    // SpiralRender.Gradient is a data class whose equality is its two Color fields, so the Brush is
    // rebuilt only when the filament color actually changes — not on every 4 Hz recomposition.
    // Mirrors the ColorWheel.kt `rememberHueSweep()` cached-Brush template (Plan 22-03).
    // The result is null for non-Gradient renders (Solid / Empty) — the draw lambda uses SolidColor
    // in those branches and never reads gradientBrush.
    val gradientBrush = remember(render) {
        if (render is SpiralRender.Gradient) {
            Brush.linearGradient(
                0f to render.start,
                1f to render.end,
                // Bind the two-stop transition to the spiral's own bounds (RESEARCH Pitfall 4):
                // a default-span brush would wash the gradient out across the whole box.
                start = Offset(spiralBounds.left, spiralBounds.center.y),
                end = Offset(spiralBounds.right, spiralBounds.center.y),
            )
        } else null
    }

    Box(modifier.size(sizeDp)) {
        // (1) Filament spiral — BEHIND the disc, so it only shows through the body's window holes. Drawn
        // in the 512-space the art was authored in, scaled to the box (geometry tracks any sizeDp).
        Canvas(Modifier.fillMaxSize()) {
            if (render is SpiralRender.Empty) return@Canvas // D-03: filament absent — draw nothing.
            scale(size.width / VIEWPORT, size.height / VIEWPORT, pivot = Offset.Zero) {
                translate(SPIRAL_DX, SPIRAL_DY) {
                    // gradientBrush is non-null iff render is SpiralRender.Gradient (see remember above).
                    val brush = when (render) {
                        is SpiralRender.Solid -> SolidColor(render.color)
                        is SpiralRender.Gradient -> gradientBrush ?: SolidColor(render.start)
                        SpiralRender.Empty -> return@translate
                    }
                    // Keyline first (slightly wider) → reads as a thin neutral outline around the coil so
                    // the filament edge is visible in the windows even when the fill ≈ surface (D-05/SC5).
                    drawPath(
                        spiralPath,
                        color = keyline,
                        style = Stroke(width = COIL_WIDTH + KEYLINE_WIDTH * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    // The reactive filament fill — the literal Spoolman/gcode color (D-04/D-08).
                    drawPath(
                        spiralPath,
                        brush = brush,
                        style = Stroke(width = COIL_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }

        // (2) Spool body disc — ON TOP, flat-tinted neutral (D-02 not reactive). Its three evenOdd window
        // holes + center reveal the spiral beneath. Reuses the existing flat-tint Icon contract.
        Icon(
            painter = painterResource(R.drawable.spool),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            tint = bodyTint,
        )
    }
}

/** Source-art viewBox edge (img/spool.svg is `0 0 512 512`). */
private const val VIEWPORT = 512f

/**
 * The wound-filament spiral centerline from img/spool.svg (the red `stroke` path), in 512-space. Stroked
 * at [COIL_WIDTH] to reproduce the coil thickness. Only M/Q commands — PathParser parses them directly.
 */
private const val SPIRAL_PATH_DATA =
    "M 250.851 259.755 Q 250.851 257.294 253.092 255.874 Q 255.484 254.358 258.612 255.274 " +
        "Q 262.09 256.292 264.294 259.755 Q 266.771 263.646 266.374 268.717 Q 265.93 274.388 262.054 279.159 " +
        "Q 257.761 284.442 250.851 286.641 Q 243.275 289.053 235.167 286.92 Q 226.359 284.603 219.805 277.679 " +
        "Q 212.742 270.216 210.522 259.755 Q 208.145 248.555 212.044 237.35 Q 216.195 225.423 226.205 217.067 " +
        "Q 236.809 208.217 250.851 205.982 Q 265.661 203.625 279.978 209.306 Q 295.021 215.275 305.181 228.388 " +
        "Q 315.822 242.121 318.067 259.755 Q 320.411 278.171 312.942 295.603 Q 305.162 313.763 288.94 325.727 " +
        "Q 272.08 338.161 250.851 340.414 Q 228.832 342.75 208.281 333.488 Q 187.005 323.9 173.237 304.565 " +
        "Q 159.007 284.582 156.749 259.755 Q 154.419 234.133 165.476 210.463 Q 176.87 186.07 199.319 170.499 " +
        "Q 222.424 154.472 250.851 152.21 Q 280.075 149.884 306.864 162.737 Q 334.375 175.937 351.749 201.501 " +
        "Q 369.574 227.728 371.839 259.755 Q 374.161 292.581 359.511 322.49 Q 344.507 353.118 315.826 372.295 " +
        "Q 286.478 391.918 250.851 394.186 Q 214.423 396.506 181.395 380.057 Q 147.648 363.25 126.669 331.452 " +
        "Q 105.247 298.983 102.976 259.755 Q 100.66 219.725 118.907 183.577 Q 137.517 146.712 172.433 123.93 " +
        "Q 208.022 100.709 250.851 98.4373 Q 294.483 96.1225 333.75 116.169 Q 373.734 136.581 398.318 174.615 " +
        "Q 418.988 206.594 424.203 244.589"

/** The near-identity translate baked into the source spiral's `transform` matrix (folded in faithfully). */
private const val SPIRAL_DX = 1.782367f
private const val SPIRAL_DY = 7.239075f

/** Coil stroke width in 512-space (the source `stroke-width: 37px`). */
private const val COIL_WIDTH = 37f

/** Neutral keyline outline half-extent in 512-space (~0.5dp at a 40dp tile). */
private const val KEYLINE_WIDTH = 7f

/**
 * The pure swatch-count → spiral-render decision (D-03/D-04/D-08/D-09), factored out of the Compose
 * `Canvas` so it is host-testable without a Compose harness. See [SpoolGlyphTest].
 */
internal sealed interface SpiralRender {
    /** No swatches → the spiral is omitted entirely (D-03 empty spool). */
    data object Empty : SpiralRender

    /** Exactly one swatch → a flat solid fill (D-08). */
    data class Solid(val color: Color) : SpiralRender

    /** Two or more swatches → a two-stop gradient across the FIRST TWO only (D-04/D-09). */
    data class Gradient(val start: Color, val end: Color) : SpiralRender
}

/**
 * Decide how the spiral renders for a resolved [swatches] list:
 *   - empty     → [SpiralRender.Empty]                 (D-03)
 *   - size == 1 → [SpiralRender.Solid]                 (D-08)
 *   - size >= 2 → [SpiralRender.Gradient] of `[0]`,`[1]` (D-04/D-09 — first two only, ignores the rest)
 */
internal fun spiralRenderFor(swatches: List<Color>): SpiralRender = when (swatches.size) {
    0 -> SpiralRender.Empty
    1 -> SpiralRender.Solid(swatches[0])
    else -> SpiralRender.Gradient(swatches[0], swatches[1])
}
