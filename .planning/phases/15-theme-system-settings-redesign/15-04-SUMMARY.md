---
phase: 15-theme-system-settings-redesign
plan: 04
subsystem: theme
tags: [theme-resolver, generate-and-cache, seed-api, deprecated-shims, two-step-retirement, wave-3]
requires:
  - "15-02 (theme/Palette.kt — Palette.generate(seedHex, dark, maxItems, poolShift, statusFromPool, simple, highContrast) -> Generated)"
  - "15-03 (theme/TokenBridge.kt — TokenBridge.build(gen, overrides, fs) -> complete ThemeTokens, the stable seam)"
provides:
  - "ThemeResolver rewired to generate-and-cache: no-arg ctor generates the default-seed palette at first launch; new seed/mode/shift/override API + single-re-emit tuple apply(); recompute() = Palette.generate + TokenBridge.build cached on the UNCHANGED StateFlow<ThemeTokens> boundary"
  - "The old per-role TokenDelta API (TokenDelta/Role/setBase/setDeltas/setFs/top-level resolve()/3-arg apply()/base-param ctor) retained as @Deprecated bridge shims so GalleryScreen/SettingsScreen + legacy theme/prompt tests keep compiling (D-04 two-step; hard delete in 15-06)"
  - "DEFAULT_SEED_HEX (#3f78ff) + DEFAULT_POOL_MAX_ITEMS (4) top-level consts"
affects:
  - "plan 15-05 (replaces AppContainer.seedTheme's default-seed tuple with the PERSISTED seed/mode/pool tuple)"
  - "plan 15-06 (hard-deletes the @Deprecated TokenDelta API + the baked resolve() shim; rebuilds SettingsScreen, re-points/trims GalleryScreen onto the data-pool override API; rewrites/deletes the legacy theme/prompt tests)"
tech-stack:
  added: []
  patterns:
    - "Generate-and-cache resolver: compute color tokens ONCE per discrete change, cache on the StateFlow; the render loop only ever reads cached Color ints (Adreno-320 fill-rate floor — the device never does color math)"
    - "Preserve-the-boundary substrate rewire: StateFlow<ThemeTokens> + asStateFlow() + recompute-and-re-emit idiom kept EXACTLY so Compose DinghyTheme + Views ThemeableView consume the same flow untouched (extended-not-rewritten)"
    - "Two-step API retirement: @Deprecated bridge shims at the wave boundary keep both main + test sourcesets green; hard delete + call-site migration one plan later (15-06). Gradle compiles the WHOLE main AND test sourceset before any --tests filter, so a hard delete here would brick every plan's test run"
    - "Private all-args primary ctor + a no-arg (live generate) ctor + a @Deprecated (base, deltas, fs) ctor — two public entry points sharing one held-state core, distinguished by a `baked` flag that routes compute() to either Palette+TokenBridge or the baked resolve()"
    - "Fail-safe try/catch around generation falling back to the BakedTokens default-seed snapshot — the printer surface must never go dark (T-15-04-01)"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt
    - app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/test/java/works/mees/dinghy/theme/ThemeResolverTest.kt
decisions:
  - "The old (base, deltas, fs) ctor + setBase/setDeltas/setFs + top-level resolve() are kept BEHAVIOR-IDENTICAL to pre-15-04 (baked tables + delta application), just @Deprecated — NOT 'ignore deltas' as the plan offered. Reason: ThemePrefsFallbackTest line 120 asserts a VALID Accent delta IS applied (resolve(...).accent == Color(0xFF112233)) and PromptStyleColorsTest asserts the full baked Dark token set; ignoring deltas would fail those. Keeping the real behavior satisfies the must-have ('keep asserting on a complete token set') with zero churn to the legacy tests"
  - "Dual public constructors via a private all-args primary ctor: no-arg ctor = LIVE generate-and-cache (default seed #3f78ff); @Deprecated (base, deltas, fs) ctor = baked path seeding via resolve(). A `baked` Boolean routes compute(). The deprecated ctor re-emits in its body because the secondary-ctor body runs AFTER the property initializers (incl. the initial _tokens compute())"
  - "BakedTokens TokensDark/TokensLight tables RETAINED (not deleted) — they back the @Deprecated resolve() shim, the recompute() fail-safe, AND the legacy tests' oracles. Demotion is the doc/role change (no longer the live source of truth) + a header KDoc; the literal-sRGB law stands and now governs the generator's output policy"
  - "setDeltas live-path is a NO-OP (chrome is seed-only, D-04); under the baked path it still applies deltas so the legacy ThemeResolverTest stays green. T-15-04-03: the stale per-role override path is dead on the live path"
metrics:
  duration: 16 min
  completed: 2026-06-05
---

# Phase 15 Plan 04: ThemeResolver Generate-and-Cache Rewire Summary

Rewired `ThemeResolver` from baked-table-lookup to a **runtime generate-and-cache** model — `recompute()` now calls `Palette.generate()` → `TokenBridge.build()` once per discrete change and caches the complete `ThemeTokens` on the **UNCHANGED** `StateFlow<ThemeTokens>` boundary — added the seed/mode/shift/override API + a single-re-emit tuple `apply()`, default-constructed with the validated default seed `#3f78ff`, demoted `BakedTokens` to a fail-safe snapshot, and kept the entire old per-role `TokenDelta` API alive as `@Deprecated` bridge shims so GalleryScreen/SettingsScreen + every legacy theme/prompt test compiles at this wave boundary (D-04 two-step retirement; hard delete is 15-06).

## What Was Built

**Task 1 — generate-and-cache resolver + @Deprecated shims + BakedTokens demotion (`be36de7`)**

- `theme/ThemeResolver.kt`:
  - **New live path.** A private all-args primary ctor holds `(seedHex, dark, paletteMode, poolShift, maxItems, poolOverrides, fs, baked)`. The **no-arg `ThemeResolver()`** ctor seeds the validated default tuple (`#3f78ff`, dark, Colorful, poolShift 0, maxItems 4, no overrides, M fs) and **generates the out-of-box palette at first launch (D-02)**. `recompute()` → `compute()` maps `paletteMode → simple/highContrast` flags (Colorful → both false, D-15; "Simple" → simple; "HighContrast" → highContrast), calls `Palette.generate(... statusFromPool=true ...)`, then `TokenBridge.build(generated, poolOverrides, fs)`, and assigns `_tokens.value`. **The `MutableStateFlow + asStateFlow() + recompute-and-re-emit` idiom is preserved EXACTLY** — `val tokens: StateFlow<ThemeTokens>` is the unchanged boundary both toolkits consume.
  - **New API:** `setSeed` / `setMode` / `setShift` / `setOverride(i, argb?)` (sparse-map edit — null clears the slot) / `setDark`, each sets its field then `recompute()`; plus `apply(seedHex, dark, paletteMode, poolShift, maxItems, overrides, fs)` that sets ALL fields then recomputes **ONCE** (Pitfall 3 — the full tuple in one re-emit, never a sequence of set*() = multiple flickers).
  - **Fail-safe (T-15-04-01):** `compute()`'s generation is wrapped in `try/catch`; on any throw it returns the `BakedTokens` default-seed snapshot (`TokensDark/TokensLight.copy(fs=fs)`) — the printer surface never goes dark.
  - **@Deprecated bridge shims (D-04; deleted in 15-06):** `data class TokenDelta` (+ `Role`/`of`/`EMPTY`), top-level `fun resolve(base, deltas, fs)` (behavior-IDENTICAL to pre-15-04: baked table + delta apply), the `(base, deltas, fs)` ctor (baked path), `setBase` (→ also drives `dark`), `setDeltas` (baked path applies; live path NO-OP — seed owns chrome, T-15-04-03), `setFs` (real fs update — unchanged this phase), and the old 3-arg `apply(base, deltas, fs)` overload. Every shim carries `@Deprecated(..., WARNING)`.
  - `DEFAULT_SEED_HEX = "#3f78ff"` + `DEFAULT_POOL_MAX_ITEMS = 4` + `MODE_COLORFUL/SIMPLE/HIGH_CONTRAST` constants.
- `theme/BakedTokens.kt`: header KDoc updated to record the **demotion** (no longer the live source of truth — fail-safe snapshot only); the literal-sRGB law stands and now governs the generator's output policy. `TokensDark/TokensLight` tables retained (they back the shim + fail-safe + legacy oracles).
- `di/AppContainer.kt`: `seedTheme`'s `collect { resolved -> ... }` adapted from the old 3-arg `apply(resolved.base, resolved.deltas, resolved.fs)` to the new tuple `apply(seedHex=DEFAULT_SEED_HEX, dark=resolved.base==Dark, Colorful, 0, 4, emptyMap, resolved.fs)`; added the `DEFAULT_SEED_HEX`/`DEFAULT_POOL_MAX_ITEMS`/`ThemeBase` imports. 15-05 swaps this for the persisted seed/mode/pool tuple.
- Verified: `:app:compileDebugKotlin` BUILD SUCCESSFUL (whole main sourceset — only the expected deprecation WARNINGS on GalleryScreen/GalleryActivity/SettingsScreen/ThemeResolver, no ERRORS); `:app:testDebugUnitTest --tests …ThemeResolverTest` BUILD SUCCESSFUL (both sourcesets compile; the pre-existing shim-backed tests stay green against the rewired resolver).

**Task 2 — extend ThemeResolverTest (`16cb3c9`)**

- `theme/ThemeResolverTest.kt`: KEPT the existing `runTest`/`UnconfinedTestDispatcher`/emission-count idiom AND all the pre-existing shim-backed tests (they still pass), then ADDED four NEW-API tests:
  - `defaultConstructed_generatesDefaultSeedTokens` — the no-arg resolver's accent/bg/pool equal an in-test default-seed **oracle** (`Palette.generate(#3f78ff, dark, 4, …)` → `TokenBridge.build(…, emptyMap, M)`), proving generate-and-cache produces the expected tokens (NOT the legacy baked table).
  - `setOverride_replacesPoolSlot_andClearRestoresSeedDerived` — `setOverride(1, custom)` puts `custom` at `pool[1]`; `setOverride(1, null)` restores the seed-derived color (D-09).
  - `applyTuple_emitsExactlyOnce_noFlicker` — collecting emissions: seed = 1, then one `apply(full tuple)` → count = 2 (increment of exactly 1, not 7; Pitfall 3) + the tuple's fs lands.
  - `setMode_simple_collapsesPoolToMonoGray` — after `setMode("Simple")` every pool entry is near-gray (per-channel `max-min ≤ 12`), proving the mode → flag wiring.
- The oracle is computed in-test (not hardcoded hexes) so it tracks the generator/bridge without golden re-litigation.
- Verified: `:app:testDebugUnitTest --tests …ThemeResolverTest` BUILD SUCCESSFUL — all tests GREEN (4 new + the pre-existing ones).

## Verification

- Task 1: `:app:compileDebugKotlin` BUILD SUCCESSFUL (deprecation warnings only). `:app:testDebugUnitTest --tests …ThemeResolverTest` BUILD SUCCESSFUL.
- Task 2: `:app:testDebugUnitTest --tests …ThemeResolverTest` BUILD SUCCESSFUL (new + legacy tests green).
- Cross-check (the load-bearing "both sourcesets stay green on the shims"): `:app:testDebugUnitTest --tests …ThemePrefsFallbackTest --tests …FontScaleTest --tests …PromptStyleColorsTest --tests …TokenBridgeTest` BUILD SUCCESSFUL — every legacy `resolve()`/`TokenDelta` consumer compiles + passes on the deprecated shims.
- Acceptance greps (all pass): `StateFlow<ThemeTokens>` ×2 ✓; `Palette.generate`/`TokenBridge.build` present ✓; `setSeed`/`setMode`/`setShift`/`setOverride`/`setDark` ×5 ✓; `data class TokenDelta` ×1 (retained) ✓; `@Deprecated` ×11 (shims) ✓; `AppContainer` calls the new tuple apply (`seedHex = DEFAULT_SEED_HEX`) ✓.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — correctness] `resolve()`/baked-ctor shims kept BEHAVIOR-IDENTICAL, not "ignore deltas"**
- **Found during:** Task 1, reading the legacy test consumers before touching the shim.
- **Issue:** The plan offered making the `resolve()` shim "generate the default-seed tokens … (ignore deltas)". But `ThemePrefsFallbackTest` line 120 asserts `resolve(Dark, {Accent→0xFF112233}, fs).accent == Color(0xFF112233)` (a VALID delta MUST be applied) and lines 100/121 assert `resolve(...).stop == TokensDark.stop` (garbage/empty delta inherits base), and `PromptStyleColorsTest` asserts the full baked Dark token set from `resolve(Dark, EMPTY, 1.0)`. Ignoring deltas / switching to generated tokens would fail those tests.
- **Fix:** Kept the `resolve()` shim + the deprecated `(base, deltas, fs)` ctor / `setBase` / `setDeltas` (baked path) **byte-behavior-identical** to pre-15-04 (baked tables + delta application), just annotated `@Deprecated`. This satisfies the must-have "keep asserting on a complete token set" with zero churn to the legacy tests (which 15-06 rewrites anyway).
- **Files modified:** `app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt`
- **Commit:** `be36de7`

**2. [Rule 3 — blocking] Dual-constructor seeding order fix**
- **Found during:** Task 1.
- **Issue:** Kotlin runs property initializers (incl. the initial `_tokens = compute()`) during the primary-ctor delegation, BEFORE the `@Deprecated (base, deltas, fs)` secondary-ctor body sets `bakedBase`/`bakedDeltas`. So `ThemeResolver(base = Light)` would seed `_tokens` with the default `bakedBase = Dark` — the wrong seeded value (latent; `resolver_exposesStateFlow` happened to use Dark so it would still pass, but `ThemeResolver(base=Light)` would be incorrect).
- **Fix:** The deprecated ctor body sets `bakedBase`/`bakedDeltas` then calls `recompute()` to re-emit the correctly-seeded value.
- **Files modified:** `app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt`
- **Commit:** `be36de7`

No other deviations — `BakedTokens` tables stayed (they back the shim + fail-safe + the legacy oracles); the new API + tuple `apply` + fail-safe try/catch match the plan; `AppContainer` adapted to the tuple `apply`.

## Notes for 15-05 / 15-06

- **15-05 (persist the tuple):** `AppContainer.seedTheme`'s `collect { resolved -> themeResolver.apply(seedHex=DEFAULT_SEED_HEX, …, fs=resolved.fs) }` currently feeds the DEFAULT seed + only honors the persisted dark/light + fs. Replace the default-seed tuple with the PERSISTED `(seedHex, mode, poolShift, maxItems, overrides)` once the profile/prefs carry them; the sanitize layer validates the seed (the resolver's `compute()` try/catch is the last-resort fail-safe).
- **15-06 (hard delete the shims):** delete `data class TokenDelta` (+ `Role`/`of`/`EMPTY`), the top-level `resolve(base, deltas, fs)`, the `@Deprecated (base, deltas, fs)` ctor, `setBase`/`setDeltas`/the 3-arg `apply`, and the `baked`/`bakedBase`/`bakedDeltas` plumbing in `ThemeResolver`. Re-point GalleryScreen (`SAMPLE_CUSTOM_DELTA`, `resolver.setBase/setDeltas/setFs`) + SettingsScreen onto `setSeed`/`setMode`/`setShift`/`setOverride`/`setDark`; rewrite/delete `ThemeResolverTest` (the shim-backed cases), `ThemePrefsFallbackTest`, `FontScaleTest`, `PromptStyleColorsTest`. `BakedTokens` then becomes a single fail-safe snapshot only.

## Known Stubs

None — the resolver produces a complete `ThemeTokens` (generate-and-cache or fail-safe). The `@Deprecated` `TokenDelta`/`resolve`/`setBase`/`setDeltas`/3-arg-`apply` shims are intentional, flagged transitional bridges slated for deletion in 15-06 (D-04 two-step), not stubs. The live-path `setDeltas` no-op is the deliberate D-04 chrome retirement, not an unfinished feature.

## Self-Check: PASSED
- FOUND: app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt (generate-and-cache + new API + @Deprecated shims)
- FOUND: app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt (demotion KDoc; tables retained)
- FOUND: app/src/main/java/works/mees/dinghy/di/AppContainer.kt (new tuple apply)
- FOUND: app/src/test/java/works/mees/dinghy/theme/ThemeResolverTest.kt (4 new tests + legacy retained)
- FOUND: commit be36de7 (Task 1)
- FOUND: commit 16cb3c9 (Task 2)
- VERIFIED: :app:compileDebugKotlin SUCCESSFUL; :app:testDebugUnitTest ThemeResolverTest GREEN; ThemePrefsFallbackTest/FontScaleTest/PromptStyleColorsTest/TokenBridgeTest GREEN on the shims
