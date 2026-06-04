package works.mees.dinghy.ui.webcam

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
 * SHAPE (the [works.mees.dinghy.ui.macros.MacroPrefs] precedent, copied verbatim): the [DataStore] is
 * INJECTED (no `preferencesDataStore` delegate) so it is host-testable; the PRODUCTION instance is its
 * OWN `webcam.preferences_pb` file (NOT shared with theme/connection/macros), created by
 * [works.mees.dinghy.DinghyApp] and exposed via [works.mees.dinghy.di.AppContainer.webcamPrefs]. Like
 * MacroPrefs it is PROCESS-SCOPED + CONNECTION-INDEPENDENT — the saved preference survives reconnects
 * and printer swaps.
 *
 * PER-PRINTER KEY (D-10 "keyed by printer/connection") — the [works.mees.dinghy.theme.ThemePrefs]
 * dynamic-string-key idiom: the preferred-cam value is stored under `preferred_cam_<host>`, derived from
 * the active [works.mees.dinghy.config.ConnectionConfig.host]. Two printers therefore keep INDEPENDENT
 * preferred cams; switching printers reads that printer's own saved cam (or none → first-in-list).
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
     * The preferred (last-viewed) cam identity for [host], or `null` when none is saved (→ first-in-list).
     * Fail-safe: a read error yields `null`, never throws.
     */
    fun preferredCam(host: String): Flow<String?> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[preferredCamKey(host)] }

    /**
     * Persist [camId] as the preferred (last-viewed) cam for [host] (D-10). Called by the holder when a
     * cam is selected/cycled — the only writer (there is no separate "set as default" UI).
     */
    suspend fun setPreferredCam(host: String, camId: String) {
        dataStore.edit { prefs -> prefs[preferredCamKey(host)] = camId }
    }

    companion object {
        /** The per-printer dynamic key (D-10) — `preferred_cam_<host>` (the ThemePrefs dynamic-key idiom). */
        fun preferredCamKey(host: String) = stringPreferencesKey("preferred_cam_$host")
    }
}
