---
phase: 26-adjustment-screens
plan: 02
subsystem: ui
tags: [compose, finetune, adjuster, flat-list, clamp-authority, busy-lock, jiib-redesign]

# Dependency graph
requires:
  - phase: 26-01
    provides: AdjusterPanel + IncrementPicker components (Wave-1 kit consumed by FineTuneScreen Focus)
  - phase: 17
    provides: FineTuneHolder + FineTuneTuner + FineTuneVm + clamp authority (NOT modified)
provides:
  - FineTuneScreen.kt: single flat-list screen with AdjusterPanel Focus (replaces Hub + 3 group screens)
  - FineTuneParams.kt: 13-entry param descriptors with D-22 clamp-routing nudge function
  - FineTuneScreenNudgeTest.kt: regression proof — every nudge clamps before markPending
  - AppShell route surgery: single NavDest.FineTune → FineTuneScreen (sub-nav dissolved)
  - Deleted: FineTuneHubScreen, ExtrusionScreen, MotionScreen, FwRetractionScreen, FineTuneGroup, FineTuneTile
affects:
  - 26-03 (Temperature screen — uses same flat-list + AdjusterPanel pattern established here)
  - 26-04..26-07 (all use groupColorFor / clampForTuner / nudge pattern)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Flat param-list screen: FineTuneParam descriptor → AdjusterPanel Focus + ListBlock Field (D-01)"
    - "D-22 clamp-routing: clampForTuner(tuner, rawTarget) before markPending, dispatch with rawTarget"
    - "groupColorFor(group, pool): take()-guarded pool indexing, 3 groups → pool indices 0/1/2"
    - "D-08 hide-not-grey: filter requiresFwRetraction rows by vm.hasFwRetraction"
    - "D-04 session memory: selectedTuner remembered; activeStep resets when selection changes"
    - "Two-overload stateless seam: live (holder+container) + stateless (vm) for previews"
    - "gutter=null: FootButtonBar inside field lambda (jiib redesign convention)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt
    - app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneScreenNudgeTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/preview/FineTunePreviews.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt
    - app/src/test/java/works/mees/dinghy/shell/ShellNavStateTest.kt
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
  deleted:
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHubScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/ExtrusionScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/MotionScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FwRetractionScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneGroup.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneTile.kt

key-decisions:
  - "D-22 clamp authority: clampForTuner() centralizes all 13-tuner clamping; nudge() enforces the invariant so FineTuneScreen never calls markPending directly"
  - "D-08 hide-not-grey: FW-retraction rows filtered (not disabled) when !vm.hasFwRetraction — unreachable commands not shown"
  - "D-02 group colors: pool indices 0/1/2 → Extrusion/Motion/FW-retraction; groupColorFor uses take()-guarded pool access"
  - "D-04 session memory: selectedTuner + activeStep in remember{} inside FineTuneContent; first param = fresh-entry fallback"
  - "Sub-nav dissolved: fineTuneGroup var + BackHandler + when(group) block deleted; single flat-list needs no back-stack"
  - "FineTuneShared.kt pruned: VelocityLimitTile + clampVelocityLimitTarget removed (sole consumers deleted); step constants + DASH + fmtValue retained (used by Holder + AdjusterPanel)"
  - "ShellNavStateTest updated: fineTuneGroup test cases removed; noop + resetTransient tests pruned of dead assertions"

patterns-established:
  - "Flat-list screen pattern: FineTuneScreen is the archetype for Temperature (26-03) and Outputs (26-04)"
  - "clampForTuner() is the single-source clamp-routing function for all 13 Fine-Tune tuners"
  - "groupColorFor() with take()-guarded pool: safe against under-sized theme pools"

requirements-completed: []

# Metrics
duration: ~90min (continuation from prior session)
completed: 2026-06-10
---

# Phase 26 Plan 02: Fine-Tune Flat-List Screen Summary

**13-tuner flat-list FineTuneScreen with AdjusterPanel Focus, D-22 clamp-routing nudge invariant, and sub-nav dissolution replacing 4 old screens (Hub+Extrusion+Motion+FwRetraction)**

## Performance

- **Duration:** ~90 min
- **Started:** 2026-06-10T20:00:00Z
- **Completed:** 2026-06-10T22:04:30Z
- **Tasks:** 3/3
- **Files modified:** 10 (6 created, 4 modified, 6 deleted)

## Accomplishments
- `FineTuneParams.kt`: 13-entry param descriptor list (one per `FineTuneTuner`) with `clampForTuner()`, `nudge()`, `groupColorFor()`, `valueForTuner()`, `baselineForTuner()`; D-22 clamp-routing proven by 7 passing unit tests
- `FineTuneScreen.kt`: flat-list screen with AdjusterPanel Focus; D-04 session memory; D-08 hide-not-grey FW-retraction; D-02 pool-color group icons; gutter=null; two-overload stateless seam; FloatingEStop+ConfirmGuard
- AppShell route surgery: `composable<NavDest.FineTune>` now renders a single `FineTuneScreen(holder, container, onBack)` — sub-nav back-stack, Hub, 3 group pages, `fineTuneGroup` var, BackHandler all deleted; 1290 lines of dead code removed

## Task Commits

1. **Task 1: Param descriptors + clamp-routing nudge + RED nudge test** - `b735127` (test/feat)
2. **Task 2: FineTuneScreen flat-list + AdjusterPanel Focus + previews** - `4bd6687` (feat)
3. **Task 3: AppShell route surgery + delete 6 dead files** - `e29396b` (refactor)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt` — 13-entry param descriptors, clampForTuner, nudge, groupColorFor, valueForTuner/baselineForTuner extension fns
- `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt` — flat-list Fine-Tune screen (two overloads)
- `app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneScreenNudgeTest.kt` — 7 nudge-clamp regression tests (all GREEN)
- `app/src/main/java/works/mees/dinghy/preview/FineTunePreviews.kt` — 7-preview matrix (3 variant × 6 theme × fs=L × landscape × isPrinting × RTL × pseudolocale)
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — route surgery: single FineTuneScreen call
- `app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt` — fineTuneGroup field removed; FineTune applyEntryReset branch removed
- `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt` — VelocityLimitTile + clampVelocityLimitTarget removed (no more consumers)
- `app/src/test/java/works/mees/dinghy/shell/ShellNavStateTest.kt` — fineTuneGroup test cases removed

**Deleted (6 files):** FineTuneHubScreen.kt, ExtrusionScreen.kt, MotionScreen.kt, FwRetractionScreen.kt, FineTuneGroup.kt, FineTuneTile.kt

## Decisions Made

- D-22 nudge invariant enforced centrally: `clampForTuner()` in `FineTuneParams.kt` is the single-source clamp function; `nudge()` calls it before `markPending`; the screen never calls `markPending` directly. Proven by `FineTuneScreenNudgeTest`.
- Pool indices 0/1/2 assigned to Extrusion/Motion/FW-retraction respectively for `groupColorFor`.
- `FineTuneShared.kt` pruned of dead helpers (`VelocityLimitTile`, `clampVelocityLimitTarget`) since their sole consumers (the 4 deleted screens) are gone. Step constants + `DASH` + `fmtValue` retained.
- `ShellNavStateTest.kt` updated: removed the `applyEntryReset_finetune_clears_sub_nav` test entirely; updated `applyEntryReset_other_dest_noop` and `resetTransient_clears_transient_state_only` to not assert on the removed `fineTuneGroup` field.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] FineTuneShared.kt had dangling FineTuneTile reference**
- **Found during:** Task 3 (AppShell route surgery)
- **Issue:** `FineTuneShared.kt` contained `VelocityLimitTile` which calls `FineTuneTile(...)` — the deleted file. Would have broken compilation.
- **Fix:** Removed `VelocityLimitTile` and `clampVelocityLimitTarget` from FineTuneShared (dead code — only the deleted screens used them). Removed dead imports (`@Composable`, `Modifier`, `VelocityLimitArgs`, `DinghyIcon`).
- **Files modified:** `FineTuneShared.kt`
- **Verification:** `assembleDebug` + `testDebugUnitTest` PASS
- **Committed in:** `e29396b` (Task 3 commit)

**2. [Rule 1 - Bug] ShellNavStateTest.kt had three test cases referencing the deleted fineTuneGroup field**
- **Found during:** Task 3 (post-deletion grep)
- **Issue:** `applyEntryReset_finetune_clears_sub_nav`, plus assertions inside `applyEntryReset_other_dest_noop` and `resetTransient_clears_transient_state_only`, all referenced `nav.fineTuneGroup` and `FineTuneGroup.MOTION` — would not compile.
- **Fix:** Deleted `applyEntryReset_finetune_clears_sub_nav` test entirely; removed fineTuneGroup set/assert lines from the other two tests.
- **Files modified:** `ShellNavStateTest.kt`
- **Verification:** `testDebugUnitTest` PASS
- **Committed in:** `e29396b` (Task 3 commit)

**3. [Rule 1 - Bug] DinghyIcons.kt comment referenced deleted ExtrusionScreen.kt**
- **Found during:** Task 3 (post-deletion grep)
- **Issue:** Stale comment `Sole consumer: ExtrusionScreen.kt (the part-fan Fine-Tune tile)` — not a compilation failure but a correctness issue in the source-of-truth comment.
- **Fix:** Updated to `Consumer: FineTuneScreen.kt (the part-fan Fine-Tune param, D-03)`.
- **Files modified:** `DinghyIcons.kt`
- **Committed in:** `e29396b` (Task 3 commit)

---

**Total deviations:** 3 auto-fixed (all Rule 1 — dead code / stale references from the 6 deleted files)
**Impact on plan:** All fixes necessary for compilation and source-of-truth accuracy. No scope creep.

## Issues Encountered

- `CommandDispatcher` import in `FineTuneParams.kt` initially resolved to wrong package (`net.CommandDispatcher` vs `command.CommandDispatcher`) — fixed during Task 1.
- `ConfirmGuard` call had wrong parameter order (missing required `title`, `message`, `confirmLabel` params) — fixed during Task 2.
- `SeverityToast` call had wrong argument order — fixed during Task 2.

All three resolved before the Task 2 commit via standard compile-cycle fixes.

## Known Stubs

None — all 13 tuner values wire to live `FineTuneVm` fields; no hardcoded placeholder data flows to the screen.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced. All clamp mitigations from the plan's threat register implemented:
- T-26-02-01: every nudge routes through `clampForTuner()` before `markPending`
- T-26-02-02: P17 busy-lock invariant preserved; regression-tested by `FineTuneScreenNudgeTest`
- T-26-02-03: FW-retraction rows gated by `vm.hasFwRetraction` (hide-not-grey, D-08)

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **26-03 (Temperature):** The flat-list + AdjusterPanel pattern is established here. `FineTuneScreen.kt` is the archetype to follow. `groupColorFor` and `clampForTuner` patterns carry over.
- **On-device UAT (phase UAT gate):** Fine-Tune renders one flat list; params load into the Focus adjuster; FW-retraction hides on printers without firmware retraction; group colors visible; Reset/Reset-All wired. Must be verified on flox before phase close.
- **FineTuneNavTest.kt (androidTest):** The existing instrumented test targets the old Hub+sub-nav design. It compiles (no deleted-symbol imports) but its assertions against `motionLabel`/`extrusionLabel` as Hub entries are now semantically stale. This test should be rewritten for the flat-list design in a future instrumented pass.

---
*Phase: 26-adjustment-screens*
*Completed: 2026-06-10*
