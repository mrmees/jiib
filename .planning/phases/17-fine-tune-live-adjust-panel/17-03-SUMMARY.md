---
phase: 17-fine-tune-live-adjust-panel
plan: 03
subsystem: state
tags: [wave-1, fine-tune, reducer, readback, config-baseline, tdd-green]
requires:
  - "17-01 Wave-0 RED reducer stubs (PrinterStateReducerTest) pinning the five Fine-Tune reducer behaviors"
  - "Existing PrinterState / PrinterStateReducer / DeriveCapabilities / PrinterStateStore / MoonrakerSession spine"
provides:
  - "Nullable Fine-Tune readback fields on PrinterState + FirmwareRetractionObject model"
  - "Null-safe diff-merge reducer walks for toolhead limits / extruder PA+smooth / fan.speed / firmware_retraction"
  - "firmware_retraction in V1_SUBSCRIBE_CORE (build-blind, intersect-guarded)"
  - "10 config-baseline one-shot StateFlows + setters (TUNE-05 reset source of truth) wired at handshake"
affects:
  - "17-04 (UI tiles read the new fields), 17-05 (holder scales raw->display + no-op reset on null baseline), 17-06 (nav + final full-suite gate)"
tech-stack:
  added: []
  patterns:
    - "Separate extruder-keyed reducer walk for PA/smooth_time (NOT the per-heater merge loop — they are not per-heater concepts)"
    - "Field-by-field merge-onto-retained for firmware_retraction (mirrors bed_mesh/manual_probe — partial diff retains omitted fields)"
    - "Reducer stores RAW ratio/units; scaling deferred to the display boundary (Pitfall 1)"
    - "Config baselines read ONCE at handshake from the SAME configfile query (Pitfall 3 — no second query); smooth-time from pressure_advance_smooth_time, NOT smooth_time (Pitfall 2)"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/state/PrinterState.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt
    - app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
    - app/src/test/java/works/mees/dinghy/state/PrinterStateReducerTest.kt
decisions:
  - "PA/smooth_time read in a SEPARATE extruder walk, not the heater loop — cleaner since they are not per-heater concepts (RESEARCH)."
  - "Reducer stores RAW values (minimum_cruise_ratio 0.5, fan.speed 0.0..1.0); NO scaling math in the reducer (Pitfall 1) — the holder converts at display."
  - "Smooth-time RESET baseline reads the CONFIG key pressure_advance_smooth_time, NOT the live status field smooth_time (Pitfall 2) — config key != status field."
  - "Speed %/Flow %/part-cooling fan deliberately have NO baseline StateFlow: speed/flow reset = protocol-neutral M220/M221 S100; Klipper [fan] has no persistent configured speed (RESEARCH A2/Open-Q1). Documented in-code; owner confirms part-fan no-reset at UAT."
  - "firmware_retraction subscribed unconditionally in V1_SUBSCRIBE_CORE — build-blind on both dev printers; the intersect loop drops it when absent (safe)."
metrics:
  duration: ~6 min
  completed: 2026-06-06
---

# Phase 17 Plan 03: Fine-Tune State Layer Summary

Built the Fine-Tune state layer: nullable readback fields + `FirmwareRetractionObject`, null-safe diff-merge reducer walks for toolhead motion limits / extruder PA+smooth / part-fan speed / firmware_retraction, `firmware_retraction` added to the subscribe core, and the 10 config-baseline one-shot StateFlows + handshake reads that source the long-press reset (TUNE-05). Turned the five 17-01 reducer RED stubs GREEN.

## What Was Built

**Task 1 — readback fields + reducer walks + subscribe-set** (`6e7271e`):
- `PrinterState`: 7 new nullable `Double?` fields (`maxVelocity`, `maxAccel`, `minimumCruiseRatio`, `squareCornerVelocity`, `pressureAdvance`, `smoothTime`, `partFanSpeed`) + `firmwareRetraction: FirmwareRetractionObject?` and the new `FirmwareRetractionObject` data class (retractLength/retractSpeed/unretractExtraLength/unretractSpeed, all nullable). Null = unreported (D-20 readout honesty — never fabricate a 0).
- `PrinterStateReducer`: extended the EXISTING `toolhead` walk with the four motion-limit reads; added a SEPARATE `extruder`-keyed walk for `pressure_advance`/`smooth_time` (NOT the per-heater loop); added a `fan` walk for `speed`; added a field-by-field `firmware_retraction` merge-onto-retained block (mirrors bed_mesh/manual_probe so a partial diff retains omitted fields). All RAW — no scaling (Pitfall 1).
- `DeriveCapabilities`: added `"firmware_retraction"` to `V1_SUBSCRIBE_CORE` (build-blind; the intersect loop guards absence).
- `PrinterStateReducerTest`: converted the five 17-01 `fail()` stubs to typed assertions (toolhead limits RAW, extruder PA/smooth from the separate walk, fan.speed raw 0.6, synthetic firmware_retraction, diff-merge retention for both toolhead and firmware_retraction); removed the now-unused `fail` import.

**Task 2 — config-baseline one-shot StateFlows + handshake reads** (`7d08ebd`):
- `PrinterStateStore`: 10 nullable baseline StateFlows + setters (`baselineMaxVelocity/MaxAccel/MinCruise/Scv/PressureAdvance/SmoothTime` + the four `baselineRetract*/Unretract*`), modeled on the `minExtrudeTemp` seam. Documented in-code that speed/flow/part-fan have NO baseline.
- `MoonrakerSession`: added `store.setBaseline*` reads INSIDE the existing configfile `runCatching` block (no second query, Pitfall 3): `printer.{max_velocity,max_accel,minimum_cruise_ratio,square_corner_velocity}`, `extruder.pressure_advance`, `extruder.pressure_advance_smooth_time` (the CONFIG key, ⚠ Pitfall 2), and `firmware_retraction.*` (build-blind). Added a `doubleOrNullAt` JsonObject helper alongside `floatOrNullAt`.

## Verification

- **Task 1:** `:app:testDebugUnitTest --tests PrinterStateReducerTest` → BUILD SUCCESSFUL (exit 0); all five Fine-Tune reducer behaviors GREEN plus the pre-existing reducer suite. Grep gates pass: reducer shows the `minimum_cruise_ratio`/`pressure_advance`/`firmware_retraction` walks; `DeriveCapabilities` `firmware_retraction` count = 3 (>= 1); NO scaling math (`* 100`/`/ 255`) in the reducer.
- **Task 2:** `:app:testDebugUnitTest --tests HandshakeTest --tests ConflationTest` → BUILD SUCCESSFUL (exit 0). These are the directly-touched store/session tests (no `PrinterStateStoreTest`/`MoonrakerSessionTest` exist — `ConflationTest` exercises the store, `HandshakeTest` the configfile handshake; substituted per the plan's "substitute the actual class names if they differ"). Grep gates pass: `pressure_advance_smooth_time` read count = 1 (the CONFIG key, not the status field); 10 `setBaseline*` setters present; NO `baselineFan/Speed/Flow` StateFlow; exactly ONE `configfile` query in the handshake.
- Per REVIEW #1, did NOT run the full host suite — the 17-01 holder/nav RED stubs stay RED until Waves 2–3 (full-suite-green is the 17-06 gate). The whole unit-test sourceset COMPILED clean in both runs.

## Deviations from Plan

None - plan executed exactly as written. The only substitution was test class names (`HandshakeTest`/`ConflationTest` for the plan's placeholder `MoonrakerSessionTest`/`PrinterStateStoreTest`), which the plan explicitly anticipated.

## Notes for Building Waves

- 17-05 holder: scale `minimumCruiseRatio` (raw 0.0..1.0) and `partFanSpeed` (raw 0.0..1.0) to a percentage at the DISPLAY boundary; convert the +tap back to a ratio for the wire (REVIEW #9). When a baseline StateFlow is null (firmware_retraction on dev printers, or any absent config section) the reset for that tuner MUST be a NO-OP (REVIEW #3) — this plan provides the nullable baselines; 17-05 enforces the no-op.
- firmware_retraction is build-blind on both dev printers — proven via the synthetic fixture here; do NOT expect an on-device UAT gate for FW-retraction (Motion + Extrusion only).

## Self-Check: PASSED

All 6 modified files exist on disk; both task commits (`6e7271e`, `7d08ebd`) are present in git history.
