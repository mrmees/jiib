<!-- refreshed: 2026-06-08 -->
# Architecture

**Analysis Date:** 2026-06-08

## System Overview

```text
┌─────────────────────────────────────────────────────────────────────────────────┐
│  MainActivity  (one Activity, one setContent{DinghyTheme(…){RootController}} )  │
│  `app/src/main/java/works/mees/dinghy/MainActivity.kt`                          │
└────────────────────────────────┬────────────────────────────────────────────────┘
                                 │ starts FGS, reads AppContainer
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  RootController  (pure Compose routing authority)                                │
│  `ui/shell/RootController.kt`                                                    │
│  derive(hasConfig, printerState) → Connect | Splash | Shell                      │
│  owns ShellNavState above the Splash/Shell split (hoisted for recovery-safety)  │
└──────────┬───────────────────────────┬──────────────────────────────────────────┘
           │ TopRoute.Shell            │ TopRoute.Splash / Connect
           ▼                           ▼
┌──────────────────────┐   ┌─────────────────────┐   ┌────────────────────────┐
│  AppShell            │   │  SplashScreen        │   │  PrintersScreen        │
│  `ui/shell/AppShell` │   │  `ui/screen/Splash`  │   │  (first-run escape)    │
│  when(dest) router   │   └─────────────────────┘   └────────────────────────┘
│  + AppDrawer overlay │
└──────────┬───────────┘
           │  collectAsStateWithLifecycle(container.spine / printerState / …)
           ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  AppContainer  (process-scoped service-locator, NO DI framework)                │
│  `di/AppContainer.kt`                                                            │
│  holds: StateFlow<SpineHandle?> spine  ·  6 DataStore files  ·  writeScope      │
│  derived: printerState / connectionState / capabilities / dispatcher (flatMap)  │
│  theme: effectiveTokens Flow (baked tuple + transient override)                  │
└────────────────────────────────┬────────────────────────────────────────────────┘
                                 │ publishSpine(handle) / publishSystemInfoHolder
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  MoonrakerService  (started FGS, OWNS the spine)                                │
│  `service/MoonrakerService.kt`                                                   │
│  collectLatest(activeConfig) → cancelAndJoin old session → buildSpineAndLaunch  │
│  builds: OkHttpClient · PrinterStateStore · JsonRpcClient · MoonrakerSession    │
│          CommandDispatcher · WebcamsHolder · ActiveSpoolFacade · SpoolmanClient  │
│  publishes: ONE atomic SpineHandle + SystemInfoHolder per config                 │
└────────────────────────────────┬────────────────────────────────────────────────┘
                                 │ session.run()
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  net/  —  Transport + JSON-RPC layer                                            │
│  MoonrakerSocket  `net/MoonrakerSocket.kt`   OkHttp WebSocket → Flow<SocketEvent>│
│  RpcConnection    `net/RpcConnection.kt`     send frames, close idempotently     │
│  JsonRpcClient    `net/JsonRpcClient.kt`     id-correlation · notify routing     │
│    SharedFlows: statusUpdates · klippyEvents · gcodeResponses                    │
│    + activeSpoolSet · spoolmanStatusChanged · procStatUpdates                    │
│  MoonrakerSession `net/MoonrakerSession.kt`  reconnect supervisor + handshake    │
└────────────────────────────────┬────────────────────────────────────────────────┘
                                 │ store.onStatusDiff / store.seed / store.setCapabilities
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  state/  —  Single-source-of-truth state                                        │
│  PrinterStateStore  `state/PrinterStateStore.kt`                                 │
│    HIGH-RATE plane (conflated ~4 Hz): temps, positions, progress                 │
│    CONTROL plane (immediate): ConnectionState, KlippyState, print_stats.state   │
│    ONE-SHOT handshake reads: minExtrudeTemp, temperatureBackfill, macroBodies,  │
│      screwsTiltConfig, outputDescriptors, fineTune baselines (17+), sysInfo     │
│  PrinterState (data class)   `state/PrinterState.kt`  — immutable snapshot      │
│  Capabilities                `state/Capabilities.kt`  — capability flags/sets   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| `DinghyApp` | Process owner of AppContainer + 6 DataStore files | `app/src/main/java/works/mees/dinghy/DinghyApp.kt` |
| `AppContainer` | Process-scoped service-locator; spine publication; writeScope; theme derivation | `di/AppContainer.kt` |
| `SpineHandle` | Immutable atomic snapshot of one live session's flows + dispatcher | `di/SpineHandle.kt` |
| `SessionControl` | Narrow UI surface for reconnect/restart (implemented by service) | `di/SessionControl.kt` |
| `MoonrakerService` | Started FGS; owns socket lifecycle; builds + publishes spine per config | `service/MoonrakerService.kt` |
| `MoonrakerSession` | Reconnect supervisor + 6-step handshake; emits ConnectionState | `net/MoonrakerSession.kt` |
| `JsonRpcClient` | JSON-RPC id-correlation; routes notifications to SharedFlows | `net/JsonRpcClient.kt` |
| `MoonrakerSocket` | OkHttp WebSocket → `Flow<SocketEvent>` bridge | `net/MoonrakerSocket.kt` |
| `PrinterStateStore` | Dual-plane StateFlow accumulator; one-shot seams for handshake reads | `state/PrinterStateStore.kt` |
| `PrinterState` | Immutable toolkit-agnostic snapshot (data class, no Compose annotations) | `state/PrinterState.kt` |
| `Capabilities` | Derived capability flags (heaters, objects, components) per reconnect | `state/Capabilities.kt` |
| `CommandDispatcher` | Debounce + busy-guard + timeout wrapper around `JsonRpcClient.request` | `command/CommandDispatcher.kt` |
| `CommandRegistry` | Typed `CommandSpec` catalogue; the D-10 drift-guard source | `command/CommandRegistry.kt` |
| `RootController` | Single routing authority; owns ShellNavState above Splash/Shell split | `ui/shell/RootController.kt` |
| `AppShell` | Running shell host; `when(dest)` router; builds per-session holders | `ui/shell/AppShell.kt` |
| `ShellNavState` | Nav state hoisted above the Splash/Shell split (back-stack, sub-nav) | `ui/shell/ShellNavState.kt` |
| `AppDrawer` | Swipe-up full-screen nav overlay; capability-gated tiles | `ui/shell/AppDrawer.kt` |
| `GraphViewHost` | `AndroidView<GraphView>` interop seam for live temp graph | `render/GraphViewHost.kt` |
| `FileListView` | `AndroidView<RecyclerView>` interop seam for file browser list | `ui/files/FileListView.kt` |
| `ConsoleListView` | `AndroidView<RecyclerView>` interop seam for console scrollback | `ui/console/ConsoleListView.kt` |
| `BedMeshHeatmapHost` | `AndroidView<BedMeshHeatmapView>` interop seam for bed mesh heatmap | `render/BedMeshHeatmapHost.kt` |
| `Media3SurfaceHost` | `AndroidView<SurfaceView>` interop seam for H.264 ExoPlayer feed | `render/Media3SurfaceHost.kt` |

## Pattern Overview

**Overall:** Headless spine + Compose shell, process-scoped service-locator (no DI framework)

**Key Characteristics:**
- The FGS (`MoonrakerService`) OWNS the connection; the Activity collects state from `AppContainer` — the spine survives rotation.
- `AppContainer` publishes ONE `StateFlow<SpineHandle?>` atomic snapshot; all per-field flows (`printerState`, `capabilities`, `dispatcher`) are `flatMapLatest` derivations — a session rebuild never exposes a partially-swapped mix.
- `PrinterStateStore` uses a dual-plane emission model: high-rate state is conflated to ~4 Hz; control-plane changes (KlippyState, ConnectionState, print lifecycle) are published immediately.
- Navigation uses a lean `when(dest)` switch in `AppShell` — no `androidx.navigation`. The `ShellNavState` is hoisted above `RootController`'s Splash/Shell split so recovery splashes do not bounce the user off their screen.
- All persistence writes route through `AppContainer.writeScope` (a `SupervisorJob + Dispatchers.IO` process-lifetime scope) — never `rememberCoroutineScope()` which is cancelled on same-frame navigation.

## Layers

**Transport (`net/`):**
- Purpose: OkHttp WebSocket → typed events; JSON-RPC id-correlation; notification routing
- Location: `app/src/main/java/works/mees/dinghy/net/`
- Contains: `MoonrakerSocket`, `MoonrakerSession`, `JsonRpcClient`, `RpcConnection`, `Backoff`, `SnapshotPoller`, `WebcamClients`, `WebcamUrl`, `WebcamProbe`, `MjpegStreamDecoder`
- Depends on: `state/` (PrinterStateStore), `auth/`, `command/` (CommandRegistry for handshake RPC specs)
- Used by: `service/MoonrakerService`

**State (`state/`):**
- Purpose: Immutable data model + mutable store; dual-plane emission; one-shot handshake seams
- Location: `app/src/main/java/works/mees/dinghy/state/`
- Contains: `PrinterState`, `PrinterStateStore`, `PrinterStateReducer`, `Capabilities`, `DeriveCapabilities`, `TemperatureStore`, `ConsoleScrollback`, `FileBrowserModels`, `WebcamModels`, `PrintMetadata`, `PrintHistory`, `LastJob`
- Depends on: Nothing above state (deliberately headless)
- Used by: `net/`, `service/`, `di/`, `ui/` feature packages

**Commands (`command/`):**
- Purpose: Typed RPC argument classes, the command catalogue, and the UI-layer dispatch wrapper
- Location: `app/src/main/java/works/mees/dinghy/command/`
- Contains: `CommandRegistry` (all `CommandSpec`s), `CommandSpec`, `CommandDispatcher`, `CommandDispatchExtensions`, `PrinterCommands`, `MacroInvocation`
- Depends on: `net/JsonRpcClient` (for live dispatch)
- Used by: `service/MoonrakerService` (handshake RPC calls), `ui/` screens (via dispatcher)

**DI (`di/`):**
- Purpose: Manual service-locator wiring; spine publication contract
- Location: `app/src/main/java/works/mees/dinghy/di/`
- Contains: `AppContainer`, `SpineHandle`, `SessionControl`, `SpineHandle`
- Depends on: All layers below it
- Used by: `DinghyApp`, `MainActivity`, `MoonrakerService`, `ui/shell/AppShell`

**Service (`service/`):**
- Purpose: FGS that assembles and owns the transport spine
- Location: `app/src/main/java/works/mees/dinghy/service/`
- Contains: `MoonrakerService`
- Depends on: `net/`, `state/`, `command/`, `di/`, `config/`, `auth/`, `spool/`, `ui/files/`, `ui/printstatus/`, `webcam/`
- Used by: `MainActivity` (starts it)

**UI Shell (`ui/shell/`, `ui/route/`):**
- Purpose: Top-level routing, navigation state, shell host composable
- Location: `app/src/main/java/works/mees/dinghy/ui/shell/`, `app/src/main/java/works/mees/dinghy/ui/route/`
- Contains: `RootController`, `AppShell`, `ShellNavState`, `AppDrawer`, `StartDestMapping`, `DevThemeCyclerOverlay`, and routing types `TopRoute`, `Dest`
- Depends on: `di/AppContainer`, all feature UI packages, `render/`
- Used by: `MainActivity`

**UI Features (`ui/<feature>/`):**
- Purpose: Per-screen composables + per-session headless state holders
- Location: `app/src/main/java/works/mees/dinghy/ui/` (15 feature sub-packages)
- Pattern: `<Feature>Screen.kt` (Compose) + `<Feature>Holder.kt` (headless, `StateFlow` out, built on `PrinterStateStore`)
- Depends on: `state/PrinterStateStore`, `di/AppContainer`, `command/CommandDispatcher`, `designsystem/`, `theme/`

**Render (`render/`):**
- Purpose: `AndroidView` host wrappers for the three classic-Views surfaces and the H.264 SurfaceView
- Location: `app/src/main/java/works/mees/dinghy/render/`
- Contains: `GraphViewHost`, `GraphView`, `BedMeshHeatmapHost`, `BedMeshHeatmapView`, `Media3SurfaceHost`, `Media3SurfaceProvider`, `WebcamViewHost`, `WebcamView`, `RingBuffer`, `ProgressRing`

**Design System (`designsystem/`):**
- Purpose: Shared reusable Compose primitives (scrubbers, numpads, icons, scaffold)
- Location: `app/src/main/java/works/mees/dinghy/designsystem/`
- Contains: `ScreenScaffold`, `ScrubberPage`, `NumpadPage`, `ConfirmGuard`, `SeverityToast`, `OutlinedControl`, `DinghyIcon`, `DinghyIcons`, `DinghyIconView`, `SpoolGlyph`, `MaterialSymbol`, `ColorWheel`, `HsvToRgb`

**Theme (`theme/`):**
- Purpose: Semantic token system, OKLCH palette generation, font scale, Compose + Views bridges
- Location: `app/src/main/java/works/mees/dinghy/theme/`
- Contains: `ThemeTokens`, `BakedTokens`, `ThemeResolver`, `ThemePrefs`, `ThemeOverride`, `Palette`, `OklchRamp`, `SeriesColor`, `StatusSlot`, `Geist`, `BrandTint`, `TokenBridge`, `compose/DinghyTheme`, `compose/LocalTokens`, `views/ThemeableView`

## Data Flow

### Primary Socket-to-Screen Path

1. OkHttp `WebSocketListener.onMessage` → `MoonrakerSocket.events()` emits `SocketEvent.Frame` (`net/MoonrakerSocket.kt`)
2. `MoonrakerSession.connectAndServe` dispatches frame to `JsonRpcClient.dispatch()` (`net/JsonRpcClient.kt`)
3. `JsonRpcClient` routes `notify_status_update` → `_statusUpdates` SharedFlow
4. `MoonrakerSession` routing coroutine: `rpc.statusUpdates.collect { store.onStatusDiff(it) }` (`net/MoonrakerSession.kt:262`)
5. `PrinterStateStore.onStatusDiff()` reduces diff into `accumulator`; control-plane fields publish immediately; high-rate fields set `pendingHighRate = true` (`state/PrinterStateStore.kt:248`)
6. Sampler coroutine (`scope.launch { delay(250ms) }`) flushes `pendingHighRate` → `_printerState.value = accumulator` (`state/PrinterStateStore.kt:221`)
7. `AppContainer.printerState` = `spine.flatMapLatest { it?.printerState }` → collected by `AppShell` via `collectAsStateWithLifecycle` (`di/AppContainer.kt:321`)
8. Composable re-composes with new `printerState` value

### Handshake Seed Path (on connect / klippy_ready)

1. `MoonrakerSession.runHandshake()` calls `rpc.request(CommandRegistry.objectsQuery, …)` → `store.seed(reduceSnapshot(status))` (`net/MoonrakerSession.kt:455`)
2. `store.seed()` writes `accumulator` + clears stale flag + publishes immediately (control-plane event)
3. One-shot reads (`temperature_store`, `configfile`, `machine.system_info`, `machine.proc_stats`, `gcode_store`) set dedicated `StateFlow` fields on the store; these carry forward to any late collector
4. `store.setCapabilities(capabilities)` → `AppContainer.capabilities` flatMap re-derives

### Command Dispatch Path

1. Screen calls `dispatcher.dispatch(key, method, params)` (`command/CommandDispatcher.kt:108`)
2. Dispatcher: debounce check → add `key` to `_inFlight` → `scope.launch { withTimeout { rpc.request(…) } }`
3. `JsonRpcClient.request()` assigns `id`, registers `CompletableDeferred`, sends frame via `RpcConnection.send()`
4. Reply arrives as `SocketEvent.Frame` → `JsonRpcClient.dispatch()` completes the pending deferred
5. `withTimeout` block returns; dispatcher removes `key` from `_inFlight`; `DispatchEvent.Failure` emitted on error

### Config-Change / Printer-Switch Path

1. `AppContainer.activeConfig` emits new `ConnectionConfig` (profile switch or host/port/key edit)
2. `MoonrakerService.runConfigLoop` (via `collectLatest`) cancels current `sessionJob` with `cancelAndJoin()`
3. `buildSpineAndLaunch(cfg)` assembles new `PrinterStateStore`, `JsonRpcClient`, `MoonrakerSession`, `CommandDispatcher`, holders
4. `container.publishSpine(handle)` — one `StateFlow` assignment, atomic spine swap
5. `AppContainer.spine.flatMapLatest` re-subscribes all derived flows to the new session's flows

**State Management:**
- Live printer state: `StateFlow<PrinterState>` on `AppContainer.printerState` (flatMapLatest off spine)
- Connection lifecycle: `StateFlow<ConnectionState>` on `AppContainer.connectionState`
- Navigation: `ShellNavState` (Compose `mutableStateOf`, hoisted in `RootController`)
- Persistence: 6 DataStore `Preferences` files, written exclusively through `AppContainer.writeScope`

## Key Abstractions

**SpineHandle:**
- Purpose: Immutable atomic snapshot of all per-session flows and collaborators
- File: `di/SpineHandle.kt`
- Pattern: Data class; published as one `StateFlow<SpineHandle?>` assignment; `sessionInstanceId` is the rotation-continuity signal

**PrinterStateStore:**
- Purpose: Mutable accumulator with dual-plane emission; owns all one-shot handshake seam StateFlows
- File: `state/PrinterStateStore.kt`
- Pattern: Per-session (constructed in service, exposed via SpineHandle.store); re-keyed in `AppShell` via `remember(store)`

**CommandRegistry:**
- Purpose: Catalogue of every `CommandSpec` (method + arg builder); the D-10 drift-guard source
- File: `command/CommandRegistry.kt`
- Pattern: Object with typed `CommandSpec<Args>` properties; `CommandDispatcher.dispatch()` and `JsonRpcClient.request()` accept them

**Holder pattern (per-session headless state):**
- Purpose: Each feature builds a `<Feature>Holder` on the live `PrinterStateStore`; it produces `StateFlow`s the screen collects
- Examples: `ui/printstatus/PrintStatusHolder.kt`, `ui/temperature/TemperatureHolder.kt`, `ui/finetune/FineTuneHolder.kt`
- Pattern: `remember(store) { FeatureHolder(scope, store) }` in AppShell; holder is re-keyed on spine rebuild

## Entry Points

**`DinghyApp.onCreate()`:**
- Location: `app/src/main/java/works/mees/dinghy/DinghyApp.kt`
- Responsibilities: Constructs 6 DataStore files; builds `AppContainer`; calls `container.seedTheme(appScope)`

**`MainActivity.onCreate()`:**
- Location: `app/src/main/java/works/mees/dinghy/MainActivity.kt`
- Responsibilities: Starts `MoonrakerService`; sets one `DinghyTheme` boundary with `RootController`

**`MoonrakerService.onCreate()`:**
- Location: `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt`
- Responsibilities: Starts foreground; launches `runConfigLoop` which watches `activeConfig` and rebuilds the spine on every config change

## Architectural Constraints

- **Threading:** Socket delivery and state reduction happen on the coroutine that collects `socketEvents` (OkHttp's reader thread via `callbackFlow`). The sampler flush runs on the `serviceScope` (Dispatchers.Default). UI collections run on the main dispatcher via `collectAsStateWithLifecycle`.
- **Global state:** `AppContainer` is a process-singleton held in `DinghyApp.container`. `PrinterStateStore.accumulator` is a `@Volatile var` mutated on the socket coroutine. `JsonRpcClient.pending` map is guarded by a `Mutex`.
- **Circular imports:** None detected. The `state/` layer deliberately has no upward dependencies; `ui/` packages depend on `state/` and `command/` but not on `service/` or `net/` directly.
- **Compose stability:** `PrinterState` carries NO Compose annotations (it is a headless data class). Holders must mark their output as `@Stable`/`@Immutable` if fine-grained skip is required.
- **DataStore single-writer invariant:** Each of the 6 DataStore files is created exactly once in `DinghyApp.onCreate()` and injected into `AppContainer`. Never create a second DataStore instance for the same file.
- **writeScope discipline:** ALL DataStore writes MUST go through `AppContainer.writeScope` (or one of its named intent methods). Using `rememberCoroutineScope()` for a write that navigates away in the same frame silently drops the write on slow Nexus 7 flash.

## AndroidView Interop Surfaces

Three surfaces are classic Views hosted inside Compose via `AndroidView`. A fourth exists for H.264 video.

### 1. Live Temperature Graph
- **Host composable:** `render/GraphViewHost.kt` (two overloads: single-trace sparkline + multi-trace temperature panel)
- **View class:** `render/GraphView.kt` (custom `View`, draws via `android.graphics.Canvas`)
- **Usage sites:** Print Status sparkline (single-trace), Temperature screen (multi-trace)
- **Token bridge:** Caller collects `LocalTokens.current` and passes `ThemeTokens` into `update` block; `view.applyTokens(tokens)` repaints without View recreation
- **Data feed:** `update` block calls `view.setData(snapshot)` (throttled ~4 Hz `RingBuffer.snapshot`); `factory` runs once
- **Preview:** Short-circuits to `PreviewPlaceholderBox` under `LocalInspectionMode`

### 2. Files List
- **Host composable:** `ui/files/FileListView.kt`
- **View class:** `RecyclerView` with `FileRowsAdapter` (`ui/files/FileRowsAdapter.kt`)
- **Usage site:** `FilesScreen.kt`
- **Key constraint:** `Modifier.clipToBounds()` is mandatory — the RecyclerView draws in the Android layer and is NOT clipped by Compose siblings. `MATCH_PARENT` layout params force bounded height so the list scrolls rather than wrapping.
- **Token bridge:** `LocalTokens.current` → `FileRowPalette` (packed ARGB ints); Views cannot read `LocalTokens`
- **Data feed:** `update` block calls `adapter.submitRows(rows)` on directory changes

### 3. Console Scrollback
- **Host composable:** `ui/console/ConsoleListView.kt`
- **View class:** `RecyclerView` with `ConsoleRowsAdapter` (`ui/console/ConsoleRowsAdapter.kt`)
- **Usage site:** `ConsoleScreen.kt`
- **Key constraint:** Same `clipToBounds()` + `MATCH_PARENT` discipline as Files. `itemAnimator = null` (Adreno-320 fill-rate budget).
- **Token bridge:** `LocalTokens.current` → `ConsoleRowPalette` (packed ARGB ints + `fs` value)
- **Update strategy:** `update` block diffs new vs old lines — incremental `notifyItemInserted` for live append, full `submitRows` for backfill/filter-toggle; scroll-to-bottom only when already at bottom before the update

### 4. Bed Mesh Heatmap (bonus View surface)
- **Host composable:** `render/BedMeshHeatmapHost.kt`
- **View class:** `render/BedMeshHeatmapView.kt`
- **Usage site:** `ui/calibration/BedMeshScreen.kt`
- **Pattern:** Identical factory-once / `update`-push discipline as `GraphViewHost`

### 5. H.264 SurfaceView (ExoPlayer)
- **Host composable:** `render/Media3SurfaceHost.kt`
- **View class:** `SurfaceView` (raw Android, no PlayerView)
- **Usage site:** `ui/webcam/WebcamScreen.kt` (via `render/Media3SurfaceHost` + `Media3SurfaceProvider`)
- **Key constraint:** `SurfaceView` punches through the Compose tree; all overlaid chrome (cam name, reconnecting state, dead-end card) MUST be a sibling Compose layer Z-ordered ABOVE the SurfaceView in the same `Box`. The `SurfaceView` must be registered in `Media3SurfaceProvider` on factory and cleared in `onReset`/`onRelease` to prevent stale surface attachment.

## Error Handling

**Strategy:** Best-effort at the transport layer; typed `DispatchEvent.Failure` at the UI layer.

**Patterns:**
- All handshake one-shot reads are wrapped in `runCatching` — failure leaves the store at its null/empty default; screens degrade gracefully (never breaks `Connected`)
- `CommandDispatcher` catches `RpcConnectionException`, `RpcError`, and `TimeoutCancellationException`, maps them to human-readable `DispatchEvent.Failure` messages — API key/token NEVER appears in error messages
- `JsonRpcClient`: malformed frame → `runCatching` drops it; a malformed response fails only its own `CompletableDeferred`
- `MoonrakerSession`: `CancellationException` is rethrown (structured concurrency preserved); other exceptions from `connectAndServe` are caught and mapped to `ConnectAttempt.Network`

## Cross-Cutting Concerns

**Logging:** `Log.i(TAG_SPINE, …)` for spine events in `MoonrakerService`; session id is logged, never the API key or token URL (T-04-03-I).

**Validation:** Wire-data accessed via null-safe `runCatching` helpers (`floatOrNullAt`, `objectOrNull`); never `!!` on Moonraker responses. API key edit resolution is a pure function in `AppContainer.resolveApiKeyEdit`.

**Authentication:** `MoonrakerAuth` fetches a one-shot token immediately before each connect attempt when keyed. The token-bearing WebSocket URL is assembled inside `buildSpineAndLaunch` and never surfaced to the UI. `xApiKeyHeader()` only used in the `identify` request body.

**Security:** The notification text is driven by `ConnectionState` only. `DispatchEvent.Failure` messages are built from `method` + fixed strings — `e.message` (which can carry `?token=` on a send failure) is never included.

---

*Architecture analysis: 2026-06-08*
