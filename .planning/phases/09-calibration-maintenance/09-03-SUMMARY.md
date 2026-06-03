---
phase: 09-calibration-maintenance
plan: 03
subsystem: calibration
tags: [kotlin, kotlinx-serialization, pure-parser, screws-tilt, bed-mesh, z-tilt, manual-probe, capability-gate, fixtures]

# Dependency graph
requires:
  - phase: 09-01
    provides: five REAL E5 *_e5.json fixtures + seven compile-fail RED parser scaffolds
  - phase: 09-02
    provides: calibration spine — ScrewsTiltObject/BedMeshObject/ScrewConfig models, Capabilities.hasObject, PrintState, live objects in PrinterState
provides:
  - parseScrewsTilt + GuidedLoopState/ScrewTurn — worst-screw (max |z|) + "X of N in tol" + 1-based screw→config-name join, clock-string adjust verbatim
  - BedMeshModel.from + MeshPoint — meshMatrix/probedMatrix, [x,y]-array min/max, profile-key list, empty-state SEPARATE from saved profiles
  - tiltState + TiltState — pure Z-tilt/QGL convergence machine (Failed only from RpcError)
  - parseZPosition + ZPositionBracket + manualProbeActive — ?????? bounds → null, is_active page-state
  - probeCalibrateGate — PROBE_CALIBRATE vs Z_ENDSTOP_CALIBRATE (A3)
  - CalibrationRoutine + calibrationSupported + calibrationSupport/RoutineEntry — hub gating, all 5 rendered supported-first
  - deleteAllowed — D-15 narrowed delete gate (only the printing file undeletable mid-print)
affects: [09-04, 09-05, 09-06, 09-07]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure fixture-fed parser (GcodeStoreParse discipline): runCatching/getOrDefault/mapNotNull, null-returning walks, no I/O/Compose/coroutine/!!"
    - "Result displays source the STRUCTURED live object (RESEARCH Pattern 1); console parse reserved for the in-progress feed (screen plans)"
    - "tiltState mirrors the TopRoute.derive() when-based pure state-machine idiom"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/calibration/ScrewsTiltResult.kt
    - app/src/main/java/works/mees/dinghy/calibration/BedMeshModel.kt
    - app/src/main/java/works/mees/dinghy/calibration/TiltResult.kt
    - app/src/main/java/works/mees/dinghy/calibration/ManualProbeState.kt
    - app/src/main/java/works/mees/dinghy/calibration/ProbePresentGate.kt
    - app/src/main/java/works/mees/dinghy/calibration/CalibrationGate.kt
    - app/src/main/java/works/mees/dinghy/ui/files/DeleteGate.kt
  modified: []

key-decisions:
  - "Worst screw ranks by max |probed z| (deviation from the base reference plane), NOT by the adjust-clock magnitude — the test fixture's worst non-base z (screw3, 0.195) carries a SMALLER clock (00:06) than screw2 (00:07/z 0.047), so adjust-rank would pick the wrong screw. adjust/sign are still carried + displayed verbatim (Klipper's pre-computed turn instruction)."
  - "In-tolerance = |z| ≤ 0.05 mm (Klipper exposes no per-screw boolean); base always in tol. Drives only the 'X of N' readout; worst screw surfaces regardless."
  - "clockToSeconds parses HH:MM as full-turns*60+minutes (RESEARCH line 282); clockToDegrees = minutes*6 for the UI-SPEC second column."
  - "deleteAllowed uses a plain relative-path == match (no normalization) so a gcodes/-prefixed selection legitimately fails to match the bare active filename — the path-form regression guard (T-09-03-02)."
  - "Implemented to the TEST contract (manualProbeActive, not the plan-prose manualProbePageState; calibrationSupported(caps, routine) Boolean) — the RED scaffolds are the law."

patterns-established:
  - "Phase-8 set-aside-RED-then-restore: Task 2's four RED scaffolds were moved aside to GREEN Task 1 in isolation (the whole test module compiles together), then restored byte-identical for the combined run."

requirements-completed: [CALIB-01, CALIB-02, CALIB-03, CALIB-04, CALIB-05, CALIB-06]

# Metrics
duration: 22min
completed: 2026-06-03
---

# Phase 9 Plan 03: Calibration Result Parsers & Gating Predicates Summary

**Seven pure, fixture-fed calibration functions — five result parsers (screws-tilt worst-screw, bed-mesh heatmap model, Z-tilt/QGL convergence, manual-probe Z-bracket) + two gating predicates (probe-present, calibration hub) + the D-15 delete gate — all GREEN over the REAL Ender-5 *_e5.json fixtures.**

## Performance

- **Duration:** ~22 min
- **Completed:** 2026-06-03
- **Tasks:** 2
- **Files created:** 7

## Accomplishments
- `parseScrewsTilt` → `GuidedLoopState`: worst non-base screw by max |z|, "X of N in tolerance", 1-based `screwN` → config `screwN_name` join, `adjust` clock-string carried verbatim with `clockToSeconds`/`clockToDegrees` helpers. 3-screw AND 4-screw inputs both pass.
- `BedMeshModel.from`: interpolated + probed matrices (array-of-arrays), `mesh_min`/`mesh_max` as `[x,y]` `MeshPoint` arrays, `profileNames` from the `profiles` dict KEYS, `isEmpty` (empty mesh OR blank `profile_name`) SEPARATE from saved-profile non-emptiness.
- `tiltState`: pure `{Idle,Running,Done,Failed}` machine — `Failed` ONLY from an RpcError, never inferred from `applied==false` (Pitfall 2); shared by Z-tilt AND QGL.
- `parseZPosition`: `// Z position: a --> b <-- c` parse where the literal `??????` bounds become `null` (never a fabricated float); malformed/no-current line → `null`. `manualProbeActive` reads `manual_probe.is_active`.
- `probeCalibrateGate` (A3), `calibrationSupported`/`calibrationSupport` (5 routines, supported-first, all rendered per D-14), and `deleteAllowed` (D-15 narrowed: only the `print_stats.filename`-matching file undeletable mid-print).
- All seven Wave-0 RED scaffolds GREEN; full `:app:testReleaseUnitTest` green (no regressions).

## Task Commits

1. **Task 1: Screws-tilt, bed-mesh, and tilt parsers** - `972cca0` (feat)
2. **Task 2: Manual-probe parse + probe/calibration/delete gates** - `4df782c` (feat)

_Both tasks are `tdd="true"`; the RED scaffolds already existed from 09-01, so each task is a single GREEN feat commit (no new test commit)._

## Files Created/Modified
- `app/.../calibration/ScrewsTiltResult.kt` - `parseScrewsTilt` + `GuidedLoopState`/`ScrewTurn` + clock helpers
- `app/.../calibration/BedMeshModel.kt` - `BedMeshModel.from` + `MeshPoint`
- `app/.../calibration/TiltResult.kt` - `tiltState` + `TiltState`
- `app/.../calibration/ManualProbeState.kt` - `parseZPosition` + `ZPositionBracket` + `manualProbeActive`
- `app/.../calibration/ProbePresentGate.kt` - `probeCalibrateGate`
- `app/.../calibration/CalibrationGate.kt` - `CalibrationRoutine` + `calibrationSupported` + `calibrationSupport`/`RoutineEntry`
- `app/.../ui/files/DeleteGate.kt` - `deleteAllowed` (D-15)

## Decisions Made
- **Worst screw = max |z|, NOT max adjust-clock.** The real fixture proves it: screw3 (z=0.195, "00:06") is the asserted worst, but screw2 (z=0.047, "00:07") carries a *larger* clock. Ranking by adjust would pick screw2 and fail the test. The probed-Z deviation from the base plane is the physical "needs the biggest correction" signal; `adjust`/`sign` are Klipper's already-computed turn instruction, displayed verbatim (RESEARCH "Don't Hand-Roll" — read `adjust`/`sign`, don't recompute). The `adjustIsParsedAsClockString` test only asserts the clock parses to >0 seconds, which `clockToSeconds` satisfies independently.
- **In-tolerance threshold = |z| ≤ 0.05 mm** (Klipper exposes no per-screw boolean). Drives only the "X of N" count; the worst screw surfaces regardless.
- **`deleteAllowed` does a plain relative-path `==`** so a `gcodes/`-prefixed selection legitimately fails to match the bare active filename — that IS the path-form guard the scaffold pins.
- **Implemented to the test scaffold's exact API** (`manualProbeActive` not the plan-prose `manualProbePageState`; `calibrationSupported(caps, routine): Boolean`). The RED scaffolds are the contract.

## Deviations from Plan
None - plan executed exactly as written. (The worst-screw-by-|z| and in-tolerance-threshold choices are within "Claude's Discretion" / the test contract, not departures from a specified algorithm — the plan/RESEARCH says "worst" without pinning the metric, and the fixture's asserted answer disambiguates it.)

## Issues Encountered
- **Test module compiles as a unit.** Task 2's four RED scaffolds reference symbols not yet built, so the test compile failed when running Task 1's tests in isolation. Resolved with the recorded Phase-8 pattern: moved Task 2's four test files aside, GREENed + committed Task 1, restored them byte-identical (git-clean), then implemented + verified Task 2. No production code was hidden or stubbed.

## Known Stubs
None — every function is a complete, fixture-verified implementation. The parsers are consumed by the 09-04..09-07 screen plans (holders + Compose/Views surfaces), which are explicitly out of scope for this pure-logic plan.

## User Setup Required
None.

## Next Phase Readiness
- All seven parsers/predicates are live, pure, and host-tested against real hardware fixtures — the bug-class-killing layer the 09-04 (Z-tilt/QGL screen), 09-05 (bed-mesh heatmap), 09-06 (manual-probe page + delete-gate wiring), and 09-07 (calibration hub) plans render on top of.
- `BEDM-01`/`BEDL-01`/`ZCAL-01` umbrella reqs remain Pending/In-Progress (closed only at Phase-9 on-device UAT, per prior-wave context #4) — NOT marked complete here.

## Self-Check: PASSED

All 7 created source files + SUMMARY.md present on disk; both task commits (`972cca0`, `4df782c`) found in git log.

---
*Phase: 09-calibration-maintenance*
*Completed: 2026-06-03*
