---
phase: 8
slug: macros-console-functional-core-complete
reviewer: Codex (gpt-5.x-codex via codex:rescue)
review_type: pre-execution plan review
date: 2026-06-02
verdict: NOT SAFE TO EXECUTE AS-IS — 2 blockers, 4 should-fix, 1 nice-to-have
---

# Phase 8 — Cross-AI Plan Review (Codex)

Second-opinion review of the 7-plan Phase 8 set, requested before execution. Codex read
CONTEXT/RESEARCH/PATTERNS/UI-SPEC + all 7 plans + the real reused source files and
sanity-checked the plans against the actual codebase. Findings below, by severity, verbatim.

## BLOCKERS (must fix before execution)

### B1 — `MacroPrefs` has no production DataStore wiring
`08-07-PLAN.md:7,78` + `08-03-PLAN.md:118` + `AppContainer.kt:48`

Plans 08-03 and 08-06 both depend on a `macros.preferences_pb` DataStore and a `MacroPrefs`
instance, but 08-07 (the integration wave) only touches theme + connection stores in
`AppContainer.kt:48-64` and `DinghyApp.kt:38-50`. There is **no task** that creates
`macroDataStore`, threads it into `AppContainer`, and exposes it to `MacroHolder`. This leaves
08-03/08-06 with a dangling dependency at integration time.

**Minimal fix:** add an explicit 08-07 task covering those three steps (create `macroDataStore`,
thread into `AppContainer`, expose to `MacroHolder`).

### B2 — Macro string-param sanitizer policy is too ambiguous to implement safely
`08-03-PLAN.md:94` + `08-01-PLAN.md:100` + `08-07-PLAN.md:109`

The plan allows "reject OR quote-and-escape" without choosing one, tests only newline and
`M112`, and leaves semicolon comment injection (`"; M112"`), quote-break inputs, tabs, and other
control chars unaddressed. Klipper's own quote-parsing behavior is not confirmed in RESEARCH.md.
An underspecified sanitizer will produce either an insecure implementation or a mid-wave design argument.

**Minimal fix:** lock the policy to **reject** (not escape); enumerate the exact forbidden
character set (`\r`, `\n`, `;`, `\t`, and all other control chars); add test cases for
`; SET_HEATER_TEMPERATURE`, `"; M112"`, embedded quotes, and multi-param truncation.

## SHOULD-FIX

### S1 — UI-SPEC references the wrong (unusable) backing structure
`08-UI-SPEC.md:23,92,138` + `RingBuffer.kt:23,30`

UI-SPEC still references `render/RingBuffer` for console backing, but that class is
`FloatArray`-only and cannot hold strings. The implementation plans (08-02) correctly call for a
new `ConsoleScrollback` / `ArrayDeque`-backed structure — so this is a stale artifact, not a plan
defect. **Fix:** update UI-SPEC to say `ConsoleScrollback`, not `render/RingBuffer`.

### S2 — Console live-append does a full 1000-item diff per line → Adreno-320 jank
`08-05-PLAN.md:81,105` + `JsonRpcClient.kt:68`

`notify_gcode_response` is unthrottled (confirmed in existing code) and 08-05 emits a full
snapshot to `ListAdapter.submitRows` after every single new line, so the differ runs up to
1000-item diffs continuously during an active print. On the Nexus 7's Adreno 320 this is the jank
source. **Fix:** use incremental `notifyItemInserted` + range removal for live appends; reserve
full submit/diff for backfill replace and filter toggles only.

### S3 — Stick-to-bottom timing is wrong
`08-05-PLAN.md:105,113`

Checking `lastVisible == itemCount - 1` *after* submitting the new list fails because `itemCount`
has already updated. **Fix:** capture `wasAtBottom` against the old adapter count *before* the
update, then scroll in the adapter commit callback only if `wasAtBottom`.

### S4 — Numeric clamp ownership is contradictory across three plans
`08-01-PLAN.md:100` + `08-03-PLAN.md:94,147` + `08-06-PLAN.md:107`

08-01 puts clamp assertions in `MacroInvocationTest`, 08-03 says numeric values are clamped
upstream, and 08-06 assigns the clamp to `NumpadPage`. These three will fight during integration.
**Fix:** pick one owner (recommend `NumpadPage` or the ViewModel, NOT the domain object) and
remove the conflicting assertions from `MacroInvocationTest`.

## NICE-TO-HAVE

### N1 — RESEARCH mislabels gcode_store as "deduplicated"
`08-RESEARCH.md:150,154,157`

Moonraker's gcode_store is a plain capped FIFO array; it does not deduplicate. Harmless because
the plans use full-replace semantics anyway, but the word "deduplicated" misleads. **Fix:** reword
to "capped FIFO cache; full replace avoids client-side append duplicates."

## What Codex confirmed is CORRECT (do not change)

- **Backfill = full replace** in both plans that touch it: `08-04-PLAN.md:100-110` calls
  `setGcodeBackfill(parseGcodeStore(...))` with an explicit "REPLACE, not append" acceptance
  criterion; `08-05-PLAN.md:81` uses `replaceRaw(snapshot)`. Correct.
- **D-04 raw-stream architecture intact:** 08-02 filters return a new filtered view without
  mutating the store; 08-05 stores raw lines and applies `ConsoleFilters` only at render time. No
  task mutates the backing store through the filter path.
- **Wave dependencies correct;** no circular dependencies; each wave's prerequisites satisfied by
  prior waves.
