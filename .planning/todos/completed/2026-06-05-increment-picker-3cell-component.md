---
created: 2026-06-05T00:00:00Z
title: Build the 3-cell increment-picker component (C2)
area: ui
target_phase: 17
files:
  - app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
  - app/src/main/java/works/mees/dinghy/designsystem/control/
---

## Problem

Surfaced during the 15.2-05 D-10 guided core-screen review (R1; redesign deferred). The app's
increment/step pickers are ad-hoc per screen. THEMING.md C2 now LAW-defines a single 3-cell pattern
the whole app should adopt: `[decrement] [center value display] [increment]`, with two interaction
modes:

- **(a) SHARED increment** (multiple controls share one step value): the +/- buttons step the
  increment through a predecided list, and TAPPING the center value scrolls up through the selection
  list.
- **(b) SINGLE-MEASUREMENT increment** (the step applies to one value): TAPPING the value performs the
  adjustment and the arrows perform the action.

This is a reusable-component build that EXCEEDS plan 06's mechanical charter (it is new UI, not a
color/intent flip), so it was deferred by owner decision.

## How to apply

Build the 3-cell increment picker as a shared design-system component realising C2's two modes. Phase
17 (Fine-Tune / Live-Adjust) naturally owns it — that phase is built on live-adjust increment controls
— and Move plus the other increment screens adopt it from there. Conform to THEMING.md C1/C5 for the
intent colors of the cells (the arrows performing the screen's expected action = accent).
