# Component Classes — the jiib design catalog

> **This is UI LAW (Phase 23).** The component-class catalog sits beside `THEMING.md` and the
> rewritten `LAYOUT.md`. Every redesigned screen in Phases 23–29 references this document to
> name and style its elements. Where this document and the implemented Kotlin code ever diverge,
> file a correction — the catalog is the spec.

---

## 1. Philosophy

Android has no CSS, but Compose supports exactly what is wanted: a **named, documented catalog of
component styles**. Each class is a spec for fill, background token, border token, corner radius,
padding, and interactive state — the **same token philosophy as the existing color system**,
extended from color to component archetype.

The point is **shared vocabulary**: when a screen description says "that's a `DetailCard`", the
developer applies the class and the styling debate is closed. The classes are expressed as:

- **Wrapper composables** — `ListRow { }`, `DetailCard { }`, `FootButtonBar { }` — that accept
  content lambdas and own the background/border/shape.
- **Named modifiers** — `Modifier.cardSurface(t)` — for call sites that need card appearance on
  a custom container.

**Guiding rules:**

- All specs reference **semantic tokens** (`ThemeTokens` fields — see `THEMING.md`). Never a raw
  `Color(0xFF…)`.
- Classes are **extracted from real screens**, not designed in the abstract. The Spoolman screen
  (the Phase-23 pilot) exercises every class in this catalog; the class boundaries crystallized
  from that rebuilt screen.
- Each class is defined once. A conformance sweep that finds an inline implementation of a
  class's appearance is a migration target, not an intentional alternative.

**Canonical term:** "component classes" — used throughout this document and in design conversations.

---

## 2. Fill convention — content vs controls

A load-bearing visual rule. Two categories of surface, visually distinct at a glance:

| Category | Background | Border | What goes here |
|---|---|---|---|
| **Content** | `Color.Transparent` | `t.outline` (1.5dp) | Scrollable list items (`ListRow`) |
| **Content — selected / active** | `t.accentSoft` | `t.accentLine` (2dp) | A `ListRow` in its selected state |
| **Controls** | `t.surface` shade | `t.outline` or intent-line if active | Buttons, tiles, action bars, detail cards |
| **Recessed context markers** | `t.bg2` | — | Non-interactive type-icon tiles (`SortFilterControlRow`) |
| **Danger controls** | `t.stopSoft` / transparent | `t.stop` | `FloatingEStop` |

Selection and active states layer accent tint on top of the base category — they do not change
the fundamental content-vs-control classification.

**No group-label words.** Grouping is communicated by outline + fill shade + accent + leading
type-icon alone. A text header above a group of controls is non-conformant in the jiib grammar.
See `SortFilterControlRow` in §3 for the canonical compound-control example of label-free grouping.

Full fill-convention documentation also lives in `LAYOUT.md §"Content vs controls — fill
convention"` — these two sections are intentionally in sync, with `LAYOUT.md` as the spatial
context and this document as the class-definition context.

---

## 3. Component catalog

One row per class. Implementations live in `app/src/main/java/works/mees/dinghy/designsystem/`.

| Class | Fill type | Background | Border | Primary content | Implementation |
|---|---|---|---|---|---|
| `ListRow` | Transparent (content) | `Color.Transparent` / `t.accentSoft` if selected | `t.outline` (1.5dp) / `t.accentLine` (2dp) if selected | Scrollable item row | `designsystem/components/ListRow.kt` |
| `DetailCard` | Filled (control surface) | `t.surface` | `ringColor` (data) / `t.accentLine` / `t.hair` fallback chain | Selected item detail pane | `designsystem/components/DetailCard.kt` |
| `FillMeter` | Filled fill layer | `t.surface3` (track) + data fill | — | Read-only fraction bar (weight remaining, progress) | `designsystem/components/FillMeter.kt` |
| `FootButtonBar` | — (container only) | — | — | Row of `OutlinedControl` buttons pinned to foot of list | `designsystem/components/FootButtonBar.kt` |
| `FloatingEStop` | Filled (danger) | `t.stopSoft` / transparent | `t.stop` | Print-cancel / e-stop overlay, printing only | `designsystem/components/FloatingEStop.kt` |
| `SortFilterControlRow` | Filled (control) | type-tile: `t.bg2`; option tile: `t.surface`; active: `t.accentSoft` | `t.outline` / `t.accentLine` if active | Sort/filter surface for a list | `designsystem/components/SortFilterControlRow.kt` |
| Control tile (general) | Filled | `t.surface` | `t.outline` / intent-line if active | Interactive grid tile (launcher, shortcut, jog pad cell) | `OutlinedControl.kt` (existing) |
| `ListBlock` | — (scroll wrapper) | — | — | Edge-faded `LazyColumn` container | `designsystem/layout/ListBlock.kt` |

### Class details

#### `ListRow`

A single row in a scrollable collection. Height = 1U (see §4). Touch target enforced via
`Modifier.heightIn(min = uDp)`. Transparent background marks it as content (browsable); the
selected state layers `accentSoft` fill to indicate it is the active subject.

Mandatory usage: list items in `ListBlock` that represent objects the user browses or selects
(spools, files, macro list, action list). Not for button rows — those are `FootButtonBar` or
control tiles.

Slots:
- `leadingContent` — type icon, color swatch, glyph (optional)
- `content` — main label(s) — fills available width
- `trailingContent` — weight, value, trailing symbol (optional)

#### `DetailCard`

The selected-item detail pane. Filled background (`t.surface`), corner radius `t.rCard` (22dp),
color-reactive border. Contains arbitrary content.

**THEME-01 data carve-out:** `DetailCard.ringColor` is the **item's actual data color** (e.g.
Spoolman filament hex), not a theme role. It bypasses the token system and is never clamped by
`brandTint`'s WCAG floor — the ring should reflect the true filament color, not a UI-adjusted
approximation. When `ringColor` is null, falls back to `t.accentLine`, then `t.hair`. See
`THEMING.md §"Carve-out"` for the established THEME-01 precedent.

#### `FillMeter`

A horizontal "how much remains" bar — thin track, filled to a fraction. Shows value + total as
Geist Mono text. Height ≈ 6dp track with rounded caps. Read-only (no gesture).

**THEME-01 data carve-out:** `FillMeter.fillColor` is the **item's data color** (same carve-out
as `DetailCard.ringColor`). Pass the actual filament color; it is never clamped by `brandTint`.
The track background is `t.surface3` (a neutral sunken well).

Parameters: `fraction: Float` (clamped internally to 0..1), `fillColor: Color`,
`label: String` (e.g. `"735 / 1000 g · 74%"`, Geist Mono).

#### `FootButtonBar`

A `Row` of `OutlinedControl` buttons, height = 1U, full-width of the Field column. The
generalized replacement for the old gutter on list-primary screens.

**Structural placement:** Inside the `field` slot lambda of `ScreenScaffold`, as the LAST
column element — NOT in the `gutter` slot. Pass `gutter = null` on redesigned screens.

Button distribution: each `OutlinedControl` uses `Modifier.weight(1f)` for equal width. Weighted
buttons (one wider primary action) are allowed if they stay on the grid (integer-ish fractions).

Intent follows the standard button-intent-color rule (see `THEMING.md §"Button intent = color"`):
- Load spool = `Intent.Accent` (ordinary physical command)
- Unload spool = `Intent.Neutral` (no hazard)
- Home = `Intent.Accent`

#### `FloatingEStop`

A floating red emergency-stop button. Overlaid top-left of the Focus region, visible only when
the printer is actively printing. Decoupled from layout flow — does NOT shift Focus geometry.

Size = 1U × 1U (square). Position = `Modifier.align(Alignment.TopStart).padding(14.dp)` inside
a `Box` that wraps Focus content as a sibling.

Important: place Focus main content (progress ring, detail card) with awareness that the very
top-left corner is reserved for the e-stop. Do not overlap it with important readout data.

Phase-23 scope: built and wired to SpoolScreen (printing-state visibility). Full integration to
every screen is Phase 24.

#### `SortFilterControlRow` — LOCKED compound anatomy

**The sort/filter row is a COMPOUND component.** Its anatomy is non-negotiable:

```
[ TYPE tile ]  [ option tile ]  [ option tile ]  …
  (recessed,    (filled, t.surface; active = t.accentSoft)
   non-tap,
   t.bg2)
```

A **leading RECESSED, non-interactive TYPE tile** precedes all option tiles:
- For a sort row: `DinghyIcons.Sort` (`sort` ligature), `t.bg2` background
- For a filter row: `DinghyIcons.FilterList` (`filter_list` ligature), `t.bg2` background

The type tile is **not tappable**. It is a visual context marker, not an option. Its recessed
`bg2` background distinguishes it from the tappable option tiles.

**NO group-label words anywhere on the row.** The type-tile icon + fill shade + accent carry
the grouping information. A bare option-list row without the leading type tile is
**NON-CONFORMANT** — do not implement one.

Active option tile: `t.accentSoft` fill, `t.accentLine` border, `t.accent2` foreground.
Inactive option tile: `t.surface` fill, `t.outline` border.

Tile height = `(uDp - 12.dp)` to preserve the inter-row gap rhythm.

#### Control tile (general)

The existing `OutlinedControl` + `Intent` system covers general control tiles (launcher tiles,
shortcut buttons, jog-pad cells). `COMPONENTS.md` does not redefine those — they follow the
established `OutlinedControl` fill convention (`t.surface`, intent-tinted border when active).

#### `ListBlock`

A thin wrapper: a `LazyColumn` with edge-fade gradient hints (fades appear only when content
exists in that direction, no visible scrollbar). Edge fades use `Box` gradient overlays driven
by `lazyListState.firstVisibleItemIndex > 0` (top) and `lazyListState.canScrollForward` (bottom).

No additional scrollbar indicator — Compose `LazyColumn` has none by default; this is intentional.

---

## 4. The unit `U`

All component heights are expressed in units of `U`. The unit grid derivation lives in
`LAYOUT.md §"The unit U"` — **that is the single source of truth for the formula and
dp-derived reasoning.** This section records the derived usage:

- **`ListRow`** = 1U (height = `uDp`)
- **`FootButtonBar`** = 1U (height = `uDp`)
- **Stepper / group-control tile** = 1–2U (see Phase 26 for restyle)
- **Focus region** = remaining units after Field rows are counted
- **`FloatingEStop`** = 1U × 1U square

`uDp` is derived at screen level via `rememberUnitGrid(minOf(contentWidth, contentHeight))`
and passed explicitly to each component. Promote to `CompositionLocal<UnitGrid>` if call-chain
depth grows past 3 levels.

**Settings/config surfaces** (C6 exempt): the unit grid does NOT apply to Settings-class screens
(Settings, Theme editor) — they use denser rows and ignore the touch-floor minimum.

---

## 5. Interaction patterns

### Field-takeover picker

When a filter tile is tapped, the **Field slot swaps** from the primary list to a picker list
for that category — in place, with no screen push or back-stack change. Reverting is a
"‹ Back" or "Show all" tap that restores the primary list.

- Implemented via a `fieldMode` enum on the screen's `PickerState`:
  `Spools` (or the list's primary mode) vs `FilterPicker(category)`.
- The `field` slot lambda is a `when(state.fieldMode)` that renders either the primary list or
  the filter option list, each with its own `FootButtonBar`.
- Sort does NOT trigger a takeover — it acts immediately in-place (see below).

**Consistent pattern:** The Field-takeover is a reusable picker pattern applicable wherever a
filter/facet/option selection would otherwise push a new screen: file-type filters, macro-param
pickers, etc.

### Sort vs filter — never conflated

| Verb | Behavior | UI indication |
|---|---|---|
| **Sort** | In-place reorder; acts immediately; direction can toggle | Active sort tile shows a direction arrow indicator |
| **Filter** | Opens a Field-takeover option list; option tiles TOGGLE independently (multi-select); **Done** returns to list; **Clear** resets that facet | Active filter tile highlights when ≥1 option is selected in that facet |

Conflating sort and filter (displaying both in the same option-type UI) is non-conformant. Each
has a distinct verb and a distinct visual affordance.

**Filter multi-select semantics (owner-approved, 23-06 checkpoint):**
- Each option tile in a filter Field-takeover **toggles independently** (selected / unselected)
  using the `ListRow` selected state. There is no auto-close on tile tap.
- **OR within a facet** — a spool matches if its value is in the selected set for that facet.
- **AND across facets** — all active facets must match simultaneously (standard faceted filtering).
- **Done** is the explicit return action; tapping it closes the Field-takeover and returns to the
  spool list.
- **Clear** resets the current facet's selections (all tiles deselected) and re-issues the list
  read; does NOT close the picker.
- The active filter tile in `FilterRow` (Focus foot) highlights whenever ≥1 option is selected
  in that facet (`isActive = set.isNotEmpty()`).

Note: the Color facet retains **single-select** behavior (one swatch at a time) because the
color-similarity two-step (`applyColorSwatch`) closes the picker on tap — a multi-swatch
color-OR is not supported by the current Spoolman similarity endpoint.

### Conditional Load / Unload

The spool-screen foot button for the load/unload action is **conditional on whether the selected
spool is currently the loaded one**:

| State | Button | Icon | Intent |
|---|---|---|---|
| Selected spool **IS** the loaded one | **Unload** | `DinghyIcons.ExpandCircleDown` (`expand_circle_down`) | `Intent.Neutral` |
| Selected spool is **NOT** the loaded one | **Load** | `DinghyIcons.ExpandCircleUp` (`expand_circle_up`) | `Intent.Accent` |

The other foot buttons (Home, Scan) are always present. This three-button foot bar is the
canonical `FootButtonBar` usage.

### Icon-registry-only law

All icons on all screens come from `DinghyIcons.kt` or are requested via the owner. **Never
auto-pick a Material Symbol or create a custom drawable independently.** See
`docs/ui_design/CLAUDE.md §"Icons: never the same glyph twice…"` and the never-auto-pick law
that follows it. This applies to SortFilterControlRow type-tiles, DetailCard glyphs, and every
other use.

---

## 6. SortFilterControlRow anatomy (LOCKED)

The sort/filter row compound component is documented fully in §3 above ("SortFilterControlRow —
LOCKED compound anatomy"). Key invariants:

1. **Always a leading type tile** (`Sort` or `FilterList` icon, `t.bg2`, non-interactive).
2. **No group-label text** anywhere on the row.
3. **Option tiles are filled** (`t.surface`), not transparent — they are controls, not content.
4. **A bare option-list row without the type tile is NON-CONFORMANT.**

---

## 7. Stepper and scrubber — post-Phase-26 sizing rules

> **UAT-3 (scrubber ≤ 1U):** While the current fill-bar scrubber style (`ScrubberControl` /
> `LedBrightnessControl`) is in use, the scrubber track + thumb must be constrained to a single U
> of height via `Modifier.heightIn(max = uDp)`. See `LAYOUT.md UAT-3`.
>
> **UAT-5 (controls ≤ 1U):** All Output controls respect the 1U height cap. The LED `ColorWheel`
> is the **sole sanctioned >1U exception**. See `LAYOUT.md UAT-5`.
>
> **UAT-1 (prominent icons ~70-80% U):** `FloatingEStop`'s glyph at `uDp * 0.7f` is the precedent
> for U-relative icon sizing on Focus-anchoring controls. See `LAYOUT.md UAT-1`.
>
> **UAT-4 (e-stop top-left reserve):** `FloatingEStop` occupies `Alignment.TopStart` of the Focus
> Box — leave that corner clear of important content. See `LAYOUT.md UAT-4`.

### Stepper and scrubber — vocabulary

The stepper (increment-picker) and scrubber (ScrubberPage) are **named in the jiib redesign
vocabulary** and ARE part of the component catalog in concept:

- **Stepper:** a step-based adjuster (no scrubber on the perf floor); 3-zone Focus layout; inline
  `was X` baseline readout. See `.claude/skills/sketch-findings-dinghy-display/references/adjustment-controls.md`
  for the locked design.
- **Scrubber (ScrubberPage):** current fill-bar style; track + thumb constrained to ≤ 1U per UAT-3.

Phase 26 executed the adjustment-screen rebuild (Fine-Tune, Temperature, Outputs). The UAT sizing
rules above (UAT-3/UAT-5/UAT-1/UAT-4) are the conformance criteria for these and all future
adjustment screens. The existing `ScrubberPage.kt` and stepper implementations are in active use.

---

## 8. Class-equivalent (Views) exceptions

Not every `ListRow`-styled surface can be implemented as a Compose `ListBlock`/`ListRow`. When the
Phase-25 spike (25-SPIKE.md) demonstrates that a Compose `LazyColumn` produces an unacceptable p90
regression on Adreno 320 under real workload, the surface is retained as a Views RecyclerView and
visually conformed to the kit via adapter-level token routing instead.

These surfaces use identical **visual** styling (token colors, `fsSp` sizes, `GeistMono` font,
spacing) but their implementation is a `ConsoleListView`/`AndroidView`-wrapped RecyclerView, not a
`ListBlock`. They are documented here so they do not count as conformance violations.

| Surface | Screen | Implementation | Spike result | Retained since |
|---------|--------|----------------|--------------|----------------|
| Console scrollback | `ConsoleScreen` | `ConsoleListView` (RecyclerView, `stackFromEnd`, `isSingleAppend`/`isAppendEvict` incremental paths) | 25-01: Compose p90 73.35 ms vs Views 9.26 ms (~8× regression under live churn on Adreno 320) | Phase 25 / 25-04 |

**Rule:** any new surface added to this table requires a measured spike verdict (gfxinfo on flox,
ADR-0001 Addendum-2 gate) justifying the Views retention. A Views surface retained WITHOUT a
spike result is a conformance violation, not an exception.

---

## 9. Scope and evolution

This catalog is **extracted from real screens and refined as screens migrate**. When a new screen
migration in Phases 23–29 requires a class not yet listed here, add it — extract the class from
the real implementation, document it in §3, and keep this document as the master catalog.

Do **not** add speculative classes before they are needed by a real screen.

**Cross-references:**

| Related doc | What it covers |
|---|---|
| `LAYOUT.md` | Spatial grammar (Focus/Field), unit U formula, fill convention in layout context, NON-NEGOTIABLES |
| `THEMING.md` | Every token name, intent colors, button-intent-by-safety rule, C-series conformance criteria |
| `CLAUDE.md` | Design philosophy, icon law (never-auto-pick), token carve-outs |
| `PREVIEW_AND_TOKENS.md` | Preview-matrix convention, `@Preview` matrix shape, `fsSp` usage rules |
