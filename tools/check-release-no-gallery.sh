#!/usr/bin/env bash
# Phase-3 packaging guard (D-08 / Pitfall 5): assert the debug-only gallery launcher
# is ABSENT from the release APK. Builds the release variant, then uses aapt to dump
# the badging and fails if any gallery launchable-activity is present.
#
# Run from the repo root (WSL). Builds are Windows-side via gw.bat; aapt is the Android
# build-tools binary. Exit code is authoritative.
set -euo pipefail

# Resolve cmd.exe robustly: prefer it on PATH, else the canonical WSL interop path.
# (gw.bat / aapt.exe are Windows binaries driven through cmd.exe from WSL.)
if command -v cmd.exe >/dev/null 2>&1; then
  CMD="cmd.exe"
else
  CMD="/mnt/c/Windows/System32/cmd.exe"
fi

AAPT="E:\\Android\\Sdk\\build-tools\\34.0.0\\aapt.exe"
# The release variant uses splits.abi (armeabi-v7a only, isUniversalApk=false), so the
# single-ABI output is app-armeabi-v7a-release-unsigned.apk — NOT app-release-unsigned.apk.
REL_APK="app\\build\\outputs\\apk\\release\\app-armeabi-v7a-release-unsigned.apk"

# 1. Build the release variant.
"$CMD" /c "E:\\Android\\gw.bat :app:assembleRelease --no-daemon" 2>&1 | tr -d '\r'

# 2. Dump badging and show the launchable-activity lines (expect only MainActivity).
BADGING="$("$CMD" /c "$AAPT dump badging $REL_APK" 2>&1 | tr -d '\r')"
echo "$BADGING" | grep 'launchable-activity' || true

# 3. Fail if the gallery activity leaked into the release manifest.
if echo "$BADGING" | grep -q 'gallery.GalleryActivity'; then
  echo "FAIL: gallery.GalleryActivity present in the RELEASE APK (D-08 violated)" >&2
  exit 1
fi
echo "OK: no gallery launcher in the release APK (D-08 held)"
