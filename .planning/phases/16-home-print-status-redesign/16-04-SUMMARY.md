---
phase: 16-home-print-status-redesign
plan: 04
subsystem: state
tags: [reducer, glance-sensor, babystep, gcode-z-offset, temperature-sensor, partial-diff-fix]
requires:
  - "16-01 RED gates: PrinterStateReducerTest homing_origin[2] cases + GlanceSensorTest"
  - "PrinterState / PrinterStateReducer / DeriveCapabilities (existing)"
provides:
  - "PrinterState.gcodeZOffset (applied Z offset readback, SC-5) -> 16-06 babystep readout"
  - "PrinterState.temperatureSensors retained map (update-on-present, retain-on-absent) -> 16-06 Standby glance"
  - "pure selectGlanceSensor(Map) -> GlanceSensor? (MCU>host>first, stable, empty->null) -> 16-06"
  - "temperature_sensor dynamic subscribe rule"
affects:
  - "16-06 (Standby screen consumes gcodeZOffset + selectGlanceSensor(state.temperatureSensors))"
tech-stack:
  added: []
  patterns:
    - "Retained-map reducer accumulation (mirrors heater loop): update-on-present, retain-on-absent — partial-diff staleness fix"
    - "Pure host-testable selector decoupled from UI (selectGlanceSensor), stable sorted-key order"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/printstatus/GlanceSensor.kt
  modified:
    - app/src/main/java/works/mees/dinghy/state/PrinterState.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt
    - app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt
    - app/src/test/java/works/mees/dinghy/state/PrinterStateReducerTest.kt
    - app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/GlanceSensorTest.kt
decisions:
  - "Glance temp modelled as a RETAINED Map<String,Double> + pure selectGlanceSensor, NOT a single field — a single field selected from the raw diff flips MCU->host/chamber or goes stale under partial diffs (T-16-04-D)"
  - "selectGlanceSensor orders by sorted key (toSortedMap), so the choice is diff-order-independent; mcu/host match is case-insensitive (contains)"
  - "gcodeZOffset uses getOrNull(2) for null-safety on a short homing_origin array — no OOB crash"
  - "machine.proc_stats host-load NOT pulled in — deferred to Phase 19 (Pitfall 4); keeps P16 no-new-backend boundary"
metrics:
  duration: ~8 min
  completed: 2026-06-06
---

# Phase 16 Plan 04: Net-New State Sources (Z-offset readback + retained glance temp) Summary

Added the two net-new Moonraker-derived state sources Phase 16 needs — the applied Z-offset readback
(`gcode_move.homing_origin[2]` → `PrinterState.gcodeZOffset`, SC-5 babystep) and the Standby glance
temperature modelled as a RETAINED `temperatureSensors` map + a pure `selectGlanceSensor` — closing the
partial-diff sensor-flip class without pulling a new backend surface into the phase.

## What Was Built

- **Task 1 — applied Z-offset readback.** New nullable `PrinterState.gcodeZOffset` field + ONE null-safe
  line inside the existing `gcode_move` reducer walk:
  `gm.doubleListOrNull("homing_origin")?.let { s = s.copy(gcodeZOffset = it.getOrNull(2)) }`. `getOrNull(2)`
  yields null on a short/garbage array (no index-out-of-bounds). Turned the two Wave-0 RED reducer cases
  GREEN (value `0.125` from index 2; short `[0,0]` → null).
- **Task 2 — retained glance-temp state.**
  - **Retained map:** new `PrinterState.temperatureSensors: Map<String, Double>` + a reducer walk
    SEPARATE from the heater loop (`isHeaterObject` excludes temperature_sensor). It UPDATE-ON-PRESENT
    merges only sensors present in the current diff with a numeric `temperature` onto the prior map; an
    absent sensor is simply not touched, so its prior value is RETAINED. This is the partial-diff fix —
    the map is never rebuilt from the current diff alone.
  - **Subscribe rule:** `name.startsWith("temperature_sensor ") -> result += name` added to
    `deriveSubscribeSet`'s dynamic loop (mirrors the `heater_generic` clause).
  - **Pure selector:** new `ui/printstatus/GlanceSensor.kt` (plain Kotlin, NO Compose) —
    `data class GlanceSensor(name, temperature)` + `fun selectGlanceSensor(Map): GlanceSensor?` picking
    mcu > host > first by stable sorted-key order (case-insensitive `contains`), null on empty. The
    16-06 Standby screen calls `selectGlanceSensor(state.temperatureSensors)` — it never re-derives the
    preference from the raw diff.
  - Host-load (`machine.proc_stats`) intentionally NOT modelled (deferred to Phase 19, Pitfall 4).

## Verification

- Task 1: `:app:testDebugUnitTest --tests PrinterStateReducerTest` → BUILD SUCCESSFUL (homing_origin[2]
  value + short-array null cases GREEN; existing reducer cases stayed GREEN).
- Task 2: `:app:testDebugUnitTest --tests DeriveCapabilitiesTest --tests PrinterStateReducerTest
  --tests GlanceSensorTest` → BUILD SUCCESSFUL. Subscribe rule, retain-on-absent + update-on-present
  reducer cases, and all 5 GlanceSensor cases (mcu>host>first + stability + empty→null) GREEN.
- `proc_stats`/`machine.proc` grep over the diff: clean (deferral honored).
- `androidx.compose` grep in `GlanceSensor.kt`: clean (pure).

## Commits

- `f3ba015` feat(16-04): parse gcode_move.homing_origin[2] into PrinterState.gcodeZOffset (Task 1)
- `ab05af8` feat(16-04): retained temperature_sensor map + pure selectGlanceSensor (Task 2)

## Deviations from Plan

None — plan executed exactly as written. The partial-diff fix (retained map + pure stable selector) was
implemented per the revised plan, not as a single glance-temp field.

## Threat Model Compliance

- **T-16-04-V5** (input validation): all reads go through the existing null-safe `doubleListOrNull` /
  typed `doubleOrNullAt` walks; malformed/short/non-numeric payloads yield null and are skipped, never a
  crash or injected value. ✅
- **T-16-04-D** (partial-diff staleness): glance temp derived from a RETAINED map (update-on-present /
  retain-on-absent) via a stable-sorted-key selector — a partial diff cannot flip MCU→host or strand a
  stale value; unit-gated by `GlanceSensorTest` stability case + the reducer retain-on-absent test. ✅
- **T-16-SC** (package installs): none this phase. ✅

## Self-Check: PASSED

- Files exist: `GlanceSensor.kt` (created); `PrinterState.kt`, `PrinterStateReducer.kt`,
  `DeriveCapabilities.kt` + 3 test files (modified). ✅
- Commits exist: `f3ba015`, `ab05af8`. ✅
