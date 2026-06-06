---
created: 2026-06-05T20:30:00Z
title: Verify webcam per-profile toggle gates the drawer tile (on-device)
area: ui
target_phase: 21
resolves_phase: 21
files:
  - app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
---

## Problem

During the Phase 15.2-04 four-tile IA on-device checkpoint, the webcam-tile-gating
test (Settings webcam toggle flips per-printer AND the Webcam drawer tile greys when
OFF / lights when ON — MEDIUM-4) was **deferred, not verified**. Owner decision: webcam
is only marginally important right now, so push the on-device verification to the final
ship hardening pass.

The wiring is implemented (15.2-03 added `webcamEnabled` per-profile + `webcamTileEnabled`
gating flow; 15.2-04 wired the AppShell consumer), but it has NOT been eyeballed on-device:
- toggle webcam ON for printer A, switch to printer B → independent state
- tile greys when OFF, lights when ON

## How to apply

Fold into the Phase 21 final theme/UI conformance + ship hardening on-device sweep.
Run alongside the other deferred webcam items if any.

## 15.2 code review (WR-02) — latent webcam-URL bug (pre-existing, from Phase 14)

`AppShell.kt:~194` webcam URL resolver still reads `connectionStore`, which is
WRITE-DEAD — every write moved to `profileStore` in Phase 14. So `cfg` is always null
→ empty host, and per-profile webcam URLs won't follow a profile switch. Repoint the
resolver to `container.activeConfig` (the same source the rest of the app uses). Small
fix; chase it before/with the camera phase (Phase 20). Not introduced by 15.2 (the
region wasn't in this phase's diff) but adjacent to the webcam-tile gating work.
