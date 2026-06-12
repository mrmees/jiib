---
phase: 27-motion-calibration
plan: 02
subsystem: navigation
tags: [nav-routes, calibration, back-handler, foot-gun, shell]
dependency_graph:
  requires: [27-01]
  provides: [calibration-navhost-spine]
  affects: [AppShell, NavDest, ShellNavState, CalibrationHubScreen]
tech_stack:
  added: []
  patterns:
    - "6 composable<NavDest.CalibrationXxx> blocks replace when(calibrationRoutine)"
    - "CalibrationRoutine.toNavDest() mapping helper for hub onOpen"
    - "D-09 overlay-gated BackHandler inside composable<NavDest.CalibrationProbe>"
key_files:
  created:
    - app/src/test/java/works/mees/dinghy/ui/route/NavDestRoundTripTest.kt
    - app/src/test/java/works/mees/dinghy/ui/route/FootGunDestsTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt
    - app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
    - app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt
    - app/src/test/java/works/mees/dinghy/ui/route/PopToRootTest.kt
    - app/src/test/java/works/mees/dinghy/ui/route/HomeActionTest.kt
    - app/src/test/java/works/mees/dinghy/shell/ShellNavStateTest.kt
decisions:
  - "D-07 nav spine: 6 real NavHost routes replace in-screen when(calibrationRoutine) dispatch; NavHost back-stack is single source of truth"
  - "D-09 BackHandler: overlay-gated (Active||starting) && !drawerOpen && !nav.scanActive && !promptView.visible to avoid out-prioritizing overlay dismissal (WARNING-6)"
  - "D-17 foot-gun coverage: all 6 CalibrationXxx routes in FOOT_GUN_DESTS + currentNavDest mapping so pop-to-root fires from any routine"
  - "CalibrationHubScreen.onNavigate renamed to onOpen (seam rename; full rebuild in 27-04)"
  - "ShellNavStateTest: removed 3 tests referencing deleted calibrationRoutine field; replaced with no-crash + non-perturb assertions"
metrics:
  duration: "~45min"
  completed: "2026-06-12"
  tasks_completed: 2
  files_changed: 13
---

# Phase 27 Plan 02: D-07 Calibration Nav Spine Summary

**One-liner:** Atomic conversion of calibration sub-nav from in-screen `when(calibrationRoutine)` to 6 real `NavHost` routes with overlay-gated D-09 BackHandler and full pop-to-root/swipe-suppress coverage.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1+2 | 6 NavDest sub-routes + all consumers migrated atomically | `ed5d1cb` | 13 files |

**Note:** Task 1 (NavDest + tests) and Task 2 (consumer migration + shell + tests) were committed atomically per the plan's P24 lesson: NavDest.Calibration removal leaves the whole tree uncompilable until every consumer is updated. A split commit would leave an uncompilable HEAD.

## What Was Built

### NavDest.kt
- `NavDest.Calibration` REMOVED
- 6 new `@Serializable data object` members added: `CalibrationHub`, `CalibrationProbe`, `CalibrationBedMesh`, `CalibrationScrewsTilt`, `CalibrationZTilt`, `CalibrationQgl`
- `knownNavDests` expanded from 17 to 22 members
- `FOOT_GUN_DESTS` expanded from 3 to 8 members (Move + Extrude + all 6 CalibrationXxx)
- `CalibrationRoutine.toNavDest()` extension function added covering all 5 routines
- `shouldPopToRoot` predicate unchanged (checks `current in FOOT_GUN_DESTS` — expands automatically)

### ShellNavState.kt
- `calibrationRoutine by mutableStateOf<CalibrationRoutine?>(null)` field REMOVED
- `applyEntryReset(NavDest.CalibrationHub)` body cleared to no-op (call-site kept for FIX-3 symmetry)
- `CalibrationRoutine` import removed
- KDocs updated throughout to document the Phase 27 D-07 removal

### AppShell.kt (5 edit sites)
1. `val calibrationRoutine = nav.calibrationRoutine` read DELETED
2. Old in-screen Calibration BackHandler (`isRoute<NavDest.Calibration>() && calibrationRoutine != null`) DELETED
3. Swipe-suppress set: `isRoute<NavDest.Calibration>()` replaced with all 6 isRoute checks
4. `currentNavDest` mapping: `isRoute<NavDest.Calibration>() -> NavDest.Calibration` replaced with 6 branches
5. `composable<NavDest.Calibration> { when(calibrationRoutine) { ... } }` REPLACED with 6 separate `composable<NavDest.CalibrationXxx>` blocks:
   - `CalibrationHub` — `LaunchedEffect(Unit) { applyEntryReset }` + `CalibrationHubScreen(onOpen = { routine -> navController.navigate(routine.toNavDest()) })`
   - `CalibrationProbe` — D-09 overlay-gated BackHandler + `ProbeCalibrateScreen`
   - `CalibrationBedMesh` — `BedMeshScreen`
   - `CalibrationScrewsTilt` — `ScrewsTiltScreen`
   - `CalibrationZTilt` — `TiltScreen(variant = ZTilt)`
   - `CalibrationQgl` — `TiltScreen(variant = Qgl)`

### CalibrationHubScreen.kt
- `onNavigate: (CalibrationRoutine) -> Unit` → `onOpen: (CalibrationRoutine) -> Unit` (seam rename)
- Internal `onClick = { onNavigate(entry.routine) }` → `onClick = { onOpen(entry.routine) }`
- KDoc updated

### Atomic consumer migrations
- `HomeAction.kt`: `NavDest.Calibration` → `NavDest.CalibrationHub` in `buildIdleActions`
- `PrintStatusField.kt`: `LauncherDest.Calibration -> NavDest.Calibration` → `... -> NavDest.CalibrationHub`
- `AppDrawer.kt`: `DrawerTileSpec(dest = NavDest.Calibration)` → `NavDest.CalibrationHub`
- `RootController.kt`: comment updated to drop calibrationRoutine reference

### Tests
- **New: `NavDestRoundTripTest.kt`** — explicit round-trip for all 22 dests + 5 CalibrationRoutine→NavDest mapping assertions
- **New: `FootGunDestsTest.kt`** — asserts FOOT_GUN_DESTS.size == 8, all 8 members present, 14 non-foot-gun dests absent
- **Rewritten: `PopToRootTest.kt`** — `calibration_isFootGun_returnsTrue` expanded to 6 tests (one per route); `footGunDests_containsExactlyThreeMembers` → `containsExactlyEightMembers`
- **Rewritten: `HomeActionTest.kt`** — all `NavDest.Calibration` → `NavDest.CalibrationHub` (3 sites)
- **Rewritten: `ShellNavStateTest.kt`** — 3 tests referencing deleted `calibrationRoutine` field removed; 2 replaced with no-crash/non-perturb assertions; `resetTransient` test updated (no calibrationRoutine assertions)

## Verification Evidence

### Grep proofs
```
grep -rn 'NavDest\.Calibration\b' app/src/main app/src/test  →  0 matches (comments only)
grep -rn 'calibrationRoutine' (live code, non-comment)        →  0 live field refs
```

### Build gates
- `:app:assembleDebug` BUILD SUCCESSFUL
- `:app:testDebugUnitTest --tests works.mees.dinghy.ui.route.* --tests works.mees.dinghy.shell.*` GREEN
- All rewritten + new tests: PopToRootTest / HomeActionTest / ShellNavStateTest / NavDestRoundTripTest / FootGunDestsTest GREEN

### Structural checks
- `composable<NavDest.Calibration*>` count in AppShell.kt: **6** (grep)
- All 6 calibration routes in swipe-suppress set: confirmed
- All 6 calibration routes in currentNavDest mapping: confirmed
- D-09 BackHandler present in `composable<NavDest.CalibrationProbe>`: confirmed
- BackHandler gating: `(Active || starting) && !drawerOpen && !nav.scanActive && !promptView.visible`

## Deviations from Plan

**1. [Rule 2 - Seam] CalibrationHubScreen.onNavigate renamed before full rebuild**

The plan instructs renaming `onNavigate` → `onOpen` as a "rename-only seam." This was done minimally: only the parameter name changed + the internal call-site + KDoc. The full hub rebuild is deferred to 27-04.

**2. Tasks 1 + 2 committed atomically (single commit)**

The plan notes the P24 lesson about atomicity. Task 1 removes `NavDest.Calibration` which is referenced in AppShell, ShellNavState, HomeAction, PrintStatusField, AppDrawer — leaving the tree uncompilable until Task 2 completes all migrations. Per the P24 lesson, committing Task 1 alone would leave an uncompilable HEAD, so both tasks are in one commit.

Otherwise the plan executed exactly as written.

## Known Stubs

None — this plan is purely structural navigation wiring. No UI surfaces are rendered in new screens; existing screen composables are wired to the new routes unchanged.

## Threat Flags

No new network endpoints, auth paths, or file access patterns introduced. The D-09 BackHandler (T-27-02-01) is implemented as specified with the overlay-gate (WARNING-6 compliant). FOOT_GUN_DESTS coverage (T-27-02-02) is implemented with all 6 calibration routes.

## Self-Check: PASSED

- `ed5d1cb` exists in git log: confirmed
- All 13 files created/modified exist on disk: confirmed
- Zero `NavDest.Calibration\b` stale refs: confirmed (grep = 0)
- Zero live `calibrationRoutine` field refs: confirmed (grep = 0 live code)
- 6 `composable<NavDest.Calibration*>` blocks in AppShell: confirmed (grep = 6)
- assembleDebug + testDebugUnitTest GREEN: confirmed
