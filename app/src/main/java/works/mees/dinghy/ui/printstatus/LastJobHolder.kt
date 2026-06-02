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
 * The holder caches [wasPrinting] (null = no emission seen yet) and fetches ONLY when entering a
 * not-printing state:
 *  - on the FIRST emission if the printer is already idle (initial idle connect), AND
 *  - on every printing→not-printing transition (a completed print → refresh the card).
 * It does NOT fetch while printing, and does NOT re-fetch on repeated not-printing emissions — the
 * `wasPrinting == null || wasPrinting == true` guard makes a steady idle stream a no-op (NO polling).
 *
 * ## Best-effort, never fatal (mirrors the 05-03 one-shot reads / Inc 2 metadata holder)
 * [fetch] is wrapped in `runCatching`; a null/failed read leaves [lastJob] at its PRIOR value (the card
 * stays put) and does NOT throw into the collector — the next not-printing transition retries. A
 * `count==0` result parses to null (the empty-state signal).
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
    /** null = no emission seen yet; true = was printing; false = was not-printing (idle). */
    private var wasPrinting: Boolean? = null

    private val _lastJob = MutableStateFlow<LastJob?>(null)
    /** The most-recent completed job; null when no history / unavailable (the empty-state signal). */
    val lastJob: StateFlow<LastJob?> = _lastJob.asStateFlow()

    init {
        scope.launch {
            printerState.collect { state ->
                val printing =
                    state.printState == PrintState.Printing || state.printState == PrintState.Paused

                // Fetch on entering a not-printing state: first-ever emission if already idle, OR a
                // printing→idle edge. Never while printing; never on a repeated idle emission (no poll).
                if (!printing && (wasPrinting == null || wasPrinting == true)) {
                    runCatching { fetch() }.getOrNull()?.let { result ->
                        _lastJob.value = parseLastJob(result.jsonObject)
                    }
                }
                wasPrinting = printing
            }
        }
    }
}
