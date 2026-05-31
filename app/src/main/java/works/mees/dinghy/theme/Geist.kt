package works.mees.dinghy.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import works.mees.dinghy.R

/**
 * The app's type substrate (THEME-02, design-law `--ui` / `--mono` tokens — docs/ui_design/THEMING.md).
 *
 * STATIC weights only, deliberately (RESEARCH Pattern 2 / Pitfall 2): variable-font axis selection is
 * API 26+, but the install floor is API 23 (Nexus 7 2013 / LineageOS), where a variable `Geist[wght].ttf`
 * silently collapses to a single weight. So each weight is bundled as its own static TTF in `res/font/`
 * and mapped to its [FontWeight] here. `Font(R.font.geist_*, FontWeight.*)` over static files works on
 * API 21+.
 *
 * [GeistMono] carries the live numerics. It is a *monospaced* face, so digit advance widths are uniform
 * by construction — the design law's `font-variant-numeric: tabular-nums` is satisfied inherently, with
 * NO OpenType `tnum` feature and NO `fontFeatureSettings` (which old ART does not honor reliably).
 * Do not add `fontFeatureSettings` here; tabular alignment is a glyph-metric property of the face.
 *
 * Fonts: Geist + Geist Mono, SIL Open Font License 1.1, first-party Vercel (github.com/vercel/geist-font),
 * bundled as passive `res/font` assets carrying no minSdk and no code (threat T-03-02: accept).
 */
val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),    // 400 — body / labels
    Font(R.font.geist_medium, FontWeight.Medium),     // 500 — most UI text
    Font(R.font.geist_semibold, FontWeight.SemiBold), // 600 — hero values, button labels, headings
    Font(R.font.geist_bold, FontWeight.Bold),         // 700 — big axis glyphs / biglabel
)

/**
 * Monospaced face for live numeric data (design-law `--mono`, the `.mono` / `.tv` roles). Tabular by
 * construction — see the note on [Geist] above; never set `fontFeatureSettings("tnum")` on it.
 */
val GeistMono = FontFamily(
    Font(R.font.geist_mono_medium, FontWeight.Medium),     // 500 — live numeric data (.mono)
    Font(R.font.geist_mono_semibold, FontWeight.SemiBold), // 600 — hero mono values (.s .v.mono, .tv)
)
