---
phase: 19
slug: output-controls-fans-lights-generic-pins
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-07
---

# Phase 19 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `19-RESEARCH.md` § Validation Architecture (live-verified against E5P + E3P).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 host unit tests (`app/src/test`) + Compose/Robolectric where used. Builds Windows-side via `E:\Android\gw.bat` (`./gradlew` does NOT run from WSL). |
| **Config file** | none new — existing Gradle test sourceset |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.outputs.* --no-daemon"` |
| **Full suite command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Estimated runtime** | ~quick: seconds · full: minutes |

---

## Sampling Rate

- **After every task commit:** Run quick: `outputs.*` + `PrinterCommandsOutputsTest`
- **After every plan wave:** Run full suite `:app:testDebugUnitTest`
- **Before `/gsd-verify-work`:** Full suite green + `verify_ligatures.py` exit 0 + owner on-device UAT
- **Max feedback latency:** quick run (seconds)

---

## Per-Task Verification Map

> Skeleton — the planner finalizes per-task IDs/commands. Maps the 4 ROADMAP Success Criteria + D-09 to tests.

| SC / Decision | Behavior | Test Type | Automated Command | File Exists | Status |
|---------------|----------|-----------|-------------------|-------------|--------|
| SC-1 | `parseOutputs` filters whitelist, recovers case from `objects.list`, excludes std outputs | unit (pure, live fixtures) | `--tests works.mees.dinghy.outputs.OutputsGateTest` | ❌ W0 | ⬜ pending |
| SC-1 / D-10 | zero whitelisted outputs → drawer tile hidden | unit | `OutputsGateTest#emptyHidesTile` | ❌ W0 | ⬜ pending |
| SC-2 | every command builder emits correct gcode + 0–100%→0–1 scaling + clamp | unit | `--tests works.mees.dinghy.command.PrinterCommandsOutputsTest` | ❌ W0 | ⬜ pending |
| SC-2 | digital vs PWM `output_pin` branches on settings `pwm` boolean | unit | `OutputsGateTest#pwmDetection` | ❌ W0 | ⬜ pending |
| SC-3 | absent live value → row value hidden but row tappable; static pin read-only | unit (holder/state) | `--tests works.mees.dinghy.outputs.OutputsHolderTest` | ❌ W0 | ⬜ pending |
| SC-3 | `servo` reports `value` (PWM), not angle → value degraded/hidden | unit | `OutputsHolderTest#servoValueHidden` | ❌ W0 | ⬜ pending |
| SC-4 | proven live against real printers (E5P + E3P) | **manual on-device + live REST capture** | owner UAT on flox; cross-check `/server/gcode_store` | manual | ⬜ pending |
| D-09 | all 8 owner-selected ligatures resolve in bundled v2.944 ttf | tooling gate | `python tools/verify_ligatures.py` (extend NEEDED set) | ✅ extend | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `outputs/OutputsGateTest.kt` — whitelist filter, case-recovery, empty-hides-tile, pwm detection (stubs, compile day-one)
- [ ] `command/PrinterCommandsOutputsTest.kt` — command builders + scaling + clamp stubs
- [ ] `outputs/OutputsHolderTest.kt` — live-value degrade + servo-value-hidden stubs
- [ ] Live-capture JSON fixtures (E5P 9 outputs, E3P 1 virtual pin) committed under test resources — REAL Moonraker shape, not idealized mock (closes mock-vs-reality trap)

*Wave-0 RED scaffolds MUST compile day-one (typed `fail()` bodies, no refs to unbuilt symbols) — Gradle compiles the whole test sourceset before `--tests` filters.*

---

## Manual-Only Verifications

| Behavior | SC | Why Manual | Test Instructions |
|----------|----|-----------|-------------------|
| Live actuation of fan %, LED color+brightness+Off, output_pin toggle/PWM, servo angle on real hardware | SC-4 | Requires real printer + physical confirmation of the object-state flip | On flox connected to E5P: open Outputs tile → set Filter Fan %, set Chamber Light color/brightness/Off, toggle/PWM a mosfet pin, set Camera Servo angle → confirm each via object-state flip; cross-check server-side `/server/gcode_store`. On E3P confirm sparse single-output renders (tile NOT hidden when ≥1). Owner is the authoritative gate. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < seconds (quick run)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
