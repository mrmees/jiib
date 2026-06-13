# Move Screen Redesign — Design Spec

**Date:** 2026-06-13
**Status:** Approved (brainstorm complete; ready for implementation plan)
**Author:** Matthew Mees + Claude

## Summary

Replace the current jog-pad Move screen with a **Move Hub**: a single screen whose
**Field** (action list) selects what the **Focus** displays. The Focus cycles through
discrete control surfaces — Touch Move (tap-to-move bed map), XY Position (scrubbers),
Z Position (scrubbers), Microstep (increment jogger), per-bookmark Move/Delete, and a
Save-Location dialog. The old jog-pad screen is preserved as `OldMoveScreen` and wired
into the debug Gallery for side-by-side comparison; it is removed before ship.

This conforms to the existing Focus/Field grammar, FocusFrame header law, component
classes (`ListRow`, `IncrementPicker`, `FootButtonBar`, `ConfirmGuard`), and THEME-01
token routing. The architecture mirrors the Fine-Tune Hub (`navigateTo` / `applyEntryReset`).

## Architecture

- **`ScreenScaffold`** with a Field (action list) and a Focus whose content is driven by a
  `MoveMode` enum: `Overview` (default), `TouchMove`, `XY`, `Z`, `Microstep`,
  `Bookmark(id)`, `SaveDialog`.
- New `MoveScreen` Composable + `MoveHolder`/`MoveVm` (extend the existing holder, which
  already exposes `x/y/z: Double?` and `xHomed/yHomed/zHomed/allHomed`).
- The current `MoveScreen` composables are renamed to **`OldMoveScreen`** and wired into the
  debug `GalleryScreen` (dev-only; removed before ship).
- Every Focus state is a **`FocusFrame`** carrying the header law: title + the icon of the
  row that opened it; idle↔printing e-stop morph handled by FocusFrame as usual.

### Hub entry/reset semantics
Selecting a Field row sets `MoveMode` and runs an entry-reset for that mode (clears any
staged target / pending scrubber preview), mirroring Fine-Tune's `applyEntryReset` — including
on same-destination re-selection. Home rows do **not** change `MoveMode` (fire-and-stay).

## Field list (the Field region)

`ListRow`s, in this order. Each row shows a leading icon, a label, and (for axis rows) is
gated on homed state. Token-routed intent coloring.

| # | Row | Icon | Shown / enabled when | Action |
|---|-----|------|----------------------|--------|
| 1 | Home All | `wifi_home` (`HomeStateUnhomed`) | **Always** (escape hatch — re-home permitted) | `G28` |
| 2 | Home XY | `wifi_home` | XY not homed | `G28 X Y` |
| 3 | Home Z | `wifi_home` | Z not homed | `G28 Z` |
| 4 | Touch Move | `my_location` *(new)* | all homed | → `TouchMove` |
| 5 | XY Position | `control_camera` *(new)* | XY homed | → `XY` |
| 6 | Z Position | `swap_vert` *(new)* | **Z homed** | → `Z` |
| 7 | Microstep | `instant_mix` (`FineTune`, reuse) | any axis homed | → `Microstep` |
| 8 | *(saved bookmarks…)* | `bookmark` *(new)* | always (one row per saved location) | → `Bookmark(id)` |
| 9 | Save Location | `bookmark_add` *(new)* | all XYZ known (homed) | → `SaveDialog` |

- **Home All** is always present (intentional escape hatch); Home XY / Home Z appear only when
  that axis group is unhomed.
- Default Focus (`Overview`) = read-only `BedMapView` showing current position when homed, or a
  "home the printer" prompt when nothing is homed.

### Icon registry additions
New entries to add via the `material-icon-bucket.json` workflow (owner-approved 2026-06-13):
`my_location`, `control_camera`, `swap_vert`, `bookmark`, `bookmark_add`. Reuse existing
`FineTune` (`instant_mix`) and `HomeStateUnhomed` (`wifi_home`).

## Focus sub-modes

### Touch Move (`my_location`)
- Layout: centered header (FocusFrame) · one-line description **"Tap to move, hold to refine"** ·
  `BedMapView` · current X/Y readout at the bottom (GeistMono tabular).
- **Tap** = immediate absolute move to the tapped point.
- **Press-drag** = a target marker follows the finger with a live X/Y readout; **release commits**
  the absolute move. (Both gestures end in a real move.)
- On commit, an **accent travel line** is drawn from the start position to the target until the
  move completes (see *Travel-line completion*).
- Moves XY only; Z unchanged.

### XY Position (`control_camera`)
- Same layout grammar. Central `BedMapView` shows the live target dot. A **horizontal X
  scrubber** along the bottom and a **vertical Y scrubber** up the side; each range =
  `axisMinimum[axis]..axisMaximum[axis]`.
- Scrubber `onValueChange` moves the preview dot (= "hold to preview"); `onSettle` (release)
  commits the absolute move (= "release to go"). Matches the Touch Move tap/hold contract.

### Z Position (`swap_vert`)
- Two **vertical** scrubbers, **both absolute Z**:
  - **Fine** — range `0..50` mm (resolution for near-bed work).
  - **Full** — range `0..axisMaximum[z]`.
- The two stay in sync (both reflect current Z; Fine clamps its display at 50). `onSettle`
  commits `G1 Z<value>`. Z value shown in the main area (mono tabular).
- Gated on Z homed.

### Microstep (`instant_mix`)
- `IncrementPicker` with steps `[0.01, 0.025, 0.1, 0.25, 1, 2.5, 10]` (Double).
- Three ± stepper rows — **X**, **Y**, **Z** — each fires a **relative** jog of the selected
  increment on that axis (reuses `PrinterCommands.jog`, which wraps `SAVE_GCODE_STATE → G91 →
  G1 → RESTORE`).
- Picker enabled if any axis homed; each axis's ± enabled only if that axis is homed.

### Bookmark (`bookmark`) — a saved row tapped
- Header = bookmark name. `BedMapView` shows **both** the current position and the bookmark
  destination, with a connecting line.
- `FootButtonBar`:
  - **Move** (Intent.Go / green) — absolute move to the bookmark's XY, plus Z if the bookmark
    saved a Z value. Travel line drawn during the move.
  - **Delete** (Intent.Danger / red) — removes the bookmark behind a `ConfirmGuard` (destructive).

### Save Location (`bookmark_add`) — Save row tapped
- Focus becomes a save form (field-takeover style, à la BedMesh SaveName):
  - **Name** text field — the sanctioned save-name keyboard exception.
  - **Checkbox** "Include Z height (Z = NN.N)".
  - **Save** (Intent.Go) / **Cancel**.
- On Save → persist `SavedLocation(name, x, y, z?)` via `SavedLocationPrefs`; the new bookmark
  appears in the Field list. Bookmarks without Z move XY-only.
- Available only when all XYZ are known (homed).

## New infrastructure

1. **Axis limits in `PrinterState`** — subscribe `toolhead.axis_minimum` and
   `toolhead.axis_maximum` (arrays `[x,y,z,e]`); add `axisMinimum`/`axisMaximum:
   ImmutableList<Double>?`. These define the bed extent and every scrubber range. **Negative
   coordinates are valid** — map `axis_minimum..axis_maximum` directly; never assume a corner
   origin. (Klipper does not store bed size anywhere; the motion limits as the user configured
   them are the only source of truth.)

2. **Vertical Scrubber** — **generalize the existing `Scrubber`** with an `orientation`
   param (`Horizontal` default, `Vertical`). Vertical mode uses a bottom-anchored fill and a
   vertical track; keep the one component class owning its style. (No separate `VerticalScrubber`.)

3. **`BedMapView`** — new **Compose** `Canvas` component (not classic Views — low-churn, repaints
   only on tap/drag and the 250 ms position tick):
   - Aspect-locked bed rectangle (no stretch) derived from `(axisMax - axisMin)` per axis.
   - Current-position marker; optional target marker; accent travel line.
   - All colors token-routed (THEME-01). Reused by Touch Move, XY, and Bookmark.
   - Maps touch ↔ bed coordinates honoring negative origins.

4. **`SavedLocationPrefs`** — new `DataStore<Preferences>` store mirroring `MacroPrefs`:
   `SavedLocation(name, x, y, z?)` entries JSON-encoded (kotlinx.serialization) in a string set;
   a holder flow exposes the list; wired through `AppContainer`. Writes go through the
   process-lifetime write scope (never a composition scope — Compose write-scope cancellation trap).

5. **`PrinterCommands.moveTo(x?, y?, z?, feed)`** → `G90` + `G1 [X..] [Y..] [Z..] F<feed>`,
   clamped to `axisMinimum..axisMaximum`. **Travel feed = the printer's configured
   `max_velocity`** (`configfile.settings.printer.max_velocity`, mm/s → ×60 mm/min) — no
   hardcoded constant. ⚠️ New `CommandSpec`(s) **require** matching rows in
   `docs/commands/catalog.json` + `printer-matrix.json` or the **D-10 `CommandCatalogDriftTest`
   fails** (known planner blind spot).

## Travel-line completion

A move is "complete" when the live `gcodePosition` is within an epsilon of the commanded
target on every moved axis (position-epsilon, evaluated on the 250 ms position tick). The
accent travel line is drawn from start→target while pending and cleared on completion. (Velocity
subscription via `motion_report` was considered and rejected as unnecessary plumbing.)

## Commands reused

- Homing: `homeAll()` `G28`, `homeXY()` `G28 X Y`, `homeAxis("Z")` `G28 Z`.
- Microstep: `jog(axis, mm, feed)` (relative, force-state-wrapped).
- New: `moveTo(...)` (absolute, clamped) per above.

## Testing

**Host unit tests**
- `moveTo` clamps to `axisMinimum..axisMaximum` (incl. negative-origin cases).
- Touch ↔ bed coordinate mapping is no-stretch and honors negative origins.
- `SavedLocationPrefs` round-trip (save / list / delete; with and without Z).
- Field-row availability per homed-state matrix.
- Travel-line completion (position-epsilon) state transitions.

**On-device UAT (flox + moto, both ABI slices)**
- Homing (All / XY / Z), re-home via always-present Home All.
- Touch Move tap + hold-drag commit; travel line appears and clears.
- XY scrubbers (incl. vertical Y) and Z fine/full scrubbers commit correctly.
- Microstep ± at each increment on X/Y/Z.
- Save → recall (Move) → Delete a bookmark; with and without Z.
- Portrait ↔ landscape rotation in each sub-mode.

## Open items / deferred

- Move feedrate sourcing: resolve exact `configfile.settings.printer.max_velocity` query path
  during planning (one-time query vs. subscribe); fallback if a printer omits it.
- RGB/visual polish of the bed map (marker styling) is expected to iterate on-device.

## Decisions log (resolved during brainstorm)

1. Touch Move: tap = go immediately; hold-drag = refine then release commits. Both move.
2. Z Position: two scrubbers, both absolute Z — Fine 0–50, Full 0–Zmax.
3. Save dialog: name field (keyboard) + "include Z" checkbox.
4. Old screen: rename to `OldMoveScreen`, keep reachable in debug Gallery (removed before ship).
5. Home All stays available at all times (escape hatch); XY/Z home rows only when unhomed.
6. Z Position gated on **Z** homed (note's "y homed" was a typo).
7. Vertical scrubber: generalize the existing `Scrubber` (orientation param).
8. Travel feed: printer's configured `max_velocity`, not a hardcoded constant.
9. Travel-line completion: position-epsilon.
10. Bed extent: `toolhead.axis_minimum/maximum` (motion volume); negative coordinates valid.
