# Roadmap: Dinghy Display

## Overview

Dinghy Display turns a retired Nexus 7-class Android tablet into a self-contained Klipper
touchscreen that talks straight to Moonraker — no Linux host, no VNC, no display-server glue.
The build is infrastructure-first by deliberate choice, and the riskiest decisions are isolated
into their own gates. It opens with a **Platform Gate**: before any architecture is committed,
an on-device Compose-vs-Views benchmark on the Nexus 7 (against a synthetic 2–4 Hz source),
a minimal cleartext smoke test proving `ws://`/`http://` reaches Moonraker from a real API-23
device, the pinned version catalog, and the build scaffold. Only once the toolkit and the
cleartext path are proven does the load-bearing **Connection & State Foundation** get built:
the resilient websocket, the diff-merged single-source-of-truth `PrinterState`, capability
gating, the reconnect-with-resync handshake, optional auth, and JSON-RPC `id` correlation —
proven against a mock socket and a real Ender 5 Plus, still with no panels. On top of that spine we
first build the **Design System & Theming Foundation** — the reusable UI substrate from the canonical
`docs/ui_design/` system (semantic-token theming dark/light/custom + S/M/L text size, the
Focus/Field/Gutter responsive grammar for portrait *and* landscape, the outline-led control language,
and the core components: Confirm guard, single-setting scrubber page, severity toast, and the
progress-ring + line-graph render primitives). Then the app comes alive: the foreground service, the
Settings screen (connection config + theme + feature toggles), klippy-state-driven routing, the swipe-up
App Drawer, the Print Status home, and the shared command-dispatch primitive. (Scope note, 2026-05-31:
v1 broadened to phones→tablets, portrait + landscape, full theming; the Nexus 7 / Adreno 320 is retained
as the perf FLOOR, not the only target.) Then, with manual control done (Temperature/Move/Extrude), the
**Command Reference & Capability Matrix** is knocked out FIRST of the remaining work (canonical in-code
command registry + a live per-printer availability matrix) so everything after it registers commands
canonically and capability-gates cleanly. Next, **Files & Print Control** — the *core print-loop gate* of
"drive a real print start-to-finish without the browser" (the old "Job Status" phase dissolved here: its
live-monitoring half was already delivered by the Status home, its print controls fold in, and its deep
robustness defers to release hardening) — then **Macros/Console** close out the *functional-core* gate.
Then the daily-driver power features, each capability-gated off the matrix: **Calibration & Maintenance**
(screws-tilt / Z-tilt / bed-mesh / QGL pages), **Webcam Streaming** (MJPEG), **Spool Management** (Spoolman

+ a tablet-camera QR-scan-to-assign flow), and **Macro Prompt Protocol** (interactive dialogs from user

macros). Only once EVERY screen exists does **Optimization, Network Efficiency & End-to-End Reliability**
do the driven backend pass — a request-cadence audit so the app stops spamming the LAN, plus reliability
hardening — work that would have been speculative before the screens defined what's actually needed. The
journey ends with **Release Hardening & Ship**: the deferred print-loop robustness (reconnect resync,
process-death recovery) plus Doze survival, burn-in protection, and a signed sideloadable release APK that
ships the whole project at once. (Roadmap expanded 2026-06-01 from 9 to **14 phases, single milestone** —
see the dated note below.)

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Platform Gate — Toolkit Benchmark, Cleartext Smoke Test & Scaffold** - The Compose-vs-Views on-device go/no-go benchmark (synthetic 2–4 Hz source), a cleartext `ws://`/`http://` smoke test on a real API-23 device, the pinned `libs.versions.toml` catalog, and the build scaffold — the gate that must close before panel architecture begins (completed 2026-05-30)
- [x] **Phase 2: Connection & State Foundation** - Resilient Moonraker websocket, diff-merged single-source-of-truth `PrinterState`, capability detection, reconnect-with-resync handshake, optional auth, and JSON-RPC id correlation — proven against a mock socket and a real Ender 5 Plus via a static dev config, still no panels (completed 2026-05-30)
- [x] **Phase 3: Design System & Theming Foundation** - The reusable UI substrate per `docs/ui_design/`: semantic-token theming (dark/light/custom) + S/M/L text size, the Focus/Field/Gutter responsive grammar (portrait + landscape), the outline-led control language, and the core components (Confirm guard, single-setting scrubber page, severity toast, progress-ring + line-graph render primitives) every later screen inherits (completed 2026-05-31)
- [x] **Phase 4: Service, Shell, Settings & Print-Status Home** - Foreground service owning the spine, the Settings screen (connection config + theme + text size + feature toggles), klippy-state-driven splash/home/job routing, swipe-up App Drawer navigation, the Print Status home (render/throttle in anger) with a Stop→Confirm-guard control, and the shared command-dispatch primitive (completed 2026-06-01)
- [x] **Phase 5: Core Print-Control Panels — Temperature, Move, Extrude** - Manual printer control with capability gating, the confirm policy, the shared command-dispatch primitive, and the full temperature graph (extending the Phase-3 line-graph primitive) — preheat, jog, and extrude on real hardware (completed 2026-06-01)
- [x] **Phase 6: Command Reference & Capability Matrix** - Done FIRST of the remaining work (moved to top 2026-06-01) because it gates everything after it. Three layers: a **command/function catalog read from the authoritative Klipper G-Code/config, Moonraker API (JSON-RPC + REST), and Spoolman docs** (the universe + what each does + error/acceptance semantics); a canonical single-source-of-truth **in-code command registry** for the commands the app actually sends; and a committed per-printer (Ender 5 Plus / Ender 3) **availability matrix** cross-referencing the catalog against live introspection — so later pages can capability-gate. Matrix knocked out early (completed 2026-06-04)
- [x] **Phase 7: Files & Print Control — Core Print-Loop Gate** - Browse gcode files/folders with off-thread decoded thumbnails, start a print from a confirm guard, delete files, AND wire the state-adaptive print-control actions (pause/resume/cancel/restart) onto the existing Print Status home — completing the core print loop as a user-drivable capability: drive a real print start-to-finish without the browser (absorbs the print-control half of the old "Job Status" phase, whose live-monitoring half was already delivered by the Phase-4 Status home + Status quick-task enrichment) (completed 2026-06-04)
- [x] **Phase 8: Macros & Console — Functional-Core Complete** - Run gcode_macros with parameter entry and send/inspect raw G-code with severity-colored history (pragmatic backend — raw response display + basic severity color), closing the functional-core-complete gate (completed 2026-06-02)
- [x] **Phase 9: Calibration & Maintenance** - Touch pages for the high-use, semi-regular calibration routines (`SCREWS_TILT_CALCULATE`, `Z_TILT_ADJUST`, `BED_MESH_CALIBRATE`, `QUAD_GANTRY_LEVEL` where present) — each capability-gated by the Phase-6 matrix, running the gcode and parsing/displaying its result (screw turns, mesh, tilt) without a browser (completed 2026-06-03; on-device UAT passed on flox + live Ender 3. One cross-cutting reliability defect — SAVE_CONFIG re-handshake live-feed freeze — deferred to Phase 13, now promoted to run next)
- [x] **Phase 10: Webcam Streaming** - View the printer's webcam(s) on-device — decode the MJPEG stream (Moonraker `/server/webcams/list`), hard-downscaled for the Adreno-320 fill-rate floor; WebRTC deferred (completed 2026-06-04)
- [x] **Phase 11: Spool Management — Spoolman + Camera QR** - Spoolman integration (list/select the active spool, filament remaining/usage) plus the headline feature: a tablet-camera **QR-scan-to-assign** flow (ZXing, GMS-free for the Nexus 7) reading Spoolman's `web+spoolman:s-<id>` labels — load a spool, scan it, done; no ESP32/NFC rig (completed 2026-06-04)
- [x] **Phase 12: Macro Prompt Protocol** - Render interactive dialogs from user macros that emit `// action:prompt_*` lines in the gcode-response stream (per the klipper-macro-prompt-protocol), reusing the Console stream + dialog primitive (completed 2026-06-04)
- [x] **Phase 13: Optimization, Network Efficiency & End-to-End Reliability** — **⏩ PROMOTED 2026-06-03: executes NEXT, immediately after Phase 9 (before Phases 10–12).** Phase 9 UAT surfaced a core-loop reliability bug (the `SAVE_CONFIG` re-handshake freezes the live feed until app restart — `05-10` G2 fix not holding on the E3); owner pulled this phase forward to standardize the connection/data models and fix the session-layer reliability class before stacking three more feature phases on it. Phase number unchanged (stable references); only execution order moved. — Now that EVERY screen exists, the driven backend pass: a request-cadence audit (one-shot vs subscribe per object, coalesce/throttle to display cadence, no per-screen polling outside the central single-subscribe handshake) so the app stops spamming the wireless LAN, plus end-to-end reliability hardening. A refactor/quality phase, not a new-screen phase (all 5 plans complete + on-device UAT PASSED 2026-06-03 — awaiting phase verification) (completed 2026-06-04)
- [x] **Phase 14: Multi-Printer Switching** - Managed printer profiles (name + host/port/key, DataStore-persisted) with a clean service spine rebind on switch, so the E5 Plus and E3 Pro are both first-class — foundational, so later new per-printer surfaces are built multi-printer-aware (completed 2026-06-05)
- [x] **Phase 15: Theme System & Settings Redesign** - Establish the full semantic-token theme system (dark/light/user-custom + S/M/L) as the visual foundation and rebuild Settings to host it (merges the former Settings-Redesign + Final-Conformance phases); pulled to the front because the parallel theme work needs the new Settings UI to test against; also conformance-sweeps existing surfaces (completed 2026-06-05). **Scoped engine-first; the semantic/shape + conformance halves split to inserted Phases 15.1 + 15.2 below.**
- [x] **Phase 15.1: Shape-Coded Status & Semantic Color** *(INSERTED 2026-06-05 — split from Phase 15)* - The deferred semantic + safety half of the theme work, built before Home so every later surface inherits it: shape-coded status (octagon=stop / triangle=caution / circle=go) + icon + position across every status signal so color is never the only safety cue; status-from-pool recolor + High-Contrast stoplight status + status-slot editability (Phase-15 D-09 open); app-wide pool→role semantic assignment; color-doctrine D-5 (back-button) / D-6 (bed-mesh perceptually-uniform OKLCH ramp) / D-8 (force-move); Move homed/unhomed status-color + force-move lock-shape; reconcile docs/ui_design/THEMING.md against ../theme_theory/COLOR-SYSTEM.md. Source: 15-CONTEXT.md ⟨deferred⟩ + COLOR-SYSTEM.md §7/§D (completed 2026-06-05)
- [ ] **Phase 15.2: Theme Conformance Sweep & Settings IA** *(INSERTED 2026-06-05 — split from Phase 15)* - Heavy audit of every existing surface against the now-reconciled theme LAW: token purity (role tokens, no raw colors), button-intent color, Focus/Field/Gutter grammar, ≥64px touch targets, the fsSp font scale, dark/light/custom correctness; plus resolve the Settings-vs-Devices boundary (Connection redundant vs the Devices screen — "printer settings page" reframe vs per-printer Devices editor). The light final re-sweep of the late surfaces (16–20) still folds into Ship. Tracked todos: 2026-06-05-settings-vs-devices-boundary + 2026-06-05-pool-color-semantic-assignment
- [ ] **Phase 16: Home / Print-Status Redesign** - Rework the home/status surface into its definitive form as the visual FOUNDATION for the remaining features (built before them to avoid later rework), leaving forward greyed/capability-gated entry points; no regression to the render/throttle primitives. Includes the conditional first-~10-layers **Z-babystep** control (moved from Fine-Tune)
- [ ] **Phase 17: Fine-Tune / Live-Adjust Panel** - A lean live-adjust tuner organized by failure-mode — **Motion** (speed M220, accel/max-velocity/SCV) and **Extrusion** (flow M221, pressure advance, firmware retraction if present, part-cooling fan) — wired from the stubbed Print-Status Tune button; capability-gated, keyboard-free, always-available; temps/pause linked not duplicated. (Z babystep moved to Phase 16; object exclusion → v2.) See 17-CONTEXT.md.
- [ ] **Phase 18: Output Controls — Fans, Lights & Generic Pins** - A dedicated page for `[fan_generic]`, `[output_pin]`, and `[led]`/`[neopixel]` outputs the active printer exposes — capability-gated, set via the shared command primitive
- [ ] **Phase 19: System Information Page** - Read-only host + Klipper/Moonraker health view (CPU/mem/temp/throttle/uptime/versions/disk) from `machine.system_info`/`proc_stats`/`server.info` via the central subscribe
- [ ] **Phase 20: WebRTC Camera Streaming** - Real camera for the project's own WebRTC-only printers (go2rtc/camera-streamer via WHEP), extending the Phase-10 webcam rung-ladder; perf-gated to the Adreno-320 floor
- [ ] **Phase 21: Release Hardening & Ship — Always-On, Lifecycle & Signed APK** - The deferred print-loop robustness (reconnect print-state resync + process-death recovery) PLUS full Doze/always-on survival, burn-in screensaver, a LIGHT final conformance sweep of the late surfaces, the "looks done but isn't" checklist, R8 release build, and a signed sideloadable APK shipped via GitHub Releases on a real Nexus 7 — ships the whole project at once

## Phase Details

### Phase 1: Platform Gate — Toolkit Benchmark, Cleartext Smoke Test & Scaffold

**Goal**: Settle the two most consequential go/no-go questions and lay the build foundation before any architecture is committed. The Compose-vs-Views toolkit decision is resolved by an on-device Nexus 7 benchmark; the cleartext-on-API-23 path is proven by a minimal smoke test; the version catalog and project scaffold are pinned. No resilient state machine, no connection layer, no panels — just prove toolkit + build + cleartext. This gate must close before the panel/state architecture begins.
**Depends on**: Nothing (first phase)
**Requirements**: PKG-02, CONN-05
**Success Criteria** (what must be TRUE):

  1. **Toolkit go/no-go is recorded:** an on-device release-mode benchmark on the Nexus 7 (scroll a list + render a 2–4 Hz live value from a SYNTHETIC source, profiled with `gfxinfo`) produces a documented Compose-everywhere vs. hybrid-Views decision that gates all panel architecture — no full connection layer required, just the synthetic feed
  2. **Cleartext smoke test passes on real hardware:** a minimal build with `res/xml/network_security_config.xml` reaches a plaintext `ws://`/`http://` Moonraker on the LAN from a real Android 6 (API 23) device at the shipping `targetSdk` — proving the OkHttp/Marshmallow cleartext path works on the only hardware that matters, not just a modern phone
  3. A pinned `gradle/libs.versions.toml` version catalog governs every dependency (minSdk 23 floor protected; pinned to the Compose 1.11 / AGP 8.7.x line) and the project/build scaffold compiles and installs

**Plans**: 4 plans
Plans:

- [x] 01-01-PLAN.md — Pinned version catalog + multi-module scaffold (:app + :macrobenchmark), shared manifest/NSC cleartext posture, armeabi-v7a release APK, and the verifyMinSdk merged-manifest gate (PKG-02)
- [x] 01-02-PLAN.md — Throwaway 3-step cleartext ws://+http:// Moonraker smoke test on the real Nexus 7 (CONN-05; device checkpoint)
- [x] 01-03-PLAN.md — Head-to-head benchmark harness: deterministic in-process feed, Compose + Views scenes, UiAutomator driver, gfxinfo framestats parser
- [x] 01-04-PLAN.md — On-device benchmark run + gfxinfo capture (device checkpoint) and the recorded Compose-vs-Views ADR with raw captures

**UI hint**: yes
**Research note**: STANDARD — the on-device benchmark and cleartext smoke test are implementation/measurement work, not research. Stack versions verified against AndroidX/Compose docs (minSdk 23 baseline, Compose 1.11 / AGP 8.7.x line).

### Phase 2: Connection & State Foundation

**Goal**: A resilient, fully testable Moonraker connection/state spine — one OkHttp websocket feeding a diff-merged single-source-of-truth `PrinterState` with capability detection, reconnect-with-resync, optional auth, and correct JSON-RPC `id` correlation — proven on a mock socket and a real Ender 5 Plus via a static/dev config. The user-facing config screen is deliberately NOT here (it lives in the shell); this phase connects with a hardcoded dev config so the spine can be exercised in isolation. No panels; this is the load-bearing foundation everything else codes against.
**Depends on**: Phase 1
**Requirements**: CONN-02, CONN-03, CONN-04, CONN-06, STATE-01, STATE-02, STATE-03, STATE-04, STATE-05
**Success Criteria** (what must be TRUE):

  1. Connecting via a static/dev config, `ws://192.168.x.x` maintains a long-lived websocket and receives live `notify_status_update`; yank Wi-Fi mid-stream and the client auto-reconnects with backoff+jitter and runs the full `identify → objects.query → objects.subscribe` resync handshake so displayed state is correct (not stale) after recovery — provable in mock-socket tests replaying golden Ender 5 Plus frames, and connection state (connecting/connected/disconnected/error) is always observable
  2. `notify_status_update` partial diffs merge into one retained `PrinterState` `StateFlow` that survives transient drops, with high-rate streams conflated to ~2–4 Hz at the state layer
  3. Capability detection turns a `printer.objects.list` response into an immutable `Capabilities` model (heaters, extruder count, macros, power, etc.) as a pure, unit-tested function, re-run on every reconnect; Klippy lifecycle state (ready/printing/startup/error/shutdown) is exposed as a first-class field that will drive routing
  4. **JSON-RPC `id` correlation is correct under interleaving:** a command's response is matched to its request `id` even when `notify_*` events interleave between request and reply — the client never assumes the next message is its answer — provable in mock-socket tests that inject notifications mid-flight
  5. **Optional auth works end-to-end:** the websocket performs the oneshot-token flow and REST calls send `X-Api-Key` when a key is configured, and a `401 auth required` is surfaced as a graceful "auth required" state rather than a crash

**Plans**: 4 plansPlans:
**Wave 1**

- [x] 02-01-PLAN.md — Wave-0 foundation: test source set + `kotlinx-coroutines-test`, BuildConfig/DevConfig static config (D-05), public contracts (JSON-RPC envelopes, PrinterState, Capabilities), golden+adversarial fixtures + FakeWebSocket harness

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 02-02-PLAN.md — Pure state layer: PrinterStateReducer diff-merge (STATE-01), deriveCapabilities (STATE-02), Klippy lifecycle (STATE-04) — unit-tested over golden frames
- [x] 02-03-PLAN.md — Transport + protocol: MoonrakerSocket callbackFlow bridge + JsonRpcClient id-correlation under interleaving (STATE-05)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 02-04-PLAN.md — Session integration: reconnect supervisor (D-01/D-02), resync handshake (CONN-04), ConnectionState (CONN-06), auth (CONN-02, mock), conflation (STATE-03) + on-device Ender 5 Plus live/reconnect proof

**Research note**: Warrants DEEPER phase-specific research before planning — pin down the exact Moonraker `notify_*` set the app subscribes to (`notify_status_update`, `notify_gcode_response`, `notify_klippy_ready/disconnected/shutdown`, `notify_proc_stat_update`) and the auth handshake edge cases (oneshot-token websocket flow, `X-Api-Key`, graceful `401 auth required`). All other patterns verified against official Moonraker docs.

### Phase 3: Design System & Theming Foundation

**Goal**: Build the reusable visual + interaction substrate every later screen inherits, per the canonical `docs/ui_design/` system — so panels are assembled from a settled vocabulary, not redesigned ad hoc. Establishes the semantic-token theme system (dark + light + user-custom) and the S/M/L text-size setting; the Focus / Field / Gutter responsive layout grammar on one shared tabular grid for BOTH portrait and landscape (sacred aspect ratios, ratio-only sizing); the outline-led, touch-first control language with button-intent colors; and the core reusable components — the full-screen Confirm guard, the single-setting scrubber/stepper page (the keyboard-free numeric primitive), the severity toast, and the progress-ring + line-graph render primitives (the shared render/throttle surface the Print Status home and the Temperature graph both consume). Geist/Geist Mono bundled. Previewable via a harness; wired to the Phase-2 spine where it shows live data. The Nexus 7 (Adreno 320) is the perf floor: static glow allowed, continuous "breathing"/looping animation is not.
**Depends on**: Phase 2
**Requirements**: THEME-01, THEME-02, UI-01, UI-02, PRIM-01, PRIM-03, PRIM-04
**Success Criteria** (what must be TRUE):

  1. A semantic-token theme system is in place: dark + light + a user-custom override, every component referencing role tokens (never raw color) so a theme is a token remap; an S/M/L text-size setting scales type app-wide and persists
  2. The Focus / Field / Gutter grammar renders correctly in BOTH portrait (stacked) and landscape (Focus|Field 50/50 + full-width gutter on the same column lines), with square content rendering square (sacred aspect ratios) and every dimension a ratio (no hardcoded px) — provable against the `docs/ui_design` reference screens
  3. The outline-led control language + button-intent colors (red/green/amber/accent/white) are implemented as reusable components with ≥64px touch targets
  4. The reusable primitives exist and are panel-consumable: the full-screen Confirm guard (PRIM-03; destructive=red / positive=green / safe-dismiss=neutral), the single-setting scrubber/stepper page (PRIM-01; keyboard-free numeric entry), and the severity toast (PRIM-04; color+icon+text)
  5. The progress-ring + line-graph render primitives draw a bounded ring buffer at the throttled ~2–4 Hz cadence WITHOUT jank on the Nexus 7 (static glow only, no continuous animation) — the SHARED surface the Print Status home (Phase 4) uses and the Temperature graph (Phase 5) extends; graph architecture is settled here

**Plans**: 8 plans
Plans:

**Wave 1**

- [x] 03-01-PLAN.md — Token core: baked oklch→sRGB table + ThemeTokens + ThemeResolver StateFlow + DataStore ThemePrefs + Wave-0 unit tests (THEME-01/02)
- [x] 03-02-PLAN.md — Geist/Geist Mono static fonts + the toolkit-agnostic bounded RingBuffer holder (THEME-02/D-12)

**Wave 2** *(blocked on Wave 1)*

- [x] 03-03-PLAN.md — Compose token boundary (LocalTokens/DinghyTheme, fontScale=1f) + ScreenScaffold (Focus/Field/Gutter) + OutlinedControl (UI-01/UI-02)

**Wave 3** *(blocked on Wave 2)*

- [x] 03-04-PLAN.md — Primitives: ConfirmGuard (PRIM-03) + ScrubberPage (PRIM-01) + SeverityToast (PRIM-04) + ThemeableView interface (D-06)
- [x] 03-05-PLAN.md — Render primitives: ProgressRing (Compose Canvas) + GraphView (Views Canvas) + AndroidView host (D-11/D-06/D-13)

**Wave 4** *(blocked on Wave 3)*

- [x] 03-06-PLAN.md — In-APK component gallery + debug-only launcher + live-spine wire (D-07/D-08/D-14)

**Wave 5** *(blocked on Wave 4)*

- [x] 03-07-PLAN.md — gfxinfo perf proof: ring+graph render scene + on-device flox capture (criterion #5/D-10; device checkpoint)

**Gap closure** *(UAT G-1..G-4)*

- [x] 03-08-PLAN.md — Fix 4 Phase-3 UAT gaps: G-3 ScrubberPage tap-to-set, G-4 ConfirmGuard opaque scrim, G-2 scrubber width, G-1 gallery token-bg root (gap_closure)

**UI hint**: yes — governed by `docs/ui_design/` (LAW); this phase IS the design-system build, so the UI contract is that bundle, not a generated UI-SPEC
**Research note**: STANDARD — Compose theming/tokens, responsive layout, custom-`Canvas` drawing, and DataStore-backed settings are well-documented; the design contract (`docs/ui_design/hifi.css` + LAYOUT/THEMING) is the spec. A light pass on a clean Compose↔Views shared-token approach (ADR 0001 hybrid) may help.

### Phase 4: Service, Shell, Settings & Print-Status Home

**Goal**: The app comes alive on the Phase-3 substrate. A foreground service owns the Moonraker spine across Activity recreation and screen-off; a conventional Android **Settings screen** (keyboard allowed) lets the user enter and persist their connection (host/port/key, replacing the static dev config), pick a theme, set text size, and toggle features; the Klippy lifecycle (not the user) drives the primary route (ready→Print Status home, printing→job, startup/error→splash); navigation is a **swipe-up full-screen App Drawer** (tiles incl. Settings + Power); the **Print Status** screen is the home/monitor surface, proving the shared render/throttle primitive in anger, with a Stop control that routes through the Confirm guard and fires `printer.emergency_stop`/cancel. The shared command-dispatch primitive (timeouts + in-flight/busy + debounce) wraps every action call.
**Depends on**: Phase 3
**Requirements**: CONN-01, SET-01, SHELL-01, SHELL-02, SHELL-03, SHELL-04, SHELL-05, PRIM-02, PRIM-05
**Success Criteria** (what must be TRUE):

  1. A foreground service owns the connection so it survives Activity recreation (rotation/config change) and screen-off; the user enters/saves a Moonraker connection (host/IP, port, optional key) in the Settings screen, persisted (DataStore), replacing the Phase-2 static dev config, and the app connects with it
  2. App shows a splash during Klippy startup, routes to the Print Status home on Klippy ready, and routes to job status when a print is active — driven by `klippy_state`, not socket state; the splash surfaces shutdown/error reason text with recovery actions (retry, firmware/Klipper restart where exposed)
  3. Navigation is a swipe-up full-screen App Drawer of destination tiles (Settings + Power present; future panels greyed "coming soon"); the content canvas is full-bleed with no persistent status bar — status is conveyed contextually / as color on existing elements
  4. The Print Status home renders live job/thermal state smoothly at the throttled ~2–4 Hz cadence on the Nexus 7 (consuming the Phase-3 render primitive); a Stop control routes through the full-screen Confirm guard and fires the correct Moonraker call (`printer.emergency_stop` / cancel)
  5. The shared command-dispatch primitive (PRIM-05) enforces explicit network timeouts, an in-flight disabled/busy state, and tap debounce for all Moonraker action calls; text entry (host/key) uses the system keyboard confined to Settings (PRIM-02)

**Plans**: 8 plans
Plans:

**Wave 1**

- [x] 04-01-PLAN.md — Persisted connection: ConnectionConfig + ConnectionStore (DataStore, fail-safe, pure sanitize) + best-effort mDNS discovery (CONN-01/D-04)
- [x] 04-02-PLAN.md — Pure primitives: CommandDispatcher (timeout+in-flight+debounce, PRIM-05/D-18) + TopRoute derive() (klippy-state routing, D-05/D-06)

**Wave 2** *(blocked on Wave 1)*

- [x] 04-03-PLAN.md — App wiring foundation: manifest FGS perms + specialUse service + DinghyApp/AppContainer service-locator + MoonrakerService owning the spine (SHELL-03/CONN-01/D-01..D-03; on-device rotation gate)

**Wave 3** *(blocked on Wave 2; parallel — disjoint files)*

- [x] 04-04-PLAN.md — Settings screen: connection (host/port/key + mDNS scan) + theme/text-size + accent picker (SET-01/PRIM-02/CONN-01/D-15..D-17)
- [x] 04-05-PLAN.md — Splash/recovery hard override: reason text + self-contained recovery actions for first-run/shutdown/unreachable (SHELL-05/D-11..D-13)
- [x] 04-06-PLAN.md — Print Status home (part 1): state-adaptive Focus + 2×3 grid (strict capability fallback) + Stop→ConfirmGuard→emergency_stop, with a reserved sparkline slot (SHELL-04/SHELL-02/D-07..D-10)

**Wave 4** *(blocked on Wave 3)*

- [x] 04-06b-PLAN.md — Print Status home (part 2): heater sparkline (GraphView) into the reserved slot + on-device combined-render perf gate, split from 04-06 per review #4 (SHELL-04/D-09; on-device perf gate)

**Wave 5** *(blocked on Wave 4)*

- [x] 04-07-PLAN.md — Shell integration: swipe-up App Drawer + AppShell full-bleed host + single RootController routing/FGS-start (SHELL-01/D-14; on-device shell gate)

**UI hint**: yes — per `docs/ui_design/` (Splash, App Drawer, Print Status, Settings screens)
**Research note**: STANDARD — foreground Service + Doze-aware lifecycle + DataStore + state-driven nav are well-documented Android patterns. (Prior Phase-3 research retargeted to this phase in the `04-*` phase dir — FGS-owns-spine, pure `derive()` routing, render/throttle seam — still applies.)

### Phase 5: Core Print-Control Panels — Temperature, Move, Extrude

**Goal**: The first real panels — manual printer control. These deliberately come before the print-loop panels because they are simpler surfaces that prove the capability-gating model, the confirm-action policy, the shared command-dispatch primitive (timeouts/in-flight/debounce), and the first sustained high-rate render — the temperature panel EXTENDS the line-graph render primitive (built in the Phase-3 design system, first used on the Phase-4 Print Status home) into a full history graph — against real hardware before the higher-complexity Files/Job-Status work.
**Depends on**: Phase 4
**Requirements**: TEMP-01, TEMP-02, TEMP-03, TEMP-04, MOVE-01, MOVE-02, MOVE-03, MOVE-04, EXTR-01, EXTR-02, EXTR-03, EXTR-04
**Success Criteria** (what must be TRUE):

  1. User sees every heater/sensor's current and target temperature, sets a target via presets and exact keypad entry, applies preheat presets and cooldown, and sees a history graph — built by EXTENDING the shell's shared render/throttle primitive into a full series backfilled from `server.temperature_store` — with no janky graph on the Nexus 7 during an active heat
  2. User can jog X/Y/Z by distance preset, home all or individual axes, disable steppers (through the shared confirm dialog), and see live toolhead position — with un-homed axes visibly gated
  3. User can extrude/retract at a selected distance and speed, run load/unload macros when present (and see a "missing macro" popup when not), and select the active tool on multi-extruder printers; extrude controls respect `min_extrude_temp` (`can_extrude`) and refuse cold extrusion
  4. **Shared command-dispatch (PRIM-05) is verified in anger:** every action button shows an immediate in-flight/disabled state, enforces an explicit timeout (no infinite hang on a dropped packet), and debounces so a second tap can't re-fire — and every panel hides controls the connected printer doesn't support (capability-gated, not disabled-and-confusing)
  5. Provable on the real Ender 5 Plus: preheat → wait for temp → extrude → jog all work end-to-end without the browser open

**Plans**: 11 plans (8 + 3 gap-closure)
Plans:

**Wave 1**

- [x] 05-01-PLAN.md — Pure state additions: gcode_position + can_extrude reducer reads, hasMacroIgnoreCase helper, GCODE_SCRIPT/TEMPERATURE_STORE methods (MOVE-04/EXTR-02/EXTR-04)
- [x] 05-02-PLAN.md — PrinterCommands pure gcode builders (bounded/clamped) + scriptParams + the third sensor-trace token (violet) baked dark+light (TEMP-02/03, MOVE-01..03, EXTR-01)

**Wave 2** *(blocked on Wave 1)*

- [x] 05-03-PLAN.md — Spine plumbing: one-shot temperature_store backfill + min_extrude_temp at handshake, pure backfill mapper, exposed on the SpineHandle/store (TEMP-04/EXTR-04)
- [x] 05-04-PLAN.md — Extend GraphView to N pre-allocated traces + fixed shared Y-range (closes Phase-4 G-1) + multi-snapshot GraphViewHost (TEMP-04)

**Wave 3** *(blocked on Wave 2; parallel — disjoint ui/ packages)*

- [x] 05-05-PLAN.md — Temperature panel: legend + scrubber-set + presets/cooldown + multi-trace backfilled graph (TEMP-01..04)
- [x] 05-06-PLAN.md — Move panel: 3×3 jog pad + Z row + distance selector + Home/Disable(ConfirmGuard)/Back, live gcode_position w/ per-axis homed gating (MOVE-01..04)
- [x] 05-07-PLAN.md — Extrude panel: Move-style extrude/retract + selectors + load/unload popup + capability-gated tool selector + live cold-extrude gate (EXTR-01..04)

**Wave 4** *(blocked on Wave 3; on-device gates)*

- [x] 05-08-PLAN.md — Wire Dest/AppDrawer/AppShell routing for the three panels + D-06 multi-trace perf re-measure on flox + SC-5 end-to-end UAT on the live Ender 5 Plus

**Gap closure** *(from 05-VERIFICATION.md; disjoint files — same wave)*

- [x] 05-09-PLAN.md — G1 BLOCKER: catch RpcError in CommandDispatcher.dispatch() → non-fatal Failure toast (printer-rejected gcode no longer crashes the app); harden dispatcher fake to emit a gcode.script JSON-RPC error (regression guard)
- [x] 05-10-PLAN.md — G2+G3: re-run the full handshake on notify_klippy_ready (re-subscribe + re-run one-shot reads) so the FGS-held session self-heals after a Klipper restart without force-stop and configfile/backfill values refresh
- [x] 05-11-PLAN.md — G4: give printer.gcode.script a 120s timeout (was flat 10s) + typed ConnectionError.Timeout so a long-running-but-successful gcode (Z-home/macros) no longer shows a false "command could not be sent" — G1 rejection-surfacing + true connection-failure messaging preserved

**UI hint**: yes — governed by `docs/ui_design/` (LAW): 04-move.png, 07-single-setting.png, 09-temperature-graph.png
**Research note**: STANDARD — Moonraker temperature/move/extrude API verified; patterns established in earlier phases.

### Phase 6: Command Reference & Capability Matrix

**Goal**: Done FIRST of the remaining work (moved to the top 2026-06-01) because it gates everything after it. Built from BOTH the authoritative docs (the universe of what exists + what each command does) AND live printer introspection (what these printers actually expose) — three layers: (1) a **command/function catalog from the docs** — read the authoritative Klipper G-Code + config/module references, the Moonraker API (JSON-RPC methods + REST endpoints), and the Spoolman API to enumerate the available commands/functions/endpoints and their semantics (params, and how stock Klipper/Moonraker signal success / error `!!` `//` / acceptance) — the docs are the source of truth here, not guesswork; (2) a canonical **in-code command registry** — one single-source-of-truth definition per command the app actually sends (the used subset of the catalog), that all later phases register into, replacing the scattered ad-hoc method constants from phases 1–5; (3) a committed **per-printer availability matrix** — the catalog cross-referenced against LIVE introspection of the real Ender 5 Plus and Ender 3 (`printer.objects.list` objects, `gcode_macro`s, available gcode commands, present Moonraker components) so each later page can ask "does THIS printer support this command/object?" instead of guessing. Establishing all this before building Files/Macros/Calibration means those phases register commands canonically from the start, with doc-grounded semantics, rather than retrofitting.
**Depends on**: Phase 5
**Requirements**: *(reference/quality phase — no new functional REQ-IDs; produces the doc-derived command catalog + the canonical in-code command registry + the committed per-printer availability matrix that later phases consume.)*
**Success Criteria** (what must be TRUE):

  1. A committed `docs/` **command/function catalog** enumerates the relevant Klipper G-Code commands, Moonraker JSON-RPC methods + REST endpoints, and Spoolman API surface the app uses or will use — each with its purpose, key params, and success/error/acceptance semantics — sourced from and citing the authoritative upstream docs (URLs recorded)
  2. A committed `docs/` **per-printer availability matrix** maps that catalog onto each real printer (E5 + E3) — present objects, `gcode_macro`s, available gcode commands, Moonraker components — from live introspection, reconciled with the existing live captures in `docs/moonraker-capabilities.md`
  3. A canonical **in-code command registry** exists (one definition per command the app sends); the existing phase-1–5 scattered method constants are migrated to reference it, and later phases register their commands there rather than inventing new ad-hoc constants
  4. Later phases can capability-gate off the matrix/registry (e.g. Calibration only shows `QUAD_GANTRY_LEVEL` where the printer has it) — the gating data is present and queryable

**Plans**: 5 plans
Plans:

**Wave 0**

- [x] 06-01-PLAN.md — Guard tests and machine-readable sidecar skeletons for registry/catalog drift, byte-identical gcode, capability predicates, dispatcher semantics, and handshake order

**Wave 1** *(blocked on Wave 0)*

- [x] 06-02-PLAN.md — Headless command registry foundation, registry dispatch/request helpers, and live `Capabilities.objects` / `hasObject()` predicate surface

**Wave 2** *(blocked on Wave 1)*

- [x] 06-03-PLAN.md — Full Phase 1-5 outbound call-site refactor through the registry while preserving handshake, dispatcher timeout, e-stop, and builder semantics

**Wave 3** *(blocked on Wave 2; includes live capture checkpoint)*

- [x] 06-04-PLAN.md — Comprehensive Klipper/Moonraker/Spoolman catalog plus E5/E3 availability matrix backed by `catalog.json` and `printer-matrix.json`

**Wave 4** *(blocked on Wave 3; final manual regression checkpoint)*

- [x] 06-05-PLAN.md — Final host verification, blocking flox + live Ender 5 Plus regression, and phase verification record

**Research note**: DEEPER — this phase is substantially a documentation-reading + reference effort: pin down and READ the authoritative Klipper G-Code/Config references, Moonraker API docs (JSON-RPC + REST), and Spoolman API, then reconcile the catalog with live `printer.objects.list` / gcode-help introspection from the real printers. (The availability matrix is being knocked out up front as a reference artifact.)

### Phase 7: Files & Print Control — Core Print-Loop Gate

**Goal**: The **core print-loop gate** — drive a real print start-to-finish without the browser. The Files panel lets a user browse their gcode library, inspect thumbnails and metadata, start a print, and delete files; the same phase wires the **state-adaptive print-control actions (pause / resume / cancel / restart)** onto the existing Print Status home (the currently-stubbed gutter Pause/Resume + Tune buttons), so starting AND controlling a print is one complete user capability. The live-monitoring half of the old "Job Status" phase (progress/temps/Z) was already delivered by the Phase-4 Print Status home + the Status quick-task enrichment (Inc 1–3), so this phase verifies it end-to-end against a real print rather than rebuilding it. Reuses the already-built `PrintMetadataHolder` / `thumbnailUrl()` / Coil wiring / `LastJobHolder` primitives and registers its commands into the Phase-6 command registry. The deep robustness (reconnect print-state resync, process-death recovery) is deferred to the ship phase (Phase 21).
**Depends on**: Phase 6
**Requirements**: FILE-01, FILE-02, FILE-03, FILE-04, JOB-01, JOB-02, JOB-03, JOB-04, JOB-05
**Success Criteria** (what must be TRUE):

  1. User can browse Moonraker gcode files and folders with name/date/size metadata, lazy-fetched as rows scroll into view
  2. User sees thumbnails decoded and downsampled off the UI thread (`inSampleSize`, bounded cache) — no OOM or hang on a large library that includes some thumbnail-less files (graceful fallback)
  3. User can start a print from a confirm dialog showing the thumbnail/details, and the start is confirmed via the resulting `print_stats` state flip rather than assumed from the command ack
  4. User can delete a gcode file through the shared confirm dialog
  5. **Print controls are wired and state-adaptive (JOB-03/04/05):** the Print Status gutter adapts to print state — printing: pause/cancel; paused: resume/cancel; complete/error: restart/files — with cancel and restart routed through the mandatory confirm guard (PRIM-03 destructive set); each control reflects the resulting `print_stats` flip, not a bare command ack
  6. **Core print-loop gate, proven on the Ender 5 Plus:** connect → browse files → start a print → watch live progress/temps/Z on the Status home (JOB-01/02) → pause, resume, and cancel — all without the browser open

**Plans**: 6

**Wave 1**

- [x] 07-01-PLAN.md - Runtime registry and command sidecar promotion for Files and print-control operations
- [x] 07-02-PLAN.md - Pure file-browser models, path discipline, sorting, and selected-file preview parsing

**Wave 2**

- [x] 07-03-PLAN.md - Session-owned file browser client, Files holder, and state-confirmed start/delete behavior

**Wave 3**

- [x] 07-04-PLAN.md - Live Files route, ConfirmGuard safe-dismiss labels, and hybrid RecyclerView Files screen

**Wave 4**

- [x] 07-05-PLAN.md - State-adaptive Print Status pause/resume/cancel/restart/Files gutter controls

**Wave 5**

- [x] 07-06-PLAN.md - Automated release verification and Ender 5 Plus core print-loop UAT

**UI hint**: yes
**Research note**: STANDARD — Moonraker file API + thumbnail URL resolution (already proven by the Status quick-tasks) and the `print_stats`-driven pause/resume/cancel/restart command set verified against official docs.

### Phase 8: Macros & Console — Functional-Core Complete

**Goal**: The escape hatches that prevent the user from ever needing SSH or a browser for anything unusual. Architecturally simple — they consume the event bus and command path already built — and they close the **functional-core-complete gate**. Built on the current pragmatic backend (raw response display + basic severity coloring); deeper request-cadence/error-handling consolidation is deferred to Phase 13. Nothing more; this phase holds the functional-core line. (Note: the Console's `notify_gcode_response` stream built here is the input the later Macro Prompt Protocol phase parses.)
**Depends on**: Phase 7
**Requirements**: MACRO-01, MACRO-02, MACRO-03, CONS-02
**Deferred (2026-06-02)**: CONS-01 (send arbitrary G-code) — the console is read-only this phase; text-send pulled and unscheduled (see `phases/08-.../08-CONTEXT.md`).
**Success Criteria** (what must be TRUE):

  1. User can list and run `gcode_macro` entries discovered from `printer.objects.list`, across three screens — a System list (check/select which macros surface, underscore-prefixed hidden by default), a Bookmarked launcher, and an Execution popup with auto-detected parameter entry (parsed from the macro's gcode body) and Execute/Cancel
  2. User sees a READ-ONLY command/response history with severity coloring (errors `!!`, warnings `//`), backfilled from `server.gcode_store` and updated live via `notify_gcode_response`, with bounded scrollback and opt-in noise filters (hide temperatures / timelapse / prompt commands)
  3. **Functional-core complete:** after a reconnect, Console history backfills correctly from `server.gcode_store` rather than silently dropping the lines that arrived while disconnected — and with this the full functional core (Connect + Temp/Move/Extrude/Files/Print-Control/Macros/Console) is in place
  
**Plans**: 7 plans
Plans:

**Wave 0**

- [x] 08-01-PLAN.md — Live read-only probe of `server.gcode_store` + `configfile` macro-body shape recorded into docs (mock-vs-reality gate) + RED test scaffolds with real probed fixtures (CONS-02/MACRO-02; live checkpoint)

**Wave 1** *(blocked on Wave 0; parallel — disjoint files)*

- [x] 08-02-PLAN.md — Console pure core: ConsoleLine + ConsoleSeverity classifier + ConsoleFilters (verbatim Mainsail regexes, view-layer only) + object-typed ConsoleScrollback ring (CONS-02/D-02..D-05)
- [x] 08-03-PLAN.md — Macro pure core: MacroParamParser (verbatim Mainsail regex) + [BLOCKING] MacroInvocation string-param sanitizer w/ injection-rejection test + MacroPrefs DataStore (MACRO-02/03/D-09/D-10)

**Wave 2** *(blocked on Wave 1)*

- [x] 08-04-PLAN.md — Spine plumbing: `server.gcode_store` registry+method+parser, handshake backfill REPLACE read + extend the single `configfile` query for macro bodies, store seams (CONS-02/MACRO-02/D-02/D-04)

**Wave 3** *(blocked on Wave 2; parallel — disjoint ui/ packages)*

- [x] 08-05-PLAN.md — Console screen: ConsoleHolder (raw, D-04) + RecyclerView-in-AndroidView scrollback (D-05) + Field-only ScreenScaffold w/ opt-in filter toggles, read-only no-keyboard (CONS-02/D-01..D-05)
- [x] 08-06-PLAN.md — Macro screens: MacroHolder (combine caps/prefs/bodies) + Bookmarked launcher + System manage-visibility + Execution popup (action gate, NumpadPage/keyboard, sanitized dispatch) (MACRO-01/02/03/D-06..D-10)

**Wave 4** *(blocked on Wave 3; on-device gate)*

- [x] 08-07-PLAN.md — Nav wiring (Dest+drawer tiles+AppShell branches, drawer-swipe suppress on scroll-Fields) + on-device UAT + console-scroll perf gate on flox + live Ender 5 Plus (functional-core gate)

**UI hint**: yes
**Research note**: STANDARD — straightforward application of the existing notify event bus and command path.

### Phase 9: Calibration & Maintenance

**Goal**: Dedicated touch pages for the high-use, semi-regular calibration routines a Klipper owner actually runs from the printer — so they never need a browser for routine maintenance. Each page runs the relevant gcode and presents its result legibly: `SCREWS_TILT_CALCULATE` (manual bed leveling — parse the per-screw "CW/CCW turns" from the gcode response into a readable adjust-this-screw display), `Z_TILT_ADJUST` (multi-Z gantry level — the E5 Plus has dual Z), `BED_MESH_CALIBRATE` (probe mesh — run + optionally visualize), and `QUAD_GANTRY_LEVEL` where present. Every page is **capability-gated by the Phase-6 matrix** (hidden when the printer lacks the command), runs through the shared confirm/dispatch path, and reflects completion from the gcode-response/state rather than a bare ack.
**Depends on**: Phase 8
**Requirements**: *(new CALIB-* family — to be defined at phase discuss)*
**Success Criteria** (what must be TRUE):

  1. The Calibration drawer/pages show ONLY the routines the connected printer supports (capability-gated by the Phase-6 matrix) — no dead buttons on a printer that lacks the command
  2. `SCREWS_TILT_CALCULATE` runs and its result is parsed into a clear per-screw adjustment display (which screw, direction, amount), not raw console text
  3. `Z_TILT_ADJUST` / `QUAD_GANTRY_LEVEL` run with live progress/convergence feedback and a clear done/failed result; `BED_MESH_CALIBRATE` runs and the result is at least confirmed (mesh visualization is a stretch goal)
  4. Long-running calibrations respect the gcode-completion timeout discipline (no false "command could not be sent") and route through the confirm/dispatch primitive

**Plans**: 7 plans
Plans:

**Wave 0**

- [x] 09-01-PLAN.md — Capture the five REAL E5 calibration fixtures + seven RED parser scaffolds (fixture-capture-first; live checkpoint)

**Wave 1** *(blocked on Wave 0)*

- [x] 09-02-PLAN.md — Spine plumbing: register ~12 calibration commands (ObjectPresent-gated, G4 timeout) + surface the five live objects in PrinterState/reducer/subscribe-superset + screws-config one-shot

**Wave 2** *(blocked on Wave 1)*

- [x] 09-03-PLAN.md — The five pure result parsers + two gating predicates (the Nyquist core; turns the RED scaffolds GREEN)

**Wave 3** *(blocked on Wave 2; parallel — disjoint files)*

- [x] 09-04-PLAN.md — Calibration hub (D-14) + screws-tilt guided-loop screen (owner-authored to-scale bed) + holders
- [x] 09-05-PLAN.md — Z-tilt/QGL screen + bed-mesh heatmap (NEW BedMeshHeatmapView) + scale toggle + profiles + ConfirmGuard amber variant

**Wave 4** *(blocked on Wave 3)*

- [x] 09-06-PLAN.md — Interactive Probe-Calibrate page (TESTZ/Accept/Abort/amber SAVE_CONFIG) + the D-15 Files-delete scoping fix + 09-UAT.md

**Wave 5** *(blocked on Wave 4; on-device gate)*

- [ ] 09-07-PLAN.md — Nav wiring (single Calibration tile → hub → five sub-routes) + SAVE_CONFIG G2 re-handshake regression + on-device heatmap perf + full live-E5 UAT

**UI hint**: yes — governed by `docs/ui_design/` (LAW): 05-screws-tilt.png (locked) + 09-UI-SPEC.md (five new screens)
**Research note**: DEEPER — exact gcode-response shapes for `SCREWS_TILT_CALCULATE` (turn directions/amounts), `Z_TILT_ADJUST`/`QGL` convergence lines, and `BED_MESH` output verified against Klipper docs + the Phase-6 capability matrix. Key de-risking finding: results come from STRUCTURED live objects (screws_tilt_adjust.results / bed_mesh.mesh_matrix / z_tilt.applied / manual_probe), not console parsing.

### Phase 10: Webcam Streaming

**Goal**: View the printer's webcam on the tablet without a browser. Decode the common Klipper MJPEG case (`ustreamer`/`crowsnest`/`mjpg-streamer`) — enumerate cams via Moonraker `/server/webcams/list`, stream the `multipart/x-mixed-replace` feed, and decode frames **hard-downscaled** to the display size (reuse-a-bitmap, `inSampleSize`) because full-res MJPEG decode will OOM/jank the Adreno-320 / 2GB floor. WebRTC (`camera-streamer`/`go2rtc`) is explicitly deferred past MJPEG.
**Depends on**: Phase 9
**Requirements**: CAM-01
**Success Criteria** (what must be TRUE):

  1. The app enumerates configured webcams (`/server/webcams/list`) and streams the selected MJPEG feed, rendering live frames
  2. Frames are decoded off the UI thread and downscaled to the view size (bounded memory) — no OOM or sustained jank on the Adreno-320 floor during a stream
  3. The stream pauses/stops cleanly when the page is backgrounded (no wasted decode/bandwidth), and a missing/unreachable cam degrades gracefully
  4. WebRTC is documented as explicitly deferred (MJPEG-only this phase)

**Plans**: 8 plans
Plans:

**Wave 1**

- [x] 10-01-PLAN.md — Wave 0: verbatim E5+E3 /server/webcams/list goldens + labeled synthetic MJPEG fixtures + hardened fakes + RED scaffolds (mock-vs-reality gate)

**Wave 2** *(blocked on Wave 1)*

- [x] 10-02-PLAN.md — Pure core: tolerant Webcam models + D-09 URL resolver/rewriter + D-02 rung selection

**Wave 3** *(blocked on Wave 2; parallel — disjoint files)*

- [x] 10-03-PLAN.md — Enumeration spine wiring: server.webcams.list one-shot spec + SpineHandle.webcams StateFlow + AppContainer webcamCount (cadence-compliant, edge-driven)
- [x] 10-04-PLAN.md — Decode I/O: Content-Type probe + lean Okio MJPEG decoder (drop-behind, inBitmap) + ~2fps snapshot poller (two derived OkHttp postures)
- [x] 10-05-PLAN.md — Render surface: WebcamView (ThemeableView, pixel-square never-stretch + rounded cutout + token chrome) + WebcamViewHost

**Wave 4** *(blocked on Wave 3)*

- [x] 10-06-PLAN.md — Orchestration: WebcamHolder (rung select + decoder/poller lifecycle + D-11 reconnect + D-12 backoff + cancel) + WebcamScreen (single-focus/aspect-aware Field/Back gutter) + per-printer WebcamPrefs

**Wave 5** *(blocked on Wave 4)*

- [x] 10-07-PLAN.md — Nav wiring: Dest.Webcam + runtime-greyed drawer tile (D-08) + AppShell arm + page-visible lifecycle + WR-01 DisposableEffect cancel + instrumented gating/lifecycle/dead-end tests

**Wave 6** *(blocked on Wave 5; on-device gate)*

- [x] 10-08-PLAN.md — On-device flox perf pin (A1/A2 fps/inSampleSize, gfxinfo 0-frozen) + snapshot-ladder UAT (E3) + rung-3 card (E5/WebRTC) + WebRTC-deferred doc (BLOCKING human-verify)

**UI hint**: yes
**Research note**: STANDARD — MJPEG multipart decode pattern is in the stack doc (CLAUDE.md camera note); `niqdev/ipcam-view` / `perthcpe23/android-mjpeg-view` as reference (reimplement lean in Kotlin).

### Phase 11: Spool Management — Spoolman + Camera QR

**Goal**: Bring real spool management to the printer-side screen — the thing people most want — via Moonraker's Spoolman integration (both target printers already run it). List spools and the active spool, show filament remaining/usage, and set the active spool. The headline feature: a **tablet-camera QR-scan-to-assign** flow — point the device camera at the spool's Spoolman QR label (`web+spoolman:s-<id>`), decode it, and set that spool active. Far easier than rigging an ESP32 NFC tag. Decoder is **ZXing** (pure-Java) because the Nexus 7 floor has no Play Services for Google ML Kit.
**Depends on**: Phase 10
**Requirements**: SPOOL-01 (umbrella) + finer family coined at discuss/plan (mirrors CALIB-*): SPOOL-02 (active-spool Status card, ⊂SPOOL-01/SC-1), SPOOL-03 (picker + filters in `Dest.Spool`, ⊂SPOOL-01/SC-1), SPOOL-04 (set/clear/change active via Moonraker JSON-RPC, ⊂SPOOL-01/SC-1), SPOOL-05 (QR scan-to-assign, ZXing GMS-free, SC-2/SC-4), SPOOL-06 (camera permission + no-camera degrade, SC-3), SPOOL-07 (print-start warn-only gate in Files, D-01), SPOOL-08 (external-change reconciliation + notify routing + pending-report staleness, D-10/D-11), SPOOL-09 (measured-weight correction + nice-to-haves: gcode-aware prefilter, location shortcuts, archived warning, D-04)
**Success Criteria** (what must be TRUE):

  1. The app lists Spoolman spools + the active spool and filament remaining/usage via Moonraker `/server/spoolman/*`, and can set the active spool — capability-gated on the `spoolman` component being present
  2. The tablet camera scans a Spoolman QR label, decodes the `web+spoolman:s-<id>` payload (ZXing, GMS-free), resolves it to a spool, and sets it active — with a clear confirm + result
  3. Camera permission is requested gracefully and the feature degrades cleanly on a device with no camera (manual spool-pick still works)
  4. Provable end-to-end on a real printer: load filament → scan its label → the active spool flips in Spoolman

**Plans**: 9 plans

Plans:

- [x] 11-01-PLAN.md — Wave 0: live fixtures→goldens, hardened fakes, compile-clean RED scaffolds, catalog gap
- [x] 11-02-PLAN.md — Wave 1: null-safe Spoolman models/parsers (proxy-v2 envelope) + FilePreviewMetadata filament arrays
- [x] 11-03-PLAN.md — Wave 1: QR payload parser (D-12), warn-only print-start gate (D-01), scan state machine (D-15)
- [x] 11-04-PLAN.md — Wave 2: Spoolman spine — specs + notify routing + SpoolmanClient + ActiveSpoolFacade + derived flows
- [x] 11-05-PLAN.md — Wave 3: CameraX 1.5 + zxing 3.3.3 deps/manifest + QrCodeAnalyzer + ZxingDecodeVersionTest
- [x] 11-06-PLAN.md — Wave 4: Dest.Spool + greyed tile + active-spool Status card + Files-style picker/holder
- [x] 11-07-PLAN.md — Wave 4: scan surface (CameraX+release) + permission glue + confirm-first + measured-weight
- [x] 11-08-PLAN.md — Wave 5: warn-only gate hook + gcode-aware prefilter + change-during-print
- [x] 11-09-PLAN.md — Wave 6: on-device end-to-end UAT (scan→flip) + degrade + save→change→restore (BLOCKING)

**UI hint**: yes
**Research note**: DEEPER — Moonraker Spoolman API surface (`/server/spoolman/*`, active-spool set), Spoolman QR label payload format (`web+spoolman:s-<id>`), CameraX + ZXing on API 23 without GMS, and camera-permission flow.

### Phase 12: Macro Prompt Protocol

**Goal**: Make user-authored macros that drive interactive dialogs work on Dinghy. Klipper macros emit `// action:prompt_begin/prompt_text/prompt_button/prompt_footer_button/prompt_show/prompt_end` lines through the gcode-response stream; this phase parses that mini-protocol (per the klipper-macro-prompt-protocol, github.com/mrmees/klipper-macro-prompt-protocol) and renders the corresponding interactive dialog — title, text, and buttons that fire their gcode — reusing the Console's `notify_gcode_response` stream and the design system's dialog primitive. The escape hatch that lets power users build their own guided flows (filament change wizards, multi-step maintenance) and have them just work on the tablet.
**Depends on**: Phase 11 (sequential); functional inputs: Phase 8 (Console `notify_gcode_response` stream) + Phase 3 (dialog primitive)
**Requirements**: *(new PROMPT-* family — to be defined at phase discuss)*
**Success Criteria** (what must be TRUE):

  1. The `// action:prompt_*` sequence is parsed out of the live gcode-response stream into a structured prompt model (title, text, buttons with label/gcode/style), tolerant of malformed/partial sequences
  2. A prompt renders as an interactive dialog using the existing design-system dialog primitive; tapping a button sends its gcode and the dialog dismisses/updates per the protocol
  3. `prompt_end`/footer semantics and re-entrancy (a macro re-prompting) are handled, and an unsupported/garbage action line never crashes or wedges the Console
  4. Provable with a real prompt-protocol macro on the printer (e.g. a load-filament wizard) driven start-to-finish from the tablet

**Plans**: 5 plans
Plans:

**Wave 1**

- [x] 12-01-PLAN.md — Fixture corpus port + RED conformance scaffold (compiles day-one) + the pure total line parser/sub-parsers + PromptMarkup AST (PROMPT-01)

**Wave 2** *(blocked on Wave 1)*

- [x] 12-02-PLAN.md — Pure reducer state machine + model + 6-key view projection; turns the 26-fixture conformance gate GREEN under the dinghy/touch identity + the 8 hardening cases (PROMPT-01/03)

**Wave 3** *(blocked on Wave 2; parallel — disjoint files)*

- [x] 12-03-PLAN.md — PromptEngine spine holder: gcode-stream subscribe→reduce, disconnect-local-close-no-prompt_end (D-10), stable dispatch keys + Failure fold (PROMPT-01/03)
- [x] 12-04-PLAN.md — PromptDialog full-screen overlay + markup→AnnotatedString (D-03 author-hex carve-out) + 6-style→token resolver + Coil-bounded image + equal-width rows + degenerate states (PROMPT-02)

**Wave 4** *(blocked on Wave 3; on-device gate)*

- [x] 12-05-PLAN.md — AppShell wiring (engine construct + overlay hoist + button/close dispatch + drawer suppress) + the D-03 UI-LAW carve-out docs + on-device live-macro UAT (SC-4 wizard, close echo, disconnect-no-prompt_end) (PROMPT-02/03/04)

**UI hint**: yes
**Research note**: STANDARD — the protocol is specced (klipper-macro-prompt-protocol); reuses the Phase-8 gcode-response bus and Phase-3 dialog primitive.

### Phase 13: Optimization, Network Efficiency & End-to-End Reliability

**Goal**: With EVERY screen built (control, files, macros/console, calibration, webcam, spool, prompts), do the driven backend pass that would have been speculative earlier — the app now knows exactly which key-values it displays and which commands it sends. A **request-cadence audit**: justify every subscribed object (one-shot vs subscribe), coalesce/throttle high-rate data to display cadence, and ensure NO screen opens its own polling outside the central `PrinterStateStore` / single `objects.subscribe` handshake — so the app stops spamming the wireless LAN multiple times a second for data that doesn't need it. Plus end-to-end reliability hardening: principled error/staleness handling per the Phase-6 command/error reference, and the cross-screen behaviors that only emerge once everything coexists. A refactor/quality phase, not a new-screen phase; pays down the deliberately-pragmatic backend the screens were built on.
**Depends on**: Phase 12
**Requirements**: *(quality/refactor phase — non-functional request-efficiency + reliability goals; no new functional REQ-IDs)*
**Success Criteria** (what must be TRUE):

  1. A request-cadence audit is complete and applied: every subscribed object is justified, high-rate data is coalesced/throttled to display cadence, and no screen polls outside the central single-subscribe handshake — measurably reducing per-second LAN request volume vs the pre-optimization baseline
  2. Error/staleness handling is principled across screens (driven by the Phase-6 command/error reference), not per-screen guesswork
  3. The optimization is behavior-preserving for the user — every screen still shows correct live data — provable on the real Ender 5 Plus and Ender 3 with the existing on-device gates green
  4. Cross-screen reliability holds (rapid navigation, reconnect mid-feature, capability changes) without leaks or stale subscriptions

**Plans**: 4 plans
**Wave 1**

- [x] 13-01-PLAN.md — Wave 0 (BLOCKING, D-10): live SAVE_CONFIG wire-capture on BOTH printers + harden the mock-that-lied (klippy-down window) + RED resumed-diff/Syncing/self-heal regression tests

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 13-02-PLAN.md — the headline fix: visible (D-03 Syncing→Connected), disconnect-driven, self-healing klippy-restart recovery in MoonrakerSession (reuse emit/markStale/seed/runHandshake/supervisor)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 13-03-PLAN.md — cadence audit deliverable (docs/request-cadence-contract.md, SC-1) + the one applied fix (remove redundant refreshProbeZOffset, sequenced after 13-02)

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 13-04-PLAN.md — binding gate (D-09): dual-printer dual-scenario on-device UAT on flox + live E5 & E3 (1st run FAILED → 2 gaps; SUPERSEDED by 13-05; RE-RUN PASSED 2026-06-03)

**Wave 5 — gap closure** *(blocked on Wave 4; closes the 13-04 binding-gate FAIL)*

- [x] 13-05-PLAN.md — gap-closure: G-B1a OkHttp pingInterval keepalive + G-A1 hoist shell nav state (no Home bounce) + G-B1b socket-reconnect Syncing splash + ~600ms min-dwell (the D-05 departure); re-run binding UAT PASSED on flox + live E5 & E3

**Research note**: DEEPER — request-cadence/subscription audit informed by the Phase-6 command/error reference and the live captures in `docs/moonraker-capabilities.md`. NOTE (D-08): the old Phase-14 "reconnect print-state resync" line is pulled INTO Phase 13 (all in-session resync — klippy-restart + mid-print network-drop reconnect + the print-state resync that rides along). Phase numbers/order otherwise unchanged.

### Phase 14: Multi-Printer Switching

**Goal**: Turn the single hardcoded-via-config connection into a managed set of printer profiles, so a user with more than one Klipper machine (the dev's own Ender 5 Plus + Ender 3 Pro) can register each printer once and switch the active connection without re-entering host/port/key. The foreground service rebinds the Moonraker spine to the selected profile; capability detection, the command registry, and every screen follow the active printer. Placed FIRST in the new block because it is cross-cutting — building the later new per-printer surfaces (fine-tune, output controls, webcam, system info) multi-printer-aware up front beats retrofitting them.
**Depends on**: Phase 13 (connection/state spine + foreground service + Settings/DataStore)
**Requirements**: MULTI-01
**Success Criteria** (what must be TRUE):

  1. User can save multiple printer profiles (name + host/port/API key) in Settings, persisted via DataStore, and choose which is active
  2. Switching the active printer cleanly tears down the current spine and rebinds the service to the new profile — capability detection, the command registry, and all screens reflect the newly-selected printer with NO stale state from the prior one
  3. An active-printer indicator/switcher is reachable from the shell (e.g. the App Drawer), and the choice survives app restart and process death
  4. Proven live by switching between the real Ender 5 Plus and Ender 3 Pro and driving each (connect → monitor → a control action) without re-entering connection details

**Plans**: 6 plans
Plans:

**Wave 0**

- [x] 14-01-PLAN.md — Profile/PersistedProfile model + ProfileStore (mirror ConnectionStore verbatim; UUID identity D-05, redacting toString V7, D-12 auto-pick in the delete writer) + 4 Wave-0 RED test scaffolds (compile day-one)

**Wave 1** *(blocked on Wave 0)*

- [x] 14-02-PLAN.md — AppContainer active-config derivation (combine→pick→map→distinctUntilChanged, the load-bearing no-churn guard) + per-printer theme re-seed (D-08) + hasConfig→"has active profile" + DinghyApp profiles.preferences_pb DataStore + the ONE-line MoonrakerService config-source swap

**Wave 2** *(blocked on Wave 1; parallel — disjoint files)*

- [x] 14-03-PLAN.md — Webcam pref re-key host→profileId (D-06, both touch points: WebcamPrefs + WebcamHolder caller) + Dest.Devices route enum
- [x] 14-04-PLAN.md — Settings restructure: Connection→profile list CRUD (add/edit, D-13) + delete behind ConfirmGuard (D-14) + Appearance section retargeted to the active profile's theme (D-09)

**Wave 3** *(blocked on Wave 2)*

- [x] 14-05-PLAN.md — New DevicesScreen switcher (field-of-printers, tap=setActive→seam rebind, no confirm D-02) + Devices drawer tile LIVE + active-name subtitle (D-01/D-03) + AppShell host/swipe-suppress + thread active profile id into the webcam holder

**Wave 4** *(blocked on Wave 3; on-device gate)*

- [x] 14-06-PLAN.md — Fill the instrumented ProfileSurvivesRestartTest (real DataStore cold re-read) + the BLOCKING live two-printer hands-on UAT on flox + live E5/E3 (SC-4)

**UI hint**: yes
**Research note**: STANDARD — reuses the Phase-2 spine + Phase-4 service/Settings/DataStore; the work is profile management + a clean spine rebind, not new protocol. The one net-new wrinkle is per-printer theme re-seeding (D-08), which reuses ThemeResolver.apply.

### Phase 15: Theme System & Settings Redesign

**Goal**: Establish the full semantic-token theme system as the app's visual foundation and rebuild the Settings screen to host it (merges the former Settings-Redesign + Final-Theme/UI-Conformance phases). Integrates the theme work developed in a parallel session (token system, dark/light/user-custom themes, S/M/L text size) and gives it a real test/config surface: Settings is reorganized into coherent sections — printers/profiles (Phase 14), connection, appearance/theme + text size, feature toggles (webcam/WebRTC, outputs, fine-tune), and system/about — the one conventional keyboard-allowed screen, scaled to the full v1 feature set. Also sweeps the EXISTING surfaces for token/grammar conformance so the theme system lands clean across what's already built. Pulled to the FRONT because the active parallel theme work needs the new Settings UI to test against; a light whole-app conformance re-check of late surfaces folds into Phase 21 (Ship).
**Depends on**: Phase 14
**Requirements**: *(refines SET-* / THEME-* / UI-* — UX + conformance rework, no new functional REQ-IDs; finer set at discuss)*
**Success Criteria** (what must be TRUE):

  1. The full semantic-token theme system (dark, light, user-custom) renders correctly across the app, configurable from the redesigned Settings, with the S/M/L text-size setting + the established `fsSp` font-size scale applied consistently (no too-small fonts — the recurring lesson)
  2. Settings is reorganized into clear sections (printers/profiles, connection, appearance/theme + text size, features/toggles, system/about) that scale to the full v1 feature set; multi-printer profile management (Phase 14) stays first-class and all existing settings persist via DataStore
  3. Existing surfaces pass a token-purity / button-intent / Focus-Field-Gutter / touch-target conformance check against `docs/ui_design/` LAW (no raw colors), in dark + light + custom
  4. Conforms to the design system; verified on-device on flox in portrait + landscape
  5. Later phases (16–20) build their new surfaces theme-conformant by construction; the final whole-app conformance re-sweep is folded into Phase 21 (Ship)

**Plans**: 7 plans (waves 0-6, design-foundation-first: engine → bridge → substrate → persistence → UI → pool wiring)
Plans:
**Wave 1**

- [x] 15-01-PLAN.md — Wave-0 RED scaffolds + Node golden oracle (color.js) dump + pinned fixture
- [x] 15-02-PLAN.md — Port color.js → pure Kotlin Palette generator; GREEN golden + math tests

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 15-03-PLAN.md — TokenBridge (tokensFromPalette) + extend ThemeTokens (pool/directional, retire violet); GREEN bridge test

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 15-04-PLAN.md — Rewire ThemeResolver to generate-and-cache; retire TokenDelta; demote BakedTokens to fail-safe

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 15-05-PLAN.md — Per-profile theme-tuple persistence (fresh-start sanitize) + AppContainer seedTheme WR-02 reactive fix

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 15-06-PLAN.md — Settings hybrid hub + pushed theme-editor sub-page (color wheel settle-regen) + on-device UAT

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 15-07-PLAN.md — Pool wiring: GraphView/Temp/Print-Status readouts + Move directional outlines + on-device UAT

**UI hint**: yes
**Research note**: STANDARD — integrates the parallel theme work + UI reorganization on the existing DataStore-backed Settings; audit/polish against the existing UI LAW + `reference/hifi.css`; no new backend.

### Phase 15.1: Shape-Coded Status & Semantic Color (INSERTED)

**Goal**: Build the load-bearing semantic + safety half of the theme system that Phase 15 scoped out (engine-first), BEFORE the Home/Status redesign so every remaining surface is built against the final model. Deliver: shape-coded status (octagon=stop / triangle=caution / circle=go) + icon + position across every status signal so color is never the sole safety cue (COLOR-SYSTEM.md §7/§D — the load-bearing mechanism); status-from-pool visual recolor; High-Contrast mode's stoplight-RYG *status* rendering; resolve whether status slots are user-editable (open from Phase-15 D-09); the app-wide pool→role semantic assignment ("what gets assigned where"); the color-doctrine decisions D-5 (back-button color, one app-wide rule), D-6 (bed-mesh dedicated perceptually-uniform OKLCH sequential ramp — render/BedMeshHeatmapView.kt), D-8 (force-move toggle color); Move's homed/unhomed status-color + force-move lock-shape (the status/shape parts deferred from Phase 15); and reconcile/rewrite docs/ui_design/THEMING.md against the sibling ../theme_theory/COLOR-SYSTEM.md to canonicalize the new model (status no longer fixed RYG, surfaces pure-neutral, pool added). Source: 15-CONTEXT.md ⟨deferred⟩ + COLOR-SYSTEM.md.
**Requirements**: *(refines THEME-* / UI-* — semantic + safety layer; finer set at discuss)*
**Depends on:** Phase 15
**Plans:** 8/8 plans complete
Plans:
**Wave 1**

- [x] 15.1-01-PLAN.md — Wave 0: type-decision lock (statusOverrides typing) + shape-glyph assets + compile-clean RED scaffolds + golden fixture
- [x] 15.1-02-PLAN.md — Wave 1: N-series seriesColor helper + PaletteMode field (D-05/D-09) + D-07 directional re-derivation
- [x] 15.1-03-PLAN.md — Wave 1: OKLCH bed-mesh sequential ramp off red/green (D-11) + golden test

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 15.1-04-PLAN.md — Wave 2: status-slot override persistence + TokenBridge application + fail-safe (D-03)
- [x] 15.1-05-PLAN.md — Wave 2: GraphView N-series rewire + PrintStatus nozzle=accent (D-06) + Stop octagon glyph

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 15.1-06-PLAN.md — Wave 3: Console ERROR/WARNING glyphs + Move unhomed caution/force-move lock-shape + neutral Back (D-01/D-02/D-12/D-10)
- [x] 15.1-07-PLAN.md — Wave 3: Theme Editor status-slot editing + shape-decorated preview (D-03)

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 15.1-08-PLAN.md — Wave 4: rewrite docs/ui_design/THEMING.md as canonical token-law + reconcile CLAUDE.md back-color (D-13)

### Phase 15.2: Theme Conformance Sweep & Settings IA (INSERTED)

**Goal**: With the semantic/shape model finalized in 15.1, sweep every EXISTING surface for conformance to the reconciled theme LAW before new surfaces are built on top: token purity (all role tokens, zero raw colors), button-intent color correctness, Focus/Field/Gutter grammar, ≥64px touch targets, the fsSp font-size scale, and dark/light/custom correctness. Also resolve the Settings-vs-Devices information-architecture boundary deferred from Phase 15 — Connection feels redundant given the Devices screen; decide between a "printer settings page" reframe vs moving connection into a per-printer Devices editor, and implement it. The old "light final conformance re-sweep folds into Ship (Phase 21)" still holds for the LATE surfaces (16–20) built after this; 15.2 is the heavy sweep of everything that exists today. Tracked todos: .planning/todos/pending/2026-06-05-settings-vs-devices-boundary.md + 2026-06-05-pool-color-semantic-assignment.md (the latter's assignment is BUILT in 15.1; 15.2 verifies surfaces conform to it).
**Requirements**: *(refines THEME-* / UI-* / SET-* — conformance audit + Settings IA; finer set at discuss)*
**Depends on:** Phase 15.1
**Plans:** 1/6 plans executed
Plans:
**Wave 1**

- [x] 15.2-01-PLAN.md — Wave 1: transient theme-override layer (effectiveTokens, no-persist) + dev-enable boolean + Wave-0 RED scaffold + AUDIT.md scorecard scaffold (D-06/D-08/D-09/D-12)

**Wave 2** *(blocked on Wave 1 completion)*

- [ ] 15.2-02-PLAN.md — Wave 2: the two floating cycler widgets (style 6-combo + size S/M/L) wired as an AppShell overlay, dev-enable-gated; on-device cycler-walk (D-06/D-07/D-08/D-09)

**Wave 3** *(blocked on Wave 2 completion)*

- [ ] 15.2-03-PLAN.md — Wave 3: Printers (= Devices renamed) absorbs Connection editing + per-profile feature-toggle field on Profile (D-02/D-04)

**Wave 4** *(blocked on Wave 3 completion)*

- [ ] 15.2-04-PLAN.md — Wave 4: 4-tile IA split (Printers · Theme · Settings · About) + Dest/DRAWER_TILES/when(dest) routing + About dev-enable toggle (D-01/D-03/D-04/D-05)

**Wave 5** *(blocked on Wave 4 completion)*

- [ ] 15.2-05-PLAN.md — Wave 5: app-wide Back-intent sweep to Neutral + D-10 guided core-screen review → harden THEMING.md + AUDIT checklist (D-10/D-13)

**Wave 6** *(blocked on Wave 5 completion)*

- [ ] 15.2-06-PLAN.md — Wave 6: app-wide AUDIT.md scorecard across all ~30 surfaces + long-tail mechanical conformance fixes + judgment-call gate (D-11/D-12/D-13)

### Phase 16: Home / Print-Status Redesign

**Goal**: Rework the home / Print-Status surface into its definitive form as the visual FOUNDATION for the remaining feature phases — built right after the theme system + Settings (Phase 15) so the later features (fine-tune, outputs, system info, webcam) slot into the final design instead of forcing a later redesign (the "avoid future rework" sequencing). The original home was built early against `docs/ui_design/`; rework the information hierarchy, quick-actions, and at-a-glance layout into the definitive home, leaving forward entry points (greyed/capability-gated tiles, the established pattern) for features built in Phases 17–20 — without regressing the proven Views-based render/throttle primitives or the core monitor loop. **Includes the Z-babystep control (moved from Fine-Tune, 17-CONTEXT):** a conditional live-Z-offset control surfaced on Print-Status that auto-appears only during the early first-layer window (~first 10 layers) and hides after — `SET_GCODE_OFFSET Z_ADJUST=±n MOVE=1` ↔ `gcode_move.homing_origin[2]`, incremental 0.01/0.05 steps, "nozzle closer/away" labels, live applied-offset readout, session-only (saving to config stays a Calibration action).
**Depends on**: Phase 15
**Requirements**: *(refines SHELL-* / JOB-* — UX rework, no new functional REQ-IDs; finer set at discuss)*
**Success Criteria** (what must be TRUE):

  1. The redesigned home presents a clear hierarchy (live print monitoring primary; quick actions to the high-use surfaces) per an updated `docs/ui_design/` contract, with forward entry points for the not-yet-built features (greyed/capability-gated until present)
  2. Portrait + landscape both honored via the Focus/Field/Gutter grammar; sacred aspect ratios + ratio-only sizing preserved
  3. The high-churn render surfaces (temp sparkline/graph, status) keep their measured Adreno-320 performance — no regression vs the current home
  4. Behavior-preserving for the core monitor loop, verified on-device
  5. Z-babystep conditional control: appears on Print-Status only during the early first-layer window (~first 10 layers), nudges live Z offset (closer/away) confirmed by the `homing_origin[2]` flip, and is session-only; proven on a real first layer on the Ender 5 Plus

**Plans**: TBD
**UI hint**: yes
**Research note**: STANDARD — design rework against the existing UI LAW (now sitting on the Phase-15 theme system); no new backend.

### Phase 17: Fine-Tune / Live-Adjust Panel

**Goal**: Wire the currently-stubbed "Tune" button into a real live-adjustment surface — the things a user reaches for to rescue/tune a print without touching the browser — organized by **failure-mode** into two categories (the user's mental model, 17-CONTEXT D-01): **Motion** (speed factor M220; acceleration M204/SET_VELOCITY_LIMIT; max velocity; square-corner-velocity) and **Extrusion** (flow/extrusion factor M221; pressure advance SET_PRESSURE_ADVANCE; firmware retraction SET_RETRACTION where `[firmware_retraction]` exists; part-cooling fan M106). Capability-gated by the Phase-6 matrix; each control is a keyboard-free scrubber (Phase-3 primitive) that dispatches through the shared command primitive and reflects the resulting `gcode_move`/`toolhead`/`extruder`/`fan` state flip (the Move/Extrude model, no ConfirmGuard). Always-available (not print-gated); per-control reset (speed/flow→100%, limits+PA→config default). Temps/layer/progress are clickable readouts (temps jump to the Temp panel); Z babystep and Pause/Resume live elsewhere. The exact on-screen layout + entry affordance ride on the Phase-15 theme system + Phase-16 home redesign.
**Depends on**: Phase 16
**Requirements**: *(new TUNE-* family — defined at phase planning)*
**Success Criteria** (what must be TRUE):

  1. The user can live-adjust the Motion set (speed factor; and the motion limits accel/max-velocity/SCV) and the Extrusion set (flow factor; pressure advance; part-cooling fan) — each via the keyboard-free scrubber — with the change confirmed by the resulting printer-object state flip, not a bare ack
  2. Every control is capability-gated: it appears only when the active printer exposes its object (e.g. firmware retraction only with `[firmware_retraction]`, fan only with a part-cooling `fan`); absent tunables are hidden, not shown disabled
  3. Controls are safe and bounded (clamped ranges — flow 50–150%, speed 25–200%, etc.; no raw keyboard), always-available, with per-control reset, reachable from the Print-Status Tune entry point
  4. Proven on a real in-progress print on the Ender 5 Plus: change speed/flow (and a motion-limit + pressure advance) mid-print and observe the effect

**Plans**: TBD
**UI hint**: yes
**Research note**: STANDARD — M220 / M221 / M204 / SET_VELOCITY_LIMIT / SET_PRESSURE_ADVANCE / SET_RETRACTION / SET_FAN_SPEED verified against Klipper docs; reuses the scrubber + command primitives; speed_factor/extrude_factor already in PrinterState, the rest (pressure_advance, fan.speed, toolhead limits, firmware_retraction) are new reducer fields. See 17-CONTEXT.md.

### Phase 18: Output Controls — Fans, Lights & Generic Pins

**Goal**: A dedicated page to control the printer's auxiliary outputs without the browser — `[fan_generic]` aux/part fans, `[output_pin]` switches (enclosure power, chamber-heater enable, etc.), and `[led]`/`[neopixel]` lighting where present. Capability-gated by the Phase-6 matrix; each output's current value comes from the central subscribe and is set through the shared command primitive. Slots into the redesigned home/Settings from Phases 15–16.
**Depends on**: Phase 17
**Requirements**: *(new OUT-* family — defined at phase discuss)*
**Success Criteria** (what must be TRUE):

  1. User sees every controllable output the active printer exposes (generic fans, output pins, LEDs) — and nothing it doesn't — sourced from `printer.objects.list` + capability gating
  2. User can set fan speeds (0–100%) and toggle/PWM output pins and set LEDs via keyboard-free controls, with the new value confirmed by the object state flip
  3. Read-only or absent outputs degrade gracefully; no control sends a command the printer can't accept
  4. Proven live against the real printers' actual fan/pin/LED configuration

**Plans**: TBD
**UI hint**: yes
**Research note**: STANDARD — SET_FAN_SPEED / SET_PIN / SET_LED + the corresponding status objects verified against the Klipper config reference.

### Phase 19: System Information Page

**Goal**: A read-only, at-a-glance page for the printer host + Klipper/Moonraker system state — `machine.system_info` / `machine.proc_stats` / `server.info` / version info: host CPU/memory, CPU temp + throttle, uptime, distro, Klipper/Moonraker versions, network, and disk usage. No control surface — a diagnostics/health view that reuses the central subscribe and the existing data-table/render primitives.
**Depends on**: Phase 18
**Requirements**: *(new SYS-* family — defined at phase discuss)*
**Success Criteria** (what must be TRUE):

  1. The page shows host system info (CPU/mem/temp/throttle/uptime/distro), Klipper + Moonraker versions, and disk/network status sourced from Moonraker machine/server endpoints
  2. Live values (proc stats, temps) update at a sane throttled cadence via the central subscribe — no dedicated polling loop (honors the Phase-13 cadence contract)
  3. Missing/unsupported fields degrade gracefully ("—"); the page never blocks or crashes on a sparse/older Moonraker
  4. Verified against both real printers (different SBCs: RPi 4 vs RockPro64)

**Plans**: TBD
**UI hint**: yes
**Research note**: STANDARD — machine.system_info / proc_stats / server.info shapes partly captured already in `docs/moonraker-capabilities.md`; confirm fields against the Moonraker API.

### Phase 20: WebRTC Camera Streaming

**Goal**: Real camera support for the project's OWN hardware. Both target printers expose WebRTC-only camera stacks (go2rtc / camera-streamer / MediaMTX), so Phase 10's MJPEG path — though correct and fixture-proven — never renders a live frame on them. This phase adds a WebRTC client (negotiated via the camera's WHEP/go2rtc endpoint, enumerated through Moonraker `/server/webcams/list`) and presents the low-latency stream on-device, EXTENDING Phase 10's webcam plumbing/rung-ladder rather than replacing it. Mindful of the Adreno-320 floor: hardware-accelerated decode where available, and the feature stays amber-flagged/perf-gated like the existing camera BETA.
**Depends on**: Phase 19
**Requirements**: *(CAM-* WebRTC extension — defined at phase discuss; continues the CAM-01 lineage)*
**Success Criteria** (what must be TRUE):

  1. The app negotiates and renders a WebRTC camera stream from a go2rtc/camera-streamer source enumerated via Moonraker, reusing the Phase-10 webcam selection/rung-ladder
  2. Live low-latency video shows on-device with correct aspect and no frozen frames, falling back to the existing MJPEG/snapshot rungs when WebRTC isn't offered
  3. The decode respects the Adreno-320 floor (hardware decode where possible; perf-gated/amber-flagged) and releases cleanly on screen exit (no leaked PeerConnection/codec)
  4. Proven live on the real Ender 5 Plus and/or Ender 3 WebRTC camera that the MJPEG path could not display

**Plans**: TBD
**UI hint**: yes
**Research note**: DEEPER — WebRTC on API-23 / Adreno-320 is heavy (the `org.webrtc`/libwebrtc footprint, WHEP/go2rtc signaling, hardware-decoder selection); the decode path + library size MUST be validated against the floor before committing (this is exactly why it was deferred from Phase 10).

### Phase 21: Release Hardening & Ship — Always-On, Lifecycle & Signed APK

**Goal**: Cross the gap from "works in dev" to "works unattended on a wall for 14 hours" — and ship the WHOLE project at once (single-milestone release). Absorbs the deferred print-loop **robustness** from the old Job-Status phase — reconnect print-state resync and process-death recovery (verified against a real in-progress print) — and pairs it with always-on appliance hardening (full Doze survival, burn-in protection), a LIGHT final whole-app UI-conformance re-sweep of the late surfaces against `docs/ui_design/` LAW (the conformance net moved to Phase 15 for the existing surfaces; this catches drift in 16–20's new screens), the full "looks done but isn't" checklist against the complete app (now including calibration/webcam/spool/prompt surfaces), and the signed, R8-minified APK sideloadable via GitHub Releases onto a real Nexus 7. Explicitly a verification-and-release phase.
**Depends on**: Phase 20
**Requirements**: PKG-01, PKG-03
**Success Criteria** (what must be TRUE):

  1. **Print-loop robustness (deferred from Job Status):** yank Wi-Fi mid-print and restore it, and the print surface restores correct (non-stale) state from a fresh `objects.query` rather than lying about a finished/paused print (reconnect resync, re-exercising CONN-04 against a live print); relaunching the app mid-print restores the correct state from a fresh query (process-death recovery, PKG-03)
  2. **Full Doze/always-on survival (PKG-03):** after the tablet sits unplugged and screen-off for 20+ minutes, the connection is still alive or cleanly resyncs (battery-optimization exemption + foreground service + first-run setup checklist for Wi-Fi-sleep), and `FLAG_KEEP_SCREEN_ON` holds the print-monitoring surface awake
  3. A burn-in screensaver (dim overlay with wake-on-tap) protects the panel without stalling reconnection
  4. **Light final UI-conformance sweep:** the late surfaces (Phases 16–20) pass the per-screen conformance checklist against `docs/ui_design/` LAW + the Phase-15 theme system (token purity / button-intent / Focus-Field-Gutter / ≥64px targets / `fsSp` scale / dark-light-custom) on flox in both orientations — no whole-app re-audit, just the screens added after Phase 15
  5. The full PITFALLS "looks done but isn't" checklist passes against the COMPLETE app (cleartext on API 23, reconnect resync, Klippy shutdown routing, capability gating on a differently-configured printer, confirm coverage, Doze survival, on-device smoothness, thumbnail/webcam edge cases, process-death recovery)
  6. A signed, R8-shrunk release APK builds (CI-signed via `apksigner`/GitHub Actions), installs cleanly, and runs on a real Nexus 7 2013 — published as a GitHub Release asset with a checksum

**Plans**: TBD
**UI hint**: yes
**Research note**: STANDARD — reconnect-resync path already built (Phase 2 CONN-04); Doze/battery-optimization exemption, R8 shrinking, and APK signing are all well-documented; the conformance re-sweep audits against the existing UI LAW + `reference/hifi.css`.

## Progress

**Execution Order:**
Originally numeric 1 → … → 14. **Revised 2026-06-03 (reliability promotion):** Phase 13 pulled ahead of
the remaining feature phases — 1 → … → 9 → **13 (PROMOTED)** → 10 → 11 → 12 — so the connection/data-model
standardization + session-layer reliability fixes landed before more screens were built on the pragmatic
backend. **Expanded 2026-06-04 (8 new phases):** with Phases 1–11 + 13 done, eight phases were added before
ship (12 → 14 Multi-Printer → Fine-Tune → Output → WebRTC → System Info → Home redesign → Settings redesign →
UI conformance → Release). **Reordered + renumbered 2026-06-05 (design-foundation-first):** with Phases 1–14
done, the back half was resequenced so the visual foundation (theme system + Settings, then Home) is locked
BEFORE the remaining feature surfaces — so features slot into the final design instead of forcing a later
redesign (avoid-future-rework). The old Settings-Redesign (20) + Final-Conformance (21) MERGED into the new
**Phase 15 (Theme System & Settings Redesign)** since the parallel theme work needs the new Settings UI to
test against; the final conformance net folds a LIGHT late-surface sweep into Ship. Phases renumbered cleanly
(no future phase dirs existed yet): **15 Theme+Settings → 16 Home redesign → 17 Fine-Tune → 18 Output →
19 System Info → 20 WebRTC → 21 Release & Ship (LAST)**. 8 phases → 7 (22 → 21 total). Old→new map:
21+20→15, 19→16, 15→17, 16→18, 18→19, 17→20, 22→21. PKG-01/03 remapped 22 → 21.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Platform Gate — Toolkit Benchmark, Cleartext Smoke Test & Scaffold | 4/4 | Complete    | 2026-05-30 |
| 2. Connection & State Foundation | 4/4 | Complete    | 2026-05-30 |
| 3. Design System & Theming Foundation | 8/8 | Complete   | 2026-06-01 |
| 4. Service, Shell, Settings & Print-Status Home | 8/8 | Complete   | 2026-06-01 |
| 5. Core Print-Control Panels — Temperature, Move, Extrude | 11/11 | Complete    | 2026-06-01 |
| 6. Command Reference & Capability Matrix | 5/5 | Complete    | 2026-06-04 |
| 7. Files & Print Control — Core Print-Loop Gate | 6/6 | Complete    | 2026-06-04 |
| 8. Macros & Console — Functional-Core Complete | 7/7 | Complete    | 2026-06-02 |
| 9. Calibration & Maintenance | 7/7 | Complete    | 2026-06-03 |
| 10. Webcam Streaming | 8/8 | Complete    | 2026-06-04 |
| 11. Spool Management — Spoolman + Camera QR | 9/9 | Complete   | 2026-06-04 |
| 12. Macro Prompt Protocol | 5/5 | Complete   | 2026-06-04 |
| 13. Optimization, Network Efficiency & End-to-End Reliability | 5/5 | Complete    | 2026-06-04 |
| 14. Multi-Printer Switching | 6/6 | Complete    | 2026-06-05 |
| 15. Theme System & Settings Redesign | 7/7 | Complete    | 2026-06-05 |
| 16. Home / Print-Status Redesign | 0/TBD | Not started | - |
| 17. Fine-Tune / Live-Adjust Panel | 0/TBD | Not started | - |
| 18. Output Controls — Fans, Lights & Generic Pins | 0/TBD | Not started | - |
| 19. System Information Page | 0/TBD | Not started | - |
| 20. WebRTC Camera Streaming | 0/TBD | Not started | - |
| 21. Release Hardening & Ship — Always-On, Lifecycle & Signed APK | 0/TBD | Not started | - |

## Future Milestones (post-v1)

The 21 phases above are the **v1 milestone** (ships once at Phase 21). Subsequent milestones are seeded
here and formalized via `/gsd-new-milestone` when v1 ships — not planned in detail yet.

### v2 — Beyond the Functional Core

- **Phase v2.1: Home Assistant Integration** *(first v2 phase)* — Bring Home Assistant onto the printer-side
  wall tablet. Direction TBD at milestone kickoff, but the likely surface: the tablet doubles as a small HA
  control panel (printer-adjacent entities — the printer's smart-plug/power, enclosure/room sensors, lights,
  fans) and/or the printer's live state is exposed to HA. Moonraker already ships an `mqtt` component (present
  on the Ender 5 Plus), so MQTT ↔ HA is a natural transport; HA's REST/WebSocket API is the alternative.
  Scope (panel-in-Dinghy vs Dinghy-state-to-HA vs both), auth, and discovery to be decided at v2 kickoff.
  *(Matthew is deep in Home Assistant — see the home-automation context in CLAUDE.md.)*

- **Mid-print object exclusion (`EXCLUDE_OBJECT` / cancel current object)** *(wishlist, deferred from Phase 17
  discuss 2026-06-05)* — cancel one failed part on a multi-object plate and let the rest finish. Destructive;
  needs a distinct UI (object picker / plate map + confirm guard) and depends on `[exclude_object]` plus the
  slicer emitting labeled objects (`exclude_object.objects/current_object/excluded_objects`). It's a "print
  action," not a tune — so it sits outside the Fine-Tune scrubber model and was parked here rather than v1.

*(WebRTC camera, the fine-tune panel, and multi-printer switching were PROMOTED into the v1 roadmap on
2026-06-04 — now Phases 20, 17, and 14 respectively after the 2026-06-05 reorder — so they are no longer v2
backlog. Remaining v2 phases — e.g. the remaining v2 reqs in REQUIREMENTS.md — to be added as the v2
milestone is scoped.)*
