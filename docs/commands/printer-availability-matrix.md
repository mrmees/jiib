# Printer Availability Matrix

**Purpose:** dev-facing E5/E3 evidence for command availability predicates. Runtime gating remains live via `Capabilities`; this file is never shipped as static per-printer app behavior.

**Latest capture:** 2026-06-02T11:46:45Z via approved read-only Moonraker probes.

Probe endpoints: `/server/info`, `/printer/info`, `/printer/objects/list`, `/printer/objects/query?gcode`, `/printer/gcode/help`. All returned HTTP 200 in the latest capture. `printer.gcode.help` is recorded as positive evidence only.

## Runtime Command Availability

Statuses are evidence summaries for the two observed printers, not shipped app defaults. `runtime_unconditional` means `CommandRegistry` has no availability predicate and the command was not executed in the read-only capture.

| Catalog ID | Predicate | Ender 5 Plus | Ender 3 Pro | Evidence note |
| --- | --- | --- | --- | --- |
| `MR-server.connection.identify` | `always` | runtime_unconditional | runtime_unconditional | CommandRegistry has no runtime availability predicate; official Moonraker docs are the source of command support. Not executed during the read-only capture. |
| `MR-access.oneshot_token` | `always` | runtime_unconditional | runtime_unconditional | CommandRegistry has no runtime availability predicate; official Moonraker docs are the source of command support. Not executed during the read-only capture. |
| `MR-server.info` | `always` | present | present | /server/info returned HTTP 200 in the approved capture. |
| `MR-printer.objects.list` | `always` | present | present | /printer/objects/list returned HTTP 200 in the approved capture. |
| `MR-printer.objects.query` | `always` | present | present | /printer/objects/query?gcode returned HTTP 200 in the approved capture. |
| `MR-printer.objects.subscribe` | `always` | runtime_unconditional | runtime_unconditional | CommandRegistry has no runtime availability predicate; official Moonraker docs are the source of command support. Not executed during the read-only capture. |
| `MR-server.temperature_store` | `component_present:history` | present | present | component history listed in /server/info capture. |
| `MR-server.files.metadata` | `component_present:file_manager` | present | present | component file_manager listed in /server/info capture. |
| `MR-server.history.list` | `component_present:history` | present | present | component history listed in /server/info capture. |
| `MR-printer.emergency_stop` | `always` | runtime_unconditional | runtime_unconditional | CommandRegistry has no runtime availability predicate; official Moonraker docs are the source of command support. Not executed during the read-only capture. |
| `MR-printer.firmware_restart` | `always` | runtime_unconditional | runtime_unconditional | CommandRegistry has no runtime availability predicate; official Moonraker docs are the source of command support. Not executed during the read-only capture. |
| `MR-printer.restart` | `always` | runtime_unconditional | runtime_unconditional | CommandRegistry has no runtime availability predicate; official Moonraker docs are the source of command support. Not executed during the read-only capture. |
| `KGC-SET_HEATER_TEMPERATURE` | `object_present:extruder` | present | present | object extruder listed in /printer/objects/list capture. |
| `KGC-SET_HEATER_TEMPERATURE_PRESET` | `object_present:heater_bed` | present | present | object heater_bed listed in /printer/objects/list capture. |
| `KGC-G1_JOG` | `object_present:toolhead` | present | present | object toolhead listed in /printer/objects/list capture. |
| `KGC-G1_OVERRIDE_JOG` | `object_present:toolhead` | present | present | object toolhead listed in /printer/objects/list capture. |
| `KGC-FORCE_MOVE` | `gcode_command_present:FORCE_MOVE` | present | present | FORCE_MOVE appeared in /printer/gcode/help positive evidence. gcode.help is positive evidence only; absence would not prove unsupported. |
| `KGC-G28_HOME_ALL` | `object_present:toolhead` | present | present | object toolhead listed in /printer/objects/list capture. |
| `KGC-G28_HOME_XY` | `object_present:toolhead` | present | present | object toolhead listed in /printer/objects/list capture. |
| `KGC-G28_HOME_AXIS` | `object_present:toolhead` | present | present | object toolhead listed in /printer/objects/list capture. |
| `KGC-G1_EXTRUDE` | `object_present:extruder` | present | present | object extruder listed in /printer/objects/list capture. |
| `KGC-T_SELECT_TOOL` | `object_present:extruder` | present | present | object extruder listed in /printer/objects/list capture. |
| `KGC-LOAD_FILAMENT` | `macro_present:LOAD_FILAMENT` | not_on_our_printers | not_on_our_printers | macro LOAD_FILAMENT not listed in /printer/objects/query?gcode capture. Private helper(s) observed instead: _LOAD_FILAMENT, _UNLOAD_FILAMENT. |
| `KGC-UNLOAD_FILAMENT` | `macro_present:UNLOAD_FILAMENT` | not_on_our_printers | not_on_our_printers | macro UNLOAD_FILAMENT not listed in /printer/objects/query?gcode capture. Private helper(s) observed instead: _UNLOAD_FILAMENT. |
| `KGC-TURN_OFF_HEATERS` | `object_present:extruder` | present | present | object extruder listed in /printer/objects/list capture. |
| `KGC-M84_DISABLE_STEPPERS` | `object_present:toolhead` | present | present | object toolhead listed in /printer/objects/list capture. |

## Ender 5 Plus (`ender5plus`, `192.168.1.120:7125`)

- Klipper `v0.13.0-662-gbd99b19b-dirty`
- Moonraker `v0.10.0-20-g9008485` - API `1.5.0`
- Components captured: 28
- Objects captured: 156
- Macros captured: 96
- G-code help positives captured: 188
- Components: `announcements`, `application`, `authorization`, `data_store`, `database`, `dbus_manager`, `extensions`, `file_manager`, `history`, `http_client`, `internal_transport`, `job_queue`, `job_state`, `jsonrpc`, `klippy_apis`, `klippy_connection`, `machine`, `mqtt`, `octoprint_compat`, `proc_stats`, `secrets`, `shell_command`, `spoolman`, `template`, `timelapse`, `update_manager`, `webcam`, `websockets`

## Ender 3 Pro (`ender3`, `192.168.1.121:7125`)

- Klipper `v0.13.0-662-gbd99b19b0-dirty`
- Moonraker `v0.10.0-20-g9008485` - API `1.5.0`
- Components captured: 27
- Objects captured: 98
- Macros captured: 60
- G-code help positives captured: 133
- Components: `analysis`, `announcements`, `application`, `authorization`, `data_store`, `database`, `dbus_manager`, `extensions`, `file_manager`, `history`, `http_client`, `internal_transport`, `job_queue`, `job_state`, `jsonrpc`, `klippy_apis`, `klippy_connection`, `machine`, `octoprint_compat`, `proc_stats`, `secrets`, `shell_command`, `spoolman`, `template`, `update_manager`, `webcam`, `websockets`

## Caveats

- Missing `printer.gcode.help` entries are not absence proof; use object, macro, component, and command result evidence for runtime decisions.
- `KGC-FORCE_MOVE` is present on both printers by `printer.gcode.help` positive evidence; neither printer exposes a `force_move` status object.
- Public `LOAD_FILAMENT` and `UNLOAD_FILAMENT` macros are not present on either observed printer. Ender 5 Plus has private `_LOAD_FILAMENT` and `_UNLOAD_FILAMENT`; Ender 3 Pro has private `_LOAD_FILAMENT` only.
- `not_on_printers` and `unknown` caveats are machine-readable in `docs/commands/printer-matrix.json`; tests consume that JSON sidecar rather than Markdown prose.
