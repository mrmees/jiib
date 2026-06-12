---
phase: 27-motion-calibration
plan: "03"
subsystem: ui/move
tags: [move, jog-pad, z-column, distance-stepper, preview, jiib-redesign, BoxWithConstraints]
dependency_graph:
  requires:
    - "27-01 (DinghyIcons.BabystepExpand/BabystepCompress owner decision — Group B)"
    - "27-02 (nav spine compiles; no direct code dependency)"
  provides:
    - "Rebuilt MoveScreen: thin wrapper + stateless MoveContent"
    - "BoxWithConstraints portrait 60%-height pad cap (D-03)"
    - "Vertical ZColumn (D-01): Z+/readout+home/Z- with t.directional.z outline"
    - "Vertical DistanceStepperColumn (D-02): +/display/- cycling DISTANCES"
    - "MovePreviews.kt: @Preview matrix on stateless MoveContent"
  affects:
    - app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
    - app/src/main/java/works/mees/dinghy/preview/MovePreviews.kt
    - app/src/main/res/values/strings.xml
tech_stack:
  added: []
  patterns:
    - "ExtrudeScreen.kt:209 thin-wrapper + stateless Content seam (WARNING-5 fix)"
    - "BoxWithConstraints manual sizing for specialized-layout exemption (D-15)"
    - "ProbeCalibrateScreen vertical 3-cell column motif (all cells Modifier.weight(1f))"
    - "FootButtonBar inside layout (gutter = null)"
    - "DinghyIcons.BabystepExpand/Compress for distance stepper +/- cells (owner decision 27-01)"
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/MovePreviews.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
    - app/src/main/res/values/strings.xml
decisions:
  - "stateless MoveContent(vm, inFlight, ...) overload created for @Preview compatibility (WARNING-5 seam; ExtrudeScreen pattern)"
  - "BoxWithConstraints used instead of portraitFocusAspect for the specialized-layout exemption — more precise portrait cap control"
  - "padSize = (maxHeight * 0.60f).coerceAtMost(maxWidth) per D-03 owner steer"
  - "ZColumn HomeZCell: live Z readout + tap-to-home uses t.directional.z outline (both the JogPad Z corner and ZColumn center read vm.z — Pitfall 7 maintained)"
  - "DistanceStepperColumn uses DinghyIcons.BabystepExpand/Compress per 27-01 Group B owner decision"
  - "MoveFootButtons helper extracts the 3-button row shared by portrait + landscape layouts"
  - "Disable ConfirmGuard survives with destructive=true (T-27-03-01 mitigation)"
  - "Force-move toggle survives with C4 filled stop-red while armed (T-27-03-02 mitigation)"
metrics:
  duration: "~25 minutes"
  completed: "2026-06-12"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 3
---

# Phase 27 Plan 03: MoveScreen Rebuild Summary

**One-liner:** MoveScreen rebuilt onto jiib grammar with BoxWithConstraints portrait 60%-height cap, vertical Z column + distance stepper, stateless MoveContent seam, and @Preview matrix.

## What Was Built

### Task 1: Rebuilt MoveScreen (thin wrapper + stateless MoveContent)

**`app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt`** — complete rewrite:

- **Thin wrapper / stateless seam (WARNING-5):** `MoveScreen(container, holder, onBack)` reads the VM/dispatcher and calls `MoveContent(vm, inFlight, callbacks)`. A second `MoveScreen(vm, onBack)` stateless overload (the `@Preview` target) wires no-op callbacks with no AppContainer dependency.

- **`MoveContent` — BoxWithConstraints layout (D-03, specialized-layout exemption):**
  - Portrait: `padSize = (maxHeight * 0.60f).coerceAtMost(maxWidth)` — the JogPad renders at `padSize × padSize` centered; Z column + distance stepper fill the remaining height below; FootButtonBar at the bottom.
  - Landscape: JogPad fills `weight(1f).fillMaxHeight()` left half; right half is Column(Z+distance columns Row + FootButtonBar).

- **`ZColumn` (D-01):** 3 equal cells all `Modifier.weight(1f)` — Z+ JogColumnCell / HomeZCell (live z readout + tap-to-home, t.directional.z outline, same vm.z as JogPad Z corner per Pitfall 7) / Z- JogColumnCell. Force-move mode lifts the homed gate.

- **`DistanceStepperColumn` (D-02):** 3 equal cells all `Modifier.weight(1f)` — `+` DistanceStepCell / DistanceDisplay (Geist Mono, fsSp(22f)) / `−` DistanceStepCell. Cycles `DISTANCES` with `coerceAtMost(lastIndex)` / `coerceAtLeast(0)` bounds. Uses `DinghyIcons.BabystepExpand` / `DinghyIcons.BabystepCompress` per 27-01 Group B owner decision.

- **JogPad internals carry over unchanged (D-04):** home-XY center, axis-corner live readouts + tap-to-home, ForceMoveCell with C4 filled stop-red while armed. Same JogCell/HomeCell/AxisCorner/PadCell composables, only the chrome tokens updated.

- **gutter = null:** Home All / Disable / Back in `FootButtonBar` inside the layout. `MoveFootButtons` helper extracts the 3-button Row shared by both orientations.

- **Disable ConfirmGuard:** `destructive = true`, dispatches `CommandRegistry.disableSteppers` (T-27-03-01 mitigation).

- **No raw `MaterialSymbol(name=)` calls** in the rebuilt screen body. Status-shape glyphs still use `MaterialSymbol` internally via `StatusShape` (decorative, `clearAndSetSemantics`).

**`app/src/main/res/values/strings.xml`** — 9 new move_* strings added:
`move_z_up`, `move_z_down`, `move_z_home`, `move_distance_increase`, `move_distance_decrease`, `move_home_all`, `move_disable_steppers`, `move_disable_confirm`, `cd_move_jog_pad`.

### Task 2: MovePreviews matrix

**`app/src/main/java/works/mees/dinghy/preview/MovePreviews.kt`** (main source set):
- Portrait + landscape matrix: 4 explicit `@Preview` entries demonstrating the portrait 60%-cap (pad NOT full-bleed), unhomed amber indicators, partial-homed state.
- 6 theme combos via `@Nexus7Previews` + `PreviewBox(colorful/simple/highContrast × dark/light)`.
- `MoveFsLargeOverflowPortrait` + `MoveFsLargeOverflowLandscape`: fs=L via `fsLargeSeed` tuple (NOT `@Preview(fontScale=)` which is a NO-OP here).
- `MovePseudolocaleSpotCheck`: `locale = "en-XA"` for i18n completeness.
- Targets `MoveScreen(vm = ...)` stateless overload — no AppContainer/VM instantiated.
- 3 `MoveVm` fixtures: `moveHomed` (all axes homed, 125/125/5.2mm), `moveUnhomed` (null positions), `movePartialHomed` (XY homed, Z not).

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | `ba4a582` | feat(27-03): rebuild MoveScreen — thin wrapper + stateless MoveContent, BoxWithConstraints layout, vertical Z column + distance stepper, FootButtonBar |
| Task 2 | `ebc89cb` | feat(27-03): add MovePreviews matrix on stateless MoveContent (portrait/landscape + 6 themes + fs=L + pseudolocale) |

## Verification Results

- `:app:testDebugUnitTest --tests works.mees.dinghy.ui.move.*` — **BUILD SUCCESSFUL** (MoveHolderTest GREEN; no regressions)
- `:app:assembleDebug` — **BUILD SUCCESSFUL** (both tasks; previews in MAIN source set, assembleDebug IS the preview compile gate per SC-4)

### Acceptance criteria met

- `MoveScreen` + `MoveContent` both declared; `MoveContent` holds the BoxWithConstraints layout ✓
- `padSize = (maxHeight * 0.60f).coerceAtMost(maxWidth)` present in portrait branch ✓
- `ZColumn` and `DistanceStepperColumn` private composables exist; every cell uses `Modifier.weight(1f)`, no `weight(2f)` ✓
- DistanceStepperColumn cycles `DISTANCES` set with index coercion (no free entry) ✓
- Z column center cell (`HomeZCell`) reads live `zValue` (same `vm.z` as JogPad Z corner — Pitfall 7) ✓
- `gutter = null`; Home All / Disable / Back inside `FootButtonBar` ✓
- Zero raw `MaterialSymbol(name=` in MoveScreen ✓
- Disable ConfirmGuard (`destructive = true`) and force-move toggle survive ✓
- `MovePreviews.kt` in `app/src/main/java/works/mees/dinghy/preview/` targeting stateless `MoveScreen(vm=...)` ✓
- @Preview count ≥ 9 (15 annotation occurrences; @Nexus7Previews = 2 each) ✓
- assembleDebug BUILD SUCCESSFUL ✓

## Deviations from Plan

None — plan executed exactly as written. The `MoveFootButtons` private helper was added to DRY the shared 3-button Row between portrait and landscape layouts (a minor refactor within the single task, not a functional change).

## Known Stubs

None — MoveContent renders fully from `MoveVm` data; no placeholder text, no hardcoded empty collections flowing to UI rendering.

## Threat Flags

None. This plan touches only UI composition and string resources. The two destructive controls (Disable steppers, force-move) are guarded per the threat register (T-27-03-01: ConfirmGuard with destructive=true; T-27-03-02: ForceMoveCell C4 filled stop-red + lock-open/closed shape signal). No new network endpoints, auth paths, or data boundary surface was introduced.

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` exists ✓
- `app/src/main/java/works/mees/dinghy/preview/MovePreviews.kt` exists ✓
- `app/src/main/res/values/strings.xml` contains `move_z_up`, `move_home_all`, `cd_move_jog_pad` ✓
- Commit `ba4a582` exists ✓
- Commit `ebc89cb` exists ✓
