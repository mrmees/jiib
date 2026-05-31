# Requirements: Dinghy Display

**Defined:** 2026-05-30
**Core Value:** Direct, reliable printer control from an old Android tablet over Moonraker — install an APK, point it at the printer, and drive a print.

## v1 Requirements

Requirements for the initial release: the functional print-control core, testable on a real printer early. Each maps to a roadmap phase.

### Connection

- [ ] **CONN-01**: User can enter and save a Moonraker connection (host/IP, port, optional API key) **via the Settings screen** (see SET-01), persisted locally across app restarts
- [x] **CONN-02**: User can supply optional Moonraker auth (API key / trusted-client) used on the websocket and REST calls
- [x] **CONN-03**: App maintains a long-lived Moonraker websocket and auto-reconnects with backoff after drops, without user intervention
- [x] **CONN-04**: On every (re)connect the app performs a full resync handshake (identify → objects.query snapshot → objects.subscribe) so displayed state is never stale after a disconnect
- [x] **CONN-05**: App connects to a plaintext `ws://`/`http://` Moonraker on the LAN from an Android 6 (API 23) device (cleartext network-security policy correctly configured) — proven on real flox hardware (LineageOS 18.1 / API 30) against live Moonraker; exercised the NSC (API-24+) cleartext path. API-23 manifest-flag path config-validated + deferred. See 01-02-SUMMARY.md.
- [x] **CONN-06**: Connection state (connecting / connected / disconnected / error) is always visible to the user

### Printer State Core

- [x] **STATE-01**: App merges Moonraker `notify_status_update` partial diffs into a single retained source-of-truth printer state that panels observe reactively
- [x] **STATE-02**: App detects printer capabilities from `printer.objects.list` and gates UI so only supported controls/panels appear (heaters, extruders, fans, macros, etc.)
- [x] **STATE-03**: High-rate update streams (temperature, position) are throttled (~2–4 Hz) before driving UI to protect weak-GPU rendering
- [x] **STATE-04**: Klippy lifecycle state (ready / printing / startup / error / shutdown) drives the app's primary route (ready→main, printing→job status, startup/error→splash)
- [x] **STATE-05**: App correlates JSON-RPC responses to requests by `id` and never assumes in-order arrival relative to interleaved `notify_*` events

### Design System & Theming

> Governed by `docs/ui_design/` (LAW) — the reusable visual/interaction substrate every later panel inherits.

- [x] **THEME-01**: Semantic-token theme system — dark + light + user-custom themes; every component references role tokens (`--bg/--surface/--text/--accent/--heat/--go/--stop`…), never a raw color, so a theme is a token remap
- [x] **THEME-02**: User text-size setting (S / M / L via the `--fs` multiplier; M is the larger default), persisted
- [ ] **UI-01**: Focus / Field / Gutter responsive layout grammar on one shared tabular grid — portrait (stacked) and landscape (Focus|Field 50/50 + full-width gutter on the same column lines); sacred aspect ratios; ratio-only sizing (no hardcoded px)
- [ ] **UI-02**: Outline-led, touch-first control language (2px outline + glow, ≥64px targets) with button-intent colors (red=stop/cancel/back · green=accept · amber=proceed-at-peril · accent=physical command · white=setting)

### App Shell

- [ ] **SHELL-01**: Minimal-chrome shell — navigation via a **swipe-up full-screen App Drawer** (square destination tiles incl. Settings + a red Power tile); maximum content canvas, no persistent title/status bar; status shown as color on existing elements / contextually, never as global chrome
- [ ] **SHELL-02**: An emergency/stop control on the Print Status (print-monitoring home) surface routes through a full-screen **Confirm guard** (PRIM-03) and triggers the appropriate Moonraker call (`printer.emergency_stop` for the firmware E-stop / cancel for the print); additional control surfaces gain stop affordances as later panels are built
- [ ] **SHELL-03**: A foreground service owns the connection so monitoring survives Activity recreation and screen-off
- [ ] **SHELL-04**: Print Status is the home/landing surface — a compact live dashboard (progress ring + stat grid; heater stats) — and proves the SHARED render/throttle primitive the Temperature graph later extends; function navigation is via the swipe-up App Drawer (not a persistent menu/rail)
- [ ] **SHELL-05**: Splash/initializing surface shows startup/connection status with contextual recovery actions (retry, restart Klipper/firmware where exposed)
- [ ] **SET-01**: Settings screen (conventional Android, keyboard allowed) for baseline app config — Moonraker connection (host/port/key, see CONN-01), theme selection (dark/light/custom), text size (S/M/L), and feature toggles

### UI Primitives

- [ ] **PRIM-01**: Reusable **single-setting page** (full-height fill-bar scrubber + increment/stepper row) for numeric value entry (targets, distances, weights) — replaces an alphanumeric keypad; numeric entry never uses the OS keyboard
- [ ] **PRIM-02**: Text entry uses the **system keyboard, confined to the Settings screen** (host/IP, API key); printer-control surfaces needing alphanumeric input (console, macro params, file search) are triaged per-control toward a keyboard-free pattern (scroll+tap / numeric) where feasible
- [ ] **PRIM-03**: Single mandatory **full-screen Confirm guard** used consistently for the destructive/high-impact set: emergency stop, cancel print, disable motors, restart print, and cooldown while actively printing (destructive=red, positive=green, safe dismiss=neutral)
- [ ] **PRIM-04**: Severity-styled message/toast primitive (info/success/warning/error — color + icon + text, never color alone) for transient feedback and errors
- [ ] **PRIM-05**: Shared command-dispatch primitive enforces explicit network timeouts, an in-flight disabled/busy state, and tap debounce for all Moonraker action calls

### Move

- [ ] **MOVE-01**: User can jog X/Y/Z by a selected distance preset
- [ ] **MOVE-02**: User can home all axes or individual axes
- [ ] **MOVE-03**: User can disable steppers (with confirmation)
- [ ] **MOVE-04**: User sees live toolhead position

### Temperature

- [ ] **TEMP-01**: User sees each heater/sensor's current and target temperature
- [ ] **TEMP-02**: User can set a heater target via presets and via exact keypad entry
- [ ] **TEMP-03**: User can apply preheat presets and cooldown (all heaters off)
- [ ] **TEMP-04**: User sees a temperature history graph (backfilled from `server.temperature_store`)

### Extrude

- [ ] **EXTR-01**: User can extrude/retract a selected distance at a selected speed
- [ ] **EXTR-02**: User can run load/unload filament macros when present
- [ ] **EXTR-03**: User can select the active extruder/tool on multi-extruder printers
- [ ] **EXTR-04**: Extrude/retract controls respect the minimum extrusion temperature

### Files / Print

- [ ] **FILE-01**: User can browse Moonraker gcode files and folders with name/date/size metadata
- [ ] **FILE-02**: User sees gcode thumbnails, decoded/downsampled off the UI thread to stay smooth on 2GB hardware
- [ ] **FILE-03**: User can start a print from a selected file (with a confirmation showing thumbnail/details)
- [ ] **FILE-04**: User can delete a gcode file (with confirmation)

### Job Status

- [ ] **JOB-01**: User sees live print progress (filename, percent, thumbnail, elapsed/remaining time)
- [ ] **JOB-02**: User sees live temperatures, speed/flow, and Z while printing
- [ ] **JOB-03**: User can pause and resume the active print
- [ ] **JOB-04**: User can cancel the active print (with confirmation)
- [ ] **JOB-05**: Job Status button set adapts to print state (printing / paused / complete / error)

### Macros

- [ ] **MACRO-01**: User can list and run printer `gcode_macros`
- [ ] **MACRO-02**: User can enter parameters for macros that declare them before running
- [ ] **MACRO-03**: User can hide/show which macros appear

### Console

- [ ] **CONS-01**: User can send an arbitrary G-code command
- [ ] **CONS-02**: User sees command/response history with severity coloring (errors `!!`, warnings `//`), backfilled from `server.gcode_store` and updated live via `notify_gcode_response`

### Packaging

- [ ] **PKG-01**: Project builds a signed release APK (R8/shrink) that installs and runs on a Nexus 7 2013 (API 23)
- [x] **PKG-02**: A pinned Gradle version catalog (`libs.versions.toml`) governs all dependencies so no library silently raises the minSdk floor
- [ ] **PKG-03**: App survives Doze/always-on (battery-optimization exemption + foreground service + `FLAG_KEEP_SCREEN_ON`) and recovers from process death

## v2 Requirements

Deferred to future release. Tracked but not in the current roadmap.

### Post-Core Panels

- **FINE-01**: Fine-tune panel — babystep Z, speed factor, extrusion factor (high value-per-effort; candidate to pull into late v1)
- **CAM-01**: Camera — native MJPEG/WebRTC stream view (first post-core expansion)
- **BEDM-01**: Bed mesh view/calibrate/profiles
- **BEDL-01**: Bed level / screws tilt adjust
- **ZCAL-01**: Z calibrate workflow
- **SHAPER-01**: Input shaper auto/manual calibration
- **FAN-01**: Fan control panel
- **LED-01**: LED control panel
- **POWER-01**: Moonraker power-device control
- **PINS-01**: Output pin / PWM control
- **LIMIT-01**: Runtime limits (accel/velocity/SCV)
- **PA-01**: Pressure advance / retraction tuning
- **SPOOL-01**: Spoolman spool list + active spool + weight editor
- **EXCL-01**: Exclude-object during print

### Multi-Printer & Platform

- **MULTI-01**: Configure and switch between multiple Moonraker printers
- ~~**PORT-01**: Portrait-optimized layout~~ — **promoted to v1** (2026-05-31) as UI-01 (portrait + landscape responsive grammar)
- **UPD-01**: Moonraker-backed update manager view (where API exposes it)
- **SYS-01**: System telemetry view from Moonraker `machine/system_info`

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| NetworkManager / Wi-Fi management UI | Android owns Wi-Fi; KlipperScreen's DBus/NetworkManager approach doesn't apply |
| Host OS service management (`systemctl` restart, host shutdown/reboot) | This is a printer control surface, not a host sysadmin console |
| `mpv`/external-player camera | The exact host-coupling this app removes; camera will be native in-app |
| Slicing / gcode generation | Absurd on 2GB Tegra hardware; slicing lives on the desktop |
| Uploading gcode files from the tablet | Tablets aren't where slices live; browse/print only |
| Google Play Store distribution | Target hardware lacks current Play Services; sideloaded signed APK instead |
| Full ~34-panel parity in v1 | Deliberately deferred to keep v1 to a testable functional core |
| Coupling to gtk4_klipperscreen | Catalog is reference only; app is independent with no shared code |

## Traceability

Which phases cover which requirements. Populated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| PKG-02 | Phase 1 | Complete |
| CONN-05 | Phase 1 | Complete |
| CONN-02 | Phase 2 | Complete |
| CONN-03 | Phase 2 | Complete |
| CONN-04 | Phase 2 | Complete |
| CONN-06 | Phase 2 | Complete |
| STATE-01 | Phase 2 | Complete |
| STATE-02 | Phase 2 | Complete |
| STATE-03 | Phase 2 | Complete |
| STATE-04 | Phase 2 | Complete |
| STATE-05 | Phase 2 | Complete |
| THEME-01 | Phase 3 | Complete |
| THEME-02 | Phase 3 | Complete |
| UI-01 | Phase 3 | Pending |
| UI-02 | Phase 3 | Pending |
| PRIM-01 | Phase 3 | Pending |
| PRIM-03 | Phase 3 | Pending |
| PRIM-04 | Phase 3 | Pending |
| CONN-01 | Phase 4 | Pending |
| SET-01 | Phase 4 | Pending |
| SHELL-01 | Phase 4 | Pending |
| SHELL-02 | Phase 4 | Pending |
| SHELL-03 | Phase 4 | Pending |
| SHELL-04 | Phase 4 | Pending |
| SHELL-05 | Phase 4 | Pending |
| PRIM-02 | Phase 4 | Pending |
| PRIM-05 | Phase 4 | Pending |
| TEMP-01 | Phase 5 | Pending |
| TEMP-02 | Phase 5 | Pending |
| TEMP-03 | Phase 5 | Pending |
| TEMP-04 | Phase 5 | Pending |
| MOVE-01 | Phase 5 | Pending |
| MOVE-02 | Phase 5 | Pending |
| MOVE-03 | Phase 5 | Pending |
| MOVE-04 | Phase 5 | Pending |
| EXTR-01 | Phase 5 | Pending |
| EXTR-02 | Phase 5 | Pending |
| EXTR-03 | Phase 5 | Pending |
| EXTR-04 | Phase 5 | Pending |
| FILE-01 | Phase 6 | Pending |
| FILE-02 | Phase 6 | Pending |
| FILE-03 | Phase 6 | Pending |
| FILE-04 | Phase 6 | Pending |
| JOB-01 | Phase 7 | Pending |
| JOB-02 | Phase 7 | Pending |
| JOB-03 | Phase 7 | Pending |
| JOB-04 | Phase 7 | Pending |
| JOB-05 | Phase 7 | Pending |
| MACRO-01 | Phase 8 | Pending |
| MACRO-02 | Phase 8 | Pending |
| MACRO-03 | Phase 8 | Pending |
| CONS-01 | Phase 8 | Pending |
| CONS-02 | Phase 8 | Pending |
| PKG-01 | Phase 9 | Pending |
| PKG-03 | Phase 9 | Pending |

**Coverage:**
- v1 requirements: 55 total
- Mapped to phases: 55 ✓
- Unmapped: 0 ✓

**Per-phase counts (9-phase roadmap, restructured 2026-05-31):**
- Phase 1 (Platform Gate): 2 — PKG-02, CONN-05
- Phase 2 (Connection & State Foundation): 9 — CONN-02, CONN-03, CONN-04, CONN-06, STATE-01..05
- Phase 3 (Design System & Theming Foundation): 7 — THEME-01, THEME-02, UI-01, UI-02, PRIM-01, PRIM-03, PRIM-04
- Phase 4 (Service, Shell, Settings & Print-Status Home): 9 — CONN-01, SET-01, SHELL-01..05, PRIM-02, PRIM-05
- Phase 5 (Temp/Move/Extrude): 12 — TEMP-01..04, MOVE-01..04, EXTR-01..04
- Phase 6 (Files / Print): 4 — FILE-01..04
- Phase 7 (Job Status): 5 — JOB-01..05
- Phase 8 (Macros/Console): 5 — MACRO-01..03, CONS-01..02
- Phase 9 (Hardening/Release): 2 — PKG-01, PKG-03

---
*Requirements defined: 2026-05-30*
*Last updated: 2026-05-31 — scope broadened per `docs/ui_design/`: added THEME-01/02, UI-01/02, SET-01 (5 new → 55 v1 reqs); revised CONN-01/SHELL-01..05/PRIM-01..04; restructured to 9 phases (new Phase 3 = Design System & Theming Foundation, shell → Phase 4). Prior: 2026-05-30 Codex cross-AI review (8-phase splits, STATE-05/PRIM-05/PKG-03).*
