package works.mees.dinghy.theme

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 15.2-01 Task 4 — the APP-GLOBAL dev-widget enable boolean (D-08). Proves the [ThemePrefs.devEnableFlow]
 * defaults FALSE on a fresh store, round-trips TRUE after [ThemePrefs.setDevEnable], and is app-global
 * (NOT profile-keyed) + a runtime flag (NOT BuildConfig.DEBUG — that is structural, asserted by grep in
 * the plan's acceptance criteria).
 *
 * HOST CONSTRAINT (per MacroPrefsTest / ConnectionStore precedent): proves the single-write + fail-safe
 * default read path; the `.preferences_pb` atomic-rename race makes back-to-back writes unreliable on the
 * Windows build host.
 */
class DevWidgetEnableTest {

    private val tmpFiles = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        tmpFiles.forEach { it.delete() }
    }

    private fun newPrefs(): Pair<ThemePrefs, CoroutineScope> {
        val file = File.createTempFile("theme_devenable_test_", ".preferences_pb").also {
            it.delete()
            tmpFiles += it
        }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += ioScope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = ioScope) { file }
        return ThemePrefs(dataStore) to ioScope
    }

    private suspend fun settle() {
        yield(); Thread.sleep(60); System.gc(); Thread.sleep(60); yield()
    }

    @Test
    fun dev_enable_defaults_false_persists_app_global() = runTest {
        val (prefs, ioScope) = newPrefs()
        withContext(ioScope.coroutineContext) {
            // Fresh store → default FALSE (a normal user never sees the dev widgets).
            assertFalse(prefs.devEnableFlow.first())

            // Round-trips TRUE through the app-global key (single write + read).
            prefs.setDevEnable(true)
            settle()
            assertTrue(prefs.devEnableFlow.first())
        }
    }
}
