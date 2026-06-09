# 22-05 SUMMARY — AndroidView interop hygiene (D-12, SC4)

**Plan:** 22-05 · **Wave:** 1 · **Status:** ✅ Complete · **Date:** 2026-06-08

## What was built

AndroidView interop hygiene — stop the Views-in-Compose hosts from forcing redundant
`invalidate()`/`onDraw` on every recomposition, plus two co-located allocation hoists (D-03).

**Task 1 (D-12) — equality-guard the host→View setters:**
- `GraphView.applyTokens`: added `private var lastTokens`; returns early when `t == lastTokens`
  before paint assignment + `invalidate()`.
- `WebcamView.applyTokens`: same `lastTokens` guard.
- `WebcamView.setTransform`: equality-guarded on all three inputs (flipH/flipV/rotation).
- `WebcamView.setChrome`: equality-guarded on all four inputs (mode/camName/serviceName/multiCam).
- `setFrame` / `GraphView.setData` / `setSetpoints` left unconditional (new data = new frame).
  Pre-fix, the `GraphViewHost`/`WebcamViewHost` `update` blocks called `applyTokens` unconditionally
  on every recomposition (→ full `onDraw`), forcing graph + webcam redraws on every 250 ms emission
  even when tokens were unchanged.

**Task 2 (D-03 opportunistic) — allocation hoists:**
- `WebcamView` draw helpers: cache `(lastText, lastWidth, lastTextSize)`; `measureText` only re-runs
  when the text or paint textSize changes (the latter handles an S/M/L `--fs` event).
- `FileRowsAdapter`: `SimpleDateFormat("MMM d, HH:mm", Locale.US)` hoisted to a file-level
  `private val FILE_DATE_FORMAT` (allocated once, reused per row; main-thread-only contract noted).
- `ConsoleRowsAdapter`: audited — clean, no per-bind allocation; no change needed.

## Key files

- **Modified:** `render/GraphView.kt`, `render/WebcamView.kt`, `ui/files/FileRowsAdapter.kt`
  (commit `d6ef7d8`).
- `render/GraphViewHost.kt`, `render/WebcamViewHost.kt`, `ui/console/ConsoleListView.kt`,
  `ui/console/ConsoleRowsAdapter.kt` — read/audited; no changes required.

## Verification

**Code (executor):**
- ✅ `:app:assembleRelease` BUILD SUCCESSFUL (exit 0) — built the merged Wave-1 tree (22-02 + 22-03 + 22-05).
- ✅ `:app:testReleaseUnitTest` BUILD SUCCESSFUL (exit 0).
- ✅ A1 leak audit: GraphView/WebcamView are invalidate-driven (no Handler/Runnable/observer survives
  `onDetachedFromWindow`); the Console RecyclerView path registers no adapter observer/scroll
  listener/Handler outside RecyclerView's own recycling — all three surfaces code-confirmed leak-free.

**SC4 — on-device measurement on flox (orchestrator-driven, owner-approved 2026-06-08):**
- **Leak/GC: PASS.** 24 enter/exit cycles across Temperature graph, Console RecyclerView, and Webcam
  (decoder setup/teardown). TOTAL PSS (via `dumpsys meminfo <pid>`): baseline 82.6 MB → cycle band
  91–99 MB with a **down-step on GC every round** (97.8→95.5 after more Console cycles; 99.7→96.8 on
  settle) → **final settled 60.3 MB, BELOW baseline**. No monotonic climb. The earlier 90s-MB band
  was transient mid-print graphics buffers, reclaimed once the print completed. The D-12 equality
  guards introduced zero retention. (GC logcat count 0 only because ART verbose-GC logging is off on
  release; the PSS reclaim is the real GC evidence.)
- **Dark↔light recolor regression: PASS.** Toggling Dark→Light, the GraphView chrome fully recolored
  (black bg → light bg, light-mode trace fills); Settings + drawer recolored too. Proves the
  equality guard short-circuits ONLY on equal tokens — a genuine token change still invalidates and
  recolors. WebcamView shares the identical `lastTokens` guard. Theme reverted to Dark after the test.

## Self-Check: PASSED

Commit: `d6ef7d8` (Tasks 1+2). SC4 owner-approved on flox. Build + host tests green.
