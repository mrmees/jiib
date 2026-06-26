package works.mees.jiib.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import works.mees.jiib.calibration.BedMeshViewType
import java.io.IOException

/**
 * Per-printer bed-mesh RENDER preferences (the 14th DataStore): view-type + High/Low ramp-color
 * selectors, keyed by profileId (like [TraceStylePrefs]). Colors store a SLOT SELECTOR, never ARGB
 * (an index survives theme changes; an ARGB would freeze the old color). Selector: 0..7 = index into
 * bedMeshPoolColors (intents 0..3 = accent/stop/heat/go, data pool 4..7). Defaults: view HEATMAP,
 * high 0 (accent), low 4 (pool[0]).
 */
class BedMeshRenderPrefs(private val dataStore: DataStore<Preferences>) {
    private fun viewKey(pid: String) = stringPreferencesKey("bm_view_$pid")
    // v2 suffix: round-1 picks used 0..3 = pool slots; now 0..7 = intent + pool (accent/stop/heat/go/pool0..3).
    // Old keys are abandoned so stale values fall through to DEFAULT_HIGH/DEFAULT_LOW.
    private fun highKey(pid: String) = intPreferencesKey("bm_high_v2_$pid")
    private fun lowKey(pid: String) = intPreferencesKey("bm_low_v2_$pid")

    fun viewType(pid: String): Flow<BedMeshViewType> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val stored = prefs[viewKey(pid)]
            BedMeshViewType.entries.firstOrNull { it.name == stored } ?: BedMeshViewType.HEATMAP
        }

    fun highColorSel(pid: String): Flow<Int> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[highKey(pid)] ?: DEFAULT_HIGH }

    fun lowColorSel(pid: String): Flow<Int> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[lowKey(pid)] ?: DEFAULT_LOW }

    suspend fun setViewType(pid: String, v: BedMeshViewType) =
        dataStore.edit { it[viewKey(pid)] = v.name }
    suspend fun setHighColorSel(pid: String, sel: Int) =
        dataStore.edit { it[highKey(pid)] = sel }
    suspend fun setLowColorSel(pid: String, sel: Int) =
        dataStore.edit { it[lowKey(pid)] = sel }

    companion object {
        const val DEFAULT_HIGH = 0  // accent (index 0 in bedMeshPoolColors)
        const val DEFAULT_LOW = 4   // pool[0] (index 4 in bedMeshPoolColors)
    }
}
