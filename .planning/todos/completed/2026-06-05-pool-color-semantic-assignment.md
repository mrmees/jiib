---
created: 2026-06-05T00:00:00Z
title: Pool-color semantic assignment ("what gets assigned where")
area: ui
target_phase: theming-conformance-followon
files:
  - app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt
  - app/src/main/java/works/mees/dinghy/theme/TokenBridge.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
---

## Problem

Surfaced during Phase 15 (15-06) on-device UAT on flox. With the new generative palette,
palette-mode switching (Colorful / Simple / High contrast) now visibly changes the Appearance
page (15-06 added a live accent+pool preview strip and pool-"traffic-light"-colored S/M/L
segments). But Matthew flagged the deeper question: **"we need to do some further discussion
on the pool colors and what should get assigned where."** i.e. which generated pool index maps
to which semantic data role across the app (graph traces, heater readouts, directional planes,
status, S/M/L indicator, etc.) and the SHAPE-coded status overlay.

This is exactly the **data-pool / SHAPE-coded-status / THEMING reconciliation** work that the
Phase-15 plan already **SPLIT to a follow-on phase** (per the theme_theory memory — engine-first
in 15, semantics in the follow-on). Deferred by owner choice (2026-06-05) rather than litigated
in the 15-06 checkpoint.

Note: 15-07 (next wave) does the *mechanical* rewire of the three concrete consumers it already
specifies (GraphView traces → pool[i%size], heater readouts → canonical-order pool index, Move
directional planes → directional.xy/.z). This todo is the broader *semantic policy* —
canonicalizing the assignment scheme app-wide and the contrast-ranked data-pool ordering from
`../theme_theory/COLOR-SYSTEM.md` — not the 15-07 mechanical migration.

## Solution

Take it up in the follow-on theming/conformance phase: reconcile `docs/ui_design/THEMING.md`
with the theme_theory COLOR-SYSTEM spec, define the canonical pool→role assignment + SHAPE-coded
status, and apply it in the conformance sweep.
