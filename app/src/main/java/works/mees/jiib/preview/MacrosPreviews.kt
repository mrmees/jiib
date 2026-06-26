package works.mees.jiib.preview

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.runtime.Composable
import works.mees.jiib.ui.macros.MacroFieldMode
import works.mees.jiib.ui.macros.MacroParam
import works.mees.jiib.ui.macros.MacroScreensState
import works.mees.jiib.ui.macros.MacroVm
import works.mees.jiib.ui.macros.BookmarkedMacrosScreen

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
 * Every preview drives the STATELESS `BookmarkedMacrosScreen(state, fieldMode, selectedName)` overload
 * from pure [macrosLauncherState] / [macrosManageState] fixtures (plus a [selectedName] of
 * [paramEntryMacro] to fill the Focus) — no [MacroHolder], no [CommandDispatcher], no socket.
 *
 * ## FieldMode coverage (D-09/D-10/D-12)
 *  - [MacroFieldMode.Launcher]   — bookmarked ListRow list (D-10); a selected macro fills the Focus
 *    with its description + numeric/string param fields (D-12).
 *  - [MacroFieldMode.ManageMode] — system manage-visibility list (D-09)
 *
 * ## Preview font scale note
 * `@Preview(fontScale = …)` is a NO-OP in this app (JiibTheme pins OS fontScale to 1f — THEME-02).
 * Use [fsLargeSeed]'s `fs` field for large-text previews — NOT the annotation.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures — pure immutable fake state, no Moonraker
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The macro shown in the Focus when selected — LOAD_FILAMENT with a numeric + a string param and a
 * description, exercising the new `MacroVm.description` field plus numeric/string param fields (D-12).
 */
private val paramEntryMacro: MacroVm = MacroVm(
    name = "LOAD_FILAMENT",
    isBookmarked = true,
    isHidden = false,
    params = listOf(
        MacroParam("TEMP", "int", "210", required = false),
        MacroParam("MATERIAL", null, "PLA", required = false),
    ),
    description = "Heat the nozzle and load filament.",
)

/** Ten representative bookmarked macros for the Launcher list (D-10). */
private val bookmarkedMacros: List<MacroVm> = listOf(
    MacroVm("START_PRINT", isBookmarked = true, isHidden = false, params = emptyList()),
    MacroVm("END_PRINT", isBookmarked = true, isHidden = false, params = emptyList()),
    paramEntryMacro,
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

/** ManageMode state: all macros visible, none hidden (revealHidden = false). */
private val macrosManageState: MacroScreensState = macrosLauncherState

// ─────────────────────────────────────────────────────────────────────────────
// PreviewParameterProvider: three FieldModes on a single theme (Colorful/dark)
// ─────────────────────────────────────────────────────────────────────────────

private data class MacrosPreviewParams(
    val state: MacroScreensState,
    val fieldMode: MacroFieldMode,
    val selectedName: String? = null,
)

private class MacrosFieldModeProvider : PreviewParameterProvider<MacrosPreviewParams> {
    override val values: Sequence<MacrosPreviewParams> = sequenceOf(
        MacrosPreviewParams(macrosLauncherState, MacroFieldMode.Launcher),
        MacrosPreviewParams(macrosLauncherState, MacroFieldMode.Launcher, selectedName = paramEntryMacro.name),
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
        BookmarkedMacrosScreen(
            state = params.state,
            fieldMode = params.fieldMode,
            selectedName = params.selectedName,
        )
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
// Selected-macro Focus landscape spot-check (D-12 coverage at ≥1 landscape)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Landscape selected-macro Focus (D-12/D-07) — the selected macro's param fields must be usable in
 * landscape too (numeric IME + string TextField both must not overflow the Focus region). Landscape
 * is also covered by [MacrosFieldModeMatrix] but an explicit named preview confirms the intent.
 */
@Preview(name = "Selected macro landscape", device = NEXUS7, showBackground = true)
@Composable
private fun MacrosSelectedMacroLandscape() {
    PreviewBox(colorfulDark) {
        BookmarkedMacrosScreen(
            state = macrosLauncherState,
            fieldMode = MacroFieldMode.Launcher,
            selectedName = paramEntryMacro.name,
        )
    }
}
