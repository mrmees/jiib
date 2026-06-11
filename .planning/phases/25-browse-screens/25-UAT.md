---
phase: 25-browse-screens
plan: "07"
status: approved
device: flox — Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30 / armeabi-v7a
build: release APK (R8-minified, debug-signed via sign-release.bat)
apk_mtime: "2026-06-10 10:18 CDT"
apk_installed: "2026-06-10 10:19 CDT"
uat_completed: "2026-06-10"
owner: Matthew Mees
---

# Phase 25 Browse-Screens: On-Device UAT Checklist

**Device:** flox (Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30 / `armeabi-v7a`)
**Build:** Release APK — R8-minified, debug-signed via `sign-release.bat`
**APK installed:** 2026-06-10 10:19 CDT (mtime postdates the latest fix commit — stale-APK gate PASS)
**Result:** OWNER APPROVED — 2026-06-10 (blanket approval, no issues reported)

Pre-UAT verification: orchestrator confirmed the installed APK was spike-free and contained all
25-03/04/05/06 work via manifest content check. adbd returned to non-root after install.

---

## 1. FilesScreen — Portrait

**What to check:** Screen renders on the jiib kit (translucent ListRows, age-sort SortRow, FootButtonBar with Back/Print/Delete, image-backed DetailCard Focus). Targets ≥64px. `fsSp` text legible. No clipping.

| Check | Result |
|-------|--------|
| Screen opens and renders file list (translucent ListRow tiles) | approved |
| DetailCard Focus shows thumbnail + print metadata (est time, filament, size) | approved |
| SortRow (CalendarClock icon) present + direction toggle works | approved |
| FootButtonBar: Back / Print / Delete present, ≥64px touch targets | approved |
| Print button enabled on file selection; disabled when nothing selected | approved |
| Delete button enabled on file selection; disabled when nothing selected | approved |
| No visual clipping at current text size setting | approved |

---

## 2. FilesScreen — Landscape

| Check | Result |
|-------|--------|
| Screen renders correctly in landscape (ScreenScaffold side-by-side Focus|Field) | approved |
| DetailCard Focus visible with thumbnail; Field shows file list | approved |
| FootButtonBar and SortRow still visible and usable | approved |
| No visual clipping in landscape | approved |

---

## 3. MacrosScreen — Portrait

**What to check:** Screen renders as ListBlock of bookmarked macros (not the old grid). ManageMode toggle. ParamEntry Field-takeover for parameterized macros.

| Check | Result |
|-------|--------|
| Screen opens with a ListBlock of bookmarked macros | approved |
| Foot: Back + Manage buttons present (≥64px) | approved |
| Tapping a no-param macro: ParamEntry mode shows; Execute fires immediately | approved |
| Tapping a param macro: shows param entry with numeric NumpadPage or string TokenTextField | approved |
| Execute fires (macro runs; gcode visible in console) | approved |
| ManageMode: tap Manage → macro list shows pin/unpin checkmarks + Show-hidden toggle | approved |
| No visual clipping | approved |

---

## 4. MacrosScreen — Landscape

| Check | Result |
|-------|--------|
| Screen renders correctly in landscape | approved |
| All three field modes (Launcher/ParamEntry/ManageMode) usable in landscape | approved |
| No visual clipping in landscape | approved |

---

## 5. ConsoleScreen — Portrait

**What to check:** Screen renders field-only (no Focus). RecyclerView scrollback (Views, not Compose — retained by spike verdict). Filter toggles in FootButtonBar.

| Check | Result |
|-------|--------|
| Screen opens with live console scrollback (RecyclerView) | approved |
| Auto-scrolls to newest lines as they arrive | approved |
| FootButtonBar: Back + 3 filter toggle buttons (HideTemps / HideTimelapse / HidePrompts) | approved |
| Toggling a filter OFF: those line types disappear from the visible list | approved |
| Toggling the same filter back ON: hidden lines RE-APPEAR (render-time filter, D-15) | approved |
| No visual clipping | approved |

---

## 6. ConsoleScreen — Landscape

| Check | Result |
|-------|--------|
| Screen renders correctly in landscape | approved |
| Scrollback and filter toggles usable in landscape | approved |
| No visual clipping in landscape | approved |

---

## 7. WebcamScreen — Portrait

**What to check:** Screen opens without crash (D-17). H.264 playback renders. FootButtonBar Back works.

| Check | Result |
|-------|--------|
| Webcam tile in App Drawer opens WITHOUT crash (no AndroidRuntime exception) | approved |
| H.264 feed renders (live video from camera) | approved |
| CamPicker visible if multiple cams configured; single-cam goes straight to feed | approved |
| FootButtonBar Back button present and returns to App Drawer | approved |
| No visual clipping | approved |

---

## 8. WebcamScreen — Landscape

| Check | Result |
|-------|--------|
| Screen renders correctly in landscape (aspect-ratio-aware cam feed) | approved |
| Back button usable in landscape | approved |
| No visual clipping in landscape | approved |

---

## 9. D-08: Files Delete-Scoping During Active Print

**Setup:** Start a print on the printer. Navigate to Files.

| Check | Result |
|-------|--------|
| With a print ACTIVE: select the PRINTING file → Delete button is DISABLED | approved |
| With a print ACTIVE: select a DIFFERENT (non-printing) file → Delete button is ENABLED | approved |
| With idle (no print): Delete works for any selected file | approved |

---

## 10. D-18: Webcam Idle-List Per-Profile HIDE Gating

**Setup:** Two printers configured (E5+ and E3). Ensure webcam is enabled for one printer and disabled for the other in Settings.

| Check | Result |
|-------|--------|
| Printer A with webcam ON: Webcam row appears in the idle App Drawer / home list | approved |
| Printer B with webcam OFF: Webcam row is ABSENT from the idle App Drawer / home list | approved |
| Switching between printers: the Webcam row appears/disappears independently per profile | approved |

---

## 11. Conformance (fsSp S/M/L + Rotation) — All Screens

Cycle text size S → M → L (via Settings or the dev cycler). Check all four screens.

| Screen | S — no clip | M — no clip | L — no clip | Rotation stable |
|--------|------------|------------|------------|----------------|
| Files | approved | approved | approved | approved |
| Macros | approved | approved | approved | approved |
| Console | approved | approved | approved | approved |
| Webcam | approved | approved | approved | approved |

---

## 12. Functional Smokes

| Smoke | Result |
|-------|--------|
| Files: select a file → tap Print → ConfirmGuard shows thumbnail + details → confirm | approved |
| Files: select a file → tap Delete → ConfirmGuard shows → confirm | approved |
| Macros: run a macro with a string param — an injection-y string is REJECTED (toast, no run) | approved |
| Console: live scrollback auto-scrolls; toggle all 3 filters ON then OFF — lines re-appear | approved |
| Webcam: H.264 playback renders; FootButtonBar Back button works | approved |

---

## Summary

| Category | Status |
|----------|--------|
| SC-1: All four screens owner-approved (portrait + landscape) | approved — 2026-06-10 |
| SC-3: Per-screen conformance (fsSp S/M/L, rotation) | approved — 2026-06-10 |
| SC-5: Functional smokes (delete/print/macros/console/webcam) | approved — 2026-06-10 |
| D-08: Delete-scoping during active print | approved — 2026-06-10 |
| D-18: Webcam idle-list per-profile gating | approved — 2026-06-10 |

**Phase 25 gate: PASSED** — all SC-1/SC-3/SC-5/D-08/D-18 rows owner-approved 2026-06-10.
