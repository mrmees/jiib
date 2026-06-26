package works.mees.jiib.ui.webcam

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Per-printer preferred-cam persistence (CAM-01, D-10) — the "last-viewed cam, remembered per printer"
 * store. There is NO explicit "set as default" / pin action: the preference is written as a side-effect
 * of the holder selecting/cycling a cam (the holder calls [setPreferredCam] on selection), and read back
 * on the next visit to default the focus to the previously-viewed cam (else first-in-list).
 *
 * SHAPE (the [works.mees.jiib.ui.macros.MacroPrefs] precedent, copied verbatim): the [DataStore] is
 * INJECTED (no `preferencesDataStore` delegate) so it is host-testable; the PRODUCTION instance is its
 * OWN `webcam.preferences_pb` file (NOT shared with theme/connection/macros), created by
 * [works.mees.jiib.DinghyApp] and exposed via [works.mees.jiib.di.AppContainer.webcamPrefs]. Like
 * MacroPrefs it is PROCESS-SCOPED + CONNECTION-INDEPENDENT — the saved preference survives reconnects
 * and printer swaps.
 *
 * PER-PRINTER KEY (D-06 "re-key on the profile id, not host") — the [works.mees.jiib.theme.ThemePrefs]
 * dynamic-string-key idiom: the preferred-cam value is stored under `preferred_cam_<profileId>`, derived
 * from the ACTIVE PROFILE'S stable UUID (`Profile.id`), NOT the host. Keying on the profile id (not the
 * host) means two profiles that share a host (same box, different port/key) keep INDEPENDENT preferred
 * cams, and the preference survives a host edit on a profile. Switching printers reads that profile's own
 * saved cam (or none → first-in-list).
 *
 * NO MIGRATION (D-07 — fresh start): old host-keyed `preferred_cam_<host>` entries are simply ORPHANED
 * (never read or copied forward). On the first visit after the re-key a profile has no saved cam → it
 * falls to first-in-list, exactly as a brand-new profile would.
 *
 * FAIL-SAFE READ CONTRACT (D-10/T-10-13, mirrors MacroPrefs/ConnectionStore): a read [IOException]
 * (corrupt/partial blob) recovers by emitting empty prefs → NO preference (null → the holder falls to
 * first-in-list), NEVER a crash. An untrusted/odd cam id round-trips as an opaque string; it only ever
 * defaults a selection when it still matches a currently-listed cam, so a stale/garbage id is harmless.
 *
 * The stored value is a cam IDENTITY string — the holder uses `Webcam.uid ?: Webcam.name` (the stable
 * per-cam id). HOST-TEST NOTE (per MacroPrefs): back-to-back writes / a second instance on one
 * `.preferences_pb` are not reliably host-testable on the Windows build host; the test proves the
 * single-write round-trip, the per-host keying, and the fail-safe default read.
 */
class WebcamPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * The preferred (last-viewed) cam identity for [profileId], or `null` when none is saved (→ first-in-list).
     * Fail-safe: a read error yields `null`, never throws.
     */
    fun preferredCam(profileId: String): Flow<String?> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[preferredCamKey(profileId)] }

    /**
     * Persist [camId] as the preferred (last-viewed) cam for [profileId] (D-06). Called by the holder when
     * a cam is selected/cycled — the only writer (there is no separate "set as default" UI).
     */
    suspend fun setPreferredCam(profileId: String, camId: String) {
        dataStore.edit { prefs -> prefs[preferredCamKey(profileId)] = camId }
    }

    companion object {
        /** The per-profile dynamic key (D-06) — `preferred_cam_<profileId>` (the ThemePrefs dynamic-key idiom). */
        fun preferredCamKey(profileId: String) = stringPreferencesKey("preferred_cam_$profileId")
    }
}
