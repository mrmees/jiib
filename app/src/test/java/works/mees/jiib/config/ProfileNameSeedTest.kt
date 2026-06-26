package works.mees.jiib.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileNameSeedTest {
    @Test fun seeds_when_unnamed_unseeded_and_hostname_present() {
        assertTrue(shouldSeedName(currentName = null, nameAutoSeeded = false, hostname = "ender5plus"))
        assertTrue(shouldSeedName(currentName = "   ", nameAutoSeeded = false, hostname = "ender3"))
    }
    @Test fun no_seed_when_already_named() {
        assertFalse(shouldSeedName(currentName = "My E5+", nameAutoSeeded = false, hostname = "ender5plus"))
    }
    @Test fun no_seed_when_already_seeded() {
        assertFalse(shouldSeedName(currentName = null, nameAutoSeeded = true, hostname = "ender5plus"))
    }
    @Test fun no_seed_when_hostname_blank_or_null() {
        assertFalse(shouldSeedName(currentName = null, nameAutoSeeded = false, hostname = null))
        assertFalse(shouldSeedName(currentName = null, nameAutoSeeded = false, hostname = "   "))
    }
    @Test fun editor_save_flag_logic() {
        // new IP-only add (no prior name, blank) → still auto-seedable on first connect
        assertFalse(resolveAutoSeededOnSave(cleanName = null, prior = false, priorName = null))
        // typed a name on add → lock auto-seed off
        assertTrue(resolveAutoSeededOnSave(cleanName = "Foo", prior = false, priorName = null))
        // EDIT a previously-named profile, clearing the name → lock (stays blank, no re-seed) — B3
        assertTrue(resolveAutoSeededOnSave(cleanName = null, prior = false, priorName = "Foo"))
        // already locked (e.g. auto-seeded) and cleared → stays locked
        assertTrue(resolveAutoSeededOnSave(cleanName = null, prior = true, priorName = null))
        // RE-name (typed a new name over an old one) → locked (both clauses fire)
        assertTrue(resolveAutoSeededOnSave(cleanName = "New", prior = false, priorName = "Old"))
    }
    @Test fun nameAutoSeeded_round_trips_and_defaults_false() {
        assertFalse(Profile(id = "a", host = "h").nameAutoSeeded)
        assertFalse(PersistedProfile(id = "a", host = "h").nameAutoSeeded)
        val p = Profile(id = "a", host = "h", name = "ender5plus", nameAutoSeeded = true)
        val round = Profile.fromPersisted(p.toPersisted())
        assertTrue(round.nameAutoSeeded)
        assertEquals("ender5plus", round.name)
    }
}
