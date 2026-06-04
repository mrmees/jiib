# Dinghy Display

## What This Is

A native Android app that turns an Android phone or tablet into a touchscreen control surface
for a Klipper 3D printer, talking directly to the Moonraker API (websocket + REST). It replaces
the convoluted KlipperScreen-on-Linux + VNC/XSDL/X11 stack people use today to get a printer
touchscreen, giving the same printer-control capability as a self-contained `.apk` with no Linux
host, no remote-desktop layer, and no display-server glue.

The target user is a Klipper owner who wants a dedicated printer screen — whether that's a phone
in hand or an old tablet mounted by the machine — without fighting VNC or re-flashing a Raspberry
Pi. **Scope note (2026-05-31):** v1 now targets phones through tablets in **portrait and
landscape**, with a **full themable UI** (dark/light/custom + text-size). The cheap-old-hardware
**Nexus 7 2013 (Adreno 320) remains the support FLOOR** — the must-run worst case everything is
budgeted against — not the only target.

## Core Value

**Direct, reliable printer control from an old Android tablet over Moonraker — install an APK,
point it at the printer, and drive a print.** If everything else fails, the connect → monitor →
control-a-print loop must work flawlessly on a Nexus 7.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

**Design system substrate** — Validated in Phase 3 (code-verified 5/5; on-device gallery sign-off pending — see `03-HUMAN-UAT.md`)
- [x] Semantic-token theme system: dark + light + user custom, plus an S/M/L text-size setting (oklch→sRGB baked tokens, toolkit-agnostic resolver, DataStore fail-safe persistence)
- [x] Focus / Field / Gutter responsive layout grammar (portrait + landscape) + the outline-led control language (intent colors, ≥64dp targets)
- [x] Reusable component substrate: confirm-guard screen, single-setting scrubber/stepper page, severity toast, progress ring + line-graph render primitives — proven on flox at the Adreno-320 perf floor (sparse-redraw budget, two-part gate; Phase-6 re-validation mandated)

**Macros & Console — functional-core gate** — Validated in Phase 8 (code-verified 3/3 + on-device UAT PASSED on flox + live Ender 5 Plus; console-scroll perf p95 9ms / 0 frozen frames)
- [x] Macros (MACRO-01/02/03): list/run `gcode_macros` across three screens — System manage-visibility (underscore-hidden by default), Bookmarked launcher, Execution popup with auto-detected parameter entry parsed from the macro body; bookmarks/reveal persist via `macros.preferences_pb`. String-param **injection-reject** sanitizer (block_on:high) proven on-device.
- [x] Console READ-ONLY (CONS-02): command/response history with severity coloring, backfilled from `server.gcode_store`, live via `notify_gcode_response`, bounded ~1000-line scrollback, opt-in noise filters; reconnect backfills correctly (no silent drop). _CONS-01 (arbitrary G-code SEND) deferred — console is read-only this phase._
- _With this, the full functional core (Connect + Temp/Move/Extrude/Files/Print-Control/Macros/Console) is in place._

**Calibration & Maintenance** — Validated in Phase 9 (code-verified + on-device UAT PASSED on flox + live Ender 3, 2026-06-03)
- [x] Bed mesh (BEDM-01/BEDL-01) + Z-tilt/QGL + screws-tilt + the one interactive Probe-Calibrate manual-probe Z session (ZCAL-01), all capability-gated, reachable from a single Calibration drawer tile → hub → routine sub-routes. Worst-screw by deviation-from-base, load-scoped tilt state, allocation-free Views heatmap, klicky-aware probe flow (macro-feedback hero, home gate, SAVE_CONFIG amber gate).
- [x] D-15: delete-during-print scoping — only the actively-printing/paused file is undeletable; every other file stays deletable.
- ✅ _Resolved in Phase 13:_ the `SAVE_CONFIG` G2 re-handshake freeze is DEAD — recovery is now visible (Syncing→Connected), self-healing, and skips the re-`identify` that 400s on the same socket (live-captured on both printers). The todo is closed.

**Optimization, Network Efficiency & End-to-End Reliability** — Validated in Phase 13 (code-verified 4/4 success criteria + on-device UAT PASSED on flox + live E5 AND E3, 2026-06-04)
- [x] Request-cadence audit applied (`docs/request-cadence-contract.md`): every subscribed object justified, single `objects.subscribe` is the only live-data source, no per-screen polling; redundant `refreshProbeZOffset` removed (gated green by `ProbeZOffsetFreshnessTest`).
- [x] Headline `SAVE_CONFIG` freeze fixed (visible self-healing klippy-restart recovery, per-drop watchdog) AND a newly-found mid-print silent-drop freeze fixed (OkHttp `pingInterval` keepalive + full Syncing splash on socket reconnect; nav-state hoisted so recovery never bounces to Home — deliberate D-05 departure).
- [x] Behavior-preserving (every screen still live) + cross-screen reliability (reconnect mid-feature, no stale subscriptions). Code review: 0 critical, 3 warnings resolved.

### Active

<!-- v1 = functional core: the daily-driver print-control loop, testable on a real printer early. -->

**Shell & settings** (per `docs/ui_design/`, builds on the Phase-3 substrate)
- [ ] Settings screen (conventional Android, keyboard allowed): Moonraker connection (host:port, optional API key) persisted locally, theme selection, feature toggles
- [ ] Resilient Moonraker websocket with auto-reconnect and clear connecting/disconnected/error states (splash/initializing surface)
- [ ] Minimal-chrome shell: swipe-up full-screen App Drawer navigation; klippy-state-driven routing; Print Status home (compact thermal dashboard) with a Stop→confirm emergency/cancel control

**Print-control core**
- [ ] Move panel: X/Y/Z jog with distance presets, home axes, disable motors, live position
- [ ] Temperature panel: per-heater current/target, preheat presets, cooldown, exact-target keypad entry, history graph
- [ ] Extrude panel: extrude/retract, load/unload, distance & speed presets, active-tool selection
- [ ] Files / Print: browse Moonraker gcode files with thumbnails/metadata, start a print
- [ ] Job Status: live print progress, thumbnail, temps/speed/Z/time summaries, pause/resume/cancel/restart
- [x] Macros: list and run `gcode_macros`, with generated parameter entry — _validated Phase 8_
- [~] Console: view command/response history with severity coloring — _read-only validated Phase 8; send G-code (CONS-01) deferred_

**Supporting primitives**
- [ ] Reusable UI primitives the panels depend on (per design system): single-setting scrubber/stepper page (replaces a numeric keypad), full-screen confirm-guard, severity toasts; text entry confined to the Settings screen (system keyboard), with alphanumeric printer controls triaged per-control
- [ ] Capability gating: only show panels/controls the connected printer actually supports (heaters, extruders, macros, etc.)

### Out of Scope

<!-- Explicit boundaries with reasoning to prevent re-adding. -->

- **Full ~34-panel KlipperScreen parity in v1** — deliberately deferred; v1 is the functional core. Remaining panels (bed mesh/level, Z-calibrate, input shaper, fans, LEDs, power, pins, fine-tune, limits, pressure advance, retraction, spoolman, exclude-object) are post-v1 expansion.
- **Linux-host OS features as-is** — NetworkManager/DBus Wi-Fi management is dropped (Android owns Wi-Fi); host `mpv` camera, `systemctl` service restart, and host telemetry are not ported literally. Where Moonraker's API exposes equivalent data/actions, an *adapted* Android-native version may come later.
- **Camera in the v1 core** — high value on a tablet but not part of the print-control loop; it's the first post-core expansion (native MJPEG/WebRTC, not `mpv`).
- **Multi-printer switching in v1** — single printer connection for v1; connection layer designed to allow multiple later (KlipperScreen multi-printer = v2).
- **Google Play Store distribution** — Nexus 7-class hardware lacks current Play Services; ship signed APKs via GitHub Releases / sideload.
- **Coupling to the gtk4_klipperscreen fork** — that fork is a scope/reference source only; this app is independent with no shared code or release coupling.

<!-- Removed from Out of Scope 2026-05-31 (now IN scope per the docs/ui_design system):
  portrait-optimized layout (was landscape-only) and dark single-theme (now dark + light + user custom). -->
- **WebRTC camera / advanced expansion panels** — still post-v1 (see the design system's screen set for what v1 covers).

## Context

- **Scope contract:** The KlipperScreen Screen Catalog at
  `E:\claude\personal\github\gtk4_klipperscreen\docs\Screen_Catalog.md` is the authoritative
  inventory of what "functional parity" means — ~34 routed panels plus shell, popups, keypad,
  keyboard, and overlay primitives. It explicitly flags Network/Camera/Updater/System as
  Android-portability boundaries. Treat it as reference, not as code to port.
- **Protocol:** Moonraker exposes a JSON-RPC websocket (subscriptions, `notify_*` push events)
  plus a REST API. The app is a Moonraker client — all printer state, files, history,
  thumbnails, macros, and actions flow through it on the local network.
- **Why old hardware:** Nexus 7 (2013) tablets are cheap, plentiful, 7" **1920×1200** (323 ppi), and
  ideal mounted next to a printer — but they ship Android 6.0.1 on a **Snapdragon S4 Pro (APQ8064) /
  Adreno 320**, only **2GB RAM**, and a **32-bit ARMv7** CPU. That high-res panel driven by a
  2012-class GPU is exactly what constrains the UI toolkit and rendering approach. (The *2012* Nexus 7
  was the Tegra 3 / 1280×800 model — this project targets the *2013* model.)
- **Existing reference work:** A GTK4 KlipperScreen fork (`gtk4_klipperscreen`) lives in the
  same workspace and produced the catalog. It's prior research, not a dependency.
- **Owner:** Matthew runs Klipper on two printers (Ender 5 Plus, Ender 3 Pro) — see
  `personal/github/3d_printer_info/` — so there's a real test bed for the connect→print loop.

## Constraints

- **Compatibility**: minSdk 23 (Android 6.0). Nexus 7 2013 is the **support FLOOR**, not the only target — v1 spans phones through tablets. Why: the cheap-old-hardware promise stays as the floor even as scope broadened.
- **Performance**: The Adreno 320 (Snapdragon S4 Pro, 1920×1200, 2GB) is the **worst-case perf budget** — fill rate is the bottleneck; effects may be richer on capable hardware but must not break the floor.
- **Orientation**: portrait AND landscape (responsive Focus/Field/Gutter grammar).
- **UI / theming**: Governed by `docs/ui_design/` (LAW). Full semantic-token theming — dark + light + user custom — plus S/M/L text size. Geist/Geist Mono. No alphanumeric keyboard in printer controls (Settings screen owns text entry).
- **Tech stack**: Native Android, Kotlin, Jetpack Compose + classic Views hybrid (ADR 0001) — Why: direct hardware/OS access, offline operation, broad-device compatibility.
- **Connectivity**: Local-network Moonraker (websocket + REST), optional API-key/trusted-client auth — Why: Moonraker is the only integration surface; the printer and tablet share a LAN.
- **Distribution**: Sideloaded signed APK via GitHub Releases — Why: no current Play Services on the target hardware.

## Key Decisions

<!-- Decisions that constrain future work. Add throughout project lifecycle. -->

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| v1 = functional core (connect + Move/Temp/Extrude/Files/JobStatus/Macros/Console), not full parity | Something usable on a real printer early; full 34-panel parity is months with nothing to test | — Pending |
| minSdk 23 / Nexus 7 2013 floor | Target is cheap old hardware; the constraint *is* the product | — Pending |
| Adapt host-integration screens to Android/Moonraker-native (Camera native, drop NetworkManager) | Linux-host features can't port 1:1; Android owns Wi-Fi, Moonraker exposes the rest | — Pending |
| Independent app; catalog is reference only | No code/release coupling to the GTK4 fork keeps the Android app free to make platform-native choices | — Pending |
| Single printer in v1, connection layer multi-ready | Avoids v1 scope creep while not painting us into a corner | — Pending |
| Sideloaded APK distribution, no Play Store | Target hardware lacks current Play Services | — Pending |
| **Portrait + landscape, both in v1** (revised 2026-05-31; was landscape-only) | Target broadened to phones→tablets; Focus/Field/Gutter grammar is responsive | — Pending |
| **Full theming in v1 — dark + light + user custom + S/M/L text size** (2026-05-31) | Per the docs/ui_design system; user customization is a first-class feature | — Pending |
| **Nexus 7 2013 = support FLOOR, not the only target** (2026-05-31) | Keeps the cheap-old-hardware soul as the worst-case perf budget while serving modern devices | — Pending |
| **`docs/ui_design/` is the canonical UI law** (2026-05-31) | A full hi-fi design system authored by Matthew; supersedes per-phase UI specs | — Pending |
| **App nav = swipe-up App Drawer; Settings screen owns text entry; no keyboard in printer controls** (2026-05-31) | Per design system; numeric via single-setting pages, text triaged per-control | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-04 — Phase 13 (Optimization, Network Efficiency & End-to-End Reliability) COMPLETE: the promoted reliability phase. Capture-first (live SAVE_CONFIG wire-captures on both printers) → hardened the mock-that-lied → visible self-healing klippy-restart recovery (the headline freeze, dead) → request-cadence contract + refreshProbeZOffset removal → binding dual-printer on-device UAT. UAT round 1 caught two live bugs green units missed (G-A1 Home-bounce; G-B1 silent mid-print WiFi-drop = no OkHttp pingInterval — the 4th mock-vs-reality strike); 13-05 gap-closure fixed both (keepalive + nav-hoist + full Syncing splash on socket reconnect, a deliberate D-05 departure) and UAT round 2 PASSED on flox + live E5 AND E3. Verification 4/4; code review 0 critical (3 warnings fixed). Codex caught a real per-drop-watchdog bug in the headline fix. Next: Phase 14 (Release Hardening & Ship) OR resume Phases 6/7 (In Progress) / 10–12 (deferred features). Prior: 2026-06-03 — Phase 9 (Calibration & Maintenance) complete: bed-mesh/Z-tilt/QGL/screws-tilt + the interactive Probe-Calibrate manual-probe Z session, all capability-gated behind one Calibration drawer tile; D-15 delete-scoping. On-device UAT PASSED on flox + live Ender 3. Probe-Calibrate substantially reworked on device (manual_probe partial-diff fix, macro-feedback hero, three-value display, home gate, dismissable toasts). **Roadmap promotion (2026-06-03):** Phase 13 (Optimization/Network-Efficiency/End-to-End-Reliability) PROMOTED to run NEXT (execution-order only; number unchanged) ahead of Webcam/Spool/Macro-Prompt — driven by a Phase-9 UAT finding that the SAVE_CONFIG re-handshake freezes the live feed (a session-layer reliability bug, deferred to Phase 13 as its headline task). Next: `/gsd-discuss-phase 13`. NOTE: this Active list still lags reality — Phases 4–9 shipped but their Active items aren't all moved to Validated; a fuller reconciliation (or `/gsd-docs-update`) remains due.*
