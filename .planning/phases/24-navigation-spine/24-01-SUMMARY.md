---
phase: 24
plan: "01"
subsystem: navigation
tags: [nav-dest, route-model, sealed-interface, navigation-compose, host-tests]
dependency_graph:
  requires: []
  provides: [NavDest, knownNavDests, FOOT_GUN_DESTS, shouldPopToRoot, HomeAction, buildIdleActions]
  affects: [AppShell, AppDrawer, ShellNavState, RootController, MainActivity, PrintStatusField, PrintStatusScreen]
tech_stack:
  added:
    - "navigation-compose 2.8.9 (2.8.x line, minSdk 21, AGP-8.7/compileSdk-36 compatible)"
  patterns:
    - "@Serializable sealed interface for type-safe NavHost route model"
    - "knownNavDests list as sealed-interface equivalent of enum.entries"
    - "FOOT_GUN_DESTS pure predicate — host-testable, zero NavHost dependency (FIX-8)"
    - "HomeAction sealed interface for data-driven idle-action list with D-08 capability gates"
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt
    - app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
    - app/src/test/java/works/mees/dinghy/ui/route/PopToRootTest.kt
    - app/src/test/java/works/mees/dinghy/ui/route/HomeActionTest.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/StartDestMapping.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/main/java/works/mees/dinghy/MainActivity.kt
    - app/src/test/java/works/mees/dinghy/ui/route/TopRouteTest.kt
    - app/src/test/java/works/mees/dinghy/shell/StartDestMappingTest.kt
    - app/src/test/java/works/mees/dinghy/shell/ShellNavStateTest.kt
    - app/src/test/java/works/mees/dinghy/ui/shell/AppDrawerOutputsGateTest.kt
decisions:
  - "FIX-5 Wave-0 isolation: Webcam row in buildIdleActions uses DinghyIcons.LauncherDrawer + R.string.cd_launcher_drawer as PLACEHOLDER(24-04) to avoid cross-Wave-0 dependency on the 24-02 ASK-OWNER plan"
  - "FIX-8 Pure predicate: PopToRootTest is PURE JVM (no TestNavController, no navigation-testing dependency); shouldPopToRoot + FOOT_GUN_DESTS in NavDest.kt are directly host-testable"
  - "TopRoute.Shell changed from data class Shell(val dest: Dest) to data object Shell — NavHost owns routing, no payload needed"
  - "knownNavDests list introduced as sealed-interface equivalent of Dest.entries for parseStartDest and round-trip testing"
metrics:
  duration: "~90 minutes (resumed from prior session)"
  completed: "2026-06-09"
  tasks_completed: 3
  files_changed: 19
---

# Phase 24 Plan 01: NavDest Route Model + Navigation-Compose Foundation Summary

Stand up the navigation-spine foundation: `@Serializable sealed interface NavDest` (17 members, replaces
`Dest` enum) + `HomeAction` idle-list model + `FOOT_GUN_DESTS`/`shouldPopToRoot` pure predicate + full
mechanical Dest→NavDest rename across all 13 call-site files, with 3 host-source-sets compiling GREEN.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add Navigation-Compose 2.8.9 | be3f15b | libs.versions.toml, app/build.gradle.kts |
| 2 | NavDest + HomeAction + pure-predicate tests | f411759 | NavDest.kt, HomeAction.kt, PopToRootTest.kt, HomeActionTest.kt |
| 3 | Mechanical Dest→NavDest rename (all 13 files) | aa17941 | 13 main+test files |

## What Was Built

**Task 1:** Added `navigation = "2.8.9"` to the version catalog and `implementation(libs.androidx.navigation.compose)` to app/build.gradle.kts. Pinned to the 2.8.x line (minSdk 21, AGP-8.7/compileSdk-36 safe; explicitly NOT 2.9.x which requires compileSdk 37/AGP 9). No navigation-testing dependency (FIX-8).

**Task 2:** Created `NavDest.kt` with:
- `@Serializable sealed interface NavDest` — 17 `@Serializable data object` members
- `NavDest.WaterfallHome` replaces `Dest.PrintStatus` (D-01 rename, morphing waterfall home)
- `knownNavDests: List<NavDest>` — all 17 members for iteration (sealed interfaces have no `.entries`)
- `FOOT_GUN_DESTS: Set<NavDest>` = `{Move, Extrude, Calibration}` (D-04)
- `shouldPopToRoot(current: NavDest?, printActive: Boolean): Boolean` — pure predicate

Created `HomeAction.kt` with:
- `sealed interface HomeAction { data class Destination(...); data object OpenDrawer }`
- `buildIdleActions(spoolmanPresent, bookmarksExist, outputsPresent, webcamEnabled)` with D-06 order
  (Spool→Files→Move→Extrude→Macros→Calibration→Outputs→Webcam) and D-08 capability gates
- Webcam row uses PLACEHOLDER icon/label (FIX-5 Wave-0 isolation; 24-04 will supply the real icon)

Created test scaffolds with 23 tests total, all GREEN:
- `PopToRootTest.kt` — 11 pure JVM tests for FOOT_GUN_DESTS/shouldPopToRoot (no NavController)
- `HomeActionTest.kt` — 12 tests for buildIdleActions (capability gates, D-06 order, icon law)

**Task 3:** Mechanical rename across 13 files (9 main, 4 test):
- All `import Dest` → `import NavDest` in main source
- `TopRoute.Shell(dest: Dest)` data class → `data object Shell` (no payload)
- All `Dest.PrintStatus` → `NavDest.WaterfallHome` (4 live-code sites)
- All remaining `Dest.X` → `NavDest.X` throughout AppShell, AppDrawer, PrintStatusField, PrintStatusScreen, ShellNavState, StartDestMapping, RootController, MainActivity
- Test files: `Dest.entries` → `knownNavDests`, `dest.name` → `dest::class.simpleName`, `TopRoute.Shell(...)` → `TopRoute.Shell`

## Build Verification

All three source sets compile GREEN:
- `:app:compileDebugKotlin` — BUILD SUCCESSFUL
- `:app:compileDebugUnitTestKotlin` — BUILD SUCCESSFUL
- `:app:compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL (pre-existing deprecation warnings in androidTest, unrelated)

All 29 relevant host unit tests pass:
- `PopToRootTest` — 11/11
- `HomeActionTest` — 12/12
- `TopRouteTest` — 6/6 (updated Shell assertion)
- `StartDestMappingTest` — 4/4 (uses knownNavDests)
- `ShellNavStateTest` — 3/3 (WaterfallHome replaces PrintStatus)
- `AppDrawerOutputsGateTest` — 4/4 (NavDest.Outputs)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Double-replacement of pre-targeted WaterfallHome sites**
- **Found during:** Task 3 build verification
- **Issue:** Three `Dest.PrintStatus` sites were manually replaced with `NavDest.WaterfallHome` BEFORE the bulk `replace_all Dest. → NavDest.` ran, causing them to become `NavNavDest.WaterfallHome`. Same in AppDrawer.
- **Fix:** Replaced all `NavNavDest.` → `NavDest.` in AppShell.kt and AppDrawer.kt
- **Files modified:** AppShell.kt, AppDrawer.kt
- **Detected by:** First build after Task 3: `Unresolved reference 'NavNavDest'` errors

**2. [Rule 2 - Unused import] Removed dead `NavDest` import from TopRoute.kt**
- **Found during:** Task 3 import cleanup
- **Issue:** After removing the Dest enum and changing Shell to data object, an `import works.mees.dinghy.ui.route.NavDest` added in the summary context was unused in TopRoute.kt
- **Fix:** Removed the import
- **Files modified:** TopRoute.kt

### Notes

The `Dest` enum was never in a standalone `Dest.kt` file — it was defined inside `TopRoute.kt` prior to this plan. After Task 3, the only survivors in `works.mees.dinghy.ui.route` are `NavDest.kt`, `HomeAction.kt`, and `TopRoute.kt`.

## Threat Flags

None. This plan introduces no new network endpoints, auth paths, file access patterns, or schema changes.

## Known Stubs

**HomeAction.kt — Webcam row placeholder (intentional, tracked)**
- File: `app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt` ~line 143
- Stub: `DinghyIcons.LauncherDrawer` + `R.string.cd_launcher_drawer` used as Webcam icon/label
- Comment: `// PLACEHOLDER(24-04): replace with DinghyIcons.LauncherWebcam + R.string.cd_launcher_webcam once 24-02 ships the icon token`
- Reason: 24-02 (Webcam icon) is a parallel Wave-0 plan not yet executed; this plan cannot reference symbols that don't exist yet (FIX-5 Wave-0 isolation). Plan 24-04 will wire the real token.
- Impact: The stub affects only the `buildIdleActions` Webcam row icon display. The `HomeActionTest` `allDestinations_haveNonNullIcon` test passes because `DinghyIcons.LauncherDrawer` is non-null; a future test in 24-04 will verify the correct webcam icon.

## Self-Check: PASSED

Files created:
- FOUND: app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt
- FOUND: app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
- FOUND: app/src/test/java/works/mees/dinghy/ui/route/PopToRootTest.kt
- FOUND: app/src/test/java/works/mees/dinghy/ui/route/HomeActionTest.kt

Commits:
- FOUND: be3f15b (chore: add navigation-compose 2.8.9)
- FOUND: f411759 (feat: NavDest route model + HomeAction + tests)
- FOUND: aa17941 (refactor: mechanical Dest→NavDest rename)
