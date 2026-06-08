---
phase: 19-output-controls-fans-lights-generic-pins
plan: 07
subsystem: ui
tags: [outputs, shell-wiring, drawer-tile, capability-gate, list-detail-backstack, selection-reset, drawer-suppression, icon-law, d-07, d-10, d-11, sc-1, sc-2]

# Dependency graph
requires:
  - phase: 19-04
    provides: AppContainer.outputsPresent (spine-scoped D-10 gate) + PrinterState.outputs/heaters live values + OutputDescriptor objectKey/commandName split
  - phase: 19-05
    provides: OutputsHolder(scope, store) + OutputsScreen(holder, onRowTap, onBack) live overload
  - phase: 19-06
    provides: OutputScrubberDetail / OutputLedDetail / OutputPinDetail entry points (container, holder, descriptor, …, onBack)
  - phase: 19-01
    provides: DinghyIcons.OutputSection owner-locked token (D-07)
provides:
  - Dest.Outputs top-level destination
  - live runtime-HIDDEN Output drawer tile (output glyph sourced from DinghyIcons.OutputSection token) + pure visibleDrawerTiles(...) helper
  - AppShell Dest.Outputs host: OutputsHolder assembly + list↔detail local back-stack + selection-reset on descriptor loss + drawer suppression
affects: [19-VALIDATION, on-device flox UAT (SC-1/SC-2 entry point)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "D-10 HIDE-not-grey divergence: a pure top-level visibleDrawerTiles(tiles, outputsEnabled) FILTERS the Output tile out when absent (vs the Webcam/Spool shown-but-greyed pattern) — host-testable without Compose"
    - "Drawer tile symbol SOURCED FROM the DinghyIcons.OutputSection token (OUTPUT_SYMBOL = (token.primary as IconRef.Ligature).name), never a hand-typed string — the icon-law glyph can't drift"
    - "Dest.Outputs hosts a LOCAL list↔detail back-stack (a single selectedOutputKey, null = list) — the Dest.Calibration hub↔routine idiom, NOT new top-level Dests"
    - "Selection reset: a LaunchedEffect keyed on the live row list nulls the selection when its objectKey disappears (removed/renamed/cleared-on-switch) — no dead detail page"
    - "OutputsHolder re-keyed on the live store (remember(store)) so a spine rebuild re-points it; selectedOutputKey re-keyed on the holder so a session swap also drops a stale selection"

key-files:
  created:
    - app/src/test/java/works/mees/dinghy/ui/shell/AppDrawerOutputsGateTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt

key-decisions:
  - "selectedOutputKey is SHELL-LOCAL (remember, like drawerOpen) — NOT hoisted onto ShellNavState: a per-output detail page is transient and need not survive a recovery Splash; re-keyed on outputsHolder so a session swap also resets it"
  - "Detail-page live-value seeding reads PrinterState.outputs/heaters directly (RAW 0..1 → display units the page expects): fan/pwm_tool .speed/.value ×100→%, heater_generic .target from heaters (single-sourced, 19-04), LED colorData[0], digital pin isOn = value>=0.5; servo seeds 0 (PWM-not-angle, SC-3 — dispatch-only)"
  - "DrawerTileSpec/DRAWER_TILES/visibleDrawerTiles promoted private→internal so the same-package host test reads .dest/.symbol without a Compose harness"
  - "Two compile-coupled task commits: adding the Dest.Outputs enum constant makes AppShell's when non-exhaustive, so Task 1 (enum+tile+helper+test) only fully compiles once Task 2 (AppShell host branch) lands — both in this execution; final HEAD is GREEN"

patterns-established:
  - "A capability-HIDDEN drawer tile = a pure visibleDrawerTiles filter on a runtime-enabled flag (the inverse of the Webcam/Spool runtime-GREY gate) — reusable for any future hide-not-grey tile"

requirements-completed: [SC-1, SC-2]

# Metrics
duration: ~15min
completed: 2026-06-08
---

# Phase 19 Plan 07: Outputs Shell Wiring (Dest + drawer tile + list↔detail host) Summary

**The Outputs feature reaches the shell (SC-1/SC-2 entry point): `Dest.Outputs` is added; the pre-stubbed greyed `bolt` placeholder flips to a LIVE Output drawer tile whose symbol is SOURCED FROM the owner-locked `DinghyIcons.OutputSection` token (D-07) and which is HIDDEN ENTIRELY when zero outputs (D-10, via a pure host-tested `visibleDrawerTiles(...)` helper — the deliberate divergence from the Webcam/Spool shown-but-greyed pattern); `AppShell` assembles the `OutputsHolder` re-keyed on the live store and hosts `Dest.Outputs` as a local list↔detail back-stack that routes each tapped output to the right 19-06 detail page by family/pwm, RESETS the selection when the descriptor disappears, pops detail→list on Back, and suppresses the swipe-up drawer; spine-scoped `outputsPresent` is threaded into the drawer as `outputsEnabled`.**

## Performance
- **Duration:** ~15 min
- **Tasks:** 2
- **Files modified:** 1 created + 3 modified

## Accomplishments

- **Task 1 — Dest.Outputs + token-sourced hidden-when-empty tile (D-07/D-10/D-11):** Added `Outputs` to the `Dest` enum. Flipped the greyed `DrawerTileSpec(label = "Output", symbol = "bolt", dest = null)` placeholder to a LIVE `DrawerTileSpec(label = "Output", symbol = OUTPUT_SYMBOL, dest = Dest.Outputs)` where `OUTPUT_SYMBOL = (DinghyIcons.OutputSection.primary as IconRef.Ligature).name` (= `output`) is SOURCED FROM the owner-locked token (D-07) so the icon-law glyph can never drift from a typo'd literal (`bolt` is freed). Added an `outputsEnabled: Boolean` param to `AppDrawer` and a PURE top-level `visibleDrawerTiles(tiles, outputsEnabled)` helper that FILTERS the Output tile out entirely when `!outputsEnabled` (D-10 HIDE-not-grey — the deliberate divergence from the Webcam/Spool grey gate; every other tile passes through untouched). `DrawerTileSpec`/`DRAWER_TILES`/`visibleDrawerTiles` were promoted `private`→`internal` so the same-package host test reaches `.dest`/`.symbol` with no Compose harness. `AppDrawerOutputsGateTest` (4 cases) proves: tile ABSENT when `outputsEnabled=false`; tile present + `dest=Dest.Outputs` + `symbol == output` when true; the `output` ligature equals `DinghyIcons.OutputSection`'s; and ONLY the Output tile is gated (no other tile dropped).

- **Task 2 — OutputsHolder assembly + Dest.Outputs list↔detail host + selection-reset + drawer suppression:** Assembled `OutputsHolder` via `remember(store) { OutputsHolder(scope, store) }` (re-keyed on spine rebuild, the calibration-holder precedent). Hosted `Dest.Outputs` as a LOCAL list↔detail back-stack — a single shell-local `selectedOutputKey` (`null` = the `OutputsScreen` list; non-null = that output's detail page), mirroring `Dest.Calibration`'s hub↔routine stack rather than five new top-level Dests. The list's `onRowTap` sets the key; each detail page's neutral Back (and a new `BackHandler` for system Back) pops to the list by clearing it. The selected descriptor is looked up in the live row list and routed by family/pwm to the right 19-06 page: `heater_generic`/`fan_generic`/`servo`/`pwm_tool` → `OutputScrubberDetail` (HEATER/FAN/SERVO/PWM_TOOL), every LED family → `OutputLedDetail`, `output_pin` → `OutputPinDetail` (which itself branches digital-toggle vs PWM-scrubber). Detail pages seed their live values off `PrinterState.outputs`/`heaters` (RAW 0..1 → the display units each page expects; heater single-sourced via `heaters`, servo seeds 0 since its PWM value is not an angle — SC-3). SELECTION RESET (review MEDIUM): a `LaunchedEffect` keyed on the live row list nulls the selection the moment its `objectKey` leaves the list (output removed/renamed, or descriptors cleared on a printer switch per 19-04) so the user is never stranded on a dead detail page. `Dest.Outputs` was added to the swipe-up drawer-suppression set (the list is a scroll Field; Back-only gutter — D-10). Spine-scoped `container.outputsPresent` is collected and threaded into the `AppDrawer(...)` call as `outputsEnabled`.

## Task Commits
1. **Task 1: Dest.Outputs + token-sourced tile + pure visibleDrawerTiles helper + gate test** — `cdae4f9` (feat)
2. **Task 2: OutputsHolder assembly + Dest.Outputs list↔detail host + selection-reset + drawer suppression** — `51e4f09` (feat)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` — added `Outputs` to the `Dest` enum
- `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` — `outputsEnabled` param + `OUTPUT_SYMBOL` (token-sourced) + pure `visibleDrawerTiles` helper + flipped Output tile (LIVE, hidden-when-empty); `DrawerTileSpec`/`DRAWER_TILES`/helper → internal
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — OutputsHolder assembly + `outputsEnabled` collect + `selectedOutputKey` local state + selection-reset LaunchedEffect + Dest.Outputs host branch (family/pwm routing) + Back handler + swipe-suppression + threaded `outputsEnabled` into AppDrawer
- `app/src/test/java/works/mees/dinghy/ui/shell/AppDrawerOutputsGateTest.kt` — created; 4 host cases over the pure `visibleDrawerTiles` helper

## Decisions Made
- `selectedOutputKey` is SHELL-LOCAL (`remember`, like `drawerOpen`), NOT hoisted onto `ShellNavState` — a per-output detail page is transient and need not survive a recovery Splash; it is re-keyed on `outputsHolder` so a session swap also drops a stale selection.
- Detail-page live-value seeding reads `PrinterState.outputs`/`heaters` directly (heater_generic single-sourced via `heaters` per 19-04; servo seeds 0 because its live `.value` is PWM not an angle — SC-3 dispatch-only).
- `DrawerTileSpec`/`DRAWER_TILES`/`visibleDrawerTiles` promoted `private`→`internal` for same-package host testing without a Compose harness.

## Deviations from Plan
None — both tasks executed as written. (Visibility promotions `private`→`internal` were the necessary, plan-anticipated host-testability seam for the pure helper.)

## Issues Encountered
- The Task-1-only state does not compile in isolation: adding the `Dest.Outputs` enum constant makes `AppShell`'s `when(dest)` non-exhaustive until Task 2 adds the host branch. This is inherent to additive-enum wiring; both tasks land in this execution and the FINAL HEAD compiles GREEN (full host suite passed). No `else` placeholder was added to keep the exhaustive-when safety net intact (a missing future Dest branch should still be a compile error, not a silent fall-through).

## Threat Surface Scan
No new security-relevant surface. T-19-07-01 (dead-end tile on a printer with no outputs) mitigated: pure `visibleDrawerTiles` filters the tile out when `!outputsPresent`, proven by `AppDrawerOutputsGateTest`. T-19-07-02 (detail strands user / drawer leaks) mitigated: local list↔detail back-stack with `BackHandler` popping detail→list, selection-reset on descriptor loss, `Dest.Outputs` in the suppression set. T-19-07-03 (stale `outputsPresent` after a switch) mitigated: `outputsPresent` is spine-scoped (`flatMapLatest` from the live store's `outputDescriptors`) so it idles to false on disconnect/switch.

## Verification
- `:app:testDebugUnitTest --tests *AppDrawerOutputsGateTest` — compiled within the full run (Task-1-only build is non-exhaustive until Task 2; see Issues).
- `:app:testDebugUnitTest` (full host suite) — **BUILD SUCCESSFUL** (whole module + test sourceset compiled, all classes incl. `AppDrawerOutputsGateTest` GREEN).
- grep: `Dest.Outputs` in `AppShell.kt` ≥ 2 (host branch + suppression set); `OutputSection` sourced in `AppDrawer.kt`; `symbol = "bolt"` gone for Output.
- No accidental file deletions across the two task commits (`git diff --diff-filter=D HEAD~2 HEAD` empty).

## Next Phase Readiness
- The full output flow now connects end-to-end: discovery (19-04) → holder + list (19-05) → detail pages (19-06) → shell entry (this plan). SC-1 ("see every output, and nothing it doesn't") closes at the entry point (tile hides when empty, routes when present); SC-2 (keyboard-free per-type control) is reachable via the list↔detail host.
- On-device flox UAT (drawer tile hide/show on a printer with/without outputs, list↔detail navigation, per-type dispatch + state-flip confirm, Adreno-320 no-jank) is the orchestrator/owner gate. Note from 19-06: the nested brightness `ScrubberPage` inside the LED scaffold may want a layout refinement on real hardware.
- No blockers.

## Self-Check: PASSED

Created file `AppDrawerOutputsGateTest.kt` verified on disk; both task commits (`cdae4f9`, `51e4f09`) verified in git log.

---
*Phase: 19-output-controls-fans-lights-generic-pins*
*Completed: 2026-06-08*
