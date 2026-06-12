---
phase: 28-system-settings-cluster
plan: 09
subsystem: ui
tags: [android, compose, jetpack, moonraker, uat, on-device, flox]

# Dependency graph
requires:
  - phase: 28-system-settings-cluster-01..08
    provides: the full Phase-28 migration (restyled cluster, System page, drawer retirement, Theme Editor S/V square, Printers Edit/Delete mode)
provides:
  - "Owner-approved on-device UAT record for the Phase-28 migration (28-UAT.md, status: passed)"
  - "GAP-A resolution: 1U height floor restored on all Phase-28 surfaces (ListRow + TokenTextField dense param deleted)"
  - "LAYOUT.md C6 amendment revoking the 'sub-1U rows' concept"
affects: [28-system-settings-cluster, 29-ship]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "UAT-in-loop gap fix: owner finds gap during on-device walk → executor fixes in-loop → fresh build reinstalled → owner re-verifies before approving"
    - "1U unconditional: ListRow.heightIn(min=uDp) applied unconditionally; dense=true param deleted from ListRow and TokenTextField"

key-files:
  created: []
  modified:
    - .planning/phases/28-system-settings-cluster/28-UAT.md

key-decisions:
  - "GAP-A: All Phase-28 rows must honor the 1U height floor unconditionally — the dense=true param is deleted from ListRow and TokenTextField. Owner ruling: 'All 1U.' (2026-06-12)"
  - "D-11 (fit-one-page at M) softened: Settings scrolls slightly past one page at M/portrait after the 1U floor fix; the owner's All-1U ruling takes precedence. This is an accepted consequence, not a defect."

patterns-established:
  - "Phase-28 UAT loop: gap found → fixed in-loop → fresh build → re-verify → approve. The stale-APK gate is load-bearing here."

requirements-completed: []

# Metrics
duration: 55min
completed: 2026-06-12
---

# Phase 28 Plan 09: On-Device UAT Gate Summary

**Phase-28 migration owner-approved on flox after a 1U-floor gap found and fixed in-loop; 28-UAT.md records 6/6 resolved.**

## Performance

- **Duration:** ~55 min (including gap fix, fresh build, reinstall, re-verify)
- **Started:** 2026-06-12T13:36:00Z (Task 1 pre-gate build)
- **Completed:** 2026-06-12T19:25:49Z
- **Tasks:** 3 (1 auto + 1 checkpoint:human-verify + 1 auto)
- **Files modified:** 1 (28-UAT.md)

## Accomplishments

- Forced-rebuild release APK verified fresh (mtime post-dates last commit by ~4 min) and installed on flox before the UAT walk
- Owner walked all 6 Manual-Only Verification items on flox (Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30) against a live printer
- GAP-A (U-grid abandoned — dense 10dp rows instead of 1U) found mid-walk, fixed in-loop via commits e51254c (ListRow/TokenTextField dense param deleted, heightIn(min=uDp) unconditional) and 89a24b8 (LAYOUT.md C6 amendment + UAT record); fresh build reinstalled and owner re-verified
- Owner approved all 6 items; 28-UAT.md status set to `passed` (6/6 resolved, 0 deferred, 0 failed)
- One accepted consequence documented: Settings scrolls slightly past one page at M in portrait — the owner's own All-1U ruling supersedes the D-11 one-page goal

## Task Commits

1. **Task 1: Verified-fresh release build + green pre-gate + UAT scaffold** - `88e02c2` (chore)
2. **Task 2: Owner on-device UAT walk (checkpoint:human-verify)** — GAP-A found mid-walk
   - `e51254c` (fix: restore 1U height floor on all Phase-28 surfaces — GAP-A)
   - `89a24b8` (docs: amend LAYOUT.md + 28-UI-SPEC + 28-UAT for GAP-A ruling)
3. **Task 3: Persist UAT results to 28-UAT.md** - `f8b6126` (test)

## Files Created/Modified

- `.planning/phases/28-system-settings-cluster/28-UAT.md` — scaffolded (Task 1), GAP-A gap record added (Task 2), all 6 items resolved + overall status set to passed (Task 3)

## Decisions Made

**GAP-A: All 1U.** The UI-SPEC D-09 / CONTEXT D-09 "sub-1U rows" guidance caused `ListRow.dense = true` to be implemented as `Modifier.padding(vertical = 10.dp)` with no `heightIn` floor. On device this meant row heights floated with content — no shared vertical rhythm. Owner ruling mid-UAT: "All 1U." Fixed by:
- Deleting the `dense` param from `ListRow` and `TokenTextField`
- Applying `heightIn(min = uDp)` unconditionally in `ListRow`
- Removing all `dense = true` call-sites in SettingsScreen, PrintersScreen, SystemInformationScreen, SystemPageScreen
- Revoking the "sub-1U rows" concept from `docs/ui_design/LAYOUT.md` C6

**D-11 softened (accepted).** The All-1U ruling makes Settings scroll slightly past one page at M in portrait. Owner accepted this when giving final approval — the ruling takes precedence over the original one-page goal.

## Deviations from Plan

### Auto-fixed Issues (in-loop during Task 2 checkpoint)

**1. [Rule 1 - Bug] GAP-A: 1U height floor abandoned on all Phase-28 surfaces**
- **Found during:** Task 2 (owner on-device UAT walk on flox)
- **Issue:** `ListRow.dense = true` used `Modifier.padding(vertical = 10.dp)` with no `heightIn` floor; rows floated at content height with no shared vertical rhythm — violating the C6 density standard and the unit-grid law
- **Fix:** Deleted `dense` param from `ListRow` and `TokenTextField`; applied `heightIn(min = uDp)` unconditionally; removed all `dense = true` call-sites; added `heightIn(min = uDp)` + `rememberUnitGrid` to `SystemPageScreen.PowerStubRow` and `AboutScreen.DevEnableRow`; amended `docs/ui_design/LAYOUT.md` C6 to revoke "sub-1U rows"
- **Files modified:** `app/src/main/.../ui/components/ListRow.kt`, `app/src/main/.../ui/components/TokenTextField.kt`, `app/src/main/.../ui/screens/SettingsScreen.kt`, `app/src/main/.../ui/screens/PrintersScreen.kt`, `app/src/main/.../ui/screens/SystemInformationScreen.kt`, `app/src/main/.../ui/screens/SystemPageScreen.kt`, `docs/ui_design/LAYOUT.md`, `.planning/phases/28-system-settings-cluster/28-UI-SPEC.md`, `.planning/phases/28-system-settings-cluster/28-UAT.md`
- **Verification:** Fresh release APK built and reinstalled on flox; owner re-verified and approved
- **Committed in:** `e51254c` (fix) + `89a24b8` (docs)

---

**Total deviations:** 1 auto-fixed (Rule 1 — Bug, in-loop during UAT)
**Impact on plan:** The gap was exactly the kind owner judgment catches; the stale-APK prevention gate and the in-loop fix loop worked as designed. No scope creep.

## Issues Encountered

None beyond GAP-A (documented above as a deviation).

## User Setup Required

None.

## Next Phase Readiness

- Phase 28 UAT gate is closed (28-UAT.md status: passed, 6/6 resolved)
- The 1U-floor is now law for all Phase-28 surfaces and codified in LAYOUT.md C6
- Ready for Phase 29 (Ship)

---
*Phase: 28-system-settings-cluster*
*Completed: 2026-06-12*
