package works.mees.dinghy.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.config.PersistedProfile
import works.mees.dinghy.config.Profile

/**
 * Pure JVM host test for the [PrinterMode] state machine (Task 5 — 2-mode machine).
 *
 * Covers:
 *  - Arming Edit from Normal → EditArmed
 *  - Disarming via toggle (arm again → Normal)
 *  - Disarming via Back → Normal
 *  - Row tap in Normal → SwitchActive
 *  - Row tap in EditArmed → OpenEditor
 *
 * No Compose, no Android runtime — uses only the pure package-level transition
 * functions that live in PrintersScreen.kt.
 */
class PrintersModeToggleTest {

    // -------------------------------------------------------------------------
    // Arming transitions
    // -------------------------------------------------------------------------

    @Test fun armEdit_fromNormal_givesEditArmed() =
        assertEquals(PrinterMode.EditArmed, armEdit(PrinterMode.Normal))

    @Test fun armEdit_fromEditArmed_disarms() =
        assertEquals(PrinterMode.Normal, armEdit(PrinterMode.EditArmed))

    // -------------------------------------------------------------------------
    // Disarm via Back
    // -------------------------------------------------------------------------

    @Test fun disarm_alwaysGivesNormal() =
        assertEquals(PrinterMode.Normal, disarm())

    // -------------------------------------------------------------------------
    // Row tap effects by mode
    // -------------------------------------------------------------------------

    @Test fun rowTap_inNormal_givesSwitchActive() =
        assertEquals(RowTapEffect.SwitchActive, rowTapEffect(PrinterMode.Normal))

    @Test fun rowTap_inEditArmed_givesOpenEditor() =
        assertEquals(RowTapEffect.OpenEditor, rowTapEffect(PrinterMode.EditArmed))

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

    // -------------------------------------------------------------------------
    // Task 7: migrated secure profile round-trips through the editor save helper
    // -------------------------------------------------------------------------

    @Test
    fun connectionEditorSave_migratedSecureProfileStillProducesWss() {
        val opened = Profile.fromPersisted(
            PersistedProfile(id = "legacy", host = "secure.local", port = 7130, useSecure = true),
        )

        val saved = buildProfileFromConnectionEditorSave(
            existing = opened,
            nameInput = "Secure Printer",
            host = opened.host,
            port = opened.port,
            apiKeyInput = "",
            keyCleared = false,
            advancedUrlInput = opened.advancedUrl.orEmpty(),
        )

        assertEquals("https://secure.local:7130", saved.advancedUrl)
        assertFalse(saved.useSecure)
        assertEquals("wss://secure.local:7130/websocket", saved.toConnectionConfig().wsUrl)
    }
}
