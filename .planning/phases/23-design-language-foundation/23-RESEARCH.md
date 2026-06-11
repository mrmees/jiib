# Phase 23: Design-Language Foundation — Research

**Researched:** 2026-06-09
**Domain:** Compose design-system kit, unit-grid derivation, component-class architecture, SpoolScreen pilot rebuild
**Confidence:** HIGH — every finding verified directly against locked design notes, existing codebase source, and authoritative skill references.

---

## Summary

Phase 23 builds the reusable component-class kit that every later redesign screen consumes, proves it against a real screen (SpoolScreen pilot rebuild), and updates the design-law documents to match. The design is **entirely pre-decided** across sketches 001–004 and the locked notes in `.planning/notes/` and `.claude/skills/sketch-findings-dinghy-display/`. This research translates those decisions into Compose/Kotlin implementation guidance.

The core kit is five primitive composables — `ListRow`, `DetailCard`, `FillMeter`, `FootButtonBar`, `FloatingEStop` — plus two reusable interaction patterns (`SortFilterControlRow` + the Field-takeover picker), the `rememberUnitGrid()` DPI-derived unit-grid primitive, and a `Modifier.cardSurface()` extension. All build on the existing `OutlinedControl` / `ScreenScaffold` / `DinghyIconView` design-system substrate.

The SpoolScreen rebuild is the pilot that validates the kit: Spoolman already exercises list + filter chips + detail card + fill meter + foot buttons, making it the most complete single test of the new grammar.

**Primary recommendation:** Build the kit bottom-up from `ListRow` → `DetailCard` → `FillMeter` → `FootButtonBar` → `SortFilterControlRow`, then rebuild `SpoolScreen` as Wave-1; the kit files extract naturally from that rebuild so later screens just consume them.

---

## Project Constraints (from CLAUDE.md)

- **minSdk 23.** No API-24+ constructs. `WindowMetrics` (API 30) is fine on flox but must not be the only path for U-derivation; use `BoxWithConstraints` + `LocalDensity` instead (available since Compose 1.0).
- **Nexus 7 2013 / Adreno 320 = perf FLOOR.** No continuous/looping animation. Static glow only. Fill-rate is the bottleneck. One-shot transitions if cheap.
- **Portrait AND landscape.** Every component is orientation-responsive. `BoxWithConstraints` for orientation detection (already the `ScreenScaffold` pattern).
- **Full semantic-token theming.** Every component reads `LocalTokens.current` — never a raw color. One named exception per THEMING.md: spool filament spiral (`SpoolGlyph`) and macro PromptMarkup hex are data, not chrome.
- **Kotlin + Jetpack Compose + classic-Views hybrid (ADR-0001).** The kit components are all Compose. Classic Views (FilesScreen, GraphView, BedMeshHeatmapView) are not touched in this phase; `SpoolScreen` is pure Compose.
- **Icons: NEVER auto-pick.** All glyphs come from the owner-curated registry (`DinghyIcons.kt` + `img/material-icon-bucket.json`). Phase 23 registers the 8 new owner-assigned glyphs; the `DinghyIconsTest` drift guard must stay GREEN.
- **`fsSp(baseSp, t.fs)` for ALL text sizes.** Never a bare `.sp`. Floor: 15sp metadata, 17–18sp body, 20–22sp titles, 26sp tabular stats.
- **`@Preview` matrix (6 theme combos + `fs=L`) per PREVIEW_AND_TOKENS.md.** Wave-0 RED scaffolds must compile (all imports resolvable; no refs to unbuilt symbols; `fail()` body only).
- **compileSdk 36, AGP 8.7.x, Compose BOM 2026.05.00 (Compose 1.11), Kotlin 2.1.x.** Do NOT cross to AGP-9 / compileSdk-37 / Compose-1.12.
- **No hardcoded px.** Dimensions via `weight`, `fillMax*`, `aspectRatio`, or `Dp` derived from `U`. The `U` primitive and the `≥64dp` touch floor are the only fixed anchors.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Unit-grid `U` derivation | Compose (composition scope) | — | `BoxWithConstraints` + `LocalDensity` gives landscape height at layout time; no platform API needed |
| `ListRow` / `DetailCard` / `FillMeter` | Compose composables | — | Pure drawing, no Views interop needed |
| `FootButtonBar` | Compose composables | — | Row of `OutlinedControl` elements; existing primitive handles intent |
| `FloatingEStop` | Compose (overlay) | — | `Box` with `Modifier.offset`; sits above Focus corner, not in layout flow |
| `SortFilterControlRow` | Compose composables | — | Stateless; Field-takeover logic lives in holder/state |
| Scrubber (ScrubberPage evolution) | Compose (existing `ScrubberPage.kt`) | — | Already implemented; Phase 23 aligns style to sketch-004 canonical (thin track, ringed thumb) |
| SpoolScreen pilot | Compose | — | Existing `SpoolScreen.kt` rebuilt on the new kit |
| Icon registry additions | `DinghyIcons.kt` + `img/material-icon-bucket.json` | `tools/verify_ligatures.py` | Existing drift-guard infrastructure |
| `docs/ui_design/LAYOUT.md` rewrite | Documentation | — | Prose change; no Kotlin |
| `docs/ui_design/COMPONENTS.md` (new) | Documentation | — | Prose; the spec every screen references |
| `hifi.css` `.ctl.warn` bug fix | `docs/ui_design/reference/hifi.css` | — | CSS-only; Kotlin side already uses `t.heat` directly (not `color-mix`) |

---

## Standard Stack

### No new packages in this phase. [VERIFIED: codebase grep]

All kit components are built on already-present dependencies. No new libraries needed.

| Library | Already in use | Role in Phase 23 |
|---------|---------------|-----------------|
| Jetpack Compose BOM 2026.05.00 | Yes | All kit composables |
| `androidx.compose.foundation` | Yes | `LazyColumn`, `BoxWithConstraints`, `Canvas`, gestures |
| `androidx.compose.material3` | Yes | `Text` (Material3 wrapper) |
| `kotlinx-collections-immutable` 0.3.x | Yes | `ImmutableList` for `ListRow` items parameter |
| Kotlin 2.1.x | Yes | Language |

**Installation:** none required.

---

## Package Legitimacy Audit

No new packages are installed in this phase. This section is intentionally empty. [VERIFIED: phase analysis — all work is new Kotlin composables on existing dependencies]

---

## Architecture Patterns

### System Architecture Diagram

```
UI Layer (Compose)
  ┌─────────────────────────────────────────────────────┐
  │  SpoolScreen (pilot rebuild)                         │
  │                                                      │
  │  ┌────────────── ScreenScaffold ──────────────────┐  │
  │  │ focus { DetailCard + SortFilterControlRow }    │  │
  │  │ field { ListBlock (ListRows) + FootButtonBar } │  │
  │  └────────────────────────────────────────────────┘  │
  │                                                      │
  │  ┌─── FloatingEStop (overlay, printing-only) ─────┐  │
  │  │  Box(absolute top-left of Focus)               │  │
  │  └────────────────────────────────────────────────┘  │
  └─────────────────────────────────────────────────────┘
         │ reads                        │ reads
  ThemeTokens (LocalTokens)      UnitGrid (rememberUnitGrid)
  (t.surface/t.outline/…)        (U: Dp, derived from DPI)
         │                             │
  DinghyIcons registry         BoxWithConstraints landscape height
```

**Data flow for the Field-takeover picker:**
```
SortFilterControlRow: filter tile tap
  → SpoolPickerState.fieldMode: SPOOLS | TYPE | COLOR | MFG
  → SpoolPicker reads fieldMode: shows spool list OR option list
  → pick → holder.apply*(filter) → new SpoolPickerState emitted
  → field reverts to SPOOLS mode
```

### Recommended Project Structure

```
app/src/main/java/works/mees/dinghy/
├── designsystem/
│   ├── layout/
│   │   ├── ScreenScaffold.kt          (existing — keep; no changes)
│   │   ├── UnitGrid.kt                (NEW — rememberUnitGrid(), UnitGrid data class)
│   │   └── ListBlock.kt               (NEW — LazyColumn + edge-fade wrapper)
│   ├── components/                    (NEW package)
│   │   ├── ListRow.kt                 (NEW)
│   │   ├── DetailCard.kt              (NEW)
│   │   ├── FillMeter.kt               (NEW)
│   │   ├── FootButtonBar.kt           (NEW)
│   │   ├── FloatingEStop.kt           (NEW)
│   │   └── SortFilterControlRow.kt    (NEW — sort+filter compound)
│   ├── control/
│   │   ├── OutlinedControl.kt         (existing — keep; no changes)
│   │   └── Intent.kt                  (existing — keep; no changes)
│   ├── icons/
│   │   ├── DinghyIcons.kt             (ADD 8 new owner-assigned entries)
│   │   └── … (existing)
│   └── ScrubberPage.kt                (existing — style-align to sketch-004 canonical in a SEPARATE task)
└── ui/spool/
    ├── SpoolScreen.kt                 (REBUILD on kit)
    └── SpoolPicker.kt                 (UPDATE — uses ListRow, SortFilterControlRow, FootButtonBar)
```

---

## Unit Grid `U` — Derivation and Compose Implementation

### The Formula (from locked design notes)

```
N = clamp(round(landscapeContentHeight / U_target_from_dpi), 5, 7)
U = landscapeContentHeight / N
```

Where `U_target_from_dpi` is approximately `(dpi / 2.4)` — on Nexus 7 2013 (320 dpi, 1200px landscape height, ~48dp system bars), landscape content height ≈ 1200 − 96 = ~1104px → ~69dp. U_target ≈ 133 px / 41 dp. N = clamp(round(1104/133), 5, 7) = clamp(8, 5, 7) = 7. Actual U = 1104/7 ≈ 158 px / ~49 dp.

On a phone-landscape (say 720dp wide, 360dp tall, 300 dpi), landscape content ≈ (360 − content insets) dp. N = 5 floor. U ≈ (content)/5 dp.

**Critical rule:** `U` is derived from the LANDSCAPE content height and held constant through rotation. Portrait just shows more rows (scrolls). This means the U-derivation reads the landscape dimension regardless of current orientation.

### Compose Implementation Pattern

```kotlin
// designsystem/layout/UnitGrid.kt
// Source: .planning/notes/2026-06-09-jiib-redesign-direction.md §"The unit (U)"

data class UnitGrid(
    val uDp: Dp,      // the unit height in dp
    val count: Int,   // how many units fit (5–7)
)

/**
 * Derive the unit grid U from the LANDSCAPE content height — constant through rotation.
 * Call at the top of any screen that needs U-aware sizing.
 *
 * Implementation note: BoxWithConstraints provides maxWidth/maxHeight in Dp already
 * (Compose wraps the density conversion). In landscape, maxHeight IS the content height
 * to divide. In portrait, maxWidth IS the landscape content height (the width is the
 * former landscape height). We read the MINIMUM of maxWidth/maxHeight to get the
 * landscape-constrained dimension regardless of orientation.
 *
 * DPI floor: U can never go below 64.dp (the ≥64dp touch target minimum).
 */
@Composable
fun rememberUnitGrid(contentMinDim: Dp): UnitGrid {
    // U_target_from_dpi: baseline ~41dp (matches ~133px at 320dpi).
    // Derived from: a comfortable tap target, physically ~9mm.
    val density = LocalDensity.current
    val uTargetDp = with(density) {
        // 130–140px baseline, converted to dp for this device's density
        (density.density * 41f).roundToInt().dp  // ~41dp at mdpi-equivalent
    }
    // But simpler and portable: use the constant dp value directly.
    // 41dp * density / density = 41dp — just use 41.dp as the target.
    val N = (contentMinDim / 41.dp).roundToInt().coerceIn(5, 7)
    val uDp = (contentMinDim / N).coerceAtLeast(64.dp)
    return remember(contentMinDim) { UnitGrid(uDp = uDp, count = N) }
}
```

**Usage pattern in a screen:**
```kotlin
// Source: .planning/notes/2026-06-09-jiib-redesign-direction.md §"The unit (U)"
BoxWithConstraints(Modifier.fillMaxSize()) {
    val landscapeDim = minOf(maxWidth, maxHeight) // min = landscape-constrained height
    val grid = rememberUnitGrid(landscapeDim)
    // …
    ListRow(uDp = grid.uDp, …)
    FootButtonBar(uDp = grid.uDp, …)
}
```

**Why `minOf(maxWidth, maxHeight)`:** In landscape `maxHeight < maxWidth`, so min = landscape height. In portrait `maxHeight > maxWidth`, so min = portrait width = the same physical dimension (the tablet's short edge). This is exactly the landscape content height. [VERIFIED: `.planning/notes/2026-06-09-jiib-redesign-direction.md`]

**Perf note:** `rememberUnitGrid` must use `remember(contentMinDim)` so recomposition on orientation flip doesn't recompute every frame — only when the dimension changes. On Adreno 320 this matters.

---

## Component Class API Shapes

All components read `LocalTokens.current` internally. All `Dp` sizes derive from `uDp` (passed in or from `LocalUnitGrid` if you wire a CompositionLocal). All text sizes use `fsSp(baseSp, t.fs)`. All token access is `val t = LocalTokens.current` at the top of the composable.

### 1. `ListRow`

**What:** A single row in a scrollable collection. Translucent background (content fill convention), `outline` border. Selected state: `accentSoft` fill + `accentLine` border + 2dp border. Height = `uDp` (1 unit). Touch target is enforced via `Modifier.heightIn(min = uDp)`.

**Closest existing analog:** `SpoolPicker.kt` — the per-spool row (currently hand-rolled inline). Extract and generalize.

**API shape:**
```kotlin
// designsystem/components/ListRow.kt
@Composable
fun ListRow(
    selected: Boolean,
    onClick: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,  // type icon, color dot, glyph
    trailingContent: (@Composable () -> Unit)? = null, // weight, value, trailing symbol
    content: @Composable RowScope.() -> Unit,          // main label(s)
)
```

**Fill convention (LOCKED):**
```kotlin
// Source: .claude/skills/sketch-findings-dinghy-display/references/foundations.md
val bgColor = if (selected) t.accentSoft else Color.Transparent
val borderColor = if (selected) t.accentLine else t.outline
val borderWidth = if (selected) 2.dp else 1.5.dp
```

**Perf landmine:** The `ListRow` instances in a `LazyColumn` MUST have stable, unique keys. Use the item's ID (e.g. spool ID) as the `items(key = { it.id })` key. Adreno 320 recycles list items aggressively — a missing key causes the entire list to recompose on any change.

---

### 2. `DetailCard`

**What:** The selected-item detail pane. Filled background (`t.surface`), `rCard` corner radius (22dp), color-reactive border (the item's color, or `t.accentLine` / `t.hair` fallback). Contains arbitrary content slots.

**Closest existing analog:** `SpoolDetailFocus` in `SpoolScreen.kt:303-400`. The 3dp `borderColor` logic + `clip(shape).border(...)`.background(t.surface)` pattern is already there — extract it.

**API shape:**
```kotlin
// designsystem/components/DetailCard.kt
@Composable
fun DetailCard(
    modifier: Modifier = Modifier,
    ringColor: Color? = null,    // null → falls back to accentLine
    content: @Composable ColumnScope.() -> Unit,
)
```

**Fill convention:** `DetailCard` is a CONTROL surface (filled), per the content-vs-controls rule. Background = `t.surface`. The `ringColor` is the filament's actual color (a THEME-01 data carve-out) — never clamped via `brandTint`.

**Internal:**
```kotlin
val shape = RoundedCornerShape(t.rCard)
val borderColor = ringColor ?: t.accentLine
Box(
    modifier.clip(shape)
        .border(BorderStroke(3.dp, borderColor), shape)
        .background(t.surface)
)
```

---

### 3. `FillMeter`

**What:** A horizontal "how much remains" bar — thin track, filled to a fraction, tinted by the data color (filament color = data carve-out). Shows value + total as Geist Mono text.

**Closest existing analog:** `ScrubberPage.kt`'s fill bar uses left-anchored fill with `onSizeChanged` for width measurement. The `FillMeter` is READ-ONLY (no gesture), so it's simpler: a `Box` with a `fillMaxWidth(fraction)` fill layer.

**API shape:**
```kotlin
// designsystem/components/FillMeter.kt
@Composable
fun FillMeter(
    fraction: Float,          // 0f..1f (clamped internally)
    fillColor: Color,         // data color — caller supplies (filament hex or t.accent)
    modifier: Modifier = Modifier,
    label: String = "",       // e.g. "735 / 1000 g · 74%"
)
```

**Implementation:**
```kotlin
// Source: .claude/skills/sketch-findings-dinghy-display/references/lists-and-detail.md
// .fill-bar > i { background: <filamentColor>; box-shadow: 0 0 12px -2px currentColor; }
val clamped = fraction.coerceIn(0f, 1f)
Box(modifier.height(6.dp).clip(RoundedCornerShape(999.dp))) {
    Box(Modifier.fillMaxSize().background(t.surface3))  // remainder track
    Box(Modifier.fillMaxHeight().fillMaxWidth(clamped).background(fillColor))  // fill
}
```

**Glow note:** The CSS sketch shows `box-shadow` on the fill. In Compose, a `drawBehind` shadow with `paint.blurMaskFilter` achieves a similar effect but is expensive on Adreno 320. **Omit the glow shadow for the fill bar** — the flat fill color is sufficient. Static glow only on the outline controls; the fill bar gets none.

---

### 4. `FootButtonBar`

**What:** Action buttons pinned to the foot of a list. The generalized gutter replacement for screens with a primary scrollable Field. Height = `1 * uDp`. Uses the same `OutlinedControl` + `Intent` primitive as the existing gutter.

**Closest existing analog:** The Gutter slot of `ScreenScaffold` via `OutlinedControl`. The difference is that `FootButtonBar` lives INSIDE the scrollable field column (pinned at the foot) rather than in the `gutter` slot.

**API shape:**
```kotlin
// designsystem/components/FootButtonBar.kt
@Composable
fun FootButtonBar(
    uDp: Dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,  // caller places OutlinedControl items
)
```

**Internal:** A `Row` with `heightIn(min = uDp)`, `fillMaxWidth()`, `Arrangement.spacedBy(8.dp)`, `padding(horizontal = 8.dp)`. Each `OutlinedControl` in the content uses `Modifier.weight(1f)` for equal distribution.

**Structural note:** `FootButtonBar` goes at the **bottom of the `field` slot lambda** in `ScreenScaffold`, NOT in the `gutter` slot. The old `gutter` slot still exists for screens that keep the gutter grammar (most pre-redesign screens). The SpoolScreen pilot removes the old gutter and moves to `FootButtonBar`.

---

### 5. `FloatingEStop`

**What:** A floating red e-stop button, overlaid top-left of the Focus, shown only when printing. Absolutely positioned — does NOT participate in layout flow.

**Closest existing analog:** The `ConfirmGuard.kt` overlay pattern; the existing e-stop `OutlinedControl` in PrintStatusScreen.

**API shape:**
```kotlin
// designsystem/components/FloatingEStop.kt
@Composable
fun FloatingEStop(
    visible: Boolean,
    onClick: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,  // caller places this in a Box overlay
)
```

**Positioning pattern:** The caller wraps the Focus region in a `Box(Modifier.fillMaxSize())`, with `FloatingEStop` as a sibling — not as a child of the Focus content. Use `Modifier.align(Alignment.TopStart).padding(14.dp)`.

**Phase 23 scope:** The floating e-stop is REGISTERED and BUILT in this phase but only wired to the SpoolScreen pilot for the proof. Full waterfall root integration is Phase 24 (navigation spine). For SpoolScreen, it shows when `isPrinting = true`.

---

### 6. `SortFilterControlRow` (compound — sort + filter)

**What:** The two control-group rows for a list: a sort row (leading `sort` type-tile + sort-option tiles with direction indicator on active) and a filter row (leading `filter_list` type-tile + filter-option tiles). No group-label words. Each type-tile is recessed (`t.bg2` fill, non-interactive).

**Closest existing analog:** `SpoolFilterControls` (inlined in `SpoolScreen.kt:247`). Currently a mix of sort chips and filter buttons. Phase 23 rebuilds it as a proper named component.

**API shape:**
```kotlin
// designsystem/components/SortFilterControlRow.kt
@Composable
fun SortRow(
    options: ImmutableList<SortOption>,  // each has icon: DinghyIcon, key: K, directionUp: Boolean?
    activeKey: K?,
    onSelect: (K) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
)

@Composable
fun FilterRow(
    options: ImmutableList<FilterOption>,  // each has icon: DinghyIcon, key: K, isActive: Boolean
    onSelect: (K) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
)

data class SortOption<K>(val key: K, val icon: DinghyIcon, val contentDescriptionRes: Int, val directionUp: Boolean? = null)
data class FilterOption<K>(val key: K, val icon: DinghyIcon, val contentDescriptionRes: Int, val isActive: Boolean)
```

**Fill convention:**
- Type-tile (leading): `background(t.bg2)` — recessed, non-interactive.
- Sort/filter option tile (inactive): `background(t.surface)` border `t.outline` — filled (control, not content).
- Sort/filter option tile (active): `background(t.accentSoft)` border `t.accentLine` color `t.accent2`.

**Key sizing rule:** Each tile height = `(uDp - 12.dp)` (1U minus the inter-row gap). Tile width can be `weight(1f)` across the option tiles. Minimum tap target = `heightIn(min = 48.dp)` (the floor is U itself which on the test device is ~49dp — passes naturally; enforce 48dp as the fallback floor per PREVIEW_AND_TOKENS.md §8).

---

### 7. Field-Takeover Picker (pattern, not a standalone component)

**What:** When a filter tile is tapped, the Field slot swaps from the spool list to a picker list for that facet. Implemented as a `fieldMode: FieldMode` state in `SpoolPickerState` + conditional rendering in the `field` slot lambda.

**Existing analog:** The `openFilter: SpoolFilterCategory?` state in `SpoolScreen.kt:94` already implements a full-screen overlay version. The Phase 23 redesign replaces the overlay with an in-place Field swap — a simpler pattern.

**Pattern:**
```kotlin
// In SpoolPickerState (add FieldMode):
sealed class FieldMode {
    object Spools : FieldMode()
    data class FilterPicker(val category: SpoolFilterCategory) : FieldMode()
}

// In the field slot of ScreenScaffold:
when (state.fieldMode) {
    is FieldMode.Spools -> {
        ListBlock { /* SpoolList: ListRows */ }
        FootButtonBar(uDp = grid.uDp) { /* Load/Unload/Home/Scan */ }
    }
    is FieldMode.FilterPicker -> {
        ListBlock { /* Option rows for the selected category */ }
        FootButtonBar(uDp = grid.uDp) { /* "Show all / clear" + implicit back */ }
    }
}
```

**No separate screen push.** The back-stack is not involved. Tapping the same filter tile (or a clear button) reverts `fieldMode` to `Spools`. [VERIFIED: `.planning/notes/2026-06-09-jiib-redesign-direction.md` §"Sort vs Filter"]

---

### 8. `ListBlock` (thin wrapper)

**What:** A `LazyColumn` wrapped with edge-fade hints (fade appears only when there is content in that direction, no visible scrollbar). Reusable for any scrollable list in the redesign.

**API shape:**
```kotlin
// designsystem/layout/ListBlock.kt
@Composable
fun ListBlock(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
)
```

**Edge-fade implementation:** Use `Modifier.graphicsLayer { ... }` with a `ComposeShader`/`gradient` approach, OR simply use `Box` with gradient overlays that fade in/out based on `LazyListState.firstVisibleItemIndex > 0` / `canScrollForward`. The latter is simpler and more performant on Adreno 320.

**Perf note:** Avoid `scrollbar-width: none` CSS analog by not adding a scrollbar indicator at all (Compose `LazyColumn` has no scrollbar by default). [VERIFIED: `.claude/skills/sketch-findings-dinghy-display/references/layout-navigation.md`]

---

### 9. `Modifier.cardSurface()` (extension)

A convenience modifier that applies the filled-card appearance (background `t.surface`, border `t.hair`, corner radius `t.rCard`). Used by `DetailCard` internally; exposed for call sites that need a card look on a custom container.

```kotlin
// In designsystem/components/DetailCard.kt or a Modifiers.kt
fun Modifier.cardSurface(t: ThemeTokens): Modifier =
    this.clip(RoundedCornerShape(t.rCard))
        .background(t.surface)
        .border(BorderStroke(1.dp, t.hair), RoundedCornerShape(t.rCard))
```

---

## SpoolScreen Pilot Rebuild — Specific Guidance

### What changes vs. the current `SpoolScreen`

| Current | Redesigned |
|---------|-----------|
| `ScreenScaffold(focus=..., field=..., gutter=...)` | `ScreenScaffold(focus=..., field=...)` — no `gutter` slot |
| Focus = detail pane + filter chips at bottom | Focus = `DetailCard` + `SortFilterControlRow` (unchanged structure, new components) |
| Field = `SpoolPicker` (LazyColumn) only | Field = `ListBlock { ListRows }` + `FootButtonBar` pinned at foot |
| Gutter = Back / Scan / Set (3 `OutlinedControl`) | `FootButtonBar` = Home / Scan / conditional Load/Unload (3 buttons) |
| Filter = full-screen `SpoolFilterPickerOverlay` | Filter = Field-takeover `FieldMode.FilterPicker` in-place |
| No `FloatingEStop` | `FloatingEStop` overlay (visible when printing) |
| No `FillMeter` | `FillMeter` inside `DetailCard` |

### Foot button logic (LOCKED 2026-06-09)

From `.claude/skills/sketch-findings-dinghy-display/references/lists-and-detail.md`:

| Selected spool IS the loaded one | Selected spool is NOT the loaded one |
|----------------------------------|--------------------------------------|
| Show **Unload** (`expand_circle_down`, `Intent.Neutral`) | Show **Load** (`expand_circle_up`, `Intent.Accent`) |

- Always present: **Home** (`home`, `Intent.Accent`) · **Scan** (`qr_code`, `Intent.Accent`)
- Load/Unload is the third button, conditional.

**No "Set Active" button in foot bar.** Loading IS the primary action; the old gutter's green "Set active" button collapses into the "Load" foot button with the same `Intent.Go`. Actually per the sketch notes, Load=accent (ordinary physical command), Unload=neutral (no hazard). Confirm from notes: "Unload (`expand_circle_down`, neutral)" — `Intent.Neutral`. "Load (`expand_circle_up`, accent)" — `Intent.Accent`. [VERIFIED: lists-and-detail.md]

### The `SpoolDetailFocus` rewrite

Current `SpoolDetailFocus` has all the right content — it just needs to be wrapped in `DetailCard` and have a `FillMeter` added. The `ringColor` = `spoolColor ?: if (spool != null) t.accentLine else t.hair` logic already exists in `SpoolScreen.kt:313-314`. Extract it into `DetailCard`'s `ringColor` parameter.

### Stateless preview seam

The existing `SpoolScreen(state: SpoolPickerState, ...)` stateless overload is preserved. The new `fieldMode: FieldMode` field is added to `SpoolPickerState`. `SampleFixtures.kt` gets a new `spoolWithFilterOpen` fixture for the picker-open state.

---

## Icon Registry Changes

### New registrations required in `DinghyIcons.kt` + `DinghyIcons.all`

[VERIFIED: `.planning/notes/2026-06-09-icon-assignments-redesign.md`]

| Val name | Ligature | Alternate | Notes |
|----------|----------|-----------|-------|
| `Sort` | `sort` | `sort` | NEW — wasn't bucketed |
| `FilterList` | `filter_list` | `filter_list` | NEW — wasn't bucketed |
| `ExpandCircleUp` | `expand_circle_up` | `expand_circle_up` | NEW — replaces `play_circle` for Load spool |
| `ExpandCircleDown` | `expand_circle_down` | `expand_circle_down` | NEW — replaces `stop_circle` for Unload spool |
| `ResetWrench` | `reset_wrench` | `reset_wrench` | NEW |
| `ResetSettings` | `reset_settings` | `reset_settings` | NEW — Reset-all |

Already registered, reused on Spoolman (no change needed):
- `Scale` (weigh), `OutputCircle` (extrude/flow), `CheckCircle`, `Palette`, `Storefront`, `Inventory`, `CalendarAddOn`

### `play_circle` / `stop_circle` conflict cleanup

**Current state in `img/material-icon-bucket.json`:**
- `stop_circle` notes: "unload spool"
- `play_circle` notes: "load spool / swap spool"

**Action:** Update their `notes` fields to reflect that load/unload is now owned by `expand_circle_up`/`expand_circle_down`. Keep `play_circle` and `stop_circle` in the bucket but clarify their status ("available for reassignment — load/unload moved to expand_circle_up/down"). Do NOT add them to `DinghyIcons.kt` unless the owner assigns them a new function.

**`verify_ligatures.py` impact:** Add all 6 new ligatures to the tool's expected set. Run the gate after registration. All 6 ligatures are present in the bundled v2.944 Material Symbols font (same font version used in Phase 20 with confirmed ligature resolution — these are standard recent glyphs). [ASSUMED: ligature presence in v2.944 — verify via `verify_ligatures.py` at Wave-0 gate]

---

## `hifi.css` `.ctl.warn` Bug Fix

### The bug (LOCKED finding, 2026-06-09)

```css
/* CURRENT — BUGGY */
.ctl.warn {
  border-color: color-mix(in oklch, var(--heat) 62%, var(--outline));
  …
}
```

`color-mix(in oklch, amber hue ~66, blue-gray hue ~255)` takes the short hue path which passes through hue ~0 (red). The amber-caution border renders orange-red instead of amber. [VERIFIED: `.planning/notes/2026-06-09-jiib-redesign-direction.md` §oklch; `.claude/skills/sketch-findings-dinghy-display/references/foundations.md`]

### The fix

```css
/* FIXED */
.ctl.warn {
  border-color: var(--heat);   /* use --heat directly, no mix */
  color: var(--heat);
  --ctl-glow: var(--heat-glow);
}
.ctl.warn:hover { background: var(--heat-soft); }
```

The Kotlin `OutlinedControl.kt` already uses `t.heat` directly for `Intent.Warn` (line ~58-64) — no Kotlin change needed. The fix is CSS-only in `docs/ui_design/reference/hifi.css`. [VERIFIED: `OutlinedControl.kt` `Intent.Warn -> t.heat`]

---

## `docs/ui_design/LAYOUT.md` Rewrite Guidance

### What must change

The current `LAYOUT.md` describes a THREE-region grammar (Focus / Field / **Gutter**). The redesign removes the Gutter from the grammar. The Kotlin `ScreenScaffold.kt` keeps its optional `gutter` slot (backward-compatible — existing screens still use it) but the grammar doc no longer names it a first-class region.

**New grammar:** Focus / Field on one shared grid. Actions move to `FootButtonBar` (inside Field) or `FloatingEStop` (overlay). System actions → System page. Power → foot button on idle root or System page.

**Preserve:**
- Non-negotiables 1/2/3 (tabular grid, sacred aspect ratios, ratio-only sizing) — unchanged.
- C3 vertical-adjustment rule — unchanged.
- C6 settings-exempt rule — unchanged.
- Orientation rules — unchanged.
- The unit-grid `U` section is ADDED.

**Remove / supersede:**
- The "three regions" opening table — replace with two-region Focus/Field + "where the Gutter's jobs went."
- The gutter scaffold HTML — replace with the `FootButtonBar` + `FloatingEStop` equivalents.
- The "Gutter is present unless…" conditional — remove.

**New sections to add:**
- §"The unit `U`" — the DPI derivation formula and Compose pattern.
- §"Content vs controls — fill convention" — `ListRow` = translucent, `DetailCard`/buttons/tiles = filled.
- §"Foot-of-list pattern" — replaces the Gutter for per-screen actions.
- §"Floating e-stop" — overlay, printing-only.

---

## `docs/ui_design/COMPONENTS.md` (New Document)

This document is the **component-class catalog** — the spec the owner points at when describing a screen element. It lives beside `THEMING.md` and the rewritten `LAYOUT.md`.

**Required sections:**

1. **Philosophy** — CSS-like named classes; shared vocabulary; built by extracting from real screens, not abstract-kit-first.
2. **Fill convention** (content vs controls) — the load-bearing visual rule.
3. **The component catalog** — one entry per class: class name, what it is, fill type, border, background, usage rule.
4. **The unit `U`** — the DPI-derived standard module height.
5. **Interaction patterns** — Field-takeover picker, sort toggle vs filter selection, conditional Load/Unload.

**Catalog table:**

| Class | Fill type | Background | Border | Primary content |
|-------|-----------|------------|--------|----------------|
| `ListRow` | Transparent (content) | `Color.Transparent` / `accentSoft` if selected | `outline` / `accentLine` if selected | Scrollable item |
| `DetailCard` | Filled (control) | `surface` | `ringColor` (data) or `accentLine` or `hair` | Selected item detail |
| `FillMeter` | Filled fill layer | `surface3` (track) + data fill | — | Read-only fraction bar |
| `FootButtonBar` | — (container only) | — | — | Row of `OutlinedControl` at foot of list |
| `FloatingEStop` | Filled (danger) | `stopSoft` / transparent | `stop` | Print-cancel overlay, printing only |
| `SortFilterControlRow` | Filled (control) | type-tile: `bg2`; option tile: `surface` | `outline` / `accentLine` if active | Sort/filter surface for a list |
| Control tile (general) | Filled | `surface` | `outline` / intent-line if active | Interactive grid tile |
| `ListBlock` | — (scroll wrapper) | — | — | Edge-faded LazyColumn container |

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| List scroll | A custom scroll implementation | `LazyColumn` + `rememberLazyListState()` | Compose's `LazyColumn` handles all recycling and reuse automatically |
| Edge fades | `Canvas` gradient shader | `Box` gradient overlays driven by `lazyListState.firstVisibleItemIndex > 0` | Cheaper on Adreno 320 |
| Fill-bar gestures | Re-implementing the scrubber | Extend `ScrubberPage.kt` (already built, P19 regression-hardened) | The P19 regression (`fa97efb`) proved rebuilding from scratch breaks; the existing `ScrubberPage` is correct |
| Theme-aware colors | Raw `Color(0xFF...)` | `LocalTokens.current.X` | The whole point of the token system; will break in light/custom themes |
| Icon glyph selection | Picking a "reasonable" Material Symbol | Stop and ask the owner | Hard law — see `docs/ui_design/CLAUDE.md` |
| `U` derivation per-component | Hardcoded `dp` values | `rememberUnitGrid()` at screen level, pass `uDp` down | Ensures physical tap-target parity across densities |

**Key insight:** The design system already has the majority of needed primitives (`OutlinedControl`, `ScreenScaffold`, `DinghyIconView`, `SpoolGlyph`, `ScrubberPage`). Phase 23 adds the missing collection/detail layer on top — it does NOT replace what's there.

---

## Common Pitfalls

### Pitfall 1: Putting `FootButtonBar` in the `gutter` slot
**What goes wrong:** `ScreenScaffold.gutter` still exists for pre-redesign screens. If the redesigned `SpoolScreen` puts its foot buttons in `gutter`, the layout behaves like the old grammar (full-width bar below the stage), not pinned to the foot of the Field list.
**Why it happens:** Copy-paste from current `SpoolContent`.
**How to avoid:** Null out `gutter = null` in the redesigned `SpoolContent`; the `FootButtonBar` is the LAST element inside the `field` lambda Column, not a separate gutter.
**Warning signs:** Foot buttons appear detached from the list in portrait mode; there is empty space between list content and foot buttons.

### Pitfall 2: Recomposing `ListRow` items on every state change
**What goes wrong:** Without `key` parameters on `LazyColumn.items(...)`, any `SpoolPickerState` change (even selecting a filter chip) causes the ENTIRE list to recompose. On Adreno 320 this is visible jank.
**Why it happens:** `items(state.spools)` without a key.
**How to avoid:** `items(state.spools, key = { it.id }) { spool -> ListRow(...) }`.
**Warning signs:** Profiling shows recomposition of all list items on a non-list state change.

### Pitfall 3: `color-mix(in oklch, ...)` for caution borders
**What goes wrong:** Amber caution border reads orange-red because oklch interpolation passes through hue 0 on the short path from amber (66) to blue-gray (255).
**Why it happens:** Copying the CSS pattern from `hifi.css` before the fix.
**How to avoid:** Use `t.heat` directly for all caution-intent borders. Never mix `heat` with `outline` in oklch.
**Warning signs:** Caution controls look red instead of amber — especially visible in High-Contrast mode.

### Pitfall 4: `U` computed from portrait height in landscape orientation
**What goes wrong:** `U` is too large in portrait (using `maxHeight`) and changes when the user rotates, making rows physically different sizes in each orientation.
**Why it happens:** Natural mistake to use `maxHeight` (the "height of the screen").
**How to avoid:** Always `minOf(maxWidth, maxHeight)` in `BoxWithConstraints` — this gives the landscape-constrained dimension regardless of orientation.
**Warning signs:** `uDp` value visible in a debug log differs between portrait and landscape.

### Pitfall 5: Scrubber rebuild instead of reuse
**What goes wrong:** A new scrubber composable is written for Phase 23, re-introducing the P19 regression (fill grows from middle, value doesn't stick on drag) because the pointer closure is stale.
**Why it happens:** `ScrubberPage.kt` looks like a whole-page wrapper and may not seem reusable for inline use.
**How to avoid:** Read `ScrubberPage.kt` carefully. The fix (`fa97efb`) is in how the internal `working` `MutableState` is updated within `pointerInput` — any scrubber MUST mirror this pattern. If a scrubber is needed inline (not full-page), extract the fill bar + drag logic from `ScrubberPage` into a `ScrubberBar` composable rather than rewriting from scratch.
**Warning signs:** Drag behavior: fill grows from the center instead of anchored left; dragging and releasing leaves the value at the drag-start position.

### Pitfall 6: `@Preview(fontScale = 1.3f)` for large-text preview
**What goes wrong:** The `fs=L` preview renders identically to `fs=M` because `DinghyTheme` pins `fontScale = 1f` at the Compose root.
**Why it happens:** `@Preview(fontScale = ...)` works in Material3 apps but not here.
**How to avoid:** Use `PreviewBox(fsLargeSeed)` — the only way to preview large text. [VERIFIED: `PreviewTheming.kt:36-42`]
**Warning signs:** The `*FsLargeOverflow` preview panel looks identical to the regular preview.

### Pitfall 7: Putting `FloatingEStop` inside the Focus layout (instead of as an overlay)
**What goes wrong:** The e-stop button shifts layout geometry in the Focus area, displacing the main display item.
**Why it happens:** Composing it as a child of the Focus content column.
**How to avoid:** The `FloatingEStop` is a `Box` overlay sibling of the Focus content inside a wrapping `Box(Modifier.fillMaxSize())`. Position with `Modifier.align(Alignment.TopStart).padding(14.dp)`.
**Warning signs:** Focus content is offset or displaced when the e-stop is visible.

---

## Code Examples

### Unit grid derivation in a screen

```kotlin
// Source: .planning/notes/2026-06-09-jiib-redesign-direction.md §"The unit (U)"
BoxWithConstraints(Modifier.fillMaxSize()) {
    // min(width, height) = the landscape-constrained short edge, constant through rotation.
    val landscapeDim = minOf(maxWidth, maxHeight)
    val N = ((landscapeDim / 41.dp).roundToInt()).coerceIn(5, 7)
    val uDp = (landscapeDim / N).coerceAtLeast(64.dp)
    // …
}
```

### `ListRow` fill convention

```kotlin
// Source: .claude/skills/sketch-findings-dinghy-display/references/foundations.md
@Composable
fun ListRow(selected: Boolean, onClick: () -> Unit, uDp: Dp, modifier: Modifier = Modifier,
            content: @Composable RowScope.() -> Unit) {
    val t = LocalTokens.current
    val bgColor = if (selected) t.accentSoft else Color.Transparent
    val borderColor = if (selected) t.accentLine else t.outline
    val borderWidth = if (selected) 2.dp else 1.5.dp
    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = uDp)
            .clip(shape)
            .background(bgColor)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
```

### `DetailCard` color-reactive border

```kotlin
// Source: SpoolScreen.kt:311-316 (existing pattern — extract and generalize)
@Composable
fun DetailCard(ringColor: Color? = null, modifier: Modifier = Modifier,
               content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val border = ringColor ?: t.accentLine
    Column(
        modifier.clip(shape).border(BorderStroke(3.dp, border), shape).background(t.surface)
    ) { content() }
}
```

### Icon registration pattern

```kotlin
// Source: DinghyIcons.kt (existing pattern — add new entries)
val Sort = DinghyIcon(IconRef.Ligature("sort"), alternate = "sort")
val FilterList = DinghyIcon(IconRef.Ligature("filter_list"), alternate = "filter_list")
val ExpandCircleUp = DinghyIcon(IconRef.Ligature("expand_circle_up"), alternate = "expand_circle_up")
val ExpandCircleDown = DinghyIcon(IconRef.Ligature("expand_circle_down"), alternate = "expand_circle_down")
val ResetWrench = DinghyIcon(IconRef.Ligature("reset_wrench"), alternate = "reset_wrench")
val ResetSettings = DinghyIcon(IconRef.Ligature("reset_settings"), alternate = "reset_settings")
// … add all 6 to DinghyIcons.all
```

### Preview matrix pattern (copy from `SpoolPreviews.kt`)

```kotlin
// Source: app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt
// The SpoolScreen rebuild's preview file follows the SAME structure:
// - *SelectionMatrix  (axis = selection state, theme = colorfulDark)
// - *ThemeColorfulDark … *ThemeHighContrastLight  (6 theme sibling fns)
// - *FsLargeOverflow  (PreviewBox(fsLargeSeed))
// - *RtlSpotCheck     (CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl))
// - *PseudolocaleSpotCheck  (@Preview(device = NEXUS7, locale = "en-XA")) — standalone, NOT @Nexus7Previews
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Focus/Field/Gutter grammar | Focus/Field + FootButtonBar + FloatingEStop | 2026-06-09 (Phase 23) | Gutter removed; actions move to foot of list or floating overlay |
| Full-screen filter overlay (`SpoolFilterPickerOverlay`) | Field-takeover in-place picker (`FieldMode`) | 2026-06-09 (Phase 23) | No navigation; consistent with lists-first grammar |
| Per-screen ad-hoc button rows | `FootButtonBar` component | 2026-06-09 (Phase 23) | Shared vocabulary; plannable per screen |
| Hub-and-spoke home (App Drawer tile grid) | Morphing waterfall root (Phase 24) | — | Phase 23 establishes the components; Phase 24 builds the morphing root |

**Deprecated/outdated for redesigned screens:**
- Gutter slot usage: still functional in `ScreenScaffold.kt` for pre-redesign screens; redesigned screens use `gutter = null` + `FootButtonBar`.
- `SpoolFilterPickerOverlay`: replaced by Field-takeover in the SpoolScreen rebuild.

---

## Open Questions

1. **`UnitGrid` as a `CompositionLocal` or passed explicitly?**
   - What we know: `rememberUnitGrid()` must be called at the screen root (inside `BoxWithConstraints`). Child components need `uDp`.
   - What's unclear: Should `uDp` be passed explicitly to each component (verbose but visible), or hoisted into a `CompositionLocal<UnitGrid>` (cleaner call sites but invisible dependency)?
   - Recommendation: Pass `uDp: Dp` explicitly for Phase 23 (the pilot; 3–4 components). Promote to `CompositionLocal` once multiple screens use it. Explicit is easier to understand and test.

2. **ScrubberPage style alignment to sketch-004**
   - What we know: Sketch 004 defines a canonical scrubber style (thin 6px track, ringed thumb with large invisible touch target). The current `ScrubberPage.kt` uses a full-height fill bar — a different visual style.
   - What's unclear: Does the Phase 23 scope include restyling `ScrubberPage` to match sketch-004, or is that deferred to Phase 26 (Adjustment)?
   - Recommendation: **Defer to Phase 26.** Phase 23's pilot is `SpoolScreen` which does NOT use a scrubber. The `ScrubberPage` restyle touches every screen that uses it (Output, Fine-Tune, Temperature); that is out of scope for the Foundation phase.

3. **`FootButtonBar` in `SpoolScreen`: replace the old Load/Unload logic in gutter vs. new foot**
   - What we know: Old gutter = Back / Scan / Set-Active. New foot = Home / Scan / Load or Unload.
   - What's unclear: The "Set Active" gutter button was the primary spool-assignment action. Load replaces it. Is `onSetActive` / `onClearActive` still the right callback, or renamed to `onLoad` / `onUnload`?
   - Recommendation: Rename to `onLoad` / `onUnload` at the SpoolScreen interface; the underlying `holder.setActiveSpool(dispatcher, id)` / `holder.clearActiveSpool(dispatcher)` calls remain unchanged internally. The rename aligns the public API with the redesigned UX vocabulary.

---

## Environment Availability

This phase is code/doc changes only. No external tools, databases, or services beyond the standard Android build environment.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `gw.bat` Gradle wrapper | Build | Yes | AGP 8.7.x | — |
| JDK 21 | Build | Yes | 21.0.10 | — |
| Android SDK (compileSdk 36) | Build | Yes | — | — |
| `verify_ligatures.py` | Wave-0 ligature gate | Yes (in `tools/`) | — | — |
| `DinghyIconsTest` | Registry drift guard | Yes | — | — |
| flox device (adb) | On-device pilot UAT | Yes (owner provides) | LineageOS 18.1 / API 30 | — |

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Kotlin test (host) — existing setup |
| Config file | `app/src/test/` (host unit tests — no instrumented for this phase) |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.designsystem.* --no-daemon"` |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |

### Phase Requirements → Test Map

| Req | Behavior | Test Type | Automated Command | File Exists? |
|-----|----------|-----------|-------------------|-------------|
| SC-1 | `DinghyIconsTest`: 6 new icons have unique `alternate`; all in `all` list | unit | `:app:testDebugUnitTest --tests works.mees.dinghy.designsystem.icons.DinghyIconsTest` | Yes |
| SC-1 | `verify_ligatures.py` resolves all 6 new ligatures in v2.944 font | tool | `python tools/verify_ligatures.py` | Yes |
| SC-2 | `ListRow` renders transparent background when unselected, `accentSoft` when selected | unit | New: `ListRowTest` | No — Wave 0 |
| SC-2 | `FillMeter` clamps fraction to 0..1 | unit | New: `FillMeterTest` | No — Wave 0 |
| SC-2 | `rememberUnitGrid` returns N in [5,7] and `uDp ≥ 64.dp` for any input | unit | New: `UnitGridTest` | No — Wave 0 |
| SC-3 | `SpoolScreen` (rebuilt) host unit suite GREEN (existing `SpoolHolderTest`, `SpoolPickerStateTest`) | unit | `:app:testDebugUnitTest --tests works.mees.dinghy.ui.spool.*` | Yes |
| SC-4 | `hifi.css .ctl.warn` fix: border-color is `var(--heat)` not `color-mix(...)` | grep | `grep -c 'color-mix.*heat.*outline' docs/ui_design/reference/hifi.css` (must be 0) | — |
| SC-5 | `@Preview` matrix builds clean (no compile errors) | compile | `:app:compileDebugKotlin` | No — Wave 0 |

### Wave 0 Gaps

- [ ] `app/src/test/java/works/mees/dinghy/designsystem/layout/UnitGridTest.kt` — covers `rememberUnitGrid()` pure logic (no Compose needed; test the formula directly)
- [ ] `app/src/test/java/works/mees/dinghy/designsystem/components/ListRowTest.kt` — basic rendering smoke (or a pure logic test for fill/border state selection)
- [ ] `app/src/test/java/works/mees/dinghy/designsystem/components/FillMeterTest.kt` — fraction clamping
- [ ] Wave-0 RED `@Preview` scaffolds for all new kit files must compile (fail() bodies, no refs to unbuilt symbols)

**Framework install:** None — existing JUnit 4 setup in `app/src/test/` is sufficient.

---

## Security Domain

`security_enforcement` is not explicitly set in config — treated as enabled. However, Phase 23 contains no authentication, network calls, data storage, or user-input validation. All work is pure UI composables and documentation.

| ASVS Category | Applies | Notes |
|---------------|---------|-------|
| V2 Authentication | No | No auth logic in this phase |
| V3 Session Management | No | No session logic |
| V4 Access Control | No | No access gates |
| V5 Input Validation | No | No text input; `FillMeter.fraction` is clamped to 0..1 (safe math, not user input) |
| V6 Cryptography | No | No crypto |

No threat patterns apply to a UI-only phase.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `reset_wrench`, `reset_settings`, `expand_circle_up`, `expand_circle_down`, `sort`, `filter_list` all resolve in the bundled v2.944 Material Symbols font | Icon Registry Changes | Build will still work (ligature renders as `?` fallback); `verify_ligatures.py` will catch it at Wave-0 gate; planner must include verify step |
| A2 | `UnitGrid` is best as explicit `uDp: Dp` parameter rather than `CompositionLocal` for this phase | Open Questions §1 | Only ergonomic impact; if 4+ components chain it a `CompositionLocal` is cleaner — trivially refactorable |
| A3 | `ScrubberPage` restyling to sketch-004 canonical is deferred to Phase 26 | Open Questions §2 | If the owner wants sketch-004 scrubber style in this phase, the scope grows; address at plan-discuss time |

---

## Sources

### Primary (HIGH confidence)
- `.planning/notes/2026-06-09-jiib-redesign-direction.md` — unit grid formula, gutter removal, sort/filter grammar, oklch hazard [VERIFIED: direct read]
- `.planning/notes/2026-06-09-component-classes-catalog.md` — component class philosophy, fill convention, catalog seed list [VERIFIED: direct read]
- `.planning/notes/2026-06-09-icon-assignments-redesign.md` — owner-assigned glyph assignments [VERIFIED: direct read]
- `.claude/skills/sketch-findings-dinghy-display/references/foundations.md` — U formula, fill CSS, intent colors [VERIFIED: direct read]
- `.claude/skills/sketch-findings-dinghy-display/references/lists-and-detail.md` — Spoolman anatomy, Field-takeover, DetailCard, FillMeter, foot buttons [VERIFIED: direct read]
- `.claude/skills/sketch-findings-dinghy-display/references/adjustment-controls.md` — stepper anatomy, scrubber style, unit-fit, oklch fix [VERIFIED: direct read]
- `.claude/skills/sketch-findings-dinghy-display/references/layout-navigation.md` — morphing home, FloatingEStop, foot-of-list [VERIFIED: direct read]
- `docs/ui_design/LAYOUT.md` — current grammar (to be rewritten) [VERIFIED: direct read]
- `docs/ui_design/THEMING.md` — token names, intent colors, `fsSp`, `rCard`/`rCtrl` [VERIFIED: direct read]
- `docs/ui_design/PREVIEW_AND_TOKENS.md` — preview matrix convention [VERIFIED: direct read]
- `docs/ui_design/CLAUDE.md` — design philosophy, icon law [VERIFIED: direct read]

### Secondary (HIGH confidence — codebase)
- `app/src/main/java/works/mees/dinghy/designsystem/layout/ScreenScaffold.kt` — existing `BoxWithConstraints` orientation detection pattern [VERIFIED: direct read]
- `app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt` — `Intent` enum, `t.heat` direct usage [VERIFIED: direct read]
- `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — registry structure, `all` list pattern [VERIFIED: direct read]
- `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt` — fill bar pattern, `MutableState` + `pointerInput` closure [VERIFIED: direct read]
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` — existing `SpoolDetailFocus` border pattern, `SpoolContent` structure [VERIFIED: direct read]
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt` — existing list row implementation [VERIFIED: direct read]
- `app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt` — preview matrix template [VERIFIED: direct read]
- `app/src/main/java/works/mees/dinghy/preview/PreviewTheming.kt` — `PreviewBox`, `fsLargeSeed`, `themeCombos` [VERIFIED: direct read]
- `docs/ui_design/reference/hifi.css` — `.ctl.warn` color-mix bug confirmed at line 225 [VERIFIED: direct read]
- `img/material-icon-bucket.json` — `play_circle`/`stop_circle` conflict notes confirmed [VERIFIED: direct grep]

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new packages; all work is on existing stack
- Architecture: HIGH — design is pre-locked across authoritative notes; component API shapes derived directly from locked CSS patterns + existing codebase analogs
- Pitfalls: HIGH — 6 of 7 pitfalls directly observed in prior phases (P19 scrubber regression, P22 recomposition, P18 icon law, P18.3 overlay positioning)
- Icon ligature resolution: ASSUMED (A1) — flagged for Wave-0 verify gate

**Research date:** 2026-06-09
**Valid until:** 2026-07-09 (design is locked; library versions are pinned; 30-day validity)
