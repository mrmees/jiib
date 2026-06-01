---
phase: 04-service-shell-settings-print-status-home
plan: 06
subsystem: print-status-home
tags: [ui, print-status, shell, e-stop, render, capability-fallback]
requires:
  - "PrinterStateStore (printerState StateFlow, 250ms conflation) — 02-04"
  - "RingBuffer (bounded rolling window, D-12) — 03-02"
  - "ProgressRing (Compose Canvas, D-11) — 03-05"
  - "ConfirmGuard / ScreenScaffold / OutlinedControl / SeverityToast — Phase 3"
  - "AppContainer.printerState + .dispatcher (CommandDispatcher) — 04-03"
  - "JsonRpcMethods.EMERGENCY_STOP — 04-02"
  - "PrinterState.klippyStateMessage / Capabilities — 04-05/02-02"
provides:
  - "PrintStatusHolder — toolkit-agnostic PrinterState → bounded ring + 2x3 grid with capability fallback"
  - "PrintStatusScreen — state-adaptive home (ring/temp-readout) + grid + reserved sparkline slot + wired E-stop"
  - "PrintStatusGrid / HeaterCell / ProgressCell / InfoCell model types"
affects:
  - "04-06b (sparkline GraphView fills the reserved Field slot; combined-render perf gate)"
  - "Shell routing (Dest.PrintStatus surface) — wired by the shell host"
tech-stack:
  added: []
  patterns:
    - "Holder mirrors PrinterStateStore StateFlow idiom; owns one primary-heater RingBuffer; consumes the already-throttled flow (no second throttle)"
    - "Explicit deterministic capability fallback (review #9): extruder-prefix primary, heater_bed-or-promote secondary, null placeholders never fabricated"
    - "Stop → full-screen ConfirmGuard → dispatcher.dispatch via per-session CommandDispatcher (never a raw transport request); klippy→shutdown routes to Splash automatically (D-10)"
key-files:
  created:
    - "app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusHolder.kt"
    - "app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt"
    - "app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusHolderTest.kt"
  modified: []
decisions:
  - "Grid exposes a flattened 6-slot `cells: List<PrintStatusCell?>` view (unfilled = null placeholder) PLUS typed primary/secondary/progress/info accessors — the screen renders '—' for null, the holder test asserts non-fabrication"
  - "Heater target of 0.0 surfaces as a null setpoint (cell shows CURRENT only) — matches Moonraker 'heater off' semantics, avoids a fake 0° target"
  - "Idle Focus is a 'Ready' + live nozzle/bed temp readout filling the region (D-08 'fill usable space'), explicitly NOT a 0% ProgressRing"
  - "DisabledTile (Tune/Pause, D-07) is an inert hairline-outline tile, not an OutlinedControl with a no-op — reads unambiguously disabled vs a tappable control"
metrics:
  duration_min: 5
  tasks: 2
  files: 3
  completed: 2026-06-01
---

# Phase 4 Plan 06: Print Status Home (part 1 of 2) Summary

State-adaptive Print Status home surface — a `ProgressRing`/temp-readout Focus, a fallback-hardened 2×3 stat grid, and a `ConfirmGuard`-gated emergency stop dispatched through the per-session `CommandDispatcher`.

## What Was Built

**Task 1 — `PrintStatusHolder` (+ host test).** A plain-Kotlin holder that collects the store's
already-throttled `printerState` (no second throttle) and exposes two `StateFlow`s: a bounded
primary-heater `RingBuffer` snapshot (`sparkline`, for the 04-06b GraphView) and a `PrintStatusGrid`
model. It implements the explicit review-#9 capability fallback:
1. primary = `extruder`, else the first `extruder`-prefixed heater;
2. secondary = `heater_bed`, else the bed slot is omitted and the next heater (e.g.
   `heater_generic chamber`) is promoted by its object name;
3. no bed + no other heater → no secondary slot (no fabricated 0°/—);
4. nonstandard heater names are surfaced verbatim;
5. fewer than 6 stats → remaining `cells` are `null` placeholders, never fabricated values.

`PrintStatusHolderTest` drives a real `PrinterStateStore` under an `UnconfinedTestDispatcher` and
covers every `<behavior>` case (latest-temp reflected, ring bounded ≤120 past capacity, standard /
no-bed / chamber-promote / nonstandard-name / under-6-stats placeholder, live StateFlow consumption).

**Task 2 — `PrintStatusScreen`.** A `ScreenScaffold`-based Compose surface, fully token-themed:
- **Focus (D-08):** `ProgressRing` in a sacred `aspectRatio(1f)` square while `Printing`/`Paused`;
  otherwise a "Ready" + live nozzle/bed temp readout filling the region (never a bare 0% ring).
- **Field:** the 2×3 GeistMono stat grid (`StatCell` renders `—` for null cells) plus a sized,
  positioned **reserved sparkline slot** (review #4) so 04-06b's GraphView is an additive fill.
- **Gutter (D-07):** greyed/disabled Tune + Pause placeholders and a wired red Stop tile. Stop raises
  the full-screen `ConfirmGuard`; confirming dispatches `printer.emergency_stop` via
  `container.dispatcher` (the per-session `CommandDispatcher`, review #1) — never a raw transport
  request. A `DispatchEvent.Failure` surfaces a `SeverityToast(Error, …)`.

## Verification Results

- `:app:testDebugUnitTest --tests …PrintStatusHolderTest` — GREEN (8/8 cases, including all
  capability-fallback cases and bounded-ring-past-120).
- `:app:compileDebugKotlin` — GREEN.
- Full `:app:testDebugUnitTest` — GREEN (no regressions).
- Greps: `RingBuffer`/`push(` present in holder; no `sample/debounce/delay`, no `androidx.compose`
  in the holder; `ProgressRing` + `Printing|Paused|aspectRatio` + `ConfirmGuard` +
  `EMERGENCY_STOP` present in the screen; `GraphViewHost`, `rpc.request`, raw `Color(`/`0x…`,
  and second-throttle calls all ABSENT.

## TDD Gate Compliance

Task 1 followed RED → GREEN: the failing `PrintStatusHolderTest` (compile-fail against the
not-yet-existent holder) was authored first to fix the holder's API, then the holder made it green.
Per project convention the RED test and its GREEN implementation were committed together as the
feature's first commit (`44a4db3`) rather than as a separate `test(...)` commit; the cycle order was
honored, the gate sequence is condensed into one feat commit. Task 2 is a Compose UI surface (no
`tdd="true"`), verified by compile + grep per the plan.

## Deviations from Plan

### Auto-fixed / clarified

**1. [Rule 3 - Blocking] Comment wording adjusted to keep negative-assertion greps clean.**
- Found during: Task 2 acceptance-grep verification.
- Issue: explanatory comments containing the literal tokens `GraphViewHost` and `rpc.request`
  tripped the "must return NOTHING" greps even though no such code existed.
- Fix: reworded the two comments ("the Views graph host", "a raw transport request") so the
  acceptance greps are unambiguous for the verifier; no behavior change.
- Files modified: PrintStatusScreen.kt
- Commit: caa23a2

Otherwise the plan executed as written. `container.printerState`/`container.dispatcher` are `Flow`
(not `StateFlow`), so the screen uses `collectAsStateWithLifecycle(initialValue = …)` — the idiomatic
read for the derived per-field flows AppContainer exposes (04-03).

## Known Stubs

- **Reserved sparkline slot** (`PrintStatusScreen.kt`, Field) — an intentionally empty sized `Box`.
  This is the planned 04-06b seam (the GraphView sparkline + combined-render perf gate), explicitly
  scoped out of this plan (review #4). Not a defect; documented in must_haves.
- **Tune / Pause gutter tiles** — inert `DisabledTile` placeholders (D-07), wired in Phase 5/7.
  Intentional, render as visibly disabled, no fabricated behavior.

## Notes for Next Plan (04-06b)

- Fill the reserved Field slot with `GraphViewHost(tokens, holder.sparkline-snapshot)` — the holder
  already owns the bounded primary-heater ring and exposes `sparkline: StateFlow<FloatArray>`.
- Run the Phase-3 two-part combined-render perf gate (ring + sparkline + grid) on flox per
  `03-PERF-RESULTS.md` re-open conditions.
- The on-device E-stop → Splash flow (D-10) is a manual UAT item (tablet currently away).

## Self-Check: PASSED

- Created files exist: PrintStatusHolder.kt, PrintStatusScreen.kt, PrintStatusHolderTest.kt, 04-06-SUMMARY.md
- Commits exist: 44a4db3 (holder + test), caa23a2 (screen)
