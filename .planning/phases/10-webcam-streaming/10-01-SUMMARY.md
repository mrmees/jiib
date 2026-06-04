---
phase: 10-webcam-streaming
plan: 01
subsystem: testing
tags: [mjpeg, moonraker, webcams, okhttp, okio, junit4, fixtures, goldens, fakes]

# Dependency graph
requires:
  - phase: 13-handshake-cadence (SessionTestHarness / FakeWebSocket)
    provides: the handshake-edge regression harness + replyFor canned-reply seam this plan extends
provides:
  - Verbatim E5 + E3 /server/webcams/list goldens (all 16 fields; webrtc-mediamtx; no-token + ?token= + blank-service + relative/127.0.0.1/localhost cams)
  - 3 labeled synthetic MJPEG multipart/x-mixed-replace .bin fixtures (with/without Content-Length + split-JPEG; boundary=dinghyboundary)
  - FakeWebcamHttp (Call.Factory) hardened to the real contract (401 Basic-Auth, 404 text/plain, multipart, image/jpeg, relative/127.0.0.1)
  - FakeMjpegStream (Okio BufferedSource over the .bin fixtures; with/no-Content-Length + split-across-reads modes)
  - SessionTestHarness extension — canned server.webcams.list reply + per-method request hit-counter (webcamsListRequests)
  - 8 COMPILING runtime-RED test scaffolds (the Wave-0 corpus the later waves replace with typed assertions)
affects: [10-02, 10-03, 10-04, 10-06, 10-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Wave-0 compiling runtime-RED scaffold: fail()-bodied JUnit4 stub with NO unbuilt-symbol reference, so the whole testDebugUnitTest source set compiles while each scaffold fails at runtime; replaced with typed assertions by the owning wave"
    - "Synthetic golden byte-stream fixtures as the authoritative MJPEG-decode proof when no live subject exists (clearly documented synthetic)"
    - "Faithful test-double hardening to the captured live contract (401/404-text-plain/no-Content-Length/split-JPEG) to defeat the mock-vs-reality bug class"

key-files:
  created:
    - app/src/test/resources/golden/webcams_list_e5.json
    - app/src/test/resources/golden/webcams_list_e3.json
    - app/src/test/resources/fixtures/mjpeg_with_content_length.bin
    - app/src/test/resources/fixtures/mjpeg_no_content_length.bin
    - app/src/test/resources/fixtures/mjpeg_split_jpeg.bin
    - app/src/test/resources/fixtures/README_mjpeg_fixtures.txt
    - app/src/test/java/works/mees/dinghy/webcam/FakeWebcamHttp.kt
    - app/src/test/java/works/mees/dinghy/webcam/FakeMjpegStream.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamListParseTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamRungSelectTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamUrlResolverTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/MjpegStreamDecoderTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/FrameDropBehindTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamReconnectStateTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamBackoffTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamEnumerationCadenceTest.kt
  modified:
    - app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt

key-decisions:
  - "Harness-local WEBCAMS_LIST literal (not JsonRpcMethods.WEBCAMS_LIST): Wave 0 adds NO production code, and the harness must compile before that production const exists; the string value is identical to the real method so the canned reply still matches the live contract"
  - "The split-JPEG fixture's body is identical to the with-Content-Length body; the split is a read-CHUNKING property FakeMjpegStream applies (not a property of the bytes), documented in README + the BOUNDARY constant"
  - "E3 golden enriched beyond the single live-captured cam with a blank-service relative-URL cam and a 127.0.0.1/localhost cam, so the URL-resolver + rung-select tests have their adversarial inputs in the goldens (Security V5 untrusted-input corpus)"

patterns-established:
  - "Compiling runtime-RED scaffold (cross-wave compile rule)"
  - "Faithful-fake hardening to the captured live contract"
  - "Per-method request hit-counter as the observable cadence seam (no private-constant peeking)"

requirements-completed: []  # CAM-01 is NOT closed by this plan — Wave 0 lays the test corpus; CAM-01 is delivered across 10-02..10-08

# Metrics
duration: ~25min
completed: 2026-06-04
---

# Phase 10 Plan 01: Webcam Wave-0 Fixtures & Hardened Doubles Summary

**Captured verbatim E5/E3 webcam-enumeration goldens + 3 synthetic MJPEG byte-stream fixtures, hardened the FakeWebcamHttp/FakeMjpegStream doubles to the real 401/404-text-plain/no-Content-Length/split-JPEG contract, extended SessionTestHarness with a canned server.webcams.list reply + per-method hit-counter, and dropped 8 compiling runtime-RED scaffolds — the whole test source set compiles while each scaffold fails RED at runtime.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-06-04T02:17:32Z
- **Completed:** 2026-06-04T02:43:00Z (approx)
- **Tasks:** 2
- **Files modified:** 17 (16 created, 1 modified)

## Accomplishments
- Verbatim E5 (webrtc-mediamtx, no-token snapshot) + E3 (?token= snapshot, blank-service relative cam, 127.0.0.1/localhost cam) `/server/webcams/list` goldens with all 16 documented fields — the untrusted-input corpus (Security V5).
- 3 clearly-labeled synthetic `multipart/x-mixed-replace` `.bin` fixtures (with / without Content-Length, plus a split-JPEG body) — the authoritative MJPEG-decode proof since neither test printer exposes a decodable MJPEG stream.
- `FakeWebcamHttp` (an `okhttp3.Call.Factory`, the same seam `MoonrakerAuth` uses) and `FakeMjpegStream` (Okio `BufferedSource`) hardened to the REAL contract — deliberately non-lenient per Pitfall 6.
- `SessionTestHarness` extended with a faithful `server.webcams.list` reply branch + a per-method request hit-counter (`webcamsListRequests` / `requestCount(method)` / `resetRequestCounts()`) — the public observable seam the cadence test (10-03) asserts against, never the private `V1_SUBSCRIBE_CORE`.
- 8 compiling runtime-RED scaffolds: the whole `testDebugUnitTest` source set COMPILES (proven via `compileDebugUnitTestKotlin` BUILD SUCCESSFUL), the 8 webcam scaffolds run RED at runtime (8 tests, 8 failed by design), and the existing harness-dependent `net.*` suite stays GREEN (extension broke nothing).

## Task Commits

Each task was committed atomically:

1. **Task 1: Capture goldens + synthetic MJPEG fixtures** - `4b2b84a` (test)
2. **Task 2: Harden fakes + extend SessionTestHarness + 8 compiling runtime-RED scaffolds** - `fe1eb89` (test)

**Plan metadata:** (final docs commit below)

## Files Created/Modified
- `app/src/test/resources/golden/webcams_list_e5.json` - Verbatim E5 enumeration golden (webrtc-mediamtx, no-token snapshot), all 16 fields, 2 cams.
- `app/src/test/resources/golden/webcams_list_e3.json` - E3 golden (?token= snapshot, odd-unicode name) + a blank-service relative-URL cam + a 127.0.0.1/localhost cam.
- `app/src/test/resources/fixtures/mjpeg_with_content_length.bin` - 3-frame multipart body, Content-Length on every part.
- `app/src/test/resources/fixtures/mjpeg_no_content_length.bin` - 3-frame multipart body, NO Content-Length (boundary/SOI-EOI scan path); literal "Content-Length" absent.
- `app/src/test/resources/fixtures/mjpeg_split_jpeg.bin` - 2-frame body replayed in tiny chunks that bisect a JPEG payload.
- `app/src/test/resources/fixtures/README_mjpeg_fixtures.txt` - Documents each .bin as SYNTHETIC and why (no live MJPEG subject on E5/E3).
- `app/src/test/java/works/mees/dinghy/webcam/FakeWebcamHttp.kt` - Hardened Call.Factory double (401/404-text-plain/multipart/jpeg/relative/127.0.0.1).
- `app/src/test/java/works/mees/dinghy/webcam/FakeMjpegStream.kt` - Okio BufferedSource over the .bin fixtures; with/no-Content-Length + split-across-reads modes.
- `app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt` - + canned server.webcams.list reply + per-method request hit-counter.
- 8 webcam `*Test.kt` scaffolds - compiling runtime-RED stubs, each replaced by its owning wave (10-02/10-03/10-04/10-06).

## Decisions Made
- Used a harness-local `WEBCAMS_LIST` literal rather than a new `JsonRpcMethods.WEBCAMS_LIST` production const, because Wave 0 adds no production code and the harness must compile before that const exists. The string value is identical to the real method name, so the canned reply matches the live contract; 10-03 may repoint it once the production const lands.
- Enriched the E3 golden beyond the single live-captured cam (added a blank-service relative-URL cam and a 127.0.0.1/localhost cam) so the URL-resolver and rung-select adversarial inputs live in the goldens themselves (the V5 untrusted-input corpus), rather than being invented ad hoc in later tests.
- The split-JPEG fixture body is identical to the with-Content-Length body; the split is a read-chunking property `FakeMjpegStream` applies (documented in the README and the shared `BOUNDARY` constant), not baked into the bytes.

## Deviations from Plan

None - plan executed exactly as written.

The plan explicitly sanctioned both judgment calls above (the harness-local literal is the plan's own "no production code" constraint; the enriched E3 golden is the plan's "E3 = multi-cam, faithful to the captures" + the threat model's V5 untrusted-input requirement). No auto-fix rules were triggered; no architectural decisions arose.

## Issues Encountered
- Pre-existing `@ExperimentalCoroutinesApi` opt-in warnings in `ConsoleHolderTest.kt` / `MacroHolderTest.kt` surfaced during the compile. These are OUT OF SCOPE (not caused by this plan's changes) — left untouched per the scope boundary; not logged to deferred-items as they are long-standing non-blocking warnings unrelated to webcam work.

## User Setup Required
None - no external service configuration required. (No new dependencies; `gradle/libs.versions.toml` unchanged — the minSdk-23 floor stays auditable, PKG-02.)

## Next Phase Readiness
- Wave-0 corpus is in place and the test source set compiles green, so the intervening waves' targeted `--tests` runs will NOT break on unresolved references.
- 10-02 replaces WebcamListParseTest / WebcamRungSelectTest / WebcamUrlResolverTest bodies with typed assertions against WebcamModels / WebcamProbe / WebcamUrl.
- 10-03 replaces WebcamEnumerationCadenceTest, asserting via the harness's new `webcamsListRequests` counter (exactly-once-per-handshake-edge) + the subscribe frame carrying no webcam objects.
- 10-04 replaces MjpegStreamDecoderTest / FrameDropBehindTest / (part of) WebcamBackoffTest; 10-06 replaces WebcamReconnectStateTest / WebcamBackoffTest.
- No blockers.

## Self-Check: PASSED

All 17 created files verified present on disk; both task commits (`4b2b84a`, `fe1eb89`) verified in git log. Test source set verified COMPILING (`compileDebugUnitTestKotlin` BUILD SUCCESSFUL); 8 webcam scaffolds verified RED at runtime (8 tests, 8 failed by design); existing `net.*` harness-dependent suite verified GREEN.

---
*Phase: 10-webcam-streaming*
*Completed: 2026-06-04*
