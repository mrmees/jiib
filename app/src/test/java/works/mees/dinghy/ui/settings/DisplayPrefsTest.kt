package works.mees.dinghy.ui.settings

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Host-side proof of the [DisplayPrefs] persistence contract (§R2, 26.5-05) — mirrors
 * [BabystepPrefsTest] (the verbatim temp-file DataStore harness):
 *
 *   - default `keepScreenOn == true` (§R2 default-ON for the dedicated-display use case)
 *   - an IOException-throwing read fails SAFE to the default (true), it does NOT propagate
 *   - `setKeepScreenOn(false)` then read flows `false`
 *   - `setKeepScreenOn(true)` after `false` flows `true` (full round-trip both ways)
 *
 * This unit-gates the project's #1 recurring trap ([[dinghy-compose-write-scope-cancellation]]):
 * the write path must persist through a process-lifetime scope + a read-modify-write inside ONE
 * `dataStore.edit`, and this test proves the write-through round-trips on a real (temp-file)
 * DataStore rather than relying on `assembleDebug`.
 */
class DisplayPrefsTest {

    private val tmpFiles = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        tmpFiles.forEach { it.delete() }
    }

    /** Verbatim BabystepPrefsTest harness — a fresh temp `.preferences_pb` on an IO scope. */
    private fun newDataStore(): Pair<DataStore<Preferences>, CoroutineScope> {
        val file = File.createTempFile("display_test_", ".preferences_pb").also {
            it.delete()
            tmpFiles += it
        }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += ioScope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = ioScope) { file }
        return dataStore to ioScope
    }

    @Test
    fun emptyStore_defaultsKeepScreenOnTrue() = runBlocking {
        val (dataStore, _) = newDataStore()
        val prefs = DisplayPrefs(dataStore)
        assertTrue("default keepScreenOn is true (§R2 dedicated-display)", prefs.keepScreenOn.first())
    }

    @Test
    fun ioExceptionRead_failsSafeToDefaultTrue() = runBlocking {
        // A DataStore whose read throws IOException — the fail-safe-read pattern must catch it and
        // yield the default (keepScreenOn=true), NOT propagate the exception.
        val throwing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("corrupt blob") }
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences = throw IOException("corrupt blob")
        }
        val prefs = DisplayPrefs(throwing)
        assertTrue("IOException read fails safe to keepScreenOn=true", prefs.keepScreenOn.first())
    }

    @Test
    fun setKeepScreenOnFalse_roundTrips() = runBlocking {
        val (dataStore, _) = newDataStore()
        val prefs = DisplayPrefs(dataStore)
        prefs.setKeepScreenOn(false)
        assertFalse("setKeepScreenOn(false) round-trips", prefs.keepScreenOn.first())
    }

    @Test
    fun setKeepScreenOnTrueAfterFalse_roundTrips() = runBlocking {
        val (dataStore, _) = newDataStore()
        val prefs = DisplayPrefs(dataStore)
        prefs.setKeepScreenOn(false)
        prefs.setKeepScreenOn(true)
        assertTrue("setKeepScreenOn(true) after false round-trips", prefs.keepScreenOn.first())
    }
}
