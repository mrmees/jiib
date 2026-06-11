---
phase: quick-260610-s8j
plan: 01
subsystem: ui/design-system
tags: [layout, sizing, scrubber, outputs, uat-conformance]
dependency_graph:
  requires: []
  provides: [UAT-1, UAT-2, UAT-3, UAT-4, UAT-5 conformance in design law + Phase-26 screens]
  affects: [ScrubberPage, OutputFocusControl, AdjusterPanel, ExtrudeScreen, TemperatureScreen, FineTuneScreen, LAYOUT.md, COMPONENTS.md, SKILL.md]
tech_stack:
  added: []
  patterns: [rememberUnitGrid + heightIn(max=uDp) for 1U height cap, uDp explicit threading (no CompositionLocal)]
key_files:
  created: []
  modified:
    - docs/ui_design/LAYOUT.md
    - docs/ui_design/COMPONENTS.md
    - .claude/skills/sketch-findings-dinghy-display/SKILL.md
    - app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt
    - app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
    - app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt
decisions:
  - "UAT-1 exception: dense list-pane icons stay at 22dp — explicit exception comments at both sites prevent future conformance sweeps from growing them"
  - "ScrubberControl uDp defaults to 80.dp (safe fallback) — callers that already have a grid always pass grid.uDp explicitly"
  - "OutputFocusControl refactored into shell (BoxWithConstraints + grid) + OutputFocusControlInner (receives uDp) to thread U without changing the public API"
  - "LedBrightnessControl weight(1f) replaced with heightIn(max=uDp) — same idiom as ScrubberControl; build-once/settle/clamp semantics untouched verbatim"
metrics:
  duration: "~35 minutes (Tasks 1+2 by prior executor; Task 3 this session)"
  completed: "2026-06-11T01:33:29Z"
  tasks_completed: 3
  files_modified: 9
---

# Phase quick-260610-s8j Plan 01: Post-Phase-26 UAT Formatting Rules Summary

**One-liner:** Five owner UAT formatting rules (icon sizing, alignment, scrubber/control 1U cap, e-stop reserve) codified as law in LAYOUT.md + SKILL.md and applied to six Phase-26 rebuilt components.

## What Was Built

### Task 1 (commit 5f0afe1) — Design Law + Skill

Added "Post-Phase-26 UAT formatting rules (owner, 2026-06-10)" section to `docs/ui_design/LAYOUT.md` with five numbered rules:

- **UAT-1**: Prominent icons ~70-80% of U; dense list-pane icons explicitly excepted
- **UAT-2**: icon · name start-aligned, value end-aligned with breathing room
- **UAT-3**: Current-style scrubber track ≤1U tall
- **UAT-4**: Focus top-left e-stop reserve — no useful content in that corner
- **UAT-5**: Controls cap at 1U height; LED ColorWheel is the sole named exception

`docs/ui_design/COMPONENTS.md` got cross-reference notes in §7 (scrubber ≤1U + ColorWheel exception) and near the e-stop / unit-U section (icon prominent + e-stop reserve). `SKILL.md` design-direction updated to point future UI work at all five rules for auto-load.

### Task 2 (commit fb6bb37) — Prominent Icon Sizing + Alignment (UAT-1/UAT-2)

- **AdjusterPanel.kt**: added `uDp: Dp` parameter; header icon now `(uDp * 0.5f).coerceIn(28.dp, 56.dp)` instead of hardcoded 24dp. Start/end alignment already satisfied; kept as-is.
- **FineTuneScreen.kt** + **TemperatureScreen.kt**: pass `uDp = grid.uDp` to `AdjusterPanel`; added `// UAT-1 exception: dense list-pane icon stays small` comment on 22dp `ListRow` leading icons in both files.
- **ExtrudeScreen.kt**: `BigCommand` glyph `fsSp(40f→56f)` (Focus hero tier); Spool placeholder `fsSp(34f→40f)` to match nozzle field-button prominence. No glyph changes.

### Task 3 (commit d84fb8c) — Scrubber + Output Control Height Cap (UAT-3/UAT-5)

- **ScrubberPage.kt / ScrubberControl**: added `uDp: Dp = 80.dp`; fill-bar `Box` changed from `.weight(1f)` to `.heightIn(max = uDp)`. `ScrubberPage` wrapper: `field` slot now wraps a `BoxWithConstraints` to derive `grid.uDp` and pass it down. `modifier` on the `ScrubberControl` call changed to `fillMaxWidth` (no longer `fillMaxSize` — the bar self-limits its height via `heightIn`).
- **OutputFocusControl.kt**: public `OutputFocusControl` now contains only a `BoxWithConstraints` shell that derives `grid` and calls private `OutputFocusControlInner(uDp = grid.uDp)`. `uDp` threaded through `FocusScrubberSurface`, `FocusLedSurface`, and `LedBrightnessControl`. `LedBrightnessControl` fill-bar `weight(1f)` → `heightIn(max = uDp)`.
- `ColorWheel` left **uncapped** with `// UAT-5 exception: LED ColorWheel may exceed 1U` comment.
- All `modifier = modifier` references in the inner function updated to `Modifier.fillMaxSize()` (outer `BoxWithConstraints` owns the surface).
- Build-once `workingState`/`remember(value, range)` seeds, `rememberUpdatedState`, clamp authority, busy-lock, `awaitEachGesture` blocks — **all untouched verbatim**.

## Verification

- `grep -q "heightIn(max = uDp)" ScrubberPage.kt` PASS
- `grep -q "UAT-5 exception" OutputFocusControl.kt` PASS
- `:app:testDebugUnitTest` BUILD SUCCESSFUL (all host tests including ScrubberMappingTest + OutputScrubberSettleTest green)
- `:app:assembleDebug` BUILD SUCCESSFUL
- `:app:compileDebugKotlin` PASS (Task 2 compile gate)

## Deviations from Plan

None — plan executed exactly as written.

The one non-obvious structural choice: rather than nesting `BoxWithConstraints` directly in the `when` block of `OutputFocusControl` (which would be awkward with the existing `collectAsStateWithLifecycle` / `LaunchedEffect` setup), I extracted `OutputFocusControlInner` to carry all the logic while keeping the public function as a thin `BoxWithConstraints` wrapper. This is cleaner than the plan's "wrap the when block" wording and produces the same result.

## Known Stubs

None. All changes are layout-only with no data stubs.

## Threat Flags

None. No new endpoints, auth paths, or trust boundaries introduced.

## Self-Check: PASSED

- `docs/ui_design/LAYOUT.md` exists with UAT rules: FOUND
- `docs/ui_design/COMPONENTS.md` exists with UAT cross-refs: FOUND
- `.claude/skills/sketch-findings-dinghy-display/SKILL.md` updated: FOUND
- `ScrubberPage.kt` has `heightIn(max = uDp)`: FOUND
- `OutputFocusControl.kt` has `UAT-5 exception` comment: FOUND
- Commit 5f0afe1 exists: FOUND (git log confirmed at start)
- Commit fb6bb37 exists: FOUND (git log confirmed at start)
- Commit d84fb8c exists: FOUND (just created)
