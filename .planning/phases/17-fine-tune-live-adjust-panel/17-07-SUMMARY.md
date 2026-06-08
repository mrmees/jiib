---
phase: 17-fine-tune-live-adjust-panel
plan: 07
subsystem: ui-finetune
tags: [fine-tune, busy-lock, clamp-authority, gap-closure, tdd]
requires:
  - "PrinterCommands Fine-Tune clamp bounds (17-02)"
  - "FineTuneHolder state-flip busy lock (17-05)"
provides:
  - "Single PrinterCommands clamp authority (7 pure clamp funcs) reused by builders AND screen markPending sites"
  - "Per-tuner strict-< float flip epsilon (step*0.1) + skip-arm guard + seq-guarded timeout backstop"
affects:
  - "Every Fine-Tune tuner '+'/'-'/reset nudge (Speed/Flow/PA/Smooth/Vel/Accel/SCV + FwRetraction)"
tech-stack:
  added: []
  patterns:
    - "Clamp authority: gcode builders delegate to pure clamp funcs; screens feed the same clamp to markPending"
    - "Monotonic seq token on a StateFlow data class to make equal-content emits distinct + guard a timeout"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/MotionScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/ExtrusionScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FwRetractionScreen.kt
    - app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneHolderTest.kt
    - app/src/test/java/works/mees/dinghy/command/PrinterCommandsTest.kt
decisions:
  - "Per-tuner flip epsilon = step*0.1 (one-TENTH of the step), STRICT '<' — NOT half-step, NOT flat 0.5"
  - "PendingStateFlip.seq is a monotonic arm id excluded from flip-detection (reached/currentFor key on tuner+target only)"
  - "FwRetraction clamps inline against existing *_MIN/*_MAX consts (no four new builder-coupled funcs); build-blind/host-only"
metrics:
  duration: ~25min
  completed: 2026-06-08
  tasks: 2
  files: 8
---

# Phase 17 Plan 07: Fine-Tune Busy-Lock Wedge Fix (GAP 1 / UAT Check 6) Summary

Closed the MAJOR Check-6 wedge: nudging a Fine-Tune tuner that sits at its clamp ceiling
(e.g. Flow 150%, tap '+') no longer wedges the whole group busy/inert until a process restart.
Root cause was an UNCLAMPED `markPending` target arming a state-flip the clamped wire command can
never reach. Fix is single-source clamp authority + a per-tuner strict-`<` float epsilon + a
seq-guarded bounded timeout backstop.

## What shipped

**Task 1 — clamp authority + epsilon + backstop (TDD, commit `5301c83`)**
- `PrinterCommands`: seven pure public clamp funcs (`clampSpeedPct`/`clampFlowPct`/`clampVelocity`/
  `clampAccel`/`clampScv`/`clampPressureAdvance`/`clampSmoothTime`), each a one-liner over the existing
  `*_MIN`/`*_MAX` consts. The `speedFactor`/`flowFactor`/`setVelocityLimit`/`setPressureAdvance` builders
  now delegate to them — output byte-identical (verified at-cap: `flowFactor(151)=="M221 S150"`,
  `speedFactor(310)=="M220 S300"`, `setVelocityLimit(accel=60000.0)` contains `ACCEL=50000`).
- `FineTuneHolder`: replaced the flat `FLIP_TOLERANCE = 0.5` with `toleranceFor(tuner) = step*0.1`
  (one-tenth of each tuner's display-unit step) and a STRICT `<` comparison in BOTH `reached()` and the
  new `markPending` skip-arm guard. Extracted the per-tuner readback into a shared `currentFor()`.
- `PendingStateFlip` gained a monotonic `seq` (pre-incremented per arm) so equal-content arms are
  distinct StateFlow values; `markPending` schedules a seq-guarded `PENDING_FLIP_TIMEOUT_MS` (8s)
  self-clear that fires only if `_pendingStateFlip.value?.seq == armed.seq`. The combine's real-flip
  clear path also cancels the now-moot timer; `clearPending()` cancels it too.
- Regression tests: small-step (PA) in-range nudge arms and holds through a midpoint reading until the
  value EQUALS target; at-cap no-op release (FLOW + small-step PA); unreachable-target timeout release;
  rapid-double-tap stale-seq does NOT cross-clear the newer flip. Plus clamp-authority byte-identical
  builder assertions.

**Task 2 — clamped targets at every call site (commit `f0d96f9`)**
- `MotionScreen` Speed +/-/reset → `clampSpeedPct`.
- `FineTuneShared.VelocityLimitTile.nudge` → `clampVelocityLimitTarget` (VELOCITY/ACCEL/SCV mapped to the
  matching clamp; MIN_CRUISE_RATIO passes through).
- `ExtrusionScreen` Flow/PA/Smooth +/-/reset → matching `PrinterCommands` clamp.
- `FwRetractionScreen.send()` → `clampRetractionTarget` against the existing retraction `*_MIN`/`*_MAX`
  consts. Dispatched args unchanged everywhere (builders re-clamp identically).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Plan's `clampSpeedPct(151) == SPEED_PCT_MAX` example was mathematically wrong**
- **Found during:** Task 1 (RED test failed)
- **Issue:** The plan's behavior block stated `clampSpeedPct(151) == SPEED_PCT_MAX (300)`, but the speed
  range is 25..300, so 151 is in-range and clamps to 151. The literal test assertion failed.
- **Fix:** Asserted `clampSpeedPct(9999) == SPEED_PCT_MAX` and `clampSpeedPct(151) == 151` (correct clamp
  semantics). Function behavior is correct; only the plan's example value was a typo.
- **Files modified:** app/src/test/java/works/mees/dinghy/command/PrinterCommandsTest.kt
- **Commit:** 5301c83

## Build-blind note

The `FwRetractionScreen` path is BUILD-BLIND — neither dev printer (E5/E3) exposes
`firmware_retraction`, so it is host-test/code-reasoned only, NOT on-device verified. It is covered by
the same clamp authority + per-tuner epsilon + timeout backstop as the other tuners.

## Verification

- `:app:testDebugUnitTest` (FineTuneHolderTest + PrinterCommandsTest) GREEN.
- `:app:assembleDebug` + full `:app:testDebugUnitTest` GREEN (no regression; `groupBusy_persists_until_state_flip` unmodified and still passes).
- Greps confirm: 7 clamp defs; `PrinterCommands.clamp` at every Motion/Extrusion/Shared call site;
  `toleranceFor` used in both `reached()` and `markPending` with strict `<`; `FLIP_TOLERANCE` count 0;
  monotonic `flipSeq` + `.seq == armed.seq` timeout guard; zero `=== armed` referential guards.

## Known Stubs

None.

## Self-Check: PASSED
- app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt — FOUND
- app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt — FOUND
- Commit 5301c83 — FOUND
- Commit f0d96f9 — FOUND
