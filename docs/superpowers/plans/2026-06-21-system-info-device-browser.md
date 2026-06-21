# System Info Device Browser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the flat read-only System Info screen into a **device browser** — Field = a list of devices (Host SBC + each Klipper MCU), Focus = the selected device's rich detail card + its contextual restart/power actions.

**Architecture:** All NEW data sourcing is self-contained in `SystemInfoHolder` via the existing `CommandDispatcher.query`/`dispatch` seams + new `CommandRegistry` specs + new pure models/parsers. **No `MoonrakerSession` handshake or `SpineHandle` surgery.** MCU enumeration + software versions are **lazy-loaded** when the screen mounts (zero cost unless opened); MCU live `last_stats` refresh on a screen-scoped ~2 s poll. Host action-gating (provider/available_services) rides along the already-fetched `SystemInfo` identity via an additive parse. The UI mirrors the just-shipped connection editor's Focus/Field grammar (Field = selectable rows; selection swaps the Focus; Back in foot bar).

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization (`JsonElement`/`JsonObject` walking), coroutines/Flow. Host JUnit unit tests. Build is Windows-side.

## Global Constraints

- **minSdk 23** floor — no library/API requiring 24+. (No new deps in this plan.)
- **Icons are owner-selected — NEVER auto-pick** (`dinghy-never-pick-icons-ask`). The glyphs in this plan are the owner's confirmed picks (2026-06-21): MCU device = `memory_alt`; Reboot = `restart_alt`; Shutdown = reuse `SystemRowPower` (`power_settings_new`); Firmware Restart = `memory`; Restart-Moonraker = `sync_alt`; Restart-Klipper = reuse `Revert` (`refresh`). Any new `DinghyIcons` val needs the `val` AND the `all` registry list entry; intentional ligature reuse across distinct vals needs a `DinghyIconsTest` allow-list entry; all glyphs must pass `python tools/verify_ligatures.py`.
- **Per-field "—" degrade** — every formatter returns `DASH` (`—`) on null; the row + its leading icon stay present (SYS-04 stable layout). MCUs vary wildly — a CAN toolhead board won't report what the mainboard does.
- **`when(device)` branches are exhaustive** — the sealed `Device` type compile-enforces full coverage.
- **All restart/power actions are `ConfirmGuard`-wrapped + intent-colored** (red = reboot/shutdown destructive; amber = restart-service/firmware/klipper hazardous-in-process). Confirm copy must say the connection will drop and (for MCU restarts) "restarts all boards".
- **FocusFrame law** — every Focus has a title + icon + uDp 1U header and the docked e-stop while printing (`isPrinting`/`onEmergencyStop`/`onPanic`). Keep existing e-stop behavior; do not expand coverage.
- **FontConformanceTest** — NO inline `fontFamily=`/`fontSize=`; all text via `DinghyType` roles (`role.toTextStyle(t)`). **DinghyTypeTest** — fixed roles only on sanctioned ramp tiers {15, 20, 22, 24, 26}.
- **Throttle bits are Pi-only** — `throttledState == null` off-Pi → hide the whole throttle block. Use the Pi firmware bit map (bit0/1/2/3 = now; bit16/17/18/**19** = occurred-since-boot), NOT Moonraker's docs table (it has a duplicated `1<<16`).
- **Build/test commands** run Windows-side: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"` piped through `tr -d '\r'`; the process exit code is authoritative. Before any UAT, force a clean rebuild and verify APK mtime (`dinghy-stale-apk-uat-gate`); push the matching ABI slice to BOTH flox + moto (`dinghy-test-devices`).

---

## File Structure

**New files:**
- `app/src/main/java/works/mees/dinghy/systeminfo/DeviceModels.kt` — sealed `Device` (`HostDevice`/`McuDevice`), `Versions`, `HostActionAvailability`.
- `app/src/main/java/works/mees/dinghy/systeminfo/McuParse.kt` — `mcuObjectNames`, `mcuDisplayName`, `parseMcuDevices`, `parseMcuStats`, `mergeStats`, `decodeThrottleConditions`.
- `app/src/main/java/works/mees/dinghy/systeminfo/VersionParse.kt` — `parseKlipperVersion`, `parseMoonrakerVersion`.
- `app/src/main/java/works/mees/dinghy/systeminfo/HostActions.kt` — `hostActionAvailability`.
- `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoLoader.kt` — orchestration over injected query lambdas.
- `app/src/main/java/works/mees/dinghy/ui/systeminfo/DeviceFocus.kt` — Focus detail composables (Host + MCU detail + action buttons).
- Test files mirroring each (under `app/src/test/java/works/mees/dinghy/systeminfo/`).

**Modified files:**
- `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — new vals + `all` list.
- `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt` — allow-list additions.
- `tools/verify_ligatures.py` — no edit; run only.
- `app/src/main/res/values/strings.xml` — new labels + contentDescriptions.
- `app/src/main/java/works/mees/dinghy/net/JsonRpc.kt` — new method constants.
- `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` — new specs + `ServiceRestartArgs`.
- `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoModels.kt` — `SystemInfo` gains `provider`/`availableServices`.
- `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoParse.kt` — parse the two new fields.
- `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoHolder.kt` — new flows + `ensureLoaded`/`refreshMcuStats`.
- `app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt` — rebuilt device browser.
- `app/src/main/java/works/mees/dinghy/preview/SysInfoPreviews.kt` — device-browser preview matrix.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — pass `dispatcher` to the screen.

---

### Task 1: Icons — register new glyphs, allow-list, verify font

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`
- Modify: `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt`

**Interfaces:**
- Produces: `DinghyIcons.McuDevice`, `DinghyIcons.HostReboot`, `DinghyIcons.RestartService`, `DinghyIcons.McuFirmwareRestart`, `DinghyIcons.RestartKlipper`. (Shutdown reuses `DinghyIcons.SystemRowPower`.)

- [ ] **Step 1: Add the new icon vals.** In `DinghyIcons.kt`, near the existing `SysInfo*` block (around line 159–166), add:

```kotlin
    // ── System Info device browser (Part 2, 2026-06-21; owner-selected glyphs) ──────────────
    /** MCU/mainboard device row + Focus header. Owner pick: a distinct chip glyph (NOT developer_board,
     *  which is the host CPU detail row shown simultaneously). */
    val McuDevice = DinghyIcon(IconRef.Ligature("memory_alt"), alternate = "mcu_device")
    /** Host Reboot action (machine.reboot, destructive). */
    val HostReboot = DinghyIcon(IconRef.Ligature("restart_alt"), alternate = "host_reboot")
    /** Restart Moonraker service action (machine.services.restart, hazardous-in-process). */
    val RestartService = DinghyIcon(IconRef.Ligature("sync_alt"), alternate = "restart_service")
    /** MCU Firmware Restart (printer.firmware_restart, "restarts all boards"). Shares the `memory`
     *  ligature with SysInfoRam by owner choice — see DinghyIconsTest allow-list. */
    val McuFirmwareRestart = DinghyIcon(IconRef.Ligature("memory"), alternate = "mcu_firmware_restart")
    /** Restart Klipper (printer.restart soft restart). Shares the `refresh` ligature with Revert by
     *  owner choice — see DinghyIconsTest allow-list. */
    val RestartKlipper = DinghyIcon(IconRef.Ligature("refresh"), alternate = "restart_klipper")
```

- [ ] **Step 2: Add them to the `all` registry list.** In the `all = listOf(...)` block (around line 409–447), add `McuDevice, HostReboot, RestartService, McuFirmwareRestart, RestartKlipper` (place near `SysInfo*` entries).

- [ ] **Step 3: Extend the `DinghyIconsTest` IconRef-uniqueness allow-list.** Open `DinghyIconsTest.kt` (the allow-list is around lines 86–87 in `iconRef_isUnique_acrossAllEntries`). Add the two intentional shares:

```kotlin
        // System Info device browser (Part 2) — owner-chosen intentional ligature reuse on
        // non-co-occurring device selections (host detail vs MCU action buttons):
        "memory",   // SysInfoRam (host RAM detail) + McuFirmwareRestart (MCU action)
        "refresh",  // Revert (adjuster revert glyph) + RestartKlipper (MCU action)
```

(Match the existing allow-list entry format exactly — it's a set/list of ligature strings.)

- [ ] **Step 4: Run the icon registry test.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.designsystem.icons.DinghyIconsTest\" --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS (unique-alternate, iconRef-unique-with-allowlist, val-in-all-list all green).

- [ ] **Step 5: Verify the new ligatures resolve in the font.**

Run: `cd /mnt/e/claude/personal/github/dinghy-display && python tools/verify_ligatures.py`
Expected: exit 0, `missing = []`. The new ligatures are `memory_alt`, `restart_alt`, `sync_alt` (`memory`/`refresh`/`power_settings_new` are already registered → already in font).
**If any miss:** STOP and report which glyph is missing + its token to the owner (do NOT substitute a glyph — icon law). The font was refreshed to current on the connection-editor branch, so all three are expected to resolve; if not, the owner refreshes the ttf or picks an alternate.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt
git commit -m "feat(sysinfo): register device-browser icons (owner-selected)"
```

---

### Task 2: Command catalog — machine reboot/shutdown/services.restart

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/net/JsonRpc.kt`
- Modify: `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt`
- Test: `app/src/test/java/works/mees/dinghy/command/CommandRegistryMachineActionsTest.kt` (new)

**Interfaces:**
- Produces: `CommandRegistry.machineReboot: CommandSpec<Unit>`, `CommandRegistry.machineShutdown: CommandSpec<Unit>`, `CommandRegistry.restartService: CommandSpec<ServiceRestartArgs>`, `data class ServiceRestartArgs(val service: String)`. (Existing: `firmwareRestart`, `restart`, `serverInfo`, `printerInfo`, `objectsQuery`.)

- [ ] **Step 1: Write the failing test.** Create `CommandRegistryMachineActionsTest.kt`:

```kotlin
package works.mees.dinghy.command

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandRegistryMachineActionsTest {
    @Test fun reboot_hasMachineRebootMethod() {
        assertEquals("machine.reboot", CommandRegistry.machineReboot.method)
        assertEquals("machine_reboot", CommandRegistry.machineReboot.dispatchKey(Unit))
    }

    @Test fun shutdown_hasMachineShutdownMethod() {
        assertEquals("machine.shutdown", CommandRegistry.machineShutdown.method)
        assertEquals("machine_shutdown", CommandRegistry.machineShutdown.dispatchKey(Unit))
    }

    @Test fun restartService_putsServiceParam() {
        val spec = CommandRegistry.restartService
        assertEquals("machine.services.restart", spec.method)
        val params = spec.params(ServiceRestartArgs("moonraker")) as JsonObject
        assertEquals("moonraker", params["service"]!!.jsonPrimitive.content)
        assertEquals("services_restart_moonraker", spec.dispatchKey(ServiceRestartArgs("moonraker")))
    }
}
```

- [ ] **Step 2: Run it to confirm it fails.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.command.CommandRegistryMachineActionsTest\" --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — `machineReboot` unresolved.

- [ ] **Step 3: Add method constants.** In `JsonRpc.kt` (the `JsonRpcMethods` object, near `FIRMWARE_RESTART`/`RESTART` at lines 109–110), add:

```kotlin
    const val MACHINE_REBOOT = "machine.reboot"
    const val MACHINE_SHUTDOWN = "machine.shutdown"
    const val MACHINE_SERVICES_RESTART = "machine.services.restart"
```

- [ ] **Step 4: Add the args type + specs.** In `CommandRegistry.kt`, add `data class ServiceRestartArgs(val service: String)` next to `ObjectSubsetArgs` (around line 43). Then near the `firmwareRestart`/`restart` specs (lines 429–441) add:

```kotlin
    /** `machine.reboot` — reboot the host SBC. Destructive; the websocket WILL drop. */
    val machineReboot: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-machine.reboot",
        method = JsonRpcMethods.MACHINE_REBOOT,
        key = { "machine_reboot" },
        params = { null },
    )

    /** `machine.shutdown` — power off the host SBC. Destructive; the websocket WILL drop. */
    val machineShutdown: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-machine.shutdown",
        method = JsonRpcMethods.MACHINE_SHUTDOWN,
        key = { "machine_shutdown" },
        params = { null },
    )

    /** `machine.services.restart` — restart a host service (we use it for `moonraker`). */
    val restartService: CommandSpec<ServiceRestartArgs> = jsonRpc(
        catalogId = "MR-machine.services.restart",
        method = JsonRpcMethods.MACHINE_SERVICES_RESTART,
        key = { args -> "services_restart_${args.service}" },
        params = { args -> buildJsonObject { put("service", args.service) } },
    )
```

(Ensure `buildJsonObject`/`put` imports exist — they're already used in the file, e.g. line 241.)

- [ ] **Step 5: Register the new specs in `CommandRegistry.all`.** The three specs MUST be added to the `all = listOf(...)` catalog list in `CommandRegistry.kt` (around line 822) — otherwise they are uncataloged and the drift test fails. Add `machineReboot, machineShutdown, restartService` there.

- [ ] **Step 6: Satisfy the catalog-drift test** (`CommandCatalogDriftTest.kt:131` + memory `dinghy-command-catalog-drift`). This is a REAL gate, not optional. The drift test cross-checks `CommandRegistry.all` against `docs/commands/catalog.json` and `docs/commands/printer-matrix.json`.
  - `docs/commands/catalog.json` already has reference rows for `machine.reboot` (line ~6121), `machine.services.restart` (~6150), `machine.shutdown` (~6237) — confirm they match the new specs' `catalogId`/`method` (adjust the rows if the drift test wants the `MR-machine.*` catalogId form).
  - `docs/commands/printer-matrix.json` currently has only `machine.system_info`/`machine.proc_stats` nearby (~line 2929) — **add matrix entries** for the three new methods following the exact shape of the existing `machine.system_info` row (copy its structure: method id + per-printer support cells). 
  - Run the drift test:

  Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.command.CommandCatalogDriftTest\" --no-daemon" 2>&1 | tr -d '\r'`
  Expected: PASS. Iterate the catalog.json / printer-matrix.json rows until the drift test is green (it prints exactly what's missing/extra).

- [ ] **Step 7: Run the action-spec test to confirm it passes.**

Run: same as Step 2.
Expected: PASS.

- [ ] **Step 8: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/net/JsonRpc.kt app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt app/src/test/java/works/mees/dinghy/command/CommandRegistryMachineActionsTest.kt docs/commands/catalog.json docs/commands/printer-matrix.json
git commit -m "feat(sysinfo): add machine reboot/shutdown/services.restart command specs + catalog"
```

---

### Task 3: Data models — sealed Device, Versions, host action availability; extend SystemInfo

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/systeminfo/DeviceModels.kt`
- Modify: `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoModels.kt`
- Test: `app/src/test/java/works/mees/dinghy/systeminfo/DeviceModelsTest.kt` (new)

**Interfaces:**
- Produces:
  - `sealed interface Device { val key: String; val displayName: String }`
  - `data class HostDevice(displayName, identity: SystemInfo?, procStats: ProcStatQuery?, live: ProcStatLive?, klipperVersion: String?, moonrakerVersion: String?) : Device` with `key = "host"` and `companion { const val HOST_KEY = "host" }`.
  - `data class McuDevice(key, displayName, firmwareVersion, chip, clockHz, interfaceDesc, mcuAwake, taskAvg, taskStddev, bytesWrite, bytesRead, bytesRetransmit) : Device` (all detail fields nullable, default null).
  - `data class Versions(val klipper: String?, val moonraker: String?)`.
  - `SystemInfo` gains `val provider: String? = null` and `val availableServices: List<String> = emptyList()`.

- [ ] **Step 1: Write the failing test.** Create `DeviceModelsTest.kt`:

```kotlin
package works.mees.dinghy.systeminfo

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceModelsTest {
    @Test fun hostDevice_keyIsStableHostKey() {
        val h = HostDevice("ender5plus", null, null, null, null, null)
        assertEquals(HostDevice.HOST_KEY, h.key)
        assertEquals("host", HostDevice.HOST_KEY)
    }

    @Test fun mcuDevice_carriesRawObjectNameAsKey_andDetailDefaultsNull() {
        val m = McuDevice(key = "mcu EBBCan", displayName = "EBBCan")
        assertEquals("mcu EBBCan", m.key)
        assertEquals("EBBCan", m.displayName)
        assertEquals(null, m.firmwareVersion)
        assertEquals(null, m.bytesRetransmit)
    }

    @Test fun systemInfo_defaultsHaveNoProviderAndEmptyServices() {
        val s = SystemInfo()
        assertEquals(null, s.provider)
        assertEquals(emptyList(), s.availableServices)
    }
}
```

- [ ] **Step 2: Run it to confirm it fails.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.systeminfo.DeviceModelsTest\" --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — `HostDevice` unresolved.

- [ ] **Step 3: Create `DeviceModels.kt`:**

```kotlin
package works.mees.dinghy.systeminfo

/**
 * The two device kinds the System Info browser lists. The Field renders one ListRow per
 * device; the Focus renders [HostDevice]/[McuDevice] detail via an exhaustive `when`. [key] is the
 * stable selection id ("host" or the raw Klipper object name, e.g. "mcu EBBCan"). All MCU detail
 * fields are nullable — a CAN toolhead board reports a different subset than the mainboard (SYS-04).
 */
sealed interface Device {
    val key: String
    val displayName: String
}

/** The host SBC. Carries the live host flows directly (rebuilt per tick in the composable). */
data class HostDevice(
    override val displayName: String,
    val identity: SystemInfo?,
    val procStats: ProcStatQuery?,
    val live: ProcStatLive?,
    val klipperVersion: String?,
    val moonrakerVersion: String?,
) : Device {
    override val key: String get() = HOST_KEY

    companion object {
        const val HOST_KEY = "host"
    }
}

/** One Klipper MCU (mainboard, sub-board, or the [mcu host] Linux-process MCU). */
data class McuDevice(
    override val key: String,
    override val displayName: String,
    val firmwareVersion: String? = null,
    val chip: String? = null,
    val clockHz: Long? = null,
    val interfaceDesc: String? = null,
    val mcuAwake: Float? = null,
    val taskAvg: Float? = null,
    val taskStddev: Float? = null,
    val bytesWrite: Long? = null,
    val bytesRead: Long? = null,
    val bytesRetransmit: Long? = null,
) : Device

/** Software versions surfaced in the Host detail (lazy-loaded). */
data class Versions(val klipper: String?, val moonraker: String?)
```

- [ ] **Step 4: Extend `SystemInfo` in `SystemInfoModels.kt`.** Add the two fields to the `data class SystemInfo(...)` (after `kernel`):

```kotlin
    val kernel: String? = null,
    /** Moonraker machine `provider` (e.g. "systemd_dbus", "supervisord", "none"). Gates host power. */
    val provider: String? = null,
    /** Controllable services (e.g. ["klipper","moonraker"]). Gates Restart-Moonraker. */
    val availableServices: List<String> = emptyList(),
```

- [ ] **Step 5: Run the test to confirm it passes.**

Run: same as Step 2.
Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/systeminfo/DeviceModels.kt app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoModels.kt app/src/test/java/works/mees/dinghy/systeminfo/DeviceModelsTest.kt
git commit -m "feat(sysinfo): Device sealed model + SystemInfo provider/services fields"
```

---

### Task 4: MCU parsing — enumeration, display names, detail, stats, throttle decode

This is the core new-data work. All pure functions over `JsonElement`; no I/O.

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/systeminfo/McuParse.kt`
- Modify: `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoParse.kt` (parse provider/availableServices)
- Test: `app/src/test/java/works/mees/dinghy/systeminfo/McuParseTest.kt` (new)
- Test fixtures: reuse the JSON-string style of existing `SystemInfoParseTest.kt`/`DegradeTest.kt` (inline `Json.parseToJsonElement("""...""")`).

**Interfaces:**
- Produces:
  - `fun mcuObjectNames(objects: Set<String>): List<String>` — filters `"mcu"` + `it.startsWith("mcu ")`, stable order (mainboard `mcu` first, then alphabetical).
  - `fun mcuDisplayName(objectName: String): String` — `"mcu"`→"Mainboard", `"mcu host"`→"Host MCU", `"mcu EBBCan"`→"EBBCan".
  - `fun parseMcuDevices(mcuNames: List<String>, queryResult: JsonElement?): List<McuDevice>` — reads `result.status.<name>` for version/constants/last_stats and `result.status.configfile.settings.<lowercased name>` for interface.
  - `fun parseMcuStats(mcuNames: List<String>, queryResult: JsonElement?): Map<String, McuStats>` and `data class McuStats(...)`; `fun mergeStats(existing: List<McuDevice>, stats: Map<String, McuStats>): List<McuDevice>`.
  - `fun decodeThrottleConditions(state: ThrottledState?): List<String>` — null → empty; else human strings from the Pi bit map.
- Consumes: `McuDevice` (Task 3).

- [ ] **Step 1: Write the failing tests.** Create `McuParseTest.kt`. Use the multi-board Ender-5 shape and a sparse/single shape:

```kotlin
package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McuParseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun enumerate_filtersMcuObjects_mainboardFirst() {
        val objs = setOf("toolhead", "mcu EBBCan", "extruder", "mcu", "mcu host", "gcode_macro X")
        assertEquals(listOf("mcu", "mcu EBBCan", "mcu host"), mcuObjectNames(objs))
    }

    @Test fun displayName_mapping() {
        assertEquals("Mainboard", mcuDisplayName("mcu"))
        assertEquals("Host MCU", mcuDisplayName("mcu host"))
        assertEquals("EBBCan", mcuDisplayName("mcu EBBCan"))
    }

    @Test fun parseDevices_readsVersionConstantsStatsAndInterface() {
        val result = json.parseToJsonElement(
            """
            {"eventtime":1.0,"status":{
              "mcu":{"mcu_version":"v0.12.0-1","mcu_constants":{"MCU":"stm32f407","CLOCK_FREQ":168000000},
                     "last_stats":{"mcu_awake":0.05,"mcu_task_avg":0.0001,"mcu_task_stddev":0.00002,
                                   "bytes_write":1000,"bytes_read":2000,"bytes_retransmit":3}},
              "mcu EBBCan":{"mcu_version":"v0.12.0-can","mcu_constants":{"MCU":"stm32g0b1"},
                     "last_stats":{"mcu_awake":0.01,"bytes_retransmit":0}},
              "configfile":{"settings":{
                 "mcu":{"serial":"/dev/serial/by-id/usb-Klipper_stm32f407"},
                 "mcu ebbcan":{"canbus_uuid":"aabbccddeeff"}}}}}
            """.trimIndent(),
        )
        val devices = parseMcuDevices(listOf("mcu", "mcu EBBCan"), result)
        val board = devices.first { it.key == "mcu" }
        assertEquals("v0.12.0-1", board.firmwareVersion)
        assertEquals("stm32f407", board.chip)
        assertEquals(168000000L, board.clockHz)
        assertTrue(board.interfaceDesc!!.contains("usb-Klipper_stm32f407"))
        assertEquals(3L, board.bytesRetransmit)
        val can = devices.first { it.key == "mcu EBBCan" }
        assertEquals("stm32g0b1", can.chip)
        assertEquals(null, can.clockHz)            // per-field degrade
        assertTrue(can.interfaceDesc!!.contains("aabbccddeeff"))  // CAN uuid via lowercased section
    }

    @Test fun parseDevices_sparseResult_allFieldsDegradeNoThrow() {
        val result = json.parseToJsonElement("""{"status":{"mcu":{}}}""")
        val devices = parseMcuDevices(listOf("mcu"), result)
        assertEquals(1, devices.size)
        assertEquals(null, devices[0].firmwareVersion)
        assertEquals(null, devices[0].chip)
        assertEquals("Mainboard", devices[0].displayName)
    }

    @Test fun parseDevices_nullResult_returnsBareRowsPerName() {
        val devices = parseMcuDevices(listOf("mcu"), null)
        assertEquals(1, devices.size)
        assertEquals("mcu", devices[0].key)
    }

    @Test fun refreshStats_mergesIntoExistingStaticDetail() {
        val existing = listOf(McuDevice(key = "mcu", displayName = "Mainboard", chip = "stm32f407"))
        val result = json.parseToJsonElement(
            """{"status":{"mcu":{"last_stats":{"mcu_awake":0.9,"bytes_retransmit":7}}}}""",
        )
        val merged = mergeStats(existing, parseMcuStats(listOf("mcu"), result))
        assertEquals("stm32f407", merged[0].chip)      // static preserved
        assertEquals(0.9f, merged[0].mcuAwake)         // dynamic updated
        assertEquals(7L, merged[0].bytesRetransmit)
    }

    @Test fun throttle_decodesPiBitsAndOccurredMirror() {
        // bit0 under-voltage now + bit19 (0x80000) soft-temp-limit occurred
        val conditions = decodeThrottleConditions(ThrottledState(bits = 0x1 or 0x80000))
        assertTrue(conditions.any { it.contains("Under-voltage", ignoreCase = true) })
        assertTrue(conditions.any { it.contains("temperature", ignoreCase = true) })
    }

    @Test fun throttle_nullStateIsEmpty() {
        assertEquals(emptyList(), decodeThrottleConditions(null))
    }
}
```

- [ ] **Step 2: Run them to confirm they fail.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.systeminfo.McuParseTest\" --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — `mcuObjectNames` unresolved.

- [ ] **Step 3: Create `McuParse.kt`.** Reuse the tolerant `JsonObject` helpers already in `SystemInfoParse.kt` (`objectOrNull`, `blankStringOrNull`, `longOrNullAt`, `floatOrNullAt`, `intOrNullAt`) — if they are `private` there, make them `internal` so this file shares them (do NOT duplicate). Implementation:

```kotlin
package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Dynamic per-tick MCU stats (the refresh-poll subset). */
data class McuStats(
    val mcuAwake: Float? = null,
    val taskAvg: Float? = null,
    val taskStddev: Float? = null,
    val bytesWrite: Long? = null,
    val bytesRead: Long? = null,
    val bytesRetransmit: Long? = null,
)

/** Mainboard `mcu` first, then the rest alphabetically (stable Field order). */
fun mcuObjectNames(objects: Set<String>): List<String> =
    objects.filter { it == "mcu" || it.startsWith("mcu ") }
        .sortedWith(compareBy({ it != "mcu" }, { it.lowercase() }))

/** "mcu"→Mainboard, "mcu host"→Host MCU, "mcu <name>"→"<name>". */
fun mcuDisplayName(objectName: String): String = when {
    objectName == "mcu" -> "Mainboard"
    objectName == "mcu host" -> "Host MCU"
    objectName.startsWith("mcu ") -> objectName.removePrefix("mcu ").trim()
    else -> objectName
}

private fun statusOf(queryResult: JsonElement?): JsonObject? = runCatching {
    queryResult?.jsonObject?.objectOrNull("status")
}.getOrNull()

/** Full initial parse: version/constants/interface (static) + last_stats (dynamic). */
fun parseMcuDevices(mcuNames: List<String>, queryResult: JsonElement?): List<McuDevice> {
    val status = statusOf(queryResult)
    val settings = status?.objectOrNull("configfile")?.objectOrNull("settings")
    return mcuNames.map { name ->
        val obj = status?.objectOrNull(name)
        val constants = obj?.objectOrNull("mcu_constants")
        val stats = parseStats(obj)
        // configfile section names are LOWERCASED by Klipper — look up with the lowercased object name.
        val section = settings?.objectOrNull(name.lowercase())
        McuDevice(
            key = name,
            displayName = mcuDisplayName(name),
            firmwareVersion = obj?.blankStringOrNull("mcu_version"),
            chip = constants?.blankStringOrNull("MCU"),
            // Klipper's mcu_constant is CLOCK_FREQ; fall back to CLOCK defensively.
            clockHz = constants?.longOrNullAt("CLOCK_FREQ") ?: constants?.longOrNullAt("CLOCK"),
            interfaceDesc = interfaceDesc(section),
            mcuAwake = stats.mcuAwake,
            taskAvg = stats.taskAvg,
            taskStddev = stats.taskStddev,
            bytesWrite = stats.bytesWrite,
            bytesRead = stats.bytesRead,
            bytesRetransmit = stats.bytesRetransmit,
        )
    }
}

/** Refresh-poll subset: just last_stats per object. */
fun parseMcuStats(mcuNames: List<String>, queryResult: JsonElement?): Map<String, McuStats> {
    val status = statusOf(queryResult)
    return mcuNames.associateWith { parseStats(status?.objectOrNull(it)) }
}

/** Re-apply fresh stats onto the static detail (version/chip/interface preserved). */
fun mergeStats(existing: List<McuDevice>, stats: Map<String, McuStats>): List<McuDevice> =
    existing.map { d ->
        val s = stats[d.key] ?: return@map d
        d.copy(
            mcuAwake = s.mcuAwake, taskAvg = s.taskAvg, taskStddev = s.taskStddev,
            bytesWrite = s.bytesWrite, bytesRead = s.bytesRead, bytesRetransmit = s.bytesRetransmit,
        )
    }

private fun parseStats(obj: JsonObject?): McuStats {
    val ls = obj?.objectOrNull("last_stats") ?: return McuStats()
    return McuStats(
        mcuAwake = ls.floatOrNullAt("mcu_awake"),
        taskAvg = ls.floatOrNullAt("mcu_task_avg"),
        taskStddev = ls.floatOrNullAt("mcu_task_stddev"),
        bytesWrite = ls.longOrNullAt("bytes_write"),
        bytesRead = ls.longOrNullAt("bytes_read"),
        bytesRetransmit = ls.longOrNullAt("bytes_retransmit"),
    )
}

private fun interfaceDesc(section: JsonObject?): String? {
    if (section == null) return null
    section.blankStringOrNull("canbus_uuid")?.let { return "CAN $it" }
    section.blankStringOrNull("serial")?.let { return it }
    return null
}

/**
 * Decode the Raspberry Pi `throttled_state` bits into human conditions. Pi firmware bit map (NOT
 * Moonraker's docs table — it has a duplicated 1<<16): bit0 under-voltage now, bit1 freq-capped now,
 * bit2 throttled now, bit3 soft-temp-limit now; bit16/17/18/19 = the occurred-since-boot mirror
 * (soft-temp-limit occurred = bit19 = 0x80000). Null state (non-Pi) → empty.
 */
fun decodeThrottleConditions(state: ThrottledState?): List<String> {
    val bits = state?.bits ?: return emptyList()
    val out = mutableListOf<String>()
    if (bits and 0x1 != 0) out += "Under-voltage detected"
    if (bits and 0x2 != 0) out += "ARM frequency capped"
    if (bits and 0x4 != 0) out += "Currently throttled"
    if (bits and 0x8 != 0) out += "Soft temperature limit active"
    if (bits and 0x10000 != 0) out += "Under-voltage has occurred"
    if (bits and 0x20000 != 0) out += "Frequency capping has occurred"
    if (bits and 0x40000 != 0) out += "Throttling has occurred"
    if (bits and 0x80000 != 0) out += "Soft temperature limit has occurred"
    return out
}
```

- [ ] **Step 4: Make shared helpers `internal` if needed.** If `parseMcuDevices` fails to compile because `objectOrNull`/`blankStringOrNull`/`longOrNullAt`/`floatOrNullAt` are `private` in `SystemInfoParse.kt`, change those helper declarations from `private fun` to `internal fun` (same package). Do not duplicate them.

- [ ] **Step 5: Parse provider/availableServices in `SystemInfoParse.kt`.** In `SystemInfo.from`, after the existing fields, read them off the `sys` (`system_info`) object:

```kotlin
        provider = sys.blankStringOrNull("provider"),
        availableServices = sys.stringListOrEmpty("available_services"),
```

Add the helper if absent (near the other helpers):

```kotlin
internal fun JsonObject.stringListOrEmpty(key: String): List<String> = runCatching {
    (this[key] as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { s -> s.isNotBlank() } }
        ?: emptyList()
}.getOrDefault(emptyList())
```

**Imports:** `jsonPrimitive`/`contentOrNull`/`JsonArray` are NOT currently imported in `SystemInfoParse.kt` (it only imports `JsonObject`/`jsonObject`-level helpers) — add `import kotlinx.serialization.json.JsonArray`, `import kotlinx.serialization.json.jsonPrimitive`, `import kotlinx.serialization.json.contentOrNull`.

- [ ] **Step 6: Add a parse test for the new SystemInfo fields.** In the existing `SystemInfoParseTest.kt`, add:

```kotlin
    @Test fun parsesProviderAndAvailableServices() {
        val result = Json.parseToJsonElement(
            """{"system_info":{"provider":"systemd_dbus","available_services":["klipper","moonraker"]}}""",
        ).jsonObject
        val info = SystemInfo.from(result)
        assertEquals("systemd_dbus", info.provider)
        assertEquals(listOf("klipper", "moonraker"), info.availableServices)
    }
```

(Match the file's existing imports/parse style.)

- [ ] **Step 7: Run all systeminfo parse tests.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.systeminfo.*\" --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS (McuParseTest, DeviceModelsTest, SystemInfoParseTest, DegradeTest).

- [ ] **Step 8: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/systeminfo/McuParse.kt app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoParse.kt app/src/test/java/works/mees/dinghy/systeminfo/McuParseTest.kt app/src/test/java/works/mees/dinghy/systeminfo/SystemInfoParseTest.kt
git commit -m "feat(sysinfo): MCU enumeration/detail/stats parsers + throttle decode + provider parse"
```

---

### Task 5: Version parsing + host action availability

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/systeminfo/VersionParse.kt`
- Create: `app/src/main/java/works/mees/dinghy/systeminfo/HostActions.kt`
- Test: `app/src/test/java/works/mees/dinghy/systeminfo/VersionAndActionsTest.kt` (new)

**Interfaces:**
- Produces:
  - `fun parseKlipperVersion(printerInfoResult: JsonElement?): String?` — reads `software_version`.
  - `fun parseMoonrakerVersion(serverInfoResult: JsonElement?): String?` — reads `moonraker_version`.
  - `data class HostActionAvailability(val canReboot: Boolean, val canShutdown: Boolean, val canRestartMoonraker: Boolean, val powerDisabledReason: String?, val moonrakerDisabledReason: String?)`.
  - `fun hostActionAvailability(identity: SystemInfo?): HostActionAvailability`.

- [ ] **Step 1: Write the failing test.**

```kotlin
package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class VersionAndActionsTest {
    @Test fun parsesKlipperAndMoonrakerVersions() {
        val pi = Json.parseToJsonElement("""{"software_version":"v0.12.0-145-gabc","hostname":"e5"}""")
        val si = Json.parseToJsonElement("""{"moonraker_version":"v0.9.3-1-gdef","klippy_connected":true}""")
        assertEquals("v0.12.0-145-gabc", parseKlipperVersion(pi))
        assertEquals("v0.9.3-1-gdef", parseMoonrakerVersion(si))
        assertEquals(null, parseKlipperVersion(null))
        assertEquals(null, parseMoonrakerVersion(Json.parseToJsonElement("""{}""")))
    }

    @Test fun availability_defaultEnabledWhenUnknown() {
        val a = hostActionAvailability(SystemInfo())   // no provider, no services
        assertTrue(a.canReboot); assertTrue(a.canShutdown); assertTrue(a.canRestartMoonraker)
    }

    @Test fun availability_providerNoneDisablesPower() {
        val a = hostActionAvailability(SystemInfo(provider = "none"))
        assertFalse(a.canReboot); assertFalse(a.canShutdown)
        assertTrue(a.powerDisabledReason!!.isNotBlank())
    }

    @Test fun availability_supervisordDisablesPower() {
        // A container/supervisord host can't reboot the metal (Codex/Moonraker machine API).
        val a = hostActionAvailability(SystemInfo(provider = "supervisord_cli"))
        assertFalse(a.canReboot); assertFalse(a.canShutdown)
    }

    @Test fun availability_systemdEnablesPower() {
        assertTrue(hostActionAvailability(SystemInfo(provider = "systemd_dbus")).canReboot)
        assertTrue(hostActionAvailability(SystemInfo(provider = "systemd_cli")).canShutdown)
    }

    @Test fun availability_servicesWithoutMoonrakerDisablesRestart() {
        val a = hostActionAvailability(SystemInfo(provider = "systemd_dbus", availableServices = listOf("klipper")))
        assertTrue(a.canReboot)
        assertFalse(a.canRestartMoonraker)
        assertTrue(a.moonrakerDisabledReason!!.isNotBlank())
    }
}
```

- [ ] **Step 2: Run it to confirm it fails.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.systeminfo.VersionAndActionsTest\" --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — unresolved.

- [ ] **Step 3: Create `VersionParse.kt`:**

```kotlin
package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

fun parseKlipperVersion(printerInfoResult: JsonElement?): String? = runCatching {
    printerInfoResult?.jsonObject?.blankStringOrNull("software_version")
}.getOrNull()

fun parseMoonrakerVersion(serverInfoResult: JsonElement?): String? = runCatching {
    serverInfoResult?.jsonObject?.blankStringOrNull("moonraker_version")
}.getOrNull()
```

- [ ] **Step 4: Create `HostActions.kt`:**

```kotlin
package works.mees.dinghy.systeminfo

/**
 * Whether each host power/restart action can run, derived from `machine.system_info`'s `provider`
 * + `available_services`. Conservative: ENABLE by default (don't trap the user when data is sparse —
 * Moonraker will still reject if wrong); DISABLE only on a positive can't-run signal, with copy
 * explaining why (Codex: container hosts / unconfigured providers can't reboot the metal).
 */
data class HostActionAvailability(
    val canReboot: Boolean,
    val canShutdown: Boolean,
    val canRestartMoonraker: Boolean,
    val powerDisabledReason: String?,
    val moonrakerDisabledReason: String?,
)

// Providers Moonraker documents as able to reboot/shutdown the host. null/unknown → allow (don't
// trap a valid setup we don't recognize; Moonraker will still reject). Known non-power providers
// (none, supervisord*) → disable + explain.
private val POWER_CAPABLE_PROVIDERS = setOf("systemd_dbus", "systemd_cli")
private val KNOWN_NON_POWER_PROVIDERS = setOf("none", "supervisord", "supervisord_cli")

fun hostActionAvailability(identity: SystemInfo?): HostActionAvailability {
    val provider = identity?.provider
    val services = identity?.availableServices ?: emptyList()
    // Disable power only on a positive can't-run signal; unknown/null providers stay enabled.
    val powerBlocked = provider != null &&
        provider !in POWER_CAPABLE_PROVIDERS &&
        (provider in KNOWN_NON_POWER_PROVIDERS || provider.startsWith("supervisord"))
    // Restart-Moonraker: block only when a positive service list omits moonraker, or no power provider.
    val moonrakerBlocked = provider == "none" || (services.isNotEmpty() && "moonraker" !in services)
    return HostActionAvailability(
        canReboot = !powerBlocked,
        canShutdown = !powerBlocked,
        canRestartMoonraker = !moonrakerBlocked,
        powerDisabledReason = if (powerBlocked) "This host's service manager can't reboot/power it off" else null,
        moonrakerDisabledReason = if (moonrakerBlocked) "Moonraker isn't a managed service here" else null,
    )
}
```

- [ ] **Step 5: Run the test to confirm it passes.**

Run: same as Step 2.
Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/systeminfo/VersionParse.kt app/src/main/java/works/mees/dinghy/systeminfo/HostActions.kt app/src/test/java/works/mees/dinghy/systeminfo/VersionAndActionsTest.kt
git commit -m "feat(sysinfo): version parsers + host action availability gating"
```

---

### Task 6: SystemInfoLoader — orchestration over injected query lambdas

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoLoader.kt`
- Test: `app/src/test/java/works/mees/dinghy/systeminfo/SystemInfoLoaderTest.kt` (new)

**Interfaces:**
- Produces:
  - `class SystemInfoLoader(queryObjects: suspend (Set<String>) -> JsonElement, queryPrinterInfo: suspend () -> JsonElement, queryServerInfo: suspend () -> JsonElement)`.
  - `suspend fun loadVersions(): Versions`
  - `suspend fun loadMcuDevices(objectNames: Set<String>): List<McuDevice>`
  - `suspend fun refreshStats(objectNames: Set<String>, existing: List<McuDevice>): List<McuDevice>`
- Consumes: `parseMcuDevices`/`parseMcuStats`/`mergeStats`/`mcuObjectNames`/`parseKlipperVersion`/`parseMoonrakerVersion`/`Versions` (Tasks 3–5).

- [ ] **Step 1: Write the failing test** (injected lambdas — no Moonraker, no SpineHandle):

```kotlin
package works.mees.dinghy.systeminfo

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemInfoLoaderTest {
    private fun el(s: String) = Json.parseToJsonElement(s)

    @Test fun loadVersions_pullsBothCalls() = runTest {
        val loader = SystemInfoLoader(
            queryObjects = { error("unused") },
            queryPrinterInfo = { el("""{"software_version":"vK"}""") },
            queryServerInfo = { el("""{"moonraker_version":"vM"}""") },
        )
        assertEquals(Versions("vK", "vM"), loader.loadVersions())
    }

    @Test fun loadMcuDevices_emptyWhenNoMcuObjects_noQuery() = runTest {
        var queried = false
        val loader = SystemInfoLoader(
            queryObjects = { queried = true; el("{}") },
            queryPrinterInfo = { el("{}") }, queryServerInfo = { el("{}") },
        )
        assertEquals(emptyList(), loader.loadMcuDevices(setOf("extruder", "toolhead")))
        assertEquals(false, queried)
    }

    @Test fun loadMcuDevices_queriesNamesPlusConfigfile() = runTest {
        var captured: Set<String>? = null
        val loader = SystemInfoLoader(
            queryObjects = { names ->
                captured = names
                el("""{"status":{"mcu":{"mcu_version":"v1","last_stats":{"mcu_awake":0.1}}}}""")
            },
            queryPrinterInfo = { el("{}") }, queryServerInfo = { el("{}") },
        )
        val devices = loader.loadMcuDevices(setOf("mcu", "heater_bed"))
        assertEquals(setOf("mcu", "configfile"), captured)
        assertEquals("v1", devices.single().firmwareVersion)
    }

    @Test fun refreshStats_requeriesMcuObjectsOnly_mergesStats() = runTest {
        var captured: Set<String>? = null
        val loader = SystemInfoLoader(
            queryObjects = { names -> captured = names; el("""{"status":{"mcu":{"last_stats":{"mcu_awake":0.9}}}}""") },
            queryPrinterInfo = { el("{}") }, queryServerInfo = { el("{}") },
        )
        val existing = listOf(McuDevice(key = "mcu", displayName = "Mainboard", chip = "stm32"))
        val merged = loader.refreshStats(setOf("mcu", "extruder"), existing)
        assertEquals(setOf("mcu"), captured)          // configfile NOT re-queried on refresh
        assertEquals("stm32", merged[0].chip)
        assertEquals(0.9f, merged[0].mcuAwake)
    }
}
```

- [ ] **Step 2: Run it to confirm it fails.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.systeminfo.SystemInfoLoaderTest\" --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — `SystemInfoLoader` unresolved.

- [ ] **Step 3: Create `SystemInfoLoader.kt`:**

```kotlin
package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.JsonElement

/**
 * Orchestrates the System Info screen's lazy data loads over injected query lambdas (so it is host-
 * unit-testable with no Moonraker / SpineHandle). The holder wires the lambdas to
 * `CommandDispatcher.query(...)`. Each call is best-effort at the holder layer (the holder wraps
 * these in runCatching); the parsers themselves never throw.
 */
class SystemInfoLoader(
    private val queryObjects: suspend (Set<String>) -> JsonElement,
    private val queryPrinterInfo: suspend () -> JsonElement,
    private val queryServerInfo: suspend () -> JsonElement,
) {
    suspend fun loadVersions(): Versions = Versions(
        klipper = parseKlipperVersion(queryPrinterInfo()),
        moonraker = parseMoonrakerVersion(queryServerInfo()),
    )

    suspend fun loadMcuDevices(objectNames: Set<String>): List<McuDevice> {
        val mcus = mcuObjectNames(objectNames)
        if (mcus.isEmpty()) return emptyList()
        val result = queryObjects(mcus.toSet() + "configfile")
        return parseMcuDevices(mcus, result)
    }

    suspend fun refreshStats(objectNames: Set<String>, existing: List<McuDevice>): List<McuDevice> {
        val mcus = mcuObjectNames(objectNames)
        if (mcus.isEmpty()) return existing
        val result = queryObjects(mcus.toSet())   // no configfile on refresh — static detail is preserved
        return mergeStats(existing, parseMcuStats(mcus, result))
    }
}
```

- [ ] **Step 4: Run the test to confirm it passes.**

Run: same as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoLoader.kt app/src/test/java/works/mees/dinghy/systeminfo/SystemInfoLoaderTest.kt
git commit -m "feat(sysinfo): SystemInfoLoader orchestration (lazy versions + MCU detail/stats)"
```

---

### Task 7: SystemInfoHolder — new flows + lazy load + refresh

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoHolder.kt`
- Test: `app/src/test/java/works/mees/dinghy/systeminfo/SystemInfoHolderTest.kt` (new — only if a `SpineHandle` test factory exists; otherwise the loader tests are the coverage and this step is a NO-OP with a note)

**Interfaces:**
- Produces (on `SystemInfoHolder`): `val mcuDevices: StateFlow<List<McuDevice>?>`, `val klipperVersion: StateFlow<String?>`, `val moonrakerVersion: StateFlow<String?>`, `suspend fun ensureLoaded()`, `suspend fun refreshMcuStats()`.
- Consumes: `spine.dispatcher` (`CommandDispatcher.query`), `spine.capabilities` (`Capabilities.objects`), `SystemInfoLoader`, `CommandRegistry.objectsQuery/printerInfo/serverInfo`, `ObjectSubsetArgs`.

- [ ] **Step 1: Add the flows + loader wiring.** Edit `SystemInfoHolder.kt`. Add imports (`kotlinx.coroutines.flow.MutableStateFlow` already imported; add `kotlinx.coroutines.sync.Mutex`, `kotlinx.coroutines.sync.withLock`, `works.mees.dinghy.command.CommandRegistry`, `works.mees.dinghy.command.ObjectSubsetArgs`, `works.mees.dinghy.systeminfo.SystemInfoLoader`). Inside the class, after the `live` flow:

```kotlin
    private val loader = SystemInfoLoader(
        queryObjects = { names -> spine.dispatcher.query(CommandRegistry.objectsQuery, ObjectSubsetArgs(names)) },
        queryPrinterInfo = { spine.dispatcher.query(CommandRegistry.printerInfo, Unit) },
        queryServerInfo = { spine.dispatcher.query(CommandRegistry.serverInfo, Unit) },
    )

    private val _mcuDevices = MutableStateFlow<List<McuDevice>?>(null)
    /** Enumerated MCUs + per-MCU detail. Null until [ensureLoaded] runs (lazy — screen-mount only). */
    val mcuDevices: StateFlow<List<McuDevice>?> = _mcuDevices.asStateFlow()

    private val _klipperVersion = MutableStateFlow<String?>(null)
    val klipperVersion: StateFlow<String?> = _klipperVersion.asStateFlow()

    private val _moonrakerVersion = MutableStateFlow<String?>(null)
    val moonrakerVersion: StateFlow<String?> = _moonrakerVersion.asStateFlow()

    private val loadMutex = Mutex()
    private var versionsLoaded = false

    /**
     * Idempotent + retry-safe: fetch versions ONCE; load MCUs whenever they aren't loaded yet AND
     * capabilities have populated. An early call (before the handshake fills `capabilities.objects`)
     * must NOT permanently cache an empty list — so the MCU load is gated on non-empty caps and only
     * sets [_mcuDevices] on a non-null result, leaving null (= "retry me") otherwise (Codex). The
     * screen retries while [mcuDevices] is null (Task 9).
     */
    suspend fun ensureLoaded() {
        loadMutex.withLock {
            if (!versionsLoaded) {
                versionsLoaded = true
                runCatching {
                    val v = loader.loadVersions()
                    _klipperVersion.value = v.klipper
                    _moonrakerVersion.value = v.moonraker
                }
            }
            if (_mcuDevices.value == null) {
                val objs = spine.capabilities.value.objects
                if (objs.isNotEmpty()) {
                    runCatching { _mcuDevices.value = loader.loadMcuDevices(objs) }
                }
            }
        }
    }

    /** Re-query MCU last_stats (screen-scoped poll). No-op until [ensureLoaded] has populated devices. */
    suspend fun refreshMcuStats() {
        val current = _mcuDevices.value ?: return
        runCatching { _mcuDevices.value = loader.refreshStats(spine.capabilities.value.objects, current) }
    }
```

(Confirm `spine` is retained as a constructor `val`/param accessible here; the current ctor is `(scope, spine, procStatUpdates)`. If `spine` is a plain param not stored, change it to `private val spine` — it's already referenced by the existing `identity`/`procStats` initializers, so it is accessible.)

- [ ] **Step 2: Decide whether a holder test is feasible.** Search for an existing SpineHandle test factory:

Run: `cd /mnt/e/claude/personal/github/dinghy-display && grep -rln "SpineHandle(" app/src/test/java app/src/main/java | head`
- If a test builder/fixture exists (or `SpineHandle` is trivially constructable with a test `CommandDispatcher` built via `CommandDispatcher(request = {...}, scope = ...)` and a `MutableStateFlow(Capabilities(objects = ...))`), write `SystemInfoHolderTest.kt` asserting: `ensureLoaded()` populates `mcuDevices`/versions from a canned `request` lambda, and is idempotent (second call doesn't re-query). Use `runTest` + a `TestScope`.
- If building a `SpineHandle` in a test is disproportionately heavy (many required fields), SKIP the holder test — the loader (Task 6) + parsers (Task 4) already cover the logic; the holder is thin glue verified by on-device UAT (Task 11). Note the skip in the commit message.

- [ ] **Step 3: Compile the test sourceset + run systeminfo tests.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.systeminfo.*\" --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS. (This also confirms the holder change compiles — Gradle compiles the whole test sourceset; `dinghy-display-gradle-hang-interop` — keep coroutine tests bounded with `runTest`.)

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoHolder.kt app/src/test/java/works/mees/dinghy/systeminfo/SystemInfoHolderTest.kt
git commit -m "feat(sysinfo): holder lazy MCU/version load + stats refresh"
```

(Drop the test path from `git add` if Step 2 skipped it.)

---

### Task 8: UI — Focus detail composables (Host + MCU + action buttons)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/systeminfo/DeviceFocus.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces:
  - `enum class HostAction { Reboot, Shutdown, RestartMoonraker }`, `enum class McuAction { FirmwareRestart, RestartKlipper }`.
  - `@Composable fun HostDetail(host: HostDevice, throttle: List<String>, uDp: Dp)` — icon/label/value rows reusing the existing `SysInfo*` glyphs + version rows + Pi throttle block (hidden when empty).
  - `@Composable fun McuDetail(mcu: McuDevice, uDp: Dp)` — firmware/chip/clock/interface/load/bandwidth rows, per-field "—".
  - `@Composable fun HostActionButtons(avail: HostActionAvailability, inFlightKeys: Set<String>, onAction: (HostAction) -> Unit, uDp: Dp)` and `McuActionButtons(inFlightKeys, onAction: (McuAction) -> Unit, uDp)` — `OutlinedControl` intent-colored, disabled when in-flight or unavailable.
- Consumes: `FocusFrame` content slot, `OutlinedControl`, `DinghyIcons` (Task 1), formatters (`formatTemp`/`formatCpuLoad`/`formatUptime`/`formatGb`/`formatMemoryUsedOverTotal`/`formatCores`), `DinghyType`, `LocalTokens`, `LocalUnitDp`.

> **No-placeholder note:** reuse the existing `InfoListRow`-style icon/label/value row from the current `SystemInformationScreen.kt` (lines 280–315). Extract it into `DeviceFocus.kt` as `internal @Composable fun DetailRow(icon, cd, label, value, uDp)` so both Host and MCU detail share it (DRY), and delete the private copy from the screen file in Task 9.

- [ ] **Step 1: Add strings.** In `strings.xml`, after the existing `sysinfo_*` block (line ~317), add:

```xml
    <!-- ===================== system info device browser (Part 2) ===================== -->
    <string name="sysinfo_device_host">Host</string>
    <string name="sysinfo_mcu_mainboard">Mainboard</string>
    <string name="sysinfo_mcu_host">Host MCU</string>
    <!-- Host detail extra rows -->
    <string name="sysinfo_klipper_version">Klipper</string>
    <string name="sysinfo_moonraker_version">Moonraker</string>
    <string name="sysinfo_throttle">Health conditions</string>
    <!-- MCU detail rows -->
    <string name="sysinfo_mcu_firmware">Firmware</string>
    <string name="sysinfo_mcu_chip">Chip</string>
    <string name="sysinfo_mcu_clock">Clock</string>
    <string name="sysinfo_mcu_interface">Interface</string>
    <string name="sysinfo_mcu_load">MCU load</string>
    <string name="sysinfo_mcu_bandwidth">Bandwidth</string>
    <string name="sysinfo_mcu_retransmits">Retransmits</string>
    <!-- Actions -->
    <string name="sysinfo_action_reboot">Reboot</string>
    <string name="sysinfo_action_shutdown">Shutdown</string>
    <string name="sysinfo_action_restart_moonraker">Restart Moonraker</string>
    <string name="sysinfo_action_firmware_restart">Firmware Restart</string>
    <string name="sysinfo_action_restart_klipper">Restart Klipper</string>
    <!-- Confirm copy -->
    <string name="sysinfo_confirm_reboot_title">Reboot host?</string>
    <string name="sysinfo_confirm_reboot_msg">The printer host will reboot. The connection will drop while it restarts.</string>
    <string name="sysinfo_confirm_shutdown_title">Shut down host?</string>
    <string name="sysinfo_confirm_shutdown_msg">The printer host will power off and the connection will drop. You\'ll need to power it back on physically.</string>
    <string name="sysinfo_confirm_restart_moonraker_title">Restart Moonraker?</string>
    <string name="sysinfo_confirm_restart_moonraker_msg">Moonraker will restart. The connection will drop briefly and reconnect on its own.</string>
    <string name="sysinfo_confirm_firmware_restart_title">Firmware restart?</string>
    <string name="sysinfo_confirm_firmware_restart_msg">Restarts Klipper firmware on ALL connected boards, not just this one. The printer will be unavailable for a few seconds while it reconnects.</string>
    <string name="sysinfo_confirm_restart_klipper_title">Restart Klipper?</string>
    <string name="sysinfo_confirm_restart_klipper_msg">Klipper will soft-restart on all boards and the printer will be briefly unavailable. Any active print will be lost.</string>
    <string name="sysinfo_confirm_common">Confirm</string>
    <!-- contentDescriptions -->
    <string name="cd_sysinfo_device_mcu">MCU board</string>
    <string name="cd_sysinfo_firmware">Firmware version</string>
    <string name="cd_sysinfo_interface">Interface</string>
    <string name="cd_sysinfo_clock">Clock</string>
    <string name="cd_sysinfo_bandwidth">Bandwidth</string>
    <string name="cd_sysinfo_klipper">Klipper version</string>
    <string name="cd_sysinfo_moonraker">Moonraker version</string>
    <string name="cd_sysinfo_throttle">Health conditions</string>
```

- [ ] **Step 2: Create `DeviceFocus.kt`.** Implement the shared `DetailRow`, `HostDetail`, `McuDetail`, the two action enums, and the action-button rows. Full code:

```kotlin
package works.mees.dinghy.ui.systeminfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.systeminfo.DASH
import works.mees.dinghy.systeminfo.HostActionAvailability
import works.mees.dinghy.systeminfo.HostDevice
import works.mees.dinghy.systeminfo.McuDevice
import works.mees.dinghy.systeminfo.formatCores
import works.mees.dinghy.systeminfo.formatCpuLoad
import works.mees.dinghy.systeminfo.formatGb
import works.mees.dinghy.systeminfo.formatMemoryUsedOverTotal
import works.mees.dinghy.systeminfo.formatTemp
import works.mees.dinghy.systeminfo.formatUptime
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import androidx.compose.material3.Text

enum class HostAction { Reboot, Shutdown, RestartMoonraker }
enum class McuAction { FirmwareRestart, RestartKlipper }

/** Shared icon · label · value row (extracted from the old InfoListRow). Value end-aligned (UAT-2). */
@Composable
internal fun DetailRow(icon: DinghyIcon, cd: String, label: String, value: String, uDp: Dp) {
    val t = LocalTokens.current
    ListRow(
        selected = false,
        onClick = {},
        uDp = uDp,
        leadingContent = { ListRowIcon(icon = icon, uDp = uDp, tint = t.text2, contentDescription = cd) },
    ) {
        Text(text = label, color = t.text, style = DinghyType.listLabel.toTextStyle(t))
        Spacer(Modifier.weight(1f))
        Text(text = value, color = t.text2, style = DinghyType.dataMeta.toTextStyle(t))
    }
}

@Composable
fun HostDetail(host: HostDevice, throttle: List<String>, uDp: Dp) {
    val id = host.identity
    DetailRow(DinghyIcons.SysInfoHost, stringResource(R.string.cd_sysinfo_host),
        stringResource(R.string.sysinfo_host), host.displayName, uDp)
    DetailRow(DinghyIcons.SysInfoCpu, stringResource(R.string.cd_sysinfo_cpu),
        stringResource(R.string.sysinfo_cpu), cpuValue(id), uDp)   // reuse existing cpuValue(SystemInfo?)
    DetailRow(DinghyIcons.LauncherTemperature, stringResource(R.string.cd_sysinfo_cpu_temp),
        stringResource(R.string.sysinfo_cpu_temp), formatTemp(host.live?.cpuTemp ?: host.procStats?.cpuTemp), uDp)
    DetailRow(DinghyIcons.Speed, stringResource(R.string.cd_sysinfo_cpu_load),
        stringResource(R.string.sysinfo_cpu_load), formatCpuLoad(host.live?.cpuLoadPercent), uDp)
    DetailRow(DinghyIcons.SysInfoMemUsage, stringResource(R.string.cd_sysinfo_mem_usage),
        stringResource(R.string.sysinfo_memory), formatMemoryUsedOverTotal(host.live?.memUsedKb, host.live?.memTotalKb), uDp)
    DetailRow(DinghyIcons.SysInfoRam, stringResource(R.string.cd_sysinfo_ram),
        stringResource(R.string.sysinfo_ram), formatGb(id?.totalMemoryKb), uDp)
    DetailRow(DinghyIcons.SysInfoDistro, stringResource(R.string.cd_sysinfo_distro),
        stringResource(R.string.sysinfo_distro), distroValue(id), uDp)   // reuse existing distroValue(SystemInfo?)
    DetailRow(DinghyIcons.SysInfoKernel, stringResource(R.string.cd_sysinfo_kernel),
        stringResource(R.string.sysinfo_kernel), id?.kernel.orDash(), uDp)
    DetailRow(DinghyIcons.SysInfoUptime, stringResource(R.string.cd_sysinfo_uptime),
        stringResource(R.string.sysinfo_uptime), formatUptime(host.procStats?.systemUptimeSeconds), uDp)
    DetailRow(DinghyIcons.SysInfoTile, stringResource(R.string.cd_sysinfo_klipper),
        stringResource(R.string.sysinfo_klipper_version), host.klipperVersion.orDash(), uDp)
    DetailRow(DinghyIcons.SystemRowAbout, stringResource(R.string.cd_sysinfo_moonraker),
        stringResource(R.string.sysinfo_moonraker_version), host.moonrakerVersion.orDash(), uDp)
    // Pi throttle block — only when present (off-Pi throttledState is null → empty list → hidden).
    throttle.forEach { condition ->
        DetailRow(DinghyIcons.Warning, stringResource(R.string.cd_sysinfo_throttle),
            stringResource(R.string.sysinfo_throttle), condition, uDp)
    }
}

@Composable
fun McuDetail(mcu: McuDevice, uDp: Dp) {
    DetailRow(DinghyIcons.SysInfoTile, stringResource(R.string.cd_sysinfo_firmware),
        stringResource(R.string.sysinfo_mcu_firmware), mcu.firmwareVersion.orDash(), uDp)
    DetailRow(DinghyIcons.SysInfoCpu, stringResource(R.string.cd_sysinfo_cpu),
        stringResource(R.string.sysinfo_mcu_chip), mcu.chip.orDash(), uDp)
    DetailRow(DinghyIcons.Speed, stringResource(R.string.cd_sysinfo_clock),
        stringResource(R.string.sysinfo_mcu_clock), formatClock(mcu.clockHz), uDp)
    DetailRow(DinghyIcons.SysInfoHost, stringResource(R.string.cd_sysinfo_interface),
        stringResource(R.string.sysinfo_mcu_interface), mcu.interfaceDesc.orDash(), uDp)
    DetailRow(DinghyIcons.SysInfoMemUsage, stringResource(R.string.cd_sysinfo_cpu_load),
        stringResource(R.string.sysinfo_mcu_load), formatLoad(mcu.mcuAwake), uDp)
    DetailRow(DinghyIcons.SysInfoMemUsage, stringResource(R.string.cd_sysinfo_bandwidth),
        stringResource(R.string.sysinfo_mcu_bandwidth), formatBytes(mcu.bytesWrite, mcu.bytesRead), uDp)
    DetailRow(DinghyIcons.Warning, stringResource(R.string.cd_sysinfo_bandwidth),
        stringResource(R.string.sysinfo_mcu_retransmits), mcu.bytesRetransmit?.toString() ?: DASH, uDp)
}

@Composable
fun HostActionButtons(
    avail: HostActionAvailability,
    inFlightKeys: Set<String>,
    onAction: (HostAction) -> Unit,
    uDp: Dp,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedControl(
            label = stringResource(R.string.sysinfo_action_reboot),
            onClick = { onAction(HostAction.Reboot) },
            intent = Intent.Danger,
            icon = DinghyIcons.HostReboot,
            enabled = avail.canReboot && "machine_reboot" !in inFlightKeys,
            modifier = Modifier.weight(1f),
        )
        OutlinedControl(
            label = stringResource(R.string.sysinfo_action_shutdown),
            onClick = { onAction(HostAction.Shutdown) },
            intent = Intent.Danger,
            icon = DinghyIcons.SystemRowPower,
            enabled = avail.canShutdown && "machine_shutdown" !in inFlightKeys,
            modifier = Modifier.weight(1f),
        )
        OutlinedControl(
            label = stringResource(R.string.sysinfo_action_restart_moonraker),
            onClick = { onAction(HostAction.RestartMoonraker) },
            intent = Intent.Warn,
            icon = DinghyIcons.RestartService,
            enabled = avail.canRestartMoonraker && "services_restart_moonraker" !in inFlightKeys,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun McuActionButtons(inFlightKeys: Set<String>, onAction: (McuAction) -> Unit, uDp: Dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedControl(
            label = stringResource(R.string.sysinfo_action_firmware_restart),
            onClick = { onAction(McuAction.FirmwareRestart) },
            intent = Intent.Warn,
            icon = DinghyIcons.McuFirmwareRestart,
            enabled = "fw_restart" !in inFlightKeys,
            modifier = Modifier.weight(1f),
        )
        OutlinedControl(
            label = stringResource(R.string.sysinfo_action_restart_klipper),
            onClick = { onAction(McuAction.RestartKlipper) },
            intent = Intent.Warn,
            icon = DinghyIcons.RestartKlipper,
            enabled = "host_restart" !in inFlightKeys,
            modifier = Modifier.weight(1f),
        )
    }
}
```

> **Helpers to add at the bottom of `DeviceFocus.kt`** (pure, no Compose) OR move to `SysInfoFormat.kt` — your call, but keep them tested-able if non-trivial. `cpuValue`/`distroValue`/`orDash` already exist privately in `SystemInformationScreen.kt`; move them to a shared internal location (e.g. `SysInfoFormat.kt`) so both files use them. New formatters:
> - `formatClock(hz: Long?): String` → `"168 MHz"` (`hz / 1_000_000`), `DASH` on null.
> - `formatLoad(awake: Float?): String` → `"%.0f%%".format(awake * 100)` guarded, `DASH` on null. (Klipper `mcu_awake` is a fraction of the stat interval.)
> - `formatBytes(write: Long?, read: Long?): String` → `"↑1.0 KB ↓2.0 KB"` style or `DASH` if both null; degrade each side.
> - The action-button `Row`s use `Arrangement.spacedBy(8.dp)` — add `import androidx.compose.ui.unit.dp` to `DeviceFocus.kt`.
> - `cpuValue(SystemInfo?)`/`distroValue(SystemInfo?)`/`orDash()` are the EXISTING signatures (keep them; just relocate to the shared file). Do NOT introduce a `cpuValue(String?, Int?)` overload.

**Add unit tests** for `formatClock`/`formatLoad`/`formatBytes` in a `SysInfoFormatExtraTest.kt` (same style as existing formatter tests) — these are pure and cheap to test.

- [ ] **Step 3: Build to confirm it compiles** (UI has no unit test; compile is the gate here):

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the new formatter tests.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.systeminfo.SysInfoFormatExtraTest\" --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/systeminfo/DeviceFocus.kt app/src/main/res/values/strings.xml app/src/main/java/works/mees/dinghy/systeminfo/SysInfoFormat.kt app/src/test/java/works/mees/dinghy/systeminfo/SysInfoFormatExtraTest.kt
git commit -m "feat(sysinfo): device Focus detail composables + action buttons + formatters"
```

---

### Task 9: UI — rebuild SystemInformationScreen as the device browser

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt`
- Modify: `app/src/main/java/works/mees/dinghy/preview/SysInfoPreviews.kt`

**Interfaces:**
- Produces: `SystemInformationScreen(holder, dispatcher, onBack, isPrinting, onEmergencyStop)` and the stateless `SystemInformationContent(...)` taking `identity, procStats, live, mcus, klipperVersion, moonrakerVersion, isPrinting, onEmergencyStop, inFlightKeys, onHostAction, onMcuAction, onBack`.
- Consumes: `DeviceFocus.kt` composables (Task 8), `ConfirmGuard`, `FocusFrame`, `ListRow`/`ListRowIcon`, `ListBlock`, `FootButtonBar`, `rememberUnitGrid`, `buildDeviceList`.

- [ ] **Step 1: Add the pure device-list assembly helper + test.** In `SystemInformationScreen.kt` (or a small `DeviceList.kt`), add:

```kotlin
internal fun buildDeviceList(
    host: HostDevice,
    mcus: List<McuDevice>?,
): List<Device> = buildList {
    add(host)
    if (mcus != null) addAll(mcus)
}
```

Test (`DeviceListTest.kt`):

```kotlin
@Test fun buildDeviceList_hostFirstThenMcus() {
    val host = HostDevice("e5", null, null, null, null, null)
    val list = buildDeviceList(host, listOf(McuDevice("mcu", "Mainboard")))
    assertEquals(listOf("host", "mcu"), list.map { it.key })
}
@Test fun buildDeviceList_nullMcus_hostOnly() {
    assertEquals(1, buildDeviceList(HostDevice("e5", null, null, null, null, null), null).size)
}
```

- [ ] **Step 2: Rebuild the screen.** Replace the body of `SystemInformationScreen.kt`. Stateful entry:

```kotlin
@Composable
fun SystemInformationScreen(
    holder: SystemInfoHolder?,
    dispatcher: CommandDispatcher?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
) {
    val identity by (holder?.identity ?: nullStateFlow()).collectAsStateWithLifecycle()
    val procStats by (holder?.procStats ?: nullStateFlow()).collectAsStateWithLifecycle()
    val live by (holder?.live ?: nullStateFlow()).collectAsStateWithLifecycle()
    val mcus by (holder?.mcuDevices ?: nullStateFlow()).collectAsStateWithLifecycle()
    val klipperVersion by (holder?.klipperVersion ?: nullStateFlow()).collectAsStateWithLifecycle()
    val moonrakerVersion by (holder?.moonrakerVersion ?: nullStateFlow()).collectAsStateWithLifecycle()
    val inFlight by (dispatcher?.inFlight ?: remember { MutableStateFlow(emptySet<String>()) })
        .collectAsStateWithLifecycle()

    // Lazy load + retry while MCUs haven't loaded (capabilities may not have populated on first call).
    LaunchedEffect(holder) {
        val h = holder ?: return@LaunchedEffect
        repeat(10) {
            h.ensureLoaded()
            if (h.mcuDevices.value != null) return@LaunchedEffect
            delay(500)
        }
    }
    // Screen-scoped live poll of MCU stats (~2 s) — stops when the screen leaves the composition.
    LaunchedEffect(holder, mcus?.isNotEmpty()) {
        if (holder != null && !mcus.isNullOrEmpty()) {
            while (true) { delay(2000); holder.refreshMcuStats() }
        }
    }

    SystemInformationContent(
        identity = identity, procStats = procStats, live = live, mcus = mcus,
        klipperVersion = klipperVersion, moonrakerVersion = moonrakerVersion,
        isPrinting = isPrinting, onEmergencyStop = onEmergencyStop, inFlightKeys = inFlight,
        onHostAction = { a -> dispatcher?.let { d -> dispatchHostAction(d, a) } },
        onMcuAction = { a -> dispatcher?.let { d -> dispatchMcuAction(d, a) } },
        onBack = onBack, modifier = modifier,
    )
}

private fun dispatchHostAction(d: CommandDispatcher, action: HostAction) = when (action) {
    HostAction.Reboot -> d.dispatch(CommandRegistry.machineReboot, Unit)
    HostAction.Shutdown -> d.dispatch(CommandRegistry.machineShutdown, Unit)
    HostAction.RestartMoonraker -> d.dispatch(CommandRegistry.restartService, ServiceRestartArgs("moonraker"))
}

private fun dispatchMcuAction(d: CommandDispatcher, action: McuAction) = when (action) {
    McuAction.FirmwareRestart -> d.dispatch(CommandRegistry.firmwareRestart, Unit)
    McuAction.RestartKlipper -> d.dispatch(CommandRegistry.restart, Unit)
}
```

(The `dispatchHostAction`/`dispatchMcuAction` private helpers below translate the UI action enum to the right `CommandRegistry` spec dispatch.)

- [ ] **Step 3: Rebuild `SystemInformationContent`.** Stateless, preview-drivable; owns selection state + the confirm dialog:

```kotlin
@Composable
fun SystemInformationContent(
    identity: SystemInfo?,
    procStats: ProcStatQuery?,
    live: ProcStatLive?,
    mcus: List<McuDevice>?,
    klipperVersion: String?,
    moonrakerVersion: String?,
    inFlightKeys: Set<String>,
    onHostAction: (HostAction) -> Unit,
    onMcuAction: (McuAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
) {
    val hostName = identity?.model ?: identity?.distroName ?: stringResource(R.string.sysinfo_device_host)
    val host = HostDevice(hostName, identity, procStats, live, klipperVersion, moonrakerVersion)
    val devices = buildDeviceList(host, mcus)

    var selectedKey by rememberSaveable { mutableStateOf(HostDevice.HOST_KEY) }
    val selected = devices.firstOrNull { it.key == selectedKey } ?: host
    var pendingHost by remember { mutableStateOf<HostAction?>(null) }
    var pendingMcu by remember { mutableStateOf<McuAction?>(null) }

    val throttle = decodeThrottleConditions(procStats?.throttledState)
    val avail = hostActionAvailability(identity)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = selected.displayName,
                    icon = deviceIcon(selected),
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxSize(),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    when (val d = selected) {
                        is HostDevice -> {
                            HostDetail(d, throttle, grid.uDp)
                            Spacer(Modifier.weight(1f))
                            HostActionButtons(avail, inFlightKeys, { pendingHost = it }, grid.uDp)
                        }
                        is McuDevice -> {
                            McuDetail(d, grid.uDp)
                            Spacer(Modifier.weight(1f))
                            McuActionButtons(inFlightKeys, { pendingMcu = it }, grid.uDp)
                        }
                    }
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    items(devices, key = { it.key }) { device ->
                        DeviceRow(device, selected = device.key == selectedKey,
                            onClick = { selectedKey = device.key }, uDp = grid.uDp)
                    }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            icon = DinghyIcons.Back, onClick = onBack, intent = Intent.Accent,
                        ),
                    ),
                )
            },
        )

        // Confirm overlays (full-bleed ConfirmGuard).
        pendingHost?.let { action ->
            val (titleRes, msgRes, destructive) = hostConfirmCopy(action)
            ConfirmGuard(
                title = stringResource(titleRes),
                message = stringResource(msgRes),
                confirmLabel = stringResource(R.string.sysinfo_confirm_common),
                onConfirm = { onHostAction(action); pendingHost = null },
                onCancel = { pendingHost = null },
                destructive = destructive,
                warn = !destructive,
            )
        }
        pendingMcu?.let { action ->
            val (titleRes, msgRes) = mcuConfirmCopy(action)
            ConfirmGuard(
                title = stringResource(titleRes),
                message = stringResource(msgRes),
                confirmLabel = stringResource(R.string.sysinfo_confirm_common),
                onConfirm = { onMcuAction(action); pendingMcu = null },
                onCancel = { pendingMcu = null },
                destructive = false, warn = true,   // amber: hazardous-but-in-process
            )
        }
    }
}

private fun deviceIcon(device: Device): DinghyIcon = when (device) {
    is HostDevice -> DinghyIcons.SysInfoHost
    is McuDevice -> DinghyIcons.McuDevice
}

@Composable
private fun DeviceRow(device: Device, selected: Boolean, onClick: () -> Unit, uDp: Dp) {
    val t = LocalTokens.current
    ListRow(
        selected = selected, onClick = onClick, uDp = uDp,
        leadingContent = {
            ListRowIcon(
                icon = deviceIcon(device), uDp = uDp, tint = if (selected) t.accent else t.text2,
                contentDescription = when (device) {
                    is HostDevice -> stringResource(R.string.cd_sysinfo_host)
                    is McuDevice -> stringResource(R.string.cd_sysinfo_device_mcu)
                },
            )
        },
    ) {
        Text(device.displayName, color = t.text, style = DinghyType.listLabel.toTextStyle(t))
        Spacer(Modifier.weight(1f))
        Text(deviceGlance(device), color = t.text2, style = DinghyType.dataMeta.toTextStyle(t))
    }
}

private fun deviceGlance(device: Device): String = when (device) {
    is HostDevice -> formatCpuLoad(device.live?.cpuLoadPercent)
    is McuDevice -> formatLoad(device.mcuAwake)
}
```

Add the two confirm-copy helpers returning the string-resource ids:

```kotlin
private fun hostConfirmCopy(a: HostAction): Triple<Int, Int, Boolean> = when (a) {
    HostAction.Reboot -> Triple(R.string.sysinfo_confirm_reboot_title, R.string.sysinfo_confirm_reboot_msg, true)
    HostAction.Shutdown -> Triple(R.string.sysinfo_confirm_shutdown_title, R.string.sysinfo_confirm_shutdown_msg, true)
    HostAction.RestartMoonraker -> Triple(R.string.sysinfo_confirm_restart_moonraker_title, R.string.sysinfo_confirm_restart_moonraker_msg, false)
}

private fun mcuConfirmCopy(a: McuAction): Pair<Int, Int> = when (a) {
    McuAction.FirmwareRestart -> R.string.sysinfo_confirm_firmware_restart_title to R.string.sysinfo_confirm_firmware_restart_msg
    McuAction.RestartKlipper -> R.string.sysinfo_confirm_restart_klipper_title to R.string.sysinfo_confirm_restart_klipper_msg
}
```

Delete the now-unused old `InfoListRow`, `healthChipVisual`, `health`-row code, and move `cpuValue`/`distroValue`/`orDash` to the shared location (Task 8). Keep `nullStateFlow()`.

- [ ] **Step 4: Update the preview matrix.** In `SysInfoPreviews.kt`, replace the old previews with `SystemInformationContent` calls covering: (a) Host selected, full Pi data + throttle conditions; (b) Host, non-Pi sparse (no throttle, "—" fields); (c) an MCU selected (multi-board fixture, CAN interface); (d) MCU sparse degrade; across dark + light themes. Pass `inFlightKeys = emptySet()`, no-op action lambdas. Wrap each in the existing preview theme harness used by the other previews in this file.

- [ ] **Step 5: Build + run the device-list test.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests \"works.mees.dinghy.systeminfo.DeviceListTest\" --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL + PASS.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt app/src/main/java/works/mees/dinghy/preview/SysInfoPreviews.kt app/src/test/java/works/mees/dinghy/systeminfo/DeviceListTest.kt
git commit -m "feat(sysinfo): rebuild System Info as a device browser (Field=devices, Focus=detail+actions)"
```

---

### Task 10: Wire the screen at the navigation call site

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`

**Interfaces:**
- Consumes: the new `SystemInformationScreen(holder, dispatcher, onBack, isPrinting, onEmergencyStop)` signature.

- [ ] **Step 1: Pass the dispatcher.** Find the `composable<NavDest.SystemInfo>` route in `AppShell.kt`. The `dispatcher` is already in scope there (it's used for `onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) }`). Update the call to pass `dispatcher = dispatcher`:

```kotlin
composable<NavDest.SystemInfo> {
    SystemInformationScreen(
        holder = systemInfoHolder,
        dispatcher = dispatcher,
        isPrinting = printerState.printState == PrintState.Printing ||
            printerState.printState == PrintState.Paused,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onBack = { navController.popBackStack() },
    )
}
```

(Match the actual variable names in scope — `systemInfoHolder`, `dispatcher`, `printerState` per the explore findings. If `dispatcher` is nullable, the screen already accepts `CommandDispatcher?`.)

- [ ] **Step 2: Build.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
git commit -m "feat(sysinfo): pass dispatcher into the device-browser screen"
```

---

### Task 11: Full verification — suite, R8, on-device UAT

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit suite.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL, all green — especially `DinghyIconsTest`, `FontConformanceTest`, `DinghyTypeTest`, all `works.mees.dinghy.systeminfo.*`, and the new command test.

- [ ] **Step 2: Verify ligatures once more** (belt-and-braces after all icon refs are in):

Run: `cd /mnt/e/claude/personal/github/dinghy-display && python tools/verify_ligatures.py`
Expected: exit 0.

- [ ] **Step 3: R8 release build** (smaller dex / proves shrink rules hold — no new serializable models need keep rules here, but confirm):

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleRelease --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Force a clean debug rebuild + verify APK freshness** (`dinghy-stale-apk-uat-gate`):

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks --no-daemon" 2>&1 | tr -d '\r'`
Then confirm the APK mtime is after the last commit before installing.

- [ ] **Step 5: Install on BOTH devices + UAT** (`dinghy-test-devices` — flox `armeabi-v7a` id `0a64b42e`, moto `arm64-v8a` id `ZY22LBDRM9`; push the matching split-ABI slice to each). Hand off to the owner to drive. UAT checklist:
  - **Ender 5 (multi-board)** — System Info shows Host + SKR3 + EZBoard + EBB_CAN as separate rows; selecting each fills the Focus with that board's firmware/chip/clock/interface (CAN uuid for the toolhead) + live load that ticks; bandwidth + retransmits present.
  - **Ender 3 (single-board)** — Host + 1 mainboard; uniform treatment (no special collapse).
  - **Host detail** — model/distro/kernel/CPU/RAM/uptime + live CPU%/temp/mem + Klipper + Moonraker versions; on the Pi, throttle conditions appear only if present; off-Pi the throttle block is absent.
  - **Actions** — Restart Moonraker (amber) confirms → drops + reconnects cleanly (surfaced as restarting, not an error). Firmware Restart / Restart Klipper (amber) confirm copy says "all boards". Reboot/Shutdown (red) confirm copy warns the connection drops. Disabled actions (if any provider gating triggers) explain why.
  - **Mid-print** — the docked e-stop is reachable in the Focus header; actions still confirm-gated.
  - **Both orientations** — portrait stacks, landscape 50/50; rows + detail survive at the 5U floor on flox.

- [ ] **Step 6: After owner UAT passes**, finalize per `superpowers:finishing-a-development-branch` (merge/PR choice is the owner's). Update memory + the CLAUDE.md note that the "System Info device-browser is UNBUILT" (it's now built).

---

## Self-Review

**Spec coverage (Part 2):**
- Device discovery (Host + MCU enumeration from `printer.objects.list`/capabilities, `mcu`/`mcu <name>`/`[mcu host]`) → Tasks 4, 6. ✓
- Field device list (icon by type, live glance) → Task 9 (`DeviceRow`, `deviceGlance`). ✓
- Focus host detail (static + live + versions + Pi throttle decode, hide off-Pi) → Tasks 4, 8 (`HostDetail`, `decodeThrottleConditions`). ✓
- Focus MCU detail (mcu_version/mcu_constants/interface/last_stats; NO board temp) → Tasks 4, 8. ✓
- Per-field "—" degrade → all formatters + parsers (Tasks 4, 8). ✓
- Restart/power contextual + ConfirmGuard + intent + gating + expect-ws-drop copy → Tasks 2, 5, 8, 9. ✓
- One Firmware Restart "restarts all boards" (no per-board) → Task 8 copy. ✓
- Foot bar Back + docked e-stop → Task 9. ✓
- Data sourcing (holder flows, query seam, configfile parse, versions) → Tasks 6, 7. ✓
- System-page power button: left as-is (out of scope, no duplication). ✓
- Icons owner-selected + verify_ligatures + DinghyIconsTest → Task 1. ✓
- Tests: MCU enumeration multi-board + degrade; `when(device)` compile-enforced → Tasks 3, 4, 6. ✓
- UAT both devices vs real E5/E3 → Task 11. ✓

**Placeholder scan:** clean after the Codex pass — the former `it2`/`8.dpU` placeholders are resolved. The only conditional step is the Task-7 holder unit test (gated on whether a `SpineHandle` test factory exists, with an explicit skip criterion + UAT fallback), which is a deliberate scope decision, not a vague TODO.

**Codex review (2026-06-21):** folded in — `CLOCK_FREQ` clock key (was `CLOCK`), stricter provider gating (`none`/`supervisord*` disable power; unknown→allow), `CommandRegistry.all` + `catalog.json`/`printer-matrix.json` drift entries, connection-impact confirm copy, `private val spine` + retry-on-empty MCU load, reuse of existing `cpuValue(SystemInfo?)`, missing `kotlinx.serialization.json` imports, case-insensitive MCU sort. Tests should prefer the real fixtures Codex found (`app/src/test/resources/outputs/objects_list_e5p.json`, `configfile_settings_e5p.json`) over inline JSON where they fit.

**Type consistency:** dispatch keys match the specs (`machine_reboot`/`machine_shutdown`/`services_restart_moonraker` from Task 2; `fw_restart`/`host_restart` are the existing keys reused for MCU actions). `Device.key` strings (`"host"` + raw object names) flow consistently through `buildDeviceList`/`selectedKey`/`deviceIcon`/`deviceGlance`. `Intent.Danger`/`Intent.Warn`/`Intent.Accent` match the confirmed enum. Formatter names match across Tasks 4/8/9.
