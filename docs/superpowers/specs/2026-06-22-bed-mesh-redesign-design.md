# Bed Mesh Screen Redesign

**Date:** 2026-06-22
**Status:** Design — approved for planning
**Scope:** One coherent spec covering the Bed Mesh screen interaction model, per-profile preview, the Focus edit morph, and the Mesh Config subpage. The **static iso wireframe view mode is explicitly split to a fast-follow** spec.

## Problem

The current Bed Mesh screen (`ui/calibration/BedMeshScreen.kt`) crams all per-profile actions into a state-adaptive `FootButtonBar` (Back + Home All / Apply+Remove / Calibrate+Save across three footer states), shows only the *live active* mesh in Focus, and offers no way to preview a saved profile without loading it or to configure how the mesh is drawn. The interaction model is hard to read and doesn't follow the lists-first grammar used everywhere else.

This redesign restructures it into the standard Focus/Field grammar: a clean Field list whose selection drives the Focus, per-profile actions contextual to what's shown (in Focus, not the footer), and a Mesh Config subpage for render settings.

## Decisions (from brainstorming)

1. **Preview model:** Focus defaults to the live/active mesh. Tapping a saved profile **previews** its stored probed points (read-only, no printer mutation). An explicit **Apply** loads it. Re-tapping a selected row does nothing (no deselect gesture). The active profile is itself a selectable, active-marked row, so tapping it returns to the live view.
2. **Rename:** `Save`/rename is offered **only for the active mesh** (Klipper can only `SAVE` the active mesh under a name). Previewing a non-active profile offers Apply + Delete, no rename.
3. **View types:** Filled heatmap (default) + Colored probe points ship now. **Static iso wireframe is a fast-follow.** (Numeric-values mode rejected.)
4. **Color override scope:** **Per-printer**, keyed by `profileId` (like `TraceStylePrefs`), defaulting to the current theme colors when unset.
5. **Color picker source:** the **theme data-pool** swatches (not the 12-color spool palette, not a full HSV picker) — stays on-theme by construction.
6. **Scale Mode:** stays as a **Focus overlay toggle** on the main screen, but its transparency is increased substantially so the mesh reads through it. Not moved into Mesh Config.

## Interaction model

### Field list (top → bottom)

1. **Clear Mesh** — shown **only when a mesh is currently loaded**. Runs new `BED_MESH_CLEAR` (runtime-only, unloads the active mesh, keeps all saved profiles, no SAVE_CONFIG). Row vanishes once cleared.
2. **Saved profiles** — one row per saved profile. The currently-active profile carries an "active" marker. Tapping a row **previews** that profile in Focus (renders its stored probed points). Tapping the active-marked row shows the live mesh.
3. **Mesh Config** — pinned at the bottom. Opens the Mesh Config subpage.

### Footer (global actions only)

- **Back** (accent) — first button, always.
- Conditional second button: **Home All** when not homed → **Calibrate** (`BED_MESH_CALIBRATE`, produces a fresh unsaved "default" mesh in Focus) when homed.

All per-profile actions (Apply / Save / Delete) leave the footer and move into the Focus edit morph below.

### Focus default & selection

- Default: the live/active mesh, or the empty-state when none is loaded.
- Selecting a list row swaps Focus to that profile's preview.
- No live-preview-deselect: selection persists until another row is tapped.

## Focus edit morph

The Focus header's **trailing slot** carries an **edit pencil** (`FocusFrame` already exposes `trailingActionIcon` / `trailingActionContentDescription` — this is its intended per-page use). The e-stop morph occupies the **leading/start** icon slot, so there is no collision (confirmed in `FocusFrame.kt:182-198`).

Tapping the pencil swaps the Focus from mesh-render → a **docked-action edit form** (consistent with other adjusters): a name text box (honest keyboard entry; reuse `isValidProfileName` allowlist) + contextual buttons keyed to **what is in Focus**:

| What's in Focus | Name box | Row 1 | Row 2 |
|---|---|---|---|
| **Active, unsaved** — `profile_name` is empty **or** `"default"` (the runtime profile a bare `BED_MESH_CALIBRATE` writes), or a just-deleted-but-still-loaded mesh | editable, prefill **blank/suggested** (never `"default"`) | **Save** (`BED_MESH_PROFILE SAVE=name` → SAVE_CONFIG guard) | — |
| **Active, saved** — `profile_name` ≠ `"default"` and present in `profiles` | editable, prefill name | **Save** *only if name changed* = true rename: `SAVE=new` → `REMOVE=old` → SAVE_CONFIG (safe because active) | **Delete** (`REMOVE=name` → SAVE_CONFIG; mesh **stays loaded as Active-unsaved**, not cleared) |
| **Previewed, non-active** profile | shown **read-only** (no rename) | **Apply** (`BED_MESH_PROFILE LOAD=name`) | **Delete** (`REMOVE=name` → SAVE_CONFIG) |

- **`"default"` is reserved** (Klipper rejects `BED_MESH_PROFILE SAVE=default`). `isValidProfileName` must reject `default` as a save/rename target, and the post-calibrate mesh (`profile_name == "default"`) is treated as **Active-unsaved**, not as a saved profile.
- `Save` is enabled only when editing the active mesh and (for a saved profile) the name changed.
- `Apply` appears only for non-active previews.
- `Delete` appears whenever a saved profile is in focus. Deleting the **active** profile only runs `REMOVE` (+SAVE_CONFIG) — it does **not** unload the mesh; the mesh remains loaded and falls back to the **Active-unsaved** state (Save available). Use **Clear Mesh** to explicitly unload.
- SAVE_CONFIG keeps its existing amber restart-guard (`BedMeshScreen` WR-02/WR-05 handling).

## Mesh Config subpage

Reached via the bottom **Mesh Config** row. Standard lists-first grammar — **each Field row has its own Focus editor** (same pattern as Theme / Increment Values). Not a live panel.

- **Field rows:**
  - **View Type** → Focus = selector: Filled heatmap (default) · Colored probe points. *(Static iso wireframe added in the fast-follow.)*
  - **High Color** → Focus = theme data-pool swatch grid (4 pool slots + an Accent option). Per-printer override storing a **slot selector** (not ARGB); default = **Accent** (current `seriesColor(0)`).
  - **Low Color** → Focus = theme data-pool swatch grid. Per-printer override storing a slot selector; default = the slot matching current `seriesColor(1)`.
  - **Preview** → Focus = the current mesh rendered with the user's current settings (the one place the combined result is shown).
- **Footer:** Back → returns to the main mesh screen.

## Build scope

### Back-end / data

- **`BED_MESH_CLEAR` command** — new `CommandSpec` in `CommandRegistry` + builder in `PrinterCommands` (gcode `BED_MESH_CLEAR`, runtime-only, no SAVE_CONFIG, availability `ObjectPresent("bed_mesh")`).
- **Profile-preview parsing** — `BedMeshObject` (`state/PrinterState.kt:310`), the reducer (`state/PrinterStateReducer.kt:236`, which currently **drops** the profile payloads) and `BedMeshModel` (`calibration/BedMeshModel.kt`) currently keep only profile *names*. Preserve each saved profile's payload — exact shape `profiles[name].points` (probed matrix) **and** `profiles[name].mesh_params` (extents: `min_x/max_x/min_y/max_y`, counts, pps, algo, tension) — so a non-active profile can be rendered without loading it. Build a preview `BedMeshModel` from the selected profile with **`meshMatrix = points` AND `probedMatrix = points`** (the heatmap fill reads `meshMatrix` and treats an empty matrix as empty-state, `BedMeshHeatmapView.kt:136`; coarser than the live interpolated mesh, accepted).
- **Per-printer render prefs** — new DataStore prefs keyed by `profileId`, wired like **`TemperatureHolder`** (`ui/temperature/TemperatureHolder.kt:65`): the holder takes the prefs + `container.activeProfileId` and seeds via `flatMapLatest`, writes route through the **AppContainer write scope** (intent methods), not a composition scope. Note `BedMeshHolder` currently takes only `scope/store/events` (`ui/shell/AppShell.kt:389`) — this adds the prefs/profileId inputs. Stored values: selected **View Type**, and High/Low color as a **theme data-pool slot selector — NOT an ARGB value** (storing ARGB freezes colors across theme changes). The pool is **4 slots** (`theme/ThemeResolver.kt:15`); `seriesColor(0)` is the **accent** (not a pool slot) and `seriesColor(1)` shifts meaning across Simple/HighContrast palette modes (`theme/SeriesColor.kt:37`). Store a stable selector (slot id `0..3` plus an `Accent`/`Default` sentinel) resolved to a color at render time. Defaults (unset): **High = Accent** (current `seriesColor(0)`), **Low = the slot matching current `seriesColor(1)`**.

### UI / render

- **`BedMeshHeatmapView`** (`render/BedMeshHeatmapView.kt`) gains a `viewMode`: `HEATMAP` (current fill) vs `PROBE_POINTS` (no fill; probe dots colored by the low→high ramp instead of the current faint `--text-3` overlay). Threaded through `BedMeshHeatmapHost.setMesh`.
- **Ramp endpoints** read the per-printer High/Low override in `applyTokens`, falling back to theme `seriesColor` when unset.
- **`BedMeshScreen` Field restructure** — Clear Mesh (conditional top) · profiles (active-marked, tap→preview) · Mesh Config (pinned bottom); footer simplified to Back + (Home All | Calibrate). Selection state drives Focus content.
- **Focus edit morph** — `trailingActionIcon` pencil → docked-action edit form with the contextual button matrix above.
- **Mesh Config subpage** — extend the `MeshFieldMode` sealed class (or a sibling nav state) with a Config list + nested editors for View Type / High Color / Low Color / Preview, each rendering in Focus.
- **Scale overlay** — increase `ScaleToggle` overlay transparency substantially so the mesh reads through.

### Out of scope (fast-follow)

- **Static iso wireframe** view mode (fixed-angle, height-colored line drawing; no rotation/touch) — its own spec after this lands. The View Type selector is built to accept a third option.

## Risks & notes

- **Coarse preview:** saved-profile previews render from probed points only (no stored interpolated grid). Visually blockier than the live mesh — accepted in brainstorming.
- **Rename writes config:** true rename (active mesh) runs SAVE→REMOVE→SAVE_CONFIG and reboots Klipper config — gated behind the existing amber SAVE_CONFIG guard and only offered for the active mesh.
- **Data-pool size:** the number of theme data-pool colors and `seriesColor(n)` indexing must be confirmed during planning to ensure the swatch grid and the default high/low selections are valid across all palette modes.
- **`default` reserved (Codex BLOCKER):** the post-calibrate mesh is `profile_name == "default"` and cannot be saved under that name; it is handled as **Active-unsaved**. `isValidProfileName` must reject `default`.
- **Active Delete leaves a loaded mesh:** `REMOVE` of the active profile does not clear the mesh — the result is an Active-unsaved mesh (Save available). Clear Mesh is the explicit unload.
- **Mid-print:** the trailing edit pencil and Calibrate/Clear are inert/hidden while printing; the leading-slot e-stop morph is unaffected.

## Test surface

- Unit: `BED_MESH_CLEAR` builder/spec; profile-points parsing from a representative `bed_mesh` JSON (incl. `profiles[].points` + `mesh_params`); per-printer color/view-type prefs round-trip + profileId scoping; the edit-button matrix selection logic (pure function of active-ness + name-changed).
- On-device UAT (flox + moto): preview a saved profile, Apply, rename the active mesh, Delete, Clear, Calibrate; Mesh Config color override per printer; View Type swap; Scale overlay transparency legibility.
