---
phase: 15-theme-system-settings-redesign
plan: 02
subsystem: theme
tags: [color-engine, oklch, palette-port, golden-test, host-pure, wave-1]
requires:
  - "15-01 (color-golden.json oracle fixture + RED PaletteGoldenTest/PaletteMathTest scaffolds)"
provides:
  - "theme/Palette.kt — pure host-testable OKLCH→sRGB palette generator (1:1 port of color.js), zero Android imports"
  - "internal Palette.hexToOklch / Palette.oklchToHex / Palette.OklchValue — the helper surface 15-03's TokenBridge derives tiers from"
  - "Palette.Generated value type (String hexes) — oracle-comparable generator output"
affects:
  - "plan 15-03 (TokenBridge ports dinghy.js tier derivation by calling the internal hexToOklch/oklchToHex helpers + an lShift)"
  - "plan 15-05 (sanitize layer feeds a validated seedHex into the now-total Palette.generate)"
tech-stack:
  added: []
  patterns:
    - "Host-pure transform core (zero Android/Compose imports) — mirrors state/PrinterStateReducer.kt"
    - "Double-throughout color math, narrow to Int/hex only at the rgbToHex boundary; /255.0 channel normalization (never Int division)"
    - "Golden-vector conformance: assert rendered hexes bit-for-bit; assert float-accumulated DIAGNOSTIC doubles (poolHues) via tight epsilon"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/theme/Palette.kt
  modified:
    - app/src/test/java/works/mees/dinghy/theme/PaletteGoldenTest.kt
    - app/src/test/java/works/mees/dinghy/theme/PaletteMathTest.kt
decisions:
  - "15-03 visibility contract = the internal-helper exposure (hexToOklch/oklchToHex/OklchValue marked `internal`), NOT moving the tier derivation into Palette — keeps Palette a clean 1:1 of color.js"
  - "poolHues asserted with a 1e-9 epsilon (not exact bit-equality): they are float-accumulated DIAGNOSTIC values whose seed hue diverges ~1e-13 between the JS oracle's atan2/cbrt and the JVM's; the RENDERED output (pool hexes, minHueGap, directional, status) re-quantizes to 8-bit and matches the oracle EXACTLY"
metrics:
  duration: 12 min
  completed: 2026-06-05
---

# Phase 15 Plan 02: Pure Kotlin Palette Port Summary

Ported the owner-reviewed `../theme_theory/app/color.js` palette generator to a host-pure Kotlin `object Palette` (zero Android/Compose imports), exposed the `internal` OKLCH helper surface 15-03's `TokenBridge` needs, and turned the Wave-0 `PaletteGoldenTest` + `PaletteMathTest` scaffolds GREEN against the committed `color-golden.json` oracle — proving the cusp-anchored accent, contrast-ranked data pool, status slots, and pure-neutral surfaces match `color.js` bit-for-bit across dark/light, Simple, High-Contrast, two edge-hue seeds, and poolShift.

## What Was Built

**Task 1 — color.js → object Palette + GREEN golden/math tests (`d5b7d5c`)**

- `theme/Palette.kt` (469 lines, `object Palette`, NO `androidx`/`android.`/`compose` import — host-pure like `PrinterStateReducer`). Faithful 1:1 port of every `color.js` function:
  - `clamp01` (`coerceIn(0.0,1.0)`), `sToL`/`lToS` gamma transfer, `hexToRgb`/`rgbToHex` (lowercase 2-pad to match the oracle).
  - `linToLab`/`labToLin` with the OKLab 3×3 matrices copied **verbatim**; `hexToOklch` (atan2/hypot, H wrapped +360); `inGamut` (±0.0002 epsilon exact); `oklchToHex` (20-iter chroma binary search); `grayHex`; `maxChromaAt` (18-iter); `cuspL` (literal `L += 0.02` accumulation loop — NOT refactored to integer-step).
  - `hueDiff`/`inSpan`/`isReserved` (ported for parity though dead at `statusFromPool=true`), `spreadHues` (step 0.5° run-length accumulation), `minHueGap`, `rankByContrast` (farthest-point reorder; JS `slice/shift/splice` → `toMutableList()`/`removeAt`), and `generate`.
  - **`/255.0` Double normalization** at every hex-channel read (the confirmed Int-division landmine — a naive `intByte / 255` collapses every channel < 0xFF to 0). `Double` throughout the math; narrowed to Int only at `rgbToHex`'s `roundToInt`.
  - Output is a plain `data class Generated` of **String** hexes (surfaces/theme/status/pool/poolHues/poolRoles/directional/minHueGap/poolShift) — NOT a `ThemeTokens`. The bridge bakes to `Color`; keeping this a 1:1 of `color.js` keeps the golden tests host-pure.
  - **15-03 contract (chosen):** `hexToOklch`, `oklchToHex`, and the `OklchValue` triple are marked **`internal`** (same-module visible, not private) so `TokenBridge.kt` can derive the in-between surface tiers via those helpers + an lShift — mirroring `color.js`'s `Palette.util` export. The tier derivation was NOT moved into `Palette` (the allowed alternative); the `internal`-helper exposure keeps `Palette` a clean 1:1 of the oracle.
- `PaletteGoldenTest`: loads `color-golden.json` from the test classpath, reads each entry's `opts`, runs `Palette.generate(...)`, and asserts accent/secondary/surfaces/pool/poolHues/poolRoles/directional/status/minHueGap/poolShift. All 7 named methods GREEN.
- `PaletteMathTest`: `oklchToSrgbRoundTrip_staysInGamut` (in-gamut hexes round-trip exactly hex→OKLCH→hex) + `gamutClamp_neverExceedsUnitInterval` (an L/H sweep at a deliberately out-of-gamut chroma 0.5 always yields channels in [0,255] — the 20-iter clamp holds). Both GREEN.

## Verification

`:app:testDebugUnitTest --tests …PaletteGoldenTest --tests …PaletteMathTest` (Windows Gradle): compileDebugKotlin + compileDebugUnitTestKotlin OK (both main AND test sourcesets compile — proves no retired-symbol breakage) → **9 tests, BUILD SUCCESSFUL, 0 failures.**

Acceptance-criteria greps (all pass):
- `grep -E "import (androidx|android\.|.*compose)" Palette.kt` → **NONE** (host-pure).
- `/255.0` Double normalization present at all three channel reads in `hexToRgb`.
- `internal fun hexToOklch` + `internal fun oklchToHex` both present.
- `cuspL` retains `L += 0.02`.
- `Palette.generate("#3f78ff", dark=true, maxItems=3)` → accent `#3c75fb`, bg `#0b0b0b`, pool `["#6895f4","#866200","#c575cc"]`, minHueGap 60 (oracle parity, via `defaultSeedDark_matchesOracle` GREEN).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Test assertion bug] `poolHues` exact-equality assertion replaced with a 1e-9 epsilon**
- **Found during:** Task 1, first golden-test run (`edgeHueYellow_matchesOracle` FAILED; the other 8 tests passed).
- **Issue:** The first cut asserted `result.poolHues == fixture.poolHues` by exact `List<Double>` equality. The `edgeYellow` vector (seed `#c8b400`) drifted in the last ~2 bits: oracle `101.35412483565102` vs Kotlin `101.35412483565095` (≈7e-14). Root cause: `poolHues` derive from the seed hue, whose `atan2`/`cbrt` last-bit value differs between the JS oracle (Node) and the JVM `kotlin.math` — an unavoidable platform float difference, NOT a port error.
- **Why it's harmless:** `poolHues` is documented in `color.js` as "for diagnostics". The RENDERED output that flows to the UI — the pool hexes, `directional`, `status`, and `minHueGap` (which rounds) — re-quantizes through `oklchToHex` to 8-bit sRGB and matches the oracle **exactly** (the same `edgeYellow` pool `["#ab9a00","#5d5abc","#04b47a"]` and `minHueGap 60` assert bit-for-bit). Exact-equality on an intermediate float-accumulated diagnostic was the wrong assertion.
- **Fix:** Assert `poolHues` size, then each element with a 1e-9 `assertEquals(expected, actual, delta)`. Rendered hexes/minHueGap remain exact bit-for-bit.
- **Files modified:** `app/src/test/java/works/mees/dinghy/theme/PaletteGoldenTest.kt`
- **Commit:** `d5b7d5c`

No other deviations — the generator math is a verbatim 1:1 of `color.js`; `statusFromPool=true` is the shipped default (D-01).

## Notes for 15-03 (TokenBridge)

- The helper contract is the **`internal` exposure**: call `Palette.hexToOklch(hex)` → `OklchValue(L,C,H)` and `Palette.oklchToHex(L,C,H)` → String hex to port `dinghy.js`'s `lShift(hex, dL)` (= hexToOklch → bump L → oklchToHex). Both are same-module visible from `theme/`.
- `Palette.generate(...)` returns `Palette.Generated` (String hexes). The bridge maps `surfaces.*`/`theme.*`/`status.*`/`pool`/`directional` onto `ThemeTokens` and bakes the String hexes to Compose `Color` (the ONLY other sanctioned `Color(0x..)` site besides `BakedTokens.kt`).

## Known Stubs

None — `loadFixture()` (the documented RED-phase stub in 15-01) was replaced by the real classpath-resource loader + per-key vector lookup.

## Self-Check: PASSED
- FOUND: app/src/main/java/works/mees/dinghy/theme/Palette.kt
- FOUND: app/src/test/java/works/mees/dinghy/theme/PaletteGoldenTest.kt (GREEN, 7 methods)
- FOUND: app/src/test/java/works/mees/dinghy/theme/PaletteMathTest.kt (GREEN, 2 methods)
- FOUND: commit d5b7d5c (Task 1)
- VERIFIED: `:app:testDebugUnitTest` 9 tests / 0 failures / BUILD SUCCESSFUL
