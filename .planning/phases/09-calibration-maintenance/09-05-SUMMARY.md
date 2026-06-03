---
phase: 09-calibration-maintenance
plan: 05
subsystem: ui
tags: [compose, classic-views, canvas, heatmap, bed-mesh, z-tilt, qgl, moonraker, klipper, calibration]

# Dependency graph
requires:
  - phase: 09-02
    provides: calibration command spine (zTiltAdjust/quadGantryLevel/bedMeshCalibrate/bedMeshProfile*/saveConfig specs, sanitizeProfileName, live zTiltApplied/qglApplied/bedMesh objects)
  - phase: 09-03
    provides: BedMeshModel.from parser + tiltState pure state machine (the structured objects these screens render)
  - phase: 03
    provides: ScreenScaffold/ConfirmGuard/SeverityToast/MaterialSymbol/ThemeableView/GraphView discipline
provides:
  - "ConfirmGuard amber proceed-at-peril variant (reusable SAVE_CONFIG restart gate, D-12)"
  - "BedMeshHeatmapView + Host — the one new render surface (allocation-free Views Canvas heatmap, sibling of GraphView)"
  - "TiltHolder + TiltScreen (Z-tilt/QGL shared automatic flow, CALIB-03)"
  - "BedMeshHolder + BedMeshScreen (heatmap + scale toggle + profile save/load/remove, CALIB-04)"
  - "PrinterCommands.isValidProfileName (non-throwing allowlist gate for the keyboard Save dialog)"
affects: [09-06, 09-07, probe-calibrate, calibration-nav-wiring, on-device-uat, perf-gate]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "BedMeshHeatmapView: allocation-free 2D Canvas heatmap (sibling-not-fork of GraphView), token-driven red(stop)/blue(accent) ramp + density-scaled probe dots, scale-mode endpoints pure & host-testable"
    - "ConfirmGuard amber warn variant takes precedence over destructive (red/green API intact)"
    - "Holder reconstructs raw bed_mesh JSON from the reduced object → BedMeshModel.from (parse stays in ONE place, ScrewsTiltHolder pattern)"
    - "Save-name keyboard carve-out recorded in docs/ui_design/ as precedent"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapView.kt
    - app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapHost.kt
    - app/src/main/java/works/mees/dinghy/calibration/TiltHolder.kt
    - app/src/main/java/works/mees/dinghy/calibration/BedMeshHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/calibration/TiltScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt
    - app/src/main/res/drawable/bed_tilt.xml
    - app/src/test/java/works/mees/dinghy/calibration/TiltHolderTest.kt
    - app/src/test/java/works/mees/dinghy/calibration/BedMeshHolderTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/ConfirmGuard.kt
    - app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt
    - docs/ui_design/CLAUDE.md
    - img/bed_tilt.svg

key-decisions:
  - "bed_tilt.svg converted to a VectorDrawable (res/drawable/bed_tilt.xml) — the app has no Coil SVG decoder; SVGs are converted to tinted vectors (the nozzle.xml precedent). Lines/polylines rewritten as M…L… path data."
  - "Heatmap ramp blends through a neutral --surface-2 mid (LOW=accent → MID=surface-2 → HIGH=stop) so a flat mesh reads calm, not a saturated wash."
  - "BedMeshHolder clears a stale Failure ONLY on the printerState clean-mesh edge — NOT on the failure-fold path (a just-arrived rejection must surface over a still-populated prior mesh)."
  - "TiltHolder is parameterized by an applied-selector lambda ({zTiltApplied}|{qglApplied}) so ONE screen+holder serves both routines (D-02)."

patterns-established:
  - "Allocation-free 2D Canvas heatmap as a GraphView sibling (not a fork) — pre-allocated cellPaint/RectF, per-cell color re-set, no onDraw allocation, no anim loop"
  - "Amber proceed-at-peril ConfirmGuard variant for SAVE_CONFIG-class config-save+restart gates"

requirements-completed: []  # BEDM-01/BEDL-01 stay Pending until Phase-9 on-device UAT (per STATE/REQUIREMENTS); CALIB-03/04 are screen-built but UAT-gated.

# Metrics
duration: 40min
completed: 2026-06-03
---

# Phase 9 Plan 05: Z-Tilt/QGL + Bed-Mesh Heatmap + ConfirmGuard Summary

**Z-tilt/QGL shared automatic-flow screen (done from `applied`, failed from RpcError, no abort) + bed-mesh overhead red/blue heatmap with a user-cyclable color scale and full profile save(amber SAVE_CONFIG)/load/remove — built on a new allocation-free Views-Canvas heatmap sibling of GraphView and an amber ConfirmGuard variant.**

## Performance

- **Duration:** ~40 min
- **Started:** 2026-06-03T03:40:00Z (approx)
- **Completed:** 2026-06-03
- **Tasks:** 3
- **Files modified/created:** 13

## Accomplishments
- **ConfirmGuard amber variant** — a `warn: Boolean` proceed-at-peril variant (Intent.Warn + `--heat-soft` tint) that takes precedence over `destructive`; the reusable SAVE_CONFIG restart gate (D-12). Red/green API untouched.
- **BedMeshHeatmapView (+ Host)** — the ONE new render surface: an allocation-free classic-Views Canvas 2D heatmap, a deliberate SIBLING of GraphView (not a fork). Token-driven red(`--stop`)↔blue(`--accent`) ramp through a neutral mid, faint density-scaled `--text-3` probe dots; six scale modes (RELATIVE/PLATE/±0.10/±0.25/±0.50/±1.00) as pure color-mapping; ThemeableView recolor; NO animation loop. Host mirrors GraphViewHost (factory once, update pushes tokens+model+scaleMode).
- **TiltHolder + TiltScreen (CALIB-03)** — ONE shared automatic-flow code path for Z-tilt AND QGL (selector lambda picks the object, D-02). Done=`applied==true`, Failed=dispatcher RpcError ONLY (Pitfall 2), Running=dispatched-and-not-applied. bed_tilt Focus + question_exchange→data_table state overlay, Run(blue, dispatches `zTiltAdjust`|`quadGantryLevel`)/Back(green), NO Abort (D-11), homed gate with inline Home offer (D-13).
- **BedMeshHolder + BedMeshScreen (CALIB-04)** — heatmap Focus + top-left scale toggle (white/setting), 50/25/25 Activate/Save/Load field. Save → keyboard-editable name (pre-filled `YY.MM.DD_HH.MM`, `isValidProfileName`-gated) → `bedMeshProfileSave` → amber ConfirmGuard SAVE_CONFIG gate → `saveConfig`. Load = scrollable selector + per-row red Remove. Empty-state copy SEPARATE from saved profiles (Pitfall 4).
- **Save-name keyboard carve-out** recorded as precedent in `docs/ui_design/CLAUDE.md`.

## Task Commits

1. **Task 1: ConfirmGuard amber + BedMeshHeatmapView/Host** - `f4a74cf` (feat)
2. **Task 2: TiltHolder + TiltScreen** - `c8c9ffd` (feat, TDD)
3. **Task 3: BedMeshHolder + BedMeshScreen** - `86fe4d4` (feat, TDD)

_TDD tasks 2 & 3 landed test + impl together (holder green before/with the screen)._

## Files Created/Modified
- `render/BedMeshHeatmapView.kt` — NEW allocation-free Views Canvas heatmap (ThemeableView, scale modes, pure deviationToRamp/lerpArgb).
- `render/BedMeshHeatmapHost.kt` — GraphViewHost-shaped AndroidView consumer.
- `calibration/TiltHolder.kt` — headless Z-tilt/QGL VM folding applied + dispatched + Failure → tiltState.
- `calibration/BedMeshHolder.kt` — headless VM (BedMeshModel.from reconstructed JSON + cyclable scaleMode + folded Failure).
- `ui/calibration/TiltScreen.kt` — shared automatic-flow screen.
- `ui/calibration/BedMeshScreen.kt` — heatmap + scale toggle + Save/Load/Remove dialogs.
- `res/drawable/bed_tilt.xml` — Z-tilt/QGL Focus vector (from img/bed_tilt.svg).
- `designsystem/ConfirmGuard.kt` — amber warn variant.
- `command/PrinterCommands.kt` — `isValidProfileName` non-throwing allowlist gate.
- `docs/ui_design/CLAUDE.md` — Save-name keyboard carve-out precedent.
- `*Test.kt` — TiltHolderTest, BedMeshHolderTest (both GREEN).

## Heatmap scale-mode math (for the 09-07 perf gate)
- The mesh fill is `cols × rows` `drawRect` calls against ONE reused RectF + cellPaint (color re-set per cell, no allocation). The captured E5 `mesh_matrix` is 7×10; the worst case is a ~50×50 interpolated grid → 2500 filled rects + up to ~2500 probe-dot circles per draw. That is the Adreno-320 fill-rate suspect.
- Scale endpoints are computed ONCE per draw (a single min/max or |max| pass over the grid) — cheap, no per-cell branching beyond a lerp.
- **09-07 must re-measure heatmap fill on flox via gfxinfo** (the Phase-3/5 two-part liveness+latency gate). Levers if it stalls: cap the rendered cell count to pixel width (GraphView's downsample precedent), or pre-rasterize to a small Bitmap and scale. NOT done here — measure first.

## Decisions Made
- See `key-decisions` frontmatter. Notable: heatmap ramp blends through a neutral mid (flat mesh reads calm); BedMeshHolder clears stale failures only on the clean-mesh edge; bed_tilt.svg → VectorDrawable (no Coil SVG decoder, nozzle.xml precedent).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] bed_tilt.svg rendered as a VectorDrawable, not an SVG asset**
- **Found during:** Task 2 (TiltScreen Focus asset)
- **Issue:** The plan/UI-SPEC calls for `img/bed_tilt.svg` as the theme-tinted Focus, but the app has NO Coil SVG decoder — existing repo SVGs (nozzle.svg) are converted to tinted Android VectorDrawables (`res/drawable/nozzle.xml`) and rendered via `Icon(painterResource, tint=…)`. Referencing the raw `.svg` would not load.
- **Fix:** Converted `img/bed_tilt.svg` → `app/src/main/res/drawable/bed_tilt.xml` (lines/polylines rewritten as path data, stroke widths preserved, strokes white for runtime token tint). The committed `img/bed_tilt.svg` source is the design master.
- **Files modified:** app/src/main/res/drawable/bed_tilt.xml (new), img/bed_tilt.svg (committed)
- **Verification:** `:app:assembleRelease` SUCCESSFUL; TiltScreen renders `R.drawable.bed_tilt` token-tinted.
- **Committed in:** `f4a74cf` (Task 1 commit — drawable landed with the render task)

**2. [Rule 2 - Missing Critical] PrinterCommands.isValidProfileName added**
- **Found during:** Task 3 (Save dialog gating)
- **Issue:** `sanitizeProfileName` THROWS on invalid input; the keyboard-editable Save dialog needs a non-throwing validity check to disable Save (T-09-05-03 — never let an invalid name reach `bedMeshProfileSave`).
- **Fix:** Added `isValidProfileName(name): Boolean` (same allowlist contract) — the dialog gates its Save gutter on it; `bedMeshProfileSave` still sanitizes (defense in depth).
- **Files modified:** app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt
- **Verification:** Release compile; BedMeshScreen Save gutter disabled for blank/illegal names.
- **Committed in:** `86fe4d4` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical)
**Impact on plan:** Both essential to deliver the planned screens correctly. No scope creep — the drawable IS the planned Focus asset; the validator IS the planned T-09-05-03 mitigation.

## Issues Encountered
- BedMeshHolderTest's failure-fold test initially failed: `buildVm` cleared the just-arrived error because the active mesh is non-empty. Resolved by gating the "clear stale error" only to the printerState clean-mesh edge (`clearOnCleanMesh`), never the failure-fold or scale-cycle paths.
- `intentColor` overload clash: an `internal fun intentColor` already exists in the calibration UI package (09-04 CalibrationHubScreen). Removed the duplicate in TiltScreen and reused it.

## Verification
- `:app:assembleRelease` SUCCESSFUL.
- `TiltHolderTest` + `BedMeshHolderTest` GREEN (fully-qualified `--tests` names per the WSL→cmd.exe build-env note).
- Full `:app:testReleaseUnitTest` GREEN (per-wave merge gate).
- Token-purity grep clean (no raw `0xFF`/`Color(0x`) over the new render + screen files; heatmap `onDraw` allocation-free; no animation loop.

## Threat Surface Notes
- All this plan's threats (T-09-05-01 amber SAVE_CONFIG gate, T-09-05-02 homed pre-flight, T-09-05-03 name allowlist two-layer, T-09-05-04 object-not-ack done/failed, T-09-05-05 allocation-free fill) are mitigated as planned. No NEW security surface introduced.

## Next Phase Readiness
- Screens + holders are BUILT and host-tested but NOT yet nav-wired (Dest/AppDrawer/AppShell wiring is 09-07, not in this plan's scope).
- **09-07 must:** wire the calibration Dest/holders into AppShell (passing the session dispatcher.events to TiltHolder/BedMeshHolder), re-measure the heatmap Adreno-320 fill-rate via gfxinfo, and run on-device UAT (BEDM-01/BEDL-01/ZCAL-01 stay Pending until then).
- The SAVE_CONFIG re-handshake (G2 firmware-restart recovery) is the Phase-5 path; this plan emits the dispatch + amber guard, the re-handshake wiring is finalized in 09-07.

## Self-Check: PASSED

All 10 created files present on disk; all 3 task commits (`f4a74cf`, `c8c9ffd`, `86fe4d4`) present in git history.

---
*Phase: 09-calibration-maintenance*
*Completed: 2026-06-03*
