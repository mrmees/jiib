---
phase: 08-macros-console-functional-core-complete
plan: 02
subsystem: console
tags: [console, severity, filters, scrollback, ring-buffer, pure-layer, tdd, CONS-02]

# Dependency graph
requires:
  - phase: 08-macros-console-functional-core-complete
    plan: 01
    provides: "RED scaffolds ConsoleSeverityTest/ConsoleFiltersTest + real-probed fixtures; ConsoleLine shape (rawMessage/severity/timeEpoch)"
provides:
  - "ConsoleLine — headless immutable raw-line model (rawMessage prefix-preserved, severity, timeEpoch?)"
  - "ConsoleSeverity.classify — pure total prefix->tier classifier (D-02), never throws"
  - "ConsoleFilters — 3 verbatim Mainsail @76fcbd2 filter regexes + pure VIEW-layer apply() that never mutates input (D-03/D-04)"
  - "ConsoleScrollback — object-typed @Synchronized bounded ring of ConsoleLine (cap 1000), NOT render/RingBuffer (D-02, Pitfall 1)"
affects: [08-04, 08-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure host-testable console layer mirroring state/DeriveCapabilities.kt + command/PrinterCommands.kt discipline (no I/O, no Compose)"
    - "Object-typed bounded ring = @Synchronized capped ArrayDeque (RingBuffer eviction idiom, ConsoleLine type)"
    - "Single-RED-wave verification: sibling RED symbols block the test-module link until 08-03..06 land, so GREEN proven via real Gradle with sibling RED files temporarily set aside + restored"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/console/ConsoleLine.kt
    - app/src/main/java/works/mees/dinghy/ui/console/ConsoleSeverity.kt
    - app/src/main/java/works/mees/dinghy/ui/console/ConsoleFilters.kt
    - app/src/main/java/works/mees/dinghy/state/ConsoleScrollback.kt
    - app/src/test/java/works/mees/dinghy/state/ConsoleScrollbackTest.kt
  modified: []

key-decisions:
  - "ConsoleFilters.apply parameter names follow the RED test (hideTemperatures/hideTimelapse/hidePrompt), NOT the plan-action shorthand (hideTemps) — the committed test is authoritative"
  - "ConsoleScrollback uses a capped ArrayDeque<ConsoleLine> (addLast + removeFirst over cap) — the simplest correct object-typed form per RESEARCH, not a hand-rolled index ring"
  - "GREEN proven by real Gradle: the single RED wave (08-01) put ALL phase test stubs in the same compile unit, so :app:testReleaseUnitTest cannot LINK until 08-03/04/05/06 land MacroInvocation/MacroParamParser/MacroHolder/MacroPrefs/parseGcodeStore/ConsoleHolder. Verified this plan's 3 tests GREEN by temporarily setting the 6 sibling RED files aside, running the real suite, then restoring them (no sibling file modified)"

requirements-completed: []  # CONS-02 is the pure-LAYER half here; full CONS-02 GREEN completes when ConsoleHolder (08-05) + UI land

# Metrics
duration: 18min
completed: 2026-06-02
---

# Phase 8 Plan 02: Console Pure Layer (model, classifier, filters, bounded ring) Summary

**The headless, host-testable console core for CONS-02 — `ConsoleLine` (raw prefix-preserved model), `ConsoleSeverity.classify` (pure prefix→tier, total/never-throws), `ConsoleFilters` (the 3 verbatim Mainsail noise regexes applied at the VIEW layer only, never mutating the raw list — D-04), and `ConsoleScrollback` (an object-typed `@Synchronized` bounded ring of `ConsoleLine`, explicitly NOT the FloatArray `RingBuffer`).**

## Performance
- **Duration:** ~18 min
- **Completed:** 2026-06-02
- **Tasks:** 2 (both TDD)
- **Files:** 5 created (4 production + 1 test)

## Accomplishments
- **Task 1 — model + classifier + filters (GREEN):** `ConsoleLine` data class matching the committed RED-test/fixture shape; `ConsoleSeverity.classify` mapping `!! `→ERROR, `// action:`→ACTION, `// debug:`→DEBUG, `// `→WARNING, else→NORMAL (total — empty/`ok`/`//nospace`→NORMAL); `ConsoleFilters` with the three verbatim fork regexes (`HIDE_TEMPERATURES`, the six-element `HIDE_TIMELAPSE` list, `HIDE_PROMPT_COMMANDS`) and a pure `apply()` returning a filtered VIEW that never touches the input list (D-04 raw-survives invariant).
- **Task 2 — bounded ring (GREEN):** `ConsoleScrollback` — a `@Synchronized` capped `ArrayDeque<ConsoleLine>` (default cap 1000 = Moonraker `gcode_store_size`): `push` O(1) evict-oldest, `replaceAll` clear+fill keeping the newest cap entries (the backfill REPLACE per D-02/Mainsail Option A), `snapshot` defensive copy oldest→newest, `clear`. Object-typed — does NOT touch/import `render/RingBuffer` (FloatArray type wall, RESEARCH Pitfall 1).
- **Tests GREEN (real Gradle):** `ConsoleSeverityTest` 6/6, `ConsoleFiltersTest` 5/5, `ConsoleScrollbackTest` 5/5 — 0 failures, 0 errors.

## Verification Evidence
- `:app:compileReleaseKotlin` → BUILD SUCCESSFUL (production code well-formed).
- `:app:testReleaseUnitTest --tests *ConsoleSeverityTest* --tests *ConsoleFiltersTest* --tests *ConsoleScrollbackTest*` → BUILD SUCCESSFUL with the 6 sibling RED files temporarily set aside; JUnit XML: ConsoleScrollbackTest tests=5/skipped=0/failures=0/errors=0, ConsoleFiltersTest 5/0/0/0, ConsoleSeverityTest 6/0/0/0. Sibling RED files restored immediately after; `git status` clean afterward.

## Deviations from Plan

### Auto-fixed / clarified
**1. [Rule 1 - Naming] `ConsoleFilters.apply` parameter names**
- **Found during:** Task 1.
- **Issue:** The plan action text wrote `hideTemps`, but the committed RED `ConsoleFiltersTest` calls `apply(raw, hideTemperatures = true, hideTimelapse = false, hidePrompt = false)` with named args.
- **Fix:** Used `hideTemperatures` / `hideTimelapse` / `hidePrompt` to match the authoritative committed test. No behavior change.

**2. [Process] Cross-plan test-link dependency (single RED wave)**
- **Found during:** Task 1 verify.
- **Issue:** 08-01 landed ALL phase test scaffolds in one compile unit. `:app:testReleaseUnitTest` cannot LINK (compile the test source set) until 08-03/04/05/06 land their production symbols (`MacroInvocation`/`MacroParamRejected`/`MacroParamParser`/`MacroHolder`/`MacroPrefs`/`parseGcodeStore`/`ConsoleHolder`). The plan's `--tests` filter selects which tests RUN, not which COMPILE — so the raw verify command reports BUILD FAILED on unrelated sibling symbols.
- **Resolution:** Confirmed zero compile errors reference THIS plan's symbols (ConsoleLine/ConsoleSeverity/ConsoleFilters/ConsoleScrollback), then proved the 3 owned tests GREEN by temporarily setting the 6 sibling RED files aside, running the real Gradle suite, and restoring them. This is expected behavior of the project's single-Nyquist-RED-wave architecture (08-01 SUMMARY), not a defect.

**Total functional deviations:** 0. The plan executed as written; the parameter-name nit follows the committed test.

## Threat Model Compliance
- **T-08-02-T (Tampering — classify over untrusted text):** mitigated. `classify` is a total `when` over `startsWith` checks with an `else -> NORMAL` fallthrough — no parsing, no throw path; `ConsoleSeverityTest.emptyOrNoPrefix_neverThrows_mapsToNormal` covers empty / `ok` / `//nospace`.
- **T-08-02-D (DoS — filter regexes):** accepted as designed. Only the 3 FIXED built-in anchored regexes ship; no user-supplied regex this phase.
- **T-08-02-SC (supply chain):** zero new packages — composition over the existing pinned stack.

## Known Stubs
None. All four production files are fully implemented (no placeholder returns, no TODO data sources). The console UI/holder that consumes this layer is intentionally a later plan (08-05) and out of this plan's scope.

## Self-Check: PASSED
- Created files present on disk: ConsoleLine.kt, ConsoleSeverity.kt, ConsoleFilters.kt, ConsoleScrollback.kt, ConsoleScrollbackTest.kt — all verified.
- Commits present: `295f094` (Task 1 console pure layer), `b3dfa7b` (Task 2 ring + test) — verified in git log.
- Tests GREEN: ConsoleSeverityTest 6/6, ConsoleFiltersTest 5/5, ConsoleScrollbackTest 5/5 (JUnit XML, 0 failures).

---
*Phase: 08-macros-console-functional-core-complete*
*Completed: 2026-06-02*
