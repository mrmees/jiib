# 22-07 SUMMARY — AppShell collection push-down + phase-closing UAT (D-01 move #2)

**Plan:** 22-07 · **Wave:** 3 · **Status:** ✅ Complete · **Date:** 2026-06-08

## What was built

The second big D-01 restructure plus the phase's closing measurement + regression gate.

**Task 1 — collection audit (`c8f858b`):** classified all 27 `AppShell`
`collectAsStateWithLifecycle` sites (`22-COLLECTION-AUDIT.md`) — 4 MOVED (the calibration VMs:
`zTiltVm`, `qglVm`, `bedMeshVm`, `probeCalibrateVm`), 2 HOISTED to AppContainer StateFlows
(`activeProfileId`, `activeName`), and `errorLines` correctly KEPT session-side (the `consoleHolder`
is `remember(store)`-keyed → session scope, NOT process scope).

**Task 2 — calibration holder migration (`3dbbade`):** `TiltScreen`/`BedMeshScreen`/
`ProbeCalibrateScreen` now take `holder + container` directly (copying the existing
`ScrewsTiltScreen` pattern) and collect `holder.vm` + `container.dispatcher` internally;
entry-reset is each screen's own `LaunchedEffect(Unit)`. AppShell drops 4 collections from its hot scope.

**Task 3 — stateScope + flow hoist + stabilize (`1e2e14f`):** added `AppContainer.stateScope`
(`SupervisorJob + Dispatchers.Default`, separate from the IO `writeScope`); hoisted `activeProfileId`
and `activeName` to stable `StateFlow<String?>` singletons (`WhileSubscribed(5s)`) replacing inline
`.map{}` flows that re-allocated on every shell recomposition; converted `drawerSpoolSwatches` to
`ImmutableList<Color>` via `remember(activeSpoolDetail)` so `AppDrawer` can structurally skip.

**Task 4 — closing gfxinfo AFTER sweep + SC1/SC2/SC5 UAT (owner-accepted):** see `22-GFXINFO-AFTER.md`.

## Closing results (Task 4)

- **SC1:** PrintStatus idle AFTER vs baseline — p50 43.0→41.2, p90 56.8→**49.6 (−13%)**, p95 58.5→54.3,
  **0 frozen frames before and after**. Owner accepted the close without the final mid-print
  measurement (idle improvement + zero frozen frames everywhere + the structural wins are sufficient).
- **SC2:** ✅ `AppContainer @Stable` + `PrinterState @Immutable` (×11) verified at the final commit.
- **SC5:** ✅ nav-regression smoke test on flox — Bed Mesh + Probe Calibrate (live −4.816 z-offset)
  feed correctly through the new holder pattern; drawer profile name (hoisted `activeName`) + spool
  tile (ImmutableList swatches) render; Z-Tilt/QGL correctly capability-gated on the E3; general nav clean.

## Key files

- `ui/shell/AppShell.kt`, `ui/shell/AppDrawer.kt`, `di/AppContainer.kt`,
  `ui/calibration/{TiltScreen,BedMeshScreen,ProbeCalibrateScreen}.kt`,
  `22-COLLECTION-AUDIT.md`, `22-GFXINFO-AFTER.md`.

## Verification

- ✅ `assembleRelease` + `testReleaseUnitTest` GREEN (Tasks 2 & 3).
- ✅ Final release built + installed on flox; SC2 grep-verified; SC5 nav smoke test driven on-device.
- ✅ Session-scoped `errorLines` kept shell-side (not hoisted to process scope) — the key correctness call.

## Self-Check: PASSED

Commits: `c8f858b` (audit), `3dbbade` (holder migration), `1e2e14f` (stateScope/hoist/stabilize).
SC1/SC2/SC5 owner-accepted as the Phase-22 closing gate. v1 milestone (ships at Phase 22) gate met.
