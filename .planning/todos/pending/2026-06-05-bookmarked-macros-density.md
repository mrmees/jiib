---
created: 2026-06-05T21:55:00Z
title: Bookmarked macros screen wastes vertical space — size to fit ~9-12, then scroll
area: ui
target_phase: 21
files:
  - app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt
---

## Problem

Phase 15.2-06 sweep (owner note, 2026-06-05): the Bookmarked macros screen currently
wastes vertical space — rows/tiles are oversized for the typical handful of bookmarks.
Users won't bookmark more than a few, so the layout should size to **fill the available
space with roughly 9-12 macros**, and only **scroll** once the count exceeds that
threshold. Today it neither fills the space nor densifies.

This is responsive layout sizing (fill-to-fit with a scroll threshold), not a
mechanical token/intent flip — deferred out of the 15.2-06 mechanical-fix scope.

## How to apply

Fold into a macros/UI polish pass (Phase 21 ship polish, or earlier if a macros phase
appears). Size the grid/list so ~9-12 entries fill the Field responsively
(portrait + landscape); switch to scrolling beyond the threshold. Keep role tokens +
fsSp scale.
