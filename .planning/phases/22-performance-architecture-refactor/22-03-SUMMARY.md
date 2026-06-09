---
phase: 22
plan: "03"
subsystem: designsystem/icons
tags: [performance, allocation, compose, spoolGlyph, brush-caching]
dependency_graph:
  requires: ["22-01"]
  provides: ["cached-gradient-brush-SpoolGlyph"]
  affects: ["SpoolGlyph", "SpoolGlyphTest"]
tech_stack:
  added: []
  patterns: ["remember(render) cached-Brush (mirrors ColorWheel.rememberHueSweep)"]
key_files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/icons/SpoolGlyph.kt
    - app/src/test/java/works/mees/dinghy/designsystem/icons/SpoolGlyphTest.kt
decisions:
  - "Keyed remember(render) on SpiralRender instance (data-class equality = two Color fields); gradientBrush is null for non-Gradient branches with SolidColor fallback in the draw lambda"
  - "Brush-instance reuse verified by code review (remember placement outside Canvas), not a host-test instance-equality assertion (Compose-runtime infra not wired in host tests)"
  - "Two new equality/inequality tests added to pin the remember(render) key semantics at the data-class level"
metrics:
  duration: "5 minutes"
  completed: "2026-06-09T01:25:43Z"
  tasks_completed: 1
  tasks_total: 1
  files_changed: 2
---

# Phase 22 Plan 03: SpoolGlyph Cached-Brush Allocation Fix Summary

**One-liner:** Eliminated per-draw `Brush.linearGradient` allocations in SpoolGlyph by hoisting the Brush into a composable-scope `remember(render)` block keyed on `SpiralRender`, mirroring the `ColorWheel.rememberHueSweep()` pattern.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Cache SpoolGlyph gradient Brush via remember(render) | 294f9de | SpoolGlyph.kt, SpoolGlyphTest.kt |

## What Was Done

### Task 1: Cache the SpoolGlyph gradient Brush via remember(render)

**Before:** `Brush.linearGradient(...)` was constructed inline inside the `Canvas { ... }` draw lambda on every recomposition. With SpoolGlyph appearing on 5 surfaces each driven at ~4 Hz, this was up to ~20 wasted `Brush` allocations per second even when the filament color never changed.

**After:** The `Brush.linearGradient(...)` construction is now inside:
```kotlin
val gradientBrush = remember(render) {
    if (render is SpiralRender.Gradient) Brush.linearGradient(...) else null
}
```
at composable scope, **above and outside** the `Canvas { }` draw lambda. The Canvas draw body reads the pre-built `gradientBrush` variable only.

**Key design decisions:**
- `remember(render)` keys on the `SpiralRender` instance. `SpiralRender.Gradient` is a `data class(val start: Color, val end: Color)` — structural equality is exactly the two filament `Color` fields. Same-color emission → equal key → cached Brush returned. Color change → unequal key → Brush rebuilt.
- `gradientBrush` is `null` for `Solid` and `Empty` branches (the `if (render is SpiralRender.Gradient)` guard inside `remember`). The draw lambda uses `gradientBrush ?: SolidColor(render.start)` as a safe fallback (the `?:` branch is unreachable in correct usage since the `when` arm is only reached when `render is SpiralRender.Gradient`, but guards against any future refactor regression).
- The `spiralRenderFor(swatches)` call was moved to composable scope (before `remember(render)`) so the render result is available as the key.
- Visual output is pixel-identical — geometry, bounds, spiral path data, keyline, disc body, THEME-01 carve-out all untouched.

**Test additions (SpoolGlyphTest):**
- `sameSwatches_producesEqualGradient_cacheKeyStable` — same two swatches produce `==` `SpiralRender.Gradient` instances, confirming the `remember` key is stable.
- `differentSwatches_producesUnequalGradient_cacheKeyInvalidates` — different second swatch produces `!=` instances, confirming the key invalidates on color change.
- All 4 original behavior tests retained and green.

## Verification

- `testReleaseUnitTest` (filtered: SpoolGlyphTest): **BUILD SUCCESSFUL** — 6 tests pass
- `testReleaseUnitTest` (full suite): **BUILD SUCCESSFUL** — no regressions
- `grep -n "remember(render)"` in SpoolGlyph.kt: returns hit at line 90 (composable scope, outside Canvas)
- `Brush.linearGradient` is absent from the Canvas draw lambda — lives entirely in the `remember(render)` block

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — pure Compose draw-allocation hygiene in the design system. No new auth/network/persistence/input-validation surface.

## Self-Check: PASSED

- [x] `app/src/main/java/works/mees/dinghy/designsystem/icons/SpoolGlyph.kt` — exists and modified
- [x] `app/src/test/java/works/mees/dinghy/designsystem/icons/SpoolGlyphTest.kt` — exists and modified
- [x] Commit `294f9de` exists in git log
- [x] `testReleaseUnitTest` BUILD SUCCESSFUL (full suite)
