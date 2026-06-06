# Phase 18: Preview Harness & Tokenization Foundation - Research

**Researched:** 2026-06-06
**Domain:** Jetpack Compose `@Preview` tooling + fixture infrastructure; Android string-resource i18n + pseudolocale/lint gates; a semantic icon-token abstraction over two icon sources; a debug-gated `start_dest` intent hook.
**Confidence:** HIGH (tooling/i18n are well-documented platform features verified against current docs + this codebase; the icon-registry shape and `start_dest` gate interaction are MEDIUM design recommendations, flagged).

---

<user_constraints>
## User Constraints (from 18-CONTEXT.md)

### Locked Decisions (D-01..D-08 — research THESE, no alternatives)
- **D-01:** Exactly **3 exemplar screens**, each de-risking a different downstream pattern:
  **PrintStatusScreen** (4 `PrintStatusMode` states → `@PreviewParameter` multi-state demo; forces the embedded-`GraphView` placeholder question), **FineTuneScreen** (present/absent capability-gating + busy-lock edge states → de-risks Phase 19 Output Controls), **SpoolScreen** (preview-safe Coil/image strategy + dense data fixtures + a Views-in-Compose picker sub-surface).
- **D-02:** Each exemplar demonstrates the **"core three + mechanical riders"** template: `@Preview` matrix **+** string tokenization **+** icon tokens **+** RTL `start`/`end`-relative modifiers **+** tokenized `contentDescription` / ≥48dp touch targets.
- **D-03:** **`@Stable` / stability-report / `ImmutableList` work is EXCLUDED** from the exemplar template (measure-then-fix, hot-path-specific, carpet-`@Immutable` causes stale-UI bugs). Deferred to Phase 22. The cheap mechanical riders (a11y, RTL start/end) DO go in the template.
- **D-04:** **Exclude** `GraphView`, `BedMeshHeatmapView`, `WebcamView` from dedicated Compose previews (`@Preview` is Compose-only) — document as a known limitation.
- **D-05:** Add a **`LocalInspectionMode` placeholder branch** so screens that *embed* a View surface still preview cleanly (labeled stand-in box, not a blank region). Same mechanism as the Coil image strategy.
- **D-06:** Live/perf truth for View surfaces stays on **`start_dest` + flox + the existing `BenchActivity`/`ViewsBenchScene` path**. No new standalone View-rendering harness this phase.
- **D-07:** Each semantic icon token (`DinghyIcon.Back`) carries a **primary reference** (Material Symbols ligature OR `ic_*` drawable — resolves transparently to either) **plus an alternate/canonical name** = the one-place remap handle a fork edits to switch icon sets without find-replacing ligatures.
- **D-08:** **Keep the icon token and the label string-token SEPARABLE** at the presentation layer — do NOT fuse into a "labeled-icon" primitive (forward-compat for the deferred icon/text/combo mode).

### Claude's Discretion (planner decides — this research recommends)
- `start_dest` readiness-gate interaction (bypass vs no-op-until-Ready) → see **Focus Q3**.
- Fixture-module location + naming → see **Focus Q2**.
- Where the preview-first / tokenized-first convention is documented → see **Focus Q4**.
- Build-wiring fix (`compose-ui-tooling-preview` configuration) → see **Focus Q1**.

### Deferred Ideas (OUT OF SCOPE)
- Icon / text / icon+text combo presentation mode (its own future phase; D-08 preserves forward-compat).
- **Exhaustive every-screen backfill** — all remaining `@Previews`, ~240 string-literal extraction, full icon call-site migration, a11y/RTL/`@Stable` riders, `compose-preview-screenshot` golden net — ONE co-sequenced per-screen pass in **Phase 22**.
- `@Stable`/`ImmutableList` migration (Phase 22).
- Test-matcher migration (`onNodeWithText("Back")` → resource-id) — only the 3 exemplars' tests this phase; rest rides Phase 22.
</user_constraints>

<phase_requirements>
## Phase Requirements

**REQUIREMENTS.md has NO PREV-* / I18N-* entries** (ROADMAP marks them "defined at phase discuss"; the discuss pass produced D-01..D-08 instead of formal REQ IDs). **Recommendation to planner:** derive coverage from the 5 ROADMAP Success Criteria below; optionally mint PREV-*/I18N-* IDs into REQUIREMENTS.md so verify-work has anchors. Each criterion maps to the discretion/decision items above.

| Success Criterion (ROADMAP §Phase 18) | Research Support |
|----|------------------|
| **SC-1** Any in-scope Compose screen renders in Studio Preview with realistic fixtures + themed tokens, under the 6 theme combos + `fs=L`, no live Moonraker — proven on the 3 exemplars | Standard Stack (preview tooling) + Architecture Pattern 1 (theme-wrapping previews via `DinghyTheme(resolver=...)`) + Pattern 3 (`@PreviewParameter`) + Focus Q8 (matrix expansion) |
| **SC-2** A reusable documented sample-data/fixture module + preview-token provider + Nexus-7 device profile exist, with a written convention future phases follow | Focus Q2 (fixture-module location), Focus Q4 (convention home), Pattern 2 (Nexus-7 device spec) |
| **SC-3** `strings.xml` + key convention + format-arg/plurals + semantic icon registry (primary+alternate, font-or-drawable) exist, proven on exemplars; pseudolocale (`en-XA`) + hardcoded-literal lint gate wired | Tokenization sections + Focus Q6 (icon registry shape) + Focus Q7 (pseudolocale + lint gate) |
| **SC-4** Build wiring correct: `compose-ui-tooling` debugImplementation, `compose-ui-tooling-preview` on compile classpath; debug `start_dest` hook jumps screens, absent/inert in release (dev-enable pattern, not `BuildConfig.DEBUG`) | Focus Q1 (build wiring) + Focus Q3 (start_dest) + Pattern 5 |
| **SC-5** Exhaustive backfill explicitly deferred/recorded as Phase-22 scope; foundation does not regress any existing screen | Deferred Ideas (already recorded) + Validation Architecture (no-regression gate) |
</phase_requirements>

## Summary

This phase is **infrastructure + convention**, not a user-facing surface. The platform features it rests on — Compose `@Preview` / `@PreviewParameter` / multipreview annotations / custom device specs, Android string resources + `stringResource()`, `en-XA` pseudolocale, `LocalInspectionMode` — are all mature, well-documented, and already supported by this build (Compose BOM 2026.05 → Compose UI 1.11.1, AGP 8.7.0, Kotlin 2.1.21, minSdk 23). **No new dependencies are required for the core work.** The only optional add is a Compose-aware hardcoded-string lint rule (the platform `HardcodedText` check is XML-only).

The real engineering is fixture design (stand-ins for live Moonraker), wrapping previews in the existing `DinghyTheme(resolver = ThemeResolver(...))` boundary so the six palette×brightness combos render, and the **icon-token abstraction** that unifies the app's two icon sources (55 `MaterialSymbol(ligature)` call sites + 7 `ic_*`/35 `painterResource` sites) behind one swap point with an alternate-name remap handle (D-07/D-08).

Two build-wiring facts are decisive and verified: (1) because `@Preview` functions for the exemplars will live in the **`main`** sourceset (same module as the screens, alongside production code per existing convention), `compose-ui-tooling-preview` **MUST stay `implementation`** (compile classpath of main) — moving it to `debugImplementation` breaks `@Preview` resolution in `main`. The "ships in release" concern is negligible: it is an annotations-only artifact, stripped by R8 as unused at runtime. (2) `compose-ui-tooling` is already correctly `debugImplementation` (line 104). So criterion SC-4's wiring is **already correct as-is** — the action is to *confirm and document*, not move.

**Primary recommendation:** Build a single `preview/` package in `main` holding (a) the `ThemeResolver`-seeded `@Preview` wrappers + custom Nexus-7 device-spec multipreview annotations, (b) reusable fixture objects (`SampleFixtures`) for the 3 exemplars, and (c) the `LocalInspectionMode` placeholder helpers. Establish `strings.xml` + `<area>_<element>` convention + the `DinghyIcon` registry as a `data class` (primary = sealed `IconRef`, plus `alternate: String`), all proven on PrintStatus/FineTune/Spool only. Wire `en-XA` via `isPseudoLocalesEnabled` (debug) + a baseline-scoped lint gate. Add the debug-gated `start_dest` extra on `MainActivity` reusing the existing `devCyclerEnabled` flag and `AppContainer.writeScope`. Record everything else as Phase-22.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `@Preview` rendering | Build/dev tooling (Layoutlib, host) | — | Host-rendered; says NOTHING about Adreno-320 perf. flox stays system-of-record. |
| Fixture / fake-state data | App (Compose, `main` sourceset) | — | Must be reachable by `@Preview` in `main` (cannot live in `test/`). |
| Theme-token seeding in previews | App theme layer (`DinghyTheme`/`ThemeResolver.bake`) | — | Reuses the existing single token boundary; no preview-only theme path. |
| String tokenization | Android resources (`res/values/strings.xml`) + `stringResource()` | — | Platform-standard build-time i18n; previews supply a context so it renders. |
| Icon-token registry | App design-system layer (above `MaterialSymbol` + `painterResource`) | Resource layer (font ttf + `ic_*` drawables) | The registry sits ABOVE the two render primitives and becomes the subset source. |
| `start_dest` jump | App entry (`MainActivity` intent) → `ShellNavState.dest` | OS (adb `am start` extra) | Mirrors `BenchActivity.EXTRA_SCENE`; debug-gated; secondary to the preview harness. |
| Pseudolocale / lint gate | Build config (`isPseudoLocalesEnabled`) + lint/detekt | — | Completeness gates; scoped to not trip on the ~240 deferred literals. |

---

## Focus Question Answers (the planner's decisive needs)

### Q1 — Build-wiring fix: is `compose-ui-tooling-preview` correct? [VERIFIED: app/build.gradle.kts:103-104]

**Current state (verified):**
```kotlin
implementation(libs.compose.ui.tooling.preview)      // line 103
debugImplementation(libs.compose.ui.tooling)         // line 104
```

**Verdict: LEAVE AS-IS. This is already the correct, recommended pattern. Do NOT move `-preview` to `debugImplementation`.**

- `compose-ui-tooling` (the renderer / `PreviewActivity` + `ComposeViewAdapter`) is correctly `debugImplementation` — stripped from release. ✅ No change.
- `compose-ui-tooling-preview` is the **annotations-only** artifact (`@Preview`, `@PreviewParameter`, `PreviewParameterProvider`, the multipreview templates). Because the exemplar `@Preview` functions will live in the **`main`** sourceset (this project authors code in `main`; there is no separate `debug/` Kotlin sourceset today), the annotation symbol **must be on the compile classpath of `main`** — i.e. `implementation`. [CITED: developer.android.com/develop/ui/compose/tooling/previews — "@Preview annotations in the main source set will be stripped... unresolved reference if -preview is debugImplementation only"]
- The CONTEXT note "ships the preview-annotation lib in release" is **technically true but harmless**: it is a tiny annotations jar with no runtime behavior; R8 (enabled for release) removes it as unreachable. The only way to make `-preview` `debugImplementation` is to ALSO move every `@Preview` into a `debug/` sourceset — needless churn for an inert artifact, and it would put previews *away* from the screens they document (against the preview-first convention this phase establishes).

**Action for the plan:** A confirmation + a code comment on line 103 explaining WHY `-preview` is `implementation` (so a future cleanup pass doesn't "fix" it and break previews). Counts toward SC-4 as *verified correct*, not *changed*. `[VERIFIED: codebase]`

### Q2 — Fixture-module location + naming

**Recommendation: a same-module package `works.mees.dinghy.preview` in `app/src/main/`** (NOT a separate Gradle module, NOT `test/`).

- `@Preview` functions are compiled and rendered against the `main` (+ debug tooling) classpath. They **cannot see `src/test/` or `src/androidTest/`** — so fixtures usable by previews must live in `main` (or a `main`-visible module). [CITED: tokenization-staging.md line 199 — "stringResource works in @Preview because previews provide a context"; the same main-visibility constraint applies to fixture objects.]
- A separate Gradle module is overkill for v1's 3 exemplars and adds build graph + the project's central minSdk-floor audit surface. Promote to a module only if fixtures grow large (Phase 22+).
- **Naming:** `works.mees.dinghy.preview` with:
  - `SampleFixtures.kt` — top-level `object SampleFixtures` exposing reusable stand-ins: `printStatusModes` (all 4), `printerState(...)` builders, `fineTuneVm`-shaped fixtures (present/absent/busy variants), `spoolList` (dense), temp series, file/macro lists. Reusable objects, NOT per-preview one-offs (staging doc requirement).
  - `PreviewTheming.kt` — the `@Composable PreviewBox(seed/tuple) { content }` wrapper (Q1+Pattern 1) and the seed list for the 6 combos.
  - `DinghyPreviews.kt` — the custom multipreview annotation classes (`@Nexus7Previews`, `@ThemeMatrixPreviews`, etc.).
  - `PreviewPlaceholders.kt` — the `LocalInspectionMode` helpers (Q5).
- **Caveat:** keep fixtures plain immutable data; they will be the seed source the Phase-22 backfill reuses. `[ASSUMED — naming convention is a recommendation; planner finalizes]`

### Q3 — `start_dest` readiness-gate interaction

**Recommendation: LAND-WITH-WHATEVER-STATE (do NOT bypass the gate, do NOT no-op-until-Ready). Seed `ShellNavState.dest` from the extra; let `RootController` apply its normal Splash/Settings/Shell routing on top.**

Rationale (verified against `RootController.kt:101-135` + `ShellNavState.kt:84-103`):
- `RootController` gates the *whole shell* (`when { Connect/escape → Printers; showSplash → Splash; else → AppShell }`). Setting `dest` does NOT bypass that — `dest` only chooses *which screen inside `AppShell`* renders, and `AppShell` is only composed in the `else` (Ready) branch. So a `start_dest` seed is **naturally inert until Klippy-Ready** without any special-casing: if the printer isn't ready you see the Splash, and the moment it goes Ready you land on the seeded screen.
- This is exactly the staging-doc framing (line 130-136): `start_dest` is the SECONDARY tool; "lands you on the screen with whatever real state the printer happens to be in." Deterministic state-injected review is the preview harness's job.
- **Implementation detail (the trap):** seed `dest` BEFORE `RootController` first composes. The cleanest seam is to read the extra in `MainActivity.onCreate`, gate on `devCyclerEnabled`, and pass an optional `startDest: Dest?` into `RootController` → `rememberShellNavState`. Because `ShellNavState` is hoisted in `RootController` and *survives the Splash blip* (ShellNavState.kt class doc), seeding it once is correct: the user boots to Splash, then arrives on the seeded screen when Ready, exactly as if they'd navigated there. **Do not** set `dest` from a `LaunchedEffect` inside `AppShell` (it would re-fire on recompose / re-apply on every recovery).
- **DataStore caveat:** if the hook also needs to *toggle* `devCyclerEnabled` (it should reuse, not re-implement, the existing flag), any write MUST route through `AppContainer.writeScope` ([[dinghy-compose-write-scope-cancellation]]). But the *read* of `start_dest` is a one-shot intent read — no write needed for the jump itself. `[VERIFIED: codebase RootController.kt, ShellNavState.kt]`

### Q4 — Where the preview-first / tokenized-first convention is documented

**Recommendation: a new `docs/CONVENTIONS.md` (or `docs/ui_design/PREVIEW_AND_TOKENS.md`) as the authoritative spec, PLUS a short pointer added to `CLAUDE.md`.**

- `docs/ui_design/` is already the project's binding UI LAW that downstream agents are instructed to read before building any screen (CLAUDE.md). Putting the preview-first/tokenized-first convention **inside the docs that screens already cite** is the only placement that makes Phases 19-21 actually follow it. A module README in `app/` would not be read by the screen-building flow.
- `CLAUDE.md` already has a "UI Design System (LAW — read `docs/ui_design/` before building any screen)" block. Add one line there: "Every new screen ships with `@Preview` coverage (the 6-combo + `fs=L` matrix), tokenized strings, and `DinghyIcon` tokens from day one — see `docs/ui_design/PREVIEW_AND_TOKENS.md`." This is the enforcement hook: CLAUDE.md is loaded into every agent's context.
- Content the convention doc must specify (so 19-21 are mechanical): the `PreviewBox` wrapper idiom, the multipreview annotation names to apply, the fixture package + how to add a fixture, the `<area>_<element>` string key convention + format-args/plurals examples, the `DinghyIcon` registration pattern, the RTL `start/end` rule + hardware-spatial exceptions, and the ≥48dp touch-target + tokenized-`cd_*` rider. `[ASSUMED — placement is a recommendation; planner's call per CONTEXT]`

### Q5 — Preview-safe Coil / `LocalInspectionMode` strategy

**Verified current state:** `LocalInspectionMode` is used NOWHERE in the codebase yet (`grep` = NONE). PrintStatusScreen uses Coil `AsyncImage` for the gcode thumbnail (`PrintStatusScreen.kt:54,523`). SpoolScreen renders its icon/QR via `painterResource` (`SpoolScreen.kt:30,379`) — **QR is NOT Coil-loaded today**, it is a static drawable; so the Spool "preview-safe image" proof is mainly about the *filament thumb/Coil-network* path, plus the dense-fixture rendering.

**The canonical idiom (two flavors):**

1. **For Coil `AsyncImage` (PrintStatus thumb, any network/QR-via-Coil):** branch on `LocalInspectionMode.current` and substitute a local placeholder painter.
```kotlin
// Source: developer.android.com/develop/ui/compose/tooling/previews (LocalInspectionMode pattern)
@Composable
fun ThumbnailImage(model: Any?, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        // Preview: Coil renders blank (no real loader) — show a labeled stand-in.
        PreviewPlaceholderBox(label = "thumb", modifier = modifier)
    } else {
        AsyncImage(model = model, contentDescription = null, modifier = modifier)
    }
}
```
   Alternatively pass `placeholder = painterResource(R.drawable.benchy)` to `AsyncImage` (Coil shows the placeholder in inspection mode) — but the explicit `LocalInspectionMode` branch is preferred because it is the SAME mechanism used for the embedded-View case (D-05), keeping one idiom.

2. **For embedded classic-View surfaces (D-05 — `GraphViewHost`, `BedMeshHeatmapHost`, `WebcamViewHost`):** `AndroidView` does not render under Layoutlib, so wrap each host's body in the inspection branch and emit a labeled stand-in `Box` instead of the `AndroidView`.
```kotlin
@Composable
fun GraphViewHost(tokens: ThemeTokens, snapshot: FloatArray, modifier: Modifier = Modifier, drawArea: Boolean = true) {
    if (LocalInspectionMode.current) {
        PreviewPlaceholderBox(label = "GraphView (live on device)", modifier = modifier)
        return
    }
    AndroidView(/* ...existing... */)
}
```
   `PreviewPlaceholderBox` should paint a token-colored outline + a centered label so the preview reads as "intentional stand-in", not "broken region" (D-05 wording: "labeled stand-in box, not a blank/broken region"). Insert points verified: `render/GraphViewHost.kt:34`, `render/BedMeshHeatmapHost.kt`, `render/WebcamViewHost.kt` (all exist). `[VERIFIED: codebase]`

### Q6 — Icon-token registry abstraction (D-07/D-08)

**Recommendation: a `data class DinghyIcon` whose primary reference is a sealed `IconRef`, carrying an `alternate: String` remap handle. Keep it SEPARABLE from any label string (D-08).**

```kotlin
// works.mees.dinghy.designsystem.icons
/** A single icon reference that resolves transparently to EITHER source (D-07). */
sealed interface IconRef {
    /** Material Symbols font ligature, e.g. "arrow_back". The current MaterialSymbol primitive renders it. */
    @JvmInline value class Ligature(val name: String) : IconRef
    /** A custom vector drawable, e.g. R.drawable.ic_lock_closed. painterResource renders it. */
    @JvmInline value class Drawable(@DrawableRes val resId: Int) : IconRef
}

/**
 * One semantic icon token. [primary] is what renders today; [alternate] is the canonical
 * remap name a community fork edits in ONE place to switch the whole app to its icon set
 * (D-07) — never find-replacing ligatures across call sites. NOT fused with a label (D-08).
 */
data class DinghyIcon(val primary: IconRef, val alternate: String)

/** The single registry — the ONE swap point + the subset source for tools/subset-symbols. */
object DinghyIcons {
    val Back      = DinghyIcon(IconRef.Ligature("arrow_back"),      alternate = "back")
    val Save      = DinghyIcon(IconRef.Save_/* ... */)
    val LockOpen  = DinghyIcon(IconRef.Drawable(R.drawable.ic_lock_open), alternate = "lock_open")
    // ...one entry per semantic icon used by the 3 exemplars this phase.
}

/** The unifying render primitive — call sites use this, never a raw ligature/painterResource. */
@Composable
fun DinghyIconView(icon: DinghyIcon, modifier: Modifier = Modifier,
                   tint: Color = LocalTokens.current.text, sizeSp: Float = 32f,
                   contentDescription: String?) {
    when (val ref = icon.primary) {
        is IconRef.Ligature -> MaterialSymbol(ref.name, modifier, tint, sizeSp)   // reuses existing primitive
        is IconRef.Drawable -> Icon(painterResource(ref.resId), contentDescription, modifier.size(sizeSp.dp), tint)
    }
}
```

Why this shape:
- **`sealed IconRef` + `data class DinghyIcon`** beats a "data class with nullable drawable/ligature" — the nullable-pair form makes both-null / both-set states representable (illegal), whereas the sealed form makes "exactly one source" a compile-time guarantee. This is the standard Kotlin modeling choice for "one of N".
- **`alternate: String`** is the D-07 remap handle: a fork maps its set by editing `DinghyIcons` entries (or, later, a name→IconRef table keyed by `alternate`) in one file. The `alternate` is the *canonical semantic name* ("back", "save", "print_done"), decoupled from the upstream ligature spelling.
- **D-08 separability:** `DinghyIcon` carries NO label. The label is a separate `stringResource(R.string.action_back)` at the call site. A future combo-mode composes `DinghyIconView(icon) + Text(label)` without re-plumbing.
- **Builds on existing foundation:** `DinghyIconView` delegates to the unchanged `MaterialSymbol` (designsystem/MaterialSymbol.kt:31) for ligatures and `painterResource` for drawables. The registry becomes the new subset source for `tools/subset-symbols` (replace the scattered-ligature inventory with "iterate `DinghyIcons`, collect `Ligature.name`s").
- **Scope this phase:** register only the icons the 3 exemplars use; migrate ONLY those call sites. The other ~50 stay raw until Phase 22. `[ASSUMED — shape is a design recommendation; MEDIUM confidence; planner/Codex-review the final shape]`

### Q7 — Pseudolocale + hardcoded-literal lint gate

**Pseudolocale:** enable `en-XA` (accented) and optionally `ar-XB` (RTL+brackets) via AGP's debug build type — no resConfigs trimming needed (project has no resConfigs today; verified).
```kotlin
// app/build.gradle.kts — debug build type
buildTypes {
    debug { isPseudoLocalesEnabled = true }   // renders en-XA / ar-XB at runtime
}
```
Then on-device/emulator, switch the system locale to "English (XA)" — every resource-sourced string renders accented+padded; any plain-English text at runtime is a still-hardcoded literal (the completeness check, staging doc line 83-87). This is **build-time capability only** — no translations shipped. [CITED: tokenization-staging.md; AGP `isPseudoLocalesEnabled` is the standard mechanism.]

**Hardcoded-literal lint gate (the scoping problem):**
- The platform `HardcodedText` lint check is **XML-only** — it does NOT catch Compose `Text("...")` (staging doc line 147-149, confirmed). [CITED: tokenization-staging.md]
- A Compose-aware rule is needed. Cleanest option for this stack: **detekt with a Compose ruleset / a small custom detekt rule** that flags string-literal arguments to `Text(...)` / `label =` / `contentDescription =`. detekt is not yet wired (verified: no detekt config, no lint config in build). [CITED: github.com/appKODE/detekt-rules-compose, mrmans0n.github.io/compose-rules]
- **CRITICAL scoping requirement (the ~240-literal problem):** a build-failing rule that fires on all ~240 not-yet-tokenized literals would brick the build immediately. **Recommendation: introduce the rule in WARN/baseline mode this phase, with a detekt baseline file capturing the existing ~240 sites as accepted.** New literals (in the 3 exemplars and all future screens) are NOT in the baseline → flagged. Phase 22's backfill drains the baseline and flips the rule to error. This gives "can't silently grow back" enforcement for new code without blocking on the deferred debt. The `@Suppress`/baseline mechanism also covers legit exceptions (em-dash placeholders, dev-only text). Pass-through `Text(filename)` is a variable, not a literal, so it never trips (staging doc line 149).
- **Alternative (lighter):** skip detekt, rely solely on the `en-XA` pseudolocale visual check as the completeness gate this phase, and defer the automated lint to Phase 22. This is acceptable if adding detekt is judged too heavy for a foundation phase — but it loses the "can't regress" guard. **Recommend the detekt-baseline route** since establishing the gate IS this phase's stated SC-3 deliverable. `[ASSUMED — detekt vs lint choice is a recommendation; verify detekt-rules-compose minSdk/AGP compatibility at plan time]`

### Q8 — `@PreviewParameter` multi-state + 6-theme × `fs=L` matrix expansion

**For the 4 `PrintStatusMode` states:** a `PreviewParameterProvider` is the textbook fit (D-01). Verified the type exists: `PrintStatusMode` sealed interface with `Standby`/`Printing`/`Paused`/`Terminal(kind)` (PrintStatusMode.kt:23-38).
```kotlin
class PrintStatusModeProvider : PreviewParameterProvider<PrintStatusMode> {
    override val values = sequenceOf(
        PrintStatusMode.Standby, PrintStatusMode.Printing, PrintStatusMode.Paused,
        PrintStatusMode.Terminal(TerminalKind.Complete),
        PrintStatusMode.Terminal(TerminalKind.Cancelled),
        PrintStatusMode.Terminal(TerminalKind.Error),
    )
}
@Preview @Composable
fun PrintStatusPreview(@PreviewParameter(PrintStatusModeProvider::class) mode: PrintStatusMode) {
    PreviewBox { PrintStatusScreen(state = SampleFixtures.forMode(mode), ...) }
}
```

**For the 6-theme × `fs=L` matrix — minimize `@Preview` proliferation:** **use multipreview annotation classes, NOT a PreviewParameter for theme** (theme is a wrapper concern, not a screen parameter). Define ONE custom annotation that bundles the meaningful configs, apply it once per exemplar:
```kotlin
// One annotation = the canonical Dinghy review matrix. Reuse on every exemplar.
@Preview(name = "Colorful Dark",  device = NEXUS7, group = "theme")
@Preview(name = "Colorful Light", device = NEXUS7, group = "theme")
@Preview(name = "Simple Dark",    device = NEXUS7, group = "theme")
@Preview(name = "Simple Light",   device = NEXUS7, group = "theme")
@Preview(name = "HighContrast Dark",  device = NEXUS7, group = "theme")
@Preview(name = "HighContrast Light", device = NEXUS7, group = "theme")
annotation class DinghyThemePreviews
```
**The catch:** `@Preview` annotations cannot pass the theme/seed INTO the composable — they only set Layoutlib config (device, locale, uiMode, fontScale). The 6 token-combos must be selected by the WRAPPER, not the annotation. So the practical idiom is **either**:
- (a) Six explicit `@Preview` functions, each calling `PreviewBox(seed = COLORFUL_DARK_SEED) { ... }` — explicit, verbose, but dead-simple and what most teams ship; **or**
- (b) A `PreviewParameterProvider<ThemeTuple>` carrying the 6 seeds, combined with the `PrintStatusModeProvider` (Compose lets you have at most one `@PreviewParameter` per function, so theme-as-parameter and mode-as-parameter cannot BOTH be parameters — pick mode as the parameter, theme as six wrapper functions, OR theme as the parameter and mode fixed per function).

**Recommendation:** Make **`PrintStatusMode` the `@PreviewParameter`** (its 4-6 states are the interesting axis), and select the **theme via a wrapper** in 6 sibling functions OR a single function reading a `group`-tagged seed. For `fs=L`, the app pins OS `fontScale=1f` (DinghyTheme.kt:48) — so the **`@Preview(fontScale=...)` axis is USELESS here** (it's neutralized). `fs=L` must be injected through the app's OWN `fs` value in the `ThemeTuple` (`tuple.fs`, verified ThemePrefs.ThemeTuple has `val fs: Float`). So add ONE more wrapper/seed with `fs = L` for the overflow check. Document this clearly — it's the #1 thing a future phase will get wrong (reaching for `@Preview(fontScale=)` and seeing no effect).

**Net guidance to minimize proliferation:** per exemplar ≈ (mode states via PreviewParameter) × (a small fixed set of theme wrappers: at minimum Colorful-Dark + High-Contrast-Light + one `fs=L`; the full 6 only where token-contrast bugs are likely). Don't render 4 modes × 6 themes × fs = 28 panels per screen by default — that's slow and noisy. Render the full theme matrix on ONE representative state and the full state matrix on ONE representative theme, plus the `fs=L` overflow shot. `[VERIFIED: codebase + CITED: developer.android.com multipreview]`

---

## Standard Stack

### Core (ALL already present — no new core deps)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.compose.ui:ui-tooling-preview` | BOM 2026.05 → 1.11.1 | `@Preview`, `@PreviewParameter`, multipreview templates, `PreviewParameterProvider` | Already in catalog + applied (`implementation`, line 103). The annotation API. `[VERIFIED: libs.versions.toml:77, build.gradle.kts:103]` |
| `androidx.compose.ui:ui-tooling` | BOM 2026.05 → 1.11.1 | The Layoutlib renderer / `ComposeViewAdapter` | Already `debugImplementation` (line 104) — correct. `[VERIFIED: build.gradle.kts:104]` |
| Android string resources | platform | `res/values/strings.xml` + `stringResource()` | The platform i18n norm; one-file-per-language community contribution model. `stringResource` works inside `@Preview` (context provided). `[CITED: tokenization-staging.md:199]` |
| `LocalInspectionMode` | Compose runtime (BOM) | preview-safe Coil + embedded-View placeholder branch | Standard Compose API; the canonical "am I in a preview" check. `[VERIFIED: not yet used in codebase]` |
| Coil 3 | 3.1.0 | existing thumbnail loader (PrintStatus); needs the inspection branch | Already present (libs.versions.toml:44). `[VERIFIED]` |

### Supporting (optional — one possible add)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `io.gitlab.arturbosch.detekt` + a Compose ruleset (e.g. `io.nlopez.compose.rules:detekt` or `ru.kode:detekt-rules-compose`) | latest compatible w/ Kotlin 2.1.21 | hardcoded-`Text("…")` lint gate (Q7) | If the planner adopts the automated gate (recommended for SC-3) vs pseudolocale-visual-only. Verify Kotlin-2.1 compatibility + introduce with a baseline. `[ASSUMED — verify compatibility at plan time]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| string resources | Kotlin string catalog / Lyricist / centralized consts | Rejected by tokenization-staging.md — makes non-coder translation harder; consts don't deliver real localization. |
| `sealed IconRef` | nullable-pair `data class(ligature:String?, drawable:Int?)` | Nullable pair allows illegal both-null/both-set states; sealed makes "exactly one source" compile-safe. |
| `@PreviewParameter` for theme | multipreview annotation + wrapper | Only one `@PreviewParameter` per function; theme is a wrapper concern, not screen data. Use PreviewParameter for the screen's state axis. |
| `compose-preview-screenshot` plugin | (defer) | Explicitly Phase-22 (golden-image churn during active polish + another mock-vs-reality host surface). AGP 8.7 supports it (≥8.5). `[CITED: developer.android.com/studio/preview/compose-screenshot-testing]` |

**Installation:** No `npm`/Gradle dependency add for the core. IF adopting the lint gate, add a detekt plugin + ruleset to `libs.versions.toml` and `app/build.gradle.kts` (verify minSdk/Kotlin compat — detekt is a build-time tool, no runtime/minSdk impact). `en-XA` is a build-config flag, not a dependency.

## Package Legitimacy Audit

The core work installs **no external packages** (all required libraries are already in the pinned catalog). The only *candidate* new dependency is a detekt Compose ruleset for Q7's lint gate, which is OPTIONAL and deferred to planner decision.

slopcheck not run (no network package install performed; all core libs are pre-existing pinned AndroidX/Square/Coil artifacts already audited by this project's `verifyMinSdk` task). If the planner adopts a detekt Compose ruleset, it MUST be verified at plan time:
- `io.nlopez.compose.rules:detekt` (the "mrmans0n / compose-rules" ruleset, formerly Twitter) — established, widely used. `[ASSUMED — verify on Maven Central + Kotlin 2.1 compat before adding]`
- `ru.kode:detekt-rules-compose` (appKODE) — established alternative. `[ASSUMED — verify before adding]`

**Disposition:** No packages to approve/remove for the core phase. The detekt ruleset, if chosen, must be gated behind a `checkpoint:human-verify` (build-tool only, but still external) and verified on Maven Central + against AGP 8.7 / Kotlin 2.1.21.

## Architecture Patterns

### System Architecture Diagram

```
                         ┌─────────────────────────────────────────────┐
   Android Studio  ─────▶│  @Preview fn (works.mees.dinghy.preview.*)   │
   Design pane           │  └─ DinghyThemePreviews multipreview          │
   (Layoutlib, HOST)     │  └─ @PreviewParameter(PrintStatusModeProvider)│
                         └───────────────┬─────────────────────────────┘
                                         │ wraps
                                         ▼
                         ┌─────────────────────────────────────────────┐
                         │  PreviewBox(seed) {                           │
                         │    DinghyTheme(resolver=ThemeResolver(seed))  │  ◀── reuses the ONE token
                         │      → LocalTokens.current resolves           │      boundary (no preview-
                         │  }                                            │      only theme path)
                         └───────────────┬─────────────────────────────┘
                                         │ renders
                                         ▼
        ┌────────────────────────────────────────────────────────────────────┐
        │  Exemplar screen (PrintStatus / FineTune / Spool)                    │
        │   ├─ stringResource(R.string.<area>_<element>)   ◀── string tokens   │
        │   ├─ DinghyIconView(DinghyIcons.X)               ◀── icon tokens     │
        │   ├─ if (LocalInspectionMode.current) Placeholder ◀── Coil + D-05     │
        │   │     else AsyncImage / GraphViewHost(AndroidView)                  │
        │   └─ fixtures from SampleFixtures (NO live Moonraker)                 │
        └────────────────────────────────────────────────────────────────────┘

   RUNTIME (device / flox — the system-of-record):
        adb am start ... --es start_dest FineTune
              │ (debug-gated by devCyclerEnabled)
              ▼
        MainActivity.onCreate reads extra ─▶ RootController(startDest)
              ▼                                   │ Splash gate applies on top
        ShellNavState.dest = FineTune  ───────────┘ (lands when Klippy Ready)
```

### Recommended Project Structure
```
app/src/main/java/works/mees/dinghy/
├── preview/                       # NEW — main-visible, reachable by @Preview
│   ├── SampleFixtures.kt          #   reusable fake-state stand-ins (4 modes, spool list, temps...)
│   ├── PreviewTheming.kt          #   PreviewBox(seed){} wrapper + the 6 combo seeds + fs=L seed
│   ├── DinghyPreviews.kt          #   custom multipreview annotations (@Nexus7, @DinghyThemePreviews)
│   └── PreviewPlaceholders.kt     #   PreviewPlaceholderBox + LocalInspectionMode helpers (D-05/Q5)
├── designsystem/icons/            # NEW — the icon-token registry (Q6)
│   ├── DinghyIcon.kt              #   IconRef sealed + DinghyIcon data class
│   ├── DinghyIcons.kt             #   the registry object (exemplar icons only this phase)
│   └── DinghyIconView.kt          #   unifying render primitive (delegates to MaterialSymbol/painterResource)
├── designsystem/MaterialSymbol.kt # UNCHANGED — registry sits above it
└── (exemplar screens edited in-place: printstatus/, finetune/, spool/)

app/src/main/res/values/strings.xml   # NEW — master English vocabulary (exemplar strings only this phase)
docs/ui_design/PREVIEW_AND_TOKENS.md   # NEW — the convention spec Phases 19-21 follow (Q4)
```

### Pattern 1: Theme-wrapped preview via the existing resolver overload
**What:** Wrap every `@Preview` in `DinghyTheme(resolver = ThemeResolver(<seed>))` so `LocalTokens.current` resolves to a real baked theme — NOT defaults.
**When:** every preview function.
```kotlin
// Source: DinghyTheme.kt:59-63 (the resolver overload exists precisely for bench/test/preview)
@Composable
fun PreviewBox(tuple: ThemePrefs.ThemeTuple = SampleFixtures.colorfulDark, content: @Composable () -> Unit) {
    val resolver = ThemeResolver()           // or seed via bake(tuple) — see note
    DinghyTheme(resolver = resolver) { content() }
}
```
**Note:** the cleanest token-seeding for the 6 combos is `ThemeResolver.bake(tuple)` (AppContainer pure-bake, ThemeResolver.kt:162) producing a `ThemeTokens`, fed through the `tokensFlow` overload as `flowOf(baked)`. The planner confirms the exact seam (resolver-construct vs bake-and-flow); both reuse the ONE boundary, no preview-only theme code. `[VERIFIED: DinghyTheme.kt, ThemeResolver.kt:162]`

### Pattern 2: Nexus-7 2013 device profile (custom device spec)
**What:** a reusable `device =` spec string matching real target geometry (1920×1200 @ 320dpi, the genuine Adreno-320 panel).
```kotlin
// Source: developer.android.com multipreview — spec:width=…,height=…,unit=…,dpi=…
const val NEXUS7 = "spec:width=1920px,height=1200px,dpi=320,orientation=landscape"
const val NEXUS7_PORTRAIT = "spec:width=1200px,height=1920px,dpi=320,orientation=portrait"
@Preview(device = NEXUS7) @Preview(device = NEXUS7_PORTRAIT)
annotation class Nexus7Previews
```
**Caveat:** this is GEOMETRY only — it tells you nothing about Adreno-320 perf (host-rendered). Portrait+landscape both matter (project supports both). `[CITED: developer.android.com/develop/ui/compose/tooling/previews]`

### Pattern 3 & 8: see Focus Q8.

### Anti-Patterns to Avoid
- **`@Preview(fontScale = 1.5f)` to test large text** — NO-OP here. The app pins OS `fontScale=1f` at DinghyTheme.kt:48; large-text must be injected via the app's own `fs` (`ThemeTuple.fs`). Document loudly.
- **Putting fixtures in `src/test/`** — `@Preview` in `main` cannot see them. Fixtures live in `main`.
- **Setting `dest` from a `LaunchedEffect` inside `AppShell`** for `start_dest` — re-fires on recompose/recovery. Seed once in `RootController` from the hoisted `ShellNavState` (Q3).
- **Carpet `@Immutable`** on list/state classes "to fix stability" — explicitly D-03-deferred; causes stale-UI bugs.
- **A build-FAILING hardcoded-string rule with no baseline** — bricks the build on the ~240 deferred literals. Use a baseline (Q7).
- **Fusing icon + label into one primitive** — violates D-08; blocks the future combo mode.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| "Am I in a preview?" detection | a custom `BuildConfig`/static flag | `LocalInspectionMode.current` | Platform-correct; true under Layoutlib, false on device. |
| Multi-state preview rendering | N copy-paste preview fns per state | `PreviewParameterProvider` | One fn, N values; the textbook idiom (D-01). |
| Repeated preview configs (theme/device matrix) | re-annotating every screen with 6 `@Preview` lines | a custom multipreview annotation class | Define once (`@DinghyThemePreviews`), apply everywhere. |
| Localization completeness check | manual grep for English strings | `en-XA` pseudolocale | Accents+pads every resource string; misses show as plain English. |
| Theme-seeding for previews | a preview-only token map | `DinghyTheme(resolver=…)` / `ThemeResolver.bake` | Reuse the ONE boundary; previews match on-device theming exactly. |
| Icon source unification | per-call-site `if ligature else drawable` | the `DinghyIcon`/`IconRef`/`DinghyIconView` registry | One swap point + alternate-name remap (D-07). |

**Key insight:** This phase is almost entirely *assembling existing platform primitives into a convention*. The only genuinely new code is fixtures, the icon registry, and ~3 lines of `start_dest` intent reading. Resist building anything bespoke.

## Runtime State Inventory

> This is NOT a rename/refactor/migration phase (it adds infrastructure + tokenizes 3 screens without changing behavior). Tokenization is a mechanical externalization that is behavior-neutral (tokenization-staging.md:185). No stored data, live-service config, OS-registered state, secrets, or build artifacts carry a renamed string that requires migration.
>
> **One adjacent note (not a migration):** externalizing the 3 exemplars' strings will break any instrumented test matching literal text (`onNodeWithText("Back")`) on those screens. This is the "test-matcher migration" deferred item — for THIS phase, only the 3 exemplars' tests are touched (migrate to `context.getString(R.string.action_back)` or `testTag`). The rest ride Phase 22. `[VERIFIED: tokenization-staging.md:157-160]`

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — no datastore keys reference exemplar UI strings. | none |
| Live service config | None. | none |
| OS-registered state | None. | none |
| Secrets/env vars | None. | none |
| Build artifacts | None requiring migration. (`tools/subset-symbols` font-subset list becomes driven BY the new registry going forward, but no existing artifact breaks this phase — only exemplar icons are registered.) | none this phase |

## Common Pitfalls

### Pitfall 1: `@Preview(fontScale=…)` silently does nothing
**What goes wrong:** A planner/dev adds `@Preview(fontScale = 1.5f)` for the `fs=L` overflow check and sees no change.
**Why:** `DinghyTheme.kt:48` overrides `LocalDensity` to `fontScale = 1f` — OS font scale is the SOLE-authority casualty by design (the app's `fs` is the only text-size axis).
**How to avoid:** inject `fs=L` via `ThemePrefs.ThemeTuple.fs` in the preview's seed/wrapper, never the `@Preview` annotation.
**Warning signs:** large-text preview looks identical to baseline.

### Pitfall 2: Moving `-preview` to `debugImplementation` to "strip it from release"
**What goes wrong:** `@Preview` in `main` becomes an unresolved reference; the whole module fails to compile (or previews vanish).
**Why:** the annotation must be on `main`'s compile classpath; `@Preview` functions live in `main`.
**How to avoid:** leave `-preview` as `implementation` (it's an inert annotations jar, R8-stripped). Add the explaining comment.

### Pitfall 3: Hardcoded-string lint bricks the build
**What goes wrong:** the new rule fires on all ~240 not-yet-tokenized literals → red build.
**Why:** the backfill of those 240 is deferred to Phase 22; the gate exists to stop NEW debt.
**How to avoid:** detekt baseline capturing the existing sites (Q7); only un-baselined (new) literals fail.

### Pitfall 4: `LocalInspectionMode` only branched for Coil, not the embedded Views
**What goes wrong:** PrintStatus preview renders fine except a blank/broken region where `GraphViewHost`'s `AndroidView` would be.
**Why:** `AndroidView` doesn't render under Layoutlib; D-05 requires the placeholder branch in the HOST, not just the image.
**How to avoid:** add the inspection branch to `GraphViewHost`/`BedMeshHeatmapHost`/`WebcamViewHost` (Q5 flavor 2).

### Pitfall 5: DataStore write-scope cancellation on the dev-enable plumbing
**What goes wrong:** toggling `devCyclerEnabled` (if the hook's UI does so) from a composition scope gets cancelled by same-frame nav → silently dropped on slow flash.
**Why:** documented project trap ([[dinghy-compose-write-scope-cancellation]]).
**How to avoid:** route any write through `AppContainer.writeScope` (verified at di/AppContainer.kt:118). The `start_dest` *read* is a one-shot intent read — no write needed for the jump.

### Pitfall 6: Treating previews as device truth
**What goes wrong:** green host preview hides an Adreno-320-only bug (the project's recurring scar: bed-mesh ramp, DataStore race, mock-vs-reality strikes).
**Why:** Layoutlib renders on the host, not the device GPU/ART.
**How to avoid:** frame previews as iteration SPEED only; flox stays system-of-record. Say NOTHING about perf in preview-derived claims. (CONTEXT hard boundary; [[dinghy-display-mock-vs-reality]].)

### Pitfall 7: Font sizes too small in the exemplar previews
**What goes wrong:** Claude defaults exemplar text too small (recurring).
**How to avoid:** use the established `fsSp(base, fs)` scale (ThemeTokens.kt:199) — floor 15sp metadata, 17-18sp body, 20-22sp titles, 26sp tabular, 30sp+ focus ([[dinghy-font-sizes-too-small]]).

## Code Examples

See Focus Q5 (LocalInspectionMode), Q6 (icon registry), Q8 (PreviewParameter + multipreview), Pattern 1 (PreviewBox), Pattern 2 (Nexus-7 device spec). All idioms cited to developer.android.com tooling docs or verified against this codebase.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Six repeated `@Preview` lines per screen | Multipreview annotation templates (`@PreviewScreenSizes`, custom annotation classes) | ui-tooling-preview 1.6.0-alpha01+ (long stable by BOM 2026.05) | Define the Dinghy matrix once, apply everywhere. `[CITED: developer.android.com]` |
| XML-only `HardcodedText` lint | Compose-aware detekt/lint rule for `Text("…")` | community rulesets (appKODE, mrmans0n/compose-rules) | Enables the Compose literal gate (Q7). `[CITED: github.com/appKODE, mrmans0n.github.io]` |
| Manual screenshot diffing | `compose-preview-screenshot` (AGP ≥8.5) | — | Phase-22 bolt-on; not now. `[CITED: developer.android.com/studio/preview/compose-screenshot-testing]` |

**Deprecated/outdated:** none relevant — the stack is current and pinned.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Fixture package `works.mees.dinghy.preview` in `main` is the right home/name | Q2 | Low — any `main`-visible package works; naming is cosmetic. |
| A2 | `start_dest` should land-with-whatever-state (gate applies on top) | Q3 | Medium — owner explicitly left to planner; this matches staging-doc framing, but planner/owner may prefer bypass-to-screen for pure layout shots. Verified mechanically sound. |
| A3 | Convention lives in `docs/ui_design/PREVIEW_AND_TOKENS.md` + a CLAUDE.md pointer | Q4 | Low — placement choice; the CLAUDE.md pointer is the real enforcement. |
| A4 | `sealed IconRef` + `data class DinghyIcon(primary, alternate)` is the right shape | Q6 | Medium — design recommendation; Codex-review the final shape (per [[codex-review-final-plans]]). |
| A5 | detekt + a Compose ruleset (baseline-scoped) is the lint gate | Q7 | Medium — verify Kotlin 2.1.21 / AGP 8.7 compatibility of the chosen ruleset before adding; pseudolocale-only is the fallback. |
| A6 | A detekt baseline correctly scopes the gate off the ~240 deferred literals | Q7 | Low — baseline is detekt's standard "accept existing, fail new" mechanism. |
| A7 | `ThemeResolver.bake(tuple)` is the cleanest 6-combo seed source for previews | Pattern 1 | Low — verified the pure-bake fn exists (ThemeResolver.kt:162); resolver-construct also works. |

## Open Questions

1. **Exact theme-seeding seam for the 6 combos (resolver-construct vs `bake`+`flowOf`).**
   - Known: both reuse the ONE `DinghyTheme` boundary; `ThemeResolver.bake(ThemeTuple)` exists and is pure.
   - Unclear: which is least friction for authoring 6 seeds (need 6 `ThemeTuple`s with the right `seedHex`/`paletteMode`/`dark`/`fs`).
   - Recommendation: planner picks during Wave 0; build `SampleFixtures.themeTuples` (6 + one `fs=L`) and a `PreviewBox(tuple)` that bakes-and-flows. Verify a preview actually renders all 6 distinctly before declaring SC-1.

2. **Whether to mint formal PREV-*/I18N-* REQ IDs.**
   - Known: REQUIREMENTS.md has none; ROADMAP defers to discuss; discuss produced D-01..D-08.
   - Recommendation: planner derives coverage from the 5 SCs; optionally add IDs so verify-work has anchors.

3. **detekt adoption vs pseudolocale-only for the lint gate.**
   - Known: `HardcodedText` is XML-only; detekt Compose rulesets exist but aren't wired.
   - Recommendation: adopt detekt with a baseline (it IS the SC-3 deliverable); fall back to pseudolocale-visual-only if compat/effort is judged too high — but record that as a scope reduction.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Compose preview tooling (ui-tooling + -preview) | the whole harness | ✓ | BOM 2026.05 → 1.11.1 | — |
| Android Studio (Windows-side, opening the repo) | rendering previews / Live Edit | ✓ (owner setup; per staging doc one-time) | — | builds still work via `gw.bat`; rendering needs Studio |
| `LocalInspectionMode`, string resources, `isPseudoLocalesEnabled` | Q5/Q7 | ✓ (platform) | — | — |
| Coil 3 | PrintStatus thumb inspection branch | ✓ | 3.1.0 | — |
| detekt + Compose ruleset | Q7 lint gate (optional) | ✗ (not wired) | — | pseudolocale-visual-only gate |
| flox (Nexus 7 / LineageOS 18.1 API 30) + live printer | `start_dest` / View-surface live truth (D-06) | ✓ (per project history; owner-driven) | API 30 | — (no host substitute — that's the point) |

**Missing with no fallback:** none blocking. **Missing with fallback:** detekt (→ pseudolocale-only); note this is a scope reduction of SC-3 if taken.

## Validation Architecture

> Nyquist validation is ENABLED (`.planning/config.json` workflow.nyquist_validation: true). This phase is unusual: its primary deliverable (`@Preview` rendering) is **host-rendered (Layoutlib) and CANNOT be asserted on-device in CI** — and per the hard boundary, must NOT be used to claim anything about device perf. The validation strategy splits into "what CI can prove" vs "what only flox proves" vs "what is a human-eyeball check in Studio."

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 (JVM unit: `app/src/test`) + AndroidX instrumented (`app/src/androidTest`, Compose UI test). Both already present. `[VERIFIED]` |
| Config file | none dedicated; standard AGP test wiring. (If detekt added → `config/detekt/detekt.yml` + baseline.) |
| Quick run command | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` (JVM tests) |
| Full suite command | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest :app:lintDebug --no-daemon"` (+ `:app:connectedDebugAndroidTest` on flox for instrumented) |

### Phase Requirements → Test Map
| SC | Behavior | Test Type | Automated Command | What CAN / CANNOT be asserted |
|----|----------|-----------|-------------------|-------------|
| SC-1 | Exemplars render under 6 combos + fs=L, no live Moonraker | **Compile + human-eyeball in Studio** | `:app:compileDebugKotlin` proves the `@Preview` fns + fixtures COMPILE; rendering correctness is a **Studio human check** (host) | CI can prove compile + no Moonraker import in fixtures; CANNOT prove visual correctness in CI (needs Studio render or the deferred screenshot net). |
| SC-2 | Fixture module + provider + Nexus-7 profile + written convention exist | **unit + existence** | a JVM unit test asserting `SampleFixtures` exposes all 4 `PrintStatusMode`s + non-empty spool/temp fixtures; doc-file existence check | Fully CI-assertable (the data + provider are pure Kotlin). |
| SC-3a | strings.xml + key convention + format-args/plurals exist, exemplars use `stringResource` | **lint + grep** | `:app:lintDebug` (MissingTranslation off; the gate); grep exemplars for `stringResource(R.string.` | CI-assertable. |
| SC-3b | icon registry exists (primary+alternate, font-or-drawable), exemplars use `DinghyIconView` | **unit** | JVM test: every `DinghyIcons` entry has a non-blank `alternate`; `IconRef` resolves; exemplars reference tokens not raw ligatures (grep) | CI-assertable. |
| SC-3c | `en-XA` pseudolocale + hardcoded-literal gate wired | **build-config + lint/detekt** | assert `isPseudoLocalesEnabled` in debug; detekt baseline runs green and a *new* literal in a test fixture is flagged (negative test) | CI-assertable; pseudolocale visual sweep is on-device/emulator human check. |
| SC-4a | build wiring correct (`-preview` impl, `-tooling` debugImpl) | **build-graph assert** | `:app:dependencies` / a check that `-tooling` is debug-only | CI-assertable. |
| SC-4b | `start_dest` jumps screens, debug-gated, inert in release | **instrumented (flox) + unit** | unit: the extra→Dest mapping is pure-testable; instrumented on flox: `am start ... --es start_dest FineTune` lands on FineTune when Ready; release: verify gated off | mapping CI-assertable; the actual jump + gate behavior is a **flox** check (live nav + readiness). |
| SC-5 | backfill deferred + recorded; no regression | **full existing suite green** | `:app:testDebugUnitTest` + existing instrumented suite stays green; ROADMAP/deferred records present | regression CI-assertable (no existing test breaks); on-device no-regression is the standard flox gate. |

### Sampling Rate
- **Per task commit:** `:app:compileDebugKotlin` (proves previews/fixtures/registry compile) + relevant JVM unit test.
- **Per wave merge:** `:app:testDebugUnitTest :app:lintDebug` (+ detekt if added).
- **Phase gate:** full JVM suite + lint green in CI; **flox** instrumented run for `start_dest` (SC-4b) + a Studio human-render pass over the 3 exemplars across the 6 combos + `fs=L` (SC-1) + an `en-XA` pseudolocale visual sweep on the 3 exemplars (SC-3c). The Studio render + pseudolocale sweep are explicitly **host/human checks, not CI gates** — and prove layout/i18n, NOT perf.

### Wave 0 Gaps
- [ ] `app/src/test/.../SampleFixturesTest.kt` — asserts fixture completeness (SC-2). MUST compile day-one with typed assertions, not `fail()` stubs referencing unbuilt symbols ([[dinghy-wave0-red-scaffold-compile.md]]).
- [ ] `app/src/test/.../DinghyIconsTest.kt` — asserts every token has a non-blank `alternate` + resolvable `IconRef` (SC-3b).
- [ ] `app/src/test/.../StartDestMappingTest.kt` — pure extra-string→`Dest` mapping (SC-4b unit half).
- [ ] (if detekt) `config/detekt/detekt.yml` + `detekt-baseline.xml` capturing the existing ~240 literals.
- [ ] Instrumented `start_dest` test belongs on flox, not CI — flag `autonomous:false` for that task.

## Security Domain

`security_enforcement` is not set to `false` in config (so nominally enabled), but this phase has **no auth, session, access-control, crypto, or external-input surface**: it adds dev tooling, fixtures, string/icon tokens, and a debug-gated nav hook.

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | **yes (narrow)** | The `start_dest` hook MUST be debug-gated via `devCyclerEnabled` (default FALSE) — absent/inert in the sideloaded release APK, NOT `BuildConfig.DEBUG`. Mirrors the existing dev-enable pattern. An exported activity reading an intent extra is the only new attack surface; gating it off in release closes it. |
| V5 Input Validation | **yes (narrow)** | `start_dest` extra is untrusted input: map it through a safe `Dest.valueOf`-with-fallback (unknown value → ignore/default, never crash). |
| V6 Cryptography | no | — |

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malicious `am start --es start_dest <garbage>` | Tampering / DoS | Safe enum parse with default; dev-gate means it's inert in release anyway. |
| Exported `MainActivity` honoring a deep-jump in release | Elevation (minor) | `devCyclerEnabled` gate (default FALSE) — the hook is a no-op in release. |

## Sources

### Primary (HIGH confidence)
- **Codebase (verified this session):** `app/build.gradle.kts:95-139`, `gradle/libs.versions.toml`, `DinghyTheme.kt`, `ThemeResolver.kt:162`, `MaterialSymbol.kt`, `MainActivity.kt`, `RootController.kt:88-143`, `ShellNavState.kt`, `PrintStatusMode.kt`, `BenchActivity.kt`, `GraphViewHost.kt`, `PrintStatusScreen.kt:54,515-523`, `SpoolScreen.kt:30,379`, `ThemePrefs.kt:117,151-175,179`, `ThemeTokens.kt:137,199`, `di/AppContainer.kt:118,242,455`. Grep counts: 55 `MaterialSymbol(` call sites, 7 `ic_*` drawables, 35 `painterResource`, 22 `Text("…")` (≈262 total literals incl. labels/cd per CONTEXT), 0 `LocalInspectionMode`, 0 `strings.xml`, no detekt/lint config.
- `../parallel_dinghy/phase-preview-harness-staging.md` + `phase-tokenization-staging.md` — primary spec.
- `.planning/phases/18-.../18-CONTEXT.md` (D-01..D-08), `.planning/ROADMAP.md` §Phase 18 (5 SCs).
- developer.android.com/develop/ui/compose/tooling/previews — `@Preview`, `@PreviewParameter`, multipreview templates, custom device specs, `LocalInspectionMode`, ui-tooling-preview-in-main classpath requirement. HIGH.

### Secondary (MEDIUM confidence)
- developer.android.com/studio/preview/compose-screenshot-testing — AGP ≥8.5 supports `compose-preview-screenshot` (Phase-22 bolt-on confirmation). MEDIUM-HIGH.
- github.com/appKODE/detekt-rules-compose + mrmans0n.github.io/compose-rules — Compose-aware lint rules (HardcodedText is XML-only). MEDIUM (compat to verify at plan time).
- WebSearch (multiple) on ui-tooling-preview configuration + multipreview device specs — cross-verified with the official docs. MEDIUM, verified.

### Tertiary (LOW confidence)
- Medium/ProAndroidDev articles on preview patterns — used only to corroborate the official idioms; not relied on alone.

## Metadata

**Confidence breakdown:**
- Standard stack / build wiring: HIGH — verified directly in the catalog + build file + official docs.
- Preview/fixture architecture: HIGH — platform features verified against the codebase's existing theme boundary.
- `start_dest` gate interaction: MEDIUM-HIGH — mechanically verified against `RootController`/`ShellNavState`; the bypass-vs-land choice is a recommendation owner left open.
- Icon-registry shape: MEDIUM — sound Kotlin modeling, but a design recommendation to Codex-review.
- Lint gate (detekt): MEDIUM — approach is standard; ruleset compatibility to verify at plan time.

**Research date:** 2026-06-06
**Valid until:** ~2026-07-06 (stable pinned stack; 30 days). Re-check only if the Compose BOM / AGP line is bumped.
