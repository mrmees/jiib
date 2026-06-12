---
phase: 17-fine-tune-live-adjust-panel
verified: 2026-06-08T12:00:00Z
status: passed
score: 8/8
overrides_applied: 0
re_verification: null
gaps: []
deferred: []
human_verification: []
---

# Phase 17: Fine-Tune / Live-Adjust Panel — Verification Report

**Phase Goal:** Wire the stubbed "Tune" button into a real live-adjustment surface organized by failure-mode into Motion (speed M220, accel/SET_VELOCITY_LIMIT, max velocity, SCV) and Extrusion (flow M221, pressure advance, firmware retraction where present, part-cooling fan). Capability-gated; keyboard-free scrubbers dispatching through the shared command primitive; state-flip confirmation (no ConfirmGuard). Always-available; per-control reset. UAT proven on a live E5 print, 8/8 checks on flox (owner-approved 2026-06-07).
**Verified:** 2026-06-08
**Status:** PASSED
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | The Tune button navigates to the Fine-Tune Hub (Motion + Extrusion entries); Fine-Tune always opens the Hub on entry (TUNE-01 / REVIEW #6) | VERIFIED | `PrintStatusScreen.kt:283` routes `PrintStatusControlAction.Tune -> onNavigate(Dest.FineTune)`; `ShellNavState.navigateTo()` calls `applyEntryReset()` on both dest-change AND same-dest re-selection (`ShellNavState.kt:95-100`), setting `fineTuneGroup = null`; UAT Check 1 PASS + Check 8 gap closed by `2bebae0` |
| 2 | Motion controls (speed M220, max velocity/accel/SCV via SET_VELOCITY_LIMIT, min cruise) are present, capability-gated, and each nudge dispatches the correct gcode via the command layer | VERIFIED | `MotionScreen.kt` renders 5 capability-gated tiles (`hasGcodeMove` / `hasToolhead`); `PrinterCommands.speedFactor`/`setVelocityLimit` builders exist and are invoked via `CommandRegistry.speedFactor`/`setVelocityLimit`; UAT Check 2 PASS (speed/accel tiles flip on live E5 print) |
| 3 | Extrusion controls (flow M221, pressure advance, smooth time, part-cooling fan, FW-retraction entry where present) are present and capability-gated; flow and PA usable cold (no temperature guard); FW-retraction hidden on printers without the object | VERIFIED | `ExtrusionScreen.kt` gates flow on `hasGcodeMove`, PA/smooth on `hasExtruder`, fan on `hasFan`, FW-retraction entry on `hasFwRetraction` with no temperature check (REVIEW #5); UAT Check 3 PASS (Flow/PA/fan flip, FW-retraction absent on E5/E3 = capability-gate correct) |
| 4 | Per-control reset: speed/flow reset to 100%, motion limits + PA reset to config baseline (nullable — no reset shown if baseline absent); part-cooling fan has no reset (no configured baseline) | VERIFIED | `MotionScreen.kt:176-179` speed reset → `clampSpeedPct(100)`; `ExtrusionScreen.kt:183-186` flow reset → `clampFlowPct(100)`; PA/smooth/velocity-limits use nullable `vm.baselines.*?.let{ ... }` so `onReset = null` when absent; fan `onReset = null` explicit (`ExtrusionScreen.kt:286`); UAT Check 4 PASS |
| 5 | Whole-group busy lock holds from dispatch until the printer-object value actually flips (state-flip, not bare ack); group stays locked through any intermediate reading; unlocks only when reported value equals clamped target within per-tuner epsilon | VERIFIED | `FineTuneHolder.kt:280-283` `reached()` uses `abs(current - flip.target) < toleranceFor(tuner)` (STRICT `<`); `toleranceFor()` returns `step * 0.1` (one-tenth of the step, below any intermediate reading); `buildVm()` sets `groupBusy = inFlight.isNotEmpty() \|\| pending != null`; UAT Check 5 PASS |
| 6 | A nudge at the clamp ceiling (e.g. Flow 150% tap '+') does NOT wedge the group busy/inert permanently; the skip-arm guard prevents arming an unreachable flip; a seq-guarded 8s timeout backstop guarantees recovery even if armed | VERIFIED | `FineTuneHolder.markPending()` compares clamped target against reported value within `toleranceFor(tuner)` before arming (`kt:182-187`); all 7 unclamped-max call sites now feed `PrinterCommands.clamp*()` output (`MotionScreen`, `ExtrusionScreen`, `FineTuneShared.VelocityLimitTile`, `FwRetractionScreen`); timeout backstop at `FineTuneHolder.kt:191-196` guarded by `armed.seq`; 5 regression tests GREEN; UAT Check 6 RESOLVED (commits `5301c83` + `f0d96f9`, on-device re-verified owner-approved) |
| 7 | Every control is always-available (not print-gated); controls are bounded/clamped; gcode is correct (M220/M221/SET_VELOCITY_LIMIT/SET_PRESSURE_ADVANCE/M106/SET_RETRACTION with correct scaling) | VERIFIED | `CommandRegistry` specs use `AvailabilityPredicate.ObjectPresent(obj)` (object-gated, not print-gated); `PrinterCommands` builders tested at-cap in `PrinterCommandsTest` (speedFactor/flowFactor/setVelocityLimit/setPressureAdvance/setFan/setRetraction — all gcode strings verified); min-cruise percent-display/ratio-wire REVIEW #9 covered in tests |
| 8 | Re-entering Fine-Tune from its own drawer tile while already on a group sub-page (e.g. Motion) resets to the Hub, not a stale page | VERIFIED | `ShellNavState.navigateTo()` extracts per-dest resets into `applyEntryReset(target)` and calls it before the same-dest early-return (`kt:95-96`); commit `2bebae0`; UAT Check 8 RESOLVED (manual flox owner-approved) |

**Score:** 8/8 truths verified

### Deferred Items

None.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt` | State-flip busy lock, per-tuner epsilon, skip-arm guard, timeout backstop | VERIFIED | 338 lines; `toleranceFor()` + `markPending()` skip-arm + `flipSeq` monotonic seq + `PENDING_FLIP_TIMEOUT_MS = 8000L` backstop all present; `FLIP_TOLERANCE` flat const absent (grep count = 0) |
| `app/src/main/java/works/mees/dinghy/ui/finetune/MotionScreen.kt` | 5 capability-gated tiles, clamp-fed markPending, dispatch | VERIFIED | 284 lines; `PrinterCommands.clampSpeedPct()` at all Speed call sites; VelocityLimitTile used for Vel/Accel/SCV |
| `app/src/main/java/works/mees/dinghy/ui/finetune/ExtrusionScreen.kt` | Flow/PA/smooth/fan/FW-retraction entry, no cold guard, clamp-fed markPending | VERIFIED | 315 lines; `PrinterCommands.clampFlowPct/clampPressureAdvance/clampSmoothTime` at all call sites; `onReset = null` for fan |
| `app/src/main/java/works/mees/dinghy/ui/finetune/FwRetractionScreen.kt` | 4-field retraction screen, clamped markPending targets (build-blind) | VERIFIED | 221 lines; `clampRetractionTarget()` helper at `kt:90` feeds clamped target to `holder.markPending` |
| `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHubScreen.kt` | Hub with Motion + Extrusion entries | VERIFIED | 155 lines; `onNavigate(FineTuneGroup.MOTION)` and `onNavigate(FineTuneGroup.EXTRUSION)` |
| `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt` | VelocityLimitTile with clamp authority | VERIFIED | `clampVelocityLimitTarget()` maps VELOCITY/ACCEL/SCV to `PrinterCommands.clamp*` (`kt:46-48`) |
| `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt` | 7 public clamp functions + builders delegating to them | VERIFIED | `clampSpeedPct`, `clampFlowPct`, `clampVelocity`, `clampAccel`, `clampScv`, `clampPressureAdvance`, `clampSmoothTime` at lines 123-150; `clampMinCruiseRatio` at line 144 (WR-03 fix); builders delegate to same clamps |
| `app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt` | `applyEntryReset()` runs on same-dest re-selection | VERIFIED | `navigateTo()` calls `applyEntryReset(target)` at `kt:96` before the same-dest early-return; `fineTuneGroup = null` in `applyEntryReset` at `kt:123` |
| `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` | `Dest.FineTune` in the `Dest` enum | VERIFIED | `Dest` enum at line 37 contains `FineTune` |
| `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` | `FineTuneHolder` per spine; `Dest.FineTune` arm with Hub/Motion/Extrusion/FwRetraction + BackHandler + onFwRetraction | VERIFIED | `fineTuneHolder = remember(store) { FineTuneHolder(...) }` at line 336; `Dest.FineTune` arm at line 642; all three group screens + `onFwRetraction` wire at lines 656-670 |
| `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` | Fine-Tune drawer tile (`instant_mix` glyph) | VERIFIED | `DrawerTileSpec(label = "Fine-Tune", symbol = "instant_mix", dest = Dest.FineTune)` at line 168 |
| `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` | Tune button → `onNavigate(Dest.FineTune)` (stub removed) | VERIFIED | Line 283: `PrintStatusControlAction.Tune -> onNavigate(Dest.FineTune)` |
| `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` | New readback fields (maxVelocity, maxAccel, minimumCruiseRatio, scv, pressureAdvance, smoothTime, partFanSpeed, firmwareRetraction) | VERIFIED | All 8 fields present at lines 141-174; `FirmwareRetractionObject` data class at line 182 |
| `app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneHolderTest.kt` | Holder tests including gap-closure regressions | VERIFIED | 382 lines; all planned methods present: `vm_scales_*`, `vm_gates_on_capabilities`, `vm_folds_baselines`, `groupBusy_persists_until_state_flip`, `reset_isNoOp_whenBaselineNull`, `nullValue_shows_dash_not_zero`; gap-closure regressions: `smallStep_inRange_nudge_arms_and_holds_until_value_equals_target`, `groupBusy_releases_when_target_at_cap_already_reported`, `smallStep_atCap_noop_releases`, `groupBusy_releases_via_timeout_on_unreachable_target`, `rapidDoubleTap_staleTimer_doesNotClearNewerFlip` |
| `app/src/androidTest/java/works/mees/dinghy/ui/finetune/FineTuneNavTest.kt` | Instrumented nav test | VERIFIED | File exists; note: both methods fail on flox due to pre-existing swipe-gesture harness defect (single-event `dragAmount` < `SWIPE_UP_THRESHOLD_PX`); manual eyeball is the authoritative gate per owner decision — deferred test-hardening |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `PrintStatusScreen.kt:283` | `Dest.FineTune` | `onNavigate(Dest.FineTune)` | WIRED | Tune action routes live |
| `AppDrawer.kt:168` | `Dest.FineTune` | `DrawerTileSpec(dest = Dest.FineTune)` | WIRED | Drawer tile routes live |
| `AppShell.kt:336` | `FineTuneHolder` | `remember(store) { FineTuneHolder(scope, store) }` | WIRED | One holder per spine |
| `AppShell.kt:642-670` | Hub/Motion/Extrusion/FwRetraction screens | `Dest.FineTune` arm, `fineTuneGroup` sub-nav | WIRED | All 4 screens + BackHandler + onFwRetraction threaded |
| `MotionScreen.kt:167-177` | `PrinterCommands.clampSpeedPct` | markPending call sites | WIRED | Clamped targets fed to markPending; builders invoked via CommandRegistry |
| `ExtrusionScreen.kt:174-184` | `PrinterCommands.clampFlowPct` | markPending call sites | WIRED | Clamped targets; Flow/PA/smooth/fan dispatched |
| `FineTuneShared.kt:46-48` | `PrinterCommands.clamp*` | `clampVelocityLimitTarget()` | WIRED | Velocity/Accel/SCV clamp authority |
| `FwRetractionScreen.kt:90` | `PrinterCommands.*_MIN/*_MAX` | `clampRetractionTarget()` | WIRED | Build-blind but clamp-symmetric with other tuners |
| `ShellNavState.kt:95-96` | `applyEntryReset()` | same-dest re-selection path | WIRED | Per-dest entry resets fire on both dest-change AND same-dest re-select |
| `FineTuneHolder.kt:189-195` | `PendingStateFlip.seq` | `++flipSeq` + seq-guarded timeout | WIRED | Monotonic seq prevents stale-timer cross-clear |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `MotionScreen` | `vm.speedPct`, `vm.maxAccel`, etc. | `FineTuneHolder.vm` (StateFlow) combining `store.printerState` + caps + baselines | Yes — `printerState` is the Moonraker-reduced state from the live WebSocket subscription; speed, velocity fields populated from `gcode_move`/`toolhead` reducer | FLOWING |
| `ExtrusionScreen` | `vm.flowPct`, `vm.pressureAdvance`, `vm.partFanPct` | Same `FineTuneHolder.vm` combine | Yes — `extrudeFactor`, `pressureAdvance`, `smoothTime`, `partFanSpeed` all reduced from live Moonraker object deltas | FLOWING |
| `FineTuneHubScreen` | `onNavigate` callbacks | `AppShell` sub-nav state (`fineTuneGroup`) | Yes — navigation state, no data rendering | FLOWING |

### Behavioral Spot-Checks

Step 7b — SKIPPED for the command-layer/pure-Kotlin parts (no server to call); the on-device UAT gate (8/8 on flox) constitutes the authoritative behavioral verification.

### Probe Execution

Step 7c — No probes declared in any PLAN file. No `scripts/*/tests/probe-*.sh` exist. SKIPPED.

### Requirements Coverage

Per the phase instructions: TUNE-* are D-tokens carried in plan frontmatter, not REQUIREMENTS.md-tracked IDs. Verified by behavior instead.

| Token | Description | Status | Evidence |
|-------|-------------|--------|---------|
| TUNE-01 | Tune button entry + Fine-Tune Hub nav + reset-to-Hub | SATISFIED | `PrintStatusScreen.kt:283`, `ShellNavState.applyEntryReset`, 17-08 commit `2bebae0`, UAT 1+8 PASS |
| TUNE-02 | Motion scrubbers (speed M220, max-vel/accel/SCV SET_VELOCITY_LIMIT) dispatch + state-flip | SATISFIED | `MotionScreen`, `PrinterCommands.speedFactor/setVelocityLimit`, UAT Check 2 PASS |
| TUNE-03 | Extrusion scrubbers (flow M221, PA, smooth, FW-retraction) dispatch + state-flip | SATISFIED | `ExtrusionScreen`, `PrinterCommands.flowFactor/setPressureAdvance/setRetraction`, UAT Check 3 PASS |
| TUNE-04 | Capability gating (absent objects hidden, not disabled) | SATISFIED | All tiles gated on `vm.has*` flags, derived from `Capabilities.hasObject()`; UAT Check 3 (FW-retraction absent = correct) |
| TUNE-05 | Per-control reset (speed/flow→100%, limits+PA→config baseline, fan no reset) | SATISFIED | Nullable baseline pattern in all screens; explicit `onReset = null` for fan; UAT Check 4 PASS |
| TUNE-06 | Whole-group state-flip busy lock (not bare ack) | SATISFIED | `FineTuneHolder.reached()` with strict-`<` epsilon; `groupBusy = inFlight.isNotEmpty() \|\| pending != null`; UAT Check 5 PASS |
| TUNE-07 | Vector drawable glyphs for all Fine-Tune controls (tintable, runtime) | SATISFIED | `DinghyIcons.*` icon tokens used throughout (Speed, MaxVelocity, MaxAccel, MinCruise, SquareCornerVelocity, OutputCircle, PressureAdvance, SmoothTime, FanMode); 17-04 plan executed |
| SC-1 (ROADMAP) | Live-adjust Motion + Extrusion scrubbers work with state-flip confirmation | SATISFIED | On-device UAT Checks 2+3 PASS on live E5 print |
| SC-2 (ROADMAP) | Capability-gated; absent tunables hidden | SATISFIED | UAT Check 3 confirms FW-retraction absent on E5/E3 |
| SC-3 (ROADMAP) | Safe/bounded, always-available, per-control reset | SATISFIED | Clamp authority in `PrinterCommands`; always-available (object-gated, not print-gated) |
| SC-4 (ROADMAP) | Proven on a real in-progress print | SATISFIED | UAT Checks 2+3+5 run against a live E5 print; physical speed change observed |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `FineTuneShared.kt:9` | `/** Unreported value placeholder (D-20: "—", never a fabricated 0). */` | Contains "placeholder" in a KDoc | INFO | Not a stub — it is a documented design decision constant (`DASH = "—"`); the comment describes why the constant exists. Not a code placeholder or debt marker. |

No `TODO`, `FIXME`, `XXX`, or `TBD` markers found in any Phase-17-modified source file. No stub patterns (`return null`, empty lambdas, `fail()` bodies) in production code. The one "placeholder" string is a KDoc comment on a named constant explaining the display convention.

### Known Limitation (Documented, Owner-Accepted — Not a Gap)

**FW-Retraction build-blind:** Neither dev printer (E5/E3) exposes `[firmware_retraction]`, so `FwRetractionScreen` was never exercised on hardware. Covered by: (a) the 17-03 synthetic-fixture reducer test for `FirmwareRetractionObject`, (b) the 17-05 holder gate (`hasFwRetraction` capability check), (c) the compile-checked `onFwRetraction` callback wire in `AppShell.kt:665`, and (d) the 17-07 clamp authority (`clampRetractionTarget` in `FwRetractionScreen.kt`). To be validated opportunistically on a printer that exposes the object.

**FineTuneNavTest swipe-gesture harness defect:** Both instrumented methods in `FineTuneNavTest` fail on flox — not at the assertion under test, but at the shared `openFineTuneViaDrawer()` setup: `performTouchInput { swipeUp() }` spreads the drag across ~12 events, so no single per-event `dragAmount` clears `AppShell`'s `SWIPE_UP_THRESHOLD_PX = 80f`. This is a pre-existing test infrastructure defect (the test compiled but was never device-run before 17-08), not a product regression. The product behavior (Fine-Tune re-entry lands on Hub) was manually verified on flox (owner-approved). Test hardening is deferred to a dedicated pass.

### Gaps Summary

No gaps. All 8 observable truths are VERIFIED with direct codebase evidence. Both UAT failures (Check 6 busy-lock wedge, Check 8 same-dest re-entry) were diagnosed, fixed by plans 17-07 and 17-08, and on-device re-verified by the owner on flox (2026-06-07). Code review warnings WR-01..WR-04 were all resolved and committed (commits `32ea19c`, `e7bd9f7`, `7b23fb7`, `825963a`). No debt markers in modified source files.

---

_Verified: 2026-06-08_
_Verifier: Claude (gsd-verifier)_
