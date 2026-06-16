package works.mees.dinghy.config

/**
 * One-time hostname-seed gate (2026-06-15): seed a profile's name from the Moonraker hostname ONLY
 * when it is un-named, not yet auto-seeded, and a real hostname is present. Pure → host-testable;
 * applied INSIDE the [ProfileStore.mutateActive] transform against the freshly-decoded profile.
 */
fun shouldSeedName(currentName: String?, nameAutoSeeded: Boolean, hostname: String?): Boolean =
    !nameAutoSeeded && currentName.isNullOrBlank() && !hostname.isNullOrBlank()

/**
 * The [Profile.nameAutoSeeded] value to persist when the connection editor SAVES. Lock auto-seed off
 * (return true) when EITHER the user is saving a non-blank name, OR the profile already had a name
 * (`priorName` non-blank — so clearing a previously-named profile stays blank), OR it was already
 * locked. A brand-new IP-only profile (blank name, no prior name, not yet seeded) returns false and
 * still auto-seeds on first connect. `priorName` is the profile's name BEFORE this save (null for a
 * new profile) — closes the pre-feature "had a name, cleared it → re-seeds" hole (Codex B3).
 */
fun resolveAutoSeededOnSave(cleanName: String?, prior: Boolean, priorName: String?): Boolean =
    prior || !cleanName.isNullOrBlank() || !priorName.isNullOrBlank()
