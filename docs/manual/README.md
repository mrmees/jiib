# jiib manual

This manual documents jiib version 0.1.0, a native Android touchscreen app for Klipper 3D printers talking directly to Moonraker. Every page is derived from the app source; screenshots referenced throughout live in `../screenshots/`.

**Start here:** [Concepts](concepts.md) — App-wide grammar: layout regions, foot bar conventions, emergency stop behavior, gating states, prompts/confirmations, and theming pointers

## Home & printing

- [Home (standby)](home.md) — The root screen: live printer state digest in standby; morphs to print dashboard during a job
- [Printing, Paused & Complete](printing.md) — Documents the home screen's three active-job states — Printing, Paused, and Complete — including the stat block fields, progress ring behavior, foot bar controls, and state transitions
- [Fine-Tune](fine-tune.md) — Live mid-print adjustment of speed, flow, pressure advance, fan, motion limits, and firmware retraction

## Everyday tools

- [Heaters](heaters.md) — Document the Heaters field takeover: a one-tap preset list (OFF + loaded-spool row + named presets) that replaces the field of Home, Extrude, or Temperature to set heater targets quickly
- [Temperature](temperature.md) — Live multi-trace temperature graph plus heater target control, with two switchable modes: Monitoring (all sensors) and Heater Adjust (heaters only)
- [Files](files.md) — Browse gcode files on the printer, view per-file slicer stats, and start or delete prints
- [Move](move.md) — Hub for every way to position the toolhead: tap-to-move bed map, axis scrubbers, increment jog, saved location bookmarks, live endstop view, and homing/disable-motors utilities
- [Extrude](extrude.md) — The filament hub: direct extrude/retract with length and speed sliders, runout sensor toggles, Load/Unload macro rows, pinned filament macros, and Spoolman spool link
- [Macros](macros.md) — Document the Macros screen: launcher list of bookmarked macros, selected-macro detail with parameter form, Manage mode for bookmarking and underscore-macro visibility, and Execute foot button
- [Bookmarked Macros (home rows)](bookmarked-macros.md) — Launch and run bookmarked Klipper gcode_macro commands with parameter entry and dispatch from a two-mode (Launcher / Manage) screen
- [Console](console.md) — Live read-only scrollback of the raw Klipper/Moonraker conversation, with severity coloring, backfill from gcode_store, and opt-in noise filters
- [Outputs](outputs.md) — Control every Klipper output (fans, servos, heaters, LEDs, pins) with type-specific Focus controls

## Calibration

- [Calibration hub](calibration.md) — Hub screen listing the printer's leveling and probing calibration routines, with per-routine capability gating and an Open action to launch the selected routine's dedicated screen
- [Bed Mesh](bed-mesh.md) — Document the Bed Mesh screen: probing, profile management, view types, color scale, and Mesh Config settings
- [Nozzle Distance](nozzle-distance.md) — Documents the six-tool probe and Z-offset suite: Z-offset calibration, probe testing, live babystepping, and three eddy-current calibration steps
- [Screws Tilt](screws-tilt.md) — Documents the Screws Tilt calibration screen: how to run a bed-screw leveling probe and read the turns-and-direction results
- [Z Tilt / gantry leveling](z-tilt.md) — Document the shared Z_TILT_ADJUST / QUAD_GANTRY_LEVEL automatic-leveling screen, its state machine, foot bar adaptation, and per-stepper adjustment display

## Spoolman

- [Spoolman](spoolman.md) — Browse the Spoolman filament inventory, filter by material/color/vendor, and set or clear the active spool

## Camera

- [Webcam (beta)](webcam.md) — Documents the live camera feed screen: navigation path, H.264/MJPEG/snapshot decode rungs, cam picker, tap-to-cycle, reconnect behavior, and the known H.264 mid-rotation limitation

## System & settings

- [System hub](system.md) — About card and hub for App Settings, Printer Settings, and Manage printers
- [App Settings](app-settings.md) — Document the App Settings screen: app-global preferences (text size, fonts, keep-awake, webcam, unsupported tools, babystep, battery optimization, dev widgets) that apply across all printer profiles
- [Font picker](font-picker.md) — Select the bundled Interface or Data typeface; live preview in Focus card, app-wide change on tap
- [Printer Settings](printer-settings.md) — Per-printer configuration hub — shows active printer identity and connection state, provides navigation doors to Connection, Theme & colors, Heat Presets, Increment Values, System Info, and Power / Reset
- [Manage printers](manage-printers.md) — Add, switch between, and manage Moonraker printer profiles — including the inline Find-on-network scan and connection editor surfaces
- [Find on network](printer-find.md) — mDNS scan for Moonraker instances with one-tap add-and-connect flow
- [Connection editor](connection.md) — Edit a printer profile's host, port, API key, and advanced URL; verify reachability with Test before saving
- [Theme & colors](theme.md) — Documents the per-printer Theme & colors screen: dark/light toggle, palette mode, seed color hue slider, and swatch-level color overrides including shuffle, revert, and save
- [Increment Values](increments.md) — Documents the per-printer step-size configuration screen, covering all 16 configurable controls, their defaults, and the input validation rules
- [Heat Presets](heat-presets.md) — Per-printer preheat preset management: add, rename, and delete named nozzle/bed temperature presets that appear on the Heaters screen
- [Power / Reset](power-reset.md) — Send host power commands (reboot, shutdown) and service restarts (Moonraker, firmware, Klipper) to the printer from one screen, with confirmation guards and service-manager capability gating
- [System Info](system-info.md) — Read-only browser of the host SBC and all connected MCUs: static identity, live CPU/memory/temperature stats, Pi throttle flags, and per-MCU load and bandwidth
- [Splash & connecting](splash.md) — Documents the startup and recovery screen that appears automatically when jiib cannot hand control to the main app — on first launch, while connecting, when firmware is down, or when the saved connection is unreachable
