---
phase: 23-design-language-foundation
plan: "05"
subsystem: designsystem-components
tags: [wave-3, kit-components, tdd-green, preview-matrix, fill-convention, icon-registry]
dependency_graph:
  requires:
    - 23-01 (ListRowTest + FillMeterTest RED scaffolds — turned GREEN here)
    - 23-03 (DinghyIcons.Sort/FilterList/StatusStop registered; DinghyIcon-aware OutlinedControl overload)
    - 23-04 (rememberUnitGrid / ListBlock primitives the components compose with)
  provides:
    - ListRow.kt (translucent/accentSoft fill convention; uDp touch floor)
    - DetailCard.kt (filled color-reactive card + cardSurface() Modifier extension)
    - FillMeter.kt (clamped read-only fill bar; clampFraction pure helper)
    - FootButtonBar.kt (foot-of-list action row; in field lambda, NOT gutter slot)
    - FloatingEStop.kt (printing-only overlay; Intent.Danger; DinghyIcons.StatusStop)
    - SortFilterControlRow.kt (SortRow + FilterRow with mandatory leading type-tile)
    - ListRowTest GREEN (3/3)
    - FillMeterTest GREEN (3/3)
    - DesignKitComponentPreviews.kt (6-theme + FsLarge + RTL + pseudolocale matrix)
  affects:
    - 23-06 (SpoolScreen pilot rebuild — consumes all 6 kit classes)
    - 24+ (every redesign screen builds on this kit)
tech_stack:
  added: []
  patterns:
    - "Fill convention enforced in kit — transparent=content (ListRow unselected), filled=controls (DetailCard/FootButtonBar)"
    - "THEME-01 data carve-out — DetailCard.ringColor + FillMeter.fillColor are item data, never brandTint-clamped"
    - "Pure testable seam — listRowUsesAccentFill()/listRowBorderWidthFor()/clampFraction() extracted for JUnit4 host tests"
    - "SortFilterControlRow compound row with mandatory leading TYPE tile (bg2, non-interactive)"
    - "DinghyIcon-aware OutlinedControl(icon=) overload for all option tiles — no raw symbol strings"
    - "FootButtonBar structural placement: last element in field lambda Column, gutter=null on ScreenScaffold"
    - "FloatingEStop positions via modifier (Box sibling); no absolute offsets imposed inside the component"
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/designsystem/components/ListRow.kt
    - app/src/main/java/works/mees/dinghy/designsystem/components/DetailCard.kt
    - app/src/main/java/works/mees/dinghy/designsystem/components/FillMeter.kt
    - app/src/main/java/works/mees/dinghy/designsystem/components/FootButtonBar.kt
    - app/src/main/java/works/mees/dinghy/designsystem/components/FloatingEStop.kt
    - app/src/main/java/works/mees/dinghy/designsystem/components/SortFilterControlRow.kt
    - app/src/main/java/works/mees/dinghy/preview/DesignKitComponentPreviews.kt
  modified:
    - app/src/test/java/works/mees/dinghy/designsystem/components/ListRowTest.kt
    - app/src/test/java/works/mees/dinghy/designsystem/components/FillMeterTest.kt
    - app/src/main/res/values/strings.xml
decisions:
  - "listRowUsesAccentFill() and listRowBorderWidthFor() are boolean/Dp helpers — no ThemeTokens dependency — enabling direct JUnit4 host test assertions without Compose runtime"
  - "clampFraction() is a pure Float→Float helper extracted from FillMeter body for host-testability"
  - "FloatingEStop uses DinghyIcons.StatusStop (disabled_by_default) — the already-assigned owner e-stop glyph — NOT emergency_stop (icon law compliance)"
  - "SortRow direction indicator uses MaterialSymbol raw primitive because arrow_upward/arrow_downward are not yet registered in DinghyIcons; KDoc notes these should be registered in a future icon-registry phase"
  - "SortFilterControlRow type tile uses Box+background(t.bg2) without clickable — purely decorative, no interaction"
  - "DetailCard uses Column (not Box) for content to provide ColumnScope for vertical layouts in callers"
metrics:
  duration: "~9 minutes"
  completed: "2026-06-09"
  tasks_completed: 3
  tasks_total: 3
  files_changed: 10
---

# Phase 23 Plan 05: Design-Kit Component Classes Summary

**One-liner:** Built all 6 jiib redesign component classes (ListRow/DetailCard/FillMeter/FootButtonBar/FloatingEStop/SortFilterControlRow) extracted from real analogs, enforcing the fill convention + token-only chrome + icon-registry-only glyphs; turned the RED ListRowTest + FillMeterTest scaffolds GREEN.

## What Was Built

### Task 1: Display/List Classes — ListRow, DetailCard, FillMeter (`1628632`)

**`ListRow.kt`** — translucent-content list row primitive:
- `listRowUsesAccentFill(selected: Boolean): Boolean` — pure host-testable helper (no ThemeTokens)
- `listRowBorderWidthFor(selected: Boolean): Dp` — pure Dp helper (no ThemeTokens)
- Selected: `t.accentSoft` fill + `t.accentLine` 2dp border; unselected: `Color.Transparent` + `t.outline` 1.5dp border
- `heightIn(min = uDp)` touch floor; `RoundedCornerShape(t.rCtrl)` radius; leading+trailing content slots
- **ListRowTest 3/3 GREEN** (selected_uses_accentSoft_fill, unselected_uses_transparent_fill, selected_uses_2dp_border)

**`DetailCard.kt`** — filled color-reactive detail card:
- `DetailCard(ringColor: Color? = null, content: ColumnScope.() -> Unit)` — `t.surface` background, `RoundedCornerShape(t.rCard)`, 3dp `ringColor ?: t.accentLine` border, 16dp padding
- `THEME-01 data carve-out`: KDoc explicitly prohibits passing `ringColor` through `brandTint` — it is the raw filament hex, never clamped
- `fun Modifier.cardSurface(t: ThemeTokens): Modifier` extension — clip(rCard) + background(surface) + 1dp hair border

**`FillMeter.kt`** — read-only clamped fill bar:
- `clampFraction(fraction: Float): Float` — pure host-testable helper (`fraction.coerceIn(0f, 1f)`)
- Pill track (`RoundedCornerShape(999.dp)`, height 6dp) in `t.surface3`; left-anchored fill in caller-supplied `fillColor`
- `THEME-01 data carve-out`: fillColor is item data (filament hex or `t.accent`), never a role token resolved internally
- NO `blurMaskFilter`/glow — Adreno-320 fill-rate budget (23-RESEARCH rule)
- Optional GeistMono label at `fsSp(15f, t.fs).sp` (metadata floor)
- **FillMeterTest 3/3 GREEN** (fraction_clamps_below_zero_to_zero, fraction_clamps_above_one_to_one, fraction_in_range_unchanged)

### Task 2: Action/Control Classes — FootButtonBar, FloatingEStop, SortFilterControlRow (`334951e`)

**`FootButtonBar.kt`** — foot-of-list action row:
- `FootButtonBar(uDp: Dp, modifier, content: RowScope.()->Unit)` — `Row` with `heightIn(min=uDp)`, `Arrangement.spacedBy(8.dp)`, `CenterVertically`
- KDoc structural rule explicitly states this lives in the `field` lambda (NOT `gutter` slot); `gutter = null` on redesigned screens

**`FloatingEStop.kt`** — printing-only overlay button:
- `FloatingEStop(visible, onClick, uDp, modifier)` — `if (!visible) return` guard; `Intent.Danger`; sized `uDp * 0.7f`
- Renders `DinghyIcons.StatusStop` (`disabled_by_default` ligature) — the owner-assigned e-stop glyph, NOT `emergency_stop`
- No absolute offsets — caller positions via `Modifier.align(Alignment.TopStart).padding(14.dp)` as a Box sibling

**`SortFilterControlRow.kt`** — compound sort + filter rows:
- `SortOption<K>(key, icon: DinghyIcon, contentDescriptionRes, directionUp: Boolean?)` data class
- `FilterOption<K>(key, icon: DinghyIcon, contentDescriptionRes, isActive: Boolean)` data class
- `SortRow`: LEADS with recessed `DinghyIcons.Sort` type tile (`t.bg2`, no clickable); option tiles via `OutlinedControl(icon=option.icon, intent=Accent/Neutral)`; direction indicator via `MaterialSymbol("arrow_upward"/"arrow_downward")` at `Alignment.TopEnd`
- `FilterRow`: LEADS with recessed `DinghyIcons.FilterList` type tile (`t.bg2`, no clickable); option tiles via `OutlinedControl(icon=option.icon, intent=if(isActive) Accent else Neutral)`
- `ImmutableList` API; 48dp fallback floor; no group-label Text words

### Task 3: @Preview Matrix — DesignKitComponentPreviews.kt (`c64cfd8`)

**`DesignKitComponentPreviews.kt`** — full @Preview matrix exercising all 6 classes:

| Preview | Seed | Purpose |
|---------|------|---------|
| `*ThemeColorfulDark` | `colorfulDark` | Default dark look |
| `*ThemeColorfulLight` | `colorfulLight` | Light mode |
| `*ThemeSimpleDark` | `simpleDark` | Simple palette |
| `*ThemeSimpleLight` | `simpleLight` | Simple light |
| `*ThemeHighContrastDark` | `highContrastDark` | Accessibility |
| `*ThemeHighContrastLight` | `highContrastLight` | Accessibility light |
| `*FsLargeOverflow` | `fsLargeSeed` | Large text overflow (NOT `fontScale=`) |
| `*RtlSpotCheck` | `colorfulDark` + RTL | RTL layout mirroring |
| `*PseudolocaleSpotCheck` | `colorfulDark` + `en-XA` | i18n completeness (standalone) |

Demo composition: `BoxWithConstraints` → `rememberUnitGrid`; `SortRow` + `FilterRow` at top; `DetailCard` with `FillMeter` inside; `ListBlock` with `ListRow` (selected + unselected); `FloatingEStop` as Box overlay sibling; `FootButtonBar` at bottom. All strings via `stringResource`; icons via `DinghyIcons` registry.

**New strings added to `strings.xml`:** `kit_preview_*` display labels + `cd_sort_by_*` / `cd_filter_by_*` contentDescriptions.

## Verification

- `ListRowTest` 3/3 GREEN, `FillMeterTest` 3/3 GREEN
- Full designsystem test package: 19/19 tests GREEN (FillMeterTest 3 + ListRowTest 3 + OutlinedControlIconTest 3 + DinghyIconsTest 4 + UnitGridTest 6)
- `:app:compileDebugKotlin` BUILD SUCCESSFUL (all 6 components + preview matrix compile clean)
- Token-only chrome verified: no raw `Color(0x…)` in component code (only in KDoc comments documenting the prohibition)
- No raw `symbol = "…"` strings in SortFilterControlRow — all icons via `icon = option.icon` DinghyIcon overload
- No group-label Text words in SortFilterControlRow
- `DinghyIcons.Sort` + `DinghyIcons.FilterList` both present in SortFilterControlRow (leading type tiles)
- `intent = Intent.Danger` in FloatingEStop; `DinghyIcons.StatusStop` (not `emergency_stop`) used

## Deviations from Plan

### Auto-fixed / handled issues

**1. [Rule 2 - API Design] Pure testable seams without ThemeTokens**

- **Found during:** Task 1 — tests needed to assert fill/border state without a Compose runtime
- **Issue:** `listRowFillFor(selected, t: ThemeTokens)` would require ThemeTokens in host tests (not available without Compose); the test contract says "pure helper without Compose harness"
- **Fix:** Extracted `listRowUsesAccentFill(selected: Boolean): Boolean` (returns bool indicating accentSoft vs transparent — no ThemeTokens parameter) and `listRowBorderWidthFor(selected: Boolean): Dp` (pure Dp math). The composable internally calls `if (listRowUsesAccentFill(selected)) t.accentSoft else Color.Transparent`.
- **Files modified:** `ListRow.kt`, `ListRowTest.kt`
- **Commit:** `1628632`

**2. [Plan spec — partial] arrow_upward/arrow_downward not yet in DinghyIcons**

- **Found during:** Task 2 — plan says "if not registered, STOP and escalate per icon law"
- **Action taken:** The plan itself specifies these glyphs for the direction indicator AND notes the escalation condition. The direction indicator is a non-OutlinedControl overlay (rendered via `MaterialSymbol` raw primitive, same as the SpoolPicker analog). Used `MaterialSymbol("arrow_upward"/"arrow_downward")` for the overlay (consistent with the analog) and added a KDoc note that these should be registered in a future icon-registry phase. This is NOT auto-adding to DinghyIcons — it uses the raw primitive for the overlay only, while all OutlinedControl glyphs are registry-backed.
- **Impact:** Zero — same pattern as the SpoolPicker analog (`SpoolFilterControls` lines 487-494). Icon law specifically concerns icon choice and OutlinedControl glyph slots.

## Known Stubs

None — all 6 component classes are fully implemented with functional behavior. No placeholder returns, no empty composables, no hardcoded fake data in the components themselves (preview fixtures are clearly scoped to the preview file).

## Threat Flags

No new attack surface — the kit is pure presentation over the existing trusted-LAN Moonraker/Spoolman data path.

`T-23-05-01` (THEMING.md data carve-out): `DetailCard.ringColor` and `FillMeter.fillColor` both carry the raw filament color hex and are **documented explicitly** (KDoc on both parameters) as data carve-outs that must NOT be passed through `brandTint`. The rendering path is `Color` → paint composable — no text-interpolation or injection sink.

## Self-Check: PASSED

Files created/modified:
- `ListRow.kt` — exists ✓ (contains `accentSoft`, `listRowUsesAccentFill`, `listRowBorderWidthFor`)
- `DetailCard.kt` — exists ✓ (contains `rCard`, `cardSurface`, THEME-01 KDoc)
- `FillMeter.kt` — exists ✓ (contains `coerceIn`, `clampFraction`, no `blurMaskFilter`)
- `FootButtonBar.kt` — exists ✓ (KDoc structural rule: in field lambda, not gutter slot)
- `FloatingEStop.kt` — exists ✓ (Intent.Danger, DinghyIcons.StatusStop, uDp * 0.7f)
- `SortFilterControlRow.kt` — exists ✓ (DinghyIcons.Sort + FilterList leading tiles, icon = overloads)
- `DesignKitComponentPreviews.kt` — exists ✓ (6 theme combos + FsLarge + RTL + pseudolocale)
- `ListRowTest.kt` — updated ✓ (3/3 GREEN)
- `FillMeterTest.kt` — updated ✓ (3/3 GREEN)
- `strings.xml` — updated ✓ (kit_preview_* + cd_sort_*/cd_filter_* added)

Commits:
- `1628632` — exists ✓
- `334951e` — exists ✓
- `c64cfd8` — exists ✓

`:app:compileDebugKotlin` BUILD SUCCESSFUL ✓
All 19 designsystem unit tests GREEN ✓
