package works.mees.dinghy.preview

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import works.mees.dinghy.designsystem.components.AdjusterPanel
import works.mees.dinghy.designsystem.components.IncrementPicker
import works.mees.dinghy.designsystem.layout.rememberUnitGrid

// ─────────────────────────────────────────────────────────────────────────────
// Fixture data — pure values, no Moonraker
// ─────────────────────────────────────────────────────────────────────────────

/** Print-speed step set used in the main preview (5%, 10%, 25%). */
private val speedSteps = persistentListOf(5.0, 10.0, 25.0)

// ─────────────────────────────────────────────────────────────────────────────
// Demo composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * AdjusterPanel demo — value differs from baseline so the "was X" span is visible.
 *
 * Uses [BoxWithConstraints] + [rememberUnitGrid] to derive [uDp] exactly as a real screen would,
 * satisfying the unit-grid contract on the preview host.
 */
@Composable
private fun AdjusterPanelDemo(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        AdjusterPanel(
            value = 105.0,
            unit = "%",
            baseline = 100.0,    // differs → "was 100%" span is visible in this preview
            decimals = 0,
            onDecrement = {},
            onIncrement = {},
            enabled = true,
            incrementPicker = {
                IncrementPicker(
                    steps = speedSteps,
                    activeStep = 10.0,
                    onSelect = {},
                    uDp = grid.uDp,
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        )
    }
}

/**
 * AdjusterPanel demo — value is null (unreported / DASH); steppers are disabled.
 * Verifies the DASH path and that the preview doesn't crash on null value.
 */
@Composable
private fun AdjusterPanelDashDemo(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        AdjusterPanel(
            value = null,        // null → DASH, steppers disabled
            unit = " s",
            baseline = null,
            decimals = 3,
            onDecrement = {},
            onIncrement = {},
            enabled = false,     // busy-lock
            incrementPicker = {
                IncrementPicker(
                    steps = persistentListOf(0.001, 0.005, 0.01),
                    activeStep = 0.001,
                    onSelect = {},
                    uDp = grid.uDp,
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme combo matrix (docs/ui_design/PREVIEW_AND_TOKENS.md)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun AdjusterPanelColorfulDark() =
    PreviewBox(colorfulDark) { AdjusterPanelDemo() }

@Nexus7Previews
@Composable
private fun AdjusterPanelColorfulLight() =
    PreviewBox(colorfulLight) { AdjusterPanelDemo() }

@Nexus7Previews
@Composable
private fun AdjusterPanelSimpleDark() =
    PreviewBox(simpleDark) { AdjusterPanelDemo() }

@Nexus7Previews
@Composable
private fun AdjusterPanelSimpleLight() =
    PreviewBox(simpleLight) { AdjusterPanelDemo() }

@Nexus7Previews
@Composable
private fun AdjusterPanelHighContrastDark() =
    PreviewBox(highContrastDark) { AdjusterPanelDemo() }

@Nexus7Previews
@Composable
private fun AdjusterPanelHighContrastLight() =
    PreviewBox(highContrastLight) { AdjusterPanelDemo() }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check — ONLY correct via fsLargeSeed, NOT @Preview(fontScale=)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun AdjusterPanelFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { AdjusterPanelDemo() }

// ─────────────────────────────────────────────────────────────────────────────
// Landscape-specific check — 5U phone landscape (800×480dp) Focus budget
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "AdjusterPanel landscape 5U",
    widthDp = 800,
    heightDp = 480,
    showBackground = true,
)
@Composable
private fun AdjusterPanelLandscape() =
    PreviewBox(colorfulDark) { AdjusterPanelDemo() }

// ─────────────────────────────────────────────────────────────────────────────
// DASH path (null value, disabled steppers, no Reset)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun AdjusterPanelDashPath() =
    PreviewBox(colorfulDark) { AdjusterPanelDashDemo() }
