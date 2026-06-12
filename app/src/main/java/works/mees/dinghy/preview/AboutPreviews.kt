package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import works.mees.dinghy.ui.screen.AboutContent

/**
 * @Preview matrix for AboutScreen (15.2-04, D-05) after 28-07 dense restyle (D-11).
 *
 * Targets the STATELESS [AboutContent] seam — no AppContainer, no Moonraker.
 *
 * ## Axes exercised
 *  - Dev-enable ON vs OFF — exercises the DevEnableRow pill + sub-label branch
 *  - 6 theme combos on dev-enable OFF (the common-case landing state)
 *  - fs = L overflow check — verifies wordmark + jib-note don't clip at large text
 *  - RTL spotcheck — confirms start/end-relative modifiers mirror correctly
 *  - Pseudolocale en-XA — i18n completeness sweep
 */

// ─────────────────────────────────────────────────────────────────────────────
// Axis matrix: dev-enable ON vs OFF
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "About: dev-enable OFF (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun AboutDevOffPortrait() = PreviewBox(colorfulDark) {
    AboutContent(devEnabled = false, onDevToggle = {}, onBack = {})
}

@Preview(
    name = "About: dev-enable OFF (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun AboutDevOffLandscape() = PreviewBox(colorfulDark) {
    AboutContent(devEnabled = false, onDevToggle = {}, onBack = {})
}

@Preview(
    name = "About: dev-enable ON (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun AboutDevOnPortrait() = PreviewBox(colorfulDark) {
    AboutContent(devEnabled = true, onDevToggle = {}, onBack = {})
}

@Preview(
    name = "About: dev-enable ON (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun AboutDevOnLandscape() = PreviewBox(colorfulDark) {
    AboutContent(devEnabled = true, onDevToggle = {}, onBack = {})
}

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme matrix on dev-enable OFF (common landing state — wordmark + jib-note)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun AboutThemeColorfulDark() = PreviewBox(colorfulDark) {
    AboutContent(devEnabled = false, onDevToggle = {}, onBack = {})
}

@Nexus7Previews
@Composable
private fun AboutThemeColorfulLight() = PreviewBox(colorfulLight) {
    AboutContent(devEnabled = false, onDevToggle = {}, onBack = {})
}

@Nexus7Previews
@Composable
private fun AboutThemeSimpleDark() = PreviewBox(simpleDark) {
    AboutContent(devEnabled = false, onDevToggle = {}, onBack = {})
}

@Nexus7Previews
@Composable
private fun AboutThemeSimpleLight() = PreviewBox(simpleLight) {
    AboutContent(devEnabled = false, onDevToggle = {}, onBack = {})
}

@Nexus7Previews
@Composable
private fun AboutThemeHighContrastDark() = PreviewBox(highContrastDark) {
    AboutContent(devEnabled = false, onDevToggle = {}, onBack = {})
}

@Nexus7Previews
@Composable
private fun AboutThemeHighContrastLight() = PreviewBox(highContrastLight) {
    AboutContent(devEnabled = false, onDevToggle = {}, onBack = {})
}

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check — wordmark ratio + jib-note text at largest size
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "About fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun AboutFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) {
    AboutContent(devEnabled = false, onDevToggle = {}, onBack = {})
}

@Preview(
    name = "About fs=L landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun AboutFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) {
    AboutContent(devEnabled = false, onDevToggle = {}, onBack = {})
}

// ─────────────────────────────────────────────────────────────────────────────
// RTL spotcheck — confirms start/end-relative modifiers mirror correctly
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun AboutRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            AboutContent(devEnabled = false, onDevToggle = {}, onBack = {})
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pseudolocale en-XA — i18n completeness sweep
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "About pseudolocale en-XA",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun AboutPseudolocaleSpotCheck() = PreviewBox(colorfulDark) {
    AboutContent(devEnabled = false, onDevToggle = {}, onBack = {})
}
