---
created: 2026-06-05T21:55:00Z
title: Webcam screen crashes during conformance sweep
area: ui
target_phase: 20
files:
  - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt
---

## Problem

During the Phase 15.2-06 app-wide on-device conformance sweep (flox), the **Webcam
screen crashes**. Owner decision (2026-06-05): acceptable to defer — the webcam work
has a dedicated upcoming phase (Phase 20, WebRTC camera), so fixing the crash now
isn't worth it. Both dev printers are WebRTC-only, so MJPEG webcam is fixture-proven
only; the camera surface is already amber-flagged BETA.

Not diagnosed yet — no stack trace captured. Reproduce by opening the Webcam tile on
flox with the current build.

## How to apply

Fold into Phase 20 (WebRTC camera). Capture the logcat stack trace first
(`adb logcat` while opening the Webcam tile), then fix as part of the camera rework.
