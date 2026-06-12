---
created: 2026-06-11T18:30:00Z
title: Consider retiring swipe-up drawer nav entirely in favor of the new navigation method
area: ui
target_phase: 28
files:
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/SwipeUpAccumulator.kt
---

## Idea

Owner remark at the 2026-06-11 flox UAT sitting (verbatim):

> "swipe-up nav may be retired entirely in favor of the new navigation method."

Context: the R10 swipe-accumulation fix made the swipe-up drawer gesture reliable (PASS at the
same sitting), but the redesigned screens' Back/FootButtonBar navigation has been carrying most
of the actual nav traffic — the swipe-up gesture may simply no longer earn its keep (and its
gesture-conflict suppression complexity, e.g. the Files-list drawer-swipe carve-out).

## Direction

**Explicitly NO ACTION NOW** — captured for triage only. If acted on, decide what (if anything)
replaces the gesture as the drawer entry point on screens without a drawer affordance, and
whether the instrumented-swipe harness defect ([[dinghy-instrumented-swipe-threshold]]) becomes
moot. Target the nav/polish window (Phase 28 or 29).
