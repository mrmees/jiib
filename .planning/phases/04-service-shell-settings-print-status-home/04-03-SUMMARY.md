---
phase: 04-service-shell-settings-print-status-home
plan: 03
subsystem: infra
tags: [foreground-service, android, kotlin, coroutines, stateflow, service-locator, datastore, moonraker, rotation]

# Dependency graph
requires:
  - phase: 02-connection-state-foundation
    provides: MoonrakerSession spine (run()/requestReconnectNow/connectionState), PrinterStateStore, JsonRpcClient, MoonrakerSocket.real/defaultClient, MoonrakerAuth (oneshot token + buildAuthedWsUrl), identify clientUrl
  - phase: 03-design-system-theming
    provides: ThemeResolver + ThemePrefs (the resolver this container seeds once)
  - phase: 04-service-shell-settings-print-status-home (wave 1)
    provides: ConnectionStore.config (04-01), CommandDispatcher + JsonRpcMethods action constants (04-02)
provides:
  - "DinghyApp (Application) — owns the AppContainer singleton + TWO separate DataStore files (theme.preferences_pb, connection.preferences_pb)"
  - "AppContainer — process-scoped service-locator: ONE StateFlow<SpineHandle?> publication point + derived per-field flows + hasConfig + narrow SessionControl + seeded ThemeResolver + stores (no DI framework, no transport construction)"
  - "SpineHandle — immutable atomic spine snapshot (flows + dispatcher + monotonic sessionInstanceId)"
  - "SessionControl — narrow UI-facing reconnect/restart contract; never exposes the raw session (review #1)"
  - "MoonrakerService — started specialUse FGS that owns the spine, rebuilds it from ConnectionStore.config (cancel-before-relaunch, D-03), publishes one atomic handle, idles on null config, shows a key-free status notification"
  - "ServiceSurvivesRotationTest — instrumented rotation-continuity gate (sessionInstanceId unchanged), PASSED on flox"
affects: [04-04 SettingsScreen (writes ConnectionStore → triggers rebuild), 04-05 SplashScreen (uses SessionControl recovery), 04-06 PrintStatus Stop, 04-07 MainActivity root controller (collects spine + starts the service)]

# Tech tracking
tech-stack:
  added: [androidx.test.uiautomator (androidTest only, for the rotation gate)]
  patterns:
    - "Atomic spine publication: ONE StateFlow<SpineHandle?> swapped whole on rebuild; per-field convenience flows derived via flatMapLatest so no partially-swapped refs (review #6)"
    - "Extracted config-rebuild loop (runConfigLoop) as a JVM Nyquist seam: cancelAndJoin-before-relaunch + monotonic-id publish provable in virtual time with fakes, no Android lifecycle/socket"
    - "Process-held service-locator (Application-owned AppContainer) replaces GalleryActivity's local assembler block; no Hilt (D-02)"
    - "Continuity-signal instrumented gate: assert sessionInstanceId UNCHANGED across rotation (review #3) rather than absence-of-transition (harness-spoofable)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/DinghyApp.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
    - app/src/main/java/works/mees/dinghy/di/SessionControl.kt
    - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
    - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt
    - app/src/test/java/works/mees/dinghy/service/MoonrakerServiceTest.kt
    - app/src/androidTest/java/works/mees/dinghy/service/ServiceSurvivesRotationTest.kt
  modified:
    - app/src/main/AndroidManifest.xml
    - app/build.gradle.kts

key-decisions:
  - "SpineHandle is one immutable snapshot published via a single StateFlow<SpineHandle?>; AppContainer's per-field flows are flatMapLatest-derived from it so a rebuild can never expose a half-old/half-new mix (review #6)"
  - "The config-rebuild loop is extracted to MoonrakerService.runConfigLoop (companion internal suspend fun) taking buildAndPublish/publishIdle hooks — the cancel-before-relaunch ordering and monotonic atomic publish are proven host-side in virtual time without a Service or socket"
  - "DinghyApp creates the two DataStore files via PreferenceDataStoreFactory.create with explicit produceFile names (theme.preferences_pb / connection.preferences_pb) on a shared IO scope"
  - "The rotation gate asserts sessionInstanceId CONTINUITY (unchanged) across UiDevice rotation — a concrete signal robust to harness re-collection (a republish would mint a new id), per review #3"
  - "uiautomator was in the version catalog but unwired; added androidTestImplementation so the rotation gate compiles (ApplicationProvider comes transitively via ext.junit's androidx.test:core)"

patterns-established:
  - "FGS-owns-the-connection: a started (not bound) specialUse service holds serviceScope + the spine; the Activity collects process-held state via StateFlow<SpineHandle?>, never binds (D-02)"
  - "Idle-on-null-config: a cleared/absent config publishes spine(null) + binds SessionControl(null) so routing falls back to Connect with no leaked socket (review #12)"

requirements-completed: [CONN-01, SHELL-03]

# Metrics
duration: 35min
completed: 2026-06-01
---

# Phase 4 Plan 03: Service, App-Container & Spine Ownership Summary

**A started `specialUse` foreground service (`MoonrakerService`) now OWNS the Moonraker spine in a process-held `AppContainer` and publishes it as ONE atomic `SpineHandle` (monotonic `sessionInstanceId`), so the connection survives Activity recreation — proven on flox by an instrumented id-continuity rotation gate — while a config change cleanly rebuilds the session (cancel-before-relaunch) and a cleared config idles it.**

> STATUS: Tasks 1–4 COMPLETE and committed; the instrumented rotation gate (Task 4) PASSED on flox.
> **Task 5 is a `checkpoint:human-verify` (gate="blocking") — PAUSED, awaiting human on-device sign-off**
> (manual rotation + screen-off with the real Ender 5 Plus reachable, notification visual check). The
> plan is NOT marked done past the unmet checkpoint.

## Performance

- **Duration:** ~35 min (through Task 4; Task 5 awaiting human)
- **Started:** 2026-06-01
- **Completed (Tasks 1–4):** 2026-06-01
- **Tasks:** 4 of 5 complete (Task 5 = blocking human checkpoint, paused)
- **Files created:** 8 (4 main + 2 unit test + 1 androidTest + ... ); **modified:** 2

## Accomplishments
- **The connection now belongs to the OS, not the Activity (SHELL-03/D-02).** `MoonrakerService` (started, `specialUse`, not bound) holds `serviceScope` + the spine and publishes it into the process-held `AppContainer`; the Activity will collect `StateFlow<SpineHandle?>` with no service binding.
- **The persisted config is the live source (CONN-01).** The service collects `ConnectionStore.config` and assembles the real spine with the verified Phase-2 constructors — `MoonrakerSocket.real`'s `DevConfig.wsUrl` default **overridden** with `cfg.wsUrl`, the identify `clientUrl` **preserved** (regressing it re-breaks Connected, per 02-04), and `MoonrakerAuth` constructed only when keyed (token-bearing ws URL via `buildAuthedWsUrl`).
- **Atomic, leak-free rebuild (D-03, review #6/#12).** `collectLatest` + `cancelAndJoin()` tears the old session down BEFORE the new one is built; the whole `SpineHandle` swaps in one assignment; a `null` config publishes an idle spine (and binds `SessionControl(null)`) so routing shows Connect with no leaked socket. Proven host-side in virtual time by `MoonrakerServiceTest`.
- **Narrow control surface (review #1).** The UI's entire reconnect/restart reach is the `SessionControl` interface (`requestReconnectNow`/`restartFirmware`/`restartHost`); the raw session is never exposed. `AppContainerTest` asserts no `SessionControl` member returns a session.
- **Instrumented rotation gate PASSED on flox (review #3).** `ServiceSurvivesRotationTest` seeds a config, starts the FGS, rotates via `UiDevice` three cycles, and asserts the published `sessionInstanceId` is UNCHANGED — the Activity did not rebuild the spine. 1 test, 0 failed on the real Nexus 7 (LineageOS 18.1 / API 30).

## Task Commits

1. **Task 1: Manifest FGS perms + specialUse service + DinghyApp registration** — `25dae25` (feat)
2. **Task 2: SpineHandle + SessionControl + DinghyApp + AppContainer (atomic publication)** — `899d37a` (feat; TDD single-GREEN — the test references the types under test, so an empty-RED would not compile, mirroring 04-01's idiom)
3. **Task 3: MoonrakerService — started FGS + config-rebuild seam + JVM seam test** — `ca4c1ed` (feat; TDD single-GREEN — same idiom)
4. **Task 4: ServiceSurvivesRotationTest — instrumented rotation continuity (+ uiautomator dep)** — `6ed4d60` (test)

**Task 5:** blocking `checkpoint:human-verify` — PAUSED (no commit; awaiting human on-device sign-off).

## Files Created/Modified
- `app/src/main/AndroidManifest.xml` (modified) — ADD ONLY: FGS/SPECIAL_USE/POST_NOTIFICATIONS/CHANGE_WIFI_MULTICAST_STATE perms, `.DinghyApp` registration, the `specialUse` `.service.MoonrakerService` + subtype property. Phase-1 cleartext posture untouched.
- `app/build.gradle.kts` (modified) — `androidTestImplementation(libs.androidx.test.uiautomator)` for the rotation gate.
- `DinghyApp.kt` — Application owning the `AppContainer` + two `PreferenceDataStoreFactory.create` files (`theme.preferences_pb`, `connection.preferences_pb`) on an IO scope; seeds the resolver.
- `di/SpineHandle.kt` — immutable snapshot: printerState/connectionState/capabilities StateFlows + dispatcher + monotonic `sessionInstanceId`.
- `di/SessionControl.kt` — narrow `requestReconnectNow`/`restartFirmware`/`restartHost` interface; no raw-session member (review #1).
- `di/AppContainer.kt` — service-locator: one `StateFlow<SpineHandle?>` + `publishSpine` + flatMapLatest-derived per-field flows + `hasConfig` + `sessionControl` (delegate bound by the service) + seeded `ThemeResolver`; constructs no transport.
- `service/MoonrakerService.kt` — the started `specialUse` FGS; `runConfigLoop` JVM seam; key-free `IMPORTANCE_LOW` notification; `onBind=null`; `START_STICKY`.
- `test/.../di/AppContainerTest.kt` — null-initial spine, atomic swap (every field reads as B), SessionControl narrowness.
- `test/.../service/MoonrakerServiceTest.kt` — null-idle + cancel-before-relaunch ordering + strictly-greater monotonic id, in virtual time with fakes.
- `androidTest/.../service/ServiceSurvivesRotationTest.kt` — the rotation-continuity gate (PASSED on flox).

## Decisions Made
See `key-decisions` frontmatter. Headlines: one immutable `SpineHandle` via a single StateFlow (per-field flows derived, review #6); the rebuild loop extracted as a host-testable seam; the rotation gate asserts id continuity (review #3); two separate DataStore files for a clean key-redaction boundary.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Wired the unused `uiautomator` catalog alias into androidTest**
- **Found during:** Task 4 (ServiceSurvivesRotationTest authoring).
- **Issue:** `androidx-test-uiautomator` existed in `libs.versions.toml` but was never added to any `androidTestImplementation` — the rotation test (which drives `UiDevice`) would not compile.
- **Fix:** Added `androidTestImplementation(libs.androidx.test.uiautomator)`. `ApplicationProvider` is available transitively via `ext.junit`'s `androidx.test:core` (no separate core alias exists, so none was invented).
- **Files modified:** `app/build.gradle.kts`.
- **Verification:** `:app:assembleDebugAndroidTest` GREEN; the test ran and PASSED on flox.
- **Committed in:** `6ed4d60` (Task 4 commit).

---

**Total deviations:** 1 auto-fixed (1 blocking). **Impact:** Necessary to compile the planned instrumented gate; no scope creep.

## Issues Encountered
- `connectedDebugAndroidTest` rejects the unit-test `--tests` flag; the instrumented test was selected via `-Pandroid.testInstrumentationRunnerArguments.class=...` instead. Resolved, test PASSED.
- The `DinghySpine` logcat line (`sessionInstanceId=<n>`) had cycled out of LineageOS's small logcat buffer by the time it was queried post-run; the test's programmatic `assertEquals(idBefore, currentId())` continuity assertion is the authoritative evidence and PASSED. The Task-5 human checkpoint will capture the live `adb logcat -s DinghySpine` sequence as additional on-device evidence.

## TDD Gate Compliance
Tasks 2 and 3 carried `tdd="true"`. As in 04-01, the failing tests reference the types under test (`AppContainer`/`SpineHandle`/`SessionControl`; `MoonrakerService.runConfigLoop`), so an empty-RED commit could not compile; the production types and their tests were authored together and proven by a single GREEN `:app:testDebugUnitTest` run per task (no separate `test(...)` RED commit precedes the `feat(...)` commit). All `<behavior>` cases are asserted: null-initial spine, atomic swap, SessionControl narrowness (Task 2); null-idle, cancel-before-relaunch ordering, monotonic strictly-greater id (Task 3).

## Known Stubs
None. Every deliverable is fully wired against its real Phase-2/Wave-1 dependencies. `MainActivity` is intentionally NOT modified here — wiring the root controller (collect `spine`, start the service, branch on `TopRoute.derive`) is plan 04-07; this plan delivers the service + container the Activity will consume.

## Threat-Model Coverage
- **T-04-03-I** (key in notification/logs) — notification text is `ConnectionState`-only; the only `apiKey` reference is the auth construction, never the notification or a log. The `DinghySpine` log carries the id only.
- **T-04-03-D** (rebuild leak) — `cancelAndJoin()` before relaunch; proven by `MoonrakerServiceTest` cancel-before-build ordering.
- **T-04-03-swap** (partial swap) — one atomic `SpineHandle` publication; `AppContainerTest` asserts every field reads as B after publishing B.
- **T-04-03-SC** (package installs) — no new third-party runtime packages; `uiautomator` is an existing catalog alias (test-only), not a new fetch.

## Next Phase Readiness
- READY for 04-07 (MainActivity root controller): collect `container.spine` / derived flows, `startForegroundService(MoonrakerService)`, branch on `TopRoute.derive(container.hasConfig, state)`.
- READY for 04-04 (Settings): write `container.connectionStore` → the service rebuilds via `collectLatest`.
- READY for 04-05/04-06: `container.sessionControl` is the recovery/reconnect surface.
- **BLOCKER (checkpoint):** Task 5 on-device human verification is pending — manual rotation + screen-off with the Ender 5 Plus reachable, plus the visual notification/key-free check. The instrumented gate passed automatically, but the human visual + screen-off sign-off cannot be automated.

## Self-Check: PASSED

All 8 created files present on disk; all four task commits (`25dae25`, `899d37a`, `ca4c1ed`, `6ed4d60`) confirmed in git history. Task 5 has no commit by design (blocking human-verify checkpoint, paused).

---
*Phase: 04-service-shell-settings-print-status-home*
*Status: paused at Task 5 (blocking human-verify checkpoint); Tasks 1–4 complete*
