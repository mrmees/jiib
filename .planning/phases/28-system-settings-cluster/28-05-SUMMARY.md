---
phase: 28-system-settings-cluster
plan: "05"
subsystem: navigation,shell,print-status
tags: [drawer-retirement, d-04, d-06, navdest-system, wave-3]
dependency_graph:
  requires:
    - "28-02 (NavDest.System + SystemPageScreen)"
    - "28-03 (Temperature/Console/FineTune rehomed to idle list)"
  provides:
    - AppDrawer.kt deleted (D-04 drawer retirement complete)
    - SwipeUpAccumulator.kt deleted
    - AppShell drawer/swipe-free with composable<NavDest.System> wired
    - PrintStatusField System foot → NavDest.System
    - PrintStatusScreen onOpenDrawer fully retired (zero app/src hits)
    - ShortcutRow SystemShortcutTile (mid-print System access, D-06)
    - LauncherDest.Drawer enum variant removed
    - HomeAction.OpenDrawer sealed object removed
  affects:
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt (deleted)
    - app/src/main/java/works/mees/dinghy/ui/shell/SwipeUpAccumulator.kt (deleted)
    - app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusUiModel.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusUiModelTest.kt
    - app/src/test/java/works/mees/dinghy/ui/shell/AppDrawerOutputsGateTest.kt (deleted)
    - app/src/test/java/works/mees/dinghy/ui/shell/SwipeUpAccumulatorTest.kt (deleted)
    - app/src/androidTest/java/works/mees/dinghy/ui/ShellPresenceTest.kt (deleted)
    - app/src/androidTest/java/works/mees/dinghy/webcam/DrawerWebcamGatingTest.kt (deleted)
    - app/src/androidTest/java/works/mees/dinghy/ui/finetune/FineTuneNavTest.kt (deleted)
tech_stack:
  added: []
  patterns:
    - SystemShortcutTile (hair-outline icon-only tile, NavDest.System, D-06 mid-print access)
    - LauncherGrid updated: Drawer tile variant retired, all tiles route via launcherDestTarget
    - composable<NavDest.System> block in AppShell NavHost following About block
key_files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusUiModel.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusUiModelTest.kt
decisions:
  - "LauncherDrawer DinghyIcons token left intact — DinghyIcons.all still references it; removing it would require a DinghyIcons edit outside this plan's scope; DinghyIconsTest stays green"
  - "LauncherGrid retained as internal composable (unused by current screens, may be referenced by previews or future code); no call-site cleanup required since it compiled cleanly"
  - "SystemShortcutTile added to ShortcutRow alongside Tune tile for mid-print System access (D-06)"
  - "NavDest.System intentionally absent from screenOwnsEstop + FOOT_GUN_DESTS — shell e-stop applies on System cluster while printing (else -> null in shouldPopToRoot covers it)"
metrics:
  duration: ~35 minutes
  completed: "2026-06-12"
  tasks_completed: 3
  tasks_total: 3
  files_modified: 6
  files_deleted: 7
---

# Phase 28 Plan 05: AppDrawer Retirement + NavDest.System Wiring Summary

**One-liner:** AppDrawer + SwipeUpAccumulator fully deleted (D-04) and their 5 test files; AppShell rewired drawer-free with composable<NavDest.System>; all System entry points (WaterfallHome foot, standby launcher System path, mid-print shortcut) route to NavDest.System; onOpenDrawer retired at all 6 callsites; full host suite green.

## Tasks Completed

| Task | Name | Commit | Key files |
|------|------|--------|-----------|
| 1 | Delete AppDrawer+SwipeUpAccumulator+drawer androidTests; remove OpenDrawer+LauncherDest.Drawer | 1af489a | AppDrawer.kt (del), SwipeUpAccumulator.kt (del), 5 test files (del), HomeAction.kt, PrintStatusUiModel.kt, PrintStatusUiModelTest.kt |
| 2 | Strip drawer/swipe plumbing from AppShell + add NavDest.System composable | 365466f | AppShell.kt |
| 3 | Retarget PrintStatusField+PrintStatusScreen System entry points to NavDest.System; full suite green | 68dc1f7 | PrintStatusField.kt, PrintStatusScreen.kt |

## What Was Built

**Task 1 — Deletions + variant removal:**
- `AppDrawer.kt` deleted (D-04: the 369-line full-screen grid dialog, DRAWER_TILES inventory)
- `SwipeUpAccumulator.kt` deleted (the per-gesture accumulator pure class, R10)
- `AppDrawerOutputsGateTest.kt` + `SwipeUpAccumulatorTest.kt` deleted (host tests for deleted classes)
- `ShellPresenceTest.kt`, `DrawerWebcamGatingTest.kt`, `FineTuneNavTest.kt` deleted (drawer-swipe androidTests; FineTuneNavTest was already known-broken on hardware per [[dinghy-instrumented-swipe-threshold]])
- `HomeAction.OpenDrawer` data object removed from the sealed interface — `when` branches over `HomeAction` are now exhaustive without it
- `LauncherDest.Drawer` enum variant removed from `PrintStatusUiModel`; `standbyLauncherDests` no longer adds it; KDoc updated to describe Console as the last tile in the base case
- `PrintStatusUiModelTest` launcher assertions updated: `drawerAlwaysLast` test renamed to `consoleAlwaysLast`, both test lists trimmed to 6/8 items (no Drawer)

**Task 2 — AppShell drawer/swipe removal + System wiring:**
- Removed `var drawerOpen by remember { mutableStateOf(false) }` state
- Removed `BackHandler(enabled = drawerOpen) { drawerOpen = false }` drawer BackHandler
- Removed entire `pointerInput(navBackStackEntry, promptView.visible)` block (the swipe-up gesture detector + `suppressSwipe` val + `SwipeUpAccumulator` usage)
- Removed `if (drawerOpen) { AppDrawer(...) }` overlay block
- Removed bottom-edge handle `Box` (the 8dp `t.hair` affordance strip)
- Removed `SWIPE_UP_THRESHOLD_PX = 80f` private const
- Removed `android.util.Log`, `BuildConfig`, `height`, `size`, `pointerInput` unused imports
- Removed `AppDrawer` / `SwipeUpAccumulator` imports (deleted files)
- Updated WaterfallHome block: removed `onOpenDrawer = { drawerOpen = true }` arg
- Updated CalibrationProbe `BackHandler`: removed `!drawerOpen &&` from the `enabled` condition
- Added `composable<NavDest.System>` block after `composable<NavDest.About>` wiring `SystemPageScreen(container, onNavigate, onBack)` — with a comment that System is intentionally absent from `screenOwnsEstop` and `FOOT_GUN_DESTS` (D-06, the `else -> null` path in `shouldPopToRoot` covers it)

**Task 3 — PrintStatusField + PrintStatusScreen retargeting:**
- `PrintStatusStandbyField`: removed `onOpenDrawer: () -> Unit` param; System foot `OutlinedControl` now calls `{ onNavigate(NavDest.System) }` directly
- `LauncherGrid`: removed `onOpenDrawer` param; Drawer tile special-case logic eliminated; all tiles route through `launcherDestTarget(d)?.let(onNavigate)`
- `launcherDestTarget()`, `launcherIcon()`, `launcherLabelRes()`: `LauncherDest.Drawer -> ...` branches removed; `when` expressions are now exhaustive over the 8 remaining variants
- Added `SystemShortcutTile` composable (icon-only, hair outline, `DinghyIcons.FootSystem` glyph, `R.string.home_foot_system` contentDescription) as a new tile in `ShortcutRow` for mid-print System access (D-06/28-05)
- `ShortcutRow` updated: inserts `SystemShortcutTile` after Tune in the row; Tune keeps its flexible weight; System gets `weight(1f)`
- `PrintStatusScreen(container)` public overload: removed `onOpenDrawer: () -> Unit = {}` param + its pass-down to `PrintStatusContent`
- `PrintStatusScreen(state)` stateless overload: removed `onOpenDrawer` param + pass-down
- `PrintStatusContent` private composable: removed `onOpenDrawer` param + pass-down to `PrintStatusStandbyField`
- KDoc updated throughout to reflect NavDest.System routing

## Verification Results

All acceptance criteria met:

- `DELETED_OK`: all 7 files absent (4 core drawer/swipe + 3 drawer/swipe androidTests)
- `grep -c 'OpenDrawer' HomeAction.kt` → 0
- `grep -rc 'LauncherDest.Drawer' PrintStatusUiModel.kt PrintStatusUiModelTest.kt` → 0, 0
- `grep -rilE 'drawer|swipeup' app/src/androidTest/` → zero hits
- `grep -c 'drawerOpen|SwipeUpAccumulator|SWIPE_UP_THRESHOLD_PX|suppressSwipe' AppShell.kt` → 0
- `grep -c 'import.*AppDrawer' AppShell.kt` → 0
- `grep -c 'composable<NavDest.System>' AppShell.kt` → 1
- `grep -A6 'screenOwnsEstop' AppShell.kt | grep 'NavDest.System'` → zero hits (System NOT in list)
- `grep -r 'onOpenDrawer' app/src/` → zero hits
- `grep -c 'NavDest.System' PrintStatusField.kt` → 7 (idle foot + standby System tile path + shortcut tile + KDoc)
- `grep -c 'FootSystem' PrintStatusField.kt` → 2 (idle foot bar + SystemShortcutTile)
- `grep -c 'OpenDrawer' PrintStatusField.kt` → 0
- `:app:compileDebugKotlin` → BUILD SUCCESSFUL
- `:app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL
- `:app:testDebugUnitTest` FULL suite → BUILD SUCCESSFUL
- `DinghyIconsTest` → still GREEN (LauncherDrawer left in registry)

## Deviations from Plan

**1. [Rule 2 - Enhancement] LauncherDrawer DinghyIcons token retained**
- **Found during:** Task 1 verification
- **Issue:** Plan said "if `LauncherDrawer` was fully unreferenced, remove it from DinghyIcons and its `all` membership". After removing `LauncherDest.Drawer`, `LauncherDrawer` is still referenced FROM `DinghyIcons.all` (the registry's own list). Removing it would require editing `DinghyIcons.kt`, which is outside the plan's file scope and risks the drift-guard test.
- **Fix:** Retained `LauncherDrawer` in the registry. The `launcherIcon(LauncherDest.Drawer)` when-branch was removed (it compiled away with the enum variant), so no app UI references it. DinghyIconsTest stays green.
- **Files modified:** None — explicit no-edit decision.

None other — plan executed within deviation rules.

## Known Stubs

None. This plan performs deletions and retargeting only; no stub data flows to any UI rendering.

## Threat Flags

None. This plan:
1. Removes network surface (the drawer was an overlay, not a network endpoint)
2. NavDest.System is intentionally absent from `screenOwnsEstop` — shell-level FloatingEStop applies (T-28-05-02 mitigated)
3. T-28-05-01 (stranded destination): reachability verified — all former DRAWER_TILES destinations confirmed reachable before deletion (28-02 System page + 28-03 idle list cover every tile)

## Self-Check: PASSED

- AppDrawer.kt: MISSING (deleted — correct)
- SwipeUpAccumulator.kt: MISSING (deleted — correct)
- AppDrawerOutputsGateTest.kt: MISSING (deleted — correct)
- SwipeUpAccumulatorTest.kt: MISSING (deleted — correct)
- ShellPresenceTest.kt: MISSING (deleted — correct)
- DrawerWebcamGatingTest.kt: MISSING (deleted — correct)
- FineTuneNavTest.kt: MISSING (deleted — correct)
- AppShell.kt: FOUND — contains `composable<NavDest.System>`, zero drawerOpen/SwipeUp/suppressSwipe hits
- HomeAction.kt: FOUND — zero OpenDrawer hits
- PrintStatusUiModel.kt: FOUND — zero LauncherDest.Drawer hits
- PrintStatusUiModelTest.kt: FOUND — zero LauncherDest.Drawer hits
- PrintStatusField.kt: FOUND — zero onOpenDrawer hits, 7 NavDest.System hits, 2 FootSystem hits
- PrintStatusScreen.kt: FOUND — zero onOpenDrawer hits
- Commits 1af489a, 365466f, 68dc1f7: verified in git log
- `:app:testDebugUnitTest` full suite: BUILD SUCCESSFUL
- `:app:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL
