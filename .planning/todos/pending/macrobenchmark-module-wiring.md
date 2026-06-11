---
id: macrobenchmark-module-wiring
created: 2026-05-30
source: 01-REVIEW.md (CR-01)
priority: low
resolves_phase: null
---

# `:macrobenchmark` module can't profile the release variant as wired

CR-01: `:app` release is `isDebuggable = false` with no `<profileable android:shell="true"/>`, but
`:macrobenchmark` is wired to measure that release variant. AndroidX Macrobenchmark cannot collect
`FrameTimingMetric` from a non-debuggable, non-profileable app — so `:macrobenchmark` is currently
non-runnable against release.

This did NOT affect Phase 1's verdict: the toolkit decision used `dumpsys gfxinfo` framestats (the
D-07 system of record), driving `BenchActivity` directly. `FrameTimingMetric` was only ever
corroboration. Captured via gfxinfo instead; corroboration deferred.

If macrobenchmark corroboration is ever wanted: add a `profileable` tag (API 29+, fine on the API-30
device) or a dedicated `benchmark` build type, and a debug-signing config so the release installs.
Note: adding `profileable` edits the shared `AndroidManifest.xml` (owned by plan 01-01).

Low priority — only matters if the macrobenchmark FrameTimingMetric path is actually needed.

Also tracked separately: `verifyMinSdk` regex takes the first `minSdkVersion` match and doesn't
handle codename API levels (WR — robustness, not currently triggered since the floor is numeric 23).

Triage 2026-06-11: target Phase 29 — priority RAISED by 26.5 R9: flox runs LineageOS/API 30 where Baseline Profiles DO apply (the old NO-OP framing was stock-API-23-only); wire profileable + generate the profile with the release-build work.
