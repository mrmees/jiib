---
phase: 10-webcam-streaming
reviewed: 2026-06-04T04:39:16Z
depth: standard
files_reviewed: 21
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/net/SnapshotPoller.kt
  - app/src/main/java/works/mees/dinghy/net/MjpegStreamDecoder.kt
  - app/src/main/java/works/mees/dinghy/net/WebcamProbe.kt
  - app/src/main/java/works/mees/dinghy/net/WebcamClients.kt
  - app/src/main/java/works/mees/dinghy/net/WebcamUrl.kt
  - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
  - app/src/main/java/works/mees/dinghy/net/Backoff.kt
  - app/src/main/java/works/mees/dinghy/state/WebcamModels.kt
  - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt
  - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamPrefs.kt
  - app/src/main/java/works/mees/dinghy/webcam/WebcamsHolder.kt
  - app/src/main/java/works/mees/dinghy/render/WebcamView.kt
  - app/src/main/java/works/mees/dinghy/render/WebcamViewHost.kt
  - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
  - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
  - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
  - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
  - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/src/main/java/works/mees/dinghy/DinghyApp.kt
findings:
  critical: 2
  warning: 6
  info: 5
  total: 13
status: issues_found
resolved:
  - CR-01  # holder child scope cancels the orphaned webcams collector (a98aa15)
  - CR-02  # redactWebcamUrl wired as the only URL-surface path via surfaceWebcamUrl (7859014)
  - WR-01  # join prior driver before relaunch — teardown-before-start (e0fd181)
  - WR-03  # double-buffer MJPEG frames across the decode/UI handoff (b824d5a)
  - WR-04  # measure real MJPEG frame bounds for inSampleSize (eb09aa3)
deferred:
  - WR-02  # holder re-keyed on view px (rotation tears down the feed) — user-deferred
  - WR-05  # unenforced bitmap ownership / recycle race — user-deferred (tied to WR-03 decision)
  - WR-06  # MJPEG boundary-substring / duplicate-Content-Length hardening — user-deferred
---

# Phase 10: Code Review Report

**Reviewed:** 2026-06-04T04:39:16Z
**Depth:** standard
**Files Reviewed:** 21
**Status:** issues_found

## Summary

Reviewed the Phase 10 native-Android webcam feature: MJPEG/snapshot decode services,
URL resolution/redaction, the orchestration holder + reconnect state machine, the
classic-Views render surface, and the spine/shell integration. The decode-discipline
and security posture (URL redaction, OOM caps, tolerant parsing, terminal-401 handling)
are mostly sound and the two previously-found on-device bugs (full-res snapshot OOM,
main-thread probe) are correctly fixed.

The serious problems are in **coroutine lifecycle / teardown** — exactly the area flagged
as highest-risk (WR-01, the project's frozen-feed history). The holder leaks long-lived
collectors on the shared composition scope (every holder re-key accumulates an orphaned
`webcams.collect`), and a benign-looking `selectCam()` re-entrancy can cancel the driver
from a *different* coroutine while the old driver's `coroutineScope`/HTTP body teardown is
still in flight. There is also a real **token-leak gap**: `redactWebcamUrl` exists but is
never actually called on any error/log path in the production code under review — the
security control is dead code today.

## Critical Issues

### CR-01: Orphaned `webcams.collect` collector leaks on every holder re-key — `cancel()`/`stop()` never stop it

> **RESOLVED** (commit `a98aa15`): WebcamHolder now owns its own child scope
> (`scope.coroutineContext + SupervisorJob(scope's Job)`); the init `webcams.collect`
> collector, the driver, and the `setPreferredCam` prefs writes all launch on it, and
> `cancel()` cancels that scope so a re-key tears everything down together — no orphaned
> collector. Regression test: `WebcamReconnectStateTest.cancel_stopsTheInitWebcamsCollector_noOrphanOnReKey`.

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt:100-109`, `140-144`
**Issue:**
The holder's `init` block launches a permanent collector on the *injected* `scope`:

```kotlin
init {
    scope.launch {
        webcams.collect { cams -> _vm.value = _vm.value.copy(cams = cams, multiCam = cams.size > 1) }
    }
}
```

`cancel()` and `stop()` only cancel `driver`; they do **not** cancel this `init` collector
(nor the `scope.launch { webcamPrefs.setPreferredCam(...) }` in `selectCam`). In `AppShell`
the injected `scope` is a single `rememberCoroutineScope()` (`AppShell.kt:116`) shared across
the entire composition and **all** holders. `webcamHolder` is re-keyed on
`remember(store, activeCfg.host, viewWidthPx, viewHeightPx)` (`AppShell.kt:190`), so a printer
swap, a config change, OR a configuration/rotation change that moves `viewWidthPx`/`viewHeightPx`
constructs a brand-new `WebcamHolder` whose `init` adds *another* live `webcams.collect` on the
same long-lived scope. The old holder's `cancel()` (fired by `DisposableEffect`, `AppShell.kt:206`)
leaves its collector running. These accumulate for the life of the composition — a structured-
concurrency violation and the exact "incomplete teardown" class WR-01 is meant to prevent. Each
orphaned collector still writes to its own dead `_vm` (harmless output) but never terminates,
holding references and the upstream subscription.

**Fix:** Give the holder its own child scope and cancel it in `cancel()`, so *all* launched work
(the init collector, the driver, the prefs writes) tears down together:

```kotlin
private val holderScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
// use holderScope.launch everywhere instead of scope.launch
fun cancel() {
    cancelled = true
    holderScope.cancel()   // cancels init collector + driver + pending prefs writes
    driver = null
}
```

(Alternatively keep a `Job` handle for the init collector and cancel it in `cancel()`.) Mirror the
MoveHolder lifecycle precedent the doc claims to follow.

### CR-02: `redactWebcamUrl` is never invoked — the V7 token-redaction control is dead code

> **RESOLVED** (commit `7859014`): added `surfaceWebcamUrl()` as the ONE sanctioned path from a
> webcam URL to any surfaced/diagnostic string (delegates to `redactWebcamUrl`), and wired it into
> `WebcamProbe`'s transport-failure path — a new pre-redacted `ProbeResult.Unsupported.reason`
> breadcrumb is the only `ProbeResult` field that may trace a URL, and it is built exclusively through
> `surfaceWebcamUrl`. Regression test:
> `WebcamProbeTest.transportFailure_diagnosticReason_isProducedThroughSurfaceRedactor_neverRawToken`
> asserts the surfaced reason equals `surfaceWebcamUrl(url)` and never contains the raw token.

**File:** `app/src/main/java/works/mees/dinghy/net/WebcamUrl.kt:63-64` (defined); `WebcamProbe.kt`, `SnapshotPoller.kt`, `WebcamHolder.kt`, `bitmapFeed` (callers — none)
**Issue:**
Security V7 / T-10-05 requires that any webcam URL (which embeds `?token=…` for the E3 snapshot)
reaching a log/crash/error surface pass through `redactWebcamUrl`. `redactWebcamUrl` is defined
and well-formed, but a grep of the phase source shows **zero call sites** in production code. The
classes correctly "log nothing in the clear" *by logging nothing at all today* — which means the
control is unenforced rather than enforced: the moment anyone adds a diagnostic log (or a thrown
message carrying the URL) the raw token leaks. More concretely, the resolved token-bearing URL is
passed as a plain `String` into `Request.Builder().url(url)` in `SnapshotPoller.fetchOne`
(`SnapshotPoller.kt:124`) and `WebcamProbe.probe` (`WebcamProbe.kt:90`); any OkHttp interceptor,
crash with the request in the stack, or future `Log.e(..., url)` exposes it unredacted. The
redactor being present but uncalled is a latent vulnerability, not a satisfied requirement.

**Fix:** Either (a) wire `redactWebcamUrl` into the one place a URL can surface today and add a
unit test asserting it is applied, or (b) if truly nothing logs URLs this phase, add a lint/test
guard (e.g. a test that fails if any `Log.*`/exception in the webcam package references a raw URL
var) so the control can't silently rot. At minimum, document the enforced invariant with an
assertion rather than a comment. Recommended concrete step:

```kotlin
// in any catch that builds a user/diagnostic message:
catch (e: IOException) {
    // if you ever surface the URL: redactWebcamUrl(url)
    Fetch.Transient
}
```
and add `WebcamUrlRedactionTest` proving `redactWebcamUrl("http://h/s?token=abc&x=1")` → `…token=<redacted>&x=1`.

## Warnings

### WR-01: `selectCam()`/`start()` cancel the driver from a foreign coroutine, racing the old driver's body/scope teardown

> **RESOLVED** (commit `e0fd181`): `start()` now hands the previous driver to the new one and
> `cancelAndJoin()`s it FIRST, so the old loop fully unwinds (body closed via `use`) before `drive()`
> re-resolves + re-opens — teardown is serialized with the next start, no overlapping streams, no racy
> `_vm` writes. Regression test:
> `WebcamReconnectStateTest.selectCam_joinsPriorDriver_teardownBeforeRelaunch_noStreamOverlap`
> asserts close-before-open ordering and at most one stream open at a time.

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt:116-124`, `147-151`, `157-164`
**Issue:**
`start()` does `driver?.cancel(); driver = scope.launch(driverContext){ drive() }`. `selectCam`
and `cycleCam` call `start()` from the main `scope` while the previous `driver` runs on
`Dispatchers.IO`. `Job.cancel()` is asynchronous — it requests cancellation but returns immediately;
the new driver is launched before the old one has unwound. The old driver may be mid-`coroutineScope{}`
in `bitmapFeed` with an open `ResponseBody` (`probe.body.use { … }`, `WebcamHolder.kt:320`) or an
active snapshot `poll`. Two effects: (1) for a brief window two drivers can be writing `_vm.value`
(last-writer-wins on a `MutableStateFlow` — racy mode/frame flicker, e.g. the new driver sets
`Live`/`null` at line 176 while the old one is still emitting frames); (2) rapid `cycleCam` taps
spawn drivers faster than they tear down, transiently multiplying open HTTP streams against the weak
SBC — contrary to the cadence/efficiency discipline. The teardown is *eventually* correct (the body
closes via `use`), but it is not serialized with the next start.

**Fix:** Join the old driver before starting the new one (or guard with a mutex):

```kotlin
fun start() {
    if (cancelled) return
    val previous = driver
    driver = scope.launch(driverContext) {
        previous?.cancelAndJoin()   // serialize: old loop fully unwinds (body closed) before drive()
        drive()
    }
}
```

### WR-02: Holder construction is keyed on view pixel size — a rotation tears down and rebuilds the entire feed

> **DEFERRED** (user choice, 2026-06-04): left as a documented note. The CR-01 fix removes the
> leak-amplifier half of this finding (a rotation re-key no longer leaks a collector); the remaining
> feed-drop-on-rotation behavior is deferred.

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:183-200`
**Issue:**
`viewWidthPx`/`viewHeightPx` are derived from `LocalConfiguration` (`AppShell.kt:183-184`) and are
part of the `remember(store, activeCfg.host, viewWidthPx, viewHeightPx)` key (`AppShell.kt:190`).
Every device rotation changes these dimensions, so the holder is destroyed (`cancel()`, leaking a
collector per CR-01) and rebuilt — dropping the live MJPEG/snapshot connection and re-probing from
scratch on every orientation change. For an always-on wall-tablet that may rotate, this is a visible
feed drop plus the CR-01 leak amplifier. The px values only feed the *decode downsample budget*
(per the comment at line 179-180), which does not require recreating the holder.

**Fix:** Drop `viewWidthPx`/`viewHeightPx` from the `remember` key (key only on `store` and host),
and push the px hint into the holder/feed as updatable state (e.g. a `setViewSize(w, h)` the
`update` block calls, or pass a `StateFlow<IntSize>`), so a rotation re-tunes the sample step without
killing the connection.

### WR-03: `WebcamView.setFrame` does not invalidate when handed the same bitmap instance twice

> **RESOLVED** (commit `b824d5a`): the MJPEG `bitmaps()` decoder now DOUBLE-BUFFERS — two decode
> targets (each its own reused `inBitmap`) ping-ponged per frame, so the bitmap just handed off over
> the drop-behind channel (possibly on-screen) is never the next write target, removing the torn-frame
> across the decode-thread → UI-thread handoff. `WebcamView.setFrame` also skips the invalidate on an
> identical bitmap reference, so an unrelated recomposition no longer forces a redundant re-blit.
> On-device tearing observation under fast MJPEG is the remaining human-verification step (no live MJPEG
> subject on either home printer; fixture-proven only).

**File:** `app/src/main/java/works/mees/dinghy/render/WebcamView.kt:186-189`; `bitmaps()` in `MjpegStreamDecoder.kt:224-241`
**Issue:**
The MJPEG decoder reuses **one** mutable `Bitmap` via `inBitmap` (the whole point of the allocation-free
path). `setFrame` does `this.frame = bitmap; invalidate()`. Because the decoder hands back the *same*
`Bitmap` object every frame with new pixels, `frame === bitmap` is always true after the first frame —
but `invalidate()` is still called unconditionally so a redraw happens, which is correct. The latent
bug is the opposite: `WebcamViewHost.update` calls `setFrame(frame)` on **every recomposition**
(`WebcamViewHost.kt:54`), and since the Compose `frame` value is the same reused-bitmap reference, an
unrelated recomposition (theme change, sibling state) triggers a full re-blit even with no new frame.
More importantly, because the bitmap is mutated in place on the IO thread while `onDraw` reads it on the
UI thread, there is **no synchronization** between the decoder writing pixels into the shared `inBitmap`
and the View blitting it — a torn frame (partial decode visible) is possible on the 2GB device. The
drop-behind `Channel` conflates *references*, not pixel ownership; with a single reused bitmap the
producer can be overwriting the very buffer the consumer is drawing.

**Fix:** The single-reused-`inBitmap` strategy is unsafe across the decoder-thread → UI-thread handoff.
Either (a) double-buffer (alternate between two `inBitmap` targets so the one being drawn is never the
one being written), or (b) accept a per-frame allocation for the handed-off bitmap (the snapshot path
already does fresh decodes safely). Confirm on-device under fast MJPEG whether tearing is observable;
if double-buffering, the decoder must not re-`inBitmap` the buffer currently referenced by the channel.

### WR-04: `bitmapFeed` computes the MJPEG `inSampleSize` from `(viewPx, viewPx)` as the *source* size — always yields 1

> **RESOLVED** (commit `eb09aa3`): `bitmaps()` now takes the view px and does a cheap
> `inJustDecodeBounds` measurement on the FIRST frame (the same two-pass the snapshot path uses),
> computes the fixed `inSampleSize` from the real `outWidth/outHeight` vs the view px, and holds it for
> the stream — no more full-res ARGB_8888 decode on the floor. A pre-measure frame falls back to a
> conservative non-1 sample. The actual downsample/OOM-avoidance is an on-device property (Adreno-320),
> flagged for the flox verification.

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt:310-316`
**Issue:**
```kotlin
val sample = MjpegDecodePolicy.computeInSampleSize(
    srcWidth = viewWidthPx, srcHeight = viewHeightPx,   // source == request
    reqWidth = viewWidthPx, reqHeight = viewHeightPx,
)
```
`computeInSampleSize` only grows the sample while `srcW/2 >= reqW`. Passing `src == req` makes the
loop's first guard false, so it **always returns 1**. The MJPEG decoder therefore decodes every frame
at full native camera resolution (e.g. 1920×1080 ARGB_8888 ≈ 8 MB) with no downsample — precisely the
OOM/jank trap on the 2GB Adreno-320 floor that D-06 and the snapshot fix were meant to avoid. Unlike the
snapshot path (which two-pass-measures the real frame size at `SnapshotPoller.kt:193-209`), the MJPEG
path never reads the real frame bounds, so it can't compute a correct sample here. The inline comment
("Source size is unknown until the first frame; start at 1") acknowledges the gap but the result is a
full-res decode on the primary live path.

**Fix:** Do the same `inJustDecodeBounds` first-frame measurement the snapshot path does, then feed the
*real* `outWidth/outHeight` as `srcWidth/srcHeight` against the view px to `computeInSampleSize`, and
build `bitmaps(boundary, realSample)`. Until then, do not pretend to downsample — either gate MJPEG
behind that measurement or apply a conservative non-1 floor sample like the snapshot pre-measure
fallback (`coerceAtLeast(2)`) so a full-res frame can't OOM the floor before the real size is known.

### WR-05: `WebcamView` references a recycled/foreign bitmap with no ownership guarantee

> **DEFERRED** (user choice, 2026-06-04): left as a documented note (tied to the WR-03 decision). The
> WR-03 double-buffer reduces the shared-mutation hazard; explicit detach/recycle ownership is deferred.

**File:** `app/src/main/java/works/mees/dinghy/render/WebcamView.kt:186-189`, `244-249`
**Issue:**
`setFrame` stores the bitmap and `onDraw` guards with `!bmp.isRecycled` — good defensive check. But the
doc says "The bitmap is owned/recycled by the decoder's drop-behind seam." No code in the reviewed
phase actually recycles frames, and with the single reused `inBitmap` the View holds a long-lived
reference to a bitmap the decoder keeps mutating (see WR-03). If a future teardown recycles the shared
bitmap while the View still references it, the `isRecycled` check races (checked, then recycled, then
`drawBitmap` throws). The ownership contract is asserted in comments but not enforced anywhere.

**Fix:** Make ownership explicit. On `WebcamView` detach/dispose, null the `frame` reference; ensure
whoever recycles bitmaps does so only after the View has released them (or never recycle handed-off
frames and rely on GC). Tie this to the WR-03 decision.

### WR-06: `parseContentLength` accepts a malformed multi-line header and a folded/duplicate header silently

> **DEFERRED** (user choice, 2026-06-04): left as a documented note. Hostile-server boundary-substring /
> duplicate-Content-Length hardening is unlikely against crowsnest/ustreamer and is deferred.

**File:** `app/src/main/java/works/mees/dinghy/net/MjpegStreamDecoder.kt:167-173`, `100-102`
**Issue:**
Two minor robustness gaps in the multipart scanner against a hostile/odd server:
1. The trailer check at line 100 inspects `source.buffer[0]/[1]` after `request(2)`, but reads from the
   *buffer head* which is whatever Okio has prefetched — it assumes the two bytes immediately after the
   boundary marker are the dashes. If the server emits `--boundary\r\n` for a normal part, byte[0] is
   `\r`, fine; but a server that pads the boundary line differently isn't handled, and a boundary that is
   a prefix of another token (`--boundaryX`) would mis-match at the `indexOf` step (line 95) since
   `indexOf` finds the substring anywhere. A frame whose JPEG payload coincidentally contains the
   boundary bytes would also be split early. This is the classic MJPEG boundary-substring hazard.
2. `parseContentLength` takes the *first* `Content-Length` line; a duplicated header (smuggling) is not
   rejected, just silently first-wins.

These are unlikely against crowsnest/ustreamer but the class explicitly claims hostile-input hardening
(T-10-02).

**Fix:** For (1), after locating a boundary require the following bytes to be `\r\n` (part) or `--`
(trailer) and otherwise resume scanning past this position rather than treating it as a part boundary;
this rejects a JPEG-embedded false boundary. For (2), reject (treat as no Content-Length → EOI scan) if
more than one `Content-Length` line is present. Add fixtures for a JPEG containing the boundary byte
sequence and a doubled header.

## Info

### IN-01: `parseWebcamsList` does redundant/dead double-extraction of `webcams`

**File:** `app/src/main/java/works/mees/dinghy/state/WebcamModels.kt:116-118`
**Issue:**
```kotlin
val webcams = (result as? JsonObject)?.get("webcams")
    ?: (result.jsonObject)["webcams"]
```
If `result` is a `JsonObject`, the first branch yields its `webcams` (possibly null → falls through to
`?:`). The `?:` then calls `result.jsonObject` which throws if `result` is *not* a JsonObject — but
that throw is swallowed by the outer `runCatching`. When `result` *is* a JsonObject with no `webcams`
key, the first branch is null and the second re-extracts the same (null) value. The second branch is
either redundant (same object) or a thrown-and-swallowed no-op. Net effect is correct (empty list) but
the logic is confused.

**Fix:** `val webcams = (result as? JsonObject)?.get("webcams") ?: return@runCatching emptyList()` —
single, clear extraction.

### IN-02: `Webcam.safeRotation` and `WebcamView.setTransform` duplicate the rotation-coercion, inconsistently

**File:** `app/src/main/java/works/mees/dinghy/state/WebcamModels.kt:49`; `app/src/main/java/works/mees/dinghy/render/WebcamView.kt:197-202`
**Issue:**
`safeRotation` coerces via `rotation in setOf(0, 90, 180, 270)`; `setTransform` re-coerces via
`if (rotation == 90 || rotation == 180 || rotation == 270) rotation else 0`. Two copies of the same
whitelist; the `setOf(...)` allocates a Set on every property read (minor). The screen already passes
`cam.safeRotation` (`WebcamScreen.kt:159`), so the View's re-coercion is belt-and-suspenders but the
duplication invites drift.

**Fix:** Make `safeRotation` use the same `when`/comparison form (no `setOf` allocation) and have the
View trust the already-coerced value (or call a shared `coerceRotation(int)` helper).

### IN-03: Snapshot de-dup keeps a strong reference to every changed frame's full byte array

**File:** `app/src/main/java/works/mees/dinghy/net/SnapshotPoller.kt:83`, `116-117`, `139-143`
**Issue:**
`lastBytes` (and `Fetch.Frame.bytes`) retains the full ~100KB JPEG byte array across poll iterations for
the de-dup compare. That is intended and bounded (one array), but the `Fetch.Frame` also carries the same
array to the loop, so during decode both the decoded Bitmap and the source bytes are live. Fine on this
path (2fps), just worth noting it is not free on the 2GB floor; not a defect.

**Fix:** None required; optionally hash (e.g. a cheap CRC32 of the bytes) instead of retaining the full
array if memory pressure is ever observed.

### IN-04: `resolveWebcamUrl` will throw inside `cfg.httpBase.toHttpUrl()` if httpBase is blank

**File:** `app/src/main/java/works/mees/dinghy/net/WebcamUrl.kt:43`
**Issue:**
`val base = cfg.httpBase.toHttpUrlOrNull() ?: cfg.httpBase.toHttpUrl()` — when `httpBase` is unparseable
(the idle fallback `ConnectionConfig(host = "")` in `AppShell.kt:178` produces a blank/odd httpBase),
`toHttpUrlOrNull()` returns null and `toHttpUrl()` then **throws** `IllegalArgumentException`. The
function is documented "Pure — no exceptions intended as control flow" and callers (`bitmapFeed`) do not
wrap it. In practice the holder only drives a feed when cams exist (which implies a real session), so the
blank-config path shouldn't reach here, but it's an unguarded throw on untrusted/idle input.

**Fix:** `val base = cfg.httpBase.toHttpUrlOrNull() ?: return null` — fail safe to "no usable URL"
(rung-3) instead of throwing.

### IN-05: `cycleCam` mod arithmetic relies on `indexOfFirst` returning -1 cleanly

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt:160-163`
**Issue:**
When the current selection isn't found, `indexOfFirst` returns -1; `(-1 + 1).mod(size)` = 0, so it
selects the first cam — acceptable. But `_vm.value.selected` is read for `currentId` while the actual
selection authority is `selectedId`/the driver's resolved cam; in a window where `_vm.selected` lags the
real selection (driver hasn't published yet), a cycle can pick a stale "next." Cosmetic given the 2fps
human-tap cadence.

**Fix:** Derive `currentId` from the same source the driver resolves from (`selectedId.value` →
preferred → first) rather than the published VM, for consistency.

---

## Summary Count

- **Critical (BLOCKER):** 2 — orphaned collector leak on shared scope (CR-01); `redactWebcamUrl` never invoked / V7 control is dead code (CR-02)
- **Warning:** 6 — driver-cancel race on re-select (WR-01); holder destroyed on rotation (WR-02); shared-`inBitmap` torn-frame across thread handoff (WR-03); MJPEG `inSampleSize` always 1 → full-res decode on the floor (WR-04); unenforced bitmap ownership (WR-05); boundary-substring / duplicate-header hardening gaps (WR-06)
- **Info:** 5

The decode safety net, tolerant parsing, terminal-401 discipline, and the previously-fixed
snapshot-OOM/main-thread-probe bugs are sound. The blockers and WR-01/WR-04 sit squarely in the
lifecycle/teardown and 2GB-memory areas the phase flagged as highest-risk and should be fixed before ship.

---

_Reviewed: 2026-06-04T04:39:16Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
