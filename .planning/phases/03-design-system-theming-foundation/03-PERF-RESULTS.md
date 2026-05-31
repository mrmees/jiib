# 03-PERF-RESULTS — Ring + Line-Graph on the Adreno-320 floor (criterion #5 / D-10)

**System of record:** raw `dumpsys gfxinfo works.mees.dinghy framestats` CSV captured on the real
`flox` tablet, parsed by `tools/gfxinfo-parser/parse_framestats.py` (p50/p90/p95 + count(frames > 700 ms)).
`FrameTimingMetric` (macrobenchmark `RenderBenchmark.kt`) is corroboration only (ADR 0001).

**Scene:** `BenchActivity --es scene render` → `RenderBenchScene` — the two shared render primitives
(Compose `ProgressRing` + classic-Views `GraphView` via `GraphViewHost`) driven by the deterministic
`SyntheticFeed` (~3 Hz) filling a `RingBuffer` (cap 120). Build = **RELEASE** variant, debug-signed for
install (release signing is PKG-01 / Phase 8). No scrolling — value-driven redraw only (D-13).

## Device / build

| | |
|---|---|
| Device | `flox` (genuine Nexus 7 2013 hardware: Adreno 320 / 2GB / armeabi-v7a) |
| OS | LineageOS 18.1 / Android 11 / **API 30** (newer ART than stock-6; SAME GPU/perf floor) |
| Screen | 1200×1920, density 320 dpi (each `weight(1f)` primitive ≈ 1200×900 px) |
| Build | `:app:assembleRelease` (R8/minify), debug-signed via `sign-release.bat`, `adb install -r` |
| adb serial | `0a64b42e` |
| Capture | reset gfxinfo → launch render scene → settle → repeated `framestats` dumps over ~30–40 s; parser dedupes the ~120-frame ring-buffer overlap by IntendedVsync |

## Redraw cadence (D-13 — no animation loop) — PASS

`Total frames rendered: 16` over a clean 5 s window ≈ **3.2 Hz**. At 60 fps a looping animation would
have produced ~300 frames. The render primitives redraw ONLY on a new throttled feed sample, never per
Choreographer frame — D-13 holds (static glow, value-driven redraw). Confirmed by eye: no continuous
animation; the ring fills with progress and the graph scrolls only as samples arrive.

## gfxinfo framestats — captured results

Two independent captures (warmup-excluded, flags!=0 frames excluded, frozen threshold 700 ms):

| Metric | Run 1 (warmup 20) | Run 2 (warmup 10) | ADR-0001 budget |
|--------|-------------------|-------------------|-----------------|
| frames measured | 106 | 83 | — |
| **p50 (ms)** | **44.35** | **45.28** | **≪ 16.6** |
| p90 (ms) | 58.55 | 55.52 | — |
| **p95 (ms)** | **61.27** | **56.58** | **~42 (Views-graph tolerance)** |
| max (ms) | 109.61 | 59.77 | — |
| **frames > 700 ms (frozen)** | **0** | **0** | **0** |

Histogram (Run 1) clusters heavily at 40–57 ms (peak: 44 ms=24, 53 ms=13), tailing to ~85 ms; nothing
beyond 110 ms.

## Verdict — FAIL against the stated p50/p95 budget; PASS on frozen-frames + cadence

- ✅ **Zero frozen frames** (0 frames > 700 ms across both runs) — no catastrophic stalls.
- ✅ **No animation loop** — ~3.2 Hz value-driven redraw (D-13).
- ✅ **Recolor on theme flip** — `GraphViewHost.update` pushes tokens to the same `GraphView` instance
  (verified in the gallery, plan 03-06); the ring reads `LocalTokens`.
- ❌ **p50 ≈ 45 ms badly misses the ADR-0001 floor (≪ 16.6 ms)** — every individual redraw overruns the
  16.6 ms vsync deadline.
- ❌ **p95 ≈ 56–61 ms exceeds the ~42 ms Views-graph tolerance.**

### Analysis — almost certainly fill-rate (threat T-03-08), driven by primitive SIZE

The Phase-1 floor of p95 ≈ 41.9 ms was measured on a `dp(260)`-tall graph. In this scene each primitive
is laid out at `weight(1f)` of a portrait screen — the `GraphView` covers ≈ **1200×900 px** and draws a
**translucent area-fill** (`drawPath(areaPath, fillPaint)`) under the line across that whole region.
On the Adreno-320 the project's stated bottleneck is **fill rate**; a near-full-screen translucent fill
plus the line, re-issued each ~3 Hz sample, is the prime suspect for the 40–57 ms cluster. This is
exactly the DoS/jank threat T-03-08 the gate exists to surface. The draw is allocation-free (Pitfall 4
holds — 0 frozen frames, bounded max), so this is a fill-cost problem, not a GC-tail problem.

The redraw cost is borne ~3×/second (≈13% duty cycle; the screen is otherwise idle), so it is **not a
continuously-janky surface** — but each update frame does miss vsync, and the numbers do not meet the
budget as written. This needs a human verdict (blocking-human gate):

1. **Accept** — at ~3 Hz a 45 ms redraw is acceptable for a printer appliance screen (no animation, no
   scroll; the frame budget that matters is "does an update look instant", and 45 ms is sub-perceptible
   for a 3 Hz value change). Re-baseline criterion #5's budget for a FULL-SIZE graph (the ~42 ms floor
   was a small graph) and sign off.
2. **Optimize then re-measure** — the fill is the likely culprit. Cheapest levers: drop/lighten the
   translucent area-fill on the floor device, cap the graph to a realistic panel height rather than half
   the screen, or clip the fill. Re-run this same harness. (Would be a Rule-1 perf fix to `GraphView`
   / the scene, but it changes the visual contract, so it is a human call, not an auto-fix.)

Recommendation: option 1 (accept + re-baseline) for the Phase-3 gate, with a noted follow-up to profile
the area-fill cost in Phase 4 when the graph lands at its real panel size — but Matthew owns this call.
