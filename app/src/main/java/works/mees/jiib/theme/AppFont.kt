package works.mees.jiib.theme

import androidx.annotation.FontRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Whether a font is a proportional UI face or a monospaced data face. */
enum class FontKind { Ui, Data }

/**
 * One selectable font. A STABLE value type: [FontCatalog] holds singletons, so [ThemeTokens]
 * structural equality (the GraphView/WebcamView repaint guard) stays correct and cheap. NO lambdas
 * here (Codex review 2026-06-29) — [weights] is plain data so `==` works.
 *
 * [family] feeds the Compose seam (JiibTextStyle); [weights] (weight → `R.font.*`) feeds the Views
 * seam (TextRoleViews). Only the static weights a family actually ships are listed; absent weights
 * fall back via [fontRes].
 */
@Immutable
data class AppFont(
    val id: String,
    val displayName: String,
    val kind: FontKind,
    val family: FontFamily,
    val weights: Map<FontWeight, Int>,
) {
    /**
     * Nearest available static TTF for [weight], following the CSS / Compose `FontMatcher` weight-
     * matching rule so this Views seam picks the SAME static file Compose's `FontFamily` matcher
     * would for a missing weight (otherwise a font lacking, say, SemiBold renders one weight in
     * Compose and another in the classic Views). The rule: exact wins; for a missing weight > 500
     * prefer the nearest HEAVIER face (then lighter); for 400–500 prefer up toward 500 (then down,
     * then up); below 400 prefer down (then up).
     */
    @FontRes
    fun fontRes(weight: FontWeight): Int {
        weights[weight]?.let { return it }
        val w = weight.weight
        val asc = weights.entries.sortedBy { it.key.weight }
        val below = asc.filter { it.key.weight < w }   // ascending → nearest-below is last()
        val above = asc.filter { it.key.weight > w }   // ascending → nearest-above is first()
        val pick = when {
            w < 400 -> below.lastOrNull() ?: above.firstOrNull()
            w <= 500 -> above.firstOrNull { it.key.weight <= 500 } ?: below.lastOrNull() ?: above.firstOrNull()
            else -> above.firstOrNull() ?: below.lastOrNull()
        }
        return (pick ?: asc.first()).value
    }
}
