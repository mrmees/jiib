# Visual Pass — Six Tweaks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Six self-contained visual tweaks across Files, Move, Macros, Temperature, and the shared Sort/Filter control rows.

**Architecture:** Each item is independent. Two items touch shared design-system components — `StepperRow` (additive, defaulted params; existing callers untouched) and `SortRow`/`FilterRow` (the `icon→label` model swap touches all sort/filter call sites). All glyphs are owner-specified; no independent icon picks (icon law).

**Tech Stack:** Kotlin, Jetpack Compose + classic-Views hybrid, DataStore, the project's `DinghyType` named-role type system and `DinghyIcons` ligature registry.

**Build/verify reality:** these are visual changes. Acceptance = a clean release+debug build, a green `verify_ligatures.py` gate, and **owner UAT on flox (Nexus 7) + moto** per project convention (`memory: dinghy-display-ondevice-iteration`). Pure-testable logic (`titleCase`) gets a unit test; the rest is build + on-device. Build via the Windows helper (`memory: dinghy-display-build-env`):
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'
```
Ensure **LF** line endings on every commit (`memory: dinghy-crlf-commit-trap`).

---

### Task 1: Temperature graph fills to the frame edges (item 5)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt:526`

- [ ] **Step 1: Zero the graph FocusFrame's content inset**

In the `selectedSensor == null` branch (the default multi-trace graph), change the `FocusFrame`
`contentInset` so the `GraphView` fills to the rounded edge (the frame's `clip(shape)` rounds the
corners). Only this frame — leave the other three `FocusInset / 2` sites (L551/L577/L790) alone.

Replace:
```kotlin
                                contentInset = FocusInset / 2, // shared calibration-focus rhythm
                            ) {
                                GraphViewHost(
```
with:
```kotlin
                                contentInset = 0.dp, // graph fills to the rounded frame edge (owner 2026-06-17)
                            ) {
                                GraphViewHost(
```
(`0.dp` — `androidx.compose.ui.unit.dp` is already imported at L36.)

- [ ] **Step 2: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
git commit -m "fix(temp): graph fills to the rounded frame edge"
```

---

### Task 2: Title Case heater names (item 6)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt:308-320`
- Modify: `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt:822-823`
- Test: `app/src/test/java/works/mees/dinghy/ui/temperature/` (add a titleCase test; grep for existing label tests first)

- [ ] **Step 1: Grep for existing tests asserting uppercase labels**

Run: `grep -rn "NOZZLE\|\"BED\"\|titleCase\|fun label" app/src/test app/src/androidTest --include=*.kt`
Expected: note any test asserting `"NOZZLE"`/`"BED"`/uppercased sensor labels — those must flip to Title Case in Step 4.

- [ ] **Step 2: Add an internal `titleCase` helper + rewrite `label()`**

In `TemperatureHolder.kt`, replace the `label()` function (L308-320) and its KDoc:
```kotlin
/**
 * Title-cases a raw token: lowercases, splits on space + underscore, capitalizes each word.
 * e.g. `mcu_temp` → "Mcu Temp", `chamber` → "Chamber". Internal so the same-package
 * TemperatureScreen sensor picker can reuse it (DRY).
 */
internal fun titleCase(raw: String): String =
    raw.lowercase()
        .split(Regex("[\\s_]+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/**
 * Short Title-Case label for a heater object name (owner 2026-06-17 — was UPPERCASE):
 * - `extruder` → "Nozzle"; `extruder1`/`extruder2` → "Nozzle 1"/… ; `heater_bed` → "Bed";
 * - `heater_generic <name>` / `temperature_sensor <name>` → title-cased name; else → title-cased.
 */
private fun label(objectName: String): String = when {
    objectName == "extruder" -> "Nozzle"
    objectName.startsWith("extruder") -> "Nozzle ${objectName.removePrefix("extruder")}"
    objectName == "heater_bed" -> "Bed"
    objectName.startsWith("heater_generic ") -> titleCase(objectName.removePrefix("heater_generic "))
    objectName.startsWith("temperature_sensor ") -> titleCase(objectName.removePrefix("temperature_sensor "))
    else -> titleCase(objectName)
}
```

- [ ] **Step 3: Reuse `titleCase` in the sensor picker label**

In `TemperatureScreen.kt`, replace `sensorPickerLabel` (L822-823):
```kotlin
private fun sensorPickerLabel(objectName: String): String =
    titleCase(objectName.removePrefix("temperature_sensor "))
```
(`titleCase` is `internal` in the same `works.mees.dinghy.ui.temperature` package — no import needed.)

- [ ] **Step 4: Update any uppercase-asserting tests found in Step 1**

For each test found, flip the expected value to Title Case (e.g. `"NOZZLE"` → `"Nozzle"`,
`"BED"` → `"Bed"`). If none were found, skip.

- [ ] **Step 5: Add a `titleCase` unit test**

Create `app/src/test/java/works/mees/dinghy/ui/temperature/TitleCaseTest.kt`:
```kotlin
package works.mees.dinghy.ui.temperature

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleCaseTest {
    @Test fun titleCases_single_word() {
        assertEquals("Chamber", titleCase("chamber"))
    }

    @Test fun titleCases_underscored_token() {
        assertEquals("Mcu Temp", titleCase("mcu_temp"))
    }

    @Test fun titleCases_spaced_token() {
        assertEquals("Pi Cpu", titleCase("pi cpu"))
    }

    @Test fun collapses_empty_segments() {
        assertEquals("A B", titleCase("a__b"))
    }

    @Test fun collapses_mixed_whitespace_and_underscores() {
        assertEquals("A B C", titleCase("a \t b__c"))
    }
}
```

- [ ] **Step 6: Run the test**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests *TitleCaseTest --no-daemon" | tr -d '\r'`
Expected: `BUILD SUCCESSFUL`, all 4 pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/temperature/ app/src/test/java/works/mees/dinghy/ui/temperature/TitleCaseTest.kt
git commit -m "feat(temp): Title Case heater + sensor names"
```

---

### Task 3: Microstep step-size cycler uses `stat_minus_1` / `stat_1` (item 3)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` (register two icons + add to the icon-set list)
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/StepperRow.kt` (additive icon-override params)
- Modify: `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt:515-528` (pass the new icons to the step-size cycler only)
- Modify: `img/material-icon-bucket.json` (registry note entries)

- [ ] **Step 1: Register the two glyphs in `DinghyIcons.kt`**

After the `Increase` definition (L187), add:
```kotlin
    // Increment-SELECTION step −/+ (owner 2026-06-17). USE WHEREVER AN INCREMENT SELECTION IS
    // ADJUSTED (the Move Microstep step-size cycler) — distinct from the generic value ± (Decrease/
    // Increase = remove/add). Both ligatures verified present in the bundled v2.944 ttf.
    val StatMinus1 = DinghyIcon(IconRef.Ligature("stat_minus_1"), alternate = "decrement_one")
    val StatPlus1 = DinghyIcon(IconRef.Ligature("stat_1"), alternate = "increment_one")
```

- [ ] **Step 2: Add them to the registry's icon-set list (if one exists)**

Run: `grep -n "Visibility, VisibilityOff" app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`
That line (~L366) is a flat list of all tokens (used for validation/iteration). Add `StatMinus1, StatPlus1,` to it. If no such aggregate list references the new tokens, skip — the `verify_ligatures.py` gate scrapes the `val … = DinghyIcon(IconRef.Ligature(...))` declarations directly.

- [ ] **Step 3: Run the ligature gate**

Run: `python tools/verify_ligatures.py`
Expected: exit 0, missing: `[]` (both glyphs already confirmed present 2026-06-17).

- [ ] **Step 4: Add additive icon-override params to `StepperRow`**

In `StepperRow.kt`, add two params (defaults preserve every existing caller) to the `StepperRow`
signature, after `increaseContentDescription` (L101):
```kotlin
    decreaseContentDescription: String? = null,
    increaseContentDescription: String? = null,
    decrementIcon: DinghyIcon = DinghyIcons.Decrease,
    incrementIcon: DinghyIcon = DinghyIcons.Increase,
) {
```
Then in the two `OutlinedControl` tiles, use the params instead of the hardcoded tokens:
- L129 `icon = DinghyIcons.Decrease,` → `icon = decrementIcon,`
- L149 `icon = DinghyIcons.Increase,` → `icon = incrementIcon,`

Update the StepperRow KDoc `## Anatomy` line to note the icons default to Decrease/Increase but can be
overridden for increment-selection cyclers (microstep). (`DinghyIcon` is already imported at L20.)

- [ ] **Step 5: Pass the new icons to the microstep step-size cycler ONLY**

In `MoveScreen.kt`, the **step-size cycler** `StepperRow` (L515-528, `Intent.Accent`, has a `center`
slot). Add the override args. Do NOT touch the jog ±-pair StepperRow at L531 (it drives real motion —
keeps Decrease/Increase). Change:
```kotlin
                                    StepperRow(
                                        onDecrement = { stepIndex = (stepIndex - 1 + steps.size) % steps.size },
                                        onIncrement = { stepIndex = (stepIndex + 1) % steps.size },
                                        uDp = grid.uDp,
                                        intent = Intent.Accent,
                                        decrementIcon = DinghyIcons.StatMinus1,
                                        incrementIcon = DinghyIcons.StatPlus1,
                                        center = {
```
(`DinghyIcons` is already imported in MoveScreen.kt.)

- [ ] **Step 6: Add registry notes to `material-icon-bucket.json`**

Add two bucket entries (mirror the existing `add`/`remove` entry shape — `family`, `iconName`, `label`,
`axes`, `color`, `size`, `notes`, `createdAt`, `updatedAt`, `key`, `url`) for `stat_minus_1` and
`stat_1`, with:
```json
  "notes": "increment-selection step − / + — use wherever an increment SELECTION is adjusted (Move Microstep step-size cycler)"
```
Use a fixed timestamp (e.g. `1781000000000`) for `createdAt`/`updatedAt` — `Date.now()` is unavailable;
copy the format from a neighboring entry. `key` = `"Material Symbols Outlined::stat_minus_1"` /
`"…::stat_1"`.

- [ ] **Step 7: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt \
        app/src/main/java/works/mees/dinghy/designsystem/components/StepperRow.kt \
        app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt \
        img/material-icon-bucket.json
git commit -m "feat(move): stat_minus_1/stat_1 on the microstep step-size cycler"
```

---

### Task 4: Move → Z screen, five columns (item 2)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt:391-450` (the `MoveMode.Z` body)
- Modify: `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt:814-863` (refactor `ZScrubberColumn`, add `ZRangeLabels`)

- [ ] **Step 1: Confirm imports for `floor` and `Spacer`**

Run: `grep -n "import kotlin.math.floor\|import androidx.compose.foundation.layout.Spacer" app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt`
If either is absent, add it with the other imports. (`Spacer` is `androidx.compose.foundation.layout.Spacer`; `floor` is `kotlin.math.floor`.)

- [ ] **Step 2: Replace the `MoveMode.Z` body with the five-column layout**

Replace L401-449 (the `Row { ZScrubberColumn(Fine) … Box(Z value) … ZScrubberColumn(Full) }`) with:
```kotlin
                                // Five columns: [fine labels] [fine slider] [Z value] [full slider]
                                // [full labels]. Range labels flank each slider as their own columns
                                // (owner 2026-06-17). Center Z value matches the X/Y coordinate text.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                ) {
                                    // Col 1 — Fine range labels: 50 (top) / 0 (bottom).
                                    ZRangeLabels(top = "50", bottom = "0")
                                    // Col 2 — Fine slider: 0–50 mm @ 0.1 mm.
                                    ZScrubberColumn(
                                        name = "Fine",
                                        value = workingZ,
                                        range = 0f..50f,
                                        step = 0.1f,
                                        uDp = grid.uDp,
                                        modifier = Modifier.weight(1f),
                                        onValueChange = { workingZ = it },
                                        onSettle = { v ->
                                            workingZ = v
                                            onMoveTo(null, null, workingZ.toDouble())
                                        },
                                    )
                                    // Col 3 — Z value, centered; matches the X/Y coordinate readout
                                    // (statValue, 26sp) on the Touch Move / XY focuses (owner 2026-06-17).
                                    Box(
                                        Modifier.fillMaxHeight().padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.2f", workingZ) + "mm",
                                            style = DinghyType.statValue.toTextStyle(t),
                                            color = t.text,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                    // Col 4 — Full slider: 0–Zmax @ 1 mm.
                                    ZScrubberColumn(
                                        name = "Full",
                                        value = workingZ,
                                        range = 0f..zMax,
                                        step = 1f,
                                        uDp = grid.uDp,
                                        modifier = Modifier.weight(1f),
                                        onValueChange = { workingZ = it },
                                        onSettle = { v ->
                                            workingZ = v
                                            onMoveTo(null, null, workingZ.toDouble())
                                        },
                                    )
                                    // Col 5 — Full range labels: floor(Zmax) (top) / 0 (bottom).
                                    ZRangeLabels(top = floor(zMax).toInt().toString(), bottom = "0")
                                }
```

- [ ] **Step 3: Refactor `ZScrubberColumn` to a bare scrubber column + add `ZRangeLabels`**

Replace the whole `ZScrubberColumn` (L819-863) with these two composables:
```kotlin
/**
 * One vertical Z scrubber column (Fine 0–50 / Full 0–Zmax). Bare — the endpoint range labels now
 * live in their own flanking [ZRangeLabels] columns (owner 2026-06-17, five-column Z layout). Both
 * sliders are `weight(1f)`-equal via [modifier].
 */
@Composable
private fun ZScrubberColumn(
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    uDp: androidx.compose.ui.unit.Dp,
    onValueChange: (Float) -> Unit,
    onSettle: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Scrubber(
            name = name,
            value = value,
            range = range,
            step = step,
            uDp = uDp,
            unit = "mm",
            orientation = ScrubberOrientation.Vertical,
            onValueChange = onValueChange,
            onSettle = onSettle,
        )
    }
}

/**
 * A range-label column flanking a Z scrubber: [top] pushed to the top of the height, [bottom] to the
 * bottom. Sized at the Z value's CURRENT size (dataInline, 20sp — owner ruling 2026-06-17; NOT the
 * 26sp the center readout grows to). Muted via `t.text2`.
 */
@Composable
private fun ZRangeLabels(top: String, bottom: String) {
    val t = LocalTokens.current
    Column(
        modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = top, style = DinghyType.dataInline.toTextStyle(t), color = t.text2)
        Spacer(Modifier.weight(1f))
        Text(text = bottom, style = DinghyType.dataInline.toTextStyle(t), color = t.text2)
    }
}
```

- [ ] **Step 4: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: `BUILD SUCCESSFUL`. (Watch for an unused-param warning if `topLabel` lingered — it should be fully removed.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
git commit -m "feat(move): five-column Z screen with flanking range labels"
```

---

### Task 5: Edit Macros — bookmark Focus copy + hide/show label (item 4)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt:309-323` (Focus ManageMode body)
- Modify: `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt:635-642` (remove underscore note from list)
- Modify: `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt:686-691` (foot button label tracks icon)
- Modify: `app/src/main/res/values/strings.xml` (new strings)

- [ ] **Step 1: Add the new strings**

In `strings.xml`, after `macros_focus_select_prompt` (L432) add:
```xml
    <string name="macros_bookmark_explainer">Bookmarked macros show on the home screen for quick access. Pin the ones you use most.</string>
    <string name="macros_foot_show">Show</string>
    <string name="macros_foot_hide">Hide</string>
```
Leave `macros_helper_hint` (L427) in place — it gets relocated, not deleted. Delete the now-unused
`macros_foot_show_hidden` (L431).

- [ ] **Step 2: Give ManageMode its own Focus body (explainer + relocated underscore note)**

In the Focus `when` block (L309-323), split the combined `ManageMode || liveMacro == null` branch so
ManageMode gets the explainer and the launcher empty-state keeps its prompt. Replace:
```kotlin
                    when {
                        state.unavailable -> MacrosUnavailable(modifier = Modifier.fillMaxWidth())
                        fieldMode is MacroFieldMode.ManageMode || liveMacro == null -> {
                            // Vertically centered prompt (owner UAT 2026-06-15) — a top-anchored text
                            // body floats high under the header divider; center it for readability.
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.macros_focus_select_prompt),
                                    color = t.text2,
                                    style = DinghyType.body.toTextStyle(t),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
```
with:
```kotlin
                    when {
                        state.unavailable -> MacrosUnavailable(modifier = Modifier.fillMaxWidth())
                        fieldMode is MacroFieldMode.ManageMode -> {
                            // Manage Focus = what bookmarking does + the relocated underscore note
                            // (owner 2026-06-17 — moved here from the top of the manage list).
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.macros_bookmark_explainer),
                                        color = t.text2,
                                        style = DinghyType.body.toTextStyle(t),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = stringResource(R.string.macros_helper_hint),
                                        color = t.text2,
                                        style = DinghyType.caption.toTextStyle(t),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        liveMacro == null -> {
                            // Launcher empty-state: nothing selected yet.
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.macros_focus_select_prompt),
                                    color = t.text2,
                                    style = DinghyType.body.toTextStyle(t),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
```
Confirm imports: `Column`, `Arrangement` (`androidx.compose.foundation.layout.*`), `spacedBy`. If
`Arrangement` is not imported, add `import androidx.compose.foundation.layout.Arrangement`.

- [ ] **Step 3: Remove the underscore note from the top of the manage list**

In `MacroManageField` (L635-642), delete the `Text(... macros_helper_hint ...)` block entirely so the
`else` branch begins directly with `ListBlock`. Resulting structure:
```kotlin
    } else {
        ListBlock(modifier = Modifier.weight(1f)) {
            items(state.visibleMacros, key = { it.name }) { macro ->
```

- [ ] **Step 4: Make the show-hidden foot button label track its icon**

In `MacroManageField`'s `FootButtonBar` (L686-691), replace the show-hidden `FootAction`:
```kotlin
            FootAction(
                label = stringResource(
                    if (state.revealHidden) R.string.macros_foot_hide else R.string.macros_foot_show,
                ),
                icon = if (state.revealHidden) DinghyIcons.Visibility else DinghyIcons.VisibilityOff,
                onClick = { onSetRevealHidden(!state.revealHidden) },
                intent = if (state.revealHidden) Intent.Accent else Intent.Neutral,
            ),
```
(`visibility_off` ⇒ "Show", `visibility` ⇒ "Hide" — owner-specified pairing, icon law satisfied.)

- [ ] **Step 5: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: `BUILD SUCCESSFUL`. If Android lint flags `macros_foot_show_hidden` as still-referenced, grep for stragglers: `grep -rn "macros_foot_show_hidden" app/src`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(macros): bookmark explainer in Focus; show/hide label tracks icon"
```

---

### Task 6: Sort/Filter rows — drop type-tiles, text labels (item 1, Files + Spoolman)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/SortFilterControlRow.kt` (model `icon→label`; presets drop `leadingTypeTile`)
- Modify: `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt:373-386` (Files sort labels)
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt:290-330` (Spool sort + filter labels)
- Modify: `app/src/main/java/works/mees/dinghy/preview/DesignKitComponentPreviews.kt:95-137` (preview labels)
- Modify: `app/src/main/res/values/strings.xml` (new label strings)
- Modify: `docs/ui_design/COMPONENTS.md` (retire the "leading TYPE tile is MANDATORY" law)

- [ ] **Step 1: Swap `icon → label` on `SortOption`/`FilterOption`; drop the type-tile in both presets**

In `SortFilterControlRow.kt`:

`SortOption` data class — replace `icon` with `label`:
```kotlin
data class SortOption<K>(
    val key: K,
    val label: String,
    val contentDescriptionRes: Int,
    val directionUp: Boolean? = null,
)
```
`FilterOption` data class — replace `icon` with `label`:
```kotlin
data class FilterOption<K>(
    val key: K,
    val label: String,
    val contentDescriptionRes: Int,
    val isActive: Boolean,
)
```
`SortRow` preset body — map `label`, drop `leadingTypeTile`:
```kotlin
    SelectorRow(
        options = options.map { opt ->
            val active = opt.key == activeKey
            SelectorOption(
                key = opt.key,
                label = opt.label,
                isActive = active,
                directionIcon = if (active) sortDirectionIcon(opt.directionUp) else null,
                contentDescription = stringResource(opt.contentDescriptionRes),
            )
        },
        onSelect = onSelect,
        uDp = uDp,
        modifier = modifier,
        // Type-tile retired (owner 2026-06-17) — text-label tiles, no leading Sort glyph.
    )
```
`FilterRow` preset body — map `label`, drop `leadingTypeTile`:
```kotlin
    SelectorRow(
        options = options.map { opt ->
            SelectorOption(
                key = opt.key,
                label = opt.label,
                isActive = opt.isActive,
                contentDescription = stringResource(opt.contentDescriptionRes),
            )
        },
        onSelect = onSelect,
        uDp = uDp,
        modifier = modifier,
        // Type-tile retired (owner 2026-06-17) — text-label tiles, no leading Filter glyph.
    )
```
Update the `SortOption`/`FilterOption`/`SortRow`/`FilterRow` KDoc: the leading type-tile is RETIRED;
tiles carry a text `label`, not a glyph. Remove the now-unused imports `DinghyIcon` and `DinghyIcons`
from this file (build will warn if they linger).

- [ ] **Step 2: Add the label strings**

In `strings.xml`, near the existing `cd_files_sort_*` / `cd_spool_sort_*` blocks, add:
```xml
    <string name="files_sort_date">Date</string>
    <string name="files_sort_size">Size</string>
    <string name="spool_sort_name">Name</string>
    <string name="spool_sort_date">Date</string>
    <string name="spool_sort_remaining">Remaining</string>
    <string name="spool_filter_type">Material</string>
    <string name="spool_filter_color">Color</string>
    <string name="spool_filter_mfg">Brand</string>
```

- [ ] **Step 3: Update the Files sort call site**

In `FilesScreen.kt` (L373-386), replace `icon = …` with `label = …`:
```kotlin
        val sortOptions = persistentListOf(
            SortOption(
                key = FileSortField.Date,
                label = stringResource(R.string.files_sort_date),
                contentDescriptionRes = R.string.cd_files_sort_date,
                directionUp = if (state.sortField == FileSortField.Date) state.sortAscending else null,
            ),
            SortOption(
                key = FileSortField.Size,
                label = stringResource(R.string.files_sort_size),
                contentDescriptionRes = R.string.cd_files_sort_size,
                directionUp = if (state.sortField == FileSortField.Size) state.sortAscending else null,
            ),
        )
```
Remove now-unused icon imports (`DinghyIcons.CalendarClock`/`LineWeight`) if they are referenced
nowhere else in the file: `grep -n "CalendarClock\|LineWeight" app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt`.

- [ ] **Step 4: Update the Spool sort + filter call sites**

In `SpoolScreen.kt` (L290-330), replace each `icon = …` with `label = …`:
```kotlin
        val sortOptions = persistentListOf(
            SortOption(
                key = SpoolSortKey.NAME,
                label = stringResource(R.string.spool_sort_name),
                contentDescriptionRes = R.string.cd_spool_sort_name,
                directionUp = if (state.sortKey == SpoolSortKey.NAME) state.sortAscending else null,
            ),
            SortOption(
                key = SpoolSortKey.DATE,
                label = stringResource(R.string.spool_sort_date),
                contentDescriptionRes = R.string.cd_spool_sort_date,
                directionUp = if (state.sortKey == SpoolSortKey.DATE) state.sortAscending else null,
            ),
            SortOption(
                key = SpoolSortKey.REMAINING,
                label = stringResource(R.string.spool_sort_remaining),
                contentDescriptionRes = R.string.cd_spool_sort_remaining,
                directionUp = if (state.sortKey == SpoolSortKey.REMAINING) state.sortAscending else null,
            ),
        )

        val filterOptions = persistentListOf(
            FilterOption(
                key = SpoolFilterCategory.TYPE,
                label = stringResource(R.string.spool_filter_type),
                contentDescriptionRes = R.string.cd_spool_filter_type,
                isActive = state.filters.materialFamilies.isNotEmpty(),
            ),
            FilterOption(
                key = SpoolFilterCategory.COLOR,
                label = stringResource(R.string.spool_filter_color),
                contentDescriptionRes = R.string.cd_spool_filter_color,
                isActive = state.filters.colorSwatchHex != null,
            ),
            FilterOption(
                key = SpoolFilterCategory.MFG,
                label = stringResource(R.string.spool_filter_mfg),
                contentDescriptionRes = R.string.cd_spool_filter_mfg,
                isActive = state.filters.vendors.isNotEmpty(),
            ),
        )
```
Remove now-unused icon imports (`MatchCase`, `Scale`, `Experiment`, `Palette`, `Storefront`) if not
referenced elsewhere in the file (grep each before removing — several may be used by spool detail/list).

- [ ] **Step 5: Update the DesignKit preview call sites**

In `DesignKitComponentPreviews.kt` (L95-137), replace `icon = …` with `label = …` on each sample
`SortOption`/`FilterOption`. Preview is debug-only — use literal strings:
- sort: `label = "Name"`, `label = "Material"`, `label = "Weight"`
- filter: `label = "Material"`, `label = "Color"`, `label = "Vendor"`

Remove now-unused icon imports if the preview references them nowhere else.

- [ ] **Step 6: Retire the type-tile law everywhere it is asserted (full sweep)**

The "leading TYPE tile is MANDATORY" claim appears in MULTIPLE places (Codex review 2026-06-17) — sweep
ALL of them, not just one paragraph:
```bash
grep -rn "leading TYPE tile\|leadingTypeTile\|type tile\|TYPE tile\|NON-CONFORMANT" \
  docs/ui_design/COMPONENTS.md \
  app/src/main/java/works/mees/dinghy/designsystem/components/SelectorRow.kt \
  app/src/main/java/works/mees/dinghy/designsystem/components/SortFilterControlRow.kt
```
- `COMPONENTS.md`: update the catalog row, the §3 SortFilterControlRow anatomy text, the icon-registry
  note, AND the §6 invariants — everywhere the mandatory type-tile is stated. Sort/Filter rows are now
  text-label tiles (no leading glyph); keep the Sort direction-overlay description. Date the change.
- `SelectorRow.kt`: its `## Anatomy (LOCKED for Sort/Filter — COMPONENTS.md §3)` KDoc (~L104-114) calls
  the leading type-tile mandatory — revise to "optional; retired for Sort/Filter 2026-06-17, retained as
  a general `SelectorRow` capability". The `leadingTypeTile` param itself STAYS (now always null from the
  presets) — only the prose changes.
- `SortFilterControlRow.kt`: the `SortRow`/`FilterRow` KDoc "leading TYPE tile is MANDATORY ... A bare
  option-list Row without the type tile is NON-CONFORMANT" — rewrite to the text-label form.

- [ ] **Step 7: Grep for + update any tests asserting the old icon/type-tile form**

Run: `grep -rn "SortOption(\|FilterOption(\|leadingTypeTile\|DinghyIcons.Sort\b\|onNodeWithContentDescription(\"Sort" app/src/test app/src/androidTest --include=*.kt`
For each test constructing `SortOption(icon=…)`/`FilterOption(icon=…)` or asserting a type-tile/icon-only
sort tile, update it to the `label` form. (Compile failure on the `icon=` named arg will pinpoint them
if any are missed.)

- [ ] **Step 8: Build (debug + release to catch R8/lint)**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: `BUILD SUCCESSFUL`. Then the unit-test sourceset:
`/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" | tr -d '\r'`
Expected: `BUILD SUCCESSFUL` (all tests green after Step 7 fixes).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/SortFilterControlRow.kt \
        app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt \
        app/src/main/java/works/mees/dinghy/preview/DesignKitComponentPreviews.kt \
        app/src/main/res/values/strings.xml docs/ui_design/COMPONENTS.md \
        app/src/test
git commit -m "feat(ui): retire sort/filter type-tiles for text labels (Files + Spoolman)"
```

---

### Task 7: Full build + on-device UAT

- [ ] **Step 1: Re-run the ligature gate**

Run: `python tools/verify_ligatures.py`
Expected: exit 0.

- [ ] **Step 2: Assemble both ABI slices (force rebuild — stale-APK trap)**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: `BUILD SUCCESSFUL`. (`--rerun-tasks` defeats the stale-APK UAT trap — `memory: dinghy-stale-apk-uat-gate`.) Verify the APK mtime is newer than the last commit before installing.

- [ ] **Step 2b: Release/R8 + lint gate (Codex review 2026-06-17)**

The debug build does NOT run R8/shrink or the release lint gate (lint `abortOnError` is enforced in
`app/build.gradle.kts`). Unused resources/imports left by the `icon→label` swap and the deleted
`macros_foot_show_hidden` string surface HERE, not in debug.

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleRelease --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: `BUILD SUCCESSFUL`. Fix any lint/R8 failures (most likely unused-resource for a stale string,
or unused-import for a dropped icon token) before installing.

- [ ] **Step 3: Install on BOTH test devices**

Push the matching ABI slice to flox (Nexus 7, armeabi-v7a, id `0a64b42e`) AND moto (arm64-v8a, id
`ZY22LBDRM9`) — `memory: dinghy-test-devices`.

- [ ] **Step 4: Owner UAT checklist**

Walk Matthew through each surface:
1. Files — sort row shows "Date"/"Size" text, no leading sort glyph; active sort keeps its asc/desc arrow.
2. Spoolman — sort ("Name"/"Date"/"Remaining") + filter ("Material"/"Color"/"Brand") text tiles, no type-tiles. Confirm the Spool labels read right (author picks — owner may want different words).
3. Move → Z — five columns; 20sp flank labels (50/0, ⌊Zmax⌋/0), 26sp center Z matching X/Y.
4. Move → Microstep — step-size cycler shows the stat −1 / +1 glyphs; jog ± pair unchanged.
5. Macros → Edit — Focus shows the bookmarking explainer + underscore note; list no longer has the top note; foot button reads "Show" (eye-off) ↔ "Hide" (eye).
6. Temperature — graph fills to the rounded frame edge (no bottom gap).
7. Temperature list — heater names in Title Case (Nozzle / Bed / sensor names).

- [ ] **Step 5: Address UAT feedback, then finalize**

Iterate per owner notes (esp. Spool label wording / Z label sizing). When approved, the per-task commits
stand; offer to push to `origin` (Windows `git.exe` — `memory: dinghy-display-git-remote`).

---

## Self-Review notes

- **Spec coverage:** all six items mapped to tasks (5→T1, 6→T2, 3→T3, 2→T4, 4→T5, 1→T6) + a UAT task.
- **Shared-component safety:** `StepperRow` change is additive (defaulted params) — its 5 callers compile unchanged; only the microstep cycler opts in. The `SortOption`/`FilterOption` `icon→label` swap is a compile-enforced breaking change — every call site is updated in T6 (Files, Spool, preview) and tests swept in T6 Step 7.
- **Icon law:** every glyph (`stat_minus_1`, `stat_1`, `visibility`/`visibility_off`) is owner-specified; the sort/filter change REMOVES glyphs (text labels) — no new picks. New glyphs verified present in the bundled font.
- **Author-pick flag:** Spool sort/filter label wording is an author choice pending owner UAT (T6 / T7).
