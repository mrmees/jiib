---
status: resolved
phase: 19-output-controls-fans-lights-generic-pins
plan: 19-08
deferred: false
result: "RESOLVED 2026-06-08. Round 1 (8 original plans): fans/output_pins/servo PASS, 2 gaps found (GAP-A off-button overlay, GAP-B LED ignores channel capability). Gap closure (19-09/19-10) fixed both; a brightness-control regression found at re-UAT (fill-from-middle + value-not-sticking) fixed in fa97efb. Round 2: owner-approved on flox — fan/LED/pin/servo all confirmed live WITH /server/gcode_store wire evidence. RGB LED page noted rough-but-good-enough (non-blocking future polish)."
updated: 2026-06-08
---

# Phase 19 — On-Device UAT (Output Controls — Fans, Lights & Generic Pins)

**Plan:** 19-08 (Task 2 — `checkpoint:human-verify`, blocking) + gap closure 19-09/19-10
**Status:** RESOLVED — owner-approved on flox against live E5P/E3P with required wire evidence captured.
**Build:** debug `app-armeabi-v7a-debug.apk` on flox (`0a64b42e`); final commit `fa97efb` (gap fixes + brightness regression fix).
**Device:** flox (LineageOS 18.1 / API 30, genuine Adreno 320 / 2GB / 1920×1200, `armeabi-v7a`)
**Printers (Moonraker):** Ender 5 Plus = `192.168.1.120:7125` (rich) · Ender 3 Pro = `192.168.1.121:7125` (sparse)

## Pre-gates (Claude — all GREEN)

- Full `:app:testDebugUnitTest` — **854 tests, 0 failures, 0 errors** (after gap closure; 848 pre-gap).
- `python tools/verify_ligatures.py` — exit 0, `missing: []` (D-09).
- `:app:installDebug` — BUILD SUCCESSFUL, installed on flox.

## Required command-evidence (CAPTURED — `GET /server/gcode_store`, E5P)

Objective HIGH-1 proof the BARE section name reached the wire through the catalog for all four families:

```
SET_FAN_SPEED FAN=FILTER_fan SPEED=0.87        # fan_generic — bare "FILTER_fan" (NOT "fan_generic FILTER_fan"), Off = SPEED 0
SET_PIN PIN=bed_safety_switch VALUE=1          # output_pin — bare name, digital
SET_LED LED=chamber_light RED=0 GREEN=0 BLUE=0 WHITE=0.92   # white-only LED drives the WHITE channel (GAP-B fix), Off = WHITE 0
SET_LED LED=expanderPixel RED=1 GREEN=0 BLUE=0 WHITE=0      # RGB LED — WHITE=0 policy
SET_SERVO SERVO=camera_servo ANGLE=78          # servo angle; Off = WIDTH=0 (also captured)
```

## Checks — E5P (rich config)

### 1 — Output tile present + list filtered (SC-1) — PASS
### 2 — Filter Fan (fan_generic), state flip (SC-2) — PASS (wire: `SET_FAN_SPEED FAN=FILTER_fan SPEED=…`)
### 3 — Chamber Light (white-only LED), brightness + Off (SC-2/SC-3, GAP-B) — PASS
- Now shows a brightness slider ONLY (no hue wheel); drag drives the WHITE channel and the value sticks
  (after the fa97efb fill-direction + value-stick fix). Wire: `SET_LED LED=chamber_light … WHITE=0.75…1.0`, Off `WHITE=0`.
### 3b — Expanderpixel (RGB neopixel), hue wheel — PASS (function); page aesthetics noted rough → non-blocking future polish
- Wire: `SET_LED LED=expanderPixel RED=… GREEN=0 BLUE=0 WHITE=0`.
### 4 — A Mosfet / output_pin, toggle/PWM (SC-2) — PASS (wire: `SET_PIN PIN=bed_safety_switch VALUE=1`)
### 5 — Camera Servo, angle + value degrade (SC-3) + Off — PASS (wire: `SET_SERVO SERVO=camera_servo ANGLE=… / WIDTH=0`)
### 6 — Off button placement (GAP-A) — PASS
- Off now sits in the gutter beside Back on every scrubber detail page (fan/PWM-pin/servo) — no longer overlaid.
### 7 — Perf / responsiveness (Adreno-320) — PASS (no frozen-frame complaints)

## Checks — E3P (sparse config)

### 8 — Tile NOT hidden when exactly one output (D-10) — PASS (round 1; single output_pin renders)

## Gaps

### GAP-A — Off button overlays the scrubber — RESOLVED (19-09)
- Fix: optional `onOff` gutter slot on `ScrubberActions.OnSettle`; overlay `Column` removed; LED double-scaffold collapsed to one. Verified on flox.

### GAP-B — LED page ignores channel capability — RESOLVED (19-10 + fa97efb)
- Fix: `ledHasRgb`/`ledHasWhite` derived in `parseOutputs`; white-only LED → brightness slider driving the WHITE
  channel; RGB LED → hue wheel (Codex SS-1: pca9533/pca9632 handled as RGBW, not pin-based). A regression in the
  19-09 inline brightness control (fill grew from center; settled value didn't stick — stale pointerInput closure)
  was found at re-UAT and fixed in `fa97efb` (internal `working` MutableState + left-anchored fill, mirroring
  ScrubberPage). Verified live with WHITE-channel wire evidence.

## Future polish (non-blocking, owner-noted)

- **RGB LED detail page aesthetics** — owner: "rgb is still pretty rough looking, but it's good enough for now."
  The white-only path (the owner's actual hardware) is clean; RGB hue-wheel + brightness layout is a candidate for
  a later visual pass. Not a v1 blocker.

## Summary

total: 9
passed: 9
issues: 0
pending: 0
skipped: 0
blocked: 0
accepted-exclusions: 1 (heater_generic — automated-only, no live printer exposes one)
future-polish: 1 (RGB LED page aesthetics — non-blocking)
