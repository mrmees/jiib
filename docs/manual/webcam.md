# Webcam (beta)

> **Beta:** Camera support is functional but carries a known v1 limitation — see the note at the bottom of this page.

Displays the live camera feed from your printer's registered webcam(s), sourced directly from Moonraker's `server.webcams.list`. Supports H.264 streams via MediaMTX (RTSP), live MJPEG streams, and snapshot-poll fallback.

**Getting there:** Home → Webcam tile in the action list. The tile only appears when at least one cam is registered by the connected Moonraker instance and the app-global Webcam toggle (App Settings → Webcam) is on.

## The screen

**Focus card** holds the camera feed. What it shows depends on decode state:

- **Live** — the active stream, either an H.264 SurfaceView (MediaMTX/RTSP cams) or a decoded MJPEG bitmap. A cam-name and service label overlays the feed.
- **Snapshot fallback** — a periodically-refreshed still frame, with a persistent corner badge reading "Snapshot ~2fps". Shown when the stream is not decodable as MJPEG but a `snapshot_url` is present.
- **Reconnecting** — the last-good frame, dimmed, with a centered "Reconnecting…" text overlay. Shown during transient stalls (Wi-Fi hiccup, mid-stream EOF) while the app backs off and retries (1 s to 10 s).
- **Dead end** — a static error card, no spinner. Shown when the cam is permanently unreachable for this session: HTTP rejection (401/403/404) with no snapshot fallback, no decodable stream and no snapshot URL, or an unsupported WebRTC-only cam.

**Field (cam picker)** is shown only when there is more than one cam AND the layout has room for it:

- Portrait device → picker below the feed.
- Landscape device with a portrait-aspect feed → picker beside the feed.
- Landscape device with a landscape-aspect feed → no picker; the feed fills the whole Focus region.

When the picker is hidden the feed is in **full-focus** mode.

**Foot bar:** Back (accent) — always present. In full-focus mode it is a full-width strip below the feed; in picker mode it appears at the bottom of the Field column.

## Options & controls

### Focus card

- **Live feed** — displays the decoded stream. No user controls on the surface itself.
  - H.264 path (cam service starts with `webrtc` or stream URL starts with `rtsp://`): video renders to a SurfaceView via ExoPlayer/Media3 over RTSP/TCP. The chrome (cam name, service, overlay glyphs) is drawn as a separate layer above the surface, not on it. If the H.264 decoder fails, the feed falls through to MJPEG/snapshot automatically without hitting the dead-end state.
  - MJPEG/snapshot path: frames are decoded as bitmaps and drawn via `WebcamViewHost`. The cam's Moonraker-configured `flip_horizontal`, `flip_vertical`, and `rotation` (0°, 90°, 180°, or 270°) are applied per frame. Any other `rotation` value folds to 0°.

- **Tap to cycle (full-focus, multiple cams only)** — when the cam picker is hidden and more than one cam is registered, tapping anywhere on the feed steps to the next cam in Moonraker list order. The feed shows a burst-glyph and the current cam's name as a tap-target cue. The selection is persisted immediately (see Cam picker below).

### Field — cam picker

- **Cam row** — each registered cam appears as a tappable row showing its name (in accent color when selected). The selected cam's row expands to show two additional details: service string (e.g. `mjpegstreamer`, `webrtc-mediamtx`) and aspect ratio (e.g. `16:9`) when the cam reports one.

  Tapping a different row switches the active feed to that cam and persists the choice for this printer profile. On next visit the last-used cam is pre-selected; if it is no longer listed, the first cam in list order is used instead. There is no separate "set as default" control — the preference updates automatically on every selection or cycle.

  > Selection is stored in `webcam.preferences_pb` keyed by the active printer profile's UUID, not by host address. Two profiles pointing at the same host keep independent preferred-cam settings.

- **Unnamed cam** — a cam with a blank Moonraker `name` field displays a placeholder label in the picker row.

### Foot bar

- **Back** (accent) — returns to the previous screen.

## Known limitation — H.264 and mid-playback rotation

Rotating the device while an H.264 cam is playing blanks the feed. The Adreno 320 decoder cannot renegotiate its output buffers into the resized SurfaceView mid-stream. The feed works correctly in both portrait and landscape on a **fresh screen entry**; blanking is not a crash and is fully recoverable by navigating away and back. Fix is deferred to a future release.

MJPEG and snapshot cams are not affected by this limitation.

## Related

[Concepts](concepts.md) — gating overlays, e-stop, connection state.  
[Home](home.md) — the Webcam tile and when it appears.  
[App Settings](app-settings.md) — the app-global Webcam toggle.
