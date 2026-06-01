---
phase: 5
slug: core-print-control-panels-temperature-move-extrude
status: ready
nyquist_compliant: true
wave_0_complete: true
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
| 05-01-01 | 01 | 1 | MOVE-04, EXTR-04 | T-05-01-T / T-05-01-Safety | gcode_position + can_extrude captured null-safely; canExtrude fail-safe false | unit | `gw.bat :app:testReleaseUnitTest --tests *PrinterStateReducerTest` | ✅ extend | ⬜ pending |
| 05-01-02 | 01 | 1 | EXTR-02 | — | case-insensitive macro presence (Pitfall 2) | unit | `gw.bat :app:testReleaseUnitTest --tests *DeriveCapabilitiesTest` | ✅ extend | ⬜ pending |
| 05-02-01 | 02 | 1 | TEMP-02/03, MOVE-01/02/03, EXTR-01 | T-05-02-T / T-05-02-Safety | bounded gcode builders; SAVE/RESTORE wrap; numeric clamp | unit | `gw.bat :app:testReleaseUnitTest --tests *PrinterCommandsTest` | ❌ W0 | ⬜ pending |
| 05-02-02 | 02 | 1 | TEMP-04 | — | third trace token baked, no raw hex (THEME-01) | compile | `gw.bat :app:compileReleaseKotlin` | ✅ | ⬜ pending |
| 05-03-01 | 03 | 2 | TEMP-04 | T-05-03-T | pure temperature_store→series mapper (order/selection/absence) | unit | `gw.bat :app:testReleaseUnitTest --tests *TemperatureStoreBackfillTest` | ❌ W0 | ⬜ pending |
| 05-03-02 | 03 | 2 | TEMP-04, EXTR-04 | T-05-03-D / T-05-03-Safety | one-shot best-effort reads; min_extrude_temp off hot path | compile+unit | `gw.bat :app:testReleaseUnitTest :app:compileReleaseKotlin` | ✅ | ⬜ pending |
| 05-04-01 | 04 | 2 | TEMP-04 | T-05-04-D | N traces allocation-free, fixed Y-range (G-1 fix) | unit | `gw.bat :app:testReleaseUnitTest --tests *GraphDownsampleTest` | ✅ extend | ⬜ pending |
| 05-04-02 | 04 | 2 | TEMP-04 | T-05-04-D | multi-snapshot host, single-trace back-compat | compile | `gw.bat :app:compileReleaseKotlin` | ✅ | ⬜ pending |
| 05-05-01 | 05 | 3 | TEMP-01/04 | — | per-sensor legend + backfilled rings, no second throttle | unit | `gw.bat :app:testReleaseUnitTest --tests *TemperatureHolderTest` | ❌ W0 | ⬜ pending |
| 05-05-02 | 05 | 3 | TEMP-01/02/03/04 | T-05-05-T/T2/D/I | dispatch via GCODE_SCRIPT; token-pure; scrubber/presets/cooldown | compile | `gw.bat :app:compileReleaseKotlin` | ✅ | ⬜ pending |
| 05-06-01 | 06 | 3 | MOVE-04 | — | gcode_position + per-axis homed gating | unit | `gw.bat :app:testReleaseUnitTest --tests *MoveHolderTest` | ❌ W0 | ⬜ pending |
| 05-06-02 | 06 | 3 | MOVE-01/02/03/04 | T-05-06-T/Safety/T2/D | jog/home via dispatcher; disable behind ConfirmGuard; token-pure | compile | `gw.bat :app:compileReleaseKotlin` | ✅ | ⬜ pending |
| 05-07-01 | 07 | 3 | EXTR-02/03/04 | T-05-07-Safety | live can_extrude gate (fail-safe); tools/macro presence/min-temp | unit | `gw.bat :app:testReleaseUnitTest --tests *ExtrudeHolderTest` | ❌ W0 | ⬜ pending |
| 05-07-02 | 07 | 3 | EXTR-01/02/03/04 | T-05-07-Safety/T/T2/D | cold-extrude gate + missing-macro popup + gated tool selector | compile | `gw.bat :app:compileReleaseKotlin` | ✅ | ⬜ pending |
| 05-08-01 | 08 | 4 | TEMP-04, MOVE-01/02/03, EXTR-01/02/04 | — | three panels reachable + full-bleed routing | compile+unit | `gw.bat :app:testReleaseUnitTest :app:compileReleaseKotlin` | ✅ | ⬜ pending |
| 05-08-02 | 08 | 4 | TEMP-04 | T-05-08-Perf | D-06 two-part Adreno-320 perf gate re-measured on flox | on-device | manual gfxinfo capture + parse_framestats.py | n/a | ⬜ pending (checkpoint) |
| 05-08-03 | 08 | 4 | MOVE-01/02/03, EXTR-01/02/04, TEMP-02/03/04 | T-05-08-Safety/D | SC-5 preheat→wait→extrude→jog end-to-end on live Ender 5 Plus | on-device | manual UAT on flox + live printer | n/a | ⬜ pending (checkpoint) |

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

**Approval:** populated by planner — Wave 0 test files (PrinterCommandsTest, TemperatureStoreBackfillTest, TemperatureHolderTest, MoveHolderTest, ExtrudeHolderTest) created within their owning plans; PerfResults + SC-5 are blocking on-device checkpoints in 05-08.
