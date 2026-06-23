# Bed Mesh — Iso Wireframe View (3rd View Type)

**Date:** 2026-06-22
**Status:** Design — approved for planning
**Branch:** `feat/bed-mesh-redesign` (added on top of the redesign, merged together)
**Scope:** The static isometric wireframe view mode that the bed-mesh redesign spec
(`2026-06-22-bed-mesh-redesign-design.md`) explicitly split out as a fast-follow. Adds a **third
`BedMeshViewType`** rendered by `BedMeshHeatmapView`. No new screen, no new control, no new data.

## Problem / context

The redesign shipped two View Types — **Filled heatmap** (`HEATMAP`) and **Colored probe points**
(`PROBE_POINTS`) — and built the View Type selector to accept a third option. This spec fills that
slot with a **static 3D isometric wireframe** of the mesh: the familiar Mainsail/Fluidd "3D bed
mesh" idea, but fixed-angle (no rotation/touch) and drawn lean for the Adreno-320 floor.

It reuses everything already in place: the `meshMatrix` vertices, the bed extents, the
`deviationToRamp` scale-mode mapping, and the baked OKLCH `rampColor`. The new **Color Scale** Field
row (added this session) drives both the color saturation *and* the 3D height — one control.

## Decisions (from brainstorming, owner-approved)

1. **Render style = WIREFRAME LINES only.** A lattice of grid lines following the mesh heights — no
   filled facets, no surface shading. Cheapest on the fill-rate-bound GPU; reads clearly as a 3D grid.
2. **Geometry source = `meshMatrix`** (the interpolated grid, same source as the filled heatmap). For
   a previewed saved profile, `BedMeshModel.previewOf` already populates `meshMatrix` from the
   profile's probed points — so the wireframe previews a saved profile automatically (coarser lattice,
   the same accepted tradeoff as the heatmap preview).
3. **Vertical height tied to the Color Scale row.** Height uses the SAME `deviationToRamp(z, loZ, hiZ)`
   fraction as the color, where `loZ/hiZ` come from the active `ScaleMode` (`endpoints()`):
   - `RELATIVE` → peaks always fill the vertical band (mesh never looks flat).
   - `PLATE` / `±0.10…±1.00` → height proportional to that fixed bound, clips past it — identical
     semantics to the color saturation.
   The neutral midplane (`frac == 0.5`, the `#787F8B` mid color) sits at zero height; deviations rise
   above / fall below it.
4. **Lines are height-colored** via the existing baked `rampColor` ramp — same low→neutral→high ramp
   as the other two views. Each segment is colored by the ramp fraction at its midpoint.
5. **Fixed isometric projection, no rotation/touch** (per the redesign spec). Auto-fit + center so any
   bed size / height fills the Focus box. Non-square beds (e.g. 300×150) render with correct aspect
   from `meshMin/meshMax`, not forced square.
6. **Glyph = `ssid_chart`** (owner-chosen 2026-06-22; verified resolvable as a real GSUB ligature in
   the bundled `material_symbols_outlined.ttf`). **Selector tiles stay TEXT-ONLY (all three)** —
   matching the current approved selector — so `ssid_chart` is NOT a tile icon. Instead it becomes the
   **"View Type" list-row icon** in the Mesh Config list, **replacing the current `blur_circular`**
   (a terrain/mesh glyph reads truer for a 3D mesh than blur). The new tile's **label = "3D Grid"**.
7. **No base plane and no probe dots in v1.** The lattice is the data; keep it clean and cheap. Both
   are easy fast-follows if wanted later.
8. **Height is frac-relative BY DESIGN** (so it always tracks the color). In `RELATIVE` mode the
   midplane (`frac == 0.5`) is the mesh's own mid `(minZ+maxZ)/2`, **not** physical `z == 0` — an
   all-positive mesh can sit partly below the midplane. This is intended: height and color share one
   mapping. `PLATE`/`PM_*` center the midplane on physical 0.

## Render design

All rendering lives in `BedMeshHeatmapView` (the existing classic-View Canvas surface, per ADR-0001).

### View mode

- Add `ViewMode.ISO_WIREFRAME` to the existing `enum ViewMode { HEATMAP, PROBE_POINTS }`.
- `onDraw` gains a third branch (before/after the existing two). Empty-state (`meshMatrix` empty) draws
  the same faint `--outline` rect as the other modes — no special case.

### Projection (pure, host-testable)

For a grid of `R` rows × `C` cols with Z value `z[j][i]` (`j` = row 0..R-1 = min→max Y; `i` = col
0..C-1 = min→max X):

1. **Physical (x,y)** from extents: `x = lerp(minX, maxX, i/(C-1))`, `y = lerp(minY, maxY, j/(R-1))`
   (guard `C==1`/`R==1`).
2. **Centered, aspect-preserving normalize:** `gx = (x - cx)/halfSpan`, `gy = (y - cy)/halfSpan`,
   where `cx,cy` = extent center and `halfSpan = max(maxX-minX, maxY-minY)/2` (guard zero span → 0.5).
   The longer bed axis spans roughly `[-1, 1]`; the shorter axis is proportionally shorter.
3. **Height:** `frac = deviationToRamp(z, loZ, hiZ)` ∈ [0,1]; `hNorm = (frac - 0.5)` ∈ [-0.5, 0.5].
4. **Isometric map to unit screen space** (fixed angle ≈ 30°, `COS=cos30≈0.866`, `SIN=sin30=0.5`).
   **Orientation is a hard requirement, not "tuned later":** the **front bed edge (min Y, j=0) MUST
   sit nearest the viewer at the BOTTOM** of the box (larger canvas Y, which grows downward), matching
   the overhead heatmap's front-at-bottom convention. Achieve this by **inverting the Y contribution**
   so min-Y maps to the bottom — e.g. use `gyView = (cy - y)/halfSpan` for the screen mapping:
   - `isoX = (gx - gyView) * COS`
   - `isoY = (gx + gyView) * SIN - hNorm * HEIGHT_AMP`
   A **host test asserts** the projected screen-Y of a front-edge (min-Y) vertex is **greater than**
   (lower on screen than) the matching rear-edge (max-Y) vertex — this pins the sign so it cannot
   regress, replacing "confirm on-device." `HEIGHT_AMP` is a unit-space amplitude constant (starting
   ≈ 0.6) controlling how tall a full-band deviation pops; a single named, on-device-tunable constant.
5. **Fit:** over all projected vertices, compute the `isoX/isoY` bounding box and apply one uniform
   scale + translate so the lattice fills the (square) Focus box with a small inset. **Guard the fit
   against a degenerate bbox:** if `bboxW ≤ ε` or `bboxH ≤ ε` (a 1×N / N×1 / single-vertex mesh), fall
   back to fitting the non-degenerate axis (or a default scale) — never divide by a zero span. This is
   in addition to the `C-1`/`R-1`/`halfSpan` guards in steps 1–2.

The index→`(isoX, isoY, frac)` mapping (steps 1–4) is a **pure function** extracted for host tests,
mirroring `deviationToRamp`/`BedMeshScaleModeTest`.

### Draw loop (allocation-free)

- Recompute a projection cache (`isoX[]`, `isoY[]`, `frac[]`, one entry per vertex) when the **mesh,
  scale mode, or view size** changes — never per frame in steady state (this view is static; it
  repaints only on `setMesh`/`applyTokens`/size change, like the heatmap). Arrays are preallocated and
  regrown only when `R*C` changes (the `ensureTraceCapacity` pattern already used for graph traces).
- **Cache invalidation must be dirty-guarded (Codex).** `BedMeshHeatmapHost` currently calls
  `applyTokens` / `setRampColors` / `setViewMode` / `setMesh` on **every** Compose update, and several
  unconditionally `invalidate()`. The iso projection cache must NOT be rebuilt on incidental
  recomposition — gate the rebuild on an actual change of the inputs it depends on (mesh identity +
  scale mode + size for geometry/frac; ramp for color, which is just the cheap `rampColor` lookup at
  draw time, so a ramp change needs only `invalidate()`, not a geometry rebuild). Add equality/dirty
  flags on the iso path mirroring the heatmap's existing P22 `applyTokens` equality guard.
- For each row `j`: draw segments `(j,i)→(j,i+1)` for `i` in 0..C-2.
  For each col `i`: draw segments `(j,i)→(j+1,i)` for `j` in 0..R-2.
  Each segment colored `rampColor((frac[a]+frac[b]) / 2)` on one reused `Paint` (stroke). Roughly
  `2*R*C` `drawLine` calls — a static ~30×30 grid (~1800 segments) is comfortably within budget.
- Draw far rows before near rows (painter's order) for a tidy overlap; purely cosmetic (lines, no fill
  occlusion).
- Stroke width: a small ratio-derived dp (consistent with the view's other strokes), not a magic px.

### Color / theme

- Reuses the per-printer High/Low + fixed `#787F8B` mid ramp already baked in `setRampColors`
  (`applyTokens` / `BedMeshHeatmapHost`). The wireframe uses the identical `rampColor` lookup — so a
  theme, palette-mode, or High/Low override change re-tints the wireframe with no extra wiring.

## Wiring

- **`BedMeshViewType`** (`calibration/BedMeshModel.kt`): add `ISO` → `{ HEATMAP, PROBE_POINTS, ISO }`.
  Update the stale "iso wireframe is a fast-follow, not here yet" comment.
- **`BedMeshHeatmapView.ViewMode`**: add `ISO_WIREFRAME`.
- **`BedMeshHeatmapHost`**: thread the new `viewMode` value through (it already forwards `viewMode`).
- **`BedMeshScreen`**:
  - `viewMode = when (vm.viewType) { … ISO -> ViewMode.ISO_WIREFRAME }` (the `when` becomes exhaustive
    over 3).
  - **`ViewTypeSelector`** grows from 2 tiles to 3: add the **"3D Grid"** tile (`BedMeshViewType.ISO`).
    Tiles stay **TEXT-ONLY** (all three) — `ViewTypeTile` is unchanged structurally; just a third
    instance. Confirm the 3-wide `Row(weight(1f))` layout still reads in portrait + landscape (labels
    like "Colored probe points" are long — verify wrap/ellipsis is acceptable, or shorten labels).
  - **"View Type" Mesh Config row icon:** swap `blur_circular` → `ssid_chart` (`DinghyIcons.MeshViewIso`).
    Check whether `blur_circular`/its `DinghyIcons` val is referenced anywhere else; if it becomes
    unused, leaving the registry entry is harmless (still resolvable) — removal is optional, not required.
- **Render prefs (Codex — real gap):** `BedMeshRenderPrefs.viewType` writes `v.name` but its **reader
  maps only `PROBE_POINTS.name`, defaulting everything else to `HEATMAP`** — so `ISO` would persist but
  read back as `HEATMAP` (silent data loss). Fix the reader to resolve all enum values robustly, e.g.
  `BedMeshViewType.entries.firstOrNull { it.name == prefs[viewKey(pid)] } ?: HEATMAP`. Add a prefs
  **round-trip test covering all three values** (HEATMAP/PROBE_POINTS/ISO).
- **`DinghyIcons`**: add `val MeshViewIso = DinghyIcon(IconRef.Ligature("ssid_chart"))` **AND add it to
  the `all` list** (the registry uniqueness test + icon catalog iterate `all`, not just the vals —
  Codex; matches the prior "new glyph needs the val AND the `all` list" lesson). `verify_ligatures.py`
  derives its check set from the registry, so the ligature is auto-gated; `ssid_chart` already confirmed
  resolvable.

## Preview / empty / mid-print

- **Saved-profile preview:** works for free — `previewOf` fills `meshMatrix`, the wireframe renders it.
- **Empty-state:** faint outline rect, same as the other modes.
- **Mid-print:** the view is read-only rendering; unaffected by the print-gating that hides
  edit/calibrate. View Type can still be switched (it is a local display setting, sends no gcode).

## Out of scope (possible later fast-follows)

- Base/floor plane or droplines under the lattice.
- Probe-point dots overlaid on the wireframe.
- Any rotation / touch / live camera control (explicitly static).
- Filled/shaded surface facets (wireframe-only by decision).

## Risks & notes

- **Dense interpolated grid (cap policy made explicit — Codex):** Klipper `bed_mesh` interpolated grids
  are bounded — typical probe counts are ~3×3…10×10 and the default `mesh_pps=2` interpolates to roughly
  the low tens per side, so `~2*R*C` segments stays small for a *static* draw. **v1 adds NO cap** (the
  realistic worst case is fine on the floor). The one guard: if a grid ever exceeds a high threshold
  (e.g. **> 60 per side**), **stride-decimate** the drawn lattice (draw every k-th line) and `log()` the
  decimation — **never silently truncate**. Confirm representative sizes from the live printers during
  planning.
- **Degenerate inputs:** 1×N / N×1 / zero-span beds must not divide-by-zero (guard `C-1`/`R-1`/`halfSpan`).
- **Orientation:** front-at-bottom must match the heatmap; verify on-device (sign of `gy`).
- **Selector at 3 tiles:** make sure the tile layout (and labels) still fit portrait + landscape.
- **`HEIGHT_AMP` taste:** the one number that decides "how dramatic"; on-device-tunable constant.

## Test surface

- **Unit (host):** the pure projection function (index + z + extents + loZ/hiZ → isoX/isoY/frac),
  asserting: (a) **front-at-bottom** — a min-Y vertex projects to a larger screen-Y than the matching
  max-Y vertex (pins the orientation sign); (b) non-square aspect (300×150 bed → elongated, not square);
  (c) the midplane invariant `frac==0.5 → hNorm==0`; (d) degenerate-grid + degenerate-bbox guards never
  divide by zero. Reuse the `deviationToRamp` test style.
- **Prefs round-trip (Codex):** `BedMeshRenderPrefs.viewType` set→read for **all three** values
  (HEATMAP/PROBE_POINTS/ISO), proving ISO no longer collapses to HEATMAP.
- **Selector:** `ViewTypeSelector` renders 3 options and emits `ISO` on tap of the 3rd.
- **Icon gate:** `verify_ligatures.py` exit 0 with `MeshViewIso` registered; `DinghyIcons` uniqueness
  test stays green with `MeshViewIso` in `all`.
- **On-device UAT (flox + moto):** switch to 3D Grid; cycle the Color Scale row and confirm both color
  and height respond; preview a saved profile in 3D; non-square bed aspect; portrait + landscape;
  empty-state.
