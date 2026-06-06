# Phase 18: Preview Harness & Tokenization Foundation - Pattern Map

**Mapped:** 2026-06-06
**Files analyzed:** 16 (8 NEW + 8 MODIFIED) + 3 NEW unit tests
**Analogs found:** 14 / 16 with strong matches (the 2 truly-new infrastructure shapes — icon registry, fixtures module — have role/idiom analogs, not 1:1 file analogs)

> **All file:line refs below were re-verified against the working tree this session.** Where CONTEXT.md drifted, the correction is noted inline. No source files were modified; this is the only file written.

---

## Drift corrections vs CONTEXT.md / RESEARCH.md

| Claim | Reality (verified) |
|-------|--------------------|
| `DinghyTheme.kt:37-63` resolver overload, `fontScale=1f` at `:48` | ✅ EXACT. Resolver overload = `DinghyTheme.kt:59-63`; flow overload `:37-51`; `fontScale = 1f` pin at `:48`. |
| `BenchActivity.kt:62` `EXTRA_SCENE` read | ✅ EXACT — `intent?.getStringExtra(EXTRA_SCENE) ?: SCENE_COMPOSE` at `:62`; const `EXTRA_SCENE = "scene"` at `:176`. |
| `MainActivity.kt:30-56` `onCreate`, no intent handling | ✅ EXACT — `onCreate` `:31-55`; reads NO intent today. `container = (application as DinghyApp).container` at `:34`. |
| `di/AppContainer.kt:455` `ThemeResolver.bake`, `:242` `devCyclerEnabled`, `:118` `writeScope` | ✅ `writeScope` declared `:118`; `devCyclerEnabled` `:242`; the bake CALL is `themeResolver.bake(tuple)` inside `effectiveTokens` at **`:452-456`** (not `:455` alone — `:455` is the `bake` line). `ThemeResolver.bake(tuple)` itself lives in `ThemeResolver.kt:162` (per research). |
| `ThemePrefs.kt:179` `dev_cycler_enabled` key, `devEnableFlow` | ✅ key `:179`; `devEnableFlow` `:117`; `ThemeTuple` `data class` `:151` with `val fs: Float` at `:162`. |
| `PrintStatusMode.kt:23-38` 4 states + `classifyPrintStatus` | ✅ sealed interface `:23-35`; `TerminalKind` enum `:38`; `classifyPrintStatus` `:45-52`. |
| `ui/route/TopRoute.kt:37` `Dest` enum (15 values) | ✅ EXACT at `:37`; 15 values confirmed. `derive(...)` `:63-68`. |
| `ui/shell/ShellNavState.kt:43-124` | ✅ class `:43`; `dest` mutableState `:45`; `navigateTo` `:84-103`; `rememberShellNavState()` `:123-124`. |
| `render/GraphViewHost.kt` D-05 insert point | ✅ `@Composable fun GraphViewHost` `:27-43` (single-snapshot) + a second multi-trace overload `:64-86`. **Both** wrap `AndroidView` and BOTH need the inspection branch. CONTEXT said "`:34`" — that's the `AndroidView(` line of overload #1. |
| `MainActivity` exported | ✅ `AndroidManifest.xml:64-65` `android:exported="true"` — confirms the V4/V5 security note (the `start_dest` extra is reachable, must be dev-gated). |
| `app/build.gradle.kts:103-104` tooling wiring | ✅ EXACT — `:103` `implementation(libs.compose.ui.tooling.preview)`, `:104` `debugImplementation(libs.compose.ui.tooling)`. Research verdict LEAVE-AS-IS confirmed. |
| `LocalInspectionMode` used in codebase | ✅ confirmed `grep` = NONE. Brand-new idiom this phase. |
| Spool QR is Coil-loaded | ❌ NO — `SpoolScreen.kt:379` QR/icon is `painterResource` (static drawable). Spool's Coil proof is its filament-thumb path + dense fixtures, NOT the QR. (Research already flagged this correctly.) |

---

## File Classification

### NEW files

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `preview/PreviewTheming.kt` (`PreviewBox` wrapper + 6 seeds + fs=L seed) | preview-infra / provider | transform (tuple→tokens) | `bench/BenchActivity.kt:88-98` (`DinghyTheme(resolver){…}` host) | role-match (exact idiom) |
| `preview/SampleFixtures.kt` (reusable fake-state) | fixture/sample-data | transform (pure data) | `bench/SyntheticFeed.kt` + `bench/ComposeSceneState` (deterministic fixtures) | role-match |
| `preview/DinghyPreviews.kt` (`@Nexus7Previews`, `@DinghyThemePreviews` multipreview annotations) | preview-infra (annotation) | n/a (compile-time) | — (no existing multipreview annotation) | NO 1:1 analog — idiom from research Pattern 2/Q8 |
| `preview/PreviewPlaceholders.kt` (`PreviewPlaceholderBox` + `LocalInspectionMode` helpers) | preview-infra / component | request-response (Composable) | `designsystem/MaterialSymbol.kt` (small token-aware leaf Composable) | role-match |
| `preview/PrintStatusPreviews.kt` + `PrintStatusModeProvider` | preview / provider | transform (state→panels) | `PrintStatusMode.kt` (the sealed states) | exact (states are the provider source) |
| `designsystem/icons/DinghyIcon.kt` (`sealed IconRef` + `data class DinghyIcon`) | model / config | n/a (data model) | `ui/route/TopRoute.kt` (`sealed interface TopRoute` modeling) | role-match (Kotlin "one-of-N" idiom) |
| `designsystem/icons/DinghyIcons.kt` (registry `object`) | config / registry | n/a (static table) | `command/PrinterCommands.kt` (`object` of constants — single source of truth) | role-match |
| `designsystem/icons/DinghyIconView.kt` (unifying render primitive) | component (design-system) | request-response | `designsystem/MaterialSymbol.kt` (the primitive it delegates to) | exact (sits directly above it) |
| `res/values/strings.xml` (master English vocabulary) | resource | n/a | — (none — 0% tokenized today) | NO analog — establishes the file |
| `docs/ui_design/PREVIEW_AND_TOKENS.md` (convention spec) | docs | n/a | `docs/ui_design/THEMING.md` (sibling LAW doc) | role-match |
| `app/src/test/.../SampleFixturesTest.kt` | test | n/a | existing JVM unit tests under `app/src/test` | role-match |
| `app/src/test/.../DinghyIconsTest.kt` | test | n/a | existing JVM unit tests | role-match |
| `app/src/test/.../StartDestMappingTest.kt` | test | n/a | existing JVM unit tests | role-match |

### MODIFIED files

| Modified File | Role | Change | Analog for the change |
|---------------|------|--------|------------------------|
| `MainActivity.kt` (`:31-55`) | activity / entry | read `start_dest` extra, dev-gated, seed `ShellNavState.dest` | `bench/BenchActivity.kt:62,176` (`EXTRA_SCENE` read + companion const) |
| `ui/shell/RootController.kt` (`:44,55,57,133`) | controller / router | thread an optional `startDest: Dest?` into `rememberShellNavState`/`AppShell` | self (existing hoist seam `:55`) |
| `render/GraphViewHost.kt` (`:27-43` + `:64-86`) | interop host | add `LocalInspectionMode` placeholder branch before `AndroidView` (BOTH overloads) | D-05 idiom (research Q5 flavor 2) |
| `render/BedMeshHeatmapHost.kt` (`fun` `:29`, `AndroidView` `:35`) | interop host | same D-05 branch | `GraphViewHost` (sibling) |
| `render/WebcamViewHost.kt` (`fun` `:37`, `AndroidView` `:49`) | interop host | same D-05 branch | `GraphViewHost` (sibling) |
| `ui/printstatus/PrintStatusScreen.kt` (`:515,523` Coil; many `MaterialSymbol`) | screen | exemplar: `@Preview`s + strings + icon tokens + Coil inspection branch + RTL/a11y | self |
| `ui/finetune/*Screen.kt` + `FineTuneVm.kt` | screen | exemplar: `@Preview`s (present/absent/busy via `FineTuneVm` flags) + strings + icons | `FineTuneVm.kt:66-75` (capability gates + `groupBusy`) |
| `ui/spool/SpoolScreen.kt` (`:379` painter; `:250-407` symbols) | screen | exemplar: `@Preview`s + dense fixtures + Coil-thumb inspection branch + strings/icons | self |
| `app/build.gradle.kts` (`:103-104`, debug build type) | config | code-comment on `:103` (WHY `-preview` stays `implementation`); add `debug { isPseudoLocalesEnabled = true }` | self |

---

## Pattern Assignments

### `preview/PreviewTheming.kt` (preview-infra, transform) — `PreviewBox` wrapper

**Analog:** `bench/BenchActivity.kt:88-98` — the only existing place that drives the `DinghyTheme(resolver = …)` overload for a non-production host (the overload exists *precisely* for bench/test/preview, per `DinghyTheme.kt:53-58` KDoc).

**The seam to reuse** (`DinghyTheme.kt:59-63`):
```kotlin
@Composable
fun DinghyTheme(resolver: ThemeResolver, content: @Composable () -> Unit) =
    DinghyTheme(tokensFlow = resolver.tokens, content = content)
```
The flow overload (`:37-51`) does the token publish + the `fontScale=1f` density pin (`:48`). **A preview MUST go through one of these two overloads** so `LocalTokens.current` resolves to a real baked theme — never construct a preview-only token map (research Don't-Hand-Roll).

**Bench's host shape to copy** (`BenchActivity.kt:88-98`):
```kotlin
setContent {
    DinghyTheme(resolver = resolver) {
        val tokens by resolver.tokens.collectAsStateWithLifecycle()
        RenderBenchScene(state = state, tokens = tokens, …)
    }
}
```

**The 6-combo seed source** — `ThemeResolver.bake(tuple)` is the pure bake fn (`ThemeResolver.kt:162`); the production path already uses it inside `effectiveTokens` (`AppContainer.kt:452-456`):
```kotlin
val effectiveTokens: Flow<ThemeTokens> =
    combine(activeThemeTuple, _themeOverride, devCyclerEnabled) { base, ov, devOn ->
        val tuple = if (ov != null && devOn) ov.mergeOnto(base) else base
        themeResolver.bake(tuple)          // ← AppContainer.kt:455 — the pure bake call
    }
```
So `PreviewBox(tuple)` bakes-and-flows: `DinghyTheme(tokensFlow = flowOf(ThemeResolver().bake(tuple))) { content() }` — reuses the ONE boundary, zero preview-only theme code. **Seed the 6 `ThemeTuple`s** from `ThemePrefs.ThemeTuple` (`ThemePrefs.kt:151`, has `val fs: Float` at `:162`).

**⚠ fs=L trap (Pitfall 1):** OS `fontScale` is pinned to `1f` at `DinghyTheme.kt:48`, so `@Preview(fontScale=…)` is a NO-OP. Inject `fs=L` via `ThemeTuple.fs` in the seed, NEVER the annotation.

---

### `preview/SampleFixtures.kt` (fixture, transform)

**Analog:** `bench/SyntheticFeed.kt` + the `ComposeSceneState`/`RenderSceneState`/`GraphSample` immutable fixtures (`BenchActivity.kt:113-148`). Same role: deterministic, pure-Kotlin fake-state with NO live Moonraker. Must live in `main` (not `test/`) so `@Preview` can see it.

**The state types the fixtures build to** (verified, the real production state classes):
- `PrintStatusMode` (`PrintStatusMode.kt:23-35`) — expose all 4/6 states: `Standby`, `Printing`, `Paused`, `Terminal(Complete|Cancelled|Error)`.
- `FineTuneVm` (`FineTuneVm.kt:49-76`) — build present/absent/busy variants by toggling the capability gates + `groupBusy`:
```kotlin
// FineTuneVm.kt:66-75 — the fields fixtures flip for the D-01 capability-gating proof
val hasGcodeMove: Boolean = false, val hasToolhead: Boolean = false,
val hasExtruder: Boolean = false, val hasFan: Boolean = false,
val hasFwRetraction: Boolean = false,         // absent → HIDDEN (not disabled), SC-2/D-02
val baselines: FineTuneBaselines = FineTuneBaselines(),
val groupBusy: Boolean = false,               // D-15 whole-group state-flip busy lock
```
Fixtures: `fineTuneAllPresent`, `fineTuneNoFwRetraction` (absent path), `fineTuneBusy` (`groupBusy = true`).
- Spool list — dense fixtures for `SpoolScreen` (the third archetype: image + dense data).

**Caveat (research A1):** keep fixtures plain immutable data — they are the seed the Phase-22 backfill reuses. Reusable `object`/builders, NOT per-preview one-offs.

---

### `preview/PreviewPlaceholders.kt` + the D-05 host branch (component, request-response)

**Analog for the leaf Composable:** `designsystem/MaterialSymbol.kt:30-44` (small token-aware leaf reading `LocalTokens.current`).

**The canonical D-05 idiom** — wrap each interop host's body in the inspection branch BEFORE the `AndroidView`:
```kotlin
@Composable
fun GraphViewHost(tokens: ThemeTokens, snapshot: FloatArray, modifier: Modifier = Modifier, drawArea: Boolean = true) {
    if (LocalInspectionMode.current) {
        PreviewPlaceholderBox(label = "GraphView (live on device)", modifier = modifier)
        return
    }
    AndroidView(/* ...existing GraphViewHost.kt:34-42... */)
}
```
**Insert points (ALL verified, ALL `AndroidView`-based, ALL need the branch):**
- `render/GraphViewHost.kt` — TWO overloads: single-snapshot `:27-43` AND multi-trace `:64-86`. Branch BOTH.
- `render/BedMeshHeatmapHost.kt` — `fun` `:29`, `AndroidView` `:35`.
- `render/WebcamViewHost.kt` — `fun` `:37`, `AndroidView` `:49`.

`PreviewPlaceholderBox` paints a token-colored outline + centered label (D-05: "labeled stand-in box, not a blank/broken region") — read `LocalTokens.current` for the outline color, same as `MaterialSymbol`'s `tint = LocalTokens.current.text` default (`MaterialSymbol.kt:34`).

**Coil flavor (same mechanism)** — `PrintStatusScreen.kt:523` (`AsyncImage`) and `:875`/`:1164` (more `AsyncImage`/thumb) + the Spool filament thumb: branch on `LocalInspectionMode.current` → `PreviewPlaceholderBox` else `AsyncImage`. Note `PrintStatusScreen.kt:515` already has a `painterResource(R.drawable.benchy)` placeholder available to reuse.

---

### `designsystem/icons/` registry (model + config + component) — D-07/D-08

**Analog for the data model:** `ui/route/TopRoute.kt:14-23` — the project's established `sealed interface` "one-of-N" modeling idiom (`TopRoute` = Connect | Splash | Shell). Mirror it for `IconRef = Ligature | Drawable`.

**Analog for the registry object:** `command/PrinterCommands.kt` (an `object` holding the single source of truth — e.g. `BABYSTEP_STEPS`, referenced by `PrintStatusMode.kt:75` not redefined). `DinghyIcons` is the icon equivalent + the new subset source.

**Analog for the render primitive:** `designsystem/MaterialSymbol.kt:30-44` — `DinghyIconView` sits directly ABOVE it and delegates the `Ligature` branch to it unchanged:
```kotlin
// designsystem/MaterialSymbol.kt:30-44 — the existing ligature primitive DinghyIconView delegates to
@Composable
fun MaterialSymbol(name: String, modifier: Modifier = Modifier,
                   tint: Color = LocalTokens.current.text, sizeSp: Float = 32f) {
    Text(text = name, modifier = modifier, color = tint,
         fontFamily = MaterialSymbols, fontSize = sizeSp.sp)
}
```
The `Drawable` branch uses `painterResource(...)` (the existing pattern at `SpoolScreen.kt:379`, `PrintStatusScreen.kt:680`).

**Shape (research Q6 — Codex-review the final form per [[codex-review-final-plans]]):**
```kotlin
sealed interface IconRef {
    @JvmInline value class Ligature(val name: String) : IconRef
    @JvmInline value class Drawable(@DrawableRes val resId: Int) : IconRef
}
data class DinghyIcon(val primary: IconRef, val alternate: String)   // alternate = one-place remap handle (D-07)
object DinghyIcons { /* one entry per icon the 3 exemplars use this phase only */ }
@Composable fun DinghyIconView(icon: DinghyIcon, …, contentDescription: String?)  // NO label fused (D-08)
```
**D-08:** `DinghyIcon` carries NO label. Label stays a separate `stringResource(...)` at the call site.
**Scope:** register ONLY the icons the 3 exemplars use; migrate ONLY those call sites. The other ~50 `MaterialSymbol(...)` sites stay raw until Phase 22.

---

### `MainActivity.kt` modification (entry) — the `start_dest` hook

**Analog:** `bench/BenchActivity.kt:62` + companion `:176`.

**Bench's read + companion pattern to mirror:**
```kotlin
// BenchActivity.kt:62
val scene = intent?.getStringExtra(EXTRA_SCENE) ?: SCENE_COMPOSE
// BenchActivity.kt:176
companion object { const val EXTRA_SCENE = "scene" }
```

**MainActivity insert point** — `onCreate` `:31-55`, after `container` resolves (`:34`), before/at `setContent` (`:40`):
```kotlin
val container = (application as DinghyApp).container   // MainActivity.kt:34 (existing)
// NEW: dev-gated start_dest read (one-shot intent read — no DataStore write needed)
val startDest: Dest? = if (container.devCyclerEnabled.firstSafe()) parseStartDest(intent) else null
…
RootController(container, startDest = startDest)        // thread into the controller
```
**Security (V4/V5 — MainActivity is `exported="true"`, manifest `:65`):**
- **Dev-gate** via `container.devCyclerEnabled` (`AppContainer.kt:242`, default FALSE, release-readable) — NOT `BuildConfig.DEBUG`. Inert in the sideloaded release.
- **Safe parse** — map the extra through `Dest.valueOf`-with-fallback (unknown → `null`/ignore, never crash). This is the `StartDestMappingTest.kt` unit target.
- **No write** — the jump is a one-shot READ. IF the hook ever toggles `devCyclerEnabled`, route through `AppContainer.writeScope` (`:118`) per [[dinghy-compose-write-scope-cancellation]] (see `setBabystepEnabled` `:206-208` as the write-helper template).

---

### `ui/shell/RootController.kt` modification (controller) — seed the hoisted nav

**Analog:** self — the existing hoist seam.

**The seam** (`RootController.kt:44,55`):
```kotlin
fun RootController(container: AppContainer) {            // :44 — add `startDest: Dest? = null`
    …
    val nav = rememberShellNavState()                    // :55 — hoisted, survives the Splash blip
```
`ShellNavState.dest` defaults to `PrintStatus` (`ShellNavState.kt:45`). Seed it ONCE from `startDest` (research Q3: land-with-whatever-state; the Splash gate at `RootController.kt:121` applies on top — `AppShell` only composes in the Ready/`else` branch `:133`). **DO NOT** seed from a `LaunchedEffect` inside `AppShell` (re-fires on recompose/recovery — research anti-pattern). Cleanest: pass `startDest` into `rememberShellNavState` and apply it in the `remember{}` initializer (`ShellNavState.kt:123-124`), or set `nav.dest` once in a keyed-on-Unit init guarded so it can't re-fire.

---

### Exemplar screens (3) — the template Phases 19-21 inherit (D-02)

Each gets the **"core three + mechanical riders"**: `@Preview` matrix + string tokens + icon tokens + RTL `start/end` + tokenized `cd_*`/≥48dp. **EXCLUDED (D-03):** `@Stable`/`@Immutable`/`ImmutableList` — deferred to Phase 22.

**`PrintStatusScreen.kt`** (anchor — multi-state via `@PreviewParameter`):
- Provider source = `PrintStatusMode.kt:23-35`. `PrintStatusModeProvider : PreviewParameterProvider<PrintStatusMode>` emitting the 4-6 states.
- Coil inspection branch at `:523` (`AsyncImage`), `:875`, `:1164`. Existing `painterResource(R.drawable.benchy)` placeholder at `:515` reusable.
- Embedded `GraphViewHost` → covered by the D-05 host branch (no per-screen work once the host is branched).
- Icon migration: the `MaterialSymbol(...)` sites (`:571,612,618,648,656,667,988,1010`) → `DinghyIconView(DinghyIcons.X)`.

**`FineTuneScreen`/`*Screen.kt` + `FineTuneVm`** (capability-gating + busy-lock proof):
- Previews driven by `FineTuneVm` fixtures: all-present / `hasFwRetraction=false` (absent→HIDDEN) / `groupBusy=true` (`FineTuneVm.kt:66-75`).
- This is the de-risk for Phase 19 Output Controls (also capability-gated).

**`SpoolScreen.kt`** (image + dense data):
- Coil filament-thumb inspection branch (the QR at `:379` is `painterResource`, NOT Coil — so the Coil proof is the thumb path).
- Dense `spoolList` fixtures.
- Icon sites `:250,282,284,295,305,312,407` → `DinghyIconView`.

**Test-matcher migration (this phase, exemplars only):** externalizing strings breaks `onNodeWithText("Back")` instrumented matchers on these 3 screens → migrate to `context.getString(R.string.…)` or `testTag`. Rest rides Phase 22.

---

### `app/build.gradle.kts` modification (config)

**Build-wiring (research Q1 — VERIFIED LEAVE-AS-IS):**
```kotlin
implementation(libs.compose.ui.tooling.preview)   // :103 — STAYS implementation (@Preview live in main; add explaining comment)
debugImplementation(libs.compose.ui.tooling)       // :104 — already correct, no change
```
Action = a code-comment on `:103` so a future cleanup doesn't "fix" it and break previews. Catalog entries verified: `libs.versions.toml:76-77`.

**Pseudolocale (research Q7):** add to the `debug` build type:
```kotlin
buildTypes { debug { isPseudoLocalesEnabled = true } }   // renders en-XA / ar-XB; no resConfigs trimming (none today)
```

---

## Shared Patterns

### Theme-token authority (apply to EVERY preview + the placeholder box)
**Source:** `theme/compose/DinghyTheme.kt:37-63` (the two overloads) + `theme/compose/LocalTokens.kt` (`staticCompositionLocalOf<ThemeTokens>`).
**Apply to:** every `@Preview` (wrap in `PreviewBox`/`DinghyTheme(resolver=…)`), `PreviewPlaceholderBox`, `DinghyIconView`.
**Rule:** read `LocalTokens.current`, never a raw color. The `fontScale=1f` pin at `:48` means `fs` is the ONLY text axis — inject `fs=L` via `ThemeTuple.fs`, not `@Preview(fontScale=)`.
```kotlin
// DinghyTheme.kt:46-50 — the ONE token+density boundary every subtree composes inside
CompositionLocalProvider(
    LocalTokens provides tokens,
    LocalDensity provides Density(density = baseDensity.density, fontScale = 1f),
    content = content,
)
```

### Pure bake seed (apply to the 6-combo PreviewBox)
**Source:** `AppContainer.kt:452-456` (`themeResolver.bake(tuple)`) / `ThemeResolver.kt:162` (the pure fn).
**Apply to:** `PreviewTheming.kt` seed list.

### `LocalInspectionMode` branch (apply to ALL host + Coil call sites in exemplars)
**Source:** NEW idiom (research Q5) — none in codebase yet.
**Apply to:** `GraphViewHost` (both overloads), `BedMeshHeatmapHost`, `WebcamViewHost`, and every `AsyncImage` in the 3 exemplars.

### Dev-gate (apply to the `start_dest` hook)
**Source:** `AppContainer.kt:242` (`devCyclerEnabled`, default FALSE, release-readable) + `ThemePrefs.kt:179` (`dev_cycler_enabled` key) + `ui/shell/DevThemeCyclerOverlay.kt` (dev-UI precedent).
**Apply to:** `MainActivity` `start_dest` read. NEVER `BuildConfig.DEBUG`.

### Write-scope discipline (apply ONLY if the hook writes)
**Source:** `AppContainer.kt:118` (`writeScope`) + the helper template `setBabystepEnabled` (`:206-208`).
**Apply to:** any DataStore write the dev plumbing makes. The `start_dest` read itself needs NO write.

### Font-size floor (apply to every exemplar preview)
**Source:** `theme/ThemeTokens.kt` `fsSp(base, fs)` (research cites `:199`).
**Apply to:** all exemplar text — floor 15sp metadata, 17-18sp body, 20-22sp titles, 26sp tabular, 30sp+ focus ([[dinghy-font-sizes-too-small]]). Existing correct usage to copy: `PrintStatusScreen.kt:988,1010` (`fsSp(40f, t.fs)`), `SpoolScreen.kt:250` (`fsSp(64f, t.fs)`).

### Sealed "one-of-N" modeling (apply to IconRef)
**Source:** `ui/route/TopRoute.kt:14-23` (`sealed interface TopRoute`) + `PrintStatusMode.kt:23-35`.
**Apply to:** `IconRef` (Ligature | Drawable) — compile-safe "exactly one source".

### Single-source-of-truth `object` registry (apply to DinghyIcons)
**Source:** `command/PrinterCommands.kt` (referenced, never redefined — see `PrintStatusMode.kt:74-78`).
**Apply to:** `DinghyIcons` — the ONE swap point + the new subset source for `tools/subset-symbols`.

---

## No Analog Found

Files with no close 1:1 match — planner uses RESEARCH.md idioms (all are platform-standard, cited there):

| File | Role | Reason |
|------|------|--------|
| `preview/DinghyPreviews.kt` (multipreview annotation classes) | preview-infra | No existing multipreview annotation in the codebase. Idiom: research Pattern 2 (`@Nexus7Previews`) + Q8 (`@DinghyThemePreviews`), cited to developer.android.com. |
| `res/values/strings.xml` | resource | 0% tokenized today (0 `stringResource`, no `strings.xml`). This phase ESTABLISHES the file + `<area>_<element>` convention. Platform-standard. |
| `LocalInspectionMode` branches | idiom | Used NOWHERE yet (verified). Brand-new this phase; platform API, research Q5. |
| `docs/ui_design/PREVIEW_AND_TOKENS.md` | docs | New convention doc; sibling `THEMING.md` is the format analog only. |

**Optional (planner decision, MEDIUM-confidence, deferred-friendly):** detekt + a Compose ruleset for the hardcoded-`Text("…")` gate (research Q7) — NOT wired today (no detekt config). If adopted: baseline-scoped off the ~240 deferred literals, `config/detekt/detekt.yml` + `detekt-baseline.xml`. Fallback = `en-XA` pseudolocale-visual-only gate (scope reduction of SC-3).

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/` (theme/, bench/, di/, designsystem/, render/, ui/route/, ui/shell/, ui/printstatus/, ui/finetune/, ui/spool/), `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/`.
**Files scanned:** ~20 read/grepped; 16 target files classified.
**Pattern extraction date:** 2026-06-06
