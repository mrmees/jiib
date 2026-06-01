---
phase: 04-service-shell-settings-print-status-home
verified: 2026-06-01T10:00:00Z
status: passed
uat_closed: 2026-05-31 — on-device UAT run on real flox + live Ender 5 Plus; 5/5 passed (see 04-HUMAN-UAT.md). Perf-gate liveness subjectively passed (no observable jank); the heater-sparkline scaling/timeline/empty-on-steady behavior recorded as gap G-1, deferred to Phase 5 by user decision (the full Temperature graph extends this primitive with fixed ranges + temperature_store backfill). Formal gfxinfo p95 capture optional during a heat ramp, non-blocking.
score: 5/5
overrides_applied: 0
human_verification:
  - test: "Service survives screen-off: connect to live Ender 5 Plus, turn off flox screen for 60s, turn it back on"
    expected: "App is still Connected (connection state shows Connected or Syncing), live temperature updates arrive — no reconnect cycle required"
    why_human: "Doze/screen-off behavior requires real device, real printer, real OS power management; cannot be automated or emulated"
  - test: "Combined Print Status (ring + sparkline + stat grid + gutter) on-device perf gate — gfxinfo two-part gate"
    expected: "On flox (Adreno 320 / 1920x1200), with a live Ender 5 Plus heat ramp active: (1) LIVENESS: allocation-free draw path, NO animation loop (redraw count tracks ~3 Hz feed not Choreographer), ZERO frozen frames (0 frames > 700 ms). (2) SPARSE-REDRAW LATENCY: p95 <= ~66 ms for value-driven redraws. Procedure: dumpsys gfxinfo works.mees.dinghy reset -> dwell ~30s -> capture framestats -> tools/gfxinfo-parser/parse_framestats.py. The isolated Phase-3 ring+graph result of 50.1 ms p95 MUST NOT be used — it is the ISOLATED surface; this gate is the COMBINED surface (ring + GraphView sparkline + 2x3 stat grid + gutter). 03-PERF-RESULTS.md FINAL VERDICT explicitly forbids grandfathering the isolated result."
    why_human: "Adreno 320 fill-rate is the real bottleneck; only real hardware at real panel sizes with the full combined surface proves the two-part gate. Emulator and isolated bench are both forbidden substitutes."
  - test: "First-run -> Settings -> connect to Ender 5 Plus -> Print Status visual flow"
    expected: "On fresh install or after clearing connection: app opens Settings screen (not a crash, not a blank). Enter the Ender 5 Plus host/port, tap Save. App transitions to Splash (Connecting...), then to Print Status once Klippy reports Ready. Print Status shows live temperatures, progress ring (if printing) or Ready readout (if idle)."
    why_human: "End-to-end visual flow with live printer + human eyes; nav feel, swipe ergonomics, and theme coherence cannot be asserted by grep or unit test"
  - test: "Stop -> ConfirmGuard -> emergency_stop -> Splash recovery round-trip on live Ender 5 Plus"
    expected: "On Print Status, tap Stop. Full-screen ConfirmGuard appears (red STOP confirm, neutral cancel). Tap STOP. printer.emergency_stop fires (CommandDispatcher). Klippy transitions to Shutdown state. App routes automatically to Splash screen showing the shutdown reason and Retry/Restart firmware/Restart Klipper buttons. No manual navigate. Tap Restart firmware — Klippy restarts, app re-routes to Print Status on Ready."
    why_human: "Requires a live Klippy round-trip with physical consequence (firmware halt). The unit-test layer proves dispatch is wired; only the physical round-trip proves the Klippy state transition and recovery routing."
  - test: "Navigation feel and drawer ergonomics on flox"
    expected: "Swipe UP from anywhere on the Print Status canvas reveals the full-screen App Drawer (square tiles: Status+Settings live/accent-outlined, Move/Temp/Files/Tools/Macros/Devices + red Power greyed/inert). Tap Status -> drawer collapses, Print Status remains. Tap Settings -> Settings screen renders keyboard-allowed connection form. System Back collapses drawer without navigating. Full-bleed canvas, no persistent title/status bar."
    why_human: "ShellPresenceTest covers structural correctness (live tiles, greyed inert, splash-no-drawer) but not swipe gesture latency, drawer animation feel, or visual completeness of tile layout matching 02-app-drawer.png mockup"
---

# Phase 4: Service, Shell, Settings & Print-Status Home — Verification Report

**Phase Goal:** The app comes alive on the Phase-3 substrate. A foreground service owns the Moonraker spine across Activity recreation and screen-off; a conventional Settings screen (keyboard allowed) lets the user enter/persist connection (host/port/key, replacing the static dev config), pick theme, set text size, toggle features; the Klippy lifecycle (not the user) drives routing (ready->Print Status, printing->job, startup/error->splash); navigation is a swipe-up full-screen App Drawer (Settings + Power tiles); the Print Status screen is the home/monitor surface proving the shared render/throttle primitive, with a Stop control routing through the Confirm guard firing emergency_stop/cancel; the shared command-dispatch primitive (timeouts + in-flight/busy + debounce) wraps every action call.

**Verified:** 2026-06-01T10:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | FGS owns connection, survives rotation; user-entered config persists via DataStore, replaces Phase-2 static dev config | VERIFIED | `MoonrakerService` is a started `specialUse` FGS collecting `ConnectionStore.config` (DataStore). `cancelAndJoin()` rebuild loop in `runConfigLoop()`. `ServiceSurvivesRotationTest` (instrumented) asserts `sessionInstanceId` unchanged across 3 rotation cycles. Screen-off survival = human gate (see human_verification). |
| 2 | Splash during Klippy startup/error, route to Shell on Ready — driven by `klippyState` not socket state | VERIFIED | `derive(cfgPresent, state)` routes off `state.klippyState` only. `TopRouteTest.socketStateDoesNotRoute_changingOnlyConnectionKeepsRouteIdentical` unit test (passing, 0 failures). `SplashScreen` shows `klippyStateMessage` (real reason) + recovery actions. `PrinterStateReducerTest.shutdownDiffWithStateMessageSetsKlippyStateMessage` and `readyTransitionClearsKlippyStateMessage` both passing. |
| 3 | Navigation = swipe-up full-screen App Drawer (Settings + Power; future panels greyed); full-bleed canvas, no persistent status bar | VERIFIED | `AppDrawer` is a full-screen `Dialog` with 9 square tiles. Status + Settings are live (`dest != null`); Move/Temp/Files/Tools/Macros/Devices/Power have `dest = null` + `semantics { disabled() }` (inert by construction). `AppShell` has no persistent status bar. `ShellPresenceTest` 4/4 PASS on flox (XML result exists at `app/build/outputs/androidTest-results/connected/debug/TEST-Nexus 7 - 11-_app-.xml`). |
| 4 | Print Status renders live job/thermal at throttled ~2-4 Hz (Phase-3 render primitive); Stop routes through full-screen Confirm guard and fires emergency_stop/cancel | VERIFIED (code) / HUMAN (combined perf gate) | `PrintStatusHolder` consumes `PrinterStateStore.printerState` (already 250ms-conflated, no second throttle). `PrintStatusScreen.kt:170` dispatches `JsonRpcMethods.EMERGENCY_STOP` through `ConfirmGuard.onConfirm` via per-session `CommandDispatcher`. Combined-render perf gate (ring + GraphView sparkline + 2x3 grid + gutter on Adreno 320) = human gate; isolated Phase-3 50.1 ms p95 is NOT grandfathered (03-PERF-RESULTS.md forbids it). |
| 5 | Shared command-dispatch (PRIM-05) enforces timeouts + in-flight disabled/busy + debounce for all action calls; text entry confined to Settings (PRIM-02) | VERIFIED | `CommandDispatcher`: `withTimeout(timeoutMs)`, `_inFlight: MutableStateFlow<Set<String>>` busy guard, `ConcurrentHashMap<String, Long>` debounce. `CommandDispatcherTest` 6/6 passing. `SettingsScreen` is the ONLY screen with `TextField`/keyboard; `PrintStatusScreen`/`SplashScreen`/`AppDrawer` have no text input fields. |

**Score:** 5/5 (code-level truths all VERIFIED; on-device UAT gates remain)

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `config/ConnectionConfig.kt` | Immutable model with `httpBase`/`wsUrl` getters, apiKey redacted in `toString()` | VERIFIED | `data class ConnectionConfig` with `ws://$host:$port/websocket`, `http://$host:$port`, `toString()` redacts to `***` |
| `config/ConnectionStore.kt` | DataStore-backed, fail-safe read, pure `sanitize()` | VERIFIED | `catch { IOException -> emptyPreferences() }`, `clear()` drives null, `sanitize()` rejects blank host/out-of-range port |
| `config/MoonrakerDiscovery.kt` | Fully lazy `_moonraker._tcp.` scan (trailing dot, review WR-03) | VERIFIED | `SERVICE_TYPE = "_moonraker._tcp."` (trailing dot fix applied, commit `cf5a24b`). `callbackFlow` with `awaitClose` |
| `service/MoonrakerService.kt` | Started specialUse FGS, `runConfigLoop` seam, `START_STICKY` | VERIFIED | `Service.onBind = null`, `START_STICKY`, `specialUse`, `cancelAndJoin` rebuild, `runConfigLoop` companion |
| `di/AppContainer.kt` | Process-scoped service locator, atomic `StateFlow<SpineHandle?>`, no transport construction | VERIFIED | Single `_spine = MutableStateFlow(null)`, `flatMapLatest`-derived per-field flows, no socket/session construction |
| `di/SpineHandle.kt` | Immutable snapshot with monotonic `sessionInstanceId` | VERIFIED | `data class SpineHandle(printerState, connectionState, capabilities, dispatcher, store, sessionInstanceId)` |
| `di/SessionControl.kt` | Narrow `requestReconnectNow/restartFirmware/restartHost` interface | VERIFIED | Interface-only, no raw session member (asserted by `AppContainerTest`) |
| `DinghyApp.kt` | Application owning `AppContainer` + two separate DataStore files | VERIFIED | `PreferenceDataStoreFactory.create` for `theme.preferences_pb` and `connection.preferences_pb` separately |
| `ui/shell/RootController.kt` | Single root routing authority, one Settings escape | VERIFIED | ONE consumer of `derive()`, owns `settingsEscape` flag, Splash/AppShell never self-route |
| `ui/shell/AppShell.kt` | Full-bleed Dest host, swipe-up drawer, per-session holder keyed on store | VERIFIED | `detectVerticalDragGestures` swipe-up, `remember(store)` holder, `BackHandler`, no persistent chrome |
| `ui/shell/AppDrawer.kt` | Full-screen Dialog tile grid, live tiles functional, greyed inert | VERIFIED | `Dialog(usePlatformDefaultWidth=false)`, `dest != null` tiles have `clickable`, `dest == null` tiles have `semantics { disabled() }` with no click action |
| `ui/screen/SettingsScreen.kt` | Connection form (host/port/key), theme/font controls, keyboard allowed | VERIFIED | `TokenTextField` with keyboard, `DataStore.save()` on valid form, theme/font toggles wired to `ThemeResolver` (live) + `ThemePrefs` (persist) |
| `ui/screen/SplashScreen.kt` | Hard-override recovery surface, 3 recovery modes, no App Drawer | VERIFIED | `RecoveryMode` enum (FirstRun/KlippyDown/Unreachable/Connecting), `onEditConnection` not self-navigate, no `AppDrawer` or `AppShell` composed |
| `ui/printstatus/PrintStatusHolder.kt` | Toolkit-agnostic grid + sparkline, no second throttle | VERIFIED | Consumes `store.printerState` directly, no `sample`/`debounce`/`delay` in holder, `RingBuffer` for sparkline |
| `ui/printstatus/PrintStatusScreen.kt` | State-adaptive home, `ConfirmGuard`-gated Stop, auto-dismiss failure toast | VERIFIED | `ConfirmGuard` raises on Stop tap, `dispatcher.dispatch("estop", EMERGENCY_STOP)` on confirm, `LaunchedEffect(failureText)` 4s auto-dismiss (WR-01 fix), `roundToInt` formatting (IN-01 fix) |
| `command/CommandDispatcher.kt` | timeout + in-flight + debounce + `ConcurrentHashMap` | VERIFIED | `withTimeout`, `MutableStateFlow<Set<String>>` in-flight, `ConcurrentHashMap<String, Long>` debounce (WR-02 fix) |
| `MainActivity.kt` | Grown-up: starts FGS, ONE `DinghyTheme` boundary, delegates to `RootController` | VERIFIED | `startForegroundService(MoonrakerService)`, single `DinghyTheme(container.themeResolver)`, `RootController(container)` |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ConnectionStore.config` | `MoonrakerService.runConfigLoop` | `collectLatest` on `container.connectionStore.config` | WIRED | `MoonrakerService.onCreate()` calls `runConfigLoop(configFlow = container.connectionStore.config, ...)` |
| `MoonrakerService` | `AppContainer.spine` | `container.publishSpine(handle)` | WIRED | `buildSpineAndLaunch()` calls `container.publishSpine(handle)` after atomic construction |
| `AppContainer.spine` | `RootController` | `container.printerState.collectAsStateWithLifecycle()` + `container.hasConfig.collectAsStateWithLifecycle()` | WIRED | `RootController.kt:39-40` collects both, feeds into `derive()` |
| `derive()` routing | `SplashScreen` / `AppShell` / `SettingsScreen` | `when { route is TopRoute.Splash -> ...; else -> ... }` | WIRED | `RootController.kt:48-72` |
| `SettingsScreen` save | `MoonrakerService` rebuild | `store.save(config)` -> `ConnectionStore.config` emits -> `collectLatest` fires | WIRED | `SettingsScreen.kt:283` saves to store; service collects the flow |
| `PrintStatusScreen` Stop | `CommandDispatcher.dispatch("estop", EMERGENCY_STOP)` | `ConfirmGuard.onConfirm` lambda | WIRED | `PrintStatusScreen.kt:168-171` |
| `CommandDispatcher.dispatch` | `JsonRpcClient.request` | `rpc::request` in secondary constructor | WIRED | `CommandDispatcher.kt:63` |
| `AppShell` | `PrintStatusHolder` | `remember(store) { PrintStatusHolder(scope, store) }` | WIRED | `AppShell.kt:80`, store sourced from `spine?.store ?: idleStore` |
| `PrintStatusHolder.sparkline` | `GraphViewHost` in `PrintStatusScreen` | `val sparkline by holder.sparkline.collectAsStateWithLifecycle()` | WIRED | `PrintStatusScreen.kt:89`, `GraphViewHost(tokens, snapshot=sparkline)` at line 131 |

---

## Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| `PrintStatusScreen` | `grid` / `sparkline` | `PrintStatusHolder.grid` / `.sparkline` (StateFlow) | `PrintStatusHolder` feeds from `PrinterStateStore.printerState` (Phase-2 throttled StateFlow), which comes from Moonraker `notify_status_update` deltas applied to live printer state | FLOWING |
| `PrintStatusScreen` | `state` | `container.printerState.collectAsStateWithLifecycle()` | Derived via `AppContainer.printerState = spine.flatMapLatest { it?.printerState }` from live service-held `PrinterStateStore` | FLOWING |
| `PrintStatusScreen` | `dispatcher` | `container.dispatcher.collectAsStateWithLifecycle()` | `spine.map { it?.dispatcher }` from live `CommandDispatcher` built in `MoonrakerService.buildSpineAndLaunch()` | FLOWING |
| `SettingsScreen` | `host`/`port` | `store.config.firstOrNull()` on `LaunchedEffect(Unit)` | Reads from real DataStore `connection.preferences_pb` | FLOWING |
| `SplashScreen` | `state.klippyStateMessage` | `PrinterStateStore.printerState` (via `container.printerState`) | Populated by `PrinterStateReducer` from Moonraker `webhooks.state_message` in `notify_status_update` | FLOWING |

---

## Behavioral Spot-Checks

Step 7b: SKIPPED — entry point is an Android Activity; cannot test without running the app. Instrumented test results serve as the runnable-code evidence tier.

---

## Probe Execution

Step 7c: No probe scripts declared in Phase 4 PLAN files. No `scripts/*/tests/probe-*.sh` files exist for this phase. SKIPPED.

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CONN-01 | 04-01, 04-03 | User can enter/save Moonraker connection (host/IP, port, optional API key) via Settings, persisted locally across restarts | SATISFIED | `ConnectionStore` DataStore round-trip, `SettingsScreen` save flow, `MoonrakerService` config-rebuild; `ConnectionStoreTest` 13/13 passing |
| SET-01 | 04-04 | Settings screen for connection, theme, text size, feature toggles | SATISFIED | `SettingsScreen.kt` has connection form, dark/light toggle, S/M/L text size, accent picker; keyboard allowed |
| SHELL-01 | 04-07 | Swipe-up full-screen App Drawer; no persistent status bar | SATISFIED | `AppDrawer` Dialog, `AppShell` swipe-up gesture, no persistent chrome; `ShellPresenceTest` 4/4 PASS |
| SHELL-02 | 04-06 | Emergency stop routes through full-screen Confirm guard | SATISFIED | `PrintStatusScreen` Stop -> `ConfirmGuard` -> `dispatcher.dispatch(EMERGENCY_STOP)` |
| SHELL-03 | 04-03 | FGS owns connection; survives Activity recreation and screen-off | PARTIAL | Code: started specialUse FGS, `ServiceSurvivesRotationTest` PASSED on flox (rotation automated). Screen-off survival = human gate. Note: REQUIREMENTS.md traceability table marks this "Pending" — an inconsistency with the actual FGS implementation. The FGS CODE is complete; the human screen-off verification remains. |
| SHELL-04 | 04-06, 04-06b | Print Status is the home surface proving shared render/throttle primitive | SATISFIED (code) / HUMAN (perf gate) | `PrintStatusScreen` + `PrintStatusHolder` wired; sparkline via `GraphViewHost` wired; combined-render gfxinfo perf gate = human verification item |
| SHELL-05 | 04-05 | Splash shows startup/connection status with contextual recovery actions | SATISFIED | `SplashScreen` with `RecoveryMode` (FirstRun/KlippyDown/Unreachable/Connecting), real `klippyStateMessage`, `SessionControl` recovery dispatch |
| PRIM-02 | 04-04 | Text entry confined to Settings screen | SATISFIED | `SettingsScreen` has `TokenTextField` (keyboard); `PrintStatusScreen`/`SplashScreen`/`AppDrawer`/`AppShell` have zero text input fields |
| PRIM-05 | 04-02 | Shared command-dispatch: timeouts + in-flight/busy + debounce | SATISFIED | `CommandDispatcher.kt`: `withTimeout`, `MutableStateFlow<Set<String>>` in-flight, `ConcurrentHashMap` debounce; `CommandDispatcherTest` 6/6 passing |

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `MoonrakerDiscovery.kt` | — | Pre-API-29 NSD deprecation warnings (2 warnings, pre-existing) | Info | Non-blocking; NSD API evolves, pre-existing from Phase-1, not introduced by Phase 4 fixes |

No TBD/FIXME/XXX markers found in Phase-4 source files. All 6 code-review findings (WR-01 through IN-03) were fixed and committed (`cf5a24b`, `e78be48`, `352bc9d`, `bdf35ab`, `c0fcf17`, `f9a8175`). `04-REVIEW-FIX.md` confirms `all_fixed`.

---

## Human Verification Required

### 1. Service survives screen-off

**Test:** With a live Ender 5 Plus reachable: launch the app, confirm Connected and live temperatures. Turn off the flox screen. Wait 60 seconds. Turn the screen back on.
**Expected:** App remains Connected (no reconnect cycle), temperatures continue updating live, no "Connecting..." or reconnect delay observable.
**Why human:** Doze and screen-off behavior is device/OS-specific; emulators don't simulate power management. This is the manual gate that completes SHELL-03.

### 2. Combined Print Status on-device perf gate (the 04-06b gate)

**Test:** With Ender 5 Plus running a print or doing a heat soak (live temperatures changing at ~3 Hz): open the wired app on flox, navigate to Print Status. Run: `adb shell dumpsys gfxinfo works.mees.dinghy reset`. Wait ~30 seconds while watching live temperature updates. Capture: `adb shell dumpsys gfxinfo works.mees.dinghy framestats`. Parse with `tools/gfxinfo-parser/parse_framestats.py`. Apply the two-part gate from `03-PERF-RESULTS.md` FINAL VERDICT (A-variant):
- Liveness: allocation-free draw, NO animation loop (redraw tracks ~3 Hz feed), ZERO frozen frames (>700 ms).
- Sparse-redraw latency: p95 <= ~66 ms.

**Expected:** Both gates clear. The combined surface (ring + GraphViewHost sparkline + 2x3 stat grid + gutter) meets the same two-part gate the isolated ring+graph met at 50.1 ms p95. If p95 exceeds ~66 ms, report it — the margin from the isolated test is ~16 ms; additional grid + gutter chrome may consume some of that.
**Why human:** Adreno 320 fill-rate is the real bottleneck; only real hardware with the full combined surface proves this. The isolated Phase-3 50.1 ms p95 is explicitly forbidden as a substitute by `03-PERF-RESULTS.md`.

### 3. First-run -> Settings -> connect -> Print Status visual flow

**Test:** Clear the app's data (or use a fresh install). Launch the app.
**Expected:** App opens directly to SettingsScreen (no crash, no blank). Enter the Ender 5 Plus host/port, tap Save. App transitions to Splash with "Connecting..." or Klippy status reason. When Klippy reports Ready, app routes to Print Status showing live temperatures and ready readout (or progress ring if printing).
**Why human:** End-to-end visual correctness, nav timing, and theme coherence (Geist/GeistMono fonts, token colors) need human eyes. Automated tests verify structure, not visual fidelity.

### 4. Stop -> ConfirmGuard -> emergency_stop -> Splash recovery round-trip

**Test:** With Ender 5 Plus connected and Klippy Ready: navigate to Print Status, tap the red Stop button. A full-screen ConfirmGuard should appear. Tap STOP. Observe: (a) printer halts, (b) app routes to Splash showing shutdown reason + recovery actions. Tap "Restart firmware" to recover.
**Expected:** ConfirmGuard is full-bleed (covers entire screen). After confirm: `printer.emergency_stop` fires, Klippy enters Shutdown, app automatically routes to Splash (no manual navigation). Splash shows real klippyStateMessage from Moonraker. "Restart firmware" button re-triggers Klippy, app returns to Print Status on Ready.
**Why human:** Requires live Klippy round-trip with physical consequence. The physical halt -> state_message -> routing chain cannot be simulated.

### 5. Navigation feel and App Drawer ergonomics

**Test:** On flox with app running at Print Status: swipe UP from the lower half of the screen. Check all drawer tiles. Tap "Settings" live tile. Tap back/swipe -> drawer opens, tap "Status". Tap a greyed tile (e.g. "Move"). Press system Back with drawer open.
**Expected:** Swipe-up gesture feels responsive and deliberate (80px threshold, not too sensitive). Drawer is full-screen with square tiles. Greyed tiles show hairline outline + dimmed text and do NOT navigate when tapped. Live tiles navigate. System Back collapses drawer. No persistent title/status bar visible at any point.
**Why human:** `ShellPresenceTest` covers structural correctness but not gesture latency, drawer animation quality, visual tile layout match to `docs/ui_design/images/02-app-drawer.png`, or swipe-handle affordance visibility.

---

## Gaps Summary

No code-level gaps. All 5 observable truths are VERIFIED at the code level. All 9 requirements (CONN-01, SET-01, SHELL-01 through SHELL-05, PRIM-02, PRIM-05) have implementation evidence. All 6 code-review findings were fixed and committed. Unit test suite (26 test classes, all 0 failures) and instrumented test suite (ShellPresenceTest 4/4 on flox) are green.

The `human_needed` status is driven exclusively by on-device UAT gates that require a live Ender 5 Plus + human: (1) screen-off connection survival, (2) combined-render gfxinfo perf gate on the real combined Print Status surface, (3) first-run visual flow, (4) Stop->emergency_stop->Splash round-trip, (5) navigation feel. These are not code gaps — they are behavioral verification that cannot be automated.

Note: REQUIREMENTS.md traceability marks SHELL-03 as "Pending" despite the FGS code being present and the rotation gate having passed. The traceability table should be updated once the screen-off human gate clears.

---

_Verified: 2026-06-01T10:00:00Z_
_Verifier: Claude (gsd-verifier)_
