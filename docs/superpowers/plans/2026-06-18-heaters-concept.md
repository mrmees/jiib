# The "Heaters" Concept — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the inconsistent preheat/cooldown UIs on Home, Extrude, and Temperature with one reusable, scope-parameterized "Heaters" list (OFF row pinned top, dynamic loaded-spool row, then user presets).

**Architecture:** A pure, unit-tested core (`buildHeatersRows`) produces capability-filtered, heater-correct, keyed `HeatersRow`s. A single Compose component (`HeatersList`) renders them as a Field takeover and dispatches each row's pre-built `HeatDispatch`. Three call sites swap their Field to this component via the existing mode-swap pattern; all bespoke preheat/cooldown/chip code is deleted.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, JUnit unit tests. Reuses existing `CommandRegistry.applyHeatPreset` / `cooldown`, `ListRow`/`ListBlock`/`FootButtonBar`/`FootAction`, `DinghyIcons`, `Capabilities`, `SpoolmanSpool`.

**Spec:** `docs/superpowers/specs/2026-06-18-heaters-concept-design.md`

**Build/test note:** This repo builds Windows-side. Run any Gradle task as:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args> --no-daemon" | tr -d '\r'
```
Unit-test task: `:app:testDebugUnitTest`. Filter with `--tests "works.mees.dinghy.ui.heaters.*"`. The process exit code is authoritative.

---

## File Structure

| File | Responsibility |
|------|----------------|
| `app/.../designsystem/icons/DinghyIcons.kt` (modify) | Add `FireCheck` (`fire_check`) + `HeatersOff` (`thermometer`) tokens; add both to `all`. |
| `app/.../ui/heaters/HeatersModel.kt` (create) | Pure core: `HeatScope`, `LoadedSpoolTemps`, `HeatDispatch`, `HeatersRow`, `loadedSpoolTemps()`, `heatSummary()`, `buildHeatersRows()`. No Compose. |
| `app/.../ui/heaters/HeatersList.kt` (create) | The Field-takeover composable + `dispatchHeat()` helper. |
| `app/src/test/.../ui/heaters/HeatersModelTest.kt` (create) | Unit tests for the pure core. |
| `app/.../ui/temperature/TemperatureScreen.kt` (modify) | `PresetPicker` → `HeatersList(Full)`; OFF row; `LoadedSpoolTemps`; foot bar collapse to `[Back · Heaters · Monitor]`; Monitor→Adjust icon → `FireCheck`. |
| `app/.../ui/extrude/ExtrudeScreen.kt` (modify) | Add `ExtrudeFieldMode.Heaters`; Cooldown foot → Heaters foot; `HeatersList(ExtruderOnly, extruderObject=vm.activeHeater)`; remove inline chips. |
| `app/.../ui/printstatus/PrintStatusField.kt` (modify) | Standby foot: Preheat/Cooldown swap → single Heaters button; field-mode takeover → `HeatersList(Full)`. |
| `app/.../ui/printstatus/PrintStatusScreen.kt` (modify) | Build `heatersRows`; drop `selectPreheatPath`/`PreheatPath`/`PresetSelector` wiring. |
| `app/.../ui/printstatus/Preheat.kt` + test (delete) | Superseded by the Heaters list. |

**Dispatch model (used by the pure core):**
```kotlin
sealed interface HeatDispatch {
    data object TurnOffAll : HeatDispatch                                  // → CommandRegistry.cooldown (TURN_OFF_HEATERS)
    data class ApplyPreset(val setpoints: Map<String, Int>, val key: String) : HeatDispatch  // → applyHeatPreset
}
```
Every row is either `TurnOffAll` (Full OFF only) or `ApplyPreset`. Distinct keys (`heat_off`, `heat_spool`, `preset_<id>`) guarantee OFF is never dropped by a prior apply (the dispatcher only dedupes within a single key). `ApplyPreset` is used even for single-heater applies so `temperature_fan` entries still route correctly.

---

## Task 1: Add the two new icon tokens

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`
- Test: `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt` (existing gate — no new test file)

Both `fire_check` and `thermometer` are confirmed resolvable in the bundled v2.944 Material Symbols font (verified during spec review). The Heaters **button** reuses the existing `OutputHeater` (`mode_heat`) token — do **not** add a duplicate `mode_heat` token (the registry test enforces unique IconRef).

- [ ] **Step 1: Add the tokens** near the other heat tokens (after `HeatPresetAdd`, ~line 361):

```kotlin
/** Temperature screen Monitor→Adjust toggle — owner-chosen `fire_check`. */
val FireCheck = DinghyIcon(IconRef.Ligature("fire_check"), alternate = "temp_enter_adjust")

/** The built-in "OFF" row in the Heaters list — owner-chosen `thermometer`. */
val HeatersOff = DinghyIcon(IconRef.Ligature("thermometer"), alternate = "heaters_off")
```

- [ ] **Step 2: Register both in the `all` list** (the `val all: List<DinghyIcon> = listOf(...)` block, ~line 385). Add `FireCheck, HeatersOff,` to a line near the other heat/temp tokens, e.g. next to `HeatPresetAdd`.

- [ ] **Step 3: Run the icon registry + ligature gates**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.designsystem.icons.DinghyIconsTest' --no-daemon" | tr -d '\r'
python3 tools/verify_ligatures.py
```
Expected: both PASS (tokens unique, ligatures present in font).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
git commit -m "feat(heaters): add fire_check + thermometer icon tokens"
```

---

## Task 2: Pure core types + `loadedSpoolTemps()` + `heatSummary()`

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/heaters/HeatersModel.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/heaters/HeatersModelTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package works.mees.dinghy.ui.heaters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.spool.SpoolmanFilament
import works.mees.dinghy.spool.SpoolmanSpool

class HeatersModelTest {
    @Test fun `loadedSpoolTemps null when no spool`() {
        assertNull(loadedSpoolTemps(null, "Loaded filament"))
    }

    @Test fun `loadedSpoolTemps null when spool has no temps`() {
        val spool = SpoolmanSpool(id = 1, filament = SpoolmanFilament(name = "PLA"))
        assertNull(loadedSpoolTemps(spool, "Loaded filament"))
    }

    @Test fun `loadedSpoolTemps maps nozzle bed color label`() {
        val spool = SpoolmanSpool(
            id = 1,
            filament = SpoolmanFilament(
                name = "Galaxy Black", material = "PLA",
                colorHex = "112233", settingsExtruderTemp = 210, settingsBedTemp = 60,
            ),
        )
        val r = loadedSpoolTemps(spool, "Loaded filament")!!
        assertEquals(210, r.nozzle)
        assertEquals(60, r.bed)
        assertEquals("#112233", r.colorHex)
        assertEquals("Galaxy Black", r.label)
    }

    @Test fun `loadedSpoolTemps label falls back to material then default`() {
        val matOnly = SpoolmanSpool(id = 1, filament = SpoolmanFilament(material = "PETG", settingsExtruderTemp = 240))
        assertEquals("PETG", loadedSpoolTemps(matOnly, "Loaded filament")!!.label)
        val neither = SpoolmanSpool(id = 1, filament = SpoolmanFilament(settingsBedTemp = 60))
        assertEquals("Loaded filament", loadedSpoolTemps(neither, "Loaded filament")!!.label)
    }

    @Test fun `heatSummary sorts by key and joins values`() {
        assertEquals("210/60", heatSummary(mapOf("heater_bed" to 60, "extruder" to 210)))
        assertEquals("", heatSummary(emptyMap()))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.heaters.HeatersModelTest' --no-daemon"`
Expected: FAIL — `HeatersModel.kt` symbols unresolved.

- [ ] **Step 3: Create `HeatersModel.kt` with the types + factories**

```kotlin
package works.mees.dinghy.ui.heaters

import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.spool.SpoolmanSpool

/** Which heaters a Heaters-list selection is allowed to touch. */
enum class HeatScope { Full, ExtruderOnly }

/** Heater setpoints pulled from the currently loaded Spoolman spool (the dynamic row source). */
data class LoadedSpoolTemps(
    val nozzle: Int?,
    val bed: Int?,
    val colorHex: String?,
    val label: String,
)

/** A ready-to-fire dispatch for one Heaters row. */
sealed interface HeatDispatch {
    data object TurnOffAll : HeatDispatch
    data class ApplyPreset(val setpoints: Map<String, Int>, val key: String) : HeatDispatch
}

/** One row in the Heaters list — display data + its pre-built, capability-filtered dispatch. */
data class HeatersRow(
    val key: String,
    val icon: DinghyIcon,
    val label: String,
    val summary: String?,
    val tintHex: String?,
    val dispatch: HeatDispatch,
)

/**
 * Build [LoadedSpoolTemps] from the active spool, or null when there is no spool / no usable temp.
 * [fallbackLabel] is the localized "Loaded filament" string supplied by the caller (kept out of this
 * pure function so it stays JVM-testable).
 */
fun loadedSpoolTemps(spool: SpoolmanSpool?, fallbackLabel: String): LoadedSpoolTemps? {
    val f = spool?.filament ?: return null
    if (f.settingsExtruderTemp == null && f.settingsBedTemp == null) return null
    return LoadedSpoolTemps(
        nozzle = f.settingsExtruderTemp,
        bed = f.settingsBedTemp,
        colorHex = f.colorSwatches.firstOrNull(),
        label = f.name ?: f.material ?: fallbackLabel,
    )
}

/** Trailing summary for a setpoint map — values sorted by object name, joined by `/` (matches presetSummary). */
fun heatSummary(setpoints: Map<String, Int>): String =
    setpoints.entries.sortedBy { it.key }.joinToString("/") { it.value.toString() }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `... --tests 'works.mees.dinghy.ui.heaters.HeatersModelTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/heaters/HeatersModel.kt app/src/test/java/works/mees/dinghy/ui/heaters/HeatersModelTest.kt
git commit -m "feat(heaters): pure model types + spool/summary helpers"
```

---

## Task 3: `buildHeatersRows()` — ordering, scope, capability filter

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/heaters/HeatersModel.kt`
- Modify: `app/src/test/java/works/mees/dinghy/ui/heaters/HeatersModelTest.kt`

- [ ] **Step 1: Add failing tests** (append to `HeatersModelTest`):

```kotlin
    private val caps = works.mees.dinghy.state.Capabilities(
        objects = setOf("extruder", "heater_bed"),
    )
    private val noBed = works.mees.dinghy.state.Capabilities(objects = setOf("extruder"))
    private val presets = listOf(
        works.mees.dinghy.config.HeatPreset("p1", "PLA", mapOf("extruder" to 200, "heater_bed" to 60)),
        works.mees.dinghy.config.HeatPreset("p2", "Bed only", mapOf("heater_bed" to 50)),
    )

    @Test fun `OFF pinned top, spool second, presets follow`() {
        val spool = LoadedSpoolTemps(210, 60, "#fff", "Galaxy")
        val rows = buildHeatersRows(presets, spool, HeatScope.Full, "extruder", caps)
        assertEquals(listOf("heat_off", "heat_spool", "preset_p1", "preset_p2"), rows.map { it.key })
        assertEquals(works.mees.dinghy.designsystem.icons.DinghyIcons.HeatersOff, rows[0].icon)
        assertEquals(works.mees.dinghy.designsystem.icons.DinghyIcons.SpoolFilament, rows[1].icon)
        assertEquals("#fff", rows[1].tintHex)
    }

    @Test fun `Full OFF turns off all, Extruder OFF sets active heater to 0`() {
        val full = buildHeatersRows(emptyList(), null, HeatScope.Full, "extruder", caps)
        assertEquals(HeatDispatch.TurnOffAll, full[0].dispatch)
        val ext = buildHeatersRows(emptyList(), null, HeatScope.ExtruderOnly, "extruder1", caps)
        assertEquals(HeatDispatch.ApplyPreset(mapOf("extruder1" to 0), "heat_off"), ext[0].dispatch)
    }

    @Test fun `Full preset drops heaters the printer lacks`() {
        val rows = buildHeatersRows(presets, null, HeatScope.Full, "extruder", noBed)
        // p1 keeps only extruder; p2 (bed only) is dropped entirely on a bedless printer.
        val p1 = rows.first { it.key == "preset_p1" }.dispatch as HeatDispatch.ApplyPreset
        assertEquals(mapOf("extruder" to 200), p1.setpoints)
        assertEquals(null, rows.firstOrNull { it.key == "preset_p2" })
    }

    @Test fun `ExtruderOnly preset maps to active heater, drops no-extruder presets`() {
        val rows = buildHeatersRows(presets, null, HeatScope.ExtruderOnly, "extruder1", caps)
        val p1 = rows.first { it.key == "preset_p1" }.dispatch as HeatDispatch.ApplyPreset
        assertEquals(mapOf("extruder1" to 200), p1.setpoints)
        assertEquals(null, rows.firstOrNull { it.key == "preset_p2" }) // p2 has no extruder temp
    }

    @Test fun `Full spool applies nozzle and bed, ExtruderOnly nozzle only`() {
        val spool = LoadedSpoolTemps(210, 60, null, "Galaxy")
        val full = (buildHeatersRows(emptyList(), spool, HeatScope.Full, "extruder", caps)
            .first { it.key == "heat_spool" }.dispatch as HeatDispatch.ApplyPreset)
        assertEquals(mapOf("extruder" to 210, "heater_bed" to 60), full.setpoints)
        val ext = (buildHeatersRows(emptyList(), spool, HeatScope.ExtruderOnly, "extruder1", caps)
            .first { it.key == "heat_spool" }.dispatch as HeatDispatch.ApplyPreset)
        assertEquals(mapOf("extruder1" to 210), ext.setpoints)
    }

    @Test fun `bed-only spool shown under Full, hidden under ExtruderOnly`() {
        val bedOnly = LoadedSpoolTemps(null, 60, null, "Galaxy")
        val full = buildHeatersRows(emptyList(), bedOnly, HeatScope.Full, "extruder", caps)
        assertEquals(mapOf("heater_bed" to 60), (full.first { it.key == "heat_spool" }.dispatch as HeatDispatch.ApplyPreset).setpoints)
        val ext = buildHeatersRows(emptyList(), bedOnly, HeatScope.ExtruderOnly, "extruder", caps)
        assertNull(ext.firstOrNull { it.key == "heat_spool" })
    }
```

> Note: confirm the `Capabilities` constructor parameter name (`objects`) against `state/Capabilities.kt`; adjust the test constructor if it differs (e.g. a builder/default). The production code only calls `caps.hasObject(name)`.

- [ ] **Step 2: Run to verify failure**

Run: `... --tests 'works.mees.dinghy.ui.heaters.HeatersModelTest'`
Expected: FAIL — `buildHeatersRows` unresolved.

- [ ] **Step 3: Implement `buildHeatersRows`** in `HeatersModel.kt`. **Add these imports to the existing import block at the TOP of the file** (Kotlin imports cannot follow declarations):

```kotlin
import works.mees.dinghy.config.HeatPreset
import works.mees.dinghy.config.sortedForDisplay
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.state.Capabilities
```

Then append the function bodies:

```kotlin
/**
 * Build the unified Heaters list: OFF pinned top, the loaded-spool row second (when present),
 * then user presets sorted by extruder temp. Every row's dispatch is already capability-filtered
 * and heater-correct for [scope]; rows whose applicable setpoints are empty are omitted (no dead rows).
 *
 * @param extruderObject the heater targeted under [HeatScope.ExtruderOnly] — the ACTIVE tool
 *   (e.g. "extruder1"), not literally "extruder".
 */
fun buildHeatersRows(
    presets: List<HeatPreset>,
    loadedSpool: LoadedSpoolTemps?,
    scope: HeatScope,
    extruderObject: String,
    capabilities: Capabilities,
): List<HeatersRow> {
    val rows = mutableListOf<HeatersRow>()

    // 1. OFF (pinned top, always present).
    rows += HeatersRow(
        key = "heat_off",
        icon = DinghyIcons.HeatersOff,
        label = "OFF",
        summary = null,
        tintHex = null,
        dispatch = when (scope) {
            HeatScope.Full -> HeatDispatch.TurnOffAll
            HeatScope.ExtruderOnly -> HeatDispatch.ApplyPreset(mapOf(extruderObject to 0), "heat_off")
        },
    )

    // 2. Loaded spool (pinned second, when it yields a non-empty applicable map).
    loadedSpool?.let { sp ->
        val map = spoolSetpoints(sp, scope, extruderObject, capabilities)
        if (map.isNotEmpty()) {
            rows += HeatersRow(
                key = "heat_spool",
                icon = DinghyIcons.SpoolFilament,
                label = sp.label,
                summary = heatSummary(map),
                tintHex = sp.colorHex,
                dispatch = HeatDispatch.ApplyPreset(map, "heat_spool"),
            )
        }
    }

    // 3. User presets (sorted), omitting any whose applicable map is empty.
    presets.sortedForDisplay().forEach { preset ->
        val map = presetSetpoints(preset, scope, extruderObject, capabilities)
        if (map.isNotEmpty()) {
            rows += HeatersRow(
                key = "preset_${preset.id}",
                icon = DinghyIcons.LauncherTemperature,
                label = preset.name,
                summary = heatSummary(map),
                tintHex = null,
                dispatch = HeatDispatch.ApplyPreset(map, "preset_${preset.id}"),
            )
        }
    }

    return rows
}

private fun spoolSetpoints(sp: LoadedSpoolTemps, scope: HeatScope, extruderObject: String, caps: Capabilities): Map<String, Int> =
    when (scope) {
        HeatScope.ExtruderOnly ->
            if (sp.nozzle != null && caps.hasObject(extruderObject)) mapOf(extruderObject to sp.nozzle) else emptyMap()
        HeatScope.Full -> buildMap {
            if (sp.nozzle != null && caps.hasObject("extruder")) put("extruder", sp.nozzle)
            if (sp.bed != null && caps.hasObject("heater_bed")) put("heater_bed", sp.bed)
        }
    }

private fun presetSetpoints(preset: HeatPreset, scope: HeatScope, extruderObject: String, caps: Capabilities): Map<String, Int> =
    when (scope) {
        HeatScope.ExtruderOnly ->
            preset.extruderTemp?.takeIf { caps.hasObject(extruderObject) }?.let { mapOf(extruderObject to it) } ?: emptyMap()
        HeatScope.Full -> preset.setpoints.filterKeys { caps.hasObject(it) }
    }
```

- [ ] **Step 4: Run tests**

Run: `... --tests 'works.mees.dinghy.ui.heaters.HeatersModelTest'`
Expected: PASS (all tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/heaters/HeatersModel.kt app/src/test/java/works/mees/dinghy/ui/heaters/HeatersModelTest.kt
git commit -m "feat(heaters): buildHeatersRows ordering + scope + capability filter"
```

---

## Task 4: `HeatersList` composable + `dispatchHeat()`

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/heaters/HeatersList.kt`

This renders `rows` as a `ListBlock` of `ListRow`s with a single `Back` foot button, and on tap calls `onApply(row.dispatch)` then `onApplied`. The actual dispatch is done by the route composable via `dispatchHeat` (keeps the preview-pure `*Content` composables free of a dispatcher). No new unit test (thin Compose glue over the tested core; verified by build + on-device UAT).

> **Import paths below are verified** against `TemperatureScreen.kt` (same symbols). Note the non-obvious ones Codex flagged: `Intent` is in `designsystem.control` (NOT `.components`), `LocalTokens`/`toTextStyle` are in `theme.compose`, `DinghyType` is in `theme`, `parseNormalizedHex` is in `ui.spool`, and `dispatch` is the extension `works.mees.dinghy.command.dispatch`.

- [ ] **Step 1: Create the file**

```kotlin
package works.mees.dinghy.ui.heaters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import works.mees.dinghy.R
import works.mees.dinghy.command.ApplyHeatPresetArgs
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.ui.spool.parseNormalizedHex

/**
 * Route-level helper: fire one Heaters selection. Distinct keys per row (`heat_off`/`heat_spool`/
 * `preset_<id>`) guarantee OFF is never debounce-dropped (nothing else shares those keys).
 */
fun dispatchHeat(dispatcher: CommandDispatcher?, dispatch: HeatDispatch) {
    when (dispatch) {
        HeatDispatch.TurnOffAll -> dispatcher?.dispatch(CommandRegistry.cooldown, Unit)
        is HeatDispatch.ApplyPreset ->
            dispatcher?.dispatch(CommandRegistry.applyHeatPreset, ApplyHeatPresetArgs(dispatch.setpoints, dispatch.key))
    }
}

/**
 * The unified Heaters Field takeover. Renders [rows] (OFF, loaded-spool, presets); tapping a row
 * calls [onApply] with its dispatch then [onApplied] (the caller returns the Field to its normal
 * list). [onBack] dismisses without applying.
 */
@Composable
fun HeatersList(
    rows: List<HeatersRow>,
    onApply: (HeatDispatch) -> Unit,
    onApplied: () -> Unit,
    onBack: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier.fillMaxSize()) {
        ListBlock(modifier = Modifier.weight(1f)) {
            items(rows, key = { it.key }) { row ->
                val tint = row.tintHex?.let { parseNormalizedHex(it) } ?: t.accent
                ListRow(
                    selected = false,
                    onClick = { onApply(row.dispatch); onApplied() },
                    uDp = uDp,
                    leadingContent = { ListRowIcon(icon = row.icon, uDp = uDp, tint = tint) },
                    trailingContent = row.summary?.let { s ->
                        { Text(text = s, style = DinghyType.dataInline.toTextStyle(t), color = t.text2) }
                    },
                ) {
                    ListRowLabel(row.label)
                }
            }
        }
        FootButtonBar(
            uDp = uDp,
            actions = listOf(
                FootAction(
                    label = stringResource(R.string.cd_back),
                    icon = DinghyIcons.Back,
                    onClick = onBack,
                    intent = Intent.Accent,
                    contentDescription = stringResource(R.string.cd_back),
                ),
            ),
        )
    }
}
```

> `parseNormalizedHex(String): Color?` returns a Color (null-safe via `?: t.accent`). `ListRowLabel(text: String)` applies the canonical list-label style (used the same way in `PrintStatusField.kt`).

- [ ] **Step 2: Build to verify it compiles**

Run: `... "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/heaters/HeatersList.kt
git commit -m "feat(heaters): HeatersList Field takeover + dispatchHeat helper"
```

---

## Task 5: Wire the Temperature screen

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt`

Replace the `PresetPicker` takeover with `HeatersList(Full)`, add the OFF row (via the shared core), swap the Monitor→Adjust icon to `FireCheck`, and collapse the two-row Adjust footer into one row `[Back · Heaters · Monitor]`.

- [ ] **Step 1: Replace `spoolPreset` with `LoadedSpoolTemps`** (~L256). Change:

```kotlin
val spoolPreset: HeatPreset? = remember(activeSpoolDetail) { ... }   // DELETE this whole block
```
to:
```kotlin
val loadedFilamentLabel = stringResource(R.string.extrude_loaded_filament) // or a temp-screen equivalent string
val loadedSpool = remember(activeSpoolDetail, loadedFilamentLabel) {
    works.mees.dinghy.ui.heaters.loadedSpoolTemps(activeSpoolDetail, loadedFilamentLabel)
}
```

- [ ] **Step 2: Build the rows in the ROUTE composable** (`TemperatureScreen`, where `caps`/`heatPresets`/`activeSpoolDetail`/`dispatcher` are collected — `caps` is at ~L173). `loadedSpool` from Step 1 is also here. Add (no `remember` — it's a cheap pure call over small lists, and avoids stale-key bugs):

```kotlin
val heatersRows = works.mees.dinghy.ui.heaters.buildHeatersRows(
    presets = heatPresets,
    loadedSpool = loadedSpool,
    scope = works.mees.dinghy.ui.heaters.HeatScope.Full,
    extruderObject = "extruder",
    capabilities = caps,
)
```

- [ ] **Step 2b: Thread it into `TemperatureContent`.** The `*Content` composables are preview-pure (no dispatcher), so pass the rows + a dispatch callback, mirroring the existing `onApplyPreset`/`onCooldown` params.
  - In the `TemperatureContent(...)` signature (~L420): **remove** `spoolPreset`, `onApplyPreset`, `onCooldown`; **add**
    ```kotlin
    heatersRows: List<works.mees.dinghy.ui.heaters.HeatersRow> = emptyList(),
    onHeatApply: (works.mees.dinghy.ui.heaters.HeatDispatch) -> Unit = {},
    ```
  - At the `TemperatureContent(...)` call site in `TemperatureScreen`: remove the `spoolPreset = …`, `onApplyPreset = …`, `onCooldown = …` arguments; add
    ```kotlin
    heatersRows = heatersRows,
    onHeatApply = { works.mees.dinghy.ui.heaters.dispatchHeat(dispatcher, it) },
    ```
    (`dispatcher` is the same value the screen already used to wire `onCooldown`/`onNudgeHeater`.)

- [ ] **Step 3: Change the Monitor→Adjust toggle icon** (~L688): `icon = DinghyIcons.OutputHeater` → `icon = DinghyIcons.FireCheck`.

- [ ] **Step 4: Collapse the Adjust footer to one row.** Replace the entire Adjust-mode `else` branch (the two `FootButtonBar` blocks at ~L695–734) with a single bar:

```kotlin
} else {
    // Adjust footer (one row): Back exits the screen · Heaters opens the takeover · Monitor returns.
    FootButtonBar(
        uDp = grid.uDp,
        actions = listOf(
            FootAction(
                label = stringResource(R.string.cd_back),
                icon = DinghyIcons.Back,
                onClick = onBack,
                intent = Intent.Accent,
                contentDescription = stringResource(R.string.cd_back),
            ),
            FootAction(
                label = stringResource(R.string.home_foot_heaters), // new string, Task 7 Step 0 (add once)
                icon = DinghyIcons.OutputHeater, // mode_heat
                onClick = { fieldMode = TempFieldMode.PresetPicker },
                intent = Intent.Accent,
                contentDescription = stringResource(R.string.home_foot_heaters),
            ),
            FootAction(
                label = stringResource(R.string.cd_temp_enter_monitor),
                icon = DinghyIcons.MonitorMode,
                onClick = { mode = TempMode.Monitoring; selectedName = null },
                intent = Intent.Accent,
                contentDescription = stringResource(R.string.cd_temp_enter_monitor),
            ),
        ),
    )
}
```
> Add the `home_foot_heaters` string resource (value `Heaters`) to `strings.xml` if not already present.

- [ ] **Step 5: Replace the `PresetPicker` field branch** (~L738–768) with `HeatersList`:

```kotlin
TempFieldMode.PresetPicker -> {
    works.mees.dinghy.ui.heaters.HeatersList(
        rows = heatersRows,
        onApply = onHeatApply,
        onApplied = { fieldMode = TempFieldMode.SensorList },
        onBack = { fieldMode = TempFieldMode.SensorList },
        uDp = grid.uDp,
    )
}
```
> `heatersRows`/`onHeatApply` are the params added in Step 2b. Remove the now-dead `PresetListRow` calls in this branch; keep `PresetListRow`/`presetSummary` only if still referenced elsewhere (`presetSummary` lives in HeatPresetsScreen and stays).

- [ ] **Step 6: Remove dead Temp plumbing.** Delete the `onCooldown` and `onApplyPreset` parameters/wiring if no longer used on this screen, and the `TempPresets`/`TempCooldown` icon usages here. Do NOT delete the `TempPresets` token (still used by HeatPresetsScreen).

- [ ] **Step 7: Build + run existing Temperature tests**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon" | tr -d '\r'
```
Expected: BUILD SUCCESSFUL; suite green (fix any test referencing the deleted `spoolPreset`/`onApplyPreset`).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(heaters): Temperature uses unified HeatersList + fire_check toggle + 3-button adjust footer"
```

---

## Task 6: Wire the Extrude screen

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt`

Add a `Heaters` field mode, replace the Cooldown foot button with a Heaters button (`HeatScope.ExtruderOnly`, `extruderObject = vm.activeHeater`), and remove the inline nozzle preset chips.

- [ ] **Step 1: Add the field mode** (the `sealed class ExtrudeFieldMode`, ~L99):

```kotlin
data object Heaters : ExtrudeFieldMode()
```

- [ ] **Step 2: Collect capabilities + build the rows in the ROUTE composable** (`ExtrudeScreen`, where `dispatcher` (L139), `heatPresets` (L144), `vm`, and the active spool detail are). Extrude does **not** currently collect capabilities — add it (mirror Temperature L173):

```kotlin
val caps by container.capabilities.collectAsStateWithLifecycle(initialValue = Capabilities())
val loadedFilamentLabel = stringResource(R.string.extrude_loaded_filament)
val heatersRows = works.mees.dinghy.ui.heaters.buildHeatersRows(
    presets = heatPresets,
    loadedSpool = works.mees.dinghy.ui.heaters.loadedSpoolTemps(activeSpoolDetail, loadedFilamentLabel),
    scope = works.mees.dinghy.ui.heaters.HeatScope.ExtruderOnly,
    extruderObject = vm.activeHeater,
    capabilities = caps,
)
```
> Add `import works.mees.dinghy.state.Capabilities` if not present. Use the route's name for the active spool detail (the value passed to `ExtrudeContent(activeSpoolDetail = …)`).

- [ ] **Step 2b: Thread into `ExtrudeContent`** (signature ~L257). **Remove** `onCooldown`, and `onSetExtruderTemp`/`heatPresets` if they go dead after the chips are removed (Step 4). **Add**:
```kotlin
heatersRows: List<works.mees.dinghy.ui.heaters.HeatersRow> = emptyList(),
onHeatApply: (works.mees.dinghy.ui.heaters.HeatDispatch) -> Unit = {},
```
At the `ExtrudeContent(...)` call site: drop `onCooldown = …`; add `heatersRows = heatersRows`, `onHeatApply = { works.mees.dinghy.ui.heaters.dispatchHeat(dispatcher, it) }`.

- [ ] **Step 3: Replace the Cooldown foot button** (~L398–404) with a Heaters button:

```kotlin
FootAction(
    label = stringResource(R.string.home_foot_heaters),
    onClick = { fieldMode = ExtrudeFieldMode.Heaters },
    intent = Intent.Accent,
    icon = DinghyIcons.OutputHeater, // mode_heat
    contentDescription = stringResource(R.string.home_foot_heaters),
),
```

- [ ] **Step 4: Remove the inline nozzle preset chips** in the `ExtrudeFieldMode.Main` branch (~L375–382, section "3. Inline nozzle-only thermal presets") — delete the loaded-spool `HeatPresetRow` item and the `items(heatPresets.filter ...)` block. Leave runout sensors, macros, and the spool link. Remove the now-unused `loadedTemp`/`loadedName`/`loadedLabel` locals and the `onSetExtruderTemp`/`heatPresets` params if they become dead (check `HeatPresetRow` usages — delete the helper if now unreferenced).

- [ ] **Step 5: Add the `Heaters` field branch** alongside `Main`/`MacroSettings` (~L416):

```kotlin
is ExtrudeFieldMode.Heaters -> {
    works.mees.dinghy.ui.heaters.HeatersList(
        rows = heatersRows,
        onApply = onHeatApply,
        onApplied = { fieldMode = ExtrudeFieldMode.Main },
        onBack = { fieldMode = ExtrudeFieldMode.Main },
        uDp = grid.uDp,
    )
}
```
> `heatersRows`/`onHeatApply` are the params added in Step 2b.

- [ ] **Step 6: Remove the now-dead `onCooldown`** wiring (the `CommandRegistry.cooldown` dispatch at L215 and the route lambda) — Step 2b already dropped the `ExtrudeContent` param.

- [ ] **Step 7: Build + suite**

Run: `... "E:\Android\gw.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon"`
Expected: BUILD SUCCESSFUL; green.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt
git commit -m "feat(heaters): Extrude uses HeatersList (extruder-only) + removes inline chips"
```

---

## Task 7: Wire the Home / standby screen

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt`
- Modify: `app/src/main/res/values/strings.xml` (add `home_foot_heaters` = `Heaters` if not added in Task 5)

The Heaters button + takeover apply ONLY in the standby states (the `else` branch of the foot bar). Printing/paused/complete foot bars are unchanged.

- [ ] **Step 1: Add params to `HomeField`** — replace `onPreheat`/`anyHeaterOn`/`onCooldown` with the rows + apply callback:

Change the signature (~L62–68): remove `onPreheat: () -> Unit`, `anyHeaterOn: Boolean = false`, `onCooldown: () -> Unit = {}`; add:
```kotlin
heatersRows: List<works.mees.dinghy.ui.heaters.HeatersRow> = emptyList(),
onHeatApply: (works.mees.dinghy.ui.heaters.HeatDispatch) -> Unit = {},
```

- [ ] **Step 2: Add a field-mode takeover** at the top of the `RegisteredRegion` body. Wrap the standby Field so that when Heaters is active it shows the list:

```kotlin
var heatersOpen by remember { mutableStateOf(false) }
if (heatersOpen && !isPrinting && !isComplete) {   // idle/standby only (owner: not Complete)
    works.mees.dinghy.ui.heaters.HeatersList(
        rows = heatersRows,
        onApply = onHeatApply,
        onApplied = { heatersOpen = false },
        onBack = { heatersOpen = false },
        uDp = uDp,
    )
    return@RegisteredRegion
}
```
> Place this immediately inside `RegisteredRegion { ... }`, before the existing `val loadedSpool = ...` / `ListBlock`. `return@RegisteredRegion` is valid for the `RegisteredRegion` content lambda. It skips the normal list+footbar while the takeover is open.

- [ ] **Step 3: Replace the standby Preheat/Cooldown swap** (the `else` branch ~L148–156) with a single Heaters button:

```kotlin
} else {
    add(FootAction(stringResource(R.string.home_foot_heaters),
        DinghyIcons.OutputHeater, { heatersOpen = true }, Intent.Accent)) // mode_heat — opens the Heaters takeover
    add(FootAction(stringResource(R.string.home_foot_system),
        DinghyIcons.FootSystem, { onNavigate(NavDest.System) }, Intent.Accent))
}
```

- [ ] **Step 4: Plumb rows/apply through `PrintStatusContent` → `HomeField`.** `HomeField` is called from `PrintStatusContent` (~L344), which is itself preview-pure. Add the two params to `PrintStatusContent`'s signature:
```kotlin
heatersRows: List<works.mees.dinghy.ui.heaters.HeatersRow> = emptyList(),
onHeatApply: (works.mees.dinghy.ui.heaters.HeatDispatch) -> Unit = {},
```
and forward them in its `HomeField(...)` call (`heatersRows = heatersRows, onHeatApply = onHeatApply`), removing the old `onPreheat`/`anyHeaterOn`/`onCooldown` forwarding.

- [ ] **Step 5: Build the rows in the ROUTE composable `PrintStatusScreen`** and pass into `PrintStatusContent`. Where `onPreheat`/`selectPreheatPath` was wired (~L171–205): collect `caps`/`heatPresets`/the spool detail if not already present (the screen already collects them for `buildIdleActions`/the old preheat path), then:

```kotlin
val loadedFilamentLabel = stringResource(R.string.extrude_loaded_filament)
val heatersRows = works.mees.dinghy.ui.heaters.buildHeatersRows(
    presets = heatPresets,
    loadedSpool = works.mees.dinghy.ui.heaters.loadedSpoolTemps(spoolDetail, loadedFilamentLabel),
    scope = works.mees.dinghy.ui.heaters.HeatScope.Full,
    extruderObject = "extruder",
    capabilities = caps,
)
```
In the `PrintStatusContent(...)` call: remove `onPreheat`/`anyHeaterOn`/`onCooldown`; add `heatersRows = heatersRows`, `onHeatApply = { works.mees.dinghy.ui.heaters.dispatchHeat(dispatcher, it) }`. (`dispatcher` is collected in this route.)

- [ ] **Step 6: Delete the now-dead preheat code in `PrintStatusScreen`** — `runPreheat()`, the `selectPreheatPath(...)` call, the `showPresetSelector` state, and the `PresetSelector(...)` render. (The `Preheat.kt` file itself is removed in Task 8.)

- [ ] **Step 7: Build + suite**

Run: `... "E:\Android\gw.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon"`
Expected: BUILD SUCCESSFUL. Existing `PrintStatusField`/home tests that referenced `onPreheat`/`anyHeaterOn` must be updated to the new params.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(heaters): Home standby uses Heaters takeover, drops Preheat/Cooldown swap"
```

---

## Task 8: Delete superseded code + dead icons + final gates

**Files:**
- Delete: `app/src/main/java/works/mees/dinghy/ui/printstatus/Preheat.kt`
- Delete: `app/src/test/java/works/mees/dinghy/ui/printstatus/PreheatTest.kt`
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` (remove dead tokens)

- [ ] **Step 1: Delete the files first** (they are the only remaining definitions of `PreheatPath`/`selectPreheatPath`):

```bash
git rm app/src/main/java/works/mees/dinghy/ui/printstatus/Preheat.kt \
       app/src/test/java/works/mees/dinghy/ui/printstatus/PreheatTest.kt
```

- [ ] **Step 2: Confirm nothing else references them** (run AFTER the `git rm`, so the deleted files don't self-match):

```bash
grep -rn "PreheatPath\|selectPreheatPath\|PreheatTest\|PreheatScreen" app/src/main app/src/test
```
Expected: no hits (Task 7 removed the call sites). If any remain, fix them before continuing.

- [ ] **Step 3: Remove dead icon tokens.** Grep each before deleting:

```bash
grep -rn "FootPreheat\|FootCooldown\|TempCooldown" app/src/main
```
For any token with zero remaining references, remove its `val` declaration AND its entry in the `all` list in `DinghyIcons.kt`. **Keep `TempPresets`** (used by `HeatPresetsScreen.kt:126`). Keep `HideTemps` (Console). Keep `OutputHeater` (now the Heaters button).

- [ ] **Step 4: Full verification — suite + ligatures + release shrink**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" | tr -d '\r'
python3 tools/verify_ligatures.py
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleRelease --no-daemon" | tr -d '\r'
```
Expected: suite green; ligatures pass; R8 release build succeeds (catches any kept-rule/keep issues + dead-code references).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore(heaters): remove superseded Preheat path + dead preheat/cooldown icons"
```

---

## On-device UAT (after Task 8 — owner-driven, both devices)

Per `[[dinghy-test-devices]]`, install the matching ABI slice on **flox** (Nexus 7, armeabi-v7a) and **moto** (arm64-v8a). Verify on a live printer (Ender 5 Plus `192.168.1.120:7125` or Ender 3 Pro `192.168.1.121:7125`):

1. **Home (standby):** Heaters button (`mode_heat`) present and static (doesn't change with heater state). Tap → list shows OFF (top) · loaded spool (if loaded, tinted) · presets. Pick a preset → heaters set, returns to home. OFF → all heaters off.
2. **Extrude:** Heaters button replaces Cooldown; takeover applies **extruder only** (bed untouched); OFF turns off only the nozzle; no inline chips remain.
3. **Temperature → Adjust:** footer is one row `[Back · Heaters · Monitor]`; Monitor→Adjust toggle shows `fire_check`; Heaters takeover includes OFF; bed-only spool still appears.
4. **Spool row:** label = filament name; icon tinted to filament color; applies nozzle+bed on Home/Temp.
5. **Bedless / multi-tool sanity** (if testable): no invalid bed gcode; ExtruderOnly targets the active tool.

---

## Self-Review (completed)

- **Spec coverage:** OFF row (T3) · spool row + loaded-spool-into-presets (T2/T3) · three entry points (T5/T6/T7) · `mode_heat` reuse + `fire_check`/`thermometer` (T1) · scope mapping + capability filter (T3) · dispatch keys/OFF delivery (T2/T4) · Home standby-only scope (T7) · deletions (T8). All mapped.
- **Placeholders:** none — all steps carry concrete code/commands.
- **Type consistency:** `buildHeatersRows`/`HeatersRow`/`HeatDispatch`/`loadedSpoolTemps`/`heatSummary`/`dispatchHeat`/`HeatScope` names match across Tasks 2–7. `OutputHeater` (mode_heat) is the reused Heaters glyph everywhere.
- **Known executor checks flagged inline:** verify the `Capabilities` constructor in the test (Task 3), confirm import paths (Task 4), confirm each screen's local `capabilities`/`dispatcher` names (Tasks 5–7).
