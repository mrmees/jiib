---
created: 2026-06-05T00:00:00Z
title: Printers — replace corner-icon affordance with explicit Edit-mode / Delete-mode buttons (R4)
area: ui
target_phase: 16
files:
  - app/src/main/java/works/mees/dinghy/ui/screen/DevicesScreen.kt
---

## Problem

Surfaced during the 15.2-05 D-10 guided core-screen review (R4; redesign deferred). The Printers
(Devices) screen passed the core-screen scoring on theme, but its current edit/delete affordance is a
corner-icon on each printer entry — a discoverability/clarity weakness. The owner wants the corner-icon
affordance replaced with **two explicit drawer buttons: an "Edit mode" toggle and a "Delete mode"
toggle**, so the destructive/edit intent is an explicit deliberate mode, not a tiny per-row glyph.

This is an interaction-model redesign (new mode toggles + re-architecting the list affordance), which
EXCEEDS plan 06's mechanical color/intent charter, so it was deferred by owner decision.

## How to apply

Replace the per-entry corner-icon affordance on the Printers/Devices screen with two explicit
drawer-level toggle buttons — "Edit mode" and "Delete mode" — that put the list into the respective
mode; entries then respond to taps according to the active mode. Conform to THEMING.md intent colors
(Delete-mode toggle = danger/stop family; Edit-mode = neutral/setting). **Phase placement:** fold into
Phase 16 (the Home/Status redesign pass is the nearest foundation-surface phase) OR a dedicated Printers
pass — pick whichever the discuss step finds the better fit; note the choice when scheduling. Cross-link
the `2026-06-05-settings-vs-devices-boundary.md` todo if the Devices page identity shifts.

Triage 2026-06-11: target Phase 28 — Printers (now PrintersScreen.kt, was DevicesScreen.kt) is in the System/Settings cluster restyle.
