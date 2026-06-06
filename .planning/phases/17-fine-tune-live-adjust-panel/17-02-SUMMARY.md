---
phase: 17-fine-tune-live-adjust-panel
plan: 02
subsystem: command
tags: [wave-1, fine-tune, command-layer, gcode-builders, registry, tdd-green, clamp-before-format]
requires:
  - "17-01 RED scaffolds (PrinterCommandsTest + CommandRegistryGcodeTest fail()-stubs)"
  - "PrinterCommands purity discipline (formatZ/clamp idiom), CommandRegistry.gcode() factory, AvailabilityPredicate.ObjectPresent"
  - "docs/commands catalog.json + printer-matrix.json sidecars (CommandCatalogDriftTest enforcement)"
provides:
  - "Six pure clamp-before-format gcode builders: speedFactor/flowFactor/setVelocityLimit/setPressureAdvance/setFan/setRetraction"
  - "Six CommandRegistry gcode() specs registered in .all, object-gated, distinct per-field dispatchKeys"
  - "Shared per-precision fmt(v, decimals) Locale.US chokepoint (generalizes formatZ)"
affects:
  - "17-03 (reducer fields the holder reads), 17-05 (holder dispatches these specs), 17-06 (nav + final full-suite gate)"
tech-stack:
  added: []
  patterns:
    - "Nullable-field buildString: one SET_VELOCITY_LIMIT / SET_PRESSURE_ADVANCE spec serves all fields; the field arg selects which builder arg is non-null"
    - "Distinct dispatchKey per motion-limit field (set_vel_<field> / set_pa_<field>) — Pitfall 4"
    - "Build-blind capability gate: ObjectPresent(firmware_retraction) mirrors quadGantryLevel; matrix records the not_on_printers exclusion"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt
    - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
    - app/src/test/java/works/mees/dinghy/command/PrinterCommandsTest.kt
    - app/src/test/java/works/mees/dinghy/command/CommandRegistryGcodeTest.kt
    - docs/commands/catalog.json
    - docs/commands/printer-matrix.json
decisions:
  - "minCruiseRatio is RATIO-on-the-wire (REVIEW #9): builder formats the ratio verbatim (0.55), display↔percent conversion deferred to the holder — never percent-scales here."
  - "setFan computes round(pct/100*255) from the DISPLAYED % each tap (no rounding accumulation) per RESEARCH Scaling notes / Pitfall 1."
  - "VelocityLimitArgs/PressureAdvanceArgs carry a field string + value so ONE spec serves all motion-limit / PA fields; distinct dispatchKey per field avoids busy-lock key-collision (Pitfall 4)."
  - "setRetraction gate = ObjectPresent(firmware_retraction), built blind (neither dev printer exposes it) — added a matrix not_on_printers exclusion mirroring quad_gantry_level so CommandCatalogDriftTest stays green."
  - "Generalized formatZ -> fmt(v, decimals); formatZ now delegates to fmt(_,2). Single Locale.US trailing-zero-strip chokepoint, no inlined String.format at call sites (ASVS V5 / T-17-02-03)."
metrics:
  duration: ~12 min
  completed: 2026-06-06
---

# Phase 17 Plan 02: Fine-Tune Command Layer Summary

The clamp-before-format gcode builders (D-03..D-12) and their capability-gated `CommandRegistry` specs are built, turning the 17-01 command-layer RED tests GREEN. Every live-adjust mutation now funnels through the existing dispatcher as a `gcode()` spec — M220/M221 speed/flow, SET_VELOCITY_LIMIT (×4 motion limits), SET_PRESSURE_ADVANCE (advance + smooth), M106 part-fan, and the build-blind SET_RETRACTION — each clamped, `Locale.US`-formatted, and scaled correctly across the ratio↔percent / 0–1↔0–255 boundaries.

## What Was Built

**Task 1 — pure builders** (`d94d6e1`):
- Added named clamp bounds to the existing consts block (SPEED/FLOW pct, VEL/ACCEL/SCV/min-cruise, PA/SMOOTH, FAN pct, RETRACT len/extra/speed) from the RESEARCH range table — UI guard rails (Klipper is the real authority).
- `speedFactor(pct)` = `M220 S<pct>` (D-03), `flowFactor(pct)` = `M221 S<pct>` (D-08) — both `coerceIn`-clamped; the holder scales the stored ratio ×100 (off-by-100 trap stays at the boundary, not here).
- `setVelocityLimit(velocity?, accel?, minCruiseRatio?, scv?)` nullable-field `buildString` — appends ONLY the non-null field, each clamped; `minCruiseRatio` is the ratio on the wire (`0.55`, REVIEW #9).
- `setPressureAdvance(advance?, smoothTime?)` — ADVANCE 3dp, SMOOTH_TIME 2dp, no `EXTRUDER=` (single-extruder v1, D-09).
- `setFan(pct)` = `M106 S<round(pct/100*255)>` from the displayed % each tap (D-11).
- `setRetraction(retractLength, unretractExtraLength, retractSpeed, unretractSpeed)` — four fields, lengths 1dp, speeds int, **no Z_HOP** (D-12).
- Generalized `formatZ` → `fmt(v, decimals)` (Locale.US + trailing-zero strip); `formatZ` delegates to `fmt(_, 2)`.
- Converted 12 `PrinterCommandsTest` `fail()` stubs to `assertEquals` incl. `MINIMUM_CRUISE_RATIO=0.55` and the no-comma Locale.US assertion.

**Task 2 — registry specs + arg classes + sidecars** (`22785d5`):
- Added `SpeedFactorArgs`/`FlowFactorArgs`/`VelocityLimitArgs`/`PressureAdvanceArgs`/`FanArgs`/`RetractionArgs`. `VelocityLimitArgs` + `PressureAdvanceArgs` carry a `field` string + value so ONE spec serves all of their fields.
- Six `gcode()` specs registered in `.all`, gated: speedFactor/flowFactor → `ObjectPresent("gcode_move")`, setVelocityLimit → `toolhead`, setPressureAdvance → `extruder`, setFan → `fan`, setRetraction → `firmware_retraction` (build-blind, mirrors `quadGantryLevel`).
- Distinct dispatchKeys: `set_vel_<field>` and `set_pa_<field>` (Pitfall 4) — verified `set_vel_velocity`/`accel`/`minCruiseRatio`/`scv` all distinct.
- Sidecars: added the missing `KGC-M106` catalog row + `command_availability` rows for all six catalogIds + a `firmware_retraction` `not_on_printers` exclusion → `CommandCatalogDriftTest` green.
- Converted 6 `CommandRegistryGcodeTest` `fail()` stubs to membership + availability + byte-identical-delegation + distinct-key assertions.

## Verification

- **Task 1:** `:app:testDebugUnitTest --tests PrinterCommandsTest` → BUILD SUCCESSFUL, exit 0 (all 12 new + existing assertions GREEN).
- **Task 2:** `:app:testDebugUnitTest --tests CommandRegistryGcodeTest --tests CommandCatalogDriftTest` → BUILD SUCCESSFUL, exit 0.
- Acceptance greps: 6 builders present; exactly 1 `ObjectPresent("firmware_retraction")`; no `String.format` without `Locale.US` (the only hit is a KDoc comment).
- Per REVIEW #1, did NOT run the full host suite — 17-01's holder/nav RED stubs stay RED until Waves 2–3; the full-suite-green gate is 17-06.

## Deviations from Plan

None — plan executed exactly as written. The two sidecar edits (adding `KGC-M106` + the firmware_retraction exclusion) were anticipated by the plan's Task-2 action ("add the corresponding rows to keep CommandCatalogDriftTest green"); `KGC-M106` was the only one of the six catalogIds genuinely absent (the other five reference rows already existed in klipper-gcode.md/catalog.json).

## Self-Check: PASSED

Both task commits (`d94d6e1`, `22785d5`) are present in git history; all four modified source/test files and both sidecars are committed; the two targeted verification commands returned exit 0.
