---
phase: 10-webcam-streaming
plan: 08
subsystem: webcam
tags: [webcams, snapshot-poll, mediamtx, de-dup, gfxinfo, perf-pin, on-device-uat, adreno-320, webrtc-deferred]

# Dependency graph
requires:
  - phase: 10-04 (decode I/O layer)
    provides: SnapshotPoller (the ~2fps finite-timeout poll loop + drop-behind + terminal-401 + backoff) and MjpegDecodePolicy (the A1/A2 decode constants) that this plan pins on-device and extends with byte-level de-dup
  - phase: 10-06 / 10-07 (holder + nav wiring)
    provides: the WebcamHolder rung lifecycle + WebcamScreen + page-visible cancel + greyed drawer tile that the on-device UAT exercises live
provides:
  - The PHASE 10 GATE result: perf gate PASS on flox (render p95 ~20ms / 0 frozen frames / near-zero GC — ADR-0001 Add. 2), snapshot ladder LIVE-PROVEN on E3, rung-3 dead-end LIVE-PROVEN on E5, rung-1 MJPEG FIXTURE-PROVEN
  - Root-cause finding: the snapshot feed's ~0.3fps perceived rate is SERVER-bound (MediaMTX snapshot regenerates every ~3-4s), NOT decode-bound — the honest v1 fallback, correctly badged
  - Snapshot DE-DUP: SnapshotPoller skips re-decoding/re-emitting byte-identical server-capped repeats (CPU/battery win on the 2GB floor), treated as SUCCESS never Transient
  - PINNED A1/A2 decode values (TARGET_FPS=12, PREFER_RGB_565=false, MAX_SAMPLE_SIZE=16, snapshot POLL_INTERVAL=500ms) — validated on-device, unchanged
  - CAM-01 DELIVERED (the requirement closes across 10-02..10-08)
affects: [13-optimization, 14-release, future-webrtc-v2]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Byte-level de-dup at the poll boundary (length-check short-circuit → exact contentEquals) so an identical server-capped fetch is treated as SUCCESS (reset backoff, hold cadence) WITHOUT a redundant JPEG decode or duplicate emit — provable host-side via the injected decode recorder, no Android needed"
    - "On-device gfxinfo perf gate as the system-of-record (ADR-0001 Add. 2): the real Adreno-320 PINS the decode constants; emulators lie about old-GPU fill rate"

key-files:
  created:
    - app/src/test/java/works/mees/dinghy/webcam/SnapshotDeDupTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/net/SnapshotPoller.kt
    - docs/view_specific_notes/camera_feed
    - .planning/phases/10-webcam-streaming/10-UAT.md

key-decisions:
  - "Snapshot mode is ACCEPTED as the honest v1 fallback for WebRTC-only printers — the ~0.3fps perceived rate is MediaMTX's server-side snapshot cadence (regenerates every ~3-4s), not a flox/decoder limitation. Don't 'fix' the rate in the app; the snapshot badge correctly communicates it. True smooth video needs WebRTC (deferred SC-4) or a printer-side MJPEG streamer (crowsnest ustreamer) — neither home printer exposes one today."
  - "De-dup lives in the POLLER at the byte level (before/around the injected decode) so it's host-testable without Android and the decode-count reduction is provable. CRITICAL contract: an identical fetch is SUCCESS, never Transient — a server-capped slideshow must NOT trip the backoff."
  - "A1/A2 decode values HOLD unchanged (TARGET_FPS=12, ARGB_8888, MAX_SAMPLE_SIZE=16, 500ms poll) — gfxinfo on flox showed render p95 ~20ms / 0 frozen frames / near-zero GC, so there's nothing to claw back by lowering fps or flipping to RGB_565 (the bottleneck is the server, not the decode)."

patterns-established:
  - "Byte-level de-dup at the poll boundary: identical fetch = success-without-decode, never a transient/backoff"
  - "On-device gfxinfo PIN of decode constants on the real Adreno-320 (perf system-of-record)"

requirements-completed: [CAM-01]

# Metrics
duration: ~25min
completed: 2026-06-04
---

# Phase 10 Plan 08: Webcam Phase Gate — Perf Pin, Snapshot De-Dup & On-Device UAT Close-Out Summary

**Closed the Phase 10 webcam gate on the real flox (Adreno-320 / 2GB) against the live Ender 3 / Ender 5 printers: the perf gate PASSES (render p95 ~20 ms / 0 frozen frames / near-zero GC), the snapshot ladder is LIVE-PROVEN on E3, the rung-3 dead-end card is LIVE-PROVEN on E5, and rung-1 MJPEG stays FIXTURE-PROVEN (no live MJPEG subject exists on either printer). The live UAT root-caused the slow snapshot feed to the SERVER — MediaMTX regenerates its snapshot JPEG only every ~3-4 s, so snapshot mode is a server-capped ~0.3 fps slideshow (the honest v1 fallback, correctly badged), NOT a decoder/flox limitation. Drove a byte-level snapshot DE-DUP (skip re-decoding the ~8 identical fetches per regenerated frame — CPU/battery on the 2 GB floor) and PINNED the A1/A2 decode constants unchanged. CAM-01 is delivered across 10-02..10-08.**

## Performance
- **Duration:** ~25 min (de-dup + tests + rebuild/reinstall + docs/UAT close-out)
- **Tasks:** 1 auto (de-dup + build/deploy) + the resolved blocking human-verify gate (UAT, decided by the user)
- **Files modified:** 4 (1 created test, 1 prod source, 2 docs)

## Accomplishments
- **Snapshot DE-DUP (`net/SnapshotPoller.kt`).** The poll loop now tracks the last fetched JPEG bytes (`var lastBytes`). On a `200` with non-empty bytes that are byte-identical (cheap length check → exact `contentEquals`) it returns a new `Fetch.Duplicate` outcome — treated as a **SUCCESSFUL poll** (reset `attempt`, `delay(pollInterval)`) but **NOT decoded and NOT re-emitted** (the view already shows that frame). A changed image decodes → `trySend` + remember `bytes` for the next compare; a null decode is still `Transient` (defensive). All prior semantics preserved (terminal 401/403, finite read timeout, foreground-only cancellation, drop-behind channel, Security-V7 no-clear-text logging). The de-dup is the right place — byte-level, in the host-testable poller, before the injected decode.
- **Regression guard (`SnapshotDeDupTest.kt`).** Host unit test via a recording `decode` + a `Call.Factory` serving repeated-then-changed bytes: proves N identical fetches yield **exactly ONE decode + ONE emitted frame** (no duplicate decode/emit), the loop **keeps polling at cadence with NO spurious Transient/backoff** (the "identical = success" contract), and a **changed** fetch yields a **second** decode + emit. A second test proves a never-changing image is decoded + emitted exactly once.
- **Perf gate PASS + A1/A2 PINNED (on flox).** gfxinfo during the live E3 snapshot feed: render **p95 ~20 ms, 0 frozen frames**, near-zero GC; network healthy (flox→E3 snapshot GET ≈30 ms, ping ≈1.3 ms). The ASSUMED A1/A2 values **hold unchanged** and are now pinned in code comments + the design note + the UAT table: `TARGET_FPS=12`, `PREFER_RGB_565=false` (ARGB_8888), `MAX_SAMPLE_SIZE=16`, snapshot `POLL_INTERVAL=500 ms`.
- **Root-cause: the slow feed is SERVER-side.** MediaMTX's snapshot endpoint regenerates only every ~3-4 s (identical md5 for ~4 s, then a new frame), so snapshot mode is a **server-capped slideshow at ~0.3 fps** regardless of poll cadence — the honest v1 fallback for WebRTC-only printers, correctly badged. An earlier full-res `inSampleSize=1` OOM-thrash (committed fix `1083108`: two-pass downsample to view px) had compounded the symptom; with that fixed, the residual rate is purely the server cadence. True smooth video needs WebRTC (deferred SC-4) or a printer-side MJPEG streamer (crowsnest ustreamer) — neither home printer exposes one.
- **Docs/UAT updated.** `docs/view_specific_notes/camera_feed` records the server-cap finding, why snapshot mode is correct, the WebRTC/MJPEG paths to smooth video, the de-dup + rationale, and the pinned A1/A2 table. `10-UAT.md` resolved with live-vs-fixture results (rung-2 LIVE-PROVEN E3, rung-3 LIVE-PROVEN E5, rung-1 FIXTURE-PROVEN), perf PASS, and an APPROVED/PASSED verdict — no gap-closure plan needed.
- **Build + deploy.** `:app:testDebugUnitTest` = **0 failures** (incl. the new de-dup test); signed release APK rebuilt (`:app:assembleRelease` → `sign-release.bat`) and `adb install -r` on flox (0a64b42e) → `Success`.

## Task Commits
1. **Snapshot de-dup + A1/A2 pin + regression test** — `daae32c` (feat)
2. **Docs/UAT close-out + this SUMMARY + state** — final docs commit below

## Verification
- `:app:testDebugUnitTest --tests *SnapshotDeDupTest* --tests *WebcamBackoff*` → BUILD SUCCESSFUL.
- Full `:app:testDebugUnitTest` → BUILD SUCCESSFUL, **0 failures**.
- `:app:assembleRelease` → BUILD SUCCESSFUL (R8/shrink green; the two R8 default-constructor warnings are pre-existing okhttp/retrofit proguard-rule warnings, out of scope).
- Signed release APK installed on flox → `Success`.
- On-device UAT (the blocking gate) RESOLVED/APPROVED by the user: perf PASS (0 frozen frames), snapshot ladder live on E3, rung-3 card correct on E5 — recorded in `10-UAT.md`.

## Deviations from Plan
The plan's Task 2 was a BLOCKING human-verify gate. The on-device UAT was run live and **resolved by the user** (the orchestrator's context): accept snapshot mode as the honest v1 fallback for WebRTC-only printers, add the CPU/battery de-dup, pin the decode values, and CLOSE the gate. The de-dup + the server-cap finding were not pre-specified work items in the plan body — they emerged from the live UAT and are the correct close-out (a perf/correctness optimization on the 2GB floor, Rule-2-class "critical for the floor's CPU/battery budget"), not a gap-closure plan. No architectural (Rule 4) changes.

## Known Stubs
None. The snapshot path, de-dup, and rung ladder are complete and exercised live. rung-1 MJPEG is fixture-proven by design (no live MJPEG subject on either home printer — a documented limitation, not a stub). WebRTC is explicitly deferred (SC-4), documented in the design contract — an intentional v2 scope boundary, not a stub.

## Threat Flags
None — no new network surface beyond the plan's `<threat_model>`. T-10-11 (fill-rate DoS) is now PINNED by the on-device gfxinfo gate (0 frozen frames); T-10-03/T-10-05 (token-snapshot redaction) preserved (the de-dup never logs bytes/URLs); T-10-SC (no installs — `libs.versions.toml` unchanged). The de-dup adds a byte compare + a retained `lastBytes` reference (one ~100 KB array), no new surface.

## Next Phase Readiness
- Phase 10 (Webcam Streaming) is GATE-CLOSED and CAM-01 is delivered. WebRTC remains explicitly deferred (SC-4) for a possible v2 phase; a printer-side crowsnest/ustreamer MJPEG cam would exercise rung-1 live if ever stood up.
- No blockers. Next: `/gsd-discuss-phase 11` (Spool Management).

## Self-Check: PASSED
`SnapshotDeDupTest.kt` verified present on disk; `SnapshotPoller.kt` de-dup + A1/A2 pin present; commit `daae32c` verified in git log; `:app:testDebugUnitTest` = 0 failures; signed APK installed on flox (`Success`). `10-UAT.md` + `camera_feed` updated.

```
FOUND: app/src/test/java/works/mees/dinghy/webcam/SnapshotDeDupTest.kt
FOUND: .planning/phases/10-webcam-streaming/10-08-SUMMARY.md
FOUND: .planning/phases/10-webcam-streaming/10-UAT.md
FOUND: docs/view_specific_notes/camera_feed
FOUND: daae32c
```

---
*Phase: 10-webcam-streaming*
*Completed: 2026-06-04*
