---
phase: 13-optimization-network-efficiency-end-to-end-reliability
plan: 05
subsystem: net
tags: [moonraker, websocket, okhttp, ping-interval, keepalive, reconnect, splash, nav-hoist, d-05-departure, gap-closure, mock-vs-reality]

# Dependency graph
requires:
  - phase: 13-optimization-network-efficiency-end-to-end-reliability
    plan: 04
    provides: the binding-gate FAIL evidence (G-A1 Home-bounce + G-B1 silent half-open mid-print drop) this plan closes
  - phase: 13-optimization-network-efficiency-end-to-end-reliability
    plan: 02
    provides: the visible/disconnect-driven/self-healing klippy-restart recovery the reconnect-splash routing extends to the socket path
provides:
  - G-B1a — OkHttp pingInterval (~10s) websocket keepalive on the shared client, so a half-open WiFi drop is detected within ~one interval → onFailure → SocketEvent.Closed → reconnect; pinned by MoonrakerSocketClientTest
  - G-A1 — shell nav state (dest + backStack + calibrationRoutine) HOISTED into RootController (new ShellNavState) above the Splash/Shell switch, so a transient recovery Splash never resets the user to Home
  - G-B1b — derive() now routes the FULL Syncing splash on socket reconnect (the D-05 departure) + a RootController-owned ~600ms min-dwell latch so the recovery splash is perceptible on BOTH the klippy-restart and socket-reconnect paths
  - The PASSING re-run of the binding D-09 on-device UAT (Run #2)
affects: [14]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Websocket keepalive (OkHttp pingInterval) is the load-bearing half-open-drop detector — readTimeout(0) stays (a ws must not die on a read timeout); pingInterval is the correct mechanism, not readTimeout"
    - "Hoist plain remember nav state ABOVE the route switch (cleaner than a SaveableStateHolder here, Codex-reviewed) so a transient Splash decompose never loses it"
    - "Min-dwell as a RootController-OWNED UI latch that delays HIDING the splash only — never blocks actual recovery; Connect/Settings routes bypass the floor entirely"
    - "derive() stays a PURE function driven directly by TopRouteTest — the new socket-reconnect arm is ordered AFTER first-run and the klippy gate so neither is trapped behind a bare Syncing splash"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
    - app/src/test/java/works/mees/dinghy/net/MoonrakerSocketClientTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSocket.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt
    - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
    - app/src/test/java/works/mees/dinghy/ui/route/TopRouteTest.kt
    - docs/ui_design/CLAUDE.md
    - .planning/phases/13-optimization-network-efficiency-end-to-end-reliability/13-UAT.md

key-decisions:
  - "D-05 DEPARTURE (Matthew 2026-06-03): socket ConnectionState NOW routes the recovery Splash — supersedes the original D-05 'socket state is chrome, never routes'. SAFE only because G-A1 hoisted the nav state so the splash no longer bounces the user."
  - "derive() arm order is load-bearing (Codex-reviewed): !cfg→Connect ; klippy!=Ready→Splash ; connection !is Connected→Splash ; else→Shell. First-run and the klippy gate still win; SplashScreen already maps Disconnected/Error→Unreachable+Retry/Edit so an OFF printer isn't trapped in an eternal dead 'Syncing' and the Settings escape stays reachable."
  - "Sub-nav disposition (Codex-required, NOT left to judgment): HOIST+PRESERVE dest+backStack+calibrationRoutine; RESET macroPopupFor on return (a transient popup must not survive a reconnect); PRESERVE macroShowSystem (a view preference, not transient state)."
  - "Min-dwell is a RootController-owned latch that delays HIDING only — it never blocks recovery; the Connect/Settings escapes are never floored."
  - "4th mock-vs-reality strike: the D-07b unit test was GREEN because FakeWebSocket synthesizes Closed; real OkHttp never emits Closed on a half-open drop without keepalive. The live gate (13-04) caught it; MoonrakerSocketClientTest now pins pingIntervalMillis > 0 as the regression guard."

patterns-established:
  - "A config-only host test (assert defaultClient().pingIntervalMillis == constant) pins a wire-keepalive contract that no socket-level fake can express"
  - "Nav-state hoist as the prerequisite that makes socket-state routing safe (no Home bounce)"

requirements-completed: []

# Metrics
duration: ~ (3 auto tasks + the passing re-run human gate)
completed: 2026-06-03
---

# Phase 13 Plan 05: Gap-Closure — Keepalive + Nav-Hoist + Reconnect-Splash (13-04 Binding-Gate FAIL) Summary

**Closed the three defects the 13-04 binding gate caught on real hardware and re-passed it. G-B1a: added an OkHttp `pingInterval` (~10s) websocket keepalive to the shared client so a half-open WiFi drop is now detected within ~one interval (→ `onFailure` → `SocketEvent.Closed` → the reconnect supervisor runs), pinned by a new config-only `MoonrakerSocketClientTest` — the 4th mock-vs-reality strike, where `FakeWebSocket` synthesizes `Closed` while real OkHttp never does without keepalive. G-A1: HOISTED the shell nav state (`dest` + `backStack` + `calibrationRoutine`) out of AppShell-local `remember` into a root-scoped `ShellNavState` owned above the RootController Splash/Shell switch, so a transient recovery Splash that decomposes AppShell no longer resets the user to Home (`macroPopupFor` reset on return, `macroShowSystem` preserved). G-B1b: made `derive()` route the FULL Syncing splash on socket reconnect (the deliberate D-05 departure — Matthew 2026-06-03) in the exact Codex-reviewed arm order, plus a RootController-owned ~600ms min-dwell latch so the recovery splash is perceptible on BOTH the klippy-restart and socket-reconnect paths; docs/ui_design synced. Full `:app:testReleaseUnitTest` GREEN + `:app:assembleRelease` SUCCESSFUL (guarded), signed APK on flox — and the RE-RUN dual-printer dual-scenario on-device UAT PASSED (Matthew: "Both pass").**

## Performance

- **Duration:** 3 auto tasks (the fixes) + the passing re-run human gate (Task 4)
- **Completed:** 2026-06-03
- **Tasks:** 4 (Tasks 1–3 the fixes; Task 4 the re-run binding gate — PASSED)
- **Files modified:** 8 (2 production + 2 test + 1 new prod file + 1 new test file + docs/ui_design + 13-UAT.md)

## The three fixes

### G-B1a — OkHttp pingInterval keepalive (the BLOCKING fix) — `404e00e`
`MoonrakerSocket.defaultClient()` now sets `.pingInterval(PING_INTERVAL_MS, MILLISECONDS)` (~10s). A WiFi-off drop is a half-open TCP socket; OkHttp's pingInterval sends WS PINGs and fails the connection on a missing PONG within the interval → `onFailure` → `SocketEvent.Closed` → the reconnect supervisor runs and resyncs. `readTimeout(0)` stays (a websocket must not die on a read timeout) — pingInterval is the correct half-open-detection mechanism. The new `MoonrakerSocketClientTest` asserts `defaultClient().pingIntervalMillis > 0` (and == the constant) — a pure config assertion, no socket needed. **This is the regression guard for the exact unit gap that let the live failure through:** `FakeWebSocket` synthesizes `Closed`, so the D-07b mid-print resync test was GREEN while the real OkHttp path froze forever (the project's 4th mock-vs-reality strike).

### G-A1 — hoist the shell nav state above the Splash/Shell switch — `5451638`
The shell's `dest` + `backStack` lived in AppShell-local `remember`, so whenever RootController swapped AppShell out for SplashScreen (klippy drop, or — after Task 3 — socket reconnect), AppShell decomposed and on return re-initialized to `Dest.PrintStatus` = Home. Hoisted this nav state into a new root-scoped `ShellNavState` owned ABOVE the route switch (in RootController) and passed into AppShell. **Explicit sub-nav disposition (Codex-required):**
- **HOIST + PRESERVE:** top-level `dest`, `backStack`, AND the in-progress Calibration `calibrationRoutine` — so a user mid-calibration-routine returns to it, not to Home or a blank hub.
- **RESET on return:** `macroPopupFor` — a transient macro-execution popup must not survive a reconnect (re-opening a half-state popup is wrong).
- **PRESERVE:** `macroShowSystem` (the System-vs-Bookmarked toggle) — a view preference, not transient state.

`navigateTo`/`back` semantics are unchanged (PrintStatus clears the backStack, etc.); did NOT switch to Navigation-Compose (D-05's lean `when(dest)` holder stands — only its state moved up). Hoisting plain `remember` was chosen over a `SaveableStateHolder` (cleaner here, Codex-reviewed).

### G-B1b — route the full Syncing splash on socket reconnect + min-dwell + doc sync — `777a74d`
**(A)** `derive()` now returns `TopRoute.Splash` ALSO when the socket is mid-reconnect with a config present and not first-run, in the **exact Codex-reviewed arm order** (order is load-bearing):
```
!cfgPresent              -> Connect
klippyState != Ready     -> Splash
connection !is Connected -> Splash   // NEW: reconnecting (Connecting/Syncing/Disconnected/Error)
else                     -> Shell
```
First-run Connect and the klippy gate still win; an auth failure is not trapped behind a bare Syncing splash. SplashScreen already maps Disconnected/Error → an "Unreachable" surface with Retry + "Edit connection", so a printer that is simply OFF shows a reachable Unreachable/Edit screen, NOT an eternal dead "Syncing" — the Settings escape stays reachable. `derive()` stays a PURE function (TopRouteTest drives it directly).
**(B)** Minimum perceptible dwell as a **RootController-owned UI latch** (NOT in the socket/session layer): on entering Splash, mark visible; on leaving, delay the remainder of ~600ms then hide, cancelling/recomputing on re-entry. The Connect/Settings routes bypass the dwell entirely. Net: a recovery completing in <600ms still shows the splash for ~600ms — perceptible on BOTH paths (the silent-fast-recovery Matthew couldn't catch last run).
**(C)** Synced `docs/ui_design/CLAUDE.md` to record that socket reconnect now shows the full recovery Splash (supersedes the earlier "socket ConnectionState is chrome, never routes" rule) — with nav hoisted so it never bounces the user. Extended `TopRouteTest` with the reconnecting/Connected/first-run/klippy arms.

## Codex correctness review (applied)

Codex's must-fix was the **explicit sub-nav enumeration** — NOT leaving "which sub-nav state survives the Splash blip" to judgment, because getting it wrong returns the user into a stale page after recovery. Applied verbatim: hoist+preserve `dest`/`backStack`/`calibrationRoutine`, reset `macroPopupFor`, preserve `macroShowSystem`. Codex also reviewed and fixed the load-bearing `derive()` arm order (so first-run + the klippy gate still win and an auth failure isn't trapped behind a Syncing splash) and confirmed the SplashScreen Unreachable/Edit-connection escape holds on the new reconnect-splash path (an OFF printer is not eternally "Syncing").

## The D-05 departure (decision)

**D-05 DEPARTURE (Matthew 2026-06-03):** socket `ConnectionState` NOW routes the recovery Splash — overriding the original D-05 rule that "socket state is chrome, never routes" (the rule TopRoute carried since 04-02). Approved scope: fix G-B1 + G-A1, and show the FULL Syncing splash on socket reconnect too. This is SAFE specifically because G-A1 hoisted the nav state — so the recovery splash no longer bounces the user to Home. Recorded in STATE.md and docs/ui_design/CLAUDE.md so the design LAW and the code stay in sync.

## The 4th mock-vs-reality lesson

The D-07b mid-print-resync unit test was GREEN, yet the live mid-print WiFi drop froze the feed forever. Why: `FakeWebSocket` synthesizes a `Closed` event, so the test's reconnect path always fired; **real OkHttp never emits `Closed` on a half-open socket without a keepalive ping**, so production never detected the dead peer. This is the project's 4th mock-vs-reality strike (after the identify-needs-`url` bug, the too-lenient subscribe fake, and the klippy-ready re-handshake gaps) — the live binding gate (13-04) caught what the green suite couldn't. The new `MoonrakerSocketClientTest` pins `pingIntervalMillis > 0` so the keepalive can't silently regress, but the durable lesson is that the live gate is the backstop a config-mismatch like this needs.

## Verification (all hard-timeout-guarded; exit codes authoritative)

| Run | Filter | Result |
|---|---|---|
| Keepalive config test | `--tests works.mees.dinghy.net.MoonrakerSocketClientTest` | **GREEN** (pingIntervalMillis > 0) |
| Routing test | `--tests works.mees.dinghy.ui.route.TopRouteTest` | **GREEN** (reconnect→Splash, all arms) |
| Release build (nav-hoist) | `:app:assembleRelease` | **BUILD SUCCESSFUL** |
| Full unit suite | `:app:testReleaseUnitTest` (no filter) | **GREEN** |
| Signed APK on flox | `sign-release.bat` + `adb install -r` | **Success** |

## The passing re-run (Task 4 — the binding gate, Run #2)

Recorded in 13-UAT.md as **PASSED (2026-06-03, Run #2 after 13-05)**. Matthew re-ran both scenarios on both live printers and reported **"Both pass"**:
- **Scenario A (E5 + E3):** SAVE_CONFIG → visible Syncing splash → feed resumes (no restart) → new print registers → **stays on the current screen** (G-A1 fixed).
- **Scenario B (E5 + E3):** mid-print WiFi off → full Syncing splash within ~one keepalive interval (G-B1a detects the half-open drop) → restore WiFi → print state resyncs (G-B1 fixed).
- **D-03:** the ~600ms min-dwell makes the splash perceptible on EVERY recovery (both paths).
- **Backstops:** Probe-Calibrate z_offset fresh after SAVE_CONFIG; Temp/Move/Files show correct live data (behavior-preserving).

## Task Commits

1. **Task 1: G-B1a — OkHttp pingInterval keepalive + MoonrakerSocketClientTest** — `404e00e` (fix)
2. **Task 2: G-A1 — hoist shell nav state above the Splash/Shell switch (ShellNavState)** — `5451638` (fix)
3. **Task 3: G-B1b — socket-reconnect Splash + ~600ms min-dwell + TopRouteTest + doc sync** — `777a74d` (fix)
4. **Task 4: re-run binding gate (PASSED)** — recorded in 13-UAT.md, finalized with this plan's metadata commit.

(Interim docs commits `6586ff3` plan, `32cd139` reset UAT rows for the 2nd run, `33ac3a5` recorded Tasks 1-3 + the D-05 departure.)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/net/MoonrakerSocket.kt` — `pingInterval(~10s)` keepalive on the shared client.
- `app/src/test/java/works/mees/dinghy/net/MoonrakerSocketClientTest.kt` (new) — pins `pingIntervalMillis > 0`.
- `app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt` (new) — root-scoped hoisted nav state.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — accepts nav state from the root (no AppShell-local `remember` that dies on the Splash override).
- `app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt` — owns ShellNavState; the min-dwell latch; routes reconnect→Splash.
- `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` — `derive()` reconnect arm.
- `app/src/test/java/works/mees/dinghy/ui/route/TopRouteTest.kt` — reconnect/Connected/first-run/klippy arms.
- `docs/ui_design/CLAUDE.md` — records the socket-reconnect-now-routes-Splash departure.
- `.planning/phases/13-.../13-UAT.md` — re-run result rows finalized PASSED.

## Decisions Made

See key-decisions in frontmatter: the D-05 departure (socket state routes the splash, safe because nav is hoisted); the load-bearing `derive()` arm order; the Codex-required sub-nav disposition (preserve dest/backStack/calibrationRoutine, reset macroPopupFor, preserve macroShowSystem); min-dwell as a hide-only latch; and the 4th mock-vs-reality strike pinned by a config-only test.

## Deviations from Plan

None — Tasks 1–3 executed exactly as written (the Codex-reviewed arm order and sub-nav disposition were baked into the plan), and Task 4 passed on the re-run.

## Known Stubs

None.

## Threat Flags

None — no new endpoints, auth paths, file access, or schema. The keepalive adds bounded WS PING/PONG traffic (~one frame / 10s) — well within the cadence contract; the self-heal reconnect reuses the proven jittered-backoff supervisor (T-13-09 reconnect-storm disposition unchanged, exercised live on the re-run).

## Issues Encountered

None during the fix tasks — the build/test/install were clean and guarded. The defects being fixed are documented in 13-04's SUMMARY and 13-UAT.md ## Gaps.

## User Setup Required

None — no external service configuration. (Task 4 required the human operator + two live printers — the nature of the binding manual gate.)

## Next Phase Readiness

- **The binding D-09 gate is SATISFIED on the re-run.** The silent mid-print-drop class and the Home-bounce UX defect are both dead on real hardware. Phase 13's reliability work is complete pending the verifier.
- The deferred print-loop robustness (reconnect print-state resync on a real in-progress print, process-death recovery) remains Phase 14's scope per the roadmap — this plan hardened the in-session socket-drop/keepalive detection path it builds on.
- No blockers. (Phase-complete is the orchestrator's call after the verifier runs — not marked here.)

## Self-Check: PASSED

- All new/modified files exist on disk (MoonrakerSocket.kt, MoonrakerSocketClientTest.kt, ShellNavState.kt, AppShell.kt, RootController.kt, TopRoute.kt, TopRouteTest.kt, docs/ui_design/CLAUDE.md, 13-UAT.md).
- Task commits verified in git log: `404e00e`, `5451638`, `777a74d` (plus interim docs `6586ff3`/`32cd139`/`33ac3a5`).
- 13-UAT.md reads **PASSED (Run #2 after 13-05)**.

---
*Phase: 13-optimization-network-efficiency-end-to-end-reliability*
*Completed: 2026-06-03*
