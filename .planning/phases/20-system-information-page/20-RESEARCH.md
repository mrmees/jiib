# Phase 20: System Information Page — Research

**Researched:** 2026-06-08
**Domain:** Moonraker host-telemetry rendering (read-only) + Android Compose label:value page + websocket notify-channel routing
**Confidence:** HIGH (all field shapes captured LIVE from both real printers; push cadence measured on the wire; icon gate run against the actual bundled ttf)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Scope = host-health-only.** Versions (live in About), disk/storage, and network are OUT. ROADMAP SC-1 already rewritten (D-13). [CITED: 20-CONTEXT.md]
- **No control surface.** Nothing on this page dispatches a command; no Apply/ConfirmGuard. Read-only.
- **Active-printer scoped.** Re-resolves on the Phase-14 multi-printer switch. No side-by-side multi-host.
- **Reuse the central subscribe + the About `InfoRow` label:value primitive** — no new data path beyond routing the proc-stat notify channel.
- **Icon slate (OWNER-SELECTED — do NOT substitute; ASK if any is missing):**
  - D-01 drawer tile → `pulse_alert`
  - D-02 Focus hostname/host model → `dns`
  - D-03 Focus CPU temp → `thermostat`
  - D-04 Focus uptime → `schedule`
  - D-05 Host CPU model + cores → `developer_board`
  - D-06 Host total RAM → `memory`
  - D-07 Host distro name + version → `deployed_code`
  - D-08 Host kernel → `code_blocks`
  - D-09 Live CPU load % → `speed`
  - D-10 Live memory used/available → `data_usage`
  - D-11 verification gate: run `verify_ligatures.py` before wiring (DONE in this research — see Icon Gate).
  - Health chip needs no glyph — renders via the Phase-15.1 shape-coded status system.
- **D-12 health-chip temp-fallback cutoffs (LOCKED — do not change the numbers):**
  - `< 70 °C` → healthy (go / shapeless)
  - `≥ 70 °C` → warn (amber triangle)
  - `≥ 80 °C` → caution (red octagon/stop)
- **Value formatting (LOCKED by staging doc):** temp whole °C; load integer %; memory `used / total` auto-scaled; uptime compact `2d 3h 14m` dropping leading-zero units; cores integer; RAM in GB; uptime is HOST uptime (`proc_stats.system_uptime`), not Klipper/Moonraker.
- **Row order = fixed logical order** (as listed in the staging doc), NOT alphabetical.
- **Graceful degradation:** missing field → `—`, keep the labeled row present, never crash on sparse/older Moonraker.
- **Entry = top-level drawer tile** alongside Files/Macros/Console; suppress the swipe-up drawer on this screen; Gutter = Back only.

### Claude's Discretion
None on owner-facing choices. Remaining work is pure research (resolved below) + standard implementation patterning.

### Deferred Ideas (OUT OF SCOPE)
- Klipper/Moonraker versions (in About), disk/storage, network section, per-process/per-core breakdown, per-interface network detail, side-by-side multi-host.
</user_constraints>

---

<phase_requirements>
## Phase Requirements (SYS-* family — planner must DEFINE these)

CONTEXT did not enumerate SYS-* IDs and REQUIREMENTS.md only has a placeholder `SYS-01` line (137) + a "new SYS-* family — TBD at discuss" note (264). **The planner must coin the SYS-* family and add it to REQUIREMENTS.md** (this is itself a planner task). Proposed mapping to the 4 ROADMAP success criteria:

| Proposed ID | Description | Maps to SC | Research support |
|-------------|-------------|-----------|------------------|
| **SYS-01** | Page shows host **identity** (CPU model/cores, total RAM, distro name+version, kernel) from `machine.system_info` | SC-1 | Field map below — all confirmed on both hosts (model empty on RockPro64 → `—`) |
| **SYS-02** | Page shows **live load** (CPU %, memory used/available) + **CPU temp**, updating at a throttled cadence via the proc-stat push channel — NO dedicated poll loop | SC-1, SC-2 | Push channel measured @ 1 Hz; throttle to 1 Hz display (already ≥ store's 250 ms floor) |
| **SYS-03** | Page shows a **host-health summary** (hostname, CPU temp, throttle/health state via shape-coded chip, host uptime) | SC-1 | Throttle + uptime sourced from a one-shot `machine.proc_stats` query (NOT the push — see Cadence) |
| **SYS-04** | Missing/unsupported fields **degrade to `—`**; page never blocks/crashes on sparse or older Moonraker | SC-3 | Both hosts already exercise the sparse case (RockPro64 empty model/desc, null throttled_state) |
| **SYS-05** | Verified against **both** real printers (RPi 4 + RockPro64) — on-device UAT | SC-4 | On-device gate per Validation Architecture |

The planner should confirm the exact ID labels with the owner if a different convention is preferred, but the 5-row decomposition cleanly covers all 4 SC.
</phase_requirements>

---

## Summary

This phase is **low-risk and well-specified**. The staging doc + CONTEXT lock ~90% of it; this research resolves the open checklist against the two live printers and finds **three things the planner must get right**, none of which are design changes:

1. **The proc-stat PUSH channel omits `throttled_state` and `system_uptime`.** Confirmed in Moonraker docs AND empirically on both hosts: `notify_proc_stat_update` carries `cpu_temp`, `system_memory`, `system_cpu_usage`, `moonraker_stats`, `network`, `websocket_connections` — but **NOT** the throttle flags and **NOT** host uptime. Those two only exist in the `machine.proc_stats` JSON-RPC/REST **query** result. So the health chip's Pi-throttle authority and the uptime row **cannot ride the push** — they need a one-shot `machine.proc_stats` query, fetched edge-driven per handshake (and on screen-enter is acceptable for a once-per-edge identity-ish read, but the Phase-13-compliant shape is a handshake one-shot seed like `temperature_store`/`gcode_store`). This is the single most important finding.

2. **`notify_proc_stat_update` is currently DROPPED.** `JsonRpcClient.kt` explicitly routes it to the `else -> Unit` branch (with a comment that the live golden interleaves nine such frames that "MUST fall through untouched"). Phase 20 must add a new notify flow (a `SharedFlow<ProcStat>`-style emission) and a store/holder consumer. This is a real plumbing task, not "just read a field that's already there."

3. **The two CommandSpecs already exist as catalog reference rows** (`MR-machine.system_info`, `MR-machine.proc_stats` at catalog.json lines 6288 / 6056, both `runtime_registry.status: reference_only`, `availability: always`). The drift gate is satisfiable cheaply: flip them to registered + add a `command_availability` row each in printer-matrix.json. Because both are `availability: always`, **no per-host predicate evidence is needed** (the `predicateReferencesAreBackedByMatrixEvidence` test only checks non-`Always` predicates). See Drift Rows.

**Both hosts expose `cpu_temp` and `system_uptime` and `system_memory`** — so the RockPro64 temp-fallback (D-12) is fully supported, and uptime works on both. The RockPro64 returns `throttled_state: null` (an explicit null key, NOT an omitted key) — the chip's "throttle authoritative when reported" precedence keys off `throttled_state == null` → fall to temp.

**Primary recommendation:** Treat the page as two data planes — a **static identity plane** (one-shot `machine.system_info` per handshake) and a **live plane** split between the **1 Hz `notify_proc_stat_update` push** (cpu %, mem, temp) and a **one-shot `machine.proc_stats` query** (throttle flags + uptime, per handshake edge). Wire one new notify flow, two new registry specs (already cataloged), one new screen reusing `InfoRow` + the shape chip. All 10 icons resolve.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Host identity (CPU/RAM/distro/kernel) | App / state layer (one-shot query → holder StateFlow) | UI (InfoRow render) | Static-ish; fetched once per handshake like configfile/temperature_store |
| Live CPU% / mem / temp | App / state layer (new notify flow from socket) | UI (throttled collect) | Pushed @1 Hz by Moonraker; routes through the existing two-plane store discipline |
| Throttle flags + host uptime | App / state layer (one-shot `machine.proc_stats`) | UI (chip + uptime row) | NOT in the push payload — must be a query; edge-driven, never polled |
| Health-chip decision (throttle vs temp fallback) | App / pure logic (testable) | UI (shape-coded render) | Pure function of throttle-state + temp → 3 states; host-unit-testable |
| Drawer entry | UI shell (`AppDrawer`) | — | New live `DrawerTileSpec` with `pulse_alert` |
| Drift compliance | Build/docs (catalog.json + printer-matrix.json) | Test (`CommandCatalogDriftTest`) | Registering the specs requires matching JSON rows |

---

## Per-Host Captured JSON Ground Truth

> Captured LIVE 2026-06-08 via read-only REST. Both hosts HTTP 200. Trimmed to relevant keys.

### Ender 5 Plus — RPi 4 (`192.168.1.120:7125`)

**`GET /machine/system_info` → `result.system_info`:**
```json
{
  "cpu_info": {
    "cpu_count": 4,
    "bits": "64bit",
    "processor": "aarch64",
    "cpu_desc": "",                                  // EMPTY on Pi
    "model": "Raspberry Pi 4 Model B Rev 1.4",       // populated on Pi
    "total_memory": 8007452,
    "memory_units": "kB"
  },
  "distribution": {
    "name": "Debian GNU/Linux 12 (bookworm)",
    "id": "debian",
    "version": "12",
    "kernel_version": "6.12.87+rpt-rpi-v8"           // ← kernel lives UNDER distribution
  }
}
```

**`GET /machine/proc_stats` → `result`:**
```json
{
  "throttled_state": { "bits": 0, "flags": [] },     // ← OBJECT on Pi (clean here)
  "cpu_temp": 64.757,
  "system_uptime": 312773.755726872,                 // ← host uptime (≈3.6 days, matches a Pi up days)
  "system_memory": { "total": 8007452, "available": 7246140, "used": 761312 },  // kB
  "system_cpu_usage": { "cpu": 29.31, "cpu0": 33.68, "cpu1": 28.12, "cpu2": 23.23, "cpu3": 33.0 },
  "moonraker_stats": [ { "time": ..., "cpu_usage": 1.43, "memory": 70728, "mem_units": "kB" }, ... ]
}
```

### Ender 3 Pro — RockPro64 (`192.168.1.121:7125`)

**`GET /machine/system_info` → `result.system_info`:**
```json
{
  "cpu_info": {
    "cpu_count": 6,
    "bits": "64bit",
    "processor": "aarch64",
    "cpu_desc": "",                                  // EMPTY
    "model": "",                                      // ← EMPTY on RockPro64 (Pi-specific field) → render "—"
    "total_memory": 3945568,
    "memory_units": "kB"
  },
  "distribution": {
    "name": "Armbian 25.11.2 noble",
    "id": "ubuntu",
    "version": "24.04",
    "kernel_version": "6.18.10-current-rockchip64"
  }
}
```

**`GET /machine/proc_stats` → `result`:**
```json
{
  "throttled_state": null,                            // ← EXPLICIT NULL on RockPro64 (key present, value null)
  "cpu_temp": 53.333,                                 // ← present → temp-fallback works
  "system_uptime": 1178981.142290853,                 // host uptime (≈13.6 days)
  "system_memory": { "total": 3945568, "available": 2814280, "used": 1131288 },  // kB
  "system_cpu_usage": { "cpu": 33.33, "cpu0": 37.89, ... "cpu5": 23.47 },
  "moonraker_stats": [ ... ]
}
```

### Live WEBSOCKET push — `notify_proc_stat_update` (measured on BOTH hosts)

Captured by opening a raw `ws://<host>/websocket` (no identify, no subscribe) and timing frames:

- **Cadence: exactly 1 Hz** on both (E5 inter-frame deltas `[1.0, 1.001, 1.0, ...]`; E3 `[1.0, 1.002, 1.016, ...]`).
- **Pushed automatically to every connected websocket** — no subscription, no identify required.
- **Push `params[0]` top-level keys (BOTH hosts):** `cpu_temp`, `moonraker_stats`, `network`, `system_cpu_usage`, `system_memory`, `websocket_connections`.
- **OMITTED from the push (BOTH hosts):** `throttled_state` ❌, `system_uptime` ❌.

This omission is documented: Moonraker's jsonrpc_notifications page states *"throttled_state and system_uptime fields are omitted from the notification."* [CITED: moonraker.readthedocs.io/en/latest/external_api/jsonrpc_notifications/] and [VERIFIED: live websocket capture, both hosts, 2026-06-08].

---

## Field-Mapping Table

| Displayed value | Section | Source path | Live source | Format rule | Host omissions |
|-----------------|---------|-------------|-------------|-------------|----------------|
| Hostname / host model | Focus | `system_info.cpu_info.model` (fallback: distro `name` or `server.info.hostname`) | one-shot query | string | **RockPro64: model = `""` → `—`** (consider falling back to distro name for a non-empty host label — see Open Q1) |
| CPU temp | Focus + Live | `proc_stats.cpu_temp` | **PUSH (1 Hz)** | whole °C, e.g. `48°C` | none (both expose) |
| Health chip | Focus | `proc_stats.throttled_state` (Pi) / `proc_stats.cpu_temp` (fallback) | **one-shot query** (throttle) + push (temp) | shape-coded (3 states) | RockPro64: `throttled_state == null` → temp path |
| Uptime | Focus | `proc_stats.system_uptime` | **one-shot query** (NOT in push) | compact `2d 3h 14m` (drop leading-zero units) | none (both expose) |
| CPU model + cores | Host | `system_info.cpu_info.model` (or `cpu_desc`/`processor`) + `cpu_info.cpu_count` | one-shot query | `<model> · <n> cores`; cores integer | RockPro64 model empty → show `aarch64` (processor) or `—` + cores (6) |
| Total RAM | Host | `system_info.cpu_info.total_memory` (+ `memory_units` = "kB") | one-shot query | GB, e.g. `7.6 GB` (kB → GB ÷ 1024²) | none (E5 8007452 kB, E3 3945568 kB) |
| Distro name + version | Host | `system_info.distribution.name` (+ `.version`) | one-shot query | `name` already contains version; show `name` verbatim | none |
| Kernel | Host | `system_info.distribution.kernel_version` | one-shot query | string verbatim | none — **NOTE kernel is under `distribution`, not top-level** |
| CPU load % | Live | `proc_stats.system_cpu_usage.cpu` (aggregate) | **PUSH (1 Hz)** | integer %, e.g. `29%` | none |
| Memory used / available | Live | `proc_stats.system_memory.used` / `.available` (or `.total`) | **PUSH (1 Hz)** | `used / total` auto-scaled (kB → MB/GB) | none |

**Unit note:** every memory field across both endpoints is in **kB** (`memory_units` / `mem_units` = `"kB"`). Auto-scale: `kB → MB` ÷1024, `→ GB` ÷1024². Staging doc example `612 MB / 3.8 GB`. For "Total RAM" the staging locks GB.

**`cpu_desc` is empty on BOTH hosts** — do NOT rely on it for the CPU model label. Use `cpu_info.model` (good on Pi, empty on RockPro64) and fall back to `cpu_info.processor` (`aarch64`) or `—`.

---

## Health-Chip Logic Spec

Pure, host-unit-testable function: `(throttledState: ThrottledState?, cpuTemp: Float?) -> HealthState`.

### Precedence
```
if throttledState != null  -> use THROTTLE path (Pi authoritative)
else                       -> use TEMP-FALLBACK path (RockPro64 / non-Pi)
```
The discriminator is `throttled_state == null` (RockPro64) vs a non-null object (Pi). Confirmed: RockPro64 returns explicit `null`; Pi returns `{bits, flags}`. Never blend both.

### Pi throttle path — `throttled_state.bits` (vcgencmd bit semantics)
`bits` is an integer bitfield (same as `vcgencmd get_throttled`). **Low nibble = currently-active; high nibble (bits 16–19) = has-occurred-since-boot.** [CITED: Raspberry Pi `get_throttled` documentation; Moonraker proc_stats wraps it] [ASSUMED for exact bit positions — see Assumptions A1]:

| Bit | Meaning | Maps to chip |
|-----|---------|-------------|
| 0 (`0x1`) | Under-voltage **now** | **caution** (red) |
| 1 (`0x2`) | Arm frequency capped **now** | caution |
| 2 (`0x4`) | Currently **throttled** | caution |
| 3 (`0x8`) | Soft temperature limit **now** | caution |
| 16 (`0x10000`) | Under-voltage **has occurred** | **warn** (amber) |
| 17 (`0x20000`) | Arm-freq-cap **has occurred** | warn |
| 18 (`0x40000`) | Throttling **has occurred** | warn |
| 19 (`0x80000`) | Soft-temp-limit **has occurred** | warn |
| `bits == 0` & `flags == []` | clean | **healthy** |

Decision: `if (bits & 0xF) != 0 → caution; else if (bits & 0xF0000) != 0 → warn; else healthy`. Moonraker ALSO provides a `flags` string array (human-readable list of the set conditions) — the planner can map off `flags` instead of raw bits if preferred (e.g. flags containing `"Currently"` → caution, `"Previously"` → warn), but **the raw `bits` mask is the robust source** and `flags` exact strings should be confirmed on a printer that is actually throttled (our E5 is clean: `bits:0, flags:[]`, so the throttled `flags` string format is UNVERIFIED here — see Assumptions A2).

### Temp-fallback path (D-12 LOCKED cutoffs)
```
cpuTemp == null  -> healthy (or — if you prefer; chip should not crash on null temp)
cpuTemp >= 80    -> caution (red octagon / StatusStop)
cpuTemp >= 70    -> warn    (amber triangle)
else             -> healthy (go / shapeless)
```

### Shape-coded rendering (reuse Phase-15.1)
The status shapes already exist in `DinghyIcons`:
- **caution / red** → `StatusStop` = `IconRef.Ligature("disabled_by_default")` (square+✕ silhouette — the renamed former "octagon"; safety carried by distinct shape, D-14).
- **warn / amber** → `IconRef.Ligature("warning")` (triangle — already in the `verify_ligatures.py` NEEDED set, confirmed resolvable).
- **healthy** → shapeless / go (no shape glyph; or a `check`/`check_circle`). Staging says "go / shapeless."

Color is redundant to shape per THEMING (never color-alone). Confirm the exact existing healthy-state convention with the Phase-15.1 implementation when wiring.

---

## Cadence Resolution (Phase-13 compliant)

| Plane | Data | Channel | Native cadence | Display cadence | Mechanism |
|-------|------|---------|---------------|-----------------|-----------|
| Static identity | CPU/RAM/distro/kernel | `machine.system_info` JSON-RPC | once | once per handshake | one-shot seed, like `temperature_store`/`gcode_store`/configfile |
| Throttle + uptime | `throttled_state`, `system_uptime` | `machine.proc_stats` JSON-RPC | once | once per handshake (re-runs on reconnect / klippy_ready re-handshake); uptime is "fresh enough" — it advances ~1s/s and the user reads it at a glance | one-shot seed |
| Live load + temp | `cpu_temp`, `system_cpu_usage`, `system_memory` | **`notify_proc_stat_update` PUSH** | **1 Hz** (measured) | 1 Hz (the push rate; already ≥ the store's 250 ms `DEFAULT_SAMPLE_MS` floor, so NO extra throttle needed) | new notify flow → store → UI `collectAsStateWithLifecycle` |

**Why this is Phase-13 compliant:**
- The push channel is **NOT a poll** — Moonraker emits it automatically at 1 Hz to every connected socket. The app does zero extra wire traffic for the live plane (the frames already arrive and are currently discarded). This is strictly *less* chatty than any poll.
- The two one-shot queries are **edge-driven** (per handshake), the canonical compliant shape per the cadence contract Rule 3 (alongside `temperature_store`, `gcode_store`, configfile). Do NOT add a "refresh on page open" query — that is the exact anti-pattern Rule 3 forbids. If uptime/throttle freshness on long-running sessions matters, the re-handshake on reconnect already refreshes it.
- 1 Hz display cadence is well within the 250 ms store floor; no need to surface a faster cadence (Rule 4).

**Implementation note (the new plumbing):** `JsonRpcClient.handleNotification` currently has `else -> Unit` dropping `notify_proc_stat_update` (line ~214, with a comment that the T-11-04 golden expects nine such frames to fall through untouched — **that test will need updating** when you add a `NOTIFY_PROC_STAT_UPDATE` case). Add:
1. A `NOTIFY_PROC_STAT_UPDATE` constant in `JsonRpcMethods`.
2. A `_procStatUpdates` `MutableSharedFlow` + a `procStatUpdates` accessor, parsing `params[0]` into a `ProcStatLive(cpuTemp, systemCpuUsage.cpu, systemMemory)`.
3. A new case in the `when(method)` routing it.
4. A holder/store consumer (model after `LastJobHolder` / the proc-stat live plane) exposing a `StateFlow<ProcStatLive?>` the screen collects. Whether this lives in `PrinterStateStore` or a dedicated `SystemInfoHolder` is a planner call — a dedicated holder keeps the host-CPU telemetry off the printer-state hot path and is cleaner (it's not Klipper status).
5. Update the `T-11-04` notify-golden test expectation (was: proc-stat frames fall through untouched).

---

## Command-Catalog / Matrix Drift Rows (HARD GATE)

Both specs already exist as **reference-only catalog rows** — so `registryCatalogIdsExistInCatalogJson` already passes once they're in the registry. To register them in `CommandRegistry.all` without failing `CommandCatalogDriftTest`:

### 1. catalog.json — flip `runtime_registry` to registered (optional but correct)
At lines 6056–6083 (`MR-machine.proc_stats`) and 6288–6314 (`MR-machine.system_info`), change:
```json
"runtime_registry": {
  "status": "registered",
  "registered": true,
  "notes": "Registered runtime CommandSpec in CommandRegistry (Phase 20 System Information)."
}
```
(The drift test does not assert on `runtime_registry.status` value, but keeping it accurate prevents catalog rot. Both already have `availability: always` / `predicate: always` — leave those.)

### 2. printer-matrix.json — add two `command_availability` rows
The `registryCommandsHaveMatrixAvailabilityRows` test requires every registered `catalogId` to have a `command_availability` entry. Append to the `command_availability` array (starts line 1303), mirroring the `MR-server.gcode_store` shape (predicate `always` → no per-host evidence needed):

```json
{
  "catalog_id": "MR-machine.system_info",
  "name": "machine.system_info",
  "source_api": "moonraker",
  "transport": "json_rpc",
  "registry_status": "registered",
  "predicate": { "type": "always" },
  "predicate_key": "always",
  "printer_status": [
    { "printer_id": "ender5plus", "status": "present",
      "evidence": "machine/system_info probed 2026-06-08; returned cpu_info{model:'Raspberry Pi 4 Model B Rev 1.4',cpu_count:4,total_memory:8007452} + distribution{name,version,kernel_version}." },
    { "printer_id": "ender3", "status": "present",
      "evidence": "machine/system_info probed 2026-06-08; returned cpu_info{model:'',cpu_count:6,total_memory:3945568} + distribution{Armbian/ubuntu 24.04, kernel 6.18.10-current-rockchip64}." }
  ]
},
{
  "catalog_id": "MR-machine.proc_stats",
  "name": "machine.proc_stats",
  "source_api": "moonraker",
  "transport": "json_rpc",
  "registry_status": "registered",
  "predicate": { "type": "always" },
  "predicate_key": "always",
  "printer_status": [
    { "printer_id": "ender5plus", "status": "present",
      "evidence": "machine/proc_stats probed 2026-06-08; throttled_state{bits:0,flags:[]}, cpu_temp:64.757, system_uptime, system_memory, system_cpu_usage present." },
    { "printer_id": "ender3", "status": "present",
      "evidence": "machine/proc_stats probed 2026-06-08; throttled_state:null (RockPro64 omits throttle), cpu_temp:53.333, system_uptime, system_memory, system_cpu_usage present." }
  ]
}
```

**Why no `predicateReferencesAreBackedByMatrixEvidence` work is needed:** that test only iterates `leafPredicates()` filtered to **non-`Always`** predicates. Both specs are `Always`, so they contribute zero leaf predicates and need zero `objects`/`components` evidence rows. The only test that touches them is `registryCommandsHaveMatrixAvailabilityRows` (satisfied by the two `command_availability` rows above) and `registryCatalogIdsExistInCatalogJson` (already satisfied — rows exist).

**Pitfall:** `id` must equal `catalog_id` (test `catalogEntriesHavePlanRequiredShape` asserts this) — the existing catalog rows already satisfy this; don't break it.

---

## Icon Ligature Gate Results (D-11)

Ran the Phase-18.1 `tools/verify_ligatures.py` logic against the actual bundled font `app/src/main/res/font/material_symbols_outlined.ttf` — **Version 2.944** (confirmed via the `name` table). GSUB LigatureSubst traversal (LookupType 4, unwrapping Extension type 7):

| Glyph | D-ref | Resolves in v2.944? |
|-------|-------|---------------------|
| `pulse_alert` | D-01 (drawer tile — flagged as the riskiest/newest) | ✅ **OK** |
| `dns` | D-02 | ✅ OK |
| `thermostat` | D-03 | ✅ OK (already in registry as `LauncherTemperature`) |
| `schedule` | D-04 | ✅ OK |
| `developer_board` | D-05 | ✅ OK |
| `memory` | D-06 | ✅ OK |
| `deployed_code` | D-07 | ✅ OK |
| `code_blocks` | D-08 | ✅ OK |
| `speed` | D-09 | ✅ OK (already in registry NEEDED set) |
| `data_usage` | D-10 | ✅ OK |

**ALL 10 resolve. `pulse_alert` — the owner's specifically-flagged risk — IS present.** No owner consultation needed; no substitutes required. The planner wires exactly these.

Additionally the chip shapes are already present: `disabled_by_default` (StatusStop) and `warning` (triangle) are both in the existing NEEDED set and resolve. **Add the 10 new names to `verify_ligatures.py`'s `NEEDED` set** (Phase-20 block) so the drift-guard keeps protecting them.

---

## Don't Hand-Roll

| Problem | Don't build | Use instead | Why |
|---------|-------------|-------------|-----|
| Label:value row | A new row composable | About's `InfoRow` (add a leading icon slot) | CONTEXT mandates reuse; consistent type/tokens |
| Status shape | A custom octagon/triangle drawable | Phase-15.1 shape-coded `StatusStop`/`warning` ligatures | Owner law + already shipped, font-backed |
| Live polling | A `while(true){delay;query}` loop | The 1 Hz `notify_proc_stat_update` push (already arriving, currently dropped) | Phase-13 Rule 2 forbids per-screen polling; the push is free |
| kB→GB / uptime formatting | ad-hoc string math scattered in the composable | small pure formatter functions (host-unit-tested) | testable, fixture-driven, matches the captured units |
| An icon for any row | Picking/creating a glyph | The owner-locked D-01..D-10 slate | Icon law: NEVER substitute |

---

## Common Pitfalls

### Pitfall 1: Reading throttle/uptime off the push
**What goes wrong:** Chip never shows throttle state and uptime row stays `—`, because `notify_proc_stat_update` omits both. **Avoid:** source throttle + uptime from a one-shot `machine.proc_stats` query; only cpu%/mem/temp from the push. **Warning sign:** uptime shows `—` on a host that's clearly been up for days.

### Pitfall 2: `throttled_state` null vs absent
RockPro64 sends `throttled_state: null` (key present, value null) — Kotlin parsing must treat `JsonNull` as "no throttle data → temp fallback", not as a parse error. **Warning sign:** crash/`—` chip on the E3.

### Pitfall 3: Kernel field location
`kernel_version` is **under `distribution`**, not top-level `system_info`. Pulling `system_info.kernel_version` yields nothing. (`docs/moonraker-capabilities.md` should be extended with these shapes.)

### Pitfall 4: Empty-string fields ≠ null
`cpu_info.model` (RockPro64) and `cpu_info.cpu_desc` (both) are `""`, not null. Degrade-to-`—` logic must treat blank strings as missing, not render an empty value.

### Pitfall 5: Breaking the T-11-04 notify golden
Adding a `notify_proc_stat_update` case changes behavior the existing golden test asserts ("nine proc-stat frames fall through untouched"). Update that test in the same wave that adds routing, or the build goes red.

### Pitfall 6: Drift gate on registration
Registering the two specs without the matrix `command_availability` rows fails `registryCommandsHaveMatrixAvailabilityRows`. Add the rows in the same wave. ([[dinghy-command-catalog-drift]])

---

## Runtime State Inventory

Greenfield screen + additive plumbing — no rename/migration. Listed for completeness:
- **Stored data:** None — page is read-only, no new persistence (no DataStore writes). Verified: scope is display-only.
- **Live service config:** None.
- **OS-registered state:** None.
- **Secrets/env vars:** None.
- **Build artifacts:** None beyond normal compile.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 (host unit tests, `app/src/test`) + instrumented Compose (`app/src/androidTest`) where relevant; gfxinfo for perf (system-of-record on API 30 floor) |
| Config file | standard AGP test config; no extra setup |
| Quick run command | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests '*SystemInfo*' --no-daemon"` |
| Full suite command | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |

### Fixtures (capture-driven — the anti-mock-vs-reality discipline)
Commit the two live JSON blobs per host as test resources, mirroring the Phase-8 pattern (`fixtures/gcode_store_e5.json`):
- `app/src/test/resources/fixtures/system_info_e5.json` / `system_info_e3.json`
- `app/src/test/resources/fixtures/proc_stats_e5.json` / `proc_stats_e3.json` (E5 clean throttle, E3 null throttle)
- `app/src/test/resources/fixtures/notify_proc_stat_push_e5.json` (a captured push frame — proves the omission of throttle/uptime so a test asserts the parser doesn't expect them there)

### Phase Requirements → Test Map
| Req | Behavior | Test type | Command | Exists? |
|-----|----------|-----------|---------|---------|
| SYS-01 | system_info parses to identity model (model empty→`—`, kernel from distribution) | unit (both fixtures) | `--tests '*SystemInfoParse*'` | ❌ Wave 0 |
| SYS-02 | proc_stat PUSH parses cpu%/mem/temp; parser does NOT require throttle/uptime | unit (push fixture) | `--tests '*ProcStatPush*'` | ❌ Wave 0 |
| SYS-02 | live values route through new notify flow; throttled to ≤1 Hz | unit (notify routing) + update T-11-04 golden | `--tests '*JsonRpc*'` | ⚠ update existing |
| SYS-03 | health chip: E5 clean→healthy, synthetic throttle bits→warn/caution, E3 temp-fallback @70/80 | unit (pure fn, both + synthetic) | `--tests '*HealthChip*'` | ❌ Wave 0 |
| SYS-03 | uptime + kB→GB/MB formatters | unit | `--tests '*SysInfoFormat*'` | ❌ Wave 0 |
| SYS-04 | sparse/null fields → `—`, no crash (empty model, null throttled_state, missing keys) | unit | `--tests '*Degrade*'` | ❌ Wave 0 |
| SYS-05 | on-device: both printers show correct identity/live/health; chip reads identically | manual UAT (flox vs E5 + E3) | on-device | manual gate |

### Sampling Rate
- **Per task commit:** `--tests '*SystemInfo*'` quick run.
- **Per wave merge:** full `:app:testDebugUnitTest`.
- **Phase gate:** full suite green + on-device UAT against BOTH printers (SC-4) before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] Capture + commit the 5 fixture JSONs (above) — from the live captures in this research.
- [ ] RED scaffolds (must compile day-one per [[dinghy-wave0-red-scaffold-compile.md]]): SystemInfoParseTest, ProcStatPushTest, HealthChipTest, SysInfoFormatTest, DegradeTest.
- [ ] Note: instrumented drawer-open swipe is flaky on-device ([[dinghy-instrumented-swipe-threshold]]) — rely on manual flox UAT for SC-5, not an instrumented nav test.

---

## State of the Art

| Old approach | Current approach | Impact |
|--------------|------------------|--------|
| Poll `machine.proc_stats` on a timer | Consume the free 1 Hz `notify_proc_stat_update` push | Zero added wire traffic; the push already arrives and is discarded |
| Read everything from one endpoint | Split: push (live) + one-shot query (throttle+uptime, omitted from push) | Correctness — the push genuinely lacks two fields |

---

## Assumptions Log

| # | Claim | Section | Risk if wrong |
|---|-------|---------|---------------|
| A1 | Pi throttle bit positions: low nibble = active, bits 16–19 = occurred-since-boot | Health-Chip | LOW — standard vcgencmd semantics, stable for years; our E5 is clean (bits:0) so not directly exercised. Mitigate by ALSO mapping off Moonraker's `flags` string array. |
| A2 | The `flags` string-array contents on a *throttled* Pi | Health-Chip | LOW — E5 was clean (`flags:[]`) so the populated-flags format is unverified live. Use `bits` mask as primary; treat `flags` as supplementary. Could verify by stressing a Pi, not worth it. |
| A3 | Falling back the host label to distro name when `cpu_info.model` is empty (RockPro64) | Field-Map (Open Q1) | LOW — pure display choice; alternative is `—`. Owner-confirmable cheaply. |
| A4 | One-shot `machine.proc_stats` per handshake is "fresh enough" for uptime/throttle | Cadence | LOW — uptime self-advances at a glance; throttle is rare and re-read on reconnect. If owner wants live throttle, that's a future tweak. |

---

## Open Questions

1. **Host-label fallback when `cpu_info.model` is empty (RockPro64).** The Focus "hostname / host model" row has nothing model-ish on the RockPro64 (`model: ""`). Options: (a) show distro `name` ("Armbian 25.11.2 noble"), (b) show `processor` ("aarch64"), (c) show `—`. **Recommendation:** distro name is the most human-useful host label; it's never empty on either host. Planner should pick (a) unless owner objects — it's a pure display call, not a re-decision of locked design.

2. **Where the live proc-stat holder lives** — `PrinterStateStore` vs a dedicated `SystemInfoHolder`. **Recommendation:** dedicated holder (host CPU telemetry is not Klipper printer-state; keeps it off the printer hot path). Planner call.

---

## Environment Availability

| Dependency | Required by | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| RPi 4 Moonraker (E5) | SC-4 UAT + capture | ✅ | Moonraker v0.10.0 / API 1.5.0 | — |
| RockPro64 Moonraker (E3) | SC-4 UAT + capture | ✅ | Moonraker v0.10.0 / API 1.5.0 | — |
| fonttools (ligature gate) | D-11 | ✅ | 4.x (WSL python3) | — |
| flox (Nexus 7) | on-device UAT | (per build env) | LineageOS 18.1 / API 30 | manual eyeball gate |

No missing dependencies. Both printers reachable and captured this session.

---

## Sources

### Primary (HIGH)
- **Live REST capture** both hosts 2026-06-08: `/machine/system_info`, `/machine/proc_stats` — field shapes, units, null/empty cases. [VERIFIED]
- **Live websocket capture** both hosts 2026-06-08: `notify_proc_stat_update` @ 1 Hz, payload keys, throttle/uptime omission. [VERIFIED]
- `tools/verify_ligatures.py` logic against `material_symbols_outlined.ttf` v2.944 — all 10 glyphs resolve. [VERIFIED]
- `app/src/test/.../CommandCatalogDriftTest.kt` — exact drift-gate assertions. [VERIFIED: codebase]
- `app/src/main/.../net/JsonRpcClient.kt` — `notify_proc_stat_update` currently dropped at `else`. [VERIFIED: codebase]
- `docs/commands/catalog.json` lines 6056/6288 — both specs exist as reference rows. [VERIFIED: codebase]
- `docs/request-cadence-contract.md` — Phase-13 rules (no poll, edge-driven one-shots, 250 ms floor). [VERIFIED: codebase]
- `AboutScreen.kt` `InfoRow`, `DinghyIcons.kt` (StatusStop/warning shapes). [VERIFIED: codebase]

### Secondary (MEDIUM-HIGH)
- moonraker.readthedocs.io jsonrpc_notifications — confirms throttled_state + system_uptime omitted from the push. [CITED]
- moonraker.readthedocs.io external_api/machine — proc_stats/system_info reference. [CITED]

### Tertiary (LOW — flagged in Assumptions)
- vcgencmd throttle bit positions (training knowledge, standard). [ASSUMED — A1/A2]

---

## Metadata

**Confidence breakdown:**
- Field shapes / units: HIGH — captured live from both hosts.
- Cadence / push channel: HIGH — measured 1 Hz on the wire + docs confirm omission.
- Icon gate: HIGH — run against the actual ttf.
- Drift rows: HIGH — read the exact test assertions.
- Throttle bit positions: MEDIUM — standard semantics, our clean Pi didn't exercise the set-bit case (A1/A2).

**Research date:** 2026-06-08
**Valid until:** ~30 days (stable; re-capture only if a printer is reflashed or Moonraker majorly upgraded).

## RESEARCH COMPLETE
