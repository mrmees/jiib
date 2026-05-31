---
phase: 4
slug: service-shell-settings-print-status-home
status: active
nyquist_compliant: true
wave_0_complete: false
created: 2026-05-30
regenerated: 2026-05-31
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `04-RESEARCH.md` § Validation Architecture and the 7 plans as written
> (04-01..04-07, waves 1–4). **Regenerated 2026-05-31** for the actual Phase-4 scope:
> the design-system primitives (confirm/keypad/toast/render) live in **Phase 3** and are
> CONSUMED here, not built — so the stale PRIM-01/PRIM-02/PRIM-04 primitive rows and the old
> "status chip / E-stop hold-gesture / dashboard" rows from the pre-restructure draft are
> removed. The shell model is the `docs/ui_design/` system (swipe-up App Drawer, Settings
> screen, Print Status home, **Stop → ConfirmGuard** per CONTEXT **D-10** — NOT a hold gesture).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 + `kotlinx-coroutines-test` (virtual time) for JVM; AndroidX Test + UiAutomator/Compose-UI-test for on-device (both source sets exist from Phase 2) |
| **Config file** | `app/build.gradle.kts` (test + androidTest source sets) |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Full suite command** | quick + `cmd.exe /c "E:\Android\gw.bat :app:connectedDebugAndroidTest --no-daemon"` on `flox` |
| **Estimated runtime** | JVM ~30–60s; connected suite ~2–4 min on-device |

---

## Sampling Rate

- **After every task commit:** Run `:app:testDebugUnitTest` (pure logic: `derive()`, `CommandDispatcher`, `ConnectionStore`, `PrintStatusHolder`, `MoonrakerService` config-rebuild seam).
- **After every plan wave:** Add the wave's instrumented tests via `:app:connectedDebugAndroidTest` on `flox` (rotation survival in Wave 2; shell presence in Wave 4).
- **Before `/gsd-verify-work`:** Full suite green **plus the on-device human gates** — (1) rotation/screen-off connection survival (04-03), (2) gfxinfo Print-Status-cadence-without-jank during a live heat (04-06), (3) shell drawer/routing/splash-no-drawer (04-07).
- **Max feedback latency:** ~60s for the JVM tier; on-device tiers are wave/phase gates, not per-task.

---

## Per-Task Verification Map

| Req ID | Plan | Wave | Behavior | Test Type | Automated Command / Signal | File Exists | Status |
|--------|------|------|----------|-----------|----------------------------|-------------|--------|
| CONN-01 | 04-01 | 1 | Connection config persists across restart | unit (DataStore round-trip) | JVM `ConnectionStoreTest` round-trip (write → read; instrumented kill/relaunch covered by 04-03 checkpoint) | ❌ W0 | ⬜ pending |
| PRIM-05 | 04-02 | 1 | CommandDispatcher: debounce + in-flight + timeout | unit (virtual time) | JVM `CommandDispatcherTest` under `runTest`: double-tap → 1 dispatch; in-flight blocks re-entry; dropped packet → timeout re-enables | ❌ W0 | ⬜ pending |
| SHELL-02 (route) | 04-02 | 1 | Routing keyed off `klippy_state`, socket-state never routes | unit (pure `derive()`) | JVM `TopRouteTest`: `derive(cfg, Startup)==Splash`; `Ready+Printing==Shell(...)`; `Ready==Shell(PrintStatus)`; socket-state does NOT change route (D-05) | ❌ W0 | ⬜ pending |
| CONN-01 / SHELL-03 | 04-03 | 2 | Config-rebuild seam: null config idle; config change cancels prior session before relaunch (D-03) | unit (virtual time) | JVM `MoonrakerServiceTest`: null → no session job; change → `cancelAndJoin` before new launch; one active job | ❌ W0 | ⬜ pending |
| SHELL-03 | 04-03 | 2 | Service survives rotation (FGS owns spine) | instrumented (UiAutomator) | `ServiceSurvivesRotationTest`: `UiDevice.setOrientationLeft/Natural` → `AppContainer.connectionState` does NOT churn Connecting/Syncing | ❌ W0 | ⬜ pending |
| SHELL-03 | 04-03 | 2 | Service survives screen-off | on-device manual gate | `flox`: screen off 60s → on → still `Connected`, temps live not stale | manual | ⬜ pending |
| SET-01 / PRIM-02 / CONN-01 | 04-04 | 3 | Settings screen edits + persists connection (host/port/key) | instrumented (Compose UI) + reuses `ConnectionStoreTest` | enter host/port → Save → `ConnectionStore.config` updated; on-device confirm via 04-07 first-run flow | ⚠ via W0 + 04-07 | ⬜ pending |
| SHELL-05 / CONN-01 | 04-05 | 3 | Splash hard-override shows reason + recovery actions | instrumented (Compose UI) | shutdown/error state → Retry + firmware_restart + restart + Edit-connection actions visible (D-12); confirmed in 04-07 splash gate | ⚠ via 04-07 | ⬜ pending |
| SHELL-04 | 04-06 | 3 | PrintStatusHolder: PrinterState → bounded ring series + grid values | unit | JVM `PrintStatusHolderTest`: latest temp reflected; ring bounded ≤120; grid tracks state; no second throttle | ❌ W0 | ⬜ pending |
| SHELL-04 | 04-06 | 3 | Print Status renders at throttled cadence without jank | on-device gfxinfo gate | live heat → `dumpsys gfxinfo works.mees.dinghy reset` → 30s dwell → framestats: two-part Phase-3 gate (p95 ≲ ~66ms sparse-redraw, 0 frozen frames; cadence ≈ 2–4 Hz) | manual (reuse Phase-1 parser) | ⬜ pending |
| SHELL-02 | 04-06 | 3 | Stop → ConfirmGuard → `printer.emergency_stop` (D-10, NOT a hold gesture) | unit (holder/dispatch) + on-device round-trip | JVM: Stop requires ConfirmGuard `onConfirm` before dispatch; on-device: E-stop → Klippy `shutdown` → routes to splash recovery | ⚠ via 04-06 gate | ⬜ pending |
| SHELL-01 | 04-07 | 4 | Shell present + navigable; greyed tiles inert; no drawer on splash | instrumented (Compose UI) | `ShellPresenceTest`: swipe-up → Status+Settings live + tappable; greyed (incl. Power) do not navigate; drawer absent under Splash route (D-06) | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky · ⚠ covered indirectly*

---

## Wave 0 Requirements

> Each Wave-0 test file is bound to the plan/task that introduces the unit under test.

- [ ] `ConnectionStoreTest.kt` (JVM) — DataStore round-trip (CONN-01) — **04-01**
- [ ] `CommandDispatcherTest.kt` (JVM, virtual time) — debounce/in-flight/timeout (PRIM-05) — **04-02**
- [ ] `TopRouteTest.kt` (JVM) — pure `derive()` cases incl. socket-state-does-not-route (SHELL-02 routing) — **04-02**
- [ ] `MoonrakerServiceTest.kt` (JVM, virtual time) — config-rebuild seam: null-idle + cancel-before-relaunch (SHELL-03/D-03) — **04-03**
- [ ] `PrintStatusHolderTest.kt` (JVM) — bounded ring accumulation + grid model off `PrinterState` (SHELL-04 data half) — **04-06**
- [ ] `ServiceSurvivesRotationTest.kt` (androidTest, UiAutomator) — rotation continuity (SHELL-03) — **04-03**
- [ ] `ShellPresenceTest.kt` (androidTest, Compose UI) — drawer presence/tappability, greyed-tile inertness, splash-no-drawer (SHELL-01/D-06/D-14) — **04-07**
- [ ] Reuse Phase-1 `gfxinfo` framestats parser for the Print-Status cadence gate (no new tool) — **04-06**

> Design-system primitives (ConfirmGuard/keypad/keyboard/toast — PRIM-01/02/03/04) are **built and
> tested in Phase 3** and only CONSUMED here; they are NOT Phase-4 Wave-0 obligations.

---

## Manual-Only Verifications

| Behavior | Requirement | Plan | Why Manual | Test Instructions |
|----------|-------------|------|------------|-------------------|
| Service survives screen-off | SHELL-03 | 04-03 | Doze/screen-off behavior is device-real; emulator lies | On `flox`: connect, screen off 60s, screen on → assert still `Connected`, temps live not stale |
| Print Status cadence without jank | SHELL-04 | 04-06 | Adreno 320 fill-rate is the real bottleneck; only real hardware proves it | Live heat ramp → `dumpsys gfxinfo works.mees.dinghy reset` → 30s dwell → parse framestats: two-part Phase-3 gate (p95 ≲ ~66ms, 0 frozen frames, cadence ≈ 2–4 Hz) |
| E-stop reaches printer (ConfirmGuard path) | SHELL-02 | 04-06 | Requires a live Klippy round-trip with physical consequence | Real Ender 5 Plus: Stop → ConfirmGuard confirm → `printer.emergency_stop` → assert `klippyState==Shutdown` within timeout → routes to splash recovery |
| Shell drawer + routing + splash-no-drawer | SHELL-01 | 04-07 | Full end-to-end shell behavior on real hardware | On `flox`: swipe-up drawer (Status+Settings live, rest+Power greyed), full-bleed routing, splash has no reachable drawer (in addition to the automated `ShellPresenceTest`) |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (04-03's three compile-only tasks now bracketed by `MoonrakerServiceTest` JVM signal)
- [x] Wave 0 covers all MISSING references (ConnectionStore, CommandDispatcher, TopRoute, MoonrakerService, PrintStatusHolder, ServiceSurvivesRotation, ShellPresence)
- [x] No watch-mode flags
- [x] Feedback latency < 60s (JVM tier)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** active (matches plans 04-01..04-07 as written, 2026-05-31)
