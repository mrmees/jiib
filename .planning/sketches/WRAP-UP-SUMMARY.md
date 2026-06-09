# Sketch Wrap-Up Summary

**Date:** 2026-06-09
**Sketches processed:** 3
**Design areas:** Foundations · Layout & Navigation · Lists & Detail · Adjustment Controls
**Skill output:** `./.claude/skills/sketch-findings-dinghy-display/`

## Included Sketches
| # | Name | Winner | Design Area |
|---|------|--------|-------------|
| 001 | waterfall-home | A · Focus-as-card | Layout & Navigation |
| 002 | spoolman-reconcile | C · sort-row + filter-picker | Lists & Detail |
| 003 | finetune-adjust | Stepper (step-based) | Adjustment Controls |

## Excluded Sketches
_None._

## Design Direction
Lists-first, conditional-waterfall redesign on real semantic tokens (Geist/Geist Mono, dark+light,
S/M/L). Focus/Field grammar with the **Gutter removed**; portrait stacks, landscape side-by-side
(~50/50). Three capability archetypes validated on real screens: **navigate** (morphing waterfall
home), **browse/detail** (Spoolman), **adjust** (Fine-Tune).

## Key Decisions
- **Unit grid `U`** — DPI-derived, 5U (phone-land) → 7U (tablet), constant through rotation; everything
  is integer units; essential layout survives at 5U.
- **Content vs controls** — list items translucent, controls filled.
- **No labels where structure suffices** — leading type-icons + outline/fill/accent group things.
- **Intent colors** — accent = physical/expected action, caution/amber = resets & proceed-at-peril,
  stop/red = destructive/e-stop, go/green = accept, neutral = plain setting.
- **Icon registry only** — Material Symbols by ligature from the owner's bucket + `DinghyIcons.kt`;
  never auto-pick. Tiles ~70% of U.
- **Waterfall home** — one surface morphing idle/printing/terminal; PrintStatus is the root when
  printing; foot-of-list actions; floating printing-only e-stop; Focus stays lean (no row-data dup).
- **Spoolman** — sort (in-place toggle) vs filter (Field-takeover picker); DetailCard + color-tinted
  FillMeter; Weigh+Delete; conditional Load/Unload.
- **Adjustment** — step-based (no scrubber on the floor); 3-zone adjuster; inline `was X` baseline.

## Follow-ups (also in MANIFEST)
- **Scrubber style** for future non-stepper screens — owner reference pending (couldn't fetch the SO link).
- **Icon registry additions** — `2026-06-09-icon-assignments-redesign.md` (incl. play_circle/stop_circle conflict).
- **oklch caution-reads-red bug** in `hifi.css` `.ctl.warn` — fix in Layer-1.
- These findings feed the **Layer-1 foundation phase** → `docs/ui_design/COMPONENTS.md` + LAYOUT.md rewrite.
