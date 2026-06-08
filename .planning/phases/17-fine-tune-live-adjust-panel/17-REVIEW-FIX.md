---
phase: 17-fine-tune-live-adjust-panel
fixed_at: 2026-06-07T00:00:00Z
review_path: .planning/phases/17-fine-tune-live-adjust-panel/17-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 17: Code Review Fix Report

**Fixed at:** 2026-06-07
**Source review:** .planning/phases/17-fine-tune-live-adjust-panel/17-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (WR-01..WR-04; the 3 Info findings are out of scope)
- Fixed: 4
- Skipped: 0

All four Warnings are one defect class: the 17-07 flip epsilon (`step × 0.1`) is
tighter than the wire builder's display-precision rounding error, so an off-grid
live value armed a `markPending` target the rounded wire value could never reach
within epsilon — degrading the original *permanent* wedge into a *transient* 8 s
busy-lock that only cleared via the timeout backstop. The remediation is a single
mechanism applied centrally: **round the armed `markPending` target to the same
display precision Klipper will echo back**, so `reported == target` exactly and an
on-grid Reset releases on the real flip, not the backstop.

The original permanent-wedge fix (17-07 single-source clamp authority + seq-guarded
timeout backstop) is preserved and NOT regressed — every prior host test still passes
and the on-device-verified at-cap skip-arm behaviour is unchanged (on-grid inputs
round to themselves).

## Build gates

- `:app:assembleDebug --no-daemon` → **BUILD SUCCESSFUL** (1m 9s; only pre-existing,
  unrelated deprecation/always-true warnings).
- `:app:testDebugUnitTest --no-daemon` → **BUILD SUCCESSFUL** (34s; full host unit
  suite green, including the 4 new off-grid regression tests).

Built Windows-side via `E:\Android\gw.bat` from a `/mnt/e` git worktree (the original
`/tmp` worktree could not be reached by the Windows toolchain — cmd rejects the
`\\wsl.localhost\...` UNC path — so the worktree was relocated to `/mnt/e` and the
in-progress edits re-applied via a captured patch before committing).

## Fixed Issues

### WR-03: Min-cruise Reset arms an unclamped `base × 100` target

**Files modified:** `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt`,
`app/src/main/java/works/mees/dinghy/ui/finetune/MotionScreen.kt`
**Commit:** 32ea19c
**Applied fix:** Added `clampMinCruiseRatio(ratio)` to the clamp-authority block
(mirroring `clampScv` et al., bounds `MIN_CRUISE_RATIO_MIN..MAX`). Routed BOTH the
inline `setVelocityLimit` ratio clamp AND the MotionScreen Min-cruise Reset's
`markPending` target through it (`clampMinCruiseRatio(base) * 100`), making the lone
inline-clamped tuner clamp-symmetric with every other Reset — exactly the asymmetry
17-07 was meant to eliminate everywhere.

### WR-01: SCV flip epsilon smaller than the wire's 1dp rounding error

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt`
**Commit:** e7bd9f7
**Status:** fixed: requires human verification (busy-lock state-machine logic change)
**Applied fix:** See central mechanism below (`roundToWirePrecision`). SCV precision = 1dp.

### WR-02: Pressure-advance & smooth-time share the same epsilon-vs-rounding hazard

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt`
**Commit:** e7bd9f7
**Status:** fixed: requires human verification (busy-lock state-machine logic change)
**Applied fix:** Same central mechanism. PA precision = 3dp, smooth-time = 2dp.

**Central mechanism (WR-01/WR-02):** Added a per-tuner `wirePrecisionFor(tuner)` that
traces each `PrinterCommands` `fmt(..., N)` precision (SCV→1, PA→3, smooth→2,
velocity/accel→0, retraction lengths→1, speeds/percents→0; MIN_CRUISE display percent
== wire 2dp-ratio → 0) and a `roundToWirePrecision` helper. `markPending` now rounds
the incoming target through it before the skip-arm comparison and before arming the
`PendingStateFlip`, so the armed target equals the value Klipper echoes back. Verified
(Java `String.format("%.Nf")` vs `roundToInt`) that the two rounding paths agree on the
off-grid values, so `reported == armed target` holds exactly. On-grid inputs round to
themselves — no behaviour change to the existing on-device-verified paths.

### WR-04: `markPending` skip-arm reads raw `printerState.value`, can disagree with the throttled combine

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt`
**Commit:** e7bd9f7
**Status:** fixed: requires human verification (busy-lock state-machine logic change)
**Applied fix:** Took the review's first recommended option — snapshot the last state the
combine collector observed into a private `lastObservedState` field (written inside the
combine lambda, main-confined / same single thread `markPending` runs on) and compare the
skip-arm against that throttled snapshot (falling back to the raw `StateFlow.value` before
the first emission) instead of the racing raw value. Now the skip-arm and `reached()` read
the same source, so a pending flip can't be dropped mid-dispatch while the combine hasn't
re-emitted for an already-on-the-wire command.

## Regression test added

**File:** `app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneHolderTest.kt`
**Commit:** 7b23fb7

Four off-grid-baseline tests (the review explicitly called for this):
`offGridReset_scv_releasesOnFlip_withoutTimeoutBackstop` (SCV 4.05),
`offGridReset_pressureAdvance_releasesOnFlip_withoutTimeoutBackstop` (PA 0.0237),
`offGridReset_smoothTime_releasesOnFlip_withoutTimeoutBackstop` (smooth 0.037),
`offGridReset_minCruise_releasesOnFlip_withoutTimeoutBackstop` (min-cruise 0.123).

Each arms an off-grid Reset target, seeds the printer with the wire-rounded echo, and
asserts the lock releases on the real flip **without** advancing past
`PENDING_FLIP_TIMEOUT_MS`. On the pre-fix code the raw target is armed outside the tight
epsilon and only the timeout backstop clears it — so these tests fail without the
precision fix and pass with it, giving the green suite a recurrence guard.

## Skipped Issues

None — all four in-scope Warnings were fixed.

## Note on commit grouping

WR-01/WR-02 and WR-04 all modify `FineTuneHolder.markPending` with interleaved hunks
(the skip-arm comparison line is touched by both the precision-rounding and the
throttled-state-snapshot changes). A clean hunk-level split would have produced a
non-compiling intermediate commit, so they share one atomic commit (e7bd9f7) that names
all three findings. WR-03 (PrinterCommands + MotionScreen) and the regression tests are
separate commits.

## Human-verification flag

The three FineTuneHolder findings (WR-01/WR-02/WR-04) change the busy-lock state machine.
Host tests cover the deterministic path, but a live mid-print check that off-grid Resets
release promptly (no 8 s dim) and rapid double-taps behave is recommended before this ships.

---

_Fixed: 2026-06-07_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
