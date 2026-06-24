package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import works.mees.dinghy.ui.screen.PrinterMode
import works.mees.dinghy.ui.screen.PrintersContent

/**
 * Printers screen preview matrix (Task 5 — mode collapse, instructions Focus) — the 2-mode
 * foot bar with the static instructions Focus and Add/Find Field rows.
 *
 * ## Interesting axes
 * - **Mode:** Normal (default interaction), EditArmed, and the Empty-list edge case.
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
// Mode axis — Normal, EditArmed, Empty — on Colorful/Dark.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Normal mode — row tap switches active printer (the zero-armed default state).
 */
@Nexus7Previews
@Composable
private fun PrintersNormalMode() =
    PreviewBox(colorfulDark) {
        PrintersContent(
            profiles    = profiles,
            activeId    = activeId,
            printerMode = PrinterMode.Normal,
            onRowClick  = {},
            onAdd       = {},
            onFind      = {},
            onArmEdit   = {},
            onBack      = {},
        )
    }

/**
 * EditArmed — the Edit foot button is filled accentSoft; row tap opens the inline connection editor.
 */
@Nexus7Previews
@Composable
private fun PrintersEditArmedMode() =
    PreviewBox(colorfulDark) {
        PrintersContent(
            profiles    = profiles,
            activeId    = activeId,
            printerMode = PrinterMode.EditArmed,
            onRowClick  = {},
            onAdd       = {},
            onFind      = {},
            onArmEdit   = {},
            onBack      = {},
        )
    }

/**
 * Empty-list edge case — no printers configured; the instructions Focus remains visible,
 * only the Add + Find rows appear in the Field (profile rows are empty).
 */
@Nexus7Previews
@Composable
private fun PrintersEmptyList() =
    PreviewBox(colorfulDark) {
        PrintersContent(
            profiles    = emptyList(),
            activeId    = null,
            printerMode = PrinterMode.Normal,
            onRowClick  = {},
            onAdd       = {},
            onFind      = {},
            onArmEdit   = {},
            onBack      = {},
        )
    }

// ─────────────────────────────────────────────────────────────────────────────
// Full 6-theme matrix on one representative state (Normal).
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun PrintersThemeColorfulDark() =
    PreviewBox(colorfulDark) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onFind = {}, onArmEdit = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrintersThemeColorfulLight() =
    PreviewBox(colorfulLight) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onFind = {}, onArmEdit = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrintersThemeSimpleDark() =
    PreviewBox(simpleDark) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onFind = {}, onArmEdit = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrintersThemeSimpleLight() =
    PreviewBox(simpleLight) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onFind = {}, onArmEdit = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrintersThemeHighContrastDark() =
    PreviewBox(highContrastDark) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onFind = {}, onArmEdit = {}, onBack = {},
        )
    }

@Nexus7Previews
@Composable
private fun PrintersThemeHighContrastLight() =
    PreviewBox(highContrastLight) {
        PrintersContent(
            profiles = profiles, activeId = activeId,
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onFind = {}, onArmEdit = {}, onBack = {},
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
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onFind = {}, onArmEdit = {}, onBack = {},
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
                printerMode = PrinterMode.Normal,
                onRowClick = {}, onAdd = {}, onFind = {}, onArmEdit = {}, onBack = {},
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
            printerMode = PrinterMode.Normal,
            onRowClick = {}, onAdd = {}, onFind = {}, onArmEdit = {}, onBack = {},
        )
    }
