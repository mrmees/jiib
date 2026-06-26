package works.mees.jiib.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Host-side proof of the [HeatPresetPrefs] persistence contract.
 *
 * HOST CONSTRAINT (mirrors SavedLocationPrefsTest): back-to-back DataStore writes on one temp-file
 * store are NOT reliably host-testable on Windows (atomic `.tmp`→final rename races). Multi-write
 * tests use an in-memory DataStore; single-write round-trip uses a real temp-file store on an IO scope.
 */
class HeatPresetPrefsTest {
    private val tmpFiles = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After fun tearDown() { scopes.forEach { it.cancel() }; tmpFiles.forEach { it.delete() } }

    /** Real temp-file DataStore on a dedicated IO scope — for single-write round-trip proof. */
    private fun newFileStore(): Pair<HeatPresetPrefs, CoroutineScope> {
        val file = File.createTempFile("heatpreset_test_", ".preferences_pb").also { it.delete(); tmpFiles += it }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO); scopes += ioScope
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = ioScope) { file }
        return HeatPresetPrefs(ds) to ioScope
    }

    /** In-memory DataStore for multi-write tests (sidesteps the Windows .tmp→rename race). */
    private fun newMemPrefs(): HeatPresetPrefs {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        val mem = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = state
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val next = transform(state.value)
                state.value = next
                return next
            }
        }
        return HeatPresetPrefs(mem)
    }

    private suspend fun settle() { yield(); Thread.sleep(60); System.gc(); Thread.sleep(60); yield() }

    @Test fun addOrUpdate_roundTrips_andSorts() = runTest {
        val prefs = newMemPrefs()
        prefs.addOrUpdate("p1", HeatPreset("b", "High", mapOf("extruder" to 230)))
        prefs.addOrUpdate("p1", HeatPreset("a", "Low", mapOf("extruder" to 150)))
        assertEquals(listOf("Low", "High"), prefs.presets("p1").first().map { it.name })
    }

    @Test fun addOrUpdate_replacesById() = runTest {
        val prefs = newMemPrefs()
        prefs.addOrUpdate("p1", HeatPreset("a", "Low", mapOf("extruder" to 150)))
        prefs.addOrUpdate("p1", HeatPreset("a", "Low+", mapOf("extruder" to 160)))
        val list = prefs.presets("p1").first()
        assertEquals(1, list.size)
        assertEquals("Low+", list[0].name)
        assertEquals(160, list[0].extruderTemp)
    }

    @Test fun delete_isIdempotent() = runTest {
        val prefs = newMemPrefs()
        prefs.addOrUpdate("p1", HeatPreset("a", "Low", mapOf("extruder" to 150)))
        prefs.delete("p1", "a"); prefs.delete("p1", "a")
        assertEquals(emptyList<String>(), prefs.presets("p1").first().map { it.name })
    }

    @Test fun seedIfEmpty_onlyWhenEmpty() = runTest {
        val prefs = newMemPrefs()
        prefs.seedIfEmpty("p1", defaultHeatPresets())
        assertEquals(listOf("Low", "Medium", "High"), prefs.presets("p1").first().map { it.name })
        // Delete one then seed again — must NOT re-seed (store is non-empty).
        prefs.delete("p1", prefs.presets("p1").first().first().id)
        prefs.seedIfEmpty("p1", defaultHeatPresets())
        assertEquals(2, prefs.presets("p1").first().size)
    }

    @Test fun perProfileIsolation() = runTest {
        val prefs = newMemPrefs()
        prefs.addOrUpdate("p1", HeatPreset("a", "Low", mapOf("extruder" to 150)))
        assertEquals(emptyList<String>(), prefs.presets("p2").first().map { it.name })
    }

    /** Single-write round-trip on a real file store proves the full encode→decode path on disk. */
    @Test fun singleAdd_roundTripsOnDisk() = runTest {
        val (prefs, ioScope) = newFileStore()
        withContext(ioScope.coroutineContext) {
            prefs.addOrUpdate("p1", HeatPreset("a", "Low", mapOf("extruder" to 150, "heater_bed" to 50)))
            settle()
            val list = prefs.presets("p1").first()
            assertEquals(1, list.size)
            assertEquals("Low", list[0].name)
            assertEquals(150, list[0].extruderTemp)
        }
    }
}
