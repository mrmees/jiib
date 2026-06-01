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
as the perf FLOOR, not the only target.) Then panels ship in daily-driver order: manual control
(Temperature/Move/Extrude),
then **Files / Print**, then **Job Status** — the *core print-loop gate* of "drive a real print
start-to-finish without the browser." Macros/Console close out the *v1 functional-core complete*
gate, and the journey ends with appliance hardening: Doze survival, burn-in protection,
process-death recovery, and a signed sideloadable release APK.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Platform Gate — Toolkit Benchmark, Cleartext Smoke Test & Scaffold** - The Compose-vs-Views on-device go/no-go benchmark (synthetic 2–4 Hz source), a cleartext `ws://`/`http://` smoke test on a real API-23 device, the pinned `libs.versions.toml` catalog, and the build scaffold — the gate that must close before panel architecture begins (completed 2026-05-30)
- [x] **Phase 2: Connection & State Foundation** - Resilient Moonraker websocket, diff-merged single-source-of-truth `PrinterState`, capability detection, reconnect-with-resync handshake, optional auth, and JSON-RPC id correlation — proven against a mock socket and a real Ender 5 Plus via a static dev config, still no panels (completed 2026-05-30)
- [x] **Phase 3: Design System & Theming Foundation** - The reusable UI substrate per `docs/ui_design/`: semantic-token theming (dark/light/custom) + S/M/L text size, the Focus/Field/Gutter responsive grammar (portrait + landscape), the outline-led control language, and the core components (Confirm guard, single-setting scrubber page, severity toast, progress-ring + line-graph render primitives) every later screen inherits (completed 2026-05-31)
- [x] **Phase 4: Service, Shell, Settings & Print-Status Home** - Foreground service owning the spine, the Settings screen (connection config + theme + text size + feature toggles), klippy-state-driven splash/home/job routing, swipe-up App Drawer navigation, the Print Status home (render/throttle in anger) with a Stop→Confirm-guard control, and the shared command-dispatch primitive (completed 2026-06-01)
- [ ] **Phase 5: Core Print-Control Panels — Temperature, Move, Extrude** - Manual printer control with capability gating, the confirm policy, the shared command-dispatch primitive, and the full temperature graph (extending the Phase-3 line-graph primitive) — preheat, jog, and extrude on real hardware
- [ ] **Phase 6: Files / Print** - Browse gcode files/folders with off-thread decoded thumbnails, start a print from a confirm guard, and delete files — the lead-in to the core print loop
- [ ] **Phase 7: Job Status — Core Print-Loop Gate** - Live progress/temps/speed/Z, state-adaptive pause/resume/cancel/restart, reconnect print-state resync, and process-death recovery — deepening the Print Status home into the core print-loop gate: drive a real print start-to-finish without the browser
- [ ] **Phase 8: Macros & Console — v1 Functional-Core Complete** - Run gcode_macros with parameter entry and send/inspect raw G-code with severity-colored history — the escape hatches that close the v1 functional-core-complete gate
- [ ] **Phase 9: Hardening & Release — Always-On, Lifecycle & Signed APK** - Full Doze/always-on survival, burn-in screensaver, the "looks done but isn't" checklist, R8 release build, and a signed sideloadable APK shipped via GitHub Releases on a real Nexus 7

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

**Plans**: 10 plans (8 + 2 gap-closure)
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
- [ ] 05-10-PLAN.md — G2+G3: re-run the full handshake on notify_klippy_ready (re-subscribe + re-run one-shot reads) so the FGS-held session self-heals after a Klipper restart without force-stop and configfile/backfill values refresh
**UI hint**: yes — governed by `docs/ui_design/` (LAW): 04-move.png, 07-single-setting.png, 09-temperature-graph.png
**Research note**: STANDARD — Moonraker temperature/move/extrude API verified; patterns established in earlier phases.

### Phase 6: Files / Print

**Goal**: The lead-in to the core print loop. The Files panel lets a user browse their gcode library, inspect thumbnails and metadata, start a print, and delete files — without ever opening a browser. It is built before Job Status because Job Status reuses its thumbnail/metadata fetching. This is the core-print-loop gate lead-in, not the gate itself.
**Depends on**: Phase 5
**Requirements**: FILE-01, FILE-02, FILE-03, FILE-04
**Success Criteria** (what must be TRUE):

  1. User can browse Moonraker gcode files and folders with name/date/size metadata, lazy-fetched as rows scroll into view
  2. User sees thumbnails decoded and downsampled off the UI thread (`inSampleSize`, bounded cache) — no OOM or hang on a large library that includes some thumbnail-less files (graceful fallback)
  3. User can start a print from a confirm dialog showing the thumbnail/details, and the start is confirmed via the resulting `print_stats` state flip rather than assumed from the command ack
  4. User can delete a gcode file through the shared confirm dialog

**Plans**: TBD
**UI hint**: yes
**Research note**: STANDARD — Moonraker file API, thumbnail URL resolution (resolve `relative_path` against server root, small/large variants), and metadata structure verified against official docs.

### Phase 7: Job Status — Core Print-Loop Gate

**Goal**: The primary product value and the **core print-loop gate**. The Job Status panel lets a user monitor a print to completion and control it — pause, resume, cancel, restart — without ever opening a browser. It also completes the print-state resync-after-reconnect path and adds a thin process-death-recovery check (full Doze/always-on hardening is deferred to Phase 9). It deepens the Print Status home surface introduced in Phase 4 (state-adaptive controls, reconnect resync, process-death recovery), reusing the Files thumbnail/metadata fetch.
**Depends on**: Phase 6
**Requirements**: JOB-01, JOB-02, JOB-03, JOB-04, JOB-05
**Success Criteria** (what must be TRUE):

  1. User sees live print progress (filename, percent, thumbnail, elapsed/remaining) plus live temps, speed/flow, and Z while printing, all rendered smoothly at the throttled cadence
  2. The Job Status button set adapts to print state — printing: pause/cancel; paused: resume/cancel; complete/error: restart/files — and both cancel and **restart print** route through the mandatory confirm dialog (PRIM-03 destructive set)
  3. Yank Wi-Fi mid-print, restore it, and Job Status restores correct (non-stale) print state from a fresh `objects.query` rather than lying about a finished or paused print
  4. **Thin lifecycle check:** relaunching the app mid-print restores the correct Job Status from a fresh `objects.query` (process-death recovery) — the full Doze/always-on hardening is verified later in Phase 8
  5. **Core print-loop gate, proven on the Ender 5 Plus:** connect → browse files → start a print → watch Job Status for the full duration → pause, resume, and cancel — all without the browser open

**Plans**: TBD
**UI hint**: yes
**Research note**: STANDARD — Moonraker `print_stats`/`virtual_sdcard`/`display_status`/`gcode_move` objects and the reconnect-resync path verified against official docs.

### Phase 8: Macros & Console — v1 Functional-Core Complete

**Goal**: The escape hatches that prevent the user from ever needing SSH or a browser for anything unusual. Architecturally simple — they consume the event bus and command path already built — and they close the **v1 functional-core-complete gate**. Nothing more; this phase holds the v1 line.
**Depends on**: Phase 7
**Requirements**: MACRO-01, MACRO-02, MACRO-03, CONS-01, CONS-02
**Success Criteria** (what must be TRUE):

  1. User can list and run `gcode_macro` entries discovered from `printer.objects.list`, with generated parameter entry for macros that declare params, and can hide/show which macros appear (underscore-prefixed hidden by default)
  2. User can type and send an arbitrary G-code command from the console using the on-screen keyboard primitive
  3. User sees command/response history with severity coloring (errors `!!`, warnings `//`), backfilled from `server.gcode_store` and updated live via `notify_gcode_response`, with bounded scrollback
  4. **v1 functional-core complete:** after a reconnect, Console history backfills correctly from `server.gcode_store` rather than silently dropping the lines that arrived while disconnected — and with this the full v1 functional core (Connect + Temp/Move/Extrude/Files/JobStatus/Macros/Console) is in place

**Plans**: TBD
**UI hint**: yes
**Research note**: STANDARD — straightforward application of the existing notify event bus and command path.

### Phase 9: Hardening & Release — Always-On, Lifecycle & Signed APK

**Goal**: Cross the gap from "works in dev" to "works unattended on a wall for 14 hours." Harden the always-on appliance behavior (full Doze survival, burn-in protection, process-death recovery), run the full "looks done but isn't" checklist against the complete app, and ship a signed, R8-minified APK sideloadable via GitHub Releases onto a real Nexus 7. This is explicitly a verification-and-release phase.
**Depends on**: Phase 8
**Requirements**: PKG-01, PKG-03
**Success Criteria** (what must be TRUE):

  1. **Full Doze/always-on survival (PKG-03):** after the tablet sits unplugged and screen-off for 20+ minutes, the connection is still alive or cleanly resyncs (battery-optimization exemption + foreground service + first-run setup checklist for Wi-Fi-sleep), `FLAG_KEEP_SCREEN_ON` holds the print-monitoring surface awake, and the app recovers from process death (relaunch mid-print restores correct state from a fresh query)
  2. A burn-in screensaver (dim overlay with wake-on-tap) protects the panel without stalling reconnection
  3. The full PITFALLS "looks done but isn't" checklist passes (cleartext on API 23, reconnect resync, Klippy shutdown routing, capability gating on a differently-configured printer, confirm coverage, Doze survival, on-device smoothness, thumbnail edge cases, process-death recovery)
  4. A signed, R8-shrunk release APK builds (CI-signed via `apksigner`/GitHub Actions), installs cleanly, and runs on a real Nexus 7 2013 — published as a GitHub Release asset with a checksum

**Plans**: TBD
**Research note**: STANDARD — Doze/battery-optimization exemption, R8 shrinking, and APK signing are all well-documented.

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Platform Gate — Toolkit Benchmark, Cleartext Smoke Test & Scaffold | 4/4 | Complete    | 2026-05-30 |
| 2. Connection & State Foundation | 4/4 | Complete    | 2026-05-30 |
| 3. Design System & Theming Foundation | 8/8 | Complete   | 2026-06-01 |
| 4. Service, Shell, Settings & Print-Status Home | 8/8 | Complete   | 2026-06-01 |
| 5. Core Print-Control Panels — Temperature, Move, Extrude | 9/10 | In Progress|  |
| 6. Files / Print | 0/TBD | Not started | - |
| 7. Job Status — Core Print-Loop Gate | 0/TBD | Not started | - |
| 8. Macros & Console — v1 Functional-Core Complete | 0/TBD | Not started | - |
| 9. Hardening & Release — Always-On, Lifecycle & Signed APK | 0/TBD | Not started | - |
