# Theme Screen Rework — Design Spec

**Date:** 2026-06-18
**Status:** Approved design (brainstorm complete) — ready for implementation plan
**Owner:** Matthew

## Goal

Rebuild the **Theme** screen so it conforms to the app's lists-first **Focus / Field** grammar
(the same pattern `AppSettingsScreen` uses). The Theme screen is the last holdout still rendered as
the legacy dense `Column.verticalScroll` (`ThemeEditorScreen`). This rework retires that scroll and
replaces it with a Field of selector/toggle rows + a Focus that swaps by selection, adds a staged
(Save/Revert) editing model with live preview, a new slider-based color picker, and a new
accent-override capability.

## Non-goals / removals

- **Presets are deleted.** No curated seed swatches anymore (`PRESET_SEEDS`, `SeedSwatch`).
- The legacy `ThemeEditorScreen` dense-scroll body is **retired** (replaced, not promoted).
- No change to the OKLCH palette generator math (`Palette.kt`) or the golden fixtures.
- No change to dark/light or palette-mode *semantics* — only their presentation moves into rows.
- Font size is **not** here (it's app-global in `AppSettingsScreen`).

## Screen structure

`ThemeScreen` becomes a `ScreenScaffold` with a stable **Field** (4 rows + Back foot bar) and a
**Focus** that swaps by the selected row. Portrait stacks the two regions; landscape is 50/50
(Focus left, Field right). The Field is **stable navigation — it never changes unless functionality
requires it** (e.g. drilling into a swatch editor changes only the Focus, not the Field).

### Field rows (top → bottom)

| # | Row | Icon | Inline control | Tapping the row body |
|---|-----|------|----------------|----------------------|
| 1 | Dark / Light | `contrast` | **Toggle** (flips live, persists immediately) | Focus → explainer text |
| 2 | Palette Mode | `invert_colors` | none (selector, shows current mode ›) | Focus → explainer + **3-segment** Colorful / Simple / High-Contrast (live, persists immediately) |
| 3 | Seed Color | `colors` | none (selector, shows current hue name ›) | Focus takeover → **hue slider + Save** |
| 4 | Theme Colors | `palette` | none (selector ›) | Focus takeover → **4×2 swatch grid + Randomize / Revert / Save** |

> The inline toggle (row 1) is sanctioned by the owner's law: **a toggle is the one control allowed
> to live on a Field item.** Dark/Light is therefore the *only* row with an inline control; every other
> row (Palette Mode, Seed Color, Theme Colors) is a pure selector whose control lives in the Focus.

### Focus states

- **Resting (nothing selected):** plain explanatory text only — *"A theme is built from a **seed**
  color, a **color style** (palette mode), and a set of **pool colors**…"* No live-preview swatches.
- **Dark / Light selected:** explainer of how dark/light works (primary elements flip to contrast;
  generated colors shift to stay contrast-safe). The control itself is the inline row toggle.
- **Palette Mode selected:** explainer of what palette mode does (Simple / High-Contrast remove all but
  the main accent from data surfaces) + the **3-segment** Colorful / Simple / High-Contrast control. It
  applies live and persists immediately — it is NOT part of the Seed/Theme-Colors Save/Revert draft.
- **Seed Color selected:** the hue picker (see Pickers) + docked **Save**.
- **Theme Colors selected:** the swatch grid (see Grid) + docked **Randomize** then **Revert / Save**.
- **Theme Colors → swatch tapped:** the swatch editor (see Pickers) replaces the Focus body; Field
  unchanged (Theme Colors stays selected). **Save / Cancel** return the Focus to the grid.

### Foot bar

Single **Back** action (`Back` icon, accent intent) in the Field foot bar, matching `AppSettingsScreen`.
Back is a back-stack step: swatch-editor → grid → (clear selection) → exit screen. The docked-e-stop
morph in `FocusFrame` is preserved on every Focus state.

## Staging / persistence model

Two classes of control:

1. **Immediate (no Save concept):** Dark/Light toggle and Palette Mode segment apply to the live
   resolver **and** persist to the active profile instantly (existing `setActiveDark` / `setActiveMode`).

2. **Drafted (Save commits, live preview):** Seed Color and Theme Colors.
   - Entering the editor starts a **draft** from the saved theme.
   - Edits drive the **live resolver only** (whole-app preview) and **do not persist**.
   - **Save** writes the staged values durably to the active profile.
   - **Back / Revert / Cancel** re-applies the saved profile to the resolver, discarding the draft.

   Inside **Theme Colors** there are two staging levels:
   - **Swatch editor Save/Cancel** stages (or discards) one color into the working grid — already
     reflected in the live preview.
   - **Grid Revert / Save** discards or durably commits the whole working set.

   Rules:
   - **Randomize** clears any pending pool/accent overrides in the draft and re-rolls a fresh
     generated set from the current seed (new `poolShift`). Revert still undoes it.
   - Changing the **Seed** does **not** wipe saved overrides — they persist on top of the new seed.

### Draft-layer architecture (key implementation concern)

The whole app is themed by the single global `ThemeResolver` (the `StateFlow<ThemeTokens>` boundary).
Live preview therefore must drive that global resolver, but **without persisting** until Save. Approach:

- Add **preview-only** mutators on `AppContainer` / resolver (e.g. `previewSeed`, `previewOverride`,
  `previewAccent`, `previewShift`) that call `themeResolver.set*` but **skip** the durable
  `mutateActiveProfile` write.
- Add a **`commitThemeDraft(...)`** that performs the durable `setActive*` writes for the staged values
  (process-lifetime `writeScope` — never a composition scope, per
  `[[dinghy-compose-write-scope-cancellation]]`).
- Add a **`revertThemeDraft()`** that re-applies the persisted active-profile tuple to the resolver
  (`resolver.apply(savedTuple)`), discarding the preview.
- **Risk:** the profile/connection collector (`seedTheme`) also drives the resolver; it must not clobber
  an in-progress preview. Guard so the collector only re-applies on an actual profile/connection change,
  not on every recomposition, and ensure entering/leaving the editor reconciles cleanly. Flag for the
  plan; cover with a test that a preview survives an unrelated state tick and that Revert restores exactly.

## The pickers

### Seed picker (hue only)

A single horizontal **hue slider** (0–360) over the spectrum gradient. Hue is the only seed axis the
generator meaningfully consumes (it re-derives lightness for contrast and clamps chroma), so S/V are
intentionally omitted. The **Focus outline reads out the resulting accent color** (the cusp-normalized
color the seed produces — the real result, not the raw hue), echoing the Spoolman filament-color border.
Drag updates the live preview (cheap — handle move); pointer-up settles. **Save** commits the seed.

### Swatch editor (pool / accent / status — full color)

Three sliders **H / S / V** with minimal inline labels. The stored value is the **literal chosen ARGB**
(pool/accent/status overrides are applied verbatim by `TokenBridge`), so all three axes matter here.

- **Focus outline = the live picked color** (compare target).
- **Focus header = a small (≤1U) swatch of the currently saved value** + the slot **title**
  ("Pool 2" / "Accent" / "Stop" / "Caution" / "Go"), so saved-vs-in-progress is directly comparable.
- **Save / Cancel.**
- **Status slots keep the existing mode-gating note:** a status override only takes visible effect in
  **Colorful** mode (Simple collapses status to text; High-Contrast forces fixed RYG). Accent and pool
  overrides apply in all modes.

Both pickers reuse the settle-not-stream gesture discipline (cheap mid-drag, durable/preview write on
settle) and the Adreno-320 motion law (no looping animation).

## The Theme Colors grid

A **4×2 grid of 8 swatches**, each rendering its literal draft color:

- **Row 1 — Pool 1–4:** the **number** (1–4) inside the swatch.
- **Row 2 — Accent · Stop · Caution · Go:** the **symbol** inside the swatch — `star` (Accent),
  Stop octagon (`DinghyIcons.StatusStop`), Caution triangle (`DinghyIcons.Warning`), `check_circle` (Go).
- **No captions under the swatches** — the inner number/symbol identifies each; the editor header names
  it on drill-in.
- **Swatch height ≤ 1U** (the control cap, UAT-5) — swatches are wide cells, not squares.
- Tapping any swatch opens its editor (above).

**Buttons (docked in the Focus, below the grid):**
- Row A: **Randomize** (`shuffle`, amber / `Intent.Warn`), full width.
- Row B: **Revert** (`refresh`/`Revert`, neutral) + **Save** (`save`/`Save`, green / `Intent.Go`).

## Color engine changes

### New: accent override (new capability)

Today the accent is always derived from the seed and cannot be overridden; only pool slots and the 3
status slots are overridable (stored in `Profile.poolOverrides`, with numeric keys for pool and
`StatusSlot.key` for status). The grid's Accent swatch makes accent editable:

- Store an accent override under a dedicated reserved key (e.g. `"accent"`) in the same override map,
  or a dedicated field — chosen at plan time; must round-trip through `Profile` ↔ `ThemePrefs.ThemeTuple`
  ↔ `ThemeResolver`/`TokenBridge` identically to pool/status overrides (`bake` == live `compute`).
- `TokenBridge.build` applies the accent override **after** generation, replacing the `accent` token.
  Unlike status, the accent override applies in **all** palette modes (accent is always shown).
- Add the matching resolver mutator (`setAccentOverride` / preview variant) and `AppContainer` intent.

### Unchanged

`Palette.generate` and the golden fixtures are untouched. Pool size stays `DEFAULT_POOL_MAX_ITEMS = 4`.

## Icons (owner-selected — never auto-picked)

| Slot | Material Symbols ligature | Status |
|------|---------------------------|--------|
| Dark / Light row | `contrast` | **new** registry entry |
| Palette Mode row | `invert_colors` | **new** registry entry |
| Seed Color row | `colors` | **new** registry entry |
| Theme Colors row | `palette` | exists (`Palette`) |
| Accent swatch symbol | `star` | **new** registry entry |
| Go swatch symbol | `check_circle` | exists (`CheckCircle`) |
| Randomize button | `shuffle` | **new** registry entry |
| Revert button | `refresh` | exists (`Revert`) |
| Save button | `save` | exists (`Save`) |
| Stop swatch symbol | (existing `StatusStop`) | exists |
| Caution swatch symbol | (existing `Warning`) | exists |

New registry entries: `contrast`, `invert_colors`, `colors`, `star`, `shuffle`. The full Material Symbols
Outlined font is bundled (no subsetting), so each renders without a font build step. **Each new glyph
needs both the `val` and an entry in the `all` list** (per the increment-values lesson), and
`verify_ligatures.py` must stay green.

## Architecture / components

- **`ThemeScreen.kt`** — rewritten as the `ScreenScaffold` host (was a thin wrapper around
  `ThemeEditorScreen`). Stateful: collects active profile / resolver tuple, owns `selected` row state and
  the draft state, wires durable + preview intents.
- **Stateless content seam** (`ThemeContent` + per-state Focus composables) — preview-first
  (`PREVIEW_AND_TOKENS.md`); the `@Preview` matrix drives every row-selection + theme state without a
  live session. Replaces `ThemeEditorContent`.
- **Reused component classes:** `ListRow` + `ToggleRow` (Field rows), a small 3-segment selector
  (reuse the palette-mode segment pattern), `FocusFrame` (every Focus state, e-stop morph),
  `FootButtonBar`/`FootAction` (Back), `OutlinedControl` (buttons), `ScreenScaffold`/`rememberUnitGrid`.
- **New components:** a `HueSlider` and an `HsvSliders` control (Compose `Canvas`, settle-not-stream
  gestures mirroring the existing `ColorWheel`/`SaturationValueSquare`), a `SwatchCell` (1U, literal fill,
  inner number/symbol), and the grid. `ColorWheel` + `SaturationValueSquare` are retired if nothing else
  uses them.
- All durable writes route through process-lifetime `writeScope` intents; preview writes update the
  resolver only. No `rememberCoroutineScope()` for persistence.

## Testing

- Host-pure: accent-override round-trips (`Profile` ↔ tuple ↔ `bake`); `bake == compute` parity with an
  accent override; Randomize clears overrides + sets a new shift; Revert restores the saved tuple exactly.
- Resolver: preview mutators change tokens without persisting; `revertThemeDraft` recomputes from the
  saved profile; a profile/state tick does not clobber an active preview.
- Compose preview matrix renders all Focus states (resting, dark/light, palette, seed, grid, swatch
  editor) across dark/light + palette modes.
- `FontConformanceTest`, `verify_ligatures.py`, and the icon `all`-list check stay green.
- On-device UAT on flox + moto (both ABIs), per `[[dinghy-test-devices]]`.

## Open risks

1. **Draft vs profile collector race** (above) — the main integration risk. Test explicitly.
2. **Accent override storage key** collision with pool numeric keys — use a clearly reserved,
   non-numeric key and assert it can't be parsed as a pool index.
3. Back-stack semantics from the swatch editor (step to grid vs exit) — confirm during build.
