package works.mees.jiib.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * The toolkit-agnostic, immutable, fully-RESOLVED token set — the single source of truth every
 * later UI surface (Compose AND classic Views, ADR 0001 hybrid) consumes. A "theme" in this app
 * is nothing more than one of these: a seed-generated palette (chrome) plus the contrast-ranked data
 * pool (D-04), at the active dark/light polarity and text-size multiplier — a complete usable theme.
 *
 * Mirrors the [works.mees.jiib.state.PrinterState] discipline — a plain value type with KDoc per
 * field tying each back to its design-law role token (docs/ui_design/THEMING.md). It carries
 * BAKED sRGB [Color]s, never oklch: oklch silently renders WRONG on API < 26 (RESEARCH Pitfall 1),
 * so [BakedTokens] bakes the design law's oklch literals to sRGB once and this type only ever holds
 * the resolved result. The ONE Compose annotation here is [@Immutable] (RESEARCH Pattern 3) so the
 * Compose runtime can skip recomposition — it is a stability hint, not toolkit coupling; classic
 * Views read the same fields via `.toArgb()`.
 *
 * Theme model (D-04): the WHOLE chrome — accent / surfaces / text — derives from a single user seed
 * (the generator's [Palette.generate] output, baked through [TokenBridge]). There is no hand-picked
 * per-role override of the chrome; the seed drives everything. The independently-editable surface is
 * the contrast-ranked DATA [pool] (D-09): each pool slot can be overridden at its index via a sparse
 * `poolOverrides` map applied in the bridge, leaving every other slot seed-derived. The full field
 * set is present so a resolved theme is always complete.
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
    /**
     * `--heat` CAUTION color (D-13): proceed-at-peril / caution, NOT a heater-identity color any
     * more. Maps from the generator's `status.caution` slot. Heater/temperature IDENTITY is now
     * carried by the data [pool] / [directional] temperature, never this token.
     */
    val heat: Color,
    /** `--heat-soft` heat tint (alpha-bearing). */
    val heatSoft: Color,
    /** `--heat-glow` heat glow (alpha-bearing). */
    val heatGlow: Color,
    /**
     * Generated contrast-ranked DATA pool (D-13). Consumers wrap `pool[i % pool.size]` (D-14).
     * Sensor traces, data readouts, and any "Nth distinct data color" need read from this list —
     * never a raw hex (THEME-01). Carried as a plain `List<Color>`: the enclosing [@Immutable]
     * annotation covers the field so Compose treats it as stable (RESEARCH A3 — no
     * `kotlinx-collections-immutable` dependency added).
     */
    val pool: List<Color>,
    /**
     * Directional standards (D-07): the temperature / XY-plane / Z-plane identity colors. ACCENT
     * LEADS — `temperature == accent`, `xy == pool[0]`, `z == pool[1]` (re-derived in [TokenBridge.build];
     * supersedes the Phase-15 D-2/D-13 warmth-tagged derivation). Movement controls wear their plane's
     * color (the jog-pad XY outline, the Z-row outline); temperature surfaces wear [Directional.temperature]
     * (= the theme accent). The values shift but the shape is unchanged, so consumers reading
     * `directional.*` move with the re-derivation without code change.
     */
    val directional: Directional,
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
    /**
     * The active [PaletteMode] (D-05). Carried as a resolved ENUM (never a string) so the N-series
     * helper [seriesColor] is a PURE READ — it branches on this field with no string parsing and no
     * extra inputs. The resolver already knows the mode; threading it onto the token set makes the
     * data-series rule a property of the theme. Defaults to [PaletteMode.Colorful] (D-15 default)
     * so every existing [ThemeTokens] constructor site (baked fallbacks, previews, test builders)
     * stays source-compatible.
     */
    val mode: PaletteMode = PaletteMode.Colorful,
)

/**
 * The three data-coloring modes (D-05/D-15). Drives the [ThemeTokens.seriesColor] N-series sequence:
 *  - [Colorful]:     accent-led, then wraps the data [ThemeTokens.pool] (full-color data).
 *  - [Simple]:       accent / text alternation (near-monochrome data).
 *  - [HighContrast]: accent / stop / caution(=heat) / go / text cycle (stoplight-coded data).
 *
 * An enum (not a string) so the series rule is a pure, exhaustive `when` with no lookup. Maps from
 * the resolver's `MODE_*` string constants — see [PaletteMode.fromFlags]. [Colorful] is the D-15 default.
 */
enum class PaletteMode {
    Colorful,
    Simple,
    HighContrast;

    companion object {
        /**
         * Map the generator's two boolean mode flags (the [Palette.Generated.simple] /
         * [Palette.Generated.highContrast] pair the resolver derives from its `MODE_*` string) onto
         * the resolved enum — the ONE place the string/flag world becomes a [PaletteMode].
         * High-contrast wins if both are set (defensive; the resolver never sets both).
         */
        fun fromFlags(simple: Boolean, highContrast: Boolean): PaletteMode = when {
            highContrast -> HighContrast
            simple -> Simple
            else -> Colorful
        }
    }
}

/**
 * Directional standards (D-07): the temperature / XY-plane / Z-plane identity colors, now ACCENT-LED.
 * The accent-leads reconception SUPERSEDES Phase-15's D-2/D-13 warmth-tagged derivation from the
 * generator's top-three hues. Re-derived in ONE place ([TokenBridge.build]): `temperature = accent`,
 * `xy = pool[0]`, `z = pool[1]` (bed shares xy's pool[0], chamber shares z's pool[1] — they never
 * co-occur on screen). accent survives ALL palette modes (D-05), so `temperature` never collapses to
 * text in Simple/HighContrast. Movement controls wear their plane's color instead of a bare accent;
 * the jog-pad XY outline reads [xy], the Z-row reads [z] (D-08). A small [@Immutable] value type that
 * mirrors the JS `directional` shape (cleaner than three flat fields).
 */
@Immutable
data class Directional(
    /** Temperature / heater identity color — the theme accent (D-07 accent-leads). */
    val temperature: Color,
    /** XY-plane motion color (the jog-pad outline) — `pool[0]` (D-07). */
    val xy: Color,
    /** Z-plane motion color (the Z-row outline) — `pool[1]` (D-07). */
    val z: Color,
)

/**
 * The two theme polarities. Retained as the dark/light vocabulary the generator's `dark` flag maps onto;
 * the per-role override model that once paired with it was retired (D-04, seed-only chrome).
 */
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
