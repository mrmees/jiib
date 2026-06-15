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

The point is **shared vocabulary**: when a screen description says "that's a `FocusFrame`", the
developer applies the class and the styling debate is closed. The classes are expressed as:

- **Wrapper composables** — `ListRow { }`, `FocusFrame { }`, `FootButtonBar { }` — that accept
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
| `FocusFrame` | Filled (Focus surface) | `t.surface` | `FocusEdge`: `t.outline` 1.5dp (Neutral, default) / data color 3dp (Data) / perimeter progress bar (Progress) | THE universal Focus container (every Focus except Webcam) | `designsystem/components/FocusFrame.kt` |
| `FillMeter` | Filled fill layer | `t.surface3` (track) + data fill | — | Read-only fraction bar (weight remaining, progress) | `designsystem/components/FillMeter.kt` |
| `FootButtonBar` | — (container only) | — | — | Row of `OutlinedControl` buttons pinned to foot of list | `designsystem/components/FootButtonBar.kt` |
| `FloatingEStop` | Filled (danger) | `t.stopSoft` / transparent | `t.stop` | Shell-fallback e-stop (Webcam + Theme only — all other screens dock in FocusFrame header) | `designsystem/components/FloatingEStop.kt` |
| `SortFilterControlRow` | Filled (control) | type-tile: `t.bg2`; option tile: `t.surface`; active: `t.accentSoft` | `t.outline` / `t.accentLine` if active | Sort/filter surface for a list | `designsystem/components/SortFilterControlRow.kt` |
| Control tile (general) | Filled | `t.surface` | `t.outline` / intent-line if active | Interactive grid tile (launcher, shortcut, jog pad cell) | `OutlinedControl.kt` (existing) |
| `ListBlock` | — (scroll wrapper) | — | — | Edge-faded `LazyColumn` container | `designsystem/layout/ListBlock.kt` |
| `RegisteredRegion` | — (frame owner) | — | — | 8dp edge-registration frame + inter-element gap owner for a screen region | `designsystem/layout/RegisteredRegion.kt` |

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

**Anatomy is OWNED BY THE PRIMITIVE (pilot ruling, 2026-06-12):** ListRow itself inserts the
standard **12dp gap** after the leading slot and gives the content slot **`weight(1f)`**, so the
trailing slot is ALWAYS end-aligned (UAT-2) — call sites must NOT add their own leading end-padding
or weight. The row's primary label renders via **`ListRowLabel(text)`** (Geist SemiBold, R11 20sp
default, `t.text`) — the ONE place that owns the list-label look; trailing VALUE readouts stay
Geist Mono at the call site (values, not labels).

#### `FocusFrame`

**THE universal Focus container** (Focus Frame law, `.planning/notes/2026-06-12-focus-frame-law-design.md`).
Every screen's Focus region is a `FocusFrame` — **except Webcam**, which stays full-bleed native
media (the one exemption). Renamed from the old `DetailCard`. Filled background (`t.surface` — visually
distinct from the translucent list/Field area), corner radius `t.rCard` (22dp). `FocusFrame` is
authored **flush** — it adds NO frame padding of its own; the enclosing `RegisteredRegion` (the
default in `ScreenScaffold`) owns the 8dp registration frame on all four sides. Callers pass
**sizing only** (`fillMaxSize`/`weight`) and never add frame padding. (`FocusFramePlacement` is
**retired** — the region now uniformly owns the frame for all placements.) It **clips its content to bounds** (no
overflow — graphical content uses `Fit` so it scales rather than clips), and applies the inner
content inset (the `contentInset` param, **default `FocusInset` = 16dp**). A screen whose Focus
content reads better tighter may pass a smaller value — the Calibration Hub passes `FocusInset / 2`
(8dp) to halve the padding around its bottom-docked Open button (2026-06-13 owner UAT). Every other
screen uses the 16dp default.

**Mandatory required params:** `title: String`, `icon: DinghyIcon`, `uDp: Dp`, plus the e-stop
seam: `isPrinting: Boolean`, `onEmergencyStop: () -> Unit`, `onPanic: () -> Unit`. All
compiler-enforced — there is no title-less or icon-less `FocusFrame`.

**Mandatory header (Focus-header law, 2026-06-13):**

Every `FocusFrame` renders a **1U-tall header** as its topmost element, before content. The
header is always present — idle and printing alike. It contains:

- **Start-aligned icon slot** — sized at `(uDp * 0.7f).coerceAtLeast(64.dp)` (UAT-1 prominent
  tier; same formula as the retired floating e-stop). This slot is the **e-stop tap target** while
  printing (below). At **idle** the slot holds the screen's **inert identity glyph** — decorative,
  not tappable — rendered at **`IDENTITY_ICON_RATIO` (0.82×) of the slot, centered** (2026-06-13
  owner UAT). The inert glyph is shrunk because edge-heavy Material Symbols (e.g. `linear_scale`,
  `blur_linear`, `linked_services`) drawn at the full slot clip against the 1U bar / the card's
  rounded corner. The **e-stop button keeps the full slot** — only the decorative glyph shrinks.
- **Centered title** — rendered with `Modifier.basicMarquee()`. The title is ALWAYS single-line;
  it scrolls horizontally on overflow — **NO shrink, NO ellipsis, NO wrap.** This is a
  **sanctioned exception to the no-continuous-animation motion law** (overflow-only, single-line,
  tiny dirty-rect — see `CLAUDE.md §Motion`). Drives the printing job-filename title.

**Idle → printing morph (e-stop dock):**

While **printing**, the icon slot **morphs in place** into the emergency-stop button — red fill,
`Intent.Danger`, `StatusStop` glyph. It is NOT a separate overlay and does NOT shift geometry;
the same 0.7U slot changes role. Tap → a `ConfirmGuard` dialog that `FocusFrame` owns and hosts
in a full-screen `Dialog` (covers the whole screen). Long-press (`onPanic`) → instant halt with
no confirmation guard.

`FocusFrame` owns the `ConfirmGuard` internally — call sites only wire `isPrinting`,
`onEmergencyStop`, and `onPanic`; they do not manage the guard state.

**D9 rule — header carries the most specific thing currently loaded:**

The icon and title shown in the `FocusFrame` header are **the most specific thing the Focus is
currently showing**:

- **Single-item Focus** (one selected subject): header = the selected item's identity — its title
  and its own icon. On Fine-Tune, the header is the selected parameter; on Outputs, the selected
  output; on Temperature (when adjusting one sensor), the selected sensor. This keeps the header in
  sync with selection — a different row → different title+icon in the header. Do NOT duplicate the
  icon + name inside the Focus content body when the header already carries them.
- **Aggregate / overview Focus** (multi-trace, collection view): header = the collection/screen
  identity. The Temperature multi-trace graph is the canonical aggregate case — the header reads
  "Temperature" with the Temperature screen icon, even though individual traces each have their own
  sensor identity, because no single item is selected.
- **Nav-entry identity fallback**: when neither of the above applies (a fixed single-subject screen
  reached via a specific nav entry), the header is the glyph and label of the button the user
  tapped to arrive — the same as the old D9 "nav entry" rule.

**Exceptions to the nav-entry fallback:**
- **PrintStatus** needs a bespoke glyph (`DinghyIcons.PrintStatusStandby` at idle) because it is
  reached from a morphing home tile that has no single fixed icon.
- **Hub / list-detail Focus pages** drive the header from the **currently SELECTED item**, not a
  fixed nav-entry glyph — the header is part of the selection feedback (selecting a different list
  row re-titles + re-icons the Focus). The Calibration Hub is the precedent: its header shows the
  selected routine's title + `routineIconToken`, falling back to the launcher identity only on the
  defensive null-selection frame. A hub's header law is selection-driven by design (2026-06-13).

**Trailing-action slot (optional, end-aligned):**

`FocusFrame` exposes an optional trailing-action slot at the end of the 1U header, mirroring the
start identity icon slot in position. Parameters: `trailingActionIcon: DinghyIcon?`,
`onTrailingAction: (() -> Unit)?`, `trailingActionContentDescription: String?`. When populated:

- **Bare glyph — no outline, no fill.** The slot renders a plain, tappable glyph with **no
  `OutlinedControl` wrapper** — it is not a button tile.
- **Neutral `text2` tint.** Not intent-colored. The header accent stripe is already the
  identity/e-stop — a second intent color in the same bar competes with it.
- **Size = `slot * IDENTITY_ICON_RATIO`** (~0.82× of the 0.7U identity slot), matching the inert
  identity glyph size. The trailing glyph is a peer, not a hero.
- **SAFE actions only.** The trailing slot is reserved for actions that cannot lose unrecoverable
  state — reverting to a default is the canonical case. Caution or destructive actions belong in the
  foot bar under the four-class intent scheme, not in the header.

**First consumer:** revert-to-default (`DinghyIcons.Revert` / `refresh` glyph), shown only when
the current value deviates from the item's default/baseline. It spends no intent color because
resetting to a known-good default is always safe.

**Shell-fallback destinations (NOT rendered in a FocusFrame header):**

Two destinations are exempt from the mandatory header because they do not render a `FocusFrame`:
- **Webcam** — full-bleed native media; the deliberate header exemption.
- **Theme** — settings-class screen outside the migration scope.

For these, `AppShell` retains the `FloatingEStop` as the **shell-level fallback** e-stop during
printing. **Splash** has no print state and needs no e-stop mechanism. All other 21 destinations
carry their own docked e-stop in the `FocusFrame` header.

**The edge encodes meaning — accent is RESERVED (`FocusEdge`):**
- **`FocusEdge.Neutral`** (default) — `t.outline` at list-row weight (1.5dp). The resting "this is the Focus" signal.
- **`FocusEdge.Data(color)`** — the edge tinted by the item's literal data color at 3dp (heavier so the color reads). THEME-01 carve-out: pass the actual hex (e.g. Spoolman filament color); never `brandTint`-clamped. Printers also uses this for connection-state color.
- **`FocusEdge.Progress(fraction)`** — the edge becomes a **perimeter progress bar** in the Scrubber's visual language (`t.surface3` track + `t.accent` fill + the 34dp ringed-thumb marker), for the actively-printing screen. Accent appears here and only here. *(Deferred to a future session — not yet implemented.)*

See `THEMING.md §"Carve-out"` for the THEME-01 precedent behind `FocusEdge.Data`.

#### `FillMeter`

A horizontal "how much remains" bar — thin track, filled to a fraction. Shows value + total as
Geist Mono text. Height ≈ 6dp track with rounded caps. Read-only (no gesture).

**THEME-01 data carve-out:** `FillMeter.fillColor` is the **item's data color** (same carve-out
as `FocusFrame.ringColor`). Pass the actual filament color; it is never clamped by `brandTint`.
The track background is `t.surface3` (a neutral sunken well).

Parameters: `fraction: Float` (clamped internally to 0..1), `fillColor: Color`,
`label: String` (e.g. `"735 / 1000 g · 74%"`, Geist Mono).

#### `FootButtonBar`

A `Row` of `OutlinedControl` buttons, height = 1U, full-width of the Field column. The
generalized replacement for the old gutter on list-primary screens.

**Structural placement:** Inside the `field` slot lambda of `ScreenScaffold`, as the LAST
column element. (The `ScreenScaffold` gutter slot was deleted 2026-06-12 — R1 migration.)

Button distribution: each `OutlinedControl` uses `Modifier.weight(1f)` for equal width. Weighted
buttons (one wider primary action) are allowed if they stay on the grid (integer-ish fractions).

**Back is always the FIRST (start-aligned) button** (R8, 2026-06-12), app-wide. The bar is
**optional per page** (R1) — it is the old gutter's successor, not a required element.

**`FootButtonBar` is authored flush** — it adds NO frame padding of its own. The enclosing
`RegisteredRegion` owns the 8dp edge frame, which automatically aligns a stacked `FootButtonBar`
with its sibling `ListBlock` (both are flush direct children of the same region). Consequences
for call sites:
- **Never** pass `start`/`end`/`horizontal` padding to a `ListBlock` or `FootButtonBar` — the
  region owns it. Callers pass only `weight`/vertical (`top`/`vertical`).
- **Never** wrap a stacked list+bar in a Column that adds `horizontal` padding — that
  double-insets the bar. Frame the Column vertically only; let the region own the horizontal
  frame.
- To change the frame width app-wide, edit `ListFrameInset` (in `designsystem/layout/ListBlock.kt`,
  aliased to `RegionInset` in `RegisteredRegion.kt`) in ONE place.

Intent follows the four-class scheme (R5 — see `THEMING.md §"Button intent = color"`):
- Load / Unload spool = `Intent.Go` (the expected action of the current selection state)
- Back / Home (navigation) = `Intent.Accent` (neutral nav)
- Anything that could wreck a print mid-process = `Intent.Caution`; destructive = `Intent.Stop`

#### `FloatingEStop`

**⚠ RETIRED as the general e-stop pattern (Focus-header law, 2026-06-13).** The e-stop now
docks inside the `FocusFrame` mandatory header (see `FocusFrame` §above). `FloatingEStop`
survives **only as the shell-level fallback** for the two destinations that do NOT render a
`FocusFrame`: **Webcam** (full-bleed media) and **Theme** (settings-class, outside migration
scope). `AppShell` renders it for those two cases only.

A floating red emergency-stop button. Overlaid top-left of the Focus region, visible only when
the printer is actively printing. Decoupled from layout flow — does NOT shift Focus geometry.

Size = `(uDp * 0.7f).coerceAtLeast(64.dp)` (0.7U, the prominent-icon tier). Position =
`Modifier.align(Alignment.TopStart).padding(14.dp)` inside a `Box` that wraps Focus content as
a sibling.

Piloted on SpoolScreen (Phase 23), integrated app-wide (Phase 24); now superseded by the docked
header morph on all `FocusFrame` screens.

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

`ListBlock` is authored **flush** — it adds NO frame padding of its own. The enclosing
`RegisteredRegion` owns the 8dp edge frame; `ListBlock`'s outer edges are therefore flush with
a stacked `FootButtonBar` by construction (both are direct children of the same region and both
sit inside the same `RegisteredRegion` inset). Callers pass only `weight` + vertical padding
(`top`/`vertical`), never `start`/`end`/`horizontal`. Embedded uses of `ListBlock` inside a
`FocusFrame` body (not a region child) must add their own inset explicitly.

#### `RegisteredRegion`

**RegisteredRegion** — the single owner of a screen region's 8dp edge-registration frame
(R26) + inter-element gap. `ScreenScaffold` wraps each slot in it by default (per-region
`focusFramed`/`fieldFramed` opt-out); Console and self-contained field helpers (PrintStatus)
call it directly. `FocusFrame`, `ListBlock`, `FootButtonBar`, `SortRow`/`FilterRow` are
authored FLUSH — they never add their own frame padding; the region owns it. Embedded
(non-region-child) uses — e.g. a `ListBlock` inside a `FocusFrame` body — add their own
inset explicitly.

#### `DinghyType`

The named text-role catalog — the **type analog of the other component classes**. A call site
says WHAT a piece of text is (a list label, a live value, a console line); the role owns its
`(family, size, weight)`. The 11 roles:

| Role | Family | Base sp | Use |
|---|---|---|---|
| `screenTitle` | Geist | 22 | screen / section titles |
| `focusHeader` | Geist | 20 | the 1U `FocusFrame` header title |
| `listLabel` | Geist | 20 | `ListRow` labels |
| `buttonLabel` | Geist | 20 | `FootButtonBar` / control-tile labels |
| `body` | Geist | 20 | prose, descriptions, captions over the floor |
| `caption` | Geist | 15 | metadata floor — timestamps, fine print |
| `focusHero` | Geist Mono | 40 → 15 (shrink-to-fit) | the Focus region's primary value, read across the room |
| `statValue` | Geist Mono | 26 | tabular live-value readouts |
| `dataInline` | Geist Mono | 20 | inline printer values (filenames, sensor names) |
| `dataMeta` | Geist Mono | 15 | small printer-value metadata |
| `consoleLine` | Geist Mono | 15 | console scrollback lines |

The family rule is one question: **"did this value come from the printer?"** Yes → Geist Mono
(`TypeRole.Data` — filenames, sensor readings, console). No → Geist (`TypeRole.Ui` — everything
else). Sizes are base sp scaled by `fsSp(baseSp, fs)` for the S/M/L `--fs` setting; `focusHero`
is the only shrink-to-fit role (`maxSp 40` / `minSp 15`). Call sites apply a role via
`role.toTextStyle(t)` (Compose) or `FocusHeroText` (the hero); the four classic-Views surfaces
derive their typeface + base size from the same roles via `TextRole.typeface(context)`. Inline
`fontFamily` / `fontSize` are **forbidden** in `app/src/main` and enforced by
`FontConformanceTest`. See THEMING.md §"The type ramp" for the ramp tiers these roles name.

---

## 4. The unit `U`

All component heights are expressed in units of `U`. The unit grid derivation lives in
`LAYOUT.md §"The unit U"` — **that is the single source of truth for the formula and
dp-derived reasoning.** This section records the derived usage:

- **`ListRow`** = 1U (height = `uDp`)
- **`FootButtonBar`** = 1U (height = `uDp`)
- **Stepper / group-control tile** = 1–2U (see Phase 26 for restyle)
- **Focus region** = remaining units after Field rows are counted
- **`FocusFrame` header** = 1U tall; icon/e-stop slot = 0.7U (min 64dp); the idle identity glyph renders at 0.82× the slot (e-stop keeps the full slot)
- **`FloatingEStop`** (shell fallback only) = 0.7U (min 64dp); was listed as 1U — the 0.7U formula matches the header slot and the UAT-1 prominent tier

`uDp` is derived at screen level via `rememberUnitGrid(minOf(contentWidth, contentHeight))`
and passed explicitly to each component. Promote to `CompositionLocal<UnitGrid>` if call-chain
depth grows past 3 levels.

Inner spacing is U-relative via `gapS`/`gapM` in `designsystem/layout/Spacing.kt` (see §7b).

**Settings/config surfaces** (C6): densified by design — tighter grouping, inline keyboard
fields, no wasted vertical whitespace — but **NEVER below the 1U row floor**. All row/control
heights remain `heightIn(min = uDp)` even on C6 screens (owner "All 1U" ruling, Phase 28,
2026-06-12 — this supersedes the earlier "ignore the touch-floor minimum" text that previously
lived here).

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
| Selected spool **IS** the loaded one | **Unload** | `DinghyIcons.ExpandCircleDown` (`expand_circle_down`) | `Intent.Go` (R5 — expected action of this state; was Neutral) |
| Selected spool is **NOT** the loaded one | **Load** | `DinghyIcons.ExpandCircleUp` (`expand_circle_up`) | `Intent.Go` (R5 — was Accent) |

The other foot buttons (Home, Scan) are always present. This three-button foot bar is the
canonical `FootButtonBar` usage.

### Icon-registry-only law

All icons on all screens come from `DinghyIcons.kt` or are requested via the owner. **Never
auto-pick a Material Symbol or create a custom drawable independently.** See
`docs/ui_design/CLAUDE.md §"Icons: never the same glyph twice…"` and the never-auto-pick law
that follows it. This applies to SortFilterControlRow type-tiles, FocusFrame glyphs, and every
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

## 7. Stepper and scrubber

> **UAT-3 (scrubber ≤ 1U):** track + thumb constrained to a single U of height via
> `Modifier.heightIn(max = uDp)`. See `LAYOUT.md UAT-3`.
>
> **UAT-5 (controls ≤ 1U):** All in-Focus controls cap at 1U (`height(uDp)`). Enforced sites:
> **AdjusterPanel ± stepper row**, **Scrubber ± row**, **IncrementPicker** (was floor-only before
> the 2026-06-13 compliance pass — now capped). The LED `ColorWheel` is the **sole sanctioned
> >1U exception**. For the tiles to actually FILL the 1U row (not just floor at 64dp),
> `LocalUnitDp` must be PROVIDED at the adjuster layer — see "LocalUnitDp provision" in the
> AdjusterPanel section below. See `LAYOUT.md UAT-5`.
>
> **UAT-1 (prominent icons ~70-80% U):** `FloatingEStop`'s glyph at `uDp * 0.7f` is the precedent
> for U-relative icon sizing on Focus-anchoring controls. See `LAYOUT.md UAT-1`.
>
> **UAT-4 — RETIRED (Focus-header law, 2026-06-13).** The e-stop docks in the `FocusFrame`
> mandatory header's start slot; there is no longer a floating overlay to reserve space for.
> See `LAYOUT.md UAT-4`.

### Stepper and AdjusterPanel

A step-based adjuster (preferred over drag on the perf floor). See
`.claude/skills/sketch-findings-dinghy-display/references/adjustment-controls.md` for the locked
design. Phase 26 executed the adjustment-screen rebuild (Fine-Tune, Temperature, Outputs) on it.

**AdjusterPanel — two-zone layout (compliance pass, 2026-06-13):**

`AdjusterPanel` is a **two-zone** component: **(1) value zone** (absorbs available slack,
centered — live value + unit span + stacked "was X" baseline readout); **(2) bottom-docked
controls** (± stepper row and IncrementPicker, pinned to the bottom of the panel). There is no
Zone-1 identity (icon + name + reset) inside the panel — **identity lives in the `FocusFrame`
header** (D9 single-item rule, above). Do NOT re-render the selected item's icon or name inside
AdjusterPanel.

**Zone-1 value display anatomy (owner UAT 2026-06-13):**

- **Value line:** the numeric part at large size (e.g. 48sp), `text` color, Geist Mono. Carries
  the R10 rejection flash.
- **Unit span:** rendered on the same line as the value via `Modifier.alignByBaseline()` — NOT
  box-bottom-aligned (that reads as a subscript). Smaller (~0.58× the numeric size, e.g. 28sp),
  lighter (`text2` color), and with **NO separating space** (compact `"120mm/s²"`). Stays `text2`
  and does NOT participate in the R10 flash.
- **"was X" baseline:** stacked **directly below** the value+unit line (text3, 18sp, `text3`
  color). Shown only when the live value has deviated from the entry baseline; hidden when
  returned to baseline. The value zone has `weight(1f)` and absorbs the extra line — **a stacked
  "was" does NOT overflow the 5U phone-landscape budget.** *(Note: earlier doc versions stated
  "was X" must be inline on the same line and that stacking overflows 5U. That was reversed by
  owner UAT 2026-06-13 — multi-word units like mm/s² made the inline form too long; the
  weight-1f zone is the correct fix.)*

**Content inset:** adjuster Focuses use `contentInset = FocusInset / 2` (8dp) — the same as the
Calibration Hub — to give the bottom-docked control group sufficient breathing room without
wasting vertical space on the value zone. This is the canonical **"Focus with a docked action
region"** pattern; see `LAYOUT.md §"Focus with a docked action region"`.

**IncrementPicker selected tile — `accentSoft` fill + accent outline (owner UAT 2026-06-13):**

The active tile in `IncrementPicker` uses **`accentSoft` FILL plus the `accentLine` outline**,
mirroring the `ListRow` selected-state convention. Inactive tiles keep the default `t.surface`
fill and `t.outline` border. This is enabled by the optional **`fill: Color?` param on
`OutlinedControl`** — when non-null, it overrides the default `t.surface` fill (null = no
change). `IncrementPicker` is the first consumer: active tile passes `fill = t.accentSoft`,
inactive tiles pass `fill = null`. This `fill` param is the correct extension point for any
future selected/toggle state that needs a custom fill rather than just a border change.

**LocalUnitDp provision — how 1U-capped rows FILL to 1U (owner UAT 2026-06-13):**

UAT-5 caps all in-Focus controls at `height(uDp)`. Without `LocalUnitDp` being PROVIDED at the
adjuster layer, `OutlinedControl` sees only its 64dp touch floor and sits top-aligned inside the
taller 1U row — tiles look "smaller than 1U and spread out" except when `uDp ≈ 64` (small phone
landscape). With `LocalUnitDp` provided, the tiles floor at 1U and FILL the row, and their
glyphs size to the 0.6U tier.

**Provider sites (in addition to `FootButtonBar` which always provided it):**
- `AdjusterPanel` ± stepper row
- `IncrementPicker`
- `Scrubber` ± row

**± = `DinghyIcons.Decrease` / `DinghyIcons.Increase` (compliance pass, 2026-06-13):**

The decrement and increment actions in AdjusterPanel and Scrubber ± rows use the
**`DinghyIcons.Decrease` / `DinghyIcons.Increase` icon tokens**, rendered through `OutlinedControl`'s
icon path — NOT the literal `"−"` / `"+"` text glyphs. This **supersedes the WR-11 exemption**
("locale-independent literal math glyph is acceptable for ±"); that exemption was a
compatibility workaround and is now retired. Any `"−"` or `"+"` text in an adjuster ± button is
a conformance flag, not an intentional variant.

### Scrubber — THE style is the sketch-004 ringed thumb (R9, owner, 2026-06-12)

The canonical drag-adjust control, owner-referenced Android-SeekBar style ported to tokens:

- **Track:** thin (~6dp, pill-rounded). Filled side = `accent`, remainder = `surface3`.
  Left/start-anchored fill.
- **Thumb:** `surface`-colored knob with a thick **5dp `accent` ring** (~34dp visible) and an
  **enlarged invisible touch target (~74dp)** for the floor. Press state = `accentSoft` halo.
- Min/max labels under the track; live value above. **Snaps to step.**
- **Load-bearing impl rule (the `fa97efb` lesson):** build the element ONCE; update fill width,
  thumb position, and value **in place** during drag. NEVER rebuild/recompose the dragged element
  per move — it detaches the node / stales the drag closure and the gesture dies.
- Source sketch: `.claude/skills/sketch-findings-dinghy-display/sources/004-scrubber-style/`.

**Status (R9 — migration COMPLETE 2026-06-12):** the canonical implementation is
`designsystem/components/Scrubber.kt`. The legacy fill-bar composables (`ScrubberPage`, then
`ScrubberControl`/`ScrubberActions` and `LedBrightnessControl`) are DELETED; every Outputs
surface (fan / servo / heater / PWM / LED brightness) renders the 004 style. The pure gesture
helpers (`fractionFromX`, `settleDispatchCount`) remain host-tested in
`designsystem/ScrubberPage.kt`.

## 7b. Stroke, floor & spacing tables (R12/R13, owner-approved as-built, 2026-06-12)

The authoritative **dp** values. Any "px" stroke/size mention elsewhere in prose is shorthand
for these; where prose and this table disagree, this table wins.

| Element | Value (dp) |
|---|---|
| `ListRow` border — unselected / selected | 1.5 / 2 |
| `FocusFrame` edge — Neutral / Data / inner padding (`contentInset`, default `FocusInset`; Calibration Hub + adjuster Focuses use 8) | 1.5 / 3 / 16 |
| `FocusFrame` header height / icon+e-stop slot / idle identity glyph | 1U / 0.7U (min 64) / 0.82× slot |
| `OutlinedControl` border / min height | 2 / 64 |
| `FillMeter` track height | 6 (pill) |
| Scrubber track / thumb visible / thumb ring / touch target | 6 / 34 / 5 / 74 |
| `SortFilterControlRow` tile height / floor | U / 48 (owner All-1U ruling, 2026-06-12; was U−12) |
| `FloatingEStop` size / corner padding (shell fallback only — see §FloatingEStop) | 0.7U (min 64) / 14 |
| Touch floors (stated once, app-wide) | controls ≥ 64 · absolute minimum ≥ 48 |

**Consolidation mandate (owner):** style should trend MINIMAL — the normalization audit proposes
value consolidations (e.g. fewer distinct stroke weights) as owner-call items; new components
must reuse a value from this table rather than introduce a new one.

**Named spacing tokens (R13)** — spacing is lawful ONLY from this set (amends LAYOUT.md
NON-NEGOTIABLE 3: spacing comes from the named set, not from U-derivation or ad-hoc dp):

| Token | Value | Use |
|---|---|---|
| `gapS` | ≈ U×0.125 (= 8dp at U=64) | grid/tile gaps, intra-row element spacing |
| `gapM` | ≈ U×0.1875 (= 12dp at U=64) | inter-row rhythm (e.g. SortFilter tile = U − gapM) |
| `padFloat` | 14dp (fixed) | floating-overlay corner padding (FloatingEStop) |

> **Audit supersession (2026-06-14):** R13's "not from U-derivation" clause is superseded. `gapS`
> and `gapM` are now U-fractions implemented in `designsystem/layout/Spacing.kt` — calibrated to
> the same 8/12dp values at U=64dp (phone-landscape floor) so existing layouts are unchanged.
> `padFloat` remains a fixed 14dp constant (floating overlays have no row context to scale against).
> Call-site migration happens in later control-audit phases; this table remains the authoritative
> token reference.

## 7c. Icon-size tiers (R15/R16, owner, 2026-06-12)

**Hybrid idiom by tier:** icons that sit WITH TEXT track the text-size setting (`fsSp`); icons
that FILL LAYOUT CELLS track the unit grid (U) — layout cells don't grow with the text setting,
and a text-tracked icon in a fixed 1U cell overflows at fs=L (the 24-05 FloatingEStop UAT bug).
**Fixed-dp icon sizes are RETIRED** except where a tier names one.

| Tier | What | Idiom | Size |
|---|---|---|---|
| **Inline / text-companion** | icon beside a label; status glyphs (`StatusStop`/triangle) riding a value | text | `fsSp(labelBase + 2).dp` |
| **List-row leading** | `ListRow` leading glyphs — use **`ListRowIcon`** | **U** | **0.6U** (R23, 2026-06-12 — supersedes R16's text-tracked fsSp(22); rows are FIXED 1U so a U-fraction is stable; icon does NOT grow with S/M/L, vertically centered by ListRow) |
| **Control** | foot-bar buttons, ≤1U labeled tiles (`OutlinedControl` glyphs) | U | **0.6U** (R24, 2026-06-12 — same ruler as ListRowIcon; carried by `LocalUnitDp`, provided by FootButtonBar / U-aware screen roots; unmigrated contexts fall back to legacy sizing until the wide pass) |
| **Prominent / hero** | Focus anchors, icon-only tiles, adjuster headers | U | 0.7–0.8U (UAT-1); `coerceAtLeast(64.dp)` when the icon IS the touch target (FloatingEStop precedent) |

Conformance: the normalization audit converts stragglers (stray fixed-dp and mis-tiered `fsSp`
heroes) to the tier idiom above.

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
