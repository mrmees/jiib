package works.mees.dinghy.ui.macros

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import works.mees.dinghy.state.Capabilities

/**
 * The view-model state the three macro screens render (MACRO-01/02/03, D-06/D-07).
 *
 * @param macros          ALL discovered macros (the System manager's full universe before the
 *                        underscore-hide filter is applied). Empty when the printer reports none.
 * @param visibleMacros   the System list as the user should SEE it: [macros] minus underscore-prefixed
 *                        helpers UNLESS `revealHidden` is on (MACRO-03 underscore-default-hide).
 * @param bookmarkedMacros the Bookmarked launcher's universe: ONLY macros the user pinned (D-07).
 * @param revealHidden    whether underscore-prefixed helpers are revealed in the System list.
 * @param unavailable     true when the printer reports NO gcode macros — the screens show the
 *                        no-macros copy instead of dead tiles (capability gate).
 */
data class MacroScreensState(
    val macros: List<MacroVm> = emptyList(),
    val visibleMacros: List<MacroVm> = emptyList(),
    val bookmarkedMacros: List<MacroVm> = emptyList(),
    val revealHidden: Boolean = false,
    val unavailable: Boolean = true,
)

/**
 * Toolkit-agnostic StateFlow holder (mirrors [works.mees.dinghy.ui.files.FileBrowserHolder]) for the
 * macro screens (MACRO-01/03). It `combine`s three reactive sources into one [MacroScreensState]:
 *
 *  1. `Capabilities.macros` — the NAME of each `gcode_macro NAME`, re-derived on every reconnect.
 *  2. `bookmarks` — the user's pinned set (from [MacroPrefs] in production; a stub flow in tests).
 *  3. `revealHidden` — the MACRO-03 underscore-reveal toggle (from [MacroPrefs] / a stub flow).
 *
 * Plus a fourth, locally-owned source: the parsed-param bodies map. The PRODUCTION
 * [works.mees.dinghy.state.PrinterStateStore.macroBodies] stream is fed in by the 08-07 wiring via
 * [setMacroBodies]; tests seed a single body with [setMacroBody]. Each macro's `params` come from
 * running [MacroParamParser.parseMacroParams] over its body (empty list until a body arrives — a
 * macro with no detected params is still launchable, the popup just shows no fields).
 *
 * Capability gate: an empty `Capabilities.macros` sets `unavailable` and yields empty lists — the
 * screens degrade to the no-macros copy rather than render dead tiles.
 *
 * Bookmark lookup uses case-insensitive matching ([Capabilities.hasMacroIgnoreCase] semantics):
 * Moonraker lowercases macro object names, so a stored bookmark "START_PRINT" must match a discovered
 * "start_print". The same case-insensitive key is used to look up parsed bodies.
 */
class MacroHolder(
    scope: CoroutineScope,
    capabilities: StateFlow<Capabilities>,
    bookmarks: StateFlow<Set<String>>,
    revealHidden: StateFlow<Boolean>,
) {
    /** Per-macro gcode bodies keyed by name (case-insensitive lookups go through [bodyFor]). */
    private val _macroBodies = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Per-macro descriptions keyed by name (case-insensitive lookups go through [descriptionFor]). */
    private val _macroDescriptions = MutableStateFlow<Map<String, String>>(emptyMap())

    private val _state = MutableStateFlow(MacroScreensState())
    val state: StateFlow<MacroScreensState> = _state.asStateFlow()

    // The combine collector below is NON-TERMINATING by design (it folds four StateFlows that never
    // complete). Run it in a child scope whose SupervisorJob is NOT a child of [scope]'s Job, so
    // [scope] is never blocked waiting for it to finish under structured concurrency — it inherits
    // [scope]'s dispatcher (so a test's StandardTestDispatcher still drives it deterministically) but
    // its lifetime is bounded by [scope]'s cancellation, not by await. This is the proven 08-05
    // ConsoleHolder pattern: it lets the TestScope-rooted MacroHolderTest (which passes `this`, not
    // `backgroundScope`) finish without an UncompletedCoroutinesError while production cancellation
    // still propagates.
    private val collectorJob = SupervisorJob()
    private val collectorScope = CoroutineScope(scope.coroutineContext + collectorJob)

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion { collectorJob.cancel() }

        // start = UNDISPATCHED so the first combined value is computed SYNCHRONOUSLY at construction
        // (every source is a StateFlow with an immediate current value), making the holder usable the
        // instant it is built and the StandardTestDispatcher-driven test deterministic.
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(
                capabilities,
                bookmarks,
                revealHidden,
                _macroBodies,
                _macroDescriptions,
            ) { caps, marks, reveal, bodies, descriptions ->
                buildState(caps.macros, marks, reveal, bodies, descriptions)
            }.collect { _state.value = it }
        }
    }

    /**
     * Seed/replace ONE macro's gcode body (the test seam; production calls [setMacroBodies]). Triggers
     * a recompute via the combined flow so the macro's parsed `params` populate.
     */
    fun setMacroBody(name: String, body: String) {
        _macroBodies.value = _macroBodies.value + (name to body)
    }

    /**
     * Replace the whole parsed-body map (the production seam — fed from
     * [works.mees.dinghy.state.PrinterStateStore.macroBodies] by the 08-07 shell wiring on every
     * handshake/reconnect).
     */
    fun setMacroBodies(bodies: Map<String, String>) {
        _macroBodies.value = bodies
    }

    /**
     * Replace the whole descriptions map (the production seam — fed from
     * [works.mees.dinghy.state.PrinterStateStore.macroDescriptions] by the shell wiring on every handshake).
     */
    fun setMacroDescriptions(descriptions: Map<String, String>) {
        _macroDescriptions.value = descriptions
    }

    private fun buildState(
        macroNames: List<String>,
        bookmarks: Set<String>,
        reveal: Boolean,
        bodies: Map<String, String>,
        descriptions: Map<String, String>,
    ): MacroScreensState {
        if (macroNames.isEmpty()) {
            return MacroScreensState(revealHidden = reveal, unavailable = true)
        }
        val all = macroNames.map { name ->
            val body = bodyFor(bodies, name)
            MacroVm(
                name = name,
                isBookmarked = bookmarks.any { it.equals(name, ignoreCase = true) },
                isHidden = name.startsWith("_"),
                params = body?.let { MacroParamParser.parseMacroParams(it) } ?: emptyList(),
                description = descriptionFor(descriptions, name),
                usesRawParams = body?.let { MacroParamParser.usesRawParams(it) } ?: false,
            )
        }
        val visible = all.filter { reveal || !it.isHidden }
        val bookmarked = all.filter { it.isBookmarked }
        return MacroScreensState(
            macros = all,
            visibleMacros = visible,
            bookmarkedMacros = bookmarked,
            revealHidden = reveal,
            unavailable = false,
        )
    }

    /** Case-insensitive body lookup (Moonraker lowercases macro names — see [Capabilities]). */
    private fun bodyFor(bodies: Map<String, String>, name: String): String? =
        bodies[name] ?: bodies.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /** Case-insensitive description lookup (Moonraker lowercases macro names). */
    private fun descriptionFor(descriptions: Map<String, String>, name: String): String? =
        descriptions[name] ?: descriptions.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /**
     * Whether this macro's parameters can be considered AUTHORITATIVE yet (WR-03). The Execution popup
     * uses this to tell "this macro genuinely takes no params" (config landed → empty list is real) apart
     * from "the configfile body read hasn't arrived yet on a cold connect" (so it shows a loading state
     * and keeps Execute disabled rather than silently running a parametered macro bare).
     *
     * True when EITHER this macro has its own parsed body, OR the bodies map is non-empty — a populated
     * map means the one-shot `configfile` query landed and was parsed, so a macro absent from it (or
     * present without a `.gcode`) genuinely declares no params. The bodies map starts empty (the shell
     * collects `store.macroBodies` whose initial value is empty), so "non-empty" is the reliable
     * configfile-landed signal without a separate latch.
     *
     * Reads the [MutableStateFlow]'s current `.value` directly; callers recompose off [state] (which the
     * combine folds `_macroBodies` into), so a late body arrival re-runs this on the fresh value.
     */
    fun paramsKnown(name: String): Boolean {
        val bodies = _macroBodies.value
        return bodyFor(bodies, name) != null || bodies.isNotEmpty()
    }

    /**
     * Cancel the detached combine collector NOW (WR-01). The host calls this when `remember(store, …)`
     * swaps this holder for a new one on a spine rebuild (reconnect): without it, this discarded holder's
     * combine collector keeps folding the dead session's flows until the whole shell leaves composition,
     * orphaning one collector per reconnect. Cancelling [collectorJob] here is idempotent and coexists
     * with the [scope]-cancellation path ([invokeOnCompletion]) that handles shell teardown.
     */
    fun cancel() {
        collectorJob.cancel()
    }
}
