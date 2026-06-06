---
phase: 17
reviewers: [codex]
reviewed_at: 2026-06-06
plans_reviewed: [17-01-PLAN.md, 17-02-PLAN.md, 17-03-PLAN.md, 17-04-PLAN.md, 17-05-PLAN.md, 17-06-PLAN.md]
model: codex-cli 0.137.0 (default model)
verdict: HIGH risk as written → MEDIUM after fixes
---

# Cross-AI Plan Review — Phase 17

## Codex Review

## Summary

The plans are unusually thorough and mostly aligned with the phase intent: command/state/UI/nav responsibilities are separated cleanly, the known scaling traps are called out, and the final route wiring includes the right top-level files. However, as written, I would not approve them unchanged. The biggest risks are not missing research; they are execution contradictions and dead-wire edges: active Wave-0 `fail()` tests break later parallel full-suite gates, busy-lock is likely ack-bound rather than state-flip-bound, the firmware-retraction entry lacks an explicit navigation callback path, and reset/null-baseline semantics are internally inconsistent.

## Strengths

- Clear wave decomposition: command layer, state layer, assets, UI, and final nav/UAT are mostly separated along real ownership boundaries.
- The critical scaling traps are explicitly known: `speed_factor`/`extrude_factor` ratio to percent, `fan.speed` 0-1 to `M106 S0..255`, and `minimum_cruise_ratio` 0-1.
- Smooth-time baseline uses the correct config key, `pressure_advance_smooth_time`, in the plan.
- `firmware_retraction` is correctly treated as build-blind and fixture-tested rather than live-UAT-gated.
- Dead-wiring coverage is mostly present: `TopRoute.kt`, `AppShell.kt`, `AppDrawer.kt`, and `PrintStatusScreen.kt` are all in `17-06` files_modified.

## Concerns

- **HIGH: Wave-0 RED tests conflict with full-suite and parallel wave verification.**  
  `17-01` adds active `fail()` tests across command, state, holder, and nav. Then `17-02` and `17-03` both require full host suite green while the other wave’s RED stubs may still be active. That makes the declared parallelism and full-suite gates impossible unless waves run in a hidden sequence.

- **HIGH: Whole-group busy-lock is not actually tied to state-flip confirmation.**  
  Plans use `groupBusy = inFlight.isNotEmpty()`. If `CommandDispatcher.inFlight` clears on RPC ack before the printer-object update arrives, the user can tap again while the VM still shows the old value. That can resend the same target instead of cumulative nudges and violates D-15’s “until confirmed state again” intent.

- **HIGH: `FineTuneTile` reset API conflicts with part-fan/no-baseline behavior.**  
  `FineTuneTile` is specified with required `onReset`, but the part-fan tile intentionally has no reset. Baseline-null motion/PA/FW cases are also not handled explicitly. This can become either a compile/API mismatch, a silent no-op long-press, or an invalid bare command dispatch.

- **HIGH: Firmware-retraction entry can be dead-wired.**  
  `ExtrusionScreen` is modeled after `ExtrudeScreen(container, holder, onBack)` but also needs to navigate to `FwRetractionScreen`. The plan does not explicitly add `onFwRetraction: () -> Unit`, and `17-06` does not explicitly pass it. Because dev hardware hides the entry, UAT will not catch this.

- **MEDIUM: Fine-Tune local sub-nav may reopen stale group pages.**  
  `PrintStatusControlAction.Tune -> onNavigate(Dest.FineTune)` does not explicitly reset `fineTuneGroup = null`. If the shell preserves local group state, Tune may reopen Motion/Extrusion/FW instead of the required Hub.

- **MEDIUM: `FineTuneGroup` visibility/location is underspecified.**  
  `FineTuneHubScreen` and `AppShell` both need the group enum. If it is accidentally private/local to a screen file, `17-06` will fail compilation. Define it as public/internal top-level in a known file.

- **MEDIUM: Android test compile/run gates are incomplete.**  
  `17-01` acceptance mentions `compileDebugAndroidTestKotlin`, but the verify command only runs unit tests. `17-06` says instrumented nav test but verifies only `assembleDebug`. This misses exactly the nav-test compile failures the phase is trying to prevent.

- **MEDIUM: Capability gating may stale on reconnect.**  
  Holder plans snapshot `store.capabilities.value` inside a `printerState`/baseline combine. If capabilities change without a printer-state emission, hidden/shown tuner gates may not refresh. Fine-Tune should include capabilities as a flow if available, or test the existing store behavior proves this safe.

- **MEDIUM: “Cold-extruder guard for PA/flow” is dangerous ambiguity.**  
  Flow factor and pressure advance are non-extruding live overrides and should be always available when connected. Adding a cold guard would violate D-02.

- **MEDIUM: `minimum_cruise_ratio` display scaling is under-tested.**  
  Plans test speed/flow/fan scaling, but not `minimum_cruise_ratio` display as percent while command wire value remains ratio.

- **LOW: Icon export is not operationally closed.**  
  `17-04` depends on Material Symbols SVG path data but does not say where the executor gets it offline. If network is unavailable, this can stall or produce invented assets.

## Suggestions

- Change Wave-0 strategy: either make future-wave tests compile-only/ignored until their owning wave, or remove full-suite-green requirements until all RED stubs are converted. Do not keep active `fail()` tests and also require unrelated wave full-suite success.

- Add a state-flip pending model: group busy should be `dispatcher.inFlight.isNotEmpty() || pendingStateFlip != null`, cleared only when the reduced VM value reaches the target or a dispatch failure/timeout arrives.

- Make reset nullable: `onReset: (() -> Unit)? = null`, install long-press only when non-null, and block reset dispatch when baseline/current value is null. Resolve the part-fan reset exception before `17-05`, not during UAT.

- Explicitly define navigation APIs:
  - top-level `enum class FineTuneGroup`
  - `FineTuneHubScreen(onNavigate: (FineTuneGroup) -> Unit)`
  - `ExtrusionScreen(onFwRetraction: () -> Unit)`
  - AppShell resets `fineTuneGroup = null` when entering `Dest.FineTune` from Tune/drawer.

- Add tests for hub-to-group, extrusion-to-FW with synthetic caps, BackHandler pop-to-hub, and Tune-entry resets-to-hub.

- Add explicit verification commands for `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebugAndroidTest`, and the nav instrumented test where feasible.

- Remove the cold-extruder guard language for flow/PA. Gate only on object capability.

- Add tests for `minimum_cruise_ratio`: live `0.5` displays as `50%`, one tap adds `0.05`, command sends `MINIMUM_CRUISE_RATIO=0.55`.

## Risk Assessment

**Overall risk: HIGH as written.** The architecture is sound, but several plan-level contradictions can produce a phase that either cannot pass its own verification gates or compiles while leaving important UI paths dead. After fixing the RED-test strategy, state-flip busy model, reset API, and sub-nav wiring, this drops to **MEDIUM** because the remaining risks are mostly build-blind FW-retraction coverage and live-device UAT variance.

---

## Orchestrator Triage (Claude, verified against plans)

| # | Finding | Sev | Verdict | Action |
|---|---------|-----|---------|--------|
| 1 | Wave-0 active `fail()` RED tests vs Wave-1 full-suite-green gates | HIGH | **CONFIRMED** (17-02:160, 17-03:148 run full suite; 17-01 plants holder/nav RED) | Fix: Wave-1 plans verify only their targeted `--tests`; full-suite-green moves to the final gate (17-06) |
| 2 | Busy-lock `inFlight.isNotEmpty()` not state-flip-bound (D-15) | HIGH | Borderline-real | Fix: `groupBusy = inFlight.isNotEmpty() || pendingStateFlip != null`, cleared on value-reaches-target / failure / timeout |
| 3 | `FineTuneTile.onReset` required vs part-fan no-reset | HIGH | **CONFIRMED** (sig at 17-05:143; part-fan no-reset at 17-05:205) | Fix: `onReset: (() -> Unit)? = null`; long-press installed only when non-null; block reset when baseline null |
| 4 | FW-retraction `onFwRetraction` callback not pinned | HIGH | PARTIAL (target modeled; callback threading underspecified) | Fix: explicit `ExtrusionScreen(onFwRetraction: () -> Unit)` wired from AppShell sub-nav |
| 5 | Cold-extruder guard on PA/flow violates D-02 (always-available) | MED | **CONFIRMED** (17-05:207) | Fix: remove cold-guard language for flow/PA; gate ONLY on object capability |
| 6 | Tune entry doesn't reset `fineTuneGroup = null` → may reopen stale group | MED | Plausible | Fix: AppShell resets sub-nav to Hub on entering Dest.FineTune |
| 7 | `FineTuneGroup` enum visibility (private→compile blocker, echoes P16 PresetSelector) | MED | Plausible | Fix: define top-level `enum class FineTuneGroup` (public/internal) in a known file |
| 8 | Instrumented/androidTest compile not gated (verify runs unit-only) | MED | **CONFIRMED** (17-06 verifies assembleDebug, not the nav test compile) | Fix: add `:app:compileDebugAndroidTestKotlin` / nav-test verify where feasible |
| 9 | `minimum_cruise_ratio` display scaling under-tested | MED | Real | Fix: add test (live 0.5→"50%", +tap→MINIMUM_CRUISE_RATIO=0.55) |
| 10 | Capability gating may stale on reconnect (snapshot vs flow) | MED | Needs check | Verify existing store behavior or fold capabilities as a flow |
| 11 | Icon SVG path-data offline source unspecified | LOW | Minor | Note where executor sources glyph path data (bucket json / offline) |

**No false positives identified** (contrast P11). Smooth-time config-key (`pressure_advance_smooth_time`) and the scaling traps were already correctly handled in the plans — Codex confirmed those as strengths.

## Consensus Summary

Single external reviewer (Codex). Architecture sound; the risk is execution-level plan contradictions, not missing research. Highest-priority fixes: the RED-vs-full-suite gate contradiction (#1), the state-flip busy model (#2), nullable reset API (#3), FW-retraction callback wiring (#4), and removing the D-02-violating cold guard on PA/flow (#5).
