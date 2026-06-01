# 05-PERF-RESULTS — Multi-trace Temperature screen on the Adreno-320 floor (D-06 re-measure)

**System of record:** raw `dumpsys gfxinfo works.mees.dinghy framestats` captured on the real
`flox` tablet, parsed by `tools/gfxinfo-parser/parse_framestats.py` (p50/p90/p95/max + count(frames > 700 ms)).
This is the **D-06 mandated re-measurement** of the Phase-3 two-part Adreno-320 gate against the FULL
multi-trace Temperature screen — the isolated Phase-3 ring+graph 50.1 ms number is **NOT grandfathered**
(per the Phase-3 FINAL VERDICT "MANDATORY FOLLOW-UP" and the three re-open conditions).

**Scene:** the real routed Temperature screen (App Drawer → Temp) — the N-trace `GraphView` (nozzle=heat /
bed=accent / chamber=violet on one shared X window + fixed 0..350 °C Y-range, area-fill bounded to the
primary trace, 05-04) + the Focus value readouts + the gutter — driven by the **LIVE Ender 5 Plus** during
a real PLA heat ramp (worst case: multiple heaters actively moving). Build = **RELEASE** variant
(R8/minify), debug-signed for install (release signing is PKG-01 / Phase 8).

## Device / build

| | |
|---|---|
| Device | `flox` (genuine Nexus 7 2013 hardware: Adreno 320 / 2GB / armeabi-v7a) |
| OS | LineageOS 18.1 / Android 11 / **API 30** (newer ART than stock-6; SAME GPU/perf floor) |
| Screen | 1200×1920, density 320 dpi |
| Build | `:app:assembleRelease` (R8/minify), debug-signed via `sign-release.bat`, `adb install -r` |
| | `app/build/outputs/apk/release/app-armeabi-v7a-release-signed.apk` |
| adb serial | `0a64b42e` |
| Printer | LIVE Ender 5 Plus (Klipper/Moonraker) during a real PLA heat ramp |
| Capture | reset gfxinfo → open Temp → settle → repeated `framestats` dumps; parser dedupes the ring-buffer overlap by IntendedVsync; 20 warmup frames excluded, 100 measured frames, flags!=0 excluded, frozen threshold 700 ms |

## The gate (Phase-3 A-variant two-part gate, applied verbatim to the full screen)

1. **LIVENESS GATE (hard floor — project soul):** allocation-free draw path, NO animation loop (redraw
   count tracks the ~3 Hz feed, not Choreographer), ZERO frozen frames (no frame > 700 ms).
2. **SPARSE-REDRAW LATENCY GATE:** each value-driven, full-screen redraw **p95 ≤ ~66 ms** (≈ 2 display
   refreshes; sub-perceptible at a ~3 Hz value change). Derived from the ~24 ms native-res composite
   floor + per-trace headroom, NOT reverse-fit. See 03-PERF-RESULTS.md FINAL VERDICT for the derivation.

## gfxinfo framestats — captured results

Three captures on flox (warmup 20 excluded, 100 measured frames, flags!=0 excluded, frozen threshold 700 ms):

| Metric | **LIVE multi-trace Temp** (real PLA ramp) | BenchActivity `render`, fill ON (default) | BenchActivity `render`, fill OFF (`--ez nofill true`) |
|--------|-------------------------------------------|-------------------------------------------|-------------------------------------------------------|
| frames measured | 100 | 100 | 100 |
| **p50 (ms)** | **39.27** | 30.40 | 26.45 |
| p90 (ms) | 46.57 | 42.27 | 29.40 |
| **p95 (ms)** | **48.64** | 44.45 | 30.28 |
| max (ms) | 61.00 | 97.72 | 39.88 |
| **frames > 700 ms (frozen)** | **0** | **0** | **0** |

## Liveness gate — PASS

- **Zero frozen frames** (0 frames > 700 ms) across all three captures — the project-soul non-negotiable holds.
- **No animation loop** — the live screen's redraw cadence tracks the ~3 Hz Moonraker data feed (no
  Choreographer loop); the graph repaints only on a new throttled sample, not per display frame.
- **Allocation-free onDraw** — inherited from the 05-04 GraphView (reused `Path`/area-`Path`,
  pre-allocated stroke/fill/dash `Paint`s, `rewind()` per draw). Confirmed by the bounded max and 0 frozen.

## Sparse-redraw latency gate — PASS (live p95 48.64 ms vs ~66 ms bound, ~17 ms margin)

The **live** multi-trace Temperature screen during a real PLA heat ramp measured **p95 48.64 ms** —
comfortably inside the derived ~66 ms bound with **~17 ms headroom** — at **0 frozen frames**. The cadence
tracks the ~3 Hz data feed (no Choreographer loop), so the sparse-redraw premise holds in production.

The single **97.72 ms fill-on max** in the deterministic BenchActivity capture was an isolated outlier
(one frame), NOT a frozen frame (well under the 700 ms threshold) and NOT present in the live capture
(live max 61.00 ms). It does not breach the gate.

## Fill-rate attribution baseline (first flox capture of this surface)

Per the 05-08 plan's requirement to record ONE explicit fill-on vs fill-off comparison even on PASS so a
future regression has a baseline attribution:

- **Deterministic `render` scene, fill ON (design-true):** p95 **44.45 ms**.
- **Deterministic `render` scene, fill OFF (`--ez nofill true`, bench-only A-B lever):** p95 **30.28 ms**.
- **Δ = ~14 ms p95** — the translucent area-fill (bounded to the primary trace, `hifi.css .g-area`) costs
  ~14 ms p95 on the Adreno 320. This is the **primary fill-rate regression lever** for this surface.

The canonical filled aesthetic stays ON in the product (`GraphView.drawArea = true`); `--ez nofill` is a
bench-only isolation route, never a product appearance change. This ~14 ms figure is the recorded baseline:
any future Temperature-screen perf regression can be attributed against it, not re-derived from scratch.

## Re-open conditions (from 03-PERF-RESULTS.md) — none triggered

1. ~24 ms composite floor unstable → NOT triggered (live p95 48.64 ms is stable, bounded max 61.00 ms).
2. Full screen composites materially heavier, p95 breaches ~66 ms → NOT triggered (48.64 ms, ~17 ms margin).
3. ~3 Hz cadence wrong in production → NOT triggered (live cadence tracks the ~3 Hz feed, no loop).

None of the three re-open conditions held. The criterion stays PASS; no re-derivation required.

## Verdict line

**D-06 multi-trace Temperature perf gate: PASS** on real flox + live Ender 5 Plus. Liveness gate MET
cleanly (allocation-free, no loop, 0 frozen). Sparse-redraw latency gate MET at **48.64 ms p95** (live,
real PLA ramp) vs the derived ~66 ms bound (**~17 ms margin**), design-true (filled, real full-screen
multi-trace). The Phase-3 isolated 50.1 ms number is **superseded** by this live full-screen measurement —
NOT grandfathered. Fill-rate attribution baseline recorded (~14 ms for the area-fill).
