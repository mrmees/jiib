# Extrude Screen Rework — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Extrude screen on the Focus/Field grammar as a filament-handling hub: a fixed feed Focus (extrude/retract + distance ±stepper + capped speed scrubber + nozzle readout) over an action Field (live runout-sensor toggles, screen-scoped bare-run filament macros, inline nozzle-only preheat chips, spool link), with a Back / Cooldown / Macro-settings foot bar.

**Architecture:** Mirror the existing Outputs live-state pipeline for filament sensors (subscribe → reducer → state map), reuse canonical component classes (`StepperRow`, `Scrubber`, `ToggleRow`, `FocusFrame`, `FootButtonBar`), reuse existing command specs (`extrude`, `setHeater`, `cooldown`, `loadFilament`/`unloadFilament`) plus one new registered command (`SET_FILAMENT_SENSOR`), and add one new DataStore-backed pin set (`ExtrudeMacroPrefs`). No new icons (uses registered `Decrease`/`Increase`).

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, DataStore (Preferences), OkHttp/Moonraker JSON-RPC, JUnit/`runTest`.

**Reference spec:** `docs/superpowers/specs/2026-06-16-extrude-screen-rework-design.md`

**Build/test commands (WSL → Windows-side):**
- Unit tests: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests '<FQCN>' --no-daemon" | tr -d '\r'`
- Full debug build: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
- The process exit code is authoritative. Gradle may mark `assembleDebug` UP-TO-DATE — when installing for UAT, force `--rerun-tasks` and verify APK mtime (memory: stale-APK UAT gate).

---

## File Structure

**Create:**
- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeMacroPrefs.kt` — DataStore-backed Set<String> of extrude-scoped pinned macro names.
- `app/src/test/java/works/mees/dinghy/ui/extrude/ExtrudeMacroPrefsTest.kt`
- `app/src/test/java/works/mees/dinghy/ui/extrude/ExtrudeSensorReducerTest.kt`
- `app/src/test/java/works/mees/dinghy/command/SetFilamentSensorCommandTest.kt`

**Modify:**
- `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt` — add `setFilamentSensor` builder + `MAX_EXTRUDE_ONLY_VELOCITY_FALLBACK`.
- `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` — add `SetFilamentSensorArgs` + `setFilamentSensor` spec + register in `all`.
- `docs/commands/catalog.json` — flip `KGC-SET_FILAMENT_SENSOR` to `registered` + real params.
- `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` — add `filamentSensors` map + `FilamentSensorState`.
- `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt` — add filament-sensor reduce arm.
- `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` — add `maxExtrudeVelocity` StateFlow + setter.
- `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` — subscribe filament-sensor families (package `works.mees.dinghy.state`, **not** `net`; `deriveSubscribeSet` is here ~line 82).
- `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` — parse `max_extrude_only_velocity`; reset one-shots on config-failure path (~line 619).
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — wire `ExtrudeMacroPrefs` (11th DataStore, prefs, StateFlow, intent method).
- `app/src/main/java/works/mees/dinghy/DinghyApp.kt` — the 10 DataStores are **created manually here (~line 42) and passed to `AppContainer(...)` (~line 127)**; add the 11th here. There is NO extension-property file.
- `app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt` (~line 47) and `app/src/test/java/works/mees/dinghy/theme/ThemeOverrideTest.kt` (~line 53) — direct `AppContainer(...)` call sites that MUST be updated for the new ctor param.
- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeHolder.kt` — expand `ExtrudeVm` (sensor rows, velocity, pinned + all macros).
- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt` — Focus + Field rebuild, foot bar, new modes.
- `app/src/main/java/works/mees/dinghy/preview/ExtrudePreviews.kt` — preview fixtures (lives in `preview/`, **not** `ui/extrude/`).
- The `ExtrudeHolder` + `ExtrudeScreen` call site (locate with `grep -rn "ExtrudeHolder(" app/src/main`).
- `app/src/main/res/values/strings.xml` — new string resources.

> **Decomposition note:** Tasks 1–4 are pure backend/plumbing (independently testable, no UI). Task 5 is the holder seam. Tasks 6–11 are UI. Each task ends green + committed.

---

## Task 1: `max_extrude_only_velocity` plumbing

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt`
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt`
- Modify: `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:555-561`

- [ ] **Step 1: Add the fallback constant.** In `PrinterCommands.kt`, near the other extrude bounds (`MAX_EXTRUDE_MM`):

```kotlin
/** Fallback feed ceiling (mm/s) for the Extrude speed scrubber when the printer does not report
 *  `max_extrude_only_velocity`. Conservative — a 1.75 mm hotend grinds far below 50 mm/s. */
const val MAX_EXTRUDE_ONLY_VELOCITY_FALLBACK = 15
```

- [ ] **Step 2: Add the StateFlow + setter to `PrinterStateStore.kt`.** Mirror the existing `maxExtrudeDistance` one-shot StateFlow exactly (find it with `grep -n "maxExtrudeDistance" PrinterStateStore.kt`):

```kotlin
private val _maxExtrudeVelocity = MutableStateFlow<Float?>(null)
/** One-shot handshake read of `extruder.max_extrude_only_velocity` (mm/s). Null = unknown. */
val maxExtrudeVelocity: StateFlow<Float?> = _maxExtrudeVelocity.asStateFlow()
fun setMaxExtrudeVelocity(v: Float?) { _maxExtrudeVelocity.value = v }
```

> **Reset note (Codex):** there is currently NO `setMaxExtrudeDistance(null)` reset call. The config-failure path in `MoonrakerSession.kt` (~line 619) clears outputs/limits/macros but not the min/max-extrude one-shots. Add `store.setMaxExtrudeVelocity(null)` beside those existing clears (and, for consistency, `store.setMaxExtrudeDistance(null)` + `store.setMinExtrudeTemp(null)` if not already there). On a successful handshake the parse in Step 3 overwrites the value, so the only gap is a connect-then-config-fail sequence.

- [ ] **Step 3: Parse it in `MoonrakerSession.kt`** beside the existing extruder-config reads (line ~560):

```kotlin
store.setMaxExtrudeDistance(extruderCfg?.floatOrNullAt("max_extrude_only_distance"))
store.setMaxExtrudeVelocity(extruderCfg?.floatOrNullAt("max_extrude_only_velocity"))
```

- [ ] **Step 4: Build to verify compilation.**

Run: `... "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add -A && git commit -m "feat(extrude): parse max_extrude_only_velocity for speed-scrubber cap

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `SET_FILAMENT_SENSOR` command (builder + registry + catalog)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt`
- Modify: `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt`
- Modify: `docs/commands/catalog.json`
- Test: `app/src/test/java/works/mees/dinghy/command/SetFilamentSensorCommandTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package works.mees.dinghy.command

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SetFilamentSensorCommandTest {

    @Test
    fun builderFormatsEnableDisable() {
        assertEquals("SET_FILAMENT_SENSOR SENSOR=Runout ENABLE=1", PrinterCommands.setFilamentSensor("Runout", true))
        assertEquals("SET_FILAMENT_SENSOR SENSOR=Runout ENABLE=0", PrinterCommands.setFilamentSensor("Runout", false))
    }

    @Test
    fun registryWrapsBuilderByteIdentically() {
        val spec = CommandRegistry.setFilamentSensor
        val script = spec.params(SetFilamentSensorArgs("encoder_sensor", false))
            ?.let { (it as kotlinx.serialization.json.JsonObject)["script"]?.jsonPrimitive?.content }
        assertEquals(PrinterCommands.setFilamentSensor("encoder_sensor", false), script)
    }

    @Test
    fun dispatchKeyIsPerSensor() {
        assertEquals("set_filament_sensor_encoder_sensor",
            CommandRegistry.setFilamentSensor.dispatchKey(SetFilamentSensorArgs("encoder_sensor", true)))
    }

    @Test
    fun registeredInAll() {
        assert(CommandRegistry.all.contains(CommandRegistry.setFilamentSensor))
    }
}
```

- [ ] **Step 2: Run it — expect FAIL** (unresolved `setFilamentSensor` / `SetFilamentSensorArgs`).

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.command.SetFilamentSensorCommandTest' --no-daemon" | tr -d '\r'`
Expected: compile FAIL.

- [ ] **Step 3: Add the builder to `PrinterCommands.kt`** (near `setHeater`). The sensor name comes from discovered objects (never free-text); guard against blank and forbidden chars defensively:

```kotlin
/** `SET_FILAMENT_SENSOR SENSOR=<name> ENABLE=0|1`. [sensor] is the BARE Klipper section name
 *  (from the discovered object list — never free-text). Blank/illegal names are rejected, not escaped. */
fun setFilamentSensor(sensor: String, enable: Boolean): String {
    require(sensor.isNotBlank() && sensor.none { it.isWhitespace() || it == ';' || it == '"' }) {
        "illegal filament sensor name"
    }
    return "SET_FILAMENT_SENSOR SENSOR=$sensor ENABLE=${if (enable) 1 else 0}"
}
```

- [ ] **Step 4: Add the Args + spec to `CommandRegistry.kt`.** Args near the other `*Args` data classes:

```kotlin
data class SetFilamentSensorArgs(val sensor: String, val enable: Boolean)
```

Spec near `cooldown` (use the `gcode(...)` factory; availability gates on the runtime presence of the command):

```kotlin
val setFilamentSensor: CommandSpec<SetFilamentSensorArgs> = gcode(
    catalogId = "KGC-SET_FILAMENT_SENSOR",
    key = { args -> "set_filament_sensor_${args.sensor}" },
    gcode = { args -> PrinterCommands.setFilamentSensor(args.sensor, args.enable) },
    availability = AvailabilityPredicate.GcodeCommandPresent("SET_FILAMENT_SENSOR"),
)
```

> If `AvailabilityPredicate.GcodeCommandPresent` does not exist, check the available predicate subclasses (`grep -n "sealed.*AvailabilityPredicate\|object\|data class" AvailabilityPredicate*.kt`) and use the one matching the catalog's `gcode_command_present` predicate type; if none exists, use `AvailabilityPredicate.Always` (the screen already gates the row on discovered sensors).

Then add `setFilamentSensor,` to the `CommandRegistry.all` list.

- [ ] **Step 5: Flip the catalog entry.** In `docs/commands/catalog.json`, find `"id": "KGC-SET_FILAMENT_SENSOR"`. Set `params` and `runtime_registry`:

```json
  "params": ["SENSOR", "ENABLE"],
  "key_params": ["SENSOR"],
```
```json
  "runtime_registry": {
    "status": "registered",
    "registered": true,
    "notes": "Registered runtime CommandSpec (Extrude rework) — runout-sensor enable/disable from the Extrude screen."
  },
```

- [ ] **Step 5b: Add the `printer-matrix.json` command-availability row (REQUIRED — Codex blocker).** `CommandCatalogDriftTest` (`app/src/test/java/works/mees/dinghy/command/CommandCatalogDriftTest.kt:131`) requires a `command_availability` row for every `catalogId` registered in `CommandRegistry.all`. The matrix currently has only `SET_FILAMENT_SENSOR` *help evidence* (`printer-matrix.json:427`, `:1042`) — NOT a command-availability row. Open `docs/commands/printer-matrix.json`, find the `command_availability` section, and add a `KGC-SET_FILAMENT_SENSOR` row mirroring the shape of an existing registered gcode row (copy e.g. the `KGC-TURN_OFF_HEATERS` row's structure and per-printer availability — both captured printers (`ender5plus`, `ender3pro`) report a `filament_*_sensor` object, so mark available accordingly; match exactly the field names/shape the drift test reads).

- [ ] **Step 6: Run the command test + the drift test — expect PASS.**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.command.SetFilamentSensorCommandTest' --tests 'works.mees.dinghy.command.CommandCatalogDriftTest' --no-daemon" | tr -d '\r'`
Expected: PASS. If drift fails, read its message — it names the exact missing/mismatched field; fix that field in `catalog.json` and re-run.

- [ ] **Step 7: Commit.**

```bash
git add -A && git commit -m "feat(command): register SET_FILAMENT_SENSOR (runout enable/disable)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Filament-sensor live state (reducer + subscribe)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterState.kt`
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt`
- Modify: `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` (package `state`, **not** `net`)
- Test: `app/src/test/java/works/mees/dinghy/ui/extrude/ExtrudeSensorReducerTest.kt`

- [ ] **Step 1: Write the failing reducer test.** Mirror `PrinterStateReducerOutputsTest` (use its `reduceSnapshot`/`reduceDiff`/`status("…")` helpers — copy the imports/helpers from that file):

```kotlin
package works.mees.dinghy.ui.extrude

import org.junit.Assert.*
import org.junit.Test
import works.mees.dinghy.state.reduceSnapshot   // adjust import to the actual helper location

class ExtrudeSensorReducerTest {

    @Test
    fun switchSensorReducesEnabledAndDetected() {
        val s = reduceSnapshot(status(
            """{"filament_switch_sensor Runout":{"enabled":true,"filament_detected":false}}"""))
        val sensor = s.filamentSensors["filament_switch_sensor Runout"]!!
        assertTrue(sensor.enabled)
        assertEquals(false, sensor.filamentDetected)
    }

    @Test
    fun partialDiffRetainsPriorFields() {
        val seeded = reduceSnapshot(status(
            """{"filament_motion_sensor Encoder":{"enabled":true,"filament_detected":true}}"""))
        val merged = reduceDiff(seeded, status(
            """{"filament_motion_sensor Encoder":{"enabled":false}}"""))
        val sensor = merged.filamentSensors["filament_motion_sensor Encoder"]!!
        assertFalse(sensor.enabled)                  // present field updates
        assertEquals(true, sensor.filamentDetected)  // absent field retained
    }
}
```

(Copy `status(...)`, `reduceSnapshot`, `reduceDiff` helper definitions verbatim from `PrinterStateReducerOutputsTest.kt`.)

- [ ] **Step 2: Run — expect FAIL** (no `filamentSensors`).

- [ ] **Step 3: Add state to `PrinterState.kt`** beside `outputs`:

```kotlin
val filamentSensors: ImmutableMap<String, FilamentSensorState> = persistentMapOf(),
```
And the value type (near `OutputLiveValue`):
```kotlin
@Immutable
data class FilamentSensorState(
    /** `enabled` — whether Klipper is currently watching this runout sensor. */
    val enabled: Boolean = false,
    /** `filament_detected` — true = filament present at the sensor (null = not reported). */
    val filamentDetected: Boolean? = null,
)
```

- [ ] **Step 4: Add the reduce arm to `PrinterStateReducer.kt`** (mirror the `outputUpdates` block, UPDATE-ON-PRESENT merge). Place it beside the outputs arm:

```kotlin
val sensorUpdates = mutableMapOf<String, FilamentSensorState>()
for ((key, value) in status) {
    if (!isFilamentSensorObject(key)) continue
    val obj = (value as? JsonObject) ?: continue
    val prev = s.filamentSensors[key] ?: FilamentSensorState()
    sensorUpdates[key] = prev.copy(
        enabled = obj.booleanOrNull("enabled") ?: prev.enabled,
        filamentDetected = obj.booleanOrNull("filament_detected") ?: prev.filamentDetected,
    )
}
if (sensorUpdates.isNotEmpty()) {
    s = s.copy(filamentSensors = (s.filamentSensors + sensorUpdates).toImmutableMap())
}
```
Add the predicate near `isReducedOutputObject`:
```kotlin
private fun isFilamentSensorObject(name: String): Boolean =
    name.substringBefore(' ') in setOf("filament_switch_sensor", "filament_motion_sensor")
```
> **Use the EXISTING `booleanOrNull(...)` helper** already in `PrinterStateReducer.kt` (~line 370) — do NOT add a new `booleanOrNullAt`. Confirm its exact call shape (it may be `obj.booleanOrNull("key")` or a free function `booleanOrNull(obj, "key")`); match the local usage in the outputs/heaters arms.

- [ ] **Step 5: Subscribe the families in `DeriveCapabilities.kt`.** In `deriveSubscribeSet`, beside the output-whitelist check (line ~108), also admit filament-sensor families:

```kotlin
val family = name.substringBefore(' ')
if (family in works.mees.dinghy.outputs.OutputsGate.WHITELIST ||
    family in setOf("filament_switch_sensor", "filament_motion_sensor")
) {
    subset += name
}
```
(Adapt to the exact local structure — the goal: any detected `filament_*_sensor` object name is added to the subscribe subset.)

- [ ] **Step 6: Add a subscribe-set test** in the existing `DeriveCapabilities`/subscribe test file (grep `deriveSubscribeSet` in `src/test`):

```kotlin
@Test
fun subscribeIncludesFilamentSensors() {
    val subset = deriveSubscribeSet(listOf("toolhead", "filament_switch_sensor Runout", "extruder"))
    assertTrue("filament_switch_sensor Runout" in subset)
}
```

- [ ] **Step 7: Run all three test classes — expect PASS.**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.extrude.ExtrudeSensorReducerTest' --tests '*DeriveCapabilities*' --no-daemon" | tr -d '\r'`

- [ ] **Step 8: Commit.**

```bash
git add -A && git commit -m "feat(state): subscribe + reduce filament_switch/motion_sensor live state

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: `ExtrudeMacroPrefs` (screen-scoped pin set)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeMacroPrefs.kt`
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`
- Modify: the DataStore extension-property declaration file (grep `savedLocationDataStore`)
- Test: `app/src/test/java/works/mees/dinghy/ui/extrude/ExtrudeMacroPrefsTest.kt`

- [ ] **Step 1: Write the failing test** (mirror `MacroPrefsTest.kt` verbatim — same `newPrefs()`/`settle()` harness):

```kotlin
package works.mees.dinghy.ui.extrude
// ... copy imports + tmpFiles/scopes/tearDown/newPrefs/settle from MacroPrefsTest ...

class ExtrudeMacroPrefsTest {
    // (paste the harness)

    @Test
    fun emptyByDefault() = runTest {
        val (prefs, io) = newPrefs()
        withContext(io.coroutineContext) { assertEquals(emptySet<String>(), prefs.pins.first()) }
    }

    @Test
    fun toggleRoundTrips() = runTest {
        val (prefs, io) = newPrefs()
        withContext(io.coroutineContext) {
            prefs.togglePin("PURGE"); settle()
            assertTrue(prefs.pins.first().contains("PURGE"))
            prefs.togglePin("PURGE"); settle()
            assertFalse(prefs.pins.first().contains("PURGE"))
        }
    }
}
```
(In `newPrefs()` return `ExtrudeMacroPrefs(dataStore)`.)

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Create `ExtrudeMacroPrefs.kt`** (copy `MacroPrefs` structure, single string-set key):

```kotlin
package works.mees.dinghy.ui.extrude

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Extrude-screen-scoped pinned filament macro NAMEs — independent of the global Macros bookmarks. */
class ExtrudeMacroPrefs(private val dataStore: DataStore<Preferences>) {
    val pins: Flow<Set<String>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[KEY_PINS] ?: emptySet() }

    suspend fun togglePin(name: String) {
        dataStore.edit { prefs ->
            val cur = prefs[KEY_PINS] ?: emptySet()
            prefs[KEY_PINS] = if (name in cur) cur - name else cur + name
        }
    }

    companion object {
        private val KEY_PINS = stringSetPreferencesKey("extrude_macro_pins")
    }
}
```

- [ ] **Step 4: Run the prefs test — expect PASS.**

- [ ] **Step 5: Wire `AppContainer` (Codex-corrected — there is NO extension-property file).** The 10 DataStores are created manually in `DinghyApp.kt` (~line 42) and handed to `AppContainer(...)` (~line 127). Do all of:
  - **(a)** In `DinghyApp.kt`, create an 11th store next to the others (copy a `PreferenceDataStoreFactory.create(...)` / `preferencesDataStore("…")` line for the existing `savedLocation` store, file name `extrude_macros`), and pass it into the `AppContainer(...)` constructor call at ~line 127.
  - **(b)** Add the constructor param `extrudeMacroDataStore: DataStore<Preferences>,` to `AppContainer`.
  - **(c)** Update the two DIRECT test call sites that construct `AppContainer(...)`: `AppContainerTest.kt` (~line 47) and `ThemeOverrideTest.kt` (~line 53) — pass a temp/in-memory store like they do for the other params (copy how they build `savedLocationDataStore` in those tests).
  - **(d)** In `AppContainer`, add:

```kotlin
val extrudeMacroPrefs: ExtrudeMacroPrefs = ExtrudeMacroPrefs(extrudeMacroDataStore)
val extrudeMacroPins: StateFlow<Set<String>> =
    extrudeMacroPrefs.pins.stateIn(stateScope, SharingStarted.Eagerly, emptySet())
fun toggleExtrudeMacroPin(name: String) { writeScope.launch { extrudeMacroPrefs.togglePin(name) } }
```

- [ ] **Step 6: Build to verify wiring compiles.**

Run: `... "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`

- [ ] **Step 7: Commit.**

```bash
git add -A && git commit -m "feat(extrude): ExtrudeMacroPrefs screen-scoped macro pin set + AppContainer wiring

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Expand `ExtrudeVm` + `ExtrudeHolder`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeHolder.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/extrude/ExtrudeHolderTest.kt` (extend if present; else create mirroring an existing holder test)

- [ ] **Step 1: Add the new VM types + fields.** Append to `ExtrudeHolder.kt`:

```kotlin
/** One discovered runout sensor surfaced to the Field as a live ToggleRow. */
data class SensorRowVm(
    val objectKey: String,       // FULL "filament_switch_sensor Runout" — live-state + busy key
    val sensorName: String,      // BARE "Runout" — the SET_FILAMENT_SENSOR SENSOR= arg
    val prettyName: String,      // "Runout" → display label
    val enabled: Boolean,        // live `enabled`
    val filamentDetected: Boolean?, // live `filament_detected` (sublabel)
)

/** One filament macro row (Load/Unload auto-rows + user pins). Runs BARE on tap. */
data class ExtrudeMacroRowVm(
    val name: String,            // canonical (case-recovered) macro name
    val description: String?,    // Klipper `description` if captured
)
```

Add to `ExtrudeVm`:
```kotlin
val sensors: List<SensorRowVm> = emptyList(),
val maxExtrudeVelocity: Int = works.mees.dinghy.command.PrinterCommands.MAX_EXTRUDE_ONLY_VELOCITY_FALLBACK,
val pinnedMacros: List<ExtrudeMacroRowVm> = emptyList(),   // Field rows (excludes load/unload — those use dedicated specs)
val allMacros: List<ExtrudeMacroRowVm> = emptyList(),      // Macro-settings takeover list (visible, non-underscore)
val pinnedNames: Set<String> = emptySet(),                 // for the settings toggle state
```

- [ ] **Step 2: Add the pins flow to the holder constructor + combine.** Change the constructor:

```kotlin
class ExtrudeHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
    private val pinnedExtrudeMacros: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
) {
```
Replace the `combine(...)` (4-arg max → use the list `combine`):
```kotlin
scope.launch {
    combine(
        store.printerState,
        store.minExtrudeTemp,
        store.maxExtrudeDistance,
        store.maxExtrudeVelocity,
        pinnedExtrudeMacros,
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        buildVm(
            state = arr[0] as PrinterState,
            caps = store.capabilities.value,
            minTemp = arr[1] as Float?,
            maxDist = arr[2] as Float?,
            maxVel = arr[3] as Float?,
            pins = arr[4] as Set<String>,
        )
    }.collect { _vm.value = it }
}
```

- [ ] **Step 3: Extend `buildVm`** to populate the new fields. Add params `maxVel: Float?`, `pins: Set<String>` and compute:

```kotlin
// Runout sensors — derive bare/pretty from the FULL object key (no separate descriptor track).
val sensors = state.filamentSensors.entries
    .sortedBy { it.key }
    .map { (objectKey, live) ->
        val bare = objectKey.substringAfter(' ')
        SensorRowVm(
            objectKey = objectKey,
            sensorName = bare,
            prettyName = bare.replace('_', ' ').replace('-', ' ').trim()
                .split(' ').filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar(Char::uppercase) },
            enabled = live.enabled,
            filamentDetected = live.filamentDetected,
        )
    }

// Macro rows. Resolve canonical names case-insensitively from caps.macros.
// v1: description = null (rows only need the name to RUN bare — owner said no fancy macro UI).
fun canonical(name: String): String? = caps.macros.firstOrNull { it.equals(name, ignoreCase = true) }
val visible = caps.macros.filter { !it.startsWith("_") }
    .map { ExtrudeMacroRowVm(it, description = null) }
val pinnedRows = pins.mapNotNull { canonical(it) }
    .filter { !it.equals(CommandMap.loadFilament.macro, ignoreCase = true) &&
              !it.equals(CommandMap.unloadFilament.macro, ignoreCase = true) }
    .map { ExtrudeMacroRowVm(it, description = null) }

// Cap the displayed/scrubbable velocity at BOTH the printer's reported max AND the builder ceiling
// (PrinterCommands.extrude clamps feed to MAX_EXTRUDE_FEED_MM_MIN/60 mm/s — never advertise above what
// the dispatch can actually send, else the scrubber shows a value that gets silently clamped).
val builderCeiling = PrinterCommands.MAX_EXTRUDE_FEED_MM_MIN / 60
val velCap = (maxVel?.toInt()?.coerceAtLeast(1)
    ?: PrinterCommands.MAX_EXTRUDE_ONLY_VELOCITY_FALLBACK).coerceAtMost(builderCeiling)

return ExtrudeVm(
    // ... existing fields ...
    sensors = sensors,
    maxExtrudeVelocity = velCap,
    pinnedMacros = pinnedRows,
    allMacros = visible,
    pinnedNames = pins,
)
```

> **Descriptions deferred (Codex):** `store.macroDescription(...)` does NOT exist — the accessor is `store.macroDescriptions: StateFlow<Map<String,String>>` (`PrinterStateStore.kt:142`). v1 ships `description = null` (the bare-run path doesn't need it), which keeps the `combine` at 5 typed flows. If descriptions are wanted later, add `store.macroDescriptions` as a 6th flow using the array-form `combine` (or nest), mirroring the typed 5-flow `combine` in `MacroHolder.kt:92`.

- [ ] **Step 4: Add/extend the holder test.** Construct the holder against a `PrinterStateStore` seeded with a filament sensor + a couple macros + a pin flow, assert:
  - `vm.sensors` has one row with `sensorName == "Runout"`, `prettyName == "Runout"`, `enabled == true`.
  - `vm.maxExtrudeVelocity` falls back to 15 when the store reports null, and reflects the store value when set.
  - A pinned macro name shows in `vm.pinnedMacros`; Load/Unload names are excluded from `pinnedMacros`.

Use the seeding pattern from the existing `ExtrudeHolderTest` (grep it) or the nearest holder test. Run with `--tests 'works.mees.dinghy.ui.extrude.ExtrudeHolderTest'`. Expect PASS.

- [ ] **Step 5: Commit.**

```bash
git add -A && git commit -m "feat(extrude): expand ExtrudeVm/Holder (sensors, velocity cap, pinned+all macros)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Focus rebuild — feed surface

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt`

Replace `FocusGrid`'s right-hand numeric-IME column with a distance ±stepper + capped speed scrubber; keep the left Extrude/Retract column; add a small nozzle readout. Distance presets stay `DISTANCE_PRESETS = listOf(1.0, 5.0, 25.0, 50.0)`.

- [ ] **Step 1: Add a `DistanceStepper` composable** (uses canonical `StepperRow` with a `center` value cell; cycles enabled presets, clamps at ends, skips presets over `maxDistance`):

```kotlin
@Composable
private fun DistanceStepper(
    selected: Double,
    maxDistance: Float?,
    onSelect: (Double) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val enabledPresets = DISTANCE_PRESETS.filter { maxDistance == null || it <= maxDistance }
        .ifEmpty { listOf(DISTANCE_PRESETS.first()) }
    val idx = enabledPresets.indexOf(selected).let { if (it < 0) enabledPresets.indexOfLast { p -> p <= selected }.coerceAtLeast(0) else it }
    StepperRow(
        onDecrement = { onSelect(enabledPresets[(idx - 1).coerceAtLeast(0)]) },
        onIncrement = { onSelect(enabledPresets[(idx + 1).coerceAtMost(enabledPresets.lastIndex)]) },
        uDp = uDp,
        modifier = modifier,
        center = {
            Text(
                text = "${fmtDist(selected)} mm",
                color = t.text,
                style = DinghyType.statValue.toTextStyle(t),
            )
        },
        decreaseContentDescription = stringResource(R.string.extrude_distance_dec),
        increaseContentDescription = stringResource(R.string.extrude_distance_inc),
    )
}
```

- [ ] **Step 2: Add a `SpeedScrubberRow`** (canonical `Scrubber`, range capped to `maxVelocity`):

```kotlin
@Composable
private fun SpeedScrubberRow(
    speed: Int,
    maxVelocity: Int,
    onSettle: (Int) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    Scrubber(
        name = stringResource(R.string.extrude_speed_readout),
        value = speed.toFloat(),
        range = MIN_SPEED_MM_S.toFloat()..maxVelocity.toFloat(),
        step = 1f,
        uDp = uDp,
        onSettle = { onSettle(it.roundToInt().coerceIn(MIN_SPEED_MM_S, maxVelocity)) },
        unit = "mm/s",
        modifier = modifier,
    )
}
```

- [ ] **Step 3: Rewrite `FocusGrid`** to host: Extrude/Retract (left column, unchanged `BigCommand` calls), and a right column of `DistanceStepper` + `SpeedScrubberRow` + a `NozzleReadout`. Thread `uDp` and `maxVelocity` down (add params). Pull `uDp` from the `rememberUnitGrid(...)` already computed in `ExtrudeContent` and pass into `FocusGrid`. Add a `NozzleReadout`:

```kotlin
@Composable
private fun NozzleReadout(temp: Double, target: Double, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.nozzle), contentDescription = null, tint = t.text2,
            modifier = Modifier.size(fsSp(28f, t.fs).dp))
        Text("${temp.roundToInt()} / ${target.roundToInt()}°C", color = t.text,
            style = DinghyType.dataInline.toTextStyle(t))
    }
}
```

Remove `NumericSettingReadout` and the IME `distanceText`/`speedText` machinery (no keyboard in printer controls — UI law). Keep `distance`/`speed` state in `ExtrudeContent`; `onDistanceChange`/`onSpeedChange` clamp as before.

**Add a velocity re-clamp `LaunchedEffect` (Codex blocker — mirror the existing `maxExtrudeDistance` clamp).** In `ExtrudeContent`, beside the `LaunchedEffect(vm.maxExtrudeDistance)` distance clamp, add:

```kotlin
// If the printer's reported max velocity is below the current speed, pull speed down so Extrude
// never dispatches over cap (the scrubber range also caps, but state seeded at DEFAULT_SPEED_MM_S
// could exceed a low reported max before the user touches it).
LaunchedEffect(vm.maxExtrudeVelocity) {
    if (speed > vm.maxExtrudeVelocity) speed = vm.maxExtrudeVelocity
}
```
Also update `MAX_SPEED_MM_S` usage: the speed scrubber's `range` top and `onSpeedChange` clamp must use `vm.maxExtrudeVelocity`, not the static `MAX_SPEED_MM_S`.

- [ ] **Step 4: Build + run the existing Extrude tests** (preview/screen tests, if any). Expect compile success; fix references.

Run: `... "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`

- [ ] **Step 5: Commit.**

```bash
git add -A && git commit -m "feat(extrude): Focus = extrude/retract + distance stepper + capped speed scrubber

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Field rebuild — action list

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt`

Replace the `ExtrudeFieldMode.Main` Field body (the distance/speed/temp/spool selectors — those moved to Focus) with a scrolling action list. Remove `ExtrudeFieldMode.FilamentPresets` (thermal is now inline chips). Add new `ExtrudeContent` params and dispatch callbacks.

- [ ] **Step 1: Extend the `ExtrudeContent` signature** with the new callbacks/data:

```kotlin
onToggleSensor: (objectKey: String, sensorName: String, enable: Boolean) -> Unit,
onRunMacro: (name: String) -> Unit,
onOpenMacroSettings: () -> Unit,
onToggleMacroPin: (name: String) -> Unit,
onCooldown: () -> Unit,
```
(`vm` already carries `sensors`, `pinnedMacros`, `allMacros`, `maxExtrudeVelocity`, `pinnedNames`, `hasLoadMacro`, `hasUnloadMacro`.)

- [ ] **Step 2: Build the Field list** (a single scrolling `Column` with `verticalScroll`, or `LazyColumn`; match what other Field lists use — grep a Field list e.g. `OutputsScreen` for the idiom). Order, top→bottom:

```kotlin
Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
       verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // 1. Runout sensors (hidden entirely when none).
    vm.sensors.forEach { s ->
        ToggleRow(
            label = s.prettyName,
            subLabel = s.filamentDetected?.let {
                stringResource(if (it) R.string.extrude_filament_present else R.string.extrude_filament_absent)
            },
            checked = s.enabled,
            onToggle = { enable -> onToggleSensor(s.objectKey, s.sensorName, enable) },
            uDp = grid.uDp,
            enabled = "set_filament_sensor_${s.sensorName}" !in inFlight,
        )
    }
    // 2. Macros — Load/Unload auto-rows shown ONLY when those macros exist (design: "auto-included
    //    at the top WHEN those macros exist"), then pins. No missing-macro toast for these rows.
    if (vm.hasLoadMacro) MacroActionRow(stringResource(R.string.extrude_load),
        DinghyIcons.ExpandCircleUp, onClick = onLoad)
    if (vm.hasUnloadMacro) MacroActionRow(stringResource(R.string.extrude_unload),
        DinghyIcons.ExpandCircleDown, onClick = onUnload)
    vm.pinnedMacros.forEach { m ->
        MacroActionRow(m.name, DinghyIcons.LauncherMacros,
            onClick = { onRunMacro(m.name) },
            busy = "macro_${m.name}" in inFlight)
    }
    // 3. Thermal preset chips (inline, nozzle-only).
    ThermalChips(activeSpoolDetail = activeSpoolDetail, onSetExtruderTemp = onSetExtruderTemp,
                 uDp = grid.uDp)
    // 4. Spool link.
    SpoolLinkRow(activeSpoolDetail = activeSpoolDetail, onOpenSpool = onOpenSpool, uDp = grid.uDp)
}
```

- [ ] **Step 3: Add the small row composables** (reuse `ListRow` if its signature fits; otherwise a thin local row mirroring `PresetRow`'s outline+click style):
  - `MacroActionRow(label, icon, onClick, busy=false)` — outlined clickable row, leading icon + label; dims when `busy`.
  - `ThermalChips(...)` — a `Row`/`FlowRow` of chips: loaded-spool (if `activeSpoolDetail?.filament?.settingsExtruderTemp != null`) + `PrinterCommands.MATERIAL_PRESETS`. Each chip onClick = `onSetExtruderTemp(preset.nozzle)` (nozzle ONLY). Reuse the `PresetRow` look or a compact chip.
  - `SpoolLinkRow(...)` — a `ListRow`/outlined row with the reactive `SpoolGlyph` + a trailing nav chevron; onClick = `onOpenSpool`.

> Keep `PresetRow`, `FieldButton` only if still referenced; delete dead code (the old DistanceSelector/SpeedSelector/ToolSelector/NumericSettingReadout/FilamentPresets paths) once the Tool selector is relocated (Step 4).

- [ ] **Step 4: Relocate the Tool selector.** The multi-extruder `ToolSelector` (shown when `vm.showToolSelector`) belongs in the Focus now (it changes which extruder Extrude/Retract drives). Render it in the Focus column above the distance stepper when `vm.showToolSelector`. Keep the existing `onSelectTool` wiring.

- [ ] **Step 5: Build.** Expect compile success. Fix references.

- [ ] **Step 6: Commit.**

```bash
git add -A && git commit -m "feat(extrude): Field = runout toggles + macros + nozzle-only thermal chips + spool link

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Macro-settings takeover mode

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt`

- [ ] **Step 1: Add the field mode.**

```kotlin
sealed class ExtrudeFieldMode {
    data object Main : ExtrudeFieldMode()
    data object MacroSettings : ExtrudeFieldMode()
}
```

- [ ] **Step 2: Render the takeover** when `fieldMode == MacroSettings`: a scrolling list of `vm.allMacros`, each a `ToggleRow` (label = macro name, checked = `name in vm.pinnedNames`, onToggle = `onToggleMacroPin(name)`), with a Back foot button returning to `Main`. Empty state: a caption "No macros found" when `vm.allMacros.isEmpty()`.

- [ ] **Step 3: Wire the entry.** The `Main` foot bar Macro-settings button sets `fieldMode = ExtrudeFieldMode.MacroSettings` (via `onOpenMacroSettings` → local state set in `ExtrudeContent`).

- [ ] **Step 4: Build + commit.**

```bash
git add -A && git commit -m "feat(extrude): macro-settings takeover to curate screen-scoped pins

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Foot bar (Back / Cooldown / Macro-settings)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt`

- [ ] **Step 1: Replace the `Main` `FootButtonBar`** (currently Back/Load/Unload) with:

```kotlin
FootButtonBar(uDp = grid.uDp) {
    OutlinedControl(label = "", onClick = onBack, modifier = Modifier.weight(1f),
        intent = Intent.Accent, icon = DinghyIcons.Back)
    OutlinedControl(label = stringResource(R.string.extrude_cooldown), onClick = onCooldown,
        modifier = Modifier.weight(1f), intent = Intent.Warn, icon = DinghyIcons.HideTemps)
    OutlinedControl(label = stringResource(R.string.extrude_macro_settings),
        onClick = onOpenMacroSettings, modifier = Modifier.weight(1f),
        intent = Intent.Accent, icon = DinghyIcons.ManageMacros)
}
```

> **ICONS — owner-decided 2026-06-16 (icon law satisfied, both already registered):** Cooldown = `DinghyIcons.HideTemps` (`mode_heat_off`); Macro-settings = `DinghyIcons.ManageMacros` (`bookmark_manager`). Do NOT substitute.

- [ ] **Step 2:** Confirm the e-stop is NOT added here (FocusFrame already docks it via `isPrinting`/`onEmergencyStop`, unchanged).

- [ ] **Step 3: Build + commit.**

```bash
git add -A && git commit -m "feat(extrude): foot bar Back / Cooldown / Macro-settings

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: Stateful `ExtrudeScreen` wiring + call site

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt`
- Modify: the `ExtrudeHolder`/`ExtrudeScreen` call site (grep `ExtrudeHolder(` and `ExtrudeScreen(` in `app/src/main`)

- [ ] **Step 1: Wire the new dispatches** in the stateful `ExtrudeScreen(container, holder, …)`:

```kotlin
onToggleSensor = { objectKey, sensorName, enable ->
    val key = "set_filament_sensor_$sensorName"
    if (key !in inFlight)
        dispatcher?.dispatch(CommandRegistry.setFilamentSensor, SetFilamentSensorArgs(sensorName, enable))
},
onRunMacro = { name ->
    val key = "macro_$name"
    if (key !in inFlight) dispatcher?.dispatch(
        key = key,
        method = JsonRpcMethods.GCODE_SCRIPT,
        params = PrinterCommands.scriptParams(MacroInvocation.buildRaw(name, "")),
    )
},
onCooldown = { dispatcher?.dispatch(CommandRegistry.cooldown, Unit) },
onToggleMacroPin = { name -> container.toggleExtrudeMacroPin(name) },
```
(`onOpenMacroSettings` is local field-mode state — no dispatcher.)

- [ ] **Step 2: Pass the pins flow into the holder.** At the construction site, change `ExtrudeHolder(scope, store)` → `ExtrudeHolder(scope, store, container.extrudeMacroPins)`. (If the holder is built inside a factory that lacks `container`, thread `container.extrudeMacroPins` through, or construct the holder where `container` is in scope.)

- [ ] **Step 3: Update the stateless preview seam** `ExtrudeScreen(vm, …)` to default the new callbacks to no-ops (so previews still compile).

- [ ] **Step 4: Full build.**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add -A && git commit -m "feat(extrude): wire sensor/macro/cooldown dispatch + pins flow into holder

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: Strings, previews, conformance, full suite, on-device UAT

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/works/mees/dinghy/preview/ExtrudePreviews.kt`

- [ ] **Step 1: Add all new string resources** referenced above: `extrude_cooldown`, `extrude_macro_settings`, `extrude_distance_dec`, `extrude_distance_inc`, `extrude_filament_present`, `extrude_filament_absent`, plus any others introduced. Reuse existing `extrude_load`/`extrude_unload`/`extrude_speed_readout` etc. (Grep each `R.string.extrude_*` you referenced; add the missing ones.)

- [ ] **Step 2: Update `ExtrudePreviews.kt`** — seed an `ExtrudeVm` with: a couple `SensorRowVm`s (one enabled, one disabled), `maxExtrudeVelocity = 15`, two `pinnedMacros`, `hasLoadMacro = true`. Add previews for portrait + landscape + the MacroSettings mode + the no-sensor case (empty `sensors`).

- [ ] **Step 3: Run the FontConformance + full Extrude-related suite.**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.theme.FontConformanceTest' --tests 'works.mees.dinghy.ui.extrude.*' --tests 'works.mees.dinghy.command.*' --tests 'works.mees.dinghy.state.*' --no-daemon" | tr -d '\r'`
Expected: PASS. (FontConformance fails the build on any inline `fontFamily=`/`fontSize=` — all new text uses `DinghyType.<role>.toTextStyle(t)`.)

- [ ] **Step 4: Full unit suite + assembleDebug (force fresh APK for UAT).**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --no-daemon" | tr -d '\r'`

- [ ] **Step 5: Install on BOTH test devices and hand to owner for UAT.** Push the matching ABI slice to flox (`0a64b42e`, armeabi-v7a) and moto (`ZY22LBDRM9`, arm64-v8a). Verify APK mtime is newer than the last commit before install (stale-APK gate). Owner drives the app; verify: cold-gate disables extrude until a thermal chip heats; distance stepper cycles 1/5/25/50 and clamps at ceiling; speed scrubber caps at the printer's reported max; runout toggle flips and reflects live `enabled`; Load/Unload + pinned macros run; macro-settings curates pins; Cooldown fires; spool link navigates.

- [ ] **Step 6: Final commit.**

```bash
git add -A && git commit -m "feat(extrude): strings + previews + conformance green; UAT-ready

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review Notes (coverage map)

| Spec requirement | Task |
|---|---|
| Stable feed Focus (extrude/retract + distance ±stepper + speed scrubber + nozzle readout) | 6 |
| Distance ±stepper through 1/5/25/50, clamp at ceiling | 6 |
| Speed scrubber capped to `max_extrude_only_velocity` (fallback 15) | 1, 6 |
| Cold-extrude gate preserved | reused (6) |
| Runout toggle(s), live `enabled`, hidden when none, new SET_FILAMENT_SENSOR | 2, 3, 5, 7, 10 |
| Screen-scoped pinned macros, bare run, Load/Unload auto-rows | 4, 5, 7, 8, 10 |
| Inline nozzle-only thermal chips | 7 |
| Spool link | 7 |
| Foot bar Back / Cooldown / Macro-settings | 9 |
| No keyboard in controls (IME readouts removed) | 6 |
| Component-class + token conformance | 6–9, 11 |
| Icon law (reuse Decrease/Increase; ASK for Cooldown/Settings glyphs) | 6, 9 |

**Open items flagged for execution:** None. (Foot-bar icons resolved by owner 2026-06-16 — Task 9: Cooldown=`HideTemps`, Macro-settings=`ManageMacros`.)

**Codex review (2026-06-16):** read-only review found 4 blockers + 5 should-fixes, ALL applied to this plan before execution — printer-matrix.json drift row (Task 2 Step 5b), AppContainer manual-wiring + test call-sites (Task 4 Step 5), `DeriveCapabilities` package `state` not `net` (Task 3), velocity re-clamp `LaunchedEffect` + builder-ceiling cap (Tasks 5/6), `setMaxExtrudeDistance(null)` non-existence (Task 1), `macroDescriptions` accessor → descriptions deferred (Task 5), preview path `preview/` (Task 11), Load/Unload presence-gated rows (Task 7), use existing `booleanOrNull` (Task 3). Verified-OK: StepperRow/Scrubber/ToggleRow/GcodeCommandPresent/gcode()/cooldown/extrude/setHeater/load/unload/raw-dispatch/buildRaw/scriptParams all exist as referenced; one `subset` feeds both query+subscribe.
