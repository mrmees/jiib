---
phase: 03-design-system-theming-foundation
plan: 04
subsystem: design-system
tags: [compose, primitives, confirm-guard, scrubber, toast, theming, views-seam]
requires:
  - "03-03: ScreenScaffold (Focus/Field/Gutter), OutlinedControl (Intent enum), LocalTokens/DinghyTheme"
  - "03-02: GeistMono FontFamily"
  - "03-01: ThemeTokens (resolved sRGB role tokens)"
provides:
  - "ConfirmGuard — full-screen Confirm safety gate (PRIM-03)"
  - "ScrubberPage — keyboard-free single-setting scrubber/stepper page (PRIM-01)"
  - "SeverityToast — Info/Success/Warning/Error toast, color + icon + text (PRIM-04)"
  - "ThemeableView — Views-side push-tokens interface (D-06)"
affects:
  - "Phase 4 panels (Stop/cancel/disable/restart route through ConfirmGuard; every numeric setpoint through ScrubberPage)"
  - "03-05 render GraphView implements ThemeableView"
tech-stack:
  added: []
  patterns:
    - "Primitives compose Wave-2 ScreenScaffold + OutlinedControl + LocalTokens — assemble, not redesign"
    - "Keyboard-free numeric entry: drag fill-bar + ±step steppers, no TextField/KeyboardType (PRIM-01)"
    - "Severity = color + icon + text, never color alone (PRIM-04 accessibility floor)"
    - "Fixed intent contract: Cancel/dismiss Neutral by default; Danger opt-in via flag, never per-panel guesswork"
key-files:
  created:
    - "app/src/main/java/works/mees/dinghy/designsystem/ConfirmGuard.kt"
    - "app/src/main/java/works/mees/dinghy/designsystem/SeverityToast.kt"
    - "app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt"
    - "app/src/main/java/works/mees/dinghy/theme/views/ThemeableView.kt"
  modified: []
decisions:
  - "Severity icons are text glyphs (i / ✓ / ! / ×) not an icon-font dependency — keeps each primitive a leaf, and satisfies 'never the same glyph twice' (CLAUDE.md)"
  - "ScrubberPage owns a LOCAL working value (caller commits on Apply) so the page is stateless re: commit and reusable for any setpoint"
metrics:
  duration_min: 3
  tasks: 2
  files: 4
  completed: 2026-05-31
---

# Phase 3 Plan 4: Design-System Primitives (Confirm / Scrubber / Toast) + Views Seam Summary

The three reusable, panel-consumable UI primitives — **PRIM-03** full-screen Confirm guard,
**PRIM-01** keyboard-free single-setting scrubber/stepper page, **PRIM-04** severity toast — plus
the one-method **ThemeableView** interface (D-06) that defines the Views-side push-tokens contract
the render graph will implement. All three primitives are assembled from the Wave-2
ScreenScaffold + OutlinedControl + LocalTokens boundary, so they inherit the Focus/Field/Gutter
layout grammar, the outline-led control language, and full token theming for free.

## What was built

- **ConfirmGuard.kt** — `@Composable fun ConfirmGuard(title, message, confirmLabel, onConfirm,
  onCancel, modifier, destructive = true)`. Full-screen, **gutter omitted** (LAYOUT.md: the Field's
  CONFIRM/CANCEL buttons ARE the actions). Confirm = `Intent.Danger` (red) when destructive else
  `Intent.Go` (green); Cancel = `Intent.Neutral` (a safe dismiss is never red). Full-bleed
  `--stop-soft` / `--go-soft` background tint signals the decision's gravity. Dispatches nothing
  itself (threat T-03-04) — callbacks only; the destructive command (PRIM-05) is Phase 4.
- **SeverityToast.kt** — `enum class Severity { Info, Success, Warning, Error }` mapping to
  `accent / go / heat / stop`, and `@Composable fun SeverityToast(severity, text, modifier)`
  rendering a token-tinted pill with a **per-severity icon AND the text** — never color alone
  (PRIM-04). Each severity uses a distinct mark (no glyph repeats).
- **ScrubberPage.kt** — `@Composable fun ScrubberPage(label, value, range, step, unit, onValueChange,
  onCancel, onApply, modifier, destructiveDismiss = false)`. Built on ScreenScaffold: Field is a
  full-height fill-bar scrubber (drag/tap to set within `range`, live value in **GeistMono**); the
  gutter holds `±step` steppers + Cancel/Apply. **No `TextField` / no OS keyboard** (PRIM-01).
  **Fixed cancel contract:** Apply = `Intent.Go`, Cancel = `Intent.Neutral` by default;
  `Intent.Danger` is reachable **only** via the opt-in `destructiveDismiss` flag (destructive-revert),
  so panels can't reinterpret the dismiss intent per-screen.
- **ThemeableView.kt** — `interface ThemeableView { fun applyTokens(t: ThemeTokens) }`. The
  classic-Views mirror of LocalTokens: the AndroidView host pushes resolved tokens and the View
  repaints via `invalidate()` with no recreation. Formalizes the existing `TempGraphView`
  setter idiom; the render GraphView (03-05) implements it.

## Verification

- `:app:assembleDebug` compiles (EXIT=0) with all four files.
- **Token-purity gate (success criterion #1):** `grep -rEn 'Color\(0[xX]'` over ConfirmGuard.kt,
  SeverityToast.kt, ScrubberPage.kt → **ZERO** raw color literals. All colors via `LocalTokens.current`.
- **Keyboard-free gate (PRIM-01):** no `TextField`/`BasicTextField`/`KeyboardType` in ScrubberPage
  (the sole grep hit is a KDoc line documenting their absence).
- **No looping animation (D-13):** `grep rememberInfiniteTransition` over `designsystem/` → ZERO.
- **Manual-only (03-VALIDATION.md):** interaction/appearance correctness (drag feel, no keyboard
  popping, guard layout, toast legibility) is deferred to the on-device gallery on flox.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Wrong `weight` import in ConfirmGuard.kt**
- **Found during:** Task 1 build.
- **Issue:** Imported `androidx.compose.foundation.layout.weight` which resolves to the *internal*
  `RowColumnParentData.weight` and fails to compile (`it is internal in file`).
- **Fix:** Removed the explicit import — `Modifier.weight(1f)` is used inside `Row {}` where the
  `RowScope.weight` extension is already in scope. Rebuilt clean.
- **Files modified:** ConfirmGuard.kt
- **Commit:** 2c20043

## Known Stubs

None. All four files are complete, compile, and are panel-consumable. (The render GraphView that
*implements* ThemeableView is intentionally out of scope — it lands in plan 03-05.)

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/designsystem/ConfirmGuard.kt
- FOUND: app/src/main/java/works/mees/dinghy/designsystem/SeverityToast.kt
- FOUND: app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt
- FOUND: app/src/main/java/works/mees/dinghy/theme/views/ThemeableView.kt
- FOUND commit: 2c20043 (Task 1)
- FOUND commit: 480714c (Task 2)
