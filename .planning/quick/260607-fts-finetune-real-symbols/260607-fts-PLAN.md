---
quick_id: 260607-fts
slug: finetune-real-symbols
created: 2026-06-07
status: ready
---

# Quick Task 260607-fts: FineTune glyphs → real Material Symbols

**Owner directive:** hand-trace NOTHING except super-custom printer-domain icons (nozzle/bed/tilt/spool).
Phase-17 hand-traced the FineTune glyphs (D-17) — wrong art. Replace with the real Material Symbol set.
All names verified against the shipped font this session.

## Task 1 — flip 8 entries Drawable→Ligature (DinghyIcons.kt)

These names are CONFIRMED present as ligatures in `res/font/material_symbols_outlined.ttf` (render from font, official art, zero files). Keep each `alternate` unchanged:

| token | new |
|---|---|
| KeyboardReturn | `IconRef.Ligature("keyboard_return")` |
| OutputCircle | `IconRef.Ligature("output_circle")` |
| InputCircle | `IconRef.Ligature("input_circle")` |
| SmoothTime | `IconRef.Ligature("avg_time")` |
| FanMode | `IconRef.Ligature("mode_fan")` |
| Speed | `IconRef.Ligature("speed")` |
| MinCruise | `IconRef.Ligature("directions_boat")` |
| SquareCornerVelocity | `IconRef.Ligature("rounded_corner")` |

MaxVelocity/MaxAccel already flipped (prior commit) — leave.

## Task 2 — PressureAdvance → official Google vector drawable

`text_select_move_forward` is bogus; real symbol is `text_select_move_forward_word`, NOT in the (older) bundled font → must be an OFFICIAL Google vector drawable (path verbatim, not hand-traced). Create `app/src/main/res/drawable/text_select_move_forward_word.xml` (960 viewport + `translateY="960"` group for the Material Symbols `0 -960 960 960` viewBox), then point `PressureAdvance` at `Drawable(R.drawable.text_select_move_forward_word)` (alternate stays `pressure_advance`). Exact XML supplied in the executor prompt.

## Task 3 — cleanup + policy

- grep `R.drawable.<name>` per orphaned drawable; delete if unreferenced after T1-T2: keyboard_return, output_circle, input_circle, avg_time, mode_fan, speed, directions_boat, rounded_corner, text_select_move_forward (bogus — always delete).
- Document icon-source policy in DinghyIcons KDoc + docs/ui_design/CLAUDE.md Icons bullet: real Material Symbols by default (Ligature from font; official Google vector drawable when font too old); custom drawables ONLY for printer-domain glyphs (nozzle/bed/tilt/spool); status octagon/triangle ARE in the font (not custom); retires D-17's "no font" stance.

**Out of scope:** MaxVelocity/MaxAccel, printer-domain customs, shape-status drawables (separate app-wide sweep), non-FineTune icons.

**Gate:** `:app:assembleDebug :app:testDebugUnitTest` green (Windows gw.bat); DinghyIconsTest stays green; install on flox for owner eyeball. PA vector uses translateY=960 — if it renders inverted/offset on-device, that transform is the thing to revisit.
