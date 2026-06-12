# Requirements: Dinghy Display

**Last verified:** 2026-06-11
**Status:** active

**Defined:** 2026-05-30
**Core Value:** Direct, reliable printer control from an old Android tablet over Moonraker — install an APK, point it at the printer, and drive a print.

## v1 Requirements

Requirements for the initial release: the functional print-control core, testable on a real printer early. Each maps to a roadmap phase.

### Connection

- [x] **CONN-01**: User can enter and save a Moonraker connection (host/IP, port, optional API key) **via the Settings screen** (see SET-01), persisted locally across app restarts
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
- [x] **UI-01**: Focus / Field / Gutter responsive layout grammar on one shared tabular grid — portrait (stacked) and landscape (Focus|Field 50/50 + full-width gutter on the same column lines); sacred aspect ratios; ratio-only sizing (no hardcoded px)
- [x] **UI-02**: Outline-led, touch-first control language (2px outline + glow, ≥64px targets) with button-intent colors (red=stop/cancel/back · green=accept · amber=proceed-at-peril · accent=physical command · white=setting)

### App Shell

- [x] **SHELL-01**: Minimal-chrome shell — navigation via a **swipe-up full-screen App Drawer** (square destination tiles incl. Settings + a red Power tile); maximum content canvas, no persistent title/status bar; status shown as color on existing elements / contextually, never as global chrome
- [x] **SHELL-02**: An emergency/stop control on the Print Status (print-monitoring home) surface routes through a full-screen **Confirm guard** (PRIM-03) and triggers the appropriate Moonraker call (`printer.emergency_stop` for the firmware E-stop / cancel for the print); additional control surfaces gain stop affordances as later panels are built
- [ ] **SHELL-03**: A foreground service owns the connection so monitoring survives Activity recreation and screen-off
- [x] **SHELL-04**: Print Status is the home/landing surface — a compact live dashboard (progress ring + stat grid; heater stats) — and proves the SHARED render/throttle primitive the Temperature graph later extends; function navigation is via the swipe-up App Drawer (not a persistent menu/rail)
- [x] **SHELL-05**: Splash/initializing surface shows startup/connection status with contextual recovery actions (retry, restart Klipper/firmware where exposed)
- [x] **SET-01**: Settings screen (conventional Android, keyboard allowed) for baseline app config — Moonraker connection (host/port/key, see CONN-01), theme selection (dark/light/custom), text size (S/M/L), and feature toggles

### UI Primitives

- [x] **PRIM-01**: Reusable **single-setting page** (full-height fill-bar scrubber + increment/stepper row) for numeric value entry (targets, distances, weights) — replaces an alphanumeric keypad; numeric entry never uses the OS keyboard
- [x] **PRIM-02**: Text entry uses the **system keyboard, confined to the Settings screen** (host/IP, API key); printer-control surfaces needing alphanumeric input (console, macro params, file search) are triaged per-control toward a keyboard-free pattern (scroll+tap / numeric) where feasible
- [x] **PRIM-03**: Single mandatory **full-screen Confirm guard** used consistently for the destructive/high-impact set: emergency stop, cancel print, disable motors, restart print, and cooldown while actively printing (destructive=red, positive=green, safe dismiss=neutral)
- [x] **PRIM-04**: Severity-styled message/toast primitive (info/success/warning/error — color + icon + text, never color alone) for transient feedback and errors
- [x] **PRIM-05**: Shared command-dispatch primitive enforces explicit network timeouts, an in-flight disabled/busy state, and tap debounce for all Moonraker action calls

### Move

- [x] **MOVE-01**: User can jog X/Y/Z by a selected distance preset
- [x] **MOVE-02**: User can home all axes or individual axes
- [x] **MOVE-03**: User can disable steppers (with confirmation)
- [x] **MOVE-04**: User sees live toolhead position

### Temperature

- [x] **TEMP-01**: User sees each heater/sensor's current and target temperature
- [x] **TEMP-02**: User can set a heater target via presets and via exact keypad entry
- [x] **TEMP-03**: User can apply preheat presets and cooldown (all heaters off)
- [x] **TEMP-04**: User sees a temperature history graph (backfilled from `server.temperature_store`)

### Extrude

- [x] **EXTR-01**: User can extrude/retract a selected distance at a selected speed
- [x] **EXTR-02**: User can run load/unload filament macros when present
- [x] **EXTR-03**: User can select the active extruder/tool on multi-extruder printers
- [x] **EXTR-04**: Extrude/retract controls respect the minimum extrusion temperature

### Files / Print

- [x] **FILE-01**: User can browse Moonraker gcode files and folders with name/date/size metadata
- [x] **FILE-02**: User sees gcode thumbnails, decoded/downsampled off the UI thread to stay smooth on 2GB hardware
- [x] **FILE-03**: User can start a print from a selected file (with a confirmation showing thumbnail/details)
- [x] **FILE-04**: User can delete a gcode file (with confirmation)

### Job Status

- [x] **JOB-01**: User sees live print progress (filename, percent, thumbnail, elapsed/remaining time)
- [x] **JOB-02**: User sees live temperatures, speed/flow, and Z while printing
- [x] **JOB-03**: User can pause and resume the active print
- [x] **JOB-04**: User can cancel the active print (with confirmation)
- [x] **JOB-05**: Job Status button set adapts to print state (printing / paused / complete / error)

### Macros

- [x] **MACRO-01**: User can list and run printer `gcode_macros`
- [x] **MACRO-02**: User can enter parameters for macros that declare them before running
- [x] **MACRO-03**: User can hide/show which macros appear

### Console

- [ ] ~~**CONS-01**: User can send an arbitrary G-code command~~ — **DEFERRED (2026-06-02):** the console is read-only for now (the project's goal is touch input for typical functions, not a troubleshooting terminal). Text-send pulled from Phase 8, unscheduled — revisit in a later phase. See `phases/08-macros-console-functional-core-complete/08-CONTEXT.md`.
- [x] **CONS-02**: User sees command/response history with severity coloring (errors `!!`, warnings `//`), backfilled from `server.gcode_store` and updated live via `notify_gcode_response` — read-only feed with opt-in noise filters (Phase 8)

### System Information

> Coined at Phase-20 planning (2026-06-08) — the SYS-* family for the read-only printer-host health/diagnostics page. Promotes the former v2 umbrella `SYS-01` (System telemetry view from Moonraker `machine/system_info`) into the active roadmap, narrowed to host-health-only (versions/disk/network OUT per ROADMAP SC-1 rescope D-13). All read-only; no control surface. Active-printer scoped.

- [x] **SYS-01**: User sees host **identity** — CPU model/cores, total RAM, distro name+version, kernel — sourced from `machine.system_info` (one-shot per-handshake seed; empty `cpu_info.model` on the RockPro64 degrades to `—` / a distro-name host label)
- [x] **SYS-02**: User sees **live host load** — CPU load %, memory used/available, CPU temp — updating at a throttled display cadence via the free 1 Hz `notify_proc_stat_update` push channel (consumed, not a dedicated poll loop — Phase-13 cadence contract, SC-2)
- [x] **SYS-03**: User sees a **host-health summary** — hostname/host model, CPU temp, host uptime, and a shape-coded **health chip** (Phase-15.1 status shapes) that is throttle-authoritative on Pi hosts (`throttled_state` bits) and CPU-temp-fallback on non-Pi hosts (D-12: warn ≥70 °C, caution ≥80 °C); throttle + uptime sourced from a one-shot `machine.proc_stats` query (both fields are OMITTED from the push)
- [x] **SYS-04**: Missing/unsupported fields **degrade to `—`** (labeled row stays present, layout stable across both SBCs); the page never blocks or crashes on a sparse or older Moonraker, an empty-string field, or a `null` `throttled_state` (SC-3)
- [x] **SYS-05**: Verified on-device against **both** real printers (RPi 4 / Ender 5 Plus and RockPro64 / Ender 3) — identity rows, live load cadence, CPU temp, and the health-chip shape read correctly and the chip means the same thing on both (SC-4)

### Packaging

- [ ] **PKG-01**: Project builds a signed release APK (R8/shrink) that installs and runs on a Nexus 7 2013 (API 23)
- [x] **PKG-02**: A pinned Gradle version catalog (`libs.versions.toml`) governs all dependencies so no library silently raises the minSdk floor
- [ ] **PKG-03**: App survives Doze/always-on (battery-optimization exemption + foreground service + `FLAG_KEEP_SCREEN_ON`) and recovers from process death

## v2 Requirements

Deferred to future release. Tracked but not in the current roadmap.

### Post-Core Panels

- **FINE-01**: Fine-tune panel — babystep Z, speed factor, extrusion factor (high value-per-effort; candidate to pull into late v1)
- ~~**CAM-01**: Camera — native MJPEG/WebRTC stream view~~ — **promoted to active roadmap (2026-06-01) → Phase 10 (Webcam Streaming, MJPEG-only; WebRTC still deferred)**
- ~~**BEDM-01**: Bed mesh view/calibrate/profiles~~ — **promoted (2026-06-01) → Phase 9 (Calibration & Maintenance)**
- ~~**BEDL-01**: Bed level / screws tilt adjust~~ — **promoted (2026-06-01) → Phase 9 (Calibration & Maintenance; `SCREWS_TILT_CALCULATE`/`Z_TILT_ADJUST`)**
- ~~**ZCAL-01**: Z calibrate workflow~~ — **promoted (2026-06-01) → Phase 9 (Calibration & Maintenance)**
- ~~**PROMPT-01**: Render interactive macro dialogs from `// action:prompt_*` lines (klipper-macro-prompt-protocol)~~ — **COMPLETE → Phase 12 (Macro Prompt Protocol); on-device UAT PASSED 2026-06-04 (flox + live E3, all 4 gates)**
- **SHAPER-01**: Input shaper auto/manual calibration
- **FAN-01**: Fan control panel
- **LED-01**: LED control panel
- **POWER-01**: Moonraker power-device control
- **PINS-01**: Output pin / PWM control
- **LIMIT-01**: Runtime limits (accel/velocity/SCV)
- **PA-01**: Pressure advance / retraction tuning
- ~~**SPOOL-01**: Spoolman spool list + active spool + weight editor~~ — **promoted (2026-06-01) → Phase 11 (Spool Management; + the headline tablet-camera QR-scan-to-assign flow)**
- **EXCL-01**: Exclude-object during print

### v2 Milestone — Beyond the Functional Core (seeded 2026-06-01)

- **HAINT-01** *(first v2 phase — Home Assistant Integration)*: Bring Home Assistant onto the wall tablet — surface/control printer-adjacent HA entities (power/smart-plug, enclosure/room sensors, lights, fans) and/or expose Dinghy/printer state to HA. Transport TBD (Moonraker `mqtt` ↔ HA, or HA REST/WebSocket API). Scope/auth/discovery decided at v2 kickoff via `/gsd-new-milestone`.

### Multi-Printer & Platform

- **MULTI-01**: Configure and switch between multiple Moonraker printers — ✅ **validated in Phase 14 (Multi-Printer Switching), 2026-06-05** (managed DataStore-persisted profiles + Settings CRUD + Devices switcher; clean `activeConfig`→service spine rebind on switch; instrumented process-death survival test + live two-printer UAT on flox + E5/E3 PASSED)
- ~~**PORT-01**: Portrait-optimized layout~~ — **promoted to v1** (2026-05-31) as UI-01 (portrait + landscape responsive grammar)
- **UPD-01**: Moonraker-backed update manager view (where API exposes it)
- ~~**SYS-01**: System telemetry view from Moonraker `machine/system_info`~~ — **promoted to active roadmap (2026-06-08) → Phase 20 (System Information Page); decomposed into the v1 SYS-01..SYS-05 family above (host-health-only scope)**

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
| UI-01 | Phase 3 | Complete |
| UI-02 | Phase 3 | Complete |
| PRIM-01 | Phase 3 | Complete |
| PRIM-03 | Phase 3 | Complete |
| PRIM-04 | Phase 3 | Complete |
| CONN-01 | Phase 4 | Complete |
| SET-01 | Phase 4 | Complete |
| SHELL-01 | Phase 4 | Complete |
| SHELL-02 | Phase 4 | Complete |
| SHELL-03 | Phase 4 | Pending |
| SHELL-04 | Phase 4 | Complete |
| SHELL-05 | Phase 4 | Complete |
| PRIM-02 | Phase 4 | Complete |
| PRIM-05 | Phase 4 | Complete |
| TEMP-01 | Phase 5 | Complete |
| TEMP-02 | Phase 5 | Complete |
| TEMP-03 | Phase 5 | Complete |
| TEMP-04 | Phase 5 | Complete |
| MOVE-01 | Phase 5 | Complete |
| MOVE-02 | Phase 5 | Complete |
| MOVE-03 | Phase 5 | Complete |
| MOVE-04 | Phase 5 | Complete |
| EXTR-01 | Phase 5 | Complete |
| EXTR-02 | Phase 5 | Complete |
| EXTR-03 | Phase 5 | Complete |
| EXTR-04 | Phase 5 | Complete |
| FILE-01 | Phase 7 | Complete |
| FILE-02 | Phase 7 | Complete |
| FILE-03 | Phase 7 | Complete |
| FILE-04 | Phase 7 | Complete |
| JOB-01 | Phase 7 | Complete |
| JOB-02 | Phase 7 | Complete |
| JOB-03 | Phase 7 | Complete |
| JOB-04 | Phase 7 | Complete |
| JOB-05 | Phase 7 | Complete |
| MACRO-01 | Phase 8 | Complete |
| MACRO-02 | Phase 8 | Complete |
| MACRO-03 | Phase 8 | Complete |
| CONS-01 | Deferred | Pulled from Phase 8 (2026-06-02) — console read-only, text-send unscheduled |
| CONS-02 | Phase 8 | Complete |
| BEDM-01 | Phase 9 | In Progress (Wave 0: fixtures + RED scaffolds landed 09-01; stays Pending until Phase-9 on-device UAT closes) |
| BEDL-01 | Phase 9 | In Progress (Wave 0: fixtures + RED scaffolds landed 09-01; stays Pending until Phase-9 on-device UAT closes) |
| ZCAL-01 | Phase 9 | In Progress (Wave 0: fixtures + RED scaffolds landed 09-01; stays Pending until Phase-9 on-device UAT closes) |
| CALIB-01 (hub/gating; ⊂ BEDL-01/BEDM-01/ZCAL-01) | Phase 9 | Planned |
| CALIB-02 (screws-tilt; ⊂ BEDL-01) | Phase 9 | Planned |
| CALIB-03 (Z-tilt/QGL; ⊂ BEDL-01) | Phase 9 | Planned |
| CALIB-04 (bed-mesh; ⊂ BEDM-01) | Phase 9 | Planned |
| CALIB-05 (PROBE_CALIBRATE; ⊂ ZCAL-01) | Phase 9 | Planned |
| CALIB-06 (files-delete scoping D-15) | Phase 9 | Planned |
| CAM-01 | Phase 10 | Complete |
| CAM-10 (throwaway on-device latency spike; ⊂ CAM-01 H.264 ext) | Phase 21 | Complete |
| CAM-11 (Media3 H.264 rung above MJPEG/Snapshot, D-09; ⊂ CAM-01) | Phase 21 | Planned |
| CAM-12 (heuristic-selects / decoder-verifies routing, D-10/T-10-06; ⊂ CAM-01) | Phase 21 | Planned |
| CAM-13 (URL resolution: explicit extra_data else derive-from-convention, D-11/D-12; ⊂ CAM-01) | Phase 21 | Planned |
| CAM-14 (ExoPlayer leak-free lifecycle/release, D-08; ⊂ CAM-01) | Phase 21 | Planned |
| CAM-15 (SurfaceView render host via AndroidView; ⊂ CAM-01) | Phase 21 | Planned |
| CAM-16 (RTSP forced TCP interleaving; ⊂ CAM-01) | Phase 21 | Planned |
| CAM-17 (on-device UAT both printers: latency/decode/no-leak/no-crash, D-08/D-20; ⊂ CAM-01) | Phase 21 | Complete (E5+ core PASS on flox/release; E3/crowsnest live verification owner-deferred to Phase 22 — no crowsnest instance running) |
| SPOOL-01 | Phase 11 | Complete |
| SPOOL-02 (active-spool Status card; ⊂ SPOOL-01) | Phase 11 | Complete |
| SPOOL-03 (picker + filters in Dest.Spool; ⊂ SPOOL-01) | Phase 11 | Complete |
| SPOOL-04 (set/clear/change active via Moonraker JSON-RPC; ⊂ SPOOL-01) | Phase 11 | Complete |
| SPOOL-05 (QR scan-to-assign, ZXing GMS-free; SC-2/SC-4) | Phase 11 | Complete |
| SPOOL-06 (camera permission + no-camera degrade; SC-3) | Phase 11 | Complete |
| SPOOL-07 (print-start warn-only gate in Files; D-01) | Phase 11 | Complete |
| SPOOL-08 (external-change reconciliation + notify routing + pending-report staleness; D-10/D-11) | Phase 11 | Complete |
| SPOOL-09 (measured-weight correction + nice-to-haves: gcode prefilter, location shortcuts, archived warning; D-04) | Phase 11 | Complete |
| PROMPT-01 | Phase 12 | Complete (umbrella; closed by the Phase-12 on-device UAT PASS — flox + live E3, 2026-06-04, all 4 gates) |
| PROMPT-02 (render dialog + buttons fire gcode; ⊂ PROMPT-01/SC-2) | Phase 12 | Complete |
| PROMPT-03 (prompt_end/footer/re-entrancy + never-wedge Console; ⊂ PROMPT-01/SC-3) | Phase 12 | Complete |
| PROMPT-04 (provable with a real macro-examples.cfg run; ⊂ PROMPT-01/SC-4) | Phase 12 | Complete |
| MULTI-01 (managed profiles + switcher + spine rebind on switch) | Phase 14 | Complete |
| SYS-01 (host identity from machine.system_info) | Phase 20 | Complete |
| SYS-02 (live load via notify_proc_stat_update push) | Phase 20 | Complete |
| SYS-03 (health summary + shape-coded chip; throttle/temp-fallback) | Phase 20 | Complete |
| SYS-04 (graceful degradation to `—`, never crash) | Phase 20 | Complete |
| SYS-05 (on-device cross-SBC UAT — RPi 4 + RockPro64) | Phase 20 | Complete |
| PKG-01 | Phase 21 | Pending |
| PKG-03 | Phase 21 | Pending |

**Coverage:**

- Functional requirements mapped: 61 (55 prior + 6 promoted/added for the expanded roadmap: BEDM-01, BEDL-01, ZCAL-01, CAM-01, SPOOL-01 promoted from v2; PROMPT-01 new)
- Phases 8 & 13 are quality/reference phases with no functional REQ-IDs
- Finer per-phase req families: **CALIB-01..06 coined at Phase-9 planning (2026-06-02)** — CALIB-01 hub/gating, CALIB-02 screws-tilt (⊂ BEDL-01), CALIB-03 Z-tilt/QGL (⊂ BEDL-01), CALIB-04 bed-mesh (⊂ BEDM-01), CALIB-05 PROBE_CALIBRATE (⊂ ZCAL-01), CALIB-06 files-delete-scoping (folded D-15). The umbrella BEDM-01/BEDL-01/ZCAL-01 stay Pending until the Phase-9 on-device UAT closes. (CAM-* resolved at Phase 10.) **SPOOL-02..09 coined at Phase-11 discuss/plan (2026-06-04)** — SPOOL-02 active-spool Status card, SPOOL-03 picker+filters (`Dest.Spool`), SPOOL-04 set/clear/change active (Moonraker JSON-RPC), SPOOL-05 QR scan-to-assign (ZXing GMS-free, SC-2/SC-4), SPOOL-06 camera-permission + no-camera degrade (SC-3), SPOOL-07 print-start warn-only gate (D-01), SPOOL-08 external-change reconciliation + notify routing + pending-report staleness (D-10/D-11), SPOOL-09 measured-weight correction + nice-to-haves (D-04); all ⊂ SPOOL-01, which stays Pending until the Phase-11 on-device UAT closes. **PROMPT-01..04 coined at Phase-12 planning (2026-06-04)** — PROMPT-01 umbrella (conformant full-v1 renderer; ⊇ the 26-fixture conformance contract), PROMPT-02 dialog render + button gcode dispatch, PROMPT-03 prompt_end/footer/re-entrancy + never-wedge, PROMPT-04 on-device real-macro proof; all ⊂ PROMPT-01, which stays Pending until the Phase-12 on-device UAT closes. **SYS-01..05 coined at Phase-20 planning (2026-06-08)** — promotes the v2 umbrella SYS-01 into the active roadmap (host-health-only scope per D-13): SYS-01 host identity (`machine.system_info`), SYS-02 live load via the 1 Hz `notify_proc_stat_update` push, SYS-03 health-summary + shape-coded chip (throttle-authoritative on Pi / temp-fallback D-12 on non-Pi; throttle+uptime from a one-shot `machine.proc_stats` query), SYS-04 graceful degradation to `—`, SYS-05 cross-SBC on-device UAT (RPi 4 + RockPro64). Map to ROADMAP Phase-20 SC-1 (SYS-01/02/03), SC-2 (SYS-02 cadence), SC-3 (SYS-04), SC-4 (SYS-05).

**Per-phase counts (21-phase single-milestone roadmap; reordered + renumbered 2026-06-05 — see note below):**

- Phase 1 (Platform Gate): 2 — PKG-02, CONN-05
- Phase 2 (Connection & State Foundation): 9 — CONN-02, CONN-03, CONN-04, CONN-06, STATE-01..05
- Phase 3 (Design System & Theming Foundation): 7 — THEME-01, THEME-02, UI-01, UI-02, PRIM-01, PRIM-03, PRIM-04
- Phase 4 (Service, Shell, Settings & Print-Status Home): 9 — CONN-01, SET-01, SHELL-01..05, PRIM-02, PRIM-05
- Phase 5 (Temp/Move/Extrude): 12 — TEMP-01..04, MOVE-01..04, EXTR-01..04
- Phase 6 (Command Reference & Capability Matrix): 0 functional reqs — reference/quality phase, done FIRST of the remaining work (doc-derived command/function catalog from Klipper/Moonraker/Spoolman docs + canonical in-code command registry + per-printer availability matrix from live introspection; gates everything after)
- Phase 7 (Files & Print Control): 9 — FILE-01..04, JOB-01..05 (JOB live-monitoring largely pre-delivered by the Phase-4 Status home + Status quick-tasks; verified here against a real print)
- Phase 8 (Macros & Console): 4 — MACRO-01..03, CONS-02 (CONS-01 deferred 2026-06-02 — console read-only this phase)
- Phase 9 (Calibration & Maintenance): BEDM-01, BEDL-01, ZCAL-01 (promoted from v2; finer CALIB-* TBD at discuss)
- Phase 10 (Webcam Streaming): CAM-01 (promoted; MJPEG-only, WebRTC deferred)
- Phase 11 (Spool Management): SPOOL-01 (promoted; + the camera-QR-scan flow, finer SPOOL-* TBD at discuss)
- Phase 12 (Macro Prompt Protocol): PROMPT-01 (new; finer PROMPT-* TBD at discuss)
- Phase 13 (Optimization, Network Efficiency & Reliability): 0 functional reqs — quality/refactor phase (request-cadence audit + reliability; non-functional)
- Phase 14 (Multi-Printer Switching): MULTI-01 validated 2026-06-05 (managed profiles + switcher + spine rebind; live two-printer UAT PASSED). No additional MULTI-* sub-reqs were needed — MULTI-01 covered the phase scope.
- Phase 15 (Theme System & Settings Redesign): refines SET-*/THEME-*/UI-* — UX + conformance rework, no new functional REQ-IDs (merges the former Settings-Redesign + Final-Conformance phases; pulled to front)
- Phase 16 (Home / Print-Status Redesign): refines SHELL-*/JOB-* — UX rework, no new functional REQ-IDs (foundation-first, before the remaining features)
- Phase 17 (Fine-Tune / Live-Adjust Panel): new TUNE-* family — TBD at discuss
- Phase 18 (Output Controls — Fans, Lights & Generic Pins): new OUT-* family — TBD at discuss
- Phase 19 (Output Controls — Fans, Lights & Generic Pins): OUT-* family (shipped 2026-06-08)
- Phase 20 (System Information Page): SYS-01..05 family (coined at Phase-20 planning 2026-06-08 — host-health-only; promotes the v2 umbrella SYS-01)
- Phase 21 (Native H.264 Camera Streaming (MediaMTX)): CAM-10..CAM-17 family (coined at Phase-21 planning 2026-06-08 — the H.264 extension to CAM-01; Media3/ExoPlayer over RTSP/HLS per D-01, NOT WebRTC — WebRTC is the deferred D-03 escape hatch)
- Phase 22 (Release Hardening & Ship): 2 — PKG-01, PKG-03 (also a LIGHT final UI-conformance sweep of the late surfaces, and re-exercises CONN-04 reconnect-resync against a live print, deferred from the dissolved Job-Status phase)

---
*Requirements defined: 2026-05-30*
*Last updated: 2026-06-08 (Phase-21 planning) — coined the CAM-10..CAM-17 family for Native H.264 Camera Streaming (MediaMTX), the H.264 extension to CAM-01 (Media3/ExoPlayer over RTSP/HLS per D-01 — pivoted off the roadmap's WebRTC framing; WebRTC stays the deferred D-03 escape hatch). The D-02 throwaway on-device spike (CAM-10) is the gating first deliverable. Prior — 2026-06-08 (Phase-20 planning) — coined the SYS-01..SYS-05 family for the System Information Page (host-health-only), promoting the v2 umbrella SYS-01 into the active roadmap. Prior — 2026-06-04 (roadmap expansion to 22 phases) — 8 phases added before ship: 14 Multi-Printer Switching, 15 Fine-Tune/Live-Adjust, 16 Output Controls (fans/lights/pins), 17 WebRTC Camera (promoted from v2; MJPEG Phase 10 can't run on the dev's WebRTC-only printers), 18 System Information, 19 Home/Status redesign, 20 Settings redesign, 21 Final Theme/UI conformance pass. Ship renumbered Phase 14 → 22 (stays last); PKG-01/03 remapped 14 → 22. New phases' finer REQ families (MULTI-*/TUNE-*/OUT-*/SYS-*) coined at each phase's discuss. Prior — 2026-06-01 (roadmap expansion to 14 phases, single milestone) — the project is now ONE roadmap shipping once at the end (no v1/v2 split). Five v2 reqs PROMOTED into the active roadmap: BEDM-01/BEDL-01/ZCAL-01 → Phase 9 (Calibration & Maintenance), CAM-01 → Phase 10 (Webcam Streaming), SPOOL-01 → Phase 11 (Spool Management); PROMPT-01 added → Phase 12 (Macro Prompt Protocol). NEW Phase 8 = Command Reference & Capability Matrix (reference/quality, matrix knocked out early). Old "Backend Consolidation" → Phase 13 "Optimization, Network Efficiency & Reliability" (moved after ALL screens). Release → Phase 14 (ships the whole project at once). PKG-01/03 remapped Phase 9 → 14. Finer feature-req families to be defined at each phase's discuss. Prior: 2026-06-01 remaining-phase (6–9) restructure (Job Status dissolved → controls to P6, robustness to release); 2026-05-31 scope broadened per `docs/ui_design/`; 2026-05-30 Codex cross-AI review.*
