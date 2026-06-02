---
phase: 07-files-print-control-core-print-loop-gate
plan: 05
subsystem: print-status-controls
tags: [print-status, pause-resume, cancel-print, restart-print, files-route, accessibility]
requires:
  - phase: 07-files-print-control-core-print-loop-gate
    provides: 07-01 print command registry and pause_resume state
  - phase: 07-files-print-control-core-print-loop-gate
    provides: 07-04 live Files route
provides:
  - Pure Print Status control model
  - State-confirmed pending clear helper
  - Live pause/resume/cancel/restart/files gutter wiring
  - Graceful cancel long-press plus accessibility action
affects: [print-loop-uat, files-uat, shell-routing]
tech-stack:
  added: []
  patterns: [pure state-to-controls model, registry-backed print controls, accessibility custom action]
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusControlModel.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusControlModelTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
key-decisions:
  - "Print Status gutter state is derived by a pure model before Compose wiring."
  - "Pause/resume/cancel/restart pending labels clear from PrinterState transitions, not command acknowledgement."
  - "Graceful cancel is separate from emergency Stop and is available by long press plus accessibility custom action."
patterns-established:
  - "Terminal restart filename resolution prefers current print_stats filename, then LastJob filename."
  - "Print Status terminal Files action routes through AppShell to Dest.Files."
requirements-completed: [FILE-03, JOB-03, JOB-04, JOB-05]
duration: 8min
completed: 2026-06-02
---

# Phase 07 Plan 05: Print Status Controls Summary

**State-adaptive Print Status gutter for pause, resume, graceful cancel, Files, Restart print, and emergency Stop separation.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-06-02T10:12:00-05:00
- **Completed:** 2026-06-02T10:19:44-05:00
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Added `PrintStatusControlModel` with host tests for printing, paused, terminal, standby, pending labels, and restart filename fallback.
- Added `clearPrintStatusPendingAction()` so visible pending state clears from printer state transitions.
- Replaced inert Pause/Resume placeholders with registry-backed pause/resume actions.
- Added graceful cancel ConfirmGuard (`Cancel print` / `Keep printing`) from long press and accessibility custom action.
- Added Restart ConfirmGuard (`Restart print` / `Not now`) using current filename first, then last-job filename.
- Routed terminal `Files` action through AppShell to `Dest.Files` while preserving emergency Stop.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add pure Print Status control model** - `63025d8` (feat)
2. **Task 2a: Add pending-state clear helper** - `4383771` (feat)
3. **Task 2b: Wire Print Status gutter actions and guards** - `878e72e` (feat)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusControlModel.kt` - Pure state-to-controls model and pending clear helper.
- `app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusControlModelTest.kt` - Host tests for D-13 through D-16, restart fallback, pending labels, and pending clearing.
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` - Model-driven gutter, print-control dispatch, cancel/restart guards, accessibility action.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` - `PrintStatusScreen` Files callback navigates to `Dest.Files`.

## Decisions Made

- Standby keeps the existing inert Tune/Pause/Stop shape and does not expose Restart.
- Terminal states always show `Files / Restart print / Stop`; Restart is disabled without a current or last-job filename.
- Emergency Stop remains the red icon path; graceful cancel is a separate guarded print-cancel path.

## Deviations from Plan

None - plan executed as written.

## Issues Encountered

- A nullable equality edge made an absent pending restart look like `Restarting`; fixed and covered by `terminalStateWithoutAnyFilename_hidesRestart`.

## Verification

`/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.printstatus.PrintStatusControlModelTest --tests works.mees.dinghy.ui.printstatus.LastJobHolderTest :app:compileReleaseKotlin --no-daemon" | tr -d '\r'`

Result: passed, `BUILD SUCCESSFUL in 11s`.

Source grep:

- `rg -n "printPause|printResume|printCancel|printStart|Cancel print|Keep printing|Restart print|Not now|CustomAccessibilityAction|onOpenFiles|Dest.Files|Emergency stop" app/src/main/java/works/mees/dinghy/ui/printstatus app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`
- Result: required registry dispatches, guard copy, Files callback, and accessibility custom action are present.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 07-06 can run the connected-device route-render and print-loop UAT with Files and Print Status controls now live.

## Self-Check: PASSED

- D-13 through D-16 are host-tested in the pure model.
- Graceful cancel and emergency Stop remain distinct.
- Terminal Files and Restart are live and filename-source-tested.
- Pending state clears from reducer-derived printer state.

---
*Phase: 07-files-print-control-core-print-loop-gate*
*Completed: 2026-06-02*
