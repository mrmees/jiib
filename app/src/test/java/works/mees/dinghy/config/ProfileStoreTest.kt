package works.mees.dinghy.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Host-side proof of the [ProfileStore] / [PersistedProfile] contract (14-01, MULTI-01).
 *
 * Two halves (mirroring [ConnectionStoreTest]):
 *  - PURE: [ProfileStore.sanitize] validated directly with no DataStore/IO (host-pure) — empty/corrupt
 *    blob → empty list, malformed entries dropped, valid siblings kept, [PersistedProfile.toString]
 *    redacts the key.
 *  - ROUND-TRIP: a real temp-file [PreferenceDataStoreFactory] DataStore proves the WRITER active-id
 *    contract: upsert into an empty store auto-selects active (D-11), a second upsert does NOT steal
 *    active, editing the active profile keeps it active, and delete auto-picks another (D-12) / clears
 *    on the last (D-11).
 */
class ProfileStoreTest {

    private val tmpFiles = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()
    private val json = Json { ignoreUnknownKeys = true }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        tmpFiles.forEach { it.delete() }
    }

    /**
     * DataStore's file actor MUST run on a real IO-backed scope, NOT the runTest virtual-time
     * dispatcher (the same Windows `.tmp`→final rename race as [ConnectionStoreTest]).
     */
    private fun newStore(): Pair<ProfileStore, CoroutineScope> {
        val file = File.createTempFile("profile_test_", ".preferences_pb").also {
            it.delete()
            tmpFiles += it
        }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += ioScope
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = ioScope) { file }
        return ProfileStore(dataStore) to ioScope
    }

    private fun profile(
        id: String,
        host: String = "192.168.1.120",
        port: Int = 7125,
        name: String? = null,
        apiKey: String? = null,
        seedHex: String = "#3f78ff",
    ) = Profile(id = id, name = name, host = host, port = port, apiKey = apiKey, seedHex = seedHex)

    private fun blob(vararg persisted: PersistedProfile): String =
        json.encodeToString(ListSerializer(PersistedProfile.serializer()), persisted.toList())

    // ---- PURE sanitize() --------------------------------------------------------------------------

    @Test
    fun sanitize_null_returnsEmpty() {
        assertTrue(ProfileStore.sanitize(null).isEmpty())
    }

    @Test
    fun sanitize_blankBlob_returnsEmpty() {
        assertTrue(ProfileStore.sanitize("").isEmpty())
        assertTrue(ProfileStore.sanitize("   ").isEmpty())
    }

    @Test
    fun sanitize_validTwoProfiles_returnsBothInOrder() {
        val raw = blob(
            PersistedProfile(id = "a", host = "192.168.1.120"),
            PersistedProfile(id = "b", host = "192.168.1.121", port = 80),
        )
        val out = ProfileStore.sanitize(raw)
        assertEquals(listOf("a", "b"), out.map { it.id })
        assertEquals(80, out[1].port)
    }

    @Test
    fun sanitize_dropsBlankHostAndOutOfRangePort_keepsValidSibling() {
        val raw = blob(
            PersistedProfile(id = "blankHost", host = "   "),
            PersistedProfile(id = "zeroPort", host = "h", port = 0),
            PersistedProfile(id = "highPort", host = "h", port = 70000),
            PersistedProfile(id = "good", host = "192.168.1.120", port = 7125),
        )
        val out = ProfileStore.sanitize(raw)
        assertEquals(listOf("good"), out.map { it.id })
    }

    @Test
    fun sanitize_portBoundaries_accepted() {
        val raw = blob(
            PersistedProfile(id = "lo", host = "h", port = 1),
            PersistedProfile(id = "hi", host = "h", port = 65535),
        )
        assertEquals(listOf("lo", "hi"), ProfileStore.sanitize(raw).map { it.id })
    }

    @Test
    fun sanitize_nonJsonBlob_returnsEmpty_neverThrows() {
        assertTrue(ProfileStore.sanitize("{not valid json").isEmpty())
        assertTrue(ProfileStore.sanitize("[{\"id\":\"a\",\"host\"").isEmpty()) // truncated
        assertTrue(ProfileStore.sanitize("garbage").isEmpty())
    }

    // ---- toString redaction (V7, T-14-01) ---------------------------------------------------------

    @Test
    fun persistedToString_redactsApiKey() {
        val s = PersistedProfile(id = "a", host = "h", apiKey = "super-secret-key").toString()
        assertTrue("toString must redact the key", s.contains("apiKey=***"))
        assertFalse("toString must not leak the raw key", s.contains("super-secret-key"))
    }

    @Test
    fun persistedToString_nullKey_showsNull() {
        assertTrue(PersistedProfile(id = "a", host = "h", apiKey = null).toString().contains("apiKey=null"))
    }

    @Test
    fun runtimeProfileToString_redactsApiKey() {
        val s = profile(id = "a", apiKey = "leak-me").toString()
        assertTrue(s.contains("apiKey=***"))
        assertFalse(s.contains("leak-me"))
    }

    // ---- toConnectionConfig projection ------------------------------------------------------------

    @Test
    fun toConnectionConfig_projectsHostPortKeyOnly_nameThemeAbsent() {
        val a = profile(id = "a", name = "Ender 5 Plus", seedHex = "#3f78ff")
        val b = profile(id = "b", name = "Different Name", seedHex = "#8b5cf6")
        // Same host/port/key but different name+theme → EQUAL ConnectionConfig (distinctUntilChanged anchor).
        assertEquals(a.toConnectionConfig(), b.toConnectionConfig())
        assertEquals(ConnectionConfig("192.168.1.120", 7125, null), a.toConnectionConfig())
    }

    @Test
    fun displayName_fallsBackToHost() {
        assertEquals("192.168.1.120", profile(id = "a", name = null).displayName())
        assertEquals("Ender 5 Plus", profile(id = "a", name = "Ender 5 Plus").displayName())
    }

    // ---- WRITER active-id contract — proven on the REAL DataStore where a SINGLE write suffices ----
    //
    // HOST CONSTRAINT (mirrors ConnectionStoreTest's documented note + VALIDATION §mock-vs-reality): the
    // Windows JVM host cannot do back-to-back `edit`s on one `.preferences_pb` — the atomic `.tmp`→final
    // rename throws "multiple instances of DataStore" / IOException. So the multi-write active-id paths
    // (second-add, edit-active, delete-auto-pick) are proven via the PURE planUpsert/planDelete writer
    // decisions below; the SINGLE-write D-11 first-add-is-active path round-trips on the real store here.
    // (Cross-process persistence is the instrumented ProfileSurvivesRestartTest's job, plan 06.)

    @Test
    fun upsertIntoEmptyStore_setsItActive() = runTest {
        val (store, ioScope) = newStore()
        withContext(ioScope.coroutineContext) {
            store.upsert(profile(id = "first"))
            settle()
            // FIX-1 gate (D-11): the FIRST profile added into a store with no active id becomes active.
            assertEquals("first", store.activeId.first())
            assertEquals(listOf("first"), store.profiles.first().map { it.id })
        }
    }

    @Test
    fun emptyStore_emitsEmptyProfilesAndNullActive() = runTest {
        val (store, ioScope) = newStore()
        withContext(ioScope.coroutineContext) {
            assertTrue(store.profiles.first().isEmpty())
            assertNull(store.activeId.first())
        }
    }

    @Test
    fun setActive_thenClear_singleWritePerStore() = runTest {
        // Each setActive is one `edit`; use two separate stores to avoid the back-to-back host race.
        val (setStore, setScope) = newStore()
        withContext(setScope.coroutineContext) {
            setStore.setActive("chosen")
            settle()
            assertEquals("chosen", setStore.activeId.first())
        }
        val (clearStore, clearScope) = newStore()
        withContext(clearScope.coroutineContext) {
            clearStore.setActive(null)
            settle()
            assertNull(clearStore.activeId.first())
        }
    }

    // ---- PURE writer decisions (host-pure, the multi-write logic — D-11/D-12) ----------------------

    private fun persisted(id: String, host: String = "h", port: Int = 7125, name: String? = null) =
        PersistedProfile(id = id, name = name, host = host, port = port)

    @Test
    fun planUpsert_intoEmptyStore_setsActive() {
        val plan = ProfileStore.planUpsert(emptyList(), currentActiveId = null, profile = profile(id = "first"))
        assertEquals("first", plan.activeId)
        assertEquals(listOf("first"), plan.profiles.map { it.id })
    }

    @Test
    fun planUpsert_secondAdd_doesNotStealActive() {
        val plan = ProfileStore.planUpsert(
            current = listOf(persisted("first")),
            currentActiveId = "first",
            profile = profile(id = "second", host = "192.168.1.121"),
        )
        assertNull("a second add must NOT change the active id", plan.activeId)
        assertEquals(listOf("first", "second"), plan.profiles.map { it.id })
    }

    @Test
    fun planUpsert_editActiveProfile_keepsItActive() {
        val plan = ProfileStore.planUpsert(
            current = listOf(persisted("first", name = "old")),
            currentActiveId = "first",
            profile = profile(id = "first", name = "renamed"),
        )
        assertNull("editing the active profile must NOT change the active id", plan.activeId)
        assertEquals("renamed", plan.profiles.single().name)
    }

    @Test
    fun planDelete_activeProfile_autoPicksAnother() {
        val plan = ProfileStore.planDelete(
            current = listOf(persisted("first"), persisted("second", host = "192.168.1.121")),
            currentActiveId = "first",
            id = "first",
        )
        assertEquals(ProfileStore.ActiveIdWrite.Set("second"), plan.activeId)
        assertEquals(listOf("second"), plan.profiles.map { it.id })
    }

    @Test
    fun planDelete_lastProfile_clearsActive() {
        val plan = ProfileStore.planDelete(
            current = listOf(persisted("only")),
            currentActiveId = "only",
            id = "only",
        )
        assertEquals(ProfileStore.ActiveIdWrite.Clear, plan.activeId)
        assertTrue(plan.profiles.isEmpty())
    }

    @Test
    fun planDelete_nonActiveProfile_leavesActiveUnchanged() {
        val plan = ProfileStore.planDelete(
            current = listOf(persisted("first"), persisted("second", host = "192.168.1.121")),
            currentActiveId = "first",
            id = "second",
        )
        assertEquals(ProfileStore.ActiveIdWrite.Unchanged, plan.activeId)
        assertEquals(listOf("first"), plan.profiles.map { it.id })
    }

    // ---- readActiveFsChoiceRaw (font-scale migration reader) ----------------------------------------

    @Test
    fun readActiveFsChoiceRaw_emptyStore_returnsNull() = runTest {
        val (store, ioScope) = newStore()
        withContext(ioScope.coroutineContext) {
            assertNull(store.readActiveFsChoiceRaw())
        }
    }

    @Test
    fun readActiveFsChoiceRaw_activeProfile_returnsMDefault() = runTest {
        val (store, ioScope) = newStore()
        withContext(ioScope.coroutineContext) {
            // toPersisted() now always writes fsChoice="M" — per-printer fsChoice is retired.
            // readActiveFsChoiceRaw() reads the persisted blob, so it returns "M" for all new profiles.
            // Legacy blobs with fsChoice="L" are decoded correctly (PersistedProfile.fsChoice still exists).
            store.upsert(profile(id = "alpha"))
            settle()
            assertEquals("M", store.readActiveFsChoiceRaw())
        }
    }

    /** Let the DataStore IO actor finish a write and release file handles before the next read/write. */
    private suspend fun settle() {
        yield()
        Thread.sleep(60)
        System.gc()
        Thread.sleep(60)
        yield()
    }
}
