---
status: partial
phase: 26-adjustment-screens
source: [26-VERIFICATION.md]
started: 2026-06-11T00:30:00Z
updated: 2026-06-11T00:30:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. On-device approval of all rebuilt screens (SC-1)
expected: Fine-Tune (flat list), Temperature (morphing Focus), Outputs (detail-in-Focus), Extrude (FootButtonBar + numeric IME + filament presets), macro numeric params, and Spool measured-weight all render and operate correctly on flox in BOTH portrait and landscape; owner approves the rebuilt surfaces.
result: [pending]

### 2. Outputs scrubber cross-output command routing (CR-04 fix)
expected: Drag fan A's scrubber, switch selection to fan B, drag again — the wire command targets fan B (not the previously selected output); drag preview keeps updating after the first echo-driven reseed.
result: [pending]

### 3. Temperature adjuster live-target stepping (CR-01 fix)
expected: Repeated +5 taps on a heater accumulate (200→205→210…) and the hero value tracks the live readout; no repeated dispatch of the same target.
result: [pending]

### 4. Off-heater step-up (CR-02 fix)
expected: An idle/off heater can be heated from the adjuster — stepper seeds from live temperature when target is null and ± controls are enabled.
result: [pending]

### 5. WR-08 fw-retraction glyph assignment (owner decision)
expected: Owner assigns four distinct registry glyphs for Retract Length / Retract Speed / Unretract Extra / Unretract Speed (currently OutputCircle/MaxVelocity/MaxAccel each appear twice on the Fine-Tune flat list when a fw-retraction printer is connected). Claude must NOT pick these.
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
