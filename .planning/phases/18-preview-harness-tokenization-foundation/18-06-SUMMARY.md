---
phase: 18-preview-harness-tokenization-foundation
plan: 06
subsystem: ui + preview-harness
tags: [preview, multipreview, tokenization, strings, icons, accessibility, capability-gating, busy-lock, exemplar]

# Dependency graph
requires:
  - phase: 18-02
    provides: PreviewBox + 6 theme seeds + fsLargeSeed + Nexus7Previews + SampleFixtures.fineTune{AllPresent,NoFwRetraction,Busy}
  - phase: 18-03
    provides: DinghyIcons registry + DinghyIconView (sizeDp/a11y) + strings.xml master vocabulary + <area>_<element>/cd_* convention
  - phase: 18-05
    provides: the D-01/D-02 ANCHOR exemplar (PrintStatusPreviews) — the state-hoist + minimized-matrix template this plan copies verbatim
provides:
  - "FineTunePreviews.kt — FineTuneVariantProvider (present/absent/busy) driving the MINIMIZED matrix: variant matrix on Extrusion (one theme), 6-theme matrix on the Hub, Motion all-present, fs=L overflow, RTL spot-check — the D-01 capability-gating + busy-lock exemplar (de-risks Phase 19)"
  - "Stateless MotionScreen(vm=…) / ExtrusionScreen(vm=…) preview overloads + shared private MotionContent/ExtrusionContent — the container-free preview seam (no Moonraker), state-hoisted from the live (container, holder) overloads"
  - "Tokenized FineTune Hub/Motion/Extrusion/Tile: stringResource(R.string.finetune_*/cd_finetune_*/common_back) sites + DinghyIconView(DinghyIcons.*) glyph routing + cd_* a11y"
  - "New DinghyIcons entries: KeyboardReturn, OutputCircle, MaxVelocity, MaxAccel, MinCruise, SquareCornerVelocity, PressureAdvance, SmoothTime, Decrease, Increase, InputCircle (all unique alternates)"
  - "New strings.xml finetune_fw_retraction_label + cd_finetune_* (per-tuner names + ± nudge a11y)"
affects: [18-07, phase-19-output-controls, phase-22-tokenization-backfill]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Exemplar #2 copies the anchor: CAPABILITY variant (present/absent/busy) = @PreviewParameter (the interesting axis here, not state); theme = explicit PreviewBox(seed) wrappers; fs=L via fsLargeSeed (NOT @Preview(fontScale=)); ONE RTL spot-check"
    - "State-hoist for capability-gated screens: extract a container-free *Content(vm, enabled, failureText, onBack, markPending, dispatch*) shared by the live (container, holder) overload and a stateless (vm=) preview overload — the dispatch side-effects are passed as lambdas (no-op in preview), the gates/value-formatting live in *Content (the thing previewed)"
    - "VelocityLimitTile takes markPending/dispatch lambdas instead of the holder, so the shared tile renders identically without a FineTuneHolder under @Preview"
    - "Icon token routing: FineTuneTile/VelocityLimitTile/HubEntry take a DinghyIcon (not @DrawableRes Int); the tile delegates the glyph to DinghyIconView, which owns the a11y semantics (the cd is the sole spoken label; ± and nav glyphs decorative where the adjacent label speaks)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/FineTunePreviews.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHubScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/MotionScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/ExtrusionScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneTile.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt
    - app/src/main/java/works/mees/dinghy/ui/finetune/FwRetractionScreen.kt
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - app/src/main/res/values/strings.xml
    - app/src/androidTest/java/works/mees/dinghy/ui/finetune/FineTuneNavTest.kt

key-decisions:
  - "State-hoist (not a fake holder/container): MotionScreen/ExtrusionScreen took a heavyweight (AppContainer, FineTuneHolder) — un-previewable. The plan's key_links call the preview entry `FineTuneHubScreen(...)` / the group screens from fixtures, so the correct move is the anchor's textbook state-hoist: extract a pure MotionContent/ExtrusionContent the live overload AND a stateless (vm=) overload both call. Behaviour-neutral (the live entry resolves its dispatcher/holder flows + builds the dispatch lambdas exactly as before, now passing them into *Content)."
  - "VelocityLimitTile re-shaped to take markPending: (FineTuneTuner, Double)->Unit + dispatch: (VelocityLimitArgs)->Unit instead of the holder + a 2-arg dispatch. This is what makes the shared tile render in a stateless preview (both no-ops) — the alternative (passing a fake holder) would have meant fabricating a PrinterStateStore in the preview path. Only MotionScreen consumes VelocityLimitTile, so the blast radius was contained."
  - "Tokenization boundary (Codex MEDIUM-6) honoured: tokenized literals in the 4 named files + their directly-edited children (VelocityLimitTile in FineTuneShared.kt). FwRetractionScreen is OUT of the exemplar boundary (build-blind, Phase-17 state, NOT previewed) — but it CALLS FineTuneTile, whose signature changed (@DrawableRes -> DinghyIcon). To keep the build green I converted ONLY its `icon` arguments (a Rule-3 blocking-issue fix); its `name`/`label` literals stay RAW for the Phase-22 backfill (the detekt baseline accepts them). The build-blind FW-retraction SCREEN is deliberately not previewed; the absent-capability proof is the FW-retraction ENTRY tile hiding in ExtrusionScreen under fineTuneNoFwRetraction."
  - "The per-tuner tile `name` (Speed/Max Vel/…) is the GLYPH's contentDescription (the tile draws no text label, 17-06), so those literals became cd_finetune_* a11y keys, NOT visible-label finetune_* keys. The Hub's drawn Motion/Extrusion/Fine-Tune labels use the existing finetune_* keys; the FW-retraction entry (a real visible OutlinedControl label) got finetune_fw_retraction_label."

patterns-established:
  - "Exemplar #2 confirms the anchor template generalizes from the multi-STATE archetype (PrintStatus) to the multi-CAPABILITY archetype (FineTune) — Phase 19 (Output Controls, also capability-gated) copies THIS file's present/absent/busy provider shape."

requirements-completed: [SC-1, SC-3, D-01, D-02]

# Metrics
duration: ~35min
completed: 2026-06-06
---

# Phase 18 Plan 06: FineTune Exemplar (Capability-Gating + Busy-Lock) Summary

**Applied the 18-05 anchor's D-02 template to the FineTune surfaces — the present/absent capability-gating + whole-group busy-lock archetype that de-risks Phase 19. Added `FineTunePreviews.kt` (a `FineTuneVariantProvider` over present / FW-retraction-absent→HIDDEN / busy driving the minimized `@Preview` matrix), state-hoisted Motion/Extrusion into container-free `*Content` + stateless `(vm=)` overloads so previews render with NO live Moonraker, tokenized the Hub/Motion/Extrusion/Tile literals to `stringResource` + routed glyphs through `DinghyIconView`/`DinghyIcons`, and migrated the instrumented `FineTuneNavTest` matchers off literal text.**

## Performance
- **Duration:** ~35 min
- **Tasks:** 3 (all executed)
- **Files:** 9 (1 created, 8 modified)

## Task Commits
1. **Task 1: FineTune variant @Preview matrix + stateless Motion/Extrusion hoist** — `ff2fb43` (feat)
2. **Task 2: Tokenize FineTune Hub/Motion/Extrusion per D-02 (strings + icons + a11y)** — `d0a1055` (feat)
3. **Task 3: Migrate FineTune instrumented matchers to resource strings** — `7106603` (test)

## What Was Built

### Task 1 — Preview matrix + the state-hoist seam (`ff2fb43`)
- `preview/FineTunePreviews.kt`: `class FineTuneVariantProvider : PreviewParameterProvider<FineTuneVm>` emitting `fineTuneAllPresent` / `fineTuneNoFwRetraction` (absent→HIDDEN) / `fineTuneBusy` (groupBusy). Matrix MINIMIZED per RESEARCH Q8: `ExtrusionVariantMatrix` (full present/absent/busy on Colorful/dark, variant = `@PreviewParameter`, rendered on Extrusion because it owns the `hasFwRetraction` gate), six `FineTuneTheme*` siblings (full 6-theme matrix on the Hub via `PreviewBox` seeds), `MotionAllPresent` (the per-tile sibling surface), `FineTuneFsLargeOverflow` (fs=L Motion via `fsLargeSeed`), `FineTuneRtlSpotCheck` (RTL Extrusion). All `@Nexus7Previews` (portrait+landscape). The build-blind FW-retraction SCREEN is NOT previewed.
- State-hoist: extracted container-free `private MotionContent(...)` / `ExtrusionContent(...)` holding the capability-gated tile column + Back gutter. The live `(container, holder, onBack)` overloads resolve their dispatcher/inFlight/vm flows + build the dispatch lambdas exactly as before, then delegate to `*Content`; added stateless `MotionScreen(vm)` / `ExtrusionScreen(vm)` overloads (no-op side-effects) — the preview seam. `VelocityLimitTile` re-shaped to take `markPending`/`dispatch` lambdas (not the holder).
- Gate: `:app:compileDebugKotlin` exit 0. Grep: all three fixtures referenced.

### Task 2 — Tokenization per D-02 (`d0a1055`)
- **Strings:** routed every user-facing literal through `stringResource` — the Hub title (`finetune_title`) + Motion/Extrusion entry labels (`finetune_motion_label`/`finetune_extrusion_label`) + Back (`common_back`); the per-tuner glyph contentDescription names (`cd_finetune_speed/max_velocity/max_accel/min_cruise/scv/flow/pressure_advance/smooth_time/part_fan`); the FW-retraction entry visible label (`finetune_fw_retraction_label`); the ± nudge a11y descriptions (`cd_finetune_decrease/increase`). Added the new keys to `strings.xml`.
- **Icons:** `FineTuneTile`/`VelocityLimitTile`/`HubEntry` now take a `DinghyIcon` (not `@DrawableRes Int`) and render via `DinghyIconView`. Registered 11 new exemplar drawable glyphs in `DinghyIcons` + `DinghyIcons.all` (Speed/FanMode already existed): `KeyboardReturn`, `OutputCircle`, `MaxVelocity`, `MaxAccel`, `MinCruise`, `SquareCornerVelocity`, `PressureAdvance`, `SmoothTime`, `Decrease`, `Increase`, plus `InputCircle` for the build-blind FW screen. All alternates unique (DinghyIconsTest green).
- **FwRetractionScreen (Rule 3 — blocking):** converted its 4 `FineTuneTile(iconRes=…)` calls to `icon = DinghyIcons.*` to keep the changed-signature build green; its `name`/`label` literals stay RAW (Phase-22 backfill — out of this exemplar's boundary).
- **D-03 respected:** zero `@Stable`/`@Immutable`/`ImmutableList` added (grep = 0).
- **Layout preserved:** the label-less `[glyph][value][−][+]` tile + the Hub `[glyph]/[label]` entries are byte-for-byte the same composition, only the glyph + label SOURCES changed.
- Gate: `:app:assembleDebug` + full `:app:testDebugUnitTest` exit 0 (no regression). Grep: stringResource sites in all 4 files, DinghyIcon routing in Motion (5)/Extrusion (4), no raw `name="..."`/`label="..."` in the 4 files.

### Task 3 — Instrumented matcher migration (`7106603`)
- `FineTuneNavTest`: the `onNodeWithText("Fine-Tune"/"Motion"/"Extrusion")` matchers (which target FineTune Hub screen literals just tokenized in Task 2) now resolve via `context.getString(R.string.finetune_title/finetune_motion_label/finetune_extrusion_label)` (added `Context`/`R` imports + lazy string vals). The string VALUES are unchanged, so the assertions still pass on-device; this hardens them against a future vocabulary edit.
- Gate: `:app:compileDebugAndroidTestKotlin` exit 0 (pre-existing `createComposeRule` deprecation warning only, out of scope).

## Deviations from Plan

### [Rule 3 — Blocking] State-hoist + VelocityLimitTile re-shape for the stateless preview contract
- **Found during:** Task 1 (previews must drive the FineTune screens from `FineTuneVm` fixtures per SC-1, but Motion/Extrusion only took `(AppContainer, FineTuneHolder)`).
- **Fix:** Hoisted rendering into `MotionContent`/`ExtrusionContent` + added stateless `(vm=)` overloads (the documented anchor pattern); re-shaped `VelocityLimitTile` to take `markPending`/`dispatch` lambdas instead of the holder. Live overloads are behaviour-neutral.
- **Files:** `MotionScreen.kt`, `ExtrusionScreen.kt`, `FineTuneShared.kt`. **Commit:** `ff2fb43`.

### [Rule 3 — Blocking] FwRetractionScreen icon-arg conversion
- **Found during:** Task 2 (changing `FineTuneTile`'s `@DrawableRes iconRes: Int` → `icon: DinghyIcon` broke the build-blind FwRetractionScreen, which calls the shared tile).
- **Fix:** Converted ONLY its 4 `icon` arguments to `DinghyIcons.*` (registered `InputCircle`); left its `name`/`label` literals raw per the tokenization boundary (Phase-22).
- **Files:** `FwRetractionScreen.kt`, `DinghyIcons.kt`. **Commit:** `d0a1055`.

Everything else executed as written.

## Phase-22 Backfill Notes
- `FwRetractionScreen.kt` keeps RAW literals (`name = "Retract Len"/"Unretract Extra"/"Retract Spd"/"Unretract Spd"`, gutter `label = "Back"`) — intentionally deferred (build-blind, out of this exemplar's boundary). Phase 22 tokenizes them (`cd_finetune_retract_*` + `common_back`).
- Two FwRetraction tiles reuse `DinghyIcons.MaxAccel` (the `sprint` glyph) — acceptable (same registered token used twice; the alternate is the uniqueness key, not the call count). Phase 22 may split them if distinct glyphs are wanted.

## Known Stubs
None. The stateless `(vm=)` overloads are the intended preview seam (the live `(container, holder)` overloads render identically on-device), not stubs.

## Threat Flags
None — behaviour-neutral state-hoist + tokenization + host-rendered previews; no new trust-boundary surface (matches the plan's accepted T-18-06-01).

## Verification Summary
- `:app:compileDebugKotlin` — exit 0 (Task 1).
- `:app:assembleDebug` + full `:app:testDebugUnitTest` — exit 0 (Task 2; no regression; DinghyIconsTest alternate-uniqueness green with 11 new entries).
- `:app:compileDebugAndroidTestKotlin` — exit 0 (Task 3).
- Grep: all three fixtures in FineTunePreviews.kt; `stringResource(R.string.finetune*/cd_finetune*/common_back)` in all 4 screen files; `icon = DinghyIcons.*` in Motion (5)/Extrusion (4); `DinghyIconView(` ×2 in Hub + ×2 in Tile; `@Stable`/`@Immutable`/`ImmutableList` = 0; no duplicate DinghyIcon alternates.
- Studio render (present/absent/busy + 6 combos + fs=L + RTL; absent HIDES FW-retraction; busy shows whole-group lock) — deferred to the phase gate (human-eyeball), per `:app:lintDebug` being a known-crashing tool (neutralized; assembleDebug + tests are the authoritative gates).
- `:app:connectedDebugAndroidTest` FineTune tests — deferred to the flox tier (string values unchanged, so the migrated matchers behave identically).

## Self-Check: PASSED
- FOUND: app/src/main/java/works/mees/dinghy/preview/FineTunePreviews.kt
- FOUND: app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHubScreen.kt (tokenized)
- FOUND: app/src/main/java/works/mees/dinghy/ui/finetune/MotionScreen.kt (hoisted + tokenized)
- FOUND: app/src/main/java/works/mees/dinghy/ui/finetune/ExtrusionScreen.kt (hoisted + tokenized)
- FOUND: app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneTile.kt (DinghyIconView)
- FOUND: app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt (new entries)
- FOUND: app/src/main/res/values/strings.xml (new keys)
- FOUND commit: ff2fb43 (Task 1)
- FOUND commit: d0a1055 (Task 2)
- FOUND commit: 7106603 (Task 3)

---
*Phase: 18-preview-harness-tokenization-foundation*
*Completed: 2026-06-06*
