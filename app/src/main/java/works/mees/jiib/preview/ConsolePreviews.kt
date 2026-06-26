package works.mees.jiib.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.jiib.ui.console.ConsoleLine
import works.mees.jiib.ui.console.ConsoleSeverity
import works.mees.jiib.ui.console.ConsoleScreen

/**
 * @Preview matrix for [ConsoleScreen] (25-04 / D-19 / D-20).
 *
 * ## Spike verdict applied — Console: Views (25-01 binding)
 * The 25-01 spike returned a ~8× p90 regression (73.35 ms vs 9.26 ms baseline) for a Compose
 * `LazyColumn` under live-churn on Adreno 320. `ConsoleListView` is retained and visually conformed.
 * The class-equivalent exception is recorded in COMPONENTS.md §8.
 *
 * ## No live Moonraker (SC-1)
 * Every preview drives the STATELESS [ConsoleScreen(lines = …)] overload with fake [ConsoleLine]
 * fixtures — no [ConsoleHolder], no `collectAsStateWithLifecycle`, no socket. The [ConsoleListView]
 * embedded RecyclerView is preview-safe (it is an [AndroidView]; preview renders it as a placeholder
 * box, which is the correct behaviour per the D-05 idiom).
 *
 * ## Matrix shape (docs/ui_design/PREVIEW_AND_TOKENS.md §"Minimize proliferation")
 *  - [ConsoleAllFiltersOff] + [ConsoleFilterOn] — the two primary filter states (toggles off / one
 *    toggle active showing Accent) on Colorful/dark via [Nexus7Previews] (portrait + landscape).
 *  - [ConsoleTheme*] siblings — the full 6-theme matrix on the representative "all-filters-off" state.
 *  - [ConsoleFsLargeOverflow] — one fs=L shot via [fsLargeSeed] (NOT @Preview fontScale — a NO-OP).
 *  - [ConsoleLandscapeSpotCheck] — explicit landscape `@Preview(widthDp=800,heightDp=480)`.
 *
 * ## Fake fixtures
 * A realistic mix of console line types: normal gcode responses, temperature report echo, a timelapse
 * frame line, a prompt command line, an error line, and a warning line. Demonstrates which lines
 * appear/disappear as filters toggle.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Fake fixtures
// ─────────────────────────────────────────────────────────────────────────────

private val fakeConsoleLines: List<ConsoleLine> = listOf(
    ConsoleLine(
        rawMessage = "// action:prompt_begin Filament change needed",
        severity = ConsoleSeverity.WARNING,
        timeEpoch = 1_749_600_010.0,
    ),
    ConsoleLine(
        rawMessage = "ok T:210.4 /210.0 B:60.1 /60.0 @:32",
        severity = ConsoleSeverity.NORMAL,
        timeEpoch = 1_749_600_020.0,
    ),
    ConsoleLine(
        rawMessage = "M104 S210",
        severity = ConsoleSeverity.NORMAL,
        timeEpoch = null,
    ),
    ConsoleLine(
        rawMessage = "_TIMELAPSE_NEW_FRAME FRAME=42",
        severity = ConsoleSeverity.NORMAL,
        timeEpoch = null,
    ),
    ConsoleLine(
        rawMessage = "!! Emergency Stop triggered by MCU watchdog",
        severity = ConsoleSeverity.ERROR,
        timeEpoch = null,
    ),
    ConsoleLine(
        rawMessage = "// probe: open",
        severity = ConsoleSeverity.WARNING,
        timeEpoch = 1_749_600_030.0,
    ),
    ConsoleLine(
        rawMessage = "ok T:209.8 /210.0 B:60.0 /60.0 @:30",
        severity = ConsoleSeverity.NORMAL,
        timeEpoch = 1_749_600_040.0,
    ),
    ConsoleLine(
        rawMessage = "G1 X120.000 Y120.000 E2.5000",
        severity = ConsoleSeverity.NORMAL,
        timeEpoch = null,
    ),
    ConsoleLine(
        rawMessage = "SET_GCODE_VARIABLE MACRO=TIMELAPSE_HASH VARIABLE=x VALUE=1",
        severity = ConsoleSeverity.NORMAL,
        timeEpoch = null,
    ),
    ConsoleLine(
        rawMessage = "LOAD_FILAMENT",
        severity = ConsoleSeverity.NORMAL,
        timeEpoch = null,
    ),
    ConsoleLine(
        rawMessage = "// action:prompt_button OK",
        severity = ConsoleSeverity.WARNING,
        timeEpoch = null,
    ),
    ConsoleLine(
        rawMessage = "Done printing file",
        severity = ConsoleSeverity.NORMAL,
        timeEpoch = null,
    ),
)

// ─────────────────────────────────────────────────────────────────────────────
// Filter-state axis (Colorful/dark — portrait + landscape via @Nexus7Previews)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * All 3 filters OFF — shows the full unfiltered log including temperature, timelapse, and prompt
 * lines. All FootButtonBar toggles display at Intent.Neutral.
 */
@Nexus7Previews
@Composable
private fun ConsoleAllFiltersOff() =
    PreviewBox(colorfulDark) {
        ConsoleScreen(
            lines = fakeConsoleLines,
            hideTemps = false,
            hideTimelapse = false,
            hidePrompt = false,
        )
    }

/**
 * hideTemps = true — temperature echo lines filtered out; HideTemps toggle renders Intent.Accent
 * (accentLine outline). Verifies the active toggle visual state.
 */
@Nexus7Previews
@Composable
private fun ConsoleFilterOn() =
    PreviewBox(colorfulDark) {
        ConsoleScreen(
            lines = fakeConsoleLines.filterNot { it.rawMessage.matches(Regex("^(?:ok\\s+)?(B|C|T\\d*):.*")) },
            hideTemps = true,
            hideTimelapse = false,
            hidePrompt = false,
        )
    }

// ─────────────────────────────────────────────────────────────────────────────
// Full 6-theme matrix (all-filters-off — representative unfiltered log state)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ConsoleThemeColorfulDark() =
    PreviewBox(colorfulDark) { ConsoleScreen(lines = fakeConsoleLines) }

@Nexus7Previews
@Composable
private fun ConsoleThemeColorfulLight() =
    PreviewBox(colorfulLight) { ConsoleScreen(lines = fakeConsoleLines) }

@Nexus7Previews
@Composable
private fun ConsoleThemeSimpleDark() =
    PreviewBox(simpleDark) { ConsoleScreen(lines = fakeConsoleLines) }

@Nexus7Previews
@Composable
private fun ConsoleThemeSimpleLight() =
    PreviewBox(simpleLight) { ConsoleScreen(lines = fakeConsoleLines) }

@Nexus7Previews
@Composable
private fun ConsoleThemeHighContrastDark() =
    PreviewBox(highContrastDark) { ConsoleScreen(lines = fakeConsoleLines) }

@Nexus7Previews
@Composable
private fun ConsoleThemeHighContrastLight() =
    PreviewBox(highContrastLight) { ConsoleScreen(lines = fakeConsoleLines) }

// ─────────────────────────────────────────────────────────────────────────────
// fs=L overflow shot — catches FootButtonBar / text clipping at LARGEST in-app text size
// ─────────────────────────────────────────────────────────────────────────────

/**
 * fs = L via [fsLargeSeed] (NOT @Preview fontScale — that is a NO-OP; see [PreviewBox] KDoc).
 * The all-filters-off state is the representative overflow shot for the FootButtonBar glyphs.
 */
@Nexus7Previews
@Composable
private fun ConsoleFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { ConsoleScreen(lines = fakeConsoleLines) }

// ─────────────────────────────────────────────────────────────────────────────
// Landscape spot-check (explicit widthDp/heightDp — ≥1 landscape per D-19 requirement)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Explicit landscape preview: field-only layout — the scrollback fills the height with the
 * FootButtonBar row at the foot. Verifies the ScreenScaffold landscape path (focus=null so the
 * full width is given to the Field).
 */
@Preview(
    name = "Console landscape 800×480",
    widthDp = 800,
    heightDp = 480,
    showBackground = true,
)
@Composable
private fun ConsoleLandscapeSpotCheck() =
    PreviewBox(colorfulDark) { ConsoleScreen(lines = fakeConsoleLines) }
