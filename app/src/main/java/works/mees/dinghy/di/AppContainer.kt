package works.mees.dinghy.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.config.ConnectionStore
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.ThemePrefs
import works.mees.dinghy.theme.ThemeResolver

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
) {
    /** Theme persistence (THEME-02/D-02) — the theme.preferences_pb-backed store. */
    val themePrefs: ThemePrefs = ThemePrefs(themeDataStore)

    /** Connection persistence (CONN-01) — the SEPARATE connection.preferences_pb-backed store. */
    val connectionStore: ConnectionStore = ConnectionStore(connectionDataStore)

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

    /** True once a usable persisted connection exists (drives routing off the Connect prompt, D-11). */
    val hasConfig: Flow<Boolean> = connectionStore.config.map { it != null }

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
     * Seed the resolver once from persisted theme prefs (mirrors GalleryActivity's seed). Call from the
     * Application on a long-lived scope; the first emission applies (base, deltas, fs) in one re-emit.
     */
    fun seedTheme(scope: CoroutineScope) {
        scope.launch {
            themePrefs.flow.collect { resolved ->
                themeResolver.apply(resolved.base, resolved.deltas, resolved.fs)
            }
        }
    }
}
