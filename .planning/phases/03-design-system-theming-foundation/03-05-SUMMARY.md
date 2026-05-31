---
phase: 03-design-system-theming-foundation
plan: 05
subsystem: render
tags: [render, compose-canvas, views-canvas, theming, d-11, d-06, d-13, pitfall-4]
requires:
  - "render/RingBuffer.kt (D-12 bounded rolling window — the data substrate, plan 03-02)"
  - "theme/ThemeTokens.kt (@Immutable resolved tokens — accent/surface2, plan 03-01)"
  - "theme/compose/LocalTokens.kt (Compose token boundary, plan 03-03)"
  - "theme/views/ThemeableView.kt (Views push-tokens seam D-06, plan 03-04)"
provides:
  - "render/ProgressRing.kt (Compose Canvas single-arc progress primitive, D-11/D-13)"
  - "render/GraphView.kt (Views custom-Canvas line graph implementing ThemeableView, D-11/D-06)"
  - "render/GraphView.sanitize(snapshot, pixelWidth) (pure NaN/Inf filter + downsample cap, Pitfall 4)"
  - "render/GraphViewHost.kt (AndroidView host pushing tokens + ring snapshot into GraphView)"
affects:
  - "Phase 4 Print Status (consumes ProgressRing + GraphView)"
  - "Phase 5 Temperature (EXTENDS GraphView into the full multi-series graph)"
tech-stack:
  added: []
  patterns:
    - "Compose Canvas single-arc, value-driven redraw, NO animation (D-13)"
    - "Classic-Views custom Canvas: reused Path (rewind), pre-allocated Paints, allocation-free onDraw (Pitfall 4)"
    - "Push-tokens theming across the toolkit boundary: applyTokens + invalidate, no view recreation (D-06)"
    - "Pure extractable sanitize/downsample helper for host-side testing without a Canvas"
key-files:
  created:
    - "app/src/main/java/works/mees/dinghy/render/ProgressRing.kt"
    - "app/src/main/java/works/mees/dinghy/render/GraphView.kt"
    - "app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt"
    - "app/src/test/java/works/mees/dinghy/render/GraphDownsampleTest.kt"
  modified: []
decisions:
  - "GraphView.sanitize extracted as a pure companion function (not a private method) so the cap + NaN/Infinity filter are host-testable on the JVM without Robolectric/Canvas."
  - "Downsample uses uniform-stride sampling (Math.round(i*span/denom)) keeping both endpoints — cheap, deterministic, allocation-bounded."
  - "setData falls back to a DEFAULT_PIXEL_CAP (256) when width==0 (sample arriving before layout) so an early snapshot is still bounded."
  - "GraphView draws a translucent accent-tinted fill area under the line (the hifi.css .g-area tint) as a single cheap filled Path — fill color = accent at low alpha, no per-point gradient (Pitfall 4)."
metrics:
  duration_min: 2
  tasks: 2
  files: 4
  completed: 2026-05-31
---

# Phase 3 Plan 5: Shared Render Primitives Summary

The render half of the shared render/throttle primitive: a Compose-Canvas progress ring (low-churn) and a classic-Views custom-Canvas line graph (high-churn per ADR 0001) drawing the bounded `RingBuffer` snapshot, plus the `AndroidView` host that push-themes and feeds it — both static-redraw at the throttled cadence, no animation (D-13), with the input-sanitization + downsample-cap contract pinned here for every later live surface to inherit.

## What Was Built

**Task 1 — `ProgressRing` (Compose Canvas, D-11/D-13)** — `@Composable fun ProgressRing(progress, modifier)` drawing a track arc (`surface2`) + progress arc (`accent`) on `Canvas(modifier.aspectRatio(1f))` (the sacred square, LAYOUT.md NON-NEGOTIABLE 2). Stroke width is a fraction of `size.minDimension` (ratio-only sizing, no hardcoded px). `progress` is coerced to `0f..1f` and `NaN`→`0f` so no malformed sweep escapes. Colors via `LocalTokens.current` only (THEME-01). No `animate*AsState` / `rememberInfiniteTransition` / tween anywhere — value-driven redraw only (D-13).

**Task 2 — `GraphView` + `GraphViewHost` + `GraphDownsampleTest`** — `class GraphView : View, ThemeableView`. ONE reusable `Path` (`rewind()` each draw, never `Path()` in `onDraw`), pre-allocated stroke + translucent fill `Paint`s, a sanitized `FloatArray` working copy. `applyTokens(t)` recolors paints from `accent.toArgb()` + `invalidate()` (D-06 push-tokens, no recreation). `setData(snapshot)` runs the pure `sanitize(snapshot, pixelWidth)` helper ONCE (drop NaN/±Infinity, uniform-stride downsample cap to pixel width) then `invalidate()` — called only on a new throttled (~2-4 Hz) sample (D-13). `GraphViewHost` wraps it in `AndroidView(factory once, update={applyTokens(tokens); setData(snapshot)})` so the caller's collected tokens recolor the Canvas on a theme flip with no view recreation.

### Input-edge contract (pinned here; later panels inherit)
- empty snapshot → draws nothing (early return, no crash)
- 1-point → a single centered dot (no line needing ≥2 points)
- constant-value (zero-range) series → flat line centered; the value→y mapping guards a zero denominator (`range <= 0f` → center fallback), no divide-by-zero
- NaN/Infinity → filtered in `sanitize` BEFORE the draw path sees them (`onDraw` only ever sees finite values)
- downsample cap → rendered vertices never exceed the horizontal pixel width; the bounded finite copy is produced ONCE per throttled sample (UI-thread copy bounded, `onDraw` allocation-free)

## How to Verify

- `cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon"` — compiles (ProgressRing/GraphView/GraphViewHost). PASSED.
- `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests *GraphDownsampleTest --no-daemon"` — 8 cases green: cap to pixel width (endpoints preserved), within-budget intact, NaN/±Infinity filtered, all-non-finite→empty, empty→empty, non-positive width→empty, constant series intact + constant-over-budget capped (no NaN). PASSED.
- Token-purity: `grep -rEn 'Color\(0[xX]' app/src/main/java/works/mees/dinghy/render/ | grep -v '^#'` → zero matches. PASSED.
- `grep -nE 'animate.*AsState|rememberInfiniteTransition' ProgressRing.kt` → only KDoc lines documenting their absence (no code). D-13 clean.

## Deviations from Plan

None — plan executed exactly as written. Both tasks landed their named artifacts and acceptance criteria; the plan explicitly anticipated extracting `sanitize` as a pure function for host-testability (done).

## On-Device / Deferred Gates (not in this plan's scope)

Per the plan's acceptance criteria and 03-VALIDATION.md, the cross-toolkit recolor proof (a dark→light/custom flip recoloring the Views Canvas graph) and the no-jank perf proof (gfxinfo framestats at ~2-4 Hz on the real flox) are Manual/on-device gates carried by the gallery + gfxinfo plans (03-06 / 03-07). This plan delivers the compiling primitives + host-side cap/filter proof; the on-device gates run later.

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/render/ProgressRing.kt
- FOUND: app/src/main/java/works/mees/dinghy/render/GraphView.kt
- FOUND: app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt
- FOUND: app/src/test/java/works/mees/dinghy/render/GraphDownsampleTest.kt
- FOUND commit: 19c3dfa (ProgressRing)
- FOUND commit: 6067cda (GraphView + host + test)
