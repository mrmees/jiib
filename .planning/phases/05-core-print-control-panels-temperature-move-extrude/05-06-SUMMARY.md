---
phase: 05-core-print-control-panels-temperature-move-extrude
plan: 06
subsystem: ui
tags: [move, jog, homing, gcode, dispatcher, confirm-guard, theme-tokens]

# Dependency graph
requires:
  - phase: 05-01
    provides: PrinterState.gcodePosition (offsets-stripped Move source) + lowercase homedAxes + GCODE_SCRIPT method
  - phase: 05-02
    provides: PrinterCommands.jog/homeXY/homeAll/homeAxis/overrideJog/DISABLE_STEPPERS/scriptParams
provides:
  - "MoveHolder + MoveVm: live gcode X/Y/Z (nullable, never fabricated) + per-axis homed gating (allHomed)"
  - "MoveScreen: 3×3 jog pad + Z row + 6-up distance selector + Home/Disable/Back gutter, all dispatching via GCODE_SCRIPT"
affects: [05-08-move-uat, shell-routing-to-Move-dest]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Holder mirrors PrintStatusHolder: consumes the store's already-throttled flow, NO second throttle"
    - "Value-on-glyph corner cells (live value over a large axis letter colored green=homed/amber=unhomed)"
    - "Single script() dispatch helper funnels every action through GCODE_SCRIPT + scriptParams (no raw rpc)"
    - "Per-axis jog gating off vm.<axis>Homed; amber Override is the only unhomed-jog path (overrideJog)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/move/MoveHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
    - app/src/test/java/works/mees/dinghy/ui/move/MoveHolderTest.kt
  modified: []

key-decisions:
  - "Distance set follows the LAW mockup (0.1/1/10/25/50/100), NOT the plan body's 0.1/1/5/10/50/100 — 04-move.png is captured-verbatim LAW (D-03) and the plan body's list was a stale paraphrase."
  - "Override cell targets ONE unhomed axis by X→Y→Z priority; inert (dim) when all axes are homed (nothing to override)."
  - "Z homing lives on the Z corner cell (tap-to-home), so the Z-row center is a passive readout — keeps homing affordance consistent with the X/Y corners."

requirements-completed: [MOVE-01, MOVE-02, MOVE-03, MOVE-04]

# Metrics
duration: 11min
completed: 2026-06-01
---

# Phase 5 Plan 06: Move Panel Summary

**The Move panel (MOVE-01..04) — a 3×3 jog pad + Z row + fixed distance selector + Home/Disable/Back gutter, reproducing 04-move.png; live gcode X/Y/Z with per-axis green/amber homed gating, every motion command routed through the dispatcher as `printer.gcode.script`, and Disable gated behind the full-screen ConfirmGuard.**

## What Was Built

**Task 1 — MoveHolder (TDD):**
- `MoveHolder` + `MoveVm(x,y,z: Double?, xHomed/yHomed/zHomed/allHomed: Boolean)`. On each `store.printerState` emission it reads `state.gcodePosition` (`getOrNull(0/1/2)` → x/y/z; **null** per-axis when unreported — never a fabricated 0, Pitfall 1) and derives per-axis homed from `'x'/'y'/'z' in state.homedAxes` (lowercase, RESEARCH §3). `allHomed = x&&y&&z`. Does NOT read `toolheadPosition`.
- No second throttle (consumes the store's 250ms conflation, mirrors PrintStatusHolder).
- RED→GREEN with committed gates: `47da821` (test) → `e3df42a` (feat). 3 host tests: seeded `[12.5,40,3.2]`+`"xy"` (x/z values, x/y homed, z not, allHomed false), null position (null x/y/z), `"xyz"` (allHomed true).

**Task 2 — MoveScreen:**
- 3×3 jog pad mirroring the mockup exactly: `[Y corner][↑ Y+][Override]` / `[← X−][XY home][→ X+]` / `[Z corner][↓ Y−][X corner]`. Each cell a sacred `aspectRatio(1f)` square.
  - Edge arrows → `jog("X"/"Y", ±distance, FEED_XY)` (MOVE-01).
  - Center → `homeXY()` (G28 X Y — XY ONLY, never full G28; D-03 / MOVE-02).
  - Axis corners render the live X/Y/Z value over a large axis letter colored `t.go` (homed) / `t.heat` (amber, unhomed); tapping homes that axis → `homeAxis(a)` (MOVE-02 / MOVE-04).
  - Amber Override cell (`Intent.Warn`) jogs the current unhomed axis via `overrideJog(axis, ±distance)` (SET_KINEMATIC_POSITION X=0 Y=0 Z=0 + relative move). Normal jog of an unhomed axis is gated off; Override is the only escape (D-03).
- Field: Z row (`∧` Z+, live-Z value-on-glyph center, `∨` Z−) + a fixed 6-up distance selector (0.1/1/10/25/50/100 mm; active step accent-outlined, default 10).
- Gutter: Home (`Intent.Accent`, `homeAll()`/G28) · Disable (`Intent.Warn`) · Back (`Intent.Danger`, `onBack`).
- Disable raises the full-screen `ConfirmGuard(destructive=false)` (amber proceed-at-peril) → on confirm dispatches `DISABLE_STEPPERS` (M84).
- Every action funnels through one `script()` helper → `dispatcher.dispatch(key, GCODE_SCRIPT, scriptParams(PrinterCommands.*))` — no raw rpc. In-flight keys disable their control (T-05-06-T). A `DispatchEvent.Failure` (e.g. a gcode error from an Override) surfaces a `SeverityToast`.
- Committed `4452c67` (feat).

## Verification

- `:app:testReleaseUnitTest --tests *MoveHolderTest` — green (3 cases).
- `:app:compileReleaseKotlin` — BUILD SUCCESSFUL.
- Full `:app:testReleaseUnitTest` suite — BUILD SUCCESSFUL, no regressions.
- `grep "Color(0x" MoveScreen.kt` — empty (token-pure, THEME-01).
- Acceptance greps confirmed: `ConfirmGuard(` + `destructive = false`; center→`homeXY()`, gutter Home→`homeAll()`, corner→`homeAxis()`; Override→`overrideJog()`; all via `GCODE_SCRIPT` + `scriptParams`; corners color by per-axis homed (`t.go`/`t.heat`).

Live jog/home/disable + the `overrideJog` unhomed-axis sequence on flox + live Ender 5 Plus are 05-08 UAT items (per the plan's verification note — not closed here).

## TDD Gate Compliance

Task 1 followed RED → GREEN with committed gates (`47da821` test, `e3df42a` feat). Task 2 was a non-TDD compile-gated UI task per the plan (`type="auto"`, no `tdd`). No unexpected RED-phase pass.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Plan-vs-LAW conflict] Distance selector uses the mockup's values, not the plan body's**
- **Found during:** Task 2
- **Issue:** The plan body lists the distance set as `0.1/1/5/10/50/100`, but the LAW mockup `04-move.png` (captured verbatim, D-03, "reproduce it, do not re-litigate") shows `0.1/1/10/25/50/100`.
- **Fix:** Used the mockup's set (`DISTANCES = 0.1/1/10/25/50/100`). The mockup is the authority; the plan-body list was a stale paraphrase.
- **Files modified:** `MoveScreen.kt`
- **Commit:** `4452c67`

No other deviations.

## Known Stubs

None — every cell is wired to a real `PrinterCommands` gcode through the dispatcher; live values come from `MoveHolder.vm`. The Override cell is intentionally inert only when all axes are homed (nothing to override), which is correct behavior, not a stub.

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/ui/move/MoveHolder.kt` — FOUND
- `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` — FOUND
- `app/src/test/java/works/mees/dinghy/ui/move/MoveHolderTest.kt` — FOUND
- Commits present: `47da821` (test), `e3df42a` (feat), `4452c67` (feat) — all in git log

---
*Phase: 05-core-print-control-panels-temperature-move-extrude*
*Completed: 2026-06-01*
