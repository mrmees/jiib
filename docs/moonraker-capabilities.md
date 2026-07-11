# Moonraker / Klipper capability catalog — what our printers actually expose

**Purpose:** the source of truth for which Moonraker/Klipper objects + fields jiib may rely
on, captured from the **real printers** (not docs, not assumptions). Built to kill the recurring
mock-vs-reality bug class: build screens against fields confirmed present here, and flag
slicer-/config-dependent fields as optional with fail-safe fallbacks.

Probe method (read-only REST; repeat to refresh):
```bash
H=192.168.1.120:7125   # Ender 5 Plus
curl -s "http://$H/printer/info"
curl -s "http://$H/server/info"
curl -s "http://$H/printer/objects/list"
curl -s "http://$H/printer/objects/query?print_stats&virtual_sdcard&display_status&gcode_move&toolhead&extruder&heater_bed&motion_report"
curl -s "http://$H/server/files/list?root=gcodes"
curl -s "http://$H/server/files/metadata?filename=<a real file>"
```

## Latest command-availability refresh — captured 2026-06-02

The Phase 06-04 live-probe checkpoint was approved by the user after successful read-only evidence.
The executor reran these read-only endpoints against both printers on 2026-06-02:
`/server/info`, `/printer/info`, `/printer/objects/list`, `/printer/objects/query?gcode`, and
`/printer/gcode/help`. Every endpoint returned HTTP 200 for both printers.

- Ender 5 Plus (`192.168.1.120:7125`): Klipper `v0.13.0-662-gbd99b19b-dirty`, Moonraker
  `v0.10.0-20-g9008485`, API `1.5.0`, 28 components, 156 objects, 96 macros, and 188
  `printer.gcode.help` positives.
- Ender 3 Pro (`192.168.1.121:7125`): Klipper `v0.13.0-662-gbd99b19b0-dirty`, Moonraker
  `v0.10.0-20-g9008485`, API `1.5.0`, 27 components, 98 objects, 60 macros, and 133
  `printer.gcode.help` positives.

The full structured evidence lives in `docs/commands/printer-matrix.json`. A local-only generated
command-by-command availability view may also exist under `docs/commands/`, but it is evidence rather
than public-clone guidance. Treat `printer.gcode.help` as positive evidence only; missing help entries
are not absence proof. The refreshed matrix reconciles
`FORCE_MOVE` from gcode-help positives, not from a nonexistent `force_move` status object. Public
`LOAD_FILAMENT` and `UNLOAD_FILAMENT` macros are absent on both observed printers, so Extrude UI must
continue using live macro gating/fallback behavior instead of assuming the private underscore helpers
are public commands.

---

## Ender 5 Plus (`ender5plus`, 192.168.1.120:7125) — captured 2026-06-01

- Klipper `v0.13.0-662` · Moonraker `v0.10.0` (api 1.5.0) · slicer in files = **OrcaSlicer 2.4**
- Heavily customized: CANbus **EBBCan** toolhead, **klicky** probe, **KAMP**, dual `stepper_z`/`z1`,
  filament switch + motion sensors, chamber/frame/MCU temp sensors, timelapse, neopixel/LEDs.
- **Moonraker components present** (`server.info.components`): includes `spoolman`, `history`,
  `job_queue`, `job_state`, `webcam`, `timelapse`, `update_manager`, `announcements`,
  `octoprint_compat`, `mqtt`, `power`? (no — not listed here). **Spoolman is active** → the Extrude
  "Spool" placeholder can talk to `/server/spoolman/*` later.

### Print-status data (all CONFIRMED present, real idle/last-print sample)

`print_stats` (subscribed, all-fields):
```
filename        "miata_battery_tpu_plate_PLA_36m31s.gcode"
state           "complete" | "printing" | "paused" | "standby" | "error" | "cancelled"
total_duration  2389.54   (s, wall time incl. heat/pause)
print_duration  2241.68   (s, actual extruding time — use for ELAPSED)
filament_used   5796.86   (mm, this job so far — use for filament-used)
message         ""
info.total_layer   50     ⚠ slicer-dependent (needs SET_PRINT_STATS_INFO; OrcaSlicer here sets it)
info.current_layer 50     ⚠ same — may be null/absent on other slicers
```
`virtual_sdcard`: `progress 0..1`, `is_active`, `file_position`, `file_size`, `file_path`.
`display_status`: `progress 0..1`, `message` (free text, e.g. "External Power OFF").
`gcode_move`: `speed_factor`, `extrude_factor`, `speed`, `gcode_position [X,Y,Z,E]` (user-facing — use
  Z = `[2]`), `homing_origin`, `absolute_coordinates/extrude`, `axis_map`.
`toolhead`: `homed_axes` ("" / "xyz"), `axis_minimum/axis_maximum [x,y,z,e]` (print volume!),
  `estimated_print_time`, `max_velocity/accel`, `extruder`.
`extruder`: `temperature`, `target`, `power`, `can_extrude` (bool), **`pressure_advance` 0.07**,
  **`smooth_time` 0.02**, `motion_queue`. → PA/smooth_time ARE live (future Tune surface).
`heater_bed`: `temperature`, `target`, `power`.
`motion_report`: `live_position [x,y,z,e]`, `live_velocity`, `live_extruder_velocity`, `steppers[]`.
`pause_resume`, `idle_timeout`, `system_stats`, `exclude_object` present.

### File metadata — `server.files.metadata?filename=…` (CONFIRMED shape)
```
size, modified, uuid, slicer "OrcaSlicer", slicer_version
estimated_time      2191    (s)  → ETA SOURCE (slicer-file-estimate; project decision 2026-06-01)
filament_total      5749.86 (mm) → total filament
filament_weight_total 17.15 (g)
layer_count         50           → total layers (reliable, from metadata)
layer_height        0.2 · first_layer_height 0.24
nozzle_diameter 0.4 · first_layer_extr_temp 190 · first_layer_bed_temp 60 · chamber_temp 0
filament_name/type/colors/temps/weights  (arrays — multi-material aware)
printer_model "Creality Ender-5 Plus" · printer_variant "0.4"
thumbnails[]  → [{width,height,size,relative_path}]  sizes 32 / 48 / 300
              relative_path e.g. ".thumbs/<name>-300x300.png"
print_start_time, job_id, gcode_start_byte/end_byte
```
**Thumbnail URL construction:** `http://<host>:<port>/server/files/gcodes/<dir>/<relative_path>`, where
`<dir>` is the directory of the gcode `filename` ("" for a root file). URL-encode path segments
(filenames have spaces). Pick the LARGEST thumbnail (300×300) and let Coil downsample to the ring size.
Coil 3 (`coil-compose` + `coil-network-okhttp`) is already in the stack and proven on-device.

### What jiib can rely on (Status screen)
- **Reliable (always present):** progress, print_duration (elapsed), filament_used, Z (gcode_position),
  temps/targets, filename, state, axis_min/max (volume), estimated_time + filament_total + layer_count +
  thumbnails (from metadata, per-file one-shot).
- **Slicer-dependent (fail-safe optional):** `print_stats.info.current_layer/total_layer` — present here
  (OrcaSlicer) but may be null elsewhere. **Fallback for current layer:** `floor((Z − first_layer_height)
  / layer_height) + 1`; **total layers:** `layer_count` from metadata. Never fabricate; show "—".
- **Time remaining (decided):** slicer file estimate → `estimated_time × (1 − progress)`, formatted
  `H:MM`. "Finish by" = now + remaining (device clock). No actual-pace blend in v1.
- **No direct ETA** from Moonraker — always computed.

### Print history — `server.history.list` / `server.history.totals` (CONFIRMED shape)

LIVE-captured Ender-5 reply to `server.history.list?limit=1&order=desc` (the most-recent completed job;
`order=desc` = newest first, `limit=1` = just the last job). This is the data source for the idle Status
"last completed job" card (260601-th9 Inc 3 — fetched ONCE per not-printing transition, never polled):
```
{
  "count": 137,                         ← total jobs in history; count==0 → NO history → empty-state
  "jobs": [
    {
      "job_id": "000089",
      "user": "No User",
      "filename": "miata/airbox-bracket.gcode",   ← gcode path (dir/ + file), thumbnail-URL key
      "status": "completed",            ← see status enum below
      "start_time": 1717200000.0,
      "end_time":   1717207191.0,
      "print_duration": 7012.4,         ← (s) actual extruding time  (ELAPSED)
      "total_duration": 7191.0,         ← (s) wall time incl. heat/pauses (TOTAL)
      "filament_used": 5749.86,         ← (mm) filament extruded this job
      "exists": true,                   ← FALSE = source gcode file was DELETED → thumbnail may 404
      "auxiliary_data": [ ... ],        ← spoolman/extra; NOT consumed
      "metadata": {                     ← SAME shape as server.files.metadata (see § "File metadata")
        "estimated_time": 7000,         ← (s) slicer estimate
        "filament_weight_total": 17.15, ← (g)
        "layer_count": 50,
        "object_height": 22.4,
        "thumbnails": [ {width,height,size,relative_path}, … 32/48/300 ]
      }
    }
  ]
}
```
**`status` enum (confirmed Moonraker values):** `completed` · `cancelled` · `error` · `klippy_shutdown`
· `interrupted` · `server_exit` · `in_progress`. Rendered verbatim (no interpretation).

**Degrade rules (260601-th9):**
- `count == 0` (or `jobs` absent/empty) → there is NO last job → the idle Status field shows the
  centered `file_copy_off` empty state, NOT a card.
- `exists: false` OR `metadata` absent/partial (deleted file, no slicer metadata) → render the TEXT
  stats (filename/status/durations/filament from the top-level job fields) with NO thumbnail; the
  metadata-derived fields (estimated_time / filament_weight_total / largest thumbnail) read null and
  the card never crashes — same null-safety discipline as Inc 2's `PrintMetadata`.
- `metadata` is the SAME shape already modeled in `state/PrintMetadata.kt`; the "largest thumbnail by
  width" pick is the SHARED `largestThumbRelPath(metadata)` helper, and the thumbnail URL uses the same
  `thumbnailUrl(httpBase, filename, relPath)` construction (it may 404 when `exists:false`).

**`server.history.totals` → `result.job_totals`** (captured for FUTURE use; NOT consumed this increment):
```
{ "job_totals": { "total_jobs": 137, "total_time": 982341.0, "total_print_time": 940120.0,
                  "total_filament_used": 812345.6, "longest_job": 7191.0, "longest_print": 7012.4 } }
```

---

## Ender 3 Pro (`ender3`, 192.168.1.121:7125) — captured 2026-06-01

- Klipper `v0.13.0-662` · Moonraker `v0.10.0` (api 1.5.0) · slicer in files = **OrcaSlicer 2.3**
- Plain rig: single MCU (no CANbus), single `stepper_z`, **Klack** probe, KAMP, filament switch +
  motion sensors, host/SKR temp sensors only. Components like the E5 + `analysis`, **minus**
  timelapse/mqtt/dbus extras. **Spoolman also active.**

### Print-status data — same objects + field shapes as the Ender 5. Sample (printer STANDBY/idle):
```
print_stats: filename "", state "standby", total_duration 0, print_duration 0, filament_used 0,
             info.total_layer = null, info.current_layer = null   ← NULL when not printing
extruder:    temperature/target/power/can_extrude, pressure_advance 0.04, smooth_time 0.04   (present)
heater_bed:  temperature/target/power
gcode_move.gcode_position = [194.0, 91.5, 2.0, 0.0]   (Z = [2])
toolhead.axis_minimum/maximum = [-10,-13,-8] .. [255,250,250]   (Ender-3 volume; differs from E5)
virtual_sdcard.progress / display_status.progress present
```
### File metadata — same shape as the E5:
```
slicer OrcaSlicer 2.3 · estimated_time 389 · filament_total 241.26 · layer_count 82
layer_height 0.12 · first_layer_height 0.24 · object_height 9.96
thumbnails[] 32/48/300, ".thumbs/<name>-WxH.png"
```

---

## Ender 5 ↔ Ender 3 diff (what matters to jiib)

- **Print-status DATA MODEL is identical** across both: same `print_stats` / `virtual_sdcard` /
  `display_status` / `gcode_move` / `toolhead` / `extruder` / `heater_bed` / `motion_report` fields, same
  metadata shape, same thumbnail scheme, both OrcaSlicer, both Spoolman-active, both have
  `SET_PRINT_STATS_INFO`. → **One Status screen built against the confirmed fields serves both.**
- **`print_stats.info.*_layer` is BOTH slicer- AND state-dependent:** null when standby/idle (seen on the
  E3), populated during/after a print (seen on the E5). Always treat as nullable → fallback to
  `floor((Z − first_layer_height)/layer_height)+1` for current and `layer_count` (metadata) for total;
  else "—". Never fabricate 0.
- **Differences are peripheral only** (don't affect Status): E5 = CANbus EBBCan, dual Z, klicky,
  chamber/frame/MCU sensors, LEDs/neopixel, multiple fans, timelapse/mqtt. E3 = single Z, Klack, plain
  MCU, fewer sensors. Print **volume** differs — always read `toolhead.axis_minimum/maximum`, never
  hardcode bed size.
- **`object_height`** present in metadata (E3 sample) — usable as total print height if wanted.

---

## Phase 8 — gcode_store + macro-body shapes (probed 2026-06-02)

Read-only live probe of the Ender 5 Plus (`192.168.1.120:7125`, Klipper `v0.13.0-662`, Moonraker
`v0.10.0` / API `1.5.0`, state `ready`) to settle RESEARCH Assumptions A1/A4 + Pitfall 6 BEFORE
writing any console/macro parsing code. This kills the mock-vs-reality bug class that bit Phases 2
and 5. The raw probed JSON is committed verbatim as the unit-test fixtures:
`app/src/test/resources/fixtures/gcode_store_e5.json` and `.../macro_bodies_e5.json`.

### `server.gcode_store` — console history backfill (CONS-02 / D-02) — CONFIRMED

Probe: `curl -s "http://192.168.1.120:7125/server/gcode_store?count=20"`

The REST mirror returns `{"result": {"gcode_store": [ {message,time,type}, … ]}}`. The JSON-RPC
`server.gcode_store` call returns the **same `result` object** (`{gcode_store:[…]}`), which is what
`parseGcodeStore` walks. **Entry shape CONFIRMED** = exactly `{message, time, type}`:

```json
{
  "gcode_store": [
    { "message": "// External Power ON - lets go!", "time": 1780358556.0222483, "type": "response" },
    { "message": "M104 T0 S195.0",                   "time": 1780358551.2493756, "type": "command"  }
  ]
}
```

- `message` (string) — raw line **including its severity prefix** (`// …`, plain commands, would carry
  `!! …` on an error). Observed live: `// `-prefixed responses (`// External Power OFF`,
  `// probe: open`, `// Attach_Probe …`), plain `response` lines (`Done printing file`), and plain
  `command` lines (`M104 S160`, `TURN_OFF_HEATERS`, `SET_FAN_SPEED FAN=FILTER_fan SPEED=0`).
- `time` (float) — Unix epoch seconds.
- **`type` enum OBSERVED values: exactly `"command"` and `"response"`** — matches the Moonraker docs
  enum verbatim. No other `type` value appeared in the live buffer. (Mainsail reclassifies a
  `response` whose message starts with `// action:`/`// debug:` at RENDER time — the wire `type`
  itself is only command/response.)

### `configfile.settings["gcode_macro <name>"].gcode` — macro-body nesting (MACRO-02 / D-09) — CONFIRMED

Probe: `curl -s "http://192.168.1.120:7125/printer/objects/query?configfile"` →
`result.status.configfile.settings`. **96 `gcode_macro <name>` sections** present.

- **VERDICT: macro `.gcode` is a single newline-joined STRING** (Python `type()` == `str` for every
  section probed) — **CONFIRMS RESEARCH Assumption A1; does NOT contradict it.** The 08-03 parser may
  run the Mainsail `paramRegex` directly on the string with NO array-join step.
- **Macro section keys are LOWERCASED** in `settings` (e.g. `"gcode_macro start_print"`,
  `"gcode_macro _pause_on_switch"`) — `Capabilities.hasMacroIgnoreCase` is the right lookup.

Real param-declaring bodies captured into `macro_bodies_e5.json` (verbatim) and the param the
Mainsail regex ACTUALLY extracts from each (this is the ground-truth the RED parser test asserts —
note the heuristic's real behavior, not the idealized one):

| Macro (lowercased key) | Declares | Parser extracts (name / type / default) |
|------------------------|----------|------------------------------------------|
| `gcode_macro start_print` | `params.BED_TEMP\|default(60)\|float`, `params.EXTRUDER_TEMP\|default(210)\|float` | `BED_TEMP`/null/`60`, `EXTRUDER_TEMP`/null/`210` — ⚠ the trailing `\|float` is NOT captured as a type by the Mainsail regex (the `default` group consumes `60`, `float` falls into `.*?`); type stays `null`. Real heuristic behavior. |
| `gcode_macro set_pause_at_layer` | `params.ENABLE is defined`, `params.LAYER\|default(...)\|int`, `params.MACRO\|default(...)` | `LAYER`/`int`/`pause_at_layer.layer`, `MACRO`/null/`pause_at_layer.call, True` |
| `gcode_macro set_pause_next_layer` | `params.ENABLE\|default(1)\|int`, `params.MACRO\|default(...)` | `ENABLE`/`int`/`1`, `MACRO`/null/`pause_next_layer.call, True` |
| `gcode_macro _client_extrude` | `params.LENGTH\|default(client.unretract)`, `params.SPEED\|default(...)` | `LENGTH`/null/`client.unretract`, `SPEED`/null/`client.speed_unretract` |
| `gcode_macro _pause_on_switch` | (none) | empty list (no-param case) |

**`'NAME' in params` guard form (RESEARCH `PARAM_IN_REGEX`): NOT present on this printer.** No macro
body on the E5 uses the single-quoted `'X' in params` membership-guard idiom (the in-params usages
that exist are `for key, value in params_filtered.items()` / `for p in params` loop forms the
guard regex deliberately does NOT match). The `PARAM_IN_REGEX` path is therefore exercised in the
RED test with a SYNTHETIC inline body (clearly labelled), since no real fixture exercises it — this
is the only place an invented string is used, and only for a form the live printer doesn't carry.

## Phase 8 — on-device UAT + console-scroll perf (2026-06-02)

The Macros & Console functional-core gate (plan 08-07, Task 3) was run on the real **flox**
(Nexus 7 2013, LineageOS 18.1 / API 30, Adreno 320 — the perf FLOOR) against the **live Ender 5
Plus** (`192.168.1.120:7125`). Matthew confirmed checks 1–6 by hand; the orchestrator measured
check 7 (perf) via gfxinfo. **All 7 checks PASS — functional core proven complete on real hardware.**

### Functional checks (hand-verified on flox + live Ender 5)

1. **Console backfill + live (CONS-02):** Console populates from `server.gcode_store` on connect
   (not blank) and new lines append LIVE as gcode/macros run.
2. **Severity color:** `!!` line renders red, `// ` echo amber, normal line default text
   (color + text, never color-alone).
3. **Filters (D-03/D-04):** toggling a filter ON hides matching lines and OFF re-reveals them —
   proving the raw buffer survives filtering (filters are a render-time view, not a destructive cull).
4. **Reconnect backfill (SC #3 — the functional-core gate):** with Console open, a
   disconnect/restore window's lines BACKFILL correctly on reconnect (not silently dropped).
5. **Macros incl. bookmark-persist (MACRO-01/02/03):** drawer Macros tile opens the Bookmarked
   launcher; Manage → System list shows all macros (underscore helpers hidden until Show hidden);
   bookmarks PERSIST across an app restart — proving the **B1** `macros.preferences_pb` DataStore
   wiring landed in 08-07. A real param-declaring macro executes from the Execution popup and the
   printer runs it.
6. **Security injection-reject (B2 / T-08-07-T):** a string param containing a newline, `;`, or
   `M112` is REJECTED by the V5 sanitizer — **no emergency-stop and no extra command fires** on the
   live printer (the on-device backstop to the `MacroInvocationTest` unit gate).

### Check 7 — console-scroll perf (gfxinfo, system-of-record on API 30)

Method: `adb shell dumpsys gfxinfo works.mees.dinghy`, reset → ~15 s **continuous scroll** of the
Console scrollback during live line flow → dump. `FrameTimingMetric`/Macrobenchmark is unreliable
on this floor, so **gfxinfo is the system of record** under the performance-floor rule in
`AGENTS.md`.

**Continuous-scroll-only pass — 991 frames:**

| Metric | Value |
|--------|-------|
| p50 | 7 ms |
| p90 | 8 ms |
| p95 | **9 ms** |
| p99 | 11 ms |
| Janky frames | 1 (0.10 %) |
| Frozen frames (≥700 ms) | **0** |
| Missed vsync | 0 |
| Slow UI thread | 0 |

**Beats the Files perf gate** (07-06 steady-scroll p95 ~15 ms). A first pass showed 32 frames in a
4950 ms overflow bucket; that was confirmed **idle-attribution** (gfxinfo charged idle wall-time to
the histogram) — a re-measured clean scroll-only run showed **0 frozen frames**. The Adreno-320
floor holds for the Console scrollback (itemAnimator=null + incremental `notifyItemInserted` +
bounded `ConsoleScrollback` + Views-in-Compose, no LazyColumn — T-08-07-A mitigated on-device).
