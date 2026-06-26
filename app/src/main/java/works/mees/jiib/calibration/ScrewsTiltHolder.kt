package works.mees.jiib.calibration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import works.mees.jiib.command.DispatchEvent
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore
import works.mees.jiib.state.Screw
import works.mees.jiib.state.ScrewConfig
import works.mees.jiib.state.ScrewResult
import works.mees.jiib.state.ScrewsTiltObject

/**
 * Toolkit-agnostic holder for the Screws-Tilt page (CALIB-02 / D-03). Mirrors the
 * [works.mees.jiib.ui.extrude.ExtrudeHolder] template exactly: ctor(scope, store), a
 * `MutableStateFlow` exposed as a read-only [StateFlow], an `init { scope.launch { combine(...) } }`
 * with a PURE [buildVm], and NO second throttle (the store already conflates the high-rate plane at
 * 250 ms). Plain Kotlin — NO Compose annotations (ADR-0001) — so it is host-unit-testable.
 *
 * ## What it combines
 *  - [PrinterStateStore.printerState] — the live `screws_tilt_adjust` results object (CALIB-02 source).
 *  - [PrinterStateStore.screwsTiltConfig] — the one-shot `[screws_tilt_adjust]` config (screw coords +
 *    names, D-04/D-06) read once at handshake (the 09-02 seam). COMBINED in (not snapshotted) so the
 *    guided loop's per-screw NAMES + the to-scale bed COORDS are deterministic the instant the one-shot
 *    read lands, exactly like [works.mees.jiib.ui.extrude.ExtrudeHolder] does with `minExtrudeTemp`.
 *  - [PrinterStateStore.capabilities] (read as `.value`) — gating not strictly needed here (the screen
 *    is only reachable when the routine is rendered) but the homed-gate (D-13) reads off `homed_axes`.
 *
 * ## Verbatim worst-screw (prior-wave correction, commit ef260cb)
 * The holder reconstructs the raw `{"screws_tilt_adjust": {...}}` JSON shape from the reduced
 * [ScrewsTiltObject] + [ScrewConfig] and feeds it to the canonical [parseScrewsTilt] parser, so the
 * worst-screw selection, the `adjust` clock string, `sign`, joined `name`, and the `X of N` count are
 * produced by the ONE place that owns that math. The screen layer renders [GuidedLoopState] VERBATIM —
 * it must NOT re-derive "worst" or reintroduce a `|z|` ranking.
 *
 * ## Done / failed detection (Pattern 2 — never a bare gcode ack)
 *  - "done" is implicit in the populated [GuidedLoopState] (results present, `error == false`).
 *  - "failed" is folded from [works.mees.jiib.command.CommandDispatcher.events]
 *    [DispatchEvent.Failure] — the printer's REDACTED RpcError rejection text (e.g. "bed level exceeds
 *    configured limits") — NEVER parsed out of the feed and NEVER the transport `e.message`
 *    (T-05-11-01 / T-09-04-02). A separate collector keyed on the screws-tilt dispatch key folds the
 *    latest failure into [ScrewsTiltVm.errorText]; it clears on the next successful results edge.
 *
 * @param scope       the lifecycle scope the combine/collect runs on (the UI host supplies it).
 * @param store       the already-assembled Phase-2/9 spine; the holder CONSUMES it, never opens a session.
 * @param events      the live session's dispatcher event stream (Failure → [ScrewsTiltVm.errorText]).
 * @param dispatchKey the dispatch key the screws-tilt Run uses (default "screws_tilt"; matches
 *                    `CommandRegistry.screwsTiltCalculate.key`), so only THIS routine's failures surface.
 */
class ScrewsTiltHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
    events: SharedFlow<DispatchEvent>? = null,
    private val dispatchKey: String = DEFAULT_DISPATCH_KEY,
) {
    private val _vm = MutableStateFlow(ScrewsTiltVm())
    /** The resolved Screws-Tilt page view-model (guided loop + homed gate + latest error). */
    val vm: StateFlow<ScrewsTiltVm> = _vm.asStateFlow()

    @Volatile
    private var latestError: String? = null

    // Measured results surface ONLY when [showResults] — the screen sets it true only after a Run
    // COMPLETES this load (false on entry + while running). screws_tilt_adjust.results PERSIST across the
    // session, so without this a returning user — or one mid-run — sees stale turn directions for screws
    // they may already have adjusted (owner). The bed/list LAYOUT still shows from config regardless.
    @Volatile
    private var showResults: Boolean = false

    init {
        // COMBINE the throttled state with the one-shot screws config so the names/coords are
        // deterministic the instant the config read lands (NOT contingent on a later status diff).
        // NO second throttle — the store conflates at 250 ms.
        scope.launch {
            combine(
                store.printerState,
                store.screwsTiltConfig,
            ) { state, config ->
                buildVm(state, config)
            }.collect { _vm.value = it }
        }

        // Fold dispatcher Failure events for THIS routine's dispatch key into the latest error text
        // (the printer's already-redacted RpcError message). A populated results edge clears it.
        if (events != null) {
            scope.launch {
                events.collect { event ->
                    if (event is DispatchEvent.Failure && event.key == dispatchKey) {
                        latestError = event.message
                        // Re-resolve immediately so the toast surfaces without waiting on a status tick.
                        _vm.value = buildVm(store.printerState.value, store.screwsTiltConfig.value)
                    }
                }
            }
        }
    }

    /**
     * Show or hide the measured turn data. The screen drives this with `armed && !running`: false on
     * entry and WHILE a run is in flight (so a fresh page and an in-progress run never show stale or
     * previous-run directions), true only once the current run completes. Hiding also clears a stale
     * error so a fresh visit / new run starts clean.
     */
    fun setShowResults(show: Boolean) {
        showResults = show
        if (!show) latestError = null
        _vm.value = buildVm(store.printerState.value, store.screwsTiltConfig.value)
    }

    private fun buildVm(state: PrinterState, config: ScrewConfig?): ScrewsTiltVm {
        val live = state.screwsTilt
        // Measured turns only when showResults (a Run completed this load) — else no measurements at all.
        val loop = if (showResults && live != null) {
            parseScrewsTilt(liveToJson(live), configToJson(config))
        } else {
            GuidedLoopState()
        }

        // A clean, populated run (results present, error == false) clears any stale failure so a
        // successful re-probe doesn't keep showing the old rejection toast.
        if (showResults && loop.totalScrews > 0 && !loop.error) {
            latestError = null
        }

        // The display list is the CONFIGURED screws (name + bed coords) — ALWAYS present so the bed/list
        // render the layout even before/without a measurement. Each measured [ScrewTurn] is joined by
        // 1-based index ONLY when showResults; turn == null = "not yet measured". No config → fall back
        // to the measured rows (no coords → D-06 list).
        val coords = config?.screws.orEmpty()
        val turnByIndex = loop.screws.associateBy { it.index }
        val points = if (coords.isNotEmpty()) {
            coords.mapIndexed { i, screw ->
                val idx = i + 1
                ScrewPoint(key = "screw$idx", index = idx, name = screw.name, x = screw.x, y = screw.y, turn = turnByIndex[idx])
            }
        } else {
            loop.screws.map { ScrewPoint(key = it.key, index = it.index, name = it.name, turn = it) }
        }

        // homed gate (D-13) — homed_axes is lowercase (RESEARCH §3); all three present = ready to probe.
        val homed = state.homedAxes
        val homedGate = 'x' in homed && 'y' in homed && 'z' in homed

        return ScrewsTiltVm(
            loop = loop,
            points = points,
            hasCoords = points.any { it.x != null && it.y != null },
            homedGate = homedGate,
            errorText = latestError,
        )
    }

    /**
     * Reconstruct the raw `{"screws_tilt_adjust": {...}}` results JSON the canonical [parseScrewsTilt]
     * consumes from the already-reduced [ScrewsTiltObject]. Both carry the same fields; this keeps the
     * worst-screw math in ONE place (the parser) instead of re-deriving it in the holder.
     */
    private fun liveToJson(live: ScrewsTiltObject): JsonObject = buildJsonObject {
        put(
            "screws_tilt_adjust",
            buildJsonObject {
                put("error", live.error)
                live.maxDeviation?.let { put("max_deviation", it) }
                put(
                    "results",
                    buildJsonObject {
                        live.results.forEach { (key, r: ScrewResult) ->
                            put(
                                key,
                                buildJsonObject {
                                    r.z?.let { put("z", it) }
                                    r.sign?.let { put("sign", it) }
                                    r.adjust?.let { put("adjust", it) }
                                    put("is_base", r.isBase)
                                },
                            )
                        }
                    },
                )
            },
        )
    }

    /**
     * Reconstruct the raw `{"screws_tilt_adjust": {"screwN":[x,y], "screwN_name":...}}` config JSON the
     * parser joins by 1-based index from the reduced [ScrewConfig]. Null config → an empty object so the
     * parser produces unnamed rows (the screen's D-06 list fallback handles the missing labels).
     */
    private fun configToJson(config: ScrewConfig?): JsonObject = buildJsonObject {
        put(
            "screws_tilt_adjust",
            buildJsonObject {
                config?.screws?.forEachIndexed { i, screw: Screw ->
                    val n = i + 1
                    put(
                        "screw$n",
                        buildJsonArray {
                            add(JsonPrimitive(screw.x))
                            add(JsonPrimitive(screw.y))
                        },
                    )
                    screw.name?.let { put("screw${n}_name", it) }
                }
            },
        )
    }

    companion object {
        /** Matches `CommandRegistry.screwsTiltCalculate.key` so only the screws-tilt failure surfaces. */
        const val DEFAULT_DISPATCH_KEY = "screws_tilt"
    }
}

/**
 * One screw on the page — the SOURCE OF TRUTH for both the bed map and the Field list. Built from the
 * CONFIG (so the layout — [name] + bed `[x, y]` coords — shows even before/without a measurement), with
 * the measured [turn] joined by 1-based index ONLY once a Run has completed this load.
 *
 *  - [turn] == null → "not yet measured": the screen shows the name + a neutral `point_scan` point with
 *    NO turn direction / degrees / probed height (a fresh-instance / mid-run screw, owner).
 *  - [x]/[y] == null → the config carried no coords for that screw (D-06 list fallback, no bed).
 */
data class ScrewPoint(
    val key: String,
    val index: Int,
    val name: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val turn: ScrewTurn? = null,
)

/**
 * The Screws-Tilt page view-model:
 *  - [loop] the [GuidedLoopState] rendered VERBATIM (worst screw + `X of N` + per-screw rows). The
 *    screen MUST NOT re-rank or re-derive "worst" (prior-wave correction, commit ef260cb).
 *  - [points] the per-screw rows joined to real bed coords for the to-scale Focus bed.
 *  - [hasCoords] true when at least one point has real coords → draw the to-scale bed; false → the
 *    screen renders the labeled-list fallback (D-06).
 *  - [homedGate] true when all of X/Y/Z are homed — `Run` (SCREWS_TILT_CALCULATE) is gated on this
 *    (D-13); false → offer the inline blue Home pre-flight.
 *  - [errorText] the latest dispatcher Failure (the printer's redacted RpcError text), or null. Cleared
 *    on the next clean populated results edge.
 */
data class ScrewsTiltVm(
    val loop: GuidedLoopState = GuidedLoopState(),
    val points: List<ScrewPoint> = emptyList(),
    val hasCoords: Boolean = false,
    val homedGate: Boolean = false,
    val errorText: String? = null,
)
