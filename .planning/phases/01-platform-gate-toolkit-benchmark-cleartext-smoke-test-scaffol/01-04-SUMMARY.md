---
phase: 01-platform-gate-toolkit-benchmark-cleartext-smoke-test-scaffol
plan: 04
subsystem: toolkit-decision
tags: [benchmark, on-device, gfxinfo, adr, compose, views, hybrid, pkg-02, d-02, d-05]
dependency_graph:
  requires:
    - "01-01 (:app release build, armeabi-v7a, exported BenchActivity)"
    - "01-03 (SyntheticFeed, ComposeBenchScene/ViewsBenchScene, BenchActivity wiring, parse_framestats.py)"
  provides:
    - "docs/adr/0001-ui-toolkit-decision.md — the recorded Compose-vs-Views verdict (HYBRID), gating Phase 2+"
    - "captures/ — raw gfxinfo framestats CSVs (3 rounds x 2 scenes) + parser summaries + rounds.md"
  affects:
    - "Phase 2+ — every panel: Compose shell + Views (RecyclerView+Canvas) for Files list / temp graph / console"
tech_stack:
  added:
    - "On-device release benchmark workflow: debug-signed release APK (apksigner) + scripted adb gfxinfo capture"
  patterns:
    - "gfxinfo framestats as system of record; identical scripted drive per scene; 3-round stability check"
key_files:
  created:
    - docs/adr/0001-ui-toolkit-decision.md
    - .planning/phases/01-platform-gate-toolkit-benchmark-cleartext-smoke-test-scaffol/captures/compose-framestats.csv
    - .planning/phases/01-platform-gate-toolkit-benchmark-cleartext-smoke-test-scaffol/captures/views-framestats.csv
    - .planning/phases/01-platform-gate-toolkit-benchmark-cleartext-smoke-test-scaffol/captures/rounds.md
  modified:
    - .gitignore
decisions:
  - "VERDICT = HYBRID (user decision): Compose for shell + low/moderate-churn panels (polish toolkit, dev velocity); classic Views (RecyclerView + custom Canvas) MANDATED for the 3 high-churn surfaces — Files list, live temp graph, Console scrollback — where measured frame-time wins ~2x and 'polish' is just efficient drawing."
  - "Benchmarked release on the real flox tablet (LineageOS 18.1 / API 30). Both toolkits cleared the absolute floors (p50 < 16.6 ms, zero frames > 700 ms, no OOM), but Views had ~2x lower tail latency (p95 ~42 vs ~73 ms; max ~72 vs ~121 ms) reproducibly across 3 rounds — contradicting the Compose-everywhere prior for high-churn surfaces."
  - "Made the release installable WITHOUT editing 01-01's build files: zipalign + debug-sign the unsigned release APK via apksigner (apk gitignored). Kept 01-04 within its declared scope (captures + ADR)."
  - "Anchored the .gitignore `captures/` rule to `/captures/` (root only, Android Studio screenshots) so the phase's benchmark captures are tracked (provenance, T-01-09)."
metrics:
  duration_min: 20
  completed: 2026-05-30
  tasks: 2
  files: 5
---

# Phase 01 Plan 04: On-Device Toolkit Benchmark + ADR Summary

Ran the head-to-head Compose-vs-Views benchmark in release mode on the real Nexus 7 2013 hardware,
captured gfxinfo framestats for both scenes across 3 rounds, parsed them to the D-04 metrics, and
recorded the verdict as `docs/adr/0001-ui-toolkit-decision.md` with the raw captures attached. This
finalizes the PKG-02 toolkit gate (ROADMAP success criterion 1 / D-02 / D-05).

## What Was Done

- **Made the release installable (no 01-01 edits):** `:app:assembleRelease` (R8 on) → unsigned APK →
  zipalign + debug-sign via `apksigner` → `adb install`. The benchmark thus measured a real
  release/R8 build, debug-signed only for installation (the genuine signing keystore is Phase 8 / PKG-01).
- **Captured both scenes identically (3 rounds each):** `am start ... --es scene compose|views`,
  3 s dwell, `dumpsys gfxinfo reset`, 6 down-flings + 6 up-flings (`input swipe`, 500 ms settle),
  framestats dumped after each fling to beat the ~120-frame ring buffer; parsed with
  `parse_framestats.py`. Mirrors `ToolkitBenchmark.driveScene()` constants (DWELL 3 s, 6 scrolls, 500 ms settle).
- **Wrote the ADR** recording the HYBRID verdict, justified by the measured medians vs the D-04 floors,
  with the device-reality (API-30/ART) caveat and the named hybrid-Views fallback set.

## Verification Evidence (PKG-02 toolkit gate)

Medians of 3 release-mode rounds on real flox (full table: `captures/rounds.md`):

| Metric | Compose | Views | D-04 floor |
|--------|--------:|------:|-----------|
| p50 (ms) | 9.97 | 7.57 | < 16.6 — both pass |
| p90 (ms) | 65.8 | 36.0 | — |
| p95 (ms) | 72.9 | 41.9 | ~33–50 |
| max (ms) | 121 | 72 | — |
| frames > 700 ms | 0 | 0 | 0 — both pass |

- Two (×3) framestats CSVs under `captures/` with real frame rows; parser reports p50/p90/p95 +
  count(>700 ms) for each. ✓
- Release-mode, real Nexus 7 2013 (flox), 1920×1200, identical scripted drive. ✓
- No OOM / GC storm during the Coil thumbnail scroll (concurrent GC, sub-ms pauses, ~5 MB heap,
  no OutOfMemory / no lowmemorykiller). ✓ (D-04 floor)
- ADR exists (Context / Decision / Consequences), tabulates metrics vs floors, cites captures. ✓

**Verdict: HYBRID** — Compose shell + Views (RecyclerView + Canvas) for Files list / temp graph /
console. Recorded in `docs/adr/0001-ui-toolkit-decision.md`.

## Deviations from Plan

- **Signing (environmental):** plan's `./gradlew :app:installRelease` can't run on an unsigned release.
  Resolved by zipalign + debug-sign via apksigner + `adb install` — no 01-01 build-file edits, 01-04
  scope preserved. (Rule 3 — blocking deviation, can't run a release benchmark otherwise.)
- **FrameTimingMetric corroboration not captured:** requires a `profileable`/debuggable release variant,
  which would edit 01-01's shared manifest. gfxinfo is the D-07 system of record and is self-sufficient;
  FrameTimingMetric is corroboration-only. Deferred (non-blocking) and noted in the ADR + rounds.md.
- **Device is API 30, not API 23** (carried from 01-02): benchmark run on LineageOS 18.1. ART caveat
  documented — the Views advantage is a lower bound vs stock-6. minSdk 23 retained.

## Threat Register Coverage

- **T-01-09 (verdict provenance):** mitigated — ADR cites raw framestats CSVs (3 rounds) retained under `captures/`; verdict derived from D-04 metrics, not assertion.
- **T-01-10 (measurement env):** mitigated — release/R8 on the real flox device at 1920×1200; no emulator/debug/modern-phone.

## Notes for Downstream (Phase 2+)

- Build panels in Compose; use `AndroidView`/`ComposeView` interop to host the three Views surfaces
  inside the Compose shell. Keep view-models toolkit-agnostic (StateFlow to both).
- The temp graph is a custom Canvas either way — implement it as a `View` per the ADR.
- Re-confirm on stock-6 only if that becomes a real shipping target (Views advantage expected to widen).

## Self-Check: PASSED
