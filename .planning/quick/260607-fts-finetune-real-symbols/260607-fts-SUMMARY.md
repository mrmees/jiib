---
quick_id: 260607-fts
slug: finetune-real-symbols
status: complete
completed: 2026-06-07
commit: d633ed9
---

# Quick Task 260607-fts — FineTune glyphs → real Material Symbols

Converted the FineTune glyph set from Phase-17 hand-traced local vectors to the real Material Symbol set, per owner directive ("hand-trace NOTHING except super-custom printer-domain icons").

## What landed (commit d633ed9)

**Task 1 — 8 flips Drawable→Ligature** (all confirmed present in `material_symbols_outlined.ttf`): KeyboardReturn, OutputCircle, InputCircle, SmoothTime(avg_time), FanMode(mode_fan), Speed, MinCruise(directions_boat), SquareCornerVelocity(rounded_corner). Now render the official font glyph; zero new files. (MaxVelocity/MaxAccel were already flipped in a prior commit.)

**Task 2 — PressureAdvance** → official Google vector drawable `text_select_move_forward_word.xml` (path data verbatim, 960 viewport + `translateY="960"` for the Material Symbols `0 -960 960 960` viewBox). The prior `text_select_move_forward` was bogus (not a real symbol); the real one (`_word`) is NOT in the bundled font (older Material Symbols cut), so it ships as the official vector drawable — not hand-traced, not a ligature.

**Task 3 — cleanup + policy:**
- Deleted all 9 orphaned hand-traced drawables (0 refs after T1-T2): keyboard_return, output_circle, input_circle, avg_time, mode_fan, speed, directions_boat, rounded_corner, + the bogus text_select_move_forward.
- Icon-source policy documented in DinghyIcons KDoc + `docs/ui_design/CLAUDE.md` Icons bullet: real Material Symbols by default (Ligature from font; official Google vector drawable when the font is too old); custom drawables ONLY for printer-domain glyphs (nozzle/bed/tilt/spool); status octagon/triangle ARE in the font (not custom). Retires D-17.

## Deviations / notes

- **Glyph collision (caught by the WR-02 test, resolved):** flipping OutputCircle→`Ligature("output_circle")` collided with the existing `LauncherExtrude`→`Ligature("output_circle")`. The plan's "all distinct" premise was wrong. Resolved per the test's documented escape hatch: **allow-listed `output_circle`** in `iconRef_isUnique_acrossAllEntries` (both are the same extrude/output concept, never co-occur on one screen — OutputCircle = FineTune Extrusion-factor; LauncherExtrude = PrintStatus launcher tile). The test still fails LOUDLY on any un-allow-listed dup.
- **Out-of-scope corruption reverted:** the first executor pass introduced a stray `width`→`widthNo ` typo in `DinghyPreviews.kt:16` (would break the NEXUS7 device spec). Reverted — that file was not in scope.
- **PressureAdvance translateY caveat:** the official vector uses `translateY="960"` for the Material Symbols negative-y viewBox. If on-device it renders inverted/offset, that transform is the thing to revisit. The 8 ligature flips are zero-risk.

## Gate
`:app:assembleDebug` + full `:app:testDebugUnitTest` green; DinghyIconsTest green (incl. the allow-listed uniqueness check). Installed on flox for owner eyeball (FineTune Motion + Extrusion).
