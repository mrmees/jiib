---
phase: 19-output-controls-fans-lights-generic-pins
plan: 05
subsystem: ui
tags: [outputs, holder, list-screen, display-scaling, busy-lock, reached, icon-law, preview-tokens, sc-1, sc-3]

# Dependency graph
requires:
  - phase: 19-02
    provides: the still-RED OutputsHolderTest Wave-0 scaffold (turned GREEN here)
  - phase: 19-03
    provides: PrinterCommands.outputPctToWire / clamp authority (markPending fed the clamped wire value)
  - phase: 19-04
    provides: OutputDescriptor (objectKey/commandName split) + PrinterState.outputs/heaters + PrinterStateStore.outputDescriptors
  - phase: 19-01
    provides: owner-locked DinghyIcons.Output* tokens (D-01..D-06)
provides:
  - OutputsHolder — StateFlow<List<OutputRowVm>> combining descriptors + live values, holder-side display scaling, per-output clamped busy lock with per-family reached() (servo timeout-only)
  - OutputRowVm — typed row VM (displayValue / swatchColor / isSettable / busy), distinct fields not one overloaded value
  - OutputsScreen + stateless OutputsContent seam — flat alpha LazyColumn, owner-locked per-family glyphs, SC-3 absent-value degrade, Back-only neutral gutter
  - OutputsPreviews — day-one @Preview matrix (6 theme combos + fs=L + RTL + pseudolocale), no live Moonraker
affects: [19-06 output detail pages, Wave-3 drawer tile + nav wiring, 19-VALIDATION]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Display scaling lives in the holder, never the reducer (RESEARCH Pitfall 1 / FineTune precedent): 0..1 → % at buildRow only"
    - "Typed row VM (review MEDIUM): displayValue / swatchColor / isSettable / busy — the screen consumes unambiguous fields"
    - "Per-output busy keyed by FULL objectKey (NOT global) with a seq-guarded per-key timeout backstop (17-07)"
    - "Per-family reached(): fan/pin/pwm/heater/led confirm from live; servo ALWAYS returns false → timeout-only (PWM value is not the commanded angle)"
    - "markPending fed the SAME clamped wire value the command sends (PrinterCommands.outputPctToWire) — target can never disagree with the wire (17-07 invariant)"
    - "heater_generic single-sourced via heaters (19-04), every other family via outputs"
    - "Stateless content seam + live overload (Spool precedent) so previews drive the screen with no holder/socket"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/outputs/OutputsHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt
    - app/src/main/java/works/mees/dinghy/preview/OutputsPreviews.kt
  modified:
    - app/src/test/java/works/mees/dinghy/outputs/OutputsHolderTest.kt
    - app/src/main/res/values/strings.xml

key-decisions:
  - "OutputRowVm carries the typed four-facet VM (displayValue/swatchColor/isSettable/busy); displayValue is null for BOTH absent (unreported) AND unreadable (servo PWM-not-angle) — the screen treats both identically (hide value, keep row tappable)"
  - "LED brightness display = the brightest channel of color_data[0] (max of r/g/b); swatchColor = packed opaque ARGB from color_data[0]. reached() for an LED confirms on that same max-channel brightness"
  - "REACHED_EPSILON = 0.005 on the 0..1 wire scale (half a display-percent step) — absorbs float jitter, an order of magnitude below one percent so a one-step nudge reads reached only at the actual commanded value"
  - "digital output_pin (pwm=false) shows On/Off from value>=0.5; PWM output_pin + pwm_tool show %; fan shows %"
  - "Glyph fallback for an unknown family = DinghyIcons.OutputSection (defensive; the discovery whitelist precludes it) — never an invented glyph (icon law)"

patterns-established:
  - "An output's pending busy entry lives in a Map<objectKey, OutputPending>; combine drops a reached entry + cancels its backstop, a per-key seq-guarded timer drops an unreachable (servo) one"

requirements-completed: [SC-1, SC-3]

# Metrics
duration: ~18min
completed: 2026-06-08
---

# Phase 19 Plan 05: Outputs Holder + Flat List Screen Summary

**The Outputs list surface (SC-1/SC-3): an `OutputsHolder` that combines the discovered descriptors with throttled live status into an alpha-sorted typed `OutputRowVm` list with holder-side display scaling, a per-output clamped busy lock and per-family `reached()` (servo timeout-only), and a flat-alpha `OutputsScreen` (owner-locked per-family glyphs, absent-value-degrades-but-row-tappable, Back-only neutral gutter) with a day-one @Preview matrix — the Wave-0 `OutputsHolderTest` turned GREEN.**

## Performance
- **Duration:** ~18 min
- **Tasks:** 2
- **Files modified:** 3 created + 2 modified

## Accomplishments

- **Task 1 — OutputsHolder + typed VM (SC-1/SC-3, 17-07):** `OutputsHolder` `combine`s `store.outputDescriptors` + `store.printerState` + a per-output pending map into a `StateFlow<List<OutputRowVm>>`, alpha-sorted by `prettyName`. The typed `OutputRowVm(descriptor, displayValue, swatchColor, isSettable, busy)` names the four facets distinctly (review MEDIUM). Display scaling lives HERE (Pitfall 1): fan `.speed` and PWM-pin/pwm_tool `.value` → `round(×100)%`, digital pin → On/Off, heater_generic → `round(temp)°C` read from `heaters` (single source, 19-04), LED → brightness% + `swatchColor` (packed ARGB) from `color_data[0]`. Servo `.value` is hidden (PWM not angle → `displayValue` null, SC-3). A read-only static pin → `isSettable=false`. Per-output `markPending(objectKey, clampedWireTarget)` arms an optimistic flip keyed by the FULL objectKey, fed the SAME clamped wire value the command sends; `reached()` confirms per family (fan/pin/pwm/heater/led from live) EXCEPT servo, which always returns false and clears only via the seq-guarded per-key timeout backstop. `OutputsHolderTest` turned GREEN with 8 cases (absent-value-hides-but-tappable, servo-hidden + servo-timeout-only, static-pin-read-only, speed-0.45→"45%", heater-from-heaters, fan-confirm-from-live, alpha-sort).
- **Task 2 — OutputsScreen flat list + preview matrix (SC-1/SC-3, PREVIEW_AND_TOKENS):** `OutputsScreen` (live, collects the holder) + a stateless `OutputsContent(rows, onRowTap, onBack)` seam (preview-drivable, no holder/socket) — the Spool stateless/live split. `ScreenScaffold` field = title + flat `LazyColumn` keyed by `objectKey`; gutter = a single neutral Back (`Intent.Neutral`, never red). Each row = the owner-locked `DinghyIcons.Output*` glyph chosen by family (heater/fan/led/servo/pin/pwm_tool → D-01..D-06) + `prettyName` + the value rendered ONLY when non-null (absent → omitted, row stays `clickable` — SC-3); LED rows also show the `swatchColor` chip (THEME-01 literal-color carve-out). Fonts via the `fsSp(base, t.fs)` scale (18sp body, 20sp tabular value, 24sp title); all strings via `stringResource` (`outputs_title`/`cd_output_glyph`/`cd_output_swatch`/`common_back`). `OutputsPreviews` drives the stateless seam from a handcrafted 5-row fixture covering every branch (fan %, LED swatch, servo no-value, read-only pin, absent-value row) across the 6 theme combos + fs=L + RTL + pseudolocale — no live Moonraker.

## Task Commits
1. **Task 1: OutputsHolder + typed row VM + reached()** — `fb394c1` (feat)
2. **Task 2: OutputsScreen flat list + preview matrix** — `f294f0a` (feat)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/outputs/OutputsHolder.kt` — created; combine + typed VM + holder-side scaling + per-output clamped busy lock + per-family reached() (servo timeout-only)
- `app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt` — created; stateless content seam + live overload, flat alpha LazyColumn, owner-locked glyphs, SC-3 degrade, Back-only neutral gutter
- `app/src/main/java/works/mees/dinghy/preview/OutputsPreviews.kt` — created; day-one @Preview matrix (6 themes + fs=L + RTL + pseudolocale), no live Moonraker
- `app/src/test/java/works/mees/dinghy/outputs/OutputsHolderTest.kt` — Wave-0 RED scaffold turned GREEN (8 cases)
- `app/src/main/res/values/strings.xml` — added `outputs_title`, `cd_output_glyph`, `cd_output_swatch`

## Decisions Made
- `displayValue` is null for BOTH the absent (unreported) AND unreadable (servo PWM-not-angle) cases; the screen handles both identically — hide the value, keep the row tappable (SC-3).
- LED brightness = the brightest channel of `color_data[0]` (max of r/g/b); `swatchColor` = packed opaque ARGB from `color_data[0]`; the LED's `reached()` confirms on that same max-channel brightness.
- `REACHED_EPSILON = 0.005` on the 0..1 wire scale (half a display-percent step) — absorbs float round-trip jitter while staying an order of magnitude below one percent.
- digital `output_pin` (pwm=false) → On/Off from `value>=0.5`; PWM `output_pin` + `pwm_tool` → %; `fan_generic` → %.
- Unknown-family glyph fallback = `DinghyIcons.OutputSection` (defensive — the whitelist precludes it), never an invented glyph (icon law).

## Deviations from Plan
None — plan executed as written.

## Issues Encountered
None. Holder test GREEN on first full run; screen + previews compiled on first run.

## Threat Surface Scan
No new security-relevant surface. T-19-05-01 (absent/null live value crash) mitigated: the holder emits null `displayValue` with `isSettable` intact and the screen omits the value Composable but keeps the row `clickable`. T-19-05-02 (icon law) mitigated: per-family `DinghyIcons.Output*` tokens only — grep proves 0 raw `IconRef.Ligature` in OutputsScreen. T-19-05-03 (busy-lock wedge) mitigated: `markPending` fed the clamped wire value + per-family `reached()` with servo on a seq-guarded timeout backstop.

## Verification
- `:app:testDebugUnitTest --tests *OutputsHolderTest` — GREEN (8 cases)
- `:app:compileDebugKotlin` — BUILD SUCCESSFUL (screen + previews compile)
- `grep -c 'DinghyIcons.Output' OutputsScreen.kt` → 10 (≥6, one per family + fallback + token import paths)
- `grep -c 'IconRef.Ligature' OutputsScreen.kt` → 0 (no raw ligature strings — icon law)
- `grep -c 'displayValue|swatchColor|isSettable' OutputsHolder.kt` → 11 (typed distinct fields)

## Next Phase Readiness
- 19-06 output detail pages join `OutputDescriptor` (objectKey/commandName) + the holder's `OutputRowVm`; they dispatch via `CommandRegistry.setGenericFan`/`setLed`/`setServo`/`setOutputPin` and arm the holder's `markPending(objectKey, outputPctToWire(pct))`.
- The Wave-3 drawer tile + nav wire `OutputsScreen(holder, onRowTap, onBack)` behind `AppContainer.outputsPresent` (D-10).
- No blockers.

## Self-Check: PASSED

All 3 created files verified on disk; both task commits (`fb394c1`, `f294f0a`) verified in git log.

---
*Phase: 19-output-controls-fans-lights-generic-pins*
*Completed: 2026-06-08*
