---
name: sketch-findings-dinghy-display
description: Validated design decisions, CSS patterns, and visual direction from the jiib redesign sketch experiments (waterfall home, Spoolman lists/detail, Fine-Tune adjustment). Auto-load when building any redesigned UI on dinghy-display.
---

<context>
## Project: dinghy-display (jiib redesign)

A fundamental visual redesign moving jiib from KlipperScreen-style tile grids + hub-and-spoke nav + a
space-eating gutter to a **lists-first, conditional-waterfall** app on real semantic tokens
(Geist/Geist Mono, dark+light, S/M/L). Visual north star = the existing Spoolman page. Supersedes the
old Focus/Field/**Gutter** grammar (Gutter removed). Full rationale lives in
`.planning/notes/2026-06-09-jiib-redesign-direction.md` and `…-component-classes-catalog.md`.

Sketch session wrapped: 2026-06-09 (sketches 001–003).
</context>

<design_direction>
## Overall Direction
- **Grammar:** Focus / Field on one shared grid, **no gutter**. Portrait stacks; landscape is
  Focus | Field side-by-side (~50/50 default).
- **Lists, not tiles.** Collections are scrollable translucent rows; spatial controls (jog pad,
  numpad) stay grids.
- **The unit `U`:** every vertical element is an integer number of units; `U` is DPI-derived from the
  landscape height (5U phone-land → 7U tablet) and held constant through rotation. Design must survive
  at 5U.
- **Content vs controls:** list items translucent (outline only); buttons/tiles/cards filled.
- **No labels where structure suffices** — leading type-icons + outline/fill/accent do the grouping.
- **Intent = color:** accent (physical command + expected action) · go (accept) · caution/amber
  (proceed-at-peril incl. resets) · stop/red (destructive/e-stop) · neutral white (plain setting).
- **Icons:** owner-curated registry only (`material-icon-bucket.json` + `DinghyIcons.kt`), Material
  Symbols by ligature; **never auto-pick a glyph — ask**. Icon tiles ~70% of U.
- **Conditional waterfall:** printer state surfaces relevant actions; PrintStatus is the root when a
  print exists, not a destination; floating e-stop printing-only.
- **Adjustment:** step-based (no scrubber on the floor), 3-zone Focus, inline `was X` baseline.
</design_direction>

<findings_index>
## Design Areas

| Area | Reference | Key Decision |
|------|-----------|--------------|
| Foundations (system) | references/foundations.md | Unit grid `U`, content/control fill, intent colors, icon registry, Focus/Field-no-gutter, oklch-mix hazard |
| Layout & Navigation | references/layout-navigation.md | Morphing waterfall home (Focus-as-card), state→content map, foot-of-list + floating e-stop |
| Lists & Detail | references/lists-and-detail.md | Spoolman: sort-vs-filter, Field-takeover picker, DetailCard + FillMeter, conditional Load/Unload |
| Adjustment Controls | references/adjustment-controls.md | Stepper + increment picker, 3-zone adjuster, inline baseline readout, intent colors |

## Theme
Winning theme tokens at `sources/themes/default.css` (dark + light oklch, Geist, `--fs`, radii).

## Source Files
Original sketch HTML (all variants, winners marked) preserved in `sources/`. Spoolman reference mock
at `sources/002-spoolman-reconcile/reference-mock.html`.

## Companion notes (live decisions / follow-ups)
- `.planning/notes/2026-06-09-icon-assignments-redesign.md` — owner glyph assignments to register
  (incl. the `play_circle`/`stop_circle` load-unload conflict to clean up).
- MANIFEST "Open follow-ups" — scrubber style TBD, oklch caution-reads-red fix.
</findings_index>

<metadata>
## Processed Sketches
- 001-waterfall-home
- 002-spoolman-reconcile
- 003-finetune-adjust
</metadata>
