---
phase: 27-motion-calibration
plan: 07
subsystem: ui
tags: [android, compose, moonraker, klipper, uat, on-device]

# Dependency graph
requires:
  - phase: 27-motion-calibration
    provides: All six Phase-27 screens rebuilt on the jiib grammar (MoveScreen, CalibrationHub, ProbeCalibrate, BedMesh, ScrewsTilt, Tilt)
provides:
  - On-device owner UAT confirming SC-1/SC-2/SC-5 gates passed on real Adreno-320 hardware (flox)
  - 27-UAT.md with recorded owner sign-off
  - C3 vertical-Z todo closed (D-01 resolved)
affects: [27-motion-calibration phase closure, phase-28-system-settings]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Force `--rerun-tasks` + APK mtime verification before every owner UAT (stale-APK guard)"

key-files:
  created:
    - .planning/phases/27-motion-calibration/27-07-SUMMARY.md
    - .planning/todos/completed/2026-06-12-move-z-vertical-layout-rework.md
  modified:
    - .planning/phases/27-motion-calibration/27-UAT.md

key-decisions:
  - "Blanket owner approval 'approved' treated as covering all SC-1/SC-2/SC-5 items including the vertical Z column (D-01/C3 disposition)"
  - "C3 todo (2026-06-05-move-z-vertical-layout-rework.md) closed via blanket approval — moved to .planning/todos/completed/"

patterns-established: []

requirements-completed: [SC-1, SC-2, SC-3, SC-5]

# Metrics
duration: 5min (continuation close-out of Task 2 verdict recording)
completed: 2026-06-12
---

# Phase 27 Plan 07: On-Device UAT — Motion + Calibration Summary

**All six Phase-27 screens owner-approved on flox (Adreno-320/LineageOS-18.1) — SC-1/SC-2/SC-5 PASS, C3 vertical-Z todo closed**

## Performance

- **Duration:** ~5 min (wrap-up of Task 2 after owner verdict received)
- **Started:** 2026-06-12T05:00:00Z (Task 1 was committed 7a8ea6e; Task 2 verdict received same session)
- **Completed:** 2026-06-12
- **Tasks:** 2 (Task 1: force-rebuild + install + scaffold; Task 2: owner UAT + record)
- **Files modified:** 3

## Accomplishments

- Non-stale debug APK (47 tasks re-executed, mtime after last P27 commit `c882c97`) installed on flox and verified before owner testing
- Owner drove Move (both orientations), CalibrationHub, ProbeCalibrate, BedMesh, ScrewsTilt, and Tilt on a live printer — blanket "approved" with no failures reported
- C3 long-deferred vertical-Z column todo (`2026-06-05-move-z-vertical-layout-rework.md`) closed; moved to completed

## Build Provenance

| Item | Value |
|------|-------|
| Build mode | `:app:assembleDebug --rerun-tasks --no-daemon` |
| Tasks executed | 47 / 47 (all re-run, no UP-TO-DATE cache) |
| APK mtime | 2026-06-12 00:00:12 -0500 |
| Last P27 screen commit | `c882c97` at 2026-06-11 23:57:44 -0500 |
| Non-stale | YES — APK post-dates commit by ~2 min |
| `adb install -r` | Success |
| Device | flox — Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30 |

## Owner UAT Results

**Owner verdict (2026-06-12):** "approved" — blanket approval, no per-screen failures reported.

| SC | What was verified | Result |
|----|------------------|--------|
| SC-1 | Move — portrait + landscape, spatial XY grid, vertical Z column (3-cell), distance stepper, force-move, Disable guard, rotation state | PASSED |
| SC-2a | CalibrationHub — list+Focus, pre-selected row, greyed unsupported, Open pushes, Back pops | PASSED |
| SC-2b | Probe Calibrate — Home All foot, Start foot, Back-suppression while Active, TESTZ Z-nudge column, Accept→amber foot, Save & Restart ConfirmGuard | PASSED |
| SC-2c | Bed Mesh — profile list, tap-select (no immediate load), Apply, SaveName takeover + validation, Remove ConfirmGuard | PASSED |
| SC-2d | Screws Tilt — Run + screw rows with turn instructions | PASSED |
| SC-2e | Z Tilt / QGL — runs if supported, greyed if not | PASSED |
| SC-5 | No regression — homing, jogging, probe/mesh/screws routines intact, no crashes | PASSED |
| Conformance | ≥64dp targets, scrollable-Field swipe does not open drawer, rotation state preservation | PASSED |

## C3 Vertical-Z Todo Closure (D-01)

The todo `2026-06-05-move-z-vertical-layout-rework.md` has been outstanding since Phase 15.2.
Phase 27 implemented the vertical Z column in the MoveScreen rebuild (plans 27-01/27-02).
The owner's blanket approval on 2026-06-12 — covering all SC-1 vertical-Z column checks — closes
this todo. File moved to `.planning/todos/completed/2026-06-12-move-z-vertical-layout-rework.md`.

## Task Commits

1. **Task 1: Force-rebuild + install on flox + scaffold 27-UAT.md** — `7a8ea6e` (chore)
2. **Task 2 wrap-up: UAT results recorded, C3 todo closed** — this commit (docs)

## Files Created/Modified

- `.planning/phases/27-motion-calibration/27-UAT.md` — Updated from `pending` to `passed`; all checklist items marked PASSED per owner blanket approval; C3 disposition recorded as CLOSED
- `.planning/todos/completed/2026-06-12-move-z-vertical-layout-rework.md` — C3 todo moved to completed with resolution metadata
- `.planning/todos/pending/2026-06-05-move-z-vertical-layout-rework.md` — Deleted (moved to completed)

## Decisions Made

- Blanket "approved" owner response treated as covering all checklist items, including the vertical Z column (D-01). The owner was presented the full checklist in the checkpoint; no per-item failures were raised, so blanket coverage is appropriate.
- C3 todo moved to completed immediately on approval — the plan's criteria were "closed iff the owner approves the vertical Z column on-device."

## Deviations from Plan

None — plan executed exactly as written. The C3 todo closure followed the plan's explicit criteria ("closed iff the owner approves the vertical Z column").

## Issues Encountered

None — APK was non-stale (stale-APK trap neutralized by `--rerun-tasks`), install succeeded on first attempt, owner approved with no failures.

## Threat Model Review

| Threat | Mitigation | Status |
|--------|-----------|--------|
| T-27-07-01: Stale APK installed for UAT | `--rerun-tasks` + APK mtime verified > last P27 commit | MITIGATED — 47/47 tasks re-executed |
| T-27-07-02: Live printer motion under owner control | All in-app destructive guards (ConfirmGuard, back-suppression, homed-gating) verified as part of SC-2b/SC-1 | ACCEPTED + VERIFIED |

## Next Phase Readiness

Phase 27 (Motion + Calibration) is complete — all 7 plans executed, all SC gates passed. The phase verifier / orchestrator should close Phase 27 and advance to Phase 28 (System/Settings).

---
*Phase: 27-motion-calibration*
*Completed: 2026-06-12*
