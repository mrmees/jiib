---
phase: 05-core-print-control-panels-temperature-move-extrude
plan: 07
subsystem: ui
tags: [extrude, retract, filament, can-extrude, cold-extrude-gate, macro-gating, tool-selector, dispatcher, theme-tokens]

# Dependency graph
requires:
  - phase: 05-01
    provides: HeaterState.canExtrude (per-tool cold-extrude gate) + Capabilities.hasMacroIgnoreCase + GCODE_SCRIPT
  - phase: 05-02
    provides: PrinterCommands.extrude/loadFilament/unloadFilament/selectTool/scriptParams
  - phase: 05-03
    provides: PrinterStateStore.minExtrudeTemp / maxExtrudeDistance one-shot StateFlows
  - phase: 05-06
    provides: Move template (MoveHolder/MoveScreen) — Extrude mirrors it (D-08, no mockup)
provides:
  - "ExtrudeHolder + ExtrudeVm: live per-tool can_extrude safety gate (fail-safe false) COMBINED with the one-shot min-extrude-temp hint + max-extrude-distance ceiling + tools list + case-insensitive load/unload macro presence"
  - "ExtrudeScreen: Move-style Extrude/Retract + distance/speed selectors + capability-gated T0/T1… tool selector + always-shown Load/Unload (missing-macro Info popup), all via GCODE_SCRIPT"
affects: [05-08-extrude-uat, shell-routing-to-Extrude-dest]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Holder COMBINEs the throttled printerState with the two one-shot capability StateFlows (deterministic hint/ceiling, not contingent on a status diff)"
    - "Per-tool live can_extrude is the authoritative safety gate; the static min_extrude_temp number is hint text only"
    - "Always-shown action + capability-gated dispatch-vs-informational-popup (Load/Unload, D-10)"
    - "Live max_extrude_only_distance disables over-ceiling distance steps; selected step falls back to the largest still-enabled value"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt
    - app/src/test/java/works/mees/dinghy/ui/extrude/ExtrudeHolderTest.kt
  modified: []

key-decisions:
  - "Tool index → heater name mapping is T0→\"extruder\", Ti→\"extruder<i>\" (Moonraker multi-extruder naming) so setActiveTool re-points the per-tool can_extrude gate to the live tool."
  - "Speed selector exposes 2/5/10 mm/s (default 5 → 300 mm/min) per RESEARCH §6; converted to mm/min at dispatch (PrinterCommands clamps again)."
  - "BigCommand/selector disable uses Modifier.alpha(0.4f) + no-op (mirrors MoveScreen's dim-disabled affordance) rather than a separate disabled control."

requirements-completed: [EXTR-01, EXTR-02, EXTR-03, EXTR-04]

# Metrics
duration: 9min
completed: 2026-06-01
---

# Phase 5 Plan 07: Extrude Panel Summary

**The Extrude panel (EXTR-01..04) — Move-style (no mockup, D-08): Extrude/Retract gated on the LIVE per-tool `can_extrude` boolean with a real-min-temp inline hint, a distance selector clamped to `max_extrude_only_distance`, a speed selector, always-shown Load/Unload that pops an informational toast when the macro is absent, and a T0/T1… tool selector shown only on multi-extruder printers — every action routed through the dispatcher as `printer.gcode.script`.**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-06-01T05:58:33Z
- **Completed:** 2026-06-01T06:07:00Z
- **Tasks:** 2
- **Files modified:** 3 (3 created)

## Accomplishments

**Task 1 — ExtrudeHolder (TDD):**
- `ExtrudeHolder` + `ExtrudeVm(canExtrude, minExtrudeTemp, maxExtrudeDistance, tools, showToolSelector, hasLoadMacro, hasUnloadMacro)`.
- `canExtrude = state.heaters[activeTool]?.canExtrude ?: false` — the PER-TOOL live safety gate, fail-safe false on a missing/unreported extruder (EXTR-04 / D-07 / T-05-07-Safety).
- COMBINEs `store.printerState` with `store.minExtrudeTemp` and `store.maxExtrudeDistance` (the 05-03 one-shot StateFlows) so the hint number and distance ceiling are deterministic the instant those handshake reads land — not contingent on a later `notify_status_update` diff.
- `tools = (0 until caps.extruderCount).map { "T$it" }`; `showToolSelector = caps.extruderCount > 1` (D-09).
- `hasLoadMacro`/`hasUnloadMacro` via `caps.hasMacroIgnoreCase("LOAD_FILAMENT"/"UNLOAD_FILAMENT")` — case-insensitive (Moonraker lowercases macro names, Pitfall 2 / D-10).
- `setActiveTool(name)` switches the gate's tool and re-resolves immediately off the latest known state.
- No second throttle (consumes the store's 250ms conflation, mirrors MoveHolder).
- RED→GREEN with committed gates: `a32586e` (test) → `1d37322` (feat). 9 host tests: can_extrude true/false/missing-extruder boundary, single vs multi extruder selector/tools, case-insensitive macro presence, missing-macro false, one-shot min-temp/max-distance surfaced, unset → null.

**Task 2 — ExtrudeScreen:**
- `ScreenScaffold` (Focus/Field/Gutter), mirroring `MoveScreen` plumbing (dispatcher + holder.vm collect, in-flight gating, one `script()` helper → `GCODE_SCRIPT` + `scriptParams`, failure-toast).
  - **Focus:** Extrude + Retract big accent commands → `extrude(±distance, speed)` (SAVE/M83/G1 E±/RESTORE, EXTR-01). When `vm.canExtrude == false` both render dim + no-op with an inline reason "Heat nozzle to `${min}°C` extrude" using the real `vm.minExtrudeTemp` when known, generic otherwise (EXTR-04 / D-07).
  - **Field:** a 6-up distance selector (1/5/10/25/50/100 mm) — any step above `vm.maxExtrudeDistance` is disabled (dim, no-op) when it is non-null; a `LaunchedEffect` falls the selected step back to the largest still-enabled value if the live ceiling makes it illegal. A speed selector (2/5/10 mm/s → mm/min). A `T0/T1…` row wrapped in `if (vm.showToolSelector)` → `selectTool(i)` + `holder.setActiveTool(...)` (EXTR-03 / D-09).
  - **Gutter:** Load + Unload (always shown) — present-macro → `loadFilament()`/`unloadFilament()`, absent → a `SeverityToast(Severity.Info)` informational popup (auto-dismissed like the failure toast, D-10); Back (`Intent.Danger`).
- Token-pure (no raw hex), GeistMono numerals, in-flight keys disable their control.
- Committed `46e8751` (feat).

## Task Commits

1. **Task 1: ExtrudeHolder (TDD)** — `a32586e` (test, RED) → `1d37322` (feat, GREEN)
2. **Task 2: ExtrudeScreen** — `46e8751` (feat)

**Plan metadata:** _(this commit)_ (docs: complete plan)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeHolder.kt` — per-tool can_extrude gate + tools/macro presence + combined one-shot hint/ceiling + setActiveTool.
- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt` — Move-style extrude/retract + distance(ceiling-clamped)/speed selectors + tool selector + load/unload Info-popup gutter.
- `app/src/test/java/works/mees/dinghy/ui/extrude/ExtrudeHolderTest.kt` — 9 host tests covering the gate, selector gating, macro presence, and one-shot reads.

## Decisions Made
- **Tool index → heater name:** T0 → `extruder`, Ti → `extruder<i>` (Moonraker's multi-extruder naming) so `setActiveTool` re-points the per-tool `can_extrude` gate to the selected live tool.
- **Speed steps:** 2/5/10 mm/s (default 5 → 300 mm/min) per RESEARCH §6; converted at dispatch (`PrinterCommands.extrude` clamps the feedrate again).
- **Dim-disabled affordance:** `Modifier.alpha(0.4f)` + no-op tap (mirrors MoveScreen) for the cold-extrude gate and over-ceiling distance steps — a disabled control, never a failed dispatch.

## Deviations from Plan

None - plan executed exactly as written.

## TDD Gate Compliance

Task 1 followed RED → GREEN with committed gates (`a32586e` test — compile-fail on missing `ExtrudeHolder`; `1d37322` feat). No unexpected RED-phase pass. No REFACTOR commit (landed clean). Task 2 was a non-TDD compile-gated UI task per the plan (`type="auto"`, no `tdd`).

## Issues Encountered
None.

## Known Stubs

None — the cold-extrude gate, distance ceiling, and macro presence are all wired to live `ExtrudeHolder.vm` data; every action dispatches a real `PrinterCommands` gcode through the session dispatcher. The tool selector is intentionally hidden (not stubbed) on single-extruder printers (D-09). Live cold→hot gate behavior, the missing-macro popup, and multi-tool gating are 05-08 UAT items (the single-extruder Ender 5 Plus proves the hidden-selector + cold-extrude paths).

## Threat Surface
No new endpoints beyond the planned threat model. All extrude/retract/tool/load taps cross the documented `User tap → dispatcher → Moonraker` boundary; mitigations applied as planned — live `can_extrude` fail-safe gate (T-05-07-Safety), all taps debounced/in-flight via the dispatcher (T-05-07-T), fixed distance/speed sets + `PrinterCommands` clamps + over-ceiling steps disabled (T-05-07-T2), dispatcher timeout clears in-flight (T-05-07-D).

## Next Phase Readiness
- The Extrude panel composes off the live per-session spine (holder built from the store like Move/PrintStatus); 05-08 UAT can route to it once the shell exposes the Extrude Dest.
- No blockers. Wave-3 sibling of the Move panel; shares the dispatcher + ScreenScaffold + token discipline.

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeHolder.kt` — FOUND
- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt` — FOUND
- `app/src/test/java/works/mees/dinghy/ui/extrude/ExtrudeHolderTest.kt` — FOUND
- Commits present: `a32586e` (test), `1d37322` (feat), `46e8751` (feat) — all in git log

---
*Phase: 05-core-print-control-panels-temperature-move-extrude*
*Completed: 2026-06-01*
