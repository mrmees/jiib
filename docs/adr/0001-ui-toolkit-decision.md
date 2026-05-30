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
- Harness: `app/src/main/java/works/mees/dinghy/bench/` (SyntheticFeed, ComposeBenchScene,
  ViewsBenchScene, BenchActivity), `macrobenchmark/.../ToolkitBenchmark.kt`,
  `tools/gfxinfo-parser/parse_framestats.py`
- Decisions: D-02 (fairness), D-03 (release/real-device), D-04 (thresholds), D-05 (Compose vs hybrid),
  D-07 (gfxinfo system of record)
