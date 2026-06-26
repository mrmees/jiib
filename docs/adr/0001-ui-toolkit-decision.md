# ADR 0001 — UI Toolkit: Hybrid (Compose shell + Views for high-churn surfaces)

- **Status:** Accepted
- **Date:** 2026-05-30
- **Deciders:** Matthew Mees (project owner); benchmark run + analysis by Claude
- **Phase:** 01 (Platform Gate) — plan 01-04; gates all Phase 2+ panel architecture
- **Requirement:** PKG-02 (toolkit decision, D-02/D-05)

## Context

The whole point of Phase 1 was to settle, on real weak hardware, whether to build the printer
touchscreen in **Jetpack Compose everywhere** or use **classic Views** for the heavy surfaces.
The constraint is an **Adreno 320 / 2 GB / 1920×1200** tablet — fill-rate-bound, high-resolution,
old GPU. A janky printer screen is worse than none, so this is decided by measurement, not preference.

**The CLAUDE.md research prior leaned Compose ("HIGH confidence")** for dev velocity and modern
tooling, while explicitly naming a **hybrid-Views fallback** for high-churn surfaces (Files list,
temperature graph, Console scrollback) if measurement demanded it. This ADR is that measurement.

### Methodology (fairness controls)

- One **deterministic in-process SyntheticFeed** (~3 Hz, fixed seed, byte-identical replay) drives
  **both** a `ComposeBenchScene` (LazyColumn + Coil 3 `AsyncImage` real PNG decode, Compose `Canvas`
  graph, Compose console) and a `ViewsBenchScene` (RecyclerView + Coil `ImageLoader` decode, custom
  `View` graph, TextView console) rendering the **same worst-case layout** at the device's real
  1920×1200. (D-02 / D-06 / D-07)
- Measured artifact is the **`:app` release build (R8/minify on)**, debug-signed for install — never
  debug, never emulator, never a modern phone. (D-03)
- **Identical drive** per scene: 3 s dwell → `dumpsys gfxinfo reset` → 6 down-flings + 6 up-flings
  (`input swipe`, 500 ms settle), framestats dumped after each fling to beat the ~120-frame ring buffer.
- **System of record = gfxinfo framestats** (per-frame `FrameCompleted − IntendedVsync`), parsed for
  p50/p90/p95 + count(frames > 700 ms), 20-frame warmup excluded. (D-04 / D-07)
- **3 rounds per scene** for stability. Raw CSVs + parser outputs retained under the phase
  `captures/` directory (see `captures/rounds.md`).

### Device-reality note (material to how these numbers read)

The physical "Nexus 7 2013" runs **LineageOS 18.1 / Android 11 / API 30**, not stock Android 6 /
API 23. Hardware is genuine `flox` (Adreno 320 / 2 GB / 1920×1200). Consequence: the API-30 runtime
(ART) is **newer and faster** than stock-6 ART, so on a true API-23 device the Compose figures would
be **worse**, not better. The Views advantage measured here is therefore a **lower bound** on the
real stock-6 target. `minSdk 23` is retained as the install floor.

## Measured Results (medians of 3 release-mode rounds, ms)

| Metric | Compose | Views | D-04 floor | Better |
|--------|--------:|------:|-----------|:------:|
| p50 frame time | 9.97 | **7.57** | < 16.6 (both pass) | Views |
| p90 frame time | 65.8 | **36.0** | — | Views |
| p95 frame time | 72.9 | **41.9** | ~33–50 tolerance | **Views** |
| max frame time | 121 | **72** | — | Views |
| frames > 700 ms | **0** | **0** | 0 (both pass) | tie |

Full per-round table and caveats: `captures/rounds.md`. GC was healthy in both (sub-millisecond
concurrent-GC pauses, ~5 MB heap, **no OutOfMemory / no lowmemorykiller**) during the Coil thumbnail
scroll — the D-04 OOM floor is met by both toolkits.

**Reading:** Both toolkits **clear the absolute floors** (p50 ≪ 16.6 ms, zero frozen frames), so
Compose-everywhere is *viable*. But **Views has ~2× lower tail latency** (p95 ~42 ms vs ~73 ms; max
~72 ms vs ~121 ms) on the worst-case high-churn scene, reproducibly across all 3 rounds. On weak
hardware, tail latency is exactly what reads as scroll jank. The result **contradicts the Compose-
everywhere prior** for these surfaces and matches the pre-named hybrid-Views fallback.

## Decision

Adopt a **hybrid UI architecture**:

- **Jetpack Compose** is the default for the app shell and all low-/moderate-churn panels — nav,
  theming (Material 3, dark high-contrast), move/extrude controls, setpoint controls, macro buttons,
  job-status cards, dialogs, transitions. This keeps Compose's polish toolkit and dev velocity where
  it matters.
- **Classic Views** (RecyclerView + custom `Canvas`) are **mandated** for the three high-churn
  surfaces where the measured frame-time win is largest and where "polish" reduces to efficient
  drawing rather than fancy animation:
  1. **Files list** (RecyclerView + Coil `ImageLoader` thumbnail decode/recycling)
  2. **Live temperature graph** (custom `View` + `Canvas`)
  3. **Console scrollback** (efficient text list)

Compose interop (`AndroidView` / `ComposeView`) hosts the Views surfaces inside the Compose shell.

## Consequences

**Positive**
- Best measured frame-time exactly where the hardware is weakest; ~90% of the app keeps Compose ergonomics.
- The three Views surfaces are the ones least dependent on Compose's animation/theming toolkit, so polish loss is minimal.
- Verdict is backed by retained raw captures (provenance: T-01-09), not assertion.

**Negative / Costs**
- Two UI paradigms in the codebase; an interop seam (`AndroidView`/`ComposeView`) at the shell↔surface boundary.
- The Views surfaces won't get Compose niceties (e.g. animated list-item insertion) — accepted: a smooth plain list beats a pretty stuttering one on a printer touchscreen.
- State must flow uniformly (StateFlow) to both Compose and Views consumers — keep view-models toolkit-agnostic.

**Follow-ups (non-blocking)**
- Capture Macrobenchmark `FrameTimingMetric` as corroboration once a `profileable` release variant
  exists (would touch 01-01's shared manifest; deferred).
- If stock-6 / API-23 ever becomes a real shipping target, re-confirm on that runtime — the Views
  advantage is expected to widen.

## References

- Captures: `.planning/phases/01-platform-gate-.../captures/` — `compose-framestats*.csv`,
  `views-framestats*.csv`, `*-summary.txt`, `rounds.md`
- Harness: `app/src/main/java/works/mees/jiib/bench/` (SyntheticFeed, ComposeBenchScene,
  ViewsBenchScene, BenchActivity), `macrobenchmark/.../ToolkitBenchmark.kt`,
  `tools/gfxinfo-parser/parse_framestats.py`
- Decisions: D-02 (fairness), D-03 (release/real-device), D-04 (thresholds), D-05 (Compose vs hybrid),
  D-07 (gfxinfo system of record)

---

## Addendum (2026-05-31) — the `p50 ≪ 16.6 ms` / `~42 ms` floors were re-scoped for criterion #5

The "Measured Results" floors recorded above (`p50 ≪ 16.6 ms`, Views-graph `p95 ≈ 41.9 ms`) were measured
on the **Phase-1 toolkit benchmark**, whose graph was a tiny **`dp(260)`-tall** surface. Those numbers
remain a faithful record of *that* benchmark and are NOT edited here.

However, Phase 3 plan 03-07 reused this number as criterion #5's acceptance for a **full-screen, value-
driven graph redraw on flox** — and that generalization was **invalid**. The Round-2 on-device per-stage
decomposition (`.planning/phases/03-design-system-theming-foundation/03-PERF-RESULTS.md`) showed that even a
line-only frame spends **~24 ms in the GPU/composite stage** just compositing the native **1200×1920**
window through the Adreno-320 ROPs. **Missing one 16.6 ms vsync is structurally unavoidable for full-window
composition on this GPU** — physics, not a code defect. The 16.6 ms figure is therefore a *frame budget* for
small surfaces, NOT a sparse-full-screen-redraw budget.

**Re-scope (decided by Matthew, with a Codex second opinion, 2026-05-31):** criterion #5 closes on a
two-part gate, NOT the literal `p50 ≪ 16.6 ms` wording:
1. **Liveness gate (hard floor):** allocation-free draw, no animation loop, ZERO frozen frames (>700 ms).
2. **Sparse-redraw latency gate:** value-driven full-screen redraw **p95 ≤ ~66 ms** (~2 vsyncs; sub-
   perceptible at ~3 Hz), derived from the measured ~24 ms composite floor + headroom — not reverse-fit.

This addendum does NOT overturn the *toolkit* decision (hybrid Views-for-high-churn stands; Views is still
~2× lower tail than Compose). It only reconciles how the Phase-1 floor was (mis)applied as criterion #5's
acceptance. Full rationale + the mandatory Phase-6 re-validation + the three re-open conditions: see
`03-PERF-RESULTS.md` § FINAL VERDICT (2026-05-31).

## Addendum 2 (2026-06-01) — the `p95 ≤ ~66 ms` latency gate relaxed to network-paced sparse-redraw

**Decided by Matthew during the Phase-5 Temperature UI pass.** The `p95 ≤ ~66 ms` sparse-redraw number
in Addendum 1 was still borrowed from interactive/animated-UI thinking. This app is **not animated** — the
graph (and every live surface) repaints **only when a new Moonraker status sample arrives**, which is
**network-paced**: ~250 ms today (the `PrinterStateStore` conflation) and arguably fine at ~1 Hz (sub-second
polling of printer temps is overkill). With ≥250 ms between repaints, a 60–90 ms graph frame is invisible and
never queues behind the data.

**Reframed gate for the value-driven render surfaces (graph, sparkline):**
1. **Liveness (hard floor, unchanged):** allocation-free `onDraw`, no animation loop, **ZERO frozen frames
   (>700 ms)**.
2. **Headroom (replaces the literal 66 ms):** a single repaint must complete **comfortably under the sample
   interval** so renders never back up, AND **interaction (taps/nav/scrubber) stays responsive**. The exact
   p95 ms is no longer a hard acceptance number — it is a sanity check, not a target to chase.

**Consequence for the Temperature graph (2026-06-01):** per-trace translucent fills (each trace shaded,
layered coolest-on-top) were added even though they add fill-rate overdraw vs the old single-trace fill —
acceptable under the reframed gate. We verify no-frozen-frames + responsiveness on flox rather than defending
a millisecond threshold. If a future surface ever *does* animate, the tighter Addendum-1 budget reapplies to
*it*.

Open lever (not yet pulled): slowing the `PrinterStateStore` conflation from 250 ms toward ~1 Hz would cut
GPU/CPU/battery on the Adreno-320 floor for free — a one-line change when desired.
