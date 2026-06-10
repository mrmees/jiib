package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.dinghy.state.FileBrowserRow
import works.mees.dinghy.state.FileBrowserRowKind
import works.mees.dinghy.state.FilePreviewMetadata
import works.mees.dinghy.ui.files.FilesScreen
import works.mees.dinghy.ui.files.FilesScreenState

/**
 * @Preview matrix for [FilesScreen] (25-03 / D-19 / D-20).
 *
 * Mirrors the [SpoolPreviews] structure: a representative selection-state matrix on ONE theme +
 * the full 6-theme matrix on one representative state + fs=L overflow + landscape spot-check.
 *
 * ## No live Moonraker (SC-1)
 * Every preview drives the STATELESS [FilesScreen(state = …)] overload from fake [FilesScreenState]
 * fixtures. No [FileBrowserHolder], no [CommandDispatcher], no socket. The thumbnail [AsyncImage]
 * is preview-unsafe — the stateless overload branches on [LocalInspectionMode.current] and renders
 * a [PreviewPlaceholderBox] in its place (D-05 idiom; wired in [FilesScreen.kt]).
 *
 * ## Matrix shape (matches docs/ui_design/PREVIEW_AND_TOKENS.md §"Minimize proliferation")
 *  - [FilesSelectionMatrix] — SELECTION axis (nothing selected / file selected + preview loaded /
 *    printing state) on ONE representative theme (Colorful/dark). [Nexus7Previews] = portrait +
 *    landscape.
 *  - [FilesTheme*] siblings — the full 6-theme combos on the dense "file selected" state.
 *  - [FilesFsLargeOverflow] — one fs=L shot (via [fsLargeSeed], NOT @Preview fontScale which is a
 *    NO-OP here — see [PreviewBox] KDoc).
 *  - [FilesLandscapeSpotCheck] — dedicated landscape @Preview with explicit widthDp/heightDp.
 *
 * ## Fake file fixtures
 * Files are plain [FileBrowserRow] objects (kind=File, filled fields). The previews demonstrate
 * the flat-list, age-sorted output: several rows, one selected.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Fake fixtures
// ─────────────────────────────────────────────────────────────────────────────

private val fakeFileRows: List<FileBrowserRow> = listOf(
    FileBrowserRow(
        kind = FileBrowserRowKind.File,
        name = "miata-airbox-bracket.gcode",
        stableId = "file:miata-airbox-bracket.gcode",
        relativeFilename = "miata-airbox-bracket.gcode",
        sizeBytes = 4_892_000L,
        modifiedEpochSeconds = 1_749_600_000.0,
    ),
    FileBrowserRow(
        kind = FileBrowserRowKind.File,
        name = "benchy-v2-final.gcode",
        stableId = "file:benchy-v2-final.gcode",
        relativeFilename = "benchy-v2-final.gcode",
        sizeBytes = 2_340_000L,
        modifiedEpochSeconds = 1_749_500_000.0,
    ),
    FileBrowserRow(
        kind = FileBrowserRowKind.File,
        name = "calibration-tower-25mm.gcode",
        stableId = "file:calibration-tower-25mm.gcode",
        relativeFilename = "calibration-tower-25mm.gcode",
        sizeBytes = 1_100_000L,
        modifiedEpochSeconds = 1_749_400_000.0,
    ),
    FileBrowserRow(
        kind = FileBrowserRowKind.File,
        name = "voron-panel-left-lower.gcode",
        stableId = "file:voron-panel-left-lower.gcode",
        relativeFilename = "voron-panel-left-lower.gcode",
        sizeBytes = 12_500_000L,
        modifiedEpochSeconds = 1_749_300_000.0,
    ),
    FileBrowserRow(
        kind = FileBrowserRowKind.File,
        name = "ender5plus-spool-holder.gcode",
        stableId = "file:ender5plus-spool-holder.gcode",
        relativeFilename = "ender5plus-spool-holder.gcode",
        sizeBytes = 890_000L,
        modifiedEpochSeconds = 1_749_200_000.0,
    ),
)

/** Preview metadata for the selected file (dense future-print detail card). */
private val fakeSelectedPreview = FilePreviewMetadata(
    filename = "benchy-v2-final.gcode",
    sizeBytes = 2_340_000L,
    modifiedEpochSeconds = 1_749_500_000.0,
    estimatedTime = 3_720.0,          // 1h 2m
    filamentTotal = 8_400.0,           // mm
    filamentWeightTotal = 24.6,        // g
    layerCount = 215,
    objectHeight = 48.0,               // mm
    largestThumbRelPath = null,        // no real thumbnail in preview
    filamentType = listOf("PLA"),
    filamentColors = listOf("#FF2020"),
)

/** The selected row (second entry = benchy). */
private val fakeSelected = fakeFileRows[1]

/** Empty list — nothing selected (empty-Focus glyph path). */
val filesNothingSelected: FilesScreenState = FilesScreenState(
    fileRows = fakeFileRows,
    selectedFile = null,
    selectedPreview = null,
    sortAscending = false,
    httpBase = "",
    isPrinting = false,
)

/** File selected with preview metadata loaded — the dense detail card. */
val filesFileSelected: FilesScreenState = FilesScreenState(
    fileRows = fakeFileRows,
    selectedFile = fakeSelected,
    selectedPreview = fakeSelectedPreview,
    sortAscending = false,
    httpBase = "",
    isPrinting = false,
)

/** File selected + isPrinting = true — FloatingEStop visible in TopStart of Focus. */
val filesFileSelectedPrinting: FilesScreenState = filesFileSelected.copy(isPrinting = true)

// ─────────────────────────────────────────────────────────────────────────────
// Selection-axis matrix (Colorful/dark — portrait + landscape via @Nexus7Previews)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Nothing selected — the empty-Focus glyph path (D-05: no folder nav; just an empty list state).
 */
@Nexus7Previews
@Composable
private fun FilesNothingSelected() =
    PreviewBox(colorfulDark) { FilesScreen(state = filesNothingSelected) }

/**
 * File selected with full preview metadata — the dense future-print DetailCard.
 */
@Nexus7Previews
@Composable
private fun FilesFileSelected() =
    PreviewBox(colorfulDark) { FilesScreen(state = filesFileSelected) }

/**
 * File selected + printing — [FloatingEStop] visible in Focus TopStart corner.
 */
@Nexus7Previews
@Composable
private fun FilesFileSelectedPrinting() =
    PreviewBox(colorfulDark) { FilesScreen(state = filesFileSelectedPrinting) }

// ─────────────────────────────────────────────────────────────────────────────
// Full 6-theme matrix (one representative state — fileSelected)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun FilesThemeColorfulDark() =
    PreviewBox(colorfulDark) { FilesScreen(state = filesFileSelected) }

@Nexus7Previews
@Composable
private fun FilesThemeColorfulLight() =
    PreviewBox(colorfulLight) { FilesScreen(state = filesFileSelected) }

@Nexus7Previews
@Composable
private fun FilesThemeSimpleDark() =
    PreviewBox(simpleDark) { FilesScreen(state = filesFileSelected) }

@Nexus7Previews
@Composable
private fun FilesThemeSimpleLight() =
    PreviewBox(simpleLight) { FilesScreen(state = filesFileSelected) }

@Nexus7Previews
@Composable
private fun FilesThemeHighContrastDark() =
    PreviewBox(highContrastDark) { FilesScreen(state = filesFileSelected) }

@Nexus7Previews
@Composable
private fun FilesThemeHighContrastLight() =
    PreviewBox(highContrastLight) { FilesScreen(state = filesFileSelected) }

// ─────────────────────────────────────────────────────────────────────────────
// fs=L overflow shot — catches text/row clipping at the LARGEST in-app text size
// ─────────────────────────────────────────────────────────────────────────────

/**
 * fs = L via [fsLargeSeed] (NOT @Preview fontScale — that is a NO-OP here; see [PreviewBox] KDoc).
 * The selected state is the densest surface — the representative overflow shot.
 */
@Nexus7Previews
@Composable
private fun FilesFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { FilesScreen(state = filesFileSelected) }

// ─────────────────────────────────────────────────────────────────────────────
// Landscape spot-check (explicit widthDp/heightDp — ≥1 landscape per D-19 requirement)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Explicit landscape preview: Focus (DetailCard) | Field (list + FootButtonBar) side-by-side.
 * Verifies the ScreenScaffold landscape path (50/50 split) renders the two-pane grammar correctly.
 */
@Preview(
    name = "Files landscape 800×480",
    widthDp = 800,
    heightDp = 480,
    showBackground = true,
)
@Composable
private fun FilesLandscapeSpotCheck() =
    PreviewBox(colorfulDark) { FilesScreen(state = filesFileSelected) }
