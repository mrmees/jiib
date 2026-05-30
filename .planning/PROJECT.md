# Dinghy Display

## What This Is

A native Android app that turns cheap, old Android hardware (Nexus 7 2013 class) into a
touchscreen control surface for a Klipper 3D printer, talking directly to the Moonraker API
(websocket + REST). It replaces the convoluted KlipperScreen-on-Linux + VNC/XSDL/X11 stack
people use today to get a printer touchscreen, giving the same printer-control capability as
a self-contained `.apk` with no Linux host, no remote-desktop layer, and no display-server glue.

The target user is a Klipper owner who has an old tablet or Android device lying around and
wants a dedicated, always-on printer screen without fighting VNC or re-flashing a Raspberry Pi.

## Core Value

**Direct, reliable printer control from an old Android tablet over Moonraker — install an APK,
point it at the printer, and drive a print.** If everything else fails, the connect → monitor →
control-a-print loop must work flawlessly on a Nexus 7.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

(None yet — greenfield, ship to validate)

### Active

<!-- v1 = functional core: the daily-driver print-control loop, testable on a real printer early. -->

**Connection & shell**
- [ ] Configure a Moonraker connection (host:port, optional API key / trusted-client auth), persisted locally
- [ ] Establish and maintain a resilient Moonraker websocket with auto-reconnect and clear connecting/disconnected/error states (splash/initializing surface)
- [ ] Persistent app shell: title bar, back/home navigation, live status, and an always-reachable Emergency Stop
- [ ] Main menu that doubles as a launcher and a compact thermal dashboard (heater rows + temperature graph)

**Print-control core**
- [ ] Move panel: X/Y/Z jog with distance presets, home axes, disable motors, live position
- [ ] Temperature panel: per-heater current/target, preheat presets, cooldown, exact-target keypad entry, history graph
- [ ] Extrude panel: extrude/retract, load/unload, distance & speed presets, active-tool selection
- [ ] Files / Print: browse Moonraker gcode files with thumbnails/metadata, start a print
- [ ] Job Status: live print progress, thumbnail, temps/speed/Z/time summaries, pause/resume/cancel/restart
- [ ] Macros: list and run `gcode_macros`, with generated parameter entry
- [ ] Console: send G-code, view command/response history with severity coloring

**Supporting primitives**
- [ ] Reusable UI primitives the panels depend on: numeric keypad, on-screen keyboard, confirm-action dialog, message/toast popups
- [ ] Capability gating: only show panels/controls the connected printer actually supports (heaters, extruders, macros, etc.)

### Out of Scope

<!-- Explicit boundaries with reasoning to prevent re-adding. -->

- **Full ~34-panel KlipperScreen parity in v1** — deliberately deferred; v1 is the functional core. Remaining panels (bed mesh/level, Z-calibrate, input shaper, fans, LEDs, power, pins, fine-tune, limits, pressure advance, retraction, spoolman, exclude-object) are post-v1 expansion.
- **Linux-host OS features as-is** — NetworkManager/DBus Wi-Fi management is dropped (Android owns Wi-Fi); host `mpv` camera, `systemctl` service restart, and host telemetry are not ported literally. Where Moonraker's API exposes equivalent data/actions, an *adapted* Android-native version may come later.
- **Camera in the v1 core** — high value on a tablet but not part of the print-control loop; it's the first post-core expansion (native MJPEG/WebRTC, not `mpv`).
- **Multi-printer switching in v1** — single printer connection for v1; connection layer designed to allow multiple later (KlipperScreen multi-printer = v2).
- **Google Play Store distribution** — Nexus 7-class hardware lacks current Play Services; ship signed APKs via GitHub Releases / sideload.
- **Portrait-optimized layout in v1** — landscape-first (how these tablets get mounted); portrait adapt comes later.
- **Coupling to the gtk4_klipperscreen fork** — that fork is a scope/reference source only; this app is independent with no shared code or release coupling.

## Context

- **Scope contract:** The KlipperScreen Screen Catalog at
  `E:\claude\personal\github\gtk4_klipperscreen\docs\Screen_Catalog.md` is the authoritative
  inventory of what "functional parity" means — ~34 routed panels plus shell, popups, keypad,
  keyboard, and overlay primitives. It explicitly flags Network/Camera/Updater/System as
  Android-portability boundaries. Treat it as reference, not as code to port.
- **Protocol:** Moonraker exposes a JSON-RPC websocket (subscriptions, `notify_*` push events)
  plus a REST API. The app is a Moonraker client — all printer state, files, history,
  thumbnails, macros, and actions flow through it on the local network.
- **Why old hardware:** Nexus 7 (2013) tablets are cheap, plentiful, 7" 1280×800, and ideal
  mounted next to a printer — but they ship Android 6.0.1 with weak Tegra/Snapdragon GPUs and
  2GB RAM, which constrains the UI toolkit and rendering approach.
- **Existing reference work:** A GTK4 KlipperScreen fork (`gtk4_klipperscreen`) lives in the
  same workspace and produced the catalog. It's prior research, not a dependency.
- **Owner:** Matthew runs Klipper on two printers (Ender 5 Plus, Ender 3 Pro) — see
  `personal/github/3d_printer_info/` — so there's a real test bed for the connect→print loop.

## Constraints

- **Compatibility**: minSdk 23 (Android 6.0, Nexus 7 2013 floor) — Why: target is cheap old hardware; this is the whole point of the project.
- **Performance**: Must stay responsive on a Tegra-era GPU with 2GB RAM — Why: the device class is weak; a janky printer screen is worse than none. Influences toolkit choice (classic Views vs Compose is a real tradeoff to settle in research).
- **Tech stack**: Native Android (to be confirmed in research) — Why: needs direct hardware/OS access, offline operation, and broad-device compatibility; rules out a web-wrapper approach that defeats the "lean on old hardware" goal.
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
| Landscape-first layout | These tablets get wall/printer-mounted in landscape | — Pending |

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
*Last updated: 2026-05-29 after initialization*
