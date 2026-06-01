---
phase: 05-core-print-control-panels-temperature-move-extrude
plan: 01
subsystem: state
tags: [state, reducer, capabilities, json-rpc, moonraker]
requires:
  - PrinterState/PrinterStateReducer (Phase 2 pure state layer)
  - Capabilities/deriveCapabilities (Phase 2)
  - JsonRpcMethods (Phase 2 net layer)
provides:
  - PrinterState.gcodePosition (MOVE-04 user-facing X/Y/Z source)
  - HeaterState.canExtrude (EXTR-04 cold-extrude gate data)
  - Capabilities.hasMacroIgnoreCase (EXTR-02 case-insensitive macro gating)
  - JsonRpcMethods.GCODE_SCRIPT + TEMPERATURE_STORE (Phase-5 action + graph-backfill methods)
affects:
  - every Phase-5 holder/screen that reads position, can_extrude, macro presence, or issues gcode/temperature-store calls
tech-stack:
  added: []
  patterns:
    - null-safe wire accessor (booleanOrNull mirrors doubleOrNullAt; runCatching, never !!)
    - merge-not-replace retention for the new can_extrude flag (STATE-01)
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/state/PrinterState.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt
    - app/src/main/java/works/mees/dinghy/state/Capabilities.kt
    - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
    - app/src/test/java/works/mees/dinghy/state/PrinterStateReducerTest.kt
    - app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt
decisions:
  - "gcode_position (offsets-stripped) is the Move panel source, distinct from raw toolheadPosition (Pitfall 1)."
  - "canExtrude defaults false as a fail-safe — a printer never reporting it reads as cannot-extrude (T-05-01-Safety)."
  - "Macro gating goes through hasMacroIgnoreCase, not raw membership — Moonraker lowercases macro object names (Pitfall 2)."
metrics:
  duration_min: 5
  completed: 2026-06-01
  tasks: 2
  files: 6
---

# Phase 5 Plan 01: State-Layer Field Additions Summary

Extended the pure headless state layer with the four live/derived fields every Phase-5 panel reads — `gcode_position` (MOVE-04 user-facing X/Y/Z), per-extruder `can_extrude` (EXTR-04 cold-extrude gate), a case-insensitive `hasMacroIgnoreCase` capability helper (EXTR-02), and the `GCODE_SCRIPT`/`TEMPERATURE_STORE` JSON-RPC method constants — all unit-proven via TDD with zero UI/I/O, settling the shared data contract before any downstream holder or screen consumes it.

## What Was Built

**Task 1 — gcode_position + can_extrude capture (TDD):**
- `PrinterState.gcodePosition: List<Double>? = null` from `gcode_move.gcode_position` `[X,Y,Z,E]` — the offsets-stripped, user-facing Move source (NOT `toolheadPosition`, Pitfall 1).
- `HeaterState.canExtrude: Boolean = false` from `extruder.can_extrude`, captured field-by-field in the heater merge loop. Defaults false as a fail-safe cold-extrude gate.
- New `booleanOrNull` null-safe accessor mirroring `doubleOrNullAt` (`runCatching`, never `!!`).
- Merge discipline (STATE-01): a temp-only extruder diff with no `can_extrude` key retains the prior flag.
- No subscribe-set change needed — `gcode_move` and `extruder` (all fields) were already in `V1_SUBSCRIBE_CORE`.

**Task 2 — hasMacroIgnoreCase + method constants (TDD):**
- `Capabilities.hasMacroIgnoreCase(name)` — `macros.any { it.equals(name, ignoreCase = true) }`. `deriveCapabilities` macro extraction left unchanged (keeps verbatim name); the case-insensitive comparison is the helper only.
- `JsonRpcMethods.GCODE_SCRIPT = "printer.gcode.script"` (all action gcodes) and `TEMPERATURE_STORE = "server.temperature_store"` (graph backfill).

## TDD Gate Compliance

Both tasks followed RED → GREEN with committed gates:
- Task 1: `f3e6fa2` (test, RED — compile-fail on missing fields) → `c83a0ea` (feat, GREEN).
- Task 2: `783d35a` (test, RED — unresolved helper) → `77eaf1b` (feat, GREEN).
No REFACTOR commits — implementation landed clean. No unexpected RED-phase passes.

## Verification

- `:app:testReleaseUnitTest --tests *PrinterStateReducerTest` — green (4 new cases: gcode_position capture, can_extrude true/false, temp-only retention).
- `:app:testReleaseUnitTest --tests *DeriveCapabilitiesTest` — green (2 new cases: lowercased-name match, empty-list false).
- Full `:app:testReleaseUnitTest` suite — BUILD SUCCESSFUL, no regressions across existing reducer/derivation/conflation/lifecycle tests.

## Deviations from Plan

None - plan executed exactly as written.

## Self-Check: PASSED

- `PrinterState.kt` contains `gcodePosition` ✓ and `HeaterState.canExtrude` ✓
- `Capabilities.kt` contains `hasMacroIgnoreCase` ✓
- `JsonRpc.kt` contains `GCODE_SCRIPT = "printer.gcode.script"` ✓ and `TEMPERATURE_STORE = "server.temperature_store"` ✓
- Commits present: f3e6fa2 ✓, c83a0ea ✓, 783d35a ✓, 77eaf1b ✓
- Full unit suite green ✓
