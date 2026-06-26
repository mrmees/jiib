package works.mees.jiib.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * GENERATED — do not edit by hand. Re-bake with `python tools/oklch-bake/bake_tokens.py --write`.
 *
 * DEMOTED (15-04, D-02): as of the generate-and-cache rewire, these baked tables are NO LONGER the
 * live source of truth — [ThemeResolver] generates the active theme at runtime via [Palette.generate]
 * + [TokenBridge.build]. [TokensDark]/[TokensLight] are now the validated default-seed FAIL-SAFE
 * snapshot used only when generation ever throws (it should not — the math is total — but the printer
 * surface must never go dark, T-15-04-01). The literal-sRGB header law below still governs the
 * generator's output policy (every emitted value is a baked sRGB Color, never an Oklab-space Color).
 *
 * The checked-in oklch->sRGB token table (D-03 / THEME-01). The design law
 * (docs/ui_design/THEMING.md + reference/hifi.css) authors every role token in CSS Color 4
 * `oklch()`. On Android API < 26 a non-sRGB (Oklab) Compose Color SILENTLY renders as sRGB at
 * the platform layer (RESEARCH Pitfall 1) — i.e. WRONG on the Nexus 7 2013 perf floor. So the
 * oklch values are baked to exact sRGB `Color(0x..)` literals ONCE, here, by bake_tokens.py.
 *
 * This file is the ONLY sanctioned home for literal sRGB Color(0x..) values in the codebase.
 *
 * GAMUT MAPPING POLICY: "clamp chroma" (CSS Color 4 gamut mapping) — out-of-sRGB-gamut high-
 * chroma oklch values are desaturated (chroma reduced at fixed L,h via binary search) until
 * in-gamut, preserving hue+lightness. Tokens that required mapping: accent2, heat, heatSoft, heatGlow, go, goSoft, goGlow.
 *
 * EXTERNALLY-VERIFIED cross-check (compare against any oklch->hex converter, e.g. oklch.com):
 *   --accent  dark  oklch(0.66 0.15 255) -> #4C94EC
 *   --accent  light oklch(0.55 0.18 256) -> #106ED7
 *   --heat    dark  oklch(0.79 0.13 66) -> #F3A958
 *   --heat    light oklch(0.62 0.16 52) -> #CE6400
 *   --go      dark  oklch(0.74 0.15 150) -> #5AC576
 *   --go      light oklch(0.56 0.16 150) -> #008C3F
 *   --stop    dark  oklch(0.66 0.2 25) -> #F4514F
 *   --stop    light oklch(0.55 0.21 25) -> #D01C29
 */

/** Resolved DARK base — the `:root` defaults of hifi.css, baked to sRGB. */
val TokensDark: ThemeTokens = ThemeTokens(
    bg = Color(0xFF0C1015),  // app background
    bg2 = Color(0xFF12161C),  // sunken well / inset (--bg-2)
    surface = Color(0xFF171C23),  // raised surface (cards, screen)
    surface2 = Color(0xFF20262E),  // raised +1 (tracks, wells)
    surface3 = Color(0xFF2A3139),  // raised +2
    text = Color(0xFFF0F2F4),  // text strong
    text2 = Color(0xFF9FA5AD),  // text muted (--text-2)
    text3 = Color(0xFF6E757E),  // text faint (--text-3)
    hair = Color(0x14FFFFFF),  // decorative hairline
    outline = Color(0xFF4B535E),  // interactive control bound
    outline2 = Color(0xFF667383),  // control, emphasised/hover
    accent = Color(0xFF4C94EC),  // signature blue — primary/motion
    accent2 = Color(0xFF70ADFB),  // accent, brighter (text/icon)
    accentSoft = Color(0x294C94EC),  // accent tint (fills)
    accentLine = Color(0x8C4C94EC),  // accent outline
    accentGlow = Color(0x594C94EC),  // accent glow
    heat = Color(0xFFF3A958),  // nozzle/bed (amber)
    heatSoft = Color(0x29F3A958),  // heat tint
    heatGlow = Color(0x61F3A958),  // heat glow
    // Data pool (D-13) — fail-safe default set. The runtime path produces this from the seed via
    // TokenBridge; this baked snapshot keeps the legacy nozzle/bed/chamber trace identities so the
    // default theme renders before the generator runs. pool[2] is the old --violet chamber color
    // (the violet shim was deleted in 15-07; the third trace now reads pool[2 % size] directly).
    pool = listOf(
        Color(0xFFF3A958),  // [0] temperature (was --heat amber)
        Color(0xFF4C94EC),  // [1] xy (was --accent blue)
        Color(0xFFAE84F2),  // [2] z / chamber trace (was --violet)
    ),
    directional = Directional(
        temperature = Color(0xFFF3A958),
        xy = Color(0xFF4C94EC),
        z = Color(0xFFAE84F2),
    ),
    go = Color(0xFF5AC576),  // success/confirm (green)
    goSoft = Color(0x295AC576),  // go tint
    goGlow = Color(0x665AC576),  // go glow
    stop = Color(0xFFF4514F),  // danger/destructive (red)
    stopSoft = Color(0x26F4514F),  // stop tint
    stopGlow = Color(0x6BF4514F),  // stop glow
    edgeGlow = Color(0x3D6F8EB6),  // neutral control glow
    rScreen = 30.dp, rCard = 22.dp, rCtrl = 16.dp, rPill = 999.dp,
    fs = 1.15f,  // M — the larger default (THEMING.md --fs)
)

/** Resolved LIGHT base — the `.screen.light` overrides of hifi.css, baked to sRGB. */
val TokensLight: ThemeTokens = ThemeTokens(
    bg = Color(0xFFF2F4F6),  // app background
    bg2 = Color(0xFFE5E8EC),  // sunken well / inset (--bg-2)
    surface = Color(0xFFFDFDFE),  // raised surface (cards, screen)
    surface2 = Color(0xFFE5E8EC),  // raised +1 (tracks, wells)
    surface3 = Color(0xFFD4D8DD),  // raised +2
    text = Color(0xFF212730),  // text strong
    text2 = Color(0xFF525864),  // text muted (--text-2)
    text3 = Color(0xFF81868F),  // text faint (--text-3)
    hair = Color(0x1C141E37),  // decorative hairline
    outline = Color(0xFFA4ABB8),  // interactive control bound
    outline2 = Color(0xFF778193),  // control, emphasised/hover
    accent = Color(0xFF106ED7),  // signature blue — primary/motion
    accent2 = Color(0xFF0060C1),  // accent, brighter (text/icon)
    accentSoft = Color(0x1F106ED7),  // accent tint (fills)
    accentLine = Color(0x80106ED7),  // accent outline
    accentGlow = Color(0x33106ED7),  // accent glow
    heat = Color(0xFFCE6400),  // nozzle/bed (amber)
    heatSoft = Color(0x24CE6400),  // heat tint
    heatGlow = Color(0x38CE6400),  // heat glow
    // Data pool (D-13) — light fail-safe default set (legacy nozzle/bed/chamber trace identities).
    pool = listOf(
        Color(0xFFCE6400),  // [0] temperature (was --heat amber)
        Color(0xFF106ED7),  // [1] xy (was --accent blue)
        Color(0xFF7B47BF),  // [2] z / chamber trace (was --violet)
    ),
    directional = Directional(
        temperature = Color(0xFFCE6400),
        xy = Color(0xFF106ED7),
        z = Color(0xFF7B47BF),
    ),
    go = Color(0xFF008C3F),  // success/confirm (green)
    goSoft = Color(0x24008C3F),  // go tint
    goGlow = Color(0x38008C3F),  // go glow
    stop = Color(0xFFD01C29),  // danger/destructive (red)
    stopSoft = Color(0x1FD01C29),  // stop tint
    stopGlow = Color(0x38D01C29),  // stop glow
    edgeGlow = Color(0x1A4973AB),  // neutral control glow
    rScreen = 30.dp, rCard = 22.dp, rCtrl = 16.dp, rPill = 999.dp,
    fs = 1.15f,  // M — the larger default (THEMING.md --fs)
)
