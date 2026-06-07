---
status: partial
phase: 18-preview-harness-tokenization-foundation
source: [18-VERIFICATION.md, 18-04-PLAN.md]
started: 2026-06-06
updated: 2026-06-06
---

## Current Test

[awaiting human testing — owner-deferred, Phase-17-UAT posture]

## Tests

### 1. Studio render — 3 exemplars
expected: Open PrintStatus / FineTune / Spool `@Preview` panels in Android Studio. Each renders the 6 theme combos (dark/light × Colorful/Simple/HighContrast) + the fs=L overflow shot + the RTL spot-check. Classic-View/Coil/CameraX surfaces show labeled placeholders (NOT blank), no live-Moonraker errors.
result: [pending]

### 2. start_dest flox deep-jump (dev-enable ON)
expected: On flox with dev-enable ON, `adb shell am start -n works.mees.dinghy/.MainActivity --es start_dest FineTune` lands on FineTune; repeat for `Spool` and `PrintStatus` (after Splash/Klippy-Ready). Each jumps to the named screen.
result: [pending]

### 3. start_dest garbage input
expected: `--es start_dest NotARealScreen` launches normally to the default screen and does NOT crash.
result: [pending]

### 4. Release-inert check
expected: From a clean data state (`adb shell pm clear works.mees.dinghy`, or install the RELEASE APK), the `start_dest` extra is IGNORED (lands on default) because `devCyclerEnabled` defaults false; confirm release exposes no UI path to enable the dev cycler.
result: [pending]

### 5. Non-exemplar regression smoke
expected: Walk Move / Calibration / Console / Files / Temperature / Webcam / Macros / Spool-scan on-device — no regressions from the tokenization/preview-harness work (these screens were NOT tokenized this phase; verify they still render/behave correctly).
result: [pending]

### 6. Pseudolocale en-XA sweep
expected: Switch device to English (XA) pseudolocale. The tokenized exemplar strings (PrintStatus/FineTune/Spool) show accented/expanded pseudo-text; deferred-to-Phase-22 literals (ScanSurface, FwRetractionScreen) show plain English. Confirms which strings are tokenized vs. backfill-pending.
result: [pending]

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps
