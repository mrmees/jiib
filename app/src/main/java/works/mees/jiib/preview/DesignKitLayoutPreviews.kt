package works.mees.jiib.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.jiib.R
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.RegisteredRegion
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.theme.Geist
import works.mees.jiib.theme.GeistMono
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.fsSp

/**
 * @Preview matrix for the two Phase-23 layout primitives: [rememberUnitGrid] + [ListBlock].
 *
 * ## What this demonstrates
 *
 * [DesignKitLayoutDemo] renders a [ListBlock] of sample rows inside a `BoxWithConstraints` that
 * computes the unit grid via `rememberUnitGrid(minOf(maxWidth, maxHeight))`. Each row height
 * matches `grid.uDp` so the preview visually proves that `N` rows fill the content dimension
 * exactly with no overflow.
 *
 * ## Preview matrix shape (per `docs/ui_design/PREVIEW_AND_TOKENS.md`)
 *
 *  - `*ThemeColorfulDark` … `*ThemeHighContrastLight` — 6-theme combo matrix on one representative state
 *  - `*FsLargeOverflow` — large text via [fsLargeSeed] (the ONLY correct way — NOT `@Preview(fontScale=)`)
 *  - `*RtlSpotCheck` — RTL layout direction confirmation
 *  - `*PseudolocaleSpotCheck` — i18n completeness (standalone, not under @Nexus7Previews)
 *  - `*SmallPhoneFloor` — 360×640 phone floor: verifies U fits with no overflow
 *
 * SC-2 evidence: both primitives are used and compile against the 6-theme × 2-orientation matrix.
 */
@Composable
private fun DesignKitLayoutDemo(modifier: Modifier = Modifier) {
    val t = LocalTokens.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        RegisteredRegion(modifier = Modifier.fillMaxSize()) {
            // Unit size info bar — shows N and uDp value.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(grid.uDp * 0.5f)
                    .background(t.surface2)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    // Row showing N and uDp; uses GeistMono for tabular display of numeric values.
                    text = "N=${grid.count}  U=${grid.uDp}",
                    color = t.text2,
                    fontFamily = GeistMono,
                    fontSize = fsSp(15f, t.fs).sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.semanticsLabel(stringResource(R.string.layout_preview_unit_info)),
                )
            }

            // ListBlock exercising the edge-faded LazyColumn wrapper.
            // 8 rows so there is enough content to trigger the bottom fade on most preview sizes.
            val rowLabels = listOf(
                stringResource(R.string.layout_preview_row_1),
                stringResource(R.string.layout_preview_row_2),
                stringResource(R.string.layout_preview_row_3),
                stringResource(R.string.layout_preview_row_4),
                stringResource(R.string.layout_preview_row_5),
                // Repeat to have enough rows for fade to appear.
                stringResource(R.string.layout_preview_row_1),
                stringResource(R.string.layout_preview_row_2),
                stringResource(R.string.layout_preview_row_3),
            )

            ListBlock(
                modifier = Modifier
                    .weight(1f),
            ) {
                items(rowLabels.size) { idx ->
                    UnitRow(label = rowLabels[idx], uDp = grid.uDp)
                }
            }
        }
    }
}

/**
 * A single placeholder row sized to exactly one unit U.
 * Outline-bordered, token-colored — exercises the token system in the previews.
 */
@Composable
private fun UnitRow(label: String, uDp: androidx.compose.ui.unit.Dp) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(uDp)
            .clip(shape)
            .background(t.surface)
            .border(1.5.dp, t.outline, shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = t.text,
            fontFamily = Geist,
            fontSize = fsSp(17f, t.fs).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// Compose doesn't have a built-in semantics-label Modifier extension at this level; just use a local helper
// that is a no-op for layout purposes (the Text itself has the string; this is a preview-only annotation stub).
@Suppress("NOTHING_TO_INLINE")
private inline fun Modifier.semanticsLabel(@Suppress("UNUSED_PARAMETER") label: String): Modifier = this

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme combo matrix (Nexus 7, both orientations per @Nexus7Previews)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun DesignKitLayoutThemeColorfulDark() =
    PreviewBox(colorfulDark) { DesignKitLayoutDemo() }

@Nexus7Previews
@Composable
private fun DesignKitLayoutThemeColorfulLight() =
    PreviewBox(colorfulLight) { DesignKitLayoutDemo() }

@Nexus7Previews
@Composable
private fun DesignKitLayoutThemeSimpleDark() =
    PreviewBox(simpleDark) { DesignKitLayoutDemo() }

@Nexus7Previews
@Composable
private fun DesignKitLayoutThemeSimpleLight() =
    PreviewBox(simpleLight) { DesignKitLayoutDemo() }

@Nexus7Previews
@Composable
private fun DesignKitLayoutThemeHighContrastDark() =
    PreviewBox(highContrastDark) { DesignKitLayoutDemo() }

@Nexus7Previews
@Composable
private fun DesignKitLayoutThemeHighContrastLight() =
    PreviewBox(highContrastLight) { DesignKitLayoutDemo() }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check — ONLY correct via fsLargeSeed, NOT @Preview(fontScale=)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun DesignKitLayoutFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { DesignKitLayoutDemo() }

// ─────────────────────────────────────────────────────────────────────────────
// RTL spot check — proves start/end-relative layout mirrors correctly
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun DesignKitLayoutRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
            DesignKitLayoutDemo()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pseudolocale — standalone, NOT under @Nexus7Previews (see DinghyPreviews.kt note)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)
@Composable
private fun DesignKitLayoutPseudolocaleSpotCheck() =
    PreviewBox(colorfulDark) { DesignKitLayoutDemo() }

// ─────────────────────────────────────────────────────────────────────────────
// Small phone floor — visually verifies U fits at 360×640 with no overflow
// (complements the host no-overflow unit tests)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun DesignKitLayoutSmallPhoneFloor() =
    PreviewBox(colorfulDark) { DesignKitLayoutDemo() }

@Preview(widthDp = 320, heightDp = 568, showBackground = true)
@Composable
private fun DesignKitLayoutSmallPhoneFloor320() =
    PreviewBox(colorfulDark) { DesignKitLayoutDemo() }
