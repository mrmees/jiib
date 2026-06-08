---
phase: 20-system-information-page
plan: 01
subsystem: systeminfo
tags: [test-fixtures, red-scaffold, icon-gate, moonraker, proc-stats, wave-0]
requires: []
provides:
  - Five live-captured fixture JSONs (both SBCs, both data planes) as test resources
  - Five compiling RED test scaffolds (SYS-01..04 surface) pending Plan 02
  - Phase-20 ligature-drift guard protecting the 10 owner-locked D-01..D-10 glyphs
affects:
  - tools/verify_ligatures.py (NEEDED set: +8 new ligatures)
tech-stack:
  added: []
  patterns:
    - "Capture-driven fixtures (anti mock-vs-reality): commit the real Moonraker JSON, encode the correctness traps in the data"
    - "Compiling-RED scaffolds: Assume.assumeTrue(<symbol pending>, false) for behavior tests + idiom-(a) fixture-shape assertions"
key-files:
  created:
    - app/src/test/resources/fixtures/system_info_e5.json
    - app/src/test/resources/fixtures/system_info_e3.json
    - app/src/test/resources/fixtures/proc_stats_e5.json
    - app/src/test/resources/fixtures/proc_stats_e3.json
    - app/src/test/resources/fixtures/notify_proc_stat_push_e5.json
    - app/src/test/java/works/mees/dinghy/systeminfo/SystemInfoParseTest.kt
    - app/src/test/java/works/mees/dinghy/systeminfo/ProcStatPushTest.kt
    - app/src/test/java/works/mees/dinghy/systeminfo/HealthChipTest.kt
    - app/src/test/java/works/mees/dinghy/systeminfo/SysInfoFormatTest.kt
    - app/src/test/java/works/mees/dinghy/systeminfo/DegradeTest.kt
  modified:
    - tools/verify_ligatures.py
decisions:
  - "thermostat (D-03) + speed (D-09) already in the NEEDED set — not re-added (gate de-dups on the ligature string)"
  - "SystemInfoParseTest uses literal getResource(/fixtures/system_info_*) call-sites to satisfy the declared key-link pattern (not a variable-path helper)"
metrics:
  duration_min: 4
  completed: 2026-06-08
  tasks: 3
  files: 11
  commits: 4
---

# Phase 20 Plan 01: System Information Wave 0 (fixtures + RED scaffolds + icon gate) Summary

Locked the empirical ground truth and the test surface for the System Information page BEFORE any production code: five live-captured Moonraker fixtures (both SBCs, both data planes), five compiling RED test scaffolds, and the Phase-20 ligature-drift guard.

## What Was Built

- **Five fixtures** from the 20-RESEARCH live captures (2026-06-08, read-only REST/WS), each encoding a correctness trap the research found:
  - `system_info_e5.json` / `system_info_e3.json` — the `machine.system_info.result.system_info` shape. E5 (RPi4) has a populated `cpu_info.model`; E3 (RockPro64) has `model: ""` (empty string, not null/absent — the degrade-to-`—` trap). `kernel_version` lives under `distribution` on both.
  - `proc_stats_e5.json` / `proc_stats_e3.json` — the `machine.proc_stats.result`. E5 has a clean Pi `throttled_state: {bits:0, flags:[]}` object; E3 has `throttled_state: null` (the explicit-null temp-fallback discriminator).
  - `notify_proc_stat_push_e5.json` — a captured `notify_proc_stat_update` `params[0]` frame proving the push **omits** `throttled_state` AND `system_uptime` (RESEARCH Pitfall 1 — the single most important finding; those two only live in the one-shot query).
- **Five RED scaffolds** in `works.mees.dinghy.systeminfo`, compiling day-one per [[dinghy-wave0-red-scaffold-compile]] — behavior assertions guarded by `Assume.assumeTrue("Plan 02 implements <symbol>", false)`, fixture-shape assertions live (idiom (a)):
  - `SystemInfoParseTest` (SYS-01): model/cpu_count, empty-model degrade, kernel-under-distribution.
  - `ProcStatPushTest` (SYS-02): push parses cpu%/mem/temp; asserts the push omits throttle+uptime so the parser must tolerate their absence.
  - `HealthChipTest` (SYS-03): clean→healthy, synthetic active-bit→caution, synthetic occurred-bit→warn, E3 null-throttle temp-fallback at the D-12 LOCKED 70/80 cutoffs.
  - `SysInfoFormatTest`: kB→GB, kB→MB, compact-uptime dropping leading-zero units (`2d 3h 14m`).
  - `DegradeTest` (SYS-04): empty model, null `throttled_state`, missing keys → `—`, no crash.
- **`tools/verify_ligatures.py`** Phase-20 block: added the 8 new-to-NEEDED owner-locked glyphs (`pulse_alert`, `dns`, `schedule`, `developer_board`, `memory`, `deployed_code`, `code_blocks`, `data_usage`). `thermostat` (D-03) and `speed` (D-09) were already present and were NOT re-added (the gate de-dups on the ligature string).

## Tasks Completed

| Task | Name | Commit | Files |
| ---- | ---- | ------ | ----- |
| 1 | Commit the five live-captured fixture JSONs | `3072073` | 5 fixtures under `app/src/test/resources/fixtures/` |
| 2 | Five RED test scaffolds that compile day-one | `17b5703` (+ `450001b` key-link fix) | 5 test files in `.../systeminfo/` |
| 3 | Add the 10 Phase-20 ligatures to the drift gate | `10a50fa` | `tools/verify_ligatures.py` |

## Verification

- Full `:app:testDebugUnitTest` → **BUILD SUCCESSFUL** exit 0 (run twice: after Task 2, and after the key-link edit). The whole test sourceset compiles; the new RED scaffolds skip (assumed-pending) or pass (fixture-shape), no compile error — satisfying the Wave-0 "scaffold bricks all per-wave runs" guard.
- `python3 tools/verify_ligatures.py` → `63 needed, 3953 ligatures in font, missing: []`, exit 0.
- Task-1 fixture-trap grep gate: `proc_stats_e3.json` has literal `throttled_state: null`; `notify_proc_stat_push_e5.json` has NEITHER `throttled_state` NOR `system_uptime`; `proc_stats_e5.json` `throttled_state` is an object with `bits`+`flags`; `system_info_e3.json` `cpu_info.model` is `""` → all OK.
- Threat T-20-01-I: secret-scan of all 5 fixtures returned no api_key/token/password/secret.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Key-link pattern not satisfied by the variable-path fixture helper**
- **Found during:** post-task verification (must-have `key_links` check).
- **Issue:** The first `SystemInfoParseTest` used a `getResource("/fixtures/$name")` helper, so the literal string `getResource(...fixtures/system_info...)` the plan's declared key-link asserts never appeared in the file (grep returned MISSING).
- **Fix:** Replaced the variable-path helper with literal per-fixture loaders (`systemInfoE5()`/`systemInfoE3()` → `getResource("/fixtures/system_info_e5.json")`), matching the BedMeshModelTest analog convention. Behavior unchanged; suite re-run GREEN.
- **Files modified:** `app/src/test/java/works/mees/dinghy/systeminfo/SystemInfoParseTest.kt`
- **Commit:** `450001b`

## Notes for Wave 1 (Plan 02)

The scaffolds name the exact production symbols Plan 02 must introduce in `works.mees.dinghy.systeminfo`: `SystemInfo.from(jsonObject)`, `ProcStatLive.fromPush(jsonObject)`, the pure `healthState(throttledState, cpuTemp)` fn + `HealthState` enum, and the kB→GB / kB→MB / compact-uptime formatters. The D-12 cutoffs (70 warn / 80 caution) and the `2d 3h 14m` uptime format are documented inline in the scaffolds as the contract. Plan 02 will also need to update the T-11-04 notify golden when it routes `notify_proc_stat_update` (RESEARCH Pitfall 5).

## Self-Check: PASSED

All 11 created/modified files present on disk; all 5 commits present in git history.
