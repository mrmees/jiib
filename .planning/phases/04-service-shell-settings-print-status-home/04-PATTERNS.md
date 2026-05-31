# Phase 4: Service, Shell, Settings & Print-Status Home - Pattern Map

**Mapped:** 2026-05-31
**Files analyzed:** 16 new + 2 modified
**Analogs found:** 14 / 16 new (2 are genuinely new infra with only partial analogs)

> Package root: `works.mees.dinghy` at `app/src/main/java/works/mees/dinghy/`.
> Every signature below is **verified against the actual source file** (line numbers cited), not the
> RESEARCH skeletons (which use illustrative names like `AppContainer`/`TempGraphView` that do NOT
> match the real types). Where RESEARCH and reality diverge, **reality wins** — notes flag it.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `DinghyApp.kt` (new) | provider (Application) | — (holds singletons) | `gallery/GalleryActivity.kt` (sole-assembler idiom) | partial (no Application exists) |
| `di/AppContainer.kt` (new) | service-locator | request-response (holds StateFlows) | `gallery/GalleryActivity.kt` assembler block (L50-58) | role-match |
| `service/MoonrakerService.kt` (new) | service (FGS) | event-driven (config→rebuild) | `gallery/GalleryActivity.kt` spine assembly + RESEARCH Pattern 1 | partial (no service exists) |
| `config/ConnectionConfig.kt` (new) | model | — | `config/DevConfig.kt` | exact (URL-shape model) |
| `config/ConnectionStore.kt` (new) | store (DataStore) | CRUD (persist) | `theme/ThemePrefs.kt` | exact |
| `config/MoonrakerDiscovery.kt` (new) | utility (NsdManager) | event-driven (scan) | — (no NSD analog) | NONE |
| `command/CommandDispatcher.kt` (new) | utility (action wrapper) | request-response | `net/JsonRpcClient.kt` (`request()` it wraps) | role-match |
| `ui/route/TopRoute.kt` (new) | utility (pure derive) | transform | `state/DeriveCapabilities.kt` (pure derive idiom) | role-match |
| `ui/route/RouteHolder` or in-Activity `when` (new) | store/state | request-response | RESEARCH Pattern 2 | role-match |
| `ui/shell/AppShell.kt` + `AppDrawer.kt` (new) | component | event-driven (swipe nav) | `gallery/GalleryScreen.kt` (Compose host idiom) | role-match |
| `ui/screen/SplashScreen.kt` (new) | component | request-response | `designsystem/ConfirmGuard.kt` (ScreenScaffold + recovery buttons) | role-match |
| `ui/screen/SettingsScreen.kt` (new) | component | CRUD (form→DataStore) | `gallery/GalleryScreen.kt` (scrollable token-themed list) | role-match (exempt from F/F/G) |
| `ui/printstatus/PrintStatusScreen.kt` (new) | component | streaming (live state) | `gallery/GalleryScreen.kt` ring+graph block (L189-204) | exact |
| `ui/printstatus/PrintStatusViewModel` or holder (new) | service (toolkit-agnostic) | transform (state→series) | `state/PrinterStateStore.kt` StateFlow idiom + `render/RingBuffer.kt` | role-match |
| `MainActivity.kt` (MODIFIED) | provider (Compose host) | request-response | `gallery/GalleryActivity.kt` | exact |
| `AndroidManifest.xml` (MODIFIED) | config | — | current manifest (add service + perms) | exact |

---

## Pattern Assignments

### `service/MoonrakerService.kt` (FGS, event-driven) — the load-bearing new file

**Analogs:** `gallery/GalleryActivity.kt` (the only existing place the spine is assembled) +
the **verified spine constructors** below. There is NO existing service; copy the *assembly order*
from RESEARCH Pattern 1 but use these **real signatures** — the RESEARCH skeleton invents
`MoonrakerSocket.real(client, cfg.wsUrl(token))` and `appContainer.publish(...)` which do not exist.

**Spine assembly — VERIFIED constructor shapes (copy these exactly):**

`PrinterStateStore` — `state/PrinterStateStore.kt:39-42`:
```kotlin
class PrinterStateStore(
    scope: CoroutineScope,                       // pass the serviceScope
    private val sampleMillis: Long = DEFAULT_SAMPLE_MS,  // 250L; the ~4Hz throttle is ALREADY built
)
// exposes: printerState: StateFlow<PrinterState>, capabilities: StateFlow<Capabilities>,
//          gcodeResponses: SharedFlow<String>
```

`JsonRpcClient` — `net/JsonRpcClient.kt:50-52`:
```kotlin
class JsonRpcClient(private val defaultTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS) // 10_000L
// the action method CommandDispatcher wraps: request(method, params, timeoutMs): JsonElement
```

`MoonrakerSocket.real(...)` — `net/MoonrakerSocket.kt:120-127` (note: defaults to `DevConfig.wsUrl`,
so you MUST pass the DataStore-sourced `wsUrl` explicitly, and pass the token-bearing URL for the auth
path via `MoonrakerAuth.buildAuthedWsUrl`):
```kotlin
fun real(
    client: OkHttpClient = defaultClient(),
    wsUrl: String = DevConfig.wsUrl,             // OVERRIDE with ConnectionConfig.wsUrl
): MoonrakerSocket
// .events(): Flow<SocketEvent>   ← this is what MoonrakerSession.socketEvents wraps
// MoonrakerSocket.defaultClient() gives the one shared OkHttpClient (reuse for REST/auth too)
```

`MoonrakerAuth` — `auth/MoonrakerAuth.kt:36-40` (construct ONLY when keyed):
```kotlin
class MoonrakerAuth(
    private val callFactory: Call.Factory,       // pass the shared OkHttpClient (it IS a Call.Factory)
    private val httpBase: String,                // ConnectionConfig.httpBase
    private val apiKey: String?,
)
```

`MoonrakerSession` — `net/MoonrakerSession.kt:60-74` (the real ctor — note `socketEvents` is
`(token: String?) -> Flow<SocketEvent>`, and `clientUrl` already correctly defaults to the
live-verified `https://mees.works/dinghy-display`; **do NOT regress that**):
```kotlin
class MoonrakerSession(
    private val store: PrinterStateStore,
    private val rpc: JsonRpcClient,
    private val socketEvents: (token: String?) -> Flow<SocketEvent>,
    private val auth: MoonrakerAuth? = null,
    private val baseWsUrl: String = "",
    private val backoffBase: Duration = DEFAULT_BASE,
    private val rng: Random = Random.Default,
    private val clientName: String = "Dinghy Display",
    private val clientVersion: String = "0.1.0",
    private val clientUrl: String = "https://mees.works/dinghy-display",  // REQUIRED by Moonraker — keep
)
// exposes: connectionState: StateFlow<ConnectionState>; fun requestReconnectNow()
// run it: suspend fun run()  ← launch in serviceScope; cancel the Job to tear down (D-03)
```

**Config-change rebuild seam (D-03 / Pitfall 4)** — `collectLatest` on the config Flow, cancel the
old `sessionJob` BEFORE building the new session (mirrors the `MoonrakerSession.run()` use of
`cancelAndJoin()` at `net/MoonrakerSession.kt:209,241`):
```kotlin
appContainer.connectionStore.config.collectLatest { cfg ->
    sessionJob?.cancelAndJoin()          // no socket/scope leak — the clean seam
    if (cfg == null) return@collectLatest // route shows Settings/Connect prompt (D-11)
    /* build store/rpc/socket/auth/session from cfg as above */
    appContainer.publishSpine(store, session, dispatcher)
    sessionJob = launch { session.run() }
}
```

**FGS lifecycle:** call `startForeground(NOTIF_ID, notif)` within ~5s; register an `IMPORTANCE_LOW`
`NotificationChannel` first (Pitfall 1). `onBind = null` (started, NOT bound — state is process-held).
`onDestroy { serviceScope.cancel() }`.

---

### `MainActivity.kt` (MODIFIED — provider/Compose host)

**Analog:** `gallery/GalleryActivity.kt` (EXACT — it explicitly says it "mirrors MainActivity's
`setContent` host shape"; now MainActivity grows up into the real version).

**Copy the host + theme-boundary pattern** (`gallery/GalleryActivity.kt:42-74`):
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DinghyApp).container   // service-locator, not GalleryActivity's local build
        setContent {
            DinghyTheme(container.themeResolver) {             // DinghyTheme(resolver){…} boundary — verified DinghyTheme.kt:35
                Surface(Modifier.fillMaxSize()) {
                    val state by container.printerState.collectAsStateWithLifecycle() // verified collector idiom
                    val route = derive(container.hasConfig, state)
                    when (route) { /* Splash | Settings | Shell(...) */ }
                }
            }
        }
    }
}
```
Current `MainActivity.kt` (L23-34) is the placeholder being replaced. Start the service from here
(`startForegroundService(Intent(this, MoonrakerService::class.java))`).

---

### `di/AppContainer.kt` (service-locator) + `DinghyApp.kt` (Application)

**Analog:** the assembler block in `gallery/GalleryActivity.kt:50-58` — it builds `ThemeResolver` +
`PrinterStateStore` and hands them down. AppContainer is that block promoted to an
`Application`-held singleton. **NO Hilt** (CLAUDE.md / D-02).

`ThemeResolver` ctor to hold — `theme/ThemeResolver.kt:82-86`:
```kotlin
class ThemeResolver(
    base: ThemeBase = ThemeBase.Dark,
    deltas: TokenDelta = TokenDelta.EMPTY,
    fs: Float = FontScale.M.multiplier,
)
// seed from ThemePrefs.flow (collect once, call resolver.apply(base, deltas, fs) — ThemeResolver.kt:115)
```
The container exposes the spine StateFlows (`printerState`, `capabilities`, `connectionState`,
`gcodeResponses`) the service publishes into it (RESEARCH Open Q2 recommendation: **service
constructs, publishes into container**), plus the `CommandDispatcher`, `themeResolver`, and the two
DataStores. Apply the GalleryActivity discipline: **the screen constructs nothing; the container/host
assembles everything.**

---

### `config/ConnectionStore.kt` (DataStore) + `config/ConnectionConfig.kt` (model)

**`ConnectionStore` analog: `theme/ThemePrefs.kt` (EXACT)** — copy its whole shape:
- ctor takes `DataStore<Preferences>` (`ThemePrefs.kt:35-37`);
- `flow: Flow<…>` with `.catch { if (e is IOException) emit(emptyPreferences()) else throw e }`
  then `.map { sanitize(...) }` (`ThemePrefs.kt:39-52`) — the **fail-safe read contract**;
- `suspend fun set…(...) { dataStore.edit { … } }` writers (`ThemePrefs.kt:54-75`);
- a PURE host-testable `sanitize(...)` companion (`ThemePrefs.kt:103-124`) — validate host/port
  (1-65535) here (V5 input-validation), return `null`/default config on empty store (→ first-run
  Connect prompt, D-11);
- keys via `stringPreferencesKey`/`longPreferencesKey` (`ThemePrefs.kt:85-88`).

> **DECIDE (CONTEXT open q):** share the existing ThemePrefs DataStore or a separate one. ThemePrefs
> currently has no visible `preferencesDataStore` delegate wired (it is constructed with an injected
> `DataStore` — only GalleryActivity seeds from `ThemePrefs.DEFAULT`, never a real file). Phase 4 is
> where the real DataStore file(s) get created in `DinghyApp`. Recommend a **separate**
> `connection.preferences_pb` from `theme.preferences_pb` (different lifecycles, cleaner redaction
> boundary for the API key — T-02-01: never log the key).

**`ConnectionConfig` analog: `config/DevConfig.kt` (EXACT model)** — `DevConfig.kt:33-37` is the
URL-shape source of truth to mirror as instance methods (NOT delete — keep DevConfig as reference):
```kotlin
data class ConnectionConfig(val host: String, val port: Int = 7125, val apiKey: String? = null) {
    val httpBase get() = "http://$host:$port"            // DevConfig.kt:34
    val wsUrl   get() = "ws://$host:$port/websocket"     // DevConfig.kt:37
}
```

---

### `command/CommandDispatcher.kt` (PRIM-05, request-response)

**Analog:** `net/JsonRpcClient.kt` — the dispatcher **wraps** `JsonRpcClient.request()`
(`JsonRpcClient.kt:89-129`), it does NOT reimplement transport. `request()` ALREADY enforces
`withTimeout` + id-correlation + fail-fast-on-no-connection (`JsonRpcClient.kt:119-128`); the
dispatcher adds only the UI-affordance layer (in-flight Set + debounce). Failures surface via
`SeverityToast` (PRIM-04, below).

**StateFlow idiom to copy:** the `MutableStateFlow(...).asStateFlow()` exposure used throughout the
spine (`PrinterStateStore.kt:49-51`, `MoonrakerSession.kt:75-77`):
```kotlin
private val _inFlight = MutableStateFlow<Set<String>>(emptySet())
val inFlight: StateFlow<Set<String>> = _inFlight.asStateFlow()
```
Catch `RpcConnectionException` (the real typed transport failure, `JsonRpcClient.kt:245-248`) — NOT a
generic Exception — and toast `Severity.Error`.

**Verified action method strings** (`net/JsonRpc.kt:93-110` defines `JsonRpcMethods`; the action set
below is NOT yet in that object — add them there or pass literals, verified live in RESEARCH):
```
"printer.emergency_stop"   // D-10 Stop — drives klippy_state → shutdown → Splash recovery
"printer.firmware_restart" // D-12 splash recovery (postponed if Klippy disconnected)
"printer.restart"          // D-12 splash recovery (soft)
```

---

### `ui/route/TopRoute.kt` (pure derive, transform)

**Analog:** `state/DeriveCapabilities.kt` (the existing pure-`derive*` idiom; `MoonrakerSession.kt:257`
calls `deriveCapabilities(objects)`). `derive()` is a pure function over `PrinterState` — unit-test it
in isolation (Wave-0 `TopRouteTest`).

**Routing inputs are VERIFIED first-class fields** (`state/PrinterState.kt`):
- `klippyState: KlippyState` = `{ Disconnected, Startup, Ready, Error, Shutdown }` (`PrinterState.kt:22,69`)
- `printState: PrintState` = `{ Standby, Printing, Paused, Complete, Error, Cancelled }` (`PrinterState.kt:25,72`)
- `connection: ConnectionState` (`PrinterState.kt:55`) — **MUST NOT** drive routing (D-05; it's chrome only).

```kotlin
fun derive(cfgPresent: Boolean, s: PrinterState): TopRoute = when {
    !cfgPresent                        -> TopRoute.Connect                 // D-11 first run → Settings
    s.klippyState != KlippyState.Ready -> TopRoute.Splash                  // D-06 hard override
    s.printState == PrintState.Printing || s.printState == PrintState.Paused
                                       -> TopRoute.Shell(Dest.PrintStatus) // printing view (D-06/D-08)
    else                               -> TopRoute.Shell(Dest.PrintStatus) // idle view (same surface)
}
```
> Note: unlike the RESEARCH skeleton there is NO separate `Dest.Job` in Phase 4 — Print Status is the
> single home surface whose content adapts to `printState` (D-06).

---

### `ui/printstatus/PrintStatusScreen.kt` (component, streaming) — the in-anger render proof

**Analog: `gallery/GalleryScreen.kt:189-204` (EXACT)** — it already composes `ProgressRing` +
`GraphViewHost` side-by-side fed by one `RingBuffer`, the exact pattern Print Status needs.

**`ProgressRing` (printing Focus, D-08)** — `render/ProgressRing.kt:40`:
```kotlin
@Composable fun ProgressRing(progress: Float, modifier: Modifier = Modifier)
// drive from PrinterState.progress (Double, 0..1 — PrinterState.kt:46); NaN/out-of-range self-clamps.
// Wrap in Modifier.aspectRatio(1f) (sacred square) — see GalleryScreen.kt:195.
```

**`GraphViewHost` (heater sparkline, D-09 — Views via AndroidView, ADR 0001)** —
`render/GraphViewHost.kt:28-33`:
```kotlin
@Composable fun GraphViewHost(
    tokens: ThemeTokens,        // collect LocalTokens.current and pass in (GalleryScreen.kt:89,199)
    snapshot: FloatArray,       // RingBuffer.snapshot()
    modifier: Modifier = Modifier,
    drawArea: Boolean = true,   // leave true in product
)
```
The `factory` runs once; `update` pushes tokens (recolor on theme flip) + data — copy
`GalleryScreen.kt:198-203` verbatim for placement.

**`RingBuffer` feed** — `render/RingBuffer.kt:23` (`capacity` default 120), `push(Float)`,
`snapshot(): FloatArray`. Feed it from the live store exactly like `GalleryScreen.kt:114-122`:
```kotlin
store.printerState.collect { state ->
    ring.push(state.heaters["extruder"]?.temperature?.toFloat() ?: 0f)   // HeaterState.temperature: Double
    graphSnapshot = ring.snapshot()
}
```
> The DASHBOARD-VIEWMODEL of RESEARCH Pattern 3 (`DashboardViewModel`/`TempGraphView`) does NOT match
> reality — the real shared render primitive is `GraphView`+`GraphViewHost`+`RingBuffer`, already
> built. A thin toolkit-agnostic holder (mirroring `PrinterStateStore`'s StateFlow idiom) that owns
> the per-heater `RingBuffer`s is the only new piece; do NOT add a second throttle (the store already
> samples at 250ms — `PrinterStateStore.kt:146`).

**2×3 numeric stat grid (D-09):** plain Compose `Text` with `GeistMono` tabular numerals + `fsSp`
sizing — copy `GalleryScreen.kt:241-246` / `SectionLabel` (L326-337) for the token+font idiom.

**⚠ PERF (D-09):** ring + sparkline + numeric grid on one screen is new render surface — measure
against the Phase-3 two-part gate (`03-PERF-RESULTS.md` re-open conditions; ADR addendum: sparse
full-screen redraw p95 ≤ ~66ms on flox, zero frozen frames).

---

### `ui/printstatus` Stop control (SHELL-02) → `ConfirmGuard` (PRIM-03)

**Analog: `designsystem/ConfirmGuard.kt` (EXACT, already built)** — `ConfirmGuard.kt:54-62`:
```kotlin
@Composable fun ConfirmGuard(
    title: String, message: String, confirmLabel: String,
    onConfirm: () -> Unit, onCancel: () -> Unit,
    modifier: Modifier = Modifier, destructive: Boolean = true,   // true → red Intent.Danger
)
```
**`ConfirmGuard` DISPATCHES NOTHING itself** (by design, `ConfirmGuard.kt:40-41`) — wire its
`onConfirm` to `dispatcher.dispatch("estop", "printer.emergency_stop")`. The gutter Stop tile is an
`OutlinedControl(intent = Intent.Danger)`. See `GalleryScreen.kt:280-299` for the show/hide guard
toggle idiom.

> **CONTEXT vs RESEARCH conflict — CONTEXT WINS:** D-10 routes E-stop through the **full-screen
> ConfirmGuard** (terse copy). The RESEARCH "fast hold/double-tap, not a modal" is SUPERSEDED by the
> newer `docs/ui_design/` LAW. Use ConfirmGuard.

---

### `ui/screen/SplashScreen.kt` (component, request-response)

**Analog:** `designsystem/ConfirmGuard.kt` (closest — full-screen, `ScreenScaffold` field with
centered Text + a Row of `OutlinedControl` buttons; `ConfirmGuard.kt:67-115`). Splash is a hard
override (D-06) so its recovery buttons must be self-contained.

- Reason text from `klippy_state` / `state_message` (D-12; `PrinterState.klippyState` is live, the
  message is surfaced from `printer.info` — RESEARCH Code Examples).
- Recovery actions = `OutlinedControl` buttons → `dispatcher.dispatch(...)` for `printer.firmware_restart`
  / `printer.restart` (D-12), plus Retry (`session.requestReconnectNow()` — `MoonrakerSession.kt:82`)
  and "Edit connection" → Settings (D-13).
- Build with `ScreenScaffold` (`designsystem/layout/ScreenScaffold.kt:48`, `gutter` may be null) +
  `OutlinedControl` + token Text — same vocabulary as ConfirmGuard.

---

### `ui/screen/SettingsScreen.kt` (component, CRUD — exempt from Focus/Field/Gutter, D-15)

**Analog: `gallery/GalleryScreen.kt` (EXACT shape)** — it is the existing conventional scrollable
token-themed Compose list (`Column.verticalScroll(rememberScrollState())`, `GalleryScreen.kt:127-133`),
with section labels and `OutlinedControl` rows. Settings is the same: **conventional Android scroll,
keyboard allowed (PRIM-02), NOT `ScreenScaffold`.**

- **Connection section** (host/port/key fields) — system keyboard allowed here ONLY. Validate before
  save (V5). Writes to `ConnectionStore` (→ triggers the service rebuild via `collectLatest`, D-03).
  An mDNS **scan** button drives `MoonrakerDiscovery`.
- **Theme section** — copy `GalleryScreen.kt:136-170` *verbatim conceptually*: Dark/Light/Custom +
  S/M/L drive the live `ThemeResolver` (`resolver.setBase/setFs/setDeltas`, `ThemeResolver.kt:97-109`)
  AND persist via `ThemePrefs.setBase/setFs/setDeltas` (`ThemePrefs.kt:54-75`).
- **Accent picker (D-16)** — write a single-role `TokenDelta` accent override:
  `TokenDelta.of(TokenDelta.Role.Accent to argbInt)` (`ThemeResolver.kt:43`). The full multi-role
  editor is deferred; the substrate (`Role = {Accent,Heat,Go,Stop,Bg}`, `ThemeResolver.kt:35`) already
  supports it.

---

### `ui/shell/AppShell.kt` + `AppDrawer.kt` (component, event-driven swipe nav, SHELL-01)

**Analog:** `gallery/GalleryScreen.kt` (Compose host idiom) — no existing drawer, but tiles are
`OutlinedControl`s on a grid. Swipe-up full-screen drawer (D-14); live tiles = **Status + Settings**;
all others (Move/Temp/Files/Tools/Macros/Devices + red Power) greyed "coming soon". A greyed tile is
an `OutlinedControl` with a no-op `onClick` (or a disabled visual — Phase-4 discretion). Tapping a
live tile collapses the drawer and sets the route holder's `Dest`. Use `BackHandler` (activity-compose,
already in catalog) to collapse the drawer on Back.

---

### `AndroidManifest.xml` (MODIFIED — config)

**Analog:** the current `app/src/main/AndroidManifest.xml` (EXACT — add to it; do NOT touch the
cleartext posture or `network_security_config.xml`, both owned by Phase 1, per the file's own header
comment L2-14 and CONTEXT). The `<activity .MainActivity>` registration (L29-37) already exists.

**Add** (verified declaration shape, RESEARCH Code Examples):
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- only if mDNS scan ships: -->
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

---

## Shared Patterns

### Theme boundary + token usage (apply to EVERY new Compose file)
**Source:** `theme/compose/DinghyTheme.kt:35` + `theme/compose/LocalTokens.kt`
- Wrap the whole UI once in `DinghyTheme(resolver) { … }` (in MainActivity — `GalleryActivity.kt:60-61`).
- Inside any composable: `val t = LocalTokens.current` then read role tokens (`t.accent`, `t.stop`,
  `t.text`, `t.surface2`, …). **NEVER a raw color literal** (THEME-01). Token role list verified at
  `theme/ThemeTokens.kt:27-91` (bg/surface/text/accent/heat/go/stop families + soft/line/glow variants
  + `rCtrl`/`rPill` radii + `fs`).
- Text sizing: `fontSize = fsSp(<base>, t.fs).sp` with `fontFamily = Geist`/`GeistMono`
  (`OutlinedControl.kt:84-90`, `SeverityToast.kt:103-118`).

### Lifecycle-safe StateFlow collection (apply to every Compose consumer)
**Source:** `theme/compose/DinghyTheme.kt:39` (`collectAsStateWithLifecycle()`) — already in catalog
(`lifecycle-runtime-compose`). Use it, never plain `collectAsState()`, for `printerState`,
`connectionState`, `inFlight`, theme tokens.

### Failure feedback (apply to CommandDispatcher + every action)
**Source:** `designsystem/SeverityToast.kt:78` — `SeverityToast(severity: Severity, text: String, modifier)`
with `Severity = {Info, Success, Warning, Error}` (`SeverityToast.kt:37`). Dispatch failures →
`Severity.Error`. Gallery usage: `GalleryScreen.kt:220-223`.

### Outline-led controls (apply to every button/tile/recovery action)
**Source:** `designsystem/control/OutlinedControl.kt:68` —
`OutlinedControl(label, onClick, modifier, intent)` with `Intent = {Neutral, Accent, Warn, Danger, Go}`
(`OutlinedControl.kt:34`). **Button intent = color** (D-10/THEMING): Stop/back = `Danger`(red),
accept = `Go`(green), proceed-at-peril = `Warn`(amber), physical command = `Accent`(blue), setting =
`Neutral`. ≥64dp touch floor is built in.

### Focus/Field/Gutter scaffold (Print Status + Splash; NOT Settings)
**Source:** `designsystem/layout/ScreenScaffold.kt:48` —
`ScreenScaffold(modifier, focus?, field?, gutter?, focusGrow, fieldGrow)`. Portrait stacks,
landscape is Focus|Field 50/50 + full-width gutter, all ratio-only. Settings is the one exempt screen
(D-15) — use the GalleryScreen scrollable-column pattern instead.

### Headless / toolkit-agnostic state (apply to TopRoute, the Print-Status holder, ConnectionConfig)
**Source:** `state/PrinterState.kt:8-19` discipline — plain Kotlin data classes / pure functions, NO
Compose annotations, so both toolkits consume identically and they stay host-unit-testable. The
StateFlow exposure idiom (`MutableStateFlow(...).asStateFlow()`) is at `PrinterStateStore.kt:49-59`,
`ThemeResolver.kt:91-94`.

### Secret redaction (apply to ConnectionStore, notification, any logging)
**Source:** `auth/MoonrakerAuth.kt:113-114,130-136` (`toString()` redaction, `redactWsUrl`),
`config/DevConfig.kt:18` (T-02-01). NEVER log the API key or a full `?token=` URL; NEVER put the key
in the FGS notification text.

---

## No Analog Found

| File | Role | Data Flow | Reason / Guidance |
|------|------|-----------|-------------------|
| `config/MoonrakerDiscovery.kt` | utility | event-driven | No NsdManager usage anywhere in the repo. Build fresh from RESEARCH Pattern 5: `NsdManager.discoverServices("_moonraker._tcp", PROTOCOL_DNS_SD, listener)` + a `WifiManager.MulticastLock` for the scan window; serialize `resolveService` (pre-API-29). **Best-effort only — manual entry is the floor** (D-04). Treat empty scan as normal (Moonraker `[zeroconf]` is opt-in, Pitfall 2). |
| `service/MoonrakerService.kt` (FGS lifecycle half) | service | event-driven | No Service/FGS exists. The *spine-assembly* half has strong analogs (above); the *FGS lifecycle* half (channel + `startForeground` + `START_STICKY`) is new — follow the verified manifest + RESEARCH Pitfall 1. The `Application`/service-locator pair is also first-of-its-kind (only `GalleryActivity`'s local assembler block is analogous). |

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/` (net, state, auth, config, render,
designsystem, theme, gallery) + `app/src/debug/.../gallery/GalleryActivity.kt` + `app/src/main/AndroidManifest.xml`.
**Files scanned (read in full):** MoonrakerSession, PrinterStateStore, PrinterState, JsonRpcClient,
MainActivity, DevConfig, ProgressRing, GraphView, GraphViewHost, RingBuffer, ConfirmGuard,
SeverityToast, OutlinedControl, ScreenScaffold, ThemePrefs, ThemeResolver, DinghyTheme, JsonRpc,
MoonrakerSocket, MoonrakerAuth, GalleryScreen, GalleryActivity, AndroidManifest.xml; grepped
ThemeTokens roles + DataStore wiring.
**Key reality-vs-RESEARCH corrections baked in:** real shared render primitive is
`GraphView`/`GraphViewHost`/`RingBuffer` (NOT `TempGraphView`/`DashboardViewModel`); E-stop routes
through `ConfirmGuard` (CONTEXT D-10 supersedes RESEARCH hold-gesture); `MoonrakerSocket.real` defaults
to `DevConfig.wsUrl` so the service MUST override it; `MoonrakerSession.clientUrl` must stay
`https://mees.works/dinghy-display`.
**Pattern extraction date:** 2026-05-31
