# Phase 6: Command Reference & Capability Matrix - Pattern Map

**Mapped:** 2026-06-01  
**Files analyzed:** 24 new/modified files or file families  
**Analogs found:** 21 / 24

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `docs/commands/klipper-gcode.md` | docs | reference/transform | `docs/moonraker-capabilities.md` | role-match |
| `docs/commands/moonraker-api.md` | docs | reference/transform | `docs/moonraker-capabilities.md` | role-match |
| `docs/commands/spoolman-api.md` | docs | reference/transform | `docs/moonraker-capabilities.md` | role-match |
| `docs/commands/printer-availability-matrix.md` | docs | reference/transform | `docs/moonraker-capabilities.md` | role-match |
| `docs/commands/catalog.json` | docs/config fixture | batch/transform | `app/src/test/resources/golden/*.json` + `GoldenFixtures.kt` | partial |
| `docs/commands/printer-matrix.json` | docs/config fixture | batch/transform | `app/src/test/resources/golden/*.json` + `GoldenFixtures.kt` | partial |
| `app/src/main/java/works/mees/dinghy/command/CommandSpec.kt` | model | request-response | `JsonRpc.kt`, `PrinterCommands.kt` | role-match |
| `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` | config/registry | request-response | `JsonRpcMethods`, `PrinterCommands` | role-match |
| `app/src/main/java/works/mees/dinghy/command/CommandDispatchExtensions.kt` | utility | request-response | `CommandDispatcher.kt`, `JsonRpcClient.kt` | role-match |
| `app/src/main/java/works/mees/dinghy/net/JsonRpc.kt` | model/config | request-response | same file | exact |
| `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt` | utility | transform | same file | exact |
| `app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt` | service | request-response | same file | exact |
| `app/src/main/java/works/mees/dinghy/state/Capabilities.kt` | model | transform | same file | exact |
| `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` | utility | transform | same file | exact |
| `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` | service | event-driven/request-response | same file | exact |
| `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt` | service/provider | event-driven/request-response | same file | exact |
| `PrintStatusScreen.kt` | component | event-driven/request-response | same file | exact |
| `TemperatureScreen.kt` | component | event-driven/request-response | same file | exact |
| `MoveScreen.kt` | component | event-driven/request-response | same file | exact |
| `ExtrudeScreen.kt` | component | event-driven/request-response | same file | exact |
| `app/src/test/java/works/mees/dinghy/command/CommandRegistryTest.kt` | test | batch/transform | `PrinterCommandsTest.kt` | role-match |
| `app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt` | test | request-response | same file | exact |
| `app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt` | test | transform | same file | exact |
| `app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt` | test | request-response | same file | exact |

## Pattern Assignments

### `docs/commands/*.md` (docs, reference/transform)

**Analog:** `docs/moonraker-capabilities.md`

**Document structure pattern** (lines 1-16):
````markdown
# Moonraker / Klipper capability catalog — what our printers actually expose

**Purpose:** the source of truth for which Moonraker/Klipper objects + fields dinghy-display may rely
on, captured from the **real printers** (not docs, not assumptions).

Probe method (read-only REST; repeat to refresh):
```bash
curl -s "http://$H/printer/info"
curl -s "http://$H/server/info"
curl -s "http://$H/printer/objects/list"
```
````

**Capability evidence pattern** (lines 21-29):
```markdown
## Ender 5 Plus (`ender5plus`, 192.168.1.120:7125) — captured 2026-06-01

- Klipper `v0.13.0-662` · Moonraker `v0.10.0` (api 1.5.0) · slicer in files = **OrcaSlicer 2.4**
- **Moonraker components present** (`server.info.components`): includes `spoolman`, `history`,
  `job_queue`, `job_state`, `webcam`, `timelapse`, `update_manager`
```

**Degrade/optional field pattern** (lines 76-86):
```markdown
- **Reliable (always present):** progress, print_duration (elapsed), filament_used, Z (gcode_position),
  temps/targets, filename, state
- **Slicer-dependent (fail-safe optional):** `print_stats.info.current_layer/total_layer`
- **No direct ETA** from Moonraker — always computed.
```

Apply this to command docs: each command row should cite upstream URL, version/capture provenance, purpose, params, semantics tier, availability predicate, and fail-safe notes. Keep Markdown human-readable; tests should target JSON sidecars.

---

### `docs/commands/catalog.json` and `docs/commands/printer-matrix.json` (docs/config fixture, batch/transform)

**Analog:** `app/src/test/resources/golden/*.json` with loader in `GoldenFixtures.kt`

**Fixture loader pattern** (`GoldenFixtures.kt` lines 15-40):
```kotlin
object GoldenFixtures {
    fun raw(name: String): String =
        requireNotNull(GoldenFixtures::class.java.getResourceAsStream("/golden/$name")) {
            "Fixture /golden/$name not found on the test classpath"
        }.bufferedReader().use { it.readText() }

    fun load(name: String): JsonElement = MoonrakerJson.parseToJsonElement(raw(name))

    fun resolve(liveName: String): JsonElement {
        val live = GoldenFixtures::class.java.getResourceAsStream("/golden/$liveName")
        return if (live != null) {
            live.bufferedReader().use { MoonrakerJson.parseToJsonElement(it.readText()) }
        } else {
            load("fallback_$liveName")
        }
    }
}
```

**Sanity-test pattern** (`FixtureSanityTest.kt` lines 24-42):
```kotlin
private val allFixtures = listOf(
    "objects_list.json",
    "objects_query_snapshot.json",
    "notify_status_update_stream.json",
)

@Test
fun allFixturesLoadAndParse() {
    allFixtures.forEach { name ->
        val obj = GoldenFixtures.loadObject(name)
        assertTrue("Fixture $name parsed empty", obj.isNotEmpty())
    }
}
```

For `docs/commands/*.json`, add a small test helper that reads from the filesystem path rather than classpath resources, but copy the same `MoonrakerJson.parseToJsonElement(...)`, fail-fast `requireNotNull` style, and parse-sanity assertions.

---

### `app/src/main/java/works/mees/dinghy/command/CommandSpec.kt` (model, request-response)

**Analogs:** `JsonRpc.kt`, `PrinterCommands.kt`, `Capabilities.kt`

**Plain serial/wire model imports** (`JsonRpc.kt` lines 1-7):
```kotlin
package works.mees.dinghy.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
```

**Loose JSON param posture** (`JsonRpc.kt` lines 25-32):
```kotlin
@Serializable
data class JsonRpcRequest(
    val method: String,
    val params: JsonElement? = null,
    val id: Long,
    val jsonrpc: String = "2.0",
)
```

**Headless immutable model pattern** (`Capabilities.kt` lines 3-10):
```kotlin
/**
 * Toolkit-agnostic, immutable capability model ... PLAIN data class with NO Compose annotations.
 */
data class Capabilities(
```

Build `CommandSpec`, `CommandTransport`, `AvailabilityPredicate`, and `CommandSemantics` as plain Kotlin in the `command` package. Use `JsonElement?` for generated params and do not depend on Android, Compose, service, or UI packages.

---

### `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` (config/registry, request-response)

**Analogs:** `JsonRpcMethods` and `PrinterCommands`

**Centralized method constants to fold in** (`JsonRpc.kt` lines 93-120):
```kotlin
object JsonRpcMethods {
    const val IDENTIFY = "server.connection.identify"
    const val OBJECTS_LIST = "printer.objects.list"
    const val OBJECTS_QUERY = "printer.objects.query"
    const val OBJECTS_SUBSCRIBE = "printer.objects.subscribe"
    const val ONESHOT_TOKEN = "access.oneshot_token"
    const val EMERGENCY_STOP = "printer.emergency_stop"
    const val FIRMWARE_RESTART = "printer.firmware_restart"
    const val RESTART = "printer.restart"
    const val GCODE_SCRIPT = "printer.gcode.script"
    const val TEMPERATURE_STORE = "server.temperature_store"
    const val FILES_METADATA = "server.files.metadata"
    const val HISTORY_LIST = "server.history.list"
}
```

**Builder wrapping rule** (`PrinterCommands.kt` lines 68-149):
```kotlin
fun setHeater(heater: String, target: Int): String =
    "SET_HEATER_TEMPERATURE HEATER=$heater TARGET=${target.coerceIn(MIN_TEMP_C, MAX_TEMP_C)}"

fun jog(axis: String, mm: Double, feedMmMin: Int): String {
    val a = requireAxis(axis)
    val d = clampMagnitude(mm, MAX_JOG_MM)
    val f = feedMmMin.coerceIn(MIN_FEED_MM_MIN, MAX_JOG_FEED_MM_MIN)
    return "SAVE_GCODE_STATE NAME=dd_jog\nG91\nG1 $a$d F$f\nRESTORE_GCODE_STATE NAME=dd_jog"
}

fun scriptParams(gcode: String): JsonElement = buildJsonObject { put("script", gcode) }
```

Registry entries for G-Code actions must call these builders verbatim and wrap the result with `scriptParams`; do not rewrite builders as generic string templates. Notification names can stay outside outbound registry.

---

### `app/src/main/java/works/mees/dinghy/command/CommandDispatchExtensions.kt` (utility, request-response)

**Analog:** `CommandDispatcher.kt`

**Dispatch signature to preserve** (`CommandDispatcher.kt` lines 63-83, 108-124):
```kotlin
class CommandDispatcher(
    private val request: suspend (method: String, params: JsonElement?, timeoutMs: Long) -> JsonElement,
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    fun dispatch(key: String, method: String, params: JsonElement? = null) {
        if (key in _inFlight.value) return
        val perCmdTimeout = if (method == JsonRpcMethods.GCODE_SCRIPT) GCODE_TIMEOUT_MS else timeoutMs
```

**Error handling pattern** (`CommandDispatcher.kt` lines 127-155):
```kotlin
try {
    withTimeout(perCmdTimeout) { request(method, params, perCmdTimeout) }
} catch (e: RpcConnectionException) {
    val message = when (e.reason) {
        is ConnectionError.Timeout -> "$method is taking longer than expected — still running"
        else -> "$method failed: command could not be sent"
    }
    _events.tryEmit(DispatchEvent.Failure(key, message))
} catch (e: RpcError) {
    _events.tryEmit(DispatchEvent.Failure(key, e.message ?: method))
} catch (e: TimeoutCancellationException) {
    _events.tryEmit(DispatchEvent.Failure(key, "$method timed out"))
} finally {
    _inFlight.update { it - key }
}
```

Add thin overloads only: `dispatch(command, args)` should compute key/method/params then delegate to the existing `dispatch(key, method, params)`, so debounce, long G-Code timeout, non-fatal `RpcError`, and redaction behavior remain centralized.

---

### `JsonRpc.kt` refactor (model/config, request-response)

**Analog:** same file

**Shared JSON singleton pattern** (lines 78-87):
```kotlin
val MoonrakerJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}
```

**Notification split pattern** (lines 122-130):
```kotlin
const val NOTIFY_STATUS_UPDATE = "notify_status_update"
const val NOTIFY_GCODE_RESPONSE = "notify_gcode_response"
const val NOTIFY_KLIPPY_READY = "notify_klippy_ready"
const val NOTIFY_KLIPPY_SHUTDOWN = "notify_klippy_shutdown"
const val NOTIFY_KLIPPY_DISCONNECTED = "notify_klippy_disconnected"
```

Keep `MoonrakerJson` and inbound notification routing here. Move or wrap outbound constants through the registry, but do not force inbound notification names into the outbound command model.

---

### `PrinterCommands.kt` preservation (utility, transform)

**Analog:** same file

**Safety and purity contract** (lines 7-23):
```kotlin
/**
 * PURE gcode builders ... NO I/O, NO coroutines, NO Compose;
 * same input -> same output; fully host-testable off-hardware.
 *
 * SECURITY ... every numeric param is CLAMPED to a named bounded range BEFORE it is
 * formatted into a string.
 *
 * SAFETY ... wraps the move in SAVE_GCODE_STATE/RESTORE_GCODE_STATE.
 */
object PrinterCommands {
```

**Mode-safe builders** (lines 88-93, 133-149):
```kotlin
fun jog(axis: String, mm: Double, feedMmMin: Int): String {
    val a = requireAxis(axis)
    val d = clampMagnitude(mm, MAX_JOG_MM)
    val f = feedMmMin.coerceIn(MIN_FEED_MM_MIN, MAX_JOG_FEED_MM_MIN)
    return "SAVE_GCODE_STATE NAME=dd_jog\nG91\nG1 $a$d F$f\nRESTORE_GCODE_STATE NAME=dd_jog"
}

fun extrude(mm: Double, feedMmMin: Int): String {
    val d = clampMagnitude(mm, MAX_EXTRUDE_MM)
    val f = feedMmMin.coerceIn(MIN_FEED_MM_MIN, MAX_EXTRUDE_FEED_MM_MIN)
    return "SAVE_GCODE_STATE NAME=dd_ext\nM83\nG1 E$d F$f\nRESTORE_GCODE_STATE NAME=dd_ext"
}

fun scriptParams(gcode: String): JsonElement = buildJsonObject { put("script", gcode) }
```

Registry work should not materially edit these builders except moving them only if tests prove byte-identical output. Prefer leaving this file intact and referencing it from registry entries.

---

### `Capabilities.kt` and `DeriveCapabilities.kt` (model/utility, transform)

**Analogs:** same files

**Current capability model extension point** (`Capabilities.kt` lines 10-35):
```kotlin
data class Capabilities(
    val hasBed: Boolean = false,
    val extruderCount: Int = 0,
    val fans: List<String> = emptyList(),
    val macros: List<String> = emptyList(),
    val powerDevices: List<String> = emptyList(),
    val heaters: List<String> = emptyList(),
) {
    fun hasMacroIgnoreCase(name: String): Boolean = macros.any { it.equals(name, ignoreCase = true) }
}
```

**Pure derivation pattern** (`DeriveCapabilities.kt` lines 20-40):
```kotlin
fun deriveCapabilities(objects: List<String>): Capabilities {
    val ext = objects.filter(::isExtruder)
    val macros = objects.filter { it.startsWith("gcode_macro ") }.map { it.removePrefix("gcode_macro ") }
    val fans = objects.filter {
        it == "fan" ||
            it.startsWith("fan_generic ") ||
            it.startsWith("heater_fan ") ||
            it.startsWith("controller_fan ")
    }
    val heaters = objects.filter {
        it == "heater_bed" || isExtruder(it) || it.startsWith("heater_generic ")
    }
    return Capabilities(
```

Add `objects: Set<String> = emptySet()` plus `fun hasObject(name: String): Boolean = name in objects`. Populate it from `objects.toSet()` inside `deriveCapabilities`. Preserve existing typed fields, `hasMacroIgnoreCase`, and pure no-I/O behavior.

---

### `MoonrakerSession.kt` call-site refactor (service, event-driven/request-response)

**Analog:** same file

**Handshake order to preserve** (`MoonrakerSession.kt` lines 299-323):
```kotlin
private suspend fun runHandshake() {
    rpc.request(JsonRpcMethods.IDENTIFY, identifyParams())

    val listResult = rpc.request(JsonRpcMethods.OBJECTS_LIST)
    val objects = parseObjectsList(listResult)

    val capabilities = deriveCapabilities(objects)
    store.setCapabilities(capabilities)
    val subset = deriveSubscribeSet(objects)

    val queryResult = rpc.request(JsonRpcMethods.OBJECTS_QUERY, objectsParam(subset))
    val status = parseStatus(queryResult)
    if (status != null) store.seed(reduceSnapshot(status))

    val subResult = rpc.request(JsonRpcMethods.OBJECTS_SUBSCRIBE, objectsParam(subset))
    parseStatus(subResult)?.let { store.seed(reduceSnapshot(it)) }
```

**Best-effort one-shot pattern** (`MoonrakerSession.kt` lines 325-350):
```kotlin
runCatching {
    val storeResult = rpc.request(JsonRpcMethods.TEMPERATURE_STORE)
    val backfill = parseTemperatureStore(storeResult.jsonObject, capabilities.heaters.toSet())
    store.setTemperatureBackfill(backfill)
}
runCatching {
    val cfgResult = rpc.request(JsonRpcMethods.OBJECTS_QUERY, objectsParam(setOf("configfile")))
    val extruderCfg = parseStatus(cfgResult)
        ?.objectOrNull("configfile")
        ?.objectOrNull("settings")
        ?.objectOrNull("extruder")
    store.setMinExtrudeTemp(extruderCfg?.floatOrNullAt("min_extrude_temp"))
}
```

Registry request helpers must preserve identify -> list -> query -> subscribe order, required `url` param, subscribe seeding, and best-effort one-shot reads.

---

### `MoonrakerService.kt` call-site refactor (service/provider, event-driven/request-response)

**Analog:** same file

**Spine construction and dispatcher ownership** (`MoonrakerService.kt` lines 126-135):
```kotlin
val session = MoonrakerSession(
    store = store,
    rpc = rpc,
    socketEvents = socketEvents,
    auth = auth,
    baseWsUrl = cfg.wsUrl,
)
val dispatcher = CommandDispatcher(rpc, serviceScope)
```

**One-shot read pattern** (`MoonrakerService.kt` lines 140-157):
```kotlin
val metadataHolder = PrintMetadataHolder(serviceScope, store.printerState) { filename ->
    runCatching {
        rpc.request(JsonRpcMethods.FILES_METADATA, buildJsonObject { put("filename", filename) })
    }.getOrNull()
}

val lastJobHolder = LastJobHolder(serviceScope, store.printerState) {
    runCatching {
        rpc.request(
            JsonRpcMethods.HISTORY_LIST,
            buildJsonObject { put("limit", 1); put("order", "desc") },
        )
    }.getOrNull()
}
```

**Recovery dispatch pattern** (`MoonrakerService.kt` lines 176-183):
```kotlin
container.bindSessionControl(object : SessionControl {
    override fun requestReconnectNow() = session.requestReconnectNow()
    override fun restartFirmware() = dispatcher.dispatch("fw_restart", JsonRpcMethods.FIRMWARE_RESTART)
    override fun restartHost() = dispatcher.dispatch("host_restart", JsonRpcMethods.RESTART)
})
```

Convert reads and recovery actions to registry helpers without changing service ownership: one `JsonRpcClient`, one `CommandDispatcher`, one immutable `SpineHandle` publication.

---

### UI call sites: `PrintStatusScreen.kt`, `TemperatureScreen.kt`, `MoveScreen.kt`, `ExtrudeScreen.kt` (component, event-driven/request-response)

**Analogs:** same files

**Shared dispatcher collection and failure toast** (`TemperatureScreen.kt` lines 101-137):
```kotlin
val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
val inFlight by remember(dispatcher) {
    dispatcher?.inFlight ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())
}.collectAsStateWithLifecycle(initialValue = emptySet())

var failureText by remember { mutableStateOf<String?>(null) }

LaunchedEffect(dispatcher) {
    failureText = null
    val d = dispatcher ?: return@LaunchedEffect
    d.events.collect { event ->
        when (event) {
            is DispatchEvent.Failure -> failureText = event.message
        }
    }
}

fun script(key: String, gcode: String) {
    if (key in inFlight) return
    dispatcher?.dispatch(key, JsonRpcMethods.GCODE_SCRIPT, PrinterCommands.scriptParams(gcode))
}
```

**Move G-Code action pattern** (`MoveScreen.kt` lines 154-159, 196-230):
```kotlin
onJog = { axis, mm, feed ->
    if (forceMove) script("jog_$axis", PrinterCommands.forceMove(axis, mm, feed / 60))
    else script("jog_$axis", PrinterCommands.jog(axis, mm, feed))
},
onHomeXY = { script("home_xy", PrinterCommands.homeXY()) },
onHomeAxis = { axis -> script("home_$axis", PrinterCommands.homeAxis(axis)) },

onConfirm = {
    dispatcher?.dispatch(
        "disable_steppers",
        JsonRpcMethods.GCODE_SCRIPT,
        PrinterCommands.scriptParams(PrinterCommands.DISABLE_STEPPERS),
    )
}
```

**Extrude live gate and macro pattern** (`ExtrudeScreen.kt` lines 163-178, 261-275):
```kotlin
fun script(key: String, gcode: String) {
    if (key in inFlight) return
    dispatcher?.dispatch(key, JsonRpcMethods.GCODE_SCRIPT, PrinterCommands.scriptParams(gcode))
}

onExtrude = { script("extrude", PrinterCommands.extrude(distance, speed * 60)) },
onRetract = { script("retract", PrinterCommands.extrude(-distance, speed * 60)) },

if (vm.hasLoadMacro) script("load", PrinterCommands.loadFilament())
else infoText = "No LOAD_FILAMENT macro configured"
```

**E-stop pattern** (`PrintStatusScreen.kt` lines 181-197):
```kotlin
StopButton(
    onTap = { showEstopGuard = true },
    onHold = { dispatcher?.dispatch("estop", JsonRpcMethods.EMERGENCY_STOP) },
)

onConfirm = {
    dispatcher?.dispatch("estop", JsonRpcMethods.EMERGENCY_STOP)
    showEstopGuard = false
}
```

Refactor UI to call named registry commands, but keep local in-flight checks, `ConfirmGuard` boundaries, missing-macro informational toasts, and `DispatchEvent.Failure` toast collection.

---

### Command and registry tests (test, batch/transform/request-response)

**Analogs:** `PrinterCommandsTest.kt`, `CommandDispatcherTest.kt`

**Byte-identical builder test pattern** (`PrinterCommandsTest.kt` lines 20-47):
```kotlin
@Test
fun setHeater_exactString() {
    assertEquals(
        "SET_HEATER_TEMPERATURE HEATER=extruder TARGET=200",
        PrinterCommands.setHeater("extruder", 200),
    )
}

@Test
fun jog_exactSaveRestoreBody() {
    assertEquals(
        "SAVE_GCODE_STATE NAME=dd_jog\nG91\nG1 X10.0 F3000\nRESTORE_GCODE_STATE NAME=dd_jog",
        PrinterCommands.jog("X", 10.0, 3000),
    )
}
```

**Payload serialization test pattern** (`PrinterCommandsTest.kt` lines 132-137):
```kotlin
@Test
fun scriptParams_serializesToScriptObject() {
    val el = PrinterCommands.scriptParams("M84")
    assertEquals("{\"script\":\"M84\"}", MoonrakerJson.encodeToString(JsonObject.serializer(), el as JsonObject))
    assertEquals(JsonPrimitive("M84"), el["script"])
}
```

**Dispatcher timeout/redaction tests to preserve** (`CommandDispatcherTest.kt` lines 245-269, 349-367):
```kotlin
dispatcher.dispatch("home_z", JsonRpcMethods.GCODE_SCRIPT)
dispatcher.dispatch("estop", JsonRpcMethods.EMERGENCY_STOP)

assertEquals(CommandDispatcher.GCODE_TIMEOUT_MS, rpc.timeouts[gcodeIdx])
assertEquals(CommandDispatcher.DEFAULT_TIMEOUT_MS, rpc.timeouts[estopIdx])

val failure = events.filterIsInstance<DispatchEvent.Failure>().first()
assertFalse("API key/token must never appear in a toast message", failure.message.contains("SECRETKEY"))
assertFalse(failure.message.contains("token="))
```

Add tests that compare registry-produced method/params against direct `PrinterCommands.*` output for representative and clamp-boundary inputs. Add catalog-link and predicate-drift tests by parsing `docs/commands/catalog.json` and `printer-matrix.json`.

---

### State and handshake tests (test, transform/request-response)

**Analogs:** `DeriveCapabilitiesTest.kt`, `HandshakeTest.kt`

**Capability derivation test pattern** (`DeriveCapabilitiesTest.kt` lines 33-45, 88-98):
```kotlin
@Test
fun fullPrinterDerivesFullCapabilities() {
    val caps = deriveCapabilities(goldenObjects())

    assertTrue("full printer has a bed", caps.hasBed)
    assertTrue("macros detected", caps.macros.isNotEmpty())
    assertTrue("heater_bed among heaters", "heater_bed" in caps.heaters)
    assertTrue("powerDevices empty (A4)", caps.powerDevices.isEmpty())
}

@Test
fun hasMacroIgnoreCaseMatchesLowercasedMoonrakerName() {
    val caps = deriveCapabilities(listOf("gcode_macro load_filament"))
    assertTrue("LOAD_FILAMENT matches load_filament", caps.hasMacroIgnoreCase("LOAD_FILAMENT"))
}
```

**Handshake order test pattern** (`HandshakeTest.kt` lines 53-65):
```kotlin
assertEquals(
    listOf(
        JsonRpcMethods.IDENTIFY,
        JsonRpcMethods.OBJECTS_LIST,
        JsonRpcMethods.OBJECTS_QUERY,
        JsonRpcMethods.OBJECTS_SUBSCRIBE,
        JsonRpcMethods.TEMPERATURE_STORE,
        JsonRpcMethods.OBJECTS_QUERY,
    ),
    handshakeMethods,
)
```

Extend `DeriveCapabilitiesTest` for `objects` retention and `hasObject()`. Extend `HandshakeTest` after registry refactor so the method order remains identical even though calls go through registry helpers.

## Shared Patterns

### Headless Spine Purity
**Source:** `PrinterCommands.kt`, `Capabilities.kt`, `DeriveCapabilities.kt`  
**Apply to:** command registry, command specs, capability predicates

```kotlin
// PrinterCommands.kt lines 7-13
// PURE gcode builders ... NO I/O, NO coroutines, NO Compose;
// same input -> same output; fully host-testable off-hardware.

// DeriveCapabilities.kt lines 20-21
fun deriveCapabilities(objects: List<String>): Capabilities {
    val ext = objects.filter(::isExtruder)
```

### JSON-RPC Loose Payloads
**Source:** `JsonRpc.kt`  
**Apply to:** command params, JSON docs tests, registry request helpers

```kotlin
// JsonRpc.kt lines 83-87
val MoonrakerJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}
```

### Centralized Dispatch Semantics
**Source:** `CommandDispatcher.kt`  
**Apply to:** all UI action sends and registry dispatch helpers

```kotlin
// CommandDispatcher.kt lines 118-124
val perCmdTimeout = if (method == JsonRpcMethods.GCODE_SCRIPT) GCODE_TIMEOUT_MS else timeoutMs

// CommandDispatcher.kt lines 142-149
} catch (e: RpcError) {
    _events.tryEmit(DispatchEvent.Failure(key, e.message ?: method))
}
```

### Runtime Gating
**Source:** `Capabilities.kt`, `DeriveCapabilities.kt`, `ExtrudeScreen.kt`
**Apply to:** `AvailabilityPredicate`, command registry, later phase panels

```kotlin
// Capabilities.kt lines 36-42
fun hasMacroIgnoreCase(name: String): Boolean = macros.any { it.equals(name, ignoreCase = true) }

// ExtrudeScreen.kt lines 263-265
if (vm.hasLoadMacro) script("load", PrinterCommands.loadFilament())
else infoText = "No LOAD_FILAMENT macro configured"
```

### Live Evidence Documentation
**Source:** `docs/moonraker-capabilities.md`  
**Apply to:** command Markdown docs and printer matrix

```markdown
<!-- docs/moonraker-capabilities.md lines 21-26 -->
## Ender 5 Plus (`ender5plus`, 192.168.1.120:7125) — captured 2026-06-01

- Klipper `v0.13.0-662` · Moonraker `v0.10.0` (api 1.5.0)
- **Moonraker components present** (`server.info.components`): includes `spoolman`, `history`,
```

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `docs/commands/catalog.json` | docs/config fixture | batch/transform | No existing committed machine-readable docs catalog; use JSON fixture loader/test patterns. |
| `docs/commands/printer-matrix.json` | docs/config fixture | batch/transform | No existing structured printer matrix; derive shape from phase decisions and `docs/moonraker-capabilities.md`. |
| `docs/commands/spoolman-api.md` | docs | reference/transform | No existing Spoolman-specific docs in repo; use `moonraker-capabilities.md` format and upstream references from research. |

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/**`, `app/src/test/java/works/mees/dinghy/**`, `app/src/test/resources/golden/**`, `docs/**`  
**Files scanned:** 70+ source/docs/test files via `find`/`rg`; 14 analog files read with line numbers  
**Pattern extraction date:** 2026-06-01
