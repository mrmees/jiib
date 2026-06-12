package works.mees.dinghy.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM host test for the [PrinterMode] state machine (28-06, D-13).
 *
 * Covers:
 *  - Arming Edit from Normal → EditArmed
 *  - Arming Delete from Normal → DeleteArmed
 *  - Disarming via toggle (arm again → Normal)
 *  - Disarming via Back → Normal
 *  - Row tap in Normal → SwitchActive
 *  - Row tap in EditArmed → OpenEditor
 *  - Row tap in DeleteArmed → RequestDelete
 *  - Mode switch from armed (Edit → Delete, Delete → Edit) rather than stacking
 *
 * No Compose, no Android runtime — uses only the pure package-level transition
 * functions that live in PrintersScreen.kt.
 */
class PrintersModeToggleTest {

    // -------------------------------------------------------------------------
    // Arming transitions
    // -------------------------------------------------------------------------

    @Test
    fun armEdit_fromNormal_givesEditArmed() {
        val result = armEdit(PrinterMode.Normal)
        assertEquals(
            "armEdit from Normal must produce EditArmed",
            PrinterMode.EditArmed,
            result,
        )
    }

    @Test
    fun armDelete_fromNormal_givesDeleteArmed() {
        val result = armDelete(PrinterMode.Normal)
        assertEquals(
            "armDelete from Normal must produce DeleteArmed",
            PrinterMode.DeleteArmed,
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Disarm via toggle (tap the armed button again)
    // -------------------------------------------------------------------------

    @Test
    fun armEdit_fromEditArmed_disarms() {
        val result = armEdit(PrinterMode.EditArmed)
        assertEquals(
            "armEdit from EditArmed (tap toggle again) must disarm to Normal",
            PrinterMode.Normal,
            result,
        )
    }

    @Test
    fun armDelete_fromDeleteArmed_disarms() {
        val result = armDelete(PrinterMode.DeleteArmed)
        assertEquals(
            "armDelete from DeleteArmed (tap toggle again) must disarm to Normal",
            PrinterMode.Normal,
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Disarm via Back
    // -------------------------------------------------------------------------

    @Test
    fun disarm_alwaysGivesNormal() {
        assertEquals("disarm from EditArmed must give Normal",   PrinterMode.Normal, disarm())
        assertEquals("disarm from DeleteArmed must give Normal", PrinterMode.Normal, disarm())
    }

    // -------------------------------------------------------------------------
    // Mode switch from armed (arm the OTHER toggle — no stacking)
    // -------------------------------------------------------------------------

    @Test
    fun armDelete_fromEditArmed_switchesToDeleteArmed() {
        val result = armDelete(PrinterMode.EditArmed)
        assertEquals(
            "armDelete from EditArmed must switch modes (not stack) → DeleteArmed",
            PrinterMode.DeleteArmed,
            result,
        )
    }

    @Test
    fun armEdit_fromDeleteArmed_switchesToEditArmed() {
        val result = armEdit(PrinterMode.DeleteArmed)
        assertEquals(
            "armEdit from DeleteArmed must switch modes (not stack) → EditArmed",
            PrinterMode.EditArmed,
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Row tap effects by mode
    // -------------------------------------------------------------------------

    @Test
    fun rowTap_inNormal_givesSwitchActive() {
        val result = rowTapEffect(PrinterMode.Normal)
        assertEquals(
            "row tap in Normal must produce SwitchActive",
            RowTapEffect.SwitchActive,
            result,
        )
    }

    @Test
    fun rowTap_inEditArmed_givesOpenEditor() {
        val result = rowTapEffect(PrinterMode.EditArmed)
        assertEquals(
            "row tap in EditArmed must produce OpenEditor",
            RowTapEffect.OpenEditor,
            result,
        )
    }

    @Test
    fun rowTap_inDeleteArmed_givesRequestDelete() {
        val result = rowTapEffect(PrinterMode.DeleteArmed)
        assertEquals(
            "row tap in DeleteArmed must produce RequestDelete",
            RowTapEffect.RequestDelete,
            result,
        )
    }

    // -------------------------------------------------------------------------
    // CR-01: Save-time API-key resolution after an explicit Clear
    // (the stale editor snapshot must never resurrect the cleared key)
    // -------------------------------------------------------------------------

    @Test
    fun resolveEditorKeyOnSave_clearedThenBlankSave_persistsNull() {
        assertNull(
            "Clear key followed by Save with a blank field must persist null — " +
                "the stale snapshot's old key must NOT resurrect (CR-01)",
            resolveEditorKeyOnSave(storedKey = "old-secret", keyCleared = true, fieldInput = ""),
        )
    }

    @Test
    fun resolveEditorKeyOnSave_clearedThenTypedSave_persistsTypedKey() {
        assertEquals(
            "Clear key followed by typing a NEW key must persist the typed key",
            "new-secret",
            resolveEditorKeyOnSave(storedKey = "old-secret", keyCleared = true, fieldInput = "new-secret"),
        )
    }

    @Test
    fun resolveEditorKeyOnSave_notClearedBlankSave_preservesStoredKey() {
        assertEquals(
            "Save with a blank field and no Clear must preserve the stored key",
            "old-secret",
            resolveEditorKeyOnSave(storedKey = "old-secret", keyCleared = false, fieldInput = ""),
        )
    }

    @Test
    fun resolveEditorKeyOnSave_notClearedTypedSave_replacesStoredKey() {
        assertEquals(
            "Save with a typed field must replace the stored key",
            "new-secret",
            resolveEditorKeyOnSave(storedKey = "old-secret", keyCleared = false, fieldInput = "new-secret"),
        )
    }
}
