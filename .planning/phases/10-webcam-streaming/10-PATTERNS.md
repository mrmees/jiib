# Phase 10: Webcam Streaming - Pattern Map

**Mapped:** 2026-06-03
**Files analyzed:** 13 new + 4 modified (17 total)
**Analogs found:** 17 / 17 (every new/modified file has a same-codebase analog)

> **Package root:** `works.mees.dinghy` (all paths below under `app/src/main/java/works/mees/dinghy/`).
> All analogs VERIFIED to exist and were read in full (or the load-bearing range) for the excerpts below.
> The RESEARCH.md "Component Responsibilities" table is the source for the file list; this doc binds each
> proposed file to a concrete codebase analog + read-first paths + the exact excerpt to copy.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `state/WebcamModels.kt` (NEW) | model | transform | `ui/move/MoveHolder.kt` (`MoveVm` data class) + Moonraker `@Serializable` models | role-match |
| `net/WebcamUrl.kt` (NEW) | utility | transform | `auth/MoonrakerAuth.kt` (`buildAuthedWsUrl`/`redactWsUrl` — HttpUrl manipulation) | role-match |
| `net/WebcamProbe.kt` (NEW) | service | request-response | `auth/MoonrakerAuth.kt` (`fetchOneshotToken` — injectable `Call.Factory`, typed errors) | exact |
| `net/MjpegStreamDecoder.kt` (NEW) | service | streaming | `net/MoonrakerSocket.kt` (callbackFlow over a long-lived OkHttp body) | role-match (data-flow) |
| `net/SnapshotPoller.kt` (NEW) | service | request-response (poll) | `auth/MoonrakerAuth.kt` (one-shot GET) + `net/Backoff.kt` | role-match |
| `render/WebcamView.kt` (NEW) | view | streaming (raster) | `render/GraphView.kt` (`ThemeableView`, allocation-free `onDraw`) | exact |
| `render/WebcamViewHost.kt` (NEW) | component | streaming | `render/GraphViewHost.kt` (`AndroidView` factory-once/update-push) | exact |
| `ui/webcam/WebcamHolder.kt` (NEW) | holder | event-driven | `ui/move/MoveHolder.kt` (toolkit-agnostic StateFlow holder) | exact |
| `ui/webcam/WebcamScreen.kt` (NEW) | component | request-response | `ui/move/MoveScreen.kt` + `ScreenScaffold` (Focus/Field/Gutter) | role-match |
| `ui/webcam/WebcamPrefs.kt` (NEW) | store | CRUD | `ui/macros/MacroPrefs.kt` + `theme/ThemePrefs.kt` (dynamic keyed prefs) | exact |
| `command/CommandRegistry.kt` (MODIFY: +`webcamsList`) | config | request-response | `CommandRegistry.serverInfo`/`filesGetDirectory` (one-shot JSON-RPC spec) | exact |
| `net/JsonRpc.kt` (MODIFY: +`WEBCAMS_LIST` const) | config | — | `JsonRpcMethods.FILES_GET_DIRECTORY` const | exact |
| `ui/route/TopRoute.kt` (MODIFY: +`Dest.Webcam`) | route | — | `enum class Dest { … }` | exact |
| `ui/shell/AppDrawer.kt` (MODIFY: greyed-gating Webcam tile) | component | — | `DrawerTileSpec` + greyed-tile idiom | exact (needs a runtime-enabled extension — see gap) |
| `ui/shell/AppShell.kt` (MODIFY: route `Dest.Webcam`, build holder) | component | event-driven | `AppShell` `when(dest)` + `remember(store){ Holder(...) }` | exact |
| `di/SpineHandle.kt` (MODIFY?: expose webcams) | model | — | `SpineHandle.metadata`/`lastJob` one-shot StateFlow forwarding | exact (only if enumeration is service-owned — see Decision note) |
| `service/MoonrakerService.kt` (MODIFY?: wire webcam one-shot) | service | request-response | `buildSpineAndLaunch` one-shot `PrintMetadataHolder`/`LastJobHolder` seam | exact (same caveat) |

**Architecture decision the planner must make (research flags it, this doc gives both analogs):**
Two valid wiring shapes for the `/server/webcams/list` enumeration, both with a real precedent:
- **(A) Holder-driven (page-open one-shot via the dispatcher/rpc):** `WebcamHolder` issues the read when
  the page opens. Simpler; no `SpineHandle`/`MoonrakerService` change. But `SpineHandle` exposes
  `dispatcher` (fire-and-forget) NOT a `JsonRpcClient` (suspend-and-await `result`), so a holder cannot
  `rpc.request` off the handle as-is.
- **(B) Service-owned one-shot forwarded as a `StateFlow` on the handle** — EXACTLY the `metadata` /
  `lastJob` precedent (`PrintMetadataHolder`/`LastJobHolder` in `buildSpineAndLaunch`, which capture the
  session's `rpc` and `rpc.request(...)` it). This is the cleaner match to the cadence-contract
  "edge-driven one-shot off the handshake" shape and needs the SpineHandle + MoonrakerService edits.
  **Recommend (B)** — it reuses a proven seam and keeps the `rpc` capture where every other one-shot read
  already lives. (A) would require newly exposing `rpc` on `SpineHandle`, which nothing else does.

---

## Pattern Assignments

### `command/CommandRegistry.kt` (+ `webcamsList`) and `net/JsonRpc.kt` (+ const)

**Analog:** `CommandRegistry.serverInfo` / `CommandRegistry.filesGetDirectory` (paramless + param one-shot specs)
**read_first:** `command/CommandRegistry.kt:82-152, 488-525`, `net/JsonRpc.kt:93-133`

`server.webcams.list` is a **paramless one-shot JSON-RPC read** — copy the `serverInfo` spec shape verbatim
(no args, `Unit` param, `params = { null }`). It is NOT a subscribe and NOT polled (cadence contract). Add a
`JsonRpcMethods.WEBCAMS_LIST = "server.webcams.list"` const beside `FILES_GET_DIRECTORY` first.

CommandRegistry one-shot spec to copy (`CommandRegistry.kt:89-94`):
```kotlin
val serverInfo: CommandSpec<Unit> = jsonRpc(
    catalogId = "MR-server.info",
    method = "server.info",
    key = { "server_info" },
    params = { null },
)
```
New spec (mirror; the `webcams` Moonraker component is universal so no availability predicate is needed —
gate the TILE on cam-count instead, D-08):
```kotlin
val webcamsList: CommandSpec<Unit> = jsonRpc(
    catalogId = "MR-server.webcams.list",
    method = JsonRpcMethods.WEBCAMS_LIST,   // "server.webcams.list"
    key = { "webcams_list" },
    params = { null },
)
```
Then add `webcamsList` to the `all` list (`CommandRegistry.kt:438-486`). The `request` helper to await its
`result` element is `CommandDispatchExtensions.request` (`command/CommandDispatchExtensions.kt:13-25`):
```kotlin
suspend fun <P> JsonRpcClient.request(command: CommandSpec<P>, args: P,
    timeoutMs: Long = JsonRpcClient.DEFAULT_REQUEST_TIMEOUT_MS): JsonElement { … }
// usage precedent (MoonrakerService.kt:145-148):  rpc.request(CommandRegistry.filesMetadata, MetadataArgs(filename))
```

---

### `state/WebcamModels.kt` (model, transform)

**Analog:** `ui/move/MoveHolder.kt`'s `MoveVm` data class (for the VM shape) + the spine's tolerant
`@Serializable` decode discipline (`ignoreUnknownKeys`, nullable fields — see Security V5/`MoonrakerJson`).
**read_first:** `ui/move/MoveHolder.kt:75-88`, RESEARCH §"Full Moonraker field set" + §"Verified live shape"

Model the 16 webcam fields **defensively** (V5: every field untrusted; `service`/`snapshot_url` may be blank;
`name` may carry odd unicode like `microsoft®_…`; `aspect_ratio` is a `"W:H"` string; `rotation ∈ {0,90,180,270}`).
The plain-Kotlin data-class + companion idiom to copy (`MoveHolder.kt:79-87`):
```kotlin
data class MoveVm(
    val x: Double? = null,
    val y: Double? = null,
    // … all-nullable, never-fabricated defaults
)
```
Decode with the spine's existing tolerant `MoonrakerJson` (see `auth/MoonrakerAuth.kt:79` —
`MoonrakerJson.parseToJsonElement(...)`); keep the `@Serializable` parse off the `result.webcams[]` array.
Rung/selected-cam UI state belongs here too (mirror `MoveVm`'s "resolved VM for the screen" role).

---

### `net/WebcamUrl.kt` (utility, transform — the D-09 resolver/rewriter)

**Analog:** `auth/MoonrakerAuth.kt` — `buildAuthedWsUrl` (HttpUrl `newBuilder()` manipulation, scheme handling)
and `redactWsUrl` (regex token strip). `config/ConnectionConfig.kt` provides the resolution base.
**read_first:** `auth/MoonrakerAuth.kt:88-105, 130-135`, `config/ConnectionConfig.kt:14-28`

This is the ONE genuinely-hand-rolled helper (RESEARCH §"Don't Hand-Roll" — `HttpUrl.resolve()` does the
relative join; the localhost rewrite is the bit you write). The base is `ConnectionConfig.httpBase`
(`ConnectionConfig.kt:19`):
```kotlin
val httpBase: String get() = "http://$host:$port"
```
HttpUrl-manipulation idiom to copy (`MoonrakerAuth.kt:96-99`):
```kotlin
val built = httpForm.toHttpUrl().newBuilder()
    .addQueryParameter("token", token)
    .build()
    .toString()
```
Resolver contract (D-09 + Pitfall 1): (a) relative → `httpBase.toHttpUrl().resolve(raw)`; (b) if the resolved
host ∈ {`localhost`,`127.0.0.1`,`0.0.0.0`,`::1`} **rewrite host to `ConnectionConfig.host`**, preserving
port/path/**query (`?token=…` MUST survive — E3 needs it)**. Then mirror `redactWsUrl` for a `redact(?token=)`
(Security V7 — never log a resolved webcam URL; the E3 URL embeds a token):
```kotlin
fun redactWsUrl(url: String): String = url.replace(Regex("([?&]token=)[^&]*"), "$1<redacted>")
```

---

### `net/WebcamProbe.kt` (service, request-response — D-02 Content-Type sniff / rung select)

**Analog:** `auth/MoonrakerAuth.kt` `fetchOneshotToken` — the **injectable `Call.Factory`** seam + typed,
never-escaping error mapping. This is the closest analog in the repo for "do an HTTP GET off the shared
client, branch on the response, never throw raw."
**read_first:** `auth/MoonrakerAuth.kt:36-86`, RESEARCH §"Pattern 1" (the sniff snippet), §Pitfall 4

Copy the injectable-callFactory constructor + `newCall(...).execute().use { resp -> … }` typed-branch shape
(`MoonrakerAuth.kt:53-76`):
```kotlin
class MoonrakerAuth(private val callFactory: Call.Factory, …) {
    fun fetchOneshotToken(): String {
        val request = Request.Builder().url("$httpBase/access/oneshot_token").get()…build()
        val body = try {
            callFactory.newCall(request).execute().use { resp ->
                if (resp.code == 401) throw AuthException(ConnectionError.AuthRequired, …)
                if (!resp.isSuccessful) throw AuthException(ConnectionError.ServerError(resp.code, …), …)
                resp.body?.string()
            }
        } catch (e: IOException) { throw AuthException(ConnectionError.NetworkUnavailable, …) }
        …
    }
}
```
For the probe: GET `stream_url` (not HEAD — some MJPEG servers ignore HEAD), read `Content-Type`, branch
to rung 1 (`multipart/x-mixed-replace` → keep body open) / rung 2 (snapshot) / rung 3 (401/403 **terminal**,
404, or anything else). Treat 401/403 as terminal-for-this-cam (A4 / Pitfall 3) exactly as
`fetchOneshotToken` maps 401 → a typed terminal error — do NOT spin-retry it.

---

### `net/MjpegStreamDecoder.kt` (service, streaming — RUNG 1)

**Analog (data-flow):** `net/MoonrakerSocket.kt` — the project's one example of a **long-lived OkHttp
body bridged into a cancellable cold stream** (`callbackFlow` + `awaitClose` cancellation). The decoder is
the imperative/Okio analog of the same "one connection held open, frames pushed out, cancel tears it down"
shape. **Decode discipline analog:** `render/GraphView.kt` (allocation-free, one-reused-buffer ethos) +
`ui/files/FileThumbnailLoader.kt` (downsample-to-view-size intent — but NOT Coil; the live loop is custom).
**read_first:** `net/MoonrakerSocket.kt:67-106` (callbackFlow + awaitClose), RESEARCH §"Pattern 2/3", §Pitfall 5

Use OkHttp `ResponseBody.source()` (Okio `BufferedSource`) for boundary scanning (RESEARCH §"Don't Hand-Roll").
The cancellation+cleanup shape to copy (`MoonrakerSocket.kt:67-106`):
```kotlin
fun events(): Flow<SocketEvent> = callbackFlow {
    … // push frames via trySend(...)
    awaitClose { connection?.close() }   // scope cancel → close the underlying resource, no leak
}
```
Decode policy (D-06): one reused mutable `ARGB_8888` bitmap via `inBitmap`+`inSampleSize`+`inMutable`;
**drop-behind** with a `CONFLATED` channel / `AtomicReference<Bitmap?>` (never queue); cap ~10–15 fps (PIN on
flox). Catch the `inBitmap` size-mismatch `IllegalArgumentException` and drop `inBitmap` for one frame
(Pitfall 5). Decode on a background dispatcher; hand off to the View via `postInvalidate()`.
**Stream client posture (Pitfall 4):** derive off the shared pool — `defaultClient().newBuilder().readTimeout(0).build()`
(a stream legitimately holds the connection open; 0/long read timeout is correct here, opposite of snapshot).

---

### `net/SnapshotPoller.kt` (service, request-response poll — RUNG 2)

**Analog:** `auth/MoonrakerAuth.kt` one-shot GET (finite read timeout) + `net/Backoff.kt` for the retry cadence.
**read_first:** `auth/MoonrakerAuth.kt:53-76`, `net/Backoff.kt:22-50`

~2 fps (~500 ms) loop of finite-timeout GETs (D-07). **Snapshot client posture (Pitfall 4):** derive a
client with a FINITE read timeout — `defaultClient().newBuilder().readTimeout(5, SECONDS).build()` — NEVER
the `readTimeout(0)` ws posture (a wedged snapshot would hang the poller forever). Coil MAY serve the
snapshot stills (`FileThumbnailLoader` precedent) since it already decodes/downsamples off-thread — but the
loop/cadence is custom. Backoff helper to reuse for the retry side (`Backoff.kt:22-26`):
```kotlin
fun backoffDelay(attempt: Int, base: Duration = DEFAULT_BASE, rng: Random = Random.Default): Duration { … }
// DEFAULT_BASE = 500.milliseconds; overflow-safe, jittered. D-12 wants 1s→~10s cap while foreground only.
```
Treat 401/403 as terminal-for-this-cam (A4) — do not spin (E5 snapshot is Basic-Auth 401; that → rung-3 card,
which is CORRECT, not a bug).

---

### `render/WebcamView.kt` (view, raster) + `render/WebcamViewHost.kt` (component)

**Analog:** `render/GraphView.kt` (the `ThemeableView` custom-Canvas View) + `render/GraphViewHost.kt`
(the `AndroidView` factory-once/update-push host). This is the **exact** ADR-0001 hybrid pattern for a
high-churn live raster surface.
**read_first:** `render/GraphView.kt:1-80`, `render/GraphViewHost.kt:27-43`, `theme/views/ThemeableView.kt:21-24`

`WebcamView` must `implements ThemeableView` for the framing/overlay/badge token colors (`ThemeableView.kt:21-24`):
```kotlin
interface ThemeableView { fun applyTokens(t: ThemeTokens) }
```
`GraphView` shows the contract to copy: `class GraphView(context: Context) : View(context), ThemeableView`,
pre-allocate ALL `Paint`/`Path`/`Matrix` in init (NEVER in `onDraw` — Pitfall 4 GC-churn trap), and use
`setData(...) → invalidate()` as the repaint trigger. For the webcam: a `setFrame(bitmap)` + `postInvalidate()`
from the decoder thread; draw the bitmap with a `Matrix` for **pixel-square never-stretch** scale + the
`flip_*`/`rotation` transform; clip to the rounded framing cutout; draw the snapshot badge (D-03), the dimmed
last-frame + "Reconnecting…" overlay (D-11), and the cycle overlay (camera_feed: burst icon + cam name).

Host to copy (`GraphViewHost.kt:34-42`) — factory runs once, `update` pushes tokens + the latest frame; a
dark/light flip recolors chrome without recreating the View:
```kotlin
AndroidView(
    factory = { ctx -> GraphView(ctx) },   // created ONCE
    update = { view ->
        view.applyTokens(tokens)           // push tokens + invalidate (recolor, no recreation)
        view.setData(snapshot)             // new sample → invalidate
    },
    modifier = modifier,
)
```

---

### `ui/webcam/WebcamHolder.kt` (holder, event-driven)

**Analog:** `ui/move/MoveHolder.kt` — the toolkit-agnostic `MutableStateFlow` → `StateFlow<Vm>` holder that
consumes the per-session store and is host-unit-testable (no Compose annotations). Also the
`PrintMetadataHolder`/`LastJobHolder` one-shot-fetch holders for the enumeration+lifecycle seam.
**read_first:** `ui/move/MoveHolder.kt:33-73`, `service/MoonrakerService.kt:144-158` (one-shot fetch holders)

Copy the StateFlow holder skeleton (`MoveHolder.kt:33-48`):
```kotlin
class MoveHolder(scope: CoroutineScope, private val store: PrinterStateStore) {
    private val _vm = MutableStateFlow(MoveVm())
    val vm: StateFlow<MoveVm> = _vm.asStateFlow()
    init { scope.launch { store.printerState.collect { _vm.value = buildVm(it) } } }
}
```
The holder owns: rung selection (calls `WebcamProbe`), the active decoder/poller lifecycle, backoff-while-
foreground (`net/Backoff.kt`), the D-11 reconnecting state machine, and the selected-cam (from
`WebcamPrefs` or first-in-list, D-10). It must expose a `cancel()` and the shell must
`DisposableEffect(holder){ onDispose { holder.cancel() } }` it on spine-rebuild (the WR-01 leak lesson —
see `AppShell.kt:210, 236`).

---

### `ui/webcam/WebcamScreen.kt` (component, Focus/Field/Gutter)

**Analog:** `ui/move/MoveScreen.kt` (panel + back-gutter) hosted in `AppShell`'s `when(dest)`. Layout grammar
is LAW (`docs/ui_design/LAYOUT.md`); the camera_feed note is the design contract (no hi-fi mockup).
**read_first:** `ui/shell/AppShell.kt:300-304` (how `MoveScreen` is wired with `onBack`), `docs/view_specific_notes/camera_feed`

Single-focus default; aspect-aware Field (cam list) show/hide vs device orientation; **Back-only gutter**
(red intent). Hosts `WebcamViewHost`. The dead-end card (D-04, rung 3) and snapshot badge (D-03) live inside
the framing cutout. Follow the `onBack = { goBack() }` gutter wiring exactly as `MoveScreen` does.

---

### `ui/webcam/WebcamPrefs.kt` (store, per-printer keyed prefs — D-10)

**Analog:** `ui/macros/MacroPrefs.kt` (injected `DataStore<Preferences>`, fail-safe read, host-testable) +
`theme/ThemePrefs.kt` for the **dynamic-string-keyed** idiom (the per-printer key D-10 needs).
**read_first:** `ui/macros/MacroPrefs.kt:33-79`, `theme/ThemePrefs.kt:85-88`

Copy MacroPrefs verbatim for the injected-DataStore + fail-safe-on-`IOException` shape (`MacroPrefs.kt:33-46`):
```kotlin
class MacroPrefs(private val dataStore: DataStore<Preferences>) {
    val bookmarks: Flow<Set<String>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[KEY_BOOKMARKS] ?: emptySet() }
    suspend fun addBookmark(name: String) = dataStore.edit { … }
}
```
For the **per-printer key** (D-10 "keyed by printer/connection"), copy `ThemePrefs`' dynamic-key idiom
(`ThemePrefs.kt:88`) — build a `stringPreferencesKey("preferred_cam_<host>")` from `ConnectionConfig.host`:
```kotlin
private fun deltaArgbKey(roleName: String) = "delta_argb_$roleName"   // → preferredCamKey(host) = "preferred_cam_$host"
```
Default on a fresh printer = first cam in list order (no "set as default" action). Use its OWN
`webcam.preferences_pb` (not shared), created by `DinghyApp`/`AppContainer` (the MacroPrefs precedent,
`MacroPrefs.kt:20-32`).

---

### `ui/route/TopRoute.kt` (+ `Dest.Webcam`)

**Analog:** the existing `Dest` enum — a one-line addition (D-05 "extra panels are a one-line addition").
**read_first:** `ui/route/TopRoute.kt:31`
```kotlin
enum class Dest { PrintStatus, Temperature, Move, Extrude, Files, Macros, Console, Calibration, Settings }
//                                                                                              ↑ add Webcam
```

---

### `ui/shell/AppShell.kt` (+ route `Dest.Webcam`, build holder)

**Analog:** the existing `when(dest)` arm wiring + the `remember(store){ Holder(...) }` re-key idiom.
**read_first:** `ui/shell/AppShell.kt:148-156` (holder re-key), `:210` (DisposableEffect cancel), `:300-304` (Move arm)

Build the holder re-keyed on the live per-session store (`AppShell.kt:151`):
```kotlin
val moveHolder = remember(store) { MoveHolder(scope = scope, store = store) }
// → val webcamHolder = remember(store) { WebcamHolder(scope = scope, store = store, …) }
```
Cancel its loops on spine-rebuild (the WR-01 leak lesson, `AppShell.kt:210`):
```kotlin
DisposableEffect(webcamHolder) { onDispose { webcamHolder.cancel() } }
```
Add the `Dest.Webcam -> WebcamScreen(… onBack = { goBack() })` arm mirroring `Dest.Move` (`:300-304`).
**Page-visible lifecycle (D-13):** bind the decode/poll/retry loops to `repeatOnLifecycle(STARTED)` +
the effect's own cancellation (RESEARCH §"Pattern 4") — `collectAsStateWithLifecycle` is already the shell's
idiom (`AppShell.kt:138`). Note the swipe-up drawer gesture: decide if Webcam joins the
`setOf(Dest.Files, Dest.Console, Dest.Macros, Dest.Calibration)` swipe-suppress set (`AppShell.kt:282`) —
the cam-cycle tap overlay may want the canvas, so likely YES (keep an explicit Back gutter as the exit).

---

## Shared Patterns

### Shared OkHttp client (two derived postures)
**Source:** `net/MoonrakerSocket.kt:137-155` (`real()` / `defaultClient()`)
**Apply to:** `WebcamProbe`, `MjpegStreamDecoder`, `SnapshotPoller`
```kotlin
fun defaultClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // ws/stream: no read timeout
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .build()
```
Reuse the ONE client/pool/TLS (CLAUDE.md networking law). Derive per-posture off it (Pitfall 4):
`client.newBuilder().readTimeout(0).build()` for the **stream**, `client.newBuilder().readTimeout(5,SECONDS).build()`
for **snapshots**. The service already builds `MoonrakerSocket.defaultClient()` once per session
(`MoonrakerService.kt:115`) — thread that same client to the webcam layer rather than minting a new one.

### `X-Api-Key` injection (Moonraker read only)
**Source:** `auth/MoonrakerAuth.kt:53-57, 108, 116-117`
**Apply to:** the `/server/webcams/list` Moonraker read where a key is configured (NOT the snapshot/stream
HTTP server — that is a separate auth domain; preserve in-URL `?token=` verbatim instead).
```kotlin
const val HEADER_API_KEY = "X-Api-Key"
fun xApiKeyHeader(): String? = apiKey?.takeIf { it.isNotBlank() }
// applied:  .apply { xApiKeyHeader()?.let { header(HEADER_API_KEY, it) } }
```
On this project's printers the enumeration goes over the JSON-RPC spine (already authed via the ws token),
so the `X-Api-Key` REST path is the keyed-fallback case, not the hot path.

### Secret redaction in logs (Security V7)
**Source:** `auth/MoonrakerAuth.kt:130-135` (`redactWsUrl`), `config/ConnectionConfig.kt:26-27` (`toString` redact)
**Apply to:** `net/WebcamUrl.kt` and ANY surface that could log a resolved snapshot/stream URL (the E3 URL
embeds `?token=…`). Mirror the regex strip before a URL reaches a log/crash surface; override `toString()`
to redact on any holder/model that carries a tokened URL.

### Tolerant `@Serializable` decode (Security V5 / Tampering)
**Source:** spine-wide `MoonrakerJson` (used at `auth/MoonrakerAuth.kt:79`)
**Apply to:** `state/WebcamModels.kt` — `ignoreUnknownKeys`, nullable fields, fail-to-empty-list (greyed tile)
on a malformed `/server/webcams/list`, never crash.

### Backoff (foreground-only retry)
**Source:** `net/Backoff.kt:22-50`
**Apply to:** `WebcamHolder`/`SnapshotPoller`/`MjpegStreamDecoder` retry. D-12 wants 1s→~10s cap, foreground
only; `backoffDelay(attempt, base)` is overflow-safe + jittered. Stop retrying entirely on background/nav
(the loop's own cancellation, RESEARCH §"Pattern 4").

### Holder leak-cancel on spine rebuild (WR-01 lesson)
**Source:** `ui/shell/AppShell.kt:210, 236` (`DisposableEffect(holder){ onDispose { holder.cancel() } }`)
**Apply to:** `WebcamHolder` — it owns long-lived decoder/poller coroutines; a re-key on reconnect MUST
cancel the old holder's loops or they leak + compound per reconnect (and keep decoding/streaming after the
session swapped — also a cadence/efficiency violation).

---

## Drawer greyed-gating: the one real GAP (D-08)

**Source:** `ui/shell/AppDrawer.kt:102-195` (`DrawerTileSpec`, `dest = null → inert/greyed`)

The drawer's greyed-tile idiom EXISTS and is the right base — a `dest = null` tile renders disabled/greyed
(`AppDrawer.kt:129` Devices, `:147-166` the live-vs-greyed styling). **But the current tile set is a STATIC
`val DRAWER_TILES` (`AppDrawer.kt:116-132`) with COMPILE-TIME `dest`** — it has no notion of a RUNTIME-toggled
enablement. D-08 needs the Webcam tile **greyed when cam-count == 0, live when ≥ 1** — a runtime condition the
current static list cannot express.

**Closest analog for the gating signal:** `AppShell` already collects `capabilities`
(`AppShell.kt:139`) and threads runtime flags into screens (e.g. `canStartPrint = capabilities.hasObject(...)`,
`AppShell.kt:314`). The minimal, in-idiom extension:
- Add `DrawerTileSpec(label = "Webcam", symbol = <unused-glyph>, dest = Dest.Webcam)` to `DRAWER_TILES`
  (pick a Material Symbol NOT already used — icon-no-repeat law; `videocam` is referenced in a comment as a
  Console gutter glyph so check it's free, else `photo_camera`/`linked_camera`).
- Thread a `webcamEnabled: Boolean` (or a `Set<Dest>` of enabled dests) PARAMETER into `AppDrawer(...)`
  from `AppShell` (sourced from the webcam-count StateFlow), and in `DrawerTile` treat a tile as greyed when
  `dest == Dest.Webcam && !webcamEnabled` — i.e. fold the runtime flag into the existing
  `val live = tile.dest != null` decision (`AppDrawer.kt:146`) so it becomes
  `val live = tile.dest != null && enabled(tile.dest)`. The greyed STYLING (hairline outline, dimmed text,
  inert `semantics { disabled() }`, no `clickable`) is already correct as-is (`AppDrawer.kt:147-166`) — only
  the enablement INPUT is new.

This is a small, surgical extension of an existing idiom, not a new pattern — but the planner must NOT assume
the drawer already supports runtime gating; it does not. (Phase 9 Calibration HID its tile; D-08 deliberately
departs to greyed-visible.)

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| — | — | — | Every Phase-10 file maps to a concrete codebase analog. |

Two items are "analog-exists-but-needs-extension" (not missing): (1) the D-09 localhost-rewrite is the one
hand-rolled bit (HttpUrl idiom exists in `MoonrakerAuth`, the rewrite logic is new — RESEARCH-sanctioned);
(2) the drawer runtime-gating input is new (styling/idiom exists, the runtime-enabled signal does not — see
the GAP section above).

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/{net,auth,config,command,render,theme,service,di,ui/{move,macros,shell,route,files,console}}/`
**Files scanned:** 17 analogs read (full or load-bearing range); 2 grep sweeps for existing webcam/method/key idioms
**Pattern extraction date:** 2026-06-03
**Package root confirmed:** `works.mees.dinghy` (NOT the `docs/` `works.mees.dinghy` placeholder — verified from source)
