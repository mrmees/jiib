---
created: 2026-06-05T00:00:00Z
title: Resolve Settings-vs-Devices boundary (Connection placement / "printer settings" reframe)
area: ui
target_phase: theming-conformance-followon
files:
  - app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/DevicesScreen.kt
---

## Problem

Surfaced during Phase 15 (15-06) on-device UAT on flox. The rebuilt Settings hub still
carries a **Connection** section (edits the active printer's host/port/key), but Phase 14
added a **Devices** screen that owns printer add/delete/switch. Matthew finds Connection-in-
Settings redundant now: "the connection state still doesn't really belong in the settings if
we have another menu page that handles the adding/deleting/managing of individual printers."

He floated reframing the whole page: **"maybe this whole settings page needs to become a
'printer settings' page instead of the 'app settings' page."** Theme is already per-profile
(Phase 14), which makes a per-printer-settings framing coherent.

The original `docs/ui_design/` UI LAW predates the Devices screen — back then Settings owned
connection by default. The Devices screen (Phase 14) created the overlap. This is a
page-identity / IA decision, not a bug, so it was **deferred** (owner choice, 2026-06-05) to
the follow-on theming/conformance phase rather than rushed into the 15-06 checkpoint. 15-06
ships with Connection left as-is.

## Options on the table (decide during the follow-on phase)

- **Printer settings hub** — Connection + Appearance framed as the ACTIVE printer's settings;
  Feature toggles + System become a small app-level section; Devices stays the switcher.
  (Matthew's floated idea; least re-architecture.)
- **App settings only** — strip Connection out of Settings into a net-new per-printer editor
  on the Devices screen; Settings becomes Appearance · Feature toggles · System. Cleanest
  app-vs-device split but more work (touches Phase-14 Devices code, likely its own plan).

## Solution

Fold into the follow-on theming/conformance phase's discuss step. Pick a framing, then
re-lay-out SettingsScreen (+ possibly DevicesScreen) accordingly. Keep within UI LAW.
