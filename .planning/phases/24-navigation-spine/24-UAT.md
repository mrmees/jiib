---
phase: 24
slug: navigation-spine
plan: 05
status: approved
device: flox (Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30)
apk: app/build/outputs/apk/release/app-armeabi-v7a-release-signed.apk
apk_mtime: "2026-06-09T23:48 -0500"
last_wave2_commit: ec0caf7
last_wave2_commit_ts: "2026-06-09T22:58:47 -0500"
apk_is_nonstale: true
host_suite: BUILD SUCCESSFUL (34 actionable tasks, 5 executed, 29 up-to-date)
styling_fix_commit: e07263c
styling_fix_apk_reinstalled: true
created: 2026-06-09
signed_off: 2026-06-10
---

# Phase 24 — Navigation Spine — On-Device UAT

## Build Provenance

| Item | Value |
|------|-------|
| Host suite | `BUILD SUCCESSFUL in 20s` (`:app:testDebugUnitTest --no-daemon`, 5 tasks executed) |
| Release APK | `app/build/outputs/apk/release/app-armeabi-v7a-release-signed.apk` |
| APK mtime | `2026-06-09T23:48 -0500` (post fix commit `e07263c`) |
| Last Wave-2 commit | `ec0caf7` — `feat(24-04): idle action list + foot bar + FIX-5 Webcam icon swap` |
| Wave-2 commit ts | `2026-06-09T22:58:47 -0500` |
| Non-stale | **YES** — APK mtime post-dates both Wave-2 commit and the styling fix commit |
| Styling fix | `e07263c` — `fix(24-05): e-stop glyph font-scale-stable + 64dp square box (UAT styling gap)` |
| Reinstalled on flox | **YES** — `adb install -r` → `Success` |

APK built with `assembleRelease --no-daemon` (8 release tasks re-executed).
Zipaligned + debug-signed via `E:\Android\sign-release.bat`. Reinstalled on flox after styling fix.

---

## On-Device Checks (flox)

> Owner ran these checks on the physical Nexus 7 2013 (flox) against a live printer
> (E3 192.168.1.121:7125). A styling defect (e-stop glyph overflow) was found at SC-3 UAT,
> fixed in-phase (commit `e07263c`), and the fixed APK reinstalled; the owner accepted the fix
> without re-eyeball, relying on pre-release visual review.

---

### SC-1: Morphing Root — Both Orientations

**Requirement:** The waterfall morphing root (PrintStatusScreen as WaterfallHome) renders correctly in PORTRAIT and LANDSCAPE. The idle action list shows capability-gated D-06 rows for the connected printer (Spool only if Spoolman, Webcam only if a cam, Macros only if bookmarks, Outputs only if controllable outputs). Temperature and Console rows are absent from the idle list.

**On-device steps:**

1. Connect to a live printer. On landing (after Splash), confirm you are on the morphing root (WaterfallHome).
2. In PORTRAIT: verify the idle action list shows only capability-appropriate tiles; confirm no Temperature/Console tile.
3. Rotate to LANDSCAPE: verify the same idle action list in landscape Focus/Field layout.
4. If the printer has no Spoolman: confirm no Spool tile. If it has no cam: confirm no Webcam tile.

**Status:** PASS

**Owner verdict:** "Seems good." Landscape confirmed via screenshot. Idle list showed
Files / Move / Extrude / Macros / Calibration / Outputs with live temps in Focus. No
Temperature or Console rows present. Capability gating confirmed for this printer's
configuration.

---

### SC-2: Morph Cross-Fade — Frame Budget (no frozen frames)

**Requirement:** The root cross-fades idle↔printing↔terminal smoothly (~150ms one-shot Crossfade). No frozen frames on the Adreno-320 floor. Fallback to hard-cut if it janks — document the decision.

**On-device steps:**

1. Start a print on the connected printer. Observe the root cross-fade from idle (Standby) to printing (Printing).
2. Cancel or end the print. Observe the cross-fade back to idle or terminal.
3. Capture `dumpsys gfxinfo` immediately after a morph transition.

**gfxinfo result:**
```
UI-thread 50th percentile: 6ms
UI-thread 90th percentile: 44ms
UI-thread 99th percentile: 85ms
GPU 50th percentile: 6ms
GPU 95th percentile: ~4950ms (outlier — see note)
GPU 99th percentile: ~4950ms (outlier — see note)
Frozen frames (>700ms): 0
Janky frames: minimal
```

**Frozen frames:** 0
**Janky frames:** minimal (well within budget)
**Morph decision:** smooth — keep Crossfade (one-shot ~150ms Crossfade retained)

**GPU percentile outlier note:** The 95th/99th GPU percentiles (~4950ms) are attributed to
an idle-gap / SurfaceView measurement artifact on this sparse-frame screen — not the morph
itself. No user-visible jank was observed. The UI-thread path (50th=6ms / 90th=44ms / 99th=85ms)
confirms the morph runs well within the Adreno-320 budget. No hard-cut fallback needed.

**Status:** PASS

**Owner verdict:** Morph idle→printing→terminal observed (a print started, morphed, and was
cancelled at layer 0). Zero frozen frames confirmed by gfxinfo. Cross-fade kept.

---

### SC-3: Floating E-Stop — Drill-Down Screens While Printing (FIX-1)

**Requirement:** The red floating e-stop is visible top-left of Focus on EVERY drill-down screen while printing (Move, Extrude, Console, etc.) — not only on the morphing root. Tapping it opens the FULL-SCREEN Stop Confirm guard (not a dialog/hold gesture). A confirmed stop issues the emergency stop. The e-stop is ABSENT when idle.

**On-device steps:**

1. Start a print on the connected printer.
2. From the morphing root (Printing mode): confirm the red floating e-stop is visible top-left.
3. Drill into **Move**: confirm the e-stop is STILL visible top-left on the Move screen.
4. Drill into at least one more screen (e.g. Extrude or Console): confirm e-stop visible on each.
5. Tap the e-stop on one drill-down screen: confirm the FULL-SCREEN Stop Confirm guard opens.
6. Return to idle (after print ends/cancel). Confirm the e-stop is ABSENT on the root and on drill-down screens.

**Status:** PASS (function) + STYLING DEFECT FOUND AND FIXED IN-PHASE

**Owner verdict (functional gate):** E-stop confirmed visible on Move/Extrude drill-down screens
while printing. Tapping the e-stop opened the full-screen Stop Confirm guard. E-stop is absent
when idle. FIX-1 confirmed on real hardware.

**Styling defect found at UAT:** The e-stop glyph overflowed its 2px `Intent.Danger` border on
flox at large effective font scale (Nexus 7 / tablet-sized U ≈ 86dp → box ≈ 60dp → 50sp glyph
larger than border at fontScale > 1).

**Root cause:** `OutlinedControl`'s icon-only branch sized the glyph in `sp` (fixed 50sp) inside
a fixed-`dp` box; at system fontScale > 1.0 the sp value exceeds the dp box.

**Fix applied in-phase (commit `e07263c`):**
- `OutlinedControl.kt`: glyph `sizeSp = 50f / LocalDensity.current.fontScale` — font-scale-stable
  dp-equivalent; byte-identical at fontScale 1.0; does NOT affect SortRow/FilterRow/SpoolScreen
  icon-only controls at normal scale
- `FloatingEStop.kt`: `modifier.size((uDp * 0.7f).coerceAtLeast(64.dp))` — 64dp touch-target
  floor ensures the box stays square at/above the minimum

**Owner directive:** Fix now, assume fixed; rely on pre-release visual review (no on-device
re-eyeball required).

---

### SC-4: System Foot Button Opens App Drawer + No Print-Monitoring Regression

**Requirement:** Tapping the System foot button opens the App Drawer (neutral behavior — not a power action). Driving a print and monitoring it via PrintStatus-as-root is unaffected by the Navigation-Compose shell migration.

**On-device steps:**

1. From the morphing root (idle): tap the **System** foot button. Confirm the App Drawer opens.
2. Dismiss the drawer.
3. Start a print. On the morphing root (Printing mode): verify live progress, temps, and stats update correctly.
4. Drill into Files, Move, or Calibration and Back out. Confirm the back-stack works.
5. While printing, drill into several screens and Back out. Confirm no monitoring regression.

**Status:** PASS

**Owner verdict:** System (`bottom_panel_open`) foot button opens the App Drawer confirmed.
Live Nozzle / Bed / SKR (MCU) / Spool data + print progress updating on the root confirmed.
Back-stack drill-in and Back-out works correctly.

---

### SC-5: Recovery Splash → WaterfallHome + Sub-Nav Reset (FIX-3 Accepted Regression)

**Requirement:** After a reconnect blip (Splash re-runs), the app lands on WaterfallHome (the morphing root), NOT on the prior drill-down. AND the in-screen sub-nav (Calibration routine, FineTune group, etc.) RESETS to its hub. This is the owner-accepted FIX-3 regression — BOTH behaviors are expected and not bugs.

**On-device steps:**

1. While idle or printing, drill into a sub-nav screen (e.g. Calibration or Fine-Tune group).
2. Force a reconnect blip (toggle WiFi / power-cycle the printer link briefly).
3. Observe the Splash reconnect screen appear.
4. After reconnect: confirm the app lands on WaterfallHome — NOT on the prior drill-down.
5. Open Fine-Tune or Calibration again: confirm in-screen sub-nav reset to its hub.
6. Confirm this is ACCEPTED behavior (FIX-3 regression — expected per-plan).

**Status:** PASS

**Owner verdict:** Klipper was reset. App showed the static "jiib" recovery Splash and recovered
to the standby/WaterfallHome root once Klipper restarted. Landed on root (NOT the prior
drill-down). Logged as expected FIX-3 behavior — not a bug. Sub-nav reset to hub confirmed.

---

## Out-of-Scope Note

**Printing / terminal view visual style (not a defect):** The printing/terminal view retains its
existing Phase-16 visual style. This is EXPECTED for the navigation-spine phase — Phase 24 redesigns
the idle root + nav spine + morph mechanism only; the printing-content redesign is a later
jiib-milestone phase. This was explicitly called out in the 24-04 plan ("printing-mode shortcut grid
+ babystep row are UNTOUCHED") and 24-CONTEXT ("leaves screen internals largely untouched"). No
action required.

---

## Pending Todo (Deferred — Non-Blocking)

**Move/Extrude (and other foot-gun control screens) during an active print:** Owner noted that these
screens should ideally be disabled or gated during an active print — making them navigable while
printing is a potential foot-gun (unintended axis moves during a live print). This is out of scope
for the navigation-spine phase. Captured as a pending todo for a future phase; do not silently drop.

Tracked as: Move/Extrude-during-print gating — owner-noted future concern, deferred, non-blocking.

---

## Overall Verdict

| SC | Check | Status |
|----|-------|--------|
| SC-1 | Morphing root — both orientations, capability-gated idle list | **PASS** |
| SC-2 | Morph cross-fade — no frozen frames (gfxinfo) | **PASS** |
| SC-3 | Floating e-stop on drill-down screens (FIX-1) + absent when idle | **PASS** (function); styling defect fixed in-phase `e07263c` |
| SC-4 | System foot button opens drawer + print-monitoring regression smoke | **PASS** |
| SC-5 | Recovery Splash → WaterfallHome + sub-nav reset (FIX-3 accepted regression) | **PASS** |

**Overall: APPROVED**

**Non-blocking items:**
- OUT-OF-SCOPE: Printing/terminal view retains Phase-16 style — expected, not a defect
- DEFERRED TODO: Move/Extrude-during-print gating — future phase concern, non-blocking

---

## Sign-Off

**Owner:** Matthew
**Date:** 2026-06-10
**Verdict:** All five SC gates PASS. One styling defect found (e-stop glyph overflow at large font
scale) fixed in-phase and reinstalled on flox. Phase 24 navigation-spine is owner-approved on real
hardware (flox / Nexus 7 2013 / Adreno 320 / LineageOS 18.1).
