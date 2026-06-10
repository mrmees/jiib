package works.mees.dinghy.preview

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.runtime.Composable
import works.mees.dinghy.ui.macros.MacroFieldMode
import works.mees.dinghy.ui.macros.MacroParam
import works.mees.dinghy.ui.macros.MacroScreensState
import works.mees.dinghy.ui.macros.MacroVm
import works.mees.dinghy.ui.macros.BookmarkedMacrosScreen

/**
 * Preview harness for the merged Macros screen (25-05 / D-09/D-10/D-12).
 *
 * ## Matrix shape (mirrors SpoolPreviews / PrintStatusPreviews convention)
 *  - [MacrosFieldModeMatrix] — the FULL FieldMode × theme matrix on the representative Colorful/dark seed,
 *    using a [PreviewParameterProvider] over the three [MacroFieldMode] states.
 *  - `MacrosTheme*` siblings — the FULL 6-theme matrix on ONE representative state (Launcher) to confirm
 *    every palette/polarity combo renders correctly.
 *  - [MacrosFsLargeOverflow] — fs = L shot ([fsLargeSeed]) to catch text overflow in the launcher list.
 *  - Pseudolocale spot-check — surfaces any unhardcoded literal strings not yet routed through stringResource.
 *
 * ## No live Moonraker (SC-1)
 * Every preview drives the STATELESS `BookmarkedMacrosScreen(state, fieldMode)` overload from pure
 * [macrosLauncherState] / [macrosParamEntryState] / [macrosManageState] fixtures — no [MacroHolder],
 * no [CommandDispatcher], no socket.
 *
 * ## FieldMode coverage (D-09/D-10/D-12)
 *  - [MacroFieldMode.Launcher]   — bookmarked ListRow list (D-10)
 *  - [MacroFieldMode.ParamEntry] — per-param Field-takeover with numeric + string params (D-12)
 *  - [MacroFieldMode.ManageMode] — system manage-visibility list (D-09)
 *
 * ## Preview font scale note
 * `@Preview(fontScale = …)` is a NO-OP in this app (DinghyTheme pins OS fontScale to 1f — THEME-02).
 * Use [fsLargeSeed]'s `fs` field for large-text previews — NOT the annotation.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures — pure immutable fake state, no Moonraker
// ─────────────────────────────────────────────────────────────────────────────

/** Ten representative bookmarked macros for the Launcher list (D-10). */
private val bookmarkedMacros: List<MacroVm> = listOf(
    MacroVm("START_PRINT", isBookmarked = true, isHidden = false, params = emptyList()),
    MacroVm("END_PRINT", isBookmarked = true, isHidden = false, params = emptyList()),
    MacroVm("LOAD_FILAMENT", isBookmarked = true, isHidden = false, params = listOf(
        MacroParam("MATERIAL", type = "string", default = "PLA"),
        MacroParam("TEMP", type = "double", default = "210"),
    )),
    MacroVm("UNLOAD_FILAMENT", isBookmarked = true, isHidden = false, params = emptyList()),
    MacroVm("BED_MESH_CALIBRATE", isBookmarked = true, isHidden = false, params = emptyList()),
    MacroVm("PROBE_CALIBRATE", isBookmarked = true, isHidden = false, params = emptyList()),
    MacroVm("PID_CALIBRATE", isBookmarked = true, isHidden = false, params = listOf(
        MacroParam("HEATER", type = "string", default = "extruder"),
        MacroParam("TARGET", type = "double", default = "220"),
    )),
    MacroVm("M600", isBookmarked = true, isHidden = false, params = emptyList()),
    MacroVm("CLEAN_NOZZLE", isBookmarked = true, isHidden = false, params = emptyList()),
    MacroVm("PRIME_LINE", isBookmarked = true, isHidden = false, params = emptyList()),
)

/** All-macros list for the Manage view (D-09): bookmarked + some hidden system macros. */
private val allMacros: List<MacroVm> = bookmarkedMacros + listOf(
    MacroVm("_PARK_TOOLHEAD", isBookmarked = false, isHidden = true, params = emptyList()),
    MacroVm("_BEEP_DONE", isBookmarked = false, isHidden = true, params = emptyList()),
    MacroVm("RESUME", isBookmarked = false, isHidden = false, params = emptyList()),
    MacroVm("CANCEL_PRINT", isBookmarked = false, isHidden = false, params = emptyList()),
)

/** Launcher state: ten bookmarked macros ready to tap. */
private val macrosLauncherState: MacroScreensState = MacroScreensState(
    macros = allMacros,
    visibleMacros = allMacros.filter { !it.isHidden },
    bookmarkedMacros = bookmarkedMacros,
    revealHidden = false,
    unavailable = false,
)

/** The macro used in ParamEntry fixture — LOAD_FILAMENT with a numeric + a string param (D-12). */
private val paramEntryMacro: MacroVm = bookmarkedMacros.first { it.name == "LOAD_FILAMENT" }

/** ParamEntry state: fields pre-populated with defaults, body known. */
private val macrosParamEntryState: MacroScreensState = macrosLauncherState

/** ManageMode state: all macros visible, none hidden (revealHidden = false). */
private val macrosManageState: MacroScreensState = macrosLauncherState

// ─────────────────────────────────────────────────────────────────────────────
// PreviewParameterProvider: three FieldModes on a single theme (Colorful/dark)
// ─────────────────────────────────────────────────────────────────────────────

private data class MacrosPreviewParams(
    val state: MacroScreensState,
    val fieldMode: MacroFieldMode,
)

private class MacrosFieldModeProvider : PreviewParameterProvider<MacrosPreviewParams> {
    override val values: Sequence<MacrosPreviewParams> = sequenceOf(
        MacrosPreviewParams(macrosLauncherState, MacroFieldMode.Launcher),
        MacrosPreviewParams(macrosParamEntryState, MacroFieldMode.ParamEntry(paramEntryMacro)),
        MacrosPreviewParams(macrosManageState, MacroFieldMode.ManageMode),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// The full FieldMode matrix on ONE representative theme (Colorful/dark)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Three-state [MacroFieldMode] matrix on Colorful/dark — the authoritative feature-completeness shot.
 * Each entry in [MacrosFieldModeProvider] renders both portrait + landscape via [Nexus7Previews].
 */
@Nexus7Previews
@Composable
private fun MacrosFieldModeMatrix(
    @PreviewParameter(MacrosFieldModeProvider::class) params: MacrosPreviewParams,
) {
    PreviewBox(colorfulDark) {
        BookmarkedMacrosScreen(state = params.state, fieldMode = params.fieldMode)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The full 6-theme matrix on the Launcher (the default / most-used surface)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun MacrosThemeColorfulDark() =
    PreviewBox(colorfulDark) { BookmarkedMacrosScreen(state = macrosLauncherState) }

@Nexus7Previews
@Composable
private fun MacrosThemeColorfulLight() =
    PreviewBox(colorfulLight) { BookmarkedMacrosScreen(state = macrosLauncherState) }

@Nexus7Previews
@Composable
private fun MacrosThemeSimpleDark() =
    PreviewBox(simpleDark) { BookmarkedMacrosScreen(state = macrosLauncherState) }

@Nexus7Previews
@Composable
private fun MacrosThemeSimpleLight() =
    PreviewBox(simpleLight) { BookmarkedMacrosScreen(state = macrosLauncherState) }

@Nexus7Previews
@Composable
private fun MacrosThemeHighContrastDark() =
    PreviewBox(highContrastDark) { BookmarkedMacrosScreen(state = macrosLauncherState) }

@Nexus7Previews
@Composable
private fun MacrosThemeHighContrastLight() =
    PreviewBox(highContrastLight) { BookmarkedMacrosScreen(state = macrosLauncherState) }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow shot (THEME-02) — catches text/row clipping at max in-app text size
// ─────────────────────────────────────────────────────────────────────────────

/**
 * fs = L overflow in the Launcher list — the densest surface that clipping can affect. fs is injected
 * via the seed's `fs` field; `@Preview(fontScale = …)` is a NO-OP here (see [PreviewBox] KDoc).
 */
@Nexus7Previews
@Composable
private fun MacrosFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { BookmarkedMacrosScreen(state = macrosLauncherState) }

// ─────────────────────────────────────────────────────────────────────────────
// Pseudolocale spot-check (SC-3c) — surfaces any un-stringResource'd literals
// ─────────────────────────────────────────────────────────────────────────────

@Preview(device = NEXUS7_PORTRAIT, locale = "en-XA", showBackground = true)
@Composable
private fun MacrosPseudolocaleSpotCheck() {
    PreviewBox(colorfulDark) {
        BookmarkedMacrosScreen(state = macrosLauncherState)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ParamEntry landscape spot-check (D-12 coverage at ≥1 landscape)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Landscape ParamEntry (D-12) — the param-entry field-takeover must be usable in landscape too
 * (NumpadPage + string TextField both must not overflow the single-column field region). Landscape
 * is also covered by [MacrosFieldModeMatrix] but an explicit named preview confirms the intent.
 */
@Preview(name = "ParamEntry landscape", device = NEXUS7, showBackground = true)
@Composable
private fun MacrosParamEntryLandscape() {
    PreviewBox(colorfulDark) {
        BookmarkedMacrosScreen(
            state = macrosParamEntryState,
            fieldMode = MacroFieldMode.ParamEntry(paramEntryMacro),
        )
    }
}
