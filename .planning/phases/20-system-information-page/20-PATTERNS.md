# Phase 20: System Information Page - Pattern Map

**Mapped:** 2026-06-08
**Files analyzed:** 11 new/modified

This page is **read-only, additive, and reuse-first** — there is no greenfield architecture. Every
file below copies an existing, shipped pattern. The planner can write grounded `<read_first>` and
`<action>` fields directly from the excerpts here.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `ui/systeminfo/SystemInformationScreen.kt` (new) | screen/component | request-response (one-shot) + streaming (1 Hz push) | `ui/screen/AboutScreen.kt` (`InfoRow` + ScreenScaffold Focus/Field) | role+flow match (iconless → add icon slot) |
| `net/JsonRpcClient.kt` (modify) | service / socket-router | event-driven (pub-sub) | itself — the `notify_active_spool_set` route added in D-10 | exact (same file, same pattern) |
| `net/JsonRpc.kt` `JsonRpcMethods` (modify) | config/constants | — | `NOTIFY_ACTIVE_SPOOL_SET` const | exact |
| `systeminfo/SystemInfoHolder.kt` (new) | state holder | streaming → StateFlow | `outputs/OutputsHolder.kt` (host-CPU telemetry ≠ Klipper state, same as Outputs being VM-shaped) | role match (dedicated non-printer-state holder) |
| `ui/shell/AppDrawer.kt` (modify) | UI shell | static registration | itself — `DRAWER_TILES` (the existing greyed `System Info` stub at line 239) | exact |
| `designsystem/icons/DinghyIcons.kt` (modify) | config/registry | — | the Phase-19 `OutputHeater`..`OutputSection` block (D-01..D-07 owner-locked ligatures) | exact |
| health-chip pure fn + render (new, in `systeminfo/`) | utility (pure) + component | transform → shape | `DinghyIcons.StatusStop` + `PrintStatusScreen.StopButton` shape-coded render | role match |
| `command/CommandRegistry.kt` (modify) | service/registry | request-response | `serverInfo` CommandSpec (no-args query, `availability` default Always) | exact |
| `docs/commands/catalog.json` (modify) | config | — | existing `MR-machine.system_info`/`MR-machine.proc_stats` reference rows (flip to registered) | exact |
| `docs/commands/printer-matrix.json` (modify) | config | — | `MR-server.gcode_store` `command_availability` row (predicate `always`) | exact |
| `app/src/test/.../SystemInfo*Test.kt` + fixtures (new) | test | request-response | `calibration/BedMeshModelTest.kt` (`/fixtures/*.json` resource load+assert) | exact |

---

## Pattern Assignments

### `ui/systeminfo/SystemInformationScreen.kt` (screen, one-shot + push)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/screen/AboutScreen.kt`

Reuse the **ScreenScaffold Field+Gutter** shell, the **`InfoRow` label:monospace-value primitive**,
the `fsSp(baseSp, t.fs)` font scale, and the `LocalTokens` color discipline. The new screen extends
`InfoRow` with a **leading Material-Symbol icon slot** (About's `InfoRow` is iconless — that is the
sole structural delta).

**Scaffold + Back-only gutter + suppressed drawer** (AboutScreen.kt lines 78-131):
```kotlin
Box(modifier.fillMaxSize()) {
    ScreenScaffold(
        field = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) { /* SectionHeader + InfoRows */ }
        },
        gutter = {
            OutlinedControl(
                label = "Back", onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                intent = Intent.Neutral,   // plain nav spends no safety color
                symbol = "arrow_back",
            )
        },
    )
}
```

**`InfoRow` primitive to copy — ADD a leading icon param** (AboutScreen.kt lines 218-240, currently `private`):
```kotlin
/** A label : monospace-value info row (version/build). */
@Composable
private fun InfoRow(label: String, value: String) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = t.text, fontFamily = Geist, fontSize = fsSp(17f, t.fs).sp)
        Text(text = value, color = t.text2, fontFamily = GeistMono, fontSize = fsSp(15f, t.fs).sp)
    }
}
```
> **Planner note:** About's `InfoRow` is `private`. The Phase-20 row is a NEW composable in
> `ui/systeminfo/` (not a refactor of About's) — copy the structure, add a leading
> `DinghyIconView(...)` / `MaterialSymbol(...)` slot before the label. Do NOT widen About's
> `private` `InfoRow` (the staging doc calls it `LabelValueRow`; the real symbol is `InfoRow`).

**Degrade-to-`—`** (CONTEXT/RESEARCH locked): a missing/blank field renders value `—`, the labeled
row stays present (stable layout across both SBCs). Treat empty-string (`cpu_info.model == ""`,
`cpu_desc == ""`) as missing, not as a rendered empty value (RESEARCH Pitfall 4).

**Font floor** ([[dinghy-font-sizes-too-small]]): 15sp metadata floor, 17-18 body, 20-22 titles —
About already obeys this via `fsSp`; copy verbatim.

---

### `net/JsonRpcClient.kt` (modify — add `notify_proc_stat_update` route)

**Analog:** the `notify_active_spool_set` / `notify_spoolman_status_changed` routes **in the same file**.

The proc-stat frame currently hits `else -> Unit` and is intentionally dropped (JsonRpcClient.kt
lines 214-217). Add a new SharedFlow + accessor + `when` case mirroring the spool route exactly.

**SharedFlow + accessor pattern** (JsonRpcClient.kt lines 76-90):
```kotlin
private val _activeSpoolSet = MutableSharedFlow<JsonObject>(extraBufferCapacity = SPOOLMAN_BUFFER)
val activeSpoolSet: SharedFlow<JsonObject> = _activeSpoolSet.asSharedFlow()
```
→ add `_procStatUpdates` / `procStatUpdates` the same way (small bounded buffer — the push is 1 Hz,
a tiny buffer is ample; mirror `SPOOLMAN_BUFFER = 16`).

**The `else` branch to REPLACE with a case** (JsonRpcClient.kt lines 196-218):
```kotlin
when (method) {
    JsonRpcMethods.NOTIFY_STATUS_UPDATE -> { statusDiff(obj)?.let { _statusUpdates.tryEmit(it) } }
    JsonRpcMethods.NOTIFY_GCODE_RESPONSE -> { gcodeLine(obj)?.let { _gcodeResponses.tryEmit(it) } }
    /* klippy events ... */
    JsonRpcMethods.NOTIFY_ACTIVE_SPOOL_SET -> { spoolNotifyParam(obj)?.let { _activeSpoolSet.tryEmit(it) } }
    JsonRpcMethods.NOTIFY_SPOOLMAN_STATUS_CHANGED -> { spoolNotifyParam(obj)?.let { _spoolmanStatusChanged.tryEmit(it) } }
    // ... the live notify golden interleaves ten `notify_proc_stat_update` frames ...
    // they MUST fall through this `else` untouched (T-11-04).
    else -> Unit
}
```
→ add `JsonRpcMethods.NOTIFY_PROC_STAT_UPDATE -> { procStatParam(obj)?.let { _procStatUpdates.tryEmit(it) } }`.

> **⚠ Stale source comment** (JsonRpcClient.kt ~line 215): the existing `else`-branch comment near the
> proc-stat fall-through reads "nine" frames, but `SpoolmanNotifyRouterTest.kt:109` actually asserts
> **ten** proc-stat frames. When 20-03 T-11-04 edits this route, correct that source comment to "ten"
> so the code matches the test's real assertion count (an executor must not trust the stale comment).

**`params[0]` extraction helper** — copy `spoolNotifyParam` (JsonRpcClient.kt lines 255-257), which
already pulls element [0] of the params array null-safely:
```kotlin
private fun spoolNotifyParam(obj: JsonObject): JsonObject? = runCatching {
    obj["params"]?.jsonArray?.firstOrNull()?.jsonObject
}.getOrNull()
```
The proc-stat push `params[0]` is an object with keys `cpu_temp`, `system_cpu_usage`,
`system_memory`, `moonraker_stats`, `network`, `websocket_connections`. Parse `cpu_temp` +
`system_cpu_usage.cpu` + `system_memory` only; the parser MUST NOT require `throttled_state` /
`system_uptime` (RESEARCH: they are OMITTED from the push — Pitfall 1).

> **⚠ T-11-04 GOLDEN BREAKS** (RESEARCH Pitfall 5): adding this case changes the behavior asserted by
> `app/src/test/java/works/mees/dinghy/spool/SpoolmanNotifyRouterTest.kt`
> (`ignoresUnrelatedProcStatNotifications`, lines 95-116) which asserts the ten proc-stat frames are
> ignored via `else -> Unit`. Update that test in the SAME wave that adds routing, or the build goes red.

---

### `net/JsonRpc.kt` `JsonRpcMethods` (modify — add the method constant)

**Analog:** `NOTIFY_ACTIVE_SPOOL_SET` (JsonRpc.kt lines 148-158):
```kotlin
const val NOTIFY_STATUS_UPDATE = "notify_status_update"
const val NOTIFY_GCODE_RESPONSE = "notify_gcode_response"
const val NOTIFY_ACTIVE_SPOOL_SET = "notify_active_spool_set"
const val NOTIFY_SPOOLMAN_STATUS_CHANGED = "notify_spoolman_status_changed"
```
→ add `const val NOTIFY_PROC_STAT_UPDATE = "notify_proc_stat_update"`.

---

### `systeminfo/SystemInfoHolder.kt` (new state holder)

**Analog:** `app/src/main/java/works/mees/dinghy/outputs/OutputsHolder.kt`

Host-CPU telemetry is NOT Klipper printer-state, so it belongs in a **dedicated holder** (RESEARCH
Open-Q2 recommendation) keeping it off the printer hot path — exactly the posture of `OutputsHolder`
(plain Kotlin, host-unit-testable, no Compose annotations, ADR-0001).

**Holder skeleton — `MutableStateFlow` + `asStateFlow` + scope-launched collect** (OutputsHolder.kt lines 76-121):
```kotlin
class OutputsHolder(
    private val scope: CoroutineScope,
    private val store: PrinterStateStore,
) {
    private val _rows = MutableStateFlow<List<OutputRowVm>>(emptyList())
    val rows: StateFlow<List<OutputRowVm>> = _rows.asStateFlow()

    init {
        scope.launch {
            combine(store.outputDescriptors, store.printerState, _pending) { ... }
                .collect { _rows.value = it }
        }
    }
}
```
→ The SystemInfo holder is SIMPLER (read-only, no busy/pending machinery): expose
`val live: StateFlow<ProcStatLive?>` (collects `JsonRpcClient.procStatUpdates`),
`val identity: StateFlow<SystemInfo?>` (seeded once from the `machine.system_info` one-shot), and
`val procStats: StateFlow<ProcStatQuery?>` (seeded from the `machine.proc_stats` one-shot — carries
throttle + uptime, the two fields the push omits). Drop the entire `markPending`/`reached`/timeout
section — there is no command dispatch here.

> **Two-plane discipline** (RESEARCH Cadence): identity + throttle/uptime = **one-shot seeds per
> handshake** (model after `temperature_store`/`gcode_store`/configfile seeds — edge-driven, NEVER a
> poll loop, per the Phase-13 contract). Live cpu%/mem/temp = the **1 Hz push flow** above. Do NOT
> add a "refresh on page open" query (Phase-13 Rule 3 anti-pattern).

---

### `ui/shell/AppDrawer.kt` (modify — promote the greyed stub to a live tile)

**Analog:** the `DRAWER_TILES` list **in the same file** (AppDrawer.kt lines 182-241).

There is ALREADY a greyed forward-stub at line 239 — flip it from `dest = null` (greyed) to a live
`Dest`, and swap the placeholder `memory` symbol to the **owner-locked D-01 `pulse_alert`**.

**Current stub** (AppDrawer.kt line 239):
```kotlin
DrawerTileSpec(label = "System Info", symbol = "memory", dest = null),
```
**Live-tile pattern to copy** (e.g. Macros/Console, AppDrawer.kt lines 188-191):
```kotlin
DrawerTileSpec(label = "Macros", symbol = "code", dest = Dest.Macros),
DrawerTileSpec(label = "Console", symbol = "terminal", dest = Dest.Console),
```
→ `DrawerTileSpec(label = "System Info", symbol = "pulse_alert", dest = Dest.SystemInfo)`
(add the `Dest.SystemInfo` route enum). No runtime gate (always shown — host always exists, unlike
the capability-gated Webcam/Spool tiles). `pulse_alert` is unique among `DRAWER_TILES` glyphs
(icon-no-repeat law — the old stub's `memory` is reused as the D-06 per-ROW glyph, which is fine —
the no-repeat law is per-surface; verify the drawer set has no other `pulse_alert`).

> **⚠ No dead-tap window** (Codex review): the drawer tile must NEVER go live in a commit that lacks a
> matching AppShell `when(dest)` routing branch — otherwise tapping System Info routes nowhere. The
> stub-flip and the AppShell `Dest.SystemInfo` branch must land in the SAME commit (20-04 collapses
> them into one task). See 20-04 Task 1/2.

> **Per the OUTPUT_SYMBOL precedent** (AppDrawer.kt line 161): the Output tile sources its glyph from
> the `DinghyIcons.OutputSection` token rather than a hand-typed literal. If a `DinghyIcons` token is
> added for `pulse_alert` (see below), prefer sourcing the drawer symbol from it the same way.

---

### `designsystem/icons/DinghyIcons.kt` (modify — add D-01..D-10 bindings)

**Analog:** the Phase-19 owner-locked output block (DinghyIcons.kt lines 86-98).

Each new entry is a `DinghyIcon(IconRef.Ligature("<ligature>"), alternate = "<unique_handle>")`, and
every new `val` MUST be appended to the `all` list (DinghyIcons.kt lines 126-136) — the list is
hand-rolled (no reflection) and a `DinghyIconsTest` uniqueness/drift guard checks it.

**Binding pattern** (DinghyIcons.kt lines 86-98):
```kotlin
val OutputHeater = DinghyIcon(IconRef.Ligature("mode_heat"), alternate = "output_heater")
val OutputFan = DinghyIcon(IconRef.Ligature("mode_fan_2"), alternate = "output_fan")
val OutputSection = DinghyIcon(IconRef.Ligature("output"), alternate = "output_section")
```
→ add (D-01..D-10, all verified resolvable in v2.944 per RESEARCH Icon Gate):
```kotlin
val SysInfoTile      = DinghyIcon(IconRef.Ligature("pulse_alert"), alternate = "sysinfo_tile")    // D-01
val SysInfoHost      = DinghyIcon(IconRef.Ligature("dns"), alternate = "sysinfo_host")             // D-02
// D-03 thermostat already exists as LauncherTemperature — REUSE, do not re-add
val SysInfoUptime    = DinghyIcon(IconRef.Ligature("schedule"), alternate = "sysinfo_uptime")      // D-04
val SysInfoCpu       = DinghyIcon(IconRef.Ligature("developer_board"), alternate = "sysinfo_cpu")  // D-05
val SysInfoRam       = DinghyIcon(IconRef.Ligature("memory"), alternate = "sysinfo_ram")           // D-06
val SysInfoDistro    = DinghyIcon(IconRef.Ligature("deployed_code"), alternate = "sysinfo_distro") // D-07
val SysInfoKernel    = DinghyIcon(IconRef.Ligature("code_blocks"), alternate = "sysinfo_kernel")   // D-08
// D-09 speed already exists as Speed — REUSE, do not re-add
val SysInfoMemUsage  = DinghyIcon(IconRef.Ligature("data_usage"), alternate = "sysinfo_mem_usage") // D-10
```
> **⚠ Reuse, don't re-add** (RESEARCH Icon Gate): `thermostat` (D-03) is already `LauncherTemperature`
> (line 49) and `speed` (D-09) is already `Speed` (line 84). Re-registering the same ligature under a
> new token name may trip the uniqueness guard — REUSE the existing tokens for those two rows.
> Also add all 10 names to `tools/verify_ligatures.py`'s `NEEDED` set (Phase-20 block) per D-11.

---

### Health chip — pure fn + shape-coded render (new, in `systeminfo/`)

**Analog (pure logic):** `OutputsHolder.reached()` style — a pure, host-testable `private fun`
returning a typed result. **Analog (render):** `DinghyIcons.StatusStop` + its render in
`PrintStatusScreen.StopButton`.

**Pure decision fn** — model `(throttledState: ThrottledState?, cpuTemp: Float?) -> HealthState`
after OutputsHolder's pure `when`-on-family decision (OutputsHolder.kt lines 158-167). RESEARCH
Health-Chip Logic Spec locks the precedence (`throttled_state != null` → throttle path; else temp
fallback) and the D-12 cutoffs (`>= 80` caution, `>= 70` warn, else healthy).

**Shape-coded render** — the chip reuses the EXISTING shipped shape tokens (NO new drawable):

`DinghyIcons.StatusStop` (DinghyIcons.kt lines 71-72) — the **caution/red** shape:
```kotlin
val StatusStop = DinghyIcon(IconRef.Ligature("disabled_by_default"), alternate = "status_stop")
```
**Render pattern** (PrintStatusScreen.kt `StopButton`, lines 996-1001) — shape carries safety,
color redundant (THEMING):
```kotlin
DinghyIconView(
    DinghyIcons.StatusStop,
    tint = t.stop,
    sizeDp = fsSp(32f, t.fs).dp,
    contentDescription = stringResource(R.string.cd_emergency_stop),
)
```
→ chip: **caution** = `DinghyIcons.StatusStop` tinted `t.stop`; **warn** = `IconRef.Ligature("warning")`
(triangle — already in the `verify_ligatures.py` NEEDED set, resolves) tinted `t.heat` (amber);
**healthy** = shapeless / `check`/`check_circle` go-state (`CheckCircle` already exists at line 44).
Use `DinghyIconView` + `fsSp`-scaled size; never rely on intrinsic glyph size.

---

### `command/CommandRegistry.kt` (modify — register two query specs)

**Analog:** `serverInfo` (CommandRegistry.kt lines 183-188) — a no-args, query-only `CommandSpec<Unit>`.

**Spec pattern** (CommandRegistry.kt lines 183-188):
```kotlin
val serverInfo: CommandSpec<Unit> = jsonRpc(
    catalogId = "MR-server.info",
    method = "server.info",
    key = { "server_info" },
    params = { null },
)
```
→ add:
```kotlin
val machineSystemInfo: CommandSpec<Unit> = jsonRpc(
    catalogId = "MR-machine.system_info",
    method = "machine.system_info",
    key = { "machine_system_info" },
    params = { null },
)   // availability default = Always (no ComponentPresent gate; both hosts always expose it)

val machineProcStats: CommandSpec<Unit> = jsonRpc(
    catalogId = "MR-machine.proc_stats",
    method = "machine.proc_stats",
    key = { "machine_proc_stats" },
    params = { null },
)
```
**Then append BOTH to `all`** (CommandRegistry.kt lines 751+, after `serverInfo`) — the list is the
drift-test's source of truth:
```kotlin
val all: List<CommandSpec<*>> = listOf(
    identify, oneshotToken, serverInfo, /* ... */ )
```

> **⚠ DRIFT GATE** ([[dinghy-command-catalog-drift]], RESEARCH Pitfall 6): registering these in `all`
> WITHOUT the two JSON rows below fails `CommandCatalogDriftTest.registryCommandsHaveMatrixAvailabilityRows`.
> Do all three edits (registry + catalog + matrix) in the SAME wave.

> **⚠ Drift test does NOT enforce the `registered: true` flip** (Codex review): the drift test verifies
> a catalog row + matrix row EXIST for each registered ID, but it does NOT assert
> `runtime_registry.registered == true`. An executor could register the spec, satisfy the drift test,
> and still leave the catalog row flagged reference-only — a silent inconsistency no test catches.
> 20-03 makes the `registered: true` flip for BOTH IDs a separately-checkable acceptance criterion
> (explicit grep/jq assertion), not implicit in "add the spec".

---

### `docs/commands/catalog.json` (modify — flip reference rows to registered)

**Analog:** the existing reference rows at lines 6056-6083 (`MR-machine.proc_stats`) and 6288-6314
(`MR-machine.system_info`). Both already exist (so `registryCatalogIdsExistInCatalogJson` passes);
flip `runtime_registry` to registered (RESEARCH Drift Rows §1):
```json
"runtime_registry": {
  "status": "registered",
  "registered": true,
  "notes": "Registered runtime CommandSpec in CommandRegistry (Phase 20 System Information)."
}
```
> `id` MUST equal `catalog_id` (`catalogEntriesHavePlanRequiredShape` asserts this — already satisfied,
> don't break it). The drift test does NOT assert on `runtime_registry.status`/`registered` value, so
> 20-03 adds an explicit grep/jq acceptance check that BOTH rows carry `registered: true` (see the
> drift-gate note above).

---

### `docs/commands/printer-matrix.json` (modify — add two availability rows)

**Analog:** the `MR-server.gcode_store` `command_availability` row shape (predicate `always` → no
per-host evidence needed). Append two rows to the `command_availability` array (starts ~line 1303),
copy the exact shape from RESEARCH Drift Rows §2:
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
    { "printer_id": "ender5plus", "status": "present", "evidence": "machine/system_info probed 2026-06-08; cpu_info{model,cpu_count:4,total_memory} + distribution{name,version,kernel_version}." },
    { "printer_id": "ender3", "status": "present", "evidence": "machine/system_info probed 2026-06-08; cpu_info{model:'',cpu_count:6,total_memory} + distribution{Armbian/ubuntu 24.04, kernel 6.18.10-current-rockchip64}." }
  ]
}
```
(+ the matching `MR-machine.proc_stats` row — full text in RESEARCH lines 305-319.)
> Because both predicates are `always`, `predicateReferencesAreBackedByMatrixEvidence` contributes
> ZERO leaf predicates → no `objects`/`components` evidence rows needed (RESEARCH Drift Rows).

---

### Capture-driven tests + fixtures (new)

**Analog:** `app/src/test/java/works/mees/dinghy/calibration/BedMeshModelTest.kt` (resource-load +
assert) and `app/src/test/java/works/mees/dinghy/net/GoldenFixtures.kt` (classpath resource loader).

**Fixture-load pattern** (BedMeshModelTest.kt lines 35-39):
```kotlin
private fun bedMeshFixture(): JsonObject {
    val res = javaClass.getResource("/fixtures/bed_mesh_e5.json")
        ?: error("fixture /fixtures/bed_mesh_e5.json missing from test classpath")
    return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
}
```
**Assert-from-fixture** (BedMeshModelTest.kt lines 41-56):
```kotlin
@Test fun realFixture_parsesMatricesAndMinMaxAsArrays() {
    val model = BedMeshModel.from(bedMeshFixture())
    assertEquals(3, model.probedMatrix.size)
    assertEquals(85.52119999999996, model.meshMin.x, 1e-6)
}
```
**Commit the 5 fixtures** (RESEARCH Validation Architecture — live captures already in 20-RESEARCH.md):
`fixtures/system_info_e5.json`, `system_info_e3.json`, `proc_stats_e5.json` (clean throttle),
`proc_stats_e3.json` (null throttle), `notify_proc_stat_push_e5.json` (proves throttle/uptime omission).

**RED scaffolds (must compile day-one, [[dinghy-wave0-red-scaffold-compile.md]]):**
`SystemInfoParseTest` (model empty→`—`, kernel under `distribution`), `ProcStatPushTest` (parser does
NOT require throttle/uptime), `HealthChipTest` (pure fn: E5 clean→healthy, synthetic bits→warn/caution,
E3 temp-fallback @70/80), `SysInfoFormatTest` (kB→GB/MB + uptime `2d 3h 14m`), `DegradeTest`
(empty model, `throttled_state: null`, missing keys → `—`, no crash).

---

## Shared Patterns

### Color / token discipline (THEME-01)
**Source:** every screen reads `val t = LocalTokens.current`; NO raw color literal.
**Apply to:** SystemInformationScreen, health chip. (LED/filament color is the only THEME-01 carve-out
— not relevant here; this page has no data-color.)

### Font scale ([[dinghy-font-sizes-too-small]])
**Source:** `fsSp(baseSp, t.fs)` — AboutScreen/PrintStatusScreen.
**Apply to:** every text + icon size on the new screen. 15sp metadata floor, 17-18 body, 20-22 titles.

### Edge-driven one-shot seed (Phase-13 cadence contract)
**Source:** `temperatureStore` / `gcodeStore` CommandSpecs (seeded once per handshake), the
`docs/request-cadence-contract.md` Rule 3 pattern.
**Apply to:** `machine.system_info` + `machine.proc_stats` queries — seed per handshake, re-run on
reconnect, NEVER a per-screen poll loop (SC-2). The 1 Hz push needs no extra throttle (already ≥ the
store's 250 ms floor).

### Graceful degradation (SC-3)
**Source:** OutputsHolder `displayValue: String?` → null hides only the value, row stays present.
**Apply to:** every InfoRow — missing/blank field → `—`, never crash, layout stable across both SBCs.
Treat empty-string ≠ null (RESEARCH Pitfall 4).

---

## No Analog Found

None. Every new/modified file maps to a concrete shipped pattern in this repo.

The only genuinely NEW pure logic is the **health-chip decision function** — but its *shape* (a pure,
host-testable `when`-decision returning a typed state) is well-precedented (`OutputsHolder.reached()`,
`buildRow()`), and its *render* reuses the shipped `StatusStop` / `warning` shape tokens. No new
drawable, no new architecture.

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/{ui/screen,ui/shell,ui/printstatus,net,outputs,command,designsystem/icons}`, `app/src/test/java/works/mees/dinghy/{calibration,net,spool}`, `app/src/test/resources/fixtures`, `docs/commands`.
**Files scanned:** ~14 source + test files read in full or targeted.
**Pattern extraction date:** 2026-06-08
