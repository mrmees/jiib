package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import works.mees.dinghy.ui.screen.SystemPageContent

/**
 * @Preview matrix for SystemPageScreen (28-02).
 *
 * Targets the STATELESS [SystemPageContent] seam (WARNING-5 preview-first convention).
 * No live Moonraker, no VM, no AppContainer — pure fixture data from [SampleFixtures.systemPage*].
 *
 * Structure:
 *  - State axis (2 states): active-printer-present / no-active-printer (blank name)
 *  - 6 theme combos (colorfulDark / colorfulLight / simpleDark / simpleLight / highContrastDark / highContrastLight)
 *  - fs = L overflow: proves no text clipping at L size
 *  - RTL spot check
 *  - Pseudolocale en-XA
 *  - Landscape spot panel: proves 40%-width Focus column
 *
 * Interesting axes (28-UI-SPEC.md §Preview Contract):
 *  - Active printer present vs absent — Focus content varies (printer name row shows/hides)
 */

// ─────────────────────────────────────────────────────────────────────────────
// State axis: printer present / absent
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SystemPage: printer present (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SystemPagePrinterPresent() = PreviewBox(colorfulDark) {
    SystemPageContent(
        activePrinterName = SampleFixtures.systemPageActivePrinter,
        versionName = SampleFixtures.systemPageVersion,
        onNavigate = {},
        onBack = {},
    )
}

@Preview(
    name = "SystemPage: no active printer (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SystemPageNoPrinter() = PreviewBox(colorfulDark) {
    SystemPageContent(
        activePrinterName = "",
        versionName = SampleFixtures.systemPageVersion,
        onNavigate = {},
        onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme matrix (printer present — most representative state)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun SystemPageThemeColorfulDark() = PreviewBox(colorfulDark) {
    SystemPageContent(
        activePrinterName = SampleFixtures.systemPageActivePrinter,
        versionName = SampleFixtures.systemPageVersion,
        onNavigate = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun SystemPageThemeColorfulLight() = PreviewBox(colorfulLight) {
    SystemPageContent(
        activePrinterName = SampleFixtures.systemPageActivePrinter,
        versionName = SampleFixtures.systemPageVersion,
        onNavigate = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun SystemPageThemeSimpleDark() = PreviewBox(simpleDark) {
    SystemPageContent(
        activePrinterName = SampleFixtures.systemPageActivePrinter,
        versionName = SampleFixtures.systemPageVersion,
        onNavigate = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun SystemPageThemeSimpleLight() = PreviewBox(simpleLight) {
    SystemPageContent(
        activePrinterName = SampleFixtures.systemPageActivePrinter,
        versionName = SampleFixtures.systemPageVersion,
        onNavigate = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun SystemPageThemeHighContrastDark() = PreviewBox(highContrastDark) {
    SystemPageContent(
        activePrinterName = SampleFixtures.systemPageActivePrinter,
        versionName = SampleFixtures.systemPageVersion,
        onNavigate = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun SystemPageThemeHighContrastLight() = PreviewBox(highContrastLight) {
    SystemPageContent(
        activePrinterName = SampleFixtures.systemPageActivePrinter,
        versionName = SampleFixtures.systemPageVersion,
        onNavigate = {},
        onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check (proves no text clipping at L size)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SystemPage fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SystemPageFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) {
    SystemPageContent(
        activePrinterName = SampleFixtures.systemPageActivePrinter,
        versionName = SampleFixtures.systemPageVersion,
        onNavigate = {},
        onBack = {},
    )
}

@Preview(
    name = "SystemPage fs=L landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun SystemPageFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) {
    SystemPageContent(
        activePrinterName = SampleFixtures.systemPageActivePrinter,
        versionName = SampleFixtures.systemPageVersion,
        onNavigate = {},
        onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// RTL spot check (start/end alignment)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SystemPage RTL spot check (portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SystemPageRtlSpotCheck() = PreviewBox(colorfulDark) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        SystemPageContent(
            activePrinterName = SampleFixtures.systemPageActivePrinter,
            versionName = SampleFixtures.systemPageVersion,
            onNavigate = {},
            onBack = {},
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pseudolocale en-XA (i18n completeness sweep)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SystemPage pseudolocale en-XA",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun SystemPagePseudolocaleSpotCheck() = PreviewBox(colorfulDark) {
    SystemPageContent(
        activePrinterName = SampleFixtures.systemPageActivePrinter,
        versionName = SampleFixtures.systemPageVersion,
        onNavigate = {},
        onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Landscape spot panel (proves 40%-width Focus column)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SystemPage landscape (Focus 40% width)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun SystemPageLandscape() = PreviewBox(colorfulDark) {
    SystemPageContent(
        activePrinterName = SampleFixtures.systemPageActivePrinter,
        versionName = SampleFixtures.systemPageVersion,
        onNavigate = {},
        onBack = {},
    )
}
