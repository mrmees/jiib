---
phase: 19-output-controls-fans-lights-generic-pins
plan: 01
subsystem: design-system / icon registry
tags: [icons, ligatures, material-symbols, output-controls, cross-phase]
requires:
  - bundled font material_symbols_outlined.ttf v2.944 (carries all 8 D-09 glyphs)
  - DinghyIcons registry + verify_ligatures.py gate (Phase 18.1)
provides:
  - "DinghyIcons.Output{Heater,Fan,Led,Servo,Pin,PwmTool,Section} tokens (D-01..D-07)"
  - "FanMode reassigned to `air` (D-08) — part-cooling fan glyph"
  - "verify_ligatures.py NEEDED set extended with the 8 D-09 glyphs"
affects:
  - "Wave-2 Phase-19 detail rows (consume DinghyIcons.Output* symbolically)"
  - "ExtrusionScreen part-cooling fan tile (now renders `air`, no call-site edit)"
tech-stack:
  added: []
  patterns:
    - "owner-locked glyph registry entries behind the ligature-resolution gate"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - tools/verify_ligatures.py
decisions:
  - "FanMode → `air` (D-08): mode_fan_2 now owns the generic-fan OUTPUT glyph, so the part-cooling fan moves to `air` to stay visually distinct"
  - "No DinghyIconsTest edit needed — it derives uniqueness/drift checks dynamically from DinghyIcons.all"
  - "Removed `mode_fan` from verify_ligatures NEEDED (no IconRef.Ligature references it post-reassignment)"
metrics:
  duration: ~6 min
  completed: 2026-06-08
  tasks: 2
  files: 2
---

# Phase 19 Plan 01: Output-Type Icon Registry + D-08 Fan Reassignment Summary

Registered the 7 owner-locked Phase-19 output-type icons (D-01..D-07), performed the cross-phase
part-cooling fan glyph reassignment (D-08: `mode_fan` → `air`), and extended `verify_ligatures.py`
to gate all 8 new glyphs (D-09) — the icon foundation Wave-2 detail rows consume, with zero glyph risk.

## What Was Built

**Task 1 — `8a6211a`** (`DinghyIcons.kt`):
- Added 7 new `val` entries, each `DinghyIcon(IconRef.Ligature("<owner-locked>"), alternate = "<unique>")`:
  - `OutputHeater` → `mode_heat` (D-01)
  - `OutputFan` → `mode_fan_2` (D-02)
  - `OutputLed` → `lightbulb_2` (D-03 — shared by led/neopixel/dotstar/pca9533/pca9632)
  - `OutputServo` → `cyclone` (D-04)
  - `OutputPin` → `check_box` (D-05 — both digital and PWM output_pin variants)
  - `OutputPwmTool` → `vital_signs` (D-06)
  - `OutputSection` → `output` (D-07 — Outputs section / App-Drawer tile)
- All 7 added to the explicit `DinghyIcons.all` drift-guard list.
- D-08 cross-phase reassignment: `FanMode` flipped from `IconRef.Ligature("mode_fan")` → `IconRef.Ligature("air")`,
  keeping the `FanMode` val name + `alternate = "fan_mode"`. KDoc updated to explain the reassignment.
  Sole consumer `ExtrusionScreen.kt:271` references `DinghyIcons.FanMode` symbolically → no call-site edit.

**Task 2 — `87913d2`** (`verify_ligatures.py`):
- Added the 8 D-09 glyph names to `NEEDED`: `mode_heat`, `mode_fan_2`, `lightbulb_2`, `cyclone`,
  `check_box`, `vital_signs`, `output`, `air`.
- Removed `mode_fan` from `NEEDED` (no `IconRef.Ligature` references it after the D-08 reassignment).
- `DinghyIconsTest` needed no edit — it iterates `DinghyIcons.all` dynamically (alternate uniqueness,
  rendered-IconRef uniqueness, drawable-keeper drift-guard) and stays green with the 7 new entries.

## Verification

- `python tools/verify_ligatures.py` → `55 needed, 3953 ligatures in font, missing: []` — **exit 0** ✅
- All 8 D-09 glyphs present in `NEEDED`; `mode_fan` removed (grep-confirmed) ✅
- `:app:compileDebugKotlin` → BUILD SUCCESSFUL ✅
- `:app:testDebugUnitTest --tests *DinghyIconsTest` → BUILD SUCCESSFUL (uniqueness + drift-guard green) ✅
- grep proofs: 7 new ligature entries present; `val FanMode` shows `air`; zero `IconRef.Ligature("mode_fan")`
  in `app/src/main`; exactly one `DinghyIcons.FanMode` consumer (ExtrusionScreen) — no regression site ✅

## must_haves coverage

- "Every Phase-19 output family resolves to its owner-locked glyph (D-01..D-07)" — 7 tokens registered ✅
- "Part-cooling fan icon reassigned mode_fan→air (D-08), ExtrusionScreen still renders a fan glyph" — done, consumer unchanged ✅
- "verify_ligatures.py exits 0 with all 8 D-09 glyphs in the NEEDED set" — confirmed ✅

## Threat mitigations applied

- **T-19-01-01** (glyph substitution): no substitution — used owner-locked D-01..D-09 ligatures verbatim;
  `verify_ligatures.py` hard gate (exit 1 on any missing) confirms all 8 resolve; registry uniqueness test green.
- **T-19-01-02** (D-08 regression of ExtrusionScreen): grep proved exactly one FanMode consumer; compile gate +
  DinghyIconsTest green; no call-site edit required (symbolic reference).

## Deviations from Plan

None — plan executed exactly as written. Zero deviations.

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt (modified)
- FOUND: tools/verify_ligatures.py (modified)
- FOUND commit: 8a6211a (Task 1)
- FOUND commit: 87913d2 (Task 2)
