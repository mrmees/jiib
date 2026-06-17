# Active-print Focus — centered data block over the thumbnail

**Date:** 2026-06-16
**Status:** design approved (pending written-spec review)
**Owner:** Matthew

## Problem

The active-print (Printing/Paused) home Focus — built in
`2026-06-16-printing-home-active-print-design.md` — currently shows the file thumbnail behind two
anchored text lines: the **filename** (top) and a **layer/height** line (bottom), with top+bottom
gradient scrims for legibility. The owner wants the Focus to carry more at-a-glance print data,
laid out as a single centered list reading cleanly over the image.

This redesigns the Focus **body** only: the thumbnail background treatment and the text content.

## Scope

**In:** the `ActivePrintFocus` composable body in `PrintStatusFocus.kt` — background scrim, the
centered multi-line data block, the text shadow, and the duration formatter. Printing **and**
Paused (same body; only the existing header label / progress-stroke color / foot button differ,
all unchanged here).

**Untouched:** header (`PRINTING · NN%` + morphing e-stop), the clockwise `FocusEdge.Progress`
stroke (accent / amber-on-pause), the foot bar (Pause/Resume + Cancel), routing, the idle/standby
digest, networking. No new Moonraker calls — every field is already in `PrinterState` /
`PrintMetadata`.

## Design

### Background & legibility

- **Scrim.** Replace today's top+bottom vertical-gradient scrims with a **single uniform overlay**
  at ~50% of the surface color (`t.surface.copy(alpha = 0.5f)`) across the whole frame body. Heavy
  enough that text is legible over any render; the thumbnail still reads through it.
- **Text shadow.** Every line in the block carries a `Shadow` in the **surface color**
  (`t.surface`, small offset + blur) applied via `style.copy(shadow = …)`. This is allowed under
  the font-conformance law — that test bans only inline `fontSize=` / `fontFamily=`, not `shadow`.
  The shadow makes text pop against busy thumbnails even where the scrim alone is marginal.

### The data block — 6 lines, centered both axes

A `Column` centered **horizontally and vertically** in the Focus body, reading as one list over the
image. Order and sources:

| # | Line | Source | Example |
|---|------|--------|---------|
| 1 | **Filename** (slightly larger) | `printFileBasename(state.printFilename)` | `calibration_benchy.gcode` |
| 2 | Heaters — current temp only, **all heaters** | `state.heaters` + `prettyHeaterLabel` | `Extruder 230 · Bed 75` |
| 3 | Job time | `state.totalDuration` (wall clock since job start) | `Job: 1h12m` |
| 4 | Print time / slicer estimate | `state.printDuration` / `printMetadata.estimatedTime` | `1h05m / 3h20m` |
| 5 | Z height | `gcodePosition[2]` / `printMetadata.objectHeight` | `1.2 / 55 mm` |
| 6 | Layers | `state.currentLayer` / `state.totalLayer` (metadata fallback as today) | `5 / 220 layers` |

**Heaters (line 2):** ALL configured heaters, not just the active ones — owner ruling 2026-06-16:
users must see when a cold hot end still needs to come up to temp. Use `orderedHeaterKeys` (every
heater in canonical order: `extruder`, `heater_bed`, then the rest), **current temperature only**
(no target), rounded to an integer, joined with the app `·` separator:
`"Extruder 230 · Bed 75"`. (When cold: `"Extruder 24 · Bed 23"`.) `prettyHeaterLabel` already
maps `extruder→Extruder`, `heater_bed→Bed`, `heater_generic chamber→Chamber`.

**Job vs print time:** line 3 is `total_duration` (wall clock incl. heating/pauses), prefixed
`Job:`. Line 4 is `print_duration` (extruding time) over the slicer's `estimated_time`, as
`elapsed / estimate`. Both formatted by `formatPrintDuration` (below).

### Typography & fit

- **Filename** in **Geist UI** (`DinghyType.screenTitle` role), **slightly** larger than the data
  lines — owner: "filename can be bigger, but just slightly." (Roughly one ramp step up; the data
  lines set the base.)
- **Lines 2–6** in **Geist Mono** tabular (`DinghyType.statValue` role), uniform size.
- **Shrink-to-fit — width AND height aware.** Today's bottom line shrinks on width only. The block
  now has 6 lines and must also fit *vertically*, so compute one uniform scale (applied to the data
  lines; the filename keeps its slightly-larger proportional step) that satisfies BOTH: the widest
  line fits the body width, and all 6 lines + spacing fit the body height. Floor at `fsSp(15f)`;
  if a pathological line still overflows at the floor it ellipsizes (single line, no wrap). Reuse
  HomeDigest's `rememberTextMeasurer` approach, extended with a height constraint.

### Duration formatter (new pure helper)

`formatPrintDuration(seconds: Double): String` in `ActivePrintFormat.kt` (host-testable, beside the
other active-print formatters):

- `≥ 1h` → `"{h}h{mm}m"`, minutes zero-padded — e.g. `3661s → "1h01m"`.
- `1m‥59m` → `"{m}m"` (no seconds) — e.g. `2700s → "45m"`.
- `< 1m` → `"{s}s"` — e.g. `30s → "30s"`, `0s → "0s"`.

(The existing `FilesScreen.formatDuration` — `"1h 5m"` / `"5m"`, no sub-minute seconds — stays as
is; it is `private`, differently formatted, and used only by Files. The minor duplication is
deliberate to avoid changing the Files display.)

### Degrade rules (never render a dash-pair or crash)

- **No slicer estimate** (`estimatedTime` null) → line 4 shows elapsed alone: `"1h05m"`.
- **No `total_duration`** (0.0) → still render `"Job: 0s"` (mid-print it is always > 0; 0 only
  pre-start).
- **Z / layers** missing → drop that clause exactly as the current `formatLayerHeight` does
  (never `—/—`). Z null → `"—"` for line 5; layers null → omit line 6.
- **Heaters** — `state.heaters` is empty (no heater objects yet, pre-subscribe) → omit line 2.
  Otherwise always shown (active or not).

## Affected files

- `ui/printstatus/PrintStatusFocus.kt` — rewrite the `ActivePrintFocus` body: uniform 50% scrim,
  centered 6-line `Column`, per-line surface-color shadow, width+height shrink-to-fit. Remove the
  top/bottom anchored filename + bottom layer/height layout.
- `ui/printstatus/ActivePrintFormat.kt` — add `formatPrintDuration`; add a `formatHeatersLine`
  (all heaters, current-only, `·`-joined) pure helper. `formatLayerHeight` reused for line 5/6
  (or split into `formatZHeight` + `formatLayers` if cleaner — decide in planning).
- `app/src/test/.../ActivePrintFormatTest.kt` — tests for `formatPrintDuration` (the 3 brackets +
  zero-pad + boundaries) and `formatHeatersLine` (order, all-heaters, current-only, empty→"").
- `app/src/main/res/values/strings.xml` — `Job:` prefix string (`printstatus_job_time` / format),
  any "mm" / "layers" / "/" wrapper strings if not already present.

## Out of scope

- Heater target temps, filament used, flow/speed factors, babystep row (one tap away / deferred).
- Changing the header, progress stroke, foot bar, or the idle/standby digest.
- Per-line icons (would need an owner icon ruling — never auto-pick a glyph).

## Owner-set defaults (trivially tweakable at on-device UAT)

- Separator is the app `·` middot, not the `-` in the owner's example (app-wide consistency).
- Durations drop seconds except under a minute (owner ruling).
- "Filename slightly bigger" = ~one ramp step; exact step tuned on-device.

## Success criteria

1. A running print renders the 6-line block centered in the Focus over a 50%-scrimmed thumbnail,
   each line shadowed in the surface color, filename slightly larger (Geist UI), data lines uniform
   (Geist Mono).
2. Heaters line shows ALL heaters, current temp only, even when cold (`Extruder 24 · Bed 23`).
3. Job time = `total_duration` (`Job: 1h12m`); print line = `print_duration / estimated_time`
   (`1h05m / 3h20m`); durations use `1h05m` / `45m` / `30s`.
4. The whole block fits the Focus on the narrowest device (moto) in both orientations without
   overflow or clipping — shrinks uniformly to the 15sp floor, ellipsis only as a pathological
   safety net.
5. Degrades cleanly: no estimate → elapsed only; missing Z/layers → clause dropped (no `—/—`);
   no heater objects → line omitted; never a crash.
6. Pausing is unchanged from the prior build (header `PAUSED · NN%`, amber stroke, Resume button).
7. Build + unit suite green (incl. `FontConformanceTest`); the printing Focus previews render.
