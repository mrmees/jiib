package works.mees.jiib.theme.compose

import androidx.compose.runtime.staticCompositionLocalOf
import works.mees.jiib.theme.ThemeTokens

/**
 * The Compose token boundary (THEME-01/D-05): every Compose surface reads its colors, shapes, and
 * the `--fs` multiplier from `LocalTokens.current` — NEVER a raw [androidx.compose.ui.graphics.Color]
 * literal. A "theme" is just a different [ThemeTokens] flowing through this local, so a theme swap is
 * a token remap with zero component edits (the success-criterion-#1 "never raw color" contract).
 *
 * `staticCompositionLocalOf` (NOT `compositionLocalOf`) is deliberate (RESEARCH Pitfall 3): a theme
 * change touches nearly every node, so recomposing the whole subtree on a swap is the CORRECT and
 * cheaper behavior — `static` reads are untracked (no per-read snapshot bookkeeping), which is what
 * we want when the value changes rarely (a theme/`--fs` change) but is read pervasively.
 *
 * The default throws: a component reading tokens outside a [JiibTheme] is a programming error,
 * surfaced loudly rather than silently rendering with a wrong fallback palette.
 */
val LocalTokens = staticCompositionLocalOf<ThemeTokens> {
    error("No ThemeTokens provided — wrap your content in JiibTheme { … }")
}
