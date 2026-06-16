package works.mees.dinghy.di

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.theme.StatusSlot
import works.mees.dinghy.theme.mergeOnto
import works.mees.dinghy.theme.ThemePrefs
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.toComposeColor
import works.mees.dinghy.ui.files.FileBrowserClient
import works.mees.dinghy.ui.macros.MacroPrefs
import works.mees.dinghy.ui.move.SavedLocation
import works.mees.dinghy.ui.move.SavedLocationPrefs
import works.mees.dinghy.ui.settings.BabystepPrefs
import works.mees.dinghy.ui.settings.DisplayPrefs
import works.mees.dinghy.ui.settings.FontScalePrefs
import works.mees.dinghy.ui.settings.TraceStylePrefs
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
/**
 * @Stable (Phase 22 A3/SC2): a single process-lifetime singleton whose identity never changes, so
 * screens receiving it as a composable parameter can skip recomposition on it. All reactive state is
 * exposed as `Flow`/`StateFlow` (Compose observes those via `collectAsState`). Stability audit: the
 * only `var` is the `private @Volatile sessionControlDelegate` (not composable-visible). The two
 * synchronous snapshot getters [currentSpoolmanClient]/[currentFileBrowser] return live `spine.value`
 * snapshots that change without Compose notification, but they are BY DESIGN non-composable accessors
 * (the reactive equivalents are the `fileBrowser`/Spoolman `Flow`s) — they don't undermine the skip
 * guarantee, which rests on the singleton's permanently-stable identity.
 */
@Stable
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
     * The SIXTH, INDEPENDENT file: babystep.preferences_pb (D-06, Phase 16). Backs the process-scoped
     * babystep app setting ([BabystepPrefs]: enable toggle + first-layer-window layer-count). Carries no
     * secrets (like macros/webcam), kept on its own connection-independent lifecycle per the separate-file
     * discipline. Created ONCE in [works.mees.dinghy.DinghyApp] (the DataStore single-writer invariant) and
     * injected here.
     */
    babystepDataStore: DataStore<Preferences>,
    /**
     * The SEVENTH, INDEPENDENT file: tracestyle.preferences_pb (D-14, Phase 26). Backs the
     * process-scoped per-sensor trace color + visibility settings ([TraceStylePrefs]: flat key-map
     * of sensor-name→ARGB-Int for color, sensor-name→Boolean for visibility). Carries no secrets
     * (like macros/webcam/babystep), kept on its own connection-independent lifecycle per the
     * separate-file discipline. Created ONCE in [works.mees.dinghy.DinghyApp] (the DataStore
     * single-writer invariant) and injected here.
     */
    traceStyleDataStore: DataStore<Preferences>,
    /**
     * The EIGHTH, INDEPENDENT file: display.preferences_pb (§R2, Phase 26.5-05). Backs the
     * process-scoped display settings ([DisplayPrefs]: the keep-screen-on toggle, default ON for
     * the dedicated-display use case, plus the app-global webcam-enabled toggle added 2026-06-15).
     * Carries no secrets (like macros/webcam/babystep/tracestyle),
     * kept on its own connection-independent lifecycle per the separate-file discipline. Created
     * ONCE in [works.mees.dinghy.DinghyApp] (the DataStore single-writer invariant) and injected here.
     */
    displayDataStore: DataStore<Preferences>,
    /**
     * The NINTH, INDEPENDENT file: savedlocations.preferences_pb (Move hub, feat/move-hub-redesign).
     * Backs the process-scoped named toolhead-position store ([SavedLocationPrefs]: ordered list of
     * [SavedLocation], identity = name). Carries no secrets, kept on its own connection-independent
     * lifecycle per the separate-file discipline. Created ONCE in [works.mees.dinghy.DinghyApp]
     * (the DataStore single-writer invariant) and injected here.
     */
    savedLocationDataStore: DataStore<Preferences>,
    /**
     * The TENTH, INDEPENDENT file: fontscale.preferences_pb (App/Printer Settings Split, Task 1.2).
     * Backs the process-scoped app-global font-scale setting ([FontScalePrefs]: S/M/L FontScale
     * choice). Replaces the retired per-printer [Profile.fsChoice] as the SOLE source of `--fs`.
     * Carries no secrets, kept on its own connection-independent lifecycle per the separate-file
     * discipline. Created ONCE in [works.mees.dinghy.DinghyApp] (the DataStore single-writer
     * invariant) and injected here.
     */
    fontScaleDataStore: DataStore<Preferences>,
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

    /**
     * Process-lifetime scope for PROCESS-SCOPED derived [StateFlow]s (D-01, 22-07). Separate from
     * [writeScope] (IO dispatcher) — these flows live on [Dispatchers.Default] because they are pure
     * in-memory transformations with no disk I/O. Only PROCESS-SCOPE flows belong here: flows keyed on
     * a session (`store`/`spine`) are SESSION-SCOPE and must stay shell-side (e.g. `errorLines`,
     * keyed on `consoleHolder` which is `remember(store)`). Currently hosts:
     *   - [activeProfileId] — `activeProfile.map { it?.id }` deduplicated StateFlow
     *   - [activeName]      — `activeProfile.map { it?.displayName() }` deduplicated StateFlow
     * Both are collected in AppShell to replace the two inline `.map{}` expressions that were creating
     * new un-memoized Flow objects on every recomposition of the shell scope (killing Compose's structural
     * equality check and re-collecting on every wide-recomposition tick).
     */
    private val stateScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
     * transform, e.g. `mutateActiveProfile { it.copy(seedHex = next) }`.
     */
    fun mutateActiveProfile(transform: (Profile) -> Profile) {
        writeScope.launch { profileStore.mutateActive(transform) }
    }

    /**
     * Toggle a macro bookmark (Macros ManageMode), durably — the [[dinghy-compose-write-scope-cancellation]]
     * guard (WR-08): a composition-scoped launch is cancelled the instant its host leaves composition
     * (e.g. a recovery Splash decomposing AppShell in the same frame as the tap), silently dropping the
     * DataStore write mid-`edit` on slow flash. Always call this from UI — never
     * `rememberCoroutineScope().launch { macroPrefs… }`.
     */
    fun toggleMacroBookmark(name: String) {
        writeScope.launch { macroPrefs.toggleBookmark(name) }
    }

    /** Persist the Macros reveal-hidden toggle, durably (same write-scope rule as [toggleMacroBookmark]). */
    fun setMacroRevealHidden(reveal: Boolean) {
        writeScope.launch { macroPrefs.setRevealHidden(reveal) }
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
     * The active profile's ID as a [StateFlow] (D-01 hoist, 22-07). Replaces the inline
     * `container.activeProfile.map { it?.id }` expression in AppShell that created a new un-memoized
     * Flow object on every shell recomposition — defeating Compose's structural equality check and
     * forcing re-collection on every wide-recomposition tick. Hoisted here (PROCESS-SCOPE: derived
     * off the process-scoped [activeProfile] with no session key) so it is a stable singleton.
     * [WhileSubscribed(5000)] matches the app's standard upstream subscription pattern.
     */
    val activeProfileId: StateFlow<String?> =
        activeProfile.map { it?.id }
            .stateIn(stateScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The active profile's display name as a [StateFlow] (D-01 hoist, 22-07). Replaces the inline
     * `container.activeProfile.map { it?.displayName() }` expression in AppShell for the same reason
     * as [activeProfileId] above. Hoisted here (PROCESS-SCOPE) so it is a stable singleton.
     * null when no active profile (the Devices drawer-tile subtitle hides on null).
     */
    val activeName: StateFlow<String?> =
        activeProfile.map { it?.displayName() }
            .stateIn(stateScope, SharingStarted.WhileSubscribed(5_000), null)

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
     * Process-scoped [StateFlow] of the user's pinned macro NAMEs (WR-02 fix, review 24). Hoisted from
     * AppShell's `stateIn(rememberCoroutineScope())` — a composition scope is cancelled when AppShell
     * leaves the composition tree (e.g. on a FIX-3 recovery Splash), causing the stateIn coroutine to
     * stop collecting; post-Splash, the cached StateFlow went stale and no longer updated. Hosting here
     * on the process-lifetime [stateScope] ensures the upstream DataStore collection is NEVER interrupted
     * by a UI lifecycle event. Eagerly matches the prior shell-side usage (macro surface needs it
     * immediately on recomposition). See [[dinghy-compose-write-scope-cancellation]].
     */
    val macroBookmarks: StateFlow<Set<String>> =
        macroPrefs.bookmarks.stateIn(stateScope, SharingStarted.Eagerly, emptySet())

    /**
     * Process-scoped [StateFlow] of the revealHidden toggle (WR-02 fix, review 24). Same rationale as
     * [macroBookmarks] — moved from AppShell's composition-scoped `stateIn` to the process-lifetime
     * [stateScope] so it outlives any Composable and survives recovery Splashes intact.
     * Eagerly matches the prior shell-side usage.
     */
    val macroRevealHidden: StateFlow<Boolean> =
        macroPrefs.revealHidden.stateIn(stateScope, SharingStarted.Eagerly, false)

    /**
     * Named toolhead-position persistence (Move hub, feat/move-hub-redesign) — the SEPARATE
     * savedlocations.preferences_pb-backed store. Like [macroPrefs]/[webcamPrefs] it is
     * PROCESS-SCOPED + CONNECTION-INDEPENDENT (NOT a field on [SpineHandle]): saved positions
     * survive reconnects and printer swaps. The Move hub reads [savedLocations] and writes through
     * the durable [saveLocation]/[deleteLocation] intent helpers.
     */
    val savedLocationPrefs: SavedLocationPrefs = SavedLocationPrefs(savedLocationDataStore)

    /**
     * Process-scoped [StateFlow] of the user's saved toolhead positions. Eagerly matches the
     * pattern of [macroBookmarks] — hosted on the process-lifetime [stateScope] so it outlives any
     * Composable and survives recovery Splashes intact. The Move hub Bookmark focus collects this.
     */
    val savedLocations: StateFlow<List<SavedLocation>> =
        savedLocationPrefs.locations.stateIn(stateScope, SharingStarted.Eagerly, emptyList())

    /**
     * Persist a new (or replace an existing same-name) saved toolhead location, durably.
     * Routes through the process-lifetime [writeScope] ([[dinghy-compose-write-scope-cancellation]])
     * — the Save dialog navigates away in the same frame as the write, and a slow Nexus-7 flash
     * drops a composition-scoped write. NEVER `rememberCoroutineScope()`.
     */
    fun saveLocation(loc: SavedLocation) {
        writeScope.launch { savedLocationPrefs.add(loc) }
    }

    /**
     * Remove the saved location named [name] (idempotent), durably.
     * Same write-scope discipline as [saveLocation] ([[dinghy-compose-write-scope-cancellation]]).
     */
    fun deleteLocation(name: String) {
        writeScope.launch { savedLocationPrefs.remove(name) }
    }

    /**
     * Per-printer preferred-cam persistence (CAM-01 / 10-06 D-10) — the SEPARATE webcam.preferences_pb
     * store holding the last-viewed cam id keyed `preferred_cam_<host>`. Like [macroPrefs] it is
     * PROCESS-SCOPED + CONNECTION-INDEPENDENT (NOT a field on [SpineHandle]): the saved cam survives
     * reconnects and printer swaps. The 10-06 holder reads [WebcamPrefs.preferredCam] to default the
     * focus cam (else first-in-list) and writes [WebcamPrefs.setPreferredCam] on a select/cycle.
     */
    val webcamPrefs: WebcamPrefs = WebcamPrefs(webcamDataStore)

    /**
     * Babystep app-setting persistence (D-06, Phase 16) — the SEPARATE babystep.preferences_pb-backed store
     * holding the [BabystepPrefs.enabled] toggle (default true) + [BabystepPrefs.layerCount] first-layer
     * window (default 5). Like [macroPrefs]/[webcamPrefs] it is PROCESS-SCOPED + CONNECTION-INDEPENDENT (NOT
     * a field on [SpineHandle]): the setting survives reconnects and printer swaps. The Settings UI reads
     * [babystepEnabled]/[babystepLayers] and writes through the durable [setBabystepEnabled]/[setBabystepLayers]
     * intent helpers; the Wave-3 Print-Status babystep row gates its visibility on these flows.
     */
    val babystepPrefs: BabystepPrefs = BabystepPrefs(babystepDataStore)

    /** Whether the Z-babystep row is offered (D-06) — default true; survives reconnects/printer swaps. */
    val babystepEnabled: Flow<Boolean> = babystepPrefs.enabled

    /** The first-layer babystep window in layers (D-06) — default 5; survives reconnects/printer swaps. */
    val babystepLayers: Flow<Int> = babystepPrefs.layerCount

    /**
     * Persist the babystep enable toggle (D-06), durably. Routes through the process-lifetime [writeScope]
     * ([[dinghy-compose-write-scope-cancellation]]) — the Settings toggle can navigate away in the same
     * frame, and a slow Nexus-7 flash drops a composition-scoped write. NEVER `rememberCoroutineScope()`.
     */
    fun setBabystepEnabled(on: Boolean) {
        writeScope.launch { babystepPrefs.setEnabled(on) }
    }

    /**
     * Persist the babystep layer-count (D-06), durably + coerced `>= 1` in [BabystepPrefs.setLayerCount].
     * Same write-scope discipline as [setBabystepEnabled] ([[dinghy-compose-write-scope-cancellation]]).
     */
    fun setBabystepLayers(n: Int) {
        writeScope.launch { babystepPrefs.setLayerCount(n) }
    }

    /**
     * Display app-setting persistence (§R2, 26.5-05) — the SEPARATE display.preferences_pb-backed store
     * holding the [DisplayPrefs.keepScreenOn] toggle (default true — the dedicated-display use case) and
     * the app-global [DisplayPrefs.webcamEnabled] toggle (default true — moved per-profile→app-global
     * 2026-06-15; surfaced as [webcamEnabled]/[setWebcamEnabled] and gated into [webcamTileEnabled]).
     * Like [babystepPrefs]/[macroPrefs] it is PROCESS-SCOPED + CONNECTION-INDEPENDENT (NOT a field on
     * [SpineHandle]): the setting survives reconnects and printer swaps. The Settings UI reads
     * [keepScreenOn] and writes through the durable [setKeepScreenOn] intent; AppShell's root effect
     * gates `View.keepScreenOn` (→ FLAG_KEEP_SCREEN_ON on the hosting window) on the same flow.
     */
    val displayPrefs: DisplayPrefs = DisplayPrefs(displayDataStore)

    /**
     * App-global font-scale persistence (Task 1.2, App/Printer Settings Split) — the SEPARATE
     * fontscale.preferences_pb-backed store holding the [FontScalePrefs.fontScale] S/M/L choice
     * (default [works.mees.dinghy.theme.FontScale.M]). Replaces the retired per-printer
     * [works.mees.dinghy.config.Profile.fsChoice] as the SOLE source of `--fs`. PROCESS-SCOPED
     * + CONNECTION-INDEPENDENT (NOT a field on [SpineHandle]): the choice survives reconnects and
     * printer swaps.
     */
    val fontScalePrefs: FontScalePrefs = FontScalePrefs(fontScaleDataStore)

    /** Whether the screen is held awake while the shell is foregrounded (§R2) — default true. */
    val keepScreenOn: Flow<Boolean> = displayPrefs.keepScreenOn

    /**
     * Persist the keep-screen-on toggle (§R2), durably. Routes through the process-lifetime [writeScope]
     * ([[dinghy-compose-write-scope-cancellation]]) — the Settings toggle can navigate away in the same
     * frame, and a slow Nexus-7 flash drops a composition-scoped write. NEVER `rememberCoroutineScope()`.
     */
    fun setKeepScreenOn(on: Boolean) {
        writeScope.launch { displayPrefs.setKeepScreenOn(on) }
    }

    /** App-global webcam-enabled (moved from per-profile, 2026-06-15) — process-scoped, durable. */
    val webcamEnabled: Flow<Boolean> = displayPrefs.webcamEnabled

    /** Persist the app-global webcam toggle, durably (process-lifetime writeScope, never composition). */
    fun setWebcamEnabled(on: Boolean) {
        writeScope.launch { displayPrefs.setWebcamEnabled(on) }
    }

    /** App-global font scale (S/M/L) — connection-independent; the SOLE source of `--fs`. */
    val fontScale: Flow<FontScale> = fontScalePrefs.fontScale

    /**
     * Persist the app-global font scale durably (writeScope, never a composition scope).
     * Routes through the process-lifetime [writeScope] ([[dinghy-compose-write-scope-cancellation]])
     * — the Settings chip tap can navigate away in the same frame, and a slow Nexus-7 flash drops
     * a composition-scoped write. NEVER `rememberCoroutineScope()`.
     */
    fun setFontScale(choice: FontScale) {
        writeScope.launch { fontScalePrefs.setFontScale(choice) }
    }

    /**
     * Per-sensor trace color + visibility persistence (D-14, Phase 26) — the SEPARATE
     * tracestyle.preferences_pb-backed store. Like [babystepPrefs]/[macroPrefs] it is
     * PROCESS-SCOPED + CONNECTION-INDEPENDENT (not a field on SpineHandle): colors and visibility
     * survive reconnects and printer swaps. The Temperature screen reads these through the holder's
     * [works.mees.dinghy.ui.temperature.TemperatureHolder.traceColors] /
     * [works.mees.dinghy.ui.temperature.TemperatureHolder.traceVisibility] StateFlows (seeded from
     * this store at holder construction) and writes through [setTraceColor]/[setTraceVisibility].
     */
    val traceStylePrefs: TraceStylePrefs = TraceStylePrefs(traceStyleDataStore)

    init {
        // One-time migration of legacy UNSCOPED trace prefs into the first active profile's scope
        // (per-printer trace config — pre-merge review fix 2026-06-14). Runs once on the first non-null
        // profile id; idempotent via the prefs sentinel. Process-lifetime writeScope (never composition).
        writeScope.launch {
            val firstProfileId = activeProfileId.filterNotNull().first()
            traceStylePrefs.migrateUnscopedTo(firstProfileId)
        }

        // One-time font-scale migration (must NOT block on an active profile). Seeds the app-global
        // font scale from the active printer's prior persisted fsChoice if one exists, else M.
        // Idempotent via the FontScalePrefs sentinel. Process-lifetime writeScope.
        writeScope.launch {
            val seed = profileStore.readActiveFsChoiceRaw()
                ?.let { runCatching { works.mees.dinghy.theme.FontScale.valueOf(it) }.getOrNull() }
                ?: works.mees.dinghy.theme.FontScale.M
            fontScalePrefs.migrateSeed(seed)
        }
    }

    /**
     * Persist a trace color (D-14), durably. Routes through the process-lifetime [writeScope]
     * ([[dinghy-compose-write-scope-cancellation]]) — never `rememberCoroutineScope()`. The
     * [argb] is an ARGB Int chosen from the fixed 8-color Colorful pool (T-26-03-01: no
     * injection surface — the UI only passes pool members, never arbitrary user text).
     */
    fun setTraceColor(sensorName: String, argb: Int) {
        val pid = activeProfileId.value ?: return // no active profile → nowhere to scope the write
        writeScope.launch { traceStylePrefs.setTraceColor(pid, sensorName, argb) }
    }

    /**
     * Persist a trace visibility toggle (D-14), durably, SCOPED to the active printer profile.
     * Same write-scope discipline as [setTraceColor] ([[dinghy-compose-write-scope-cancellation]]).
     */
    fun setTraceVisibility(sensorName: String, visible: Boolean) {
        val pid = activeProfileId.value ?: return
        writeScope.launch { traceStylePrefs.setTraceVisibility(pid, sensorName, visible) }
    }

    /** Persist a Temperature sensor's monitored-set membership (opt-in), scoped to the active profile. */
    fun setSensorSelected(sensorName: String, selected: Boolean) {
        val pid = activeProfileId.value ?: return
        writeScope.launch { traceStylePrefs.setSensorSelected(pid, sensorName, selected) }
    }

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

    // ---- Dev theme-cycler enable + transient override (15.2-01) ------------------------------------
    //
    // The dev cyclers (15.2-02+) flip the WHOLE live app to a transient look without persisting (D-06/
    // D-08). The enable boolean is APP-GLOBAL + RELEASE-readable (NOT BuildConfig.DEBUG); the override
    // itself is the in-memory [_themeOverride] StateFlow below. Both feed the PURE [effectiveTokens]
    // derivation (Task 3).

    /** The app-global dev-widget enable flow (D-08) — default false, release-readable. */
    val devCyclerEnabled: Flow<Boolean> = themePrefs.devEnableFlow

    /**
     * The transient theme override (15.2-01 Task 3) — an IN-MEMORY only sparse override that never
     * persists. Declared here (with its bare setter) so [setDevCyclerEnabled] below can clear it on
     * disable (HIGH-5); Task 3 formalizes the [ThemeOverride] model, [updateThemeOverride], and the
     * [effectiveTokens] derivation that consumes it.
     */
    private val _themeOverride =
        MutableStateFlow<works.mees.dinghy.theme.ThemeOverride?>(null)

    /** The current transient override (null = none). Public read for the cycler overlay (15.2-02+). */
    val themeOverride: StateFlow<works.mees.dinghy.theme.ThemeOverride?> = _themeOverride.asStateFlow()

    /**
     * Set the transient override directly (in-memory ONLY — no DataStore write, the deliberate
     * non-persisting exception to [[dinghy-compose-write-scope-cancellation]]: there is no write to lose).
     */
    fun setThemeOverride(o: works.mees.dinghy.theme.ThemeOverride?) {
        _themeOverride.value = o
    }

    /**
     * ATOMICALLY step the override (MEDIUM — rapid-tap-safe). The cyclers call this to flip ONE axis at a
     * time (`{ (it ?: ThemeOverride()).copy(dark = …) }`). [MutableStateFlow.update] applies the transform
     * to the LIVE current value, so two fast taps cannot each read the same Compose-captured snapshot and
     * lose an axis (a style tap clobbering a just-applied size, or vice-versa). In-memory only — no persist.
     */
    fun updateThemeOverride(
        transform: (works.mees.dinghy.theme.ThemeOverride?) -> works.mees.dinghy.theme.ThemeOverride?,
    ) {
        _themeOverride.update(transform)
    }

    /**
     * Toggle the app-global dev-widget enable (D-08). The boolean IS persisted, so the write routes
     * through the process-lifetime [writeScope] ([[dinghy-compose-write-scope-cancellation]]). Turning
     * OFF ALSO clears any active override (HIGH-5) so the app can never be stranded in an overridden look
     * with the dismiss control hidden — pairs with the `!devOn` gate in [effectiveTokens] (Task 3).
     */
    fun setDevCyclerEnabled(on: Boolean) {
        if (!on) setThemeOverride(null)
        writeScope.launch { themePrefs.setDevEnable(on) }
    }

    // ---- Spine publication (review #6) -------------------------------------------------------------

    private val _spine = MutableStateFlow<SpineHandle?>(null)

    /** The ONE spine publication point the Activity collects; `null` = idle/no session. */
    val spine: StateFlow<SpineHandle?> = _spine.asStateFlow()

    /** Publish a whole new spine snapshot atomically (`null` = idle). Called only by the service. */
    fun publishSpine(handle: SpineHandle?) {
        _spine.value = handle
    }

    private val _systemInfoHolder =
        MutableStateFlow<works.mees.dinghy.systeminfo.SystemInfoHolder?>(null)

    /**
     * The current session's [works.mees.dinghy.systeminfo.SystemInfoHolder] (Phase 20 System
     * Information), or `null` when idle. Constructed per session by the service alongside the spine —
     * it needs the JsonRpcClient's `procStatUpdates` push flow (not carried on the SpineHandle data
     * class), so it is published on its own slot. The System Information screen (Plan 04) collects its
     * identity/procStats/live StateFlows off this.
     */
    val systemInfoHolder: StateFlow<works.mees.dinghy.systeminfo.SystemInfoHolder?> =
        _systemInfoHolder.asStateFlow()

    /** Publish the per-session SystemInfoHolder (`null` = idle). Called only by the service. */
    fun publishSystemInfoHolder(holder: works.mees.dinghy.systeminfo.SystemInfoHolder?) {
        _systemInfoHolder.value = holder
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
     * The WIRED Webcam-tile/surface gate (MEDIUM-4, 15.2-03 D-04) — the tile is live ONLY when the
     * connected printer actually HAS cams ([webcamCount] > 0) AND the app-global
     * [DisplayPrefs.webcamEnabled] toggle is on. The gate is now app-global (moved from per-profile,
     * 2026-06-15). Pure predicate [webcamTileGate] is the host-tested core.
     */
    val webcamTileEnabled: Flow<Boolean> =
        combine(webcamCount, displayPrefs.webcamEnabled) { count, enabled ->
            webcamTileGate(count, enabled)
        }

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
     * Whether the connected printer exposes ANY controllable output (Phase 19, D-10 / SC-1) — the drawer
     * Output-tile greyed-gating input, the role [spoolmanPresent] plays for the Spool tile. Derived off the
     * current session's store-backed [works.mees.dinghy.state.PrinterStateStore.outputDescriptors] so it
     * always reflects the CURRENT session (the descriptors are cleared on switch/failure); false when idle.
     */
    val outputsPresent: Flow<Boolean> =
        spine.flatMapLatest { it?.store?.outputDescriptors ?: flowOf(emptyList()) }
            .map { it.isNotEmpty() }

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
     * The SINGLE canonical theme TUPLE source (HIGH-1) — the active profile's tuple, or REACTIVELY the
     * GLOBAL idle [ThemePrefs.tupleFlow] when there is no active profile (the no-active idle theme + the
     * new-profile default look; WR-02 — a global edit while idle re-emits, never a one-shot freeze). BOTH
     * [seedTheme] (which applies it to [themeResolver]) AND [effectiveTokens] (which BAKES it) derive from
     * THIS one flow, so the persisted theme and any transient override can never bake from a different
     * profile/tuple than the one [seedTheme] applies — no idle-tuple omission, no profile skew.
     */
    val activeThemeTuple: Flow<ThemePrefs.ThemeTuple> =
        combine(
            activeProfile.flatMapLatest { p ->
                if (p != null) flowOf(p.toThemeTuple()) else themePrefs.tupleFlow
            },
            fontScalePrefs.fontScale,
        ) { tuple, appFs ->
            // App-global font scale is the SOLE source of `--fs` — override whatever the per-profile /
            // idle tuple carried. Both theme paths (seedTheme + effectiveTokens) read this flow.
            tuple.copy(fs = appFs.multiplier)
        }

    /**
     * The override-aware EFFECTIVE token flow the single Compose [DinghyTheme] boundary collects — a PURE
     * derivation of its combine inputs (HIGH-1, the foundational fix). It BAKES IN BOTH branches via the
     * pure [ThemeResolver.bake] (HIGH-2 — no shared-mutable resolver to tear): the persisted/normal path
     * returns `bake(base)`, the override path returns `bake(ov.mergeOnto(base))`.
     *
     * CRITICAL — it NEVER reads `themeResolver.tokens` / `.tokens.value`: [seedTheme] applies to the
     * resolver ASYNCHRONOUSLY after [activeThemeTuple] emits, so reading the resolver's `tokens.value`
     * imperatively in the non-override branch could emit a STALE snapshot lagged behind the latest tuple
     * and never self-correct under a profile/theme change. Baking the SAME canonical `base` tuple
     * [seedTheme] applies makes this a pure function of its inputs that re-emits whenever the tuple changes.
     *
     * The override is BAKED only while [devCyclerEnabled] is true (HIGH-5 — the `!devOn` gate ignores a
     * stranded override; [setDevCyclerEnabled] also CLEARS it). The Views hosts root in `LocalTokens`
     * (PATTERNS FACT 2), so flipping this ONE collect re-themes the classic-Views surfaces too.
     */
    val effectiveTokens: Flow<works.mees.dinghy.theme.ThemeTokens> =
        combine(activeThemeTuple, _themeOverride, devCyclerEnabled) { base, ov, devOn ->
            val tuple = if (ov != null && devOn) ov.mergeOnto(base) else base
            themeResolver.bake(tuple)
        }

    /**
     * Re-seed the resolver per ACTIVE PROFILE (D-03/D-08, RESEARCH Pattern 3). Collects the single
     * canonical [activeThemeTuple] and applies each tuple in ONE [ThemeResolver.apply] call (one re-emit —
     * no flicker, no stale theme). The resolver's `tokens` flow still feeds any non-effectiveTokens
     * consumer; [effectiveTokens] is the override-aware boundary the UI collects. Call from the Application
     * on a long-lived scope.
     */
    fun seedTheme(scope: CoroutineScope) {
        scope.launch {
            activeThemeTuple
                .collect { tuple ->
                    // 15-05 (D-03): apply the PERSISTED tuple in ONE re-emit. The sanitize layer already
                    // validated it; the resolver's compute() try/catch is the last-resort fail-safe.
                    themeResolver.apply(
                        seedHex = tuple.seedHex,
                        dark = tuple.dark,
                        paletteMode = tuple.paletteMode,
                        poolShift = tuple.poolShift,
                        // Shared ARGB-Long → Color helper (StatusSlot.kt) — the proven `.toInt()`-based
                        // conversion, never Color(longArgb). Status overrides are applied MODE-GATED in
                        // TokenBridge (Colorful only), so this resolution path honors D-04 too.
                        overrides = tuple.poolOverrides.mapValues { it.value.toComposeColor() },
                        statusOverrides = tuple.statusOverrides.mapValues { it.value.toComposeColor() },
                        fs = tuple.fs,
                    )
                }
        }
    }

    // ---- Theme-edit intent helpers (15-06 editor calls these) --------------------------------------
    //
    // The DURABLE write surface (T-15-05-04, [[dinghy-compose-write-scope-cancellation]]): every theme
    // write routes through the process-lifetime [writeScope] + an atomic [ProfileStore.mutateActive]
    // read-modify-write (active) OR [ThemePrefs] (idle global). NEVER a composition `rememberCoroutineScope()`.

    /** Persist the seed hex — active profile (durable, lost-update-safe), else the global idle theme. */
    fun setActiveSeed(active: Boolean, seedHex: String) {
        if (active) mutateActiveProfile { it.copy(seedHex = seedHex) }
        else writeScope.launch { themePrefs.setSeed(seedHex) }
    }

    /** Persist the palette mode — active profile, else global. */
    fun setActiveMode(active: Boolean, mode: String) {
        if (active) mutateActiveProfile { it.copy(paletteMode = mode) }
        else writeScope.launch { themePrefs.setMode(mode) }
    }

    /** Persist the pool hue-shift — active profile, else global. */
    fun setActiveShift(active: Boolean, shift: Int) {
        if (active) mutateActiveProfile { it.copy(poolShift = shift) }
        else writeScope.launch { themePrefs.setShift(shift) }
    }

    /** Persist dark/light polarity — active profile, else global. */
    fun setActiveDark(active: Boolean, dark: Boolean) {
        if (active) mutateActiveProfile { it.copy(dark = dark) }
        else writeScope.launch { themePrefs.setDark(dark) }
    }

    /**
     * Edit ONE pool override slot (poolIndex → unsigned-32 ARGB, or null to clear) — active profile, else
     * global. Read-modify-write of the sparse [Profile.poolOverrides] map inside the durable [mutateActiveProfile].
     */
    fun setActiveOverride(active: Boolean, index: Int, argb: Long?) {
        val key = index.toString()
        if (active) {
            mutateActiveProfile { p ->
                val next = p.poolOverrides.toMutableMap()
                if (argb == null) next.remove(key) else next[key] = argb and 0xFFFFFFFFL
                p.copy(poolOverrides = next)
            }
        } else {
            // WR-02: do the read-modify-write inside ThemePrefs' single edit so two fast slot edits can't
            // each re-encode a stale snapshot and drop one (the same lost-update fix mutateActive applies).
            writeScope.launch {
                themePrefs.mutateOverrides { current ->
                    val next = current.toMutableMap()
                    if (argb == null) next.remove(key) else next[key] = argb and 0xFFFFFFFFL
                    next
                }
            }
        }
    }

    /**
     * Edit ONE status-slot override (D-03 — Stop/Caution/Go → unsigned-32 ARGB, or null to clear) —
     * active profile, else global. The status keys ride the SAME String-keyed override map as the pool
     * indices (reserved [StatusSlot.key] strings cannot collide with "0".."63"), so this mirrors
     * [setActiveOverride] exactly — a read-modify-write of [Profile.poolOverrides] (active) / the
     * [ThemePrefs] global map (idle) inside ONE durable edit.
     *
     * MANDATORY [[dinghy-compose-write-scope-cancellation]]: routes through the process-lifetime
     * [writeScope] + [mutateActiveProfile]/[ThemePrefs.mutateOverrides] ONLY — NEVER a composition
     * `rememberCoroutineScope()` (a same-frame nav cancels the write on slow flash → silently dropped).
     * The edit re-emits through [seedTheme]'s single [ThemeResolver.apply], where the override is applied
     * MODE-GATED (it only changes the rendered status color in Colorful — D-04).
     */
    fun setActiveStatusOverride(active: Boolean, slot: StatusSlot, argb: Long?) {
        val key = slot.key
        if (active) {
            mutateActiveProfile { p ->
                val next = p.poolOverrides.toMutableMap()
                if (argb == null) next.remove(key) else next[key] = argb and 0xFFFFFFFFL
                p.copy(poolOverrides = next)
            }
        } else {
            writeScope.launch {
                themePrefs.mutateOverrides { current ->
                    val next = current.toMutableMap()
                    if (argb == null) next.remove(key) else next[key] = argb and 0xFFFFFFFFL
                    next
                }
            }
        }
    }

    /**
     * Reset the theme to the validated defaults (D-09) — clears seed/mode/shift/overrides back to
     * the out-of-box tuple. Active profile, else global. fsChoice is a SEPARATE setting and is NOT reset.
     * maxItems is no longer a per-profile axis (D-17 / 28-04) — nothing to reset.
     */
    fun resetActiveTheme(active: Boolean) {
        if (active) {
            mutateActiveProfile {
                it.copy(
                    seedHex = ThemePrefs.DEFAULT_SEED,
                    dark = true,
                    paletteMode = ThemePrefs.DEFAULT_MODE,
                    poolShift = ThemePrefs.DEFAULT_SHIFT,
                    poolOverrides = emptyMap(),
                )
            }
        } else {
            // WR-03: ONE atomic edit (not six sequential ones) so tupleFlow emits once — no partial-reset
            // flicker as the idle theme re-themes through intermediate half-reset tuples.
            writeScope.launch { themePrefs.resetToDefaults() }
        }
    }

    companion object {
        /**
         * PURE Webcam-tile gate (MEDIUM-4) — the tile is live ONLY when the printer has cams AND the
         * app-global webcam toggle is on. Host-testable with no flow/IO; the [webcamTileEnabled] flow wraps it.
         */
        fun webcamTileGate(count: Int, webcamEnabled: Boolean): Boolean = count > 0 && webcamEnabled

        /**
         * PURE Connection apiKey edit resolution (MEDIUM-5/V7) — the single source of truth for what a
         * Connection save writes for the key, so the raw stored key never has to round-trip into the UI:
         *  - [cleared] (an explicit Clear) → `null` (remove the key) — Clear wins even over a typed field;
         *  - blank [fieldInput] (and NOT cleared) → [existing] (PRESERVE — a save without retyping keeps it);
         *  - non-blank [fieldInput] → [fieldInput] (REPLACE).
         */
        fun resolveApiKeyEdit(existing: String?, fieldInput: String, cleared: Boolean): String? = when {
            cleared -> null
            fieldInput.isBlank() -> existing
            else -> fieldInput
        }
    }
}
