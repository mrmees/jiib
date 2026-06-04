# Phase 11: Spool Management — Spoolman + Camera QR - Pattern Map

**Mapped:** 2026-06-04
**Files analyzed:** 24 (15 new, 9 modified)
**Analogs found:** 22 / 24 (2 net-new: camera/permission stack)

This map answers "what existing code should each new file copy from." All analog paths are absolute-from-repo-root under `app/src/main/java/works/mees/dinghy/`. Package root is `works.mees.dinghy`. Everything routes through the established idioms verified by direct read: tolerant `MoonrakerJson` `JsonElement`-walk parsing, the `CommandSpec`/`CommandRegistry` registration helpers, the `WebcamsHolder`→`SpineHandle`→`AppContainer` one-shot-edge-driven-flow chain, the Files dense-list Views-in-Compose pinned-height scroll lesson, and the webcam `DisposableEffect` lifecycle-release discipline.

## File Classification

### NEW files

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `spool/SpoolmanModels.kt` | model | transform | `state/WebcamModels.kt` (model half) | exact |
| `spool/SpoolmanParsers.kt` | model+parser | transform | `state/WebcamModels.kt` (`parseWebcamsList`) + `state/PrintMetadata.kt` (`parsePrintMetadata`) | exact |
| `spool/SpoolmanClient.kt` | service (REST/proxy client) | request-response | `ui/files/FileBrowserClient.kt` + `command/CommandDispatchExtensions.kt` `JsonRpcClient.request` | role-match |
| `spool/ActiveSpoolFacade.kt` | service (session facade) | event-driven | `webcam/WebcamsHolder.kt` | exact |
| `spool/SpoolmanNotifyRouting.kt` (or inline in JsonRpcClient) | net (router) | pub-sub | `net/JsonRpcClient.kt` `dispatch()` notify branch | exact |
| `ui/spool/SpoolScreen.kt` | component (screen) | request-response | `ui/files/FilesScreen.kt` + `ui/webcam/WebcamScreen.kt` | exact |
| `ui/spool/SpoolPicker.kt` | component (dense list + chips) | CRUD | `ui/files/FilesScreen.kt` `FileBrowserField`/`FileListView` | role-match |
| `ui/spool/SpoolHolder.kt` | store (page holder) | event-driven | `ui/files/FileBrowserHolder.kt` | exact |
| `ui/spool/ActiveSpoolCard.kt` | component (Status card) | request-response | `ui/printstatus/PrintStatusScreen.kt` `LastJobCard` | exact |
| `ui/spool/scan/ScanSurface.kt` | component (camera surface) | streaming | `ui/webcam/WebcamScreen.kt` `DisposableEffect` + `AndroidView` host | role-match (net-new hardware) |
| `ui/spool/scan/QrCodeAnalyzer.kt` | utility (frame decoder) | streaming | RESEARCH Pattern 1 (no repo analog) | **no analog** |
| `ui/spool/scan/QrPayloadParser.kt` | utility (pure parser) | transform | `ui/files/DeleteGate.kt` (pure host-tested predicate idiom) | role-match |
| `ui/spool/scan/CameraPermission.kt` | utility (state machine) | event-driven | `ui/route/TopRoute.kt` `derive()` (pure state derivation) | role-match (net-new API) |
| `ui/spool/PrintStartGate.kt` | utility (pure gate) | transform | `ui/files/DeleteGate.kt` `deleteAllowed` | exact |
| `ui/spool/MeasuredWeightPage.kt` (thin wrapper) | component (numeric entry) | request-response | `designsystem/NumpadPage.kt` | exact (reuse, don't fork) |

### MODIFIED files

| Modified File | Change | Pattern Source (in same file) |
|---------------|--------|-------------------------------|
| `state/PrintMetadata.kt` | extend `FilePreviewMetadata` with `filament_type[]`/`name[]`/`colors[]`/`weights[]` | existing `parseFilePreviewMetadata` null-safe walk |
| `net/JsonRpc.kt` | add 2 notify + 4 method-name constants to `JsonRpcMethods` | existing `NOTIFY_*` / `WEBCAMS_LIST` consts |
| `net/JsonRpcClient.kt` | route the 2 spoolman notifications in `dispatch()` `when(method)` | existing `NOTIFY_STATUS_UPDATE`/`NOTIFY_GCODE_RESPONSE` branches + `statusDiff`/`gcodeLine` helpers |
| `command/CommandRegistry.kt` | add `spoolmanStatus`/`getSpoolId`/`postSpoolId`/`proxy` specs + append to `all` | existing `jsonRpc(...)` helper + `webcamsList` spec |
| `command/CommandRegistry.kt` (args) | add `SetSpoolArgs`/`SpoolmanProxyArgs` data classes | existing `PrintStartArgs`/`FileDirectoryArgs` |
| `di/SpineHandle.kt` | add `activeSpoolStatus`/`activeSpool` StateFlow fields | existing `webcams`/`metadata`/`lastJob` fields |
| `di/AppContainer.kt` | add derived `spoolman`/`activeSpool` flows + `spoolmanPresent` gate | existing `webcams`/`webcamCount` `flatMapLatest`/`map` flows |
| `ui/route/TopRoute.kt` | add `Dest.Spool` to the enum | existing `Dest.Webcam` |
| `ui/shell/AppDrawer.kt` | add Spool `DrawerTileSpec` + capability gate input | existing Webcam tile `DrawerTileSpec(... beta=)` + `webcamEnabled` gating |
| `ui/files/FilesScreen.kt` | hook the warn-only gate into the `FileGuard.Start` `ConfirmGuard` | existing `ConfirmGuard` + `selectedFileDetails` |
| `gradle/libs.versions.toml` | add CameraX 1.5.x + zxing core 3.3.3 versions+libraries | existing `[versions]`/`[libraries]` pin idiom |
| `app/build.gradle.kts` | add the 5 camera/zxing `implementation(...)` lines | existing dependencies block |
| `AndroidManifest.xml` | add CAMERA permission + 2 `uses-feature required=false` | existing `uses-permission` block |

## Pattern Assignments

### `spool/SpoolmanModels.kt` (model, transform)

**Analog:** `state/WebcamModels.kt` (the `@Serializable data class Webcam` half).

The CONTEXT-supplied data model (`docs/view_specific_notes/spoolman.md` §Suggested Data Model) is the target shape. Mirror `Webcam`'s exact conventions: every wire field nullable-or-defaulted, snake_case mapped via `@SerialName`, derived display helpers as computed `val`s (the `safeRotation`/`hasSnapshot` precedent → `colorHex` normalization + `multiColorHexes` split here).

**`@Serializable` + `@SerialName` null-safe field idiom** (`WebcamModels.kt` lines 25-53):
```kotlin
@Serializable
data class Webcam(
    val name: String = "",
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("snapshot_url") val snapshotUrl: String? = null,
    val rotation: Int = 0,
    @SerialName("extra_data") val extraData: JsonObject = JsonObject(emptyMap()),
) {
    val safeRotation: Int get() = if (rotation in setOf(0, 90, 180, 270)) rotation else 0
    val hasSnapshot: Boolean get() = !snapshotUrl.isNullOrBlank()
}
```
Apply to: `SpoolmanSpool` (`@SerialName("remaining_weight")` etc.), `SpoolmanFilament` (`@SerialName("color_hex")`, `multi_color_hexes`, `settings_extruder_temp`), `SpoolmanVendor`, `SpoolmanStatus` (`spoolman_connected`, `spool_id`, `pending_reports`), `PendingSpoolmanReport`. The D-08 color-normalize and `multi_color_hexes`-split belong here as computed `val`s exactly like `safeRotation`. `extra` is `Map<String,String>` defaulted to empty (mirror `extraData`).

### `spool/SpoolmanParsers.kt` (model+parser, transform)

**Analog 1 (top-level tolerant walk):** `state/WebcamModels.kt` `parseWebcamsList` (lines 113-123).
**Analog 2 (field-by-field null-safe pull):** `state/PrintMetadata.kt` `parsePrintMetadata` (lines 58-69).

**Tolerant decode-per-entry, drop-the-bad-one idiom** (`WebcamModels.kt` lines 113-123):
```kotlin
fun parseWebcamsList(result: JsonElement?): List<Webcam> {
    if (result == null) return emptyList()
    return runCatching {
        val array = (result as? JsonObject)?.get("webcams")?.jsonArray ?: return emptyList()
        array.mapNotNull { entry ->
            runCatching { MoonrakerJson.decodeFromJsonElement(Webcam.serializer(), entry) }.getOrNull()
        }
    }.getOrDefault(emptyList())
}
```
Apply to the proxy v2 envelope: walk `{response, error, response_headers}`, treat `error: null` as success (NOT "no data" — RESEARCH anti-pattern), pull `X-Total-Count` from `response_headers`, then `mapNotNull` each spool row via `MoonrakerJson.decodeFromJsonElement(SpoolmanSpool.serializer(), entry)`. One bad row drops, the rest survive. A malformed envelope yields an empty result, never throws.

**Field-by-field `runCatching{...jsonPrimitive...}.getOrNull()` idiom** (`PrintMetadata.kt` lines 59-61) — use this for `SpoolmanStatus` (the `server.spoolman.status` reply is hand-walked, not a full `@Serializable` decode):
```kotlin
val layerCount = runCatching { result["layer_count"]?.jsonPrimitive?.intOrNull }.getOrNull()
```

**Goldens:** Wave-0 tests parse the verbatim `docs/commands/spoolman-live-*.json` fixtures (per RESEARCH §Wave 0 Gaps); assert `X-Total-Count == "7"` against `spoolman-live-ender5-proxy-pla.json`, multi-color split against `…-proxy-spool3.json`. The shared `MoonrakerJson` instance (`net/JsonRpc.kt` lines 83-87, `ignoreUnknownKeys`/`isLenient`) is the ONE Json — do not build a new `Json {}`.

### `spool/SpoolmanClient.kt` (service, request-response)

**Analog:** `ui/files/FileBrowserClient.kt` (interface + `MoonrakerFileBrowserClient` impl) + the `JsonRpcClient.request(command, args)` extension.

D-07/Discretion: a SMALL proxy client, NOT forced through `CommandDispatcher`. It wraps the existing session `JsonRpcClient` and issues `server.spoolman.proxy` requests. Mirror `MoonrakerFileBrowserClient`'s "interface of suspend reads returning `JsonElement?`, each `runCatching{rpc.request(spec, args)}.getOrNull()`" shape.

**Interface + impl idiom** (`FileBrowserClient.kt` lines 15-43):
```kotlin
interface FileBrowserClient {
    suspend fun getDirectory(path: String?, extended: Boolean = true): JsonElement? = null
}
class MoonrakerFileBrowserClient(private val rpc: JsonRpcClient, private val dispatcher: CommandDispatcher) : FileBrowserClient {
    override suspend fun getDirectory(path: String?, extended: Boolean): JsonElement? =
        runCatching { rpc.request(CommandRegistry.filesGetDirectory, FileDirectoryArgs(path, extended)) }.getOrNull()
}
```
Apply: `getSpool(id)`, `listSpools(query)`, `listFilaments(query)`, `listMaterials()`/`listVendors()`/`listLocations()`, `measureSpool(id, grossGrams)`, each building a `SpoolmanProxyArgs(use_v2_response=true, request_method, path, query)` and calling `rpc.request(CommandRegistry.spoolmanProxy, args)`. **D-07/Pitfall 5:** URL-encode dotted query keys (`filament.material`) in this layer before they hit the proxy `query` string — assert against `…-proxy-pla.json`.

### `spool/ActiveSpoolFacade.kt` (service, event-driven)

**Analog:** `webcam/WebcamsHolder.kt` (the whole class — exact template for "session-scoped, edge-driven, best-effort, published off SpineHandle").

This owns active-spool truth via Moonraker JSON-RPC (`server.spoolman.status`/`get_spool_id`/`post_spool_id`) AND reconciles the two notifications (D-10). Copy the rising-edge `!Connected → Connected` one-shot-per-handshake fetch pattern for the initial `status` read.

**Edge-driven once-per-handshake best-effort idiom** (`WebcamsHolder.kt` lines 51-77):
```kotlin
class WebcamsHolder(scope, connectionState: StateFlow<ConnectionState>, private val fetch: suspend () -> JsonElement?) {
    private var wasConnected = false
    private val _webcams = MutableStateFlow<List<Webcam>>(emptyList())
    val webcams: StateFlow<List<Webcam>> = _webcams.asStateFlow()
    init {
        scope.launch {
            connectionState.collect { state ->
                val connected = state is ConnectionState.Connected
                if (connected && !wasConnected) {
                    val result = runCatching { fetch() }.getOrNull()
                    _webcams.value = parseWebcamsList(result)
                }
                wasConnected = connected
            }
        }
    }
}
```
Apply: an `_activeSpool: MutableStateFlow<SpoolmanStatus?>` re-fetched on the handshake edge AND updated on `notify_active_spool_set` / `notify_spoolman_status_changed` (the facade subscribes to a notify SharedFlow — see next file). Keep it plain Kotlin (injectable `fetch` + substitutable `StateFlow<ConnectionState>`) so it is host-unit-testable, exactly like `WebcamsHolder`. D-10 reconcile: on a pushed id, overwrite local state with the pushed value (do not re-assert stale local).

### `spool/SpoolmanNotifyRouting.kt` / inline in `net/JsonRpcClient.kt` (net, pub-sub)

**Analog:** `net/JsonRpcClient.kt` `dispatch()` notification branch (lines 178-194) + the `statusDiff`/`gcodeLine` 1-element-array extractors (lines 217-224).

**`when(method)` route + `tryEmit` to a bounded SharedFlow idiom** (`JsonRpcClient.kt` lines 178-194):
```kotlin
val method = obj["method"]?.jsonPrimitive?.contentSafe() ?: return
when (method) {
    JsonRpcMethods.NOTIFY_STATUS_UPDATE -> statusDiff(obj)?.let { _statusUpdates.tryEmit(it) }
    JsonRpcMethods.NOTIFY_KLIPPY_READY, ... -> _klippyEvents.tryEmit(method)
    else -> Unit
}
```
**1-element-array extractor** (D-10: Moonraker sends `params` as a 1-element array — `[{spool_id:3}]`), mirror `statusDiff` (lines 217-219):
```kotlin
private fun spoolNotifyParam(obj: JsonObject): JsonObject? = runCatching {
    obj["params"]?.jsonArray?.firstOrNull()?.jsonObject
}.getOrNull()
```
Add two bounded `MutableSharedFlow` fields (`activeSpoolSet`, `spoolmanStatusChanged`) following the `_statusUpdates`/`_klippyEvents` precedent (lines 60-74, bounded `extraBufferCapacity`), expose `asSharedFlow()`, and add two `when` arms keyed on the new `JsonRpcMethods` constants. Unmodeled `notify_*` already falls through `else -> Unit` (line 193) so unrelated notifies are ignored — golden-test `…-notify.json` asserts `notify_proc_stat_update` is ignored.

### `ui/spool/ActiveSpoolCard.kt` (component, request-response)

**Analog:** `ui/printstatus/PrintStatusScreen.kt` `LastJobCard` (lines 449-531) + `LastJobStatRow` (lines 538-548) + the existing `parseHexColor` color-swatch helper.

D-03: compact card, material / color swatch / vendor·name / remaining / state. The `LastJobCard` is the EXACT precedent — it already renders a filament `type · name` row with a color swatch and icon-led stat rows, all via `LocalTokens`.

**Icon-led stat row + color swatch idiom** (`PrintStatusScreen.kt` lines 499-505, 538-548):
```kotlin
if (job.filamentType != null || job.filamentName != null) {
    LastJobScrollRow(
        symbol = "palette",
        text = listOfNotNull(job.filamentType, job.filamentName).joinToString(" · "),
        swatch = job.filamentColor?.let { parseHexColor(it) },
        widthModifier = Modifier.fillMaxWidth(),
    )
}
```
Apply: reuse `parseHexColor` (D-08 accept with/without `#`, 6/8 hex → else neutral marker — verify it does this; if not, the normalization belongs in `SpoolmanModels`'s computed `val`). The card's quick actions (`Scan`/`Change`/`Clear`) use `OutlinedControl` with `Intent` colors (see Shared Patterns → Button Intent). State variants (unavailable/disconnected/no-active/loading/loaded/stale/pending/changed) are a `when` over the facade's `SpoolmanStatus?` + capability flow.

### `ui/spool/SpoolScreen.kt` + `SpoolPicker.kt` (component, CRUD / request-response)

**Analog:** `ui/files/FilesScreen.kt` (Focus=preview / Field=dense list / Gutter, portrait collapse) + `ui/webcam/WebcamScreen.kt` (`BoxWithConstraints` orientation collapse).

D-02/Discretion: picker = Focus(selected spool detail) | Field(dense scrollable list + chips) | Gutter, portrait/landscape collapse. Files is the picker template.

**ScreenScaffold Focus/Field/Gutter + portrait-collapse idiom** (`FilesScreen.kt` lines 91-146):
```kotlin
BoxWithConstraints(Modifier.fillMaxSize()) {
    val showFocus = maxWidth > maxHeight || selected != null
    ScreenScaffold(
        focus = if (showFocus) { { FilePreviewFocus(...) } } else null,
        field = { FileBrowserField(state, ..., onRowClick = { ... }) },
        gutter = { Row(...) { /* Cancel + Print intent controls */ } },
    )
}
```

**⚠ Views-in-Compose dense-list scroll lesson (Phase-7, load-bearing)** (`FilesScreen.kt` lines 202-211): a `RecyclerView` hosted via `AndroidView` over-measures (wraps all rows) when handed a loose height and composites OVER its Compose neighbors. **Pin it to an EXACT height** inside a `BoxWithConstraints`:
```kotlin
BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
    FileListView(..., modifier = Modifier.fillMaxWidth().height(maxHeight))
}
```
If the spool list reuses a RecyclerView (the FileListView pattern) apply this verbatim. If it is a Compose `LazyColumn` the lesson is moot but the Focus/Field/Gutter collapse still applies. The webcam `CamPicker` (`WebcamScreen.kt` lines 169-221) is the pure-Compose dense-list-with-expanded-selected-row precedent if you go Compose. **MEMORY note:** Files Delete-blocks-all-during-print is a known deferred defect — do not copy a print-state-gating bug into the picker.

**Holder:** `SpoolHolder.kt` mirrors `ui/files/FileBrowserHolder.kt` (lines 32-80) — `MutableStateFlow<…State>` + `asStateFlow()`, `scope.launch{}` reaction to upstream flows, `runCatching` reads that degrade to `null`/empty, suspend mutators (`selectSpool`, `applyFilters`).

### `ui/spool/scan/ScanSurface.kt` (component, streaming)

**Analog:** `ui/webcam/WebcamScreen.kt` `DisposableEffect` lifecycle release (lines 80-84) + the `AndroidView`-host discipline. **The release pattern, not the code, transfers** (webcam is network MJPEG; this is the first on-device hardware camera).

**DisposableEffect start/stop release idiom** (`WebcamScreen.kt` lines 80-84):
```kotlin
DisposableEffect(holder) {
    holder.start()
    onDispose { holder.stop() }
}
```
Apply (RESEARCH Pattern 2) — bind CameraX to the lifecycle and `unbindAll()` on dispose so the camera releases on pause/background/nav-away (D-14):
```kotlin
DisposableEffect(lifecycleOwner) {
    val provider = ProcessCameraProvider.getInstance(ctx).get()
    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
    onDispose { provider.unbindAll() }
}
```
Host the `PreviewView` via `AndroidView` (same hybrid-host approach as `FileListView`/`WebcamViewHost`). Gutter = Back-only red `Intent.Danger` (mirror `WebcamScreen` lines 119-131). D-14: `DEFAULT_BACK_CAMERA` first, NEVER hard-code front; `STRATEGY_KEEP_ONLY_LATEST`. Full-screen sub-surface launched from the Spool screen / Status card (NOT a drawer `Dest`).

### `ui/spool/scan/QrCodeAnalyzer.kt` (utility, streaming) — NO ANALOG

No existing codebase analog (first ImageAnalysis.Analyzer / first ZXing use). Copy RESEARCH Pattern 1 verbatim (`11-RESEARCH.md` lines 204-222): `MultiFormatReader` hinted QR-only, `planes[0].buffer` → `PlanarYUVLuminanceSource(data, rowStride, height, …)` (Pitfall: `rowStride` NOT `width`) → `HybridBinarizer` → `decodeWithState`, `catch(NotFoundException)` no-op, **`image.close()` in `finally`** (KEEP_ONLY_LATEST stalls otherwise). The pure-Kotlin discipline of the headless layer still applies: keep `onResult: (String) -> Unit` injectable so the parser side is unit-tested headless.

### `ui/spool/scan/QrPayloadParser.kt` (utility, transform)

**Analog:** `ui/files/DeleteGate.kt` `deleteAllowed` (the pure, host-tested, single-purpose predicate idiom).

**Pure host-tested function idiom** (`DeleteGate.kt` lines 22-32) — no Android, no I/O, fully unit-testable, exhaustive doc of the contract:
```kotlin
fun deleteAllowed(selectedPath: String?, activePrintFilename: String, printState: PrintState): Boolean {
    if (selectedPath == null) return false
    ...
}
```
Apply (D-12, Security V5 — untrusted input): `fun parseSpoolId(payload: String): SpoolQrResult` returning a sealed result (`Spool(id: Int)` / `UnsupportedSpoolmanCode` / `NotASpoolCode`). Accept `web+spoolman:s-<digits>` case-insensitively (incl. `WEB+SPOOLMAN:S-`) AND `http(s)://…/spool/show/<digits>` (id-only, NEVER trust the host); reject `f-`/other schemes/UPC/EAN/non-numeric. Unit-test all four accept/reject classes (RESEARCH Wave 0 `QrPayloadParserTest`).

### `ui/spool/scan/CameraPermission.kt` (utility, event-driven)

**Analog:** `ui/route/TopRoute.kt` `derive()` pure state-derivation (lines 57-62) for the headless state machine; RESEARCH Pattern 3 for the Activity Result wiring (no repo analog — first runtime permission).

The headless `ScanState` machine (granted/denied/no-camera/busy/unreadable/no-QR/unsupported) is a pure `when`-derived enum like `derive()` — host-testable (`ScanStateMachineTest`). The `rememberLauncherForActivityResult(RequestPermission())` wiring (RESEARCH lines 239-242) is net-new Compose glue. All denial/no-camera paths route to a degrade state with "Use picker instead" (D-15: manual picker always works).

### `ui/spool/PrintStartGate.kt` (utility, transform)

**Analog:** `ui/files/DeleteGate.kt` `deleteAllowed` (exact — same pure-predicate role, same file directory).

D-01 warn-only gate. A pure function taking (active `SpoolmanSpool?`, `SpoolmanStatus`, extended `FilePreviewMetadata`) → an ordered list of amber warnings (or pass). Each D-01 condition (no-spool, material family-mismatch per D-05, remaining<needed+margin, archived, pending_reports stale, fetch-failed) is a branch. Host-tested decision table (`PrintStartGateTest`). **Never blocks** — returns warnings the confirm flow taps past. Skip the low-filament check when `filamentWeightTotal == null`.

### `ui/spool/MeasuredWeightPage.kt` (component, request-response)

**Analog:** `designsystem/NumpadPage.kt` (REUSE, do not fork).

`NumpadPage` (lines 70+) already is the full-screen single-bounded-numeric-entry primitive (`label`, `initial`, `range`, `unit`, `allowDecimal`, `onCancel`, `onSet(value)`). D-04 measured gross-weight = a thin caller that opens `NumpadPage` with `label="Gross weight"`, `unit="g"`, `allowDecimal=true`, and on `onSet` calls `SpoolmanClient.measureSpool(id, grams)`. The page itself must SHOW that `remaining`/`used` are linked (`used = initial − remaining`, D-04/Pitfall 7) — that is caller-side copy, not a NumpadPage change.

## Modified-File Pattern Assignments

### `state/PrintMetadata.kt` (extend `FilePreviewMetadata`)

Add `filamentType: List<String>`, `filamentName: List<String>`, `filamentColors: List<String>`, `filamentWeights: List<Double>` to `FilePreviewMetadata` (lines 36-49). In `parseFilePreviewMetadata` (lines 71-84) pull each array with the existing null-safe walk. Mirror `largestThumbRelPath`'s array iteration (lines 94-107) for the `JsonArray` reads:
```kotlin
filamentType = runCatching {
    (result["filament_type"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
}.getOrNull().orEmpty(),
```
Keep every walk `runCatching{...}.getOrNull()` — a missing array yields empty, never throws (existing file contract).

### `net/JsonRpc.kt` `JsonRpcMethods`

Add to the object (mirror the `WEBCAMS_LIST` + `NOTIFY_*` const block, lines 124, 139-144):
```kotlin
const val SPOOLMAN_STATUS = "server.spoolman.status"
const val SPOOLMAN_GET_SPOOL_ID = "server.spoolman.get_spool_id"
const val SPOOLMAN_POST_SPOOL_ID = "server.spoolman.post_spool_id"
const val SPOOLMAN_PROXY = "server.spoolman.proxy"
const val NOTIFY_ACTIVE_SPOOL_SET = "notify_active_spool_set"
const val NOTIFY_SPOOLMAN_STATUS_CHANGED = "notify_spoolman_status_changed"
```
**Open-Q2 (RESEARCH):** add catalog entries for the two notifications in `docs/commands/moonraker-api.md` BEFORE wiring the router parser tests.

### `command/CommandRegistry.kt`

Add four specs via the existing private `jsonRpc(...)` helper (lines 505-520), append all four to the `all` list (lines 448-497). The `proxy`/`post_spool_id` are gated `AvailabilityPredicate.ComponentPresent("spoolman")` (the Webcam tile is NOT gated, but spoolman SHOULD be — D-02/the `Capabilities.hasComponent` gate). Pattern, mirror `historyList` (lines 208-219) for an args-carrying spec:
```kotlin
val spoolmanProxy: CommandSpec<SpoolmanProxyArgs> = jsonRpc(
    catalogId = "MR-server.spoolman.proxy",
    method = JsonRpcMethods.SPOOLMAN_PROXY,
    key = { args -> "spoolman_proxy_${args.path}" },
    params = { args -> buildJsonObject {
        put("use_v2_response", true)
        put("request_method", args.method)
        put("path", args.path)
        args.query?.let { put("query", it) }
    } },
    availability = AvailabilityPredicate.ComponentPresent("spoolman"),
)
```
Add `SetSpoolArgs(spoolId: Int?)` (null = clear, D-13 — `post_spool_id {}`) and `SpoolmanProxyArgs(method, path, query)` next to the existing arg classes (lines 26-34). Reads go through `JsonRpcClient.request(spec, args)`; the set/clear goes through `dispatcher.dispatch(spec, args)` (the `CommandDispatchExtensions.kt` extensions, both already verified).

### `di/SpineHandle.kt` + `di/AppContainer.kt`

Add `activeSpool: StateFlow<SpoolmanStatus?>` (and the resolved-detail flow) to `SpineHandle` (mirror the `webcams`/`metadata` fields, lines 72-91). In `AppContainer` add the derived flows + capability gate mirroring `webcams`/`webcamCount` (lines 143-152):
```kotlin
val activeSpool: Flow<SpoolmanStatus?> = spine.flatMapLatest { it?.activeSpool ?: flowOf(null) }
val spoolmanPresent: Flow<Boolean> = capabilities.map { it.hasComponent("spoolman") }
```
`spoolmanPresent` is the D-02 drawer-greying input (same role `webcamCount > 0` plays for Webcam). The service constructs the `ActiveSpoolFacade` and publishes its flows on the handle (service-constructs/UI-consumes, D-02) — the UI never sees a raw `JsonRpcClient`.

### `ui/route/TopRoute.kt`

Add `Spool` to the `Dest` enum (line 31): `enum class Dest { …, Webcam, Spool, Settings }`. The QR scan surface is NOT a `Dest` (D-02 — a sub-surface, owned by the Spool screen's own nav state).

### `ui/shell/AppDrawer.kt`

Add a `DrawerTileSpec(label="Spool", symbol=<unique glyph>, dest=Dest.Spool)` to `DRAWER_TILES` (lines 132-158). D-02 capability-greying mirrors the Webcam runtime gate (lines 176, 69-77): thread a `spoolEnabled: Boolean = false` param (fed `AppContainer.spoolmanPresent`) and fold it into the `live` decision exactly as `webcamEnabled` is (`tile.dest != Dest.Spool || spoolEnabled`). Pick a glyph NOT already in `DRAWER_TILES` (icon-no-repeat law) — e.g. `category` or `inventory_2`. No `beta=true` unless desired (Webcam uses it for the amber dev flag).

### `ui/files/FilesScreen.kt` (gate hook)

The gate hooks into the existing `FileGuard.Start` `ConfirmGuard` (lines 149-163). D-01 warn-only: before showing the plain `Print file` confirm, compute the gate warnings (`PrintStartGate` against the active spool + `state.selectedPreview`). If warnings exist, surface them as amber proceed-at-peril text in the same `ConfirmGuard` (the `message` slot already carries `selectedFileDetails`, lines 433-441) plus the `Pick spool`/`Scan`/`Print anyway`/`Back` actions; a clean pass shows the existing confirm unchanged. The hook is BEFORE `holder.requestStartSelected()` (line 156) — same point `FileBrowserClient.startPrint` dispatches `printer.print.start`.

### `gradle/libs.versions.toml` + `app/build.gradle.kts`

Add to `[versions]` (mirror the pinned-with-comment idiom, lines 18-58):
```toml
cameraX = "1.5.0"      # minSdk 23 == floor; PROVE via verifyMinSdk before locking
zxingCore = "3.3.3"    # NOT 3.4.0+ — decode path uses List.sort (API 24) above 3.3.3
```
Add to `[libraries]` the four `androidx.camera:camera-{core,camera2,lifecycle,view}` (`version.ref = "cameraX"`) and `com.google.zxing:core` (`version.ref = "zxingCore"`), then the five `implementation(libs.…)` lines in `app/build.gradle.kts` dependencies (after line 126). **Run `verifyMinSdkRelease` after** — it asserts merged-manifest minSdk == 23 (RESEARCH Pitfall 2). Do NOT add `coreLibraryDesugaring` (Option A pin avoids it).

### `AndroidManifest.xml`

Add to the `uses-permission` block (after line ~37) — additive, the Phase-1 cleartext posture is untouched:
```xml
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
<uses-permission android:name="android.permission.CAMERA" />
```
`required="false"` is load-bearing (D-15): a camera-less device still installs; scan degrades to picker-only.

## Shared Patterns

### Capability gating (Spoolman present)
**Source:** `state/Capabilities.kt` `hasComponent` (line 55) + `command/CommandSpec.kt` `AvailabilityPredicate.ComponentPresent` (line 23).
**Apply to:** the drawer tile greying (`AppContainer.spoolmanPresent`), the spoolman command specs' `availability`, and every Spool surface's "unavailable" state.
```kotlin
fun hasComponent(name: String): Boolean = name in components
// spec: availability = AvailabilityPredicate.ComponentPresent("spoolman")
```

### Tolerant JSON (the ONE Json + null-safe walk)
**Source:** `net/JsonRpc.kt` `MoonrakerJson` (lines 83-87).
**Apply to:** all Spoolman parsing — never build a per-call `Json {}`; every wire field nullable; one bad row drops, never throws (D-08).

### Button intent colors
**Source:** `ui/files/FilesScreen.kt` `intentColor` (lines 425-431) + `designsystem/control/OutlinedControl` + `Intent`.
**Apply to:** every Spool control. red=`stop`/Danger (Back/Cancel/Clear), green=`go`/Go (Load/Set/accept), amber=`heat`/Warn (proceed-at-peril gate/archived warning), accent=`accentLine` (physical command), via `LocalTokens` (THEME-01 — NO raw color literal).
```kotlin
Intent.Warn -> t.heat   // amber proceed-at-peril (the gate + archived warnings)
Intent.Danger -> t.stop // red Back/Clear
Intent.Go -> t.go       // green Load/Set
```

### Pure host-tested predicate / state derivation
**Source:** `ui/files/DeleteGate.kt`, `ui/route/TopRoute.kt` `derive()`.
**Apply to:** `QrPayloadParser`, `PrintStartGate`, `CameraPermission` `ScanState` machine — all pure, no Android, no I/O, exhaustively unit-tested (the Wave-0 gaps). Matches the Wave-0 RED-must-compile discipline (MEMORY: typed assertions, no refs to unbuilt symbols).

### One-shot-edge-driven flow off SpineHandle/AppContainer
**Source:** `webcam/WebcamsHolder.kt` → `di/SpineHandle.kt` `webcams` → `di/AppContainer.kt` `webcams`/`webcamCount`.
**Apply to:** `ActiveSpoolFacade` → `SpineHandle.activeSpool` → `AppContainer.activeSpool`/`spoolmanPresent`. Service constructs, UI consumes; `flatMapLatest{ it?.flow ?: flowOf(default) }` for the derived flow.

## No Analog Found

| File | Role | Data Flow | Reason | Mitigation |
|------|------|-----------|--------|------------|
| `ui/spool/scan/QrCodeAnalyzer.kt` | utility | streaming | First `ImageAnalysis.Analyzer` / first ZXing use in the repo | Copy RESEARCH Pattern 1 verbatim (`11-RESEARCH.md` lines 204-224); keep `onResult` injectable for headless test |
| `ui/spool/scan/CameraPermission.kt` (Activity Result glue) | utility | event-driven | First runtime-permission flow (Phase-1 only declared INTERNET; camera is the first dangerous perm) | Headless `ScanState` machine follows `derive()`; the `rememberLauncherForActivityResult` wiring is RESEARCH Pattern 3 (no repo precedent) |

Both are net-new BECAUSE this is the project's first on-device hardware-camera surface (the Phase-10 webcam is network-only — RESEARCH is explicit the release *discipline* transfers but the *code* does not). Everything else has a strong in-repo analog.

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/{command,net,di,state,ui/files,ui/webcam,webcam,ui/printstatus,ui/route,ui/shell,designsystem}` + `gradle/libs.versions.toml` + `app/src/main/AndroidManifest.xml` + `app/build.gradle.kts`.
**Files read in full or targeted:** CommandSpec.kt, CommandRegistry.kt, CommandDispatchExtensions.kt, FileBrowserClient.kt, FileBrowserHolder.kt, FilesScreen.kt, DeleteGate.kt, JsonRpc.kt, JsonRpcClient.kt, SnapshotPoller.kt (idiom), WebcamModels.kt, WebcamsHolder.kt, WebcamScreen.kt, SpineHandle.kt, AppContainer.kt, PrintMetadata.kt, Capabilities.kt, TopRoute.kt, AppDrawer.kt, NumpadPage.kt (signature), PrintStatusScreen.kt (LastJobCard), libs.versions.toml, AndroidManifest.xml, build.gradle.kts (deps).
**Pattern extraction date:** 2026-06-04
