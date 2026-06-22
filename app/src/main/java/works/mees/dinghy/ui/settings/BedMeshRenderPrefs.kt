package works.mees.dinghy.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import works.mees.dinghy.calibration.BedMeshViewType
import java.io.IOException

/**
 * Per-printer bed-mesh RENDER preferences (the 14th DataStore): view-type + High/Low ramp-color
 * selectors, keyed by profileId (like [TraceStylePrefs]). Colors store a SLOT SELECTOR, never ARGB
 * (an index survives theme changes; an ARGB would freeze the old color). Selector: -1 = Accent
 * sentinel; 0..3 = data-pool slot. Defaults: view HEATMAP, high -1 (accent), low 0 (pool[0]).
 */
class BedMeshRenderPrefs(private val dataStore: DataStore<Preferences>) {
    private fun viewKey(pid: String) = stringPreferencesKey("bm_view_$pid")
    private fun highKey(pid: String) = intPreferencesKey("bm_high_$pid")
    private fun lowKey(pid: String) = intPreferencesKey("bm_low_$pid")

    fun viewType(pid: String): Flow<BedMeshViewType> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            when (prefs[viewKey(pid)]) {
                BedMeshViewType.PROBE_POINTS.name -> BedMeshViewType.PROBE_POINTS
                else -> BedMeshViewType.HEATMAP
            }
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
        const val ACCENT_SEL = -1
        const val DEFAULT_HIGH = ACCENT_SEL // accent == seriesColor(0)
        const val DEFAULT_LOW = 0           // pool slot 0 == seriesColor(1) in Colorful
    }
}
