---
phase: 19-output-controls-fans-lights-generic-pins
plan: 19-09
subsystem: ui-outputs
tags: [gap-closure, layout, scrubber, led, gutter, GAP-A]
gap_closure: true
requires:
  - "ScrubberPage (designsystem) OnSettle mode"
  - "OutputScrubberContent / OutputLedContent (Phase 19-06)"
provides:
  - "ScrubberActions.OnSettle optional gutter Off slot (onOff + offLabel)"
  - "LedBrightnessControl — internal reusable inline brightness control (consumed by 19-10)"
affects:
  - "every scrubber detail page (fan, servo, PWM output_pin, pwm_tool, heater)"
  - "the LED detail page (single-scaffold)"
tech-stack:
  added: []
  patterns:
    - "Off as a gutter action on the shared Focus/Field/Gutter grid (not an overlay)"
    - "inline fill-bar control reusing fractionFromX (no nested ScreenScaffold)"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputScrubberDetail.kt
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputLedDetail.kt
decisions:
  - "Off renders as [Off (Danger) | Back (Neutral)] weight(1f) row in the OnSettle gutter when onOff != null; single full-width Back otherwise (back-compat)."
  - "Heater current-temp readout + failure toast re-homed top-anchored (Alignment.TopCenter) so they no longer overlap the scrubber's centered value/track."
  - "LedBrightnessControl built as an internal reusable composable (value in / settled value out, no LED-specific logic) so 19-10 reuses it verbatim for the white-only LED page."
metrics:
  duration: "~4 min"
  completed: "2026-06-08"
  tasks: 3
  files: 3
---

# Phase 19 Plan 09: Scrubber Off-Slot & Single-Scaffold LED Summary

GAP-A layout fix: the Off button now lives in the scrubber gutter on the shared grid (red Danger Off beside neutral Back) instead of floating over the scrubber value/track, and the LED page collapses to a single `ScreenScaffold` by replacing its nested `ScrubberPage` with an inline reusable `LedBrightnessControl`.

## What was built

1. **`ScrubberActions.OnSettle` gutter Off slot** — two optional params `onOff: (() -> Unit)? = null` and `offLabel: String? = null` (source-compatible defaults). When `onOff != null` the OnSettle gutter renders a two-button `[Off (Intent.Danger) | Back (Intent.Neutral)]` row, each `Modifier.weight(1f)`; when null it keeps the original single full-width Back. The `ApplyCancel` branch is byte-for-byte unchanged (Temperature / Gallery unaffected).

2. **`OutputScrubberContent` overlay removed** — the floating overlay `Column` that rendered the Off button "over the field" is deleted. Off is now passed into `ScrubberPage` via `ScrubberActions.OnSettle(onOff = …, offLabel = stringResource(R.string.output_off))`. The heater current-temp readout (`R.string.output_heater_current`, HEATER only) and the transient failure `SeverityToast` are re-homed to a `TopCenter`-aligned Column so they no longer share the scrubber value's vertical center.

3. **LED page single-scaffold** — `OutputLedContent` no longer nests a whole `ScrubberPage` (which carries its own scaffold + gutter) inside the LED field. The nested page is replaced by a new `internal fun LedBrightnessControl(...)`: a self-contained horizontal fill-bar (WR-01 single-`awaitEachGesture`, reusing the shared `fractionFromX` pure mapping) + a `[− +]` stepper row, dispatching `onColorSettle(hue, settled)` ONCE per settle (gesture-end / stepper tap). The hue wheel, live swatch, brightness label, and the `[Off | Back]` gutter are unchanged. `LedBrightnessControl` is `internal` and reusable so 19-10 (GAP-B white-only page) consumes it verbatim.

## Verification

- `:app:testDebugUnitTest --tests works.mees.dinghy.ui.outputs.OutputScrubberSettleTest` — GREEN (settle-count contract intact; new optional params don't touch it).
- `:app:testDebugUnitTest --tests works.mees.dinghy.ui.outputs.*` — GREEN.
- `:app:assembleDebug` — BUILD SUCCESSFUL (full app + the `@Preview` matrix in `OutputDetailPreviews.kt` compile; `LedCell()` signature unchanged).
- grep gates: `ScreenScaffold(` in OutputLedDetail.kt = 1; `ScrubberPage(` in OutputLedDetail.kt = 0; `internal fun LedBrightnessControl` present; `onOff =` present in OutputScrubberDetail.kt and `onOff`/`offLabel` present in ScrubberPage.kt OnSettle.

## Deviations from Plan

None — plan executed exactly as written. `OutputScrubberSettleTest` and `OutputDetailPreviews.kt` needed no edits (the OnSettle change is purely additive/optional and the content-seam signatures were untouched), which the plan explicitly anticipated ("ONLY if the OnSettle constructor change breaks compilation" / "Adjust the preview cell only if the param list changed").

## Known Stubs

None.

## Notes for 19-10

`LedBrightnessControl(value, onValueChange, onSettle, modifier, enabled)` is the reusable inline brightness control 19-10 reuses for the white-only LED page — it carries no RGB/LED-specific logic (value in, settled value out), so the white-only page wires its `onSettle` to a WHITE-channel dispatch instead of the RGB hue dispatch.

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt (cdd2b7c)
- FOUND: app/src/main/java/works/mees/dinghy/ui/outputs/OutputScrubberDetail.kt (831efe5)
- FOUND: app/src/main/java/works/mees/dinghy/ui/outputs/OutputLedDetail.kt (91f4123)
- FOUND commits: cdd2b7c, 831efe5, 91f4123
