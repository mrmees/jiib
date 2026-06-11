---
phase: 23-design-language-foundation
plan: "02"
subsystem: docs/ui_design
tags:
  - design-law
  - layout
  - components
  - jiib-redesign
  - documentation
dependency_graph:
  requires:
    - 23-01 (icon registry — registers DinghyIcons.Sort/FilterList etc. this doc references)
  provides:
    - docs/ui_design/LAYOUT.md (rewritten two-region grammar)
    - docs/ui_design/COMPONENTS.md (new component-class catalog)
    - docs/ui_design/CLAUDE.md (updated pointers)
  affects:
    - 23-03 (UnitGrid.kt Compose helper — implements the formula LAYOUT.md specifies)
    - 23-04 (kit components — implement classes COMPONENTS.md defines)
    - 23-05 (SpoolScreen pilot — consumes classes this plan documents)
    - 24+ (every redesign screen references COMPONENTS.md + LAYOUT.md)
tech_stack:
  added: []
  patterns:
    - "Component-class pattern — named Compose wrappers + modifiers with token-based specs"
    - "Fill convention — transparent=content, surface-filled=controls"
    - "Field-takeover picker pattern — in-place Field swap, no screen push"
    - "FIT-PRESERVING unit U formula — min(nTarget, nMaxFit) × U == contentMinDim"
key_files:
  created:
    - docs/ui_design/COMPONENTS.md
  modified:
    - docs/ui_design/LAYOUT.md
    - docs/ui_design/CLAUDE.md
decisions:
  - "LAYOUT.md is the single source of truth for the unit U formula; COMPONENTS.md §4 cross-references it"
  - "Component-class catalog is extracted from real screens (Spoolman pilot), not designed abstractly first"
  - "Stepper and scrubber restyle explicitly DEFERRED to Phase 26 in COMPONENTS.md §7"
  - "DetailCard.ringColor and FillMeter.fillColor are THEME-01 data carve-outs — never brandTint-clamped"
  - "SortFilterControlRow leading type-tile is mandatory; bare option-list row is NON-CONFORMANT"
  - "ScreenScaffold.gutter slot preserved for backward compat; grammar no longer names it first-class"
metrics:
  duration: "~30 minutes"
  completed: "2026-06-09"
  tasks_completed: 3
  tasks_total: 3
  files_changed: 3
---

# Phase 23 Plan 02: Design-Language Foundation Docs Summary

## One-liner

Rewrote LAYOUT.md from three-region Focus/Field/Gutter to two-region Focus/Field grammar with unit U, fill convention, foot-of-list, and floating e-stop; created COMPONENTS.md as the component-class catalog every redesign screen references.

## What Was Built

**Task 1 — LAYOUT.md rewrite (`f7bb7ea`):**
- Replaced the three-region opening table with a two-region Focus/Field table + "Where the gutter's jobs went" rehoming table (FootButtonBar / FloatingEStop / System page).
- Added §"The unit U" with the full FIT-PRESERVING formula: `nTarget`, `nMaxFit`, `N = min(nTarget, nMaxFit)`, `U = contentMinDim / N`. Explains why the naive `(dim/N).coerceAtLeast(64dp)` overflows at 360dp × N=7 (7×64=448dp > 360dp) and why the `nMaxFit` reduction step is required. Includes the dp-derived / DPI-derived reconciliation note.
- Added §"Content vs controls — fill convention" (transparent=content, surface-filled=controls, no group-label words).
- Added §"Foot-of-list pattern" with FootButtonBar structural placement note.
- Added §"Floating e-stop" overlay spec (top-left Focus corner, printing-only, 1U, decoupled from layout flow).
- Preserved all three NON-NEGOTIABLES, C3, C6, orientation rules, content-display rules.
- Replaced the CSS gutter scaffold with dual scaffolds (redesigned = no gutter div; pre-redesign = legacy gutter slot).

**Task 2 — COMPONENTS.md created (`2ef89d3`):**
- New document: 8 sections covering philosophy, fill convention, component catalog (8 classes), unit U cross-reference, interaction patterns, SortFilterControlRow anatomy, stepper/scrubber deferral, and scope/evolution.
- Full component catalog table with fill type / background / border / primary content / implementation path for: ListRow, DetailCard, FillMeter, FootButtonBar, FloatingEStop, SortFilterControlRow, control tile (general), ListBlock.
- Locked THEME-01 data carve-outs: DetailCard.ringColor and FillMeter.fillColor are item data, never theme roles, never brandTint-clamped.
- SortFilterControlRow anatomy locked: leading recessed type-tile mandatory; a bare option-list row is explicitly called out as NON-CONFORMANT.
- Field-takeover picker, sort-vs-filter distinction, and conditional Load/Unload patterns documented.
- Stepper/scrubber restyle explicitly DEFERRED to Phase 26 with rationale.

**Task 3 — CLAUDE.md updated (`853f321`):**
- Revised the "Focus / Field / Gutter layout grammar" bullet to "Focus / Field grammar (Gutter removed in the jiib redesign)" with full rehoming explanation.
- Added pointer to rewritten LAYOUT.md and new COMPONENTS.md.
- Updated layout NON-NEGOTIABLES bullet to reference foot-button-row grid alignment and unit U.
- Preserved all icon law bullets verbatim (never-auto-pick, Material Symbols rules, procedure steps).

## Verification

All automated checks passed:
- `grep -qi 'unit' LAYOUT.md` — PASS
- `grep -qi 'fill convention' LAYOUT.md` — PASS
- `grep -qi 'floating e-stop' LAYOUT.md` — PASS
- NON-NEGOTIABLE count: 5 (≥ 3 required) — PASS
- C3/C6 count: 10 (≥ 2 required) — PASS
- Old "Gutter | primary actions" first-class row removed — PASS
- FIT-PRESERVING formula present (nTarget, nMaxFit) — PASS
- dp-derived note present — PASS
- COMPONENTS.md exists with all required classes — PASS
- COMPONENTS.md has takeover, Phase 26 deferral, THEME-01 carve-outs, NON-CONFORMANT label — PASS
- CLAUDE.md references COMPONENTS.md — PASS
- CLAUDE.md icon law preserved verbatim — PASS
- CLAUDE.md gutter-removed grammar reflected — PASS

## Deviations from Plan

None — plan executed exactly as written.

## Threat Flags

None. Documentation-only plan; no new attack surface.

## Known Stubs

None. These are design-law documents; all content is substantive prose, not placeholders.

## Self-Check: PASSED

Files created/modified:
- `docs/ui_design/LAYOUT.md` — exists ✓
- `docs/ui_design/COMPONENTS.md` — exists ✓
- `docs/ui_design/CLAUDE.md` — exists ✓

Commits:
- `f7bb7ea` — exists ✓
- `2ef89d3` — exists ✓
- `853f321` — exists ✓
