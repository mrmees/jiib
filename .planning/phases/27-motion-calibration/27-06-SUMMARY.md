---
phase: 27-motion-calibration
plan: "06"
subsystem: calibration-ui
tags: [restyle, jiib-grammar, preview, d10, screws-tilt, tilt, qgl]
dependency_graph:
  requires: [27-02, 27-05]
  provides: [ScrewsTiltContent-seam, TiltContent-seam, CalibrationPreviews-complete]
  affects: [CalibrationPreviews, SampleFixtures]
tech_stack:
  added: []
  patterns:
    - ListRow screw list with Geist Mono trailing turn instruction
    - TiltVariant enum parameterizing one screen for ZTilt + QGL
    - WARNING-5 stateless Content seam for @Preview isolation
    - FootButtonBar state-adaptive branches (unhomed/running/result)
key_files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/calibration/TiltScreen.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
    - app/src/main/java/works/mees/dinghy/preview/CalibrationPreviews.kt
decisions:
  - "ScrewsTilt Focus spatial visualization (BedScale + BoxWithPoints) preserved as-is (D-10 pure restyle, not converted to list)"
  - "FloatingEStop(visible=false) on both screens — calibration routes are pop-to-root foot-guns, not accessible during printing"
  - "TiltScreen remains ONE parameterized screen for ZTilt + QGL — no split (D-10 confirmed)"
  - "GuidedLoopState.totalScrews > 0 is the result-shown signal for ScrewsTilt FootButtonBar (Run Again vs Run)"
  - "TiltState.Done || TiltState.Failed both trigger Run Again foot branch"
metrics:
  duration: "~45 minutes (continued from prior context)"
  completed: "2026-06-12T04:56:39Z"
  tasks_completed: 3
  tasks_total: 3
  files_changed: 5
  commits: 3
---

# Phase 27 Plan 06: ScrewsTilt + Tilt Restyle + CalibrationPreviews Closure Summary

ScrewsTiltScreen and TiltScreen restyled to jiib grammar with ListBlock/ListRow, FootButtonBar,
and gutter=null; CalibrationPreviews extended with ScrewsTilt + Tilt @Preview matrices, closing
SC-4 (all 6 Phase 27 screens now have @Preview coverage).

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | ScrewsTiltScreen restyle — ListRow list + spatial Focus + FootButtonBar | `8736a92` | ScrewsTiltScreen.kt, strings.xml |
| 2 | TiltScreen restyle — one parameterized ZTilt+QGL screen + FootButtonBar | `9faf3e6` | TiltScreen.kt |
| 3 | CalibrationPreviews ScrewsTilt+Tilt matrices + SampleFixtures | `ea9ae24` | CalibrationPreviews.kt, SampleFixtures.kt |

## What Was Built

### Task 1 — ScrewsTiltScreen
- Thin wrapper `ScrewsTiltScreen` collects VM + inFlight dispatch state, arms/disarms result display
- Stateless seam `ScrewsTiltContent` is the @Preview target (WARNING-5)
- `ScrewListRow` uses `ListBlock` + `ListRow` with Geist Mono trailing turn instruction:
  - Base screw → "base"
  - No result yet → "—"
  - With turn → "HH:MM CW/CCW" (e.g. "01:45 CW")
- Spatial `ScrewsTiltFocus` (BedScale + BoxWithPoints) preserved entirely (D-10 — the spatial layout is the value)
- FootButtonBar branches: `!homedGate → HomeAll` / `running → Running (disabled)` / `totalScrews > 0 → Run Again` / `else → Run`
- `FloatingEStop(visible = false)` — calibration screens are pop-to-root foot-guns
- 3 new strings: `calibration_run`, `calibration_run_again`, `calibration_running`

### Task 2 — TiltScreen
- Single `TiltContent` composable handles both `TiltVariant.ZTilt` and `TiltVariant.Qgl`
- `TiltFocus` renders bed_tilt icon + state headline; icon tint is state-aware (Done → t.go, Failed → t.stop)
- `TiltFieldBody` shows status text + `ZAdjustment` list in Done state (Geist Mono delta values)
- FootButtonBar: `!homedGate → HomeAll` / `running → Running (disabled)` / `Done || Failed → Run Again` / `else → Run`
- `FloatingEStop(visible = false)` — same rationale as ScrewsTilt
- Old `TiltGutterButton` private composable removed (gutter → null; foot is inside field)

### Task 3 — CalibrationPreviews + SampleFixtures
- `screwsTiltIdle` — 4-screw idle snapshot (hasCoords=true, homedGate=true, no turn results)
- `screwsTiltResult` — 4-screw result with CW/CCW turns (front-left=base, rear-right worst "01:45 CW")
- `tiltContent(variant, state, homedGate)` factory — returns TiltVm for any state/variant combination
- ScrewsTilt previews: Idle+Result state matrix (portrait+landscape), 6-theme matrix, fs=L overflow, en-XA
- Tilt previews: all 5 states (Idle-unhomed/homed/Running/Done/Failed) × both variants, 6-theme matrix on ZTilt-Idle, fs=L overflow on Done, en-XA

## Verification Gates

| Gate | Status | Notes |
|------|--------|-------|
| SC-4: assembleDebug (previews compile) | PASS | BUILD SUCCESSFUL 28s |
| SC-5: testDebugUnitTest (no regressions) | PASS | BUILD SUCCESSFUL 21s |
| SC-5: assembleRelease (R8 + minify) | PASS | BUILD SUCCESSFUL 2m 5s |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — both screens wire live VM state; all fixture data in SampleFixtures is fully populated.

## Threat Flags

None — pure restyle (D-10); no new network endpoints, auth paths, or schema changes.

## Self-Check: PASSED

- `ScrewsTiltScreen.kt` — modified (Task 1 restyle complete)
- `TiltScreen.kt` — modified (Task 2 restyle complete)
- `strings.xml` — 3 new strings added
- `CalibrationPreviews.kt` — ScrewsTilt + Tilt sections appended
- `SampleFixtures.kt` — screwsTiltIdle, screwsTiltResult, tiltContent fixtures added
- Commits `8736a92` / `9faf3e6` / `ea9ae24` — all present in git log
