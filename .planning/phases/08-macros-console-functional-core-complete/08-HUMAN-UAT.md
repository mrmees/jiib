---
status: partial
phase: 08-macros-console-functional-core-complete
source: [08-VERIFICATION.md, 08-REVIEW.md WR-03]
started: 2026-06-02
updated: 2026-06-02
---

## Current Test

[awaiting human testing — cold-connect macro popup race]

## Tests

### 1. Cold-connect parametered-macro popup (WR-03)
expected: On a COLD connect (just-launched app, before the `configfile` handshake read
lands — typically a sub-second window), open the Execution popup for a macro that DECLARES
params (e.g. START_PRINT / a LOAD_FILAMENT with `params.X|default(...)`). The popup must
show the param fields, NOT "No parameters — Execute runs this macro as-is". If it shows
"No parameters" and would dispatch the macro with none, the popup is freezing `MacroVm.params`
at tap time before the bodies arrive — a real defect (runs a parametered macro bare).
result: [pending]

## Summary

total: 1
passed: 0
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps
