# Move Hub Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Five-item polish of the Move Hub: relocate Disable Motors + Add Bookmark into the Field menu, hide the bookmark group until all axes are homed, conform the Microstep + Bookmark Focus buttons to the canonical spacing, shrink-fit the bookmark coordinate line, and add a "Polls Every 500ms" note + vertical centering to the Endstops Focus.

**Architecture:** All changes live in `ui/move`. One new pure helper (`moveModeAfterHomedChange`) is host-tested; the rest are Compose edits in `MoveScreen.kt` verified by build + on-device screenshot (the project's real loop for visual/spacing work per ADR-0001 — Compose spacing is not meaningfully unit-testable, and faking such tests would be theater). Spec: `docs/superpowers/specs/2026-06-21-move-hub-polish-design.md`.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4 (host unit tests). Build Windows-side via `E:\Android\gw.bat`; install split-ABI debug APKs on flox (armeabi-v7a) + moto (arm64-v8a).

## Global Constraints

- minSdk 23 floor; perf floor = Nexus 7 2013 (Adreno 320). No continuous animation.
- All text styled via a `DinghyType` role `.toTextStyle(t)` (or the sized overload) — inline `fontSize=`/`fontFamily=` fail `FontConformanceTest`.
- List rows translucent, buttons filled; intent colors R5/R18/R19 (red=`t.stop`/Danger, accent=neutral/nav, go=expected action). Controls cap at 1U (`controlHeight(uDp)` + `LocalUnitDp`).
- Never introduce a new icon glyph — all three needed (`MoveDisableMotors`, `SaveLocation`, `SavedLocation`) already exist in `DinghyIcons`.
- Build command (run from repo root, exit code is authoritative):
  `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>" 2>&1 | tr -d '\r'`
- adb: `/mnt/e/Android/Sdk/platform-tools/adb.exe`; flox id `0a64b42e`, moto id `ZY22LBDRM9`.

---

## File Structure

- `app/src/main/java/works/mees/dinghy/ui/move/MoveModeReset.kt` — **NEW.** Pure `moveModeAfterHomedChange(mode, allHomed)` fallback helper.
- `app/src/test/java/works/mees/dinghy/ui/move/MoveModeResetTest.kt` — **NEW.** Host test for the helper.
- `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` — **MODIFY.** Field list rows + foot bar (Task 2), mode-reset wiring (Task 2), Bookmark Focus (Task 3), Microstep Focus spacing (Task 4), Endstops Focus note + centering (Task 5).

Reference (read-only, do not edit): `designsystem/components/StepperRow.kt` (the canonical 1U `LocalUnitDp` + `controlHeight` + `gapS` button-row mechanism), `designsystem/components/AdjusterPanel.kt` (Fine-Tune Zone 2 reference), `MoveAvailability.kt` (`avail.*` is unchanged — the bookmark group gates on `vm.allHomed` directly).

---

### Task 1: Mode-reset fallback helper

Pure function: when the printer un-homes, transient Focus modes only reachable while homed (`Bookmark`, `SaveDialog`) fall back to the hub default `TouchMove`; everything else is unchanged. `MoveMode` is the sealed interface declared in `MoveScreen.kt` (`TouchMove`/`XY`/`Z`/`Microstep` objects, `SaveDialog` object, `Endstops` data object, `Bookmark(name)` data class), same package, so the test can construct each variant.

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/move/MoveModeReset.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/move/MoveModeResetTest.kt`

**Interfaces:**
- Consumes: `MoveMode` (existing sealed interface in `MoveScreen.kt`).
- Produces: `fun moveModeAfterHomedChange(mode: MoveMode, allHomed: Boolean): MoveMode` — consumed by Task 2's `LaunchedEffect`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/works/mees/dinghy/ui/move/MoveModeResetTest.kt`:

```kotlin
package works.mees.dinghy.ui.move

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveModeResetTest {
    @Test fun bookmark_resets_to_touchmove_when_unhomed() {
        assertEquals(MoveMode.TouchMove, moveModeAfterHomedChange(MoveMode.Bookmark("Front Left"), allHomed = false))
    }

    @Test fun savedialog_resets_to_touchmove_when_unhomed() {
        assertEquals(MoveMode.TouchMove, moveModeAfterHomedChange(MoveMode.SaveDialog, allHomed = false))
    }

    @Test fun bookmark_kept_when_homed() {
        val m = MoveMode.Bookmark("Front Left")
        assertEquals(m, moveModeAfterHomedChange(m, allHomed = true))
    }

    @Test fun savedialog_kept_when_homed() {
        assertEquals(MoveMode.SaveDialog, moveModeAfterHomedChange(MoveMode.SaveDialog, allHomed = true))
    }

    @Test fun motion_and_endstop_modes_never_reset_even_when_unhomed() {
        // Motion + Endstop modes are out of scope for this reset (pre-existing behavior). Reset must not touch them.
        assertEquals(MoveMode.TouchMove, moveModeAfterHomedChange(MoveMode.TouchMove, allHomed = false))
        assertEquals(MoveMode.Microstep, moveModeAfterHomedChange(MoveMode.Microstep, allHomed = false))
        assertEquals(MoveMode.XY, moveModeAfterHomedChange(MoveMode.XY, allHomed = false))
        assertEquals(MoveMode.Z, moveModeAfterHomedChange(MoveMode.Z, allHomed = false))
        assertEquals(MoveMode.Endstops, moveModeAfterHomedChange(MoveMode.Endstops, allHomed = false))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.move.MoveModeResetTest --rerun-tasks --no-daemon" 2>&1 | tr -d '\r' | tail -20`
Expected: FAIL — `moveModeAfterHomedChange` is unresolved (compile error).

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/works/mees/dinghy/ui/move/MoveModeReset.kt`:

```kotlin
package works.mees.dinghy.ui.move

/**
 * Transient Move Hub modes — the [MoveMode.Bookmark] detail and the [MoveMode.SaveDialog] save form —
 * are only reachable from the Field menu while all axes are homed (their rows are gated on
 * `vm.allHomed`). When the printer un-homes (e.g. the user taps Disable Motors), fall back to the hub
 * default [MoveMode.TouchMove] so we never strand the user on a Focus whose menu row just vanished.
 *
 * Only [MoveMode.Bookmark] and [MoveMode.SaveDialog] are reset, because this pass's bookmark-group
 * gating is what newly makes them homed-only. Motion modes (TouchMove/XY/Z/Microstep) and
 * [MoveMode.Endstops] are returned unchanged — their un-homed behavior is pre-existing and out of
 * scope here (their own Focus bodies already handle it; the guards differ — Microstep on homed
 * state, TouchMove/XY/Z on bounds availability).
 */
fun moveModeAfterHomedChange(mode: MoveMode, allHomed: Boolean): MoveMode =
    if (!allHomed && (mode is MoveMode.Bookmark || mode == MoveMode.SaveDialog)) MoveMode.TouchMove else mode
```

- [ ] **Step 4: Run test to verify it passes**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.move.MoveModeResetTest --rerun-tasks --no-daemon" 2>&1 | tr -d '\r' | tail -20`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/move/MoveModeReset.kt app/src/test/java/works/mees/dinghy/ui/move/MoveModeResetTest.kt
git commit -m "feat(move): mode-reset helper for un-homing transient modes"
```

---

### Task 2: Field menu rows + foot bar + mode-reset wiring

Move Disable Motors and Add Bookmark out of the foot bar into the Field list; gate the whole bookmark group (Add Bookmark + saved-location rows) on `vm.allHomed`; wire the Task 1 reset.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` (mode-reset ~after line 221; Field list ~807–826; foot bar ~842–866)

**Interfaces:**
- Consumes: `moveModeAfterHomedChange` (Task 1); `vm.allHomed`, `mode`, `avail`, `grid.uDp`, `t`, `savedLocations`, `onDisableSteppers`, `onHomeAll`, `onBack` (all in scope in `MoveScreen`).
- Produces: nothing downstream depends on it.

- [ ] **Step 1: Wire the mode-reset effect**

In `MoveScreen.kt`, immediately after `val avail = moveRowAvailability(vm.xHomed, vm.yHomed, vm.zHomed)` (~line 221), add:

```kotlin
    // When the printer un-homes, drop out of any homed-only transient Focus (Bookmark/SaveDialog)
    // so we don't strand the user on a Focus whose menu row just disappeared (see Task 1 helper).
    LaunchedEffect(vm.allHomed) {
        mode = moveModeAfterHomedChange(mode, vm.allHomed)
    }
```

(`LaunchedEffect` is already imported — `MoveScreen.kt:22`.)

- [ ] **Step 2: Gate the bookmark group + add the Add Bookmark row**

Replace the saved-locations block (currently ~807–816):

```kotlin
                    // Saved-location rows.
                    items(savedLocations, key = { "bookmark_${it.name}" }) { loc ->
                        MoveRow(
                            loc.name,
                            DinghyIcons.SavedLocation,
                            mode == MoveMode.Bookmark(loc.name),
                            grid.uDp,
                            t.accent,
                        ) { mode = MoveMode.Bookmark(loc.name) }
                    }
```

with (the whole bookmark group — Add Bookmark first, then saved rows — gated on `vm.allHomed`):

```kotlin
                    // Bookmark group: only meaningful once all axes are homed (a bookmark Move needs a
                    // known coordinate frame). Hidden entirely otherwise. Add Bookmark leads the group.
                    if (vm.allHomed) {
                        item("add_bookmark") {
                            MoveRow("Add Bookmark", DinghyIcons.SaveLocation, false, grid.uDp, t.accent) {
                                mode = MoveMode.SaveDialog
                            }
                        }
                        items(savedLocations, key = { "bookmark_${it.name}" }) { loc ->
                            MoveRow(
                                loc.name,
                                DinghyIcons.SavedLocation,
                                mode == MoveMode.Bookmark(loc.name),
                                grid.uDp,
                                t.accent,
                            ) { mode = MoveMode.Bookmark(loc.name) }
                        }
                    }
```

- [ ] **Step 3: Add the Disable Motors row at the bottom of the list**

Immediately after the `item("endstops") { … }` block (~826, the last item in the `ListBlock`), add:

```kotlin
                    // Disable Motors — destructive utility, pinned at the bottom. Fires immediately on
                    // tap (owner: one tap, no confirm); does NOT swap the Focus. Red icon (t.stop) reads
                    // destructive on a translucent row. Un-homes the printer → the LaunchedEffect above
                    // drops any transient Focus and the bookmark group + motion rows collapse.
                    item("disable_motors") {
                        MoveRow("Disable Motors", DinghyIcons.MoveDisableMotors, false, grid.uDp, t.stop) {
                            onDisableSteppers()
                        }
                    }
```

- [ ] **Step 4: Trim the foot bar to Back + Home All**

In the `FootButtonBar` `actions = listOf(...)` (~835–867), delete the **Disable Motors** `FootAction` (~842–849) and the **Save Location** `FootAction` (~858–866), leaving exactly Back and Home All:

```kotlin
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            onClick = onBack,
                            intent = Intent.Accent,
                            icon = DinghyIcons.Back,
                            contentDescription = "Back",
                        ),
                        // Home All (go) — the expected homing action.
                        FootAction(
                            label = stringResource(R.string.move_home_all),
                            onClick = { onHomeAll() },
                            intent = Intent.Go,
                            icon = DinghyIcons.MoveHomeAll,
                            contentDescription = "Home all",
                        ),
                    ),
```

(Two actions → the FootButtonBar count rule renders them icon+text. `avail.saveLocation` is now unused by the foot bar but the field stays in `MoveRowAvailability` — leave it; out of scope to remove.)

- [ ] **Step 5: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r' | tail -6`
Expected: `BUILD SUCCESSFUL`. (No new imports needed — `MoveRow`, `DinghyIcons`, `t.stop`, `LaunchedEffect`, `item/items` all already in scope/imported.)

- [ ] **Step 6: Install + on-device verify**

```bash
cd app/build/outputs/apk/debug
/mnt/e/Android/Sdk/platform-tools/adb.exe -s 0a64b42e install -r app-armeabi-v7a-debug.apk
/mnt/e/Android/Sdk/platform-tools/adb.exe -s ZY22LBDRM9 install -r app-arm64-v8a-debug.apk
```

Verify on the moto: foot bar shows only Back + Home All (icon+text). When homed: Add Bookmark row appears above the saved bookmarks, Disable Motors is the bottom row with a red icon. Tapping Disable Motors disables instantly and the bookmark group + Touch/XY/Z rows disappear (Home XY/Z reappear). When un-homed, no bookmark rows show.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
git commit -m "feat(move): Disable Motors + Add Bookmark as Field rows; gate bookmarks on homed"
```

---

### Task 3: Bookmark Focus — shrink-fit coord line + 1U Move/Delete buttons

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` (Bookmark Focus ~594–643)

**Interfaces:**
- Consumes: `loc` (`SavedLocation`), `grid.uDp`, `t`, `fmt1`, `onMoveTo`, `deleteConfirm` (all in scope in the Bookmark branch).
- Produces: nothing downstream.

- [ ] **Step 1: Replace the coordinate line with the Microstep autosize treatment**

Replace the coord `Text` (~594–605):

```kotlin
                                    Text(
                                        text = "X ${fmt1(loc.x)}   Y ${fmt1(loc.y)}" +
                                            if (loc.z != null) "   Z ${fmt1(loc.z)}" else "",
                                        style = DinghyType.statValue.toTextStyle(t),
                                        color = t.text,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                    )
```

with a shrink-to-fit `BasicText` (mirrors the Microstep readout, `MoveScreen.kt` ~527–541 — so X/Y/Z always fits the Focus width, no clipping on the moto):

```kotlin
                                    BasicText(
                                        text = "X ${fmt1(loc.x)}   Y ${fmt1(loc.y)}" +
                                            if (loc.z != null) "   Z ${fmt1(loc.z)}" else "",
                                        style = DinghyType.focusHero.toTextStyle(t).copy(
                                            color = t.text,
                                            textAlign = TextAlign.Center,
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        autoSize = TextAutoSize.StepBased(
                                            minFontSize = fsSp(15f, t.fs).sp,
                                            maxFontSize = fsSp(40f, t.fs).sp,
                                            stepSize = 1.sp,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                    )
```

(`BasicText`, `TextAutoSize`, `fsSp`, `.sp` are already imported/used by the Microstep branch in this file.)

- [ ] **Step 2: Re-wrap Move/Delete as a canonical 1U button row**

Replace the action `Row` (~627–643):

```kotlin
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedControl(
                                            label = "Move",
                                            onClick = { onMoveTo(loc.x, loc.y, loc.z) },
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Go,
                                        )
                                        OutlinedControl(
                                            label = "Delete",
                                            onClick = { deleteConfirm = loc.name },
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Danger,
                                        )
                                    }
```

with the canonical 1U pattern (mirrors `StepperRow`'s internals — `LocalUnitDp` so `OutlinedControl` floors at 1U, `controlHeight(uDp)` on the row, `gapS(uDp)` spacing):

```kotlin
                                    CompositionLocalProvider(LocalUnitDp provides grid.uDp) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .controlHeight(grid.uDp),
                                            horizontalArrangement = Arrangement.spacedBy(gapS(grid.uDp)),
                                        ) {
                                            OutlinedControl(
                                                label = "Move",
                                                onClick = { onMoveTo(loc.x, loc.y, loc.z) },
                                                modifier = Modifier.weight(1f),
                                                intent = Intent.Go,
                                            )
                                            OutlinedControl(
                                                label = "Delete",
                                                onClick = { deleteConfirm = loc.name },
                                                modifier = Modifier.weight(1f),
                                                intent = Intent.Danger,
                                            )
                                        }
                                    }
```

- [ ] **Step 3: Add imports if missing**

Ensure these imports exist at the top of `MoveScreen.kt` (add any that are absent):

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import works.mees.dinghy.designsystem.layout.LocalUnitDp
import works.mees.dinghy.designsystem.layout.controlHeight
import works.mees.dinghy.designsystem.layout.gapS
```

Verify the exact package paths against `StepperRow.kt`'s imports (it uses all four); copy from there if a path differs.

- [ ] **Step 4: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r' | tail -6`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Install + screenshot-verify on moto**

```bash
cd app/build/outputs/apk/debug
/mnt/e/Android/Sdk/platform-tools/adb.exe -s ZY22LBDRM9 install -r app-arm64-v8a-debug.apk
/mnt/e/Android/Sdk/platform-tools/adb.exe -s 0a64b42e install -r app-armeabi-v7a-debug.apk
```

On the moto, open a bookmark with a Z value. Capture and inspect:
`/mnt/e/Android/Sdk/platform-tools/adb.exe -s ZY22LBDRM9 exec-out screencap -p > /tmp/bookmark-focus.png`
Confirm: the full `X … Y … Z …` line fits the Focus width (no clipping/ellipsis), and the Move/Delete buttons are 1U tall with spacing matching the Fine-Tune stepper buttons.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
git commit -m "fix(move): bookmark Focus — shrink-fit coord line + 1U Move/Delete row"
```

---

### Task 4: Microstep Focus button spacing → match Fine-Tune (diagnose-then-fix)

The delta is **not derivable from the source** (Microstep already uses `StepperRow` + `Column(spacedBy(8.dp))`, the same primitives as Fine-Tune's `AdjusterPanel`). This task is screenshot-driven: measure the difference, then make the smallest change that matches Fine-Tune. Do NOT guess-edit before screenshotting.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` (Microstep Focus ~485–583) — exact edit determined in Step 2.

**Interfaces:**
- Consumes: existing Microstep-branch scope. Produces: nothing downstream.

- [ ] **Step 1: Capture both Focuses on the moto (current build from Task 3)**

Navigate to the Microstep Focus, then a Fine-Tune adjuster Focus, capturing each:
```bash
/mnt/e/Android/Sdk/platform-tools/adb.exe -s ZY22LBDRM9 exec-out screencap -p > /tmp/microstep-focus.png
/mnt/e/Android/Sdk/platform-tools/adb.exe -s ZY22LBDRM9 exec-out screencap -p > /tmp/finetune-focus.png
```

- [ ] **Step 2: Diff and identify the actual delta**

Compare the two screenshots for the button rows. Check, in this order, which one differs from Fine-Tune:
  1. **Outer content inset / breathing room** — Microstep weights the coord readout `weight(1f)` and bottom-docks three control rows in `Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp))`; Fine-Tune's `AdjusterPanel` Zone 2 is a separate bottom column. Note any difference in the gap above the buttons or the side padding (Fine-Tune content sits inside the FocusFrame's standard inset).
  2. **Inter-row vertical gap** between the step cycler / jog pair / axis selector vs Fine-Tune's stepper↔picker gap.
  3. **Row internals** — the step cycler's center value slot, or `AxisSelectorRow` tile height, rendering taller/shorter than a plain `StepperRow`.

Write down the single concrete difference found (e.g. "axis selector row is 8dp closer than Fine-Tune's picker" or "side inset differs by N dp").

- [ ] **Step 3: Apply the smallest matching change**

Edit only the attribute identified in Step 2 so the Microstep control rows match Fine-Tune. The likely fixes (apply whichever the diff dictates):
  - normalize the vertical gap token to match `AdjusterPanel` (`Arrangement.spacedBy(8.dp)` — already the value Fine-Tune uses; only change if Microstep diverges),
  - remove any extra `Modifier.padding(...)` on the Microstep `Column` that `AdjusterPanel` does not have,
  - if the bottom-dock weighting is the culprit, restructure the control rows into their own bottom `Column` mirroring `AdjusterPanel.kt` ~174–193.

Do not change jog/step/axis behavior — spacing/padding only.

**If the diff in Step 2 reveals NO concrete spacing/padding difference** (Microstep already matches
Fine-Tune): make no code change, do NOT commit. Record the finding (attach both screenshots) and
return to the owner — the item may be already-correct, or the owner saw something on a screen/state
not yet reproduced. Skip Steps 4–5.

- [ ] **Step 4: Build, install, re-screenshot to confirm parity**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r' | tail -6` → `BUILD SUCCESSFUL`.
```bash
cd app/build/outputs/apk/debug
/mnt/e/Android/Sdk/platform-tools/adb.exe -s ZY22LBDRM9 install -r app-arm64-v8a-debug.apk
/mnt/e/Android/Sdk/platform-tools/adb.exe -s 0a64b42e install -r app-armeabi-v7a-debug.apk
/mnt/e/Android/Sdk/platform-tools/adb.exe -s ZY22LBDRM9 exec-out screencap -p > /tmp/microstep-focus-after.png
```
Confirm the Microstep buttons now match Fine-Tune spacing/padding. If not, return to Step 2.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
git commit -m "fix(move): conform Microstep Focus button spacing to Fine-Tune"
```

---

### Task 5: Endstops Focus — "Polls Every 500ms" note + vertical centering

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` (Endstops loaded branch ~746–753)

**Interfaces:**
- Consumes: `current` (`List<EndstopStatus>`), `grid.uDp`, `t`. Produces: nothing downstream.

- [ ] **Step 1: Add the note + center the rows**

Replace the loaded-state block (~746–753):

```kotlin
                                current != null -> {
                                    Column(
                                        Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        current.forEach { es -> EndstopRow(es, grid.uDp) }
                                    }
                                }
```

with (note pinned at top in dim caption; rows centered vertically in the space below via a weighted column with center arrangement):

```kotlin
                                current != null -> {
                                    Column(Modifier.fillMaxSize()) {
                                        // Polling cadence note — the poll loop above is literally delay(500).
                                        Text(
                                            text = "Polls Every 500ms",
                                            style = DinghyType.caption.toTextStyle(t),
                                            color = t.text3,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                        )
                                        // Endstop rows centered vertically in the remaining space.
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                                        ) {
                                            current.forEach { es -> EndstopRow(es, grid.uDp) }
                                        }
                                    }
                                }
```

(`Text`, `DinghyType`, `TextAlign`, `Alignment`, `Arrangement`, `padding`, `fillMaxWidth`, `weight` are all already imported/used in this file. The text is an inline string, matching the existing `FocusHint("Querying…")` convention in this file — `FontConformanceTest` only governs styling, which goes through `DinghyType.caption.toTextStyle(t)`.)

- [ ] **Step 2: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r' | tail -6`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install + screenshot-verify**

```bash
cd app/build/outputs/apk/debug
/mnt/e/Android/Sdk/platform-tools/adb.exe -s ZY22LBDRM9 install -r app-arm64-v8a-debug.apk
/mnt/e/Android/Sdk/platform-tools/adb.exe -s 0a64b42e install -r app-armeabi-v7a-debug.apk
/mnt/e/Android/Sdk/platform-tools/adb.exe -s ZY22LBDRM9 exec-out screencap -p > /tmp/endstops-focus.png
```
Open the Endstops view: confirm "Polls Every 500ms" sits at the top and the endstop rows are vertically centered in the space below.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
git commit -m "feat(move): Endstops Focus — poll-cadence note + vertical centering"
```

---

### Task 6: Full-suite + release gate

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit suite**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r' | tail -15`
Expected: `BUILD SUCCESSFUL` (incl. `FontConformanceTest`, `MoveAvailabilityTest`, `MoveModeResetTest`, `MoveHolderTest`).

- [ ] **Step 2: Release (R8) build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleRelease --no-daemon" 2>&1 | tr -d '\r' | tail -8`
Expected: `BUILD SUCCESSFUL` (verifies R8/minify keeps the changes intact).

- [ ] **Step 3: Owner UAT on flox + moto**

Install the latest debug slices on both devices (commands as in Task 2 Step 6) and have the owner walk the five items on both. Address any UAT findings before merge.

---

## Self-Review

**Spec coverage:**
- Item 1 (Disable + Add Bookmark → rows; foot bar → Back/Home All) → Task 2.
- Item 2 (gate bookmark group on homed) → Task 2 Step 2; dangling-mode reset → Task 1 + Task 2 Step 1.
- Item 3 (Microstep spacing) → Task 4.
- Item 4 (Bookmark coord shrink-fit + 1U buttons) → Task 3.
- Item 5 (Endstops note + centering) → Task 5.
- Verification (suite + R8 + on-device) → Tasks 2–5 install steps + Task 6.

All spec sections map to a task.

**Placeholder scan:** Task 4 is intentionally diagnostic (screenshot-first) because the spec established the delta is not visible from code; it carries concrete capture/compare/commit steps and an enumerated candidate-fix list rather than a fabricated code block. All other tasks contain complete code.

**Type consistency:** `moveModeAfterHomedChange(mode, allHomed)` — same signature in Task 1 (definition + test) and Task 2 (call site). `MoveMode` variants (`TouchMove`/`XY`/`Z`/`Microstep`/`SaveDialog`/`Bookmark`/`Endstops`) used consistently. `MoveRow(label, icon, selected, uDp, tint) { onClick }`, `OutlinedControl(label, onClick, modifier, intent)`, `controlHeight(uDp)`, `gapS(uDp)`, `LocalUnitDp`, `TextAutoSize.StepBased`, `fsSp(_, t.fs)` all match existing call sites in the file.
