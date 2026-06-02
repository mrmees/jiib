package works.mees.dinghy.ui.printstatus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import works.mees.dinghy.state.LastJob
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.parseLastJob

/**
 * Session-scoped, one-shot-ON-IDLE print-history fetcher (260601-th9 Inc 3). Watches the live
 * [printerState] and fires a single `server.history.list?limit=1&order=desc` read (via the injected
 * [fetch] seam) exactly when the printer ENTERS a not-printing state — parses it with [parseLastJob]
 * and publishes on [lastJob], which the idle Status field consumes to render the "last completed job"
 * card (or the empty state when null). Mirrors [PrintMetadataHolder]'s one-shot discipline.
 *
 * ## Fetch-once-on-idle semantics (the whole point)
 * "printing" = `Printing || Paused`; everything else (Standby/Complete/Error/Cancelled) is not-printing.
 * The holder tracks [fetchedSinceIdle] — whether a fetch has SUCCEEDED since the printer last entered
 * this not-printing period — and fetches while not-printing until one succeeds:
 *  - on the initial idle connect, AND
 *  - on every printing→not-printing transition (a completed print → refresh the card; the flag is reset
 *    whenever printing resumes).
 * It does NOT fetch while printing, and once a fetch SUCCEEDS it does not re-fetch on a steady idle
 * stream (NO polling).
 *
 * ## Best-effort, never fatal — and latch only on success (mirrors the Inc 2 metadata holder)
 * [fetch] is wrapped in `runCatching`; a failed/no-connection read returns null, leaves [lastJob] at its
 * PRIOR value (the card stays put), does NOT throw into the collector, and — critically — does NOT latch
 * [fetchedSinceIdle], so the NEXT idle emission retries. This is load-bearing: the holder collects the
 * SEED PrinterState (idle) before the socket is bound, so the first fetch always throws; latching on that
 * failure would strand the empty state forever against a printer that has history. A non-null result
 * (success, INCLUDING `count==0` which parses to null) latches the flag.
 *
 * Plain Kotlin (no Compose, no socket) — host-unit-testable with an injected [fetch] lambda and a
 * substitutable `StateFlow<PrinterState>`, exactly the injectable-rpc discipline the session uses.
 *
 * @param scope the lifecycle scope the `printerState` collect runs on (the service supplies it).
 * @param printerState the live (already-throttled) printer state stream.
 * @param fetch the single suspend read seam — returns the `server.history.list` `result` element
 *   (NOT wrapped in `status`), or null on best-effort failure / no connection.
 */
class LastJobHolder(
    scope: CoroutineScope,
    printerState: StateFlow<PrinterState>,
    private val fetch: suspend () -> JsonElement?,
) {
    /** Has a fetch SUCCEEDED since the printer last entered this not-printing period? Reset to false
     *  whenever printing resumes (so a completed print re-fetches); latched true only on a non-null
     *  result (so a failed pre-connect fetch retries on the next idle emission). */
    private var fetchedSinceIdle = false

    private val _lastJob = MutableStateFlow<LastJob?>(null)
    /** The most-recent completed job; null when no history / unavailable (the empty-state signal). */
    val lastJob: StateFlow<LastJob?> = _lastJob.asStateFlow()

    init {
        scope.launch {
            printerState.collect { state ->
                val printing =
                    state.printState == PrintState.Printing || state.printState == PrintState.Paused

                if (printing) {
                    // Printing → the next not-printing period should fetch a fresh last-job.
                    fetchedSinceIdle = false
                } else if (!fetchedSinceIdle) {
                    // Not printing and no successful fetch yet this idle period → attempt it. Latch ONLY
                    // on a real response (success, incl. count==0 → null empty-state); a failed/no-
                    // connection fetch returns null and we retry on the next idle emission (connect race).
                    val result = runCatching { fetch() }.getOrNull()
                    if (result != null) {
                        _lastJob.value = parseLastJob(result.jsonObject)
                        fetchedSinceIdle = true
                    }
                }
            }
        }
    }
}
