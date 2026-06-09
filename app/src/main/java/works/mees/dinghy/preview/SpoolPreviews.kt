package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.LayoutDirection
import works.mees.dinghy.spool.SpoolmanStatus
import works.mees.dinghy.ui.spool.SpoolPickerState
import works.mees.dinghy.ui.spool.SpoolScreen

/**
 * Exemplar #3 previews (18-07 / D-01, D-02, D-05) — applies the [PrintStatusPreviews] ANCHOR template to
 * `SpoolScreen`, the **preview-safe image + dense-data** archetype (the third and final exemplar; the one
 * the convention doc `docs/ui_design/PREVIEW_AND_TOKENS.md` is written against).
 *
 * The interesting axis here is the SELECTION state — nothing selected (the landscape empty-Focus glyph) vs
 * a dense row selected (the full detail pane: split swatch, vendor, weight, temps, badges) — over a dense
 * [works.mees.dinghy.preview.SampleFixtures.spoolList] fixture, so the selection matrix is driven by a
 * [PreviewParameterProvider] ([SpoolSelectionProvider]) while THEME stays a wrapper concern ([PreviewBox]
 * seeds). A `@Preview` annotation cannot select the Colorful/Simple/High-Contrast palette MODE; the themes
 * MUST be explicit wrappers (per [PreviewBox]/[Nexus7Previews] KDoc).
 *
 * ## Matrix shape (RESEARCH Q8 — MINIMIZE proliferation)
 * Do NOT render every selection × every theme × fs. Instead:
 *  - [SpoolSelectionMatrix] — the FULL no-selection/selected matrix on ONE representative theme
 *    (Colorful/dark), selection = the `@PreviewParameter`.
 *  - `SpoolTheme*` siblings — the FULL 6-theme matrix on ONE representative state (a spool selected, the
 *    densest detail surface), six [PreviewBox] seed wrappers.
 *  - [SpoolFsLargeOverflow] — ONE `fs = L` overflow shot ([fsLargeSeed]). `@Preview(fontScale = …)` is a
 *    verified NO-OP here (OS fontScale pinned to 1f); fs is injected via the seed's `fs`, never the
 *    annotation — the #1 thing a copy-pasting phase gets wrong.
 *  - [SpoolRtlSpotCheck] — ONE RTL spot-check proving `start`/`end`-relative modifiers mirror.
 *
 * ## No live Moonraker (SC-1)
 * Every preview drives the STATELESS `SpoolScreen(state = …)` overload from a pure [SampleFixtures]
 * [SpoolPickerState] — no `SpoolHolder`, no `CommandDispatcher`, no `SpoolmanClient`, no socket. The
 * dense [SampleFixtures.spoolList] (materials / colors incl. a multi-color split / vendors / locations)
 * is the archetype's "dense data" proof.
 *
 * ## D-05 image strategy (preview-safe)
 * SpoolScreen has NO Coil `AsyncImage` — its color identity is rendered with token-colored `Box` swatches
 * and its glyphs are `painterResource`/Material-Symbol tokens (RESEARCH Q5 verified: the QR is a static
 * drawable, not Coil). The Spool feature's genuine preview-unsafe "embed a live View" surface is the
 * CameraX `PreviewView` in `ui/spool/scan/ScanSurface.kt`, which is branched on `LocalInspectionMode`
 * to a [works.mees.dinghy.preview.PreviewPlaceholderBox] (the same D-05 idiom as the 18-02 classic-View
 * hosts). That scan surface
 * is NOT previewed here — it resolves its state through a live Android permission/camera gate (un-
 * previewable, like the live-only modals the anchor omits); the D-05 branch is verified by grep + on-device.
 */
class SpoolSelectionProvider : PreviewParameterProvider<SpoolPickerState> {
    override val values: Sequence<SpoolPickerState> = sequenceOf(
        noSelection,           // landscape empty-Focus glyph; portrait stays Field-first
        firstSelected,         // a dense row selected → the full detail pane
        SampleFixtures.spoolWithFilterOpen, // Field-takeover: TYPE filter picker open (23-06 FieldMode)
    )
}

/** The dense list with NOTHING selected (the empty-Focus path) — a live active spool marks one row loaded. */
private val noSelection: SpoolPickerState = SpoolPickerState(
    spools = SampleFixtures.spoolList,
    selected = null,
    activeStatus = SpoolmanStatus(activeSpoolId = SampleFixtures.spoolList.first().id),
)

/** The dense list with the first row selected — the full detail pane (swatch/vendor/weight/temps/badges). */
private val firstSelected: SpoolPickerState = noSelection.copy(
    selected = SampleFixtures.spoolList.first(),
)

/**
 * The full SELECTION matrix on ONE representative theme (Colorful/dark) — one panel per [SpoolPickerState]
 * the provider yields, across both Nexus-7 orientations. Selection is the `@PreviewParameter`; theme is the
 * [PreviewBox] wrapper.
 */
@Nexus7Previews
@Composable
private fun SpoolSelectionMatrix(
    @PreviewParameter(SpoolSelectionProvider::class) state: SpoolPickerState,
) {
    PreviewBox(colorfulDark) {
        SpoolScreen(state = state)
    }
}

// ---------------------------------------------------------------------------------------------
// The full 6-theme matrix on ONE representative state (a spool selected) — six sibling PreviewBox seeds.
// (A @Preview annotation cannot select the palette MODE, so the themes MUST be explicit wrappers.)
// ---------------------------------------------------------------------------------------------

@Nexus7Previews
@Composable
private fun SpoolThemeColorfulDark() =
    PreviewBox(colorfulDark) { SpoolScreen(state = firstSelected) }

@Nexus7Previews
@Composable
private fun SpoolThemeColorfulLight() =
    PreviewBox(colorfulLight) { SpoolScreen(state = firstSelected) }

@Nexus7Previews
@Composable
private fun SpoolThemeSimpleDark() =
    PreviewBox(simpleDark) { SpoolScreen(state = firstSelected) }

@Nexus7Previews
@Composable
private fun SpoolThemeSimpleLight() =
    PreviewBox(simpleLight) { SpoolScreen(state = firstSelected) }

@Nexus7Previews
@Composable
private fun SpoolThemeHighContrastDark() =
    PreviewBox(highContrastDark) { SpoolScreen(state = firstSelected) }

@Nexus7Previews
@Composable
private fun SpoolThemeHighContrastLight() =
    PreviewBox(highContrastLight) { SpoolScreen(state = firstSelected) }

/**
 * The fs = L overflow shot ([fsLargeSeed]) — catches text/row clipping at the LARGEST in-app text size in
 * the dense list + detail pane. fs is injected through the seed's `fs` field; `@Preview(fontScale = …)` is
 * a verified NO-OP here. The selected state is the densest surface, so it's the representative overflow shot.
 */
@Nexus7Previews
@Composable
private fun SpoolFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { SpoolScreen(state = firstSelected) }

/**
 * The RTL spot-check — forces [LayoutDirection.Rtl] over the selected state to prove the screen uses
 * `start`/`end`-relative modifiers (not hardcoded left/right), so it mirrors correctly in RTL locales.
 */
@Nexus7Previews
@Composable
private fun SpoolRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            SpoolScreen(state = firstSelected)
        }
    }
}

/**
 * The pseudolocale (`en-XA`) spot-check (SC-3c) — the i18n-completeness companion to the RTL check.
 * A pseudolocalized run accordion-pads + brackets the APP vocabulary, so any plain-English text that
 * shows through unpseudolocalized is a still-hardcoded literal (not yet routed through stringResource).
 * Locale comes from the annotation alone — no CompositionLocalProvider; this is its OWN dedicated single
 * `@Preview`, NOT part of `@Nexus7Previews`/`@DeviceAndLocalePreviews`.
 */
@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)
@Composable
private fun SpoolPseudolocaleSpotCheck() {
    PreviewBox(colorfulDark) {
        SpoolScreen(state = firstSelected)
    }
}

// ---------------------------------------------------------------------------------------------
// 23-06 new states: Field-takeover filter picker + printing-state FloatingEStop exercise
// ---------------------------------------------------------------------------------------------

/**
 * The Field-takeover filter picker state (23-06 [FieldMode.FilterPicker]) — the Field swaps to the TYPE
 * category option list in place. Drives the stateless [SpoolScreen] with [SampleFixtures.spoolWithFilterOpen]
 * so the preview matrix covers the rebuilt picker path without full-screen overlay.
 */
@Nexus7Previews
@Composable
private fun SpoolFilterPickerOpen() =
    PreviewBox(colorfulDark) { SpoolScreen(state = SampleFixtures.spoolWithFilterOpen) }

/**
 * The printing state — [FloatingEStop] visible in the Focus TopStart corner. Uses `isPrinting = true` on the
 * stateless seam to exercise the overlay at design-time without a live [container.printerState] StateFlow.
 * This is the only preview that exercises the FloatingEStop; the live overload sources it from the
 * real printer state at runtime.
 */
@Nexus7Previews
@Composable
private fun SpoolPrintingState() =
    PreviewBox(colorfulDark) { SpoolScreen(state = firstSelected, isPrinting = true) }
