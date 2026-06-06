---
created: 2026-06-06T00:00:00Z
title: Add Phase-17 Fine-Tune tuning icons to material-icon-bucket.json
area: ui
target_phase: 17
files:
  - img/material-icon-bucket.json
---

## Problem

`img/material-icon-bucket.json` is the canonical/primary icon source (Phase-17 CONTEXT D-17). The
2026-06-06 export was verified to be MISSING icons needed by the Fine-Tune / Live-Adjust tiles. The
planner/executor must not invent ligatures — these have to be added to the bucket by the owner first.

## Icons to add (with usage notes, like the other bucket entries)

**Generic nudge (decided):**
- `add` — generic increment (+) for value tiles
- `remove` — generic decrement (−) for value tiles

**Motion group (no bucket icon yet):**
- Max velocity
- Max acceleration
- Minimum cruise ratio
- Square-corner velocity

**Extrusion group (no bucket icon yet):**
- Pressure advance
- Smooth time
- Part-cooling fan  ← **no fan icon exists in the bucket at all**

**Firmware-retraction mini-screen (4 sub-controls):**
- Retract length · Retract speed · Unretract extra length · Unretract speed
- May reuse `input_circle` (retract) / `output_circle` (unretract) / `sprint` (speed) if the owner
  prefers, rather than four new glyphs.

**Already in the bucket (no action):** Speed % → `sprint`; Flow % → `output_circle`; Firmware-retraction
entry → `input_circle`; Back → `keyboard_return`.

## How to apply

Owner picks Material Symbols Outlined glyphs on fonts.google.com, adds them to the bucket with notes,
re-exports `img/material-icon-bucket.json`. Fill before Phase 17 execute. Planner exports the chosen
ligatures to `res/drawable/*.xml` and references them via `painterResource` (established pattern).
