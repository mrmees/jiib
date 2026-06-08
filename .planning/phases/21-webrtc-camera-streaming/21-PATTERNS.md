# Phase 21: Native H.264 Camera Streaming (MediaMTX) - Pattern Map

**Mapped:** 2026-06-08
**Files analyzed:** 9 (5 new / 3 modified / 1 throwaway)
**Analogs found:** 9 / 9 (every new file has a direct in-repo analog — this phase is an *addition above* the Phase-10 ladder, not a rewrite)

> **Core framing (from CONTEXT/RESEARCH):** The new work is a **Media3/ExoPlayer H.264 rung** added as the
> new TOP rung above the existing Phase-10 MJPEG → Snapshot → Unsupported ladder. Almost every new file
> mirrors an existing Phase-10 webcam file 1:1. The phase's *real* novel work is (a) the **throwaway
> on-device spike** (D-02, scratch artifact), (b) the **URL-derive transform** (`:8889`→`:8554`/`:8888`,
> parse-path-from-stream_url landmine), and (c) the **main-thread ExoPlayer confinement** that DIVERGES
> from the existing IO-dispatched feed. Everything else is "plug a new `WebcamFeed` into the existing
> host-tested holder."

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `state/WebcamModels.kt` (**MODIFY**: add `Rung.H264`, `NativeTransport`, extra_data tag helper) | model | transform | self (`Rung` enum + `rungFor`) | exact (extend in place) |
| `net/WebcamUrl.kt` (**MODIFY**: add `deriveNativeStreamUrl()`) | utility | transform | self (`resolveWebcamUrl`) | exact (extend in place) |
| `ui/webcam/Media3Feed.kt` (**NEW**) | service / feed | streaming | `bitmapFeed` in `WebcamHolder.kt` (the `WebcamFeed<T>` impl) | exact (same seam) |
| `ui/webcam/WebcamHolder.kt` (**MODIFY**: H264 rung select branch; main-thread feed marshaling) | service | streaming/event-driven | self (the `drive()` reconnect machine + `FeedOutcome`) | exact (extend in place) |
| `render/Media3SurfaceHost.kt` (**NEW**) | provider / interop | render | `render/WebcamViewHost.kt` (`AndroidView` host) | exact (same factory/update shape) |
| `net/WebcamProbe.kt` (selector heuristic — likely a NEW pure `selectsH264Rung()` fn, NOT inside the GET probe) | utility | request-response | `rungFor` (pure selector) + `WebcamProbe.probe` | role-match (heuristic ≠ Content-Type probe — see Pattern 2) |
| `state/WebcamModels.kt` / `ui/webcam/WebcamScreen.kt` (**MOSTLY UNTOUCHED** — inherit) | screen | — | self | exact (no change beyond DeadEnd-card copy if any) |
| `ui/webcam/WebcamPrefs.kt` (**UNTOUCHED** — inherited) | store | CRUD | self | exact (zero change; H.264 cam ids round-trip the same) |
| `spike/SpikeActivity.kt` (**THROWAWAY**, deleted after D-02) | scratch / Activity | streaming | — (no shipped analog; a bare Activity, deliberately NOT the rung) | scratch artifact |
| `app/build.gradle.kts` + `gradle/libs.versions.toml` (**MODIFY**: media3 deps) | config | — | the CameraX wiring block (`build.gradle.kts:144-151`) | role-match |

## Pattern Assignments

### `state/WebcamModels.kt` — add `Rung.H264` + `NativeTransport` + extra_data helper (MODIFY, model/transform)

**Analog:** self — the existing `Rung` enum (lines 59-68) and tolerant `Webcam` model (lines 26-53).

**How a rung is added** — the existing enum carries a numeric `tier` mirroring the D-01 rung numbers. D-09
makes H.264 the new **top** rung. The existing tiers are MJPEG=1, Snapshot=2, Unsupported=3; H.264 must be
the lowest tier number (best). Existing shape to extend (lines 59-68):
```kotlin
enum class Rung(val tier: Int) {
    Mjpeg(1),
    Snapshot(2),
    Unsupported(3),
}
// → add (tier 0 = new top/preferred rung, D-09):
//   H264(0),   // decoded natively by Media3 over RTSP/HLS; selected by heuristic, verified by decoder
```
> ⚠ Audit every `when (rung)` / `rungFor` consumer for exhaustiveness when the case is added — the compiler
> flags non-exhaustive `when`, but `rungFor` (lines 86-103) is Content-Type-only and **must NOT return
> `H264`** (RTSP/WHEP are a different protocol — D-10; the heuristic selects H.264, not the GET probe).

**`extra_data` read is already a field lookup** — the model already carries `extraData` (line 40):
```kotlin
@SerialName("extra_data") val extraData: JsonObject = JsonObject(emptyMap()),
```
The D-12 explicit-tag read (`extra_data.<ravens_perch_native_rtsp>`) is a `JsonObject` key lookup against
this existing field — it lies dormant until ravens-perch ships the tag (D-14). Mirror the tolerant
never-fabricate decode discipline (class KDoc lines 11-24): a missing/garbage tag → `null` → fall to derive.

**Add a `NativeTransport` enum** (Rtsp / Hls) alongside `Rung` — the derive transform's parameter (see
`WebcamUrl` below). Keep it in this state file next to `Rung` (the model + its consumers in one place,
per the file's existing `ResolvedWebcam` precedent, lines 133-138).

---

### `net/WebcamUrl.kt` — add `deriveNativeStreamUrl()` (MODIFY, utility/transform)

**Analog:** self — `resolveWebcamUrl` (lines 39-56). This file is the ONE sanctioned home for webcam URL
transforms, all `HttpUrl`-based, all pure (no Android, no logging side-effects — class KDoc line 26).

**Imports already present** (lines 1-5) — reuse verbatim, no new import needed:
```kotlin
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import works.mees.dinghy.config.ConnectionConfig
```

**The `HttpUrl`-parse-never-string-concat discipline** to copy (lines 42-55) — the existing resolver shows
the exact idiom (`newBuilder()` to swap one component while preserving the rest):
```kotlin
val base = cfg.httpBase.toHttpUrlOrNull() ?: cfg.httpBase.toHttpUrl()
val resolved = base.resolve(raw.trim()) ?: return null
if (resolved.host in LOOPBACK_HOSTS) {
    return resolved.newBuilder().host(cfg.host).build().toString()   // swap ONE component, preserve rest
}
```

**The derive transform to add** (RESEARCH § URL Resolution gives the concrete shape; CAM-13). ⚠ **LANDMINE
(RESEARCH-flagged):** parse the path **out of the existing `stream_url`**, NEVER reconstruct from the
webcam `name` — `name` (= friendly_name) ≠ MediaMTX `path` (= camera_id). Both go through
`.replace(' ','_').lower()` but are *different identifiers*:
```kotlin
// EXTEND WebcamUrl.kt — pure, HttpUrl-based (mirrors resolveWebcamUrl). null when not derivable → rung falls through.
fun deriveNativeStreamUrl(webrtcStreamUrl: String?, transport: NativeTransport): String? {
    val url = webrtcStreamUrl?.trim()?.toHttpUrlOrNull() ?: return null
    val path = url.encodedPath.trim('/')          // MediaMTX path = the single segment from stream_url
    if (path.isBlank()) return null
    return when (transport) {
        NativeTransport.Rtsp -> "rtsp://${url.host}:8554/$path"      // port 8889→8554, scheme http→rtsp
        NativeTransport.Hls  -> "http://${url.host}:8888/$path/"     // port 8889→8888, keep trailing slash
    }
}
```
Port constants (8554/8888/8889) are VERIFIED against ravens-perch `stream_manager.py`. The explicit-tag
read (D-12) is a **prefer-if-present** layer ABOVE this derive (read `extraData`, fall to derive when absent).

**Security V5/V7 reuse (mandatory):** route any surfaced/logged derived URL through the existing
`surfaceWebcamUrl(url)` (lines 75-76) — even though RTSP/HLS are tokenless, the snapshot fallback URL is
not, and a mixed diagnostic must not leak it. A garbage `stream_url` → `toHttpUrlOrNull()` null → `null`
return → rung falls through (the existing fail-safe pattern, V5).

---

### `ui/webcam/Media3Feed.kt` — the H.264 `WebcamFeed` binding (NEW, service/streaming)

**Analog:** `bitmapFeed(...)` in `WebcamHolder.kt` (lines 331-384) — the existing production `WebcamFeed<Bitmap>`
that the holder drives. The new `Media3Feed` is a SIBLING binding plugged into the **same** `WebcamFeed<T>`
seam (lines 316-318), so the holder's host-tested reconnect machine drives it unchanged.

**The `WebcamFeed` interface to implement** (lines 316-318) — the contract is "run the cam, push frames,
return a `FeedOutcome`":
```kotlin
fun interface WebcamFeed<T> {
    suspend fun run(cam: ResolvedWebcam, onFrame: (T) -> Unit): FeedOutcome
}
```

**Outcome mapping pattern (Pattern 3 reuse — don't reinvent the state machine).** The existing
`bitmapFeed` maps decode lifecycle → `FeedOutcome.{Transient, Terminal, Cancelled}` (lines 360-383); the
Media3 feed maps `PlaybackException` the same way (RESEARCH § Code Examples):
```kotlin
player.addListener(object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
        val outcome = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> FeedOutcome.Transient
            else -> FeedOutcome.Terminal   // decoder-init / unsupported → decoder VERIFIES (D-10) → next rung
        }
        onOutcome(outcome)
    }
})
```

**RTSP-forced-TCP player construction** (CAM-16, RESEARCH § Pattern 1 — MediaMTX RTSP is TCP-only):
```kotlin
val player = ExoPlayer.Builder(context).build()
val source = RtspMediaSource.Factory()
    .setForceUseRtpTcp()        // MediaMTX is TCP-only — force interleaved RTP/TCP
    .setTimeoutMs(8000)
    .createMediaSource(MediaItem.fromUri(rtspUri))
player.setMediaSource(source)
player.setVideoSurfaceView(surfaceView)   // raw SurfaceView, NO PlayerView
player.playWhenReady = true
player.prepare()
```

> ⚠ **CRITICAL DIVERGENCE (flag in plan, RESEARCH Anti-Pattern + Pitfall 2):** `bitmapFeed` runs blocking
> decode on `Dispatchers.IO` (holder passes `driverContext = Dispatchers.IO`, `webcamBitmapHolder` line
> 408). **ExoPlayer is single-threaded by contract — ALL player ops (`build`/`prepare`/`setVideoSurfaceView`/
> `release`) MUST run on `Dispatchers.Main`.** The `Media3Feed` must marshal player calls back to main
> (`withContext(Dispatchers.Main)` or a main `Handler`) while still being driven by the holder's
> IO-dispatched driver loop. This is the single biggest structural difference from the existing feed.

**Host-testability seam preserved:** the holder's state machine stays host-provable via the injected
`WebcamFeed` (lines 53-59 KDoc) — the real player binding is an on-device property exactly as
`bitmapFeed` is (line 327 KDoc: "its own end-to-end behavior is an on-device property"). Unit-test the
*outcome mapping* (a fake listener), not the player.

---

### `ui/webcam/WebcamHolder.kt` — H.264 rung select + main-thread marshaling (MODIFY, service/streaming)

**Analog:** self — the `drive()` reconnect/backoff/dead-end state machine (lines 205-242) and the
production wiring (`bitmapFeed`/`webcamBitmapHolder`, lines 331-409).

**REUSE VERBATIM — do NOT touch (RESEARCH § Don't Hand-Roll):** the `drive()` loop (lines 205-242), the
`FeedOutcome` enum (lines 297-307), the WR-01 idempotent `cancel()` (lines 172-179), the
`start()`/`stop()` lifecycle (lines 137-165), the selected-cam resolution (lines 245-258). The H.264 rung
is a new `WebcamFeed`, so the whole reconnect/dead-end/leak-free machinery is reused.

**The ExoPlayer lifecycle contract to mirror** (CAM-14, RESEARCH table maps each phase to existing code):
the existing `cancel()` (lines 172-179) is the leak-free idempotent teardown precedent — the codec +
player MUST `setVideoSurfaceView(null)` THEN `player.release()`, idempotently, exactly mirroring the
holder's "complete, leak-free teardown" discipline (class KDoc lines 47-65, the frozen-feed-after-restart
history). On screen exit (existing `DisposableEffect onDispose { holder.stop() }`, WebcamScreen lines
81-84) the player must release.

**Where the H.264 select slots in:** `resolveSelectedCam()` (lines 245-258) returns a `ResolvedWebcam`
with a provisional `Rung`; today it's hardcoded `Rung.Mjpeg` (line 257) because "the feed resolves the
rung itself (probe)". For H.264, the **heuristic** `selectsH264Rung(cam)` (see WebcamProbe below) decides
whether the resolved cam takes the `Rung.H264` branch → `Media3Feed`, else falls to the existing
`bitmapFeed` probe path. Add a `media3Feed`-vs-`bitmapFeed` selection (or a composed feed that tries H.264
first, falls through on `Terminal` to the MJPEG/snapshot feed — the D-09 ladder order).

**Production-wiring analog** (`webcamBitmapHolder`, lines 391-409) — the new holder needs the same shape
but a `Context` threaded in for `ExoPlayer.Builder(context)` and main-thread marshaling; the IO
`driverContext` STAYS for the orchestration loop (lines 406-408), only the player ops marshal to main.

---

### `render/Media3SurfaceHost.kt` — `AndroidView<SurfaceView>` host (NEW, provider/render)

**Analog:** `render/WebcamViewHost.kt` (lines 38-68) — the EXACT factory-once/update-push `AndroidView`
interop shape (ADR-0001 hybrid). The ExoPlayer `SurfaceView` hosts the same way.

**The factory-once/update-push contract to copy** (lines 58-67):
```kotlin
AndroidView(
    factory = { ctx -> WebcamView(ctx) },   // created ONCE; never recreated on a theme/frame/chrome change
    update = { view ->
        view.applyTokens(tokens)
        view.setFrame(frame)
        view.setChrome(...)
    },
    modifier = modifier,
)
```
For Media3: `factory = { ctx -> SurfaceView(ctx) }`, attach the player's surface in factory/update, and
**add the documented Compose-AndroidView player-leak guard** (RESEARCH § Surface Hosting) — `onReset`/
`onRelease` setting `player.setVideoSurfaceView(null)`. This is the `onDispose` release contract the
existing host implicitly satisfies via `WebcamView`'s allocation-free lifecycle.

**Preview short-circuit to copy** (lines 54-57) — the existing host stands in under `@Preview` (no live
frame); the Media3 host must do the same (`LocalInspectionMode.current → PreviewPlaceholderBox`), since
a live RTSP feed never renders under `@Preview`:
```kotlin
if (LocalInspectionMode.current) {
    PreviewPlaceholderBox(label = "Webcam (live on device)", modifier = modifier)
    return
}
```

> ⚠ **SurfaceView clipping limitation (RESEARCH Pitfall 5, Open Question 3):** SurfaceView punches through
> the view hierarchy — the existing `WebcamView` rounded-cutout clip (`clipPath`, WebcamView lines 245-246)
> WON'T apply to a SurfaceView. Either draw the chrome + rounded mask as a **sibling overlay above** the
> SurfaceView, or accept square corners for the H.264 rung. **OWNER AESTHETIC CALL — do NOT silently pick
> (ties to the [[dinghy-never-pick-icons-ask]] discipline).** Confirm in the spike review.

---

### `net/WebcamProbe.kt` area — `selectsH264Rung()` heuristic (likely NEW pure fn, utility/request-response)

**Analog:** `rungFor` (WebcamModels.kt lines 86-103, pure selector) for the *pure-function* shape;
`WebcamProbe.probe` (lines 94-149) for the "what a probe does" contrast.

**Pattern 2 — heuristic SELECTS, decoder VERIFIES (D-10, preserves T-10-06).** This is the key
reconciliation: the existing `rungFor`/`WebcamProbe` make the **HTTP Content-Type the sole authority** and
NEVER consult `service` (WebcamModels.kt lines 70-85, WebcamProbe lines 11-19). RTSP/WHEP can't be
Content-Type-probed (different protocol / a POST), so the H.264 rung uses a `service`/scheme **hint** to
*select*, and the ExoPlayer decoder *verifies* (errors → fall through). A hostile/wrong `service` can
never route bytes into a fatal mis-decode — a bad guess just falls to MJPEG/Snapshot. The pure heuristic
(RESEARCH § Pattern 2):
```kotlin
// Heuristic SELECT — a hint only, NEVER the authority (T-10-06 preserved by decoder-verify)
fun selectsH264Rung(cam: Webcam): Boolean =
    cam.service.startsWith("webrtc", ignoreCase = true) ||      // de-facto "webrtc-mediamtx"
    cam.streamUrl?.startsWith("rtsp://", ignoreCase = true) == true
```
> Keep this **pure + host-testable** like `rungFor` (no Android, no I/O) so `RungSelectTest` (Wave 0)
> covers it. It does NOT belong inside `WebcamProbe.probe` (that's the GET Content-Type authority for
> MJPEG/Snapshot and must stay `service`-blind). Place it as a sibling pure fn (WebcamModels.kt next to
> `rungFor`, or a small new file).

---

### `ui/webcam/WebcamScreen.kt` + `WebcamPrefs.kt` — INHERITED (mostly/entirely untouched)

**Analog:** self. `WebcamScreen` (lines 71-134) keeps its Focus/Field/Gutter scaffold, `DisposableEffect`
lifecycle (lines 81-84), and cam picker — the H.264 rung renders through the same `FeedFocus` path
(swap `WebcamViewHost` → `Media3SurfaceHost` only for the H.264 rung, or host both). `WebcamPrefs`
(entire file) is **zero-change**: `preferred_cam_<profileId>` (line 69) round-trips an H.264 cam id as an
opaque string identically. WR-03 null-key edge is **verified in UAT (D-20)**, not re-coded.

---

### `spike/SpikeActivity.kt` — THROWAWAY (scratch artifact, NOT a shipped surface)

**Analog:** NONE intentionally. This is **throwaway code** (D-02): a bare temporary Activity that builds
an `ExoPlayer` + raw `SurfaceView`, plays RTSP and HLS, logs the decoder name (`MediaCodecList`), and is
the D-02 latency/CPU/APK-delta harness. **Deleted after the spike reports.** Map it as its own scratch
artifact — it is explicitly NOT the shipped rung (`Media3Feed`/`Media3SurfaceHost` are the shipped
surfaces). Do NOT build it against the rung-ladder analogs; it bypasses the holder/`WebcamFeed` entirely.
It exists to settle the three spike-gated unknowns (latency, OMX.qcom hardware decode, SPS/PPS-in-fmtp).

---

### `app/build.gradle.kts` + `gradle/libs.versions.toml` — media3 deps (MODIFY, config)

**Analog:** the CameraX wiring block (`build.gradle.kts` lines 144-151) — a prior "add a floor-checked
native-capability dep" precedent:
```kotlin
// --- Camera + QR decode (SPOOL-05/06, Phase 11) ---
// CameraX 1.5.0 (floor == 23, PROVEN by verifyMinSdkRelease) ...
implementation(libs.androidx.camera.core)
implementation(libs.androidx.camera.camera2)
...
```
Mirror this exactly for media3 (RESEARCH § Standard Stack): add `media3 = "1.10.1"` to the version
catalog (NOT 1.10.0 — #3121 fix), declare `media3-exoplayer` + `media3-exoplayer-rtsp` (+ `-hls` for the
spike), then **run `verifyMinSdkRelease`** (the merged-manifest floor guard, the project's central trap —
build.gradle.kts:8/32, libs.versions.toml comments). The APK is `armeabi-v7a`-ONLY (splits block lines
50-54); media3 is pure Java/Kotlin (platform MediaCodec, no bundled `.so`), so the cost is small.

## Shared Patterns

### Security V7 — redacted URL surface (apply to ALL new URL-handling code)
**Source:** `net/WebcamUrl.kt` lines 63-76 (`redactWebcamUrl` / `surfaceWebcamUrl`).
**Apply to:** `Media3Feed`, `deriveNativeStreamUrl`, any H.264 diagnostic.
```kotlin
fun surfaceWebcamUrl(url: String?): String =
    if (url.isNullOrBlank()) "" else redactWebcamUrl(url)   // ?token= → <redacted>; the ONLY sanctioned surface
```
RTSP/HLS are tokenless, but the snapshot fallback URL is not — a mixed log must not leak it.

### Tolerant, never-fabricate model decode (apply to the extra_data tag read + derive)
**Source:** `state/WebcamModels.kt` lines 11-24 (class KDoc), `parseWebcamsList` lines 113-123.
**Apply to:** the D-12 explicit-tag read, the derive transform.
Every `Webcam` field is untrusted; a malformed/missing value → `null`/default → rung falls through to a
greyed tile or the next rung. NEVER throw, NEVER fabricate a URL the printer didn't imply.

### Idempotent leak-free teardown (apply to the ExoPlayer lifecycle)
**Source:** `ui/webcam/WebcamHolder.kt` lines 172-179 (`cancel()`), the WR-01 history (class KDoc lines 47-65).
**Apply to:** `Media3Feed` / the H.264 holder branch.
```kotlin
fun cancel() {
    cancelled = true
    holderScope.cancel()   // idempotent — cancelling an already-cancelled scope is a no-op
    driver = null
}
```
For the player: `setVideoSurfaceView(null)` THEN `release()`, idempotent. A held MediaCodec keeps the FGS
hot and starves the 2 GB device — the project has explicit frozen-feed-after-restart history here.

### AndroidView factory-once / update-push (apply to the surface host)
**Source:** `render/WebcamViewHost.kt` lines 58-67 + the preview short-circuit lines 54-57.
**Apply to:** `Media3SurfaceHost`. Factory runs once; `update` pushes state into the same instance; add
the `onReset`/`onRelease` null-surface leak guard; stand in under `@Preview`.

### Injected-feed host-testability (apply to keep the state machine green)
**Source:** `ui/webcam/WebcamHolder.kt` lines 53-59 (generic `<T>` over an injected `WebcamFeed`), the
`RungSelectTest`/`WebcamReconnectStateTest` precedent.
**Apply to:** the H.264 rung. Unit-test the *pure* logic (rung select, URL derive, outcome mapping); the
real player is an on-device gate (the [[dinghy-display-mock-vs-reality]] lesson — green units ≠ passing
camera; the load-bearing criteria are the spike + on-device UAT, D-08/D-20).

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `spike/SpikeActivity.kt` | scratch/Activity | streaming | Throwaway by design (D-02) — bypasses the holder/`WebcamFeed`; not a shipped surface; deleted after the spike. Use RESEARCH § Throwaway Spike directly, not a rung analog. |

> Everything *shipped* has a direct in-repo analog — this phase is an addition above the Phase-10 ladder,
> not a new module. The only genuinely-new shipped logic is the URL-derive transform (no prior
> port-swap precedent — but `resolveWebcamUrl`'s `HttpUrl.newBuilder()` idiom is the pattern to copy) and
> the main-thread ExoPlayer marshaling (a deliberate DIVERGENCE from the IO-dispatched `bitmapFeed`).

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/{state,net,ui/webcam,render,di}/`,
`app/build.gradle.kts`, `gradle/libs.versions.toml`, `DinghyApp.kt`.
**Files scanned:** WebcamModels.kt, WebcamUrl.kt, WebcamProbe.kt, WebcamHolder.kt, WebcamView.kt,
WebcamViewHost.kt, WebcamScreen.kt, WebcamPrefs.kt, AppContainer.kt (grep), build.gradle.kts (deps block).
**Pattern extraction date:** 2026-06-08
