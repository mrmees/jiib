---
status: diagnosed
phase: 19-output-controls-fans-lights-generic-pins
plan: 19-08
deferred: false
result: "owner ran on-device 2026-06-08: fans/output_pins/servo functionally work; 2 gaps found — (A) Off button overlays the scrubber on every scrubber detail page; (B) LED page ignores channel capability (shows RGB on a white-only light, value changes have no effect). Phase NOT complete — 2 gap-closure plans needed."
updated: 2026-06-08
---

# Phase 19 — On-Device UAT (Output Controls — Fans, Lights & Generic Pins)

**Plan:** 19-08 (Task 2 — `checkpoint:human-verify`, blocking)
**Status:** PENDING — all code built, host-tested green, D-09 ligatures verified, debug APK installed on flox.
Awaiting the human gate: Matthew drives the Outputs feature on flox against the live printers and records
PASS/FAIL + the REQUIRED `/server/gcode_store` wire evidence below.
**Build:** debug `app-armeabi-v7a-debug.apk` installed on flox (`0a64b42e`) from commit `41bb10b`
**Device:** flox (LineageOS 18.1 / API 30, genuine Adreno 320 / 2GB / 1920×1200, `armeabi-v7a`)
**Printers (Moonraker):** Ender 5 Plus = `192.168.1.120:7125` (rich) · Ender 3 Pro = `192.168.1.121:7125` (sparse)

## Pre-gates (Claude — all GREEN)

- Full `:app:testDebugUnitTest` — **848 tests, 0 failures, 0 errors** (132 suites). The legitimate full-suite
  phase gate (HIGH-4: by this final wave every Wave-0 RED scaffold has been filled by its owning plan).
- `python tools/verify_ligatures.py` — **exit 0**, `missing: []` (D-09 gate).
- `:app:installDebug` — **BUILD SUCCESSFUL**, installed on flox.

## Scope & ground rules

- **State-flip, not optimistic.** "Row value updates" means the row's REPORTED value updates to the commanded
  value after the printer object actually changes (per the central subscribe) — not an instant local echo.
- **Perf reframe (ADR-0001 Add.2):** judge on **no frozen frames + responsiveness**, NOT the gfxinfo artifact.
- **On-device iteration model:** Claude built + installed; **Matthew navigates + eyeballs** and records
  PASS/FAIL below. A FAIL spawns a gap-closure plan (orchestrator-owned) — the phase is NOT marked complete
  on a FAIL.
- **Accepted automated-only exclusion:** neither dev printer exposes a `heater_generic`. Its detail page is
  covered by unit tests ONLY (the existing `setHeater` builder + 19-03 tests + the CommandRegistry drift
  test). Live `heater_generic` verification is EXPLICITLY ACCEPTED as automated-only / not-live-covered,
  deferred to a printer that exposes one. This does NOT block SC-4 (scope = "fan/pin/LED configuration").
  Recorded as an accepted exclusion, **not a gap**.

## REQUIRED command-evidence (NOT optional)

For at least **fan, LED, output_pin, and servo** dispatches, capture server-side `GET /server/gcode_store`
on the printer and paste the matching command line as objective wire evidence (proves the bare section name
reached the wire — HIGH-1 — and the command went through the catalog). Missing evidence = that item is NOT PASS.

Quick capture (from any shell on the network):
`curl -s "http://192.168.1.120:7125/server/gcode_store?count=20" | python3 -m json.tool`

## Checks — E5P (192.168.1.120:7125, rich config)

> Record `PASS` / `FAIL` + notes per check. Leave PENDING until run.

### 1 — Output tile present + list filtered (SC-1) — PASS
- Owner reports the Output tile present and the outputs list shows the expected whitelisted outputs.

### 2 — Filter Fan (fan_generic), state flip (SC-2) — PASS (function) / blocked by GAP-A (layout)
- Fan responds and the row flips. Functional. BUT the Off button overlays the scrubber (GAP-A).
- gcode_store wire evidence: NOT captured this run — to capture at the post-fix re-UAT.

### 3 — Chamber Light / Expanderpixel (LED), color + Off (D-12) — FAIL (GAP-B)
- The chamber light is a **white/brightness-only** LED (`led chamber_light` has only `white_pin`, no
  red/green/blue). The page shows the RGB hue wheel and dispatches RGB with WHITE=0, so color/value changes
  have **no visible effect** and there's no way to drive the white channel. See GAP-B.

### 4 — A Mosfet (output_pin), toggle/PWM (SC-2) — PASS (function) / blocked by GAP-A (layout)
- Pin responds and the row reflects it. Functional. Off-button overlay (GAP-A) present on the PWM scrubber.
- gcode_store wire evidence: NOT captured this run — to capture at the post-fix re-UAT.

### 5 — Camera Servo, angle + value degrade (SC-3) + Off — PASS (function) / blocked by GAP-A (layout)
- Servo moves; row degrades the value. Functional. Off-button overlay (GAP-A) present.
- gcode_store wire evidence: NOT captured this run — to capture at the post-fix re-UAT.

### 6 — Failure path (toast-stay) — PENDING
- Not explicitly reported this run. Re-verify at post-fix re-UAT.

### 7 — Mid-print availability (optional, if convenient) — PENDING
- Not tested this run.

### 8 — Perf / responsiveness (Adreno-320) — PASS (implied)
- No jank/frozen-frame complaints from the owner walk.

## Checks — E3P (192.168.1.121:7125, sparse config)

### 9 — Tile NOT hidden when exactly one output (D-10) — PENDING
- Not explicitly reported this run. Re-verify at post-fix re-UAT.

## Summary

total: 9
passed: 4
issues: 2
pending: 3
skipped: 0
blocked: 0
accepted-exclusions: 1 (heater_generic — automated-only, no live printer exposes one)

## Gaps

### GAP-A — Off button overlays the scrubber on every scrubber detail page (layout)
- **status:** failed
- **severity:** major (affects every scrubber detail: fan, servo, PWM output_pin, pwm_tool, heater, and the
  LED brightness sub-scrubber)
- **symptom (owner):** "off button is overlaid on top of every scrubber instead of having its own space."
- **root cause:** `ScrubberActions.OnSettle` (in `designsystem/ScrubberPage.kt`) has NO slot for an Off
  action — its gutter is only `[− +]` steppers + a single full-width Back. So
  `ui/outputs/OutputScrubberDetail.kt` `OutputScrubberContent` renders the Off button in a separate `Column`
  that is a **Box sibling overlaid on top of the full-screen `ScrubberPage` field** (the code comment says
  "float over the field"), colliding with the scrubber's value/track. `OutputLedDetail.kt` compounds it by
  **nesting a whole `ScrubberPage`** (its own `ScreenScaffold` + gutter) inside the LED page's scaffold field
  (the double-scaffold the 19-06 SUMMARY already flagged as a layout-refinement candidate).
- **fix direction:** extend `ScrubberActions.OnSettle` with an optional `onOff` (+ label) that renders the Off
  button **in the gutter** beside Back (red Danger | neutral Back, ≥64px, on the shared grid — button-intent
  law). Remove the overlay `Column` from `OutputScrubberContent` (keep the transient failure toast + the
  heater current-temp readout placed sanely, not floating). Re-home the LED brightness control so it does not
  nest a second `ScreenScaffold`/gutter inside the LED field.
- **files (likely):** `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt`,
  `app/src/main/java/works/mees/dinghy/ui/outputs/OutputScrubberDetail.kt`,
  `app/src/main/java/works/mees/dinghy/ui/outputs/OutputLedDetail.kt`, the existing call sites in
  ApplyCancel mode are unaffected (optional param). Update previews + `OutputScrubberSettleTest`.

### GAP-B — LED page ignores channel capability (white-only light shown an ineffective RGB control)
- **status:** failed
- **severity:** major (LED control is non-functional on a white/brightness-only LED — the owner's E5 chamber
  light)
- **symptom (owner):** "should be able to detect if a light is rgb applicable and not show that control if
  not. My e5 chamber light is brightness control only, and there is no way to change it to white, so value
  adjustments have no effect. Brightness should be a slider with step adjustment."
- **root cause:** the channel capability is knowable from `configfile.settings` but is not captured:
  - `led chamber_light` → only `white_pin` (no `red_pin`/`green_pin`/`blue_pin`) ⇒ **white/brightness-only**.
  - `neopixel expanderpixel` → `color_order: ["GRB"]` ⇒ **RGB-capable** (no white).
  `OutputDescriptor` (built in 19-04 `outputs/OutputsGate.parseOutputs`) carries no channel info, and
  `OutputLedDetail` unconditionally renders the hue wheel and dispatches `SET_LED ... WHITE=0`, so on a
  white-only LED the RGB write is a no-op on the hardware.
- **fix direction:**
  1. In `parseOutputs`, derive per-LED channel capability from `configfile.settings` — for `[led]`/`[pca9533]`
     etc.: presence of `red_pin`/`green_pin`/`blue_pin` (RGB) and `white_pin` (white); for
     `[neopixel]`/`[dotstar]`: parse `color_order` (e.g. "GRB"/"RGB" ⇒ RGB; "GRBW"/"RGBW" ⇒ RGB+W;
     "W"/white-only ⇒ white). Capture on `OutputDescriptor` (e.g. `ledHasRgb`, `ledHasWhite`).
  2. `OutputLedDetail`: if **RGB-capable** → hue wheel + brightness (existing path, layout fixed per GAP-A);
     if **white/brightness-only** → render a brightness **slider with step adjustment** ONLY (no hue wheel),
     dispatching the WHITE channel: `setLed(name, 0, 0, 0, w = brightness)` (clamp 0..1). Off stays all-zero.
     (RGBW lights: RGB wheel + brightness in v1; a separate white channel is out of scope unless trivial.)
  3. Seed brightness from the live `color_data[0]` white component for white-only; reflect on the row swatch
     (a white/grey swatch for white-only, not a colored chip).
- **files (likely):** `app/src/main/java/works/mees/dinghy/outputs/OutputDescriptor.kt`,
  `app/src/main/java/works/mees/dinghy/outputs/OutputsGate.kt`,
  `app/src/main/java/works/mees/dinghy/ui/outputs/OutputLedDetail.kt` (and possibly the row swatch in
  `OutputsHolder`/`OutputsScreen`). New gate tests off the real E5 fixtures (chamber_light=white-only,
  expanderpixel=RGB) — the fixtures already exist from 19-02. Update LED previews.
- **note:** re-capture the REQUIRED fan/LED/pin/servo `/server/gcode_store` wire evidence at the post-fix
  re-UAT (deferred this run).
