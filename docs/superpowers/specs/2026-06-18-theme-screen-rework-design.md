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

Single **Back** action in the Field foot bar, matching `AppSettingsScreen`. Back is a back-stack step:
swatch-editor → grid → (clear selection) → exit screen.

**Back intent is contextual (Codex #4, THEMING.md R5):** accent (pure nav) when no draft is dirty; when
Back would **discard an uncommitted draft** it is a discarding control and renders **red (`Intent.Danger`)**
— matching the law that a back/cancel discarding pending input is red. (Save is the explicit commit;
Revert/Cancel are the explicit discards.) Confirm this Back-vs-dirty-draft behavior at UAT.

**E-stop shell gate (Codex #5, `[[dinghy-focus-frame]]`):** Theme currently receives the shell-fallback
`FloatingEStop` *because it has no `FocusFrame`* (`AppShell.kt:~941/978`). Adopting `FocusFrame` REQUIRES
adding Theme to the `screenOwnsEstop` route set so it becomes a **screen-owned** e-stop destination —
remove it from the shell fallback and pass `isPrinting` / `onEmergencyStop` / `onPanic` into **every**
Theme `FocusFrame` state, or the docked e-stop and shell fallback both render (the known double-e-stop
gap). The docked-e-stop morph is then preserved on every Focus state.

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

### Draft-layer architecture (CORRECTED per Codex review — load-bearing)

**The app does NOT theme from `ThemeResolver.tokens` in production.** `MainActivity` collects
`container.effectiveTokens` (`MainActivity.kt:~129`), which is `combine(activeThemeTuple, _themeOverride,
devCyclerEnabled){ … themeResolver.bake(tuple) }` (`AppContainer.kt:~926`) — a **bake of the canonical
`activeThemeTuple`** plus a transient **`_themeOverride`** overlay. The resolver's mutable `set*` state is
NOT the production boundary (the resolver-based `DinghyTheme` overload is benchmark/test-only,
`DinghyTheme.kt:~54`). So a "preview via `themeResolver.set*`" model would not preview anything in the
real app. Live preview must flow through `effectiveTokens`.

The draft layer therefore **reuses/extends the existing `_themeOverride` overlay** (the same mechanism the
dev cycler already uses to feed `effectiveTokens` without persisting):

- **Preview** = write a **draft `ThemeTuple`** into the override overlay (the editor builds the draft from
  the saved tuple and mutates seed / overrides / accent / shift on it). `effectiveTokens` bakes it live —
  **no DataStore write.**
- **`commitThemeDraft()`** = perform the durable `setActive*` writes for the staged tuple via the
  process-lifetime `writeScope` (never a composition scope, `[[dinghy-compose-write-scope-cancellation]]`),
  then **clear the draft overlay** (the now-persisted `activeThemeTuple` carries the value).
- **`revertThemeDraft()`** = **clear the draft overlay** — `effectiveTokens` falls back to the saved
  `activeThemeTuple`. No resolver re-apply needed.
- Because the draft lives in the same overlay slot the dev cycler uses, define a clear precedence (an
  active edit draft supersedes the dev cycler) and ensure entering/leaving the editor sets/clears it.
- **Test:** preview changes `effectiveTokens` WITHOUT a DataStore write; commit writes through
  `writeScope` and clears the draft; revert clears the draft and `effectiveTokens` returns to the saved
  tuple exactly; a profile/state tick does not clobber an active draft.

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
- **Save** (`Intent.Go` green) **/ Cancel** (`Intent.Danger` red — Cancel discards the pending swatch
  edit, and a control discarding pending input is red per THEMING.md R5, Codex #4).
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

**Buttons (docked in the Focus, below the grid) — ONE 1U row of three (Codex #7, 5U budget):**
**Randomize** (`shuffle`, `Intent.Warn` amber) · **Revert** (`refresh`/`Revert`, `Intent.Warn` amber —
undo-the-draft, NOT neutral; neutral is retired for action buttons per THEMING.md R5) · **Save**
(`save`/`Save`, `Intent.Go` green).

> **5U layout budget:** header 1U + grid 2U (two 1U swatch rows) + actions 1U = **4U**, leaving ~1U for
> the inset/gaps so the Focus survives the 5U minimum without clipping or scrolling. The owner's earlier
> mock had two action rows (Randomize on top; Revert+Save below) — collapsed to one row to honor the cap.
> *(Owner-confirmed 2026-06-18.)*

## Color engine changes

### New: accent override (new capability)

Today the accent is always derived from the seed and cannot be overridden; only pool slots and the 3
status slots are overridable (stored in `Profile.poolOverrides`, with numeric keys for pool and
`StatusSlot.key` for status). The grid's Accent swatch makes accent editable.

**Storage — a dedicated field, NOT the shared map (Codex #2).** The override sanitizer drops any
non-numeric, non-status key (`ThemePrefs.kt:~252`: `rawKey.toIntOrNull() ?: continue`), so an `"accent"`
map key would be silently discarded. Add a dedicated **`accentOverrideArgb: Long?`** to `Profile`,
`ThemePrefs.ThemeTuple`, and the global theme prefs, round-tripping identically to the other overrides
(`bake` == live `compute`/`apply`).

**Application — feed the whole accent family, not one token (Codex #3).** Accent is a *family*: `accent`,
`accent2`, `accentSoft`, `accentLine`, `accentGlow`, and `directional.temperature` all derive from
`primary` in `TokenBridge.build` (`TokenBridge.kt:~116/133/146`). The override must resolve the accent
**before** the family is derived, so every accent-derived token (and `directional.temperature`) follows
it. Unlike status, the accent override applies in **all** palette modes (accent is always shown).

- Add the matching resolver/bridge plumbing + an `AppContainer` durable intent and a preview-draft path.

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

> **Documented exception — Go glyph (Codex #6, THEMING.md R-status-shape):** elsewhere in the app Go is
> *shapeless* (only Stop=octagon and Caution=triangle carry status glyphs). The Theme Colors grid gives
> every intent swatch an inner identifier (Accent=`star`, Stop, Caution, Go), so Go needs one here; the
> **owner explicitly chose `check_circle`** over leaving it blank. This is a deliberate, owner-sanctioned
> exception scoped to the grid swatch only — it does NOT change Go's shapeless treatment anywhere else.

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
- Compose preview matrix (Codex #8, `PREVIEW_AND_TOKENS.md`) renders **every** Focus state (resting,
  dark/light, palette, seed, grid, swatch editor) across **portrait AND landscape**, dark/light, all three
  palette modes, the S/M/L font scales, and an RTL/pseudolocale pass — token-only chrome, no raw colors.
- `FontConformanceTest`, `verify_ligatures.py`, and the icon `all`-list check stay green.
- On-device UAT on flox + moto (both ABIs), per `[[dinghy-test-devices]]`.

## Codex review

Reviewed read-only against the design standards on 2026-06-18 (verdict: not compliant unchanged →
revised). All 8 findings accepted and folded in above: #1 preview boundary (themes from `effectiveTokens`,
not the resolver — draft reuses `_themeOverride`), #2 accent stored in a dedicated field, #3 accent feeds
the whole token family, #4 intent corrections (Revert amber, Cancel/dirty-Back red, neutral retired),
#5 AppShell `screenOwnsEstop` migration, #6 Go-glyph owner exception documented, #7 actions collapse to
one 1U row for the 5U budget, #8 fuller preview matrix.

## Open risks

1. **Draft overlay vs dev-cycler precedence** — both use `_themeOverride`; define which wins and clear
   the draft on editor exit. Test a draft survives an unrelated state tick and Revert restores exactly.
2. **Accent token family completeness** — ensure no accent-derived token (incl. `directional.temperature`)
   is missed when the override is applied; assert `bake == compute`.
3. **Back-vs-dirty-draft** — owner-confirmed 2026-06-18: Back renders red while a draft is unsaved (it
   doubles as the "you haven't saved" cue) and discards on exit. Verify the red↔accent transition at UAT.
4. **Single action row** in Theme Colors — owner-confirmed 2026-06-18.
