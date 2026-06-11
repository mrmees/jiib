---
task: 260611-cj1
slug: lock-edge-to-edge-system-bars-to-active-theme
type: quick
status: complete
completed: 2026-06-11
duration: ~20 min
commits:
  - 350d096  # test(quick-cj1): RED — 4 failing isDarkBackdrop host tests + PLAN.md
  - 08ede99  # feat(quick-cj1): GREEN — gate + SyncSystemBarsToTheme + MainActivity wiring
  - 209fa0f  # feat(quick-cj1): SyncDialogWindowToTheme + AppDrawer Dialog wiring
  - 2545191  # docs(quick-cj1): Moto UAT bookkeeping + R7 Phase-29 deferral
files:
  created:
    - app/src/main/java/works/mees/dinghy/theme/compose/SystemBars.kt
    - app/src/test/java/works/mees/dinghy/theme/compose/SystemBarsTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/MainActivity.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - .planning/phases/26.5-overnight-hardening-slate-audit-r-packages/OVERNIGHT-REPORT.md
    - docs/top-down-audit-roadmap.md
---

# Quick 260611-cj1: Lock Edge-to-Edge System Bars to Active Theme — Summary

**One-liner:** System-bar icon contrast + pre-API-35 bar colors now derive from the ACTIVE
ThemeTokens bg via a luminance gate (`bg.luminance() < 0.5f`), reactively re-applied on every
theme change at both the activity window and the App Drawer's Dialog window — fixing the white
nav bar the dark theme wore on the system-light Moto G Play 2024 (API 34) and the bar restyle
when the drawer opened.

## What changed

### Task 1 — activity window (TDD: 350d096 RED → 08ede99 GREEN)
- **`theme/compose/SystemBars.kt`** (new):
  - `isDarkBackdrop(bg) = bg.luminance() < 0.5f` — the SOLE bar dark/light authority.
    **Luminance-gate decision:** ThemeTokens carries no dark/light flag (the resolver's `dark`
    is private; MainActivity consumes the override-aware `effectiveTokens` flow), and icon
    contrast is a function of the ACTUAL backdrop anyway — a custom theme declared "dark" with
    a light bg still needs dark icons. Pure host-testable math (BrandTint precedent).
  - `SyncSystemBarsToTheme()`: `LaunchedEffect(dark, scrim)` re-calls
    `activity.enableEdgeToEdge(...)` with `SystemBarStyle.dark(scrim)` /
    `SystemBarStyle.light(scrim, scrim)` (scrim = `t.bg.toArgb()` for both args — never a
    system-picked contrast color). Pre-35 the scrims paint the bars; 35+ ignores scrims and
    keeps only icon contrast — same style selection correct on both generations. No-ops in
    previews (`isInEditMode`) and non-activity hosts (ContextWrapper unwrap → null).
- **`MainActivity.kt`**: `SyncSystemBarsToTheme()` composed as the FIRST child inside the
  `DinghyTheme` boundary.
- **`SystemBarsTest.kt`**: 4 host tests pin the gate (TokensDark.bg dark, TokensLight.bg light,
  Black/White extremes, `#767676` boundary doc — perceptual mid-grey linearizes to ~0.18 →
  deterministically dark).

### The two `enableEdgeToEdge` call sites (plan invariant — exactly two)
1. `MainActivity.kt:88` — the no-arg pre-first-frame baseline (first statement of onCreate,
   unchanged).
2. `SystemBars.kt:88` — the explicit-style reactive re-call inside `SyncSystemBarsToTheme()`.

### Task 2 — App Drawer Dialog window (209fa0f)
- `SyncDialogWindowToTheme()` added to SystemBars.kt: resolves the dialog window via
  `(LocalView.current.parent as? DialogWindowProvider)?.window` (silent null = non-dialog host);
  `SideEffect` sets `isAppearanceLightStatusBars/NavigationBars = !dark` and — gated
  `SDK_INT < 35` under `@Suppress("DEPRECATION")` — the window bar colors to `t.bg.toArgb()`
  (35+ forces transparent bars, so the suppression is the intentional pre-35 path).
- `AppDrawer.kt`: called first inside the Dialog content lambda. The drawer is the app's ONLY
  window-creating popup (plan audit); PromptDialog/BedMesh overlays untouched (no window).

### Task 3 — bookkeeping (2545191)
- **OVERNIGHT-REPORT.md** "S25 Ultra group": dated note that the 2026-06-11 sitting ran on a
  Moto G Play 2024 (API 34, arm64). R1 arm64 install PASS · R1 edge-to-edge **PARTIAL** (white
  nav bar + popup restyle → fixed by this task) · R1 predictive back PASS · R1
  POST_NOTIFICATIONS PASS · R2 display-settings rows PASS (line added — the original group had
  no R2 item) · all three R7 TLS items **DEFERRED to Phase 29 polish per owner**. flox group +
  CI wrap-up untouched.
- **docs/top-down-audit-roadmap.md** "Status 2026-06-11": R1 entry now carries the Moto UAT
  parenthetical (flox regression still pending); R7's "live-wss UAT pending" → "DEFERRED to
  Phase 29 polish per owner (2026-06-11)".

## Verification
- RED gate: 4/4 tests failed (`NotImplementedError`) before implementation.
- GREEN gate: `:app:testDebugUnitTest --tests ...SystemBarsTest` exit 0.
- Task 2 grep gate: `SyncDialogWindowToTheme()` wired exactly once in AppDrawer +
  `DialogWindowProvider` present in SystemBars.kt → WIRED.
- Full gate: `:app:testDebugUnitTest :app:assembleDebug` **exit 0**.
- `enableEdgeToEdge` call-site count: exactly 2 (baseline + reactive re-call).
- No raw `Color(0x...)` literals in SystemBars.kt / MainActivity.kt / AppDrawer.kt production
  paths (test fixtures exempt).

## Deviations from Plan

None - plan executed exactly as written. (The RED scaffold used a compiling `TODO()` stub per the
repo's wave-0 lesson so the test sourceset never bricked — within the plan's TDD flow.)

## Owner follow-up (NOT a gate for this plan)
Post-merge eyeball on the Moto (API 34, system light): dark theme → dark nav bar with light
icons; App Drawer open/close → no bar restyle; theme cycling restyles bars live. Re-check flox
(API 30) for no regression. Per the stale-APK trap: force-rebuild and verify APK mtime before
installing.

## Self-Check: PASSED

- Created files exist (SystemBars.kt, SystemBarsTest.kt)
- All 4 task commits present (350d096, 08ede99, 209fa0f, 2545191)
- Working tree clean apart from this SUMMARY (committed next)
