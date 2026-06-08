# Phase 21: WebRTC Camera Streaming - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-08
**Phase:** 21-webrtc-camera-streaming
**Areas discussed:** Transport + spike gate, Rung placement + detection, Target cams + "done", Fold in webcam bugs
**Key input:** `research/webrtc-vs-media3-feasibility.md` (owner-supplied; reviewed and folded in)

---

## Transport + spike gate

| Option | Description | Selected |
|--------|-------------|----------|
| Accept pivot, spike-decides | Media3 H.264 primary, WebRTC fallback, spike latency final say | ✓ |
| Media3-primary, firm | Commit to Media3 now, WebRTC only if spike fails | |
| Stick with WebRTC | Keep roadmap's WebRTC/WHEP-primary | |
| WebRTC-only, drop Media3 | Single WebRTC path, full libwebrtc footprint | |

| Option | Description | Selected |
|--------|-------------|----------|
| Separate /gsd-spike first | Throwaway spike BEFORE staging | |
| Fold spike into Phase 21 | Spike = Phase 21 plan 1; staging provisional until it reports | ✓ |
| Skip the spike | Commit transport without measurement | |

| Option | Description | Selected |
|--------|-------------|----------|
| Media3 only, defer WebRTC | Build H.264 rung; WebRTC designed-but-unbuilt | ✓ |
| Build both now | Media3 + WebRTC fallback this phase | |
| Spike outcome decides | Build WebRTC only if spike finds a Media3-incompatible cam | |

**User's choice:** Media3 H.264 primary; spike folded into Phase 21 as plan 1; build Media3 only, defer WebRTC.
**Notes:** Refined to a conditional: WebRTC stays deferred UNLESS the spike shows Media3-RTSP can't meet the ~1–2s bar (escape hatch). Roadmap phase name "WebRTC Camera Streaming" is now a misnomer → rename recommended.

---

## Rung placement + detection

| Option | Description | Selected |
|--------|-------------|----------|
| MJPEG stays preferred | H.264 below MJPEG; only catches no-MJPEG cams | |
| H.264 preferred when present | H.264 = new top rung | ✓ |
| User-selectable per cam | Per-camera transport pref in settings | |

| Option | Description | Selected |
|--------|-------------|----------|
| Heuristic picks, decoder verifies | service/scheme selects rung, ExoPlayer decode is authority | ✓ (implied) |
| Strict probe first | Active RTSP DESCRIBE / fMP4 HEAD before selecting | |
| You decide | Defer to research/planning | |

**User's choice:** H.264 preferred/top rung. Detection: heuristic-selects-decoder-verifies (carried from the security discussion; user's free-text instead surfaced the two-provider scope).
**Notes:** User redirected the detection question to scope — "only two providers I'm worried about: Ravens Perch (mine, top priority) and crowsnest (second, less flexible)." Both later confirmed MediaMTX-backed. Detection tuned to those two's `webrtc-mediamtx` convention rather than a generic camera zoo.

---

## Target cams + "done"

| Option | Description | Selected |
|--------|-------------|----------|
| (resolved via free-text) | Ravens Perch (MediaMTX, owner-controlled) + crowsnest (MediaMTX) | ✓ |

**User's choice:** Two targets, both MediaMTX/H.264. Ravens Perch built on MediaMTX → can expose anything MediaMTX does (RTSP/HLS/WebRTC). crowsnest backend also MediaMTX. Webcam settings pulled from Moonraker; owner can add to the ravens-perch namespace in the Moonraker DB.
**Notes:** Drove the Explore review of `/mnt/e/claude/personal/github/ravens-perch` — confirmed endpoints (RTSP 8554 / WebRTC 8889 / HLS 8888), H.264 baseline/no-B-frames/1s-GOP, `service: "webrtc-mediamtx"`, and that the registered `stream_url` points at the WebRTC port (the integration crux). No v2 rewrite (corrects stale memory).

### URL resolution (sub-decision)

| Option | Description | Selected |
|--------|-------------|----------|
| Explicit-if-present, else derive | Prefer ravens-perch extra_data tag, else derive :8554/:8888 from convention | ✓ |
| ravens-perch advertises it | Change stream_url/extra_data directly | |
| App derives from convention only | Always map by port+path convention | |

| Option | Description | Selected |
|--------|-------------|----------|
| RTSP first, HLS fallback | RTSP-over-TCP lead | |
| HLS/LL-HLS first | HLS lead | |
| Spike decides which leads | Measured latency picks RTSP vs HLS | ✓ |

| Option | Description | Selected |
|--------|-------------|----------|
| ~2-4s, watch-a-print | Comfortable monitoring bar | |
| ~1-2s, tight | Near-real-time, usable while jogging | ✓ |
| Qualitative, no hard number | Judge by feel | |

**User's choice:** Explicit-if-present-else-derive; spike decides RTSP-vs-HLS lead; ~1–2s tight latency bar.
**Notes:** Owner clarified the browser-WebRTC rationale (HTML5 has no low-latency non-WebRTC path) and confirmed it doesn't bind a native player. Owner will add native-client RTSP/HLS tags to the ravens-perch `extra_data` namespace while KEEPING the standard WebRTC Moonraker fields so browser frontends stay usable. Cross-repo dependency noted as non-blocking (derive fallback).

---

## Fold in webcam bugs

| Option | Description | Selected |
|--------|-------------|----------|
| Crash only; defer the 2 minor | Fix webcam-screen-crash here | |
| Fold all three | Crash + null-key + tile-gating | |
| Defer all to Phase 22 | Strictly the H.264 feature | |
| (free-text) | Don't fix explicitly; add to UAT verification | ✓ |

**User's choice:** Don't fix the webcam bugs explicitly in Phase 21 — add them to the UAT.
**Notes:** Owner reasoning: the crashes were almost certainly the symptom of bad video-protocol usage (WebRTC-only cam hitting Rung 3), so the H.264 support IS the fix. UAT verifies on-device that the rebuilt screen no longer crashes on the MediaMTX cams, gates the tile correctly, and holds per-printer selection. Real remaining failure → gap; otherwise retired for free.

## Claude's Discretion

- Media3 module selection / version pinning + R8 keep rules (research/planning, per CLAUDE.md stack).
- Spike harness shape + on-device latency measurement method.
- Multi-camera UX inherits Phase-10 `preferred_cam`.

## Deferred Ideas

- WebRTC/WHEP client — built only if spike fails the ~1–2s bar.
- H.265/HEVC — not confirmed decodable; cameras are H.264 anyway.
- WR-02 idle seedTheme re-seed — unrelated to webcams; Phase-22.
- Roadmap phase rename → apply at planning.
