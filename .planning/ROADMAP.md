# Roadmap: Dinghy Display

## Overview

Dinghy Display turns a retired Nexus 7-class Android tablet into a self-contained Klipper
touchscreen that talks straight to Moonraker — no Linux host, no VNC, no display-server glue.
The build is infrastructure-first by deliberate choice: the load-bearing connection/state spine
(resilient websocket → diff-merged single-source-of-truth `PrinterState` → capability gating)
is built and proven against a mock websocket *and* a real Ender 5 Plus before a single panel
exists. On top of that spine we layer the foreground service, state-driven shell, and the UI
primitives every panel depends on, then build panels in daily-driver order: manual control
(Temperature/Move/Extrude), then the core value loop (Files/Print → Job Status — the v1 exit
gate of "drive a real print without the browser"), then the escape hatches (Macros/Console).
The journey ends with appliance hardening: Doze survival, burn-in protection, and a signed
sideloadable release APK. The single most consequential early decision — Compose vs. Views on
a Tegra GPU with no Baseline Profile support — is resolved by an on-device benchmark inside the
Foundation phase, because it gates all high-churn panel architecture.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Foundation — Connection, State & On-Device Toolkit Spike** - Resilient Moonraker websocket, diff-merged PrinterState, capability detection, cleartext-on-API-23, and the Compose-vs-Views go/no-go benchmark — proven against a mock socket and a real printer before any UI
- [ ] **Phase 2: Service, Shell & State-Driven Navigation** - Foreground service, persisted connection config, splash/main/job state-driven routing, always-reachable Emergency Stop, and the UI primitives (keypad, keyboard, confirm dialog, toast) + confirm policy + notify throttling every panel depends on
- [ ] **Phase 3: Core Print-Control Panels — Temperature, Move, Extrude** - Manual printer control with capability gating, the confirm policy, and the first sustained high-rate render (temp graph) — preheat, jog, and extrude on real hardware
- [ ] **Phase 4: Files/Print & Job Status — The Core Value Loop** - Browse gcode with thumbnails, start a print, and watch live job status with pause/resume/cancel — the v1 exit gate: drive a real print start-to-finish without the browser
- [ ] **Phase 5: Macros & Console** - Run gcode_macros with parameter entry and send/inspect raw G-code with severity-colored history — the escape hatches that complete v1 scope
- [ ] **Phase 6: Hardening & Release — Always-On, Lifecycle & Signed APK** - Doze survival, burn-in screensaver, process-death recovery, R8 release build, and a signed sideloadable APK shipped via GitHub Releases on a real Nexus 7

## Phase Details

### Phase 1: Foundation — Connection, State & On-Device Toolkit Spike
**Goal**: A resilient, fully testable Moonraker connection/state spine — one OkHttp websocket feeding a diff-merged single-source-of-truth `PrinterState` with capability detection and reconnect-with-resync — proven on a mock socket and a real Ender 5 Plus, with the UI-toolkit question settled by an on-device benchmark. No panels; this is the load-bearing foundation everything else codes against.
**Depends on**: Nothing (first phase)
**Requirements**: CONN-01, CONN-02, CONN-03, CONN-04, CONN-05, CONN-06, STATE-01, STATE-02, STATE-03, STATE-04, PKG-02
**Success Criteria** (what must be TRUE):
  1. `ws://192.168.x.x` connects and receives live `notify_status_update` on a real Android 6 (API 23) device at the shipping targetSdk — cleartext works on actual hardware, not just a modern phone
  2. Yank Wi-Fi mid-stream and the client auto-reconnects with backoff and runs the full `identify → objects.query → objects.subscribe` resync handshake, so displayed state is correct (not stale) after recovery — provable in mock-socket tests replaying golden Ender 5 Plus frames
  3. `notify_status_update` partial diffs merge into one retained `PrinterState` `StateFlow` that survives transient drops, with high-rate streams conflated to ~2–4 Hz at the state layer
  4. Capability detection turns a `printer.objects.list` response into an immutable `Capabilities` model (heaters, extruder count, macros, power, etc.) as a pure, unit-tested function, re-run on every reconnect
  5. **Toolkit go/no-go is recorded:** an on-device release-mode benchmark on the Nexus 7 (scroll a list + render a 2–4 Hz live value, profiled with `gfxinfo`) produces a documented Compose-everywhere vs. hybrid-Views decision that gates all panel architecture
  6. A pinned `gradle/libs.versions.toml` version catalog governs every dependency (minSdk 23 floor protected; pinned to the Compose 1.11 / AGP 8.7.x line)
**Plans**: TBD
**Research note**: Warrants DEEPER phase-specific research before planning — pin down the exact Moonraker `notify_*` set the app subscribes to (`notify_status_update`, `notify_gcode_response`, `notify_klippy_ready/disconnected/shutdown`, `notify_proc_stat_update`) and the auth handshake edge cases (oneshot-token websocket flow, `X-Api-Key`, graceful `401 auth required`). All other patterns verified against official Moonraker docs.

### Phase 2: Service, Shell & State-Driven Navigation
**Goal**: The connection spine becomes a living app: a foreground service hosts it across Activity recreation and screen-off, the Klippy lifecycle (not the user) drives the primary route (ready→main, printing→job, startup/error→splash), and the persistent shell exposes an always-reachable Emergency Stop. The UI primitives, the single mandatory confirm-action policy, and the notify-throttling-into-the-dashboard pattern are all established here so every later panel inherits them.
**Depends on**: Phase 1
**Requirements**: SHELL-01, SHELL-02, SHELL-03, SHELL-04, SHELL-05, PRIM-01, PRIM-02, PRIM-03, PRIM-04
**Success Criteria** (what must be TRUE):
  1. App connects to the Ender 5 Plus, shows a splash during Klippy startup, routes to the main menu on Klippy ready, and routes to job status when a print is active — the connect→splash→main loop works and the route is driven by `klippy_state`, not socket state
  2. The main menu doubles as a launcher and a compact thermal dashboard (heater rows + live temperature graph) that renders smoothly at the throttled ~2–4 Hz cadence on the Nexus 7
  3. Emergency Stop is reachable from the shell on every screen, triggers `printer.emergency_stop`, and is fast-but-deliberate (hold/double-tap) rather than a slow modal
  4. The connection survives Activity recreation (rotation/config change) and the connection/printer status is always visible in the shell chrome; the splash surfaces shutdown/error reason text with recovery actions (retry, firmware/Klipper restart where exposed)
  5. A single confirm-action dialog primitive exists and is wired as the mandatory gate for the destructive set; numeric keypad, on-screen keyboard, and severity-styled toast/message primitives exist and are consumable by panels
**Plans**: TBD
**UI hint**: yes
**Research note**: STANDARD — foreground Service + Doze-aware lifecycle + DataStore + state-driven nav are well-documented Android patterns; no phase research needed.

### Phase 3: Core Print-Control Panels — Temperature, Move, Extrude
**Goal**: The first real panels — manual printer control. These deliberately come before the print-loop panels because they are simpler surfaces that prove the capability-gating model, the confirm-action policy, the in-flight/debounce pattern, and the first sustained high-rate render (the temperature history graph) against real hardware before the higher-complexity Files/Job-Status work.
**Depends on**: Phase 2
**Requirements**: TEMP-01, TEMP-02, TEMP-03, TEMP-04, MOVE-01, MOVE-02, MOVE-03, MOVE-04, EXTR-01, EXTR-02, EXTR-03, EXTR-04
**Success Criteria** (what must be TRUE):
  1. User sees every heater/sensor's current and target temperature, sets a target via presets and exact keypad entry, applies preheat presets and cooldown, and sees a history graph backfilled from `server.temperature_store` — with no janky graph on the Nexus 7 during an active heat
  2. User can jog X/Y/Z by distance preset, home all or individual axes, disable steppers (through the shared confirm dialog), and see live toolhead position — with un-homed axes visibly gated
  3. User can extrude/retract at a selected distance and speed, run load/unload macros when present (and see a "missing macro" popup when not), and select the active tool on multi-extruder printers
  4. Extrude controls respect `min_extrude_temp` (`can_extrude`) and refuse cold extrusion; every panel hides controls the connected printer doesn't support (capability-gated, not disabled-and-confusing)
  5. Provable on the real Ender 5 Plus: preheat → wait for temp → extrude → jog all work end-to-end without the browser open
**Plans**: TBD
**UI hint**: yes
**Research note**: STANDARD — Moonraker temperature/move/extrude API verified; patterns established in earlier phases.

### Phase 4: Files/Print & Job Status — The Core Value Loop
**Goal**: The primary product value and the v1 exit gate. Together these two panels let a user browse gcode, start a print, and monitor it to completion without ever opening a browser. Files is implemented first because Job Status reuses its thumbnail/metadata fetching. This phase also completes the print-state resync-after-reconnect path.
**Depends on**: Phase 3
**Requirements**: FILE-01, FILE-02, FILE-03, FILE-04, JOB-01, JOB-02, JOB-03, JOB-04, JOB-05
**Success Criteria** (what must be TRUE):
  1. User can browse Moonraker gcode files and folders with name/date/size metadata, see thumbnails decoded and downsampled off the UI thread (no OOM or hang on a large library with some thumbnail-less files), start a print from a confirm dialog showing the thumbnail/details, and delete a file with confirmation
  2. User sees live print progress (filename, percent, thumbnail, elapsed/remaining) plus live temps, speed/flow, and Z while printing, all rendered smoothly at the throttled cadence
  3. The Job Status button set adapts to print state — printing: pause/cancel; paused: resume/cancel; complete/error: restart/files — and cancel routes through the confirm dialog
  4. Yank Wi-Fi mid-print, restore it, and Job Status restores correct (non-stale) print state from a fresh `objects.query` rather than lying about a finished or paused print
  5. **v1 exit gate, proven on the Ender 5 Plus:** connect → browse files → start a print → watch Job Status for the full duration → pause, resume, and cancel — all without the browser open
**Plans**: TBD
**UI hint**: yes
**Research note**: STANDARD — Moonraker file API, thumbnail URL resolution, and print-stats objects verified against official docs.

### Phase 5: Macros & Console
**Goal**: The escape hatches that prevent the user from ever needing SSH or a browser for anything unusual. Architecturally simple — they consume the event bus and command path already built — and they complete the v1 functional-core scope. Nothing more; this phase holds the v1 line.
**Depends on**: Phase 4
**Requirements**: MACRO-01, MACRO-02, MACRO-03, CONS-01, CONS-02
**Success Criteria** (what must be TRUE):
  1. User can list and run `gcode_macro` entries discovered from `printer.objects.list`, with generated parameter entry for macros that declare params, and can hide/show which macros appear (underscore-prefixed hidden by default)
  2. User can type and send an arbitrary G-code command from the console using the on-screen keyboard primitive
  3. User sees command/response history with severity coloring (errors `!!`, warnings `//`), backfilled from `server.gcode_store` and updated live via `notify_gcode_response`, with bounded scrollback
  4. After a reconnect, Console history backfills correctly from `server.gcode_store` rather than silently dropping the lines that arrived while disconnected
**Plans**: TBD
**UI hint**: yes
**Research note**: STANDARD — straightforward application of the existing notify event bus and command path.

### Phase 6: Hardening & Release — Always-On, Lifecycle & Signed APK
**Goal**: Cross the gap from "works in dev" to "works unattended on a wall for 14 hours." Harden the always-on appliance behavior (Doze survival, burn-in protection, process-death recovery), run the full "looks done but isn't" checklist against the complete app, and ship a signed, R8-minified APK sideloadable via GitHub Releases onto a real Nexus 7.
**Depends on**: Phase 5
**Requirements**: PKG-01
**Success Criteria** (what must be TRUE):
  1. After the tablet sits unplugged and screen-off for 20+ minutes, the connection is still alive or cleanly resyncs (battery-optimization exemption + foreground service + first-run setup checklist for Wi-Fi-sleep), and `FLAG_KEEP_SCREEN_ON` holds the print-monitoring surface awake
  2. A burn-in screensaver (dim overlay with wake-on-tap) protects the panel without stalling reconnection
  3. Killing the app mid-print and relaunching restores correct Job Status from a fresh query (process-death recovery)
  4. The full PITFALLS "looks done but isn't" checklist passes (cleartext on API 23, reconnect resync, Klippy shutdown routing, capability gating on a differently-configured printer, confirm coverage, Doze survival, on-device smoothness, thumbnail edge cases, process-death recovery)
  5. A signed, R8-shrunk release APK builds (CI-signed via `apksigner`/GitHub Actions), installs cleanly, and runs on a real Nexus 7 2013 — published as a GitHub Release asset with a checksum
**Plans**: TBD
**Research note**: STANDARD — Doze/battery-optimization exemption, R8 shrinking, and APK signing are all well-documented.

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation — Connection, State & On-Device Toolkit Spike | 0/TBD | Not started | - |
| 2. Service, Shell & State-Driven Navigation | 0/TBD | Not started | - |
| 3. Core Print-Control Panels — Temperature, Move, Extrude | 0/TBD | Not started | - |
| 4. Files/Print & Job Status — The Core Value Loop | 0/TBD | Not started | - |
| 5. Macros & Console | 0/TBD | Not started | - |
| 6. Hardening & Release — Always-On, Lifecycle & Signed APK | 0/TBD | Not started | - |
