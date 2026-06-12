---
status: partial
phase: 27-motion-calibration
source: [27-VERIFICATION.md]
started: 2026-06-12T05:45:00Z
updated: 2026-06-12T05:45:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Scan overlay Back path (post-CR-01 fix)
expected: Spool → open Scan → press system Back → scan overlay closes; Spool screen is still there (no nav pop underneath)
result: [pending]

### 2. Prompt overlay Back path (post-CR-01 fix)
expected: With a macro prompt visible over a drilled-down screen → press system Back → prompt dismisses; the screen underneath does NOT pop
result: [pending]

### 3. Prompt during Active probe session (the D-09 failure mode CR-01 targeted)
expected: During an Active probe routine (klicky prompt mid-routine), press system Back → probe session survives; route is NOT popped. Requires a live printer running a klicky probe flow.
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
