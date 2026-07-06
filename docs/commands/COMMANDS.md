<!--
  GENERATED FILE — DO NOT EDIT BY HAND.
  Rendered from docs/commands/catalog.json by CommandReferenceDoc (test sourceset).
  To update: change the CommandRegistry / catalog.json, then regenerate:
    ./gradlew :app:testDebugUnitTest --tests '*CommandReferenceDocDriftTest' -Djiib.regenerateDocs=true
  A stale file fails the build (CommandReferenceDocDriftTest).
-->

# jiib — Moonraker & Printer Command Reference

Every G-code command and Moonraker API call **jiib actually sends** to your printer — the
complete surface of how the app talks to Moonraker/Klipper. It is generated from jiib's runtime
command registry, so it can never fall out of sync with the code.

This lists the **86** commands jiib sends. It intentionally omits the broad
Klipper/Moonraker reference surface jiib does *not* use; the full 338-entry catalog
(including planned and reference-only rows) lives in
[`docs/commands/catalog.json`](./catalog.json).

## How jiib talks to the printer

jiib holds a single WebSocket to Moonraker and speaks **JSON-RPC** over it. On connect it runs a
fixed handshake — `server.connection.identify` → `server.info` → `printer.objects.list` (discover
what this printer exposes) → `printer.objects.query` for the subset jiib needs → then
`printer.objects.subscribe` for that same subset, after which Moonraker pushes
`notify_status_update` diffs that jiib merges into its live state. Printer motion and macros are
sent as G-code through `printer.gcode.script`; everything else is a direct JSON-RPC method call.
Which commands are offered on a given printer is gated at runtime by the **Available when**
predicate shown below (evidence lives in [`printer-matrix.json`](./printer-matrix.json)).

## Commands jiib sends (86)

### Action

#### `printer.emergency_stop` · Moonraker JSON-RPC
Emergency-stop Klippy from Dinghy confirm guard.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/printer/

### Auth

#### `access.oneshot_token` · Moonraker JSON-RPC
Request a short-lived token for websocket/download access when auth is required.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/authorization/

### Calibration

#### `ACCEPT` · G-code (via `printer.gcode.script`)
Klipper `ACCEPT` — accept the current Z and conclude the manual-probe session (D-01).
- **Available when:** Klipper object `manual_probe` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `BED_MESH_CALIBRATE` · G-code (via `printer.gcode.script`)
Klipper `BED_MESH_CALIBRATE` command from the official G-Code reference.
- **Available when:** Klipper object `bed_mesh` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `BED_MESH_PROFILE LOAD` · G-code (via `printer.gcode.script`)
Klipper `BED_MESH_PROFILE LOAD=<name>` — load a saved mesh profile (D-10).
- **Available when:** Klipper object `bed_mesh` present
- **Parameters:** `LOAD`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `BED_MESH_PROFILE REMOVE` · G-code (via `printer.gcode.script`)
Klipper `BED_MESH_PROFILE REMOVE=<name>` — remove a saved mesh profile (D-10).
- **Available when:** Klipper object `bed_mesh` present
- **Parameters:** `REMOVE`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `BED_MESH_PROFILE SAVE` · G-code (via `printer.gcode.script`)
Klipper `BED_MESH_PROFILE SAVE=<name>` — save the active mesh into a named profile (D-10).
- **Available when:** Klipper object `bed_mesh` present
- **Parameters:** `SAVE`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `PROBE` · G-code (via `printer.gcode.script`)
Klipper `PROBE` command from the official G-Code reference.
- **Available when:** Klipper object `probe` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `PROBE_ACCURACY` · G-code (via `printer.gcode.script`)
Klipper `PROBE_ACCURACY` command from the official G-Code reference.
- **Available when:** Klipper object `probe` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `PROBE_CALIBRATE` · G-code (via `printer.gcode.script`)
Klipper `PROBE_CALIBRATE` command from the official G-Code reference.
- **Available when:** Klipper object `probe` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `QUAD_GANTRY_LEVEL` · G-code (via `printer.gcode.script`)
Klipper `QUAD_GANTRY_LEVEL` command from the official G-Code reference.
- **Available when:** Klipper object `quad_gantry_level` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `QUERY_PROBE` · G-code (via `printer.gcode.script`)
Klipper `QUERY_PROBE` command from the official G-Code reference.
- **Available when:** Klipper object `probe` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `SCREWS_TILT_CALCULATE` · G-code (via `printer.gcode.script`)
Klipper `SCREWS_TILT_CALCULATE` command from the official G-Code reference.
- **Available when:** Klipper object `screws_tilt_adjust` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `TESTZ` · G-code (via `printer.gcode.script`)
Klipper `TESTZ Z=<step>` — nudge Z within the manual-probe session (D-01).
- **Available when:** Klipper object `manual_probe` present
- **Parameters:** `Z`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `Z_TILT_ADJUST` · G-code (via `printer.gcode.script`)
Klipper `Z_TILT_ADJUST` command from the official G-Code reference.
- **Available when:** Klipper object `z_tilt` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

### Config

#### `SAVE_CONFIG` · G-code (via `printer.gcode.script`)
Klipper `SAVE_CONFIG` command from the official G-Code reference.
- **Available when:** Always
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `Z_OFFSET_APPLY_* + SAVE_CONFIG` · G-code (via `printer.gcode.script`)
Compound: apply the live Z-offset (Z_OFFSET_APPLY_PROBE/ENDSTOP) then SAVE_CONFIG, as one ordered gcode block (Live Z-Offset Save & Restart).
- **Available when:** Always
- **Reference:** https://www.klipper3d.org/G-Codes.html

### Connection

#### `server.connection.identify` · Moonraker JSON-RPC
Identify Dinghy as a Moonraker websocket client before subscriptions.
- **Available when:** Always
- **Parameters:** `client_name`, `version`, `type`, `url`, `api_key`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/server/

### Console

#### `server.gcode_store` · Moonraker JSON-RPC
Read cached G-Code responses for console backfill.
- **Available when:** Always
- **Parameters:** `count`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/server/

### Extrusion

#### `G1 E` · G-code (via `printer.gcode.script`)
Dinghy bounded extrude/retract wrapper.
- **Available when:** Klipper object `extruder` present
- **Parameters:** `E`, `F`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `T<n>` · G-code (via `printer.gcode.script`)
Select active tool by numeric index.
- **Available when:** Klipper object `extruder` present
- **Parameters:** `tool index`
- **Reference:** https://www.klipper3d.org/G-Codes.html

### Files

#### `server.files.delete_file` · Moonraker JSON-RPC
Delete a file from an allowed Moonraker root.
- **Available when:** Moonraker component `file_manager` present
- **Parameters:** `path`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/file_manager/

#### `server.files.get_directory` · Moonraker JSON-RPC
Browse one directory without walking the entire root.
- **Available when:** Moonraker component `file_manager` present
- **Parameters:** `path`, `extended`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/file_manager/

#### `server.files.metadata` · Moonraker JSON-RPC
Read slicer metadata and thumbnails for a selected gcode file.
- **Available when:** Moonraker component `file_manager` present
- **Parameters:** `filename`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/file_manager/

#### `server.files.thumbnails` · Moonraker JSON-RPC
Read thumbnail details for a gcode file.
- **Available when:** Moonraker component `file_manager` present
- **Parameters:** `filename`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/file_manager/

### History

#### `server.history.list` · Moonraker JSON-RPC
Read recent print history for the idle Status last-job card.
- **Available when:** Moonraker component `history` present
- **Parameters:** `limit`, `order`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/history/

#### `server.temperature_store` · Moonraker JSON-RPC
Read cached temperature history for graph backfill.
- **Available when:** Moonraker component `history` present
- **Parameters:** `include_monitors`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/server/

### Klipper Module

#### `ABORT` · G-code (via `printer.gcode.script`)
Klipper `ABORT` command from the official G-Code reference.
- **Available when:** Klipper object `manual_probe` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `LDC_CALIBRATE_DRIVE_CURRENT` · G-code (via `printer.gcode.script`)
Klipper `LDC_CALIBRATE_DRIVE_CURRENT` command from the official G-Code reference.
- **Available when:** G-code `LDC_CALIBRATE_DRIVE_CURRENT` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `PROBE_EDDY_CURRENT_CALIBRATE` · G-code (via `printer.gcode.script`)
Klipper `PROBE_EDDY_CURRENT_CALIBRATE` command from the official G-Code reference.
- **Available when:** G-code `PROBE_EDDY_CURRENT_CALIBRATE` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `PROBE_EDDY_CURRENT_TAP_CALIBRATE` · G-code (via `printer.gcode.script`)
Klipper `PROBE_EDDY_CURRENT_TAP_CALIBRATE` command from the official G-Code reference.
- **Available when:** G-code `PROBE_EDDY_CURRENT_TAP_CALIBRATE` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `SET_FILAMENT_SENSOR` · G-code (via `printer.gcode.script`)
Klipper `SET_FILAMENT_SENSOR` command from the official G-Code reference.
- **Available when:** G-code `SET_FILAMENT_SENSOR` present
- **Parameters:** `SENSOR`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `SET_GCODE_OFFSET` · G-code (via `printer.gcode.script`)
Klipper `SET_GCODE_OFFSET` command from the official G-Code reference.
- **Available when:** G-code `SET_GCODE_OFFSET` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `SET_GCODE_OFFSET (clear)` · G-code (via `printer.gcode.script`)
Klipper `SET_GCODE_OFFSET Z=0 MOVE=1` — clear the live Z babystep offset.
- **Available when:** Klipper object `gcode_move` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `SET_LED` · G-code (via `printer.gcode.script`)
Klipper `SET_LED` command from the official G-Code reference.
- **Available when:** G-code `SET_LED` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `SET_PRESSURE_ADVANCE` · G-code (via `printer.gcode.script`)
Klipper `SET_PRESSURE_ADVANCE` command from the official G-Code reference.
- **Available when:** G-code `SET_PRESSURE_ADVANCE` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `SET_RETRACTION` · G-code (via `printer.gcode.script`)
Klipper `SET_RETRACTION` command from the official G-Code reference.
- **Available when:** G-code `SET_RETRACTION` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `SET_TEMPERATURE_FAN_TARGET` · G-code (via `printer.gcode.script`)
Klipper `SET_TEMPERATURE_FAN_TARGET` command from the official G-Code reference.
- **Available when:** G-code `SET_TEMPERATURE_FAN_TARGET` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `SET_VELOCITY_LIMIT` · G-code (via `printer.gcode.script`)
Klipper `SET_VELOCITY_LIMIT` command from the official G-Code reference.
- **Available when:** G-code `SET_VELOCITY_LIMIT` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `Z_ENDSTOP_CALIBRATE` · G-code (via `printer.gcode.script`)
Klipper `Z_ENDSTOP_CALIBRATE` command from the official G-Code reference.
- **Available when:** G-code `Z_ENDSTOP_CALIBRATE` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `Z_OFFSET_APPLY_ENDSTOP` · G-code (via `printer.gcode.script`)
Klipper `Z_OFFSET_APPLY_ENDSTOP` command from the official G-Code reference.
- **Available when:** G-code `Z_OFFSET_APPLY_ENDSTOP` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `Z_OFFSET_APPLY_PROBE` · G-code (via `printer.gcode.script`)
Klipper `Z_OFFSET_APPLY_PROBE` command from the official G-Code reference.
- **Available when:** G-code `Z_OFFSET_APPLY_PROBE` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

### Machine

#### `machine.proc_stats` · Moonraker JSON-RPC
Moonraker `machine.proc_stats` operation from the official external API documentation.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/machine/

#### `machine.reboot` · Moonraker JSON-RPC
Moonraker `machine.reboot` operation from the official external API documentation.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/machine/

#### `machine.services.restart` · Moonraker JSON-RPC
Moonraker `machine.services.restart` operation from the official external API documentation.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/machine/

#### `machine.shutdown` · Moonraker JSON-RPC
Moonraker `machine.shutdown` operation from the official external API documentation.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/machine/

#### `machine.system_info` · Moonraker JSON-RPC
Moonraker `machine.system_info` operation from the official external API documentation.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/machine/

### Macro

#### `LOAD_FILAMENT` · G-code (via `printer.gcode.script`)
Run the user-defined `LOAD_FILAMENT` macro when present.
- **Available when:** macro `LOAD_FILAMENT` present
- **Reference:** https://www.klipper3d.org/Command_Templates.html

#### `UNLOAD_FILAMENT` · G-code (via `printer.gcode.script`)
Run the user-defined `UNLOAD_FILAMENT` macro when present.
- **Available when:** macro `UNLOAD_FILAMENT` present
- **Reference:** https://www.klipper3d.org/Command_Templates.html

### Motion

#### `FORCE_MOVE` · G-code (via `printer.gcode.script`)
Klipper `FORCE_MOVE` command from the official G-Code reference.
- **Available when:** G-code `FORCE_MOVE` present
- **Parameters:** `STEPPER`, `DISTANCE`, `VELOCITY`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `G1 absolute move` · G-code (via `printer.gcode.script`)
Dinghy bounded absolute move-to wrapper (Move Hub touch/scrubber moves).
- **Available when:** Klipper object `toolhead` present
- **Parameters:** `X`, `Y`, `Z`, `F`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `G1 relative jog` · G-code (via `printer.gcode.script`)
Dinghy bounded relative jog wrapper.
- **Available when:** Klipper object `toolhead` present
- **Parameters:** `X`, `Y`, `Z`, `F`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `G28` · G-code (via `printer.gcode.script`)
Dinghy home-all command.
- **Available when:** Klipper object `toolhead` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `G28 <axis>` · G-code (via `printer.gcode.script`)
Dinghy single-axis home command.
- **Available when:** Klipper object `toolhead` present
- **Parameters:** `X`, `Y`, `Z`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `G28 X Y` · G-code (via `printer.gcode.script`)
Dinghy home-X/Y command that does not home Z.
- **Available when:** Klipper object `toolhead` present
- **Parameters:** `X`, `Y`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `M106` · G-code (via `printer.gcode.script`)
Dinghy Fine-Tune part-cooling fan live-adjust (D-11).
- **Available when:** Klipper object `fan` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `M84` · G-code (via `printer.gcode.script`)
Dinghy disable-steppers confirm-guard action.
- **Available when:** Klipper object `toolhead` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `SET_KINEMATIC_POSITION + G1` · G-code (via `printer.gcode.script`)
Explicit unhomed override jog path.
- **Available when:** G-code `FORCE_MOVE` present
- **Parameters:** `X`, `Y`, `Z`, `F`
- **Reference:** https://www.klipper3d.org/G-Codes.html

### Output

#### `SET_FAN_SPEED` · G-code (via `printer.gcode.script`)
Set a generic (`fan_generic`) fan speed; BARE section name (HIGH-1).
- **Available when:** Always
- **Parameters:** `FAN`, `SPEED`
- **Reference:** https://www.klipper3d.org/Config_Reference.html#fan_generic

#### `SET_LED` · G-code (via `printer.gcode.script`)
Set an LED/neopixel color; BARE section name (HIGH-1); D-12 Off = all-zero.
- **Available when:** Always
- **Parameters:** `LED`, `RED`, `GREEN`, `BLUE`
- **Reference:** https://www.klipper3d.org/Config_Reference.html#led

#### `SET_PIN` · G-code (via `printer.gcode.script`)
Set a digital or PWM `output_pin` (also serves `pwm_tool`); BARE section name (HIGH-1).
- **Available when:** Always
- **Parameters:** `PIN`, `VALUE`
- **Reference:** https://www.klipper3d.org/Config_Reference.html#output_pin

#### `SET_SERVO` · G-code (via `printer.gcode.script`)
Set a servo angle, or disable it via `WIDTH=0`; BARE section name (HIGH-1).
- **Available when:** Always
- **Parameters:** `SERVO`, `ANGLE`
- **Reference:** https://www.klipper3d.org/Config_Reference.html#servo

### Print Control

#### `SDCARD_RESET_FILE` · G-code (via `printer.gcode.script`)
Klipper `SDCARD_RESET_FILE` command from the official G-Code reference.
- **Available when:** Klipper object `virtual_sdcard` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `printer.print.cancel` · Moonraker JSON-RPC
Cancel the active print.
- **Available when:** Klipper object `pause_resume` present
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/printer/

#### `printer.print.pause` · Moonraker JSON-RPC
Pause the active print.
- **Available when:** Klipper object `pause_resume` present
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/printer/

#### `printer.print.resume` · Moonraker JSON-RPC
Resume a paused print.
- **Available when:** Klipper object `pause_resume` present
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/printer/

#### `printer.print.start` · Moonraker JSON-RPC
Start printing a gcode file.
- **Available when:** Klipper object `virtual_sdcard` present
- **Parameters:** `filename`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/printer/

### Printer

#### `printer.info` · Moonraker JSON-RPC
Read Klippy connection and printer software state.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/printer/

#### `printer.query_endstops.status` · Moonraker JSON-RPC
Read the current trigger state (open/TRIGGERED) of each configured endstop.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/printer/

### Recovery

#### `printer.firmware_restart` · Moonraker JSON-RPC
Restart Klipper firmware from recovery UI.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/printer/

#### `printer.restart` · Moonraker JSON-RPC
Restart Klippy host process from recovery UI.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/printer/

### Server

#### `server.info` · Moonraker JSON-RPC
Read server metadata and component names for live component predicates.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/server/

### Spoolman

#### `server.spoolman.get_spool_id` · Moonraker JSON-RPC
Read active spool ID from Moonraker Spoolman integration.
- **Available when:** Moonraker component `spoolman` present
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/integrations/

#### `server.spoolman.post_spool_id` · Moonraker JSON-RPC
Set active spool ID through Moonraker Spoolman integration.
- **Available when:** Moonraker component `spoolman` present
- **Parameters:** `spool_id`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/integrations/

#### `server.spoolman.proxy` · Moonraker JSON-RPC
Proxy Spoolman REST requests through Moonraker.
- **Available when:** Moonraker component `spoolman` present
- **Parameters:** `request_method`, `path`, `body`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/integrations/

#### `server.spoolman.status` · Moonraker JSON-RPC
Read Moonraker Spoolman integration status.
- **Available when:** Moonraker component `spoolman` present
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/integrations/

### State

#### `printer.objects.list` · Moonraker JSON-RPC
List available Klipper status objects for live capability derivation.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/printer/

#### `printer.objects.query` · Moonraker JSON-RPC
Read a point-in-time status snapshot for selected Klipper objects.
- **Available when:** Always
- **Parameters:** `objects`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/printer/

#### `printer.objects.subscribe` · Moonraker JSON-RPC
Subscribe to printer status diffs for selected Klipper objects.
- **Available when:** Always
- **Parameters:** `objects`
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/printer/

### Temperature

#### `APPLY_HEAT_PRESET` · G-code (via `printer.gcode.script`)
Apply a per-printer Heat Preset — one SET_HEATER_TEMPERATURE / SET_TEMPERATURE_FAN_TARGET line per heater in the preset's sparse setpoint map.
- **Available when:** Klipper object `extruder` present
- **Parameters:** `HEATER`, `TARGET`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `SET_HEATER_TEMPERATURE` · G-code (via `printer.gcode.script`)
Klipper `SET_HEATER_TEMPERATURE` command from the official G-Code reference.
- **Available when:** any of `extruder`, `heater_bed`, `heater_generic chamber` present
- **Parameters:** `HEATER`, `TARGET`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `SET_TEMPERATURE_FAN_TARGET` · G-code (via `printer.gcode.script`)
Klipper `SET_TEMPERATURE_FAN_TARGET` command — set a temperature_fan's target temperature.
- **Available when:** Always
- **Parameters:** `TEMPERATURE_FAN`, `TARGET`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `TURN_OFF_HEATERS` · G-code (via `printer.gcode.script`)
Klipper `TURN_OFF_HEATERS` command from the official G-Code reference.
- **Available when:** any of `extruder`, `heater_bed` present
- **Reference:** https://www.klipper3d.org/G-Codes.html

### Tuning

#### `M220` · G-code (via `printer.gcode.script`)
Set speed factor override.
- **Available when:** Klipper object `gcode_move` present
- **Parameters:** `S`
- **Reference:** https://www.klipper3d.org/G-Codes.html

#### `M221` · G-code (via `printer.gcode.script`)
Set extrude factor override.
- **Available when:** Klipper object `gcode_move` present
- **Parameters:** `S`
- **Reference:** https://www.klipper3d.org/G-Codes.html

### Webcam

#### `server.webcams.list` · Moonraker JSON-RPC
List configured webcams.
- **Available when:** Always
- **Reference:** https://moonraker.readthedocs.io/en/latest/external_api/webcams/
