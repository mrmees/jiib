package works.mees.jiib.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Wave-0 RED scaffold (16-01) — turned GREEN by 16-05 (`BabystepPrefs`).
 *
 * Mirrors the [works.mees.jiib.ui.macros.MacroPrefsTest] analog: a host JUnit test over a
 * temp/in-memory `DataStore<Preferences>` that proves the BabystepPrefs persistence CONTRACT:
 *
 *   - default `enabled == true`
 *   - default `layerCount == 5`
 *   - an IOException-throwing read fails SAFE to the defaults (enabled=true / layers=5), it does NOT
 *     propagate the exception
 *   - `setEnabled(false)` then read flows `enabled == false`
 *   - `setLayerCount(3)` then read flows `layerCount == 3`
 *   - `setLayerCount(0)` coerces to >= 1
 *
 * This unit-gates the project's #1 recurring trap ([[dinghy-compose-write-scope-cancellation]]): 16-05
 * must persist through a process-lifetime scope + a read-modify-write inside ONE `dataStore.edit`, and
 * this test proves the write-through round-trips rather than relying on `assembleDebug`.
 *
 * RED discipline ([[dinghy-wave0-red-scaffold-compile]]): `BabystepPrefs` does NOT exist yet (it lands
 * in 16-05). The DataStore harness below is copied VERBATIM from MacroPrefsTest (it already compiles)
 * so 16-05 can swap in real `BabystepPrefs(dataStore)` assertions; every test body is a
 * `fail("not yet implemented — 16-05 BabystepPrefs")` so the file compiles day-one but is RED.
 */
class BabystepPrefsTest {

    private val tmpFiles = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        tmpFiles.forEach { it.delete() }
    }

    /** Verbatim MacroPrefsTest harness — a fresh temp `.preferences_pb` on a process-lifetime IO scope. */
    private fun newDataStore(): Pair<DataStore<Preferences>, CoroutineScope> {
        val file = File.createTempFile("babystep_test_", ".preferences_pb").also {
            it.delete()
            tmpFiles += it
        }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += ioScope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = ioScope) { file }
        return dataStore to ioScope
    }

    @Test
    fun emptyStore_defaultsEnabledTrueLayersFive() = runBlocking {
        val (dataStore, _) = newDataStore()
        val prefs = BabystepPrefs(dataStore)
        assertTrue("default enabled is true", prefs.enabled.first())
        assertEquals("default layerCount is 5", 5, prefs.layerCount.first())
    }

    @Test
    fun ioExceptionRead_failsSafeToDefaults() = runBlocking {
        // A DataStore whose read throws IOException — the MacroPrefs fail-safe-read pattern must catch it
        // and yield the defaults (enabled=true / layers=5), NOT propagate the exception.
        val throwing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("corrupt blob") }
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences = throw IOException("corrupt blob")
        }
        val prefs = BabystepPrefs(throwing)
        assertTrue("IOException read fails safe to enabled=true", prefs.enabled.first())
        assertEquals("IOException read fails safe to layers=5", 5, prefs.layerCount.first())
    }

    @Test
    fun setEnabledFalse_roundTrips() = runBlocking {
        val (dataStore, _) = newDataStore()
        val prefs = BabystepPrefs(dataStore)
        prefs.setEnabled(false)
        assertFalse("setEnabled(false) round-trips", prefs.enabled.first())
    }

    @Test
    fun setLayerCount_roundTrips() = runBlocking {
        val (dataStore, _) = newDataStore()
        val prefs = BabystepPrefs(dataStore)
        prefs.setLayerCount(3)
        assertEquals("setLayerCount(3) round-trips", 3, prefs.layerCount.first())
    }

    @Test
    fun setLayerCountZero_coercesToAtLeastOne() = runBlocking {
        val (dataStore, _) = newDataStore()
        val prefs = BabystepPrefs(dataStore)
        prefs.setLayerCount(0)
        assertEquals("setLayerCount(0) coerces to >= 1", 1, prefs.layerCount.first())
    }
}
