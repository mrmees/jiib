package works.mees.jiib.ui.printstatus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import works.mees.jiib.state.PrintMetadata
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.parsePrintMetadata

/**
 * Session-scoped, one-shot-per-filename gcode-metadata fetcher (260601-sip Inc 2). Watches the live
 * [printerState] and, the moment a print becomes active under a NEW filename, fires a single
 * `server.files.metadata` read (via the injected [fetch] seam), parses it with [parsePrintMetadata],
 * and publishes the result on [metadata]. The Status home consumes [metadata] to light up the ring
 * thumbnail and the Layer/Z/Remaining cells.
 *
 * ## One-shot semantics (the whole point)
 * Every `notify_status_update` delta carries `print_stats.filename`, so the active filename re-emits
 * constantly while printing. The holder caches [lastKey] and fetches ONLY on a transition to a
 * non-blank filename that DIFFERS from the cached key — identical re-emits are cheap no-ops past the
 * `active != lastKey` guard. A filename CHANGE (next print) re-keys and re-fetches. The key stays
 * live through the TERMINAL states (complete/cancelled/error) so the Terminal result hero/stats keep
 * the finished print's thumbnail+metadata; only true STANDBY (idle / new session) blanks the key,
 * clearing [metadata] to null AND resetting [lastKey] so the NEXT print re-fetches.
 *
 * ## Best-effort, never fatal (mirrors the 05-03 one-shot reads)
 * [fetch] is wrapped in `runCatching`; a null/failed read leaves [metadata] null (the cells degrade,
 * the ring keeps Benchy) and does NOT throw into the collector — a later filename change still
 * retries. The fetch runs on the collect coroutine but is a single suspend call per key, so the
 * store's 250ms conflation means re-emits of the same filename never pile up work.
 *
 * Plain Kotlin (no Compose, no socket) — host-unit-testable with an injected [fetch] lambda and a
 * substitutable `StateFlow<PrinterState>`, exactly the injectable-rpc discipline the session uses.
 *
 * @param scope the lifecycle scope the `printerState` collect runs on (the service supplies it).
 * @param printerState the live (already-throttled) printer state stream.
 * @param fetch the single suspend read seam — returns the `server.files.metadata` `result` element
 *   (NOT wrapped in `status`), or null on best-effort failure / no connection.
 */
class PrintMetadataHolder(
    scope: CoroutineScope,
    printerState: StateFlow<PrinterState>,
    private val fetch: suspend (filename: String) -> JsonElement?,
) {
    private var lastKey: String? = null

    private val _metadata = MutableStateFlow<PrintMetadata?>(null)
    /** Parsed metadata for the active print; null when idle / unavailable. */
    val metadata: StateFlow<PrintMetadata?> = _metadata.asStateFlow()

    init {
        scope.launch {
            printerState.collect { state ->
                // Keep the key live through the TERMINAL states (Complete/Cancelled/Error) too, not just
                // Printing/Paused — so the Terminal result hero + stats can still show the FINISHED print's
                // thumbnail/metadata. Only true Standby (idle / new session) blanks the key, which clears
                // metadata and lets the NEXT print re-fetch. (2026-06-06 UAT: Terminal showed the app icon
                // because metadata was nulled the instant the print ended.)
                val active = if (state.printState == PrintState.Standby) "" else state.printFilename

                if (active.isBlank()) {
                    // Transitioned to Standby (or no filename) — clear and reset so the next print re-fetches.
                    if (lastKey != null) {
                        _metadata.value = null
                        lastKey = null
                    }
                } else if (active != lastKey) {
                    // New active filename — one-shot fetch, best-effort, never throws into the collector.
                    lastKey = active
                    runCatching { fetch(active) }.getOrNull()?.let { result ->
                        _metadata.value = parsePrintMetadata(result.jsonObject)
                    }
                }
            }
        }
    }
}
