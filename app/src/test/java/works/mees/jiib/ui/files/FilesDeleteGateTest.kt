// RED scaffold (Wave 0) — turns GREEN in 09-07 (deleteAllowed D-15 fix).
package works.mees.jiib.ui.files

import works.mees.jiib.state.PrintState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (09-01) — turned GREEN by 09-07 (`deleteAllowed`, the D-15 FilesScreen fix).
 *
 * REQ-CALIB-06 (D-15). The CURRENT FilesScreen predicate blocks ALL files during any print
 * (FilesScreen.kt: `deleteEnabled = selected != null && !printingActive`). D-15 narrows this:
 * during a print, ONLY the currently-printing file is undeletable; every other idle file stays
 * deletable. This pure predicate is extracted so the path-form match is host-tested.
 *
 * PATH-FORM CONTRACT: `selectedPath` and `activePrintFilename` are the SAME relative,
 * dir-prefixed form WITHOUT a leading "gcodes/" (e.g. "miata/airbox-bracket.gcode") — verified
 * against docs/moonraker-capabilities.md. Idle (activePrintFilename == "") → everything deletable.
 *
 * Production symbol referenced (NOT YET BUILT → RED):
 *   `deleteAllowed(selectedPath, activePrintFilename, printState): Boolean`
 * in `works.mees.jiib.ui.files`.
 */
class FilesDeleteGateTest {

    private val printing = PrintState.Printing
    private val paused = PrintState.Paused
    private val standby = PrintState.Standby

    @Test
    fun duringPrint_onlyTheActiveFileIsUndeletable() {
        // The printing file itself cannot be deleted...
        assertFalse(
            deleteAllowed(
                selectedPath = "miata/airbox-bracket.gcode",
                activePrintFilename = "miata/airbox-bracket.gcode",
                printState = printing,
            )
        )
        // ...but any OTHER idle file stays deletable mid-print (the D-15 relaxation).
        assertTrue(
            deleteAllowed(
                selectedPath = "calibration/temp-tower.gcode",
                activePrintFilename = "miata/airbox-bracket.gcode",
                printState = printing,
            )
        )
    }

    @Test
    fun pausedPrint_alsoScopesToActiveFileOnly() {
        assertFalse(
            deleteAllowed(
                selectedPath = "miata/airbox-bracket.gcode",
                activePrintFilename = "miata/airbox-bracket.gcode",
                printState = paused,
            )
        )
        assertTrue(
            deleteAllowed(
                selectedPath = "other.gcode",
                activePrintFilename = "miata/airbox-bracket.gcode",
                printState = paused,
            )
        )
    }

    @Test
    fun idle_everythingDeletable() {
        // activePrintFilename == "" (idle) → every file deletable, including a same-named one.
        assertTrue(
            deleteAllowed(
                selectedPath = "miata/airbox-bracket.gcode",
                activePrintFilename = "",
                printState = standby,
            )
        )
    }

    @Test
    fun pathFormIsRelativeDirPrefixed_noLeadingGcodes() {
        // A leading "gcodes/" mismatch must NOT accidentally protect the wrong file: the active
        // filename is the bare relative form, so a "gcodes/"-prefixed selection does NOT match.
        assertTrue(
            deleteAllowed(
                selectedPath = "gcodes/miata/airbox-bracket.gcode",
                activePrintFilename = "miata/airbox-bracket.gcode",
                printState = printing,
            )
        )
    }
}
