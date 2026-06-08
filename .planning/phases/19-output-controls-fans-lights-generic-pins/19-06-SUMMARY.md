---
phase: 19-output-controls-fans-lights-generic-pins
plan: 06
subsystem: ui
tags: [outputs, detail-pages, scrubber, led, color-wheel, output-pin, toggle, settle-dispatch, command-registry, sc-2, sc-3]

# Dependency graph
requires:
  - phase: 19-03
    provides: PrinterCommands output builders (setGenericFan/setLed/setServo*/setPin*) + clamp authority (outputPctToWire) + typed CommandSpecs (setGenericFan/setLed/setServo/setOutputPin) + hsvToRgb
  - phase: 19-05
    provides: OutputsHolder per-output markPending/clearPending busy lock + per-family reached() + OutputRowVm + OutputDescriptor (objectKey/commandName split)
  - phase: 17-05
    provides: the ExtrusionScreen dispatch + failure-toast + per-key busy precedent the detail pages mirror
provides:
  - ScrubberPage settle-dispatch mode (HIGH-3) — sealed ScrubberActions { ApplyCancel (default) | OnSettle(onSettle,onBack) }; OnSettle has NO Apply, dispatches ONCE on gesture-end/stepper
  - settleDispatchCount pure helper (ScrubPhase) — proves one-dispatch-per-settle host-side
  - OutputScrubberDetail — shared single-numeric detail (fan %, heater °C, servo °, pwm output_pin %, pwm_tool %) dispatching via CommandRegistry (HIGH-2) from bare commandName (HIGH-1), explicit Off (servo WIDTH=0), readOnly degrade
  - OutputLedDetail — hue ColorWheel + brightness scrubber + explicit Off (D-12), color_data[0] initial state + WHITE=0 policy + THEME-01 carve-out
  - OutputPinDetail — branches digital→OutputToggleControl / pwm→scrubber, both via setOutputPin
  - OutputToggleControl — the one new digital On/Off page
  - rgbToHsv inverse helper; PrinterCommands.clampHeaterTarget clamp authority
affects: [Wave-4 drawer tile + nav wiring (routes OutputRowVm tap → the right detail page), 19-VALIDATION]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ScrubberActions sealed-mode swap: one ScrubberPage serves both the Apply-commit grammar (Fine-Tune/Temperature) and the immediate-settle grammar (outputs) — HIGH-3"
    - "Settle-dispatch on gesture-END only (T-19-06-02): the awaitEachGesture loop calls settle() once after the last pointer-up, never per move frame; a stepper tap is itself a settle"
    - "Detail pages dispatch THROUGH the catalog (HIGH-2) built from the BARE commandName (HIGH-1) — never raw GCODE_SCRIPT, never the full objectKey"
    - "markPending fed the SAME clamped wire value the command sends (17-07): outputPctToWire for %, clampHeaterTarget for °C, clampServoAngle for °, brightest-channel for LED, 1.0/0.0 for digital"
    - "WHITE-CHANNEL POLICY (T-19-06-05): every LED color dispatch sends WHITE=0 so a stale white never lingers behind a chosen hue"
    - "Live container overload + stateless content seam (Extrusion/Spool precedent) so the @Preview matrix drives every page with no holder/socket"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputScrubberDetail.kt
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputLedDetail.kt
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputPinDetail.kt
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputToggleControl.kt
    - app/src/main/java/works/mees/dinghy/preview/OutputDetailPreviews.kt
    - app/src/test/java/works/mees/dinghy/ui/outputs/OutputScrubberSettleTest.kt
    - app/src/test/java/works/mees/dinghy/ui/outputs/OutputLedCommandTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt
    - app/src/main/java/works/mees/dinghy/designsystem/HsvToRgb.kt
    - app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt
    - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
    - app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt
    - app/src/main/res/values/strings.xml

key-decisions:
  - "ScrubberActions REPLACES the old onCancel/onApply/destructiveDismiss params (one sealed `actions` arg); both existing call sites (Temperature, Gallery) migrated to ScrubberActions.ApplyCancel in the same task so the module compiles"
  - "A ± stepper tap in OnSettle mode is itself a settle (ends a discrete adjustment) → dispatches once; the gesture-end (pointer-up) is the other settle point — no per-frame dispatch"
  - "One OutputScrubberType enum parameterizes the shared scrubber detail (range/unit/step + which CommandSpec + the dispatchKey for the busy lock); heater key = `set_<commandName>` (matches the setHeater spec's default key)"
  - "OutputScrubberDetail dispatchKey strings mirror the CommandRegistry spec keys exactly so the per-page busy lock reads the SAME inFlight key the dispatcher writes"
  - "Added PrinterCommands.clampHeaterTarget so the heater markPending optimistic flip uses the same clamp authority the setHeater TARGET does (17-07 single-source)"
  - "LED markPending target = the brightest channel max(r,g,b) — matches OutputsHolder.reached()'s LED confirm (color_data[0].max), so the optimistic flip releases on a real live match"
  - "rgbToHsv added next to hsvToRgb (dependency-free, host-tested) to seed the LED page hue+brightness from live color_data[0]; achromatic input parks hue at 0 (value still exact)"

patterns-established:
  - "Output detail page = live container overload (resolves dispatcher/inFlight/failure + builds dispatch side-effects, arms holder markPending/clearPending) delegating to a container-free stateless content seam the previews drive"

requirements-completed: [SC-2, SC-3]

# Metrics
duration: ~30min
completed: 2026-06-08
---

# Phase 19 Plan 06: Output Detail Pages (settle-dispatch scrubber + LED + toggle) Summary

**The per-type immediate-dispatch detail pages (SC-2/SC-3): `ScrubberPage` gains a concrete settle-dispatch mode (HIGH-3 — sealed `ScrubberActions` with a real `OnSettle` variant that has NO Apply and dispatches ONCE on settle), a shared `OutputScrubberDetail` serves the five single-numeric types, an `OutputLedDetail` pairs the hue `ColorWheel` with a brightness scrubber + explicit Off (D-12) with a live `color_data[0]` initial state + WHITE=0 policy + THEME-01 carve-out, and `OutputPinDetail` branches digital→`OutputToggleControl` / pwm→scrubber — every page dispatches THROUGH the `CommandRegistry` catalog (HIGH-2) built from the BARE `commandName` (HIGH-1), with no Apply, no confirm-guard step, toast-on-failure-stay, and a per-objectKey busy lock.**

## Performance
- **Duration:** ~30 min
- **Tasks:** 2
- **Files modified:** 6 modified + 7 created

## Accomplishments

- **Task 1 — ScrubberPage OnSettle mode (HIGH-3) + shared scrubber detail (`814eea3`):** `ScrubberPage` now takes a sealed `actions: ScrubberActions` (REPLACING the old `onCancel`/`onApply`/`destructiveDismiss` params). `ApplyCancel` preserves today's Cancel/Apply-commit grammar (both existing call sites — Temperature + Gallery — migrated in the same task); `OnSettle(onSettle, onBack)` is the NEW immediate-dispatch grammar: NO Apply button (gutter = stepper row + a single neutral Back), and the value dispatches via `onSettle(working)` EXACTLY ONCE when a gesture ends (the awaitEachGesture loop calls `settle()` after the last pointer-up) or a ± stepper is tapped — never per intermediate scrub frame (Adreno-320 budget, T-19-06-02). The settle decision is the pure `settleDispatchCount(List<ScrubPhase>)` helper, host-proven by `OutputScrubberSettleTest` (a tap = 1, a 20-move drag = 1, mid-drag = 0, two gestures = 2). `OutputScrubberDetail` (parameterized by `OutputScrubberType`: FAN/HEATER/SERVO/PWM_PIN/PWM_TOOL) reuses `ScrubberPage` OnSettle: it dispatches THROUGH the catalog (`dispatchCommand(CommandRegistry.setGenericFan/setHeater/setServo/setOutputPin, …)`, HIGH-2) built from the bare `descriptor.commandName` (HIGH-1), arms `holder.markPending(objectKey, clampedWire)` with the SAME clamped value the command sends (17-07), shows a `SeverityToast` + `clearPending` and STAYS on the page on failure (T-19-06-03), scopes busy to this objectKey, has an explicit Off (fan SPEED=0 / heater TARGET=0 / pwm VALUE=0 / **servo WIDTH=0** via `setServoDisable`), and degrades a read-only pin to value-only (SC-3). Added `PrinterCommands.clampHeaterTarget` as the heater clamp authority.

- **Task 2 — LED page + output_pin branch + toggle + previews (`94e3d45`):** `OutputLedDetail` pairs the hue-only `ColorWheel` with a brightness `ScrubberPage` (OnSettle, 0..100 %), v1 saturation fixed at 1.0. It SEEDS hue+brightness from the live `color_data[0]` via the new `rgbToHsv` inverse and shows that color in a literal-color swatch (THEME-01 carve-out). On settle (wheel OR brightness) it computes RGB via `hsvToRgb(hue, 1f, brightness/100f)` and dispatches `CommandRegistry.setLed(name, r, g, b, w = 0f)` — the WHITE-CHANNEL POLICY (every color dispatch sends WHITE=0, T-19-06-05) — built from the bare name; an explicit Off (D-12) dispatches all-zero immediately. `OutputPinDetail` branches on `descriptor.pwm`: PWM → the shared `OutputScrubberDetail` % path (`setOutputPin pwm=true`); digital → the NEW `OutputToggleControl` On/Off page (`setOutputPin pwm=false`, active-state accent, readOnly degrade). `OutputLedCommandTest` pins the exact SET_LED strings (hue 0/100% → `RED=1 GREEN=0 BLUE=0 WHITE=0`; hue 120/50% → `GREEN=0.5 … WHITE=0`; Off → all-zero), asserts WHITE=0 is ALWAYS present across a hue/brightness spread, asserts the bare name (HIGH-1), and round-trips `rgbToHsv(hsvToRgb(h,1,1))`. `OutputDetailPreviews` drives the stateless seams (LED 6-theme + fs=L, fan/heater scrubber, On/Off toggle, pseudolocale) with no live Moonraker.

## Task Commits
1. **Task 1: ScrubberPage OnSettle + shared scrubber detail** — `814eea3` (feat)
2. **Task 2: LED detail + output_pin toggle/scrubber branch** — `94e3d45` (feat)

## Files Created/Modified
- `app/.../designsystem/ScrubberPage.kt` — sealed `ScrubberActions` (ApplyCancel|OnSettle) + `settle()` at gesture-end + `settleDispatchCount`/`ScrubPhase` pure helper (HIGH-3)
- `app/.../designsystem/HsvToRgb.kt` — added `rgbToHsv` inverse (LED initial-state seed)
- `app/.../command/PrinterCommands.kt` — added `clampHeaterTarget` clamp authority
- `app/.../ui/outputs/OutputScrubberDetail.kt` — created; shared fan/heater/servo/pwm-pin/pwm_tool settle-dispatch page
- `app/.../ui/outputs/OutputLedDetail.kt` — created; hue+brightness+Off, color_data seed, WHITE=0, THEME-01 carve-out
- `app/.../ui/outputs/OutputPinDetail.kt` — created; digital/pwm branch
- `app/.../ui/outputs/OutputToggleControl.kt` — created; digital On/Off page
- `app/.../preview/OutputDetailPreviews.kt` — created; day-one detail preview matrix
- `app/.../ui/temperature/TemperatureScreen.kt`, `.../gallery/GalleryScreen.kt` — migrated ScrubberPage call sites to ScrubberActions.ApplyCancel
- `app/.../res/values/strings.xml` — added output_off / output_read_only / output_heater_current / output_led_brightness / output_on / cd_output_hue_wheel
- `app/src/test/.../OutputScrubberSettleTest.kt`, `.../OutputLedCommandTest.kt` — created (settle-once + LED command-gen/WHITE=0)

## Decisions Made
See frontmatter `key-decisions`. Headline: `ScrubberActions` replaces the old two-callback params with one sealed `actions` arg (both call sites migrated); a stepper tap is a settle; the per-page busy `dispatchKey` strings mirror the registry spec keys; LED markPending target = brightest channel (matches holder reached()).

## Deviations from Plan
None — plan executed as written. The shared scrubber `dispatchKey` derivation was added (not separately specced) so the per-page busy lock reads the exact `inFlight` key the dispatcher writes; `PrinterCommands.clampHeaterTarget` was extracted (Rule 2 — single-source clamp authority the heater markPending needs, consistent with the 19-03 clamp pattern).

## Issues Encountered
None. Both host suites GREEN on first full run; `:app:compileDebugKotlin` GREEN (ScrubberPage migration + 4 new pages + previews compile).

## Threat Surface Scan
No new security-relevant surface. T-19-06-02 (per-frame spam) mitigated: settle-dispatch on gesture-end only, proven by `OutputScrubberSettleTest`. T-19-06-03 (failure strands/wedges) mitigated: failure → toast + clearPending + stay, busy per-objectKey. T-19-06-04 (catalog bypass) mitigated: grep proves 0 `GCODE_SCRIPT` + `commandName` (not objectKey) on the command path across all detail pages. T-19-06-05 (stale white) mitigated: WHITE=0 on every color dispatch, asserted in `OutputLedCommandTest`.

## Verification
- `:app:testDebugUnitTest --tests *OutputScrubberSettleTest` — GREEN (one dispatch per settle, not per frame)
- `:app:testDebugUnitTest --tests *OutputLedCommandTest` — GREEN (exact SET_LED string, WHITE=0 always, bare name, rgbToHsv round-trip)
- `:app:testDebugUnitTest --tests *HsvToRgbTest` — GREEN (rgbToHsv addition no-regression)
- `:app:compileDebugKotlin` — BUILD SUCCESSFUL (ScrubberPage migration + detail pages + toggle + previews)
- grep gates: `dispatchCommand(CommandRegistry` ≥1 + `GCODE_SCRIPT` ==0 + `commandName` ≥1 + `ConfirmGuard` ==0 in OutputScrubberDetail; `hsvToRgb` ≥1 + `CommandRegistry.setLed` ==3 + `GCODE_SCRIPT` ==0 + `Color(0x` ==0 in OutputLedDetail; `ConfirmGuard` ==0 across LED/Pin/Toggle; `descriptor.pwm` branch in OutputPinDetail; `ScrubberActions` ≥1 in ScrubberPage

## Next Phase Readiness
- The Wave-4 drawer tile + nav plan routes an `OutputRowVm` tap to the right detail page by family: fan/heater/servo/pwm_tool/pwm-output_pin → `OutputScrubberDetail`/`OutputPinDetail`; led-families → `OutputLedDetail`; digital output_pin → `OutputPinDetail` (toggle branch). Each page takes `(container, holder, descriptor, …, onBack)`.
- On-device flox UAT (the SC-2 keyboard-free set + state-flip confirm + Adreno-320 no-jank gates) is the orchestrator/owner gate — the nested brightness ScrubberPage inside the LED scaffold may want a layout refinement on real hardware (project on-device-iteration norm).
- No blockers.

## Self-Check: PASSED

All 7 created files verified on disk; both task commits (`814eea3`, `94e3d45`) verified in git log.

---
*Phase: 19-output-controls-fans-lights-generic-pins*
*Completed: 2026-06-08*
