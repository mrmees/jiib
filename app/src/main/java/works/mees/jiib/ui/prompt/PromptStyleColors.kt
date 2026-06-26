package works.mees.jiib.ui.prompt

import androidx.compose.ui.graphics.Color
import works.mees.jiib.prompt.PromptStyle
import works.mees.jiib.theme.ThemeTokens

/**
 * The 6-protocol-style → Dinghy role-token resolver (UI-SPEC "Content & Footer button style → color"
 * table; 12-04 Task 1). The Macro Prompt Protocol carries SIX semantic button styles
 * (`primary`/`secondary`/`info`/`warning`/`error`/`success`); Dinghy's `designsystem/control/Intent`
 * is the FIVE-value safety vocabulary. `info` is the one style with no matching Intent member, so this
 * is a SEPARATE map (Intent stays 5 — do NOT add a 6th member) and `info` resolves to the brighter
 * accent text tint `accent2` so it reads as distinct-but-related to `primary` (UI-SPEC §Color).
 *
 * Every color comes from [ThemeTokens] (THEME-01 — no raw color literal here; the ONLY sanctioned raw
 * `Color(...)` in the prompt UI is the author-hex carve-out inside [PromptMarkupText]). The mapping:
 *
 *  | style       | token        |
 *  |-------------|--------------|
 *  | primary     | `accentLine` |
 *  | secondary   | `outline`    | (the protocol default — also the unknown/unnormalized fallback)
 *  | info        | `accent2`    |
 *  | warning     | `heat`       |
 *  | error       | `stop`       |
 *  | success     | `go`         |
 */

/** Resolve a [PromptStyle] enum (the normalized 12-01 form) to its outline/label token color. */
fun promptStyleColor(style: PromptStyle, t: ThemeTokens): Color = when (style) {
    PromptStyle.PRIMARY -> t.accentLine
    PromptStyle.SECONDARY -> t.outline
    PromptStyle.INFO -> t.accent2
    PromptStyle.WARNING -> t.heat
    PromptStyle.ERROR -> t.stop
    PromptStyle.SUCCESS -> t.go
}

/**
 * Resolve a raw style string to its token color, case-insensitively. Anything that does not match one
 * of the six protocol styles falls back to `outline` (the `secondary` default) — the same tolerance the
 * 12-01 `normalizeStyle` applies. Host-testable (pure (String, ThemeTokens) → Color).
 */
fun promptStyleColor(style: String, t: ThemeTokens): Color =
    promptStyleColor(PromptStyle.fromToken(style) ?: PromptStyle.SECONDARY, t)
