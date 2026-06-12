# Phase 10: Webcam Streaming - Context

**Gathered:** 2026-06-03
**Status:** Ready for planning

<domain>
## Phase Boundary

View the printer's webcam(s) on the tablet without a browser. Enumerate configured
cams via Moonraker `/server/webcams/list`, decode the common Klipper MJPEG case
(`multipart/x-mixed-replace` from ustreamer/crowsnest/mjpg-streamer), and render live
frames **hard-downscaled** to the display size because full-res MJPEG decode will
OOM/jank the Adreno-320 / 2GB floor. The page layout/interaction is **already locked by
the owner's `docs/view_specific_notes/camera_feed` note** (see Specific Ideas) — this
phase clarifies the streaming/decode/error-handling mechanics, not the visual design.

**In scope:** MJPEG stream decode; snapshot-poll fallback; cam enumeration + capability
gating; the camera_feed-note layout (single-focus default, aspect-aware field show/hide,
back-only gutter, in-feed cycle overlay, rounded framing cutout, pixel-square no-stretch);
preferred-cam persistence; error/stall/retry UX; page-visible lifecycle.

**Out of scope (explicitly deferred):** WebRTC / H.264 decode (`camera-streamer`/`go2rtc`)
— documented as deferred (SC-4); a Print Status home mini-preview; any webcam config
editing (add/delete/edit cams — Moonraker `server.webcams.*` write ops are catalog
`reference_only`, Dinghy doesn't send them).

</domain>

<decisions>
## Implementation Decisions

### Format & Fallback Ladder
- **D-01:** Three-rung fallback ladder per camera: **(1)** decode the MJPEG
  `multipart/x-mixed-replace` `stream_url`; **(2)** if the stream isn't MJPEG-decodable,
  fall back to **polling `snapshot_url`** at ~2 fps; **(3)** if there's no usable stream
  AND no snapshot (e.g. pure WebRTC), show an "unsupported" card.
- **D-02:** Path selection uses the `/server/webcams/list` **`service` field as a HINT
  but the HTTP `Content-Type` as the source of truth** — confirm `multipart/x-mixed-replace`
  before committing to the MJPEG decoder (the `service` string is frequently blank/misconfigured
  in real installs). This makes ravens-perch (defaults to `mjpeg`, registers a token-protected
  snapshot URL) and crowsnest (MJPEG or H.264) both work where they can.
- **D-03:** **Snapshot-fallback indicator = a small persistent corner badge** overlaid on the
  feed (e.g. "Snapshot ~2fps") for the whole time fallback is active. Honest, non-nagging — NOT
  a one-shot toast. The user must always know they're not on the live stream.
- **D-04:** **Dead-end (rung 3) card** lives inside the framing cutout, **names the detected
  service** ("This camera uses WebRTC — not supported yet"), and has **NO open-in-browser
  button** (target wall tablets often have no usable system browser).

### Decode & Bandwidth Budget
- **D-05:** **No user-facing tuning knobs this phase** — fixed sane defaults only ("keep it
  simple for now"). The Adreno-320 floor dictates the values, not the user. Knobs can be added
  later if a real need emerges.
- **D-06:** Fixed decode policy: **cap decode to ~10–15 fps**, **always `inSampleSize`-downscale
  to the view's pixel size**, **drop frames when decode falls behind** (never queue/lag), and
  **reuse a single bitmap**. Exact fps/sample numbers to be **pinned by on-device measurement on
  flox** during research/planning (the project's standard perf-gate discipline).
- **D-07:** **Snapshot-fallback poll rate = ~2 fps** (~every 500ms). Marginally snappier than
  ravens-perch's own ~1/sec snapshot refresh; still light on bandwidth/CPU.

### Capability Gating & Enumeration
- **D-08:** **Webcam drawer tile is GREYED/DISABLED — not hidden — when zero cams are
  configured** (or the endpoint is absent). This is a **deliberate departure from Phase 9's
  hide-the-tile gating**: the owner wants users to discover the capability exists, consistent
  with the App Drawer's "coming soon" greyed convention.
- **D-09:** **URL resolution is aggressive:** resolve relative `stream_url`/`snapshot_url`
  against the configured Moonraker host AND **rewrite `localhost`/`127.0.0.1` to the Moonraker
  host the tablet actually uses** — the #1 real-world "works in Mainsail, blank here" gotcha.
- **D-10:** **Preferred camera = last-viewed, remembered per printer** (persisted, DataStore-style,
  keyed by printer/connection). No explicit "set as default" / pin action. Default on a fresh
  printer with no saved preference = the first cam in the `/server/webcams/list` order.

### Error, Stall & Lifecycle UX
- **D-11:** **On stall/mid-view drop: keep the last good frame visible (dimmed) with a subtle
  "Reconnecting…" overlay** while auto-retrying — avoids a jarring black flash on a brief Wi-Fi
  hiccup on an always-on panel. Do NOT clear straight to an error state on transient blips.
- **D-12:** **Retry with backoff (≈1s → cap ≈10s) only while the Webcam page is foreground.**
  Stop retrying entirely on navigate-away/background — no wasted decode/bandwidth (owner's
  efficiency concern + Phase-13 cadence discipline). Self-recovers when the cam returns.
- **D-13:** **Page-visible lifecycle:** the stream/poll runs ONLY while the Webcam page is
  visible+foreground; it pauses/stops cleanly on background or navigation (SC-3). This is the
  governing rule for both decode work and retry loops.

### Claude's Discretion
- Exact decode fps cap and `inSampleSize` step (D-06) — measure on flox, pick the value that
  holds zero-jank at the Adreno-320 floor.
- The precise MJPEG multipart boundary-parsing implementation (lean Kotlin over OkHttp streaming
  response; the old Java references are reference-only, not dependencies — see canonical refs).
- Threading/coroutine structure for off-UI-thread decode + frame hand-off to the view.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Owner design intent (HIGHEST PRIORITY — locks the layout/interaction)
- `docs/view_specific_notes/camera_feed` — the owner's explicit vision for THIS screen:
  single-focus default + per-printer preferred cam; aspect-aware field (cam list) show/hide
  vs device orientation; back-only gutter; in-feed cycle overlay (burst icon + cam name) when
  multiple cams; never-stretch / pixel-square in both orientations; rounded framing cutout
  (cropping OK for visual continuity); crowsnest as the de-facto target + ravens-perch
  (MediaMTX/FFmpeg) support; hard bandwidth/CPU efficiency concern.

### UI design system (LAW)
- `docs/ui_design/CLAUDE.md` — design philosophy + non-negotiables
- `docs/ui_design/LAYOUT.md` — Focus / Field / Gutter grammar (the field-show/hide logic in the
  camera_feed note is an application of this), portrait + landscape rules, ratio-only sizing
- `docs/ui_design/THEMING.md` — semantic tokens, button-intent colors (back = red)
- (No dedicated webcam hi-fi mockup exists — the camera_feed note is the design contract for
  this screen; reuse the Focus/Field/Gutter grammar from existing mockups.)

### Moonraker / webcam API
- `docs/commands/catalog.json` — webcams section; upstream
  `https://moonraker.readthedocs.io/en/latest/external_api/webcams/`
  (`server.webcams.list` is the enumeration source; the `*.create_item`/`delete_item`/`get_item`
  write ops are `reference_only` — out of scope)
- `docs/commands/printer-matrix.json` — per-printer `webcam` object presence (E5/E3)
- `docs/moonraker-capabilities.md` — live captures; reconcile the `/server/webcams/list` shape here
- `https://github.com/mainsail-crew/gb-crowsnest/blob/main/configuration/cam-section.md` —
  crowsnest cam-section encoder formats (owner-cited)

### Reliability / cadence guardrail
- `docs/request-cadence-contract.md` — Phase-13 contract; the webcam stream is its OWN HTTP
  connection (NOT Moonraker JSON-RPC polling) so it's exempt from the single-subscribe rule, but
  the "no waste, stop when not visible" discipline (D-12/D-13) honors its spirit.

### Stack guidance (from CLAUDE.md "Camera note")
- Project `CLAUDE.md` camera note: OkHttp streaming response → split on multipart boundary →
  `BitmapFactory.decodeByteArray` per frame → Compose `Image`/`Canvas`; reuse bitmap +
  `inSampleSize`-downscale hard. References `niqdev/ipcam-view`, `perthcpe23/android-mjpeg-view`
  (old/Java — REFERENCE only, reimplement lean in Kotlin, do NOT depend on them).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Off-thread bitmap decode + downsample (Phase 7 Files):** `inSampleSize` downsample, bounded
  cache, graceful fallback patterns already proven — the same discipline applies to MJPEG frames.
  Coil 3 wiring exists for thumbnails (snapshot stills could reuse Coil; the live MJPEG loop is
  custom OkHttp-stream decode, not Coil).
- **Views-in-Compose hosts:** `render/GraphViewHost.kt`, `render/BedMeshHeatmapHost.kt`,
  `ui/files/FileListView.kt`, `ui/console/ConsoleListView.kt`, `theme/views/ThemeableView.kt` —
  the established `AndroidView`/`ComposeView` hybrid host pattern for a custom-drawn surface;
  a custom MJPEG frame view likely follows the same `ThemeableView` host approach.
- **OkHttp single-client stack:** `net/MoonrakerSocket.kt`, `service/MoonrakerService.kt`,
  `auth/MoonrakerAuth.kt`, and `defaultClient()` (with the Phase-13 `pingInterval` keepalive) —
  reuse the one OkHttp client/TLS/connection-pool config for the streaming response + snapshot GETs;
  send `X-Api-Key` where auth is configured (ravens-perch snapshot URLs already carry their own token
  in the URL from `/server/webcams/list`).

### Established Patterns
- **Capability gating off the Phase-6 matrix** (`Capabilities.hasObject()` / matrix predicates) —
  but NOTE D-08: webcam tile is GREYED, not hidden, unlike Phase-9 Calibration.
- **Foreground-service-owned spine + page-visible lifecycle** — the stream must hook the same
  lifecycle discipline (stop on background) the rest of the app uses.
- **Per-printer DataStore prefs** (theme prefs, macro prefs precedents) — preferred-cam persistence
  follows the same keyed-prefs pattern.

### Integration Points
- New Webcam destination wired into the App Drawer + `Dest`/AppShell routing (greyed-when-no-cams).
- `/server/webcams/list` fetch hooked into the connection/handshake layer (REST GET via the shared
  OkHttp client); enumeration result feeds the cam picker (field) + cycle overlay.
- Back-only gutter routes through the existing shell back handling.

</code_context>

<specifics>
## Specific Ideas

**Owner's `camera_feed` note is the verbatim design contract** (do NOT re-litigate):
- Default view = single focus frame showing the first available cam, or the per-printer
  previously-selected one.
- **Aspect-aware field:** detect feed ratio (portrait/landscape). Show the cam-list field ONLY
  when it makes sense vs device orientation — e.g. landscape device + landscape feed → no field;
  landscape device + portrait feed → show field.
- Gutter = **Back only**.
- In full-focus mode (no field) with multiple cams detected: an **in-feed overlay icon**
  (burst-mode icon + the cam's Moonraker name) that **cycles cameras on tap**.
- **Never stretch** feeds — pixel-square always; grow/shrink to fit; correct in portrait AND
  landscape.
- Put the feed in a **rounded-edge framing cutout box**; losing edge content to the rounding is
  acceptable for visual continuity (square feeds clash with the round-edge aesthetic elsewhere).
- Target backends: **crowsnest** (de-facto standard, either encoder format) + the owner's own
  **ravens-perch** (MediaMTX/FFmpeg; defaults to `mjpeg`, token-protected snapshot URL).

</specifics>

<deferred>
## Deferred Ideas

- **"Layer level doesn't work" (from the camera_feed note's last line)** — this is a **Print
  Status data bug**, NOT webcam scope: the current/total layer readout is unreliable and needs a
  reliable source (likely `print_stats.info.current_layer` / `total_layer` vs the metadata-derived
  `layer_count`). Capture to the backlog (`/gsd-capture`) as a Status-screen fix; revisit in a
  reliability/polish pass, not here.
- **WebRTC / H.264 decode** (`camera-streamer`/`go2rtc` low-latency) — explicitly deferred past
  MJPEG (roadmap SC-4; a likely v2 phase). This phase only documents it as deferred.
- **Print Status home mini-preview / cam thumbnail** — not requested in the note; would be its own
  enhancement.

</deferred>

---

*Phase: 10-webcam-streaming*
*Context gathered: 2026-06-03*
