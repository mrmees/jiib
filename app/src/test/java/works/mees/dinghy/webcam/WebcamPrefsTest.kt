package works.mees.dinghy.webcam

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
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.ui.webcam.WebcamPrefs
import java.io.File

/**
 * Per-printer preferred-cam DataStore round-trip (CAM-01, D-10) — the [WebcamPrefs] store.
 *
 * HOST CONSTRAINT (the MacroPrefs/ConnectionStore precedent, STATE.md 04-01): back-to-back DataStore
 * writes / a second instance to one `.preferences_pb` are NOT reliably host-testable on the Windows
 * build host (atomic `.tmp`→final rename races). So — like MacroPrefsTest — this proves the SINGLE-write
 * + read round-trip, the per-HOST keying (two hosts keep independent cams), and the fail-safe default
 * read; multi-write atomicity is deferred to on-device.
 */
class WebcamPrefsTest {

    private val tmpFiles = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        tmpFiles.forEach { it.delete() }
    }

    private fun newPrefs(): Pair<WebcamPrefs, CoroutineScope> {
        val file = File.createTempFile("webcam_test_", ".preferences_pb").also {
            it.delete()
            tmpFiles += it
        }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += ioScope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = ioScope) { file }
        return WebcamPrefs(dataStore) to ioScope
    }

    private suspend fun settle() {
        yield(); Thread.sleep(60); System.gc(); Thread.sleep(60); yield()
    }

    @Test
    fun emptyStore_noPreferredCam() = runTest {
        val (prefs, ioScope) = newPrefs()
        withContext(ioScope.coroutineContext) {
            // No saved preference → null (the holder falls to first-in-list, D-10).
            assertNull(prefs.preferredCam("192.168.1.120").first())
        }
    }

    @Test
    fun setPreferredCam_roundTrips() = runTest {
        val (prefs, ioScope) = newPrefs()
        withContext(ioScope.coroutineContext) {
            prefs.setPreferredCam("192.168.1.120", "cam-uid-2")
            settle()
            assertEquals("cam-uid-2", prefs.preferredCam("192.168.1.120").first())
        }
    }

    @Test
    fun preferredCam_isKeyedPerHost() = runTest {
        val (prefs, ioScope) = newPrefs()
        withContext(ioScope.coroutineContext) {
            // D-10: keyed by printer/connection — a cam saved for one host must NOT leak to another.
            prefs.setPreferredCam("192.168.1.120", "e5-cam")
            settle()
            assertEquals("e5-cam", prefs.preferredCam("192.168.1.120").first())
            assertNull(prefs.preferredCam("192.168.1.121").first())
        }
    }
}
