package works.mees.dinghy.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.di.AppContainer

/**
 * Host-pure proof of the Phase-15.2-03 per-profile feature toggle (D-04) + the wired webcam gating
 * (MEDIUM-4) + the explicit Connection apiKey edit semantics (MEDIUM-5).
 *
 * All four behaviors are provable WITHOUT a DataStore/IO (no Windows back-to-back-`edit` race):
 *  - the toggle round-trips through the same `toPersisted → fromPersisted` wire form `ProfileStore.mutateActive`
 *    re-encodes inside its single `edit` (so a value persisted by `mutateActive { it.copy(webcamEnabled = …) }`
 *    survives a reload) — exercised here as the pure round-trip the writer applies;
 *  - the toggle is PER-PROFILE, not global (two profiles hold different values at once);
 *  - the `count × toggle` gate predicate (the Webcam-tile gate) is pure;
 *  - the apiKey blank-preserves / clear / replace resolution is pure.
 */
class ProfileToggleTest {

    private fun profile(id: String, webcamEnabled: Boolean = true, apiKey: String? = null) =
        Profile(id = id, host = "192.168.1.120", port = 7125, apiKey = apiKey, webcamEnabled = webcamEnabled)

    // ---- D-04: per-profile toggle round-trips through the wire form mutateActive re-encodes -----------

    @Test
    fun toggle_defaultsTrue_onFreshProfile() {
        // Preserve today's always-on webcam behavior: a profile constructed without the flag defaults true.
        assertTrue(Profile(id = "fresh", host = "h").webcamEnabled)
        assertTrue(PersistedProfile(id = "fresh", host = "h").webcamEnabled)
    }

    @Test
    fun toggle_round_trips_through_mutate_active() {
        // The exact transform mutateActive applies: copy the field, re-encode to the wire form, lift back.
        val before = profile(id = "a", webcamEnabled = true)
        val mutated = before.copy(webcamEnabled = false)
        val reloaded = Profile.fromPersisted(mutated.toPersisted())
        assertFalse("the toggled value must survive toPersisted → fromPersisted", reloaded.webcamEnabled)
        // The OTHER fields ride through unchanged.
        assertEquals("192.168.1.120", reloaded.host)
        assertEquals(7125, reloaded.port)
    }

    @Test
    fun toggle_is_per_profile_not_global() {
        val a = profile(id = "a", webcamEnabled = false)
        val b = profile(id = "b", webcamEnabled = true)
        // Mutating A does not touch B — they hold independent values simultaneously.
        val aReloaded = Profile.fromPersisted(a.toPersisted())
        val bReloaded = Profile.fromPersisted(b.toPersisted())
        assertFalse(aReloaded.webcamEnabled)
        assertTrue(bReloaded.webcamEnabled)
    }

    // ---- MEDIUM-4: the webcam-tile gate requires BOTH count>0 AND the per-profile toggle on -----------

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
