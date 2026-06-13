# Move Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the jog-pad Move screen with a "Move Hub" — a Field action-list that swaps the Focus between Touch-Move (tap-to-move bed map), XY/Z scrubbers, a Microstep jogger, per-bookmark Move/Delete, and a Save-Location dialog — backed by new axis-limit state, an absolute `moveTo` command, a vertical Scrubber mode, a Compose bed-map, and saved-location persistence.

**Architecture:** A single `ScreenScaffold` whose Field is the action list and whose Focus content is driven by a screen-local `MoveMode` state (the Fine-Tune Hub pattern). New infra is added bottom-up first (state → command → persistence → components) so each layer is independently testable before the Hub composes them. The old screen is preserved as `OldMoveScreen` in the debug Gallery.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, DataStore (Preferences), OkHttp/Retrofit Moonraker layer, JUnit host tests. Builds Windows-side via `E:\Android\gw.bat`.

---

## Conventions for every task

- **Build (Windows-side, from repo root):**
  ```bash
  /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'
  ```
- **Host unit test (single class):**
  ```bash
  /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.<FQCN>\" --no-daemon" 2>&1 | tr -d '\r'
  ```
  The process exit code is authoritative (CR progress bars stripped by `tr -d '\r'`). Guard against hangs with `timeout` per the build-env memory.
- **Every commit message ends with the repo trailer:**
  ```
  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  ```
- **Icons are owner-law** ([[dinghy-never-pick-icons-ask]]): the five new glyphs are already owner-approved (`my_location`, `control_camera`, `swap_vert`, `bookmark`, `bookmark_add`). Do **not** introduce any other glyph without asking.
- **DataStore writes** go through `AppContainer.writeScope` intent methods, never a composition scope ([[dinghy-compose-write-scope-cancellation]]).
- **On-device UAT** installs both ABI slices on flox (`0a64b42e`) and moto (`ZY22LBDRM9`); force-rebuild before UAT ([[dinghy-stale-apk-uat-gate]]).

---

# PHASE A — Data & command foundation (no UI, fully host-tested)

### Task A1: Axis limits in PrinterState

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` (add two fields near `toolheadPosition`, ~line 60-71)
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt:110-121` (the `toolhead` walk)
- Test: `app/src/test/java/works/mees/dinghy/state/PrinterStateReducerAxisLimitsTest.kt` (new)

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.state

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterStateReducerAxisLimitsTest {
    private fun status(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun toolhead_axisLimits_areReducedIntoState() {
        val s = applyStatus(
            PrinterState(),
            status("""{"toolhead":{"axis_minimum":[-5.0,0.0,0.0,0.0],"axis_maximum":[355.0,355.0,340.0,0.0]}}"""),
        )
        assertEquals(listOf(-5.0, 0.0, 0.0, 0.0), s.axisMinimum)
        assertEquals(listOf(355.0, 355.0, 340.0, 0.0), s.axisMaximum)
    }

    @Test
    fun partialToolheadDiff_retainsExistingAxisLimits() {
        val seeded = applyStatus(
            PrinterState(),
            status("""{"toolhead":{"axis_maximum":[355.0,355.0,340.0,0.0]}}"""),
        )
        val after = applyStatus(seeded, status("""{"toolhead":{"homed_axes":"xyz"}}"""))
        assertEquals(listOf(355.0, 355.0, 340.0, 0.0), after.axisMaximum)
    }
}
```

> NOTE: `applyStatus` is `internal` in `PrinterStateReducer.kt`; the test is in the same package + module so it is visible. If it is `private`, change it to `internal` (it is already routed through by both `reduceSnapshot`/`reduceDiff`).

- [ ] **Step 2: Run test to verify it fails**

Run the host-test command for `state.PrinterStateReducerAxisLimitsTest`.
Expected: FAIL — `axisMinimum`/`axisMaximum` unresolved (compile error) or assertion failure.

- [ ] **Step 3: Add the two fields to `PrinterState`**

In `PrinterState.kt`, immediately after the `toolheadPosition` field (~line 61), add:

```kotlin
    /** `toolhead.axis_minimum` `[x, y, z, e]` (mm) — lower motion bound; null until first snapshot. May be negative. */
    val axisMinimum: ImmutableList<Double>? = null,

    /** `toolhead.axis_maximum` `[x, y, z, e]` (mm) — upper motion bound; null until first snapshot. */
    val axisMaximum: ImmutableList<Double>? = null,
```

- [ ] **Step 4: Add the two reducer lines**

In `PrinterStateReducer.kt`, inside the existing `status.objectOrNull("toolhead")?.let { th -> ... }` block (after the `square_corner_velocity` line, ~121):

```kotlin
        th.doubleListOrNull("axis_minimum")?.let { s = s.copy(axisMinimum = it.toImmutableList()) }
        th.doubleListOrNull("axis_maximum")?.let { s = s.copy(axisMaximum = it.toImmutableList()) }
```

(`doubleListOrNull` and `toImmutableList()` are already imported/used for `position`.)

- [ ] **Step 5: Run the test — expect PASS**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/state/PrinterState.kt \
        app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt \
        app/src/test/java/works/mees/dinghy/state/PrinterStateReducerAxisLimitsTest.kt
git commit -m "feat(state): reduce toolhead axis_minimum/axis_maximum into PrinterState"
```

---

### Task A2: `PrinterCommands.moveTo` (absolute, clamped, mode-safe)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt` (add builder near `jog`, ~line 275-313)
- Test: `app/src/test/java/works/mees/dinghy/command/PrinterCommandsMoveToTest.kt` (new)

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.command

import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterCommandsMoveToTest {
    @Test
    fun moveTo_buildsModeSafeAbsoluteMove_withAllAxes() {
        val g = PrinterCommands.moveTo(x = 100.0, y = 120.5, z = 5.0, feedMmMin = 9000)
        assertEquals(
            "SAVE_GCODE_STATE NAME=dd_moveto\nG90\nG1 X100 Y120.5 Z5 F9000\nRESTORE_GCODE_STATE NAME=dd_moveto",
            g,
        )
    }

    @Test
    fun moveTo_omitsNullAxes() {
        val g = PrinterCommands.moveTo(x = 100.0, y = 120.0, z = null, feedMmMin = 6000)
        assertEquals(
            "SAVE_GCODE_STATE NAME=dd_moveto\nG90\nG1 X100 Y120 F6000\nRESTORE_GCODE_STATE NAME=dd_moveto",
            g,
        )
    }

    @Test
    fun moveTo_clampsToProvidedBounds() {
        // X requested above max -> clamped to max; Z requested below min -> clamped to min.
        val g = PrinterCommands.moveTo(
            x = 999.0, y = 50.0, z = -10.0, feedMmMin = 6000,
            minBounds = listOf(-5.0, 0.0, 0.0), maxBounds = listOf(355.0, 355.0, 340.0),
        )
        assertEquals(
            "SAVE_GCODE_STATE NAME=dd_moveto\nG90\nG1 X355 Y50 Z0 F6000\nRESTORE_GCODE_STATE NAME=dd_moveto",
            g,
        )
    }

    @Test
    fun moveTo_clampsFeedToJogFeedCeiling() {
        val g = PrinterCommands.moveTo(x = 10.0, y = null, z = null, feedMmMin = 999_999)
        assertEquals(
            "SAVE_GCODE_STATE NAME=dd_moveto\nG90\nG1 X10 F30000\nRESTORE_GCODE_STATE NAME=dd_moveto",
            g,
        )
    }
}
```

- [ ] **Step 2: Run test — expect FAIL** (`moveTo` unresolved).

- [ ] **Step 3: Implement `moveTo`**

Add inside `object PrinterCommands`, just below `jog` (~line 280):

```kotlin
    /**
     * Absolute toolhead move to the given coordinates. Null axes are omitted. Each provided axis is
     * clamped to its [minBounds]/[maxBounds] (pass the live toolhead.axis_minimum/axis_maximum); a
     * null bounds list means "no clamp for that axis". Mode-safe: saves/restores gcode state and
     * forces G90 so it never corrupts the caller's relative/absolute mode. Feed clamped to the jog
     * feed window. Doubles are formatted through [fmt] (the single Locale.US chokepoint).
     */
    fun moveTo(
        x: Double?,
        y: Double?,
        z: Double?,
        feedMmMin: Int,
        minBounds: List<Double>? = null,
        maxBounds: List<Double>? = null,
    ): String {
        fun clampAxis(v: Double?, i: Int): Double? {
            if (v == null) return null
            val lo = minBounds?.getOrNull(i)
            val hi = maxBounds?.getOrNull(i)
            var r = v
            if (lo != null) r = maxOf(r, lo)
            if (hi != null) r = minOf(r, hi)
            return r
        }
        val cx = clampAxis(x, 0)
        val cy = clampAxis(y, 1)
        val cz = clampAxis(z, 2)
        val f = feedMmMin.coerceIn(MIN_FEED_MM_MIN, MAX_JOG_FEED_MM_MIN)
        val axes = buildString {
            if (cx != null) append(" X").append(fmt(cx, MOVE_DECIMALS))
            if (cy != null) append(" Y").append(fmt(cy, MOVE_DECIMALS))
            if (cz != null) append(" Z").append(fmt(cz, MOVE_DECIMALS))
        }
        return "SAVE_GCODE_STATE NAME=dd_moveto\nG90\nG1$axes F$f\nRESTORE_GCODE_STATE NAME=dd_moveto"
    }
```

Add the decimals constant beside the other consts (~line 49):

```kotlin
    const val MOVE_DECIMALS = 3
```

(`MIN_FEED_MM_MIN`, `MAX_JOG_FEED_MM_MIN`, and `fmt` already exist. `fmt` trims trailing zeros, so `100.0 -> "100"`, `120.5 -> "120.5"`.)

- [ ] **Step 4: Run the test — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt \
        app/src/test/java/works/mees/dinghy/command/PrinterCommandsMoveToTest.kt
git commit -m "feat(command): add bounded absolute moveTo gcode builder"
```

---

### Task A3: Register `moveTo` command + catalog/matrix rows (D-10)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` (args data class ~line 22; spec ~line 431; `all` list ~line 773-840)
- Modify: `docs/commands/catalog.json` (append one entry to the `commands` array)
- Modify: `docs/commands/printer-matrix.json` (append one entry to `command_availability`)
- Test: `app/src/test/java/works/mees/dinghy/command/CommandCatalogDriftTest.kt` (existing — must stay green)

- [ ] **Step 1: Add the args data class** (beside `JogArgs`, ~line 22)

```kotlin
data class MoveToArgs(
    val x: Double?,
    val y: Double?,
    val z: Double?,
    val feedMmMin: Int,
    val minBounds: List<Double>?,
    val maxBounds: List<Double>?,
)
```

- [ ] **Step 2: Add the registry spec** (beside `jog`, ~line 431)

```kotlin
    val moveTo: CommandSpec<MoveToArgs> = gcode(
        catalogId = "KGC-G1_MOVE_TO",
        key = { "move_to" },
        gcode = { args ->
            PrinterCommands.moveTo(args.x, args.y, args.z, args.feedMmMin, args.minBounds, args.maxBounds)
        },
        availability = AvailabilityPredicate.ObjectPresent("toolhead"),
    )
```

- [ ] **Step 3: Append `moveTo` to the `all` list** (~line 773-840, alongside `jog`, `homeAll`, etc.)

- [ ] **Step 4: Append the catalog.json entry** (to the `commands` array)

```json
    {
      "id": "KGC-G1_MOVE_TO",
      "catalog_id": "KGC-G1_MOVE_TO",
      "source_api": "klipper_gcode",
      "transport": "gcode_script",
      "name": "G1 absolute move",
      "category": "motion",
      "purpose": "Dinghy bounded absolute move-to wrapper (Move Hub touch/scrubber moves).",
      "params": ["X", "Y", "Z", "F"],
      "key_params": ["X", "Y", "Z", "F"],
      "semantics_tier": "full",
      "upstream_url": "https://www.klipper3d.org/G-Codes.html",
      "rest_endpoint": null,
      "http_method": null,
      "availability": { "type": "object_present", "name": "toolhead" },
      "predicate": { "type": "object_present", "name": "toolhead" },
      "runtime_registry": {
        "status": "registered",
        "registered": true,
        "notes": "Present in CommandRegistry for current Dinghy runtime sends."
      },
      "success_semantics": "Klipper accepts the G-Code script and reports follow-up state or response text when applicable.",
      "error_semantics": "Klipper rejects invalid or unsafe commands with gcode error text.",
      "acceptance_semantics": "Reference-only entries are not sent by Dinghy unless a later phase registers them."
    }
```

- [ ] **Step 5: Append the printer-matrix.json entry** (to the `command_availability` array)

```json
    {
      "catalog_id": "KGC-G1_MOVE_TO",
      "name": "G1 absolute move",
      "source_api": "klipper_gcode",
      "transport": "gcode_script",
      "registry_status": "registered",
      "predicate": { "type": "object_present", "name": "toolhead" },
      "predicate_key": "object_present:toolhead",
      "printer_status": [
        { "printer_id": "ender5plus", "status": "present", "evidence": "object toolhead listed in /printer/objects/list capture." },
        { "printer_id": "ender3", "status": "present", "evidence": "object toolhead listed in /printer/objects/list capture." }
      ]
    }
```

- [ ] **Step 6: Run the drift test — expect PASS**

Run host test for `command.CommandCatalogDriftTest`. Expected: PASS (all four assertions). If `registryCommandsHaveMatrixAvailabilityRows` or `registryCatalogIdsExistInCatalogJson` fails, re-check the `catalog_id` string matches exactly.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt docs/commands/catalog.json docs/commands/printer-matrix.json
git commit -m "feat(command): register KGC-G1_MOVE_TO with catalog + matrix rows"
```

---

### Task A4: `SavedLocation` model + `SavedLocationPrefs`

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/move/SavedLocation.kt`
- Create: `app/src/main/java/works/mees/dinghy/ui/move/SavedLocationPrefs.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/move/SavedLocationPrefsTest.kt`

- [ ] **Step 1: Write the model**

```kotlin
package works.mees.dinghy.ui.move

import kotlinx.serialization.Serializable

/** A named toolhead position. [z] is null when the user chose not to save a Z height (XY-only recall). */
@Serializable
data class SavedLocation(
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double? = null,
)
```

- [ ] **Step 2: Write the failing prefs round-trip test**

```kotlin
package works.mees.dinghy.ui.move

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SavedLocationPrefsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun newStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { File(tmp.newFolder(), "savedlocations.preferences_pb") },
        )

    @Test
    fun add_listsLocation_withAndWithoutZ() = runTest {
        val prefs = SavedLocationPrefs(newStore())
        prefs.add(SavedLocation("center", 175.0, 175.0, 5.0))
        prefs.add(SavedLocation("purge", -2.0, 10.0, null))
        val all = prefs.locations.first()
        assertEquals(2, all.size)
        assertEquals(5.0, all.first { it.name == "center" }.z)
        assertEquals(null, all.first { it.name == "purge" }.z)
    }

    @Test
    fun remove_dropsByName() = runTest {
        val prefs = SavedLocationPrefs(newStore())
        prefs.add(SavedLocation("a", 1.0, 2.0, null))
        prefs.add(SavedLocation("b", 3.0, 4.0, null))
        prefs.remove("a")
        assertEquals(listOf("b"), prefs.locations.first().map { it.name })
    }

    @Test
    fun add_sameName_replaces() = runTest {
        val prefs = SavedLocationPrefs(newStore())
        prefs.add(SavedLocation("home", 1.0, 1.0, null))
        prefs.add(SavedLocation("home", 9.0, 9.0, 2.0))
        val all = prefs.locations.first()
        assertEquals(1, all.size)
        assertEquals(9.0, all.first().x, 0.0)
    }
}
```

- [ ] **Step 3: Run test — expect FAIL** (`SavedLocationPrefs` unresolved).

- [ ] **Step 4: Implement `SavedLocationPrefs`** (MacroPrefs template + JSON blob)

```kotlin
package works.mees.dinghy.ui.move

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Persists the user's named toolhead positions as one JSON array string in DataStore. Order is
 * insertion order; name is the identity (adding an existing name replaces it). Fail-safe: a read
 * error or corrupt blob yields the empty list, never throws.
 */
class SavedLocationPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    val locations: Flow<List<SavedLocation>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> decode(prefs[KEY_LOCATIONS]) }

    /** Add [loc], replacing any existing entry with the same name. */
    suspend fun add(loc: SavedLocation) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_LOCATIONS]).filterNot { it.name == loc.name }
            prefs[KEY_LOCATIONS] = JSON.encodeToString(current + loc)
        }
    }

    /** Remove the location named [name] (idempotent). */
    suspend fun remove(name: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_LOCATIONS]).filterNot { it.name == name }
            prefs[KEY_LOCATIONS] = JSON.encodeToString(current)
        }
    }

    private fun decode(raw: String?): List<SavedLocation> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { JSON.decodeFromString<List<SavedLocation>>(raw) }.getOrDefault(emptyList())

    companion object {
        private val KEY_LOCATIONS = stringPreferencesKey("locations")
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
```

> If `kotlinx.serialization.json.Json.encodeToString`/`decodeFromString` reified overloads are not resolving, ensure the `import kotlinx.serialization.encodeToString` / `import kotlinx.serialization.decodeFromString` reified helpers are present (they are part of the kotlinx-serialization-json artifact already on the classpath).

- [ ] **Step 5: Run the test — expect PASS**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/move/SavedLocation.kt \
        app/src/main/java/works/mees/dinghy/ui/move/SavedLocationPrefs.kt \
        app/src/test/java/works/mees/dinghy/ui/move/SavedLocationPrefsTest.kt
git commit -m "feat(move): saved-location model + DataStore persistence"
```

---

### Task A5: Wire `SavedLocationPrefs` through `DinghyApp` + `AppContainer`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/DinghyApp.kt` (create 9th DataStore ~line 55; pass into `AppContainer(...)` ~line 106)
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` (ctor param ~line 80; instance ~line 259; derived flow ~line 270; intent methods ~line 199)

- [ ] **Step 1: Create the DataStore in `DinghyApp.kt`** (mirror the `macroDataStore` block, ~line 55)

```kotlin
    val savedLocationDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = appScope,
        produceFile = { applicationContext.preferencesDataStoreFile("savedlocations.preferences_pb") },
    )
```

- [ ] **Step 2: Pass it into the `AppContainer(...)` call** (~line 106) — add `savedLocationDataStore = savedLocationDataStore,` alongside the other stores.

- [ ] **Step 3: Add the ctor param + instance + flow + intents in `AppContainer.kt`**

Ctor (~line 80), add:
```kotlin
    savedLocationDataStore: DataStore<Preferences>,
```

Instance + derived flow (beside `macroPrefs`, ~line 259):
```kotlin
    val savedLocationPrefs: SavedLocationPrefs = SavedLocationPrefs(savedLocationDataStore)

    val savedLocations: StateFlow<List<SavedLocation>> =
        savedLocationPrefs.locations.stateIn(stateScope, SharingStarted.Eagerly, emptyList())
```

Intent methods (beside `toggleMacroBookmark`, ~line 199):
```kotlin
    fun saveLocation(loc: SavedLocation) {
        writeScope.launch { savedLocationPrefs.add(loc) }
    }

    fun deleteLocation(name: String) {
        writeScope.launch { savedLocationPrefs.remove(name) }
    }
```

Add imports: `works.mees.dinghy.ui.move.SavedLocation`, `works.mees.dinghy.ui.move.SavedLocationPrefs`.

- [ ] **Step 4: Build — expect SUCCESS**

Run the assembleDebug command. Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/DinghyApp.kt app/src/main/java/works/mees/dinghy/di/AppContainer.kt
git commit -m "feat(move): wire SavedLocationPrefs through DinghyApp + AppContainer"
```

---

# PHASE B — Vertical Scrubber

### Task B1: `fractionFromY` helper (vertical, Y-inverted)

**Files:**
- Modify: the file that defines `fractionFromX` (find via `grep -rn "fun fractionFromX" app/src/main`; it lives in package `works.mees.dinghy.designsystem`)
- Test: `app/src/test/java/works/mees/dinghy/designsystem/ScrubberFractionTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubberFractionTest {
    @Test
    fun fractionFromY_bottomIsZero_topIsOne() {
        // Vertical scrubbers fill from the BOTTOM: y == height -> 0f, y == 0 -> 1f.
        assertEquals(0f, fractionFromY(100f, 100f), 0.0001f)
        assertEquals(1f, fractionFromY(0f, 100f), 0.0001f)
        assertEquals(0.5f, fractionFromY(50f, 100f), 0.0001f)
    }

    @Test
    fun fractionFromY_clampsOutOfBounds() {
        assertEquals(1f, fractionFromY(-20f, 100f), 0.0001f)
        assertEquals(0f, fractionFromY(140f, 100f), 0.0001f)
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (`fractionFromY` unresolved).

- [ ] **Step 3: Implement** (next to `fractionFromX`)

```kotlin
/** Vertical-scrubber fraction: 0f at the bottom (y == height), 1f at the top (y == 0). Clamped. */
fun fractionFromY(y: Float, trackHeightPx: Float): Float {
    if (trackHeightPx <= 0f) return 0f
    return (1f - (y / trackHeightPx)).coerceIn(0f, 1f)
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/*.kt app/src/test/java/works/mees/dinghy/designsystem/ScrubberFractionTest.kt
git commit -m "feat(designsystem): fractionFromY helper for vertical scrubbers"
```

---

### Task B2: Add `orientation` to `Scrubber`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/Scrubber.kt`
- Modify: `app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt` (add a vertical-scrubber demo section)

**Approach:** Add `orientation: ScrubberOrientation = ScrubberOrientation.Horizontal`. Keep ALL snap/clamp/`fmt` math axis-agnostic (unchanged). Branch only the layout-bearing pieces:
- `onSizeChanged`: capture both width and height; the gesture uses the relevant axis.
- gesture: `setFromX(change.position.x)` for Horizontal, `setFromY(change.position.y)` for Vertical (where `setFromY` uses `fractionFromY`).
- `drawBehind`: Horizontal = left-anchored `Size(fillEnd, trackThickness)`; Vertical = bottom-anchored `Size(trackThickness, fillLen)` at `Offset(cx - trackHalf, h - fillLen)`.
- thumb: Horizontal `align(CenterStart)` + X offset; Vertical `align(BottomCenter)` + negative-Y offset.
- container: Horizontal `fillMaxWidth().height(rowHeight)`; Vertical `fillMaxHeight().width(rowHeight)` (caller gives it a column height).

- [ ] **Step 1: Add the orientation enum** (top of `Scrubber.kt`)

```kotlin
enum class ScrubberOrientation { Horizontal, Vertical }
```

- [ ] **Step 2: Add the param** to the `Scrubber` signature (after `uDp`):

```kotlin
    orientation: ScrubberOrientation = ScrubberOrientation.Horizontal,
```

- [ ] **Step 3: Generalize size capture + gesture**

Replace `var trackWidthPx by remember { mutableFloatStateOf(0f) }` with both dimensions:
```kotlin
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
```

Add a `setFromY`:
```kotlin
    fun setFromY(y: Float) {
        if (trackHeightPx <= 0f) return
        set(range.start + fractionFromY(y, trackHeightPx) * span)
    }
```

In the gesture box, branch `onSizeChanged` and the position reads:
```kotlin
                .onSizeChanged { trackWidthPx = it.width.toFloat(); trackHeightPx = it.height.toFloat() }
                .pointerInput(range, step, orientation) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        if (orientation == ScrubberOrientation.Horizontal) setFromX(down.position.x) else setFromY(down.position.y)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    if (orientation == ScrubberOrientation.Horizontal) setFromX(change.position.x) else setFromY(change.position.y)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        pressed = false
                        settle()
                    }
                }
```

- [ ] **Step 4: Branch the `drawBehind` fill and the thumb + container modifier** per the Approach notes above (vertical: bottom-anchored fill, `BottomCenter` thumb with `IntOffset(0, -(center - thumbRadiusPx))`, container `fillMaxHeight().width(rowHeight)`). The header/ends/stepper rows remain only for the Horizontal layout; for Vertical, the caller composes its own label (the Z/XY focus owns labels), so when `orientation == Vertical` render ONLY the gesture box (no header/ends/stepper Column wrapper). Keep the Horizontal path byte-for-byte as today.

> Concretely: wrap the existing `Column { header; gestureBox; ends; stepperRow }` in `if (orientation == Horizontal) { ...existing... } else { Box(Modifier.fillMaxHeight().width(rowHeight)) { gestureBox-vertical } }`. Factor the gesture box into a local `@Composable` so both paths share it.

- [ ] **Step 5: Add a Gallery demo section** in `GalleryScreen.kt` (append in the Column):

```kotlin
        SectionLabel("Vertical Scrubber")
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            var v by remember { mutableFloatStateOf(25f) }
            Scrubber(
                name = "Z", value = v, range = 0f..50f, step = 0.1f, uDp = 64.dp,
                orientation = ScrubberOrientation.Vertical,
                onSettle = { v = it }, onValueChange = { v = it },
            )
        }
```

- [ ] **Step 6: Build, then install on flox + moto and eyeball the Gallery vertical scrubber** (fills from the bottom, thumb tracks the finger, snaps to step, `onSettle` fires once on release). Horizontal scrubbers elsewhere (Outputs) are visually unchanged.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/Scrubber.kt app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt
git commit -m "feat(designsystem): vertical orientation for Scrubber"
```

---

# PHASE C — BedMapView (Compose Canvas)

### Task C1: Bed↔touch coordinate mapping (pure functions)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/move/BedCoords.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/move/BedCoordsTest.kt`

The bed extent is `axisMin[0..1]..axisMax[0..1]`. The drawn rect is aspect-locked (no stretch) and centered in the available box; bed +Y is screen-up. Negative origins are valid.

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.ui.move

import org.junit.Assert.assertEquals
import org.junit.Test

class BedCoordsTest {
    private val bed = BedExtent(xMin = -5.0, xMax = 355.0, yMin = 0.0, yMax = 355.0)

    @Test
    fun fit_isAspectLocked_noStretch_squareBedInWideBox() {
        // 360x355 bed into a 200x100 box -> letterboxed to keep aspect; width-bound.
        val r = bedFitRect(bed, boxW = 200f, boxH = 100f)
        // aspect = 360/355 ~= 1.014; box aspect 2.0 -> width is the binding side minus nothing,
        // height = 200 / 1.014 = 197.2 > 100 -> actually height-bound. Assert no stretch:
        assertEquals(r.width / r.height, (bed.xMax - bed.xMin).toFloat() / (bed.yMax - bed.yMin).toFloat(), 0.001f)
    }

    @Test
    fun bedToScreen_originCornerMapsToBottomLeft_topRightToTop() {
        val r = bedFitRect(bed, boxW = 360f, boxH = 355f) // 1:1 mapping, no letterbox
        val bl = bedToScreen(bed, r, x = -5.0, y = 0.0)   // bed min corner
        val tr = bedToScreen(bed, r, x = 355.0, y = 355.0) // bed max corner
        assertEquals(r.left, bl.x, 0.5f)
        assertEquals(r.bottom, bl.y, 0.5f)   // +Y up => yMin at bottom
        assertEquals(r.right, tr.x, 0.5f)
        assertEquals(r.top, tr.y, 0.5f)
    }

    @Test
    fun screenToBed_isInverseOf_bedToScreen() {
        val r = bedFitRect(bed, boxW = 300f, boxH = 300f)
        val p = bedToScreen(bed, r, x = 100.0, y = 200.0)
        val back = screenToBed(bed, r, p.x, p.y)
        assertEquals(100.0, back.first, 0.01)
        assertEquals(200.0, back.second, 0.01)
    }
}
```

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement `BedCoords.kt`**

```kotlin
package works.mees.dinghy.ui.move

/** Bed motion extent in mm (from toolhead.axis_minimum/axis_maximum). May have negative mins. */
data class BedExtent(val xMin: Double, val xMax: Double, val yMin: Double, val yMax: Double) {
    val width: Double get() = xMax - xMin
    val height: Double get() = yMax - yMin
}

/** A screen rectangle in px. */
data class BedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class ScreenPoint(val x: Float, val y: Float)

/** Aspect-locked (no-stretch) bed rectangle centered inside a [boxW] x [boxH] px box. */
fun bedFitRect(bed: BedExtent, boxW: Float, boxH: Float): BedRect {
    val bedAspect = (bed.width / bed.height).toFloat()   // w/h
    val boxAspect = boxW / boxH
    val (w, h) = if (boxAspect > bedAspect) {
        // box is wider than the bed -> height-bound
        val hh = boxH
        val ww = hh * bedAspect
        ww to hh
    } else {
        val ww = boxW
        val hh = ww / bedAspect
        ww to hh
    }
    val left = (boxW - w) / 2f
    val top = (boxH - h) / 2f
    return BedRect(left, top, left + w, top + h)
}

/** Bed mm -> screen px. Bed +Y maps to screen-up (smaller y). */
fun bedToScreen(bed: BedExtent, rect: BedRect, x: Double, y: Double): ScreenPoint {
    val fx = ((x - bed.xMin) / bed.width).toFloat().coerceIn(0f, 1f)
    val fy = ((y - bed.yMin) / bed.height).toFloat().coerceIn(0f, 1f)
    return ScreenPoint(
        x = rect.left + fx * rect.width,
        y = rect.bottom - fy * rect.height,  // invert: yMin at bottom
    )
}

/** Screen px -> bed mm (inverse of [bedToScreen]); clamps to the bed extent. */
fun screenToBed(bed: BedExtent, rect: BedRect, px: Float, py: Float): Pair<Double, Double> {
    val fx = ((px - rect.left) / rect.width).coerceIn(0f, 1f)
    val fy = ((rect.bottom - py) / rect.height).coerceIn(0f, 1f)
    return (bed.xMin + fx * bed.width) to (bed.yMin + fy * bed.height)
}
```

- [ ] **Step 4: Run — expect PASS.**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/move/BedCoords.kt app/src/test/java/works/mees/dinghy/ui/move/BedCoordsTest.kt
git commit -m "feat(move): aspect-locked bed<->screen coordinate mapping"
```

---

### Task C2: `BedMapView` composable

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/move/BedMapView.kt`
- Modify: `app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt` (demo section)

`BedMapView` is a Compose `Canvas` (low-churn; repaints on tap/drag/250ms tick). It draws: bed rect outline (token `outline`), current marker (filled dot, token `accent` or `text2`), optional target marker (ring, `accent`), and an optional travel line (`accent`) between them. It raises `onTapBed(x,y)` / `onDragBed(x,y)` / `onDragEnd()` in BED coords. Colors are token-routed.

- [ ] **Step 1: Implement `BedMapView`**

```kotlin
package works.mees.dinghy.ui.move

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import works.mees.dinghy.theme.compose.LocalTokens

/**
 * To-scale, no-stretch overhead bed map. [current] and [target] are bed-mm coordinates (or null).
 * Tap or drag report bed-mm via [onTapBed]/[onDragBed]; [onDragEnd] fires once on release. When
 * [travel] is true, an accent line is drawn current->target (the in-flight move indicator).
 */
@Composable
fun BedMapView(
    bed: BedExtent,
    current: Pair<Double, Double>?,
    target: Pair<Double, Double>?,
    travel: Boolean,
    modifier: Modifier = Modifier,
    onTapBed: ((Double, Double) -> Unit)? = null,
    onDragBed: ((Double, Double) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    val t = LocalTokens.current
    var rect by remember { mutableStateOf(BedRect(0f, 0f, 0f, 0f)) }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(bed) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val (bx, by) = screenToBed(bed, rect, down.position.x, down.position.y)
                    onTapBed?.invoke(bx, by)
                    onDragBed?.invoke(bx, by)
                    down.consume()
                    do {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { c ->
                            if (c.pressed) {
                                val (dx, dy) = screenToBed(bed, rect, c.position.x, c.position.y)
                                onDragBed?.invoke(dx, dy)
                                c.consume()
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    onDragEnd?.invoke()
                }
            },
    ) {
        rect = bedFitRect(bed, size.width, size.height)
        // Bed plate
        drawRect(
            color = t.surface3,
            topLeft = Offset(rect.left, rect.top),
            size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
        )
        drawRect(
            color = t.outline,
            topLeft = Offset(rect.left, rect.top),
            size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
            style = Stroke(width = 2.dp.toPx()),
        )
        val cur = current?.let { bedToScreen(bed, rect, it.first, it.second) }
        val tgt = target?.let { bedToScreen(bed, rect, it.first, it.second) }
        if (travel && cur != null && tgt != null) {
            drawLine(color = t.accent, start = Offset(cur.x, cur.y), end = Offset(tgt.x, tgt.y), strokeWidth = 3.dp.toPx())
        }
        // Target = hollow ring
        if (tgt != null) {
            drawCircle(color = t.accent, radius = 9.dp.toPx(), center = Offset(tgt.x, tgt.y), style = Stroke(width = 3.dp.toPx()))
        }
        // Current = filled dot
        if (cur != null) {
            drawCircle(color = t.accent, radius = 6.dp.toPx(), center = Offset(cur.x, cur.y))
        }
    }
}
```

> ICON NOTE: the spec mentioned a `my_location` glyph as the current-position marker. A glyph inside a Canvas needs a measured/painted vector, which is heavier; the dot above is the lean default. If the owner wants the actual `my_location` glyph on the map, that is a follow-up polish item — do NOT swap a glyph in without asking ([[dinghy-never-pick-icons-ask]]).

- [ ] **Step 2: Add a Gallery demo** (`GalleryScreen.kt`):

```kotlin
        SectionLabel("Bed Map")
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            var tgt by remember { mutableStateOf<Pair<Double, Double>?>(null) }
            BedMapView(
                bed = BedExtent(-5.0, 355.0, 0.0, 355.0),
                current = 175.0 to 175.0,
                target = tgt,
                travel = tgt != null,
                onTapBed = { x, y -> tgt = x to y },
                onDragBed = { x, y -> tgt = x to y },
                onDragEnd = {},
            )
        }
```

- [ ] **Step 3: Build, install on flox + moto, eyeball** — bed rect is not stretched, tapping drops a target ring + accent line from center, drag moves the ring.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/move/BedMapView.kt app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt
git commit -m "feat(move): BedMapView Compose canvas (bed rect, markers, travel line)"
```

---

# PHASE D — Hub shell, holder, rename

### Task D1: Rename current Move → OldMove + wire into Gallery

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` (rename symbols only)
- Rename file to: `app/src/main/java/works/mees/dinghy/ui/move/OldMoveScreen.kt`
- Modify: `app/src/main/java/works/mees/dinghy/preview/MovePreviews.kt` (retarget)
- Modify: `app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt` (add section)
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:541` (temporarily render `OldMoveScreen` to keep the app building until D3 swaps in the new Hub)

- [ ] **Step 1:** `git mv` the file, then rename inside it: both `fun MoveScreen` → `fun OldMoveScreen`, `fun MoveContent` → `fun OldMoveContent`. Update `MovePreviews.kt` references (`MoveContent`/`MoveScreen` preview overload → `OldMove*`).

```bash
git mv app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt app/src/main/java/works/mees/dinghy/ui/move/OldMoveScreen.kt
```

- [ ] **Step 2:** Update `AppShell.kt:541` `composable<NavDest.Move>` block to call `OldMoveScreen(container = container, holder = moveHolder, onBack = { navController.popBackStack() })` (keeps the app running on the old screen until D3).

- [ ] **Step 3:** Add a Gallery section (`GalleryScreen.kt`) rendering the stateless overload:

```kotlin
        SectionLabel("OldMoveScreen (retired jog pad)")
        Box(Modifier.fillMaxWidth().height(420.dp)) {
            OldMoveScreen(vm = MoveVm(xHomed = true, yHomed = true, zHomed = true, allHomed = true, x = 117.0, y = 117.0, z = 5.0))
        }
```

- [ ] **Step 4: Build — expect SUCCESS.** App still shows the old Move; Gallery shows it too.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(move): rename Move screen to OldMoveScreen + add to debug Gallery"
```

---

### Task D2: Extend `MoveHolder`/`MoveVm` (bounds, feed, saved locations, availability)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/move/MoveHolder.kt`
- Create: `app/src/main/java/works/mees/dinghy/ui/move/MoveAvailability.kt` (pure availability logic, host-tested)
- Test: `app/src/test/java/works/mees/dinghy/ui/move/MoveAvailabilityTest.kt`

- [ ] **Step 1: Write the failing availability test**

```kotlin
package works.mees.dinghy.ui.move

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveAvailabilityTest {
    private fun rows(x: Boolean, y: Boolean, z: Boolean): MoveRowAvailability =
        moveRowAvailability(xHomed = x, yHomed = y, zHomed = z)

    @Test fun homeAll_alwaysShown() {
        assertTrue(rows(true, true, true).homeAll)
        assertTrue(rows(false, false, false).homeAll)
    }
    @Test fun homeXY_onlyWhenXyUnhomed() {
        assertTrue(rows(false, true, true).homeXY)
        assertFalse(rows(true, true, true).homeXY)
    }
    @Test fun homeZ_onlyWhenZUnhomed() {
        assertTrue(rows(true, true, false).homeZ)
        assertFalse(rows(true, true, true).homeZ)
    }
    @Test fun touchMove_onlyWhenAllHomed() {
        assertTrue(rows(true, true, true).touchMove)
        assertFalse(rows(true, true, false).touchMove)
    }
    @Test fun xy_requiresXY_z_requiresZ() {
        assertTrue(rows(true, true, false).xy)
        assertFalse(rows(true, false, false).xy)
        assertTrue(rows(false, false, true).z)
    }
    @Test fun microstep_anyHomed_saveLocation_allHomed() {
        assertTrue(rows(false, false, true).microstep)
        assertFalse(rows(false, false, false).microstep)
        assertTrue(rows(true, true, true).saveLocation)
        assertFalse(rows(true, true, false).saveLocation)
    }
}
```

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement `MoveAvailability.kt`**

```kotlin
package works.mees.dinghy.ui.move

/** Which Field rows are shown/enabled for a given homed state. */
data class MoveRowAvailability(
    val homeAll: Boolean,
    val homeXY: Boolean,
    val homeZ: Boolean,
    val touchMove: Boolean,
    val xy: Boolean,
    val z: Boolean,
    val microstep: Boolean,
    val saveLocation: Boolean,
)

fun moveRowAvailability(xHomed: Boolean, yHomed: Boolean, zHomed: Boolean): MoveRowAvailability {
    val xy = xHomed && yHomed
    val all = xy && zHomed
    val any = xHomed || yHomed || zHomed
    return MoveRowAvailability(
        homeAll = true,                 // always — escape hatch (owner decision 5)
        homeXY = !xy,
        homeZ = !zHomed,
        touchMove = all,
        xy = xy,
        z = zHomed,                     // owner decision 6: Z gated on Z homed
        microstep = any,
        saveLocation = all,
    )
}
```

- [ ] **Step 4: Run — expect PASS.**

- [ ] **Step 5: Extend `MoveVm` + `MoveHolder`** to expose bounds + travel feed (`maxVelocity`). Add to `MoveVm`:

```kotlin
    val axisMin: List<Double>? = null,
    val axisMax: List<Double>? = null,
    val travelFeedMmMin: Int = 6000,   // maxVelocity (mm/s) * 60; default if unknown
```

In `buildVm`:
```kotlin
        val travelFeed = state.maxVelocity?.let { (it * 60).toInt() }?.coerceAtLeast(1) ?: 6000
        return MoveVm(
            x = x, y = y, z = z,
            xHomed = xHomed, yHomed = yHomed, zHomed = zHomed,
            allHomed = xHomed && yHomed && zHomed,
            axisMin = state.axisMinimum,
            axisMax = state.axisMaximum,
            travelFeedMmMin = travelFeed,
        )
```

(`state.maxVelocity` already exists in `PrinterState`.)

- [ ] **Step 6: Build + run the availability test — expect SUCCESS/PASS.**

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/move/MoveHolder.kt \
        app/src/main/java/works/mees/dinghy/ui/move/MoveAvailability.kt \
        app/src/test/java/works/mees/dinghy/ui/move/MoveAvailabilityTest.kt
git commit -m "feat(move): MoveVm bounds/feed + Field-row availability logic"
```

---

### Task D3: New Move Hub shell (Field list + Overview Focus) + icons + AppShell swap

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` (the new Hub)
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` (5 new icons + `all` append)
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:541` (render new `MoveScreen`)
- Modify: `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` consumers as needed

- [ ] **Step 1: Add the five owner-approved icons** to `DinghyIcons.kt` and append each to `all`:

```kotlin
    val MoveTouch = DinghyIcon(IconRef.Ligature("my_location"), alternate = "move_touch")
    val MoveXY = DinghyIcon(IconRef.Ligature("control_camera"), alternate = "move_xy")
    val MoveZ = DinghyIcon(IconRef.Ligature("swap_vert"), alternate = "move_z")
    val SavedLocation = DinghyIcon(IconRef.Ligature("bookmark"), alternate = "saved_location")
    val SaveLocation = DinghyIcon(IconRef.Ligature("bookmark_add"), alternate = "save_location")
```

(All five are unique ligatures → no `DinghyIconsTest` edit needed. Homing rows reuse `HomeStateUnhomed`; Microstep reuses `FineTune`.)

- [ ] **Step 2: Verify the icon drift test stays green**

Run host test for `designsystem.icons.DinghyIconsTest`. Expected: PASS.

- [ ] **Step 3: Write the new Hub `MoveScreen.kt`**

Structure (uses the Fine-Tune Hub pattern verbatim — `BoxWithConstraints` → `rememberUnitGrid` → `ScreenScaffold(focus, field)` with screen-local `var mode by remember { mutableStateOf<MoveMode>(MoveMode.Overview) }`):

```kotlin
package works.mees.dinghy.ui.move

// imports: Compose runtime/foundation/layout, ScreenScaffold, FocusFrame, ListRow, ListRowLabel,
// ListRowIcon, FootButtonBar, OutlinedControl, Intent, IncrementPicker, Scrubber, ConfirmGuard,
// BedMapView, DinghyIcons, LocalTokens, fsSp, Geist/GeistMono, rememberUnitGrid, CommandRegistry,
// MoveToArgs, JogArgs, HomeAxisArgs, dispatch, AppContainer, kotlinx immutable list.

sealed interface MoveMode {
    data object Overview : MoveMode
    data object TouchMove : MoveMode
    data object XY : MoveMode
    data object Z : MoveMode
    data object Microstep : MoveMode
    data class Bookmark(val name: String) : MoveMode
    data object SaveDialog : MoveMode
}

@Composable
fun MoveScreen(
    container: AppContainer,
    holder: MoveHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val vm by holder.vm.collectAsStateWithLifecycle()
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val savedLocations by container.savedLocations.collectAsStateWithLifecycle()
    val inFlight by /* container in-flight set, same source as OldMoveScreen */ ...

    MoveHubContent(
        vm = vm,
        savedLocations = savedLocations,
        isPrinting = printerState.isPrinting,   // use the same predicate OldMoveScreen used
        inFlight = inFlight,
        onMoveTo = { x, y, z -> dispatcher?.dispatch(CommandRegistry.moveTo, MoveToArgs(x, y, z, vm.travelFeedMmMin, vm.axisMin, vm.axisMax)) },
        onJog = { axis, mm -> dispatcher?.dispatch(CommandRegistry.jog, JogArgs(axis, mm, vm.travelFeedMmMin)) },
        onHomeAll = { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
        onHomeXY = { dispatcher?.dispatch(CommandRegistry.homeXY, Unit) },
        onHomeAxis = { axis -> dispatcher?.dispatch(CommandRegistry.homeAxis, HomeAxisArgs(axis)) },
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onSaveLocation = { loc -> container.saveLocation(loc) },
        onDeleteLocation = { name -> container.deleteLocation(name) },
        onBack = onBack,
        modifier = modifier,
    )
}
```

`MoveHubContent` (stateless, preview-friendly) holds `var mode`, builds the Field `ListRow`s from `moveRowAvailability(...)` + `savedLocations`, and renders Focus content via `when (mode)`. The Overview/each sub-mode are filled in Phase E; in THIS task ship only:
- The Field list with all rows (homing fires commands; selectable rows set `mode`; Save Location sets `SaveDialog`; each bookmark sets `Bookmark(name)`).
- The Focus = `FocusFrame` rendering, for now, `MoveMode.Overview` → a `BedMapView` (read-only: `current = vm.x to vm.y` when homed, no target) or a "Home the printer" hint when `axisMax == null` / nothing homed. All other modes can render a placeholder `Text("…")` inside the FocusFrame — Phase E replaces these.
- FocusFrame header: title + icon = the selected row's title/icon; Overview uses `DinghyIcons.LauncherMove` + the Move label. Pass `isPrinting`, `onEmergencyStop`, `onPanic = onEmergencyStop` (header e-stop law).
- A FootButtonBar with a Back control (Intent.Accent, `DinghyIcons.Back`, first) that, when `mode != Overview`, instead returns to Overview; at Overview it calls `onBack`.

> Build the Field rows exactly like FineTuneScreen's `ListRow` calls: `leadingContent = { ListRowIcon(icon = row.icon, uDp = grid.uDp, tint = t.accent) }`, label via `ListRowLabel(row.title)`. Gate non-available rows by simply not emitting them (ListRow has no `enabled` param).

- [ ] **Step 4: Point `AppShell.kt:541` at the new `MoveScreen`** (signature unchanged from Old, so it's a one-line edit back from `OldMoveScreen` → `MoveScreen`).

- [ ] **Step 5: Add an `applyEntryReset` branch (optional)** — if the selected `mode` should reset to Overview on every fresh entry, that already happens because `mode` is `remember{}` screen state (resets on nav). No ShellNavState edit needed.

- [ ] **Step 6: Build, install on flox + moto.** Verify: entering Move shows the new Field list with correct rows for the current homed state; Home All always present; tapping Touch Move/XY/Z/Microstep changes the FocusFrame title+icon; Save Location + each saved bookmark appear when expected; Back steps Overview→nav and sub-mode→Overview.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(move): Move Hub shell — Field list, Overview focus, mode routing, icons"
```

---

# PHASE E — Focus sub-modes

> Each task fills one `when (mode)` branch inside `MoveHubContent`'s FocusFrame. All build + install on flox + moto and are verified on-device (these are visual/interaction surfaces; the spec's verification path is UAT, not unit tests). Re-use existing components exactly; do not invent styling — pull `fsSp`, `GeistMono`, tokens per the Fine-Tune examples.

### Task E1: Touch Move focus

- [ ] **Step 1:** Implement the `MoveMode.TouchMove` branch: a `Column` with the description `Text("Tap to move, hold to refine", GeistMono, fsSp(15f,t.fs))`, a `BedMapView(bed, current = vm.x to vm.y, target = staged, travel = pending, onTapBed = { x,y -> onMoveTo(x,y,null); staged = x to y; pending = true }, onDragBed = { x,y -> staged = x to y }, onDragEnd = { staged?.let { onMoveTo(it.first, it.second, null); pending = true } })`, and a bottom readout `X / Y` (GeistMono tabular, `fmtValue`). `bed = BedExtent(vm.axisMin/Max …)` — render a "waiting for bounds" hint if `axisMin/Max` is null.
- [ ] **Step 2:** Build, install, UAT on flox + moto: tap moves the head; press-drag refines + readout follows; release commits; accent line draws during travel and clears (Task F1 supplies the clear). Confirm via `/server/gcode_store` that an absolute `G1 X.. Y..` wrapped in SAVE/RESTORE was sent ([[dinghy-stream-uat-via-moonraker]]).
- [ ] **Step 3:** Commit `feat(move): Touch Move focus (tap/hold bed map -> moveTo)`.

### Task E2: XY Position focus

- [ ] **Step 1:** Implement `MoveMode.XY`: central `BedMapView` (read-only markers, `target = working`), a horizontal `Scrubber(name="X", range = xMin..xMax, orientation = Horizontal)` along the bottom and a vertical `Scrubber(name="Y", range = yMin..yMax, orientation = Vertical)` up the side. `onValueChange` updates the `working` target dot; `onSettle` calls `onMoveTo(workingX, workingY, null)`. Lay out with the bed map weighted center, Y scrubber in a fixed-width side column, X scrubber in a bottom row.
- [ ] **Step 2:** Build, install, UAT: each scrubber previews the dot live and commits on release; ranges equal axis limits (incl. negative X min if present).
- [ ] **Step 3:** Commit `feat(move): XY Position focus (H+V scrubbers + bed map)`.

### Task E3: Z Position focus

- [ ] **Step 1:** Implement `MoveMode.Z`: a `Row` of two vertical `Scrubber`s — `name="Fine", range = 0f..50f, step = 0.1f` and `name="Full", range = 0f..zMax, step = 1f` (zMax = `vm.axisMax?.getOrNull(2)`). Both seed from `vm.z`; both `onSettle = { onMoveTo(null, null, it.toDouble()) }`. The non-dragged scrubber re-seeds from `vm.z` on the next state tick (already automatic via the `remember(value)` re-seed in Scrubber). Z readout (GeistMono) in the main area.
- [ ] **Step 2:** Build, install, UAT: Fine gives 0.1 resolution to 50mm; Full spans to Zmax; committing one updates the other after the move; gated unreachable unless Z homed (row hidden otherwise).
- [ ] **Step 3:** Commit `feat(move): Z Position focus (fine + full vertical scrubbers)`.

### Task E4: Microstep focus

- [ ] **Step 1:** Implement `MoveMode.Microstep`: an `IncrementPicker(steps = persistentListOf(0.01,0.025,0.1,0.25,1.0,2.5,10.0), activeStep, onSelect, uDp)` plus three labeled ± rows for X/Y/Z. Each ± uses two `OutlinedControl`s (Intent.Accent, labels `"−"`/`"+"`) calling `onJog("X", +activeStep)` / `onJog("X", -activeStep)`. Per-axis ± enabled only if that axis homed (gate the `onClick`; dim via alpha when disabled).
- [ ] **Step 2:** Build, install, UAT: each increment jogs the right distance on the right axis (verify in `/server/gcode_store`: relative `G1 X<step>` wrapped in SAVE/RESTORE).
- [ ] **Step 3:** Commit `feat(move): Microstep focus (increment picker + per-axis steppers)`.

### Task E5: Bookmark focus (Move / Delete)

- [ ] **Step 1:** Implement `MoveMode.Bookmark(name)`: look up the `SavedLocation`; `BedMapView(current = vm.x to vm.y, target = loc.x to loc.y, travel = false)` so both points + the connector show. `FootButtonBar` with **Move** (`OutlinedControl`, Intent.Go, label "Move") → `onMoveTo(loc.x, loc.y, loc.z)` (z null if not saved) then set `pending = true`; and **Delete** (Intent.Danger, "Delete") → set a `confirm` flag.
- [ ] **Step 2:** When `confirm` is set, render `ConfirmGuard(title = "Delete \"$name\"?", message = "Remove this saved location.", confirmLabel = "Delete", destructive = true, onConfirm = { onDeleteLocation(name); mode = MoveMode.Overview; confirm = false }, onCancel = { confirm = false })`.
- [ ] **Step 3:** Build, install, UAT: a saved bookmark opens this view with both markers; Move drives the head there (+Z when saved); Delete confirms then removes the row and returns to Overview.
- [ ] **Step 4:** Commit `feat(move): bookmark focus — Move/Delete with confirm`.

### Task E6: Save Location dialog

- [ ] **Step 1:** Implement `MoveMode.SaveDialog`: a `Column` inside the FocusFrame with a name `OutlinedTextField` (single-line; `keyboardOptions` default text — this is the sanctioned save-name keyboard exception), a checkbox row "Include Z height (Z = ${fmt(vm.z)})" bound to `var includeZ by remember { mutableStateOf(true) }`, and a `FootButtonBar` with **Cancel** (Intent.Accent) → `mode = Overview` and **Save** (Intent.Go, enabled when name non-blank) → `onSaveLocation(SavedLocation(name.trim(), vm.x!!, vm.y!!, if (includeZ) vm.z else null)); mode = Overview`.
- [ ] **Step 2:** Build, install, UAT: Save with a name persists a row that survives app restart (DataStore); the "include Z" toggle controls whether recall moves Z; Cancel discards. Verify XY-only bookmark moves only XY on recall.
- [ ] **Step 3:** Commit `feat(move): Save Location dialog (name + include-Z) -> persistence`.

---

# PHASE F — Travel-line completion, suite, UAT

### Task F1: Travel-line completion (position-epsilon)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/move/TravelState.kt` (pure)
- Test: `app/src/test/java/works/mees/dinghy/ui/move/TravelStateTest.kt`
- Modify: `MoveScreen.kt` (consume it for the `travel`/`pending` flags)

- [x] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.ui.move

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelStateTest {
    @Test fun pending_whileFarFromTarget() {
        assertTrue(travelPending(curX = 0.0, curY = 0.0, tgtX = 100.0, tgtY = 0.0, epsilon = 0.5))
    }
    @Test fun complete_whenWithinEpsilon_onBothAxes() {
        assertFalse(travelPending(curX = 99.9, curY = 50.05, tgtX = 100.0, tgtY = 50.0, epsilon = 0.5))
    }
    @Test fun pending_whenOneAxisStillFar() {
        assertTrue(travelPending(curX = 100.0, curY = 10.0, tgtX = 100.0, tgtY = 50.0, epsilon = 0.5))
    }
}
```

- [x] **Step 2: Run — expect FAIL. Step 3: Implement**

```kotlin
package works.mees.dinghy.ui.move

import kotlin.math.abs

/** True while the live position is still further than [epsilon] from the target on either axis. */
fun travelPending(curX: Double, curY: Double, tgtX: Double, tgtY: Double, epsilon: Double = 0.5): Boolean =
    abs(curX - tgtX) > epsilon || abs(curY - tgtY) > epsilon
```

- [x] **Step 4: Run — expect PASS.**

- [x] **Step 5: Wire into `MoveScreen.kt`:** when a move is committed, store the target; on each `vm` tick recompute `travel = target != null && travelPending(vm.x, vm.y, target.x, target.y)`; clear `target`/`travel` when it returns false. Pass `travel` to `BedMapView`.

- [x] **Step 6: Build, install, UAT:** the accent line shows during a Touch-Move/bookmark move and disappears when the head arrives.

- [x] **Step 7: Commit** `feat(move): position-epsilon travel-line completion`.

### Task F2: Full host suite + release build

- [ ] **Step 1:** Run the whole host unit suite:
  ```bash
  /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
  ```
  Expected: all green (incl. `CommandCatalogDriftTest`, `DinghyIconsTest`, all new Move tests).
- [ ] **Step 2:** Release assemble:
  ```bash
  /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleRelease --no-daemon" 2>&1 | tr -d '\r'
  ```
  Expected: BUILD SUCCESSFUL.
- [ ] **Step 3:** Commit any lint/rule fixes surfaced; otherwise nothing to commit.

### Task F3: On-device UAT sweep (flox + moto)

- [ ] **Step 1:** Force-rebuild + install both ABI slices ([[dinghy-stale-apk-uat-gate]], [[dinghy-test-devices]]). Confirm APK mtime is after the last commit.
- [ ] **Step 2:** Owner-driven UAT checklist (both devices, portrait + landscape):
  - Homing: Home All (always), Home XY, Home Z appear/fire correctly; rows update as axes home.
  - Touch Move: tap-go, hold-drag-refine-release-go, travel line draws + clears.
  - XY: horizontal X + vertical Y scrubbers preview + commit; negative-X-min printer maps correctly.
  - Z: Fine (0–50) + Full (0–Zmax) commit; cross-update.
  - Microstep: each increment on X/Y/Z.
  - Save → recall (Move) → Delete a bookmark; with-Z and XY-only; persistence across app restart.
  - Rotation in each sub-mode; e-stop header morph while printing.
- [ ] **Step 3:** File any defects as follow-up tasks; when clean, the redesign is done.

---

## Self-review notes (author)

- **Spec coverage:** every spec section maps to a task — axis limits (A1), moveTo (A2/A3), persistence (A4/A5), vertical scrubber (B), bed map (C), Hub + field list + Overview (D3), the six sub-modes (E1–E6), travel-line (F1), testing (F2/F3), OldMove rename (D1). Icons (D3 step 1).
- **No invented glyphs:** only the five owner-approved ligatures; the `my_location`-on-canvas marker is explicitly deferred-with-ask, dot used by default.
- **Type consistency:** `MoveToArgs(x,y,z,feedMmMin,minBounds,maxBounds)` is identical at the builder (A2), the args class (A3), and the call site (D3/E1). `moveRowAvailability(x,y,z)` field names match the test and the Field rows.
- **Known traps wired in:** D-10 drift rows (A3), write-scope intents (A5), stale-APK + dual-device UAT (F3), ListRow-has-no-enabled (D3/E4 gate via emission/alpha).
