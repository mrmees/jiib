---
phase: 03-design-system-theming-foundation
plan: 06
subsystem: ui
tags: [compose, gallery, debug-launcher, theming, views-interop, manifest-merge, d-07, d-08, d-14]

# Dependency graph
requires:
  - phase: 03-03
    provides: DinghyTheme + LocalTokens boundary, ScreenScaffold, OutlinedControl
  - phase: 03-04
    provides: ConfirmGuard, ScrubberPage, SeverityToast
  - phase: 03-05
    provides: ProgressRing (Compose Canvas), GraphViewHost (Views Canvas), RingBuffer
  - phase: 02-02
    provides: PrinterStateStore.printerState StateFlow (the live spine consumed for D-14)
  - phase: 01-03
    provides: SyntheticFeed deterministic ~3 Hz feed (reused, not re-authored)
provides:
  - In-APK component gallery (D-07) — token × component × dark/light/custom × S/M/L preview matrix
  - Debug-only gallery launcher (D-08) via app/src/debug/AndroidManifest.xml — absent from release
  - Cross-toolkit theme-remap proof surface (Compose surfaces + Views Canvas graph re-theme together)
  - D-14 dual-source render: ring/graph driven by BOTH SyntheticFeed AND injected PrinterStateStore
  - Automated release packaging guard (tools/check-release-no-gallery.sh)
affects: [03-07 perf-proof, 04-shell, theming sign-off, manual on-device verification]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure dependency injection into a Compose screen — GalleryScreen constructs none of its deps; GalleryActivity is the sole assembler"
    - "src/debug source-set manifest merge for a debug-only LAUNCHER (NOT a runtime BuildConfig.DEBUG guard)"
    - "Hoisted feed-source selector toggling a RingBuffer between a synthetic feed and a live StateFlow"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt
    - app/src/debug/AndroidManifest.xml
    - app/src/debug/java/works/mees/dinghy/gallery/GalleryActivity.kt
    - tools/check-release-no-gallery.sh
  modified: []

key-decisions:
  - "GalleryActivity injects a connection-less PrinterStateStore as the D-14 live spine — proves the consume-an-injected-store seam without crossing the Phase-4 connection boundary"
  - "Release ABI-split output is app-armeabi-v7a-release-unsigned.apk; the guard script's APK path was corrected accordingly"
  - "Sample custom delta recolors accent/heat/go/stop via TokenDelta.of(Int ARGB) — no raw Compose Color literal, token-purity held"

patterns-established:
  - "Pattern: a debug-only Activity in src/debug/java + a src/debug/AndroidManifest.xml LAUNCHER block is the clean seam to ship a tool only in debug"
  - "Pattern: an automated aapt-badging guard asserts a debug surface is absent from the release APK"

requirements-completed: [THEME-01, THEME-02, UI-01, UI-02, PRIM-01, PRIM-03, PRIM-04]

# Metrics
duration: 7min
completed: 2026-05-31
---

# Phase 3 Plan 06: In-APK Component Gallery (D-07/D-08/D-14) Summary

**A debug-only, on-device component gallery that renders the full token × component × theme × text-size matrix from injected dependencies, re-themes Compose AND the classic-Views graph together on a theme flip, drives its ring/graph from both the synthetic feed and the injected live spine, and is provably absent from the release APK.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-05-31T19:00:03Z
- **Completed:** 2026-05-31T19:06:33Z
- **Tasks:** 2 completed
- **Files modified:** 4 created

## Accomplishments
- **GalleryScreen (D-07/D-14):** a pure-DI Compose screen accepting a `ThemeResolver`, a nullable `PrinterStateStore?`, and a hoisted feed-source selector — constructing none of them. It drives the injected resolver from Dark/Light/custom-delta/S-M-L controls so the whole matrix re-themes live, and feeds one `RingBuffer` (backing `ProgressRing` + `GraphViewHost`) from BOTH `SyntheticFeed().events()` (~3 Hz) and the injected `PrinterStateStore.printerState` extruder temp, via a source toggle.
- **Debug-only launcher (D-08):** `app/src/debug/AndroidManifest.xml` registers `.gallery.GalleryActivity` as a MAIN/LAUNCHER in the debug source set only; `GalleryActivity` (in `src/debug/java`) is the sole dep-assembler, wraps `GalleryScreen` in `DinghyTheme`, opens no Moonraker connection, and sets `FLAG_KEEP_SCREEN_ON`. Main manifest untouched.
- **Automated D-08 packaging guard:** `tools/check-release-no-gallery.sh` builds `:app:assembleRelease` and asserts via `aapt dump badging` that the release APK has no gallery launchable-activity. Verified: debug APK has BOTH `GalleryActivity` + `MainActivity` launchers; release has ONLY `MainActivity`.
- **Cross-toolkit remap:** the active `LocalTokens` are passed to `GraphViewHost`, so a dark↔light↔custom flip recolors the Views Canvas graph at the same instant the Compose surfaces remap.

## Task Commits

Each task was committed atomically:

1. **Task 1: GalleryScreen — token/component matrix wired to injected feed + live spine** - `70cc3d1` (feat)
2. **Task 2: Debug-only launcher + release-manifest guard** - `187202d` (feat)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt` - The D-07 preview-matrix Compose screen; pure DI; dual-source (D-14) ring/graph; renders OutlinedControl (5 intents), ScreenScaffold, ConfirmGuard, ScrubberPage, SeverityToast (4 severities), ProgressRing, GraphViewHost.
- `app/src/debug/AndroidManifest.xml` - Debug-only LAUNCHER registration of GalleryActivity (D-08 manifest-merge seam).
- `app/src/debug/java/works/mees/dinghy/gallery/GalleryActivity.kt` - The sole assembler of GalleryScreen's deps; DinghyTheme boundary; no connection; FLAG_KEEP_SCREEN_ON.
- `tools/check-release-no-gallery.sh` - The automated D-08 release packaging guard (adopted from an out-of-band file; corrected, see Deviations).

## Decisions Made
- **Connection-less live store for D-14:** `GalleryActivity` injects a `PrinterStateStore(scope = lifecycleScope)` with no socket/transport opened. This satisfies D-14's "consume an already-provided StateFlow" without the screen owning a connection lifecycle (the Phase-4 boundary). On the "Live" source the gallery shows the store's current (default/empty) readings — the seam is proven end-to-end; real seeding is a future host's job, not the gallery's.
- **Token purity via Int-ARGB delta:** the sample custom theme uses `TokenDelta.of(Role to 0xFF…toInt())` — Int ARGB, not a Compose `Color(0x…)` literal — so the token-purity gate (zero raw `Color(0x` under `gallery/`) holds.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Unclosed-comment compile error from `/*` inside KDoc**
- **Found during:** Task 1 (GalleryScreen)
- **Issue:** A KDoc line referenced `docs/ui_design/images/*.png`. Kotlin block comments nest, so the `/*` in `images/*` opened a nested comment and the closing `*/` only closed the inner one — `:app:assembleDebug` failed with "Unclosed comment".
- **Fix:** Reworded the KDoc to "the `docs/ui_design/images` mockups" (no `/*` sequence).
- **Files modified:** app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt
- **Verification:** `:app:assembleDebug` then compiled successfully.
- **Committed in:** `70cc3d1` (part of Task 1 commit)

**2. [Rule 3 - Blocking] Guard script pointed at the wrong release-APK filename**
- **Found during:** Task 2 (release guard)
- **Issue:** The out-of-band `check-release-no-gallery.sh` set `REL_APK=app-release-unsigned.apk`, but `splits.abi` (armeabi-v7a only, `isUniversalApk=false`) names the actual output `app-armeabi-v7a-release-unsigned.apk`. The script would `aapt`-fail on a nonexistent path.
- **Fix:** Updated `REL_APK` to the ABI-split filename (with an explanatory comment).
- **Files modified:** tools/check-release-no-gallery.sh
- **Verification:** Confirmed against `app/build/outputs/apk/release/` listing and a successful guard run (exit 0).
- **Committed in:** `187202d` (part of Task 2 commit)

**3. [Rule 3 - Blocking] Bare `cmd.exe` not resolvable in the executor shell**
- **Found during:** Task 2 (release guard)
- **Issue:** The script invoked bare `cmd.exe`, which is not on PATH in this shell — `bash tools/check-release-no-gallery.sh` exited 127 (`cmd.exe: command not found`) before any build.
- **Fix:** Added robust resolution: prefer `cmd.exe` if on PATH, else the canonical interop path `/mnt/c/Windows/System32/cmd.exe`; routed both invocations through it.
- **Files modified:** tools/check-release-no-gallery.sh
- **Verification:** Re-ran the guard — built release and exited 0.
- **Committed in:** `187202d` (part of Task 2 commit)

---

**Total deviations:** 3 auto-fixed (1× Rule 1 bug, 2× Rule 3 blocking)
**Impact on plan:** All three were necessary to make the planned artifacts compile/run. No scope creep — the guard-script fixes are the same file the plan asked us to "confirm matches the local build env." The heads-up about the out-of-band `tools/check-release-no-gallery.sh` was honored: it matched the plan's intent, was adopted, corrected, and committed (no orphan left).

## Issues Encountered
None beyond the auto-fixed deviations above. Pre-existing R8/proguard warnings from retrofit2/okhttp bundled rules appear during the release build; they are upstream-library warnings unrelated to this plan and out of scope.

## User Setup Required
None - no external service configuration required.

This plan delivers the surface for the **manual** sign-off of success criteria #1–#4 (theme remap across both toolkits, S/M/L, Focus/Field/Gutter orientation, controls/primitives) per 03-VALIDATION.md. That sign-off is performed on the real flox tablet by installing the debug APK and eyeballing the gallery against `docs/ui_design` — it is intentionally NOT an automated test in v1 (D-09). The on-device gfxinfo perf proof is plan 03-07.

## Next Phase Readiness
- Gallery is installable on flox (debug APK builds with the gallery launcher); ready for the manual matrix sign-off and the 03-07 perf run (FLAG_KEEP_SCREEN_ON in place).
- D-08 is automated and green — the release packaging guard can run in CI.
- No blockers. Remaining Phase-3 work: 03-07 (on-device perf proof) and the manual sign-offs hosted here.

## Self-Check: PASSED

All 4 created files exist on disk; both task commits (`70cc3d1`, `187202d`) are present in git history.

---
*Phase: 03-design-system-theming-foundation*
*Completed: 2026-05-31*
