---
phase: 03-design-system-theming-foundation
verified: 2026-05-31T00:00:00Z
status: passed
uat_closed: 2026-05-31 — on-device UAT (03-HUMAN-UAT.md) run on real flox; 4/6 passed, 2 issues → all 4 gaps (G-1..G-4) fixed in plan 03-08 and re-verified on flox. WR-01 ScrubberPage tap-to-set now genuinely resolved (single awaitEachGesture). Sole remaining design-system carry: none blocking.
score: 5/5 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Rotate device in gallery; compare portrait (stacked) and landscape (Focus|Field 50/50 + full-width gutter on same column lines) against docs/ui_design/images/*.png; verify gutter column-line alignment and sacred-square content (ring) renders as a true circle"
    expected: "Portrait: focus/field/gutter stack ~40/40/20. Landscape: Focus and Field side-by-side 50/50; gutter full-width on the same grid; ring content is a circle centered in its cell, not distorted. No hardcoded pixel gaps or mis-aligned column breaks."
    why_human: "Visual grid-line fidelity vs hifi.css reference screens cannot be asserted host-side; ScreenScaffold uses weight/BoxWithConstraints which is evaluated only at runtime on real hardware."
  - test: "In the gallery, toggle Dark / Light / Custom theme; verify the Compose surfaces AND the Views Canvas graph both recolor (both toolkits, no stale colors remaining)"
    expected: "Every Compose surface (ring, controls, scaffolds) and the Views GraphView all repaint in the new palette simultaneously. No surface retains the previous palette after the toggle."
    why_human: "Cross-toolkit visual repaint (Compose + Views) requires live rendering; host tests cannot observe pixel output."
  - test: "Set device fontScale to maximum (Accessibility > Font size); toggle S / M / L in the gallery; verify only the in-app --fs setting governs type size (no double-application)"
    expected: "Type scales with the in-app S/M/L setting only. A 'Large' OS font size combined with the S app setting produces the same text size as S alone — not an additive product."
    why_human: "The LocalDensity(fontScale=1f) override needs an OS-level fontScale interaction to exercise — requires a real device with the accessibility slider."
  - test: "In the gallery, use the ScrubberPage for a numeric value (e.g. temperature target); drag the fill bar and tap the +/- steppers; verify no keyboard appears and that the value updates correctly with both gestures"
    expected: "No OS alphanumeric keyboard appears at any point. Drag sets value proportionally to bar width. Steppers increment/decrement by step. Apply calls onApply with the working value; Cancel dismisses without changing the caller's state. WR-01: test for the dual-pointerInput tap/drag race — a short tap (as opposed to a drag) should reliably set the value from the tap position."
    why_human: "Keyboard suppression and touch gesture reliability (including the WR-01 tap-vs-drag race) require physical interaction on the device; host tests have no touch dispatch."
  - test: "In the gallery, trigger the ConfirmGuard for both destructive=true (red) and destructive=false (green) variants; verify correct tint, button colors, and that Confirm and Cancel both fire their callbacks reliably"
    expected: "Destructive guard: full-screen stop-soft tint, confirm button is red (Danger), cancel is neutral. Positive guard: go-soft tint, confirm button is green (Go), cancel is neutral. Both callbacks fire reliably on tap."
    why_human: "Appearance (tint + intent colors) and interaction correctness require on-device rendering and touch."
  - test: "In the gallery, view the SeverityToast for all four severities (Info/Success/Warning/Error); verify color + distinct icon glyph + text are all present for each"
    expected: "Each severity shows a distinct badge glyph (i / checkmark / ! / ×) in the severity color, a text message, and a colored border. No two severities share the same glyph. Color is reinforced by, not a substitute for, the icon."
    why_human: "Visual rendering of glyphs and color requires on-device display."
---

# Phase 3: Design System & Theming Foundation — Verification Report

**Phase Goal:** Build the reusable visual + interaction substrate every later screen inherits — semantic-token theme system (dark + light + user-custom), S/M/L text-size, Focus/Field/Gutter responsive grammar, outline-led touch-first controls, and the core reusable primitives (Confirm guard, scrubber/stepper page, severity toast, progress-ring + line-graph render primitives). Geist/Geist Mono bundled. Wired to the Phase-2 spine. Nexus 7 (Adreno 320) perf floor: static glow allowed, no continuous/looping animation.

**Verified:** 2026-05-31T00:00:00Z
**Status:** human_needed (automated substrate fully verified; 6 manual-only items require on-device gallery sign-off per 03-VALIDATION.md)
**Re-verification:** No — initial verification.

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Semantic-token theme system: dark + light + user-custom, every component referencing role tokens (never raw color), S/M/L text-size scales app-wide and persists | VERIFIED (automated substrate); human_needed for OS-fontScale double-apply check | `ThemeTokens.kt` — `@Immutable data class ThemeTokens` with 30 role fields; `BakedTokens.kt` — `TokensDark`/`TokensLight` with sRGB `Color(0x…)` literals; `ThemeResolver.kt` — `StateFlow<ThemeTokens>` with `setBase/setDeltas/setFs`; `ThemePrefs.kt` — DataStore persistence with deterministic fail-safe `sanitize()`; `DinghyTheme.kt` — `fontScale=1f` override; zero raw `Color(0x…)` literals outside `BakedTokens.kt` across all of `designsystem/`, `render/`, and `compose/`; all 5 Wave-0 theme unit tests present and substantive |
| 2 | Focus/Field/Gutter grammar renders in BOTH portrait and landscape; sacred aspect ratios; ratio-only sizing | VERIFIED (code); human_needed for visual fidelity on device | `ScreenScaffold.kt` — `BoxWithConstraints` branching `maxWidth > maxHeight`; portrait = Column stack with `weight`; landscape = Row stage + full-width gutter Box; zero hardcoded px for regions (only `64.dp` touch floor and `--fs` text step permitted as fixed values per plan); sacred-square contract in code: caller wraps content in `Modifier.aspectRatio(1f)`, region is never made square |
| 3 | Outline-led control language + button-intent colors (red/green/amber/accent/white) as reusable components with ≥64dp touch targets | VERIFIED (code); human_needed for touch feel | `OutlinedControl.kt` — `Intent` enum (Neutral/Accent/Warn/Danger/Go) mapping to `t.outline`/`t.accentLine`/`t.heat`/`t.stop`/`t.go`; `heightIn(min = 64.dp)` in code; `BorderStroke(2.dp, intent.outlineColor(t))`; reads all colors via `LocalTokens.current` (no raw hex); no `rememberInfiniteTransition` or `animate*AsState` (D-13 holds) |
| 4 | Reusable primitives panel-consumable: full-screen Confirm guard (PRIM-03), single-setting scrubber/stepper page (PRIM-01, keyboard-free), severity toast (PRIM-04) | VERIFIED (code); human_needed for interaction on device | `ConfirmGuard.kt` — full-screen, gutter omitted, `Intent.Danger`/`Intent.Go`/`Intent.Neutral` intents correct, reads `LocalTokens.current`; `ScrubberPage.kt` — no `TextField`/`BasicTextField`/`KeyboardType` (grep confirms), fill-bar + stepper, `destructiveDismiss=false` default; `SeverityToast.kt` — `Severity` enum → 4 distinct tokens, per-severity badge glyph + text always rendered (never color-alone); `ThemeableView.kt` — `interface ThemeableView { fun applyTokens(t: ThemeTokens) }` present |
| 5 | progress-ring + line-graph render primitives draw a bounded ring buffer at throttled ~2–4 Hz WITHOUT jank on the Nexus 7 (static glow only, no continuous animation) — human-ratified PASS under the A-variant two-part gate | VERIFIED (evidence exists; human-ratified verdict accepted per verification context) | `RenderBenchScene.kt` references `RingBuffer`, `SyntheticFeed`, `ProgressRing`, `GraphView`; `RenderBenchmark.kt` declares `MacrobenchmarkRule @Test renderScene()` with gfxinfo-is-SoR header; `03-PERF-RESULTS.md` — Round-2 filled capture on real `flox`: p95 = 50.1 ms vs derived ~66 ms bound; 0 frozen frames (>700 ms); ~3.2 Hz cadence (no animation loop); FINAL VERDICT = PASS (Matthew's sign-off, 2026-05-31); Phase-6 re-validation mandate documented in PERF-RESULTS.md and ADR 0001 |

**Score:** 5/5 truths verified (automated substrate + human-ratified perf verdict)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt` | `@Immutable data class ThemeTokens` + `ThemeBase` enum + `fsSp` helper | VERIFIED | Present; 30 role fields, `ThemeBase.Dark/Light`, `FontScale(S/M/L)`, `fsSp(baseSp, fs)` top-level helper |
| `app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt` | `TokensDark` + `TokensLight` as sRGB `Color(0x…)` literals | VERIFIED | Present; gamut-mapping policy comment; ≥8 externally-verified hex cross-checks; only file with raw sRGB literals in the codebase |
| `app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt` | `val tokens: StateFlow<ThemeTokens>` + setBase/setDeltas/setFs mutators | VERIFIED | Present; `MutableStateFlow` exposed as `asStateFlow()`; `resolve()` pure function picks baked base, applies deltas, stamps fs |
| `app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt` | DataStore persistence + deterministic fail-safe `sanitize()` | VERIFIED | Present; `dataStore.data.catch { IOException -> emptyPreferences() }.map { sanitize(...) }`; `sanitize()` is a pure function covering all four D-02 fail-safe cases |
| `tools/oklch-bake/bake_tokens.py` | One-time oklch→sRGB conversion script (traceability) | VERIFIED | Present; committed script whose output is byte-identical to `BakedTokens.kt` (per code review) |
| `app/src/main/java/works/mees/dinghy/theme/Geist.kt` | `val Geist: FontFamily` + `val GeistMono: FontFamily` over static TTFs | VERIFIED | Present; 4 Geist weights + 2 GeistMono weights via `R.font.geist_*`; no variable font; no `fontFeatureSettings` |
| `app/src/main/res/font/geist_regular.ttf` (+ 5 others) | 6 static-weight TTFs bundled | VERIFIED | All 6 files present: `geist_regular/medium/semibold/bold.ttf` + `geist_mono_medium/semibold.ttf` |
| `app/src/main/java/works/mees/dinghy/theme/compose/LocalTokens.kt` | `staticCompositionLocalOf<ThemeTokens>` token boundary | VERIFIED | Present; `staticCompositionLocalOf` (not `compositionLocalOf`); default throws with clear message |
| `app/src/main/java/works/mees/dinghy/theme/compose/DinghyTheme.kt` | `collectAsStateWithLifecycle()` + `fontScale=1f` override | VERIFIED | Present; collects `resolver.tokens.collectAsStateWithLifecycle()`; `CompositionLocalProvider(LocalTokens provides tokens, LocalDensity provides Density(..., fontScale = 1f))` |
| `app/src/main/java/works/mees/dinghy/designsystem/layout/ScreenScaffold.kt` | Focus/Field/Gutter responsive layout (UI-01) | VERIFIED | Present; `BoxWithConstraints` portrait/landscape branch; weight-only sizing; no hardcoded px regions |
| `app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt` | Outline-led control, Intent enum, ≥64dp touch floor (UI-02) | VERIFIED | Present; `heightIn(min = 64.dp)`; `2.dp` border; Intent → token mapping via `LocalTokens.current` |
| `app/src/main/java/works/mees/dinghy/designsystem/ConfirmGuard.kt` | Full-screen Confirm guard (PRIM-03) | VERIFIED | Present; gutter omitted; `Intent.Danger`/`Intent.Go` for confirm; `Intent.Neutral` for cancel |
| `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt` | Single-setting scrubber/stepper, keyboard-free (PRIM-01) | VERIFIED | Present; no `TextField`/`BasicTextField`/`KeyboardType`; `destructiveDismiss=false` default (neutral cancel); WR-01 dual-pointerInput warning noted |
| `app/src/main/java/works/mees/dinghy/designsystem/SeverityToast.kt` | Severity toast, color + icon + text (PRIM-04) | VERIFIED | Present; `Severity` enum with 4 distinct badge glyphs (i/✓/!/×) + text; colors from `LocalTokens.current` |
| `app/src/main/java/works/mees/dinghy/theme/views/ThemeableView.kt` | Views push-tokens interface (D-06) | VERIFIED | Present; `interface ThemeableView { fun applyTokens(t: ThemeTokens) }` |
| `app/src/main/java/works/mees/dinghy/render/ProgressRing.kt` | Compose Canvas progress ring (D-11, no animation) | VERIFIED | Present; `Canvas(modifier.aspectRatio(1f))`; NaN coercion; no `animate*AsState`/`rememberInfiniteTransition`; reads `LocalTokens.current` |
| `app/src/main/java/works/mees/dinghy/render/GraphView.kt` | Views custom-Canvas graph implementing ThemeableView (D-11/D-06) | VERIFIED | Present; one reused `Path` with `rewind()`; pre-allocated paints; `applyTokens` pushes token colors; `setData` calls `sanitize()`; full input-edge contract (empty/1-point/constant/NaN/Inf/cap) |
| `app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt` | AndroidView host (D-06) | VERIFIED | Present; `AndroidView(factory, update = { applyTokens + setData })`; no recreation on theme change |
| `app/src/main/java/works/mees/dinghy/render/RingBuffer.kt` | Bounded rolling buffer (D-12) | VERIFIED | Present; `@Synchronized push/snapshot`; `capacity=120` default; defensive-copy `snapshot()` |
| `app/src/main/java/works/mees/dinghy/bench/RenderBenchScene.kt` | Ring+graph perf scene driven by SyntheticFeed (criterion #5) | VERIFIED | Present; references `RingBuffer`, `SyntheticFeed`, `ProgressRing`, `GraphView`; layout matches canonical Focus/Field/Gutter proportions |
| `macrobenchmark/src/main/java/works/mees/dinghy/macrobenchmark/RenderBenchmark.kt` | UiAutomator driver for the render scene | VERIFIED | Present; `MacrobenchmarkRule @Test renderScene()`; gfxinfo-is-SoR header; cold-launch with `--es scene render` |
| `.planning/phases/03-design-system-theming-foundation/03-PERF-RESULTS.md` | Captured gfxinfo p50/p90/p95 + frozen-frame count on flox | VERIFIED | Present; two independent captures (Round 1 + Round 2 A-B); FINAL VERDICT = PASS; Phase-6 re-validation mandate documented |
| `app/src/test/java/works/mees/dinghy/theme/BakedTokenTableTest.kt` | Drift guard for baked sRGB table | VERIFIED | Present; asserts ≥8 token hex values for dark+light; alpha-bearing token alpha checks |
| `app/src/test/java/works/mees/dinghy/theme/ThemeResolverTest.kt` | Resolver StateFlow + empty-delta invariant | VERIFIED | Present; empty-delta === base; single-delta changes one token only; setBase/setDeltas/setFs each re-emit |
| `app/src/test/java/works/mees/dinghy/theme/TokenDeltaSerializationTest.kt` | Delta round-trip serialization | VERIFIED (file present) | Present |
| `app/src/test/java/works/mees/dinghy/theme/FontScaleTest.kt` | fsSp math + S/M/L round-trip | VERIFIED (file present) | Present |
| `app/src/test/java/works/mees/dinghy/theme/ThemePrefsFallbackTest.kt` | Four D-02 fail-safe cases | VERIFIED | Present; covers all 4 cases (invalid base/fs/partial-delta/garbage-ARGB) + fully-corrupt blob; host-pure (no Robolectric) |
| `app/src/test/java/works/mees/dinghy/render/RingBufferHolderTest.kt` | Capacity, eviction, snapshot stability | VERIFIED | Present; asserts capacity bound, oldest-eviction at pos 81 of 200, defensive-copy stability, concurrent safety |
| `app/src/test/java/works/mees/dinghy/render/GraphDownsampleTest.kt` | Downsample cap + NaN/Inf filter + edge cases | VERIFIED | Present; pixel-cap enforcement, NaN/Inf removal, empty→empty, constant series |
| `app/src/debug/java/works/mees/dinghy/gallery/GalleryActivity.kt` | Debug-only gallery launcher | VERIFIED | Present in `src/debug/` source set only; `check-release-no-gallery.sh` tool also present |
| `app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt` | Token × component × theme × fs preview matrix | VERIFIED (file present) | Present in main source set; driven via debug GalleryActivity only |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ThemeResolver` | `BakedTokens` | `resolve(base, deltas, fs)` reads `TokensDark`/`TokensLight` | WIRED | `ThemeResolver.kt` line 55-58: `when (base) { ThemeBase.Dark -> TokensDark; ThemeBase.Light -> TokensLight }` |
| `ThemePrefs` | `ThemeResolver` | persisted base/deltas/fs feed the resolver via `apply()` | WIRED | `ThemePrefs.sanitize` returns `Resolved`; `ThemeResolver.apply()` takes the triple; pattern present in `GalleryActivity.kt` |
| `DinghyTheme` | `ThemeResolver` | `resolver.tokens.collectAsStateWithLifecycle()` | WIRED | `DinghyTheme.kt` line 39: `val tokens by resolver.tokens.collectAsStateWithLifecycle()` |
| `OutlinedControl` | `LocalTokens` | reads `t.outline`/`t.accentLine`/`t.heat`/`t.stop`/`t.go` by intent | WIRED | `OutlinedControl.kt` line 74: `val t = LocalTokens.current`; `intent.outlineColor(t)` delegates to the token map |
| `GraphViewHost (AndroidView)` | `GraphView.applyTokens + setData` | `update = { view.applyTokens(tokens); view.setData(snapshot) }` | WIRED | `GraphViewHost.kt` lines 36-39: both calls present in `update` block |
| `GraphView` | `RingBuffer.snapshot()` | draws the bounded buffer's `FloatArray` snapshot | WIRED | `GraphView.setData(snapshot: FloatArray)` calls `sanitize(snapshot, cap)` then `invalidate()`; callers supply the ring buffer snapshot |
| `ConfirmGuard/ScrubberPage/SeverityToast` | `LocalTokens` | intent colors + shapes read from `LocalTokens.current` | WIRED | All three read `val t = LocalTokens.current` and use only `t.*` tokens |
| `ConfirmGuard/ScrubberPage` | `ScreenScaffold + OutlinedControl` | built on scaffold using intent controls | WIRED | Both import and call `ScreenScaffold(...)` and `OutlinedControl(...)` |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| `ProgressRing` | `progress: Float` | caller supplies from throttled StateFlow | Yes — parameter-driven; no internal state | FLOWING |
| `GraphViewHost` | `tokens: ThemeTokens`, `snapshot: FloatArray` | caller collects `resolver.tokens` + `ringBuffer.snapshot()` | Yes — both passed as parameters from the caller's flow collection | FLOWING |
| `GraphView` | `data: FloatArray` | `setData(snapshot)` → `sanitize()` | Yes — transforms the ring-buffer snapshot into the bounded working copy drawn in `onDraw` | FLOWING |
| `RenderBenchScene` | progress + snapshot | `SyntheticFeed` driving a `RingBuffer` at ~3 Hz | Yes — deterministic synthetic feed, verified by gfxinfo cadence capture (3.2 Hz) | FLOWING |

---

### Behavioral Spot-Checks

Step 7b SKIPPED for the UI/render-primitive artifacts — the design system components require rendering on a physical device (Adreno 320 GPU) for meaningful behavioral verification. The host-runnable unit tests (Wave-0 suite) cover all host-testable behaviors.

---

### Probe Execution

No `scripts/*/tests/probe-*.sh` convention applies to this phase. The equivalent is the on-device gfxinfo capture in 03-PERF-RESULTS.md, which was run by the human per the non-autonomous 03-07 task contract. The verdict is recorded there and accepted per the verification context.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| THEME-01 | 03-01, 03-03, 03-05 | Semantic-token theme system; components reference role tokens, never raw color | SATISFIED | `BakedTokens.kt` + `ThemeResolver` + `LocalTokens` + zero raw `Color(0x…)` in `designsystem/` + `render/` |
| THEME-02 | 03-01, 03-02, 03-03 | S/M/L `--fs` multiplier persisted; Geist/GeistMono type substrate | SATISFIED | `FontScale` enum in `ThemeTokens.kt`; `ThemePrefs.setFs` persists via DataStore; `DinghyTheme` pins `fontScale=1f`; `Geist.kt` + 6 TTFs |
| UI-01 | 03-03, 03-07 | Focus/Field/Gutter responsive grammar; portrait + landscape; ratio-only sizing | SATISFIED (substrate); NEEDS HUMAN (visual) | `ScreenScaffold.kt` uses `BoxWithConstraints` + `weight`/`fillMax` only; visual fidelity is manual-only per 03-VALIDATION.md |
| UI-02 | 03-03 | Outline-led controls; ≥64dp touch targets; button-intent colors | SATISFIED (substrate); NEEDS HUMAN (touch) | `OutlinedControl.kt` — `heightIn(min=64.dp)`, `2.dp` border, `Intent` → token mapping |
| PRIM-01 | 03-04 | Keyboard-free single-setting scrubber/stepper page | SATISFIED (substrate); NEEDS HUMAN (gesture) | `ScrubberPage.kt` — no `TextField`/`BasicTextField`/`KeyboardType`; fill-bar + stepper; neutral dismiss default |
| PRIM-03 | 03-04 | Full-screen Confirm guard (destructive=red, positive=green, safe-dismiss=neutral) | SATISFIED (substrate); NEEDS HUMAN (appearance) | `ConfirmGuard.kt` — gutter omitted; `Intent.Danger`/`Intent.Go`/`Intent.Neutral` intent mapping |
| PRIM-04 | 03-04 | Severity toast (color + icon + text, never color alone) | SATISFIED (substrate); NEEDS HUMAN (appearance) | `SeverityToast.kt` — `Severity` enum with 4 distinct badge glyphs + text; colors from `LocalTokens.current` |

All 7 phase-3 requirements (THEME-01, THEME-02, UI-01, UI-02, PRIM-01, PRIM-03, PRIM-04) are accounted for. No orphaned requirements.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `designsystem/ScrubberPage.kt` | 127–132 | Dual `pointerInput` blocks on the same `Box` (WR-01 from code review) | Warning | The tap detector and drag detector are separate `pointerInput` coroutines competing for the same pointer stream — can cause a short press to be claimed by the drag detector, making tap unreliable. The keyboard-free scrubber is the single numeric-entry primitive every later setpoint screen inherits. Fix: use a single `pointerInput` with `detectDragGestures(onDragStart = { offset -> setFromX(offset.x) }) { change, _ -> setFromX(change.position.x) }` — a tap becomes a zero-length drag, eliminating the second detector. Confirmed: code review WR-01 calls this out explicitly. |
| `gallery/GalleryActivity.kt` | 51–58 | ThemeResolver and PrinterStateStore constructed in `onCreate` (WR-03 from code review) | Warning (debug-only) | On rotation the Activity recreates, a new resolver/store are built, and any gallery-selected theme state resets — undermining the orientation sign-off surface. Debug-only; does not affect release. |
| `bench/RenderBenchScene.kt` | 122–128 | `RenderSceneState.equals` compares `progress` with `==` — NaN-unsafe (WR-04 from code review) | Warning (bench-only) | `NaN == NaN` is false in Kotlin; if `progress` ever became NaN, every emission would force a recompose, defeating the custom `equals`/`hashCode` stability contract. Latent (synthetic feed never emits NaN today). Fix: use `progress.toBits() == other.progress.toBits()`. |
| `main/AndroidManifest.xml` | 24–25 | `usesCleartextTraffic="true"` app-wide (WR-02 from code review) | Warning | Enables cleartext for ALL destinations on API 23, not just the LAN Moonraker host. On API 24+ the NSC scopes it; on API 23 it is unbounded. Phase-1 legacy. Not a Phase-3 defect but carried forward. |

No `TBD`, `FIXME`, or `XXX` markers found in any file modified by this phase.

---

### Human Verification Required

#### 1. Portrait and Landscape Visual Grid Alignment

**Test:** In the gallery on real `flox`, rotate between portrait and landscape; compare against `docs/ui_design/images/*.png` reference screens. Verify gutter column-line alignment (middle button center should land on the Focus/Field divide in landscape).

**Expected:** Portrait stacks focus/field/gutter at approximately the 40/40/20 rhythm. Landscape shows Focus and Field side-by-side at 50/50 with the gutter full-width below on the same column grid. No pixel drift off the divide.

**Why human:** Visual fidelity vs hifi.css reference screens cannot be asserted host-side; ScreenScaffold uses `BoxWithConstraints` + `weight`, which is only evaluated at runtime on real hardware.

#### 2. Cross-Toolkit Theme Recolor

**Test:** In the gallery, toggle Dark / Light / Custom theme; observe both Compose surfaces and the Views Canvas graph.

**Expected:** Every surface (ring, controls, scaffolds) and the Views `GraphView` all repaint simultaneously in the new palette. No surface retains stale colors.

**Why human:** Cross-toolkit visual repaint (Compose + Views Canvas) requires live rendering; host tests cannot observe pixel output.

#### 3. `--fs` as Sole Text-Size Authority (no OS double-apply)

**Test:** Set device font size to maximum via Accessibility settings; toggle S / M / L in the gallery.

**Expected:** Type scales only with the in-app setting. OS large font + app S setting = same as app S setting alone (no additive product).

**Why human:** The `LocalDensity(fontScale=1f)` override needs an OS-level `fontScale` interaction to exercise — requires a real device with the accessibility slider.

#### 4. ScrubberPage — Keyboard-Free and WR-01 Gesture Reliability

**Test:** Use the ScrubberPage for a numeric value; drag the fill bar and tap the +/- steppers; also attempt a short tap directly on the fill bar. Confirm no OS keyboard appears at any point.

**Expected:** No keyboard. Drag sets value proportionally. Steppers work. A short tap (zero-length drag) reliably sets the value from the tap position — the WR-01 dual-`pointerInput` race must not cause tap-to-value misses.

**Why human:** Keyboard suppression and touch gesture reliability (including the tap/drag race) require physical interaction; host tests have no touch dispatch.

#### 5. ConfirmGuard — Appearance and Callback Reliability

**Test:** In the gallery, trigger the Confirm guard for both `destructive=true` (red) and `destructive=false` (green); tap both Confirm and Cancel.

**Expected:** Destructive: stop-soft tint, red confirm button, neutral cancel. Positive: go-soft tint, green confirm button, neutral cancel. Both callbacks fire reliably on tap.

**Why human:** Appearance and interaction correctness require on-device rendering and touch.

#### 6. SeverityToast — All Four Severities

**Test:** In the gallery, view the SeverityToast for Info/Success/Warning/Error.

**Expected:** Each shows a distinct badge glyph (i / ✓ / ! / ×), a colored border, a soft tint fill, and a text message. No two severities share the same glyph. Color is reinforced by the icon, not a substitute for it.

**Why human:** Visual rendering of glyphs and color requires on-device display.

---

### Gaps Summary

No automatable gaps found. All five success criteria have verifiable substrate evidence in the codebase:

1. **Criterion 1 (THEME-01/02):** Fully verified. Baked token table, resolver StateFlow, DataStore persistence with fail-safe, fontScale=1f neutralization, all Wave-0 unit tests present and substantive.

2. **Criterion 2 (UI-01):** Code substrate verified. ScreenScaffold correctly branches portrait/landscape via BoxWithConstraints with ratio-only sizing. Visual grid alignment is a manual-only check per 03-VALIDATION.md.

3. **Criterion 3 (UI-02):** Code substrate verified. OutlinedControl has ≥64dp touch target, 2dp outline, Intent → token mapping, no raw colors. Touch feel is manual-only.

4. **Criterion 4 (PRIM-01/03/04):** Code substrate verified. ConfirmGuard, ScrubberPage (keyboard-free), SeverityToast all present and substantively implemented. WR-01 dual-pointerInput warning in ScrubberPage is a known quality issue from the code review — not a missing feature but a reliability improvement needed before release; flagged in the human verification item. Gallery interaction is manual-only.

5. **Criterion 5 (perf):** Human-ratified PASS. Evidence artifacts exist (RenderBenchScene, RenderBenchmark, 03-PERF-RESULTS.md with two rounds of gfxinfo captures). Matthew reviewed the numbers + Codex second opinion and decided PASS on the A-variant two-part gate (liveness gate: MET; sparse-redraw p95 ≤ ~66 ms: MET at 50.1 ms). Mandatory Phase-6 re-validation is documented in PERF-RESULTS.md and ADR 0001.

**Outstanding actions:** The 6 manual-only sign-off items above (visual, touch, font-scale, gesture, confirm, toast) must be completed in the on-device gallery on `flox` before Phase 3 can be considered fully closed. WR-01 (ScrubberPage gesture race) should be fixed before Phase 4's setpoint controls depend on the scrubber — it is the only warning with user-facing reliability impact.

---

_Verified: 2026-05-31T00:00:00Z_
_Verifier: Claude (gsd-verifier)_
