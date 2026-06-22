# Bed Mesh Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Bed Mesh screen into the lists-first Focus/Field grammar — Field selection previews saved profiles, per-profile actions move into a Focus edit morph, and a new Mesh Config subpage controls view-type and per-printer High/Low ramp colors.

**Architecture:** Bottom-up. First the command + state/model data path gain the ability to (a) clear the active mesh and (b) carry saved-profile payloads so a non-active profile can be rendered without loading it. Then a new per-printer DataStore (`BedMeshRenderPrefs`, the 14th store) holds view-type + ramp-color selectors, seeded into `BedMeshHolder` like `TemperatureHolder`. Then `BedMeshHeatmapView` gains a `viewMode` and override-able ramp endpoints. Finally the screen is rebuilt: Field restructure + simplified footer, a Focus edit morph, and the Mesh Config subpage.

**Tech Stack:** Kotlin, Jetpack Compose + classic-Views hybrid, kotlinx.serialization (JsonObject walking), DataStore (Preferences), coroutines/Flow, JUnit4 unit tests.

## Global Constraints

- **minSdk 23** floor; perf floor = Nexus 7 2013 / Adreno 320. No continuous animation; static treatments only.
- **All UI routes through role tokens** (`LocalTokens.current` / `ThemeTokens`), never raw colors. Type via `DinghyType.<role>.toTextStyle(t)` / `fsSp(...)` — inline `fontFamily=`/`fontSize=` are build-failingly forbidden (`FontConformanceTest`).
- **NEVER pick an icon glyph** — every new glyph is an owner decision (see **Glyph Decisions** below; tasks that need a glyph are blocked until the owner picks).
- **Compose persistence writes** must route through the `AppContainer` process-lifetime `writeScope` via intent methods — never `rememberCoroutineScope()` (cancellation trap).
- **`"default"` is a reserved Klipper bed-mesh profile name** — never offer it as a save/rename target.
- **Component-class discipline:** reuse `FocusFrame`, `ListBlock`/`ListRow`, `FootButtonBar`, `OutlinedControl`, `ScreenScaffold` — do not hand-roll Focus/Field chrome.
- **Build/test command** (this repo builds Windows-side; `./gradlew` does NOT work from WSL):
  ```bash
  /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests '<FQCN>' --rerun-tasks" 2>&1 | tr -d '\r'
  ```
  (`--rerun-tasks` because Gradle marks the suite UP-TO-DATE and `--tests` filtering on a cached run yields stale/empty results. Tests are **JUnit4** — `@Test` from `org.junit`, `org.junit.Assert.*`, not `kotlin.test`.) Full assemble: `"E:\Android\gw.bat :app:assembleDebug --no-daemon"`.
- **On-device UAT** runs on BOTH targets after UI tasks: flox (Nexus 7 2013, `armeabi-v7a`, id `0a64b42e`) and moto (Moto G Play 2024, `arm64-v8a`, id `ZY22LBDRM9`). Force-rebuild before installing (stale-APK trap).

## Glyph Decisions (BLOCKING — owner must pick, per icon law)

These tasks cannot draw a glyph until the owner names a `DinghyIcons.<X>` for each. Surface this list to the owner before Tasks 7–9. Where an existing glyph is an obvious reuse, propose it but let the owner confirm.

| Need | Where | Notes / candidate to confirm |
|---|---|---|
| **Edit / pencil** | Focus trailing-action slot (Task 8) | new glyph likely needed |
| **Clear Mesh** row leading icon | Field list (Task 7) | candidate: an existing "clear" glyph (e.g. the one Dismiss uses) — confirm |
| **Mesh Config** row leading icon | Field list (Task 7) | new |
| **View Type / High Color / Low Color / Preview** row icons | Mesh Config subpage (Task 9) | new (4) |
| **Apply / Save / Delete** edit-form button icons | Focus edit morph (Task 8) | Apply/Save/Delete glyphs already exist in the current footer — confirm reuse |

If a glyph is missing from the icon font, it must be added to `DinghyIcons` (the `val` AND the `all` registry list) and pass `verify_ligatures.py`.

---

### Task 1: `BED_MESH_CLEAR` command + reject reserved name `default`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt` (add const + reject `default` in `isValidProfileName`, ~lines 210, 527)
- Modify: `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` (add `bedMeshClear` spec near lines 614-640)
- Test: `app/src/test/java/works/mees/dinghy/command/BedMeshClearCommandTest.kt`

**Interfaces:**
- Produces: `PrinterCommands.BED_MESH_CLEAR: String` = `"BED_MESH_CLEAR"`; `CommandRegistry.bedMeshClear: CommandSpec<Unit>`; `PrinterCommands.isValidProfileName("default") == false`.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/works/mees/dinghy/command/BedMeshClearCommandTest.kt
package works.mees.dinghy.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BedMeshClearCommandTest {
    @Test fun clearConstantIsBareGcode() {
        assertEquals("BED_MESH_CLEAR", PrinterCommands.BED_MESH_CLEAR)
    }

    @Test fun clearSpecEmitsClearGcode() {
        val spec = CommandRegistry.bedMeshClear
        assertEquals("bed_mesh_clear", spec.dispatchKey(Unit))
        assertEquals(
            "BED_MESH_CLEAR",
            (spec.params(Unit)["script"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
    }

    @Test fun defaultProfileNameIsRejected() {
        assertFalse(PrinterCommands.isValidProfileName("default"))
        assertTrue(PrinterCommands.isValidProfileName("bed_cold"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `… "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.command.BedMeshClearCommandTest' --rerun-tasks"`
Expected: FAIL — `BED_MESH_CLEAR` unresolved / `bedMeshClear` unresolved.

- [ ] **Step 3: Add the const and reject `default`**

In `PrinterCommands.kt`, next to `const val BED_MESH_CALIBRATE = "BED_MESH_CALIBRATE"` (line ~210):
```kotlin
/** `BED_MESH_CLEAR` — unloads the active mesh (runtime only; saved profiles untouched; no SAVE_CONFIG). */
const val BED_MESH_CLEAR = "BED_MESH_CLEAR"
```
Change `isValidProfileName` (line ~527) to reject the reserved name:
```kotlin
fun isValidProfileName(name: String): Boolean =
    name.isNotEmpty() &&
        !name.equals("default", ignoreCase = true) && // "default" is reserved: Klipper rejects SAVE=default
        name.length <= MAX_PROFILE_NAME_LEN &&
        name.matches(PROFILE_NAME_ALLOWLIST)
```

- [ ] **Step 4: Add the CommandSpec**

In `CommandRegistry.kt`, after `bedMeshCalibrate` (line ~619):
```kotlin
val bedMeshClear: CommandSpec<Unit> = gcode(
    catalogId = "KGC-BED_MESH_CLEAR",
    key = { "bed_mesh_clear" },
    gcode = { PrinterCommands.BED_MESH_CLEAR },
    availability = AvailabilityPredicate.ObjectPresent("bed_mesh"),
)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `… --tests 'works.mees.dinghy.command.BedMeshClearCommandTest' --rerun-tasks`
Expected: PASS (3 tests). Also run the existing `PrinterCommands`/`CommandRegistry` test classes to confirm the `isValidProfileName` change broke nothing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt \
        app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt \
        app/src/test/java/works/mees/dinghy/command/BedMeshClearCommandTest.kt
git commit -m "feat(calibration): BED_MESH_CLEAR command + reject reserved 'default' profile name"
```

---

### Task 2: Preserve saved-profile payloads through state → model

The reducer currently keeps only profile **keys** (`PrinterStateReducer.kt:236`). To preview a non-active profile we must carry each profile's `points` + `mesh_params` end-to-end: raw JSON → `BedMeshObject` → `liveToJson` → `BedMeshModel`. The merge must respect partial deltas (a LOAD delta omits `profiles`; a SAVE delta omits the matrices) — absent field ⇒ retain prior.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` (add `BedMeshProfilePayload`, extend `BedMeshObject`, ~lines 309-317)
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt` (parse `profiles[name].points`+`mesh_params`, ~lines 217-239)
- Modify: `app/src/main/java/works/mees/dinghy/calibration/BedMeshModel.kt` (add `BedMeshProfile`, `profiles` map, parse it; add `BedMeshViewType` enum)
- Modify: `app/src/main/java/works/mees/dinghy/calibration/BedMeshHolder.kt` (`liveToJson` serializes payloads, ~lines 122-148)
- Test: `app/src/test/java/works/mees/dinghy/state/BedMeshProfilePayloadReducerTest.kt`
- Test: `app/src/test/java/works/mees/dinghy/calibration/BedMeshModelProfilesTest.kt`

**Interfaces:**
- Produces:
  - `state.BedMeshProfilePayload(points: ImmutableList<ImmutableList<Double>>, minX: Double, maxX: Double, minY: Double, maxY: Double)`
  - `state.BedMeshObject.profiles: ImmutableMap<String, BedMeshProfilePayload>` (new field; `profileNames` unchanged, still `profiles.keys` sorted)
  - `calibration.BedMeshProfile(points: List<List<Double>>, minX: Double, maxX: Double, minY: Double, maxY: Double)`
  - `calibration.BedMeshModel.profiles: Map<String, BedMeshProfile>` (new field)
  - `calibration.BedMeshViewType { HEATMAP, PROBE_POINTS }` (enum, used by later tasks)
- Consumes: Task 1 (none directly).

- [ ] **Step 1: Write the failing reducer test**

```kotlin
// app/src/test/java/works/mees/dinghy/state/BedMeshProfilePayloadReducerTest.kt
package works.mees.dinghy.state

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BedMeshProfilePayloadReducerTest {
    private fun status(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    // A SAVE-style delta carries the profiles dict with points + mesh_params.
    private val saveDelta = """
      {"bed_mesh":{"profile_name":"cold","profiles":{
        "cold":{"points":[[0.1,0.2],[0.3,0.4]],
                "mesh_params":{"min_x":10.0,"max_x":210.0,"min_y":12.0,"max_y":208.0,
                               "x_count":2,"y_count":2,"algo":"bicubic","tension":0.2}}}}}
    """.trimIndent()

    // A LOAD-style delta carries matrices but NO profiles key — must retain the prior payload.
    private val loadDelta = """
      {"bed_mesh":{"profile_name":"cold","mesh_matrix":[[0.1,0.2],[0.3,0.4]]}}
    """.trimIndent()

    @Test fun parsesProfilePayload() {
        val s = reduceBedMeshForTest(PrinterState(), status(saveDelta))
        val p = s.bedMesh!!.profiles["cold"]!!
        assertEquals(listOf(listOf(0.1, 0.2), listOf(0.3, 0.4)), p.points.map { it.toList() })
        assertEquals(10.0, p.minX, 0.0)
        assertEquals(208.0, p.maxY, 0.0)
    }

    @Test fun retainsPayloadAcrossLoadDeltaThatOmitsProfiles() {
        val afterSave = reduceBedMeshForTest(PrinterState(), status(saveDelta))
        val afterLoad = reduceBedMeshForTest(afterSave, status(loadDelta))
        // profiles omitted in the load delta -> retained, not wiped.
        assertEquals(setOf("cold"), afterLoad.bedMesh!!.profiles.keys)
        assertEquals(2, afterLoad.bedMesh!!.profiles["cold"]!!.points.size)
    }
}
```

> **Note for implementer:** `reduceBedMeshForTest` is a thin test seam — if the reducer is a top-level `fun reduce(state, status)` you can call it directly instead. Inspect `PrinterStateReducer.kt` for the actual entry point and adapt the two helper calls; do NOT add a production-only test function. If the existing reducer entry point is `PrinterStateReducer.reduce(state, statusJsonObject)`, replace `reduceBedMeshForTest(a, b)` with that call.

- [ ] **Step 2: Run test to verify it fails**

Run: `… --tests 'works.mees.dinghy.state.BedMeshProfilePayloadReducerTest' --rerun-tasks`
Expected: FAIL — `profiles` (payload map) unresolved on `BedMeshObject`.

- [ ] **Step 3: Add the state payload type + field**

In `PrinterState.kt`, add above `BedMeshObject` (line ~309):
```kotlin
/** A saved bed-mesh profile's renderable payload (probed [points] + bed extents from `mesh_params`). */
@Immutable
data class BedMeshProfilePayload(
    val points: ImmutableList<ImmutableList<Double>> = persistentListOf(),
    val minX: Double = 0.0,
    val maxX: Double = 0.0,
    val minY: Double = 0.0,
    val maxY: Double = 0.0,
)
```
Extend `BedMeshObject` with the payload map (keep `profileNames`):
```kotlin
@Immutable
data class BedMeshObject(
    val profileName: String = "",
    val meshMin: ImmutableList<Double>? = null,
    val meshMax: ImmutableList<Double>? = null,
    val probedMatrix: ImmutableList<ImmutableList<Double>>? = null,
    val meshMatrix: ImmutableList<ImmutableList<Double>>? = null,
    val profileNames: ImmutableList<String> = persistentListOf(),
    val profiles: ImmutableMap<String, BedMeshProfilePayload> = persistentMapOf(), // NEW: renderable payloads
)
```
(Add imports `kotlinx.collections.immutable.ImmutableMap`, `persistentMapOf`, `toImmutableMap` as needed.)

- [ ] **Step 4: Parse payloads in the reducer (delta-merge safe)**

In `PrinterStateReducer.kt`, inside the `bed_mesh` block (line ~228-238), replace the `profileNames` line and add the payload parse. The `profiles` object is parsed ONCE; both `profileNames` and `profiles` derive from it, and both fall back to `prev` when the delta omits `profiles`:
```kotlin
val profilesObj = bm.objectOrNull("profiles")
val parsedProfiles = profilesObj?.let { obj ->
    obj.entries.mapNotNull { (name, value) ->
        val pj = value as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val pts = pj.double2dListOrNull("points")?.toImmutable2d() ?: return@mapNotNull null
        val mp = pj.objectOrNull("mesh_params")
        name to BedMeshProfilePayload(
            points = pts,
            minX = mp?.doubleOrNull("min_x") ?: 0.0,
            maxX = mp?.doubleOrNull("max_x") ?: 0.0,
            minY = mp?.doubleOrNull("min_y") ?: 0.0,
            maxY = mp?.doubleOrNull("max_y") ?: 0.0,
        )
    }.toMap().toImmutableMap()
}
s = s.copy(
    bedMesh = BedMeshObject(
        profileName = bm.stringOrNull("profile_name") ?: prev.profileName,
        meshMin = bm.doubleListOrNull("mesh_min")?.toImmutableList() ?: prev.meshMin,
        meshMax = bm.doubleListOrNull("mesh_max")?.toImmutableList() ?: prev.meshMax,
        probedMatrix = bm.double2dListOrNull("probed_matrix")?.toImmutable2d() ?: prev.probedMatrix,
        meshMatrix = bm.double2dListOrNull("mesh_matrix")?.toImmutable2d() ?: prev.meshMatrix,
        profileNames = profilesObj?.keys?.toImmutableList() ?: prev.profileNames,
        profiles = parsedProfiles ?: prev.profiles, // absent profiles dict -> retain prior payloads
    ),
)
```
> **Implementer note:** confirm the helper names actually present in this file (`objectOrNull`, `stringOrNull`, `doubleListOrNull`, `double2dListOrNull`, `toImmutable2d`). If a scalar `doubleOrNull(key)` helper does not exist on `JsonObject`, add a tiny private one mirroring `stringOrNull`, or read `pj["min_x"]?.jsonPrimitive?.doubleOrNull`.

- [ ] **Step 5: Run reducer test to verify it passes**

Run: `… --tests 'works.mees.dinghy.state.BedMeshProfilePayloadReducerTest' --rerun-tasks`
Expected: PASS (2 tests).

- [ ] **Step 6: Write the failing model test**

```kotlin
// app/src/test/java/works/mees/dinghy/calibration/BedMeshModelProfilesTest.kt
package works.mees.dinghy.calibration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BedMeshModelProfilesTest {
    private val raw = """
      {"bed_mesh":{"profile_name":"cold","profiles":{
        "cold":{"points":[[0.1,0.2],[0.3,0.4]],
                "mesh_params":{"min_x":10.0,"max_x":210.0,"min_y":12.0,"max_y":208.0}}}}}
    """.trimIndent()

    @Test fun fromParsesProfilePayloads() {
        val m = BedMeshModel.from(Json.parseToJsonElement(raw) as JsonObject)
        val p = m.profiles["cold"]!!
        assertEquals(listOf(listOf(0.1, 0.2), listOf(0.3, 0.4)), p.points)
        assertEquals(210.0, p.maxX, 0.0)
    }
}
```

- [ ] **Step 7: Add `BedMeshProfile`, `profiles`, `BedMeshViewType`, and parse in `BedMeshModel`**

In `BedMeshModel.kt`:
```kotlin
/** A saved profile's renderable payload at the model layer (plain lists for UI use). */
data class BedMeshProfile(
    val points: List<List<Double>>,
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
)

/** Selectable render styles for the mesh Focus (iso wireframe is a fast-follow, not here yet). */
enum class BedMeshViewType { HEATMAP, PROBE_POINTS }
```
Add `val profiles: Map<String, BedMeshProfile> = emptyMap(),` to the `BedMeshModel` data class. In `BedMeshModel.from(...)` add (after `profileNames`):
```kotlin
profiles = (bm["profiles"]?.jsonObject ?: kotlinx.serialization.json.JsonObject(emptyMap()))
    .mapNotNull { (name, value) ->
        val pj = value as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val pts = matrix(pj["points"])
        if (pts.isEmpty()) return@mapNotNull null
        val mp = pj["mesh_params"]?.jsonObject
        name to BedMeshProfile(
            points = pts,
            minX = mp?.get("min_x")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            maxX = mp?.get("max_x")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            minY = mp?.get("min_y")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            maxY = mp?.get("max_y")?.jsonPrimitive?.doubleOrNull ?: 0.0,
        )
    }.toMap(),
```
(`matrix(...)` is the existing private helper used for `mesh_matrix`/`probed_matrix`.)

- [ ] **Step 8: Serialize payloads in `liveToJson`**

In `BedMeshHolder.kt`, replace the `profiles` block in `liveToJson` (lines ~135-142) so it emits payloads (so `BedMeshModel.from` round-trips them):
```kotlin
if (live.profiles.isNotEmpty()) {
    put("profiles", buildJsonObject {
        live.profiles.forEach { (name, p) ->
            put(name, buildJsonObject {
                put("points", matrixJson(p.points.map { it.toList() }))
                put("mesh_params", buildJsonObject {
                    put("min_x", JsonPrimitive(p.minX)); put("max_x", JsonPrimitive(p.maxX))
                    put("min_y", JsonPrimitive(p.minY)); put("max_y", JsonPrimitive(p.maxY))
                })
            })
        }
    })
} else if (live.profileNames.isNotEmpty()) {
    // Fallback: names only (no payloads yet this session) — keep the saved-profile list populated.
    put("profiles", buildJsonObject { live.profileNames.forEach { put(it, buildJsonObject {}) } })
}
```

- [ ] **Step 9: Run model test to verify it passes**

Run: `… --tests 'works.mees.dinghy.calibration.BedMeshModelProfilesTest' --rerun-tasks`
Expected: PASS. Also re-run any existing `BedMeshModel`/`BedMeshHolder` tests — they must stay green.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/state/PrinterState.kt \
        app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt \
        app/src/main/java/works/mees/dinghy/calibration/BedMeshModel.kt \
        app/src/main/java/works/mees/dinghy/calibration/BedMeshHolder.kt \
        app/src/test/java/works/mees/dinghy/state/BedMeshProfilePayloadReducerTest.kt \
        app/src/test/java/works/mees/dinghy/calibration/BedMeshModelProfilesTest.kt
git commit -m "feat(calibration): carry saved bed-mesh profile payloads through state->model"
```

---

### Task 3: Preview-model builder

A pure function that turns a selected saved profile into a renderable `BedMeshModel` (heatmap fill reads `meshMatrix`, so set BOTH `meshMatrix` and `probedMatrix` to the profile's points).

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/calibration/BedMeshModel.kt`
- Test: `app/src/test/java/works/mees/dinghy/calibration/BedMeshPreviewModelTest.kt`

**Interfaces:**
- Consumes: `BedMeshModel.profiles` (Task 2).
- Produces: `BedMeshModel.previewOf(name: String): BedMeshModel?` — returns a model whose `meshMatrix == probedMatrix == profiles[name].points`, extents from `mesh_params`, `profileName = name`; `null` if the name is absent.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/works/mees/dinghy/calibration/BedMeshPreviewModelTest.kt
package works.mees.dinghy.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BedMeshPreviewModelTest {
    private val base = BedMeshModel(
        profileName = "live",
        meshMatrix = listOf(listOf(9.0)),
        profiles = mapOf("cold" to BedMeshProfile(
            points = listOf(listOf(0.1, 0.2), listOf(0.3, 0.4)),
            minX = 10.0, maxX = 210.0, minY = 12.0, maxY = 208.0,
        )),
        profileNames = listOf("cold"),
    )

    @Test fun previewSetsBothMatricesToPoints() {
        val p = base.previewOf("cold")!!
        assertEquals("cold", p.profileName)
        assertEquals(p.meshMatrix, p.probedMatrix)
        assertEquals(listOf(listOf(0.1, 0.2), listOf(0.3, 0.4)), p.meshMatrix)
        assertEquals(MeshPoint(10.0, 12.0), p.meshMin)
        assertEquals(MeshPoint(210.0, 208.0), p.meshMax)
        assertFalse(p.isEmpty) // non-empty matrix + non-blank name
    }

    @Test fun previewOfUnknownIsNull() {
        assertNull(base.previewOf("nope"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `… --tests 'works.mees.dinghy.calibration.BedMeshPreviewModelTest' --rerun-tasks` → FAIL (`previewOf` unresolved).

- [ ] **Step 3: Implement `previewOf`**

In `BedMeshModel.kt` (inside the class):
```kotlin
/**
 * Build a renderable model for a SAVED, non-active profile [name] without loading it. The heatmap
 * fill reads [meshMatrix] (empty == empty-state), so set both matrices to the profile's probed
 * points — coarser than a live interpolated mesh, by design (spec: accepted). Returns null if absent.
 */
fun previewOf(name: String): BedMeshModel? {
    val p = profiles[name] ?: return null
    return copy(
        profileName = name,
        meshMatrix = p.points,
        probedMatrix = p.points,
        meshMin = MeshPoint(p.minX, p.minY),
        meshMax = MeshPoint(p.maxX, p.maxY),
    )
}
```

- [ ] **Step 4: Run to verify it passes** — `… --tests '…BedMeshPreviewModelTest' --rerun-tasks` → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/calibration/BedMeshModel.kt \
        app/src/test/java/works/mees/dinghy/calibration/BedMeshPreviewModelTest.kt
git commit -m "feat(calibration): BedMeshModel.previewOf builds renderable model from a saved profile"
```

---

### Task 4: Per-printer `BedMeshRenderPrefs` (14th DataStore) + AppContainer wiring

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/settings/BedMeshRenderPrefs.kt`
- Modify: `app/src/main/java/works/mees/dinghy/DinghyApp.kt` (create `bedMeshRenderDataStore`)
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` (inject store, expose prefs + intent methods)
- Test: `app/src/test/java/works/mees/dinghy/ui/settings/BedMeshRenderPrefsTest.kt`

**Interfaces:**
- Produces:
  - `BedMeshRenderPrefs(dataStore)` with: `viewType(profileId): Flow<BedMeshViewType>`, `highColorSel(profileId): Flow<Int>`, `lowColorSel(profileId): Flow<Int>`, and suspend `setViewType/setHighColorSel/setLowColorSel(profileId, …)`.
  - Color selector convention: **Int** — `-1` = Accent sentinel; `0..3` = data-pool slot. Defaults when unset: High = `-1` (accent = `seriesColor(0)`), Low = `0` (pool[0] = `seriesColor(1)` in Colorful).
  - `AppContainer.bedMeshRenderPrefs`, and intents `setBedMeshViewType(BedMeshViewType)`, `setBedMeshHighColorSel(Int)`, `setBedMeshLowColorSel(Int)` (all scope to `activeProfileId`, route through `writeScope`).
- Consumes: `BedMeshViewType` (Task 2).

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/works/mees/dinghy/ui/settings/BedMeshRenderPrefsTest.kt
package works.mees.dinghy.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import works.mees.dinghy.calibration.BedMeshViewType

class BedMeshRenderPrefsTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = TestScope()) { tmp.newFile("bm_${System.nanoTime()}.preferences_pb") }

    @Test fun defaultsWhenUnset() = runBlocking {
        val p = BedMeshRenderPrefs(store())
        assertEquals(BedMeshViewType.HEATMAP, p.viewType("printerA").first())
        assertEquals(-1, p.highColorSel("printerA").first()) // accent sentinel
        assertEquals(0, p.lowColorSel("printerA").first())   // pool slot 0
    }

    @Test fun roundTripsScopedByProfileId() = runBlocking {
        val p = BedMeshRenderPrefs(store())
        p.setViewType("printerA", BedMeshViewType.PROBE_POINTS)
        p.setHighColorSel("printerA", 2)
        assertEquals(BedMeshViewType.PROBE_POINTS, p.viewType("printerA").first())
        assertEquals(2, p.highColorSel("printerA").first())
        // Different printer is unaffected (independent scope).
        assertEquals(BedMeshViewType.HEATMAP, p.viewType("printerB").first())
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `… --tests 'works.mees.dinghy.ui.settings.BedMeshRenderPrefsTest' --rerun-tasks` → FAIL (class missing).

- [ ] **Step 3: Implement `BedMeshRenderPrefs`** (mirror `TraceStylePrefs` key-namespacing)

```kotlin
// app/src/main/java/works/mees/dinghy/ui/settings/BedMeshRenderPrefs.kt
package works.mees.dinghy.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import works.mees.dinghy.calibration.BedMeshViewType
import java.io.IOException

/**
 * Per-printer bed-mesh RENDER preferences (the 14th DataStore): view-type + High/Low ramp-color
 * selectors, keyed by profileId (like [TraceStylePrefs]). Colors store a SLOT SELECTOR, never ARGB
 * (an index survives theme changes; an ARGB would freeze the old color). Selector: -1 = Accent
 * sentinel; 0..3 = data-pool slot. Defaults: view HEATMAP, high -1 (accent), low 0 (pool[0]).
 */
class BedMeshRenderPrefs(private val dataStore: DataStore<Preferences>) {
    private fun viewKey(pid: String) = stringPreferencesKey("bm_view_$pid")
    private fun highKey(pid: String) = intPreferencesKey("bm_high_$pid")
    private fun lowKey(pid: String) = intPreferencesKey("bm_low_$pid")

    fun viewType(pid: String): Flow<BedMeshViewType> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            when (prefs[viewKey(pid)]) {
                BedMeshViewType.PROBE_POINTS.name -> BedMeshViewType.PROBE_POINTS
                else -> BedMeshViewType.HEATMAP
            }
        }

    fun highColorSel(pid: String): Flow<Int> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[highKey(pid)] ?: DEFAULT_HIGH }

    fun lowColorSel(pid: String): Flow<Int> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[lowKey(pid)] ?: DEFAULT_LOW }

    suspend fun setViewType(pid: String, v: BedMeshViewType) =
        dataStore.edit { it[viewKey(pid)] = v.name }
    suspend fun setHighColorSel(pid: String, sel: Int) =
        dataStore.edit { it[highKey(pid)] = sel }
    suspend fun setLowColorSel(pid: String, sel: Int) =
        dataStore.edit { it[lowKey(pid)] = sel }

    companion object {
        const val ACCENT_SEL = -1
        const val DEFAULT_HIGH = ACCENT_SEL // accent == seriesColor(0)
        const val DEFAULT_LOW = 0           // pool slot 0 == seriesColor(1) in Colorful
    }
}
```

- [ ] **Step 4: Run prefs test to verify it passes** — `… --tests '…BedMeshRenderPrefsTest' --rerun-tasks` → PASS (2 tests).

- [ ] **Step 5: Create the DataStore in `DinghyApp.kt`**

Mirror the `traceStyleDataStore` block (the file carries no secrets → independent lifecycle):
```kotlin
// The 14th, INDEPENDENT file: bedmesh_render.preferences_pb — per-printer bed-mesh view-type +
// High/Low ramp-color SLOT selectors. No secrets; own connection-independent lifecycle. One per process.
val bedMeshRenderDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = appScope,
    produceFile = { applicationContext.preferencesDataStoreFile("bedmesh_render.preferences_pb") },
)
```
Pass `bedMeshRenderDataStore` into the `AppContainer(...)` constructor call (find where the other `*DataStore` args are passed).

- [ ] **Step 6: Wire into `AppContainer.kt`**

Add constructor param `bedMeshRenderDataStore: DataStore<Preferences>,`. Expose the prefs + intents (mirror `setTraceColor`):
```kotlin
/** Per-printer bed-mesh render prefs (14th store): view-type + High/Low ramp-color slot selectors. */
val bedMeshRenderPrefs: BedMeshRenderPrefs = BedMeshRenderPrefs(bedMeshRenderDataStore)

fun setBedMeshViewType(v: BedMeshViewType) {
    val pid = activeProfileId.value ?: return
    writeScope.launch { bedMeshRenderPrefs.setViewType(pid, v) }
}
fun setBedMeshHighColorSel(sel: Int) {
    val pid = activeProfileId.value ?: return
    writeScope.launch { bedMeshRenderPrefs.setHighColorSel(pid, sel) }
}
fun setBedMeshLowColorSel(sel: Int) {
    val pid = activeProfileId.value ?: return
    writeScope.launch { bedMeshRenderPrefs.setLowColorSel(pid, sel) }
}
```
(Add imports for `BedMeshRenderPrefs` and `BedMeshViewType`.)

- [ ] **Step 7: Build to verify wiring compiles**

Run: `… "E:\Android\gw.bat :app:assembleDebug --no-daemon"` → BUILD SUCCESSFUL. (No new positional test fixtures should break — but if any `AppContainer` test constructor is positional, update it; the new param is the trap from prior phases.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/settings/BedMeshRenderPrefs.kt \
        app/src/main/java/works/mees/dinghy/DinghyApp.kt \
        app/src/main/java/works/mees/dinghy/di/AppContainer.kt \
        app/src/test/java/works/mees/dinghy/ui/settings/BedMeshRenderPrefsTest.kt
git commit -m "feat(calibration): per-printer BedMeshRenderPrefs (14th DataStore) + AppContainer intents"
```

---

### Task 5: Seed render prefs into `BedMeshHolder`; update `AppShell` construction

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/calibration/BedMeshHolder.kt` (add prefs + activeProfileId params; expose `viewType`/`highColorSel`/`lowColorSel` on the VM)
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` (line ~389 construction)
- Test: `app/src/test/java/works/mees/dinghy/calibration/BedMeshHolderRenderPrefsTest.kt`

**Interfaces:**
- Consumes: `AppContainer.bedMeshRenderPrefs`, `container.activeProfileId` (Task 4); `BedMeshViewType` (Task 2).
- Produces: `BedMeshVm` gains `viewType: BedMeshViewType`, `highColorSel: Int`, `lowColorSel: Int`. Holder constructor gains `renderPrefs: BedMeshRenderPrefs? = null, activeProfileId: Flow<String?>? = null` (nullable so existing in-memory/test construction still works).

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/works/mees/dinghy/calibration/BedMeshHolderRenderPrefsTest.kt
package works.mees.dinghy.calibration

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import works.mees.dinghy.state.PrinterStateStore
import works.mees.dinghy.ui.settings.BedMeshRenderPrefs

class BedMeshHolderRenderPrefsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun vmReflectsPersistedViewTypeForActiveProfile() = runTest {
        val prefs = BedMeshRenderPrefs(
            PreferenceDataStoreFactory.create(scope = this.backgroundScope) { tmp.newFile("p.preferences_pb") },
        )
        prefs.setViewType("printerA", BedMeshViewType.PROBE_POINTS)
        prefs.setHighColorSel("printerA", 3)
        val pid = MutableStateFlow<String?>("printerA")
        val holder = BedMeshHolder(
            scope = backgroundScope,
            store = PrinterStateStore(),
            events = null,
            renderPrefs = prefs,
            activeProfileId = pid,
        )
        val vm = holder.state.first { it.viewType == BedMeshViewType.PROBE_POINTS }
        assertEquals(BedMeshViewType.PROBE_POINTS, vm.viewType)
        assertEquals(3, vm.highColorSel)
    }
}
```
> **Implementer note:** confirm the holder's public StateFlow name (the Explore report shows `BedMeshVm` is the VM type; verify whether it is exposed as `holder.state`, `holder.vm`, or similar, and adapt). Also confirm `PrinterStateStore()` no-arg construction exists in tests (the existing `BedMeshHolder` tests will show the right construction).

- [ ] **Step 2: Run to verify it fails** — FAIL (`renderPrefs`/`activeProfileId` params + `viewType` on VM unresolved).

- [ ] **Step 3: Extend `BedMeshVm` and the holder**

Add to `BedMeshVm` (in `BedMeshHolder.kt`):
```kotlin
val viewType: BedMeshViewType = BedMeshViewType.HEATMAP,
val highColorSel: Int = BedMeshRenderPrefs.DEFAULT_HIGH,
val lowColorSel: Int = BedMeshRenderPrefs.DEFAULT_LOW,
```
Add constructor params and seed via `flatMapLatest` (mirror `TemperatureHolder`). Hold three internal `MutableStateFlow`s and fold them into the emitted `BedMeshVm`:
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class BedMeshHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
    events: SharedFlow<DispatchEvent>? = null,
    renderPrefs: BedMeshRenderPrefs? = null,
    activeProfileId: Flow<String?>? = null,
) {
    private val _viewType = MutableStateFlow(BedMeshViewType.HEATMAP)
    private val _highSel = MutableStateFlow(BedMeshRenderPrefs.DEFAULT_HIGH)
    private val _lowSel = MutableStateFlow(BedMeshRenderPrefs.DEFAULT_LOW)

    init {
        if (renderPrefs != null && activeProfileId != null) {
            scope.launch {
                activeProfileId.flatMapLatest { pid ->
                    if (pid == null) flowOf(BedMeshViewType.HEATMAP) else renderPrefs.viewType(pid)
                }.collect { _viewType.value = it }
            }
            scope.launch {
                activeProfileId.flatMapLatest { pid ->
                    if (pid == null) flowOf(BedMeshRenderPrefs.DEFAULT_HIGH) else renderPrefs.highColorSel(pid)
                }.collect { _highSel.value = it }
            }
            scope.launch {
                activeProfileId.flatMapLatest { pid ->
                    if (pid == null) flowOf(BedMeshRenderPrefs.DEFAULT_LOW) else renderPrefs.lowColorSel(pid)
                }.collect { _lowSel.value = it }
            }
        }
        // ... existing init (state collection) ...
    }
}
```
Fold `_viewType/_highSel/_lowSel` into the VM emission. The existing VM is built from a `combine`/`map` on `store.printerState` (+ error/scale). Add these three flows to that `combine` so the VM carries them. (If the existing builder uses `combine(a, b, c) { … }`, extend to include the three new flows; `combine` supports up to 5 typed args, else use the list/vararg form.)

- [ ] **Step 4: Update `AppShell.kt` construction** (line ~389)

```kotlin
val bedMeshHolder = remember(store) {
    BedMeshHolder(
        scope = scope,
        store = store,
        events = calibEvents,
        renderPrefs = container.bedMeshRenderPrefs,
        activeProfileId = container.activeProfileId,
    )
}
```

- [ ] **Step 5: Run holder test + build** — `… --tests '…BedMeshHolderRenderPrefsTest' --rerun-tasks` → PASS; then `assembleDebug` → SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/calibration/BedMeshHolder.kt \
        app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt \
        app/src/test/java/works/mees/dinghy/calibration/BedMeshHolderRenderPrefsTest.kt
git commit -m "feat(calibration): seed per-printer bed-mesh render prefs into BedMeshHolder VM"
```

---

### Task 6: `viewMode` + override-able ramp on `BedMeshHeatmapView`; Host plumbing

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapView.kt` (add `ViewMode`, `setViewMode`, `setRampColors`, PROBE_POINTS draw path)
- Modify: `app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapHost.kt` (thread viewMode + resolved colors)
- Create: `app/src/main/java/works/mees/dinghy/render/BedMeshColorResolve.kt` (pure selector→Color resolver, unit-testable)
- Test: `app/src/test/java/works/mees/dinghy/render/BedMeshColorResolveTest.kt`

**Interfaces:**
- Produces:
  - `fun resolveMeshColor(t: ThemeTokens, sel: Int): Color` — `sel < 0` → `t.accent`; else `t.pool.getOrElse(sel) { t.accent }`.
  - `BedMeshHeatmapView.ViewMode { HEATMAP, PROBE_POINTS }`; `view.setViewMode(mode)`; `view.setRampColors(lowArgb: Int, highArgb: Int)`.
  - `BedMeshHeatmapHost(tokens, model, scaleMode, viewMode, lowColorArgb, highColorArgb, modifier)`.
- Consumes: `BedMeshViewType` (Task 2) — mapped to the view's `ViewMode` by the screen.

> **Canvas note:** the actual draw is not unit-tested (a `View.onDraw` needs an instrumented/Robolectric harness this project doesn't use for render). We unit-test the **pure color resolver**; the draw paths are verified by on-device UAT in Tasks 7–9.

- [ ] **Step 1: Write the failing resolver test**

```kotlin
// app/src/test/java/works/mees/dinghy/render/BedMeshColorResolveTest.kt
package works.mees.dinghy.render

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.theme.ThemeTokens

class BedMeshColorResolveTest {
    // Build a minimal ThemeTokens with a known accent + 4-slot pool. Use the existing test factory if
    // present (search test sources for a ThemeTokens fixture); else construct via its default ctor and copy.
    private val accent = Color(0xFF112233)
    private val pool = listOf(Color(0xFF0A0A0A), Color(0xFF0B0B0B), Color(0xFF0C0C0C), Color(0xFF0D0D0D))
    private val t = testThemeTokens(accent = accent, pool = pool)

    @Test fun negativeSelIsAccent() = assertEquals(accent, resolveMeshColor(t, -1))
    @Test fun slotSelIsPool() = assertEquals(pool[2], resolveMeshColor(t, 2))
    @Test fun outOfRangeSelFallsBackToAccent() = assertEquals(accent, resolveMeshColor(t, 9))
}
```
> **Implementer note:** find how other tests build a `ThemeTokens` (grep test sources for `ThemeTokens(` or a `*Preview`/`*Fixture`). Reuse that. `testThemeTokens(...)` above is a placeholder for whatever the real fixture is; do not invent a new production factory.

- [ ] **Step 2: Run to verify it fails** — FAIL (`resolveMeshColor` unresolved).

- [ ] **Step 3: Implement the resolver**

```kotlin
// app/src/main/java/works/mees/dinghy/render/BedMeshColorResolve.kt
package works.mees.dinghy.render

import androidx.compose.ui.graphics.Color
import works.mees.dinghy.theme.ThemeTokens

/**
 * Resolve a stored bed-mesh ramp color selector to a live Color. Selector: -1 = Accent sentinel
 * (== seriesColor(0)); 0..3 = data-pool slot. Out-of-range/empty-pool falls back to accent. Resolving
 * at render time (not storing ARGB) is what lets mesh colors track theme changes.
 */
fun resolveMeshColor(t: ThemeTokens, sel: Int): Color =
    if (sel < 0) t.accent else t.pool.getOrElse(sel) { t.accent }
```

- [ ] **Step 4: Run resolver test to verify it passes** — PASS (3 tests).

- [ ] **Step 5: Add `ViewMode` + setters + draw path to `BedMeshHeatmapView.kt`**

Add the enum + fields:
```kotlin
enum class ViewMode { HEATMAP, PROBE_POINTS }
private var viewMode: ViewMode = ViewMode.HEATMAP
fun setViewMode(mode: ViewMode) { if (viewMode != mode) { viewMode = mode; invalidate() } }

/** Override ramp endpoints (resolved from the per-printer selectors). Re-bakes the OKLCH ramp. */
fun setRampColors(lowArgb: Int, highArgb: Int) {
    rampStops = OklchRamp.themedRampStops(lowArgb = lowArgb, highArgb = highArgb)
    invalidate()
}
```
In `onDraw`, branch on `viewMode`. Keep the existing HEATMAP fill+dots path. Add the PROBE_POINTS path: NO fill; draw each probed point as a filled circle colored by the ramp (frac of its z over the probed-matrix endpoints):
```kotlin
if (viewMode == ViewMode.PROBE_POINTS) {
    val probed = model.probedMatrix
    if (probed.isEmpty() || probed[0].isEmpty()) {
        canvas.drawRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, emptyPaint); return
    }
    val (loZ, hiZ) = endpoints(probed, scaleMode)
    val span = hiZ - loZ
    val pRows = probed.size; val pCols = probed[0].size
    val r = (max(w / pCols, h / pRows) * DOT_RADIUS_FRAC / 2f).coerceAtLeast(MIN_DOT_PX)
    for (row in 0 until pRows) {
        val cy = (pRows - 1 - row + 0.5f) * (h / pRows)
        for (c in 0 until pCols) {
            val z = probed[row].getOrElse(c) { loZ }
            val frac = if (span <= 0.0) 0.5 else ((z - loZ) / span).coerceIn(0.0, 1.0)
            cellPaint.color = rampColor(frac.toFloat())
            canvas.drawCircle((c + 0.5f) * (w / pCols), cy, r, cellPaint)
        }
    }
    return
}
// ...existing HEATMAP fill+faint-dots path unchanged below...
```
> **Implementer note:** confirm the real names of the private helpers (`endpoints(matrix, scaleMode)`, `rampColor(frac)`, `cellPaint`, `emptyPaint`, `DOT_RADIUS_FRAC`, `MIN_DOT_PX`) from the file and reuse them. `endpoints` currently takes the grid matrix; pass `probed` here. `applyTokens` keeps setting dot/empty colors and bakes a DEFAULT ramp; `setRampColors` overrides it afterward in the Host update block.

- [ ] **Step 6: Thread through the Host**

```kotlin
@Composable
fun BedMeshHeatmapHost(
    tokens: ThemeTokens,
    model: BedMeshModel,
    scaleMode: BedMeshHeatmapView.ScaleMode,
    viewMode: BedMeshHeatmapView.ViewMode,
    lowColorArgb: Int,
    highColorArgb: Int,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) { PreviewPlaceholderBox("Bed mesh (live on device)", modifier); return }
    AndroidView(
        factory = { ctx -> BedMeshHeatmapView(ctx) },
        update = { view ->
            view.applyTokens(tokens)                       // dot/empty colors + default ramp
            view.setRampColors(lowColorArgb, highColorArgb) // override ramp from per-printer selectors
            view.setViewMode(viewMode)
            view.setMesh(model, scaleMode)
        },
        modifier = modifier,
    )
}
```

- [ ] **Step 7: Build to verify it compiles** — `assembleDebug` → SUCCESSFUL. (Callers of `BedMeshHeatmapHost` break here — they're fixed in Task 7. To keep this commit compiling, temporarily update the one call site in `BedMeshScreen.kt`'s `BedMeshFocusRegion` to pass `viewMode = BedMeshHeatmapView.ViewMode.HEATMAP, lowColorArgb = tokens.seriesColor(1).toArgb(), highColorArgb = tokens.seriesColor(0).toArgb()` — Task 7 replaces this with the real wiring.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapView.kt \
        app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapHost.kt \
        app/src/main/java/works/mees/dinghy/render/BedMeshColorResolve.kt \
        app/src/test/java/works/mees/dinghy/render/BedMeshColorResolveTest.kt \
        app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt
git commit -m "feat(calibration): BedMeshHeatmapView viewMode (probe-points) + override-able ramp"
```

---

### Task 7: Screen — Field restructure, simplified footer, selection→preview, scale overlay transparency

This and Tasks 8–9 rebuild `BedMeshScreen.kt`. **Glyph-blocked** (Clear Mesh + Mesh Config row icons — see Glyph Decisions). Verified by on-device UAT, not unit tests.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt`

**Interfaces:**
- Consumes: `vm.viewType/highColorSel/lowColorSel` (Task 5), `vm.model.previewOf` (Task 3), `CommandRegistry.bedMeshClear` (Task 1), `resolveMeshColor` + new Host params (Task 6).
- Produces (within file): `MeshFieldMode.MeshConfig` mode; `selectedProfile` drives Focus preview; `onClearMesh` intent.

- [ ] **Step 1: Add `MeshConfig` to `MeshFieldMode` + Saver**

```kotlin
internal sealed class MeshFieldMode {
    data object ProfileList : MeshFieldMode()
    data class SaveName(val prefill: String) : MeshFieldMode()
    data object MeshConfig : MeshFieldMode()                 // NEW: Mesh Config subpage list
    data class MeshConfigEditor(val item: MeshConfigItem) : MeshFieldMode() // NEW: a config row's editor
}
enum class MeshConfigItem { VIEW_TYPE, HIGH_COLOR, LOW_COLOR, PREVIEW }
```
Extend `MeshFieldModeSaver` save/restore with `"MeshConfig"` and `"MeshConfigEditor:<item>"` cases (mirror the existing `SaveName:` string encoding).

- [ ] **Step 2: Focus renders live OR preview based on `selectedProfile`**

In `BedMeshFocusRegion` (or where the Host is invoked), compute the model to render and resolve colors:
```kotlin
val renderModel = remember(vm.model, selectedProfile) {
    val sel = selectedProfile
    if (sel != null && sel != vm.model.profileName) vm.model.previewOf(sel) ?: vm.model else vm.model
}
val lowArgb = resolveMeshColor(t, vm.lowColorSel).toArgb()
val highArgb = resolveMeshColor(t, vm.highColorSel).toArgb()
BedMeshHeatmapHost(
    tokens = t,
    model = renderModel,
    scaleMode = vm.scaleMode,
    viewMode = when (vm.viewType) {
        BedMeshViewType.HEATMAP -> BedMeshHeatmapView.ViewMode.HEATMAP
        BedMeshViewType.PROBE_POINTS -> BedMeshHeatmapView.ViewMode.PROBE_POINTS
    },
    lowColorArgb = lowArgb,
    highColorArgb = highArgb,
    modifier = Modifier.fillMaxSize(),
)
```

- [ ] **Step 3: Field list — Clear Mesh (conditional top) + Mesh Config (bottom)**

In the `MeshFieldMode.ProfileList` branch, wrap the existing profile `ListBlock` with a conditional Clear Mesh row first and a Mesh Config row last. Clear Mesh shows only when a mesh is loaded (`!vm.isEmpty`):
```kotlin
ListBlock(modifier = Modifier.weight(1f)) {
    if (!vm.isEmpty) {
        item(key = "__clear__") {
            ListRow(onClick = onClearMesh, uDp = grid.uDp,
                leadingIcon = /* owner-picked Clear glyph */) { ListRowLabel("Clear Mesh") }
        }
    }
    items(vm.profileNames, key = { it }) { name ->
        val isActive = name == vm.model.profileName && !vm.isEmpty
        ListRow(selected = name == selectedProfile, onClick = { onSelectProfile(name) }, uDp = grid.uDp,
            trailingContent = if (isActive) { { /* existing active marker */ } } else null) {
            ListRowLabel(name)
        }
    }
    item(key = "__config__") {
        ListRow(onClick = onOpenMeshConfig, uDp = grid.uDp,
            leadingIcon = /* owner-picked Mesh Config glyph */) { ListRowLabel("Mesh Config") }
    }
}
```
> **Implementer note:** confirm `ListRow`'s real leading-icon parameter name (the Explore report shows `ListRow(selected, onClick, uDp, trailingContent) { … }`; check for a `leadingIcon`/`leading` slot — if rows elsewhere render a leading glyph, copy that exact API). Do not draw any glyph until the owner has picked it.

- [ ] **Step 4: Simplify the footer to Back + (Home All | Calibrate)**

Replace the three-branch `FootButtonBar.actions` (Unhomed / Selected / Homed) with two global actions only — per-profile Apply/Remove/Save move to the Focus edit morph (Task 8):
```kotlin
FootButtonBar(uDp = grid.uDp, actions = buildList {
    add(FootAction(label = "Back", icon = /* existing Back glyph */, intent = Intent.Accent, onClick = onBack))
    if (!vm.homed) {
        add(FootAction(label = "Home All", icon = /* existing */, intent = Intent.Go, onClick = onHomeAll))
    } else {
        add(FootAction(label = "Calibrate", icon = /* existing CalibrationRun */, intent = Intent.Go, onClick = onCalibrate))
    }
})
```
> The FootButtonBar count-driven rule (≤2 = icon+text) already yields icon+text for these two.

- [ ] **Step 5: Wire `onClearMesh` in the `BedMeshScreen` wrapper**

```kotlin
onClearMesh = {
    dispatcher?.dispatch(CommandRegistry.bedMeshClear, Unit)
    selectedProfile = null
    // BED_MESH_CLEAR is runtime-only; no SAVE_CONFIG guard (saved profiles untouched).
},
```
Remove the old footer-driven `onApplyProfile`/`onRemove`/save plumbing from the ProfileList branch (they relocate to Task 8). Keep the dispatch lambdas in the wrapper; they're re-pointed by Task 8.

- [ ] **Step 6: Scale overlay — bump transparency**

In `ScaleToggle`, lower the background alpha so the mesh reads through (was `t.surface2` solid):
```kotlin
.background(t.surface2.copy(alpha = 0.45f))   // see-through so the mesh shows behind the toggle
```
> Owner will fine-tune the exact alpha on-device; 0.45 is the starting point.

- [ ] **Step 7: Build + install + on-device UAT (flox + moto)**

Run: `… "E:\Android\gw.bat :app:assembleDebug --no-daemon"` (force a fresh build — stale-APK trap). Install BOTH ABIs and have the owner verify: Clear Mesh appears only when a mesh is loaded and unloads it; tapping a saved profile previews it; active profile is marked and shows the live mesh; footer is Back + Home/Calibrate; scale toggle is see-through. **Owner UAT gate.**

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt
git commit -m "feat(calibration): bed mesh Field restructure (clear/config rows, preview, slim footer)"
```

---

### Task 8: Screen — Focus edit morph (pencil) + Apply / Save-rename / Delete matrix

**Glyph-blocked** (edit pencil + Apply/Save/Delete button glyphs). Verified by on-device UAT.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/calibration/BedMeshEditStateTest.kt` (pure classifier only)

**Interfaces:**
- Produces (within file):
  - `enum class MeshEditKind { ACTIVE_UNSAVED, ACTIVE_SAVED, PREVIEW_NONACTIVE }`
  - `fun classifyMeshEdit(activeName: String, isEmpty: Boolean, selected: String?, savedNames: Set<String>): MeshEditKind` — the pure rule deciding which button set the edit form shows.
- Consumes: dispatch builders (`bedMeshProfileSave/Load/Remove`, `saveConfig`).

- [ ] **Step 1: Write the failing classifier test**

```kotlin
// app/src/test/java/works/mees/dinghy/ui/calibration/BedMeshEditStateTest.kt
package works.mees.dinghy.ui.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class BedMeshEditStateTest {
    private fun c(active: String, empty: Boolean, sel: String?, saved: Set<String>) =
        classifyMeshEdit(active, empty, sel, saved)

    @Test fun freshCalibrateDefaultIsActiveUnsaved() =
        assertEquals(MeshEditKind.ACTIVE_UNSAVED, c("default", false, null, setOf("default")))

    @Test fun noMeshLoadedIsActiveUnsaved() =
        assertEquals(MeshEditKind.ACTIVE_UNSAVED, c("", true, null, emptySet()))

    @Test fun viewingActiveSavedProfile() =
        assertEquals(MeshEditKind.ACTIVE_SAVED, c("cold", false, "cold", setOf("cold")))

    @Test fun viewingActiveSavedWithNoSelectionDefaultsToActive() =
        assertEquals(MeshEditKind.ACTIVE_SAVED, c("cold", false, null, setOf("cold")))

    @Test fun previewingNonActiveProfile() =
        assertEquals(MeshEditKind.PREVIEW_NONACTIVE, c("cold", false, "hot", setOf("cold", "hot")))
}
```

- [ ] **Step 2: Run to verify it fails** — FAIL (`classifyMeshEdit`/`MeshEditKind` unresolved).

- [ ] **Step 3: Implement the classifier**

```kotlin
// top-level in BedMeshScreen.kt
internal enum class MeshEditKind { ACTIVE_UNSAVED, ACTIVE_SAVED, PREVIEW_NONACTIVE }

/**
 * Which edit-form button set to show. The effective focus target is [selected] if set, else the active
 * mesh. "default" is reserved (never a saved target) so an active mesh named default == Active-unsaved.
 */
internal fun classifyMeshEdit(
    activeName: String,
    isEmpty: Boolean,
    selected: String?,
    savedNames: Set<String>,
): MeshEditKind {
    val target = selected ?: activeName
    val isActiveTarget = selected == null || selected == activeName
    return when {
        !isActiveTarget -> MeshEditKind.PREVIEW_NONACTIVE
        isEmpty || target.isEmpty() || target == "default" || target !in savedNames -> MeshEditKind.ACTIVE_UNSAVED
        else -> MeshEditKind.ACTIVE_SAVED
    }
}
```

- [ ] **Step 4: Run classifier test to verify it passes** — PASS (5 tests).

- [ ] **Step 5: Add the pencil to the FocusFrame + the edit morph**

Pass the trailing pencil to `FocusFrame` (only when not printing — printing keeps the leading-slot e-stop; the trailing pencil is simply hidden while a print runs). Add `editing` state:
```kotlin
var editing by rememberSaveable { mutableStateOf(false) }
// in ScreenScaffold.focus = { FocusFrame(... ) }:
FocusFrame(
    title = …, icon = …, uDp = grid.uDp, isPrinting = isPrinting,
    onEmergencyStop = onEmergencyStop, onPanic = onEmergencyStop,
    trailingActionIcon = if (!isPrinting) /* owner-picked Edit pencil */ else null,
    onTrailingAction = if (!isPrinting) ({ editing = true }) else null,
    trailingActionContentDescription = "Edit mesh profile",
) {
    if (editing) MeshEditForm(...) else BedMeshFocusRegion(...)
}
```
`MeshEditForm` is a docked-action Focus composable: a name `OutlinedTextField`/`OutlinedControl` text entry + contextual buttons from `classifyMeshEdit(...)`:
```kotlin
@Composable
private fun MeshEditForm(
    kind: MeshEditKind,
    targetName: String,
    onApply: () -> Unit,
    onSave: (String) -> Unit,     // SAVE=name (+ rename handled by caller when active-saved & changed)
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    t: ThemeTokens,
) {
    var name by rememberSaveable(targetName) {
        mutableStateOf(if (kind == MeshEditKind.ACTIVE_UNSAVED) "" else targetName)
    }
    val readOnly = kind == MeshEditKind.PREVIEW_NONACTIVE
    val nameValid = PrinterCommands.isValidProfileName(name)
    val nameChanged = name != targetName
    Column(/* docked-action focus layout */) {
        // name entry (read-only for non-active previews)
        MeshNameField(value = name, onValueChange = { name = it }, readOnly = readOnly, t = t)
        Spacer(Modifier.weight(1f))
        // Row 1: Apply (preview) / Save (active)
        Row {
            if (kind == MeshEditKind.PREVIEW_NONACTIVE) {
                OutlinedControl(label = "Apply", intent = Intent.Go, onClick = onApply /* glyph: owner */)
            }
            if (kind == MeshEditKind.ACTIVE_UNSAVED) {
                OutlinedControl(label = "Save", intent = Intent.Go, enabled = nameValid, onClick = { onSave(name) })
            }
            if (kind == MeshEditKind.ACTIVE_SAVED) {
                OutlinedControl(label = "Save", intent = Intent.Go,
                    enabled = nameValid && nameChanged, onClick = { onSave(name) }) // rename
            }
        }
        // Row 2: Delete (whenever a saved profile is the target)
        if (kind != MeshEditKind.ACTIVE_UNSAVED) {
            OutlinedControl(label = "Delete", intent = Intent.Danger, onClick = onDelete)
        }
    }
}
```
> **Implementer note:** use the project's canonical name-entry control (the existing `SaveName` field in this same file already does alphanumeric keyboard entry — extract/reuse it as `MeshNameField`). Use `OutlinedControl` with owner-picked glyphs. Do not hand-roll buttons.

- [ ] **Step 6: Wire the edit intents in the wrapper**

```kotlin
onEditApply = { name -> dispatcher?.dispatch(CommandRegistry.bedMeshProfileLoad, BedMeshProfileArgs(name)); editing = false },
onEditSave = { newName ->
    val d = dispatcher
    val active = vm.model.profileName
    if (d != null) {
        d.dispatch(CommandRegistry.bedMeshProfileSave, BedMeshProfileArgs(newName))
        // ACTIVE_SAVED rename: also remove the old profile so it's a rename, not a copy.
        if (classifyMeshEdit(active, vm.isEmpty, selectedProfile, vm.profileNames.toSet()) == MeshEditKind.ACTIVE_SAVED
            && newName != active) {
            d.dispatch(CommandRegistry.bedMeshProfileRemove, BedMeshProfileArgs(active))
        }
        showSaveConfigGuard = true
    }
    editing = false
},
onEditDelete = {
    val d = dispatcher
    val target = selectedProfile ?: vm.model.profileName
    if (d != null && target.isNotEmpty() && target != "default") {
        d.dispatch(CommandRegistry.bedMeshProfileRemove, BedMeshProfileArgs(target))
        showSaveConfigGuard = true // REMOVE is runtime-only; SAVE_CONFIG persists it
    }
    selectedProfile = null
    editing = false
},
onEditCancel = { editing = false },
```
> The amber `showSaveConfigGuard` reuses the EXISTING SAVE_CONFIG confirm dialog already in `BedMeshContent`.

- [ ] **Step 7: Build + install + on-device UAT (flox + moto)**

Owner verifies each matrix row: fresh Calibrate → Save names it (rejects "default"); active saved → rename (old gone, SAVE_CONFIG prompt); active saved → Delete (mesh stays loaded, becomes unsaved, Save available); preview non-active → Apply loads it, Delete removes it; pencil hidden while printing. **Owner UAT gate.**

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt \
        app/src/test/java/works/mees/dinghy/ui/calibration/BedMeshEditStateTest.kt
git commit -m "feat(calibration): bed mesh Focus edit morph (apply/save-rename/delete matrix)"
```

---

### Task 9: Screen — Mesh Config subpage (View Type / High / Low / Preview) + data-pool swatch picker

**Glyph-blocked** (4 config-row icons). Verified by on-device UAT.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt`
- Create: `app/src/main/java/works/mees/dinghy/ui/calibration/PoolColorPicker.kt` (data-pool swatch grid)

**Interfaces:**
- Consumes: `vm.viewType/highColorSel/lowColorSel` (Task 5); `container.setBedMeshViewType/setBedMeshHighColorSel/setBedMeshLowColorSel` (Task 4); `MeshFieldMode.MeshConfig`/`MeshConfigEditor` (Task 7).

- [ ] **Step 1: Mesh Config list (Field) — `MeshFieldMode.MeshConfig` branch**

Render the config rows; each opens its own editor via `MeshConfigEditor(item)`. Footer = Back (returns to `ProfileList`).
```kotlin
is MeshFieldMode.MeshConfig -> {
    ListBlock(modifier = Modifier.weight(1f)) {
        item(key="vt") { ListRow(onClick={onOpenConfigEditor(MeshConfigItem.VIEW_TYPE)}, uDp=grid.uDp,
            leadingIcon=/*owner*/) { ListRowLabel("View Type") } }
        item(key="hi") { ListRow(onClick={onOpenConfigEditor(MeshConfigItem.HIGH_COLOR)}, uDp=grid.uDp,
            leadingIcon=/*owner*/) { ListRowLabel("High Color") } }
        item(key="lo") { ListRow(onClick={onOpenConfigEditor(MeshConfigItem.LOW_COLOR)}, uDp=grid.uDp,
            leadingIcon=/*owner*/) { ListRowLabel("Low Color") } }
        item(key="pv") { ListRow(onClick={onOpenConfigEditor(MeshConfigItem.PREVIEW)}, uDp=grid.uDp,
            leadingIcon=/*owner*/) { ListRowLabel("Preview") } }
    }
    FootButtonBar(uDp=grid.uDp, actions=listOf(
        FootAction(label="Back", icon=/*existing*/, intent=Intent.Accent, onClick=onBackToProfileList)))
}
```

- [ ] **Step 2: Config editors drive the Focus**

When `fieldMode is MeshConfigEditor`, the Focus shows that item's editor. VIEW_TYPE = a 2-option selector; HIGH/LOW = `PoolColorPicker`; PREVIEW = the mesh rendered with current settings (reuse `BedMeshFocusRegion`/Host with `selectedProfile = null`). The Field shows the config list (or a Back). Selection persists via the existing prefs intents:
```kotlin
when (fieldMode) {
    is MeshFieldMode.MeshConfigEditor -> when (fieldMode.item) {
        MeshConfigItem.VIEW_TYPE -> ViewTypeSelector(
            current = vm.viewType,
            onPick = { container.setBedMeshViewType(it) }, t = t)
        MeshConfigItem.HIGH_COLOR -> PoolColorPicker(
            selected = vm.highColorSel, onPick = { container.setBedMeshHighColorSel(it) }, t = t)
        MeshConfigItem.LOW_COLOR -> PoolColorPicker(
            selected = vm.lowColorSel, onPick = { container.setBedMeshLowColorSel(it) }, t = t)
        MeshConfigItem.PREVIEW -> BedMeshFocusRegion(/* selectedProfile=null, current settings */)
    }
    else -> { /* existing live/preview focus */ }
}
```

- [ ] **Step 3: `PoolColorPicker` component**

A 5-tile selector (Accent sentinel + 4 pool slots), styled like `ColorSwatchGrid`'s `ColorTile` (selected = accent outline + soft fill). Selection value maps to the stored Int (`-1` for Accent, `0..3` for slots):
```kotlin
// app/src/main/java/works/mees/dinghy/ui/calibration/PoolColorPicker.kt
package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import works.mees.dinghy.render.resolveMeshColor
import works.mees.dinghy.theme.ThemeTokens

/** Pick a bed-mesh ramp color from the theme data-pool (Accent + 4 slots). Stores a SLOT SELECTOR. */
@Composable
internal fun PoolColorPicker(selected: Int, onPick: (Int) -> Unit, t: ThemeTokens, modifier: Modifier = Modifier) {
    val options = listOf(-1, 0, 1, 2, 3) // -1 = Accent sentinel; 0..3 = pool slots
    // Fill-to-fit row/grid of swatch tiles, each backgrounded by resolveMeshColor(t, sel),
    // selected = accent outline + soft fill (mirror ui/spool/SpoolPicker.ColorTile).
    // ... lay out tiles, onClick = { onPick(sel) } ...
}
```
> **Implementer note:** copy the tile visuals from `ui/spool/SpoolPicker.kt` `ColorTile` (selection treatment, orientation-aware layout). Tile background = `resolveMeshColor(t, sel)`. Empty pool (`t.pool` size < 4) → show only the available slots + Accent (guard `getOrNull`).

- [ ] **Step 4: `ViewTypeSelector`**

Two selectable rows/tiles — Filled heatmap (`HEATMAP`) and Colored probe points (`PROBE_POINTS`) — selected one marked; `onPick` calls the prefs intent. (Iso wireframe is the documented fast-follow; do not add a third option.)

- [ ] **Step 5: Build + install + on-device UAT (flox + moto)**

Owner verifies: Mesh Config opens; View Type swaps render style live after pick; High/Low color picks change the ramp and persist; the choice is **per-printer** (switch printers → independent); Preview shows current settings; Back returns to the profile list. **Owner UAT gate.**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/calibration/PoolColorPicker.kt
git commit -m "feat(calibration): bed mesh Mesh Config subpage (view type + per-printer ramp colors)"
```

---

## Final verification

- [ ] Full suite: `… "E:\Android\gw.bat :app:testDebugUnitTest --rerun-tasks"` → all green.
- [ ] R8 release build: `… "E:\Android\gw.bat :app:assembleRelease --no-daemon"` → SUCCESSFUL (keep rules intact).
- [ ] `FontConformanceTest` + `verify_ligatures.py` green (any new glyph added to `DinghyIcons` val + `all`).
- [ ] Owner UAT sign-off on flox + moto across the full flow (preview, edit matrix, clear, calibrate, config colors per-printer, scale overlay legibility).

## Spec coverage check

- Field list (Clear Mesh / profiles / Mesh Config) → Task 7. Footer Back + (Home|Calibrate) → Task 7.
- Preview model (probed points, no mutation) → Tasks 2, 3, 7. `BED_MESH_CLEAR` → Tasks 1, 7.
- Focus edit morph + matrix (Apply / Save-rename-active-only / Delete; `default` reserved; active-Delete leaves loaded) → Tasks 1, 8.
- Mesh Config subpage (View Type, High/Low data-pool color, Preview) → Tasks 4, 9. Per-printer persistence (slot selector, not ARGB; TemperatureHolder pattern) → Tasks 4, 5, 6.
- `PROBE_POINTS` view mode → Task 6. Scale overlay transparency → Task 7.
- Fast-follow (iso wireframe) → explicitly excluded (Tasks 6/9 leave room for a third view type).
