---
phase: 08-macros-console-functional-core-complete
plan: 03
subsystem: command
tags: [macros, security, gcode-injection, asvs-v5, datastore, kotlin, regex, mainsail]

# Dependency graph
requires:
  - phase: 08-01
    provides: RED test scaffolds (MacroParamParserTest/MacroInvocationTest/MacroPrefsTest) + real probed fixture macro_bodies_e5.json
  - phase: 05-02
    provides: PrinterCommands.scriptParams + the "never concatenate a user string into a script" invariant this sanitizer restores
provides:
  - MacroParamParser.parseMacroParams (verbatim Mainsail two-pass regex, D-09) + MacroModels (MacroParam/MacroVm)
  - MacroInvocation.build REJECT-on-forbidden-char sanitizer + typed MacroParamRejected (the phase's load-bearing V5 gcode-injection gate, block_on:high)
  - MacroPrefs DataStore (bookmarks Set<String> + revealHidden Boolean, MACRO-03 underscore-default-hide)
affects: [08-06 (MacroExecutionPopup consumes build + MacroParam), 08-07 (AppContainer wires the production macros.preferences_pb)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure host-testable transform (no I/O/Compose) — parser + sanitizer mirror PrinterCommands/DeriveCapabilities discipline"
    - "REJECT-on-forbidden-char (not escape) for the one D-10-sanctioned alpha-keyboard injection site"
    - "Injected-DataStore prefs with fail-safe read (ConnectionStore shape), own .preferences_pb"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/macros/MacroModels.kt
    - app/src/main/java/works/mees/dinghy/ui/macros/MacroParamParser.kt
    - app/src/main/java/works/mees/dinghy/command/MacroInvocation.kt
    - app/src/main/java/works/mees/dinghy/ui/macros/MacroPrefs.kt
  modified: []

key-decisions:
  - "MacroInvocation REJECTS (throws typed MacroParamRejected) on any forbidden char — never escapes/strips/truncates — because Klipper quote-parsing is unconfirmed; escaping an unverified-semantics char is the insecure path"
  - "Forbidden set = \\r \\n ; \\t embedded-\" + all ASCII control 0x00-0x1F/0x7F; the embedded double-quote is rejected (would break out of KEY=\"...\" grammar)"
  - "MacroInvocation does NOT clamp numerics — numeric range-clamping is owned by NumpadPage in 08-06; build() string-quotes all values, buildTyped() exists for already-clamped unquoted numerics"
  - "Mainsail regex is reproduced VERBATIM, not 'improved' — the real-fixture quirk (trailing |float after |default(60) -> type=null) is preserved per the RED test's ground truth"
  - "MacroParamRejected is a top-level class in command/ (not nested) to match the test's bare reference and avoid a name clash"

patterns-established:
  - "REJECT-on-forbidden-char string sanitizer for user free-text crossing into a gcode line"
  - "Heuristic, total (never-throwing) macro-body param parser"

requirements-completed: [MACRO-02, MACRO-03]

# Metrics
duration: 14min
completed: 2026-06-02
---

# Phase 8 Plan 03: Pure Macro Layer + gcode-injection Security Gate Summary

**Verbatim Mainsail two-pass macro-param parser, a REJECT-on-forbidden-char gcode-injection sanitizer (the phase's block_on:high V5 gate), and a DataStore for macro bookmarks/reveal-hidden — MACRO-02/MACRO-03 backend complete.**

## Performance

- **Duration:** ~14 min
- **Started:** 2026-06-02
- **Completed:** 2026-06-02
- **Tasks:** 3
- **Files created:** 4

## Accomplishments
- `MacroParamParser` reproduces Mainsail's exact two-pass `getMacroParams` regex (D-09), proven GREEN against the REAL probed Ender 5 macro bodies — including the real quirk that a trailing `|float` after `|default(60)` parses to `type=null`.
- `MacroInvocation.build` is the load-bearing V5 security gate: REJECTS (typed `MacroParamRejected`) any string value containing `\r \n ; \t " ` or any ASCII control char 0x00-0x1F/0x7F — never escapes/strips/truncates. Restores the `PrinterCommands` no-user-string-concat invariant for the single D-10-sanctioned alpha-keyboard site.
- `MacroPrefs` (own `macros.preferences_pb`, injected DataStore, fail-safe reads) persists `bookmarks: Set<String>` + `revealHidden: Boolean` (default false = underscore-default-hide, MACRO-03).

## Task Commits

Each task committed atomically (TDD turning pre-existing RED scaffolds GREEN):

1. **Task 1: MacroModels + MacroParamParser (D-09, MACRO-02)** - `05ddde8` (feat)
2. **Task 2 [BLOCKING]: MacroInvocation REJECT-on-forbidden-char sanitizer (ASVS V5, block_on:high, D-10)** - `4aff289` (feat)
3. **Task 3: MacroPrefs DataStore round-trip (MACRO-03)** - `186d52e` (feat)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/ui/macros/MacroModels.kt` - `MacroParam(name/type/default)` + `MacroVm` data classes.
- `app/src/main/java/works/mees/dinghy/ui/macros/MacroParamParser.kt` - Pure verbatim Mainsail `PARAM_REGEX` + `PARAM_IN_REGEX`, two-pass linkedMap first-seen dedup; total function.
- `app/src/main/java/works/mees/dinghy/command/MacroInvocation.kt` - REJECT-on-forbidden-char sanitizer + gcode-line assembly + top-level `MacroParamRejected`.
- `app/src/main/java/works/mees/dinghy/ui/macros/MacroPrefs.kt` - Injected-DataStore prefs (bookmarks + revealHidden) with fail-safe reads.

## Decisions Made
See key-decisions frontmatter. Most load-bearing: the sanitizer **rejects** rather than escapes (Klipper quote-parsing unconfirmed → escaping is the insecure path), and the embedded `"` is in the forbidden set so a value can never break out of the `KEY="..."` grammar. Clamping numerics is deliberately NOT this file's job (NumpadPage owns it in 08-06).

## Deviations from Plan

None of substance — all three pre-existing RED scaffolds turned GREEN exactly as specified. Two minor implementation notes (not behavioral deviations):

1. **API surface matched to the RED tests, not the plan prose.** The plan described `build(macroName, params: List<Pair<String,String>>)` and `toggleBookmark`. The committed RED tests (the authoritative contract) call `build(macroName, params: Map<String,String>)` and `MacroPrefs.addBookmark(name)` / `setRevealHidden(bool)` / `bookmarks` / `revealHidden` flows. Implemented to the tests. `toggleBookmark`/`removeBookmark` and a `buildTyped` (unquoted-numeric) variant added as forward conveniences for 08-06 — no extra behavior on the tested paths.
2. **`MacroParamRejected` is a top-level class** in `works.mees.dinghy.command` (not nested in `MacroInvocation`) so the test's bare `MacroParamRejected::class.java` resolves and there is no simple-name clash. Cosmetic.

**Total deviations:** 0 functional. **Impact:** none — behavior is exactly what the RED tests + threat model demand.

## Issues Encountered
- The full test source set will not compile until ALL sibling RED symbols exist (per 08-02). To prove this plan's three tests GREEN against the real Gradle build, the still-RED sibling test files referencing not-yet-built symbols were temporarily set aside: `ConsoleHolderTest.kt`, `GcodeStoreParseTest.kt` (08-04/05 symbols), `MacroHolderTest.kt` (08-06 `MacroHolder`). They were **restored afterward** — the final working tree contains only this plan's four new production files; no sibling production or test file was modified.

## User Setup Required
None.

## Next Phase Readiness
- MACRO-02/MACRO-03 backend + the V5 injection gate are complete and host-proven.
- 08-06 (MacroExecutionPopup) can now call `MacroInvocation.build(...)` and route through `PrinterCommands.scriptParams` + `dispatcher.dispatch`; numeric clamping there via `NumpadPage` range.
- 08-07 must create the production `macros.preferences_pb` in DinghyApp and expose `macroPrefs` via AppContainer.
- Multi-write DataStore atomicity for `MacroPrefs` is deferred to on-device (Windows build host cannot reliably host-test back-to-back writes — same constraint as ConnectionStore per STATE.md 04-01).

## Self-Check: PASSED
- All four created files present on disk (verified).
- Commits `05ddde8`, `4aff289`, `186d52e` present in git log.
- `:app:testReleaseUnitTest --tests *MacroParamParserTest* *MacroInvocationTest* *MacroPrefsTest*` → BUILD SUCCESSFUL (all GREEN) against the real Windows Gradle build.
- Working tree clean; no sibling file modified; no accidental deletions.

---
*Phase: 08-macros-console-functional-core-complete*
*Completed: 2026-06-02*
