---
phase: 11-spool-management-spoolman-camera-qr
plan: 07
subsystem: spool
tags: [wave-4, camera, qr-scan, camerax, activity-result, confirm-first, measured-weight, d-04, d-12, d-14, d-15, d-16, spool-05, spool-06, spool-09, first-hardware-camera, first-runtime-permission]
requires:
  - "11-03: parseSpoolId (D-12 id-only V5 parser) + deriveScanState/ScanState (D-15 headless degrade machine)"
  - "11-05: QrCodeAnalyzer (parse-free ImageAnalysis.Analyzer) + CameraX 1.5.0 deps + CAMERA permission/uses-feature in the manifest"
  - "11-06: SpoolScreen/active-spool-card onScan seam (the stub this plan wires) + SpoolmanClient on SpineHandle + SetSpoolArgs/spoolmanPostSpoolId dispatch"
provides:
  - camera-permission-gate: "CameraPermissionGate — Activity-Result CAMERA glue (request ONLY on entry) feeding deriveScanState; FEATURE_CAMERA_ANY → cameraAvailable; the project's FIRST runtime hardware-permission flow"
  - scan-surface: "ScanSurface — full-screen CameraX PreviewView (AndroidView) + QrCodeAnalyzer bound DEFAULT_BACK_CAMERA + KEEP_ONLY_LATEST; unbindAll() on dispose (D-14); decode → parseSpoolId → AwaitingConfirm; degrade panels with the picker escape (D-15)"
  - scan-confirm-card: "ScanConfirmCard — D-12 confirm-first: resolves the spool detail, green Set-active is the SOLE set-active trigger, archived D-09 warning, scanned host never navigated"
  - measured-weight-page: "MeasuredWeightPage — thin NumpadPage caller (D-04); PUT measure; header shows used = initial − remaining linked"
  - shared-spool-detail-parser: "parseSpoolmanSpoolDetail(envelope, expectedId) on SpoolmanParsers (single-object proxy-v2 detail walk; shared with the confirm card)"
  - scan-overlay-wiring: "ShellNavState.scanActive (transient, reset on recovery Splash) + AppShell ScanSurface overlay; the 11-06 onScan stub (Spool screen + active-spool card) now opens the live scan"
affects:
  - "11-08 (print-start gate hook) is independent; shares the active-spool truth + SpoolmanClient this consumes"
  - "11-09 (end-to-end UAT) runs the scan-to-assign + measured-weight flows on the flox device + the sacrificial spool; the instrumented ScanSurfaceLifecycleTest runs there too"
tech-stack:
  added: []
  patterns:
    - "RESEARCH Pattern 2 (CameraX bindToLifecycle + DisposableEffect unbindAll release) — first on-device hardware camera"
    - "RESEARCH Pattern 3 (rememberLauncherForActivityResult(RequestPermission)) — first runtime dangerous-permission flow"
    - "Transient sub-nav overlay (macroPopupFor precedent) extended to scanActive — reset on recovery Splash"
    - "Thin NumpadPage caller (no fork) for the measured-weight correction page"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/spool/scan/CameraPermission.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanConfirmCard.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/MeasuredWeightPage.kt
  modified:
    - app/src/main/java/works/mees/dinghy/spool/SpoolmanParsers.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/androidTest/java/works/mees/dinghy/spool/ScanSurfaceLifecycleTest.kt
decisions:
  - "CameraPermissionGate is a HEADLESS-feeding glue Composable (onState callback) — it owns ONLY the permission/hardware inputs (permissionGranted/permissionResolved/cameraAvailable) and calls deriveScanState (11-03); the caller (ScanSurface) owns bindFailed + lastDecode. The granted/denied/no-camera mapping is NOT re-implemented here."
  - "CameraX bound ONLY while ScanState.Scanning/Unsupported/NotRecognized (the camera-live states); the bind lives in a CameraPreview sub-composable so AwaitingConfirm/degrade states decompose it → unbindAll() fires the instant the user leaves the live preview, not just on full surface exit (tighter than D-14's minimum)."
  - "The decode parse runs in the analyzer executor callback (parseSpoolId(raw) → lastDecode) — the V5 boundary (11-03) is the SOLE place the untrusted string is interpreted; ScanSurface never reads a host/URL."
  - "Added a SHARED parseSpoolmanSpoolDetail to SpoolmanParsers (single-object envelope walk with an optional expectedId guard) rather than duplicating PrintStatusScreen's private parseSpoolDetail — the confirm card needs the same lone-object detail decode."
  - "scanActive added to ShellNavState as TRANSIENT (reset in resetTransient like macroPopupFor): a live camera surface must NOT survive a recovery Splash — the camera was released on decompose, re-opening a half-state scan is wrong."
  - "The scan surface is a full-screen OVERLAY rendered outside when(dest) (the MacroExecutionPopup precedent), NOT a Dest — matches D-02 (scan is a sub-surface, not a drawer destination) and PATTERNS."
  - "PrintStatusScreen got a distinct onScanSpool param (defaulting to onOpenSpool) so the active-spool card's Scan opens the live scan while Change still routes to the picker — both seams wired to nav.scanActive in AppShell."
  - "MeasuredWeightPage seeds the NumpadPage initial from the spool's current remaining (the empty-spool weight isn't exposed on SpoolmanSpool here); the user overwrites it with the scale reading. The header spells out used = initial − remaining (D-04 caller-side copy, not a NumpadPage change)."
  - "ScanSurfaceLifecycleTest release assertion uses ProcessCameraProvider.isBound(probe) after decompose (no bound use case = released); the no-camera degrade is proven via the device-independent deriveScanState derivation (NoCamera/PermissionDenied/Unsupported/NotRecognized all keep canFallBackToPicker)."
metrics:
  duration: ~16m
  completed: 2026-06-04
  tasks: 3
  files: 9
---

# Phase 11 Plan 07: QR Scan Surface + Confirm-First Assign + Measured Weight Summary

Built the camera half of the Spool view — the project's FIRST on-device hardware camera AND first runtime dangerous-permission flow. The full-screen `ScanSurface` hosts a CameraX `PreviewView` (via `AndroidView`) with the 11-05 `QrCodeAnalyzer` bound `DEFAULT_BACK_CAMERA` + `STRATEGY_KEEP_ONLY_LATEST`, and **releases the camera with `unbindAll()` on dispose** (D-14, mirroring the Phase-10 webcam DisposableEffect discipline — the *pattern* transfers, the network-MJPEG *code* does not). `CameraPermissionGate` requests CAMERA **only on scan entry** via the Activity Result API and feeds the headless `deriveScanState` machine (11-03); every denial / no-camera / busy path degrades to a **"Use picker instead"** escape (D-15 — the manual picker always works). A decoded QR is parsed **id-only** by `parseSpoolId` (the V5 boundary — the scanned host is never read or navigated) and routed to `AwaitingConfirm`; `ScanConfirmCard` resolves the spool detail and its **green "Set active" is the SOLE set-active trigger** (D-12 confirm-first, never auto-loads). `MeasuredWeightPage` reuses `NumpadPage` (no fork) and shows that remaining/used are **linked** (`used = initial − remaining`, D-04). The 11-06 `onScan` stub is now wired: the Spool screen Scan gutter and the active-spool card Scan action open the live scan overlay. `:app:assembleDebug` SUCCESSFUL, `:app:compileDebugAndroidTestKotlin` clean, the full unit suite is **511/0/0**, and the former RED `ScanSurfaceLifecycleTest` is now GREEN with real assertions.

## What Was Built

### Task 1 — CameraPermission Activity-Result glue (commit `2d61207`)
- `CameraPermission.kt`: `CameraPermissionGate(bindFailed, lastDecode, onState, onRequestPermission)` — `rememberLauncherForActivityResult(RequestPermission())` whose callback feeds `permissionGranted`/`permissionResolved`. A `LaunchedEffect(Unit)` requests CAMERA **only on scan entry**: a prior `checkSelfPermission == GRANTED` skips the prompt; only a camera-equipped, not-yet-granted device `launch`es. Camera HARDWARE presence is read once via `hasSystemFeature(FEATURE_CAMERA_ANY)` → `cameraAvailable`. The derived state is the **headless** `deriveScanState` (11-03) — the mapping is NOT re-implemented; `onState` delivers it to the surface.

### Task 2 — ScanSurface + ScanConfirmCard + the onScan wiring (commit `bc7ca99`)
- `ScanSurface.kt`: a full-screen `Column` (preview area + red `Intent.Danger` Back gutter, mirroring `WebcamScreen`). `CameraPermissionGate` derives the `ScanState`; a `when(state)` renders: live `CameraPreview` (Scanning/Unsupported/NotRecognized) with an aiming hint; the `ScanConfirmCard` (AwaitingConfirm); or a `DegradePanel` (PermissionDenied/NoCamera/Busy) with the **"Use picker instead"** escape. `CameraPreview` hosts `PreviewView` in `AndroidView`, and in a `DisposableEffect(lifecycleOwner)` builds `Preview` + `ImageAnalysis(KEEP_ONLY_LATEST)` carrying `QrCodeAnalyzer { raw -> onDecode(parseSpoolId(raw)) }`, binds `DEFAULT_BACK_CAMERA`, and **`onDispose { boundProvider?.unbindAll(); analyzerExecutor.shutdown() }`** (D-14). A provider/bind failure → `onBindFailed(true)` → `Busy`. **Never** references `DEFAULT_FRONT_CAMERA`.
- `ScanConfirmCard.kt`: on `AwaitingConfirm(id)` resolves the detail via `client.getSpool(id)` → `parseSpoolmanSpoolDetail`; shows split swatch / material·name / vendor / remaining (26sp GeistMono hero) / location / the **archived D-09 warning**. The **green "Set active" is the only `onConfirm` trigger**; red Back cancels (clears the decode → back to scanning). A failed detail read still allows confirm on a lightweight "Spool {id}" shape. The scanned host is never navigated.
- `SpoolmanParsers.kt`: added shared `parseSpoolmanSpoolDetail(result, expectedId)` (single-object proxy-v2 `response` walk; optional id-match guard; best-effort null, never throws).
- Wiring: `ShellNavState.scanActive` (transient, reset in `resetTransient`); `AppShell` renders the `ScanSurface` overlay when `scanActive` (outside `when(dest)`, the MacroExecutionPopup precedent) with a `BackHandler` to close it; the Spool screen `onScan` and a new `PrintStatusScreen.onScanSpool` both set `nav.scanActive = true`; `onConfirm` dispatches `spoolmanPostSpoolId(SetSpoolArgs(id))` (D-13) and closes; `onUsePicker` closes and navigates to `Dest.Spool` (D-15).

### Task 3 — MeasuredWeightPage (commit `70bbac2`)
- `MeasuredWeightPage.kt`: a **thin caller of `NumpadPage`** (no fork) — `label="Gross weight"`, `unit="g"`, `allowDecimal=true`, `range=0..10000g`, seeded from the spool's current remaining. `onSet(grams)` → `client.measureSpool(spool.id, grams)` (PUT `/v1/spool/{id}/measure`, D-04) then `onMeasured()`. A caller-side header names the spool, shows the current **remaining + used** as the GeistMono tabular pair (26sp), and spells out the load-bearing **`used = initial − remaining`** linked relationship (D-04 / Pitfall 7). Numeric-only entry; `onCancel` returns without a write.

### Test — ScanSurfaceLifecycleTest turned GREEN (commit `9283814`)
- `(1)` release-on-dispose: composes `ScanSurface`, decomposes it, asserts the shared `ProcessCameraProvider` has no bound probe use case afterward (`unbindAll()` ran in `onDispose`, D-14).
- `(2)` no-camera degrade: `deriveScanState(cameraAvailable=false) == NoCamera` with `canFallBackToPicker`; `PermissionDenied` and `Unsupported`/`NotRecognized` all keep the picker escape (D-15) — the device-independent authoritative proof.

## Verification

- `:app:compileDebugKotlin` → BUILD SUCCESSFUL after each task.
- `:app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL (only `createComposeRule` deprecation warnings, identical to the existing webcam tests — no errors).
- `:app:testDebugUnitTest` (full) → **511 tests, 0 failures, 0 errors, 0 skipped** — no regressions.
- `:app:assembleDebug` → BUILD SUCCESSFUL — the scan overlay wiring lands in the live route graph and the APK packages.
- Grep gates: `DEFAULT_FRONT_CAMERA` in `ScanSurface.kt` → **0** (never referenced); `unbindAll` → present (in `onDispose`); `DEFAULT_BACK_CAMERA` + `STRATEGY_KEEP_ONLY_LATEST` → present; decode → `parseSpoolId` → `AwaitingConfirm` path exists and set-active fires only inside the confirm action; `MeasuredWeightPage` calls `NumpadPage` (no forked numeric entry) + `measureSpool`.
- **Font scale (D-16): NO hardcoded `.sp`** in any of the four new files — every `.sp` sits on an `fsSp(...)` call (grep for raw `N.sp`/`Nf.sp` literals not on `fsSp(` → none). Scale matches D-16: confirm-card spool title 22sp, remaining tabular 26sp GeistMono, detail rows 17sp, scan-status text 17sp, metadata floor 15sp (never 12–13); measured-weight figures 26sp, relationship line 17sp.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Shared single-object spool-detail parser**
- **Found during:** Task 2 (the confirm card must resolve `/v1/spool/{id}` detail, but the only detail decode — `parseSpoolDetail` — was PRIVATE to `PrintStatusScreen`; `parseSpoolmanSpools` returns empty for a lone-object response).
- **Issue:** Without a shared single-object walk the confirm card could never resolve a spool detail.
- **Fix:** Added `parseSpoolmanSpoolDetail(result, expectedId)` to the shared `SpoolmanParsers` (the canonical parser home), with the same null-safe envelope-`response`-object decode + an optional `expectedId` guard. Left `PrintStatusScreen`'s private copy untouched (no behavior change there) — a future cleanup can fold it onto the shared one.
- **Files modified:** `SpoolmanParsers.kt`.
- **Commit:** `bc7ca99`.

**2. [Rule 2 - Missing functionality] Distinct onScanSpool seam on PrintStatusScreen**
- **Found during:** Task 2 (the active-spool card already exposes a dedicated `onScan` hook, but 11-06 routed it to `onOpenSpool` (→ the picker) as a stopgap; the plan calls for the card Scan to open the live scan).
- **Issue:** Wiring the card Scan to the scan surface needed a seam distinct from the Change/picker route.
- **Fix:** Added `onScanSpool: () -> Unit = onOpenSpool` to `PrintStatusScreen` (defaults to the picker for safety) and pointed the card's `onScan` at it; `AppShell` wires it to `nav.scanActive = true`.
- **Files modified:** `PrintStatusScreen.kt`, `AppShell.kt`.
- **Commit:** `bc7ca99`.

No other deviations — the locked decisions held: CameraX bound `DEFAULT_BACK_CAMERA` + `KEEP_ONLY_LATEST` releasing on dispose (D-14), Activity-Result CAMERA only on entry with the picker degrade (D-15), confirm-first id-only assign (D-12), NumpadPage-reused measured weight with the linked copy (D-04), and all text via `fsSp` at the D-16 scale.

## Known Stubs

None. All four files are complete production implementations. The instrumented `ScanSurfaceLifecycleTest` device RUN lands at the 11-09 flox UAT (its on-device camera bind/release path needs real hardware); the assertions are real and compile-clean here. `CameraPermissionGate.onRequestPermission` is an optional null-defaulted rationale hook (unused by the current surface, available for a future denial-rationale UI) — not a stub, an extension seam.

## Threat Flags

None beyond the plan's `<threat_model>`. The register is mitigated as prescribed: T-11-07-01 (hostile QR host) — `parseSpoolId` (11-03) returns id only, the host is never read/navigated, non-spoolman → Unsupported/NotRecognized stay scanning; T-11-07-02 (auto-set bypass) — valid decode → `AwaitingConfirm` only, set-active fires SOLELY on the green confirm in `ScanConfirmCard`; T-11-07-03 (camera over-grant/frame leak) — CAMERA requested only on entry, `unbindAll()` on dispose, decode in-memory (no frame stored), `uses-feature required=false` (11-05); T-11-07-05 (camera stall/front fixed-focus) — `QrCodeAnalyzer` closes in `finally` (11-05), `DEFAULT_BACK_CAMERA` first, never front. No new network endpoints/auth/schema surface beyond the planned `getSpool`/`measureSpool` proxy reads + the existing `post_spool_id` write. T-11-07-04 (measured-weight write on a real spool) is the 11-09 UAT's save→change→restore protocol on the sacrificial spool.

## Self-Check: PASSED

- Files created: `CameraPermission.kt`, `ScanSurface.kt`, `ScanConfirmCard.kt` (under `ui/spool/scan/`), `MeasuredWeightPage.kt` (under `ui/spool/`) — all FOUND.
- Commits: `2d61207`, `bc7ca99`, `70bbac2`, `9283814` — all FOUND in git log.
- Gates: `:app:assembleDebug` + `:app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL; full `:app:testDebugUnitTest` 511/0/0; zero hardcoded `.sp` in the new files; no `DEFAULT_FRONT_CAMERA`; `unbindAll` in `onDispose`.
