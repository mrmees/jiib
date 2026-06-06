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
> gating, command builders, preheat selection); the two binding gates (SC-5 babystep round-trip,
> SC-3 perf no-regression) are on-device manual.

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
- **Before `/gsd-verify-work`:** Full suite green **AND** live babystep UAT on flox + Ender 5 Plus (SC-5) **AND** Adreno-320 perf eyeball/gfxinfo on the release build (SC-3).
- **Max feedback latency:** ~60s for the per-task quick run.

---

## Per-Task Verification Map

| Behavior | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|----------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| `classifyPrintStatus` maps all 6 raw states → 4 modes; Standby-stays-Standby with stale filename; klippy shutdown ≠ Terminal | 0/1 | classifier core (refines SHELL-*) | — | N/A (pure derivation, Moonraker-only) | unit (pure) | `--tests '...PrintStatusModeTest'` | ❌ W0 | ⬜ pending |
| Babystep gating: null `currentLayer` hides; layer ≤ threshold shows; > threshold hides (no time fallback) | 0/1 | babystep (SC-5) | — | N/A | unit (pure) | `--tests '...PrintStatusModeTest'` | ❌ W0 | ⬜ pending |
| `SET_GCODE_OFFSET Z_ADJUST=±step MOVE=1` builder: sign (Compress=−/Expand=+), step ∈ fixed set {.02/.05/.10/.15/.20}, clamp/validate | 1 | babystep (SC-5) | T-16 V5 | Only fixed identifier + validated-against-set numeric — never free-text concat | unit (pure) | `--tests '...PrinterCommandsTest'` | ✅ extend | ⬜ pending |
| `SDCARD_RESET_FILE` (Terminal Dismiss) builder | 1 | terminal D-05 | T-16 V5 | Fixed gcode, no interpolation | unit (pure) | `--tests '...PrinterCommandsTest'` | ❌ W0 | ⬜ pending |
| Spool-aware Preheat selection: direct-apply when spool temps present, per-temp independent guard, fall to PresetSelector when absent/both-null | 1/2 | D-01 | — | N/A | unit (pure) | `--tests '...PreheatTest'` | ❌ W0 | ⬜ pending |
| `gcode_move.homing_origin[2]` parsed into PrinterState (one reducer line) → applied-offset readout | 1 | babystep (SC-5) | — | N/A | unit (pure) | `--tests '...PrinterStateReducerTest'` | ✅ extend | ⬜ pending |
| Babystep round-trip on a live first layer: nudge → `homing_origin[2]` flip confirms sign/direction; session-only | — | SC-5 | T-16 (physical motion) | `MOVE=1` small Z jog gated to early-layer window, session-only, no SAVE_CONFIG | **manual on-device** | flox + E5 — NOT automatable | manual gate | ⬜ pending |
| Adreno-320 perf: no regression vs current home (Views GraphView/status untouched) | — | SC-3 | — | N/A | on-device gfxinfo / eyeball | flox release build | manual gate | ⬜ pending |
| Core monitor loop behavior-preserving | — | SC-4 | — | N/A | on-device | flox + live printer | manual gate | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `PrintStatusModeTest.kt` — classifier mapping (all 6 raw states + Standby/klippy edges) + babystep gating + step-cycle
- [ ] `PreheatTest.kt` — spool-aware Preheat selection (direct / per-temp guard / selector fallback)
- [ ] Extend existing `PrinterCommandsTest` for `SET_GCODE_OFFSET` + new `SDCARD_RESET_FILE`
- [ ] Extend `PrinterStateReducerTest` for `gcode_move.homing_origin[2]` parse
- [ ] **Wave-0 RED scaffolds MUST compile day-one** — `fail()` bodies, no refs to unbuilt symbols; Gradle compiles the whole test sourceset before `--tests` filtering, so a bad scaffold bricks every per-wave run ([[dinghy-wave0-red-scaffold-compile]])

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
- [ ] Wave 0 covers all MISSING references (classifier, preheat, command builders, reducer extend)
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s for the quick run
- [ ] `nyquist_compliant: true` set in frontmatter (after planner wires per-task automated verify)

**Approval:** pending
