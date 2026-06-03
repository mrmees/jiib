---
phase: 09-calibration-maintenance
plan: 04
subsystem: ui
tags: [calibration, screws-tilt, compose, holder, stateflow, moonraker, screws_tilt_adjust]

# Dependency graph
requires:
  - phase: 09-02
    provides: "calibration command spine (screwsTiltCalculate gated on screws_tilt_adjust, G4 120s timeout), the five live calibration objects in PrinterState, the screwsTiltConfig one-shot StateFlow"
  - phase: 09-03
    provides: "parseScrewsTilt → GuidedLoopState (verbatim worst-screw by deviation-from-base, adjust clock string, sign, name join, X-of-N count); calibrationSupport(caps) supported-first routine list; CalibrationRoutine enum"
provides:
  - "ScrewsTiltHolder — headless StateFlow<ScrewsTiltVm> combining live results + screws config + dispatcher Failure"
  - "CalibrationHubHolder — headless StateFlow<List<RoutineEntry>> re-derived off capabilities"
  - "CalibrationHubScreen (CALIB-01/D-14) — the five-routine tile grid"
  - "ScrewsTiltScreen (CALIB-02 §2) — owner-authored to-scale bed + 20/40/40 point list, Run/Back one-shot"
affects: [09-05, 09-06, 09-07]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Reduced-model → raw-JSON reconstruction in the holder so the canonical pure parser (parseScrewsTilt) keeps sole ownership of worst-screw math — the screen renders GuidedLoopState verbatim, never re-ranks"
    - "Runtime-derived clock_loader_10 wedge bearing (atan2 center→screw + Y-flip + native-offset constant), no hardcoded per-screw angles"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/calibration/ScrewsTiltHolder.kt
    - app/src/main/java/works/mees/dinghy/calibration/CalibrationHubHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt
    - app/src/test/java/works/mees/dinghy/calibration/ScrewsTiltHolderTest.kt
  modified: []

key-decisions:
  - "Holder reconstructs the raw {\"screws_tilt_adjust\":{...}} JSON from the reduced ScrewsTiltObject + ScrewConfig and feeds parseScrewsTilt, rather than re-implementing the worst-screw selection over the reduced model — one source of truth for the math (honors prior-wave correction ef260cb)"
  - "To-scale bed extents derive from the screw-coord bounding box (with 18% margin), NOT from toolhead axis bounds (PrinterState carries no axis_minimum/maximum) — keeps the holder/screen self-contained and N-screw generic"
  - "Run is gated on homed (D-13); when unhomed the gutter swaps Run for an inline blue Home (G28) — never a disabled dead Run"
  - "ScrewsTiltHolder takes an optional events: SharedFlow<DispatchEvent>? + dispatchKey so Failure folding is host-testable and only THIS routine's rejections surface; the screen ALSO collects dispatcher.events directly (belt-and-braces, matches ExtrudeScreen)"

patterns-established:
  - "Calibration holders mirror ExtrudeHolder exactly: ctor(scope, store), combine(store flows) → StateFlow<Vm>, pure buildVm, no second throttle, no Compose annotations (ADR-0001)"
  - "Calibration screens mirror FilesScreen/ScreenScaffold: Focus/Field/Gutter slots, Intent→token color, dispatcher.dispatch(CommandRegistry.x, args), Failure → error SeverityToast (redacted, never e.message)"

# Metrics
metrics:
  duration: ~35 min
  tasks: 2
  files: 5
  completed: 2026-06-03
---

# Phase 9 Plan 04: Calibration Hub + Screws-Tilt Screen Summary

Wave-3 screen layer: the headless `ScrewsTiltHolder`/`CalibrationHubHolder` plus the `CalibrationHubScreen` (five-routine tile grid) and the owner-authored `ScrewsTiltScreen` (to-scale bed + 20/40/40 guided-loop point list), rendering the D-03 guided one-screw-at-a-time loop verbatim off the structured `screws_tilt_adjust` object — no console parsing, no re-ranking.

## What was built

**Task 1 — Headless holders (commit `da5b952`)**
- `ScrewsTiltHolder`: `combine(store.printerState, store.screwsTiltConfig)` → `ScrewsTiltVm`. It reconstructs the raw `{"screws_tilt_adjust":{...}}` results + config JSON from the already-reduced `ScrewsTiltObject` + `ScrewConfig` and feeds the canonical `parseScrewsTilt`, so the worst screw, its `adjust` clock string, `sign`, joined `name`, and the `X of N` count come straight from `GuidedLoopState` — the holder never re-derives "worst" (honors the prior-wave correction `ef260cb`). It joins config coords to each row by 1-based index for the to-scale bed (`ScrewPoint`), exposes a `homedGate` off `homed_axes` (D-13), and folds dispatcher `Failure` (by dispatch key) into `errorText`, clearing it on the next clean populated-results edge.
- `CalibrationHubHolder`: re-derives `calibrationSupport(caps)` on every capabilities change (supported-first, greyed-tappable last) so the hub refreshes live on reconnect.
- `ScrewsTiltHolderTest` (6 cases, GREEN): worst/count/name verbatim from a seeded 4-screw state; coord join by 1-based index; D-06 no-coords fallback; `homedGate` reflects `homed_axes`; `Failure` fold; unrelated-key ignored.

**Task 2 — Screens (commit `ca6543c`)**
- `CalibrationHubScreen` (CALIB-01/D-14): Field-only `ScreenScaffold`, 3-col `LazyVerticalGrid` of square `aspectRatio(1f)` tiles from the holder. Supported → accent outline + full text; unsupported → hairline outline + dimmed, BUT still tappable (owner override §1 — opens the page for inspection), sorted last. Five unique glyphs (`architecture` / `vertical_align_center` / `crop_square` / `grid_on` / `straighten`); single green `Back` gutter.
- `ScrewsTiltScreen` (CALIB-02 §2): Focus = the headline ("X of N in tolerance" + the worst screw's turn in Geist Mono Display, verbatim) over a to-scale bed (Compose `Canvas` + `MaterialSymbol` state glyphs at real coords; `clock_loader_10` wedge bearing derived at runtime via `atan2(center→screw)` with the Y-up→Y-down flip and a single calibrated native-offset constant — no hardcoded per-screw angles). D-06 graceful fallback: no coords → headline only. Field = a vertically-scrollable point list, each row ratio-split 20%/40%/40% (indicator / `hh:mm` turn / degrees, Mono) — generic N-screw (D-04), never assumes 3/4 rows. Gutter = `Run` (blue, `SCREWS_TILT_CALCULATE`) gated on homed with an inline blue `Home` when unhomed (D-13) + green `Back`; one-shot, nothing applied (D-05 — no Accept/Cancel). Dispatcher `Failure` → error `SeverityToast` with the redacted RpcError text (T-09-04-02).

## Verification

| Check | Result |
|-------|--------|
| `ScrewsTiltHolderTest` (FQN run) | GREEN (6 cases) |
| `:app:assembleRelease` | BUILD SUCCESSFUL |
| Full `:app:testReleaseUnitTest` (per-wave merge) | BUILD SUCCESSFUL (no regressions) |
| Holders contain no `import androidx.compose` | PASS (headless, ADR-0001) |
| No second throttle (`.sample(`/`.debounce(`) | PASS |
| `errorText` folded from `events` `Failure` | PASS |
| Token purity (`Color(0x` / `0xFF`) over both screens | ZERO hits |
| Five unique hub glyphs (no repeat) | PASS |
| Run dispatches `screwsTiltCalculate`; no Accept/Cancel | PASS |
| 20/40/40 `weight(...)` row split, no `.dp` on columns | PASS |

> Build note: the plan's `--tests '*ScrewsTiltHolderTest*'` glob returned "No tests found" because `cmd.exe` mangles the single-quoted glob over the WSL→Windows interop boundary; re-running with the fully-qualified class name (`--tests works.mees.dinghy.calibration.ScrewsTiltHolderTest`) is GREEN. Same tests, quoting artifact only.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing `androidx.compose.runtime.getValue` import**
- **Found during:** Task 2 (CalibrationHubScreen first compile)
- **Issue:** `val routines by holder.routines.collectAsStateWithLifecycle()` failed — the `by` delegate needs `getValue` in scope, which cascaded into `items(...)` resolving to the count-overload (lambda param typed `Int`).
- **Fix:** Added the `getValue` import; the delegate + grid `items(list)` then resolve correctly.
- **Files modified:** `CalibrationHubScreen.kt`
- **Commit:** `ca6543c`

### Design decisions worth recording (not deviations)

- **To-scale bed extents from screw coords, not toolhead bounds.** `PrinterState` carries no `axis_minimum`/`axis_maximum`, so the bed square is sized from the screw-coordinate bounding box (+18% margin). This keeps the screen self-contained and inherently N-screw generic; it also means a degenerate single-screw config can't define a span (guarded with a `max(span, 1.0)`).
- **Holder takes an optional `events` flow.** The screws-tilt screen collects `dispatcher.events` directly for its toast (matching `ExtrudeScreen`), but the holder ALSO accepts the events flow so `Failure` folding is host-testable and so a future caller can drive `errorText` purely off the holder. Both paths are wired; the screen prefers its own freshly-collected `failureText`, falling back to `vm.errorText`.

## Known Stubs

None that block the plan's goal. The `onNavigate(routine)` callback on the hub is an intentional seam — the hub→routine sub-routing is wired in **09-07** (per the pattern map). The to-scale-bed perf and the live guided-loop UX are on-device gates owned by **09-07** (full live-E5 UAT); this plan delivers the compiled, token-pure, host-tested screens.

## Requirements

Per STATE/REQUIREMENTS and the prior-wave note: **BEDL-01 / CALIB-01 / CALIB-02 are NOT marked Complete here** — they stay Pending/Planned until the Phase-9 on-device UAT (09-07) exercises the screens on the live Ender 5 Plus. This plan provides the implementation; the requirement closure is gated on the on-device gate.

## Self-Check: PASSED

- All five created files exist on disk.
- Both task commits (`da5b952`, `ca6543c`) are in `git log`.
