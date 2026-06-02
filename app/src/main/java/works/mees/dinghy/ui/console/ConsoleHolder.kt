package works.mees.dinghy.ui.console

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import works.mees.dinghy.state.ConsoleScrollback

/**
 * The toolkit-agnostic console state holder (CONS-02 / D-02 / D-04) — mirrors
 * `ui/files/FileBrowserHolder.kt`'s StateFlow-holder shape.
 *
 * It collects TWO already-wired seams from `PrinterStateStore` (no new transport — the live
 * `notify_gcode_response` path is plumbed end-to-end):
 *  - [gcodeResponses] — the live, un-throttled raw line stream. Each line is classified via
 *    [ConsoleSeverity.classify] into a [ConsoleLine] and pushed onto the bounded [ConsoleScrollback]
 *    ring; the snapshot is re-emitted.
 *  - [consoleBackfill] — the `server.gcode_store` snapshot landed on every (re)connect and on the
 *    `notify_klippy_ready` re-handshake. Each emission REPLACES the whole ring (Mainsail-parity
 *    Option A, D-02) so disconnect-window lines the server still holds are recovered without any
 *    append/dedup logic.
 *
 * D-04 (LOAD-BEARING): [state] is the RAW (unfiltered) line list. The holder NEVER filters — the
 * `ConsoleScreen` applies [ConsoleFilters] at render only, so the raw stream survives a toggle-off
 * and neither the backfill nor the Phase-12 prompt engine is ever starved by what the console hides.
 *
 * Both args are typed as [SharedFlow] (the store's `consoleBackfill` is a `StateFlow`, which IS a
 * `SharedFlow`) so the holder is decoupled from the store concrete type and trivially fakeable in a
 * host test (`ConsoleHolderTest`).
 *
 * @param scope the holder's collection scope (one per holder lifetime; cancelled by the caller).
 * @param gcodeResponses the live raw line stream (`PrinterStateStore.gcodeResponses`).
 * @param consoleBackfill the (re)connect snapshot stream (`PrinterStateStore.consoleBackfill`).
 * @param capacity the scrollback bound; defaults to Moonraker's `gcode_store_size` (1000).
 */
class ConsoleHolder(
    scope: CoroutineScope,
    gcodeResponses: SharedFlow<String>,
    consoleBackfill: SharedFlow<List<ConsoleLine>>,
    capacity: Int = ConsoleScrollback.DEFAULT_CAPACITY,
) {
    private val ring = ConsoleScrollback(capacity)

    private val _state = MutableStateFlow<List<ConsoleLine>>(emptyList())

    /** The RAW (unfiltered) scrollback snapshot, oldest→newest (D-04). */
    val state: StateFlow<List<ConsoleLine>> = _state.asStateFlow()

    // The two collectors below are NON-TERMINATING by design (the live stream never completes). We run
    // them in a child scope whose SupervisorJob is NOT a child of [scope]'s Job, so [scope] is never
    // blocked waiting for them to finish — it inherits [scope]'s dispatcher (so a test's
    // StandardTestDispatcher still drives them deterministically), but its lifetime is bounded by
    // [scope]'s cancellation via [bindToParentLifecycle], not by structured-concurrency await. This is
    // what lets the TestScope-rooted ConsoleHolderTest (which passes `this`, not `backgroundScope`)
    // finish without an UncompletedCoroutinesError while production cancellation still propagates.
    private val collectorJob = SupervisorJob()
    private val collectorScope = CoroutineScope(scope.coroutineContext + collectorJob)

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion { collectorJob.cancel() }

        // start = UNDISPATCHED so each collector subscribes to its SharedFlow SYNCHRONOUSLY at
        // construction — before any producer emits. The live `gcodeResponses` SharedFlow has replay=0,
        // so a value emitted before subscription would be lost; subscribing eagerly closes that race
        // (and is what makes the StandardTestDispatcher-driven ConsoleHolderTest deterministic).
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // RAW, upstream of any filter (D-04): classify once, push, re-emit.
            gcodeResponses.collect { line -> appendRaw(line) }
        }
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Backfill REPLACE (D-02): the snapshot is authoritative and supersedes prior content.
            consoleBackfill.collect { snapshot -> replaceRaw(snapshot) }
        }
    }

    /** Classify [line] from its original prefix, push onto the ring, and re-emit the snapshot. */
    private fun appendRaw(line: String) {
        ring.push(ConsoleLine(rawMessage = line, severity = ConsoleSeverity.classify(line), timeEpoch = null))
        _state.value = ring.snapshot()
    }

    /** Replace the whole ring with [snapshot] (already-classified lines) and re-emit. */
    private fun replaceRaw(snapshot: List<ConsoleLine>) {
        ring.replaceAll(snapshot)
        _state.value = ring.snapshot()
    }

    /**
     * Cancel the detached collectors NOW (WR-01). The host calls this when `remember(store)` swaps this
     * holder for a new one on a spine rebuild (reconnect): without it, this discarded holder's two
     * collectors keep collecting the dead session's flows until the whole shell leaves composition,
     * orphaning one collector pair per reconnect. Cancelling [collectorJob] here is idempotent and
     * coexists with the [scope]-cancellation path ([invokeOnCompletion]) that handles shell teardown.
     */
    fun cancel() {
        collectorJob.cancel()
    }
}
