package works.mees.dinghy.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * The toolkit-agnostic, immutable, fully-RESOLVED token set — the single source of truth every
 * later UI surface (Compose AND classic Views, ADR 0001 hybrid) consumes. A "theme" in this app
 * is nothing more than one of these: pick a base (Dark/Light), apply any user [TokenDelta]
 * overrides, set the text-size multiplier, and you have a complete usable theme (THEME-01/D-05).
 *
 * Mirrors the [works.mees.dinghy.state.PrinterState] discipline — a plain value type with KDoc per
 * field tying each back to its design-law role token (docs/ui_design/THEMING.md). It carries
 * BAKED sRGB [Color]s, never oklch: oklch silently renders WRONG on API < 26 (RESEARCH Pitfall 1),
 * so [BakedTokens] bakes the design law's oklch literals to sRGB once and this type only ever holds
 * the resolved result. The ONE Compose annotation here is [@Immutable] (RESEARCH Pattern 3) so the
 * Compose runtime can skip recomposition — it is a stability hint, not toolkit coupling; classic
 * Views read the same fields via `.toArgb()`.
 *
 * Custom-theme scope (D-01): only `accent`/`heat`/`go`/`stop`/`bg` are user-overridable; every other
 * role inherits the chosen base. The full field set is present so a resolved theme is always complete.
 */
@Immutable
data class ThemeTokens(
    /** `--bg` app background. */
    val bg: Color,
    /** `--bg-2` sunken well / inset. */
    val bg2: Color,
    /** `--surface` raised surface (cards, screen body). */
    val surface: Color,
    /** `--surface-2` raised +1 (tracks, wells). */
    val surface2: Color,
    /** `--surface-3` raised +2. */
    val surface3: Color,
    /** `--text` strong text. */
    val text: Color,
    /** `--text-2` muted text. */
    val text2: Color,
    /** `--text-3` faint text. */
    val text3: Color,
    /** `--hair` decorative hairline (alpha-bearing). */
    val hair: Color,
    /** `--outline` interactive control bound (the affordance edge). */
    val outline: Color,
    /** `--outline-2` control, emphasised/hover. */
    val outline2: Color,
    /** `--accent` signature blue — primary/motion (USER-OVERRIDABLE, D-01). */
    val accent: Color,
    /** `--accent-2` accent, brighter (text/icon). */
    val accent2: Color,
    /** `--accent-soft` accent tint fill (alpha-bearing). */
    val accentSoft: Color,
    /** `--accent-line` accent outline (alpha-bearing). */
    val accentLine: Color,
    /** `--accent-glow` accent glow (alpha-bearing). */
    val accentGlow: Color,
    /** `--heat` nozzle/bed amber (USER-OVERRIDABLE, D-01). */
    val heat: Color,
    /** `--heat-soft` heat tint (alpha-bearing). */
    val heatSoft: Color,
    /** `--heat-glow` heat glow (alpha-bearing). */
    val heatGlow: Color,
    /**
     * `--violet` third sensor trace (chamber/generic) — RESEARCH Open Q2; nozzle=heat, bed=accent,
     * chamber=violet per README §9. The multi-trace GraphView (05-04) reads this via the token,
     * never a raw hex (THEME-01). Not user-overridable (inherits the base, D-01).
     */
    val violet: Color,
    /** `--go` success/confirm green (USER-OVERRIDABLE, D-01). */
    val go: Color,
    /** `--go-soft` go tint (alpha-bearing). */
    val goSoft: Color,
    /** `--go-glow` go glow (alpha-bearing). */
    val goGlow: Color,
    /** `--stop` danger/destructive red (USER-OVERRIDABLE, D-01). */
    val stop: Color,
    /** `--stop-soft` stop tint (alpha-bearing). */
    val stopSoft: Color,
    /** `--stop-glow` stop glow (alpha-bearing). */
    val stopGlow: Color,
    /** `--edge-glow` neutral control glow (alpha-bearing). */
    val edgeGlow: Color,
    /** `--r-screen` screen/bezel corner radius (30px). */
    val rScreen: Dp,
    /** `--r-card` card corner radius (22px). */
    val rCard: Dp,
    /** `--r-ctrl` control/button corner radius (16px). */
    val rCtrl: Dp,
    /** `--r-pill` pill/chip corner radius (999px). */
    val rPill: Dp,
    /**
     * `--fs` user text-size multiplier (S≈1.0 / M≈1.15 / L≈1.32; M is the larger default).
     * The SOLE text-size authority (D-04) — type sizes are `baseSp * fs` via [fsSp]; the OS
     * `fontScale` is neutralised at the Compose root so this never double-applies.
     */
    val fs: Float,
)

/** The two built-in theme bases. A user-custom theme is one of these plus a [TokenDelta] (D-02). */
enum class ThemeBase { Dark, Light }

/**
 * The `--fs` text-size step. M is the larger default tuned for reading a phone at arm's length
 * (~3 ft) per THEMING.md. Persisted as the S/M/L choice; [multiplier] is the value that lands in
 * [ThemeTokens.fs].
 */
enum class FontScale(val multiplier: Float) {
    S(1.0f),
    M(1.15f),
    L(1.32f),
}

/**
 * The sole text-size helper (D-04/Pattern 5): a base sp value scaled by the active `--fs`.
 * e.g. `fsSp(16f, 1.32f) == 21.12f`. Returns a plain Float (sp number) so it stays toolkit-agnostic;
 * the Compose boundary appends `.sp`.
 */
fun fsSp(baseSp: Float, fs: Float): Float = baseSp * fs
