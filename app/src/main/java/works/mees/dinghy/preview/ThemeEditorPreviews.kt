package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.theme.StatusSlot
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.ui.screen.ThemeEditorContent

/**
 * @Preview matrix for ThemeEditorScreen (28-08).
 *
 * Targets the stateless [ThemeEditorContent] seam — no live Moonraker, no AppContainer,
 * no coroutines. Fixture data is defined inline (plan rule: do NOT edit SampleFixtures.kt
 * this wave — 28-06 owns it).
 *
 * Primary preview axis: pool-slot picker OPEN vs CLOSED (the S/V square toggle).
 *
 * Matrix structure:
 *  - 6 theme combos (main body closed — the most common view of the editor)
 *  - fs = L overflow check (long labels + wheel + S/V square + presets all visible)
 *  - RTL spot check (layout mirroring of the slot grid + presets row)
 *  - Pseudolocale (i18n sweep)
 *  - Pool slot picker OPEN (combo 0: colorfulDark)
 *  - Status slot picker OPEN (combo 0: colorfulDark)
 */

// ─────────────────────────────────────────────────────────────────────────────
// Inline fixture data (do NOT import from SampleFixtures — 28-06 owns it)
// ─────────────────────────────────────────────────────────────────────────────

/** A mock pool overrides map: slot 0 has a custom amber override. */
private val mockPoolOverrides: Map<String, Long> = mapOf("0" to 0xFFFFA500L)

/** A mock status overrides map: Stop slot has a custom blue override. */
private val mockStatusOverrides: Map<String, Long> = mapOf("stop" to 0xFF4488FFL)

// ─────────────────────────────────────────────────────────────────────────────
// Main body CLOSED — 6 theme combos
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "ThemeEditor: Colorful/Dark closed (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ThemeEditorColorfulDarkClosed() = PreviewBox(colorfulDark) {
    ThemeEditorContent(
        hue = 220f, sat = 0.85f, value = 1f,
        dark = true, fsChoice = FontScale.M, paletteMode = ThemeResolver.MODE_COLORFUL,
        poolOverrides = mockPoolOverrides, statusOverrides = emptyMap(),
        editingSlot = null, editingStatusSlot = null, onBack = {},
    )
}

@Preview(
    name = "ThemeEditor: Colorful/Light closed (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ThemeEditorColorfulLightClosed() = PreviewBox(colorfulLight) {
    ThemeEditorContent(
        hue = 220f, sat = 0.85f, value = 1f,
        dark = false, fsChoice = FontScale.M, paletteMode = ThemeResolver.MODE_COLORFUL,
        poolOverrides = emptyMap(), statusOverrides = emptyMap(),
        editingSlot = null, editingStatusSlot = null, onBack = {},
    )
}

@Preview(
    name = "ThemeEditor: Simple/Dark closed (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ThemeEditorSimpleDarkClosed() = PreviewBox(simpleDark) {
    ThemeEditorContent(
        hue = 220f, sat = 0.85f, value = 1f,
        dark = true, fsChoice = FontScale.M, paletteMode = ThemeResolver.MODE_SIMPLE,
        poolOverrides = emptyMap(), statusOverrides = emptyMap(),
        editingSlot = null, editingStatusSlot = null, onBack = {},
    )
}

@Preview(
    name = "ThemeEditor: Simple/Light closed (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ThemeEditorSimpleLightClosed() = PreviewBox(simpleLight) {
    ThemeEditorContent(
        hue = 220f, sat = 0.85f, value = 1f,
        dark = false, fsChoice = FontScale.M, paletteMode = ThemeResolver.MODE_SIMPLE,
        poolOverrides = emptyMap(), statusOverrides = emptyMap(),
        editingSlot = null, editingStatusSlot = null, onBack = {},
    )
}

@Preview(
    name = "ThemeEditor: HighContrast/Dark closed (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ThemeEditorHighContrastDarkClosed() = PreviewBox(highContrastDark) {
    ThemeEditorContent(
        hue = 220f, sat = 0.85f, value = 1f,
        dark = true, fsChoice = FontScale.M, paletteMode = ThemeResolver.MODE_HIGH_CONTRAST,
        poolOverrides = emptyMap(), statusOverrides = emptyMap(),
        editingSlot = null, editingStatusSlot = null, onBack = {},
    )
}

@Preview(
    name = "ThemeEditor: HighContrast/Light closed (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ThemeEditorHighContrastLightClosed() = PreviewBox(highContrastLight) {
    ThemeEditorContent(
        hue = 220f, sat = 0.85f, value = 1f,
        dark = false, fsChoice = FontScale.M, paletteMode = ThemeResolver.MODE_HIGH_CONTRAST,
        poolOverrides = emptyMap(), statusOverrides = emptyMap(),
        editingSlot = null, editingStatusSlot = null, onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "ThemeEditor: FsLargeOverflow (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ThemeEditorFsLargeOverflow() = PreviewBox(fsLargeSeed) {
    ThemeEditorContent(
        hue = 120f, sat = 0.7f, value = 0.9f,
        dark = true, fsChoice = FontScale.L, paletteMode = ThemeResolver.MODE_COLORFUL,
        poolOverrides = mockPoolOverrides, statusOverrides = mockStatusOverrides,
        editingSlot = null, editingStatusSlot = null, onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// RTL spot check
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "ThemeEditor: RTL spot check (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    locale = "ar",
    showBackground = true,
)
@Composable
private fun ThemeEditorRtlSpotCheck() = PreviewBox(colorfulDark) {
    ThemeEditorContent(
        hue = 30f, sat = 1f, value = 1f,
        dark = true, fsChoice = FontScale.M, paletteMode = ThemeResolver.MODE_COLORFUL,
        poolOverrides = emptyMap(), statusOverrides = emptyMap(),
        editingSlot = null, editingStatusSlot = null, onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Pseudolocale — i18n sweep
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "ThemeEditor: Pseudolocale en-XA (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun ThemeEditorPseudolocale() = PreviewBox(colorfulDark) {
    ThemeEditorContent(
        hue = 220f, sat = 0.85f, value = 1f,
        dark = true, fsChoice = FontScale.M, paletteMode = ThemeResolver.MODE_COLORFUL,
        poolOverrides = emptyMap(), statusOverrides = emptyMap(),
        editingSlot = null, editingStatusSlot = null, onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Slot picker OPEN — pool slot (primary axis: D-16 S/V square visible)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "ThemeEditor: Pool slot 0 picker OPEN Colorful/Dark (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ThemeEditorPoolPickerOpen() = PreviewBox(colorfulDark) {
    ThemeEditorContent(
        hue = 30f, sat = 0.8f, value = 0.9f,
        dark = true, fsChoice = FontScale.M, paletteMode = ThemeResolver.MODE_COLORFUL,
        poolOverrides = mockPoolOverrides, statusOverrides = emptyMap(),
        editingSlot = 0, editingStatusSlot = null, onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Slot picker OPEN — status slot (Stop — the safety-shape overlay is visible)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "ThemeEditor: Stop status picker OPEN Colorful/Dark (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ThemeEditorStatusPickerOpen() = PreviewBox(colorfulDark) {
    ThemeEditorContent(
        hue = 0f, sat = 1f, value = 0.9f,
        dark = true, fsChoice = FontScale.M, paletteMode = ThemeResolver.MODE_COLORFUL,
        poolOverrides = emptyMap(), statusOverrides = mockStatusOverrides,
        editingSlot = null, editingStatusSlot = StatusSlot.Stop, onBack = {},
    )
}
