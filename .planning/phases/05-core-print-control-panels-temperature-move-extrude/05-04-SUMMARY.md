---
phase: 05-core-print-control-panels-temperature-move-extrude
plan: 04
subsystem: render
tags: [graphview, classic-views, canvas, multi-trace, theme-tokens, perf-floor]

# Dependency graph
requires:
  - phase: 05-02
    provides: ThemeTokens.violet third sensor-trace token (baked dark+light) for the multi-trace palette
  - phase: 03-05
    provides: single-trace GraphView + GraphViewHost + GraphView.sanitize input-edge contract this EXTENDS in place (D-05, not forked)
provides:
  - "GraphView N-trace render: Array<Path>/Array<Paint> (MAX_TRACES=3) on one shared X window + one FIXED shared Y-range (0..350 °C), allocation-free onDraw"
  - "Per-trace dashed current-setpoint line (D-04) — DashPathEffect pre-allocated once in init, colored per-trace in onDraw, no allocation"
  - "applyTokens trace palette nozzle=heat / bed=accent / chamber=violet — no raw hex (THEME-01); theme flip recolors all traces with no recreation"
  - "GraphViewHost series:List<FloatArray> overload forwarding tokens+yRange+setpoints into one View instance; single-snapshot overload retained (Print Status sparkline back-compat)"
affects: [05-05-temperature-panel, 05-08-perf-gate]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "EXTEND the one render primitive in place (D-05) — never fork the allocation-free contract; the perf gate stays anchored to the single GraphView"
    - "FIXED shared Y-range replaces per-frame window min/max auto-range (Phase-4 gap G-1 fix); default ceiling covers the setHeater clamp so a legal target never clips"
    - "All onDraw paint/path/effect state pre-allocated (incl. DashPathEffect built once in init) — Pitfall 4 allocation-free draw on the Adreno-320 floor"

key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/render/GraphView.kt
    - app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt
    - app/src/test/java/works/mees/dinghy/render/GraphDownsampleTest.kt

key-decisions:
  - "Fixed Y default 0..350 °C (NOT 0..300) so the full setHeater clamp ceiling fits — a legal nozzle target never clips off-screen (Codex finding / G-1 fix)"
  - "Area fill bounded to the PRIMARY trace (index 0) only — fill-rate is the Adreno-320 suspect; drawArea stays the perf-isolation lever for the 05-08 gate"
  - "Setpoint line is the CURRENT target only, not a historical target series — deliberate fill-budget narrowing (D-04)"

patterns-established:
  - "Per-trace independence: each series sanitizes once via GraphView.sanitize; a NaN or length in one trace never contaminates another"

requirements-completed: [TEMP-04]

# Metrics
duration: 3min
completed: 2026-06-01
---

# Phase 5 Plan 04: Multi-trace GraphView + fixed Y-range Summary

**Extended the single-trace classic-Views GraphView IN PLACE to N (≤3) pre-allocated traces on one shared axis with a FIXED 0..350 °C Y-range (the Phase-4 G-1 fix) plus per-trace dashed current-setpoint lines, and widened GraphViewHost to forward N snapshots — keeping the Print Status sparkline back-compatible.**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-06-01T05:38:02Z
- **Completed:** 2026-06-01
- **Tasks:** 2
- **Files modified:** 3 (0 created, 3 modified)

## Accomplishments
- `GraphView` now holds `linePaths: Array<Path>` + `linePaints: Array<Paint>` sized to `MAX_TRACES=3` (nozzle/bed/chamber), each pre-allocated and `rewind()`-per-draw — no `Path()`/`Paint()` in `onDraw` (Pitfall 4).
- Replaced the per-frame window min/max auto-range with a FIXED settable `yRange` defaulting to `0f..350f` (covers the `setHeater` 0..350 clamp ceiling so a legal target never clips; the Phase-4 gap G-1 fix). A degenerate range is guarded (centers the trace); y is clamped to the view height.
- Per-trace OPTIONAL dashed current-setpoint line (D-04): `setSetpoints(List<Float?>)`; ONE `DashPathEffect` built once in init on a pre-allocated `setpointPaint`, recolored to the trace's color per line — never allocated in `onDraw`. Non-finite/null targets draw nothing.
- `applyTokens` colors trace 0=`t.heat`, 1=`t.accent`, 2=`t.violet` (added 05-02), fill from the primary trace color at low alpha — no raw hex (`grep "Color(0x"` returns nothing).
- Area fill bounded to the primary trace (index 0) only; `drawArea` retained as the fill-rate isolation lever for the 05-08 Adreno-320 perf gate.
- Back-compat: `setData(FloatArray)` delegates to `setData(List<FloatArray>)` with one element; PrintStatusScreen's single-snapshot `GraphViewHost(...)` call compiles unchanged (resolves to the retained single-snapshot overload, no setpoint line).
- New `GraphViewHost(series: List<FloatArray>, setpoints, yRange, drawArea)` overload pushes all of tokens/yRange/series/setpoints into the SINGLE factory-created instance — a theme flip recolors all traces with no recreation.
- Extended `GraphDownsampleTest` with per-series independence (NaN in one trace doesn't contaminate another; long series capped; single-point intact) + empty-series-among-traces; full `:app:testReleaseUnitTest` green.

## Task Commits

Each task was committed atomically:

1. **Task 1: Generalize GraphView to N pre-allocated traces + fixed Y-range** — `36ea3a7` (feat)
2. **Task 2: Widen GraphViewHost to N snapshots + token trace colors** — `2223dca` (feat)

**Plan metadata:** _(final docs commit)_

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/render/GraphView.kt` — N-trace `Array<Path>`/`Array<Paint>`, fixed `yRange` (0..350), pre-allocated dashed setpoint paint, per-trace token colors, primary-only area fill, single-trace back-compat overload.
- `app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt` — added `series: List<FloatArray>` overload (forwards tokens/yRange/setpoints into one View); single-snapshot overload retained.
- `app/src/test/java/works/mees/dinghy/render/GraphDownsampleTest.kt` — per-series independence + empty-series-among-traces cases.

## Decisions Made
- **Default Y ceiling = 350 °C, not 300.** A 0..300 default would clip a legal nozzle target near the `setHeater` clamp ceiling; 0..350 covers the full clamp so no legal target ever renders off-screen (Codex finding / G-1 fix).
- **Area fill = primary trace only.** Fill-rate is the Adreno-320 bottleneck; one translucent near-region fill is the suspect, so only trace 0 fills and `drawArea` stays the per-region isolation lever the 05-08 gate flips.
- **Current-setpoint line only (not a historical target series).** Deliberate fill-budget narrowing per D-04 — the contract is one horizontal dashed line at the live target.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None — render-only extension, no external service configuration.

## Next Phase Readiness
- The multi-trace history graph the Temperature panel (05-05) hosts is ready: pass per-sensor `RingBuffer.snapshot()`s as `series`, live targets as `setpoints`, tokens forwarded for the heat/accent/violet palette.
- **D-06 perf MANDATE stands:** the multi-trace fill-rate on the real flox / Adreno 320 is RE-MEASURED on the FULL Temperature screen against the two-part gate in 05-08 — do NOT grandfather the Phase-3 isolated 50.1 ms number. `drawArea` is the attribution lever.
- No blockers.

## Threat Flags

None — render-only extension; no new network/input boundary. The render-DoS threat (T-05-04-D, per-frame allocation / unbounded vertices) is mitigated in code (all paths/paints/dash-effect pre-allocated, `rewind()` per draw, `sanitize` caps each series to pixel width once in `setData`); the on-device confirmation is the 05-08 perf gate.

## Self-Check: PASSED

- All 3 modified files present on disk.
- Both task commits present in git log (36ea3a7, 2223dca).
- `:app:testReleaseUnitTest` (GraphDownsampleTest) green; `:app:compileReleaseKotlin` green (PrintStatusScreen back-compat intact).

---
*Phase: 05-core-print-control-panels-temperature-move-extrude*
*Completed: 2026-06-01*
