---
phase: 15-theme-system-settings-redesign
plan: 03
subsystem: theme
tags: [token-bridge, theme-tokens, oklch-lshift, pool, directional, wave-2]
requires:
  - "15-02 (theme/Palette.kt — Palette.Generated + internal hexToOklch/oklchToHex helpers)"
provides:
  - "ThemeTokens extended with pool: List<Color> + directional: Directional (+ sibling @Immutable Directional value type); violet retired to a @Deprecated get-only shim"
  - "theme/TokenBridge.build(gen, overrides, fs): Palette.Generated -> complete ThemeTokens — the SECOND sanctioned ThemeTokens producer; derives in-between tiers + applies sparse pool overrides + emits pure-neutral surfaces, all baked sRGB Color"
affects:
  - "plan 15-04 (ThemeResolver rewire calls TokenBridge.build instead of returning a baked base + deltas)"
  - "plan 15-06/15-07 (consumers read pool[]/directional; 15-07 deletes the @Deprecated violet shim and migrates GraphView/TemperatureScreen to pool[2 % size])"
tech-stack:
  added: []
  patterns:
    - "Second ThemeTokens producer (besides BakedTokens) — the only other sanctioned site to construct a Color from generator output"
    - "Tier derivation by delegating to Palette's internal OKLCH helpers (lShift = hexToOklch -> bump L -> oklchToHex), NOT re-porting the math — avoids golden drift"
    - "Two-step token retirement: @Deprecated get-only shim at the wave boundary (keeps consumers compiling), delete + migrate one plan later (15-07)"
    - "Defensive sparse-override application: overrides[i] ?: c via mapIndexed — out-of-range keys never match, dropped without crash"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/theme/TokenBridge.kt
  modified:
    - app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt
    - app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt
    - app/src/test/java/works/mees/dinghy/theme/TokenBridgeTest.kt
decisions:
  - "violet retired via @Deprecated get-only computed property (pool[2 % size]), NOT deleted now — keeps GraphView + TemperatureScreen compiling at this wave boundary (deprecation warnings, not errors); 15-07 deletes the shim + migrates the two reads"
  - "BakedTokens default pool/directional carry the LEGACY nozzle(heat)/bed(accent)/chamber(violet) trace identities so the fail-safe default theme renders before the generator runs; pool[2] = old --violet so the @Deprecated shim returns the chamber color"
  - "Bridge test asserts parity against an INDEPENDENT dinghy.js-equivalent derivation (Palette internal helpers + the same dL/alpha constants reconstructed in the test), not against hardcoded magic hexes — proves the bridge wires the right source+shift without re-asserting the OKLCH golden (already locked in 15-02)"
metrics:
  duration: 9 min
  completed: 2026-06-05
---

# Phase 15 Plan 03: Token Bridge + Extended ThemeTokens Summary

Extended `ThemeTokens` with the contrast-ranked data `pool` + `Directional` standards (retiring `violet` to a flagged `@Deprecated` shim) and ported `dinghy.js`'s `tokensFromPalette()` into a new `TokenBridge.build()` that maps the slim `Palette.Generated` onto a complete `ThemeTokens` — deriving the in-between surface/outline tiers in Kotlin via 15-02's `internal` OKLCH helpers (an `lShift`, so `Palette` stays a 1:1 of `color.js`), applying sparse pool overrides at-index, and emitting pure-neutral surfaces — then turned the Wave-0 bridge scaffold GREEN.

## What Was Built

**Task 1 — extend ThemeTokens; retire violet (`93d9aaf`)**

- `theme/ThemeTokens.kt`: added `val pool: List<Color>` (KDoc "Generated contrast-ranked DATA pool (D-13). Consumers wrap `pool[i % pool.size]` (D-14)"; stability via the existing class-level `@Immutable`, no `kotlinx-collections-immutable` dep per RESEARCH A3) and `val directional: Directional`, plus a sibling `@Immutable data class Directional(temperature, xy, z)`. `violet` is now a **`@Deprecated` get-only computed property** (`get() = if (pool.isEmpty()) accent else pool[2 % pool.size]`, `ReplaceWith("pool[2 % pool.size]")`) — flagged for 15-07 to delete. Reworded the `heat` KDoc to caution-only (D-13, not heater identity) and replaced the D-01 custom-scope KDoc with the seed-derived model (D-04/D-09).
- `theme/BakedTokens.kt`: dropped the `violet = Color(...)` named constructor arg from BOTH `ThemeTokens(...)` calls (a computed property is not a parameter), and added `pool`/`directional` literals carrying the legacy nozzle/bed/chamber trace identities (`pool[2]` = old `--violet` so the shim returns the chamber color).
- Verified: `:app:compileDebugKotlin` SUCCESSFUL — whole MAIN sourceset compiles; the only output is the two expected `'val violet' is deprecated` warnings on `GraphView.kt:186` + `TemperatureScreen.kt:450` (the shim keeps both `.violet` reads resolving).

**Task 2 — TokenBridge.build() + GREEN bridge test (`efc030c`)**

- `theme/TokenBridge.kt` (`object TokenBridge`, `fun build(gen, overrides, fs): ThemeTokens`): ported `tokensFromPalette()`. `lShift(hex, dL)` calls 15-02's `internal` `Palette.hexToOklch`/`oklchToHex` (NO re-ported OKLCH math); `rgbaOf(hex, a)` = `bake(hex).copy(alpha = a)`; `bake(hex)` reads `#rrggbb` → opaque sRGB `Color(red,green,blue,alpha=0xFF)`. 1:1 maps (`surface`/`text`/`text2`←muted/`accent`←primary/`go`/`stop`/`heat`←caution per D-13); derived tiers per the RESEARCH dark/light table (`bg`←neutral bg verbatim — D-16, no tint; `bg2`/`surface2`/`surface3`/`text3`/`outline2`=lShift, `hair`/`edgeGlow`/`accent*Soft/Line/Glow` + heat/go/stop `*Soft`/`*Glow`=rgbaOf); `pool` = `gen.pool` baked then `overrides[i] ?: c` via `mapIndexed` (out-of-range keys never match → dropped, T-15-03-01); `directional` baked from `gen.directional`; radii + `fs` threaded. Every value is a baked sRGB `Color` — no `Oklab`/`ColorSpaces` API.
- `TokenBridgeTest`: replaced the two `fail(...)` RED bodies and added a third. `derivesInBetweenTiers_matchesDinghyJs` asserts the sampled derived tiers (bg2/surface2/3/text3/outline2/accent2) + alpha variants (hair/edgeGlow/accentGlow) + 1:1 maps (heat=caution/go/stop/directional.xy) equal an INDEPENDENT dinghy.js-equivalent derivation (Palette internal helpers + the dark dL/alpha constants reconstructed in the test). `appliesPoolOverrides_atIndex` asserts `overrides[1]` replaces `pool[1]`, every other slot stays seed-derived, the out-of-range `999` key is dropped (size unchanged), and the override actually differs. `surfacesArePureNeutral_bgVerbatim` asserts `bg` == the generator's neutral bg (D-16).
- Verified: `:app:testDebugUnitTest --tests …TokenBridgeTest` — both main AND test sourcesets compile → BUILD SUCCESSFUL (3 methods GREEN, 0 failures).

## Verification

- Task 1: `:app:compileDebugKotlin` BUILD SUCCESSFUL (whole main sourceset; only the 2 expected violet-deprecation warnings).
- Task 2: `:app:testDebugUnitTest --tests works.mees.dinghy.theme.TokenBridgeTest` BUILD SUCCESSFUL — TokenBridgeTest GREEN (3/3).
- Acceptance greps: `val pool: List<Color>` ✓, `data class Directional` ✓, `@Deprecated` near violet ✓, `violet = Color` count in BakedTokens = 0 ✓, `fun build` ✓, TokenBridge calls `hexToOklch`/`oklchToHex` ✓, no `ColorSpaces|Oklab` API usage (only doc-comment mentions) ✓.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 — defensive correctness] `violet` shim guards the empty-pool case**
- **Found during:** Task 1.
- **Issue:** The plan specified the shim as `pool[getOrElse...]`; a bare `pool[2 % pool.size]` divides by zero if `pool` is ever empty.
- **Fix:** `get() = if (pool.isEmpty()) accent else pool[2 % pool.size]` — falls back to `accent` rather than crashing. Defensive only (BakedTokens + the bridge always supply ≥3 pool entries; the substrate's `resolve` will too).
- **Files modified:** `app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt`
- **Commit:** `93d9aaf`

**2. [Plan refinement] BakedTokens pool/directional given concrete legacy values**
- **Found during:** Task 1.
- **Issue:** The plan focused on dropping the `violet` arg; it did not specify what `pool`/`directional` literals the baked fail-safe defaults should carry (they are now required constructor params).
- **Fix:** Seeded both bases' `pool`/`directional` with the legacy nozzle(heat)/bed(accent)/chamber(violet) trace identities so the default theme renders correctly before the generator runs, and `pool[2]` = the old `--violet` chamber color so the `@Deprecated` shim returns the historically-correct trace color.
- **Files modified:** `app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt`
- **Commit:** `93d9aaf`

**3. [Test robustness] Added a third test method `surfacesArePureNeutral_bgVerbatim`**
- The plan's must-have "Surfaces are pure-neutral — the bridge emits the generator's neutral bg, no surface tint reintroduced (D-16)" warranted its own explicit assertion rather than living only in a code comment. Added as a GREEN test.
- **Files modified:** `app/src/test/java/works/mees/dinghy/theme/TokenBridgeTest.kt`
- **Commit:** `efc030c`

No other deviations — the tier derivation is a verbatim port of `dinghy.js`'s `tokensFromPalette()`, the dL/alpha constants match the RESEARCH dark/light table, and the OKLCH math is delegated to Palette (not re-ported).

## Notes for 15-04 / 15-07

- **15-04 (resolver rewire):** `TokenBridge.build(gen, overrides, fs)` is the seam — feed it `Palette.generate(...)` output + the sanitized `poolOverrides` map + the `fs` multiplier. It is the SECOND sanctioned `ThemeTokens` producer; the resolver should call it rather than returning a baked base + deltas.
- **15-07 (delete the shim):** `ThemeTokens.violet` is a `@Deprecated` get-only computed property → delete it and migrate the two reads — `GraphView.kt:186` (`t.violet.toArgb()`) and `TemperatureScreen.kt:450` (`else -> t.violet`) — to `pool[2 % pool.size]` (the `ReplaceWith` already names the target).

## Known Stubs

None — `TokenBridge.build` produces a complete `ThemeTokens` (every field populated); the `@Deprecated violet` shim is an intentional, flagged transitional shim slated for deletion in 15-07, not a stub.

## Self-Check: PASSED
- FOUND: app/src/main/java/works/mees/dinghy/theme/TokenBridge.kt
- FOUND: app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt (pool + Directional + @Deprecated violet)
- FOUND: app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt (pool/directional added, no violet= arg)
- FOUND: app/src/test/java/works/mees/dinghy/theme/TokenBridgeTest.kt (3 methods GREEN)
- FOUND: commit 93d9aaf (Task 1)
- FOUND: commit efc030c (Task 2)
- VERIFIED: :app:compileDebugKotlin SUCCESSFUL; :app:testDebugUnitTest TokenBridgeTest 3/0 BUILD SUCCESSFUL
