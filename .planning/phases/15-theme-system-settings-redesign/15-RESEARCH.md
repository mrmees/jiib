# Phase 15: Theme System & Settings Redesign - Research

**Researched:** 2026-06-05
**Domain:** Generative color engine port (JS→Kotlin), OKLCH color math, theme persistence, Settings UI rebuild
**Confidence:** HIGH

## Summary

Phase 15 is **engine-first**: hand-port the pure, dependency-free `Palette.generate()` generator from the sibling `../theme_theory/app/color.js` to Kotlin, wire it into the **existing** Phase-3 theme substrate (`ThemeTokens`/`ThemeResolver`/`LocalTokens`/`ThemeableView` all stay), add a contrast-ranked **data pool** + directional standards + status slots to the token set, add **palette modes** (Colorful/Simple/High-Contrast), rework per-profile theme persistence from the retired `(themeBase, fsChoice, themeDeltaArgb)` triple to a full **theme tuple** `(seed, dark, paletteMode, poolShift, maxItems, poolOverrides)`, and rebuild Settings into a hybrid hub + a pushed theme-editor sub-page. `fsChoice` (S/M/L `--fs`) stays separate and unchanged.

The single most important finding: **the generator is genuinely portable.** I ran `color.js` in Node (v24) directly — it is pure, deterministic (verified: identical output across repeated calls), has zero DOM/dependency coupling, and is ~270 lines of straight float math (OKLCH↔sRGB matrices, gamut binary-search, contrast ranking). It ports to Kotlin almost line-for-line. Because Node is available in the dev environment, **golden reference values can be generated directly from `color.js`** and asserted bit-for-bit in Kotlin — this phase is an ideal candidate for a golden-value conformance validation architecture (the JS reference is the oracle).

**Primary recommendation:** Port `color.js` to a pure Kotlin `Palette` object (no Android deps), generate golden test vectors from the JS via a committed Node script, wire `ThemeResolver` to call `Palette.generate()` once-per-change and cache sRGB `Color` ints into the existing `ThemeTokens` (extended with `pool: List<Color>`, `directional`, status slots). Retire `BakedTokens` as source-of-truth but keep one validated default-seed snapshot as the fail-safe. Replace `Profile`'s theme fields with the tuple; reuse Phase-14's `mutateActiveProfile`/`writeScope` durable write path. Build the color wheel as a Compose `Canvas` + `pointerInput` control (mirrors `ScrubberPage`'s `awaitEachGesture` pattern), regenerating only on gesture-settle (D-07).

## Project Constraints (from CLAUDE.md)

- **UI Design System is LAW** — read `docs/ui_design/` before any screen. Settings is the ONE FFG-exempt, keyboard-allowed conventional screen. Outline-led, touch-first controls (2px outline + glow, ≥64px targets). Button intent = color.
- **minSdk 23 floor / Adreno-320 perf floor.** No new heavy deps. No `oklch()` Compose Color (silently wrong on API<26 — `BakedTokens.kt` header documents this; the port MUST emit baked sRGB `Color(0x..)` ints, never an Oklab-space Compose Color).
- **No GMS / no Material You dynamic color** (needs API 31) — roll the generator ourselves (COLOR-SYSTEM.md §12 confirms this is the explicit design intent).
- **Build runs Windows-side** via `E:\Android\gw.bat` (not `./gradlew` from WSL). AGP glob `--tests 'pkg.*'` false-fails — list test classes explicitly.
- **`[[dinghy-font-sizes-too-small]]`** — every new Settings/editor surface uses the `fsSp(baseSp, t.fs)` scale (15sp metadata floor … 30sp+ focus).
- **`[[dinghy-compose-write-scope-cancellation]]`** — DataStore writes route through `AppContainer.writeScope` (process-lifetime) via `mutateActiveProfile`/`saveProfile`; NEVER `rememberCoroutineScope().launch { … }`; read-modify-write inside one `dataStore.edit`.
- **Commit/push only when asked; branch first if on default.**

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| OKLCH→sRGB palette generation | Pure Kotlin (host-testable, no Android) | — | Mirrors `color.js`: zero deps, golden-testable against the JS oracle. Lives below the toolkit boundary like `PrinterStateReducer`. |
| Palette caching + StateFlow exposure | `ThemeResolver` (headless) | — | Already the single resolved-theme source of truth; gets rewired from baked-table lookup to generate-and-cache. |
| Theme tuple persistence | `Profile`/`ProfileStore` (DataStore) | `ThemePrefs` (global fallback) | Per-profile theme is Phase-14 D-08; reuse `mutateActive`/`writeScope`. |
| Compose token consumption | `DinghyTheme`/`LocalTokens` | — | Unchanged boundary — reads the extended `ThemeTokens`. |
| Views token consumption | `ThemeableView.applyTokens` | — | `GraphView` rewires trace colors to `pool[i]`. |
| Color-wheel touch control | Compose `Canvas` + `pointerInput` | — | UI-only; gesture mirrors `ScrubberPage`. Settles → triggers regen (D-07). |
| Settings IA | Compose conventional screen (FFG-exempt) | — | Hybrid hub + pushed editor sub-page (D-10). |

## Standard Stack

**No new dependencies.** The port is pure stdlib Kotlin math; the UI uses Compose primitives already in the project. Confirmed available in `gradle/libs.versions.toml`:

| Library | Already present | Used for |
|---------|-----------------|----------|
| `kotlin.math` (stdlib) | yes | `cbrt`, `hypot`, `atan2`, `cos`, `sin`, `pow`, `roundToInt` — every function `color.js` needs |
| Jetpack Compose (BOM pinned) | yes | `Canvas`, `pointerInput`, `awaitEachGesture` for the wheel; the editor sub-page |
| `androidx.datastore` (Preferences) | yes | Profile theme tuple persistence (existing `ProfileStore`) |
| JUnit 4.13.2 | yes | Golden conformance tests (host-side, no Android) |
| Node (dev env only) | v24.16.0 | Generate golden vectors from `color.js` — NOT a runtime/build dep |

### Don't add
- No color library (Coil's color utils, AndroidX `androidx.core.graphics.ColorUtils`, etc.) — the math is self-contained and must match the JS oracle bit-for-bit; an external lib's rounding/gamut policy would diverge.
- No Material You / dynamic-color APIs (API 31+, breaks the floor).

## Package Legitimacy Audit

> Not applicable — this phase installs **zero external packages**. All work is pure Kotlin against the existing stack. Node is a dev-time oracle for golden-vector generation, not a project dependency. No `npm install` / `pip install` / `cargo add` occurs.

## The Port Surface — `color.js` → Kotlin

The file is `../theme_theory/app/color.js` (~270 LOC). It is an IIFE exposing `Palette.generate(opts)` + a `util` namespace. **Confirmed pure & dependency-free** — runs unmodified in Node, no DOM, no globals beyond the module wrapper. Port target: a Kotlin `object Palette` (or top-level functions) in `theme/` with NO Compose/Android import in the math core (so it's host-unit-testable like `PrinterStateReducer`).

### Functions to port (enumerated)

| JS function | What it does | Kotlin equivalent | Landmine |
|-------------|--------------|-------------------|----------|
| `clamp01` | clamp to [0,1] | `coerceIn(0.0, 1.0)` | none |
| `sToL` / `lToS` | sRGB↔linear gamma transfer | `Math.pow` → `kotlin.math.pow`; same branches | none — pure `Double` |
| `hexToRgb` / `rgbToHex` | hex string ↔ [r,g,b] 0..1 | `String.substring` + `Integer.parseInt(.,16)`; `roundToInt` + `toString(16).padStart(2,'0')` | JS `padStart` → Kotlin `padStart(2,'0')`; use lowercase hex to match oracle |
| `linToLab` / `labToLin` | linear-sRGB ↔ OKLab (3×3 matrices + cube/cbrt) | direct; `Math.cbrt` → `kotlin.math.cbrt` | **matrix constants must be copied verbatim** (12+13 doubles each). A transcription typo = silent color drift. Golden tests catch it. |
| `hexToOklch` | hex → {L,C,H} (atan2 + hypot, H wrapped to 0..360) | `atan2`, `hypot`, `+360 if <0` | radian↔degree: JS uses `*180/PI`; replicate exactly |
| `inGamut` | is (L,C,H) inside sRGB ±0.0002 | `.all { it in -0.0002..1.0002 }` | keep the ±0.0002 epsilon EXACTLY |
| `oklchToHex` | OKLCH→hex, **20-iter binary search** chroma reduction if out of gamut | for-loop 20 iters | iteration count is load-bearing for bit-exactness — keep 20 |
| `grayHex` | `oklchToHex(L,0,0)` | trivial | none |
| `maxChromaAt` | **18-iter binary search** for max in-gamut chroma at (L,H) | for-loop 18 iters | keep 18 |
| `cuspL` | scan L in 0.30..0.92 step 0.02, find L of peak chroma | `var L = 0.30; while (L <= 0.92) { …; L += 0.02 }` | **float accumulation:** `L += 0.02` over a range. JS and Kotlin both use IEEE-754 `Double` → identical accumulation, but DO NOT refactor to integer-step + multiply (changes the values). Keep the exact `+= 0.02` loop. |
| `hueDiff` / `inSpan` / `isReserved` | hue arithmetic, reserved-zone test | `Math.abs(a-b) % 360` | `band`/reserved path is dead when `statusFromPool=true` (the shipped config) — port it anyway for parity but it's not exercised |
| `spreadHues` | spread n hues across allowed arc, step 0.5°, dodge reserved | the `runs`/`acc` accumulation loop | step `0.5` and the run-length accumulation are exact; port literally |
| `minHueGap` | smallest adjacent gap (sorted) | `sorted()` + min | `Math.round` at call site → `roundToInt` |
| `rankByContrast` | farthest-point reorder (any prefix = most-separated subset) | nested loop with `splice` → `removeAt` | `slice()`/`shift()`/`splice()` → `toMutableList()`/`removeAt(0)`/`removeAt(i)` |
| `generate` | the one call — assembles surfaces/theme/status/pool/directional | the public entry | see contract below |

### JS→Kotlin idiom translation landmines

1. **Number type:** JS numbers are `Double`. Use Kotlin `Double` throughout the math (not `Float`) so accumulation matches the oracle bit-for-bit. Only convert to `Int`/`Color` at the final hex/ARGB boundary. `[VERIFIED: ran color.js in Node — output is deterministic across runs]`
2. **`Math.cbrt`:** exists as `kotlin.math.cbrt` (since Kotlin 1.3). `[VERIFIED: kotlin stdlib]`
3. **Array semantics:** JS `.map`/`.reduce`/`.sort` → Kotlin `.map`/`.fold`/`.sortedBy`. **`Array.prototype.sort` is in-place and mutates; `[0,1,2].sort(cmp)`** in `top3i`/`ord` → use `.sortedWith` (returns new list) to avoid aliasing bugs.
4. **`%` on negatives:** JS `%` is remainder (sign of dividend); Kotlin `%` matches for `Double`. Hue math already guards with `+360`, so behavior is equivalent — but verify with golden vectors including a low-hue seed (e.g. `#e23a3a`, hue ~25°).
5. **String→hex parsing:** `parseInt(h.slice(0,2),16)` → `h.substring(0,2).toInt(16)`. Output `rgbToHex` must lowercase + 2-pad to match the oracle (`#3c75fb` not `#3C75FB`).
6. **No JS engine on Android (D-01):** confirmed — we port the math, we do NOT embed a JS runtime. There is zero need for one.

### Verified golden reference values (from `color.js` via Node)

`Palette.generate({ seedHex: "#3f78ff", dark: true, maxItems: 3, poolShift: 0, statusFromPool: true })` returns: `[VERIFIED: ran color.js in Node v24.16.0]`

```
accent (theme.primary): #3c75fb
surfaces.bg:  #0b0b0b      surfaces.text: #e8e8e8   ← PURE NEUTRAL (D-16 confirmed)
pool:        ["#6895f4", "#866200", "#c575cc"]
poolHues:    [263.944…, 83.944…, 323.944…]
poolRoles:   ["xy", "z", "temperature"]
directional: { temperature: "#c575cc", xy: "#6895f4", z: "#866200" }
status:      { stop: "#af3c3c", caution: "#007780", go: "#57af58" }
minHueGap:   60
```

Mode behavior verified `[VERIFIED: Node]`:
- `simple:true` → pool + status all collapse to the text gray `#e8e8e8`, accent kept.
- `highContrast:true` → pool collapses to gray, status restores stoplight RYG (`#de3d30`/`#f5ae39`/`#3bac56`).
- `poolShift:120` → pool hues rotate; **accent unchanged** (`#3c75fb`) — confirms shift affects only data/directional/status, not chrome.
- `maxItems:8` → `pool` array has **exactly 8 entries**, `minHueGap` drops to 33° (below the ~40° distinctness floor — the lightness staircase carries it).

## The Token Bridge — `tokensFromPalette()` → `ThemeTokens.kt`

The generator emits a **slim** set; dinghy wants more tiers. `dinghy.js`'s `tokensFromPalette()` (lines 34–86) is the derivation to port. Confidence HIGH (the function is short and explicit). Field-by-field mapping onto `ThemeTokens`:

### Maps 1:1 from generator output
| ThemeTokens field | Generator source | Note |
|-------------------|------------------|------|
| `surface` | `surfaces.surface` | direct |
| `text` | `surfaces.text` | direct |
| `text2` | `surfaces.muted` | direct |
| `accent` | `theme.primary` | cusp-anchored seed accent |
| `go` (+`goSoft`/`goGlow`) | `status.go` (+ alpha) | direct |
| `stop` (+`stopSoft`/`stopGlow`) | `status.stop` (+ alpha) | direct |
| `heat` (+`heatSoft`/`heatGlow`) | `status.caution` (+ alpha) | **`heat` now means ONLY caution** (D-2). See migration note below. |

### DERIVED in-between tiers (port `lShift`/`rgbaOf` from `dinghy.js`)
`lShift(hex, dL)` = hexToOklch → bump L by dL (clamped) → oklchToHex. `rgbaOf(hex, a)` = pack alpha into ARGB.

| ThemeTokens field | Derivation (dark / light) |
|-------------------|---------------------------|
| `bg` | `surfaces.bg` (pure neutral now — D-16) |
| `bg2` | `lShift(bg, +0.03 / -0.022)` |
| `surface2` | `lShift(surface, +0.04 / -0.045)` |
| `surface3` | `lShift(surface, +0.085 / -0.09)` |
| `text3` | `lShift(muted, -0.12 / +0.13)` |
| `hair` | `rgbaOf(text, 0.08 / 0.11)` |
| `outline` | `surfaces.divider` |
| `outline2` | `lShift(divider, +0.11 / -0.12)` |
| `accent2` | `lShift(primary, +0.08 / -0.05)` |
| `accentSoft` | `rgbaOf(primary, 0.16 / 0.12)` |
| `accentLine` | `rgbaOf(primary, 0.55 / 0.50)` |
| `accentGlow` | `rgbaOf(primary, 0.35 / 0.20)` |
| `edgeGlow` | `rgbaOf(muted, 0.24 / 0.12)` |
| `heatSoft`/`heatGlow`/`goSoft`/`goGlow`/`stopSoft`/`stopGlow` | `rgbaOf(status.*, …)` per `dinghy.js` |

**Note:** `dinghy.js` derives `--surface-2` etc. from `surface` whereas the *current* baked `BakedTokens` has hand-tuned values. The generated values will differ slightly from today's; that's expected and intended (D-16 supersedes the old surface tints). The `--edge-glow` source differs (generator uses `muted`; old token is a fixed blue-gray) — generated is the new truth.

### NEW fields to add to `ThemeTokens`
| New field | Type | Source | Consumed by |
|-----------|------|--------|-------------|
| `pool` | `List<Color>` (immutable) | `P.pool` mapped to baked `Color` | `GraphView` traces, temp readouts (D-13) |
| `poolRoles` | `List<String?>` (optional, diagnostics) | `P.poolRoles` | swatch strip in editor |
| `directional` | nested value (`temperature`,`xy`,`z`: `Color`) or 3 flat fields `dirTemperature`/`dirXy`/`dirZ` | `P.directional` | Move jog-pad/Z-row outlines (D-13) |
| status slots | `statusStop`/`statusCaution`/`statusGo` already covered by `stop`/`heat`/`go` | `P.status` | already mapped |

**Discretion call (per CONTEXT.md):** keep `ThemeTokens` `@Immutable` and flat-ish. `pool` as `List<Color>` is fine for Compose stability IF wrapped — note `List` is unstable to Compose by default; consider `kotlinx-collections-immutable` `ImmutableList` (already a recommended supporting lib in CLAUDE.md stack) OR rely on the `@Immutable` annotation on the enclosing `ThemeTokens` covering it (the whole data class is annotated, so its `List` field is treated stable). The latter is simpler and already the project pattern. The directional trio: a small `@Immutable data class Directional(...)` reads cleaner than three flat fields and matches the JS shape.

**Retire `violet`:** the `ThemeTokens.violet` field is removed; its two consumers (`GraphView`, `TemperatureScreen`) move to `pool[2]`. `[VERIFIED: grep — only those 2 files reference `.violet`]`

## The Generate-and-Cache Rewire — `ThemeResolver`

Today `ThemeResolver.resolve(base, deltas, fs)` picks a `BakedTokens` table + applies `TokenDelta` overrides. **Rewire:**

1. The resolver's inputs change from `(base, deltas, fs)` to the **theme tuple** `(seedHex, dark, paletteMode, poolShift, maxItems, poolOverrides, fs)`.
2. `recompute()` calls `Palette.generate(opts)` ONCE, runs `tokensFromPalette()` to build the full `ThemeTokens` (all colors baked to sRGB `Color` ints), applies `poolOverrides` (sparse `Map<Int, Color>` — replace `pool[i]` for overridden indices), stamps `fs`, and emits on the existing `StateFlow<ThemeTokens>`.
3. The Compose (`DinghyTheme.collectAsStateWithLifecycle`) and Views (`ThemeableView.applyTokens`) boundaries are **unchanged** — they still consume `StateFlow<ThemeTokens>`. This is the whole reason the substrate is "extended not rewritten."
4. **Default-seed-at-first-launch:** the resolver is constructed with a validated default seed (replace today's `ThemeResolver()` no-arg default). The default generates the out-of-box palette. Keep ONE snapshot of the default-seed output baked in `BakedTokens` as the **fail-safe** (if generation ever throws — it shouldn't, the math is total — the resolver falls back to the snapshot). COLOR-SYSTEM.md §12 explicitly requires "ship a validated default seed."

**Perf (D-02/D-07):** generation is ~15 color conversions + the pool spread/rank (a few hundred gamut binary-searches at 18–20 iters each). This is **microseconds-to-low-milliseconds**, run ONCE per seed/mode/shift change — never per frame, never per render. The render loop reads cached `Color` ints. This is well within the Adreno-320 budget (the device never does color math). D-07: the wheel drag regenerates only on lift/settle, not per-pixel, so even a fast drag triggers at most a few generations.

**`mono` modes:** `simple`/`highContrast` are derived from `paletteMode` (Colorful → both false; Simple → `simple:true`; High-Contrast → `highContrast:true`). Default mode = Colorful (D-15).

## Per-Profile Persistence Rework

### Replace these `Profile` fields (D-05 fresh-start, NO migration)
Remove: `themeBase: String`, `themeDeltaArgb: Map<String,Long>`. **Keep:** `fsChoice: String` (S/M/L `--fs` stays separate, unchanged — D-05).

Add the theme tuple as persisted PRIMITIVES (never a baked `ThemeTokens` — same discipline as today, Profile.kt:32 comment):
```
val seedHex: String = "#3f78ff",          // validated default seed
val dark: Boolean = true,
val paletteMode: String = "Colorful",     // Colorful | Simple | HighContrast
val poolShift: Int = 0,                    // degrees, cached with theme (D-09)
val maxItems: Int = 4,                     // nozzle+bed+chamber+headroom (D-13/D-14)
val poolOverrides: Map<Int, Long> = emptyMap(), // sparse poolIndex -> unsigned-32 ARGB (D-09)
// fsChoice retained unchanged
```
`PersistedProfile` (the `@Serializable` wire form) gets the matching fields. `poolOverrides` keyed by `Int` serializes fine via kotlinx (map of Int→Long); the existing `Json { ignoreUnknownKeys = true }` means old blobs with the dropped `themeBase`/`themeDeltaArgb` keys decode cleanly to defaults (the no-migration fresh-start — the old keys are simply ignored, new fields default).

### Resolution path
`Profile.toThemeResolved()` currently returns `ThemePrefs.Resolved(base, deltas, fs)`. Rewire it to return a new theme-tuple value (or feed the tuple straight into `ThemeResolver.apply(...)`). Reuse the `ThemePrefs.sanitize` fail-safe DISCIPLINE: an unparseable `seedHex` → default seed; bad `paletteMode` → Colorful; junk `poolShift`/`maxItems` → defaults; garbage `poolOverrides` entry → drop just that index. NEVER throws, NEVER black-screens (the deterministic fail-safe contract from Phase 3 D-02 carries forward).

### Durable write path (reuse Phase-14 machinery)
- Live retheme on edit: `container.themeResolver.<setSeed/setMode/setShift/setOverride>(…)` (new mutators on the resolver mirroring today's `setBase`/`setFs`).
- Persist target = active profile via `container.mutateActiveProfile { it.copy(seedHex = …) }` (process-lifetime `writeScope`, read-modify-write inside one `dataStore.edit`). Fallback to global `themePrefs` when no active profile. **`[[dinghy-compose-write-scope-cancellation]]` applies directly** — never persist from a `rememberCoroutineScope()`.

### WR-02 fix (from CONTEXT.md deferred-todo, naturally subsumed)
`AppContainer.seedTheme()` currently wraps the no-active branch in `flowOf(themePrefs.flow.firstOrNull() ?: DEFAULT)` — a ONE-SHOT read inside `flatMapLatest`. When rebuilding `seedTheme` for the new tuple, make the no-active branch collect the global theme **reactively** (mirror the active branch — `flatMapLatest` into `themePrefs.flow` so a global-default edit while idle re-emits). This closes WR-02. `[CITED: 15-CONTEXT.md deferred / 2026-06-05-phase-14-review-deferred-wr02-wr03]`

## Settings Rebuild (D-10/D-11/D-12)

### Hybrid structure
- **Hub** (`SettingsScreen.kt` rebuild): stays a flat `Column.verticalScroll`, token-themed, FFG-exempt, keyboard-allowed. Sections (D-11, all scaffolded now): **Profiles** (existing CRUD list — reuse verbatim), **Connection** (the host/port/key + mDNS form — reuse), **Appearance** (dark/light toggle, S/M/L, palette-mode picker, + an "Edit theme…" row that pushes the editor), **Feature toggles** (webcam live; outputs/WebRTC/fine-tune as **greyed capability-gated placeholders** — reuse the established forward-entry tile pattern), **System/About** (app version + build only, D-12).
- **Theme editor sub-page** (new pushed screen): seed picker + per-slot pool palette + Randomize/Reset. Too big to inline (D-10). Follows the same conventional-screen discipline.

### Seed picker (D-06)
- **Touch color wheel/ring** (Compose `Canvas` + `pointerInput`) — gloved-finger, ≥64px handle, `fsSp` labels. The wheel primarily picks **hue** (the generator normalizes L/C to the cusp — COLOR-SYSTEM.md §2/§5), so a hue ring is the natural control; a small L/C handle is optional. The handle moves freely during drag; the palette resolves on **lift/settle** (D-07).
- **Curated preset seed swatches** — a row of validated seed colors covering the 90% case (these are theme DATA, ARGB ints, not rendered-chrome literals — same carve-out as today's accent picker, SettingsScreen.kt:90).
- Hex entry NOT required (Settings allows keyboard, but wheel+presets is the chosen affordance).

### Preview (D-08)
Live app retheme (the editor IS inside `DinghyTheme`, so changing the resolver retints it live) + a **generated-swatch strip** showing accent, `pool[0..n]`, status colors. Port `swatches()` shape from `dinghy.js` (lines 145–175) as the strip's content model.

### Randomize / overrides / Reset (D-09)
- **Randomize** button → roll a new `poolShift` (random degree 0..360), cache it with the theme. NEVER random-per-render (breaks stable identity — the generator echoes `poolShift` for exactly this reason).
- **Per-slot pool override** → after generation, each `pool[i]` swatch is tappable to edit (route through a color picker — could reuse the wheel or a swatch grid); writes `poolOverrides[i] = argb`. Persisted in the tuple. **Data-pool slots only** — chrome (accent/surfaces) stays seed-only (D-04). Status-slot editability rides with the follow-on phase.
- **Reset** → clears `poolOverrides` + `poolShift`, returns to the seed-derived set / default seed.

### Reusable components
`ConfirmGuard` (destructive profile delete + Reset confirm), `OutlinedControl`/`Intent` (every button), `ScrubberPage`/`NumpadPage` (any numeric — e.g. `maxItems` if exposed), the greyed forward-entry tile. The color wheel is NEW — see Code Examples.

## Pool Wiring (D-13)

**Critical scoping distinction the planner must honor:** `t.heat` is referenced in **23 files**, but the OVERWHELMING MAJORITY use it as the **caution/warn semantic** (Console WARNING, `ConfirmGuard`, `OutlinedControl` Warn intent, macro popups, etc.) — D-13 **KEEPS** `heat` = caution. Only the **heater-readout** uses migrate to the pool:

| Consumer | Today | D-13 rewire |
|----------|-------|-------------|
| `GraphView.applyTokens` (lines 182–188) | trace0=`heat`, 1=`accent`, 2=`violet` | trace `i` → `pool[i]` |
| `PrintStatusScreen` nozzle/bed readouts | `heat` (amber) | `pool[0]` / `pool[1]` (match graph by stable index) |
| `TemperatureScreen` readouts + `violet` ref | `heat`/`accent`/`violet` | `pool[0..2]` |
| heater scrubber (single-setting page) | `heat`/`temperature` | `pool[0]` (= `directional.temperature`) |
| Move jog-pad outline (XY) | `accent` | `directional.xy` |
| Move Z-row outline | `accent` | `directional.z` |

**Stable identity rule:** the same sensor = same `pool` index everywhere. Render the canonical order (nozzle, bed, chamber…) from `pool[0]` down (COLOR-SYSTEM.md Part II §C). The Print-Status readout index MUST equal the GraphView trace index.

**D-14 (cycle infinitely) — IMPORTANT generator finding:** the generator's `pool` array is sized **exactly to `maxItems`** (verified: `maxItems:8` → 8-entry pool). So "cycle infinitely" is a **consumer-side `pool[i % pool.size]` wrap**, NOT a generator change. **`GraphView.MAX_TRACES = 3` is a hard cap** (3 pre-allocated paths/paints) — if a printer ever shows >3 sensors this caps them. For THIS phase (nozzle/bed/chamber = 3) it's fine; if the planner wants true >3 support, `GraphView` needs more pre-allocated trace slots — but that's arguably out of scope (no current printer exceeds 3). Recommend: set default `maxItems = 4` (nozzle+bed+chamber+headroom per D-13) but keep `GraphView` at 3 traces for now, and have consumers wrap `pool[i % pool.size]` so a 4th readout (e.g. MCU temp on a future screen) gets a valid color. Flag the `MAX_TRACES` cap explicitly for the planner.

**Move's homed/unhomed status-color + force-move lock-shape stay with the status follow-on** — only the directional-plane *outline* colors are in scope here.

## Architecture Patterns

### System Architecture Diagram

```
  [Settings: seed wheel / mode picker / poolShift / overrides]
                          │ (on settle, D-07)
                          ▼
        ThemeResolver.setSeed/setMode/setShift/setOverride
                          │
                          ▼
              Palette.generate(opts)  ── pure Kotlin, OKLCH→sRGB ──┐
                          │                                         │ (golden-tested
                          ▼                                         │  vs color.js)
              tokensFromPalette()  ── derive tiers + apply overrides ┘
                          │  (cache sRGB Color ints)
                          ▼
              StateFlow<ThemeTokens>  ◄── the UNCHANGED boundary
                 │                  │
   collectAsState │                  │ applyTokens(t)
                 ▼                  ▼
        Compose surfaces      Views (GraphView)
        (LocalTokens)          traces = pool[i]
                                                          ║
   [Profile theme tuple] ──seedTheme()──► ThemeResolver  ║ render loop reads
   (DataStore, per-profile)  reactive       (default seed ║ CACHED ints —
   mutateActiveProfile/writeScope            at launch)   ║ NO color math
```

### Pattern 1: Pure host-testable color core
**What:** `Palette.generate` + all math in a package with zero Android imports (mirrors `state.PrinterStateReducer`). **When:** the entire color engine. **Why:** golden-testable against the JS oracle host-side; no emulator needed.

### Pattern 2: Generate-once-cache-forever
**What:** color math runs only in `ThemeResolver.recompute()` on a discrete change; everything downstream reads baked `Color`. **When:** all theme resolution. **Why:** Adreno-320 floor — the device must never do float color math at render time (the whole point of baking, per `BakedTokens` header + COLOR-SYSTEM.md §3).

### Pattern 3: Settle-not-stream regeneration (D-07)
**What:** the wheel updates a transient handle position during drag (cheap), regenerates the palette only on pointer-up. **When:** the seed wheel. **Why:** protects the floor from a regen-storm; mirrors `ScrubberPage`'s single-`awaitEachGesture` discipline.

### Anti-Patterns to Avoid
- **Oklab-space Compose Color:** never construct a `Color` in a non-sRGB color space — silently wrong on API<26 (`BakedTokens` header). Always bake to sRGB `Color(0xAARRGGBB)`.
- **Per-render generation:** never call `Palette.generate` from `onDraw`/recompose. Cache.
- **Random-per-render poolShift:** breaks stable item identity. Roll once, persist.
- **Migrating old theme fields:** D-05 is fresh-start — drop `themeBase`/`themeDeltaArgb`, don't write a migration.
- **`Float` math in the core:** use `Double` to match the oracle bit-for-bit.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| OKLCH↔sRGB conversion | a fresh conversion from a blog | port `color.js` verbatim | it's the validated oracle; any reimplementation diverges from the sandbox the owner reviewed |
| Gamut mapping | custom clamp/desaturate | the JS binary-search (20 iter) | matches CSS Color 4 "reduce chroma" policy already used in `BakedTokens` |
| Contrast-ranked pool | a hand-tuned palette | `rankByContrast` farthest-point | guarantees any prefix is the most-separated subset (stable-identity property) |
| Theme persistence | a new store | extend `Profile`/`ProfileStore` | Phase-14 `mutateActive`/`writeScope` already solves the durable-write race |
| Touch numeric/settle control | new gesture code | `ScrubberPage`'s `awaitEachGesture` pattern | already solves the WR-01 tap-vs-drag coordination |

**Key insight:** the generator is a finished, owner-reviewed artifact. The value of this phase is a *faithful* port + clean substrate wiring, NOT redesigning the color math. Golden tests against `color.js` are the contract.

## Common Pitfalls

### Pitfall 1: Color drift from a transcription typo in the matrices
**What goes wrong:** one wrong digit in a 3×3 OKLab matrix → every color subtly off, invisible to the eye but failing the sandbox parity. **Why:** 25+ hand-copied float constants. **How to avoid:** golden conformance tests asserting Kotlin output == `color.js` output for several seeds (dark+light, all 3 modes, edge-hue seeds). Generate the vectors via a committed Node script. **Warning signs:** golden test fails on a specific channel.

### Pitfall 2: `Float` vs `Double` accumulation
**What goes wrong:** using `Float` in `cuspL`'s `L += 0.02` loop accumulates differently → cusp lands one step off → accent lightness drifts. **How to avoid:** `Double` everywhere in the core; only narrow at the hex boundary. **Warning signs:** accent hex off-by-a-few on certain hues.

### Pitfall 3: Theme-switch flicker during rebind (carried open from Phase 14 D-09)
**What goes wrong:** changing seed/mode emits a new `ThemeTokens`; because `LocalTokens` is `staticCompositionLocalOf`, the WHOLE subtree recomposes (correct + intended) — but if persistence and live-retheme race, a frame can render the old palette then the new. **Why:** two paths (resolver live-set + DataStore re-seed) both touch the resolver. **How to avoid:** live-retheme drives the resolver directly (immediate); persistence is fire-and-forget and the `seedTheme` collector must be **idempotent** (re-applying the same tuple is a no-op re-emit). Apply the full tuple in ONE `ThemeResolver.apply(...)` call (the existing single-re-emit pattern, AppContainer.kt:314) — never 3 separate `set*` calls in sequence (3 flickers). **Warning signs:** a brief wrong-color flash on settle.

### Pitfall 4: `heat` over-migration
**What goes wrong:** blindly replacing all 23 `.heat` references with `pool[0]` breaks Console WARNING / Warn-intent buttons. **How to avoid:** migrate ONLY heater-readout uses (the 6 in the D-13 table); `heat` = caution stays everywhere else. **Warning signs:** warning toasts/Warn buttons change color.

### Pitfall 5: `GraphView.MAX_TRACES = 3` cap silently truncates a 4th sensor
**What goes wrong:** `maxItems = 4` generates a 4-color pool but GraphView only has 3 pre-allocated paths → a 4th trace is dropped. **How to avoid:** for this phase, no printer shows >3 graph traces, so it's fine — but the planner should KNOW the cap exists and decide whether to bump it. Readout consumers (not the graph) should `pool[i % size]`. **Warning signs:** a 4th temperature series never appears.

## Code Examples

### Color wheel control (Compose Canvas + settle-regen, D-06/D-07)
```kotlin
// Pattern from ScrubberPage.kt:153-167 — ONE pointerInput, settle on up.
// Source: existing ScrubberPage awaitEachGesture idiom
Canvas(
    Modifier
        .size(/* ≥64dp diameter */)
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                var hue = hueAt(down.position, size)  // transient handle move
                onHandleMove(hue)                      // cheap: move handle, NO regen
                do {
                    val e = awaitPointerEvent()
                    e.changes.forEach { if (it.pressed) { hue = hueAt(it.position, size); onHandleMove(hue) } }
                } while (e.changes.any { it.pressed })
                onSettle(hue)  // D-07: regenerate palette ONLY here (pointer up)
            }
        }
) { /* draw hue ring; place handle at current hue */ }
```

### Golden conformance test (host-side, JUnit)
```kotlin
// Source: BakedTokenTableTest.kt pattern (assert exact hex)
@Test fun defaultSeedDark_matchesColorJsOracle() {
    val p = Palette.generate(seedHex = "#3f78ff", dark = true, maxItems = 3,
                             poolShift = 0, statusFromPool = true)
    assertEquals("#3c75fb", p.theme.primary)              // accent
    assertEquals("#0b0b0b", p.surfaces.bg)                // pure neutral (D-16)
    assertEquals(listOf("#6895f4","#866200","#c575cc"), p.pool)
    assertEquals("#af3c3c", p.status.stop)
    assertEquals(60, p.minHueGap)
}
```

### Generating the golden vectors (committed dev script, Node)
```bash
# tools/color-golden/dump.js — emits a JSON fixture the Kotlin test loads/asserts.
node -e 'const P=require("../../../theme_theory/app/color.js");
  console.log(JSON.stringify(P.generate({seedHex:"#3f78ff",dark:true,maxItems:3,statusFromPool:true})))'
```

## Runtime State Inventory

> This phase is a code/config change to an in-process theme system. No external runtime state, but the persistence rework has a fresh-start implication.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `Profile` blobs in `profiles.preferences_pb` carry old `themeBase`/`fsChoice`/`themeDeltaArgb`. Global `theme.preferences_pb` (ThemePrefs) carries old base/fs/deltas. | **Code edit only (D-05 fresh-start).** Old keys are ignored on decode (`ignoreUnknownKeys=true`); new theme-tuple fields default to the validated seed. NO data migration — the upgrade resets theme to default, by design. `fsChoice` is preserved. |
| Live service config | None — theme is in-process, never touches Moonraker. | None — verified: theme change MUST NOT disturb the spine rebind (`activeConfig.distinctUntilChanged` keys on host/port/key only). |
| OS-registered state | None. | None. |
| Secrets/env vars | None — theme has no secrets. | None. |
| Build artifacts | `tools/oklch-bake/bake_tokens.py` + `BakedTokens.kt` become non-source-of-truth (retained only as the default-seed fail-safe snapshot). | Keep `BakedTokens` for the ONE validated default; the bake script is no longer the generation path (document it as legacy or regenerate the snapshot from the Kotlin `Palette` + default seed). |

## Validation Architecture

> nyquist_validation is enabled (no `workflow.nyquist_validation:false` found). This phase is a **strong** validation candidate: a port of pure deterministic color math with an executable oracle (`color.js` in Node).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 (host unit tests, `app/src/test/`) |
| Config file | `app/build.gradle.kts` (testOptions) |
| Quick run command | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.theme.PaletteGeneratorTest --no-daemon"` |
| Full suite command | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |

**AGP gotcha:** list test classes explicitly — the `--tests 'pkg.*'` glob false-fails on this AGP (memory `[[dinghy ...AGP gotcha]]`).

### Phase Requirements → Test Map
| Behavior | Test Type | Automated Command | File |
|----------|-----------|-------------------|------|
| Kotlin `Palette.generate` == `color.js` (default seed, dark) | unit (golden) | `… --tests …PaletteGoldenTest` | ❌ Wave 0 |
| Golden parity across light, Simple, High-Contrast modes | unit (golden) | same | ❌ Wave 0 |
| Golden parity for edge-hue seeds (red ~25°, yellow ~85°) + poolShift | unit (golden) | same | ❌ Wave 0 |
| OKLCH↔sRGB round-trip + gamut clamp stays in [0,1] | unit (property) | `…PaletteMathTest` | ❌ Wave 0 |
| `tokensFromPalette` derivation (tiers, alpha) matches `dinghy.js` | unit | `…TokenBridgeTest` | ❌ Wave 0 |
| `ThemeResolver` generate-and-cache emits expected `ThemeTokens` | unit | `…ThemeResolverTest` (extend existing) | exists — extend |
| Theme-tuple sanitize fail-safe (junk seed/mode/shift/overrides → defaults, never throws) | unit | `…ProfileThemeSeedTest` (extend) | exists — extend |
| `poolOverrides` applied at correct index; Reset clears | unit | `…ThemeResolverTest` | extend |
| Fresh-start decode (old blob keys ignored, new fields default) | unit | `…ProfileStore`/serialization test | extend `TokenDeltaSerializationTest` analog |

### Sampling Rate
- **Per task commit:** the golden + math unit tests (`PaletteGoldenTest`, `PaletteMathTest`) — sub-second, host-side.
- **Per wave merge:** full `:app:testDebugUnitTest`.
- **Phase gate:** full unit suite green + on-device eyeball UAT (the owner-reviewed sandbox parity: reseed on flox, watch traces stay separated, confirm pure-neutral surfaces, Simple/High-Contrast modes, Settings editor flow). Hands-on UAT is mandatory — this project has a documented string of "green-suite mock-vs-reality" misses.

### Wave 0 Gaps
- [ ] `PaletteGoldenTest.kt` — golden vectors from `color.js` (load a committed JSON fixture or inline the verified hexes above).
- [ ] `tools/color-golden/dump.js` — committed Node script that emits the fixture from the sibling `color.js` (regenerable oracle).
- [ ] `PaletteMathTest.kt` — round-trip / gamut property checks.
- [ ] `TokenBridgeTest.kt` — `tokensFromPalette` derivation parity.
- [ ] Extend `ThemeResolverTest.kt`, `ProfileThemeSeedTest.kt` for the tuple + overrides + fail-safe.

## Security Domain

> `security_enforcement` not explicitly disabled → applicable, but this phase has a small surface.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | yes | Sanitize the persisted theme tuple: validate `seedHex` (hex format), `paletteMode` (enum), `poolShift`/`maxItems` (range), `poolOverrides` (valid ARGB) — fail-safe to defaults, NEVER throw (extends the Phase-3 D-02 deterministic fail-safe contract). |
| V7 Data Protection | yes (indirect) | `Profile.toString` already redacts `apiKey`. Theme-field additions must not widen the redaction surface — they carry no secrets. Keep the redacted `toString` when adding fields. |
| V2/V3/V4/V6 | no | No auth/session/access-control/crypto in a theme system. |

### Known Threat Patterns
| Pattern | STRIDE | Mitigation |
|---------|--------|------------|
| Corrupt theme blob crashes/black-screens the printer display | Denial of Service | Pure `sanitize` fail-safe → always a complete usable theme (the load-bearing Phase-3 contract; the device is a printer surface that must never go dark). |
| A theme edit churns the connection spine | (availability) | `activeConfig.distinctUntilChanged` keys on host/port/key only — theme-only edits are already suppressed (Phase-14 T-14-04). Verify the new fields stay OUT of `ConnectionConfig`. |

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Build-time `BakedTokens` dark/light tables | Runtime `Palette.generate` + cache (generated from a seed) | this phase | retire baked tables as source-of-truth; keep one default-seed snapshot as fail-safe |
| Per-role `TokenDelta` chrome overrides | Seed-only chrome (D-04); data-pool slots individually editable (D-09) | this phase | one knob that can't produce a broken palette; `TokenDelta`/`Role` retired |
| Fixed reserved RYG status + warm-arc reservation | Status = 3 dedicated pool slots, full-wheel pool (shape carries safety — follow-on) | this phase adds the pool; shape-status is the follow-on | doubles usable hue space; `violet` retired |
| Faint cool-tint surfaces (`oklch .012`) | Pure-neutral surfaces (D-16) | this phase | supersedes `THEMING.md` `--bg`; bg flips polarity per generator |

**Deprecated/retired this phase:** `ThemeTokens.violet` (→`pool[2]`), `TokenDelta`/`TokenDelta.Role` (chrome overrides gone), `Profile.themeBase`/`themeDeltaArgb` (→theme tuple), `BakedTokens` as generation source (→fail-safe snapshot only).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Default validated seed = `#3f78ff` (the generator's own default + the sandbox default) | Persistence/Resolver | LOW — owner can pick a different default seed; it's one constant. The sandbox uses this; confirm it's the intended ship default during UAT. |
| A2 | Default `maxItems = 4` (nozzle+bed+chamber+headroom per D-13) | Persistence/Pool | LOW — D-13 says "default ~4"; a 3-trace graph works at 3 or 4. Confirm on-device. |
| A3 | `kotlinx-collections-immutable` is acceptable (or `@Immutable` on the data class suffices) for `pool: List<Color>` stability | Token Bridge | LOW — both work; the `@Immutable`-covers-the-field route adds zero deps and is the existing pattern. |
| A4 | `GraphView.MAX_TRACES` stays at 3 this phase (no printer shows >3 graph traces) | Pool Wiring | LOW — flagged for the planner; bumping it is a small, optional follow-up. |

## Open Questions (RESOLVED)

1. **Default seed value (A1).** The validated default seed is the out-of-box look and the "Reset" target (COLOR-SYSTEM.md §12 requires a validated one). `#3f78ff` is the generator/sandbox default. Recommendation: ship `#3f78ff` unless the owner picks another during UAT — it's a single constant.
   RESOLVED: ship #3f78ff as the validated default seed (plans 15-04/15-05).
2. **`GraphView.MAX_TRACES` cap vs D-14 "cycle infinitely."** The generator caps `pool` to `maxItems`; consumers wrap `pool[i % size]`. The graph has a hard 3-trace cap. Recommendation: keep 3 traces this phase (no printer exceeds it), wrap in readout consumers, flag the cap. A true >3-trace graph is a separate, optional task.
   RESOLVED: keep MAX_TRACES=3; consumers wrap pool[i % size] (plan 15-07).
3. **Where the in-between-tier derivation lives.** `dinghy.js` derives tiers in the bridge; an alternative is teaching the Kotlin `Palette` to emit them. Recommendation: derive in the Kotlin token-bridge (port `tokensFromPalette`) — keeps `Palette.generate` a faithful 1:1 of `color.js` (so golden tests stay clean), and the bridge is dinghy-specific anyway. (Per CONTEXT.md this is explicitly the researcher/planner's discretion.)
   RESOLVED: derive the tiers in the Kotlin TokenBridge, keeping Palette.generate a 1:1 of color.js (plan 15-03).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node | Generating golden vectors from `color.js` (dev/test only) | ✓ | v24.16.0 | Inline the verified hexes from this doc into the test |
| `../theme_theory/app/color.js` | the port source + golden oracle | ✓ | present | — |
| Kotlin stdlib `kotlin.math` (cbrt/hypot/atan2) | the math core | ✓ | (Kotlin 2.1.x) | — |
| Windows Gradle (`E:\Android\gw.bat`) | building/testing | ✓ | — | — |
| flox (LineageOS/API 30, Adreno 320) | on-device UAT | ✓ (per project) | — | — |

**No blocking missing dependencies.** Note `color.js` lives OUTSIDE this repo (sibling). The port copies the math IN; the test oracle references the sibling path — pin the verified golden hexes in-repo so the test doesn't depend on the sibling at CI time.

## Sources

### Primary (HIGH confidence)
- `../theme_theory/app/color.js` — the generator code (read in full; ran in Node v24.16.0 to verify purity, determinism, mode behavior, and golden output) — **VERIFIED**
- `../theme_theory/app/dinghy.js` `tokensFromPalette()` — the token bridge (read in full) — **VERIFIED**
- `../theme_theory/COLOR-SYSTEM.md` Part III §A (token bridge table), Part IV (generator contract), §12 (platform constraints), Part II §C (pool index) — **CITED**
- `../theme_theory/app/README.md` — generator usage/opts — **CITED**
- Existing substrate read in full: `ThemeTokens.kt`, `ThemeResolver.kt`, `BakedTokens.kt`, `ThemePrefs.kt`, `Profile.kt`, `ProfileStore.kt`, `AppContainer.kt`, `DinghyTheme.kt`, `LocalTokens.kt`, `ThemeableView.kt`, `GraphView.kt`, `SettingsScreen.kt` (head) — **VERIFIED**
- `15-CONTEXT.md` (D-01..D-16, IN/OUT scope, deferred WR-02) — **CITED**
- Grep audits: `.violet` (2 files), `.heat` (23 files, semantic split), test inventory, `MAX_TRACES=3` — **VERIFIED**

### Secondary (MEDIUM confidence)
- `kotlin.math.cbrt` availability (stdlib since 1.3) — **CITED (training)**, low risk

### Tertiary (LOW confidence)
- None — all load-bearing claims were verified against source or executed.

## Metadata

**Confidence breakdown:**
- Port surface (color.js → Kotlin): HIGH — read + executed the source; pure deterministic math, golden-testable.
- Token bridge mapping: HIGH — `tokensFromPalette` is short and explicit; mapped field-by-field.
- Generate-and-cache rewire: HIGH — substrate read in full; the StateFlow boundary is unchanged.
- Persistence rework: HIGH — Phase-14 machinery (`mutateActive`/`writeScope`) is proven and reusable.
- Settings rebuild: MEDIUM — IA is clear (D-10/D-11); the color-wheel control is new (Compose Canvas, no existing wheel in repo — but the gesture pattern is established in `ScrubberPage`).
- Pool wiring: HIGH — consumers grepped; the `heat`-semantic-split and `MAX_TRACES` cap are the key gotchas, both flagged.

**Research date:** 2026-06-05
**Valid until:** 2026-07-05 (stable — pure in-repo math + existing libs; the only external is the sibling `color.js`, which is the owner-frozen oracle)
