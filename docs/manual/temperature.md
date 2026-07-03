# Temperature

<img src="../screenshots/v0.1.0/temperature-monitoring-flox-landscape-light_2026-07-03.png" width="640"/>
<img src="../screenshots/v0.1.0/temperature-adjust-moto-portrait-dark_2026-07-03.png" width="220"/>

Live temperature graph and heater control for every sensor Klipper reports. Two modes —
**Monitoring** for an at-a-glance view of all sensors, and **Heater Adjust** for setting
nozzle and bed targets — share the same graph and switch with a foot bar tap.

**Getting there:** Home → Temperature (list row).

## The screen

The Focus card holds the live temperature graph at all times. Traces cover every heater
Klipper exposes plus any passive `temperature_sensor` objects you have added via the sensor
picker. Active heaters draw a dashed horizontal setpoint line alongside their trace.
The Y-axis range auto-fits all live history and active setpoints, rounded outward to the
nearest 5 °C and padded 5 °C above and below; the scale never twitches per sample because
bounds snap to 5 °C steps. Before any data arrives the graph defaults to 0–40 °C. Each
trace uses a distinct theme color; you can override any trace's color or hide it entirely.

The list below the graph shows sensor rows. In **Monitoring** mode every sensor appears —
heaters first (alphabetically by object name), then any passive sensors you have added.
In **Heater Adjust** mode the list shrinks to adjustable heaters only.

The foot bar changes with the current mode (see below).

## Options & controls

### Focus card

**Temperature graph** — multi-trace line graph of every monitored sensor. Each row in the
list corresponds to one trace; the trace color matches the row icon tint. Setpoint lines
(dashed) appear for any heater whose target is above 0 °C. The graph is read-only; to
interact with a sensor, tap its row in the list.

### List rows

Each row shows a sensor icon (tinted to that sensor's trace color), the sensor's display
name, the live current temperature on the right, and — for active heaters — the current
target below it in the trace color.

Display names are derived from the Moonraker object name: `extruder` → Nozzle,
`extruder1` / `extruder2` → Nozzle 1 / Nozzle 2, `heater_bed` → Bed,
`heater_generic <name>` and `temperature_sensor <name>` → the trailing name, title-cased.

**Tapping a row in Monitoring mode** opens the **Appearance popup** in the Focus card:

- **Color swatches** — 4×2 grid of 8 colors drawn from the Colorful palette pool
  (always Colorful regardless of the active palette mode). Tap a swatch to assign that
  color to this sensor's trace. The current selection is indicated by a heavier border.
  Color is persisted per sensor, per printer profile.
- **Visibility toggle** — show or hide this sensor's trace in the graph. The button shows
  an eye icon; the icon and styling flip when the trace is hidden. Visibility is persisted
  per sensor, per printer profile.
- **Done** — closes the popup and returns the Focus card to the graph.

**Tapping a row in Heater Adjust mode** opens the **Heater Control popup** in the Focus card:

- **Target value display** — large readout showing the current setpoint in °C (0 when the
  heater is off). The display updates live while dragging the scrubber; it shows the pending
  working target while a command is in flight.
- **Coarse scrubber** — bare horizontal track from 0 to the heater's configured `max_temp`
  (falls back to 350 °C when config limits are unavailable). Drag to preview; the target
  commits on pointer release.
- **− / + buttons** — fine-step the target by ±1 °C per tap. Both buttons dim to 38% opacity
  while a dispatch for this heater is in-flight.
- **Off** — sends the target to 0, turning the heater off. Styled amber (hazardous-in-process
  intent) because the heater is already running when this becomes relevant.
  Sends `SET_HEATER_TEMPERATURE HEATER=<name> TARGET=0`.
- **Done** — closes the popup without changing any values.

All target changes (scrubber settle, ± tap, Off) are batched: taps accumulate a working
target locally and a single `SET_HEATER_TEMPERATURE HEATER=<name> TARGET=<value>` command
fires after a ~500 ms quiet window. The target is clamped to 0–350 °C before dispatch.
If the command fails, a toast appears and the working target reverts to the live value.

### Foot bar — Monitoring mode

1. **Back** — navigate back to Home (accent intent).
2. **Sensor display settings** — opens the **Sensor Picker** in the Focus card. The picker
   lists only `temperature_sensor` objects (passive sensors from your Klipper config);
   heaters are always included and do not appear here. Tap a row to toggle it in or out of
   the monitored set; the eye icon on the right shows current state. Toggled sensors join or
   leave the graph immediately. Selection is persisted per printer profile. Tap **Done**
   to close. Uses Moonraker's `objects/list` capability data to enumerate available sensors.
3. **Switch to adjust mode** — switches the screen to Heater Adjust mode.

### Foot bar — Heater Adjust mode

1. **Back** — navigate back to Home (accent intent).
2. **Heaters** — opens the full **Heaters list** as a field takeover (the same list as the
   dedicated Heaters screen: OFF pinned at top, loaded-spool temperatures, then named
   presets). Tap any row to apply it. Sends `TURN_OFF_HEATERS` for the OFF row; sends a
   single g-code script of `SET_HEATER_TEMPERATURE` calls for heaters and
   `SET_TEMPERATURE_FAN_TARGET` calls for any `temperature_fan` entries in the preset. Tap
   **Back** inside the list to return to the sensor list without applying.
3. **Switch to monitoring mode** — switches back to Monitoring mode.

### Sensor Picker (Focus morph, Monitoring mode)

Opened via the **Sensor display settings** foot button. Lists every `temperature_sensor`
object reported in Klipper's capability set. An absent or new sensor appears as soon as
capabilities arrive. Toggling a sensor updates the graph and the sensor list immediately;
the change persists per printer profile via `TraceStylePrefs` DataStore.

## Related

- [Heaters](heaters.md) — quick one-tap preset application for nozzle and bed, available
  from nearly everywhere via the foot bar.
- [Fine-Tune](fine-tune.md) — live print-speed, flow, and fan adjustments during a print.
- [Concepts](concepts.md) — gating, e-stop, and the Focus / Field screen grammar.
