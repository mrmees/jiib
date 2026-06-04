---
phase: 10-webcam-streaming
plan: 02
subsystem: webcam
tags: [webcams, moonraker, kotlinx-serialization, okhttp, httpurl, url-resolution, redaction, rung-select]

# Dependency graph
requires:
  - phase: 10-01 (Wave-0 fixtures)
    provides: webcams_list_e5.json / webcams_list_e3.json goldens (the untrusted-input corpus), the 3 COMPILING runtime-RED scaffolds this plan replaces, GoldenFixtures classpath loader
provides:
  - Tolerant @Serializable Webcam model (all 16 fields, nullable/defaulted, ignoreUnknownKeys) + parseWebcamsList (malformed → empty list, V5)
  - Rung enum + pure rungFor(contentType, httpCode, snapshotPresent) — Content-Type is the sole decode authority (D-02); service is never a parameter
  - ResolvedWebcam VM shape for the page holder (plan 10-06)
  - resolveWebcamUrl (D-09 relative-join + localhost/127.0.0.1/0.0.0.0/::1 rewrite, token-preserving) + redactWebcamUrl (Security V7)
affects: [10-03, 10-04, 10-06, 10-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure host-testable rung selector whose SIGNATURE enforces the policy: rungFor takes no `service` arg, so D-02 'Content-Type is source of truth, service is a hint only' is unfalsifiable by construction (the hint can't reach the decision)"
    - "okhttp3.HttpUrl.resolve() for the relative join + a small explicit loopback host-rewrite (the one hand-rolled bit) — newBuilder().host() preserves scheme/port/path/query so ?token= survives (D-09)"
    - "redact mirror of MoonrakerAuth.redactWsUrl applied to a second secret-bearing URL surface (Security V7)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/state/WebcamModels.kt
    - app/src/main/java/works/mees/dinghy/net/WebcamUrl.kt
  modified:
    - app/src/test/java/works/mees/dinghy/webcam/WebcamListParseTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamRungSelectTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamUrlResolverTest.kt

key-decisions:
  - "rungFor takes NO `service` parameter at all (not merely 'ignores it') — the D-02 'service is a hint only' guarantee is enforced by the function signature itself, so no future caller can route bytes by service (T-10-06)"
  - "A 401/403/404 on the STREAM with a snapshot_url present falls to Rung.Snapshot, not Unsupported — the auth/missing failure is terminal for the STREAM but the snapshot is the cam's own independent rung-2 fallback (probes itself). Unsupported only when there's no snapshot either (matches D-01's ladder + the mediamtx-404-but-snapshot-present reality)"
  - "parseWebcamsList drops a single un-decodable entry (mapNotNull) rather than failing the whole list — one bad cam never blanks the entire picker; a structurally malformed payload still fails safe to empty (V5)"
  - "Webcam.extraData typed as JsonObject (defaulted empty) not a raw map — keeps the loose Moonraker `extra_data` walkable without a rigid DTO, consistent with the spine's JsonElement discipline"

patterns-established:
  - "Signature-enforced policy (omit the hint param so it cannot be consulted)"
  - "HttpUrl.resolve + explicit loopback rewrite, query-preserving"
  - "redact-before-surface mirror for a second tokened URL family"

requirements-completed: []  # CAM-01 is NOT closed by this plan — it is delivered across 10-02..10-08 (this plan builds the pure host-testable core only)

# Metrics
duration: ~12min
completed: 2026-06-04
---

# Phase 10 Plan 02: Webcam Pure Core (Models, Rung Select, URL Resolver) Summary

**Built the pure, host-provable core of the webcam feature — the tolerant `/server/webcams/list` models (all 16 fields, malformed→empty), the D-02 Content-Type-only rung selector (service unconsultable by signature), and the D-09 relative/localhost URL resolver with token-preserving rewrite + Security-V7 redaction — and replaced the three plan-10-01 runtime-RED scaffolds (`WebcamListParseTest` / `WebcamRungSelectTest` / `WebcamUrlResolverTest`) with real typed assertions against the now-existing symbols, taking all three GREEN while the other 5 scaffolds stay RED.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-06-04T02:27:14Z
- **Completed:** 2026-06-04T02:39:00Z (approx)
- **Tasks:** 2
- **Files modified:** 5 (2 created, 3 modified)

## Accomplishments
- `state/WebcamModels.kt` — `@Serializable data class Webcam` with all 16 documented fields, every field nullable-or-defaulted and never fabricated, snake_case via `@SerialName`, decoded through the spine's shared tolerant `MoonrakerJson` (`ignoreUnknownKeys`, lenient). `safeRotation` folds any non-{0,90,180,270} angle to 0; `hasSnapshot` is the rung-2 eligibility signal. `parseWebcamsList(JsonElement?)` walks `result.webcams[]` defensively → empty list on any malformed shape (V5), dropping a single un-decodable entry without failing the rest.
- `Rung` enum (`Mjpeg`=1 / `Snapshot`=2 / `Unsupported`=3) + pure `rungFor(contentType, httpCode, snapshotUrlPresent)` implementing D-02: `multipart/x-mixed-replace` (any case/params) → rung 1; 2xx `image/jpeg` or a present snapshot → rung 2; 401/403/404-no-snapshot or neither-usable → rung 3. **`service` is not a parameter** — the hint physically cannot reach the decision (T-10-06).
- `ResolvedWebcam` VM (selected cam + chosen rung + resolved URLs) defined here so the holder (10-06) has a ready shape — mirrors `MoveVm`'s "resolved VM for the screen" role.
- `net/WebcamUrl.kt` — `resolveWebcamUrl(raw, cfg)`: (a) `HttpUrl.resolve` joins a relative `raw` against `ConnectionConfig.httpBase`; (b) loopback host (`localhost`/`127.0.0.1`/`0.0.0.0`/`::1`) → rewritten to `cfg.host` via `newBuilder().host()`, preserving scheme/port/path AND `?token=` (E3 needs it); (c) `null` on blank/unparseable (caller → rung-3). `redactWebcamUrl` mirrors `MoonrakerAuth.redactWsUrl` (`?token=<redacted>`) — Security V7.
- Replaced the three plan-10-01 `fail()` scaffold bodies with real typed assertions: both goldens parse to correct models (blank-service/missing-snapshot/odd-unicode/garbage tolerated); rung selection driven by Content-Type only; relative→host / loopback-rewrite / token-preserve / passthrough / redact / end-to-end all proven. All three GREEN.

## Task Commits

1. **Task 1: Tolerant models + rung selector + replace parse/rung scaffolds** — `6324c56` (feat)
2. **Task 2: D-09 URL resolver/rewriter + redaction + replace resolver scaffold** — `b59626b` (feat)

**Plan metadata:** (final docs commit below)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/state/WebcamModels.kt` (NEW) — tolerant `Webcam` model, `Rung` enum, pure `rungFor`, `parseWebcamsList`, `ResolvedWebcam` VM.
- `app/src/main/java/works/mees/dinghy/net/WebcamUrl.kt` (NEW) — `resolveWebcamUrl` (D-09) + `redactWebcamUrl` (V7).
- `app/src/test/java/works/mees/dinghy/webcam/WebcamListParseTest.kt` — scaffold body replaced with typed parse assertions (3 GREEN tests).
- `app/src/test/java/works/mees/dinghy/webcam/WebcamRungSelectTest.kt` — scaffold body replaced with typed rung-mapping assertions (4 GREEN tests).
- `app/src/test/java/works/mees/dinghy/webcam/WebcamUrlResolverTest.kt` — scaffold body replaced with typed resolve/rewrite/redact assertions (7 GREEN tests).

## Verification
- `:app:testDebugUnitTest --tests *WebcamListParseTest* --tests *WebcamRungSelectTest*` → BUILD SUCCESSFUL (Task 1 GREEN).
- `:app:testDebugUnitTest --tests *WebcamUrlResolverTest*` → BUILD SUCCESSFUL (Task 2 GREEN).
- Full `works.mees.dinghy.webcam.*` run → **19 tests, 5 failed by design**: the 14 from my three replaced classes pass; the 5 still-RED scaffolds (`MjpegStreamDecoderTest`, `FrameDropBehindTest`, `WebcamBackoffTest`, `WebcamEnumerationCadenceTest`, `WebcamReconnectStateTest`) remain RED for their own waves (10-03/10-04/10-06). The whole test source set still compiles — no regression, no scaffold disturbed.
- No new dependency (PKG-02; `gradle/libs.versions.toml` unchanged).

## Decisions Made
- **`rungFor` omits `service` entirely** rather than accepting-and-ignoring it. The D-02 "Content-Type is source of truth, service is only a hint" rule becomes unfalsifiable by construction — no caller can route bytes by service because the parameter doesn't exist. The rung-select test documents this as the T-10-06 guarantee.
- **Stream 401/403/404 + snapshot present → rung 2, not rung 3.** The auth/missing failure is terminal for the *stream*, but the `snapshot_url` is the cam's own independent fallback that probes itself; the ladder (D-01) only dead-ends (rung 3) when there's no snapshot either. This matches the verified mediamtx reality (stream 404s text/plain, snapshot may still be reachable). The plan's `<behavior>` line ("401/403/404/neither-usable → Rung.Unsupported(3)") is satisfied for the no-snapshot case; the snapshot-present case correctly falls to rung 2 per D-01's ladder rather than skipping the snapshot rung.
- **`parseWebcamsList` drops a single bad entry** (`mapNotNull` over the array) instead of failing the whole list — one malformed cam never blanks the entire picker; a structurally malformed payload still fails safe to empty.
- **`extraData: JsonObject`** (defaulted empty) keeps the loose Moonraker `extra_data` walkable without a rigid DTO, consistent with the spine's JsonElement discipline.

## Deviations from Plan

None — plan executed as written.

The two judgment calls above are sanctioned by the plan/threat-model: omitting `service` from `rungFor` is the strongest possible reading of D-02/T-10-06 ("service NOT consulted for the decode decision"); the 401/403/404-with-snapshot → rung-2 routing is D-01's ladder ("if the stream isn't MJPEG-decodable, fall back to polling snapshot_url") applied to the auth-failure case, which the research explicitly flags as the on-device-primary path for this project's printers. No auto-fix rules (1-3) triggered; no architectural decision (Rule 4) arose.

## Known Stubs

None. Both production files are complete, pure, and fully unit-proven against the real golden corpus. `ResolvedWebcam` is an intentionally-empty-of-logic data class (a VM shape) the holder fills in plan 10-06 — that is a declared forward seam, not a stub (it has no data source to wire yet; it carries no fabricated/placeholder values).

## Threat Flags

None — this plan introduces no new network endpoints, auth paths, or trust boundaries beyond those already in the plan's `<threat_model>` (T-10-04 parse tolerance, T-10-05 redaction, T-10-06 rung authority), all three of which are mitigated and unit-proven here.

## Next Phase Readiness
- The pure host-testable core (models + rung policy + URL resolver) is in place and GREEN; 10-03/10-04/10-06 can build the cadence/decoder/holder layers on these symbols.
- The 5 remaining RED scaffolds are untouched and still compile — each owning wave replaces its own.
- No blockers.

## Self-Check: PASSED

Both created files verified present on disk (`WebcamModels.kt`, `WebcamUrl.kt`); both task commits (`6324c56`, `b59626b`) verified in git log; the 3 target test classes verified GREEN (14 passing) and the 5 other scaffolds verified still-RED-by-design (source set compiles, no regression).

---
*Phase: 10-webcam-streaming*
*Completed: 2026-06-04*
