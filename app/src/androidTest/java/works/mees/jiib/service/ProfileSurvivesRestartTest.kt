package works.mees.jiib.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import works.mees.jiib.config.Profile
import works.mees.jiib.config.ProfileStore
import java.io.File

/**
 * Instrumented proof of the active-profile survival-across-process-death gate (MULTI-01, D-02/SC-3):
 * the persisted `active_id` written to a REAL `profiles.preferences_pb`-shaped DataStore file survives a
 * cold re-read and lands on the RIGHT printer. This MUST be instrumented (NOT a host unit test):
 * 14-VALIDATION §"Where a Fake would LIE" point 1 — DataStore persistence is NOT host-faithful on the
 * Windows build host; a `FakeProfileStore` round-trips in memory and would lie about cross-process survival.
 *
 * Mirrors [ServiceSurvivesRotationTest]'s real-store discipline: a REAL [PreferenceDataStoreFactory.create]
 * over a real file in the instrumentation target context's `datastore/` dir (same `produceFile` shape as
 * [works.mees.jiib.JiibApp]'s `profiles.preferences_pb`). "Process death + cold start" is simulated by
 * CANCELLING the first store's scope (DataStore is single-writer per file — releasing the file lock) and
 * then constructing a SECOND [ProfileStore] over a FRESH [DataStore] instance reading the SAME persisted
 * bytes off disk.
 *
 * All coroutine waits are bounded with [withTimeout] per the gradle-hang lesson
 * ([[dinghy-display-gradle-hang-interop]]) so a never-emitting flow can never wedge the run.
 */
@RunWith(AndroidJUnit4::class)
class ProfileSurvivesRestartTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** A unique-per-run file so a leftover from a prior run can never contaminate the cold re-read. */
    private val storeFile: File =
        File(context.filesDir, "datastore/profile-survival-test-${System.nanoTime()}.preferences_pb")

    // The "first process" scope — owns the writer store, then is CANCELLED to simulate process death.
    private var writerScope: CoroutineScope? = null
    // The "cold restart" scope — owns the second, re-reading store.
    private var readerScope: CoroutineScope? = null

    @Before
    fun setUp() {
        // Start from a clean slate: no stale bytes from a previously-aborted run.
        storeFile.delete()
        storeFile.parentFile?.mkdirs()
    }

    @Test
    fun activeIdSurvivesProcessDeath() = runBlocking {
        val profileA = Profile(id = Profile.newId(), name = "Ender 5 Plus", host = "192.168.1.120", port = 7125)
        val profileB = Profile(id = Profile.newId(), name = "Ender 3 Pro", host = "192.168.1.121", port = 7125)

        // ---- "First process": write two profiles, then make B the active selection. ----
        val firstScope = newStoreScope().also { writerScope = it }
        val writerStore = ProfileStore(createDataStore(firstScope))
        withTimeout(WRITE_TIMEOUT_MS) {
            writerStore.upsert(profileA)
            writerStore.upsert(profileB)
            writerStore.setActive(profileB.id)
        }
        // Sanity: the live writer store already reflects the active selection before the "restart".
        val activeBeforeDeath = withTimeout(READ_TIMEOUT_MS) { writerStore.activeId.first() }
        assertEquals("writer store must hold B active before the cold restart", profileB.id, activeBeforeDeath)

        // ---- "Process death": cancel the writer scope so DataStore releases the single-writer file lock. ----
        firstScope.cancel()
        writerScope = null

        // ---- "Cold start": a FRESH store instance over the SAME file re-reads the persisted bytes. ----
        val secondScope = newStoreScope().also { readerScope = it }
        val reReadStore = ProfileStore(createDataStore(secondScope))

        val activeAfterRestart = withTimeout(READ_TIMEOUT_MS) { reReadStore.activeId.first() }
        val profilesAfterRestart = withTimeout(READ_TIMEOUT_MS) { reReadStore.profiles.first() }

        // The active selection landed on the RIGHT printer (B) after the cold re-read (D-02/SC-3).
        assertEquals(
            "active_id did NOT survive process death — cold re-read must land on profile B",
            profileB.id,
            activeAfterRestart,
        )
        // Both printers survived (the profile SET persisted, not just the active pointer).
        val survivingIds = profilesAfterRestart.map { it.id }.toSet()
        assertTrue(
            "profile A must survive the cold re-read (got ids=$survivingIds)",
            profileA.id in survivingIds,
        )
        assertTrue(
            "profile B must survive the cold re-read (got ids=$survivingIds)",
            profileB.id in survivingIds,
        )
    }

    @After
    fun tearDown() {
        writerScope?.cancel()
        readerScope?.cancel()
        storeFile.delete()
    }

    /** A fresh app-lifetime-style scope per "process" (mirrors JiibApp's IO-backed DataStore scope). */
    private fun newStoreScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** A REAL DataStore over [storeFile] — same `PreferenceDataStoreFactory.create` shape as JiibApp. */
    private fun createDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { storeFile })

    private companion object {
        const val WRITE_TIMEOUT_MS = 10_000L
        const val READ_TIMEOUT_MS = 10_000L
    }
}
