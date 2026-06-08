---
phase: 19-output-controls-fans-lights-generic-pins
plan: 04
subsystem: state
tags: [moonraker, outputs, discovery, subscribe, reducer, case-recovery, high-1, d-10]

# Dependency graph
requires:
  - phase: 19-02
    provides: real E5P/E3P configfile.settings + objects.list fixtures and the OutputsGateTest RED scaffold
  - phase: 19-03
    provides: PrinterCommands bare-name builders + clamp authority (consumes objectKey/commandName split this plan produces)
provides:
  - OutputDescriptor model with the HIGH-1 objectKey (state/busy key) + commandName (G-code) split
  - pure OutputsGate.parseOutputs (configfile.settings ∩ WHITELIST ∩ objects.list, case-recovered, prettified, alpha-sorted) + WHITELIST set
  - deriveSubscribeSet wiring of whitelisted output objects (pre-configfile, single subscribe-set owner)
  - 5th configfile consumer on the existing one-shot query (no new query) with clear-on-failure/switch
  - PrinterState.outputs map + OutputLiveValue reduced RAW with update-on-present; heater_generic single-sourced via heaters
  - PrinterStateStore.outputDescriptors StateFlow seam
  - AppContainer.outputsPresent Flow<Boolean> (D-10 drawer tile gate)
affects: [19-05/06 output detail pages + holder, Wave-3 drawer tile, 19-VALIDATION]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "HIGH-1 naming split: objectKey (full, case-preserved, state/busy key) vs commandName (bare, G-code) carried on one descriptor"
    - "Case-recovery: lowercased settings key → case-preserved objects.list name (the hasMacroIgnoreCase lesson); no live match → DROP"
    - "Subscribe set is owned by deriveSubscribeSet (from objects.list, pre-configfile) — never ad-hoc session additions"
    - "5th consumer off the SAME one-shot configfile query (Pitfall 3, no duplicate query), modeled on setScrewsTiltConfig seam"
    - "Clear-on-failure/switch: setOutputDescriptors(emptyList()) before re-read and on the failure path (no stale hardware controls)"
    - "RAW live values in the reducer (Pitfall 1); update-on-present merge (absent field retained, never clobbered)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/outputs/OutputDescriptor.kt
    - app/src/main/java/works/mees/dinghy/outputs/OutputsGate.kt
    - app/src/test/java/works/mees/dinghy/state/PrinterStateReducerOutputsTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterState.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/test/java/works/mees/dinghy/outputs/OutputsGateTest.kt

key-decisions:
  - "outputsPresent derives off the store's outputDescriptors StateFlow via spine.flatMapLatest — no new SpineHandle field needed (SpineHandle already exposes the store)"
  - "readOnly = settings contains a `static_value` key; NO fixture exercises it (both dev printers' outputs are commandable), so readOnly is false everywhere on real data — guarded structurally"
  - "OutputLiveValue keeps nullable temperature/target fields for model completeness but the reducer NEVER populates them for heater_generic (single-sourced via heaters); divergence test enforces this"
  - "led/neopixel color_data uses whole-array replace semantics (a present color_data diff replaces; absent retains index 0) — matches Klipper's color_data being a full strip array, not a field-merge"

patterns-established:
  - "Output families intersect-with-detected in deriveSubscribeSet reuse OutputsGate.WHITELIST as the single source of the family list (reducer's isReducedOutputObject also reuses it)"

requirements-completed: [SC-1, SC-3]

# Metrics
duration: ~22min
completed: 2026-06-08
---

# Phase 19 Plan 04: Output Discovery + Live-State Spine Summary

**The output discovery + live-state data layer: a pure `parseOutputs` gate (configfile.settings ∩ whitelist ∩ objects.list, case-recovered, prettified, alpha-sorted) producing the HIGH-1 `objectKey`/`commandName` split, output objects added to the subscribe set in `deriveSubscribeSet`, a 5th consumer on the existing one-shot configfile query with clear-on-failure/switch, RAW live-value reduction into `PrinterState.outputs`, and the `outputsPresent` D-10 gate — all host-tested on the real E5P/E3P fixtures.**

## Performance

- **Duration:** ~22 min
- **Tasks:** 2 (both TDD)
- **Files modified:** 3 created + 7 modified

## Accomplishments

- **Task 1 — OutputDescriptor + parseOutputs (SC-1, HIGH-1):** `OutputDescriptor` carries the unambiguous HIGH-1 split — `objectKey` (the FULL case-preserved name, the state-map/busy/dispatch key) AND `commandName` (the BARE section name, the only name a G-code builder accepts). `OutputsGate.parseOutputs` implements the settled 7-step discovery algorithm: whitelist-filter (10 families), case-recover the objectKey from `objects.list` (drop configured-but-not-loaded entries — the `hasMacroIgnoreCase` lesson), read pwm/maximum_servo_angle/static_value, prettify the bare name (no camelCase split), sort by prettyName. `OutputsGateTest` turned GREEN on the real captures: E5P parses to exactly the 9 whitelisted outputs (1 fan_generic, 1 led, 1 neopixel, 2 servo, 4 output_pin), `objectKey` is case-PRESERVED (`FILTER_fan`, `expanderPixel`), `commandName` is bare, excluded sections never appear, pwm digital/PWM split holds, servoAngleMax defaults 180, E3P is the sparse single-output_pin case.
- **Task 2 — subscribe set + 5th consumer + reducer + outputsPresent (SC-1/SC-3):** `deriveSubscribeSet` adds every detected object whose first token ∈ `OutputsGate.WHITELIST` (from `objects.list`, before the configfile read — the single subscribe-set owner). MoonrakerSession reads the descriptors as a 5th consumer off the SAME one-shot configfile query (no new query — Pitfall 3 holds, grep count = 1), parsing with the case-preserved `capabilities.objects`; `setOutputDescriptors(emptyList())` is called BEFORE the re-read and on the `.onFailure` path (clear-on-failure/switch, T-19-04-04). `PrinterState.outputs` (keyed by objectKey) + `OutputLiveValue` reduce fan/led/servo/pin/pwm_tool RAW (no scaling — Pitfall 1) with update-on-present merge; heater_generic is single-sourced through the existing `heaters` map. `PrinterStateStore.outputDescriptors` StateFlow seam re-emits each handshake; `AppContainer.outputsPresent` gates the D-10 drawer tile. `PrinterStateReducerOutputsTest` GREEN (RAW reduce, partial-diff retain, absent-object retain, color_data index-0 retain + whole-array replace, heater_generic divergence).

## Task Commits

1. **Task 1: OutputDescriptor + pure parseOutputs gate** — `b902b2b` (feat)
2. **Task 2: subscribe set + 5th configfile consumer + reducer + outputsPresent** — `e8ac5b4` (feat)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/outputs/OutputDescriptor.kt` — created; HIGH-1 objectKey/commandName split + pwm/servoAngleMax/readOnly metadata
- `app/src/main/java/works/mees/dinghy/outputs/OutputsGate.kt` — created; WHITELIST + pure parseOutputs (case-recovery, prettify, sort) + hasAnyOutput
- `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` — output families added to the subscribe subset (intersect-with-detected, A3)
- `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` — 5th configfile consumer (no new query) + clear-before-reread + clear-on-failure
- `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` — `outputs: Map<String, OutputLiveValue>` + `OutputLiveValue` data class
- `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt` — non-heater output reduce loop (RAW, update-on-present) + `isReducedOutputObject`
- `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` — `outputDescriptors` StateFlow + `setOutputDescriptors`
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — `outputsPresent: Flow<Boolean>` (D-10)
- `app/src/test/java/works/mees/dinghy/outputs/OutputsGateTest.kt` — RED scaffold turned GREEN on real fixtures
- `app/src/test/java/works/mees/dinghy/state/PrinterStateReducerOutputsTest.kt` — created; merge/retain/color_data/divergence

## Decisions Made

- `outputsPresent` derives off the store's `outputDescriptors` via `spine.flatMapLatest` — no new `SpineHandle` field, since `SpineHandle` already exposes the concrete `store` (mirrors how `AppShell` reaches `spine.store`).
- `readOnly` keys off a `static_value` settings key. No fixture exercises a static pin (both dev printers' pins/outputs are commandable), so `readOnly` is false on all real data — the rule is structurally present for the static-pin case (SC-3) but unproven by live data.
- `OutputLiveValue` keeps nullable `temperature`/`target` for model completeness; the reducer never populates them for heater_generic (single-sourced via `heaters`). The divergence test asserts heater_generic is observable via `heaters` and `null` in `outputs`.
- led/neopixel `color_data` uses whole-array replace semantics (present diff replaces, absent retains index 0) — Klipper's `color_data` is the full strip array, not a field-merged sub-object.

## Deviations from Plan

None — both tasks executed as written. (One trivial build fix: a `*/` literal inside an OutputsGate KDoc — the `stepper_*/tmc*` glob text — closed the comment early and broke compilation; reworded to comma-separated prose, no behavior change.)

## Issues Encountered

- Initial `:app:compileDebugKotlin` failure: a `stepper_*/tmc*` sequence inside an OutputsGate KDoc was parsed as the `*/` comment terminator. Fixed by rewording the comment; rebuilt GREEN.

## Verification

- `:app:testDebugUnitTest --tests *OutputsGateTest --tests *PrinterStateReducerOutputsTest` — BUILD SUCCESSFUL (both classes GREEN), whole module + test sourceset compiled.
- `grep -c 'ObjectSubsetArgs(setOf("configfile"))' MoonrakerSession.kt` → 1 (Pitfall 3: still one configfile query, no duplicate).
- E5P fixture parses to exactly 9 whitelisted outputs; objectKey case-preserved; commandName bare (HIGH-1); excluded sections absent; pwm digital/PWM split; servoAngleMax 180.
- Reducer: RAW values (no /100); update-on-present (absent field retained); color_data index-0 retained on partial; heater_generic single-sourced via `heaters`, null in `outputs`.

## Known Stubs

None. The data layer is fully wired (discovery → subscribe → reduce → gate flow). The detail pages/holder that consume this layer are Wave-2 (19-05/06) and out of scope here; the still-RED `OutputsHolderTest` (19-02 scaffold) is owned by a later plan (HIGH-4 per-class scoping), not this one.

## Next Phase Readiness

- Wave-2 holder/detail pages (19-05/06) join `OutputDescriptor` (objectKey/commandName) to `PrinterState.outputs[objectKey]` for live values and `PrinterStateStore.outputDescriptors` for the list; heater_generic reads from `PrinterState.heaters[objectKey]`.
- The Wave-3 drawer tile gates on `AppContainer.outputsPresent`.
- No blockers.

## Self-Check: PASSED

All 3 created files verified on disk; both task commits (`b902b2b`, `e8ac5b4`) verified in git log.

---
*Phase: 19-output-controls-fans-lights-generic-pins*
*Completed: 2026-06-08*
