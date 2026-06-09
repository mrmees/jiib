---
sketch: 002
name: spoolman-reconcile
question: "How do true filter facets and sort coexist in Spoolman's left grid, on our component system (unit grid, edge-fade list, tokens, fill meter, Weigh+Delete)?"
winner: "C (refined: sort row + filter-picker)"
tags: [spoolman, filtering, sorting, components, detail-card, fill-meter, field-takeover]
---

# Sketch 002: Spoolman Reconcile

## Design Question
The Spoolman look is locked (reference mock), but the design session flattened our **filters** into
**sorts**. This sketch restores true filtering and resolves how filter facets + sort coexist — rebuilt
on everything 001 proved (unit grid `U`, edge-fade list, arm's-length type scale, semantic tokens,
foot-of-list actions) plus the owner divergences (**Weigh + Delete**, **horizontal fill meter**, no
Spoolman icon, no scrollbar).

## How to View
open .planning/sketches/002-spoolman-reconcile/index.html

Top bar: Variant (A/B/C) × Orientation × Device(units). Tap material chips / color swatches to
**filter**; tap sort controls to **sort** (toggles direction). Tap a row to select (drives the
detail). Bottom-right: Dark/Light, text S/M/L, unit grid.

## Variants (how filter + sort coexist)
- **A: Filter-led + sort pill** — left column = DetailCard + **filter facets** (material chips, color
  swatches). **Sort** is a single cycling **pill** in the list header. Filtering is the loud action;
  sorting is quiet. Cleanest separation.
- **B: Split grid** — left column = DetailCard + a **filter** chip row *and* the mock's **6-key sort
  grid** (with direction badge), visually separated. Both live together on the left, equal billing.
- **C: Mock-faithful sort grid + filter bar** — keeps the reference's exact **6-button sort grid** on
  the left (closest to the locked look), and adds true filtering as a **chip bar atop the list**.

## What to Look For
- **Filter vs sort clarity:** is it obvious which control *narrows* the list vs *reorders* it? Tap
  PETG, then tap a color — does the narrowing read clearly (count updates)?
- **Detail pane:** Weigh + Delete top-right (Weigh = re-weigh to update; Delete = red/destructive).
  The **fill meter** is tinted by the spool's real color (THEME-01 data carve-out). No Spoolman icon.
- **Fill meter** — is the horizontal tinted bar the right "how much is left" read? Big enough?
- **Where filters belong on a phone (portrait):** detail on top, a compact material chip-bar over the
  list below. Right call, or should portrait push detail to a tap-through and lead with the list?
- **Unit consistency with 001:** rows, foot buttons, sort tiles all snap to the same `U`.

## Notes / placeholders
- Filament **swatch colors are real data** (the spool's actual color) — correct per the THEME-01
  carve-out; everything else routes through semantic tokens.
- Labels (Weigh/Delete/Home/Scan QR/Add, sort keys "Aa/Date/Wt…") are **text placeholders** — real
  icons are the owner's to choose.
