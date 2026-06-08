---
phase: 17-fine-tune-live-adjust-panel
reviewed: 2026-06-07T00:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/ExtrusionScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/FwRetractionScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/MotionScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
findings:
  critical: 0
  warning: 4
  info: 3
  total: 7
status: issues_found
---

# Phase 17: Code Review Report

**Reviewed:** 2026-06-07
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Reviewed the gap-closure diffs for plans **17-07** (busy-lock wedge fix: single-source clamp
authority + per-tuner flip epsilon + seq-guarded timeout backstop) and **17-08** (ShellNavState
same-dest entry-reset) against base `55b4dc6`.

The architecture of the fix is sound. The original permanent-wedge defect is genuinely closed by
two independent mechanisms: (1) feeding `markPending` the *same* clamp the wire builder applies, so
an at-cap `+` produces a true no-op the skip-arm short-circuits; and (2) a `seq`-guarded 8-second
self-clear backstop that can never be cleared by a stale (lower-seq) timer. The `seq` field is
correctly excluded from flip detection (`reached`/`currentFor` read only `tuner`+`target`). The
ShellNavState refactor cleanly extracts `applyEntryReset` and fixes the same-dest re-entry hole for
Macros / Calibration / FineTune in one place. Concurrency is safe: the holder runs on AppShell's
composition `rememberCoroutineScope()` (main-confined), and `markPending` is invoked from the UI
thread, so `flipSeq`/`timeoutJob` are never touched from two threads.

The remaining concerns are all in the **per-tuner epsilon vs. wire-rounding** interaction. The fix
sized the flip epsilon at `step × 0.1` (an order of magnitude *below* the step) to avoid early
release, but for the three tuners whose wire command rounds to fewer decimal places than the value's
true precision, an **off-grid** live value can make the dispatched (rounded) value land *outside*
that tight epsilon — so the flip is never detected and the group dims for the full 8 s backstop
before self-clearing. This is a real regression risk introduced by tightening the tolerance, but it
degrades the original *permanent* wedge into a *transient* 8 s busy-lock, so it is a WARNING, not a
BLOCKER. Worth fixing before this ships because the backstop was meant for the pathological
never-reached case, not normal off-grid resets.

## Warnings

### WR-01: SCV flip epsilon (0.01) is smaller than the wire's 1dp rounding error (up to 0.05)

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt:211` (`toleranceFor`),
with `PrinterCommands.kt:365` (`setVelocityLimit` formats SCV to **1dp**)
**Issue:** For Square-Corner-Velocity, `SCV_STEP = 0.1` → `toleranceFor = 0.01`. The wire builder
formats SCV with `fmt(clampScv(it), 1)` (one decimal place). If the live `squareCornerVelocity` is
off the 0.1 grid — e.g. a config baseline of `4.05` reached via the **Reset** affordance
(`VelocityLimitTile.onReset → nudge(base)` with `base = vm.baselines.scv`) — then `markPending`
arms `target = clampScv(4.05) = 4.05`, but the wire sends `fmt(4.05, 1) = "4.0"` (or `"4.1"`).
Klipper reports back `4.0`, and `reached` computes `abs(4.0 - 4.05) = 0.05`, which is **not** `<
0.01`. The flip is never observed; the whole group stays busy for the full
`PENDING_FLIP_TIMEOUT_MS = 8_000` ms before the backstop self-clears. The tight epsilon (chosen to
prevent early release) reintroduces a transient version of the very wedge 17-07 set out to kill,
specifically on off-grid resets.
**Fix:** Make the epsilon account for the wire's formatting precision, not just float jitter. Either
arm `markPending` with the *wire-rounded* target (round to the same dp the builder uses) so target
and reported value match exactly, or widen the per-tuner epsilon to `max(step × 0.1, halfUlpOf(dp))`
where `dp` is the format precision (SCV: half of 0.1 = 0.05 → epsilon must exceed 0.05). The cleanest
is to round the `markPending` target through the same `fmt(..., decimals)` the builder applies:
```kotlin
// in VelocityLimitTile.nudge / per-tuner: arm the value the WIRE will actually report,
// not the raw double, so reported == armed target exactly.
markPending(tuner, PrinterCommands.roundedScv(clampVelocityLimitTarget(field, target)))
```

### WR-02: Pressure-advance & smooth-time share the same epsilon-vs-rounding hazard as SCV

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt:213-214`
(`PRESSURE_ADVANCE` epsilon `= PA_STEP × 0.1 = 0.0001`; `SMOOTH_TIME` epsilon `= SMOOTH_STEP × 0.1 =
0.001`), with `PrinterCommands.kt:375-376` (`setPressureAdvance` formats ADVANCE to **3dp**,
SMOOTH_TIME to **2dp**)
**Issue:** Same class of defect as WR-01. PA wire rounds to 3dp (max rounding error 0.0005 >
epsilon 0.0001); smooth-time rounds to 2dp (max error 0.005 > epsilon 0.001). An off-grid live PA
value (a `+0.001` nudge from a 4-or-more-dp config baseline, or a baseline **Reset** to e.g.
`0.0235`) arms a target the 3dp-rounded wire value can't match within 0.0001 → 8 s transient wedge.
On the normal on-grid path (config baseline at the builder's precision, then integer step counts)
this never fires, which is why host tests at 0.041 / 1.001 pass — but a real printer reporting a
higher-precision `pressure_advance` will trip it.
**Fix:** Same as WR-01 — round the `markPending` target to the builder's display precision before
arming, so the armed target equals the value Klipper will report. Add a host test that seeds an
*off-grid* baseline (e.g. PA `0.0237`) and asserts the flip is detected without waiting for the
backstop.

### WR-03: Min-cruise Reset arms an unclamped `base × 100` target, diverging from the wire path

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/MotionScreen.kt:237-243`
**Issue:** Every other tuner's `markPending` target was deliberately routed through a
`PrinterCommands.*` clamp in 17-07 so the armed target equals the wire-clamped value (the whole point
of the fix). The Min-cruise **Reset** branch is the lone exception: it arms
`markPending(MIN_CRUISE, base * 100)` while the wire sends `VelocityLimitArgs(MIN_CRUISE_RATIO, base)`
which the builder clamps to `MIN_CRUISE_RATIO_MIN..MAX` and formats to 2dp. If `base` is out of the
0.0..1.0 range or off the 2dp grid, the armed display target and the reported `minimumCruiseRatio ×
100` diverge by more than the `0.5` epsilon, reproducing the WR-01/WR-02 transient wedge. `base`
comes from config so it is normally in-range, but the inconsistency is exactly the asymmetry 17-07
was meant to eliminate everywhere.
**Fix:** Clamp/round symmetrically: arm
`markPending(MIN_CRUISE, PrinterCommands.clampMinCruiseRatio(base) * 100)` (add the missing
`clampMinCruiseRatio` to the clamp-authority block — it is conspicuously the one Fine-Tune bound
*without* a `clamp*` helper) and round to the 2dp the wire uses.

### WR-04: `markPending` skip-arm reads `store.printerState.value` synchronously and can disagree with the combine's in-flight `state`

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt:156-173`
**Issue:** The skip-arm guard reads the *current* `store.printerState.value` at call time and
compares it to `target`. The `reached`/clear logic, however, runs inside the `combine` lambda over
the **throttled** `store.printerState` flow, which can lag the raw `StateFlow.value`. In the window
where a previous in-flight command has already moved the raw `printerState.value` to `target` but the
throttled combine has not yet re-emitted, a second `+` tap computes `current ≈ target` and **skips
the arm** (`_pendingStateFlip.value = null`), dropping the busy lock even though a dispatch is still
on the wire. The dispatcher's `inFlight` set still keeps `groupBusy` true via the screen-side
`inFlight.isNotEmpty()` term, so this is not a hard wedge — but the holder's `pendingStateFlip`
contract (clear only on real flip / failure / timeout) is subtly violated and the two state sources
can momentarily disagree.
**Fix:** Either read the same throttled value the combine sees (snapshot the last `buildVm` input
state into a private field and compare against that in `markPending`), or document explicitly that
the skip-arm is intentionally racing the raw value and that `inFlight` is the authoritative busy term
in that window. Add a test that taps `+` twice in rapid succession with a stale-but-already-moved raw
state and asserts the lock behaves.

## Info

### IN-01: `clampVelocityLimitTarget` MIN_CRUISE_RATIO branch is dead by construction

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt:45-50`
**Issue:** `clampVelocityLimitTarget` has no `MIN_CRUISE_RATIO` case (falls to `else -> value`), and
the KDoc states the tile is "never used for it." `VelocityLimitTile` is indeed only instantiated for
VELOCITY/ACCEL/SCV in `MotionScreen`. The `else` branch is therefore unreachable for any real field
and exists only for `when`-exhaustiveness over an open `String`. Harmless, but the comment promises a
"pass through unchanged ... per completeness" path that no caller exercises — a future field added to
`VelocityLimitArgs` would silently get no clamp.
**Fix:** Leave as-is, or convert `field` to a sealed/enum type so the `when` is compiler-exhaustive
and a new field forces a clamp decision rather than silently hitting `else`.

### IN-02: FW-Retraction tile names are hardcoded literals, not `stringResource`

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FwRetractionScreen.kt:132, 150, 169, 188,
212`
**Issue:** `"Retract Len"`, `"Unretract Extra"`, `"Retract Spd"`, `"Unretract Spd"`, and the gutter
`"Back"` are inline string literals, diverging from the project's PREVIEW_AND_TOKENS convention
(every new screen ships `stringResource` strings) followed by the sibling Motion/Extrusion screens
(`R.string.cd_finetune_*`, `R.string.common_back`). Not introduced by the 17-07 diff (pre-existing in
this build-blind screen) but lives in a reviewed file.
**Fix:** Extract to `R.string.*` like the other Fine-Tune screens; reuse `R.string.common_back` for
the gutter.

### IN-03: `FwRetractionScreen` duplicates the dispatcher/inFlight/failure boilerplate verbatim across all three Fine-Tune screens

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FwRetractionScreen.kt:55-84`,
`MotionScreen.kt:63-95`, `ExtrusionScreen.kt:65-94`
**Issue:** The dispatcher resolution, `inFlight` collection, `groupBusy` derivation, the
`DispatchEvent.Failure → failureText + holder.clearPending()` collector, and the 4 s
failure-auto-clear `LaunchedEffect` are copy-pasted identically in all three screens. Any future fix
to this busy-lock plumbing (e.g. WR-04) must be applied in three places, and the three already drifted
slightly (FwRetraction lacks the `// a failure clears the state-flip wait` comment Motion has). Code
duplication, not a correctness bug.
**Fix:** Extract a small `rememberFineTuneScreenState(container, holder)` helper returning
`(vm, enabled, failureText, dispatchCommand)` so the three screens share one plumbing path.

---

_Reviewed: 2026-06-07_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
