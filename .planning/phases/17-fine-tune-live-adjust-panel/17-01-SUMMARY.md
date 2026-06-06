---
phase: 17-fine-tune-live-adjust-panel
plan: 01
subsystem: testing
tags: [wave-0, red-scaffold, fine-tune, tdd-gate, command, state, holder, nav]
requires:
  - "Existing PrinterCommandsTest / CommandRegistryGcodeTest / PrinterStateReducerTest / ExtrudeHolderTest siblings"
  - "AvailabilityPredicate.ObjectPresent, CommandSpec, PrinterStateStore, AndroidJUnit4 test infra"
provides:
  - "RED executable Nyquist gate pinning every TUNE-01..07 Fine-Tune behavior before the building waves"
  - "5 compiling test files (3 extended + 2 new) — RED until 17-02/17-03/17-05/17-06 convert them GREEN"
affects:
  - "17-02 (command builders + registry specs), 17-03 (reducer fields), 17-05 (holder vm), 17-06 (nav + final gate)"
tech-stack:
  added: []
  patterns:
    - "[[dinghy-wave0-red-scaffold-compile]]: fail()-stubbed RED tests referencing only existing symbols so the whole sourceset compiles before --tests filter"
key-files:
  created:
    - app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneHolderTest.kt
    - app/src/androidTest/java/works/mees/dinghy/ui/finetune/FineTuneNavTest.kt
  modified:
    - app/src/test/java/works/mees/dinghy/command/PrinterCommandsTest.kt
    - app/src/test/java/works/mees/dinghy/command/CommandRegistryGcodeTest.kt
    - app/src/test/java/works/mees/dinghy/state/PrinterStateReducerTest.kt
decisions:
  - "All new tests are fail() stubs (not assertions against unbuilt symbols) per [[dinghy-wave0-red-scaffold-compile]] — Gradle compiles the WHOLE test sourceset before the --tests filter, so a forward-ref would brick every per-wave run."
  - "Each fail() message carries the EXACT target string/value/gate (M220 S105, M106 S153, MINIMUM_CRUISE_RATIO=0.55, ObjectPresent predicates, D-15 busy formula) so the building waves convert each stub to a typed assertion against a fixed target."
  - "androidTest compile gated explicitly (REVIEW #8) because FineTuneNavTest lives in androidTest — a forward-ref there is invisible to the unit-test compile."
metrics:
  duration: ~5 min
  completed: 2026-06-06
---

# Phase 17 Plan 01: Wave-0 RED Test Scaffolds Summary

Authored 5 compiling, RED test scaffolds (3 extended + 2 new) that pin every Fine-Tune behavior — exact gcode + scaling, registry membership + availability, reducer field-reads, holder vm derivation (incl. the D-15 state-flip busy model + nullable reset), and the nav route — as the executable Nyquist gate for TUNE-01..07, all RED until the building waves (17-02/03/05/06) turn them GREEN.

## What Was Built

**Task 1 — command-layer RED stubs** (`d4a42a1`):
- `PrinterCommandsTest`: 12 `fail()`-stubbed methods pinning exact gcode incl. scaling — `speedFactor` (M220, clamp 25..300), `flowFactor` (M221, clamp 50..150), `setVelocityLimit` per-field (VELOCITY / ACCEL / MINIMUM_CRUISE_RATIO clamp 0..1 / SQUARE_CORNER_VELOCITY), the min-cruise **display-percent / ratio-wire** case (+tap from 0.5 → `MINIMUM_CRUISE_RATIO=0.55`, REVIEW #9), `setPressureAdvance` (ADVANCE 3dp, SMOOTH_TIME 2dp), `setFan` pct→PWM (60%→S153, 100%→S255, 0%→S0), `setRetraction` four-field with **no Z_HOP**, and `Locale.US` no-comma.
- `CommandRegistryGcodeTest`: 6 `fail()`-stubbed methods pinning `.all` membership + `AvailabilityPredicate.ObjectPresent` per spec (`gcode_move`/`toolhead`/`extruder`/`fan`/`firmware_retraction`) + distinct per-field motion-limit dispatchKeys (Pitfall 4).

**Task 2 — state + holder + instrumented nav RED stubs** (`bd8c257`):
- `PrinterStateReducerTest`: 5 `fail()`-stubbed methods — toolhead motion-limit reduce (raw, no scaling), extruder PA + smooth_time from a separate extruder walk, fan.speed→partFanSpeed (raw 0..1), **synthetic firmware_retraction** (build-blind coverage), diff-merge retains omitted fields.
- `FineTuneHolderTest.kt` (NEW, mirrors `ExtrudeHolderTest`): 8 `fail()`-stubbed methods — ratio→% / 0..1→% / min-cruise-display-% scaling, capability gates (`hasFan`/`hasFwRetraction`), baseline folding, the **D-15 whole-group state-flip busy-lock** (`inFlight.isNotEmpty() || pendingStateFlip != null`, busy persists past the bare ack until the value flips to target — REVIEW #2), **nullable-baseline no-op reset** (REVIEW #3), null-shows-dash-not-zero.
- `FineTuneNavTest.kt` (NEW, androidTest): `fail()`-stubbed instrumented skeleton pinning `PrintStatusControlAction.Tune → Dest.FineTune` (GREEN in 17-06).

## Verification

- **Task 1:** `:app:testDebugUnitTest --tests PrinterCommandsTest --tests CommandRegistryGcodeTest` → `compileDebugUnitTestKotlin` SUCCEEDED, **18/18 new stubs RED** (12 + 6). Build exit 1 is the intended RED outcome, not a compile or logic error.
- **Task 2:** `:app:testDebugUnitTest --tests PrinterStateReducerTest --tests FineTuneHolderTest` → compile SUCCEEDED, **13/13 new stubs RED** (5 + 8). Separately `:app:compileDebugAndroidTestKotlin` → **BUILD SUCCESSFUL** (REVIEW #8 androidTest-compile gate, proving `FineTuneNavTest` has no forward-ref).
- No unrelated existing test's COMPILE regressed (full unit + androidTest sourcesets compiled clean; only the intentionally-RED new methods fail).

## Deviations from Plan

None - plan executed exactly as written. Every Wave-0 gap from 17-VALIDATION.md (incl. REVIEW #2 state-flip busy, #3 nullable reset, #9 min-cruise percent display) has a named RED test with a documented target.

## Notes for Building Waves

- 17-02 converts the Task-1 `fail()` messages to real `assertEquals` against the exact strings (`M220 S105`, `M106 S153`, `MINIMUM_CRUISE_RATIO=0.55`, `SET_RETRACTION ...` no Z_HOP) and `.all`/availability assertions.
- 17-03 turns the reducer stubs GREEN; 17-05 the holder stubs; 17-06 the nav test + the FINAL full-suite-green gate.
- **Per REVIEW #1:** Wave-1 plans (17-02, 17-03) MUST verify ONLY their own targeted `--tests <class>` — the holder/nav stubs stay RED until Waves 2–3, so the FULL host-suite-green gate is deferred to 17-06.

## Self-Check: PASSED

All 5 test files and the SUMMARY exist on disk; both task commits (`d4a42a1`, `bd8c257`) are present in git history.
