---
phase: 14-multi-printer-switching
plan: 01
subsystem: config-persistence
tags: [datastore, profiles, multi-printer, serialization, theme-primitives, wave-0]
requires: []
provides:
  - "PersistedProfile @Serializable wire form + runtime Profile (id/name/host/port/apiKey + theme primitives)"
  - "Profile.toConnectionConfig() — the distinctUntilChanged anchor for plan 02's activeConfig"
  - "Profile.toThemeResolved() — per-printer theme triple via ThemePrefs.sanitize parity (D-08)"
  - "ProfileStore — DataStore-backed profile blob + active-id, fail-safe read, pure sanitize"
  - "ProfileStore.upsert (D-11 first-add-active) / delete (D-12 auto-pick) / setActive writers"
  - "ProfileStore.planUpsert / planDelete — PURE host-testable writer decisions"
  - "Wave-0 scaffolds: ActiveConfigDerivationTest, ProfileThemeSeedTest, ProfileSurvivesRestartTest"
affects:
  - "plan 02 lifts the local activeConfig pipeline + theme re-seed into AppContainer"
  - "plan 06 fills ProfileSurvivesRestartTest with real on-device persistence assertions"
tech-stack:
  added: []
  patterns:
    - "ConnectionStore shape copied verbatim (injected DataStore, .catch IOException, pure sanitize)"
    - "ThemePrefs persisted-primitives shape reused for per-profile theme (never baked ThemeTokens)"
    - "active-id is writer-owned (D-11/D-12), never a read derivation"
    - "PURE planUpsert/planDelete seam to host-test multi-write logic around the Windows DataStore rename race"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/config/Profile.kt
    - app/src/main/java/works/mees/dinghy/config/ProfileStore.kt
    - app/src/test/java/works/mees/dinghy/config/ProfileStoreTest.kt
    - app/src/test/java/works/mees/dinghy/di/ActiveConfigDerivationTest.kt
    - app/src/test/java/works/mees/dinghy/theme/ProfileThemeSeedTest.kt
    - app/src/androidTest/java/works/mees/dinghy/service/ProfileSurvivesRestartTest.kt
  modified: []
decisions:
  - "D-11 first-add-is-active + D-12 auto-pick implemented in the writers, proven via PURE planUpsert/planDelete (host-testable) because back-to-back DataStore edits hit the Windows .tmp-rename race"
  - "explicit ListSerializer(PersistedProfile.serializer()) — the reified encodeToString<List<…>> is ambiguous on this Kotlin/serialization version"
metrics:
  duration: ~20min
  completed: 2026-06-05
---

# Phase 14 Plan 01: Profile Model + ProfileStore Foundation Summary

Built the Phase-14 data foundation — `PersistedProfile`/`Profile` model and the DataStore-backed
`ProfileStore` (profile set + active-id) copying `ConnectionStore`'s shape verbatim, plus the four
Wave-0 test scaffolds the rest of the phase turns green. No new dependencies; pure generalize-existing-patterns.

## What Was Built

### Task 1 — Profile model + ProfileStore (commit `be852de`)

- **`Profile.kt`**: `@Serializable PersistedProfile` (wire form) + runtime `Profile`, both with a
  redacting `toString()` masking `apiKey` to `***` (V7/T-14-01). `toConnectionConfig()` projects
  host/port/apiKey ONLY (the `distinctUntilChanged` anchor so name/theme edits don't churn the spine),
  `displayName()` falls back to host (D-10), `toThemeResolved()` reuses `ThemePrefs.sanitize` parity so
  corrupt theme primitives fail safe (D-08). `Profile.newId()` = `UUID.randomUUID()` (D-05).
- **`ProfileStore.kt`**: injected `DataStore<Preferences>`, fail-safe `.catch { IOException → emptyPreferences() }`
  read flows for `profiles` + `activeId`, suspend `upsert`/`delete`/`setActive` writers, and a PURE
  `sanitize(rawBlob)` companion (decode in `runCatching`, drop blank-host/out-of-range-port entries).
  `upsert` auto-selects the first-added profile active (D-11); `delete` auto-picks a remaining profile
  or clears active-id (D-12) — both in the writer, via PURE `planUpsert`/`planDelete` decisions.
- **`ProfileStoreTest.kt`** GREEN: pure sanitize (null/blank/corrupt/truncated → empty, malformed
  entries dropped, port boundaries), redaction, `toConnectionConfig` name/theme-invariance, the D-11
  first-add round-trip on a real temp-file DataStore, and the D-11/D-12 multi-write logic via the pure planners.

### Task 2 — Wave-0 RED scaffolds (commit `1cf809d`)

- **`ActiveConfigDerivationTest`**: builds the RESEARCH-Pattern-2 pipeline LOCALLY
  (`combine → pick → map { toConnectionConfig() } → distinctUntilChanged`) and proves a name/theme-only
  edit collapses to ONE `ConnectionConfig` (Pitfall 1), a host/port edit re-emits, and a dangling
  active-id resolves to null. Plan 02 lifts this same shape into the container.
- **`ProfileThemeSeedTest`**: exercises `Profile.toThemeResolved()` (real) — base/fs/delta mapping plus
  fail-safe on corrupt `themeBase="Banana"`/`fsChoice="XXL"`/junk delta role.
- **`ProfileSurvivesRestartTest`**: compile-clean instrumented pending stub (typed `fail(...)`, no
  unbuilt-wiring refs), filled on-device in plan 06.

Both unit + androidTest sourcesets compile day-one; the two host tests pass against Task-1 symbols.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Explicit list serializer for the JSON blob**
- **Found during:** Task 1 verification (`compileDebugKotlin` failed)
- **Issue:** `json.encodeToString(next)` / `decodeFromString<List<PersistedProfile>>(...)` — the reified
  `encodeToString` extension is ambiguous/unresolvable on this Kotlin/serialization version (inferred the
  arg as a `SerializationStrategy`).
- **Fix:** Use the explicit `ListSerializer(PersistedProfile.serializer())` form for both encode and decode.
- **Files modified:** `ProfileStore.kt`, `ProfileStoreTest.kt` (test blob helper).
- **Commit:** `be852de`

**2. [Rule 3 - Blocking] PURE planUpsert/planDelete seam for multi-write writer tests**
- **Found during:** Task 1 verification (6 tests `IOException at FileStorage.kt:121`)
- **Issue:** The second-add / edit-active / delete-auto-pick tests do back-to-back DataStore `edit`s on
  one `.preferences_pb`, hitting the documented Windows `.tmp`→final rename race ("multiple instances of
  DataStore") — a host-filesystem quirk, NOT a product bug (rename-over-open is atomic on Android/Linux).
  This is the exact constraint `ConnectionStoreTest` works around and VALIDATION §mock-vs-reality flags
  (DataStore persistence is not host-faithful).
- **Fix:** Extracted the writer DECISION logic into PURE host-testable `planUpsert`/`planDelete` companion
  functions (returning an `UpsertPlan`/`DeletePlan` with an `ActiveIdWrite` sealed result); the `upsert`/
  `delete` suspend writers now delegate to them. The plan's acceptance criteria explicitly permits this
  fallback ("assert the writer logic via a thin testable seam"). The single-write D-11 first-add path
  still round-trips on the real DataStore; full cross-process persistence is the instrumented test's job (plan 06).
- **Files modified:** `ProfileStore.kt`, `ProfileStoreTest.kt`
- **Commit:** `be852de`

Both deviations are auto-fixes for blocking/correctness issues; no architectural change.

## Verification

- `gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.ProfileStoreTest` — **GREEN** (19 tests).
- `gw.bat :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin` — **SUCCESS** (RED scaffolds compile day-one).
- `gw.bat :app:testDebugUnitTest --tests …ProfileThemeSeedTest --tests …ActiveConfigDerivationTest` — **GREEN**.
- Source assertions: `PersistedProfile` is `@Serializable` with redacting `toString()`; `upsert`/`delete`
  reference `KEY_ACTIVE_ID` via the writer planners; `sanitize` is a pure `String? → List<Profile>` wrapping
  decode in `runCatching`; diff introduces NO `publishSpine`/`runConfigLoop`/disconnect rebind path;
  `ConnectionStore.kt` left in the tree (D-07).

## Known Stubs

- `ProfileSurvivesRestartTest.activeIdSurvivesProcessDeath()` is an INTENTIONAL pending stub (typed
  `fail(...)`) — instrumented on-device persistence assertions are filled in plan 06 (host DataStore is
  not persistence-faithful per VALIDATION §mock-vs-reality). Documented in the file's KDoc.

## Self-Check: PASSED

- All six created files exist on disk (verified).
- Commits `be852de` and `1cf809d` exist (verified in git log).
- No new rebind path introduced; `ConnectionStore` retained (D-07).
