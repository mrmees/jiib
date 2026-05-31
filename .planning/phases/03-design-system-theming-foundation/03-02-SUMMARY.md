---
phase: 03-design-system-theming-foundation
plan: 02
subsystem: theming-render-foundation
tags: [fonts, typography, ring-buffer, render-substrate, api23]
requires:
  - "03-01 headless theme core (sibling foundation; this plan is independent of it)"
provides:
  - "Geist + GeistMono Compose FontFamily over static res/font TTFs (THEME-02 type substrate)"
  - "Bounded plain-Kotlin RingBuffer holder (D-12) — toolkit-agnostic rolling sample window"
affects:
  - "Later theme/typography wiring consumes Geist/GeistMono"
  - "Both render primitives (live temp graph, console sparkline) consume RingBuffer"
tech-stack:
  added:
    - "Geist + Geist Mono static TTFs (SIL OFL 1.1, Vercel) as res/font assets — no Gradle dep"
  patterns:
    - "Static-weight FontFamily per RESEARCH Pattern 2 (no variable fonts on API-23 floor)"
    - "Monospace = tabular by construction (no fontFeatureSettings/tnum)"
    - "Fixed FloatArray ring with @Synchronized push/snapshot; defensive-copy snapshot (Pitfall 4)"
key-files:
  created:
    - "app/src/main/res/font/geist_regular.ttf (400)"
    - "app/src/main/res/font/geist_medium.ttf (500)"
    - "app/src/main/res/font/geist_semibold.ttf (600)"
    - "app/src/main/res/font/geist_bold.ttf (700)"
    - "app/src/main/res/font/geist_mono_medium.ttf (500)"
    - "app/src/main/res/font/geist_mono_semibold.ttf (600)"
    - "app/src/main/java/works/mees/dinghy/theme/Geist.kt"
    - "app/src/main/java/works/mees/dinghy/render/RingBuffer.kt"
    - "app/src/test/java/works/mees/dinghy/render/RingBufferHolderTest.kt"
  modified: []
decisions:
  - "Sourced static TTFs from Fontsource latin subsets (~27-31KB each, ~175KB for all 6) rather than full Vercel TTFs — Latin-only is sufficient for this Latin/numeric UI and keeps the APK lean (RESEARCH allows trimming for size). Same OFL 1.1 Geist/Geist Mono faces; all 6 validated as genuine TrueType."
  - "RingBuffer push is O(1) over a fixed-size FloatArray ring (head+count modular indexing), no per-push allocation — only snapshot() allocates, matching Pitfall 4 (per-frame draw alloc avoided on the draw side; the buffer hands a clean copy)."
metrics:
  duration_min: 9
  completed: 2026-05-31
  tasks: 2
  files: 9
---

# Phase 3 Plan 02: Typography Substrate & Bounded RingBuffer Summary

Bundled the six Geist / Geist Mono static-weight TTFs as `res/font` assets and exposed them as
Compose `Geist`/`GeistMono` FontFamilies (THEME-02), and landed the plain-Kotlin bounded `RingBuffer`
holder (D-12) — the toolkit-agnostic rolling sample window both render primitives will draw — with a
TDD-driven unit test covering capacity, eviction, snapshot stability, and concurrent safety.

## What Was Built

### Task 1 — Geist static TTFs + FontFamily (commit `3d761a4`)
- Six static-weight TTFs under `app/src/main/res/font/` with the required lowercase_underscore names:
  `geist_regular` (400), `geist_medium` (500), `geist_semibold` (600), `geist_bold` (700),
  `geist_mono_medium` (500), `geist_mono_semibold` (600). All validated as genuine TrueType
  (`file` reports "TrueType Font data, 15 tables"). Geist + Geist Mono, SIL OFL 1.1, first-party Vercel.
- `theme/Geist.kt` exposes `val Geist: FontFamily` and `val GeistMono: FontFamily` mapping each weight
  to its `R.font.geist_*` file via `Font(R.font.geist_*, FontWeight.*)`.
- **Static weights only** — no variable `Geist[wght].ttf` (would collapse to one weight on API < 26;
  floor is API 23). **No `fontFeatureSettings`** anywhere in code — Geist Mono is monospace so live
  numerics are tabular by construction (RESEARCH A2 / Pattern 2 / Pitfall 2). The only mentions of
  "fontFeatureSettings"/"variable" in the file are KDoc guardrails explicitly forbidding their use.
- Verified by `:app:assembleDebug` compiling green — the `R.font.geist_*` references resolve.

### Task 2 — Bounded RingBuffer holder (TDD; `test` `3b24d3d` → `feat` `1e3a4e8`)
- `render/RingBuffer.kt`: a headless `class RingBuffer(val capacity: Int = 120)` — NO Compose imports
  (PrinterState discipline). `push(Float)` appends and evicts the oldest past capacity (O(1), fixed
  `FloatArray` ring, no per-push allocation); `size`; `snapshot(): FloatArray` returns a stable
  defensive copy oldest→newest; `clear()`. `push`/`size`/`snapshot` are `@Synchronized` for the
  one-coroutine-write / main-thread-read split. Default capacity 120 = bench `GRAPH_MAX` precedent.
- `RingBufferHolderTest.kt` (mirrors `ConflationTest`'s JUnit4 idiom) covers the full behavior block:
  capacity bound + oldest-eviction (200 pushes → size 120, first retained = 81st pushed value),
  snapshot defensive-copy stability under later pushes, latest-`capacity` insertion order, empty and
  below-capacity cases, and a 4-thread concurrent push/snapshot stress (never throws, never returns an
  over-capacity/torn array).
- TDD gate honored: RED commit (test fails to compile — no `push`/`snapshot`) → GREEN commit (all
  tests pass, `:app:testDebugUnitTest --tests *RingBufferHolderTest` exits 0). No refactor needed.

## Verification

| Check | Command | Result |
|-------|---------|--------|
| Fonts resolve / app compiles | `:app:assembleDebug --no-daemon` | BUILD SUCCESSFUL, exit 0 |
| RingBuffer behavior | `:app:testDebugUnitTest --tests *RingBufferHolderTest --no-daemon` | BUILD SUCCESSFUL, exit 0 |

On-device tabular-digit alignment is a Manual-Only check (03-VALIDATION.md) deferred to the gallery on
flox — out of scope for this autonomous plan.

## Deviations from Plan

### Decisions (not auto-fixes)

**1. Fonts sourced from Fontsource Latin subsets rather than full Vercel TTFs**
- The plan permits either Vercel `geist-font` or Fontsource as the source and explicitly allows trimming
  for APK size. I pulled the Latin-subset static TTFs from the Fontsource CDN (~27–31KB each, ~175KB for
  all six) instead of the full multi-script Vercel TTFs. Latin glyphs + digits fully cover this UI's
  needs, and the smaller assets suit the armeabi-v7a-only APK. Same OFL 1.1 Geist / Geist Mono faces;
  all six verified as genuine TrueType. No functional difference for the app's text/numerics.

No code-behavior deviations. No Rule 1–4 auto-fixes were needed; both tasks executed as written.

## TDD Gate Compliance

- RED gate: `test(03-02): add failing test for bounded RingBuffer holder` (`3b24d3d`) — failed before impl.
- GREEN gate: `feat(03-02): implement bounded RingBuffer holder (D-12)` (`1e3a4e8`) — tests pass.
- REFACTOR: none required (implementation landed clean).

## Known Stubs

None. Both artifacts are complete and used-as-built by later plans; no placeholders, empty values, or
TODOs introduced.

## Self-Check: PASSED
- FOUND: app/src/main/java/works/mees/dinghy/theme/Geist.kt
- FOUND: app/src/main/java/works/mees/dinghy/render/RingBuffer.kt
- FOUND: app/src/test/java/works/mees/dinghy/render/RingBufferHolderTest.kt
- FOUND: all six app/src/main/res/font/geist_*.ttf
- FOUND commit 3d761a4 (fonts + FontFamily)
- FOUND commit 3b24d3d (RED test)
- FOUND commit 1e3a4e8 (GREEN impl)
