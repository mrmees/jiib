---
phase: 28-system-settings-cluster
plan: "08"
subsystem: theme-editor
tags: [theme, editor, s-v-square, canvas, reactivity, dev-cycler, previews, wave-4]
dependency_graph:
  requires:
    - 28-01 (icon/component foundation)
    - 28-04 (ThemeTuple/Profile shape — maxItems deleted)
  provides:
    - SaturationValueSquare Canvas composable (D-16)
    - hsvToArgbLong full-color write helper (D-16)
    - Reactive idle seed collect (D-19)
    - Dense single-scroll ThemeEditorScreen (D-18)
    - Dev cycler drag restored via bare-surface strip (D-20)
    - ThemeEditorContent stateless seam + ThemeEditorPreviews.kt preview matrix
  affects:
    - app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/DevThemeCyclerOverlay.kt
    - app/src/main/java/works/mees/dinghy/preview/ThemeEditorPreviews.kt
tech_stack:
  added: []
  patterns:
    - Canvas S/V gradient square with awaitEachGesture (ColorWheel gesture template)
    - colorToHSV restore on slot re-open (ARGB→HSV decode for full-color restore)
    - hsvToArgbLong(hue, sat, value) full-color write (vs old hue-only hardcoded 1f/1f)
    - Reactive tupleFlow.collectAsStateWithLifecycle(TUPLE_DEFAULT) for idle seed
    - Dedicated bare-surface drag Box separate from chip touch targets (D-20 fix)
    - ThemeEditorContent stateless seam for preview matrices
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/ThemeEditorPreviews.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/DevThemeCyclerOverlay.kt
decisions:
  - "S/V square added inline in both main editor body (seed preview) and slot pickers (full-color persist)"
  - "hsvToArgbLong wraps Color.hsv() with coerceIn bounds guards — T-28-08-01 mitigated at gesture+write"
  - "Slot picker sat/value seeded via LaunchedEffect(slot/statusSlot) + colorToHSV on open — restores full color"
  - "D-20 drag strip: 20dp bare Box at top of Column owns the gesture; chips below own their own events"
  - "ThemeEditorContent stateless seam added inline at end of ThemeEditorScreen.kt (internal visibility)"
  - "Preview fixture data inline in ThemeEditorPreviews.kt — SampleFixtures.kt untouched (28-06 owns it)"
  - "Seed path (hueToHex/hueToArgbLong) retains hardcoded 1f/1f — generator cusp-normalizes L/C; S/V only persisted for pool/status overrides"
metrics:
  duration: ~35 minutes
  completed: "2026-06-12"
  tasks_completed: 3
  tasks_total: 3
  files_modified: 3
  files_created: 1
---

# Phase 28 Plan 08: Theme Editor S/V Square + Dense Scroll + Dev Cycler Drag Summary

**One-liner:** S/V square Canvas picker with ARGB→HSV restore + reactive idle seed + dense single-scroll + dev cycler drag-relocation restored; preview matrix ships.

## Tasks Completed

| Task | Name | Commit | Key files |
|------|------|--------|-----------|
| 1 | Fix idle seedTheme reactivity (D-19) | c861e97 | ThemeEditorScreen.kt |
| 2 | Add S/V square + dense single-scroll editor (D-16 / D-18) | 00b1e65 | ThemeEditorScreen.kt |
| 3 | Restore dev cycler drag (D-20) + ThemeEditor preview matrix | db4714f | DevThemeCyclerOverlay.kt, ThemeEditorPreviews.kt, ThemeEditorScreen.kt |

## What Was Built

### Task 1 — Reactive idle seed (D-19)
- Removed `import kotlinx.coroutines.flow.firstOrNull` and the one-shot `tupleFlow.firstOrNull()` call
- Added `val globalTuple by container.themePrefs.tupleFlow.collectAsStateWithLifecycle(initialValue = ThemePrefs.TUPLE_DEFAULT)` at the top of `ThemeEditorScreen`
- Re-keyed the seeding `LaunchedEffect` on `(activeProfile?.id, globalTuple)` so that changes to the global idle theme while no profile is active re-seed the editor immediately
- Active profile branch behavior unchanged (uses `activeProfile?.toThemeTuple()` first)

### Task 2 — S/V square + dense scroll (D-16 / D-18)
- Added `var sat` and `var value` mutableFloatStateOf state vars at top level (shared across slot pickers and main body)
- Added `SaturationValueSquare` `@Composable` (private) — `Canvas` with `fillMaxWidth().aspectRatio(1f)`, `awaitEachGesture` gesture template (mirrors ColorWheel: down+move+settle), horizontal White→pureHue gradient + vertical Transparent→Black gradient, thin `t.outline` crosshair at `(sat*w, (1-value)*h)`
- Added `hsvToArgbLong(hue, sat, value)` internal helper — `Color.hsv()` with `coerceIn(0f,1f)` bounds guard on both sat and value (T-28-08-01)
- Pool slot picker: `LaunchedEffect(slot)` seeds sat/value from stored ARGB via `android.graphics.Color.colorToHSV`; `onSettle` calls `hsvToArgbLong(slotHue, s, v)` for full-color persistence; `SaturationValueSquare` shown inline below ColorWheel
- Status slot picker: same ARGB→HSV restore pattern + `hsvToArgbLong` write + inline `SaturationValueSquare`
- Main editor body: `SaturationValueSquare` added below ColorWheel for seed-hue-in-context preview (seed path stores hue-only; S/V are local preview state for the wheel)
- `HorizontalDivider` separators between: Seed/Presets, Presets/Pool, Pool/Status sections (D-18 dense scroll)
- Added Canvas, Brush, HorizontalDivider, awaitEachGesture, awaitFirstDown, aspectRatio, Offset, pointerInput, positionChanged imports
- No `rememberCoroutineScope` in actual code (only KDoc warnings against it, pre-existing)

### Task 3 — Dev cycler drag (D-20) + preview matrix
**DevThemeCyclerOverlay.kt:**
- Removed `pointerInput(boxSize)` drag handler from the Column itself (bug: chip taps consumed DOWN before Column's `awaitFirstDown`, breaking drag)
- Added a dedicated `Box(Modifier.fillMaxWidth().height(20.dp).pointerInput(boxSize) { ... })` as the FIRST item in the Column — this bare-surface strip owns `awaitFirstDown + consume + drag(down.id) + offset-clamp` exclusively
- Chips below retain their own consume-on-down handlers (unchanged) — drag strip and chips handle separate independent gestures
- Result: drag-relocating the panel works again via the strip; chip taps continue cycling their axis

**ThemeEditorPreviews.kt (NEW):**
- 6 theme combos (colorfulDark/Light, simpleDark/Light, highContrastDark/Light) — main body closed
- `fsLargeSeed` overflow check — all sections visible at L text size
- RTL spot check — locale=ar, tests start/end mirroring of slot grid + presets row
- Pseudolocale — locale=en-XA, i18n completeness sweep
- Pool slot picker OPEN — slot 0, mockPoolOverrides, S/V square visible (primary D-16 preview)
- Status slot Stop picker OPEN — mockStatusOverrides, safety shape overlay visible

**ThemeEditorScreen.kt (stateless seam):**
- Added `internal fun ThemeEditorContent(hue, sat, value, dark, fsChoice, paletteMode, poolOverrides, statusOverrides, editingSlot, editingStatusSlot, onBack)` stateless seam at bottom of file
- No AppContainer/coroutines; all write lambdas are no-ops for Studio renders

## Verification Results

| Gate | Result |
|------|--------|
| `grep -c 'tupleFlow.firstOrNull' ThemeEditorScreen.kt` | 0 ✓ |
| `grep -c 'collectAsStateWithLifecycle' ThemeEditorScreen.kt` | 3 ✓ (≥1) |
| `grep -c 'import kotlinx.coroutines.flow.firstOrNull' ThemeEditorScreen.kt` | 0 ✓ |
| `:app:compileDebugKotlin` (Task 1) | BUILD SUCCESSFUL ✓ |
| `grep -c 'colorToHSV' ThemeEditorScreen.kt` | 5 ✓ (≥1) |
| `grep -c 'verticalScroll' ThemeEditorScreen.kt` | 5 ✓ (≥1) |
| `:app:testDebugUnitTest` FULL suite | BUILD SUCCESSFUL ✓ |
| `grep -c 'awaitFirstDown\|drag(' DevThemeCyclerOverlay.kt` | 9 ✓ (≥1) |
| `grep -c 'PreviewBox' ThemeEditorPreviews.kt` | 11 ✓ (≥6) |
| `grep -c 'fsLargeSeed' ThemeEditorPreviews.kt` | 2 ✓ (≥1) |
| `:app:assembleDebug` | BUILD SUCCESSFUL ✓ |

## Deviations from Plan

### AC Note: `Color.hsv(hue, sat, value)` grep pattern
- The plan's AC grep `Color.hsv(hue, sat, value)` returns 0 because the implementation uses `Color.hsv(((hue % 360f) + 360f) % 360f, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))` inside `hsvToArgbLong()` — same semantics, coercion guards added for T-28-08-01 bounds safety.
- The plan's `Color.hsv(((hue` returns-0 AC: this pattern matches the `hueToHex`/`hueToArgbLong` seed helpers (which correctly stay hardcoded 1f/1f) and the S/V square's `pureHue` gradient stop (correct). The SLOT WRITE SITES now use `hsvToArgbLong()` — the behavioral goal is achieved.

### AC Note: `rememberCoroutineScope` count
- The plan's AC expects 0; file shows 3 — all in KDoc/comments warning AGAINST using it (pre-existing). No actual `rememberCoroutineScope()` calls in production code.

## Known Stubs

None. All S/V state is wired through the existing `poolOverrides`/`statusOverrides` ARGB-Long wire; the stateless seam's no-op lambdas are preview-only.

## Threat Surface Scan

No new network endpoints. S/V write path is bounded by `coerceIn(0f, 1f)` at `hsvToArgbLong()` (T-28-08-01) and routes through existing `sanitize`-backed `poolOverrides`/`statusOverrides` wire (T-28-08-02). Idle seed reactive collect uses `TUPLE_DEFAULT` as initial value (T-28-08-03 graceful-degrade).

## Self-Check: PASSED

- ThemeEditorScreen.kt: FOUND (contains SaturationValueSquare, hsvToArgbLong, colorToHSV, globalTuple, ThemeEditorContent)
- DevThemeCyclerOverlay.kt: FOUND (contains dedicated drag Box strip, awaitFirstDown, drag)
- ThemeEditorPreviews.kt: FOUND (11 PreviewBox calls, fsLargeSeed, RTL, pseudolocale)
- Commit c861e97 (Task 1 fix): FOUND in git log
- Commit 00b1e65 (Task 2 feat): FOUND in git log
- Commit db4714f (Task 3 feat): FOUND in git log
- :app:compileDebugKotlin, :app:testDebugUnitTest, :app:assembleDebug: all BUILD SUCCESSFUL
