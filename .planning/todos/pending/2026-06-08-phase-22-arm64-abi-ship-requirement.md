---
created: 2026-06-08T16:45:00Z
title: Ship requirement — release APK must include arm64-v8a (modern 64-bit-only phones)
area: build
target_phase: 22
files:
  - app/build.gradle.kts:53
---

## Problem

The release APK is ABI-split to **`armeabi-v7a` ONLY** (`app/build.gradle.kts` `splits { abi { include("armeabi-v7a"); isUniversalApk = false } }`, the D-01a Nexus-7-floor decision). That is correct for the 32-bit dev floor but **wrong for the shipped artifact** — every modern 64-bit-only flagship will reject a v7a-only APK that carries native libs (Media3/ExoPlayer, CameraX, zxing) with `INSTALL_FAILED_NO_MATCHING_ABIS`.

**Empirically confirmed 2026-06-08 on a Samsung Galaxy S25 Ultra (`SM_S938U`, Snapdragon 8 Elite):**
- `ro.product.cpu.abilist` = `arm64-v8a` only; `abilist32` = EMPTY (no 32-bit execution at all).
- Android 16 / API 36 — far above the minSdk-23 floor; the app CODE is fully compatible.
- A throwaway `arm64-v8a` debug build (split temporarily set to `include("armeabi-v7a", "arm64-v8a")`, then reverted) installed and launched cleanly on the S25 Ultra — `MainActivity` resumed, no crash, arm64 native libs loaded (no `UnsatisfiedLink`/`dlopen` errors). So the only gap is the missing arm64 slice.

## Requirement (Phase 22 — Ship)

The GitHub-Releases artifact MUST run on both the Nexus 7 2013 (armeabi-v7a floor) AND modern 64-bit-only devices (arm64-v8a). Decide the strategy:
- **Per-ABI release APKs** (`include("armeabi-v7a", "arm64-v8a")`, `isUniversalApk = false`) → publish both `app-armeabi-v7a-release.apk` + `app-arm64-v8a-release.apk` as separate release assets, OR
- **Universal APK** (`isUniversalApk = true`) → one fatter APK carrying both slices (simpler for users, larger download).

Keep the merged-manifest minSdk-23 floor assertion (`verifyMinSdkRelease`) intact regardless. Re-confirm the signed release variant installs + runs on BOTH a v7a device (flox) and an arm64 device (S25 Ultra) as part of the ship UAT. Note: the current `// D-01a` comment in build.gradle.kts asserting "v7a ONLY ... can never carry an arm64 slice" must be updated when this lands.
