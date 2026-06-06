---
created: 2026-06-05T00:00:00Z
title: Theme + settings-type pages densify-to-one-page restyle (C6)
area: ui
target_phase: 20
files:
  - app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt
---

## Problem

Surfaced during the 15.2-05 D-10 guided core-screen review (R3; redesign deferred). THEMING.md/LAYOUT.md
C6 now LAW-defines that config/settings-type surfaces (Settings, Theme editor, similar config pages)
are EXEMPT from the ≥64px touch-friendly minimum and should be DENSIFIED — they are a deliberate
close-interaction use case (held in hand, not read across the room), so tighter rows, toggles /
dropdowns / popups, fit-on-one-page. The print-control surfaces stay bound by the ≥64px rule.

Both Settings and Theme passed the core-screen scoring on theme/color ("good enough"), but neither is
densified to the C6 standard — they currently use the across-the-room touch sizing. The restyle EXCEEDS
plan 06's mechanical charter (it is a layout re-density, not a color/intent flip), so it was deferred by
owner decision.

## How to apply

Densify the Settings and Theme-editor pages per C6: tighter rows, toggles/dropdowns/popups, aim for
fit-on-one-page, drop the ≥64px floor on these config surfaces (keep it on print-control surfaces).
This is a **settings-IA densification follow-on** — best folded into Phase 20 (or its own settings pass)
alongside the related deferred settings-IA work. **Cross-link:** the existing
`2026-06-05-settings-vs-devices-boundary.md` todo (the Settings-vs-Devices page-identity decision)
should be resolved in the same pass — the page framing decision and the densify restyle are the same
surface, so do them together.
