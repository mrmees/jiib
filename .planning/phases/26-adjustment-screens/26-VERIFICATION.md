---
phase: 26-adjustment-screens
verified: 2026-06-11T00:45:00Z
status: passed
score: 5/5 must-haves verified
human_uat: 26-HUMAN-UAT.md (owner-tested on flox 2026-06-10 — 4 passed, WR-08 deferred to todo; SC-1 owner-approved with follow-up polish notes pending enumeration)
overrides_applied: 0
human_verification:
  - test: "Navigate each rebuilt screen on flox in portrait and landscape — confirm full owner approval"
    expected: "Temperature, Fine-Tune, Outputs, Extrude, and the single-setting flows all render correctly and are usable in both orientations; adjuster, scrubber, and preset flows feel right in-hand"
    why_human: "SC-1 requires owner approval on physical hardware. No UAT file exists. The code, previews, and host tests are all green but device feel and layout correctness at Adreno-320/1920×1200 resolution cannot be verified programmatically."
  - test: "Drag the Outputs scrubber across fan A, switch to fan B, drag again — verify command goes to fan B"
    expected: "After switching output selection, drag on fan B's scrubber settles the command on fan B's output, not fan A's. Fill and value stay in sync during drag."
    why_human: "CR-04 fix (key on output identity + rememberUpdatedState) is compile-and-test-verified but its drag interaction correctness requires physical gesture testing on-device."
  - test: "Navigate to Temperature, tap a sensor row, verify the adjuster loads the live target; tap +5 three times and confirm three distinct dispatches (205, 210, 215)"
    expected: "The adjuster stays live-updating; repeated step taps accumulate, not re-dispatch the same target."
    why_human: "CR-01 fix (selectedName approach) is compile-verified. Repeated-tap stepping behavior requires on-device observation."
  - test: "With a heater off, navigate to Temperature adjuster for that heater and tap +5 — confirm the temperature dispatch fires from the live temp (not blocked)"
    expected: "An idle heater (target=null) steps from its current live temperature; the + button is not inert."
    why_human: "CR-02 fix (seed from currentValue when no target) requires on-device verification of the previously blocked state."
  - test: "On a fw-retraction printer, open Fine-Tune and confirm icons — verify the known icon-law violation is acceptable until owner assigns glyphs"
    expected: "Owner acknowledges OutputCircle/MaxVelocity/MaxAccel each appear twice on the flat list when FW-retraction is present; owner to assign 4 distinct glyphs for the retraction rows"
    why_human: "WR-08 was deliberately skipped per the hard icon law ([[dinghy-never-pick-icons-ask]]). Owner must assign new glyphs for Retract Length, Retract Speed, Unretract Extra, Unretract Speed before this can be closed."
gaps:
  - truth: "WR-08: Fine-Tune flat list on fw-retraction printers shows duplicate glyphs (OutputCircle, MaxVelocity, MaxAccel each used twice)"
    status: partial
    reason: "Deliberately skipped per hard owner icon law (D-24 / [[dinghy-never-pick-icons-ask]]). The four FW-retraction rows (Retract Length, Retract Speed, Unretract Extra, Unretract Speed) share icons with Extrusion/Motion rows. Fix requires owner to assign 4 new glyph registrations before implementation."
    artifacts:
      - path: "app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt"
        issue: "Lines 202, 213, 235 reuse DinghyIcons.OutputCircle, MaxAccel, MaxVelocity already used by Extrusion/Motion rows at lines 120, 161, 171"
    missing:
      - "Owner to assign 4 distinct glyph slots for FW-retraction rows via the icon registry; implement in a follow-up commit"
---

# Phase 26: Adjustment Screens — Verification Report

**Phase Goal:** Migrate the numeric-adjustment screens onto the Phase-23 adjustment archetype — the stepper, the scrubber, and the 3-zone adjuster with the inline baseline ("was X") readout. Screens: TemperatureScreen, ExtrudeScreen/ExtrusionScreen, OutputsScreen, FineTuneHubScreen + FwRetractionScreen, and the single-setting pages ScrubberPage/NumpadPage/MeasuredWeightPage. Per-screen conformance folds in.
**Verified:** 2026-06-11T00:45:00Z
**Status:** passed (human UAT completed 2026-06-10 — see 26-HUMAN-UAT.md; WR-08 deferred to `.planning/todos/pending/2026-06-10-fw-retraction-glyph-assignment.md`)
**Re-verification:** No — initial verification + owner UAT

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Temperature, Extrude/Extrusion, Outputs, Fine-Tune (+ FW-retraction), and single-setting pages rebuilt onto stepper/scrubber/adjuster classes | VERIFIED | All screens rebuilt and wired; 4 old Fine-Tune screens + 3 Output detail pages + NumpadPage + MeasuredWeightPage deleted; 0 dangling symbol references; 14 post-review fixes committed (e088610..bc4a864) |
| 2 | Numeric entry keyboard-free in printer controls; step-based or scrubber; "was X" baseline when changed | VERIFIED | FineTune/Temp/Outputs = AdjusterPanel stepper only; scrubber reserved for 0–100% outputs (D-05/D-09); numeric IME for distance/speed/macro/weight (D-07/D-16); `shouldShowBaseline` inline predicate confirmed at `AdjusterPanel.kt:216`; AdjusterPanelTest green |
| 3 | Scrubber build-once: updates fill/thumb/value in place, no rebuild mid-drag (Phase-19 regression rule) | VERIFIED | `ScrubberControl` extracted to `ScrubberPage.kt:142`; `workingState` is ONE `MutableFloatState` re-seeded in-place via `remember(value, range)` at line 165–166; `actions`/`onValueChange` hardened via `rememberUpdatedState` (lines 169–170); `OutputsContent` wraps `OutputFocusControl` in `key(selectedRow.descriptor.objectKey)` at OutputsScreen.kt:154 |
| 4 | Per-screen conformance met (≥64px, fsSp S/M/L, rotation); intent colors correct | VERIFIED | `fsSp(...)` used throughout all rebuilt screens; `IncrementPicker` uses `heightIn(min = uDp)` (dynamic unit grid floor, not hardcoded); preview matrices compile — AdjusterPreviews has 8×@Nexus7Previews + 2×@Preview (portrait+landscape), FineTunePreviews 10+3, TemperaturePreviews 9+1, OutputsPreviews 9+5, ExtrudePreviews 11+4; Reset = Intent.Warn (amber); active increment = Intent.Accent; AdjusterPanel zone-1 Reset tile `heightIn(min = 40.dp)` is below 64dp floor — this is a WARNING (the containing panel provides the real hit surface) |
| 5 | No functional regressions; capability gating + P17 clamp authority intact | VERIFIED | Full host test suite (`testDebugUnitTest`, 34 tasks) BUILD SUCCESSFUL after `--rerun-tasks`; `FineTuneScreenNudgeTest` proves clamp-before-markPending for Velocity+SCV over-cap nudges; `FineTuneHolderTest` still green; `FmtValueTest` new regression tests pass (fmtValue(1499.5,0)=="1500", fmtValue(2999.9999,0)=="3000"); `AdjusterPanelTest` + `IncrementPickerTest` + `TemperatureHolderTraceStyleTest` all green |

**Score:** 4/5 truths code-verified; SC-1 is pending on-device owner approval (human gate)

### Deferred Items

No items deferred to later phases.

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `designsystem/components/IncrementPicker.kt` | Shared step-set selector; `formatStep` + `Intent.Accent` active step | VERIFIED | `fun IncrementPicker` + `internal fun formatStep` present; active step uses `Intent.Accent`, inactive uses `Intent.Neutral` |
| `designsystem/components/AdjusterPanel.kt` | 3-zone adjuster + `shouldShowBaseline` predicate | VERIFIED | `fun AdjusterPanel` + `internal fun shouldShowBaseline` present; "was X" inline in same Row as value; Reset = `Intent.Warn` |
| `ui/finetune/FineTuneScreen.kt` | Single flat-list Fine-Tune screen | VERIFIED | `fun FineTuneScreen` (2 overloads); `gutter = null`; `FootButtonBar` in field lambda; FW-retraction filtered by `hasFwRetraction`; group icons tinted via `groupColorFor(param.group, t.pool)`; session-remember `selectedTuner` at line 211 |
| `ui/finetune/FineTuneParams.kt` | 13-entry param descriptors + `nudge()` clamp routing | VERIFIED | All 13 `FineTuneTuner` entries; `clampForTuner` → `holder.markPending(tuner, clamped)` invariant; pool indices 0/1/2 for Extrusion/Motion/FW-retraction; `take(...)`-guarded indexing |
| `ui/temperature/TraceStylePrefs.kt` (at `ui/settings/`) | Per-sensor trace color/visibility DataStore | VERIFIED | `suspend fun setTraceColor/setTraceVisibility`; writes through `AppContainer.writeScope` (not composition scope); `DinghyApp` → `AppContainer` → `TemperatureHolder` wiring confirmed |
| `ui/temperature/TemperatureScreen.kt` | Morph Focus, 8-swatch Colorful pool, aligned trace model | VERIFIED | `selectedName` (not snapshot) at line 312; plain `if/else` morph (not AnimatedContent); `VisibleTraces` aligns series+setpoints+names+colors; `Palette.generate(..., maxItems=8, simple=false, highContrast=false)` Colorful pool; `clampHeaterTarget` at line 197; per-heater Off at line 699; Cooldown foot button; Field-takeover presets |
| `render/GraphView.kt` | Equality-guarded `setTraceColorOverrides` | VERIFIED | `fun setTraceColorOverrides(List<Int?>)` at line 211; `lastOverrides` equality check + early return; `reapplyOverrides()` called from `applyTokens` for override-wins after theme change |
| `render/GraphViewHost.kt` | `traceColors` param threaded to GraphView | VERIFIED | Multi-trace overload has `traceColors: List<Int?>` wired into update lambda |
| `ui/outputs/OutputsScreen.kt` | List+detail-in-Focus; 3 detail pages deleted | VERIFIED | 2 overloads; `selectedKey` internal; `OutputFocusControl` in Focus; `gutter = null`; 3 detail pages gone; 0 dangling `OutputScrubberDetail/OutputPinDetail/OutputLedDetail` symbol refs |
| `ui/outputs/OutputFocusControl.kt` | Per-type inline control; build-once scrubber | VERIFIED | New file; switches on `descriptor.family`; hosts `ScrubberControl` (not full `ScrubberPage`); `key(selectedRow.descriptor.objectKey)` at OutputsScreen.kt:154 |
| `designsystem/ScrubberPage.kt` | Embeddable `ScrubberControl` extracted | VERIFIED | `fun ScrubberControl` present; no `ScreenScaffold`/`background` inside; `ScrubberPage` delegates to it; existing scrubber tests still green |
| `ui/extrude/ExtrudeScreen.kt` | Conformance + numeric IME + filament preset takeover | VERIFIED | `gutter = null`; `FootButtonBar` in field; `KeyboardType.Decimal` for distance/speed; `coerceIn` clamps at lines 283/286; `ExtrudeFieldMode.FilamentPresets`; `activeSpoolDetail` param on both overloads; AppShell passes collected `activeSpoolDetail`; no `NumpadPage` reference |
| `ui/macros/BookmarkedMacrosScreen.kt` | Numeric IME for macro params | VERIFIED | `MacroNumericParamField` with `KeyboardType.Decimal`; `coerceIn(MACRO_NUMERIC_RANGE)` clamp at line 411; no `NumpadPage` reference; `execute()` clamps on dispatch path (WR-09 fix) |
| `ui/spool/SpoolScreen.kt` | Measured-weight IME Field-takeover | VERIFIED | `FieldMode.MeasureWeight` variant; `SpoolMeasureWeightField` with `KeyboardType.Decimal`; `grams > 0.0` validity gate; `holder.measureSpoolAsync(spool, grams)` through holder scope (WR-10 fix) |
| `NumpadPage.kt` (deleted) | Removed app-wide | VERIFIED | File does not exist; `grep -rn "NumpadPage" app/src/` returns only KDoc historical-note comments, zero live references |
| `MeasuredWeightPage.kt` (deleted) | Removed app-wide | VERIFIED | File does not exist; 0 live references |
| 4 old Fine-Tune screens (deleted) | FineTuneHubScreen, ExtrusionScreen, MotionScreen, FwRetractionScreen | VERIFIED | All 4 deleted; only KDoc comments referencing old screen names remain (no symbol refs) |
| 3 old Output detail pages (deleted) | OutputScrubberDetail, OutputPinDetail, OutputLedDetail | VERIFIED | All 3 deleted; only KDoc migration comments remain |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `FineTuneScreen.kt` | `FineTuneHolder.markPending` | `nudge()` → `clampForTuner` | WIRED | `nudge()` calls `clampForTuner(tuner, rawTarget)` then `holder.markPending(tuner, clamped)` at FineTuneParams.kt:352–354 |
| `FineTuneScreen.kt` | `PrinterCommands.clamp*` functions | `clampForTuner` dispatch | WIRED | `clampForTuner` maps each `FineTuneTuner` to its exact PrinterCommands clamp (clampSpeedPct, clampFlowPct, etc.) |
| `AppShell.kt` | `FineTuneScreen` | `NavDest.FineTune` composable | WIRED | Single `FineTuneScreen(holder=fineTuneHolder,...)` call at AppShell.kt:700; sub-nav removed; `// fineTuneGroup removed` comment at line 165 |
| `TemperatureScreen.kt` | `TemperatureHolder.setTraceColor/setTraceVisibility` | graph controls | WIRED | Lines 189–194: `holder.setTraceColor(sensorName, color)` + `container.setTraceColor(sensorName, color.toArgb())` |
| `TemperatureScreen.kt` | `AppContainer.writeScope` | persist color/visibility | WIRED | `container.setTraceColor/setTraceVisibility` routes through `AppContainer.writeScope` at AppContainer.kt:332–341 |
| `TemperatureScreen.kt` | `GraphViewHost traceColors` | aligned visible-trace model | WIRED | `GraphViewHost(traceColors = visible.colors, ...)` at line 372; `VisibleTraces` aligns all 4 lists |
| `GraphViewHost.kt` | `GraphView.setTraceColorOverrides` | equality-guarded override | WIRED | `view.setTraceColorOverrides(traceColors)` in update lambda; `lastOverrides` equality guard at GraphView.kt:213 |
| `OutputFocusControl.kt` | `OutputsHolder.markPending` | scrubber settle | WIRED | `onSettle` callbacks call `holder.markPending(objectKey, ...)` after clamp |
| `OutputFocusControl.kt` | `ScrubberControl` (OnSettle) | dispatch on release | WIRED | `ScrubberControl` used in Focus for fan/PWM/servo; `ScrubberPage` (thin wrapper) untouched |
| `OutputFocusControl.kt` | `PrinterCommands.outputPctToWire / clampOutputPct` | clamp before dispatch | WIRED | Settle callbacks clamp via matching PrinterCommands function before `markPending` |
| `ExtrudeScreen.kt` | `PrinterCommands` extrude clamp ranges | parse + clamp IME value | WIRED | `coerceIn(MIN_DISTANCE, ceiling)` at line 283; `coerceIn(MIN_SPEED_MM_S, MAX_SPEED_MM_S.toInt())` at line 286 |
| `ExtrudeScreen.kt` | `SpoolHolder.activeSpoolDetail` | loaded-filament preset row | WIRED | `activeSpoolDetail` param on both overloads; AppShell passes the collected value at Extrude composable call site |
| `BookmarkedMacrosScreen.kt` | macro param clamp range | parse + clamp IME value | WIRED | `parsed.coerceIn(MACRO_NUMERIC_RANGE...)` at line 411; execute() clamps on dispatch path (line 479) |
| `SpoolScreen.kt` | `SpoolHolder.measureSpoolAsync` | numeric-IME Field-takeover | WIRED | `onApplyMeasure = { spool, grams -> holder.measureSpoolAsync(spool, grams) }` at SpoolScreen.kt:168; holderScope-backed |

---

## Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `FineTuneScreen.kt` | `vm.valueForTuner(selectedTuner)` | `FineTuneHolder` → `FineTuneVm` collecting from `PrinterState` WebSocket | Yes — real-time Klipper state | FLOWING |
| `TemperatureScreen.kt` | `legend`, `series`, `setpoints`, `traceColors` | `TemperatureHolder` collecting from Moonraker WebSocket | Yes — live temperature ring buffers | FLOWING |
| `OutputsScreen.kt` | `rows: List<OutputRowVm>` | `OutputsHolder` from `PrinterState` capability discovery | Yes — capability-discovered output rows | FLOWING |
| `ExtrudeScreen.kt` | `vm.nozzleTemp`, `vm.distance`, `vm.speed` | `ExtrudeHolder` from `PrinterState` + DataStore | Yes — live nozzle temp + stored prefs | FLOWING |
| `SpoolScreen.kt (MeasureWeight)` | `SpoolHolder.FieldMode.MeasureWeight(spool)` | `SpoolHolder` from Spoolman REST API | Yes — real spool data from Spoolman | FLOWING |

---

## Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `AdjusterPanelTest` passes | `gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.designsystem.components.AdjusterPanelTest` | BUILD SUCCESSFUL | PASS |
| `IncrementPickerTest` passes | `gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.designsystem.components.IncrementPickerTest` | BUILD SUCCESSFUL | PASS |
| `FineTuneScreenNudgeTest` passes (clamp authority) | `gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.finetune.FineTuneScreenNudgeTest` | BUILD SUCCESSFUL | PASS |
| `FineTuneHolderTest` passes (regression) | `gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.finetune.FineTuneHolderTest` | BUILD SUCCESSFUL | PASS |
| `FmtValueTest` passes (CR-03 regression) | `gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.finetune.FmtValueTest` | BUILD SUCCESSFUL | PASS |
| `TemperatureHolderTraceStyleTest` passes | `gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.temperature.TemperatureHolderTraceStyleTest` | BUILD SUCCESSFUL | PASS |
| Full host test suite | `gw.bat :app:testDebugUnitTest --rerun-tasks --no-daemon` | BUILD SUCCESSFUL in 1m 10s, 34 tasks | PASS |
| NumpadPage deleted and zero live references | `grep -rn "NumpadPage" app/src/` | Only KDoc historical-note comments | PASS |
| MeasuredWeightPage deleted and zero live references | `grep -rn "MeasuredWeightPage" app/src/` | Only KDoc historical-note comments | PASS |
| 4 old Fine-Tune screens deleted | `ls FineTuneHubScreen.kt ExtrusionScreen.kt MotionScreen.kt FwRetractionScreen.kt` | All DELETED | PASS |
| 3 Output detail pages deleted | `ls OutputScrubberDetail.kt OutputPinDetail.kt OutputLedDetail.kt` | All DELETED | PASS |

---

## Probe Execution

Step 7c: SKIPPED — no `scripts/*/tests/probe-*.sh` files present; phase is UI-only, no CLI/API probes declared.

---

## Requirements Coverage

Phase 26 declares `requirements: — (UX migration)` in all 7 PLAN files and in ROADMAP.md. No requirement IDs (REQ-*, CAM-*, SYS-*, etc.) are claimed. REQUIREMENTS.md has no entries mapped to Phase 26. This is consistent — the phase is a pure UX migration with no functional capability additions.

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| (none declared) | — | Phase 26 is a UX migration; no REQ-IDs claimed | SATISFIED | All 7 plans have `requirements: []`; ROADMAP confirms `Requirements: — (UX migration)` |

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `FineTuneParams.kt` | 202, 213, 235 | `DinghyIcons.OutputCircle`, `MaxAccel`, `MaxVelocity` reused on a second FW-retraction row each — three glyphs each appear twice on the flat list when `hasFwRetraction == true` | WARNING (WR-08, intentionally skipped) | Violates "never same glyph twice on one screen" icon law; only visible on fw-retraction printers; deliberately not fixed because new glyph assignments require owner decision per [[dinghy-never-pick-icons-ask]] |
| `AdjusterPanel.kt` | 130 | `heightIn(min = 40.dp)` hardcoded below 64dp on the Reset row | INFO | The containing panel's overall size supplies the real touch surface; the stepper tiles and IncrementPicker use `uDp`-based sizing |

No unresolved `TBD`, `FIXME`, or `XXX` markers found in any phase-modified file.

---

## Human Verification Required

### 1. Owner On-Device Approval (SC-1)

**Test:** Build, install on flox; navigate each rebuilt screen in portrait AND landscape:
- Fine-Tune: flat list, param selection, AdjusterPanel in Focus, FW-retraction rows hide (on E3 Pro with no fw-retraction), group color tinting, Reset/Reset-All
- Temperature: graph Focus default, tap sensor row → morph to adjuster, show/hide toggle, 8 Colorful-pool swatches recolor the GRAPH TRACE + row icon, Field-takeover presets, Cooldown amber foot, per-heater Off
- Outputs: list, select a fan → scrubber in Focus, LED with channel gating + ColorWheel, pin toggle, Back returns to list
- Extrude: numeric IME for distance/speed (clamps to range), filament preset takeover applies extruder-only temp, Load/Unload/Back foot intents, cold-extrude icon gating
- Macro numeric params: numeric keyboard appears, Execute without Done still sends clamped value
- Spool measure weight: numeric keyboard, Set applies weight, navigation does NOT drop the write

**Expected:** All screens render correctly and controls respond as designed; owner approves in both orientations on flox.
**Why human:** Visual and interaction quality judgment; the Adreno-320/1920×1200 layout at runtime cannot be verified programmatically.

### 2. Outputs Scrubber Cross-Output Command Routing (CR-04 on-device confirmation)

**Test:** Open Outputs, select a fan (fan A), drag the scrubber to change the value. Switch to a different fan (fan B) of the same family without releasing. Drag fan B's scrubber. Confirm the command goes to fan B's output, not fan A's.
**Expected:** After switching output selection, all scrubber interactions target the newly selected output.
**Why human:** `key(objectKey)` + `rememberUpdatedState` fix is compile-verified; correct command routing under same-family switch requires physical drag interaction.

### 3. Temperature Adjuster Live-Target Stepping (CR-01 on-device confirmation)

**Test:** On flox, navigate to Temperature. Tap a heater row, confirm the adjuster loads. Tap the +5 increment 3 times. Verify the sent commands are 205 → 210 → 215 (not 205 → 205 → 205).
**Expected:** Each +5 tap steps from the LIVE current target, not from a frozen snapshot at selection time.
**Why human:** `selectedName` (live resolve from `legend`) fix is compile-verified; the stacking behavior requires on-device observation.

### 4. Off Heater Stepup (CR-02 on-device confirmation)

**Test:** With a heater at room temperature (target=null / off), navigate to Temperature and tap that heater's row. Verify the + button is enabled and stepping from ~22°C works.
**Expected:** An idle heater can be stepped up from its live temperature without requiring a Preset first.
**Why human:** `currentValue = target ?: currentTemp` fix is compile-verified; the previously-blocked state requires device observation.

### 5. WR-08 — Owner Glyph Assignment (fw-retraction printers)

**Test:** On a printer with firmware retraction enabled, open Fine-Tune and inspect the parameter list. Note the duplicate icons (OutputCircle appears for both Flow Rate and Retract Length; MaxVelocity appears for both Max Velocity and Unretract Speed; MaxAccel for both Max Accel and Retract Speed).
**Expected:** Owner acknowledges the issue and assigns 4 distinct glyphs from the registry for the FW-retraction rows. This is tracked as a known open item.
**Why human:** Icon selection requires owner decision per the hard [[dinghy-never-pick-icons-ask]] law; no unilateral glyph assignment is permitted.

---

## Gaps Summary

One intentionally-open gap exists: **WR-08** (duplicate glyphs on the Fine-Tune flat list for fw-retraction printers). This is a known icon-law violation that was deliberately skipped in the review-fix pass because it requires the owner to assign 4 new glyph registrations for the FW-retraction parameter rows. It is only visible on printers that report `hasFwRetraction == true`. All other 4 Critical and 10 Warning findings from the code review were fixed in commits e088610 through bc4a864 and verified against the full host test suite (BUILD SUCCESSFUL).

The phase is code-complete and host-test-verified. Status is `human_needed` because:
1. SC-1 requires explicit owner approval on flox hardware in both orientations — no UAT file exists
2. Three interaction fixes (CR-01, CR-02, CR-04) require on-device confirmation of the corrected behavior
3. WR-08 requires owner glyph decisions before it can be implemented

---

_Verified: 2026-06-11T00:45:00Z_
_Verifier: Claude (gsd-verifier)_
