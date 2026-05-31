---
phase: 03-design-system-theming-foundation
plan: 07
subsystem: ui
tags: [perf, gfxinfo, macrobenchmark, adreno-320, render-primitives, jetpack-compose, custom-canvas, criterion-5]

# Dependency graph
requires:
  - phase: 03-05
    provides: shared render primitives — ProgressRing (Compose Canvas) + GraphView (Views Canvas) + GraphViewHost (AndroidView interop) + RingBuffer
  - phase: 03-06
    provides: in-APK gallery + debug-only launcher (the surface used to eyeball static-glow / theme-flip recolor)
  - phase: 01-04
    provides: the Phase-1 gfxinfo methodology — SyntheticFeed, BenchActivity scene-routing, :macrobenchmark module, tools/gfxinfo-parser/parse_framestats.py
provides:
  - "On-device criterion-#5 proof: ring+graph render scene driven by the deterministic ~3 Hz SyntheticFeed, captured via dumpsys gfxinfo framestats on real flox"
  - "The A-variant two-part acceptance gate for sparse value-driven redraws on the Adreno-320 floor (liveness gate + sparse-redraw latency p95 <= ~66 ms)"
  - "An empirical attribution: graph SIZE (over-sized region), not the .16-opacity area-fill, was the dominant fill-rate cost; the ~24 ms cost is the native-res window-composite floor (physics)"
  - "A dated re-scope reconciling the Phase-1 16.6 ms / 42 ms floors that were mis-applied as criterion #5 acceptance"
affects: [phase-06-temperature, phase-04-print-status, render-primitives, perf-budget]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Sparse-redraw latency budget (NOT a 60 fps frame budget) for ~2-4 Hz value-driven appliance surfaces on the floor device"
    - "Fill-rate isolation lever: a debug/bench-only drawArea flag (--ez nofill) to A-B attribute composite cost without changing product appearance"
    - "Per-stage framestats decomposition to separate CPU draw-record from the GPU/composite floor"

key-files:
  created:
    - .planning/phases/03-design-system-theming-foundation/03-PERF-RESULTS.md
    - app/src/main/java/works/mees/dinghy/bench/RenderBenchScene.kt
    - macrobenchmark/src/main/java/works/mees/dinghy/macrobenchmark/RenderBenchmark.kt
  modified:
    - app/src/main/java/works/mees/dinghy/render/GraphView.kt
    - app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt
    - app/src/main/java/works/mees/dinghy/bench/BenchActivity.kt
    - app/src/main/AndroidManifest.xml
    - docs/adr/0001-ui-toolkit-decision.md
    - .planning/phases/03-design-system-theming-foundation/03-VALIDATION.md

key-decisions:
  - "Criterion #5 closed PASS on the A-variant TWO-PART gate (liveness + sparse-redraw latency p95 <= ~66 ms), NOT the original p50 << 16.6 ms single threshold"
  - "The original p50 << 16.6 ms target came from a tiny dp(260) Phase-1 spike and was INVALID generalized to a full-screen redraw — missing one 16.6 ms vsync is structurally unavoidable for full-window composition on Adreno 320"
  - "Stop calling 16.6 ms a 'frame budget' — it is a SPARSE-REDRAW latency budget on the floor device; the 60 Hz mental model does not apply at ~3 Hz"
  - "Keep the canonical .16-opacity area-fill ON in the product (fill cost is only ~5.6 ms p95); SIZE was the real culprit — fixed by laying the graph out at its true Field-panel footprint"
  - "Phase 6 (Temperature, multi-trace extension of this graph) MUST re-measure the full real screen against this same two-part gate; three named conditions force a re-open"

patterns-established:
  - "Sparse-redraw latency gate: appliance surfaces redrawing at the conflated 2-4 Hz state cadence are judged on frozen-frames + does-an-update-read-instant, not 60 fps janky-frame %"
  - "Derived (not reverse-fit) perf budgets: anchor the bound to the measured hardware composite floor + realistic headroom, then state the derivation"

requirements-completed: [UI-01]

# Metrics
duration: 1 day (two on-device capture rounds + human verdict)
completed: 2026-05-31
---

# Phase 3 Plan 07: gfxinfo Perf Proof — Ring+Graph on the Adreno-320 Floor (Criterion #5) Summary

**On-device gfxinfo proof that the shared render primitives draw the bounded buffer at ~3 Hz on real flox without jank — closed PASS on a derived two-part gate (allocation-free / no-loop / zero-frozen liveness + sparse-redraw p95 ≤ ~66 ms, met at 50.1 ms) after the A-B isolated graph SIZE, not the area-fill, as the Round-1 fill-rate culprit.**

## Performance

- **Duration:** ~1 day (two on-device capture rounds + Codex second opinion + human verdict)
- **Completed:** 2026-05-31
- **Tasks:** 2 (Task 1 render scene + driver; Task 2 on-device capture → human verdict, this continuation)
- **Files modified:** 6 (+ 1 perf-results doc as system of record)

## Accomplishments

- **Criterion #5 CLOSED — PASS** under the A-variant two-part gate. This was the phase's hardest gate (the reason the toolkit split D-11 was settled here): the render primitives every later live surface inherits provably draw without jank on the Adreno-320 floor.
- **Built the D-10 perf scene** (`RenderBenchScene`) reusing the Phase-1 harness verbatim — the deterministic ~3 Hz `SyntheticFeed` fills a `RingBuffer` driving BOTH the Compose `ProgressRing` and the Views `GraphView` (via `GraphViewHost`), plus the `RenderBenchmark` macrobenchmark driver. No new perf rig.
- **Two on-device capture rounds on real flox** (Adreno 320 / 2GB / 1920×1200, LineageOS 18.1 / API 30), release build, debug-signed, gfxinfo framestats parsed by the Phase-1 parser as the system of record.
- **Optimization finding via A-B:** Round 1 (over-sized `weight(1f)` half-screen graph) hit p95 ~61 ms / max ~110 ms and "failed" the literal Phase-1 budget. Round 2 corrected the layout to the canonical Focus/Field/Gutter ≈ 40/40/20 Field-panel footprint → p95 dropped to **50.1 ms**, tail collapsed (max 110 → 54 ms). Dropping the area-fill entirely shaved only ~5.6 ms more, proving **SIZE was the prime culprit, not the aesthetic** — so the canonical `.16`-opacity fill stays ON in the product.
- **Per-stage decomposition** showed ~24 ms of every frame is the unavoidable GPU/composite floor for the native 1200×1920 window on Adreno 320 (even line-only no-fill frames pay it) — physics, not a code defect. CPU draw-record is only ~3.7 ms and allocation-free (Pitfall 4 holds: 0 frozen frames across all captures).
- **Reconciled the budget honestly:** the original `p50 ≪ 16.6 ms` target (a Phase-1 `dp(260)` spike) was invalid generalized to a full-screen redraw. Recorded the FINAL VERDICT in 03-PERF-RESULTS.md, appended a dated re-scope Addendum to ADR-0001 (append-only, toolkit decision untouched), and added a dated pointer note in 03-VALIDATION.md.
- **Mandated Phase-6 re-validation** (Temperature panel extends this exact graph with multiple heater traces + stat grid + gutter — composites materially more) against the same two-part gate, with three named re-open conditions.

## Task Commits

1. **Task 1: RenderBenchScene + bench route + RenderBenchmark driver** — `9b77484` (feat) — ring+graph perf scene + macrobenchmark driver
2. **(Round-1 capture, recorded)** — `8b81d41` (docs) — on-device gfxinfo capture for ring+graph scene
3. **(Optimize lever)** — `8b65ad9` (perf) — make GraphView area-fill toggleable to isolate fill-rate cost
4. **(Scene size fix + A-B route)** — `768b488` (perf) — size render-scene graph to real Field panel + nofill route
5. **(Round-2 capture, recorded)** — `818e468` (docs) — Round-2 optimize-then-re-measure perf capture on flox
6. **Task 2 verdict + reconciliation** — `d23805e` (docs) — criterion-#5 FINAL VERDICT (A-variant two-part gate) + ADR/validation reconcile

**Plan metadata:** (this SUMMARY + STATE/ROADMAP/REQUIREMENTS) — committed separately.

## Files Created/Modified

- `.planning/phases/.../03-PERF-RESULTS.md` — system of record: round-1/round-2/no-fill tables + per-stage decomposition + FINAL VERDICT (A-variant two-part gate, Phase-6 mandate, three re-open conditions)
- `app/src/main/java/works/mees/dinghy/bench/RenderBenchScene.kt` — D-10 perf scene; ProgressRing + GraphViewHost driven by the ~3 Hz SyntheticFeed at the real 40/40/20 Field-panel footprint
- `macrobenchmark/.../RenderBenchmark.kt` — UiAutomator/MacrobenchmarkRule driver cold-launching the render scene (FrameTimingMetric corroboration; gfxinfo is SoR)
- `app/src/main/java/works/mees/dinghy/render/GraphView.kt` — added the bench-only `drawArea` isolation lever (default `true` = design-true); allocation-free draw unchanged
- `app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt` — `drawArea` param defaults `true` (product is always design-true)
- `app/src/main/java/works/mees/dinghy/bench/BenchActivity.kt` — `--ez nofill` route for the A-B (bench-only; product never sets it)
- `docs/adr/0001-ui-toolkit-decision.md` — dated 2026-05-31 Addendum re-scoping the 16.6 ms/42 ms floors as criterion-#5 acceptance (append-only)
- `.planning/phases/.../03-VALIDATION.md` — dated pointer note on the criterion-#5 row

## Decisions Made

- **Criterion #5 = PASS on the A-variant two-part gate** (decided by Matthew with a Codex second opinion). Gate 1 (liveness — allocation-free, no animation loop, zero frozen frames) MET cleanly; Gate 2 (sparse-redraw latency p95 ≤ ~66 ms, DERIVED from the ~24 ms composite floor + headroom) MET at 50.1 ms with ~16 ms margin.
- **The original `p50 ≪ 16.6 ms` is retired as criterion-#5 wording** — it was a small-surface frame budget mis-generalized to full-screen composition. Re-framed as a sparse-redraw latency budget; the 60 Hz mental model does not apply at ~3 Hz / ~13% duty cycle.
- **Keep the canonical area-fill in the product.** The fill is real but secondary (~5.6 ms); the win came from layout fidelity, not from degrading the design contract.
- **Reconcile, don't rewrite, history.** ADR-0001's Phase-1 measurements stand as recorded; a dated Addendum + pointer notes explain the re-scope.

## Deviations from Plan

The plan's Task-2 acceptance criteria asserted the now-retired Phase-1 budget (`p50 ≪ 16.6 ms`, `~42 ms` Views-graph tolerance). Reaching that gate honestly required:

### Auto-fixed Issues

**1. [Rule 1 - Bug] Round-1 perf scene over-sized the graph vs the canonical layout**
- **Found during:** Task 2 (on-device capture, Round 1)
- **Issue:** The first render scene laid ring + graph each at `weight(1f)`, giving the `GraphView` ~half the portrait screen (~1200×900 px) — NOT the canonical Focus/Field/Gutter ≈ 40/40/20 Field-panel footprint from LAYOUT.md. This over-stated the composite/fill cost (p95 ~61 ms, max ~110 ms).
- **Fix:** Corrected `RenderBenchScene` to the real 40/40/20 rhythm (graph as an inset Field panel) and added a bench-only `drawArea` isolation lever to A-B the fill independently of size.
- **Files modified:** `RenderBenchScene.kt`, `GraphView.kt`, `GraphViewHost.kt`, `BenchActivity.kt`
- **Verification:** Round-2 on-device re-capture on flox — p95 50.1 ms, max 53.9 ms, 0 frozen; A-B confirmed size (not fill) as the dominant lever.
- **Committed in:** `8b65ad9`, `768b488`, `818e468`

The `drawArea` / `--ez nofill` lever is **debug/bench-scoped only** — product callers default `drawArea = true` (design-true). It does not change the default product appearance.

---

**Total deviations:** 1 auto-fixed (Rule 1 — layout-fidelity bug that was over-stating the perf number). No scope creep; the fix made the gate measurable against the real surface size.

## Issues Encountered

- **The literal Phase-1 budget could not be met and should not have been the target.** Resolved by the checkpoint that produced this plan's continuation: Matthew (with a Codex second opinion) re-scoped criterion #5 to the A-variant two-part gate. The ~24 ms native-res window-composite floor is an Adreno-320 hardware ceiling (per-stage decomposition proves even no-fill frames pay it), not removable in software short of dropping resolution.

## Known Stubs

None. The `drawArea` flag is a documented bench-only isolation lever, not a stub — product code renders the design-true (filled, real-size) graph by default.

## Next Phase Readiness

- **Render primitives are perf-proven on the floor device** and ready for Phase 4 (Print Status) and Phase 6 (Temperature) consumption.
- **MANDATORY for Phase 6:** re-measure the full real Temperature screen (multi-trace graph + Field stat grid + gutter) against this same two-part gate — the isolated ring+graph ~16 ms margin is NOT a blank check for the heavier full screen. Three re-open conditions documented in 03-PERF-RESULTS.md: (a) ~24 ms composite floor not stable, (b) full Temperature screen composites materially heavier, (c) the ~3 Hz cadence assumption wrong in production (sustained bursts).

---
*Phase: 03-design-system-theming-foundation*
*Completed: 2026-05-31*

## Self-Check: PASSED

All claimed files exist on disk (03-PERF-RESULTS.md, 03-07-SUMMARY.md, RenderBenchScene.kt, RenderBenchmark.kt, ADR-0001). All claimed commits present in git log (9b77484, 8b81d41, 8b65ad9, 768b488, 818e468, d23805e, 3884140).
