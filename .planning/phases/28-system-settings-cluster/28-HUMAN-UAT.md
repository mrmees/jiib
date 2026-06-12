---
status: partial
phase: 28-system-settings-cluster
source: [28-VERIFICATION.md]
started: 2026-06-12T20:45:00Z
updated: 2026-06-12T20:45:00Z
---

## Current Test

[awaiting human testing — flox offline; signed APK ready at app/build/outputs/apk/release/app-armeabi-v7a-release-signed.apk]

## Tests

### 1. Dev-cycler drag relocates (WR-01 fix)
expected: With the dev theme-cycler overlay visible, dragging its bare strip smoothly relocates the panel (no snap-back/jitter); chip taps still work.
result: [pending]

### 2. Back dismisses delete ConfirmGuard (WR-02 fix)
expected: Printers → arm Delete → tap a row → ConfirmGuard opens. Pressing Back closes the guard ONLY (stays on Printers); a second Back leaves the screen.
result: [pending]

### 3. Babystep field stable while typing (WR-03 fix)
expected: Settings → babystep layers field: typing a multi-digit value is not clobbered mid-typing; value persists after leaving the field and after navigating away/back.
result: [pending]

### 4. Printers + Theme editor render via shared seams (WR-04 fix)
expected: Both screens look and behave identically to the UAT-approved state (no missing rows/controls after the ~500-line duplicate deletion); status slot sublabel correct.
result: [pending]

### 5. S/V square stays responsive (WR-09 fix)
expected: Theme editor S/V square tracks drags correctly including rapid slot switches; crosshair restores on re-open.
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
