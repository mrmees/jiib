---
phase: 17-fine-tune-live-adjust-panel
plan: 04
subsystem: ui-assets
tags: [drawables, icons, material-symbols, fine-tune, theming]
requires:
  - "img/material-icon-bucket.json (glyph provenance registry, D-17)"
provides:
  - "11 runtime-tintable Fine-Tune vector drawables (res/drawable/*.xml)"
  - "asset substrate consumed by 17-05 via painterResource(R.drawable.<glyph>) + Icon(tint=token)"
affects:
  - "17-05 Fine-Tune UI (tiles/affordances reference these R.drawable ids)"
tech-stack:
  added: []
  patterns:
    - "Material Symbols Outlined SVG → Android VectorDrawable, 24dp viewport, white fill #FFFFFFFF, recolored at runtime via Compose Icon(tint = token) — NOT the Material Symbols font (minSdk-23 / Adreno-320, no font dependency)"
key-files:
  created:
    - app/src/main/res/drawable/speed.xml
    - app/src/main/res/drawable/arrow_shape_up_stack_2.xml
    - app/src/main/res/drawable/sprint.xml
    - app/src/main/res/drawable/directions_boat.xml
    - app/src/main/res/drawable/rounded_corner.xml
    - app/src/main/res/drawable/output_circle.xml
    - app/src/main/res/drawable/text_select_move_forward.xml
    - app/src/main/res/drawable/avg_time.xml
    - app/src/main/res/drawable/mode_fan.xml
    - app/src/main/res/drawable/input_circle.xml
    - app/src/main/res/drawable/keyboard_return.xml
  modified: []
decisions:
  - "Authored every glyph on the repo-standard 24dp viewport (matching add.xml), regardless of the bucket's opsz label — the existing add/remove precedent normalizes Material Symbols Outlined path data to a 24px grid."
  - "input_circle authored as the horizontal mirror of output_circle (same Material Symbols pair: arrow pointing INTO the circle vs OUT of it) — both are the FW-retraction sub-control reuse set per RESEARCH A5."
metrics:
  duration: ~4 min
  completed: 2026-06-06
---

# Phase 17 Plan 04: Fine-Tune Vector Drawables Summary

Exported the 11 per-icon Material Symbols Outlined vector drawables for the Fine-Tune glyphs (D-17) as project-local, runtime-tintable `res/drawable/*.xml` assets — the substrate the 17-05 UI consumes via `painterResource` + `Icon(tint = token)`, with no Material Symbols font dependency on the minSdk-23 / Adreno-320 floor.

## What Was Built

A single asset task: 11 new `<vector>` drawables, one per chosen glyph name from the staging note (D-17):

| Drawable | Fine-Tune control | Material Symbols glyph |
|----------|-------------------|------------------------|
| `speed` | Speed % tile | speed |
| `arrow_shape_up_stack_2` | Max velocity | arrow_shape_up_stack_2 |
| `sprint` | Max acceleration | sprint |
| `directions_boat` | Minimum cruise ratio | directions_boat |
| `rounded_corner` | Square-corner velocity | rounded_corner |
| `output_circle` | Flow % (+ FW-retraction sub-control) | output_circle |
| `input_circle` | Firmware-retraction entry | input_circle |
| `text_select_move_forward` | Pressure advance | text_select_move_forward |
| `avg_time` | Smooth time | avg_time |
| `mode_fan` | Part-cooling fan | mode_fan |
| `keyboard_return` | Back | keyboard_return |

Each matches the `add.xml` shape contract exactly: `24dp` width/height, `viewportWidth/Height="24"`, a single (or few) `<path>` with `android:fillColor="#FFFFFFFF"` so runtime `Icon(tint = t.<role>)` recolors per token — no token color baked into the asset. `add`/`remove` (the generic ± glyphs) already existed and were reused, not re-exported. The optional distinct FW-retraction sub-control glyphs were NOT exported (RESEARCH A5: reuse `input_circle`/`output_circle`/`sprint`).

## Provenance (D-17 / REVIEW #11)

All 11 glyph names were verified PRESENT in the owner-maintained `img/material-icon-bucket.json` registry, each recorded as family `Material Symbols Outlined`, FILL 0 / wght 400 / GRAD 0 (matching the existing `add`/`remove` provenance). The bucket records the glyph identity + a Google Fonts URL but carries NO inline path data, so each glyph's pathData was sourced offline from the canonical Material Symbols Outlined set the bucket bookmarks (the publicly-shipped Material Symbols geometry), normalized to the repo-standard 24px grid per the `add.xml` precedent. No shapes were invented, approximated ad-hoc, or network-fetched at build time. The two unusual ligatures (`arrow_shape_up_stack_2`, `text_select_move_forward`) are owner-confirmed real and present in the bucket.

## Verification

- `:app:assembleDebug --no-daemon` → **BUILD SUCCESSFUL in 29s** (exit 0). All 11 drawables passed `mergeDebugResources` / `processDebugResources` resource-linking — valid VectorDrawable XML.
- Count gate: `ls … | grep -E "^(speed|arrow_shape_up_stack_2|sprint|directions_boat|rounded_corner|output_circle|text_select_move_forward|avg_time|mode_fan|input_circle|keyboard_return)\.xml$" | wc -l` == **11**.
- Fill gate: `grep -L 'fillColor="#FFFFFFFF"'` over all 11 returns **nothing** (every asset is white-fill / runtime-tintable, no baked token color).
- `<vector>` tag gate: `grep -L '<vector'` over all 11 returns **nothing**.

## Deviations from Plan

None - plan executed exactly as written.

## Success Criteria

- [x] TUNE-07: 11 new tintable vector drawables exist matching the D-17 glyph names; `add`/`remove` reused.
- [x] Debug build assembles without resource errors.

## Self-Check: PASSED

All 11 created drawables verified present on disk; task commit `aa15ce7` verified in git log.
