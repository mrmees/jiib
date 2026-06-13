# Setting-Adjustment Focus Compliance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring every value-adjustment Focus (Fine-Tune, Temperature, Outputs) onto one compliant grammar — header carries the selected control's identity, revert-to-default is a bare end-aligned header glyph, ± are icons, all in-Focus controls cap at 1U, control groups dock to the bottom at the calibration inset — and codify the resulting rules in `docs/ui_design/`.

**Architecture:** A cross-component compliance sweep over already-shared building blocks. The header identity + bare trailing-action slot live in `FocusFrame`; the stepper/value layout lives in `AdjusterPanel`; drag adjust lives in `Scrubber`; the step-set selector is `IncrementPicker`. Screens (`FineTuneScreen`, `TemperatureScreen`, `OutputsScreen`) wire the selected item into the `FocusFrame` header. Tasks are ordered so every intermediate commit compiles and stays functional (header identity is added *before* the redundant in-panel identity is removed, so nothing ever loses its "which control am I editing?" label mid-plan).

**Tech Stack:** Kotlin, Jetpack Compose, Material Symbols (DinghyIcons registry), Gradle (Windows-side build via `E:\Android\gw.bat`).

**Build / test commands (this repo builds Windows-side — `./gradlew` does NOT work from WSL):**
- Build debug APK: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
- Host unit tests: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" | tr -d '\r'`
- Ligature gate: `python tools/verify_ligatures.py` (run from repo root)
- The process exit code is authoritative; Gradle CR progress bars are stripped by `tr -d '\r'`.

**On-device reality (from project memory):** UI structural changes are verified by **build-pass + on-device UAT on BOTH test targets** (flox = Nexus 7 2013 armeabi-v7a, moto = Moto G Play arm64-v8a). Host unit tests cover pure logic only. Push the matching ABI slice to BOTH during UAT. Beware the stale-APK trap: confirm the installed APK mtime is after the fix before testing.

---

## Spec coverage map

| Spec section | Task(s) |
|--------------|---------|
| A. Header carries selection | 5 (Fine-Tune), 6 (Temperature), 7 (Outputs), 8 (removes redundant in-panel identity) |
| B. Revert = bare header glyph | 1 (icon), 2 (slot), 5 (Fine-Tune wiring) |
| C. ± become icons | 4 (Scrubber), 8 (AdjusterPanel) |
| D. 1U caps | 3 (IncrementPicker), 4 (Scrubber ± row), 8 (AdjusterPanel ± row) |
| E. Bottom-dock + shared inset | 5, 6, 7 (contentInset), 8 (AdjusterPanel bottom-dock) |
| F. Codify rules | 9 (docs) |

---

## Task 1: Register the `Revert` icon token

The revert-to-default header glyph uses the `refresh` ligature (owner-chosen; present in `img/material-icon-bucket.json` line 1123, unregistered). The existing `DinghyIconsTest` iterates `DinghyIcons.all` and enforces non-blank/unique alternate + resolvable ref, so registration is the whole change.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` (add the val near `Decrease`/`Increase` ~line 170; add to the `all` list ~line 327)
- Test: `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt` (existing — no edit, just runs)

- [ ] **Step 1: Add the token** next to the other adjustment glyphs (after the `InputCircle` line ~171):

```kotlin
    val InputCircle = DinghyIcon(IconRef.Ligature("input_circle"), alternate = "input_circle")
    // Revert-to-default header action (setting-adjustment compliance, 2026-06-13; owner-chosen
    // `refresh` ligature, img/material-icon-bucket.json). Bare end-aligned glyph in the Focus header.
    val Revert = DinghyIcon(IconRef.Ligature("refresh"), alternate = "revert")
```

- [ ] **Step 2: Add `Revert` to the `all` list** — append it to the adjustment-glyph line in `val all` (the line containing `Decrease, Increase, InputCircle,`):

```kotlin
        PressureAdvance, SmoothTime, Decrease, Increase, InputCircle, Revert,
```

- [ ] **Step 3: Run the registry test + ligature gate**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"*DinghyIconsTest\" --no-daemon" | tr -d '\r'`
Then: `python tools/verify_ligatures.py`
Expected: tests PASS (alternate "revert" unique, ligature "refresh" non-blank); ligature gate exits 0 (`refresh` resolves in the bundled ttf).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
git commit -m "feat(icons): register Revert (refresh) token for the adjuster header"
```

---

## Task 2: Add the end-aligned bare-glyph header action slot to `FocusFrame`

Additive and fully back-compatible (new params default to null → the 21 existing call sites are unchanged). A bare tappable glyph mirrored from the start identity icon: neutral `text2` tint, sized `slot * IDENTITY_ICON_RATIO`, aligned `CenterEnd`, rendered only when both icon + handler are non-null. The centered title already reserves `slot` on BOTH sides (its `padding(horizontal = slot)`), so it clears the trailing glyph automatically.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt`

- [ ] **Step 1: Add the params to `FocusFrame`** — insert after `contentInset` in the signature (~line 142):

```kotlin
    contentInset: Dp = FocusInset,
    trailingActionIcon: DinghyIcon? = null,
    onTrailingAction: (() -> Unit)? = null,
    trailingActionContentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
```

- [ ] **Step 2: Pass them into `FocusHeader`** — update the `FocusHeader(...)` call inside `FocusFrame` (~line 158):

```kotlin
        FocusHeader(
            title = title,
            icon = icon,
            uDp = uDp,
            isPrinting = isPrinting,
            onEmergencyStop = onEmergencyStop,
            onPanic = onPanic,
            trailingActionIcon = trailingActionIcon,
            onTrailingAction = onTrailingAction,
            trailingActionContentDescription = trailingActionContentDescription,
        )
```

- [ ] **Step 3: Extend `FocusHeader`** — add the params to its signature (~line 185) and render the trailing glyph. Add params after `onPanic`:

```kotlin
private fun FocusHeader(
    title: String,
    icon: DinghyIcon,
    uDp: Dp,
    isPrinting: Boolean,
    onEmergencyStop: (() -> Unit)?,
    onPanic: (() -> Unit)?,
    trailingActionIcon: DinghyIcon? = null,
    onTrailingAction: (() -> Unit)? = null,
    trailingActionContentDescription: String? = null,
) {
```

- [ ] **Step 4: Render the bare end glyph** — inside `FocusHeader`'s `Box`, after the start-icon `Box(modifier = Modifier.align(Alignment.CenterStart)) { ... }` block (~line 243), add:

```kotlin
        // End slot: optional BARE tappable action glyph (setting-adjustment compliance, 2026-06-13).
        // Mirrors the start identity icon — neutral text2 tint, same IDENTITY_ICON_RATIO size, NO
        // outline/fill. Reserved for SAFE actions only (e.g. revert-to-default); anything caution/
        // destructive stays a content button under the four-class intent law (THEMING.md).
        if (trailingActionIcon != null && onTrailingAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(slot)
                    .clip(RoundedCornerShape(t.rCtrl))
                    .clickable(onClick = onTrailingAction),
                contentAlignment = Alignment.Center,
            ) {
                DinghyIconView(
                    icon = trailingActionIcon,
                    tint = t.text2,
                    sizeDp = slot * IDENTITY_ICON_RATIO,
                    contentDescription = trailingActionContentDescription,
                )
            }
        }
```

- [ ] **Step 5: Add the missing imports** to `FocusFrame.kt` (top of file, alongside the existing `androidx.compose.foundation.*` imports):

```kotlin
import androidx.compose.foundation.clickable
```

(`RoundedCornerShape`, `size`, `Box`, `Alignment`, `DinghyIconView`, `DinghyIcon`, `t.rCtrl` are already imported/available in this file.)

- [ ] **Step 6: Verify `DinghyIconView` accepts `contentDescription`.**

Run: `grep -n "fun DinghyIconView" app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIconView.kt`
Expected: a `contentDescription: String? = null` param exists (Amendment-1 precedent referenced in `OutlinedControl`). If the param name differs, adjust Step 4 to match; if it has no such param, drop the `contentDescription` argument from the `DinghyIconView` call (the tap target still works).

- [ ] **Step 7: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL (additive change, no existing call site touched).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt
git commit -m "feat(focus): optional bare end-aligned header action slot on FocusFrame"
```

---

## Task 3: Cap `IncrementPicker` at 1U

Today the row is `heightIn(min = uDp)` (floor only — can grow). Cap it at exactly 1U (UAT-5).

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/IncrementPicker.kt`

- [ ] **Step 1: Change the floor to a fixed 1U height** — in the `Row` modifier (~line 49):

```kotlin
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(uDp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
```

- [ ] **Step 2: Swap the import** — replace `import androidx.compose.foundation.layout.heightIn` with:

```kotlin
import androidx.compose.foundation.layout.height
```

- [ ] **Step 3: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/IncrementPicker.kt
git commit -m "fix(adjuster): cap IncrementPicker row at 1U (UAT-5)"
```

---

## Task 4: `Scrubber` — icon ± and 1U cap on the stepper row

The Outputs adjusters (fans/LEDs/servos/PWM pins) use `Scrubber`'s ± row, which currently renders literal `"−"`/`"+"` and has no height cap. Switch to icon tokens and cap the row at 1U. (The gesture row is already capped via `rowHeight = min(uDp, 74dp)`.)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/Scrubber.kt` (the horizontal ± row ~line 399-415)

- [ ] **Step 1: Replace the ± stepper row** (~line 398-415) with icon-only controls + 1U cap:

```kotlin
                // ± stepper row — discrete adjust; each tap is its own settle (ends a discrete gesture).
                Row(
                    Modifier.fillMaxWidth().height(uDp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedControl(
                        label = "",
                        onClick = { set(working - step); settle() },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent, // R5: setting adjustment = accent (neutral retired)
                        icon = DinghyIcons.Decrease,
                        contentDescription = stringResource(R.string.cd_decrement),
                    )
                    OutlinedControl(
                        label = "",
                        onClick = { set(working + step); settle() },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent, // R5: setting adjustment = accent (neutral retired)
                        icon = DinghyIcons.Increase,
                        contentDescription = stringResource(R.string.cd_increment),
                    )
                }
```

- [ ] **Step 2: Add imports** to `Scrubber.kt`:

```kotlin
import androidx.compose.ui.res.stringResource
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.icons.DinghyIcons
```

(`androidx.compose.foundation.layout.height` and `OutlinedControl` are already imported.)

- [ ] **Step 3: Ensure the content-description strings exist.**

Run: `grep -n "cd_decrement\|cd_increment" app/src/main/res/values/strings.xml`
If absent, add to `app/src/main/res/values/strings.xml`:

```xml
    <string name="cd_decrement">Decrease</string>
    <string name="cd_increment">Increase</string>
```

- [ ] **Step 4: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/Scrubber.kt app/src/main/res/values/strings.xml
git commit -m "fix(scrubber): icon ± steppers + 1U cap on the stepper row"
```

---

## Task 5: `FineTuneScreen` — header carries the selected param + revert glyph

Wire the selected param's identity into the `FocusFrame` header, and add the revert glyph as the trailing header action (shown only when off-default). The in-panel Zone-1 identity + reset stay for now (Task 8 removes them) — temporary, harmless redundancy that keeps identity visible the whole time.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt` (the `FocusFrame(...)` at ~line 304)

- [ ] **Step 1: Replace the `FocusFrame` opening** (~line 304-314) so the header reflects the selection, adopts the calibration inset, and gets the revert trailing action:

```kotlin
                FocusFrame(
                    title = selectedParam.name,
                    icon = selectedParam.icon,
                    uDp = grid.uDp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                    contentInset = FocusInset / 2, // shared calibration-focus rhythm (E)
                    trailingActionIcon = run {
                        val v = working[selectedTuner.name] ?: vm.valueForTuner(selectedTuner)
                        val base = vm.baselineForTuner(selectedTuner)
                        if (shouldShowBaseline(v, base, selectedParam.decimals)) DinghyIcons.Revert else null
                    },
                    onTrailingAction = vm.baselineForTuner(selectedTuner)?.let { base ->
                        { onNudgeToBaseline(selectedParam, base) }
                    },
                    trailingActionContentDescription = stringResource(R.string.adjuster_reset),
                ) {
```

- [ ] **Step 2: Add imports** to `FineTuneScreen.kt` (if not already present):

```kotlin
import works.mees.dinghy.designsystem.components.shouldShowBaseline
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.FocusInset
```

Run: `grep -n "import works.mees.dinghy.designsystem.icons.DinghyIcons\|import works.mees.dinghy.designsystem.layout.FocusInset\|shouldShowBaseline" app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt`
Add only the ones missing. (`DinghyIcons` is already imported — it's used for `DinghyIcons.LauncherFineTune` at the old call site.)

- [ ] **Step 3: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL. (At this point the header shows the param + revert; the AdjusterPanel still also shows icon/name/reset — redundant, removed in Task 8.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt
git commit -m "feat(finetune): header carries selected param + revert glyph; calibration inset"
```

---

## Task 6: `TemperatureScreen` — adjuster header swaps to the selected sensor

The overview `FocusFrame` (~line 450, no sensor selected = the multi-trace graph) stays generic. The adjuster `FocusFrame` (~line 473, a sensor is selected) swaps its header to the sensor's name + icon. Both adopt the shared inset. No revert glyph (heaters have no default baseline).

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt` (the two `FocusFrame(...)` calls ~line 450 and ~line 473)

- [ ] **Step 1: Adopt the shared inset on the overview `FocusFrame`** (~line 450) — keep its generic title/icon, add `contentInset`:

```kotlin
                        FocusFrame(
                            title = stringResource(R.string.cd_launcher_temperature),
                            icon = DinghyIcons.LauncherTemperature,
                            uDp = grid.uDp,
                            modifier = Modifier.fillMaxSize(),
                            isPrinting = isPrinting,
                            onEmergencyStop = onEmergencyStop,
                            onPanic = onEmergencyStop,
                            contentInset = FocusInset / 2, // shared calibration-focus rhythm (E)
                        ) {
```

- [ ] **Step 2: Swap the adjuster `FocusFrame` header to the sensor** (~line 473) — `sensor` is the non-null selected `SensorReadout` in scope here:

```kotlin
                        FocusFrame(
                            title = sensor.label,
                            icon = iconForSensor(sensor.name),
                            uDp = grid.uDp,
                            modifier = Modifier.fillMaxSize(),
                            isPrinting = isPrinting,
                            onEmergencyStop = onEmergencyStop,
                            onPanic = onEmergencyStop,
                            contentInset = FocusInset / 2, // shared calibration-focus rhythm (E)
                        ) {
```

- [ ] **Step 3: Add the `FocusInset` import** if missing:

Run: `grep -n "import works.mees.dinghy.designsystem.layout.FocusInset\|fun iconForSensor" app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt`
Expected: `iconForSensor` is defined in this file (it is — ~line 793). Add `import works.mees.dinghy.designsystem.layout.FocusInset` if not already present.

- [ ] **Step 4: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
git commit -m "feat(temperature): adjuster header swaps to selected sensor; shared inset"
```

---

## Task 7: `OutputsScreen` — header carries the selected output

When an output is selected, the `FocusFrame` (~line 162) shows that output's `prettyName` + family glyph instead of the generic section title. `glyphFor(...)` is a private fun in the same file. Adopt the shared inset. No revert (outputs have no default baseline; Off button unchanged).

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt` (the `FocusFrame(...)` at ~line 162)

- [ ] **Step 1: Replace the `FocusFrame` opening** (~line 162-169):

```kotlin
                        FocusFrame(
                            title = selectedRow.descriptor.prettyName,
                            icon = glyphFor(selectedRow.descriptor.family),
                            uDp = grid.uDp,
                            modifier = Modifier.fillMaxSize(),
                            isPrinting = isPrinting,
                            onEmergencyStop = onEmergencyStop,
                            onPanic = onEmergencyStop,
                            contentInset = FocusInset / 2, // shared calibration-focus rhythm (E)
                        ) {
```

- [ ] **Step 2: Add the `FocusInset` import** if missing:

Run: `grep -n "import works.mees.dinghy.designsystem.layout.FocusInset" app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt`
Add `import works.mees.dinghy.designsystem.layout.FocusInset` if absent.

- [ ] **Step 3: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt
git commit -m "feat(outputs): header carries selected output identity; shared inset"
```

---

## Task 8: `AdjusterPanel` — drop Zone 1, bottom-dock controls, icon ±, 1U caps

Now that all three headers carry identity (Tasks 5-7), remove `AdjusterPanel`'s redundant Zone-1 (icon + name + reset). The panel becomes two zones: value (absorbs slack, centered) + bottom-docked controls. ± become icon tokens; the ± row caps at 1U. This is an API change — `icon`, `name`, `onReset` params are removed, so ALL FOUR call sites (2 previews + Fine-Tune + Temperature) must update in this same commit to stay compiling.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt`
- Modify: `app/src/main/java/works/mees/dinghy/preview/AdjusterPreviews.kt` (2 call sites)
- Modify: `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt` (1 call site)
- Modify: `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt` (1 call site)

- [ ] **Step 1: Rewrite the `AdjusterPanel` signature** (~line 103-119) — remove `icon`, `name`, `onReset`; keep the rest:

```kotlin
@Composable
fun AdjusterPanel(
    value: Double?,
    unit: String,
    baseline: Double?,
    decimals: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    enabled: Boolean,
    incrementPicker: @Composable () -> Unit,
    uDp: Dp = 64.dp,
    rejectTick: Long = 0L,
    busy: Boolean = false,
    modifier: Modifier = Modifier,
) {
```

- [ ] **Step 2: Remove the reset-enable plumbing** — delete these lines (~line 133-136), since there is no in-panel Reset anymore:

```kotlin
    // DELETE:
    // val resetEnabled = controlsEnabled && !busy
    // val resetDisabledModifier =
    //     if (!resetEnabled) Modifier.alpha(0.38f).semantics { disabled() } else Modifier
```

- [ ] **Step 3: Delete Zone 1 entirely** — remove the whole `headerIconSize` val + the Zone-1 `Row { ... }` block (~line 155-189, from the `// Zone 1 — Header` comment through its closing `}`). The `Column(... verticalArrangement = Arrangement.SpaceBetween)` now opens directly onto Zone 2.

- [ ] **Step 4: Replace the Zone-3 stepper `Row`** (~line 229-255) with icon controls + 1U cap (keep the `incrementPicker()` call after it):

```kotlin
            Row(
                modifier = Modifier.fillMaxWidth().height(uDp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedControl(
                    label = "",
                    onClick = onDecrement,
                    enabled = controlsEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .then(disabledModifier)
                        .then(busyDimModifier),
                    intent = Intent.Accent,
                    icon = DinghyIcons.Decrease,
                    contentDescription = stringResource(R.string.cd_decrement),
                )
                OutlinedControl(
                    label = "",
                    onClick = onIncrement,
                    enabled = controlsEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .then(disabledModifier)
                        .then(busyDimModifier),
                    intent = Intent.Accent,
                    icon = DinghyIcons.Increase,
                    contentDescription = stringResource(R.string.cd_increment),
                )
            }
            incrementPicker()
```

- [ ] **Step 5: Fix imports in `AdjusterPanel.kt`** — add:

```kotlin
import androidx.compose.foundation.layout.height
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.icons.DinghyIcons
```

Then remove now-unused imports: `DinghyIconView`, `Geist` (if Zone-1's name `Text` was the only Geist user — verify with grep before removing), `width` (the Zone-1 `Spacer(width(8.dp))` is gone — verify), and the `R.string.adjuster_reset` usage is gone. Run after editing:
`grep -n "DinghyIconView\|FontWeight\|Geist\b\|\.width(\|adjuster_reset\|stringResource" app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt`
Keep any import still referenced (e.g. `stringResource` is now used by the cd_* strings; `FontWeight`/`GeistMono` are still used by Zone 2). Remove only those with zero remaining references.

- [ ] **Step 6: Update `AdjusterPreviews.kt`** — both `AdjusterPanel(` calls (~line 37, ~line 71). Delete the `icon = …`, `name = …`, and `onReset = …` argument lines from each. Example for the first preview:

```kotlin
        AdjusterPanel(
            value = 105.0,
            unit = "%",
            baseline = 100.0,
            decimals = 0,
            onDecrement = {},
            onIncrement = {},
            enabled = true,
            incrementPicker = { /* existing preview picker */ },
            uDp = 64.dp,
        )
```

Open the file first and mirror its existing argument values; only the three removed args change. Repeat for the second call site.

- [ ] **Step 7: Update the Fine-Tune `AdjusterPanel` call** (`FineTuneScreen.kt` ~line 320) — delete the `icon = selectedParam.icon`, `name = selectedParam.name`, and `onReset = …` lines; drop the redundant inner `.padding(12.dp)` so the calibration inset is the single source of spacing:

```kotlin
                    AdjusterPanel(
                        value = value,
                        unit = selectedParam.unit,
                        baseline = baseline,
                        decimals = selectedParam.decimals,
                        onDecrement = {
                            onNudge(selectedParam, value, -activeStep)
                        },
                        onIncrement = {
                            onNudge(selectedParam, value, +activeStep)
                        },
                        enabled = true,
                        busy = busy,
                        incrementPicker = {
                            IncrementPicker(
                                steps = selectedParam.steps,
                                activeStep = activeStep,
                                onSelect = { activeStep = it },
                                uDp = grid.uDp,
                            )
                        },
                        uDp = grid.uDp,
                        rejectTick = rejectTicks[dispatchKeyForTuner(selectedTuner)] ?: 0L,
                        modifier = Modifier.fillMaxSize(),
                    )
```

- [ ] **Step 8: Update the Temperature `AdjusterPanel` call** (`TemperatureScreen.kt` ~line 749) — delete the `icon = iconForSensor(sensor.name)`, `name = sensor.label`, and `onReset = null` lines:

```kotlin
            AdjusterPanel(
                value = currentValue,
                unit = "°C",
                baseline = null,
                decimals = 0,
                onDecrement = {
                    val base = currentValue ?: 0.0
                    onDecrement(base)
                },
                onIncrement = {
                    val base = currentValue ?: 0.0
                    onIncrement(base)
                },
                enabled = true,
                busy = busy,
                incrementPicker = {
                    IncrementPicker(
                        steps = TEMP_STEPS,
                        activeStep = activeStep,
                        onSelect = onStepSelect,
                        uDp = uDp,
                    )
                },
                uDp = uDp,
                rejectTick = rejectTick,
                modifier = Modifier.fillMaxWidth(),
            )
```

- [ ] **Step 9: Update the `AdjusterPanel` KDoc** — the class doc (~line 43-101) still describes "Zone 1 — Header" and the Reset button. Trim those paragraphs to describe the two-zone (value + bottom-docked controls) layout and note that identity now lives in the `FocusFrame` header (cross-reference the spec). Remove the `@param icon`, `@param name`, `@param onReset` lines.

- [ ] **Step 10: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL (all four call sites updated; no stale `icon=`/`name=`/`onReset=` args).

- [ ] **Step 11: Run host unit tests** (catch any seam this touched)

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL / all tests PASS. (`shouldShowBaseline` is unchanged and still tested.)

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt \
        app/src/main/java/works/mees/dinghy/preview/AdjusterPreviews.kt \
        app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
git commit -m "feat(adjuster): two-zone AdjusterPanel (header owns identity), icon ±, 1U caps"
```

---

## Task 9: Codify the rules in `docs/ui_design/`

Make the sweep law, not tribal knowledge. These are doc edits; no build needed, but keep them precise.

**Files:**
- Modify: `docs/ui_design/COMPONENTS.md`
- Modify: `docs/ui_design/LAYOUT.md`
- Modify: `docs/ui_design/THEMING.md`

- [ ] **Step 1: `COMPONENTS.md`** — in the component catalog, add/update:
  - A **FocusFrame header trailing-action slot** entry: bare end-aligned glyph, neutral `text2` tint, sized `slot * IDENTITY_ICON_RATIO`, no outline/fill, mirrors the start identity icon; reserved for SAFE actions only.
  - The **"header carries the current selection's identity"** rule: the Focus header reflects the most specific thing loaded — the selected item when a single item is in the Focus, the collection/screen identity for an aggregate/overview (Temperature graph = the aggregate case). Corollary: do NOT duplicate icon+name in the Focus content when the header carries it.
  - Update the **AdjusterPanel** entry to the two-zone layout (value + bottom-docked controls), identity-in-header.
  - **± = `DinghyIcons.Decrease`/`Increase` icon tokens**, not literal `−`/`+`. Note this supersedes the WR-11 "literal math glyph" exemption.

- [ ] **Step 2: `LAYOUT.md`** — add the **"Focus with a docked action region"** pattern (weighted body absorbs slack, control group docks to the bottom, shared `contentInset = FocusInset / 2` matching the calibration hub) as the canonical layout for adjuster/detail Focuses. Reinforce the **1U control cap** (UAT-5) and list the now-enforced sites (stepper rows, IncrementPicker, Scrubber ± row).

- [ ] **Step 3: `THEMING.md`** — record that the **bare-glyph header action spends no intent color** and is reserved for SAFE actions (revert-to-default qualifies — reverting to a default cannot lose unrecoverable state). Anything caution/destructive stays a content button under the four-class intent scheme.

- [ ] **Step 4: Commit**

```bash
git add docs/ui_design/COMPONENTS.md docs/ui_design/LAYOUT.md docs/ui_design/THEMING.md
git commit -m "docs(ui_design): codify header-identity, bare header action, icon ±, docked-action Focus"
```

---

## Task 10: On-device UAT (both targets) — verification checkpoint

Structural Compose changes are verified on-device, not by unit tests. This is the real acceptance gate.

**Files:** none (verification only)

- [ ] **Step 1: Build the release/debug APK and confirm freshness**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Then confirm the APK mtime is newer than the last commit (stale-APK trap). If Gradle reports UP-TO-DATE after source changes, re-run with `--rerun-tasks`.

- [ ] **Step 2: Install on BOTH devices** (flox `0a64b42e` armeabi-v7a, moto `ZY22LBDRM9` arm64-v8a) — push the matching ABI slice to each.

Run (adb via interop): `E:\Android\Sdk\platform-tools\adb.exe -s <id> install -r <apk>`

- [ ] **Step 3: UAT matrix** — Matthew drives; verify on each device, in portrait AND landscape, at fs = S/M/L:
  - **Fine-Tune:** header shows the selected param (icon + name); selecting a different param updates the header; revert glyph appears at the header END only when off-default and reverts correctly when tapped; ± render as icons at proper size; ± row + IncrementPicker sit at ≤1U; controls docked at the bottom; nothing clips at 5U phone-landscape.
  - **Temperature:** graph-overview header = generic "Temperature"; tapping a heater swaps the header to that sensor (icon + name); back to graph restores generic; ± icons; 1U; Off button still works; no revert glyph.
  - **Outputs:** header shows the selected output (prettyName + family glyph); fan/LED/servo scrubber ± are icons at ≤1U; Off/back unchanged.
  - **During a print:** the start header slot shows the e-stop (identity carried by title + value) on all three; revert glyph (Fine-Tune) still reachable at the end.

- [ ] **Step 4: Record the UAT verdict** in the plan/commit trail. Fix any defects found before declaring the phase complete (loop back to the relevant task).

---

## Self-review notes

- **Spec coverage:** every spec section (A–F) maps to a task (see the table at top). ✓
- **Type/name consistency:** `Revert` token (Task 1) used in Tasks 2-spec/5; `shouldShowBaseline` reused (internal, same module) in Task 5; `trailingActionIcon`/`onTrailingAction`/`trailingActionContentDescription` names consistent across Tasks 2 and 5; `glyphFor`/`iconForSensor`/`selectedParam` are existing same-file symbols. ✓
- **Compile-safe ordering:** headers carry identity (5-7) before Zone-1 removal (8); the AdjusterPanel API change updates all four call sites in one commit. ✓
- **Known assumptions to verify during execution (grep steps included):** `DinghyIconView` `contentDescription` param (Task 2.6); `cd_decrement`/`cd_increment` strings (Task 4.3); per-file `FocusInset`/`DinghyIcons` import presence (Tasks 5-7); unused-import cleanup in `AdjusterPanel` (Task 8.5).
