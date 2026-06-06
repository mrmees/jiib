---
phase: 17-fine-tune-live-adjust-panel
plan: 05
subsystem: ui
tags: [wave-2, fine-tune, holder, tile, screens, state-flip-busy, tdd-green]
requires:
  - "17-02 command builders + registry specs (speedFactor/flowFactor/setVelocityLimit/setPressureAdvance/setFan/setRetraction)"
  - "17-03 nullable readback fields + null-safe reducer + 10 nullable config-baseline StateFlows on PrinterStateStore"
  - "17-04 11 glyph drawables in res/drawable (consumed via painterResource)"
  - "17-01 RED FineTuneHolderTest stubs (turned GREEN here)"
provides:
  - "Top-level public FineTuneGroup enum (MOTION/EXTRUSION/FW_RETRACTION) for AppShell (17-06)"
  - "FineTuneHolder + FineTuneVm: display-scaled (ratio→%, 0..1→%) values, capability gates, baseline folding, D-15 state-flip whole-group busy lock, nullable-baseline reset"
  - "FineTuneTile (tap-inert value, nullable long-press reset, accent ±, busy-dim) + FineTuneHubScreen (two typed-nav entries)"
  - "MotionScreen / ExtrusionScreen / FwRetractionScreen — capability-gated, state-flip busy-locked, onFwRetraction wired, NO cold-guard on flow/PA, build-blind FW-retraction"
affects:
  - "17-06 (nav wiring + final full-suite gate + on-device UAT) reads FineTuneGroup + all four screens"
tech-stack:
  added: []
  patterns:
    - "Display scaling lives in the holder buildVm ONLY (Pitfall 1) — reducer stores RAW"
    - "Caps folded as a FLOW in combine (REVIEW #10) so reconnect re-emits the vm"
    - "Ten baseline StateFlows wrapped into ONE FineTuneBaselines list-form combine (combine arity cap)"
    - "D-15 state-flip busy: markPending(tuner,target) on each nudge; groupBusy = inFlight.isNotEmpty() || pendingStateFlip != null; clears when reduced value reaches target (NOT on bare ack), or on failure"
    - "Tap-inert value cell; long-press handler installed ONLY when onReset != null (REVIEW #3 nullable reset)"
    - "minCruise: DISPLAY percent, WIRE ratio (REVIEW #9) — vm.minCruisePct shown, target/100 sent"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneGroup.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneVm.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneTile.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHubScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/MotionScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/ExtrusionScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FwRetractionScreen.kt
  modified:
    - app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneHolderTest.kt
decisions:
  - "Added FineTuneTuner enum + PendingStateFlip(tuner,target) data class to model the D-15 state-flip busy lock; the holder's reached() maps each tuner to its reduced printer-object value (with the same display scaling the vm exposes) and a 0.5 display-unit tolerance covers wire rounding."
  - "Ten baseline StateFlows folded via a list-form combine into one FineTuneBaselines flow, then a 5-arg top-level combine(printerState, capabilities, baselinesFlow, inFlight, pendingStateFlip) — stays within the combine arity cap (PATTERNS)."
  - "Added FineTuneShared.kt (not in the plan's named files) carrying the fixed per-control steps, DASH, fmtValue, and the shared VelocityLimitTile — avoids re-typing the velocity/accel/scv nudge+reset boilerplate three times. Documented deviation below."
  - "PA/smooth formatted at the tile via String.format(Locale.US, %.3f/%.2f) for the finer precision; motion limits use fmtValue (drop trailing .0)."
metrics:
  duration: ~9 min
  completed: 2026-06-06
---

# Phase 17 Plan 05: Fine-Tune UI Summary

Built the keyboard-free Fine-Tune live-adjust surface: the top-level `FineTuneGroup` enum, the headless `FineTuneHolder`/`FineTuneVm` (display-scaling + capability gates + baseline folding + the D-15 state-flip whole-group busy lock + nullable-baseline reset), the shared tap-inert/long-press-reset `FineTuneTile`, the two-entry `FineTuneHubScreen`, and the three group screens (Motion / Extrusion / FW-Retraction). Turned the 17-01 `FineTuneHolderTest` RED stubs GREEN; debug build assembles.

## What Was Built

**Task 1 — group enum + holder + vm** (`b43c3eb`):
- `FineTuneGroup` — top-level PUBLIC enum (`MOTION`/`EXTRUSION`/`FW_RETRACTION`), referenceable by both the screens and AppShell (REVIEW #7, avoids the P16 private-symbol blocker).
- `FineTuneVm` — plain data class (NO Compose annotations, host-testable): display-scaled `speedPct`/`flowPct`/`minCruisePct`/`partFanPct`, RAW `maxVelocity`/`maxAccel`/`scv`/`pressureAdvance`/`smoothTime`, the four FW-retraction sub-values, five capability gates, a nullable-per-tuner `FineTuneBaselines` bundle, and `groupBusy`.
- `FineTuneHolder` — `combine(printerState, capabilities(FLOW, REVIEW #10), baselinesFlow, inFlight, pendingStateFlip) → buildVm`. Scaling lives ONLY in `buildVm` (Pitfall 1). The ten baseline StateFlows are wrapped in one list-form `FineTuneBaselines` combine (arity cap). The D-15 busy model: `markPending(tuner,target)`/`clearPending()`/`setInFlight(keys)`; `groupBusy = inFlight.isNotEmpty() || pendingStateFlip != null`; the pending flip clears the instant the reduced printer-object value for that tuner reaches the target (`reached()`, 0.5 display-unit tolerance) — NOT on the bare ack.
- `FineTuneHolderTest` — all 8 stubs converted GREEN: ratio→% (1.05→105), 0..1→% fan (0.6→60), minCruise 0.5→50%, gates, baseline folding, the state-flip busy persistence past the ack, the nullable-baseline no-op, and null→dash.

**Task 2 — shared tile + hub** (`fc6523f`):
- `FineTuneTile` — `iconRes·name·value·−·+`. Value cell is tap-INERT (D-14); the long-press reset handler is installed ONLY when `onReset != null` (REVIEW #3 / D-16). ± are plain `clickable` in the accent intent (THEMING C5). `!enabled` dims the whole tile (alpha 0.4) and strips ALL handlers (D-15). Glyphs via `painterResource` (17-04 drawables) + `Icon(tint)`, NO Material Symbols font (D-17). `fsSp` scale throughout (value hero 48f, name 18f, no sub-15sp).
- `FineTuneHubScreen` — two large entries (Motion / Extrusion) ONLY, NO live values (D-20); each calls the typed `onNavigate(FineTuneGroup)` (REVIEW #4). Gutter Back = neutral with the `keyboard_return` glyph.

**Task 3 — three group screens** (`f19c9c1`):
- `FineTuneShared` — fixed per-control steps (D-03..D-12 / D-18), `DASH`, `fmtValue`, and the shared `VelocityLimitTile` (velocity/accel/scv).
- `MotionScreen` — 5 capability-gated tiles (Speed[gcode_move], Max Vel/Max Accel/Min Cruise/SCV[toolhead]). Min-cruise DISPLAYs percent, SENDs ratio (REVIEW #9). Speed reset = `M220 S100` (always available); the four limits reset to their baseline ONLY when non-null (`onReset = baseline?.let { … }`, REVIEW #3). State-flip whole-group busy lock.
- `ExtrusionScreen` — Flow[gcode_move], Press Adv/Smooth[extruder], Part Fan[fan] + a FW-Retraction entry tile shown ONLY when `hasFwRetraction` → `onFwRetraction()` (typed, REVIEW #4). NO cold/temperature guard on flow or PA (REVIEW #5) — gating is object-presence only. Part-fan `onReset = null` (A2 / no `[fan]` config baseline). Flow reset = `M221 S100`.
- `FwRetractionScreen` — 4 `SET_RETRACTION` tiles, re-sending all four fields each nudge; BUILD-BLIND (gated off on both dev printers, Pitfall 3), baseline-conditional resets (null on dev printers → no-op).
- All screens: state-flip busy via `groupBusy = inFlight.isNotEmpty() || holder.pendingStateFlip != null || vm.groupBusy`, every tile `enabled = !groupBusy`, no optimistic local value state (tiles render `vm.*` only), `DispatchEvent.Failure → SeverityToast` + `clearPending()` (G1 / T-17-05-02), gutter Back = `Intent.Neutral`.

## Verification

- **Task 1:** `:app:testDebugUnitTest --tests works.mees.dinghy.ui.finetune.FineTuneHolderTest` → BUILD SUCCESSFUL (exit 0), all 8 tests GREEN. Grep gates pass: enum present + NOT private; `pendingStateFlip` in holder; `store.capabilities` flow-folded; `buildVm` present + ≥1 scaling op; `FineTuneVm` has 0 Compose annotations.
- **Task 2 + 3:** `:app:assembleDebug` → BUILD SUCCESSFUL (exit 0). Grep gates pass: nullable `onReset` + conditional `detectTapGestures` on the value cell; `painterResource` + 0 `MaterialSymbol`; typed `onNavigate(FineTuneGroup)`; no raw `.sp` outside `fsSp`; `pendingStateFlip` in all three group screens; `onFwRetraction` in the Extrusion signature + called from the gated entry; NO `canExtrude`/cold guard applied (the 4 `cold` hits are all comments/KDoc stating its ABSENCE); part-fan `onReset = null`; no optimistic value state.
- Re-ran the holder test after all screens compiled in → still GREEN. Per REVIEW #1, did NOT run the full host suite — the full-suite-green gate is 17-06.

## Deviations from Plan

### Auto-added (Rule 2 - missing supporting structure)

**1. [Rule 2] Added `FineTuneShared.kt` (not in the plan's named files)**
- **Found during:** Task 3
- **Issue:** The plan named 8 production files; the three group screens share the fixed per-control steps, the `DASH` constant, the `fmtValue` formatter, and an identical velocity/accel/scv nudge+reset tile. Inlining all of it three times would be error-prone and violate DRY.
- **Fix:** Added `FineTuneShared.kt` carrying those shared internals + the `VelocityLimitTile` composable. Purely additive; no behavior change vs the plan's intent.
- **Commit:** `f19c9c1`

**2. [Rule 2] Added `FineTuneTuner` enum + `PendingStateFlip` data class to the holder**
- **Found during:** Task 1
- **Issue:** The plan describes the D-15 state-flip busy lock and `pendingStateFlip` but did not name the supporting types. A state-flip lock needs to know WHICH tuner it is waiting on and the target value (in the vm's display unit) to detect the flip.
- **Fix:** Added `FineTuneTuner` (one entry per tunable) and `PendingStateFlip(tuner, target)`; the holder's `reached()` maps each tuner to its reduced value with the same scaling the vm exposes. Documented in-code.
- **Commit:** `b43c3eb`

Otherwise the plan executed as written. (The `dp` import was missing from the three group screens on first compile — a trivial Rule-3 blocking-import fix, folded into the Task-3 commit before it landed.)

## Notes for 17-06

- Wire the route: `FineTuneHubScreen(onNavigate)` → on `MOTION`/`EXTRUSION` push the group screen; `ExtrusionScreen(onFwRetraction)` → push `FwRetractionScreen`. Build ONE `FineTuneHolder` per spine (re-keyed on rebuild, mirror the Calibration/Extrude holder construction) and feed it to all four screens so the state-flip busy lock is shared.
- The on-device UAT covers Motion + Extrusion ONLY (FW-retraction is build-blind, gated off on both dev printers — do NOT expect a FW-retraction gate). The SC-1 babystep-style sign check does not apply here.
- The full-suite-green gate is 17-06's; this plan verified compile + holder test + grep gates only (REVIEW #1).

## Self-Check: PASSED

All 9 created files + the modified test exist on disk; all three task commits (`b43c3eb`, `fc6523f`, `f19c9c1`) are present in git history.
