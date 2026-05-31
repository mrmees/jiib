---
phase: 03-design-system-theming-foundation
plan: 03
subsystem: design-system / theming
tags: [compose, theming, tokens, layout, controls, ui-01, ui-02, theme-01, theme-02]
requires:
  - "03-01: ThemeResolver (StateFlow<ThemeTokens>), ThemeTokens (resolved sRGB Color + dp + fs)"
  - "03-02: theme/Geist.kt (Geist FontFamily)"
provides:
  - "LocalTokens — staticCompositionLocalOf<ThemeTokens> Compose token boundary (THEME-01/D-05)"
  - "DinghyTheme — theme boundary: collects resolver flow, pins fontScale=1f, provides LocalTokens (D-04/THEME-02)"
  - "ScreenScaffold — Focus/Field/Gutter responsive layout primitive (UI-01)"
  - "OutlinedControl — outline-led intent-colored ≥64dp control (UI-02)"
affects:
  - "every later Compose panel composes inside DinghyTheme and reads LocalTokens.current"
tech-stack:
  added: []
  patterns:
    - "staticCompositionLocalOf token bridge (RESEARCH Pattern 3 / Pitfall 3)"
    - "LocalDensity fontScale=1f override at the one theme boundary (RESEARCH Pattern 5 / D-04)"
    - "slot-based ScreenScaffold via BoxWithConstraints + weight/fillMax (RESEARCH Pattern 1)"
    - "token-driven intent→color control (hifi.css .ctl language)"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/theme/compose/LocalTokens.kt
    - app/src/main/java/works/mees/dinghy/theme/compose/DinghyTheme.kt
    - app/src/main/java/works/mees/dinghy/designsystem/layout/ScreenScaffold.kt
    - app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt
  modified: []
decisions:
  - "Intent→token map: Neutral→outline, Accent→accentLine, Warn→heat, Danger→stop, Go→go (matches hifi.css .ctl/.accent/.warn/.danger/.go)"
  - "ScreenScaffold kept slot-based (no custom Layout); weighted-gutter grid-line alignment deferred to on-device gallery (A4 fallback = custom Layout for gutter row only)"
  - "fontScale=1f override lives at exactly one boundary (DinghyTheme); no downstream density path may re-introduce fontScale"
metrics:
  duration_min: 6
  completed: 2026-05-31
  tasks: 2
  files: 4
---

# Phase 3 Plan 03: Compose Token Boundary + Layout/Control Primitives Summary

Established the Compose half of the theming seam plus the two most-reused visual primitives: the
`LocalTokens` token boundary, the `DinghyTheme` boundary (collects the resolver flow and pins
`fontScale=1f` so `--fs` is the sole text authority), the `ScreenScaffold` Focus/Field/Gutter
responsive layout primitive, and the token-driven `OutlinedControl` intent-colored control language.

## What Was Built

### Task 1 — LocalTokens + DinghyTheme (commit `714f806`)
- `theme/compose/LocalTokens.kt` — `val LocalTokens = staticCompositionLocalOf<ThemeTokens> { error(…) }`.
  `static` (not dynamic) per Pitfall 3: a theme swap touches nearly every node, so subtree
  recomposition is correct and reads are untracked/cheaper. Throwing default catches reads outside a
  `DinghyTheme`.
- `theme/compose/DinghyTheme.kt` — `@Composable fun DinghyTheme(resolver: ThemeResolver, content)`:
  collects `resolver.tokens` via `collectAsStateWithLifecycle()`, then
  `CompositionLocalProvider(LocalTokens provides tokens, LocalDensity provides Density(density =
  current.density, fontScale = 1f))`. The `fontScale=1f` override (D-04) neutralizes the OS
  accessibility font-scale at this ONE boundary so the in-app `--fs` (via `fsSp`) is the only
  text-size multiplier and can't double-apply. Documented as the intentional appliance-screen
  text-authority trade.

### Task 2 — ScreenScaffold (UI-01) + OutlinedControl (UI-02) (commit `b6a07f0`)
- `designsystem/layout/ScreenScaffold.kt` — slot composable
  `ScreenScaffold(modifier, focus?, field?, gutter?, focusGrow=1f, fieldGrow=1f)`. Inside
  `BoxWithConstraints`, branches on `maxWidth > maxHeight`: LANDSCAPE = a weighted Row stage
  (`weight(focusGrow)` | `weight(fieldGrow)`, 50/50 default) above a full-width gutter Box on the
  same grid; PORTRAIT = a Column stacking focus(weight)/field(weight)/full-width gutter (~40/40/20
  tunable). Either focus or field may be null (the other takes the freed space); gutter may be null.
  Regions use `weight`/`fillMax` ONLY — no hardcoded px (NON-NEGOTIABLE 3). KDoc documents that
  sacred-square content (ring, jog pad) is wrapped by the CALLER in `Modifier.aspectRatio(1f)` INSIDE
  its region — the region is never made square (NON-NEGOTIABLE 2).
- `designsystem/control/OutlinedControl.kt` — `enum class Intent { Neutral, Accent, Warn, Danger, Go }`
  mapping to `t.outline / t.accentLine / t.heat / t.stop / t.go` (matching hifi.css
  `.ctl/.accent/.warn/.danger/.go`); `@Composable fun OutlinedControl(label, onClick, modifier,
  intent = Neutral)` with `heightIn(min = 64.dp)`, `clip(RoundedCornerShape(t.rCtrl))`, a `2.dp`
  border in the intent color, transparent fill, and a Geist label at `fsSp(18f, t.fs).sp` in
  `t.text`. Glow is a STATIC layer (D-13) — no looping/infinite Compose animation.

## Verification

- `:app:assembleDebug` compiles after both tasks (EXIT=0 twice).
- **Token-purity gate (success criterion #1):** `grep -rEn 'Color\(0[xX]'` over `designsystem/`
  returns ZERO matches — all colors flow from `LocalTokens.current`.
- **Animation gate (D-13):** `grep -rEn 'rememberInfiniteTransition|animate.*AsState'` over
  `designsystem/` returns ZERO matches.

## Deferred to On-Device Gallery (Manual-Only, 03-VALIDATION.md)

These are Manual-Only acceptance criteria, NOT gaps:
- Portrait/landscape grid-line alignment + sacred-square rendering + ≥64px finger-test vs
  `docs/ui_design/images/*.png` on flox.
- Proof that OS `fontScale` does NOT double-apply (device fontScale at max in the gallery).
- Weighted-gutter middle-button-center-on-divide alignment (A4 risk; fallback = custom `Layout` for
  the gutter row only if drift is observed).

## Deviations from Plan

None — plan executed exactly as written. Intent→token mapping confirmed against `hifi.css` (`.ctl`
= `--outline`, `.accent` = `--accent-line`, `.warn` = `--heat`, `.danger` = `--stop`, `.go` =
`--go`). One trivial in-flight reword: a KDoc line in OutlinedControl originally spelled out
`rememberInfiniteTransition`/`animate*AsState` while explaining their deliberate ABSENCE — reworded
to "looping/infinite Compose transition" so the D-13 animation grep gate is unambiguously zero.

## Known Stubs

None. All four files are complete primitives wired to live tokens; no placeholder data, no empty
sources. Consumers (panels) arrive in later plans/phases by design.

## Threat Flags

None. Pure in-process Compose UI reading local theme tokens — no sockets, persistence, or untrusted
input. Matches the plan's T-03-03 (accept).

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/theme/compose/LocalTokens.kt
- FOUND: app/src/main/java/works/mees/dinghy/theme/compose/DinghyTheme.kt
- FOUND: app/src/main/java/works/mees/dinghy/designsystem/layout/ScreenScaffold.kt
- FOUND: app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt
- FOUND commit: 714f806 (Task 1)
- FOUND commit: b6a07f0 (Task 2)
