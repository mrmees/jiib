---
phase: 20-system-information-page
plan: 04
subsystem: systeminfo
tags: [ui, icons, drawer-tile, screen, preview-first, shape-coded-status, on-device-uat, wave-4]
requires:
  - "SystemInfoHolder (identity / procStats / live StateFlows) + AppContainer.systemInfoHolder slot (Plan 20-03)"
  - "Pure SystemInfo / ProcStatLive / ProcStatQuery models + formatters + healthState fn (Plan 20-02)"
  - "Owner-locked D-01..D-10 icon slate verified resolvable in v2.944 (20-RESEARCH Icon Gate)"
provides:
  - "8 owner-locked System-Info DinghyIcons tokens (SysInfoTile/Host/Uptime/Cpu/Ram/Distro/Kernel/MemUsage; D-03/D-09 reuse thermostat/speed)"
  - "Dest.SystemInfo route enum value"
  - "Live drawer System Info tile (pulse_alert via SYSINFO_SYMBOL token) — no dead-tap window"
  - "SystemInformationScreen — read-only host-health page (Focus summary + Field Host/Live-load + Back gutter)"
  - "SystemInformationContent stateless seam + @Preview matrix (healthy/temp-fallback/degraded/caution)"
affects:
  - "AppShell when(dest) — new Dest.SystemInfo branch + swipe-suppress membership"
  - "Phase 20 closure: pending the on-device cross-SBC UAT (SYS-05 / SC-4)"
tech-stack:
  added: []
  patterns:
    - "Icon-led InfoRow: About's label:monospace-value row + a LEADING DinghyIconView slot (NOT widening About's private InfoRow)"
    - "Drawer-symbol-from-token (OUTPUT_SYMBOL precedent): SYSINFO_SYMBOL sources the pulse_alert literal off DinghyIcons.SysInfoTile"
    - "Stateless *Content seam driving a preview-first @Preview matrix (no live Moonraker)"
    - "Shape-coded health chip: StatusStop/warning/check_circle by HealthState (shape carries safety, color redundant)"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt
    - app/src/main/java/works/mees/dinghy/preview/SystemInfoPreviews.kt
    - .planning/phases/20-system-information-page/20-UAT.md
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/res/values/strings.xml
decisions:
  - "D-03 (CPU temp) reuses the existing LauncherTemperature (thermostat) token; D-09 (CPU load) reuses Speed (speed) — re-registering those ligatures would trip the iconRef uniqueness guard, so they are NOT re-added"
  - "The drawer tile flip (dest = Dest.SystemInfo) and the AppShell Dest.SystemInfo routing branch landed in the SAME commit (Task 1, 0cc461d) — no dead-tap window (Codex atomicity)"
  - "Q1 host-label fallback applied at the UI as identity.model ?: identity.distroName (the holder carries both; no display logic baked into the holder)"
  - "Health chip temp input prefers live.cpuTemp, falling back to procStats.cpuTemp (the 1 Hz push may not have landed yet)"
  - "SystemInfo joins the AppShell swipe-suppress set (drawer suppressed on-screen, staging-doc + About precedent); Back-only neutral gutter"
requirements: [SYS-01, SYS-02, SYS-03, SYS-04, SYS-05]
metrics:
  duration_min: 30
  completed: 2026-06-08
  tasks: 3
  files: 8
  commits: 2
---

# Phase 20 Plan 04: System Information Wave 4 (UI + on-device gate) Summary

The Wave-3 holder data is now surfaced as the staging-doc-locked read-only host-health page. The owner-locked
icon slate is bound, the greyed drawer stub is flipped to a live tile **atomically with its AppShell routing
branch** (no dead-tap window), and the screen renders the Focus health summary + Field grouped Host/Live-load
detail with the EXACT owner-locked D-02..D-10 glyphs and the shape-coded health chip. The cross-SBC on-device
UAT (SYS-05 / SC-4) is **scaffolded and the debug APK is installed on flox — it is the PENDING blocking
checkpoint awaiting Matthew's eyeball on both real SBCs.**

## What Was Built

- **Owner-locked icon slate (Task 1)** — 8 new `DinghyIcons` tokens bound to the owner-locked v2.944 ligatures:
  `SysInfoTile`→`pulse_alert` (D-01), `SysInfoHost`→`dns` (D-02), `SysInfoUptime`→`schedule` (D-04),
  `SysInfoCpu`→`developer_board` (D-05), `SysInfoRam`→`memory` (D-06), `SysInfoDistro`→`deployed_code` (D-07),
  `SysInfoKernel`→`code_blocks` (D-08), `SysInfoMemUsage`→`data_usage` (D-10). D-03 (CPU temp) **REUSES** the
  existing `LauncherTemperature` (thermostat) and D-09 (CPU load) **REUSES** `Speed` (speed) — NOT re-added
  (the `iconRef_isUnique` guard checks ligature reuse). All 8 appended to `DinghyIcons.all`; the drift guard
  (`DinghyIconsTest`, all 4 methods incl. iconRef-uniqueness + drawable-keepers) stayed GREEN with no test edit.
- **Route + drawer tile + AppShell branch, ATOMIC (Task 1)** — `Dest.SystemInfo` added to the enum; the
  drawer stub flipped from `dest = null` (greyed `memory`) to a live tile sourcing its glyph from
  `SYSINFO_SYMBOL` (= `DinghyIcons.SysInfoTile.primary` → `pulse_alert`, the `OUTPUT_SYMBOL` token precedent so
  the icon-law glyph can never drift from a typo'd literal); and a placeholder `Dest.SystemInfo` AppShell
  branch added **in the same commit** so the live tile is never live without a handler.
- **The read-only screen (Task 2)** — `SystemInformationScreen(holder, onBack)` + a stateless
  `SystemInformationContent(identity, procStats, live, onBack)` seam. `ScreenScaffold` with a `verticalScroll`
  Field and a Back-only **Neutral** gutter (drawer suppressed on-screen). A NEW icon-led `IconInfoRow` copies
  About's label:monospace-value structure + a leading `DinghyIconView` slot (About's private `InfoRow` is NOT
  widened). Layout in FIXED logical order: **Focus** host/model (Q1 `model ?: distroName`), CPU temp
  (`formatTemp`), the health chip, uptime (`formatUptime`); **Field Host** CPU model+cores, Total RAM
  (`formatGb`), distro name+version, kernel; **Field Live load** CPU % (`formatCpuLoad`), memory used/total
  (`formatMemoryUsedOverTotal`). Every value flows through the Wave-2 formatters → "—" on null with the row +
  icon STILL present (SYS-04 stable layout). The **health chip** renders `healthState(throttledState, temp)`
  as shape-coded glyphs (caution = `StatusStop` tinted `t.stop`; warn = `warning` triangle tinted `t.heat`;
  healthy = `CheckCircle` tinted `t.go`) — no new drawable.
- **Strings + preview matrix (Task 2)** — all labels + `cd_*` content descriptions as string resources (no
  hardcoded UI literals). `SystemInfoPreviews` drives the `SystemInformationContent` seam from pure sample
  data (NO live Moonraker): healthy RPi 4 across the 6-theme matrix + `fs = L`, temp-fallback RockPro64,
  degraded/sparse (all "—"), caution (active under-voltage bit → red StatusStop), plus a pseudolocale check.
- **AppShell wiring (Task 2)** — the `Dest.SystemInfo` placeholder branch body replaced with the real screen
  off `container.systemInfoHolder` (collected once; null while idle → degraded state); `Dest.SystemInfo`
  added to the swipe-suppress set so the global drawer is suppressed on this scrollable Field surface.
- **On-device scaffold (Task 3)** — debug APK built + `:app:installDebug` on flox (BUILD SUCCESSFUL on the
  real Adreno 320 / API 30 device); `20-UAT.md` scaffolded with one row per check (1-6) for the cross-SBC walk.

## Tasks Completed

| Task | Name | Commit | Files |
| ---- | ---- | ------ | ----- |
| 1 | Bind icon slate + Dest route + atomic drawer-tile-flip + AppShell branch | `0cc461d` | DinghyIcons.kt, TopRoute.kt, AppDrawer.kt, AppShell.kt |
| 2 | Build the System Information screen + previews + fill the AppShell branch | `7045f40` | SystemInformationScreen.kt, strings.xml, SystemInfoPreviews.kt, AppShell.kt |
| 3 | On-device cross-SBC UAT (SYS-05 / SC-4) | — (PENDING checkpoint) | 20-UAT.md (scaffolded; APK installed on flox) |

## Verification

- Task 1: `:app:testDebugUnitTest --tests *DinghyIcons*` → BUILD SUCCESSFUL (drift + uniqueness + drawable-keeper
  guards GREEN). Grep gate: `Dest.SystemInfo` present in BOTH AppDrawer.kt AND AppShell.kt + `SYSINFO_SYMBOL`
  in AppDrawer.kt → `TILE_AND_BRANCH_OK` (same-commit atomicity, no dead-tap window).
- Task 2: `:app:assembleDebug` → BUILD SUCCESSFUL. Grep: `SystemInformationScreen` present in AppShell.kt
  (`SHELL_WIRED_OK`); 9 owner-locked glyph-token references in the screen (SysInfoHost/Cpu/Ram/Distro/Kernel/
  MemUsage + LauncherTemperature + Speed + SysInfoUptime).
- Full `:app:testDebugUnitTest` → BUILD SUCCESSFUL (no regression).
- Task 3: `:app:installDebug` on flox → "Installed on 1 device" / BUILD SUCCESSFUL.

## Deviations from Plan

None — plan executed exactly as written. (D-03/D-09 token reuse, the same-commit atomicity, and the Q1
host-label fallback were all explicit plan instructions, not deviations.)

## Known Stubs

None. The screen reads real session-backed holder StateFlows; every row is wired to the Wave-2/Wave-3 data.
The PENDING item is the human on-device UAT gate (Task 3), not a code stub.

## Threat Flags

None beyond the plan's own `<threat_model>`. Read-only diagnostics render with no control surface, no new
network/auth path (rides the existing trusted-LAN session), no untrusted input accepted. T-20-04-D (sparse/
malicious payload) is mitigated: every value flows through the Wave-2 formatters → "—", rows stay present;
the degraded/caution preview states + the cross-SBC UAT exercise it. No package installs (T-20-04-SC).

## Pending Checkpoint (Task 3 — blocking human-verify, SYS-05 / SC-4)

The on-device cross-SBC UAT is the deferred blocking gate. The debug APK is installed on flox and `20-UAT.md`
is scaffolded (checks 1-6, both SBCs). **Awaiting Matthew's on-device walk** on the RPi 4 (E5, .120) and
RockPro64 (E3, .121) to confirm identity/live/health render and the health chip means the SAME on both. A FAIL
spawns a gap-closure plan; on "approved", record PASS per check and proceed to phase closure.

## Self-Check: PASSED

- Created files exist: `SystemInformationScreen.kt`, `SystemInfoPreviews.kt`, `20-UAT.md` — all present on disk.
- Commits present in git history: `0cc461d`, `7045f40`.
