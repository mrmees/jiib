# Phase 21: WebRTC Camera Streaming - Context

**Gathered:** 2026-06-08
**Status:** Ready for planning

> ⚠ **Phase name is now a misnomer.** The roadmap titles this "WebRTC Camera Streaming," but the
> locked decision below is to ship **native Media3/ExoPlayer H.264** (over RTSP/HLS), NOT WebRTC.
> WebRTC is a *deferred* fallback. **Recommend renaming the roadmap phase** (e.g. "Native H.264
> Camera Streaming (MediaMTX)") at planning time. The *capability* — real camera for the printers
> whose only camera output is WebRTC-class H.264 — is unchanged; only the transport pivoted.

<domain>
## Phase Boundary

Deliver a **real, low-latency live camera view** for the developer's printers whose cameras emit
**WebRTC-class H.264** (which the existing Phase-10 MJPEG/snapshot ladder cannot play, dead-ending
them at Rung 3 "Unsupported"). The phase **extends the Phase-10 webcam rung ladder** with a new
**H.264 rung** decoded natively by **AndroidX Media3/ExoPlayer** over **RTSP (or HLS)** — same
hardware-decoded H.264 the cameras already produce, no native `libwebrtc` blob — perf-gated to the
Adreno-320 floor. Driven by Moonraker `/server/webcams/list`. Read-only viewing, capability-gated
BETA like the existing camera feature.

**In scope:** the Media3 H.264 rung + its placement/detection/URL-resolution in the existing ladder;
a **throwaway on-device latency spike** that gates the design; on-device UAT on both printers.
**Out of scope (this phase):** building the WebRTC/WHEP client (deferred — see D-03); fixing the
deferred webcam bugs as dedicated tasks (they are UAT-verified instead — see D-20).
</domain>

<decisions>
## Implementation Decisions

### Transport & the spike gate
- **D-01: Media3/ExoPlayer H.264 is the primary transport, NOT WebRTC.** The cameras emit H.264 over
  WebRTC AND expose the *same* H.264 over lighter transports (RTSP, HLS) that Media3 hardware-decodes
  with a far smaller footprint (modular, ~40% R8 shrink) and no multi-ABI `libwebrtc` AAR. This
  pivots off the roadmap's WebRTC assumption. Grounded in `research/webrtc-vs-media3-feasibility.md`
  (adversarially verified; footprint win proven, latency win NOT — hence the spike).
- **D-02: A throwaway on-device spike is Phase 21's FIRST plan.** Before the feature is staged/built,
  a throwaway spike runs on the **real Nexus 7 (flox)** against **both printers**, measuring
  **glass-to-glass latency, CPU/GPU load, and APK-size delta** for Media3-RTSP vs Media3-HLS (with
  WebRTC as a reference point). The downstream staging/feature design is **provisional until the
  spike reports**. The spike is throwaway code (not shipped).
- **D-03: WebRTC is built ONLY if the spike fails the latency bar.** Default: build the Media3 H.264
  rung(s) only; document the WebRTC/WHEP path as a **designed-but-unbuilt** higher rung. `libwebrtc`
  stays OUT of the APK **unless** the spike shows Media3-RTSP cannot meet the latency bar (D-05), in
  which case WebRTC re-enters scope as the escape hatch. This matches the project's footprint
  discipline and the rung-ladder "add a higher rung later" philosophy.
- **D-04: Spike decides RTSP-vs-HLS lead.** No predetermined primary transport; the spike's measured
  latency picks whether RTSP (`:8554`, TCP — lowest-latency candidate given the encoder config) or
  HLS (`:8888`) leads. The non-leading one becomes a robustness fallback rung.
- **D-05: Latency bar = ~1–2 s glass-to-glass (tight).** Near-real-time so the view is usable while
  jogging/manual moves, not just passive monitoring. RTSP-over-TCP with the cameras' encoder config
  (baseline/constrained-baseline profile, **no B-frames**, 1 s GOP) should clear it; plain HLS likely
  will not (LL-HLS maybe). This is the spike's pass/fail and the trigger for the D-03 WebRTC escape
  hatch. **Why native can hit this where browsers can't:** browser frontends (Mainsail/Fluidd) use
  WebRTC because HTML5 has no good low-latency non-WebRTC path (native `<video>` HLS ≈ Safari-only;
  Chrome needs hls.js+MSE w/ segment latency). Media3 is a *native* player — it decodes the RTSP pipe
  directly with none of the browser tax, so the browser's reason to prefer WebRTC does not bind us.

### Targets & "done"
- **D-06: Exactly two camera providers in scope — both MediaMTX-backed H.264.**
  1. **Ravens Perch** (PRIMARY) — the developer's own camera daemon, MediaMTX v1.5.1 backend, the
     dev controls it. Cross-repo: `/mnt/e/claude/personal/github/ravens-perch`.
  2. **crowsnest** (SECONDARY, "less flexibility") — also running a **MediaMTX** backend on the dev's
     printers; the dev cannot change its output metadata.
  No go2rtc / camera-streamer / ustreamer variance to chase — both targets are MediaMTX exposing the
  same RTSP/HLS/WebRTC pipes over the same H.264.
- **D-07: Camera codec is H.264 only, decode-friendly.** baseline / constrained-baseline profile,
  level 3.1, **no B-frames** (`-bf 0`), 1 s GOP, 640×480–1280×720 @ 5–30 fps — comfortably under the
  Adreno 320's confirmed 1080p H.264 hardware-decode ceiling. (Source: ravens-perch
  `daemon/stream_manager.py`.)
- **D-08: "Done" = the ~1–2 s bar (D-05) met on BOTH printers, on-device, for the leading transport,
  with clean codec/player release on screen exit.** Verified on flox against both SBCs.

### Rung ladder placement & stream-type detection
- **D-09: H.264 becomes the new top/preferred rung.** A cam offering both H.264 and MJPEG uses the
  lower-bandwidth hardware-decoded H.264. New fallback order (best→worst): **H.264 (Media3) → MJPEG
  (rung 1 today) → Snapshot (rung 2) → Unsupported (rung 3)**. The dev's MediaMTX cams have no MJPEG,
  so they take the H.264 rung; existing crowsnest/MJPEG setups elsewhere are undisturbed when they
  lack an H.264 pipe.
- **D-10: Detection = `service`/scheme heuristic SELECTS the rung, the decoder VERIFIES it.** Use
  `service` startsWith `webrtc` (de-facto `webrtc-mediamtx`) and/or the URL scheme to *select* the
  H.264 rung, but the rung **self-verifies**: ExoPlayer either decodes the stream or errors and falls
  to the next rung. This preserves the Phase-10 security rule (**the decoder is the authority,
  `service` is a hint only** — a hostile/wrong `service` can never route bytes into a fatal
  mis-decode; a bad guess just falls through, never renders garbage). The HTTP-Content-Type probe
  that authoritatively classifies MJPEG (`rungFor`) does NOT apply to RTSP (different protocol) /
  WHEP (a POST), so the heuristic-selects-decoder-verifies split is the reconciliation.

### URL resolution (the integration crux)
- **D-11: Moonraker's advertised `stream_url` points at WebRTC `:8889`, but Media3 needs RTSP
  `:8554` / HLS `:8888`.** The app must resolve a Media3-playable URL from a Moonraker entry that
  advertises the WebRTC pipe.
- **D-12: Resolution policy = explicit-if-present, else derive-from-convention.**
  - **Explicit:** Ravens Perch adds **new `extra_data` tags** (a ravens-perch namespace) carrying the
    RTSP/HLS URL for native clients. The app **prefers these tags when present.**
  - **Derive (fallback):** when no explicit tag (e.g. crowsnest), the app **derives** the RTSP/HLS
    URL from the `webrtc-mediamtx` convention — swap port `:8889`→`:8554` (RTSP) / `:8888` (HLS),
    reuse the `/<camera_id>` path (path name = `id.replace(' ','_').lower()`).
- **D-13: ravens-perch KEEPS its standard Moonraker fields unchanged.** `stream_url` stays the WebRTC
  `:8889/<id>/` URL and `service` stays `webrtc-mediamtx` so **browser frontends (Mainsail/Fluidd)
  remain usable**. The native-client RTSP/HLS info is **additive `extra_data`**, never a replacement
  of the standard fields. Browser and native app coexist on one Moonraker webcam entry.
- **D-14: Cross-repo dependency is non-blocking.** The ravens-perch `extra_data` tag work (D-12
  explicit path) is a *separate* change in `/mnt/e/claude/personal/github/ravens-perch`. dinghy is
  **NOT blocked** on it because the derive fallback (D-12) covers both targets today; dinghy simply
  prefers the explicit tags once ravens-perch ships them. Planner should treat the explicit-tag path
  as an enhancement layered on the convention-derive baseline.

### Webcam bugs (deferred — verified, not fixed)
- **D-20: No dedicated fixes for the 3 deferred webcam todos; fold them into Phase-21 UAT as
  verification checks.** Rationale (owner): the `webcam-screen-crash` was almost certainly the
  symptom of the screen hitting a WebRTC-only / bad-protocol cam it couldn't handle — adding real
  H.264 support *is* the fix. So Phase-21 UAT must verify on-device, on both printers, that the
  rebuilt screen: (a) **no longer crashes** on the MediaMTX cameras that previously crashed it;
  (b) the **drawer tile gates** correctly; (c) **per-printer camera selection holds** (the WR-03
  null-key edge). If UAT surfaces a *real* remaining failure → it becomes a gap then; otherwise the
  H.264 work retired them for free. The two non-protocol items (WR-03 null-key idle-edge,
  tile-gating) otherwise remain Phase-22 ship-hardening if UAT passes.

### Claude's Discretion
- Media3 module selection / version pinning (modular `media3-exoplayer` + the chosen source module,
  R8 keep rules) — research/planning per the CLAUDE.md stack guidance; not a user decision.
- Exact spike harness shape (throwaway Activity/screen, how latency is measured on-device — e.g. a
  visible clock filmed by the camera, or timestamp overlay) — planner/spike author's call.
- Multi-camera UX inherits Phase-10's `preferred_cam` selection; no new decision needed.

### Reviewed Todos (folded into UAT, not as fix-tasks) — see D-20
- `2026-06-05-webcam-screen-crash.md` → UAT regression check (likely retired by H.264 support).
- `2026-06-05-webcam-tile-gating-verification.md` → UAT gating check.
- `2026-06-05-phase-14-review-deferred-wr02-wr03.md` (WR-03 webcam per-printer null-key) → UAT
  per-printer-selection check; WR-02 (idle seedTheme) is unrelated → stays Phase-22.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase-21 research (the pivot rationale — READ FIRST)
- `.planning/phases/21-webrtc-camera-streaming/research/webrtc-vs-media3-feasibility.md` — the
  adversarially-verified feasibility study that drives D-01..D-05. Media3-over-H.264 vs WebRTC on the
  Adreno 320; the unsettled latency question the spike must answer; refuted claims (do NOT rely on
  Media3-RTSP "2-3s" or "H.264-only/SPS-PPS-in-fmtp" — test on-device).

### Existing webcam ladder (Phase 10 — the surface being extended)
- `app/src/main/java/works/mees/dinghy/state/WebcamModels.kt` — the `Webcam` tolerant model +
  `Rung` enum (D-01/D-02 three-rung ladder) + `rungFor` pure selector (Content-Type-as-authority).
- `app/src/main/java/works/mees/dinghy/net/WebcamProbe.kt` — the GET Content-Type probe + rung
  selection; where the new H.264 rung selection slots in (D-09/D-10).
- `app/src/main/java/works/mees/dinghy/net/WebcamUrl.kt` — stream/snapshot URL resolution (D-09 base,
  relative/localhost handling — where derive-from-convention (D-12) lands).
- `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt`,
  `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt`,
  `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamPrefs.kt` — the screen, holder lifecycle
  (clean release on exit), and per-printer `preferred_cam` selection (WR-03 edge).
- `app/src/main/java/works/mees/dinghy/net/MjpegStreamDecoder.kt`,
  `app/src/main/java/works/mees/dinghy/net/SnapshotPoller.kt`,
  `app/src/main/java/works/mees/dinghy/render/WebcamView.kt` — the MJPEG/snapshot rungs that drop
  beneath the new H.264 rung; render surface (the H.264 player surface should mirror its
  AndroidView/lifecycle hosting).
- `.planning/phases/10-webcam-streaming/10-CONTEXT.md` + `10-0*-SUMMARY.md` — the D-01/D-02 rung
  decisions, the "Content-Type is truth, service is hint" security stance (T-10-06), and tile-gating.

### Cross-repo target source-of-truth (Ravens Perch — outside the dinghy repo)
- `/mnt/e/claude/personal/github/ravens-perch/daemon/moonraker_client.py` — the Moonraker
  registration: hardcoded `service: "webrtc-mediamtx"`, `build_stream_url` (→ `:8889/<id>/`),
  `build_snapshot_url` (→ `http://host/cameras/snapshot/<id>.jpg?token=…`), `extra_data` usage
  (D-11/D-12/D-13: where the new RTSP/HLS tags would be added).
- `/mnt/e/claude/personal/github/ravens-perch/daemon/stream_manager.py` — MediaMTX path convention
  (`_path_name`), endpoint ports (RTSP 8554 / WebRTC 8889 / HLS 8888), and the H.264 encoder config
  (baseline/no-B-frames/1s-GOP — D-07).
- `/mnt/e/claude/personal/github/ravens-perch/HANDOFF_2026-06-04.md` — current state (branch
  `fix/mediamtx-service-lifecycle`; **no v2 rewrite** — supersedes the stale dinghy-memory "ravens-
  perch-v2 is current" note); real deployment resolutions/fps.

### Roadmap / requirements / stack
- `.planning/ROADMAP.md` § Phase 21 — goal + the rename note (this CONTEXT supersedes the WebRTC
  framing per D-01).
- `.planning/REQUIREMENTS.md` — CAM-01 (Phase-10, Complete) continues here as the CAM-* WebRTC/H.264
  extension (TBD req IDs coined at planning).
- `CLAUDE.md` § "Camera note (later phase — not core)" + Technology-Stack Media3/Vico notes — the
  pre-committed stack guidance (MJPEG-first done; this is the H.264 extension; defer libwebrtc).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **The Phase-10 rung ladder** (`Rung` enum, `WebcamProbe`, `WebcamHolder`): the new H.264 rung is an
  *addition above* it, not a rewrite. Reuse the holder's session-scoped lifecycle + clean-release
  discipline for the ExoPlayer instance (release codec on screen exit — same contract as the MJPEG
  decoder / `WebcamView` `onDispose`).
- **`WebcamUrl` resolution + `Webcam.extraData: JsonObject`**: already parsed and carried — the D-12
  explicit-tag read is a field lookup on existing `extra_data`; the derive-from-convention is a URL
  transform alongside existing relative/localhost handling.
- **`preferred_cam_<profileId>` per-printer selection** (WebcamPrefs): inherited multi-cam UX; the
  WR-03 null-key edge is verified in UAT (D-20).
- **The capability-gated BETA tile + amber flagging** (AppShell webcam-tile gate, D-08 in Phase 15.2):
  the H.264 feature stays behind the same gating.

### Established Patterns
- **"Decoder is the authority, `service` is a hint"** (T-10-06) — preserved by D-10
  (heuristic-selects, decoder-verifies).
- **Tolerant, never-fabricate model decode** (`MoonrakerJson` lenient, `Webcam` all-nullable, empty
  list → greyed tile) — the H.264 rung must not break the fail-safe-to-greyed-tile contract.
- **AndroidView-hosted render surface** (`WebcamViewHost`) — the ExoPlayer `PlayerView`/`SurfaceView`
  hosts the same way (Compose ↔ classic-Views hybrid, ADR-0001).

### Integration Points
- `/server/webcams/list` → `Webcam` model (existing) → **new H.264 rung selection** (D-09/D-10) →
  **URL resolution** (D-12) → **Media3 player** (new) → `WebcamView`/`PlayerView` render.
- Cross-repo: ravens-perch `moonraker_client.py` `extra_data` tags ⇄ dinghy `Webcam.extraData` read
  (D-12/D-14) — non-blocking (derive fallback covers it).
</code_context>

<specifics>
## Specific Ideas

- **Spike how-to:** measure *glass-to-glass* latency on the real Nexus 7 — the only thing that
  settles RTSP-vs-HLS-vs-WebRTC (no verified source pins it). Against BOTH printers (RPi 4 / Ender 5
  Plus + RockPro64 / Ender 3), both MediaMTX. Capture latency + CPU/GPU load + APK-size delta.
- **Encoder reality (already ideal):** baseline/constrained-baseline, no B-frames, 1 s GOP — the
  easiest possible H.264 for low-latency hardware decode. RTSP is over **TCP** (no UDP) — Media3 RTSP
  must be configured for TCP interleaving; verify stability on LineageOS API 30 (a refuted-claim area
  — test, don't assume).
- **Snapshot auth:** ravens-perch stream pipes (RTSP/HLS/WebRTC) are **open** (no token); only the
  snapshot URL carries `?token=` (already handled by the rung-2 snapshot poller).
- **Owner controls the primary target** — if any app-side need arises (extra metadata, an alternate
  endpoint), ravens-perch can add it via additive `extra_data` (D-12/D-13), never breaking browsers.
</specifics>

<deferred>
## Deferred Ideas

- **WebRTC/WHEP client** (`io.github.webrtc-sdk:android`, stripped variant, POST SDP offer to go2rtc/
  MediaMTX `/api/webrtc`) — designed-but-unbuilt fallback rung; built ONLY if the spike fails the
  ~1–2 s bar (D-03/D-05). Otherwise a future phase if a real camera ever needs it.
- **H.265/HEVC** — not confirmed hardware-decodable on the APQ8064; cameras are H.264 anyway. Out.
- **WR-02 idle `seedTheme` re-seed** (from `2026-06-05-phase-14-review-deferred-wr02-wr03.md`) —
  unrelated to webcams; stays Phase-22 ship-hardening.
- **Roadmap phase rename** ("WebRTC Camera Streaming" → "Native H.264 Camera Streaming (MediaMTX)") —
  apply at planning; tracked here, not a scope item.

### Reviewed Todos (not folded as fix-tasks — verified in UAT instead, see D-20)
- `2026-06-05-webcam-screen-crash.md` — UAT regression check (likely retired by H.264 support).
- `2026-06-05-webcam-tile-gating-verification.md` — UAT gating check.
- `2026-06-05-phase-14-review-deferred-wr02-wr03.md` — WR-03 webcam null-key → UAT per-printer-
  selection check; WR-02 unrelated → Phase-22.
</deferred>

---

*Phase: 21-webrtc-camera-streaming*
*Context gathered: 2026-06-08*
