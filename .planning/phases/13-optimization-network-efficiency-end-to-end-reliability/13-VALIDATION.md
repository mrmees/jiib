---
phase: 13
slug: optimization-network-efficiency-end-to-end-reliability
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-03
---

# Phase 13 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `13-RESEARCH.md` → Validation Architecture. This is a refactor/reliability
> phase with **no functional REQ-IDs** — requirements below map to ROADMAP Success Criteria
> (SC-1…SC-4) and the binding CONTEXT decisions (D-03, D-07, D-09, D-10).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 + kotlinx-coroutines-test (`runTest`, `UnconfinedTestDispatcher`, virtual time) |
| **Config file** | `app/build.gradle` test deps — no separate config |
| **Quick run command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests KlippyReadyResyncTest --no-daemon"` |
| **Full suite command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` |
| **Estimated runtime** | ~Quick <30s · Full suite a few min (release unit tests) |

> Pipe Gradle output through `tr -d '\r'` (CR progress bars); the process **exit code is authoritative**.

---

## Sampling Rate

- **After every task commit:** Run the keystone regression — `:app:testReleaseUnitTest --tests KlippyReadyResyncTest` (the hardened mock-vs-reality test).
- **After every plan wave:** Run the full suite — `:app:testReleaseUnitTest`.
- **Before `/gsd-verify-work`:** Full unit suite green **AND** the D-09 dual-printer dual-scenario on-device UAT passed.
- **Max feedback latency:** < 30s for the quick run.

---

## Per-Task Verification Map

> Plan-level mapping; the planner fills exact task IDs. Sources: ROADMAP SC-1…SC-4, CONTEXT D-03/D-07/D-09/D-10.

| Item | Behavior | Threat Ref | Test Type | Automated Command | File Exists | Status |
|------|----------|------------|-----------|-------------------|-------------|--------|
| D-10 (gate) | klippy-down window → re-subscribe → **diffs resume** (printState→Printing reaches `store`) | — | unit (hardened mock) | `… --tests KlippyReadyResyncTest` | ❌ MUST HARDEN — currently asserts sent-frames only | ⬜ pending |
| D-03 | re-handshake emits `Syncing` then `Connected` (not silent) | — | unit | `… --tests *Session*` / new `KlippyRecoveryStateTest` | ❌ W0 | ⬜ pending |
| Self-heal | re-handshake FAILURE escalates to full reconnect (no dead-forever socket) | — | unit (mock rejects subscribe in down-window) | new test vs hardened mock | ❌ W0 | ⬜ pending |
| SC-2 / D-07 | mid-print socket `Closed` → reconnect → printState resyncs | — | unit | `… --tests ReconnectSupervisorTest` (extend) | ⚠ extend existing | ⬜ pending |
| SC-1 | `refreshProbeZOffset` redundant `configfile` query removed; no per-screen polling reintroduced | — | unit + doc grep | `… --tests *Session*` + grep for stray `objects.subscribe` / timer polls | ⚠ remove-test new (W0) | ⬜ pending |
| SC-3 / D-09a | SAVE_CONFIG → start print → feed live + printState→Printing, **no app restart** | — | manual on-device, BOTH printers | live UAT (Matthew + flox) | manual-only — justified | ⬜ pending |
| SC-3 / D-09b | mid-print network drop → reconnect → print resyncs | — | manual on-device, BOTH printers | live UAT | manual-only — justified | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] **Capture-first (D-10) — BLOCKS all fix tasks:** live websocket capture of the SAVE_CONFIG / klippy-restart sequence on E5 (Pi 4, 192.168.1.120:7125) **and** E3 (RockPro64, 192.168.1.121:7125); commit `.jsonl` fixtures.
- [ ] Install a scriptable websocket client for the capture (`pip install websockets` WSL-native, or `websocat`).
- [ ] Harden `FakeWebSocket` / `SessionTestHarness` with a **klippy-down window** mode (reject/withhold subscribe replies; replay the captured notification sequence).
- [ ] Rewrite `KlippyReadyResyncTest` to assert **resumed diffs** (inject post-restart `notify_status_update` printState→Printing, assert it reaches `store`) — not sent-frame counts.
- [ ] New `KlippyRecoveryStateTest` — asserts `Syncing`→`Connected` emission on re-handshake (D-03).
- [ ] New self-heal test — re-handshake failure in down-window escalates to full reconnect.
- [ ] Extend `ReconnectSupervisorTest` for mid-print socket-death resync (D-07b).

---

## Manual-Only Verifications

| Behavior | Source | Why Manual | Test Instructions |
|----------|--------|------------|-------------------|
| SAVE_CONFIG → start print → feed stays live + printState→Printing without app restart | SC-3 / D-09a | Requires a real Klipper FIRMWARE_RESTART on physical hardware | On E5 then E3: connect app → issue SAVE_CONFIG (or FIRMWARE_RESTART) → start a print → assert feed stays live and new print registers, no force-close |
| mid-print network drop → reconnect → print state resyncs | SC-3 / D-09b | Requires real LAN drop mid-print | On E5 then E3: start a print → drop WiFi/network → restore → assert print state resyncs correctly |

> Both run on **BOTH printers** (D-09) — the E5 is unproven, not assumed-good (D-02).

---

## Validation Sign-Off

- [ ] All tasks have an `<automated>` verify or a Wave 0 dependency
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (capture fixtures + hardened mock)
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s (quick run)
- [ ] `nyquist_compliant: true` set in frontmatter
- [ ] D-09 dual-printer dual-scenario on-device UAT defined as the binding phase gate

**Approval:** pending
