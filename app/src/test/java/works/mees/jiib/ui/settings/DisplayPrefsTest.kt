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
        // TWO writes on one store: the temp-FILE harness hits the Windows-host .tmp→rename race on a
        // second write over an existing blob (the documented back-to-back host race — see
        // ProfileStoreTest "use two separate stores to avoid the back-to-back host race"). The contract
        // under test is DisplayPrefs KEY semantics (a true write overrides a persisted false), not
        // FileStorage rename behavior — so use the in-memory DataStore (the ThemeOverrideTest
        // MemDataStore shape), which the single-write tests above complement on the real file path.
        val mem = object : DataStore<Preferences> {
            private val state = kotlinx.coroutines.flow.MutableStateFlow<Preferences>(
                androidx.datastore.preferences.core.emptyPreferences(),
            )
            override val data: Flow<Preferences> = state
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences {
                val next = transform(state.value)
                state.value = next
                return next
            }
        }
        val prefs = DisplayPrefs(mem)
        prefs.setKeepScreenOn(false)
        assertFalse("intermediate false state persisted", prefs.keepScreenOn.first())
        prefs.setKeepScreenOn(true)
        assertTrue("setKeepScreenOn(true) after false round-trips", prefs.keepScreenOn.first())
    }

    @Test
    fun emptyStore_defaultsWebcamEnabledFalse() = runBlocking {
        // Webcam ships DEFAULT-OFF (2026-07-05) until the rotation/stability issues are fixed — the
        // user opts in per-install via App Settings rather than being handed a known-flaky feature.
        val (dataStore, _) = newDataStore()
        val prefs = DisplayPrefs(dataStore)
        assertFalse("default webcamEnabled is false", prefs.webcamEnabled.first())
    }

    @Test
    fun setWebcamEnabledFalse_roundTrips() = runBlocking {
        val (dataStore, _) = newDataStore()
        val prefs = DisplayPrefs(dataStore)
        prefs.setWebcamEnabled(false)
        assertFalse("setWebcamEnabled(false) round-trips", prefs.webcamEnabled.first())
    }

    @Test
    fun setWebcamEnabledTrueAfterFalse_roundTrips() = runBlocking {
        // Two writes on one store → use the in-memory DataStore (the keepScreenOn two-write test's
        // shape) to dodge the Windows .tmp→rename race; the single-write tests above cover the file path.
        val mem = object : DataStore<Preferences> {
            private val state = kotlinx.coroutines.flow.MutableStateFlow<Preferences>(
                androidx.datastore.preferences.core.emptyPreferences(),
            )
            override val data: Flow<Preferences> = state
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences {
                val next = transform(state.value); state.value = next; return next
            }
        }
        val prefs = DisplayPrefs(mem)
        prefs.setWebcamEnabled(false)
        assertFalse("intermediate false state persisted", prefs.webcamEnabled.first())
        prefs.setWebcamEnabled(true)
        assertTrue("setWebcamEnabled(true) after false round-trips", prefs.webcamEnabled.first())
    }
}
