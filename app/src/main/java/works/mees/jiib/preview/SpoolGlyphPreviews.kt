package works.mees.jiib.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import works.mees.jiib.designsystem.icons.SpoolGlyph
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.fsSp

/**
 * The Wave-0 validation `@Preview` matrix for [SpoolGlyph] (18.3-02 — SC2/SC4/SC5). This is the Studio
 * gate authors eyeball BEFORE the on-device flox UAT (plan 04): it proves the band's three render states
 * (empty / single / two-color), the neutral keyline's legibility against extreme fills, and the
 * token-routed body across all six theme combos at `fs = L`, with NO live Moonraker (literal `Color`
 * lists in, no `SpoolHolder`/`SpoolmanClient`/socket).
 *
 * ## What the matrix exercises
 *  - **Band states** (the interesting axis, via [SpoolBandStateProvider]): `emptyList()` → no band (D-03),
 *    one swatch → flat solid (D-08), two swatches → two-stop gradient (D-04).
 *  - **Themes** (wrapper concern, [PreviewBox] seeds): a `@Preview` annotation cannot select the
 *    Colorful/Simple/High-Contrast palette MODE — the six combos are explicit `PreviewBox(seed)` wrappers.
 *  - **`fs = L`** ([fsLargeSeed]): `@Preview(fontScale = …)` is a verified NO-OP (OS fontScale pinned to
 *    1f); the large text size is injected via the seed's `fs` field only.
 *  - **Keyline stress (SC5)**: white PLA (`#FFFFFF`) on the LIGHT theme and black filament (`#000000`) on
 *    the DARK theme — the cases where the band fill ≈ surface and ONLY the neutral keyline makes the edge
 *    read. The true hex is rendered, never `brandTint`-clamped (D-05).
 *
 * The body/keyline tints come from neutral [LocalTokens] roles (`text2`/`outline`), mirroring the
 * launcher-tile call site (plan 03). Sizes use the established `fsSp(baseSp, t.fs).dp` scale — never
 * hardcoded (the launcher 40sp tile and the larger 64sp SpoolScreen surface).
 */
class SpoolBandStateProvider : PreviewParameterProvider<List<Color>> {
    override val values: Sequence<List<Color>> = sequenceOf(
        emptyList(),                              // D-03 empty spool — band omitted
        listOf(WHITE_PLA),                        // D-08 single color (also a keyline-stress fill)
        listOf(Color(0xFFE53935), Color(0xFF1E88E5)), // D-04 two-stop gradient (red → blue)
    )
}

/** Common extreme fills used for the SC5 keyline-stress cases. */
private val WHITE_PLA = Color(0xFFFFFFFF)
private val BLACK_FILAMENT = Color(0xFF000000)

/**
 * One panel rendering the [SpoolGlyph] at BOTH the launcher-tile size (40sp scale) and the larger
 * SpoolScreen-detail size (64sp scale), so a band state is judged at the two real surface sizes at once.
 */
@Composable
private fun SpoolGlyphRow(swatches: List<Color>) {
    val t = LocalTokens.current
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpoolGlyph(
            swatches = swatches,
            bodyTint = t.text2,
            keyline = t.outline,
            sizeDp = fsSp(40f, t.fs).dp,
            contentDescription = null,
        )
        SpoolGlyph(
            swatches = swatches,
            bodyTint = t.text3,
            keyline = t.outline,
            sizeDp = fsSp(64f, t.fs).dp,
            contentDescription = null,
        )
    }
}

/**
 * The FULL band-state matrix on ONE representative theme (Colorful/dark): empty / single / two-color, each
 * at the two surface sizes. Band state is the `@PreviewParameter`; theme is the [PreviewBox] wrapper.
 */
@Nexus7Previews
@Composable
private fun SpoolGlyphBandStateMatrix(
    @PreviewParameter(SpoolBandStateProvider::class) swatches: List<Color>,
) {
    PreviewBox(colorfulDark) {
        SpoolGlyphRow(swatches)
    }
}

// ---------------------------------------------------------------------------------------------
// The full 6-theme matrix on ONE representative state (the two-color gradient band, the densest render) —
// six sibling PreviewBox seeds. A @Preview annotation cannot select the palette MODE; themes are wrappers.
// ---------------------------------------------------------------------------------------------

private val gradientBand: List<Color> = listOf(Color(0xFFE53935), Color(0xFF1E88E5))

@Nexus7Previews
@Composable
private fun SpoolGlyphThemeColorfulDark() =
    PreviewBox(colorfulDark) { SpoolGlyphRow(gradientBand) }

@Nexus7Previews
@Composable
private fun SpoolGlyphThemeColorfulLight() =
    PreviewBox(colorfulLight) { SpoolGlyphRow(gradientBand) }

@Nexus7Previews
@Composable
private fun SpoolGlyphThemeSimpleDark() =
    PreviewBox(simpleDark) { SpoolGlyphRow(gradientBand) }

@Nexus7Previews
@Composable
private fun SpoolGlyphThemeSimpleLight() =
    PreviewBox(simpleLight) { SpoolGlyphRow(gradientBand) }

@Nexus7Previews
@Composable
private fun SpoolGlyphThemeHighContrastDark() =
    PreviewBox(highContrastDark) { SpoolGlyphRow(gradientBand) }

@Nexus7Previews
@Composable
private fun SpoolGlyphThemeHighContrastLight() =
    PreviewBox(highContrastLight) { SpoolGlyphRow(gradientBand) }

/**
 * The SC5 keyline-stress matrix — the two cases where the band fill ≈ the theme surface and ONLY the
 * neutral keyline makes the band edge read:
 *  - white PLA (`#FFFFFF`) on the LIGHT theme, and
 *  - black filament (`#000000`) on the DARK theme.
 * If the keyline works, both bands are visibly bounded against their near-matching backgrounds. The true
 * hex is rendered, never clamped (D-05).
 */
@Nexus7Previews
@Composable
private fun SpoolGlyphKeylineStressWhiteOnLight() =
    PreviewBox(colorfulLight) { SpoolGlyphRow(listOf(WHITE_PLA)) }

@Nexus7Previews
@Composable
private fun SpoolGlyphKeylineStressBlackOnDark() =
    PreviewBox(colorfulDark) { SpoolGlyphRow(listOf(BLACK_FILAMENT)) }

/**
 * The empty-spool variant on its own (D-03) — the body draws (neutral token), the band is ABSENT. The
 * presence/absence of the band IS the "filament loaded" signal; this panel is the visual confirm that the
 * fallback reads as an honest empty spool, not a recolored/greyed placeholder.
 */
@Nexus7Previews
@Composable
private fun SpoolGlyphEmptySpool() =
    PreviewBox(colorfulDark) { SpoolGlyphRow(emptyList()) }

/**
 * The `fs = L` shot ([fsLargeSeed]) — the glyph at the LARGEST in-app text size, proving the `fsSp`-scaled
 * box grows correctly. `@Preview(fontScale = …)` is a verified NO-OP here; fs rides the seed's `fs` field.
 */
@Nexus7Previews
@Composable
private fun SpoolGlyphFsLarge() =
    PreviewBox(fsLargeSeed) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // A label proves the fs=L text scale is active alongside the (text-size-tracking) glyph box.
            val t = LocalTokens.current
            Text(text = "fs = L", color = t.text2)
            SpoolGlyphRow(gradientBand)
        }
    }
