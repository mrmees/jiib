package works.mees.dinghy.ui.printstatus

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import works.mees.dinghy.state.PrinterState

/**
 * The PURE per-mode layout/control derivation for the Phase-16 four-state Print-Status home (16-06,
 * the seam Codex asked for so the ~942-line Compose screen has an automated gate BEFORE the on-device
 * pass). NO Compose, NO Android — plain Kotlin so it runs in the JVM unit suite ([PrintStatusUiModelTest]).
 *
 * It maps a [PrintStatusMode] (+ the small extra inputs the screen already has at hand) to a
 * host-testable description of the per-mode surface:
 *  - [launcherDests] — the ordered, curated Standby launcher destination list (empty for the non-Standby
 *    modes; the Standby grid renders FROM this in the fixed UI-SPEC order),
 *  - [foot] — the active foot-bar control set (built by REUSING the `PrintStatusControlModel`
 *    per-mode list-builders — this model does NOT re-derive the foot sets; R1 gutter→foot migration),
 *  - [activeRow] — which Field row is active (shortcut vs babystep) for the Printing/Paused modes,
 *  - [showErrorLines] — whether the Terminal error-line area is shown (Terminal(Error) only).
 *
 * The screen renders FROM this so the mode→layout/control logic is unit-tested before the big Compose
 * edit (ADR-0001 toolkit-agnostic). The screen still owns the actual COMPOSE (the focus composition,
 * the stat frame, dispatch wiring); this model owns the structural/ordering DECISIONS.
 */

/** A launcher destination on the Standby grid. Decoupled from `ui.route.Dest` so this stays pure
 *  (no UI import) — the screen maps each [LauncherDest] to its `Dest` + `onNavigate`. */
enum class LauncherDest {
    Files,
    Temperature,
    Move,
    Extrude,
    Calibration,
    Spool,
    Macros,
    Console,
}

/** Which Field row the Printing/Paused mode shows: the normal shortcut grid, or the babystep 3-cell
 *  row (early-layer window). [None] for Standby/Terminal (no Field shortcut/babystep row). */
enum class PrintStatusFieldRow {
    Shortcut,
    Babystep,
    None,
}

/**
 * The pure per-mode surface description the screen renders from.
 *
 * @param mode the classified mode (the input — carried for the screen's `when`).
 * @param launcherDests the ordered Standby launcher list (empty for non-Standby modes).
 * @param foot the active foot-bar control set (reused from the per-mode list-builders; empty for
 *   Standby — its foot bar is hand-built in `PrintStatusStandbyField`).
 * @param activeRow which Field row is active (shortcut vs babystep) — [PrintStatusFieldRow.None] off
 *   the active modes.
 * @param showErrorLines whether the Terminal error-line area is shown (true only for Terminal(Error)).
 */
data class PrintStatusUiModel(
    val mode: PrintStatusMode,
    /** ImmutableList so Compose's strong-skip treats the launcher grid as a stable param. */
    val launcherDests: ImmutableList<LauncherDest>,
    val foot: List<PrintStatusControl>,
    val activeRow: PrintStatusFieldRow,
    val showErrorLines: Boolean,
)

/**
 * Build the pure [PrintStatusUiModel] for [mode]. Reuses the per-mode foot list-builders via
 * [derivePrintStatusControls] (so the foot sets are owned in ONE place, never re-derived here).
 *
 * @param mode the classified mode.
 * @param state the printer state (passed straight to [derivePrintStatusControls] for the foot set
 *   + restart-filename resolution — this model does not read it for anything else).
 * @param lastJob the one-shot last completed job (foot restart-filename input only).
 * @param pendingAction the in-flight debounce action (foot label input only).
 * @param spoolmanPresent whether the printer exposes Spoolman — gates the Spool launcher tile.
 * @param hasBookmarkedMacros whether the user has bookmarked macros — gates the Macros launcher tile.
 * @param babystepVisible whether the early-layer babystep window is active — picks the Field row for
 *   the Printing/Paused modes (shortcut when false, babystep when true).
 */
fun uiModel(
    mode: PrintStatusMode,
    state: PrinterState,
    lastJob: works.mees.dinghy.state.LastJob? = null,
    pendingAction: PrintStatusPendingAction? = null,
    spoolmanPresent: Boolean = false,
    hasBookmarkedMacros: Boolean = false,
    babystepVisible: Boolean = false,
): PrintStatusUiModel {
    val foot = derivePrintStatusControls(state = state, lastJob = lastJob, pendingAction = pendingAction).controls
    val launcherDests: ImmutableList<LauncherDest> = if (mode is PrintStatusMode.Standby) {
        standbyLauncherDests(spoolmanPresent = spoolmanPresent, hasBookmarkedMacros = hasBookmarkedMacros)
    } else {
        persistentListOf()
    }
    val activeRow = when (mode) {
        is PrintStatusMode.Printing,
        is PrintStatusMode.Paused,
        -> if (babystepVisible) PrintStatusFieldRow.Babystep else PrintStatusFieldRow.Shortcut
        else -> PrintStatusFieldRow.None
    }
    val showErrorLines = mode is PrintStatusMode.Terminal && mode.kind == TerminalKind.Error
    return PrintStatusUiModel(
        mode = mode,
        launcherDests = launcherDests,
        foot = foot,
        activeRow = activeRow,
        showErrorLines = showErrorLines,
    )
}

/**
 * The fixed, curated Standby launcher order (UI-SPEC "Launcher order"):
 * Files · Temperature · Move · Extrude · Calibration · Spool[if present] · Macros[if bookmarked] ·
 * Console. Forward stubs and system destinations (System page) are reached via WaterfallHome's
 * System foot button (NavDest.System) rather than via a launcher tile.
 */
private fun standbyLauncherDests(
    spoolmanPresent: Boolean,
    hasBookmarkedMacros: Boolean,
): ImmutableList<LauncherDest> = buildList {
    add(LauncherDest.Files)
    add(LauncherDest.Temperature)
    add(LauncherDest.Move)
    add(LauncherDest.Extrude)
    add(LauncherDest.Calibration)
    if (spoolmanPresent) add(LauncherDest.Spool)
    if (hasBookmarkedMacros) add(LauncherDest.Macros)
    add(LauncherDest.Console)
}.toImmutableList()
