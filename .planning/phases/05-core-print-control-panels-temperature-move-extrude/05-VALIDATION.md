---
phase: 5
slug: core-print-control-panels-temperature-move-extrude
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-31
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 (unit) + AndroidX instrumented (`connectedAndroidTest`); on-device UAT on `flox` |
| **Config file** | `app/build.gradle.kts` (test deps); see Phase 2–4 test modules |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Full suite command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon"` |
| **Estimated runtime** | ~60–180 seconds (unit fast; instrumented gated by device) |

---

## Sampling Rate

- **After every task commit:** Run quick unit-test command for touched reducers/mappers
- **After every plan wave:** Run full suite command
- **Before `/gsd-verify-work`:** Full suite must be green + live-printer UAT items exercised
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| {N}-01-01 | 01 | 1 | REQ-{XX} | T-{N}-01 / — | {expected secure behavior or "N/A"} | unit | `{command}` | ✅ / ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
*Per-task rows are populated by the planner from PLAN.md tasks.*

---

## Wave 0 Requirements

- [ ] State-model reducer tests — `can_extrude`, `gcode_position`, `min_extrude_temp`, homed-axes, macro presence (pure-reducer additions to `deriveCapabilities`/`PrinterState`)
- [ ] `temperature_store` → multi-series mapping unit tests (TEMP-04 backfill)

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| PRIM-05 in-flight/timeout/debounce in anger | SC-4 | Requires dropped-packet / real-latency conditions | On flox + live Ender 5 Plus: tap action twice fast (debounce), pull network mid-call (timeout), observe in-flight state |
| Multi-trace graph perf gate (D-06) | TEMP-04 | Adreno-320 fill-rate is the bottleneck; emulators lie | Re-measure two-part gate on flox: liveness (alloc-free / no anim loop / 0 frozen frames) + sparse-redraw p95 ≤ ~66 ms during active heat. Do NOT grandfather 50.1 ms isolated number. |
| End-to-end preheat → wait → extrude → jog | SC-5 | Real hardware, no browser | On live Ender 5 Plus: PLA preheat preset → wait temp → extrude 5mm → jog X/Y/Z |
| Override-cell unhomed jog gcode | MOVE-01 | Exact gcode unconfirmed (research LOW confidence) | Confirm on flox the Override cell jogs an unhomed axis without faulting |

*If none: "All phase behaviors have automated verification."*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
