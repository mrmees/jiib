---
status: passed
phase: 26-adjustment-screens
source: [26-VERIFICATION.md]
started: 2026-06-11T00:30:00Z
updated: 2026-06-11T01:05:00Z
---

## Current Test

[complete — owner tested on flox 2026-06-10]

## Tests

### 1. On-device approval of all rebuilt screens (SC-1)
expected: Fine-Tune (flat list), Temperature (morphing Focus), Outputs (detail-in-Focus), Extrude (FootButtonBar + numeric IME + filament presets), macro numeric params, and Spool measured-weight all render and operate correctly on flox in BOTH portrait and landscape; owner approves the rebuilt surfaces.
result: passed — owner: "the general transition to the new navigation structure came out very well." Finer-detail points + new formatting rules to be enumerated by owner as follow-up polish (not blockers; pending owner enumeration).

### 2. Outputs scrubber cross-output command routing (CR-04 fix)
expected: Drag fan A's scrubber, switch selection to fan B, drag again — the wire command targets fan B (not the previously selected output); drag preview keeps updating after the first echo-driven reseed.
result: passed

### 3. Temperature adjuster live-target stepping (CR-01 fix)
expected: Repeated +5 taps on a heater accumulate (200→205→210…) and the hero value tracks the live readout; no repeated dispatch of the same target.
result: passed (behavior note: rapid presses drop while awaiting echo — owner accepts for now; future option captured as todo to fire without waiting on this more-relaxed screen)

### 4. Off-heater step-up (CR-02 fix)
expected: An idle/off heater can be heated from the adjuster — stepper seeds from live temperature when target is null and ± controls are enabled.
result: passed

### 5. WR-08 fw-retraction glyph assignment (owner decision)
expected: Owner assigns four distinct registry glyphs for Retract Length / Retract Speed / Unretract Extra / Unretract Speed (currently OutputCircle/MaxVelocity/MaxAccel each appear twice on the Fine-Tune flat list when a fw-retraction printer is connected). Claude must NOT pick these.
result: deferred — owner marked as future cleanup todo (.planning/todos/pending/2026-06-10-fw-retraction-glyph-assignment.md)

## Summary

total: 5
passed: 4
issues: 0
pending: 0
skipped: 1
blocked: 0

## Gaps
