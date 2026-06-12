---
phase: 28-system-settings-cluster
plan: "04"
subsystem: theme-config-persistence
tags:
  - theme
  - schema-deletion
  - persistence
  - D-17
dependency_graph:
  requires:
    - 28-01 (icon/component foundation)
  provides:
    - trimmed ThemeTuple/Profile/ThemeResolver shapes for 28-07 ThemeEditorScreen rebuild
  affects:
    - ThemePrefs.kt (ThemeTuple shape)
    - Profile.kt / PersistedProfile (wire shape)
    - ThemeResolver.kt (apply/bake/compute params)
    - AppContainer.kt (seedTheme + resetActiveTheme)
tech_stack:
  added: []
  patterns:
    - ignoreUnknownKeys migration safety (orphan stored key tolerated on decode)
    - DEFAULT_POOL_MAX_ITEMS hardcoded at Palette.generate boundary (D-17)
key_files:
  modified:
    - app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt
    - app/src/main/java/works/mees/dinghy/config/Profile.kt
    - app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/test/java/works/mees/dinghy/theme/ThemePrefsFallbackTest.kt
    - app/src/test/java/works/mees/dinghy/theme/ProfileThemeSeedTest.kt
    - app/src/test/java/works/mees/dinghy/theme/ThemeResolverBakeTest.kt
    - app/src/test/java/works/mees/dinghy/theme/ThemeResolverTest.kt
    - app/src/test/java/works/mees/dinghy/theme/StatusOverrideTokenBridgeTest.kt
    - app/src/test/java/works/mees/dinghy/theme/TokenDeltaSerializationTest.kt
    - app/src/test/java/works/mees/dinghy/config/ConnectionConfigTest.kt
decisions:
  - "D-17: maxItems removed as user-configurable persistence axis; pool size hardcoded at DEFAULT_POOL_MAX_ITEMS=4 at the Palette.generate boundary in ThemeResolver.computeFrom()"
  - "Palette.generate(maxItems=...) function param PRESERVED — TemperatureScreen's dedicated maxItems=8 swatch pool and TokenBridgeTest/ThemeResolverTest Palette.generate calls are unchanged"
  - "Migration safety via ProfileStore.Json ignoreUnknownKeys=true: stored blobs carrying orphan maxItems key decode cleanly without crash or data loss"
metrics:
  duration: ~25 min
  completed: "2026-06-12"
  tasks_completed: 2
  files_modified: 11
---

# Phase 28 Plan 04: Delete maxItems Persistence Axis (D-17) Summary

Deleted the dead user-configurable `maxItems` persistence axis across the full theme/config stack, hardcoding the 4-slot default at the `Palette.generate` boundary so live theming is byte-identical.

## What Was Done

### Task 1: Source layer deletions + compile gate

**ThemePrefs.kt:**
- Removed `ThemeTuple.maxItems` field
- Removed `KEY_MAX_ITEMS`, `DEFAULT_MAX_ITEMS`, `MAX_ITEMS_RANGE` constants
- Removed `setMaxItems()` method
- Removed `rawMaxItems: Int?` param from `sanitizeTuple()` and the validation logic
- Removed `maxItems = DEFAULT_MAX_ITEMS` write from `resetToDefaults()`
- Removed `prefs[KEY_MAX_ITEMS]` read from `tupleFlow`
- Updated `TUPLE_DEFAULT` (no longer carries `maxItems`)

**Profile.kt / PersistedProfile:**
- Removed `maxItems: Int = 4` field from both `PersistedProfile` and `Profile`
- Updated `toPersisted()`, `fromPersisted()`, `toThemeTuple()`, and both `toString()` overrides

**ThemeResolver.kt:**
- Removed `private var maxItems` stored field
- Removed `maxItems: Int` param from `apply()`, `bake()`, and `computeFrom()`
- `computeFrom()` now passes `maxItems = DEFAULT_POOL_MAX_ITEMS` literal to `Palette.generate()`
- `DEFAULT_POOL_MAX_ITEMS = 4` constant PRESERVED (it is now the sole authority)

**AppContainer.kt:**
- Removed `maxItems = tuple.maxItems` from `seedTheme`'s `themeResolver.apply()` call
- Removed `maxItems = ThemePrefs.DEFAULT_MAX_ITEMS` from `resetActiveTheme`'s `mutateActiveProfile` block

Compile gate: `:app:compileDebugKotlin` BUILD SUCCESSFUL.

### Task 2: Test suite updates + full suite green

Updated all 7 listed test files to remove `maxItems` references:

- **ThemePrefsFallbackTest:** removed `maxItems` param from `sanitize()` helper, removed `t.maxItems in 1..64` assertion from `assertFullyUsable`, deleted `outOfRangeMaxItems_fallsBackToDefault` test, removed `maxItems = 9999` arg from `fullyCorruptBlob` test
- **ProfileThemeSeedTest:** removed `maxItems` param from `profile()` helper and `Profile()` constructor, deleted `outOfRangeMaxItems_failsSafeToDefault` test, removed `maxItems = 999` from `corruptEverything` test, removed `assertEquals(6, t.maxItems)` assertion
- **ThemeResolverBakeTest:** removed `maxItems` from `tuple()` helper, `ThemeTuple()` constructor, and `apply()` call
- **ThemeResolverTest:** removed `maxItems = DEFAULT_POOL_MAX_ITEMS` from `apply()` call
- **StatusOverrideTokenBridgeTest:** removed `rawMaxItems = 4` from `sanitizeTuple()` calls, removed `maxItems = 4` from `Profile()` in `resolveViaActiveProfile`, removed `maxItems = tuple.maxItems` from `applyTuple`'s `apply()` call
- **TokenDeltaSerializationTest:** removed `assertEquals(4, p.maxItems)` assertion, removed `maxItems = 6` from `PersistedProfile()` round-trip test constructor
- **ConnectionConfigTest:** removed `"maxItems":4` from old-blob JSON fixture (field no longer exists; `ignoreUnknownKeys` would handle it anyway)

**Preserved (by design):** `Palette.generate(maxItems=...)` calls in `TokenBridgeTest`, `ThemeResolverTest`, and `StatusOverrideTokenBridgeTest` are unchanged — these use the surviving Palette function parameter, not the deleted ThemeTuple axis.

Full host unit suite gate: `:app:testDebugUnitTest` BUILD SUCCESSFUL.

## Acceptance Criteria Verification

| Check | Expected | Result |
|-------|----------|--------|
| `grep -rc 'KEY_MAX_ITEMS\|setMaxItems\|MAX_ITEMS_RANGE\|DEFAULT_MAX_ITEMS' ThemePrefs.kt` | 0 | 0 |
| `grep -c 'maxItems' Profile.kt` | 0 | 0 |
| `grep -c 'DEFAULT_POOL_MAX_ITEMS' ThemeResolver.kt` | >=1 | 5 |
| `grep -c 'maxItems = 8' TemperatureScreen.kt` | 1 | 1 |
| `grep -c 'fun generate' Palette.kt` | 1 (unchanged) | 1 |
| `grep -c 'tuple.maxItems' AppContainer.kt` | 0 | 0 |
| `grep -c 'ignoreUnknownKeys' ProfileStore.kt` | >=1 | 1 |
| `:app:compileDebugKotlin` | BUILD SUCCESSFUL | PASS |
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL | PASS |

Note: `grep -rln 'maxItems' test/theme test/config | grep -v PaletteGoldenTest` returns 4 files — but all surviving references are `Palette.generate(maxItems=...)` calls in `TokenBridgeTest`, `ThemeResolverTest`, and `StatusOverrideTokenBridgeTest` (the surviving Palette function param, explicitly preserved per plan). No ThemeTuple/Profile/ThemePrefs `maxItems` references remain in tests.

## Deviations from Plan

None — plan executed exactly as written. The Palette.generate call-site `maxItems` references in test helpers were correctly identified as preserved (D-17 scope: Palette.generate param survives, ThemeTuple/Profile axis deleted).

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. The only schema change is a deletion (Profile.maxItems field removed from PersistedProfile), mitigated by the pre-existing `ignoreUnknownKeys = true` in ProfileStore (T-28-04-01).

## Self-Check: PASSED

- `ThemePrefs.kt` exists: FOUND
- `Profile.kt` exists: FOUND
- Commit `8f8277b` (refactor: source changes): FOUND
- Commit `643423c` (test: test updates): FOUND
- `:app:testDebugUnitTest` BUILD SUCCESSFUL: CONFIRMED
