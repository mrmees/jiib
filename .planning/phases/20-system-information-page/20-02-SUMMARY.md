---
phase: 20-system-information-page
plan: 02
subsystem: systeminfo
tags: [parsers, pure-logic, health-chip, formatters, moonraker, proc-stats, wave-2, tdd]
requires:
  - Five live-captured fixture JSONs + five compiling RED scaffolds (Plan 20-01)
provides:
  - SystemInfo / ProcStatLive / ProcStatQuery / ThrottledState models (all-nullable, degrade-friendly)
  - Tolerant parsers SystemInfo.from / ProcStatLive.fromPush / ProcStatQuery.from (JsonNull/blank-tolerant, never throw)
  - Pure healthState(throttledState, cpuTemp) -> HealthState (throttle-authoritative + D-12 temp fallback)
  - Value formatters (kB->GB/MB auto-scale, compact uptime, cpu load/temp/cores), '—' on null
affects:
  - "Wave-3 (Plan 20-03): plumbing + UI consume these pure fns verbatim"
tech-stack:
  added: []
  patterns:
    - "Companion-extension parsers (SystemInfo.from etc.) on all-nullable data classes carrying an empty `companion object` so the extension fns resolve"
    - "Tolerant JsonObject walk (objectOrNull/blankStringOrNull/intOrNullAt/longOrNullAt/floatOrNullAt/throttledStateOrNull) mirroring BedMeshModel.from — runCatching/null-return, never !!"
    - "blank-as-missing (empty String '' -> null) + JsonNull-as-no-data discriminators encoded in the parser, not the UI"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoModels.kt
    - app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoParse.kt
    - app/src/main/java/works/mees/dinghy/systeminfo/HealthChip.kt
    - app/src/main/java/works/mees/dinghy/systeminfo/SysInfoFormat.kt
  modified:
    - app/src/test/java/works/mees/dinghy/systeminfo/SystemInfoParseTest.kt
    - app/src/test/java/works/mees/dinghy/systeminfo/ProcStatPushTest.kt
    - app/src/test/java/works/mees/dinghy/systeminfo/DegradeTest.kt
    - app/src/test/java/works/mees/dinghy/systeminfo/HealthChipTest.kt
    - app/src/test/java/works/mees/dinghy/systeminfo/SysInfoFormatTest.kt
decisions:
  - "ThrottledState lives in SystemInfoModels.kt (single home, imported by both parser + healthState) — plan said 'models is fine'"
  - "Data classes carry an empty `companion object` so the plan-named `SystemInfo.from`/`ProcStatLive.fromPush`/`ProcStatQuery.from` companion-extension call shape compiles"
  - "Local private JsonObject walk helpers (project helpers are all `private` in their own files) instead of exposing/sharing them — matches the per-file idiom"
metrics:
  duration_min: 8
  completed: 2026-06-08
  tasks: 2
  files: 9
  commits: 2
---

# Phase 20 Plan 02: System Information Wave 2 (pure parsers + health chip + formatters) Summary

All the System-Information correctness now lives in four pure, host-tested files — the identity/live/query models + their tolerant Moonraker parsers, the 3-state health-chip decision fn, and the value formatters — turning the five Plan-01 RED scaffolds GREEN against both-SBC live captures. This is the Nyquist core: Wave-3 plumbing + UI just wire these.

## What Was Built

- **`SystemInfoModels.kt`** — four all-nullable data classes: `SystemInfo` (model/cpuDesc/processor/cpuCount/totalMemoryKb/distroName/distroVersion/kernel), `ProcStatLive` (cpuTemp/cpuLoadPercent/mem used·total·available kB), `ProcStatQuery` (throttledState/cpuTemp/systemUptimeSeconds), and `ThrottledState` (bits/flags). The first three carry an empty `companion object` so the plan-named companion-extension parsers resolve.
- **`SystemInfoParse.kt`** — three tolerant `runCatching`/null-returning parsers mirroring `BedMeshModel.from`, never `!!`, never schema-strict `@Serializable`:
  - `SystemInfo.from(result)` reads `cpu_info.{...}` + `distribution.{...}`; **empty string "" → null** (RockPro64 blank `model` degrades, Pitfall 4); **kernel read from `distribution.kernel_version`** (Pitfall 3).
  - `ProcStatLive.fromPush(push)` reads `cpu_temp` + `system_cpu_usage.cpu` + `system_memory.{used,total,available}` and **tolerates the absence of `throttled_state`/`system_uptime`** (Pitfall 1 — the push genuinely omits both).
  - `ProcStatQuery.from(result)` reads `throttled_state` (object → `ThrottledState`; **JsonNull/absent → null, no crash** — Pitfall 2), `cpu_temp`, `system_uptime`.
- **`HealthChip.kt`** — `enum HealthState { Healthy, Warn, Caution }` + the total side-effect-free `healthState(throttledState, cpuTemp)`: throttle-authoritative when non-null (`bits & 0xF` → Caution; else `bits & 0xF0000` → Warn; else Healthy), else D-12 **LOCKED** temp fallback (`>=80` Caution / `>=70` Warn / else Healthy; null temp → Healthy, no crash).
- **`SysInfoFormat.kt`** — pure formatters: `formatGb` (kB→GB, 1dp), `formatMemoryUsedOverTotal` (auto-scale MB<1GB else GB), `formatUptime` (compact `2d 3h 14m` dropping leading-zero units), `formatCpuLoad` (int %), `formatTemp` (whole °C), `formatCores`. Every one returns `—` (em dash, the SYS-04 degrade contract) on null; all number formatting `Locale.US`.
- **Five tests** flipped from Assume-guarded RED to live assertions against the fixtures (all `assumeTrue` guards removed).

## Tasks Completed

| Task | Name | Commit | Files |
| ---- | ---- | ------ | ----- |
| 1 | Identity/live/query models + tolerant parsers | `25981e9` | SystemInfoModels.kt, SystemInfoParse.kt + 3 tests GREEN |
| 2 | Pure health-chip fn + value formatters | `4c1c902` | HealthChip.kt, SysInfoFormat.kt + 2 tests GREEN |

## Verification

- Task 1 scoped run (`*SystemInfoParse* *ProcStatPush* *Degrade*`) → BUILD SUCCESSFUL.
- Task 2 scoped run (`*HealthChip* *SysInfoFormat*`) → BUILD SUCCESSFUL after the formatter fix below.
- **Full `:app:testDebugUnitTest` → BUILD SUCCESSFUL, exit 0** (no regression across the whole suite).
- The three correctness traps are now encoded in passing tests: push omission tolerated (`pushParserDoesNotRequireThrottleOrUptime`), null throttle → temp fallback (`nullThrottledState_noCrash` + `e3NullThrottle_tempFallback_70warn_80caution`), empty model → null (`e3_emptyModelDegrades`). D-12 cutoffs (70/80) and the `2d 3h 14m` uptime format are locked by assertions.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `Companion` extension references didn't resolve**
- **Found during:** Task 1 first compile (`Unresolved reference 'Companion'` ×3).
- **Issue:** The plan names the parsers as `SystemInfo.from(...)` / `ProcStatLive.fromPush(...)` / `ProcStatQuery.from(...)` — companion-extension call shape — but plain `data class`es declare no companion object, so the `fun X.Companion.from(...)` extensions had no receiver to attach to.
- **Fix:** Added an empty `companion object` to `SystemInfo`, `ProcStatLive`, and `ProcStatQuery`. No behavior change; call shape now matches the RED scaffolds verbatim.
- **Files modified:** `SystemInfoModels.kt`
- **Commit:** `25981e9`

**2. [Rule 1 - Bug] Test expectation off-by-one on MB rounding (illustrative staging value)**
- **Found during:** Task 2 scoped run (`kbToMb` ComparisonFailure `744` vs `743`).
- **Issue:** The plan's staging example wrote "744 MB" for 761312 kB, but 761312 / 1024 = 743.47 → rounds to **743 MB**. The "744 MB" was an illustrative approximation ("style"), not a locked value.
- **Fix:** Corrected the test expectation to the mathematically-correct `743 MB`; left the formatter's standard `roundToLong` intact (it is correct). The locked GB values (7.6 / 3.8) and the uptime format were unaffected.
- **Files modified:** `SysInfoFormatTest.kt`
- **Commit:** `4c1c902`

## Known Stubs

None — all four files are fully-wired pure logic with no placeholder data paths.

## Threat Flags

None — pure logic, no I/O, no new network/auth/file surface. T-20-02-D (DoS on sparse/malformed payload) is mitigated by the tolerant parsers + the DegradeTest gate (missing keys, JsonNull throttle, blank model, and null inputs all proven non-throwing).

## Notes for Wave 3 (Plan 20-03)

The pure API the screen/plumbing must consume: `SystemInfo.from(result)`, `ProcStatLive.fromPush(push)`, `ProcStatQuery.from(result)`, `healthState(throttledState, cpuTemp)`, and the `formatGb`/`formatMemoryUsedOverTotal`/`formatUptime`/`formatCpuLoad`/`formatTemp`/`formatCores` formatters (all return `—` on null). Wave 3 still needs to route `notify_proc_stat_update` and (per RESEARCH Pitfall 5) update the T-11-04 notify golden, plus issue the one-shot `machine.proc_stats` query for throttle/uptime since the push omits them.

## Self-Check: PASSED

All 4 created + 5 modified files present on disk; both commits (`25981e9`, `4c1c902`) present in git history.
