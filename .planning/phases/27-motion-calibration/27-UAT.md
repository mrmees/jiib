---
phase: 27
slug: motion-calibration
plan: 07
status: passed
device: flox (Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30)
apk: app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
apk_mtime: "2026-06-12T00:00:12 -0500"
last_p27_commit: c882c97
last_p27_commit_ts: "2026-06-11T23:57:44 -0500"
apk_is_nonstale: true
build_mode: assembleDebug --rerun-tasks (47 tasks executed)
adb_install: Success
created: 2026-06-12
signed_off: "2026-06-12"
owner_verdict: approved
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

## Owner Sign-Off

**Owner verdict (2026-06-12):** "approved" — blanket approval with no per-screen failures reported.

The blanket approval covers all SC-1, SC-2, SC-5, and conformance checklist items listed below,
including the SC-1 vertical Z column (D-01/C3 disposition — see section at bottom). All items
are recorded as **passed** per the owner's authoritative on-device report.

---

## On-Device Checks (flox)

> Owner drove these checks on the physical Nexus 7 2013 (flox) against a live printer
> (E3 192.168.1.121:7125 OR E5+ 192.168.1.120:7125). All items passed per owner blanket
> approval "approved" on 2026-06-12.

---

### SC-1: MoveScreen — Both Orientations (D-01/D-02/D-03)

**Requirement:** MoveScreen renders correctly in PORTRAIT and LANDSCAPE. The spatial XY jog grid
is intact, the vertical Z column (Z-up / live-Z readout + tap-to-home / Z-down) is present, and
the distance stepper column (+/readout/−) sits beside the Z column. In portrait: the XY pad is
capped at ~60% of height — columns are NOT crowded out. In landscape: the pad fills its half-width
full height, columns beside it.

**SC-1 Status:** PASSED

| Check | Result | Notes |
|-------|--------|-------|
| Portrait: XY pad ~60% height, columns visible | **PASSED** | Blanket approval 2026-06-12 |
| Portrait: XY jog X/Y/Z fires correctly | **PASSED** | Blanket approval 2026-06-12 |
| Portrait: vertical Z column (3 equal cells, Z-up/readout/Z-down) | **PASSED** | Blanket approval 2026-06-12 |
| Portrait: Z column center tap → Home Z | **PASSED** | Blanket approval 2026-06-12 |
| Portrait: distance stepper +/− cycles preset steps | **PASSED** | Blanket approval 2026-06-12 |
| Portrait: force-move toggle → red filled when armed | **PASSED** | Blanket approval 2026-06-12 |
| Portrait: Disable Steppers → red ConfirmGuard | **PASSED** | Blanket approval 2026-06-12 |
| Landscape: pad fills half-screen height | **PASSED** | Blanket approval 2026-06-12 |
| Landscape: Z + distance columns beside pad | **PASSED** | Blanket approval 2026-06-12 |
| Landscape: all controls still function | **PASSED** | Blanket approval 2026-06-12 |
| FloatingEStop visible top-left (while printing) | **PASSED** | Blanket approval 2026-06-12 |
| Rotation does NOT reset selected distance | **PASSED** | Blanket approval 2026-06-12 |

---

### SC-2: Calibration Hub + Five Routine Screens — Nav Coherence (D-05..D-10)

**Requirement:** The calibration hub is a list+Focus screen. Nav hub→routine pushes to back-stack;
Back pops to hub. Probe Calibrate: system Back is suppressed while a session is Active. BedMesh:
profile tap selects, Apply loads, SaveName takeover, Remove behind ConfirmGuard. Screws/Tilt:
run → turn instructions / adjustment data.

#### SC-2a: CalibrationHub

**SC-2a Status:** PASSED

| Check | Result | Notes |
|-------|--------|-------|
| Hub shows routine list with first supported pre-selected | **PASSED** | Blanket approval 2026-06-12 |
| Focus shows icon + title + description + Open button | **PASSED** | Blanket approval 2026-06-12 |
| Tapping a row updates Focus | **PASSED** | Blanket approval 2026-06-12 |
| Unsupported routines shown greyed/dimmed (not hidden) | **PASSED** | Blanket approval 2026-06-12 |
| Open → pushes to routine screen | **PASSED** | Blanket approval 2026-06-12 |
| Back → pops to hub (not to home) | **PASSED** | Blanket approval 2026-06-12 |
| Scrollable list does NOT open App Drawer on swipe-up | **PASSED** | Blanket approval 2026-06-12 |

#### SC-2b: Probe Calibrate (klicky probe — E3 or E5+)

*Note: Printer must be homed before the session can start.*

**SC-2b Status:** PASSED

| Check | Result | Notes |
|-------|--------|-------|
| Probe screen: Home All foot when unhomed | **PASSED** | Blanket approval 2026-06-12 |
| Probe screen: Start foot when homed + ready | **PASSED** | Blanket approval 2026-06-12 |
| Active session: system Back is SWALLOWED (D-09) | **PASSED** | Blanket approval 2026-06-12 |
| Active session: Z-nudge column fires TESTZ | **PASSED** | Blanket approval 2026-06-12 |
| Active session: step column +/− cycles step size | **PASSED** | Blanket approval 2026-06-12 |
| Accept → amber Save & Restart foot | **PASSED** | Blanket approval 2026-06-12 |
| Save & Restart → amber ConfirmGuard | **PASSED** | Blanket approval 2026-06-12 |
| Cancel exits guard without saving | **PASSED** | Blanket approval 2026-06-12 |

#### SC-2c: Bed Mesh

**SC-2c Status:** PASSED

| Check | Result | Notes |
|-------|--------|-------|
| Profile list renders (or "No saved profiles" empty state) | **PASSED** | Blanket approval 2026-06-12 |
| Row tap selects (no immediate load) | **PASSED** | Blanket approval 2026-06-12 |
| Apply loads the selected profile | **PASSED** | Blanket approval 2026-06-12 |
| Save → SaveName takeover with timestamp pre-fill | **PASSED** | Blanket approval 2026-06-12 |
| Invalid name → Confirm button disabled | **PASSED** | Blanket approval 2026-06-12 |
| Valid name → Confirm button enabled | **PASSED** | Blanket approval 2026-06-12 |
| Cancel → exits SaveName without saving | **PASSED** | Blanket approval 2026-06-12 |
| Remove → red ConfirmGuard | **PASSED** | Blanket approval 2026-06-12 |
| Scrollable profile list does NOT open App Drawer on swipe-up | **PASSED** | Blanket approval 2026-06-12 |

#### SC-2d: Screws Tilt

**SC-2d Status:** PASSED

| Check | Result | Notes |
|-------|--------|-------|
| Spatial bed visualization in Focus | **PASSED** | Blanket approval 2026-06-12 |
| Screw ListRows in Field | **PASSED** | Blanket approval 2026-06-12 |
| Run → executes screws-tilt routine | **PASSED** | Blanket approval 2026-06-12 |
| Result: screw rows show turn instructions | **PASSED** | Blanket approval 2026-06-12 |
| Scrollable screw list does NOT open App Drawer | **PASSED** | Blanket approval 2026-06-12 |

#### SC-2e: Z Tilt / QGL

*Note: greyed/disabled if the printer doesn't support the routine.*

**SC-2e Status:** PASSED

| Check | Result | Notes |
|-------|--------|-------|
| Z Tilt: runs if supported, greyed if not | **PASSED** | Blanket approval 2026-06-12 |
| QGL: runs if supported, greyed if not | **PASSED** | Blanket approval 2026-06-12 |

---

### SC-5: No Functional Regression — Homing/Jog/Probe/Mesh/Screws Intact

**Requirement:** All printer-control functions that existed before Phase 27 continue to work.
The migration is UX-only — no holder or command path changes.

**SC-5 Status:** PASSED

| Check | Result | Notes |
|-------|--------|-------|
| Homing works correctly | **PASSED** | Blanket approval 2026-06-12 |
| X/Y/Z jogging works at all step sizes | **PASSED** | Blanket approval 2026-06-12 |
| Probe Calibrate full flow (Idle→Active→Accepted→SAVE_CONFIG/Cancel) | **PASSED** | Blanket approval 2026-06-12 |
| Bed mesh calibrate + save + apply works | **PASSED** | Blanket approval 2026-06-12 |
| Screws tilt run → result data | **PASSED** | Blanket approval 2026-06-12 |
| No crashes or frozen states | **PASSED** | Blanket approval 2026-06-12 |

---

### Conformance Spot-Checks

**Status:** PASSED

| Check | Result | Notes |
|-------|--------|-------|
| ≥64dp touch targets feel comfortable | **PASSED** | Blanket approval 2026-06-12 |
| Scrollable list swipe-up does NOT open App Drawer | **PASSED** | Blanket approval 2026-06-12 |
| Profile selection survives rotation | **PASSED** | Blanket approval 2026-06-12 |
| Probe Active state survives rotation | **PASSED** | Blanket approval 2026-06-12 |

---

### C3 Vertical-Z Todo Disposition

**Context (D-01):** The C3 todo (`2026-06-05-move-z-vertical-layout-rework.md`) tracked the
long-deferred vertical-Z column rework. Phase 27 implements it as part of the MoveScreen rebuild.
The todo is closed **only if the owner approves the vertical Z column on-device**.

**Status: CLOSED** — Owner gave blanket "approved" on 2026-06-12, covering SC-1 check items
including "Portrait: vertical Z column (3 equal cells, Z-up/readout/Z-down)" and
"Portrait: Z column center tap → Home Z". No explicit per-item vertical-Z concern was raised.
The blanket approval is treated as authoritative closure of D-01 per the plan's criteria.

Todo file `2026-06-05-move-z-vertical-layout-rework.md` moved to
`.planning/todos/completed/2026-06-12-move-z-vertical-layout-rework.md`.

| Disposition | Condition |
|-------------|-----------|
| **CLOSED** | Owner gave blanket "approved" covering all SC-1 checks (2026-06-12) |

---

## Overall Verdict

| SC | Check | Status |
|----|-------|--------|
| SC-1 | Move in both orientations — spatial grid intact, vertical Z column, distance stepper | **PASSED** |
| SC-2a | CalibrationHub — list+Focus, nav push/pop, back-stack coherence | **PASSED** |
| SC-2b | Probe Calibrate — vertical TESTZ columns, state-adaptive foot, back-suppression | **PASSED** |
| SC-2c | Bed Mesh — profile list, Apply, SaveName takeover, Remove ConfirmGuard | **PASSED** |
| SC-2d | Screws Tilt — run + turn instructions | **PASSED** |
| SC-2e | Z Tilt / QGL — runs if supported | **PASSED** |
| SC-5 | No functional regression — homing/jog/probe/mesh/screws intact | **PASSED** |
| Conformance | ≥64dp targets, scrollable-Field suppresses drawer, rotation state preservation | **PASSED** |

**Overall: PASSED — all SC-1/SC-2/SC-5 items passed; owner approved 2026-06-12.**

---

## Sign-Off

**Owner:** Matthew
**Date:** 2026-06-12
**Verdict:** approved
