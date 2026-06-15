# App / Printer Settings Split — Design

**Date:** 2026-06-15
**Status:** Approved — ready for planning (Codex-reviewed 2026-06-15; findings folded in, marked ⚠)
**Scope:** Rework the System/Settings cluster. Systematically split settings into **App Settings**
(device / user preference) and **Printer Settings** (specific to one printer), and rebuild the
affected screens on the current Focus/Field/Foot + FocusFrame + 1U-grid laws. Folds the standalone
Theme screen and the Printers manager into Printer Settings; shrinks the System hub to three rows.

---

## Problem

The System cluster grew organically and the boundary between "app" and "printer" settings is
incoherent:

- **`SettingsScreen` is a grab-bag.** It interleaves one per-printer toggle (Webcam) with three
  app/device-global ones (Babystep, Keep-screen-on, Battery optimization). The user can't tell what
  is global and what follows the active printer.
- **Per-printer settings are scattered across three destinations.** Connection lives in `Printers`,
  Theme/colors + font size live in the standalone `Theme` screen, Webcam lives in `Settings`. There
  is no single place that answers "what are the settings for *this* printer?"
- **Font size is misfiled.** `Profile.fsChoice` is per-printer, but text size is about the user's
  eyes, not the printer — it should be one app-global accessibility preference.
- **The hub has six flat rows** (Printers, Settings, Theme, System Info, About, Power) with no
  organizing principle.

Goal: two clean doors off the hub — **App Settings** and **Printer Settings** — each holding exactly
the settings that match its scope, built to the same grammar as the rest of the redesigned app.

---

## Decisions (locked in brainstorm)

1. **Theme/colors stay per-printer; font size becomes app-global.** Per-printer color gives each
   printer a visual identity (tell the E5 from the E3 at a glance); text size is a user/accessibility
   preference.
2. **Two settings doors, management folded in.** The hub exposes App Settings and Printer Settings.
   Printer switching / add / delete happens *inside* Printer Settings (a "Manage printers"
   sub-screen), not as a separate hub door — not every user has multiple printers.
3. **Babystep stays app-global** (a UI preference about whether/how the babystep row appears).
4. **Printer Settings page shape = "settings rows + Manage row"** (mockup option 3): per-printer
   setting rows up top; a divider; a "Manage printers (N)" row that opens the full list sub-screen.
   Foot is just Back.
5. **Hub leftovers:** **About** stays a hub destination. **System Info** and **Power** move *under
   Printer Settings* (both are about the active printer's host machine / power devices).
6. **Connection has two entry points** — its own row in Printer Settings (edit active printer) and
   inside Manage→Add (new printer) — reusing the same editor component.
7. **`Profile.fsChoice` is cleanly retired** from the per-printer theme path, with a one-time
   migration seeding the new app-global value.

---

## Information Architecture (target)

```
System hub  (home → foot "System")
├── App Settings        mobile_gear           ⟵ NEW (replaces "Settings")
├── Printer Settings    print                 ⟵ NEW (replaces "Printers" + "Theme")
└── About               (unchanged)

App Settings  (flat list — no sub-screens)
├── Text size            S / M / L segmented    format_size   ⟵ MOVED IN (was Profile.fsChoice)
├── Keep screen on       toggle
├── Battery optimization status + tap-to-request
└── Babystep             enable toggle + first-layer window (layers)

Printer Settings  (active printer is the subject)
├── Connection ›         → connection editor (host · port · API key · secure)
├── Theme & colors ›     → theme sub-screen (seed · dark/light · palette · pool)   — NO font control
├── Webcam               inline toggle
├── System Info ›        → existing SystemInformationScreen   ⟵ MOVED IN from hub
├── Power ›              → existing power stub                 ⟵ MOVED IN from hub
│   ──────────
└── Manage printers (N) › format_list_numbered → printer list sub-screen

Manage printers  (sub-screen — today's PrintersScreen, repurposed)
└── list · switch active (tap) · add · delete · rename (drive_file_rename)
```

**Labels** are the long forms above ("App Settings", "Printer Settings", "Manage printers"). Owner
confirmed longer labels are fine in this area.

---

## Icons (owner-specified — Material Symbols names)

The icon law forbids Claude picking glyphs; the owner selected these. Implementation sources the
SVGs into the `DinghyIcons` registry per the existing icon-add flow ([[dinghy-check-img-source-assets]] —
check `img/` for an existing source SVG before authoring).

| Surface              | Glyph                  |
|----------------------|------------------------|
| App Settings (hub)   | `mobile_gear`          |
| Printer Settings (hub)| `print`               |
| Manage printers      | `format_list_numbered` |
| Text size            | `format_size`          |
| Rename               | `drive_file_rename`    |

System Info, Power, Connection, Theme, Webcam reuse their existing registry glyphs.

---

## Design

### 1. System hub (`SystemPageScreen`)

Trim `systemNavRows()` from five entries to two: **App Settings** (`NavDest.AppSettings`,
`mobile_gear`) and **Printer Settings** (`NavDest.PrinterSettings`, `print`), followed by the
existing **About** row. Remove the Printers, Settings (old), Theme, System Info rows and the Power
stub row from this screen (Power + System Info relocate under Printer Settings). Focus strip
(brand lockup + version + active printer name) is unchanged. Foot = Back.

### 2. App Settings page (`AppSettingsScreen` — new)

Rebuild of today's `SettingsScreen`, **minus Webcam**, **plus Text size**. Standard
Focus/Field/Foot via `ScreenScaffold` + `FocusFrame`(title "App Settings", `mobile_gear`).

- **Field** (scrolling `Column`, 1U rows):
  - **Text size** — a new segmented **S / M / L** selector row (`format_size` leading glyph). Reads/
    writes the new app-global font-scale setting (see §5). Selected segment uses the established
    selected-fill token treatment; labels via `DinghyType` roles (no inline sizes).
  - **Keep screen on** — `DenseToggleRow`, lifted verbatim (`container.keepScreenOn` /
    `setKeepScreenOn`).
  - **Battery optimization** — status + tap-to-request row, lifted verbatim.
  - **Babystep enable** — `DenseToggleRow` (`container.babystepEnabled`).
  - **Babystep first-layer window** — numeric `TokenTextField` row (`container.babystepLayers`),
    including the WR-03 focus-guarded re-seed already implemented.
- **Foot:** Back (accent).
- All persistence routes through process-lifetime `container.set*` writeScope intents
  ([[dinghy-compose-write-scope-cancellation]]).
- Keep the stateless `AppSettingsContent` seam for the `@Preview` matrix (PREVIEW_AND_TOKENS law).

### 3. Printer Settings page (`PrinterSettingsScreen` — new)

- **Focus:** `FocusFrame` = active printer card — `displayName()`, `host:port`, connection-state
  ring/label — lifted from today's `PrintersContent` Focus (including the `FocusEdge.Data(ringColor)`
  treatment). Title "Printer Settings", `print` glyph. Empty state (no profile configured) shows the
  existing "no printers" Focus and routes the Field straight to an Add action.
- **Field** (1U `ListRow`s):
  1. **Connection ›** → opens the inline `PrinterConnectionEditor` targeting the **active** profile.
  2. **Theme & colors ›** → navigates to the theme sub-screen.
  3. **Webcam** — inline toggle (`activeProfile.webcamEnabled` / `setActiveWebcamEnabled`).
  4. **System Info ›** → `NavDest.SystemInfo` (existing screen, unchanged).
  5. **Power ›** → existing power stub.
  6. *divider*
  7. **Manage printers (N) ›** → `NavDest.ManagePrinters` (count N = profile count).
- **Foot:** Back (accent).
- Stateless `PrinterSettingsContent` seam for previews.

### 4. Manage printers sub-screen (`NavDest.ManagePrinters`)

This is today's `PrintersScreen` repurposed as the list/management surface:

- **Focus:** active printer card (same as Printer Settings Focus, or omitted — see Open Questions).
- **Field:** profile-row `ListBlock`; tap a row switches active and pops back to Printer Settings.
- **Foot:** `FootButtonBar` — Back / Add / Edit / Delete mode-toggle (existing `PrinterMode`
  state machine, D-13 — unchanged), **plus Rename**. Edit-armed row tap opens the connection editor;
  Delete-armed raises the existing `ConfirmGuard`. Rename writes `Profile.name` (a name-only save-
  field; the alphanumeric-keyboard Settings/Save-name exception applies — renaming is a sanctioned
  text field).
- Add opens the connection editor for a new profile. All API-key clear/preserve semantics (CR-01,
  T-28-06-01) carry over verbatim.
- **⚠ Codex finding — extract the connection editor.** `PrinterConnectionEditor` is currently
  `private` inside `PrintersScreen.kt`. Reusing it from the new `PrinterSettingsScreen` Connection
  row requires **extracting it into a shared composable** (its own file / `internal` visibility),
  not a direct call. Keep its preview seam intact.

### 5. Theme sub-screen (existing Theme editor)

- Reached from Printer Settings → "Theme & colors". Content unchanged (seed, dark/light, palette
  mode, pool overrides) **except the font-size (S/M/L) control is removed** — it now lives in App
  Settings. The per-printer theme write path (`setActive*`) is otherwise untouched.
- `NavDest.Theme` is retained as the route (now reached from Printer Settings instead of the hub).
- **Codex correction:** the font control lives in **`ThemeEditorScreen`**, not the `ThemeScreen`
  route wrapper, and its **stateless preview seam takes `fsChoice`**. Removing the control means
  updating that content seam's signature and its `@Preview` matrix, not just deleting one row.

---

## Data & migration (the one real technical lift)

Only **one** thing moves in the data layer; everything else is UI relocation with zero data change.

### Font size: per-printer → app-global

- **Today:** `Profile.fsChoice: String` ("S"/"M"/"L"), consumed via `Profile.toThemeTuple()` →
  `ThemePrefs.sanitizeTuple(... rawFs ...)` → `ThemeResolver`/`FontScale`.
- **Target:** a new app-global DataStore-backed setting, sibling of `keepScreenOn`/babystep
  (process-scoped, connection-independent). New `container.fontScale: Flow<FontScale>` +
  `container.setFontScale(FontScale)` writeScope intent. The theme/`--fs` resolution reads the font
  scale from this app setting instead of from the per-printer tuple.
- **⚠ Codex finding — `fs` is baked deeper than the tuple inputs.** `ThemeTuple.fs` is not display
  state: it flows through **`ThemeResolver.apply` AND `ThemeResolver.bake`**, plus the preview/dev
  `ThemeOverride` paths. The app-global font scale must be **injected into the resolution where the
  baked tokens (incl. `--fs`) are produced for the ACTIVE theme flow** — not merely surfaced as a
  Settings row. AppContainer carries **two** theme concepts (active per-profile theme + a global
  idle/new-profile theme); the font scale must feed the active-theme flow or active printers will
  keep driving `--fs` from their own profile tuple. Plan task: trace every `ThemeTuple.fs` /
  `rawFs` read and re-point it at `container.fontScale`.
- **⚠ Codex finding — migration must NOT block on an active profile.** Do **not** copy
  `TraceStylePrefs.migrateUnscopedTo` literally: it awaits `activeProfileId.filterNotNull().first()`,
  so a no-printer install would never set the global font default. **Migration rule:** on first launch
  after upgrade, if the app-global font key is unset, seed it from the active profile's `fsChoice`
  **if a profile exists right now**, else default `M` immediately; mark migrated via a sentinel key.
  Never suspend waiting for a profile.
- **Retire `fsChoice`:** drop it from the runtime `Profile` and from the theme tuple inputs. Old
  persisted blobs still decode cleanly (kotlinx `ignoreUnknownKeys`), so `PersistedProfile.fsChoice`
  may be kept as a tolerated-but-ignored decode field OR removed — implementation detail, but the
  runtime `Profile` and `toThemeTuple()` stop carrying it. Update `ThemePrefs.sanitizeTuple` /
  `ThemeTuple` to no longer take `rawFs`. **Test surface (Codex):** the breaks land in the theme
  tuple / fallback / profile tests that assert `rawFs`, `fsChoice`, and `ThemeTuple.fs` directly —
  update these, plus start-destination and shell-state tests. `FontConformanceTest` (scans inline
  `fontSize`/`fontFamily`) and `CommandCatalogDriftTest` (CommandRegistry vs `docs/commands/*.json`)
  are **NOT** triggered by this change.

### No data change (UI relocation only)

- **Webcam** (`Profile.webcamEnabled`) — stays per-printer; renders under Printer Settings.
- **Theme, Connection** — stay per-printer; reorganized UI only.
- **Keep-screen-on, babystep, battery** — already app-global.

---

## Routing changes (`NavDest`)

- **Add new route objects:** `NavDest.AppSettings`, `NavDest.PrinterSettings`,
  `NavDest.ManagePrinters`. Keep `NavDest.Theme`, `NavDest.SystemInfo`, `NavDest.About`.
- **⚠ Codex finding — ADD, don't rename.** Routes are kotlinx-serialization `data object`s and the
  `start_dest` safe-parser + tests key on **`simpleName`**. Renaming `Devices`→`ManagePrinters` or
  `Settings`→`AppSettings` would break the parser and persisted start-dest values. Instead **add the
  new objects and remove the now-unused `Settings`/`Devices` objects**; a stale persisted start-dest
  pointing at a removed name simply falls back to `WaterfallHome` via the existing safe-parse default
  (acceptable). Do not reuse the old `simpleName`s.
- Update `knownNavDests`, `parseStartDest`/`StartDestMapping`, the **start-destination tests and
  shell-state tests** (Codex: these key on `simpleName` and will need updating), and any round-trip
  tests.
- **FOOT_GUN_DESTS / mid-print reachability:** the new settings destinations inherit System's
  mid-print-reachable posture (not foot-guns). Verify e-stop gating (`screenOwnsEstop`) covers the
  new routes per the FocusFrame docked-e-stop law ([[dinghy-focus-frame]]).

---

## Non-Goals (explicitly out of scope)

- **App-level theme defaults / per-printer overrides** — rejected as YAGNI; theme stays simply
  per-printer.
- **Moving babystep or webcam scoping** — babystep stays app-global, webcam stays per-printer.
- **New power-device control** — Power remains the existing stub; only its location moves.
- **Reworking the connection editor internals or mDNS scan** — reused verbatim.
- **Tucking About into App Settings** — About stays a hub destination.

---

## Open Questions (for planning, not blocking)

1. **Manage printers Focus:** keep an active-printer Focus card on the Manage sub-screen, or make it
   Field-only (list + foot) since Printer Settings already showed the active card one level up?
   Lean: Field-only to avoid redundancy, but confirm during UI build.
2. **Text-size control idiom:** segmented S/M/L inline selector vs. a stepper. Lean segmented (it is a
   3-value enum, not a range). Confirm against `COMPONENTS.md` control classes during planning.

---

## Affected files (initial map — refine in planning)

- `ui/route/NavDest.kt` — add/repurpose destinations; update `knownNavDests` + parse/round-trip.
- `ui/screen/SystemPageScreen.kt` — trim hub to App Settings / Printer Settings / About.
- `ui/screen/SettingsScreen.kt` → `AppSettingsScreen.kt` — drop Webcam, add Text size.
- `ui/screen/PrinterSettingsScreen.kt` — **new** (Focus card + setting rows + Manage row).
- `ui/screen/PrintersScreen.kt` → Manage printers sub-screen; add Rename; **extract
  `PrinterConnectionEditor`** to a shared composable (Codex).
- `ui/.../ThemeEditorScreen.kt` — remove font-size control; **update its content seam signature +
  `@Preview` matrix** (font control lives here, not the `ThemeScreen` wrapper — Codex).
- `config/Profile.kt` + `ProfileStore` — retire `fsChoice` from runtime/tuple.
- `theme/…` (`ThemePrefs.sanitizeTuple`/`ThemeTuple`, **`ThemeResolver.apply` + `.bake`**, preview/
  `ThemeOverride` paths, `FontScale` wiring) — `--fs` baked from the app-global font scale, fed into
  the **active-theme** flow (Codex).
- `di/AppContainer.kt` — new `fontScale` flow + `setFontScale` intent + DataStore key + **non-blocking
  migration** (must not await an active profile — Codex); wire font scale into the active-theme path.
- `ui/route/AppShell` / NavHost + `RootController` / shell-state — route the new destinations.
- Preview seams + `@Preview` matrices for the new/changed screens (Settings/System/Printers/
  ThemeEditor content seams have compile-time signature changes — Codex).
- Tests: NavDest round-trip + **start-destination + shell-state** tests, font-scale migration,
  App/Printer settings content, **theme tuple/fallback/profile tests asserting `rawFs`/`fsChoice`/
  `ThemeTuple.fs`** (Codex). (`FontConformanceTest` / `CommandCatalogDriftTest` not triggered.)

---

## Verification

- Build + full suite green (Windows-side via `E:\Android\gw.bat`).
- Font-scale migration: existing install with a non-default per-printer `fsChoice` lands on the same
  text size app-globally after upgrade; fresh install defaults to `M`.
- On-device UAT on flox + moto ([[dinghy-test-devices]]): hub → both doors; font size changes apply
  app-wide across printer switches; theme/colors still per-printer; switch/add/delete/rename via
  Manage; System Info + Power reachable under Printer Settings; e-stop present on the new screens.
