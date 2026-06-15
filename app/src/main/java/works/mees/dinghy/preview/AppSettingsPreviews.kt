package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.ui.screen.AppSettingsContent

/**
 * @Preview matrix for AppSettingsScreen (app-global settings: text size, display, battery, babystep).
 *
 * Targets the STATELESS [AppSettingsContent] seam — no AppContainer, no Moonraker.
 *
 * ## Axes exercised
 *  - All-on vs all-off toggle states (battery exempt vs optimized)
 *  - 6 theme combos on toggles-all-ON state
 *  - FontScale axis: M (default) across most previews; L in the overflow check
 *  - fs = L overflow check — verifies dense rows + text-size selector + numeric field don't clip
 *  - RTL spotcheck — confirms row mirrors correctly under Arabic layout direction
 *  - Pseudolocale en-XA — i18n completeness (all strings via stringResource)
 */

// ─────────────────────────────────────────────────────────────────────────────
// Helper to reduce preview boilerplate
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun appSettingsAllOn() {
    AppSettingsContent(
        fontScale = FontScale.M,
        onFontScale = {},
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
private fun appSettingsAllOff() {
    AppSettingsContent(
        fontScale = FontScale.M,
        onFontScale = {},
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
    name = "AppSettings: all toggles ON (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun AppSettingsAllOnPortrait() = PreviewBox(colorfulDark) { appSettingsAllOn() }

@Preview(
    name = "AppSettings: all toggles ON (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun AppSettingsAllOnLandscape() = PreviewBox(colorfulDark) { appSettingsAllOn() }

@Preview(
    name = "AppSettings: all toggles OFF + exempt (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun AppSettingsAllOffPortrait() = PreviewBox(colorfulDark) { appSettingsAllOff() }

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme matrix on all-ON (most interactive state — switches lit, babystep field, S/M/L selector)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun AppSettingsThemeColorfulDark() = PreviewBox(colorfulDark) { appSettingsAllOn() }

@Nexus7Previews
@Composable
private fun AppSettingsThemeColorfulLight() = PreviewBox(colorfulLight) { appSettingsAllOn() }

@Nexus7Previews
@Composable
private fun AppSettingsThemeSimpleDark() = PreviewBox(simpleDark) { appSettingsAllOn() }

@Nexus7Previews
@Composable
private fun AppSettingsThemeSimpleLight() = PreviewBox(simpleLight) { appSettingsAllOn() }

@Nexus7Previews
@Composable
private fun AppSettingsThemeHighContrastDark() = PreviewBox(highContrastDark) { appSettingsAllOn() }

@Nexus7Previews
@Composable
private fun AppSettingsThemeHighContrastLight() = PreviewBox(highContrastLight) { appSettingsAllOn() }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check — dense rows + text-size selector + numeric field at largest text size
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "AppSettings fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun AppSettingsFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) {
    AppSettingsContent(
        fontScale = FontScale.L,
        onFontScale = {},
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

@Preview(
    name = "AppSettings fs=L landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun AppSettingsFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) {
    AppSettingsContent(
        fontScale = FontScale.L,
        onFontScale = {},
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
// RTL spotcheck — confirms start/end-relative modifiers mirror correctly
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun AppSettingsRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            appSettingsAllOn()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pseudolocale en-XA — i18n completeness sweep
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "AppSettings pseudolocale en-XA",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun AppSettingsPseudolocaleSpotCheck() = PreviewBox(colorfulDark) { appSettingsAllOn() }
