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
        PrintState.Paused -> activeControls(
            label = when (pendingAction) {
                PrintStatusPendingAction.Resume -> "Resuming"
                PrintStatusPendingAction.Cancel -> "Cancelling"
                else -> "Resume"
            },
            tapAction = PrintStatusControlAction.ResumePrint,
        )
        PrintState.Complete,
        PrintState.Error,
        PrintState.Cancelled,
        -> terminalControls(
            restartFilename = restartFilename,
            pendingRestart = pendingAction as? PrintStatusPendingAction.Restart,
        )
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

private fun terminalControls(
    restartFilename: String?,
    pendingRestart: PrintStatusPendingAction.Restart?,
): List<PrintStatusControl> {
    val restartEnabled = restartFilename != null
    return listOf(
        PrintStatusControl(
            label = "Files",
            enabled = true,
            tapAction = PrintStatusControlAction.OpenFiles,
        ),
        PrintStatusControl(
            label = if (pendingRestart != null && pendingRestart.filename == restartFilename) "Restarting" else "Restart print",
            enabled = restartEnabled,
            tapAction = if (restartEnabled) PrintStatusControlAction.RestartPrint else null,
        ),
        stopControl(),
    )
}

private fun standbyControls(): List<PrintStatusControl> = listOf(
    PrintStatusControl(
        label = "Tune",
        enabled = false,
        tapAction = null,
    ),
    PrintStatusControl(
        label = "Pause",
        enabled = false,
        tapAction = null,
    ),
    stopControl(),
)

private fun stopControl(): PrintStatusControl =
    PrintStatusControl(
        label = "Stop",
        enabled = true,
        tapAction = PrintStatusControlAction.EmergencyStop,
    )

private fun PrinterState.restartFilename(lastJob: LastJob?): String? {
    if (printState != PrintState.Complete && printState != PrintState.Error && printState != PrintState.Cancelled) {
        return null
    }
    return printFilename.ifBlank { lastJob?.filename.orEmpty() }.ifBlank { null }
}
