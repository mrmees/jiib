# Printing-phase home — active-print Focus + foot bar

**Date:** 2026-06-16
**Status:** design approved, pre-plan
**Owner:** Matthew

## Problem

The 2026-06-15 state-collapse (`2026-06-15-printstatus-state-collapse-design.md`) reduced the
home to one skeleton: every printer state renders the universal `HomeFocus` + `HomeField`. While
a print runs, the Focus shows only the **legacy temp glance** (Nozzle · Bed · sensor · spool) and
the foot bar shows **Preheat + System** — explicitly logged there as *"an accepted interim state;
richer per-state content is the next effort."*

This is that next effort: build up the **active-print** treatment from the collapsed baseline.

## Scope

"Active print" = `state.printState ∈ {Printing, Paused}` — the existing `isPrinting` branch in
`HomeFocus` (and `showGlance = isPrinting && !klippyFault`). Printing and Paused are two near-
identical variants of one umbrella treatment (three small conditionals; see Variants below).

**In:** the Focus body + header + foot bar + Field list, for Printing/Paused only.

**Untouched:**
- Idle / Standby — keeps the digest + Preheat/System foot bar.
- Terminal (Complete/Cancelled/Error) and Klipper fault — keep the digest.
- Routing, capability gating, the e-stop mechanism, networking. No new Moonraker calls.

## Design

### Focus (the card) — replaces the glance block while printing

- **Background — thumbnail.** Coil 3 `AsyncImage` of the active file's largest thumbnail, filling
  the `FocusFrame` body, `ContentScale.Fit`, centered (the model is not cropped; the frame
  background shows in the letterbox bands). Source: `container.printMetadata.largestThumbRelPath`
  + `container.httpBase` → `thumbnailUrl(httpBase, printFilename, relPath)`. **No thumbnail
  (null metadata / no relPath / not yet fetched) → fall back to today's faint brand watermark.**
  Top + bottom scrim gradients behind the text so it stays legible over any render.
- **Edge — progress stroke.** Implement the deferred `FocusEdge.Progress(state.progress)`: an
  accent perimeter bar on the rounded-rect, starting at **top-center (12 o'clock)**, growing
  **clockwise**, arc length = `progress` (0..1). 0% → invisible; 50% → reaches 6 o'clock (entire
  right side lit); 75% → reaches 9 o'clock; 100% → closes the loop. Accent is already reserved for
  exactly this edge (`FocusFrame.kt`, `FocusEdge.Progress`). Color is **amber when Paused** (see
  Variants).
- **Top line — filename.** `state.printFilename` basename (leading path stripped), top-aligned,
  single line, `basicMarquee()` on overflow. Geist title role (UI text).
- **Bottom line — layer / height.** Bottom-aligned, **Geist Mono tabular** (live data). Format
  `"1.2/55mm · 5/220 layers"`:
  - height = current Z height `/` `printMetadata.objectHeight` (mm).
  - layers = `state.currentLayer` `/` `state.totalLayer`.
  - **Graceful degrade:** `objectHeight` null → `"1.2mm"` (current only); `currentLayer`/
    `totalLayer` null → drop the layers clause entirely (never render `—/— layers`). Worst case
    shows just the current height.
- **Header.** Title text → `"PRINTING · 42%"` (integer `round(progress*100)`); multi-printer
  prefix still applies → `"Ender 5 · PRINTING · 42%"`, marquee handles overflow. The header icon
  already morphs to the red e-stop while `isPrinting && onEmergencyStop != null` (`FocusFrame`) —
  no change; `HomeFocus` already passes both. Tapping it runs the existing e-stop confirm path.

### Field (list + foot bar)

- **List.** Unchanged capability-gated idle actions, **+ a System row appended while printing**
  (System leaves the foot bar, so it moves into the list). `buildIdleActions` gains an
  `isPrinting` flag that appends the `NavDest.System` Destination.
- **Foot bar.** Branches on state (the idle bar is unchanged):
  - Printing → **Pause** (amber / `Intent.Warn` — interrupts the running process) + **Cancel**
    (red / `Intent.Danger`).
  - Paused → **Resume** (green / `Intent.Go` — the expected action) + **Cancel** (red).
  - Idle / terminal → today's **Preheat + System** (unchanged).
- **Cancel** opens a `ConfirmGuard` ("Cancel print?") before dispatching (destructive, hard to
  undo). **Pause / Resume** fire immediately. Dispatch via the existing `CommandRegistry.printPause`
  / `printResume` / print-cancel specs (intact post-collapse; only the old UI control machinery was
  deleted). Each is availability-gated on `pause_resume` already.

### Variants (the only Printing vs Paused deltas)

| | Printing | Paused |
|---|---|---|
| Header label | `PRINTING · NN%` | `PAUSED · NN%` |
| Progress stroke color | accent | **amber** |
| Foot button (left) | Pause (amber) | **Resume** (green) |

Everything else — thumbnail bg, filename, layer/height line, stroke geometry, Cancel, e-stop — is
shared. The stroke amber-on-pause is the in-frame "paused" signal that complements the header label
and the Resume button.

## Plumbing

- `PrintStatusScreen(container=…)` collects `container.printMetadata: Flow<PrintMetadata?>` and
  `container.httpBase: Flow<String>` (both already exposed on `AppContainer`) and threads the
  thumbnail URL inputs + layer/height + the pause/resume/cancel dispatch lambdas into
  `HomeFocus` / `HomeField`. No `AppShell` edit, no new container surface.
- The stateless `PrintStatusScreen(state=…)` preview overload gains fixture metadata + a fixture
  `httpBase` so the printing Focus renders under `@Preview`.
- `FocusEdge.Progress` rendering is implemented in `FocusFrame` (currently borderless / deferred):
  the clockwise-from-top-center perimeter arc, accent/amber, width per the edge-stroke table.

## Affected files (indicative)

- `ui/printstatus/PrintStatusFocus.kt` — active-print Focus body (thumbnail bg + scrim + filename +
  layer/height); `FocusEdge.Progress` passed for printing; the glance block stays for any other
  branch that still uses it (none, post-this — verify during planning).
- `ui/printstatus/PrintStatusField.kt` — state-branched foot bar (Pause/Resume + Cancel vs
  Preheat + System); Cancel `ConfirmGuard`; System appended to the list while printing.
- `ui/printstatus/PrintStatusScreen.kt` — collect `printMetadata` + `httpBase`; thread title %,
  metadata, and dispatch lambdas; fixture metadata in the preview overload.
- `ui/route/HomeAction.kt` (`buildIdleActions`) — `isPrinting` flag → append System row.
- `designsystem/components/FocusFrame.kt` — implement `FocusEdge.Progress` perimeter arc.
- Strings: `PRINTING · %` / `PAUSED · %` title formats; Pause / Resume / Cancel labels; "Cancel
  print?" confirm copy.

## Out of scope

- Babystep / Z-offset row, flow / speed factors, terminal-result hero.
- Per-state title icons (needs an owner icon ruling — never auto-pick a glyph).
- Temps during a print (owner decision 2026-06-16: dropped — image-forward Focus; temps are one
  tap away in the Temperature screen / field list).
- Curated mid-print field list (owner decision 2026-06-16: keep the full idle list + System).

## Success criteria

1. A running print renders: thumbnail background (or watermark fallback), filename top, layer/
   height bottom, `PRINTING · NN%` header, red e-stop icon, accent clockwise progress stroke.
2. Pausing flips the header to `PAUSED · NN%`, the stroke to amber, and the Pause foot button to
   Resume (green); Cancel and the rest are unchanged.
3. Foot bar shows Pause/Resume + Cancel while printing/paused (Preheat + System when idle); Cancel
   confirms via `ConfirmGuard` before aborting; System appears in the field list while printing.
4. Progress stroke geometry matches: invisible at 0%, full right side at 50%, to 9 o'clock at 75%,
   closed loop at 100%, clockwise from top-center.
5. Layer/height degrades gracefully (no `—/—`); no thumbnail → watermark, never a crash.
6. Build + test suite green; the printing Focus is previewable.
