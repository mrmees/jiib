---
phase: 27
slug: motion-calibration
plan: 07
status: pending
device: flox (Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30)
apk: app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
apk_mtime: "2026-06-12T00:00:12 -0500"
last_p27_commit: c882c97
last_p27_commit_ts: "2026-06-11T23:57:44 -0500"
apk_is_nonstale: true
build_mode: assembleDebug --rerun-tasks (47 tasks executed)
adb_install: Success
created: 2026-06-12
signed_off: pending
---

# Phase 27 — Motion + Calibration — On-Device UAT

## Build Provenance

| Item | Value |
|------|-------|
| Build command | `:app:assembleDebug --rerun-tasks --no-daemon` |
| Tasks executed | 47 actionable tasks: **47 executed** (all re-run — no UP-TO-DATE cache) |
| APK path | `app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk` |
| APK mtime | `2026-06-12 00:00:12 -0500` |
| Last P27 screen commit | `c882c97` — `docs(27-06): complete ScrewsTilt+Tilt restyle plan` |
| Last P27 commit ts | `2026-06-11 23:57:44 -0500` |
| Non-stale | **YES** — APK mtime (00:00:12) is AFTER last commit (23:57:44) |
| `adb install -r` | **Success** |
| Build warnings | 2 Kotlin deprecation warnings in `MoonrakerDiscovery.kt` (pre-existing, unrelated to P27) |

Stale-APK trap neutralized: `--rerun-tasks` forced all 47 build tasks to re-execute. APK mtime
post-dates the last Phase-27 screen commit (`c882c97`) by ~2 minutes.

---

## On-Device Checks (flox)

> Owner to run these checks on the physical Nexus 7 2013 (flox) against a live printer
> (E3 192.168.1.121:7125 OR E5+ 192.168.1.120:7125 — both have klicky probes).
> All items start **pending**. Do NOT mark any item passed without the owner's word.

---

### SC-1: MoveScreen — Both Orientations (D-01/D-02/D-03)

**Requirement:** MoveScreen renders correctly in PORTRAIT and LANDSCAPE. The spatial XY jog grid
is intact, the vertical Z column (Z-up / live-Z readout + tap-to-home / Z-down) is present, and
the distance stepper column (+/readout/−) sits beside the Z column. In portrait: the XY pad is
capped at ~60% of height — columns are NOT crowded out. In landscape: the pad fills its half-width
full height, columns beside it.

**On-device steps:**

1. Connect to a live printer (E3 or E5+). Navigate to Move via the App Drawer.
2. **PORTRAIT:**
   - Confirm the XY jog pad renders as a square grid occupying roughly 60% of the screen height — it does NOT push the Z/distance columns out of view or off-screen.
   - Jog X, Y, and Z in each direction. Confirm correct printer response.
   - Observe the vertical Z column: 3 equally-sized cells (Z-up / Z readout in center / Z-down). Tap the center readout — does it trigger "Home Z"?
   - Observe the distance stepper column: +/distance readout/−. Cycle +/− and confirm the step value changes through the preset set.
   - Arm the red **Force-Move** toggle (red fill when armed). Confirm the toggle state is obvious (C4 filled stop-red).
   - Tap **Disable Steppers** — confirm the red ConfirmGuard appears with correct warning text.
3. Rotate to **LANDSCAPE:**
   - Confirm the pad now fills the full height of its half of the screen.
   - Confirm the Z and distance columns are still beside it.
   - Re-confirm all jog directions + Z column + distance stepper work.
4. Confirm `FloatingEStop` is visible in the Focus top-left corner (UAT-4 reserve).
5. Try swiping **up** inside the Move screen — confirm it does NOT open the App Drawer (the jog surface captures the swipe).
6. Rotate mid-session: confirm the selected distance step is NOT reset by rotation.

**SC-1 Status:** pending

| Check | Result | Notes |
|-------|--------|-------|
| Portrait: XY pad ~60% height, columns visible | pending | |
| Portrait: XY jog X/Y/Z fires correctly | pending | |
| Portrait: vertical Z column (3 equal cells, Z-up/readout/Z-down) | pending | |
| Portrait: Z column center tap → Home Z | pending | |
| Portrait: distance stepper +/− cycles preset steps | pending | |
| Portrait: force-move toggle → red filled when armed | pending | |
| Portrait: Disable Steppers → red ConfirmGuard | pending | |
| Landscape: pad fills half-screen height | pending | |
| Landscape: Z + distance columns beside pad | pending | |
| Landscape: all controls still function | pending | |
| FloatingEStop visible top-left (while printing) | pending | |
| Rotation does NOT reset selected distance | pending | |

---

### SC-2: Calibration Hub + Five Routine Screens — Nav Coherence (D-05..D-10)

**Requirement:** The calibration hub is a list+Focus screen. Nav hub→routine pushes to back-stack;
Back pops to hub. Probe Calibrate: system Back is suppressed while a session is Active. BedMesh:
profile tap selects, Apply loads, SaveName takeover, Remove behind ConfirmGuard. Screws/Tilt:
run → turn instructions / adjustment data.

#### SC-2a: CalibrationHub

**On-device steps:**

1. From the App Drawer, navigate to **Calibration**.
2. Confirm the hub shows a **list of routines** (ListRows) with the first supported routine pre-selected.
3. The **Focus** (top / left half in landscape) shows: routine icon, title, description text, and an **Open** button.
4. Tap a different routine row — confirm the Focus updates to show that routine's icon + description.
5. Tap an **unsupported** routine (should appear dimmed/greyed, not hidden) — confirm it selects but is clearly distinguished from supported ones.
6. Tap **Open** on a supported routine — confirm navigation pushes to that routine's screen.
7. Tap system **Back** (or the Back button) — confirm it pops back to the CalibrationHub, not to the App Drawer or home.
8. In the hub list, swipe **up** — confirm it does NOT open the App Drawer.

**SC-2a Status:** pending

| Check | Result | Notes |
|-------|--------|-------|
| Hub shows routine list with first supported pre-selected | pending | |
| Focus shows icon + title + description + Open button | pending | |
| Tapping a row updates Focus | pending | |
| Unsupported routines shown greyed/dimmed (not hidden) | pending | |
| Open → pushes to routine screen | pending | |
| Back → pops to hub (not to home) | pending | |
| Scrollable list does NOT open App Drawer on swipe-up | pending | |

#### SC-2b: Probe Calibrate (klicky probe — E3 or E5+)

*Note: Printer must be homed before the session can start.*

**On-device steps:**

1. From the hub, Open **Probe Calibrate**. If printer is unhomed, confirm the [Home All] foot appears; home it.
2. Once homed: confirm the [Start] foot appears (accent).
3. Tap **Start** to begin the manual probe session.
4. **While the session is Active (Accept/Abort visible):** try the system **Back gesture** — confirm it is SWALLOWED (no screen exit, no route change). This is the D-09 back-suppression requirement.
5. Use the **Z-nudge column** (vertical 3-cell: Z-up / live-Z readout / Z-down) to position the nozzle to paper-drag height.
6. Use the **step column** (+/step readout/−) to cycle the step size.
7. Tap **Accept** — confirm the foot changes to amber [Save & Restart] (ConfirmGuard).
8. Tap Save & Restart — confirm the SAVE_CONFIG ConfirmGuard appears with amber/caution intent: "Save calibration and restart Klipper? All pending changes will be applied."
9. Optionally: tap Cancel (not Save). Confirm cancel exits the guard without saving.
10. Use Back to return to the hub.

**SC-2b Status:** pending

| Check | Result | Notes |
|-------|--------|-------|
| Probe screen: Home All foot when unhomed | pending | |
| Probe screen: Start foot when homed + ready | pending | |
| Active session: system Back is SWALLOWED (D-09) | pending | |
| Active session: Z-nudge column fires TESTZ | pending | |
| Active session: step column +/− cycles step size | pending | |
| Accept → amber Save & Restart foot | pending | |
| Save & Restart → amber ConfirmGuard | pending | |
| Cancel exits guard without saving | pending | |

#### SC-2c: Bed Mesh

**On-device steps:**

1. From the hub, Open **Bed Mesh**. Confirm profile list shows (or empty state if no profiles).
2. Tap a profile row — confirm it shows as **selected** (accentSoft fill + accentLine border) but is NOT immediately loaded.
3. Tap **Apply** — confirm the profile loads onto the printer (Klipper `BED_MESH_PROFILE LOAD=name`).
4. Tap **Save** — confirm the **SaveName takeover** opens: a text field pre-filled with a timestamp (`YY.MM.DD_HH.MM`).
5. In SaveName mode: clear the field and enter an **invalid name** (e.g. spaces or special chars) — confirm the Confirm/Save button is DISABLED.
6. Enter a **valid name** — confirm the Confirm/Save button becomes ENABLED.
7. Tap Cancel (red foot) — confirm it exits SaveName without saving.
8. Select a profile and tap **Remove** — confirm the **red ConfirmGuard** appears: "Remove this profile? This cannot be undone."
9. Cancel the remove.

**SC-2c Status:** pending

| Check | Result | Notes |
|-------|--------|-------|
| Profile list renders (or "No saved profiles" empty state) | pending | |
| Row tap selects (no immediate load) | pending | |
| Apply loads the selected profile | pending | |
| Save → SaveName takeover with timestamp pre-fill | pending | |
| Invalid name → Confirm button disabled | pending | |
| Valid name → Confirm button enabled | pending | |
| Cancel → exits SaveName without saving | pending | |
| Remove → red ConfirmGuard | pending | |
| Scrollable profile list does NOT open App Drawer on swipe-up | pending | |

#### SC-2d: Screws Tilt

**On-device steps:**

1. From the hub, Open **Screws Tilt**. Confirm the screen shows a bed-level spatial visualization in Focus and a screw list (ListRows) in Field.
2. If unhomed: [Home All] foot. Once homed: [Run] foot.
3. Tap **Run** — confirm Klipper executes `SCREWS_TILT_CALCULATE`.
4. After the run completes: confirm each screw row shows **turn instructions** (e.g. "CW 0:30") in the trailing slot.
5. Tap **Run Again** to re-run. Back returns to hub.

**SC-2d Status:** pending

| Check | Result | Notes |
|-------|--------|-------|
| Spatial bed visualization in Focus | pending | |
| Screw ListRows in Field | pending | |
| Run → executes screws-tilt routine | pending | |
| Result: screw rows show turn instructions | pending | |
| Scrollable screw list does NOT open App Drawer | pending | |

#### SC-2e: Z Tilt / QGL

*Note: greyed/disabled if the printer doesn't support the routine.*

**On-device steps:**

1. From the hub, Open **Z Tilt** (or confirm it's greyed on single-Z printers).
2. If supported: [Home All] when unhomed; [Run] when homed. Confirm the run executes.
3. From the hub, Open **QGL** (or confirm it's greyed on non-CoreXY printers).

**SC-2e Status:** pending

| Check | Result | Notes |
|-------|--------|-------|
| Z Tilt: runs if supported, greyed if not | pending | |
| QGL: runs if supported, greyed if not | pending | |

---

### SC-5: No Functional Regression — Homing/Jog/Probe/Mesh/Screws Intact

**Requirement:** All printer-control functions that existed before Phase 27 continue to work.
The migration is UX-only — no holder or command path changes.

**On-device steps:**

1. **Homing:** From Move, trigger Home All. Confirm all axes home correctly.
2. **Jogging:** Jog X, Y, Z in each direction at multiple step sizes. Confirm correct motion.
3. **Probe routine:** Complete a full Probe Calibrate session (see SC-2b) — confirm the state machine transitions correctly (Idle → Active → Accepted → SAVE_CONFIG or Cancel).
4. **Bed mesh:** Calibrate (run) a new bed mesh profile, save it, apply it — confirm it loads to Klipper.
5. **Screws tilt:** Run the screws-tilt routine — confirm turn data appears.
6. No unexpected crashes, toasts, or frozen states during any of the above.

**SC-5 Status:** pending

| Check | Result | Notes |
|-------|--------|-------|
| Homing works correctly | pending | |
| X/Y/Z jogging works at all step sizes | pending | |
| Probe Calibrate full flow (Idle→Active→Accepted→SAVE_CONFIG/Cancel) | pending | |
| Bed mesh calibrate + save + apply works | pending | |
| Screws tilt run → result data | pending | |
| No crashes or frozen states | pending | |

---

### Conformance Spot-Checks

**On-device steps:**

1. **≥64dp targets:** On Move and any calibration screen, confirm all interactive controls feel easy to tap with a fingertip. Nothing feels tiny.
2. **Scrollable Field suppresses App Drawer:** In the Calibration hub list, Bed Mesh profile list, and Screws list — swipe up inside the list. Confirm the App Drawer does NOT open.
3. **Rotation does not reset state:** While on Bed Mesh with a profile selected, rotate. Confirm the profile selection persists. While on Probe Calibrate in the Active state, rotate — confirm the session does not reset (the state machine stays Active).
4. **S/M/L text size:** (optional) If the dev cycler is accessible, verify the screens are legible at fs=L.

| Check | Result | Notes |
|-------|--------|-------|
| ≥64dp touch targets feel comfortable | pending | |
| Scrollable list swipe-up does NOT open App Drawer | pending | |
| Profile selection survives rotation | pending | |
| Probe Active state survives rotation | pending | |

---

### C3 Vertical-Z Todo Disposition

**Context (D-01):** The C3 todo (`2026-06-05-move-z-vertical-layout-rework.md`) tracked the
long-deferred vertical-Z column rework. Phase 27 implements it as part of the MoveScreen rebuild.
The todo is closed **only if the owner approves the vertical Z column on-device**.

**Status:** pending owner verdict at SC-1

| Disposition | Condition |
|-------------|-----------|
| CLOSED | Owner approves the vertical Z column behavior on-device (SC-1 PASS) |
| REMAINS OPEN | Owner identifies a defect in the vertical Z column requiring a gap fix |

---

## Overall Verdict

| SC | Check | Status |
|----|-------|--------|
| SC-1 | Move in both orientations — spatial grid intact, vertical Z column, distance stepper | **pending** |
| SC-2a | CalibrationHub — list+Focus, nav push/pop, back-stack coherence | **pending** |
| SC-2b | Probe Calibrate — vertical TESTZ columns, state-adaptive foot, back-suppression | **pending** |
| SC-2c | Bed Mesh — profile list, Apply, SaveName takeover, Remove ConfirmGuard | **pending** |
| SC-2d | Screws Tilt — run + turn instructions | **pending** |
| SC-2e | Z Tilt / QGL — runs if supported | **pending** |
| SC-5 | No functional regression — homing/jog/probe/mesh/screws intact | **pending** |
| Conformance | ≥64dp targets, scrollable-Field suppresses drawer, rotation state preservation | **pending** |

**Overall: PENDING — awaiting owner on-device report**

---

## Sign-Off

**Owner:** Matthew
**Date:** pending
**Verdict:** pending
