---
phase: 03-design-system-theming-foundation
plan: 08
subsystem: design-system-primitives
gap_closure: true
tags: [scrubber, confirm-guard, gallery, theming, gesture, uat-gap-closure]
requires:
  - "03-04 design-system primitives (ScrubberPage, ConfirmGuard)"
  - "03-06 in-APK component gallery (GalleryActivity)"
  - "03-03 LocalTokens compose seam"
provides:
  - "ScrubberPage tap-to-set (zero-movement tap registers) via one coordinated awaitEachGesture"
  - "internal fun fractionFromX — host-testable offset→fraction mapping (tap == drag at same x)"
  - "ScrubberPage fill bar + button group shared 16.dp horizontal inset (G-2)"
  - "ConfirmGuard opaque token-bg backdrop under the stop/go intent tint (G-4)"
  - "token-bg painted gallery + MainActivity roots (G-1)"
affects:
  - "every numeric setpoint screen that reuses ScrubberPage (PRIM-01)"
  - "every destructive action that routes through ConfirmGuard (PRIM-03)"
  - "the debug gallery sign-off surface (theme matrix readability)"
tech-stack:
  added: []
  patterns:
    - "awaitEachGesture { awaitFirstDown → set-from-down → loop pressed-move set } — single pointer consumer, no dual-detector race; tap is a zero-length drag"
    - "chained .background(opaqueToken).background(alphaTint) — opaque scrim under translucent intent tint"
    - "token-bg root (Box.background(LocalTokens.current.bg)) replaces bare Material3 Surface() (colorScheme never populated)"
key-files:
  created:
    - "app/src/test/java/works/mees/dinghy/designsystem/ScrubberMappingTest.kt"
  modified:
    - "app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt"
    - "app/src/main/java/works/mees/dinghy/designsystem/ConfirmGuard.kt"
    - "app/src/debug/java/works/mees/dinghy/gallery/GalleryActivity.kt"
    - "app/src/main/java/works/mees/dinghy/MainActivity.kt"
decisions:
  - "fractionFromX extracted as internal top-level (not a local lambda) so the test module proves the mapping host-side — closing the coverage gap that let the original on-device tap-swallow slip past green tests"
  - "G-2 solved by a shared 16.dp horizontal inset, NOT a fixed px bar width (LAYOUT.md non-negotiable #3: ratio/inset only)"
  - "G-4 solved by layering opaque t.bg UNDER the alpha-bearing stop/go tint (no raw Color, no alpha math) — preserves the intent gravity while obscuring content"
metrics:
  tasks_completed: 3
  tasks_total: 4
  files_created: 1
  files_modified: 4
  duration_min: 18
  completed_date: 2026-06-01
---

# Phase 3 Plan 08: Phase-3 UAT Gap Closure (G-1..G-4) Summary

Closed the four Phase-3 on-device UAT gaps in one consolidated pass: ScrubberPage zero-movement
tap-to-set (G-3, major) + shared bar/button width (G-2, cosmetic), an opaque ConfirmGuard backdrop
(G-4, minor), and a token-bg gallery/MainActivity root (G-1, minor debug-only). TDD on the gesture
core (`fractionFromX`), full unit suite green, debug APK rebuilt + reinstalled on flox and gallery
launched — **on-device re-check (Task 4) is the open blocking checkpoint.**

## What Was Built (per gap)

### G-3 (major) — ScrubberPage tap-to-set
- **File:** `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt`
- **Root cause:** `detectDragGestures` requires touch-slop movement before `onDragStart` fires, so a
  pure zero-movement tap was dropped on-device (the WR-01 "fix" never actually resolved it).
- **Fix:** one `awaitEachGesture` block (the re-arming wrapper) — `awaitFirstDown` sets the value
  immediately from the down position (makes a tap register), then loop each still-pressed move.
  Both the down-set and the move-set funnel through `setFromX` → the new
  `internal fun fractionFromX(x, barWidthPx)`. One `pointerInput`, one pointer consumer — no
  dual-detector race. No `detectTapGestures`, no second `pointerInput`, no TextField/KeyboardType.
- **Test:** `ScrubberMappingTest` (6 cases: edges, midpoint, clamp below/above, zero-width no-NaN,
  tap == drag at same x). TDD RED (Unresolved reference) → GREEN.

### G-2 (cosmetic) — shared fill-bar / button-group width
- **File:** `ScrubberPage.kt` (Field bar `padding`).
- **Fix:** bar inset `padding(horizontal = 16.dp, vertical = 24.dp)` to match the gutter button
  group's existing `horizontal = 16.dp`. Shared left-edge + width via shared inset — NOT a fixed px
  bar width (LAYOUT.md non-negotiable #3).

### G-4 (minor) — ConfirmGuard opaque scrim
- **File:** `app/src/main/java/works/mees/dinghy/designsystem/ConfirmGuard.kt:67`
- **Root cause:** root was `.background(tint)` only, where `t.stopSoft`/`t.goSoft` are alpha-bearing
  (…/ .15, …/ .16) — content behind bled through.
- **Fix:** `Box(modifier.fillMaxSize().background(t.bg).background(tint))` — chained `.background`
  paints in order, so the opaque app bg lands first and the translucent intent tint on top. Firmly
  obscures while keeping the stop-soft/go-soft gravity. All color via tokens, zero raw `Color(`.

### G-1 (minor, debug-only + hardening) — token-bg roots
- **Files:** `app/src/debug/.../gallery/GalleryActivity.kt`, `app/src/main/.../MainActivity.kt`
- **Root cause:** bare Material3 `Surface(modifier=…)` defaults to `MaterialTheme.colorScheme.surface`
  — never populated in this app (color flows through `LocalTokens`, not Material colorScheme) — so
  the gallery page bg ignored dark/light and dark-theme text sat on a stuck-light field.
- **Fix:** `Box(Modifier.fillMaxSize().background(LocalTokens.current.bg))` inside the `DinghyTheme`
  boundary, mirroring the production AppShell/SplashScreen pattern; dropped the `material3.Surface`
  import from both. MainActivity got the same fix as defensive hardening (footgun removal).

## Verification

- **Unit:** `ScrubberMappingTest` GREEN (TDD RED→GREEN); **full `:app:testDebugUnitTest` BUILD
  SUCCESSFUL, zero failures** (no regression from the gesture/scrim/root edits).
- **Build:** `:app:assembleDebug` BUILD SUCCESSFUL (debug variant incl. src/debug GalleryActivity).
- **Token-purity guards:** `Color(` == 0 in ScrubberPage.kt and ConfirmGuard.kt; no actual
  `TextField`/`KeyboardType`/`detectTapGestures`/second `pointerInput` in ScrubberPage (the lone
  `TextField`/`Surface` grep hits are KDoc prose, confirmed by line inspection).
- **Install:** `adb -s 0a64b42e install -r app-armeabi-v7a-debug.apk` → **Success**; gallery launched
  via `am start -n works.mees.dinghy/.gallery.GalleryActivity`.

> Build-path note: the debug build is an ABI split (`app-armeabi-v7a-debug.apk`), not the plan's
> assumed `app-debug.apk`. Used the actual split path (flox is `armeabi-v7a`). [Rule 3 — blocking
> issue: corrected the install path so the verify step could run.]

## On-Device Re-Check (Task 4 — OPEN blocking checkpoint)

NOT signed off — awaiting the user on flox. Re-check steps:
1. **G-3:** short TAP (no drag) on a ScrubberPage fill bar at various x — value should JUMP to the
   tapped position each time; drag still scrubs; tap == drag at the same spot; no keyboard ever.
2. **G-2:** the fill bar and its own −/+/Cancel/Apply group share width + left-edge.
3. **G-4:** trigger a ConfirmGuard — backdrop firmly obscures content behind it, only a faint
   red/green tint visible.
4. **G-1:** toggle Dark/Light/Custom in the gallery — the PAGE background switches palettes.

## Deviations from Plan

- **[Rule 3 — blocking issue] Install path corrected.** Plan's verify used
  `app/build/outputs/apk/debug/app-debug.apk`; the project ships an ABI split, so the real artifact
  is `app-armeabi-v7a-debug.apk`. Used the split path (matches flox's `armeabi-v7a`). No code impact.

Otherwise plan executed as written.

## Commits

- `32a613a` test(03): ScrubberMappingTest covers offset→value mapping (tap == drag) [RED]
- `022e8e6` fix(03): G-3 tap-to-set on ScrubberPage via single coordinated gesture + G-2 shared width [GREEN]
- `0b34a84` fix(03): G-4 opaque ConfirmGuard scrim + G-1 token-bg gallery/MainActivity root

## Self-Check: PASSED

All created/modified files present; all three task commits (32a613a, 022e8e6, 0b34a84) in git log.
