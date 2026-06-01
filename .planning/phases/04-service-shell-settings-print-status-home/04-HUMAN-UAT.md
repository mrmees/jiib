---
status: partial
phase: 04-service-shell-settings-print-status-home
source: [04-VERIFICATION.md]
started: 2026-05-31
updated: 2026-05-31
---

## Current Test

[awaiting human on-device testing against the live Ender 5 Plus]

## Tests

### 1. FGS connection survives rotation + screen-off (SC1 live half)
expected: With a real connection saved, the foreground-service notification shows connection status WITHOUT the API key. Rotate portrait↔landscape several times and screen-off ~60s then on — the connection stays Connected, temps stay live, and `sessionInstanceId` (adb logcat -s DinghySpine) is UNCHANGED. (Automated rotation half already PASSED on flox: ServiceSurvivesRotationTest 1/1.)
result: [pending]

### 2. Combined-render perf gate on the real Print Status surface (SC4 perf half / 04-06b)
expected: `dumpsys gfxinfo works.mees.dinghy reset` → dwell ~30s on Print Status with a live heat ramp/print so ring+sparkline+grid all update → capture `framestats` → tools/gfxinfo-parser/parse_framestats.py. Clears the two-part Adreno-320 gate from 03-PERF-RESULTS.md: liveness (allocation-free/no-loop/0 frozen frames) + sparse-redraw p95 ≤ ~66ms. The isolated Phase-3 50.1ms number is NOT a valid substitute.
result: [pending]

### 3. First-run → Settings → connect → Print Status flow (SC1/SC2 visual)
expected: Fresh launch lands on the connection-entry screen; enter the Ender 5 Plus host/port (+ key if used), Save; app connects, shows Splash during Klippy startup, then routes to Print Status home on Klippy ready — driven by klippy_state.
result: [pending]

### 4. Stop → ConfirmGuard → emergency_stop → Splash round-trip (SC4)
expected: On Print Status, the Stop control opens the full-screen Confirm guard (not a dialog); confirming fires `printer.emergency_stop`, the printer halts, and the app routes to Splash with shutdown reason text + recovery actions (retry / firmware-restart / restart-Klipper). Cancel on the guard dispatches nothing.
result: [pending]

### 5. Navigation feel / App Drawer ergonomics (SC3 visual)
expected: Swipe-up from the bottom opens the full-screen App Drawer of square tiles; Status + Settings tiles are tappable, future panels + red Power tile are greyed/inert; full-bleed canvas, no persistent status bar; drawer swipe feels responsive on the Nexus 7 (no jank). (Structural presence already PASSED: ShellPresenceTest 4/4.)
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
