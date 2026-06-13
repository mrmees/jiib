package works.mees.dinghy.ui.move

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
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Host-side proof of the [SavedLocationPrefs] persistence contract.
 *
 * HOST CONSTRAINT (mirrors ConnectionStoreTest / MacroPrefsTest): back-to-back DataStore writes
 * on one temp-file store are NOT reliably host-testable on Windows (atomic `.tmp`→final rename
 * races). Multi-write tests therefore use an in-memory DataStore (the DisplayPrefsTest MemDataStore
 * pattern); single-write round-trip uses a real temp-file store on an IO scope.
 */
class SavedLocationPrefsTest {

    private val tmpFiles = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        tmpFiles.forEach { it.delete() }
    }

    /** Real temp-file DataStore on a dedicated IO scope — for single-write round-trip proof. */
    private fun newFileStore(): Pair<SavedLocationPrefs, CoroutineScope> {
        val file = File.createTempFile("savedlocations_test_", ".preferences_pb").also {
            it.delete()
            tmpFiles += it
        }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += ioScope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = ioScope) { file }
        return SavedLocationPrefs(dataStore) to ioScope
    }

    /** In-memory DataStore for multi-write tests (sidesteps the Windows .tmp→rename race). */
    private fun newMemPrefs(): SavedLocationPrefs {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        val mem = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = state
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val next = transform(state.value)
                state.value = next
                return next
            }
        }
        return SavedLocationPrefs(mem)
    }

    private suspend fun settle() {
        yield(); Thread.sleep(60); System.gc(); Thread.sleep(60); yield()
    }

    @Test
    fun add_listsLocation_withAndWithoutZ() = runTest {
        val prefs = newMemPrefs()
        prefs.add(SavedLocation("center", 175.0, 175.0, 5.0))
        prefs.add(SavedLocation("purge", -2.0, 10.0, null))
        val all = prefs.locations.first()
        assertEquals(2, all.size)
        assertEquals(5.0, all.first { it.name == "center" }.z)
        assertNull(all.first { it.name == "purge" }.z)
    }

    @Test
    fun remove_dropsByName() = runTest {
        val prefs = newMemPrefs()
        prefs.add(SavedLocation("a", 1.0, 2.0, null))
        prefs.add(SavedLocation("b", 3.0, 4.0, null))
        prefs.remove("a")
        assertEquals(listOf("b"), prefs.locations.first().map { it.name })
    }

    @Test
    fun add_sameName_replaces() = runTest {
        val prefs = newMemPrefs()
        prefs.add(SavedLocation("home", 1.0, 1.0, null))
        prefs.add(SavedLocation("home", 9.0, 9.0, 2.0))
        val all = prefs.locations.first()
        assertEquals(1, all.size)
        assertEquals(9.0, all.first().x, 0.0)
    }

    /** Single-write round-trip on a real file store proves the full encode→decode path on disk. */
    @Test
    fun singleAdd_roundTripsOnDisk() = runTest {
        val (prefs, ioScope) = newFileStore()
        withContext(ioScope.coroutineContext) {
            prefs.add(SavedLocation("probe", 5.0, 5.0, 0.5))
            settle()
            val all = prefs.locations.first()
            assertEquals(1, all.size)
            assertEquals("probe", all.first().name)
            assertEquals(0.5, all.first().z)
        }
    }

    @Test
    fun emptyStore_emitsEmptyList() = runTest {
        val (prefs, ioScope) = newFileStore()
        withContext(ioScope.coroutineContext) {
            assertEquals(emptyList<SavedLocation>(), prefs.locations.first())
        }
    }
}
