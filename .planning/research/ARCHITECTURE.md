# Architecture Research
**Status:** historical (research snapshot; see CLAUDE.md for current stack)

**Domain:** Native Android client for a single real-time data source (Moonraker/Klipper printer control) on old hardware (minSdk 23, Nexus 7 2013, 2GB RAM, Tegra-era GPU)
**Researched:** 2026-05-29
**Confidence:** HIGH (Moonraker protocol verified against official docs; Android patterns verified against Android Developers docs + multiple corroborating sources)

## TL;DR Recommendation

- **Pattern:** Unidirectional / MVI-flavored on top of a **single source-of-truth state store**, fed by one long-lived websocket. Not classic MVVM-with-per-screen-models. The whole app is "render a projection of one reactive `PrinterState`."
- **Connection home:** A **started foreground service** that owns the OkHttp websocket and the Moonraker client, exposing `StateFlow`s. Survives Activity recreation and screen-off; reconnects on its own. The Activity holds `FLAG_KEEP_SCREEN_ON` (screen-on can *only* be set on a window/Activity, never a service).
- **State:** One normalized `PrinterState` `StateFlow` in a repository. `notify_status_update` diffs are *merged* into it. Capability flags derived once from `printer.objects.list` + config. Panels observe slices.
- **Navigation:** Printer state **drives the primary route** (splash / main / job_status). User navigation is a stack on top of that root, exactly mirroring the KlipperScreen `__main` / `__print` / `__splashscreen` model.
- **Build order:** Connection layer → state store/normalization → capability detection → shell + state-driven nav → primitives → panels. **Foundation must exist and be testable against a mock websocket before any panel is written.**

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│  UI LAYER (Activity + per-panel ViewModels, MVI)                       │
│  Activity holds FLAG_KEEP_SCREEN_ON. Single-Activity host.             │
│  ┌────────┐ ┌────────┐ ┌─────────┐ ┌──────┐ ┌───────┐ ┌─────────┐     │
│  │ Move   │ │ Temp   │ │ Extrude │ │Files │ │ Job   │ │ Console │ ... │
│  │ VM     │ │ VM     │ │ VM      │ │ VM   │ │Status │ │ VM      │     │
│  └───┬────┘ └───┬────┘ └────┬────┘ └──┬───┘ └───┬───┘ └────┬────┘     │
│      │ observe slices of PrinterState  │ send intents → commands       │
├──────┴──────────┴───────────┴──────────┴─────────┴──────────┴─────────┤
│  STATE LAYER  (PrinterRepository — single source of truth)            │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │ StateFlow<PrinterState>   StateFlow<ConnectionState>             │ │
│  │ StateFlow<Capabilities>   SharedFlow<ConsoleLine> (gcode resp.)  │ │
│  │ merges notify_status_update diffs; derives capabilities once     │ │
│  └─────────────────────────────────────────────────────────────────┘ │
├───────────────────────────────────────────────────────────────────────┤
│  CONNECTION LAYER (inside a started Foreground Service)                │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────────┐   │
│  │ MoonrakerClient  │  │ JsonRpc id-corr  │  │ Notify dispatcher  │   │
│  │ (OkHttp WS + REST)│ │ (id → Completable│  │ notify_* → typed    │   │
│  │ reconnect/backoff│  │  Deferred map)   │  │ events → Repository │   │
│  └────────┬─────────┘  └──────────────────┘  └────────────────────┘   │
├───────────┴───────────────────────────────────────────────────────────┤
│  PERSISTENCE: connection config (DataStore/Prefs). No printer-state    │
│  persistence — it is ephemeral and re-derived from the printer.        │
└───────────────────────────────────────────────────────────────────────┘
                              │ ws://host:port/websocket  +  HTTP REST
                              ▼
                        Moonraker / Klipper
```

### Component Responsibilities

| Component | Responsibility | Implementation |
|-----------|----------------|----------------|
| **MoonrakerClient** | Own the single OkHttp `WebSocket` and the REST `OkHttpClient`. Send JSON-RPC, surface raw frames. Reconnect with backoff. | OkHttp `WebSocketListener`; one client instance, lives in service |
| **JSON-RPC correlator** | Map outgoing request `id` → suspending result; resolve on matching response; time out. | `MutableMap<Long, CompletableDeferred<JsonElement>>` + atomic id counter |
| **Notify dispatcher** | Route `notify_*` methods (no `id`) to typed event handlers: `notify_status_update`, `notify_gcode_response`, `notify_klippy_ready/disconnected/shutdown`, `notify_proc_stat_update`, etc. | `when(method)` switch → `Channel`/`SharedFlow` |
| **PrinterRepository** | THE single source of truth. Merge status diffs into `PrinterState`. Hold `ConnectionState`, `Capabilities`. Expose suspend command methods (`home()`, `setTarget()`, `startPrint()`). | Singleton scoped to service; exposes `StateFlow`/`SharedFlow` |
| **CapabilityDetector** | On Klippy-ready, query `printer.objects.list` + relevant config; produce immutable `Capabilities` (has heaters? extruder count? macros? firmware retraction? power devices?). Re-run on reconnect. | Pure function over query results → `Capabilities` |
| **ConnectionService** | Foreground service hosting the above. Keeps connection alive across Activity recreation, screen-off, app-backgrounded. Owns a service-lifetime `CoroutineScope`. | `Service` + `startForeground()` + ongoing notification |
| **Per-panel ViewModel** | Map a slice of `PrinterState`/`Capabilities` → immutable panel `UiState`; translate user intents → repository commands. | `ViewModel` + `StateFlow<UiState>` |
| **Navigation host** | Compute root route from `PrinterState`; manage user back-stack on top of it. | Single Activity + nav (see §Navigation) |
| **Config store** | Persist host/port/API key. | DataStore (or SharedPreferences) |

## Recommended Project Structure

```
app/src/main/java/.../dinghy/
├── connection/                 # CONNECTION LAYER — built first, no Android UI deps
│   ├── MoonrakerClient.kt      # OkHttp WS + REST, send/receive raw frames
│   ├── JsonRpcCodec.kt         # request/response framing, id allocation
│   ├── RpcCorrelator.kt        # id → CompletableDeferred map, timeouts
│   ├── NotifyDispatcher.kt     # notify_* → typed events
│   ├── Reconnector.kt          # backoff state machine
│   └── dto/                    # serializable wire models (kotlinx.serialization)
├── state/                      # STATE LAYER — single source of truth
│   ├── PrinterRepository.kt    # merges diffs, exposes StateFlows + commands
│   ├── PrinterState.kt         # normalized immutable domain model
│   ├── ConnectionState.kt      # sealed: Connecting/Initializing/Ready/Disconnected/Error
│   ├── Capabilities.kt         # immutable derived capability flags
│   └── CapabilityDetector.kt
├── service/
│   └── ConnectionService.kt    # foreground service hosting connection+state
├── config/
│   └── ConnectionConfigStore.kt# DataStore-backed persistence
├── ui/
│   ├── shell/                  # persistent chrome: titlebar, back/home, E-STOP, status
│   ├── nav/                    # state-driven root route + user back-stack
│   ├── primitives/             # numeric keypad, keyboard, confirm dialog, toast/popup
│   └── panels/
│       ├── move/   (Screen + ViewModel)
│       ├── temperature/
│       ├── extrude/
│       ├── files/
│       ├── jobstatus/
│       ├── macros/
│       └── console/
└── di/                         # manual DI or Hilt wiring (service scope vs vm scope)
```

### Structure Rationale

- **`connection/` has zero Android-UI dependencies** — it's plain Kotlin + OkHttp + coroutines, so it is unit-testable against a mock `WebSocket` with no emulator. This is the seam that lets the foundation be validated before any panel exists.
- **`state/` depends on `connection/` but not on UI** — the repository is the contract every panel codes against. Panels can be built in parallel once `PrinterState` and `Capabilities` shapes are frozen.
- **`service/` is thin** — it's a lifecycle host, not logic. Logic lives in `connection/`+`state/` so it stays testable.
- **`ui/primitives/` before `ui/panels/`** — every panel needs the keypad/keyboard/confirm-dialog (per the catalog, Move/Temp/Extrude/Files all trigger these). Primitives are a hard dependency of panels.

## Architectural Patterns

### Pattern 1: Single Source of Truth via diff-merge into one StateFlow

**What:** Moonraker's `notify_status_update` sends **partial diffs** — only changed fields of subscribed objects (verified: `notify_status_update` params contain only changed values, e.g. `{"gcode_move":{"speed":1500},"toolhead":{"status":"Ready"}}`). The repository keeps the *full* `PrinterState` and merges each diff in. Panels never see raw diffs — they observe the merged state.

**When to use:** Always here. This is the core of the whole app.

**Trade-offs:** One big state object means careful slicing so a temperature tick doesn't recompose the Move panel. Mitigate with `StateFlow.map{...}.distinctUntilChanged()` per slice, or split into a few coarse `StateFlow`s (temps / motion / job / files) rather than one monolith — on 2GB/Tegra hardware, **minimizing recomposition/redraw scope matters more than purity.**

```kotlin
// Repository core
private val _state = MutableStateFlow(PrinterState.EMPTY)
val state: StateFlow<PrinterState> = _state.asStateFlow()

fun onStatusUpdate(diff: StatusDiff) {
    _state.update { current -> current.mergedWith(diff) }   // immutable copy w/ changed fields
}

// Panel observes only what it needs — no recompose on unrelated ticks
val nozzleTemp = repo.state
    .map { it.heaters["extruder"]?.current }
    .distinctUntilChanged()
```

### Pattern 2: Connection layer in a started Foreground Service (not a bound service, not a VM-scoped client)

**What:** The websocket lives in a `startForeground()` service with a service-lifetime `CoroutineScope`. The Activity binds only to read flows; the connection's existence does not depend on any Activity. `FLAG_KEEP_SCREEN_ON` is set on the Activity window (it *cannot* be set in a service).

**When to use:** When a single connection must outlive Activity recreation (rotation, config change), survive screen-off, and reconnect autonomously — exactly the wall-mounted-tablet case.

**Trade-offs:** A foreground service requires an ongoing notification (fine for a dedicated appliance). It is heavier than a VM-scoped client but is the only correct home for a "stay connected while mounted" model. On a kiosk-style appliance you can keep the Activity foregrounded; the service guarantees the connection survives the brief window during Activity recreation and any backgrounding.

> minSdk 23 note: typed foreground services / `FOREGROUND_SERVICE_*` permissions and the stricter background-start rules are post-API-26 concerns. On API 23 the service model is *simpler*, not harder. Just `startService` + `startForeground`. Doze on API 23 affects deferred/background CPU, but a foreground service with the screen on (the mounted case) is not Doze-throttled. Use a partial wakelock only if you intend to keep the connection alive with the screen off.

### Pattern 3: State-driven primary route + user back-stack on top

**What:** Mirror KlipperScreen exactly. The **printer state selects the root**: `ready → main_menu`, `printing/paused → job_status`, `startup/shutdown/error/disconnected → splash`. The user's panel navigation is a stack pushed *on top of* that root. When printer state changes root (e.g. print starts), the stack resets to the new root.

**When to use:** Whenever an external state machine — not the user — owns the "home screen."

**Trade-offs:** This fights the default Android assumption that the user owns navigation. You must intercept and re-root on state transitions. Worth it: it's the catalog's proven model and prevents "stuck on Move panel while the printer is mid-startup."

```kotlin
val rootRoute: StateFlow<Route> = combine(connectionState, printerState) { conn, p ->
    when {
        conn !is Ready          -> Route.Splash      // connecting/init/error/shutdown
        p.printState.isActive   -> Route.JobStatus   // printing or paused
        else                    -> Route.MainMenu
    }
}.stateIn(scope, Eagerly, Route.Splash)
```

### Pattern 4: Capability gating as derived, cached flags

**What:** On every Klippy-ready, run `CapabilityDetector` once: query `printer.objects.list` and the needed config, produce an immutable `Capabilities` (hasExtruder, extruderCount, hasHeaterBed, macros, hasFirmwareRetraction, powerDevices, hasFilamentSensor...). The shell and panels read `Capabilities` to show/hide controls. Re-derive on reconnect (printer config can change).

**Trade-offs:** Adds a gate before the UI is "fully ready," but it is the only correct way to avoid showing controls the printer can't honor (the catalog flags this repeatedly — most panels are conditional on capabilities).

## Data Flow

### Inbound: websocket → observable UI state

```
ws frame
  → MoonrakerClient (raw text)
  → JsonRpcCodec (parse)
  → has "id"?  ── yes ──→ RpcCorrelator resolves CompletableDeferred (command result)
       │ no
       ▼
  NotifyDispatcher (switch on method)
       ├─ notify_status_update ─→ PrinterRepository.merge(diff) → PrinterState StateFlow
       ├─ notify_gcode_response ─→ ConsoleLine SharedFlow (Console panel)
       ├─ notify_klippy_ready ───→ run CapabilityDetector → ConnectionState=Ready
       ├─ notify_klippy_shutdown/disconnected → ConnectionState=Error/Disconnected
       └─ notify_power_changed / proc_stat / job history → targeted state
  → ViewModel maps slice → UiState → Compose renders
```

### Outbound: user intent → command

```
User taps "Home All"
  → MoveViewModel.onIntent(HomeAll)
  → PrinterRepository.runGcode("G28")  (suspend)
  → MoonrakerClient.send( {method:"printer.gcode.script", params:{script:"G28"}, id:N} )
  → RpcCorrelator awaits id=N → result or error
  → state changes arrive independently via notify_status_update (homed axes flip)
```

Key principle: **commands and state are decoupled.** A command's ack means "Moonraker accepted it," not "the world changed." UI truth comes only from the merged `PrinterState`. This avoids optimistic-UI drift on a device that may briefly disconnect.

### Connection lifecycle (verified against Moonraker docs)

```
connect ws://host:port/websocket
  → server.connection.identify  (announce client name/version)
  → query server.info → klippy_state:
        "startup"  → splash, re-poll ~2s
        "ready"    → subscribe + detect capabilities → main/job route
        "error"/"shutdown" → splash with state_message + recovery actions
        "disconnected" → splash (Klipper host down)
  → printer.objects.subscribe({objects:{...}})   # one subscription, overrides previous
  ... receive notify_status_update diffs ...
  on notify_klippy_disconnected → back to splash, repeat startup sequence on reconnect
```

## Reconnection & Transient Disconnect Handling

- **Backoff state machine** in `Reconnector`: exponential with cap (e.g. 0.5s → 1s → 2s → … → 15s max), jittered, reset on successful identify. On a LAN this is fast; don't back off too aggressively or a router blip locks you out for 30s.
- **Two distinct disconnect kinds, surfaced differently:**
  1. **Websocket dropped** (network/transport) → `ConnectionState.Reconnecting`. **Do NOT clear `PrinterState`.** Show a non-destructive banner ("Reconnecting…") over the *last known* UI. The print is still running; the operator should still see last temps/progress.
  2. **Klippy disconnected/shutdown** (`notify_klippy_disconnected`/`shutdown`) → route to splash; this is a real printer-side state, not a transport hiccup.
- **Re-subscribe on reconnect.** A new `printer.objects.subscribe` overrides the previous (verified) — just re-send the subscription and re-run capability detection; the first batch of diffs re-hydrates state.
- **Stale-state marking:** tag `PrinterState` with `lastUpdate` timestamp / a `stale` flag while reconnecting so panels can dim/disable destructive actions (don't let someone hit "Cancel Print" against state that's 20s old).

## Threading / Coroutine Strategy

| Concern | Dispatcher / scope |
|---------|--------------------|
| Websocket I/O, parsing, diff-merge | Service-lifetime scope on `Dispatchers.Default` (CPU-bound JSON merge) or a single dedicated thread to serialize state mutation |
| REST calls (file lists, thumbnails) | `Dispatchers.IO` |
| State mutation (`_state.update{}`) | Keep on ONE dispatcher/thread to avoid races; `StateFlow.update` is atomic but funnel all merges through the repository's own scope |
| UI collection | `viewModelScope`, `repeatOnLifecycle(STARTED)` in the Activity to stop collecting when backgrounded (but the *service* keeps the connection regardless) |
| Image/thumbnail decode | `Dispatchers.IO` + downsample aggressively (2GB RAM — never decode full-res gcode thumbnails) |

## Persistence Boundary

- **Persist:** connection config only (host, port, API key / trusted-client flag). Use **DataStore** (Preferences) — or plain SharedPreferences given the tiny surface and minSdk 23. Multi-printer-ready means store a *list* even though v1 uses one.
- **Do NOT persist:** `PrinterState`, capabilities, file lists. All ephemeral and re-derived on connect. Persisting them invites stale/wrong UI after restart.

## Testing Seams

- **Mock websocket:** `MoonrakerClient` takes an injectable transport interface (`WebSocketTransport`) so tests feed canned JSON-RPC frames and assert `PrinterState` evolution — no emulator, no real printer. This is the single most valuable test seam; build it into the connection layer from day one.
- **Golden-frame fixtures:** capture real `notify_status_update`/`notify_gcode_response` sequences from Matthew's Ender 5 Plus and replay them in tests (great regression net for the diff-merge).
- **Capability detection** is a pure function: feed `printer.objects.list` responses → assert `Capabilities`.
- **ViewModels** test against a fake `PrinterRepository` exposing controllable `StateFlow`s — assert intent→command and state→UiState mapping with no network.

## Toolkit Note (Compose vs Views on Tegra/2GB)

This is a STACK decision but it constrains architecture: as of Compose 1.9+ (2025), Compose scroll-jank parity with Views is documented, but **startup cost and runtime interpretation on API 23 without AOT/Baseline-Profile support is a real risk on a 2013 Tegra.** Baseline Profiles help most on API 24+. If chosen, Compose demands disciplined recomposition scoping (slice the StateFlow, `derivedStateOf`, stable keys) — the architecture above (per-slice `distinctUntilChanged` flows) is *designed for that*. Classic Views remain the conservative, lowest-risk choice for this exact hardware. Either way, the connection/state foundation is UI-toolkit-agnostic, so this decision can be deferred without blocking foundation work. (Flagged for STACK.md; settle before panel work.)

## Build Order (drives phase sequencing)

This is the load-bearing output for the roadmap. **Strict dependency chain — foundation before panels:**

```
1. Connection layer        MoonrakerClient + JSON-RPC codec + id correlation
   (no UI)                 + notify dispatch + reconnect. Test w/ mock socket.
        ↓ depends on
2. State store             PrinterState model + diff-merge + ConnectionState
                           + command methods. Single source of truth.
        ↓ depends on
3. Capability detection    printer.objects.list → Capabilities. Re-run on reconnect.
        ↓ depends on
4. Foreground service      Host #1–3; keep-alive; ongoing notification.
   + connection config     Config persistence + first-run connect screen.
        ↓ depends on
5. Shell + state-driven    Persistent chrome (titlebar, back/home, E-STOP, live
   navigation + splash     status) + root-route-from-state + splash/initializing.
        ↓ depends on
6. UI primitives           Numeric keypad, keyboard, confirm dialog, toast/popup.
        ↓ depends on
7. Panels (parallelizable  Move, Temperature, Extrude, Files/Print, Job Status,
   once 1–6 exist)         Macros, Console — each = Screen + ViewModel over the repo.
```

- **Steps 1–3 are the project's spine** and should be a phase (or two) with NO UI deliverable beyond a debug "is it connected / dump state" view. Resist building panels here.
- **Step 5 (shell + state-driven nav)** is the first thing that *looks* like the app and proves the connect→splash→main loop — the core-value loop's skeleton.
- **Step 7 panels** can be ordered by the daily-driver loop: Files/Print + Job Status first (start-and-watch-a-print is the core value), then Temperature/Move/Extrude (manual control), then Macros/Console.

## Anti-Patterns

### Anti-Pattern 1: Websocket owned by a ViewModel / Activity
**What people do:** Open the socket in a `ViewModel` or `onCreate`. **Why wrong:** rotation/config-change recreates it → reconnect storms; backgrounding kills the print monitor. **Instead:** connection in a started foreground service; ViewModels only observe.

### Anti-Pattern 2: Treating each `notify_status_update` as full state
**What people do:** Replace state with the diff. **Why wrong:** diffs are partial — you'd lose every field not in this tick. **Instead:** merge diffs into a retained full `PrinterState` (Pattern 1).

### Anti-Pattern 3: Optimistic UI from command acks
**What people do:** Flip the heater UI to "on" because the command returned ok. **Why wrong:** on a flaky LAN the world and UI drift. **Instead:** UI truth comes only from merged state arriving via `notify_status_update`.

### Anti-Pattern 4: Clearing state on transient websocket drop
**What people do:** Wipe the screen to "disconnected" on any socket close. **Why wrong:** a 2-second router blip during a 6-hour print blanks the operator's view. **Instead:** keep last state, show a reconnect banner, distinguish transport-drop from `notify_klippy_disconnected`.

### Anti-Pattern 5: One monolithic StateFlow recomposing everything
**What people do:** Every panel collects the whole `PrinterState`. **Why wrong:** a temp tick (multiple per second) recomposes/redraws the entire UI — fatal on Tegra/2GB. **Instead:** per-slice `map{}.distinctUntilChanged()` or a few coarse flows.

### Anti-Pattern 6: Letting the user own the home route
**What people do:** Standard Android back-stack with a fixed home. **Why wrong:** the printer's state machine should own "where home is" (printing → job_status). **Instead:** state-driven root route + user stack on top (Pattern 3).

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Moonraker websocket | One persistent OkHttp `WebSocket`, JSON-RPC 2.0, id-correlated requests + `notify_*` events | The *only* event source. Requires persistent connection for subscriptions (verified). |
| Moonraker REST | OkHttp HTTP for file lists, thumbnails, history, uploads | "REST-ish"; use for bulk/file ops, not real-time. Same host:port. |
| Klipper (via Moonraker) | `printer.gcode.script` for commands, `printer.objects.subscribe` for state | Never talk to Klipper directly; everything is via Moonraker. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| Service ↔ Activity | Bound-on-read of `StateFlow`s (or DI singleton + `repeatOnLifecycle`) | Activity reads; never owns the connection. |
| Connection ↔ State | Connection emits typed events; Repository merges | Connection knows nothing about UI or domain semantics beyond DTOs. |
| State ↔ Panels | Panels observe `StateFlow` slices; send intents → suspend commands | Repository is the frozen contract enabling parallel panel work. |

## Sources

- Moonraker — WebSocket/JSON-RPC introduction & connection lifecycle (klippy_state, identify, ids, notify_klippy_*): https://moonraker.readthedocs.io/en/latest/external_api/introduction/ (HIGH — official)
- Moonraker — JSON-RPC Notifications (`notify_status_update` partial diffs, `notify_gcode_response`): https://moonraker.readthedocs.io/en/latest/external_api/jsonrpc_notifications/ (HIGH — official)
- Moonraker — Printer Administration (`printer.objects.subscribe` semantics, subscription override/cancel): https://moonraker.readthedocs.io/en/latest/external_api/printer/ (HIGH — official)
- Android Developers — Keep the screen on (FLAG_KEEP_SCREEN_ON activity-only): https://developer.android.com/develop/background-work/background-tasks/awake/screen-on (HIGH — official)
- Android Developers — Choose the right API to keep the device awake (Doze, foreground service, wakelock): https://developer.android.com/develop/background-work/background-tasks/awake (HIGH — official)
- Android Developers — Compose vs Views metrics / performance & Baseline Profiles on API 23+: https://developer.android.com/develop/ui/compose/migrate/compare-metrics , https://developer.android.com/develop/ui/compose/performance (HIGH — official)
- Eureka Engineering — Android unidirectional architecture with StateFlow/SharedFlow: https://medium.com/eureka-engineering/android-unidirectional-architecture-with-stateflow-f5b2dda5ae73 (MEDIUM)
- Handling WebSocket in Compose with OkHttp + SharedFlow (repository/VM/service layering): https://medium.com/@danimahardhika/handle-websocket-in-jetpack-compose-with-okhttp-and-sharedflow-b1ed7c9fd713 (MEDIUM)
- Bugfender — Real-time Android apps with WebSockets and Kotlin (foreground service for continuous connection): https://bugfender.com/blog/android-websockets/ (MEDIUM)
- KlipperScreen Screen Catalog (state-driven routes `__main`/`__print`/`__splashscreen`, capability gating) — workspace reference doc (HIGH for UI-model intent)

---
*Architecture research for: native Android Moonraker client on constrained old hardware*
*Researched: 2026-05-29*
