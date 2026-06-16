# App Settings screen — Focus/Field redesign

**Date:** 2026-06-15
**Status:** design approved, pre-plan
**Owner:** Matthew

## Problem

The App Settings screen (`AppSettingsScreen.kt`, split out of the old System cluster in
`e26f3af7` / `7923b13c`) does not obey the app's standardized two-region **Focus / Field**
grammar that Macros, Move Hub, and Temperature already use. Today it is a flat scrollable
`Column` of rows with **interactive controls embedded in the rows** (Material `Switch`es, an
inline S/M/L group, an inline numeric field) and an **empty Focus pane**. This:

- Violates the UI LAW ("list rows translucent/selectable, controls FILLED in Focus").
- Makes rows ambiguous on a Nexus-7 touchscreen — tapping a row sometimes toggles a setting.
- Wastes the Focus pane (currently header-only, no body).
- Provides no plain-language explanation of what each setting does.
- Pins **Back inside the scrollable Column** instead of a `FootButtonBar` (foot-bar law).

Separately, **webcam-disable is currently a per-printer setting** (`Profile.webcamEnabled`,
surfaced as a toggle row in Printer Settings). It should be **app-global** — a printer that
has a webcam already hides the tile when the cam is unavailable, so per-printer granularity
isn't useful; the real use case is "this device should never show webcam" (no plan to use it,
or the hardware can't handle the stream).

## Goals

1. Make App Settings obey the pure-selector Focus/Field model used elsewhere in the app.
2. Give every setting a plain-language explanation + its control(s) in the Focus pane.
3. Move webcam-disable from per-printer to a single app-global setting.
4. Fix Back to be the first button of a pinned `FootButtonBar`.

## Non-goals

- No change to *what* the settings do (font scale, keep-awake, babystep, battery exemption,
  webcam gating semantics all keep their current effects).
- No new settings beyond the webcam row relocating here.
- No theming/token changes beyond using existing roles.

## Interaction model (decided: pure selectors — Option A)

The Field is a scrollable list of `ListRow`s. Each row shows **icon + setting name + a
right-aligned text state indicator**. **No interactive control lives in any row.** Tapping a
row selects it (translucent selected state) and loads its explanation + control(s) into the
**Focus** pane. One tap-meaning per row: tap = select. The state indicator is read-only text
so nothing on the row looks like it can be changed in place.

Controls in the Focus pane are **filled** (per LAW). A plain on/off setting uses a single
filled toggle control in Focus with **green intent** (green = "the expected action"), not a
row-embedded `Switch`.

## Settings inventory (Field row → Focus content)

| # | Row name | Icon | State indicator (right, read-only) | Focus body when selected |
|---|----------|------|-----------------------------------|--------------------------|
| 1 | Text Size | `TextSize` (existing) | current value: `S` / `M` / `L` | explanation + S/M/L `OutlinedControl` group (existing `TextSizeSelector`) |
| 2 | Keep Screen Awake | `Fluorescent` (add) | `On` / `Off` | explanation + a `ToggleRow` (on/off) |
| 3 | Webcam | `LauncherWebcam` (reuse "videocam") | `On` / `Off` | explanation + a `ToggleRow` (on/off) |
| 4 | Z-Babystep | `LineWeight` (existing) | `Off`, or `N layers` when enabled | explanation + enable `ToggleRow` **and** first-layer-window `StepperRow`, together (one concept) |
| 5 | Battery Optimization | `shield_lock` (add) | `Exempt` / `Optimized` | explanation + button that deep-links to the system battery-optimization dialog |

### Per-setting notes

- **Text Size:** indicator shows the active scale letter; Focus reuses the existing 3-button
  `TextSizeSelector`. App-global font scale unchanged.
- **Keep Screen Awake:** Focus = explanation + a `ToggleRow` (the existing toggle component
  class — a bordered text-pill row, **not** a green-filled control; it carries no `Intent` API,
  so don't spec a fill/green). Same `container.setKeepScreenOn` write path.
- **Webcam:** see migration section. Focus = explanation ("show the webcam tile when a printer
  has a camera; turn off if this device won't use it or can't handle the stream") + a
  `ToggleRow`.
- **Z-Babystep:** Focus holds **both** the enable `ToggleRow` and the first-layer-window control,
  together — they are one concept. Indicator: `Off` when disabled, `N layers` when enabled
  (communicates both on-ness and the window in one short string).
  - **The layer-count control changes from a numeric text field to a `StepperRow` (± inc/dec,
    coerced `>= 1`).** This RETIRES the current `TokenTextField` + numeric-keyboard +
    focused-edit-buffer race handling (WR-03) on this control. Rationale: a small integer layer
    count is a textbook stepper case, it matches the LAW's "numeric adjust = stepper (preferred),
    no alphanumeric keyboard," and it deletes the edit-buffer complexity rather than porting it.
    `StepperRow` exposes increment/decrement callbacks; wire them to `setBabystepLayers(current ± 1)`.
  - Reuse the existing `setBabystepEnabled` / `setBabystepLayers` durable write paths
    (`setBabystepLayers` already coerces `>= 1`).
- **Battery Optimization:** read-only `Exempt` / `Optimized` indicator (re-checked on
  `ON_RESUME` as today). Focus = explanation + a button to open the system dialog; tap still
  deep-links to `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. When already `Exempt`, the
  action is disabled/greyed (nothing to do).

### Proposed list order

Text Size · Keep Screen Awake · Webcam · Z-Babystep · Battery Optimization.
(Order is cheap to adjust on-device; not load-bearing.)

## Focus default state (nothing selected)

On fresh entry no row is selected. `FocusFrame` shows title **"App Settings"** + the
`AppSettings` icon, with a one-line body: "Tap a setting to see what it does and change it."
Once a row is selected, the `FocusFrame` title + icon become **that setting's** name + icon
(the established "use whatever got you here" header rule).

## Webcam: per-printer → app-global migration

- **Add an app-global `webcamEnabled: Boolean` to `DisplayPrefs`** (`display.preferences_pb`) —
  the existing process-scoped, connection-independent store that already holds `keepScreenOn`.
  Default = **On (true)**. Add a matching `AppContainer.setWebcamEnabled(Boolean)` write-scope
  intent + a `webcamEnabled: Flow<Boolean>`, mirroring `keepScreenOn` / `setKeepScreenOn`.
  - ⚠ **Do NOT reuse `WebcamPrefs` (`webcam.preferences_pb`)** — that store is the *per-printer
    preferred-cam-ID* store, a different concept. The new boolean is app-global and lives in
    `DisplayPrefs`.
- **No data migration** from the old per-printer values: hard-default to On. (The per-printer
  feature is niche; defaulting on is harmless for anyone with a working cam and trivially
  re-disabled. Keeps the change simple.)
- **Re-point** `AppContainer.webcamTileEnabled` to combine `webcamCount > 0` with the new
  `DisplayPrefs.webcamEnabled` flow instead of `activeProfile.webcamEnabled`. The
  `webcamTileGate(count, enabled)` predicate shape is unchanged.
- **Remove** the per-printer webcam machinery:
  - The toggle row from `PrinterSettingsScreen` (`PrinterSettingsWebcamToggleRow`).
  - `webcamEnabled` from `Profile` and `PersistedProfile`, and the `setActiveWebcamEnabled`
    helper on `AppContainer`.
  - **Compile-breaking consumers that MUST be updated in the same change (Codex-flagged):**
    - `PrinterSettingsPreviews.kt` (~lines 185–189) — passes `webcamEnabled = …` /
      `onWebcamToggle = {}` to the removed row; delete those args / the preview wiring.
    - `ProfileToggleTest.kt` (~lines 24, 32) — constructs `Profile(… webcamEnabled = …)` and
      asserts `.webcamEnabled`; remove/replace those references.
  - **Decode safety (two distinct mechanisms, both already present):** old persisted profile JSON
    carrying a now-removed `webcamEnabled` key still loads because `ProfileStore`'s Json has
    `ignoreUnknownKeys = true`; and any *missing* declared field is covered by Kotlin default
    values. (Removed field → ignoreUnknownKeys; missing field → default value.)

## Back button (foot-bar conformance)

Back leaves the (now removed) hand-rolled scroll `Column` and becomes the **first button in a
`FootButtonBar`** placed below the `ListBlock` inside the Field region's `RegisteredRegion`
(Back = first foot-bar button law). The `ListBlock` scrolls independently above the bar; the
bar and list share `ListFrameInset` so their outer edges align. Back intent = accent
(neutral/nav).

## Icon registry additions

Two Material Symbols ligatures must be registered in `DinghyIcons`
(`designsystem/icons/DinghyIcons.kt`), same pattern as the existing `LineWeight` entry:

```kotlin
val ScreenAwake = DinghyIcon(IconRef.Ligature("fluorescent"), alternate = "<canonical>")
val BatteryOptimization = DinghyIcon(IconRef.Ligature("shield_lock"), alternate = "<canonical>")
```

(Member names illustrative; canonical `alternate` handles set per the subset-tool convention.
No drawables drawn — owner pre-selected both glyphs: `fluorescent`, `shield_lock`.) `LineWeight`
and `LauncherWebcam` already exist and are reused as-is.

⚠ **Both new icons MUST also be added to the `DinghyIcons.all` list** (the registry's enumeration,
`DinghyIcons.kt` ~lines 311–347) or `IconCatalogDriftTest` fails the build (Codex-flagged).

## Base-frame conformance — THE WHOLE PAGE (the load-bearing requirement)

The entire page — both regions, all selection states, both orientations — must live inside the
standard base-frame component stack. **The current screen violates this**: it sets
`fieldFramed = false` and hand-rolls its own 16dp/12dp/8dp padding instead of using the
registration frame. That opt-out is removed.

The base frame is the **`RegisteredRegion`** registration-frame system (LAYOUT.md R26):
`ScreenScaffold` wraps **each** slot (Focus *and* Field) in a `RegisteredRegion` that owns the
8dp `RegionInset` frame, and every region-filling component is authored **FLUSH** — it never
adds its own frame padding. Concretely:

- **Use `ScreenScaffold` with its DEFAULT framed slots** — do **not** pass `fieldFramed = false`,
  and remove the screen-local 16dp/12dp padding. The scaffold's `RegisteredRegion` owns the frame.
- **Focus slot = `FocusFrame`** (flush), per the Focus Frame law:
  - Mandatory 1U header: leading icon slot (`uDp * 0.7f`, floored at 64dp; the glyph itself is
    further scaled by `IDENTITY_ICON_RATIO` 0.82f inside `FocusFrame`) + centered title (marquee
    on overflow). Inner content inset `FocusInset` (16) is owned by `FocusFrame`, not the screen.
  - Title + icon = "use whatever got you here": no selection → "App Settings" / `AppSettings`;
    row selected → that setting's name + icon.
  - Docked e-stop: the route owns its e-stop (registered in `screenOwnsEstop`); header glyph
    morphs in place to the red e-stop while printing (internal ConfirmGuard). No reliance on the
    retired `FloatingEStop`. Verify the route is in `screenOwnsEstop` (no double / no missing).
- **Field slot = `ListBlock` + `FootButtonBar`**, both authored **flush** (they do NOT apply the
  inset themselves — `RegisteredRegion` owns it via `RegionInset` = `ListFrameInset`, which is why
  the list edges and the Back-bar edges align automatically):
  - The settings list is a **`ListBlock`** (the standard edge-faded, scrollbar-less `LazyColumn`
    with internal list state and 8dp inter-row spacing) — **not** a hand-rolled
    `Column(verticalScroll)`.
  - Back is the first button of a **`FootButtonBar`** stacked below the `ListBlock` as a direct
    child of the Field `RegisteredRegion` (so `RegionGap` spaces it from the list — note
    `RegionGap` spaces only DIRECT children, so the `ListBlock` and `FootButtonBar` must both be
    direct children of the region, not nested in an intermediate `Column`).
- Holds in both orientations via `ScreenScaffold` (landscape Focus|Field 50/50; portrait
  stacked) and across S/M/L and all themes.

## Component / structure notes

- Rows are the standard `ListRow` + `ListRowIcon`; selected row uses the translucent selected
  state. The right-aligned indicator is plain text via the established type roles (no inline
  `fontSize`/`fontFamily` — `FontConformanceTest` will fail the build otherwise).
- Keep the stateless `AppSettingsContent` seam (PREVIEW_AND_TOKENS pattern) so the `@Preview`
  matrix can drive every theme + selection state without a live session. Add a "selected row"
  parameter to the content seam's inputs.
- A small selection state holder (which row is selected) is composition-local; **persistence
  writes continue to route through `AppContainer` write-scope intents**, never a composition
  scope (Compose write-scope cancellation trap).
- Removing `fieldFramed = false` + the bespoke padding is itself a conformance win — App Settings
  stops being the screen that frames itself differently from every other.

## Testing

- Preview matrix: each setting selected, across dark/light/custom themes and S/M/L font scale,
  plus the no-selection default Focus.
- Unit/host: `webcamTileGate` still gates on `count > 0 && appGlobalWebcamEnabled`;
  `DisplayPrefs.webcamEnabled` read/write round-trips (default On); babystep `StepperRow` inc/dec
  writes through `setBabystepLayers` and stays coerced `>= 1` (the old `TokenTextField` edit-buffer
  test, if any, is removed with the field).
- Conformance: `FontConformanceTest` green (no inline font); icon registry/catalog drift tests
  green after the two additions.
- On-device UAT on flox (Nexus 7 2013) **and** moto (per the two-device rule): select each row,
  confirm Focus explanation + control, flip each toggle, confirm Back foot button, confirm
  webcam gating now responds to the app-global setting and the per-printer toggle is gone.
