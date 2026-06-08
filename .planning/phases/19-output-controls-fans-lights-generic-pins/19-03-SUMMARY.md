---
phase: 19-output-controls-fans-lights-generic-pins
plan: 03
subsystem: command
tags: [moonraker, gcode, outputs, clamp-authority, command-registry, hsv, led, servo, fan, output-pin]

# Dependency graph
requires:
  - phase: 19-02
    provides: PrinterCommandsOutputsTest RED scaffold + real E5P/E3P fixtures the builder strings are verified against
provides:
  - PrinterCommands generic-output builders (setGenericFan/setLed/setServoAngle/setServoDisable/setPinDigital/setPinPwm) — clamp-before-format, built from the BARE section name (HIGH-1)
  - output-pct clamp authority (clampOutputPct/clampServoAngle/outputPctToWire) so a markPending wire target equals the dispatched value (17-07 invariant)
  - typed output CommandSpecs in CommandRegistry.all (setGenericFan/setLed/setServo/setOutputPin) dispatched through the catalog (HIGH-2)
  - hsvToRgb pure helper (fixed-saturation v1) for the LED page
affects: [19-04 descriptor discovery + holder, 19-05/06 output detail pages (LED/servo/fan/pin), 19-VALIDATION]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single-source clamp authority (17-07): builders + markPending share one clamp output via outputPctToWire"
    - "BARE section name on the wire (HIGH-1): name interpolated directly after FAN=/LED=/PIN=/SERVO=, family prefix never reaches Klipper"
    - "Output gcode routed through registered CommandSpecs (HIGH-2), never raw GCODE_SCRIPT — the ExtrusionScreen/Fine-Tune precedent"
    - "Registry delegates byte-identically to PrinterCommands (D-11 drift contract), guarded by CommandRegistryGcodeTest"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/designsystem/HsvToRgb.kt
    - app/src/test/java/works/mees/dinghy/designsystem/HsvToRgbTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt
    - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
    - app/src/test/java/works/mees/dinghy/command/PrinterCommandsOutputsTest.kt
    - app/src/test/java/works/mees/dinghy/command/CommandRegistryGcodeTest.kt

key-decisions:
  - "SetServoArgs folds angle + disable into one args type (nullable-free: a `disable: Boolean` flag) so the page's Off affordance dispatches the same setServo spec"
  - "SetOutputPinArgs.pwm branches one spec across digital + PWM output_pin AND pwm_tool — no dedicated pwm-tool command exists, all route through SET_PIN"
  - "Output specs use availability=Always: the runtime descriptor discovery (19-04) is the source of truth for what the UI offers, so the registry spec does not duplicate the object-presence gate"
  - "hsvToRgb saturation defaults to 1f — the v1 fixed-saturation (hue + brightness) decision (RESEARCH Q1/A4), reuses the hue-only ColorWheel verbatim, signature-future-proof"
  - "Rephrased the three 'no SET_PWM_TOOL' doc comments so the literal-token grep gate over app/src/main returns 0"

patterns-established:
  - "outputPctToWire is the public wire-precision helper Wave-2 holders call to compute an optimistic markPending target identical to the dispatched value"

requirements-completed: [SC-2, SC-3]

# Metrics
duration: ~12min
completed: 2026-06-08
---

# Phase 19 Plan 03: Output Command Builders + Clamp Authority + Registry Summary

**Generic-output gcode builders (fan/LED/servo/pin/pwm-tool) added to `PrinterCommands` as clamp-before-format, BARE-name builders (HIGH-1), registered as typed `CommandSpec`s in `CommandRegistry.all` so detail pages dispatch through the catalog like Fine-Tune (HIGH-2), plus the new pure `hsvToRgb` helper the LED page needs — all host-tested against live-verified strings.**

## Performance

- **Duration:** ~12 min
- **Tasks:** 3
- **Files modified:** 4 modified + 2 created

## Accomplishments
- **Clamp authority + builders (Task 1):** added `OUTPUT_PCT_MIN/MAX`, `SERVO_ANGLE_DEFAULT_MAX`, `clampOutputPct`, `clampServoAngle`, and the public `outputPctToWire` wire-precision helper (the 17-07 invariant: the markPending optimistic target uses the SAME clamped value the wire sends). Added `setGenericFan`/`setLed`/`setServoAngle`/`setServoDisable`/`setPinDigital`/`setPinPwm`, every one clamp-before-format through the existing private `fmt(...)` chokepoint (no new `String.format`), built from the BARE section name. heater_generic reuses the existing `setHeater` (no new builder).
- **hsvToRgb (Task 2):** dependency-free top-level `hsvToRgb(hue, saturation=1f, value): Triple<Float,Float,Float>` — standard HSV sextant, hue wraps 0..360, sat/value clamped, outputs clamped 0f..1f, zero androidx imports. The default `saturation=1f` encodes the v1 fixed-saturation decision.
- **Registry specs (Task 3):** `SetGenericFanArgs`/`SetLedArgs`/`SetServoArgs`/`SetOutputPinArgs` (BARE name) + `setGenericFan`/`setLed`/`setServo`/`setOutputPin` typed `CommandSpec`s, each delegating byte-identically to the Task-1 builders, each with a distinct per-output dispatchKey, all added to `CommandRegistry.all`. One `setOutputPin` spec serves digital + PWM output_pin AND pwm_tool.

## Task Commits

1. **Task 1: Output command builders + clamp authority** — `04e8b06` (feat)
2. **Task 2: hsvToRgb pure helper** — `cefe0d4` (feat)
3. **Task 3: Register output CommandSpecs + drift test** — `02ae23c` (feat)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt` — output clamp consts/helpers + 6 bare-name builders (TDD GREEN)
- `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` — 4 output args classes + 4 typed CommandSpecs in `all`
- `app/src/main/java/works/mees/dinghy/designsystem/HsvToRgb.kt` — pure HSV→RGB helper (created)
- `app/src/test/java/works/mees/dinghy/command/PrinterCommandsOutputsTest.kt` — RED scaffold turned GREEN (live strings, clamp, LED-Off, servo-disable, HIGH-1 guard, wire-helper)
- `app/src/test/java/works/mees/dinghy/designsystem/HsvToRgbTest.kt` — pure helper tests (created)
- `app/src/test/java/works/mees/dinghy/command/CommandRegistryGcodeTest.kt` — extended with per-family drift cases + HIGH-1 wire-guard + all-list + per-output dispatchKey

## Decisions Made
- `SetServoArgs` folds angle + disable into one args type via a `disable: Boolean` flag (page Off → same spec).
- `SetOutputPinArgs.pwm` branches one spec across digital + PWM output_pin AND pwm_tool (no dedicated pwm-tool command).
- Output specs use `availability=Always`; the 19-04 descriptor discovery is the source of truth for UI availability, so the registry does not re-derive object presence.
- `hsvToRgb` default `saturation=1f` encodes the v1 fixed-saturation decision (RESEARCH Q1/A4) without a future signature break.
- Rephrased the three "no SET_PWM_TOOL" doc comments so `grep -rn SET_PWM_TOOL app/src/main` returns 0 (the gate checks for an emitted literal; the comments were the inverse but tripped a literal grep).

## Deviations from Plan

None — plan executed as written. (The doc-comment rephrase noted above is a clean-up to satisfy the literal grep gate, not a behavior change.)

## Issues Encountered
None. Both printers' fixtures from 19-02 backed the live-verified strings; all three suites green on first full run.

## Verification
- `:app:testDebugUnitTest --tests *PrinterCommandsOutputsTest` — GREEN
- `:app:testDebugUnitTest --tests *HsvToRgbTest` — GREEN
- `:app:testDebugUnitTest --tests *CommandRegistryGcodeTest` — GREEN (output specs + drift + HIGH-1 wire-guard + all-list)
- `grep -rn SET_PWM_TOOL app/src/main` → 0 (pwm_tool uses SET_PIN)
- `grep -cE 'class SetGenericFanArgs|class SetLedArgs|class SetServoArgs|class SetOutputPinArgs' CommandRegistry.kt` → 4
- `grep -c 'import androidx' HsvToRgb.kt` → 0
- `grep -c 'String.format' PrinterCommands.kt` → 2 (unchanged from before — the lone `fmt` chokepoint; no new inline format)

## Next Phase Readiness
- Wave-2 holder/detail pages (19-04+) can compute markPending via `PrinterCommands.outputPctToWire` and dispatch via `CommandRegistry.setGenericFan`/`setLed`/`setServo`/`setOutputPin` (HIGH-2 catalog path).
- The LED page (Wave 2) consumes `hsvToRgb(hue, 1f, brightness)` → `setLed` RED/GREEN/BLUE.
- No blockers.

## Self-Check: PASSED

All created/modified files verified on disk; all three task commits (`04e8b06`, `cefe0d4`, `02ae23c`) verified in git log.

---
*Phase: 19-output-controls-fans-lights-generic-pins*
*Completed: 2026-06-08*
