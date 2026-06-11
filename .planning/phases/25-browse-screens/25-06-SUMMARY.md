---
phase: 25-browse-screens
plan: "06"
subsystem: ui
tags: [webcam, crash-fix, wr-02, token-conformance, footbuttonbar, preview, appshell]
dependency_graph:
  requires:
    - phase: 25-browse-screens
      plan: "02"
      provides: "DinghyIcons.Back registered token"
    - phase: 25-browse-screens
      plan: "05"
      provides: "AppShell macro wiring (25-06 touches adjacent webcam region, not macro lines)"
  provides:
    - "WR-02 resolved: AppShell reads container.activeConfig (live Phase-14 source) for webcam URL resolution"
    - "WebcamScreen token-conformed (light touch): FootButtonBar Back, gutter=null, stringResource strings, rememberUnitGrid"
    - "Stateless WebcamScreen(vm, surfaceProvider, onBack) overload for preview"
    - "WebcamPreviews.kt @Preview matrix (9 @Nexus7Previews + 1 landscape = 19 renders)"
    - "25-WEBCAM-CRASH.md: crash diagnosis + on-device evidence (screen no longer crashes)"
tech_stack:
  added: []
  patterns:
    - "WR-02 pattern: container.activeConfig (Flow<ConnectionConfig?>) replaces write-dead connectionStore.config"
    - "Null-safe pattern: activeCfg?.host ?: '' for remember() keys when the source is nullable"
    - "D-16 light-touch conformance: rememberUnitGrid + FootButtonBar + gutter=null without restructuring picker logic"
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/WebcamPreviews.kt
    - .planning/phases/25-browse-screens/25-WEBCAM-CRASH.md
  modified:
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt
    - app/src/main/res/values/strings.xml
key_decisions:
  - "Crash root cause: Phase-21 49f3fe2 already fixed the crash (resolveWebcamUrl returns null on unparseable base); WR-02 stale-source read was still present — fixed in this plan"
  - "Stateless seam approach: extract private WebcamContent composable, both live and stateless overloads delegate to it"
  - "Preview feed surface: Media3SurfaceHost and WebcamViewHost already guard with LocalInspectionMode internally; no extra gating needed in WebcamPreviews"
requirements-completed: []
metrics:
  duration: "~45 min"
  completed_date: "2026-06-10"
  completed_tasks: 3
  total_tasks: 3
---

# Phase 25 Plan 06: Webcam Crash Fix + WR-02 + Token Conformance Summary

**One-liner:** WR-02 URL-resolver bug fixed (AppShell now reads the live container.activeConfig instead of write-dead connectionStore.config), WebcamScreen token-conformed with FootButtonBar and gutter=null (D-16 light touch, render path intact), and the full @Preview matrix shipped with a new stateless overload.

## Crash Root Cause (D-17)

The original crash (`IllegalArgumentException: Invalid URL host: ""`) was from Phase 15.2-06 (2026-06-05) and was **already fixed in Phase 21 commit `49f3fe2`** before this plan ran. On-device reproduction on flox confirmed: the Webcam screen opens cleanly, live feed renders, no crash.

**Original crash chain:**
1. `AppShell.kt` read `container.connectionStore.config` — write-dead since Phase 14
2. An empty-host fallback produced `ConnectionConfig(host = "")`
3. `resolveWebcamUrl` called `cfg.httpBase.toHttpUrl()` → `IllegalArgumentException: Invalid URL host: ""`
4. Phase-21 fix: changed to `toHttpUrlOrNull() ?: return null` (graceful degrade)

**WR-02 residual:** The Phase-21 fix was a defensive guard in `resolveWebcamUrl`, not a fix of the stale-source read. This plan fixes the root cause.

## What Was Built

### Task 1 — Crash diagnosis + on-device evidence (commit `5dc442b`)

- Installed current debug APK on flox, opened Webcam tile → no crash (screen works)
- Captured `adb logcat` confirming no `FATAL`/`AndroidRuntime` error from the app process
- Documented original stack trace (from Phase-21 forensics via git log + WebcamUrl.kt comments)
- Wrote `25-WEBCAM-CRASH.md` with full root-cause chain and Phase-21 fix summary
- Confirmed WR-02 stale-source read still present at plan start → Task-2 fix target

### Task 2 — WR-02 fix + token-conform WebcamScreen (commit `ec60a4e`)

**AppShell.kt (WR-02 fix — webcam region only):**
- Removed `val cfg by container.connectionStore.config.collectAsStateWithLifecycle(initialValue = null)` + `val activeCfg = cfg ?: ConnectionConfig(host = "")`
- Replaced with `val activeCfg by container.activeConfig.collectAsStateWithLifecycle(initialValue = null)`
- `activeCfg` is now `ConnectionConfig?` (nullable)
- Both `remember(store, activeCfg?.host ?: "", activeProfileId)` keys updated for null safety (Pitfall 7)
- Holder factory: `cfg = activeCfg ?: ConnectionConfig(host = "")` for idle-state fallback
- Only the webcam config region changed; macro lines from 25-05 untouched

**WebcamScreen.kt (D-16 light touch):**
- Added `val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))` inside `BoxWithConstraints`
- Moved `Back` from `gutter` lambda into `FootButtonBar(uDp = grid.uDp)` at foot of `field`
- `gutter = null` per redesign grammar (D-15)
- `OutlinedControl` uses `DinghyIcons.Back` token instead of raw string
- `stringResource(R.string.common_back)` for the Back label
- CamPicker strings use `stringResource` for `(unnamed)`, `unknown service`, `aspect %s`
- FeedFocus / Media3SurfaceHost / WebcamViewHost render path UNTOUCHED
- `DisposableEffect(holder){ holder.start(); onDispose{ holder.stop() } }` preserved verbatim

**strings.xml:** Added `webcam_cam_unnamed`, `webcam_service_unknown`, `webcam_aspect_format`

### Task 3 — Stateless seam + @Preview matrix (commit `b722d12`)

**WebcamScreen.kt:**
- Extracted `private fun WebcamContent(vm, surfaceProvider, onCycleCam, onSelectCam, onBack)` from live overload
- Both live and stateless overloads delegate to `WebcamContent`; all lifecycle stays in the live overload

**WebcamPreviews.kt (created):**
- 9 `@Nexus7Previews` (portrait + landscape each) + 1 explicit landscape `@Preview` = **19 renders**
- Fixture axis: single-cam (full-focus, no picker Field) + multi-cam (picker shown)
- Full 6-theme matrix: colorfulDark/Light, simpleDark/Light, highContrastDark/Light
- fs=L overflow shot via `fsLargeSeed`
- Landscape portrait-feed spot-check: 9:16 cam in landscape → `camera_feed` rule shows picker Field
- Feed surface preview-safe: `Media3SurfaceHost` and `WebcamViewHost` both guard with `LocalInspectionMode` internally (D-05) — no extra gating needed
- No `WebcamHolder`, no `collectAsStateWithLifecycle`, Moonraker-free

## Acceptance Criteria Results

| Criterion | Result |
|-----------|--------|
| `25-WEBCAM-CRASH.md` holds captured crash trace + diagnosis | PASS |
| `container.activeConfig` present in AppShell (WR-02 fix) | PASS (2 occurrences: in comment + code) |
| `connectionStore.config` removed from AppShell (non-comment) | PASS (0 occurrences in active code) |
| `activeCfg?.host` null-safe keys | PASS (3 occurrences) |
| `FootButtonBar` in WebcamScreen | PASS |
| `gutter = null` in WebcamScreen | PASS |
| `DisposableEffect(holder)` preserved | PASS |
| Render path (Media3SurfaceHost/WebcamViewHost) untouched | PASS |
| AppShell diff shows only webcam region changed | PASS |
| `WebcamPreviews.kt` exists with ≥6 @Preview + ≥1 landscape | PASS (19 renders total) |
| Feed surface uses LocalInspectionMode placeholder | PASS (hosts handle it internally) |
| No holder/collect in WebcamPreviews | PASS |
| `:app:assembleDebug` + `:app:testDebugUnitTest` exit 0 | PASS |

## Deviations from Plan

### Auto-applied (Rule 2 — Missing critical functionality)

**1. [Rule 2 - Missing functionality] Extract WebcamContent for clean two-overload seam**
- **Found during:** Task 3 — adding a stateless overload required factoring out the shared layout logic
- **Issue:** Without extraction, both overloads would duplicate the BoxWithConstraints + ScreenScaffold body
- **Fix:** Extracted `private fun WebcamContent(...)` that both overloads delegate to; this is standard phase-25 pattern (mirrors SpoolScreen, ConsoleScreen, FilesScreen)
- **Files modified:** WebcamScreen.kt

None — all D-16 scope constraints honored. FeedFocus/Media3SurfaceHost/WebcamViewHost, rung-select logic, cam-cycle logic, and DisposableEffect lifecycle are verbatim unchanged.

## Known Stubs

None. The stateless preview overload uses fake `WebcamVm` fixtures but is preview-only. The live overload is fully wired to `WebcamHolder.vm` (StateFlow).

## Threat Surface Scan

No new network endpoints, auth paths, or file access patterns introduced. The WR-02 fix (T-25-06-01) mitigated: `container.activeConfig` now feeds the webcam URL resolver, ensuring a non-empty host resolves to a valid URL or null (graceful degrade). D-16 scope (T-25-06-02) honored: render path untouched, no streaming regression risk.

## Self-Check: PASSED

- `25-WEBCAM-CRASH.md` — FOUND ✓
- `WebcamPreviews.kt` — FOUND ✓
- `AppShell.kt` — `container.activeConfig` present, `connectionStore.config` absent in active code ✓
- `WebcamScreen.kt` — FootButtonBar, gutter=null, DisposableEffect, FeedFocus all confirmed ✓
- Commit `5dc442b` (Task 1) — FOUND ✓
- Commit `ec60a4e` (Task 2) — FOUND ✓
- Commit `b722d12` (Task 3) — FOUND ✓
- `assembleDebug` + `testDebugUnitTest` GREEN ✓
