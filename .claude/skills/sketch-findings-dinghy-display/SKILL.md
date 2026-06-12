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
- **Intent = color (UPDATED by R5+R18+R19, 2026-06-12 — supersedes the sketch-era assignment):**
  stop/red (could be destructive) · caution/amber (could be destructive but part of the process —
  load/heat filament, resets, force-move; NOT ordinary jog/home) · go (the screen's EXPECTED
  action — Print, Load, Save, AND motion when motion is the screen's purpose, R19) · accent
  (neutral items + plain nav: Back, Home; Back = FIRST foot-bar button per R8). Neutral RETIRED
  for action buttons; survives only as the inactive toggle-state style (R18). Buttons filled;
  rows translucent. Law: `docs/ui_design/THEMING.md`.
- **Icons:** owner-curated registry only (`material-icon-bucket.json` + `DinghyIcons.kt`), Material
  Symbols by ligature; **never auto-pick a glyph — ask**. Icon tiles ~70-80% of U (prominent
  icons; dense list-pane icons stay small — see Post-Phase-26 UAT rules below).
- **Conditional waterfall:** printer state surfaces relevant actions; PrintStatus is the root when a
  print exists, not a destination; floating e-stop printing-only.
- **Adjustment:** step-based (no scrubber on the floor), 3-zone Focus, inline `was X` baseline.
- **Post-Phase-26 UAT formatting rules UAT-1..UAT-5** (owner, 2026-06-10 — apply to ALL new/rebuilt
  screens from Phase 26 onward):
  - UAT-1 (+R15/R23, 2026-06-12): prominent icons ~70-80% of U (`uDp * 0.75f` etc.); list-row
    leading icons = **0.6U via `ListRowIcon`** (R23 — U-relative, does NOT grow with S/M/L;
    supersedes R16's text-tracked fsSp(22)). Full icon-size tier law (text-companion icons =
    fsSp, cell-filling + list-row icons = U, fixed-dp retired): `docs/ui_design/COMPONENTS.md §7c`.
  - UAT-2: icon · name · value rows — name start-aligned with the icon, value end-aligned;
    `Spacer(weight(1f))` carries the gap.
  - UAT-3: current-style scrubber track + thumb ≤ 1U (`heightIn(max = uDp)`).
  - UAT-4: Focus top-left corner reserved for `FloatingEStop`; keep important content clear of it.
  - UAT-5: controls cap at 1U unless deliberately named otherwise; LED `ColorWheel` is the sole
    sanctioned >1U exception in Outputs.
  Full rule text: `docs/ui_design/LAYOUT.md §"Post-Phase-26 UAT formatting rules"`.
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
- Former open follow-ups, both RESOLVED 2026-06-12: scrubber style = the 004 ringed-thumb
  SeekBar port (R9 — law in `docs/ui_design/COMPONENTS.md §7`); oklch caution-reads-red fix is
  in the normalization sweep's scope (R10).
</findings_index>

<metadata>
## Processed Sketches
- 001-waterfall-home
- 002-spoolman-reconcile
- 003-finetune-adjust
- 004-scrubber-style
</metadata>
