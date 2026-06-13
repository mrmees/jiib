package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import works.mees.dinghy.designsystem.layout.FocusInset
import works.mees.dinghy.designsystem.layout.ListFrameInset
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens

/**
 * The Focus edge — the bounded-surface border whose COLOR/FORM encodes meaning (Focus Frame law §2,
 * `.planning/notes/2026-06-12-focus-frame-law-design.md`). The edge is the constant "this is the
 * Focus" signal; what it looks like tells you why. Accent is RESERVED for [Progress].
 */
sealed interface FocusEdge {
    /** Default resting edge: neutral [ThemeTokens.outline] at list-row stroke weight. */
    data object Neutral : FocusEdge

    /**
     * Edge tinted by the item's literal data color (THEME-01 carve-out — e.g. a spool's filament
     * color). Pass the parsed color directly; it is NOT brandTint-clamped.
     */
    data class Data(val color: Color) : FocusEdge

    /**
     * Print-progress: the edge becomes a perimeter progress bar (the Scrubber's visual language
     * unwrapped around the frame). Drawn specially in [FocusFrame] — NOT a uniform border.
     * Wired in Stage 3 of the implementation plan; until then it renders borderless.
     */
    data class Progress(val fraction: Float) : FocusEdge
}

/** A resolved uniform-border stroke. */
data class EdgeStroke(val color: Color, val widthDp: Float)

/**
 * Pure (host-testable) mapping from a [FocusEdge] to its uniform border stroke, or `null` when the
 * edge is drawn specially ([FocusEdge.Progress] — a perimeter bar, not a border).
 *  - [FocusEdge.Neutral] → [outline] at 1.5dp (the list-row outline weight).
 *  - [FocusEdge.Data]    → the literal data color at 3dp (heavier so the color reads as a signal).
 *  - [FocusEdge.Progress]→ `null` (perimeter bar drawn by [FocusFrame]).
 */
fun focusEdgeStroke(edge: FocusEdge, outline: Color): EdgeStroke? = when (edge) {
    FocusEdge.Neutral -> EdgeStroke(outline, 1.5f)
    is FocusEdge.Data -> EdgeStroke(edge.color, 3f)
    is FocusEdge.Progress -> null
}

/**
 * The universal Focus container (Focus Frame law). Renamed from `DetailCard` — the spoolman/calibrate
 * bounded-card look, now the shell EVERY Focus uses except Webcam.
 *
 * ## Structure
 *  - Outer frame: self-owns the **horizontal** region-edge inset ([ListFrameInset], 8dp) so the
 *    Focus aligns with the Field's horizontal frame. Callers pass vertical (top/bottom) + sizing
 *    (`fillMaxSize`/`weight`) — never start/end/horizontal (mirrors [works.mees.dinghy.designsystem.layout.ListBlock]).
 *  - Fill: [ThemeTokens.surface] — visually distinct from the translucent list/Field area.
 *  - Edge: [FocusEdge] — [FocusEdge.Neutral] by default ([ThemeTokens.outline]); [FocusEdge.Data]
 *    tints it with item data; [FocusEdge.Progress] (Stage 3) draws a perimeter bar.
 *  - Content clip: content is clipped to the rounded bounds — it never overflows the frame.
 *  - Inner inset: [FocusInset] (16dp).
 *
 * ## THEME-01 data carve-out — [FocusEdge.Data]
 * Carries the item's actual physical color hex (e.g. Spoolman colorSwatches). It is item DATA, not a
 * chrome token, and must NOT be brandTint-clamped. All other colors are role tokens.
 *
 * @param modifier caller-supplied modifier (sizing + vertical padding).
 * @param edge     the Focus edge mode; defaults to [FocusEdge.Neutral].
 * @param content  column content rendered inside the framed, clipped, padded surface.
 */
@Composable
fun FocusFrame(
    modifier: Modifier = Modifier,
    edge: FocusEdge = FocusEdge.Neutral,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val stroke = focusEdgeStroke(edge, outline = t.outline)
    Column(
        modifier = modifier
            .padding(horizontal = ListFrameInset) // outer region-edge frame (horizontal; matches the Field)
            .clip(shape) // clip content to bounds — no overflow past the frame
            .then(
                if (stroke != null) {
                    Modifier.border(BorderStroke(stroke.widthDp.dp, stroke.color), shape)
                } else {
                    Modifier // FocusEdge.Progress draws its own perimeter bar (Stage 3)
                },
            )
            .background(t.surface)
            .padding(FocusInset), // inner content inset
        content = content,
    )
}

/**
 * A [Modifier] extension that applies the card surface treatment to any composable: clip to
 * [ThemeTokens.rCard] radius, [ThemeTokens.surface] background, and a 1dp [ThemeTokens.hair]
 * decorative hairline border.
 *
 * The lightweight variant for call sites that need the card look without the [FocusFrame] Column
 * wrapper — embedding an image or custom layout in a card-styled container. This is the embryonic
 * shared "bounded surface" primitive (fill + edge + radius) the whole system could one day unify on.
 *
 * **THEME-01 compliance:** all colors are role tokens; no raw `Color(0x…)`.
 */
fun Modifier.cardSurface(t: ThemeTokens): Modifier =
    this
        .clip(RoundedCornerShape(t.rCard))
        .background(t.surface)
        .border(BorderStroke(1.dp, t.hair), RoundedCornerShape(t.rCard))
