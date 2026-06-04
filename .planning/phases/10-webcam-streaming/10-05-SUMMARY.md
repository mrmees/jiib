---
phase: 10-webcam-streaming
plan: 05
subsystem: webcam
tags: [webcam, render, views, canvas, themeableview, androidview, adr-0001-hybrid, adreno-320, mjpeg]

# Dependency graph
requires:
  - phase: 10-02 (Webcam pure core)
    provides: Webcam model (flip_*/safeRotation), Rung enum, ResolvedWebcam VM shape the chrome renders from
provides:
  - WebcamView — ThemeableView custom-Canvas raster surface (pixel-square never-stretch frame + flip/rotation + rounded cutout + token chrome: D-03 badge / D-11 reconnect / D-04 dead-end / camera_feed cycle overlay)
  - WebcamViewHost — AndroidView factory-once/update-push host (GraphViewHost shape) the screen drives
affects: [10-06, 10-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Raster sibling of GraphView/BedMeshHeatmapView: ALL Paint/Path/Matrix/RectF pre-allocated in init, allocation-free onDraw (the Adreno-320 fill-rate floor / Pitfall-4 GC-churn trap), repaint via imperative setFrame/setChrome → invalidate (NO animation loop)"
    - "OPAQUE-BLACK-placeholder + push-tokens chrome colors (the BedMeshHeatmapView precedent): every color comes from applyTokens role tokens, zero raw hex (THEME-01 / TOKEN_PURE)"
    - "Pixel-square scale-to-FIT via one reused Matrix computed against the POST-rotation extent (90/270 swaps fitted W/H), flip applied as scale sign, centered — never-stretch in both orientations (camera_feed LOCKED contract)"
    - "Rounded framing cutout via a cached Path rebuilt on size/radius change (onSizeChanged + applyTokens), clipped in onDraw — edge loss accepted (camera_feed note)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/render/WebcamView.kt
    - app/src/main/java/works/mees/dinghy/render/WebcamViewHost.kt
  modified: []

key-decisions:
  - "WebcamView added a setTransform(flipH, flipV, rotation) seam (the flip_*/rotation must reach the View somewhere) and a second {0,90,180,270} guard mirroring Webcam.safeRotation — the Matrix can never be fed a nonsense angle even if a caller skips safeRotation"
  - "The cutout radius is the --r-card token (Dp→px via density), re-derived in applyTokens so a theme change that altered radii still tracks — matches the round-everything-else design law the camera_feed note reconciles with"
  - "Dead-end card draws title=--text + body=--text-2 (no red in-feed); the red Back intent lives on the SCREEN gutter (plan 10-06), keeping nothing destructive painted inside the frame"
  - "A backdrop fill (--bg-2) paints behind the frame so a portrait feed in a landscape cutout letterboxes cleanly instead of showing whatever was last in the buffer"
  - "Burst-mode glyph drawn as a 3-square offset stack (a 'multiple frames' affordance) in --accent-2 — a cheap allocation-free vector, no icon-font dependency added"

patterns-established:
  - "WebcamView/WebcamViewHost as the THIRD ThemeableView/AndroidView render pair (after GraphView + BedMeshHeatmapView) — the hybrid raster-surface recipe is now a proven trio"

requirements-completed: []  # CAM-01 is delivered across 10-02..10-08; this plan builds only the render surface + host

# Metrics
duration: ~5min
completed: 2026-06-04
---

# Phase 10 Plan 05: Webcam Render Surface (WebcamView + WebcamViewHost) Summary

**Built the only new render surface this phase — `WebcamView`, a classic-Views custom-`Canvas` `ThemeableView` raster sibling of `GraphView`/`BedMeshHeatmapView` that blits the current frame PIXEL-SQUARE / never-stretched in both orientations (Matrix scale-to-fit against the post-rotation extent + the cam's flip/rotation), clips to a rounded framing cutout, and paints all token-colored chrome (D-03 snapshot badge, D-11 dimmed-last-frame + "Reconnecting…" overlay, D-04 service-naming dead-end card, the camera_feed burst-glyph cycle overlay) — plus `WebcamViewHost`, the `AndroidView` factory-once/update-push host mirroring `GraphViewHost`. Every Paint/Path/Matrix/RectF is pre-allocated in init (allocation-free `onDraw` — the Adreno-320 fill-rate floor), there is no animation loop, and the chrome is TOKEN_PURE (zero raw color literals; all colors via `applyTokens` role tokens).**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-06-04T03:05:41Z
- **Completed:** 2026-06-04T03:10:02Z (approx)
- **Tasks:** 2
- **Files modified:** 2 (2 created, 0 modified)

## Accomplishments
- `render/WebcamView.kt` — `class WebcamView(context) : View(context), ThemeableView`. A `Mode` enum (Live / SnapshotFallback / Reconnecting / DeadEnd) drives the chrome. `setFrame(Bitmap?)` (decoder/poller thread → `postInvalidate`), `setTransform(flipH, flipV, rotation)`, and `setChrome(mode, camName, serviceName, multiCam)` are the imperative repaint triggers. `onDraw` clips to the cached rounded `cutoutPath`, paints the `--bg-2` backdrop, computes the pixel-square scale-to-fit + flip/rotation into the ONE reused `frameMatrix` (no allocation), blits the bitmap, then paints the active chrome: a persistent top-right "Snapshot ~2fps" pill (D-03), a dim scrim + centered "Reconnecting…" pill over the last frame (D-11), a centered dead-end card naming the detected service with NO browser button (D-04, tokened URL never drawn — T-10-12), and the bottom-left burst-glyph + cam-name cycle overlay when `multiCam` (camera_feed note). `applyTokens` derives every color from a role token and re-derives the `--r-card` cutout radius, then `invalidate()`s.
- `render/WebcamViewHost.kt` — `@Composable fun WebcamViewHost(tokens, frame, mode, camName, serviceName, multiCam, modifier, flipH, flipV, rotation)`: `factory = { WebcamView(ctx) }` (created once), `update` pushes `applyTokens` + `setTransform` + `setFrame` + `setChrome`. Thin host — no decode/poll logic (the holder/services own that, plan 10-06).

## Task Commits

1. **Task 1: WebcamView — pixel-square frame + token chrome (badge/reconnect/cycle/dead-end)** — `f3d5628` (feat)
2. **Task 2: WebcamViewHost — AndroidView factory-once/update-push** — `f9ae341` (feat)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/render/WebcamView.kt` (NEW) — the ThemeableView raster surface.
- `app/src/main/java/works/mees/dinghy/render/WebcamViewHost.kt` (NEW) — the AndroidView host.

## Verification
- `:app:compileReleaseKotlin` → BUILD SUCCESSFUL (both tasks).
- **TOKEN-PURITY GATE (Task 1):** the `Color(0x…` / `0x…ARGB` / `"#…"` grep over `render/WebcamView.kt` returns ZERO matches → prints `TOKEN_PURE`. All chrome colors come from the `applyTokens` role tokens (THEME-01 / UI-LAW "never raw color", the GraphView/BedMeshHeatmapView precedent).
- `:app:assembleRelease` → BUILD SUCCESSFUL (full release APK builds; R8/shrink pass green).
- `:app:compileDebugUnitTestKotlin` → BUILD SUCCESSFUL (the whole test source set still compiles — no regression).
- Holder scaffold `WebcamReconnectStateTest` remains a COMPILING RED `fail()` scaffold (3 `fail()` bodies, untouched) — it is plan 10-06's to replace; not disturbed here.
- All Paint/Path/Matrix/RectF allocated in init; `onDraw` allocates nothing (the transform is computed into the reused `frameMatrix`; the cutout path is rebuilt only on size/radius change in `onSizeChanged`/`applyTokens`). No animation loop (CLAUDE.md motion rule / Adreno-320 floor — T-10-11).

## Decisions Made
- **Added a `setTransform(flipH, flipV, rotation)` seam** — the plan's `setFrame`/`setChrome` cover the frame + overlay state, but the cam's `flip_*`/`rotation` (camera_feed "draw the flip/rotation") needs a path into the View; a dedicated setter keeps the host's `update` block one-call-per-concern (the GraphViewHost style). A second `{0,90,180,270}` guard mirrors `Webcam.safeRotation` so the Matrix can never receive a nonsense angle even if a caller forgets to pre-coerce.
- **Cutout radius = the `--r-card` token, re-derived in `applyTokens`** — the camera_feed note demands rounded edges "to ensure visual continuity… the square images of the feed will clash with our round edges everywhere else." Tying the radius to `--r-card` (not a hardcoded px) makes the cutout match the rest of the app's card rounding under any theme.
- **Nothing red painted inside the feed** — the dead-end card uses `--text`/`--text-2` (not `--stop`); the red Back intent is the SCREEN's gutter affordance (plan 10-06), keeping the in-feed surface free of any destructive-intent color.
- **A `--bg-2` backdrop behind the blit** — so a portrait feed inside a landscape cutout (or vice-versa) letterboxes onto the page surface tone instead of exposing stale buffer content; cheap single fill, inside the clip.
- **Burst glyph = a hand-drawn 3-square offset stack** in `--accent-2` — an allocation-free vector affordance, avoiding adding an icon-font/vector-asset dependency for one overlay glyph.

## Deviations from Plan

None — plan executed as written. The `setTransform` seam is an in-scope mechanical addition the plan's own `<action>` calls for ("applying the cam's `flip_horizontal`/`flip_vertical`/`rotation`") — the plan named `setFrame`/`setChrome` explicitly and left the transform hand-off shape to the implementation; a dedicated setter is the natural GraphViewHost-style choice. No auto-fix rules (1–3) triggered; no architectural decision (Rule 4) arose.

## Known Stubs

None. Both files are complete render code. The pixel-square/never-stretch + rounded-clip + flip/rotation are concrete and drawn; their final *visual* confirmation in both orientations is an on-device property whose verification home is plan 10-08 Task 2 (the human-verify UAT checklist explicitly names them) — that is the declared verification seam, not a stub. The View has no data source of its own (it renders what the host pushes); the host's `frame` is wired by the screen in plan 10-06 — a declared forward seam, not placeholder data.

## Threat Flags

None — this plan introduces no new network endpoint, auth path, or trust boundary. The two threats in the plan's `<threat_model>` are honored: T-10-11 (fill-rate DoS) is mitigated by the allocation-free `onDraw` + no-animation-loop discipline (the on-device perf gate, plan 10-08, pins it); T-10-12 (info disclosure) is honored — the dead-end card draws ONLY the non-secret `serviceName`, never the tokened URL.

## Next Phase Readiness
- The render surface + host are in place and the release APK builds. Plan 10-06 (the holder + screen) can host `WebcamViewHost`, collect the decoder's drop-behind frame flow into its `frame` param, and drive `mode`/`camName`/`serviceName`/`multiCam` from the resolved rung + reconnect state machine — and replace the `WebcamReconnectStateTest` scaffold.
- The test source set still compiles; the holder scaffold is the only webcam RED left for its own wave.
- No blockers.

## Self-Check: PASSED

Both created files verified present on disk (`WebcamView.kt`, `WebcamViewHost.kt`); both task commits (`f3d5628`, `f9ae341`) verified in git log; `:app:assembleRelease` + `:app:compileDebugUnitTestKotlin` both BUILD SUCCESSFUL; TOKEN_PURE confirmed; the reconnect scaffold verified still-RED (3 `fail()` bodies).

---
*Phase: 10-webcam-streaming*
*Completed: 2026-06-04*
