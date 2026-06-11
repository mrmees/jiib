---
phase: 23-design-language-foundation
plan: "04"
subsystem: designsystem-layout
tags: [wave-2, unit-grid, list-block, preview-matrix, fit-preserving, tdd-green]
dependency_graph:
  requires:
    - 23-01 (UnitGridTest RED scaffold — turned GREEN here)
    - 23-02 (LAYOUT.md FIT-PRESERVING formula spec — implemented exactly here)
  provides:
    - UnitGrid.kt (data class + pure unitGridFor() + rememberUnitGrid())
    - ListBlock.kt (edge-faded LazyColumn wrapper)
    - UnitGridTest GREEN (6 tests, 0 failures)
    - DesignKitLayoutPreviews.kt (6-theme + FsLarge + RTL + pseudolocale + SmallPhoneFloor matrix)
  affects:
    - 23-05 (kit components — ListRow/DetailCard/etc. receive grid.uDp explicitly)
    - 23-06 (SpoolScreen pilot — ListBlock + rememberUnitGrid are the structural primitives)
    - 24+ (every redesign screen uses rememberUnitGrid and ListBlock)
tech_stack:
  added: []
  patterns:
    - "FIT-PRESERVING unit U formula — min(nTarget, nMaxFit) × U == contentMinDim (no overflow)"
    - "Pure non-Composable helper unitGridFor() — host-testable Dp arithmetic"
    - "remember(contentMinDim) key in rememberUnitGrid — Adreno-320 perf gate"
    - "ListBlock edge fades via Box-gradient overlays — no pointer consumption (touch passes through)"
    - "derivedStateOf for scroll-edge signals — minimises recomposition on scroll"
    - "Preview matrix: 6-theme + FsLargeOverflow via fsLargeSeed (not fontScale=) + RTL + pseudolocale"
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/designsystem/layout/UnitGrid.kt
    - app/src/main/java/works/mees/dinghy/designsystem/layout/ListBlock.kt
    - app/src/main/java/works/mees/dinghy/preview/DesignKitLayoutPreviews.kt
  modified:
    - app/src/test/java/works/mees/dinghy/designsystem/layout/UnitGridTest.kt
    - app/src/main/res/values/strings.xml
decisions:
  - "unitGridFor() is pure Kotlin — no Compose dependency — so UnitGridTest is a host test with no JVM shim needed; Dp arithmetic (.value comparisons, uDp.value * count) works in JVM scope"
  - "Dp comparison in host tests done via .value (Float) comparisons not Dp operators — Dp comparison operator requires compose-ui-android; .value is always available"
  - "ListBlock fade Boxes have no pointer-input modifiers — plain Box(Modifier.background(brush)) is transparent to gestures; this is the correct non-interception pattern"
  - "derivedStateOf used for showTopFade / showBottomFade — avoids recomposing ListBlock content on every scroll frame"
  - "Preview demo uses BoxWithConstraints with minOf(maxWidth, maxHeight) per LAYOUT.md call-site contract"
metrics:
  duration: "~40 minutes"
  completed: "2026-06-09"
  tasks_completed: 3
  tasks_total: 3
  files_modified: 5
---

# Phase 23 Plan 04: UnitGrid + ListBlock Layout Primitives Summary

**One-liner:** Implemented the FIT-PRESERVING unit-grid formula (unitGridFor/rememberUnitGrid) and the edge-faded ListBlock wrapper, turned the UnitGridTest RED scaffold GREEN, and delivered the full @Preview matrix.

## What Was Built

### Task 1: UnitGrid.kt + UnitGridTest GREEN

**`designsystem/layout/UnitGrid.kt`** — three exports:

| Export | Type | Purpose |
|--------|------|---------|
| `data class UnitGrid(uDp, count)` | data class | Grid descriptor: one U in dp + N count |
| `fun unitGridFor(contentMinDim: Dp): UnitGrid` | pure helper | Host-testable FIT-PRESERVING formula |
| `@Composable fun rememberUnitGrid(contentMinDim: Dp): UnitGrid` | composable | Compose wrapper with `remember(contentMinDim)` key |

**The FIT-PRESERVING formula** (from LAYOUT.md §"The unit U"):
```
nTarget = round(contentMinDim / 41.dp).coerceIn(5, 7)   // anchor → ideal N
nMaxFit = (contentMinDim / 64.dp).toInt().coerceAtLeast(5)  // 64dp floor → max N
N       = min(nTarget, nMaxFit)                          // fit-preserving pick
uDp     = contentMinDim / N                             // U fills exactly
```

Guarantees: `N × uDp == contentMinDim` (no overflow); `uDp >= 64.dp` for `contentMinDim >= 320.dp`; `N in [5, 7]`.

**UnitGridTest** — the 23-01 RED scaffold turned GREEN (6/6 tests pass):

| Test | Input | Expected | Result |
|------|-------|----------|--------|
| `n_clamps_to_5_floor` | 320dp | count=5 (nMaxFit wins over nTarget=7) | PASS |
| `n_clamps_to_7_ceiling` | 600dp | count=7 (nTarget ceiling) | PASS |
| `udp_never_below_64dp` | 320dp, 360dp | uDp >= 64dp both | PASS |
| `udp_equals_dim_over_n` | 360dp | count × uDp == 360dp | PASS |
| `no_overflow_at_320dp` | 320dp | product ≤ 320dp AND uDp ≥ 64dp AND count==5 AND uDp==64dp | PASS |
| `no_overflow_at_360dp` | 360dp | product ≤ 360dp AND uDp ≥ 64dp AND count==5 | PASS |

**Implementation note:** Dp comparison in host tests uses `.value` (Float) comparisons instead of Dp operators — this avoids a Compose runtime dependency in pure JUnit tests. The fix was discovered during the RED→GREEN step (Rule 1 auto-fix: the initial code used `grid.count * grid.uDp` which is `Int * Dp`, unsupported without operator extension; corrected to `grid.uDp.value * grid.count`).

### Task 2: ListBlock.kt

**`designsystem/layout/ListBlock.kt`** — the keyed, edge-faded, scrollbar-less `LazyColumn` wrapper:

```kotlin
@Composable
fun ListBlock(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit)
```

Key implementation choices:
- **`rememberLazyListState`** held internally — callers supply only the `content` lambda
- **`Arrangement.spacedBy(8.dp)`** built in — consistent 8dp inter-row gap per design system
- **Edge fades** as `Box` gradient overlays driven by `derivedStateOf` scroll-edge signals:
  - Top: `firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0`
  - Bottom: `canScrollForward`
- **No pointer-input modifiers on fade Boxes** — plain `Box(Modifier.background(brush))` is gesture-transparent; touch passes through to the `LazyColumn` beneath
- **No scrollbar** — edge fades are the scroll hint per COMPONENTS.md
- **Token-routed fade color** — `t.bg` with alpha gradient; no raw `Color(0x...)` literals

### Task 3: DesignKitLayoutPreviews.kt

**`preview/DesignKitLayoutPreviews.kt`** — full @Preview matrix:

| Preview | Seed | Purpose |
|---------|------|---------|
| `*ThemeColorfulDark` | `colorfulDark` | Default look |
| `*ThemeColorfulLight` | `colorfulLight` | Light mode |
| `*ThemeSimpleDark` | `simpleDark` | Simple palette |
| `*ThemeSimpleLight` | `simpleLight` | Simple light |
| `*ThemeHighContrastDark` | `highContrastDark` | Accessibility |
| `*ThemeHighContrastLight` | `highContrastLight` | Accessibility light |
| `*FsLargeOverflow` | `fsLargeSeed` | Large text overflow check (NOT fontScale=) |
| `*RtlSpotCheck` | `colorfulDark` + RTL | RTL layout mirroring |
| `*PseudolocaleSpotCheck` | `colorfulDark` + `en-XA` | i18n completeness (standalone) |
| `*SmallPhoneFloor` | `colorfulDark` | 360×640 — U fits no-overflow visual proof |
| `*SmallPhoneFloor320` | `colorfulDark` | 320×568 — narrowest phone floor |

All previews: strings via `stringResource`, text via `fsSp(baseSp, t.fs).sp`, color via `LocalTokens.current`.

**New strings added to `strings.xml`:** `cd_unit_grid_demo`, `layout_preview_row_1..5`, `layout_preview_unit_info`.

## Verification

- `:app:testDebugUnitTest --tests ...UnitGridTest` — BUILD SUCCESSFUL, 6/6 tests GREEN
- `:app:compileDebugKotlin` — BUILD SUCCESSFUL (ListBlock + DesignKitLayoutPreviews compile clean)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Dp arithmetic operator ordering in host test**

- **Found during:** Task 1 — initial test code used `grid.count * grid.uDp` (Int × Dp, not defined)
- **Issue:** Kotlin `Dp` value class has `Dp.times(Int)` but NOT `Int.times(Dp)`. Compile error at the `grid.count * grid.uDp` expression and `product <= 320.dp` Dp comparison operator.
- **Fix:** Changed to `grid.uDp.value * grid.count` (Float × Int, always available) and `productValue <= 320f` (Float comparison). Semantically identical — Dp is just a Float wrapper.
- **Files modified:** `UnitGridTest.kt`
- **Commit:** `3ccd602`

None of the plan's semantic requirements changed — the formula, test assertions, and correctness contracts are identical.

## Known Stubs

None — both primitives are fully implemented with no placeholder behavior. The preview rows use sample data via `stringResource` which is non-empty text.

## Threat Flags

No new attack surface — layout primitives over existing trusted StateFlow data. `unitGridFor` clamps N and floors uDp (safe math over constant dp values, not user input). T-23-04-01: accepted.

## Self-Check: PASSED

- `UnitGrid.kt` exists and exports all three symbols: ✓
  - `data class UnitGrid` ✓
  - `fun unitGridFor` ✓
  - `fun rememberUnitGrid` ✓
- `ListBlock.kt` exists with required APIs: ✓
  - `rememberLazyListState` ✓
  - `canScrollForward` ✓
  - `firstVisibleItem*` ✓
  - No pointer-consuming modifiers on fade boxes ✓
- `UnitGridTest.kt` turns GREEN (6/6 tests pass) ✓
- `DesignKitLayoutPreviews.kt` exists with both primitives used ✓
- `strings.xml` has new layout preview strings ✓
- Commits exist: `3ccd602`, `ad560d3`, `75e0a05` ✓
- `:app:compileDebugKotlin` BUILD SUCCESSFUL ✓
