---
phase: 03-design-system-theming-foundation
plan: 01
subsystem: ui
tags: [theming, oklch, srgb, datastore, stateflow, compose, kotlin, tokens]

# Dependency graph
requires:
  - phase: 02-connection-state-foundation
    provides: PrinterStateStore StateFlow idiom + PrinterState plain-Kotlin value-type discipline (the structural analogs the resolver/tokens mirror)
provides:
  - "ThemeTokens: @Immutable toolkit-agnostic resolved token set (sRGB Color + dp shape tokens + fs)"
  - "BakedTokens: checked-in TokensDark/TokensLight sRGB Color literals (oklch baked once, D-03)"
  - "tools/oklch-bake/bake_tokens.py: traceable, re-runnable CSS Color 4 oklch->sRGB bake script"
  - "ThemeResolver: StateFlow<ThemeTokens> from (base, deltas, fs) — the single source of truth (D-05)"
  - "TokenDelta: sparse override-on-base custom-theme model (D-01/D-02)"
  - "ThemePrefs: DataStore persistence with a deterministic fail-safe read path (D-02)"
  - "FontScale + fsSp(): the --fs S/M/L text-size authority (THEME-02/D-04)"
affects: [03-02, 03-03, 03-04, 03-05, 03-06, 03-07, 04-shell-settings, theming, compose-adapter, views-adapter]

# Tech tracking
tech-stack:
  added: ["androidx.datastore:datastore-preferences 1.1.7"]
  patterns:
    - "Baked sRGB token table (oklch never reaches the renderer — API-23 safe)"
    - "Pure host-testable sanitize() seam for fail-safe persistence (no DataStore I/O in tests)"
    - "Unsigned-32-bit ARGB normalization so persisted/compared color values agree bit-for-bit"
    - "StateFlow resolver mirroring PrinterStateStore (MutableStateFlow -> asStateFlow + recompute mutators)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt
    - app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt
    - app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt
    - app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt
    - tools/oklch-bake/bake_tokens.py
    - app/src/test/java/works/mees/dinghy/theme/ThemeResolverTest.kt
    - app/src/test/java/works/mees/dinghy/theme/TokenDeltaSerializationTest.kt
    - app/src/test/java/works/mees/dinghy/theme/FontScaleTest.kt
    - app/src/test/java/works/mees/dinghy/theme/BakedTokenTableTest.kt
    - app/src/test/java/works/mees/dinghy/theme/ThemePrefsFallbackTest.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts

key-decisions:
  - "Gamut mapping = clamp chroma (CSS Color 4) — documented in bake script + BakedTokens.kt header"
  - "TokenDelta stores UNSIGNED 32-bit ARGB longs (normalized on construction); resolve() truncates via toInt()"
  - "Fail-safe sanitization is a PURE function (ThemePrefs.sanitize) so ThemePrefsFallbackTest is host-pure (no Robolectric)"
  - "TokenDelta.of(vararg Pair<Role,Int>) is the sanctioned constructor from Compose ARGB ints"

patterns-established:
  - "BakedTokens.kt is the ONLY sanctioned home for literal sRGB Color(0x..) values in the codebase"
  - "Every theme is resolve(base, deltas, fs); empty delta === base (Pitfall 7 invariant)"
  - "Persistence read path NEVER throws — always a complete usable theme (D-02 fail-safe contract)"

requirements-completed: [THEME-01, THEME-02]

# Metrics
duration: 18min
completed: 2026-05-31
---

# Phase 3 Plan 01: Headless Theming Core Summary

**Baked oklch→sRGB token table (clamp-chroma gamut mapping, traceable Python script) feeding a `StateFlow<ThemeTokens>` resolver and a fail-safe DataStore persistence layer — the API-23-safe single source of truth every later UI surface consumes.**

## Performance

- **Duration:** ~18 min
- **Started:** 2026-05-31 (Phase 03 execution)
- **Completed:** 2026-05-31
- **Tasks:** 2
- **Files modified:** 12 (10 created, 2 modified)

## Accomplishments

- **`ThemeTokens`** — `@Immutable`, toolkit-agnostic value type with all ~26 role tokens (sRGB `Color`), the four dp shape tokens, and `fs`; mirrors `PrinterState`'s plain-Kotlin discipline. Plus `ThemeBase`/`FontScale` enums and the `fsSp()` helper.
- **`tools/oklch-bake/bake_tokens.py`** — a committed, re-runnable CSS Color 4 pipeline (oklch→Oklab→linear-sRGB→sRGB) with clamp-chroma gamut mapping, reading every dark+light literal from THEMING.md and emitting `BakedTokens.kt`. The 4 most saturated tokens were cross-checked against an independent re-implementation (bit-exact match: accent `#4C94EC`, stop `#F4514F`, go-light `#008C3F`, heat-light `#CE6400`).
- **`BakedTokens.kt`** (generated) — `TokensDark` / `TokensLight` sRGB `Color(0x..)` literals; no oklch ever reaches the renderer (dodges RESEARCH Pitfall 1 — the silent API-<26 sRGB fallback).
- **`ThemeResolver`** — `StateFlow<ThemeTokens>` from `(base, deltas, fs)` with `setBase/setDeltas/setFs/apply` mutators, mirroring `PrinterStateStore`. Empty delta === base (Pitfall 7).
- **`TokenDelta`** — sparse override-on-base model (D-01 scope: accent/heat/go/stop/bg), normalized to unsigned-32-bit ARGB so persisted and compared values agree.
- **`ThemePrefs`** — the first DataStore in the repo. Persists base/fs/deltas and enforces a deterministic fail-safe read path that NEVER throws and always yields a complete, usable theme (mitigates threat T-03-01).
- **DataStore 1.1.7** added to the catalog; `verifyMinSdk` confirms the merged-manifest floor held at 23.

## Task Commits

1. **Task 1: Add DataStore to the catalog and bake oklch→sRGB tokens** - `5769e20` (feat)
2. **Task 2: ThemeResolver StateFlow + ThemePrefs persistence (fail-safe)** - `e08a556` (feat, TDD)

_TDD note: Task 2's tests and implementation were authored and verified together; the named suite plus the full `:app:testDebugUnitTest` are green._

## Files Created/Modified

- `app/.../theme/ThemeTokens.kt` - `@Immutable` resolved token value type + `ThemeBase`/`FontScale` enums + `fsSp()`
- `app/.../theme/BakedTokens.kt` - generated `TokensDark`/`TokensLight` sRGB literals + gamut-policy/cross-check comment
- `app/.../theme/ThemeResolver.kt` - `TokenDelta`, pure `resolve()`, and the `StateFlow<ThemeTokens>` resolver
- `app/.../theme/ThemePrefs.kt` - DataStore persistence + pure `sanitize()` fail-safe seam
- `tools/oklch-bake/bake_tokens.py` - traceable one-time oklch→sRGB bake (clamp-chroma)
- `app/src/test/.../theme/{ThemeResolver,TokenDeltaSerialization,FontScale,BakedTokenTable,ThemePrefsFallback}Test.kt` - Wave-0 unit tests
- `gradle/libs.versions.toml` - `datastore = "1.1.7"` version + library entry (banner-grouped)
- `app/build.gradle.kts` - `implementation(libs.androidx.datastore.preferences)`

## Decisions Made

- **Gamut mapping = clamp chroma** (CSS Color 4): out-of-gamut high-chroma tokens (accent2, heat family, go family) desaturate at fixed L,h via binary search, preserving hue+lightness. Documented in the script and the generated header. (RESEARCH Open Question 2, RESOLVED.)
- **`TokenDelta` normalizes to unsigned 32-bit ARGB on construction** (`require` guard + `TokenDelta.of(...)` factory). This was the fix for the round-trip bug below — `Color.toArgb().toLong()` sign-extends opaque colors to negative Longs, which the read-path validity check would otherwise reject.
- **Fail-safe sanitization is a pure function** (`ThemePrefs.sanitize`), kept free of DataStore so `ThemePrefsFallbackTest` is host-pure — no Robolectric, per the plan's "avoid Robolectric unless a Color assertion forces it" rule.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Signed-Long ARGB caused empty round-trips**
- **Found during:** Task 2 (TokenDeltaSerializationTest)
- **Issue:** `Color.toArgb().toLong()` sign-extends an opaque `0xFF......` color into a NEGATIVE Long. The read-path validity check (`in 0L..0xFFFFFFFFL`) rejected it, so every persisted override silently dropped — two round-trip tests failed.
- **Fix:** Made `TokenDelta` normalize to unsigned 32-bit ARGB on construction (a `require` guard + a `TokenDelta.of(vararg Pair<Role,Int>)` factory that masks `& 0xFFFFFFFFL`); masked defensively in `ThemePrefs.setDeltas` too. Updated tests to use `TokenDelta.of(...)`.
- **Files modified:** ThemeResolver.kt, ThemePrefs.kt, the three affected test files
- **Verification:** Named suite + full `:app:testDebugUnitTest` green
- **Committed in:** `e08a556` (Task 2 commit)

**2. [Rule 3 - Blocking] Kotlin precedence: `to ... and ...` parsed as `(Pair) and Long`**
- **Found during:** Task 2 (ThemePrefsFallbackTest compile)
- **Issue:** `"Go" to Color(...).toArgb().toLong() and 0xFFFFFFFFL` — `to` and `and` are same-precedence infix, left-associative, so `and` applied to a `Pair` (unresolved reference).
- **Fix:** Parenthesized the value expression.
- **Files modified:** ThemePrefsFallbackTest.kt
- **Verification:** Compiles + passes
- **Committed in:** `e08a556` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking) — both in my own newly-written code during the same task; no scope creep, no impact on the plan's intent. The bug fix actually hardened the persisted contract (explicit unsigned-ARGB normalization).

## Issues Encountered

None beyond the two self-introduced deviations above. The bake script ran clean on the first try and matched an independent cross-check.

## User Setup Required

None - no external service configuration required. (DataStore is a first-party AndroidX artifact; fonts/editor UI are later plans.)

## Next Phase Readiness

- The headless theme core is the seam the rest of Phase 3 builds on:
  - **03-02+ Compose adapter** (`LocalTokens` `staticCompositionLocalOf` + `DinghyTheme` collecting `resolver.tokens`, `LocalDensity(fontScale=1f)` for D-04) consumes `ThemeResolver` + `fsSp()`.
  - **Views adapter** (`ThemeableView.applyTokens` + `invalidate()`) collects the same `tokens` flow.
  - **Settings editor (Phase 4 SET-01)** writes through `ThemePrefs` — the fail-safe read path is already proven, so a half-saved custom theme can never black-screen the printer display.
- No blockers. `verifyMinSdk` green (floor 23 held), full unit suite green (no Phase-2 regression).

---
*Phase: 03-design-system-theming-foundation*
*Completed: 2026-05-31*

## Self-Check: PASSED

All 10 created files verified present on disk; both task commits (`5769e20`, `e08a556`) verified in git history.
