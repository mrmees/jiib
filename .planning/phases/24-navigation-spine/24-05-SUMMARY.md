---
phase: 24-navigation-spine
plan: 05
subsystem: ui
tags: [android, compose, moonraker, navigation, uat, flox, e-stop, font-scale]

# Dependency graph
requires:
  - phase: 24-navigation-spine
    provides: "NavHost shell (24-03) + morphing waterfall root (24-04) merged tree"
provides:
  - "Owner-verified on-device gate for Phase 24 navigation spine on flox / Adreno 320"
  - "UAT-driven styling fix: FloatingEStop glyph font-scale-stable + 64dp square box"
  - "24-UAT.md with SC-1..SC-5 PASS verdicts + deferred items recorded"
affects: [25-browse, 26-adjustment, 29-ship]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Font-scale-stable icon-only glyph: sizeSp = baseSp / LocalDensity.current.fontScale for fixed-dp boxes"
    - "coerceAtLeast(64.dp) touch-target floor on size modifier for floating overlay buttons"

key-files:
  created:
    - .planning/phases/24-navigation-spine/24-UAT.md
    - .planning/phases/24-navigation-spine/24-05-SUMMARY.md
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt
    - app/src/main/java/works/mees/dinghy/designsystem/components/FloatingEStop.kt

key-decisions:
  - "Font-scale-stable glyph sizing for icon-only OutlinedControl: divide sp by fontScale so the glyph renders at a fixed dp regardless of system Accessibility font scale"
  - "FloatingEStop box floors at 64.dp (coerceAtLeast) — preserves square aspect at all U values"
  - "Styling fix assumed-good without on-device re-eyeball (owner directive); pre-release visual review is the final gate"
  - "GPU gfxinfo 95th/99th percentile outlier (~4950ms) attributed to idle-gap / SurfaceView measurement artifact — not morph jank; Crossfade retained"
  - "Move/Extrude-during-print gating deferred as non-blocking pending todo"

patterns-established:
  - "Font-scale-stable dp-equivalent sizing: 50f / LocalDensity.current.fontScale for icon-only cells in fixed-dp boxes"

requirements-completed: [SC-1, SC-2, SC-3, SC-4, SC-5]

# Metrics
duration: 45min
completed: 2026-06-10
---

# Phase 24 Plan 05: On-Device UAT Summary

**Navigation spine owner-verified on flox (Adreno 320) — all 5 SC gates PASS; UAT-surfaced e-stop glyph overflow fixed in-phase with font-scale-stable sizing + 64dp floor**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-06-10T05:30Z
- **Completed:** 2026-06-10T06:15Z
- **Tasks:** 2 (Task 1 scaffolded + built in prior wave; Task 2 UAT verdicts recorded + fix applied)
- **Files modified:** 4

## Accomplishments

- Full host unit suite GREEN (`testDebugUnitTest` BUILD SUCCESSFUL, 34 tasks)
- Release APK built, debug-signed, reinstalled on flox after styling fix (`adb install -r` → Success)
- SC-1..SC-5 all PASS on flox / Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30
- UAT-surfaced styling defect (e-stop glyph overflow at fontScale > 1) fixed in-phase with font-scale-stable glyph sizing and 64dp coerceAtLeast box floor
- 24-UAT.md records all five SC verdicts, out-of-scope printing-style note, and deferred Move/Extrude-during-print todo

## Task Commits

1. **Task 1: Full host suite + build + 24-UAT scaffold** - prior wave (`ec0caf7` context)
2. **Task 2: UAT verdicts + styling fix** - `e07263c` (fix: e-stop glyph font-scale-stable + 64dp square box)
3. **Plan docs commit** - `[docs commit hash]`

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt` — icon-only branch: `sizeSp = 50f / LocalDensity.current.fontScale` (font-scale-stable dp-equivalent); import `LocalDensity`
- `app/src/main/java/works/mees/dinghy/designsystem/components/FloatingEStop.kt` — `modifier.size((uDp * 0.7f).coerceAtLeast(64.dp))`; import `dp`; updated KDoc
- `.planning/phases/24-navigation-spine/24-UAT.md` — SC-1..SC-5 verdicts, gfxinfo results, styling defect record, out-of-scope note, deferred todo
- `.planning/phases/24-navigation-spine/24-05-SUMMARY.md` — this file

## Decisions Made

- **Font-scale-stable glyph sizing:** `sizeSp / fontScale` converts sp to a fixed-dp equivalent — at fontScale 1.0 rendering is byte-identical; at fontScale > 1 the glyph no longer overflows its border. This pattern applies to icon-only controls sized in fixed-dp boxes; does NOT affect labeled controls (which use `fsSp`) or icon-only controls at normal scale.
- **64dp coerceAtLeast floor on FloatingEStop:** Ensures the box stays a square at or above the touch-target minimum for all tablet-sized U values. Complements the sp→dp stable sizing.
- **Styling fix without on-device re-eyeball:** Owner directive — fix now, assume correct, rely on pre-release visual review at Phase 29. Not a functional regression (e-stop remains fully functional; only glyph sizing corrected).
- **GPU outlier not a morph jank:** The 95th/99th GPU percentile anomalies (~4950ms) are an idle-gap / SurfaceView measurement artifact on a sparse-frame screen. UI-thread path confirms smooth morph. Crossfade retained.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] E-stop glyph font-scale-stable sizing**
- **Found during:** Task 2 (on-device UAT — SC-3)
- **Issue:** `OutlinedControl`'s icon-only branch sized glyph at a fixed 50sp inside a fixed-dp box. At system fontScale > 1.0 (large Accessibility font setting) the 50sp value exceeds the box size, overflowing the 2px border. Confirmed on flox (Nexus 7 tablet at effective large font scale) during the e-stop UAT.
- **Fix:** `sizeSp = 50f / LocalDensity.current.fontScale` — renders at a constant ~50dp equivalent regardless of system font scale. At fontScale 1.0 identical to prior behavior. Added `LocalDensity` import.
- **Files modified:** `OutlinedControl.kt`
- **Verification:** Host suite GREEN; owner accepted without re-eyeball; pre-release visual review is the final gate.
- **Committed in:** `e07263c`

**2. [Rule 2 - Missing Critical] FloatingEStop 64dp square box floor**
- **Found during:** Task 2 (same UAT — root cause analysis)
- **Issue:** `FloatingEStop` used `modifier.size(uDp * 0.7f)` with no floor. On a tablet (U ≈ 86dp) this is ~60dp — below the 64dp touch-target minimum — and `heightIn(min=64dp)` in `OutlinedControl` could make the box non-square, compounding the glyph overflow. The `coerceAtLeast` floor was the missing corrective.
- **Fix:** `modifier.size((uDp * 0.7f).coerceAtLeast(64.dp))` — box stays a square at or above 64dp. Added `dp` import. Updated KDoc.
- **Files modified:** `FloatingEStop.kt`
- **Verification:** Host suite GREEN; owner directive assumed-good.
- **Committed in:** `e07263c`

---

**Total deviations:** 2 auto-fixed (both Rule 2 — correctness gap; the icon-only sizing pattern was missing the font-scale-stable dp conversion required for fixed-dp overlay boxes)
**Impact on plan:** Both fixes address the same root cause (UAT-surfaced glyph overflow). No scope creep. No behavioral regression to other icon-only controls at fontScale 1.0.

## Issues Encountered

One styling defect found at SC-3 UAT (e-stop glyph overflow on flox at large font scale). Fixed inline before the docs commit per owner directive. No functional issues encountered — all five SC gates PASS.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Phase 24 (navigation spine) complete and owner-verified on real hardware floor
- All SC-1..SC-5 gates PASS; 24-UAT.md records results honestly including the in-phase fix
- Pending todo recorded: Move/Extrude-during-print gating (deferred, non-blocking)
- Font-scale-stable glyph sizing pattern established for fixed-dp icon-only boxes — applies to future floating overlay buttons
- Next: Phase 25 (Browse) or as ordered by orchestrator

---
*Phase: 24-navigation-spine*
*Completed: 2026-06-10*
