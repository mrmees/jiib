---
task: 260611-rmr
slug: stepper-trailing-commit-batching
type: quick
autonomous: true
requirements: [QUICK-260611-RMR]
files_modified:
  - app/src/main/java/works/mees/dinghy/command/TrailingCommitBatcher.kt
  - app/src/test/java/works/mees/dinghy/command/TrailingCommitBatcherTest.kt
  - app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
  - app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneScreenNudgeTest.kt
  - .planning/phases/26.5-overnight-hardening-slate-audit-r-packages/OVERNIGHT-REPORT.md
  - .planning/todos/pending/2026-06-11-retire-swipe-up-nav.md
  - .planning/todos/pending/2026-06-10-temperature-adjuster-fire-without-wait.md

must_haves:
  truths:
    - "Rapid +/− taps on a Fine-Tune or heater stepper update the on-screen value INSTANTLY and locally; the stepper never locks out during a tap burst"
    - "The displayed working value is clamped per-tap through the PrinterCommands clamp authority — the screen can never show an un-clamped value (17-07 Check-6 lesson)"
    - "After ~500ms with no taps, exactly ONE wire command dispatches carrying the final working value; markPending arms the clamped + wire-precision-rounded target at that single commit (all 17-07 invariants intact)"
    - "Taps landing DURING the in-flight commit accumulate into a NEW working value that commits after the next quiet window — no rejection, no 5s lockout"
    - "Resets (nudgeToBaseline / Reset All / heater Off) commit immediately and cancel any pending working value for that key"
    - "Leaving the screen mid-burst COMMITS the pending working value (commit-on-dispose) — a same-frame nav never silently drops the adjustment"
    - "CommandDispatcher is byte-identical (400ms debounce, in-flight guard, rejectedKey all untouched); the rejection flash stays wired as the fallback signal"
  artifacts:
    - path: "app/src/main/java/works/mees/dinghy/command/TrailingCommitBatcher.kt"
      provides: "Pure-Kotlin per-key trailing-commit debouncer (tap/cancel/flush/dispose, working-value StateFlow, canCommit reschedule gate)"
    - path: "app/src/test/java/works/mees/dinghy/command/TrailingCommitBatcherTest.kt"
      provides: "Host tests: N-tap burst → exactly 1 commit with final value; window restart; tap-during-commit → second commit; flush-on-dispose; cancel"
    - path: "app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt"
      provides: "commitTunerValue() (clamp → markPending → dispatch, the single commit-time write path); nudge() deleted"
  key_links:
    - from: "app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt"
      to: "TrailingCommitBatcher.onCommit → commitTunerValue"
      via: "commit lambda resolving holder.vm.value + dispatcher AT FIRE TIME (no stale composition capture — 19-09 lesson)"
      pattern: "commitTunerValue\\("
    - from: "app/src/main/java/works/mees/dinghy/command/TrailingCommitBatcher.kt"
      to: "CommandDispatcher.inFlight"
      via: "canCommit gate — commit reschedules while the key is in-flight, so markPending never arms for a guaranteed-rejected dispatch"
      pattern: "canCommit"
    - from: "app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt"
      to: "TrailingCommitBatcher"
      via: "heater stepper taps → clampHeaterTarget per-tap → batcher.tap(sensorName, …); commit dispatches setHeater once"
      pattern: "TrailingCommitBatcher"
---

<objective>
Switch the AdjusterPanel-driven steppers (Fine-Tune params + Temperature heater target) from
dispatch-per-tap to TRAILING-COMMIT BATCHING: rapid +/− taps update the on-screen working value
instantly and locally (clamped per-tap), and the wire command dispatches ONCE after ~500ms of no
taps, sending the final working value.

Owner UAT redirect (flox, 2026-06-11, he is AT the device): "the rejection flash is broken...
after tapping a few times, the number flashes yellow for a split second, then the controls lock
out for about 5 seconds... batch commands by waiting for a user to not press for half a second
before sending the current value." The per-tap dispatch model means tap #2 of a burst hits the
dispatcher's 400ms debounce / in-flight guard → rejection flash → markPending busy-dim until the
echo + skip-arm churn settles. Batching makes rejections structurally rare instead of trying to
protect every micro-tap.

Purpose: multi-tap a stepper freely; the number follows your thumb; one command lands.
Output: TrailingCommitBatcher + wiring in both screens, migrated/new host tests, green build
gate, 26.5 UAT bookkeeping.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt
@app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt
@app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt
@app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
@app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt
@app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt
@app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneScreenNudgeTest.kt
@app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneHolderTest.kt
@.planning/phases/26.5-overnight-hardening-slate-audit-r-packages/26.5-03-SUMMARY.md

Verified facts (read during planning — trust these, re-verify only if the code moved):

- `CommandDispatcher` (DO NOT MODIFY): 400ms `DEFAULT_DEBOUNCE_MS` keyed on last ACCEPTED
  dispatch; atomic in-flight guard; both early returns emit `rejectedKey` (26.5-03). The key
  removes itself in `finally` always — an in-flight key ALWAYS clears eventually.
- `FineTuneParams.nudge()` (line 342) is the ONLY per-tap write path: clampForTuner →
  holder.markPending(clamped) → dispatchForTuner(raw). `nudgeToBaseline()` (line 362) is the
  same shape with an absolute target. Only FineTuneScreen.kt calls them in prod;
  FineTuneScreenNudgeTest.kt calls `nudge()` in 4 clamp tests.
- `FineTuneHolder` is UNTOUCHED by this task: markPending already does wire-precision rounding
  (WR-01/02), skip-arm vs the throttled snapshot (WR-04), per-tuner step×0.1 strict-< epsilon,
  and the seq-guarded 8s timeout backstop. The whole 17-07 machinery survives verbatim — only
  the CALL FREQUENCY changes (once per commit instead of once per tap).
- FineTune busy: `groupBusy = inFlight.isNotEmpty() || holder.pendingStateFlip != null ||
  vm.groupBusy`, currently passed as `enabled = !groupBusy` into AdjusterPanel — this is the 5s
  lockout the owner is rejecting (markPending fires per tap, so a burst arms flip after flip).
  Once markPending only fires at commit, groupBusy is naturally confined to the commit+echo
  window.
- Temperature heater: `onNudgeHeater` clamps via `PrinterCommands.clampHeaterTarget(Int)` and
  dispatches per tap with key `heaterDispatchKey(name)` = `"set_heater_$name"`. The 26.5-03
  deviation removed the local in-flight pre-check (dispatcher owns dedup). The live overload no
  longer collects `inFlight`.
- `AdjusterPanel`: `enabled=false` or `value==null` → TRUE disablement (no clickable, no ripple,
  semantics disabled — R10 law). `rejectTick` one-shot flash stays as-is. Call sites: FineTune,
  Temperature, AdjusterPreviews only (grep-verified) — a new defaulted param breaks nothing.
- `dispatchKeyForTuner()`: PA and SMOOTH_TIME have distinct keys; the FOUR retraction tuners
  SHARE one dispatch key. So batcher keys must be the TUNER name (display identity), not the
  dispatch key; canCommit maps tuner → dispatch key.
- Stale-closure lesson (19-09): lambdas captured at composition go stale — the commit lambda
  must read `holder.vm.value` and the live dispatcher at FIRE time.
- Tests landscape: FineTuneHolderTest (19 tests) — UNCHANGED. CommandDispatcherTest — UNCHANGED.
  TemperatureHolderTest/TraceStyle/GraphYRange — UNCHANGED (no heater-dispatch tests exist).
  FineTuneScreenNudgeTest — 4 clamp tests MIGRATE to the commit path; 3 structural tests stay.
- Build: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"` from repo root, pipe
  `tr -d '\r'`, exit code authoritative. The whole test sourceset compiles before any --tests
  filter — deleting `nudge()` without migrating its tests bricks every test run.

DESIGN DECISIONS (locked by this plan):

1. **Dispose behavior = COMMIT-on-dispose.** Justification: the user expressed intent by
   tapping; silently dropping the adjustment on a same-frame nav is exactly the
   write-scope-cancellation bug class this project has been burned by twice
   ([[dinghy-compose-write-scope-cancellation]]). The flush happens synchronously in
   `onDispose`; the actual network call rides `CommandDispatcher`'s app-lifetime scope, so it
   completes even though the screen (and the batcher's own scope) is gone. Cancel-and-revert
   would also lie to the user: the display showed a value that never got sent.
2. **Busy-dim ≠ disable during the in-flight commit.** The owner requires taps DURING the
   in-flight commit to accumulate — so the −/+ tiles must stay tappable. Reconciliation of the
   two constraints: AdjusterPanel gains `busy: Boolean = false` which DIMS the −/+ tiles
   (alpha 0.38) but keeps them clickable and keeps semantics enabled; only Reset is truly
   disabled while busy (a deliberate single action shouldn't stack on an in-flight commit).
   True disablement (`enabled=false` / `value==null` → no clickable, no ripple) is unchanged.
3. **Resets commit immediately** (recommendation accepted): nudgeToBaseline, Reset All, and the
   heater Off button are single deliberate taps — they cancel any pending working value for
   their key(s) and dispatch through the existing immediate path.
4. **Commit reschedules while in-flight** (canCommit gate): if the timer fires while the key's
   dispatch is still in flight, re-wait one quiet window instead of dispatching into a
   guaranteed rejection — this is what keeps markPending from arming a target whose dispatch
   the dispatcher would drop (which would re-create the 8s-backstop dim). Because the 500ms
   quiet window exceeds the 400ms debounce, a rescheduled commit can never hit the debounce
   either. Rejections become structurally rare; rejectedKey flash stays wired as fallback.
5. **Working value retained ~2s after commit, then auto-cleared** (unless a new tap re-arms):
   prevents the display snapping back to the stale live value during the echo window; the
   swap to the live echo is invisible in the normal case (echo lands well under 2s).
6. The pre-existing pending todo `2026-06-10-temperature-adjuster-fire-without-wait.md` is
   SUPERSEDED by this implementation (same owner complaint, stronger fix) — delete it in Task 3.
</context>

<tasks>

<task type="tdd">
  <name>Task 1: TrailingCommitBatcher — pure host-tested trailing-commit debouncer</name>
  <files>app/src/main/java/works/mees/dinghy/command/TrailingCommitBatcher.kt, app/src/test/java/works/mees/dinghy/command/TrailingCommitBatcherTest.kt</files>
  <behavior>
    All tests in TrailingCommitBatcherTest, written RED first against the not-yet-implemented
    class (typed assertions, compiling stubs — [[dinghy-wave0-red-scaffold-compile]]):
    - burst_commitsOnceWithFinalValue: 5 taps on one key inside 500ms (e.g. at 0/100/200/300/400ms)
      → ZERO commits during the burst; advance past lastTap+500ms → exactly ONE onCommit(key, finalValue).
    - tapRestartsQuietWindow: tap at 0ms, tap at 400ms → no commit at 500ms; commit fires at 900ms.
    - tapDuringInFlightCommit_producesSecondCommit: commit 1 fires; canCommit flips false
      (simulating in-flight); new tap; quiet window elapses while canCommit false → NO dispatch;
      canCommit returns true → next scheduled check commits ONCE with the new value (2 commits total,
      values in order).
    - flushOnDispose_commitsPendingImmediately: tap, then dispose() before the window elapses →
      exactly one commit with the working value, fired synchronously from dispose.
    - cancelDropsWorking: tap, cancel(key) → no commit ever; working map no longer contains key.
    - independentKeys: taps on two keys interleaved → each commits its own final value once.
    - workingAutoClearsAfterSettle: after a commit, working[key] survives for the settle window
      (display retention) then clears; a new tap during the settle window re-arms instead.
  </behavior>
  <action>
    Create `TrailingCommitBatcher` in package `works.mees.dinghy.command` (plain Kotlin, ZERO
    Compose/Android imports — host-testable like CommandDispatcher). Shape:

    - Constructor: `scope: CoroutineScope` (production callers pass an internally-created
      `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` — provide a secondary
      no-scope constructor or factory for that; tests inject a TestScope),
      `quietMs: Long = QUIET_WINDOW_MS` (500L), `settleMs: Long = SETTLE_CLEAR_MS` (2_000L),
      `canCommit: (String) -> Boolean = { true }`,
      `onCommit: (key: String, value: Double) -> Unit`.
    - `val working: StateFlow<PersistentMap<String, Double>>` — kotlinx immutable PersistentMap
      (the @Stable Compose-param house pattern from rejectTicks, Phase-22 discipline).
    - `fun tap(key: String, value: Double)` — caller passes the ALREADY-CLAMPED new working
      value (clamp authority stays at the call site, this class knows nothing about limits —
      same division of labor as AdjusterPanel's KDoc). Stores working[key], cancels that key's
      pending timer Job AND any settle-clear Job, launches a new timer: `delay(quietMs)` →
      if `!canCommit(key)` loop `delay(quietMs)` again (bounded: the dispatcher always clears
      in-flight in `finally`) → fire `onCommit(key, working[key])` → schedule settle-clear:
      `delay(settleMs)` → remove working[key] (skipped/cancelled if a new tap re-armed).
      Timer discipline per the task constraints: coroutine delay only, cancel-and-restart per
      tap, NO Timer/Handler. Use per-key Job maps; cancel before relaunch so no stale timer can
      fire (mirror the holder's timeoutJob hygiene; a per-key seq token is acceptable extra
      belt-and-braces but plain Job cancellation suffices since all access is main-confined).
    - `fun cancel(key: String)` — drop working + cancel jobs for the key (reset supersedes).
    - `fun cancelAll()` — same for every key (dispatch Failure revert).
    - `fun flush()` — for every key with a pending (uncommitted) timer: cancel the timer and
      fire `onCommit` immediately and synchronously (no canCommit wait at flush — accepted rare
      corner: a flush into an in-flight key gets dispatcher-rejected and flashes; document it).
      Keys already committed and merely in settle-retention are NOT re-committed.
    - `fun dispose()` — flush() then cancel the scope's children (the production-owned scope).

    KDoc the class with the commit-flow contract and the two accepted corners (flush-while-in-
    flight; settle-clear racing a slow echo). RED commit first (failing tests), then GREEN.
  </action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.command.TrailingCommitBatcherTest --no-daemon" | tr -d '\r'</automated>
  </verify>
  <done>All batcher tests green; class has zero Compose/Android imports (grep `^import androidx|^import android` returns nothing); RED commit precedes GREEN commit.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Wire Fine-Tune to trailing commit (batcher + commitTunerValue + AdjusterPanel busy param)</name>
  <files>app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt, app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt, app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt, app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneScreenNudgeTest.kt</files>
  <behavior>
    Migrated + new tests in FineTuneScreenNudgeTest (whole sourceset must compile — the 4
    existing clamp tests reference `nudge()` which this task deletes; migrate them IN THE SAME
    commit):
    - velocity_overCap / velocity_atCap / scv_overCap / scv_atCap: same seeds and assertions as
      today, but exercised via `commitTunerValue(param, absoluteOverCapTarget, vm, holder, null)` —
      armed pending.target == clamp ceiling, never above; at-cap skip-arms (groupBusy stays false).
    - burst_dispatchesExactlyOnce_withFinalClampedValue (NEW, the money test): build a real
      `CommandDispatcher` with an injected request-counting lambda + virtual timeSource
      (CommandDispatcherTest pattern) and a `TrailingCommitBatcher` on the test scope whose
      onCommit calls `commitTunerValue` resolving holder.vm.value at fire time. Simulate 5 FLOW
      taps (clampForTuner per tap, batcher.tap per tap); advance past the quiet window → the
      request lambda was invoked EXACTLY ONCE, pending flip target == the final clamped value,
      and zero requests happened during the burst.
    - offGridCommit_armsWirePrecisionRoundedTarget (NEW): commit an off-grid SCV value (e.g.
      4.05) via commitTunerValue → holder.pendingStateFlip.target equals the 1dp-rounded value
      (proves the WR-01/02 rounding still guards the SINGLE commit; the holder's own off-grid
      tests stay untouched as the deeper proof).
  </behavior>
  <action>
    **FineTuneParams.kt:** Delete `nudge()` (no remaining prod callers after this task). Add
    `commitTunerValue(param, target: Double, vm, holder, dispatcher)` = `clampForTuner` (idempotent
    re-clamp — the hard invariant "clamp BEFORE markPending" holds even if a caller forgot) →
    `holder.markPending(tuner, clamped)` → `dispatchForTuner(tuner, clamped, clamped, vm, dispatcher)`.
    Note: pass the CLAMPED value as the wire arg (builders re-clamp identically — same result,
    but commit-time has no meaningful "raw" anymore). `nudgeToBaseline` delegates to
    `commitTunerValue` (identical semantics, keeps the reset call sites readable). Update KDoc:
    commitTunerValue is now "the ONLY call site that writes markPending" law.

    **AdjusterPanel.kt:** Add `busy: Boolean = false` (defaulted — AdjusterPreviews and any
    other call site compile unchanged). Semantics per design decision 2: when busy, the −/+
    tiles get the alpha-0.38 dim WITHOUT `semantics { disabled() }` and WITHOUT enabled=false
    (clickable stays installed — taps accumulate during the commit); Reset gets
    `enabled = controlsEnabled && !busy` plus the dim. True disablement
    (`!enabled || value == null`) unchanged: no clickable, no ripple, semantics disabled.
    Update the class KDoc busy/enabled contract.

    **FineTuneScreen.kt (live overload):**
    - `val batcher = remember(dispatcher) { TrailingCommitBatcher(scope = <production scope>,
      canCommit = { tunerName -> the tuner's dispatchKeyForTuner(...) !in (dispatcher?.inFlight?.value ?: emptySet()) },
      onCommit = { tunerName, value -> commitTunerValue(paramFor(tunerName), value, holder.vm.value, holder, dispatcherAtFireTime) }) }`.
      Batcher KEYS = `FineTuneTuner.name` (the four retraction tuners share a dispatch key —
      keying by tuner keeps their working values independent; canCommit translates tuner→dispatch
      key). The commit lambda MUST resolve `holder.vm.value` and the dispatcher at fire time
      (19-09 stale-closure lesson) — capture `holder` and a stable dispatcher source, never the
      composed `vm`/`dispatcher` snapshots.
    - `DisposableEffect(batcher) { onDispose { batcher.dispose() } }` — commit-on-dispose
      (design decision 1); also covers the remember(dispatcher) re-key on reconnect.
    - Collect `batcher.working` (collectAsStateWithLifecycle); pass into FineTuneContent as
      `working: ImmutableMap<String, Double> = persistentMapOf()`.
    - `onNudge` handler: `val base = working[tuner.name] ?: vm.valueForTuner(tuner) ?: return` →
      `batcher.tap(tuner.name, clampForTuner(tuner, base + stepDelta))`. NO markPending, NO
      dispatch, NO null-base fabrication (unreported value stays a no-op, existing behavior).
    - `onNudgeToBaseline` + `onResetAll`: `batcher.cancel(key)` (cancelAll for Reset All) then
      the existing immediate `nudgeToBaseline` path (design decision 3).
    - Failure collector: keep `holder.clearPending()`, add `batcher.cancelAll()` (revert working
      to live on failure — the SeverityToast already explains why).
    - FineTuneContent: replace `enabled: Boolean` with `busy: Boolean`; AdjusterPanel gets
      `value = working[selectedTuner.name] ?: vm.valueForTuner(selectedTuner)`,
      `enabled = true`, `busy = busy`. Live overload passes `busy = groupBusy` (which now only
      lights during the single commit+echo window, since markPending fires once per commit);
      preview overload passes `busy = vm.groupBusy`. The Field list trailing readouts stay on
      live vm values (they settle within the echo window — acceptable).
    - rejectTicks plumbing: UNCHANGED (fallback signal per the owner's direction).
  </action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.finetune.* --no-daemon" | tr -d '\r'</automated>
  </verify>
  <done>FineTuneHolderTest green UNMODIFIED (holder untouched — `git diff --stat` shows no FineTuneHolder.kt change); migrated clamp tests + both new tests green; `grep -rn "fun nudge(" app/src/main` returns nothing; AdjusterPanel busy contract: dimmed-but-tappable −/+ while busy, Reset disabled while busy.</done>
</task>

<task type="auto">
  <name>Task 3: Wire Temperature heater stepper + 26.5 UAT bookkeeping + full gate</name>
  <files>app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt, .planning/phases/26.5-overnight-hardening-slate-audit-r-packages/OVERNIGHT-REPORT.md, .planning/todos/pending/2026-06-11-retire-swipe-up-nav.md, .planning/todos/pending/2026-06-10-temperature-adjuster-fire-without-wait.md</files>
  <action>
    **TemperatureScreen.kt:**
    - Live overload: `remember(dispatcher) { TrailingCommitBatcher(...) }` keyed by SENSOR NAME;
      `canCommit = { name -> heaterDispatchKey(name) !in (dispatcher?.inFlight?.value ?: emptySet()) }`;
      `onCommit = { name, value -> dispatcher-at-fire-time?.dispatch(CommandRegistry.setHeater,
      SetHeaterArgs(name, PrinterCommands.clampHeaterTarget(value.roundToInt()), heaterDispatchKey(name))) }`.
      `DisposableEffect(batcher) { onDispose { batcher.dispose() } }`. `heaterDispatchKey` needs
      `internal` (currently private) OR keep the batcher construction inside this file where it's
      visible — it is in this file; private stays fine.
    - Re-add a lifecycle-aware `inFlight` collection in the live overload (display-only — this
      does NOT reintroduce the 26.5-03-removed dedup pre-check; the dispatcher still owns dedup).
      Used solely for the busy dim below.
    - Collect `batcher.working` → pass to TemperatureContent as
      `workingTargets: ImmutableMap<String, Double> = persistentMapOf()` (preview seam unchanged
      via the default).
    - In TemperatureContent's adjuster branch: `currentTarget = workingTargets[sensor.name]
      ?: sensor.target` (the CR-02 live-temp seed logic inside TemperatureAdjusterFocus then
      composes naturally on top — working wins when present). Pass
      `busy = heaterDispatchKey-in-inFlight` for the panel dim (plumb a Boolean down; the
      stateless seam defaults it false).
    - Per-tap: repurpose the existing onDecrement/onIncrement path — the live overload's
      `onNudgeHeater(name, rawTarget)` becomes the TAP handler:
      `batcher.tap(name, PrinterCommands.clampHeaterTarget(rawTarget).toDouble())` — clamped
      per-tap, display can never exceed bounds. NO dispatch on tap. (The base the AdjusterFocus
      computes from `currentTarget` already reflects the working value via the param plumb.)
    - Off button (`onOff`): `batcher.cancel(sensor.name)` then the existing immediate
      `dispatch(setHeater, target=0)` — immediate per design decision 3. Same for presets and
      cooldown: untouched (they're not stepper paths).
    - Failure collector: add `batcher.cancelAll()` alongside the existing failureText set.
    - rejectTicks plumbing: UNCHANGED.
    - No existing temperature test exercises the heater dispatch path (grep-verified) — no test
      migration here; the batcher behavior is covered by Task 1, the clamp by the existing
      PrinterCommands tests.

    **Bookkeeping — OVERNIGHT-REPORT.md (flox group):** record the 2026-06-11 flox UAT sitting:
    - `[x]` R2 doze survival — **PASS** (forced deep idle, websocket survived, double-checked).
    - `[x]` R10 drawer swipe — **PASS** (slow deliberate swipe opens reliably).
    - `[x]` R10 disabled stepper = no ripple — **PASS**.
    - R10 tap torture: overall tap feel **improved**, BUT the rejection-flash UX is **REJECTED
      by owner** — after a few taps the value flashes amber then the controls lock out ~5s
      (markPending-per-tap busy churn). Superseded by quick task **260611-rmr**
      (stepper trailing-commit batching) — note inline on the rejection-flash checklist item,
      keep the item itself as historical record with the supersession pointer.
    Match the existing report formatting (the S25 sitting block is the precedent).

    **Deferred idea — create `.planning/todos/pending/2026-06-11-retire-swipe-up-nav.md`**
    (frontmatter format per the existing pending todos: created/title/area/target_phase/files):
    owner remark at the 2026-06-11 flox sitting — "swipe-up nav may be retired entirely in
    favor of the new navigation method." Capture verbatim, target the nav/polish phase
    (28 or 29), explicitly NO ACTION NOW.

    **Delete `.planning/todos/pending/2026-06-10-temperature-adjuster-fire-without-wait.md`** —
    superseded by this task (same owner complaint, stronger fix: trailing-commit batching now
    covers the Temperature adjuster); say so in the commit message.
  </action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon" | tr -d '\r'</automated>
  </verify>
  <done>Full host suite + assembleDebug exit 0 (the whole-sourceset compile trap cleared); CommandDispatcher.kt has ZERO diff (`git diff --stat app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt` empty); OVERNIGHT-REPORT carries the flox results + supersession note; new deferred todo exists; superseded todo gone.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| app → Moonraker gcode | Batched commit sends user-derived numeric targets to the printer |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-rmr-01 | Tampering | TrailingCommitBatcher commit path | mitigate | Per-tap clamp via PrinterCommands authority + idempotent re-clamp inside commitTunerValue/clampHeaterTarget at commit — an un-clamped value can never reach markPending or the wire (17-07 invariant preserved) |
| T-rmr-02 | DoS (printer/SBC flood) | stepper tap burst | mitigate | Batching REDUCES wire traffic (N taps → 1 command); CommandDispatcher's debounce + in-flight guard untouched as backstop (T-26.5-07 unchanged) |
| T-rmr-03 | Repudiation (lost adjustment) | dispose mid-burst | mitigate | Commit-on-dispose flush; dispatch rides CommandDispatcher's app-lifetime scope |
</threat_model>

<verification>
- Whole host suite + assembleDebug green via gw.bat (exit 0 after `tr -d '\r'`).
- `git diff` shows CommandDispatcher.kt and FineTuneHolder.kt untouched.
- Burst test proves N taps → exactly 1 wire request with the final clamped value.
- AdjusterPanel: busy = dim-but-tappable −/+, Reset disabled; true disablement unchanged.
- Owner re-test on flox follows (NOT a gate of this plan — but force-rebuild + APK-mtime check
  before install, [[dinghy-stale-apk-uat-gate]]; the owner is at the device waiting).
</verification>

<success_criteria>
- Fine-Tune and heater steppers accumulate taps locally with instant clamped display updates and
  zero lockout during the burst; one dispatch per quiet window; 17-07 clamp/precision/epsilon/
  timeout invariants intact on that single dispatch; taps during the in-flight commit produce a
  follow-up commit; resets immediate; commit-on-dispose; CommandDispatcher byte-identical;
  full gate green; 26.5 UAT bookkeeping recorded.
</success_criteria>

<output>
Create `.planning/quick/260611-rmr-stepper-trailing-commit-batching/SUMMARY.md` when done.
</output>
