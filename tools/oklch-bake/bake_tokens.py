#!/usr/bin/env python3
"""
jiib — one-time oklch -> sRGB token bake (D-03, Phase 3 / THEME-01).

WHY THIS EXISTS
---------------
The hi-fi design law (docs/ui_design/THEMING.md + reference/hifi.css) expresses every role
token in CSS Color 4 `oklch()`. On Android API < 26, a Compose `Color` built in any non-sRGB
color space (Oklab) SILENTLY falls back to sRGB at the platform render layer — so an oklch
value handed to the renderer on the Nexus 7 2013 (Adreno 320 / our perf floor) renders the
*wrong* color with no error (RESEARCH Pitfall 1). D-03 dodges this by baking oklch -> sRGB
exactly ONCE, here, and committing the resulting sRGB `Color(0x..)` literals as BakedTokens.kt.

This script is the *traceable* source of that table: re-runnable, diffable against hifi.css.
It is committed alongside its generated output (BakedTokens.kt) so a reviewer can prove the
literals are correct, not eyeballed.

USAGE (WSL python 3.13 — no third-party deps; pure stdlib math):
    python tools/oklch-bake/bake_tokens.py            # prints BakedTokens.kt to stdout
    python tools/oklch-bake/bake_tokens.py --write     # writes app/.../theme/BakedTokens.kt

PIPELINE (CSS Color 4 §15 / §11.2, Oklab paper):
    oklch -> Oklab        L = L ; a = C*cos(h) ; b = C*sin(h)
    Oklab -> LMS'         apply inverse-M2 ; cube each component (l = l'^3)
    LMS  -> linear sRGB   apply inverse-M1
    linear -> sRGB gamma  v<=0.0031308 ? 12.92v : 1.055 v^(1/2.4) - 0.055 ; clamp [0,1] ; *255

GAMUT MAPPING POLICY (RESEARCH Open Question 2, RESOLVED = "clamp chroma"):
    The saturated tokens (--accent blue, --stop red) can fall outside the sRGB gamut. We use the
    CSS Color 4 gamut-mapping approach: REDUCE CHROMA (binary search toward C=0 at fixed L,h)
    until the color is in-gamut, rather than naively clipping RGB (which shifts hue). This
    preserves hue + lightness, only desaturating the minimum necessary.
"""

import math
import sys

# --- oklch -> Oklab -------------------------------------------------------------------------
def oklch_to_oklab(L, C, h_deg):
    h = math.radians(h_deg)
    return (L, C * math.cos(h), C * math.sin(h))


# --- Oklab -> linear sRGB (Björn Ottosson's reference matrices) ------------------------------
def oklab_to_linear_srgb(L, a, b):
    # Oklab -> LMS' (inverse M2), then cube to LMS
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l = l_ ** 3
    m = m_ ** 3
    s = s_ ** 3
    # LMS -> linear sRGB (inverse M1)
    r = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    return (r, g, bl)


def in_gamut(rgb_lin, eps=1e-4):
    return all(-eps <= c <= 1 + eps for c in rgb_lin)


def gamut_map_clamp_chroma(L, C, h_deg):
    """CSS Color 4 gamut mapping: keep L,h, reduce C (binary search) until in-gamut sRGB."""
    lab = oklab_to_linear_srgb(*oklch_to_oklab(L, C, h_deg))
    if in_gamut(lab):
        return L, C, h_deg, False  # already in gamut, no mapping needed
    lo, hi = 0.0, C
    for _ in range(40):  # ~1e-12 resolution; far past 8-bit precision
        mid = (lo + hi) / 2
        lab = oklab_to_linear_srgb(*oklch_to_oklab(L, mid, h_deg))
        if in_gamut(lab):
            lo = mid
        else:
            hi = mid
    return L, lo, h_deg, True  # mapped (chroma reduced)


def linear_to_srgb_8bit(v):
    v = max(0.0, min(1.0, v))
    s = 12.92 * v if v <= 0.0031308 else 1.055 * (v ** (1 / 2.4)) - 0.055
    return max(0, min(255, round(s * 255)))


def oklch_to_rgb_hex(L, C, h_deg):
    """Returns ((r,g,b) ints, gamut_mapped: bool)."""
    _, Cm, _, mapped = gamut_map_clamp_chroma(L, C, h_deg)
    r, g, b = oklab_to_linear_srgb(*oklch_to_oklab(L, Cm, h_deg))
    return (linear_to_srgb_8bit(r), linear_to_srgb_8bit(g), linear_to_srgb_8bit(b)), mapped


def rgb_to_argb_literal(r, g, b, alpha=None):
    """Color(0xAARRGGBB). alpha None -> opaque FF."""
    a = 255 if alpha is None else max(0, min(255, round(alpha * 255)))
    return f"Color(0x{a:02X}{r:02X}{g:02X}{b:02X})"


# --- TOKEN TABLE (read verbatim from docs/ui_design/THEMING.md + reference/hifi.css) ---------
# Each entry: (kotlin_field, oklch_or_rgba_dark, oklch_or_rgba_light)
# oklch tuple = (L, C, h, alpha|None). rgba tuple = ("rgba", r, g, b, alpha) for the hairlines.
DARK = "dark"
LIGHT = "light"

# (field, kdoc, dark_spec, light_spec)
TOKENS = [
    ("bg",         "app background",                 (0.17, 0.012, 255, None),  (0.966, 0.004, 255, None)),
    ("bg2",        "sunken well / inset (--bg-2)",   (0.20, 0.014, 255, None),  (0.93, 0.006, 255, None)),
    ("surface",    "raised surface (cards, screen)", (0.225, 0.015, 255, None), (0.995, 0.001, 255, None)),
    ("surface2",   "raised +1 (tracks, wells)",      (0.265, 0.017, 255, None), (0.93, 0.006, 255, None)),
    ("surface3",   "raised +2",                      (0.31, 0.018, 255, None),  (0.88, 0.008, 255, None)),
    ("text",       "text strong",                    (0.96, 0.004, 255, None),  (0.27, 0.02, 262, None)),
    ("text2",      "text muted (--text-2)",          (0.72, 0.014, 255, None),  (0.46, 0.02, 262, None)),
    ("text3",      "text faint (--text-3)",          (0.56, 0.016, 255, None),  (0.62, 0.015, 262, None)),
    ("hair",       "decorative hairline",            ("rgba", 255, 255, 255, 0.08), ("rgba", 20, 30, 55, 0.11)),
    ("outline",    "interactive control bound",      (0.44, 0.02, 255, None),   (0.74, 0.02, 262, None)),
    ("outline2",   "control, emphasised/hover",      (0.55, 0.03, 255, None),   (0.6, 0.03, 262, None)),
    ("accent",     "signature blue — primary/motion",(0.66, 0.15, 255, None),   (0.55, 0.18, 256, None)),
    ("accent2",    "accent, brighter (text/icon)",   (0.74, 0.13, 255, None),   (0.5, 0.2, 256, None)),
    ("accentSoft", "accent tint (fills)",            (0.66, 0.15, 255, 0.16),   (0.55, 0.18, 256, 0.12)),
    ("accentLine", "accent outline",                 (0.66, 0.15, 255, 0.55),   (0.55, 0.18, 256, 0.5)),
    ("accentGlow", "accent glow",                    (0.66, 0.15, 255, 0.35),   (0.55, 0.18, 256, 0.2)),
    ("heat",       "nozzle/bed (amber)",             (0.79, 0.13, 66, None),    (0.62, 0.16, 52, None)),
    ("heatSoft",   "heat tint",                      (0.79, 0.13, 66, 0.16),    (0.62, 0.16, 52, 0.14)),
    ("heatGlow",   "heat glow",                      (0.79, 0.13, 66, 0.38),    (0.62, 0.16, 52, 0.22)),
    ("violet",     "third sensor trace (chamber)",   (0.70, 0.16, 300, None),   (0.52, 0.18, 300, None)),
    ("go",         "success/confirm (green)",        (0.74, 0.15, 150, None),   (0.56, 0.16, 150, None)),
    ("goSoft",     "go tint",                        (0.74, 0.15, 150, 0.16),   (0.56, 0.16, 150, 0.14)),
    ("goGlow",     "go glow",                        (0.74, 0.15, 150, 0.4),    (0.56, 0.16, 150, 0.22)),
    ("stop",       "danger/destructive (red)",       (0.66, 0.2, 25, None),     (0.55, 0.21, 25, None)),
    ("stopSoft",   "stop tint",                      (0.66, 0.2, 25, 0.15),     (0.55, 0.21, 25, 0.12)),
    ("stopGlow",   "stop glow",                      (0.66, 0.2, 25, 0.42),     (0.55, 0.21, 25, 0.22)),
    ("edgeGlow",   "neutral control glow",           (0.64, 0.07, 255, 0.24),   (0.55, 0.1, 256, 0.1)),
]


def bake_spec(spec):
    """spec -> (literal, gamut_mapped, hexstr_for_comment)."""
    if spec[0] == "rgba":
        _, r, g, b, a = spec
        return rgb_to_argb_literal(r, g, b, a), False, f"#{r:02X}{g:02X}{b:02X}@{a}"
    L, C, h, a = spec
    (r, g, b), mapped = oklch_to_rgb_hex(L, C, h)
    return rgb_to_argb_literal(r, g, b, a), mapped, f"#{r:02X}{g:02X}{b:02X}"


def field_block(field, kdoc, dark_lit, light_lit):
    return field, dark_lit, light_lit, kdoc


def main():
    write = "--write" in sys.argv

    baked = []  # (field, dark_lit, light_lit, kdoc, dark_mapped, light_mapped, dark_hex, light_hex)
    for field, kdoc, dspec, lspec in TOKENS:
        d_lit, d_mapped, d_hex = bake_spec(dspec)
        l_lit, l_mapped, l_hex = bake_spec(lspec)
        baked.append((field, d_lit, l_lit, kdoc, d_mapped, l_mapped, d_hex, l_hex))

    mapped_tokens = [b[0] for b in baked if b[4] or b[5]]

    # Externally-verifiable cross-check set: the 3 most saturated tokens (per Pattern 4),
    # report their baked hex so the comment can be checked against an external oklch->hex tool.
    crosscheck = {b[0]: (b[6], b[7]) for b in baked if b[0] in ("accent", "stop", "heat", "go")}

    lines = []
    w = lines.append
    w("package works.mees.jiib.theme")
    w("")
    w("import androidx.compose.ui.graphics.Color")
    w("import androidx.compose.ui.unit.dp")
    w("")
    w("/**")
    w(" * GENERATED — do not edit by hand. Re-bake with `python tools/oklch-bake/bake_tokens.py --write`.")
    w(" *")
    w(" * The checked-in oklch->sRGB token table (D-03 / THEME-01). The design law")
    w(" * (docs/ui_design/THEMING.md + reference/hifi.css) authors every role token in CSS Color 4")
    w(" * `oklch()`. On Android API < 26 a non-sRGB (Oklab) Compose Color SILENTLY renders as sRGB at")
    w(" * the platform layer (RESEARCH Pitfall 1) — i.e. WRONG on the Nexus 7 2013 perf floor. So the")
    w(" * oklch values are baked to exact sRGB `Color(0x..)` literals ONCE, here, by bake_tokens.py.")
    w(" *")
    w(" * This file is the ONLY sanctioned home for literal sRGB Color(0x..) values in the codebase.")
    w(" *")
    w(" * GAMUT MAPPING POLICY: \"clamp chroma\" (CSS Color 4 gamut mapping) — out-of-sRGB-gamut high-")
    w(" * chroma oklch values are desaturated (chroma reduced at fixed L,h via binary search) until")
    if mapped_tokens:
        w(f" * in-gamut, preserving hue+lightness. Tokens that required mapping: {', '.join(mapped_tokens)}.")
    else:
        w(" * in-gamut, preserving hue+lightness. (No token required mapping at the authored chroma.)")
    w(" *")
    w(" * EXTERNALLY-VERIFIED cross-check (compare against any oklch->hex converter, e.g. oklch.com):")
    for name, (dhex, lhex) in crosscheck.items():
        # find the source oklch for the comment
        src = next(t for t in TOKENS if t[0] == name)
        dspec, lspec = src[2], src[3]
        w(f" *   --{name:<7} dark  oklch({dspec[0]} {dspec[1]} {dspec[2]}) -> {dhex}")
        w(f" *   --{name:<7} light oklch({lspec[0]} {lspec[1]} {lspec[2]}) -> {lhex}")
    w(" */")
    w("")

    # TokensDark
    w("/** Resolved DARK base — the `:root` defaults of hifi.css, baked to sRGB. */")
    w("val TokensDark: ThemeTokens = ThemeTokens(")
    for field, d_lit, l_lit, kdoc, *_ in baked:
        w(f"    {field} = {d_lit},  // {kdoc}")
    w("    rScreen = 30.dp, rCard = 22.dp, rCtrl = 16.dp, rPill = 999.dp,")
    w("    fs = 1.15f,  // M — the larger default (THEMING.md --fs)")
    w(")")
    w("")
    # TokensLight
    w("/** Resolved LIGHT base — the `.screen.light` overrides of hifi.css, baked to sRGB. */")
    w("val TokensLight: ThemeTokens = ThemeTokens(")
    for field, d_lit, l_lit, kdoc, *_ in baked:
        w(f"    {field} = {l_lit},  // {kdoc}")
    w("    rScreen = 30.dp, rCard = 22.dp, rCtrl = 16.dp, rPill = 999.dp,")
    w("    fs = 1.15f,  // M — the larger default (THEMING.md --fs)")
    w(")")
    w("")

    out = "\n".join(lines)

    if write:
        import os
        dest = os.path.join(
            os.path.dirname(__file__), "..", "..",
            "app", "src", "main", "java", "works", "mees", "jiib", "theme", "BakedTokens.kt",
        )
        dest = os.path.normpath(dest)
        with open(dest, "w", newline="\n") as f:
            f.write(out)
        sys.stderr.write(f"Wrote {dest}\n")
    else:
        sys.stdout.write(out)


if __name__ == "__main__":
    main()
