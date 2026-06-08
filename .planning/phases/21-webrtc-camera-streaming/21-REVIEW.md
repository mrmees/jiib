---
phase: 21-webrtc-camera-streaming
reviewed: 2026-06-08T00:00:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/net/WebcamUrl.kt
  - app/src/main/java/works/mees/dinghy/net/WebcamProbe.kt
  - app/src/main/java/works/mees/dinghy/state/WebcamModels.kt
  - app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt
  - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt
  - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt
  - app/src/main/java/works/mees/dinghy/render/Media3SurfaceHost.kt
  - app/src/main/java/works/mees/dinghy/render/Media3SurfaceProvider.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/build.gradle.kts
  - gradle/libs.versions.toml
findings:
  critical: 1
  warning: 6
  info: 4
  total: 11
status: issues_found
---

# Phase 21: Code Review Report

**Reviewed:** 2026-06-08
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Reviewed the Phase 21 native-H.264 rung: the composite Media3/ExoPlayer feed, its
player lifecycle/teardown, the ravens-perch nested-schema URL resolution, the
surface-provider handshake, and the security redaction surface. The code is
careful and heavily documented, and the WR-01 teardown discipline (idempotent
`setVideoSurfaceView(null)` then `release()` under `NonCancellable + Dispatchers.Main`)
is genuinely well-handled.

However, the surface bridge has a real correctness defect: the running ExoPlayer
attempt captures ONE SurfaceView from `awaitSurface()` and never observes a
subsequent `clear()`/re-`register()` swap. On any recompose that recreates the
`AndroidView` SurfaceView (orientation change, theme change forcing a re-layout,
or a config-driven `AndroidView` reset), the player keeps rendering into a
destroyed/detached surface with no error and no recovery → a black feed that only
a nav-away+return fixes. That is the blocker. Several lifecycle/edge-case gaps
(clean-EOF hang, swallowed `CancellationException`, an unbounded `awaitSurface`)
are warnings. The pure URL logic (resolve/derive/explicit/redact) is solid and
well fail-safed.

## Critical Issues

### CR-01: Running ExoPlayer attempt pins a stale SurfaceView across a surface swap → permanent black feed

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt:189-204`
(with `app/src/main/java/works/mees/dinghy/render/Media3SurfaceHost.kt:75-84`,
`app/src/main/java/works/mees/dinghy/render/Media3SurfaceProvider.kt:31-45`)

**Issue:** `realH264Attempt` resolves the surface ONCE via
`surfaceProvider.awaitSurface()` (`_surface.filterNotNull().first()`), attaches it
with `exo.setVideoSurfaceView(surfaceView)`, then suspends in `result.await()` for
the entire healthy-stream lifetime. A healthy H.264 stream never returns from the
attempt, so the player holds that captured `SurfaceView` reference indefinitely.

But `Media3SurfaceHost` recreates its SurfaceView on `AndroidView` lifecycle: the
`factory` registers a NEW `SurfaceView`, and `onReset`/`onRelease` call
`surfaceProvider.clear()`. On an orientation change (the project explicitly
supports portrait AND landscape — see `CLAUDE.md`), Compose can reset/recreate the
`AndroidView`, producing a *new* SurfaceView and a `clear()` → `register(new)`
sequence on the provider. The already-running player is never told: it still holds
the OLD SurfaceView, whose underlying `Surface` has been destroyed. ExoPlayer does
not raise a `PlaybackException` for a destroyed output surface, so nothing
completes `result`, no `Transient`/`FallThrough` is produced, and the feed never
re-attaches. Result: a black/frozen H.264 feed after rotation that only recovers
on a full nav-away (which cancels the driver) and return.

This defeats the stated WR-01 surface-bridge intent ("a stale surface is never
re-attached") — the guard protects the *next* attempt, but does nothing for the
*currently-running* one whose surface was yanked.

**Fix:** Make the attempt react to surface changes instead of capturing once.
Collect the surface flow for the player's lifetime and re-attach (or tear down →
let the composite re-run) when it changes:

```kotlin
withContext(Dispatchers.Main) {
    val exo = ExoPlayer.Builder(context).build().also { player = it }
    exo.addListener(/* onPlayerError -> result.complete(...) */)
    exo.setMediaSource(buildMediaSource(transport, nativeUrl))
    exo.playWhenReady = true
    exo.prepare()
    // Re-bind the surface on every change; a null (host disposed/reset) detaches.
    launch {
        surfaceProvider.surface.collect { sv ->
            exo.setVideoSurfaceView(sv) // null detaches cleanly; new SV re-attaches
        }
    }
}
result.await()
```

At minimum, complete `result` with `Transient` when the registered surface goes
`null` while playing, so the composite re-runs and re-`awaitSurface()`s the fresh
one. A single `first()` capture is not safe for a surface that outlives a recompose.

## Warnings

### WR-01: Clean stream-end (STATE_ENDED) hangs the attempt forever — no fall-through, no reconnect

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt:194-210`

**Issue:** The only `Player.Listener` callback handled is `onPlayerError`.
`result` is *only* completed on an error. If the RTSP/HLS source reaches
`Player.STATE_ENDED` (server tears the stream down cleanly, HLS playlist ends, or
an idle MediaMTX path closes without an RTP error), no `PlaybackException` fires,
`result.await()` never resumes, and the feed sits suspended indefinitely — no
`Transient` retry, no `FallThrough`, no dead-end. The reconnect ladder the holder
exists to drive never engages for the clean-EOF case.

**Fix:** Also handle the ended/idle state and complete the attempt (Transient is
the right mapping — let the holder back off and re-attempt the H.264 rung):

```kotlin
override fun onPlaybackStateChanged(state: Int) {
    if (state == Player.STATE_ENDED) result.complete(H264AttemptResult.Transient)
}
```

### WR-02: `CancellationException` is caught and swallowed instead of rethrown

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt:211-213`

**Issue:** The attempt catches `CancellationException` and returns
`H264AttemptResult.Cancelled` rather than rethrowing it. Swallowing
`CancellationException` breaks structured-concurrency cancellation propagation:
the surrounding coroutine is left "completed normally" after a cancellation
request. Today the holder's `drive()` happens to `return` on `FeedOutcome.Cancelled`
so the symptom is masked, but this is fragile — if the attempt is ever composed
under another scope (or the `finally`'s `NonCancellable` block itself is what was
cancelled), the swallow hides a genuine cancellation. The KDoc even says
"Re-thrown after teardown," but the code returns instead of rethrowing.

**Fix:** Either rethrow after recording the outcome, or map to `Cancelled` only
after confirming the teardown completed and explicitly document that the holder
relies on the normal return. Preferred:

```kotlin
} catch (ce: CancellationException) {
    // finally runs first (NonCancellable teardown), then propagate cancellation.
    throw ce
}
```
and let the composite/holder treat the thrown `CancellationException` as the clean
exit it already handles structurally.

### WR-03: `awaitSurface()` can suspend forever for an H.264-selected cam that never composes the host

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt:189`
(with `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt:168-178`)

**Issue:** `awaitSurface()` has no timeout. `Media3SurfaceHost` only composes (and
thus only registers a SurfaceView) when `isLiveH264 = selectsH264Rung(cam) && vm.frame == null`.
There is an ordering dependency: the holder’s `drive()` sets `mode = Live, frame = null`
and starts the feed, which immediately `awaitSurface()`s — but the recomposition
that mounts `Media3SurfaceHost` must occur for a surface to ever appear. If for any
reason the host is not composed (e.g. the screen is on the Bitmap branch because a
transient frame lingered, or a future refactor gates the host differently), the IO
driver coroutine parks indefinitely on `awaitSurface()` with no diagnostic. It is
cancellation-safe (nav-away cancels it) but not self-healing while on-page.

**Fix:** Bound the wait and fall through if no surface arrives:

```kotlin
val surfaceView = withTimeoutOrNull(SURFACE_WAIT_MS) { surfaceProvider.awaitSurface() }
    ?: return@H264Attempt H264AttemptResult.FallThrough
```

### WR-04: Provider doc/threading contract mismatch — `awaitSurface()` is called on IO, not Main

**File:** `app/src/main/java/works/mees/dinghy/render/Media3SurfaceProvider.kt:40-45`
(call site `app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt:189`)

**Issue:** The KDoc states "The H.264 feed calls this on the main thread before
`player.setVideoSurfaceView(...)`." It does not — the feed runs on `Dispatchers.IO`
(the holder's `driverContext`), and `awaitSurface()` is invoked *before* the
`withContext(Dispatchers.Main)` block, i.e. on IO. It is functionally fine
(`StateFlow.first()` is thread-agnostic), but the contract comment is wrong and
will mislead the next maintainer reasoning about thread confinement on this exact
file the phase brief flags as the divergence.

**Fix:** Correct the KDoc to "called on the feed's driver dispatcher (IO); the
returned SurfaceView is attached inside a `withContext(Dispatchers.Main)` block,"
or actually marshal the await onto Main if main-thread confinement is intended.

### WR-05: HTTP-only `Content-Type` rung probe re-runs on every H.264 fall-through retry against the weak SBC

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt:144-149`
(with `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt:215-233`)

**Issue:** On an H.264 `FallThrough`, the composite delegates to `lowerRung.run(cam, onFrame)`
within the same `run()`. But the holder's reconnect loop re-invokes the WHOLE
composite `feed.run(cam)` on every `Transient` backoff cycle. For an H.264-selected
cam whose decoder keeps failing transiently (network flaps), each cycle does:
build ExoPlayer → fail → release → (on the next cycle) re-attempt H.264 again,
not the already-determined lower rung. There is no memory that the decoder already
verified H.264 is unviable for this cam this session, so a flapping cam pays the
full ExoPlayer build/release tax every retry on a 2GB device. (Out of strict v1
perf scope, but it is also a correctness smell: "the decoder verified the guess was
wrong" is re-litigated indefinitely rather than latched.)

**Fix:** Latch a per-cam "H.264 verified-bad this session" flag (keyed on cam id)
so subsequent retries skip straight to the lower rung until the selection inputs
change. Keep it session-scoped so a genuine reconnect re-probes.

### WR-06: `webcamSurfaceProvider` and `webcamHolder` re-key on identical keys but are independently `remember`ed — narrow swap-ordering window

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:244-260`

**Issue:** `webcamSurfaceProvider` and `webcamHolder` are two separate `remember(...)`
calls with the SAME key tuple `(store, activeCfg.host, activeProfileId, viewWidthPx, viewHeightPx)`.
Compose evaluates them in source order, so on a re-key the new provider is created,
then the new holder is built around it — fine in steady state. But the old
`webcamHolder`'s `cancel()` runs in a `DisposableEffect(webcamHolder){ onDispose{...} }`
whose disposal is NOT ordered relative to the new provider's creation. During the
swap there is a window where the OLD holder's still-unwinding H.264 attempt could
observe the provider state. Because the provider is re-`remember`ed (a fresh
instance), the old attempt keeps its own old provider reference, so this is mostly
benign — but the coupling is implicit and undocumented, and it compounds CR-01:
the old attempt is exactly the one holding a stale surface. Worth making explicit.

**Fix:** Bundle the provider construction inside `webcamMedia3Holder` (build it
where the holder is built, return it for the screen) so provider and holder share
one lifetime by construction, eliminating the two-`remember` coordination.

## Info

### IN-01: Stale KDoc — `nativeStreamUrlFor` references flat `extra_data` keys that ravens-perch never emits

**File:** `app/src/main/java/works/mees/dinghy/net/WebcamUrl.kt:149-161`

**Issue:** The `nativeStreamUrlFor` KDoc step (1) references `[EXTRA_NATIVE_RTSP]` /
`[EXTRA_NATIVE_HLS]` and calls the explicit read "DORMANT today (ravens-perch ships
no tag yet)." That is the old flat-key contract; the actual implementation
(`explicitNativeStreamUrl`) now reads the nested `extra_data.ravens_perch.streams.<proto>.url`
schema, which the commit `a254469` says ravens-perch DOES emit. The doc contradicts
the code and the (correct) nested-schema doc block just above it. Flagged once per
the review brief.

**Fix:** Update the KDoc to describe the nested `ravens_perch.streams.<proto>.url`
read and drop the dead `[EXTRA_NATIVE_RTSP]`/`[EXTRA_NATIVE_HLS]` symbol references
and the "DORMANT" claim.

### IN-02: `@Suppress("UNUSED_VARIABLE") safeForLog` is dead code held alive only to "touch" the redactor

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt:182-183`

**Issue:** `safeForLog = surfaceWebcamUrl(nativeUrl)` is computed and never used; it
exists solely to keep `surfaceWebcamUrl` referenced. The redaction control is
already genuinely live in `WebcamProbe` (line 113), so this touch-only assignment
in the H.264 path is dead code dressed up as a control. If a real diagnostic is
ever logged here it should use it; otherwise it is noise.

**Fix:** Remove the unused variable, or wire an actual (redacted) diagnostic on the
no-URL / fall-through path so the value is used.

### IN-03: `WebcamProbe.probe` reads `response.body` after `rungFor` may have classified Mjpeg without null-guarding the open-body invariant for non-Mjpeg

**File:** `app/src/main/java/works/mees/dinghy/net/WebcamProbe.kt:120-135`

**Issue:** Minor robustness note (not a leak): on the `Rung.Mjpeg` branch, if
`body == null || boundary.isBlank()` the code calls `response.close()` then returns
Snapshot/Unsupported — correct. The other branches also close. The body is handled
on every path, so there is no leak; flagging only that the `response.header(...) ?:
response.body?.contentType()` read touches `body` for content-type before the
branch decides ownership — harmless with OkHttp but easy to misread. No fix
required; documentation-level note.

**Fix:** None required; optionally add a one-line comment that reading
`contentType()` does not consume the body.

### IN-04: `selectsH264Rung(cam ?: Webcam())` allocates a throwaway Webcam every recomposition

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt:168`

**Issue:** `val isLiveH264 = selectsH264Rung(cam ?: Webcam()) && vm.frame == null`
allocates a default `Webcam()` on every recomposition when `cam` is null. Trivial,
but on the Adreno-320 / 2GB floor the project is fastidious about needless
allocation in hot composition paths. `selectsH264Rung` on a default Webcam is
always false anyway.

**Fix:** `val isLiveH264 = cam != null && selectsH264Rung(cam) && vm.frame == null`.

---

_Reviewed: 2026-06-08_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
