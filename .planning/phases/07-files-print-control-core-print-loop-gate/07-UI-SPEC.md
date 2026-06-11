---
phase: 7
slug: files-print-control-core-print-loop-gate
status: superseded
superseded_by: docs/ui_design/
superseded_on: 2026-06-11
shadcn_initialized: false
preset: none
created: 2026-06-02
reviewed_at: 2026-06-02
---

# Phase 7 - UI Design Contract

> Visual and interaction contract for the Files & Print Control core print-loop gate. Generated inline by the `gsd-ui-phase` workflow and verified against the six UI checker dimensions.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | Native Android hybrid Dinghy design system; no shadcn |
| Preset | not applicable |
| Component library | Existing Compose primitives (`ScreenScaffold`, `OutlinedControl`, `ConfirmGuard`, `SeverityToast`, `MaterialSymbol`) plus RecyclerView + Coil for the Files list |
| Icon library | Material Symbols through the local `MaterialSymbol` wrapper; file/folder placeholders are content thumbnails, not action glyphs |
| Font | Geist for UI text; Geist Mono for live data, file metadata, and tabular values |

Sources: `docs/ui_design/CLAUDE.md`, `docs/ui_design/LAYOUT.md`, `docs/ui_design/THEMING.md`, `docs/adr/0001-ui-toolkit-decision.md`, and Phase 7 context/research.

---

## Spacing Scale

Declared values use the project 4/8/16/24/32/48/64 grid. Existing reference-artboard values such as 9, 11, 13, and 15 are prototype-only for this phase and must normalize to the production grid.

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4dp | Icon gaps, row metadata separators, focus overlay micro-gaps |
| sm | 8dp | Row gaps, path-chip icon gap, compact inline spacing |
| md | 16dp | Default row padding, preview overlay padding, control corner radius |
| lg | 24dp | Screen/region padding and ConfirmGuard field padding |
| xl | 32dp | Large Focus-to-content breathing room where space allows |
| 2xl | 48dp | Stable thumbnail slot size for file rows |
| 3xl | 64dp | Minimum touch target and gutter button height |

Exceptions: none for new Phase 7 layout structure. Region sizes use weights, percentages, `aspectRatio`, and container-relative sizing only.

---

## Typography

All sizes are base `sp` values multiplied by `ThemeTokens.fs` (`S=1.0`, `M=1.15`, `L=1.32`). Use exactly the two declared weights.

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Body | 16sp * fs | 400 | 1.35 |
| Label / metadata | 13sp * fs | 400 | 1.25 |
| Control / row emphasis | 18sp * fs | 600 | 1.2 |
| Display / guard title | 28sp * fs | 600 | 1.15 |

Geist Mono is mandatory for size/date/time/filament/layer/Z values. Text must fit its container at all three text-size settings; truncate long filenames in rows, but allow the selected-file Focus and ConfirmGuard body to wrap.

---

## Color

The contract is token-first; hex values are the checked-in dark-theme sRGB fallbacks from `BakedTokens.kt`.

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) | `--bg` `#0C1015`; `--surface` `#171C23` | Full-screen background, screen body, empty Focus placeholder |
| Secondary (30%) | `--bg-2` `#12161C`; `--surface-2` `#20262E`; `--outline` `#4B535E` | File rows, thumbnail wells, neutral controls, path chip, inactive tracks |
| Accent (10%) | `--accent` `#4C94EC`; `--accent-2` `#70ADFB`; `--accent-line` `#8C4C94EC` | Selected file outline/checkmark, active path chip edge, enabled pause/resume/files actions, pending print-start affordance |
| Positive | `--go` `#5AC576` | `Print file` confirm, non-destructive commit states, successful delete toast icon/accent |
| Caution | `--heat` `#F3A958` | Deferred/disabled Tune affordance explanation only if surfaced; no new caution action in Files list |
| Destructive | `--stop` `#F4514F` | Emergency Stop, graceful `Cancel print`, `Delete file`, restart-print ConfirmGuard if treated as high-impact/destructive |

Accent reserved for: selected file row, selected thumbnail/focus outline, active path chip, enabled Pause/Resume/Files gutter controls, and bounded pending-action indicators. Accent is not a blanket color for every interactive element.

---

## Visual And Interaction Contract

### Files Screen

- Use `ScreenScaffold`; do not introduce persistent chrome, a toolbar, cards-inside-cards, or a separate navigation stack.
- Field is the file browser. It owns the path chip, in-list Up row, loading/empty/error rows, and the RecyclerView list.
- Focus is the selected-file preview. It shows a square thumbnail/placeholder first, then a centered overlay with filename, estimated time, size, modified date, filament/weight, layer count, and object height when available.
- Landscape always shows Focus + Field in the 50/50 shared grid. Focus shows a placeholder until a file is selected.
- Portrait shows Field-only while browsing. Once a file is selected, Focus appears above the Field with the standard stacked rhythm.
- Gutter is `Cancel picker` / `Print file`. `Print file` is disabled until a gcode file is selected and `virtual_sdcard` is available.
- Delete is a selected-file Focus action, idle-only. It is never exposed per row, through row long-press, or while printing/paused.
- File rows are stable height, tap-to-select only, and never expand. Selected state is an accent outline/checkmark; details remain in Focus.
- Folder rows use folder placeholder art and open the folder on tap. Gcode rows use thumbnail when available and a distinct gcode placeholder when not.
- Android system Back returns to the caller screen. Folder-up is the in-list Up row, not system Back.

### ConfirmGuard Screens

- Start print uses full-screen ConfirmGuard with thumbnail/details when available. Confirm label: `Print file`; safe dismiss label: `Keep browsing`.
- Delete uses destructive ConfirmGuard with filename/path plus size/date when known. Confirm label: `Delete file`; safe dismiss label: `Keep file`.
- Graceful cancel uses destructive ConfirmGuard. Confirm label: `Cancel print`; safe dismiss label: `Keep printing`.
- Restart uses ConfirmGuard for the current/last filename. Confirm label: `Restart print`; safe dismiss label: `Not now`.
- ConfirmGuard has no gutter; its Field buttons are the whole decision surface.

### Print Status Gutter

- Preserve the existing Print Status Focus and Field structure.
- Printing state: `Tune` remains disabled/deferred, tap `Pause` dispatches pause, long-press `Pause` opens graceful cancel, `Stop` remains firmware emergency stop.
- Paused state: `Tune` remains disabled/deferred, tap `Resume` dispatches resume, long-press `Resume` opens graceful cancel, `Stop` remains firmware emergency stop.
- Complete/error/cancelled with a known filename: show `Files` / `Restart print` / `Stop`. `Files` opens the picker; `Restart print` routes through ConfirmGuard; `Stop` remains available only if Klippy is ready.
- Pause/resume/cancel/restart/start surfaces must show pending state until `print_stats` / `pause_resume` confirms the transition. Do not display command-ack success as final success.

### States

- Loading: keep the path chip and gutter stable; show skeleton/placeholder rows in Field.
- Empty folder: keep the path chip and Up row if nested; show the empty copy below.
- Thumbnail or metadata failure: preserve layout with placeholders and allow printing.
- Capability unavailable: show actionable error copy and disable the affected action only.
- Delete success: stay in the current folder, remove the row, clear selection/preview, and show a success toast.
- Error after command ack without state flip: show warning toast and leave the user on the current screen with controls enabled according to live state.

### Performance And Accessibility

- Files list is RecyclerView + Coil, hosted through the Compose shell. Rows must recycle thumbnail slots and downsample off the UI thread.
- No animated list insertion, no continuous shimmer, no looping loading animation, and no alpha keyboard/search.
- Every action control has an accessible label. Gutter controls use icon+label where space allows; icon-only panic/stop paths keep a content description.
- Action glyphs must not repeat on the same control surface. Repeated folder/file placeholders inside the list are content markers, not action glyphs.
- Touch targets are at least 64dp tall.

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| Primary CTA | `Print file` |
| Picker dismiss | `Cancel picker` |
| Empty state heading | `No gcode files` |
| Empty state body | `This folder has no printable gcode files. Add files in Moonraker, then reopen Files.` |
| Directory error state | `File list unavailable. Check the printer connection and try again.` |
| Thumbnail/metadata fallback | `Preview unavailable. Filename and path are enough to print.` |
| Start confirmation | `Print {filename}?`: show estimated time, size/date, filament/layers when known; confirm `Print file` |
| Delete confirmation | `Delete {filename}?`: `Removes this file from Moonraker. This cannot be undone.`; confirm `Delete file` |
| Cancel print confirmation | `Cancel this print?`: `{percent} done. This cannot be undone.`; confirm `Cancel print` |
| Restart confirmation | `Restart {filename}?`: `The printer will start this job again from the beginning.`; confirm `Restart print` |
| Delete blocked | `Finish or cancel the active print before deleting files.` |
| Start pending | `Starting print... waiting for printer state.` |
| Pause pending | `Pausing... waiting for printer state.` |
| Resume pending | `Resuming... waiting for printer state.` |
| Cancel pending | `Cancelling print... waiting for printer state.` |

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not applicable |
| third-party registries | none | no third-party component code |

---

## Checker Sign-Off

- [x] Dimension 1 Copywriting: PASS
- [x] Dimension 2 Visuals: PASS
- [x] Dimension 3 Color: PASS
- [x] Dimension 4 Typography: PASS
- [x] Dimension 5 Spacing: PASS
- [x] Dimension 6 Registry Safety: PASS

**Approval:** approved 2026-06-02

---

## Post-Build Revisions (on-device polish, 2026-06-02)

The Codex-built first pass missed several basics; fixed live on flox with Matthew. These refine the
contract above — design-system laws were also synced into `docs/ui_design/CLAUDE.md` + `LAYOUT.md`.

- **Files Focus is the "future-print" image-backed card** (supersedes "square thumbnail then overlay").
  Dimmed gcode thumbnail BACKGROUND + left-aligned, vertically-centered icon-led stat lines OVERLAID,
  mirroring the Print Status last-job card. Shows ONLY future-print fields — filename (Geist Mono),
  est time, filament (mm · g), layers, height, size, modified — never elapsed/finished/status. The
  Delete action (idle-only) stays in the Focus, as a full-width red button beneath the card.
- **Content image uses `Fit`, not `Crop`** (Files Focus AND Status last-job card): show the whole
  preview, letterboxed — `Crop` zoomed into a center strip in the tall landscape panes. Global law.
- **Thumbnail size by surface:** list rows pull the SMALLEST `thumbnails[]` variant (≈32px — cheap to
  decode per cell on the Adreno-320 floor); the Focus card pulls the LARGEST. (Codex pulled the
  300×300 for every row.)
- **Hidden (dotfile) directories are filtered** from the browser (`.thumbs`, `.git`, …) — Moonraker
  machinery, and hiding them cuts misclicks/confusion. Folders only; printable gcode never starts with `.`.
- **Swipe-up App Drawer suppressed on Files** (its Field is a finger-scrollable RecyclerView); exit via
  the "Cancel picker" gutter button. Global law for any scroll-Field screen.
- **Panel text fills the full cell width**, marquee-scrolling only on genuine overflow (removed the
  prior locked-to-stat-block width on the Status card). Global law.
