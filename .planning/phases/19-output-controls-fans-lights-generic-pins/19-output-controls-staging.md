# Phase 19 Output Controls - Staging Notes (corrected from mis-titled "Phase 18" source)

Source project: `/mnt/e/claude/personal/github/dinghy-display`

These notes are staging context for a future GSD discussion/planning pass. They are not written
into the real project checkout.

## Core Direction

The Outputs section is a whitelist of non-standard, user-controllable printer equipment.

It should not include normal printer controls that already live elsewhere, such as nozzle temp,
bed temp, print cooling fan speed, motion controls, probes, motor tuning, or standard system fans.

Phase 18 should be available while printing. It should not lock out controls by print state.

## Whitelist

Include only these Klipper config/object families:

- `heater_generic`
- `fan_generic`
- `led`
- `neopixel`
- `dotstar`
- `pca9533`
- `pca9632`
- `servo`
- `output_pin`
- `pwm_tool`

Explicitly excluded from earlier notes:

- `manual_stepper` - out of Phase 18; users who need this can expose safe custom macros
- `pwm_cycle_time` - not user-adjustable for Phase 18; do not show cycle-time control

## Exclusions

Exclude standard or automatic printer behavior:

- `extruder`, `extruder1`, `extruder2`, etc.
- `heater_bed`
- `fan` standard part-cooling fan
- `heater_fan`
- `controller_fan`
- `temperature_fan`
- `temperature_sensor`
- `temperature_probe`
- `thermistor`
- `adc_temperature`
- `static_pwm_clock`
- `static_digital_output`
- `multi_pin`
- `extruder_stepper`
- `dual_carriage`
- core motion steppers: `stepper_x`, `stepper_y`, `stepper_z`, etc.
- TMC driver sections
- digipot/current-control sections
- board expander / pin-provider sections
- probe sections
- accessory-specific integrations such as Palette workflows
- `respond`
- display sections
- button/input sections

Do not infer user-facing outputs from board expanders, aliases, static pins, or low-level pin plumbing.

## Discovery

Discovery source is intentionally unresolved for the planner/research step.

During Phase 18, compare:

- `printer.objects.list`
- `configfile.config`
- `configfile.settings`

Development should test what differs between those sources on the real printers.

Working assumption until proven:

- Use `configfile` to identify configured whitelisted sections and metadata.
- Use `objects.list` / live status query to confirm runtime object/value availability.

## Top-Level Outputs Screen

Use one flat Outputs list.

- No group subpages.
- No visible group headers.
- Pure alphabetical sorting across all output types.
- Icons denote output type.
- No search/filter in Phase 18.
- No hide/favorite/reorder customization in Phase 18.

Field behavior:

- Outputs Field is scrollable.
- Suppress the global swipe-up drawer on this screen.
- Gutter contains `Back` only.

List row grammar:

- Icon on the left.
- Prettified name in the middle.
- Current value/status on the right when available.
- If value/status is unavailable, hide only the value/status, not the item.
- Item remains tappable even when current value is unavailable.

Name prettifying:

- Remove type prefix.
- Replace `_` and `-` with spaces.
- Title-case words.
- Preserve raw Klipper object/section name internally for commands and diagnostics.
- Do not add raw-name suffixes for duplicate prettified names; rely on icon/type disambiguation.

## Detail Pages

Tapping a row opens an individual page suitable for that output type.

- Reuse existing single-setting / scrubber patterns when they fit.
- This is not mandatory; choose the control shape that matches the hardware.
- All output controls dispatch immediately.
- No Apply flow.
- No ConfirmGuard, including for pins/PWM tools that may control risky hardware.
- Off/zero actions dispatch immediately.
- Command busy state is scoped to the individual output detail page.
- Toasts are enough for transient command feedback.
- Command failure shows a toast and leaves the user on the current output page.
- No "all off" action in Phase 18.

## Per-Type Controls

### Custom Heaters

Applies to `heater_generic`.

List row:

- Show current temperature only.

Detail page:

- Similar to Temperature heater control.
- Shows current temp and target temp.
- Setpoint control.
- Explicit Off action.
- No presets.

Command expectation:

- Set target with `SET_HEATER_TEMPERATURE HEATER=<name> TARGET=<value>`.
- Off likely sets target to `0`, unless planner finds a better canonical command.

### Generic Fans

Applies to `fan_generic`.

List row:

- Show current percent when available.

Detail page:

- Percent speed scrubber.
- Off action.
- No presets.

Command expectation:

- Set speed with `SET_FAN_SPEED FAN=<name> SPEED=<0..1>`.
- Off sets speed to `0`.

### Lights / LEDs

Applies to:

- `led`
- `neopixel`
- `dotstar`
- `pca9533`
- `pca9632`

List row:

- Show brightness percent plus a color swatch when available.

Detail page:

- Whole-strip/all-LED control only.
- Simple RGB color picker.
- Brightness scrubber.
- No per-index addressable LED control in Phase 18.

Command expectation:

- Use `SET_LED` or the correct family-specific command as planner verifies.

### Servos

Applies to `servo`.

List row:

- Show current angle if available.
- Hide value/status if unavailable.

Detail page:

- Angle scrubber.
- Disable output control.
- No presets.

Command expectation:

- Use `SET_SERVO SERVO=<name> ANGLE=<value>` for angle.
- Use the canonical disable form as planner verifies.

### Output Pins

Applies to `output_pin`.

List row:

- Digital pin: show current On/Off state when available.
- PWM pin: show current percent when available.

Detail page:

- Detect `pwm: true`.
- Digital pin: toggle page.
- PWM pin: percent scrubber page.

Command expectation:

- Use `SET_PIN PIN=<name> VALUE=<value>`.

### PWM Tools

Applies to `pwm_tool`.

List row:

- Show current percent/value when available.

Detail page:

- Value/duty-cycle scrubber only.
- Off/zero sets value to `0`.
- Do not expose cycle-time control.

## Product Boundaries

- Standard printer controls stay on their dedicated screens.
- Generic fans include only `fan_generic`; not automatic/system fans.
- Auxiliary steppers are excluded; users can make safe custom macros when needed.
- No output customization in Phase 18.
- No search/filter in Phase 18.
- No special print-state safety gating in Phase 18.

## Open Planner Work

- Compare `printer.objects.list`, `configfile.config`, and `configfile.settings` for every whitelisted
  family on real printer captures.
- Verify exact live status fields for each output type.
- Verify exact command syntax and accepted value ranges for:
  - `SET_HEATER_TEMPERATURE`
  - `SET_FAN_SPEED`
  - `SET_LED`
  - `SET_SERVO`
  - `SET_PIN`
  - `pwm_tool` command path
- Confirm LED families can be controlled uniformly enough for one RGB+brightness page, or define
  per-family fallbacks.
- Confirm which output types expose current values; missing current value hides the row status only.
