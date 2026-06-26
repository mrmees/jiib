package works.mees.jiib.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.net.ConnectionError
import works.mees.jiib.ui.screen.PrinterSettingsContent

/**
 * @Preview matrix for [PrinterSettingsContent] (per-printer settings hub).
 *
 * Targets the STATELESS [PrinterSettingsContent] seam — no AppContainer, no Moonraker.
 *
 * ## Axes exercised
 *  - **Connection state:** Connected (accent ring), Disconnected (no ring), Error (stop ring).
 *  - **Active vs empty:** active profile present vs null (empty state).
 *  - **6 theme combos** on the representative Connected/active state.
 *  - **fs = L overflow** — dense rows must not clip at largest text size.
 *  - **RTL spot-check** — start/end-relative Modifiers mirror correctly.
 *  - **Pseudolocale en-XA** — i18n completeness (all strings via stringResource).
 */

// Private fixtures — match SampleFixtures' Profile construction shape.
private val activeProfile = SampleFixtures.printerProfileList.first()

// ─────────────────────────────────────────────────────────────────────────────
// Connection-state axis (Colorful/dark — the default design intent).
// ─────────────────────────────────────────────────────────────────────────────

/** Connected — accent ring on FocusFrame, all rows visible. */
@Nexus7Previews
@Composable
private fun PrinterSettingsConnected() =
    PreviewBox(colorfulDark) {
        PrinterSettingsContent(
            activeProfile = activeProfile,
            connectionState = ConnectionState.Connected,
            onConnection = {},
            onTheme = {},
            onSystemInfo = {},
            onAdd = {},
            onBack = {},
        )
    }

/** Disconnected — no ring on FocusFrame, connection-state label grayed. */
@Nexus7Previews
@Composable
private fun PrinterSettingsDisconnected() =
    PreviewBox(colorfulDark) {
        PrinterSettingsContent(
            activeProfile = activeProfile,
            connectionState = ConnectionState.Disconnected,
            onConnection = {},
            onTheme = {},
            onSystemInfo = {},
            onAdd = {},
            onBack = {},
        )
    }

/** Error state — stop-colored ring on FocusFrame. */
@Nexus7Previews
@Composable
private fun PrinterSettingsError() =
    PreviewBox(colorfulDark) {
        PrinterSettingsContent(
            activeProfile = activeProfile,
            connectionState = ConnectionState.Error(ConnectionError.NetworkUnavailable),
            onConnection = {},
            onTheme = {},
            onSystemInfo = {},
            onAdd = {},
            onBack = {},
        )
    }

// ─────────────────────────────────────────────────────────────────────────────
// Empty state (no active profile).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Empty state — activeProfile = null. Focus shows the no-printers headline/body;
 * Field shows only the Add affordance row.
 */
@Nexus7Previews
@Composable
private fun PrinterSettingsEmpty() =
    PreviewBox(colorfulDark) {
        PrinterSettingsContent(
            activeProfile = null,
            connectionState = ConnectionState.Disconnected,
            onConnection = {},
            onTheme = {},
            onSystemInfo = {},
            onAdd = {},
            onBack = {},
        )
    }

// ─────────────────────────────────────────────────────────────────────────────
// Full 6-theme matrix on one representative state (Connected/active).
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun PrinterSettingsThemeColorfulDark() =
    PreviewBox(colorfulDark) {
        PrinterSettingsContent(
            activeProfile = activeProfile,
            connectionState = ConnectionState.Connected,
            onConnection = {}, onTheme = {}, onSystemInfo = {},
            onAdd = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrinterSettingsThemeColorfulLight() =
    PreviewBox(colorfulLight) {
        PrinterSettingsContent(
            activeProfile = activeProfile,
            connectionState = ConnectionState.Connected,
            onConnection = {}, onTheme = {}, onSystemInfo = {},
            onAdd = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrinterSettingsThemeSimpleDark() =
    PreviewBox(simpleDark) {
        PrinterSettingsContent(
            activeProfile = activeProfile,
            connectionState = ConnectionState.Connected,
            onConnection = {}, onTheme = {}, onSystemInfo = {},
            onAdd = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrinterSettingsThemeSimpleLight() =
    PreviewBox(simpleLight) {
        PrinterSettingsContent(
            activeProfile = activeProfile,
            connectionState = ConnectionState.Connected,
            onConnection = {}, onTheme = {}, onSystemInfo = {},
            onAdd = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrinterSettingsThemeHighContrastDark() =
    PreviewBox(highContrastDark) {
        PrinterSettingsContent(
            activeProfile = activeProfile,
            connectionState = ConnectionState.Connected,
            onConnection = {}, onTheme = {}, onSystemInfo = {},
            onAdd = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrinterSettingsThemeHighContrastLight() =
    PreviewBox(highContrastLight) {
        PrinterSettingsContent(
            activeProfile = activeProfile,
            connectionState = ConnectionState.Connected,
            onConnection = {}, onTheme = {}, onSystemInfo = {},
            onAdd = {}, onBack = {},
        )
    }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow, RTL spot-check, pseudolocale spot-check.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The fs = L overflow shot — dense [ListRow] items must not clip label text at the largest
 * in-app text size. fs is injected via [fsLargeSeed]'s `fs` field.
 */
@Nexus7Previews
@Composable
private fun PrinterSettingsFsLargeOverflow() =
    PreviewBox(fsLargeSeed) {
        PrinterSettingsContent(
            activeProfile = activeProfile,
            connectionState = ConnectionState.Connected,
            onConnection = {}, onTheme = {}, onSystemInfo = {},
            onAdd = {}, onBack = {},
        )
    }

/**
 * RTL spot-check — forces [LayoutDirection.Rtl] to verify start/end-relative Modifiers
 * mirror correctly in RTL locales.
 */
@Nexus7Previews
@Composable
private fun PrinterSettingsRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            PrinterSettingsContent(
                activeProfile = activeProfile,
                connectionState = ConnectionState.Connected,
                onConnection = {}, onTheme = {}, onSystemInfo = {},
                onAdd = {}, onBack = {},
            )
        }
    }
}

/**
 * Pseudolocale spot-check (`en-XA`) — accordion-pads + brackets the app vocabulary so any
 * plain-English string that shows through un-pseudolocalized is a still-hardcoded literal.
 */
@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)
@Composable
private fun PrinterSettingsPseudolocaleSpotCheck() =
    PreviewBox(colorfulDark) {
        PrinterSettingsContent(
            activeProfile = activeProfile,
            connectionState = ConnectionState.Connected,
            onConnection = {}, onTheme = {}, onSystemInfo = {},
            onAdd = {}, onBack = {},
        )
    }
