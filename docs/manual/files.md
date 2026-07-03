# Files

<img src="../screenshots/v0.1.0/files-flox-landscape-light_2026-07-03.png" width="640"/>
<img src="../screenshots/v0.1.0/files-moto-portrait-dark_2026-07-03.png" width="220"/>

Browse the gcode files stored on your printer and start or delete prints from here.

**Getting there:** Home → Files row.

## The screen

The **Focus card** shows a placeholder file icon when nothing is selected. Tap any file in the list and the card fills in: the gcode thumbnail dims behind a stat block covering estimated print time, filament length and weight, layer count, model height, file size, and last-modified date. Stats load asynchronously — the card shows a loading indicator until Moonraker returns the metadata.

Below the Focus card (portrait: directly under it; landscape: still in the left pane beneath the card) a **sort row** with two tiles — **Date** and **Size** — controls list order.

The **list** is a flat view of every gcode file in your printer's `gcodes` folder, one row per file. Each row shows the thumbnail on the left, the filename on the first line, and the modified date and file size on the second. Tapping a row selects it and starts loading its metadata.

The **foot bar** holds three buttons: **Back**, **Print**, and **Delete**.

## Options & controls

### Focus card

**Placeholder** — shown when no file is selected. Displays the Files screen icon.

**Detail card** — shown when a file is selected. The gcode thumbnail is the dimmed background (alpha 0.3). The foreground shows a stat block; any stat absent from the metadata is omitted:

- **Est** — slicer-estimated print duration (e.g. `2h 35m`).
- **Filament** — total extrusion length in mm; weight in grams appended if available (e.g. `14823 mm · 42.6 g`).
- **Layers** — total layer count.
- **Height** — model height in mm.
- **Size** — file size (B / KB / MB).
- **Modified** — last-modified timestamp (`MMM d, HH:mm`).

While metadata is loading, a loading indicator row appears in place of the slicer stats.

> Metadata uses Moonraker's `server.files.metadata` endpoint. Stats depend on what your slicer writes into the gcode file — missing fields are simply not shown.

### Sort row

**Date** tile — sorts by last-modified timestamp, newest first by default. Tap again to reverse to oldest first. The active tile shows an arrow indicating current direction.

**Size** tile — sorts by file size, largest first by default. Tap again to reverse. Changing the sort field or reversing direction resets the list scroll position to the top.

> The list is sorted client-side from the file listing returned by `server.files.get_directory`.

### List rows

Each row contains:

- **Thumbnail** — a small preview image pulled from the file's embedded gcode thumbnail. Absent if the slicer did not embed one.
- **Filename** — ellipsized to one line if long. Highlighted in accent color when selected.
- **Modified date** — left-aligned on the secondary line.
- **File size** — right-aligned on the secondary line.

**Empty state** — when no files are found, a centered message reads "No printable gcode files. Add files in Moonraker, then reopen Files." While the directory is loading, "Loading files…" is shown instead. If the load fails, an error message appears and an error toast is shown at the bottom of the list.

**Tap** a row to select it and begin loading its metadata into the Focus card. Tapping an already-selected file does nothing.

### Foot bar — Back

**Back** (accent) — returns to the previous screen. Always available.

### Foot bar — Print

**Print** (green) — confirms and starts the selected file. Dimmed and disabled when:

- No file is selected.
- The printer is not in a state that can accept a new print job.
- A print-start request is already in flight (pending confirmation from Moonraker).

Tapping Print when enabled opens a confirmation dialog before anything is sent to the printer.

> Calls `printer.print.start` with the selected file's relative path.

#### Print confirmation dialog

When no Spoolman warnings apply, a standard confirm dialog shows the filename and file details (path, estimated time, size, date, filament, layers). **Print** (confirm) proceeds; **Cancel** dismisses.

#### Spool warning overlay (Spoolman only)

When a Spoolman server is configured, an active spool is set, and the spool-compatibility check raises warnings (material mismatch, low remaining weight, etc.), the standard dialog is replaced by an amber warning overlay listing the issues. Four actions are offered:

- **Pick spool** (accent) — opens the Spoolman spool picker, pre-filtered to filament types and colors declared by the gcode file.
- **Scan** (accent) — opens the QR scanner to identify a spool by its label.
- **Print anyway** (amber) — proceeds immediately; the gate is advisory and never blocks a print.
- **Back** (accent) — dismisses the overlay without printing.

This overlay is skipped entirely when Spoolman is not configured.

### Foot bar — Delete

**Delete** (red) — confirms and permanently deletes the selected file from the printer. Dimmed and disabled when:

- No file is selected.
- The selected file is the file that is **currently printing or paused**. All other files remain deletable even while a print is in progress.

Tapping Delete when enabled opens a confirmation dialog.

> Calls `server.files.delete_file` with the gcodes-prefixed path. The file is removed from the list immediately after the request is sent; the printer's file system reflects the change once Moonraker completes it.

#### Delete confirmation dialog

Shows the filename and file details. **Delete** (destructive confirm) proceeds; **Cancel** dismisses. Styled as a destructive action.

## Related

[Concepts](concepts.md) — gating overlays, e-stop, and connection states explained app-wide.  
[Spoolman](spoolman.md) — managing spools and the spool-link workflow.  
[Heaters](heaters.md) — preheat before starting a print.
