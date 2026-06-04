---
phase: quick-260604-kup
plan: 01
subsystem: spool
tags: [spoolman, camera, codex-review, bugfix]
requires: []
provides:
  - "MeasuredWeightPage dismisses only on a successful measure write"
  - "Corrected measureSpool KDoc (body, not query)"
  - "ScanSurface dispose-before-bind camera-leak guard"
affects:
  - app/src/main/java/works/mees/dinghy/ui/spool/MeasuredWeightPage.kt
  - app/src/main/java/works/mees/dinghy/spool/SpoolmanClient.kt
  - app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt
tech-stack:
  added: []
  patterns: ["DisposableEffect disposed-flag guard for async camera-provider listener"]
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/ui/spool/MeasuredWeightPage.kt
    - app/src/main/java/works/mees/dinghy/spool/SpoolmanClient.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt
decisions:
  - "PUT-vs-POST Codex CRITICAL is a confirmed FALSE POSITIVE — PUT /v1/spool/{id}/measure is correct per Spoolman 0.22.1; method left unchanged"
  - "No new error/toast plumbing added to MeasuredWeightPage — minimal fix is keeping the page open on failure (no channel was already at hand)"
metrics:
  duration: ~12 min
  completed: 2026-06-04
---

# Phase 11 Quick 260604-kup: Spool Codex-Review Fixes Summary

Applied three surgical, single-file fixes from the deferred Codex review of the Phase 11
Spoolman feature — one HIGH silent-success bug, one HIGH camera-leak race, one NIT stale
comment — with no new abstractions and no HTTP-method change.

## What Was Done

**FIX 1 (HIGH) — MeasuredWeightPage silent false-success.** The "Set" onClick coroutine
previously ran `client?.measureSpool(...); onMeasured()`, so the page dismissed as if the
write succeeded even when `client` was null (no session) or `measureSpool` returned null
(failed write). Now it captures the validated grams into a non-null local `g`, calls
`val result = client?.measureSpool(spool.id, g)`, and only calls `onMeasured()` when
`result != null`. On null it does nothing — the page stays open so the user can retry. No
new error/toast channel was added (none was already plumbed into the composable or its
caller; per the plan, keeping the page open is the minimal acceptable fix).
Commit `782f2f5`.

**FIX 2 (NIT) — measureSpool KDoc.** Comment-only change: the KDoc said the gross grams
travel "in the query (the proxy forwards the query)"; the implementation (commit fec00e9)
sends them in the request body via `buildJsonObject { put("weight", grossGrams) }`. Updated
the text to "in the request body (the proxy forwards the body)". The `PUT` method, path
`/v1/spool/$id/measure`, and the body are untouched — PUT is correct per Spoolman 0.22.1
and was validated live. Commit `8db0953`.

**FIX 3 (HIGH) — ScanSurface dispose-before-bind camera leak.** `providerFuture.addListener`
is async; if `onDispose` ran before the listener fired, `boundProvider` was still null so
onDispose unbound nothing, then the listener went on to `bindToLifecycle` and leaked a camera
that never got released. Added `var disposed = false` in the effect scope; at the top of the
listener (right after resolving the provider) `if (disposed) { provider?.unbindAll(); return@addListener }`
releases the late-resolved provider and skips binding; `onDispose` now sets `disposed = true`
in addition to the existing `boundProvider?.unbindAll()` / `analyzerExecutor.shutdown()`.
Lens-keying, the FILL_CENTER/COMPATIBLE PreviewView, analyzer wiring, and onBindFailed calls
are unchanged. Commit `ef465d4`.

## Verification

- **Release compile:** the full `:app:compileReleaseKotlin` ran clean — all three modified
  files compiled. The only warning on the changed files is an expected
  `Condition is always 'true'` on MeasuredWeightPage:139 (the `g != null` check is redundant
  with `valid`, but kept for null-safety/readability per the plan's guidance). Pre-existing
  deprecation/opt-in warnings elsewhere are out of scope.
- **Spool unit suite:** GREEN. `:app:testReleaseUnitTest` over all seven spool test classes
  (`PrintStartGateTest`, `QrPayloadParserTest`, `ScanStateMachineTest`, `SpoolmanModelParserTest`,
  `SpoolmanNotifyRouterTest`, `SpoolmanProxyParserTest`, `ZxingDecodeVersionTest`) →
  **BUILD SUCCESSFUL, exit code 0**.
- MeasuredWeightPage and ScanSurface are UI-callback-level with no direct unit test — expected;
  their correctness is by clean compile + review against the fix spec.

## Deviations from Plan

### Verify-command quirk (not a code change)

**1. [Rule 3 - Blocking] Gradle `--tests` package-wildcard `works.mees.dinghy.spool.*` returns "No tests found" on this AGP version.**
- **Found during:** running the plan's literal verify gate.
- **Issue:** Both `works.mees.dinghy.spool.*` and `works.mees.dinghy.spool.*Test` produced
  `No tests found for given includes`, failing the task with exit 1 — even though the seven
  spool test classes are present and compile (confirmed in
  `app/build/tmp/kotlin-classes/releaseUnitTest/works/mees/dinghy/spool/`). A
  fully-qualified single class (`...SpoolmanProxyParserTest`) ran fine, so it is a `--tests`
  glob-expansion quirk for a trailing package `.*`, not a test failure.
- **Fix:** ran the suite by listing all seven spool test classes explicitly via repeated
  `--tests` flags → BUILD SUCCESSFUL, exit 0. No source changed.
- **Files modified:** none.

No code deviations — all three fixes match the plan spec exactly.

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/ui/spool/MeasuredWeightPage.kt
- FOUND: app/src/main/java/works/mees/dinghy/spool/SpoolmanClient.kt
- FOUND: app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt
- FOUND commit: 782f2f5 (FIX 1)
- FOUND commit: 8db0953 (FIX 2)
- FOUND commit: ef465d4 (FIX 3)
