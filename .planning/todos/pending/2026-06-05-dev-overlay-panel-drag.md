---
created: 2026-06-05T20:55:00Z
title: Dev theme/printer cycler overlay no longer drag-relocates
area: ui
target_phase: 21
files:
  - app/src/main/java/works/mees/dinghy/ui/shell/DevThemeCyclerOverlay.kt
---

## Problem

While fixing a 15.2-04 checkpoint regression (chip taps bleeding into the parent
panel-drag handler), the gesture fix (`change.consume()` on chip taps) had the side
effect that the overlay panel no longer **drag-relocates** at all — the panel stays
put. Tapping the Style/Size/Printer chips works correctly (steps the axis, no panel
movement), but the deliberate "drag the panel to a new corner" affordance is dead.

Owner decision (2026-06-05): acceptable for now — the cyclers are a DEV-only debug
tool (gated behind About → Developer), not a shipped feature, so this should not hold
up the conformance phase.

## How to apply

If the dev cyclers survive to ship (they may be removed before v1), restore panel
relocation: give the panel its own bare-surface drag region whose pointerInput is
distinct from the chips', so chip taps consume their own gestures while a drag started
on the panel background still moves it. Otherwise close as won't-fix when the dev
overlay is retired.
