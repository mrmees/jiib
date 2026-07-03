# Spoolman

<img src="../screenshots/v0.1.0/spoolman-flox-landscape-light_2026-07-03.png" width="640"/>
<img src="../screenshots/v0.1.0/spoolman-moto-portrait-dark_2026-07-03.png" width="220"/>

Your Spoolman filament inventory, on the printer screen: browse, filter, and set the active spool with one tap.

**Getting there:** Home → spool row (top of the home list, visible when a Spoolman server is configured) · Extrude → spool row (at the bottom of the action list) · Active spool card → **Change**.

## The screen

The Focus card shows the selected spool's detail: a weight bar, recommended print temperatures, and registration date. When no spool is selected, a placeholder occupies the Focus. The sort row and filter row sit at the foot of the Focus region, above the list.

The list holds up to 50 spools from Spoolman, sorted and filtered by the active controls. Tapping a row selects it and loads its detail into the Focus card. Archived spools are excluded from the list by default; if one appears, it carries an amber badge.

The foot bar holds **Back**, **Scan**, and a context-sensitive **Load** or **Unload** button.

## Options & controls

### Focus card

**FillMeter bar** — Remaining filament as a proportion of the original spool weight. The label reads "remaining/original g · X%" when both weights are on record in Spoolman, or "remaining g" when the original weight is unknown (the bar shows at 50% as a neutral indicator in that case). When neither weight is known, the bar is empty. Tapping the bar opens the measure-weight entry (see below).

**Measure weight (field takeover)** — The list field swaps to a numeric entry for the spool's gross weight (spool + filament together) in grams. Enter the number, review the **Spool Weight** and **Expected Total** shown in the info card, then tap **Set** to apply. Pressing Done on the keyboard dismisses the keyboard but does not write — **Set** is the only save action, and is a no-op until a positive value has been entered. **Back** (red) discards without writing. The write goes to Spoolman via `server.spoolman.proxy` (PUT `/v1/spool/{id}/measure`); requires the `spoolman` Moonraker component.

**Nozzle temperature** — Recommended extrude temperature from the Spoolman filament definition; shows "—" when unset. Display only.

**Bed temperature** — Recommended bed temperature from the Spoolman filament definition; shows "—" when unset. Display only.

**Registration date** — Date the spool was registered in Spoolman, in YY-MM-DD format. Display only.

**Archived badge** — Shown in amber when the spool's `archived` flag is set in Spoolman. You can still select and load an archived spool; a warning appears when you go to print. Display only.

**Loaded checkmark** — A green checkmark appears in the Focus header when the selected spool is the currently active spool on the printer. Display only.

### Sort row

Three icon-only tiles directly below the Focus card. Tapping the active tile flips its direction; tapping a different tile switches to that key and resets to its default direction.

- **Name** — Sorts by material, then by name. Default: A → Z.
- **Date** — Sorts by last-used date, then registration date. Default: most recent first.
- **Remaining** — Sorts by remaining weight. Default: lightest first (about-to-run-out spools at the top).

### Filter row

Three icon-only tiles at the foot of the Focus region. An active filter is visually highlighted. Tapping a tile swaps the list field to a filter-selection panel for that category. Filters across categories combine with AND semantics — a spool must satisfy all active filters.

**Material** — Material family filter. The list swaps to a panel showing only the material families present in your inventory: PLA, PETG, ABS/ASA, TPU, PC, Nylon. Multi-select; OR within the category. Selecting "PLA" matches PLA+, PLA Meta, and any other material whose name contains "PLA". Sends `filament.material=<comma-separated terms>` to Spoolman.

**Color** — Color-family filter. The list swaps to a 3×4 grid of 12 named families: Black, White, Natural, Gray, Red, Orange, Yellow, Green, Blue, Purple, Pink, Brown. Multi-select; OR within the category. Classification uses hue and lightness — "Green" matches every greenish spool regardless of its exact hex code. Sends `filament.id=<csv>` derived from the Spoolman filament library. Portrait: swatch above label; landscape: label left of swatch.

**Brand** — Manufacturer filter. The list swaps to a panel of vendor names present in your inventory. Multi-select; OR within the category. Sends `filament.vendor.name=<csv>` to Spoolman.

**Clear** (red, filter picker foot bar) — Removes all selections for the current filter category and re-fetches the list without that filter.

**Done** (green, filter picker foot bar) — Closes the filter picker and returns to the spool list with the current selections applied.

### List rows

Each spool row shows: color swatch dot(s) at the leading edge (up to three dots for multi-color filaments); material and name on the main line (e.g., "PLA · Galaxy Black"); vendor and storage location on the second line (shown only when present); remaining weight in grams at the trailing edge; and either a green "Loaded" badge (the active spool) or an amber "Archived" badge.

Tapping a row selects it; re-tapping the same row does nothing.

When the list is empty: a notice explains whether spools are loading, could not be loaded, or no spools match the active filters.

### Foot bar (normal mode)

**Back** (accent) — Returns to the home screen.

**Scan** (accent) — Opens the QR scan surface.

**Load** (green) — Sets the selected spool as the active spool on the printer. Shown when the selected spool is not currently loaded. Available while printing — this only re-points Spoolman's tracking and does not affect a running print. Sends `server.spoolman.post_spool_id` with the spool id; requires the `spoolman` Moonraker component.

**Unload** (green) — Clears the active spool. Shown when the selected spool is currently loaded. Available while printing. Sends `server.spoolman.post_spool_id` with no spool id (the Moonraker clear contract); requires the `spoolman` Moonraker component.

### QR scan surface

<img src="../screenshots/v0.1.0/spoolman-filters-flox-landscape-light_2026-07-03.png" width="640"/>
<img src="../screenshots/v0.1.0/spoolman-filters-moto-portrait-dark_2026-07-03.png" width="220"/>

Reached from the **Scan** foot bar button. Uses the rear camera by default. Scanned codes that are not Spoolman spool QRs are ignored and scanning continues — aim and move on.

**Accepted QR formats:**
- `web+spoolman:s-<digits>` — the canonical Spoolman spool URI (case-insensitive).
- `http(s)://<host>/spool/show/<digits>` — a Spoolman web URL. Only the numeric spool id is extracted; the URL host is never read, trusted, or used as a Spoolman address.

**Filament QR codes** (`web+spoolman:f-<id>`) are recognized but not loadable; the preview continues with a hint.

**Back** (accent, scan gutter) — Always present; exits the scan surface and returns to the spool list without loading anything.

**Camera flip** — While a live preview is showing (scanning, unrecognized code, or unsupported code), a second gutter button toggles between the rear and front camera.

**Confirm card** — When a valid spool QR is decoded, scanning pauses and the confirm card appears. It shows the spool's material, name, vendor, remaining weight, location, and an archived warning if applicable. If the detail fetch fails, it shows "Spool {id}" with a note and still allows confirm. Scanning never auto-loads — only the confirm step does.

- **Back** (red) — Cancels; returns to scanning without loading anything.
- **Set active** (green) — Sets the spool as the active one. Sends `server.spoolman.post_spool_id` with the spool id.

**Degrade states** — shown when the camera cannot be used:

- *Permission denied* — camera access was not granted. Shows "Camera permission denied" and a **Use picker instead** button that returns to the manual list.
- *No camera* — the device has no usable camera. Shows "No camera available" and **Use picker instead**.
- *Camera busy* — the camera could not be opened (in use by another app). Shows "Camera unavailable" and **Use picker instead**.

### Prefiltered entry (from Files)

When the Files screen warns about a material mismatch or missing spool before printing, the "Pick spool" action opens the Spoolman screen pre-filtered by the file's material type and color hint. The material chip(s) are pre-selected; the color hint highlights the nearest palette family without hard-filtering by color. Clear or adjust the filters as normal.

## Related

[Home](home.md) · [Extrude](extrude.md) · [Files](files.md)
