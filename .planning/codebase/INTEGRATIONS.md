# External Integrations

**Analysis Date:** 2026-06-08

## Moonraker JSON-RPC WebSocket

**Primary integration — the app's entire data surface.**

- Transport: OkHttp 4.12.0 WebSocket (`MoonrakerSocket`, `app/src/main/java/works/mees/dinghy/net/MoonrakerSocket.kt`)
- Bridged to coroutines via `callbackFlow` → `Flow<SocketEvent>` (`SocketEvent.Open / Frame / Closed`)
- One shared `OkHttpClient` for WebSocket + REST + auth (finite `connectTimeout = 10s`, `readTimeout = 0` — required for ws, `pingInterval = 10s` for half-open detection)
- URL: `ws://<host>:<port>/websocket` (or `wss://` when configured)
- Auth: optional `X-Api-Key` header + oneshot token (`?token=…`) via `MoonrakerAuth` (`app/src/main/java/works/mees/dinghy/auth/MoonrakerAuth.kt`); open/trusted-client path is the default

**JSON-RPC layer** (`app/src/main/java/works/mees/dinghy/net/JsonRpc.kt`, `JsonRpcClient.kt`):
- Request/response correlation by incrementing `Long` id; pending map `Map<Long, CompletableDeferred<JsonElement>>` in `JsonRpcClient`
- Per-request timeout (`DEFAULT_REQUEST_TIMEOUT_MS`); `close()` fails all pending deferred on socket death
- Frame types: `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcErrorResponse`, `JsonRpcNotification`
- Shared `Json` instance `MoonrakerJson` — `ignoreUnknownKeys = true, isLenient = true, encodeDefaults = true`
- Notifications (no `id`) routed by method name to `SharedFlow`s: `statusUpdates` (`notify_status_update`), `klippyEvents` (`notify_klippy_*`), `gcodeResponses` (`notify_gcode_response`)

**Reconnect supervisor** (`app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt`):
- Structured-concurrency loop: uncapped exponential backoff + jitter on failure; gentle quiescence on `AuthRequired` (no churn)
- Per-(re)connect handshake order (EXACT, once each): `server.connection.identify` → `server.info` → `printer.objects.list` → derive capabilities/subscribe set → `printer.objects.query(subset)` (full seed) → `printer.objects.subscribe(subset)` → emit `Connected`
- In-session `notify_klippy_ready` drives a full re-handshake (skipping identify — re-identify 400s on the same socket); serialized by `rehandshakeMutex`
- Drop-recovery watchdog: a bounded `RECOVERY_WINDOW = 30s` select/onTimeout that closes the real socket if no re-handshake lands (prevents a hung-forever feed)
- `ConnectionState` five-state machine: `Disconnected / Connecting / Syncing / Connected / Error`; flows through `PrinterStateStore`

**One-shot reads per handshake (all best-effort, never break Connected):**
- `server.temperature_store` — temperature history backfill for the graph
- `machine.system_info` — static host identity (Phase 20)
- `machine.proc_stats` — throttled_state + uptime (not in the 1 Hz push)
- `server.gcode_store` — console history backfill (up to 1000 lines)
- `printer.objects.query(["configfile"])` — extruder min/max config, probe z_offset, screws_tilt_adjust config, macro bodies, Fine-Tune reset baselines, Output Controls discovery (5 consumers off ONE result)

**Well-known JSON-RPC method names** (`JsonRpcMethods` object in `net/JsonRpc.kt`):
- State: `OBJECTS_LIST`, `OBJECTS_QUERY`, `OBJECTS_SUBSCRIBE`
- Actions (gcode): `GCODE_SCRIPT` — runs ALL action commands (jog, temp set, extrude, macros)
- Recovery: `EMERGENCY_STOP`, `FIRMWARE_RESTART`, `RESTART`
- Files: `FILES_GET_DIRECTORY`, `FILES_THUMBNAILS`, `FILES_DELETE_FILE`, `FILES_METADATA`
- Print: `PRINT_START`, `PRINT_PAUSE`, `PRINT_RESUME`, `PRINT_CANCEL`
- History: `HISTORY_LIST`
- Webcams: `WEBCAMS_LIST`
- Spoolman: `SPOOLMAN_STATUS`, `SPOOLMAN_GET_SPOOL_ID`, `SPOOLMAN_POST_SPOOL_ID`, `SPOOLMAN_PROXY`
- Notifications: `NOTIFY_STATUS_UPDATE`, `NOTIFY_GCODE_RESPONSE`, `NOTIFY_KLIPPY_READY/SHUTDOWN/DISCONNECTED`, `NOTIFY_ACTIVE_SPOOL_SET`, `NOTIFY_SPOOLMAN_STATUS_CHANGED`, `NOTIFY_PROC_STAT_UPDATE`

## Moonraker REST

- Same shared `OkHttpClient` under Retrofit 2.11.0
- Used for auth: `GET /access/oneshot_token` (via `MoonrakerAuth.fetchOneshotToken()`)
- Used for cleartext network probing and smoke tests
- `X-Api-Key` header set on REST calls when an API key is configured

## Spoolman (via Moonraker proxy)

- Access: `server.spoolman.proxy` JSON-RPC method with `use_v2_response=true` — rides the SAME session `JsonRpcClient`, NOT a direct HTTP connection to Spoolman
- Client: `SpoolmanClient` interface / `MoonrakerSpoolmanClient` implementation (`app/src/main/java/works/mees/dinghy/spool/SpoolmanClient.kt`)
- Active-spool STATE (status/get/set/clear) is Moonraker-owned and routes through `ActiveSpoolFacade` (`app/src/main/java/works/mees/dinghy/spool/ActiveSpoolFacade.kt`) using dedicated JSON-RPC methods
- Inventory (spools, filaments, vendors, materials, locations) routes through `MoonrakerSpoolmanClient` as read-only proxy GETs
- Push notifications: `notify_active_spool_set` and `notify_spoolman_status_changed` reconcile external changes (e.g. Fluidd, runout macro)
- Dotted filter keys (`filament.material`, `filament.vendor.name`) are URL-encoded in the client layer before building the proxy query string
- Color data from Spoolman (`colorSwatches`) is the primary source for the SpoolGlyph spiral tint; falls back to gcode `filament_colors[0]`

## Webcam Streams

**URL resolution** (`app/src/main/java/works/mees/dinghy/net/WebcamUrl.kt`):
- Raw `stream_url`/`snapshot_url` from `server.webcams.list` are resolved against the configured Moonraker host (relative join via `HttpUrl.resolve()`; loopback rewrite for `127.0.0.1`/`localhost`)
- `?token=` parameters preserved through resolution; redacted via `surfaceWebcamUrl()` before any log/diagnostic surface
- ravens-perch webcam URL contract: reads `extra_data.ravens_perch.streams.<proto>.url` (absolute, config-free) from the webcam list entry

**Webcam probe ladder** (`app/src/main/java/works/mees/dinghy/net/WebcamProbe.kt`):
- GET probe of `stream_url`; Content-Type is the sole decode authority
- Rung 1 (MJPEG): `multipart/x-mixed-replace` → `MjpegStreamDecoder` (`app/src/main/java/works/mees/dinghy/net/MjpegStreamDecoder.kt`) — Okio multipart boundary scanner; single reused bitmap + `inSampleSize` for memory efficiency; DROP_OLDEST channel (1-deep) for backpressure; OOM guard at `MAX_PART_BYTES`
- Rung 2 (Snapshot): `SnapshotPoller` (`app/src/main/java/works/mees/dinghy/net/SnapshotPoller.kt`) — finite read timeout (snapshot self-DoS guard)
- Rung 3 (Terminal): no usable URL or 401/403 (terminal-for-this-cam, no spin-retry)

**H.264 / media3 (Phase 21):**
- `compositeMedia3Feed` (`app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt`) — H.264-first feed; tries RTSP (lead) then falls through to MJPEG/Snapshot on decoder error (never dead-ends while a lower rung is viable)
- ExoPlayer built via `media3-exoplayer-rtsp` + `media3-exoplayer-hls`; `setForceUseRtpTcp(true)` (MediaMTX serves RTP over TCP only)
- ALL ExoPlayer ops marshal to `Dispatchers.Main`; orchestration on `Dispatchers.IO`
- Surface lifecycle: collects `Media3SurfaceProvider.surface` StateFlow for the player's whole lifetime (re-attaches on rotation instead of pinning a destroyed surface)
- `WebcamClients` (`app/src/main/java/works/mees/dinghy/net/WebcamClients.kt`): derives two OkHttp postures from the shared client — `streamClient` (`readTimeout(0)`) for MJPEG/probe; `snapshotClient` (finite ~5s) for snapshot polling

## CameraX + ZXing QR Decode (Spool Screen)

- CameraX 1.5.0 (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) — live camera feed for QR scanning
- ZXing Core 3.3.3 — `QrCodeAnalyzer` (`app/src/main/java/works/mees/dinghy/ui/spool/scan/QrCodeAnalyzer.kt`) decodes QR frames from CameraX `ImageAnalysis`
- `QrPayloadParser` (`app/src/main/java/works/mees/dinghy/ui/spool/scan/QrPayloadParser.kt`) parses the Spoolman QR payload to extract spool ID

## Moonraker mDNS Discovery

- `MoonrakerDiscovery` (`app/src/main/java/works/mees/dinghy/config/MoonrakerDiscovery.kt`) — fully-lazy NSD scanner using Android `NsdManager`; machinery acquired only inside `discover()` on collect and released on `awaitClose`; triggered by the Settings "Scan" button
- Injected into `AppContainer`; no Context held by the container itself

## Data Storage

**Databases:**
- None — no Room, no SQLite

**DataStore Preferences:**
- Six independent `*.preferences_pb` files owned and created once by `DinghyApp` (`app/src/main/java/works/mees/dinghy/DinghyApp.kt`); injected into `AppContainer`
  - `theme.preferences_pb` — theme tuple (seed/dark/mode/shift/maxItems/overrides) + S/M/L `--fs` + dev-cycler enable; `ThemePrefs` (`app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt`)
  - `connection.preferences_pb` — single legacy connection; `ConnectionStore` (`app/src/main/java/works/mees/dinghy/config/ConnectionStore.kt`)
  - `profiles.preferences_pb` — managed profile set + active selection; `ProfileStore` (`app/src/main/java/works/mees/dinghy/config/ProfileStore.kt`)
  - `macros.preferences_pb` — macro ordering/pinning prefs; `MacroPrefs`
  - `webcam.preferences_pb` — webcam selection + preferences; `WebcamPrefs`
  - `babystep.preferences_pb` — babystep enable toggle + first-layer window; `BabystepPrefs`
- All writes route through a process-lifetime scope (`AppContainer.writeScope`) — never a composition scope (prevents silent DataStore write drops on same-frame navigation)

**File Storage:**
- None (no local file cache; thumbnails are in-memory only via Coil's 2 MB MemoryCache)

**Caching:**
- Coil `MemoryCache` — capped at 2 MB for gcode file thumbnails (`FileThumbnailLoader`); no disk cache configured

## Authentication & Identity

**Auth Provider:**
- Moonraker trusted-client / API-key (optional) — `MoonrakerAuth` (`app/src/main/java/works/mees/dinghy/auth/MoonrakerAuth.kt`)
- Open/trusted-client path is the default (no key configured)
- Keyed path: `X-Api-Key` header on REST; oneshot `?token=` on WebSocket connect (5s TTL, single-use, fetched immediately before each connect)
- `server.connection.identify` carries `type="display"` + `client_name="Dinghy Display"` + `url="https://mees.works/dinghy-display"` (Moonraker requires a non-empty `url` field or returns 400)
- No OAuth, no external identity provider

## Monitoring & Observability

**Error Tracking:**
- None — no Sentry, no Firebase Crashlytics (GMS-free requirement)

**Logs:**
- `android.util.Log` used in `MoonrakerService` only; API key / `?token=` URLs are NEVER logged (security rule enforced via `redactWebcamUrl()`/`redactWsUrl()` — all URL surfaces must pass through these)
- Pseudolocale builds (`en-XA`/`ar-XB`) on debug for i18n sweep only

## CI/CD & Deployment

**Hosting:**
- GitHub Releases — signed APK (`armeabi-v7a`) distributed as a release asset

**CI Pipeline:**
- Not yet wired (Phase 22 / PKG-01); dev builds use `E:\Android\sign-release.bat` (debug-keystore sign for on-device installs)

**Remote:**
- `mrmees/dinghy-display` (private GitHub); push/config via Windows `git.exe` (Linux git fails `chmod` on `/mnt/e` drvfs)

## Build-time Static Configuration (dev only)

**Source:** gitignored `local.properties` in the repo root → `BuildConfig` fields regenerated each build
- `BuildConfig.MOONRAKER_HOST` — dev printer IP (default `192.168.1.50`)
- `BuildConfig.MOONRAKER_PORT` — default `7125`
- `BuildConfig.MOONRAKER_API_KEY` — default empty

These are overridden at runtime by the user-saved `ProfileStore` connection; `DevConfig` (`app/src/main/java/works/mees/dinghy/config/DevConfig.kt`) reads `BuildConfig` for the static fallback.

## Webhooks & Callbacks

**Incoming:**
- None — no HTTP server on the tablet side

**Outgoing:**
- None — all communication is client-initiated over the Moonraker WebSocket or REST endpoints

---

*Integration audit: 2026-06-08*
