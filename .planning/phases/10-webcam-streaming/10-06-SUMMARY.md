---
phase: 10-webcam-streaming
plan: 06
subsystem: webcam
tags: [webcam, holder, stateflow, reconnect-state-machine, backoff, datastore, per-printer-prefs, focus-field-gutter, wr-01, host-testable]

# Dependency graph
requires:
  - phase: 10-02 (pure core)
    provides: Webcam model (safeRotation/hasSnapshot), Rung enum, ResolvedWebcam VM, resolveWebcamUrl (D-09)
  - phase: 10-03 (enumeration spine)
    provides: SpineHandle.webcams StateFlow + AppContainer.webcams/webcamCount (the holder's cam-list source)
  - phase: 10-04 (decode I/O)
    provides: WebcamProbe / MjpegStreamDecoder (bitmaps) / SnapshotPoller (snapshotBitmaps) / WebcamClients postures
  - phase: 10-05 (render surface)
    provides: WebcamView.Mode + WebcamViewHost (the screen embeds it; setFrame/setChrome/setTransform)
provides:
  - WebcamPrefs — per-printer (preferred_cam_<host>) preferred-cam DataStore, fail-safe; own webcam.preferences_pb
  - AppContainer.webcamPrefs (process-scoped, connection-independent) + DinghyApp's 4th preferences file
  - WebcamHolder<T> — toolkit-agnostic StateFlow holder: D-10 selected-cam, rung-select via injected WebcamFeed,
    D-11 reconnect state machine (keep-last-frame Reconnecting), D-12 foreground 1s→10s backoff, A4 terminal,
    D-13 start()/stop(), WR-01 idempotent cancel(); WebcamVm<T> + FeedOutcome + WebcamFeed seam
  - bitmapFeed / webcamBitmapHolder — production T=Bitmap binding wiring the 10-04 probe/decoder/poller
  - WebcamScreen — ScreenScaffold Focus/Field/Gutter webcam page (aspect-aware Field, Back-only red gutter)
  - WebcamReconnectStateTest GREEN (the LAST RED scaffold) → FULL unit suite 0-failures
affects: [10-07, 10-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Injected WebcamFeed seam (fun interface) so the holder's NEW reconnect/backoff/terminal STATE MACHINE is host-provable on the JVM without Robolectric/Bitmap — the test scripts transient/terminal/cancelled outcomes; production binds T=Bitmap via bitmapFeed (the same generic-decoder + injectable-decode discipline 10-04 established)"
    - "Single owned driver Job (start launches, stop/cancel cancel) → structured-concurrency teardown of the active decode/poll loop AND its pending backoff delay in one cancel; cancel() flips a @Volatile cancelled flag so it is idempotent + makes the holder inert (the WR-01 frozen-feed-after-restart guard)"
    - "Reconnecting set SYNCHRONOUSLY before the backoff delay (the VM copy runs, keeping `frame`, then the loop suspends on delay) — so the keep-last-frame Reconnecting state is observable deterministically in virtual time regardless of the jittered backoff length"
    - "4th separate DataStore file (webcam.preferences_pb) on its own lifecycle — the MacroPrefs separate-file precedent extended; per-printer dynamic key (ThemePrefs idiom) preferred_cam_<host>"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamPrefs.kt
    - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamPrefsTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/DinghyApp.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamReconnectStateTest.kt
    - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt

key-decisions:
  - "WebcamHolder is GENERIC over the frame type T and drives an injected WebcamFeed rung-select/active-loop seam — the genuinely-new orchestration (reconnect/backoff/terminal/cancel) is proven host-side with a scripted feed (no Robolectric, no Bitmap); the Bitmap-bound bitmapFeed wires the already-unit-proven 10-04 services and its end-to-end behavior is the on-device property of plan 10-08. This mirrors 10-04's generic-decoder + injectable-decode discipline."
  - "A stream EOF/mid-read end maps to FeedOutcome.Transient (NOT immediately Terminal) so a brief drop keeps the last frame + reconnects (D-11, no black flash); a genuinely-dead cam re-PROBES on the next retry and the probe yields Terminal there (the 401/403/WebRTC dead-end is decided by the probe, not guessed by the holder)."
  - "Per-cam identity = uid ?: name (camIdOf) — the stable key for both the selected-cam selection and the per-printer preferred-cam DataStore value; a stale/garbage saved id only ever defaults a selection when it STILL matches a listed cam (else falls to first-in-list), so a corrupt store is harmless (T-10-13)."
  - "WebcamScreen owns the page-visible lifecycle via DisposableEffect(holder){ start(); onDispose{ stop() } } (D-13 decode-only-while-visible); the shell (10-07) layers the spine-rebuild cancel() (WR-01) ON TOP — start/stop is page-level, cancel is holder-death-level."
  - "Field show/hide is the camera_feed rule AND gated on multiCam (a single cam never needs the picker → always full-focus); the in-feed cycle overlay (multiCam param to WebcamViewHost) is wired ONLY in full-focus (when the Field picker is absent), so the two cam-choosers never both show."

patterns-established:
  - "Injected fun-interface feed seam → host-provable holder state machine without Android"
  - "Synchronous-state-then-suspend so virtual-time tests observe the transient state deterministically"
  - "Idempotent + inert cancel() via a @Volatile flag (the WR-01 complete-teardown pattern)"

requirements-completed: []  # CAM-01 is delivered across 10-02..10-08; this plan builds the orchestration layer (holder/screen/prefs) — on-device UAT is 10-08

# Metrics
duration: ~12min
completed: 2026-06-04
---

# Phase 10 Plan 06: Webcam Orchestration (Holder + Screen + Prefs) Summary

**Built the orchestration layer that turns the enumeration spine + decode services + render surface into a working screen — `WebcamHolder<T>` (the toolkit-agnostic StateFlow holder owning the D-10 selected-cam pick, rung-select via an injected `WebcamFeed`, the D-11 keep-last-frame Reconnecting state machine, the D-12 foreground-only 1s→10s backoff, the A4 terminal→dead-end, the D-13 `start()`/`stop()` page surface, and the WR-01 idempotent `cancel()`), `WebcamScreen` (single-focus default + aspect-aware Field show/hide + Back-only red gutter, hosting `WebcamViewHost`), and `WebcamPrefs` (per-printer `preferred_cam_<host>` DataStore on its own `webcam.preferences_pb`) — then REPLACED the LAST runtime-RED scaffold (`WebcamReconnectStateTest`) with typed assertions against the holder's state machine, taking the FULL unit suite to 0 failures.**

## Performance
- **Duration:** ~12 min
- **Started:** 2026-06-04T03:14:31Z
- **Completed:** 2026-06-04T03:26:23Z
- **Tasks:** 3
- **Files modified:** 8 (4 created, 4 modified)

## Accomplishments
- **`WebcamPrefs.kt`** — `class WebcamPrefs(DataStore<Preferences>)` mirroring `MacroPrefs`: a fail-safe `.catch { IOException → emptyPreferences() }` read, the per-printer dynamic key `preferred_cam_<host>` (the `ThemePrefs` idiom). `preferredCam(host): Flow<String?>` (null = no saved pref → first-in-list) + `suspend setPreferredCam(host, camId)` — the holder is the only writer (no "set as default" UI; the preference is the side-effect of selecting/cycling a cam, D-10).
- **`DinghyApp` / `AppContainer`** — a FOURTH, INDEPENDENT `webcam.preferences_pb` (the separate-file discipline), constructed once per process; `AppContainer.webcamPrefs` is PROCESS-scoped + connection-independent (survives reconnects/printer swaps, like `macroPrefs`).
- **`WebcamHolder<T>.kt`** — the `MoveHolder`-skeleton StateFlow holder (plain Kotlin, no Compose, host-testable). Owns: (1) D-10 selected-cam (explicit `selectCam`/`cycleCam` → preferred-if-still-listed → first-in-list; a select/cycle persists via `setPreferredCam`), (2) D-09 URL resolution, (3) rung-select + active loop via the injected `WebcamFeed`, (4) the **D-11 reconnect state machine** — a `Transient` outcome flips the VM to `Reconnecting` KEEPING `frame` and backs off (`backoffDelay`, 1s base, 10s cap) FOREGROUND-ONLY; a `Terminal` outcome → `DeadEnd` with no spin (A4), (5) latest-wins frame hand-off into the VM, (6) the D-13 `start()`/`stop()` surface + the **WR-01 `cancel()`** (a `@Volatile cancelled` flag makes it idempotent + the holder inert; it cancels the one owned driver `Job`, which structurally tears down the active decode/poll loop AND any pending backoff `delay`). `WebcamVm<T>` (selected/frame/mode/cams/multiCam + camName/serviceName), `FeedOutcome` (Transient/Terminal/Cancelled), `WebcamFeed<T>` fun-interface, and the production `bitmapFeed`/`webcamBitmapHolder` (binding `T=Bitmap` to the 10-04 probe/decoder/poller).
- **`WebcamScreen.kt`** — `@Composable WebcamScreen(holder: WebcamHolder<Bitmap>, onBack)` on `ScreenScaffold`. FOCUS = `WebcamViewHost` (frame/mode/flip/rotation from the VM); full-focus multi-cam → tap-the-feed cycles (`holder::cycleCam`). FIELD = the aspect-aware cam picker (camera_feed rule: landscape-device+landscape-feed → hide, landscape-device+portrait-feed → show, portrait-device → stack; AND gated on `multiCam`), the selected cam expanded with its Moonraker name/service/aspect. GUTTER = Back ONLY (`Intent.Danger` red), wired `onBack` like `MoveScreen`. D-13 page-visible lifecycle via `DisposableEffect(holder){ start(); onDispose{ stop() } }`. NO keyboard. `isPortraitFeed` parses the cam's `aspect_ratio` `"W:H"`.
- **`WebcamReconnectStateTest`** — the plan-10-01 `fail()` scaffold body REPLACED with 4 typed tests against the real holder (an injected scripted `WebcamFeed<String>` sentinel): transient stall → `Reconnecting` + last frame KEPT (not error) + auto-retry under backoff; terminal 401/403/WebRTC → `DeadEnd` invoked EXACTLY once (no spin); `cancel()` stops all loops + is idempotent + leaves the holder inert (WR-01); D-10 first-cam default-pick when no preference. **GREEN.**

## Task Commits
1. **Task 1: per-printer preferred-cam DataStore (WebcamPrefs) + AppContainer/DinghyApp wiring** — `3d6d68f` (feat)
2. **Task 2: WebcamHolder — rung select + decoder/poller lifecycle + D-11 reconnect machine + WR-01 cancel + replace scaffold** — `6bc5bbc` (feat)
3. **Task 3: WebcamScreen — single-focus + aspect-aware Field + Back-only red gutter** — `3dfd094` (feat)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamPrefs.kt` (NEW) — per-printer fail-safe preferred-cam store.
- `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt` (NEW) — orchestration holder + VM + feed seam + production binding.
- `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt` (NEW) — Focus/Field/Gutter webcam page.
- `app/src/test/java/works/mees/dinghy/webcam/WebcamPrefsTest.kt` (NEW) — single-write round-trip + per-host keying + fail-safe default.
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` (MOD) — `webcamDataStore` param + `webcamPrefs` field.
- `app/src/main/java/works/mees/dinghy/DinghyApp.kt` (MOD) — the 4th `webcam.preferences_pb` file.
- `app/src/test/java/works/mees/dinghy/webcam/WebcamReconnectStateTest.kt` (MOD) — scaffold body replaced, GREEN.
- `app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt` (MOD) — +4th `FakeDataStore()` for the new constructor param.

## Verification
- **Task 1:** `:app:testDebugUnitTest --tests *WebcamPrefs*` → BUILD SUCCESSFUL; `:app:compileReleaseKotlin` → SUCCESSFUL.
- **Task 2:** `:app:testDebugUnitTest --tests *WebcamReconnectStateTest* --tests *WebcamBackoff* --tests *WebcamHolder*` → BUILD SUCCESSFUL (reconnect + backoff GREEN together).
- **Full suite:** `:app:testDebugUnitTest` (whole module) → **BUILD SUCCESSFUL, 0 failures** — `WebcamReconnectStateTest` was the LAST RED scaffold; after this plan every webcam scaffold is GREEN.
- **Release:** `:app:compileReleaseKotlin` → SUCCESSFUL; `:app:assembleRelease` → BUILD SUCCESSFUL (R8/shrink pass green; the holder's production Bitmap path + the screen survive minify).
- **Contains-checks:** `grep "fun cancel"` → present in `WebcamHolder.kt`; `grep "preferred_cam"` → present in `WebcamPrefs.kt`.
- No new dependency (`gradle/libs.versions.toml` unchanged — minSdk-23 floor stays auditable, T-10-SC N/A).

## Decisions Made
- **Generic holder + injected `WebcamFeed` seam (host-provability).** The project has no Robolectric and the reconnect/backoff/terminal/cancel state machine is the genuinely-new logic — so the holder is generic over `T` and drives an injectable feed; the test scripts outcomes with a `String` sentinel frame. The Bitmap-bound `bitmapFeed` wires the unit-proven 10-04 services; its end-to-end behavior is the on-device gate of 10-08. (Same discipline as 10-04's generic `MjpegStreamDecoder<T>`.)
- **Stream-EOF → `Transient`, never a guessed `Terminal`.** A dropped stream keeps the last frame + reconnects (D-11); the holder NEVER decides "dead-end" itself — it re-runs the feed, whose PROBE returns `Terminal` for a real 401/403/WebRTC cam on the next attempt. This keeps the terminal decision where the authority lives (the Content-Type probe, D-02/A4) and avoids a black flash on a transient blip.
- **`cancel()` idempotent + inert via a `@Volatile cancelled` flag.** This project's frozen-feed-after-restart history (Phase-5 G2) makes a complete, repeatable teardown load-bearing: a second `cancel()` is a no-op, and `start()` after `cancel()` is inert (a reconnect builds a fresh holder; the dead one never resurrects a loop).
- **Field gated on `multiCam` + the camera_feed aspect rule; the in-feed cycle overlay only in full-focus.** A single cam is always full-focus (no picker); when a Field shows, the in-feed cycle overlay is suppressed (the picker IS the chooser) so the two cam-selectors never both appear.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `AppContainerTest` construction needed a 4th `FakeDataStore()`**
- **Found during:** Task 1.
- **Issue:** Adding the `webcamDataStore` constructor param to `AppContainer` broke the host test's `AppContainer(FakeDataStore(), FakeDataStore(), FakeDataStore(), lazyDiscovery())` call-site (arity mismatch) — it would not compile, bricking the whole test source set.
- **Fix:** added the 4th `FakeDataStore()` to the test's `newContainer()`.
- **Files modified:** `app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt`.
- **Commit:** `3d6d68f`.

**2. [Rule 2 - Missing test] Added `WebcamPrefsTest` (the Task-1 verify referenced `*WebcamPrefs*`).**
- **Found during:** Task 1 (the plan's automated verify globs `*WebcamPrefs*` but no such test existed — `WebcamPrefs` is new this plan).
- **Issue:** the per-printer DataStore's round-trip + per-host keying + fail-safe default read were otherwise unproven.
- **Fix:** created `WebcamPrefsTest` mirroring `MacroPrefsTest` (single-write round-trip, per-host independence, empty-store default) — host-pure, the documented single-write discipline.
- **Files modified:** `app/src/test/java/works/mees/dinghy/webcam/WebcamPrefsTest.kt`.
- **Commit:** `3d6d68f`.

No architectural decisions (Rule 4) arose. No auth gates.

## Known Stubs
None. `WebcamPrefs`/`WebcamHolder`/`WebcamScreen` are complete and wired to real seams. `bitmapFeed`/`webcamBitmapHolder` are the production Bitmap binding wiring the 10-04 services — declared FORWARD seams the shell consumes in 10-07 (the shell threads the shared OkHttp client + `ConnectionConfig` + view px), NOT placeholder data; their end-to-end + on-device behavior is plan 10-08's verification home. The holder's `start()`/`stop()` surface is consumed by the screen's `DisposableEffect` here AND will be lifecycle-bound by the shell in 10-07.

## Threat Flags
None — no new network endpoint, auth path, or trust boundary beyond the plan's `<threat_model>`. The three registered threats are mitigated:
- **T-10-09 (DoS/battery)** — `cancel()` stops all decode/poll/retry; idempotent + inert (proven by `cancel_stopsAllLoops_andIsIdempotent`); the shell's `DisposableEffect(holder){onDispose{cancel()}}` (10-07) fires it on spine rebuild.
- **T-10-10 (self-DoS SBC)** — 401/403/WebRTC terminal → dead-end no-spin (proven by `terminalAuthFailure_goesDeadEnd_noSpinRetry`); transient backoff 1s→10s foreground-only (`WebcamBackoffTest` + the reconnect test).
- **T-10-13 (corrupt prefs)** — `WebcamPrefs` fail-safe `.catch { IOException → emptyPreferences() }` → null preference → first-in-list, never a crash (proven by `emptyStore_noPreferredCam`).

## Next Phase Readiness
- The orchestration layer (holder + screen + per-printer prefs) is in place and GREEN; the full unit suite is 0-failures (no RED scaffolds remain). Plan 10-07 can: add `Dest.Webcam` + the greyed-when-no-cams drawer tile (off `AppContainer.webcamCount`); build the holder via `webcamBitmapHolder(...)` re-keyed on the live store; route `Dest.Webcam → WebcamScreen(holder, onBack)`; and add the spine-rebuild `DisposableEffect(holder){ onDispose{ holder.cancel() } }` (WR-01). Plan 10-08 drives the on-device snapshot-ladder UAT + pins `MjpegDecodePolicy`/sample-size on flox.
- No blockers.

## Self-Check: PASSED

All 4 created files verified present on disk (`WebcamPrefs.kt`, `WebcamHolder.kt`, `WebcamScreen.kt`, `WebcamPrefsTest.kt`); all 3 task commits (`3d6d68f`, `6bc5bbc`, `3dfd094`) verified in git log; `WebcamReconnectStateTest` + `WebcamPrefsTest` verified GREEN and the FULL `:app:testDebugUnitTest` verified 0-failures; `:app:assembleRelease` verified SUCCESSFUL; the `fun cancel` / `preferred_cam` contains-checks verified present.

---
*Phase: 10-webcam-streaming*
*Completed: 2026-06-04*
