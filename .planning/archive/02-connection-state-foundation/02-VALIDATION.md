---
phase: 2
slug: connection-state-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-30
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `02-RESEARCH.md` § Validation Architecture (FakeWebSocket replay, pure reducers,
> virtual-time coroutines, golden Ender 5 Plus corpus).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 (JVM unit tests) + `kotlinx-coroutines-test` (virtual time) |
| **Config file** | `app/build.gradle.kts` (test deps); Wave 0 adds `kotlinx-coroutines-test` to `gradle/libs.versions.toml` |
| **Quick run command** | `gw.bat :app:testDebugUnitTest --tests "works.mees.dinghy.connection.*"` (Windows-side via helper) |
| **Full suite command** | `gw.bat :app:testDebugUnitTest` |
| **Estimated runtime** | ~{TBD by planner} seconds |

---

## Sampling Rate

- **After every task commit:** Run quick run command (relevant package)
- **After every plan wave:** Run full unit suite
- **Before `/gsd-verify-work`:** Full suite must be green; on-device real-Ender-5-Plus smoke run (no-auth path) per D-06
- **Max feedback latency:** {TBD} seconds

---

## Per-Task Verification Map

> Populated against the PLAN.md tasks. Each Phase-2 requirement maps to at least one automated seam
> (see 02-RESEARCH.md req→test map). Auth (CONN-02) is mock-socket-only per D-06.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| {N}-01-01 | 01 | 1 | REQ-{XX} | T-{N}-01 / — | {expected secure behavior or "N/A"} | unit | `{command}` | ✅ / ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `gradle/libs.versions.toml` — add `kotlinx-coroutines-test` (test-only; virtual-time backoff/conflation tests)
- [ ] Golden-frame fixture corpus captured from live Ender 5 Plus (`notify_status_update`, `objects.query`) — seeds mock-socket replay
- [ ] Hand-authored adversarial frames: interleaved `notify_*` (STATE-05), `401`/identify-error, klippy shutdown/error
- [ ] FakeWebSocket / mock-socket test harness scaffold

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live long-lived ws + auto-reconnect on Wi-Fi yank | CONN-03 / CONN-04 | Requires real Ender 5 Plus on LAN + physical network interruption | Launch dev build pointed at live printer; confirm live `notify_status_update`; yank Wi-Fi; confirm backoff reconnect + resync handshake restores correct state |

*Auth (CONN-02) is NOT manually verified on hardware — test-bed printer is open/no-auth (D-06); proven in mock-socket tests only.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < {N}s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
