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
    /** Nearest available static TTF for [weight]: exact, else heaviest ≤ weight, else the lightest present. */
    @FontRes
    fun fontRes(weight: FontWeight): Int {
        weights[weight]?.let { return it }
        val sorted = weights.entries.sortedBy { it.key.weight }
        val leq = sorted.lastOrNull { it.key.weight <= weight.weight }
        return (leq ?: sorted.first()).value
    }
}
