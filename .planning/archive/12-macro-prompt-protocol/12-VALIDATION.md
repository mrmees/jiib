---
phase: 12
slug: macro-prompt-protocol
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-04
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> The protocol's 26 renderer-neutral fixtures + the clean-room JS reference engine ARE the
> conformance oracle — the parser/reducer is validated fixture-for-fixture against the SAME
> contract every other frontend uses (D-14). See `12-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 (host unit tests) + Compose UI / instrumented tests (on-device) |
| **Config file** | `app/build.gradle.kts` (existing; no new framework — zero new deps) |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.prompt.*' --no-daemon"` (list classes explicitly — glob false-fails on this AGP) |
| **Full suite command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Estimated runtime** | ~30–90 seconds (host unit suite) |

> Build runs Windows-side via `E:\Android\gw.bat` (`./gradlew` won't run from WSL). Guard hung
> Gradle with `timeout` + `taskkill /F /T` (Windows java.exe children outlive the WSL process).

---

## Sampling Rate

- **After every task commit:** Run the quick command for the touched package.
- **After every plan wave:** Run the full host unit suite.
- **Before `/gsd-verify-work`:** Full host suite green + the on-device macro-examples.cfg run (manual gate).
- **Max feedback latency:** ~90 seconds (host suite).

---

## Per-Task Verification Map

> Planner fills one row per task during planning. The fixture-conformance task is the load-bearing
> automated gate; the on-device macro run is the load-bearing manual gate.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 12-01-01 | 01 | 1 | PROMPT-* | — | parser never throws on malformed/partial input | unit | `gw.bat :app:testDebugUnitTest --tests <ParserReducerTest>` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Port `fixtures/fixtures.json` (26 fixtures) into `app/src/test/resources/` + a parameterized
      conformance test (mirror the existing `GoldenFixtures` / parameterized-JUnit idiom).
- [ ] Add the `dinghy → ["touch"]` identity row so `expected_by_frontend` fixtures (e.g.
      `target-touch-only`) resolve under Dinghy's identity.
- [ ] RED scaffolds must compile day-one (typed stubs / `fail()` bodies, no refs to unbuilt symbols)
      — Gradle compiles the whole test sourceset before the `--tests` filter ([[dinghy-wave0-red-scaffold-compile]]).

*Existing JUnit + Compose-test infrastructure covers the framework; no new deps.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Close control → `prompt_end` echo round-trip closes the overlay | SC-3 | Requires the live Moonraker echo of the dispatched `action:prompt_end` back through the gcode stream — a fake stream can hide the round-trip (mock-vs-reality trap) | Run a `macro-examples.cfg` prompt on a real printer; tap Close; confirm the overlay closes via the echoed `prompt_end`, not a local teardown |
| Disconnect closes overlay locally WITHOUT emitting `prompt_end` (D-10) | SC-3 | Cross-client side effect; unit tests can't prove no gcode is dispatched on disconnect | With a prompt open, drop the printer connection; confirm overlay closes and NO `prompt_end` is sent (other clients keep the prompt) |
| Full load-filament wizard driven start-to-finish from the tablet | SC-4 | End-to-end on-device proof on real hardware (Adreno-320 fill-rate, button gcode firing) | Run the wizard macro on E5+ (192.168.1.120:7125) or E3 (192.168.1.121:7125); complete the flow tapping only the tablet |
| Image bounding / fill-rate on Adreno-320 | SC-2 | GPU fill-rate is the perf floor; can't be measured host-side | Render a `prompt_image` prompt on the real flox device; confirm no jank/OOM |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (fixture port + identity row)
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
