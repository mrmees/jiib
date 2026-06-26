package works.mees.jiib.ui.files

import works.mees.jiib.state.PrintState

/**
 * Pure file-delete gate (CALIB-06 / D-15). The OLD FilesScreen predicate blocked ALL files during
 * any print; D-15 narrows it: during a print, ONLY the currently-printing file is undeletable —
 * every other idle file stays deletable. Extracted as a pure predicate so the path-form match is
 * host-tested ([FilesDeleteGateTest]) and can't silently regress (T-09-03-02).
 *
 * PATH-FORM CONTRACT: [selectedPath] and [activePrintFilename] are the SAME relative, dir-prefixed
 * form WITHOUT a leading `gcodes/` (e.g. `"miata/airbox-bracket.gcode"`) — verified against
 * docs/moonraker-capabilities.md. A `gcodes/`-prefixed selection therefore does NOT match the bare
 * active filename, so it stays deletable (the path-form regression guard).
 *
 * Idle (`activePrintFilename == ""`, or a non-print state) → everything deletable.
 *
 * @param selectedPath the file the user picked (relative, no `gcodes/`); null = nothing selected.
 * @param activePrintFilename `print_stats.filename` (same form); `""` when idle.
 * @param printState the live print state.
 */
fun deleteAllowed(
    selectedPath: String?,
    activePrintFilename: String,
    printState: PrintState,
): Boolean {
    if (selectedPath == null) return false
    val printActive = printState == PrintState.Printing || printState == PrintState.Paused
    if (!printActive || activePrintFilename.isEmpty()) return true
    // Mid-print: only the actively-printing file is protected.
    return selectedPath != activePrintFilename
}
