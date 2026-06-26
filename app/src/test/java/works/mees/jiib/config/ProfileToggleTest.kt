package works.mees.jiib.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.di.AppContainer

/**
 * Host-pure proof of the wired webcam gating (MEDIUM-4) + the explicit Connection apiKey edit
 * semantics (MEDIUM-5). Webcam is now app-global (DisplayPrefs) — per-profile toggle tests removed.
 *
 * All behaviors are provable WITHOUT a DataStore/IO (no Windows back-to-back-`edit` race):
 *  - the `count × toggle` gate predicate (the Webcam-tile gate) is pure;
 *  - the apiKey blank-preserves / clear / replace resolution is pure.
 */
class ProfileToggleTest {

    // ---- MEDIUM-4: the webcam-tile gate requires BOTH count>0 AND the app-global toggle on -----------

    @Test
    fun webcam_tile_gating_requires_both_count_and_toggle() {
        assertFalse("count==0 → off regardless of toggle", AppContainer.webcamTileGate(count = 0, webcamEnabled = true))
        assertFalse("count==0 + toggle off → off", AppContainer.webcamTileGate(count = 0, webcamEnabled = false))
        assertFalse("toggle off → off regardless of count", AppContainer.webcamTileGate(count = 3, webcamEnabled = false))
        assertTrue("both hold → on", AppContainer.webcamTileGate(count = 1, webcamEnabled = true))
        assertTrue("both hold (multi-cam) → on", AppContainer.webcamTileGate(count = 5, webcamEnabled = true))
    }

    // ---- MEDIUM-5: explicit apiKey blank-preserves / clear / replace ----------------------------------

    @Test
    fun apikey_blank_preserves_existing() {
        // Blank field + no Clear → KEEP the stored key (a save without retyping must NOT wipe it).
        assertEquals("abc", AppContainer.resolveApiKeyEdit(existing = "abc", fieldInput = "", cleared = false))
        assertEquals("abc", AppContainer.resolveApiKeyEdit(existing = "abc", fieldInput = "   ", cleared = false))
    }

    @Test
    fun apikey_nonBlank_replaces() {
        assertEquals("xyz", AppContainer.resolveApiKeyEdit(existing = "abc", fieldInput = "xyz", cleared = false))
        // Replace also applies when there was no prior key.
        assertEquals("xyz", AppContainer.resolveApiKeyEdit(existing = null, fieldInput = "xyz", cleared = false))
    }

    @Test
    fun apikey_clear_writesNull() {
        // An explicit Clear removes the key even if the field is non-blank (Clear wins).
        assertNull(AppContainer.resolveApiKeyEdit(existing = "abc", fieldInput = "", cleared = true))
        assertNull(AppContainer.resolveApiKeyEdit(existing = "abc", fieldInput = "still-typed", cleared = true))
    }

    @Test
    fun apikey_blank_noExisting_returnsNull() {
        // A brand-new printer with a blank key field and nothing stored → no key.
        assertNull(AppContainer.resolveApiKeyEdit(existing = null, fieldInput = "", cleared = false))
    }
}
