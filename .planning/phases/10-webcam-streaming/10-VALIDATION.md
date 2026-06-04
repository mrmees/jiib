---
phase: 10
slug: webcam-streaming
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-03
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `10-RESEARCH.md` § Validation Architecture (live-probe-verified 2026-06-03).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 + kotlinx-coroutines-test (JVM unit); Compose UI test / instrumented (`androidTest`) for the View + drawer gating; on-device `gfxinfo` for the perf gate |
| **Config file** | Gradle (`app/build.gradle.kts`); fixtures under `app/src/test/resources/fixtures/`, goldens under `app/src/test/resources/golden/` (existing conventions) |
| **Quick run command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` (pipe through `tr -d '\r'`; guard with `timeout` + taskkill per build-env memory) |
| **Full suite command** | `… "E:\Android\gw.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon"` + on-device `gfxinfo` perf capture |
| **Estimated runtime** | ~60–90 s JVM unit; instrumented + on-device gate adds device time |

---

## Sampling Rate

- **After every task commit:** Run the relevant `*Test*` class(es) from the map below (quick JVM run)
- **After every plan wave:** Run `:app:testDebugUnitTest` (full unit suite)
- **Before `/gsd-verify-work`:** Full unit suite green + `connectedDebugAndroidTest` green + on-device flox UAT (snapshot ladder on E3, rung-3 card on the WebRTC streams, `gfxinfo` 0-frozen-frames during ~15 s decode)
- **Max feedback latency:** ~90 s (JVM unit); device gates are phase-gate-only

---

## Per-Task Verification Map

| Req / SC | Behavior | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|----------|----------|------------|-----------------|-----------|-------------------|-------------|--------|
| CAM-01 / enumerate | Parse `/server/webcams/list` golden frames (E5+E3 verbatim, all 16 fields, blank `service`, missing snapshot) → correct cam models | T-V5 | Untrusted fields tolerated (`ignoreUnknownKeys`, nullable); fail safe, never crash | unit | `…--tests *WebcamListParseTest*` | ❌ W0 | ⬜ pending |
| CAM-01 / D-02 sniff | `webrtc-mediamtx` / blank / `image/jpeg` / `multipart` Content-Types route to the correct rung (1/2/3) | — | Content-Type is source of truth, `service` only a hint | unit | `…--tests *WebcamRungSelectTest*` | ❌ W0 | ⬜ pending |
| CAM-01 / D-09 | Relative URL resolves against host; `127.0.0.1`/`localhost` rewritten to host; `?token=` preserved | T-V7 | Resolved URL never logged (redact `?token=…`) | unit | `…--tests *WebcamUrlResolverTest*` | ❌ W0 | ⬜ pending |
| SC-2 / decode | Mock MJPEG byte stream (WITH and WITHOUT Content-Length; JPEG split across reads) yields N frames; one reused bitmap, no per-frame alloc | T-V5 | Cap part size; reject absurd Content-Length (OOM-by-hostile-frame) | unit | `…--tests *MjpegStreamDecoderTest*` | ❌ W0 | ⬜ pending |
| SC-2 / no-OOM/no-jank | Real decoder on flox over a captured MJPEG byte stream / live snapshot loop; `gfxinfo` p95 + **0 frozen frames** during ~15 s continuous decode | — | `inSampleSize` downscale always | on-device (manual + gfxinfo) | `adb shell dumpsys gfxinfo works.mees.dinghy` (reset→decode→dump) | ❌ W0 | ⬜ pending |
| SC-1 / drop-behind | A slow consumer never backs up the decoder (conflated latest-wins) | — | N/A | unit | `…--tests *FrameDropBehindTest*` | ❌ W0 | ⬜ pending |
| SC-3 / lifecycle | Loops start on STARTED, cancel on STOPPED / nav-away; no work in background | T-DoS | Page-visible-only loops; cancel on background | unit (virtual time) + instrumented | `…--tests *WebcamLifecycleTest*` | ❌ W0 | ⬜ pending |
| D-11 | Transient stall keeps last frame dimmed + "Reconnecting…"; does not clear to error | — | N/A | unit (holder state machine) | `…--tests *WebcamReconnectStateTest*` | ❌ W0 | ⬜ pending |
| D-12 | Backoff 1 s→10 s while foreground; 401/403 terminal-for-cam (no spin) | T-DoS | Auth failure terminal, not transient (protects weak SBC host) | unit | `…--tests *WebcamBackoffTest*` | ❌ W0 | ⬜ pending |
| SC-4 | WebRTC / `webrtc-mediamtx` cam → rung-3 dead-end card naming the service, no browser button | — | N/A | unit + instrumented | `…--tests *WebcamUnsupportedCardTest*` | ❌ W0 | ⬜ pending |
| D-08 | Drawer Webcam tile greyed when 0 cams, live when ≥1 | — | N/A | instrumented | `…connectedDebugAndroidTest --tests *DrawerWebcamGatingTest*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/resources/golden/webcams_list_e5.json` / `webcams_list_e3.json` — capture verbatim (the RESEARCH.md § Code Examples blocks are the source)
- [ ] `app/src/test/resources/fixtures/mjpeg_with_content_length.bin` + `mjpeg_no_content_length.bin` + `mjpeg_split_jpeg.bin` — golden MJPEG byte streams (synthesize a 2–3-frame `multipart/x-mixed-replace` body; the **only** place a synthetic stream is justified since no live MJPEG cam exists on E5/E3 — clearly label it synthetic)
- [ ] `FakeMjpegStream` / `FakeWebcamHttp` test doubles **hardened to the real contract** (no-Content-Length path, split JPEG, 401, 404 `text/plain`, relative + `127.0.0.1` URLs) — guards against the project's recurring mock-vs-reality gap
- [ ] RED scaffolds for the test classes above
- [ ] No framework install needed (JUnit / coroutines-test / Compose-test already present)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| No-OOM / no-jank during sustained decode on the Adreno-320 floor | SC-2 | Perf is the system-of-record on real hardware (ADR-0001 Add. 2); emulators lie about old-GPU fill rate | On flox: reset `gfxinfo`, run a ~15 s snapshot/MJPEG decode loop, dump `gfxinfo works.mees.dinghy`; require p95 within budget + **0 frozen frames** |
| Live MJPEG (rung-1) acceptance | SC-1 | No live MJPEG cam exists on E5/E3 (both run ravens-perch as `webrtc-mediamtx`) — golden-byte-stream unit tests are the authoritative proof; owner may stand up a temp crowsnest cam | If a crowsnest cam is available, point Dinghy at it and confirm a live moving feed; otherwise rely on `MjpegStreamDecoderTest` goldens |
| Snapshot-ladder live UAT | SC-1 / D-03 | The primary on-device-demoable path on this project's hardware (E3 token snapshot → 200 image/jpeg) | On flox + E3 (192.168.1.121): open Webcam, confirm ~2 fps snapshot feed + persistent "Snapshot ~2fps" badge |
| Rung-3 dead-end card on real WebRTC cam | SC-4 / D-04 | E5 + E3 ravens-perch streams are `webrtc-mediamtx`; E5 snapshot is 401 (no creds) — both must show the rung-3 card on-device (NOT a bug) | On flox: select an E5 cam, confirm the card names the WebRTC service, no browser button |

---

## Validation Sign-Off

- [ ] All tasks have an `<automated>` verify or a Wave 0 dependency
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (goldens, MJPEG fixtures, hardened fakes, RED scaffolds)
- [ ] No watch-mode flags
- [ ] Feedback latency < 90 s (JVM unit)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
