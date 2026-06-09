---
title: Component Classes — the "CSS for Compose" catalog
date: 2026-06-09
context: Companion to 2026-06-09-jiib-redesign-direction.md. The foundation kit every redesigned screen consumes.
area: ui
status: intent (to become docs/ui_design/COMPONENTS.md)
---

# Component Classes — a named catalog of reusable component styles

## The idea

Android has no CSS, but Compose supports exactly what's wanted: a **named, documented catalog of
component styles**. Each class is a spec for border / background-token / elevation / padding / corner —
the **same token philosophy as the existing color system**, extended from *color* to *component
archetype*.

The point is shared vocabulary: the owner points at an element and says **"that's a `card`"**, Claude
applies the class, and we never re-argue the styling. Built as **reusable Compose wrappers and/or
modifier extensions** so screen migrations just *consume* classes — making every migration cheaper.

- Expressed as wrapper composables (`ListRow { }`, `DetailCard { }`) and/or named modifiers
  (`Modifier.cardSurface()`).
- All specs reference existing **semantic color tokens** (THEMING.md) — never raw colors.
- Destined to become **`docs/ui_design/COMPONENTS.md`**, sitting beside THEMING.md and the
  (to-be-rewritten) LAYOUT.md.

## Working catalog (seed list — refine during the Spoolman pilot)

| Class | What it is | Notes |
|-------|-----------|-------|
| `ListRow` | A scrollable-collection row | The core primitive of the tiles→lists shift |
| `DetailCard` | The detail/info pane container | Spoolman detail pane is the reference |
| `FilterChip` / `FilterBar` | Filter buttons over a list | **Shaded background** to separate from the scrollable list |
| `FillMeter` | Horizontal "how much remains" meter | New, from the Spoolman detail tweak; likely reusable (e.g. progress) |
| `FootButtonBar` | Action buttons pinned to the foot of a list | The generalized gutter replacement |
| `FloatingEStop` | Floating emergency-stop | Every screen, printing-only |
| (more) | TBD | Extract from real screens, don't over-design up front |

## How it's built (sequencing)

**Do not design the kit in the abstract and hope it fits.** Extract it from a **rebuilt Spoolman**
(the pilot) — Spoolman already exercises the list, filter chips, detail card, fill meter, and
foot-button bar. The catalog crystallizes from a real, owner-approved screen, then later screens
consume it. See the sequencing section in the redesign-direction note.

## Fill convention — content vs controls (LOCKED 2026-06-09)

A load-bearing visual rule that separates the two: **list items are translucent** (transparent fill,
outline only — they're *content*); **buttons / control tiles / action bars are filled** (`surface`
shade — they're *controls*). This is what makes a tappable `ListRow` read differently from a
`FootButtonBar` button or a sort/filter tile even though both carry an outline. Selection/active states
layer accent tint on top. No text labels are used to group controls — separation is carried by
**outline + fill shade + accent**, never a header word (owner: "context + styling should be all we
need to group things naturally," 2026-06-09).

## Open naming question

Owner-proposed terms: **"component classes"** / **"style classes."** Pick one canonical term for the
doc so it's unambiguous in conversation. (Used "component classes" throughout these notes.)
