package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.LayoutDirection
import works.mees.dinghy.ui.finetune.ExtrusionScreen
import works.mees.dinghy.ui.finetune.FineTuneHubScreen
import works.mees.dinghy.ui.finetune.FineTuneVm
import works.mees.dinghy.ui.finetune.MotionScreen

/**
 * Exemplar #2 previews (18-06 / D-01, D-02) — applies the [PrintStatusPreviews] ANCHOR template to the
 * FineTune surfaces. FineTune is the **present/absent capability-gating + busy-lock** archetype that
 * directly de-risks Phase 19 (Output Controls, also capability-gated).
 *
 * The interesting axis here is the [FineTuneVm] CAPABILITY state — present / absent (FW-retraction →
 * HIDDEN, not disabled) / busy (whole-group lock) — so the variant matrix is driven by a
 * [PreviewParameterProvider] ([FineTuneVariantProvider]) while THEME stays a wrapper concern
 * ([PreviewBox] seeds). A `@Preview` annotation cannot select the Colorful/Simple/High-Contrast palette
 * MODE; the themes MUST be explicit wrappers (per [PreviewBox]/[DinghyPreviews] KDoc).
 *
 * ## Matrix shape (RESEARCH Q8 — MINIMIZE proliferation)
 * Do NOT render every variant × every theme × fs. Instead:
 *  - [ExtrusionVariantMatrix] — the FULL present/absent/busy matrix on ONE representative theme
 *    (Colorful/dark), rendered on **Extrusion** (the screen that owns the `hasFwRetraction` HIDDEN gate
 *    and the most tiles), variant = the `@PreviewParameter`.
 *  - `FineTuneTheme*` siblings — the FULL 6-theme matrix on ONE representative state (the Hub, the always-
 *    present entry surface), six [PreviewBox] seed wrappers.
 *  - [MotionAllPresent] — the Motion group's all-present tiles on one theme (the per-tile sibling surface).
 *  - [FineTuneFsLargeOverflow] — ONE `fs = L` overflow shot ([fsLargeSeed]). `@Preview(fontScale=)` is a
 *    verified NO-OP here (OS fontScale pinned to 1f); fs is injected via the seed's `fs`, never the
 *    annotation — the #1 thing a copy-pasting phase gets wrong.
 *  - [FineTuneRtlSpotCheck] — ONE RTL spot-check proving `start`/`end`-relative modifiers mirror.
 *
 * ## No live Moonraker (SC-1)
 * Every preview drives the STATELESS screen overloads (`MotionScreen(vm=…)`, `ExtrusionScreen(vm=…)`, and
 * the inherently-stateless `FineTuneHubScreen`) from pure [SampleFixtures] FineTune fixtures — no
 * [works.mees.dinghy.di.AppContainer], no dispatcher, no socket. The build-blind FW-Retraction SCREEN is
 * deliberately NOT previewed (Phase-17 state); the absent-capability proof is the FW-Retraction ENTRY tile
 * HIDING under `fineTuneNoFwRetraction`.
 */
class FineTuneVariantProvider : PreviewParameterProvider<FineTuneVm> {
    override val values: Sequence<FineTuneVm> = sequenceOf(
        SampleFixtures.fineTuneAllPresent,      // every tunable present (full panel)
        SampleFixtures.fineTuneNoFwRetraction,  // FW-retraction ABSENT → entry tile HIDDEN (not disabled)
        SampleFixtures.fineTuneBusy,            // groupBusy=true → whole-group lock (every tile dimmed)
    )
}

/**
 * The full present/absent/busy variant matrix on ONE representative theme (Colorful/dark), rendered on the
 * Extrusion group — the screen that owns the `hasFwRetraction` HIDDEN gate. The absent variant proves the
 * FW-Retraction entry tile is HIDDEN (not disabled); the busy variant proves the whole-group lock.
 */
@Nexus7Previews
@Composable
private fun ExtrusionVariantMatrix(
    @PreviewParameter(FineTuneVariantProvider::class) vm: FineTuneVm,
) {
    PreviewBox(colorfulDark) {
        ExtrusionScreen(vm = vm)
    }
}

/** The all-present Motion group on one theme — the per-tile sibling surface (Speed/Vel/Accel/Cruise/SCV). */
@Nexus7Previews
@Composable
private fun MotionAllPresent() =
    PreviewBox(colorfulDark) { MotionScreen(vm = SampleFixtures.fineTuneAllPresent) }

// ---------------------------------------------------------------------------------------------
// The full 6-theme matrix on ONE representative state (the Hub) — six sibling PreviewBox seeds.
// (A @Preview annotation cannot select the palette MODE, so the themes MUST be explicit wrappers.)
// ---------------------------------------------------------------------------------------------

@Nexus7Previews
@Composable
private fun FineTuneThemeColorfulDark() =
    PreviewBox(colorfulDark) { FineTuneHubScreen(onNavigate = {}, onBack = {}) }

@Nexus7Previews
@Composable
private fun FineTuneThemeColorfulLight() =
    PreviewBox(colorfulLight) { FineTuneHubScreen(onNavigate = {}, onBack = {}) }

@Nexus7Previews
@Composable
private fun FineTuneThemeSimpleDark() =
    PreviewBox(simpleDark) { FineTuneHubScreen(onNavigate = {}, onBack = {}) }

@Nexus7Previews
@Composable
private fun FineTuneThemeSimpleLight() =
    PreviewBox(simpleLight) { FineTuneHubScreen(onNavigate = {}, onBack = {}) }

@Nexus7Previews
@Composable
private fun FineTuneThemeHighContrastDark() =
    PreviewBox(highContrastDark) { FineTuneHubScreen(onNavigate = {}, onBack = {}) }

@Nexus7Previews
@Composable
private fun FineTuneThemeHighContrastLight() =
    PreviewBox(highContrastLight) { FineTuneHubScreen(onNavigate = {}, onBack = {}) }

/**
 * The fs = L overflow shot ([fsLargeSeed]) — catches text/tile clipping at the LARGEST in-app text size.
 * fs is injected through the seed's `fs` field; `@Preview(fontScale = …)` is a verified NO-OP here. The
 * all-present Motion group is the densest tile column, so it's the representative overflow surface.
 */
@Nexus7Previews
@Composable
private fun FineTuneFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { MotionScreen(vm = SampleFixtures.fineTuneAllPresent) }

/**
 * The RTL spot-check — forces [LayoutDirection.Rtl] over the all-present Extrusion group to prove the
 * screen uses `start`/`end`-relative modifiers (not hardcoded left/right), so it mirrors in RTL locales.
 */
@Nexus7Previews
@Composable
private fun FineTuneRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ExtrusionScreen(vm = SampleFixtures.fineTuneAllPresent)
        }
    }
}
