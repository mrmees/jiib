package works.mees.jiib.ui.extrude

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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task 4 (Extrude rework) — DataStore prefs round-trip for the EXTRUDE-screen-scoped pinned filament
 * macro NAMEs ([ExtrudeMacroPrefs.pins], a `Set<String>`), independent of the global Macros bookmarks.
 *
 * The default-read + add-round-trip harness is copied verbatim from
 * [works.mees.jiib.ui.macros.MacroPrefsTest] (the tmpFiles/scopes fields, @After tearDown,
 * newPrefs() over PreferenceDataStoreFactory.create, and settle()).
 *
 * HOST CONSTRAINT (per the MacroPrefsTest docblock): back-to-back DataStore writes to ONE real
 * `.preferences_pb` are NOT reliably testable on the Windows build host — the atomic `.tmp`→final
 * rename over an already-written file fails and DataStore surfaces it as the misleading "multiple
 * instances of DataStore" IOException; MacroPrefsTest defers multi-write atomicity to on-device for
 * exactly this reason. So the TOGGLE-OFF (set-removal) branch — which inherently needs a second write
 * over a non-empty file — is proven over a controllable in-memory DataStore (the same technique
 * [works.mees.jiib.theme.ThemeOverrideTest] uses), which round-trips writes with no file rename.
 */
class ExtrudeMacroPrefsTest {

    private val tmpFiles = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        tmpFiles.forEach { it.delete() }
    }

    private fun newPrefs(): Pair<ExtrudeMacroPrefs, CoroutineScope> {
        val file = File.createTempFile("extrude_macros_test_", ".preferences_pb").also {
            it.delete()
            tmpFiles += it
        }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += ioScope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = ioScope) { file }
        return ExtrudeMacroPrefs(dataStore) to ioScope
    }

    private suspend fun settle() {
        yield(); Thread.sleep(60); System.gc(); Thread.sleep(60); yield()
    }

    /** A controllable in-memory DataStore — `updateData` mutates the held snapshot and re-emits. No file. */
    private class MemDataStore(seed: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(seed)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    @Test
    fun emptyByDefault() = runTest {
        val (prefs, io) = newPrefs()
        withContext(io.coroutineContext) { assertEquals(emptySet<String>(), prefs.pins.first()) }
    }

    /** Real-file single-write round-trip (the MacroPrefsTest-proven host-safe pattern): toggle ON adds. */
    @Test
    fun toggleOnAddsAndPersists() = runTest {
        val (prefs, io) = newPrefs()
        withContext(io.coroutineContext) {
            prefs.togglePin("PURGE"); settle()
            assertTrue(prefs.pins.first().contains("PURGE"))
        }
    }

    /**
     * Full toggle round-trip over an in-memory store (no file rename → safe for two back-to-back writes
     * on the Windows host): ON adds "PURGE", a second toggle OFF removes it.
     */
    @Test
    fun toggleRoundTrips() = runTest {
        val prefs = ExtrudeMacroPrefs(MemDataStore())
        prefs.togglePin("PURGE")
        assertTrue(prefs.pins.first().contains("PURGE"))
        prefs.togglePin("PURGE")
        assertFalse(prefs.pins.first().contains("PURGE"))
    }
}
