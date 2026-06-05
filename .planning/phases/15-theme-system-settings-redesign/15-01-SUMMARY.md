---
phase: 15-theme-system-settings-redesign
plan: 01
subsystem: theme
tags: [color-engine, golden-test, oklch, validation-foundation, wave-0]
requires: []
provides:
  - "tools/color-golden/dump.js — regenerable Node oracle dumping golden vectors from sibling color.js"
  - "app/src/test/resources/color-golden.json — pinned in-repo golden fixture (7 vectors)"
  - "PaletteGoldenTest / PaletteMathTest / TokenBridgeTest — RED scaffolds (compile day-one)"
affects:
  - "plan 15-02 (Palette port turns PaletteGoldenTest + PaletteMathTest GREEN)"
  - "plan 15-03 (TokenBridge port turns TokenBridgeTest GREEN)"
tech-stack:
  added: []
  patterns:
    - "Golden-vector conformance: JS oracle (color.js) dumped to a committed JSON fixture, asserted bit-for-bit in Kotlin"
    - "Wave-0 RED scaffolds with fail() bodies + zero unbuilt-symbol refs (full test sourceset compiles day-one)"
key-files:
  created:
    - tools/color-golden/dump.js
    - app/src/test/resources/color-golden.json
    - app/src/test/java/works/mees/dinghy/theme/PaletteGoldenTest.kt
    - app/src/test/java/works/mees/dinghy/theme/PaletteMathTest.kt
    - app/src/test/java/works/mees/dinghy/theme/TokenBridgeTest.kt
  modified: []
decisions:
  - "edgeYellow golden seed = #c8b400 (OKLCH hue 101.35°); the plan's '~85°' was an approximation — the oracle output is the pinned contract"
  - "All 7 golden vectors generated with statusFromPool:true (D-01 ships the from-pool status language, not the JS default false)"
metrics:
  duration: 8 min
  completed: 2026-06-05
---

# Phase 15 Plan 01: Color-Engine Validation Foundation Summary

Committed a regenerable Node oracle (`dump.js`) that dumps 7 golden color vectors from the sibling `color.js`, pinned them in-repo as `color-golden.json`, and laid three RED test scaffolds (golden/math/bridge) that compile day-one and fail on assertions until the Kotlin `Palette`/`TokenBridge` port lands in waves 1–2.

## What Was Built

**Task 1 — Node oracle + pinned golden fixture (`b10a8e5`)**
- `tools/color-golden/dump.js`: `require`s `../theme_theory/app/color.js`, calls `Palette.generate(opts)` for 7 labeled vectors (`defaultDark`, `defaultLight`, `simple`, `highContrast`, `edgeRed`, `edgeYellow`, `shift120`), projects each to the golden field set (accent/secondary/surfaces/pool/poolHues/poolRoles/directional/status/minHueGap/poolShift), and writes `app/src/test/resources/color-golden.json`. Header comment documents it as a dev-time regenerable oracle, **not** a runtime/build dependency. All vectors use `statusFromPool:true` (D-01 canonical config).
- `app/src/test/resources/color-golden.json`: the committed fixture so CI never crosses the sibling boundary.
- Verified the `defaultDark` entry matches the RESEARCH-verified hexes exactly: accent `#3c75fb`, bg `#0b0b0b`, text `#e8e8e8`, pool `["#6895f4","#866200","#c575cc"]`, status.stop `#af3c3c`, minHueGap `60`. `shift120` accent stays `#3c75fb` (poolShift rotates the data pool, not chrome).

**Task 2 — three RED test scaffolds (`86e1090`)**
- `PaletteGoldenTest.kt` — class with 7 `@Test` methods (one per fixture key) + a private `loadFixture()` stub documenting the classpath-resource load the GREEN impl will use. No unbuilt-symbol refs.
- `PaletteMathTest.kt` — `oklchToSrgbRoundTrip_staysInGamut` + `gamutClamp_neverExceedsUnitInterval`.
- `TokenBridgeTest.kt` — `derivesInBetweenTiers_matchesDinghyJs` + `appliesPoolOverrides_atIndex`.
- Every body is `org.junit.Assert.fail("RED — …")`. `:app:compileDebugUnitTestKotlin` succeeded (full sourceset compiles — the load-bearing `[[dinghy-wave0-red-scaffold-compile]]` gate) and all 11 tests ran to RED `AssertionError`s (not compile errors, not "No tests found"). Each class listed explicitly in `--tests` (AGP glob gotcha honored).

## Verification

- `node tools/color-golden/dump.js` regenerates `color-golden.json` deterministically; the default-dark check (`accent #3c75fb / bg #0b0b0b / minHueGap 60`) prints `golden OK`.
- `:app:testDebugUnitTest --tests …PaletteGoldenTest --tests …PaletteMathTest --tests …TokenBridgeTest` (Windows Gradle): compileDebugUnitTestKotlin OK → 11 tests run → 11 RED assertion failures. BUILD FAILED is the expected RED state, not a compile failure.

## Deviations from Plan

None — plan executed as written. One clarifying note (not a deviation): the plan loosely cited the yellow edge-hue seed as "~85°"; the chosen seed `#c8b400` resolves to OKLCH hue 101.35° (still a yellow-ish seed near the reserved caution arc, the relevant edge case). The pinned oracle output is the authoritative contract — there is no fixed-target hex to match, the test will assert against whatever `color.js` emitted.

## Known Stubs

`loadFixture()` in `PaletteGoldenTest.kt` is an intentional documented stub for the RED phase (no body — it must not reference the unbuilt `Palette` types). The building plan 15-02 wires it. This is by design per the Wave-0 RED-scaffold pattern, not an unwired-data defect.

## Self-Check: PASSED
- FOUND: tools/color-golden/dump.js
- FOUND: app/src/test/resources/color-golden.json
- FOUND: app/src/test/java/works/mees/dinghy/theme/PaletteGoldenTest.kt
- FOUND: app/src/test/java/works/mees/dinghy/theme/PaletteMathTest.kt
- FOUND: app/src/test/java/works/mees/dinghy/theme/TokenBridgeTest.kt
- FOUND: commit b10a8e5 (Task 1)
- FOUND: commit 86e1090 (Task 2)
