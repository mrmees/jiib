---
phase: 16-home-print-status-redesign
plan: 02
subsystem: ui-printstatus
tags: [classifier, print-status-mode, babystep, preheat, control-model, behavior-change, pure-fn]
requires:
  - "16-01 RED gates: PrintStatusModeTest (10) + PreheatTest (5)"
  - "16-03: PrinterCommands.BABYSTEP_STEPS (single source of truth) — consumed, not redefined"
  - "PrinterState / PrintState enum, KlippyState (existing)"
provides:
  - "classifyPrintStatus(PrinterState) -> PrintStatusMode (4-state, printState-only) -> 16-06"
  - "babystepVisible / nextBabystepStep pure helpers -> 16-06 babystep row"
  - "selectPreheatPath(spoolmanPresent, nozzle?, bed?) -> PreheatPath (per-temp guard, never 0) -> 16-06"
  - "PrintStatusControlAction Preheat/Dismiss/Power + per-mode gutter sets -> 16-06 PrintStatusUiModel"
  - "Standby-stays-Standby behavior change (masquerade branch deleted)"
affects:
  - "16-06 (consumes classifier, selectPreheatPath, and per-mode control sets to build PrintStatusUiModel)"
tech-stack:
  added: []
  patterns:
    - "Pure host-testable classifier as a function of one state field (printState only) — separate axis from klippy lifecycle"
    - "Sealed PreheatPath result: per-temp nullable carry (missing temp stays null, never 0)"
    - "Per-mode gutter control-set builders (private list-fns) mirroring the existing activeControls discipline"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusMode.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/Preheat.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusControlModel.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusModeTest.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/PreheatTest.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusControlModelTest.kt
decisions:
  - "nextBabystepStep snaps an off-grid `current` to the nearest BABYSTEP_STEPS member before advancing, so it can never strand the cycle on a non-member value"
  - "Per-mode gutter sets were adjusted now (not deferred) so 16-06's PrintStatusUiModel reuses the CORRECT sets (Paused = no E-Stop, Terminal = Dismiss+Reprint) — the 'keep shapes' note in the plan refers to the pure list-builder structure, not the legacy labels"
  - "Screen runAction/controlColor got minimal stub branches for Preheat/Dismiss/Power so the exhaustive `when`s compile; the real spool-aware Preheat + SDCARD_RESET_FILE Dismiss wiring lands in 16-06 per plan"
metrics:
  duration: ~25 min
  completed: 2026-06-06
---

# Phase 16 Plan 02: PrintStatusMode Classifier + Preheat Selection + Extended Control Sets Summary

Built the locked core move of Phase 16: the ONE pure `PrintStatusMode` classifier (the phase's
central architectural intent), the pure spool-aware `selectPreheatPath` decision, and the extended
per-mode gutter control sets — turning the 16-01 RED `PrintStatusModeTest` (10) and `PreheatTest` (5)
fully GREEN while keeping the entire host suite passing. The deliberate behavior change —
**Standby is ALWAYS Standby, no terminal masquerade on a leftover filename** — lives here and is
locked by a passing test.

## What Was Built

- **`PrintStatusMode.kt` (new, pure — no Compose).**
  - `sealed interface PrintStatusMode { Standby; Printing; Paused; Terminal(kind) }` +
    `enum TerminalKind { Complete, Cancelled, Error }`.
  - `classifyPrintStatus(state)` maps all 6 `PrintState` values to the 4 modes reading ONLY
    `state.printState`. The Standby branch reads NO `printFilename`/`lastJob`/`restartFilename` (the
    behavior change vs `PrintStatusControlModel.kt:67`), and the classifier ignores `klippyState` (a
    klippy shutdown/error does NOT manufacture Terminal — separate axis).
  - `babystepVisible(settingEnabled, currentLayer, layerThreshold)` — null layer hides (NO time
    fallback), `<= threshold` shows, `> threshold` hides, setting-off hides.
  - `nextBabystepStep(current)` advances the cycle by REFERENCING the single canonical
    `PrinterCommands.BABYSTEP_STEPS` (16-03) — never redefines it; snaps off-grid input to the
    nearest member before advancing, wraps `0.20 -> 0.02`.
- **`Preheat.kt` (new, pure — no Compose, no fetch, no dispatch).**
  - `sealed interface PreheatPath { DirectTemps(nozzle: Int?, bed: Int?); OpenSelector }`.
  - `selectPreheatPath(spoolmanPresent, nozzleTemp, bedTemp)`: `DirectTemps` when Spoolman present
    AND at least one temp non-null, carrying each temp AS-IS (a missing temp stays `null`, NEVER
    coerced to 0 — the per-temp guard that prevents an accidental cooldown of an un-set heater);
    `OpenSelector` when Spoolman absent OR both temps null. Decides the PATH only — 16-06 dispatches
    per-temp `setHeater` / opens `PresetSelector`.
- **`PrintStatusControlModel.kt` (extended).**
  - `PrintStatusControlAction` gained `Preheat` (accent), `Dismiss` (neutral, terminal clear), and
    `Power` (inert red stop-intent chrome, D-04).
  - **Standby masquerade branch DELETED** — `PrintState.Standby -> standbyControls()`
    unconditionally (no `if (restartFilename != null) terminalControls(...)`). Restart-from-idle
    moves to the Standby launcher Files tile (16-06), not the gutter.
  - Per-mode gutter sets now match the UI-SPEC binding contract: **Standby** = Preheat + Power (no
    E-Stop); **Printing** = Tune + Pause + E-Stop (unchanged `activeControls`); **Paused** = Tune +
    Resume + Cancel (NO E-Stop, new `pausedControls`); **Terminal** = Dismiss + Reprint (no Stop).
  - The existing `PrintStatusPendingAction` debounce machinery (`clearPrintStatusPendingAction`) is
    reused untouched.
- **`PrintStatusScreen.kt` (minimal compile-keep).** Added stub `runAction` branches
  (Preheat/Dismiss/Power → no-op for now) and `controlColor` intents (Preheat→accent, Power→stop,
  Dismiss→neutral) so the exhaustive `when`s compile after the enum extension. The real spool-aware
  Preheat + `SDCARD_RESET_FILE` Dismiss wiring lands in 16-06 (Wave 3) per plan.

## Verification

- Task 1: `:app:testDebugUnitTest --tests PrintStatusModeTest` → **BUILD SUCCESSFUL** (10/10 GREEN).
  `PrintStatusMode.kt` grep-clean of `androidx.compose`; `grep 'val BABYSTEP_STEPS' app/src/main`
  returns exactly one hit (in `PrinterCommands.kt`).
- Task 2: `:app:testDebugUnitTest --tests PreheatTest` → **BUILD SUCCESSFUL** (5/5 GREEN).
  `Preheat.kt` grep-clean of compose/dispatch/fetch (the only hits are KDoc).
- Task 3: full `ui.printstatus.*` host suite (7 classes, explicit `--tests`) → **BUILD SUCCESSFUL**;
  full `:app:testDebugUnitTest` → **BUILD SUCCESSFUL** (no regression across the existing host
  tests); `:app:assembleDebug` → **BUILD SUCCESSFUL** (screen compiles with the extended enum).
- `PrintStatusControlAction` contains Preheat/Dismiss/Power; the Standby branch no longer calls
  `terminalControls` on a non-null restartFilename (masquerade removed).

## Commits

- `744ce8b` feat(16-02): PrintStatusMode classifier + babystep gating/step-cycle (pure) (Task 1)
- `07992c7` feat(16-02): pure selectPreheatPath spool-aware Preheat selection (Task 2)
- `65be6e4` feat(16-02): extend PrintStatusControlModel — Preheat/Dismiss/Power + per-mode gutters (Task 3)

## Deviations from Plan

### [Rule 3 - Blocking] Screen exhaustive `when`s required compile-keep branches for the new actions

- **Found during:** Task 3
- **Issue:** `PrintStatusScreen.kt` has two exhaustive `when (action)` / `when (control.tapAction)`
  blocks (`runAction` @188, `controlColor` @810). Adding `Preheat`/`Dismiss`/`Power` to the enum
  made both non-exhaustive → compile blocker. The plan defers the screen's full Preheat/Dismiss
  wiring + final colors to Wave 3 (16-06), but the screen must still compile NOW.
- **Fix:** Added minimal `runAction` no-op branches (Preheat/Dismiss/Power) and `controlColor`
  intents (Preheat→accentLine, Power→stop, Dismiss→hair). Real wiring lands in 16-06.
- **Files modified:** PrintStatusScreen.kt
- **Commit:** 65be6e4

### [Rule 1 - Behavior-change test update] Updated PrintStatusControlModelTest for the new per-mode gutters

- **Found during:** Task 3
- **Issue:** The existing `PrintStatusControlModelTest` cases asserted the OLD behavior the plan
  deliberately changes: Standby+lastJob → Files/Restart/Stop (the masquerade), Terminal →
  Files/Restart/Stop, Paused → Tune/Resume/Stop. These are not frozen "no-regression" assertions —
  they encode the exact behavior this plan retires.
- **Fix:** Rewrote the affected cases to the new contract (Standby → Preheat/Power even with a
  lastJob; Terminal → Dismiss/Reprint; Paused → Tune/Resume/Cancel with no E-Stop), made
  `assertControls` variadic, and updated the pending-restart label to "Reprinting". All host tests
  GREEN.
- **Files modified:** PrintStatusControlModelTest.kt
- **Commit:** 65be6e4

## Threat Model Compliance

- **T-16-02-V5** (input validation on classifier + preheat + control derivation): all three are pure
  functions of typed inputs (`PrinterState`, `Int?` spool temps). No user-string interpolation, no
  gcode emitted here. `selectPreheatPath` returns a PATH only — the V5-relevant command builders
  live in 16-03 and the per-temp `setHeater` dispatch (never 0) in 16-06. The per-temp null guard is
  locked by `PreheatTest`. ✅
- **T-16-SC** (package installs): none this phase. ✅

## Known Stubs

- `PrintStatusScreen.runAction` Preheat/Dismiss/Power branches are intentional no-ops (Power is inert
  by D-04; Preheat/Dismiss get their real wiring in 16-06 Wave 3). Documented and bounded by the plan
  — NOT goal-blocking for 16-02, whose goal is the pure classifier/selection/control-set seams.

## Self-Check: PASSED

- Files exist: PrintStatusMode.kt, Preheat.kt (created); PrintStatusControlModel.kt,
  PrintStatusScreen.kt, PrintStatusModeTest.kt, PreheatTest.kt, PrintStatusControlModelTest.kt
  (modified). ✅
- Commits exist: 744ce8b, 07992c7, 65be6e4. ✅
- `classifyPrintStatus` + `babystepVisible` + `nextBabystepStep` in PrintStatusMode.kt;
  `selectPreheatPath` + `PreheatPath` in Preheat.kt; `Preheat`/`Dismiss`/`Power` in the enum;
  Standby masquerade branch removed. ✅
