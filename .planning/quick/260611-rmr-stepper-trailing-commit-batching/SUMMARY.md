---
task: 260611-rmr
slug: stepper-trailing-commit-batching
type: quick
status: complete
completed: 2026-06-11
requirements: [QUICK-260611-RMR]
commits:
  - 5a78e04: "test(quick-rmr): RED — TrailingCommitBatcher contract tests (7 failing)"
  - 23bd1ab: "feat(quick-rmr): GREEN — TrailingCommitBatcher trailing-commit debouncer"
  - 43e5c38: "test(quick-rmr): RED — commit-path clamp/burst/off-grid tests (commitTunerValue stub)"
  - 478bf0d: "feat(quick-rmr): GREEN — Fine-Tune trailing-commit wiring (commitTunerValue, busy dim, nudge() deleted)"
  - 72bf397: "feat(quick-rmr): Temperature heater stepper trailing-commit wiring"
  - d670e6d: "docs(quick-rmr): 26.5 flox sitting bookkeeping + todo supersession"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/command/TrailingCommitBatcher.kt
    - app/src/test/java/works/mees/dinghy/command/TrailingCommitBatcherTest.kt
    - .planning/todos/pending/2026-06-11-retire-swipe-up-nav.md
  modified:
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt
    - app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt
    - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
    - app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneScreenNudgeTest.kt
    - .planning/phases/26.5-overnight-hardening-slate-audit-r-packages/OVERNIGHT-REPORT.md
  deleted:
    - .planning/todos/pending/2026-06-10-temperature-adjuster-fire-without-wait.md
---

# Quick Task 260611-rmr: Stepper Trailing-Commit Batching Summary

**Steppers (Fine-Tune + heater) now batch taps locally and dispatch ONE clamped wire command per
~500ms quiet window via a new pure-Kotlin TrailingCommitBatcher — the owner-rejected per-tap
rejection-flash/5s-lockout UX is structurally gone while every 17-07 invariant survives on the
single commit.**

## What Was Done

### Task 1 — TrailingCommitBatcher (TDD: 5a78e04 RED → 23bd1ab GREEN)
Pure-Kotlin per-key trailing-commit debouncer in `works.mees.dinghy.command` (zero
Compose/Android imports, host-testable like CommandDispatcher). tap/cancel/cancelAll/flush/
dispose + `working: StateFlow<PersistentMap<String, Double>>`. Coroutine-delay timers only,
cancel-and-restart per tap; `canCommit` reschedule gate (a commit re-waits one quiet window while
the key's dispatch is in flight — markPending never arms for a guaranteed-rejected dispatch);
post-commit 2s settle retention of the working value (display never snaps back during the echo
window); commit-on-dispose flush. 7/7 contract tests (RED commit shows all 7 failing against
compiling TODO stubs per the wave-0 scaffold law).

### Task 2 — Fine-Tune wiring (TDD: 43e5c38 RED → 478bf0d GREEN)
- `commitTunerValue()` is now the ONLY markPending write path: idempotent re-clamp →
  `markPending(clamped)` → dispatch clamped. `nudge()` DELETED; `nudgeToBaseline` delegates.
- FineTuneScreen: batcher keyed by `FineTuneTuner.name` (the four retraction tuners share a
  dispatch key — tuner keys keep working values independent; canCommit translates tuner →
  dispatch key). Commit lambda resolves `holder.vm.value` + dispatcher AT FIRE TIME (19-09
  stale-closure lesson). Taps clamp per-tap (`clampForTuner`) into the working value — the
  display can never show an un-clamped number (17-07 Check-6). Resets cancel the pending working
  value and commit immediately; Failure → `clearPending()` + `batcher.cancelAll()`;
  `DisposableEffect` commit-on-dispose.
- AdjusterPanel: new `busy: Boolean = false` — −/+ tiles DIM (alpha 0.38) but stay tappable with
  semantics enabled (taps accumulate during the in-flight commit); Reset truly disabled while
  busy. True disablement (R10: no clickable, no ripple) unchanged.
- Tests: 4 clamp tests migrated nudge→commitTunerValue (same seeds/assertions); NEW
  `burst_dispatchesExactlyOnce_withFinalClampedValue` (real CommandDispatcher + batcher: 5 FLOW
  taps → 0 requests during the burst, exactly 1 after the quiet window, pending flip target =
  final clamped 105.0); NEW `offGridCommit_armsWirePrecisionRoundedTarget` (SCV 4.07 → armed
  4.1, WR-01/02 guards the single commit).

### Task 3 — Temperature + bookkeeping (72bf397, d670e6d)
- TemperatureScreen: batcher keyed by sensor name; `onNudgeHeater` is now the TAP handler
  (`clampHeaterTarget` per tap, no dispatch); onCommit dispatches one clamped `setHeater` per
  quiet window; new `onHeaterOff` callback — Off cancels the pending working target then
  dispatches target=0 immediately; working target wins over live for the adjuster display
  (CR-02 live-temp seed composes on top); display-only in-flight collection re-added solely for
  the busy dim (dispatcher still owns dedup — 26.5-03 semantics unchanged); presets/cooldown
  untouched; rejectTicks plumbing unchanged (fallback signal).
- OVERNIGHT-REPORT flox group: 2026-06-11 sitting #3 recorded — R2 doze PASS, R10 drawer swipe
  PASS, R10 disabled-stepper PASS; rejection-flash UX REJECTED by owner with supersession
  pointer to this task (items kept as historical record).
- New deferred todo `2026-06-11-retire-swipe-up-nav.md` (owner remark verbatim, NO ACTION NOW);
  deleted `2026-06-10-temperature-adjuster-fire-without-wait.md` (superseded — same complaint,
  stronger fix).

## Verification

- Full host suite + assembleDebug: **BUILD SUCCESSFUL — 1086 tests, 0 failures** (whole-sourceset
  compile trap cleared; nudge() fully migrated).
- TDD gates real: both RED commits show failing tests (7/7 NotImplementedError; 6/9 commit-path
  failures), both GREEN commits show them passing.
- `CommandDispatcher.kt` and `FineTuneHolder.kt`: **ZERO diff** vs plan baseline (860e94f) —
  400ms debounce, in-flight guard, rejectedKey, and all 17-07 holder machinery verbatim.
- `grep -rn "fun nudge(" app/src/main` → empty; batcher import purity: zero android/androidx.
- Must-have key links present: `commitTunerValue(` wired from FineTuneScreen onCommit;
  `canCommit` gates on `dispatcher.inFlight`; `TrailingCommitBatcher` wired in TemperatureScreen.

## Deviations from Plan

None - plan executed exactly as written. (Two RED-stage notes, both within plan intent: Task 2's
RED used a `TODO()` stub for commitTunerValue and retained `nudge()` until GREEN so the whole
sourceset compiled at every commit; the off-grid test uses 4.07 rather than the plan's
"e.g. 4.05" because 4.05's binary representation rounds ambiguously — 4.07 proves the same
1dp half-up wire rounding deterministically.)

## Known Stubs

None — all wiring is live; no placeholder values or unwired components.

## Next

Owner re-test on flox (NOT a gate of this plan): force-rebuild + APK-mtime check before install
([[dinghy-stale-apk-uat-gate]]). Expect: stepper number follows the thumb instantly, one command
lands ~500ms after the last tap, no lockout; taps during the commit window keep accumulating
(dimmed, not dead); Off/Reset fire immediately.

## Self-Check: PASSED

- TrailingCommitBatcher.kt / TrailingCommitBatcherTest.kt / retire-swipe-up-nav todo: FOUND
- Superseded temperature todo: DELETED (intentional)
- Commits 5a78e04, 23bd1ab, 43e5c38, 478bf0d, 72bf397, d670e6d: FOUND in log
- Working tree clean at completion

## Post-review fixes

Codex post-review of 5a78e04..72bf397 returned 1 HIGH + 3 MEDIUM verdicts; all four were
verified against the real code (all real) and fixed in commit **7a56297**. Full host suite
1090/1090 + assembleDebug green; CommandDispatcher.kt + FineTuneHolder.kt stayed zero-diff.

**1. [HIGH] Heater Off was droppable (TemperatureScreen.kt).** The old wiring
(`batcher.cancel` + bare immediate `dispatch(target=0)`) raced the dispatcher's guards: if the
scheduled nonzero commit had just fired, the same-key S0 was rejected by the in-flight guard —
and verification found a SECOND drop window codex didn't list: after a fast reply clears the
key, an immediate S0 within 400ms of the accepted nonzero dispatch is DEBOUNCE-rejected
(`lastAccepted` keyed on the prior commit). Either way the heater stayed hot with no retry.
Fix: Off routes through `batcher.tap(sensorName, 0.0)` — display shows 0 instantly (working
target wins), the canCommit loop re-waits while in flight, and the 500ms quiet window strictly
exceeds the 400ms debounce, so the S0 lands exactly once. New `TemperatureHeaterOffTest`
(2 tests, real CommandDispatcher + virtual clock) proves both race windows: in-flight commit +
Off → zero attempts while in flight, exactly one TARGET=0 after the key clears; Off inside the
debounce window → still delivered. Tradeoff accepted: an idle-heater Off now lands ≤~500ms
later instead of instantly, with instant visual feedback.

**2. [MEDIUM] commitTunerValue armed markPending with no command on a null dispatcher
(FineTuneParams.kt).** markPending fired before dispatchForTuner's null early-return — an
offline flush armed a pending flip no echo could ever release (8s-backstop dim, the 17-07
wedge class re-opened). Fix: hard early-return BEFORE any state write when `dispatcher == null`.
The 5 commit-asserting tests in FineTuneScreenNudgeTest now build a real test CommandDispatcher
(`newDispatcher()` helper, CommandDispatcherTest pattern); new
`nullDispatcher_commitIsNoOp_neverArmsPending` regression locks the order.

**3. [MEDIUM] remember(dispatcher) re-keyed the batcher on reconnect (both screens).** A
dispatcher emission mid-burst disposed the old batcher (early flush — through a possibly-null
dispatcher during a disconnect→reconnect transition, dropping the values entirely) and replaced
it with an empty one (working values / settle retention lost, display snap-back). Fix:
`remember {}` unkeyed — verified that EVERY lambda capture (`canCommit`, `onCommit`, both
screens) reads `dispatcher` through the local `by` state delegate, so each invocation already
resolves the CURRENT dispatcher at fire time; no `rememberUpdatedState` needed (no non-delegate
snapshot captures exist). `DisposableEffect(batcher)` now fires exactly once, on screen exit.

**4. [MEDIUM] Tap-time working values were clamped but not wire-precision-rounded
(FineTuneScreen.kt).** The commit path rounds (holder's WR-01/02 `roundToWirePrecision`), the
tap path didn't — an off-grid live baseline (e.g. SCV 4.07) displayed a working value that
differed from what wired (4.1). Fix: new public `canonicalTunerValue(tuner, raw)` in
FineTuneParams.kt = `clampForTuner` + half-up rounding to a `wireDecimalsForTuner` table, applied
at BOTH `batcher.tap` time and inside `commitTunerValue` — display == armed target == wire,
always. The table necessarily DUPLICATES the holder's private `wirePrecisionFor` (the holder is
rail-frozen); drift guard = the extended off-grid test asserting tap-time canonical value ==
armed pending target with delta 0.0, plus a per-precision-class spec test (covering
RETRACT_LENGTH, whose display decimals (2) ≠ wire precision (1dp) — why `FineTuneParam.decimals`
could not be reused).

Test deltas: FineTuneScreenNudgeTest 9 → 11 tests; TemperatureHeaterOffTest +2 (new file);
TrailingCommitBatcher itself needed NO changes — all four fixes were call-site/ordering bugs.
