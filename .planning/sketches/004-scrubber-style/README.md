---
sketch: 004
name: scrubber-style
question: "What's the canonical scrubber (drag-adjust) style for future non-stepper screens?"
winner: "Thin track + ringed thumb (owner-referenced style, ported to jiib tokens)"
tags: [scrubber, slider, adjustment, drag, components]
---

# Sketch 004: Scrubber style

## Design Question
Fine-Tune is step-based (003), but other future screens will need drag-adjust. Owner supplied a
reference style (Android SeekBar XML + screenshot): a **thin track with a large ringed thumb**. Port
it to jiib tokens and lock it.

## How to View
open .planning/sketches/004-scrubber-style/index.html
Drag the thumb (or tap the track). Toggle Light (bottom-right) to compare against the reference.

## The Style (owner-approved)
- **Thin track** (6px; ref was 3dp — bumped slightly for visibility/touch), rounded.
- **Filled side = `--accent`** (ref navy `#18244D`); **remainder = `--surface-3`** (ref gray `#94A3B3`).
- **Thumb = ringed circle:** `--surface` knob center + a **thick 5px `--accent` ring**, ~34px visible,
  with an **enlarged invisible touch target (~74px)** for the floor hardware. Press shows an
  `--accent-soft` halo.
- Min/max end labels under the track; value shown above.

## Implementation rule (load-bearing)
**Build the DOM/composable once; update fill width + thumb position + value IN PLACE during drag.**
Do NOT rebuild/recompose the dragged element on each move — that detaches the node / staleifies the
drag closure and kills the gesture (fill-grows-from-middle, value-not-sticking). This is the exact
**Phase-19 inline-scrubber regression** ([[dinghy-display-ondevice-iteration]] / 19-09 fix `fa97efb`).
Left-anchored fill; snap to the param's step.

## Origin
Sketch 004. Source: `sources/004-scrubber-style/index.html`. Reference: owner Android SeekBar XML +
screenshot (thin track, white-centered navy-ring thumb).
