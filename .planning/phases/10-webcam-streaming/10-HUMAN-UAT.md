---
status: partial
phase: 10-webcam-streaming
source: [10-VERIFICATION.md]
started: 2026-06-04
updated: 2026-06-04
---

## Current Test

[awaiting human testing — environmental dependency not present on the developer network]

## Tests

### 1. Live MJPEG (rung-1) eyeball
expected: With a crowsnest/ustreamer camera serving `multipart/x-mixed-replace`, opening the Webcam page shows smooth live video — NO snapshot badge (rung-1, not rung-2), no jank on the Adreno-320 floor (WR-04 downscale holds), no torn frames (WR-03 double-buffer), and the stream STOPS on navigate-away / background (SC-3 foreground-only). Token never appears in any surfaced/logged URL (CR-02).
result: [pending]
note: Neither home printer exposes an MJPEG stream (both Ender 5 Plus and Ender 3 run WebRTC/MediaMTX). The rung-1 path is golden-fixture-proven in unit tests and all critical fixes are source-verified; only the live visual confirmation is deferred until an MJPEG source exists on the network. NOT a code gap.

## Summary

total: 1
passed: 0
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps
