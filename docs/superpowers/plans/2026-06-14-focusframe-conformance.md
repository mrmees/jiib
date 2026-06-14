# FocusFrame Conformance (Phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Focus card (`FocusFrame`) render an identical, uniform 8dp registration frame across every normal screen by moving frame ownership into the class, so callers can't diverge.

**Architecture:** Add a `FocusFramePlacement` role enum to `FocusFrame`. `Region` (default) self-owns the 8dp frame on all four sides; `Composed` keeps today's horizontal-only frame for the three screens whose Focus column composes its own vertical scheme (Spool/Files/Console). Strip per-screen frame padding from `Region` callers; frame two empty states; update the design-law docs. No behavior is centralized into `ScreenScaffold` — that is Phase 2 (field-side pass).

**Tech Stack:** Kotlin, Jetpack Compose, JUnit host tests. Builds run Windows-side via `E:\Android\gw.bat` (see `CLAUDE.md` → "Local Build Environment"). Design law: `docs/ui_design/`. Full design: `docs/superpowers/specs/2026-06-14-focusframe-conformance-design.md`.

---

## Setup (before Task 1)

We are on `master`. Create a working branch first (Windows git per `CLAUDE.md`):

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display checkout -b focusframe-conformance
```

**Reusable commands (referenced throughout):**

- Host unit test (one class):
  ```bash
  /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.designsystem.components.FocusFramePaddingTest' --no-daemon" 2>&1 | tr -d '\r'
  ```
- Compile/assemble debug:
  ```bash
  /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'
  ```
- The process exit code is authoritative; CR progress bars are stripped with `tr -d '\r'`.

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `app/.../designsystem/components/FocusFrame.kt` | The Focus container + pure helpers | Add `FocusFramePlacement` enum + `focusFramePadding()` pure fn + `placement` param; wire it |
| `app/src/test/.../designsystem/components/FocusFramePaddingTest.kt` | Host test for the pure padding mapping | Create |
| `app/.../ui/calibration/CalibrationHubScreen.kt` | Calibration hub screen | Strip `.padding(vertical = 8.dp)` |
| `app/.../ui/move/MoveScreen.kt` | Move hub screen | Strip `.padding(8.dp)` |
| `app/.../ui/finetune/FineTuneScreen.kt` | Fine-tune screen | Strip `.padding(8.dp)` |
| `app/.../ui/screen/PrintersScreen.kt` | Printers screen | Strip padding + add sizing (selected); frame empty-profile; drop field empty-box |
| `app/.../ui/outputs/OutputsScreen.kt` | Outputs screen | Drop wrapper `.padding(8.dp)`; frame unselected prompt |
| `app/.../ui/spool/SpoolScreen.kt` | Spool screen | Add `placement = Composed` |
| `app/.../ui/files/FilesScreen.kt` | Files screen | Add `placement = Composed` |
| `app/.../ui/console/ConsoleScreen.kt` | Console screen | Add `placement = Composed` |
| `app/.../designsystem/components/FocusFrame.kt` (KDoc) | — | Doc update |
| `docs/ui_design/COMPONENTS.md`, `docs/ui_design/LAYOUT.md` | Design law | Doc update |

The ~13 `fillMaxSize()` screens (PrintStatus ×4, Probe, BedMesh, Tilt, ScrewsTilt, Extrude, Macros, Temperature ×3, About, Settings, SystemPage, SystemInfo) need **no code edit** — Task 1's `Region` default fixes them. They are verified in Task 7.

`app/.../ui/move/OldMoveScreen.kt` is the deprecated jog-pad, reachable ONLY from the debug Gallery
(`GalleryActivity`) — not normal nav (`AppShell` uses `MoveScreen`). It is **not migrated**, but because
the new default is `Region`, literally leaving it untouched would still drift its debug-gallery geometry.
To keep it frozen, it gets the `placement = FocusFramePlacement.Composed` marker only (Task 5, Step 4) —
no other change.

---

### Task 1: `FocusFramePlacement` enum + pure padding helper, wired into `FocusFrame`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt`
- Test: `app/src/test/java/works/mees/dinghy/designsystem/components/FocusFramePaddingTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/works/mees/dinghy/designsystem/components/FocusFramePaddingTest.kt`:

```kotlin
package works.mees.dinghy.designsystem.components

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import works.mees.dinghy.designsystem.layout.ListFrameInset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure mapping contract for [focusFramePadding] — the Focus registration frame (LAYOUT.md R26). */
class FocusFramePaddingTest {
    @Test
    fun region_self_owns_the_8dp_frame_on_all_four_sides() {
        val p = focusFramePadding(FocusFramePlacement.Region)
        assertEquals(ListFrameInset, p.calculateTopPadding())
        assertEquals(ListFrameInset, p.calculateBottomPadding())
        assertEquals(ListFrameInset, p.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(ListFrameInset, p.calculateRightPadding(LayoutDirection.Ltr))
    }

    @Test
    fun composed_keeps_horizontal_only_so_the_caller_column_owns_vertical() {
        val p = focusFramePadding(FocusFramePlacement.Composed)
        assertEquals(0.dp, p.calculateTopPadding())
        assertEquals(0.dp, p.calculateBottomPadding())
        assertEquals(ListFrameInset, p.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(ListFrameInset, p.calculateRightPadding(LayoutDirection.Ltr))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run the host-unit-test command (Setup). Expected: FAIL — compilation error, `focusFramePadding` / `FocusFramePlacement` unresolved.

- [ ] **Step 3: Add the enum + pure helper**

In `FocusFrame.kt`, add the import `import androidx.compose.foundation.layout.PaddingValues` (near the other layout imports), and add this above the `FocusFrame` composable (next to `focusEdgeStroke`):

```kotlin
/**
 * Where a [FocusFrame] sits, which decides who owns its 8dp edge-registration frame (LAYOUT.md R26).
 *  - [Region]   — the FocusFrame IS the whole Focus region; it self-owns the uniform 8dp frame on
 *                 ALL four sides. Callers pass SIZING ONLY (fillMaxSize / weight) — never frame padding.
 *  - [Composed] — the FocusFrame is one card inside a larger Focus column whose siblings/wrapper own
 *                 the VERTICAL registration (sort/filter rows, a foot bar). Keeps the horizontal-only
 *                 frame; the caller column owns top/bottom. (Spool/Files/Console — Phase-2 untangles these.)
 */
enum class FocusFramePlacement { Region, Composed }

/**
 * Pure (host-testable) mapping from [FocusFramePlacement] to the FocusFrame's outer registration
 * padding. [FocusFramePlacement.Region] frames all four sides at [inset]; [FocusFramePlacement.Composed]
 * frames horizontal only (the caller column owns vertical). Value defaults to the shared [ListFrameInset].
 */
fun focusFramePadding(placement: FocusFramePlacement, inset: Dp = ListFrameInset): PaddingValues =
    when (placement) {
        FocusFramePlacement.Region -> PaddingValues(inset)
        FocusFramePlacement.Composed -> PaddingValues(horizontal = inset)
    }
```

- [ ] **Step 4: Add the `placement` parameter and wire it**

In the `FocusFrame` composable signature, add the parameter (place it right after `contentInset`):

```kotlin
    contentInset: Dp = FocusInset,
    placement: FocusFramePlacement = FocusFramePlacement.Region,
```

Then change the outer `Column` modifier line from:

```kotlin
            .padding(horizontal = ListFrameInset) // outer region-edge frame (matches the Field)
```

to:

```kotlin
            .padding(focusFramePadding(placement)) // outer registration frame (R26): Region = all 4 sides
```

- [ ] **Step 5: Run the test to verify it passes**

Run the host-unit-test command. Expected: PASS (both tests).

- [ ] **Step 6: Compile the app**

Run the assemble-debug command. Expected: BUILD SUCCESSFUL. (This also confirms the ~13 default-`Region` screens still compile.)

- [ ] **Step 7: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt app/src/test/java/works/mees/dinghy/designsystem/components/FocusFramePaddingTest.kt
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "feat(focusframe): self-own 8dp registration frame via FocusFramePlacement

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Strip frame padding from the simple `Region` callers (CalibrationHub, Move, FineTune)

These three already size correctly; they only carry redundant/doubled frame padding now owned by the class. Default `placement = Region` applies automatically.

**Files:**
- Modify: `app/.../ui/calibration/CalibrationHubScreen.kt:132-135`
- Modify: `app/.../ui/move/MoveScreen.kt:222-225`
- Modify: `app/.../ui/finetune/FineTuneScreen.kt:310-313`

- [ ] **Step 1: CalibrationHub** — change the `FocusFrame` modifier from:

```kotlin
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 8.dp),
```

to:

```kotlin
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
```

- [ ] **Step 2: Move** — change the `FocusFrame` modifier from:

```kotlin
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
```

to:

```kotlin
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
```

- [ ] **Step 3: FineTune** — change the `FocusFrame` modifier from:

```kotlin
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
```

to:

```kotlin
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
```

- [ ] **Step 4: Compile**

Run the assemble-debug command. Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(focusframe): strip caller frame padding (CalibrationHub, Move, FineTune)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Outputs — drop the wrapper padding (selected) and frame the unselected prompt

**Files:**
- Modify: `app/.../ui/outputs/OutputsScreen.kt:155-207`

- [ ] **Step 1: Selected branch — drop the wrapper `.padding(8.dp)`** (the `Box` keeps its `weight(1f)`; `FocusFrame` now self-frames). Change:

```kotlin
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(8.dp),
                    ) {
                        FocusFrame(
```

to:

```kotlin
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        FocusFrame(
```

- [ ] **Step 2: Unselected branch — replace the bare prompt Box with a framed `Region` FocusFrame.** Change the `else` block:

```kotlin
                } else {
                    // No selection (or preview overload): prompt text centered in the Focus region.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.outputs_select_prompt),
                            color = t.text3,
                            fontFamily = Geist,
                            fontWeight = FontWeight.Normal,
                            fontSize = fsSp(18f, t.fs).sp,
                        )
                    }
                }
```

to:

```kotlin
                } else {
                    // No selection: framed empty state — the Outputs section identity (icon law:
                    // existing owner-locked OutputSection glyph) with the prompt centered inside.
                    FocusFrame(
                        title = stringResource(R.string.outputs_title),
                        icon = DinghyIcons.OutputSection,
                        uDp = grid.uDp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.outputs_select_prompt),
                                color = t.text3,
                                fontFamily = Geist,
                                fontWeight = FontWeight.Normal,
                                fontSize = fsSp(18f, t.fs).sp,
                            )
                        }
                    }
                }
```

- [ ] **Step 3: Verify the referenced symbols are in scope/imported**

Run: `grep -nE "isPrinting|onEmergencyStop|DinghyIcons|R.string.outputs_title|val grid" app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt | head`
Expected: `isPrinting`, `onEmergencyStop`, `DinghyIcons`, and a `grid` (uDp source) are all present in this screen's scope. If `R.string.outputs_title` is missing, it is defined in `res/values/strings.xml` (`outputs_title` = "Outputs") — no new string needed.

- [ ] **Step 4: Compile**

Run the assemble-debug command. Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(focusframe): Outputs — drop wrapper pad, frame the unselected state

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Printers — strip padding + add sizing (selected), frame empty-profile, drop field empty-box

**Files:**
- Modify: `app/.../ui/screen/PrintersScreen.kt:184-245`

- [ ] **Step 1: Selected branch — strip padding and add full-slot sizing.** Printers' `FocusFrame` is currently `fillMaxWidth()` + vertical padding with no `weight`/`fillMaxSize`, so stripping alone would leave it wrap-content. Change:

```kotlin
                if (activeProfile != null) {
                    FocusFrame(
                        title = stringResource(R.string.system_row_printers),
                        icon = DinghyIcons.SystemRowPrinters,
                        uDp = grid.uDp,
                        modifier = Modifier
                            .fillMaxWidth()
                            // R26 frame: ring lands at 8dp top (matches the Field list's first row).
                            .padding(top = 8.dp, bottom = 4.dp),
                        edge = ringColor?.let { FocusEdge.Data(it) } ?: FocusEdge.Neutral,
```

to:

```kotlin
                if (activeProfile != null) {
                    FocusFrame(
                        title = stringResource(R.string.system_row_printers),
                        icon = DinghyIcons.SystemRowPrinters,
                        uDp = grid.uDp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        edge = ringColor?.let { FocusEdge.Data(it) } ?: FocusEdge.Neutral,
```

- [ ] **Step 2: Add the framed empty-profile branch.** Immediately after the selected branch's closing `}` (the end of the `if (activeProfile != null) { FocusFrame(...) { ... } }`), add an `else`:

```kotlin
                } else {
                    // Framed empty state — Printers identity (existing owner-locked SystemRowPrinters
                    // glyph) holding the no-printers headline/body (moved out of the Field).
                    FocusFrame(
                        title = stringResource(R.string.system_row_printers),
                        icon = DinghyIcons.SystemRowPrinters,
                        uDp = grid.uDp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.printers_empty_headline),
                                    color = t.text,
                                    fontFamily = Geist,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = fsSp(17f, t.fs).sp,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(R.string.printers_empty_body),
                                    color = t.text2,
                                    fontFamily = Geist,
                                    fontSize = fsSp(15f, t.fs).sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
```

- [ ] **Step 3: Drop the now-duplicate empty-box from the Field.** In the `field = {` block, change:

```kotlin
                if (profiles.isEmpty()) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.printers_empty_headline),
                                color = t.text,
                                fontFamily = Geist,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = fsSp(17f, t.fs).sp,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.printers_empty_body),
                                color = t.text2,
                                fontFamily = Geist,
                                fontSize = fsSp(15f, t.fs).sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                } else {
                    ListBlock(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
```

to (keep a blank **weighted** spacer when empty so the always-present `FootButtonBar` stays pinned to the foot — the empty `Box` was the weight(1f) that held it down; only the duplicated text is removed):

```kotlin
                if (profiles.isEmpty()) {
                    // Blank weighted spacer keeps the FootButtonBar pinned to the foot of the Field;
                    // the empty headline/body moved into the Focus card above.
                    Spacer(Modifier.weight(1f))
                } else {
                    ListBlock(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
```

Leave the `FootButtonBar(...)` block that follows unchanged (it is outside this `if`). Verify
`androidx.compose.foundation.layout.Spacer` is imported in `PrintersScreen.kt`; add the import if missing.

- [ ] **Step 4: Verify symbols in scope**

Run: `grep -nE "isPrinting|onEmergencyStop|TextAlign|import .*Column" app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt | head`
Expected: `isPrinting`, `onEmergencyStop`, `TextAlign`, and `Column` are present/imported (the empty Column already used them in the Field block we just moved).

- [ ] **Step 5: Compile**

Run the assemble-debug command. Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(focusframe): Printers — size+strip selected, frame empty-profile state

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: `Composed` opt-outs (Spool, Files, Console) — leave layouts byte-for-byte unchanged

Each gets ONE added argument so the class's new `Region` default does not double-pad their composed columns.

**Files:**
- Modify: `app/.../ui/spool/SpoolScreen.kt:343-352` (the `FocusFrame` at ~343)
- Modify: `app/.../ui/files/FilesScreen.kt:391-402`
- Modify: `app/.../ui/console/ConsoleScreen.kt:195-203`

- [ ] **Step 1: Spool** — add `placement = FocusFramePlacement.Composed,` to the `FocusFrame(...)` call (e.g. directly after its `modifier = Modifier.fillMaxSize(),` line). Add the import `import works.mees.dinghy.designsystem.components.FocusFramePlacement` if not already present.

- [ ] **Step 2: Files** — add `placement = FocusFramePlacement.Composed,` to the `FocusFrame(...)` call (after its `modifier = …` block). Add the import if needed.

- [ ] **Step 3: Console** — add `placement = FocusFramePlacement.Composed,` to the `FocusFrame(...)` call (after `contentInset = 0.dp,`). Add the import if needed.

- [ ] **Step 4: OldMove (freeze the debug Gallery)** — add `placement = FocusFramePlacement.Composed,` to **both** `FocusFrame(...)` calls in `app/.../ui/move/OldMoveScreen.kt` (~line 249 and ~line 296). This keeps the deprecated debug-gallery screen visually identical under the new `Region` default. Add the import if needed. No other change to that file.

- [ ] **Step 5: Compile**

Run the assemble-debug command. Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt app/src/main/java/works/mees/dinghy/ui/move/OldMoveScreen.kt
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(focusframe): mark Spool/Files/Console/OldMove as Composed (own their vertical frame)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Update the design-law docs

**Files:**
- Modify: `app/.../designsystem/components/FocusFrame.kt` (KDoc)
- Modify: `docs/ui_design/COMPONENTS.md`
- Modify: `docs/ui_design/LAYOUT.md`

- [ ] **Step 1: FocusFrame KDoc** — in the `FocusFrame` KDoc "## Structure" block, replace the line describing the outer frame:

Find:
```
 *  - Outer frame: self-owns the **horizontal** region-edge inset ([ListFrameInset], 8dp) so the
 *    Focus aligns with the Field's horizontal frame. Callers pass vertical (top/bottom) + sizing
 *    (`fillMaxSize`/`weight`) — never start/end/horizontal.
```
Replace with:
```
 *  - Outer frame: with [FocusFramePlacement.Region] (default) self-owns the uniform 8dp registration
 *    frame ([ListFrameInset]) on ALL four sides — callers pass SIZING ONLY (`fillMaxSize`/`weight`),
 *    never frame padding. [FocusFramePlacement.Composed] keeps the horizontal-only frame for screens
 *    whose Focus column composes its own vertical registration (Spool/Files/Console).
```
Also add `@param placement  see [FocusFramePlacement]; defaults to [FocusFramePlacement.Region].` in the `@param` block.

- [ ] **Step 2: COMPONENTS.md** — in the `§FocusFrame` section, add a sentence: "FocusFrame self-owns its 8dp registration frame on all four sides (`FocusFramePlacement.Region`, the default); callers pass sizing only and never add frame padding. The three composed-focus screens (Spool, Files, Console) pass `FocusFramePlacement.Composed` and own their vertical registration — pending the Phase-2 field-side pass."

- [ ] **Step 3: LAYOUT.md** — in the "Edge registration — the 8dp screen frame (R26)" section, add a bullet: "`FocusFrame` satisfies its 8dp top/bottom (and horizontal) registration **by construction** in `Region` placement (like `FootButtonBar`'s self-owned vertical gapS). Callers never add frame padding. `Composed`-placement screens (Spool/Files/Console) own their vertical scheme until the field-side pass unifies them."

- [ ] **Step 4: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt docs/ui_design/COMPONENTS.md docs/ui_design/LAYOUT.md
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "docs(focusframe): codify Region/Composed placement + 8dp self-framing law

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Full build + on-device verification (the real gate)

Geometry is not host-testable; this is the acceptance gate. Both ABIs (flox = armeabi-v7a, moto = arm64-v8a) per `dinghy-test-devices` memory.

- [ ] **Step 1: Full unit-test suite (no regressions)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL, all tests pass (incl. `FocusFramePaddingTest`).

- [ ] **Step 2: Build the debug APK(s)**

Run the assemble-debug command. Expected: BUILD SUCCESSFUL. Confirm the APK mtime is newer than the last commit (guard against a stale-APK install — `dinghy-stale-apk-uat-gate` memory).

- [ ] **Step 3: Install the matching slice on BOTH devices**

```bash
E:\Android\Sdk\platform-tools\adb.exe -s 0a64b42e install -r <flox-armeabi-v7a-debug.apk>
E:\Android\Sdk\platform-tools\adb.exe -s ZY22LBDRM9 install -r <moto-arm64-v8a-debug.apk>
```
(Run via `cmd.exe /c` interop; resolve the exact split-APK paths from the assemble output.)

- [ ] **Step 4: Owner UAT — checklist (Matthew drives; Claude does not adb-navigate)**

1. **Uniformity:** flip across CalibrationHub → Standby → Move → Temperature → Settings → Probe. The Focus card's four edges all register at 8dp and the card is the **same size** on every screen (top aligns with the first list row's top; bottom aligns with the foot-button bottom).
2. **Width fixes:** Move and FineTune Focus cards are full-width (not inset ~16dp).
3. **Regression guard — unchanged:** Spool, Files, Console look exactly as before (sort/filter rows and foot bars still aligned; Console feed still tight to its foot bar).
4. **New framed empty states:** Outputs with nothing selected, and Printers with no profiles, each show the consistent card + 1U header (Outputs/Printers identity) with the prompt/empty message inside.
5. Both flox and moto.

- [ ] **Step 5: On owner approval, finish the branch**

Use `superpowers:finishing-a-development-branch` to choose merge/PR. (Do not merge or push without owner say-so — `CLAUDE.md`.)

---

## Self-Review (completed by plan author)

- **Spec coverage:** enum/default (Task 1) ✓; bucket-1 strips incl. Outputs wrapper + Printers sizing trap (Tasks 2–4) ✓; auto-fixed ~13 (Task 1 default, verified Task 7) ✓; empty-state framing (Tasks 3–4) ✓; Composed opt-outs (Task 5) ✓; docs (Task 6) ✓; deferred/OldMove out-of-scope (File Structure note) ✓; on-device verification incl. regression guard (Task 7) ✓.
- **Placeholders:** none — every code step shows exact before/after.
- **Type consistency:** `FocusFramePlacement` (`Region`/`Composed`) and `focusFramePadding(placement, inset)` are used identically in the test, the helper, the param, and all five call-site edits.
