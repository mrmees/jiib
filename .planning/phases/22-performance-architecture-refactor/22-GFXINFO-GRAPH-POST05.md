# 22 — Temperature Graph gfxinfo, POST Plan-05 D-12 guard (pre fill-change)

The isolated before-anchor for Plan 06's fill change. Measures the Temperature multi-trace graph
on flox AFTER the Plan-05 D-12 equality guard merged, BEFORE any GraphView fill-overdraw change —
so the fill change's contribution is isolated from the D-12 guard's.

- **Build:** release (R8, debug-signed) at the post-22-04 commit (D-12 guard from 22-05 present;
  22-04 did not touch GraphView). Device flox (E3 Pro, dark/landscape, --fs M).
- **State:** printer idle/cooling, both traces live (nozzle ~31.8°C blue, bed ~31.5°C red) with the
  overlapping gradient area fills (the overdraw target).
- **Capture:** 3 × ~10 s windows, `parse_framestats.py --warmup 5` (4 Hz redraw).

## Numbers

| Measurement | frames | p50 | p90 | p95 | max | frozen >700ms |
|---|---:|---:|---:|---:|---:|:---:|
| Plan-01 baseline (Temperature) | 84 | 55.0 | 65.8 | 69.5 | 76.4 | **0** |
| **POST Plan-05 D-12 (this)** | 72 | **48.5** | **58.8** | **60.6** | 68.4 | **0** |
| Δ (D-12 guard alone) | | −6.5 (−12%) | −7.0 | −8.9 (−13%) | −8.0 | 0 |

## Is the gate already met by D-12 alone?

**YES.** The ADR-0001 Addendum-2 gate = **zero frozen frames (>700 ms) + responsive**. The graph had
0 frozen frames at the Plan-01 baseline and still has **0 frozen frames** post-D-12 — now with
~12–13% lower per-frame cost (the D-12 guard eliminated redundant token-driven `invalidate()`s; the
graph still redraws at 4 Hz on data updates, but no longer on spurious token re-application).

The p95 (60.6 ms) remains over the 16.6 ms/60 Hz budget — the per-trace gradient-fill overdraw still
costs — but that is a *responsiveness/headroom* margin, NOT a gate failure. **The SC3 gate does not
require the Plan-06 fill change.** Task 1 (reduce secondary-trace fills) is therefore optional
per-frame headroom, traded against a *visual* change to the graph (secondary traces go line-only or
reduced-alpha). Owner decides: proceed / minimize / skip the fill change (D-09/D-10).

## Owner decision (2026-06-08): SKIP the fill change

The owner reviewed the exact visual tradeoff — current behavior is each trace fills to the baseline
in its own translucent hue, so overlapping traces **blend** (the purplish band under the traces);
Option A would make the area a **single** primary-trace color with secondary traces as lines only.
**Owner chose to KEEP the current layered-blend look** (the designed aesthetic). The SC3 gate is
already met by the D-12 guard alone, so no GraphView fill edit is made. **Task 1 (fill change) is
SKIPPED by owner decision; Task 2 (side-by-side) is therefore N/A.** GraphView is unchanged this plan.
