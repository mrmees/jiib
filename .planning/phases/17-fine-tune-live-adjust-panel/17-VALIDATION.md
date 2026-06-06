---
phase: 17
slug: fine-tune-live-adjust-panel
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-06
---

# Phase 17 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `17-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit (host) + AndroidX instrumented (on-device) — existing dinghy setup |
| **Config file** | Gradle (`app/build.gradle`); host tests in `app/src/test`, instrumented in `app/src/androidTest` |
| **Quick run command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.command.PrinterCommandsTest' --no-daemon"` |
| **Full suite command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Estimated runtime** | ~60–120 seconds (host suite) |

> ⚠ List test classes explicitly — `--tests 'works.mees.dinghy.*'` glob false-fails ("No tests found") on this AGP (MEMORY lesson).

---

## Sampling Rate

- **After every task commit:** Run the relevant `--tests` class (`PrinterCommandsTest` / `CommandRegistryTest` / `PrinterStateReducerTest` / `FineTuneHolderTest`).
- **After every plan wave:** Run the full host suite (`:app:testDebugUnitTest`).
- **Before `/gsd-verify-work`:** Full host suite green + on-device UAT of Motion + Extrusion state-flip on flox.
- **Max feedback latency:** ~120 seconds (host suite).

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | — | 0 | TUNE-02/03 | — / — | N/A | unit | `... --tests 'works.mees.dinghy.command.PrinterCommandsTest'` | ❌ W0 (extend) | ⬜ pending |
| TBD | — | 0 | TUNE-02/03 | — / — | N/A | unit | `... --tests 'works.mees.dinghy.command.CommandRegistryGcodeTest'` | ❌ W0 (extend) | ⬜ pending |
| TBD | — | 0 | TUNE-02/03/05 | — / — | N/A | unit | `... --tests 'works.mees.dinghy.state.PrinterStateReducerTest'` | ❌ W0 (extend) | ⬜ pending |
| TBD | — | 0 | TUNE-04/05/06 | — / — | N/A | unit | `... --tests 'works.mees.dinghy.ui.finetune.FineTuneHolderTest'` | ❌ W0 (new) | ⬜ pending |
| TBD | — | 0 | TUNE-01 | — / — | N/A | instrumented | `... :app:connectedAndroidTest` (Tune → FineTune nav) | ❌ W0 (new) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
*Task IDs filled by the planner; this map is the validation skeleton the plans must satisfy.*

---

## Wave 0 Requirements

- [ ] Extend `PrinterCommandsTest` — new builders (M220/M221/M106, SET_VELOCITY_LIMIT, SET_PRESSURE_ADVANCE, SET_RETRACTION) + scaling assertions (1.05 ratio→`S105`; 60%→`M106 S153`)
- [ ] Extend `CommandRegistryGcodeTest` — new `gcode()` specs present in `.all` + correct capability availability
- [ ] Extend `PrinterStateReducerTest` — new field walks (toolhead limits, `extruder.pressure_advance`/`smooth_time`, `fan.speed`, `firmware_retraction.*`) + synthetic `firmware_retraction` fixture + diff-merge retention
- [ ] New `FineTuneHolderTest` — vm scaling, capability gates, config-baseline reset derivation, whole-group busy-lock derivation
- [ ] New instrumented nav test — `PrintStatusControlAction.Tune` → `Dest.FineTune` route

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| State-flip confirm on live print (speed/flow + a motion-limit + pressure advance) | TUNE-07 (live) | Requires a real in-progress print on E5; verifies the printer-object state actually flips, not a bare ack | Start a print on flox+E5; nudge speed%, flow%, max-accel, pressure-advance; observe each tile's reported value flip to the commanded value and observe physical effect |
| Part-cooling fan nudge state-flip | TUNE-03 | Requires live `fan` object | On flox+E5, nudge part-fan ±; observe `fan.speed` readout flip |
| Adreno-320 perf check on Fine-Tune screens | TUNE (perf floor) | gfxinfo on real hardware; emulators lie | gfxinfo framestats on flox; judge on no-frozen-frames + responsiveness (ADR-0001 Add.2) — static value-tile screens, LOW perf risk |
| Firmware-retraction mini-screen | TUNE-04 | **Neither dev printer exposes `[firmware_retraction]`** → build-blind, fixture-tested only | Cannot live-UAT; verify via host fixture (gate ON when synthetic object present), like `QUAD_GANTRY_LEVEL` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
