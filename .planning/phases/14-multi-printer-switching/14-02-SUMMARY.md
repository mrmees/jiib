---
phase: 14-multi-printer-switching
plan: 02
subsystem: di-spine-wiring
tags: [appcontainer, active-config, distinctUntilChanged, theme-reseed, datastore, wave-1, multi-printer]
requires:
  - "14-01: ProfileStore (profiles + activeId flows, writers), Profile.toConnectionConfig()/toThemeResolved()"
provides:
  - "AppContainer.activeProfile — combine(profiles, activeId) pure pick-by-id (null on dangling id)"
  - "AppContainer.activeConfig — activeProfile.map { toConnectionConfig() }.distinctUntilChanged() (the service rebind source; suppresses name/theme-only edits, T-14-04)"
  - "AppContainer.profileStore — ProfileStore wired over the new profiles.preferences_pb DataStore"
  - "hasConfig generalized to 'has active profile' (D-11) — drives routing off the Connect prompt"
  - "Per-profile theme re-seed (D-08): seedTheme re-applies the active profile's (base,deltas,fs) in one ThemeResolver.apply; themePrefs retained as the no-active-profile / new-profile fallback"
  - "MoonrakerService consumes activeConfig via the UNCHANGED runConfigLoop seam (the only service edit)"
affects:
  - "plan 03+ (Devices UI) calls profileStore.setActive — the switch is just 'activeConfig emits a different value'; this plan made the spine react to it"
  - "plan 06 fills the on-device persistence/switch instrumented assertions"
tech-stack:
  added: []
  patterns:
    - "RESEARCH Pattern 2: combine→pick-by-id→map→distinctUntilChanged for the active-ConnectionConfig derivation"
    - "RESEARCH Pattern 3: flatMapLatest over activeProfile → toThemeResolved() with themePrefs/DEFAULT fallback, single atomic apply(base,deltas,fs)"
    - "distinctUntilChanged on the connection projection is the load-bearing spine-churn suppressor (T-14-04)"
    - "single rebind path: only the configFlow ARG changes; runConfigLoop body byte-unchanged (T-14-05)"
    - "DataStore single-writer: the 5th file (profiles.preferences_pb) created ONCE in DinghyApp, injected"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/DinghyApp.kt
    - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
    - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt
    - app/src/test/java/works/mees/dinghy/theme/ProfileThemeSeedTest.kt
decisions:
  - "ActiveConfigDerivationTest kept as the standalone Pattern-2 pipeline (plan explicitly permits this when full-AppContainer host construction is impractical — FakeDataStore emits a fixed single flow and cannot drive multi-emission derivation). It already asserts the exact required behavior over the same combine→pick→map→distinctUntilChanged shape lifted into the container."
  - "themePrefs RETAINED (not deleted) — it is now the no-active-profile idle theme + the new-profile default seed, per RESEARCH Pattern 3 (NOT dead code)."
  - "connectionStore field left in the container untouched (D-07 — dead but kept until the phase is verified)."
metrics:
  duration: ~3min
  completed: 2026-06-05
---

# Phase 14 Plan 02: Active-Config Derivation + Per-Profile Theme Re-seed Summary

Wired the profile model into the spine: `AppContainer` now derives `activeConfig` (the active profile's
`ConnectionConfig`, behind `distinctUntilChanged`) and `activeProfile`, re-seeds the theme per active
profile in one atomic `apply`, and generalizes `hasConfig` to "has active profile" (D-11). `DinghyApp`
owns the new `profiles.preferences_pb` DataStore (single-writer), and `MoonrakerService` consumes
`activeConfig` through its UNCHANGED `runConfigLoop` rebind seam — the entire switching mechanism is now
"`activeConfig` emits a different value" with no second rebind path invented. This is the spine of the phase.

## What Was Built

### Task 1 — AppContainer active-config + theme re-seed + hasConfig + DinghyApp datastore + test ctor (commit `06b2787`)

- **`AppContainer.kt`**: added `profileDataStore: DataStore<Preferences>` as the FIFTH DataStore ctor
  param (after `webcamDataStore`, before `val discovery` — keeping `discovery` last). Wired
  `val profileStore = ProfileStore(profileDataStore)`. Added:
  - `activeProfile: Flow<Profile?>` = `combine(profileStore.profiles, profileStore.activeId) { list, id -> list.firstOrNull { it.id == id } }` — pure pick, dangling id → null (RESEARCH Pattern 2; auto-pick stays in the store writer, D-12).
  - `activeConfig: Flow<ConnectionConfig?>` = `activeProfile.map { it?.toConnectionConfig() }.distinctUntilChanged()` — the `distinctUntilChanged` is LOAD-BEARING: name/theme-only edits collapse to an equal `ConnectionConfig` and are suppressed (no spine churn, T-14-04/Pitfall 1).
  - `hasConfig` now `activeConfig.map { it != null }` (D-11 "has active profile"); no longer references `connectionStore.config`.
  - `seedTheme` rewired to `activeProfile.flatMapLatest { p -> flowOf(p?.toThemeResolved() ?: themePrefs.flow.firstOrNull() ?: ThemePrefs.DEFAULT) }.collect { themeResolver.apply(it.base, it.deltas, it.fs) }` — one re-emit per switch (no flicker); `themePrefs` RETAINED as the idle / new-profile-default fallback (D-08, Pattern 3).
- **`DinghyApp.kt`**: created `profiles.preferences_pb` via `PreferenceDataStoreFactory.create` ONCE
  (single-writer, Pitfall 3) and passed `profileDataStore = profileDataStore` into `AppContainer(...)`.
  `seedTheme(appScope)` call site unchanged.
- **`AppContainerTest.kt`**: `newContainer()` updated to the new 5-store ctor (five `FakeDataStore()` +
  `lazyDiscovery()`) — MANDATORY so the whole test sourceset compiles ([[dinghy-wave0-red-scaffold-compile]]).
  `FakeDataStore` reads as an empty profile store, so the spine-publication assertions are unaffected.

### Task 2 — MoonrakerService config source + theme-switch test strengthening (commit `8ff26dc`)

- **`MoonrakerService.kt`**: ONE edit — `runConfigLoop(configFlow = container.activeConfig, ...)` (was
  `container.connectionStore.config`). The `runConfigLoop` body with its `cancelAndJoin()`-before-rebuild
  is byte-unchanged — no second rebind path (T-14-05).
- **`ProfileThemeSeedTest.kt`**: added `switchingActiveProfile_yieldsTheNewProfilesThemeTriple` (two
  distinct profiles resolve to two DISTINCT token sets — the re-seed is observable) and
  `corruptPrimitivesProfile_failsSafeToThemePrefsDefault` (corrupt-everything → `ThemePrefs.DEFAULT`).
- **`ActiveConfigDerivationTest.kt`** (unchanged from 14-01): already asserts the exact Task-2 behavior —
  name/theme-only edit → ONE `ConnectionConfig`; host/port edit → TWO; dangling active-id → null — over
  the same `combine→pick→map→distinctUntilChanged` pipeline now living in the container.

## Deviations from Plan

None — plan executed exactly as written. No bugs, blocking issues, architectural changes, auth gates, or
package installs encountered. (Per the plan's explicit allowance, `ActiveConfigDerivationTest` was kept as
the standalone Pattern-2 pipeline rather than re-routed through a host-constructed `AppContainer`, because
`FakeDataStore` emits a fixed single flow and cannot drive the multi-emission derivation the test asserts —
the contract and pipeline shape are identical to the container's.)

## Verification

- `gw.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:testDebugUnitTest --tests …AppContainerTest` — **BUILD SUCCESSFUL** (whole test sourceset compiles; AppContainerTest GREEN).
- `gw.bat :app:testDebugUnitTest --tests …ActiveConfigDerivationTest --tests …ProfileThemeSeedTest --tests …MoonrakerServiceTest` — **GREEN** (derivation, theme-switch/fail-safe, cancel-before-rebuild seam).
- `gw.bat :app:testDebugUnitTest` (FULL suite) — **BUILD SUCCESSFUL** — no regression from the config-source swap or the 5-store ctor change.
- Source assertions: `AppContainer` ctor has `profileDataStore` as the 5th DataStore before `discovery`;
  defines `activeProfile` (combine), `activeConfig` (`.map { it?.toConnectionConfig() }.distinctUntilChanged()`),
  `hasConfig` reads `activeConfig.map { it != null }`; `seedTheme` flat-maps `activeProfile.toThemeResolved()`
  with the `?: themePrefs.flow.firstOrNull() ?: ThemePrefs.DEFAULT` fallback. `DinghyApp` creates
  `profiles.preferences_pb` once. `MoonrakerService` line ~94 reads `configFlow = container.activeConfig`;
  `runConfigLoop` body unchanged. No `publishSpine` / disconnect sequencer added in the diff.

## Threat Mitigations Applied

- **T-14-04 (DoS via spine churn):** `distinctUntilChanged` after `map { toConnectionConfig() }` suppresses
  name/theme-only edits — asserted in `ActiveConfigDerivationTest`. (On-device "no Splash on accent change"
  eyeball is plan 06.)
- **T-14-05 (parallel rebind path):** only the `configFlow` arg changed; `runConfigLoop` body unchanged;
  no UI/service `publishSpine` added — `MoonrakerServiceTest` (cancel-before-rebuild) still GREEN.
- **T-14-06 (API-key disclosure):** the derived `ConnectionConfig` inherits the existing redacting
  `toString()`; no new sink. The new `profiles.preferences_pb` carries the key, so `Profile`/`PersistedProfile`
  redact it (from 14-01) and the DinghyApp comment flags the redaction discipline.

## Known Stubs

None. (`ProfileSurvivesRestartTest` instrumented stub remains owned by plan 06, per 14-01.)

## Self-Check: PASSED

- Modified files present on disk: `AppContainer.kt`, `DinghyApp.kt`, `MoonrakerService.kt`,
  `AppContainerTest.kt`, `ProfileThemeSeedTest.kt` (all verified).
- Commits `06b2787` (Task 1) and `8ff26dc` (Task 2) exist in git log (verified).
- No second rebind path introduced; `connectionStore`/`themePrefs` retained (D-07 / Pattern 3).
