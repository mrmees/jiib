package works.mees.dinghy.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Wave-0 RED scaffold (16-01) — turned GREEN by 16-05 (`BabystepPrefs`).
 *
 * Mirrors the [works.mees.dinghy.ui.macros.MacroPrefsTest] analog: a host JUnit test over a
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
    fun emptyStore_defaultsEnabledTrueLayersFive() {
        val (dataStore, _) = newDataStore()
        // Keep the harness load-bearing so the DataStore wiring compile-proves.
        require(dataStore != null)
        // EXPECT (16-05): BabystepPrefs(dataStore).enabled.first() == true
        // EXPECT (16-05): BabystepPrefs(dataStore).layerCount.first() == 5
        fail("not yet implemented — 16-05 BabystepPrefs (defaults enabled=true / layers=5)")
    }

    @Test
    fun ioExceptionRead_failsSafeToDefaults() {
        // EXPECT (16-05): a DataStore read that throws IOException is caught and yields the defaults
        // (enabled=true / layers=5), NOT a propagated exception (the MacroPrefs fail-safe-read pattern).
        fail("not yet implemented — 16-05 BabystepPrefs (IOException -> fail-safe defaults)")
    }

    @Test
    fun setEnabledFalse_roundTrips() {
        val (dataStore, _) = newDataStore()
        require(dataStore != null)
        // EXPECT (16-05): after setEnabled(false), enabled.first() == false
        fail("not yet implemented — 16-05 BabystepPrefs (setEnabled write-through)")
    }

    @Test
    fun setLayerCount_roundTrips() {
        val (dataStore, _) = newDataStore()
        require(dataStore != null)
        // EXPECT (16-05): after setLayerCount(3), layerCount.first() == 3
        fail("not yet implemented — 16-05 BabystepPrefs (setLayerCount write-through)")
    }

    @Test
    fun setLayerCountZero_coercesToAtLeastOne() {
        val (dataStore, _) = newDataStore()
        require(dataStore != null)
        // EXPECT (16-05): setLayerCount(0) coerces to >= 1 (never a zero-layer window).
        fail("not yet implemented — 16-05 BabystepPrefs (setLayerCount coerce >= 1)")
    }
}
