---
title: Icon assignments surfaced during the jiib redesign sketches
date: 2026-06-09
context: Owner-assigned Material Symbols glyphs captured during /gsd-sketch 002 (Spoolman). To be added to the registry (DinghyIcons.kt + img/material-icon-bucket.json) during Layer-1 implementation. Never auto-pick — [[dinghy-never-pick-icons-ask]].
area: ui
status: to-register
---

# Icon assignments — jiib redesign (owner-assigned 2026-06-09)

Glyphs the owner assigned during the Spoolman sketch. All are real Material Symbols (bundled
`material_symbols_outlined.ttf`). These need adding to the registry (`DinghyIcons.kt` and the
`img/material-icon-bucket.json` notes) when Layer-1 implements these surfaces.

## New / clarified assignments
| Function | Glyph (ligature) | Notes |
|----------|------------------|-------|
| Sort (control-group leader) | `sort` | NEW — wasn't bucketed |
| Filter (control-group leader) | `filter_list` | NEW — wasn't bucketed |
| Weigh spool (re-weigh to update remaining) | `scale` | = `DinghyIcons.Scale`; binds it to the Weigh action |
| **Load spool** | `expand_circle_up` | NEW — **overrides** the old bucket note `play_circle` = "load/swap spool" |
| **Unload current spool** | `expand_circle_down` | NEW — **overrides** the old bucket note `stop_circle` = "unload spool" |
| Flow / extrusion multiplier | `output_circle` | REUSE — already the extrude glyph (`DinghyIcons.OutputCircle`) |
| Reset a single setting | `reset_wrench` | NEW |
| Reset all settings | `reset_settings` | NEW |

⚠️ **Registry conflict to clean up:** the bucket currently notes `play_circle` = "load spool / swap
spool" and `stop_circle` = "unload spool". The owner reassigned load/unload to
`expand_circle_up`/`expand_circle_down`; remove or repurpose the stale `play_circle`/`stop_circle`
notes so two glyphs don't claim the same function. (`play_circle`/`stop_circle` may still be wanted
for a separate "swap" concept — confirm at registration.)

## Already-registered glyphs reused on Spoolman (no change)
match_case (sort by name) · calendar_clock (sort by date) · balance (sort by weight) ·
experiment (material) · palette (color) · storefront (brand) · thermostat (temps) ·
calendar_add_on (added date) · delete (delete spool) · qr_code (scan) · home (nav home) ·
keyboard_return (back, per bucket note) · delete_sweep (clear filter, per bucket note) ·
check_circle (selected option) · disabled_by_default (e-stop).

## Standing rule
Any function without an assigned glyph renders a dashed `?` placeholder in sketches and is reported to
the owner — **never auto-picked** ([[dinghy-never-pick-icons-ask]]).
