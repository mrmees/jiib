package works.mees.jiib.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The WCAG large-graphic non-text contrast minimum (3.0:1). A brand mark is a large graphical element,
 * so the [W3C 1.4.11 Non-text Contrast](https://www.w3.org/WAI/WCAG21/Understanding/non-text-contrast.html)
 * 3:1 floor is the right bar (NOT the 4.5:1 small-text bar). This is the D-06 watch-item threshold:
 * below it, the accent-tinted mark would wash out against the background and we fall back to text.
 */
const val BRAND_CONTRAST_FLOOR: Float = 3.0f

/**
 * Resolve the brand-mark tint, guarding the D-06 accent-vs-bg contrast watch-item.
 *
 * The in-app jiib marks (Splash lockup, About wordmark) are tinted from the theme [accent] to keep
 * the brand inside the token system (THEME-01 — never a literal). But on a user-CUSTOM theme the
 * accent can land too close to [bg] and the mark would disappear. This pure function computes the
 * WCAG-style relative-luminance contrast ratio between [accent] and [bg] —
 * `ratio = (Lmax + 0.05) / (Lmin + 0.05)` where `L` is [Color.luminance] (the same Compose extension
 * used in ThemeEditorScreen) — and:
 *  - returns [accent] when the ratio clears [BRAND_CONTRAST_FLOOR] (the mark reads against the bg), or
 *  - returns [text] otherwise (the always-legible token fallback, so the brand never vanishes).
 *
 * Pure function of the three [Color] args — no Compose runtime, no token dependency — so it is
 * host-unit testable and reusable by both Splash and About.
 *
 * @param accent the preferred brand tint (`t.accent`).
 * @param bg the background the mark renders over (`t.bg`).
 * @param text the legible fallback when the accent washes out (`t.text`).
 */
fun brandTint(accent: Color, bg: Color, text: Color): Color {
    val la = accent.luminance()
    val lb = bg.luminance()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    val ratio = (hi + 0.05f) / (lo + 0.05f)
    return if (ratio >= BRAND_CONTRAST_FLOOR) accent else text
}
