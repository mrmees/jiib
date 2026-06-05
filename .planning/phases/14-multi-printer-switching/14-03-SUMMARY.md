---
phase: 14-multi-printer-switching
plan: 03
subsystem: webcam-prefs-rekey + route-enum
tags: [webcam, preferred-cam, datastore, profile-id, rekey, dest-enum, wave-2, multi-printer, D-06, D-07]
requires:
  - "14-02: AppContainer.activeProfile (Flow<Profile?>) — the profile-id source threaded into the webcam holder"
provides:
  - "WebcamPrefs.preferredCam/setPreferredCam/preferredCamKey keyed on profileId — key prefix preferred_cam_<profileId> (D-06)"
  - "WebcamHolder ctor + webcamBitmapHolder factory thread profileId (not cfg.host) for the per-printer pref KEY; feed URLs stay on cfg"
  - "AppShell webcam build site threads the active profile id (container.activeProfile.map { it?.id }) into webcamBitmapHolder, replacing cfg.host"
  - "Dest.Devices route enum value (registered for plan 05's switcher screen)"
  - "AppShell when(dest) Dest.Devices placeholder arm (Box) — keeps the exhaustive when compile-clean until plan 05 replaces it"
affects:
  - "plan 05 (wave 3): hosts the real DevicesScreen on Dest.Devices (replaces the placeholder Box arm); does NOT touch the webcam holder (the D-06 re-key landed fully here)"
tech-stack:
  added: []
  patterns:
    - "RESEARCH Pattern 4 / Pitfall 5: re-key the per-printer pref on the stable profile UUID at ALL THREE touch points atomically (no half-wired host/profileId state across the wave)"
    - "ThemePrefs dynamic-string-key idiom retained — only the suffix source moves host -> profileId"
    - "container.activeProfile.map { it?.id }.collectAsStateWithLifecycle(...) — the profile-id projection consumed in Compose"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamPrefs.kt
    - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamReconnectStateTest.kt
    - app/src/androidTest/java/works/mees/dinghy/webcam/WebcamLifecycleTest.kt
    - app/src/androidTest/java/works/mees/dinghy/webcam/WebcamUnsupportedCardTest.kt
decisions:
  - "Added a Dest.Devices placeholder arm (Box) to AppShell's exhaustive when(dest) (Rule 3 — blocking compile issue). The plan called Task 2 a 'single-token change' but the AppShell when(dest) has NO else, so adding the enum value alone breaks the exhaustive when. A blank Box keeps wave 2 compile-clean; plan 05 (wave 3) replaces it with the real DevicesScreen. The drawer Devices tile is not wired live until plan 05, so the arm is unreachable in wave 2 — no behavior change."
  - "WebcamPrefsTest left untouched: it constructs the pref with POSITIONAL string args (preferredCam(\"...\")/setPreferredCam(\"...\")), which still compile + pass after the param rename. Only the named host = args (WebcamReconnectStateTest + 2 androidTest holders) had to change. Its method names/comments still say 'per-host' but are now profile-id-shaped values — left as scope-minimal (compiles + GREEN)."
metrics:
  duration: ~6min
  completed: 2026-06-05
---

# Phase 14 Plan 03: Webcam Pref Re-key (host -> profile id) + Dest.Devices Summary

Closed the D-06 preferred-webcam re-key END-TO-END in this one wave and registered the `Dest.Devices`
route plan 05 will host the switcher on. The per-printer preferred-cam pref now keys on the active
profile's stable UUID instead of the host — two same-host profiles keep INDEPENDENT preferred cams and the
preference survives a host edit. All three touch points (`WebcamPrefs` signatures, the
`WebcamHolder`/`webcamBitmapHolder` caller, AND the `AppShell` build site) flipped from `host` to
`profileId` together, so wave 2 ends compile-clean with the re-key fully wired (plan 05 does NOT touch the
webcam holder). No migration (D-07) — old `preferred_cam_<host>` entries are simply orphaned.

## What Was Built

### Task 1 — Re-key WebcamPrefs + WebcamHolder + AppShell webcam build, host -> profileId (D-06) (commit `dc838ef`)

- **`WebcamPrefs.kt`**: pure parameter/prefix rename across all three members —
  `preferredCam(profileId)`, `setPreferredCam(profileId, camId)`,
  `preferredCamKey(profileId) = stringPreferencesKey("preferred_cam_$profileId")`. The
  `.catch { IOException -> emptyPreferences() }` fail-safe read and the `dataStore.edit` write are
  byte-unchanged. KDoc rewritten to state the key derives from the active PROFILE ID (D-06) not the host,
  plus a NO-MIGRATION note (D-07: old host-keyed entries orphaned → first-visit falls to first-in-list).
- **`WebcamHolder.kt`**: ctor param `private val host: String` → `private val profileId: String` (+ KDoc).
  Both pref call sites updated — `setPreferredCam(profileId, camId)` (selectCam) and
  `preferredCam(profileId).first()` (resolveSelectedCam). The `webcamBitmapHolder(...)` factory gained a
  `profileId: String` parameter and passes `profileId = profileId` to the holder (replacing
  `host = cfg.host`). The factory still takes `cfg: ConnectionConfig` — the loopback-rewrite host for the
  FEED stays on `cfg` inside `bitmapFeed(cfg, …)`; ONLY the per-printer pref KEY moved to profileId
  (Pitfall 5 — no host fallback for the key).
- **`AppShell.kt`**: added `import kotlinx.coroutines.flow.map`; collected
  `val activeProfileId by container.activeProfile.map { it?.id }.collectAsStateWithLifecycle(initialValue = null)`
  near the existing webcam collects; threaded `profileId = activeProfileId ?: ""` into the
  `webcamBitmapHolder(...)` call; added `activeProfileId` to the `remember(store, activeCfg.host,
  activeProfileId, viewWidthPx, viewHeightPx)` keys so the holder rebuilds on profile switch.
  `cfg`/`activeCfg` stay as-is for the feed.
- **Tests**: `WebcamReconnectStateTest.kt` (5 holder constructions) + `WebcamLifecycleTest.kt` +
  `WebcamUnsupportedCardTest.kt` (androidTest) flipped their named `host =` arg to `profileId =` so the
  whole test sourceset still compiles ([[dinghy-wave0-red-scaffold-compile]]).

### Task 2 — Add Dest.Devices to the route enum (commit `c87e76b`)

- **`TopRoute.kt`**: `Devices` inserted before `Settings` in `enum class Dest { … Spool, Devices, Settings }`.
  `derive()` (lines 57-62) is UNTOUCHED — its `!cfgPresent → Connect` arm already covers the D-11 empty
  state (hasConfig became "has active profile" in plan 02).
- **`AppShell.kt`**: added a `Dest.Devices -> Box(Modifier.fillMaxSize())` placeholder arm to the
  exhaustive `when (dest)` (deviation Rule 3 — see Deviations). Plan 05 replaces it with the real
  Field-of-printers DevicesScreen.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking compile issue] Added a Dest.Devices placeholder arm to AppShell's exhaustive when(dest)**
- **Found during:** Task 2 (the "single-token" enum addition).
- **Issue:** `AppShell.kt`'s `when (dest)` (renders the active destination) is exhaustive with NO `else`
  arm. Adding `Dest.Devices` to the enum makes that `when` non-exhaustive → `:app:compileDebugKotlin`
  fails. The plan described Task 2 as a single-token change and did not account for this consumer.
- **Fix:** Added `Dest.Devices -> Box(Modifier.fillMaxSize())` (a blank full-bleed Box) immediately before
  the `Dest.Settings` arm. `Box`/`Modifier`/`fillMaxSize` were already imported. This keeps wave 2
  compile-clean; plan 05 (wave 3) replaces the placeholder with the real switcher. The drawer Devices tile
  is NOT wired live until plan 05, so this arm is unreachable in wave 2 — no behavior change.
- **Files modified:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`
- **Commit:** `c87e76b`

(`WebcamPrefsTest` was intentionally left untouched — it uses positional args that still compile and pass;
only the named `host =` arguments in the three holder-construction tests had to change. See the decisions
frontmatter.)

## Verification

- `gw.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon` — **BUILD SUCCESSFUL**
  (Task 1: whole app + unit test sourceset compiles — no half-wired host/profileId mismatch).
- `gw.bat :app:compileDebugKotlin --no-daemon` — **BUILD SUCCESSFUL** (Task 2: Dest.Devices + placeholder arm).
- `gw.bat :app:testDebugUnitTest --no-daemon` (FULL suite) — **BUILD SUCCESSFUL** — no regression from the
  param rename across the webcam pref/holder + the route enum addition.
- Source assertions:
  - `WebcamPrefs.preferredCamKey` builds `"preferred_cam_$profileId"`; no `host` param remains in the three members.
  - `WebcamHolder` ctor takes `profileId: String`; both pref call sites pass `profileId`; `webcamBitmapHolder`
    takes a `profileId` param and no longer passes `host = cfg.host`.
  - `AppShell.kt` collects `container.activeProfile.map { it?.id }`, passes `profileId = activeProfileId ?: ""`
    into `webcamBitmapHolder(...)`, and the holder `remember(...)` keys on `activeProfileId`.
  - The three holder-construction tests use `profileId =` (no stale `host =` compile reference).
  - No data-migration code added (D-07): nothing reads `preferred_cam_<host>` to copy forward.
  - `Dest` enum contains `Devices`; `derive()` body unchanged.

## Threat Mitigations Applied

- **T-14-07 (Tampering / state confusion — webcam pref keyed on host → same-host profiles cross-read):**
  MITIGATED. The pref now keys on the stable profile UUID at ALL THREE touch points (`WebcamPrefs`,
  `WebcamHolder`/`webcamBitmapHolder`, AND the `AppShell` caller) — re-keyed atomically in this wave
  (Pitfall 5: the key does NOT fall back to host). Two same-host profiles therefore keep INDEPENDENT
  preferred cams. (On-device two-printer eyeball is the phase UAT.)
- **T-14-SC (npm/pip/cargo installs):** N/A — no new external packages this phase.

## Known Stubs

- **`Dest.Devices -> Box(Modifier.fillMaxSize())` in `AppShell.kt`** — INTENTIONAL placeholder, resolved by
  plan 05 (wave 3), which replaces it with the real DevicesScreen. Documented above (deviation Rule 3). The
  Devices drawer tile is not wired live until plan 05, so the placeholder is unreachable in wave 2 — it
  exists only to keep the exhaustive `when (dest)` compiling.

## Self-Check: PASSED

- Modified files present on disk: `WebcamPrefs.kt`, `WebcamHolder.kt`, `AppShell.kt`, `TopRoute.kt`,
  `WebcamReconnectStateTest.kt`, `WebcamLifecycleTest.kt`, `WebcamUnsupportedCardTest.kt` (all verified).
- Commits `dc838ef` (Task 1) and `c87e76b` (Task 2) exist in git log (verified).
- D-06 re-key wired end-to-end in this wave; `Dest.Devices` registered; `derive()` untouched; full unit
  suite GREEN.
