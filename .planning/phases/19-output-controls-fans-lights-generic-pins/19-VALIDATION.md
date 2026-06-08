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

> CRITICAL (HIGH-4 review fix): Wave-0 commits intentionally-RED scaffolds (`PrinterCommandsOutputsTest`,
> `OutputsGateTest`, `OutputsHolderTest`) that are turned GREEN by their owning plan in a LATER wave. A full
> `:app:testDebugUnitTest` run during waves 0–4 would therefore FAIL on still-red scaffolds owned by another
> plan. So per-wave/per-task gates are SCOPED to the specific test classes that wave actually turns green
> (`--tests <Class>`); the true full-suite gate is reserved for the FINAL pre-UAT step (19-08), by which point
> every scaffold has been filled.

- **After every task commit:** Run the SCOPED class(es) that task turns green — e.g.
  `--tests *PrinterCommandsOutputsTest` (19-03), `--tests *OutputsGateTest --tests *PrinterStateReducerOutputsTest`
  (19-04), `--tests *OutputsHolderTest` (19-05), `--tests *OutputScrubberSettleTest --tests *OutputLedCommandTest`
  (19-06), `--tests *AppDrawerOutputsGateTest` (19-07). NEVER the unfiltered full suite while red scaffolds exist.
- **Per-wave gate:** the scoped class run for that wave's new-green classes (NOT the full suite).
- **Phase gate (pre-UAT, 19-08 only):** the FULL `:app:testDebugUnitTest` — legitimate here because every
  Wave-0 scaffold is now filled green. Plus `verify_ligatures.py` exit 0 + owner on-device UAT.
- **Max feedback latency:** quick scoped run (seconds).

*Exception: 19-07 Task 2 (shell wiring) runs the full suite as its regression check — it is in Wave 4, after
all builder/gate/holder/detail scaffolds (waves 0–3) are green, so no red scaffold remains to brick it.*

---

## Per-Task Verification Map

> Skeleton — the planner finalizes per-task IDs/commands. Maps the 4 ROADMAP Success Criteria + D-09 to tests.

| SC / Decision | Behavior | Test Type | Automated Command (scoped per-wave) | File Exists | Status |
|---------------|----------|-----------|-------------------------------------|-------------|--------|
| SC-1 | `parseOutputs` filters whitelist, recovers case (objectKey) + bare commandName, excludes std outputs | unit (pure, live fixtures) | `--tests *OutputsGateTest` | ❌ W0 | ⬜ pending |
| SC-1 / D-10 | zero whitelisted outputs → drawer tile hidden (pure visibleDrawerTiles) | unit | `--tests *OutputsGateTest` / `--tests *AppDrawerOutputsGateTest` | ❌ W0/W4 | ⬜ pending |
| SC-2 | every command builder emits correct gcode (bare name) + 0–100%→0–1 scaling + clamp | unit | `--tests *PrinterCommandsOutputsTest` | ❌ W0 | ⬜ pending |
| SC-2 | output families registered as CommandSpecs (catalog) + drift | unit | `--tests *CommandRegistryGcodeTest` | ✅ extend | ⬜ pending |
| SC-2 | digital vs PWM `output_pin` branches on settings `pwm` boolean | unit | `*OutputsGateTest#pwmDetection` | ❌ W0 | ⬜ pending |
| SC-2 | settle-dispatch fires once per gesture, not per frame | unit/compose | `--tests *OutputScrubberSettleTest` | ❌ W3 | ⬜ pending |
| SC-2 | LED command-gen (hue/brightness → SET_LED, WHITE=0 policy) | unit | `--tests *OutputLedCommandTest` | ❌ W3 | ⬜ pending |
| SC-3 | absent live value → row value hidden but row tappable; static pin read-only; per-family reached (servo timeout-only) | unit (holder/state) | `--tests *OutputsHolderTest` | ❌ W0 | ⬜ pending |
| SC-3 | `servo` reports `value` (PWM), not angle → value degraded/hidden | unit | `*OutputsHolderTest#servoValueHidden` | ❌ W0 | ⬜ pending |
| SC-3 | OutputLiveValue partial/null merge; heater_generic single-source divergence | unit | `--tests *PrinterStateReducerOutputsTest` | ❌ W1 | ⬜ pending |
| SC-4 | proven live against real printers (E5P + E3P) | **manual on-device + live REST capture** | owner UAT on flox; REQUIRED `/server/gcode_store` for fan/LED/pin/servo | manual | ⬜ pending |
| D-09 | all 8 owner-selected ligatures resolve in bundled v2.944 ttf | tooling gate | `python tools/verify_ligatures.py` (extend NEEDED set) | ✅ extend | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `outputs/OutputsGateTest.kt` — whitelist filter, case-recovery (objectKey) + bare commandName, empty-hides-tile, pwm detection (stubs, compile day-one)
- [ ] `command/PrinterCommandsOutputsTest.kt` — command builders (bare name) + scaling + clamp stubs
- [ ] `outputs/OutputsHolderTest.kt` — live-value degrade + servo-value-hidden + per-family reached stubs
- [ ] Live-capture JSON fixtures (E5P 9 outputs, E3P 1 virtual pin) committed under test resources — REAL Moonraker shape, not idealized mock (closes mock-vs-reality trap)

*Wave-0 RED scaffolds MUST compile day-one (typed `fail()` bodies, no refs to unbuilt symbols) — Gradle compiles the whole test sourceset before `--tests` filters. They are turned green by their OWNING plan in a later wave; intermediate gates are scoped to avoid bricking on another plan's still-red scaffold (HIGH-4).*

---

## Manual-Only Verifications

| Behavior | SC | Why Manual | Test Instructions |
|----------|----|-----------|-------------------|
| Live actuation of fan %, LED color+brightness+Off, output_pin toggle/PWM, servo angle on real hardware | SC-4 | Requires real printer + physical confirmation of the object-state flip | On flox connected to E5P: open Outputs tile → set Filter Fan %, set Chamber Light color/brightness/Off, toggle/PWM a mosfet pin, set Camera Servo angle/Off → confirm each via object-state flip; REQUIRED server-side `/server/gcode_store` evidence for fan/LED/pin/servo. On E3P confirm sparse single-output renders (tile NOT hidden when ≥1). Owner is the authoritative gate. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] Per-wave gates SCOPED to that wave's green classes; full suite reserved for 19-08 phase gate (HIGH-4)
- [ ] No watch-mode flags
- [ ] Feedback latency < seconds (quick run)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
