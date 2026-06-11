---
id: benchmark-harness-fairness-fixes
created: 2026-05-30
source: 01-REVIEW.md (CR-02, WR-04, WR-03)
priority: medium
resolves_phase: null
---

# Fix benchmark-harness fairness asymmetries before any re-run

The Phase-1 toolkit benchmark harness (`app/src/main/java/works/mees/dinghy/bench/`) has
rendering-path asymmetries between the Compose and Views scenes. They **handicapped Views**
(the winner), so the HYBRID verdict in `docs/adr/0001-ui-toolkit-decision.md` is direction-robust
and was NOT reversed. But if the harness is ever re-run (e.g. to confirm on stock-6 / API-23),
fix these first so the numbers are clean:

- **CR-02:** `BenchActivity.mountComposeScene()` pre-seeds its `StateFlow` from `replay().first()`
  then re-collects event 0 from `events()` with different window semantics (`takeLast` vs deque-trim);
  `mountViewsScene()` has no pre-seed. Early measured frames differ → breaks byte-identical fairness (D-02).
- **WR-04:** `ViewsBenchScene` `FilesAdapter.submit` uses `notifyDataSetChanged()` (re-binds/re-decodes
  all visible thumbnails every ~3 Hz tick) while Compose uses keyed diffing. Use `DiffUtil`. This is a
  harness asymmetry, not a real toolkit difference — and it made Views slower, yet Views still won.
- **WR-03 / leak:** `BenchImageLoader` pins an Activity context in a process-lifetime static (leak;
  biases a 5×-relaunch run). Use application context.
- Unbounded `SyntheticThumbnail.cache` (bound it).

Not blocking: the harness is throwaway measurement code and the verdict is recorded. Track only.

---
**Closed 2026-06-11 (26.5-02 R9 re-triage):** stale — the Phase-1 benchmark harness served its ADR-0001 decision purpose; perf gating moved to release-mode gfxinfo on-device (Phase 22 baseline method, ADR-0001 Addendum 2). Harness fairness fixes are moot unless the harness is revived.
