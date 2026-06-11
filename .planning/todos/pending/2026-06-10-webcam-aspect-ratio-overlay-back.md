---
created: 2026-06-11T04:10:00Z
title: Webcam — always preserve feed aspect ratio + overlay Back on the feed
area: ui
target_phase: 28
files:
  - app/src/main/java/works/mees/dinghy/ui/webcam/
  - docs/ui_design/LAYOUT.md
---

## Problem

Owner note at the 2026-06-10 UAT work-through (E3 H.264 verification — feed itself works
great): two webcam-screen layout goals aren't met:

1. **Aspect ratio:** the camera feed should ALWAYS maintain its native aspect ratio (no
   stretch/crop to fit the slot).
2. **Back button:** should be OVERLAID on top of the camera feed, not placed below it —
   the feed should get the full surface.

Owner explicitly acknowledged this likely conflicts with the standard design rules
(FootButtonBar at foot, UAT-4 top-left e-stop reserve): "obviously live video feeds are a
unique situation."

## Direction

- Treat live-video surfaces as a documented LAW exception (add to LAYOUT.md alongside the
  existing Views-exceptions table / sacred-aspect-ratio rule): full-bleed feed at native
  aspect, controls overlaid.
- Back overlay placement must still respect UAT-4 (top-left is the e-stop reserve) — put
  Back elsewhere on the feed (e.g. bottom-left/bottom-bar overlay), intent colors per law
  (Back = red/stop family per C7).
- Mind the Adreno-320 overlay-above-SurfaceView lesson from Phase 21 (overlay rendering
  above the video SurfaceView is already proven).

## Acceptance

- Feed letterboxes/pillarboxes instead of stretching in both orientations
- Back floats over the feed, ≥64px target, not in the top-left e-stop reserve
- Exception documented in LAYOUT.md
- Owner re-eyeball on flox

Triage 2026-06-11: target Phase 28 (as filed) — webcam aspect/overlay-Back layout polish; LAYOUT.md doc touch rides the same pass.
