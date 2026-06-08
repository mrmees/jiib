---
phase: 20-system-information-page
plan: 03
subsystem: systeminfo
tags: [spine, notify-route, command-catalog-drift, handshake-seeds, holder, moonraker, wave-3]
requires:
  - "SystemInfo / ProcStatLive / ProcStatQuery models + tolerant parsers (Plan 20-02)"
  - "machine.system_info + machine.proc_stats reference-only catalog rows (already present)"
provides:
  - "JsonRpcClient.procStatUpdates SharedFlow — the routed 1 Hz host-telemetry push (was dropped)"
  - "machineSystemInfo + machineProcStats registered CommandSpecs (drift gate GREEN)"
  - "PrinterStateStore.systemInfo + procStatQuery one-shot seam StateFlows (off the hot path)"
  - "SpineHandle.systemInfo + procStatQuery forwarded off the store"
  - "SystemInfoHolder — dedicated read-only host-telemetry holder (identity / procStats / live)"
  - "AppContainer.systemInfoHolder per-session slot the screen collects off"
affects:
  - "Wave-4 (Plan 20-04): the System Information SCREEN collects identity/procStats/live off the holder"
tech-stack:
  added: []
  patterns:
    - "Thin-router notify case: params[0] JsonObject -> bounded MutableSharedFlow.tryEmit (mirrors spoolNotifyParam)"
    - "Edge-driven one-shot handshake seed in runHandshake step 7 (best-effort runCatching, NO poll loop) — mirrors temperature_store/gcode_store/configfile"
    - "Dedicated per-session holder off the SpineHandle + JsonRpcClient push flow (OutputsHolder shape, READ-ONLY — no markPending/reached/timeout)"
    - "Per-session holder published on its own AppContainer slot (procStatUpdates dependency not carried on the SpineHandle data class)"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoHolder.kt
    - app/src/test/java/works/mees/dinghy/systeminfo/ProcStatRouteTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
    - app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt
    - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
    - docs/commands/catalog.json
    - docs/commands/printer-matrix.json
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt
    - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
    - app/src/test/java/works/mees/dinghy/spool/SpoolmanNotifyRouterTest.kt
    - app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt
decisions:
  - "Q1 RESOLVED: host-label fallback (cpu_info.model blank -> distribution.name) is a Plan-04 DISPLAY choice (model ?: distroName); the holder carries BOTH fields so the screen never reaches past it"
  - "Q2 RESOLVED: a dedicated SystemInfoHolder off the printer hot path, NOT PrinterStateStore — host CPU telemetry is not Klipper printer-state"
  - "SystemInfoHolder is published on its own AppContainer slot (publishSystemInfoHolder), NOT carried on the SpineHandle data class — its procStatUpdates dependency lives on the JsonRpcClient, not the store"
  - "The two new SpineHandle fields default to null-seeded StateFlows so the three headless SpineHandle test sites build unchanged"
requirements: [SYS-01, SYS-02, SYS-03]
metrics:
  duration_min: 35
  completed: 2026-06-08
  tasks: 3
  files: 14
  commits: 3
---

# Phase 20 Plan 03: System Information Wave 3 (spine plumbing) Summary

Both data planes the research mandates are now wired off the existing Moonraker session with zero added wire traffic: the **live plane** (the free ~1 Hz `notify_proc_stat_update` push, previously dropped at the dispatch `else`, now routed to `JsonRpcClient.procStatUpdates`) and the **static/query plane** (identity from `machine.system_info` + throttle/uptime from `machine.proc_stats`, both edge-seeded once per handshake — the push omits throttle+uptime). Everything surfaces through a dedicated read-only `SystemInfoHolder` off the printer hot path; the screen (Plan 04) just collects it. Both coupled-edit landmines were handled in this single wave (notify route + the T-11-04 golden in Task 1; spec registration + catalog flip + matrix rows in Task 2).

## What Was Built

- **Notify route (Task 1)** — `NOTIFY_PROC_STAT_UPDATE` constant + a bounded `_procStatUpdates: MutableSharedFlow<JsonObject>` (extraBufferCapacity 16, mirroring the spoolman buffer) + a `procStatUpdates` accessor + a `NOTIFY_PROC_STAT_UPDATE ->` case emitting `procStatParam(obj)` (the params[0] extractor cloned from `spoolNotifyParam`). The frames that used to fall through `else -> Unit` now reach the new flow. The stale source comment ("nine" proc-stat frames) was corrected to "ten" to match `SpoolmanNotifyRouterTest.kt:109`'s asserted count.
- **T-11-04 golden update (Task 1)** — the old `ignoresUnrelatedProcStatNotifications` became `routesProcStatToProcStatUpdatesAndNeverToActiveSpool`: it now asserts (a) all ten golden proc-stat frames reach `procStatUpdates` carrying `cpu_temp`, AND (b) they STILL never reach `activeSpoolSet`. `routesActiveSpoolSetFromOneElementParamsArray` is untouched (still yields `[3, 5]`). New `ProcStatRouteTest` wraps the live-captured push body as a notify envelope, dispatches it through a real `JsonRpcClient`, and asserts the frame surfaces on `procStatUpdates` + `ProcStatLive.fromPush` parses it.
- **Two registered query specs through the drift gate (Task 2)** — `machineSystemInfo` + `machineProcStats` CommandSpecs (no-args, `Always` availability — every Moonraker host exposes `machine.*` unconditionally) added to `CommandRegistry.all`. `catalog.json`: both reference-only rows flipped to `runtime_registry.status: "registered"` / `registered: true` (surgically, 6 lines, leaving `id`/`catalog_id` and `availability:always`/`predicate:always` intact). `printer-matrix.json`: two `command_availability` rows (predicate `always`, ender5plus + ender3 both `present` with the 2026-06-08 probe evidence). Because both predicates are `Always`, no `objects`/`components` evidence rows are needed.
- **Edge-driven handshake seeds (Task 3)** — `runHandshake` step 7 gained two best-effort `runCatching` one-shots (`machine.system_info` -> `SystemInfo.from(result)` -> `store.setSystemInfo`; `machine.proc_stats` -> `ProcStatQuery.from(result)` -> `store.setProcStatQuery`), placed after `temperature_store` and before `gcode_store`. They run inside `runHandshake` so they inherit the reconnect AND `notify_klippy_ready` reruns (Phase-13 Rule 3 — NO page-open query, NO poll loop). A host lacking either endpoint leaves the seam at null and the page degrades to "—".
- **State seam + spine forwarding (Task 3)** — `PrinterStateStore.systemInfo` / `procStatQuery` StateFlows + setters (seeded once per handshake, NOT the throttled hot path); `SpineHandle.systemInfo` / `procStatQuery` forwarded straight off the store.
- **SystemInfoHolder (Task 3)** — a dedicated plain-Kotlin (no Compose) holder in `OutputsHolder` shape but READ-ONLY (no markPending/reached/timeout). Exposes `identity: StateFlow<SystemInfo?>` (off the handle's `systemInfo`), `procStats: StateFlow<ProcStatQuery?>` (off `procStatQuery` — carries throttle+uptime), and `live: StateFlow<ProcStatLive?>` (a `scope.launch` collect on `procStatUpdates` -> `ProcStatLive.fromPush`). The 1 Hz push is already >= the store's 250 ms floor, so no extra throttle.
- **AppContainer / service wiring (Task 3)** — a per-session `systemInfoHolder` slot + `publishSystemInfoHolder`; the service constructs the holder from the just-published handle + `rpc.procStatUpdates`, publishes it alongside the spine, and clears it (`null`) on the idle path.

## Tasks Completed

| Task | Name | Commit | Files |
| ---- | ---- | ------ | ----- |
| 1 | Route notify_proc_stat_update + update the T-11-04 golden | `8e86828` | JsonRpc.kt, JsonRpcClient.kt, SpoolmanNotifyRouterTest.kt, ProcStatRouteTest.kt |
| 2 | Register the two query specs through the drift gate | `643ec95` | CommandRegistry.kt, catalog.json, printer-matrix.json |
| 3 | One-shot handshake seeds + SystemInfoHolder + spine wiring | `ccf751b` | MoonrakerSession.kt, PrinterStateStore.kt, SpineHandle.kt, SystemInfoHolder.kt, AppContainer.kt, MoonrakerService.kt, HandshakeTest.kt |

## Verification

- Task 1 scoped (`*SpoolmanNotifyRouter* *ProcStatRoute*`) → BUILD SUCCESSFUL.
- Task 2 scoped (`*CommandCatalogDrift*`) → BUILD SUCCESSFUL (D-10 drift gate GREEN).
- Task 2 explicit catalog acceptance: `jq`-equivalent Python check confirmed BOTH `MR-machine.system_info` and `MR-machine.proc_stats` rows have `runtime_registry.registered == true` (length==2, all true) — a separately-checkable assertion NOT covered by the drift test.
- Full `:app:testDebugUnitTest` → **BUILD SUCCESSFUL, 874 tests** (no regression).
- `:app:assembleDebug` + `:app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL (only pre-existing `createComposeRule` deprecation warnings, out of scope).
- Grep proofs: `NOTIFY_PROC_STAT_UPDATE ->` present once in JsonRpcClient; zero "nine" occurrences remain.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] HandshakeTest ordered-method golden out of date**
- **Found during:** Task 3 first full-suite run (`HandshakeTest.handshakeRunsInOrder_onceEach_andSeedsState` FAILED).
- **Issue:** The test pins the EXACT ordered list of handshake JSON-RPC methods. The two new in-handshake reads (`machine.system_info`, `machine.proc_stats`) appear after `TEMPERATURE_STORE` and before `GCODE_STORE`, so the asserted sequence no longer matched.
- **Fix:** Added the two methods to the expected list in their actual emission position (the harness's `else` branch already answers unknown methods with `{result:{}}`, so the best-effort reads complete without hanging). The fix reflects the real, correct handshake order this plan introduces — it is the golden catching the intended new behavior, not a workaround.
- **Files modified:** `app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt`
- **Commit:** `ccf751b`

## Resolved Open Questions

- **Q1 (host-label fallback):** RESOLVED as a Plan-04 DISPLAY choice. When `cpu_info.model` is blank (the RockPro64 case), the host label should fall back to `distribution.name`. The model already nulls a blank `model` and carries `distroName`; the holder exposes the full `SystemInfo`, so Plan 04 applies `identity.model ?: identity.distroName` at the row. No display logic was baked into the holder (keeps the holder a thin seam).
- **Q2 (holder home):** RESOLVED as a dedicated `SystemInfoHolder` off the printer hot path, NOT `PrinterStateStore`. Host CPU telemetry is not Klipper printer-state.

## Known Stubs

None — all flows are fully wired (route -> holder -> AppContainer slot). The UI consumer arrives in Plan 04; the holder exposes real session-backed StateFlows today.

## Threat Flags

None beyond the plan's own `<threat_model>` (additive read-only plumbing on the existing trusted-LAN session). T-20-03-D mitigated: each new handshake read is its own best-effort `runCatching` after subscribe, so an absent endpoint leaves the seam null and the page degrades — never breaks Connected. No new dependency, network path, or auth surface; no package installs.

## Notes for Wave 4 (Plan 20-04)

The screen obtains the holder off `AppContainer.systemInfoHolder` (a `StateFlow<SystemInfoHolder?>`, null when idle). Collect `holder.identity` (SystemInfo), `holder.procStats` (ProcStatQuery — throttle+uptime), and `holder.live` (ProcStatLive — cpu%/mem/temp). Apply the Q1 host-label fallback (`model ?: distroName`) and the Plan-02 formatters/healthState at the row. All three flows are null until the first read/frame lands and degrade to "—" (SYS-04) via the existing formatters.

## Self-Check: PASSED

- Created files exist: `SystemInfoHolder.kt`, `ProcStatRouteTest.kt` — both present on disk.
- Commits present in git history: `8e86828`, `643ec95`, `ccf751b`.
