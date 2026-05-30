# Feature Research

**Domain:** Native Android touch-control client for a Klipper 3D printer over the Moonraker API (a self-contained KlipperScreen-equivalent for old Android tablets)
**Researched:** 2026-05-29
**Confidence:** HIGH

Grounded in the actual KlipperScreen Screen Catalog (`gtk4_klipperscreen/docs/Screen_Catalog.md`) and the current Moonraker external API docs (verified against `moonraker.readthedocs.io/en/latest/external_api/*`, 2026). Where a claim rests only on training data it is flagged.

---

## Framing: What "table stakes" means here

This app is not competing in a vacuum — it competes against **the user's current working setup**: KlipperScreen-on-Linux behind VNC/XSDL, or Mainsail/Fluidd in a browser. The bar for "table stakes" is therefore concrete and brutal:

> **A user will not abandon their VNC/KlipperScreen setup unless this app can run a real print start-to-finish without forcing them back to the browser mid-job.**

That single sentence defines v1. Everything that supports "connect → watch temps → start a file → pause/resume/cancel → tweak Z mid-print → run a macro → drop into the console when something's weird" is table stakes. Everything else is expansion.

---

## Feature Landscape

### Table Stakes (Users Expect These)

These map 1:1 to the PROJECT.md v1 "functional core." Missing any of them and the user keeps a browser tab open, which defeats the entire value proposition.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Configure + persist a Moonraker connection** (host:port, optional API key / trusted-client) | Can't do anything without it | LOW | REST + WS to one host. Trusted-client (LAN IP allowlist) is the common case; API key is the fallback. Persist locally (no cloud). |
| **Resilient websocket with auto-reconnect + explicit state surface** | Tablets sleep, Wi-Fi drops, printer reboots. A frozen screen mid-print is worse than VNC | **HIGH** | This is the single highest-risk, highest-value v1 item. JSON-RPC over WS with `notify_*` push. States: connecting / ready / reconnecting / klipper-shutdown / error. Re-subscribe on reconnect. See cross-cutting section. |
| **App shell with always-reachable Emergency Stop** | Safety. A printer screen without an instant E-stop is non-credible | LOW | `printer.emergency_stop` (no params). Persistent chrome: title bar, back/home, live status chip. Catalog: `base_panel`. |
| **Main menu = launcher + thermal dashboard** | First thing you see; "is the printer hot?" at a glance | MEDIUM | Grid of panel buttons + heater rows + temp graph. Catalog `main_menu`. Inline target-set keypad + cooldown. |
| **Move panel** (X/Y/Z jog, distance presets, home, disable motors, live position) | Core manual control; can't level/load/clear without it | MEDIUM | `printer.gcode.script` with `G91/G1`, `G28`, `M84`. Live position from `toolhead.position` / `motion_report.live_position` + `homed_axes` (gray out un-homed axes). Disable-motors **must** confirm (catalog gates it). |
| **Temperature panel** (per-heater current/target, preheat presets, cooldown, exact keypad entry, history graph) | You cannot start a print without preheating; the graph is how people trust the heater | MEDIUM | Targets via `SET_HEATER_TEMPERATURE`. Subscribe `extruder*`/`heater_bed` (`temperature`,`target`,`power`). Graph backfill from `server.temperature_store`. Enforce `max_temp` (reject over-limit). |
| **Extrude panel** (extrude/retract, load/unload, distance & speed presets, active-tool select) | Filament changes / loading are routine | MEDIUM | `M83` + `G1 E`. Block below `min_extrude_temp` (catalog dependency). Load/Unload call `LOAD_FILAMENT`/`UNLOAD_FILAMENT` macros — gate on their existence (catalog shows a "missing macro" popup). |
| **Files / Print** (browse gcode files, thumbnails, metadata, start print) | The whole point — launching a job | **HIGH** | `server.files.get_directory` (dirs/files/disk_usage). `server.files.metadata` → `estimated_time`, `filament_total`, `layer_height`, `object_height`, thumbnails. Thumbnails are PNGs fetched over REST at `thumbnail_path`/`relative_path`. Start: `printer.print.start` (`filename`). Confirm dialog with thumbnail before start. Thumbnail decode/caching is the perf risk on a Tegra GPU. |
| **Job Status** (live progress, thumbnail, temps/speed/Z/time, pause/resume/cancel/restart) | The screen you stare at for 8 hours; must replace the browser | **HIGH** | `print_stats` (`state`,`filename`,`print_duration`,`filament_used`), `virtual_sdcard.progress`, `display_status`. Buttons: `printer.print.pause/resume/cancel`, restart = `printer.print.start` again. **Cancel must confirm.** Button row changes by state (printing vs paused vs complete). |
| **Macros** (list `gcode_macro`s, run, generated parameter entry) | Everyone's workflow lives in macros (LOAD_FILAMENT, NOZZLE_CLEAN, custom) | MEDIUM | Discover from `printer.objects.list` (entries prefixed `gcode_macro `). Params: KlipperScreen reads them from config metadata; simplest v1 is free-text/numeric param entry, send via `printer.gcode.script`. Hide `_underscore` and `rename_existing` macros by default. |
| **Console** (send g-code, scrollback with severity coloring) | The escape hatch — when a panel can't do it, you type it. Without this users keep SSH/browser open | MEDIUM | Backfill `server.gcode_store` (each entry: `message`,`time`,`type`). Live via `notify_gcode_response` WS push. Color `!!`=error, `//`=warning. Send via `printer.gcode.script`. Cap scrollback (~1000 lines). |
| **Numeric keypad primitive** | Temp targets, jog distances, macro params, exact values | LOW | Inline, not a global modal (matches catalog). Reused by Temp / Move / Macros. |
| **On-screen keyboard primitive** | Folder search, macro text params, connection host entry | LOW-MEDIUM | Android IME mostly covers this — prefer the system keyboard over a custom one (don't rebuild KlipperScreen's matchbox fallback; that existed only because GTK-on-kiosk had no IME). |
| **Confirm-action dialog + toast/message popup primitives** | Safety gates + feedback. Catalog uses these everywhere | LOW | One confirm primitive (accept/cancel + rendered text) covers cancel/disable-motors/estop/delete. One toast primitive for `notify_gcode_response` warnings and errors. |
| **Capability gating** (only show panels/controls the printer actually has) | A "Extrude" tab on a printer with no extruder, or empty heater rows, looks broken | MEDIUM | Single source of truth: `printer.objects.list` once Klipper is `ready`. Drives which heaters/extruders/macros/sensors render. This is the backbone that makes every panel honest — build it early. |

### Differentiators (Competitive Advantage)

Why pick *this* over running KlipperScreen-in-a-VM-behind-VNC. These are not all v1, but they're where the product wins.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **No Linux host, no VNC, no X11 glue — just an APK** | The entire reason the project exists. "Install, point at printer, drive a print." | (Inherent) | This is the differentiator. Everything else exists to not undermine it. |
| **Runs well on Nexus-7-class hardware** | Competitors assume a Pi + display; this resurrects e-waste tablets | **HIGH** (ongoing) | minSdk 23, 2GB RAM, Tegra GPU. Forces lean rendering, careful thumbnail/graph drawing, classic Views likely over Compose. Perf *is* a feature here. |
| **Native MJPEG/WebRTC camera** | First post-core expansion; huge value on a mounted tablet (watch the print, not just stats) | HIGH | `server.webcams.list` for URLs. MJPEG over HTTP is the pragmatic v1 of camera (WebRTC is heavy on old GPUs). Do NOT shell to `mpv` like KlipperScreen — render natively. |
| **Fine-tune mid-print** (babystep Z, speed %, flow %) | The one tuning panel people reach for during *every* first layer | MEDIUM | `SET_GCODE_OFFSET MOVE=1`, `M220`, `M221`. Live values from `gcode_move.homing_origin`/`speed_factor`/`extrude_factor`. High value-to-effort; strong candidate to pull *into late v1* alongside Job Status. |
| **Landscape-first, mount-friendly layout** | These tablets get wall/printer-mounted; competitors are portrait-phone-centric | MEDIUM | Design system assumes 1280×800 landscape. Big touch targets (gloved/dirty fingers near a printer). |
| **Screensaver / burn-in protection + always-on behavior** | Dedicated always-on screen needs dimming/blanking that doesn't kill responsiveness | LOW-MEDIUM | Android `KEEP_SCREEN_ON` + a wake-on-tap dim overlay. Catalog has `screensaver`; adapt natively. |
| **Clean single-printer onboarding** | KlipperScreen multi-printer config is fiddly; a dead-simple "scan/enter IP" flow is a real edge | LOW | Connection layer designed multi-ready (per PROJECT.md) but v1 ships single, simple. |

### Anti-Features (Deliberately NOT Built)

Documented to prevent scope creep and re-litigation. Most are direct Android-portability boundaries the catalog itself flags.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Wi-Fi / network management** (KlipperScreen `Network` panel, NetworkManager/DBus) | KlipperScreen has it | **Android owns Wi-Fi.** No NetworkManager/DBus on Android; reimplementing is pointless and fights the OS | Deep-link to Android Wi-Fi settings if anything. Otherwise ignore. |
| **Host OS management** (`System` telemetry, `Updater`, service restart, host shutdown/reboot) | KlipperScreen exposes CPU/mem, update manager, `machine.services.restart` | This is a *printer control surface*, not a sysadmin console. The tablet isn't the host; managing the Pi's OS belongs in Mainsail/SSH | Drop entirely in v1. *Maybe* a thin power-device toggle later (`notify_power_changed`) since that's printer-adjacent, not host-OS. |
| **Slicer functionality** | "Wouldn't it be nice to slice on the tablet" | Slicing is a heavyweight desktop task; on 2GB Tegra it's absurd. Massive scope, zero fit | Out, permanently. Slice on a PC, upload to Moonraker. |
| **`mpv`-style external-player camera** | It's how KlipperScreen does it | Shelling to an external Linux player is the exact host-coupling this app removes | Native MJPEG/WebRTC surface. |
| **File upload from the tablet** | "Send my gcode to the printer" | Tablets aren't where slices live; upload UX on a 7" touchscreen is bad; not in the print-control loop | Browse + print files already on Moonraker. Upload via Mainsail/Fluidd. (Delete/rename are arguably in-scope later; upload is not.) |
| **Full config editing** (Mainsail's `printer.cfg` editor) | Power users want it | Editing config on a touchscreen is error-prone and dangerous; encourages footguns near a live printer | Console handles ad-hoc commands. Config edits belong on a real keyboard. |
| **Multi-printer switching in v1** | Some users have 2+ printers | Doubles connection-layer complexity before the single-printer loop is even proven | Connection layer is multi-*ready* (per PROJECT.md) but v1 ships one. v2. |
| **Google Play distribution** | "Easier to install" | Target hardware lacks current Play Services | Signed APK via GitHub Releases / sideload (PROJECT.md decision). |
| **Notifications log panel / Lock screen / Settings-as-a-mega-panel** | Catalog has them | Low value for v1; Android has its own notifications and lock; settings should be minimal | Toast popups cover transient messages. A tiny settings surface (connection + a few toggles) suffices. |

---

## Feature Dependencies

```
Connection (REST + resilient WebSocket)
    └──requires──> JSON-RPC client + notify_* event bus
                       └──requires──> Capability model (printer.objects.list)
                                          │
        ┌─────────────────┬──────────────┼──────────────┬─────────────────┐
        v                 v              v              v                 v
   Subscriptions     Temperature      Move          Files/Print       Macros
   (objects.subscribe)  panel         panel         (REST files       (objects.list
        │                 │             │             + metadata        gcode_macro*)
        │                 │             │             + thumbnails)
        │                 │             │                  │
        └─────────────────┴──────┬──────┴──────────────────┘
                                 v
                            Job Status  ──enhances──> Fine-tune (live SET_GCODE_OFFSET / M220 / M221)
                                 │
                                 v
                            Console (gcode_store backfill + notify_gcode_response live)

UI primitives (numeric keypad, system keyboard, confirm dialog, toast)
    └──underpin──> every panel above (build first)

Capability model ──gates──> ALL panels (heaters, extruders, macros, fans, leds, power, sensors)
```

### Dependency Notes

- **Everything requires the resilient WebSocket + event bus.** This is the foundation and the riskiest piece. Build and harden it before any panel work; a flaky transport makes every feature feel broken.
- **Capability model (`printer.objects.list`) gates every panel.** Query once on `ready`, cache, re-query on reconnect/Klipper-restart. Heaters/extruders/macros/fans/leds/power/sensors all key off it. Build it right after the transport.
- **Subscriptions (`printer.objects.subscribe`) feed Temperature, Move, and Job Status.** One subscription manager, multiple consumers. Re-subscribe on reconnect is mandatory.
- **Job Status depends on `print_stats` + `virtual_sdcard` + `display_status` + metadata/thumbnail.** It reuses the Files panel's thumbnail/metadata fetching and the subscription manager — so Files and the subscription layer come first.
- **Fine-tune enhances Job Status** and shares its live-value subscriptions (`gcode_move`); cheap to add once Job Status exists. Strong "late-v1 stretch" candidate.
- **Console depends on `server.gcode_store` (backfill) + `notify_gcode_response` (live)** — independent of panels, can be built in parallel once the event bus exists.
- **UI primitives underpin everything** — build the numeric keypad / confirm dialog / toast before the panels that use them, or you'll rebuild them four times.

---

## MVP Definition

### Launch With (v1 — the functional core)

The non-negotiable daily-driver print-control loop. This is exactly PROJECT.md's Active list, ordered by dependency.

- [ ] **Resilient WebSocket + JSON-RPC client + notify event bus** — foundation; nothing works without it
- [ ] **Connection config + persistence + state surface** (splash/connecting/error) — entry point
- [ ] **Capability model** (`printer.objects.list`) — gates every panel; build early
- [ ] **Subscription manager** (`printer.objects.subscribe`, re-subscribe on reconnect) — feeds live panels
- [ ] **UI primitives** (numeric keypad, confirm dialog, toast; use system IME for text) — reused everywhere
- [ ] **App shell + always-on Emergency Stop** — safety, navigation
- [ ] **Main menu** (launcher + thermal dashboard + temp graph)
- [ ] **Temperature panel** (targets, presets, cooldown, keypad, graph from `temperature_store`)
- [ ] **Move panel** (jog, home, disable-motors-with-confirm, live position)
- [ ] **Extrude panel** (extrude/retract, load/unload macro-gated, presets, tool select)
- [ ] **Files / Print** (browse + thumbnails + metadata + `printer.print.start` with confirm)
- [ ] **Job Status** (live progress, thumbnail, summaries, pause/resume/cancel-with-confirm/restart)
- [ ] **Macros** (list `gcode_macro`s, run, basic param entry)
- [ ] **Console** (`gcode_store` backfill + `notify_gcode_response` live, severity coloring)

**Ruthless cut line:** if it's not in this list, it's not v1. The test is "can I run a real print on the Ender 5 Plus start-to-finish without touching a browser." If yes, ship and validate.

### Add After Validation (v1.x — first expansion, ranked)

Pulled forward because they're high-value and lean on v1 infrastructure already built. **Camera and Fine-tune are the two strongest candidates** — Fine-tune is nearly free given Job Status; Camera is the most-requested missing thing on a mounted tablet.

- [ ] **Fine-tune** (babystep Z / speed% / flow%) — *trigger:* Job Status shipped; reuses its subscriptions. Highest value-per-effort. Arguably sneak into late v1.
- [ ] **Camera (native MJPEG)** — *trigger:* core loop validated. First post-core per PROJECT.md. MJPEG first, WebRTC later.
- [ ] **Fans** (`fan`/`fan_generic` control, others read-only) — *trigger:* user demand; simple sliders.
- [ ] **Power devices** (Moonraker power on/off, `notify_power_changed`) — *trigger:* user has a smart plug; one of the few "host-adjacent" things worth keeping.
- [ ] **LEDs** (RGBW sliders + presets) — *trigger:* user has addressable LEDs.

### Future Consideration (v2+ — defer)

Deeper, lower-frequency, or higher-complexity. Each is a real KlipperScreen panel but none is needed to *drive a print*.

- [ ] **Bed Mesh** (heatmap render + profile load/calibrate) — *defer:* needs a custom heatmap widget (BedMap); maintenance task, not print-loop.
- [ ] **Bed Level / screws-tilt** — *defer:* setup task, infrequent.
- [ ] **Z Calibrate** (staged probe workflow) — *defer:* complex staged UI, conservative confirmations; infrequent.
- [ ] **Input Shaper** (auto-cal + manual) — *defer:* needs accelerometer + guided flow; expert, rare.
- [ ] **Pressure Advance / Retraction / Limits** — *defer:* expert runtime-tuning sliders; small audience.
- [ ] **Pins** (PWM/output pin control) — *defer:* niche hardware-hacker surface.
- [ ] **Spoolman / Spool Editor** — *defer:* needs Moonraker Spoolman component; useful but optional ecosystem feature.
- [ ] **Exclude Object** (object map + exclude) — *defer:* needs `ObjectMap` polygon widget; reached from Job Status for print recovery. High value when a single object fails, but complex widget.
- [ ] **Multi-printer switching** — *defer:* v2 (PROJECT.md).
- [ ] **WebRTC camera** — *defer:* heavy on Tegra GPU; MJPEG first.
- [ ] **Portrait layout** — *defer:* landscape-first (PROJECT.md).

---

## Feature Prioritization Matrix

### v1 core (all P1 — they're the loop)

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Resilient WS + event bus | HIGH | HIGH | P1 |
| Connection config + state | HIGH | LOW | P1 |
| Capability model | HIGH | MEDIUM | P1 |
| Subscription manager | HIGH | MEDIUM | P1 |
| UI primitives (keypad/confirm/toast) | HIGH | LOW | P1 |
| App shell + E-stop | HIGH | LOW | P1 |
| Main menu + dashboard | HIGH | MEDIUM | P1 |
| Temperature panel | HIGH | MEDIUM | P1 |
| Move panel | HIGH | MEDIUM | P1 |
| Extrude panel | MEDIUM | MEDIUM | P1 |
| Files / Print | HIGH | HIGH | P1 |
| Job Status | HIGH | HIGH | P1 |
| Macros | HIGH | MEDIUM | P1 |
| Console | MEDIUM | MEDIUM | P1 |

### Post-core expansion (ranked value vs complexity)

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Fine-tune (babystep/speed/flow) | HIGH | LOW-MEDIUM | P2 |
| Camera (MJPEG) | HIGH | MEDIUM-HIGH | P2 |
| Fans | MEDIUM | LOW | P2 |
| Power devices | MEDIUM | LOW | P2 |
| LEDs | LOW-MEDIUM | MEDIUM | P2 |
| Exclude Object | MEDIUM-HIGH | HIGH | P3 |
| Bed Mesh | MEDIUM | HIGH | P3 |
| Spoolman + editor | MEDIUM | MEDIUM-HIGH | P3 |
| Z Calibrate | MEDIUM | HIGH | P3 |
| Bed Level | LOW-MEDIUM | MEDIUM | P3 |
| Input Shaper | LOW | HIGH | P3 |
| Pressure Advance / Retraction / Limits | LOW | LOW-MEDIUM | P3 |
| Pins | LOW | LOW | P3 |
| WebRTC camera | MEDIUM | HIGH | P3 |
| Multi-printer | MEDIUM | HIGH | P3 |

**Priority key:** P1 = must have for v1 launch · P2 = first expansion after validation · P3 = future/deferred.

---

## Moonraker API Reality Check (verified)

The features above are achievable because the API exposes exactly what's needed. Verified against current Moonraker external API docs (2026).

| Capability | Method / Notification | Returns / Notes | Confidence |
|-----------|----------------------|-----------------|------------|
| Browse files | `server.files.get_directory` | `dirs`, `files`, `disk_usage`, `root_info`. Each file: `path`, `modified`, `size`, `permissions` | HIGH |
| File metadata | `server.files.metadata` | `estimated_time`, `filament_total`, `layer_height`, `object_height`, `first_layer_height`, `thumbnails[]` | HIGH |
| Thumbnails | in metadata / `server.files.thumbnails` | each: `width`, `height`, `size`, `thumbnail_path` (root-relative) and `relative_path` (gcode-parent-relative). Fetched as PNG over REST | HIGH |
| File ops | `server.files.delete_file`, `server.files.move`, `server.files.copy` | post-v1 (not in print loop) | HIGH |
| Start / pause / resume / cancel | `printer.print.start` (param `filename`), `printer.print.pause`, `printer.print.resume`, `printer.print.cancel` | start needs filename; others no params | HIGH |
| Emergency stop | `printer.emergency_stop` | no params | HIGH |
| Run g-code / macros | `printer.gcode.script` (param `script`, newline-separated allowed) | used by Move/Temp/Extrude/Macros/Console | HIGH |
| Capability detection | `printer.objects.list` | array of available object names (after Klipper `ready`). Macros appear as `gcode_macro <name>`. This is the gating backbone | HIGH |
| Query state | `printer.objects.query` (param `objects` map) | one-shot snapshot | HIGH |
| Live subscriptions | `printer.objects.subscribe` (param `objects` map; empty cancels) | pushes deltas via `notify_status_update`. Feeds temps, position, progress | HIGH |
| Temp history backfill | `server.temperature_store` | per-sensor `temperatures`, `targets`, `speeds`, `powers` arrays — seeds the graph on open | HIGH |
| Console backfill | `server.gcode_store` | array of `{message, time, type}` (`type` = command/response) | HIGH |
| Console live | `notify_gcode_response` | WS push of each response line; `!!` = error, `//` = warning (catalog console dependency) | HIGH (catalog + docs) |
| Job history | `server.history.list` / `server.history.totals` | jobs: `filename`, `status`, `start_time`, `end_time`, `print_duration`, `total_duration`, `filament_used`, `metadata`. Post-v1 nicety | HIGH |
| Cameras | `server.webcams.list` | configured webcams + URLs (MJPEG/WebRTC). Drives native camera (post-core) | MEDIUM (docs excerpt incomplete; well-established) |
| Power devices | power on/off + `notify_power_changed` | post-v1; printer-adjacent, not host-OS | MEDIUM |
| Connection identity | `server.connection.identify` | returns `connection_id`; identify the client on WS connect | HIGH |

**Key relevant objects to subscribe/query:** `extruder`/`extruder1`… (`temperature`,`target`,`power`,`can_extrude`), `heater_bed`, `print_stats` (`filename`,`state`,`print_duration`,`filament_used`,`total_duration`), `virtual_sdcard` (`progress`,`is_active`,`file_position`), `display_status` (`message`,`progress`), `toolhead` (`position`,`homed_axes`,`max_velocity`…), `gcode_move` (`speed_factor`,`extrude_factor`,`homing_origin`), `motion_report` (`live_position`,`live_velocity`), `fan`.

---

## Cross-Cutting Expectations (the things that make it feel solid)

These aren't panels — they're qualities that span the whole app. Skimping here makes every feature feel broken.

- **Connection resilience is THE feature.** Auto-reconnect with backoff; re-run `printer.objects.list` + re-subscribe on every reconnect; survive tablet sleep/Wi-Fi drop/Klipper restart/firmware restart without a manual app restart. Distinct, honest states: connecting / ready / reconnecting / klipper-shutdown / error. A spinner that never resolves is the #1 way to lose a user. **HIGH complexity, build first, test by yanking Wi-Fi mid-print.**
- **Capability-gated UI everywhere.** Never render a control for hardware the printer lacks. Empty heater rows, a dead Extrude tab, or a non-functional macro button all read as "broken app." Drive purely off `printer.objects.list`.
- **Confirmation gates on destructive actions.** Print cancel, disable motors, emergency stop, file delete → confirm. The catalog notes KlipperScreen is *inconsistent* here (Power and Pins fire immediately) — **do better and be consistent.** One confirm primitive, applied uniformly.
- **Thumbnail rendering must be cheap.** PNG decode + cache on a 2GB Tegra device is a real perf cliff. Decode off the UI thread, cache decoded bitmaps by file+size, pick the smallest adequate thumbnail size, never block scrolling. This is the most likely place the app feels janky.
- **Landscape-first, big touch targets.** 1280×800, wall/printer-mounted, possibly touched with dirty/gloved fingers. Design the spacing for that, not for a phone in the hand.
- **Always-on with burn-in protection.** Keep-screen-on during prints; dim/blank overlay with wake-on-tap when idle; don't let blanking stall reconnection or live updates.
- **Graceful Klipper-shutdown handling.** When Klipper is in `shutdown`/`error`, most controls are invalid — surface the shutdown reason (from `webhooks`/`printer.info`) and offer firmware restart, rather than letting buttons silently fail.

---

## Competitor Feature Analysis

| Feature | KlipperScreen (on Linux + VNC) | Mainsail/Fluidd (browser) | Dinghy Display (this app) |
|---------|-------------------------------|---------------------------|---------------------------|
| Delivery | Linux host + X + display server + VNC/XSDL | Any browser, needs a screen + browser | Single signed APK, no host |
| Target hardware | Pi + DSI/HDMI display, or VNC to a tablet | Desktop/phone browser | Old Android tablets (Nexus 7 class) |
| Print-control loop | Full | Full | **Full (v1 goal)** |
| Camera | `mpv` external player | Browser MJPEG/WebRTC | Native MJPEG (post-core), WebRTC later |
| Wi-Fi mgmt | NetworkManager panel | n/a | **Deliberately dropped (Android owns Wi-Fi)** |
| Host/OS admin, updater, slicing | Yes (host-coupled) | Mainsail has config/update UI | **Deliberately out (not a sysadmin console)** |
| Touch ergonomics on a mounted tablet | Mediocre via VNC | Browser zoom fiddling | **Native, landscape-first, big targets** |
| Setup friction | High (flash Pi, X, VNC glue) | Medium (open URL, but needs a kiosk browser) | **Low (install APK, enter IP)** |

The wedge is unambiguous: **same print-control capability, none of the host/VNC/X11 baggage, on hardware people already have in a drawer.**

---

## Sources

- KlipperScreen Screen Catalog (authoritative scope reference): `personal/github/gtk4_klipperscreen/docs/Screen_Catalog.md`
- Project context: `personal/github/dinghy-display/.planning/PROJECT.md`
- [Moonraker File Management API](https://moonraker.readthedocs.io/en/latest/external_api/file_manager/) — files, metadata, thumbnails
- [Moonraker Printer Administration API](https://moonraker.readthedocs.io/en/latest/external_api/printer/) — print start/pause/resume/cancel, emergency_stop, gcode.script, objects list/query/subscribe
- [Moonraker Server Administration API](https://moonraker.readthedocs.io/en/latest/external_api/server/) — gcode_store, temperature_store, connection.identify
- [Moonraker Printer Objects](https://moonraker.readthedocs.io/en/latest/printer_objects/) — extruder/heater_bed/print_stats/virtual_sdcard/display_status/toolhead/gcode_move/motion_report fields
- [Moonraker history component (server.history.list)](https://github.com/Arksine/moonraker/blob/master/moonraker/components/history.py) — job history fields
- `notify_gcode_response`: confirmed as the live console feed in the Screen Catalog console panel dependency list; consistent with Moonraker notification model

---
*Feature research for: native Android Moonraker/Klipper touch-control client (KlipperScreen-equivalent for old tablets)*
*Researched: 2026-05-29*
