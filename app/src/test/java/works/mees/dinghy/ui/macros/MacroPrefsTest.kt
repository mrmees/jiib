package works.mees.dinghy.ui.macros

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 * Wave-0 RED scaffold (08-01) — turned GREEN by 08-03 (`MacroPrefs`).
 *
 * REQ-MACRO-03. DataStore prefs round-trip for macro bookmarks (`Set<String>`) + `revealHidden`.
 *
 * HOST CONSTRAINT (per STATE.md 04-01 ConnectionStore precedent): back-to-back DataStore writes /
 * a second instance to one `.preferences_pb` are NOT reliably host-testable on the Windows build host
 * (atomic `.tmp`→final rename races). So — like ConnectionStoreTest — this proves the SINGLE-write +
 * read path and the fail-safe default read; multi-write atomicity is deferred to on-device.
 *
 * Production symbols referenced (NOT YET BUILT → RED): [MacroPrefs] + `bookmarks`/`revealHidden`
 * flows, `addBookmark`/`setRevealHidden` writes.
 */
class MacroPrefsTest {

    private val tmpFiles = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        tmpFiles.forEach { it.delete() }
    }

    private fun newPrefs(): Pair<MacroPrefs, CoroutineScope> {
        val file = File.createTempFile("macros_test_", ".preferences_pb").also {
            it.delete()
            tmpFiles += it
        }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += ioScope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = ioScope) { file }
        return MacroPrefs(dataStore) to ioScope
    }

    private suspend fun settle() {
        yield(); Thread.sleep(60); System.gc(); Thread.sleep(60); yield()
    }

    @Test
    fun emptyStore_defaults() = runTest {
        val (prefs, ioScope) = newPrefs()
        withContext(ioScope.coroutineContext) {
            assertEquals(emptySet<String>(), prefs.bookmarks.first())
            assertFalse(prefs.revealHidden.first())
        }
    }

    @Test
    fun addBookmark_roundTrips() = runTest {
        val (prefs, ioScope) = newPrefs()
        withContext(ioScope.coroutineContext) {
            prefs.addBookmark("START_PRINT")
            settle()
            assertTrue(prefs.bookmarks.first().contains("START_PRINT"))
        }
    }

    @Test
    fun setRevealHidden_roundTrips() = runTest {
        val (prefs, ioScope) = newPrefs()
        withContext(ioScope.coroutineContext) {
            prefs.setRevealHidden(true)
            settle()
            assertTrue(prefs.revealHidden.first())
        }
    }
}
