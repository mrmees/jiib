# Screws Tilt Adjust — Focus & List Cleanup

**Date:** 2026-06-22
**Status:** Approved (brainstorm)
**Scope:** `ScrewsTiltScreen.kt` only — no data-model, holder, or parser changes.

## Goal

Clean up the Screws Tilt Adjust calibration screen so the Focus bed map is
visually honest and uncluttered, and the Field list reads cleanly.

## Background (as-built)

- **File:** `app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt`
- **Focus** (`ScrewsTiltFocus` → `BedScale` → `BoxWithPoints`): draws each screw as a
  state glyph at its real Klipper X/Y, with the screw **name** and the **Z probe
  reading** (`"%.3f mm"`) stacked beneath the glyph.
- **List** (`ScrewListRow`): primary = screw name verbatim from Klipper config
  (lowercase, e.g. `"front left screw"`); trailing = turn instruction (`"00:07 CCW"`).
- Screw X/Y come from `[screws_tilt_adjust]` config; the Y axis is flipped
  (printer-up → screen-down) so the map matches the physical bed seen from the front.

### Problems

1. The Focus shows the screw **name** under every glyph — redundant clutter; the
   list already names every screw.
2. **Placement is distorted.** `BedScale` stretches the screw bounding box to fill the
   pane (width 0.95, full height), so the layout's real X:Y aspect ratio is lost and
   every configuration splays out to the pane corners. A 300 mm × 260 mm screw span is
   drawn at the pane's aspect, not 300:260. This is the "is this actually positioned
   right?" smell — relative geometry is wrong.
3. List screw names are raw lowercase from Klipper config.

## Changes

### 1. Focus — remove the screw name, keep the distance

In `BoxWithPoints`, delete the `p.name?.let { … shortScrewName(name) … }` Text block.
Keep the glyph and the Z-reading Text (`"%.3f mm"`). Delete the now-unused
`shortScrewName` helper. Glyph + tint continue to encode
pending / base / in-tolerance / CW / CCW.

### 2. Focus — aspect-correct placement (the real fix)

Preserve the **real aspect ratio of the screw-position bounding box** instead of
stretching it to the pane. This is layout-shape agnostic — it works for a 3-screw
triangle, a 4-screw quad, or any count; each screw lands at its true relative position
and the overall shape is undistorted.

- Compute the bounding box of all screw positions (existing `minX/maxX/minY/maxY` +
  18% padding → `loX/hiX/loY/hiY`). Unchanged.
- **Replace** the current fixed sizing chain on the framed surface
  (`.fillMaxHeight().fillMaxWidth(0.95f)` at ~line 300–302) with an explicit
  **letterboxed fitted rectangle** computed via `BoxWithConstraints` (already imported):
  read the pane's `maxWidth`/`maxHeight`, and size the bordered/surfaced rectangle to the
  largest `w × h` that matches the screw box's aspect ratio and still fits — width-bound
  when the pane is taller than the ratio, height-bound when it's wider — then center it in
  the pane. This avoids `Modifier.aspectRatio`'s constraint-interaction gotcha (appending
  it to a fill chain still stretches / can overflow-and-clip); the explicit fit is
  predictable. Empty margin falls on whichever axis is shorter-fitting → letterboxing.
- **Clamp the aspect ratio** to a sane range (e.g. `coerceIn(0.25f, 4f)`) so a
  degenerate near-collinear screw layout can't collapse the frame to a sliver. (Valid
  Klipper configs are 3+ non-collinear screws, so this is cheap insurance, not the
  common path.)
- Keep the existing fractional placement (`fx`, `fy`) and the Y flip inside that region.
- Keep enough inner padding that edge glyphs aren't clipped. (Placement aligns each
  screw's glyph/label *column box* via `BiasAlignment`, not the geometric point center —
  good enough for this visual fix.)

Result: the screw layout is drawn to scale. A wide bed's screws look wide; a triangle
stays a correct triangle.

### 3. List — Title Case the screw name

In `ScrewListRow`, render `point.name` Title-Cased
(`"front left screw"` → `"Front Left Screw"`). When `name` is null, keep the existing
`point.key` fallback (`screw1`) unchanged.

## Out of scope

- Data model, `ScrewsTiltHolder`, `ScrewsTiltResult` parser.
- The turn-instruction text and per-screw glyph/color logic.
- The no-coords fallback prompt (`screws_run_prompt` / `screws_map_unavailable`).
- Drawing the *full bed rectangle* from axis limits (considered, deferred — bounding-box
  aspect is the agreed scope).
- No config knob for viewing side; front-facing is assumed (universal for a printer-side
  screen).

## Verification

- Unit/Compose tests for `ScrewsTiltScreen` still pass; title-case transform covered.
- On-device UAT on flox + moto: name gone from Focus, Z reading retained, screw layout
  not stretched (verify with a 4-screw E5 and ideally a 3-screw bed), list names
  Title-Cased.
