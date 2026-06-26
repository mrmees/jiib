package works.mees.jiib.webcam

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.Webcam
import works.mees.jiib.state.parseWebcamsList

/**
 * Session-scoped, one-shot-PER-HANDSHAKE webcam enumeration fetcher (CAM-01, plan 10-03). Watches the
 * live [connectionState] and fires a single `server.webcams.list` read (via the injected [fetch] seam)
 * on EACH handshake edge — the rising transition INTO [ConnectionState.Connected] — parses it with
 * [parseWebcamsList], and publishes the result on [webcams]. The spine forwards [webcams] off the
 * [SpineHandle][works.mees.jiib.di.SpineHandle], exactly the `metadata`/`lastJob` one-shot precedent
 * (the service captures the session's `rpc` and `rpc.request(...)`s it; nothing reaches a raw rpc off
 * the handle).
 *
 * ## Edge-driven once-per-handshake (the whole point — cadence contract Rule 3, T-10-07)
 * [ConnectionState] reaches [ConnectionState.Connected] EXACTLY ONCE per completed handshake: once on the
 * initial connect, and once again on the in-session re-handshake the session runs after a
 * `notify_klippy_ready` (a FIRMWARE_RESTART / SAVE_CONFIG reload re-emits Syncing → Connected). The
 * holder tracks the PRIOR state and fetches ONLY on the `!Connected → Connected` rising edge, so:
 *  - the enumeration fires ONCE per handshake (request hit-count == 1 after the first edge, 2 after a
 *    second), never on a wall-clock timer and never on a steady stream of repeated Connected emissions;
 *  - it is NOT a subscribe and NOT a poll — it never wakes the FGS in a loop (the one-shot enumeration
 *    is the inverse of the always-on stream a webcam-feed would be).
 *
 * This is the same Rule-3 regression class the Phase-13 KlippyReadyResync re-handshake test protects
 * against — re-running the read on every handshake edge (so a printer that gained/lost a cam across a
 * restart is re-enumerated) WITHOUT degrading into a poll.
 *
 * ## Best-effort, never fatal (mirrors the 05-03 / Inc-2 / Inc-3 one-shot reads)
 * [fetch] is wrapped in `runCatching`; a rejected/absent `server.webcams.list` (a printer without the
 * webcam component, an auth failure, a malformed payload) leaves [webcams] EMPTY — the greyed-tile signal
 * (D-08), never an exception into the collector. [parseWebcamsList] is itself tolerant (Security V5): a
 * structurally malformed result yields an empty list, a single bad cam is dropped without blanking the
 * rest. A subsequent handshake edge re-attempts the read.
 *
 * Plain Kotlin (no Compose, no socket) — host-unit-testable with an injected [fetch] lambda and a
 * substitutable `StateFlow<ConnectionState>`, exactly the injectable-rpc discipline the session uses.
 *
 * @param scope the lifecycle scope the `connectionState` collect runs on (the service supplies it).
 * @param connectionState the live session connection lifecycle (CONN-06).
 * @param fetch the single suspend read seam — returns the `server.webcams.list` `result` element (NOT
 *   wrapped in `status`), or null on best-effort failure / no connection.
 */
class WebcamsHolder(
    scope: CoroutineScope,
    connectionState: StateFlow<ConnectionState>,
    private val fetch: suspend () -> JsonElement?,
) {
    /** The prior connection state — used to detect the `!Connected → Connected` RISING edge. */
    private var wasConnected = false

    private val _webcams = MutableStateFlow<List<Webcam>>(emptyList())
    /** The enumerated webcams for the current handshake; empty when none / unavailable (D-08). */
    val webcams: StateFlow<List<Webcam>> = _webcams.asStateFlow()

    init {
        scope.launch {
            connectionState.collect { state ->
                val connected = state is ConnectionState.Connected
                if (connected && !wasConnected) {
                    // Rising edge INTO Connected = one handshake completed → fire the one-shot enumeration.
                    // Best-effort: a rejected/absent read leaves the list empty (greyed tile), never throws.
                    val result = runCatching { fetch() }.getOrNull()
                    _webcams.value = parseWebcamsList(result)
                }
                wasConnected = connected
            }
        }
    }
}
