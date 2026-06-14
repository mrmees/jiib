# Region framing — the field-side conformance pass (Phase 2 of 2) — design

**Date:** 2026-06-14
**Status:** approved design, pending implementation plan
**Supersedes mechanism of:** `2026-06-14-focusframe-conformance-design.md` (Phase 1). Phase 1 pushed the
8dp frame *down* into `FocusFrame` (component self-owns). Phase 2 hoists the frame *up* to the
**region** so the WHOLE screen shares one registration grid owned in ONE place. Phase 1 was the
conformance recon that standardised the call sites and surfaced the outliers; this is the real
centralisation. `FocusFramePlacement` (introduced in Phase 1) is **retired** here.

## Goal

Centralise the 8dp edge-registration frame (`docs/ui_design/LAYOUT.md` R26 / C-E2) so that **each
region (focus, field) is framed exactly once**, the frame value + inter-element gap live in **one
constant/composable**, and every region-filling component (`FocusFrame`, `ListBlock`, `FootButtonBar`,
`SortRow`, `FilterRow`) becomes **flush-fill** with no self-owned frame padding. A single edit to the
region inset must move the whole app. This is "single-location changes to app-wide screen layout."

Today the frame is hand-composed per screen across `ListBlock` (horizontal self), a caller
`.padding(top = 8.dp)`, `FootButtonBar` (both axes self), and the `SortRow`/`FilterRow` controls
(neither axis — every caller hand-pads them). That is the "bunch of individual padding" smell.

## Model (owner-confirmed)

> A **region** = an optional primary element (`FocusFrame` card **or** `ListBlock` list) + an optional
> stack of **control rows** (`SortRow`/`FilterRow`/`FootButtonBar`) that sit **outside** the primary.
> One 8dp outer frame, 8dp gaps between elements.

**Control rows are a distinct thing: outside both the list and the focus card, and dockable under
EITHER region.** This requirement is satisfied *by construction* (not a new abstraction): the region
primitive is identical for focus and field, and the control rows are flush children — so a control row
renders pixel-identically whether it sits under the focus card or under the field list.

Pure registration refactor: **no element is physically relocated** in this pass; the look is unchanged
except two intended fixes (Spool card width; Console top edge — see §"Intended visual changes").

## The primitive — `RegisteredRegion`

A single `Column` wrapper that owns the frame and the gap, and nothing else:

```kotlin
@Composable
fun RegisteredRegion(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(RegionInset),               // 8dp, ALL four sides — THE frame
        verticalArrangement = Arrangement.spacedBy(RegionGap),  // 8dp between stacked elements
        content = content,
    )
}
```

- `RegionInset` and `RegionGap` are both **8dp** — the repurposed `ListFrameInset` value. This is the
  ONE place the app's registration lives. (`ListFrameInset` is repurposed/aliased as the region
  constant; keep the symbol to minimise churn, re-document it as the region frame source.)
- It provides `ColumnScope`, so a flexible child uses `Modifier.weight(1f)` and fixed children
  (control rows) take their intrinsic height — exactly as today.

### Why this reproduces today's geometry exactly

| Edge / gap today | Composed from | Under `RegisteredRegion` |
|---|---|---|
| Region top / bottom 8dp | caller padding / `FootButtonBar` self | region `padding(8)` |
| Region horizontal 8dp | each component's self horizontal | region `padding(8)` |
| Gap between stacked elements | `4 + 4` composed paddings | `spacedBy(8)` |

`4 + 4 = 8` and self-8 → region-8 are byte-equivalent, so framed screens render identically.

## Scaffold integration — opt-out / default-on

`ScreenScaffold` wraps each non-null slot's content in `RegisteredRegion` **by default**, with a
per-region boolean to turn it off:

```kotlin
fun ScreenScaffold(
    …,
    focusFramed: Boolean = true,
    fieldFramed: Boolean = true,
)
```

Internally, for each slot: `if (framed) RegisteredRegion(regionMod, content) else Column(regionMod,
content)`, where `regionMod` is the existing `weight(...).fillMaxHeight()` (landscape) /
`weight(...).fillMaxWidth()` (portrait) — the framing changes ONLY whether the region Column carries
the padding + `spacedBy`.

**Rationale for default-on (owner):** new screens are correct for free; the frozen screens are
temporary exceptions that should converge to the standard, so the default encodes the *target*, not
the exception.

Console is **not** a `ScreenScaffold` — it calls `RegisteredRegion` directly as its single full-screen
region.

## Components go flush (the centralisation)

Each shared component DELETES its own frame padding; the region now owns it:

- **`FocusFrame`** — delete the `.padding(focusFramePadding(placement))` modifier line and **remove
  `FocusFramePlacement` entirely**: the enum, the `focusFramePadding()` helper, the `placement`
  parameter, and the Phase-1 `FocusFramePaddingTest`. Inner `contentInset` (16dp, incl. the
  `FocusInset/2` adjuster half-inset screens) is **unchanged** — it is content breathing room, not
  outer registration.
- **`ListBlock`** — drop `.padding(horizontal = ListFrameInset)` (becomes flush; the edge fades and
  `LazyColumn` are unchanged).
- **`FootButtonBar`** — drop `.padding(horizontal = ListFrameInset, vertical = 8.dp)`. Keeps
  `fillMaxWidth()` and `heightIn(min = uDp)`. Its bottom edge is now the region's bottom padding; the
  gap above it is the region `spacedBy`.
- **`SortRow`/`FilterRow`** — already flush internally; no component change (only their call sites lose
  hand-padding).

## Blast-radius catch — shared components affect FROZEN screens too

Making `FootButtonBar`/`ListBlock` flush hits **every** consumer, including opted-out screens. Audit
result: the only frozen screen using a now-flush shared component is **Webcam** (its cam-picker
`FootButtonBar`). Therefore Webcam is frozen **per-region**:

- `focusFramed = false` — the full-bleed video keeps its existing hand-applied `padding(8)`.
- `fieldFramed = true` — the cam-picker list + `FootButtonBar` adopt the region frame, so the foot bar
  stays correctly padded (region 8 + flush bar = the current 8).

About / Settings / Splash / ConfirmGuard do **not** use `ListBlock`/`FootButtonBar` (they are
`verticalScroll` reading columns or raw `OutlinedControl` rows), so both their regions opt out safely
with no component-level fallout. Gallery / OldMoveScreen are debug-only.

## Scope

### Active — adopt region framing
PrintStatus ×4 (Standby/Printing/Paused/Terminal), Probe, BedMesh, Tilt, ScrewsTilt, CalibrationHub,
Extrude, Move, FineTune, Macros (BookmarkedMacros), Temperature, Outputs, Printers, **SystemPage**,
**SystemInformation**, and the untangle trio **Spool, Files, Console**.

### Frozen — opt out, leave byte-for-byte
About, Settings (whole settings/system menu reworked later), Splash, ConfirmGuard (centred specials),
Gallery, OldMoveScreen (debug), **Webcam** (`focusFramed = false`, `fieldFramed = true` per above).

## Per-screen edits

- **~13 standard screens** (PrintStatus ×4, Probe, BedMesh, Tilt, ScrewsTilt, CalibrationHub, Extrude,
  Move, FineTune, Macros, Temperature, Outputs, Printers): Phase 1 already stripped their `FocusFrame`
  padding, so the only edit is the field side — `ListBlock(Modifier.weight(1f).padding(top = 8.dp))` →
  `ListBlock(Modifier.weight(1f))`. `FootButtonBar` call sites are unchanged (padding removed inside
  the component). Outputs' framed empty-state and Printers' framed empty-profile branches (from Phase
  1) keep their `FocusFrame`s — those go flush automatically.
- **SystemPage / SystemInformation**: same field-side `ListBlock` strip.
- **Spool**: delete the padded `Box` wrapper and the per-row `.padding(...)` on `SortRow`/`FilterRow`;
  the focus content becomes flush `FocusFrame(weight1)` + `SortRow()` + `FilterRow()`. (The stale
  "FloatingEStop as Box sibling" comment goes — the e-stop lives in the `FocusFrame` header now.) This
  is where the **16dp double-pad** is fixed.
- **Files**: delete the `FocusFrame` `top8/bottom4` and the `SortRow` `top4/bottom8` paddings; flush
  `FocusFrame(weight1)` + `SortRow()`.
- **Console**: replace the hand-rolled inner `Column(fillMaxSize)` with `RegisteredRegion(fillMaxSize)`;
  `FocusFrame(weight1)` + `FootButtonBar()` flush. Keep the outer `Box(bg)` + `BoxWithConstraints`
  (the `rememberUnitGrid` source) and the pinned-height feed wrapper inside the `FocusFrame` content.

## Intended visual changes (everything else is pixel-identical)

1. **Spool focus card** — horizontal inset collapses from 16dp (Box 8 + internal 8) to a uniform 8dp,
   so the card outline aligns with the `SortRow`/`FilterRow` tiles below it. (The Phase-1-deferred bug.)
2. **Console card top** — moves from 0dp to 8dp, registering on the grid like every other screen.

## Docs / law updates

- `FocusFrame` KDoc — frame is region-owned; the component is flush; `FocusFramePlacement` retired.
- `docs/ui_design/COMPONENTS.md` §FocusFrame / §ListBlock / §FootButtonBar / §SortFilterControlRow —
  "the region owns the 8dp frame (`RegisteredRegion`); components are flush-fill." Add a `RegisteredRegion`
  entry.
- `docs/ui_design/LAYOUT.md` R26 — the 8dp frame is owned once per region by `RegisteredRegion`
  (default-on in `ScreenScaffold`); components never add frame padding; replace the Phase-1 bullet
  about `FocusFramePlacement.Region/Composed`.

## Verification

Geometry is not host-testable → the gate is on-device UAT on **flox + moto** (both ABIs):

1. **Pixel-identical:** flip across CalibrationHub / Standby / Move / Temperature / Probe / Outputs /
   Printers / SystemPage / SystemInfo — every framed Focus/Field edge registers at 8dp and is unchanged
   from before.
2. **Spool fix:** the focus card outline is full-width-to-8dp and aligns with the sort/filter tiles
   (no longer inset narrower).
3. **Console fix:** the card top registers at 8dp; the feed is still tight to its foot bar.
4. **Files:** unchanged.
5. **Regression guard — frozen set unchanged:** About, Settings, Splash, ConfirmGuard, Gallery, and
   **Webcam** (including its cam-picker foot bar still padded) look exactly as before.
6. **Landscape cross-region alignment** (load-bearing): focus card bottom aligns with the field
   `FootButtonBar` bottom (both = region bottom padding 8dp), on every two-region screen.

Host: a tiny test asserting `RegionInset == 8.dp` (and `RegionGap == 8.dp`) replaces the deleted
`FocusFramePaddingTest`. The `@Preview` files (`DesignKitComponentPreviews`, `WebcamPreviews`) render a
bare `FocusFrame`; wrap their previews in `RegisteredRegion` so they keep the 8dp inset (cosmetic).

## Decision log

- **Frame ownership:** hoisted to the **region** (`RegisteredRegion`), reversing Phase 1's
  component-self-frame. Phase 1 was deliberately the recon step (owner, 2026-06-14).
- **Mechanism:** `RegisteredRegion` primitive + `ScreenScaffold` default-on framing with per-region
  `focusFramed`/`fieldFramed` opt-out booleans. Opt-out (not opt-in) so new screens are correct by
  default and frozen screens are explicit, temporary exceptions (owner, 2026-06-14).
- **Interpretation of "sort/filter rows move to field ownership":** registration ownership only — the
  rows stay physically in the focus column; the look does not change (owner, 2026-06-14, Option A).
- **Control rows dockable under either region:** satisfied by construction (identical region primitive
  + flush children), no separate control-stack composable (owner-confirmed).
- **Frozen set:** About, Settings, Splash, ConfirmGuard, Gallery, OldMoveScreen, Webcam — most will be
  deleted or reworked to comply later, so they stay opted-out for now (owner, 2026-06-14).
- **`FocusFramePlacement` retired:** the region owns framing, so the Phase-1 enum/helper/test/param are
  deleted.
- **Codex review:** to be run on the final design + plan via the non-reaping background path before
  execute (per `[[codex-rescue-bg-dispatch-dies-midstream]]`).
