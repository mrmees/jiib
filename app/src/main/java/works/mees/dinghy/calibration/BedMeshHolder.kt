package works.mees.dinghy.calibration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.render.BedMeshHeatmapView.ScaleMode
import works.mees.dinghy.state.BedMeshObject
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Toolkit-agnostic holder for the Bed-Mesh page (CALIB-04 / D-07/09). Mirrors the
 * [works.mees.dinghy.ui.extrude.ExtrudeHolder] / [ScrewsTiltHolder] template: ctor(scope, store), a
 * `MutableStateFlow` exposed as a read-only [StateFlow], an `init { scope.launch { ... } }` collect, and
 * NO second throttle (the store conflates the high-rate plane at 250 ms). Plain Kotlin — NO Compose
 * annotations (ADR-0001) — so it is host-unit-testable.
 *
 * ## What it produces (a [BedMeshVm] the screen renders)
 *  - [BedMeshVm.model] — [BedMeshModel.from] over the live `bed_mesh`. The holder reconstructs the raw
 *    `{"bed_mesh": {...}}` JSON from the reduced [BedMeshObject] (the same liveToJson trick
 *    [ScrewsTiltHolder] uses) so the parse stays in ONE place — the screen NEVER re-walks raw JSON.
 *  - [BedMeshVm.isEmpty] / [BedMeshVm.profileNames] — the empty-state (no LOADED mesh) is SEPARATE from
 *    saved-profiles non-emptiness (Pitfall 4): a printer can have saved profiles but no active mesh.
 *  - [BedMeshVm.scaleMode] — the holder-owned, user-cyclable color-scale mode (D-09). [cycleScaleMode]
 *    walks the fixed list; it ONLY re-colors the existing mesh (no re-probe) — the setActiveTool-style
 *    imperative-setter precedent.
 *  - [BedMeshVm.errorText] — folded from a dispatcher [DispatchEvent.Failure] (the printer's REDACTED
 *    RpcError text, e.g. "bed level exceeds configured limits (0.42mm)!") — never `e.message`
 *    (T-05-11-01). Cleared on the next clean populated-mesh edge.
 *
 * @param scope  the lifecycle scope the collect runs on (the UI host supplies it).
 * @param store  the already-assembled Phase-2/9 spine; the holder CONSUMES it, never opens a session.
 * @param events the live session's dispatcher event stream (Failure → [BedMeshVm.errorText]).
 */
class BedMeshHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
    events: SharedFlow<DispatchEvent>? = null,
) {
    private val _vm = MutableStateFlow(BedMeshVm())
    /** The resolved Bed-Mesh page view-model (heatmap model + scale mode + profiles + latest error). */
    val vm: StateFlow<BedMeshVm> = _vm.asStateFlow()

    @Volatile
    private var scaleMode: ScaleMode = ScaleMode.RELATIVE

    @Volatile
    private var latestError: String? = null

    init {
        // The printerState edge MAY clear a stale error when a clean populated mesh arrives (a
        // successful re-calibrate shouldn't keep showing the old rejection).
        scope.launch {
            store.printerState.collect { state ->
                _vm.value = buildVm(state, clearOnCleanMesh = true)
            }
        }

        // Fold dispatcher Failure events (calibrate/save/load/remove) into the latest error text — the
        // page is bed-mesh-only context, so any routine failure here belongs to bed-mesh. This path does
        // NOT clear (the failure just arrived — it must surface even over a still-populated prior mesh).
        if (events != null) {
            scope.launch {
                events.collect { event ->
                    if (event is DispatchEvent.Failure) {
                        latestError = event.message
                        _vm.value = buildVm(store.printerState.value, clearOnCleanMesh = false)
                    }
                }
            }
        }
    }

    /**
     * Cycle the color-scale mode (D-09): RELATIVE → PLATE → ±0.10 → ±0.25 → ±0.50 → ±1.00 → RELATIVE.
     * Pure view-layer — re-colors the EXISTING mesh only (no re-probe). The setActiveTool imperative-
     * setter precedent: it re-resolves the vm immediately off the latest state.
     */
    fun cycleScaleMode() {
        val modes = ScaleMode.entries
        scaleMode = modes[(scaleMode.ordinal + 1) % modes.size]
        _vm.value = buildVm(store.printerState.value, clearOnCleanMesh = false)
    }

    private fun buildVm(state: PrinterState, clearOnCleanMesh: Boolean): BedMeshVm {
        val model = BedMeshModel.from(liveToJson(state.bedMesh))

        // A clean, populated mesh clears any stale failure so a successful re-calibrate / load doesn't
        // keep showing the old rejection toast — but ONLY on the printerState edge, never when a failure
        // just landed (the fold path passes false) or on a pure scale-cycle.
        if (clearOnCleanMesh && !model.isEmpty) {
            latestError = null
        }

        // Homed gate (D-13) — homed_axes is lowercase (RESEARCH §3); all three present = ready to probe.
        // BED_MESH_CALIBRATE probes the bed, so its action is gated on homed exactly like screws-tilt/Z-tilt.
        val homed = 'x' in state.homedAxes && 'y' in state.homedAxes && 'z' in state.homedAxes

        return BedMeshVm(
            model = model,
            scaleMode = scaleMode,
            errorText = latestError,
            homed = homed,
        )
    }

    /**
     * Reconstruct the raw `{"bed_mesh": {...}}` JSON the canonical [BedMeshModel.from] consumes from the
     * already-reduced [BedMeshObject] — keeping the parse in ONE place. Null object → an empty
     * `{"bed_mesh": {}}` so `from` yields the empty model (never throws).
     */
    private fun liveToJson(live: BedMeshObject?): JsonObject = buildJsonObject {
        put(
            "bed_mesh",
            buildJsonObject {
                if (live != null) {
                    put("profile_name", live.profileName)
                    live.meshMin?.let { put("mesh_min", buildJsonArray { it.forEach { v -> add(JsonPrimitive(v)) } }) }
                    live.meshMax?.let { put("mesh_max", buildJsonArray { it.forEach { v -> add(JsonPrimitive(v)) } }) }
                    live.meshMatrix?.let { put("mesh_matrix", matrixJson(it)) }
                    live.probedMatrix?.let { put("probed_matrix", matrixJson(it)) }
                    if (live.profileNames.isNotEmpty()) {
                        put(
                            "profiles",
                            buildJsonObject {
                                // Only the KEYS matter for the saved-profile list; an empty object value is fine.
                                live.profileNames.forEach { name -> put(name, buildJsonObject {}) }
                            },
                        )
                    }
                }
            },
        )
    }

    private fun matrixJson(rows: List<List<Double>>) = buildJsonArray {
        rows.forEach { row -> add(buildJsonArray { row.forEach { v -> add(JsonPrimitive(v)) } }) }
    }
}

/**
 * The Bed-Mesh page view-model:
 *  - [model] the pure [BedMeshModel] the heatmap renders (interpolated grid + probe dots + extents).
 *  - [scaleMode] the active color-scale mode (D-09 — cycled by the screen's overlay toggle).
 *  - [errorText] the latest dispatcher Failure (the printer's redacted RpcError text), or null.
 *  - [homed] true when all of X/Y/Z are homed — the primary action runs BED_MESH_CALIBRATE (a bed
 *    probe) only when homed; otherwise the screen swaps in a Home-All action (D-13).
 */
data class BedMeshVm(
    val model: BedMeshModel = BedMeshModel(),
    val scaleMode: ScaleMode = ScaleMode.RELATIVE,
    val errorText: String? = null,
    val homed: Boolean = false,
) {
    /** Convenience: the saved-profile names (the Load selector list). */
    val profileNames: List<String> get() = model.profileNames

    /** Convenience: no LOADED mesh (empty-state Focus copy). SEPARATE from [profileNames] (Pitfall 4). */
    val isEmpty: Boolean get() = model.isEmpty
}
