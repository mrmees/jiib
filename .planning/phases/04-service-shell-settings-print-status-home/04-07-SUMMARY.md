---
phase: 04-service-shell-settings-print-status-home
plan: 07
subsystem: ui
tags: [shell, app-drawer, routing, compose, navigation, instrumented-test, e2e-wiring]

# Dependency graph
requires:
  - phase: 04-02
    provides: "TopRoute.derive(cfgPresent, state) — the single pure routing authority (Connect/Splash/Shell)"
  - phase: 04-03
    provides: "AppContainer + SpineHandle + SessionControl + MoonrakerService (FGS that owns the spine); DinghyApp container"
  - phase: 04-04
    provides: "SettingsScreen(container, onConnectionSaved) — conventional keyboard-allowed settings"
  - phase: 04-05
    provides: "SplashScreen(container, hasConfig, state, onEditConnection) — hard-override recovery surface"
  - phase: 04-06
    provides: "PrintStatusScreen(container, holder) + PrintStatusHolder(scope, store) — state-adaptive home"
  - phase: 04-06b
    provides: "GraphViewHost sparkline wired into the Print Status Field slot (combined-render gate held open)"
provides:
  - "AppDrawer — swipe-up full-screen tile grid (Status+Settings live; Move/Temp/Files/Tools/Macros/Devices + red Power greyed/inert)"
  - "AppShell — full-bleed in-shell Dest host (lean route holder, swipe-up drawer + BackHandler collapse, in-shell Settings as Dest.Settings)"
  - "RootController — the single top-level routing authority + the one open-Settings escape (review #2/#11)"
  - "Grown-up MainActivity — starts the FGS, one DinghyTheme boundary, delegates all routing to RootController"
  - "SpineHandle.store — per-session PrinterStateStore exposed so the UI builds the PrintStatusHolder from the live store"
  - "ShellPresenceTest — instrumented drawer/routing/splash-no-drawer + notif-denied smoke (PASSES on flox)"
affects: [phase-5-files-print, phase-6-job-status, phase-7-console-macros, "any future panel = one Dest + one drawer tile"]

# Tech tracking
tech-stack:
  added: [compose-ui-test-junit4, compose-ui-test-manifest]
  patterns:
    - "Single root routing authority: ONE RootController consumes derive() and owns the one open-Settings escape; Splash/AppShell never self-route"
    - "Lean Dest route holder (var dest by remember mutableStateOf) — NOT Navigation-Compose (D-05); a new panel = one when-branch + one drawer tile"
    - "Swipe-up full-screen App Drawer hosted as a Dialog over the full-bleed destination canvas; greyed tiles carry no click action (inert by construction)"
    - "Per-session UI holders built off SpineHandle.store and remember(store)-keyed so a spine rebuild re-keys the holder onto the new session"
    - "Instrumented Compose test hosts the production RootController under createComposeRule + real DinghyTheme; routing seeded via published SpineHandle (no live socket)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt
    - app/src/androidTest/java/works/mees/dinghy/ui/ShellPresenceTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/MainActivity.kt
    - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
    - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
    - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt
    - gradle/libs.versions.toml
    - app/build.gradle.kts

key-decisions:
  - "Exposed the per-session PrinterStateStore on SpineHandle (Rule 3 wiring fix) so the shell can construct PrintStatusHolder(scope, store) from the LIVE session store — the holder's contract (it needs the concrete store, not just its flows) made this the lowest-risk seam; service still constructs, UI still only consumes (D-02)."
  - "AppDrawer is a full-screen Dialog over the destination canvas; greyed 'coming soon' tiles (incl. red Power) are rendered with NO clickable + semantics{disabled()} so they are inert BY CONSTRUCTION — a UI test sees the tile but it cannot navigate (T-04-07-E)."
  - "ShellPresenceTest hosts the production RootController under createComposeRule (not MainActivity) and seeds routing by publishing a SpineHandle whose printerState klippyState forces Shell (Ready) vs Splash (Shutdown) through pure derive() — no live socket, mirrors the AppContainerTest fake-spine discipline."

patterns-established:
  - "Single root routing authority — no competing routers in Splash/AppShell/MainActivity"
  - "Lean Dest route holder over Navigation-Compose (D-05)"
  - "Greyed/inert navigation tiles via absent click action (not a disabled-color-only visual)"

requirements-completed: [SHELL-01]

# Metrics
duration: 95min
completed: 2026-06-01
---

# Phase 4 Plan 07: Shell, App Drawer & Root Routing Wiring Summary

**The app is now navigable and whole: MainActivity starts the FGS and hosts ONE DinghyTheme boundary that delegates ALL top-level routing to a single RootController (Connect/escape → Settings, Splash hard-override, Shell → AppShell), with a swipe-up full-screen App Drawer (Status+Settings live, the rest + red Power greyed/inert) over a full-bleed destination canvas — proven on the real flox device by a 4-case instrumented ShellPresenceTest.**

## Performance

- **Duration:** ~95 min
- **Started:** 2026-06-01
- **Completed:** 2026-06-01
- **Tasks:** 3 of 4 (Task 4 is a blocking human-verify checkpoint — see Next Phase Readiness)
- **Files modified:** 10 (4 created, 6 modified)

## Accomplishments
- Replaced the Phase-1 "Dinghy Display — scaffold" stub `MainActivity` with the real shell host: it resolves the `AppContainer`, starts the `MoonrakerService` FGS, and composes one `DinghyTheme` boundary delegating all routing to `RootController`.
- `RootController` is the SINGLE routing authority: it is the only consumer of `derive()` and the only owner of the "open Settings outside the Shell" escape (review #2/#11). Splash and AppShell never self-route.
- `AppShell` renders the active `Dest` full-bleed with no persistent status bar, a lean `when(dest)` route holder (no Navigation-Compose), a swipe-up drawer + `BackHandler` collapse, and in-shell Settings as a `Dest.Settings`.
- `AppDrawer` is the swipe-up full-screen square-tile grid matching the `02-app-drawer.png` mockup: Status + Settings live; Move/Temp/Files/Tools/Macros/Devices + red Power greyed and inert (no click action).
- The combined Print Status surface (ring/temp-readout + 2×3 grid + GraphView sparkline + gutter) is now actually composed on-device through `PrintStatusScreen(container, holder)`, built from the live per-session store — which **unblocks the 04-06b combined-render perf gate** (it is now runnable, no longer a scaffold stub).
- `ShellPresenceTest` runs GREEN on the real flox device (Nexus 7 - 11, API 30, `0a64b42e`) — all 4 cases pass.

## Task Commits

Each task was committed atomically:

1. **Task 1: AppDrawer + AppShell** — `8119092` (feat) — also exposed `SpineHandle.store` + updated the service + the AppContainerTest fake (Rule 3 wiring fix)
2. **Task 2: RootController + grown-up MainActivity** — `d265b3c` (feat)
3. **Task 3: ShellPresenceTest (runs green on flox)** — `3fe90c6` (test) — added the BOM-governed Compose UI test deps

**Plan metadata:** docs commit (this SUMMARY + STATE + ROADMAP) follows.

## Files Created/Modified
- `ui/shell/AppDrawer.kt` (created) — swipe-up full-screen tile grid; live Status+Settings, greyed Move/Temp/Files/Tools/Macros/Devices + red Power (inert).
- `ui/shell/AppShell.kt` (created) — full-bleed Dest host; lean route holder, swipe-up gesture + BackHandler, per-session `PrintStatusHolder` keyed on `SpineHandle.store`.
- `ui/shell/RootController.kt` (created) — the single root routing authority + the one open-Settings escape.
- `MainActivity.kt` (modified) — Phase-1 scaffold replaced; starts the FGS, one DinghyTheme boundary, delegates routing to RootController.
- `di/SpineHandle.kt` (modified) — added `store: PrinterStateStore` so the UI builds the holder from the live session store.
- `service/MoonrakerService.kt` (modified) — populates `SpineHandle.store` with the session's store.
- `di/AppContainerTest.kt` (modified, test) — fake handle updated for the new `store` field.
- `androidTest/.../ui/ShellPresenceTest.kt` (created) — instrumented proof; passes on flox.
- `gradle/libs.versions.toml` + `app/build.gradle.kts` (modified) — BOM-governed `compose-ui-test-junit4` + `ui-test-manifest`.

## Decisions Made
- **Exposed the per-session `PrinterStateStore` on `SpineHandle`.** `PrintStatusHolder`'s contract needs the concrete store (not just its StateFlows), and the store is per-session and owned by the spine. Adding `store` to `SpineHandle` is the lowest-risk seam that preserves the "service constructs, UI consumes" discipline (D-02) without touching the holder's tested API. (Rule 3 — blocking wiring fix.)
- **Greyed tiles are inert by construction, not just dimmed.** A "coming soon" tile (incl. red Power) is rendered with no `clickable` and `semantics { disabled() }`, so it cannot navigate even if tapped — the test asserts `assertHasNoClickAction()`. This directly mitigates T-04-07-E (the Power elevation path).
- **The instrumented test hosts `RootController` (not `MainActivity`)** under `createComposeRule`, seeding routing by publishing a fake `SpineHandle` — exercising the REAL router/drawer/splash code without opening a socket, mirroring the AppContainerTest fake-spine discipline.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Exposed the per-session `PrinterStateStore` on `SpineHandle`**
- **Found during:** Task 1 (AppShell — wiring `PrintStatusScreen(container, holder)`)
- **Issue:** `AppShell` must construct a `PrintStatusHolder(scope, store)`, but `PrintStatusHolder` requires a concrete `PrinterStateStore`. The container/spine previously exposed only the store's StateFlows (`printerState`/`capabilities`), not the store object — so the shell could not build the holder and the combined Print Status surface could not compose.
- **Fix:** Added a `store: PrinterStateStore` field to `SpineHandle`; the service (which already constructs the store) now publishes it on the handle; `AppShell` reads `spine.store` and `remember(store)`-keys the holder. Idle (null spine) uses a local fallback store so the home surface still composes.
- **Files modified:** `di/SpineHandle.kt`, `service/MoonrakerService.kt`, `ui/shell/AppShell.kt`, `test/.../di/AppContainerTest.kt` (fake handle).
- **Verification:** `:app:assembleDebug` green; `:app:testDebugUnitTest` green (AppContainerTest still passes with the new field); ShellPresenceTest composes the Shell route on-device.
- **Committed in:** `8119092` (Task 1 commit)

**2. [Rule 3 - Blocking] Added the Compose UI test dependencies**
- **Found during:** Task 3 (ShellPresenceTest)
- **Issue:** The androidTest source set had no `compose-ui-test-junit4`/`ui-test-manifest`, so `createComposeRule` / the Compose test matchers were unavailable.
- **Fix:** Added BOM-governed `compose-ui-test-junit4` (androidTestImplementation) + `ui-test-manifest` (debugImplementation, supplies the empty test host Activity) to the catalog + `build.gradle.kts`. NOT a third-party package install (BOM-pinned AndroidX, no slopsquat risk).
- **Files modified:** `gradle/libs.versions.toml`, `app/build.gradle.kts`.
- **Verification:** `:app:compileDebugAndroidTestKotlin` green; the test runs on flox.
- **Committed in:** `3fe90c6` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 3 — blocking). Both were necessary to compose the wired surface / author the instrumented gate. No scope creep; the only production-type change (SpineHandle.store) keeps the "service constructs, UI consumes" discipline intact.

## Issues Encountered
- **ShellPresenceTest first runs (2 failures), fixed on-device, then green.** The 9-tile drawer overflows one screen, so lower tiles (Settings, Power) are lazily un-composed below the fold; the first test used `assertIsDisplayed()` on off-screen tiles. Fixed by scrolling the lazy grid to the node (`onNode(hasScrollAction()).performScrollToNode(hasText(...))`) before asserting. A follow-up `assertExists()` on an upper tile (Move) after scrolling down failed because the lazy grid had recycled it — switched the "tapping a greyed tile is inert" check to assert the tapped tile (Power) is still composed (drawer didn't collapse). After both fixes all 4 cases pass on flox. (No production code weakened — the drawer legitimately scrolls; the test now drives it the way a user would.)

## Threat Flags

None — no new security-relevant surface beyond the plan's `<threat_model>`. The greyed red Power tile (T-04-07-E) is rendered inert (no click action), asserted by ShellPresenceTest; the splash hard-override composes no AppShell (T-04-07-trap), asserted by ShellPresenceTest; the single RootController owns routing (T-04-07-route).

## Next Phase Readiness
- **The CODE + instrumented-test portion of 04-07 is COMPLETE and proven on-device.** MainActivity routes the real app; ShellPresenceTest is green on flox.
- **04-06b's combined-render perf gate is now RUNNABLE.** The production Print Status surface (ring/temp-readout + 2×3 grid + GraphView sparkline + gutter) is composed on-device via the wired MainActivity → RootController → AppShell → PrintStatusScreen path — it is no longer the scaffold stub that blocked 04-06b Task 2. The gfxinfo two-part gate can now be measured against a live Ender 5 Plus.
- **OPEN — Task 4 is a blocking human-verify checkpoint (deferred, not signed off).** The end-of-phase manual on-device VISUAL UAT — navigation feel, drawer swipe ergonomics, first-run → Settings → connect → Print Status flow, Klippy-shutdown → splash-no-drawer, the single-root Edit-connection escape, all with a live Ender 5 Plus and human eyes — was NOT performed (it requires the tablet + printer + a human). Per the plan's `checkpoint:human-verify gate="blocking"`, this remains an OPEN human-verify item to clear via `/gsd-verify-work 4` (alongside the still-deferred 04-03 manual rotation/screen-off sign-off and the 04-06b on-device perf gate). The automated `:app:assembleDebug` + greps + on-device `ShellPresenceTest` portion of that checkpoint already PASSES.

## Self-Check: PASSED

All created files exist on disk; all three task commits (`8119092`, `d265b3c`, `3fe90c6`) are present in git history.

---
*Phase: 04-service-shell-settings-print-status-home*
*Completed: 2026-06-01*
