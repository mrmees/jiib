---
phase: 25-browse-screens
plan: "07"
status: pending
device: flox — Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30 / armeabi-v7a
build: release APK (R8-minified, debug-signed via sign-release.bat)
apk_mtime: "2026-06-10 10:18 CDT"
apk_installed: "2026-06-10 10:19 CDT"
---

# Phase 25 Browse-Screens: On-Device UAT Checklist

**Device:** flox (Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30 / `armeabi-v7a`)
**Build:** Release APK — R8-minified, debug-signed via `sign-release.bat`
**APK installed:** 2026-06-10 10:19 CDT (mtime postdates the latest fix commit — stale-APK gate PASS)

Connect to a live printer (E5+ = 192.168.1.120:7125 or E3 = 192.168.1.121:7125).
Record each row's result: `approved` / `issue: <description>`.

---

## 1. FilesScreen — Portrait

**What to check:** Screen renders on the jiib kit (translucent ListRows, age-sort SortRow, FootButtonBar with Back/Print/Delete, image-backed DetailCard Focus). Targets ≥64px. `fsSp` text legible. No clipping.

| Check | Result |
|-------|--------|
| Screen opens and renders file list (translucent ListRow tiles) | PENDING |
| DetailCard Focus shows thumbnail + print metadata (est time, filament, size) | PENDING |
| SortRow (CalendarClock icon) present + direction toggle works | PENDING |
| FootButtonBar: Back / Print / Delete present, ≥64px touch targets | PENDING |
| Print button enabled on file selection; disabled when nothing selected | PENDING |
| Delete button enabled on file selection; disabled when nothing selected | PENDING |
| No visual clipping at current text size setting | PENDING |

---

## 2. FilesScreen — Landscape

| Check | Result |
|-------|--------|
| Screen renders correctly in landscape (ScreenScaffold side-by-side Focus|Field) | PENDING |
| DetailCard Focus visible with thumbnail; Field shows file list | PENDING |
| FootButtonBar and SortRow still visible and usable | PENDING |
| No visual clipping in landscape | PENDING |

---

## 3. MacrosScreen — Portrait

**What to check:** Screen renders as ListBlock of bookmarked macros (not the old grid). ManageMode toggle. ParamEntry Field-takeover for parameterized macros.

| Check | Result |
|-------|--------|
| Screen opens with a ListBlock of bookmarked macros | PENDING |
| Foot: Back + Manage buttons present (≥64px) | PENDING |
| Tapping a no-param macro: ParamEntry mode shows; Execute fires immediately | PENDING |
| Tapping a param macro: shows param entry with numeric NumpadPage or string TokenTextField | PENDING |
| Execute fires (macro runs; gcode visible in console) | PENDING |
| ManageMode: tap Manage → macro list shows pin/unpin checkmarks + Show-hidden toggle | PENDING |
| No visual clipping | PENDING |

---

## 4. MacrosScreen — Landscape

| Check | Result |
|-------|--------|
| Screen renders correctly in landscape | PENDING |
| All three field modes (Launcher/ParamEntry/ManageMode) usable in landscape | PENDING |
| No visual clipping in landscape | PENDING |

---

## 5. ConsoleScreen — Portrait

**What to check:** Screen renders field-only (no Focus). RecyclerView scrollback (Views, not Compose — retained by spike verdict). Filter toggles in FootButtonBar.

| Check | Result |
|-------|--------|
| Screen opens with live console scrollback (RecyclerView) | PENDING |
| Auto-scrolls to newest lines as they arrive | PENDING |
| FootButtonBar: Back + 3 filter toggle buttons (HideTemps / HideTimelapse / HidePrompts) | PENDING |
| Toggling a filter OFF: those line types disappear from the visible list | PENDING |
| Toggling the same filter back ON: hidden lines RE-APPEAR (render-time filter, D-15) | PENDING |
| No visual clipping | PENDING |

---

## 6. ConsoleScreen — Landscape

| Check | Result |
|-------|--------|
| Screen renders correctly in landscape | PENDING |
| Scrollback and filter toggles usable in landscape | PENDING |
| No visual clipping in landscape | PENDING |

---

## 7. WebcamScreen — Portrait

**What to check:** Screen opens without crash (D-17). H.264 playback renders. FootButtonBar Back works.

| Check | Result |
|-------|--------|
| Webcam tile in App Drawer opens WITHOUT crash (no AndroidRuntime exception) | PENDING |
| H.264 feed renders (live video from camera) | PENDING |
| CamPicker visible if multiple cams configured; single-cam goes straight to feed | PENDING |
| FootButtonBar Back button present and returns to App Drawer | PENDING |
| No visual clipping | PENDING |

---

## 8. WebcamScreen — Landscape

| Check | Result |
|-------|--------|
| Screen renders correctly in landscape (aspect-ratio-aware cam feed) | PENDING |
| Back button usable in landscape | PENDING |
| No visual clipping in landscape | PENDING |

---

## 9. D-08: Files Delete-Scoping During Active Print

**Setup:** Start a print on the printer. Navigate to Files.

| Check | Result |
|-------|--------|
| With a print ACTIVE: select the PRINTING file → Delete button is DISABLED | PENDING |
| With a print ACTIVE: select a DIFFERENT (non-printing) file → Delete button is ENABLED | PENDING |
| With idle (no print): Delete works for any selected file | PENDING |

---

## 10. D-18: Webcam Idle-List Per-Profile HIDE Gating

**Setup:** Two printers configured (E5+ and E3). Ensure webcam is enabled for one printer and disabled for the other in Settings.

| Check | Result |
|-------|--------|
| Printer A with webcam ON: Webcam row appears in the idle App Drawer / home list | PENDING |
| Printer B with webcam OFF: Webcam row is ABSENT from the idle App Drawer / home list | PENDING |
| Switching between printers: the Webcam row appears/disappears independently per profile | PENDING |

---

## 11. Conformance (fsSp S/M/L + Rotation) — All Screens

Cycle text size S → M → L (via Settings or the dev cycler). Check all four screens.

| Screen | S — no clip | M — no clip | L — no clip | Rotation stable |
|--------|------------|------------|------------|----------------|
| Files | PENDING | PENDING | PENDING | PENDING |
| Macros | PENDING | PENDING | PENDING | PENDING |
| Console | PENDING | PENDING | PENDING | PENDING |
| Webcam | PENDING | PENDING | PENDING | PENDING |

---

## 12. Functional Smokes

| Smoke | Result |
|-------|--------|
| Files: select a file → tap Print → ConfirmGuard shows thumbnail + details → confirm | PENDING |
| Files: select a file → tap Delete → ConfirmGuard shows → confirm | PENDING |
| Macros: run a macro with a string param — an injection-y string is REJECTED (toast, no run) | PENDING |
| Console: live scrollback auto-scrolls; toggle all 3 filters ON then OFF — lines re-appear | PENDING |
| Webcam: H.264 playback renders; FootButtonBar Back works | PENDING |

---

## Summary

| Category | Status |
|----------|--------|
| SC-1: All four screens owner-approved (portrait + landscape) | PENDING |
| SC-3: Per-screen conformance (fsSp S/M/L, rotation) | PENDING |
| SC-5: Functional smokes (delete/print/macros/console/webcam) | PENDING |
| D-08: Delete-scoping during active print | PENDING |
| D-18: Webcam idle-list per-profile gating | PENDING |

**Phase 25 gate:** This UAT must record `approved` for all SC-1/SC-3/SC-5/D-08/D-18 rows before the phase is verified complete.
