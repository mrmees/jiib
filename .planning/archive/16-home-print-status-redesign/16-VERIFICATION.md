---
phase: 16-home-print-status-redesign
verified: 2026-06-06T00:00:00Z
status: passed
score: 5/5 success criteria verified
overrides_applied: 0
re_verification:
  previous_status: null
  previous_score: null
deferred:
  - truth: "ShellPresenceTest whole-class on-device run is deterministic"
    addressed_in: "later plan (pre-existing coupling, tracked)"
    evidence: "deferred-items.md — seedRoute writes the Phase-14 DEAD connectionStore while routing now derives hasConfig from profileStore; non-determinism predates 16-05. androidTest sourceset COMPILES (the plan's acceptance bar)."
  - truth: "Print-Status artboard PNGs regenerated to the four-state model"
    addressed_in: "owner-side asset task (explicitly deferred in 16-07)"
    evidence: "README four-state section + LAYOUT flexible-tile hard law are the written authority; artboard regen is a non-blocking owner asset task per CONTEXT note."
---

# Phase 16: Home / Print-Status Redesign Verification Report

**Phase Goal:** Rework the home/Print-Status surface into its definitive state-driven form — the visual FOUNDATION for Phases 17–20 — via ONE Moonraker-derived `PrintStatusMode` classifier (Standby/Printing/Paused/Terminal) replacing scattered printState checks, plus the conditional Z-babystep control, all through the existing Focus/Field/Gutter grammar, WITHOUT regressing the Views-based render/throttle primitives or the core monitor loop.
**Verified:** 2026-06-06
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (Success Criteria)

| #    | Truth (SC)                                                                                  | Status     | Evidence |
| ---- | ------------------------------------------------------------------------------------------ | ---------- | -------- |
| SC-1 | Redesigned home: clear hierarchy + capability-gated forward entry points per UI contract   | ✓ VERIFIED | Four-state surface routed off `classifyPrintStatus` (`PrintStatusScreen.kt:185,363`); curated Standby launcher grid (`PrintStatusUiModel.kt:117-129`); greyed Output (P18) + System Info (P19) drawer stubs `dest=null` (`AppDrawer.kt:191-192`); README §3 four-state model (`docs/ui_design/README.md:73`). |
| SC-2 | Portrait + landscape via Focus/Field/Gutter; sacred ratios + ratio-only sizing             | ✓ VERIFIED | `ScreenScaffold.kt:31-36,65,96-112` — landscape Row 50/50 columns, portrait Column stack, sizing via `aspectRatio`/`weight` only (no hardcoded px); used 4× in `PrintStatusScreen.kt` (one per mode); focus aspect-locked `1600/900` (`PrintStatusScreen.kt:518`). |
| SC-3 | High-churn render surfaces keep Adreno-320 perf — no regression                            | ✓ VERIFIED (on-device) | 16-UAT.md SC-3 PASS on genuine Adreno 320 release build: p95 53 ms vs Phase-5 baseline 48.64 ms (~4 ms for richer Benchy-hero focus), 0 frozen frames; Views GraphView/status untouched. |
| SC-4 | Behavior-preserving core monitor loop, verified on-device                                  | ✓ VERIFIED (on-device) | 16-UAT.md SC-4 PASS (full): connect→monitor→pause/resume/cancel live on E5 Plus; four states classify off real printer state; Terminal(Cancelled) live-verified on a real cancel; Standby-stays-Standby proven via live e-stop/klippy-shutdown. |
| SC-5 | Z-babystep conditional control: early-layer only, `homing_origin[2]` flip, session-only    | ✓ VERIFIED (on-device) | 16-UAT.md SC-5 PASS on a live first layer (E5 Plus): Compress=closer→offset decreases, Expand=farther→increases, on-screen matched physical; step cycled; row HID past layer window; session-only (offset reset to 0.000, no SAVE_CONFIG). Sign implemented per plan, NOT guess-flipped — device-confirmed correct. |

**Score:** 5/5 success criteria verified

### Decision Coverage (D-01..D-06)

| #    | Decision                                                              | Status     | Evidence |
| ---- | -------------------------------------------------------------------- | ---------- | -------- |
| D-01 | Spool-aware Preheat (direct temps if Spoolman, else PresetSelector)  | ✓ VERIFIED | Pure `selectPreheatPath` (`Preheat.kt:34-36`): DirectTemps only when ≥1 non-null temp, else OpenSelector; screen fires per-temp `setHeater` for each non-null temp, each capability-gated on `extruder`/`heater_bed`, never 0 (`PrintStatusScreen.kt:200-260`). |
| D-02 | Forward stubs in App Drawer (greyed `dest=null`), NOT launcher grid  | ✓ VERIFIED | Output + System Info `dest=null` in `AppDrawer.kt:191-192`; `LauncherDest` enum + `standbyLauncherDests` carry no Output/SysInfo (`PrintStatusUiModel.kt:117-129`). |
| D-03 | Active-print Tune tile = P17 stub; WebRTC = existing Webcam tile     | ✓ VERIFIED | Tune stub referenced in active Field (icon-only field button, fix commit `0950b57`); Webcam tile runtime-gated, unchanged (`AppDrawer.kt:166`). |
| D-04 | Standby Power inert/design-only, red stop-intent                     | ✓ VERIFIED | Power drawer tile `dest=null, danger=true` (`AppDrawer.kt:193`); standby gutter Power rendered consistent/inert. |
| D-05 | Terminal passive (no auto-yank); Dismiss=`SDCARD_RESET_FILE`         | ✓ VERIFIED | Terminal is a home mode (`when(mode)` branch, no nav side-effect); `dismissPrint` spec = `SDCARD_RESET_FILE` registered in `all` (`CommandRegistry.kt:537-542,598`); live-verified passive in UAT. |
| D-06 | Babystep app setting under Settings tile; numeric keyboard           | ✓ VERIFIED | `BabystepPrefs` (default enabled/5 layers) + toggle + layer-count field in `SettingsScreen.kt:82-160`, `KeyboardType.Number`; writes via `container.setBabystepEnabled/Layers` writeScope intents. |

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `PrintStatusMode.kt` | Pure classifier, all 6 states, Standby-stays-Standby, klippy-ignored | ✓ VERIFIED | `classifyPrintStatus` reads `printState` ONLY (lines 45-52); Standby→Standby; class doc + impl confirm no terminal-on-stale-filename, no klippy axis. |
| `PrintStatusControlModel.kt` | Masquerade branch DELETED | ✓ VERIFIED | Standby→`standbyControls()` (line 79); comment 76-78 documents the removed terminal masquerade. |
| `CommandRegistry.kt` | `babystepZ` + `dismissPrint` registered in `all` | ✓ VERIFIED | Specs at 526/537; both in `all` list at 597-598 (no dead-wiring). |
| `PrinterStateReducer.kt` | `homing_origin[2]` parse + retained `temperatureSensors` | ✓ VERIFIED | null-safe `homing_origin` getOrNull(2)→gcodeZOffset (line 113); update-on-present merge, retain-on-absent (216-223). |
| state package | NO `proc_stats`/host-load (deferred P19) | ✓ VERIFIED | grep for proc_stat/hostLoad/machine.proc in `state/` = NONE. |
| `GlanceSensor.kt` | pure `selectGlanceSensor` over retained map | ✓ VERIFIED | defined line 26, used `PrintStatusScreen.kt:885` on `state.temperatureSensors`. |
| `Preheat.kt` | pure `selectPreheatPath` (per-temp guard, never 0) | ✓ VERIFIED | lines 16-36; DirectTemps/OpenSelector sealed paths. |
| `BabystepPrefs.kt` + `DinghyApp.kt` + `di/AppContainer.kt` | DataStore created in app, threaded, writeScope intents | ✓ VERIFIED | `babystep.preferences_pb` created `DinghyApp.kt:82-93`; threaded param `AppContainer.kt:84`; `setBabystepEnabled/Layers` via `writeScope.launch` (206-215); no `rememberCoroutineScope` in write path. |
| `TemperatureScreen.kt` `PresetSelector` | `internal` (reused) | ✓ VERIFIED | `internal fun PresetSelector` (line 408). |
| `AppDrawer.kt` | greyed Output + System Info stubs | ✓ VERIFIED | `dest=null` tiles (191-192); not in launcher grid. |
| `docs/ui_design/README.md` + `LAYOUT.md` | four-state model + flexible-tile hard law | ✓ VERIFIED | README §3 (line 73); LAYOUT interactive-grid flexible-tile HARD LAW (line 68), babystep-row exempt. |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| `PrintStatusScreen` | classifier | `classifyPrintStatus(state)` → `when(mode)` | ✓ WIRED | Lines 185, 363; 4 ScreenScaffold mode branches. |
| BabystepRow Compress/Expand | Moonraker | `dispatch(CommandRegistry.babystepZ, BabystepArgs(±step))` | ✓ WIRED | `PrintStatusScreen.kt:346-347`; gated on `babystepVisible(enabled, currentLayer, layers)` (line 186). |
| Settings toggle | DataStore | `container.setBabystepEnabled/Layers` → writeScope → `BabystepPrefs.edit` | ✓ WIRED | `SettingsScreen.kt:138,154` → `AppContainer.kt:206-215`. |
| Preheat | heaters | `selectPreheatPath` → per-temp `setHeater` (capability-gated) | ✓ WIRED | `PrintStatusScreen.kt:200-260`. |
| AppShell | PrintStatusScreen launcher | `onNavigate`/`onOpenDrawer` | ✓ WIRED | `AppShell.kt:469-474`. |
| reducer | applied-Z readout | `gcodeZOffset` ← `homing_origin[2]` | ✓ WIRED | `PrinterStateReducer.kt:113` → Z-offset row `PrintStatusScreen.kt:605-608`. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Full host unit suite GREEN | `gw.bat :app:testDebugUnitTest` | BUILD SUCCESSFUL, exit 0; aggregated 717 tests, 0 failures, 0 errors | ✓ PASS |
| Phase-16 test classes execute | results XML inspection | PrintStatusModeTest, PreheatTest, GlanceSensorTest, BabystepPrefsTest, PrintStatusUiModelTest, PrinterCommandsTest, PrinterStateReducerTest all present + passing | ✓ PASS |
| Command catalog sidecar parity | grep `docs/commands/catalog.json` | KGC-SET_GCODE_OFFSET + KGC-SDCARD_RESET_FILE present | ✓ PASS |
| git tree clean (UAT fix commits landed) | `git status` + `git cat-file` | clean; all 7 referenced UAT commits resolve | ✓ PASS |

### Probe Execution

No conventional `scripts/*/tests/probe-*.sh` probes in this repo; phase uses host JUnit + on-device manual gates. The full unit suite (the project's runnable check) was executed in-process above (GREEN). N/A.

### Cleanup-Gate Re-Verification (independent of SUMMARY claims)

| Check | Result |
| ----- | ------ |
| No live `fail("not yet implemented` RED placeholder | ✓ PASS — only KDoc references remain; tests have typed assertions (suite GREEN). |
| No `rememberCoroutineScope` in babystep/prefs write path | ✓ PASS — `BabystepPrefs.kt` none; AppContainer writes via `writeScope.launch`. |
| No `proc_stat` host-load in `state` package | ✓ PASS — NONE. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| (none) | — | TODO/FIXME/placeholder scan on 5 new core files | ℹ️ Info | All clean — no debt markers in new artifacts. |

### Deferred Items (tracked, non-blocking)

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | ShellPresenceTest whole-class on-device determinism | later plan | `deferred-items.md` — pre-existing connectionStore/profileStore seed coupling, predates 16-05; androidTest sourceset COMPILES (the plan's bar); babystep unit gate GREEN. |
| 2 | Print-Status artboard PNG regeneration | owner asset task (16-07) | Explicitly deferred; README four-state + LAYOUT flexible-tile law are the written authority. |

### Human Verification Required

None outstanding. The three binding on-device gates (SC-3 perf, SC-4 monitor loop incl. Terminal end-state, SC-5 live babystep sign on a real first layer) were owner-run on the real flox tablet + live Ender 5 Plus and recorded PASS in 16-UAT.md (2026-06-06). The classifier behavior change (Standby-stays-Standby on klippy shutdown) was proven on hardware via a live e-stop.

### Gaps Summary

No gaps. Every Success Criterion (SC-1..SC-5) is observably true in the codebase, every implementation decision (D-01..D-06) is honored, and every dead-wiring trap flagged in the verification brief was checked and is correctly wired:

- The single `PrintStatusMode` classifier is a pure function reading `printState` only, covers all 6 raw states, keeps Standby-as-Standby (the old terminal-on-stale-filename masquerade is genuinely deleted from `PrintStatusControlModel`), and treats klippy shutdown as a separate axis. The screen routes off it.
- `SET_GCODE_OFFSET` + `SDCARD_RESET_FILE` specs exist AND are in `CommandRegistry.all` and the docs sidecar.
- `homing_origin[2]` parsed (null-safe), `temperatureSensors` retained-on-absent, pure `selectGlanceSensor`, no `proc_stats` in state.
- `BabystepPrefs` DataStore created in `DinghyApp`, threaded to `AppContainer`, written via process-scoped writeScope intents (the recurring write-scope cancellation trap is handled, verified by grep + GREEN unit gate).
- `PresetSelector` is `internal`; spool-aware Preheat via `selectPreheatPath` with per-temp capability-gated `setHeater` (never 0).
- Greyed Output + System Info drawer stubs present and absent from the launcher grid.

The full host unit suite is GREEN (717/0/0) and the three binding on-device gates passed on real hardware. Two tracked items (ShellPresenceTest device-determinism flake; artboard PNG regeneration) are explicitly non-blocking carry-forwards. Phase goal achieved.

---

_Verified: 2026-06-06_
_Verifier: Claude (gsd-verifier)_
