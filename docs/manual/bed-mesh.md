# Bed Mesh

<table><tr>
<td valign="top"><img src="../screenshots/v0.1.0/bedmesh-flox-landscape-light_2026-07-03.png" width="640"/></td>
<td valign="top"><img src="../screenshots/v0.1.0/bedmesh-moto-portrait-dark_2026-07-03.png" width="220"/></td>
</tr></table>

Probe the bed surface, view the height map, and manage the saved profiles that Klipper loads
at print start to compensate for bed geometry.

**Getting there:** Home → Calibration → tap the **Bed Mesh** row → **Open**. The Bed Mesh
row is only present when Klipper reports a `bed_mesh` object.

## The screen

The **Focus card** shows a square heatmap of the currently active (loaded) mesh. Colors
range from the configured low-color at the lowest measured point to the configured high-color
at the highest, with a fixed gray mid-point. When no mesh is loaded the Focus card shows an
empty-state icon and a message; when a saved profile is selected in the list below, the Focus card
previews that profile's stored points without changing the printer's active mesh. A pencil
icon in the Focus header opens the profile edit form (hidden while printing or calibrating).

The **list** contains, in order from top:

- **Clear Mesh** — present only when a mesh is loaded and the printer is not printing or calibrating.
- Saved profile rows — one per profile stored on the printer.
- **Color Scale** — cycles the heatmap color scale; current mode shown at the right.
- **Mesh Config** — opens visualization settings.

The **foot bar** holds **Back** plus one calibration action: **Home All** when the printer is
not yet homed, or **Calibrate** (green) once it is. Both are hidden while printing.

## Options & controls

### Focus card

**Heatmap display** — updates live as the active mesh changes. Selecting a profile row below
previews that profile's stored probe points in the Focus card (using a coarser grid than the live
interpolated mesh). Deselecting or editing returns to the live mesh.

**Edit icon (pencil, header trailing slot)** — opens the profile edit form in the Focus card.
Hidden while the printer is printing, while a calibration or homing operation is in
progress, or while the Mesh Config subpage is open.

### Profile edit form (Focus morph)

The form appears after tapping the pencil icon. Its controls depend on which profile is being
targeted:

- **Active mesh — unsaved** (the in-memory mesh has no saved counterpart, or is Klipper's
  internal `default`): an editable name field (initially blank), a **Save** button (green,
  enabled when the name is valid), and **Cancel**. Saving stores the in-memory mesh under the
  typed name. > Sends `BED_MESH_PROFILE SAVE=<name>`; requires `[bed_mesh]` in your Klipper
  config.

- **Active mesh — saved** (the active mesh corresponds to a saved profile): an editable name
  field pre-filled with the current profile name, a **Save** button (green, enabled when the
  name is valid and has changed — this is a rename), a **Delete** button (red), and
  **Cancel**. Renaming sends the new name as one atomic save-then-remove script. > Rename
  sends `BED_MESH_PROFILE SAVE=<new>\nBED_MESH_PROFILE REMOVE=<old>` as a single ordered
  gcode block to prevent the two operations from racing.

- **Saved profile selected (not active)**: a read-only name field showing the selected
  profile's name, an **Apply** button (green — loads this profile as the active mesh), a
  **Delete** button (red), and **Cancel**. > Apply sends `BED_MESH_PROFILE LOAD=<name>`.

**Profile name rules:** letters, digits, underscores, hyphens, and periods (`[A-Za-z0-9_.-]`,
max 64 characters). The name `default` is reserved by Klipper and cannot be used for new
saves. The **Save** button stays disabled while the name does not meet these rules.

**Delete** (red) — raises a confirmation dialog before removing the profile. > Sends
`BED_MESH_PROFILE REMOVE=<name>`; does not trigger a `SAVE_CONFIG` prompt (the removal
is in-session only until the next firmware restart, by design).

**SAVE_CONFIG prompt** — after a successful profile save or rename, an amber confirmation
dialog asks whether to run `SAVE_CONFIG`, which writes the profile to `printer.cfg` and
restarts Klipper. Dismissing the dialog leaves the profile in Klipper's in-memory state; it
will be lost on firmware restart if you skip this step.

### Profile list rows

Each saved profile appears as a row. The trailing slot shows:

- **Span** — the max-minus-min Z deviation across the profile's probe points, formatted as
  `0.000 mm`. Only shown for profiles whose full point data has been loaded this session.
- **Active dot** — a small accent-colored circle marking whichever profile is currently
  loaded.

Tap a row to select it and preview it in the Focus card. Tap again (or tap the pencil icon) to
edit or apply it.

### Clear Mesh row

Shown when a mesh is loaded, not printing, and not calibrating or homing. Unloads the active mesh from
the printer's live state. > Sends `BED_MESH_CLEAR`; saved profiles are untouched; no
`SAVE_CONFIG` is triggered.

### Color Scale row

Cycles the heatmap color scale mode. The current mode is shown at the right of the row. Six
modes in order:

| Mode | Scale endpoints |
|------|-----------------|
| **RELATIVE** | The measured min and max of the current mesh fill the full color ramp. Every mesh looks fully saturated; useful for seeing shape regardless of total deviation. |
| **PLATE** | Symmetric about 0; the larger absolute extreme sets full saturation both ways (e.g., worst deviation −0.15 mm → scale is −0.15 to +0.15). |
| **±0.10** | Fixed ±0.10 mm about 0. |
| **±0.25** | Fixed ±0.25 mm about 0. |
| **±0.50** | Fixed ±0.50 mm about 0. |
| **±1.00** | Fixed ±1.00 mm about 0. |

The fixed modes are useful for comparing meshes over time: a flat mesh stays mostly gray at
±0.25 even if RELATIVE would have stretched it across the full ramp.

### Mesh Config row

Opens the visualization settings subpage. Back in this subpage returns to the profile list.
Four settings rows:

- **View Type** — choose how the mesh is drawn. See [View Type](#view-type) below.
- **High Color** — the color mapped to the highest Z deviation. See [Color picker](#high-color--low-color-pickers).
- **Low Color** — the color mapped to the lowest Z deviation.
- **Preview** — shows the current mesh in the Focus card with the current color and view settings applied, without leaving Mesh Config.

Settings are per-printer and saved immediately to DataStore; they persist across app restarts.

### View Type

Three options, selected via icon buttons in the Focus card. The active view's button shows a soft
accent fill.

- **2D Heatmap** — top-down grid; each cell is filled with a color from the ramp by its Z
  value. The classic bed-mesh view.
- **3D Mesh** — isometric wireframe; the grid lifts by deviation so the bed's shape is
  visible in three dimensions. Lines are colored by height using the same ramp.
- **Probe Points** — measured probe points only, rendered as height-colored dots with no
  fill or interpolation between them. Useful for seeing the raw probe data without the
  interpolated grid.

### High Color / Low Color pickers

A 4 × 2 grid of eight colored swatches, no labels. Tap a swatch to assign it to the high
or low end of the ramp. The selected swatch has an accent outline and a soft fill. All
eight colors are theme-aware and update live when the theme changes.

The eight slots, in order:

| Slot | Color role |
|------|------------|
| 0 | Accent (theme accent color) |
| 1 | Stop (red — danger / stop) |
| 2 | Heat (orange / amber) |
| 3 | Go (green — expected action) |
| 4–7 | Data pool colors (theme-defined) |

Default high color is **Accent** (slot 0); default low color is **pool[0]** (slot 4).

### Foot bar (profile list mode)

- **Back** — leaves Bed Mesh (accent styling). While calibrating or homing, tapping Back
  triggers a confirmation step because an operation is in progress.
- **Home All** — shown when the printer is not homed and not printing; disabled while
  calibrating or homing. > Sends `G28`; HardLock gating (the foot bar button stays disabled
  until homing completes).
- **Calibrate** — shown when the printer is homed and not printing; green styling. Probes the
  bed and builds a new mesh in memory. While running, the Focus card switches to a "Calibrating"
  status card and the list dims; the e-stop remains live. > Sends `BED_MESH_CALIBRATE`;
  requires `[bed_mesh]` (and a configured probe) in your Klipper config; gated on the
  `bed_mesh` object; HardLock gating with a 10-minute timeout.

### Gating and print-state behavior

- **While printing:** Calibrate and Home All are hidden. Clear Mesh is hidden. The pencil
  icon is hidden. The heatmap and profile list remain visible and navigable.
- **While calibrating or homing:** The Focus body is replaced by a "Calibrating" or "Homing"
  status card. The list is dimmed. Clear Mesh is hidden. The pencil icon is hidden. Back is
  guarded by a confirmation step. The e-stop icon stays live in the Focus header.
- **Unknown state** (the link dropped while a calibration or homing was in progress): an
  explicit "still running?" card replaces the Focus body and requires manual dismissal before
  normal navigation resumes.

## Related

[Concepts](concepts.md) · [Calibration](calibration.md) · [Nozzle Distance](nozzle-distance.md)
