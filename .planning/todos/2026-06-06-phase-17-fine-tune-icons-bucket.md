---
created: 2026-06-06T00:00:00Z
title: Fold Phase-17 Fine-Tune glyphs into material-icon-bucket.json + verify 2 ligatures
area: ui
target_phase: 17
files:
  - img/material-icon-bucket.json
---

## Problem

Per-control glyphs for the Fine-Tune panel are ALREADY CHOSEN in `phase-17-fine-tune-staging.md` §Icons
(authoritative — see CONTEXT D-17). They are NOT in `img/material-icon-bucket.json` yet, and the bucket
is the canonical icon registry going forward. So this is a small fold-in + verify task, not an
icon-selection task.

## Chosen glyphs (from staging — fold into the bucket with usage notes)

**Motion:** Speed % → `speed` · Max velocity → `arrow_shape_up_stack_2` · Max acceleration → `sprint`
· Minimum cruise ratio → `directions_boat` · Square-corner velocity → `rounded_corner`

**Extrusion:** Flow % → `output_circle` · Pressure advance → `text_select_move_forward` · Smooth time
→ `avg_time` · Part-cooling fan → `mode_fan` · Firmware-retraction entry → `input_circle`

**Generic:** `add` (+) / `remove` (−) for the ± nudge · `keyboard_return` for Back

`output_circle` and `input_circle` are already in the bucket; `sprint` is too (but mis-noted — see below).

## Actions

1. **Verify two unusual ligatures** are real Material Symbols Outlined names; fall back to a near
   equivalent only if missing: `arrow_shape_up_stack_2` (max velocity), `text_select_move_forward`
   (pressure advance).
2. **Add `add` and `remove`** to the bucket as the canonical generic increment/decrement glyphs.
3. **Reconcile the `sprint` note:** bucket currently notes `sprint` = "generic speed fields," but
   staging uses `speed` for Speed % and `sprint` for Max acceleration. Staging per-field wins — tidy
   the bucket note.
4. (Optional) Decide whether FW-retraction sub-controls (retract length/speed, unretract extra
   length/speed) get distinct glyphs or reuse `input_circle`/`output_circle`/`sprint`.

Fill before Phase 17 execute. Planner exports the chosen ligatures to `res/drawable/*.xml` and
references them via `painterResource` (established pattern).
