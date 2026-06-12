---
phase: 14-multi-printer-switching
reviewed: 2026-06-04T00:00:00Z
depth: standard
files_reviewed: 17
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/config/Profile.kt
  - app/src/main/java/works/mees/dinghy/config/ProfileStore.kt
  - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
  - app/src/main/java/works/mees/dinghy/DinghyApp.kt
  - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
  - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/DevicesScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt
  - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamPrefs.kt
  - app/src/test/java/works/mees/dinghy/config/ProfileStoreTest.kt
  - app/src/test/java/works/mees/dinghy/di/ActiveConfigDerivationTest.kt
  - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt
  - app/src/test/java/works/mees/dinghy/theme/ProfileThemeSeedTest.kt
  - app/src/androidTest/java/works/mees/dinghy/service/ProfileSurvivesRestartTest.kt
findings:
  critical: 0
  warning: 3
  info: 5
  total: 8
status: issues_found
---

# Phase 14: Code Review Report

**Reviewed:** 2026-06-04
**Depth:** standard
**Files Reviewed:** 17
**Status:** issues_found

## Summary

Phase 14 generalizes the single persisted `ConnectionConfig` into a managed set of named printer
profiles with a persisted active selection, per-printer full themes, and a Devices switcher. The core
architecture is sound: the `runConfigLoop` rebind seam is genuinely reused unchanged (the service edit
is a one-liner swapping `connectionStore.config → activeConfig`), `distinctUntilChanged` is correctly
placed AFTER the `ConnectionConfig` projection (so name/theme edits do not churn the spine), the
writer-owned active-id (D-11/D-12 auto-pick) lives in pure `planUpsert`/`planDelete` decisions, API-key
redaction is implemented on both `PersistedProfile.toString()` and `Profile.toString()`, and the
`writeScope` gap-closure (781277f) correctly moves navigation-racing writes off composition scope.

The four phase-focus concerns came out mostly clean:
1. **Concurrency/lifecycle:** `writeScope` is a process-lifetime `SupervisorJob`+`Dispatchers.IO` owned
   by the process-lifetime container — no leak, no cancellation issue. All UI write call-sites verified
   to route through `setActiveProfile`/`saveProfile`/`deleteProfile`; the mDNS scan and the
   no-active-profile global-theme branch correctly stay composition-scoped.
2. **Rebind seam:** `collectLatest` + `cancelAndJoin` is unchanged and drives exactly one clean
   teardown+rebuild per distinct config.
3. **Security:** both `toString()` overrides redact the key; no key reaches a log/notification.
4. **`distinctUntilChanged`:** structurally correct.

The defects found are a genuine **lost-update race on rapid active-profile theme edits** (WR-01), a
**non-live global-theme re-seed when no profile is active** (WR-02), and a **fragile empty-string
webcam pref key** (WR-03), plus several documentation/test-fidelity Info items. No Critical issues.

## Warnings

### WR-01: Rapid active-profile theme edits race → lost update (last-writer-clobbers-field)

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt:432,442,457-458,477,488` (via `persistBase`/`persistFs`/`persistDeltas`) → `app/src/main/java/works/mees/dinghy/di/AppContainer.kt:113-115`

**Issue:** Every Appearance control persists by capturing the *currently-composed* `activeProfile`
snapshot and calling `container.saveProfile(active.copy(<oneField> = ...))`. `saveProfile` does
`writeScope.launch { profileStore.upsert(profile) }` on a multi-threaded `Dispatchers.IO` scope, so
each tap launches an independent coroutine carrying a profile value built from the `active` snapshot
that was current *at tap time*. The state-backed `active` only refreshes after the DataStore round-trip
recomposes. So if the user taps "Dark" then taps an accent swatch before recomposition lands, the
second write is built off the **pre-Dark** profile and its `upsert` (replace-by-id) overwrites the whole
entry — silently reverting `themeBase` back to Light. The two `copy()` calls touch different fields but
each persists the *entire* profile, so it is a classic read-modify-write lost update. `upsert` itself is
atomic within one `dataStore.edit`, but it re-encodes the stale full object, defeating that atomicity.
The instrumented/host tests never tap two Appearance controls back-to-back, so this is invisible to the
suite (another mock-vs-reality gap).

**Fix:** Make the active-profile theme writes read-modify-write *inside* the store under one
serialized actor, e.g. add a `ProfileStore.updateActiveTheme { it.copy(...) }` writer that, inside a
single `dataStore.edit`, decodes the blob, finds the active profile by the persisted active-id, applies
the field mutation, and re-encodes — so concurrent field edits compose instead of clobber:
```kotlin
// ProfileStore
suspend fun mutateActive(transform: (PersistedProfile) -> PersistedProfile) {
    dataStore.edit { prefs ->
        val list = decode(prefs[KEY_PROFILES]).toMutableList()
        val activeId = prefs[KEY_ACTIVE_ID] ?: return@edit
        val idx = list.indexOfFirst { it.id == activeId }
        if (idx >= 0) list[idx] = transform(list[idx])
        prefs[KEY_PROFILES] = json.encodeToString(PROFILE_LIST_SERIALIZER, list)
    }
}
```
Then `persistBase` becomes `container.mutateActiveProfile { it.copy(themeBase = next.name) }`, removing
the stale-snapshot capture entirely.

### WR-02: Global theme edits are not re-seeded live when no profile is active

**File:** `app/src/main/java/works/mees/dinghy/di/AppContainer.kt:304-313`

**Issue:** `seedTheme` collects `activeProfile.flatMapLatest { p -> flowOf(p?.toThemeResolved() ?:
themePrefs.flow.firstOrNull() ?: ThemePrefs.DEFAULT) }`. When there is no active profile (first-run /
Connect / idle), the inner flow is a one-shot `flowOf(themePrefs.flow.firstOrNull())` — it samples the
global theme *once* and never re-collects it. If the user edits the theme in Settings while no profile
is active, `persistBase`/`persistFs`/`persistDeltas` write to `themePrefs`, but the resolver is NOT
re-seeded from the new persisted value because `activeProfile` does not re-emit. The visible theme
still updates because the Settings call-sites also call `themeResolver.setBase/setFs/setDeltas` live, so
the bug is masked in the active session — but a *cold restart while still profile-less* will lose any
idle theme tweak that did not also flow through the live resolver, and any other surface that relies on
the seed (not the live setters) sees a stale idle theme. The research narrative claims `themePrefs` is
"the no-active-profile idle theme"; this wiring only honors that at process start, not live.

**Fix:** Collect `themePrefs.flow` continuously in the no-active branch instead of sampling it once:
```kotlin
activeProfile.flatMapLatest { p ->
    if (p != null) flowOf(p.toThemeResolved())
    else themePrefs.flow            // live idle-theme updates, not firstOrNull()
}.collect { resolved -> themeResolver.apply(resolved.base, resolved.deltas, resolved.fs) }
```
(`themePrefs.flow` already fail-safes to `DEFAULT`, so the final `?: ThemePrefs.DEFAULT` is redundant.)

### WR-03: Webcam preferred-cam pref falls back to an empty-string key (`""`) when no active profile id

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:209-219` (`profileId = activeProfileId ?: ""`) → `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamPrefs.kt:69`

**Issue:** The webcam holder is built with `profileId = activeProfileId ?: ""`. When `activeProfileId`
is null, the preferred-cam pref is read/written under the literal key `preferred_cam_` (empty suffix).
Today the Webcam surface is only reachable with an active profile (so the null branch is rarely hit),
making this latent — but it is a shared global bucket: *any* profile-less state that touches the webcam
holder reads/writes the same `preferred_cam_` entry, so it is not per-printer-isolated for that edge,
which is exactly the property D-06 exists to guarantee. A future phase that surfaces the cam before an
active profile resolves (or a transient null during a switch) would cross-contaminate cam selections.

**Fix:** Skip the pref entirely when there is no profile id rather than collapsing to a shared empty
key — e.g. have `WebcamPrefs.preferredCam`/`setPreferredCam` early-return null/no-op on a blank
`profileId`, or guard the holder so a blank id never resolves/writes a preferred cam:
```kotlin
fun preferredCam(profileId: String): Flow<String?> =
    if (profileId.isBlank()) flowOf(null)
    else dataStore.data.catch { ... }.map { it[preferredCamKey(profileId)] }

suspend fun setPreferredCam(profileId: String, camId: String) {
    if (profileId.isBlank()) return
    dataStore.edit { it[preferredCamKey(profileId)] = camId }
}
```

## Info

### IN-01: Stale KDoc on `MoonrakerService` still references `ConnectionStore.config`

**File:** `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt:56-58`

**Issue:** The class doc says "The service collects `ConnectionStore.config` via `collectLatest`." The
actual `onCreate` now feeds `container.activeConfig` (line 99). The inline comment at 94-98 correctly
explains the Phase-14 change, but the class-level KDoc was not updated, so a future reader gets a
contradicting description of the config source.

**Fix:** Update the KDoc to "collects `AppContainer.activeConfig` (the active profile's connection) via
`collectLatest`" to match the implementation.

### IN-02: Stale KDoc on `DinghyApp` says "THREE SEPARATE preference files"

**File:** `app/src/main/java/works/mees/dinghy/DinghyApp.kt:20`

**Issue:** The class KDoc opens "THREE SEPARATE preference files: `connection`, `theme`, `macros`." The
code now creates five (`theme`, `connection`, `macros`, `webcam`, `profiles`). The five `val`s are each
individually well-commented, but the summary count is wrong.

**Fix:** Change "THREE" to "FIVE" and list `webcam.preferences_pb` and `profiles.preferences_pb`.

### IN-03: Stale doc on `AppContainer.themeResolver` / `webcamPrefs` predates per-profile theming

**File:** `app/src/main/java/works/mees/dinghy/di/AppContainer.kt:175,153-159`

**Issue:** `themeResolver` is documented "seeded below from `themePrefs`" (line 175) but it is now seeded
from the *active profile's* theme with `themePrefs` only as the no-active fallback (D-08, `seedTheme`).
Likewise the `webcamPrefs` KDoc (153-159) still describes the key as `preferred_cam_<host>` though D-06
re-keyed it to the profile id (the `WebcamPrefs` class itself is updated; only this container-side
comment lags). Comments only — no behavior impact.

**Fix:** Update both KDocs to reflect active-profile seeding and the `preferred_cam_<profileId>` key.

### IN-04: `AppContainerTest.FakeDataStore.updateData` ignores the transform (silent no-op writes)

**File:** `app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt:34-38`

**Issue:** The fake's `updateData` returns `emptyPreferences()` without applying `transform`, so any
`upsert`/`setActive`/`delete` against a container built on this fake silently does nothing. These tests
deliberately do not exercise the profile writers, so it is currently harmless — but it is exactly the
kind of too-lenient fake the project's documented mock-vs-reality history warns about; a future test
added to this class that assumes writes round-trip would pass falsely.

**Fix:** Either make the fake apply the transform over an in-memory `MutableStateFlow<Preferences>`, or
add a comment asserting this fake is read-only and must not be used to test writers (the real-store
`ProfileStoreTest` is the writer authority).

### IN-05: `ProfileStoreTest.settle()` uses `Thread.sleep` + `System.gc()` — flaky timing guard

**File:** `app/src/test/java/works/mees/dinghy/config/ProfileStoreTest.kt:281-287`

**Issue:** The round-trip tests serialize DataStore writes by sleeping 120ms and forcing `System.gc()`
to release the file actor between operations. This is documented as the Windows host `.tmp`→rename race
workaround, but a fixed-sleep settle is inherently timing-fragile on a loaded CI host (the authoritative
persistence proof is correctly delegated to the instrumented `ProfileSurvivesRestartTest`). Acceptable
given the documented host constraint; flagged so a future intermittent failure here is recognized as
the known race, not a logic regression.

**Fix:** No change required; optionally raise the sleep or gate these three single-write round-trips
behind an assumption so a host-host flake is a skip, not a red.

---

## Summary Table

| ID    | Severity | File:Line | Issue |
|-------|----------|-----------|-------|
| WR-01 | Warning  | SettingsScreen.kt:432–488 / AppContainer.kt:113 | Rapid active-profile theme edits race → lost-update field clobber (stale-snapshot read-modify-write) |
| WR-02 | Warning  | AppContainer.kt:304–313 | `seedTheme` samples global theme once (`firstOrNull`) when no active profile → idle theme edits not re-seeded live |
| WR-03 | Warning  | AppShell.kt:209 / WebcamPrefs.kt:69 | `profileId ?: ""` collapses to a shared `preferred_cam_` key, breaking per-printer isolation on the null edge |
| IN-01 | Info     | MoonrakerService.kt:56 | KDoc still says `ConnectionStore.config`; code uses `activeConfig` |
| IN-02 | Info     | DinghyApp.kt:20 | KDoc says "THREE" preference files; there are five |
| IN-03 | Info     | AppContainer.kt:175,153 | `themeResolver`/`webcamPrefs` KDocs predate per-profile theming + profile-id cam key |
| IN-04 | Info     | AppContainerTest.kt:34 | Fake `updateData` ignores transform → silent no-op writes (latent mock-vs-reality trap) |
| IN-05 | Info     | ProfileStoreTest.kt:281 | `Thread.sleep`+`gc()` settle is timing-fragile (known host race) |

---

_Reviewed: 2026-06-04_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
