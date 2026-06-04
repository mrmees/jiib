---
phase: 10-webcam-streaming
plan: 07
subsystem: webcam
tags: [webcam, shell-routing, drawer-gating, lifecycle-binding, wr-01, leak-cancel, off-main-dispatch, instrumented-tests, d-08, d-13]

# Dependency graph
requires:
  - phase: 10-03 (enumeration spine)
    provides: AppContainer.webcamCount (the D-08 greyed-gating signal) + SpineHandle.webcams StateFlow
  - phase: 10-06 (orchestration)
    provides: WebcamHolder<Bitmap> / webcamBitmapHolder / WebcamScreen / WebcamPrefs — the seams the shell consumes
provides:
  - Dest.Webcam route + a RUNTIME-gated (D-08) greyed Webcam drawer tile (greyed at 0 cams, live at >=1)
  - AppShell Dest.Webcam arm building/re-keying the WebcamHolder per session+host; WR-01 DisposableEffect leak-cancel
  - Page-visible lifecycle binding (SC-3/D-13) — repeatOnLifecycle(STARTED) gated on dest==Webcam
  - AppContainer.webcamHttpClient — process-scoped shared OkHttp client for the webcam decode/poll layer
  - WebcamHolder.driverContext injectable dispatcher (Dispatchers.IO in production) — off-main decode/probe
  - 3 instrumented tests GREEN on flox: DrawerWebcamGatingTest / WebcamLifecycleTest / WebcamUnsupportedCardTest
affects: [10-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Runtime drawer greyed-gating: thread a `webcamEnabled: Boolean` (from AppContainer.webcamCount>0) into AppDrawer and fold it into the existing `val live = tile.dest != null` decision — the D-08 deliberate departure from Phase-9's hide-the-tile, expressed as the one runtime input the static DRAWER_TILES could not"
    - "Page-visible lifecycle via repeatOnLifecycle(STARTED) keyed on the active dest: the decode/poll/retry loops run ONLY while dest==Dest.Webcam AND the process is foreground; nav-away (dest leaves Webcam) cancels the effect -> stop(); STARTED covers screen-off/home (SC-3/D-13). Composes cleanly with the screen's own DisposableEffect (stop() is idempotent)"
    - "WR-01 leak-cancel layered: the screen's DisposableEffect(holder){start();onDispose{stop()}} is page-level; the shell's DisposableEffect(holder){onDispose{cancel()}} is holder-death-level (spine rebuild / config change) — start/stop is the visible surface, cancel is the teardown"
    - "Injectable driverContext on the holder (default = scope dispatcher for virtual-time host tests; Dispatchers.IO in production via webcamBitmapHolder) so the blocking HTTP probe + JPEG decode never touch the main-thread Compose scope the shell builds the holder on"

key-files:
  created:
    - app/src/androidTest/java/works/mees/dinghy/webcam/DrawerWebcamGatingTest.kt
    - app/src/androidTest/java/works/mees/dinghy/webcam/WebcamLifecycleTest.kt
    - app/src/androidTest/java/works/mees/dinghy/webcam/WebcamUnsupportedCardTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt

key-decisions:
  - "The shared OkHttp client the webcam layer derives off is a PROCESS-SCOPED `AppContainer.webcamHttpClient` (lazy, via MoonrakerSocket.defaultClient()), NOT the per-session ws client. Reason: the per-session client lives in the SERVICE and is not reachable from the UI; the webcam stream/snapshot HTTP is its OWN connection (cadence contract exempts it from the single-subscribe ws); process-scoped means it survives reconnects/printer swaps (like webcamPrefs). One pool/TLS per CLAUDE.md networking law; lazy so no pool until the first Webcam page open."
  - "The Webcam holder is re-keyed on `remember(store, activeCfg.host, viewWidthPx, viewHeightPx)` — store (the MoveHolder per-session precedent) AND the connection host (a printer swap re-resolves D-09 URLs + the per-printer preferred-cam key) AND the view px (the MJPEG decode downscale budget). Each re-key fires the WR-01 DisposableEffect onDispose{cancel()} on the OLD holder, then builds a fresh one."
  - "Webcam JOINS the swipe-up drawer-suppress set (with Files/Console/Macros/Calibration): the full-focus cam-cycle tap overlay wants the whole canvas (a full-canvas vertical-drag detector fights that tap); the explicit red Back gutter is the exit (D-05, PATTERNS.md recommended YES)."
  - "The instrumented dead-end-card test asserts the SCREEN-level contract (holder reaches DeadEnd for a webrtc-mediamtx cam + NO clickable browser/open affordance on the surface), NOT the card text — because drawDeadEndCard paints title/body on the classic-View WebcamView Canvas, so that text is intentionally absent from the Compose semantics tree (the service-name-in-card formatting is a WebcamView unit property)."

patterns-established:
  - "Runtime greyed-gating of a static drawer tile via a threaded boolean folded into `val live`"
  - "repeatOnLifecycle(STARTED) + dest-keyed effect = the canonical page-visible-foreground loop binding"
  - "Injectable driver dispatcher keeps a holder host-testable (virtual time) AND off-main in production"

requirements-completed: []  # CAM-01 is delivered across 10-02..10-08; this plan wires the shell route + gating + lifecycle. On-device snapshot-ladder UAT is 10-08.

# Metrics
duration: ~15min
completed: 2026-06-04
---

# Phase 10 Plan 07: Webcam Shell Wiring (Route + Gating + Lifecycle) Summary

**Wired the Webcam destination into the shell exactly the way the rest of the app routes — a RUNTIME-greyed-gated drawer tile (D-08, the deliberate departure from Phase-9's hide-the-tile: the tile is ALWAYS shown, greyed when the session enumerated 0 cams and live at >=1), the `Dest.Webcam` arm building/re-keying the `WebcamHolder` per per-session store + connection host, the page-visible lifecycle binding (decode/poll/retry runs ONLY while `dest==Dest.Webcam` AND the process is foreground — SC-3/D-13 via `repeatOnLifecycle(STARTED)`), and the WR-01 `DisposableEffect(holder){onDispose{cancel()}}` leak-cancel on spine rebuild — plus the three instrumented gating/lifecycle/dead-end-card tests, all GREEN on the real flox device. An instrumented test caught a genuine off-main-thread bug (the holder's blocking probe/decode ran on the shell's main-thread Compose scope -> `NetworkOnMainThreadException`); fixed with an injectable driver dispatcher (`Dispatchers.IO` in production, scope dispatcher in virtual-time host tests).**

## Performance
- **Duration:** ~15 min
- **Started:** 2026-06-04T03:30:53Z
- **Completed:** 2026-06-04T03:46:04Z
- **Tasks:** 3
- **Files modified:** 8 (3 created, 5 modified)

## Accomplishments
- **`TopRoute.kt`** — added `Webcam` to the `Dest` enum (the one-line panel addition, D-05).
- **`AppDrawer.kt`** — added a `photo_camera` Webcam tile to `DRAWER_TILES` (unique glyph, icon-no-repeat law; `videocam` is a Console gutter glyph, not a drawer tile). Threaded a new `webcamEnabled: Boolean` param through `AppDrawer` -> `DrawerTile` and folded it into the existing live-decision: `val live = tile.dest != null && (tile.dest != Dest.Webcam || webcamEnabled)` — so the Webcam tile renders GREYED (hairline outline, dimmed text, inert `semantics{disabled()}`, no clickable) at 0 cams and LIVE (accent outline, clickable) at >=1. The greyed STYLING was already correct; only the enablement INPUT is new. Default `false` greys it on an idle/no-session drawer.
- **`AppContainer.kt`** — added a process-scoped lazy `webcamHttpClient: OkHttpClient` (= `MoonrakerSocket.defaultClient()`) — the single shared pool the webcam decode/poll layer derives its stream/snapshot postures off (CLAUDE.md networking law). Connection-independent (survives reconnects/printer swaps, like `webcamPrefs`); lazy so no pool until the first Webcam page open.
- **`AppShell.kt`** — collects `webcamCount` -> `webcamEnabled` (passed to `AppDrawer`); collects the live `webcams` StateFlow + the persisted `ConnectionConfig`; builds the `webcamBitmapHolder` re-keyed on `(store, cfg.host, viewWidthPx, viewHeightPx)`; added `DisposableEffect(webcamHolder){onDispose{cancel()}}` (WR-01 leak-cancel on spine rebuild); bound the page-visible lifecycle via a `dest`-keyed `LaunchedEffect` running `repeatOnLifecycle(Lifecycle.State.STARTED){ start(); awaitCancellation()finally stop() }` (SC-3/D-13); added the `Dest.Webcam -> WebcamScreen(holder, onBack=goBack)` arm mirroring `Dest.Move`; added `Dest.Webcam` to the swipe-up drawer-suppress set.
- **`WebcamHolder.kt`** — `[Rule 1 fix]` added an injectable `driverContext` (default = the scope's own dispatcher so virtual-time host tests are unchanged); `start()` now launches the driver on it; `webcamBitmapHolder` passes `Dispatchers.IO` so the production HTTP probe + JPEG decode never run on the main thread.
- **`DrawerWebcamGatingTest`** (instrumented) — mirrors `ShellPresenceTest`: seeds a `SpineHandle` with a `webcams` StateFlow; 0 cams -> the Webcam tile is present but `assertHasNoClickAction()` (greyed); >=1 cam -> `assertHasClickAction()` + tapping navigates (Back gutter appears, drawer collapses).
- **`WebcamLifecycleTest`** (instrumented) — a scripted `WebcamFeed` whose loop increments a counter on entry and suspends on `awaitCancellation`; composing `WebcamScreen` starts the driver (counter==1), decomposing it (nav-away) stops it (counter==0) — proves start-on-visible / stop-on-nav-away (SC-3/D-13).
- **`WebcamUnsupportedCardTest`** (instrumented) — a `webrtc-mediamtx` cam + a `Terminal`-returning feed -> the holder reaches `WebcamView.Mode.DeadEnd` EXACTLY once (no spin, host-observable on the VM) AND the rendered surface has NO clickable browser/open affordance — only the red Back gutter (D-04 / SC-4 / T-10-12).

## Task Commits
1. **Task 1: Dest.Webcam + runtime-gated greyed Webcam drawer tile (D-08)** — `7343fc6` (feat)
2. **Task 2: AppShell Dest.Webcam arm + holder re-key + page-visible lifecycle + WR-01 cancel** — `62e6886` (feat)
3. **Task 3: instrumented gating/lifecycle/dead-end tests + off-main driver fix** — `952d49f` (test)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` (MOD) — `Dest.Webcam`.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` (MOD) — runtime-gated Webcam tile + `webcamEnabled` thread-through.
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` (MOD) — process-scoped `webcamHttpClient`.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` (MOD) — holder build + re-key + WR-01 cancel + lifecycle binding + `Dest.Webcam` arm + swipe-suppress.
- `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt` (MOD) — injectable `driverContext` (off-main production decode).
- `app/src/androidTest/java/works/mees/dinghy/webcam/DrawerWebcamGatingTest.kt` (NEW).
- `app/src/androidTest/java/works/mees/dinghy/webcam/WebcamLifecycleTest.kt` (NEW).
- `app/src/androidTest/java/works/mees/dinghy/webcam/WebcamUnsupportedCardTest.kt` (NEW).

## Verification
- **Task 1:** `:app:compileReleaseKotlin` — exhaustive-`when` break (expected; `Dest.Webcam` forces the AppShell arm) resolved by Task 2; route+drawer changes structurally complete.
- **Task 2:** `:app:compileReleaseKotlin` -> SUCCESSFUL; `:app:testDebugUnitTest` (full module) -> **BUILD SUCCESSFUL, 0 failures**; `:app:assembleRelease` -> **BUILD SUCCESSFUL** (R8/shrink green — the new shell wiring + holder survive minify).
- **Task 3:** `:app:compileDebugAndroidTestKotlin` -> SUCCESSFUL; `:app:assembleDebugAndroidTest` -> SUCCESSFUL.
- **On-device instrumented run (NOT deferred — a device WAS attached):** `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=works.mees.dinghy.webcam` -> **4 tests, 0 failures on flox (Nexus 7 / API 30)** — `DrawerWebcamGatingTest` (2), `WebcamLifecycleTest` (1), `WebcamUnsupportedCardTest` (1) all GREEN.
- **Post-Rule-1-fix:** `:app:testDebugUnitTest` re-run -> still 0 failures (the `driverContext` default preserves virtual-time host behavior).
- No new dependency (`gradle/libs.versions.toml` unchanged — minSdk-23 floor stays auditable, T-10-SC N/A).

## Decisions Made
- **Process-scoped shared OkHttp client for the webcam layer (`AppContainer.webcamHttpClient`).** The per-session ws client lives in the SERVICE (unreachable from the UI); the webcam stream/snapshot HTTP is its own connection (cadence-contract-exempt from the single-subscribe ws). A process-scoped lazy client = one pool/TLS (CLAUDE.md law) that survives reconnects/printer swaps and allocates nothing until the first Webcam page open.
- **Holder re-key on `(store, cfg.host, viewWidthPx, viewHeightPx)`.** Store = the MoveHolder per-session precedent; host = a printer swap re-resolves D-09 URLs + the per-printer preferred-cam key; view px = the MJPEG decode downscale budget. Every re-key fires the WR-01 `onDispose{cancel()}` on the old holder first.
- **Webcam joins the swipe-suppress set.** The full-focus cam-cycle tap overlay wants the canvas; the red Back gutter is the exit (D-05, PATTERNS.md YES).
- **Dead-end-card instrumented assertion is screen-contract-level, not card-text.** `drawDeadEndCard` paints on the classic-View `WebcamView` Canvas -> not in Compose semantics; the test proves DeadEnd mode (VM-observable) + no browser-button affordance. The service-name-in-card formatting is a `WebcamView` unit property (the strings `DEAD_END_BODY_FMT`/`DEAD_END_BODY_GENERIC` are Canvas-drawn).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] WebcamHolder driver ran on the main-thread Compose scope -> NetworkOnMainThreadException**
- **Found during:** Task 3 (the first on-device run of `DrawerWebcamGatingTest.withCams_webcamTileIsLiveAndNavigates`).
- **Issue:** the shell builds the holder on `rememberCoroutineScope()` (the MAIN thread). `WebcamHolder.start()` launched its driver directly on that scope, so the production `bitmapFeed`'s blocking `WebcamProbe.probe()` (a synchronous OkHttp `Call.execute()`) ran on the main thread and threw `NetworkOnMainThreadException` the instant the page opened against a real cam. This would have hit EVERY real-cam open in production, not just the test.
- **Fix:** added an injectable `driverContext: CoroutineContext` to `WebcamHolder` (default `EmptyCoroutineContext` = the scope's own dispatcher, so the virtual-time host tests are unchanged); `start()` launches the driver on it; `webcamBitmapHolder` (the production builder) passes `Dispatchers.IO`. Cancellation still propagates (the child job is cancelled with the parent scope on `stop()`/`cancel()` regardless of dispatcher). RESEARCH explicitly mandates "decode on a background dispatcher."
- **Files modified:** `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt`.
- **Commit:** `952d49f`.

No architectural decisions (Rule 4) arose. No auth gates. The `webcamHttpClient` addition to `AppContainer` is the plan-prescribed shared-client seam (PATTERNS.md "thread that same client to the webcam layer"), not a deviation — the plan's read_first pointed at a `sharedClient` the shell had to source, and the process-scoped container client is the in-idiom place (the per-session client is service-private).

## Known Stubs
None. The shell route, gating, holder build, lifecycle binding, and leak-cancel are all wired to real seams (`AppContainer.webcamCount`/`webcams`/`webcamHttpClient`/`webcamPrefs`, `connectionStore.config`, `webcamBitmapHolder`). The end-to-end live decode (snapshot ladder on E3, rung-3 card on the real WebRTC streams, `gfxinfo` 0-frozen-frames) is plan 10-08's on-device UAT — declared FORWARD, not a placeholder.

## Threat Flags
None — no new network endpoint, auth path, or trust boundary beyond the plan's `<threat_model>`. The registered threats are mitigated:
- **T-10-09 (DoS/battery)** — the page-visible `repeatOnLifecycle(STARTED)` gate runs the loops ONLY while `dest==Dest.Webcam` + foreground; the `DisposableEffect(webcamHolder){onDispose{cancel()}}` fires on spine rebuild -> no background decode, no leaked stream (proven by `WebcamLifecycleTest` start-on-visible/stop-on-nav-away).
- **T-10-14 (greyed-tile navigability)** — the `!webcamEnabled` Webcam tile is inert (no clickable, `semantics{disabled()}`) — proven by `DrawerWebcamGatingTest.zeroCams_webcamTileIsGreyedAndInert` (`assertHasNoClickAction`).
- **T-10-SC (installs)** — N/A; `libs.versions.toml` unchanged.

## Next Phase Readiness
- The Webcam destination is fully shell-wired: greyed-gated drawer tile (D-08), `Dest.Webcam` arm, per-session+host holder re-key, page-visible lifecycle (SC-3/D-13), WR-01 leak-cancel, off-main decode dispatcher. Full unit suite 0-failures; release assembles; 3 instrumented tests GREEN on flox.
- **Plan 10-08** runs the on-device snapshot-ladder UAT (E3 192.168.1.121: ~2 fps snapshot + "Snapshot ~2fps" badge), the rung-3 dead-end card on the real E5/E3 `webrtc-mediamtx` streams, and the `gfxinfo` perf gate (p95 within budget + 0 frozen frames during ~15 s decode) — and pins `MjpegDecodePolicy`/sample-size on flox. The off-main driver fix from this plan is a prerequisite that 10-08's live decode now depends on.
- No blockers.

## Self-Check: PASSED

All 3 created instrumented test files verified present on disk (`DrawerWebcamGatingTest.kt`, `WebcamLifecycleTest.kt`, `WebcamUnsupportedCardTest.kt`) + the SUMMARY; all 3 task commits (`7343fc6`, `62e6886`, `952d49f`) verified in git log; full `:app:testDebugUnitTest` verified 0-failures; `:app:assembleRelease` verified SUCCESSFUL; the 4 webcam instrumented tests verified GREEN on the attached flox device (0 failures).

---
*Phase: 10-webcam-streaming*
*Completed: 2026-06-04*
