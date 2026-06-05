# Phase 15: Theme System & Settings Redesign - Pattern Map

**Mapped:** 2026-06-05
**Files analyzed:** 18 (4 new code, 9 modified code, 5 test new/extend)
**Analogs found:** 17 / 18 (1 genuinely-new: the color wheel — gesture analog only)

> NATIVE ANDROID (Kotlin + Jetpack Compose + classic Views hybrid, ADR 0001). All package paths below
> are under `app/src/main/java/works/mees/dinghy/` (code) or `app/src/test/java/works/mees/dinghy/`
> (host unit tests). The whole Phase-3 theme substrate is the integration target, NOT a rewrite — the
> seed generator feeds *into* the existing `StateFlow<ThemeTokens>` boundary, which is unchanged.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| **NEW** `theme/Palette.kt` (generator port) | utility (pure math) | transform | `state/PrinterStateReducer.kt` (host-pure core); `theme/BakedTokens.kt` (oklch→sRGB policy) | role-match |
| **NEW** `theme/TokenBridge.kt` (`tokensFromPalette`) | utility (mapper) | transform | `theme/BakedTokens.kt` (Palette out → `ThemeTokens`); `resolve()` in `ThemeResolver.kt` | role-match |
| **MODIFY** `theme/ThemeTokens.kt` | model (value type) | n/a | self (extend in place) | exact |
| **MODIFY** `theme/ThemeResolver.kt` | service (StateFlow resolver) | event-driven (re-emit on change) | self (rewire `resolve`/mutators) | exact |
| **MODIFY** `theme/BakedTokens.kt` | config (default-seed snapshot) | n/a | self (demote to fail-safe) | exact |
| **MODIFY** `theme/ThemePrefs.kt` | store (global DataStore fallback) | CRUD (persist/sanitize) | self (extend tuple + sanitize) | exact |
| **MODIFY** `theme/compose/DinghyTheme.kt` + `LocalTokens.kt` | provider (Compose boundary) | request-response | self (unchanged boundary; verify) | exact |
| **MODIFY** `theme/views/ThemeableView.kt` consumers | provider (Views boundary) | push | self (unchanged interface) | exact |
| **MODIFY** `config/Profile.kt` | model (persisted + runtime) | CRUD | self (Phase-14 theme-tuple replace) | exact |
| **MODIFY** `config/ProfileStore.kt` | store (DataStore profile set) | CRUD | self (`mutateActive`/`sanitize` reuse) | exact |
| **MODIFY** `di/AppContainer.kt` | provider (service-locator) | event-driven (reactive re-seed) | self (`seedTheme` rewire + WR-02) | exact |
| **MODIFY** `render/GraphView.kt` (~L182-188) | component (custom Views render) | streaming (throttled repaint) | self (`applyTokens` rewire to `pool[i]`) | exact |
| **MODIFY** `ui/temperature/TemperatureScreen.kt` (~L446-451) | component (screen) | request-response | self (`traceColor` → pool index) | exact |
| **MODIFY** `ui/printstatus/PrintStatusScreen.kt` (~L463-469) | component (screen) | request-response | self (nozzle/bed `heat` → pool index) | exact |
| **NEW** color-wheel control | component (Compose Canvas) | event-driven (gesture-settle) | `designsystem/ScrubberPage.kt` (`awaitEachGesture` + settle) | role-match (gesture only) |
| **NEW** theme-editor sub-page | component (screen) | request-response | `ui/temperature/TemperatureScreen.kt` overlay + `SettingsScreen.kt` discipline | role-match |
| **MODIFY** `ui/screen/SettingsScreen.kt` | component (screen) | CRUD + request-response | self (re-organize sections; replace accent picker) | exact |
| **NEW/EXTEND** tests (see Tests section) | test | — | `ThemeResolverTest` / `ProfileThemeSeedTest` / `BakedTokenTableTest` / `TokenDeltaSerializationTest` | exact |

---

## Pattern Assignments

### NEW `theme/Palette.kt` (utility, pure transform — port of `../theme_theory/app/color.js`)

**Analog (responsibility):** `state/PrinterStateReducer.kt` — a host-pure core with ZERO Android
imports so it is unit-testable without an emulator. **Analog (oklch→sRGB policy + the "literal sRGB
only" law):** `theme/BakedTokens.kt`.

**Critical convention — NO Compose/Android import in the math core.** Mirror the `BakedTokens.kt`
header law (`BakedTokens.kt:6-30`): oklch silently renders WRONG on API<26, so the generator emits
baked sRGB only. The port uses `Double` throughout (match the JS oracle bit-for-bit), narrows to
`Int`/hex only at the boundary. Functions to port verbatim (RESEARCH §"The Port Surface"): `clamp01`,
`sToL`/`lToS`, `hexToRgb`/`rgbToHex`, `linToLab`/`labToLin` (matrices copied EXACTLY), `hexToOklch`,
`inGamut` (±0.0002), `oklchToHex` (20-iter), `maxChromaAt` (18-iter), `cuspL` (keep `L += 0.02`),
`spreadHues`, `minHueGap`, `rankByContrast`, `generate`.

**Pattern to follow — kotlin.math, lowercase hex output, `kotlin.math.cbrt`:**
```kotlin
// Pure object, no androidx imports. Return a plain data class of String hexes (oracle-comparable),
// NOT Compose Color — the bridge bakes to Color. Keeps golden tests host-pure.
object Palette {
    fun generate(
        seedHex: String, dark: Boolean, maxItems: Int,
        poolShift: Int = 0, statusFromPool: Boolean = true,
        simple: Boolean = false, highContrast: Boolean = false,
    ): Generated { /* … */ }
}
```

**Why a plain value type (not `ThemeTokens`):** keeps `Palette.generate` a faithful 1:1 of `color.js`
so golden tests stay clean (RESEARCH Open Q3 — derive tiers in the bridge, not the generator).

---

### NEW `theme/TokenBridge.kt` (utility, transform — port of `tokensFromPalette()`)

**Analog:** `theme/BakedTokens.kt` (the existing oklch→sRGB derivation that produces `ThemeTokens`)
+ `resolve()` in `ThemeResolver.kt` (the existing "assemble a complete `ThemeTokens`" function).

**Core pattern — map generator output → the extended `ThemeTokens`, deriving in-between tiers** via
`lShift(hex, dL)` and `rgbaOf(hex, a)` ports (RESEARCH §"The Token Bridge", full field table). 1:1
maps: `surface`/`text`/`text2`/`accent`/`go`/`stop`/`heat`(=caution). DERIVED dark/light pairs:
`bg2`/`surface2`/`surface3`/`text3`/`hair`/`outline2`/`accent2`/`accentSoft`/`accentLine`/`accentGlow`/
`edgeGlow` + the `*Soft`/`*Glow` alpha variants. Then apply `poolOverrides` (replace `pool[i]`).

**Excerpt to model — how `BakedTokens` constructs the full immutable set (verbatim shape to emit):**
```kotlin
// BakedTokens.kt:33-63 — the bridge output mirrors THIS constructor, but every value is computed
// from Palette.generate(...) + lShift/rgbaOf, not a literal. This is the ONLY other sanctioned
// producer of a complete ThemeTokens besides resolve().
ThemeTokens(
    bg = …, bg2 = …, surface = …, surface2 = …, surface3 = …,
    text = …, text2 = …, text3 = …, hair = …, outline = …, outline2 = …,
    accent = …, /* … */ heat = …, /* … */
    pool = …, directional = …,           // NEW fields (see ThemeTokens below)
    rScreen = 30.dp, rCard = 22.dp, rCtrl = 16.dp, rPill = 999.dp, fs = …,
)
```

---

### MODIFY `theme/ThemeTokens.kt` (model — add `pool[]` + `directional` + retire `violet`)

**Analog:** self. Add fields to the existing `@Immutable data class ThemeTokens` (`ThemeTokens.kt:24`).

**Discretion calls already settled in RESEARCH:**
- `pool: List<Color>` — rely on the existing `@Immutable` annotation covering the field (the project
  pattern; no `kotlinx-collections-immutable` dep needed — RESEARCH Assumption A3).
- `directional` — a small `@Immutable data class Directional(temperature, xy, z: Color)` (matches the
  JS shape; cleaner than 3 flat fields).
- **Retire `ThemeTokens.violet`** (`ThemeTokens.kt:64-69`) — only 2 consumers (`GraphView`,
  `TemperatureScreen`) → both move to `pool[2]`.
- **Replace the "Custom-theme scope (D-01)" KDoc** (`ThemeTokens.kt:21-22`) with the seed model.
- `heat` KDoc (`ThemeTokens.kt:58`) — reword: `heat` now means **only caution**, not heater identity.

```kotlin
@Immutable
data class ThemeTokens(
    /* …existing fields… */
    /** Generated contrast-ranked DATA pool (D-13). Consumers wrap `pool[i % pool.size]` (D-14). */
    val pool: List<Color>,
    /** Directional standards — pool[0..2] re-tagged by warmth (Move outlines + heater identity). */
    val directional: Directional,
    /* fs, radii unchanged */
)
@Immutable data class Directional(val temperature: Color, val xy: Color, val z: Color)
```

---

### MODIFY `theme/ThemeResolver.kt` (service — generate-and-cache rewire)

**Analog:** self. Keep the `MutableStateFlow` + `asStateFlow()` + recompute-and-re-emit idiom
EXACTLY (`ThemeResolver.kt:82-125`); swap the inputs and the `recompute()` body.

**Pattern to preserve — the StateFlow source-of-truth + the single-`apply()` re-emit (Pitfall 3):**
```kotlin
// ThemeResolver.kt:91-125 — KEEP this shape. The whole substrate is "extended not rewritten" because
// this StateFlow<ThemeTokens> boundary stays identical; only resolve() inputs + recompute() change.
private val _tokens = MutableStateFlow(/* generate from default seed */)
val tokens: StateFlow<ThemeTokens> = _tokens.asStateFlow()

// New mutators mirror setBase/setDeltas/setFs (ThemeResolver.kt:97-113):
fun setSeed(next: String)            { seed = next; recompute() }
fun setMode(next: String)            { mode = next; recompute() }
fun setShift(next: Int)              { poolShift = next; recompute() }
fun setOverride(i: Int, argb: Color?){ /* sparse map edit */ recompute() }

// CRITICAL (Pitfall 3): apply the FULL tuple in ONE call — never 3 sequential set*() = 3 flickers.
// Mirror the existing apply() (ThemeResolver.kt:115-120).
fun apply(seed: String, dark: Boolean, mode: String, poolShift: Int, maxItems: Int,
          overrides: Map<Int, Color>, fs: Float) { /* set all; recompute() once */ }

private fun recompute() {
    // generate-once-cache-forever (Pattern 2): Palette.generate(...) → tokensFromPalette() → cache.
    // Fail-safe: if generation ever throws, fall back to the BakedTokens default-seed snapshot.
    _tokens.value = TokenBridge.build(Palette.generate(...), overrides, fs)
}
```

**Construct with a validated default seed** (replace the no-arg `ThemeResolver()` default at
`ThemeResolver.kt:82-86`) — RESEARCH A1: `#3f78ff`. **Retire `TokenDelta`/`Role`** (D-04) — the whole
per-role override model (`ThemeResolver.kt:20-71`) is replaced by seed-only chrome + sparse pool
overrides.

---

### MODIFY `theme/BakedTokens.kt` (config — demote to fail-safe snapshot)

**Analog:** self. Keep ONE validated default-seed snapshot as the `recompute()` fail-safe (RESEARCH
§"Generate-and-Cache Rewire" point 4). Either keep `TokensDark`/`TokensLight` as the snapshot or
regenerate them from the Kotlin `Palette` + default seed. The `BakedTokens.kt:6-30` header (the "literal
sRGB Color(0x..) only, oklch is wrong on API<26" law) **stays load-bearing** — it now also governs the
generator's output policy.

---

### MODIFY `theme/ThemePrefs.kt` (store — global theme-tuple fallback + sanitize)

**Analog:** self. This is the textbook deterministic-fail-safe DataStore (`ThemePrefs.kt:35-139`).
Extend the persisted keys + the PURE `sanitize` to the tuple `(seedHex, dark, paletteMode, poolShift,
maxItems, poolOverrides, fs)`.

**Pattern to preserve — the never-throws sanitizer + `catch{ IOException → emptyPreferences() }`:**
```kotlin
// ThemePrefs.kt:38-52 + 103-124 — KEEP the catch→empty→sanitize→complete-theme contract. Extend
// sanitize: unparseable seedHex → default seed; bad paletteMode → Colorful; junk poolShift/maxItems →
// defaults; garbage poolOverrides entry → drop just that index. NEVER throws, NEVER black-screens.
val flow: Flow<Resolved> = dataStore.data
    .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
    .map { prefs -> sanitize(/* raw primitives */) }
```
`Resolved` (`ThemePrefs.kt:78-82`) becomes the new theme tuple. `DEFAULT` (`ThemePrefs.kt:91`) =
validated default seed. **`fsChoice` stays a separate unchanged setting** (D-05).

---

### MODIFY `config/Profile.kt` + `config/ProfileStore.kt` (model + store — theme-tuple persistence)

**Analog:** self (Phase-14 machinery — the proven durable, lost-update-safe per-profile path).

**`Profile.kt`** — replace `themeBase`/`themeDeltaArgb` (`Profile.kt:34-36`, `:57-59`) with the tuple
PRIMITIVES; **keep** `fsChoice`. `PersistedProfile` gets matching `@Serializable` fields. The
`ignoreUnknownKeys=true` (`ProfileStore.kt:128`) means old blobs decode cleanly — old keys ignored, new
fields default (D-05 fresh-start, NO migration). **Preserve the redacted `toString`** (`Profile.kt:39-42`,
`:97-100`) — theme fields carry no secrets; don't widen the redaction surface (V7).

```kotlin
// Profile.kt — the persisted-primitives discipline (Profile.kt:32-36 comment) carries forward.
// Mirror toThemeResolved() (Profile.kt:76-82): resolve the tuple reusing ThemePrefs.sanitize parity.
val seedHex: String = "#3f78ff",
val dark: Boolean = true,
val paletteMode: String = "Colorful",      // Colorful | Simple | HighContrast
val poolShift: Int = 0,
val maxItems: Int = 4,                      // nozzle+bed+chamber+headroom (D-13/A2)
val poolOverrides: Map<Int, Long> = emptyMap(),   // sparse poolIndex -> unsigned-32 ARGB (D-09)
// fsChoice retained
```

**`ProfileStore.kt`** — `mutateActive` (`ProfileStore.kt:109-118`) is REUSED VERBATIM as the durable,
lost-update-safe write path for theme edits (read-modify-write inside ONE `dataStore.edit`). The
connection projection `toConnectionConfig()` (`Profile.kt:66`) must stay host/port/apiKey-only so a
theme edit yields an EQUAL `ConnectionConfig` and does NOT churn the spine (T-14-04 / RESEARCH Security).

---

### MODIFY `di/AppContainer.kt` (provider — `seedTheme` rewire + WR-02 reactive fix)

**Analog:** self. The `seedTheme(scope)` re-seed (`AppContainer.kt:314-324`) + `mutateActiveProfile`
(`AppContainer.kt:128-130`) + `writeScope` (`AppContainer.kt:105`) are the proven pattern.

**WR-02 fix (subsumed deferred todo) — make the no-active branch REACTIVE:** the current no-active
branch wraps a ONE-SHOT `themePrefs.flow.firstOrNull()` inside `flatMapLatest` (`AppContainer.kt:318`).
Mirror the active branch — `flatMapLatest` into `themePrefs.flow` so a global-default edit while idle
re-emits.
```kotlin
// AppContainer.kt:314-324 — KEEP apply()-in-one-call; FIX the no-active branch to be reactive:
fun seedTheme(scope: CoroutineScope) {
    scope.launch {
        activeProfile
            .flatMapLatest { p ->
                if (p != null) flowOf(p.toThemeResolved())
                else themePrefs.flow                       // WR-02: reactive, not firstOrNull()
            }
            .collect { resolved -> themeResolver.apply(/* full tuple in ONE apply */) }
    }
}
```
`themeResolver` is constructed at `AppContainer.kt:186` — update the default-seed construction here.

---

### MODIFY `render/GraphView.kt` (component — trace colors → `pool[i]`)

**Analog:** self. The `applyTokens` rewire (`GraphView.kt:182-193`) is the pool's first real consumer.

```kotlin
// GraphView.kt:182-193 — TODAY hard-binds trace0=heat, 1=accent, 2=violet. Rewire to pool[i]:
override fun applyTokens(t: ThemeTokens) {
    for (i in 0 until MAX_TRACES) linePaints[i].color = t.pool[i % t.pool.size].toArgb()  // D-14 wrap
    fillPaint.color = t.pool[0].toArgb(); fillPaint.alpha = FILL_ALPHA
    labelPaint.color = t.text3.toArgb()
    labelPaint.textSize = fsSp(LABEL_BASE_SP, t.fs) * density
    invalidate()
}
```
**Update the class KDoc** (`GraphView.kt:46-48`) — the nozzle=heat/bed=accent/chamber=violet line is
now pool-indexed. **`MAX_TRACES = 3` (`GraphView.kt:343`) is a HARD CAP** — fine this phase (no printer
shows >3 graph traces; RESEARCH Pitfall 5 / A4). Flagged, NOT changed.

---

### MODIFY `ui/temperature/TemperatureScreen.kt` + `ui/printstatus/PrintStatusScreen.kt` (readout pool index)

**Analog:** self. **Stable-identity rule:** same sensor = same `pool` index everywhere (readout index
MUST equal the GraphView trace index).

```kotlin
// TemperatureScreen.kt:446-451 — TODAY:  0->heat, 1->accent, 2->violet  →  index into pool:
private fun traceColor(index: Int, t: ThemeTokens): Color = t.pool[index % t.pool.size]
```
`PrintStatusScreen.kt:463-469` nozzle/bed `t.heat` → `t.pool[0]` / `t.pool[1]` (heater identity =
`pool[0]` = `directional.temperature`, NOT `heat`). **Do NOT touch the other 21 `.heat` references**
(Console WARNING, Warn-intent buttons, `ConfirmGuard`) — `heat` = caution stays (Pitfall 4).

---

### NEW color-wheel control (component — Compose Canvas + settle-regen)

**Analog (gesture only):** `designsystem/ScrubberPage.kt` — there is NO wheel in the repo, but the
single-`awaitEachGesture` settle-on-up discipline is exact.

**Pattern to copy — ONE `awaitEachGesture`, set transiently on each move, settle on pointer-up:**
```kotlin
// ScrubberPage.kt:161-176 — the WR-01 pattern. ONE pointerInput, ONE consumer, no second detector to
// race the stream. detectDragGestures is WRONG (touch-slop swallows a zero-movement tap).
.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onHandleMove(hueAt(down.position, size)); down.consume()    // cheap: move handle, NO regen
        do {
            val e = awaitPointerEvent()
            e.changes.forEach { if (it.pressed) { onHandleMove(hueAt(it.position, size)); it.consume() } }
        } while (e.changes.any { it.pressed })
        onSettle()   // D-07: regenerate the palette ONLY here (pointer-up) — protect the Adreno-320 floor
    }
}
```
**Carve-out (precedented):** the wheel/swatches render their literal generated/seed color — like
`AccentSwatch` (`SettingsScreen.kt:585-628`), Spoolman spool-color border, PromptMarkup hex. These are
theme DATA, not chrome. ≥64dp handle hit area; ring is a sacred circle (`aspect-ratio(1f)`).

---

### NEW theme-editor sub-page (component — pushed screen, FFG-exempt)

**Analog (overlay/sub-page hosting):** `ui/temperature/TemperatureScreen.kt:200` shows `ScrubberPage`
as a state-gated full-screen overlay; `ui/extrude/ExtrudeScreen.kt:298-317` hosts `NumpadPage` the same
way. **Analog (screen discipline + carve-out + persist helpers):** `SettingsScreen.kt`.

**Routing options (both precedented in `AppShell.kt`):**
1. A local back-stack WITHIN `Dest.Settings` — mirror `Dest.Calibration`'s
   `calibrationRoutine`-null-or-page pattern (`AppShell.kt:510-521`): a `null`/editor state inside the
   Settings render branch, green Back pops to the hub. **Recommended** (D-10 "pushed sub-page off
   Settings", no new top-level Dest).
2. A new top-level `Dest.ThemeEditor` (`AppShell.kt:602`-style branch) — heavier; only if the planner
   wants drawer reachability (it doesn't — it's reached only from Settings → Appearance).

**Section order** (RESEARCH §"Settings Rebuild" + UI-SPEC §"New surface"): Seed color (wheel) ·
Presets · Preview (generated swatch strip) · Pool colors (per-slot override grid) · Actions
(Randomize=`Intent.Warn` amber, Reset=`Intent.Danger` red + `ConfirmGuard`, Done/Back=`Intent.Go`
green). Reuse `OutlinedControl`/`Intent`, `ConfirmGuard`, the `SectionLabel` grammar. Honor the
`fsSp(baseSp, t.fs)` floors ([[dinghy-font-sizes-too-small]]).

---

### MODIFY `ui/screen/SettingsScreen.kt` (component — re-organize hub, replace accent picker)

**Analog:** self. Reuse the existing grammar VERBATIM: `Column.verticalScroll(rememberScrollState())
.padding(16.dp)` (`SettingsScreen.kt:174-180`), `SectionHeader`/`SectionLabel`/`ProfileRow`/
`AddPrinterRow`/`OutlinedControl`/`TokenTextField` — only the section SET + order changes.

**Sections in order (D-11):** Printers (the Phase-14 CRUD list — reused 1:1, `SettingsScreen.kt:189-220`)
· Connection (host/port/key + mDNS form — reused, `:225-415`) · Appearance (dark/light + S/M/L chips +
**palette-mode chip row** + **"Edit theme…"** forward-entry row) · Feature toggles (webcam live;
outputs/WebRTC/fine-tune as greyed placeholders) · System (version+build only).

**REMOVE the accent picker** — the `AccentSwatch` row + `ACCENT_PALETTE` + `persistDeltas`
(`SettingsScreen.kt:466-492`, `:553-628`) are replaced by the seed model (D-04 retires `TokenDelta`
chrome overrides). **Reuse the persist-to-active-else-global helper shape** (`persistBase`/`persistFs`,
`SettingsScreen.kt:520-547`) for the palette-mode persist — route through `mutateActiveProfile` /
fallback `themePrefs`.

**Greyed forward-entry pattern** — model on the existing live-accent-outlined rows (`ProfileRow`/
`AddPrinterRow`, `SettingsScreen.kt:640-716`) but disabled + `t.text3` + "Coming soon" sub-label.

---

## Shared Patterns

### Persist-to-active-profile-else-global (the durable write path)
**Source:** `di/AppContainer.kt:128-130` (`mutateActiveProfile`) + `ui/screen/SettingsScreen.kt:520-533`
(`persistBase` helper) + `[[dinghy-compose-write-scope-cancellation]]`.
**Apply to:** every theme edit (seed/mode/shift/override) in the editor + Settings appearance.
```kotlin
// Live retheme drives the resolver directly (immediate); persistence is fire-and-forget through the
// PROCESS-lifetime writeScope — NEVER rememberCoroutineScope() (cancelled by same-frame nav → dropped
// write on slow flash). Read-modify-write inside ONE dataStore.edit (mutateActive).
container.themeResolver.setSeed(hex)                       // live, immediate
if (active != null) container.mutateActiveProfile { it.copy(seedHex = hex) }   // durable
else scope.launch { container.themePrefs.setSeed(hex) }   // idle/global fallback
```

### Deterministic fail-safe sanitize (never throws, never black-screens)
**Source:** `theme/ThemePrefs.kt:103-138` + `config/ProfileStore.kt:138-154` + `Profile.toThemeResolved`
(`Profile.kt:76-82`).
**Apply to:** the theme-tuple read path (V5). Validate `seedHex` (hex format), `paletteMode` (enum),
`poolShift`/`maxItems` (range), `poolOverrides` (valid ARGB) — fail-safe to defaults per field, drop
junk entries individually, NEVER throw. The device is a printer surface that must never go dark.

### Single re-emit per change (no flicker)
**Source:** `theme/ThemeResolver.kt:115-120` (`apply`) + `AppContainer.kt:307-324` (one-`apply` re-seed).
**Apply to:** the editor settle (D-07) and the per-profile re-seed. Apply the FULL tuple in ONE
`ThemeResolver.apply(...)` call — never 3 sequential `set*` calls (3 flickers; Pitfall 3).

### Token purity + the data-color carve-out
**Source:** `theme/compose/LocalTokens.kt:20-22` (the throwing boundary) + `SettingsScreen.kt:585-628`
(`AccentSwatch` carve-out KDoc).
**Apply to:** all editor chrome routes through `LocalTokens.current`/`ThemeTokens`. The ONLY literal
colors are swatches/wheel rendering generated/seed DATA (precedented). `Intent` → outline color map
(`OutlinedControl.kt:48-54`) governs every button.

---

## Tests (Wave-0 + extend)

| Test file | New/Extend | Analog | What it covers |
|-----------|-----------|--------|----------------|
| `theme/PaletteGoldenTest.kt` | **NEW** | `theme/BakedTokenTableTest.kt:15-37` (assert exact hex) | Kotlin `Palette.generate` == `color.js` oracle (default seed dark/light, Simple, High-Contrast, edge-hue seeds, poolShift). Pin the verified hexes from RESEARCH §"golden reference" in-repo. |
| `theme/PaletteMathTest.kt` | **NEW** | `BakedTokenTableTest` (host-pure assert) | OKLCH↔sRGB round-trip + gamut clamp stays in [0,1]. |
| `theme/TokenBridgeTest.kt` | **NEW** | `ThemeResolverTest.kt:24-48` (resolve assertions) | `tokensFromPalette` tier/alpha derivation parity vs `dinghy.js`. |
| `theme/ThemeResolverTest.kt` | **EXTEND** | self | generate-and-cache emits expected `ThemeTokens`; `poolOverrides` applied at index; Reset clears; single-`apply` re-emit count. Keep the `runTest`/`UnconfinedTestDispatcher`/emission-count idiom (`ThemeResolverTest.kt:51-76`). |
| `theme/ProfileThemeSeedTest.kt` | **EXTEND** | self | theme-tuple `toThemeResolved` + sanitize fail-safe (junk seed/mode/shift/overrides → defaults, never throws). Keep the corrupt-primitive table (`ProfileThemeSeedTest.kt:44-93`). |
| `theme/TokenDeltaSerializationTest.kt` | **EXTEND/REPLACE** | self | fresh-start decode — old blob keys (`themeBase`/`themeDeltaArgb`) ignored, new tuple fields default (`ignoreUnknownKeys`). |

**Golden oracle script:** **NEW** `tools/color-golden/dump.js` — committed Node script emitting the
fixture from the sibling `../theme_theory/app/color.js` (RESEARCH §"Generating the golden vectors").
Pin the verified hexes in-repo so CI doesn't depend on the sibling.

**Build/test reality (CLAUDE.md):** runs Windows-side via `E:\Android\gw.bat`; **list test classes
explicitly** — the `--tests 'pkg.*'` glob false-fails on this AGP. Host unit tests:
`cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.theme.PaletteGoldenTest --no-daemon"`.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| color-wheel `Canvas` ring | component | event-driven | No wheel/ring control exists in the repo. The **gesture** has an exact analog (`ScrubberPage.awaitEachGesture`); the **hue-ring drawing** (Canvas hue arc + handle placement) is genuinely new — follow `color.js` hue math + the carve-out for literal color. |

Everything else maps to an existing analog (most are in-place modifications of the file itself).

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/{theme,config,di,render,ui,designsystem}/`
+ `app/src/test/java/works/mees/dinghy/theme/`.
**Files scanned:** ~22 read in full or in targeted ranges; grep audits for `.violet` (2 files),
`MAX_TRACES` (3 cap), `SettingsScreen(` call sites (2: `AppShell.kt:602`, `RootController.kt:105`),
`Dest.*` routing.
**Sibling oracle (read-only, NOT in this repo):** `../theme_theory/app/{color.js,dinghy.js}` +
`COLOR-SYSTEM.md` — the frozen owner-reviewed port source + golden oracle.
**Pattern extraction date:** 2026-06-05
