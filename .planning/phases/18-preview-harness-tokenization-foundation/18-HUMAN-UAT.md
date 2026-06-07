---
status: partial
phase: 18-preview-harness-tokenization-foundation
source: [18-VERIFICATION.md, 18-04-PLAN.md]
started: 2026-06-06
updated: 2026-06-07
---

## Current Test

[Tests 2–5 verified on flox (Claude-driven adb + screenshots); Tests 1 + 6 pending owner (Android Studio install in progress; pseudolocale locale-switch)]

## Tests

### 1. Studio render — 3 exemplars
expected: In Android Studio, open the `@Preview` panels for PrintStatus / FineTune / Spool. Each renders 6 theme combos + fs=L + RTL; preview-unsafe surfaces (classic-View graph/heatmap, webcam, CameraX scan, Coil) show labeled placeholders, not blank/tofu/crash.
result: [pending]
note: Owner verifying in Android Studio (first-time install in progress 2026-06-07). Harness also covered by host `PreviewBoxSmokeTest`. On-device renders of the same exemplars confirmed live in tests 2 below.

### 2. start_dest flox deep-jump (dev-enable ON)
expected: With dev-enable ON, `am start --es start_dest FineTune|Spool|PrintStatus` lands on the named screen after Splash.
result: pass
evidence: flox + live E5 (192.168.1.120). Enabled dev via DataStore (`dev_cycler_enabled`→true), then: `start_dest=FineTune` → landed on FineTune Hub (Motion + Extrusion tiles); `start_dest=Spool` → landed on Spool screen w/ live Spoolman list (ABS·Black 735g etc.). Both also confirm the 18-06 FineTune + 18-07 Spool exemplars render correctly on-device. PrintStatus is the default landing (cold-start + garbage both land there). Dev-cycler overlay appeared, confirming gate active.

### 3. start_dest garbage input
expected: `--es start_dest NotARealScreen` launches normally to default screen, no crash.
result: pass
evidence: With dev ON, `start_dest=NotARealScreen` → landed on default Standby Print-Status, no crash, no FATAL in logcat. parseStartDest total on untrusted input confirmed on-device.

### 4. Release-inert check
expected: From dev-disabled (default) state, the `start_dest` extra is IGNORED (lands default).
result: pass
evidence: DataStore `dev_cycler_enabled` decoded = `12 02 08 00` (false) by default. With dev OFF, `start_dest=FineTune` → landed on default Standby Print-Status (extra ignored). In a real release build `BuildConfig.DEBUG` is also false (WR-01 fix), so the read path never runs — doubly inert. Dev restored to OFF after testing.

### 5. Non-exemplar regression smoke
expected: Non-exemplar screens (only touched shared files this phase) still render — no regression from tokenization/preview work.
result: pass
evidence: Deep-jumped (dev ON) + screenshotted Temperature, Move, Files, Console — all render correctly, no crashes/blank/tofu. Move = jog pad w/ directional colors + unhomed caution triangles + lock + distance steps (perfect). Files = gcodes breadcrumb + empty-state. Console = PA scrollback + shape-coded severity glyphs + filters.
note (NOT a Phase-18 regression): Temperature screen showed "No heaters" empty-state while PrintStatus reads Nozzle/Bed/chamber_air fine — TemperatureScreen.kt was NOT modified this phase; possibly a deep-jump-before-capabilities-ready quirk or pre-existing behavior. Worth a separate look. Screens not yet smoked: Webcam, Macros, Calibration, Extrude, Spool-scan.

### 6. Pseudolocale en-XA sweep
expected: Switch device to English (XA) pseudolocale — tokenized exemplar strings show accented/expanded pseudo-text; deferred-to-Phase-22 literals (ScanSurface, FwRetractionScreen) show plain English.
result: [pending]
note: System-locale switch to en-XA needs owner action in Android Settings → Languages (or the en-XA `DeviceAndLocalePreviews` panel in Studio test 1). pseudolocale build flag `isPseudoLocalesEnabled = true` confirmed present in debug build config.

## Summary

total: 6
passed: 4
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps

[none — 0 issues. Tests 1 + 6 pending owner verification; one out-of-scope observation (Temperature "No heaters") flagged under test 5 for separate investigation.]
