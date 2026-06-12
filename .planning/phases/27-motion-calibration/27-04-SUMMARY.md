---
phase: 27-motion-calibration
plan: "04"
subsystem: calibration-ui
tags: [calibration, hub, probe, list-focus, preview, stateless-seam]
dependency_graph:
  requires: ["27-01", "27-02"]
  provides: ["CalibrationHubContent", "ProbeCalibrateContent", "CalibrationPreviews"]
  affects: ["27-05", "27-06"]
tech_stack:
  added: []
  patterns:
    - "list+Focus hub following SpoolScreen pilot (ListBlock + ListRow + DetailCard)"
    - "3-cell vertical columns (Move motif) — Z-nudge + step selector"
    - "state-adaptive FootButtonBar (Idle/Active/Accepted branches)"
    - "WARNING-5 stateless seam — thin VM-wrapper + CalibrationHubContent/ProbeCalibrateContent"
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/CalibrationPreviews.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
decisions:
  - "D-05: LaunchedEffect(routines) pre-selects first entry so Focus is never empty on initial render"
  - "D-06 owner override: all 5 routines always render; unsupported dimmed t.text3 but still selectable + openable"
  - "D-08: TESTZ paired vertical 3-cell columns (Move motif) with new ZReadoutDisplay center cell"
  - "D-09: state-adaptive FootButtonBar; Active branch omits Back (nav-layer BackHandler owns system-Back)"
  - "intentColor() preserved as internal fun in CalibrationHubScreen.kt — package-internal utility used by BedMeshScreen, ScrewsTiltScreen, TiltScreen, ProbeCalibrateScreen"
metrics:
  duration: "~40 minutes (continued from prior session)"
  completed: "2026-06-12"
  tasks_completed: 3
  tasks_total: 3
  files_created: 1
  files_modified: 4
---

# Phase 27 Plan 04: CalibrationHub + ProbeCalibrate Rebuild Summary

**One-liner:** Hub rebuilt as list+Focus screen (SpoolScreen pilot) with registry icons + pre-select; ProbeCalibrate rebuilt with 3-cell vertical TESTZ columns (Move motif), state-adaptive FootButtonBar, and verbatim starting/reset semantics.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Rebuild CalibrationHubScreen as list+Focus (D-05/D-06) | `9b15ef0` | CalibrationHubScreen.kt, strings.xml |
| 2 | Rebuild ProbeCalibrateScreen — vertical TESTZ columns + state-adaptive FootButtonBar | `67b6abc` | ProbeCalibrateScreen.kt |
| 3 | CalibrationPreviews + SampleFixtures calibration fixtures | `a212635` | CalibrationPreviews.kt (new), SampleFixtures.kt |

## What Was Built

### CalibrationHubScreen (Task 1)

The old `LazyVerticalGrid` tile layout was replaced with the jiib list+Focus grammar following the SpoolScreen pilot:

- **Thin wrapper** `CalibrationHubScreen(holder, onOpen, onBack)` collects `holder.routines`, owns `selected` state, pre-selects via `LaunchedEffect(routines)` (D-05 — Focus never empty).
- **Stateless** `CalibrationHubContent(routines, selected, onSelect, onOpen, onBack)` holds all layout — the WARNING-5 @Preview seam.
- Field = `ListBlock` of `ListRow`s (supported-first, all 5 always present; unsupported greyed `t.text3` but fully selectable + openable — D-06).
- Focus = `DetailCard` with `HubRoutineFocus`: prominent icon (grid.uDp × 0.75f) via `DinghyIconView(routineIconToken(routine))` using owner-confirmed Routine* tokens (no raw MaterialSymbol), title (20sp semibold), description (15sp), accent Open button.
- `FloatingEStop(visible=false)` structurally present for UAT-4 top-left reservation.
- `gutter = null`; single Back in FootButtonBar.
- `intentColor()` internal utility preserved (used by BedMeshScreen, ScrewsTiltScreen, TiltScreen, ProbeCalibrateScreen).

New strings added: `calibration_open_routine`, `calibration_hub_title`, 5 × `calibration_routine_*_title`, 5 × `calibration_routine_*_desc`, `cd_calibration_hub`.

### ProbeCalibrateScreen (Task 2)

The old gutter-based state-adaptive buttons were moved to FootButtonBar; the 2-cell Z-nudge column gained a center ZReadoutDisplay (D-08):

- **Thin wrapper** `ProbeCalibrateScreen(container, holder, onBack)` — `starting` boolean carried VERBATIM (Pitfall 6): `vm.state == ProbePageState.Idle && ("probe_calibrate" in inFlight || "z_endstop_calibrate" in inFlight)`. `LaunchedEffect(Unit) { holder.reset() }` preserved (Pitfall 3).
- **Stateless** `ProbeCalibrateContent(vm, step, starting, saveGuard, toastError, enabled, ...)` — the WARNING-5 @Preview seam.
- Field = two vertical 3-cell columns side by side (the Move motif):
  - Column 1 (Z-nudge): Z-up `ProbeIconButton` / `ZReadoutDisplay` (Geist Mono, `t.directional.z` outline, shows `vm.zPosition ?: vm.savedZOffset`) / Z-down `ProbeIconButton`
  - Column 2 (step selector): + / `StepDisplay` / − cycling `TESTZ_STEPS`
- **State-adaptive FootButtonBar** (D-09):
  - `Idle + starting` → disabled "Starting…" (neutral) — Back also suppressed here
  - `Idle + !homedGate` → Home All (accent) + Back
  - `Idle + homedGate` → Start (accent) + Back
  - `Active` → Accept (go) + Abort (danger) — **NO Back** (nav-layer BackHandler from 27-02 owns system-Back)
  - `Accepted` → Save & Restart (warn, behind ConfirmGuard) + Back
- SAVE_CONFIG `ConfirmGuard(warn=true)` preserved (T-27-04-02).
- `gutter = null`; zero raw MaterialSymbol.

New strings: `calibration_home_all`, `calibration_start`, `calibration_accept`, `calibration_abort`, `calibration_save_config`, `calibration_save_config_confirm`, `probe_starting`.

### CalibrationPreviews + SampleFixtures (Task 3)

- **`CalibrationPreviews.kt`** — 27 `@Preview`/`@Nexus7Previews` annotations (~39 rendered previews):
  - **Hub section**: ProbeCalibrate-selected / BedMesh-selected / QGL-unsupported-greyed matrix; 6 theme combos; fs=L portrait+landscape overflow checks; pseudolocale en-XA
  - **Probe section**: Idle-unhomed / Idle-homed / starting / Active / Accepted matrix; 6 theme combos on Active; fs=L portrait+landscape overflow; pseudolocale en-XA
  - Targets stateless `CalibrationHubContent` and `ProbeCalibrateContent` (no VM, no live Moonraker)
  - Structured with per-screen sections for 27-05/27-06 to extend cleanly
- **`SampleFixtures.calibrationRoutineList`** — 5 `RoutineEntry` values; 3 supported (PROBE_CALIBRATE, BED_MESH, SCREWS_TILT) + 2 greyed (Z_TILT, QUAD_GANTRY_LEVEL), matching the real E3/E5 test printer profile
- **`SampleFixtures.probeVm(state, homedGate)`** — per-state stub `ProbeCalibrateVm` snapshots (Idle/Active/Accepted) with realistic Z values (paper-test 0.050mm gap, typical klicky offset 1.425mm)

## Verification Results

| Check | Result |
|-------|--------|
| `CalibrationHubHolderTest` (all calibration unit tests) | PASS |
| `ProbeCalibrateHolderTest` | PASS |
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| `:app:compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL |
| Zero `RoutineTile` in calibration screens | PASS |
| Zero raw `MaterialSymbol(name=` in calibration screens | PASS |
| D-06 greyed branch `if (entry.isSupported) t.accent2 else t.text3` | PRESENT |
| `starting` verbatim (`probe_calibrate` + `z_endstop_calibrate`) | PRESENT |
| Active foot no-Back (Accept + Abort only) | CONFIRMED |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `intentColor()` missing after CalibrationHubScreen rewrite**
- **Found during:** Task 1 build
- **Issue:** Old `CalibrationHubScreen.kt` contained `internal fun intentColor(intent, t)` used by BedMeshScreen, ScrewsTiltScreen, TiltScreen, ProbeCalibrateScreen (package-internal). The rewrite removed it → `Unresolved reference 'intentColor'` in 4 files.
- **Fix:** Added `intentColor()` back to the new CalibrationHubScreen.kt as the same package-internal utility.
- **Files modified:** `CalibrationHubScreen.kt`
- **Commit:** `9b15ef0`

None beyond the auto-fix above. Plan executed exactly as written otherwise.

## Known Stubs

None — all data flows are wired. Fixtures in `SampleFixtures` are intentional preview-only stubs (documented as such).

## Threat Flags

No new security-relevant surface introduced. T-27-04-01 (system Back during Active probe) mitigated: Active FootButtonBar has no Back button; nav-layer BackHandler (27-02) covers system-Back. T-27-04-02 (SAVE_CONFIG restart) mitigated: amber ConfirmGuard preserved.

## Self-Check

Files exist:
- `/mnt/e/claude/personal/github/dinghy-display/app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt` — FOUND
- `/mnt/e/claude/personal/github/dinghy-display/app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt` — FOUND
- `/mnt/e/claude/personal/github/dinghy-display/app/src/main/java/works/mees/dinghy/preview/CalibrationPreviews.kt` — FOUND

Commits exist:
- `9b15ef0` — CalibrationHubScreen rebuild (Task 1) — FOUND
- `67b6abc` — ProbeCalibrateScreen rebuild (Task 2) — FOUND
- `a212635` — CalibrationPreviews + SampleFixtures (Task 3) — FOUND

## Self-Check: PASSED
