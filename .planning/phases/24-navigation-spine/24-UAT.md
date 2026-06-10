---
phase: 24
slug: navigation-spine
plan: 05
status: pending
device: flox (Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30)
apk: app/build/outputs/apk/release/app-armeabi-v7a-release-signed.apk
apk_mtime: "2026-06-09T23:10 -0500"
last_wave2_commit: ec0caf7
last_wave2_commit_ts: "2026-06-09T22:58:47 -0500"
apk_is_nonstale: true
host_suite: BUILD SUCCESSFUL (34 actionable tasks, 2 executed, 32 up-to-date)
created: 2026-06-09
---

# Phase 24 — Navigation Spine — On-Device UAT

## Build Provenance

| Item | Value |
|------|-------|
| Host suite | `BUILD SUCCESSFUL in 14s` (`:app:testDebugUnitTest --no-daemon`, 2 tasks executed) |
| Release APK | `app/build/outputs/apk/release/app-armeabi-v7a-release-signed.apk` |
| APK mtime | `2026-06-09T23:10 -0500` |
| Last Wave-2 commit | `ec0caf7` — `feat(24-04): idle action list + foot bar + FIX-5 Webcam icon swap` |
| Wave-2 commit ts | `2026-06-09T22:58:47 -0500` |
| Non-stale | **YES** — APK mtime (+11 min) post-dates the last Wave-2 commit |

APK built with `--rerun-tasks` (all 56 release tasks re-executed). Zipaligned + debug-signed via
`E:\Android\sign-release.bat`.

---

## On-Device Checks (flox)

> Owner runs these checks on the physical Nexus 7 2013 (flox) against a live printer
> (E5+ 192.168.1.120:7125 or E3 192.168.1.121:7125) with the freshly-installed signed APK.

---

### SC-1: Morphing Root — Both Orientations

**Requirement:** The waterfall morphing root (PrintStatusScreen as WaterfallHome) renders correctly in PORTRAIT and LANDSCAPE. The idle action list shows capability-gated D-06 rows for the connected printer (Spool only if Spoolman, Webcam only if a cam, Macros only if bookmarks, Outputs only if controllable outputs). Temperature and Console rows are absent from the idle list.

**On-device steps:**

1. Connect to a live printer. On landing (after Splash), confirm you are on the morphing root (WaterfallHome).
2. In PORTRAIT: verify the idle action list shows only capability-appropriate tiles; confirm no Temperature/Console tile.
3. Rotate to LANDSCAPE: verify the same idle action list in landscape Focus/Field layout.
4. If the printer has no Spoolman: confirm no Spool tile. If it has no cam: confirm no Webcam tile.

**Status:** PENDING

**Owner verdict:** _[fill in after flox walk]_

---

### SC-2: Morph Cross-Fade — Frame Budget (no frozen frames)

**Requirement:** The root cross-fades idle↔printing↔terminal smoothly (~150ms one-shot Crossfade). No frozen frames on the Adreno-320 floor. Fallback to hard-cut if it janks — document the decision.

**On-device steps:**

1. Start a print on the connected printer. Observe the root cross-fade from idle (Standby) to printing (Printing).
2. Cancel or end the print. Observe the cross-fade back to idle or terminal.
3. Capture `dumpsys gfxinfo` immediately after a morph transition:

```
adb shell dumpsys gfxinfo works.mees.dinghy framestats
```

4. Check the output for `Janky frames` and `Frozen frames`. Record the raw counts below.

**gfxinfo result (paste here):**
```
[paste dumpsys gfxinfo works.mees.dinghy framestats output here]
```

**Frozen frames:** _[count from gfxinfo]_
**Janky frames:** _[count from gfxinfo]_
**Morph decision:** _[ ] smooth — keep Crossfade_ / _[ ] janky — fall back to hard-cut_

**Status:** PENDING

**Owner verdict:** _[fill in after flox walk]_

---

### SC-3: Floating E-Stop — Drill-Down Screens While Printing (FIX-1)

**Requirement:** The red floating e-stop is visible top-left of Focus on EVERY drill-down screen while printing (Move, Extrude, Console, etc.) — not only on the morphing root. Tapping it opens the FULL-SCREEN Stop Confirm guard (not a dialog/hold gesture). A confirmed stop issues the emergency stop. The e-stop is ABSENT when idle.

**On-device steps:**

1. Start a print on the connected printer.
2. From the morphing root (Printing mode): confirm the red floating e-stop is visible top-left.
3. Drill into **Move**: confirm the e-stop is STILL visible top-left on the Move screen.
4. Drill into at least one more screen (e.g. Extrude or Console): confirm e-stop visible on each.
5. Tap the e-stop on one drill-down screen: confirm the FULL-SCREEN Stop Confirm guard opens.
6. (Optional — live printer willing) Confirm the guard's confirm button issues an emergency stop.
7. Return to idle (after print ends/cancel). Confirm the e-stop is ABSENT on the root and on drill-down screens.

**Status:** PENDING

**Owner verdict:** _[fill in after flox walk]_

---

### SC-4: System Foot Button Opens App Drawer + No Print-Monitoring Regression

**Requirement:** Tapping the System foot button opens the App Drawer (neutral behavior — not a power action). Driving a print and monitoring it via PrintStatus-as-root is unaffected by the Navigation-Compose shell migration.

**On-device steps:**

1. From the morphing root (idle): tap the **System** foot button. Confirm the App Drawer opens (swipe-up sheet with navigation tiles).
2. Dismiss the drawer.
3. Start a print. On the morphing root (Printing mode): verify live progress, temps, and stats update correctly.
4. Drill into Files, Move, or Calibration and Back out. Confirm the back-stack works (Back returns you up the stack correctly).
5. While printing, drill into several screens and Back out. Confirm no monitoring regression (progress/temps continue updating on the root).

**Status:** PENDING

**Owner verdict:** _[fill in after flox walk]_

---

### SC-5: Recovery Splash → WaterfallHome + Sub-Nav Reset (FIX-3 Accepted Regression)

**Requirement:** After a reconnect blip (Splash re-runs), the app lands on WaterfallHome (the morphing root), NOT on the prior drill-down. AND the in-screen sub-nav (Calibration routine, FineTune group, etc.) RESETS to its hub. This is the owner-accepted FIX-3 regression — BOTH behaviors are expected and not bugs.

**On-device steps:**

1. While idle or printing, drill into a sub-nav screen — e.g. open Calibration and enter a routine page, OR open Fine-Tune and navigate to a group (Motion/Extrusion/etc.).
2. Force a reconnect blip: toggle WiFi off and back on, OR power-cycle the printer link briefly.
3. Observe the Splash reconnect screen appear.
4. After reconnect: confirm the app lands on **WaterfallHome** (the morphing root) — NOT on the Calibration routine or Fine-Tune group you were in.
5. Open Fine-Tune or Calibration again (via drawer): confirm the in-screen sub-nav has **reset to its hub** (routine = null, group = null) — you are NOT returned to the prior sub-page.
6. Confirm this is ACCEPTED behavior (not a defect): the FIX-3 regression is expected per-plan.

**Status:** PENDING

**Owner verdict:** _[fill in after flox walk]_

---

## Overall Verdict

| SC | Check | Status |
|----|-------|--------|
| SC-1 | Morphing root — both orientations, capability-gated idle list | PENDING |
| SC-2 | Morph cross-fade — no frozen frames (gfxinfo) | PENDING |
| SC-3 | Floating e-stop on drill-down screens (FIX-1) + absent when idle | PENDING |
| SC-4 | System foot button opens drawer + print-monitoring regression smoke | PENDING |
| SC-5 | Recovery Splash → WaterfallHome + sub-nav reset (FIX-3 accepted regression) | PENDING |

**Overall:** PENDING

**Non-blocking polish / deferred items:** _[list any items owner notes but does not block on]_

---

## Sign-Off

**Owner:** _[Matthew, after flox walk]_
**Date:** _[to be filled]_
**Resume signal:** Type "approved" (record any PARTIAL/deferred items + the morph fallback decision if applicable), or describe issues.
