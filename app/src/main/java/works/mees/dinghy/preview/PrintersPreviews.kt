package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.ui.screen.PrinterMode
import works.mees.dinghy.ui.screen.PrintersContent

/**
 * Printers screen preview matrix (28-06, D-13/D-15) — the mode-toggle rebuild with the
 * Focus/Field/Foot layout and Edit/Delete armed states.
 *
 * ## Interesting axes
 * - **Mode:** Normal (default interaction), EditArmed, DeleteArmed, and the Empty-list edge case.
 * - **Connection state:** Connected (ring = accent), Error (ring = stop), Disconnected (no ring).
 *   These drive the [DetailCard] ringColor path.
 * - **Theme:** six [PreviewBox] seeds (three palette modes × dark/light).
 * - **fs = L overflow:** one [fsLargeSeed] panel to catch text/row clipping in dense mode.
 * - **RTL:** one [LocalLayoutDirection.Rtl] panel to prove `start`/`end`-relative Modifiers.
 * - **Pseudolocale:** one `locale = "en-XA"` panel to catch hardcoded English strings.
 *
 * ## No live Moonraker (SC-1)
 * Every preview drives the STATELESS [PrintersContent] seam (extracted 28-06) with pure
 * [SampleFixtures.printerProfileList] data — no AppContainer, no ViewModel, no socket.
 */

// Private fixture sets reused across panels.
private val profiles   = SampleFixtures.printerProfileList
private val activeId   = SampleFixtures.printerActiveId

// ─────────────────────────────────────────────────────────────────────────────
// Mode axis — Normal, EditArmed, DeleteArmed, Empty — on Colorful/Dark.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Normal mode — row tap switches active printer (the zero-armed default state).
 * Connection is [ConnectionState.Connected] so the [DetailCard] ring is accent-colored.
 */
@Nexus7Previews
@Composable
private fun PrintersNormalMode() =
    PreviewBox(colorfulDark) {
        PrintersContent(
            profiles        = profiles,
            activeId        = activeId,
            connectionState = ConnectionState.Connected,
            printerMode     = PrinterMode.Normal,
            onRowClick      = {},
            onAdd           = {},
            onArmEdit       = {},
            onArmDelete     = {},
            onBack          = {},
        )
    }

/**
 * EditArmed — the Edit FAB is highlighted; row tap opens the inline connection editor.
 * Connection is [ConnectionState.Disconnected] so no ring (the "not yet connected" state).
 */
@Nexus7Previews
@Composable
private fun PrintersEditArmedMode() =
    PreviewBox(colorfulDark) {
        PrintersContent(
            profiles        = profiles,
            activeId        = activeId,
            connectionState = ConnectionState.Disconnected,
            printerMode     = PrinterMode.EditArmed,
            onRowClick      = {},
            onAdd           = {},
            onArmEdit       = {},
            onArmDelete     = {},
            onBack          = {},
        )
    }

/**
 * DeleteArmed — the Delete FAB is highlighted; row tap triggers the ConfirmGuard.
 * Connection is [ConnectionState.Error] so the ring is stop-colored (the worst-case
 * design-time ring path for the [DetailCard]).
 */
@Nexus7Previews
@Composable
private fun PrintersDeleteArmedMode() =
    PreviewBox(colorfulDark) {
        PrintersContent(
            profiles        = profiles,
            activeId        = activeId,
            connectionState = ConnectionState.Error(works.mees.dinghy.net.ConnectionError.NetworkUnavailable),
            printerMode     = PrinterMode.DeleteArmed,
            onRowClick      = {},
            onAdd           = {},
            onArmEdit       = {},
            onArmDelete     = {},
            onBack          = {},
        )
    }

/**
 * Empty-list edge case — no printers configured; the empty-state headline + body are
 * the only content in the Field region, and the Focus [DetailCard] renders without a profile.
 */
@Nexus7Previews
@Composable
private fun PrintersEmptyList() =
    PreviewBox(colorfulDark) {
        PrintersContent(
            profiles        = emptyList(),
            activeId        = null,
            connectionState = ConnectionState.Disconnected,
            printerMode     = PrinterMode.Normal,
            onRowClick      = {},
            onAdd           = {},
            onArmEdit       = {},
            onArmDelete     = {},
            onBack          = {},
        )
    }

// ─────────────────────────────────────────────────────────────────────────────
// Full 6-theme matrix on one representative state (Normal/Connected).
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun PrintersThemeColorfulDark() =
    PreviewBox(colorfulDark) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            connectionState = ConnectionState.Connected,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onArmEdit = {}, onArmDelete = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrintersThemeColorfulLight() =
    PreviewBox(colorfulLight) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            connectionState = ConnectionState.Connected,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onArmEdit = {}, onArmDelete = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrintersThemeSimpleDark() =
    PreviewBox(simpleDark) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            connectionState = ConnectionState.Connected,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onArmEdit = {}, onArmDelete = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrintersThemeSimpleLight() =
    PreviewBox(simpleLight) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            connectionState = ConnectionState.Connected,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onArmEdit = {}, onArmDelete = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrintersThemeHighContrastDark() =
    PreviewBox(highContrastDark) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            connectionState = ConnectionState.Connected,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onArmEdit = {}, onArmDelete = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrintersThemeHighContrastLight() =
    PreviewBox(highContrastLight) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            connectionState = ConnectionState.Connected,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onArmEdit = {}, onArmDelete = {}, onBack = {},
        )
    }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow, RTL spot-check, pseudolocale spot-check.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The fs = L overflow shot — dense [ListRow] items must not clip label text at the largest
 * in-app text size. fs is injected via [fsLargeSeed]'s `fs` field; `@Preview(fontScale = …)`
 * is a verified NO-OP here.
 */
@Nexus7Previews
@Composable
private fun PrintersFsLargeOverflow() =
    PreviewBox(fsLargeSeed) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            connectionState = ConnectionState.Connected,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onArmEdit = {}, onArmDelete = {}, onBack = {},
        )
    }

/**
 * RTL spot-check — forces [LayoutDirection.Rtl] to verify `start`/`end`-relative Modifiers
 * (Modifier.padding, Modifier.align) mirror correctly in RTL locales.
 */
@Nexus7Previews
@Composable
private fun PrintersRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            PrintersContent(
                profiles = profiles, activeId = activeId,
                connectionState = ConnectionState.Connected,
                printerMode = PrinterMode.Normal,
                onRowClick = {}, onAdd = {}, onArmEdit = {}, onArmDelete = {}, onBack = {},
            )
        }
    }
}

/**
 * Pseudolocale spot-check (`en-XA`) — accordion-pads + brackets the app vocabulary so any
 * plain-English string that shows through un-pseudolocalized is a still-hardcoded literal.
 * Locale via the annotation alone; NOT part of [Nexus7Previews] (one dedicated panel).
 */
@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)
@Composable
private fun PrintersPseudolocaleSpotCheck() =
    PreviewBox(colorfulDark) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            connectionState = ConnectionState.Connected,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onArmEdit = {}, onArmDelete = {}, onBack = {},
        )
    }
