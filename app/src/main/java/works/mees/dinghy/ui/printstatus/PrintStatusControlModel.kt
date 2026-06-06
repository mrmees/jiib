package works.mees.dinghy.ui.printstatus

import works.mees.dinghy.state.LastJob
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

data class PrintStatusControlModel(
    val controls: List<PrintStatusControl>,
    val restartFilename: String?,
)

data class PrintStatusControl(
    val label: String,
    val enabled: Boolean,
    val tapAction: PrintStatusControlAction?,
    val holdAction: PrintStatusControlAction? = null,
    val accessibilityAction: PrintStatusControlAction? = null,
)

enum class PrintStatusControlAction {
    OpenFiles,
    RestartPrint,
    Tune,
    PausePrint,
    ResumePrint,
    GracefulCancel,
    EmergencyStop,

    // Phase-16 extended actions (per-state gutter sets — UI-SPEC "Per-state button intents").
    /** Standby spool-aware Preheat (accent). The decision routes through `selectPreheatPath` (16-02). */
    Preheat,

    /** Terminal clear/back (neutral) — dispatches `SDCARD_RESET_FILE` (16-03) in the Wave-3 screen. */
    Dismiss,

    /** Standby inert Power tile (red stop-intent chrome, NONFUNCTIONAL in P16, D-04). */
    Power,
}

sealed interface PrintStatusPendingAction {
    data object Pause : PrintStatusPendingAction
    data object Resume : PrintStatusPendingAction
    data object Cancel : PrintStatusPendingAction
    data class Restart(val filename: String) : PrintStatusPendingAction
}

fun derivePrintStatusControls(
    state: PrinterState,
    lastJob: LastJob? = null,
    pendingAction: PrintStatusPendingAction? = null,
): PrintStatusControlModel {
    val restartFilename = state.restartFilename(lastJob)
    val controls = when (state.printState) {
        PrintState.Printing -> activeControls(
            label = when (pendingAction) {
                PrintStatusPendingAction.Pause -> "Pausing"
                PrintStatusPendingAction.Cancel -> "Cancelling"
                else -> "Pause"
            },
            tapAction = PrintStatusControlAction.PausePrint,
        )
        PrintState.Paused -> pausedControls(
            label = when (pendingAction) {
                PrintStatusPendingAction.Resume -> "Resuming"
                PrintStatusPendingAction.Cancel -> "Cancelling"
                else -> "Resume"
            },
        )
        PrintState.Complete,
        PrintState.Error,
        PrintState.Cancelled,
        -> terminalControls(
            restartFilename = restartFilename,
            pendingRestart = pendingAction as? PrintStatusPendingAction.Restart,
        )
        // Standby is ALWAYS Standby (Phase-16 behavior change): a leftover restartFilename does NOT
        // masquerade as a terminal state. Restart-from-idle moves to the Standby launcher Files tile
        // (16-06), not the gutter — so we no longer branch to terminalControls here.
        PrintState.Standby -> standbyControls()
    }
    return PrintStatusControlModel(controls = controls, restartFilename = restartFilename)
}

fun clearPrintStatusPendingAction(
    pendingAction: PrintStatusPendingAction?,
    state: PrinterState,
): PrintStatusPendingAction? = when (pendingAction) {
    null -> null
    PrintStatusPendingAction.Pause -> when (state.printState) {
        PrintState.Printing -> pendingAction
        else -> null
    }
    PrintStatusPendingAction.Resume -> when (state.printState) {
        PrintState.Paused -> pendingAction
        else -> null
    }
    PrintStatusPendingAction.Cancel -> when (state.printState) {
        PrintState.Printing,
        PrintState.Paused,
        -> pendingAction
        else -> null
    }
    is PrintStatusPendingAction.Restart -> {
        val active = state.printState == PrintState.Printing || state.printState == PrintState.Paused
        if (active && state.printFilename == pendingAction.filename) null else pendingAction
    }
}

private fun activeControls(
    label: String,
    tapAction: PrintStatusControlAction,
): List<PrintStatusControl> = listOf(
    PrintStatusControl(
        label = "Tune",
        enabled = false,
        tapAction = null,
    ),
    PrintStatusControl(
        label = label,
        enabled = true,
        tapAction = tapAction,
        holdAction = PrintStatusControlAction.GracefulCancel,
        accessibilityAction = PrintStatusControlAction.GracefulCancel,
    ),
    stopControl(),
)

// Paused gutter (UI-SPEC): Resume (go) + Cancel (ConfirmGuard, red). NO E-Stop — the user is
// already intentionally intervening; Cancel ends the job. (Distinct from the Printing gutter, which
// keeps the E-Stop.)
private fun pausedControls(label: String): List<PrintStatusControl> = listOf(
    PrintStatusControl(
        label = "Tune",
        enabled = false,
        tapAction = null,
    ),
    PrintStatusControl(
        label = label,
        enabled = true,
        tapAction = PrintStatusControlAction.ResumePrint,
        holdAction = PrintStatusControlAction.GracefulCancel,
        accessibilityAction = PrintStatusControlAction.GracefulCancel,
    ),
    PrintStatusControl(
        label = "Cancel",
        enabled = true,
        tapAction = PrintStatusControlAction.GracefulCancel,
    ),
)

// Terminal gutter (UI-SPEC): Dismiss (neutral — clears via SDCARD_RESET_FILE, does not discard
// pending input) + Reprint (accent — natural primary, no guard). Reprint is disabled when no
// restart filename is resolvable.
private fun terminalControls(
    restartFilename: String?,
    pendingRestart: PrintStatusPendingAction.Restart?,
): List<PrintStatusControl> {
    val restartEnabled = restartFilename != null
    return listOf(
        PrintStatusControl(
            label = "Dismiss",
            enabled = true,
            tapAction = PrintStatusControlAction.Dismiss,
        ),
        PrintStatusControl(
            label = if (pendingRestart != null && pendingRestart.filename == restartFilename) "Reprinting" else "Reprint",
            enabled = restartEnabled,
            tapAction = if (restartEnabled) PrintStatusControlAction.RestartPrint else null,
        ),
    )
}

// Standby gutter (UI-SPEC "Per-state button intents"): Preheat (accent) + Power (inert, red
// stop-intent chrome, D-04). NO E-Stop in Standby — there is no active job to halt. The Power tile
// is rendered but nonfunctional in P16 (the Wave-3 screen wires its inert no-op).
private fun standbyControls(): List<PrintStatusControl> = listOf(
    PrintStatusControl(
        label = "Preheat",
        enabled = true,
        tapAction = PrintStatusControlAction.Preheat,
    ),
    PrintStatusControl(
        label = "Power",
        enabled = false,
        tapAction = PrintStatusControlAction.Power,
    ),
)

private fun stopControl(): PrintStatusControl =
    PrintStatusControl(
        label = "Stop",
        enabled = true,
        tapAction = PrintStatusControlAction.EmergencyStop,
    )

private fun PrinterState.restartFilename(lastJob: LastJob?): String? {
    return when (printState) {
        PrintState.Complete,
        PrintState.Error,
        PrintState.Cancelled,
        -> printFilename.ifBlank { lastJob?.filename.orEmpty() }.ifBlank { null }
        PrintState.Standby -> lastJob?.filename?.ifBlank { null }
        PrintState.Printing,
        PrintState.Paused,
        -> null
    }
}
