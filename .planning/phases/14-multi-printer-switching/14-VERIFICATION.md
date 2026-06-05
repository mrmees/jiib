---
phase: 14-multi-printer-switching
verified: 2026-06-04T00:00:00Z
status: human_needed
score: 9/9 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Mid-print switch (D-04): with a print running on one printer, switch to the other and back"
    expected: "The print continues untouched; the tablet just changes which printer it watches"
    why_human: "UAT item 4 was owner-deferred by Matthew (filament cost). The rebind path is proven by items 1/2, but the live in-progress-print boundary has not been eyeballed on-device. Run opportunistically the next time a print is genuinely running."
---

# Phase 14: Multi-Printer Switching Verification Report

**Phase Goal:** Managed printer profiles (name + host/port/key, DataStore-persisted) with a clean service spine rebind on switch, so the Ender 5 Plus and Ender 3 Pro are both first-class — foundational, so later new per-printer surfaces are built multi-printer-aware.
**Verified:** 2026-06-04
**Status:** human_needed (all code truths VERIFIED; one owner-deferred live scenario remains)
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | ProfileStore.sanitize drops malformed profiles, recovers from corrupt blob; upsert into empty store auto-selects new profile as active (D-11); delete auto-picks another remaining (D-12); PersistedProfile.toString() redacts apiKey | VERIFIED | `ProfileStore.kt`: pure `planUpsert`/`planDelete` companion functions confirmed in code. `sanitize` wraps decode in `runCatching`. `upsert` sets `KEY_ACTIVE_ID` only when `currentActiveId.isNullOrBlank()`. `delete` uses `ActiveIdWrite.Set/Clear/Unchanged` sealed interface. `PersistedProfile.toString()` confirmed: `"apiKey=${if (apiKey != null) "***" else "null"}"`. 26-test `ProfileStoreTest.kt` covers all these branches. |
| 2 | activeConfig emits the active profile's ConnectionConfig; name/theme-only edits do NOT re-emit (distinctUntilChanged suppresses non-connection edits) | VERIFIED | `AppContainer.kt:150-151`: `val activeConfig: Flow<ConnectionConfig?> = activeProfile.map { it?.toConnectionConfig() }.distinctUntilChanged()`. `Profile.toConnectionConfig()` confirmed to project host/port/apiKey ONLY (name/theme absent). `ActiveConfigDerivationTest` has three tests covering no-churn on name/theme edit, churn on host/port edit, and dangling active-id → null. UAT item 3 confirmed on-device (no Splash on accent change). |
| 3 | The rebind seam (runConfigLoop cancel-before-rebuild) stays the sole data path — no second rebind path introduced | VERIFIED | `MoonrakerService.kt:101`: `configFlow = container.activeConfig` — confirmed as the single edit. `runConfigLoop` body with `cancelAndJoin()` unchanged. No `publishSpine`/disconnect code in `DevicesScreen.kt` or `AppShell.kt` — both files have explicit comments about this (T-14-11). `MoonrakerServiceTest` remains GREEN. |
| 4 | Switching profiles re-seeds ThemeResolver from the active profile's theme triple atomically (no stale theme); hasConfig is now "has an active profile" | VERIFIED | `AppContainer.kt:314-321`: `seedTheme` uses `activeProfile.flatMapLatest { p -> flowOf(p?.toThemeResolved() ?: themePrefs.flow.firstOrNull() ?: ThemePrefs.DEFAULT) }`. `hasConfig` at line 276: `val hasConfig: Flow<Boolean> = activeConfig.map { it != null }` (no longer references `connectionStore.config`). UAT item 1 + 2 confirmed full-app look flips per printer on-device (D-08). |
| 5 | DevicesScreen lists one tile per saved profile (active highlighted), tapping a profile persists it active and the seam rebinds; after a switch tap the shell explicitly navigates to Dest.PrintStatus; swipe-up drawer suppressed; green gutter Back | VERIFIED | `DevicesScreen.kt` (243 lines, substantive): `container.setActiveProfile(profile.id)` then `onSwitched()` on tile tap. No `ConfirmGuard` on switch path. `AppShell.kt:599`: `onSwitched = { navigateTo(Dest.PrintStatus) }`. `AppShell.kt:431`: `Dest.Devices` in the swipe-suppress `setOf(...)`. Green `Intent.Go` gutter Back confirmed. UAT item 2 PASS on flox + E5/E3. |
| 6 | Devices drawer tile is LIVE (dest = Dest.Devices) showing active-printer name as subtitle; Settings Connection section is a profile-list CRUD (add/edit/delete behind ConfirmGuard) with Appearance retargeted to active profile | VERIFIED | `AppDrawer.kt:177`: `DrawerTileSpec(label = "Devices", symbol = "cable", dest = Dest.Devices)`. `AppDrawer.kt:107`: `subtitle = if (tile.dest == Dest.Devices) activeName else null`. `AppShell.kt:197`: collects `activeProfile.map { it?.displayName() }` and passes `activeName`. `SettingsScreen.kt`: collects `profileStore.profiles`/`activeId`, ConfirmGuard with `title = "Delete printer?"`, calls `container.profileStore.delete`. Appearance persist now uses `container.mutateActiveProfile { it.copy(themeBase=…) }` (WR-01 fixed in commit 4e4d83b). UAT items 1, 3, 5 PASS. |
| 7 | Preferred-webcam pref is re-keyed on active profile id (D-06), not host | VERIFIED | `WebcamPrefs.kt:69`: `fun preferredCamKey(profileId: String) = stringPreferencesKey("preferred_cam_$profileId")`. `WebcamHolder.kt:85`: `private val profileId: String` (not `host`). Both pref call sites pass `profileId`. `AppShell.kt:215`: `profileId = activeProfileId ?: ""` threads into `webcamBitmapHolder`. `AppShell.kt:209`: `remember(store, activeCfg.host, activeProfileId, …)` keyed on profile id. |
| 8 | Persisted active-profile id survives process death (instrumented, on-device) | VERIFIED | `ProfileSurvivesRestartTest.kt`: real `DataStore<Preferences>` via `PreferenceDataStoreFactory.create`, two-store write-then-cold-reread pattern (no `fail(…)` stub remaining). Writes profile B as active, re-reads via second store, asserts `activeId.first() == profileB.id`. Confirmed GREEN on flox at commit `7bb52dc` per UAT prereqs. |
| 9 | Live two-printer switch + drive-each + no-re-entry succeeds on real hardware (SC-4); delete auto-pick confirmed; survival confirmed | VERIFIED | `14-UAT.md`: items 1/2/3/5/6 all PASS on flox + live E5+(192.168.1.120:7125) and E3(192.168.1.121:7125). Matthew: "switch works as fast as I can navigate the screens to do it." The G-1 intermittent-revert root cause (composition-scoped writes cancelled mid-navigation) was fixed in commit `781277f` (durable `writeScope` in `AppContainer`), then re-verified PASS. 0 FAIL items. |

**Score:** 9/9 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/works/mees/dinghy/config/Profile.kt` | PersistedProfile @Serializable + apiKey redaction + toConnectionConfig/displayName/toThemeResolved | VERIFIED | 119 lines; `@Serializable data class PersistedProfile`; `toString()` masks apiKey; `fun toConnectionConfig()` projects host/port/apiKey only |
| `app/src/main/java/works/mees/dinghy/config/ProfileStore.kt` | DataStore-backed profile set + active-id, sanitize, planUpsert/planDelete, mutateActive | VERIFIED | 207 lines; `sanitize` is pure companion; `planUpsert`/`planDelete` pure decisions; `mutateActive` atomic read-modify-write (WR-01 fix) |
| `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` | profileStore, activeProfile, activeConfig (distinctUntilChanged), hasConfig, writeScope, durable write API, seedTheme per active profile | VERIFIED | All wiring confirmed: `profileStore`, `activeProfile`, `activeConfig`, `hasConfig`, `writeScope`, `setActiveProfile`/`saveProfile`/`deleteProfile`/`mutateActiveProfile` |
| `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt` | configFlow = container.activeConfig (the ONLY service edit) | VERIFIED | Line 101: `configFlow = container.activeConfig`. runConfigLoop body unchanged. |
| `app/src/main/java/works/mees/dinghy/DinghyApp.kt` | profiles.preferences_pb DataStore created once, passed to AppContainer | VERIFIED | Lines 72-74: `PreferenceDataStoreFactory.create` with `"profiles.preferences_pb"`. Passed as `profileDataStore = profileDataStore`. |
| `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` | Dest.Devices in enum | VERIFIED | Line 31: `enum class Dest { PrintStatus, Temperature, Move, Extrude, Files, Macros, Console, Calibration, Webcam, Spool, Devices, Settings }` |
| `app/src/main/java/works/mees/dinghy/ui/screen/DevicesScreen.kt` | Full-screen switcher, ScreenScaffold Field + gutter Back, setActive + onSwitched, no confirm | VERIFIED | 243 lines; `ScreenScaffold`; `container.setActiveProfile(profile.id)` then `onSwitched()`; no ConfirmGuard; Intent.Go gutter Back |
| `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt` | Profile-list CRUD + ConfirmGuard delete + Appearance retargeted to active profile via mutateActiveProfile | VERIFIED | `ConfirmGuard(title = "Delete printer?", …)`; `container.mutateActiveProfile { it.copy(themeBase=…) }` in `persistBase`/`persistFs`/`persistDeltas` private helpers |
| `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` | Devices tile dest=Dest.Devices + activeName subtitle | VERIFIED | `dest = Dest.Devices`; `activeName: String? = null` param; subtitle rendered at 15sp for Devices tile only |
| `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` | Dest.Devices host arm + swipe-suppress + activeName thread + post-switch navigateTo(PrintStatus) | VERIFIED | Swipe-suppress set includes `Dest.Devices`; `onSwitched = { navigateTo(Dest.PrintStatus) }`; `activeName` collected and threaded to AppDrawer |
| `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamPrefs.kt` | preferredCamKey(profileId) not host | VERIFIED | `"preferred_cam_$profileId"` confirmed |
| `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt` | profileId param not host | VERIFIED | `private val profileId: String`; both pref call sites pass `profileId` |
| `app/src/test/java/works/mees/dinghy/config/ProfileStoreTest.kt` | Host tests for sanitize + D-11 + D-12 | VERIFIED | 288 lines, 26 test functions, covering all sanitize variants, redaction, D-11 auto-active, D-12 auto-pick |
| `app/src/test/java/works/mees/dinghy/di/ActiveConfigDerivationTest.kt` | Derivation pipeline no-churn + host/port churn | VERIFIED | 3 @Test methods: nameOrThemeEditDoesNotChurnConfig, hostOrPortEditEmitsADistinctConfig, danglingActiveIdResolvesToNull |
| `app/src/test/java/works/mees/dinghy/theme/ProfileThemeSeedTest.kt` | Profile.toThemeResolved() mapping + corrupt fallback | VERIFIED | 7 @Test methods covering base/fs mapping, delta override, corrupt primitives, and profile switch |
| `app/src/androidTest/java/works/mees/dinghy/service/ProfileSurvivesRestartTest.kt` | Real instrumented DataStore survival test (no fail() stub) | VERIFIED | `fun activeIdSurvivesProcessDeath() = runBlocking { … }` with two real store instances; no `fail(…)` stub |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ProfileStore.upsert` | `KEY_ACTIVE_ID` set when null/blank | `planUpsert` pure decision, atomic in `dataStore.edit` | WIRED | `plan.activeId?.let { prefs[KEY_ACTIVE_ID] = it }` in upsert |
| `ProfileStore.delete` | `KEY_ACTIVE_ID` rewrite (auto-pick D-12) | `planDelete` pure decision, atomic in `dataStore.edit` | WIRED | `ActiveIdWrite.Set/Clear/Unchanged` sealed interface; key mutated inside edit |
| `AppContainer.activeConfig` | `MoonrakerService.runConfigLoop` | `configFlow = container.activeConfig` argument | WIRED | Single-line change confirmed; runConfigLoop body unchanged |
| `AppContainer.seedTheme` | `themeResolver.apply` | `activeProfile.flatMapLatest { … toThemeResolved() … }.collect { … }` | WIRED | `flatMapLatest` confirmed in AppContainer.kt:317-321 |
| `DevicesScreen tile tap` | `profileStore.setActive(id)` + `onSwitched()` | `container.setActiveProfile(profile.id)` then `onSwitched()` | WIRED | Both calls confirmed in DevicesScreen.kt:113-114 |
| `AppShell Dest.Devices arm onSwitched` | `navigateTo(Dest.PrintStatus)` | Explicit D-02 nav in when(dest) arm | WIRED | AppShell.kt:599: `onSwitched = { navigateTo(Dest.PrintStatus) }` |
| `SettingsScreen Appearance` | `container.mutateActiveProfile` | `persistBase`/`persistFs`/`persistDeltas` private helpers | WIRED | Confirmed in SettingsScreen.kt:529/543/560 via `mutateActiveProfile { it.copy(…) }` |
| `AppShell webcam holder build` | `WebcamHolder/webcamBitmapHolder profileId` | `activeProfileId` collected from `container.activeProfile.map { it?.id }` | WIRED | AppShell.kt:194 collects activeProfileId; line 215: `profileId = activeProfileId ?: ""` |
| `AppDrawer Devices tile` | active profile name subtitle | `activeName` threaded from AppShell → AppDrawer → DrawerTile for Devices only | WIRED | AppShell.kt:197 collects activeName; AppDrawer.kt:107 renders subtitle for Dest.Devices tile |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| `DevicesScreen` | `profiles` / `activeId` | `container.profileStore.profiles` / `.activeId` (DataStore-backed flows) | Yes — DataStore-backed, live flows | FLOWING |
| `AppContainer.activeConfig` | `ConnectionConfig?` | `profileStore.profiles` + `profileStore.activeId` → `combine` → `map { toConnectionConfig() }` → `distinctUntilChanged()` | Yes — derives from real DataStore | FLOWING |
| `SettingsScreen` theme persist | `active` profile | `container.activeProfile` (composed from profileStore flows) | Yes — reads from DataStore-backed flow | FLOWING |
| `AppShell` activeName subtitle | `activeName` | `container.activeProfile.map { it?.displayName() }` | Yes — live, derives from profileStore | FLOWING |
| `MoonrakerService` rebind | `cfg` | `container.activeConfig` (the distinctUntilChanged flow) | Yes — live, drives the spine | FLOWING |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED for this phase — the primary verifiable behaviors are network-dependent (live Moonraker + two real printers) and were proven by the live UAT. The instrumented `ProfileSurvivesRestartTest` covers the one statically-verifiable behavioral claim (persistence across process death).

---

### Probe Execution

Step 7c: No probe scripts declared or found for this phase (`scripts/*/tests/probe-*.sh` — none exist for Phase 14).

---

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| MULTI-01 | 14-01, 14-02, 14-03, 14-04, 14-05, 14-06 | Configure and switch between multiple Moonraker printers | SATISFIED | Full implementation verified: ProfileStore, AppContainer wiring, DevicesScreen switcher, SettingsScreen CRUD, webcam re-key, live two-printer UAT PASSED on E5+ + E3. |

**Traceability note:** REQUIREMENTS.md line 134 defines MULTI-01 in the v2/Multi-Printer section. The traceability table (lines 158-237) does not have a Phase 14 row for MULTI-01 — only the per-phase count note (line 258) marks it as "new MULTI-* family — TBD at discuss." This is a documentation gap in the traceability table (the roadmap expansion note was updated, the table row was not). The capability is fully implemented and UAT-proven in code; the missing table row is a docs-only deficiency and does not affect the code gate.

---

### Anti-Patterns Found

| File | Issue | Severity | Impact |
|------|-------|----------|--------|
| `AppContainer.kt:318` | `seedTheme` samples `themePrefs.flow.firstOrNull()` in the no-active branch — a one-shot sample, not a live subscription. If the user edits global theme while profile-less, the live `themeResolver.setBase/setFs/setDeltas` calls keep the screen correct in-session, but a cold restart while profile-less will not reflect idle theme edits. (WR-02 from code review — explicitly deferred to Phase 22 ship hardening in commit 4e4d83b.) | WARNING | Latent, masked by live resolver setters; only manifests after a cold restart with no active profile while idle theme was edited |
| `AppShell.kt:215` | `profileId = activeProfileId ?: ""` collapses to a shared `preferred_cam_` key (empty suffix) when no active profile id. The webcam surface is only reachable with an active printer, so this edge is rarely hit today, but a future path that reaches the webcam holder without an active profile would cross-contaminate preferred-cam selections across profile-less states. (WR-03 from code review — explicitly deferred to Phase 22.) | WARNING | Latent; current app routing prevents it from being hit in practice |

No `TBD`, `FIXME`, or `XXX` markers found in any Phase-14-modified files. No placeholder/stub implementations in production code.

**Anti-patterns that are NOT blockers:**
- WR-01 (stale-snapshot theme persist race): FIXED in commit 4e4d83b — `mutateActive`/`mutateActiveProfile` atomic read-modify-write now used.
- WR-02/WR-03: WARNING-level, explicitly deferred to Phase 22 ship hardening. They are latent (not hit in normal app flow) and were accepted by the developer with a tracking note in commit 4e4d83b.
- IN-01..IN-05: Info-level documentation and test-fidelity items. IN-01 (KDoc) and IN-02 (DinghyApp "FIVE") were fixed in 4e4d83b. IN-03 (container KDoc), IN-04 (FakeDataStore silent writes), IN-05 (Thread.sleep timing) remain as known info items; none affect correctness.

---

### Human Verification Required

### 1. Mid-Print Switch (D-04)

**Test:** With a print actively running on one printer (E5+ or E3), open Devices, tap the OTHER printer, wait for the Splash + reconnect, then switch back. Observe the first printer's print throughout.

**Expected:** The running print continues untouched on the first printer while the tablet switches to monitoring the other printer. Switching back returns to the first printer's Status with the print still in progress (no gcode sent to interrupt it).

**Why human:** UAT item 4 was owner-deferred by Matthew during the 2026-06-04 session (did not want to start a live print solely for this test). The rebind path exercised by this test is the SAME `runConfigLoop`/`cancelAndJoin` seam proven by UAT items 1 and 2 (switching only changes which printer the tablet watches; it sends no print-affecting gcode). Risk is low but the boundary has not been eyeballed on-device. Run opportunistically the next time a print is genuinely in progress.

---

### Gaps Summary

No code gaps. All 9 observable truths verified against the actual codebase. All artifacts exist and are substantive. All key links are wired. WR-01 was fixed post-code-review (commit 4e4d83b). WR-02/WR-03 are latent defects explicitly deferred to Phase 22 ship hardening — they do not block the phase goal (managed printer profiles with a clean service spine rebind on switch).

The sole open item is a human verification: the mid-print switch scenario (UAT item 4), owner-deferred by Matthew during the UAT session.

---

_Verified: 2026-06-04_
_Verifier: Claude (gsd-verifier)_
