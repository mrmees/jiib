# Z-Tilt Adjust / Quad Gantry Level

One screen, two routines: **Z-Tilt Adjust** runs `Z_TILT_ADJUST` to level a dual-Z gantry;
**Quad Gantry Level** runs `QUAD_GANTRY_LEVEL` to level a four-corner CoreXY gantry. Both
follow the same automatic flow — probe, compute, apply — and share the same layout and controls.

**Getting there:** Home → Calibration → select **Z-Tilt Adjust** or **Quad Gantry Level** in
the list → tap **Open** in the foot bar.

The Calibration hub dims routines your printer does not have configured; a dimmed entry can
still be opened, but the routine will fail if the required Klipper section is absent.

If a print starts while you are on this screen, jiib pops you back to the home screen
automatically.

## The screen

The **Focus card** shows a bed-tilt icon that changes color with run state: normal (idle or
running), green (leveled successfully), or red (failed). While the routine is in flight, the
Focus body replaces with a centered status card ("Adjusting Z tilt…" or "Leveling gantry…")
and the icon is not shown. If homing runs at the same time, the card reads "Homing…".

Below the Focus card, a **status area** (not a scrollable list) shows state-contextual text:
- **Idle, axes not homed** — "Home Axis First" in amber; "Home all axes before running
  [routine]."
- **Idle, axes homed** — "[Routine] Ready to Run"; "Run to level the gantry automatically."
- **Running** — "Running…"; "Probing and adjusting. Hands-off — wait for it to converge."
- **Leveled** — "Leveled" in green; either "Gantry leveled." (if no per-stepper adjustment
  lines were parsed from the console output) or "Z adjustments applied:" followed by a
  per-stepper table: stepper name and signed delta in mm to four decimal places
  (e.g. `stepper_z  +0.0528 mm`).
- **Failed** — "Failed" in red; the error text the printer returned, or "The printer rejected
  the routine." if no message was available.

The **foot bar** adapts to run state (buttons in order):

1. **Back** — leave the screen (accent). If a hard lock is active — the routine or a home
   operation is still in flight — a confirmation dialog appears before navigating away.
2. State-adaptive second button (see controls below).

## Options & controls

### Focus card

**Bed-tilt icon** — tinted to reflect state. No interaction; it is a visual indicator only.

**Emergency stop** — appears in the Focus header when gating is active (any lock, including
the routine itself or a triggered home) or while printing. At idle with no lock active and
no print in progress, the same slot shows the home-navigation icon instead. See
[Concepts](concepts.md) for e-stop behavior.

**"Still running" / Unknown card** — if the connection drops while a hard lock is active and
jiib cannot confirm whether the routine completed, the Focus body shows an "Unknown" card with
a **Dismiss** button. Tapping Dismiss clears the unknown state; it does not stop the printer.

**HardLock status card** — while the routine or a triggered home is in flight, the Focus body
shows a centered status message ("Adjusting Z tilt…", "Leveling gantry…", or "Homing…"). The
e-stop remains live in the header above it.

### Status area

**Per-stepper adjustment table** — shown after a successful run that produced adjustments.
Each row is stepper name (e.g. `stepper_z`, `stepper_z1`) and the applied delta in mm, signed
and to four decimal places. Parsed live from the printer's gcode console output; reflects the
last completed iteration.

**Error text** — shown after a failed run. Displays the error message the printer returned via
the JSON-RPC error response, or a generic fallback if none was provided.

### Foot bar

**Back** (always first, accent) — navigates away. Requires confirmation if a hard lock
(routine run or homing) is active on this screen.

**Home All** — shown when X, Y, or Z is not yet homed. Triggers `G28`. Disabled while any
tilt/QGL or home hard lock is active. Sends `G28`; requires a `toolhead` object in the
Moonraker session (gated by `AvailabilityPredicate.ObjectPresent("toolhead")`).

**Run** (green) — shown when axes are homed and no run has been dispatched this visit. Starts
the leveling routine. Disabled while the routine is in flight or a hard lock is active. Sends
`Z_TILT_ADJUST` (Z-Tilt variant); requires `[z_tilt]` in your Klipper config. Sends
`QUAD_GANTRY_LEVEL` (Quad Gantry Level variant); requires `[quad_gantry_level]` in your
Klipper config. Both commands apply a HardLock (backed by a 120-second gcode timeout).

**Running…** (disabled) — replaces Run while the command is in flight. Not interactive.

**Run Again** (green) — replaces Run after the routine completes (either Leveled or Failed).
Re-dispatches the same command and clears the previous result. Same gating and plumbing as
Run.

## Related

[Concepts](concepts.md) — e-stop, gating overlays, and hard-lock behavior.
[Bed Mesh](bed-mesh.md) — probed mesh calibration, also reached from the Calibration hub.
[Screws Tilt](screws-tilt.md) — manual screw-turn guidance for beds with leveling screws.
