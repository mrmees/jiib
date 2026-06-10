---
phase: 25-browse-screens
plan: "01"
subsystem: browse-screens
tags: [spike, gfxinfo, perf, files, console, compose]
dependency_graph:
  requires: []
  provides: ["Files and Console Compose-vs-Views toolkit verdict (25-SPIKE.md)"]
  affects: ["25-03-PLAN.md (FilesScreen migration toolkit)", "25-04-PLAN.md (ConsoleScreen migration toolkit)"]
tech_stack:
  added: []
  patterns: ["BrowseSpikeActivity throwaway harness pattern (mirrors BenchActivity precedent)"]
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/dev/BrowseSpikeActivity.kt
  modified:
    - app/src/main/AndroidManifest.xml
decisions:
  - "Spike uses programmatically-generated ARGB_8888 Bitmap (not Coil) for thumbnail cost measurement — avoids requiring an HTTP base in a throwaway, while preserving per-row image draw cost (same Bitmap format Coil decodes to)"
  - "Console scene uses LazyColumn (not ListBlock) to allow externally-controlled listState for programmatic auto-scroll — ListBlock holds internal state; Console needs scroll control"
  - "Stable key for Console items: idx * 10007L + line.length — position-only collides during eviction; content-hash alone collides when lines repeat; combined form is unique across the live-churn ring"
metrics:
  duration: "~10 min (build 1m 16s)"
  completed: "2026-06-10"
  completed_tasks: 1
  total_tasks: 2
---

# Phase 25 Plan 01: Compose Spike Harness for Files + Console — Summary

**One-liner:** Throwaway Compose ListRow/LazyColumn spike harness for Files (thumbnail rows) and Console (live append+evict churn) surfaces, built for release-mode gfxinfo measurement on flox against the ADR-0001 Addendum-2 gate.

## Status

**Task 1 COMPLETE** — release APK builds with the spike harness.
**Task 2 PENDING** — on-device gfxinfo capture + 25-SPIKE.md verdict (checkpoint:human-verify).

**TOOLKIT VERDICTS** (set after Task 2 completes — currently PENDING):
- Files: [TBD — awaiting flox gfxinfo capture]
- Console: [TBD — awaiting flox gfxinfo capture]

## What Was Built

### Task 1 — BrowseSpikeActivity (commit `b8738d8`)

`BrowseSpikeActivity` (package `works.mees.dinghy.dev`): a throwaway ComponentActivity rendered
with `DinghyTheme` and no Moonraker connection.

**FILES scene** (`spike_surface=files`):
- `ListBlock { items(60 SpikeFileRows, key = { it.id }) { ListRow(...) } }` — literal kit class use
- Each row: `leadingContent = Image(Bitmap.asImageBitmap(), size = 48.dp)` — 96×96px ARGB_8888
  bitmap with varied colors per row; forces real per-row image draw cost (not a flat Box)
- Row content: filename in `FontFamily.Monospace` at `fsSp(17f, t.fs).sp`, meta at `fsSp(13f, ...)`
- `rememberUnitGrid(minOf(maxWidth, maxHeight))` drives `uDp` (touch-target floor)
- 60 rows, stable `key = { it.id }`

**CONSOLE scene** (`spike_surface=console`):
- `LazyColumn` seeded with 300 synthetic gcode-style lines
- `LaunchedEffect(Unit)` ticker: appends 1 new line per 100ms (~10 lines/sec), evicts oldest once
  list exceeds cap=300 — the real steady-state append+evict path (`isAppendEvict`)
- `LaunchedEffect(lines.size)`: scrolls to `lines.size - 1` on every new append (auto-scroll)
- Stable key: `idx.toLong() * 10007L + lines[idx].length` — position×prime + content length
- Line coloring: `!!` → `t.stop`, `//` → `t.text2`, `Send:` → `t.text`, else `t.text`
- 300 items, stable `key = { ... }`

**Manifest registration:**
```xml
<activity android:name=".dev.BrowseSpikeActivity"
          android:exported="false"
          android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden" />
```
No `intent-filter` MAIN/LAUNCHER — dev-only, adb-launched.

**Acceptance criteria verification (all pass):**
- `ListRow(` count = 1 ≥ 1 ✓
- `key = {` count = 3 ≥ 2 ✓
- `Image(` count = 1 ≥ 1 (per-row bitmap, not a flat Box) ✓
- `LaunchedEffect|delay(|while (` count = 6 ≥ 1 (live churn ticker) ✓
- Manifest: `android:exported="false"`, no LAUNCHER category ✓
- `:app:assembleRelease` exits 0 (BUILD SUCCESSFUL, 1m 16s) ✓

**Launch commands:**
```
adb shell am start -n works.mees.dinghy/.dev.BrowseSpikeActivity --es spike_surface files
adb shell am start -n works.mees.dinghy/.dev.BrowseSpikeActivity --es spike_surface console
```

## Deviations from Plan

### Auto-fixed Issues

None.

### Design decisions made during implementation

1. **Programmatic Bitmap instead of Coil** — The plan allowed "same-cost placeholder painter (decoded Bitmap of the same target dimensions)" as an alternative to Coil when wiring a real Coil load is impractical for a throwaway. Used `Bitmap.createBitmap(96, 96, ARGB_8888).eraseColor(color)` — no HTTP base needed, same ARGB_8888 format Coil decodes to, distinct color per row forces real per-row GPU fill cost.

2. **LazyColumn for Console, not ListBlock** — `ListBlock` holds internal `rememberLazyListState`; the Console spike needs external `listState` for `scrollToItem(lines.size - 1)` auto-scroll. Used `LazyColumn(state = listState, ...)` directly, which is the correct pattern for bottom-pinned auto-scroll (mirrors ConsoleListView's `scrollToPosition` logic).

## Threat Surface Scan

No new network endpoints, auth paths, or file access patterns introduced. `BrowseSpikeActivity` is NOT exported (`android:exported="false"`), has no LAUNCHER intent-filter, and will be deleted in plan 25-07. T-25-01-01 fully mitigated.

## Self-Check

**Task 1 artifacts:**
- `app/src/main/java/works/mees/dinghy/dev/BrowseSpikeActivity.kt` — FOUND ✓
- Commit `b8738d8` — FOUND ✓

**Task 2 pending:** 25-SPIKE.md will be created at the human-verify checkpoint.

## Self-Check: PASSED (Task 1)

Task 2 (flox gfxinfo capture + 25-SPIKE.md verdict) is a `checkpoint:human-verify` gate — results to be filled in after the owner completes the on-device capture.
