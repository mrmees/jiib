# Coding Conventions

**Analysis Date:** 2026-06-08

## Naming Patterns

**Files:**
- Screen composables: `<Name>Screen.kt` (e.g. `PrintStatusScreen.kt`, `MoveScreen.kt`, `TemperatureScreen.kt`)
- State holders: `<Name>Holder.kt` (e.g. `FineTuneHolder.kt`, `PrintStatusHolder.kt`)
- View-models (holder output): `<Name>Vm.kt` or inner data class `<Name>Vm` (e.g. `FineTuneVm.kt`)
- Design system primitives: descriptive noun `ScrubberPage.kt`, `NumpadPage.kt`, `ConfirmGuard.kt`, `OutlinedControl.kt`
- Icons: `DinghyIcon.kt` (type), `DinghyIcons.kt` (registry), `DinghyIconView.kt` (render primitive)
- Tests: `<Target>Test.kt` mirrors production file (e.g. `FineTuneHolderTest.kt`, `JsonRpcClientTest.kt`)

**Functions:**
- Composables: `PascalCase` (e.g. `PrintStatusScreen`, `ScrubberPage`, `ConfirmGuard`)
- Private helpers: `camelCase` (e.g. `intentColor`, `fractionFromX`, `settleDispatchCount`)
- Extension helpers on `ThemeTokens`: `seriesColor(n)`, `fsSp(base, fs)` (flat package-level functions)
- Test functions: backtick descriptive strings `fun \`a drag dispatches exactly once\`()`

**Variables:**
- Tokens local: `val t = LocalTokens.current` — single-letter `t` is the house convention everywhere
- State: `by collectAsStateWithLifecycle()` assigned to camelCase val (e.g. `val vm by holder.vm.collectAsStateWithLifecycle()`)
- DataStore-backed: `val themePrefs: ThemePrefs`, `val connectionStore: ConnectionStore`

**Types:**
- Pure immutable state: `data class` with KDoc, all fields defaulted (e.g. `PrinterState`, `FineTuneVm`)
- Token set: `@Immutable data class ThemeTokens(...)` in `app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt`
- Sealed interfaces for discriminated unions: `ScrubberActions`, `IconRef`, `ScrubPhase`
- Enums for fixed sets: `Intent`, `PaletteMode`, `FontScale`, `ThemeBase`
- Registry singletons: `object DinghyIcons`, `object CommandRegistry`

---

## Compose State Patterns

**Primary collection pattern** — `collectAsStateWithLifecycle` is used exclusively (never bare `collectAsState`):
```kotlin
val vm by holder.vm.collectAsStateWithLifecycle()
val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
val tokens by tokensFlow.collectAsStateWithLifecycle(initialValue = TokensDark)
```
File reference: `app/src/main/java/works/mees/dinghy/theme/compose/DinghyTheme.kt:44`

**State hoisting** — Compose state that must survive navigation / Splash blip is hoisted out of the composable into a plain `class ShellNavState` using `mutableStateOf` and `mutableStateListOf`:
```kotlin
class ShellNavState {
    var dest by mutableStateOf(startDest ?: Dest.PrintStatus)
    val backStack = mutableStateListOf<Dest>()
    var macroPopupFor by mutableStateOf<MacroVm?>(null)
}
```
File: `app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt`

**`@Immutable` and `@Stable`** — applied at the token/model layer, not screen layer:
- `@Immutable data class ThemeTokens(...)` — covers the `List<Color> pool` field so Compose skips recomposition
- `@Immutable data class Directional(...)` — nested stable subtype
- `@Immutable data class PromptModel(...)` / `PromptButton(...)` etc. in `app/src/main/java/works/mees/dinghy/prompt/PromptModel.kt`
- `@Immutable data class ComposeSceneState(...)` in bench code
- `PrinterState` is a plain `data class` with NO Compose annotations (headless; stability handled at the UI boundary)

**`derivedStateOf`** — not observed in screen-level code; the pattern is to derive in the holder (JVM, `StateFlow.map`) rather than inside composition. Compose-side derivation exists in geometry-sensitive spots (scrubber fraction is computed inline from `working` and `range` — not `derivedStateOf` because the values are local mutable state, not externally-collected flows).

**`ImmutableList`** — NOT used (kotlinx-collections-immutable is not a declared dependency). `List<Color>` carried by `@Immutable ThemeTokens` is covered by the enclosing stability annotation per KDoc comment at `ThemeTokens.kt:75-78`. Per CLAUDE.md "no `kotlinx-collections-immutable` dependency added" is a deliberate RESEARCH/A3 decision.

---

## Design-System Token Conventions (LAW)

**THEME-01: Never a raw color.** Every color in every Compose screen comes from `LocalTokens.current` role tokens:
```kotlin
val t = LocalTokens.current
// Correct
color = t.text
border = BorderStroke(2.dp, t.outline)
background = t.bg
// Correct — data pool
val nozzleColor = t.seriesColor(0)
```
File: `app/src/main/java/works/mees/dinghy/theme/compose/LocalTokens.kt`

**The `t` alias** is the universal local alias: every composable reads `val t = LocalTokens.current` as its first line and accesses ALL role tokens through `t.*`.

**Token roles and their field names on `ThemeTokens`:**
| Design-law token | Kotlin field | Semantic role |
|-----------------|--------------|---------------|
| `--bg` | `t.bg` | App background |
| `--surface` | `t.surface` | Card/screen body |
| `--text` | `t.text` | Strong text |
| `--text-2` | `t.text2` | Muted text |
| `--text-3` | `t.text3` | Faint/disabled text |
| `--outline` | `t.outline` | Neutral control border |
| `--accent` | `t.accent` | Physical command (motion, heat) |
| `--accent-line` | `t.accentLine` | Accent outline |
| `--accent-soft` | `t.accentSoft` | Accent fill scrubber |
| `--go` | `t.go` | Accept/commit (green) |
| `--stop` | `t.stop` | Danger/cancel (red) |
| `--heat` | `t.heat` | Proceed-at-peril / caution (amber) |
| `--hair` | `t.hair` | Decorative hairline |
| `--r-ctrl` | `t.rCtrl` | Control corner radius |
| `--r-card` | `t.rCard` | Card corner radius |
| `--fs` | `t.fs` | Text-size multiplier |
| data pool | `t.pool[i]` | N-th data-series color |
| directional | `t.directional.xy` etc. | Motion/temp identity |

**`seriesColor(n)` helper** — the sole way to pick a data-series color (temp trace, sensor legend); wraps the pool with mode-aware fallback. Source: `app/src/main/java/works/mees/dinghy/theme/SeriesColor.kt`.

**THEME-01 carve-outs** (legitimate raw colors):
- `Color(argb)` for LED output swatch — the swatch IS a live hardware color, not a UI role
- SpoolGlyph filament spiral tint — live filament color from Spoolman data
- `PromptStyleColors.kt` — server-authored hex colors in the KlipperScreen-compatible prompt UI

---

## `fsSp` Text-Scaling Helper (THEME-02)

**Contract:** `fsSp(baseSp: Float, fs: Float): Float = baseSp * fs`
Source: `app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt:199`

**Usage everywhere:**
```kotlin
fontSize = fsSp(18f, t.fs).sp      // button labels
fontSize = fsSp(56f, t.fs).sp      // scrubber hero value
fontSize = fsSp(15f, t.fs).sp      // metadata floor
sizeSp = fsSp(40f, t.fs)           // MaterialSymbol glyph size
```

**Floor sizes (the RECURRING-TRAP scale from memory):**
- 15sp — metadata floor (smallest permitted)
- 17–18sp — body / button labels
- 20–22sp — section titles
- 26–30sp — tabular stats
- 48–72sp — focus-hero numbers (ScrubberPage, probe Z offset)

`@Preview(fontScale = X)` is a **no-op** in this app — `DinghyTheme` pins OS `fontScale = 1f` at the Compose root. To preview large text use `fsLargeSeed` with `PreviewBox`:
```kotlin
@Nexus7Previews
@Composable
fun MyScreenFsLargePreview() = PreviewBox(fsLargeSeed) { MyScreen(...) }
```
Source: `app/src/main/java/works/mees/dinghy/preview/PreviewTheming.kt:99-102`

---

## DinghyIcon System

**Architecture:** `DinghyIcon(primary: IconRef, alternate: String)` — sealed `IconRef` is either `IconRef.Ligature(name)` (Material Symbols font) or `IconRef.Drawable(resId)` (VectorDrawable).

**Registry:** `DinghyIcons` object in `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — all semantic tokens with hand-rolled `DinghyIcons.all: List<DinghyIcon>` for drift-guard testing.

**Render primitive:** `DinghyIconView(icon, tint, sizeDp)` in `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIconView.kt` — single call handles both `Ligature` and `Drawable` arms. `sizeDp` is the universal unit (ligature `sp` derived via `dpToSp`, valid only under the `fontScale=1f` pin).

**Raw `MaterialSymbol(name, tint, sizeSp)` is still used directly** in calibration screens and many older screens — this is a known Phase-18.1/Phase-22 backlog (D-03 deferred backfill). New code must use `DinghyIconView`.

**Policy:**
- Ligature = default for any glyph in the bundled Material Symbols v2.944 font
- `IconRef.Drawable` = ONLY for custom printer-domain glyphs Material Symbols lacks (`nozzle`, `heat_bed`, `launcher_spool`)
- **NEVER invent or choose an icon independently** — see `[[dinghy-never-pick-icons-ask]]`

**Drift guard:** `DinghyIconsTest` enforces non-blank alternates, unique alternates, unique `primary` IconRefs (with explicit allow-list), and that `Drawable`-backed entries are only the three sanctioned customs.
File: `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt`

---

## Button Intent Convention

**`Intent` enum** in `app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt`:
```
Intent.Neutral  → t.outline       (plain nav, secondary actions, Back)
Intent.Accent   → t.accentLine    (physical command: jog, heat, fan)
Intent.Warn     → t.heat          (proceed-at-peril: disable motors, SAVE_CONFIG restart)
Intent.Danger   → t.stop          (stop/cancel/destructive: cancel print, delete)
Intent.Go       → t.go            (accept/commit: Apply, Set, Save)
```

**C7 exception:** Back is `Neutral` ONLY for pure navigation (no state change). A Back that **discards pending input** uses `Danger` (e.g. `ScanConfirmCard` reject, `MeasuredWeightPage` discard).

**`OutlinedControl`** is the house button primitive. Touch floor: `heightIn(min = 64.dp)` — hard-coded only in leaf controls, never in scaffolds.

---

## Layout Convention (Focus / Field / Gutter)

Every screen is built on `ScreenScaffold` (`app/src/main/java/works/mees/dinghy/designsystem/layout/ScreenScaffold.kt`):
- `focus` — primary visual item; caller wraps content in `Modifier.aspectRatio(1f)` for sacred squares
- `field` — divisible info/control area
- `gutter` — primary action row on the shared grid

Orientation is detected with `BoxWithConstraints` (`maxWidth > maxHeight`), not `LocalConfiguration`. Portrait stacks focus/field/gutter; landscape splits focus|field 50/50 with full-width gutter below.

**Sizing:** `weight` / `fillMax*` only. NO hardcoded px for regions. Only `heightIn(min = 64.dp)` (touch floor) and `fsSp` text sizes are sanctioned fixed values.

---

## Error Handling Patterns

**Network / RPC errors** surface through `SharedFlow<DispatchEvent>` events that the shell toasts via `SeverityToast`:
```kotlin
// In screens:
SeverityToast(event, onDismiss = { ... })
```

**`Result`/sealed** used in pure domain code (e.g. `ScrewsTiltResult`, `TiltResult`), not in Compose code.

**No `try/catch` in Compose** — error states are modeled as sealed states in holders/VMs.

**DataStore writes** route through `AppContainer.writeScope` (process-lifetime coroutine scope), never a `rememberCoroutineScope` — composition scope is cancelled on navigation, silently dropping writes on slow flash.
File: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`

---

## Logging

No structured logging framework. `Log.d` / `Log.w` used sparingly in production network code (e.g. reconnect events). Tests use direct assertions, never log-scraping.

---

## Preview Convention (Phase 18+)

**`PreviewBox(tuple) { ... }`** wraps every new screen preview — uses the live `DinghyTheme(tokensFlow = flowOf(ThemeResolver().bake(tuple)))` path, not a hand-rolled palette.

**Six canonical combos:** `colorfulDark`, `colorfulLight`, `simpleDark`, `simpleLight`, `highContrastDark`, `highContrastLight` from `app/src/main/java/works/mees/dinghy/preview/PreviewTheming.kt`.

**`@Nexus7Previews`** — multipreview annotation providing Nexus 7 landscape + portrait geometry. Defined in `app/src/main/java/works/mees/dinghy/preview/DinghyPreviews.kt`.

**`fsLargeSeed`** — the only way to preview `--fs = L` text overflow (required companion preview per PREVIEW_AND_TOKENS.md).

---

## Inconsistencies and Conformance Gaps

These are divergences from the house patterns that a conformance sweep (Phase 22 or dedicated cleanup phase) should resolve.

### 1. `intentColor()` duplicated in 4+ screens

`OutlinedControl.kt` already has a private `Intent.outlineColor(t)` extension. Four screens define their own private (or `internal`) copies of the same mapping:
- `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt:202` — `internal fun intentColor`
- `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt:524` — `private fun intentColor`
- `app/src/main/java/works/mees/dinghy/ui/macros/MacroExecutionPopup.kt:352` — `private fun intentColor`
- `app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt:230` — `private fun intentColor`

All four exist because these screens use inline `Box`/`border` construction instead of `OutlinedControl`. The fix is to expose `Intent.outlineColor` as `internal` in `OutlinedControl.kt` and delete the copies.

### 2. Raw `MaterialSymbol(name, tint, sizeSp)` calls instead of `DinghyIconView`

The `DinghyIconView` + `DinghyIcons` registry was introduced in Phase 18.1 for the exemplar screens. Pre-18.1 screens still call `MaterialSymbol` directly with raw ligature string literals. Affected files (partial list):
- `app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt`
- `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt`
- `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt`
- `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt`
- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt`
- `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt`
- `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt`

These are tracked as D-03 "Phase-22 backfill" in `DinghyIcons.kt`. New phases must use `DinghyIconView`.

### 3. Calibration screens use bespoke button components, not `OutlinedControl`

`CalibrationHubScreen.kt`, `ProbeCalibrateScreen.kt`, `BedMeshScreen.kt`, `ScrewsTiltScreen.kt`, and `TiltScreen.kt` all define local `HubActionControl`/`CalibActionControl` composables that re-implement the `Box + border + heightIn + Text` pattern. They respect `fsSp` and token colors but bypass `OutlinedControl`. The 64dp floor is correctly applied via `heightIn(min = 64.dp)`.

### 4. `ScrubberPage` not used by Fine-Tune / Temperature numeric input paths

Fine-Tune group screens (`ExtrusionScreen.kt`, `MotionScreen.kt`, `FwRetractionScreen.kt`) open a full-screen `ScrubberPage` via the holder's edit state. `TemperatureScreen.kt` also opens `ScrubberPage` for setpoint editing (TEMP-02). However the jog **distance** selector in `MoveScreen.kt` is a bespoke row of `OutlinedControl`s (not `ScrubberPage`) — this is intentional per the mockup (D-03, 6-distance selector grid).

### 5. `SystemInformationScreen.kt` uses a fixed `.height(24.dp)` spacer

`app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt:212` has `Box(Modifier.height(24.dp))` as a visual spacer. Minor; Layout.md says no hardcoded px for *regions*, not for decorative spacers. Low priority.

### 6. `TemperatureScreen.kt` uses `1.sp` (hardcoded) for `TextAutoSize.StepBased`

`app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt:379` uses `stepSize = 1.sp` in `TextAutoSize.StepBased`. This is the auto-size step granularity (not a user-visible font size), so it does not violate `fsSp` for content text. Acceptable edge case.

### 7. Calibration + Macros "same-dest re-entry reset" — FIXED in Phase 17

The `ShellNavState.navigateTo` same-destination early-return bug (no hub reset on re-entry) was fixed in Phase 17 via `applyEntryReset(target)`. Both Calibration and Macros share the fix. Not a current gap.

### 8. `DinghyIcons.all` hand-rolled list — requires manual update on new icon additions

Phase 22 readiness: adding a new `val` to `DinghyIcons` requires also adding it to `DinghyIcons.all`. The `DinghyIconsTest.iconRef_isUnique_acrossAllEntries` test will catch a missing entry only if the new entry duplicates an existing glyph. A missing entry that uses a unique glyph silently escapes the test. Low risk for now; a future improvement would use reflection or a compile-time annotation processor.

---

*Convention analysis: 2026-06-08*
