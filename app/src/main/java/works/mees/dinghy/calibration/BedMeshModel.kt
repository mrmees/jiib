package works.mees.dinghy.calibration

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure `bed_mesh` view-model (CALIB-04 / D-07). Mirrors the project's pure-parser discipline
 * ([works.mees.dinghy.ui.console.parseGcodeStore]): NO I/O, NO coroutines, NO Compose,
 * `runCatching`/`getOrDefault`/null-returning walks, never `!!`. Host-tested off-hardware
 * ([works.mees.dinghy.calibration.BedMeshModelTest]).
 *
 * REAL-SHAPE CONTRACT (09-01 surprise #4, captured live on the Ender 5 Plus — a KAMP adaptive mesh):
 *  - **`mesh_min` / `mesh_max` are JSON ARRAYS `[x, y]`** (Python tuple → array), NOT objects. A
 *    `.jsonObject["x"]` walk would throw — read element [0]/[1].
 *  - `probed_matrix` (raw dots) and `mesh_matrix` (wider interpolated grid) are arrays-of-arrays of
 *    Doubles.
 *  - `profiles` is a DICT keyed by saved-profile NAME — the saved list is its KEYS, not an array.
 *  - empty-state = `mesh_matrix` empty OR `profile_name == ""` — SEPARATE from `profiles` being
 *    non-empty (a printer can have saved profiles but no LOADED mesh).
 *
 * Source is the structured live object (RESEARCH Pattern 1), never console text. This transform reads
 * the SAME `{"bed_mesh": {...}}` JsonObject the reducer surfaces (the holder consumes this view).
 */

/** A bed extent corner as `[x, y]` Doubles (mesh_min / mesh_max). */
data class MeshPoint(val x: Double, val y: Double)

/** The bed-mesh heatmap view-model derived purely from the live `bed_mesh` object. */
data class BedMeshModel(
    /** Active profile name; `""` when no mesh is loaded (empty-state indicator). */
    val profileName: String = "",
    /** Interpolated mesh grid (rows × cols of Z), the heatmap source. */
    val meshMatrix: List<List<Double>> = emptyList(),
    /** Raw probed dot grid (sparser than [meshMatrix]); the faint probe-point dots (D-08). */
    val probedMatrix: List<List<Double>> = emptyList(),
    /** Bed extent minimum `[x, y]`. */
    val meshMin: MeshPoint = MeshPoint(0.0, 0.0),
    /** Bed extent maximum `[x, y]`. */
    val meshMax: MeshPoint = MeshPoint(0.0, 0.0),
    /** Saved-profile names (the KEYS of the `profiles` dict), sorted. */
    val profileNames: List<String> = emptyList(),
) {
    /**
     * Empty-state (Pitfall 4): no LOADED mesh. SEPARATE from [profileNames] non-emptiness — a printer
     * can have saved profiles but no active mesh. True when the interpolated grid is empty OR the
     * active profile name is blank.
     */
    val isEmpty: Boolean
        get() = meshMatrix.isEmpty() || profileName.isEmpty()

    companion object {
        /**
         * Pure `bed_mesh` walker. Accepts the OUTER `{"bed_mesh": {...}}` object; a malformed/absent
         * shape degrades to an empty model (never throws).
         */
        fun from(bedMesh: JsonObject?): BedMeshModel = runCatching {
            val bm = bedMesh?.get("bed_mesh")?.jsonObject ?: return@runCatching BedMeshModel()
            BedMeshModel(
                profileName = bm["profile_name"]?.jsonPrimitive?.contentOrNull ?: "",
                meshMatrix = matrix(bm["mesh_matrix"]),
                probedMatrix = matrix(bm["probed_matrix"]),
                meshMin = point(bm["mesh_min"]),
                meshMax = point(bm["mesh_max"]),
                profileNames = (bm["profiles"]?.jsonObject?.keys ?: emptySet()).sorted(),
            )
        }.getOrDefault(BedMeshModel())

        /** `[x, y]` array → [MeshPoint]; missing/short → (0,0). */
        private fun point(el: JsonElement?): MeshPoint {
            val arr = el as? JsonArray ?: return MeshPoint(0.0, 0.0)
            val x = arr.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: 0.0
            val y = arr.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: 0.0
            return MeshPoint(x, y)
        }

        /** array-of-arrays-of-Double → `List<List<Double>>`; non-numeric cells dropped. */
        private fun matrix(el: JsonElement?): List<List<Double>> {
            val rows = el as? JsonArray ?: return emptyList()
            return rows.mapNotNull { row ->
                (row as? JsonArray)?.mapNotNull { it.jsonPrimitive.doubleOrNull }
            }
        }
    }
}
