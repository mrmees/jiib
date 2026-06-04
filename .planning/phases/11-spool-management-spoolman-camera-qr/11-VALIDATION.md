---
phase: 11
slug: spool-management-spoolman-camera-qr
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-04
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `11-RESEARCH.md` §Validation Architecture. Mock-vs-reality discipline
> applies (7 prior strikes) — Wave-0 fakes MUST be hardened to the 23 live
> `docs/commands/spoolman-live-*.json` fixtures.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 (JVM unit) + AndroidJUnitRunner (instrumented) — both already pinned |
| **Config file** | `app/build.gradle.kts` test/androidTest source sets (no separate config) |
| **Quick run command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Full suite command** | `… "E:\Android\gw.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon"` (guard with `timeout` + `taskkill /F /T` per the gradle-hang interop note) |
| **Estimated runtime** | ~60–120 s unit; instrumented adds device time |

---

## Sampling Rate

- **After every task commit:** Run the quick unit command (parser / gate / state-machine seams).
- **After every plan wave:** Run the full unit suite + relevant instrumented tests.
- **Before `/gsd-verify-work`:** Full suite green + on-device UAT (real lens, real ZXing version, real label) — then Codex final-plan review per standing policy.
- **Max feedback latency:** ~120 s (unit).

---

## Per-Task Verification Map

| Req ID | Behavior | Test Type | Automated Command (suffix after `gw.bat :app:testDebugUnitTest`) | File Exists | Status |
|--------|----------|-----------|------------------------------------------------------------------|-------------|--------|
| SPOOL-02/08 | proxy v2 envelope parser (`{response,error,response_headers}`, `X-Total-Count`) | unit (golden) | `--tests *SpoolmanProxyParserTest` | ❌ W0 | ⬜ pending |
| SPOOL-02 | null-safe spool/filament/vendor parser (omitted fields, color normalize, multi-color split) | unit (golden) | `--tests *SpoolmanModelParserTest` | ❌ W0 | ⬜ pending |
| SPOOL-04/08 | notify router fans out `notify_active_spool_set` / `notify_spoolman_status_changed`, ignores unrelated | unit | `--tests *SpoolmanNotifyRouterTest` | ❌ W0 | ⬜ pending |
| SPOOL-05 | QR payload parser — accept/reject classes (`s-` / `/spool/show/` URL / `f-` / UPC / non-numeric, case-insensitive) | unit | `--tests *QrPayloadParserTest` | ❌ W0 | ⬜ pending |
| SPOOL-05 | ZXing **3.3.3** decodes a known QR on the JVM (version-lock guard — Pitfall 1) | unit | `--tests *ZxingDecodeVersionTest` | ❌ W0 | ⬜ pending |
| SPOOL-07 | warn-only gate decision table (each D-01 condition → expected warning/pass) | unit | `--tests *PrintStartGateTest` | ❌ W0 | ⬜ pending |
| SPOOL-07 | `FilePreviewMetadata` lifts `filament_type[]`/`name[]`/`colors[]`/`weights[]` | unit | `--tests *FilePreviewMetadataTest` (extend) | ❌ W0 (extend) | ⬜ pending |
| SPOOL-06 | camera permission/degrade state machine (granted/denied/no-camera/busy/unreadable/no-QR/unsupported) | unit (headless) | `--tests *ScanStateMachineTest` | ❌ W0 | ⬜ pending |
| SPOOL-03/06 | scan surface releases camera on dispose; no-camera → picker | instrumented | `connectedDebugAndroidTest *ScanSurfaceLifecycleTest` | ❌ W0 | ⬜ pending |
| SPOOL-05/06 | **on-device end-to-end:** scan real label → confirm → active flips | manual (flox + live printer) | save→change→restore protocol | UAT | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `SpoolmanProxyParserTest` + golden from `docs/commands/spoolman-live-ender5-proxy-pla.json` (assert `X-Total-Count == "7"`, `error == null`)
- [ ] `SpoolmanModelParserTest` + goldens from `…-proxy-spool3.json` / `spoolman-live-direct-spool3-before.json` (null-safety, color normalize, multi-color split)
- [ ] `SpoolmanNotifyRouterTest` + golden from `…-ender5-notify.json` (1-element `params` array; ignore `notify_proc_stat_update`)
- [ ] `QrPayloadParserTest` (accept `web+spoolman:s-<id>` + uppercase `WEB+SPOOLMAN:S-` + `/spool/show/<id>` URL; reject `f-`, UPC, non-numeric, hostile host)
- [ ] `ZxingDecodeVersionTest` (locks the **3.3.3** pin — decodes a fixture QR on the JVM; would FAIL on 3.4.0+ via `List.sort` `NoSuchMethodError`)
- [ ] `PrintStartGateTest` (D-01 decision table — every condition, all warn-only, no hard block)
- [ ] `ScanStateMachineTest` (headless permission/degrade states)
- [ ] `FakeSpoolmanClient` / `FakeMoonrakerSession` hardened to the live fixtures (mock-vs-reality — 7th-strike prevention; replicate the proxy v2 envelope + 1-element notify array exactly)
- [ ] Wave-0 RED stubs MUST **compile** (`fail()` bodies, no refs to unbuilt symbols — a bad scaffold bricks the whole test source set)
- [ ] Add the two Spoolman notification entries to `docs/commands/moonraker-api.md` before wiring router parser tests (catalog gap)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real flox rear-lens decodes a real printed Spoolman label at scan distance | SPOOL-05 | ZXing decode reliability on Adreno-320 throttled frames + real optics cannot be asserted from docs (A2/A3) | On flox: open scan, point at a printed label, confirm decode→confirm→`notify_active_spool_set`; restore active spool after |
| End-to-end active-spool flip in Spoolman | SPOOL-05/SC-4 | Requires a live printer + Spoolman server | Load filament → scan label → verify active spool flips in Spoolman UI; restore baseline (E5=5 / E3=1) |
| No-camera degrade keeps manual pick working | SPOOL-06 | Requires a device path with camera unavailable | Deny CAMERA / no-camera build path → picker still loads + sets active |

---

## Validation Sign-Off

- [ ] All tasks have an `<automated>` verify or a Wave 0 dependency
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (parsers, QR, ZXing-version-lock, gate, state machine, hardened fakes)
- [ ] No watch-mode flags
- [ ] Feedback latency < 120 s (unit)
- [ ] `nyquist_compliant: true` set in frontmatter (at plan-check)

**Approval:** pending
