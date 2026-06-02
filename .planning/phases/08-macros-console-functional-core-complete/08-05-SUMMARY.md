---
phase: 08-macros-console-functional-core-complete
plan: 05
subsystem: ui
tags: [console, scrollback, recyclerview, views-in-compose, severity, filters, kotlin, compose]

# Dependency graph
requires:
  - phase: 08-01
    provides: RED ConsoleHolderTest scaffold (the GREEN target) + the FakeConsoleSource seam shape
  - phase: 08-02
    provides: ConsoleLine model + ConsoleSeverity.classify + ConsoleFilters.apply + ConsoleScrollback ring (the bounded backing store)
  - phase: 08-04
    provides: PrinterStateStore.gcodeResponses (live SharedFlow) + consoleBackfill (REPLACE StateFlow) — the two seams ConsoleHolder collects
  - phase: 07 (Files)
    provides: FileListView/FileRowsAdapter/FilesScreen — the Views-in-Compose scroll pattern (clipToBounds + pinned height) copied verbatim (D-05)
provides:
  - ConsoleHolder(scope, gcodeResponses, consoleBackfill) — collects live + backfill into a RAW bounded StateFlow<List<ConsoleLine>> (D-02/D-04)
  - ConsoleListView (RecyclerView-in-AndroidView, severity-colored, clipToBounds + MATCH_PARENT + itemAnimator=null, D-05)
  - ConsoleRowsAdapter — split update strategy: incremental notifyItemInserted live-append (S2) vs submitRows replace path
  - ConsoleScreen(holder, onBack, backfillFailed) — Field-only ScreenScaffold + 3 render-only filter toggles + green Back (D-01/D-03)
affects: [08-07 (AppShell wires Dest.Console -> ConsoleScreen + suppresses drawer swipe; provides the holder + backfillFailed flag)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Views-in-Compose scroll surface (3rd one): clipToBounds on AndroidView + pinned .height(maxHeight) + MATCH_PARENT + itemAnimator=null (D-05)"
    - "Split RecyclerView update strategy: per-line incremental notifyItemInserted/notifyItemRangeRemoved on the live hot path (S2) — full refresh reserved to backfill-replace + filter-toggle only"
    - "Stick-to-bottom timing (S3): capture wasAtBottom from the OLD item count BEFORE mutating the adapter; scroll only if so"
    - "View-side token bridge: Composable resolves role tokens (stop/heat/text/go/text3) to .toArgb() into a palette object handed down (Views can't read LocalTokens)"
    - "Filter-at-render (D-04): holder stores RAW; ConsoleScreen applies ConsoleFilters.apply in the render path so toggle-off re-reveals — raw stream never starved"
    - "Detached collector scope: a SupervisorJob child scope inheriting the parent dispatcher but NOT awaited by it — lets a non-terminating collector finish a TestScope-rooted holder cleanly while production cancellation still propagates"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/console/ConsoleHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/console/ConsoleListView.kt
    - app/src/main/java/works/mees/dinghy/ui/console/ConsoleRowsAdapter.kt
    - app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt
  modified: []

decisions:
  - "ConsoleHolder takes two SharedFlow args (not the store) — matches the RED test signature, decouples from PrinterStateStore, trivially fakeable. The store's consoleBackfill StateFlow IS a SharedFlow, so 08-07 wiring just passes store.gcodeResponses + store.consoleBackfill."
  - "Collectors run UNDISPATCHED in a detached SupervisorJob child scope (not the passed scope's awaited children). UNDISPATCHED closes the replay=0 subscribe race (a live line emitted before subscription would be dropped); the detached job means runTest(this) doesn't hang on the non-terminating collect. invokeOnCompletion cancels it with the parent for production lifecycle."
  - "ConsoleListView picks append-vs-replace by comparing the incoming list against the adapter's current contents (lines.size == old+1 && adapter holds lines.subList(0, old)) — single live line takes the incremental S2 path, everything else (backfill/filter-toggle) takes submitRows. No per-line DiffUtil."
  - "stackFromEnd = true on the LinearLayoutManager so a short scrollback sits at the bottom (terminal feel) and new lines grow up from there; combined with the S3 wasAtBottom scroll for the full-list case."
  - "ConsoleRowView is a plain TextView (not a LinearLayout) — a console line is just severity-colored mono text full-width. Geist Mono resolved via ResourcesCompat.getFont(R.font.geist_mono_medium) (the same res/font the Compose GeistMono FontFamily uses), MONOSPACE fallback."
  - "Filter-toggle glyphs: thermostat / videocam / chat_bubble; Back = arrow_back. All distinct (icon-no-repeat law). Note: 08-07 adds a 'terminal' Console drawer tile — none of these repeat it."
  - "No per-line timestamp rendered (planner discretion, D-02) — density over chrome; timeEpoch stays on the model for future use."

metrics:
  duration: ~30m
  completed: 2026-06-02
---

# Phase 8 Plan 05: Read-only Console Screen Summary

A read-only, severity-colored, reconnect-resilient Console screen (CONS-02 / D-01..D-05) built on the proven Views-in-Compose scroll pattern: `ConsoleHolder` folds the already-wired live `gcodeResponses` stream and the REPLACE-on-(re)connect `consoleBackfill` snapshot into a RAW bounded `StateFlow<List<ConsoleLine>>`, `ConsoleListView`/`ConsoleRowsAdapter` render it in a pinned RecyclerView with an incremental per-line append hot path, and `ConsoleScreen` applies the three opt-in noise filters at the view layer only with a green Back and no keyboard.

## What Was Built

- **Task 1 — `ConsoleHolder`** (`cf6b210`): collects both seams in `init`; classifies live lines via `ConsoleSeverity.classify` and pushes onto the 08-02 `ConsoleScrollback` ring; backfill emissions `replaceAll` the ring (D-02 disconnect-window recovery). Exposes the RAW snapshot (D-04). Turns `ConsoleHolderTest` GREEN (all 4 cases: live-append-classifies, backfill-replaces-then-live-accrues, ring-evicts-at-cap, raw-unfiltered).
- **Task 2 — `ConsoleListView` + `ConsoleRowsAdapter`** (`ec8b9b6`'s parent commit): `clipToBounds` + `MATCH_PARENT` + `itemAnimator = null` copied verbatim from `FileListView`; token→argb severity palette (stop/heat/text/go/text3); rows in Geist Mono, prefix stripped for display. Split update strategy (S2): `appendLine` = `notifyItemInserted` (+`notifyItemRangeRemoved` on eviction), `submitRows` reserved to backfill-replace + filter-toggle. Stick-to-bottom captures `wasAtBottom` from the OLD count BEFORE the update (S3).
- **Task 3 — `ConsoleScreen`**: `focus = null` Field-only `ScreenScaffold`; Field = pinned-height `ConsoleListView`; Gutter = 3 distinct-glyph filter toggles (active edge = accentLine, default OFF) + green Back. `ConsoleFilters.apply` runs at render only over the holder's RAW lines (D-04). Empty-state `Console is quiet` + backfill-failed `History unavailable` notices verbatim. No TextField/keyboard/send (D-01); token-pure.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] ConsoleHolder collectors hung the TestScope-rooted test**
- **Found during:** Task 1 (first two test runs)
- **Issue:** The RED test passes `this` (the `TestScope`) as the holder's scope, not `backgroundScope`. Launching the non-terminating `collect` coroutines as awaited children of that scope produced `UncompletedCoroutinesError` (runTest waits forever for them). Additionally the live `SharedFlow` has `replay = 0`, so a line emitted before the collector subscribed was dropped (the test emits before `advanceUntilIdle`).
- **Fix:** Run the collectors `start = CoroutineStart.UNDISPATCHED` (synchronous subscribe at construction, closes the race) inside a detached `SupervisorJob` child scope that inherits the parent dispatcher but is NOT awaited by the parent; `invokeOnCompletion` cancels it with the parent for production lifecycle parity.
- **Files modified:** `ConsoleHolder.kt`
- **Commit:** `cf6b210`

This is production-correct, not a test hack: the real-world holder is handed a long-lived scope and its collectors must run for the holder's lifetime and stop when that scope cancels — exactly what the detached-but-lifecycle-bound child scope gives, while also satisfying the TestScope contract.

## Threat Model Compliance

- **T-08-05-T (Tampering — rendering untrusted printer text):** mitigated. Lines render as plain text in a `TextView` (`ConsoleRowView`) with no HTML/markup/clickable-span surface; only the leading Klipper severity prefix is stripped for display (cosmetic), the raw form is preserved on the model. No injection surface.
- **T-08-05-I (Info disclosure):** accepted per plan (LAN-trusted, read-only monitor, no secrets in gcode responses).
- **T-08-05-SC (package installs):** zero new packages — composition over the existing pinned stack (`androidx.core.content.res.ResourcesCompat` and `recyclerview` were already on the classpath via the Files surface).

## Verification

- `:app:testReleaseUnitTest --tests *ConsoleHolderTest*` → **BUILD SUCCESSFUL** (ConsoleHolderTest GREEN). The still-RED sibling `MacroHolderTest` (08-06's target) was set aside for the test-source-set compile, then restored — tree is clean and the file matches its committed version.
- `:app:assembleRelease` → **BUILD SUCCESSFUL** (after Task 2 and again after Task 3).
- grep: no `TextField`/`BasicTextField`/`onSend`/`keyboard` in code (D-01); `ConsoleFilters.apply` in the render path (D-04); `clipToBounds` + `MATCH_PARENT` + `itemAnimator = null` + severity palette (`t.stop`/`t.heat`/`t.go`) present (D-05); `notifyItemInserted`/`notifyItemRangeRemoved` on the append path, `notifyDataSetChanged` only on `submitRows` (S2); `wasAtBottom` read before the update (S3); no raw `Color` literals in `ConsoleScreen` (token-pure).

## Known Stubs

None. The Console UI is functionally complete for this plan's scope. Nav wiring (`Dest.Console`, the drawer tile, drawer-swipe suppression, and passing `store.gcodeResponses`/`store.consoleBackfill` + a real `backfillFailed` flag into `ConsoleScreen`) is explicitly 08-07's job per the plan — `ConsoleScreen`/`ConsoleHolder` are ready to be constructed there.

## Notes for 08-07 (wiring)

- Construct: `ConsoleHolder(scope, store.gcodeResponses, store.consoleBackfill)`; pass it to `ConsoleScreen(holder, onBack = { goBack() }, backfillFailed = <derived>)`.
- Add `Dest.Console` to the shell `when(dest)` and suppress the swipe-up drawer on it (`dest !in setOf(Dest.Files, Dest.Console, ...)`).
- The drawer `Console` tile should use the `terminal` glyph (08-PATTERNS) — distinct from this screen's gutter glyphs (thermostat/videocam/chat_bubble/arrow_back).

## Self-Check: PASSED

- Created files: ConsoleHolder.kt, ConsoleListView.kt, ConsoleRowsAdapter.kt, ConsoleScreen.kt — all present.
- Commits: `cf6b210` (Task 1), `f66073c` (Task 2), `ec8b9b6` (Task 3) — all in git log.
- ConsoleHolderTest GREEN; `:app:assembleRelease` BUILD SUCCESSFUL; MacroHolderTest restored clean.
