# Phase 23: Design-Language Foundation — Pattern Map

**Mapped:** 2026-06-09
**Files analyzed:** 14 new/modified files
**Analogs found:** 13 / 14

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `designsystem/layout/UnitGrid.kt` | utility | transform | `ScreenScaffold.kt` (BoxWithConstraints orientation) | role-match |
| `designsystem/layout/ListBlock.kt` | component | CRUD (read-only scroll) | `SpoolPicker.kt` (LazyColumn + state) | role-match |
| `designsystem/components/ListRow.kt` | component | request-response | `SpoolPicker.kt` `SpoolRow`/`OptionButton` (lines 448–491, 494–561) | exact |
| `designsystem/components/DetailCard.kt` | component | CRUD (display) | `SpoolScreen.kt` `SpoolDetailFocus` (lines 302–447) | exact |
| `designsystem/components/FillMeter.kt` | component | transform | `ScrubberPage.kt` fill-bar Box (lines 239–245) | role-match |
| `designsystem/components/FootButtonBar.kt` | component | request-response | `OutlinedControl.kt` + `SpoolContent` gutter Row (lines 261–294) | role-match |
| `designsystem/components/FloatingEStop.kt` | component | event-driven | `ConfirmGuard.kt` overlay Box + `PrintStatusScreen.kt` overlay siblings (lines 261–339) | role-match |
| `designsystem/components/SortFilterControlRow.kt` | component | request-response | `SpoolPicker.kt` `SpoolFilterControls`/`CategoryButton` (lines 134–203) | exact |
| `ui/spool/SpoolScreen.kt` (REBUILD) | component | CRUD | `SpoolScreen.kt` + `SpoolPicker.kt` (existing — rebuild on kit) | exact (self) |
| `designsystem/icons/DinghyIcons.kt` (ADD 6 entries) | config | — | `DinghyIcons.kt` (existing — pattern already established) | exact (self) |
| `img/material-icon-bucket.json` (update notes) | config | — | `img/material-icon-bucket.json` (existing) | exact (self) |
| `docs/ui_design/LAYOUT.md` (rewrite) | documentation | — | existing doc + sketch notes | exact (self) |
| `docs/ui_design/COMPONENTS.md` (NEW) | documentation | — | `.planning/notes/2026-06-09-component-classes-catalog.md` | role-match |
| `docs/ui_design/reference/hifi.css` (`.ctl.warn` fix) | config | — | `OutlinedControl.kt` `Intent.Warn` (line 61) | exact |

---

## Pattern Assignments

### `designsystem/layout/UnitGrid.kt` (utility, transform)

**Analog:** `ScreenScaffold.kt` — `BoxWithConstraints` orientation detection

**Imports pattern** (ScreenScaffold.kt lines 1–12):
```kotlin
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
```

**Core pattern — BoxWithConstraints orientation-agnostic dimension** (ScreenScaffold.kt lines 64–65):
```kotlin
BoxWithConstraints(modifier.fillMaxSize()) {
    val landscape = maxWidth > maxHeight
    // …use maxWidth/maxHeight as Dp directly (Compose wraps density)
}
```

**UnitGrid derivation pattern** (from RESEARCH.md §"Unit Grid"):
```kotlin
data class UnitGrid(val uDp: Dp, val count: Int)

@Composable
fun rememberUnitGrid(contentMinDim: Dp): UnitGrid {
    // contentMinDim = minOf(maxWidth, maxHeight) at the call site —
    // this is ALWAYS the landscape-constrained short edge regardless of orientation.
    // minOf(w,h): in landscape maxHeight < maxWidth so min = landscape height;
    // in portrait maxHeight > maxWidth so min = portrait width = same physical dim.
    val N = ((contentMinDim / 41.dp).roundToInt()).coerceIn(5, 7)
    val uDp = (contentMinDim / N).coerceAtLeast(64.dp)
    return remember(contentMinDim) { UnitGrid(uDp = uDp, count = N) }
}
```

**Usage at screen root:**
```kotlin
BoxWithConstraints(Modifier.fillMaxSize()) {
    val landscapeDim = minOf(maxWidth, maxHeight)
    val grid = rememberUnitGrid(landscapeDim)
    // pass grid.uDp explicitly to child components
}
```

**Perf constraint:** `remember(contentMinDim)` key is mandatory — prevents recomputing U on every recomposition; only recomputes when the physical dimension changes (orientation flip).

---

### `designsystem/layout/ListBlock.kt` (component, CRUD read-only scroll)

**Analog:** `SpoolPicker.kt` lines 91–125 — `LazyColumn` with `items(key = { it.id })`, empty-state branching, `Arrangement.spacedBy`

**Imports pattern** (SpoolPicker.kt lines 1–45, relevant subset):
```kotlin
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
```

**Core pattern — LazyColumn with items keyed** (SpoolPicker.kt lines 111–125):
```kotlin
LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items(state.spools, key = { it.id }) { spool ->
        SpoolRow(…)
    }
}
```

**ListBlock API shape** (thin wrapper — caller provides the `LazyListScope.() -> Unit`):
```kotlin
@Composable
fun ListBlock(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
        // Edge-fade overlays: Box gradient driven by listState.firstVisibleItemIndex > 0
        // and listState.canScrollForward — cheaper on Adreno 320 than ComposeShader.
    }
}
```

**Pitfall to avoid:** Do NOT add a scrollbar indicator — `LazyColumn` has no visible scrollbar by default; the edge-fade is the scroll hint. Do NOT embed `Arrangement.spacedBy` inside the items themselves; put it on the column.

---

### `designsystem/components/ListRow.kt` (component, request-response)

**Analog:** `SpoolPicker.kt` — `SpoolRow` (lines 494–561) and `OptionButton` (lines 448–491)

**Imports pattern** (SpoolPicker.kt lines 1–45, relevant):
```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import works.mees.dinghy.theme.compose.LocalTokens
```

**Fill/border selection pattern** (SpoolPicker.kt `SpoolRow` lines 503–516, `OptionButton` lines 456–466):
```kotlin
// SpoolRow — selected/active/default 3-way:
val outline = when {
    selected -> t.accentLine
    isActive -> t.go
    else -> t.outline
}
Row(
    Modifier
        .fillMaxWidth()
        .clip(shape)
        .border(BorderStroke(2.dp, outline), shape)
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    …
)

// OptionButton — selected/unselected:
Row(
    Modifier
        .fillMaxWidth()
        .heightIn(min = 64.dp)
        .clip(shape)
        .border(BorderStroke(2.dp, if (selected) t.accentLine else t.outline), shape)
        .background(if (selected) t.accentSoft else Color.Transparent)
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp),
    …
)
```

**ListRow generalized API shape** (combines both — uses `uDp` for touch target):
```kotlin
@Composable
fun ListRow(
    selected: Boolean,
    onClick: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val bgColor = if (selected) t.accentSoft else Color.Transparent
    val borderColor = if (selected) t.accentLine else t.outline
    val borderWidth = if (selected) 2.dp else 1.5.dp
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = uDp)            // touch floor = U (≥64dp enforced by U itself)
            .clip(shape)
            .background(bgColor)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.invoke()
        content()
        trailingContent?.invoke()
    }
}
```

**Text sizes within a row** (SpoolPicker.kt `SpoolRow` lines 521–557):
```kotlin
// Primary label: fsSp(18f, t.fs).sp, Geist, SemiBold
// Secondary/metadata: fsSp(15f, t.fs).sp, Geist  ← floor
// Trailing tabular value: fsSp(18f, t.fs).sp, GeistMono, SemiBold
```

**Key law:** `items(list, key = { it.id }) { ListRow(…) }` — always provide a stable unique key. Missing key causes whole-list recomposition on any state change on Adreno 320.

---

### `designsystem/components/DetailCard.kt` (component, CRUD display)

**Analog:** `SpoolScreen.kt` — `SpoolDetailFocus` (lines 302–447)

**Imports pattern** (SpoolScreen.kt lines 1–54, relevant):
```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import works.mees.dinghy.theme.compose.LocalTokens
```

**Color-reactive border + filled surface pattern** (SpoolScreen.kt lines 309–321):
```kotlin
val t = LocalTokens.current
val shape = RoundedCornerShape(t.rCard)
// spoolColor = first valid swatch hex parsed to Color; null when no spool
val spoolColor = spool?.filament?.colorSwatches?.firstNotNullOfOrNull { parseNormalizedHex(it) }
val borderColor = spoolColor ?: if (spool != null) t.accentLine else t.hair
Box(
    modifier
        .clip(shape)
        .border(BorderStroke(3.dp, borderColor), shape)
        .background(t.surface)
        .padding(16.dp),
) { /* content */ }
```

**DetailCard generalized API shape:**
```kotlin
@Composable
fun DetailCard(
    modifier: Modifier = Modifier,
    ringColor: Color? = null,   // null → t.accentLine; data carve-out — NOT clamped by brandTint
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val borderColor = ringColor ?: t.accentLine
    Column(
        modifier
            .clip(shape)
            .border(BorderStroke(3.dp, borderColor), shape)
            .background(t.surface)
            .padding(16.dp),
        content = content,
    )
}
```

**`cardSurface()` Modifier extension** (for call sites needing the card look without the Column wrapper):
```kotlin
fun Modifier.cardSurface(t: ThemeTokens): Modifier =
    this.clip(RoundedCornerShape(t.rCard))
        .background(t.surface)
        .border(BorderStroke(1.dp, t.hair), RoundedCornerShape(t.rCard))
```

**Token carve-out:** `ringColor` is the raw filament hex (THEMING.md D-10 carve-out, same precedent as `SpoolDetailFocus`). Never pass it through `brandTint` — that would distort the true physical color.

---

### `designsystem/components/FillMeter.kt` (component, transform)

**Analog:** `ScrubberPage.kt` — the fill-bar `Box` (lines 239–245), `onSizeChanged` measurement

**Fill-bar implementation pattern** (ScrubberPage.kt lines 194–248):
```kotlin
// The accent-tinted fill tracks the value (left-anchored, fillMaxWidth(fraction)):
Box(
    Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(t.rCard))
        .background(t.surface2)           // track background
        .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCard))
        .onSizeChanged { barWidthPx = it.width.toFloat() }
        // …gesture detector (FillMeter is READ-ONLY — omit pointerInput entirely)
) {
    Box(                                   // fill layer, left-anchored
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(fraction)        // fraction = 0f..1f
            .background(t.accentSoft),
    )
}
```

**FillMeter API shape** (read-only — simpler than ScrubberPage, no gesture):
```kotlin
@Composable
fun FillMeter(
    fraction: Float,        // caller supplies 0f..1f; clamped internally
    fillColor: Color,       // data carve-out — filament hex or t.accent; never a theme role
    modifier: Modifier = Modifier,
    label: String = "",     // e.g. "735 / 1000 g · 74%"
) {
    val t = LocalTokens.current
    val clamped = fraction.coerceIn(0f, 1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().height(6.dp)
                .clip(RoundedCornerShape(999.dp))
        ) {
            Box(Modifier.fillMaxSize().background(t.surface3))           // track
            Box(Modifier.fillMaxHeight().fillMaxWidth(clamped).background(fillColor))  // fill
        }
        if (label.isNotEmpty()) {
            Text(label, color = t.text2, fontFamily = GeistMono,
                 fontSize = fsSp(15f, t.fs).sp)
        }
    }
}
```

**No glow on fill bar:** The CSS sketch shows `box-shadow` on the fill, but `blurMaskFilter` on Adreno 320 costs fill rate we can't spare. Flat fill color only. Static glow lives only on `OutlinedControl` borders.

---

### `designsystem/components/FootButtonBar.kt` (component, request-response)

**Analog:** `SpoolScreen.kt` `SpoolContent` gutter Row (lines 261–294) + `OutlinedControl.kt`

**Gutter Row pattern** (SpoolScreen.kt lines 261–294):
```kotlin
gutter = {
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedControl(
            label = stringResource(R.string.common_back),
            onClick = onBack,
            modifier = Modifier.weight(1f),
            intent = Intent.Neutral,
            symbol = "arrow_back",
        )
        OutlinedControl(
            label = "",
            onClick = onScan,
            modifier = Modifier.weight(1f),
            intent = Intent.Accent,
            symbol = "qr_code",
        )
        OutlinedControl(
            label = "",
            onClick = onSetActive,
            onLongClick = onClearActive,
            modifier = Modifier.weight(1f),
            intent = Intent.Go,
            symbol = "add_circle",
        )
    }
},
```

**FootButtonBar API shape** (lives INSIDE `field` lambda, NOT in `gutter` slot):
```kotlin
@Composable
fun FootButtonBar(
    uDp: Dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = uDp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
```

**Structural rule:** `FootButtonBar` is the LAST element inside the `field` slot lambda Column. Pass `gutter = null` in redesigned screens. The old `gutter` slot in `ScreenScaffold.kt` stays for pre-redesign screens (backward-compatible). Each `OutlinedControl` inside `FootButtonBar` uses `Modifier.weight(1f)` for equal distribution, mirroring the gutter Row pattern exactly.

---

### `designsystem/components/FloatingEStop.kt` (component, event-driven)

**Analog:** `PrintStatusScreen.kt` overlay siblings pattern (lines 261–339) + `ConfirmGuard.kt` overlay Box (lines 91–139)

**Overlay sibling pattern** (PrintStatusScreen.kt lines 261–339):
```kotlin
// The hosting Box wraps both the content and any overlay sibling:
Box(modifier.fillMaxSize()) {
    PrintStatusContent(…)           // the primary content
    if (showEstopGuard) {
        ConfirmGuard(               // the overlay sibling — same Box, NOT a child of Content
            …,
            modifier = Modifier,    // takes up the whole Box by default (fillMaxSize via ConfirmGuard)
        )
    }
}
```

**ConfirmGuard opaque overlay** (ConfirmGuard.kt lines 91–92):
```kotlin
Box(modifier.fillMaxSize().background(t.bg).background(tint)) {
    ScreenScaffold(…)
}
```

**FloatingEStop API shape** (overlay button, NOT a full-screen guard):
```kotlin
@Composable
fun FloatingEStop(
    visible: Boolean,
    onClick: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    // Caller wraps the Focus content in a Box(Modifier.fillMaxSize()).
    // FloatingEStop is a sibling (via Box overlay), aligned TopStart.
    OutlinedControl(
        label = "",
        onClick = onClick,
        modifier = modifier
            .size(uDp * 0.7f)           // ~70% of U — icon tile sizing rule
            .align(Alignment.TopStart)  // caller provides the Box; this aligns within it
            .padding(14.dp),
        intent = Intent.Danger,
        symbol = "emergency_stop",       // PLACEHOLDER — wait for owner glyph assignment
    )
}
```

**Positioning rule** — the caller wraps the Focus content in `Box(Modifier.fillMaxSize())` and places `FloatingEStop` as a sibling with `Modifier.align(Alignment.TopStart).padding(14.dp)`. This is identical to how `ConfirmGuard` is placed inside `PrintStatusScreen`'s outer Box — a sibling, never a child of the Focus content. Placing it inside the Focus Column would shift layout geometry.

**Scope in Phase 23:** Build and register; wire only to `SpoolScreen` pilot (visible when `isPrinting = true`). Full screen integration is Phase 24.

---

### `designsystem/components/SortFilterControlRow.kt` (component, request-response)

**Analog:** `SpoolPicker.kt` — `SpoolFilterControls` (lines 134–203), `CategoryButton` (lines 193–203)

**Sort row pattern with direction indicator** (SpoolPicker.kt lines 146–167):
```kotlin
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    SpoolSortKey.entries.forEach { key ->
        val active = key == state.sortKey
        Box(Modifier.weight(1f)) {
            OutlinedControl(
                label = "",
                symbol = key.icon,
                onClick = { onSelectSort(key) },
                modifier = Modifier.fillMaxWidth(),
                intent = if (active) Intent.Accent else Intent.Neutral,
            )
            if (active) {
                MaterialSymbol(
                    name = if (state.sortAscending) "arrow_upward" else "arrow_downward",
                    tint = t.accent2,
                    sizeSp = fsSp(16f, t.fs),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }
    }
}
```

**Filter row with category buttons** (SpoolPicker.kt lines 169–203):
```kotlin
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    CategoryButton(
        category = SpoolFilterCategory.TYPE,
        active = state.filters.materialFamilies.isNotEmpty(),
        onClick = { onOpenFilter(SpoolFilterCategory.TYPE) },
        modifier = Modifier.weight(1f),
    )
    // …
}

private fun CategoryButton(category: SpoolFilterCategory, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    OutlinedControl(
        label = "",
        symbol = category.icon,
        onClick = onClick,
        modifier = modifier,
        intent = if (active) Intent.Accent else Intent.Neutral,
    )
}
```

**SortFilterControlRow generalized API shape:**
```kotlin
// In SortFilterControlRow.kt:

data class SortOption<K>(
    val key: K,
    val icon: DinghyIcon,
    val contentDescriptionRes: Int,
    val directionUp: Boolean? = null,   // null = not active (no arrow)
)

data class FilterOption<K>(
    val key: K,
    val icon: DinghyIcon,
    val contentDescriptionRes: Int,
    val isActive: Boolean,
)

@Composable
fun <K> SortRow(
    options: ImmutableList<SortOption<K>>,
    activeKey: K?,
    onSelect: (K) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val active = opt.key == activeKey
            Box(Modifier.weight(1f).heightIn(min = uDp - 12.dp)) {
                OutlinedControl(
                    label = "",
                    symbol = (opt.icon.primary as? IconRef.Ligature)?.name ?: "",
                    onClick = { onSelect(opt.key) },
                    modifier = Modifier.fillMaxWidth(),
                    intent = if (active) Intent.Accent else Intent.Neutral,
                )
                // Direction indicator when active (arrow_upward / arrow_downward)
                val direction = opt.directionUp
                if (active && direction != null) {
                    MaterialSymbol(
                        name = if (direction) "arrow_upward" else "arrow_downward",
                        tint = t.accent2,
                        sizeSp = fsSp(16f, t.fs),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun <K> FilterRow(
    options: ImmutableList<FilterOption<K>>,
    onSelect: (K) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            OutlinedControl(
                label = "",
                symbol = (opt.icon.primary as? IconRef.Ligature)?.name ?: "",
                onClick = { onSelect(opt.key) },
                modifier = Modifier.weight(1f).heightIn(min = uDp - 12.dp),
                intent = if (opt.isActive) Intent.Accent else Intent.Neutral,
            )
        }
    }
}
```

**Tile sizing rule:** height = `uDp - 12.dp` (one unit minus the inter-row gap). Minimum tap target = `heightIn(min = 48.dp)` as fallback floor — U on the test device is ~49dp which satisfies naturally; the floor protects small-U edge cases.

---

### `ui/spool/SpoolScreen.kt` (REBUILD on kit, CRUD)

**Analog:** Self (rebuild) — the existing `SpoolScreen.kt` + `SpoolPicker.kt` are the direct source; the rebuild is a structural refactor, not a from-scratch rewrite.

**State-hoist / stateless seam pattern** (SpoolScreen.kt lines 178–214):
```kotlin
// Stateless preview seam — preserved in the rebuild
@Composable
fun SpoolScreen(
    state: SpoolPickerState,   // add fieldMode: FieldMode to SpoolPickerState
    modifier: Modifier = Modifier,
    onSelectSort: (SpoolSortKey) -> Unit = {},
    onToggleFilterCategory: (SpoolFilterCategory) -> Unit = {},
    onRowClick: (SpoolmanSpool) -> Unit = {},
    onLoad: () -> Unit = {},       // renamed from onSetActive (UX vocabulary alignment)
    onUnload: () -> Unit = {},     // renamed from onClearActive
    onScan: () -> Unit = {},
    onHome: () -> Unit = {},
    isPrinting: Boolean = false,   // drives FloatingEStop visibility
) { … }
```

**ScreenScaffold structure for the rebuild:**
```kotlin
ScreenScaffold(
    focus = {
        // Focus still = DetailCard + SortFilterControlRow at bottom (structure unchanged)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            DetailCard(ringColor = resolvedSpoolColor, modifier = Modifier.fillMaxSize()) {
                SpoolDetailContent(spool = selected, …)
            }
            FloatingEStop(
                visible = isPrinting,
                onClick = onEmergencyStop,
                uDp = grid.uDp,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            )
        }
        SortRow(options = sortOptions, activeKey = state.sortKey, onSelect = onSelectSort, uDp = grid.uDp,
                modifier = Modifier.padding(horizontal = 8.dp))
        FilterRow(options = filterOptions, onSelect = onToggleFilterCategory, uDp = grid.uDp,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
    },
    field = {
        // Field-takeover: show spool list OR filter picker based on fieldMode
        when (state.fieldMode) {
            is FieldMode.Spools -> {
                ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    items(state.spools, key = { it.id }) { spool ->
                        ListRow(selected = spool.id == state.selected?.id, onClick = { onRowClick(spool) },
                                uDp = grid.uDp) { SpoolRowContent(spool, t) }
                    }
                }
                FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(8.dp)) {
                    OutlinedControl(label = "", symbol = "home", onClick = onHome,
                                   modifier = Modifier.weight(1f), intent = Intent.Accent)
                    OutlinedControl(label = "", symbol = "qr_code", onClick = onScan,
                                   modifier = Modifier.weight(1f), intent = Intent.Accent)
                    // Conditional Load/Unload (RESEARCH §"Foot button logic")
                    if (isSelectedSpoolLoaded) {
                        OutlinedControl(label = "", symbol = "expand_circle_down", onClick = onUnload,
                                       modifier = Modifier.weight(1f), intent = Intent.Neutral)
                    } else {
                        OutlinedControl(label = "", symbol = "expand_circle_up", onClick = onLoad,
                                       modifier = Modifier.weight(1f), intent = Intent.Accent)
                    }
                }
            }
            is FieldMode.FilterPicker -> {
                ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    items(currentFilterOptions, key = { it.key.toString() }) { opt ->
                        ListRow(selected = opt.isActive, onClick = { onToggleOption(opt.key) },
                                uDp = grid.uDp) { FilterOptionRowContent(opt, t) }
                    }
                }
                FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(8.dp)) {
                    OutlinedControl(label = "", symbol = "close", onClick = onClearFilter,
                                   modifier = Modifier.weight(1f), intent = Intent.Danger)
                    OutlinedControl(label = "", symbol = "check", onClick = onDoneFilter,
                                   modifier = Modifier.weight(1f), intent = Intent.Go)
                }
            }
        }
    },
    gutter = null,     // redesigned screens null out the gutter — FootButtonBar is in field
)
```

**Key changes vs current `SpoolScreen`:**
- `gutter = null` — actions move to `FootButtonBar` inside `field`
- `SpoolFilterPickerOverlay` removed — replaced by `FieldMode.FilterPicker` in-place
- `DetailCard` wraps `SpoolDetailFocus` content
- `FloatingEStop` added as Box overlay sibling inside Focus
- `FillMeter` added inside `DetailCard` for remaining weight fraction
- Callbacks renamed: `onSetActive` → `onLoad`, `onClearActive` → `onUnload`

---

### `designsystem/icons/DinghyIcons.kt` (ADD 6 entries)

**Analog:** Self — the existing registry `DinghyIcons.kt` (lines 23–154). The pattern for new entries is established by every prior phase.

**Registration pattern** (DinghyIcons.kt lines 26–60 — each new entry follows this form):
```kotlin
// In the DinghyIcons object body, add new val entries:
val Sort = DinghyIcon(IconRef.Ligature("sort"), alternate = "sort")
val FilterList = DinghyIcon(IconRef.Ligature("filter_list"), alternate = "filter_list")
val ExpandCircleUp = DinghyIcon(IconRef.Ligature("expand_circle_up"), alternate = "expand_circle_up")
val ExpandCircleDown = DinghyIcon(IconRef.Ligature("expand_circle_down"), alternate = "expand_circle_down")
val ResetWrench = DinghyIcon(IconRef.Ligature("reset_wrench"), alternate = "reset_wrench")
val ResetSettings = DinghyIcon(IconRef.Ligature("reset_settings"), alternate = "reset_settings")
```

**`all` list addition** (DinghyIcons.kt lines 141–153 — add all 6 to the hand-rolled list):
```kotlin
val all: List<DinghyIcon> = listOf(
    // … existing entries …
    Sort, FilterList, ExpandCircleUp, ExpandCircleDown, ResetWrench, ResetSettings,
)
```

**Drift guard:** After registration, `DinghyIconsTest` (uniqueness + resolvable-source) and `tools/verify_ligatures.py` (all 6 new ligatures must resolve in the bundled v2.944 font) must be run as the Wave-0 gate. Both gates are already wired — no new test infrastructure needed.

**`play_circle` / `stop_circle` cleanup:** Update their `notes` fields in `img/material-icon-bucket.json` to "available for reassignment — load/unload moved to expand_circle_up/down". Do NOT add them to `DinghyIcons.kt`.

---

### `docs/ui_design/reference/hifi.css` (`.ctl.warn` fix)

**Bug location:** `hifi.css` line 225:
```css
/* CURRENT — BUGGY */
.ctl.warn { border-color: color-mix(in oklch, var(--heat) 62%, var(--outline)); … }
```

**Why it's broken:** `color-mix(in oklch, amber hue ~66, blue-gray hue ~255)` takes the short hue path through hue ~0 (red). Amber caution reads orange-red.

**The fix** (CSS-only; `OutlinedControl.kt` already uses `t.heat` directly at line 61):
```css
/* FIXED */
.ctl.warn {
  border-color: var(--heat);
  color: var(--heat);
  --ctl-glow: var(--heat-glow);
}
.ctl.warn:hover { background: var(--heat-soft); }
```

**Kotlin analog confirms no code change needed** (OutlinedControl.kt lines 57–64):
```kotlin
private fun Intent.outlineColor(t: ThemeTokens): Color = when (this) {
    Intent.Neutral -> t.outline
    Intent.Accent  -> t.accentLine
    Intent.Warn    -> t.heat        // already uses t.heat directly — no mix
    Intent.Danger  -> t.stop
    Intent.Go      -> t.go
}
```

---

## Shared Patterns

### Token access — `val t = LocalTokens.current`
**Source:** Every existing composable — `OutlinedControl.kt` line 95, `SpoolScreen.kt` line 309, `ScrubberPage.kt` line 151
**Apply to:** ALL new kit composables
```kotlin
val t = LocalTokens.current
// Then: t.surface, t.outline, t.accentLine, t.accentSoft, t.accent2, t.heat,
//       t.stop, t.stopSoft, t.go, t.text, t.text2, t.text3, t.hair,
//       t.rCard, t.rCtrl, t.fs
// NEVER a raw Color(0xFF...) for chrome — always a token field.
```

### Text sizing — `fsSp(baseSp, t.fs)`
**Source:** `SpoolScreen.kt` lines 339–341; `OutlinedControl.kt` lines 119–126; `SpoolPicker.kt` lines 109, 419, 476
**Apply to:** EVERY `fontSize = …` in new kit files — no bare `.sp`
```kotlin
// Scale floors (from existing screens, do not go below):
val headerSp  = fsSp(26f, t.fs)   // detail card title / hero
val bodySp    = fsSp(18f, t.fs)   // primary body / row primary label
val metaSp    = fsSp(15f, t.fs)   // metadata / secondary — the FLOOR
val statSp    = fsSp(26f, t.fs)   // tabular stat values (GeistMono)
val focusSp   = fsSp(30f, t.fs)   // focus/hero numeric values

Text(…, fontSize = bodySp.sp)     // note the .sp suffix — fsSp returns Float
```

### Corner radii — `t.rCard` / `t.rCtrl`
**Source:** `SpoolScreen.kt` line 310, `SpoolPicker.kt` line 457, `OutlinedControl.kt` line 96
**Apply to:** All shapes in new components
```kotlin
val shape = RoundedCornerShape(t.rCard)   // 22dp — cards (DetailCard)
val shape = RoundedCornerShape(t.rCtrl)  // 16dp — controls (ListRow, tiles, SortFilterRow)
val shape = RoundedCornerShape(999.dp)   // pill — FillMeter track
```

### Preview matrix pattern
**Source:** `SpoolPreviews.kt` (entire file) + `PreviewTheming.kt`
**Apply to:** Every new `.kt` file that contains a composable
```kotlin
// The ONLY way to preview large text — NOT @Preview(fontScale = ...)
@Nexus7Previews
@Composable
private fun MyComponentFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { MyComponent(…) }

// The 6-theme matrix:
@Nexus7Previews @Composable private fun MyComponentThemeColorfulDark()  = PreviewBox(colorfulDark)  { MyComponent(…) }
@Nexus7Previews @Composable private fun MyComponentThemeColorfulLight() = PreviewBox(colorfulLight) { MyComponent(…) }
// … and so on for all 6 seeds from PreviewTheming.kt

// RTL spot check:
@Nexus7Previews @Composable private fun MyComponentRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MyComponent(…)
        }
    }
}

// Pseudolocale (standalone — NOT part of @Nexus7Previews):
@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)
@Composable private fun MyComponentPseudolocaleSpotCheck() =
    PreviewBox(colorfulDark) { MyComponent(…) }
```

### Geist + GeistMono type pairing
**Source:** `SpoolPicker.kt` lines 524–554; `SpoolScreen.kt` lines 339–428
**Apply to:** All text in new components
```kotlin
// UI / labels — always Geist:
Text(text = label, fontFamily = Geist, fontWeight = FontWeight.SemiBold, …)
Text(text = meta,  fontFamily = Geist, fontWeight = FontWeight.Medium, …)

// Live numeric data — always GeistMono (tabular numerals avoid jitter):
Text(text = "${value} g", fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, …)
```

### `OutlinedControl` with `Intent`
**Source:** `OutlinedControl.kt` (entire file); `SpoolScreen.kt` lines 264–293
**Apply to:** `FootButtonBar` content, `FloatingEStop`, sort/filter tiles
```kotlin
// Load (physical command, expected action) → Accent
OutlinedControl(label = "", symbol = "expand_circle_up", onClick = onLoad,
                modifier = Modifier.weight(1f), intent = Intent.Accent)
// Unload (neutral — no hazard) → Neutral
OutlinedControl(label = "", symbol = "expand_circle_down", onClick = onUnload,
                modifier = Modifier.weight(1f), intent = Intent.Neutral)
// E-stop → Danger
OutlinedControl(…, intent = Intent.Danger)
// Active sort/filter → Accent; inactive → Neutral
intent = if (active) Intent.Accent else Intent.Neutral
```

### `DinghyIconView` for icon rendering
**Source:** `SpoolScreen.kt` lines 379, 381, 392, 402, 409; `DinghyIcons.kt`
**Apply to:** All icon rendering in new components — NEVER raw `MaterialSymbol` for registered icons
```kotlin
DinghyIconView(
    icon = DinghyIcons.Sort,
    tint = t.text2,
    sizeDp = iconSp.dp,
    contentDescription = stringResource(R.string.cd_sort_row),
)
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `docs/ui_design/COMPONENTS.md` (NEW) | documentation | — | No component-class catalog exists yet; the content derives from `.planning/notes/2026-06-09-component-classes-catalog.md` and the RESEARCH.md §"Component catalog table" — use those directly as the content source |

---

## Critical Anti-Patterns (verified from prior phases)

### Anti-pattern 1: `FootButtonBar` in the `gutter` slot
**Symptom:** Foot buttons appear detached from list; gap in portrait.
**Fix:** `gutter = null` in `ScreenScaffold`; `FootButtonBar` is the LAST child inside the `field` lambda Column.
**Evidence:** RESEARCH.md §Pitfall 1.

### Anti-pattern 2: `LazyColumn.items` without `key`
**Symptom:** Entire list recomposes on any `SpoolPickerState` change (visible jank on Adreno 320).
**Fix:** `items(list, key = { it.id }) { … }` — shown in `SpoolPicker.kt` line 113.
**Evidence:** RESEARCH.md §Pitfall 2; SpoolPicker.kt line 113.

### Anti-pattern 3: `U` derived from portrait `maxHeight` (not `minOf`)
**Symptom:** `uDp` differs between portrait and landscape, making rows physically different sizes.
**Fix:** `val landscapeDim = minOf(maxWidth, maxHeight)` inside `BoxWithConstraints`.
**Evidence:** RESEARCH.md §Pitfall 4; ScreenScaffold.kt lines 64–65 (orientation pattern).

### Anti-pattern 4: `@Preview(fontScale = 1.3f)` for large-text preview
**Symptom:** `*FsLargeOverflow` preview looks identical to normal preview.
**Fix:** `PreviewBox(fsLargeSeed)` — the only working path.
**Evidence:** `PreviewTheming.kt` lines 36–42, `PREVIEW_AND_TOKENS.md` §4.

### Anti-pattern 5: Rebuilding the scrubber fill bar from scratch
**Symptom:** P19 regression — fill grows from center, value doesn't stick on drag (stale `pointerInput` closure).
**Fix:** Reuse `ScrubberPage.kt` as-is (or extract the bar + pointer-input pattern from lines 219–236).
**Evidence:** RESEARCH.md §Pitfall 5; ScrubberPage.kt lines 219–236 (the `awaitEachGesture` fix).

### Anti-pattern 6: Raw color literals for chrome
**Symptom:** Theme breaks in light mode; custom themes ignored.
**Fix:** Always `val t = LocalTokens.current`, then `t.surface`, `t.outline`, etc. Only raw `Color` for data carve-outs (`ringColor`, `fillColor` = filament hex).
**Evidence:** `OutlinedControl.kt` lines 57–64; `SpoolScreen.kt` lines 312–314.

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/designsystem/`, `app/src/main/java/works/mees/dinghy/ui/spool/`, `app/src/main/java/works/mees/dinghy/ui/printstatus/`, `app/src/main/java/works/mees/dinghy/preview/`
**Files scanned:** 9 source files + 5 design-law docs
**Pattern extraction date:** 2026-06-09
