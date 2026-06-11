---
created: 2026-06-05T00:00:00Z
title: Move — Z-button vertical-layout rework (C3)
area: ui
target_phase: 17
files:
  - app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
---

## Problem

Surfaced during the 15.2-05 D-10 guided core-screen review (R2; redesign deferred). The Move screen's
Z adjustment currently sits in a horizontal row. THEMING.md/LAYOUT.md C3 now LAW-defines that a control
for a vertical quantity (Z) must NOT be placed in a horizontal row when the active orientation
(landscape vs portrait) layout affords a vertical arrangement — a vertical Z control reads as "up/down"
the way the physical axis moves.

This is a layout rework (re-arranging the Z control, orientation-aware), which EXCEEDS plan 06's
mechanical color/intent charter, so it was deferred by owner decision.

## How to apply

Rework the Move Z control to a vertical arrangement where the orientation layout allows it (per C3 /
LAYOUT.md). Phase 17 (Fine-Tune / Live-Adjust) is the natural home — it reworks the live-adjust
movement controls and will likely adopt the C2 3-cell increment picker (R1) at the same time, so the
Z-vertical rework rides along with that pass. Keep within the Focus/Field/Gutter grammar and the sacred
aspect-ratio rule.

Triage 2026-06-11: target Phase 27 — Move is rebuilt in the Motion redesign; fold the C3 Z-button vertical rework into that migration.
