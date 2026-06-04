# Phase 10: Webcam Streaming - Research

**Researched:** 2026-06-03
**Domain:** Android MJPEG `multipart/x-mixed-replace` decode over OkHttp + Moonraker `/server/webcams/list` enumeration, on the Adreno-320 / 2GB perf FLOOR
**Confidence:** HIGH (codebase + live-printer probes verified; decode mechanics CITED from Android docs; exact fps/sample numbers ASSUMED pending on-device flox measurement)

## Summary

This phase decodes the common Klipper MJPEG webcam case in-app and renders it hard-downscaled, with a snapshot-poll fallback when the stream isn't MJPEG-decodable. The UX is **locked** by the owner's `camera_feed` note and `10-CONTEXT.md` (D-01..D-13) — research below covers MECHANICS only.

**The single most important finding — a mock-vs-reality landmine, VERIFIED by live probe today (2026-06-03):** *both* of the project's test printers run the owner's own **ravens-perch / MediaMTX**, which registers every camera with `service: "webrtc-mediamtx"` and a `stream_url` pointing at the WHEP/WebRTC path (`http://host:8889/N/`). That stream is the **deferred** (out-of-scope) case. A plain HTTP GET of that URL returns `404 / Content-Type: text/plain` from `mediamtx` — it is NOT `multipart/x-mixed-replace`. **Consequence: on the owner's actual hardware, the MJPEG-stream rung (D-01 rung 1) will essentially never engage — every ravens-perch cam falls straight to the snapshot-poll rung (rung 2).** This is exactly why D-02's "Content-Type is source of truth, `service` is only a hint" rule exists, and it means **the snapshot-poll path is the primary on-device-demoable path for this project's printers, not an edge case.** The MJPEG decoder must still be built and unit-proven against golden byte streams (crowsnest is the de-facto standard for *other* users), but **on-device acceptance will be driven through the snapshot ladder** unless a crowsnest/ustreamer cam is temporarily stood up. Plan the live UAT accordingly.

Second critical finding: the two printers **differ in snapshot auth**, both verified live today. The **Ender 3** snapshot URL carries a `?token=...` query and returns `200 image/jpeg` on a plain GET. The **Ender 5** snapshot URL has NO token in `/server/webcams/list` and returns **`401 Unauthorized` with `WWW-Authenticate: Basic realm="Ravens Perch"`** — ravens-perch's optional nginx Basic Auth is enabled on that host. So even the snapshot fallback is not guaranteed to work without credentials we don't have. The decoder/poller must degrade gracefully to the rung-3 "unsupported / unreachable" card on a 401/403, never crash or spin.

**Primary recommendation:** Build a lean Kotlin MJPEG multipart decoder over the **existing shared OkHttp client**, gated behind a Content-Type sniff; pair it with a snapshot poller; host the live frame surface as a classic **`View` inside Compose via `AndroidView`** following the established `GraphViewHost`/`ThemeableView` pattern. Reuse one mutable ARGB_8888 bitmap sized to the view via `inSampleSize`+`inBitmap`, drop-behind frames, cap decode ~10–15 fps (pin on flox). Bind the whole stream/poll to page-visible-foreground only. Resolve relative + `localhost`/`127.0.0.1` URLs against the configured Moonraker host (D-09).

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CAM-01 | Camera — native MJPEG stream view (WebRTC deferred) | Moonraker enumeration shape (§Standard Stack / §Code Examples), lean MJPEG multipart decode over the existing OkHttp client (§Architecture Patterns), Adreno-320 decode budget (§Adreno-320 Decode Budget), snapshot-poll fallback + Content-Type sniff (D-01/D-02), URL resolution (D-09), page-visible lifecycle + backoff (D-11/12/13), and the WebRTC-deferred dead-end card (D-04, SC-4). |
</phase_requirements>

<user_constraints>
## User Constraints (from CONTEXT.md)

> The design/UX is LOCKED by the owner's `docs/view_specific_notes/camera_feed` note and `10-CONTEXT.md`. Research the MECHANICS only — do NOT re-open layout/interaction questions.

### Locked Decisions (D-01..D-13, verbatim intent)

- **D-01:** Three-rung fallback ladder per camera: (1) decode the MJPEG `multipart/x-mixed-replace` `stream_url`; (2) if not MJPEG-decodable, poll `snapshot_url` at ~2 fps; (3) no usable stream AND no snapshot → "unsupported" card.
- **D-02:** Path selection uses the `service` field as a **HINT** but the HTTP **`Content-Type` as source of truth** — confirm `multipart/x-mixed-replace` before committing to the MJPEG decoder (the `service` string is frequently blank/misconfigured).
- **D-03:** Snapshot-fallback indicator = a small **persistent corner badge** (e.g. "Snapshot ~2fps") for the whole time fallback is active. Not a one-shot toast.
- **D-04:** Dead-end (rung 3) card lives inside the framing cutout, **names the detected service** ("This camera uses WebRTC — not supported yet"), and has **NO open-in-browser button**.
- **D-05:** **No user-facing tuning knobs this phase** — fixed sane defaults only. The Adreno-320 floor dictates values.
- **D-06:** Fixed decode policy: cap decode to **~10–15 fps**, always **`inSampleSize`-downscale to the view's pixel size**, **drop frames when decode falls behind** (never queue), and **reuse a single bitmap**. Exact fps/sample to be PINNED by on-device flox measurement.
- **D-07:** Snapshot-fallback poll rate = **~2 fps** (~every 500ms).
- **D-08:** Webcam drawer tile is **GREYED/DISABLED — not hidden — when zero cams** (deliberate departure from Phase-9 hide-the-tile gating; matches the App Drawer "coming soon" greyed convention).
- **D-09:** **Aggressive URL resolution:** resolve relative `stream_url`/`snapshot_url` against the configured Moonraker host AND **rewrite `localhost`/`127.0.0.1` to the Moonraker host the tablet uses** (the #1 "works in Mainsail, blank here" gotcha).
- **D-10:** **Preferred camera = last-viewed, remembered per printer** (DataStore-style, keyed by printer/connection). No explicit "set as default". Fresh-printer default = first cam in list order.
- **D-11:** On stall/mid-view drop: **keep the last good frame visible (dimmed) with a subtle "Reconnecting…" overlay** while auto-retrying. Do NOT clear to an error state on transient blips.
- **D-12:** **Retry with backoff (≈1s → cap ≈10s) only while the Webcam page is foreground.** Stop retrying entirely on navigate-away/background.
- **D-13:** **Page-visible lifecycle:** stream/poll runs ONLY while the Webcam page is visible+foreground; pauses/stops cleanly on background or navigation (SC-3).

### Claude's Discretion

- Exact decode fps cap and `inSampleSize` step (D-06) — measure on flox.
- The precise MJPEG multipart boundary-parsing implementation (lean Kotlin over OkHttp streaming; old Java refs are reference-only, NOT dependencies).
- Threading/coroutine structure for off-UI-thread decode + frame hand-off to the view.

### Deferred Ideas (OUT OF SCOPE)

- **WebRTC / H.264 decode** (`camera-streamer`/`go2rtc`/`webrtc-mediamtx`) — documented as deferred (SC-4). This phase only renders the rung-3 dead-end card for it.
- **Print Status home mini-preview / cam thumbnail** — its own future enhancement.
- **Any webcam config editing** (add/delete/edit — Moonraker `server.webcams.*` write ops are catalog `reference_only`).
- **"Layer level doesn't work"** (camera_feed note's last line) — a Print Status data bug, NOT webcam scope; capture to backlog separately.
</user_constraints>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Enumerate cams (`/server/webcams/list`) | API / Backend (Moonraker JSON-RPC over the spine) | — | One-shot read, edge-driven (page-open / handshake), off the throttled hot path — same shape as `files.get_directory`. NOT a subscribe (cadence contract). |
| Content-Type sniff + rung selection | Client (the tablet's own HTTP request) | — | Must hit the actual stream URL; cannot be derived from Moonraker state. |
| MJPEG stream decode loop | Client (background coroutine, off UI thread) | — | Pure on-device CPU work; the printer host only serves bytes. Keep it OFF the Moonraker JSON-RPC connection (its own HTTP conn — exempt from single-subscribe per cadence contract). |
| Snapshot poll loop | Client (background coroutine) | — | Same — its own short-lived GETs, NOT a JSON-RPC poll. |
| Frame raster → screen | Client (custom `View` via `AndroidView`) | — | Classic-Views Canvas surface for the high-churn live raster (ADR-0001 hybrid: high-churn surfaces are Views). |
| Preferred-cam persistence | Client (DataStore) | — | Per-printer keyed prefs (theme/macro precedent). |
| URL resolution / localhost rewrite | Client | — | Depends on the tablet's configured Moonraker host, not the printer. |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OkHttp | 4.12.0 (pinned, `libs.versions.toml`) | Streaming HTTP for the MJPEG body + snapshot GETs | `[VERIFIED: codebase libs.versions.toml]` Already the one shared client (`MoonrakerSocket.defaultClient()`); reuse its TLS/pool. `ResponseBody.source()` (Okio `BufferedSource`) is the right streaming primitive for boundary parsing. |
| `android.graphics.BitmapFactory` | platform (API 23+) | `decodeByteArray` per JPEG part, with `inBitmap`+`inSampleSize`+`inMutable` | `[CITED: developer.android.com/topic/performance/graphics/manage-memory]` Standard reuse path; `inBitmap`+`inSampleSize` combine on API 19+. No dependency. |
| Coroutines + Flow | kotlinx-coroutines 1.9.x | Off-UI-thread decode loop + frame hand-off; backoff/retry; lifecycle binding | `[VERIFIED: codebase]` The project's universal concurrency model; `callbackFlow`/`StateFlow` precedent throughout. |
| `AndroidView` (Compose interop) | Compose BOM (pinned) | Host the custom live-raster `View` in the Compose tree | `[VERIFIED: codebase render/GraphViewHost.kt]` Established hybrid host pattern. |
| DataStore (Preferences) | androidx.datastore 1.1.x | Per-printer preferred-cam (D-10) | `[VERIFIED: codebase]` Theme/macro prefs precedent. |

### Supporting / reuse (no new deps)
| Asset | Path | Use |
|-------|------|-----|
| Shared OkHttp client | `net/MoonrakerSocket.kt` → `defaultClient()` | **DO NOT** reuse the `readTimeout(0)` ws client verbatim for snapshot GETs — a snapshot needs a finite read timeout. For the MJPEG **stream**, a 0/long read timeout is correct (a stream legitimately holds the connection open). See Pitfall 4. Build a small derived client (`.newBuilder().readTimeout(...).build()`) sharing the connection pool. |
| `X-Api-Key` injection | `auth/MoonrakerAuth.kt` → `xApiKeyHeader()` | Apply to the `/server/webcams/list` REST/JSON-RPC read where keyed. NOTE: the webcam HTTP host (nginx/mediamtx) is often a DIFFERENT auth domain than Moonraker — the `X-Api-Key` is for Moonraker, not necessarily the snapshot/stream server (see Pitfall 3). |
| Off-thread bitmap decode + bounded discipline | `ui/files/FileThumbnailLoader.kt`, Phase-7 thumbnail work | Reference for off-UI-thread decode + memory bounding; but the live MJPEG loop is **custom** (not Coil) — Coil is request-per-image, the MJPEG loop is one long stream. |
| Views-in-Compose host + push-tokens | `render/GraphViewHost.kt`, `theme/views/ThemeableView.kt` | The custom MJPEG `View` should implement `ThemeableView` (for the framing/overlay token colors) and be hosted exactly like `GraphView`. |
| Drawer tile gating (greyed) | `ui/shell/AppDrawer.kt` (`DrawerTileSpec`, `dest = null` → inert+disabled) | D-08 greyed-when-no-cams is **already the drawer's native idiom** — a tile with a null/disabled dest renders greyed. Wire a `Dest.Webcam` and gate its enablement on cam-count > 0. |
| `Dest` enum | `ui/route/TopRoute.kt` (`enum class Dest { … }`) | Add `Webcam`. |
| Per-printer prefs | `theme/ThemePrefs.kt`, `ui/macros/MacroPrefs.kt` | Pattern for the per-printer preferred-cam DataStore. |
| Connection host/base | `config/ConnectionConfig.kt` (`host`, `httpBase = "http://$host:$port"`) | The resolution base for D-09. **There is no existing relative-URL/localhost-rewrite helper — this phase must add one** (see §Don't Hand-Roll / Pitfall 1). |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Custom MJPEG decoder | `niqdev/ipcam-view`, `perthcpe23/android-mjpeg-view` | `[CITED: CLAUDE.md camera note]` Both old/Java, unmaintained, drag in their own threading/View. CONTEXT D-decision: **reference only, reimplement lean in Kotlin.** Do NOT add as deps. |
| Custom MJPEG decoder | Coil 3 (already in stack) for the stream | Coil is request-per-image; it can serve the **snapshot stills** (it already decodes/downsamples off-thread), but it cannot consume a single never-ending `multipart/x-mixed-replace` body. Use Coil for snapshots if convenient; custom loop for the live stream. |
| OkHttp stream | Ktor/`HttpURLConnection` | New stack or a second HTTP config on a 2GB device; the shared OkHttp client already exists. |
| WebRTC now | `org.webrtc` / libdatachannel | `[CITED: CLAUDE.md]` Large lib, heavy on Adreno-320/API-23. Explicitly deferred (SC-4). |

**Installation:** None. **No new dependencies.** Everything is platform (`BitmapFactory`) or already-pinned (`okhttp`, `coil`, `datastore`, coroutines, Compose interop). This keeps the minSdk-23 floor auditable (PKG-02).

## Package Legitimacy Audit

> **N/A — this phase installs no external packages.** All capabilities are satisfied by the Android platform (`android.graphics.*`) and already-pinned, already-on-device dependencies (OkHttp 4.12.0, Coil 3.1.0, DataStore, kotlinx-coroutines, Compose). slopcheck not required (no install surface). The version catalog `gradle/libs.versions.toml` is unchanged by this phase.

## Architecture Patterns

### System Architecture Diagram

```
                  (Moonraker JSON-RPC over the existing spine — edge-driven, NOT polled)
  Webcam page open ──► SpineHandle.rpc.request(server.webcams.list)
                           │  result.webcams[]  (name, service, stream_url, snapshot_url,
                           │                      target_fps, flip_*, rotation, aspect_ratio, uid, …)
                           ▼
                  ┌─────────────────────────┐
                  │ WebcamHolder (per-page)  │  resolve each url (D-09: relative→host, localhost→host)
                  │ - cam list (Field)       │  pick preferred cam (D-10: per-printer DataStore | first)
                  │ - selected cam           │
                  └───────────┬─────────────┘
                              │ selected cam → its OWN HTTP connection (separate from JSON-RPC)
                              ▼
                  ┌──────────────────────────────────────────────┐
   D-02 sniff ──► │ 1. HEAD/GET stream_url, read Content-Type     │
                  │    multipart/x-mixed-replace? ── yes ─► RUNG 1 │──► MjpegStreamDecoder
                  │                                  └─ no  ─┐     │     (Okio source → split on
                  │ 2. snapshot_url present & 2xx? ── yes ──┼─► RUNG 2│      boundary → decodeByteArray
                  │                                  └─ no  ─┤     │      → 1 reused bitmap → frame)
                  │ 3. neither usable ──────────────────────┴─► RUNG 3│──► SnapshotPoller (~2fps GET loop)
                  └──────────────────────────────────────────────┘     RUNG 3 ─► "unsupported" card (D-04)
                              │ frames (drop-behind, never queue — D-06)
                              ▼
                  ┌─────────────────────────┐
                  │ WebcamView (classic View)│  AndroidView host (GraphViewHost pattern)
                  │ - draws current Bitmap   │  pixel-square, never-stretch (Matrix), rounded cutout
                  │ - rotation/flip applied  │  badge (D-03), reconnecting overlay (D-11),
                  │ - ThemeableView tokens   │  cycle overlay (camera_feed: burst icon + cam name)
                  └─────────────────────────┘
                              ▲
   Lifecycle ─────────────────┘  repeatOnLifecycle(STARTED) / DisposableEffect: the decode+poll+retry
   (D-11/12/13)                  loops run ONLY while the page is foreground; cancel on background/nav.
```

File-to-implementation mapping is in the Component Responsibilities table below — the diagram shows data flow, not files.

### Component Responsibilities (proposed; planner refines)
| Conceptual component | Likely file(s) | Reuses |
|----------------------|----------------|--------|
| Enumeration read | `command/CommandRegistry.kt` (+`webcamsList` spec), wired through `SpineHandle` | `files.get_directory` one-shot shape; cadence-contract "edge-driven one-shot" |
| Webcam models + URL resolver | `state/WebcamModels.kt` (new), `net/WebcamUrl.kt` (new) | `ConnectionConfig.httpBase` |
| Page holder | `ui/webcam/WebcamHolder.kt` (new) | `MoveHolder`/`ConsoleHolder` holder pattern |
| Rung selection / Content-Type sniff | `net/WebcamProbe.kt` (new) | shared OkHttp client |
| MJPEG decoder | `net/MjpegStreamDecoder.kt` (new) | OkHttp `ResponseBody.source()` (Okio) |
| Snapshot poller | `net/SnapshotPoller.kt` (new) | shared OkHttp client (finite read timeout) |
| Live raster view + host | `render/WebcamView.kt`, `render/WebcamViewHost.kt` (new) | `GraphView`/`GraphViewHost`/`ThemeableView` |
| Screen | `ui/webcam/WebcamScreen.kt` (new) | `ScreenScaffold`, Focus/Field/Gutter grammar |
| Preferred-cam prefs | `ui/webcam/WebcamPrefs.kt` (new) | `MacroPrefs`/`ThemePrefs` DataStore |
| Drawer/route wiring | `ui/route/TopRoute.kt` (+`Webcam`), `ui/shell/AppDrawer.kt`, `ui/shell/AppShell.kt` | `DrawerTileSpec`, greyed-tile idiom |

### Pattern 1: Content-Type sniff before committing (D-02)
**What:** Issue a request to `stream_url`, read the response `Content-Type`, branch on `multipart/x-mixed-replace` before handing the body to the MJPEG decoder.
**When to use:** Always — `service` is a hint only and is frequently wrong/blank in real installs (and is `webrtc-mediamtx` on BOTH this project's printers — see Summary).
**Example:**
```kotlin
// Source: pattern derived from OkHttp ResponseBody streaming + Moonraker webcam D-02.
// Use a GET (not HEAD) because some MJPEG servers don't answer HEAD; read only the header,
// then either keep the body (MJPEG) or close it (fall through). Boundary token also lives here.
val req = Request.Builder().url(resolvedStreamUrl).build()
val resp = streamClient.newCall(req).execute()      // streamClient: long/0 read timeout
val ct = resp.header("Content-Type").orEmpty()       // e.g. "multipart/x-mixed-replace;boundary=..."
when {
    !resp.isSuccessful -> { resp.close(); fallToSnapshot() }   // 401/403/404 etc.
    ct.startsWith("multipart/x-mixed-replace", ignoreCase = true) -> {
        val boundary = ct.substringAfter("boundary=", "").trim().trim('"')
        decodeMjpeg(resp.body!!.source(), boundary)  // RUNG 1 — keep the body open
    }
    else -> { resp.close(); fallToSnapshot() }        // RUNG 2 (image/jpeg or anything else)
}
```

### Pattern 2: Lean MJPEG multipart decode loop (RUNG 1)
**What:** Stream the body via Okio `BufferedSource`, find each part's headers (`Content-Length` when present, else scan for the next boundary), read the JPEG bytes, `decodeByteArray` into one reused bitmap, hand off, **drop if behind**.
**When to use:** When the sniff confirms `multipart/x-mixed-replace`.
**Example:**
```kotlin
// Source: lean reimplementation (CONTEXT: niqdev/ipcam-view & perthcpe23 are REFERENCE ONLY).
// Boundary on the wire is preceded by "--". Parts look like:
//   --<boundary>\r\nContent-Type: image/jpeg\r\nContent-Length: NNNN\r\n\r\n<JPEG bytes>\r\n
// Content-Length is OPTIONAL; ustreamer usually sends it, some servers don't. Handle both:
//   - if Content-Length present: read exactly that many bytes.
//   - else: scan for the JPEG SOI/EOI (FFD8 … FFD9) or the next boundary marker.
private val opts = BitmapFactory.Options().apply {
    inMutable = true
    inSampleSize = sampleStep        // computed from frame size vs view px (Pattern 3)
    inBitmap = reusedBitmap          // one mutable ARGB_8888; null on first frame
}
// drop-behind: a Channel(CONFLATED) or AtomicReference<Bitmap?> the View reads — a slow
// View never backs up the decoder; newest frame wins (D-06 "never queue").
while (currentCoroutineContext().isActive) {
    val jpegBytes = readNextPart(source, boundary)   // suspending, honors cancellation
    val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts)
    if (bmp != null) { opts.inBitmap = bmp; latestFrame.set(bmp); view.postInvalidate() }
}
```
Key safety: `inBitmap` reuse requires the next decoded byte-count ≤ the reused allocation. A fixed-resolution camera produces same-size frames, so one bitmap suffices; if `decodeByteArray` throws `IllegalArgumentException` ("Problem decoding into existing bitmap"), drop `inBitmap` for one frame and re-establish. `[CITED: developer.android.com/topic/performance/graphics/manage-memory]`

### Pattern 3: `inSampleSize` to view pixel size (D-06)
**What:** Decode the frame at `inSampleSize` so the decoded bitmap is ≈ the on-screen view size, not the camera's native res. Two-pass: first `inJustDecodeBounds=true` to read `outWidth/outHeight` (do this ONCE per stream, the res is stable), compute the largest power-of-two sample that keeps decoded ≥ view px, then stream at that fixed sample.
**Why:** A 1920×1080 ARGB_8888 frame is ~8.3 MB; at `inSampleSize=4` it's ~520 KB. On a 2GB device decoding native-res frames at 15 fps will OOM/GC-thrash. `[CITED: developer.android.com/topic/performance/graphics/load-large-bitmaps]`

### Pattern 4: Page-visible lifecycle binding (D-11/12/13)
**What:** Start the decode/poll/retry loops only while the page is foreground; cancel on background/nav.
**When to use:** Always — this is the governing rule (SC-3) and the cadence-contract "stop when not visible" spirit.
**Example:**
```kotlin
// Source: codebase precedent — collectAsStateWithLifecycle is already used across ui/calibration/*.
// For an imperative loop, bind to the lifecycle directly:
val lifecycleOwner = LocalLifecycleOwner.current
LaunchedEffect(selectedCamUid, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        // launched fresh on each STARTED, auto-cancelled on STOPPED (background/nav-away)
        runStreamWithBackoff(selectedCam)   // 1s→10s backoff, foreground-only (D-12)
    }
}
```
On nav-away within the app (Compose back-stack pop, not a process background), the simplest correct binding is the effect's own cancellation (the `LaunchedEffect` leaves composition → coroutine cancelled). `repeatOnLifecycle` covers the screen-off/home-button case. Use both; they compose cleanly.

### Anti-Patterns to Avoid
- **Polling Moonraker for webcam state.** `/server/webcams/list` is a **one-shot edge-driven read** (page open / handshake), exactly like `files.get_directory`. Do NOT add a `while(true){ delay; query }` loop, and do NOT add a second `objects.subscribe`. `[CITED: docs/request-cadence-contract.md Rules 1–3]`
- **Running the MJPEG loop on the Moonraker websocket / JSON-RPC connection.** The stream is its OWN HTTP connection (cadence contract explicitly exempts it from single-subscribe).
- **Reusing the `readTimeout(0)` ws client for snapshots.** A snapshot GET that hangs forever never fails → the poller wedges. Snapshots need a finite read timeout; the stream needs a long/0 one. Two postures off the same connection pool.
- **Queuing frames.** Drop-behind only (CONFLATED). A backed-up queue on a slow Adreno-320 = ever-growing latency + memory.
- **Decoding at native resolution.** Always `inSampleSize` to view px.
- **Trusting `service` to choose the decoder.** Content-Type only (D-02).
- **Blocking the UI thread with `decodeByteArray`.** Decode on a background dispatcher; hand the bitmap to the View via `postInvalidate`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JPEG decode | A JPEG parser | `BitmapFactory.decodeByteArray` | Platform, hardware-assisted, handles all the JPEG variants. |
| Bitmap memory reuse | A custom pool | `inBitmap` + one reused mutable bitmap | `[CITED]` Standard, avoids GC churn on the 2GB floor. |
| Streaming byte reader | Manual `InputStream` index math | OkHttp `ResponseBody.source()` (Okio `BufferedSource`) | `indexOf(ByteString)`, `readByteArray(n)`, `request(n)` make boundary scanning safe + cancellable. |
| HTTP client / TLS / pool | A second client | Existing shared OkHttp client (derived posture) | One pool/TLS config (CLAUDE.md). |
| Compose↔Views host | A new interop bridge | `AndroidView` per `GraphViewHost` | Established, theme-token-aware. |
| Backoff | A custom timer | `net/Backoff.kt` (already in repo) | Reuse the project's backoff. |
| URL relative-resolution | String concat | `okhttp3.HttpUrl` `resolve()` + an explicit localhost-rewrite | `HttpUrl.resolve()` correctly joins relative paths; the localhost rewrite is the one bit you DO hand-roll (small, see Pitfall 1). |

**Key insight:** Almost all of this is platform + existing-asset reuse. The genuinely new, custom code is small and bounded: the multipart boundary scanner, the rung selector, the URL resolver/rewriter, and the live `View`. Everything else is wiring.

## Runtime State Inventory

> N/A — this is a greenfield feature phase (new screen + new decode path), not a rename/refactor/migration. No stored data, OS-registered state, or build artifacts carry a name this phase changes. The only new persisted state is the per-printer preferred-cam key in DataStore (additive, no migration). **None — verified: this phase adds a screen and a decode path; it renames/migrates nothing.**

## Common Pitfalls

### Pitfall 1: `localhost`/`127.0.0.1`/relative URLs — the #1 "works in Mainsail, blank here" bug (D-09)
**What goes wrong:** Moonraker returns `stream_url`/`snapshot_url` exactly as configured. Common real values: `"/webcam/?action=stream"` (relative), `"http://127.0.0.1:8080/?action=stream"` or `"http://localhost/webcam/"` (host-local). A browser ON the printer host resolves these fine; the tablet cannot — `127.0.0.1` is the *tablet*, and a bare relative path has no host. Result: a blank/broken feed.
**Why it happens:** The URLs are written from the printer host's frame of reference.
**How to avoid:** Resolve aggressively. (a) Relative → `HttpUrl.get(httpBase).resolve(rawUrl)`. (b) After resolving, if the host is `localhost`/`127.0.0.1`/`0.0.0.0` (or `::1`), **rewrite the host to the configured Moonraker host** (`ConnectionConfig.host`), preserving port/path/query. **Preserve the `?token=...` query** (ravens-perch E3 needs it — verified live). Unit-test this resolver with golden inputs (see Validation).
**Warning signs:** Feed blank on-device but the same cam works in Fluidd/Mainsail on a desktop.

### Pitfall 2: ravens-perch / mediamtx registers `webrtc-mediamtx` — the MJPEG rung never fires on this project's printers (VERIFIED live 2026-06-03)
**What goes wrong:** You build + unit-test the MJPEG decoder, it's green, but on the owner's flox+E5/E3 the feed is always snapshot-only (or blank), and you suspect a decoder bug. There is no decoder bug — the `stream_url` is a WHEP/WebRTC endpoint (`:8889/N/`) that 404s on a plain GET and is never `multipart/x-mixed-replace`.
**Why it happens:** Both test printers run ravens-perch/MediaMTX. `service: "webrtc-mediamtx"`, `stream_url: "http://192.168.1.12x:8889/N/"`.
**How to avoid:** Expect rung-2 (snapshot) as the **primary on-device path** for this project's hardware. Drive on-device acceptance through the snapshot ladder (and the rung-3 card for the WebRTC stream). To exercise rung-1 live, temporarily stand up a crowsnest/ustreamer MJPEG cam, OR rely on the golden-byte-stream unit tests as the MJPEG proof. **Tell the planner the live UAT plan must not assume an MJPEG stream is reachable on E5/E3.**
**Warning signs:** Every cam shows the "Snapshot ~2fps" badge; the WebRTC cam shows the dead-end card.

### Pitfall 3: snapshot auth differs per host — E5 = Basic Auth 401, E3 = URL token 200 (VERIFIED live 2026-06-03)
**What goes wrong:** The E3 snapshot URL has `?token=...` and returns `200 image/jpeg`. The E5 snapshot URL has NO token and returns **`401 + WWW-Authenticate: Basic realm="Ravens Perch"`** (ravens-perch optional nginx Basic Auth is on). The Moonraker `X-Api-Key` does NOT satisfy nginx Basic Auth — different auth domain.
**Why it happens:** The webcam HTTP server (nginx/mediamtx) is separate from Moonraker; its auth is independent and not exposed via `/server/webcams/list` (beyond a token baked into the URL when present).
**How to avoid:** Treat a 401/403 on stream OR snapshot as **rung-3 "camera not reachable / auth required"** — degrade gracefully, never crash, never spin-retry a 401 (a 401 won't fix itself on retry; D-12 backoff should treat 401/403 as terminal-for-this-cam, not transient). Preserve any in-URL token verbatim (D-09). This phase does **not** add a webcam-credentials UI (D-05 "no knobs"); the dead-end card is the honest outcome.
**Warning signs:** E5 cam blank/unsupported while E3 cam shows snapshots — that's *correct* given the auth config, not a bug.

### Pitfall 4: read-timeout posture — stream vs snapshot are opposite
**What goes wrong:** Reuse the ws client (`readTimeout(0)`) for snapshots → a wedged snapshot GET hangs the poller forever. Or use a short read timeout for the MJPEG stream → the stream is killed between frames.
**How to avoid:** Two derived clients off the shared pool: stream = `readTimeout(0)` (or generous, e.g. 30s idle), snapshot = finite (e.g. 5s). `client.newBuilder().readTimeout(...).build()` shares the connection pool/TLS.
**Warning signs:** Snapshot fallback freezes on a flaky cam; or MJPEG drops a frame and never recovers.

### Pitfall 5: `inBitmap` size mismatch crash
**What goes wrong:** If a camera changes resolution mid-stream (rare, but multi-cam cycling switches resolution), reusing a too-small bitmap throws `IllegalArgumentException`.
**How to avoid:** Re-read `outWidth/outHeight` (cheap `inJustDecodeBounds`) when (re)starting a stream or switching cams; allocate a fresh reused bitmap per cam. Catch the decode exception, drop `inBitmap` for one frame, re-establish. `[CITED]`
**Warning signs:** Crash on cam-cycle.

### Pitfall 6: mock-vs-reality (the project's recurring class — bitten twice, see STATE.md)
**What goes wrong:** A too-lenient `FakeMjpegStream` (e.g. always sends a clean `Content-Length`, never a split JPEG across reads, never a 401) hides the real-server behaviors that break on-device.
**How to avoid:** Harden the test fixtures to the REAL contract captured today: include a fixture with NO `Content-Length` (boundary-scan path), a JPEG split across multiple `source.read()` chunks, a `webrtc-mediamtx` `/server/webcams/list` frame (must route to rung-3, not rung-1), a relative-URL cam, a `127.0.0.1` cam, a `?token=` snapshot cam, and a 401 snapshot. Capture real `/server/webcams/list` JSON from both printers as golden fixtures (see §Code Examples for the verified shapes). **On-device live UAT remains mandatory** — the unit suite alone has missed real bugs twice.
**Warning signs:** Green unit suite, broken/blank feed on flox.

## Code Examples

### Verified live `/server/webcams/list` shape (probed 2026-06-03)
```json
// Source: live GET http://192.168.1.120:7125/server/webcams/list (Ender 5 Plus) — VERIFIED 2026-06-03
{ "result": { "webcams": [
  {
    "name": "playstation_eye", "enabled": true, "icon": "mdiWebcam",
    "aspect_ratio": "16:9", "target_fps": 30, "target_fps_idle": 5, "location": "printer",
    "service": "webrtc-mediamtx",                                  // ← HINT only (D-02); this is the DEFERRED case
    "stream_url": "http://192.168.1.120:8889/3/",                  // ← WHEP/WebRTC, NOT multipart — 404 on plain GET
    "snapshot_url": "http://192.168.1.120/cameras/snapshot/3.jpg", // ← E5: NO token → 401 Basic Auth (Pitfall 3)
    "flip_horizontal": false, "flip_vertical": false, "rotation": 0,
    "source": "database", "extra_data": {}, "uid": "5bfa41e7-…"
  }
] } }
```
```json
// Source: live GET http://192.168.1.121:7125/server/webcams/list (Ender 3 Pro) — VERIFIED 2026-06-03
{ "result": { "webcams": [
  {
    "name": "microsoft®_lifecam_hd-3000:_mi", "service": "webrtc-mediamtx",
    "stream_url": "http://192.168.1.121:8889/1/",
    "snapshot_url": "http://192.168.1.121/cameras/snapshot/1.jpg?token=udaFhoavcj6K04ZBhQdtqbyLXMIE69pC4TmhvORIMpk",
    "aspect_ratio": "16:9", "target_fps": 30, "target_fps_idle": 5, "rotation": 0,
    "flip_horizontal": false, "flip_vertical": false, "uid": "3ba24469-…"
    // E3 snapshot WITH token → 200 image/jpeg (84626 bytes) on plain GET — VERIFIED
  }
] } }
```

### Full Moonraker field set (per the spec — what to model)
`[CITED: moonraker.readthedocs.io/en/latest/external_api/webcams/]`
`name`, `location`, `service`, `enabled`, `icon`, `target_fps`, `target_fps_idle`, `stream_url`, `snapshot_url`, `flip_horizontal`, `flip_vertical`, `rotation`, `aspect_ratio`, `extra_data`, `source`, `uid`. Both `stream_url` and `snapshot_url` "may be a complete url, or a url path relative to Moonraker's host" — hence D-09. Model defensively: `service` may be blank; `snapshot_url` may be empty; `aspect_ratio` is a `"W:H"` string; `rotation` ∈ {0,90,180,270}; `flip_*` booleans drive a draw-time `Matrix`.

### crowsnest / ustreamer MJPEG endpoints (the de-facto standard for OTHER users)
`[CITED: gb-crowsnest cam-section.md]` ustreamer "mjpg" mode exposes `/webcam/?action=stream` (+ `/webcam2..4/`) on ports 8080–8083, snapshot `?action=snapshot`. The stream Content-Type is `multipart/x-mixed-replace; boundary=...` (standard MJPEG). camera-streamer mode = h.264/WebRTC = deferred. This is the rung-1 case to unit-test against golden bytes.

### Verified per-printer probe results (2026-06-03)
| Check | E5 (192.168.1.120) | E3 (192.168.1.121) |
|-------|--------------------|--------------------|
| `/server/webcams/list` | 200, 2 cams, all `webrtc-mediamtx` | 200, 3 cams, all `webrtc-mediamtx` |
| stream_url (`:8889/N/`) plain GET | 404 `text/plain` (mediamtx) | (same shape) |
| snapshot_url plain GET | **401 Basic Auth** (`realm="Ravens Perch"`) | **200 `image/jpeg`** (token in URL) |
| `webcam` component present | yes (`server.info.components`) | yes |

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `mjpg-streamer` | `ustreamer` (via crowsnest) | ~2020+ | Still `multipart/x-mixed-replace`; same decode path. |
| MJPEG-only cams | WebRTC (camera-streamer/go2rtc/mediamtx) for low latency | ~2022+ | The growing case this phase **defers** (SC-4); rung-3 card. ravens-perch is MediaMTX-WebRTC-first — hence the snapshot-primary reality on this project. |
| Old Java MJPEG view libs | Lean Kotlin OkHttp+Okio reimplementation | this project | Avoids unmaintained deps + their threading/View baggage. |

**Deprecated/outdated:** `niqdev/ipcam-view`, `perthcpe23/android-mjpeg-view` — reference patterns only, not dependencies (CONTEXT-locked).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | A starting decode cap of **~12 fps** and an `inSampleSize` chosen so the decoded frame ≈ view px holds zero-jank on flox/Adreno-320. | §Adreno-320 Decode Budget / D-06 | Must be re-pinned by on-device gfxinfo measurement; if jank appears, lower to ~8–10 fps and/or step `inSampleSize` up. This is explicitly Claude's-discretion-pending-measurement, NOT a fact. |
| A2 | A single reused mutable **ARGB_8888** bitmap at view size is the right memory posture; **RGB_565** is the fallback if 8888 still GC-churns (half the bytes, no alpha — fine for opaque camera frames). | §Adreno-320 Decode Budget | If 8888 OOMs at the chosen res, switch to 565; revisit during the perf gate. |
| A3 | The snapshot poll at **~2 fps** (D-07) is light enough to run indefinitely on the floor without jank. | D-07 | Almost certainly fine (Coil/Phase-7 decode 96px thumbs trivially); confirm on flox. |
| A4 | A 401/403 on stream/snapshot should be treated as **terminal-for-this-cam** (→ rung-3 card), not a transient retry. | Pitfall 3 / D-12 | If a cam legitimately returns transient 401 (token refresh), it'd show the card prematurely. Low risk for this phase (no creds UI); revisit if it bites. |
| A5 | `target_fps_idle` (5 on these cams) is a server-side hint for the printer's idle state, NOT something the client must honor; the client caps its OWN decode rate (D-06) independent of it. | §Standard Stack | If a server actually throttles its stream to `target_fps_idle`, the client simply receives fewer frames — harmless. |

**These five are the only non-verified claims.** Everything tagged `[VERIFIED]`/`[CITED]` is either confirmed in the codebase, by live probe today, or by official docs.

## Open Questions (RESOLVED)

1. **Will any rung-1 (MJPEG) cam be available for live on-device UAT?**
   - What we know: Both project printers are WebRTC/snapshot (verified). The MJPEG decoder is the headline of CAM-01 but can't be exercised live on E5/E3 as-is.
   - What's unclear: Whether the owner will temporarily stand up a crowsnest/ustreamer cam for the gate.
   - Recommendation: Treat the **golden-byte-stream unit tests as the authoritative MJPEG proof**; drive live UAT through the snapshot ladder + rung-3 card. Flag this to the owner at discuss/plan time so the SC-2 "no-OOM/no-jank live decode" gate has a concrete subject (either a temp crowsnest cam, or an instrumented test that feeds a captured MJPEG byte stream through the real `MjpegStreamDecoder` on-device).
   - **RESOLVED:** The golden-byte-stream unit tests (`MjpegStreamDecoderTest` + the with/no-Content-Length + split-JPEG fixtures, captured in plan 10-01 Wave 0) ARE the authoritative MJPEG-decode proof for this phase — there is no live MJPEG subject on E5/E3 and one is NOT required to close the phase. Live UAT is driven via the E3 token-snapshot ladder (plan 10-08, Task 2). The owner MAY optionally stand up a temporary crowsnest/ustreamer cam to exercise rung-1 live, but that is a nice-to-have, not a phase-close gate.

2. **E5 snapshot is behind Basic Auth we don't have credentials for.**
   - What we know: E5 snapshot = 401. E3 snapshot = 200.
   - Recommendation: E5 cams will honestly show the rung-3/unreachable card on-device; that's correct behavior, not a defect to chase. Use the E3 (token-in-URL, 200) as the working snapshot-ladder demo subject.
   - **RESOLVED:** The E5 snapshot 401 correctly drives the rung-3 dead-end card (terminal-for-cam per A4) — this is correct behavior, NOT a bug to chase. E3 (token-in-URL, 200) is the snapshot-ladder demo subject for the live UAT (plan 10-08). No webcam-credentials UI is added this phase (D-05).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Live Moonraker (E5) `/server/webcams/list` | enumeration | ✓ | api 1.5.0 | — |
| Live Moonraker (E3) `/server/webcams/list` | enumeration | ✓ | api 1.5.0 | — |
| A reachable MJPEG (`multipart/x-mixed-replace`) cam | rung-1 live UAT | ✗ | — | Golden-byte-stream unit tests; optionally stand up crowsnest temporarily |
| A reachable snapshot cam (no auth) | rung-2 live UAT | ✓ (E3, token) / ✗ (E5, 401) | — | Use E3 for the snapshot demo |
| flox device (Nexus 7 2013 / Adreno-320 / API 30) | perf gate | ✓ | LineageOS 18.1 | — |
| Windows-side Gradle (`E:\Android\gw.bat`) | build/install | ✓ | JDK 21 / SDK 35 | — |
| OkHttp / Coil / DataStore / coroutines / Compose | all | ✓ (pinned) | 4.12.0 / 3.1.0 / 1.1.x / 1.9.x / BOM | — |

**Missing dependencies with no fallback:** None that block — but the **MJPEG live path has no live subject on this project's printers** (mitigated by unit tests; see Open Q1).
**Missing dependencies with fallback:** rung-1 live cam → golden-byte-stream tests; E5 snapshot auth → use E3.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 + kotlinx-coroutines-test (JVM unit) + Compose UI test / instrumented (`androidTest`) for the View + on-device gfxinfo for perf |
| Config file | Gradle (`app/build.gradle.kts`); fixtures under `app/src/test/resources/fixtures/` (existing convention), goldens under `app/src/test/resources/golden/` |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` (pipe through `tr -d '\r'`; guard with `timeout`+taskkill per build-env memory) |
| Full suite command | `… "E:\Android\gw.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon"` + on-device gfxinfo perf capture |

### Phase Requirements → Test Map
| Req / SC | Behavior | Test Type | Automated Command | File Exists? |
|----------|----------|-----------|-------------------|-------------|
| CAM-01 / enumerate | Parse `/server/webcams/list` golden frames (E5+E3 verbatim, incl. all 16 fields, blank `service`, missing snapshot) → correct cam models | unit | `…gw.bat :app:testDebugUnitTest --tests *WebcamListParseTest*` | ❌ Wave 0 (capture goldens) |
| CAM-01 / D-02 sniff | `webrtc-mediamtx`/blank/`image/jpeg`/`multipart` Content-Types route to the correct rung (1/2/3) | unit | `…--tests *WebcamRungSelectTest*` | ❌ Wave 0 |
| CAM-01 / D-09 | Relative URL resolves against host; `127.0.0.1`/`localhost` rewritten to host; `?token=` preserved | unit | `…--tests *WebcamUrlResolverTest*` | ❌ Wave 0 |
| SC-2 / decode | Mock MJPEG byte stream (WITH and WITHOUT Content-Length; JPEG split across reads) yields N frames; bounded memory (one reused bitmap; no per-frame alloc) | unit | `…--tests *MjpegStreamDecoderTest*` | ❌ Wave 0 |
| SC-2 / no-OOM/no-jank | Feed a captured MJPEG byte stream (or live snapshot loop) through the real decoder on flox; gfxinfo p95 + **0 frozen frames** during ~15s continuous decode | on-device (manual+gfxinfo) | `adb shell dumpsys gfxinfo works.mees.dinghy` (reset→decode→dump) | ❌ Wave 0 (perf gate; system-of-record per ADR-0001 Add.2) |
| SC-1 / drop-behind | A slow consumer never backs up the decoder (CONFLATED latest-wins) | unit | `…--tests *FrameDropBehindTest*` | ❌ Wave 0 |
| SC-3 / lifecycle | Loops start on STARTED, cancel on STOPPED/nav-away; no work in background | unit (virtual time) + instrumented | `…--tests *WebcamLifecycleTest*` | ❌ Wave 0 |
| D-11 | Transient stall keeps last frame dimmed + "Reconnecting…"; does not clear to error | unit (holder state machine) | `…--tests *WebcamReconnectStateTest*` | ❌ Wave 0 |
| D-12 | Backoff 1s→10s while foreground; 401/403 terminal-for-cam (no spin) | unit | `…--tests *WebcamBackoffTest*` | ❌ Wave 0 |
| SC-4 | WebRTC/`webrtc-mediamtx` cam → rung-3 dead-end card naming the service, no browser button | unit + instrumented | `…--tests *WebcamUnsupportedCardTest*` | ❌ Wave 0 |
| D-08 | Drawer Webcam tile greyed when 0 cams, live when ≥1 | instrumented | `…connectedDebugAndroidTest --tests *DrawerWebcamGatingTest*` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** the relevant `*Test*` class(es) above (quick JVM run).
- **Per wave merge:** full `:app:testDebugUnitTest`.
- **Phase gate:** full unit suite green + `connectedDebugAndroidTest` green + **on-device flox UAT** (snapshot ladder on E3, rung-3 card on the WebRTC streams, gfxinfo 0-frozen-frames during decode) before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `app/src/test/resources/golden/webcams_list_e5.json` / `webcams_list_e3.json` — capture verbatim (the §Code Examples blocks are the source).
- [ ] `app/src/test/resources/fixtures/mjpeg_with_content_length.bin` + `mjpeg_no_content_length.bin` + `mjpeg_split_jpeg.bin` — golden MJPEG byte streams (synthesize a 2–3-frame `multipart/x-mixed-replace` body; the only place a synthetic stream is justified since no live MJPEG cam exists — clearly label it).
- [ ] `FakeMjpegStream` / `FakeWebcamHttp` test doubles **hardened to the real contract** (no-Content-Length path, split JPEG, 401, 404 text/plain, relative + 127.0.0.1 URLs) — Pitfall 6.
- [ ] RED scaffolds for the test classes above.
- [ ] No framework install needed (JUnit/coroutines-test/Compose-test already present).

## Security Domain

> `security_enforcement: true` in `.planning/config.json` — section required.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Reuse `X-Api-Key` for the Moonraker `/server/webcams/list` read where keyed (`MoonrakerAuth.xApiKeyHeader()`). The webcam HTTP server's auth (nginx Basic / in-URL token) is SEPARATE — preserve in-URL tokens verbatim; do not invent a credentials UI (D-05). |
| V3 Session Management | no | Stateless GETs; no session. |
| V4 Access Control | n/a | Read-only; no webcam write ops (catalog `reference_only`). |
| V5 Input Validation | yes | Treat all `/server/webcams/list` fields as untrusted: `service`/`name` may be empty/odd-unicode (E3 has `microsoft®_…`); URLs may be relative/localhost/malformed → validate + resolve, fail safe to rung-3 on a parse failure. Bound JPEG part size (reject absurd `Content-Length` to avoid an OOM-by-hostile-frame). |
| V6 Cryptography | no | Plaintext LAN (project posture, CONN-05). No crypto hand-rolled. |
| V7 Secret Logging | yes | **Do NOT log the resolved snapshot/stream URL** — the E3 URL embeds a token (`?token=…`). Mirror `MoonrakerAuth.redactWsUrl`: add a `redact(?token=…)` before any URL reaches a log/crash surface. |

### Known Threat Patterns for {Android MJPEG-over-HTTP on LAN}
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Hostile/oversized JPEG frame (OOM) | Denial of Service | Cap `Content-Length` / accumulated part size to a sane ceiling; drop the part if exceeded; `inSampleSize` downscale always. |
| Token leak via logged URL | Information Disclosure | Redact `?token=…` in any surfaced URL (V7); never log resolved webcam URLs. |
| Wedged connection holding the FGS awake | DoS / battery | Page-visible-only loops (D-13) + finite snapshot read timeout + backoff cap (D-12); cancel on background. |
| Malformed `/server/webcams/list` (parse crash) | Tampering | Tolerant kotlinx.serialization (`ignoreUnknownKeys`, nullable fields) — same discipline as the rest of the spine; fail to greyed/empty, never crash. |
| 401/403 spin-retry (self-DoS the printer host) | DoS | Treat auth failures as terminal-for-cam (A4), not transient (protects the weak SBC host — cadence-contract spirit). |

## Sources

### Primary (HIGH confidence)
- **Live printer probes (2026-06-03)** — `GET /server/webcams/list`, stream/snapshot `HEAD/GET` Content-Type + auth checks on E5 (192.168.1.120) and E3 (192.168.1.121). The single most load-bearing finding (webrtc-mediamtx + per-host snapshot auth).
- **Codebase** — `net/MoonrakerSocket.kt`, `service/MoonrakerService.kt`, `auth/MoonrakerAuth.kt`, `config/ConnectionConfig.kt`, `render/GraphViewHost.kt`, `theme/views/ThemeableView.kt`, `ui/files/FileThumbnailLoader.kt`, `ui/shell/AppDrawer.kt`, `ui/route/TopRoute.kt`, `gradle/libs.versions.toml`, `docs/request-cadence-contract.md`, `docs/moonraker-capabilities.md`, `docs/commands/{catalog,printer-matrix}.json`.
- **moonraker.readthedocs.io/en/latest/external_api/webcams/** — `server.webcams.list` field set; URLs may be relative.
- **developer.android.com/topic/performance/graphics/manage-memory + /load-large-bitmaps** — `inBitmap`+`inSampleSize`+`inMutable` reuse rules (API 19+).
- **ravens-perch repo** (`/mnt/e/claude/personal/github/ravens-perch/{CLAUDE,README}.md`) — MediaMTX/nginx/snapshot/Basic-Auth model, `service` defaults, URL table.

### Secondary (MEDIUM confidence)
- **gb-crowsnest cam-section.md** — ustreamer MJPEG endpoints + camera-streamer (h264/WebRTC) split; standard MJPEG Content-Type.

### Tertiary (LOW confidence)
- WebSearch on `inBitmap`/MJPEG parsing — corroborates the platform docs; no library adopted.

## Metadata

**Confidence breakdown:**
- Enumeration shape + per-printer reality: **HIGH** — live-probed both printers today.
- Decode mechanics (`BitmapFactory`/`inBitmap`/`inSampleSize`/Okio): **HIGH** — platform docs + codebase precedent.
- Reuse map (OkHttp/AndroidView/DataStore/drawer): **HIGH** — read the actual files.
- Exact fps cap + `inSampleSize` step + ARGB_8888-vs-565: **MEDIUM/ASSUMED** — must be pinned on flox (A1/A2).
- MJPEG live-path acceptance: **MEDIUM** — no live MJPEG cam on this project's printers; unit-test-driven (Open Q1).

**Research date:** 2026-06-03
**Valid until:** ~2026-07-03 (stable; the live per-printer auth/service config could change if the owner reconfigures ravens-parch/crowsnest — re-probe before the live UAT).
