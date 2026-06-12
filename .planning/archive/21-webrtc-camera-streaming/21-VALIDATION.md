---
phase: 21
slug: webrtc-camera-streaming
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-08
---

# Phase 21 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `21-RESEARCH.md` § Validation Architecture: the load-bearing
> gates for this phase are **on-device UAT on both printers** (latency, decode,
> no-leak). Green host unit tests CANNOT close this phase — they cover only the
> pure logic (rung selection, URL derive-from-convention, lifecycle state,
> outcome mapping). The **D-02 spike** is the gating first measurement; the
> shipped-rung design CONSUMES its recorded verdict.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 + Robolectric (host unit tests, `app/src/test`) |
| **Config file** | `app/build.gradle.kts` (existing test sourceset) |
| **Quick run command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests *Webcam* --no-daemon"` |
| **Full suite command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Estimated runtime** | ~60–120 s host unit suite (Windows-side via gw.bat; pipe through `tr -d '\r'`, exit code authoritative) |

*(Build runs Windows-side via `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat …"` — see CLAUDE.md Local Build Environment. `./gradlew` does NOT run from WSL.)*

---

## Sampling Rate

- **After every task commit:** Run quick command (`*Webcam*` filter)
- **After every plan wave:** Run full unit suite + `:app:assembleRelease :app:verifyMinSdkRelease` (floor + R8 keep rules survive media3)
- **Before `/gsd-verify-work`:** Full host suite green + **the D-02 spike readout (`21-SPIKE-RESULT.md`)** + **on-device UAT on both printers (`21-UAT.md`)**
- **Max feedback latency:** ~120 s for the host unit slice; the load-bearing on-device gates are owner-paced

---

## Per-Task Verification Map

*UAT-only criteria (latency, decode, no-leak, "proven on real printer") are in the Manual-Only table below, not here.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 21-01-01 | 01 | 1 | CAM-11/13/14/16 | T-21-01-01 | media3 added, merged-manifest minSdk stays 23 | build/floor | `gw.bat :app:verifyMinSdkRelease` | ✅ existing task | ⬜ pending |
| 21-01-02 | 01 | 1 | CAM-11/13/14/16 | — | RED scaffolds compile, fail (not error), reference no unbuilt symbol | unit (scaffold) | `gw.bat :app:compileDebugUnitTestKotlin` | ❌ W0 (this task creates) | ⬜ pending |
| 21-02-01 | 02 | 2 | CAM-10 | T-21-02-03 | spike builds ExoPlayer (RTSP forced-TCP + HLS), releases on exit | build | `gw.bat :app:assembleDebug` | ❌ (throwaway) | ⬜ pending |
| 21-02-02 | 02 | 2 | CAM-10 | T-21-02-01/02 | recorded DECISION (lead/fallback/WebRTC-escape/cutout) | **UAT-only** | manual on flox (spike) | n/a | ⬜ pending |
| 21-03-01 | 03 | 3 | CAM-11/12 | T-21-03-01 | Rung.H264 tier-0; selectsH264Rung pure HINT; rungFor stays service-blind | unit | `gw.bat :app:testDebugUnitTest --tests *RungSelectTest` | ❌ W0 (03 fills) | ⬜ pending |
| 21-03-02 | 03 | 3 | CAM-13 | T-21-03-02/03 | derive swaps port keeps path (not name); explicit-tag preferred; null fail-safe | unit | `gw.bat :app:testDebugUnitTest --tests *WebcamUrlDeriveTest` | ❌ W0 (03 fills) | ⬜ pending |
| 21-04-01 | 04 | 4 | CAM-14/16 | T-21-04-01/04 | PlaybackException→FeedOutcome; forceUseRtpTcp; main-thread; leak-free | unit | `gw.bat :app:testDebugUnitTest --tests *Media3FeedOutcomeTest --tests *RtspSourceConfigTest` | ❌ W0 (04 fills) | ⬜ pending |
| 21-04-02 | 04 | 4 | CAM-12/15 | T-21-04-01/02 | H.264 feed plugs into reconnect machine: Terminal→next rung; spike deleted | unit | `gw.bat :app:testDebugUnitTest --tests *WebcamReconnectStateTest` | ⚠ extend existing | ⬜ pending |
| 21-05-01 | 05 | 5 | CAM-17 | — | release-class build installs on flox; 21-UAT.md scaffolded | build | `gw.bat :app:assembleRelease` | n/a | ⬜ pending |
| 21-05-02 | 05 | 5 | CAM-17 | T-21-05-01/02 | live H.264 + latency + no-leak + fallback + D-20 fold-ins, both printers | **UAT-only** | manual on flox (both printers) | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `RungSelectTest.kt` — H.264 rung-selection heuristic; `rungFor` stays H264-free (CAM-11/12, D-10) — created in 21-01, filled green in 21-03
- [ ] `WebcamUrlDeriveTest.kt` — derive `:8889`→`:8554`/`:8888` parsing the path from stream_url NOT name (the landmine) + explicit-tag precedence (CAM-13) — created in 21-01, filled green in 21-03
- [ ] `Media3FeedOutcomeTest.kt` — PlaybackException→FeedOutcome (network→Transient, decoder-init→Terminal) (CAM-14) — created in 21-01, filled green in 21-04
- [ ] `RtspSourceConfigTest.kt` — RtspMediaSource.Factory built with forceUseRtpTcp (CAM-16) — created in 21-01, filled green in 21-04
- [ ] Extend `WebcamReconnectStateTest` — H.264 feed plugs into the existing reconnect machine (Terminal→next rung) (CAM-12) — extended in 21-04
- [ ] Throwaway `SpikeActivity` — NOT a test; the D-02 measurement harness (created 21-02, deleted 21-04)

*Wave-0 RED stubs MUST compile day-one (project memory: wave-0 RED scaffold compile rule). The 21-01 scaffolds use `fail()` bodies referencing ONLY existing symbols (Webcam model, FeedOutcome, Rung) — NOT the unbuilt selectsH264Rung/deriveNativeStreamUrl/Rung.H264/NativeTransport — so the whole test sourceset compiles before 03/04 land.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Glass-to-glass latency meets ~1–2 s bar (D-05) | CAM-10 (spike), CAM-17 (UAT) | Inherently on-device; no verified source pins it (D-02 spike settles, D-08 confirms) | Spike on flox vs both printers (clock-on-camera, median of ~10); confirm in UAT |
| RTSP-vs-HLS lead decided (D-04) | CAM-10 | Only measured latency picks it | Spike records the DECISION in `21-SPIKE-RESULT.md` |
| MediaMTX RTSP SDP carries SPS/PPS in fmtp (highest-risk unknown) | CAM-10 | Protocol-level, on-device only | Spike logs the RTSP `prepare()` error / `ffprobe` the SDP; absence → HLS leads |
| Hardware decoder selected (OMX.qcom not OMX.google) | CAM-10/17 | MediaCodec selection is a runtime/device property | `MediaCodecList`/logcat on flox; CPU% steady (no software-fallback jank) |
| Live H.264 renders, correct aspect, no frozen frames | CAM-17 | Requires real MediaMTX camera + hardware decode on Adreno 320 | On-device UAT on Ender 5 Plus + Ender 3 |
| Clean codec/player release on screen exit (no leaked codec) | CAM-14/17 | Leak observable only at runtime | Enter/exit the webcam screen ~5×; no rising memory / frozen feed on re-entry |
| Falls back to MJPEG/snapshot when H.264 not offered | CAM-17 | Cross-camera behavior | UAT on a non-H.264 cam |
| No-crash regression on the MediaMTX cams that previously crashed the screen (D-20) | CAM-17 | Reproduces only against the real cams | On-device UAT both printers |
| Drawer tile gates correctly (D-20) | CAM-17 | Live gating state | On-device UAT (greyed when unavailable, live when available) |
| Per-printer camera selection holds — WR-03 null-key edge (D-20) | CAM-17 | Multi-printer runtime state | Switch printers on flox; each retains its own preferred cam |
| crowsnest `stream_url` matches the derive convention (RESEARCH A2) | CAM-13/17 | crowsnest metadata not dev-controllable | UAT sub-check on the Ender 3 |
| SurfaceView rounded-cutout chrome aesthetic | CAM-15 | Owner aesthetic call (hard owner law — never silently pick) | Owner decides in the spike review; implemented per the recorded call in 21-04 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (the two checkpoint tasks are UAT-only by design — spike + on-device gate)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (every auto task has a gw.bat verify; the two checkpoints are the inherently-on-device gates)
- [x] Wave 0 covers all MISSING references (4 new test files + 1 extend, created in 21-01, filled in 21-03/04)
- [x] No watch-mode flags
- [ ] Feedback latency < ~120s (host slice) — confirmed at execution
- [ ] `nyquist_compliant: true` set in frontmatter — set after Wave-0 scaffolds land + the spike/UAT gates are recorded

**Approval:** pending (flip `nyquist_compliant: true` once 21-01 scaffolds compile-and-fail and the per-task map rows go green / UAT recorded)
