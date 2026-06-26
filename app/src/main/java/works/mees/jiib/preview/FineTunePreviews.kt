package works.mees.jiib.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.LayoutDirection
import works.mees.jiib.ui.finetune.FineTuneScreen
import works.mees.jiib.ui.finetune.FineTuneVm

/**
 * Fine-Tune flat-list screen preview matrix (26-02 / D-23 preview-first convention).
 *
 * The redesigned screen is a SINGLE [FineTuneScreen] composable (replaces Hub + 3 group screens).
 * The interesting axes are:
 *  - [FineTuneVm] capability state: all-present / no-FW-retraction (hides FW-ret rows, D-08) / busy
 *  - Theme: 6 combos × dark/light × palette mode
 *  - fs=L overflow check
 *  - Landscape 5U phone budget
 *
 * Matrix shape (MINIMIZE proliferation — PREVIEW_AND_TOKENS.md):
 *  - [FineTuneVariantMatrix] — full present/absent/busy × Colorful/dark (3 params, 1 theme)
 *  - [FineTuneTheme*] — 6-theme matrix × single state (all-present)
 *  - [FineTuneFsLargeOverflow] — fs=L overflow check on all-present
 *  - [FineTuneLandscape] — 5U phone-landscape check (800×480dp)
 *  - [FineTuneIsPrinting] — printing=true to confirm FloatingEStop renders
 *  - [FineTuneRtlSpotCheck] — RTL layout direction check
 *  - [FineTunePseudolocaleSpotCheck] — pseudolocale en-XA
 */
class FineTuneVariantProvider : PreviewParameterProvider<FineTuneVm> {
    override val values: Sequence<FineTuneVm> = sequenceOf(
        SampleFixtures.fineTuneAllPresent,      // every tunable present (full list)
        SampleFixtures.fineTuneNoFwRetraction,  // FW-retraction ABSENT → rows HIDDEN (D-08 proof)
        SampleFixtures.fineTuneBusy,            // groupBusy=true → AdjusterPanel disabled
    )
}

/**
 * Full present/absent/busy variant matrix on ONE representative theme (Colorful/dark).
 * Proves: FW-retraction rows hide when absent, AdjusterPanel disabled when busy.
 */
@Nexus7Previews
@Composable
private fun FineTuneVariantMatrix(
    @PreviewParameter(FineTuneVariantProvider::class) vm: FineTuneVm,
) {
    PreviewBox(colorfulDark) {
        FineTuneScreen(vm = vm)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme matrix on ONE representative state (all-present)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun FineTuneThemeColorfulDark() =
    PreviewBox(colorfulDark) { FineTuneScreen(vm = SampleFixtures.fineTuneAllPresent) }

@Nexus7Previews
@Composable
private fun FineTuneThemeColorfulLight() =
    PreviewBox(colorfulLight) { FineTuneScreen(vm = SampleFixtures.fineTuneAllPresent) }

@Nexus7Previews
@Composable
private fun FineTuneThemeSimpleDark() =
    PreviewBox(simpleDark) { FineTuneScreen(vm = SampleFixtures.fineTuneAllPresent) }

@Nexus7Previews
@Composable
private fun FineTuneThemeSimpleLight() =
    PreviewBox(simpleLight) { FineTuneScreen(vm = SampleFixtures.fineTuneAllPresent) }

@Nexus7Previews
@Composable
private fun FineTuneThemeHighContrastDark() =
    PreviewBox(highContrastDark) { FineTuneScreen(vm = SampleFixtures.fineTuneAllPresent) }

@Nexus7Previews
@Composable
private fun FineTuneThemeHighContrastLight() =
    PreviewBox(highContrastLight) { FineTuneScreen(vm = SampleFixtures.fineTuneAllPresent) }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check — catches text/tile clipping at the LARGEST in-app text size
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun FineTuneFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { FineTuneScreen(vm = SampleFixtures.fineTuneAllPresent) }

// ─────────────────────────────────────────────────────────────────────────────
// Landscape — 5U phone-landscape Focus budget (800×480dp)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "FineTune landscape 5U",
    widthDp = 800,
    heightDp = 480,
    showBackground = true,
)
@Composable
private fun FineTuneLandscape() =
    PreviewBox(colorfulDark) { FineTuneScreen(vm = SampleFixtures.fineTuneAllPresent) }

// ─────────────────────────────────────────────────────────────────────────────
// Printing state — confirms FloatingEStop renders (isPrinting = true)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun FineTuneIsPrinting() =
    PreviewBox(colorfulDark) {
        FineTuneScreen(
            vm = SampleFixtures.fineTuneAllPresent,
            isPrinting = true,
        )
    }

// ─────────────────────────────────────────────────────────────────────────────
// RTL — confirms start/end-relative modifiers mirror correctly
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun FineTuneRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FineTuneScreen(vm = SampleFixtures.fineTuneAllPresent)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pseudolocale — en-XA reveals any hardcoded plain-English text
// ─────────────────────────────────────────────────────────────────────────────

@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)
@Composable
private fun FineTunePseudolocaleSpotCheck() {
    PreviewBox(colorfulDark) {
        FineTuneScreen(vm = SampleFixtures.fineTuneAllPresent)
    }
}
