package works.mees.dinghy.ui.settings

import androidx.datastore.core.DataStore
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Host-side proof of the [TraceStylePrefs] selectedSensors persistence contract.
 *
 * Mirrors the [BabystepPrefsTest] / [DisplayPrefsTest] harness:
 *   - Single-write tests use the real temp-file DataStore (proves the flat-key encoding).
 *   - Multi-write tests use an in-memory DataStore to sidestep the Windows-host back-to-back
 *     rename race (`File.renameTo` fails if destination exists; see [DisplayPrefsTest] comment).
 *
 * Contract covered:
 *   - absent key == not selected (sparse store)
 *   - `setSensorSelected(name, true)` adds the sensor to [TraceStylePrefs.selectedSensors]
 *   - `setSensorSelected(name, false)` removes the key (sparse deselect)
 *   - multiple selections accumulate correctly
 */
class TraceStylePrefsSelectionTest {

    private val tmpFiles = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        tmpFiles.forEach { it.delete() }
    }

    /** Verbatim BabystepPrefsTest harness — fresh temp `.preferences_pb` on a process-lifetime IO scope. */
    private fun newDataStore(): Pair<DataStore<Preferences>, CoroutineScope> {
        val file = File.createTempFile("tracesel_test_", ".preferences_pb").also {
            it.delete()
            tmpFiles += it
        }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += ioScope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = ioScope) { file }
        return dataStore to ioScope
    }

    /**
     * In-memory DataStore (verbatim DisplayPrefsTest MemDataStore shape) for multi-write tests.
     * Sidesteps the Windows-host back-to-back .tmp→rename race that breaks File.renameTo() when
     * the destination already exists — see DisplayPrefsTest.setKeepScreenOnTrueAfterFalse_roundTrips.
     */
    private fun memDataStore(): DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        return object : DataStore<Preferences> {
            override val data: Flow<Preferences> = state
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences {
                val next = transform(state.value)
                state.value = next
                return next
            }
        }
    }

    @Test
    fun emptyStore_selectedSensorsIsEmpty() = runBlocking {
        val (dataStore, _) = newDataStore()
        val prefs = TraceStylePrefs(dataStore)
        assertTrue("empty by default", prefs.selectedSensors.first().isEmpty())
    }

    @Test
    fun setSensorSelected_true_roundTrips() = runBlocking {
        // Single write — real file DataStore proves key encoding on disk.
        val (dataStore, _) = newDataStore()
        val prefs = TraceStylePrefs(dataStore)
        prefs.setSensorSelected("temperature_sensor chamber", true)
        assertEquals(setOf("temperature_sensor chamber"), prefs.selectedSensors.first())
    }

    @Test
    fun setSensorSelected_false_removesKey() = runBlocking {
        // Two writes on one store — use in-memory DataStore (Windows rename race; see class doc).
        val prefs = TraceStylePrefs(memDataStore())
        prefs.setSensorSelected("temperature_sensor chamber", true)
        assertEquals(setOf("temperature_sensor chamber"), prefs.selectedSensors.first())
        prefs.setSensorSelected("temperature_sensor chamber", false)
        assertTrue("deselect removes the key", prefs.selectedSensors.first().isEmpty())
    }

    @Test
    fun multipleSelectionsAccumulate() = runBlocking {
        // Two writes on one store — use in-memory DataStore (Windows rename race; see class doc).
        val prefs = TraceStylePrefs(memDataStore())
        prefs.setSensorSelected("temperature_sensor chamber", true)
        prefs.setSensorSelected("temperature_sensor mcu", true)
        assertEquals(
            setOf("temperature_sensor chamber", "temperature_sensor mcu"),
            prefs.selectedSensors.first(),
        )
    }
}
