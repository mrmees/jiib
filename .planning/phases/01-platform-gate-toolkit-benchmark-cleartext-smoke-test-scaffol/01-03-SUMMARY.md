---
phase: 01-platform-gate-toolkit-benchmark-cleartext-smoke-test-scaffol
plan: 03
subsystem: toolkit-benchmark-harness
tags: [benchmark, compose, views, coil, macrobenchmark, uiautomator, gfxinfo, pkg-02, d-02, d-07]
dependency_graph:
  requires:
    - "01-01 (:app + :macrobenchmark scaffold, exported BenchActivity stub, version catalog, Coil/AndroidX deps)"
  provides:
    - "SyntheticFeed — deterministic byte-identical 2-4 Hz in-process fixture shared by both scenes (D-02/D-06)"
    - "ComposeBenchScene + ViewsBenchScene — identical worst-case printer layout with REAL Coil decode at 1920x1200 (D-05/D-07)"
    - "BenchActivity scene-select wiring (intent extra mounts scene + starts feed)"
    - "ToolkitBenchmark — UiAutomator driver running both scenes identically against :app release (D-02/D-05)"
    - "parse_framestats.py — gfxinfo framestats CSV -> p50/p90/p95 + count(>700ms), the on-device system of record (D-04/D-07)"
  affects:
    - "01-04 (on-device run) — installs :app release, launches BenchActivity per scene, captures gfxinfo framestats, parses with this parser, records the ADR verdict"
tech_stack:
  added:
    - "Coil 3 custom Fetcher/Keyer (synthetic in-memory PNG source) + shared ImageLoader"
    - "androidx.benchmark MacrobenchmarkRule + FrameTimingMetric + UiAutomator drive script"
    - "Python 3 framestats CSV parser (stdlib only)"
  patterns:
    - "One deterministic feed, two render backends selected at launch — identical-workload head-to-head harness (RESEARCH Pattern 1)"
    - "gfxinfo framestats CSV as system of record; FrameTimingMetric as corroboration only (RESEARCH Pattern 2)"
    - "Immutable/stable Compose state (@Immutable snapshot), collectAsStateWithLifecycle, keyed LazyColumn items"
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/bench/SyntheticFeed.kt
    - app/src/main/java/works/mees/dinghy/bench/SyntheticThumbnail.kt
    - app/src/main/java/works/mees/dinghy/bench/ComposeBenchScene.kt
    - app/src/main/java/works/mees/dinghy/bench/ViewsBenchScene.kt
    - macrobenchmark/src/main/java/works/mees/dinghy/macrobenchmark/ToolkitBenchmark.kt
    - tools/gfxinfo-parser/parse_framestats.py
  modified:
    - app/src/main/java/works/mees/dinghy/bench/BenchActivity.kt
    - macrobenchmark/build.gradle.kts
decisions:
  - "SyntheticThumbnail generates REAL PNG bytes in-process and decodes them through a Coil 3 Fetcher/Keyer (D-07: no placeholder drawables / predecoded bitmaps). Added beyond the plan's file list because the real-decode requirement is hollow without a real source."
  - "A shared BenchImageLoader (single ImageLoader, modest memory cache) is used by BOTH scenes so cache pressure / fetch path are identical (fairness, D-02)."
  - "Added a `release` build type to :macrobenchmark so `:macrobenchmark:assembleRelease` exists — a com.android.test module declares no release variant by default (the plan's literal verify required it)."
metrics:
  duration_min: 7
  completed: 2026-05-30
  tasks: 2
  files: 8
---

# Phase 01 Plan 03: Toolkit Benchmark Harness Summary

The head-to-head Compose-vs-Views measurement apparatus: one byte-identical
deterministic synthetic feed driving two scenes that render the same worst-case
printer-screen layout (Files list + live temp graph + console) with REAL Coil PNG
decode at the 1920x1200 target, plus the UiAutomator driver that runs both scenes
identically against the `:app` release variant and the Python framestats parser that
turns the on-device gfxinfo CSV into the D-04 percentiles + frozen-frame count.

## What Was Built

- **Task 1 — Synthetic feed + both scenes.**
  - `SyntheticFeed`: a fixed-seed / fixed-schedule generator emitting ~3 Hz (333 ms,
    inside the 2-4 Hz band) Moonraker-shaped `FeedEvent`s — extruder/bed temps,
    progress, toolhead position, file-list churn (insert-at-top with eviction), rolling
    graph samples, and bounded console appends. `replay()` is a pure function of
    (seed, eventCount); `assertDeterministic()` proves two feeds replay `equals`-identically
    so fairness (D-02/T-01-07) can't silently break. `events()` adds only timing on top of
    `replay()`, so both scenes still see byte-identical events.
  - `SyntheticThumbnail` + `ThumbModel`/`ThumbKeyer`/`SyntheticThumbnailFetcher` +
    `BenchImageLoader`: a deterministic in-process 512x512 PNG source decoded through
    Coil 3's REAL decode+downsample path under a shared modest memory cache (D-07). Same
    seed -> same PNG bytes -> both scenes decode identical work.
  - `ComposeBenchScene`: `LazyColumn` (keyed items) + Coil `AsyncImage`, a live Compose
    `Canvas` temperature graph, and a console spew. Immutable `ComposeSceneState` snapshot,
    `collectAsStateWithLifecycle`, temps read low in the tree (CLAUDE.md Compose perf rules).
  - `ViewsBenchScene`: `RecyclerView` (Coil `ImageLoader.enqueue` decode of the SAME
    thumbnails via the SAME shared loader) + a custom `View` temperature graph + a
    `TextView` console — the same layout at 1920x1200, exposing `render()` so the activity
    pushes each shared-feed event.

- **Task 2 — Activity wiring + driver + parser.**
  - `BenchActivity` (the 01-01 skeleton, modified — manifest untouched): reads the `scene`
    intent extra, runs full-screen + keep-screen-on at the device's real resolution, mounts
    the chosen scene, and drives it from the shared `SyntheticFeed` with rolling
    graph/console windows. `assertDeterministic()` runs at entry.
  - `ToolkitBenchmark` (`:macrobenchmark`): two `@Test`s (`composeScene`, `viewsScene`)
    calling the SAME `driveScene()` (same dwell, same scroll count/distance, same settle) —
    the only difference is the scene-select extra. `FrameTimingMetric` is configured as
    CORROBORATION ONLY, with an explicit comment that `frameOverrunMs` needs API 31+ (absent
    on the API-23 target) and that the gfxinfo framestats CSV is the SYSTEM OF RECORD. No
    Baseline-Profile generator (no-op on API 23, D-03).
  - `parse_framestats.py`: parses `dumpsys gfxinfo <pkg> framestats` CSV, computes per-frame
    total = (FrameCompleted - IntendedVsync), emits p50/p90/p95 + count(frames>700ms),
    excludes a warmup window, dedups the ~120-frame ring buffer by IntendedVsync across
    concatenated dumps, and drops platform-flagged (non-zero Flags) frames.

## Verification Evidence

| Check | Result |
|-------|--------|
| `grep SyntheticFeed` in both scenes | both reference it; both consume `FeedEvent`/`GraphSample`/`GcodeFile` + shared `BenchImageLoader` |
| `:app:compileReleaseKotlin` (Task 1 verify, via gw.bat) | **EXIT 0** — BUILD SUCCESSFUL (real Coil 3 API usage confirmed) |
| `grep FrameTimingMetric` in ToolkitBenchmark | present (configured as corroboration; documented SoR + no baseline-profile gate) |
| `grep ComposeBenchScene\|ViewsBenchScene` in BenchActivity | present (both mounted via scene select) |
| `grep -i framestats` in parser | present |
| `:macrobenchmark:assembleRelease` (Task 2 verify, via gw.bat) | **EXIT 0** — BUILD SUCCESSFUL (`compileReleaseKotlin` + packaging) |
| parser self-test (synthetic dump) | correct: 31 deduped frames (ring-buffer overlap + flagged rows removed), 5 warmup excluded, p50/p90/p95 computed, exactly 1 frame > 700 ms detected |

> No on-device run here — that is plan 01-04. This plan delivers the apparatus and proves
> it BUILDS against the release variant; the actual gfxinfo capture + ADR verdict are 01-04.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] Added `SyntheticThumbnail.kt` (real PNG source for Coil)**
- **Found during:** Task 1.
- **Issue:** D-07 mandates REAL Coil decode+downsample (no placeholder drawables / no
  predecoded bitmaps). The plan's `files_modified` list named the two scenes but no PNG
  source — and Coil cannot do a real decode without real bytes. A placeholder would make
  the "worst-case" / "real decode" claim hollow and invalidate the benchmark.
- **Fix:** Added `SyntheticThumbnail` (deterministic in-memory 512x512 PNG encoder) plus a
  Coil 3 `Fetcher`/`Keyer` and a shared `BenchImageLoader`, so both scenes decode the SAME
  real PNG bytes through Coil's real path under shared memory-cache pressure.
- **Files modified:** `app/src/main/java/works/mees/dinghy/bench/SyntheticThumbnail.kt` (new)
- **Commit:** fc2c9c8

**2. [Rule 3 - Blocking] Added a `release` build type to `:macrobenchmark`**
- **Found during:** Task 2 verify (`:macrobenchmark:assembleRelease`).
- **Issue:** The plan's verify command runs `:macrobenchmark:assembleRelease`, but a
  `com.android.test` module declares NO `release` build type by default — 01-01 only
  created a `benchmark` type (with `matchingFallbacks += "release"`). So `assembleRelease`
  did not exist ("task 'assembleRelease' not found in project ':macrobenchmark'").
- **Fix:** Added a minimal `release` build type (debuggable, debug-signed, same
  `matchingFallbacks += "release"`) so `assembleRelease` exists and compiles
  `ToolkitBenchmark` against `:app`'s release variant. The existing `benchmark` variant is
  untouched. Debug-signed because the Phase-1 `:app` release APK is still unsigned (real
  keystore signing is PKG-01 / Phase 8).
- **Files modified:** `macrobenchmark/build.gradle.kts`
- **Commit:** 030c0bf

## Threat Register Coverage

- **T-01-07 (Tampering — benchmark fairness, mitigate):** addressed by the single
  deterministic `SyntheticFeed` consumed by both scenes, `assertDeterministic()` proving
  byte-identical replay, the identical `driveScene()` UiAutomator sequence, and the shared
  `BenchImageLoader` (identical decode path/cache pressure).
- **T-01-08 (Repudiation — measurement provenance, mitigate):** addressed by making
  `parse_framestats.py` over the raw gfxinfo framestats CSV the system of record (with
  `FrameTimingMetric` documented as corroboration only); raw captures are retained in 01-04.
- **T-01-04 (cleartext, accept):** owned by 01-01; this plan uses no network (local
  synthetic feed only).

## Known Stubs

None. The harness is complete and builds against the release variant. The remaining
"unfinished" surface is intentional and scheduled: the actual on-device gfxinfo capture,
the recorded percentiles, and the ADR verdict are plan 01-04's deliverables (this plan is
the apparatus, per its own objective: "NO on-device run here").

## Notes for Downstream (01-04)

- Launch per scene:
  `adb shell am start -n works.mees.dinghy/.bench.BenchActivity --es scene compose`
  (and `--es scene views`).
- Capture loop: `adb shell dumpsys gfxinfo works.mees.dinghy reset` before the window;
  run the scripted scene; `adb shell dumpsys gfxinfo works.mees.dinghy framestats`
  repeatedly (beat the ~120-frame ring buffer); pipe the concatenated dumps into
  `python3 tools/gfxinfo-parser/parse_framestats.py -`.
- `:app` release APK is still **unsigned** (PKG-01 / Phase 8) — 01-04 installs a
  locally/debug-signed variant for the on-device run.
- The benchmark can also be run via `:macrobenchmark:connectedReleaseAndroidTest` for the
  `FrameTimingMetric` corroboration numbers (gfxinfo CSV remains the verdict).

## Self-Check: PASSED

All six created files and both modified files verified present; both task commits
(fc2c9c8, 030c0bf) verified in git history.
