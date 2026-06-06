---
phase: 16
slug: home-print-status-redesign
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-06
---

# Phase 16 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `16-RESEARCH.md` §Validation Architecture. This is a UI/UX rework phase against a
> mature codebase — the bulk of new logic is host-testable pure functions (classifier, babystep
> gating, command builders, preheat selection, glance-sensor selection, the print-status UI model);
> the two binding gates (SC-5 babystep round-trip, SC-3 perf no-regression) are on-device manual.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit (host unit) + AndroidX instrumented (`connectedAndroidTest`). Existing host-test pattern: `PrintStatusControlModel`, `PrintStatusHolder` already host-tested. |
| **Config file** | Gradle (`app/build.gradle.kts`); run via `E:\Android\gw.bat` (./gradlew won't run from WSL — see CLAUDE.md build env). |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.printstatus.PrintStatusModeTest' --tests 'works.mees.dinghy.command.PrinterCommandsTest'"` (list classes explicitly — glob `*` false-fails on this AGP per memory) |
| **Full suite command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest"` |
| **Estimated runtime** | ~quick ≤60s · full suite ~minutes (guard with `timeout` + taskkill /F /T — Windows java.exe children outlive WSL bash, see [[dinghy-display-gradle-hang-interop]]) |

---

## Sampling Rate

- **After every task commit:** Run the quick `printstatus` + `command` unit run.
- **After every plan wave:** Run the full `:app:testDebugUnitTest` suite.
- **Before `/gsd-verify-work`:** Full suite green **AND** the pre-UAT cleanup gate clean (no leftover
  `fail("not yet implemented`, no `rememberCoroutineScope` in babystep/prefs, no `proc_stat` in state)
  **AND** live babystep UAT on flox + Ender 5 Plus (SC-5) **AND** Adreno-320 perf eyeball/gfxinfo on the
  release build (SC-3).
- **Max feedback latency:** ~60s for the per-task quick run.

---

## Per-Task Verification Map

| Behavior | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|----------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| `classifyPrintStatus` maps all 6 raw states → 4 modes; Standby-stays-Standby with stale filename; klippy shutdown ≠ Terminal | 0/2 | classifier core (refines SHELL-*) | — | N/A (pure derivation, Moonraker-only) | unit (pure) | `--tests '...PrintStatusModeTest'` | ❌ W0 | ⬜ pending |
| Babystep gating: null `currentLayer` hides; layer ≤ threshold shows; > threshold hides (no time fallback) | 0/2 | babystep (SC-5) | — | N/A | unit (pure) | `--tests '...PrintStatusModeTest'` | ❌ W0 | ⬜ pending |
| `SET_GCODE_OFFSET Z_ADJUST=±step MOVE=1` builder: sign (Compress=−/Expand=+), step ∈ fixed set {.02/.05/.10/.15/.20}, canonicalize-against-set + Locale.US, invalid-input snaps to nearest | 1 | babystep (SC-5) | T-16 V5 | Only fixed identifier + canonicalized-against-set numeric — never free-text concat, never off-grid | unit (pure) | `--tests '...PrinterCommandsTest'` | ✅ extend | ⬜ pending |
| `SDCARD_RESET_FILE` (Terminal Dismiss) builder | 1 | terminal D-05 | T-16 V5 | Fixed gcode, no interpolation | unit (pure) | `--tests '...PrinterCommandsTest'` | ❌ W0 | ⬜ pending |
| Catalog/matrix sidecars at `docs/commands/catalog.json` + `docs/commands/printer-matrix.json` carry the two new rows (drift-free) | 1 | babystep/terminal | T-16 V5 | Registry ↔ docs sidecar parity | unit | `--tests '...CommandCatalogDriftTest'` | ✅ extend | ⬜ pending |
| Spool-aware Preheat selection (pure `selectPreheatPath`): `DirectTemps` when spool temps present (per-temp independent guard, never 0), `OpenSelector` when absent/both-null | 0/2 | D-01 | — | N/A | unit (pure) | `--tests '...PreheatTest'` | ❌ W0 | ⬜ pending |
| Glance-sensor selection (pure `selectGlanceSensor`): MCU > host > first by stable order, empty → null, choice stable across calls (partial-diff-flip fix) | 0/1 | standby glance temp (Claude discretion → temperature_sensor) | T-16-04-D | N/A (pure selection over a RETAINED map, never the raw diff) | unit (pure) | `--tests '...GlanceSensorTest'` | ❌ W0 | ⬜ pending |
| Retained `temperatureSensors` map: update-on-present, retain-on-absent (a partial diff omitting the sensor object keeps its prior value) | 1 | standby glance temp | T-16-04-D | N/A | unit (pure) | `--tests '...PrinterStateReducerTest'` | ✅ extend | ⬜ pending |
| `temperature_sensor` dynamic subscribe rule (mirrors heater_generic); no `proc_stats` host-load | 1 | standby glance temp | — | N/A | unit (pure) | `--tests '...DeriveCapabilitiesTest'` | ✅ extend | ⬜ pending |
| `gcode_move.homing_origin[2]` parsed into PrinterState (one reducer line) → applied-offset readout | 1 | babystep (SC-5) | — | N/A | unit (pure) | `--tests '...PrinterStateReducerTest'` | ✅ extend | ⬜ pending |
| `BabystepPrefs` persistence: default enabled=true / layers=5; IOException-fail-safe read → defaults; `setEnabled`/`setLayerCount` write-through; layers coerced ≥1 (DataStore created in DinghyApp, threaded to AppContainer) | 0/1 | babystep app setting (D-06) | — | N/A (DataStore via writeScope intent helper, never composition scope) | unit (pure) | `--tests '...BabystepPrefsTest'` | ❌ W0 | ⬜ pending |
| `PrintStatusUiModel`: mode → per-mode gutter set + launcher order + babystep-row + terminal-error-area flags (the seam the 942-line screen renders from) | 3 | four-state home (refines SHELL-*/JOB-*) | — | N/A (pure, toolkit-agnostic per ADR-0001) | unit (pure) | `--tests '...PrintStatusUiModelTest'` | ❌ new (16-06 T1) | ⬜ pending |
| Babystep round-trip on a live first layer: nudge → `homing_origin[2]` flip confirms sign/direction; session-only | — | SC-5 | T-16 (physical motion) | `MOVE=1` small Z jog gated to early-layer window, session-only, no SAVE_CONFIG | **manual on-device** | flox + E5 — NOT automatable | manual gate | ⬜ pending |
| Adreno-320 perf: no regression vs current home (Views GraphView/status untouched) | — | SC-3 | — | N/A | on-device gfxinfo / eyeball | flox release build | manual gate | ⬜ pending |
| Core monitor loop behavior-preserving | — | SC-4 | — | N/A | on-device | flox + live printer | manual gate | ⬜ pending |
| Pre-UAT cleanup gate: no leftover `fail("not yet implemented`, no `rememberCoroutineScope` in babystep/prefs, no `proc_stat` in state | 5 | phase-completion hygiene | — | N/A | grep gate | (greps in 16-08 Task 1 `<verify>`) | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `PrintStatusModeTest.kt` — classifier mapping (all 6 raw states + Standby/klippy edges) + babystep gating + step-cycle
- [ ] `PreheatTest.kt` — spool-aware Preheat selection (direct / per-temp guard / selector fallback)
- [ ] `GlanceSensorTest.kt` — glance-sensor selection (MCU>host>first, stable order, empty→null) — gates the pure `selectGlanceSensor` in 16-04 (partial-diff-flip fix)
- [ ] `BabystepPrefsTest.kt` — babystep persistence (defaults enabled/5, IOException-fail-safe read, write-through, layers ≥1) — mirrors the `MacroPrefs` test analog; unit-gates the write-scope trap
- [ ] Extend existing `PrinterCommandsTest` for `SET_GCODE_OFFSET` (incl. off-grid canonicalization) + new `SDCARD_RESET_FILE`
- [ ] Extend `PrinterStateReducerTest` for `gcode_move.homing_origin[2]` parse + retained `temperatureSensors` retain-on-absent
- [ ] **Wave-0 RED scaffolds MUST compile day-one** — `fail()` bodies, no refs to unbuilt symbols; Gradle compiles the whole test sourceset before `--tests` filtering, so a bad scaffold bricks every per-wave run ([[dinghy-wave0-red-scaffold-compile]])

> Note: `PrintStatusUiModelTest` (16-06 Task 1) is created+turned-green IN the same wave-3 plan as a
> dedicated pre-Compose unit gate, NOT a Wave-0 RED scaffold — the UI model is built and tested before
> the big screen edit within 16-06.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Babystep live round-trip | SC-5 | Requires a real first layer on real hardware; sign/direction (`homing_origin[2]` flip) cannot be trusted from a mock — the project's #1 recurring failure class ([[dinghy-display-mock-vs-reality]]) | Start a print on flox + Ender 5 Plus (192.168.1.120:7125); during first ~N layers, nudge Z closer/away; confirm `gcode_move.homing_origin[2]` moves the right direction and the live readout matches; confirm control hides after the layer window; confirm session-only (no config write) |
| Adreno-320 perf no-regression | SC-3 | Emulators lie about old-GPU fill rate; must measure on genuine Adreno 320 | Release build on flox; eyeball + `gfxinfo framestats` on Print-Status (Standby + Printing); confirm no frozen frames / jank vs current home |
| Core monitor loop preserved | SC-4 | End-to-end Moonraker behavior on live printer | flox + live printer: connect → monitor → drive a print; confirm no regression in the connect/monitor/control loop |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (classifier, preheat, glance-sensor, command builders, reducer extend)
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s for the quick run
- [ ] `nyquist_compliant: true` set in frontmatter (after planner wires per-task automated verify)

**Approval:** pending
