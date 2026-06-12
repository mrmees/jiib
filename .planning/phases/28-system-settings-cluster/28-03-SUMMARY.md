---
phase: 28-system-settings-cluster
plan: "03"
subsystem: route-model
tags: [idle-list, home-action, d-05, wave-2]
dependency_graph:
  requires:
    - 28-01 (DinghyIcons.LauncherFineTune + cd_launcher_fine_tune string)
  provides:
    - buildIdleActions emits Temperature / Console / Fine-Tune as unconditional idle-list rows
    - HomeActionTest asserts 11-row full list + new D-05 order
  affects:
    - app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
    - app/src/test/java/works/mees/dinghy/ui/route/HomeActionTest.kt
tech_stack:
  added: []
  patterns:
    - Unconditional (no-gate) row insertion in buildIdleActions buildList block
    - Exact-count assertions in HomeActionTest (no >= weakening)
key_files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
    - app/src/test/java/works/mees/dinghy/ui/route/HomeActionTest.kt
decisions:
  - "Temperature/Console/FineTune inserted after CalibrationHub, before Outputs gate (D-05 order)"
  - "All three rows unconditionally present — no capability gate, revising P24 D-07 printing-only posture"
  - "HomeAction.OpenDrawer left intact — its removal is owned atomically by 28-05"
  - "Minimal-list count rises from 4 to 7 (three new always-present rows added to no-capability case)"
  - "Full-list count rises from 8 to 11 (three new rows added to all-capability case)"
metrics:
  duration: ~3 minutes
  completed: "2026-06-12"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 2
---

# Phase 28 Plan 03: Rehome Temperature / Console / Fine-Tune to WaterfallHome Idle List Summary

**One-liner:** Three drawer-orphan destinations (Temperature, Console, Fine-Tune) added as unconditional always-present rows to `buildIdleActions` in D-05 order; HomeActionTest updated to exact 11-row full list.

## Tasks Completed

| Task | Name | Commit | Key files |
|------|------|--------|-----------|
| 1 | Extend buildIdleActions with Temperature / Console / Fine-Tune rows (D-05) | f7289a2 | HomeAction.kt |
| 2 | Update HomeActionTest count + order for the 11-row full list | c6cd704 | HomeActionTest.kt |

## What Was Built

**Task 1 — buildIdleActions extension:**
- Added three `HomeAction.Destination` rows after `CalibrationHub`, before the `outputsPresent` gate
- `NavDest.Temperature` / `R.string.cd_launcher_temperature` / `DinghyIcons.LauncherTemperature` (`thermostat`)
- `NavDest.Console` / `R.string.cd_launcher_console` / `DinghyIcons.LauncherConsole` (`terminal`)
- `NavDest.FineTune` / `R.string.cd_launcher_fine_tune` / `DinghyIcons.LauncherFineTune` (`line_style`)
- All three are unconditionally present (no capability gate) — revises P24 D-07 posture
- `HomeAction.OpenDrawer` left intact for atomic removal in 28-05

**Task 2 — HomeActionTest update:**
- Full-list count: `8 → 11` (all three new rows always-present in all-capabilities case)
- Minimal-list count: `4 → 7` (same three rows always-present even with no optional capabilities)
- `v1OrderIsPreserved` test: expected list extended with `Temperature → Console → FineTune` after `CalibrationHub`, before `Outputs`
- All assertions remain exact-count (no `size >= N` weakening — test regression-guards the D-05 reachability precondition for 28-05)
- `OpenDrawer` test case intact (variant still present in HomeAction.kt)

## Verification Results

- `grep -c 'NavDest.Temperature|NavDest.Console|NavDest.FineTune' HomeAction.kt`: 3
- `grep -c 'LauncherFineTune' HomeAction.kt`: 1
- `grep -c 'OpenDrawer' HomeAction.kt`: 1 (intact)
- `:app:compileDebugKotlin`: BUILD SUCCESSFUL
- `grep -c '11' HomeActionTest.kt`: 3
- `grep -c 'NavDest.Temperature|NavDest.Console|NavDest.FineTune' HomeActionTest.kt`: 9
- `:app:testDebugUnitTest --tests *HomeActionTest`: BUILD SUCCESSFUL (all tests GREEN)

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. This plan modifies route-model code only; no stub data flows to any UI rendering.

## Threat Flags

None. Pure route-model extension; no new network surface, no untrusted input.

## Self-Check: PASSED

- HomeAction.kt: FOUND (contains NavDest.Temperature, NavDest.Console, NavDest.FineTune, LauncherFineTune, OpenDrawer)
- HomeActionTest.kt: FOUND (contains 11, NavDest.Temperature, NavDest.Console, NavDest.FineTune)
- Commit f7289a2: verified in git log
- Commit c6cd704: verified in git log
