---
sketch: 003
name: finetune-adjust
question: "How do we present and change a numeric value — the adjustment capability — in this system?"
winner: "Stepper (step-based; scrubber deferred to a future style decision)"
tags: [adjustment, stepper, finetune, value-tile, components]
---

# Sketch 003: Fine-Tune (numeric adjustment)

## Design Question
Adjustment is a whole interaction class the home (nav) and Spoolman (browse) never exercised, and it's
a major style driver. Fine-Tune is pure live numeric adjustment. **How does a value read, and how do
you nudge it** — on the Adreno-320 floor (where drag is risky)?

## How to View
open .planning/sketches/003-finetune-adjust/index.html

Tap a param in the list to load it into the adjuster (Focus). Use `–`/`+`, the increment picker, and
(B/C) drag the scrubber track. Top bar: Variant × Orientation × Device(units); bottom-right tools.

## Variants (the adjuster in Focus)
- **A: Stepper** — big value + big `–`/`+` tiles + an increment picker (±step). Tap-only, no drag —
  safest on cheap/old touchscreens. The recommended floor default.
- **B: Scrubber** — big value + a draggable track (fill-from-left) for coarse + fine `–`/`+`.
- **C: Hybrid** — value + scrubber **and** stepper **and** increment picker (the kitchen sink).

## What to Look For
- **Value read:** big tabular numeral + unit. Legible at arm's length? Right hierarchy vs the list?
- **Nudge feel:** stepper tiles are ~1.15U tall (big touch targets). Increment picker = how big a step
  each `–`/`+` makes (±1/±5/±10, or ±0.005/0.01/0.05 for Z). Clear?
- **Scrubber (B/C):** drag the track. On the *real floor* drag is iffy — does A (no drag) feel
  sufficient, or is the scrubber worth the risk for coarse moves?
- **Content vs control:** the param list is translucent (content); the adjuster tiles/steppers are
  filled (controls) — consistent with 001/002.
- **Reset:** per-param reset shows the default value; a reset-all sits in the foot.

## Notes / unassigned glyphs (owner must assign — never auto-picked)
- **Flow** (extrusion multiplier) — no registry glyph. Dashed `?`.
- **Reset / Reset-all** — no registry glyph. Dashed `?`.
- Assigned glyphs used: speed=`speed`, fan=`air`, Z=`height`, PA=`text_select_move_forward_word`,
  accel=`sprint`, nudge=`add`/`remove`, home=`home`.
