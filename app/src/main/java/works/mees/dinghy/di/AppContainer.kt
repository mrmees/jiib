package works.mees.dinghy.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.config.ConnectionStore
import works.mees.dinghy.config.MoonrakerDiscovery
import works.mees.dinghy.config.Profile
import works.mees.dinghy.config.ProfileStore
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.LastJob
import works.mees.dinghy.state.PrintMetadata
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.Webcam
import works.mees.dinghy.theme.DEFAULT_POOL_MAX_ITEMS
import works.mees.dinghy.theme.DEFAULT_SEED_HEX
import works.mees.dinghy.theme.ThemeBase
import works.mees.dinghy.theme.ThemePrefs
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.ui.files.FileBrowserClient
import works.mees.dinghy.ui.macros.MacroPrefs
import works.mees.dinghy.ui.webcam.WebcamPrefs

/**
 * The process-scoped service-locator (no DI framework — D-02). It is the promotion of GalleryActivity's
 * sole-assembler block to an [android.app.Application]-held singleton ([DinghyApp] owns the instance and
 * the two DataStore files). It owns the headless, connection-independent state — [ThemePrefs],
 * [ConnectionStore], and a [ThemeResolver] seeded once from persisted theme prefs — and exposes ONE
 * publication point for the live spine.
 *
 * ## Single atomic spine publication (review #6)
 * The service writes whole [SpineHandle] snapshots via [publishSpine]; the Activity collects [spine]
 * (one `StateFlow<SpineHandle?>`). Publishing `null` = idle/no session (review #12: a cleared config
 * idles the spine). The per-field convenience flows ([printerState]/[connectionState]/[capabilities]/
 * [dispatcher]) are DERIVED by flat-mapping [spine], so a rebuild never exposes a partially-swapped mix
 * of an old session's flows with a new session's dispatcher — every reader sees the whole new handle.
 *
 * ## No socket/session construction here
 * AppContainer constructs NO transport — no websocket, no session. The SERVICE assembles the spine and
 * publishes in. The UI's reconnect/restart surface is the narrow [sessionControl] (review #1) whose live
 * delegate the service supplies via [bindSessionControl]; the container never holds a raw session
 * reference reachable by UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppContainer(
    themeDataStore: DataStore<Preferences>,
    connectionDataStore: DataStore<Preferences>,
    macroDataStore: DataStore<Preferences>,
    webcamDataStore: DataStore<Preferences>,
    /**
     * The FIFTH, INDEPENDENT file: profiles.preferences_pb (MULTI-01, Phase 14). Backs the managed
     * PROFILE SET + active-profile selection ([ProfileStore]) — the Phase-14 generalization of the
     * single [ConnectionStore]. Created ONCE in [works.mees.dinghy.DinghyApp] (the DataStore
     * single-writer invariant) and injected here. Inserted before [discovery] so [discovery] stays the
     * last (non-DataStore) ctor param.
     */
    profileDataStore: DataStore<Preferences>,
    /**
     * The FULLY-LAZY mDNS scanner (04-01, review #5) the Settings "Scan" button collects. Holding it
     * here pins NO radio — its constructor touches neither NsdManager nor the multicast lock; the
     * machinery is acquired only inside `discover()` on collect and released on `awaitClose`. Injected
     * (rather than built here) because constructing it needs an Android Context, which the container
     * deliberately does not hold — [works.mees.dinghy.DinghyApp] supplies the Context-bound instance.
     */
    val discovery: MoonrakerDiscovery,
) {
    /** Theme persistence (THEME-02/D-02) — the theme.preferences_pb-backed store. */
    val themePrefs: ThemePrefs = ThemePrefs(themeDataStore)

    /** Connection persistence (CONN-01) — the SEPARATE connection.preferences_pb-backed store. */
    val connectionStore: ConnectionStore = ConnectionStore(connectionDataStore)

    /**
     * Managed-profile persistence (MULTI-01, Phase 14) — the profiles.preferences_pb-backed store of the
     * printer SET + active-profile id, the Phase-14 generalization of [connectionStore]. The source of
     * [activeProfile] / [activeConfig] below and the per-profile theme re-seed (D-08). The single
     * [connectionStore] field is left in place (D-07 — dead but retained until the phase is verified).
     */
    val profileStore: ProfileStore = ProfileStore(profileDataStore)

    /**
     * Process-lifetime scope for fire-and-forget PERSISTENCE writes (active-profile switch + profile
     * CRUD). These MUST outlive the calling composition: a `rememberCoroutineScope()` write is cancelled
     * the instant its screen leaves the composition — and the Devices switch + Settings save/delete all
     * navigate away in the SAME frame as the write. On a slow Nexus-7 flash the DataStore `.tmp`→rename
     * loses that race, silently dropping the write → the Phase-14 "switch sometimes reverts to the old
     * printer / active selection doesn't update" bug. This scope is owned by the process-scoped container
     * so a write always runs to completion regardless of UI lifecycle. Use [setActiveProfile] /
     * [saveProfile] / [deleteProfile] from the UI — NEVER `rememberCoroutineScope().launch { profileStore… }`.
     */
    private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Switch the active profile (D-02), durably — survives the Devices screen navigating away. */
    fun setActiveProfile(id: String) {
        writeScope.launch { profileStore.setActive(id) }
    }

    /** Insert/replace a profile (Settings save + theme persist), durably — survives navigation. */
    fun saveProfile(profile: Profile) {
        writeScope.launch { profileStore.upsert(profile) }
    }

    /** Delete a profile (Settings delete; D-12 auto-pick lives in the writer), durably. */
    fun deleteProfile(id: String) {
        writeScope.launch { profileStore.delete(id) }
    }

    /**
     * Atomically mutate the active profile's persisted fields (the Settings theme persists), durably and
     * lost-update-safe (WR-01): the read-modify-write happens inside ProfileStore's single `edit`, so two
     * fast Appearance taps don't each re-encode a stale snapshot and drop one change. Pass a field-level
     * transform, e.g. `mutateActiveProfile { it.copy(themeBase = base.name) }`.
     */
    fun mutateActiveProfile(transform: (Profile) -> Profile) {
        writeScope.launch { profileStore.mutateActive(transform) }
    }

    /**
     * The currently-active [Profile] (or null when there is none — no profiles, or a dangling active-id).
     * A PURE pick: combine the sanitized profile set with the writer-owned active-id and pick by id
     * (RESEARCH Pattern 2). The D-12 auto-pick on delete lives in the [ProfileStore] writer, NOT here —
     * a dangling id resolves cleanly to null → the Connect prompt (D-11/D-12).
     */
    val activeProfile: Flow<Profile?> =
        combine(profileStore.profiles, profileStore.activeId) { list, id ->
            list.firstOrNull { it.id == id }
        }

    /**
     * The active profile's connection projection (host/port/apiKey ONLY) — the value the service rebind
     * seam consumes. The [distinctUntilChanged] is LOAD-BEARING (RESEARCH Pitfall 1 / T-14-04): a
     * name-only or theme-only edit on the active profile yields a STRUCTURALLY-EQUAL [ConnectionConfig]
     * (data-class equality over host/port/apiKey), so it is suppressed and does NOT churn the spine — no
     * spurious reconnect Splash. A host/port/key change DOES re-emit, driving exactly one rebind.
     */
    val activeConfig: Flow<ConnectionConfig?> =
        activeProfile.map { it?.toConnectionConfig() }.distinctUntilChanged()

    /**
     * Macro visibility persistence (MACRO-03 / 08-07 B1) — the SEPARATE macros.preferences_pb-backed
     * store holding the user's macro bookmarks ([Set]<String>) + the revealHidden toggle. It is
     * PROCESS-SCOPED and CONNECTION-INDEPENDENT (like [themePrefs]/[connectionStore], NOT a field on
     * [SpineHandle]): bookmarks survive reconnects and printer swaps. The 08-07 shell wiring hands this
     * instance's flows + suspend mutators to the per-session [works.mees.dinghy.ui.macros.MacroHolder]
     * and the System/Bookmarked screens.
     */
    val macroPrefs: MacroPrefs = MacroPrefs(macroDataStore)

    /**
     * Per-printer preferred-cam persistence (CAM-01 / 10-06 D-10) — the SEPARATE webcam.preferences_pb
     * store holding the last-viewed cam id keyed `preferred_cam_<host>`. Like [macroPrefs] it is
     * PROCESS-SCOPED + CONNECTION-INDEPENDENT (NOT a field on [SpineHandle]): the saved cam survives
     * reconnects and printer swaps. The 10-06 holder reads [WebcamPrefs.preferredCam] to default the
     * focus cam (else first-in-list) and writes [WebcamPrefs.setPreferredCam] on a select/cycle.
     */
    val webcamPrefs: WebcamPrefs = WebcamPrefs(webcamDataStore)

    /**
     * The shared OkHttp client the webcam decode/poll layer derives its two postures off (CLAUDE.md
     * networking law: ONE pool/TLS config). The per-session websocket client lives in the SERVICE and is
     * not reachable from the UI; the webcam stream/snapshot HTTP is its OWN connection (the cadence
     * contract exempts it from the single-subscribe ws) and is process-scoped + connection-independent
     * (it survives reconnects/printer swaps, like [webcamPrefs]). [WebcamClients] derives the stream
     * (readTimeout 0) and snapshot (finite readTimeout) postures off this single client. Lazy so no pool
     * is allocated until the first Webcam page open. Mirrors [net.MoonrakerSocket.defaultClient]'s posture.
     */
    val webcamHttpClient: okhttp3.OkHttpClient by lazy {
        works.mees.dinghy.net.MoonrakerSocket.defaultClient()
    }

    /** The single active-theme source of truth (D-05); seeded below from [themePrefs]. */
    val themeResolver: ThemeResolver = ThemeResolver()

    // ---- Spine publication (review #6) -------------------------------------------------------------

    private val _spine = MutableStateFlow<SpineHandle?>(null)

    /** The ONE spine publication point the Activity collects; `null` = idle/no session. */
    val spine: StateFlow<SpineHandle?> = _spine.asStateFlow()

    /** Publish a whole new spine snapshot atomically (`null` = idle). Called only by the service. */
    fun publishSpine(handle: SpineHandle?) {
        _spine.value = handle
    }

    // ---- Derived per-field convenience flows (always read off the whole current handle) ------------

    /** Live printer state; falls back to an empty default when idle (no session). */
    val printerState: Flow<PrinterState> =
        spine.flatMapLatest { it?.printerState ?: flowOf(PrinterState()) }

    /** Live connection lifecycle; Disconnected when idle. */
    val connectionState: Flow<ConnectionState> =
        spine.flatMapLatest { it?.connectionState ?: flowOf(ConnectionState.Disconnected) }

    /** Live re-derived capabilities; empty when idle. */
    val capabilities: Flow<Capabilities> =
        spine.flatMapLatest { it?.capabilities ?: flowOf(Capabilities()) }

    /** The current session's dispatcher, or null when idle. */
    val dispatcher: Flow<CommandDispatcher?> = spine.map { it?.dispatcher }

    /** Live one-shot-per-filename gcode metadata; null when idle / unavailable (260601-sip Inc 2). */
    val printMetadata: Flow<PrintMetadata?> =
        spine.flatMapLatest { it?.metadata ?: flowOf(null) }

    /** Live one-shot-on-idle last completed job; null when no history / idle (260601-th9 Inc 3). */
    val lastJob: Flow<LastJob?> =
        spine.flatMapLatest { it?.lastJob ?: flowOf(null) }

    /** Live one-shot-per-handshake webcam enumeration; empty when none / idle (CAM-01, 10-03). */
    val webcams: Flow<List<Webcam>> =
        spine.flatMapLatest { it?.webcams ?: flowOf(emptyList()) }

    /**
     * The webcam COUNT (CAM-01, 10-03) — the D-08 drawer greyed-gating signal (tile live when ≥1, greyed
     * when 0) AND the D-10 default-cam pick source (the holder picks the first cam when none is saved).
     * Derived off [webcams] so it always reflects the CURRENT session's enumeration; 0 when idle.
     */
    val webcamCount: Flow<Int> = webcams.map { it.size }

    /**
     * Live active-spool status (SPOOL-01/08, plan 11-04) — forwarded off the current session's
     * [SpineHandle.activeSpool], which a service-owned [works.mees.dinghy.spool.ActiveSpoolFacade]
     * fetches on each handshake edge and reconciles to the two server-push notifications (D-10). null
     * when no active spool / unavailable / idle.
     */
    val activeSpool: Flow<works.mees.dinghy.spool.SpoolmanStatus?> =
        spine.flatMapLatest { it?.activeSpool ?: flowOf(null) }

    /**
     * Whether the connected printer has the Moonraker `spoolman` component (D-02). The drawer
     * greyed-gating input for the Spool tile — the role [webcamCount] > 0 plays for the Webcam tile.
     * Derived off [capabilities] so it always reflects the CURRENT session; false when idle.
     */
    val spoolmanPresent: Flow<Boolean> = capabilities.map { it.hasComponent("spoolman") }

    /**
     * The current session's lean Spoolman INVENTORY reader (SPOOL-02/03, plan 11-06), or null when idle.
     * The Spool picker holder reads its list/filter inventory through this — the role [fileBrowser]
     * plays for the Files picker. Synchronous snapshot access mirrors [currentFileBrowser]: the shell
     * `remember`s the holder keyed on the session, so it grabs the current client at construction.
     */
    val currentSpoolmanClient: works.mees.dinghy.spool.SpoolmanClient?
        get() = spine.value?.spoolmanClient

    /** Current session's Files facade, or null when idle. */
    val fileBrowser: Flow<FileBrowserClient?> = spine.map { it?.fileBrowser }

    /** Synchronous nullable access for code paths that only need the current session snapshot. */
    val currentFileBrowser: FileBrowserClient?
        get() = spine.value?.fileBrowser

    /** The current session's REST base for thumbnail URLs; "" when idle (260601-sip Inc 2). */
    val httpBase: Flow<String> = spine.map { it?.httpBase ?: "" }

    /**
     * True once there is an ACTIVE PROFILE (D-11 generalization of "has a usable persisted connection"):
     * 0 profiles (or a dangling active-id) → false → the existing Connect prompt; ≥1 with an active id →
     * true → the Shell. Derived off [activeConfig] so it shares the same null-when-idle semantics.
     */
    val hasConfig: Flow<Boolean> = activeConfig.map { it != null }

    // ---- SessionControl (review #1) ----------------------------------------------------------------

    @Volatile
    private var sessionControlDelegate: SessionControl? = null

    /**
     * The UI's entire reconnect/restart surface (review #1). Forwards to the live delegate the service
     * binds via [bindSessionControl]; a call before any session is published (idle) is a safe no-op.
     */
    val sessionControl: SessionControl = object : SessionControl {
        override fun requestReconnectNow() {
            sessionControlDelegate?.requestReconnectNow()
        }

        override fun restartFirmware() {
            sessionControlDelegate?.restartFirmware()
        }

        override fun restartHost() {
            sessionControlDelegate?.restartHost()
        }
    }

    /** The service supplies the concrete, session-bound delegate (`null` clears it on idle). */
    fun bindSessionControl(delegate: SessionControl?) {
        sessionControlDelegate = delegate
    }

    /**
     * Re-seed the resolver per ACTIVE PROFILE (D-08, RESEARCH Pattern 3). Each time the active profile
     * changes, apply ITS persisted theme triple `(base, deltas, fs)` in ONE [ThemeResolver.apply] call
     * (one re-emit — no 3-call flicker, no stale theme). When there is NO active profile (idle/Connect
     * prompt), fall back to the GLOBAL [themePrefs] (which is ALSO the new-profile default look) and
     * finally [ThemePrefs.DEFAULT]. [themePrefs] is therefore RETAINED — it is the no-active-profile idle
     * theme + the new-profile seed, not dead code. Call from the Application on a long-lived scope.
     */
    fun seedTheme(scope: CoroutineScope) {
        scope.launch {
            activeProfile
                .flatMapLatest { p ->
                    flowOf(p?.toThemeResolved() ?: themePrefs.flow.firstOrNull() ?: ThemePrefs.DEFAULT)
                }
                .collect { resolved ->
                    // 15-04 (D-02/D-04): the resolver is generate-and-cache now. Feed the new tuple
                    // (seed-only chrome — the per-role deltas are retired) with the default seed and
                    // the persisted dark/light polarity + fs. 15-05 replaces this with the persisted
                    // seed/mode/pool tuple.
                    themeResolver.apply(
                        seedHex = DEFAULT_SEED_HEX,
                        dark = resolved.base == ThemeBase.Dark,
                        paletteMode = ThemeResolver.MODE_COLORFUL,
                        poolShift = 0,
                        maxItems = DEFAULT_POOL_MAX_ITEMS,
                        overrides = emptyMap(),
                        fs = resolved.fs,
                    )
                }
        }
    }
}
