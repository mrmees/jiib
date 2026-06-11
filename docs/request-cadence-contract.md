# Request-cadence contract — what dinghy-display actually puts on the wire

**Purpose:** the wire-truth guardrail for *how often* and *under what trigger* dinghy-display talks to
Moonraker — the sibling of `docs/moonraker-capabilities.md` (which governs *which* objects/fields we may
rely on). It exists to kill the recurring **chattiness / mock-vs-reality** bug class: build a screen and
you are tempted to "just poll the printer when this page opens." Don't. Live data comes from ONE persistent
`objects.subscribe`; everything else is a one-shot fired on a real edge. This contract is what phases 10–12
must honor before they stack new panels on the spine — it protects the LAN **and** the weak SBC host CPU
(the Moonraker/Klipper host is frequently a Pi 4 or RockPro64; a chatty client taxes the printer's own
controller, not just the network — D-05).

It was authored from the **real code** (`CommandRegistry`, `DeriveCapabilities`, `PrinterStateStore`,
`MoonrakerSession.runHandshake`) cross-checked against the **live SAVE_CONFIG/FIRMWARE_RESTART wire
captures** committed under `docs/commands/e5-saveconfig-capture.jsonl` and
`docs/commands/e3-saveconfig-capture.jsonl` (E5 + E3, identical — the freeze the headline fix kills was
app-side, not printer-dependent). The captures are the wire-truth for the klippy-restart re-handshake the
cadence below assumes.

---

## RULES FOR FUTURE PHASES (phases 10–12 — read before adding any network call)

1. **No `objects.subscribe` outside the central handshake.** There is exactly ONE persistent subscription,
   established in `MoonrakerSession.runHandshake()` and re-established on every reconnect AND every
   `notify_klippy_ready` re-handshake. A new panel does NOT open its own subscription — it reads the fields
   it needs off the already-subscribed `PrinterStateStore`. If your object is not in the subscribe set, add
   it to `DeriveCapabilities.V1_SUBSCRIBE_CORE` (justified, below), do not bolt on a second subscribe.

2. **No per-screen polling.** Do not add a `while (true) { delay(N); query(...) }` loop to refresh a panel.
   Derive live values from the central subscribe. (For the record: every `delay(4_000)` in `ui/` is a
   **toast / failure-text auto-dismiss timer** inside a `LaunchedEffect(failureText)` — MoveScreen,
   Temperature, Extrude, PrintStatus, ScrewsTilt — NOT a poll. Verified by grep across `ui/`. Do not cite
   those as a polling precedent; there is none.)

3. **One-shot reads go through the `SpineHandle`, off the throttled hot path, and are EDGE-DRIVEN —
   never timer-driven.** The canonical compliant shapes already in the codebase:
   - `PrintMetadataHolder` — `server.files.metadata` fetched **once per active-filename change**.
   - `LastJobHolder` — `server.history.list?limit=1` fetched **once per not-printing transition** (idle edge).
   - the handshake one-shot seeds (`temperature_store`, `gcode_store`, `configfile`) — once per handshake.
   The trigger is always an observed state edge (filename changed, went idle, (re)connected, klippy_ready),
   never a wall-clock timer. If you find yourself adding a "refresh on page open" query, stop — that was the
   exact anti-pattern the one applied fix in this phase removed (see § The one applied fix).

4. **High-rate numeric data is conflated to `DEFAULT_SAMPLE_MS` (250 ms) before it reaches the UI.** Never
   surface a faster cadence. `PrinterStateStore` accumulates high-rate `notify_status_update` diffs and
   flushes them on a ~4 Hz sampled tick (`DEFAULT_SAMPLE_MS = 250L`, `PrinterStateStore.kt`). Control-plane
   edges (`print_stats`, `webhooks`, `toolhead.homed_axes`) publish immediately; the gcode/console stream is
   its own buffer. A panel must not try to render temps/positions faster than the 250 ms flush.

---

## Two-plane throttle model (`PrinterStateStore`)

`notify_status_update` diffs land in `PrinterStateStore.onStatusDiff`. The store runs two planes:

| Plane | What | Cadence | Mechanism |
|-------|------|---------|-----------|
| **Control plane** | `print_stats`, `webhooks`, `toolhead.homed_axes` edges | **immediate** | published the instant the diff lands (a job/state/home edge must not wait for a tick) |
| **High-rate plane** | temps, targets, live positions, factors | **conflated to 250 ms** | accumulated, flushed by the `init` sampled loop every `DEFAULT_SAMPLE_MS = 250L` IF a high-rate update is pending |
| gcode / console stream | `notify_gcode_response` + `gcode_store` backfill | separate buffer | bounded `ConsoleScrollback`, not the status accumulator |

The UI collects the store's `StateFlow`s; it never reads the socket and never sees a sub-250 ms numeric cadence.

---

## Call inventory — DECLARED specs vs LIVE call sites

Sourced by walking `CommandRegistry.all`, then **verifying each entry against its actual production call
site**. The point of this contract is wire-truth, not a transcription of the registry — so a registry spec
with **no production websocket call site** is labelled *declared, not live on the socket*, NOT listed as a
live cadence.

### ⚠ Declared-but-not-live-on-the-socket (registry spec ≠ wire reality)

| Registry entry | Declared (CommandRegistry) | Reality | Verdict |
|----------------|----------------------------|---------|---------|
| **`oneshotToken`** | `MR-access.oneshot_token` JSON-RPC spec (`CommandRegistry.kt` ~82–87, listed in `all` ~440) | Production fetches the one-shot token via **REST `GET /access/oneshot_token`** in `MoonrakerAuth.fetchOneshotToken()` (`MoonrakerAuth.kt` ~53–58), invoked from `MoonrakerSession.connectAndServe` (~195) ONLY when a key is configured. The `CommandRegistry.oneshotToken` JSON-RPC spec has **NO production websocket call site**. | **Declared, not live on the socket (REST path).** Not a websocket cadence. Only fires at all on the keyed (auth) path; the default open path skips it entirely. |

(Every other `CommandRegistry.all` entry was checked and DOES have a live call site — the gcode/print/file
specs are dispatched per user action via `CommandDispatcher`. `oneshotToken` is the sole declared-not-live
entry. If a future phase adds a registry spec, re-run this check and flag any new declared-not-live entries
here rather than listing them as live cadence.)

### LIVE calls — the actual wire cadence

| Call | Method | Transport | Type | Trigger | Frequency | Verdict |
|------|--------|-----------|------|---------|-----------|---------|
| identify | `server.connection.identify` | JSON-RPC | one-shot | each fresh (re)connect; **SKIPPED** on the in-session re-handshake (same socket already identified → 400, per capture) | per fresh connect | OK |
| server.info | `server.info` | JSON-RPC | one-shot | each handshake | per (re)handshake | OK (component detection) |
| objects.list | `printer.objects.list` | JSON-RPC | one-shot | each handshake | per (re)handshake | OK (capability source) |
| objects.query(subset) | `printer.objects.query` | JSON-RPC | one-shot (seed) | each handshake | per (re)handshake | OK (snapshot seed) |
| **objects.subscribe(subset)** | `printer.objects.subscribe` | JSON-RPC | **PERSISTENT** | each handshake | per (re)handshake — **drives ALL live data** | **The single subscribe. Every object justified below.** |
| temperature_store | `server.temperature_store` | JSON-RPC | one-shot | each handshake | per (re)handshake | OK (graph backfill seed; gated on `history` component) |
| gcode_store(1000) | `server.gcode_store` | JSON-RPC | one-shot | each handshake | per (re)handshake | OK (console backfill, REPLACE semantics) |
| configfile query | `printer.objects.query {configfile}` | JSON-RPC | one-shot | each handshake (step 7) | per (re)handshake | OK — the ONE configfile read; THREE consumers off the same result (min_extrude_temp / max_extrude_distance, every `gcode_macro` body, probe.z_offset + screws_tilt config) |
| files.metadata | `server.files.metadata` | JSON-RPC | one-shot-per-filename | active filename change (`PrintMetadataHolder`) | per filename edge | OK (edge-driven, never polled) |
| files.get_directory | `server.files.get_directory` | JSON-RPC | one-shot | file-browser open / navigate | per user nav | OK (user-driven) |
| files.thumbnails | `server.files.thumbnails` | JSON-RPC | one-shot | thumbnail need | per file | OK |
| files.delete_file | `server.files.delete_file` | JSON-RPC | one-shot | user delete action | per tap | OK |
| history.list(1) | `server.history.list` | JSON-RPC | one-shot | every not-printing transition (`LastJobHolder`) | per idle edge | OK (edge-driven, never polled) |
| print start/pause/resume/cancel | `printer.print.*` | JSON-RPC | one-shot | user action | per tap | OK |
| emergency_stop / firmware_restart / restart | `printer.emergency_stop` etc. | JSON-RPC | one-shot | user action | per tap | OK |
| gcode / preset / jog / home / extrude / calibration / save_config | `printer.gcode.script` | JSON-RPC (gcode) | one-shot | user action | per tap | OK (G4 120 s timeout for long scripts) |
| Spoolman notify (push, server→client) | `notify_active_spool_set` / `notify_spoolman_status_changed` | JSON-RPC notification (no `id`, no request) | push | external spool change (Fluidd, runout macro) / Spoolman backend connect-disconnect | per server edge | OK — push-driven reconcile/re-fetch in `ActiveSpoolFacade` (D-10), never polled; payload shape per `docs/commands/moonraker-api.md` (~166–188): `params` is always a 1-element array |

**Net cadence shape:** ONE persistent `objects.subscribe` carries all live data; a fixed set of one-shot
seeds runs once per handshake; the rest are one-shots fired on a real edge (filename change, idle
transition, user tap). No timer-driven query. No second subscribe. No per-screen poll.

---

## Subscribe-set justification (every subscribed object → its consumer)

The single `objects.subscribe` set is `DeriveCapabilities.V1_SUBSCRIBE_CORE` **intersected with the
printer's live `objects.list`** (`deriveSubscribeSet`, `DeriveCapabilities.kt` ~76–98) — the A3 rule:
*never subscribe to an object the printer does not define.* It is re-derived (pure function) on EVERY
reconnect/re-handshake. Plus the dynamic objects the printer actually defines (extra `extruderN`, fans,
`heater_generic *`).

| Subscribed object | Consumer / why |
|-------------------|----------------|
| `webhooks` | klippy state / state_message (STATE-04) — connection-state surface |
| `print_stats` | job lifecycle (state/filename/durations/filament) — Status, Print (Phase 6) — control-plane edge |
| `pause_resume` | pause/resume confirmation (Phase 7) |
| `virtual_sdcard` | print progress 0..1 (Phase 6) |
| `display_status` | progress / message (Phase 6) |
| `toolhead` | position, `homed_axes`, axis min/max volume (Move / Phase 4) — `homed_axes` is a control-plane edge |
| `gcode_move` | speed/extrude factor, `gcode_position` (Z) (Phase 4/6) |
| `heater_bed` | bed temp/target/power (Temp / Phase 4) |
| `extruder` | hotend temp/target/power/`can_extrude` (Temp / Extrude / Phase 4) |
| `screws_tilt_adjust` | CALIB-02 guided-loop result source — **only diffs during calibration; cheap when idle** |
| `z_tilt` | CALIB-03 applied flag — only diffs during calibration |
| `quad_gantry_level` | CALIB-03 applied flag — **gated OFF on both test printers (E5+E3 lack it, D-02); kept for forward-compat**, intersect drops it when absent |
| `bed_mesh` | CALIB-04 mesh matrices / profiles — only diffs during calibration |
| `manual_probe` | CALIB-05 `is_active` / `z_position` session state — only diffs during a probe session |
| `probe` | CALIB-05 probe-present gate (PROBE_CALIBRATE vs Z_ENDSTOP_CALIBRATE, A3) |
| dynamic: `extruderN`, `fan`/`fan_generic *`/`heater_fan *`/`controller_fan *`, `heater_generic *` | added from the live object list when the printer defines them |

**Notes for phases 10–12:** every object above maps to a real consumer — none is unjustified. Two to keep
in mind: `quad_gantry_level` is forward-compat (the intersect loop drops it on both current test printers),
and the calibration objects (`screws_tilt_adjust`, `z_tilt`, `bed_mesh`, `manual_probe`) only push diffs
*during* a calibration routine, so they cost effectively nothing when idle. Adding a new panel = add its
object to `V1_SUBSCRIBE_CORE` with a justification row here, NOT a new subscription.

---

## The one applied fix this phase landed (cadence audit's single real finding)

The audit found exactly ONE redundant call: `MoonrakerSession.refreshProbeZOffset()` issued a **second**
`objects.query {configfile}` every time the Probe-Calibrate page opened, purely to re-read `probe.z_offset`
after a SAVE_CONFIG. The handshake's step-7 configfile one-shot (`runHandshake` ~500–511) **already** reads
`configfile` and publishes `probe.z_offset` via `store.setProbeZOffset`. Once the Phase-13 re-handshake fix
made the post-SAVE_CONFIG `notify_klippy_ready` re-run `runHandshake()` (re-reading configfile), the
page-open re-query became pure redundancy.

It was removed end-to-end (method + service wiring + `SpineHandle` field + `AppShell` caller) — gated on the
GREEN `ProbeZOffsetFreshnessTest`, which proves the re-handshake keeps `probe.z_offset` fresh via the
handshake's own configfile read (not via a SUMMARY sentence). This is the canonical example of Rule 3: a
"refresh on page open" query is the anti-pattern; the edge-driven re-handshake one-shot is the compliant
shape. **Do not reintroduce a page-open configfile re-query.**
