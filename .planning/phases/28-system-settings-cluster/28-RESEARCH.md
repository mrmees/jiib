# Phase 28: System / Settings Cluster — Research

**Researched:** 2026-06-12
**Domain:** Android/Kotlin/Compose UX migration — System page, swipe-up retirement, C6 densification, theme-editor functional fixes
**Confidence:** HIGH (codebase-internal research; all critical claims verified by direct source inspection)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**System page & nav retirement**
- D-01: Real System page. A new jiib-style NavDest route. System foot button on WaterfallHome opens it; interim `HomeAction.OpenDrawer` retired.
- D-02: Direct-tap rows + static info Focus capped at 20% HEIGHT (portrait) / 40% WIDTH (landscape).
- D-03: Entry order = Printers → Settings → Theme → System Info → About → Power (stub).
- D-04: Swipe-up gesture + AppDrawer **deleted entirely**. Closes `2026-06-11-retire-swipe-up-nav.md`.
- D-05: Orphans (Temperature, Console, Fine-Tune) rehome to WaterfallHome idle list. Glyphs need owner sign-off.
- D-06: Mid-print System access via printing shortcut grid — System entry added.
- D-07: Preheat foot button survives on WaterfallHome.
- D-08: Power stays inert stub (red/greyed).

**Densification grammar (C6)**
- D-09: Dense jiib ListRow variant as reusable kit component.
- D-10: 15sp text floor HOLDS. Density from tighter rows, not smaller type.
- D-11: Fit-on-one-page hard goal for Settings + About only (at M text size). Theme/Printers/SysInfo scroll freely.
- D-12: Keyboard fields = densified TokenTextField inline; numeric fields keep numeric keyboard.

**Printers interaction model**
- D-13: Edit/Delete = FootButtonBar mode toggles (edit=neutral, delete=stop/red). ConfirmGuard for delete.
- D-14: 15.2 Settings-IA boundary final. Nothing moves.
- D-15: Printers layout: Focus=active printer card (name, host:port, color-coded state), Field=profile rows, Foot=Add+Edit+Delete+Back.

**Theme editor scope**
- D-16: WR-02 fix = hue wheel + 2D saturation/value square. Persisted colors carry S/V; re-open restores.
- D-17: WR-01 fix = DELETE dead maxItems plumbing (setMaxItems + persist/sanitize/resolve axis).
- D-18: Editor layout = one dense scroll. Sub-pickers stay inline expansions. ThemeScreen wrapper contract intact.
- D-19: Phase-14 WR-02 seedTheme fix — no-active branch collects tupleFlow reactively (flatMapLatest), not one-shot firstOrNull().
- D-20: Dev cycler overlay drag restored — bare-surface drag region so panel relocates while chip taps consume events.

### Claude's Discretion
- Exact dense-row heights/spacing, section grouping, toggle/dropdown control shapes.
- SystemInformationScreen + AboutScreen composition shape.
- System page NavDest shape, back-stack behavior, FloatingEStop/UAT-4 application.
- Home idle list integration of three new rows (ordering within D-06 P24 order, capability gating).
- S/V square implementation details (size, inline vs expanding) within one-dense-scroll rule.
- WR-03 webcam null-key NOT in scope.

### Deferred Ideas (OUT OF SCOPE)
- Real power control (host shutdown/reboot, Moonraker power devices).
- Calibration hub hide-not-grey flip.
- `2026-06-05-bookmarked-macros-density.md` — Macros surface.
- `2026-06-10-webcam-aspect-ratio-overlay-back.md` — Webcam.
- `2026-06-10-fw-retraction-glyph-assignment.md` — Fine-Tune rows.
- Phase-14 WR-03 (webcam null-key).
- Any ship items (Phase 29).
</user_constraints>

---

## Summary

Phase 28 is a codebase-internal UX migration: five C6-exempt settings-cluster screens are restyled to the jiib dense kit, a new `NavDest.System` page is created, and the swipe-up `AppDrawer` (with `SwipeUpAccumulator`) is deleted entirely. Three orphaned drawer-only destinations (Temperature, Console, Fine-Tune) migrate onto the WaterfallHome idle list. Four theme-editor functional bugs are fixed in the same pass.

The work touches roughly 15 files, adds one new NavDest, extends `buildIdleActions` and `HomeAction`, changes the printing shortcut grid, and deletes `AppDrawer.kt`, `SwipeUpAccumulator.kt`, and the swipe-up detection in `AppShell.kt`. The nav spine (NavHost, FOOT_GUN_DESTS, shouldPopToRoot) requires careful extension but not restructuring. The `maxItems` plumbing deletion (D-17) spans `ThemePrefs`, `Profile`, `ThemeResolver`, `AppContainer`, and their tests.

**Primary recommendation:** Wave the work as: (0) NavDest.System + glyph gaps owner-resolved, (1) AppDrawer/SwipeUp deletion + nav wiring, (2) dense kit component + C6 screens rebuild, (3) theme-editor fixes + seedTheme fix, (4) on-device gate.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| System page route | NavHost (AppShell) | NavDest model | New composable block added to NavHost; route is a sealed NavDest object |
| Swipe-up deletion | AppShell | SwipeUpAccumulator | gesture detection lives in AppShell.pointerInput; accumulator is a pure helper |
| AppDrawer deletion | AppShell overlay | AppDrawer.kt (deleted) | Drawer rendered in AppShell Box block; file + its test migrate/delete |
| Home idle list new rows | HomeAction.kt / buildIdleActions | AppShell (reads it) | Pure builder function; new params added |
| C6 dense ListRow variant | designsystem/components/ | COMPONENTS.md | Extends existing Phase-23 kit; same layer |
| Dense screen rebuilds | ui/screen/ | AppShell NavHost | Each screen is a composable NavHost branch |
| maxItems deletion | ThemePrefs + ThemeResolver + Profile | AppContainer, tests | Cross-layer schema change; all 5 layers must be consistent |
| seedTheme reactive fix | AppContainer.seedTheme | (internal) | One-line flatMapLatest → reactive collect |
| Dev cycler drag fix | DevThemeCyclerOverlay.kt | (internal) | Bare-surface pointerInput region added |
| S/V picker | ThemeEditorScreen (inline expansion) | ThemePrefs S/V persist | New UI section + new DataStore keys |
| FloatingEStop on System cluster | AppShell (shell-level, already wired) | System screen composition | Shell-level e-stop already fires on ALL dests when printing; D-06 makes System print-reachable — no new wiring needed |
| Back wiring (System page) | AppShell NavHost popBackStack | FootButtonBar | Same pattern as all other leaf screens |

---

## Standard Stack

This phase uses **zero new external dependencies**. All implementation is pure Kotlin/Compose using the existing project stack.

### Existing Kit in Use

| Component | Location | Role in Phase 28 |
|-----------|----------|------------------|
| `ListRow` / `ListBlock` / `FootButtonBar` | `designsystem/components/` | Dense ListRow variant derives from these; System page + rebuilt screens use them |
| `ScreenScaffold` | `designsystem/layout/` | Wrapper for rebuilt screens |
| `OutlinedControl` | `designsystem/control/` | Foot buttons on all rebuilt screens |
| `ConfirmGuard` | `designsystem/` | Printers Delete mode (D-13) |
| `TokenTextField` | `ui/screen/TokenTextField.kt` | Densified inline field (D-12) |
| `DetailCard` | `designsystem/components/` | Printers Focus = active printer card (D-15) |
| `rememberUnitGrid` | `designsystem/layout/` | System page Focus size cap (D-02) |
| `NavDest` sealed interface | `ui/route/NavDest.kt` | Add `NavDest.System` |
| `HomeAction` / `buildIdleActions` | `ui/route/HomeAction.kt` | Add three new rows (D-05) |
| `DinghyIcons` / `DinghyIcon` | `designsystem/icons/` | Glyph tokens for new rows |
| `LocalTokens` / `fsSp` | `theme/compose/` | All color + font sizing |
| `AppContainer` intent helpers | `di/AppContainer.kt` | All writes via writeScope |
| `brandTint` lockup + jiib drawables | 18.2 assets | System page Focus brand header (D-02) |

**No npm / no external package install.** Android build, no slopcheck needed.

---

## Package Legitimacy Audit

Not applicable — no new external packages. [VERIFIED: direct source inspection]

---

## Architecture Patterns

### System Architecture Diagram

```
WaterfallHome (idle)
  Foot: [Preheat] [System]
  Idle list: Spool? → Files → Move → Extrude → Macros? → Calibration → Outputs? → Webcam?
                    ↑ NEW: Temperature | Console | Fine-Tune (D-05)
  System foot → NavDest.System

NavDest.System (new)
  Focus: brand lockup + version + active printer  [20%H portrait / 40%W landscape]
  Field: dense ListRow rows
    Printers (D-03 order 1)
    Settings
    Theme
    System Info
    About
    Power [stub, red, inert]
  No Gutter — system page is Focus|Field only
  FloatingEStop: shell-level, applies while printing (D-06 makes cluster print-reachable)
  Back: navController.popBackStack()

AppShell NavHost (additions)
  composable<NavDest.System> { SystemPageScreen(...) }       ← NEW
  REMOVED: composable<NavDest.*> → AppDrawer routing
  REMOVED: drawerOpen var, BackHandler(drawerOpen), swipe pointerInput, AppDrawer(...) overlay
  REMOVED: bottom-edge swipe handle bar
  
HomeAction.buildIdleActions (extended)
  NEW params: temperatureEnabled? (always true — always present), consoleEnabled (always true)
  fineTuneEnabled (always true — always present)
  NEW rows: NavDest.Temperature, NavDest.Console, NavDest.FineTune
  REVISED comment: D-07 "printing-only" posture REVOKED (D-05)
  OpenDrawer variant: DELETED

PrintStatusField (printing shortcut grid)
  Tune tile survives
  System tile added: → NavDest.System (D-06)
  onOpenDrawer param: RETIRED, replaced with onNavigateSystem: () -> Unit

ThemeEditorScreen (rebuilt)
  Seed: ColorWheel (hue) stays
  Pool slot pickers: now ColorWheel + S/V square below (inline expansion, D-16)
  Status slot pickers: same S/V upgrade
  maxItems section: DELETED entirely (D-17)
  Dense scroll (D-18): all in one Column.verticalScroll

AppContainer.seedTheme (D-19)
  no-active branch: firstOrNull() → flatMapLatest (mirror active branch)

DevThemeCyclerOverlay (D-20)
  bare-surface drag region added to Column so panel relocates
  chip tap events stay consumed so they block bleeding to parent drag
```

### Recommended Project Structure (new/modified files)

```
ui/route/
├── NavDest.kt              # add NavDest.System; knownNavDests updated
├── HomeAction.kt           # extend buildIdleActions (D-05); delete OpenDrawer variant
ui/shell/
├── AppShell.kt             # delete drawer state/gesture/overlay; add NavDest.System branch;
│                           # delete swipe-suppress entries for deleted screens;
│                           # remove onOpenDrawer call-site;
│                           # System-cluster screens added to screenOwnsEstop? (NO — shell e-stop applies)
├── AppDrawer.kt            # DELETED
├── SwipeUpAccumulator.kt   # DELETED
├── DevThemeCyclerOverlay.kt # D-20 drag-surface fix
ui/systempage/             # new package or ui/screen/
├── SystemPageScreen.kt     # new screen
ui/screen/
├── SettingsScreen.kt       # jiib dense restyle (D-09..D-12)
├── ThemeScreen.kt          # unchanged wrapper (just hosts ThemeEditorScreen)
├── ThemeEditorScreen.kt    # S/V picker (D-16) + maxItems deletion (D-17) + dense scroll (D-18)
├── PrintersScreen.kt       # Focus/Field/Foot with mode-toggle foot bar (D-13/D-15)
├── AboutScreen.kt          # jiib dense restyle, fits one page (D-11)
ui/systeminfo/
├── SystemInformationScreen.kt  # jiib dense restyle, scrolls freely
ui/printstatus/
├── PrintStatusField.kt     # onOpenDrawer → onNavigateSystem; System shortcut in printing grid (D-06)
di/
├── AppContainer.kt         # seedTheme no-active branch fix (D-19)
theme/
├── ThemePrefs.kt           # KEY_MAX_ITEMS removal (D-17) + new S/V keys (D-16)
├── ThemeResolver.kt        # maxItems param removal (D-17)
config/
├── Profile.kt              # maxItems field removal (D-17)
designsystem/components/
├── ListRow.kt              # dense variant (D-09) — may be a new @Composable overload
```

### Pattern 1: Dense ListRow Variant (D-09)

**What:** A `ListRow` overload (or `param: Boolean = false` dense flag) that reduces vertical padding from full-U to C6 spacing, keeping 15sp text floor and translucent-outline fill convention.

**When to use:** Any row inside a C6-exempt screen. The `U`-based sizing is suspended (LAYOUT.md C6 note); dense rows may be sub-1U in height.

**Derivation:**
```kotlin
// Source: docs/ui_design/COMPONENTS.md + LAYOUT.md C6 exemption [VERIFIED: direct file read]
// Standard ListRow uses heightIn(min = uDp) — that constraint is removed for C6.
// Dense variant: fixed vertical padding (e.g. 10.dp) instead of U-derived sizing.
@Composable
fun ListRow(
    dense: Boolean = false,   // <-- new flag; dense = C6 surfaces
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val padV = if (dense) 10.dp else /* U-derived */ ...
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .border(1.5.dp, t.outline)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = padV),
        content = content,
    )
}
```

### Pattern 2: NavDest.System Addition

**What:** New `@Serializable data object System : NavDest` added to the sealed interface; added to `knownNavDests`.

**Integration points:**
- `AppShell.kt`: add `composable<NavDest.System> { SystemPageScreen(...) }` block inside NavHost.
- `PrintStatusField.kt`: idle foot System button and printing shortcut grid both call `onNavigate(NavDest.System)`.
- `WaterfallHome` call site in AppShell: `onOpenDrawer = { drawerOpen = true }` replaced with `onNavigate = { navController.navigate(it) }` (already the existing lambda — the System foot just routes through it).
- `FOOT_GUN_DESTS`: System and its cluster sub-screens are NOT in this set — safe mid-print (D-06 makes them print-reachable).

### Pattern 3: buildIdleActions Extension (D-05)

**What:** Add Temperature, Console, FineTune rows with always-true presence (no capability gate for these three).

**Current signature (from HomeAction.kt):**
```kotlin
fun buildIdleActions(
    spoolmanPresent: Boolean,
    bookmarksExist: Boolean,
    outputsPresent: Boolean,
    webcamEnabled: Boolean,
): List<HomeAction>
```

**New signature:** Add rows for Temperature/Console/FineTune before the `OpenDrawer` removal. The three new rows are unconditionally present (Temperature and Console are always available; FineTune is always available). The D-06 P24 order is extended — new rows insert logically after the existing 8 (owner to confirm exact position at UAT).

**`HomeAction.OpenDrawer` case:** This sealed variant is DELETED. Every call site in `AppShell.kt` that matched `is HomeAction.OpenDrawer -> drawerOpen = true` is replaced with the System navigate path. `PrintStatusStandbyField` already routes through `onNavigate` for destinations and a separate `onOpenDrawer` for the drawer; the `onOpenDrawer` param and all callers die.

**`HomeActionTest`**: Tests for D-06 order and D-08 hide-rules must be updated. The count assertions ("8 destination rows") increase by 3; the order test gains the three new destinations. `OpenDrawer` test case deleted.

### Pattern 4: S/V Picker (D-16)

**What:** After the ColorWheel (which picks hue), inline expansion shows a 2D Saturation/Value square when a slot is being edited. Persisted alongside hue as two new DataStore keys per slot.

**Current state:** `ThemeEditorScreen.kt` uses `Color.hsv(hue, 1f, 1f)` — hardcoded S=1, V=1 (pure, maximally bright). Re-opening restores hue only; S/V drop back to 1.

**Fix scope:**
- `ThemePrefs`: add per-slot S and V DataStore keys (or encode as `argb` — the color IS the persisted value; actually the easiest approach is to persist the full ARGB long already done in `poolOverrides`, and decode S/V back from it on re-open). The existing ARGB-long wire format already carries full color info — the bug is in the READ path decoding to `hsv(hue, 1f, 1f)`. Fix: decode the stored ARGB to HSV triplet on re-open, seed S and V vars from it.
- New `var sat`, `var value` state vars alongside `var slotHue` in the slot-picker composable.
- A 2D square composable (Canvas-drawn: x-axis = saturation 0..1, y-axis = value 1..0 from top; crosshair at current S/V) inline below the ColorWheel.
- `onSettle` writes `Color.hsv(hue, sat, value)` instead of `Color.hsv(hue, 1f, 1f)`.

**Compose canvas S/V square pattern (no external lib needed):** [ASSUMED — pattern from Compose Canvas API knowledge; verify implementation approach at build time]
```kotlin
// Conceptual — the exact implementation is Claude's discretion (D-16)
Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
    // left-to-right gradient: white (S=0) → hue-pure (S=1)
    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
    // top-to-bottom overlay: transparent → black (V: 1→0)
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
    // crosshair at (sat, 1-value) position
}
```

### Pattern 5: maxItems Deletion (D-17)

**What:** Remove the entire `maxItems` axis — it has no write surface and no UI.

**Scope (all files that carry `maxItems`):**
- `ThemePrefs.kt`: remove `KEY_MAX_ITEMS`, `setMaxItems()`, `rawMaxItems` in `sanitizeTuple`, `maxItems` field in `ThemeTuple`, reference in `resetToDefaults()`.
- `ThemeResolver.kt`: remove `maxItems` constructor param, field, and all usages in `apply()`, `bake()`, `compute()`.
- `AppContainer.kt`: remove `maxItems = tuple.maxItems` in `seedTheme` and `resetActiveTheme`.
- `Profile.kt` (`Profile` data class + `StoredProfile`): remove `maxItems` field.
- `ThemePrefsFallbackTest.kt` + other theme tests: remove any `maxItems` assertions.

**Migration safety:** The DataStore key `theme_max_items` will simply be ignored on next read (no crash — the `sanitizeTuple` code that reads it is deleted). Stored profiles with `maxItems` field are sanitized by `StoredProfile.toProfile()` — after the field is removed it will be absent and ignored by kotlinx.serialization (default value convention). [ASSUMED based on kotlinx.serialization lenient decode; verify]

### Pattern 6: seedTheme Reactive Fix (D-19)

**Current buggy code (AppContainer.seedTheme, conceptual):**
```kotlin
// WR-02: the no-active branch is ONE-SHOT
activeThemeTuple.collect { tuple ->
    themeResolver.apply(...)
}
```

**Wait** — examining AppContainer line 650-671 directly: `seedTheme` already collects `activeThemeTuple` reactively with `.collect { ... }`. The `activeThemeTuple` is itself a `flatMapLatest` over `activeProfile`:
```kotlin
val activeThemeTuple: Flow<ThemePrefs.ThemeTuple> =
    activeProfile.flatMapLatest { p ->
        if (p != null) flowOf(p.toThemeTuple()) else themePrefs.tupleFlow
    }
```

When `p == null` (no active profile), this already uses `themePrefs.tupleFlow` reactively. The D-19 bug is actually in `ThemeEditorScreen.kt` line 117:
```kotlin
val tuple = activeProfile?.toThemeTuple() ?: container.themePrefs.tupleFlow.firstOrNull()
```
This `firstOrNull()` is the ONE-SHOT read in the editor's `LaunchedEffect` — it seeds the mirror state vars once and never re-runs if the global theme changes while idle. Fix: collect `container.themePrefs.tupleFlow` in the effect OR use `collectAsStateWithLifecycle` to keep it reactive. [VERIFIED: direct file read of AppContainer.kt lines 616-670 and ThemeEditorScreen.kt line 117]

### Pattern 7: AppDrawer Deletion

**Files to delete:**
- `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt`
- `app/src/main/java/works/mees/dinghy/ui/shell/SwipeUpAccumulator.kt`
- `app/src/test/java/works/mees/dinghy/ui/shell/AppDrawerOutputsGateTest.kt`
- `app/src/test/java/works/mees/dinghy/ui/shell/SwipeUpAccumulatorTest.kt`

**AppShell.kt hunks to remove:**
- `var drawerOpen by remember { mutableStateOf(false) }` (line 165)
- `BackHandler(enabled = drawerOpen) { drawerOpen = false }` (line 510)
- The entire `pointerInput(navBackStackEntry, promptView.visible)` swipe-detect block (lines 526-580)
- The bottom-edge swipe handle `Box` (lines 975-982)
- The `if (drawerOpen) { AppDrawer(...) }` block (lines 963-973)
- All `swipeEnabled`, `suppressSwipe`, `SWIPE_UP_THRESHOLD_PX` references
- `import works.mees.dinghy.ui.shell.AppDrawer` (and SwipeUpAccumulator)

**Swipe-suppress set deletion:** Once the gesture dies the entire `suppressSwipe` val (which lists 20 NavDest cases) disappears. All the per-screen `ScreenScaffold.gutter` Back affordances that existed solely because "scrollable Fields disable the swipe-up drawer" remain as-is (they're the real navigation exit — that rule holds independently of the gesture).

**`HomeAction.OpenDrawer` deletion:**
- Remove `data object OpenDrawer : HomeAction` from the sealed interface.
- Remove the matching `when` branch in `PrintStatusStandbyField` that calls `onOpenDrawer()`.
- Remove `onOpenDrawer` parameter from `PrintStatusStandbyField` and `PrintStatusField`.

**`PrintStatusField.kt` changes:**
- Idle foot System button: `onClick = onOpenDrawer` → `onClick = { onNavigate(NavDest.System) }` — but `onNavigate` already exists; simplest is to route `FootSystem` through the existing `onNavigate` lambda.
- Printing shortcut grid: add a System tile calling `onNavigate(NavDest.System)` (D-06).

### Anti-Patterns to Avoid

- **Don't add `NavDest.System` to `FOOT_GUN_DESTS`** — the System cluster is intentionally print-reachable (D-06). Settings changes mid-print are legitimate.
- **Don't add NavDest.System to the `screenOwnsEstop` set** — the shell-level FloatingEStop should appear on the System page and its cluster when printing (the user might navigate there during a print and must always have the e-stop reachable).
- **Don't use `rememberCoroutineScope()` for any persistence writes** — all writes through `container.writeScope` intent methods. The existing screen code mostly gets this right; verify every new toggle row in the dense rebuild.
- **Don't hardcode px for dense row heights** — C6 exempt from U, but still uses dp, not px.
- **Don't make the System page's Focus a live telemetry surface** — D-02 is STATIC (brand lockup + version string from `BuildConfig.VERSION_NAME` + active printer name from `container.activeName`). No connection state, no live printer data.
- **Don't invent glyphs for Temperature, Console, FineTune idle-list rows** — D-05 glyph choices are owner decisions. Research reveals `LauncherTemperature` and `LauncherConsole` ARE already registered in `DinghyIcons` (`thermostat` and `terminal`). Fine-Tune does NOT have a `LauncherFineTune` token — only `FineAllerTuneHub`-style tokens exist for the hub. **Flag for owner confirmation before building.**

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Dense ListRow styling | New bespoke component | Extend existing `ListRow` with `dense: Boolean` param | One visual family; conformance sweep can target one class |
| S/V square color picker | External library | Custom Compose `Canvas` draw | No library needed; pattern is ~30 lines; no new minSdk risk |
| Confirm guard for Delete | Custom dialog | Existing `ConfirmGuard` composable | Already token-themed, tested, and used by several screens |
| Active printer card | New component | `DetailCard` from Phase-23 kit | Already carries the correct fill/border/color-coding pattern |
| Process-death-safe persistence | composition scope writes | `container.writeScope` + intent helpers | [[dinghy-compose-write-scope-cancellation]] — proven trap |

---

## Icon Inventory (D-05 glyph gate)

The drawer's `DRAWER_TILES` list is the authoritative source of glyph assignments for destinations that used to be drawer-only. For the three orphan destinations rehoming to the idle list:

| Destination | Drawer glyph | `DinghyIcons` token | LauncherXxx token? | Status |
|-------------|-------------|---------------------|-------------------|--------|
| Temperature | `thermostat` | `LauncherTemperature` (line 49 of DinghyIcons.kt) | YES | REGISTERED — safe to use |
| Console | `terminal` | `LauncherConsole` (line 62 of DinghyIcons.kt) | YES | REGISTERED — safe to use |
| Fine-Tune | `instant_mix` (DrawerTileSpec) | No `LauncherFineTune` found | NO | MISSING — STOP AND ASK owner before building |

**For the System page rows**, the drawer tile glyphs map directly:
| Row | Drawer glyph | Source token | Status |
|-----|-------------|--------------|--------|
| Printers | `cable` | `DrawerTileSpec` literal | Not in DinghyIcons as Launcher* — row glyph NEEDED (ask or use DrawerTileSpec glyph directly if icon-law allows) |
| Settings | `settings` | `DrawerTileSpec` literal | Same |
| Theme | `palette` | `DrawerTileSpec` literal | Same |
| System Info | `pulse_alert` | `DinghyIcons.SysInfoTile` (registered) | REGISTERED |
| About | `info` | `DrawerTileSpec` literal | Not in Launcher* tokens |
| Power | `power_settings_new` | `DrawerTileSpec` literal (danger=true) | Inert stub; glyph already decided |

**Wave 0 MUST:** The Fine-Tune `LauncherFineTune` glyph gap requires owner input before Wave 0 can close. System page row glyphs (`cable`/`settings`/`palette`/`info`) are used verbatim from DrawerTileSpec — the planner should confirm whether new DinghyIcons tokens are required for them or if they are used as literal strings (precedent: DrawerTileSpec already used literals, so registering them as LauncherXxx tokens would be consistent with icon-law). Flag as owner decision.

---

## Common Pitfalls

### Pitfall 1: D-17 Deletion Leaves Test Golden Files Stale

**What goes wrong:** `ThemePrefsFallbackTest` and `PaletteGoldenTest` reference `maxItems` in their assertion paths. After deletion the test code fails to compile.

**Why it happens:** `maxItems` is baked into `ThemeTuple.maxItems`, `Profile.maxItems`, and the `sanitizeTuple` test helper. Tests that construct these objects with a named `maxItems` param will break.

**How to avoid:** Grep for `maxItems` across the entire test sourceset before execution; fix all references in a single commit.

**Warning signs:** Compiler error "unresolved reference: maxItems" in test files.

### Pitfall 2: HomeAction Tests Fail on Updated Counts

**What goes wrong:** `HomeActionTest.allCapabilitiesPresent_fullIdleList` asserts `assertEquals("Full list must have 8 destination rows", 8, dests.size)`. After D-05 adds 3 rows, this fails.

**Why it happens:** Test was written before Phase 28 extended the list.

**How to avoid:** Update `HomeActionTest` in the same plan that modifies `buildIdleActions`. Count becomes 11 (all-capable). Order test must add Temperature/Console/FineTune in owner-confirmed positions.

### Pitfall 3: OpenDrawer Removal Leaves Call-Site Dead Code

**What goes wrong:** `PrintStatusStandbyField` has `onOpenDrawer: () -> Unit` param and a `when(action) { is HomeAction.OpenDrawer -> onOpenDrawer() }` branch. After `OpenDrawer` is removed from the sealed interface, the `when` branch is unreachable and the param is dead.

**Why it happens:** Three-file coupling (HomeAction.kt, PrintStatusField.kt, AppShell.kt) must be updated atomically.

**How to avoid:** Update all three in a single plan/wave. The `when` branch becomes a compile error once `OpenDrawer` is removed (exhaustive `when` on sealed interface — good).

### Pitfall 4: Swipe-Suppress Set References Dead Routes

**What goes wrong:** After deleting AppDrawer+SwipeUp the entire `suppressSwipe` block is gone. But if a developer leaves an import or a stale reference to `SwipeUpAccumulator` or `AppDrawer`, the compile fails.

**How to avoid:** Delete both files first, then fix AppShell.kt. Kotlin's import hygiene makes stale references compile errors — let the compiler flag them.

### Pitfall 5: seedTheme Fix in Wrong Layer

**What goes wrong:** D-19 fix target is actually `ThemeEditorScreen.kt` line 117 (`tupleFlow.firstOrNull()` in the `LaunchedEffect`), NOT `AppContainer.seedTheme`. `AppContainer.seedTheme` already collects `activeThemeTuple` reactively (flatMapLatest over activeProfile — VERIFIED). Fixing the wrong place (AppContainer) changes nothing.

**Why it happens:** The Phase-14 review TODO described the bug as "AppContainer.seedTheme no-active branch is one-shot" — but since then the reactive flow was wired and the remaining one-shot is in the editor's local LaunchedEffect seed.

**How to avoid:** Read AppContainer.kt lines 616-671 and ThemeEditorScreen.kt line 117 before writing the fix. The correct fix is in `ThemeEditorScreen` — replace `container.themePrefs.tupleFlow.firstOrNull()` with a reactive collect (e.g. `collectAsStateWithLifecycle`) or restructure the `LaunchedEffect` to re-collect on changes.

### Pitfall 6: System Page FloatingEStop Double-Render

**What goes wrong:** If `SystemPageScreen` renders its own FloatingEStop (like FineTune/Temperature/Spool do), the shell-level e-stop also fires → two e-stop buttons.

**Why it happens:** AppShell's `screenOwnsEstop` suppresses the shell-level e-stop only for `NavDest.FineTune`, `Temperature`, and `Spool`. If System page and sub-screens add themselves to that set AND render their own e-stop, the pattern is maintained. If they add themselves WITHOUT rendering their own, the e-stop disappears mid-print.

**How to avoid:** Do NOT add `NavDest.System` / the cluster screens to `screenOwnsEstop`. Let the shell-level FloatingEStop handle e-stop on these screens. System page is read-only and has no per-screen e-stop placement requirement. The shell-level overlay (top-start, 14dp padding) is correct.

### Pitfall 7: Dense TokenTextField Breaks Existing Tests

**What goes wrong:** If the densified `TokenTextField` changes its default height via a param, call sites that use the default may produce layout changes that break existing UI snapshot tests.

**How to avoid:** Make density a new `dense: Boolean = false` param with the old behavior as default. Existing call sites are unchanged.

### Pitfall 8: Profile.maxItems Removal Breaks DataStore Migration

**What goes wrong:** Stored `StoredProfile` JSON blobs in DataStore contain `"maxItems": 4`. After the field is removed, deserialization might fail if `kotlinx.serialization` does not ignore unknown JSON keys.

**How to avoid:** `@Serializable` classes with `kotlinx.serialization` in lenient/default mode DO ignore unknown keys when using `Json { ignoreUnknownKeys = true }` (which the project uses for Moonraker JSON-RPC handling). Verify the ProfileStore's Json instance carries `ignoreUnknownKeys = true`. If it does, stored `maxItems` fields are silently ignored on next read. [ASSUMED: verify ProfileStore Json config before executing D-17]

---

## Runtime State Inventory

Not applicable — this is a UI migration/restyle phase, not a rename/refactor of persisted identifiers. No stored collection names, process registrations, or external service configs reference "AppDrawer" or "swipe-up" by name.

The only DataStore schema change is removing `KEY_MAX_ITEMS` (key `"theme_max_items"`) from reads. The key is never written after deletion; existing stored values are silently ignored. No migration script needed. [VERIFIED: ThemePrefs.kt read — KEY_MAX_ITEMS is a `intPreferencesKey("theme_max_items")`; removal means the stored value persists but is never read]

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4 + kotlinx-coroutines-test (host), instrumented androidTest (Compose) |
| Config file | None — Gradle task-based |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest :app:assembleRelease --no-daemon"` |

### Success Criterion → Test Map

| SC | Behavior | Test Type | Command / Method | File Status |
|----|----------|-----------|------------------|-------------|
| SC-1 | Settings/Theme/Printers/SysInfo/About restyled + System page, owner-approved | on-device UAT | Manual flox inspection | N/A — UAT |
| SC-1 | `@Preview` matrix (6 combos + fsL) for each rebuilt screen + SystemPageScreen | compile + Studio | Build + `assembleDebug` | Wave 0 gap: new preview files |
| SC-2 | C6 densification — no row uses `<15sp` text | host unit | grep + compile guard | Wave 0 gap: fsSp floor test |
| SC-3 | Settings-IA boundary final; nothing moves (D-14) | manual code review | N/A | N/A |
| SC-4 | `fsSp` scale honored; correct rotation; token purity | host unit (ThemePrefsFallbackTest) + compile | existing suite | Existing (may need update for maxItems removal) |
| SC-5 | No functional regressions: connection edit, theme apply, printer add/remove, sysinfo | on-device smoke | Manual flox | N/A — UAT |
| SC-5 (host) | `buildIdleActions` order + gating correct after D-05 extension | host unit | `:app:testDebugUnitTest --tests *HomeActionTest` | Wave 0 gap: update existing test |
| SC-5 (host) | `AppDrawer`/`SwipeUpAccumulator` tests deleted cleanly (not stale) | compile | `:app:compileDebugKotlin` | Wave 1 deletion |
| SC-5 (host) | `maxItems` fully removed — no orphan references | compile | `assembleDebug` | Wave 2-3 |
| SC-5 (host) | `NavDest.System` in `knownNavDests`; round-trip serialization | host unit | `NavDestRoundTripTest` (extend existing) | Wave 0 gap |
| SC-5 (host) | seedTheme reactive (no one-shot) — idle theme edit re-emits | host unit | new `AppContainerSeedThemeTest` or extend existing | Wave 0 gap |
| SC-5 (host) | PrintersScreen mode-toggle logic (Edit/Delete arm/disarm, ConfirmGuard trigger) | host unit | new `PrintersModeToggleTest` | Wave 0 gap |

### Sampling Rate

- **Per task commit:** `gw.bat :app:testDebugUnitTest --no-daemon` (full host unit suite, ~30s)
- **Per wave merge:** `gw.bat :app:assembleRelease :app:testDebugUnitTest --no-daemon`
- **Phase gate:** Full suite green + owner on-device UAT on flox (LineageOS 18.1 / API 30)

### Wave 0 Gaps (test infrastructure before implementation)

- [ ] Update `HomeActionTest` — new row count (8→11), new order assertions, delete `OpenDrawer` case
- [ ] Extend `NavDestRoundTripTest` (or `StartDestMappingTest`) — add `NavDest.System` to round-trip coverage
- [ ] New `PrintersModeToggleTest` — pure host test for the D-13 mode-toggle state machine (arm edit, arm delete, disarm, ConfirmGuard trigger)
- [ ] New or extended seedTheme reactive test — verify `tupleFlow.firstOrNull()` is gone from `ThemeEditorScreen` and the idle branch re-emits
- [ ] `ThemePrefsFallbackTest` updated — remove all `maxItems` assertions before D-17 deletion
- [ ] Preview files: `SystemPagePreviews.kt`, `SettingsPreviews.kt`, `ThemeEditorPreviews.kt`, `PrintersPreviews.kt`, `SysInfoPreviews.kt`, `AboutPreviews.kt` — 6-combo + fsL matrix each (PREVIEW_AND_TOKENS.md LAW)

---

## Security Domain

`security_enforcement: true`, ASVS Level 1 applies.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No new auth paths |
| V3 Session Management | No | No new session handling |
| V4 Access Control | No | No new permission checks |
| V5 Input Validation | YES | TokenTextField inline in C6 screens (host/port/API-key/babystep-layers); existing validation patterns |
| V6 Cryptography | No | No new crypto |
| V7 Error Handling | Partial | Theme settings must degrade gracefully (fail-safe ThemePrefs contract preserved) |

### Input Validation (V5) — Settings Screens

The densified TokenTextField for connection fields (host, port, API key) and babystep-layers inherits the existing validation from `PrintersScreen` / `SettingsScreen`. Key rules already in place [VERIFIED: SettingsScreen.kt inspection]:
- Babystep layers: `digits.toIntOrNull()?.let { container.setBabystepLayers(it) }` — invalid input silently not persisted
- API key: `resolveApiKeyEdit` — blank preserves stored key, explicit clear writes null, never pre-fills raw key
- Port: numeric keyboard (`KeyboardType.Number`) — existing constraint
- Host: free text; no shell-injection risk (passed to OkHttp URL builder which URL-encodes)

**No new security surface introduced.** The System page Focus is read-only (BuildConfig version, `container.activeName`). Theme editor writes are internal DataStore operations.

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Stored DataStore corruption after maxItems removal | Tampering (inadvertent) | `sanitizeTuple` / `StoredProfile.toProfile()` fail-safe — unknown keys ignored |
| API key exposure via `toString()` | Information Disclosure | `Profile.toString()` already masks to `***` [VERIFIED: Profile.kt line 61] |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | kotlinx.serialization ignores removed `maxItems` field in stored ProfileStore JSON blobs (lenient decode) | Pitfall 8, D-17 | Stored profiles might fail to deserialize; app would fall back to default profile, losing user config |
| A2 | S/V square is ~30 lines of Canvas draw (no external library needed) | Pattern 4 | If Canvas approach is hard, may need more planning time; still no library needed |
| A3 | `ProfileStore.Json` instance carries `ignoreUnknownKeys = true` | Pitfall 8 | Same as A1 |
| A4 | Fine-Tune has no `LauncherFineTune` DinghyIcons token (searched `instant_mix` and `LauncherFineTune`) | Icon Inventory | If one exists under a different name, no new owner ask is needed |

---

## Open Questions

1. **Fine-Tune idle-list glyph (D-05)**
   - What we know: `LauncherTemperature` (`thermostat`) and `LauncherConsole` (`terminal`) are registered. `DrawerTileSpec` uses `instant_mix` for Fine-Tune. No `LauncherFineTune` token exists in `DinghyIcons.kt`.
   - What's unclear: Is `instant_mix` (from DrawerTileSpec) acceptable as the home-list glyph, or does the owner want a different glyph for the "calm" idle-list context vs. the drawer?
   - Recommendation: **Wave 0 blocker** — ask owner before building. The D-05 decision text says "if any of the three needs a glyph not already registered, STOP and ASK."

2. **System page row glyphs — new DinghyIcons registration required?**
   - What we know: `cable`/`settings`/`palette`/`info` are used as literal strings in `DrawerTileSpec` but are NOT registered as `Launcher*` or `System*` DinghyIcons tokens (the `DinghyIcons` registry only has `SysInfoTile` for System Info). The icon-law says "every row's icon MUST reference a DinghyIcons registry token."
   - What's unclear: Do System-page rows need new tokens registered (e.g., `SystemRowPrinters`, `SystemRowSettings`…) or can they reuse the literal `DrawerTileSpec` glyphs via the existing ligature mechanism?
   - Recommendation: Register them as DinghyIcons tokens before Wave 1 (the drift-guard test will catch any unregistered glyph). Ask owner to confirm the glyphs are correct for the new row context.

3. **D-05 idle-list ordering for Temperature / Console / Fine-Tune**
   - What we know: D-06 (P24) order is Spool → Files → Move → Extrude → Macros → Calibration → Outputs → Webcam. D-05 says these three join "the home idle list" but doesn't specify exact positions.
   - Recommendation: Propose a logical order (e.g., after Calibration, before Outputs: …Calibration → Temperature → Console → Fine-Tune → Outputs → Webcam) and let the owner judge at UAT.

4. **ProfileStore ignoreUnknownKeys**
   - What we know: the `kotlinx.serialization` Json config on the Moonraker JSON-RPC side uses lenient settings.
   - What's unclear: does `ProfileStore` use the same Json instance?
   - Recommendation: grep `ProfileStore.kt` for `Json {` block before executing D-17. If it lacks `ignoreUnknownKeys = true`, add it before removing the field.

---

## Environment Availability

Step 2.6: SKIPPED (no external dependencies — pure Kotlin/Compose, builds Windows-side via `gw.bat`). The existing build environment is confirmed from CLAUDE.md: JDK 21 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`, Android SDK at `E:\Android\Sdk`.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Swipe-up drawer as sole nav | NavHost + FootButtonBar + System page | Phase 24 NavHost, Phase 28 drawer retirement | Gesture conflicts eliminated; nav is explicit and reliable |
| `when(dest)` hub-and-spoke | Navigation-Compose NavHost | Phase 24 | Back-stack owned by NavHost |
| `maxItems` as configurable pool size | Hardcoded 4-slot pool | D-17 (Phase 28) | Dead plumbing deleted; pool size not user-configurable |
| Hue-only color picker | Hue + S/V picker | D-16 (Phase 28) | Full color freedom for pool/status custom colors |

**Deprecated in this phase:**
- `HomeAction.OpenDrawer` — removed; System foot uses `NavDest.System` directly
- `AppDrawer` component — the entire file is retired; the `DRAWER_TILES` registry served as the destination inventory (now dissolved)
- `SwipeUpAccumulator` — pure helper, deleted with the gesture
- `ThemePrefs.KEY_MAX_ITEMS` / `ThemePrefs.setMaxItems()` — dead plumbing, deleted

---

## Sources

### Primary (HIGH confidence)
- Direct codebase inspection via Read tool:
  - `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` — DRAWER_TILES registry, glyph ownership
  - `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` (lines 510-580, 900-1027) — swipe-up wiring, drawer overlay, e-stop logic, suppress-set
  - `app/src/main/java/works/mees/dinghy/ui/shell/SwipeUpAccumulator.kt` — accumulator contract
  - `app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt` — buildIdleActions, OpenDrawer
  - `app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt` — FOOT_GUN_DESTS, knownNavDests, shouldPopToRoot
  - `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt` — toggles, layout, writeScope usage
  - `app/src/main/java/works/mees/dinghy/ui/screen/ThemeScreen.kt` — wrapper contract
  - `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt` (lines 1-220) — hue-only picker, firstOrNull() bug, maxItems absent from UI
  - `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt` — current mode/grid shape
  - `app/src/main/java/works/mees/dinghy/ui/shell/DevThemeCyclerOverlay.kt` — drag wiring, chip consume
  - `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` (lines 600-720) — seedTheme reactive confirm, maxItems usage
  - `app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt` — maxItems keys/methods, KEY_MAX_ITEMS
  - `app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt` — maxItems field
  - `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — LauncherTemperature, LauncherConsole, FootSystem, SysInfoTile presence
  - `app/src/test/java/works/mees/dinghy/ui/route/HomeActionTest.kt` — count/order assertions to update
  - `app/src/test/java/works/mees/dinghy/ui/shell/AppDrawerOutputsGateTest.kt` — file to delete

- Design LAW files:
  - `docs/ui_design/COMPONENTS.md` — component catalog, ListRow/ListBlock/FootButtonBar
  - `docs/ui_design/LAYOUT.md` — C6 exemption, UAT-1..5 rules
  - `.claude/skills/sketch-findings-dinghy-display/SKILL.md` — post-Phase-26 UAT rules
  - `.claude/skills/sketch-findings-dinghy-display/references/foundations.md` — unit U, intent colors
  - `.planning/phases/28-system-settings-cluster/28-CONTEXT.md` — all D-## decisions

---

## Metadata

**Confidence breakdown:**
- Swipe-up deletion scope: HIGH — all files verified by direct read
- maxItems deletion scope: HIGH — all 5 files identified and confirmed
- S/V picker implementation: MEDIUM — Canvas pattern is known; exact composable structure is Claude's discretion
- seedTheme fix location: HIGH — ThemeEditorScreen line 117 is the one-shot; AppContainer is already reactive
- Icon gap (FineTune): HIGH — confirmed LauncherFineTune absent from DinghyIcons.all

**Research date:** 2026-06-12
**Valid until:** Phase 28 execution (codebase-internal; stable until next merge)
