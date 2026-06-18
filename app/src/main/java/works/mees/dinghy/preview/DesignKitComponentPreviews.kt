package works.mees.dinghy.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.persistentListOf
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FocusEdge
import works.mees.dinghy.designsystem.components.FillMeter
import works.mees.dinghy.designsystem.components.FilterOption
import works.mees.dinghy.designsystem.components.FilterRow
import works.mees.dinghy.designsystem.components.FloatingEStop
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.SortOption
import works.mees.dinghy.designsystem.components.SortRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.RegisteredRegion
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

// ─────────────────────────────────────────────────────────────────────────────
// Sample option keys for SortRow / FilterRow fixture data
// ─────────────────────────────────────────────────────────────────────────────

private enum class SampleSortKey { Name, Material, Weight }
private enum class SampleFilterKey { Material, Color, Vendor }

// ─────────────────────────────────────────────────────────────────────────────
// Sample fixtures (local — no Moonraker, pure values)
// ─────────────────────────────────────────────────────────────────────────────

// Filament color as raw hex Color — THEME-01 data carve-out (not brandTint-clamped)
private val sampleFilamentColor = Color(0xFF_EF_EF_EF.toInt())  // white filament hex
private val sampleBlueFilamentColor = Color(0xFF_00_7A_CC.toInt()) // blue filament hex

// ─────────────────────────────────────────────────────────────────────────────
// Primary demo composable — exercises all 6 component classes in one composition
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @Preview demo exercising all 6 Phase-23 component classes:
 * [ListRow], [FocusFrame], [FillMeter], [FootButtonBar], [FloatingEStop], [SortRow] + [FilterRow].
 *
 * Uses [BoxWithConstraints] → [rememberUnitGrid] to derive `grid.uDp` exactly as a real screen
 * would. All text via [stringResource]; all colors via [LocalTokens.current]; icons via [DinghyIcons].
 */
@Composable
private fun DesignKitComponentDemo(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        // Sample sort options — text-label tiles (type-tile retired 2026-06-17)
        val sortOptions = persistentListOf(
            SortOption(
                key = SampleSortKey.Name,
                label = "Name",
                contentDescriptionRes = R.string.cd_sort_by_name,
                directionUp = true,     // active + ascending
            ),
            SortOption(
                key = SampleSortKey.Material,
                label = "Material",
                contentDescriptionRes = R.string.cd_sort_by_material,
                directionUp = null,     // not active
            ),
            SortOption(
                key = SampleSortKey.Weight,
                label = "Weight",
                contentDescriptionRes = R.string.cd_sort_by_weight,
                directionUp = null,     // not active
            ),
        )

        // Sample filter options — text-label tiles (type-tile retired 2026-06-17)
        val filterOptions = persistentListOf(
            FilterOption(
                key = SampleFilterKey.Material,
                label = "Material",
                contentDescriptionRes = R.string.cd_filter_by_material,
                isActive = true,
            ),
            FilterOption(
                key = SampleFilterKey.Color,
                label = "Color",
                contentDescriptionRes = R.string.cd_filter_by_color,
                isActive = false,
            ),
            FilterOption(
                key = SampleFilterKey.Vendor,
                label = "Vendor",
                contentDescriptionRes = R.string.cd_filter_by_vendor,
                isActive = false,
            ),
        )

        RegisteredRegion(modifier = Modifier.fillMaxSize()) {
            // ── SortRow + FilterRow ───────────────────────────────────────────────
            SortRow(
                options = sortOptions,
                activeKey = SampleSortKey.Name,
                onSelect = {},
                uDp = grid.uDp,
                modifier = Modifier.fillMaxWidth(),
            )
            FilterRow(
                options = filterOptions,
                onSelect = {},
                uDp = grid.uDp,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── FocusFrame with FillMeter inside ────────────────────────────────
            // edge = FocusEdge.Data(sampleFilamentColor) (data carve-out, not brandTint-clamped)
            FocusFrame(
                title = "Preview",
                icon = DinghyIcons.LauncherFiles,
                uDp = 96.dp,
                edge = FocusEdge.Data(sampleFilamentColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.kit_preview_detail_title),
                    color = t.text,
                    fontFamily = Geist,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = fsSp(20f, t.fs).sp,
                )
                Text(
                    text = stringResource(R.string.kit_preview_detail_vendor),
                    color = t.text2,
                    fontFamily = Geist,
                    fontSize = fsSp(15f, t.fs).sp,
                )
                FillMeter(
                    fraction = 0.74f,
                    fillColor = sampleFilamentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    label = stringResource(R.string.kit_preview_detail_fill_label),
                )
            }

            // ── ListBlock with ListRow entries (selected + unselected) ───────────
            // Box wrapping the list + FloatingEStop as overlay sibling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                ListBlock(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // selected row
                    item(key = "spool-1") {
                        ListRow(
                            selected = true,
                            onClick = {},
                            uDp = grid.uDp,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.kit_preview_spool_name),
                                    color = t.text,
                                    fontFamily = Geist,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = fsSp(17f, t.fs).sp,
                                )
                                Text(
                                    text = stringResource(R.string.kit_preview_spool_weight),
                                    color = t.text2,
                                    fontFamily = GeistMono,
                                    fontSize = fsSp(15f, t.fs).sp,
                                )
                            }
                        }
                    }
                    // unselected row
                    item(key = "spool-2") {
                        ListRow(
                            selected = false,
                            onClick = {},
                            uDp = grid.uDp,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.kit_preview_spool_name_2),
                                    color = t.text,
                                    fontFamily = Geist,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = fsSp(17f, t.fs).sp,
                                )
                                Text(
                                    text = stringResource(R.string.kit_preview_spool_weight_2),
                                    color = t.text2,
                                    fontFamily = GeistMono,
                                    fontSize = fsSp(15f, t.fs).sp,
                                )
                            }
                        }
                    }
                }

                // FloatingEStop — overlay sibling, positioning via modifier (Pitfall 7)
                FloatingEStop(
                    visible = true,
                    onClick = {},
                    uDp = grid.uDp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }

            // ── FootButtonBar ────────────────────────────────────────────────────
            // ≤2 actions → icon+text mode
            FootButtonBar(uDp = grid.uDp, actions = listOf(
                FootAction("Back", DinghyIcons.Back, {}, Intent.Accent),
                FootAction("Save", DinghyIcons.Save, {}, Intent.Go),
            ))
            // ≥3 actions → icon-only mode (FOOT_BAR_ICON_ONLY_THRESHOLD)
            FootButtonBar(uDp = grid.uDp, actions = listOf(
                FootAction("Back", DinghyIcons.Back, {}, Intent.Accent),
                FootAction("Edit", DinghyIcons.Edit, {}, Intent.Accent),
                FootAction("Delete", DinghyIcons.Delete, {}, Intent.Danger),
            ))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme combo matrix (Nexus 7, both orientations per @Nexus7Previews)
// SC-2 evidence: all 6 classes used across 6 theme combos × 2 orientations
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun DesignKitComponentThemeColorfulDark() =
    PreviewBox(colorfulDark) { DesignKitComponentDemo() }

@Nexus7Previews
@Composable
private fun DesignKitComponentThemeColorfulLight() =
    PreviewBox(colorfulLight) { DesignKitComponentDemo() }

@Nexus7Previews
@Composable
private fun DesignKitComponentThemeSimpleDark() =
    PreviewBox(simpleDark) { DesignKitComponentDemo() }

@Nexus7Previews
@Composable
private fun DesignKitComponentThemeSimpleLight() =
    PreviewBox(simpleLight) { DesignKitComponentDemo() }

@Nexus7Previews
@Composable
private fun DesignKitComponentThemeHighContrastDark() =
    PreviewBox(highContrastDark) { DesignKitComponentDemo() }

@Nexus7Previews
@Composable
private fun DesignKitComponentThemeHighContrastLight() =
    PreviewBox(highContrastLight) { DesignKitComponentDemo() }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check — ONLY correct via fsLargeSeed, NOT @Preview(fontScale=)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun DesignKitComponentFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { DesignKitComponentDemo() }

// ─────────────────────────────────────────────────────────────────────────────
// RTL spot check — proves start/end-relative layout mirrors correctly
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun DesignKitComponentRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            DesignKitComponentDemo()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pseudolocale — standalone, NOT under @Nexus7Previews (see DinghyPreviews.kt note)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)
@Composable
private fun DesignKitComponentPseudolocaleSpotCheck() =
    PreviewBox(colorfulDark) { DesignKitComponentDemo() }
