package works.mees.jiib.prompt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import works.mees.jiib.command.DispatchEvent
import works.mees.jiib.command.PrinterCommands
import works.mees.jiib.net.JsonRpcMethods
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.PrinterStateStore
import kotlinx.serialization.json.JsonElement

/**
 * The spine-level Macro Prompt Protocol holder (PROMPT-01 tolerant-under-a-live-stream; PROMPT-03
 * re-entrancy / never-wedge under real ordering). The wiring centerpiece that bridges the PROVEN pure
 * reducer (12-02) to the live session WITHOUT owning any transport (D-13): it is an INDEPENDENT collector
 * on the same un-throttled [PrinterStateStore.gcodeResponses] SharedFlow the Console reads, so the console
 * filters never starve it (T-12-10), and the parser is total (12-01) so a garbage stream cannot wedge the
 * collect loop.
 *
 * Mirrors the [works.mees.jiib.calibration.ProbeCalibrateHolder] template: ctor(scope, store, events?),
 * a [MutableStateFlow] exposed read-only as a [StateFlow], `init { scope.launch { … } }` collects, NO
 * second throttle. Plain Kotlin — NO `androidx.compose.runtime` import (ADR-0001) — so it is
 * host-unit-testable ([works.mees.jiib.prompt.PromptEngineTest]).
 *
 * ## Disconnect is a LOCAL close that dispatches NOTHING (D-10 / Pitfall 6 — T-12-09)
 * On a Connected → Disconnected/Error edge the engine runs the reducer's disconnect path (clears the
 * prompt + pending + active container) and emits the resulting hidden view. It dispatches NO `prompt_end`
 * gcode — a local teardown (disconnect / process-death) must NOT broadcast a cross-client side effect that
 * would close the prompt on KlipperScreen / Mainsail / Fluidd. A [ConnectionState.Syncing] /
 * [ConnectionState.Connecting] transition does NOT close the prompt (Assumption A3 — only
 * Disconnected/Error trigger the local close).
 *
 * ## Dispatch-key bookkeeping (Pitfall 4 / T-12-11)
 * The engine owns the stable [CommandDispatcher] keys the overlay dispatches under so the busy/debounce
 * guard stays correct across recomposition; the `epoch` isolates a replaced prompt's keys from the prior
 * one. Content buttons and footer buttons live in SEPARATE key namespaces ([buttonKey] vs [footerKey]) so
 * a content button and a footer button at the same index NEVER collide on the same dispatcher key (a Codex
 * pre-execute finding — a same-index collision would let an in-flight content button debounce-suppress the
 * wrong footer control). The engine does NOT call dispatch itself for buttons/close — it exposes keys +
 * params so the AppShell wires [works.mees.jiib.command.CommandDispatcher.dispatch] (12-05) — but it
 * MUST guarantee NO dispatch happens on disconnect.
 *
 * @param scope  the lifecycle scope the collects run on (the UI host supplies it).
 * @param store  the already-assembled Phase-2/9 spine; the engine CONSUMES it, never opens a session.
 * @param events the live session's dispatcher event stream (a `prompt:`-keyed Failure folds into
 *               [latestPromptError] for the overlay toast); null skips the fold.
 * @param opts   the engine identity + behavior (Dinghy = `jiib` + `[touch]`, liveAppend; D-08/D-02).
 */
class PromptEngine(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
    events: SharedFlow<DispatchEvent>? = null,
    opts: PromptOpts = PromptOpts(),
) {
    /** The full internal reducer state — only [view] is exposed; the renderer must NEVER see this. */
    private var state: PromptStateData = initialPromptState(opts)

    private val _view = MutableStateFlow(promptView(state))
    /** The 6-key conformance projection the shell observes (visible/title/targets/size/items/footer). */
    val view: StateFlow<PromptView> = _view.asStateFlow()

    /** The latest `prompt:`-keyed dispatcher Failure (redacted by [CommandDispatcher]) for the overlay toast. */
    @Volatile
    var latestPromptError: String? = null
        private set

    /** The stable dispatcher key for the close control (Pitfall 4 — fixed across recomposition). */
    val closeKey: String = "prompt:close"

    /** The `action:prompt_end` gcode the close control dispatches (a LOCAL user close emits it; disconnect does NOT). */
    val closeGcode: String = """RESPOND TYPE=command MSG="action:prompt_end""""

    init {
        // (1) Subscribe the un-throttled raw gcode stream (the SAME SharedFlow the Console reads, as an
        // INDEPENDENT collector — D-13). parseAction is total (12-01) and reduce never wedges (12-02), so
        // a garbage / out-of-order line degrades deterministically; the loop never throws.
        scope.launch {
            store.gcodeResponses.collect { line ->
                parseAction(line)?.let { event ->
                    state = reduce(state, event)
                    _view.value = promptView(state)
                }
            }
        }

        // (2) Disconnect (D-10 / Pitfall 6): on a Connected → Disconnected/Error EDGE run the reducer's
        // LOCAL disconnect path (clears prompt + pending + container) and emit the hidden view. Dispatch
        // NOTHING — a local teardown must not close the prompt on other connected clients (T-12-09). A
        // transition to Syncing/Connecting does NOT close (A3). `distinctUntilChanged` + the prior-edge
        // guard so only a true Connected→down edge triggers (not the first idle emission).
        scope.launch {
            var wasConnected = false
            store.printerState
                .map { it.connection }
                .distinctUntilChanged()
                .collect { connection ->
                    val isDown = connection is ConnectionState.Disconnected || connection is ConnectionState.Error
                    if (isDown && wasConnected) {
                        // LOCAL close: reducer disconnect path only — NO CommandDispatcher.dispatch here.
                        state = reduce(state, disconnectEvent())
                        _view.value = promptView(state)
                    }
                    wasConnected = connection is ConnectionState.Connected
                }
        }

        // (3) Fold dispatcher Failure events whose key belongs to THIS prompt into the redacted error text
        // (the overlay toast). The message is already redacted by CommandDispatcher (T-12-12); the engine
        // never builds an error from raw exception text. A non-`prompt:` key is ignored.
        if (events != null) {
            scope.launch {
                events.collect { event ->
                    if (event is DispatchEvent.Failure && event.key.startsWith("prompt:")) {
                        latestPromptError = event.message
                    }
                }
            }
        }
    }

    /**
     * The stable dispatcher key for the content button at [index] of the current prompt:
     * `prompt:<epoch>:<index>`. The epoch isolates a replaced prompt's keys from the prior one (Pitfall 4).
     */
    fun buttonKey(index: Int): String = "prompt:${state.epoch}:$index"

    /**
     * The stable dispatcher key for the FOOTER button at [index]: `prompt:<epoch>:footer:<index>` — a
     * SEPARATE namespace from [buttonKey] so a content button and a footer button at the same index NEVER
     * share a [CommandDispatcher] key (Codex pre-execute finding: a same-index collision would let an
     * in-flight content button debounce-suppress the wrong footer control).
     */
    fun footerKey(index: Int): String = "prompt:${state.epoch}:footer:$index"

    /** The JSON-RPC method every prompt gcode dispatch uses ([JsonRpcMethods.GCODE_SCRIPT]). */
    val dispatchMethod: String = JsonRpcMethods.GCODE_SCRIPT

    /** Wrap a button/close [gcode] into the `{"script": …}` params the overlay dispatches (reuses [PrinterCommands]). */
    fun scriptParamsFor(gcode: String): JsonElement = PrinterCommands.scriptParams(gcode)
}
