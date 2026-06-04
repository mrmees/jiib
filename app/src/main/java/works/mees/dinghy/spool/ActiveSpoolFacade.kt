package works.mees.dinghy.spool

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import works.mees.dinghy.state.ConnectionState

/**
 * Session-scoped, edge-driven + notify-reconciled ACTIVE-SPOOL truth (SPOOL-01/08; D-10). The Spoolman
 * analogue of [works.mees.dinghy.webcam.WebcamsHolder] — same template: an injectable [fetchStatus]
 * read seam, a substitutable [connectionState] StateFlow, a one-shot-per-handshake rising-edge fetch,
 * and a published [activeSpool] StateFlow the spine forwards off the
 * [SpineHandle][works.mees.dinghy.di.SpineHandle]. Plain Kotlin (no Compose, no socket) so it is
 * host-unit-testable exactly like WebcamsHolder.
 *
 * ## Three collectors (the whole point)
 *  - **(a) handshake-edge fetch** — on the `!Connected → Connected` RISING edge (one completed
 *    handshake), fire the one-shot `server.spoolman.status` read via [fetchStatus] and decode it into
 *    [activeSpool] via [parseSpoolmanStatus]. Best-effort (`runCatching`): a rejected/absent read
 *    leaves the prior value, never throws. Re-runs on every handshake edge (re-connect / klippy_ready
 *    re-handshake) so an externally-changed spool is re-read, WITHOUT degrading into a poll.
 *  - **(b) notify_active_spool_set reconcile (D-10)** — an external mutator (Fluidd, a runout macro)
 *    pushed a new active spool id. OVERWRITE the local id with the pushed `spool_id` (null = cleared);
 *    NEVER re-assert stale local state. We preserve the rest of the prior status (connected/pending)
 *    and only swap the id — the pushed frame carries only the id.
 *  - **(c) notify_spoolman_status_changed re-fetch (D-10)** — the Spoolman backend connect/disconnect
 *    changed; a status push may imply connected/pending changes the 1-field notify doesn't carry, so
 *    RE-READ status for truth (best-effort).
 *
 * @param scope the lifecycle scope the collectors run on (the service supplies [serviceScope]).
 * @param connectionState the live session connection lifecycle (CONN-06) — the handshake-edge source.
 * @param activeSpoolSet the `notify_active_spool_set` payloads (`params[0]` = `{spool_id}`) from the client.
 * @param spoolmanStatusChanged the `notify_spoolman_status_changed` payloads (`params[0]`) from the client.
 * @param fetchStatus the single suspend read seam — returns the `server.spoolman.status` `result`
 *   element (the bare status object, NOT a proxy-v2 envelope), or null on best-effort failure.
 */
class ActiveSpoolFacade(
    scope: CoroutineScope,
    connectionState: StateFlow<ConnectionState>,
    activeSpoolSet: SharedFlow<JsonObject>,
    spoolmanStatusChanged: SharedFlow<JsonObject>,
    private val fetchStatus: suspend () -> JsonElement?,
) {
    /** The prior connection state — used to detect the `!Connected → Connected` RISING edge. */
    private var wasConnected = false

    private val _activeSpool = MutableStateFlow<SpoolmanStatus?>(null)
    /** The current active-spool status; null until the first handshake fetch lands / unavailable. */
    val activeSpool: StateFlow<SpoolmanStatus?> = _activeSpool.asStateFlow()

    init {
        // (a) Edge-driven one-shot-per-handshake status fetch (the WebcamsHolder wasConnected pattern).
        scope.launch {
            connectionState.collect { state ->
                val connected = state is ConnectionState.Connected
                if (connected && !wasConnected) {
                    refetchStatus()
                }
                wasConnected = connected
            }
        }

        // (b) notify_active_spool_set → reconcile to the pushed id (D-10 — overwrite, never stale).
        scope.launch {
            activeSpoolSet.collect { params ->
                val pushedId = runCatching { params["spool_id"]?.jsonPrimitive?.intOrNull }.getOrNull()
                // Overwrite ONLY the id; preserve the rest of the prior status (the notify carries id only).
                val prior = _activeSpool.value ?: SpoolmanStatus()
                _activeSpool.value = prior.copy(activeSpoolId = pushedId)
            }
        }

        // (c) notify_spoolman_status_changed → re-read status for truth (D-10).
        scope.launch {
            spoolmanStatusChanged.collect {
                refetchStatus()
            }
        }
    }

    /** Best-effort `server.spoolman.status` read → [parseSpoolmanStatus] → [_activeSpool]; never throws. */
    private suspend fun refetchStatus() {
        val result = runCatching { fetchStatus() }.getOrNull()
        _activeSpool.value = parseSpoolmanStatus(result)
    }
}
