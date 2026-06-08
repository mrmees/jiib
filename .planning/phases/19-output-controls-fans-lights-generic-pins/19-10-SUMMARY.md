---
phase: 19-output-controls-fans-lights-generic-pins
plan: 19-10
subsystem: outputs
tags: [gap-closure, led, capability, white-channel, GAP-B]
gap_closure: true
requires:
  - "OutputDescriptor / OutputsGate.parseOutputs (19-04)"
  - "LedBrightnessControl — reusable inline brightness control (19-09)"
  - "OutputsHolder.LED_FAMILIES single-source LED-family identity"
provides:
  - "OutputDescriptor.ledHasRgb / ledHasWhite — per-LED channel capability from configfile.settings"
  - "OutputLedContent capability branch (RGB wheel vs white-only brightness)"
  - "white-only LED dispatch via the WHITE channel (setLed w=brightness)"
  - "white/grey row swatch for a white-only LED"
affects:
  - "the LED detail page (OutputLedDetail / OutputLedContent)"
  - "the Outputs list row swatch (OutputsHolder.buildRow)"
tech-stack:
  added: []
  patterns:
    - "capability derived from settings (red/green/blue/white_pin + color_order) at parse time, carried on the descriptor"
    - "UI branches on the descriptor's capability flags — no control sends a command the LED can't accept (SC-3)"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/outputs/OutputDescriptor.kt
    - app/src/main/java/works/mees/dinghy/outputs/OutputsGate.kt
    - app/src/main/java/works/mees/dinghy/outputs/OutputsHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputLedDetail.kt
    - app/src/main/java/works/mees/dinghy/preview/OutputDetailPreviews.kt
    - app/src/test/java/works/mees/dinghy/outputs/OutputsGateTest.kt
    - app/src/test/java/works/mees/dinghy/outputs/OutputsHolderTest.kt
    - app/src/test/java/works/mees/dinghy/ui/outputs/OutputLedCommandTest.kt
decisions:
  - "Capability is derived once at parse time and carried on OutputDescriptor (ledHasRgb/ledHasWhite, default false) so the UI/holder branch on data, never re-inspect settings."
  - "pca9533 = fixed RGBW driver (both flags) and pca9632-with-no-color_order defaults RGBW — NOT routed through the pin check (Codex SS-1), which would misclassify them."
  - "FALLBACK to RGB-capable for any unrecognized LED so we never hide all controls (a no-op RGB write is the pre-fix behavior, not a regression)."
  - "RGBW lights still use the RGB wheel + brightness in v1 (a separate white slider is out of scope per the UAT fix direction); ledHasWhite is informational for that case."
  - "White-only brightness seeds from color_data[0][3] (the white component), not the brightest RGB channel (which is 0 on a white-only light)."
metrics:
  duration: "~6 min"
  completed: "2026-06-08"
  tasks: 3
  files: 8
---

# Phase 19 Plan 10: LED Channel-Capability Branch (GAP-B) Summary

GAP-B fix: the LED page now derives per-LED channel capability from `configfile.settings` and branches the control — an RGB-capable LED keeps the hue wheel + brightness, while a white/brightness-only LED (the owner's E5 `led chamber_light`, which has only `white_pin`) shows a brightness control ALONE and dispatches the WHITE channel so value changes actually drive the hardware.

## What was built

1. **`ledHasRgb` / `ledHasWhite` on `OutputDescriptor`** (default `false`) — the GAP-B channel capability, derived in `OutputsGate.parseOutputs` only for LED families (reusing `OutputsHolder.LED_FAMILIES`, no second hard-coded list). The `ledCapability(family, section)` derivation:
   - pin-based `led`: `red_pin`/`green_pin`/`blue_pin` ⇒ RGB; `white_pin` ⇒ white (an RGBW `[led]` is both); a bare `[led]` with neither falls back RGB-capable.
   - color_order families (`neopixel`/`dotstar`/`pca9632`): a token carrying R/G/B ⇒ RGB; a token carrying "W" ⇒ white; handled via a defensive `colorOrderTokens()` that accepts both the Klipper array shape (`["GRB"]`) and a scalar string. Absent `color_order` ⇒ RGB, plus white only for `pca9632` (its Klipper default order is RGBW).
   - `pca9533`: fixed 4-channel RGBW driver — both flags directly (Codex SS-1; it has no `*_pin` / no `color_order` so the pin check would yield neither).
   - Proven off the REAL E5 fixtures: `led chamber_light` → `ledHasRgb=false, ledHasWhite=true`; `neopixel expanderPixel` (color_order `["GRB"]`) → `ledHasRgb=true, ledHasWhite=false`.

2. **`OutputLedContent` capability branch** — the `ColorWheel` now renders ONLY inside the `ledHasRgb == true` branch. A white-only LED shows the reusable inline `LedBrightnessControl` (19-09) alone + a grey/white swatch (THEME-01 literal data-color, scaled by brightness — not a hue chip) + label + the `[Off | Back]` gutter. `OutputLedDetail` adds `dispatchWhite(brightnessPct)` → `setLed(name, 0, 0, 0, w = brightness/100)` through `CommandRegistry.setLed` with the BARE name (HIGH-1), `markPending`-ing the white fraction (the max channel when r=g=b=0, matching `OutputsHolder.reached()`). White-only brightness seeds from the live `color_data[0][3]`; the RGB path (seed via `rgbToHsv`, dispatch `hsvToRgb(...) w=0f`) is byte-equivalent to 19-09. The `OutputLedDetail` call site in `AppShell.kt` needed NO change — it already passes `descriptor` + `colorData`, and the new flags ride on the descriptor.

3. **White-only row swatch + previews** — `OutputsHolder.buildRow` surfaces a grey/white swatch (`packArgb([w,w,w])` scaled by the white component) for a white-only LED instead of black; the RGB swatch path is unchanged; brightness `%` was already correct (the white component IS the max channel when r=g=b=0). `OutputDetailPreviews` adds a `WhiteOnlyLedCell` (brightness-only, no wheel) with `colorfulDark` + `fsLarge` cells, and `LedCell` updated to explicit RGB params.

## Verification

- `:app:testDebugUnitTest --tests works.mees.dinghy.outputs.OutputsGateTest` — GREEN (capability proven off the real E5 fixtures: chamber_light white-only, expanderpixel RGB; plus the led/neopixel/pca9533/pca9632 derivation cases).
- `:app:testDebugUnitTest --tests works.mees.dinghy.ui.outputs.OutputLedCommandTest` — GREEN (white-only brightness 80% → `SET_LED LED=caselight RED=0 GREEN=0 BLUE=0 WHITE=0.8`; Off → `WHITE=0`; bare name; all RGB-path assertions still green).
- `:app:testDebugUnitTest --tests works.mees.dinghy.outputs.* --tests works.mees.dinghy.ui.outputs.*` — GREEN (incl. the new white-only swatch assertion: `[0,0,0,0.8]` → `80%` + non-black grey `R==G==B>0`).
- `:app:assembleDebug` — BUILD SUCCESSFUL (the LED `@Preview` matrix incl. the new white-only cells compiles).

## Deviations from Plan

None — plan executed exactly as written. `AppShell.kt` was listed in `files_modified` but needed no change (the new capability flags ride on the existing `descriptor` argument), which the plan's Task-2 `read_first` anticipated ("the descriptor now carries the new flags so no new wiring needed").

## Known Stubs

None.

## Threat Flags

None — no new network endpoints, auth paths, file access, or schema changes at trust boundaries beyond the planned `configfile.settings` capability inference (T-19-10-01, mitigated as designed).

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/outputs/OutputDescriptor.kt
- FOUND: app/src/main/java/works/mees/dinghy/outputs/OutputsGate.kt
- FOUND: app/src/main/java/works/mees/dinghy/outputs/OutputsHolder.kt
- FOUND: app/src/main/java/works/mees/dinghy/ui/outputs/OutputLedDetail.kt
- FOUND: app/src/main/java/works/mees/dinghy/preview/OutputDetailPreviews.kt
- FOUND commits: 8ba5988, d505a02, 72982df
