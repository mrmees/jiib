# Console Screen Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Console screen one Focus pane whose outlined frame contains the console feed (with the button row beneath), reclaiming the height the blurb wasted and fixing the landscape side-by-side split.

**Architecture:** Replace the two-region `ScreenScaffold(focus = FocusFrame{blurb}, field = {list + FootButtonBar})` with a plain vertical `Column` containing one `FocusFrame` (weight 1f, `contentInset = 0.dp`) that holds the `ConsoleListView` feed, followed by the `FootButtonBar`. E-stop, filters, feed engine, severity coloring, and empty/backfill states are unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, `FocusFrame` + `FootButtonBar` design-system components, RecyclerView-in-Compose (`ConsoleListView`).

**Testing note:** This is a pure Compose layout restructure — there is no unit test for "feed sits inside the frame." Pure-logic tests (`ConsoleFiltersTest`, `ConsoleHolderTest`, `ConsoleSeverityTest`) are untouched and must still pass. Real verification is a clean compile + on-device eyeball on flox and moto (see Task 3).

**Spec:** `docs/superpowers/specs/2026-06-13-console-screen-cleanup-design.md`

---

### Task 1: Restructure `ConsoleContent` into one Focus pane + foot bar

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt`

This task replaces the shared `ConsoleContent` body (the `private @Composable fun ConsoleContent`), removes the blurb `Text`, removes the now-unused `ScreenScaffold` import, and rewrites the stale top-of-file KDoc. The two public `ConsoleScreen` overloads, `EmptyConsole`, and `BackfillFailedNotice` are NOT changed.

- [ ] **Step 1: Remove the unused `ScreenScaffold` import**

In the import block, delete this line:

```kotlin
import works.mees.dinghy.designsystem.layout.ScreenScaffold
```

Leave `import works.mees.dinghy.designsystem.layout.rememberUnitGrid` — it is still used.

- [ ] **Step 2: Replace the `ConsoleContent` function body**

Replace the entire `private fun ConsoleContent(...)` function (currently the `Box → BoxWithConstraints → ScreenScaffold` body) with the version below. The parameter list is unchanged; only the body changes.

```kotlin
@Composable
private fun ConsoleContent(
    lines: List<ConsoleLine>,
    rawLineCount: Int,
    backfillFailed: Boolean,
    hideTemps: Boolean,
    hideTimelapse: Boolean,
    hidePrompt: Boolean,
    onToggleTemps: () -> Unit,
    onToggleTimelapse: () -> Unit,
    onTogglePrompt: () -> Unit,
    onBack: () -> Unit,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Box(modifier.fillMaxSize().background(t.bg)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
            // One Focus pane (outline + 1U header carrying the e-stop morph) holding the feed,
            // with the FootButtonBar beneath it. NOT a two-region ScreenScaffold — the console is a
            // single-pane special-use screen; a plain Column is the honest structure and renders the
            // same in portrait and landscape (no side-by-side split).
            Column(Modifier.fillMaxSize()) {
                FocusFrame(
                    title = stringResource(R.string.cd_launcher_console),
                    icon = DinghyIcons.LauncherConsole,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                    contentInset = 0.dp, // feed fills the frame edge-to-edge (rows carry their own padding)
                ) {
                    // Pinned-height BoxWithConstraints wrapper — load-bearing (the Files scroll lesson):
                    // pins the RecyclerView so it can't over-measure and composite past the frame.
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                        // .height(maxHeight) is load-bearing — see FileListView / ConsoleListView patterns.
                        ConsoleListView(
                            lines = lines,
                            modifier = Modifier.fillMaxWidth().height(maxHeight),
                        )
                        when {
                            // rawLineCount (not lines.size): "raw lines exist but every one is
                            // filtered out" must NOT show the fresh-connect empty overlay (WR-01).
                            rawLineCount == 0 && !backfillFailed ->
                                EmptyConsole(Modifier.matchParentSize())
                            backfillFailed ->
                                BackfillFailedNotice(Modifier.fillMaxWidth())
                        }
                    }
                }
                // D-15: filter toggles + Back live in the FootButtonBar beneath the Focus pane.
                FootButtonBar(
                    uDp = grid.uDp,
                ) {
                    // Back FIRST (accent — R5/R8, supersedes D-10's neutral-Back).
                    OutlinedControl(
                        label = "",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent,
                        icon = DinghyIcons.Back,
                        contentDescription = stringResource(R.string.common_back),
                    )
                    // WR-05: each icon-only toggle gets its cd_* spoken label plus selected-state
                    // semantics — active-filter state is otherwise outline-color-only.
                    // Hide-temperatures toggle
                    OutlinedControl(
                        label = "",
                        onClick = onToggleTemps,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { selected = hideTemps },
                        intent = if (hideTemps) Intent.Accent else Intent.Neutral,
                        icon = DinghyIcons.HideTemps,
                        contentDescription = stringResource(R.string.cd_console_hide_temps),
                    )
                    // Hide-timelapse toggle
                    OutlinedControl(
                        label = "",
                        onClick = onToggleTimelapse,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { selected = hideTimelapse },
                        intent = if (hideTimelapse) Intent.Accent else Intent.Neutral,
                        icon = DinghyIcons.HideTimelapse,
                        contentDescription = stringResource(R.string.cd_console_hide_timelapse),
                    )
                    // Hide-prompts toggle
                    OutlinedControl(
                        label = "",
                        onClick = onTogglePrompt,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { selected = hidePrompt },
                        intent = if (hidePrompt) Intent.Accent else Intent.Neutral,
                        icon = DinghyIcons.HidePrompts,
                        contentDescription = stringResource(R.string.cd_console_hide_prompts),
                    )
                }
            }
        }
    }
}
```

Note what is gone vs. the old body: the `ScreenScaffold(...)` call, its `focus = { FocusFrame { Text(console_focus_blurb) } }` slot (the blurb is deleted), and the `field = {}` wrapper. The `ConsoleListView` + overlays + `FootButtonBar` move verbatim into the new `Column`/`FocusFrame`.

- [ ] **Step 3: Check for newly-unused imports and remove only the truly-unused ones**

After Step 2, the blurb `Text` is gone but `Text` is still used by `EmptyConsole`/`BackfillFailedNotice`, and `TextAlign`, `Geist`, `fsSp`, `Alignment`, `FontWeight`, `fillMaxWidth`, `Column` are all still used elsewhere in the file. The ONLY import that becomes unused is `ScreenScaffold` (already removed in Step 1). Do NOT remove any other import. (If the IDE/compiler flags anything else as unused, re-verify it is genuinely unreferenced before deleting.)

- [ ] **Step 4: Rewrite the stale top-of-file KDoc**

Replace the file's leading KDoc block (currently the paragraph claiming *"A **Field-only** `ScreenScaffold`: `focus = null` (D-14) … no gutter (D-15)"* and the `## Toolkit` / `## Filter design` / `## Raw-holder invariant` sections) so its structural description matches the new layout. Keep the still-accurate guidance (Views spike verdict, filter design, raw-holder invariant, D-01 read-only, swipe-up suppression). Replace only the opening structural paragraph with:

```kotlin
/**
 * The read-only Console screen (CONS-02 / D-01..D-05). A SINGLE Focus pane: one [FocusFrame]
 * (outline + mandatory 1U header carrying the e-stop morph) whose content IS the console feed
 * ([ConsoleListView], edge-to-edge via `contentInset = 0`), with a [FootButtonBar] (Back + the three
 * noise-filter toggles) beneath it. Built as a plain `Column`, NOT a two-region `ScreenScaffold`:
 * the console is a single-pane special-use screen, so it renders the same stacked layout in portrait
 * and landscape (the old `ScreenScaffold(focus, field)` split it side-by-side in landscape).
 *
```

Leave the rest of the KDoc (`## Toolkit: Views`, `## Filter design (D-15)`, `## Raw-holder invariant`, the `D-01` line, the swipe-up `NOTE`, and the `@param` block) intact.

- [ ] **Step 5: Compile (this is the verification for a layout restructure)**

Run from the repo root:

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r' | tail -n 30
```

Expected: `BUILD SUCCESSFUL`. If it fails on an unresolved `ScreenScaffold`, you missed a usage; if it fails on an unused-import error, only `ScreenScaffold`'s import should have been removed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt
git commit -m "refactor(console): single Focus pane holds the feed; drop blurb + ScreenScaffold

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Remove the unused `console_focus_blurb` string

**Files:**
- Modify: `app/src/main/res/values/strings.xml:391`

- [ ] **Step 1: Confirm the string has no remaining references**

```bash
grep -rn "console_focus_blurb" app/src
```

Expected: ONLY the definition line in `app/src/main/res/values/strings.xml` (the `ConsoleScreen.kt` usage was deleted in Task 1). If any other reference appears, stop and resolve it before deleting.

- [ ] **Step 2: Delete the string resource**

Remove this line from `app/src/main/res/values/strings.xml`:

```xml
<string name="console_focus_blurb">Live printer console output.</string>
```

- [ ] **Step 3: Compile to confirm no dangling `R.string` reference**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r' | tail -n 20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "chore(console): remove unused console_focus_blurb string

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Build, install on both devices, on-device verification

**Files:** none (build + manual verification)

- [ ] **Step 1: Run the untouched console unit tests (sanity)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests *Console* --no-daemon" 2>&1 | tr -d '\r' | tail -n 20
```

Expected: `BUILD SUCCESSFUL`, `ConsoleFiltersTest` / `ConsoleHolderTest` / `ConsoleSeverityTest` pass.

- [ ] **Step 2: Clean-rebuild the split-ABI debug APK (avoid the stale-APK UAT gate)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks --no-daemon" 2>&1 | tr -d '\r' | tail -n 20
```

Expected: `BUILD SUCCESSFUL`. Then confirm the APK mtime is newer than the Task-1 commit before installing:

```bash
ls -l --time-style=+%s app/build/outputs/apk/debug/*.apk && git log -1 --format=%ct
```

The APK mtime(s) must be ≥ the commit timestamp.

- [ ] **Step 3: Install the matching ABI slice on BOTH devices**

flox = armeabi-v7a (id `0a64b42e`), moto = arm64-v8a (id `ZY22LBDRM9`). Install the matching split per device (adjust filenames to the actual split outputs):

```bash
E:\Android\Sdk\platform-tools\adb.exe -s 0a64b42e install -r app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
E:\Android\Sdk\platform-tools\adb.exe -s ZY22LBDRM9 install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

(Run via WSL interop, e.g. prefix with `/mnt/c/Windows/System32/cmd.exe /c` or call the `.exe` from the platform-tools dir as the build-env doc describes.)

- [ ] **Step 4: Owner on-device eyeball (Matthew navigates)**

Confirm on flox and moto, both orientations:
- One outlined Focus pane with the title/icon header; the console feed sits INSIDE the outline, reaching the frame edges (no blurb, no second box).
- The button row (Back + 3 filter toggles) sits beneath the pane.
- Scroll + stick-to-bottom still work; toggling each filter hides/reveals lines.
- Landscape: the pane fills the width with the bar beneath (NOT a side-by-side blurb|feed split).
- While a print runs: the header icon morphs to the red e-stop and the confirm guard fires.

---

## Self-Review

- **Spec coverage:** ✅ single Focus pane holds feed (Task 1) · blurb deleted (Task 1 Step 2) · unused string removed (Task 2) · `contentInset = 0` edge-to-edge (Task 1) · e-stop unchanged (kept in `FocusFrame` header) · `ScreenScaffold` dropped (Task 1) · pinned-height wrapper + overlays kept (Task 1) · stale KDoc rewritten (Task 1 Step 4) · landscape fix (inherent to the `Column` restructure) · tests/devices (Task 3).
- **Placeholders:** none — all code shown in full.
- **Type consistency:** `ConsoleContent` signature unchanged; `FocusFrame`/`FootButtonBar`/`OutlinedControl`/`ConsoleListView` calls match their current usages verbatim; `contentInset` is an existing `FocusFrame` param (`Dp`, default `FocusInset`).
```
