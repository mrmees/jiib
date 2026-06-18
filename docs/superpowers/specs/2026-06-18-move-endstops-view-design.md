# Move menu — Endstops view

**Date:** 2026-06-18
**Status:** Design approved, pending spec review
**Scope:** Add a read-only "Endstops" sub-mode to the Move Hub that shows live endstop trigger states.

## Goal

Let the user see the current trigger state of each configured endstop (X / Y / Z / probe)
from the Move menu, updating live so they can physically press a switch and watch it flip —
the classic endstop-wiring test. Read-only; no control.

## Context

- The Move screen (`app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt`) is a "Move Hub":
  a `sealed interface MoveMode` whose selection swaps the Focus content; the Field shows a list
  of mode rows + saved-location bookmarks. State comes from a `MoveVm` fed by the Move holder
  (`ui/move/MoveHolder.kt`, class `n`), which owns a coroutine scope + the printer state store.
- **Endstop state is NOT in the `objects/subscribe` stream.** Unlike temps/position, it must be
  fetched on demand via the Moonraker RPC `printer.query_endstops/status`, which makes Klipper
  poll the MCU and returns e.g. `{"x":"open","y":"TRIGGERED","z":"open"}`. The app already has a
  request/response path: `net/JsonRpcClient.kt` `request(method, params)`.

## Design

### 1. New sub-mode
- Add `MoveMode.Endstops` (object) to the `MoveMode` sealed interface.
- `moveModeHeader(...)` maps it to title `"Endstops"`, icon `center_focus_strong`.
- Add a Field list row `"Endstops"` (icon `center_focus_strong`) **last in the list — after the
  saved-location bookmark rows.**

### 2. Data path
- New RPC method constant for `printer.query_endstops/status`.
- A suspend query that calls `JsonRpcClient.request(...)` and parses the result object into
  `Map<String, Boolean>` where `true` = `"TRIGGERED"`, `false` = anything else (`"open"`).
- Surface it off the Move holder (`n`) as a `StateFlow<Map<String, Boolean>?>`
  (null = no result yet) plus `startEndstopPolling()` / `stopEndstopPolling()`.
- Expose the flow to the screen via `MoveVm`.

### 3. Live polling
- While `mode == MoveMode.Endstops` AND the screen is foreground, re-query every **~500ms**.
- Driven by a `DisposableEffect(mode)` (or equivalent) that calls `startEndstopPolling()` on
  enter and `stopEndstopPolling()` on leave, so the loop stops the instant the user leaves the
  sub-mode or backgrounds the screen.
- **No print-state gating** — the whole Move menu will be gated behind printing state in a later
  pass, so this feature adds none of its own.

### 4. Focus content
- A list of status rows, **one per key returned** (rendered dynamically — do not hardcode XYZ —
  so `probe` appears when present).
- Each row: title-cased axis name (`x`→"X", `probe`→"Probe") + state treatment:
  - **Open / inactive** → glyph `crop_free`, muted status token.
  - **Triggered / active** → glyph `center_focus_strong`, active status token.
- Convey state by **color AND shape** (the two distinct glyphs) so it survives the
  high-contrast / colorblind palette modes (THEMING.md status vocabulary).
- States before/around the first response:
  - No result yet → "Querying…" placeholder.
  - Query error / Klippy not ready → short "unavailable" hint.
- Compose from the canonical Focus/Field component classes (FocusFrame + ListBlock/ListRow or
  the established status-row pattern), not hand-rolled rows.

### 5. Foot bar
- Unchanged. Back returns to `MoveMode.TouchMove` (existing behavior). No Refresh button — the
  live poll makes it redundant.

### 6. Icon plumbing
- Add `CropFree` and `CenterFocusStrong` to `DinghyIcons` — both the `val` declaration AND the
  `all` registry list.
- Verify both ligatures exist in the Geist icon font via `verify_ligatures.py`; if missing, add
  the glyphs to the font before relying on them.

## Out of scope
- Any endstop control / re-homing from this view (read-only).
- Print-state gating (handled later, menu-wide).
- A manual Refresh button.

## Risks / notes
- `query_endstops/status` is on-demand and slightly heavier than reading subscribed state; the
  500ms cadence + stop-on-leave keeps it bounded. Acceptable while idle (the only state the Move
  menu will be reachable in once gating lands).
- New `MoveMode` member: ensure all `when(mode)` exhaustive branches (header, Focus content) are
  updated, or the build breaks (this is a feature, not a bug — compile-enforced coverage).
