---
status: resolved
phase: 17-fine-tune-live-adjust-panel
plan: 17-06
deferred: false
deferred_by: owner
deferred_on: 2026-06-06
resumed_on: 2026-06-08
resume_when: "Matthew back at the computer with flox on a live printer (E5/E3)"
result: "8/8 — initial run 6 PASS / 2 FAIL; both gaps fixed (Check 6 -> 17-07, Check 8 -> 17-08) and on-device re-verified on flox (owner-approved 2026-06-07)"
updated: 2026-06-07
---

# Phase 17 — On-Device UAT (Fine-Tune / Live-Adjust)

**Plan:** 17-06 (Task 2 — `checkpoint:human-verify`, blocking)
**Status:** DEFERRED (owner) — all code + the landscape-clip fix are built and installed on flox; the
hands-on functional checks (state-flip / reject / mid-print effect / perf) are intentionally deferred to a
later session when Matthew is back in front of the device. NOT blocked, NOT failed — awaiting the human gate.
Visual review of layout/entry/orientation already done remotely via screenshots (`uat-shots/`, incl. the
`*b` set proving landscape clipping resolved).
**Build:** debug `app-armeabi-v7a-debug.apk` installed on flox (`0a64b42e`) from commit `73296fd`
(includes `fix(17-06)` label-less tiles / landscape clip fix on top of the `2c4cbc4` nav wiring)
**Device:** flox (LineageOS 18.1 / API 30, genuine Adreno 320 / 2GB / 1920×1200, `armeabi-v7a`)
**Printers (Moonraker):** Ender 5 Plus = `192.168.1.120:7125` · Ender 3 Pro = `192.168.1.121:7125`

## Scope & ground rules

- **Motion + Extrusion ONLY.** FW-Retraction is **build-blind** on both dev printers (neither exposes a
  `[firmware_retraction]` object) — it is EXPLICITLY EXCLUDED from on-device UAT (covered by the 17-03
  synthetic-fixture reducer test + the 17-05 gate + the compile-checked `onFwRetraction` wire). Expect NO
  FW-retraction entry on the Extrusion screen on these printers (that ABSENCE is itself check 3 / SC-2).
- **State-flip, not optimistic.** "Tile flips" means the tile's REPORTED value updates to the commanded
  value after the printer object actually changes — not an instant local echo. The whole group stays
  busy/dimmed until the value flips (D-15).
- **Perf reframe (ADR-0001 Add.2):** judge on **no frozen frames + responsiveness**, NOT the 95%-janky
  gfxinfo artifact (these are sparse event-driven static value-tile screens — LOW perf risk).
- **On-device iteration model:** Claude built + installed; **Matthew navigates + eyeballs** the result and
  records PASS/FAIL below. A FAIL spawns a gap-closure plan — the phase is NOT marked complete on a FAIL.

## Pre-flight (Matthew)

1. Open Dinghy Display on flox; connect to a live printer (E5 `192.168.1.120:7125` for the mid-print checks,
   or E3 `192.168.1.121:7125`).
2. For the state-flip-during-print checks (2 & 3), **start a real print on E5** (ROADMAP SC-4) so speed/flow
   changes have a visible physical effect. The Standby/idle path still proves the tiles flip; the live print
   proves the physical effect + mid-print availability.

## Checks

> Record `PASS` / `FAIL` + notes per check. Leave PENDING until run.

### 1 — Entry + reset-to-Hub (TUNE-01 / D-21 / REVIEW #6) — PASS
- Tap the **Print-Status Tune button** (the first/flexible tile in the active-print shortcut row, sliders
  glyph) → the **Fine-Tune Hub** opens. Hub shows **Motion + Extrusion** entries, **no summary values** (D-20).
- Navigate into **Motion**, **Back** to the Hub, leave Fine-Tune (Back again), then **re-enter via the Tune
  button** → it opens the **HUB** again, NOT the Motion group page (REVIEW #6 — reset-to-Hub on entry).
- Repeat the re-entry check via the **drawer "Fine-Tune" tile** (swipe up → Fine-Tune): also opens the Hub.
- **Expected:** Tune (and the drawer tile) always land on the Hub; group sub-nav never shows a stale page.

### 2 — Motion state-flip + perf (SC-1 / SC-4 / Open-Q2) — PASS
- On **Motion**, nudge **Speed % ±5** → the tile's reported value **flips** to the commanded value (not
  instant-optimistic) and the print **visibly speeds/slows**.
- Nudge **Max accel ±100** → the tile value **flips**.
- Confirm **5 tiles** show (Speed, Max velocity, Max accel, Min cruise, Square-corner velocity), each with
  **≥64px** targets, and the screen is usable in **BOTH portrait and landscape** (Open-Q2).
- **Expected:** every nudge flips the reported tile value; physical effect visible on Speed; layout works
  both orientations.

### 3 — Extrusion state-flip + cold-enable + capability-gating (D-02 / SC-2 / REVIEW #5) — PASS
- On **Extrusion**, nudge **Flow % ±1** → tile **flips** + physical effect; nudge **Pressure advance ±0.001**
  → tile **flips**; nudge **Part-cooling fan ±5%** → the `fan.speed` readout **flips**.
- Confirm **Flow and PA tiles are ENABLED even when the extruder is COLD** (D-02 / REVIEW #5 — no cold guard).
- Confirm the **FW-retraction entry is ABSENT** (neither dev printer exposes it — capability-gated, SC-2).
- **Expected:** all three flip; Flow/PA usable cold; no FW-retraction entry.

### 4 — Long-press reset (TUNE-05 / D-16 / REVIEW #3) — PASS
- **Long-press the Speed value** → resets to **100%**; **long-press the Max-accel value** → resets to the
  **config baseline**.
- Confirm the **part-fan tile has NO reset long-press** (`onReset = null`, REVIEW #3), and any tuner whose
  baseline is absent likewise has **no reset** (no-op, nothing happens).
- **Expected:** Speed→100%, Max accel→baseline; part-fan + baseline-absent tuners do not reset.

### 5 — Whole-group busy-lock holds until state-flip (D-15 / REVIEW #2) — PASS
- **Rapid-nudge** a tile → the **whole group dims/disables** and **STAYS busy** until the tile's reported
  value actually **flips to the commanded target** (state-flip, not just the ack), then re-enables.
- **Expected:** group locks on dispatch; unlocks only when the value reaches target (or on failure).

### 6 — Reject path (G1 / T-17-06-01) — RESOLVED (was FAIL; fixed by 17-07, on-device re-verified 2026-06-07)
result: resolved
reported: "Pushing extrusion flow above Klipper's 150% cap: NO error toast appears at all. The whole Fine-Tune group just locks into the inactive/busy (dimmed) state and stays there permanently. Leaving + re-entering Fine-Tune does NOT recover it; reverting the value down does NOT recover it. Only closing and reopening the app restores the controls. App does not crash. (Originally reached via Fluidd setting flow >150%, then jiib blocked — same underlying bug.)"
severity: major
root_cause_hypothesis: "PRIMARY: the JSON-RPC request for the rejected command never completes — when Klipper returns an *error* response for the request id, jiib does not resolve that request's CompletableDeferred exceptionally, so the awaiting coroutine hangs forever. Result: (a) no toast (no exception is ever thrown to surface one), and (b) the busy-lock engaged on dispatch never releases (neither success-flip nor error path fires). No timeout backstop on the await; process restart is the only reset. SECONDARY (if the error IS thrown but swallowed): the RpcError catch path neither shows the toast nor clears the per-group busy/pending lock. Diagnosis must confirm which — inspect the JSON-RPC response router's error branch and the Fine-Tune command dispatch/await + lock-release wiring."
- Nudge a value **Klipper rejects** (or push beyond range) → a **non-fatal error toast**, the readout does
  **NOT move**, the **busy-lock releases**, **no crash** (G1 lesson — RpcError must not be uncaught).
- **Expected:** toast + no readout move + lock releases + app stays up.

### 7 — Perf (SC-3 / ADR-0001 Add.2) — PASS
- While nudging on Motion/Extrusion, observe responsiveness (and optionally capture `gfxinfo framestats`).
- Judge on **no frozen frames + responsive** (NOT the 95%-janky gfxinfo artifact). Static value-tile
  screens, LOW perf risk.
- **Expected:** no frozen frames; taps respond promptly.

### 8 — Instrumented nav test on-device (REVIEW #8, where feasible) — RESOLVED (was FAIL; product fix by 17-08, on-device re-verified 2026-06-07; instrumented-test harness defect deferred)
result: resolved
reported: "Ran on flox (build 7ed8e70). 2 tests: 1 PASS, 1 FAIL. FAIL = reEnteringFineTune_opensHub_notStaleGroupPage — AssertionError: hasText('Motion') is not displayed after re-entry. NOTE: manual Check 1 (reset-to-Hub on re-entry) PASSED on the SAME build, so product behavior is correct on-device → most likely test drift from the 18.x rebrand/icon changes (Tune-button matcher or Hub-tile content the test keys on changed), NOT a product regression. Needs diagnosis to confirm test-only vs real."
severity: minor
root_cause_hypothesis: "Test infrastructure drift: FineTuneNavTest's navigation/assertion matchers (Tune-button finder or Hub 'Motion' tile text/semantics) likely changed across Phase 18.1 (icon conformance) / 18.2 (jiib rebrand) / 18.3 (spool icon), so the re-entry test fails to drive to or recognize the Hub. The first-entry test still passes, which argues the Hub renders fine — pointing at the re-entry test's specific navigation steps, not product nav. Diagnosis must read FineTuneNavTest + the current Tune-button/Hub composables to confirm test-only fix vs genuine reset-to-Hub regression in the tested code path."
- Run on flox:
  `:app:connectedDebugAndroidTest --tests 'works.mees.dinghy.ui.finetune.FineTuneNavTest'`
  (host-side via `E:\Android\gw.bat`).
- **Expected:** GREEN (Tune → Dest.FineTune Hub + reset-to-Hub on re-entry).

## Results

| Check | Result | Notes |
|-------|--------|-------|
| 1 Entry + reset-to-Hub | PASS | Tune button + drawer tile both land on Hub; reset-to-Hub on re-entry confirmed |
| 2 Motion state-flip + perf | PASS | Speed/accel tiles flip; physical speed change visible on live E5 print; portrait+landscape OK |
| 3 Extrusion + cold + cap-gate | PASS | Flow/PA/fan flip; Flow+PA enabled cold; no FW-retraction entry (cap-gated) |
| 4 Long-press reset | PASS | Speed→100%, Max accel→baseline; part-fan + baseline-absent = no-op |
| 5 Busy-lock holds to state-flip | PASS | Group locks on dispatch, unlocks only when reported value reaches target |
| 6 Reject path | **RESOLVED** | Was FAIL (permanent busy-lock wedge at clamp ceiling, no toast). Fixed by 17-07; on-device re-verified on flox — at-cap '+' no longer wedges. Owner-approved 2026-06-07 |
| 7 Perf no-frozen-frames | PASS | Responsive on live E5 print, no frozen frames while nudging |
| 8 Instrumented nav test on-device | **RESOLVED** | Was FAIL — real product bug (same-dest re-entry skipped reset-to-Hub). Fixed by 17-08; on-device re-verified on flox — re-entry lands on Hub. Owner-approved 2026-06-07. (Instrumented FineTuneNavTest still RED on a pre-existing swipe-gesture harness defect — deferred test-hardening; manual eyeball was the authoritative gate) |

**Overall:** RESOLVED — 8/8. Initial run 6 PASS / 2 FAIL; both gaps fixed (Check 6 → 17-07, Check 8 → 17-08) and on-device re-verified on flox (owner-approved 2026-06-07).

## Gaps

```yaml
- truth: "Nudging a Fine-Tune value Klipper rejects (e.g. flow >150% extrude cap) shows a non-fatal error toast, the readout does not move, the whole-group busy-lock RELEASES, and the app does not crash (G1 / REVIEW #2)."
  status: resolved
  resolution: "Fixed by plan 17-07 (commits 5301c83 + f0d96f9; 17-07-SUMMARY.md) — single-source clamp authority in PrinterCommands + clamped markPending targets at every tuner call site + per-tuner strict-< float epsilon (step*0.1) replacing the flat FLIP_TOLERANCE=0.5 + a seq-guarded bounded-timeout backstop. ON-DEVICE RE-VERIFIED on flox: nudging Flow to the 150% cap and tapping '+' at the cap no longer wedges the group (stays responsive). Owner-approved 2026-06-07."
  reason: "User reported (live E5, build 7ed8e70): rejecting >150% flow shows NO toast at all; the controls just lock and dim permanently. App does NOT crash. Leaving+re-entering Fine-Tune and reverting the value both fail to recover; only closing+reopening the app clears it. (Originally surfaced via Fluidd setting flow >150% then jiib blocking — same underlying bug.)"
  severity: major
  test: 6
  root_cause_CONFIRMED: "NOT a JSON-RPC/toast bug — both original hypotheses REFUTED by code diagnosis. JsonRpcClient.kt:168-175 DOES completeExceptionally on error responses; request() has withTimeout (line 136); CommandDispatcher.kt:142-155 catches RpcError, toasts, and clears in-flight in finally. ALL correct. REAL CAUSE: client-side clamp vs optimistic markPending target mismatch. At flow=150% tapping '+': ExtrusionScreen.kt:176-181 sets target=151 and calls markPending(FLOW, 151.0) with the UNCLAMPED target, then dispatchFlow(151) → PrinterCommands.flowFactor(151) coerceIn-clamps to M221 S150 (FLOW_PCT_MAX=150, PrinterCommands.kt:72,311). Klipper ACCEPTS S150 as a no-op → NO RpcError → no Failure event → no toast, clearPending never called. extrude_factor stays 1.5 (flowPct=150). FineTuneHolder.reached() (kt:131-149) compares current=150 vs target=151: |150-151|=1 > FLIP_TOLERANCE(0.5) → never reached → _pendingStateFlip stuck → groupBusy = inFlight||pending permanently true (kt:188) → enabled=!groupBusy dims+inerts the whole group (FineTuneTile.kt:107,142 strip handlers when !enabled, so user can't even tap '-' to recover). Holder lives in AppShell.kt:336 remember(store), survives sub-nav, only rebuilt on reconnect → restart-only recovery. WHOLE-CLASS bug: same trap for all unclamped-max '+' tuners (FLOW, SPEED 300, MAX_VELOCITY 1000, MAX_ACCEL 50000, SCV 20, PA 1.0, SMOOTH 0.2). MIN_CRUISE + PART_FAN safe (clamp display target before markPending). Test gap: FineTuneHolderTest.kt:102 only arms a REACHABLE target — no at-cap/unreachable-target test exists, so green suite never caught it. RECOMMENDED FIX: (A) clamp markPending target to the same bounds the wire uses (at-cap nudge = no-op, doesn't arm unreachable flip) OR have markPending skip arming when target==already-reported value; (B) backstop: bounded timeout on _pendingStateFlip so an unreachable flip self-clears (FineTuneHolder.kt:103-117/123-128); plus a regression test arming an unreachable markPending target and asserting groupBusy releases."
  artifacts: []
  missing: []

- truth: "FineTuneNavTest.reEnteringFineTune_opensHub_notStaleGroupPage passes on flox — re-entering Fine-Tune lands on the Hub (Motion/Extrusion shown), not a stale group page (REVIEW #8)."
  status: resolved
  resolution: "Fixed by plan 17-08 (commit 2bebae0; 17-08-SUMMARY.md) — ShellNavState.navigateTo() now runs the per-dest entry-reset side-effects (extracted into applyEntryReset(target)) on a same-dest re-selection BEFORE the no-push early-return, so re-entering Fine-Tune from its own drawer tile while inside Motion snaps to the Hub; one-spot fix also closes the identical latent Calibration + Macros holes. ON-DEVICE RE-VERIFIED on flox: re-entering Fine-Tune while inside Motion lands on the Hub. Owner-approved 2026-06-07. NOTE: the instrumented FineTuneNavTest still fails on a pre-existing swipe-gesture harness defect (swipeUp never clears AppShell's 80px single-event SWIPE_UP_THRESHOLD_PX); owner used manual eyeball as the authoritative gate, instrumented-test hardening deferred to a separate pass."
  reason: "Instrumented run on flox (build 7ed8e70): test RED with AssertionError hasText('Motion') not displayed after re-entry. The sibling first-entry test PASSED, and manual Check 1 (same behavior) PASSED on the same build — so product nav is correct; the test itself appears stale."
  severity: minor
  test: 8
  root_cause_CONFIRMED: "NOT test drift — original hypothesis REFUTED. All matchers read live string resources ('Motion'/'Extrusion'/'Fine-Tune', strings.xml:113-115) UNCHANGED across Phase 18.x; sibling first-entry test passes with the same matchers. REAL CAUSE: genuine (narrow) product bug. ShellNavState.navigateTo() early-returns at kt:90 when target==dest, BEFORE the Fine-Tune reset-to-Hub at kt:104-106 (fineTuneGroup=null). Hub→group is sub-nav within a single Dest.FineTune (tapping Motion only sets nav.fineTuneGroup=MOTION, AppShell.kt:653; dest stays Dest.FineTune). So re-entering Fine-Tune WHILE ALREADY INSIDE it (drawer tile over the Motion sub-page → onDestination→navigateTo(Dest.FineTune), AppShell.kt:852) hits target==dest → early-return fires before the reset → stale MotionScreen renders (AppShell.kt:656). MotionScreen shows NO visible 'Motion' Text (only tile-glyph contentDescriptions), so onNodeWithText('Motion').assertIsDisplayed() (FineTuneNavTest.kt:160) throws exactly as reported. First-entry PASSES because dest=PrintStatus then (target!=dest → reset runs → Hub). Manual checks PASSED because both owner paths change dest first (Tune button lives on Dest.PrintStatus; manual drawer re-entry lands on Status between visits). The test caught the one same-dest re-selection path neither manual run used. SAME latent hole exists for Calibration (calibrationRoutine reset kt:98-100) and Macros (kt:93-96). RECOMMENDED FIX (product, not test): ShellNavState.kt:89-108 — run the per-dest entry-reset side-effects even on same-dest re-selection (handle target==dest by running resets then returning, instead of unconditional early-return). One-spot change closes Fine-Tune + Calibration + Macros holes; makes REVIEW #6 'Fine-Tune always opens the Hub' hold for every entry path. Test is correct as written — keep it."
  artifacts: []
  missing: []
```

## Known limitation (recorded per T-17-06-02)

FW-Retraction is shipped **untested on hardware that has it** — build-blind on both dev printers (E5/E3).
It is covered by the 17-03 synthetic-fixture reducer test, the 17-05 holder gate, and the explicit
compile-checked `onFwRetraction` wire (REVIEW #4). To be validated opportunistically on a printer that
reports a `firmware_retraction` object.
