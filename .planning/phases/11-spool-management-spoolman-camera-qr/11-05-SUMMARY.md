---
phase: 11-spool-management-spoolman-camera-qr
plan: 05
subsystem: spool
tags: [wave-3, camera, qr, zxing, camerax, version-landmine, minsdk-23-floor, d-14, d-15, spool-05, spool-06]
requires:
  - "11-01: the version-catalog idiom + verifyMinSdk PKG-02 gate + the spool test package layout"
  - "11-03: QrPayloadParser (D-12) — the headless parse the analyzer's onResult string feeds"
provides:
  - camera-qr-toolchain: "CameraX 1.5.0 (4 artifacts) + com.google.zxing:core 3.3.3 pinned in the catalog + wired into app deps; merged-manifest minSdk held EXACTLY 23 (verifyMinSdkRelease)"
  - qr-code-analyzer: "QrCodeAnalyzer : ImageAnalysis.Analyzer — QR-only zxing decode of a YUV frame's luma plane (rowStride, always-close), emits the RAW decoded string to an injectable onResult; parse-free"
  - zxing-version-lock: "ZxingDecodeVersionTest — a real JVM QR round-trip through HybridBinarizer+MultiFormatReader that locks the 3.3.3 pin against the API-23 decode-path regression"
  - camera-manifest: "CAMERA permission + camera/autofocus uses-feature required=false (D-15: camera-less device still installs, degrades to picker)"
affects:
  - "11-07 (ScanSurface) binds this QrCodeAnalyzer to a CameraX preview (DEFAULT_BACK_CAMERA + continuous-AF, KEEP_ONLY_LATEST, DisposableEffect release) and owns the Activity-Result CAMERA permission flow"
  - "11-06 (CameraPermission glue) consumes the CAMERA permission declared here"
tech-stack:
  added:
    - "androidx.camera:camera-core/camera2/lifecycle/view 1.5.0 (floor == 23)"
    - "com.google.zxing:core 3.3.3 (PINNED — NOT 3.4.0+)"
  patterns:
    - "Pinned-with-comment version-catalog idiom extended to the camera/QR landmine deps"
    - "RESEARCH Pattern 1 verbatim ImageAnalysis.Analyzer (rowStride luma + always-close + parse-free onResult)"
    - "JVM decode-path version-lock test (the mock-vs-reality guard for an emulator-invisible regression)"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/spool/scan/QrCodeAnalyzer.kt
    - app/src/test/java/works/mees/dinghy/spool/ZxingDecodeVersionTest.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/AndroidManifest.xml
decisions:
  - "CameraX 1.5.0 holds the minSdk-23 floor on the MERGED manifest (verifyMinSdkRelease green) — NO Camera2 fallback needed; the highest-risk floor-break Pitfall 2 did not materialize"
  - "zxing:core pinned to 3.3.3 with an explicit catalog comment; the JVM ZxingDecodeVersionTest decodes through HybridBinarizer+MultiFormatReader (the FinderPatternFinder List.sort surface) so a future 3.4.0+ drift erroring on API 23 surfaces in CI, not silently on-device"
  - "uses-feature camera + autofocus required=false (D-15) so a camera-less device still installs and degrades to the manual spool picker; no coreLibraryDesugaring added (the 3.3.3 pin avoids it)"
  - "QrCodeAnalyzer emits the RAW decoded string only (injectable onResult); QrPayloadParser (11-03) owns the untrusted-input parse — the analyzer stays parse-free and the D-12 boundary stays in one place"
  - "Widened the analyzer's try to cover planes[0]/buffer access (Rule 2 hardening) so a malformed frame can never leak the ImageProxy past the finally — the verbatim research snippet left that access outside the try"
metrics:
  duration: ~9m
  completed: 2026-06-04
  tasks: 3
  files: 5
---

# Phase 11 Plan 05: Camera + QR Decode Toolchain Summary

Landed the camera/QR toolchain — the phase's highest-risk plan (net-new on-device camera code plus the two version landmines) — without breaking the Nexus-7-2013 minSdk-23 floor. CameraX 1.5.0 (4 artifacts) and `com.google.zxing:core` 3.3.3 are pinned in the version catalog and wired into the app; the merged-manifest minSdk held EXACTLY 23 (`verifyMinSdkRelease` green), so **no Camera2 fallback was needed**. The manifest now declares an OPTIONAL camera (`uses-feature required="false"`, D-15) + the CAMERA runtime permission. The lean `QrCodeAnalyzer` decodes QR-only frames from the luma plane (rowStride, always-close, parse-free), and a real JVM `ZxingDecodeVersionTest` locks the 3.3.3 pin against the API-23 decode-path regression that an emulator would never catch.

## What Was Built

### Task 1 — pin + wire CameraX/zxing, declare optional camera, prove the floor (commit `7a0164a`)
- `libs.versions.toml`: added `cameraX = "1.5.0"` and `zxingCore = "3.3.3"` to `[versions]` (each with a load-bearing comment: floor==23 PROVEN-by-verifyMinSdk for CameraX; the 3.4.0+ `List.sort`/API-24 decode-crash warning for zxing), and five `[libraries]` coordinates (4 `androidx.camera:camera-{core,camera2,lifecycle,view}` + `com.google.zxing:core`).
- `app/build.gradle.kts`: wired the five `implementation(libs.…)` lines after the Coil block; **no `coreLibraryDesugaring`** (the 3.3.3 pin avoids it).
- `AndroidManifest.xml` (additive, Phase-1 cleartext posture untouched): `CAMERA` permission + `android.hardware.camera` and `android.hardware.camera.autofocus` `uses-feature required="false"` (D-15 — camera-less device still installs, degrades to picker).
- **`verifyMinSdkRelease` PASSED**: `merged-manifest minSdkVersion == 23 for variant 'release'` — CameraX 1.5.0 held the floor; the Pitfall-2 floor-break did not occur, so the Camera2 fallback path was not needed. `compileDebugKotlin` succeeded.

### Task 2 — ZxingDecodeVersionTest, the 3.3.3 version lock (commit `2308889`)
- `ZxingDecodeVersionTest.kt`: a pure-JVM round-trip — encode `"web+spoolman:s-42"` to a QR `BitMatrix` (`QRCodeWriter`), then decode it back through the SAME on-device pipeline (`HybridBinarizer` → QR-hinted `MultiFormatReader.decodeWithState`), asserting the text round-trips.
- A small in-test `BitMatrixLuminanceSource` (black module → luma 0, white → 255) keeps it AWT-free while still exercising `FinderPatternFinder` — the exact `List.sort` surface that `NoSuchMethodError`-crashes on API 23 from zxing 3.4.0+. flox is API 30, so a device run would never surface that drift; this CI test is the only guard. A future bump to 3.4.0+ erroring on the decode path surfaces here, not silently on a floor device.
- GREEN: `--tests *ZxingDecodeVersionTest` → BUILD SUCCESSFUL.

### Task 3 — QrCodeAnalyzer, the QR-only ImageAnalysis.Analyzer (commit `499ffa4`)
- `QrCodeAnalyzer.kt`: implements `androidx.camera.core.ImageAnalysis.Analyzer` with a constructor `onResult: (String) -> Unit` (injectable → the parse side stays headless-testable). RESEARCH Pattern 1 verbatim: a reused `MultiFormatReader` hinted `POSSIBLE_FORMATS = [QR_CODE]`; in `analyze` it reads `image.planes[0].buffer` into a `ByteArray`, builds `PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)` (**rowStride** as the row length, `image.width` ONLY in the crop-width slot), wraps in `BinaryBitmap(HybridBinarizer(source))`, decodes, and emits the raw text.
- `NotFoundException` is a silent no-op (no QR this frame is normal); any other `Throwable` is swallowed (one odd frame never kills the scan loop); `image.close()` is in a `finally` (`KEEP_ONLY_LATEST` stalls otherwise).
- The analyzer is parse-free — it emits the RAW decoded string; `QrPayloadParser` (11-03, D-12 Security-V5 boundary) owns the untrusted-input validation. Only state is the reused `MultiFormatReader`.
- `compileDebugKotlin` succeeded.

## Verification

- `:app:verifyMinSdkRelease` → **PASS** (`merged-manifest minSdkVersion == 23`, variant `release`) after the camera deps landed. The floor held — no Camera2 fallback.
- `:app:testDebugUnitTest --tests *ZxingDecodeVersionTest` → **GREEN** (real JVM QR decode round-trips).
- Full `:app:testDebugUnitTest` → **511 tests, 0 failed, 0 ignored** (BUILD SUCCESSFUL). No regressions; 11-04's former RED `SpoolmanNotifyRouterTest` is now green too (that plan completed). The instrumented `ScanSurfaceLifecycleTest` stays 11-07's (not introduced here).
- `:app:assembleDebug` → **BUILD SUCCESSFUL** after the dep additions.
- Analyzer discipline grep: `rowStride` present at the row-length slot; `image.width` only in the crop-width slot; `image.close()` in `finally`; `NotFoundException` a silent no-op.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] Widened the analyzer try to cover the plane/buffer access**
- **Found during:** Task 3
- **Issue:** The verbatim RESEARCH Pattern-1 snippet places `image.planes[0].buffer` + `ByteArray` copy OUTSIDE the `try { … } finally { image.close() }`. If that access ever threw (a malformed/odd frame), the `ImageProxy` would leak past the `finally` and `STRATEGY_KEEP_ONLY_LATEST` would stall the whole scan pipeline (the exact anti-pattern the close-in-finally exists to prevent).
- **Fix:** Moved the entire `analyze` body inside the outer `try` whose `finally` closes the proxy, with the inner `try` still giving `NotFoundException` its silent no-op. `image.close()` now runs for ALL frames, decode-success or frame-error.
- **Files modified:** `app/src/main/java/works/mees/dinghy/ui/spool/scan/QrCodeAnalyzer.kt`
- **Commit:** `499ffa4`

No other deviations — the two load-bearing landmines resolved as designed: CameraX 1.5.0 held minSdk 23 (no Camera2 fallback), and the zxing 3.3.3 pin is locked by a real JVM decode.

## Known Stubs

None. All three deliverables are complete production implementations. The camera PREVIEW binding, permission Activity-Result flow, and lifecycle release are deliberately out of scope here — they are 11-06/11-07's net-new surfaces that consume this plan's CAMERA permission and `QrCodeAnalyzer`.

## Threat Flags

None — no new security surface beyond the plan's `<threat_model>`. T-11-05-01 (zxing drift) mitigated by the 3.3.3 pin + `ZxingDecodeVersionTest`; T-11-05-02 (floor break) mitigated by CameraX 1.5.0 + `verifyMinSdkRelease`; T-11-05-03 (over-broad camera) mitigated by `uses-feature required="false"` (permission request deferred to 11-07); T-11-05-04 (unclosed proxy) mitigated by `image.close()` in `finally` + rowStride source. The supply-chain coordinates are the canonical first-party Google/AndroidX + ZXing artifacts (RESEARCH Package Legitimacy Audit, T-11-05-SC accept — no legitimacy checkpoint required).

## Self-Check: PASSED

- Files: `QrCodeAnalyzer.kt` (FOUND under `app/src/main/.../ui/spool/scan/`) + `ZxingDecodeVersionTest.kt` (FOUND under `app/src/test/.../spool/`); `libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml` all modified.
- Commits: `7a0164a`, `2308889`, `499ffa4` all present in `git log`.
- Gates: `verifyMinSdkRelease` green (minSdk 23), `ZxingDecodeVersionTest` green, full suite 511/0/0, `assembleDebug` BUILD SUCCESSFUL.
