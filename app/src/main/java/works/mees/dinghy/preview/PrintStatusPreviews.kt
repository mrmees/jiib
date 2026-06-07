package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.LayoutDirection
import works.mees.dinghy.ui.printstatus.PrintStatusMode
import works.mees.dinghy.ui.printstatus.PrintStatusScreen
import works.mees.dinghy.ui.printstatus.TerminalKind

/**
 * The ANCHOR exemplar previews (18-05 / D-01, D-02, D-05) — the convention Phases 19-21 copy verbatim.
 *
 * `PrintStatusScreen` is the multi-state archetype: its 4/6 [PrintStatusMode] states are the interesting
 * axis, so the STATE matrix is driven by a [PreviewParameterProvider] ([PrintStatusModeProvider]) while
 * THEME is a wrapper concern ([PreviewBox] seeds) — a Compose `@Preview` allows at most one
 * `@PreviewParameter`, and (per [PreviewBox]/[Nexus7Previews] KDoc) an annotation can select
 * device/uiMode/locale but NEVER the Colorful/Simple/High-Contrast palette MODE.
 *
 * ## Matrix shape (RESEARCH Q8 — MINIMIZE proliferation)
 * Do NOT render every state × every theme × fs (that's 28+ noisy/slow panels per screen). Instead:
 *  - [PrintStatusStateMatrix] — the FULL state matrix on ONE representative theme (mode = the parameter).
 *  - The six `PrintStatusTheme*` previews — the FULL 6-theme matrix on ONE representative state
 *    (Printing), each a sibling [PreviewBox] seed wrapper.
 *  - [PrintStatusFsLargeOverflow] — ONE `fs = L` overflow shot ([fsLargeSeed]). `@Preview(fontScale=)` is
 *    a NO-OP in this app (OS fontScale pinned to 1f) — fs is injected via the seed's `fs`, never the
 *    annotation. This is the #1 thing a copy-pasting phase gets wrong; the seed is the ONLY way.
 *  - [PrintStatusRtlSpotCheck] — ONE RTL spot-check proving `start`/`end`-relative modifiers mirror.
 *
 * ## No live Moonraker (SC-1)
 * Every preview wraps `PrintStatusScreen(state = SampleFixtures.forMode(mode))` — the stateless overload
 * that renders the four-state scaffold from a pure [SampleFixtures] fixture, no AppContainer, no socket.
 * The embedded GraphView region + Coil thumbnails preview as labeled stand-ins via the D-05/D-02
 * `LocalInspectionMode` branches (covered by 18-02's host branch + this phase's Coil branch).
 */
class PrintStatusModeProvider : PreviewParameterProvider<PrintStatusMode> {
    override val values: Sequence<PrintStatusMode> = sequenceOf(
        PrintStatusMode.Standby,
        PrintStatusMode.Printing,
        PrintStatusMode.Paused,
        PrintStatusMode.Terminal(TerminalKind.Complete),
        PrintStatusMode.Terminal(TerminalKind.Cancelled),
        PrintStatusMode.Terminal(TerminalKind.Error),
    )
}

/**
 * The full STATE matrix on ONE representative theme (Colorful/dark) — one panel per [PrintStatusMode] the
 * provider yields, across both Nexus-7 orientations. Mode is the `@PreviewParameter`; theme is the
 * [PreviewBox] wrapper. Terminal(Error) gets a small error-line projection so its error block renders.
 */
@Nexus7Previews
@Composable
private fun PrintStatusStateMatrix(
    @PreviewParameter(PrintStatusModeProvider::class) mode: PrintStatusMode,
) {
    val errorLines = if (mode is PrintStatusMode.Terminal && mode.kind == TerminalKind.Error) {
        listOf("MCU 'mcu' shutdown: Timer too close", "Once the underlying issue is corrected,", "use the RESTART command to reload.")
    } else {
        emptyList()
    }
    PreviewBox(colorfulDark) {
        PrintStatusScreen(
            state = SampleFixtures.forMode(mode),
            errorLines = errorLines,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The full 6-theme matrix on ONE representative state (Printing) — six sibling PreviewBox seeds.
// (A @Preview annotation cannot select the palette MODE, so the themes MUST be explicit wrappers.)
// ---------------------------------------------------------------------------------------------

@Nexus7Previews
@Composable
private fun PrintStatusThemeColorfulDark() =
    PreviewBox(colorfulDark) { PrintStatusScreen(state = SampleFixtures.forMode(PrintStatusMode.Printing)) }

@Nexus7Previews
@Composable
private fun PrintStatusThemeColorfulLight() =
    PreviewBox(colorfulLight) { PrintStatusScreen(state = SampleFixtures.forMode(PrintStatusMode.Printing)) }

@Nexus7Previews
@Composable
private fun PrintStatusThemeSimpleDark() =
    PreviewBox(simpleDark) { PrintStatusScreen(state = SampleFixtures.forMode(PrintStatusMode.Printing)) }

@Nexus7Previews
@Composable
private fun PrintStatusThemeSimpleLight() =
    PreviewBox(simpleLight) { PrintStatusScreen(state = SampleFixtures.forMode(PrintStatusMode.Printing)) }

@Nexus7Previews
@Composable
private fun PrintStatusThemeHighContrastDark() =
    PreviewBox(highContrastDark) { PrintStatusScreen(state = SampleFixtures.forMode(PrintStatusMode.Printing)) }

@Nexus7Previews
@Composable
private fun PrintStatusThemeHighContrastLight() =
    PreviewBox(highContrastLight) { PrintStatusScreen(state = SampleFixtures.forMode(PrintStatusMode.Printing)) }

/**
 * The fs = L overflow shot ([fsLargeSeed]) — catches text clipping at the LARGEST in-app text size.
 * fs is injected through the seed's `fs` field; `@Preview(fontScale = …)` is a verified NO-OP here.
 * Standby is the densest glance/launcher surface, so it's the representative state for the overflow check.
 */
@Nexus7Previews
@Composable
private fun PrintStatusFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { PrintStatusScreen(state = SampleFixtures.forMode(PrintStatusMode.Standby)) }

/**
 * The RTL spot-check — forces [LayoutDirection.Rtl] over the Printing state to prove the screen uses
 * `start`/`end`-relative modifiers (not hardcoded left/right), so it mirrors correctly in RTL locales.
 */
@Nexus7Previews
@Composable
private fun PrintStatusRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            PrintStatusScreen(state = SampleFixtures.forMode(PrintStatusMode.Printing))
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
private fun PrintStatusPseudolocaleSpotCheck() {
    PreviewBox(colorfulDark) {
        PrintStatusScreen(state = SampleFixtures.forMode(PrintStatusMode.Printing))
    }
}
