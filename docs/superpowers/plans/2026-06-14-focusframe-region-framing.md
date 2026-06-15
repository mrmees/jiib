# Region Framing (FocusFrame conformance Phase 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Centralise the 8dp edge-registration frame so each screen region (focus, field) is framed exactly once by a single `RegisteredRegion` primitive, with every region-filling component (`FocusFrame`, `ListBlock`, `FootButtonBar`, `SortRow`, `FilterRow`) flush-fill — one edit moves the whole app.

**Architecture:** Add `RegisteredRegion` (a `Column` owning `padding(8dp)` + `spacedBy(8dp)`). `ScreenScaffold` wraps each slot in it **by default**, with per-region `focusFramed`/`fieldFramed` opt-out booleans. Shared components lose their self-padding. `FocusFramePlacement` (Phase 1) is retired. Screens migrate by structural pattern: direct-child (strip caller pad), helper-owned-framing / non-scaffold (wrap in `RegisteredRegion`, opt the slot out), composed-focus (Spool/Files), embedded/frozen (preserve insets explicitly).

**Tech Stack:** Kotlin, Jetpack Compose, JUnit host tests. Builds run Windows-side via `E:\Android\gw.bat` (see `CLAUDE.md` → "Local Build Environment"). Design law: `docs/ui_design/`. Full design: `docs/superpowers/specs/2026-06-14-focusframe-region-framing-design.md`.

**Branch:** already on `focusframe-region-framing` (the spec commits live here).

**⚠ Geometry is not host-testable** (precedent: Phase 1; ADR-0001). Only Task 1 has a unit test; every other task ends at **BUILD SUCCESSFUL**, and the real acceptance gate is the on-device UAT in Task 14. Tasks 4–12 leave the app in a transient visually-incomplete state (components flushed before/while call sites are fixed) — the build stays green throughout; do **not** expect correct rendering until Task 12 completes.

**Reusable commands:**
- Host unit test (one class):
  ```bash
  /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.designsystem.layout.RegisteredRegionTest' --no-daemon" 2>&1 | tr -d '\r'
  ```
- Compile/assemble debug:
  ```bash
  /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'
  ```
- Windows git (Linux git fails on the drvfs `.git/config.lock`):
  ```bash
  "/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display <args>
  ```
The process exit code is authoritative; strip CR bars with `tr -d '\r'`.

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `designsystem/layout/RegisteredRegion.kt` | The region frame primitive + `RegionInset`/`RegionGap` | Create |
| `designsystem/layout/ListBlock.kt` | `ListFrameInset` constant + `ListBlock` | Drop ListBlock self horizontal pad |
| `test/.../layout/RegisteredRegionTest.kt` | Host test for the region constants | Create |
| `designsystem/layout/ScreenScaffold.kt` | Focus/Field skeleton | Add `focusFramed`/`fieldFramed`, wrap slots |
| `designsystem/components/FocusFrame.kt` | Focus container | Make flush; **retire `FocusFramePlacement`** |
| `designsystem/components/FootButtonBar.kt` | Foot action bar | Drop self padding |
| ~13 active screens (calibration, printstatus, files, spool, console, temperature, outputs, printers, system\*, move, finetune, macros, extrude) | — | Strip caller pad / wrap / untangle per pattern |
| `ui/move/OldMoveScreen.kt` | Frozen debug jog pad | Remove 2 `placement =` args + import |
| frozen screens (about, settings, splash, confirmguard, gallery, webcam, outputtoggle) | — | Opt-out flags / preserve insets |
| `preview/*Previews.kt` | `@Preview` renders | Wrap bare `FocusFrame` in `RegisteredRegion` |
| `docs/ui_design/{COMPONENTS,LAYOUT}.md` | Design law | Doc update |

---

### Task 1: `RegisteredRegion` primitive + region constants (TDD)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/designsystem/layout/RegisteredRegion.kt`
- Test: `app/src/test/java/works/mees/dinghy/designsystem/layout/RegisteredRegionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/works/mees/dinghy/designsystem/layout/RegisteredRegionTest.kt`:

```kotlin
package works.mees.dinghy.designsystem.layout

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The single source of the 8dp edge-registration grid (LAYOUT.md R26). */
class RegisteredRegionTest {
    @Test
    fun region_inset_and_gap_are_the_8dp_grid() {
        assertEquals(8.dp, RegionInset)
        assertEquals(8.dp, RegionGap)
    }

    @Test
    fun region_constants_share_one_source() {
        // One knob: both derive from ListFrameInset so a single edit moves the whole app.
        assertEquals(ListFrameInset, RegionInset)
        assertEquals(ListFrameInset, RegionGap)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run the host-unit-test command. Expected: FAIL — `RegisteredRegion`/`RegionInset`/`RegionGap` unresolved.

- [ ] **Step 3: Create the primitive**

Create `app/src/main/java/works/mees/dinghy/designsystem/layout/RegisteredRegion.kt`:

```kotlin
package works.mees.dinghy.designsystem.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * The outer edge-registration frame inset (LAYOUT.md R26 / C-E2): the FIRST visible outline lands
 * [RegionInset] from the region edge, the LAST lands [RegionInset] from the opposite edge. Derived
 * from [ListFrameInset] so the WHOLE app's 8dp grid is one knob.
 */
val RegionInset: Dp = ListFrameInset

/** The gap between stacked elements inside a [RegisteredRegion]. One source with [RegionInset]. */
val RegionGap: Dp = ListFrameInset

/**
 * The single owner of a screen region's 8dp registration frame (LAYOUT.md R26). A [Column] that
 * frames all four edges at [RegionInset] and spaces its DIRECT children by [RegionGap]. Every
 * region-filling component ([works.mees.dinghy.designsystem.components.FocusFrame],
 * [ListBlock], [works.mees.dinghy.designsystem.components.FootButtonBar],
 * [works.mees.dinghy.designsystem.components.SortRow]/[works.mees.dinghy.designsystem.components.FilterRow])
 * is authored FLUSH and never adds its own frame padding.
 *
 * [ScreenScaffold] wraps each slot in this by default; non-scaffold screens (Console) and
 * self-contained field helpers (PrintStatus) call it directly.
 *
 * ⚠ [RegionGap] spaces only DIRECT children. Content whose single child is a helper/Column that owns
 * its own padding must be flattened so its parts are direct children here (see the plan's pattern 2).
 */
@Composable
fun RegisteredRegion(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(RegionInset),
        verticalArrangement = Arrangement.spacedBy(RegionGap),
        content = content,
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the host-unit-test command. Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/designsystem/layout/RegisteredRegion.kt app/src/test/java/works/mees/dinghy/designsystem/layout/RegisteredRegionTest.kt
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "feat(region): RegisteredRegion primitive + RegionInset/RegionGap (8dp grid)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `ScreenScaffold` default-on framing + per-region opt-out

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/layout/ScreenScaffold.kt`

- [ ] **Step 1: Add the two params and wrap each slot**

Add `focusFramed`/`fieldFramed` to the signature (after `portraitFocusAspect`):

```kotlin
    portraitFocusAspect: Float? = null,
    focusFramed: Boolean = true,
    fieldFramed: Boolean = true,
```

Add the import:

```kotlin
import androidx.compose.runtime.Composable
// (already present) — and add:
```
(no new import needed beyond existing; `RegisteredRegion` is same package `designsystem.layout`.)

Replace the FOUR slot-render sites so each region Column is a `RegisteredRegion` when framed, else the
existing plain `Column`. Landscape focus:

```kotlin
                if (focus != null) {
                    val mod = Modifier.weight(focusGrow).fillMaxHeight()
                    if (focusFramed) RegisteredRegion(mod, content = focus)
                    else Column(mod, content = focus)
                }
```

Landscape field:

```kotlin
                if (field != null) {
                    val mod = Modifier.weight(fieldGrow).fillMaxHeight()
                    if (fieldFramed) RegisteredRegion(mod, content = field)
                    else Column(mod, content = field)
                }
```

Portrait focus (preserve the `portraitFocusAspect` branch — framing only changes the Column kind):

```kotlin
                if (focus != null) {
                    val focusMod = if (portraitFocusAspect != null) {
                        Modifier.fillMaxWidth().aspectRatio(portraitFocusAspect)
                    } else {
                        Modifier.fillMaxWidth().weight(focusGrow)
                    }
                    if (focusFramed) RegisteredRegion(focusMod, content = focus)
                    else Column(focusMod, content = focus)
                }
```

Portrait field:

```kotlin
                if (field != null) {
                    val fieldMod = if (portraitFocusAspect != null) {
                        Modifier.fillMaxWidth().weight(1f)
                    } else {
                        Modifier.fillMaxWidth().weight(fieldGrow)
                    }
                    if (fieldFramed) RegisteredRegion(fieldMod, content = field)
                    else Column(fieldMod, content = field)
                }
```

Update the KDoc "Sizing discipline" note to mention the region frame is now owned here by default.

- [ ] **Step 2: Compile**

Run the assemble-debug command. Expected: BUILD SUCCESSFUL. (All current screens still self-pad — they
are now double-framed; fixed across Tasks 3–12. Build is green.)

- [ ] **Step 3: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/designsystem/layout/ScreenScaffold.kt
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "feat(region): ScreenScaffold frames each slot via RegisteredRegion (default-on, per-region opt-out)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Make `FocusFrame` flush + retire `FocusFramePlacement`

**Files:**
- Modify: `app/.../designsystem/components/FocusFrame.kt`
- Modify: `app/.../ui/spool/SpoolScreen.kt`, `ui/files/FilesScreen.kt`, `ui/console/ConsoleScreen.kt`, `ui/move/OldMoveScreen.kt`
- Delete: `app/src/test/java/works/mees/dinghy/designsystem/components/FocusFramePaddingTest.kt`

- [ ] **Step 1: FocusFrame — drop the outer frame and delete the enum/helper/param**

In `FocusFrame.kt`:

1. Delete the `FocusFramePlacement` enum (the `enum class FocusFramePlacement { Region, Composed }` block + its KDoc).
2. Delete the `focusFramePadding(...)` function (+ its KDoc) and the now-unused `import androidx.compose.foundation.layout.PaddingValues`.
3. Remove the `placement: FocusFramePlacement = FocusFramePlacement.Region,` parameter from the `FocusFrame` signature.
4. Change the outer `Column` modifier line from:

```kotlin
            .padding(focusFramePadding(placement)) // outer registration frame (R26): Region = all 4 sides
```

to (delete the line entirely — the region owns the frame now). The `Column` modifier becomes:

```kotlin
        modifier = modifier
            .clip(shape)
```

5. Update the `## Structure` KDoc "Outer frame" bullet and the `@param modifier` / remove the `@param placement` line:

Replace the outer-frame KDoc bullet with:

```
 *  - Outer frame: NONE. The enclosing region ([works.mees.dinghy.designsystem.layout.RegisteredRegion])
 *    owns the 8dp edge-registration frame; FocusFrame is flush. Callers pass SIZING ONLY
 *    (`fillMaxSize`/`weight`).
```

- [ ] **Step 2: Remove `placement =` args from the three composed screens + OldMove**

- `SpoolScreen.kt:351` — delete the line `placement = FocusFramePlacement.Composed,`.
- `FilesScreen.kt:401` — delete `placement = FocusFramePlacement.Composed,`.
- `ConsoleScreen.kt:205` — delete `placement = FocusFramePlacement.Composed,`.
- `OldMoveScreen.kt` (~lines 250 and 298) — delete BOTH `placement = FocusFramePlacement.Composed,` args.
- In all four files, delete any now-unused `import works.mees.dinghy.designsystem.components.FocusFramePlacement`.

- [ ] **Step 3: Delete the Phase-1 test**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display rm app/src/test/java/works/mees/dinghy/designsystem/components/FocusFramePaddingTest.kt
```

- [ ] **Step 4: Verify zero references remain**

Run: `grep -rn "FocusFramePlacement\|focusFramePadding" app/src` — Expected: **no output**.

- [ ] **Step 5: Compile**

Run the assemble-debug command. Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add -A
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(region): FocusFrame goes flush; retire FocusFramePlacement (enum/helper/param/test)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Make `ListBlock` + `FootButtonBar` flush

**Files:**
- Modify: `app/.../designsystem/layout/ListBlock.kt`
- Modify: `app/.../designsystem/components/FootButtonBar.kt`

- [ ] **Step 1: ListBlock — drop the self horizontal pad**

In `ListBlock.kt`, change:

```kotlin
    Box(modifier.padding(horizontal = ListFrameInset)) {
```

to:

```kotlin
    Box(modifier) {
```

Update the two KDoc references that say "horizontal frame is OWNED here" to "the enclosing region owns
the horizontal frame; `ListBlock` is flush (embedded, non-region uses must add their own inset)."

- [ ] **Step 2: FootButtonBar — drop the self padding**

In `FootButtonBar.kt`, change:

```kotlin
            modifier = modifier
                .fillMaxWidth()
                // Horizontal frame = the SHARED ListFrameInset so the bar's outer edges align with a
                // stacked ListBlock's (owner rule, 2026-06-12). Vertical breathing room owned here.
                .padding(horizontal = ListFrameInset, vertical = 8.dp)
                .heightIn(min = uDp),
```

to:

```kotlin
            modifier = modifier
                .fillMaxWidth()
                // Flush: the enclosing RegisteredRegion owns the 8dp frame + inter-element gap.
                // Non-region uses (e.g. inside a reading column) must pass their own padding.
                .heightIn(min = uDp),
```

Update the `@param modifier` KDoc accordingly.

- [ ] **Step 3: Compile**

Run the assemble-debug command. Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/designsystem/layout/ListBlock.kt app/src/main/java/works/mees/dinghy/designsystem/components/FootButtonBar.kt
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(region): ListBlock + FootButtonBar go flush (region owns frame)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Direct-child screens — strip the caller list pad

Each site is the same mechanical transform: the `ListBlock`/`DesignListBlock` carries a caller frame
pad now owned by the region — remove ONLY the `.padding(...)` (keep `.weight(1f)`). Confirm each line
matches before editing.

**Files & exact edits** (remove the `.padding(...)` shown):
- `ui/calibration/CalibrationHubScreen.kt:151` — `ListBlock(modifier = Modifier.weight(1f).padding(top = 8.dp))` → `ListBlock(modifier = Modifier.weight(1f))`
- `ui/calibration/BedMeshScreen.kt:339` — same `.padding(top = 8.dp)` removal
- `ui/screen/PrintersScreen.kt:261` — same
- `ui/screen/SystemPageScreen.kt:154` — same
- `ui/systeminfo/SystemInformationScreen.kt:132` — same
- `ui/files/FilesScreen.kt:609` — same (the Field list `ListBlock`)
- `ui/temperature/TemperatureScreen.kt:615` and `:725` — same `.padding(top = 8.dp)` removal (both `when` branches)
- `ui/macros/BookmarkedMacrosScreen.kt:508` and `:656` — `ListBlock(modifier = Modifier.weight(1f).padding(vertical = 4.dp))` → `ListBlock(modifier = Modifier.weight(1f))` *(gap normalises to region 8dp — enumerate at UAT)*
- `ui/spool/SpoolScreen.kt:483`, `:554`, `:613` — `DesignListBlock(modifier = Modifier.weight(1f).padding(top = 8.dp))` → `DesignListBlock(modifier = Modifier.weight(1f))` (the Spool **field** list — distinct from the Spool focus untangle in Task 8)

- [ ] **Step 1: Apply all the removals above.**
- [ ] **Step 2: Compile.** Run assemble-debug. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add -A
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(region): strip caller list pad from direct-child fields (region owns it)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Simple primary-padding drops (Probe, Tilt, Extrude)

Each field is `<primary>(.padding(8.dp))` + a sibling `FootButtonBar` (two direct region children). Remove
the primary's `.padding(8.dp)`; the region now owns the frame and the inter-element gap (which normalises
from the current ~16dp to region 8dp — **enumerate at UAT**).

**Exact edits:**
- `ui/calibration/ProbeCalibrateScreen.kt:233-237` — the field `Row` modifier:
  ```kotlin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(8.dp),
  ```
  → remove `.padding(8.dp)`:
  ```kotlin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
  ```
- `ui/calibration/TiltScreen.kt:174` — the field body call `TiltFieldBody(modifier = Modifier...weight(1f)...padding(8.dp))`: remove the `.padding(8.dp)` from that modifier (keep `weight(1f)`). Read the exact line first to match it precisely.
- `ui/extrude/ExtrudeScreen.kt:333` — the field `Column(modifier = Modifier...weight(1f)...padding(8.dp))`: remove the `.padding(8.dp)` (keep `weight(1f)`). Read the exact line first.

- [ ] **Step 1: Apply the three removals.**
- [ ] **Step 2: Compile.** Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add -A
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(region): drop primary-child frame pad (Probe, Tilt, Extrude)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: PrintStatus — self-contained field regions

The four PrintStatus field slots call a self-contained helper that hand-rolls the region pattern. Opt the
**field** slots out of scaffold framing and have each helper own its region via `RegisteredRegion`
(the focus slots keep `focusFramed = true` default). Three helpers, same shape.

**Files:**
- Modify: `ui/printstatus/PrintStatusScreen.kt` (the four `ScreenScaffold` field slots)
- Modify: `ui/printstatus/PrintStatusField.kt` (`PrintStatusStandbyField`, `PrintStatusActiveField`, `PrintStatusTerminalField`)

- [ ] **Step 1: Opt the four field slots out**

In `PrintStatusScreen.kt`, add `fieldFramed = false,` to each of the four `ScreenScaffold(` calls
(Standby `:472`, Printing `:492`, Paused `:517`, Terminal `:542`) — e.g.:

```kotlin
        is PrintStatusMode.Standby -> ScreenScaffold(
            fieldFramed = false,
            focus = { … },
            field = { … },
        )
```

- [ ] **Step 2: `PrintStatusStandbyField` — replace the Box+Column wrapper with `RegisteredRegion`**

In `PrintStatusField.kt`, change:

```kotlin
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ListBlock(modifier = Modifier.weight(1f)) { … }

            failureText?.let { msg ->
                SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth().padding(horizontal = ListFrameInset))
            }

            FootButtonBar(uDp = uDp) { … }
        }
    }
```

to (region owns frame+gap+horizontal; toast pad dropped):

```kotlin
    RegisteredRegion(modifier.fillMaxSize()) {
        ListBlock(modifier = Modifier.weight(1f)) { … }

        failureText?.let { msg ->
            SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
        }

        FootButtonBar(uDp = uDp) { … }
    }
```

Add `import works.mees.dinghy.designsystem.layout.RegisteredRegion`. (`ListFrameInset` import may become
unused here — remove if so.)

- [ ] **Step 3: `PrintStatusActiveField` — same wrapper swap; drop the inner Column's horizontal pad**

Change the outer wrapper:

```kotlin
    Column(
        modifier.fillMaxSize().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier.weight(1f).padding(horizontal = ListFrameInset),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { … }
        PrintStatusFootBar( … )
    }
```

to (outer → region; inner content Column drops its horizontal pad but KEEPS its own `spacedBy(8)` for
content rows):

```kotlin
    RegisteredRegion(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { … }
        PrintStatusFootBar( … )
    }
```

- [ ] **Step 4: `PrintStatusTerminalField` — identical swap**

Same as Step 3: outer `Column(...padding(vertical = 8.dp), spacedBy(8))` → `RegisteredRegion(modifier.fillMaxSize())`,
and the inner content `Column(Modifier.weight(1f).padding(horizontal = ListFrameInset), spacedBy(8))` →
`Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp))`. (The inner
`TerminalStatsList`'s own `.padding(horizontal = 12.dp, vertical = 8.dp)` is content inset — leave it.)

- [ ] **Step 5: Compile.** Expected: BUILD SUCCESSFUL.
- [ ] **Step 6: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(region): PrintStatus fields own their region via RegisteredRegion (fieldFramed=false)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Spool focus untangle (+ the 16dp double-pad fix)

**Files:**
- Modify: `app/.../ui/spool/SpoolScreen.kt:336-379`

- [ ] **Step 1: Flatten the focus column**

Replace the padded `Box` wrapper + per-row padded sort/filter (lines ~337–379) with flush direct
children of the (now scaffold-framed) focus region:

```kotlin
            focus = {
                FocusFrame(
                    title = focusTitle,
                    icon = DinghyIcons.SpoolFilament,
                    iconTint = spoolColor,
                    uDp = grid.uDp,
                    edge = spoolColor?.let { FocusEdge.Data(it) } ?: FocusEdge.Neutral,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    SpoolDetailContent(
                        spool = selected,
                        isActive = isSelectedLoaded,
                        spoolColor = spoolColor,
                        onMeasure = onMeasure,
                        t = t,
                    )
                }
                SortRow(
                    options = sortOptions,
                    activeKey = state.sortKey,
                    onSelect = onSelectSort,
                    uDp = grid.uDp,
                )
                FilterRow(
                    options = filterOptions,
                    onSelect = onOpenFilter,
                    uDp = grid.uDp,
                )
            },
```

(The card now sizes with `weight(1f)`; the `Box` wrapper, its `padding(...)`, and the `SortRow`/`FilterRow`
`.padding(...)` modifiers are gone. This is where the 16dp card collapses to a uniform 8dp.)

- [ ] **Step 2: Compile.** Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "fix(region): Spool focus flush — uniform 8dp card aligns with sort/filter (16dp double-pad fixed)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Files focus untangle

**Files:**
- Modify: `app/.../ui/files/FilesScreen.kt:392-419`

- [ ] **Step 1: Flush the focus card + sort row**

Remove the `FocusFrame` modifier's `.padding(top = 8.dp, bottom = 4.dp)` (keep `.fillMaxWidth().weight(1f)`)
and remove the `SortRow` modifier `.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)` entirely:

```kotlin
            focus = {
                FocusFrame(
                    title = state.selectedFile?.name ?: stringResource(R.string.cd_launcher_files),
                    icon = DinghyIcons.LauncherFiles,
                    uDp = grid.uDp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    FilesDetailContent(state = state, t = t)
                }
                SortRow(
                    options = sortOptions,
                    activeKey = state.sortField,
                    onSelect = { onSelectSort(it) },
                    uDp = grid.uDp,
                )
            },
```

- [ ] **Step 2: Compile.** Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(region): Files focus flush (card + sort row)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Console — region via `RegisteredRegion` (top 0→8dp)

**Files:**
- Modify: `app/.../ui/console/ConsoleScreen.kt:195-274`

- [ ] **Step 1: Swap the hand-rolled Column for `RegisteredRegion`**

Change the inner `Column(Modifier.fillMaxSize()) { FocusFrame(...) ; FootButtonBar(...) }` to wrap in
`RegisteredRegion(Modifier.fillMaxSize())` (keep the outer `Box(bg)` + `BoxWithConstraints`/`grid`):

```kotlin
            RegisteredRegion(Modifier.fillMaxSize()) {
                FocusFrame(
                    title = stringResource(R.string.cd_launcher_console),
                    icon = DinghyIcons.LauncherConsole,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                    contentInset = 0.dp,
                ) {
                    … (the BoxWithConstraints feed wrapper — unchanged) …
                }
                FootButtonBar(uDp = grid.uDp) { … (unchanged) … }
            }
```

Add `import works.mees.dinghy.designsystem.layout.RegisteredRegion`. (Console's card top now registers at
8dp — intended; the feed stays tight to its foot bar via region `spacedBy`.)

- [ ] **Step 2: Compile.** Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(region): Console uses RegisteredRegion (card top now on the 8dp grid)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: Embedded + frozen-component insets (Temperature picker, Webcam, OutputToggle)

These are NOT region children — the global flush removed their inset, so restore it explicitly to keep
them byte-for-byte.

**Files:**
- Modify: `ui/temperature/TemperatureScreen.kt:788` (embedded `ListBlock` in `SensorPickerFocus`)
- Modify: `ui/webcam/WebcamScreen.kt` (`WebcamBackBar` + the scaffold opt-out)
- Modify: `ui/outputs/OutputToggleControl.kt:67,123`

- [ ] **Step 1: Temperature `SensorPickerFocus` embedded list**

`TemperatureScreen.kt:788` — this `ListBlock` lives inside a `FocusFrame` body (not a region). Restore its
horizontal inset:

```kotlin
            ListBlock(modifier = Modifier.weight(1f).padding(horizontal = ListFrameInset)) {
```

Confirm `import works.mees.dinghy.designsystem.layout.ListFrameInset` is present (add if missing).

- [ ] **Step 2: Webcam — freeze entirely (both regions) + restore the back bar's pad**

`WebcamScreen.kt:170` — opt the show-field scaffold out of BOTH regions:

```kotlin
            ScreenScaffold(
                focusFramed = false,
                fieldFramed = false,
                focus = { … },
                field = { … },
            )
```

`WebcamScreen.kt:224` — `WebcamBackBar` wraps a `FootButtonBar` that is now flush in BOTH the cam-picker
field and the full-focus paths; restore the padding the component used to self-apply so Webcam is
unchanged:

```kotlin
    FootButtonBar(uDp = uDp, modifier = modifier.padding(horizontal = ListFrameInset, vertical = 8.dp)) {
```

Add `import androidx.compose.foundation.layout.padding` and `import works.mees.dinghy.designsystem.layout.ListFrameInset`
if missing. (Leave `FeedFocus`/`CamPicker`'s own `padding(8.dp)` as-is — frozen.)

- [ ] **Step 3: OutputToggleControl — keep its reading layout + restore the foot bar pad**

`OutputToggleControl.kt:67` — opt the field out:

```kotlin
        ScreenScaffold(
            fieldFramed = false,
            field = { … },
        )
```

`OutputToggleControl.kt:123` — its `FootButtonBar` sits inside a `padding(16)/spacedBy(12)` reading Column
and is now flush; restore the foot bar's previous self-pad so the layout is unchanged:

```kotlin
                    FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = ListFrameInset, vertical = 8.dp)) {
```

Add `ListFrameInset` import if missing.

- [ ] **Step 4: Compile.** Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add -A
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(region): preserve embedded/frozen-component insets (Temp picker, Webcam, OutputToggle)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: Frozen-screen opt-outs

Add the opt-out flags so these screens keep their current layout byte-for-byte.

**Files & edits** (add the flag(s) to the `ScreenScaffold(` call):
- `ui/screen/AboutScreen.kt:105` — `fieldFramed = false,` (focus stays framed — its `FocusFrame` keeps the frame)
- `ui/screen/SettingsScreen.kt:186` — `fieldFramed = false,`
- `ui/screen/SplashScreen.kt:81` — `fieldFramed = false,` (field-only screen)
- `designsystem/ConfirmGuard.kt:93` — `fieldFramed = false,`
- `ui/files/FilesScreen.kt:773` (the `SpoolWarningGuard` scaffold) — `focusFramed = false, fieldFramed = false,`
- `gallery/GalleryScreen.kt:272` — `focusFramed = false, fieldFramed = false,`

- [ ] **Step 1: Apply the flags above.**
- [ ] **Step 2: Compile.** Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add -A
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "refactor(region): opt frozen screens out of region framing (About/Settings/Splash/ConfirmGuard/SpoolWarning/Gallery)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 13: Previews + design-law docs

**Files:**
- Modify: `preview/DesignKitComponentPreviews.kt`, `preview/DesignKitLayoutPreviews.kt`, `preview/WebcamPreviews.kt`
- Modify: `designsystem/components/FocusFrame.kt` (KDoc — done in Task 3; verify)
- Modify: `docs/ui_design/COMPONENTS.md`, `docs/ui_design/LAYOUT.md`

- [ ] **Step 1: Wrap bare-`FocusFrame` previews**

In each preview that renders a `FocusFrame` (or a bare `ListBlock`/`FootButtonBar`) directly without a
scaffold, wrap the rendered content in `RegisteredRegion { … }` so it keeps the 8dp inset. (Grep
`FocusFrame(`/`ListBlock(`/`FootButtonBar(` in the three preview files; wrap each top-level render.)

- [ ] **Step 2: COMPONENTS.md** — add a `RegisteredRegion` entry: "The single owner of a region's 8dp
  registration frame + inter-element gap. `ScreenScaffold` applies it per slot by default
  (`focusFramed`/`fieldFramed` opt-out); Console and self-contained field helpers call it directly.
  `FocusFrame`/`ListBlock`/`FootButtonBar`/`SortRow`/`FilterRow` are flush — they never add frame padding.
  Embedded (non-region-child) uses add their own inset." Update each component's section to "flush; region
  owns the frame."

- [ ] **Step 3: LAYOUT.md R26** — replace the Phase-1 `FocusFramePlacement.Region/Composed` bullet with:
  "The 8dp frame is owned once per region by `RegisteredRegion` (default-on in `ScreenScaffold`, per-region
  `focusFramed`/`fieldFramed` opt-out). Components never add frame padding; inter-element gaps are the
  region's `spacedBy(8)`. Non-region/embedded uses add their own inset."

- [ ] **Step 4: Compile.** Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display add -A
"/mnt/c/Program Files/Git/cmd/git.exe" -C /mnt/e/claude/personal/github/dinghy-display commit -m "docs(region): RegisteredRegion law in COMPONENTS/LAYOUT; wrap bare-FocusFrame previews

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 14: Full suite + on-device UAT (the acceptance gate)

Geometry is not host-testable; this is the gate. Both ABIs (flox = armeabi-v7a `0a64b42e`, moto =
arm64-v8a `ZY22LBDRM9` — `dinghy-test-devices` memory).

- [ ] **Step 1: Full unit-test suite**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL, all pass (incl. `RegisteredRegionTest`). Confirm `grep -rn "FocusFramePlacement" app/src` is empty.

- [ ] **Step 2: Build the debug APK(s)**

Run assemble-debug. Confirm APK mtime is newer than the last commit (`dinghy-stale-apk-uat-gate` memory).

- [ ] **Step 3: Install the matching slice on BOTH devices**

```bash
E:\Android\Sdk\platform-tools\adb.exe -s 0a64b42e install -r <flox-armeabi-v7a-debug.apk>
E:\Android\Sdk\platform-tools\adb.exe -s ZY22LBDRM9 install -r <moto-arm64-v8a-debug.apk>
```

- [ ] **Step 4: Owner UAT checklist (Matthew drives; Claude does not adb-navigate)**

1. **Pixel-identical (direct-child + Files):** CalibrationHub, Printers, SystemPage, SystemInfo, Outputs,
   Move, FineTune, ScrewsTilt, BedMesh, Files — all four region edges at 8dp, unchanged from before.
2. **Pattern-2 + helper screens:** Probe, Tilt, Extrude, PrintStatus (Standby/Printing/Paused/Terminal),
   Macros, Temperature (Monitoring + Adjust) — no double-pad, no missing frame; confirm the enumerated
   16dp→8dp gap normalisations look right (Probe/Tilt/Extrude inter-element gap; Macros list rhythm;
   Temperature Adjust two-bar gap).
3. **Fixes:** Spool focus card is full-width-to-8dp and aligned with its sort/filter tiles; Console card
   top registers at 8dp with the feed still tight to its foot bar.
4. **Embedded/frozen unchanged:** Temperature sensor-picker list keeps its inset; Webcam (both cam-picker
   and full-focus back bar) unchanged; OutputToggle unchanged; About/Settings (focus card still framed,
   reading field unchanged); Splash, ConfirmGuard, Files spool-warning guard, Gallery — all as before.
5. **Landscape cross-region alignment:** focus card bottom aligns with field `FootButtonBar` bottom on
   every two-region screen.
6. Both flox and moto.

- [ ] **Step 5: On owner approval, finish the branch**

Use `superpowers:finishing-a-development-branch`. (Do not merge/push without owner say-so — `CLAUDE.md`.)

---

## Self-Review (completed by plan author)

- **Spec coverage:** primitive (T1) ✓; scaffold default-on + opt-out (T2) ✓; FocusFrame flush + enum
  retirement incl. OldMove + test (T3) ✓; ListBlock/FootButtonBar flush (T4) ✓; the three structural
  patterns — direct-child (T5), simple primary-drop (T6), helper-owned PrintStatus (T7), composed Spool/Files
  (T8/T9), non-scaffold Console (T10) ✓; embedded/frozen-component insets (T11) ✓; frozen opt-outs incl.
  About/Settings `fieldFramed=false` + nested SpoolWarningGuard/Gallery (T12) ✓; previews + docs (T13) ✓;
  on-device UAT incl. regression guard + enumerated normalisations (T14) ✓. All 30 scaffold sites accounted
  for (active: calibration ×4, printstatus ×4, files, spool, temperature, outputs, printers, system ×2,
  move, finetune, macros, extrude; non-scaffold Console; opted-out: about, settings, splash, confirmguard,
  spoolwarning, gallery, webcam, outputtoggle).
- **Placeholders:** code shown for every bespoke edit; mechanical strips give exact file:line + the exact
  before/after line (Tilt/Extrude flagged "read the exact line first" since their full modifier wasn't
  captured verbatim — the transform is unambiguous: remove `.padding(8.dp)`).
- **Type consistency:** `RegisteredRegion`, `RegionInset`, `RegionGap`, `focusFramed`/`fieldFramed` used
  identically across T1, T2, and all call sites.
- **Codex findings folded:** #1 About/Settings `fieldFramed=false` (T12); #2 OldMove + test in enum
  retirement (T3); #3 per-screen patterns replace blanket claim (T5–T11) + enumerated normalisations (T14);
  #4 PrintStatus helper region (T7); #5 full 30-site audit incl. nested (T12); #6 Webcam both branches (T11);
  #7 embedded ListBlock/FootButtonBar insets (T11).
