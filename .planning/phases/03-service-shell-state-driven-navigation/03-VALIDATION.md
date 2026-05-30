---
phase: 3
slug: service-shell-state-driven-navigation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-30
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `03-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 + `kotlinx-coroutines-test` (virtual time) for JVM; AndroidX Test + UiAutomator for on-device (both source sets exist from Phase 2) |
| **Config file** | `app/build.gradle.kts` (test + androidTest source sets) |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Full suite command** | quick + `cmd.exe /c "E:\Android\gw.bat :app:connectedDebugAndroidTest --no-daemon"` on `flox` |
| **Estimated runtime** | JVM ~30–60s; connected suite ~2–4 min on-device |

---

## Sampling Rate

- **After every task commit:** Run `:app:testDebugUnitTest` (pure logic: `derive()`, `CommandDispatcher`, `ConnectionStore`, dashboard ring).
- **After every plan wave:** Add `:app:connectedDebugAndroidTest` on `flox` (rotation-survival, route-screen presence).
- **Before `/gsd-verify-work`:** Full suite green **plus two on-device human gates** — (1) rotation/screen-off connection survival, (2) gfxinfo dashboard-cadence-without-jank during a live heat.
- **Max feedback latency:** ~60s for JVM tier; on-device tiers are wave/phase gates, not per-task.

---

## Per-Task Verification Map

> Task IDs are placeholders until plans are written; the planner MUST bind each Wave-0 test file to the task that introduces the unit under test. Threat refs from `03-RESEARCH.md` § Security Domain.

| Req ID | Behavior | Test Type | Automated Command / Signal | File Exists | Status |
|--------|----------|-----------|----------------------------|-------------|--------|
| CONN-01 | Config persists across restart | unit (DataStore) + instrumented | JVM `ConnectionStore` round-trip; instrumented write→kill→relaunch→read | ❌ W0 | ⬜ pending |
| SHELL-03 | Service survives rotation | instrumented (UiAutomator) | Connect → `UiDevice.setOrientationLeft/Natural` → assert `ConnectionState` stays `Connected` (no Connecting/Syncing transition), `PrinterState` flow uninterrupted | ❌ W0 | ⬜ pending |
| SHELL-03 | Service survives screen-off | on-device manual gate | Screen off 60s → on → still `Connected`, temps live not stale | manual | ⬜ pending |
| SHELL-02/route | Routing keyed off `klippy_state` | unit (pure `derive()`) | JVM: `derive(cfg, klippyState=Startup)==Splash`; `Ready+Printing==Shell(Job)`; `Ready==Shell(Dashboard)`; socket-state does NOT change route | ❌ W0 | ⬜ pending |
| SHELL-05 | Splash shows reason + recovery | unit + instrumented | JVM: Splash VM exposes `state_message`; instrumented: shutdown state → recovery buttons visible | ❌ W0 | ⬜ pending |
| SHELL-04 | Dashboard renders at throttled cadence w/o jank | on-device gfxinfo gate | Live heat ramp → `dumpsys gfxinfo reset` → 30s dwell → framestats: p95 < ADR floor (~42ms Views), 0 frozen frames; cadence ≈ 4 Hz | manual (reuse Phase-1 parser) | ⬜ pending |
| SHELL-01 | Status chip visible every screen | instrumented | UiAutomator: on each route assert status chip present | ❌ W0 | ⬜ pending |
| SHELL-02 | E-stop reachable every screen | instrumented | UiAutomator: on Splash/Dashboard/Job assert E-stop affordance; hold → `printer.emergency_stop` sent | ❌ W0 | ⬜ pending |
| SHELL-02 | E-stop reaches printer | on-device round-trip | Real printer: E-stop → Klippy → `klippyState==Shutdown` within timeout | manual gate | ⬜ pending |
| PRIM-05 | timeout + in-flight + debounce | unit (virtual time) | JVM `runTest`: double-tap within debounce → 1 dispatch; in-flight key blocks re-entry; dropped packet → timeout fires, button re-enables | ❌ W0 | ⬜ pending |
| PRIM-03 | Confirm gates destructive set | unit + instrumented | JVM: each destructive action requires confirm callback before dispatch; instrumented: estop/cancel shows dialog | ❌ W0 | ⬜ pending |
| PRIM-01 | Numeric keypad exists + consumable | instrumented (Compose UI test) | Keypad emits value | ❌ W0 | ⬜ pending |
| PRIM-02 | On-screen keyboard exists + consumable | instrumented (Compose UI test) | Keyboard emits text | ❌ W0 | ⬜ pending |
| PRIM-04 | Severity-styled toast exists + consumable | instrumented (Compose UI test) | Toast shows by severity | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `ConnectionStoreTest.kt` — DataStore round-trip (CONN-01)
- [ ] `TopRouteTest.kt` — pure `derive()` cases incl. socket-state-does-not-route (SHELL-02 routing)
- [ ] `CommandDispatcherTest.kt` — debounce/in-flight/timeout under `runTest` (PRIM-05)
- [ ] `ConfirmGateTest.kt` — destructive set requires confirm (PRIM-03)
- [ ] `DashboardRingTest.kt` — bounded ring accumulation off `PrinterState` (SHELL-04 data half)
- [ ] instrumented `ServiceSurvivesRotationTest.kt` (androidTest) — SHELL-03
- [ ] instrumented `ShellPresenceTest.kt` — status + E-stop on every route (SHELL-01/02/04)
- [ ] Compose-test stubs for keypad/keyboard/toast primitives (PRIM-01/02/04)
- [ ] Reuse Phase-1 `gfxinfo` framestats parser for the dashboard-cadence gate (no new tool)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Service survives screen-off | SHELL-03 | Doze/screen-off behavior is device-real; emulator lies | On `flox`: connect, screen off 60s, screen on → assert still `Connected`, temps live not stale |
| Dashboard cadence without jank | SHELL-04 | Adreno 320 fill-rate is the real bottleneck; only real hardware proves it | Live heat ramp → `dumpsys gfxinfo <pkg> reset` → 30s dwell → parse framestats: p95 < ~42ms, 0 frozen frames, cadence ≈ 4 Hz |
| E-stop reaches printer | SHELL-02 | Requires a live Klippy round-trip with physical consequence | Real Ender 5 Plus: trigger E-stop → assert `klippyState==Shutdown` within timeout |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s (JVM tier)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
