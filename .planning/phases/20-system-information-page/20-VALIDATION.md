---
phase: 20
slug: system-information-page
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-08
---

# Phase 20 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `20-RESEARCH.md` § Validation Architecture (capture-driven fixtures from BOTH live hosts).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 host unit tests (`app/src/test`); on-device manual UAT for SC-4 (flox + both printers) |
| **Config file** | standard AGP test config — no extra setup |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests '*SystemInfo*' --no-daemon"` |
| **Full suite command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Estimated runtime** | ~60–120 seconds (host unit suite) |

---

## Sampling Rate

- **After every task commit:** Run quick run (`--tests '*SystemInfo*'`)
- **After every plan wave:** Run full suite (`:app:testDebugUnitTest`)
- **Before `/gsd-verify-work`:** Full suite green + on-device UAT against BOTH printers (SC-4)
- **Max feedback latency:** ~120 seconds

---

## Per-Task Verification Map

| Req | Behavior | Wave | Test Type | Automated Command | File Exists | Status |
|-----|----------|------|-----------|-------------------|-------------|--------|
| SYS-01 | `system_info` parses to identity model (empty `model`→`—`, kernel from `distribution`) | 1 | unit (both host fixtures) | `--tests '*SystemInfoParse*'` | ❌ W0 | ⬜ pending |
| SYS-02 | proc_stat PUSH parses cpu%/mem/temp; parser does NOT require throttle/uptime | 1 | unit (push fixture) | `--tests '*ProcStatPush*'` | ❌ W0 | ⬜ pending |
| SYS-02 | live values route through new notify flow; throttled to ≤1 Hz; T-11-04 golden updated | 1 | unit (notify routing) | `--tests '*JsonRpc*'` | ⚠️ update existing | ⬜ pending |
| SYS-03 | health chip: E5 clean→healthy; synthetic throttle bits→warn/caution; E3 temp-fallback @70/80 | 1 | unit (pure fn, both + synthetic) | `--tests '*HealthChip*'` | ❌ W0 | ⬜ pending |
| SYS-03 | uptime compact + kB→GB/MB formatters | 1 | unit | `--tests '*SysInfoFormat*'` | ❌ W0 | ⬜ pending |
| SYS-04 | sparse/null fields → `—`, no crash (empty model, null `throttled_state`, missing keys) | 1 | unit | `--tests '*Degrade*'` | ❌ W0 | ⬜ pending |
| SYS-05 | on-device: both printers show correct identity/live/health; chip reads identically | — | manual UAT (flox vs E5 + E3) | on-device | manual gate | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Capture + commit 5 fixture JSONs from this research's live captures:
  - `app/src/test/resources/fixtures/system_info_e5.json` / `system_info_e3.json`
  - `app/src/test/resources/fixtures/proc_stats_e5.json` / `proc_stats_e3.json` (E5 clean throttle, E3 null throttle)
  - `app/src/test/resources/fixtures/notify_proc_stat_push_e5.json` (captured push frame — proves throttle/uptime omission)
- [ ] RED scaffolds (must compile day-one per `dinghy-wave0-red-scaffold-compile`): `SystemInfoParseTest`, `ProcStatPushTest`, `HealthChipTest`, `SysInfoFormatTest`, `DegradeTest`
- [ ] No new framework install needed — existing JUnit4 host infra covers it

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Both printers render correct identity/live/health; chip reads identically across RPi 4 + RockPro64 | SYS-05 / SC-4 | Cross-SBC ground truth + visual chip parity can only be confirmed on real hosts | Build, installDebug on flox, point at 192.168.1.120 (E5/RPi4) then 192.168.1.121 (E3/RockPro64); owner eyeballs identity rows, live CPU/mem cadence, CPU temp, and health-chip shape on each |
| Drawer-tile nav to System Information | SYS-05 | Instrumented drawer-open swipe is flaky on-device (`dinghy-instrumented-swipe-threshold`) | Manual flox tap, not an instrumented nav test |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (5 fixtures + 5 RED scaffolds)
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
