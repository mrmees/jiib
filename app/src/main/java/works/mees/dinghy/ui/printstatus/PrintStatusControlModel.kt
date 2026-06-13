package works.mees.dinghy.ui.printstatus

import androidx.annotation.StringRes
import works.mees.dinghy.R
import works.mees.dinghy.state.LastJob
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

data class PrintStatusControlModel(
    val controls: List<PrintStatusControl>,
    val restartFilename: String?,
)

/**
 * One Print-Status foot-bar control (R1 gutter→foot migration, 2026-06-12). Labels are string
 * RESOURCE ids (R14 — no raw label strings in the model); the screen resolves them via
 * `stringResource`. The old `holdAction`/`accessibilityAction` hold-to-cancel channel is retired —
 * Cancel is an explicit foot button in both active modes (sketch-001 law: Pause · Cancel).
 */
data class PrintStatusControl(
    @StringRes val labelRes: Int,
    val enabled: Boolean,
    val tapAction: PrintStatusControlAction?,
)

/**
 * The foot-bar action vocabulary. E-Stop is NOT here — the AppShell-level [FloatingEStop] owns the
 * emergency stop on every destination while Printing/Paused (24-03/D-14). Standby's Preheat/System
 * actions are owned directly by `PrintStatusStandbyField` (no model-derived foot in Standby).
 */
enum class PrintStatusControlAction {
    RestartPrint,
    PausePrint,
    ResumePrint,
    GracefulCancel,

    /** Terminal clear/back — dispatches `SDCARD_RESET_FILE` (16-03). */
    Dismiss,
}

sealed interface PrintStatusPendingAction {
    data object Pause : PrintStatusPendingAction
    data object Resume : PrintStatusPendingAction
    data object Cancel : PrintStatusPendingAction
    data class Restart(val filename: String) : PrintStatusPendingAction
}

/**
 * Derive the per-mode foot-bar control set (sketch-001 state→foot law):
 *  - **Printing:** Pause · Cancel
 *  - **Paused:**   Resume · Cancel
 *  - **Terminal:** Dismiss · Reprint (Reprint disabled when no restart filename resolves)
 *  - **Standby:**  EMPTY — the Standby foot bar (Preheat · System) is hand-built in
 *    `PrintStatusStandbyField`, not model-derived.
 *
 * A non-null [pendingAction] swaps the matching control's label to its "-ing" debounce variant;
 * the renderer dims ALL foot controls while any action is pending.
 */
fun derivePrintStatusControls(
    state: PrinterState,
    lastJob: LastJob? = null,
    pendingAction: PrintStatusPendingAction? = null,
): PrintStatusControlModel {
    val restartFilename = state.restartFilename(lastJob)
    val controls = when (state.printState) {
        PrintState.Printing -> listOf(
            PrintStatusControl(
                labelRes = if (pendingAction == PrintStatusPendingAction.Pause) {
                    R.string.printstatus_foot_pausing
                } else {
                    R.string.printstatus_foot_pause
                },
                enabled = true,
                tapAction = PrintStatusControlAction.PausePrint,
            ),
            cancelControl(pendingAction),
        )
        PrintState.Paused -> listOf(
            PrintStatusControl(
                labelRes = if (pendingAction == PrintStatusPendingAction.Resume) {
                    R.string.printstatus_foot_resuming
                } else {
                    R.string.printstatus_foot_resume
                },
                enabled = true,
                tapAction = PrintStatusControlAction.ResumePrint,
            ),
            cancelControl(pendingAction),
        )
        PrintState.Complete,
        PrintState.Error,
        PrintState.Cancelled,
        -> terminalControls(
            restartFilename = restartFilename,
            pendingRestart = pendingAction as? PrintStatusPendingAction.Restart,
        )
        // Standby is ALWAYS Standby (Phase-16 behavior change): a leftover restartFilename does NOT
        // masquerade as a terminal state. The Standby foot (Preheat · System) is hand-built in
        // PrintStatusStandbyField — no model-derived controls here.
        PrintState.Standby -> emptyList()
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

/**
 * Clear a pending debounce action whose DISPATCH FAILED (R1 follow-up, Codex W-03). A failed
 * pause/resume/cancel/reprint never flips `printState`, so [clearPrintStatusPendingAction] (which
 * watches state) would leave the matching [pendingAction] stuck — dimming the whole foot bar until
 * the screen restarts (the 17-07 wedge class). Matches on the dispatcher's failure [failedKey]
 * (CommandRegistry: `pause_print` / `resume_print` / `cancel_print` / `start_print_<filename>`)
 * so an UNRELATED command failure (babystep, preheat) never prematurely clears the debounce.
 */
fun clearPendingOnDispatchFailure(
    pendingAction: PrintStatusPendingAction?,
    failedKey: String,
): PrintStatusPendingAction? = when (pendingAction) {
    null -> null
    PrintStatusPendingAction.Pause -> if (failedKey == "pause_print") null else pendingAction
    PrintStatusPendingAction.Resume -> if (failedKey == "resume_print") null else pendingAction
    PrintStatusPendingAction.Cancel -> if (failedKey == "cancel_print") null else pendingAction
    is PrintStatusPendingAction.Restart ->
        if (failedKey == "start_print_${pendingAction.filename}") null else pendingAction
}

/** The shared explicit Cancel control (Printing + Paused). Tap opens the GracefulCancel ConfirmGuard. */
private fun cancelControl(pendingAction: PrintStatusPendingAction?): PrintStatusControl =
    PrintStatusControl(
        labelRes = if (pendingAction == PrintStatusPendingAction.Cancel) {
            R.string.printstatus_foot_cancelling
        } else {
            R.string.printstatus_foot_cancel
        },
        enabled = true,
        tapAction = PrintStatusControlAction.GracefulCancel,
    )

// Terminal foot (sketch-001): Dismiss (accent — clears via SDCARD_RESET_FILE, does not discard
// pending input) + Reprint (go — the expected action, no guard, D-05). Reprint is disabled when no
// restart filename is resolvable.
private fun terminalControls(
    restartFilename: String?,
    pendingRestart: PrintStatusPendingAction.Restart?,
): List<PrintStatusControl> {
    val restartEnabled = restartFilename != null
    return listOf(
        PrintStatusControl(
            labelRes = R.string.printstatus_foot_dismiss,
            enabled = true,
            tapAction = PrintStatusControlAction.Dismiss,
        ),
        PrintStatusControl(
            labelRes = if (pendingRestart != null && pendingRestart.filename == restartFilename) {
                R.string.printstatus_foot_reprinting
            } else {
                R.string.printstatus_foot_reprint
            },
            enabled = restartEnabled,
            tapAction = if (restartEnabled) PrintStatusControlAction.RestartPrint else null,
        ),
    )
}

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
