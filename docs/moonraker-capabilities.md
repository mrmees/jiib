# Moonraker / Klipper capability catalog — what our printers actually expose

**Purpose:** the source of truth for which Moonraker/Klipper objects + fields dinghy-display may rely
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

### What dinghy-display can rely on (Status screen)
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

## Ender 5 ↔ Ender 3 diff (what matters to dinghy-display)

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
