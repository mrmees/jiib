# FocusFrame conformance pass (Phase 1 of 2) — design

**Date:** 2026-06-14
**Status:** approved design, pending implementation plan
**Scope:** the **Focus side** only. A later **field-side conformance pass** (Phase 2) rolls in the
sort/filter rows, `FootButtonBar`, and `ListBlock` registration. Phase 1 is designed to set Phase 2
up, not to be ripped out.

## Problem

The Focus region's bounded card (`FocusFrame`) renders at inconsistent heights and widths across the
~21 screens that use it. Switching between most screens should leave the Focus area the exact same
size; instead some are "too tall," one is "too narrow," and a few are off by a few dp.

## Root cause

`ScreenScaffold` already sizes the Focus slot correctly (landscape = 50% width / full height;
portrait = 50% height via `weight`). Inside that slot, `Modifier.fillMaxSize()` and
`Modifier.weight(1f)` are equivalent — **the sizing keyword is not the problem.**

The problem is the **8dp edge-registration frame** (`docs/ui_design/LAYOUT.md` R26 / C-E2): the card
border must sit a uniform 8dp inside its slot on all four sides. `FocusFrame` internally owns only
its **horizontal** 8dp (`.padding(horizontal = ListFrameInset)`); the **vertical** top/bottom 8dp is
left to each caller, and callers diverge:

| Pattern | Screens | Symptom |
|---|---|---|
| `fillMaxSize()`, no vertical padding | PrintStatus (Standby/Printing/Paused/Terminal), Probe, BedMesh, Tilt, ScrewsTilt, Extrude, Macros, Temperature ×3, About, Settings, SystemPage, SystemInfo | frame touches top/bottom → **too tall** |
| `weight(1f).padding(vertical = 8dp)` ✅ | **CalibrationHub** (the one correct reference) | correct |
| `.padding(8.dp)` (vertical ✓ but doubles horizontal: 8 + internal 8 = 16) | Move, FineTune | right height, **too narrow** |
| `fillMaxWidth().padding(top = 8, bottom = 4)` | Printers, Files | bottom off by 4 |
| **padded `Box` wrapper** `Box(weight(1f).padding(8.dp)){ FocusFrame(fillMaxSize) }` | **Outputs** (selected branch) | 16dp horizontal; not a plain frame |

Content does **not** force the frame larger — it's bounded by the scaffold weight. No intrinsic-height
constraints are needed; the entire fix is the missing/duplicated registration frame.

## The fix — `FocusFramePlacement`

Move frame ownership into the class so callers can't get it wrong. Add a **role enum** (not a bare
`Dp`, not a boolean — chosen for greppability and to allow a future region-owned mode in Phase 2
without an API rethink; Codex review, gpt-5.5, 2026-06-14):

```kotlin
enum class FocusFramePlacement {
    /** The FocusFrame IS the whole Focus region. It self-owns the uniform 8dp registration frame
     *  on all four sides. Callers pass SIZING ONLY (fillMaxSize / weight) — never frame padding. */
    Region,
    /** The FocusFrame is one card composed inside a larger Focus column whose siblings/wrapper own
     *  the vertical registration (sort/filter rows, a foot bar). Keeps today's horizontal-only frame;
     *  the caller column owns vertical. Phase-2 untangles these. */
    Composed,
}
```

`FocusFrame` gains `placement: FocusFramePlacement = FocusFramePlacement.Region`. Internal padding:

- `Region` → `.padding(ListFrameInset)` (all four sides, 8dp; value from the shared `ListFrameInset`).
- `Composed` → `.padding(horizontal = ListFrameInset)` (today's behavior, unchanged).

**Default = `Region`** because most screens are whole-region cards. A new ordinary screen that forgets
the flag gets the correct 8dp card; a new composed screen that forgets it produces a visible, rare
error (better than silently recreating today's common bug). `contentInset` is independent and
untouched.

## Bucket classification & per-screen actions

### Bucket 1 — `Region` (default); strip caller frame padding, pass sizing only

Audit rule: strip frame padding in **both** forms — on the `FocusFrame` modifier **and** on any
wrapping `Box`/`Column`.

- **No caller edit** (already sizing-only `fillMaxSize()`; fixed for free by the default):
  PrintStatus (all 4 modes), Probe, BedMesh, Tilt, ScrewsTilt, Extrude, Macros, Temperature ×3,
  About, Settings, SystemPage, SystemInfo.
- **CalibrationHub** — drop `.padding(vertical = 8.dp)` (renders identically; the 8dp relocates into
  the class). This screen is the geometry anchor everything converges onto.
- **Move, FineTune** — drop `.padding(8.dp)` (fixes the doubled horizontal / too-narrow width).
- **Printers** — drop `.padding(top = 8, bottom = 4)` **and add full-slot sizing**. Its current
  modifier is `fillMaxWidth()` + vertical padding with **no `weight`/`fillMaxSize`** — stripping
  alone would leave it wrap-content. Add `fillMaxSize()` (or `weight(1f)`). *(Codex trap.)*
- **Outputs** — **remove the padded `Box` wrapper's `.padding(8.dp)`** around the selected-branch
  `FocusFrame` (keep the `Box`'s `weight(1f)` sizing). This also fixes its latent 16dp horizontal.

### Bucket 2 — `Composed`; pass `placement = FocusFramePlacement.Composed`, leave layouts byte-for-byte unchanged

These compose their own vertical scheme (8/4/4/8 gaps) and cross-region `FootButtonBar` alignment.
One line added each; deferred to the Phase-2 field-side pass (where the sort/filter rows belong).

- **Spool** — `Box(weight1, top8/bottom4/h8){FocusFrame}` + `SortRow` + `FilterRow`; `FilterRow.bottom=8`
  aligns its tiles with the Field `FootButtonBar`.
- **Files** — `FocusFrame(weight1, top8/bottom4)` + `SortRow(top4/bottom8)`.
- **Console** — non-scaffold single `Column`; `FocusFrame(weight1, contentInset=0)` above a
  `FootButtonBar` (which self-owns vertical 8dp).

### Empty-state framing (owner decision, 2026-06-14: **frame them**)

Two screens currently render **no `FocusFrame`** until something is selected, so they look
structurally different from every other screen. Wrap the empty/unselected state in a `Region`
`FocusFrame` using the screen's **existing owner-locked identity** (no new icons — icon law):

- **Outputs**, nothing selected (`selectedKey == null`): the centered "Select an output" prompt
  becomes `FocusFrame` content. Identity: `icon = DinghyIcons.OutputSection`,
  `title = R.string.outputs_title`.
- **Printers**, no profiles (`activeProfile == null`): the empty-headline/body becomes `FocusFrame`
  content. Identity: `icon = DinghyIcons.SystemRowPrinters`, `title = R.string.system_row_printers`
  (matching the screen's own selected-branch identity).

## Out of scope / deferred (recorded so the deferral is intentional)

- **Field-side registration (Phase 2):** centralize so each region is framed once and
  `FocusFrame`/`ListBlock`/`FootButtonBar`/`SortRow`/`FilterRow` become flush-fill; untangle the three
  `Composed` screens (sort/filter rows move to field ownership). The `placement = Composed` call sites
  are Phase 2's exact worklist. Do **not** move registration into `ScreenScaffold` now — field
  registration is still split across `ListBlock`/caller-padding/`FootButtonBar`, and scaffold-level
  framing would need exceptions for Webcam/Spool/Files/Console (Codex: Phase-2 territory).
- **Spool's 16dp horizontal double-pad** (Box 8 + internal 8): preserved by `Composed`; marked
  **Phase-2 debt**. Fixing now would drag a composed screen into this migration.
- **Inner `contentInset`** (incl. the `FocusInset/2` half-inset screens): unchanged — it controls
  content breathing room, not outer registration.
- **`OldMoveScreen`** (debug Gallery): audit routing; do not let a repo-wide sweep rewrite dead code.
  Migrate or explicitly exclude.

## Docs / law updates (part of Phase 1)

- `FocusFrame` KDoc — the frame self-owns all four sides in `Region`; callers pass sizing only;
  `Composed` for composed-focus screens.
- `docs/ui_design/COMPONENTS.md §FocusFrame` — the placement contract.
- `docs/ui_design/LAYOUT.md` R26 / C-E2 — `FocusFrame` self-satisfies its top/bottom 8dp by
  construction (like `FootButtonBar`'s self-owned vertical gapS); callers never add frame padding.

## Verification

Pure layout geometry (host tests won't see it) → on-device UAT on **flox + moto** (both ABIs):

1. Bucket-1 Focus frames are pixel-identical across CalibrationHub / Standby / Move / Temperature /
   Settings / etc. — all four edges register at 8dp.
2. **Regression guard:** Spool / Files / Console look **unchanged**.
3. The two newly framed empty states (Outputs unselected, Printers empty-profile) render the
   consistent card + 1U header with the correct existing identity.
4. Landscape: focus card bottom aligns with `FootButtonBar` bottom (both 8dp from screen bottom).

## Decision log

- **Frame ownership:** FocusFrame owns it now (Option A); scaffold-level centralization deferred to
  Phase 2's field-side pass (owner, 2026-06-14).
- **Primitive:** role enum `FocusFramePlacement`, not `composedFocus: Boolean` / `verticalFrame: Dp`
  (Codex gpt-5.5 review — greppable, forward-compatible with a Phase-2 region-owned mode).
- **Default:** `Region` (self-frame); inverting would recreate the current bug.
- **Empty states:** framed, using existing owner-locked identities (owner, 2026-06-14).
- **Codex review** (gpt-5.5/xhigh, true-background path) verdict: *"Change X — not a blocker after
  adjustments"*; all adjustments folded in above.
