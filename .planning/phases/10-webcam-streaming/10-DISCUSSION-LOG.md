# Phase 10: Webcam Streaming - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-03
**Phase:** 10-webcam-streaming
**Areas discussed:** Format fallback ladder, Decode & bandwidth budget, Capability gating & enumeration, Error & stall UX

---

## Format Fallback Ladder

### Non-MJPEG handling
| Option | Description | Selected |
|--------|-------------|----------|
| Snapshot-poll fallback | If stream isn't MJPEG, poll snapshot_url at low rate; "can't display" only if no snapshot either | ✓ |
| MJPEG-only, else message | Only attempt MJPEG; non-MJPEG shows unsupported card; snapshot polling deferred | |
| You decide | — | |

**User's choice:** Snapshot-poll fallback — **"but make sure the user knows they fell back to snapshot mode."**

### Detection method
| Option | Description | Selected |
|--------|-------------|----------|
| Service field first, content-type verify | Use `service` as a guess, confirm via HTTP Content-Type before decoding | ✓ |
| Content-type only | Ignore `service`; open stream and inspect Content-Type | |
| You decide | — | |

**User's choice:** Service field first, content-type verify.

### Fallback indicator
| Option | Description | Selected |
|--------|-------------|----------|
| Small persistent badge | Unobtrusive corner badge ("Snapshot ~Nfps") visible whole time in fallback | ✓ |
| One-shot toast on entry | Single severity toast when fallback kicks in, then nothing | |
| Badge + toast | Both | |

**User's choice:** Small persistent badge.

### Dead-end (no stream, no snapshot)
| Option | Description | Selected |
|--------|-------------|----------|
| Explain + name the format | Card naming the detected service; no browser button | ✓ |
| Explain + 'open in browser' | Same message + intent to open cam URL in system browser | |
| You decide | — | |

**User's choice:** Explain + name the format.

**Notes:** ravens-perch (owner's own software, MediaMTX/FFmpeg) defaults cams to `mjpeg` and
registers a token-protected snapshot URL with Moonraker — so the "support ravens-perch" ask is
satisfiable within MJPEG-only scope, not a WebRTC blocker.

---

## Decode & Bandwidth Budget

### Tuning knobs
| Option | Description | Selected |
|--------|-------------|----------|
| Fixed sane defaults, zero knobs | Hard-downscale + FPS cap + drop-behind, no settings | ✓ |
| One knob: target FPS cap | Defaults + a single max-FPS control | |
| Two knobs: FPS + resolution cap | Defaults + FPS and resolution ceilings | |

**User's choice:** "No knobs adjustment at this time."

### Decode ceiling
| Option | Description | Selected |
|--------|-------------|----------|
| ~10-15 fps, downscale to view, drop-behind | Cap ~10-15 fps, inSampleSize-downscale, drop frames if behind, reuse bitmap | ✓ |
| ~5 fps conservative | Lower ceiling, zero-jank/min-bandwidth priority | |
| You decide | — | |

**User's choice:** ~10-15 fps, downscale to view, drop-behind (exact numbers pinned on flox).

### Snapshot-fallback poll rate
| Option | Description | Selected |
|--------|-------------|----------|
| ~1 fps | Matches ravens-perch's own snapshot refresh cadence | |
| ~2 fps | Snappier (~every 500ms), ~2x requests, still light | ✓ |
| You decide | — | |

**User's choice:** ~2 fps.

---

## Capability Gating & Enumeration

### Tile gating
| Option | Description | Selected |
|--------|-------------|----------|
| Hide tile when zero cams | Don't show Webcam tile at all (Phase-9 pattern) | |
| Show tile, empty-state inside | Always show tile; empty state if no cams | (variant) |
| You decide | — | |

**User's choice:** **"Grayed out, just like the others, want people to know what the software
can do."** — greyed/disabled tile (NOT hidden, NOT a fully-active empty page); deliberate
departure from Phase-9 hide-the-tile gating, matching the App Drawer "coming soon" convention.

### URL resolution
| Option | Description | Selected |
|--------|-------------|----------|
| Resolve relative + rewrite localhost | Prefix relative paths AND rewrite localhost/127.0.0.1 to the real Moonraker host | ✓ |
| Resolve relative only | Prefix relative paths, trust absolute URLs verbatim | |
| You decide | — | |

**User's choice:** Resolve relative + rewrite localhost.

### Preferred camera
| Option | Description | Selected |
|--------|-------------|----------|
| Last-viewed is remembered | Last cycled-to cam silently remembered per printer | ✓ |
| Explicit 'set as default' action | Pin a preferred cam via long-press/star | |
| You decide | — | |

**User's choice:** Last-viewed is remembered (per printer).

---

## Error & Stall UX

### Stall / mid-view drop
| Option | Description | Selected |
|--------|-------------|----------|
| Frozen last frame + reconnecting overlay | Keep last frame (dimmed) + subtle "Reconnecting…" overlay while retrying | ✓ |
| Clear to error state immediately | Drop frame, show error/retrying state right away | |
| You decide | — | |

**User's choice:** Frozen last frame + reconnecting overlay.

### Auto-retry policy
| Option | Description | Selected |
|--------|-------------|----------|
| Backoff retry while page is open | Backoff (1s→~10s) while foreground; stop on background; self-recovers | ✓ |
| Few tries then give up | Retry a handful of times, then static "tap to retry" card | |
| You decide | — | |

**User's choice:** Backoff retry while page is open.

---

## Claude's Discretion

- Exact decode fps cap + `inSampleSize` step (measure on flox).
- MJPEG multipart boundary-parsing implementation (lean Kotlin over OkHttp; old Java libs reference-only).
- Off-UI-thread decode + frame hand-off threading structure.

## Deferred Ideas

- **"Layer level doesn't work"** (camera_feed note last line) — Print Status data bug, NOT webcam
  scope; capture to backlog for a Status-screen fix (reliable current/total layer source).
- **WebRTC / H.264 decode** — explicitly deferred past MJPEG (roadmap SC-4; likely v2).
- **Print Status home mini cam preview** — not requested; own future enhancement.
