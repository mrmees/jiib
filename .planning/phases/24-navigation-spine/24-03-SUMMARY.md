---
phase: 24-navigation-spine
plan: "03"
subsystem: navigation
tags: [navhost, shell, navigation-compose, routing, estop]
dependency_graph:
  requires: [24-01]
  provides: [24-04, 24-05]
  affects: [AppShell, ShellNavState, RootController]
tech_stack:
  added: []
  patterns:
    - "NavHost with 17 composable<NavDest.*> entries replacing when(dest) hub-and-spoke"
    - "Hoisted session holders above NavHost (holder survives destination changes)"
    - "App-level FloatingEStop + ConfirmGuard as Box siblings after NavHost"
    - "shouldPopToRoot pure predicate wired in LaunchedEffect(printState)"
    - "isRoute<T>() private extension for nav-compose 2.8.9 type-arg incompatibility"
    - "applyEntryReset called in LaunchedEffect(Unit) inside Macros/Calibration/FineTune lambdas"
key_files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java//works/mees/dinghy/ui/shell/ShellNavState.kt
    - app/src/test/java/works/mees/dinghy/shell/ShellNavStateTest.kt
decisions:
  - "isRoute<T>() helper used instead of hasRoute<T>() because nav-compose 2.8.9 resolves the instance method (taking String) which does not accept type args; companion-level generic is not accessible via instance; string-comparison helper is stable and correct"
  - "FIX-3 accepted: post-Splash lands on WaterfallHome + sub-nav resets (NavHost decomposes during RootController Splash gate; owner-locked 2026-06-09)"
  - "Tasks 1 and 2 committed together (atomic) because the NavHost structure and the pop-to-root LaunchedEffect are inseparable — both live in AppShell.kt"
metrics:
  duration: "~45 minutes (context-continuation from prior session)"
  completed: "2026-06-09"
  tasks_completed: 2
  files_changed: 3
---

# Phase 24 Plan 03: NavHost Shell Summary

NavHost replaces AppShell's ~1000-line `when(dest)` hub-and-spoke with a Navigation-Compose back-stack; all session holders hoisted above; FloatingEStop promoted to app-overlay layer.

## What Was Built

### AppShell.kt — NavHost migration

- `NavHost(navController, startDestination = nav.startDest ?: NavDest.WaterfallHome)` with 18 `composable<NavDest.*>` entries (one per destination + one startDest redirect)
- All ~20 session holders (`rememberXxxHolder()`) live ABOVE the NavHost block; `remember(store)` re-keys preserved; holder instances pass into `composable<>` lambdas as closed-over values
- 4 `DisposableEffect { onDispose { holder.cancel() } }` leak-cancel blocks preserved verbatim (webcam/spool/console/macro)
- All overlays (MacroPopup, ScanSurface, PromptDialog, DevThemeCycler, AppDrawer, bottom bar) remain as Box siblings after the NavHost
- **FIX-1**: `FloatingEStop` + `ConfirmGuard` hoisted to AppShell overlay layer — visible from ANY destination when `printState == Printing || Paused`
- **FIX-2**: BackHandler priority correct — overlay handlers compose AFTER NavHost, so they have higher priority than NavHost's implicit back handling; in-screen BackHandlers inside `composable<>` lambdas have even higher priority
- **FIX-7**: `startDestination = nav.startDest ?: NavDest.WaterfallHome` — seed read once at composition, not via LaunchedEffect re-fire
- **D-04**: `LaunchedEffect(printerState.printState)` uses `shouldPopToRoot` pure predicate (from 24-01) to pop Move/Extrude/Calibration → WaterfallHome on print start/end
- `applyEntryReset` called in `LaunchedEffect(Unit)` inside Macros/Calibration/FineTune composable lambdas
- Private `isRoute<T>()` extension for nav-compose 2.8.9 type-argument incompatibility

### ShellNavState.kt — slimmed

- Removed: `var dest`, `val backStack`, `fun navigateTo()`, `fun goBack()`
- `applyEntryReset` promoted from `private` to `internal` (AppShell composable lambdas in same package call it)
- `startDest: NavDest?` stored as a `val` (readable by AppShell once at composition time)
- KDoc updated to document Phase-24-03 migration and FIX-3 accepted regression

### ShellNavStateTest.kt — updated

- Removed all tests for the deleted router API (`navigateTo`, `dest`, `backStack`, `goBack`)
- Added: `applyEntryReset` behavior tests for Macros/Calibration/FineTune
- Added: `resetTransient` clears transient but not preserved state (G-A1 contract)
- Added: `startDest` stored/null-when-not-seeded
- Added: four IA destinations are distinct in `knownNavDests` (Devices rename verification)

## Acceptance Criteria

| Criterion | Result |
|-----------|--------|
| `composable<NavDest.` count ≥ 17 | 18 found |
| Zero live `when(dest)` routing | 0 found (2 in comments only) |
| 4 `onDispose.*cancel` blocks | 4 found |
| `FloatingEStop` in overlay Box | Confirmed |
| `shouldPopToRoot` wired in LaunchedEffect | Confirmed |
| `applyEntryReset` in composable lambdas | Confirmed (Macros/Calibration/FineTune) |
| Full unit test suite green | BUILD SUCCESSFUL |
| PopToRootTest green | BUILD SUCCESSFUL |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `hasRoute<T>()` type argument incompatibility in nav-compose 2.8.9**
- **Found during:** Task 1 compilation
- **Issue:** `destination.hasRoute<NavDest.X>()` resolved to the instance method `hasRoute(String, Bundle?)` in nav-compose 2.8.9, which does not accept type arguments. The generic reified companion-level `hasRoute<T>()` was inaccessible via instance call. Attempting `NavDestination.hasRoute<T>(this)` gave "Unresolved reference 'hasRoute'".
- **Fix:** Added a private `isRoute<T>()` file-level extension using `T::class.qualifiedName` route string comparison with `?` query-param suffix check. Replaced all `hasRoute<` with `isRoute<` throughout AppShell.kt.
- **Files modified:** `AppShell.kt`
- **Commit:** 0c62f25

**2. [Rule 1 - Bug] ShellNavStateTest.kt compilation failure after ShellNavState slim**
- **Found during:** Task 1 compilation (test sourceset)
- **Issue:** Old test used `nav.navigateTo()`, `nav.dest`, `nav.backStack`, `nav.goBack()` — all removed from ShellNavState.
- **Fix:** Rewrote ShellNavStateTest to test the remaining functionality.
- **Files modified:** `ShellNavStateTest.kt`
- **Commit:** 0c62f25

**3. [Rule 1 - Bug] SpoolPrefilterSeed constructor type mismatch in test**
- **Found during:** Test rewrite
- **Issue:** Used `SpoolPrefilterSeed("PLA", emptyList())` but constructor takes `List<String>, List<String>`.
- **Fix:** Changed to `SpoolPrefilterSeed(listOf("PLA"), emptyList())`.
- **Files modified:** `ShellNavStateTest.kt`
- **Commit:** 0c62f25

## Known Stubs

None. All destinations are wired to their real composable implementations. The NavHost is fully functional.

## Threat Flags

None. No new network endpoints, auth paths, or schema changes introduced.

## Self-Check: PASSED
- `AppShell.kt` exists and contains `NavHost`: confirmed
- `ShellNavState.kt` exists and lacks `navigateTo`: confirmed
- `ShellNavStateTest.kt` exists and references `applyEntryReset`: confirmed
- Commit `0c62f25` exists: confirmed
- Full unit test suite green: confirmed
