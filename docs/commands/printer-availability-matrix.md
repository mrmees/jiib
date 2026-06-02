# Printer Availability Matrix

**Purpose:** dev-facing E5/E3 evidence for command availability predicates. Runtime gating remains live via `Capabilities`; this file is never shipped as static per-printer app behavior.

**Latest capture:** 2026-06-02T11:46:45Z via approved read-only Moonraker probes.

Probe endpoints: `/server/info`, `/printer/info`, `/printer/objects/list`, `/printer/objects/query?gcode`, `/printer/gcode/help`. All returned HTTP 200 in the latest capture. `printer.gcode.help` is recorded as positive evidence only.

## Ender 5 Plus (`ender5plus`, `192.168.1.120:7125`)

- Klipper `v0.13.0-662-gbd99b19b-dirty`
- Moonraker `v0.10.0-20-g9008485` · API `1.5.0`
- Components captured: 28
- Objects captured: 156
- Macros captured: 96
- G-code help positives captured: 188
- Components: `announcements`, `application`, `authorization`, `data_store`, `database`, `dbus_manager`, `extensions`, `file_manager`, `history`, `http_client`, `internal_transport`, `job_queue`, `job_state`, `jsonrpc`, `klippy_apis`, `klippy_connection`, `machine`, `mqtt`, `octoprint_compat`, `proc_stats`, `secrets`, `shell_command`, `spoolman`, `template`, `timelapse`, `update_manager`, `webcam`, `websockets`

## Ender 3 Pro (`ender3`, `192.168.1.121:7125`)

- Klipper `v0.13.0-662-gbd99b19b0-dirty`
- Moonraker `v0.10.0-20-g9008485` · API `1.5.0`
- Components captured: 27
- Objects captured: 98
- Macros captured: 60
- G-code help positives captured: 133
- Components: `analysis`, `announcements`, `application`, `authorization`, `data_store`, `database`, `dbus_manager`, `extensions`, `file_manager`, `history`, `http_client`, `internal_transport`, `job_queue`, `job_state`, `jsonrpc`, `klippy_apis`, `klippy_connection`, `machine`, `octoprint_compat`, `proc_stats`, `secrets`, `shell_command`, `spoolman`, `template`, `update_manager`, `webcam`, `websockets`

## Caveats

- Missing `printer.gcode.help` entries are not absence proof; use object, macro, component, and command result evidence for runtime decisions.
- Command-by-command predicate reconciliation is maintained in `docs/commands/printer-matrix.json` and expanded below in later task work.
