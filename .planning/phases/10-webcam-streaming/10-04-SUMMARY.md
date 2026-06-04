---
phase: 10-webcam-streaming
plan: 04
subsystem: webcam
tags: [webcams, mjpeg, okhttp, okio, bitmapfactory, snapshot-poll, backoff, drop-behind, redaction, security-v7, dos-guard]

# Dependency graph
requires:
  - phase: 10-01 (Wave-0 fixtures)
    provides: the 3 synthetic MJPEG .bin fixtures (with/no Content-Length + split-JPEG, boundary=dinghyboundary), FakeWebcamHttp (Call.Factory), FakeMjpegStream (Okio BufferedSource), and the COMPILING runtime-RED MjpegStreamDecoderTest / FrameDropBehindTest / WebcamBackoffTest scaffolds this plan replaces
  - phase: 10-02 (pure core)
    provides: Rung enum + pure rungFor(contentType, httpCode, snapshotPresent) the probe folds through; redactWebcamUrl (Security V7)
provides:
  - WebcamProbe — D-02 Content-Type sniff (GET not HEAD) → ProbeResult.Mjpeg (body kept open + boundary) / Snapshot / Unsupported(terminal); 401/403/404 terminal-for-cam, IOException folds to a typed non-escaping result
  - MjpegStreamDecoder<T> — lean host-testable Okio multipart boundary scanner (Content-Length + no-CL boundary/EOI scan + split-JPEG reassembly), drop-behind Channel(DROP_OLDEST), hostile-frame OOM reject; bitmaps() production factory (inBitmap+inSampleSize+inMutable, Pitfall-5 recovery)
  - SnapshotPoller<T> — ~2fps finite-timeout GET loop, 401/403 terminal-no-spin, transient→capped backoff, drop-behind; snapshotBitmaps() production factory
  - WebcamClients — the two Pitfall-4 read-timeout postures (stream readTimeout(0) vs snapshot finite 5s) derived off the ONE shared OkHttp client via newBuilder()
  - MjpegDecodePolicy — named, easily-tuned A1/A2 decode constants (TARGET_FPS, PREFER_RGB_565, MAX_SAMPLE_SIZE, computeInSampleSize) for 10-08 on-device pinning
affects: [10-06, 10-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Android-platform seam parameterisation: the decode (BitmapFactory) is injected as a `(ByteArray) -> T?` function so the genuinely-new multipart boundary scanner is host-provable on the JVM WITHOUT Robolectric — the test injects a byte-recorder; production injects BitmapFactory.decodeByteArray"
    - "Drop-behind frame hand-off via Channel(capacity=1, onBufferOverflow=DROP_OLDEST): trySend never blocks, conflated latest-wins — a slow Adreno-320 View never back-pressures the decode/poll loop (D-06/SC-1)"
    - "Hostile-frame OOM guard at the byte-extraction boundary: an absurd Content-Length / an unterminated no-CL part is rejected (returns empty → skipped) BEFORE any allocation (T-10-02)"
    - "Two derived read-timeout postures off the ONE shared client (newBuilder().readTimeout) — stream=0, snapshot=finite — never a second client (Pitfall 4)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/net/WebcamProbe.kt
    - app/src/main/java/works/mees/dinghy/net/MjpegStreamDecoder.kt
    - app/src/main/java/works/mees/dinghy/net/SnapshotPoller.kt
    - app/src/main/java/works/mees/dinghy/net/WebcamClients.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamProbeTest.kt
  modified:
    - app/src/test/java/works/mees/dinghy/webcam/MjpegStreamDecoderTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/FrameDropBehindTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamBackoffTest.kt

key-decisions:
  - "MjpegStreamDecoder is GENERIC over the frame type T with an injectable decode(jpeg) seam — so the multipart boundary scanner (the real new code) is proven on the JVM with a byte-recorder, while BitmapFactory stays entirely out of the unit suite (no Robolectric in this project). Production binds T=Bitmap via bitmaps()/snapshotBitmaps()."
  - "WebcamProbe reads Content-Type from the response header, FALLING BACK to the body's media type when the header is absent (the two are wire-equivalent; some OkHttp response constructions surface the type only on the body — the FakeWebcamHttp double does exactly this). Without the fallback a real multipart body whose type lived only on the ResponseBody would mis-route to Snapshot."
  - "The two read-timeout postures are pinned HERE in WebcamClients (stream readTimeout(0) / snapshot finite 5s) even though the live client is threaded in by the holder (10-06) — so the Pitfall-4 contract is auditable in code in this plan, not deferred to a comment."
  - "A1/A2 decode params (fps cap, RGB_565 vs 8888, max sample size) live in MjpegDecodePolicy as named consts so 10-08 can re-pin them on flox without touching the decoder."

patterns-established:
  - "Android-platform-seam parameterisation for host-testable decode"
  - "Drop-behind conflated channel frame hand-off"
  - "Hostile-frame OOM reject at the byte boundary"

requirements-completed: []  # CAM-01 is NOT closed by this plan — the decode I/O layer is built + unit-proven; CAM-01 closes across 10-06..10-08 (holder/screen/on-device UAT)

# Metrics
duration: ~11min
completed: 2026-06-04
---

# Phase 10 Plan 04: Webcam Decode I/O Layer (Probe, MJPEG Decoder, Snapshot Poller) Summary

**Built the genuinely-new custom webcam decode I/O: the D-02 Content-Type probe/rung-selector (`WebcamProbe`), the lean host-testable Okio multipart MJPEG decoder with drop-behind + hostile-frame OOM guard (`MjpegStreamDecoder`), and the ~2fps finite-timeout snapshot poller with terminal-401 + capped backoff (`SnapshotPoller`) — all three off the ONE shared OkHttp client via the two Pitfall-4 read-timeout postures (`WebcamClients`) — then replaced the plan-10-01 runtime-RED `MjpegStreamDecoderTest` / `FrameDropBehindTest` / `WebcamBackoffTest` scaffolds (and added `WebcamProbeTest`) with real typed assertions proving all three .bin fixtures, the split-JPEG reassembly, the absurd-Content-Length reject, the conflated drop-behind, and the 401-terminal-no-spin — taking them GREEN while only the 10-06-owned `WebcamReconnectStateTest` stays RED-by-design.**

## Performance
- **Duration:** ~11 min
- **Started:** 2026-06-04T02:50:49Z
- **Completed:** 2026-06-04T03:01:16Z (approx)
- **Tasks:** 3
- **Files modified:** 8 (5 created, 3 test scaffolds replaced)

## Accomplishments
- **`net/WebcamProbe.kt`** — GETs (not HEAD — some MJPEG servers ignore HEAD, RESEARCH Pattern 1) the resolved stream URL through an injectable `Call.Factory`, reads Content-Type, and folds through the pure `rungFor` (10-02) into a `ProbeResult`: `Mjpeg(body, boundary)` keeps the body OPEN + parses the `boundary=` token; `Snapshot` / `Unsupported(terminal)` close it. A 401/403/404-no-snapshot is a TERMINAL `Unsupported` (A4/Pitfall 3 — no spin-retry); an `IOException` folds to a typed transient (non-terminal) result — NEVER an escaping exception.
- **`net/MjpegStreamDecoder.kt`** — the genuinely-new custom code: a lean Okio `BufferedSource` multipart boundary scanner. Reads each part by `Content-Length` when present, else scans to the JPEG EOI (`FF D9`) / next boundary (the no-Content-Length path); a JPEG split across `source.read()` chunks is reassembled transparently via Okio `indexOf`/`require`/`readByteArray`. Drop-behind hand-off via `Channel(capacity=1, DROP_OLDEST)` (conflated latest-wins, `trySend` never blocks — D-06/SC-1). **OOM guard (T-10-02):** an absurd `Content-Length` (or an unterminated no-CL part) is rejected at `MAX_PART_BYTES` BEFORE any allocation. Generic over `T` with an injectable `decode(jpeg)` seam → host-provable WITHOUT Android; the `bitmaps()` production factory binds `T=Bitmap` with `inBitmap`+`inSampleSize`+`inMutable` reuse and a Pitfall-5 size-mismatch recovery.
- **`net/SnapshotPoller.kt`** — a ~500ms (~2fps, D-07) loop of finite-timeout GETs. 401/403 → terminal `PollOutcome.Unsupported` (STOP, no spin — A4/Pitfall 3/T-10-10, the E5 ravens-perch Basic-Auth reality); a transient timeout/IOException/5xx → `backoffDelay` (reused, overflow-safe) capped at 10s, a success resets the attempt counter (D-12). Same drop-behind seam + injectable decode; `snapshotBitmaps()` production factory.
- **`net/WebcamClients.kt`** — the two Pitfall-4 read-timeout postures derived off the ONE shared `OkHttpClient` via `newBuilder()` (never a second client): `streamClient` = `readTimeout(0)` (a stream holds the connection open), `snapshotClient` = finite 5s (a wedged snapshot must time out, not hang).
- **`MjpegDecodePolicy`** — A1/A2 decode params as named, easily-tuned consts (`TARGET_FPS=12`, `PREFER_RGB_565=false`, `MAX_SAMPLE_SIZE`, `computeInSampleSize`) so 10-08 can re-pin them on flox without surgery.
- **Tests:** replaced the three plan-10-01 `fail()` scaffolds with typed assertions + added `WebcamProbeTest`; full unit suite **461 tests, 1 failed** = only the 10-06-owned `WebcamReconnectStateTest` (RED-by-design), no regression.

## Task Commits
1. **Task 1: D-02 Content-Type probe + rung selector (WebcamProbe)** — `f6d7c1b` (feat)
2. **Task 2: lean Okio MJPEG multipart decoder, drop-behind (MjpegStreamDecoder)** — `c2f5565` (feat)
3. **Task 3: ~2fps snapshot poller + two derived OkHttp postures (SnapshotPoller/WebcamClients)** — `838b747` (feat)

**Plan metadata:** (final docs commit below)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/net/WebcamProbe.kt` (NEW) — D-02 sniff/rung select, sealed `ProbeResult`, terminal-401, redaction-aware.
- `app/src/main/java/works/mees/dinghy/net/MjpegStreamDecoder.kt` (NEW) — generic Okio multipart scanner + drop-behind + OOM guard; `bitmaps()` + `MjpegDecodePolicy`.
- `app/src/main/java/works/mees/dinghy/net/SnapshotPoller.kt` (NEW) — ~2fps finite-timeout poll loop + terminal-401 + capped backoff; `snapshotBitmaps()`.
- `app/src/main/java/works/mees/dinghy/net/WebcamClients.kt` (NEW) — the two derived read-timeout postures off the shared client.
- `app/src/test/java/works/mees/dinghy/webcam/WebcamProbeTest.kt` (NEW) — 8 typed assertions vs FakeWebcamHttp (multipart/404/401/transport/GET-not-HEAD/redact).
- `app/src/test/java/works/mees/dinghy/webcam/MjpegStreamDecoderTest.kt` — scaffold body replaced: 3 fixtures correct frame count + exact bytes, split-JPEG, one-reused-state, absurd-CL reject.
- `app/src/test/java/works/mees/dinghy/webcam/FrameDropBehindTest.kt` — scaffold body replaced: conflated DROP_OLDEST never blocks, latest-wins, no backlog.
- `app/src/test/java/works/mees/dinghy/webcam/WebcamBackoffTest.kt` — scaffold body replaced: 401 terminal-1-GET, 10s backoff cap, transient-retry-then-cancel, E3 token snapshot yields frames.

## Verification
- `--tests *WebcamRungSelectTest* --tests *WebcamProbeTest*` → BUILD SUCCESSFUL (Task 1 GREEN).
- `--tests *MjpegStreamDecoderTest* --tests *FrameDropBehindTest*` → BUILD SUCCESSFUL (Task 2 GREEN).
- `--tests *SnapshotPoller* --tests *WebcamBackoff*` → BUILD SUCCESSFUL (Task 3 GREEN).
- Full `:app:testDebugUnitTest` → **461 tests, 1 failed**: the sole failure is `WebcamReconnectStateTest` (plan-10-06 scaffold, RED-by-design); all Task 1/2/3 tests + every existing suite GREEN. No regression; the render/holder scaffolds untouched.
- `newBuilder().readTimeout` present in `WebcamClients.kt` (the two-derived-postures key_link); `BufferedSource`/`.source()` discipline present in `MjpegStreamDecoder.kt`.
- **No new dependency** (PKG-02; `gradle/libs.versions.toml` + `app/build.gradle.kts` unchanged) — platform `BitmapFactory` + already-pinned OkHttp/Okio/coroutines only.

## Decisions Made
- **Generic decoder + injectable decode seam.** `MjpegStreamDecoder<T>`/`SnapshotPoller<T>` take a `(ByteArray) -> T?` decode function. The project has NO Robolectric, so `BitmapFactory.decodeByteArray` is unavailable in JVM unit tests; parameterising the decode keeps the genuinely-new byte-scanner host-provable (a byte-recorder in tests) while `BitmapFactory` is confined to the `bitmaps()`/`snapshotBitmaps()` production factories. This is the same "extract the pure core so it runs on the JVM without a View" discipline as `GraphView.sanitize`/`GraphDownsampleTest`.
- **Content-Type header fallback to body media type (auto-fix, Rule 1).** A `multipart/x-mixed-replace` `ResponseBody` constructed via `toResponseBody(mediaType)` carries its type on the body, not necessarily as a `Content-Type` RESPONSE header (the FakeWebcamHttp double does exactly this — and real servers/stacks vary). Reading the header alone mis-routed the multipart case to `Snapshot`. Fixed by `response.header("Content-Type") ?: response.body?.contentType()?.toString()` — the two forms are wire-equivalent. (Surfaced as a Task-1 RED on the first run; fixed before commit.)
- **Read-timeout postures pinned in code, not comments.** `WebcamClients` derives both postures off the shared client here so Pitfall 4 is auditable in this plan even though the live client is threaded in by the holder (10-06).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Content-Type read from response header alone mis-routed the multipart case**
- **Found during:** Task 1 (first verification run — `multipartStream_yieldsMjpeg_…` FAILED).
- **Issue:** `WebcamProbe` read `response.header("Content-Type")` only. An OkHttp `Response` whose body was built with `toResponseBody(mediaType)` (as `FakeWebcamHttp` does, and as some real stacks do) exposes the media type on the BODY, not as a response header — so `rungFor(null, 200, true)` selected `Snapshot` for a genuine `multipart/x-mixed-replace` stream.
- **Fix:** fall back to the body's media type — `response.header("Content-Type") ?: response.body?.contentType()?.toString()`. Wire-equivalent; the boundary parse reads the same source.
- **Files modified:** `net/WebcamProbe.kt`.
- **Commit:** `f6d7c1b` (fixed before the Task-1 commit).

No architectural (Rule 4) decisions arose. The generic-decoder + injectable-decode-seam shape is the plan's own "host-testable against the 10-01 fixtures" mandate applied to a project with no Robolectric (the plan's `<action>` says "decode each part via `BitmapFactory.decodeByteArray`" — parameterising that call is what makes the byte-scanner unit-provable without changing the production decode policy).

## Known Stubs
None. All three services are complete and fully unit-proven against the real 10-01 fixture corpus + the hardened doubles. The `bitmaps()`/`snapshotBitmaps()` production factories wire the actual `BitmapFactory` decode (exercised on-device, not in the JVM suite). `MjpegDecodePolicy`'s A1/A2 constants are ASSUMED-pending-on-device-measurement (10-08) by explicit design (D-06 / RESEARCH A1/A2) — named tunables, not stubs.

## Threat Flags
None — this plan introduces no network surface beyond the plan's `<threat_model>`. All five registered threats are mitigated and unit-proven here: T-10-02 (hostile-frame OOM reject — `absurdContentLength_isRejected_neverDecoded`), T-10-09 (finite snapshot read timeout + 10s backoff cap), T-10-10 (401/403 terminal-no-spin — `authFailure401_isTerminal_…`), T-10-05 (redaction — `redactWebcamUrl` applied before any surface), T-10-SC (no installs; `libs.versions.toml` unchanged).

## Next Phase Readiness
- The decode I/O layer (probe → MJPEG decoder → snapshot poller, off the two derived client postures) is in place and GREEN; 10-06 can build the `WebcamHolder` on these symbols (rung selection via `WebcamProbe`, frame hand-off via the decoder/poller drop-behind `frames` flows, foreground-only backoff, the D-11 reconnect state machine — `WebcamReconnectStateTest` is its RED scaffold).
- 10-08 re-pins `MjpegDecodePolicy` A1/A2 on flox and drives the on-device snapshot-ladder UAT (E3 token snapshot; the E5 401 correctly shows the rung-3 card).
- No blockers.

## Self-Check: PASSED
All 5 created files verified present on disk (`WebcamProbe.kt`, `MjpegStreamDecoder.kt`, `SnapshotPoller.kt`, `WebcamClients.kt`, `WebcamProbeTest.kt`); all 3 task commits (`f6d7c1b`, `c2f5565`, `838b747`) verified in git log. The 3 replaced scaffolds + `WebcamProbeTest` verified GREEN via targeted `--tests` runs; the full suite verified 461 tests / 1 failed (only the 10-06 `WebcamReconnectStateTest` RED-by-design, no regression). No new dependency (`libs.versions.toml` unchanged).

---
*Phase: 10-webcam-streaming*
*Completed: 2026-06-04*
