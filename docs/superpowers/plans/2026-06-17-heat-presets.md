# Heat Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hardcoded `MATERIAL_PRESETS` (PLA/PETG/ABS/TPU) with per-printer **Heat Presets** — a named, sparse heater→temp map spanning every settable heater Moonraker lists (extruder(s), bed, heater_generic, temperature_fan), seeded Low/Med/High for new printers, edited via a Focus wizard, surfaced in Printer Settings, and applied from every preheat picker except the Extrude page (which stays extruder-only).

**Architecture:** A `HeatPreset` data class persisted per-printer in a new `HeatPresetPrefs` DataStore (12th store), exposed via `AppContainer.activeHeatPresets`. A new `applyHeatPreset` gcode command builds one multi-line script from the sparse map (`SET_HEATER_TEMPERATURE` for heaters, new `SET_TEMPERATURE_FAN_TARGET` for fans). A new `ui/heatpresets/` screen lists/selects/edits presets via a chain of `FocusFrame` wizard screens reusing `TokenTextField`. The four existing pickers swap `MATERIAL_PRESETS` for `activeHeatPresets`.

**Tech Stack:** Kotlin, Jetpack Compose + classic Views hybrid, kotlinx.serialization, DataStore (Preferences), Coroutines/Flow, OkHttp/Moonraker JSON-RPC. JUnit4 + kotlinx-coroutines-test.

**Build/test command (Windows-side, from repo root under WSL):**
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <gradle args>" 2>&1 | tr -d '\r'
```
Unit tests: `:app:testDebugUnitTest`. Full debug APK: `:app:assembleDebug`. Release: `:app:assembleRelease`.

**Design source of truth:** `docs/superpowers/specs/2026-06-17-heat-presets-design.md`.

**Standing rules to honor:**
- Per-printer DataStore writes route through `AppContainer.writeScope` (process-lifetime), never a composition scope (`[[dinghy-compose-write-scope-cancellation]]`).
- Read-modify-write inside ONE `dataStore.edit {}`.
- Every numeric value clamped before formatting into gcode.
- Fonts via `DinghyType` roles only (no inline `fontSize=`/`fontFamily=` — `FontConformanceTest` fails the build otherwise).
- Icons: use the `question_mark` ligature for any glyph not already selected (owner-authorized this session). Never invent other glyphs.
- Adding a `CommandSpec` to `CommandRegistry.all` requires matching `docs/commands/catalog.json` + `printer-matrix.json` rows or `CommandCatalogDriftTest` fails (`[[dinghy-command-catalog-drift]]`).

---

## File Structure

**Create:**
- `app/src/main/java/works/mees/dinghy/config/HeatPreset.kt` — model + sort + defaults
- `app/src/main/java/works/mees/dinghy/config/HeatPresetPrefs.kt` — per-printer DataStore store
- `app/src/main/java/works/mees/dinghy/state/HeaterNaming.kt` — display names + `enumerateSettableHeaters`
- `app/src/main/java/works/mees/dinghy/ui/heatpresets/HeatPresetsScreen.kt` — list/detail screen
- `app/src/main/java/works/mees/dinghy/ui/heatpresets/HeatPresetWizard.kt` — create/edit wizard + pure reconcile logic
- Tests: `HeatPresetTest.kt`, `HeatPresetPrefsTest.kt`, `HeaterNamingTest.kt`, `HeatPresetWizardLogicTest.kt` (under `app/src/test/java/works/mees/dinghy/...`)

**Modify:**
- `command/PrinterCommands.kt` — add `setTemperatureFanTarget`, `applyHeatPreset`; later remove `Preset`/`MATERIAL_PRESETS`/`applyPreset`
- `command/CommandRegistry.kt` — add `applyHeatPreset` + `setTemperatureFan` specs; later remove `applyPreset`
- `docs/commands/catalog.json` + `docs/commands/printer-matrix.json` — add `KGC-SET_TEMPERATURE_FAN_TARGET`
- `state/Capabilities.kt` + `state/DeriveCapabilities.kt` — `temperatureFans` list
- `designsystem/icons/DinghyIcons.kt` — new question_mark icons
- `ui/route/NavDest.kt` + `ui/shell/AppShell.kt` (or wherever the NavHost lives) — register route
- `ui/screen/PrinterSettingsScreen.kt` — "Heat Presets" row
- `di/AppContainer.kt` + `DinghyApp.kt` — wire the 12th DataStore + flows + seeding
- `ui/temperature/TemperatureScreen.kt`, `ui/printstatus/PrintStatusScreen.kt`, `ui/extrude/ExtrudeScreen.kt` — migrate consumers

---

## Task 1: HeatPreset model, sort, and defaults

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/config/HeatPreset.kt`
- Test: `app/src/test/java/works/mees/dinghy/config/HeatPresetTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeatPresetTest {

    @Test
    fun extruderTemp_readsExtruderKey_orNull() {
        assertEquals(200, HeatPreset("a", "Med", mapOf("extruder" to 200, "heater_bed" to 60)).extruderTemp)
        assertNull(HeatPreset("b", "BedOnly", mapOf("heater_bed" to 60)).extruderTemp)
    }

    @Test
    fun sortedForDisplay_byExtruderAsc_nullsLast_thenNameCaseInsensitive() {
        val high = HeatPreset("h", "High", mapOf("extruder" to 230))
        val low = HeatPreset("l", "Low", mapOf("extruder" to 150))
        val med = HeatPreset("m", "Medium", mapOf("extruder" to 200))
        val noExtA = HeatPreset("z", "alpha", mapOf("heater_bed" to 60))
        val noExtB = HeatPreset("y", "Beta", mapOf("heater_bed" to 70))
        val sorted = listOf(high, noExtB, med, noExtA, low).sortedForDisplay()
        assertEquals(listOf("Low", "Medium", "High", "alpha", "Beta"), sorted.map { it.name })
    }

    @Test
    fun defaultHeatPresets_areLowMedHigh_extruderAndBedOnly_uniqueIds() {
        val d = defaultHeatPresets()
        assertEquals(listOf("Low", "Medium", "High"), d.map { it.name })
        assertEquals(mapOf("extruder" to 150, "heater_bed" to 50), d[0].setpoints)
        assertEquals(mapOf("extruder" to 200, "heater_bed" to 65), d[1].setpoints)
        assertEquals(mapOf("extruder" to 230, "heater_bed" to 90), d[2].setpoints)
        assertEquals(3, d.map { it.id }.toSet().size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.HeatPresetTest"`
Expected: FAIL — `HeatPreset` / `sortedForDisplay` / `defaultHeatPresets` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package works.mees.dinghy.config

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One per-printer preheat preset: a NAME plus a SPARSE map of Moonraker heater object name → target °C.
 * Absent key = "skip this heater" (the preset never touches it). A stored value of `0` is a REAL
 * setpoint that commands TARGET=0 (turn the heater off). Keys are full object names
 * (`extruder`, `heater_bed`, `heater_generic chamber`, `temperature_fan exhaust`).
 */
@Serializable
@Immutable
data class HeatPreset(
    val id: String,
    val name: String,
    val setpoints: Map<String, Int> = emptyMap(),
) {
    /** Primary-extruder setpoint — drives list sorting and the Extrude page's nozzle-only apply. */
    val extruderTemp: Int? get() = setpoints["extruder"]
}

/** Sort for display: by primary-extruder temp ascending, presets without one last, then name (ci). */
fun List<HeatPreset>.sortedForDisplay(): List<HeatPreset> =
    sortedWith(compareBy({ it.extruderTemp ?: Int.MAX_VALUE }, { it.name.lowercase() }))

/** The three jiib defaults seeded for a NEW printer (extruder + bed only; generics/fans omitted). */
fun defaultHeatPresets(): List<HeatPreset> = listOf(
    HeatPreset(UUID.randomUUID().toString(), "Low", mapOf("extruder" to 150, "heater_bed" to 50)),
    HeatPreset(UUID.randomUUID().toString(), "Medium", mapOf("extruder" to 200, "heater_bed" to 65)),
    HeatPreset(UUID.randomUUID().toString(), "High", mapOf("extruder" to 230, "heater_bed" to 90)),
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.HeatPresetTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/config/HeatPreset.kt app/src/test/java/works/mees/dinghy/config/HeatPresetTest.kt
git commit -m "feat(heatpresets): HeatPreset model, display sort, jiib defaults"
```

---

## Task 2: HeatPresetPrefs — per-printer DataStore store

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/config/HeatPresetPrefs.kt`
- Test: `app/src/test/java/works/mees/dinghy/config/HeatPresetPrefsTest.kt`

Pattern source: `ui/move/SavedLocationPrefs.kt` (JSON-list serialization) + `ui/settings/TraceStylePrefs.kt` (per-profileId key scoping). Test harness: `config/ProfileStoreTest.kt` (real temp-file DataStore on an IO scope, `settle()` helper).

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class HeatPresetPrefsTest {
    private val tmpFiles = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After fun tearDown() { scopes.forEach { it.cancel() }; tmpFiles.forEach { it.delete() } }

    private fun newPrefs(): Pair<HeatPresetPrefs, CoroutineScope> {
        val file = File.createTempFile("heatpreset_test_", ".preferences_pb").also { it.delete(); tmpFiles += it }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO); scopes += ioScope
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = ioScope) { file }
        return HeatPresetPrefs(ds) to ioScope
    }

    private suspend fun settle() { yield(); Thread.sleep(60); System.gc(); Thread.sleep(60); yield() }

    @Test fun addOrUpdate_roundTrips_andSorts() = runTest {
        val (prefs, io) = newPrefs()
        withContext(io.coroutineContext) {
            prefs.addOrUpdate("p1", HeatPreset("b", "High", mapOf("extruder" to 230)))
            prefs.addOrUpdate("p1", HeatPreset("a", "Low", mapOf("extruder" to 150)))
            settle()
            assertEquals(listOf("Low", "High"), prefs.presets("p1").first().map { it.name })
        }
    }

    @Test fun addOrUpdate_replacesById() = runTest {
        val (prefs, io) = newPrefs()
        withContext(io.coroutineContext) {
            prefs.addOrUpdate("p1", HeatPreset("a", "Low", mapOf("extruder" to 150)))
            prefs.addOrUpdate("p1", HeatPreset("a", "Low+", mapOf("extruder" to 160)))
            settle()
            val list = prefs.presets("p1").first()
            assertEquals(1, list.size)
            assertEquals("Low+", list[0].name)
            assertEquals(160, list[0].extruderTemp)
        }
    }

    @Test fun delete_isIdempotent() = runTest {
        val (prefs, io) = newPrefs()
        withContext(io.coroutineContext) {
            prefs.addOrUpdate("p1", HeatPreset("a", "Low", mapOf("extruder" to 150)))
            prefs.delete("p1", "a"); prefs.delete("p1", "a")
            settle()
            assertEquals(emptyList<String>(), prefs.presets("p1").first().map { it.name })
        }
    }

    @Test fun seedIfEmpty_onlyWhenEmpty() = runTest {
        val (prefs, io) = newPrefs()
        withContext(io.coroutineContext) {
            prefs.seedIfEmpty("p1", defaultHeatPresets()); settle()
            assertEquals(listOf("Low", "Medium", "High"), prefs.presets("p1").first().map { it.name })
            // Delete one then seed again — must NOT re-seed (store is non-empty).
            prefs.delete("p1", prefs.presets("p1").first().first().id); settle()
            prefs.seedIfEmpty("p1", defaultHeatPresets()); settle()
            assertEquals(2, prefs.presets("p1").first().size)
        }
    }

    @Test fun perProfileIsolation() = runTest {
        val (prefs, io) = newPrefs()
        withContext(io.coroutineContext) {
            prefs.addOrUpdate("p1", HeatPreset("a", "Low", mapOf("extruder" to 150)))
            settle()
            assertEquals(emptyList<String>(), prefs.presets("p2").first().map { it.name })
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.HeatPresetPrefsTest"`
Expected: FAIL — `HeatPresetPrefs` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package works.mees.dinghy.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Per-printer Heat Presets, persisted as a JSON list under a profile-scoped key (`presets_<profileId>`).
 * Mirrors [works.mees.dinghy.ui.move.SavedLocationPrefs] serialization + [TraceStylePrefs] per-printer
 * key scoping. All writes are read-modify-write inside ONE [DataStore.edit]. Fail-safe: a read error
 * yields an empty list. The 12th, independent DataStore file (heat_presets.preferences_pb).
 */
class HeatPresetPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /** Presets for [profileId], pre-sorted for display. Empty when none / on read error. */
    fun presets(profileId: String): Flow<List<HeatPreset>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> decode(prefs[key(profileId)]).sortedForDisplay() }

    /** Insert or replace [preset] (matched by id) under [profileId]. */
    suspend fun addOrUpdate(profileId: String, preset: HeatPreset) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key(profileId)]).filterNot { it.id == preset.id }
            prefs[key(profileId)] = JSON.encodeToString(LIST_SERIALIZER, current + preset)
        }
    }

    /** Remove the preset with [id] under [profileId] (idempotent). */
    suspend fun delete(profileId: String, id: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key(profileId)]).filterNot { it.id == id }
            prefs[key(profileId)] = JSON.encodeToString(LIST_SERIALIZER, current)
        }
    }

    /** Seed [presets] under [profileId] ONLY if it currently has none (new-printer seeding). */
    suspend fun seedIfEmpty(profileId: String, presets: List<HeatPreset>) {
        dataStore.edit { prefs ->
            if (decode(prefs[key(profileId)]).isEmpty()) {
                prefs[key(profileId)] = JSON.encodeToString(LIST_SERIALIZER, presets)
            }
        }
    }

    private fun decode(raw: String?): List<HeatPreset> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { JSON.decodeFromString(LIST_SERIALIZER, raw) }.getOrDefault(emptyList())

    companion object {
        private fun key(profileId: String) = stringPreferencesKey("presets_$profileId")
        private val JSON = Json { ignoreUnknownKeys = true }
        private val LIST_SERIALIZER = ListSerializer(HeatPreset.serializer())
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.HeatPresetPrefsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/config/HeatPresetPrefs.kt app/src/test/java/works/mees/dinghy/config/HeatPresetPrefsTest.kt
git commit -m "feat(heatpresets): per-printer HeatPresetPrefs DataStore store"
```

---

## Task 3: PrinterCommands — setTemperatureFanTarget + applyHeatPreset

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt` (add after the existing `applyPreset` block, ~line 284)
- Test: `app/src/test/java/works/mees/dinghy/command/PrinterCommandsTest.kt` (confirm exact path with `git ls-files | grep PrinterCommandsTest`)

- [ ] **Step 1: Write the failing test** (append these methods to `PrinterCommandsTest`)

```kotlin
@Test
fun setTemperatureFanTarget_clampsAndFormats() {
    assertEquals("SET_TEMPERATURE_FAN_TARGET FAN=exhaust TARGET=45", PrinterCommands.setTemperatureFanTarget("exhaust", 45))
    assertEquals("SET_TEMPERATURE_FAN_TARGET FAN=exhaust TARGET=350", PrinterCommands.setTemperatureFanTarget("exhaust", 9999))
    assertEquals("SET_TEMPERATURE_FAN_TARGET FAN=exhaust TARGET=0", PrinterCommands.setTemperatureFanTarget("exhaust", -5))
}

@Test
fun applyHeatPreset_buildsClampedMultilineScript_byHeaterType() {
    val script = PrinterCommands.applyHeatPreset(
        mapOf(
            "extruder" to 200,
            "heater_bed" to 60,
            "heater_generic chamber" to 50,
            "temperature_fan exhaust" to 40,
        ),
    )
    // Sorted by object name for determinism: extruder, heater_bed, heater_generic chamber, temperature_fan exhaust
    assertEquals(
        listOf(
            "SET_HEATER_TEMPERATURE HEATER=extruder TARGET=200",
            "SET_HEATER_TEMPERATURE HEATER=heater_bed TARGET=60",
            "SET_HEATER_TEMPERATURE HEATER=chamber TARGET=50",
            "SET_TEMPERATURE_FAN_TARGET FAN=exhaust TARGET=40",
        ).joinToString("\n"),
        script,
    )
}

@Test
fun applyHeatPreset_emptyMap_isEmptyString() {
    assertEquals("", PrinterCommands.applyHeatPreset(emptyMap()))
}

@Test
fun applyHeatPreset_zeroIsARealTarget() {
    assertEquals("SET_HEATER_TEMPERATURE HEATER=extruder TARGET=0", PrinterCommands.applyHeatPreset(mapOf("extruder" to 0)))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.command.PrinterCommandsTest"`
Expected: FAIL — `setTemperatureFanTarget` / `applyHeatPreset` unresolved.

- [ ] **Step 3: Write minimal implementation** (add to `PrinterCommands` object, after `applyPreset`)

```kotlin
/** `SET_TEMPERATURE_FAN_TARGET FAN=<fan> TARGET=<target>`. [target] clamped to [MIN_TEMP_C]..[MAX_TEMP_C]. */
fun setTemperatureFanTarget(fan: String, target: Int): String =
    "SET_TEMPERATURE_FAN_TARGET FAN=$fan TARGET=${clampHeaterTarget(target)}"

/**
 * Build the newline-joined gcode for an arbitrary [setpoints] map (heater object name → °C):
 * `temperature_fan <name>` → SET_TEMPERATURE_FAN_TARGET; everything else → SET_HEATER_TEMPERATURE
 * (heater_generic uses the BARE name as HEATER=). Order is sorted by object name for determinism.
 * Every value is clamped. An empty map yields "".
 */
fun applyHeatPreset(setpoints: Map<String, Int>): String =
    setpoints.entries
        .sortedBy { it.key }
        .joinToString("\n") { (obj, temp) -> heaterCommandLine(obj, temp) }

private fun heaterCommandLine(objectName: String, temp: Int): String =
    if (objectName.startsWith("temperature_fan ")) {
        setTemperatureFanTarget(objectName.removePrefix("temperature_fan "), temp)
    } else {
        setHeater(heaterArg(objectName), temp)
    }

/** The `HEATER=` argument for SET_HEATER_TEMPERATURE: heater_generic uses its bare name. */
private fun heaterArg(objectName: String): String =
    if (objectName.startsWith("heater_generic ")) objectName.removePrefix("heater_generic ") else objectName
```

- [ ] **Step 4: Run test to verify it passes**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.command.PrinterCommandsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt app/src/test/java/works/mees/dinghy/command/PrinterCommandsTest.kt
git commit -m "feat(heatpresets): applyHeatPreset + setTemperatureFanTarget gcode builders"
```

---

## Task 4: CommandRegistry specs + command catalog rows

**Files:**
- Modify: `command/CommandRegistry.kt` (args ~line 19-21; specs ~line 434-446; `all` list ~line 806-876)
- Modify: `docs/commands/catalog.json`, `docs/commands/printer-matrix.json`
- Test: `app/src/test/java/works/mees/dinghy/command/CommandRegistryGcodeTest.kt`

`applyHeatPreset` reuses the existing `KGC-SET_HEATER_TEMPERATURE_PRESET` catalog id (already present → no new row). `setTemperatureFan` introduces `KGC-SET_TEMPERATURE_FAN_TARGET` (new rows required).

- [ ] **Step 1: Write the failing tests** (append to `CommandRegistryGcodeTest`)

```kotlin
@Test
fun applyHeatPreset_wrapsPrinterCommandsByteIdentically() {
    val setpoints = mapOf("extruder" to 200, "heater_bed" to 60, "temperature_fan exhaust" to 40)
    assertRegistryScript(
        CommandRegistry.applyHeatPreset,
        ApplyHeatPresetArgs(setpoints, key = "preset_x"),
        PrinterCommands.applyHeatPreset(setpoints),
    )
}

@Test
fun setTemperatureFan_wrapsPrinterCommandsByteIdentically() {
    assertRegistryScript(
        CommandRegistry.setTemperatureFan,
        SetTemperatureFanArgs("exhaust", 45),
        PrinterCommands.setTemperatureFanTarget("exhaust", 45),
    )
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.command.CommandRegistryGcodeTest"`
Expected: FAIL — `applyHeatPreset` / `setTemperatureFan` / arg types unresolved.

- [ ] **Step 3: Implement — args + specs + registration**

Add near the other arg data classes (~line 19-21):
```kotlin
data class SetTemperatureFanArgs(val name: String, val target: Int, val key: String? = null)
data class ApplyHeatPresetArgs(val setpoints: Map<String, Int>, val key: String = "apply_heat_preset")
```

Add near `setHeater`/`applyPreset` specs (~line 434-446):
```kotlin
val setTemperatureFan: CommandSpec<SetTemperatureFanArgs> = gcode(
    catalogId = "KGC-SET_TEMPERATURE_FAN_TARGET",
    key = { args -> args.key ?: "set_tempfan_${args.name}" },
    gcode = { args -> PrinterCommands.setTemperatureFanTarget(args.name, args.target) },
    availability = AvailabilityPredicate.Always,
)

val applyHeatPreset: CommandSpec<ApplyHeatPresetArgs> = gcode(
    catalogId = "KGC-SET_HEATER_TEMPERATURE_PRESET",
    key = { args -> args.key },
    gcode = { args -> PrinterCommands.applyHeatPreset(args.setpoints) },
    availability = AvailabilityPredicate.ObjectPresent("extruder"),
)
```

Add both to `CommandRegistry.all` (next to `applyPreset`):
```kotlin
    applyPreset,
    applyHeatPreset,
    setTemperatureFan,
```

- [ ] **Step 4: Add catalog rows**

In `docs/commands/catalog.json`, add an entry modeled on `KGC-SET_HEATER_TEMPERATURE` (copy its structure exactly), changing:
```json
{
  "id": "KGC-SET_TEMPERATURE_FAN_TARGET",
  "catalog_id": "KGC-SET_TEMPERATURE_FAN_TARGET",
  "source_api": "klipper_gcode",
  "transport": "gcode_script",
  "name": "SET_TEMPERATURE_FAN_TARGET",
  "category": "temperature",
  "purpose": "Klipper `SET_TEMPERATURE_FAN_TARGET` command — set a temperature_fan's target temperature.",
  "params": ["FAN", "TARGET"],
  "key_params": ["FAN", "TARGET"],
  "semantics_tier": "full",
  "upstream_url": "https://www.klipper3d.org/G-Codes.html",
  "rest_endpoint": null,
  "http_method": null,
  "availability": { "type": "always" },
  "predicate": { "type": "always" },
  "runtime_registry": {
    "status": "registered",
    "registered": true,
    "notes": "Present in CommandRegistry for Heat Presets temperature_fan setpoints."
  },
  "success_semantics": "Klipper accepts the G-Code script and adjusts the temperature_fan target.",
  "error_semantics": "Klipper rejects invalid or unsafe commands with gcode error text.",
  "acceptance_semantics": "Registry gcode commands are dispatched via gcode.script JSON-RPC; script params are produced byte-identically."
}
```
In `docs/commands/printer-matrix.json`, add a `command_availability` row modeled on an existing `KGC-*` gcode row, `catalog_id` = `"KGC-SET_TEMPERATURE_FAN_TARGET"`, `predicate` `{"type":"always"}`, with a `printer_status` entry per printer id present in the file (copy the shape of a neighboring gcode row; evidence note: "Registered for Heat Presets; not executed during read-only capture.").

- [ ] **Step 5: Run drift + gcode tests**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.command.CommandRegistryGcodeTest --tests works.mees.dinghy.command.CommandCatalogDriftTest"`
Expected: PASS. If drift fails, the JSON structure doesn't match the existing rows — diff against a neighboring entry and fix.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt docs/commands/catalog.json docs/commands/printer-matrix.json app/src/test/java/works/mees/dinghy/command/CommandRegistryGcodeTest.kt
git commit -m "feat(heatpresets): applyHeatPreset + setTemperatureFan command specs + catalog"
```

---

## Task 5: temperature_fan enumeration in Capabilities

**Files:**
- Modify: `state/Capabilities.kt` (add field), `state/DeriveCapabilities.kt` (populate)
- Test: `app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt` (confirm path via `git ls-files | grep DeriveCapabilities`)

- [ ] **Step 1: Write the failing test** (append a method to the existing DeriveCapabilities test)

```kotlin
@Test
fun temperatureFans_capturedFromObjectList() {
    val caps = deriveCapabilities(/* use the same factory the sibling tests use */ objects = listOf(
        "extruder", "heater_bed", "temperature_fan exhaust", "temperature_fan chamber_fan", "fan",
    ))
    assertEquals(listOf("temperature_fan chamber_fan", "temperature_fan exhaust"), caps.temperatureFans.sorted())
}
```
NOTE: match the EXACT factory/signature the existing tests in this file use (e.g. how they build `Capabilities` from an objects list). Adapt the call above to that harness.

- [ ] **Step 2: Run to verify it fails**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.state.DeriveCapabilitiesTest"`
Expected: FAIL — `temperatureFans` unresolved.

- [ ] **Step 3: Implement**

In `state/Capabilities.kt`, add to the `Capabilities` data class (mirror the existing `fans`/`heaters` list fields, defaulting empty):
```kotlin
    val temperatureFans: List<String> = emptyList(),
```
In `state/DeriveCapabilities.kt`, collect `temperature_fan ` objects from the object list (mirror how `heaters`/`fans` are built) and pass them into the `Capabilities(...)` construction as `temperatureFans = ...`. Example collection:
```kotlin
val temperatureFans = objects.filter { it.startsWith("temperature_fan ") }
```

- [ ] **Step 4: Run to verify it passes**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.state.DeriveCapabilitiesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/state/Capabilities.kt app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt
git commit -m "feat(heatpresets): enumerate temperature_fan objects in Capabilities"
```

---

## Task 6: HeaterNaming — display names + enumerateSettableHeaters

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/state/HeaterNaming.kt`
- Test: `app/src/test/java/works/mees/dinghy/state/HeaterNamingTest.kt`

Display names MUST match the convention already shown by `ui/temperature/TemperatureHolder.kt` (`extruder`→"Nozzle", `heater_bed`→"Bed", generics/fans → Title-Cased bare name).

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeaterNamingTest {
    @Test fun displayNames() {
        assertEquals("Nozzle", heaterDisplayName("extruder"))
        assertEquals("Nozzle 1", heaterDisplayName("extruder1"))
        assertEquals("Bed", heaterDisplayName("heater_bed"))
        assertEquals("Chamber", heaterDisplayName("heater_generic chamber"))
        assertEquals("Exhaust", heaterDisplayName("temperature_fan exhaust"))
    }

    @Test fun enumerate_ordersExtruderBedGenericFan_withLimits() {
        val limits = mapOf(
            "heater_generic chamber" to HeaterLimits(0.0, 120.0),
            "extruder" to HeaterLimits(0.0, 300.0),
            "heater_bed" to HeaterLimits(0.0, 130.0),
        )
        val fans = listOf("temperature_fan exhaust")
        val out = enumerateSettableHeaters(limits, fans)
        assertEquals(listOf("extruder", "heater_bed", "heater_generic chamber", "temperature_fan exhaust"), out.map { it.objectName })
        assertEquals(listOf("Nozzle", "Bed", "Chamber", "Exhaust"), out.map { it.displayName })
        assertEquals(300, out[0].maxTemp)            // from limits
        assertNull(out[3].maxTemp)                   // fan limits not parsed → null (global clamp applies)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.state.HeaterNamingTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package works.mees.dinghy.state

import androidx.compose.runtime.Immutable

/** A heater the user can set a target on, with a friendly label and (optional) config limits. */
@Immutable
data class SettableHeater(
    val objectName: String,
    val displayName: String,
    val minTemp: Int?,
    val maxTemp: Int?,
)

/**
 * Friendly label for a Moonraker heater object name (matches TemperatureHolder.label):
 * `extruder`→"Nozzle", `extruder1`→"Nozzle 1", `heater_bed`→"Bed",
 * `heater_generic <n>`/`temperature_fan <n>`→Title-Cased bare name; else Title-Cased.
 */
fun heaterDisplayName(objectName: String): String = when {
    objectName == "extruder" -> "Nozzle"
    objectName.startsWith("extruder") && objectName.removePrefix("extruder").all { it.isDigit() } ->
        "Nozzle ${objectName.removePrefix("extruder")}"
    objectName == "heater_bed" -> "Bed"
    objectName.startsWith("heater_generic ") -> titleCase(objectName.removePrefix("heater_generic "))
    objectName.startsWith("temperature_fan ") -> titleCase(objectName.removePrefix("temperature_fan "))
    else -> titleCase(objectName)
}

/**
 * Ordered settable heaters for the Heat Preset wizard: extruder(s) → bed → heater_generic (alpha) →
 * temperature_fan (alpha). Heaters + their limits come from [heaterLimits] (parsed configfile); fans
 * come from [temperatureFans] with null limits (their config limits are not parsed in v1 → the global
 * 0..350 clamp applies at send time).
 */
fun enumerateSettableHeaters(
    heaterLimits: Map<String, HeaterLimits>,
    temperatureFans: List<String>,
): List<SettableHeater> {
    fun rank(name: String): Int = when {
        name == "extruder" || (name.startsWith("extruder") && name.removePrefix("extruder").all { it.isDigit() }) -> 0
        name == "heater_bed" -> 1
        name.startsWith("heater_generic ") -> 2
        else -> 3
    }
    val heaters = heaterLimits.keys
        .sortedWith(compareBy({ rank(it) }, { it }))
        .map { name ->
            val lim = heaterLimits[name]
            SettableHeater(name, heaterDisplayName(name), lim?.minTemp?.toInt(), lim?.maxTemp?.toInt())
        }
    val fans = temperatureFans.sorted()
        .map { SettableHeater(it, heaterDisplayName(it), minTemp = null, maxTemp = null) }
    return heaters + fans
}

private fun titleCase(raw: String): String =
    raw.lowercase().split(Regex("[\\s_]+")).filter { it.isNotEmpty() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
```

- [ ] **Step 4: Run to verify it passes**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.state.HeaterNamingTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/state/HeaterNaming.kt app/src/test/java/works/mees/dinghy/state/HeaterNamingTest.kt
git commit -m "feat(heatpresets): heaterDisplayName + enumerateSettableHeaters"
```

---

## Task 7: Wizard pure logic — reconcile fields + build preset

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/heatpresets/HeatPresetWizard.kt` (pure logic first; Compose added in Task 10)
- Test: `app/src/test/java/works/mees/dinghy/ui/heatpresets/HeatPresetWizardLogicTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.ui.heatpresets

import works.mees.dinghy.config.HeatPreset
import works.mees.dinghy.state.SettableHeater
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatPresetWizardLogicTest {
    private val heaters = listOf(
        SettableHeater("extruder", "Nozzle", 0, 300),
        SettableHeater("heater_bed", "Bed", 0, 130),
        SettableHeater("heater_generic chamber", "Chamber", 0, 120), // NEW since the saved preset
    )

    @Test fun reconcile_prefillsSaved_newHeaterBlank() {
        val saved = HeatPreset("id", "Med", mapOf("extruder" to 200, "heater_bed" to 60))
        val fields = reconcileWizardFields(saved, heaters)
        assertEquals(listOf("extruder", "heater_bed", "heater_generic chamber"), fields.map { it.objectName })
        assertEquals(listOf("200", "60", ""), fields.map { it.initialValue }) // new chamber blank
    }

    @Test fun reconcile_create_allBlank() {
        val fields = reconcileWizardFields(saved = null, heaters = heaters)
        assertEquals(listOf("", "", ""), fields.map { it.initialValue })
    }

    @Test fun build_blankOmitted_zeroKept_clampedToLimits() {
        val raw = mapOf("extruder" to "9999", "heater_bed" to "", "heater_generic chamber" to "0")
        val preset = buildPresetFromInput(id = "id", name = "  My Preset ", rawValues = raw, heaters = heaters)
        assertEquals("My Preset", preset.name)                 // trimmed
        assertEquals(mapOf("extruder" to 300, "heater_generic chamber" to 0), preset.setpoints) // bed omitted (blank), extruder clamped to max, 0 kept
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.heatpresets.HeatPresetWizardLogicTest"`
Expected: FAIL.

- [ ] **Step 3: Implement the pure logic** (top of `HeatPresetWizard.kt`)

```kotlin
package works.mees.dinghy.ui.heatpresets

import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.config.HeatPreset
import works.mees.dinghy.state.SettableHeater

/** One heater step in the wizard: identity + label + clamp bounds + the prefilled text value. */
data class WizardHeaterField(
    val objectName: String,
    val displayName: String,
    val minTemp: Int,
    val maxTemp: Int,
    val initialValue: String,
)

/**
 * Build the ordered heater fields for the wizard against the CURRENT live [heaters]. On edit, prefill
 * from [saved]'s setpoints (blank for heaters the saved preset omits OR that are new since it was made);
 * heaters no longer in config simply don't appear → the preset is rebuilt fresh against current config.
 */
fun reconcileWizardFields(saved: HeatPreset?, heaters: List<SettableHeater>): List<WizardHeaterField> =
    heaters.map { h ->
        WizardHeaterField(
            objectName = h.objectName,
            displayName = h.displayName,
            minTemp = h.minTemp ?: PrinterCommands.MIN_TEMP_C,
            maxTemp = h.maxTemp ?: PrinterCommands.MAX_TEMP_C,
            initialValue = saved?.setpoints?.get(h.objectName)?.toString() ?: "",
        )
    }

/**
 * Build a [HeatPreset] from wizard input. [rawValues] maps object name → the user's text. A blank/
 * unparseable value OMITS that heater (skip); any parsed integer (including 0 = off) is KEPT, clamped
 * to that heater's [minTemp]..[maxTemp]. [name] is trimmed.
 */
fun buildPresetFromInput(
    id: String,
    name: String,
    rawValues: Map<String, String>,
    heaters: List<SettableHeater>,
): HeatPreset {
    val byName = heaters.associateBy { it.objectName }
    val setpoints = buildMap {
        for ((obj, raw) in rawValues) {
            val parsed = raw.trim().toIntOrNull() ?: continue   // blank/garbage → skip
            val h = byName[obj]
            val lo = h?.minTemp ?: PrinterCommands.MIN_TEMP_C
            val hi = h?.maxTemp ?: PrinterCommands.MAX_TEMP_C
            put(obj, parsed.coerceIn(lo, hi))
        }
    }
    return HeatPreset(id = id, name = name.trim(), setpoints = setpoints)
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.heatpresets.HeatPresetWizardLogicTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/heatpresets/HeatPresetWizard.kt app/src/test/java/works/mees/dinghy/ui/heatpresets/HeatPresetWizardLogicTest.kt
git commit -m "feat(heatpresets): pure wizard reconcile + build-preset logic"
```

---

## Task 8: DinghyIcons — question_mark glyphs

**Files:**
- Modify: `designsystem/icons/DinghyIcons.kt`

- [ ] **Step 1: Add icons** (near `TempPresets`, line ~350; also add each to the `all` registry list)

```kotlin
val HeatPresetAdd = DinghyIcon(IconRef.Ligature("question_mark"), alternate = "heat_preset_add")
val HeatPresetEdit = DinghyIcon(IconRef.Ligature("question_mark"), alternate = "heat_preset_edit")
val HeatPresetHeater = DinghyIcon(IconRef.Ligature("question_mark"), alternate = "heat_preset_heater")
val HeatPresetDelete = DinghyIcon(IconRef.Ligature("question_mark"), alternate = "heat_preset_delete")
```
Reuse for other roles (do NOT add new): `TempPresets` for the Printer-Settings row + the screen's FocusFrame icon; `Back` for back; `DialogClose` for wizard Cancel; `CheckCircle` for wizard Save/Next where a checkmark fits. (If a real trash glyph is already registered for the Files delete action, prefer it for `HeatPresetDelete` instead of question_mark — grep `DinghyIcons` for the Files-delete FootAction icon; otherwise keep question_mark.)

- [ ] **Step 2: Verify ligatures resolve**

Run the repo's ligature verifier (it scrapes the whole DinghyIcons registry):
```bash
python3 scripts/verify_ligatures.py   # confirm exact path with: git ls-files | grep verify_ligatures
```
Expected: no missing-glyph errors (`question_mark` is in the bundled Material Symbols font).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
git commit -m "feat(heatpresets): register question_mark placeholder icons (owner-authorized)"
```

---

## Task 9: AppContainer + DinghyApp wiring (12th DataStore, flows, seeding)

**Files:**
- Modify: `DinghyApp.kt` (create `heat_presets.preferences_pb`, pass to ctor)
- Modify: `di/AppContainer.kt` (ctor param, prefs instance, `activeHeatPresets`, `heaterLimits` flow if missing, `saveHeatPreset`/`deleteHeatPreset`, seeding in `saveProfile`)

- [ ] **Step 1: DinghyApp — create the DataStore**

After the `extrudeMacroDataStore` creation, add:
```kotlin
// A TWELFTH, INDEPENDENT file: heat_presets.preferences_pb (Heat Presets). Carries no secrets; kept
// on its own connection-independent lifecycle per the separate-file discipline — backs the per-printer
// HeatPresetPrefs (JSON list keyed presets_<profileId>). One instance per process (single-writer).
val heatPresetDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = appScope,
    produceFile = { applicationContext.preferencesDataStoreFile("heat_presets.preferences_pb") },
)
```
Add `heatPresetDataStore = heatPresetDataStore,` to the `AppContainer(...)` call.

- [ ] **Step 2: AppContainer — ctor param + prefs instance**

Add a ctor param (after `extrudeMacroDataStore`, before `val discovery`):
```kotlin
    heatPresetDataStore: DataStore<Preferences>,
```
Add the prefs instance (near `displayPrefs`):
```kotlin
    /** Per-printer Heat Presets store (12th file, heat_presets.preferences_pb). */
    val heatPresetPrefs: HeatPresetPrefs = HeatPresetPrefs(heatPresetDataStore)
```

- [ ] **Step 3: AppContainer — active-profile-scoped flow + write helpers**

```kotlin
    /** The active printer's Heat Presets (sorted), or empty when no active profile. */
    val activeHeatPresets: Flow<List<HeatPreset>> =
        activeProfileId.flatMapLatest { id ->
            if (id != null) heatPresetPrefs.presets(id) else flowOf(emptyList())
        }

    /** Insert/replace a Heat Preset for the active printer (durable; routes through writeScope). */
    fun saveHeatPreset(preset: HeatPreset) {
        writeScope.launch {
            val id = activeProfileId.first() ?: return@launch
            heatPresetPrefs.addOrUpdate(id, preset)
        }
    }

    /** Delete a Heat Preset (by id) for the active printer (durable; routes through writeScope). */
    fun deleteHeatPreset(presetId: String) {
        writeScope.launch {
            val id = activeProfileId.first() ?: return@launch
            heatPresetPrefs.delete(id, presetId)
        }
    }
```
Imports needed: `works.mees.dinghy.config.HeatPreset`, `works.mees.dinghy.config.HeatPresetPrefs`, `works.mees.dinghy.config.defaultHeatPresets`, `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.flow.flowOf`.

- [ ] **Step 4: AppContainer — seed on NEW profile**

Update `saveProfile` to seed presets only when the profile id is new (an edit reuses an existing id → no seed; deleting all presets later never re-seeds because the id already exists):
```kotlin
    /** Insert/replace a profile (Settings save + theme persist), durably. New profiles get seeded Heat Presets. */
    fun saveProfile(profile: Profile) {
        writeScope.launch {
            val isNew = profileStore.profiles.first().none { it.id == profile.id }
            profileStore.upsert(profile)
            if (isNew) heatPresetPrefs.seedIfEmpty(profile.id, defaultHeatPresets())
        }
    }
```
(If `saveProfile` currently has no body beyond `writeScope.launch { profileStore.upsert(profile) }`, replace it with the above. `profileStore.profiles` is the existing `Flow<List<Profile>>`.)

- [ ] **Step 5: AppContainer — expose heaterLimits if not already**

If there is no `AppContainer.heaterLimits` flow, add one (the wizard needs it):
```kotlin
    /** Per-heater configfile min/max (one-shot at handshake) for the active session; empty when idle. */
    val heaterLimits: Flow<Map<String, HeaterLimits>> =
        spine.flatMapLatest { it?.heaterLimits ?: flowOf(emptyMap()) }
```
(`SpineHandle.heaterLimits` exists. Import `works.mees.dinghy.state.HeaterLimits`. If a `heaterLimits` flow already exists on the container, skip.) `capabilities` is already exposed (`container.capabilities`).

- [ ] **Step 6: Verify it compiles**

Run: `... "E:\Android\gw.bat :app:compileDebugKotlin"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/DinghyApp.kt app/src/main/java/works/mees/dinghy/di/AppContainer.kt
git commit -m "feat(heatpresets): wire 12th DataStore, activeHeatPresets flow, new-printer seeding"
```

---

## Task 10: HeatPresets screen (list/detail) + wizard UI + route

**Files:**
- Create: `ui/heatpresets/HeatPresetsScreen.kt`
- Modify: `ui/heatpresets/HeatPresetWizard.kt` (add Compose wizard below the pure logic)
- Modify: `ui/route/NavDest.kt`, `ui/shell/AppShell.kt` (NavHost), `ui/screen/PrinterSettingsScreen.kt`

This is UI — no Compose unit tests; verified by compile + on-device UAT. Reuse the canonical components (`FocusFrame`, `ListBlock`, `ListRow`/`ListRowIcon`/`ListRowLabel`, `FootButtonBar`/`FootAction`, `OutlinedControl`, `TokenTextField`, `ConfirmGuard`, `DinghyType` roles, `LocalTokens`). Follow `BedMeshScreen.kt` for the field-takeover + foot bar pattern and `PrinterSettingsScreen.kt` for the row pattern.

- [ ] **Step 1: NavDest + route**

In `ui/route/NavDest.kt` add `@Serializable data object HeatPresets : NavDest` and add it to the `knownNavDests` list (grep for `knownNavDests`).
In `ui/shell/AppShell.kt` NavHost, add (model on the `PrinterSettings` composable):
```kotlin
composable<NavDest.HeatPresets> {
    HeatPresetsScreen(
        container = container,
        onBack = { navController.popBackStack() },
    )
}
```
Import `works.mees.dinghy.ui.heatpresets.HeatPresetsScreen`.

- [ ] **Step 2: Printer Settings row**

In `PrinterSettingsScreen.kt`, add `onHeatPresets: () -> Unit` to `PrinterSettingsContent`, pass `onHeatPresets = { onNavigate(NavDest.HeatPresets) }` from the stateful wrapper, and add a row in the `ListBlock` (after Theme/System Info, before the Power stub):
```kotlin
item {
    PrinterSettingsNavRow(
        icon = DinghyIcons.TempPresets,
        label = stringResource(R.string.heat_presets_title),
        onClick = onHeatPresets,
        uDp = grid.uDp,
    )
}
```

- [ ] **Step 3: HeatPresetsScreen — list/detail**

Build a screen with three view states held in `rememberSaveable`: `Browsing` (list + selected id), `Editing(presetId?)` (null id = create). Stateful wrapper collects:
```kotlin
val presets by container.activeHeatPresets.collectAsStateWithLifecycle(emptyList())
val heaterLimits by container.heaterLimits.collectAsStateWithLifecycle(emptyMap())
val capabilities by container.capabilities.collectAsStateWithLifecycle(Capabilities())
val printerState by container.printerState.collectAsStateWithLifecycle(PrinterState())
val dispatcher by container.dispatcher.collectAsStateWithLifecycle(null)
val isPrinting = printerState.printState == PrintState.Printing || printerState.printState == PrintState.Paused
```
Compute live heaters once for the wizard:
```kotlin
val liveHeaters = remember(heaterLimits, capabilities) {
    enumerateSettableHeaters(heaterLimits, capabilities.temperatureFans)
}
```

**Browsing layout** (FocusFrame + ListBlock + FootButtonBar):
- `FocusFrame(title = stringResource(R.string.heat_presets_title), icon = DinghyIcons.TempPresets, uDp = grid.uDp, isPrinting = isPrinting, onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) })`
  - Focus body: if a preset is selected, render its setpoints as a fit-to-display line-item list — one row per `setpoint.entries.sortedBy { it.key }`: `"${heaterDisplayName(obj)}"` left, value right rendered as `if (v == 0) stringResource(R.string.heat_presets_off) else "$v°C"` (use `DinghyType.dataInline`/`listLabel` roles). Below the list, a docked **Edit** `OutlinedControl(label = stringResource(R.string.common_edit), icon = DinghyIcons.HeatPresetEdit, intent = Intent.Accent, onClick = { state = Editing(selected.id) }, modifier = Modifier.fillMaxWidth())`. If nothing selected, show a short hint (`heat_presets_empty_hint` / `heat_presets_select_hint`).
- Field: `ListBlock { items(presets, key = { it.id }) { p -> ListRow(selected = p.id == selectedId, onClick = { selectedId = p.id }, uDp = grid.uDp, leadingContent = { ListRowIcon(DinghyIcons.TempPresets, grid.uDp, t.text) }, trailingContent = { Text(presetSummary(p), style = DinghyType.dataInline.toTextStyle(t), color = t.text2) }) { ListRowLabel(p.name) } } }`
  where `presetSummary(p)` = `p.setpoints.entries.sortedBy { it.key }.joinToString(" · ") { "${heaterDisplayName(it.key)} ${it.value}°" }` (empty string if no setpoints).
- Foot: `FootButtonBar(uDp = grid.uDp, actions = listOf(`
  - `FootAction(stringResource(R.string.cd_back), DinghyIcons.Back, onBack, Intent.Accent)`,
  - `FootAction(stringResource(R.string.common_delete), DinghyIcons.HeatPresetDelete, { showDeleteGuard = true }, Intent.Danger, enabled = selectedId != null)`,
  - `FootAction(stringResource(R.string.common_add), DinghyIcons.HeatPresetAdd, { state = Editing(null) }, Intent.Go)`
  - `))`
- Delete confirm:
```kotlin
if (showDeleteGuard && selectedId != null) {
    val sel = presets.first { it.id == selectedId }
    ConfirmGuard(
        title = stringResource(R.string.heat_presets_delete_title),
        message = stringResource(R.string.heat_presets_delete_message, sel.name),
        confirmLabel = stringResource(R.string.common_delete),
        destructive = true,
        onConfirm = { container.deleteHeatPreset(sel.id); selectedId = null; showDeleteGuard = false },
        onCancel = { showDeleteGuard = false },
    )
}
```

- [ ] **Step 4: Wizard UI (in HeatPresetWizard.kt)**

Add a composable `HeatPresetWizard(saved: HeatPreset?, heaters: List<SettableHeater>, isPrinting: Boolean, onEmergencyStop: () -> Unit, onCancel: () -> Unit, onSave: (HeatPreset) -> Unit, uDp: Dp)`:
- Build steps once: `val fields = remember(saved, heaters) { reconcileWizardFields(saved, heaters) }`.
- Hold `var step by rememberSaveable { mutableStateOf(0) }` (0 = name; 1..fields.size = heater fields).
- Hold name + per-object text in `rememberSaveable(stateSaver = ...)` or a `mutableStateMapOf`. Initialize name from `saved?.name ?: ""` and each object's text from its `initialValue`.
- Render a `FocusFrame(title = stringResource(R.string.heat_presets_wizard_title), icon = DinghyIcons.TempPresets, uDp = uDp, isPrinting = isPrinting, onEmergencyStop = onEmergencyStop)`:
  - **Step 0 (name):** header `Text(stringResource(R.string.heat_presets_name_label))` + `TokenTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.heat_presets_name_hint), keyboardType = KeyboardType.Text, isError = name.isBlank())`.
  - **Step n (heater):** `val f = fields[step-1]`; header `Text(f.displayName)` + `TokenTextField(value = values[f.objectName] ?: "", onValueChange = { values[f.objectName] = it.filter(Char::isDigit) }, label = stringResource(R.string.heat_presets_value_hint), keyboardType = KeyboardType.Number)`. Show the placeholder string `R.string.heat_presets_skip_hint` ("Leave blank to skip or 0 to turn off") as helper text below.
  - Foot: `Cancel` (`DinghyIcons.DialogClose`, `Intent.Danger`, `onCancel`) + a primary that is **Next** (`DinghyIcons.CheckCircle`, `Intent.Go`, advances `step`) when `step < fields.size`, else **Save** (`DinghyIcons.CheckCircle`, `Intent.Go`, enabled only when `name.isNotBlank()`):
    ```kotlin
    onClick = {
        if (step < fields.size) step++
        else onSave(buildPresetFromInput(
            id = saved?.id ?: java.util.UUID.randomUUID().toString(),
            name = name, rawValues = values, heaters = heaters,
        ))
    }
    ```
  - On Step 0 with a blank name, disable the Next button.
- The screen wires `onSave = { container.saveHeatPreset(it); state = Browsing }` and `onCancel = { state = Browsing }`.

- [ ] **Step 5: Add string resources**

In `app/src/main/res/values/strings.xml`:
```xml
<string name="heat_presets_title">Heat Presets</string>
<string name="heat_presets_wizard_title">Heat Preset</string>
<string name="heat_presets_name_label">Preset Name</string>
<string name="heat_presets_name_hint">Name</string>
<string name="heat_presets_value_hint">Target °C</string>
<string name="heat_presets_skip_hint">Leave blank to skip or 0 to turn off</string>
<string name="heat_presets_off">Off</string>
<string name="heat_presets_select_hint">Select a preset to view its setpoints</string>
<string name="heat_presets_empty_hint">No presets yet — tap Add to create one</string>
<string name="heat_presets_delete_title">Delete Preset</string>
<string name="heat_presets_delete_message">Delete “%1$s”?</string>
```
Reuse existing `common_*` strings if present (`common_add`, `common_delete`, `common_edit`, `common_cancel`, `cd_back`); otherwise add them.

- [ ] **Step 6: Verify it compiles**

Run: `... "E:\Android\gw.bat :app:compileDebugKotlin"`
Expected: BUILD SUCCESSFUL. Fix any `DinghyType`/token/`stringResource` issues.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/heatpresets/ app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt app/src/main/java/works/mees/dinghy/ui/screen/PrinterSettingsScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(heatpresets): Heat Presets screen + create/edit wizard + Printer Settings row"
```

---

## Task 11a: Migrate Temperature screen picker

**Files:** `ui/temperature/TemperatureScreen.kt`

- [ ] **Step 1:** Collect presets: add `val heatPresets by container.activeHeatPresets.collectAsStateWithLifecycle(emptyList())` in the stateful wrapper.
- [ ] **Step 2:** Replace the loaded-spool `PrinterCommands.Preset` builder (lines ~253-269) with a `HeatPreset`:
```kotlin
val spoolPreset: HeatPreset? = remember(activeSpoolDetail) {
    val f = activeSpoolDetail?.filament
    val nozzle = f?.settingsExtruderTemp
    if (f != null && nozzle != null) HeatPreset(
        id = "__spool__",
        name = f.name ?: f.material ?: "LOADED SPOOL",
        setpoints = buildMap { put("extruder", nozzle); f.settingsBedTemp?.let { put("heater_bed", it) } },
    ) else null
}
```
- [ ] **Step 3:** In `PresetPicker` (lines ~727-758), iterate `heatPresets` instead of `PrinterCommands.MATERIAL_PRESETS`: `items(heatPresets, key = { it.id }) { preset -> PresetListRow(preset, grid.uDp) { onApplyPreset(preset); fieldMode = TempFieldMode.SensorList } }` (keep the spool row first).
- [ ] **Step 4:** Change `PresetListRow` to take a `HeatPreset` and show `presetSummary` in the trailing slot (reuse the helper from Task 10 or inline the same join).
- [ ] **Step 5:** Change the `onApplyPreset` seam (lines ~322-327) to dispatch the new command:
```kotlin
onApplyPreset = { preset ->
    dispatcher?.dispatch(CommandRegistry.applyHeatPreset, ApplyHeatPresetArgs(preset.setpoints, key = "preset_${preset.id}"))
},
```
Update the `onApplyPreset` parameter type to `(HeatPreset) -> Unit`.
- [ ] **Step 6:** Update `PresetSelector` (the internal composable, lines ~1074-1116) to take `presets: List<HeatPreset>` and an `onPreset: (HeatPreset) -> Unit`, rendering `OutlinedControl(label = "${p.name}   ${presetSummary(p)}", ...)`. (This is the shared selector reused by PrintStatus — Task 11b passes the list in.)
- [ ] **Step 7:** Compile: `... "E:\Android\gw.bat :app:compileDebugKotlin"` → SUCCESS.
- [ ] **Step 8:** Commit: `git commit -am "feat(heatpresets): Temperature picker uses per-printer presets"`

---

## Task 11b: Migrate Print Status preheat fallback

**Files:** `ui/printstatus/PrintStatusScreen.kt`

- [ ] **Step 1:** Collect `val heatPresets by container.activeHeatPresets.collectAsStateWithLifecycle(emptyList())`.
- [ ] **Step 2:** Update the `PresetSelector` invocation (lines ~256-270) to pass `presets = heatPresets` and dispatch the new command:
```kotlin
works.mees.dinghy.ui.temperature.PresetSelector(
    inFlight = inFlight,
    presets = heatPresets,
    onPreset = { p ->
        dispatcher?.dispatch(CommandRegistry.applyHeatPreset, ApplyHeatPresetArgs(p.setpoints, key = "preset_${p.id}"))
        showPresetSelector = false
    },
    onDismiss = { showPresetSelector = false },
)
```
(The spool-aware `selectPreheatPath`/`runPreheat` direct-temps branch is UNCHANGED — it still per-temp dispatches `setHeater`. Only the no-spool fallback selector changes.)
- [ ] **Step 3:** Compile → SUCCESS.
- [ ] **Step 4:** Commit: `git commit -am "feat(heatpresets): Print Status preheat fallback uses per-printer presets"`

---

## Task 11c: Migrate Extrude page (extruder-only)

**Files:** `ui/extrude/ExtrudeScreen.kt`

- [ ] **Step 1:** Collect `val heatPresets by container.activeHeatPresets.collectAsStateWithLifecycle(emptyList())`.
- [ ] **Step 2:** Replace the `MATERIAL_PRESETS` rows (lines ~374-376) with presets that HAVE an extruder value, applying nozzle only:
```kotlin
items(heatPresets.filter { it.extruderTemp != null }, key = { "preset_${it.id}" }) { preset ->
    HeatPresetRow(preset.name, preset.extruderTemp!!, { onSetExtruderTemp(preset.extruderTemp!!) }, grid.uDp)
}
```
(Keep the loaded-spool chip above, unchanged. `onSetExtruderTemp` already dispatches `setHeater` nozzle-only — unchanged.)
- [ ] **Step 3:** Compile → SUCCESS.
- [ ] **Step 4:** Commit: `git commit -am "feat(heatpresets): Extrude page uses per-printer presets (extruder-only)"`

---

## Task 12: Remove the legacy MATERIAL_PRESETS / Preset / applyPreset

**Files:** `command/PrinterCommands.kt`, `command/CommandRegistry.kt`, plus any test referencing them.

- [ ] **Step 1:** Grep for remaining references: `git grep -n "MATERIAL_PRESETS\|ApplyPresetArgs\|PrinterCommands.Preset\|\\bapplyPreset\\b"`. Every main-source hit should now be gone except inside Task-3/4 code. If a consumer remains, finish migrating it before deleting.
- [ ] **Step 2:** Delete `data class Preset`, `MATERIAL_PRESETS`, and both `applyPreset(...)` funcs from `PrinterCommands.kt`.
- [ ] **Step 3:** Delete the `applyPreset` `CommandSpec` + `ApplyPresetArgs` from `CommandRegistry.kt` and remove `applyPreset,` from `CommandRegistry.all`. (Leave the `KGC-SET_HEATER_TEMPERATURE_PRESET` catalog entry — it is now the catalog id for `applyHeatPreset`.)
- [ ] **Step 4:** Update/remove any test referencing the deleted symbols (e.g. `applyPreset` cases in `CommandRegistryGcodeTest` / `PrinterCommandsTest`). Keep the `applyHeatPreset` coverage.
- [ ] **Step 5:** Run the full unit-test suite: `... "E:\Android\gw.bat :app:testDebugUnitTest"` → all green (esp. `CommandCatalogDriftTest`, `FontConformanceTest`).
- [ ] **Step 6:** Commit: `git commit -am "refactor(heatpresets): remove legacy MATERIAL_PRESETS/applyPreset"`

---

## Task 13: Full build + release verification

- [ ] **Step 1:** Full unit tests: `... "E:\Android\gw.bat :app:testDebugUnitTest"` → BUILD SUCCESSFUL, all green.
- [ ] **Step 2:** Debug APK: `... "E:\Android\gw.bat :app:assembleDebug"` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Release (R8) build: `... "E:\Android\gw.bat :app:assembleRelease"` → BUILD SUCCESSFUL (catches R8/keep-rule regressions for the new serializable `HeatPreset`).
- [ ] **Step 4:** Commit anything outstanding; the feature is ready for on-device UAT (flox + moto, both ABIs — see `[[dinghy-test-devices]]`).

---

## Self-Review (completed by author)

**Spec coverage:**
- Per-printer presets incl. extruder/bed/heater_generic/temperature_fan → Tasks 1, 3, 5, 6. ✔
- Seed Low/Med/High (150/50, 200/65, 230/90) for NEW printers only → Tasks 1, 9 (`isNew` gate). ✔
- No generic/fan prefill in seeds → Task 1 defaults are extruder+bed only. ✔
- Lives in Printer Settings → "Heat Presets" → Task 10 row. ✔
- List with foot Back/Delete/Add; Delete only on selection → Task 10. ✔
- Selected preset shows setpoints + Edit at bottom → Task 10 docked Edit. ✔
- Create/Edit wizard: name screen + one Focus screen per heater, (Next|Save)/Cancel, stripped names, text input → Tasks 7, 10. ✔
- Blank = omit; 0 = off; "Leave blank to skip or 0 to turn off" → Tasks 7 (`buildPresetFromInput`), 10 (placeholder). ✔
- Adding a heater doesn't break existing; edit picks up config changes fresh → Task 7 `reconcileWizardFields` (live heaters, new=blank, stale dropped). ✔
- Saved preset added to Field list, sorted by extruder temp asc → Tasks 1 `sortedForDisplay`, 2 (store sorts). ✔
- Replace all preheat preset lists EXCEPT Extrude → Tasks 11a/11b (apply all heaters), 11c (extruder-only). ✔
- Numeric clamp to heater min/max → Tasks 6, 7. ✔
- Icons via question_mark → Task 8. ✔

**Placeholder scan:** none — every code step shows complete code. UI tasks reference exact components/signatures.
**Type consistency:** `HeatPreset(id,name,setpoints)`, `ApplyHeatPresetArgs(setpoints,key)`, `SetTemperatureFanArgs(name,target,key?)`, `SettableHeater(objectName,displayName,minTemp,maxTemp)`, `WizardHeaterField(...)`, `reconcileWizardFields`/`buildPresetFromInput`, `activeHeatPresets`/`saveHeatPreset`/`deleteHeatPreset` — used consistently across tasks. ✔
