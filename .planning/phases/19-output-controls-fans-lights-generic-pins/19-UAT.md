---
status: pending
phase: 19-output-controls-fans-lights-generic-pins
plan: 19-08
deferred: false
result: "awaiting owner on-device gate — code built, host suite green (848/0/0), D-09 ligatures green, debug APK installed on flox"
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

### 1 — Output tile present + list filtered (SC-1) — PENDING
- Swipe up → confirm the **Output** tile is present (icon = the `output` glyph) and tap it.
- List shows ONLY whitelisted outputs (Filter Fan, Chamber Light, Expanderpixel, Camera Servo, Mosfet pins,
  virtual pins) — **NO** nozzle/bed/part-fan/temperature sensors.

### 2 — Filter Fan (fan_generic), state flip (SC-2) — PENDING
- Open → scrub to ~50% → release → fan responds; row value updates to ~50% (state flip).
- **gcode_store evidence (REQUIRED):**
  ```
  (paste SET_FAN_SPEED FAN=... S=... line here)
  ```

### 3 — Chamber Light / Expanderpixel (LED), color + Off (D-12) — PENDING
- Open → pick a hue + brightness → release → strip changes color; row shows swatch + brightness.
- Tap **Off** → strip goes dark.
- **gcode_store evidence (REQUIRED):**
  ```
  (paste SET_LED LED=... ... WHITE=0 line here)
  ```

### 4 — A Mosfet (output_pin), toggle/PWM (SC-2) — PENDING
- Open → toggle On/Off (digital) or scrub % (PWM) → pin responds; row reflects it.
- **gcode_store evidence (REQUIRED):**
  ```
  (paste SET_PIN PIN=... VALUE=... line here)
  ```

### 5 — Camera Servo, angle + value degrade (SC-3) + Off — PENDING
- Open → set an angle → servo moves; the row **hides** the value (servo value isn't the commanded angle).
- Tap **Off** → servo disables (WIDTH=0).
- **gcode_store evidence (REQUIRED):**
  ```
  (paste SET_SERVO SERVO=... ANGLE=.../WIDTH=0 line here)
  ```

### 6 — Failure path (toast-stay) — PENDING
- Trigger a failure (e.g. while disconnected, or an intentionally bad case) → a toast shows and you STAY on
  the page (no nav away).

### 7 — Mid-print availability (optional, if convenient) — PENDING
- Controls work WHILE the printer is printing (no print-state lockout).

### 8 — Perf / responsiveness (Adreno-320) — PENDING
- List scrolls and detail pages respond without frozen frames.

## Checks — E3P (192.168.1.121:7125, sparse config)

### 9 — Tile NOT hidden when exactly one output (D-10) — PENDING
- Confirm the **Output** tile IS present (≥1 output) and the list shows the single sparse output
  (Ignore M600) — proving the tile is not hidden when exactly one output exists.

## Summary

total: 9
passed: 0
issues: 0
pending: 9
skipped: 0
blocked: 0
accepted-exclusions: 1 (heater_generic — automated-only, no live printer exposes one)

## Gaps

(none recorded yet — populated on any FAIL)
