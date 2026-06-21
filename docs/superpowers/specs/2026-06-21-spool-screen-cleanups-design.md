# Spool / Filaments Screen Cleanups — Design

**Date:** 2026-06-21
**Status:** Draft (awaiting owner spec review)
**Scope:** Three independent cleanups to the Spoolman filament screen.

## Context

The filament screen (`ui/spool/`) is the as-built Spoolman north-star surface. Three rough
edges to clean up:

1. The Focus header reserves empty trailing space (a vestigial icon slot) at the end of the
   title; the "loaded" status lives as a body text badge.
2. The chemistry (TYPE) filter shows 6 **fixed** material families regardless of inventory,
   unlike the manufacturer (MFG) filter, which is **inventory-derived**.
3. The color filter is **single-select** (re-tap to clear), unlike MFG and chemistry which
   are already **multi-select**.

Relevant files:
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt`
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt`
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt`
- `app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt` (shared header)

---

## Change 1 — Loaded icon in the title trailing slot

### Today
`FocusHeader` (FocusFrame.kt:283-379) centers the title with **symmetric** padding
(`padding(horizontal = slot)`, line 316) so it clears the start identity icon. That mirror
padding reserves an equal empty `slot` at the END of the title even when nothing renders
there — the "space at the end of the line" the owner sees. The end slot is only filled today
by an optional **tappable** `trailingActionIcon` (revert-to-default glyph; lines 358-378).

The "loaded" state shows as a body badge inside `SpoolDetailContent`
(SpoolScreen.kt:854-855): a green `CheckCircle` + the text **"Loaded on this printer"**
(`spool_badge_loaded`).

### Target
- **Add a non-interactive trailing STATUS icon** to `FocusFrame` / `FocusHeader`, distinct
  from the existing tappable `trailingActionIcon`:
  - New params: `trailingStatusIcon: DinghyIcon? = null`,
    `trailingStatusTint: Color? = null`,
    `trailingStatusContentDescription: String? = null`.
  - Rendered in the **end slot** at the same position/size as the trailing action glyph, but
    with **no `clickable`** (it is a status indicator, not a control).
- **Reclaim the trailing slot ONLY when the title overflows** (owner decision 2026-06-21 —
  preserve true centering for everything that fits). True centering requires symmetric
  padding, so the header chooses padding mode by measuring the title:
  - Wrap the title region in `BoxWithConstraints`; use `rememberTextMeasurer()` to measure the
    title's intrinsic single-line width (`softWrap = false`, `maxLines = 1`,
    `DinghyType.focusHeader` style). Memoize on `(title, maxWidth, t.fs)`.
  - Let `endSlotPresent = (trailingActionIcon != null && onTrailingAction != null) ||
    trailingStatusIcon != null`, and `symmetricBudgetPx = (maxWidth - slot*2).toPx()`.
  - **Fits** (`intrinsicPx <= symmetricBudgetPx`): keep symmetric `padding(horizontal = slot)`,
    `textAlign = Center`, **no marquee** → truly centered about the card center, exactly as
    today. (When a status/action glyph IS present, it sits in the already-reserved trailing
    slot — still symmetric, still centered.)
  - **Overflows**: `padding(start = slot, end = if (endSlotPresent) slot else 0.dp)` +
    `basicMarquee()`. The title now uses the trailing slot's width (reclaimed) unless an
    end glyph occupies it; centering is moot while scrolling.
  - **Measurement details (Codex #6):** `maxWidth` is the `BoxWithConstraints` inner width
    *after* the header's `padding(horizontal = FocusInset)` (so measure inside that padded
    Box). Measure with the exact `DinghyType.focusHeader.toTextStyle(t)` style; include
    density + style/`t.fs` in the `remember(...)` key. **Coerce a negative budget to 0**
    (`(maxWidth - slot*2).coerceAtLeast(0.dp)`) for tiny widths. Apply `basicMarquee()` ONLY
    on the overflow path (never on the fitting path).
  - **This is a GLOBAL FocusHeader behavior change**, but a conservative one: only titles that
    would otherwise marquee are affected (a borderline-long title that fits in
    `fullWidth - slot` but not `fullWidth - 2*slot` now fits without marquee). No title that
    already fits ever shifts. Applied system-wide for consistency.
  - **Blast radius (Codex #7):** there are ~35 `FocusFrame(` call sites; **FineTune is the
    only current `trailingActionIcon` user** — so it is the regression check for end-slot
    *action* precedence (action must still win the slot and reserve it). Every other screen
    has an empty end slot today and is exercised by the fitting/overflow paths above.
- **Spool wiring:** at the FocusFrame call (SpoolScreen.kt:339-350) pass
  `trailingStatusIcon = if (isSelectedLoaded) DinghyIcons.CheckCircle else null`,
  `trailingStatusTint = t.go`,
  `trailingStatusContentDescription = stringResource(R.string.cd_spool_loaded)`.
  `isSelectedLoaded` is already in scope at that call site.
- **Remove dead `isActive` plumbing (Codex #8):** once the loaded body badge is gone,
  `SpoolDetailContent`'s `isActive` parameter (and the `isActive = isSelectedLoaded` argument)
  is unused by the body — delete the parameter and argument. `isSelectedLoaded` is still used
  directly at the `FocusFrame` call for `trailingStatusIcon`.
- **Remove the body "Loaded on this printer" badge** (SpoolScreen.kt:854-855). Keep the
  **archived** badge (856-858). The `spool_badge_loaded` string becomes unused — delete it;
  reuse `cd_spool_loaded` as the trailing status content description.

### Constraints / notes
- `trailingStatusIcon` and `trailingActionIcon` are **mutually exclusive** per screen (both
  target the one end slot). The spool screen uses status only. If both are ever provided,
  the existing tappable action takes precedence and status is ignored.
- Icon glyph confirmed by owner: **reuse `CheckCircle`**, tinted `t.go`.
- Status icon size = `slot * IDENTITY_ICON_RATIO`, matching the start identity glyph and the
  trailing action glyph.

---

## Change 2 — Chemistry (TYPE) filter is inventory-derived (families)

### Today
The TYPE filter renders all 6 fixed `MATERIAL_FAMILIES` (SpoolHolder.kt:46-53) unconditionally
(SpoolScreen.kt:505-527). By contrast, the MFG filter derives its vendor universe from the
actual spool inventory in `loadChips()` (SpoolHolder.kt:524-549) — only manufacturers
represented by a physical spool appear.

### Target (owner choice: **in-inventory families**, keep grouping)
- In `loadChips()`, additionally derive the set of **material strings present on spools** the
  same way vendors are derived — walk the unfiltered base spool read (`allow_archived=false`,
  no facets) `spool → filament.material`, trim, distinct. **Reuse the SAME base spool read that
  already derives vendors** — do NOT derive from the `/v1/material` endpoint (Codex #4): that
  endpoint lists material *definitions* including ones with zero owned spools (the exact bug
  already fixed for vendors).
- **Remove the now-dead `materials` state + `listMaterials()` read (Codex #4):** the existing
  `materials: List<String>` state (SpoolHolder.kt:152, populated at :526/:548) is written but
  never read by the UI — only `MATERIAL_FAMILIES` is rendered. Drop the field, the
  `client.listMaterials()` call, and `parseSpoolmanMaterials` if it has no other caller (verify
  tests first; update any fixture that references it).
- Compute the subset of `MATERIAL_FAMILIES` that have **at least one matching spool**: a family
  is "available" if any in-inventory material (uppercased) **contains** any of the family's
  tokens (uppercased). `contains` mirrors Spoolman's substring filter semantics (so `PLA`
  catches `PLA+`), keeping availability consistent with what the filter will actually match.
  - Accepted edge: a token that is a substring of an unrelated material (e.g. `PC` ⊂ `PCTG`)
    could mark a family available when only the unrelated material is owned. Pre-existing
    family-grouping tradeoff; acceptable.
- Store the available families on state (e.g. `availableMaterialFamilies: List<String>` of
  family labels, preserving `MATERIAL_FAMILIES` order). Degrades to empty independently like
  the other chip universes.
- The TYPE filter UI (SpoolScreen.kt:505-527) iterates `availableMaterialFamilies` instead of
  the raw `MATERIAL_FAMILIES` constant. Empty → show an empty-state message consistent with
  the MFG empty state; add a new `spool_type_empty` string mirroring `spool_mfg_empty`.
- Family grouping/labels and the existing toggle + comma-joined `filament.material=A,B` query
  semantics are unchanged. `MATERIAL_FAMILIES` remains the source-of-truth token map (used to
  resolve a family label → its tokens both for availability and the query).

### Notes
- **Chip derivation is LOAD-TIME, not refresh-time (Codex #5):** `loadChips()` runs only in
  `load()`, not in `refresh()`. So `availableMaterialFamilies` (like the vendor universe)
  reflects inventory as of the last `load()` and updates on the next one — identical to MFG
  today. We are NOT adding a chip reload to `refresh()`; this is intentional parity with MFG.
- A selected family that is no longer in the available set (e.g. inventory changed before a
  reload) simply won't render as a row — same as MFG vendors. Selection state is keyed by label;
  `clearMaterialFamilies()` / the Clear control still resets it. Acceptable — matches MFG.

---

## Change 3 — Color filter becomes multi-select (OR)

### Today
`SpoolFilters` (SpoolHolder.kt:85-86) holds a single `colorSwatchHex: String?` plus
`colorFilamentIds: List<Int>?`. `applyColorSwatch()` (390-423) is single-select: re-tap clears,
otherwise it classifies the tapped swatch to one family, fetches the filament library, and sets
`colorFilamentIds` to that family's matching ids. `colorSwatchHex` doubles as a gcode-seed HINT
highlight (set with `colorFilamentIds == null` by `seedPrefilter()`, 475-498). The grid
(`SpoolPicker.kt` `ColorSwatchGrid`) takes a single `selectedHex: String?`.

### Target
Replace the single hex with a multi-select set, union the matching ids (OR across families),
and keep the gcode-seed hint working by splitting it into its own field.

- **State (`SpoolFilters`):**
  - `colorSwatchHexes: List<String> = emptyList()` — the hard-selected swatches (multi-select).
  - `colorFilamentIds: List<Int>? = null` — **union** of filament ids across all selected
    families (null when no hard color filter).
  - `colorSeedHex: String? = null` — NEW: the gcode-seed hint highlight (display-only, no
    filtering). Separated from the hard set so a seed can be shown without being a hard filter.
- **Color tap must NOT close the picker (Codex #1, BLOCKER):** today
  `onTapSwatch` (SpoolScreen.kt:148) does `applyColorSwatch(it); closeFilterPicker()` — the
  auto-close defeats multi-select. **Remove the `closeFilterPicker()` call**; the COLOR picker
  closes via Done/Clear exactly like TYPE/MFG (which never auto-close on a chip tap).
- **`applyColorSwatch(hex)`** becomes a toggle:
  - **Normalize** `hex` via `normalizeColorHex` before storing/comparing; store normalized
    hexes in `colorSwatchHexes` so membership/highlight checks are stable (Codex #3).
  - Toggle the normalized `hex` membership in `colorSwatchHexes`.
  - **Consume the seed (Codex #2):** clear `colorSeedHex` on the first hard color interaction
    (any `applyColorSwatch` call), so a re-tap can't leave the tile highlighted as a lingering
    seed after its hard filter is removed.
  - Recompute `colorFilamentIds` as the **union**: fetch the filament library once; target
    families = `colorFamily(hex)` for each selected hex; a filament matches if any of its
    sub-colors classifies into **any** selected family. `null` when the set becomes empty.
  - **Fetch failure** leaves the filter unchanged (don't collapse the list to nothing) —
    preserve current robustness.
- **`seedPrefilter()`** sets `colorSeedHex = colorHint`, `colorSwatchHexes = emptyList()`,
  `colorFilamentIds = null`.
- **`clearColor()`** clears all three (`colorSwatchHexes = emptyList()`,
  `colorFilamentIds = null`, `colorSeedHex = null`).
- **Filter-active indicator** (SpoolScreen.kt:325):
  `state.filters.colorSwatchHexes.isNotEmpty() || state.filters.colorSeedHex != null`.
- **Query building** (SpoolHolder.kt:589) is unchanged — still consumes `colorFilamentIds`
  (now the union).
- **`ColorSwatchGrid` / `ColorTile`** (SpoolPicker.kt): change `selectedHex: String?` →
  `selectedHexes: Set<String>`; a tile is highlighted if its **normalized** hex is in the set
  (compare via `normalizeColorHex` on both sides, Codex #3). The screen passes
  `colorSwatchHexes.toSet()` **plus** `colorSeedHex` (when set) as the highlight set;
  `onTapSwatch(hex)` still drives `applyColorSwatch`.

### Notes
- OR semantics across both selected families and a filament's multiple sub-colors — matches the
  owner's "allow multi color selection like mfg and chem."
- Multi-select means N family fetches collapse to one library fetch + N classifications, so no
  extra network cost beyond today.

---

## Testing

- **Unit (SpoolHolder):**
  - Chemistry availability: given a spool set with materials {PLA+, PETG}, only PLA and PETG
    families are available; ABS/ASA, TPU, PC, Nylon are not. Empty inventory → empty.
  - Color multi-select: toggling two swatches unions their families' filament ids; re-toggling
    one removes its contribution; clearing empties ids; fetch failure leaves state unchanged.
  - Seed hint + consumption (Codex #2): `seedPrefilter` sets `colorSeedHex` without a hard
    filter; the first `applyColorSwatch` clears `colorSeedHex` and adds the (normalized) hex to
    `colorSwatchHexes`; a subsequent re-tap removes it cleanly with no lingering highlight.
  - Hex normalization (Codex #3): a swatch tapped with a differently-cased/`#`-prefixed hex
    still highlights and toggles correctly.
- **Conformance/build:** font + icon conformance tests must stay green (no new inline
  font/size; `CheckCircle` already registered). Existing FontConformanceTest applies.
- **On-device UAT** (flox + moto, both ABI slices): loaded spool shows the check in the title
  trailing slot and the body badge is gone; a short title stays truly centered whether loaded
  or not; a long (overflowing) title with no check uses the reclaimed trailing width before
  marquee-ing; chemistry filter lists only owned families; color filter selects multiple
  swatches without the picker closing on each tap.
- **FocusHeader blast-radius spot-check (Codex #7):** verify **FineTune** (the only
  `trailingActionIcon` user) still reserves its end slot and the revert glyph wins the slot;
  spot-check one long-title, no-action screen for the overflow reclaim + a short-title screen
  for unchanged centering.

## Out of scope
- No change to sort, to the MFG filter, to the row-level loaded badge
  (`SpoolRowTrailing`, still text), or to query/network plumbing beyond the union id list.
- No restyle of the FocusHeader beyond the conditional trailing padding + status slot.
