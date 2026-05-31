# Phase 4: Service, Shell, Settings & Print-Status Home - Research

> ⚠ **RETARGETED 2026-05-31.** Phase renumbered **3 → 4**. The technical research below — **FGS-owns-spine,
> pure `derive()` klippy routing, the render/throttle seam, Moonraker action RPCs, mDNS, `specialUse`
> FGS** — is **still valid** for Phase 4. BUT the UI/shell sections (nav-rail/edge-swipe drawer, top-bar
> E-stop, the dashboard-as-menu, code skeletons drawing those) are **superseded by `docs/ui_design/`**
> (LAW): swipe-up App Drawer, Settings screen, Print Status home, Stop→Confirm. The render/throttle and
> the reusable primitives now live in the new **Phase 3 (Design System & Theming Foundation)**; this
> phase *consumes* them. Treat the architecture diagram + skeletons as the connection/service/routing
> reference only, not the UI. Re-run `/gsd-plan-phase 4 --research` to refresh against the new scope.

**Researched:** 2026-05-30
**Domain:** Android foreground service (connection ownership), Compose Material 3 shell + state-driven routing, hybrid Compose/Views render primitive, command-dispatch + confirm primitives, NsdManager mDNS
**Confidence:** HIGH (the hard parts — FGS type, routing model, render-primitive seam — are verified against official docs + the existing spine code)

## Summary

Phase 3 takes the headless, gate-proven connection spine from Phase 2 (`MoonrakerSession` → `JsonRpcClient` → `PrinterStateStore` exposing `StateFlow<PrinterState>`/`StateFlow<Capabilities>`/`StateFlow<ConnectionState>` + a gcode `SharedFlow`) and turns it into a living app. The single load-bearing architectural move is **moving ownership of the spine out of any Activity and into a started foreground service whose `serviceScope` constructs and runs the spine**, with the StateFlows held in a process-scoped service-locator so the Compose UI just collects them. Once that is right, Activity recreation (rotation) and screen-off become non-events — the connection never blinks because nothing connection-related lives in the Activity.

Everything else in the phase is well-trodden Android wiring made specific by this project's constraints: a `specialUse` FGS type declared for `targetSdk 35` (no-op on the API-30 test device but correct for newer hardware, and honest for "hold a LAN session to a printer"); `klippy_state`-driven top-level routing modeled as **derived state over the existing `PrinterState.klippyState`/`printState` fields** (a `when` over a single derived `TopRoute`, not `androidx.navigation`); a **toolkit-agnostic ViewModel/holder feeding both a Compose dashboard and a classic-Views `Canvas` graph** so the render/throttle primitive established here is exactly what Phase 4's temperature graph extends; and a small set of reusable primitives (confirm dialog, command-dispatch wrapper, keypad, keyboard, toast). The throttle half of the render primitive **already exists** (`PrinterStateStore` samples high-rate fields at ~250 ms / 4 Hz); Phase 3 only adds the *render* half.

**Primary recommendation:** Build a started+`START_STICKY` `MoonrakerService` that owns `serviceScope`, constructs the spine from a **DataStore-sourced config** (replacing `DevConfig`), and publishes the existing StateFlows through an `Application`-held service-locator (`AppContainer`). Drive top-level routing from a single derived `TopRoute` computed off `PrinterState`. Keep the dashboard/graph behind a toolkit-agnostic `DashboardViewModel` so the Compose mini-graph and the later Views `Canvas` graph consume identical `StateFlow` series. Make a `CommandDispatcher` suspend wrapper (timeout + in-flight set + debounce) the *only* path that issues Moonraker action RPCs, and route the destructive set through one `ConfirmDialog` primitive. mDNS via `NsdManager` is a best-effort adjunct — **manual host/port is the floor** (most Moonraker installs don't even run the opt-in `[zeroconf]` component).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Hold the long-lived Moonraker websocket across rotation/screen-off | **Foreground Service (process)** | — | A started FGS with a process-scoped `serviceScope` is the only place a connection survives Activity recreation and screen-off; SHELL-03 is definitionally a service concern. |
| Persist connection config (host/port/key) | **DataStore (process I/O)** | Service (reads it) | DataStore is the coroutine-safe settings store; the service collects it and (re)builds the session. CONN-01. |
| Decide top-level route (splash/main/job) | **UI state derivation** | Service (provides `PrinterState`) | Routing is a pure function of `klippyState`/`printState` — derived state in the UI layer, NOT a service responsibility and NOT socket-state-driven. |
| Render the thermal dashboard + temp graph | **UI (Compose shell + Views `Canvas`)** | Service (provides throttled `StateFlow`) | Per ADR 0001 the graph is a classic-Views `Canvas`; the throttle already happens at the state layer. UI only draws. |
| Issue Moonraker action RPCs (estop, restart, etc.) | **UI-invoked command primitive** | Service/`JsonRpcClient` (transport) | `CommandDispatcher` wraps the existing `JsonRpcClient.request()`; timeout/in-flight/debounce are a UI-affordance concern layered over transport. |
| Emergency Stop reachability | **Shell chrome (UI)** | Command primitive | Must be on every screen → lives in the persistent shell scaffold, not any one panel. |
| mDNS discovery | **UI (config screen) via NsdManager** | — | A first-run convenience; `NsdManager` is a system service the config screen drives. Never blocks connecting. |

## Standard Stack

### Core (all ALREADY in the catalog — Phase 3 adds exactly one runtime dep)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Compose (BOM) | `2026.05.00` → UI 1.11.1 | Shell, nav-rail, dashboard, dialogs, primitives | Locked by CLAUDE.md/ADR 0001 `[CITED: gradle/libs.versions.toml]`. |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.7 | `collectAsStateWithLifecycle()` for StateFlow→Compose | Already in catalog; the correct lifecycle-aware collector. `[CITED: libs.versions.toml]` |
| `androidx.activity:activity-compose` | 1.9.3 | `ComponentActivity` host, `BackHandler` | Already in catalog. `[CITED: libs.versions.toml]` |
| `androidx.appcompat` + `androidx.constraintlayout` | 1.7.0 / 2.1.4 | Host the Views `Canvas` graph under the AppCompat theme via `AndroidView` | Already in catalog (added for the hybrid benchmark). `[CITED: libs.versions.toml]` |
| OkHttp / Retrofit / kotlinx.serialization / Coroutines | per catalog | The spine + REST | Already wired in Phase 2. |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **`androidx.datastore:datastore-preferences`** | **1.1.1** | Persist host/port/optional key (CONN-01) | **The one new runtime dep this phase adds.** See Package Legitimacy Audit. minSdk-23-safe. |
| `android.net.nsd.NsdManager` | platform (API 16+) | mDNS `_moonraker._tcp` scan | Built into the OS — **no dependency**. Best-effort adjunct only. |
| `android.app.NotificationManager` / `NotificationChannel` | platform | Persistent FGS notification | Built-in. Channel required on API 26+ (your API-30 device). |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Started FGS owning the spine | Bound-only service | A pure bound service dies when the last client unbinds — the connection would drop on the exact rotation you're trying to survive. Wrong tool. Use **started** (`START_STICKY`); binding is optional and NOT needed for state observation (the StateFlows are process-held). |
| `when(TopRoute)` state holder | `androidx.navigation:navigation-compose` | CONTEXT locks this out for v1 — top route is reactive to `klippy_state` (a `when`), not a user-driven back-stack. Adding Nav-Compose is needless weight on 2GB. Revisit when destination count grows. |
| `NsdManager` (platform) | `jmDNS` (library) | jmDNS is a heavier 3rd-party dep with its own quirks; `NsdManager` is built-in and minSdk-safe. Neither is reliable enough to be the primary path — see Pitfalls. Prefer the zero-dependency platform API for the best-effort scan. |
| DataStore Preferences | SharedPreferences | SharedPreferences does main-thread disk I/O → ANR risk; DataStore is coroutine/Flow-native and the service already lives in coroutines. CLAUDE.md mandates DataStore. |
| DataStore Preferences | DataStore Proto / Room | Overkill for ~4 scalar keys (host/port/key + a discovered-name). Preferences is correct; Proto/Room is for v2 multi-printer. |

**Installation (Gradle — this is Android, not npm):**
```toml
# gradle/libs.versions.toml — add under [versions]
datastore = "1.1.1"
# under [libraries]
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```
```kotlin
// app/build.gradle.kts
implementation(libs.androidx.datastore.preferences)
```

**Version verification:**
- `androidx.datastore:datastore-preferences` **1.1.1** is current-stable, minSdk 21 (well under the 23 floor). `[VERIFIED: Google Maven / developer.android.com Jetpack versions]` — confirm the exact pin against `developer.android.com/jetpack/androidx/releases/datastore` at plan time; if a 1.1.x patch newer than 1.1.1 exists, prefer it but re-confirm minSdk stays ≤ 23 (the `verifyMinSdk` task is the backstop).
- No other new runtime deps. NsdManager and NotificationManager are platform APIs.

## Package Legitimacy Audit

> One new runtime package this phase. slopcheck was not available in this research environment (`pip install slopcheck` not run / Python sandbox); per protocol the new package is tagged `[ASSUMED]` and the planner should gate it behind a `checkpoint:human-verify` — though it is a first-party Google AndroidX artifact, which is the lowest-risk class.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `androidx.datastore:datastore-preferences` 1.1.1 | Google Maven (AndroidX) | mature (DataStore GA 2021) | first-party Google | github.com/androidx/androidx | unavailable | `[ASSUMED]` — Approved; first-party AndroidX. Planner: one `checkpoint:human-verify` on add, then `verifyMinSdk` proves the floor. |

**Packages removed due to slopcheck [SLOP]:** none
**Packages flagged [SUS]:** none

*slopcheck unavailable → the one new package is `[ASSUMED]`. It is a Google AndroidX artifact (not registry-resolved from an LLM suggestion), so risk is minimal; the existing `verifyMinSdk` merged-manifest gate independently catches any floor violation.*

## Architecture Patterns

### System Architecture Diagram

```
                          ┌─────────────────────────────────────────────┐
   user taps app icon ──► │ MoonrakerService (started, START_STICKY)     │
                          │   serviceScope (SupervisorJob + Dispatchers) │
   DataStore(host,port,   │     ▲ collects config                       │
     key) ───────────────┼─────┘                                        │
        ▲                 │   builds spine on config change:            │
        │ save            │     OkHttpClient → MoonrakerSocket.real(url) │
        │                 │       → JsonRpcClient → PrinterStateStore    │
   ConnectScreen          │       → MoonrakerSession.run()              │
   (manual + NsdManager   │   startForeground(notification)             │
    scan, best-effort)    └───────────────┬─────────────────────────────┘
        ▲                                  │ publishes (process-held)
        │ no saved config?                 ▼
        │            ┌──────────────────────────────────────────┐
        │            │ AppContainer (Application-held singleton)  │
        │            │  StateFlow<PrinterState>                   │
        │            │  StateFlow<Capabilities>                   │
        │            │  StateFlow<ConnectionState>                │
        │            │  SharedFlow<gcode>                         │
        │            │  CommandDispatcher (wraps JsonRpcClient)   │
        │            └──────────────────┬─────────────────────────┘
        │                               │ collectAsStateWithLifecycle()
        │                               ▼
        │            ┌──────────────────────────────────────────┐
        └────────────┤ MainActivity (Compose host)               │
                     │  val route = derive(PrinterState)         │
                     │  when(route) {                            │
                     │    Connect  -> ConnectScreen              │
                     │    Splash   -> SplashScreen(reason,actions)│  ◄─ klippy non-ready
                     │    Shell    -> AppShell {                  │
                     │       full-bleed content (no rail/top-bar) │  ◄─ max canvas; status lives in drawer
                     │       EdgeHandle + edge-swipe → AppDrawer  │  ◄─ full-screen tile launcher
                     │       when(dest) { Dashboard | Job | ... } │
                     │         Dashboard: Compose rows +          │
                     │           AndroidView(TempGraphView Canvas)│  ◄─ shared render primitive
                     │           + E-STOP (hold) — Home only      │  ◄─ E-stop on dashboard only
                     │    }                                       │
                     │  }                                         │
                     │  primitives: ConfirmDialog, Keypad,        │
                     │    Keyboard, Toast (panel-consumable)      │
                     └──────────────────────────────────────────┘
```

Trace the primary use case: app launch → service starts and (if config saved) builds the spine → `PrinterState` flows to `AppContainer` → Activity derives `TopRoute` → Klippy `Startup` shows Splash, `Ready` shows the full-bleed Shell (edge-swipe drawer for nav), `Printing` lands on Job. Rotate the device → Activity is destroyed/recreated, re-collects the *same* process-held StateFlows, connection never dropped (it lives in the service).

### Recommended Project Structure
```
app/src/main/java/works/mees/dinghy/
├── DinghyApp.kt              # Application; constructs AppContainer
├── di/AppContainer.kt        # manual service-locator: spine StateFlows + CommandDispatcher (NO Hilt)
├── service/
│   └── MoonrakerService.kt   # started FGS: serviceScope owns the spine; startForeground notification
├── config/
│   ├── ConnectionConfig.kt   # data class (host, port, apiKey?) — replaces DevConfig as the model
│   ├── ConnectionStore.kt    # DataStore(Preferences) read/write Flow<ConnectionConfig?>
│   └── MoonrakerDiscovery.kt # NsdManager _moonraker._tcp scan, best-effort
├── command/
│   └── CommandDispatcher.kt  # PRIM-05: timeout + in-flight set + debounce over JsonRpcClient.request
├── ui/
│   ├── route/TopRoute.kt     # derive(PrinterState) -> Connect|Splash|Shell(landing)
│   ├── shell/AppShell.kt     # NavigationRail + TopBar(status, E-stop) + content host
│   ├── shell/EmergencyStop.kt# hold/double-tap gesture → printer.emergency_stop
│   ├── screen/SplashScreen.kt, ConnectScreen.kt
│   ├── dashboard/DashboardViewModel.kt   # toolkit-agnostic; StateFlow<List<HeaterSeries>>
│   ├── dashboard/DashboardScreen.kt      # Compose rows + AndroidView(TempGraphView)
│   ├── graph/TempGraphView.kt            # classic View + Canvas (ADR 0001) — the SHARED primitive
│   └── primitives/{ConfirmDialog,NumericKeypad,OnScreenKeyboard,Toast}.kt
```

### Pattern 1: Started foreground service owns the spine; UI observes process-held StateFlows
**What:** The service constructs the spine in its `serviceScope`, calls `startForeground()`, and runs `session.run()`. The StateFlows are held in an `Application`-scoped `AppContainer` (or exposed by the service into it) so the Activity collects them without binding.
**When to use:** Always for this app — it is SHELL-03.
**Example:**
```kotlin
// service/MoonrakerService.kt  [ASSUMED shape — verify FGS APIs at plan time]
class MoonrakerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sessionJob: Job? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        startForeground(NOTIF_ID, buildStatusNotification()) // must be called fast (ANR window)
        observeConfigAndRebuild()
        return START_STICKY
    }

    private fun observeConfigAndRebuild() = serviceScope.launch {
        appContainer.connectionStore.config.collectLatest { cfg ->
            sessionJob?.cancelAndJoin()                  // tear down cleanly (no socket/scope leak)
            if (cfg == null) return@collectLatest        // no config → idle, route shows ConnectScreen
            val store = PrinterStateStore(serviceScope)  // reuse Phase-2 store
            val rpc = JsonRpcClient(/* ... */)
            val session = MoonrakerSession(
                store, rpc,
                socketEvents = { token -> MoonrakerSocket.real(client, cfg.wsUrl(token)).events() },
                auth = cfg.apiKey?.let { MoonrakerAuth(/* ... */) },
                baseWsUrl = cfg.wsUrl(null),
            )
            appContainer.publish(store, session)         // expose StateFlows + dispatcher
            sessionJob = launch { session.run() }
        }
    }
    override fun onBind(i: Intent?): IBinder? = null     // started, not bound (state is process-held)
    override fun onDestroy() { serviceScope.cancel() }
}
```
**Why this shape:** matches the Phase-2 SUMMARY's explicit "Phase 3 wiring" note — *the foreground service should own a single `MoonrakerSession`; the shell observes its StateFlows; a config change triggers a rebuild (host/key are construction-time inputs, `requestReconnectNow()` alone can't swap them)*. `collectLatest` on the config Flow IS the config-change-recreates-session seam from CONTEXT.

### Pattern 2: `klippy_state`-driven routing as derived state (NOT socket state, NOT Nav-Compose)
**What:** A pure function maps `PrinterState` → a `TopRoute`. The Activity does `when(route)`. The route is recomputed every time `PrinterState` emits.
**Example:**
```kotlin
sealed interface TopRoute { object Connect; object Splash; data class Shell(val landing: Dest) : TopRoute }

fun derive(cfgPresent: Boolean, s: PrinterState): TopRoute = when {
    !cfgPresent                            -> TopRoute.Connect
    s.klippyState != KlippyState.Ready     -> TopRoute.Splash      // Startup/Error/Shutdown/Disconnected → hard override
    s.printState == PrintState.Printing ||
        s.printState == PrintState.Paused  -> TopRoute.Shell(Dest.Job)   // LAND on Job, nav stays open
    else                                   -> TopRoute.Shell(Dest.Dashboard)
}
```
- **Splash is a hard override** while Klippy is non-ready: the user cannot reach panels (CONTEXT). It surfaces `state_message` reason text + recovery actions.
- **Print-active is a *landing*, not a lock** (CONTEXT): inside `Shell`, the rail destination is independent user state; "home" returns to Job. Don't trap the user mid-print.
- Routing reads `klippyState`/`printState`, **never** `connection: ConnectionState` — that drives the *chrome status indicator* only (CONN-06).

### Pattern 3: Toolkit-agnostic dashboard ViewModel feeds both Compose rows and the Views `Canvas` graph (the load-bearing forward-compat decision)
**What:** A `DashboardViewModel` (plain class, no Compose/Android-View imports) exposes a `StateFlow` of a render-ready series model. The Compose dashboard collects it for heater rows; the classic-Views `TempGraphView` (hosted via `AndroidView`) collects the *same* StateFlow for the sparkline. Phase 4's full temperature panel reuses `TempGraphView` and the same series model, only widening the window.
**Example:**
```kotlin
// ui/dashboard/DashboardViewModel.kt — TOOLKIT-AGNOSTIC (StateFlow in, StateFlow out)
class DashboardViewModel(printerState: StateFlow<PrinterState>, scope: CoroutineScope) {
    // The state layer ALREADY throttles to ~4 Hz (PrinterStateStore.DEFAULT_SAMPLE_MS=250).
    // Do NOT add a second throttle here; just accumulate a bounded ring buffer per heater.
    val series: StateFlow<List<HeaterSeries>> = printerState
        .map { it.heaters }
        .runningFold(initialRing()) { ring, heaters -> ring.appendBounded(heaters) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), initialRing())
}

// ui/graph/TempGraphView.kt — classic View, custom Canvas (ADR 0001), the SHARED primitive
class TempGraphView(ctx: Context) : View(ctx) {
    fun bind(series: StateFlow<List<HeaterSeries>>, scope: CoroutineScope) =
        scope.launch { series.collect { data -> latest = data; postInvalidateOnAnimation() } }
    override fun onDraw(c: Canvas) { /* draw bounded ring; reuse Paint/Path, no per-frame alloc */ }
}

// ui/dashboard/DashboardScreen.kt — Compose hosts the View
@Composable fun DashboardScreen(vm: DashboardViewModel) {
    Column {
        val rows by vm.series.collectAsStateWithLifecycle()
        rows.forEach { HeaterRow(it.name, it.current, it.target) }   // Compose rows (low churn)
        AndroidView(factory = { TempGraphView(it).also { v -> v.bind(vm.series, scope) } })
    }
}
```
**Why this matters:** This is the phase's stated "graph architecture settled HERE, not discovered late" (SHELL-03). The seam is: **throttle lives in the state layer (done), the series model + the `Canvas` View are the reusable render primitive, and the ViewModel is toolkit-agnostic so Compose and Views consume identically.** Phase 4 extends `TempGraphView` (longer window, axes/markers, `server.temperature_store` backfill) without re-architecting.

### Pattern 4: `CommandDispatcher` — the single action path (PRIM-05) that composes with confirm (PRIM-03)
**What:** Every Moonraker *action* (estop, restart, pause, home, set-temp, …) goes through one wrapper that (a) enforces an explicit timeout, (b) tracks an in-flight/busy set so the button disables, (c) debounces re-taps. The confirm dialog gates the destructive subset *before* dispatch.
**Example:**
```kotlin
class CommandDispatcher(private val rpc: () -> JsonRpcClient?, private val scope: CoroutineScope) {
    private val _inFlight = MutableStateFlow<Set<String>>(emptySet())
    val inFlight: StateFlow<Set<String>> = _inFlight.asStateFlow()
    private val lastFired = mutableMapOf<String, Long>()

    fun dispatch(key: String, method: String, params: JsonElement? = null,
                 timeoutMs: Long = 8_000, debounceMs: Long = 400) {
        val now = SystemClock.elapsedRealtime()
        if (key in _inFlight.value) return                                 // in-flight guard
        if (now - (lastFired[key] ?: 0) < debounceMs) return               // debounce
        lastFired[key] = now
        scope.launch {
            _inFlight.update { it + key }
            try { rpc()?.request(method, params, timeoutMs) }              // explicit timeout
            catch (e: RpcConnectionException) { toast(Severity.Error, e.message) } // PRIM-04
            finally { _inFlight.update { it - key } }
        }
    }
}
// Confirm + dispatch composition:
fun onCancelPrint() = confirm(destructive = true, action = "Cancel print") {
    dispatcher.dispatch("cancel", "printer.print.cancel")
}
```
- The Phase-2 `JsonRpcClient.request()` *already* enforces a `withTimeout` and fails-fast on no-connection — `CommandDispatcher` adds the **UI-affordance** layer (busy/debounce) on top, it does not reimplement transport.
- E-stop is fast-but-deliberate: a **hold or double-tap gesture** in the shell, NOT routed through the slow modal `ConfirmDialog` (CONTEXT: "fast-but-deliberate (hold/double-tap) not a slow modal"). It still goes through `CommandDispatcher`.

### Pattern 5: NsdManager best-effort scan, manual entry is the floor
**What:** On the connect screen, optionally run an `NsdManager.discoverServices("_moonraker._tcp", PROTOCOL_DNS_SD, listener)`; resolve hits to host/port; show a pick-list. Manual host/port/key fields are always present and never gated on the scan.
**Example:** Acquire a `WifiManager.MulticastLock` for the scan window (multicast frames are dropped on Wi-Fi without it); release it when discovery stops. Serialize `resolveService` calls (NsdManager pre-API-29 cannot resolve concurrently — queue them).

### Anti-Patterns to Avoid
- **Owning the connection in the Activity / a ViewModel scoped to the Activity.** Dies on rotation — the exact failure SHELL-03 forbids. Connection lives in the service.
- **Routing off `ConnectionState`.** Socket-connected ≠ Klippy-ready. Route off `klippyState`; `ConnectionState` is chrome only. (Explicit in CONTEXT + STATE-04.)
- **Adding a second throttle in the dashboard.** The state layer already conflates to ~4 Hz; re-throttling double-samples and adds lag.
- **Drawing the temp graph in Compose `Canvas`.** ADR 0001 mandates classic Views for this high-churn surface (measured ~2× lower p95). Compose hosts it via `AndroidView`.
- **Binding the service to observe state.** Unnecessary; the StateFlows are process-held. Binding only matters if the UI needs to *call into* the service synchronously — it doesn't (config changes go through DataStore).
- **Per-frame allocation in `onDraw`.** On Adreno 320 this is jank. Reuse `Paint`/`Path`, use `postInvalidateOnAnimation()`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Settings persistence | Custom file/SharedPrefs wrapper | DataStore Preferences | Coroutine-safe, no main-thread ANR, atomic writes. |
| mDNS / DNS-SD | Raw multicast socket + packet parser | `NsdManager` | DNS-SD wire format is fiddly; the platform does it (best-effort). |
| Request timeout/correlation | New timeout logic | Phase-2 `JsonRpcClient.request(timeoutMs)` | Already enforces `withTimeout` + id-correlation + fail-fast. Wrap, don't replace. |
| Reconnect/backoff | New reconnect loop | Phase-2 `MoonrakerSession` + `requestReconnectNow()` | Gate-proven on real hardware incl. Wi-Fi-yank. |
| Lifecycle-safe Flow collection | Manual `LaunchedEffect`+repeatOnLifecycle | `collectAsStateWithLifecycle()` | Already in catalog (`lifecycle-runtime-compose`); correct STARTED-scoped collection. |

**Key insight:** Phase 3 is ~80% *wiring existing proven parts into Android lifecycle scaffolding* and ~20% new UI primitives. The temptation is to re-solve connection/timeout/reconnect concerns in the UI layer — don't; the spine already owns them. The genuinely new code is: the service shell, the DataStore config, the route-derivation function, the `CommandDispatcher` affordance layer, the `TempGraphView`, and the four UI primitives.

## Runtime State Inventory

> Phase 3 introduces app wiring; it is not a rename/refactor phase. The one stateful migration is config source.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Connection config moves from `BuildConfig`/`local.properties` (`DevConfig`) → DataStore. No existing user data to migrate (Phase 2 used a static dev config; there are no shipped users). | Code: new `ConnectionStore`; `DevConfig` retained only as a URL-shape model reference (CONTEXT). First-run with empty DataStore → ConnectScreen. |
| Live service config | None — no external service config embeds app strings. | None — verified by CONTEXT `<code_context>`. |
| OS-registered state | New: a `NotificationChannel` (API 26+) for the FGS notification; a manifest `<service>` + `<property>` FGS-subtype tag. None pre-existing to migrate. | Code: register channel on first service start; add manifest entries. |
| Secrets/env vars | API key moves from `BuildConfig.MOONRAKER_API_KEY` → DataStore (user-entered). `MoonrakerAuth` reads it at session-construction time (unchanged contract). | Code: feed DataStore key into session rebuild; never log it (existing redaction rule). |
| Build artifacts | None — no compiled artifact carries a renamed string. | None. |

## Common Pitfalls

### Pitfall 1: `startForeground()` not called fast enough / wrong type → crash or ANR
**What goes wrong:** On API 26+ (your API-30 device) the service must call `startForeground()` within ~5s of `startForegroundService()`, with a notification on a registered channel, and (API 34+, your `targetSdk 35`) declare a `foregroundServiceType` matching the manifest. Miss it → `ForegroundServiceDidNotStartInTimeException` / `MissingForegroundServiceTypeException`.
**Why it happens:** Channel not registered, or type declared in manifest but not passed/declared correctly.
**How to avoid:** Register the `NotificationChannel` (IMPORTANCE_LOW) before `startForeground`; declare `android:foregroundServiceType="specialUse"` + the `<property>` subtype + both permissions (below). On API 30 the type-permission is not *enforced* (it became required at API 34), but declaring it is correct for `targetSdk 35` and newer hardware (CONTEXT).
**Warning signs:** Service crashes seconds after launch; logcat `Context.startForegroundService() did not then call Service.startForeground()`.

### Pitfall 2: mDNS scan finds nothing because Moonraker's `[zeroconf]` is opt-in
**What goes wrong:** The `NsdManager` scan for `_moonraker._tcp` returns zero results on a perfectly working printer.
**Why it happens:** Moonraker's mDNS advertisement comes from the **`[zeroconf]` component, which is NOT enabled by default** — the user must add a `[zeroconf]` section to `moonraker.conf`. Most installs don't. `[VERIFIED: github.com/Arksine/moonraker components/zeroconf.py — registers "_moonraker._tcp.local.", conditionally initialized from config]`
**How to avoid:** This is *exactly* why CONTEXT mandates **manual entry as the floor**. Treat a empty scan as normal, not an error. Don't show "no printer found" as a failure — show the manual fields prominently and the scan as a convenience.
**Warning signs:** Testers report "scan never finds my printer" — it's working as designed for default Moonraker.

### Pitfall 3: NsdManager unreliability on older Android + Wi-Fi multicast loss
**What goes wrong:** Discovery hangs, mis-fires callbacks, or silently drops services; worse on weak Wi-Fi.
**Why it happens:** `NsdManager` had documented lockups on KitKat/Nexus-7-2012 (better on 5.0+, so your API-30 device is OK) `[CITED: vshivam.github.io jmDNS-on-Android; androidmetro NsdManager]`; and 802.11 does not ACK multicast frames so loss is inherent `[CITED: RFC 7558]`. Pre-API-29 `resolveService` can't run concurrently.
**How to avoid:** Acquire a `MulticastLock` for the scan window; serialize resolves; bound the scan with a timeout and a "rescan" button; never block connecting on it. Manual entry always works.
**Warning signs:** `onResolveFailed` with `FAILURE_ALREADY_ACTIVE`; duplicate/missing `onServiceFound`.

### Pitfall 4: Config change rebuild leaks the old socket/scope
**What goes wrong:** Saving a new host spawns a second live session; the old socket keeps reconnecting in the background.
**Why it happens:** Launching a new `session.run()` without cancelling the previous job.
**How to avoid:** `sessionJob?.cancelAndJoin()` *before* building the new session (CONTEXT: "must not leak the old socket/scope; the rebuild is the clean seam"). `collectLatest` on the config Flow gives this for free if structured correctly.
**Warning signs:** Two reconnect log streams; temps flicker between two printers.

### Pitfall 5: Mock-looser-than-server (the Phase-2 lesson, re-applied)
**What goes wrong:** Tests of the service/command path pass against a fake that accepts any RPC, but the real action RPC has a required arg or different method name.
**Why it happens:** The exact trap that hid the `identify` `url` bug in Phase 2 — the fake encoded the code's assumption, not Moonraker's contract.
**How to avoid:** Use the **exact verified method strings** (below); assert them in tests; keep an on-device gate for the action set (estop is observable as a Klippy `shutdown` transition). `[VERIFIED: 02-04-SUMMARY.md]`
**Warning signs:** Green JVM suite, dead buttons on the real printer.

### Pitfall 6: Recovery actions on Splash that Moonraker can't currently satisfy
**What goes wrong:** Splash offers "firmware restart" while Klippy is disconnected and it appears to do nothing.
**Why it happens:** `printer.firmware_restart` is *postponed* until Klippy reconnects if it can't run immediately `[VERIFIED: moonraker.readthedocs.io external_api/printer]`. Also some recovery (host service restart) is gated behind Moonraker auth/permissions and may not be exposed.
**How to avoid:** Offer the actions that map to verified methods (below); show the postponed/queued nature in UI copy; gate "where exposed" (CONTEXT) by capability/permission, don't assume.

## Code Examples

### Verified Moonraker recovery/action RPC methods (for Splash recovery + E-stop + confirm set)
```
// Source: moonraker.readthedocs.io/en/latest/external_api/printer/  [VERIFIED]
"printer.emergency_stop"   // E-stop: immediately halts printer → Klippy "shutdown" state
"printer.firmware_restart" // full restart, resets app + MCUs (postponed if Klippy disconnected)
"printer.restart"          // soft restart: reload Klippy app + config, MCUs not reset
"printer.print.cancel"     // cancel active print  (confirm set)
"printer.print.pause"      // pause   (Job panel, Phase 6)
"printer.print.resume"     // resume  (Job panel, Phase 6)
// Klippy state + reason text for Splash:
"printer.info" -> { "state": "ready|startup|error|shutdown", "state_message": "<human reason>" }
// state already live in PrinterState.klippyState; state_message is the reason text to surface.
```
Note: `disable motors` (confirm set) is a G-code (`M84` / `SET_STEPPER_ENABLE`) sent via `printer.gcode.script` — the action lives in Phase 4 (Move), but the **confirm gate** for it is established here. `restart print` is `printer.print.start` of the same file (Phase 6); its confirm gate is established here too.

### specialUse FGS manifest (verified declaration shape)
```xml
<!-- Source: developer.android.com/develop/background-work/services/fgs/service-types  [VERIFIED] -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<!-- API 33+ runtime notification permission for the persistent status notification -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- only if mDNS scan is implemented: -->
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />

<service
    android:name=".service.MoonrakerService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Maintains a persistent LAN websocket session to a Klipper/Moonraker 3D printer for live status and control" />
</service>
```
- `POST_NOTIFICATIONS` is a **runtime** permission on API 33+ (your API-30 device does not need the runtime grant — channel-only — but `targetSdk 35` means request it where present; if denied, the FGS still runs, the notification is just suppressed).
- The Play-Console `specialUse` justification is moot (sideload) per CONTEXT; the `<property>` value is still required syntactically on API 34+.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Untyped foreground service | Mandatory `foregroundServiceType` | Android 14 / API 34 | Must declare `specialUse` + property for `targetSdk 35`; no-op enforcement on API 30. `[VERIFIED]` |
| SharedPreferences | DataStore | DataStore GA 2021 | Coroutine-safe settings; the project standard. |
| `collectAsState()` | `collectAsStateWithLifecycle()` | lifecycle 2.6+ | STARTED-scoped collection avoids work while backgrounded. |
| Navigation back-stack everywhere | Reactive `when` for state-driven top route | n/a (design choice) | CONTEXT-locked: top route follows `klippy_state`, not a stack. |

**Deprecated/outdated:** none new this phase. Do NOT bump Compose BOM to the 2026.05 *alpha* (→ Compose 1.12 / compileSdk 37 / AGP 9 — the churn the project avoids).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `datastore-preferences` 1.1.1 is the current minSdk-23-safe pin | Standard Stack | Low — `verifyMinSdk` catches a floor violation; re-confirm exact patch at plan time. |
| A2 | The exact `MoonrakerService`/`AppContainer`/`CommandDispatcher` code shapes | Patterns 1–4 | Medium — these are illustrative skeletons, not verified against a compiled build; the *contracts* (StateFlow types, `request()` signature, `MoonrakerSession` ctor) ARE verified from source. |
| A3 | On API 30 the `specialUse` type-permission is declared-but-not-enforced (enforcement began API 34) | Pitfall 1 / CONTEXT | Low — matches official FGS-types-required (API 34) doc and CONTEXT decision. |
| A4 | `disable motors` / `restart print` map to gcode / `print.start` (actions land in Phase 4/6, confirm gate here) | Code Examples | Low — confirm gate is the Phase-3 deliverable; action wiring is later-phase. |
| A5 | Holding a `MulticastLock` is needed for reliable mDNS scan on Wi-Fi | Pattern 5 / Pitfall 3 | Low — well-established Android behavior; scan is best-effort regardless. |

## Open Questions

1. **Persistent notification content + tap target**
   - What we know: needs a low-importance channel; should show connection/printer status and tap back into the app (CONTEXT open question).
   - What's unclear: exact status string (Klippy state? temps? connection?) and whether it updates live (battery cost on always-on).
   - Recommendation: static-ish "Connected to <host>" + Klippy state, updated on `ConnectionState`/`KlippyState` transitions only (not on every temp tick — that'd churn the notification). Settle copy in `/gsd-ui-phase 3`.

2. **`AppContainer` ownership: Application-held vs service-held-published-to-Application**
   - What we know: no service binding needed for state; Phase-2 SUMMARY says "service owns a single session."
   - What's unclear: whether `PrinterStateStore` is constructed by the `Application` (and handed to the service) or by the service (and published into `AppContainer`).
   - Recommendation: **service constructs, publishes into `AppContainer`** — keeps the spine's lifecycle tied to the service that owns it; `AppContainer` is just the typed holder the UI reads. Settle in planning.

3. **Emergency Stop gesture exact form (hold vs double-tap) + placement**
   - What we know: fast-but-deliberate, every screen, not a slow modal (CONTEXT).
   - What's unclear: hold-duration vs double-tap, top-bar vs FAB.
   - Recommendation: defer the *interaction* tuning to `/gsd-ui-phase 3`; the plan must establish that it (a) lives in shell chrome, (b) goes through `CommandDispatcher`, (c) is reachable on every route including Splash.

## Environment Availability

> External dependencies for this phase are the same LAN Moonraker + device toolchain already proven in Phases 1–2; no new external tooling.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Live Moonraker (Ender 5 Plus) | On-device gate for routing/estop/config | ✓ (proven Phase 2) | v0.13 @ 192.168.1.120:7125 | Mock socket harness for JVM tests |
| `flox` Nexus 7 device | Render-cadence + rotation-survival gates | ✓ | LineageOS 18.1 / API 30 | none — the only hardware that proves jank/Doze |
| Windows-side Gradle (`E:\Android\gw.bat`) | Build/install | ✓ | JDK 21 / SDK 35 | none (`./gradlew` won't run from WSL) |
| `adb` (interop) | Install + `dumpsys gfxinfo` capture | ✓ | platform-tools | none |
| Moonraker `[zeroconf]` enabled | mDNS scan to find anything | ✗ (opt-in, likely off) | — | **Manual host/port entry (the floor)** |

**Missing with no fallback:** none.
**Missing with fallback:** `[zeroconf]` advertisement — the scan is best-effort; manual entry always works.

## Validation Architecture

> Nyquist validation is enabled. Per success criterion: the observable signal + the cheapest point it can be sampled. Existing test infra (JVM `:app:testDebugUnitTest` + on-device `:app:connectedDebugAndroidTest` via `gw.bat`, gfxinfo framestats parser) carries most of it.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 + `kotlinx-coroutines-test` (virtual time) for JVM; AndroidX Test + UiAutomator for on-device |
| Config file | `app/build.gradle.kts` (test + androidTest source sets exist from Phase 2) |
| Quick run command | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| Full suite command | above + `:app:connectedDebugAndroidTest` on `flox` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command / Signal | File Exists? |
|--------|----------|-----------|----------------------------|-------------|
| CONN-01 | Config persists across restart | unit (DataStore) + instrumented | `pytest`-equiv: JVM test on `ConnectionStore` round-trip; instrumented: write→kill→relaunch→read | ❌ Wave 0 |
| SHELL-03 | **Service survives rotation** | instrumented (UiAutomator) | Connect → rotate device (`UiDevice.setOrientationLeft/Natural`) → assert `ConnectionState` stays `Connected` (no Connecting/Syncing transition) and `PrinterState` flow uninterrupted | ❌ Wave 0 |
| SHELL-03 | Service survives screen-off | on-device manual gate | Screen off 60s, screen on → connection still `Connected`, temps live (not stale) | manual |
| STATE-04/route | Routing keyed off `klippy_state` | **unit (pure `derive()`)** | JVM: `derive(cfg, PrinterState(klippyState=Startup))==Splash`; `Ready+Printing==Shell(Job)`; `Ready==Shell(Dashboard)`; assert socket-state does NOT change route | ❌ Wave 0 |
| SHELL-05 | Splash shows reason + recovery | unit + instrumented | JVM: Splash VM exposes `state_message`; instrumented: shutdown state → recovery buttons visible | ❌ Wave 0 |
| SHELL-04 | **Dashboard renders at throttled cadence w/o jank** | on-device gfxinfo gate | Live heat ramp → `dumpsys gfxinfo reset` → 30s dwell → framestats parsed: p95 < ADR floor (~42ms Views), 0 frozen frames; cadence ≈ 4 Hz (count updates/sec) | manual (reuse Phase-1 parser) |
| SHELL-01/02 | Status visible + E-stop every screen | instrumented | UiAutomator: on each route (Splash/Dashboard/Job) assert status chip + E-stop affordance present; E-stop hold → `printer.emergency_stop` sent | ❌ Wave 0 |
| SHELL-02 | E-stop reaches printer | **on-device round-trip** | Real printer: E-stop → assert Klippy transitions to `shutdown` (`klippyState==Shutdown`) within timeout | manual gate |
| PRIM-05 | timeout + in-flight + debounce | **unit (virtual time)** | JVM with `runTest`: double-tap within debounce → 1 dispatch; in-flight key blocks re-entry; dropped-packet → timeout fires, button re-enables | ❌ Wave 0 |
| PRIM-03 | Confirm gates destructive set | unit + instrumented | JVM: each destructive action requires confirm callback before dispatch; instrumented: tapping estop/cancel shows dialog | ❌ Wave 0 |
| PRIM-01/02/04 | Keypad/keyboard/toast exist + consumable | instrumented (Compose UI test) | UiAutomator/Compose-test: keypad emits value, keyboard emits text, toast shows by severity | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `:app:testDebugUnitTest` (pure logic: `derive()`, `CommandDispatcher`, `ConnectionStore`, dashboard ring).
- **Per wave merge:** add `:app:connectedDebugAndroidTest` (rotation-survival, route-screen presence) on `flox`.
- **Phase gate:** full suite green + **two on-device human gates**: (1) rotation/screen-off connection survival, (2) gfxinfo dashboard-cadence-without-jank during a live heat. Mirrors the Phase-2 "real-hardware gate is the backstop" discipline.

### Wave 0 Gaps
- [ ] `ConnectionStoreTest.kt` — DataStore round-trip (CONN-01)
- [ ] `TopRouteTest.kt` — pure `derive()` cases incl. socket-state-does-not-route (STATE-04)
- [ ] `CommandDispatcherTest.kt` — debounce/in-flight/timeout under `runTest` (PRIM-05)
- [ ] `ConfirmGateTest.kt` — destructive set requires confirm (PRIM-03)
- [ ] `DashboardRingTest.kt` — bounded ring accumulation off `PrinterState`
- [ ] instrumented `ServiceSurvivesRotationTest.kt` (androidTest) — SHELL-03
- [ ] instrumented `ShellPresenceTest.kt` — status + E-stop on every route (SHELL-01/02/04)
- [ ] reuse Phase-1 `gfxinfo` parser for the dashboard-cadence gate (no new tool)

## Security Domain

> `security_enforcement: true`, ASVS level 1. This is a LAN appliance; the relevant surface is small but real.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (minimal) | Optional Moonraker API key via DataStore → existing `MoonrakerAuth` (oneshot-token + `X-Api-Key`). No new auth surface. |
| V3 Session Management | no | No app-side sessions; Moonraker owns the websocket session. |
| V4 Access Control | no | Single local user on a wall-mounted tablet; no multi-user model. |
| V5 Input Validation | yes | Validate host/port from the config screen + NSD results before building a URL (reject malformed host, port out of 1–65535). |
| V6 Cryptography | no | Cleartext `ws://`/`http://` is a *deliberate* LAN decision (CONN-05, owned by the shared manifest/NSC) — not a finding. Do NOT hand-roll TLS. |

### Known Threat Patterns for Android FGS + LAN config

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| API key leaked to logcat / notification / `?token=` URL | Information Disclosure | Existing redaction rule (never log key or full token URL); do NOT put the key in the notification text. `[CITED: DevConfig.kt T-02-01]` |
| Exported service started by another app | Elevation/Tampering | `android:exported="false"` on `MoonrakerService` (it's launched only by the app). |
| Malformed/hostile mDNS TXT record drives a bad URL | Tampering | Validate resolved host/port before constructing the ws/http URL (V5); treat NSD data as untrusted input. |
| Notification tap intent hijack | Tampering | Use an explicit `PendingIntent` to `MainActivity` with `FLAG_IMMUTABLE` (required API 31+; good practice on 30). |

No HIGH findings that block the phase. The cleartext posture is an accepted, documented project decision, not a vulnerability to remediate.

## Sources

### Primary (HIGH confidence)
- `moonraker.readthedocs.io/en/latest/external_api/printer/` — exact RPC method strings (`printer.emergency_stop`, `firmware_restart`, `restart`), `printer.info` `state`/`state_message`. **VERIFIED**
- `github.com/Arksine/moonraker` `moonraker/components/zeroconf.py` — `_moonraker._tcp.local.` service type, TXT records, **opt-in `[zeroconf]`**. **VERIFIED**
- `developer.android.com/develop/background-work/services/fgs/service-types` + `/about/versions/14/changes/fgs-types-required` — `specialUse` declaration, `<property>` subtype, permissions, API-34 enforcement. **VERIFIED**
- Repo source read this session: `MoonrakerSession.kt`, `PrinterState.kt`, `PrinterStateStore.kt`, `JsonRpcClient.kt`, `MoonrakerSocket.kt`, `DevConfig.kt`, `AndroidManifest.xml`, `libs.versions.toml`, ADR 0001, `02-04-SUMMARY.md`. **VERIFIED**

### Secondary (MEDIUM confidence)
- `developer.android.com/jetpack/androidx/releases/datastore` — datastore-preferences 1.1.x minSdk/version (re-confirm exact pin at plan time).

### Tertiary (LOW confidence)
- NsdManager reliability anecdotes (vshivam.github.io jmDNS, androidmetro NsdManager); RFC 7558 (multicast loss) — directional, not load-bearing; manual entry is the floor regardless.

## Metadata

**Confidence breakdown:**
- Service/FGS architecture: HIGH — verified FGS docs + Phase-2 SUMMARY's explicit wiring guidance.
- Routing model: HIGH — pure function over already-first-class `PrinterState` fields; CONTEXT-locked.
- Render/throttle primitive seam: HIGH — throttle exists in code; ADR 0001 dictates the Views `Canvas`; the toolkit-agnostic ViewModel seam is the standard hybrid pattern.
- Moonraker action/recovery methods: HIGH — verified against official external API docs.
- mDNS: MEDIUM-HIGH — service type + opt-in nature verified; reliability is inherently best-effort.
- Code skeletons: MEDIUM — illustrative; contracts verified, exact code is for the planner to specify.

**Research date:** 2026-05-30
**Valid until:** ~2026-06-29 (30 days; stable stack, pinned versions). Re-confirm datastore pin and any Compose BOM channel note at plan time.
