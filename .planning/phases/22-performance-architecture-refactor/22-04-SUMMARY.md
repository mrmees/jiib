# 22-04 SUMMARY — PrintStatusScreen god-component split + hot-path fixes (D-01, D-03)

**Plan:** 22-04 · **Wave:** 2 · **Status:** ✅ Complete · **Date:** 2026-06-08

## What was built

The first big D-01 restructure: split the 1585-line `PrintStatusScreen.kt` god-component into
independently-restartable scopes (the P0 inline-lambda-slot fix), then land the co-located P1/P2
hot-path fixes (D-03) as separate attributable commits — gated behind two on-device parity checks.

**Task 1 — STRUCTURAL SPLIT (`6997ad6`):** `PrintStatusScreen.kt` (1585 → 619 lines, orchestration
shell) split into `PrintStatusFocus.kt`, `PrintStatusField.kt`, `PrintStatusGutter.kt`, plus the
executor's finer decomposition (`PrintStatusControlModel.kt`, `PrintStatusHolder.kt`,
`PrintStatusMode.kt`, `PrintStatusUiModel.kt`) and a `PrintStatusPreviews.kt` pointer doc. Each
`ScreenScaffold` slot lambda body now calls a single named composable → restartable child scopes
ScreenScaffold can skip (the P0 inline-lambda-slot fix). The real `@Preview` matrix is unchanged
(it lives in `works.mees.dinghy.preview.PrintStatusPreviews` per the existing convention).

**Task 2 — on-device structural parity (owner-approved):** orchestrator built+installed the release
on flox; **all 4 PrintStatus modes verified pixel-identical** to pre-refactor — Standby, Printing,
Paused (dimmed ring + pause overlay), Terminal (cancelled). Pure refactor, no visual change.

**Task 3 — opportunistic P1/P2 hot-path fixes (D-03), each its own commit:**
- `006db81` — `spoolSwatches` → `ImmutableList<Color>` via `remember(spoolDetail, metadata)`; type
  threaded through the Field signatures (SpoolGlyph accepts it covariantly).
- `604bf56` — `ImageRequest.Builder` wrapped in `remember(thumbUrl, context)` (both Focus sites).
- `f7b3fa1` — `fmtDuration` memoized on an integer-second key at call sites + `FmtDurationTest` (13 cases).
- `2204387` — `LauncherGrid` stabilized with `ImmutableList<LauncherDest>`; **LazyVerticalGrid
  deliberately SKIPPED** — the flexible "Drawer tile grows to full width when alone on the last row"
  layout contract (LAW) can't be reproduced with `GridCells.Fixed` without per-item `GridItemSpan`,
  and Lazy measurement/scroll would change behavior for a tiny fixed tile set with no benefit.
- `531f00c` — removed per-cell `BoxWithConstraints` from `IconTwoRowCell`/`IconValueCell`: a SINGLE
  `BoxWithConstraints` now sits at the `StatGrid` level and computes `cellHeight = (maxHeight −
  8dp·(numRows−1)) / numRows` once (numRows 3, or 4 when showZOffset), passing `iconSp` down.
  Provably visual-identical (equal-weight rows → old per-cell `maxHeight` already == gridHeight/numRows);
  eliminates 5–6 subcomposition scopes per StatGrid recomposition at 4 Hz.

**Task 4 — on-device opportunistic-fix parity (owner-approved):** Standby verified identical
(launcher grid 3e + spool surface 3b + layout). The three mid-print-only fixes (3a stat-grid icons,
3c thumbnail, 3d duration) are provably/structurally safe — owner approved deferring their visual
spot-check to the 22-06/22-07 prints (both run a live print). **⚠ Carry-forward:** confirm mid-print
StatGrid icon sizing, thumbnail, and duration text render identically during the next print.

## Key files

- `ui/printstatus/PrintStatusScreen.kt` (1585→619), `PrintStatusFocus.kt`, `PrintStatusField.kt`,
  `PrintStatusGutter.kt`, `PrintStatusControlModel.kt`, `PrintStatusHolder.kt`, `PrintStatusMode.kt`,
  `PrintStatusUiModel.kt`, `PrintStatusPreviews.kt` (pointer doc), `test/.../FmtDurationTest.kt`.

## Verification

- ✅ `assembleRelease` + `testReleaseUnitTest` GREEN after every task (per-fix incremental + final gate).
- ✅ Task 2: all 4 PrintStatus modes pixel-identical on flox (owner-approved).
- ✅ Task 4: Standby identical on flox (owner-approved); mid-print 3a/3c/3d deferred to 22-06/07 (provably safe).
- ✅ `@Preview` matrix intact (separate preview package, untouched by the split).

## Self-Check: PASSED

Commits: `6997ad6` (split), `006db81`/`604bf56`/`f7b3fa1`/`2204387`/`531f00c` (opportunistic fixes).
Two on-device parity gates owner-approved. One carry-forward: mid-print 3a/3c/3d visual spot-check at next print.
