---
phase: 25-browse-screens
plan: "01"
subsystem: browse-screens
tags: [spike, gfxinfo, perf, files, console, compose, ADR-0001]
dependency_graph:
  requires: []
  provides: ["Files and Console Compose-vs-Views toolkit verdict (25-SPIKE.md)"]
  affects: ["25-03-PLAN.md (FilesScreen migration toolkit)", "25-04-PLAN.md (ConsoleScreen migration toolkit)"]
tech_stack:
  added: []
  patterns: ["BrowseSpikeActivity throwaway harness pattern (mirrors BenchActivity precedent)", "release-mode ADR-0001 gfxinfo perf gate"]
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/dev/BrowseSpikeActivity.kt
    - .planning/phases/25-browse-screens/25-SPIKE.md
  modified:
    - app/src/main/AndroidManifest.xml
decisions:
  - "Spike uses programmatically-generated ARGB_8888 Bitmap (not Coil) for thumbnail cost measurement — avoids requiring an HTTP base in a throwaway, while preserving per-row image draw cost (same Bitmap format Coil decodes to)"
  - "Console scene uses LazyColumn (not ListBlock) to allow externally-controlled listState for programmatic auto-scroll — ListBlock holds internal state; Console needs scroll control"
  - "Stable key for Console items: idx * 10007L + line.length — position-only collides during eviction; content-hash alone collides when lines repeat; combined form is unique across the live-churn ring"
  - "Files: Compose (migrate to ListBlock/ListRow) — p90 16.42ms vs Views baseline 41.95ms (-60%), ADR-0001 gate PASS"
  - "Console: Views (keep RecyclerView, conform visually) — p90 73.35ms vs Views baseline 9.26ms (~8x regression), ADR-0001 gate FAIL"
  - "Console pure-churn diagnostic capture confirms append/evict recomposition is the cost source (not scroll interaction)"
metrics:
  duration: "~393 min (checkpoint human-verify split plan across two sessions)"
  completed: "2026-06-10"
  completed_tasks: 2
  total_tasks: 2
---

# Phase 25 Plan 01: Compose Spike Harness for Files + Console — Summary

**One-liner:** Release-mode flox gfxinfo spike on literal Compose ListRow harness: Files migrates to Compose (p90 −60%), Console stays Views (~8× regression under live-churn).

## TOOLKIT VERDICTS (BINDING — 25-03 and 25-04 branch on these)

- **Files: Compose** (migrate to literal `ListBlock`/`ListRow`)
- **Console: Views** (keep RecyclerView/Views scrollback, conform visually)

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

## Tasks

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Build throwaway Compose spike harness (Files + Console) | `b8738d8` | `BrowseSpikeActivity.kt`, `AndroidManifest.xml` |
| 2 | Capture flox gfxinfo + write per-surface verdict | `98c9b70` | `25-SPIKE.md` |

## Spike Results

### Files Surface

| Capture | p50 | p90 | p95 | max | frozen |
|---------|-----|-----|-----|-----|--------|
| Compose spike (109 frames, 4 scroll sweeps, per-row bitmap) | 10.71 ms | **16.42 ms** | 19.89 ms | 34.05 ms | 0 |
| Phase-22 Views baseline | 8.91 ms | 41.95 ms | 42.96 ms | — | 0 |

ADR-0001 gate: 0 frozen ✓, p90 16.42 ms << 41.95 ms budget ✓ → **PASS → Files: Compose**

### Console Surface

| Capture | p50 | p90 | p95 | max | frozen |
|---------|-----|-----|-----|-----|--------|
| Compose (churn + scroll, 110 frames) | 42.67 ms | **73.35 ms** | 80.22 ms | 86.76 ms | 0 |
| Compose (pure churn, no scroll, 110 frames) | 46.00 ms | **74.94 ms** | 86.06 ms | 94.90 ms | 0 |
| Phase-22 Views baseline | 7.79 ms | 9.26 ms | 10.29 ms | — | 0 |

ADR-0001 gate: 0 frozen ✓, p90 73.35 ms >> 9.26 ms budget ✗ (~8× regression) → **FAIL → Console: Views**

## Deviations from Plan

### Auto-fixed Issues

None.

### Design decisions made during implementation

1. **Programmatic Bitmap instead of Coil** — The plan allowed "same-cost placeholder painter (decoded Bitmap of the same target dimensions)" as an alternative to Coil when wiring a real Coil load is impractical for a throwaway. Used `Bitmap.createBitmap(96, 96, ARGB_8888).eraseColor(color)` — no HTTP base needed, same ARGB_8888 format Coil decodes to, distinct color per row forces real per-row GPU fill cost.

2. **LazyColumn for Console, not ListBlock** — `ListBlock` holds internal `rememberLazyListState`; the Console spike needs external `listState` for `scrollToItem(lines.size - 1)` auto-scroll. Used `LazyColumn(state = listState, ...)` directly, which is the correct pattern for bottom-pinned auto-scroll (mirrors ConsoleListView's `scrollToPosition` logic).

## Threat Surface Scan

No new network endpoints, auth paths, or file access patterns introduced. `BrowseSpikeActivity` is NOT exported (`android:exported="false"`), has no LAUNCHER intent-filter, and will be deleted in plan 25-07. T-25-01-01 fully mitigated.

## adb Launch Note for 25-07 Teardown

`BrowseSpikeActivity` is `android:exported="false"`. Launch requires `adb root` before `adb shell am start`. 25-07 deletes the file and manifest entry; after deletion the launch sequence is no longer valid (intended).

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/dev/BrowseSpikeActivity.kt` — FOUND ✓
- `.planning/phases/25-browse-screens/25-SPIKE.md` — FOUND ✓
- Commit `b8738d8` (Task 1) — FOUND ✓
- Commit `98c9b70` (Task 2) — FOUND ✓
- Acceptance criteria grep: PRESENT ✓
