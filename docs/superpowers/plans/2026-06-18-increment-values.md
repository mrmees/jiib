# Per-Printer Increment Values Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the discrete step-selector value lists (13 Fine-Tune params, Move Microstep, Print Babystep, Probe Z-Test) per-printer configurable, seeded from the current hardcoded "jiib" defaults, edited from a new "Increment Values" door in Printer Settings.

**Architecture:** Mirror the Heat Presets feature: a per-printer `IncrementListPrefs` DataStore (13th file) keyed `increments_<profileId>` storing `Map<controlKey,String>`; a single `IncrementControls.ALL` registry that derives Fine-Tune entries from `ALL_FINE_TUNE_PARAMS` (zero drift) and lists the three unlimited controls; an `AppContainer.activeIncrementLists` Flow resolving stored-or-default per active profile; consuming selectors read the resolved list instead of a hardcoded constant.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX DataStore (Preferences), kotlinx.serialization, JUnit.

**Build/test commands (Windows-side, run from repo root):**
- Unit tests: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'`
- Single test class: append `--tests "works.mees.dinghy.config.IncrementListParsingTest"`
- Assemble: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
- Ligature check: `python tools/verify_ligatures.py`

**Conventions:** No raw colors (LocalTokens only). Type via `DinghyType.<role>.toTextStyle(t)` — no inline `fontSize`/`fontFamily`. Never auto-pick an icon. Commit after every green step.

---

## File Structure

**New files:**
- `app/src/main/java/works/mees/dinghy/config/IncrementListParsing.kt` — pure parse/format/validate of the comma string.
- `app/src/test/java/works/mees/dinghy/config/IncrementListParsingTest.kt`
- `app/src/main/java/works/mees/dinghy/ui/increments/IncrementControls.kt` — the control registry (`IncrementControlSpec` + `ALL` + default helpers).
- `app/src/test/java/works/mees/dinghy/ui/increments/IncrementControlsTest.kt`
- `app/src/main/java/works/mees/dinghy/config/IncrementListPrefs.kt` — per-printer DataStore store.
- `app/src/test/java/works/mees/dinghy/config/IncrementListPrefsTest.kt`
- `app/src/main/java/works/mees/dinghy/ui/increments/IncrementValuesScreen.kt` — the settings UI (top list + Fine-Tune submenu + edit Focus).

**Modified files:**
- `app/src/main/java/works/mees/dinghy/DinghyApp.kt` — create the 13th DataStore file; pass to container.
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — ctor param, prefs field, `activeIncrementLists`/`activeIncrementStrings` flows, `saveIncrementList` helper, seed in `saveProfile`.
- `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — add `QuestionMark` placeholder (babystep).
- `app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt` — `IncrementValues` route + `knownNavDests`.
- `app/src/main/java/works/mees/dinghy/ui/screen/PrinterSettingsScreen.kt` — new "Increment Values" row + `onIncrementValues` param.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — wire the route + the PrinterSettings callback.
- `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt` — read active lists; build active params; re-key selected step.
- `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` — microstep list from active lists + clamp.
- `app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt` — TESTZ list from active lists + value-rebase.
- `app/src/main/res/values/strings.xml` — new string resources.

---

## Task 1: Increment list parsing + validation (pure core, TDD)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/config/IncrementListParsing.kt`
- Test: `app/src/test/java/works/mees/dinghy/config/IncrementListParsingTest.kt`

Rules (from spec §Edit Focus): allowed chars `[0-9 . , space]`; strip spaces; split on `,`; reject empty tokens; each token a number `> 0` with at most one `.`; at least 1 value; for a fixed-count control exactly N values. Store/echo verbatim (post-space-strip) so `0.001` survives.

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementListParsingTest {

    @Test fun `filter strips disallowed chars, keeps digits dot comma space`() {
        assertEquals("1,5.5, 10", filterIncrementInput("1a,5.5x, 10!"))
    }

    @Test fun `canonical strips spaces, preserves token text and order`() {
        val r = parseIncrementInput("10, 5 , 1", maxCount = null) as IncrementParse.Ok
        assertEquals("10,5,1", r.canonical)
        assertEquals(listOf(10.0, 5.0, 1.0), r.values)
    }

    @Test fun `decimal precision preserved verbatim`() {
        val r = parseIncrementInput("0.001,0.005,0.01", maxCount = 3) as IncrementParse.Ok
        assertEquals("0.001,0.005,0.01", r.canonical)
        assertEquals(0.001, r.values[0], 0.0)
    }

    @Test fun `empty token rejected`() {
        assertTrue(parseIncrementInput("1,,5", maxCount = null) is IncrementParse.Error)
        assertTrue(parseIncrementInput("1,5,", maxCount = null) is IncrementParse.Error)
    }

    @Test fun `non-positive rejected`() {
        assertTrue(parseIncrementInput("0,5", maxCount = null) is IncrementParse.Error)
        assertTrue(parseIncrementInput("-1,5", maxCount = null) is IncrementParse.Error)
    }

    @Test fun `multiple decimal points rejected`() {
        assertTrue(parseIncrementInput("1.2.3", maxCount = null) is IncrementParse.Error)
    }

    @Test fun `at least one value required`() {
        assertTrue(parseIncrementInput("", maxCount = null) is IncrementParse.Error)
        assertTrue(parseIncrementInput("   ", maxCount = null) is IncrementParse.Error)
    }

    @Test fun `fixed count enforced`() {
        assertTrue(parseIncrementInput("1,5", maxCount = 3) is IncrementParse.Error)
        assertTrue(parseIncrementInput("1,5,10,25", maxCount = 3) is IncrementParse.Error)
        assertTrue(parseIncrementInput("1,5,10", maxCount = 3) is IncrementParse.Ok)
    }

    @Test fun `formatList renders canonical comma string with trimmed zeros`() {
        assertEquals("0.001,0.05,1,10", formatIncrementList(listOf(0.001, 0.05, 1.0, 10.0)))
    }
}
```

- [ ] **Step 2: Run the test, verify it fails to compile (symbols undefined)**

Run: `… "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --tests works.mees.dinghy.config.IncrementListParsingTest"`
Expected: FAIL — unresolved references `filterIncrementInput`, `parseIncrementInput`, `IncrementParse`, `formatIncrementList`.

- [ ] **Step 3: Implement the module**

```kotlin
package works.mees.dinghy.config

/** Result of parsing a user increment string. */
sealed interface IncrementParse {
    /** Valid: [canonical] is the space-stripped verbatim string; [values] the parsed doubles in order. */
    data class Ok(val canonical: String, val values: List<Double>) : IncrementParse
    /** Invalid: [reason] is a short user-facing message. */
    data class Error(val reason: String) : IncrementParse
}

/** Keystroke filter: keep only digits, '.', ',', and space; drop everything else. */
fun filterIncrementInput(raw: String): String =
    raw.filter { it.isDigit() || it == '.' || it == ',' || it == ' ' }

/**
 * Parse a user increment string into canonical form + values, or an error.
 *  - spaces stripped; split on ',' ; tokens kept verbatim (so "0.001" is preserved).
 *  - each token: non-blank, at most one '.', parses as a finite Double > 0.
 *  - at least one value; when [maxCount] != null, EXACTLY [maxCount] values (fixed-count controls).
 */
fun parseIncrementInput(raw: String, maxCount: Int?): IncrementParse {
    val stripped = raw.replace(" ", "")
    if (stripped.isEmpty()) return IncrementParse.Error("Enter at least one value")
    val tokens = stripped.split(",")
    val values = ArrayList<Double>(tokens.size)
    for (tok in tokens) {
        if (tok.isEmpty()) return IncrementParse.Error("Empty value — check the commas")
        if (tok.count { it == '.' } > 1) return IncrementParse.Error("'$tok' is not a number")
        val v = tok.toDoubleOrNull()
        if (v == null || !v.isFinite()) return IncrementParse.Error("'$tok' is not a number")
        if (v <= 0.0) return IncrementParse.Error("Values must be greater than 0")
        values.add(v)
    }
    if (maxCount != null && values.size != maxCount) {
        return IncrementParse.Error("This control needs exactly $maxCount values")
    }
    return IncrementParse.Ok(canonical = tokens.joinToString(","), values = values)
}

/** Render a value list to the canonical comma string (whole numbers lose the ".0"; zeros trimmed). */
fun formatIncrementList(values: List<Double>): String =
    values.joinToString(",") { v ->
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else v.toBigDecimal().stripTrailingZeros().toPlainString()
    }
```

- [ ] **Step 4: Run the test, verify PASS**

Run the same command from Step 2. Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/config/IncrementListParsing.kt app/src/test/java/works/mees/dinghy/config/IncrementListParsingTest.kt
git commit -m "feat(increments): pure parse/validate/format for increment value lists"
```

---

## Task 2: Control registry (`IncrementControls`)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/increments/IncrementControls.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/increments/IncrementControlsTest.kt`

Fine-Tune entries DERIVE from `ALL_FINE_TUNE_PARAMS` (icon/name/steps reused → zero drift). The three unlimited entries are listed explicitly; babystep's default references `PrinterCommands.BABYSTEP_STEPS` so it can't drift from the (deferred) consumer.

- [ ] **Step 0: Add the `QuestionMark` placeholder icon FIRST (registry depends on it)**

The registry references `DinghyIcons.QuestionMark`, which does not exist yet, so it must be added before this task's code compiles. In `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` (inside `object DinghyIcons`), add near the other utility glyphs:

```kotlin
    /** Placeholder for controls without a chosen icon yet — babystep increment row (UAT review). */
    val QuestionMark = DinghyIcon(IconRef.Ligature("question_mark"), alternate = "question_mark")
```

**Also add `QuestionMark` to the `DinghyIcons.all` list** (~line 366) — the registry's hand-rolled
iteration source for `tools/subset-symbols` and the uniqueness test. Append `QuestionMark,` to any line
of that `listOf(...)`. Omitting it means the new glyph is never subset into the font and `all`-based
tests miss it.

Then run `python tools/verify_ligatures.py` — expected PASS. If it reports `question_mark` missing from the font subset, STOP and flag to the owner (icon law: do not substitute a different glyph).

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.ui.increments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.ui.finetune.ALL_FINE_TUNE_PARAMS

class IncrementControlsTest {

    @Test fun `has 16 controls — 13 fine-tune plus 3 unlimited`() {
        assertEquals(16, IncrementControls.ALL.size)
        assertEquals(13, IncrementControls.ALL.count { it.group == "Fine-Tune" })
    }

    @Test fun `fine-tune entries mirror the param descriptors exactly`() {
        ALL_FINE_TUNE_PARAMS.forEach { p ->
            val spec = IncrementControls.ALL.firstOrNull { it.key == p.tuner.name }
            assertNotNull("missing spec for ${p.tuner}", spec)
            assertEquals(p.name, spec!!.controlTitle)
            assertEquals(p.steps.toList(), spec.defaultValues)
            assertEquals(3, spec.maxCount)
            assertEquals(p.icon, spec.icon)
        }
    }

    @Test fun `unlimited controls have null maxCount`() {
        listOf("move_microstep", "babystep", "probe_testz").forEach { key ->
            assertNull(IncrementControls.ALL.first { it.key == key }.maxCount)
        }
    }

    @Test fun `babystep default mirrors the command constant`() {
        assertEquals(
            PrinterCommands.BABYSTEP_STEPS.toList(),
            IncrementControls.ALL.first { it.key == "babystep" }.defaultValues,
        )
    }

    @Test fun `keys are unique`() {
        val keys = IncrementControls.ALL.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun `defaultStringMap is canonical strings for every key`() {
        val map = IncrementControls.defaultStringMap()
        assertEquals(16, map.size)
        assertEquals("0.001,0.005,0.01", map["PRESSURE_ADVANCE"])
        assertTrue(map.values.none { it.isBlank() })
    }
}
```

- [ ] **Step 2: Run the test → FAIL** (unresolved `IncrementControls`).

Run: `… --tests works.mees.dinghy.ui.increments.IncrementControlsTest`

- [ ] **Step 3: Implement the registry**

```kotlin
package works.mees.dinghy.ui.increments

import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.config.formatIncrementList
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.ui.finetune.ALL_FINE_TUNE_PARAMS

/**
 * One user-configurable increment selector.
 *
 * @param key           stable persistence key (FineTuneTuner name, or "move_microstep"/"babystep"/"probe_testz").
 * @param group         display group ("Fine-Tune", "Move", "Print", "Calibrate").
 * @param controlTitle  per-control label when the group has multiple selectors (Fine-Tune); null otherwise.
 * @param icon          the control's existing Focus-header icon (never auto-picked).
 * @param maxCount      fixed value count (3 = Fine-Tune) or null = unlimited (min 1).
 * @param defaultValues the jiib default step list.
 */
data class IncrementControlSpec(
    val key: String,
    val group: String,
    val controlTitle: String?,
    val icon: DinghyIcon,
    val maxCount: Int?,
    val defaultValues: List<Double>,
)

object IncrementControls {

    /** The 13 Fine-Tune controls, derived from the live param descriptors (single source of truth). */
    private val fineTune: List<IncrementControlSpec> = ALL_FINE_TUNE_PARAMS.map { p ->
        IncrementControlSpec(
            key = p.tuner.name,
            group = "Fine-Tune",
            controlTitle = p.name,
            icon = p.icon,
            maxCount = 3,
            defaultValues = p.steps.toList(),
        )
    }

    /** The three unlimited (min-1) controls. Babystep is setting-only this phase (no live selector). */
    private val unlimited: List<IncrementControlSpec> = listOf(
        IncrementControlSpec(
            key = "move_microstep", group = "Move", controlTitle = null,
            icon = DinghyIcons.FineTune, maxCount = null,
            defaultValues = listOf(0.01, 0.025, 0.1, 0.25, 1.0, 2.5, 10.0),
        ),
        IncrementControlSpec(
            // Babystep has NO existing Focus icon (no live selector yet) → question_mark placeholder,
            // flagged for owner UAT (spec §Icons). Default mirrors PrinterCommands.BABYSTEP_STEPS.
            key = "babystep", group = "Print", controlTitle = null,
            icon = DinghyIcons.QuestionMark, maxCount = null,
            defaultValues = PrinterCommands.BABYSTEP_STEPS.toList(),
        ),
        IncrementControlSpec(
            key = "probe_testz", group = "Calibrate", controlTitle = null,
            icon = DinghyIcons.RoutineProbeCalibrate, maxCount = null,
            defaultValues = listOf(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 5.0, 10.0),
        ),
    )

    val ALL: List<IncrementControlSpec> = fineTune + unlimited

    fun specFor(key: String): IncrementControlSpec? = ALL.firstOrNull { it.key == key }

    /** key → canonical default string, for new-printer seeding. */
    fun defaultStringMap(): Map<String, String> =
        ALL.associate { it.key to formatIncrementList(it.defaultValues) }

    /** key → default value list, for read-path fallback. */
    fun defaultValueMap(): Map<String, List<Double>> =
        ALL.associate { it.key to it.defaultValues }
}
```

> NOTE for executor: confirm `DinghyIcons.RoutineProbeCalibrate` is the exact name (grep `RoutineProbeCalibrate` in `CalibrationHubScreen.kt`). If the import resolves, you're good. `QuestionMark` is added in Task 5 — if Tasks run out of order, add the icon line first or this won't compile.

- [ ] **Step 4: Run the test → PASS.**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/increments/IncrementControls.kt app/src/test/java/works/mees/dinghy/ui/increments/IncrementControlsTest.kt
git commit -m "feat(increments): control registry derived from fine-tune params + 3 unlimited"
```

---

## Task 3: Per-printer `IncrementListPrefs` DataStore

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/config/IncrementListPrefs.kt`
- Test: `app/src/test/java/works/mees/dinghy/config/IncrementListPrefsTest.kt`

Stores `Map<controlKey, canonicalString>` JSON-encoded under `increments_<profileId>`. Direct analog of `HeatPresetPrefs`. Look at `app/src/test/java/works/mees/dinghy/config/HeatPresetPrefsTest.kt` for the in-memory DataStore test harness and copy its setup.

- [ ] **Step 1: Write the failing test** (adapt the HeatPresetPrefs test harness)

All tests use an in-memory DataStore — back-to-back writes on a temp-file store are unreliable on
Windows (atomic `.tmp`→rename race). This mirrors `HeatPresetPrefsTest.newMemPrefs()`.

```kotlin
package works.mees.dinghy.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementListPrefsTest {

    /** In-memory DataStore for multi-write tests (sidesteps the Windows .tmp→rename race). */
    private fun newMemPrefs(): IncrementListPrefs {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        val mem = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = state
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val next = transform(state.value); state.value = next; return next
            }
        }
        return IncrementListPrefs(mem)
    }

    @Test fun `absent key reads empty`() = runTest {
        assertEquals(emptyMap<String, String>(), newMemPrefs().lists("p1").first())
    }

    @Test fun `setList round-trips`() = runTest {
        val prefs = newMemPrefs()
        prefs.setList("p1", "move_microstep", "0.1,1,10")
        assertEquals(mapOf("move_microstep" to "0.1,1,10"), prefs.lists("p1").first())
    }

    @Test fun `setList overwrites only its key`() = runTest {
        val prefs = newMemPrefs()
        prefs.setList("p1", "SPEED", "1,5,10")
        prefs.setList("p1", "SPEED", "2,4,6")
        prefs.setList("p1", "FLOW", "1,5,10")
        assertEquals(mapOf("SPEED" to "2,4,6", "FLOW" to "1,5,10"), prefs.lists("p1").first())
    }

    @Test fun `profiles are isolated`() = runTest {
        val prefs = newMemPrefs()
        prefs.setList("p1", "SPEED", "1,5,10")
        prefs.setList("p2", "SPEED", "9,9,9")
        assertEquals(mapOf("SPEED" to "1,5,10"), prefs.lists("p1").first())
        assertEquals(mapOf("SPEED" to "9,9,9"), prefs.lists("p2").first())
    }

    @Test fun `seedIfEmpty only seeds when empty`() = runTest {
        val prefs = newMemPrefs()
        prefs.seedIfEmpty("p1", mapOf("SPEED" to "1,5,10"))
        prefs.setList("p1", "SPEED", "2,4,6")
        prefs.seedIfEmpty("p1", mapOf("SPEED" to "1,5,10")) // must NOT overwrite
        assertEquals("2,4,6", prefs.lists("p1").first()["SPEED"])
    }
}
```

- [ ] **Step 2: Run → FAIL** (`IncrementListPrefs` unresolved).

- [ ] **Step 3: Implement** (mirror `HeatPresetPrefs.kt`)

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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Per-printer increment step lists, persisted as a JSON `Map<controlKey, canonicalString>` under a
 * profile-scoped key (`increments_<profileId>`). Mirrors [HeatPresetPrefs]. The 13th, independent
 * DataStore file (increments.preferences_pb). All writes are read-modify-write inside ONE edit; a
 * read error yields an empty map.
 */
class IncrementListPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /** The stored control→string map for [profileId] (absent keys mean "use the default"). */
    fun lists(profileId: String): Flow<Map<String, String>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> decode(prefs[key(profileId)]) }

    /** Set one control's canonical string under [profileId] (read-modify-write). */
    suspend fun setList(profileId: String, controlKey: String, canonical: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key(profileId)]).toMutableMap()
            current[controlKey] = canonical
            prefs[key(profileId)] = JSON.encodeToString(SERIALIZER, current)
        }
    }

    /** Seed [defaults] under [profileId] ONLY if it currently has none (new-printer seeding). */
    suspend fun seedIfEmpty(profileId: String, defaults: Map<String, String>) {
        dataStore.edit { prefs ->
            if (decode(prefs[key(profileId)]).isEmpty()) {
                prefs[key(profileId)] = JSON.encodeToString(SERIALIZER, defaults)
            }
        }
    }

    private fun decode(raw: String?): Map<String, String> =
        if (raw.isNullOrBlank()) emptyMap()
        else runCatching { JSON.decodeFromString(SERIALIZER, raw) }.getOrDefault(emptyMap())

    companion object {
        private fun key(profileId: String) = stringPreferencesKey("increments_$profileId")
        private val JSON = Json { ignoreUnknownKeys = true }
        private val SERIALIZER = MapSerializer(String.serializer(), String.serializer())
    }
}
```

- [ ] **Step 4: Run → PASS.**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/config/IncrementListPrefs.kt app/src/test/java/works/mees/dinghy/config/IncrementListPrefsTest.kt
git commit -m "feat(increments): per-printer IncrementListPrefs DataStore (13th file)"
```

---

## Task 4: Wire the store into DinghyApp + AppContainer (+ seed)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/DinghyApp.kt`
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`

- [ ] **Step 1: Create the 13th DataStore file in DinghyApp**

In `DinghyApp.kt`, after the `heatPresetDataStore` block (~line 139-142), add:

```kotlin
        // A THIRTEENTH, INDEPENDENT file: increments.preferences_pb (per-printer increment value lists).
        val incrementDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { applicationContext.preferencesDataStoreFile("increments.preferences_pb") },
        )
```

Then in the `AppContainer(...)` construction call (where `heatPresetDataStore = heatPresetDataStore,` is passed, ~line 156), add:

```kotlin
            incrementDataStore = incrementDataStore,
```

- [ ] **Step 2: Add the ctor param to AppContainer**

In `AppContainer.kt`, after the `heatPresetDataStore: DataStore<Preferences>,` ctor param (~line 163), add:

```kotlin
    /**
     * The THIRTEENTH, INDEPENDENT file: increments.preferences_pb (per-printer increment value lists).
     * Backs [IncrementListPrefs] (JSON map keyed `increments_<profileId>`). Created once in
     * [works.mees.dinghy.DinghyApp] and injected here (single-writer DataStore invariant).
     */
    incrementDataStore: DataStore<Preferences>,
```

- [ ] **Step 3: Add the prefs field + active flows + write helper**

After the Heat Presets block (`deleteHeatPreset`, ~line 478), add:

```kotlin
    /** Per-printer increment value lists store (13th file, increments.preferences_pb). */
    val incrementListPrefs: IncrementListPrefs = IncrementListPrefs(incrementDataStore)

    /** The active printer's RAW stored increment strings (absent keys = default), or empty. */
    val activeIncrementStrings: Flow<Map<String, String>> =
        activeProfileId.flatMapLatest { id ->
            if (id != null) incrementListPrefs.lists(id) else flowOf(emptyMap())
        }

    /**
     * The active printer's RESOLVED increment lists: every registry key → parsed stored list, or the
     * jiib default when absent/unparseable. Consuming selectors collect this and look up by key.
     */
    val activeIncrementLists: Flow<Map<String, List<Double>>> =
        activeIncrementStrings.map { stored ->
            IncrementControls.ALL.associate { spec ->
                // Defensive: validate against the control's own count rule; a malformed/wrong-length
                // stored value falls back to the jiib default rather than feeding a bad list to a selector.
                val parsed = stored[spec.key]?.let { raw ->
                    (parseIncrementInput(raw, spec.maxCount) as? IncrementParse.Ok)?.values
                }
                spec.key to (parsed ?: spec.defaultValues)
            }
        }

    /** Set one control's increment string for the active printer (durable; routes through writeScope). */
    fun saveIncrementList(controlKey: String, canonical: String) {
        writeScope.launch {
            val id = activeProfileId.first() ?: return@launch
            incrementListPrefs.setList(id, controlKey, canonical)
        }
    }
```

Add imports at the top of `AppContainer.kt`:

```kotlin
import works.mees.dinghy.config.IncrementListPrefs
import works.mees.dinghy.config.IncrementParse
import works.mees.dinghy.config.parseIncrementInput
import works.mees.dinghy.ui.increments.IncrementControls
```

- [ ] **Step 4: Seed on new-printer save**

In `saveProfile` (~line 227), add the increment seed next to the heat-preset seed:

```kotlin
            if (isNew) {
                heatPresetPrefs.seedIfEmpty(profile.id, defaultHeatPresets())
                incrementListPrefs.seedIfEmpty(profile.id, IncrementControls.defaultStringMap())
            }
```

- [ ] **Step 5: Build to verify wiring compiles**

Run: `… "E:\Android\gw.bat :app:assembleDebug --no-daemon"`
Expected: BUILD SUCCESSFUL.

> If `AppContainer` is constructed in any test fixture with positional args, the new ctor param breaks them (see memory `dinghy-heat-presets`: "new AppContainer ctor param breaks positional test ctors"). Run `:app:testDebugUnitTest` and fix any fixture by adding `incrementDataStore = <fake>` (mirror how the fixture passes `heatPresetDataStore`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/DinghyApp.kt app/src/main/java/works/mees/dinghy/di/AppContainer.kt
git commit -m "feat(increments): wire IncrementListPrefs + activeIncrementLists + seed on printer-add"
```

---

## Task 5: (folded into Task 2) — QuestionMark icon already added

The `QuestionMark` placeholder icon is added as **Task 2, Step 0** (the registry depends on it),
including the `python tools/verify_ligatures.py` check. This task is intentionally a no-op —
skip it. The babystep icon remains a UAT-review item (spec §Icons).

---

## Task 6: Navigation + Printer Settings entry

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterSettingsScreen.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the route**

In `NavDest.kt`, after `@Serializable data object HeatPresets : NavDest`:

```kotlin
    @Serializable data object IncrementValues        : NavDest
```

And append `NavDest.IncrementValues,` to the end of the `knownNavDests` list (and bump the count comment from 24 → 25).

- [ ] **Step 2: Add strings**

In `strings.xml`:

```xml
    <string name="increment_values_title">Increment Values</string>
    <string name="increment_values_finetune">Fine-Tune</string>
    <string name="increment_values_microstep">Microstep</string>
    <string name="increment_values_babystep">Babystep</string>
    <string name="increment_values_probe_testz">Probe Z Test</string>
    <string name="increment_values_select_hint">Select a control to edit its increment steps.</string>
    <string name="increment_values_input_hint">Comma-separated values</string>
    <string name="increment_values_input_label">Increment steps</string>
```

> Executor: confirm the Probe Z Test label matches the in-app probe-calibrate title; if it differs, use the existing label string instead (spec Open-UAT item).

- [ ] **Step 3: Add the Printer Settings row**

In `PrinterSettingsScreen.kt`, add a param to the composable signature (near `onHeatPresets: () -> Unit = {},`):

```kotlin
    onIncrementValues: () -> Unit = {},
```

And after the Heat Presets row item (~line 275), add:

```kotlin
                        // Row 4b: Increment Values — per-printer step-selector value lists
                        item {
                            PrinterSettingsNavRow(
                                icon = DinghyIcons.FineTune,
                                label = stringResource(R.string.increment_values_title),
                                onClick = onIncrementValues,
                                uDp = grid.uDp,
                            )
                        }
```

And where `PrinterSettingsScreen(...)` is invoked inside the same file (the wrapper that maps callbacks, near `onHeatPresets = { onNavigate(NavDest.HeatPresets) },` ~line 104), add:

```kotlin
        onIncrementValues = { onNavigate(NavDest.IncrementValues) },
```

- [ ] **Step 4: Wire the route in AppShell**

In `AppShell.kt`, after the `composable<NavDest.HeatPresets> { … }` block (~line 762), add:

```kotlin
            composable<NavDest.IncrementValues> {
                IncrementValuesScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
```

Add the import:

```kotlin
import works.mees.dinghy.ui.increments.IncrementValuesScreen
```

> `IncrementValuesScreen` is created in Task 7 — this won't compile until then. Implement Task 7 before building, or stub the screen first.

- [ ] **Step 5: Commit (after Task 7 builds green — or stage together).**

```bash
git add app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt app/src/main/java/works/mees/dinghy/ui/screen/PrinterSettingsScreen.kt app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt app/src/main/res/values/strings.xml
git commit -m "feat(increments): nav route + Printer Settings 'Increment Values' door"
```

---

## Task 7: `IncrementValuesScreen` — top list, Fine-Tune submenu, edit Focus

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/increments/IncrementValuesScreen.kt`

One file, three view states (mirrors `HeatPresetsScreen`'s sealed-state pattern): **TopList** (4 rows) → **FineTuneSubmenu** (13 rows) → **Editing(key)** (the edit Focus). Marquee long titles the same way `FocusFrame`/`PrintStatusField` do (`Modifier.basicMarquee()` on the label `Text`).

- [ ] **Step 1: Implement the screen**

```kotlin
package works.mees.dinghy.ui.increments

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.config.IncrementParse
import works.mees.dinghy.config.filterIncrementInput
import works.mees.dinghy.config.formatIncrementList
import works.mees.dinghy.config.parseIncrementInput
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.ui.screen.TokenTextField

/** Top-level rows (Fine-Tune opens a submenu; the rest open the editor directly). */
private val TOP_ROWS = listOf(
    IncrementControls.specFor("move_microstep")!!,
    IncrementControls.specFor("babystep")!!,
    IncrementControls.specFor("probe_testz")!!,
)
private val FINE_TUNE_ROWS = IncrementControls.ALL.filter { it.group == "Fine-Tune" }

/** The Increment Values settings screen. Reached from Printer Settings → "Increment Values". */
@Composable
fun IncrementValuesScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stored by container.activeIncrementStrings.collectAsStateWithLifecycle(emptyMap())
    val printerState by container.printerState.collectAsStateWithLifecycle(PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(null)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val estop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit); Unit }

    var view by rememberSaveable(stateSaver = IncViewSaver) { mutableStateOf<IncView>(IncView.TopList) }

    /** The current canonical string for [spec]: stored, else its default. */
    fun currentString(spec: IncrementControlSpec): String =
        stored[spec.key] ?: formatIncrementList(spec.defaultValues)

    when (val v = view) {
        is IncView.Editing -> {
            val spec = IncrementControls.specFor(v.key)
            if (spec == null) { view = IncView.TopList; return }
            IncrementEditFocus(
                spec = spec,
                initial = currentString(spec),
                isPrinting = isPrinting,
                onEmergencyStop = estop,
                onCancel = { view = if (spec.group == "Fine-Tune") IncView.FineTuneSubmenu else IncView.TopList },
                onSave = { canonical ->
                    container.saveIncrementList(spec.key, canonical)
                    view = if (spec.group == "Fine-Tune") IncView.FineTuneSubmenu else IncView.TopList
                },
                modifier = modifier,
            )
        }
        IncView.FineTuneSubmenu -> IncrementListView(
            title = stringResource(R.string.increment_values_finetune),
            icon = DinghyIcons.FineTune,
            rows = FINE_TUNE_ROWS,
            summaryFor = { currentString(it) },
            isPrinting = isPrinting,
            onEmergencyStop = estop,
            onRowClick = { view = IncView.Editing(it.key) },
            onBack = { view = IncView.TopList },
            modifier = modifier,
        )
        IncView.TopList -> IncrementListView(
            title = stringResource(R.string.increment_values_title),
            icon = DinghyIcons.FineTune,
            rows = TOP_ROWS,
            // The Fine-Tune entry is a NAV row (opens the submenu), not an editable control.
            extraNavRow = FineTuneNavRow,
            summaryFor = { currentString(it) },
            isPrinting = isPrinting,
            onEmergencyStop = estop,
            onRowClick = { spec ->
                view = if (spec === FineTuneNavRow) IncView.FineTuneSubmenu else IncView.Editing(spec.key)
            },
            onBack = onBack,
            modifier = modifier,
        )
    }
}

/** A synthetic "row" for the Fine-Tune submenu entry (not a real control; has no editable list). */
private val FineTuneNavRow = IncrementControlSpec(
    key = "__finetune_nav", group = "Fine-Tune", controlTitle = null,
    icon = DinghyIcons.FineTune, maxCount = null, defaultValues = emptyList(),
)

private sealed interface IncView {
    data object TopList : IncView
    data object FineTuneSubmenu : IncView
    data class Editing(val key: String) : IncView
}

/** Saver so the view state (incl. the edited key) survives rotation / process death. */
private val IncViewSaver = androidx.compose.runtime.saveable.Saver<IncView, String>(
    save = { v ->
        when (v) {
            IncView.TopList -> "T"
            IncView.FineTuneSubmenu -> "F"
            is IncView.Editing -> "E:${v.key}"
        }
    },
    restore = { raw ->
        when {
            raw == "F" -> IncView.FineTuneSubmenu
            raw.startsWith("E:") -> IncView.Editing(raw.removePrefix("E:"))
            else -> IncView.TopList
        }
    },
)

/** Shared Focus/Field list shell for the top list + the Fine-Tune submenu. */
@Composable
private fun IncrementListView(
    title: String,
    icon: works.mees.dinghy.designsystem.icons.DinghyIcon,
    rows: List<IncrementControlSpec>,
    summaryFor: (IncrementControlSpec) -> String,
    isPrinting: Boolean,
    onEmergencyStop: () -> Unit,
    onRowClick: (IncrementControlSpec) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraNavRow: IncrementControlSpec? = null,
) {
    val allRows = remember(rows, extraNavRow) { (listOfNotNull(extraNavRow) + rows) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = title,
                    icon = icon,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.increment_values_select_hint),
                            color = LocalTokens.current.text2,
                            style = DinghyType.body.toTextStyle(LocalTokens.current),
                        )
                    }
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    items(allRows, key = { it.key }) { spec ->
                        val t = LocalTokens.current
                        val isNav = spec === extraNavRow
                        ListRow(
                            selected = false,
                            onClick = { onRowClick(spec) },
                            uDp = grid.uDp,
                            leadingContent = { ListRowIcon(spec.icon, grid.uDp, t.text) },
                            trailingContent = {
                                if (!isNav) {
                                    Text(
                                        text = summaryFor(spec).replace(",", " / "),
                                        style = DinghyType.dataMeta.toTextStyle(t),
                                        color = t.text2,
                                    )
                                }
                            },
                        ) {
                            Text(
                                text = rowLabel(spec),
                                style = DinghyType.listLabel.toTextStyle(t),
                                color = t.text,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().basicMarquee(),
                            )
                        }
                    }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            icon = DinghyIcons.Back,
                            onClick = onBack,
                            intent = Intent.Accent,
                            contentDescription = stringResource(R.string.cd_back),
                        ),
                    ),
                )
            },
        )
    }
}

/** The edit Focus for one control: a filtered text field + live validation + Save. */
@Composable
private fun IncrementEditFocus(
    spec: IncrementControlSpec,
    initial: String,
    isPrinting: Boolean,
    onEmergencyStop: () -> Unit,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(spec.key) { mutableStateOf(initial) }
    val parse = parseIncrementInput(text, spec.maxCount)
    val valid = parse is IncrementParse.Ok
    val errorMsg = (parse as? IncrementParse.Error)?.reason

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = rowLabel(spec),
                    icon = spec.icon,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TokenTextField(
                            value = text,
                            onValueChange = { text = filterIncrementInput(it) },
                            label = stringResource(R.string.increment_values_input_label),
                            keyboardType = KeyboardType.Number,
                            isError = !valid,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = errorMsg ?: stringResource(R.string.increment_values_input_hint),
                            color = if (errorMsg != null) LocalTokens.current.stop else LocalTokens.current.text2,
                            style = DinghyType.caption.toTextStyle(LocalTokens.current),
                        )
                    }
                }
            },
            field = {
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            icon = DinghyIcons.Back,
                            onClick = onCancel,
                            intent = Intent.Accent,
                            contentDescription = stringResource(R.string.cd_back),
                        ),
                        FootAction(
                            label = stringResource(R.string.common_save),
                            icon = DinghyIcons.CheckCircle,
                            onClick = { (parse as? IncrementParse.Ok)?.let { onSave(it.canonical) } },
                            intent = Intent.Go,
                            enabled = valid,
                        ),
                    ),
                )
            },
        )
    }
}

/** Row/Focus label: the control title when present, else its group name. */
private fun rowLabel(spec: IncrementControlSpec): String = spec.controlTitle ?: when (spec.key) {
    "move_microstep" -> "Microstep"
    "babystep" -> "Babystep"
    "probe_testz" -> "Probe Z Test"
    "__finetune_nav" -> "Fine-Tune"
    else -> spec.group
}
```

> Executor: confirm `DinghyType.dataMeta`, `DinghyType.listLabel`, `DinghyType.body`, `DinghyType.caption` exist (they're used in `HeatPresetsScreen.kt`). Confirm `ListRowIcon(icon, uDp, color)` arg order against `HeatPresetsScreen.kt:166`. `basicMarquee` is experimental — add `@OptIn(ExperimentalFoundationApi::class)` on `IncrementListView` if the build flags it (check how `FocusFrame.kt` opts in).

- [ ] **Step 2: Build**

Run: `… "E:\Android\gw.bat :app:assembleDebug --no-daemon"`
Expected: BUILD SUCCESSFUL. Fix import/arg mismatches against the referenced `HeatPresetsScreen.kt` call sites.

- [ ] **Step 3: Commit** (together with Task 6's nav wiring)

```bash
git add app/src/main/java/works/mees/dinghy/ui/increments/IncrementValuesScreen.kt
git commit -m "feat(increments): Increment Values screen (top list + fine-tune submenu + edit focus)"
```

---

## Task 8: Consume the active lists — Fine-Tune

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt`

Replace the static `ALL_FINE_TUNE_PARAMS` reads with an **active** param list whose `steps` come from `container.activeIncrementLists`. Re-key the selected-step state on the active list so the picker's `activeStep` is always a member.

- [ ] **Step 1: Collect + build active params**

Find where `FineTuneScreen` (the public composable at ~line 91/241) obtains `container` and renders `FineTuneContent`. Collect the lists at that level:

```kotlin
    val incrementLists by container.activeIncrementLists.collectAsStateWithLifecycle(emptyMap())
    val activeParams = remember(incrementLists) {
        ALL_FINE_TUNE_PARAMS.map { p ->
            val steps = incrementLists[p.tuner.name]
            if (steps != null && steps.isNotEmpty()) p.copy(steps = steps.toImmutableList()) else p
        }
    }
```

Add imports: `kotlinx.collections.immutable.toImmutableList`, `androidx.lifecycle.compose.collectAsStateWithLifecycle` (if not present).

- [ ] **Step 2: Thread `activeParams` through `FineTuneContent`**

Change `FineTuneContent` to take `params: List<FineTuneParam>` and replace the three internal `ALL_FINE_TUNE_PARAMS` reads (lines ~285, 288, 294) with `params`. At the call site pass `params = activeParams`. The live batcher reference at ~line 133 (`ALL_FINE_TUNE_PARAMS.first { it.tuner == tuner }`) also needs the active list — pass `activeParams` into that scope or look up from it (its `steps` aren't used in the batcher, but use `activeParams` for consistency).

- [ ] **Step 3: Make `selectedParam` resolve from `params` (selection stays valid)**

Keep `var activeStep by remember(selectedTuner) { mutableStateOf(defaultStepFor(selectedParam)) }`
(line ~289) — do **not** key it on `steps` (that would reset the user's selected position). But the
list CAN change once while Fine-Tune is open: the very first collection emits `emptyMap()` (→ default
steps) and then the real stored map, so on initial load a stored list differing from the default could
leave the remembered `activeStep` value off the new list — and `IncrementPicker` requires `activeStep`
to be a member. Guard it with a member-safe derived value and USE THAT everywhere `activeStep` is read:

```kotlin
    val selectedParam = params.first { it.tuner == selectedTuner }   // was: ALL_FINE_TUNE_PARAMS.first {...}
    var activeStep by remember(selectedTuner) { mutableStateOf(defaultStepFor(selectedParam)) }
    // Member-safe: if the active list changed under us and activeStep is no longer present, fall back
    // to the param's default step (always valid). Preserves position whenever activeStep IS still present.
    val safeActiveStep = if (activeStep in selectedParam.steps) activeStep else defaultStepFor(selectedParam)
```

Replace the `activeStep` READS at the nudge handlers (lines ~335 `-activeStep`, ~338 `+activeStep`) and
the `IncrementPicker(activeStep = activeStep, ...)` (line ~347) with `safeActiveStep`. Leave the WRITE
`onSelect = { activeStep = it }` (line ~348) and the reset `activeStep = defaultStepFor(param)` (line
~372) writing the raw `activeStep` var. `defaultStepFor` indexes the param's CURRENT `steps`
(`defaultStepIndex` 0–2, valid for a fixed-3 list), so every fallback is a real member.

- [ ] **Step 4: Build + run FineTune tests**

Run: `… "E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon --tests *FineTune*"`
Expected: BUILD SUCCESSFUL; existing FineTune tests still green (count unchanged at 3 keeps `defaultStepIndex` valid). Fix any test that constructs `FineTuneContent` with the old signature by passing `params = ALL_FINE_TUNE_PARAMS`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt
git commit -m "feat(increments): Fine-Tune reads per-printer step lists (active params)"
```

---

## Task 9: Consume the active lists — Move Microstep + Probe Z-Test

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt`

- [ ] **Step 1: Move microstep — thread the active list through `MoveHubContent`**

The microstep `steps` block lives inside the stateless `MoveHubContent` (~line 166), which has no
increment-list parameter — so the list must be threaded in (a closed-over `incrementLists` from
`MoveScreen` is NOT in scope there).

(a) In the `MoveScreen` composable (where `container` is available), collect + resolve the list:

```kotlin
    val incrementLists by container.activeIncrementLists.collectAsStateWithLifecycle(emptyMap())
    val microstepSteps: ImmutableList<Double> = remember(incrementLists) {
        (incrementLists["move_microstep"]
            ?: IncrementControls.defaultValueMap().getValue("move_microstep")).toImmutableList()
    }
```

(b) Add a **default-valued** param to `MoveHubContent` (default keeps any preview/test call sites
compiling without edits):

```kotlin
private fun MoveHubContent(
    /* ...existing params... */
    microstepSteps: ImmutableList<Double> =
        IncrementControls.defaultValueMap().getValue("move_microstep")
            .toImmutableList(),
)
```

and pass `microstepSteps = microstepSteps` from `MoveScreen`'s call to `MoveHubContent`.

(c) Replace the inline microstep `steps`/`stepIndex` (lines ~462-468) with the param + a
position-preserving clamp (do NOT re-key `stepIndex` on the list — that would reset the user's
selected position; clamp at access instead, per spec §rebasing for index-tracked controls):

```kotlin
                                val steps: ImmutableList<Double> = microstepSteps
                                var stepIndex by remember(mode) {
                                    mutableStateOf(steps.indexOf(0.1).coerceAtLeast(0))
                                }
                                val activeStep = steps[stepIndex.coerceIn(0, steps.lastIndex)]
```

The existing modulo increment/decrement (`% steps.size`) stays in range. Add imports:
`kotlinx.collections.immutable.toImmutableList`, `works.mees.dinghy.ui.increments.IncrementControls`.

- [ ] **Step 2: Probe Z-Test — read active list + value-rebase**

In `ProbeCalibrateScreen.kt`, the `TESTZ_STEPS` private val (line 80) becomes the default fallback only. In the `ProbeCalibrateScreen` composable (has `container`), collect and resolve:

```kotlin
    val incrementLists by container.activeIncrementLists.collectAsStateWithLifecycle(emptyMap())
    val testzSteps = remember(incrementLists) {
        incrementLists["probe_testz"] ?: IncrementControls.defaultValueMap().getValue("probe_testz")
    }
```

Replace the `step` state initialiser (line 115) and rebase if the active list changes so `step` is always a member:

```kotlin
    var step by remember { mutableStateOf(0.05) }
    // Rebase: when the active list changes, snap `step` to the nearest present value (value-tracked control).
    LaunchedEffect(testzSteps) {
        if (step !in testzSteps) {
            step = testzSteps.minByOrNull { kotlin.math.abs(it - step) } ?: testzSteps.first()
        }
    }
```

Add a **default-valued** `steps` param to `ProbeCalibrateContent` (~line 191) so the ~14 main-source
preview call sites (e.g. `preview/CalibrationPreviews.kt`) keep compiling without edits:

```kotlin
fun ProbeCalibrateContent(
    /* ...existing params... */
    steps: List<Double> = IncrementControls.defaultValueMap().getValue("probe_testz"),
)
```

Pass `steps = testzSteps` from `ProbeCalibrateScreen`. Then fix every `TESTZ_STEPS` reference by scope:
- **Screen-level wrapper lambdas** (`onStepUp`/`onStepDown` at lines ~152-153, which live in
  `ProbeCalibrateScreen`, NOT in the content) → use the screen-level `testzSteps` var.
- **Inside `ProbeCalibrateContent`** (lines ~216, ~298, ~304) → use the passed `steps` param.

After that, `grep -n TESTZ_STEPS app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt`
should show only the line-80 private val (now unused) — delete it (the registry hardcodes the same default).

Add imports: `androidx.compose.runtime.LaunchedEffect`, `works.mees.dinghy.ui.increments.IncrementControls`.

- [ ] **Step 3: Build + targeted tests**

Run: `… "E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon --tests *Move* --tests *Probe*"`
Expected: BUILD SUCCESSFUL; tests green. Fix any `ProbeCalibrateContent` test that needs the new `steps` param (pass the default list).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt
git commit -m "feat(increments): Move microstep + Probe Z-Test read per-printer lists (clamp/rebase)"
```

---

## Task 10: Full verification

- [ ] **Step 1: Full unit-test suite**

Run: `… "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"`
Expected: BUILD SUCCESSFUL, all green. Pay attention to any `AppContainer` fixture (new ctor param) and `NavDest` round-trip / count tests (`knownNavDests` now 25).

- [ ] **Step 2: Release assemble + ligature check**

Run: `… "E:\Android\gw.bat :app:assembleRelease --no-daemon"` then `python tools/verify_ligatures.py`
Expected: BUILD SUCCESSFUL; ligatures PASS.

- [ ] **Step 3: Commit any fixup, then hand off for Codex review + on-device UAT**

The feature is code-complete when: suite green, release assembles, ligatures pass. UAT items to surface to the owner: (a) the **babystep `question_mark` icon** placeholder, (b) the **Probe Z Test row title** vs the in-app probe label.

---

## Self-Review Notes (filled during writing)

- **Spec coverage:** storage/seed (T3/T4), registry + jiib defaults (T2), parsing/validation incl. allowed-chars/strip-spaces/≥1/exact-3/>0 (T1), settings door + 4-row list + Fine-Tune submenu + marquee titles + `/`-summary (T6/T7), filtered keyboard edit + entered-order verbatim (T1/T7), icons reuse + babystep UAT flag (T2/T5), consumption replacement for all 3 live groups + rebasing (T8/T9), babystep setting-only/no consumption (T2 default ref only). ✔
- **No multi-selector-page titles needed** beyond Fine-Tune (the only multi-selector group), handled by the submenu + `controlTitle`. ✔
- **Type consistency:** `IncrementParse.Ok(canonical, values)`, `IncrementControlSpec(key, group, controlTitle, icon, maxCount, defaultValues)`, `activeIncrementLists: Flow<Map<String,List<Double>>>`, `activeIncrementStrings: Flow<Map<String,String>>`, `saveIncrementList(key, canonical)` — used consistently across tasks. ✔
