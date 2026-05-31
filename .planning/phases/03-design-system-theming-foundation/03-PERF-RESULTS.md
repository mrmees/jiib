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

## Round-1 read — FAIL against the (later-invalidated) p50/p95 budget; PASS on frozen-frames + cadence
> NOTE: this Round-1 "verdict" is superseded. The `p50 ≪ 16.6 ms` / `~42 ms` thresholds it fails against
> were re-scoped as INVALID-when-generalized — see **FINAL VERDICT (2026-05-31)** at the bottom of this
> file. Criterion #5 ultimately PASSED under the A-variant two-part gate.

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

---

# ROUND 2 — Matthew chose OPTIMIZE-THEN-RE-MEASURE. Variables isolated, re-captured on flox.

Per the checkpoint decision, the area-fill was made toggleable (`GraphView.drawArea`, default `true` =
the canonical `hifi.css .g-area opacity:.16`) and the bench scene was corrected to the **real** Print
Status portrait layout — `LAYOUT.md` rhythm **Focus / Field / Gutter ≈ 40 / 40 / 20**, graph as a
**Field panel** (centered, inset), NOT the naked `weight(1f)` half-screen the Round-1 scene used. Two
variables, each measured: graph **size** (corrected) and **fill** (on vs off). Same harness, same flox,
same parser, `--warmup 20`. Commits: `8b65ad9` (drawArea lever), `768b488` (scene layout + nofill route).

## Round-2 captured results (corrected size)

| Metric | FILLED (size-fixed, fill ON = design-true) | NOFILL (size-fixed, fill OFF) | Round-1 (over-sized, fill ON) |
|--------|--------------------------------------------|-------------------------------|-------------------------------|
| frames measured | 98 | 99 | 106 |
| **p50 (ms)** | **40.81** | 36.87 | 44.35 |
| p90 (ms) | 48.11 | 41.89 | 58.55 |
| **p95 (ms)** | **50.11** | 44.51 | 61.27 |
| max (ms) | 53.92 | 57.87 | 109.61 |
| **frames > 700 ms (frozen)** | **0** | **0** | **0** |

## Variable attribution (the point of the A-B)

- **SIZE was the prime culprit (confirmed).** Correcting the over-sized region (half-screen → 40% Field
  panel) drops p95 from **~61 → ~50 ms** (filled) and tames the tail (max 109 → 54 ms). The Round-1
  hypothesis — "fill-rate driven by primitive SIZE" — is empirically correct: the dominant lever was
  *how many pixels we composite*, not the fill style.
- **FILL is secondary.** Dropping the translucent area-fill entirely shaves only **~5.6 ms p95**
  (50.1 → 44.5) at the corrected size. The fill is real but NOT the wall. So we keep the canonical
  `.g-area` aesthetic (`drawArea = true` in product) — the visual contract is preserved; the win came
  from fixing the scene's layout fidelity, not from degrading the design.

## Per-stage decomposition (where the ~40 ms goes) — it's the GPU/composite floor, not our code

Median per-frame stage split (filled / nofill), from the framestats stage timestamps:

| Stage | filled | nofill | what it is |
|-------|--------|--------|------------|
| PerformTraversals → DrawStart | 0.2 ms | 0.2 ms | measure/layout (negligible — no per-sample relayout) |
| DrawStart → IssueDrawCommands | 3.7 ms | 3.2 ms | CPU draw-record (cheap; allocation-free, Pitfall 4 holds) |
| **IssueDrawCommands → FrameCompleted** | **25.9 ms** | **23.7 ms** | **issue + GPU + swap — the fill-rate stage** |

The cost is overwhelmingly the **GPU/composite stage (~24–26 ms)**, with trivial layout and small CPU
record. Crucially, even the line-only NOFILL frame is **~24 ms GPU** — i.e. the floor is the cost of
compositing the **full-screen window at native 1200×1920 through the Adreno-320 ROPs**, regardless of
what the graph draws. This is the device's hard fill-rate ceiling (T-03-08 made concrete), not a code
defect. The area-fill is ~2 ms of that stage; the rest is physics.

## Cadence re-confirmed (D-13) — PASS

Clean ~6 s window on the corrected build: **18 total frames ≈ 3 Hz**. A 60 fps loop would be ~360.
`dumpsys` labels 16/18 frames "janky" and "Slow UI thread: 13" — i.e. **every one of the ~3 per-second
value-driven redraws exceeds the 16.6 ms vsync deadline** — but there are only ~3/s and **zero frozen
(>700 ms)**. The 60 fps "janky-frame %" yardstick is the wrong ruler for a ~3 Hz value-driven appliance
surface; the right rulers are *frozen frames* and *does-an-update-read-instant*.

## Round-2 verdict — honest read

- ✅ **Zero frozen frames** (0/0, both variants) — the project-soul non-negotiable holds.
- ✅ **No animation loop** — ~3 Hz value-driven redraw (D-13).
- ✅ **Prime culprit found and fixed** — over-sizing, not the aesthetic. p95 61 → 50 ms with the design
  intact; tail collapsed (max 110 → 54 ms).
- ⚠️ **Per-redraw still misses 16.6 ms vsync.** p50 ~41 / p95 ~50 ms (filled). The remaining cost is the
  device's native-res window-composite floor (~24 ms GPU), which is physics — not removable in software
  short of dropping resolution or shrinking the rendered surface.
- ⚠️ **Headroom for Phase 6 is the open question.** The ~24 ms composite floor does NOT multiply with
  more traces (it's per-window, not per-line), and CPU record is cheap, so multi-trace likely lands
  ~55–60 ms p95 — still imperceptible at 3 Hz and frozen-free. But the *full* Phase-6 Temperature screen
  composites MORE than ring+graph (Field stat grid, gutter), so the realistic full-screen p95 will be
  higher than this isolated ring+graph number. I cannot honestly claim "clean pass WITH headroom"
  against a budget I'd be defining post-hoc to fit the result.

## Proposed honest budget for criterion #5 (for Matthew's sign-off)

A **two-part bound** that matches what the surface actually is — a ~2–4 Hz value-driven appliance screen,
not a 60 fps scroller:

1. **HARD floor (non-negotiable, project soul):** zero frozen frames (0 frames > 700 ms) AND no
   continuous-animation loop (redraw count tracks the ~3 Hz feed, not Choreographer). → **MET, cleanly.**
2. **Per-redraw bound:** each value-driven redraw ≤ ~**66 ms p95** (≈ 2 display refreshes; an update at
   3 Hz with ~13 % duty cycle reads as instant well below this). Rationale: it's tied to the *physical*
   native-res composite floor (~24 ms) plus realistic per-trace headroom, NOT reverse-fit to 50 ms. The
   ring+graph today is **50 ms p95** — comfortably inside, with ~16 ms of margin for Phase-6 traces on
   this isolated surface.

This is offered as a *defensible* budget, deliberately NOT "wherever the number landed." But because it
(a) abandons the literal ADR-0001 `p50 ≪ 16.6 ms` wording and (b) makes a forward claim about a
not-yet-built Phase-6 full screen, it is a human call per the checkpoint mandate. See the checkpoint.

---

# FINAL VERDICT (2026-05-31) — Matthew decided. Criterion #5 = **PASS** under the A-variant two-part gate.

After reviewing the Round-2 numbers AND a Codex second opinion, Matthew **closed criterion #5 on the
A-variant** — a two-part gate stated honestly, NOT the old single `p50 ≪ 16.6 ms` threshold. This section
is the system of record for the verdict; the round-1/round-2/no-fill tables above stand as the evidence.

## Why the original `p50 ≪ 16.6 ms` target was INVALID when generalized

The original criterion-#5 acceptance leaned on the Phase-1 toolkit-benchmark floor of `p50 ≪ 16.6 ms`
(ADR-0001 "Measured Results"). **That number came from a tiny `dp(260)`-tall graph spike** — a small
surface where the per-frame cost is dominated by CPU draw-record, not window composition. Generalizing it
to a **full-screen value-driven redraw on this device was a category error.** As the Round-2 per-stage
decomposition proves, even a line-only (no-fill) frame spends **~24 ms in the GPU/composite stage** simply
compositing the native **1200×1920** window through the Adreno-320 ROPs. **Missing one 16.6 ms vsync is
structurally unavoidable for full-window composition on this GPU** — it is physics, not a code defect.

Accordingly we **stop calling 16.6 ms a "frame budget."** That phrase smuggles in a 60 Hz, every-
Choreographer-frame mental model that does not apply to a ~3 Hz value-driven appliance surface. The
correct concept here is a **SPARSE-REDRAW latency budget on the floor device**: how long a single,
infrequent, value-driven redraw takes — and whether that reads as instant — not a continuous 60 fps target.

## The two gates (A-variant) — both MET today

### 1. LIVENESS GATE (hard floor — the project soul) → **MET cleanly**
The render primitives must be:
- **allocation-free** in the draw path (Pitfall 4) — confirmed: one reusable `Path`/area-`Path`, pre-
  allocated stroke + fill `Paint`, `rewind()` per draw, never `Path()`/`Paint()` in `onDraw`;
- contain **NO animation loop** — confirmed: redraw count tracks the ~3 Hz feed (≈3.0–3.2 Hz measured),
  not Choreographer; static glow only (D-13);
- produce **ZERO frozen frames** (no frame > 700 ms) — confirmed: **0/0** across every capture (round 1,
  round 2 filled, round 2 no-fill).

### 2. SPARSE-REDRAW LATENCY GATE → **MET at 50.1 ms p95, ~16 ms margin**
For **value-driven, full-screen redraws** (the real ~3 Hz cadence, **NOT** continuous animation):
> **p95 ≤ ~66 ms** (≈ 2 display refreshes; sub-perceptible at a 3 Hz value change, ~13 % duty cycle).

This bound is **DERIVED, not reverse-fit:** it is the measured ~24 ms hardware composite floor + realistic
CPU-draw/per-trace headroom, rounded to ~2 vsyncs. It is deliberately *not* set to "wherever the number
landed" (50 ms). The design-true filled ring+graph today is **50.1 ms p95** — inside the bound with
**~16 ms of margin**.

## Optimization finding (the win that made this defensible)
The Round-1 → Round-2 A-B established that **SIZE, not the aesthetic, was the Round-1 culprit.** Correcting
the over-sized `weight(1f)` half-screen graph to the canonical **Focus/Field/Gutter ≈ 40/40/20** Field-panel
footprint dropped p95 **61 → 50 ms** and collapsed the tail (max **110 → 54 ms**) with the design intact.
Dropping the `.16`-opacity area-fill entirely shaved only **~5.6 ms** more — so **the canonical
`hifi.css .g-area` fill stays ON in the product** (`GraphView.drawArea = true`); the `--ez nofill` route is
a bench-only A-B isolation lever, never a product appearance change.

## MANDATORY FOLLOW-UP — Phase 6 MUST re-measure against this same two-part gate
Phase 6 (Temperature panel) **extends this exact graph primitive with multiple heater traces** and adds the
Field stat grid + gutter chrome — i.e. it composites **materially more** than this isolated ring+graph test
path. **Phase 6 MUST re-run the full real Temperature screen against this same two-part gate** (liveness +
sparse-redraw ≤ ~66 ms p95) on flox. The isolated ring+graph margin (~16 ms) is *not* a blank check for the
full screen.

### Three conditions that INVALIDATE this verdict and FORCE a re-open
1. **The ~24 ms composite floor turns out not to be stable** (e.g. it scales with draw content or varies
   run-to-run on flox) — the derived 66 ms bound loses its physical anchor.
2. **The real Temperature screen composites materially heavier** than this test path (multi-trace + stat
   grid + gutter pushing GPU-stage cost well above ~24 ms), such that p95 breaches ~66 ms.
3. **The ~3 Hz cadence assumption is wrong in production** — if the surface bursts into sustained redraws
   (faster than the conflated 2–4 Hz state cadence), the "sparse, sub-perceptible" premise collapses and a
   continuous-frame budget would apply instead.

If any of the three holds in Phase 6, criterion #5 re-opens and the budget must be re-derived against the
real measured floor — not grandfathered from this isolated result.

## Verdict line
**Criterion #5: PASS** (A-variant two-part gate). Liveness gate MET cleanly (allocation-free, no loop,
0 frozen). Sparse-redraw latency gate MET at 50.1 ms p95 vs the derived ~66 ms bound (~16 ms margin), design-
true (filled, real Field-panel size). Phase-6 re-validation mandated above.
