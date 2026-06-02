---
status: resolved
phase: 08-macros-console-functional-core-complete
source: [08-VERIFICATION.md, 08-REVIEW.md WR-03]
started: 2026-06-02
updated: 2026-06-02
resolution: resolved-by-fix (aeed483)
---

## Current Test

[resolved — see Test 1]

## Tests

### 1. Cold-connect parametered-macro popup (WR-03)
expected: On a COLD connect (just-launched app, before the `configfile` handshake read
lands — typically a sub-second window), open the Execution popup for a macro that DECLARES
params (e.g. START_PRINT / a LOAD_FILAMENT with `params.X|default(...)`). The popup must
show the param fields, NOT "No parameters — Execute runs this macro as-is". If it shows
"No parameters" and would dispatch the macro with none, the popup is freezing `MacroVm.params`
at tap time before the bodies arrive — a real defect (runs a parametered macro bare).
result: resolved-by-fix — commit aeed483 made the popup read params reactively from
holder.state and gate Execute behind a "Loading parameters…" state until params are
authoritative (MacroHolder.paramsKnown()), structurally removing the bare-dispatch path.
The manual race window is not reproducible by hand — the sub-second configfile read always
lands before a human can navigate to the macro popup (confirmed live by Matthew on flox),
which also shows the original bug's practical impact was negligible. Full release unit suite
GREEN post-fix. Accepted 2026-06-02.

## Summary

total: 1
passed: 1
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
