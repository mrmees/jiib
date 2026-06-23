package works.mees.dinghy.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * DataStore(Preferences) persistence of the managed PROFILE SET + active-profile selection (MULTI-01) —
 * the Phase-14 generalization of [ConnectionStore]. Copies its shape VERBATIM: an injected [DataStore],
 * fail-safe [catch]/[map] read flows, suspend writers, and a PURE host-testable [sanitize] companion.
 *
 * Instead of three flat keys, the profiles live in ONE kotlinx-serialized JSON blob under [KEY_PROFILES]
 * (a `List<PersistedProfile>`), with a separate [KEY_ACTIVE_ID] string holding the active profile's id.
 * The blob shape is simpler to read/write/reorder than a per-profile-key scheme and is one atomic `edit`
 * (RESEARCH Pattern 1).
 *
 * FAIL-SAFE READ CONTRACT (mirrors [ConnectionStore]/[works.mees.dinghy.theme.ThemePrefs], T-14-02): a
 * read [IOException] (corrupt/partial blob) recovers by emitting empty prefs — which [sanitize] turns
 * into an empty list, NEVER a crash. An empty store yields no profiles → no active id → the Connect
 * prompt (D-11), never a black screen.
 *
 * ACTIVE-ID IS WRITER-OWNED (D-11/D-12): the active-id is only ever mutated by the suspend writers,
 * never derived on the read side. [upsert] auto-selects the FIRST profile added (D-11); [delete]
 * auto-picks another remaining profile when the active one is removed (D-12). The read derivation stays
 * pure (a dangling active-id resolves cleanly to `null` → Connect prompt).
 *
 * V5 input validation lives in the PURE [sanitize] (host trim/blank-reject, port range 1..65535), so a
 * malformed host or out-of-range port is dropped BEFORE it can become an active [ConnectionConfig].
 */
class ProfileStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** Sanitized profile set — never throws; an empty list is the first-run Connect prompt (D-11). */
    val profiles: Flow<List<Profile>> =
        dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { prefs -> sanitize(prefs[KEY_PROFILES]) }

    /** The active profile id (or null). A dangling id resolves to no active profile downstream. */
    val activeId: Flow<String?> =
        dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { it[KEY_ACTIVE_ID] }

    /**
     * Persist [profile] (insert or replace-by-id), atomically with the active-id auto-select.
     *
     * D-11 (first-add-is-active): if NO profile is currently active (active-id null/blank), the new
     * profile's id becomes the active id — so the first printer added immediately flips `hasConfig`
     * true and the root controller routes into the Shell instead of stranding on Connect. When an
     * active id already exists it is left UNCHANGED — a later add never steals active; switching is the
     * user's Devices tap. The blob write and the active-id write happen in ONE `edit` block so they are
     * atomic. This mirrors the D-12 auto-pick-in-the-writer pattern: active-id is writer-owned.
     */
    suspend fun upsert(profile: Profile) {
        dataStore.edit { prefs ->
            val plan = planUpsert(decode(prefs[KEY_PROFILES]), prefs[KEY_ACTIVE_ID], profile)
            prefs[KEY_PROFILES] = json.encodeToString(PROFILE_LIST_SERIALIZER, plan.profiles)
            // D-11: first-add becomes active; an existing active id is never disturbed.
            plan.activeId?.let { prefs[KEY_ACTIVE_ID] = it }
        }
    }

    /**
     * Upsert [profile] AND force it active in ONE `edit` (the discovery add-and-connect path). Unlike
     * [upsert] — which only auto-selects the first profile (D-11) and never steals active afterward — this
     * ALWAYS writes [profile].id as the active id, because the user just deliberately picked this printer.
     */
    suspend fun upsertAndSetActive(profile: Profile) {
        dataStore.edit { prefs ->
            val plan = planUpsert(decode(prefs[KEY_PROFILES]), prefs[KEY_ACTIVE_ID], profile)
            prefs[KEY_PROFILES] = json.encodeToString(PROFILE_LIST_SERIALIZER, plan.profiles)
            prefs[KEY_ACTIVE_ID] = profile.id
        }
    }

    /**
     * Delete the profile with [id]. D-12 auto-pick lives HERE, in the writer (NOT in any read
     * derivation): if [id] was the active profile, the active id is rewritten to the first remaining
     * profile's id, or removed when none remain (→ Connect prompt, D-11). Atomic in one `edit`.
     */
    suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            val plan = planDelete(decode(prefs[KEY_PROFILES]), prefs[KEY_ACTIVE_ID], id)
            prefs[KEY_PROFILES] = json.encodeToString(PROFILE_LIST_SERIALIZER, plan.profiles)
            when (plan.activeId) {
                is ActiveIdWrite.Set -> prefs[KEY_ACTIVE_ID] = plan.activeId.id
                ActiveIdWrite.Clear -> prefs.remove(KEY_ACTIVE_ID)
                ActiveIdWrite.Unchanged -> Unit
            }
        }
    }

    /** Set (or clear) the active profile id directly — the switch path (D-02). */
    suspend fun setActive(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_ACTIVE_ID) else prefs[KEY_ACTIVE_ID] = id
        }
    }

    /**
     * Atomically read-modify-write the ACTIVE profile in ONE `edit` (WR-01). [transform] runs against the
     * CURRENT persisted active profile read INSIDE the edit block — NOT a stale caller-held composition
     * snapshot — so rapid back-to-back single-field edits (e.g. two Appearance taps, base then accent) each
     * compose on the LATEST value instead of both re-encoding the pre-tap profile and silently dropping one
     * change (a classic lost update; the per-`edit` atomicity of a plain [upsert] does NOT save you when the
     * stale full object is what gets written). A null/dangling active-id is a no-op; the active-id key is
     * never touched. Used by the Settings theme persists (D-09).
     */
    suspend fun mutateActive(transform: (Profile) -> Profile) {
        dataStore.edit { prefs ->
            val list = decode(prefs[KEY_PROFILES])
            val idx = list.indexOfFirst { it.id == prefs[KEY_ACTIVE_ID] }
            if (idx < 0) return@edit
            val next = list.toMutableList()
                .also { it[idx] = transform(Profile.fromPersisted(list[idx])).toPersisted() }
            prefs[KEY_PROFILES] = json.encodeToString(PROFILE_LIST_SERIALIZER, next)
        }
    }

    /**
     * One-shot, non-blocking read of the active printer's persisted fsChoice — for the font-scale
     * migration ONLY. `dataStore.data.first()` emits the CURRENT stored prefs immediately (even when
     * empty); it never waits for a non-null active profile. Reads the PERSISTED blob, so it still works
     * after the runtime [Profile.fsChoice] is retired (later task).
     */
    suspend fun readActiveFsChoiceRaw(): String? {
        val prefs = dataStore.data.first()
        val activeId = prefs[KEY_ACTIVE_ID] ?: return null
        return decode(prefs[KEY_PROFILES]).firstOrNull { it.id == activeId }?.fsChoice
    }

    companion object {
        private val KEY_PROFILES = stringPreferencesKey("profiles")
        private val KEY_ACTIVE_ID = stringPreferencesKey("active_id")

        /** Inclusive valid TCP port range (ConnectionStore parity). */
        private val PORT_RANGE = 1..65535

        /** Lenient JSON: tolerate unknown keys so an older/newer blob shape never throws on decode. */
        private val json = Json { ignoreUnknownKeys = true }

        /** Explicit list serializer (the reified `encodeToString<List<…>>` is ambiguous on this Kotlin). */
        private val PROFILE_LIST_SERIALIZER = ListSerializer(PersistedProfile.serializer())

        /**
         * PURE fail-safe decode of the raw blob → [PersistedProfile] list. Never throws: a non-JSON /
         * truncated blob (or null) yields an empty list (T-14-02 runCatching fail-safe). Validation is
         * NOT applied here — [sanitize] filters; this is the raw decode the writers reuse.
         */
        private fun decode(rawBlob: String?): List<PersistedProfile> =
            runCatching {
                json.decodeFromString(PROFILE_LIST_SERIALIZER, rawBlob ?: return emptyList())
            }.getOrDefault(emptyList())

        /**
         * PURE input-validation sanitizer (V5/T-14-03) — host-testable with no DataStore/IO. Decodes the
         * blob fail-safe, drops any entry with a blank host or out-of-range port, and maps survivors to
         * runtime [Profile]s. NEVER throws.
         *   - null/blank blob          → emptyList()
         *   - non-JSON / truncated     → emptyList() (runCatching)
         *   - blank host / bad port    → that entry dropped, valid siblings kept
         */
        fun sanitize(rawBlob: String?): List<Profile> =
            decode(rawBlob)
                .filter { it.host.isNotBlank() && it.port in PORT_RANGE }
                .map { Profile.fromPersisted(it) }

        /**
         * PURE upsert decision (D-11) — host-testable with no DataStore/IO so the writer logic is provable
         * without the Windows back-to-back-`edit` rename race. Replaces-by-id or appends, and decides the
         * active-id write: when no profile is currently active, the new profile becomes active; otherwise
         * the active id is untouched ([UpsertPlan.activeId] == null means "leave active-id as-is").
         */
        fun planUpsert(
            current: List<PersistedProfile>,
            currentActiveId: String?,
            profile: Profile,
        ): UpsertPlan {
            val next = current.toMutableList()
            val idx = next.indexOfFirst { it.id == profile.id }
            if (idx >= 0) next[idx] = profile.toPersisted() else next += profile.toPersisted()
            val newActive = if (currentActiveId.isNullOrBlank()) profile.id else null
            return UpsertPlan(profiles = next, activeId = newActive)
        }

        /**
         * PURE delete decision (D-12) — host-testable with no DataStore/IO. Removes [id]; if [id] was the
         * active profile, auto-picks the first remaining profile (or clears active-id when none remain).
         * A non-active delete leaves the active-id [ActiveIdWrite.Unchanged].
         */
        fun planDelete(
            current: List<PersistedProfile>,
            currentActiveId: String?,
            id: String,
        ): DeletePlan {
            val remaining = current.filterNot { it.id == id }
            val activeWrite =
                if (currentActiveId == id) {
                    remaining.firstOrNull()?.id?.let { ActiveIdWrite.Set(it) } ?: ActiveIdWrite.Clear
                } else {
                    ActiveIdWrite.Unchanged
                }
            return DeletePlan(profiles = remaining, activeId = activeWrite)
        }
    }

    /** The PURE result of an [upsert] decision. [activeId] non-null ⇒ write it; null ⇒ leave as-is (D-11). */
    data class UpsertPlan(val profiles: List<PersistedProfile>, val activeId: String?)

    /** The PURE result of a [delete] decision (D-12). */
    data class DeletePlan(val profiles: List<PersistedProfile>, val activeId: ActiveIdWrite)

    /** How a writer mutates the active-id key. */
    sealed interface ActiveIdWrite {
        data class Set(val id: String) : ActiveIdWrite
        data object Clear : ActiveIdWrite
        data object Unchanged : ActiveIdWrite
    }
}
