# Phase 3: Design System & Theming Foundation - Research

**Researched:** 2026-05-31
**Domain:** Android UI design-system implementation — Jetpack Compose + classic-Views hybrid (ADR 0001), translating a web/CSS-idiom token system (oklch, custom properties, aspect-ratio/fr/clamp) to a minSdk-23 / Adreno-320 native stack.
**Confidence:** HIGH (the locked decisions are sound; every load-bearing HOW is verified against official docs or the in-repo design law)

## Summary

This phase has no genuinely *unknown* architecture left — CONTEXT.md D-01..D-14 already settled the hard calls, and they are all correct for the constraints. The research job was to nail down the concrete HOW for eight de-risking questions and surface the API-23/Adreno-320 landmines hiding inside each locked decision. Every one resolves cleanly with an API-23-safe path:

- **oklch is a real trap, and D-03 dodges it exactly right.** Compose's `Color`/`ColorSpace` *defines* Oklab, but on API < 26 any non-sRGB color space **silently falls back to sRGB at render time** [VERIFIED: Android ColorSpace docs]. So an `oklch()` value handed to Compose on a Nexus 7 would render *wrong*, not crash — the worst kind of bug. Baking oklch→sRGB once (a checked-in Kotlin token table) is the only safe path. The conversion math is fully specified by CSS Color 4 (oklch→Oklab→linear-sRGB→sRGB).
- **The toolkit-agnostic `ThemeTokens` seam (D-05/D-06) is the load-bearing design.** It resolves to: one `StateFlow<ThemeTokens>` of plain sRGB `Color` + dp values; Compose reads it via `collectAsStateWithLifecycle()` at the theme boundary and republishes through a `staticCompositionLocalOf` (the standard design-token idiom); the Views graph collects the *same* flow and repaints via push-tokens + `invalidate()`. This is the correct shape and matches every official recommendation.
- **`--fs` as sole text authority (D-04) is a clean, documented Compose pattern:** override `LocalDensity` to force `fontScale = 1f` at the app root, then drive all type sizes from `--fs`-multiplied values. This is the standard kiosk/appliance technique [VERIFIED: ProAndroidDev, Android accessibility docs].
- **Geist/Geist Mono:** OFL-licensed (safe to bundle), ships static TTF weights, and **Geist Mono is monospaced** — so tabular numerals are *inherent to the glyph metrics*, no OpenType `tnum` feature needed (which sidesteps the real risk that `fontFeatureSettings` is unreliable on old ART).
- **Perf proof (#5)** reuses the Phase-1 harness verbatim: in-process synthetic feed → BenchActivity scene → `gfxinfo framestats` parser as system of record. The ring (Compose Canvas) and line graph (Views Canvas) get measured on the real `flox` tablet.

**Primary recommendation:** Build a single `core/theme` module exposing `ThemeTokens` as a `StateFlow`, baked sRGB tokens in a checked-in Kotlin table (with a one-time conversion script committed for traceability), a `staticCompositionLocalOf` bridge for Compose and a push-tokens adapter for the Views graph, a slot-based `ScreenScaffold` for Focus/Field/Gutter (Row/Column + `weight`/`aspectRatio` + `BoxWithConstraints`, NOT a custom `Layout`), and gate the gallery launcher behind a `src/debug/AndroidManifest.xml` source set. Add `androidx.datastore:datastore-preferences:1.1.7` (minSdk-23-safe) for persistence.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Semantic token resolution (THEME-01) | Plain-Kotlin resolver (`ThemeTokens` StateFlow) | — | Toolkit-agnostic by D-05; both Compose and Views consume it |
| Compose theme exposure | Compose (`staticCompositionLocalOf`) | — | The Compose adapter half of D-05 |
| Views graph theming | Classic Views (push-tokens + `invalidate()`) | — | The Views adapter half of D-06; graph is the only Views surface this phase |
| Focus/Field/Gutter layout (UI-01) | Compose (`ScreenScaffold` slot composable) | — | Shell/layout is Compose per ADR 0001 |
| `--fs` text-size authority (THEME-02) | Compose (`LocalDensity` override at root) | — | App-wide density override is a Compose-root concern |
| Progress ring primitive (D-11) | Compose Canvas | — | Low-churn single arc, themeable inline |
| Line-graph primitive (D-11) | Classic Views custom-Canvas | hosted via `AndroidView` | High-churn surface per ADR 0001 |
| Bounded ring-buffer holder (D-12) | Plain-Kotlin | — | Toolkit-agnostic, unit-testable, survives recreation |
| Settings persistence (THEME-02/D-02) | DataStore (Preferences) | — | Coroutine/Flow-native, feeds the StateFlow resolver |
| Gallery launcher (D-07/D-08) | Compose Activity + Views interop | `src/debug` source set | Debug-only launch surface |

## User Constraints (from CONTEXT.md)

### Locked Decisions (D-01..D-14 — DO NOT re-litigate)

- **D-01** — User-custom theme scope = accent + status roles only (`--accent`/`--heat`/`--go`/`--stop`/reasonably `--bg`); the remaining ~30 tokens derive from the chosen base. Override *mechanism* built here; editor UI is Phase 4.
- **D-02** — Custom = override-on-a-base: picks dark or light, stores only token **deltas**; unoverridden tokens inherit the base. Cheap to persist; half-configured custom is still usable.
- **D-03** — Bake oklch→sRGB once (build-time or checked-in table), store as Compose `Color`. Custom colors picked in sRGB. **No runtime oklch** (Compose Oklab color space is effectively unusable < API 26 — see Pitfall 1).
- **D-04** — `--fs` (S≈1.0 / M≈1.15 / L≈1.32; M is the larger default) is the SOLE text-size authority; applied to dp-based sizes so OS `fontScale` does not double-apply. Persist the S/M/L choice.
- **D-05** — One toolkit-agnostic `ThemeTokens` resolver = single source of truth, exposed as a `StateFlow`. Compose adapts via `CompositionLocal`; Views collects the same flow.
- **D-06** — Views repaint via push-tokens + `invalidate()` (no view recreation; preserves ring-buffer + graph state across theme/orientation change).
- **D-07** — In-APK component gallery is the primary preview surface (on real Nexus 7). Compose `@Preview` is secondary.
- **D-08** — Gallery is the TEMP dev launcher this phase, gated out of the release APK. Phase 4 routing replaces it.
- **D-09** — **No screenshot-regression tests** in v1 (no Paparazzi/Roborazzi). On-device gallery + manual review.
- **D-10** — Prove criterion #5 with a synthetic 2-4 Hz feed + `gfxinfo` on the real `flox` tablet (reuse Phase-1 methodology; gfxinfo is system of record).
- **D-11** — Ring = Compose Canvas; line graph = Views custom-Canvas (hosted via `AndroidView`).
- **D-12** — Bounded ring-buffer in a toolkit-agnostic plain-Kotlin holder, fed by the throttled StateFlow; unit-testable; survives recreation/rotation/theme swap.
- **D-13** — Motion: static redraw at the throttled cadence + a cheap one-shot screen-entry "draw-on" ONLY. **No per-update tween, no breathing dot, no perpetual sheen.** (The hi-fi doc's "status dot breathes" / sheen / pulse animations are explicitly OUT.)
- **D-14** — Wire the gallery primitive to BOTH the synthetic feed (deterministic perf proof) and the live Phase-2 `PrinterStateStore` StateFlow (proves the seam end-to-end).

### Claude's Discretion (planner's call — research recommends below)

- Exact Focus/Field/Gutter Compose primitive shape (slot composable vs custom `Layout`) — **recommendation: slot-based `ScreenScaffold`** (Pattern 1).
- Geist/Geist Mono static-weight selection + `res/font` wiring — **recommendation: 4 static weights, monospace digits inherently tabular** (Standard Stack + Pattern 2).
- Per-component visual token mapping, exact Confirm-guard / single-setting-page / severity-toast contracts, module/package layout — driven by `hifi.css` + mockups.

### Deferred Ideas (OUT OF SCOPE)

- Full all-token custom-theme editor (beyond accent + status).
- Runtime oklch / perceptual custom-color picking (D-03 bakes to sRGB).
- Screenshot-regression tests (Paparazzi/Roborazzi).
- The Settings-screen theme/text-size/connection EDITING UI — Phase 4 (SET-01). Phase 3 builds only the override *mechanism* + gallery.
- PRIM-05 command-dispatch primitive — Phase 4.
- Real launcher / App Drawer / foreground service — Phase 4.
- Full temperature-history graph backfilled from `server.temperature_store` — Phase 5 EXTENDS this phase's line-graph primitive.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| THEME-01 | Semantic-token theme system (dark/light/custom), every component references role tokens | `ThemeTokens` resolver (Pattern 3) + baked sRGB table (Pattern 4); override-on-base for custom |
| THEME-02 | S/M/L `--fs` multiplier, persisted | `LocalDensity` override (Pattern 5) + DataStore persistence (Standard Stack) |
| UI-01 | Focus/Field/Gutter responsive grammar, one shared grid, portrait + landscape | `ScreenScaffold` slot composable (Pattern 1) |
| UI-02 | Outline-led control language + intent colors (≥64px targets) | Token-driven `OutlinedControl` composable mapping `hifi.css` `.ctl`/`.ctl.accent/.warn/.danger/.go` (Code Examples) |
| PRIM-01 | Single-setting scrubber/stepper page (keyboard-free numeric) | `hifi.css` `.setval`/`.fillbar`/`.scrubber`/`.adjrow` contract; built on `ScreenScaffold` |
| PRIM-03 | Full-screen Confirm guard | `hifi.css` `.alert`/`.confirm-acts` contract (Field = CONFIRM/CANCEL, no gutter) |
| PRIM-04 | Severity-styled toast (info/success/warning/error — color + icon + text) | Token-driven severity → `--accent`/`--go`/`--heat`/`--stop` mapping; never color alone (icon + text) |

## Standard Stack

### Core (already in `libs.versions.toml` — no change)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Compose (BOM) | `2026.05.00` → Compose UI 1.11.1 | Shell, most panels, ring | Already pinned; `Modifier.weight`/`aspectRatio`/`Canvas`/`BoxWithConstraints` cover all layout needs [VERIFIED: libs.versions.toml] |
| `compose.foundation` | via BOM | `Canvas`, `BoxWithConstraints`, `Layout` | Already declared |
| `compose.material3` | via BOM | Components base | Already declared |
| `androidx.recyclerview` | 1.3.2 | (Views interop infra) | Already declared; not core to this phase but present |
| `androidx.lifecycle-runtime-compose` | 2.8.7 | `collectAsStateWithLifecycle()` at the theme boundary | Already declared — the seam D-05 needs [VERIFIED: libs.versions.toml] |
| kotlinx-coroutines | 1.9.0 | StateFlow plumbing for `ThemeTokens` | Already declared |

### Supporting (NEW — add to catalog)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.datastore:datastore-preferences` | **1.1.7** | Persist S/M/L choice + theme selection + custom token deltas (D-02/D-04) | This phase introduces persistence. 1.1.x line is minSdk-23-safe (AndroidX default floor since June 2025). See Pitfall 6 on version choice. |

**Geist / Geist Mono fonts** are **bundled `res/font/` assets, NOT a Gradle dependency** [VERIFIED: in-repo CONTEXT.md note + Fontsource]. Download static TTFs from the official Vercel repo or Fontsource; they are **SIL Open Font License 1.1** — free to bundle in a shipped APK [VERIFIED: github.com/vercel/geist-font states OFL 1.1].

**Recommended static weights to bundle** (no variable fonts — variable-font support is API 26+, floor is 23):

| File | Weight | Use |
|------|--------|-----|
| `geist_regular.ttf` | 400 | body / labels |
| `geist_medium.ttf` | 500 | most UI text (`hifi.css` uses 500/550 heavily) |
| `geist_semibold.ttf` | 600 | hero values, button labels, headings |
| `geist_bold.ttf` | 700 | the big axis glyphs / biglabel |
| `geist_mono_medium.ttf` | 500 | live numeric data (`.mono`) |
| `geist_mono_semibold.ttf` | 600 | hero mono values (`.s .v.mono`, `.tv`) |

Six static TTFs ≈ ~600KB-1MB total; acceptable for an armeabi-v7a-only APK. Trim to 4 (drop Bold, fold mono-600 into one) if APK size bites — but `hifi.css` genuinely uses 400/500/600/700, so 6 is the faithful set. See Pitfall 2 on tabular numerals.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Checked-in baked sRGB token table | Gradle build-time codegen of the table | Codegen is "purer" (single oklch source) but adds build complexity + a custom Gradle task to maintain on a solo project. A checked-in table + a committed one-time conversion script is simpler and equally traceable (Pattern 4). **Recommend checked-in table.** |
| Slot-based `ScreenScaffold` | Custom `Layout` | Custom `Layout` gives pixel-exact grid-line control but is far more code and harder to read. Row/Column + `weight`/`aspectRatio` + `BoxWithConstraints` expresses the whole Focus/Field/Gutter grammar (Pattern 1). **Recommend slot composable.** |
| DataStore Preferences | SharedPreferences | SharedPreferences is simpler but main-thread I/O risks ANR and isn't Flow-native (doesn't compose with the StateFlow resolver). DataStore is the locked-stack choice. |
| `staticCompositionLocalOf` | `compositionLocalOf` | See Pitfall 3 — `static` is correct *because* a theme swap should recompose the whole subtree (a theme change touches nearly every node anyway), and it's cheaper to read. |

**Installation (Gradle version catalog — never inline):**
```toml
# [versions]
datastore = "1.1.7"
# [libraries]
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```
```kotlin
// app/build.gradle.kts
implementation(libs.androidx.datastore.preferences)
```

**Version verification:** `androidx.datastore:datastore-preferences` 1.1.x line is the stable minSdk-23 series (1.1.2 Jan 2025 → 1.1.7 in the 1.1.x line; 1.2.1 is current stable but stays on the same minSdk-23 floor). [VERIFIED: developer.android.com/jetpack/androidx/releases/datastore + AndroidX minSdk-23 default since June 2025]. Pin whichever 1.1.x the planner confirms via `gw.bat` dependency resolution; do NOT jump to a `1.3.0-alpha`.

## Package Legitimacy Audit

> This phase adds exactly ONE new dependency (DataStore). Fonts are bundled assets (OFL 1.1), not packages. slopcheck is a PyPI/npm tool and does not apply to AndroidX/Maven; legitimacy is established by the package living under the official `androidx.datastore` group on Google's Maven repo.

| Package | Registry | Age | Source Repo | Verdict | Disposition |
|---------|----------|-----|-------------|---------|-------------|
| `androidx.datastore:datastore-preferences` | Google Maven (androidx) | 4+ yrs (1.0 2021) | android.googlesource.com (AndroidX) | First-party Google | Approved |
| Geist / Geist Mono (TTF) | github.com/vercel/geist-font (OFL 1.1) | 2+ yrs | vercel/geist-font | First-party Vercel, OFL | Approved — bundle TTFs only, verify minSdk-23 unaffected (assets carry no minSdk) |

**Packages removed due to slopcheck [SLOP] verdict:** none (slopcheck N/A for Maven; both deps are first-party).
**Packages flagged as suspicious [SUS]:** none.

*Provenance note: DataStore confirmed via official Android release docs (authoritative). The `1.1.7` exact pin is `[ASSUMED]` pending the planner's `gw.bat` resolution — any stable `1.1.x` is minSdk-23-safe; the planner should confirm the exact resolved version and that `verifyMinSdk` still passes.*

## Architecture Patterns

### System Architecture Diagram

```
                 ┌──────────────────────────────────────────────────┐
   user S/M/L +  │              core/theme  (plain Kotlin)          │
   theme choice  │                                                  │
   + custom      │   DataStore(Prefs) ──read──► ThemeResolver       │
   deltas        │      (persist)               │                   │
        │        │                              ▼                   │
        └───────►│   BAKED sRGB token table ──► resolve(base,       │
   (Phase-4 UI   │   (TokensDark / TokensLight    deltas, fs) ──►   │
    writes;       │    checked-in Compose Color)                │   │
    P3 = mech.)  │                                             ▼   │
                 │                       StateFlow<ThemeTokens>     │
                 └───────────────┬───────────────────────┬─────────┘
                                 │ (one source of truth)  │
              collectAsStateWithLifecycle()        .collect { push }
                                 │                        │
                     ┌───────────▼─────────┐   ┌──────────▼──────────────┐
                     │  Compose adapter    │   │   Views adapter         │
                     │ staticComposition   │   │  (AndroidView host):    │
                     │ LocalOf(LocalTokens)│   │  view.tokens = t;       │
                     │                     │   │  view.invalidate()      │
                     └──────┬──────────────┘   └──────────┬──────────────┘
                            │                             │
        ┌───────────────────┼──────────────┐    ┌─────────▼─────────────┐
        ▼                   ▼              ▼    │  GraphView (custom     │
  ScreenScaffold      OutlinedControl  Ring     │  Canvas) reads tokens  │
  (Focus/Field/       ConfirmGuard    (Compose   │  at draw time;         │
   Gutter)            SeverityToast    Canvas)    │  draws ring-buffer     │
        │              ScrubberPage              └─────────▲─────────────┘
        │                                                  │
        ▼                                       ┌──────────┴───────────┐
  LocalDensity(fontScale=1f) at app root        │  RingBuffer holder   │
  → --fs is sole text authority                 │  (plain Kotlin, D-12)│
                                                └──────────▲───────────┘
                                                           │
  ┌────────────────── data in ────────────────────────────┘
  │  SyntheticFeed (2-4 Hz, deterministic)  ──► perf proof (D-10)
  │  PrinterStateStore.printerState StateFlow ──► live temps (D-14)
  └──────────────────────────────────────────────────────────────
```

### Recommended Project Structure

```
app/src/main/java/works/mees/dinghy/
├── theme/
│   ├── ThemeTokens.kt          # @Immutable data class: sRGB Color + dp + shape values
│   ├── BakedTokens.kt          # checked-in TokensDark / TokensLight (sRGB Color literals)
│   ├── ThemeResolver.kt        # plain-Kotlin: base + deltas + fs → StateFlow<ThemeTokens>
│   ├── ThemePrefs.kt           # DataStore: S/M/L + base + custom deltas (THEME-02/D-02)
│   ├── compose/
│   │   ├── LocalTokens.kt      # staticCompositionLocalOf<ThemeTokens>
│   │   └── DinghyTheme.kt      # collects flow, LocalDensity(fontScale=1f), provides locals
│   └── views/
│       └── ThemeableView.kt    # interface: fun applyTokens(t: ThemeTokens)
├── designsystem/
│   ├── layout/ScreenScaffold.kt   # Focus/Field/Gutter slot composable (UI-01)
│   ├── control/OutlinedControl.kt # the .ctl language (UI-02)
│   ├── ConfirmGuard.kt            # PRIM-03
│   ├── ScrubberPage.kt            # PRIM-01
│   └── SeverityToast.kt           # PRIM-04
├── render/
│   ├── RingBuffer.kt           # plain-Kotlin bounded holder (D-12)
│   ├── ProgressRing.kt         # Compose Canvas (D-11)
│   └── GraphView.kt            # classic View custom-Canvas (D-11), ThemeableView
└── gallery/                    # (see src/debug below for the launcher)
    └── GalleryScreen.kt        # token × component × theme × fs matrix

app/src/debug/
├── AndroidManifest.xml         # registers GalleryActivity as LAUNCHER (debug only, D-08)
└── java/.../gallery/GalleryActivity.kt
```

### Pattern 1: Focus / Field / Gutter as a slot-based `ScreenScaffold` (UI-01)

**What:** A composable taking `focus`, `field`, `gutter` slot lambdas; reads orientation via `BoxWithConstraints` (or `LocalConfiguration`) and lays out portrait (Column, stacked, full-width) vs landscape (Row 50/50 stage + full-width gutter Row). No hardcoded px — `Modifier.weight` for region splits, `Modifier.aspectRatio(1f)` for sacred squares centered in their cell.

**When to use:** Every screen in the app. This is *the* layout primitive.

**Why slot composable over custom `Layout`:** The grammar maps 1:1 onto Row/Column + `weight`/`aspectRatio`. `weight` IS the `fr`/`flex` equivalent; `aspectRatio` IS CSS `aspect-ratio`; `fillMaxSize` + `weight` IS the percentage sizing. A custom `Layout` buys pixel-exact grid-line math but at a large readability/maintenance cost on a solo project. [CITED: developer.android.com/develop/ui/compose/layout]

```kotlin
// The one-shared-grid trick: in landscape, stage is a Row of two weight(1f) halves;
// the gutter is a separate full-width Row below. Because BOTH the stage Row and the
// gutter Row fill the same content-area width, a 3-equal-button gutter's middle-button
// center lands on the Focus/Field divide automatically (LAYOUT.md §1).
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    focus: (@Composable ColumnScope.() -> Unit)? = null,
    field: (@Composable ColumnScope.() -> Unit)? = null,
    gutter: (@Composable () -> Unit)? = null,   // null when Field IS the navigation
    focusGrow: Float = 1f,   // --focus-grow
    fieldGrow: Float = 1f,   // --field-grow
) {
    BoxWithConstraints(modifier) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            // ---- stage ----
            if (landscape) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    focus?.let { Column(Modifier.weight(focusGrow).fillMaxHeight(), content = it) }
                    field?.let { Column(Modifier.weight(fieldGrow).fillMaxHeight(), content = it) }
                }
            } else {
                focus?.let { Column(Modifier.fillMaxWidth().weight(focusGrow), content = it) }
                field?.let { Column(Modifier.fillMaxWidth().weight(fieldGrow), content = it) }
            }
            // ---- gutter (full-width row, same grid) ----
            gutter?.let { Box(Modifier.fillMaxWidth()) { it() } }
        }
    }
}
```
Sacred-square content (ring, jog pad) goes INSIDE its region wrapped in `Modifier.aspectRatio(1f)` + center alignment — never make the region itself square (matches `hifi.css` `.ringblock .ringwrap{aspect-ratio:1}` and the explicit comment at hifi.css L267-271).

### Pattern 2: Geist FontFamily on API 23 (static weights, monospace = tabular)

**What:** Place static TTFs in `res/font/`, build a Compose `FontFamily` mapping each weight to its file. No variable fonts, no `fontFeatureSettings`.

**Why API-23-safe:** Compose `Font(R.font.geist_regular, FontWeight.Normal)` over static files works on API 21+. Variable-font axis selection is API 26+; avoid it. Geist Mono is *monospaced*, so digit advance widths are uniform by construction — `font-variant-numeric:tabular-nums` from the CSS is automatically satisfied without needing the OpenType `tnum` feature (which old ART may not honor reliably). [VERIFIED: Fontsource confirms Geist Mono is monospace]

```kotlin
val Geist = FontFamily(
    Font(R.font.geist_regular,  FontWeight.Normal),   // 400
    Font(R.font.geist_medium,   FontWeight.Medium),   // 500
    Font(R.font.geist_semibold, FontWeight.SemiBold), // 600
    Font(R.font.geist_bold,     FontWeight.Bold),     // 700
)
val GeistMono = FontFamily(
    Font(R.font.geist_mono_medium,   FontWeight.Medium),
    Font(R.font.geist_mono_semibold, FontWeight.SemiBold),
)
```
For the Views graph axis labels (mono numbers), set the `Paint` typeface from the same TTF via `ResourcesCompat.getFont(context, R.font.geist_mono_medium)`.

### Pattern 3: The `ThemeTokens` resolver + Compose `staticCompositionLocalOf` bridge (D-05)

**What:** `ThemeTokens` is an `@Immutable data class` of resolved sRGB `Color`s + dp shape values + the `fs` multiplier. The `ThemeResolver` combines (base, deltas, fs) into a `StateFlow<ThemeTokens>`. `DinghyTheme` collects it at the boundary and provides it through a `staticCompositionLocalOf`.

```kotlin
@Immutable
data class ThemeTokens(
    val bg: Color, val surface: Color, val text: Color, val textMuted: Color,
    val outline: Color, val accent: Color, val heat: Color, val go: Color, val stop: Color,
    /* …~30 roles… */
    val rCard: Dp, val rCtrl: Dp, val fs: Float,
)

val LocalTokens = staticCompositionLocalOf<ThemeTokens> { error("No ThemeTokens provided") }

@Composable
fun DinghyTheme(resolver: ThemeResolver, content: @Composable () -> Unit) {
    val tokens by resolver.tokens.collectAsStateWithLifecycle()  // boundary collection
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalTokens provides tokens,
        // D-04: neutralize OS fontScale so --fs is sole authority (Pattern 5)
        LocalDensity provides Density(density = base.density, fontScale = 1f),
        content = content,
    )
}
```
Components read `LocalTokens.current.accent` etc. — never a raw color. A theme swap re-emits the flow → new `tokens` → the whole `DinghyTheme` content recomposes (correct, since a theme change touches nearly everything). [VERIFIED: developer.android.com/develop/ui/compose/compositionlocal + droidcon static-vs-dynamic analysis]

**Views half (D-06):** the `AndroidView` host collects the SAME `resolver.tokens` flow (via the host Activity/composable's lifecycle scope) and on each emission calls `graphView.applyTokens(t)` which stores the tokens and calls `invalidate()`. The View re-reads `tokens.accent` etc. inside `onDraw`. No view recreation.

```kotlin
AndroidView(
    factory = { ctx -> GraphView(ctx) },
    update = { view -> view.applyTokens(currentTokens); view.setData(ringSnapshot) }
)
// 'currentTokens' = resolver.tokens.collectAsStateWithLifecycle() in the composable
// hosting the AndroidView — so a theme flip recolors the Canvas graph too.
```

### Pattern 4: oklch → sRGB bake (D-03) — checked-in table + committed conversion script

**What:** A one-time Python (or Kotlin) script reads the oklch literals from THEMING.md/hifi.css, runs the CSS Color 4 pipeline, and emits `BakedTokens.kt` with exact `Color(0xFFRRGGBB)` literals. Commit BOTH the script and its output so the table is *traceable* (re-runnable, diffable against hifi.css).

**The conversion pipeline** (oklch → Oklab → linear-sRGB → sRGB) [VERIFIED: Oklab Wikipedia + CSS Color 4 §15/§11.2]:
1. **oklch → Oklab:** `L=L`, `a = C·cos(h·π/180)`, `b = C·sin(h·π/180)`.
2. **Oklab → LMS:** apply the inverse `M2` matrix, then cube each component (`l = l_'³` …).
3. **LMS → linear-sRGB:** apply the inverse `M1` matrix.
4. **linear → sRGB gamma:** `v ≤ 0.0031308 ? 12.92·v : 1.055·v^(1/2.4) − 0.055`, clamp to [0,1], ×255 round.

For alpha-bearing tokens (`--accent-soft …/.16`) bake the RGB and keep the alpha as `Color(...).copy(alpha = 0.16f)` or a premultiplied literal.

**Reliable reference to cross-check the table:** any oklch→hex tool (e.g. oklch.com, the CSS spec sample code) — pick a couple of tokens and verify the script output matches. **Do not eyeball; verify ≥3 tokens against an external converter.** Out-of-gamut oklch values (high chroma) must be gamut-mapped (clamp chroma) — the bright accent/stop reds are the ones to check.

**Traceability guard:** add a unit test that asserts a handful of baked literals equal known-good hex values (so a careless edit to BakedTokens.kt is caught). This is the "doesn't silently drift" safety net.

### Pattern 5: `--fs` as sole text authority (D-04, THEME-02)

**What:** Override `LocalDensity` to force `fontScale = 1f` (Pattern 3), so `sp` no longer tracks the OS accessibility setting. Then express type sizes as `baseDp * fs` where `fs ∈ {1.0, 1.15, 1.32}`. With fontScale pinned to 1f, `sp` and `dp` coincide — the app's `--fs` is the only multiplier in play. [VERIFIED: ProAndroidDev "Preventing Font Scaling in Jetpack Compose"; Android "Support user-scalable content" docs]

```kotlin
// type sizes derive from --fs only:
fun fsSp(baseSp: Float, fs: Float) = (baseSp * fs).sp   // e.g. fsSp(16f, tokens.fs)
```

**Tradeoff acknowledged:** this *defeats* OS accessibility font scaling — deliberate per CONTEXT.md (a dedicated arm's-length appliance screen wants predictable layout; the owner doesn't manage device-level fontScale). Document it as an intentional accessibility trade, not an oversight.

### Anti-Patterns to Avoid

- **Handing an `oklch()`-derived Compose `Color` to the renderer on API 23.** It silently renders as sRGB-fallback (wrong color, no error). Always pass *baked sRGB* literals. (Pitfall 1)
- **Per-frame allocation in `GraphView.onDraw`** (new `Path`/`Paint`/array each draw) → GC churn → tail-latency jank on Adreno 320. Reuse a single `Path`, `Paint`, and the ring buffer's backing array. (Pitfall 4)
- **Driving `invalidate()` from the Choreographer every frame** for the graph. Invalidate only when a new throttled sample arrives (~2-4 Hz), per D-13. No continuous animation loop.
- **Animated glow / breathing / sheen** (the hi-fi CSS `@keyframes breathe/sheen/draw/logopulse/dotpulse/knobpulse`). All OUT per D-13. Use a *static* shadow/blur layer for glow.
- **Reading a rapidly-changing token (temps) high in the Compose tree.** Keep live-value reads as low as possible so recomposition scope stays tight (matches the CLAUDE.md Compose-perf guidance).
- **A second density/fontScale path somewhere downstream** re-introducing OS scaling after the root override. Keep the override at exactly one place (the theme root).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| oklch→sRGB conversion at runtime | A runtime oklch parser/converter | Bake to sRGB once at build/author time (Pattern 4) | Runtime oklch is API-26+ and pointless on the target; baking is exact and free at render time |
| Settings persistence | Hand-rolled file/SharedPreferences wrapper | DataStore Preferences | Coroutine/Flow-native, no ANR, composes with the StateFlow resolver |
| Lifecycle-aware flow collection | Manual `lifecycleScope.launch` + `repeatOnLifecycle` plumbing in every composable | `collectAsStateWithLifecycle()` | One-liner, already in the catalog (lifecycle-runtime-compose 2.8.7) |
| Tabular numerals | OpenType `fontFeatureSettings("tnum")` on old ART | Use Geist **Mono** (monospace = inherently tabular) for live data | `tnum` honoring is unreliable on old ART; monospace sidesteps it entirely |
| Debug-only launcher gating | Runtime `if (BuildConfig.DEBUG)` branching in MainActivity | `src/debug/AndroidManifest.xml` source set with the LAUNCHER `<activity>` | Variant source sets keep the gallery fully ABSENT from the release manifest/APK (D-08), not just hidden |
| Orientation detection | Listening to configuration-change broadcasts | `BoxWithConstraints` (`maxWidth>maxHeight`) or `LocalConfiguration.orientation` | Compose recomposes on config change automatically |

**Key insight:** The whole phase is about *not* hand-rolling a per-screen visual treatment — the substrate exists so later panels are *assembled* from settled tokens/components. The single highest-leverage anti-hand-roll is the baked-token table: it makes "a theme is a token remap" literally true across both toolkits.

## Common Pitfalls

### Pitfall 1: oklch Color silently renders as sRGB-fallback on API 23 (the central trap)
**What goes wrong:** You construct a Compose `Color` in the Oklab color space (or pass oklch-derived values expecting perceptual rendering). On API < 26 it does NOT error — it falls back to sRGB at the platform render layer, producing the *wrong* color on the exact target device, while looking correct in the IDE preview / on a modern phone.
**Why it happens:** Compose defines the Oklab `ColorSpace` but "color spaces not supported for platform rendering operations … or not available on the current API level will safely fall back to the Srgb color space." [VERIFIED: developer.android.com ColorSpace reference]
**How to avoid:** D-03 — bake to exact sRGB literals once (Pattern 4); never let an oklch value reach the renderer. Verify the baked table against an external oklch→hex converter.
**Warning signs:** colors look slightly off (especially the chroma-heavy accent blue / stop red) on the flox tablet but right in `@Preview`.

### Pitfall 2: Bundling variable Geist fonts (API 26+) instead of static weights
**What goes wrong:** You bundle `Geist[wght].ttf` (variable). On API 23 the weight axis isn't honored; you get one default weight everywhere.
**Why it happens:** Variable-font axis support is API 26+. The floor is 23.
**How to avoid:** Bundle individual static-weight TTFs (Pattern 2). Download the *static* set, not the variable file.
**Warning signs:** all UI text looks the same weight on-device despite `FontWeight` differences.

### Pitfall 3: Wrong CompositionLocal flavor for tokens
**What goes wrong:** Using `compositionLocalOf` (dynamic) for tokens adds read-tracking overhead for no benefit; or expecting `staticCompositionLocalOf` to update only the readers (it recomposes the whole provided subtree).
**Why it happens:** Misreading the two flavors' semantics.
**How to avoid:** Use `staticCompositionLocalOf` for tokens (Pattern 3). A theme change touches nearly every node, so recomposing the subtree is appropriate AND reads are cheaper (untracked). [VERIFIED: Android docs + droidcon analysis]
**Warning signs:** none functional; it's a perf/correctness-of-intent choice.

### Pitfall 4: GraphView per-frame allocation → tail-latency jank on Adreno 320
**What goes wrong:** Allocating `Path`/`Paint`/`FloatArray` inside `onDraw` causes GC churn; on a fill-rate-bound weak GPU this shows as p95 tail jank (exactly the metric ADR 0001 measured).
**Why it happens:** Naive Canvas code rebuilds objects every draw.
**How to avoid:** Hold one reusable `Path` (rewind, don't recreate), pre-allocated `Paint`s themed via `applyTokens`, and draw straight from the ring buffer's backing array. Downsample/cap the point count to the pixel width (no point drawing more vertices than horizontal pixels). Keep fill (the `.g-area` translucent fill) cheap — a single filled path, no per-point gradients.
**Warning signs:** gfxinfo p95 climbs during the graph scene; visible stutter on the flox.

### Pitfall 5: Letting the gallery launcher leak into the release APK
**What goes wrong:** A runtime `BuildConfig.DEBUG` guard leaves the GalleryActivity *registered* in the release manifest (dead code shipped, and it could be launched via intent).
**Why it happens:** Gating at runtime instead of at the manifest/source-set level.
**How to avoid:** Put the LAUNCHER `<activity>` only in `src/debug/AndroidManifest.xml` and the class only in `src/debug/java/...` (D-08). The release variant never sees it. Keep `MainActivity` as the (placeholder) release launcher.
**Warning signs:** `aapt dump badging` on the release APK shows the gallery activity / a second launcher.

### Pitfall 6: A DataStore (or any new dep) transitively raising the merged-manifest minSdk
**What goes wrong:** A new dependency (or its transitives) pulls a minSdk-24+ artifact, breaking the floor.
**Why it happens:** Pins stop a *direct* bump but not a transitive one — exactly why the repo has the `verifyMinSdk` task.
**How to avoid:** Add DataStore to the catalog, then run the `verifyMinSdk` build-logic task; it asserts the MERGED manifest stays 23. DataStore 1.1.x is minSdk-23 by the AndroidX June-2025 default, so this should pass — but *prove it*, don't assume. [VERIFIED: in-repo libs.versions.toml header + verifyMinSdk task]
**Warning signs:** `verifyMinSdk` fails after adding the dependency.

### Pitfall 7: The custom theme drifting from its base (D-02 deltas)
**What goes wrong:** A custom theme stores absolute values for some tokens; when the base changes (or D-01's derive logic updates), the custom theme no longer tracks.
**Why it happens:** Persisting full token sets instead of deltas.
**How to avoid:** Persist ONLY the overridden token keys (D-02 deltas). `resolve()` = baseTokens then apply deltas. A unit test should assert that an empty delta set === the base, and that a single delta overrides exactly one token.
**Warning signs:** changing the base leaves a custom theme half-stale.

## Code Examples

### Token-driven outline control (UI-02) — maps `hifi.css` `.ctl` family
```kotlin
enum class Intent { Neutral, Accent, Warn, Danger, Go }  // white/blue/amber/red/green

@Composable
fun OutlinedControl(label: String, intent: Intent = Intent.Neutral,
                    onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val outline = when (intent) {
        Intent.Neutral -> t.outline
        Intent.Accent  -> t.accentLine
        Intent.Warn    -> t.heat        // "proceed at peril"
        Intent.Danger  -> t.stop        // stop/cancel/back
        Intent.Go      -> t.go          // accept/confirm
    }
    Box(
        modifier
            .heightIn(min = 64.dp)                         // ≥64px touch floor (LAYOUT.md exception #2)
            .clip(RoundedCornerShape(t.rCtrl))
            .border(2.dp, outline, RoundedCornerShape(t.rCtrl)) // 2px outline = the affordance
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontFamily = Geist, fontSize = fsSp(18f, t.fs), color = t.text) }
    // glow = a STATIC shadow/elevation layer (D-13) — never an animated one.
}
```

### Progress ring (D-11) — Compose Canvas, single arc, no animation
```kotlin
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Canvas(modifier.aspectRatio(1f)) {        // sacred square (LAYOUT.md §2)
        val stroke = size.minDimension * 0.06f
        drawArc(t.surface2, -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round)) // track
        drawArc(t.accent, -90f, 360f * progress, false,                                       // arc
                style = Stroke(stroke, cap = StrokeCap.Round))
    }
    // Redraws only when 'progress' changes at the throttled cadence (D-13). No tween.
}
```

### GraphView (D-11/D-12) — classic View custom-Canvas, reused Path, themed via push
```kotlin
class GraphView(ctx: Context) : View(ctx) {
    private val line = Path()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * resources.displayMetrics.density }
    private var buffer: FloatArray = FloatArray(0)   // snapshot from the ring buffer (D-12)
    private var count = 0

    fun applyTokens(t: ThemeTokens) { linePaint.color = t.accent.toArgb(); invalidate() } // D-06 push
    fun setData(snapshot: FloatArray, n: Int) { buffer = snapshot; count = n; invalidate() } // 2-4 Hz only

    override fun onDraw(c: Canvas) {
        if (count < 2) return
        line.rewind()                                  // reuse, no per-frame alloc (Pitfall 4)
        val dx = width.toFloat() / (count - 1)
        // …map buffer[i] to y, line.lineTo(i*dx, y)…
        c.drawPath(line, linePaint)
    }
}
```

### Debug-only gallery launcher (D-08) — `app/src/debug/AndroidManifest.xml`
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application>
    <activity android:name=".gallery.GalleryActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>
  </application>
</manifest>
```
Release keeps only `MainActivity` as LAUNCHER (the placeholder). The merge tool adds the gallery LAUNCHER only in the debug variant. (Two launchers in debug is fine; if undesirable, demote MainActivity's debug launcher category via a debug manifest tweak — planner's call.)

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `sp` text scaling honoring OS fontScale | `LocalDensity(fontScale=1f)` override for appliance UIs | Stable Compose pattern | Lets `--fs` be sole authority (D-04) |
| Variable fonts | Static weights for API < 26 | N/A (API constraint) | Must bundle static TTFs |
| Runtime oklch (CSS Color 4 in browsers) | Bake to sRGB for native API-23 | N/A | D-03 — render-time correctness |
| Manual `repeatOnLifecycle` flow collection | `collectAsStateWithLifecycle()` | lifecycle-runtime-compose | Already in catalog; use at theme boundary |

**Deprecated/outdated:** The hi-fi CSS animations (`@keyframes breathe/sheen/draw/logopulse/dotpulse/knobpulse`) are explicitly superseded by D-13 (Adreno animation ban) — do NOT port them.

## Validation Architecture

> `workflow.nyquist_validation` was not found set to false; treating as enabled. Note D-09: NO screenshot-regression tests in v1.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 (`junit:4.13.2`) for JVM unit tests; AndroidX Test + `:macrobenchmark` (`benchmark-macro-junit4:1.3.3`) for the on-device perf proof — all already in `libs.versions.toml` |
| Config file | `app/build.gradle.kts` (test deps), `macrobenchmark/` module (Phase-1 harness) |
| Quick run command | `cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` (JVM unit tests) |
| Full suite command | unit tests + on-device gallery `gfxinfo framestats` capture on `flox` (D-10) |

### Five Success Criteria → Validation Map
The five criteria are: (1) token theme system dark/light/custom works; (2) S/M/L text-size works; (3) Focus/Field/Gutter responsive in both orientations; (4) outline-led controls + components render per the design; (5) ring + line-graph render the bounded buffer at ~2-4 Hz WITHOUT jank on the real device.

| Criterion | Validation Type | Method | System of Record |
|-----------|-----------------|--------|------------------|
| #1 token theme (THEME-01) | manual + unit | Gallery renders every component × dark/light/custom on flox; **unit test** asserts baked-token hex correctness (Pattern 4 traceability) + delta-resolution (Pitfall 7) | Manual eyeball vs `docs/ui_design` mockups; unit test for the table |
| #2 S/M/L (THEME-02) | manual + unit | Gallery toggles S/M/L; **unit test** asserts `fsSp(base, fs)` math and that DataStore round-trips the choice | Manual eyeball; unit test |
| #3 Focus/Field/Gutter (UI-01) | manual | Gallery shows scaffold instances in portrait + landscape on flox; verify grid-line alignment + sacred squares vs `LAYOUT.md` non-negotiables | Manual visual review (host-side screenshot tests CANNOT catch the real risks — D-09) |
| #4 controls + components (UI-02, PRIM-01/03/04) | manual | Gallery renders OutlinedControl intents, ConfirmGuard, ScrubberPage, SeverityToast | Manual review vs hi-fi mockups |
| **#5 ring + graph perf (D-10/D-11)** | **automated on-device** | **Synthetic 2-4 Hz feed → gallery ring+graph scene → `dumpsys gfxinfo framestats` → Phase-1 parser; p50/p90/p95 + frozen-frame count on real flox, release build** | **gfxinfo framestats (THE system of record)** — matches ADR 0001 floors (p50 ≪ 16.6ms, zero frames > 700ms; aim p95 ≤ ~42ms Views-graph parity) |

### Sampling Rate
- **Per task commit:** `:app:testReleaseUnitTest` (baked-token + fs-math + ring-buffer + delta-resolution unit tests).
- **Per wave merge:** full unit suite + an on-device gallery smoke install on flox.
- **Phase gate (criterion #5):** the gfxinfo framestats perf proof on flox, release build, p95 within ADR-0001 tolerance. Hard evidence, not vibes (D-10).

### Wave 0 Gaps
- [ ] `app/src/test/.../theme/BakedTokensTest.kt` — assert ≥3 baked sRGB literals == external-converter hex (Pattern 4 drift guard).
- [ ] `app/src/test/.../theme/ThemeResolverTest.kt` — empty delta === base; single delta overrides exactly one token (Pitfall 7).
- [ ] `app/src/test/.../theme/FontScaleTest.kt` — `fsSp(16f, 1.15f)` math; DataStore prefs round-trip (use `kotlinx-coroutines-test`, already in catalog).
- [ ] `app/src/test/.../render/RingBufferTest.kt` — bounded holder: wraparound, snapshot stability, survives feed bursts (D-12).
- [ ] Extend the Phase-1 `:macrobenchmark` harness with a ring+graph scene driven by the synthetic feed (reuse `SyntheticFeed`/`BenchActivity`/`parse_framestats.py`) for the D-10 proof.
- [ ] No framework install needed — JUnit4 + macrobenchmark + coroutines-test all present.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Windows-side Gradle (`E:\Android\gw.bat`) | All builds | ✓ | JDK 21 / SDK E:\Android\Sdk | — (`./gradlew` does NOT work from WSL) |
| `adb` (`E:\Android\Sdk\platform-tools`) | On-device install + gfxinfo capture | ✓ | platform-tools | — |
| Real `flox` tablet (LineageOS 18.1 / API 30, Adreno 320) | criterion #5 perf proof (D-10) | ✓ (project device) | — | None — gfxinfo proof REQUIRES real hardware (emulators lie) |
| Phase-1 gfxinfo parser (`tools/gfxinfo-parser/parse_framestats.py`) | criterion #5 | ✓ (in repo) | — | — |
| Python (for the oklch→sRGB bake script) | Pattern 4 one-time bake | ✓ (WSL `python` 3.13) | 3.13 | Kotlin script alternative |
| Geist/Geist Mono static TTFs | Pattern 2 | ✗ (not downloaded yet) | OFL 1.1 | Download from vercel/geist-font releases or Fontsource — blocking until fetched |

**Missing dependencies with no fallback:** none blocking architecture. The Geist TTFs must be downloaded (trivial; OFL-licensed) before font wiring — a fetch task, not a risk.
**Missing dependencies with fallback:** Python bake script can be Kotlin instead.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `datastore-preferences` `1.1.7` is the exact pin to use | Standard Stack | Low — any stable 1.1.x is minSdk-23-safe; planner confirms exact resolved version via gw.bat + verifyMinSdk. Don't hard-fail on the digit. |
| A2 | Geist Mono's monospace metrics fully satisfy "tabular numerals" without any OpenType feature | Pattern 2 | Low — monospace = uniform advance by definition; verify by eyeballing aligned columns of digits in the gallery on flox |
| A3 | The 6 static weights (400/500/600/700 sans + 500/600 mono) match every weight hifi.css actually uses | Standard Stack | Low — derived from grepping hifi.css; planner can trim to 4 if APK size matters |
| A4 | A slot `ScreenScaffold` (not custom `Layout`) can hold the one-shared-grid alignment exactly | Pattern 1 | Medium — the 3-button-gutter-center-on-divide alignment is automatic ONLY if stage and gutter rows share the same width and equal weights; weighted buttons (1.5/1/1.5) need care. If pixel-exact alignment fails the non-negotiable, escalate to a custom `Layout` for the gutter row only. |
| A5 | gfxinfo `FrameTimingMetric` issues noted in CLAUDE.md don't affect the raw `gfxinfo framestats` parser path | Validation | Low — the project already uses the raw `gfxinfo framestats` parser (not Macrobenchmark FrameTimingMetric) as system of record per ADR 0001 |

**These five `[ASSUMED]` items want a glance from the planner/discuss-phase before locking — none are blocking; all have cheap in-gallery verification.**

## Open Questions (RESOLVED)

1. **Two LAUNCHER activities in the debug variant (MainActivity + GalleryActivity).**
   - What we know: `src/debug/AndroidManifest.xml` adds the gallery LAUNCHER; MainActivity already has one in the main manifest.
   - What's unclear: whether to demote MainActivity's launcher category in debug so only the gallery launches, or accept two launcher icons in debug.
   - Recommendation: accept two in debug (simplest); the gallery is clearly the dev surface. Planner's call — trivial either way.
   - RESOLVED: accept two launcher icons in debug (03-06 Task 2); they are absent from release.

2. **Out-of-gamut oklch values (high-chroma accent/stop).**
   - What we know: some oklch literals (the saturated blue accent, red stop) may exceed the sRGB gamut.
   - What's unclear: exact gamut-mapping policy (clamp chroma vs clip RGB) for those tokens.
   - Recommendation: use the CSS Color 4 gamut-mapping (reduce chroma) in the bake script; cross-check the ~3 most-saturated tokens against an external converter (Pattern 4). Document the chosen policy in BakedTokens.kt.
   - RESOLVED: clamp chroma per CSS Color 4 gamut-mapping when baking; documented in the bake script + BakedTokens.kt (03-01 Task 1).

3. **Weighted gutter buttons (e.g. 1.5/1/1.5) keeping grid-line alignment (the A4 risk).**
   - What we know: LAYOUT.md permits weighted buttons but they must stay integer-ish units of the same grid.
   - What's unclear: whether `Modifier.weight` with fractional weights lands edges exactly on the Focus/Field divide.
   - Recommendation: validate visually in the gallery early; if it drifts, use a custom `Layout` for the gutter row only (keep the rest as the slot scaffold).
   - RESOLVED: validate visually in the gallery; escalate to a custom Layout for the gutter row ONLY if it drifts (03-03 Task 2).

## Sources

### Primary (HIGH confidence)
- `docs/ui_design/THEMING.md`, `LAYOUT.md`, `CLAUDE.md`, `reference/hifi.css` (597 lines) — the canonical design law; exact token values, layout grammar, control language. (in-repo, LAW)
- `docs/adr/0001-ui-toolkit-decision.md` — hybrid toolkit law, benchmark methodology + measured floors (p95 Views ~42ms vs Compose ~73ms). (in-repo)
- `.planning/phases/03-design-system-theming-foundation/03-CONTEXT.md` — D-01..D-14 locked decisions. (in-repo)
- `gradle/libs.versions.toml` — pinned stack (Compose 1.11.1, AGP 8.7.0, Kotlin 2.1.21, minSdk 23). (in-repo)
- `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` — the Phase-2 spine StateFlows + 250ms (~4Hz) conflation the render primitive consumes (D-14). (in-repo)
- developer.android.com/reference/kotlin/androidx/compose/ui/graphics/colorspace/ColorSpace — non-sRGB color spaces fall back to sRGB on unsupported API levels (the D-03 trap). **HIGH**
- developer.android.com/develop/ui/compose/compositionlocal — CompositionLocal idiom; staticCompositionLocalOf for design tokens. **HIGH**
- developer.android.com/jetpack/androidx/releases/datastore + AndroidX minSdk-23 default (June 2025) — DataStore 1.1.x minSdk-23-safe. **HIGH**
- en.wikipedia.org/wiki/Oklab_color_space + CSS Color 4 §15/§11.2 — oklch→Oklab→linear-sRGB→sRGB conversion math + matrices. **HIGH**
- github.com/vercel/geist-font — Geist/Geist Mono are SIL OFL 1.1. **HIGH**

### Secondary (MEDIUM confidence)
- proandroiddev.com "Preventing Font Scaling in Jetpack Compose" — LocalDensity fontScale=1f override pattern (D-04). Cross-verified with Android accessibility docs. **MEDIUM-HIGH**
- droidcon.com (Oct 2025) "Static vs Dynamic CompositionLocals" — read/write/recompose tradeoffs for token locals. **MEDIUM**
- fontsource.org/fonts/geist-mono — Geist Mono is monospace (tabular digits inherent); static weights 100-900 available. **MEDIUM**

### Tertiary (LOW confidence — flagged for in-gallery verification)
- The exact `1.1.7` DataStore pin (A1) — confirm via gw.bat resolution.
- The 6-weight Geist selection (A3) — confirm against final component set.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — almost entirely already-pinned, verified libs; one new first-party dep (DataStore).
- Architecture (the token seam, scaffold, render split): HIGH — every pattern verified against official Compose/Android docs + in-repo design law + ADR 0001.
- oklch bake: HIGH — conversion math is fully specified (CSS Color 4); the API-23 fallback risk is confirmed by official docs.
- Pitfalls: HIGH — all seven are concrete and tied to a verified source or in-repo constraint.
- Layout grid-line exactness (A4): MEDIUM — slot scaffold handles the common case; weighted-gutter exactness wants early in-gallery validation.

**Research date:** 2026-05-31
**Valid until:** ~2026-07-01 (stable stack; the only fast-moving item is the DataStore patch version, which doesn't affect the floor)
