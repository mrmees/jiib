package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import works.mees.dinghy.ui.screen.SettingsContent

/**
 * @Preview matrix for SettingsScreen (28-07, D-11/D-12).
 *
 * Targets the STATELESS [SettingsContent] seam — no AppContainer, no Moonraker.
 *
 * ## Axes exercised
 *  - Webcam-enabled axis: toggles all ON (webcam profile present) vs all OFF
 *  - Battery exempt vs optimized status row
 *  - 6 theme combos on toggles-all-ON state
 *  - fs = L overflow check — verifies dense rows + numeric field don't clip at large text
 *  - RTL spotcheck — confirms row mirrors correctly under Arabic layout direction
 *  - Pseudolocale en-XA — i18n completeness (all strings via stringResource)
 */

// ─────────────────────────────────────────────────────────────────────────────
// Helper to reduce preview boilerplate
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun settingsAllOn() {
    SettingsContent(
        webcamOn = true,
        webcamEnabled = true,
        onWebcamToggle = {},
        babystepOn = true,
        onBabystepToggle = {},
        babystepLayers = 5,
        onBabystepLayers = {},
        keepScreenOn = true,
        onKeepScreenOnToggle = {},
        isExempt = false,
        onRequestExempt = {},
        onBack = {},
    )
}

@Composable
private fun settingsAllOff() {
    SettingsContent(
        webcamOn = false,
        webcamEnabled = true,
        onWebcamToggle = {},
        babystepOn = false,
        onBabystepToggle = {},
        babystepLayers = 3,
        onBabystepLayers = {},
        keepScreenOn = false,
        onKeepScreenOnToggle = {},
        isExempt = true,
        onRequestExempt = {},
        onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Axis matrix: all-ON vs all-OFF
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "Settings: all toggles ON (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SettingsAllOnPortrait() = PreviewBox(colorfulDark) { settingsAllOn() }

@Preview(
    name = "Settings: all toggles ON (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun SettingsAllOnLandscape() = PreviewBox(colorfulDark) { settingsAllOn() }

@Preview(
    name = "Settings: all toggles OFF + exempt (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SettingsAllOffPortrait() = PreviewBox(colorfulDark) { settingsAllOff() }

@Preview(
    name = "Settings: webcam disabled (no profile) (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SettingsWebcamNoProfile() = PreviewBox(colorfulDark) {
    SettingsContent(
        webcamOn = false,
        webcamEnabled = false, // no active profile — toggle greyed
        onWebcamToggle = {},
        babystepOn = true,
        onBabystepToggle = {},
        babystepLayers = 5,
        onBabystepLayers = {},
        keepScreenOn = true,
        onKeepScreenOnToggle = {},
        isExempt = false,
        onRequestExempt = {},
        onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme matrix on all-ON (most interactive state — switches lit, babystep field)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun SettingsThemeColorfulDark() = PreviewBox(colorfulDark) { settingsAllOn() }

@Nexus7Previews
@Composable
private fun SettingsThemeColorfulLight() = PreviewBox(colorfulLight) { settingsAllOn() }

@Nexus7Previews
@Composable
private fun SettingsThemeSimpleDark() = PreviewBox(simpleDark) { settingsAllOn() }

@Nexus7Previews
@Composable
private fun SettingsThemeSimpleLight() = PreviewBox(simpleLight) { settingsAllOn() }

@Nexus7Previews
@Composable
private fun SettingsThemeHighContrastDark() = PreviewBox(highContrastDark) { settingsAllOn() }

@Nexus7Previews
@Composable
private fun SettingsThemeHighContrastLight() = PreviewBox(highContrastLight) { settingsAllOn() }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check — dense rows + numeric field at largest text size
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "Settings fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SettingsFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) { settingsAllOn() }

@Preview(
    name = "Settings fs=L landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun SettingsFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) { settingsAllOn() }

// ─────────────────────────────────────────────────────────────────────────────
// RTL spotcheck — confirms start/end-relative modifiers mirror correctly
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun SettingsRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            settingsAllOn()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pseudolocale en-XA — i18n completeness sweep
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "Settings pseudolocale en-XA",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun SettingsPseudolocaleSpotCheck() = PreviewBox(colorfulDark) { settingsAllOn() }
