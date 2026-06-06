---
phase: 16-home-print-status-redesign
plan: 01
subsystem: testing
tags: [red-scaffold, wave-0, print-status, babystep, glance-sensor, preheat, gcode-builders, reducer]
requires:
  - "PrinterState / PrintState enum (existing)"
  - "MacroPrefsTest DataStore harness (existing analog)"
  - "PrinterCommandsTest / PrinterStateReducerTest (existing, extended)"
provides:
  - "RED gate: PrintStatusModeTest (4-mode classifier + babystep gating/step-cycle) -> 16-02"
  - "RED gate: PreheatTest (selectPreheatPath DirectTemps/OpenSelector, never-0 guard) -> 16-02"
  - "RED gate: GlanceSensorTest (selectGlanceSensor MCU>host>first, stable, empty->null) -> 16-04"
  - "RED gate: PrinterCommandsTest setGcodeOffsetZAdjust + SDCARD_RESET_FILE cases -> 16-03"
  - "RED gate: PrinterStateReducerTest homing_origin[2]->gcodeZOffset cases -> 16-04"
  - "RED gate: BabystepPrefsTest (defaults/fail-safe/write-through/coerce) -> 16-05"
affects:
  - "16-02 (classifier + preheat), 16-03 (gcode builders), 16-04 (glance sensor + reducer), 16-05 (BabystepPrefs)"
tech-stack:
  added: []
  patterns:
    - "Wave-0 RED scaffold compile discipline: fail()/literal bodies only, no unbuilt-symbol refs"
    - "Verbatim DataStore harness copy from MacroPrefsTest for the BabystepPrefs persistence gate"
key-files:
  created:
    - app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusModeTest.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/PreheatTest.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/GlanceSensorTest.kt
    - app/src/test/java/works/mees/dinghy/ui/settings/BabystepPrefsTest.kt
  modified:
    - app/src/test/java/works/mees/dinghy/command/PrinterCommandsTest.kt
    - app/src/test/java/works/mees/dinghy/state/PrinterStateReducerTest.kt
decisions:
  - "All scaffolds use fail()/literal bodies (no unbuilt-symbol refs) so the whole testDebug sourceset compiles day-one ([[dinghy-wave0-red-scaffold-compile]])"
  - "gcodeZOffset reducer case left as fail() because the field doesn't exist until 16-04 — parses the frame with existing helpers for compile-proof, asserts via fail()"
metrics:
  duration: ~10 min
  completed: 2026-06-06
---

# Phase 16 Plan 01: Wave-0 RED Test Scaffolds Summary

Established the six Phase-16 Wave-0 RED test scaffolds (four new files + two extended) that gate
Waves 1–3 — every later task now has a concrete `--tests` command to turn GREEN — with the whole
`:app:testDebugUnitTest` sourceset compiling day-one (no unbuilt-symbol references).

## What Was Built

- **PrintStatusModeTest** (new, 10 cases): the 4-mode classifier contract — Printing/Paused/Terminal
  (Complete|Cancelled|Error)/Standby; the CRITICAL `Standby-stays-Standby-on-stale-filename` behavior
  change vs `derivePrintStatusControls`; classifier-reads-printState-only (klippy shutdown ≠ Terminal);
  babystep gating (null layer hides / `<=` threshold shows / `>` hides, no time fallback); the fixed
  `.02→.05→.10→.15→.20` step cycle. Gates **16-02**.
- **PreheatTest** (new, 5 cases): the pure `selectPreheatPath(spoolPresent, nozzle?, bed?)` contract —
  DirectTemps when spool+temps, per-temp guard (missing temp is `null` NEVER `0`), OpenSelector when
  spool absent or both temps null. Gates **16-02**.
- **GlanceSensorTest** (new, 5 cases): the pure `selectGlanceSensor(Map)` contract — MCU > host > first
  by stable key order; SAME map → SAME choice across calls (the partial-diff-flip unit guard, T-16-01-D);
  empty → null. Gates **16-04**.
- **PrinterCommandsTest** (extended, +3 cases): `setGcodeOffsetZAdjust(±step)` signed-delta exact string,
  off-grid-snap-to-nearest-{0.02..0.20} (V5 validation guard, T-16-01-V5), `SDCARD_RESET_FILE` literal
  const. Gates **16-03**.
- **PrinterStateReducerTest** (extended, +2 cases): `gcode_move.homing_origin[2]` → new
  `PrinterState.gcodeZOffset`; short-array yields null/retains-prior (no OOB crash). Gates **16-04**.
- **BabystepPrefsTest** (new, 5 cases): DataStore persistence — defaults (enabled=true/layers=5),
  IOException fail-safe-to-defaults, `setEnabled`/`setLayerCount` write-through, `setLayerCount(0)`
  coerce ≥1. Harness copied verbatim from MacroPrefsTest. Unit-gates the write-scope-cancellation
  recurring trap. Gates **16-05**.

## Verification

- Task 1: `:app:testDebugUnitTest --tests PrintStatusModeTest --tests PreheatTest --tests GlanceSensorTest`
  → sourceset compiled, **20/20 RED** (as designed).
- Task 2: `:app:testDebugUnitTest --tests PrinterCommandsTest --tests PrinterStateReducerTest`
  → sourceset compiled, **5 new cases RED**, existing 42 cases stayed **GREEN**.
- Task 3: `:app:testDebugUnitTest --tests BabystepPrefsTest` → sourceset compiled, **5/5 RED**.
- Unbuilt-symbol grep: zero code references to `classifyPrintStatus`/`PrintStatusMode`/`selectPreheatPath`/
  `PreheatPath`/`selectGlanceSensor`/`GlanceSensor`/`BabystepPrefs(`/`setGcodeOffsetZAdjust`/
  `SDCARD_RESET_FILE`/`.gcodeZOffset` — all hits are KDoc/comments/class-names only.

Each filtered Gradle run compiles the WHOLE `testDebug` sourceset before applying the `--tests` filter,
so the three passing compilations (across all six scaffolds present) prove the central Wave-0 risk —
a non-compiling scaffold bricking every per-wave run — is not present.

## Commits

- `75dee50` test(16-01): PrintStatusMode/Preheat/GlanceSensor RED scaffolds (Task 1)
- `f985a75` test(16-01): PrinterCommands + reducer RED extensions (Task 2)
- `acf83f5` test(16-01): BabystepPrefsTest RED scaffold (Task 3)

## Deviations from Plan

None — plan executed exactly as written. All scaffolds RED, sourceset compiles, no unbuilt-symbol
references.

## Threat Model Compliance

- **T-16-01-V5** (gcode-builder input validation): the SET_GCODE_OFFSET cases ENCODE the
  canonicalize-against-{0.02..0.20} + off-grid-snap + no-free-text contract 16-03 must satisfy. ✅
- **T-16-01-D** (partial-diff staleness): the GlanceSensor stability case ENCODES the diff-order-stable
  MCU>host>first preference 16-04's `selectGlanceSensor` must satisfy. ✅
- No package installs this phase (Android/Gradle, no new deps). ✅

## Self-Check: PASSED

- Files exist: PrintStatusModeTest.kt, PreheatTest.kt, GlanceSensorTest.kt, BabystepPrefsTest.kt
  (created); PrinterCommandsTest.kt, PrinterStateReducerTest.kt (modified). ✅
- Commits exist: 75dee50, f985a75, acf83f5. ✅
