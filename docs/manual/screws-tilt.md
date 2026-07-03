# Screws Tilt

Calculates how much to turn each manual bed-leveling screw to level the bed. After running,
every screw shows a turn count and direction; the base (reference) screw is the one you leave
untouched.

**Getting there:** Home → Calibration → select Screws Tilt from the list → **Open**.

Requires `[screws_tilt_adjust]` in your Klipper config; by default the Calibration hub hides
this routine when `screws_tilt_adjust` is absent, and shows it dimmed (but still openable)
only when **Show unsupported tools** is on. The app returns you to home if a print starts
while you are on this screen.

## The screen

The **Focus card** shows a to-scale bed map with one glyph per configured screw, letterboxed
to preserve the real X/Y aspect ratio (printer Y-up is flipped to screen Y-down). On entry
the glyphs are neutral — no measurement has been taken. After a run each glyph updates to
reflect its state (reference, within tolerance, turn clockwise, turn counter-clockwise), and
the probed Z height (in mm) appears below each glyph.

If your Klipper config has no screw XY positions, the Focus card shows a text prompt instead of the
map ("Run to probe the bed screws" before a run, "Bed screw map unavailable" after one). The
list still carries the turn instructions in that case.

While the routine is running or while homing, the Focus body is replaced by a status card
("Measuring bed screws…" or "Homing…"); the header and e-stop remain active above it.

Results do not persist across navigation visits. If you leave and return, the map and list both
reset to their pre-run state; tap **Run** again to get fresh measurements.

The **list** shows one row per configured screw: the screw name on the left, the turn
instruction on the right. Rows appear immediately on entry from config; the trailing column
shows "—" before any run is complete. Rows are informational only — tapping does nothing.
The list dims while a run or homing is in progress.

The **foot bar** holds **Back** plus a state-adaptive primary action (see below).

## Options & controls

### Focus card — bed map glyph states

Each screw position shows one of five states:

- **Pending** — no measurement yet. Shown for every screw on entry and cleared when a run
  completes. Glyph is neutral/muted.
- **Base** — the reference screw used as the level plane; do not turn this one. Shown in
  muted color; its trailing list column reads "base".
- **In tolerance** — within 0.05 mm of the base Z height; shown in accent color. A small
  turn amount may still appear in the list but the screw is acceptable.
- **Clockwise** — needs turning CW to match the base plane; shown in green.
- **Counter-clockwise** — needs turning CCW; shown in red.

Each non-pending glyph also shows the probed Z height of that screw (e.g., `2.450 mm`) below
the glyph.

**Unknown-state morph:** if the connection drops mid-run, the Focus body shows a "Still
running" card with a **Dismiss** button. Klipper continues the probe server-side; dismissing
clears the card but does not abort the routine.

### List rows

One row per configured screw (or per measured screw if no XY positions are in the config):

- **Primary label** — the screw's name from `[screws_tilt_adjust]` (title-cased, e.g.
  "Front Left"), falling back to the raw key (`screw1`, `screw2`, …) if no name is
  configured.
- **Trailing instruction**:
  - `—` — no measurement yet (on entry, or while a run is in flight).
  - `base` — reference screw; leave this one alone.
  - `HH:MM CW` or `HH:MM CCW` — how much to turn and in which direction. `HH` is full
    screw rotations; `MM` is clock-minutes, where 60 = one full rotation (360°). Example:
    `01:15 CW` means one full clockwise turn plus a further 90° (15/60 of a rotation).
    Values are carried verbatim from Klipper's `adjust` field.

### Foot bar

- **Back** — returns to the Calibration hub (accent styling, always first). While a run or
  homing is in flight, tapping Back shows an amber confirmation dialog before navigating;
  the printer continues server-side regardless.

The second button is state-adaptive:

- **Home All** — appears when any of X, Y, or Z are not homed. Homing is required before
  `SCREWS_TILT_CALCULATE` can probe the bed. Disabled while another gating operation is
  active. Sends `G28`.
- **Running…** — appears while the routine is measuring; non-interactive. The Focus body
  also shows the "Measuring bed screws…" status card at this point.
- **Run Again** — appears once results are present. Clears current results and fires a fresh
  probe. Green (expected action).
- **Run** — appears when all axes are homed and no results are shown yet (on entry or after
  navigating back in). Green (expected action). Sends `SCREWS_TILT_CALCULATE`; requires
  `[screws_tilt_adjust]` in your Klipper config.

### Error toast

If Klipper rejects the run (for example, "Bed level exceeds limits"), a red error toast
appears above the foot bar for 4 seconds. The text is the verbatim rejection from Klipper.
A subsequent successful run clears the toast.

## Related

[Concepts](concepts.md) · [Calibration](calibration.md) · [Bed Mesh](bed-mesh.md) ·
[Z Tilt](z-tilt.md)
