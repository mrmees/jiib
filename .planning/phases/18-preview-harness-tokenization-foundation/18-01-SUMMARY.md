---
phase: 18-preview-harness-tokenization-foundation
plan: 01
subsystem: build-tooling + preview-foundation
tags: [preview-harness, tokenization, pseudolocale, lint-gate, compile-scaffold]
requires: []
provides:
  - "en-XA/ar-XB pseudolocale generation on the debug build (SC-3c)"
  - "verified-correct + documented compose-ui-tooling-preview build wiring (SC-4a)"
  - "3 day-one-compiling compile scaffolds (SC-2 / SC-3b / SC-4b) the building plans convert"
  - "recorded SC-3 automated-lint-gate downgrade (deferred to Phase 22)"
affects:
  - app/build.gradle.kts
  - app/src/test/java/works/mees/dinghy/preview/SampleFixturesTest.kt
  - app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt
  - app/src/test/java/works/mees/dinghy/shell/StartDestMappingTest.kt
tech-stack:
  added: []   # NO new dependency — detekt fallback fired (see below)
  patterns:
    - "isPseudoLocalesEnabled on the debug build type (AGP pseudolocale, build-time only)"
    - "compile scaffolds (NOT RED tests): assert current facts + // TODO(18-NN) pending markers"
key-files:
  created:
    - app/src/test/java/works/mees/dinghy/preview/SampleFixturesTest.kt
    - app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt
    - app/src/test/java/works/mees/dinghy/shell/StartDestMappingTest.kt
  modified:
    - app/build.gradle.kts
    - .planning/phases/18-preview-harness-tokenization-foundation/18-VALIDATION.md
decisions:
  - "Task-1 lint-gate decision = detekt-baseline with pseudolocale fallback; Task-3 verification FAILED → FALLBACK FIRED (no detekt wired, SC-3 automated lint deferred to Phase 22)"
  - "compose-ui-tooling-preview stays implementation (RESEARCH Q1 verified leave-as-is), not debugImplementation — commented to prevent a future 'fix'"
metrics:
  duration: ~25 min
  completed: 2026-06-06
---

# Phase 18 Plan 01: Preview/Tokenization Foundation (build wiring + pseudolocale + lint-gate + compile scaffolds) Summary

en-XA pseudolocale wired on debug, the `-preview` classpath confirmed-and-commented, three day-one-compiling compile scaffolds landed, and the SC-3 hardcoded-literal lint gate **downgraded** because neither verified Compose detekt ruleset actually ships a hardcoded-string rule under this toolchain — the documented fallback fired cleanly and the phase is NOT blocked.

## What Was Built

### Task 2 — build wiring confirmed + pseudolocale (SC-4a, SC-3c)
- Added `debug { isPseudoLocalesEnabled = true }` to `app/build.gradle.kts` → generates the `en-XA`
  (accented) + `ar-XB` (RTL+bracketed) pseudolocales on the debug build only. Build-time capability,
  no translations shipped, no resConfigs to trim (project has none). This is the SC-3c i18n
  completeness-sweep mechanism (switch device locale to English (XA); plain-English text that shows
  through = a still-hardcoded literal).
- Added an explaining comment on `implementation(libs.compose.ui.tooling.preview)` documenting WHY it
  must stay on `main`'s compile classpath (the `@Preview` annotations are referenced by `@Preview`
  functions that live in `main`; moving it to `debugImplementation` would break the module compile).
  It is an inert annotations jar R8 strips from release. This is RESEARCH Q1's **verified leave-as-is**
  verdict — SC-4a is *confirmed correct + documented*, not changed.
- Verification: `:app:assembleDebug` → BUILD SUCCESSFUL (exit 0).
- Commit: `c30aa57`.

### Task 3 — lint-gate decision executed (FALLBACK) + 3 compile scaffolds (SC-2/SC-3b/SC-4b)
- **Lint gate — the Task-1 decision and its fallback:** the owner's recorded decision was
  **detekt-baseline with `detekt-fallback-to-pseudolocale` as the Task-3 escape hatch**. Task 3
  *verified* the candidate rulesets before wiring anything:
  - `io.nlopez.compose.rules:detekt:0.4.22` (mrmans0n) — resolved from Maven Central, rule classes
    inspected: ONLY Compose API-convention rules (naming, modifier, parameter-order, unstable
    collections). **No hardcoded-string / `Text("…")` rule.**
  - `ru.kode:detekt-rules-compose:1.4.0` (appKODE) — resolved + inspected: modifier/ordering/event-
    handler rules. **No hardcoded-string rule either.**
  - The platform `HardcodedText` lint is XML-only (RESEARCH Q7) — it does not see Compose literals.
  No verified ruleset exposes a working Compose hardcoded-literal check under Kotlin 2.1.21 / AGP 8.7,
  so per the explicit fallback branch **no detekt was wired** (verified zero residue: no plugin, no
  catalog entry, no `config/detekt/`). SC-3's automated "can't-regress" lint sub-criterion is
  **DEFERRED to Phase 22**; the `en-XA` pseudolocale sweep (Task 2) is the manual completeness check
  in its place. **This fallback firing is a SUCCESS, not a failure** — recorded in `18-VALIDATION.md`
  and here.
- **3 compile scaffolds** (NOT failing "RED" tests — they assert CURRENT facts about EXISTING typed
  symbols and pass today, each carrying a `// TODO(18-NN):` pending marker the building plan converts
  to a live assertion):
  - `preview/SampleFixturesTest.kt` — asserts the 4/6 `PrintStatusMode` states + `TerminalKind` exist
    and are distinct, and a default `FineTuneVm()` is all-absent/null. TODO(18-02) → `SampleFixtures`.
  - `designsystem/icons/DinghyIconsTest.kt` — placeholder invariant for the icon-token contract.
    TODO(18-03) → `DinghyIcons` / `IconRef` / `DinghyIcon`.
  - `shell/StartDestMappingTest.kt` — asserts the real `Dest` enum carries `Dest.FineTune` and
    `Dest.valueOf` safe-parses. TODO(18-04) → the pure `parseStartDest` mapping fn.
  - **Compile-day-one rule honored** ([[dinghy-wave0-red-scaffold-compile.md]]): every not-yet-built
    symbol is referenced ONLY in a comment, never a live import — so the whole test sourceset compiles
    and per-wave `--tests` filtering is never bricked.
- Verification: `:app:compileDebugUnitTestKotlin` → BUILD SUCCESSFUL (whole test sourceset compiles);
  the three scaffolds run GREEN via `:app:testDebugUnitTest --tests …`.
- Commit: `9c6b6e3`.

## Deviations from Plan

None affecting scope. The detekt route was NOT adopted, but that is the **planned, owner-decided
fallback branch**, not a deviation — Task 1's decision explicitly reserved `detekt-fallback-to-
pseudolocale` for exactly the "no compatible Compose hardcoded-string rule verifies" outcome that
occurred. No auto-fixes (Rules 1–3) were needed.

## Decision Recorded (Task 1 checkpoint resolution)

**Owner decision (Codex-reviewed during planning):** detekt-baseline + fallback-to-pseudolocale.
**Runtime outcome:** the fallback **fired** — neither `io.nlopez.compose.rules:detekt` nor
`ru.kode:detekt-rules-compose` ships a hardcoded-string rule under Kotlin 2.1.21 / AGP 8.7, so the
scoped pseudolocale-only SC-3 downgrade is in effect (automated lint gate → Phase 22). The `en-XA`
pseudolocale ships regardless (Task 2). Recorded in `18-VALIDATION.md` (frontmatter context +
"SC-3 lint-gate downgrade — FALLBACK FIRED" note).

## Known Stubs

None. The three new test files are intentional **compile scaffolds** (a documented Wave-0 pattern),
not stubs hiding missing functionality — each asserts a real current fact and is wired to be converted
by 18-02/03/04. No empty data flowing to UI, no placeholder rendering.

## Verification Summary

- `:app:assembleDebug` — exit 0 (pseudolocale + wiring).
- `:app:compileDebugUnitTestKotlin` — exit 0 (full test sourceset compiles).
- 3 scaffolds via `:app:testDebugUnitTest --tests …` — exit 0 (pass on current facts).
- detekt residue grep — clean (no plugin/catalog/config).

## Self-Check: PASSED
- app/build.gradle.kts — FOUND (modified, `isPseudoLocalesEnabled` present)
- app/src/test/java/works/mees/dinghy/preview/SampleFixturesTest.kt — FOUND
- app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt — FOUND
- app/src/test/java/works/mees/dinghy/shell/StartDestMappingTest.kt — FOUND
- Commit c30aa57 — FOUND
- Commit 9c6b6e3 — FOUND
