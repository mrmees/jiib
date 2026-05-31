---
phase: 03-design-system-theming-foundation
reviewed: 2026-05-31T00:00:00Z
depth: standard
files_reviewed: 32
files_reviewed_list:
  - app/build.gradle.kts
  - app/src/debug/AndroidManifest.xml
  - app/src/debug/java/works/mees/dinghy/gallery/GalleryActivity.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/works/mees/dinghy/bench/BenchActivity.kt
  - app/src/main/java/works/mees/dinghy/bench/RenderBenchScene.kt
  - app/src/main/java/works/mees/dinghy/designsystem/ConfirmGuard.kt
  - app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt
  - app/src/main/java/works/mees/dinghy/designsystem/layout/ScreenScaffold.kt
  - app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt
  - app/src/main/java/works/mees/dinghy/designsystem/SeverityToast.kt
  - app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt
  - app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt
  - app/src/main/java/works/mees/dinghy/render/GraphView.kt
  - app/src/main/java/works/mees/dinghy/render/ProgressRing.kt
  - app/src/main/java/works/mees/dinghy/render/RingBuffer.kt
  - app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt
  - app/src/main/java/works/mees/dinghy/theme/compose/DinghyTheme.kt
  - app/src/main/java/works/mees/dinghy/theme/compose/LocalTokens.kt
  - app/src/main/java/works/mees/dinghy/theme/Geist.kt
  - app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt
  - app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt
  - app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt
  - app/src/main/java/works/mees/dinghy/theme/views/ThemeableView.kt
  - app/src/test/java/works/mees/dinghy/render/GraphDownsampleTest.kt
  - app/src/test/java/works/mees/dinghy/render/RingBufferHolderTest.kt
  - app/src/test/java/works/mees/dinghy/theme/BakedTokenTableTest.kt
  - app/src/test/java/works/mees/dinghy/theme/FontScaleTest.kt
  - app/src/test/java/works/mees/dinghy/theme/ThemePrefsFallbackTest.kt
  - app/src/test/java/works/mees/dinghy/theme/ThemeResolverTest.kt
  - app/src/test/java/works/mees/dinghy/theme/TokenDeltaSerializationTest.kt
  - macrobenchmark/src/main/java/works/mees/dinghy/macrobenchmark/RenderBenchmark.kt
  - tools/check-release-no-gallery.sh
  - tools/oklch-bake/bake_tokens.py
  - gradle/libs.versions.toml
findings:
  critical: 0
  warning: 4
  info: 6
  total: 10
status: issues_found
---

# Phase 3: Code Review Report

**Reviewed:** 2026-05-31T00:00:00Z
**Depth:** standard
**Files Reviewed:** 32
**Status:** issues_found

## Summary

Phase 3 lays the design-system + theming foundation: the baked oklch→sRGB token table, the
`ThemeResolver`/`ThemePrefs` fail-safe persistence chain, the Compose `LocalTokens` boundary and the
Views push-token mirror, the shared render primitives (`ProgressRing` + `GraphView`/`RingBuffer`), and
the leaf design components (`OutlinedControl`, `ScrubberPage`, `ConfirmGuard`, `SeverityToast`,
`ScreenScaffold`) plus the debug gallery and bench/macrobench harnesses.

Overall the phase holds up well against its own non-negotiables. I independently verified the
load-bearing claims:

- **Bake reproducibility:** re-running `tools/oklch-bake/bake_tokens.py` produces output byte-identical
  to the committed `BakedTokens.kt` (`diff` → IDENTICAL). The drift guard is real.
- **Token purity:** no raw `Color(0x…)` styling literals leak into `designsystem/` or `render/`. The
  only hex literals outside `BakedTokens.kt` are the debug-gallery `SAMPLE_CUSTOM_DELTA` sample data
  (acceptable — it is test fixture data, not a styled surface).
- **Fail-safe theme reads:** `ThemePrefs.sanitize` + `IOException`→`emptyPreferences()` cannot throw on
  a corrupt blob; every malformed input degrades to the default theme. Cannot black-screen.
- **Allocation-free draw:** `GraphView.onDraw` reuses one `Path`/`Paint` set, `rewind()`s, and never
  allocates per frame. `RingBuffer.snapshot()` is the only per-tick allocation and it is by-contract.
- **No looping animation (D-13):** none of the primitives use `rememberInfiniteTransition`/`animate*`.
- **Symbol resolution:** all cross-module field references (`event.extruderTemp`, `event.graphSample.extruder`,
  `state.heaters["extruder"]?.temperature`, `state.progress`) resolve and are range-consistent (progress
  is 0..1 on both `PrinterState` and `SyntheticFeed`).

No BLOCKER-class defects found. The findings below are robustness/quality issues — the most concrete is
the dual-`pointerInput` gesture race in `ScrubberPage` (WR-01), which can drop or fight taps/drags on the
one keyboard-free numeric-entry primitive every later setpoint screen inherits.

## Warnings

### WR-01: ScrubberPage races a tap detector against a drag detector on the same element

**File:** `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt:127-132`
**Issue:** The scrubber bar attaches TWO separate `pointerInput` blocks to the same `Box` — one running
`detectTapGestures` and one running `detectDragGestures`:

```kotlin
.pointerInput(range, step) { detectTapGestures { offset -> setFromX(offset.x) } }
.pointerInput(range, step) { detectDragGestures { change, _ -> setFromX(change.position.x) } }
```

Each `pointerInput` spawns its own coroutine and both compete for the same pointer stream. Neither
detector consumes events in a way that coordinates with the other, so the initial pointer-down is
delivered to both: a short press can be claimed by the drag detector (so the tap callback never fires),
or the two can interleave. For the ONE primitive that replaces the alphanumeric keypad for every numeric
setpoint (heater target, jog distance, fan %), unreliable touch handling is exactly the failure the
design system exists to prevent. This will not be caught by the host-pure unit tests (no Robolectric /
no touch), and the gallery sign-off is manual — easy to miss intermittent drops.
**Fix:** Drive both gestures from a SINGLE `pointerInput` so they share one coordinated dispatch, e.g.:
```kotlin
.pointerInput(range, step) {
    detectTapGestures { offset -> setFromX(offset.x) }
}
// combine into one awaitEachGesture, OR use a single pointerInput that does drag with an
// onDragStart that also sets from the start position (covers the "tap == zero-length drag" case):
.pointerInput(range, step) {
    detectDragGestures(
        onDragStart = { offset -> setFromX(offset.x) },
    ) { change, _ -> setFromX(change.position.x) }
}
```
The `onDragStart` form makes a tap a zero-length drag, eliminating the second detector entirely.

### WR-02: App-wide `usesCleartextTraffic="true"` is broader than the stated need

**File:** `app/src/main/AndroidManifest.xml:24-25`
**Issue:** The application element sets both `android:usesCleartextTraffic="true"` (governs API 23) and
`android:networkSecurityConfig` (governs API 24+). The flag enables cleartext for ALL destinations
app-wide on API 23, not just the LAN Moonraker host. The phase comment frames this as intentional for
the local-network Moonraker case, and the NSC presumably scopes API 24+ — but on the API-23 floor the
blanket flag means any future code path (a stray analytics/update fetch, a mistyped URL) silently
transmits in cleartext with no domain restriction. Given the device test target actually runs API 30
(LineageOS), the API-23-only justification rarely applies in practice.
**Fix:** Keep the network-security-config as the single source of truth and scope cleartext to the
configured Moonraker host/subnet there (`<domain-config cleartextTrafficPermitted="true">` for the LAN
range only). For the API-23 floor, accept that the NSC is ignored, but document that cleartext to
arbitrary hosts is a deliberate floor-only concession rather than leaving the unbounded flag as the
primary control. At minimum, add a regression check that no non-LAN host is ever contacted.

### WR-03: `GalleryActivity` rebuilds resolver + PrinterStateStore on every configuration change

**File:** `app/src/debug/java/works/mees/dinghy/gallery/GalleryActivity.kt:51-58`
**Issue:** `ThemeResolver` and `PrinterStateStore(scope = lifecycleScope)` are constructed directly in
`onCreate`. On rotation (the gallery is explicitly used to validate portrait↔landscape) the Activity is
recreated, a NEW resolver and a NEW store are built, and the previous store's `lifecycleScope`-bound
sampler coroutine is cancelled — but any theme state the user set via the gallery's controls (Dark/Light,
Custom, S/M/L) is reset to `ThemePrefs.DEFAULT` on each rotation because the resolver is reseeded. The
debug gallery is the on-device sign-off surface for the exact theme×orientation matrix, so losing the
selected theme on every rotation actively undermines its purpose.
**Issue is debug-only** (release never ships the gallery), hence WARNING not BLOCKER.
**Fix:** Hoist the resolver/store across recreation — construct via a `ViewModel` (survives config
change) or seed the resolver from persisted `ThemePrefs` so a rotation re-reads the same state. At
minimum, persist the gallery's current (base, custom, fs) selection so rotation restores it.

### WR-04: `RenderSceneState.equals` compares `progress` with `==`, mishandling NaN

**File:** `app/src/main/java/works/mees/dinghy/bench/RenderBenchScene.kt:122-128`
**Issue:** The custom `equals` does `progress == other.progress`. Kotlin `Float.==` returns `false` for
`NaN == NaN`, so if `progress` ever became `NaN`, every emission would be considered unequal and force a
recompose each tick (defeating the stability the custom `equals`/`hashCode` exists to provide). The
companion `ProgressRing` defends against NaN (`if (progress.isNaN()) 0f`), but `RenderSceneState` does
not, so the state object itself is not NaN-stable. Today the synthetic feed never emits NaN, so this is
latent, but the class is the per-tick state for the perf scene and the whole point of the custom
`equals` is recomposition control on the Adreno-320 floor.
**Fix:** Use a NaN-safe comparison:
```kotlin
return progress.toBits() == other.progress.toBits() && snapshot === other.snapshot
```
(`toBits()` already feeds `hashCode` via `progress.hashCode()`, so this also keeps equals/hashCode
consistent.)

## Info

### IN-01: ConfirmGuard paints a translucent tint as the only background layer

**File:** `app/src/main/java/works/mees/dinghy/designsystem/ConfirmGuard.kt:64,67`
**Issue:** `tint` is `stopSoft`/`goSoft` (alpha ≈ 0x26/0x29). It is applied as the full-screen `Box`
background with no opaque base color beneath it inside the guard. When the guard is shown as a true
full-screen surface this composites over the host `Surface` (fine), but when embedded (as in the gallery
at a fixed 320dp height) it composites over whatever is behind, which can read as a washed-out tint
rather than the intended faint wash over the app background.
**Fix:** Draw an opaque base (e.g. `t.bg`) first, then the soft tint on top, so the guard's gravity wash
is deterministic regardless of host: `Box(modifier.fillMaxSize().background(t.bg).background(tint))`.

### IN-02: `BenchActivity.mountRenderScene` reads `intent` extras but the activity ignores new intents

**File:** `app/src/main/java/works/mees/dinghy/bench/BenchActivity.kt:62,86`
**Issue:** Scene + `nofill` are read once in `onCreate` from `intent`. The activity has no
`onNewIntent`/`singleTop` handling, so re-launching with a different `--es scene`/`--ez nofill` while the
activity is already running (the macrobench `FLAG_ACTIVITY_CLEAR_TASK` mitigates this for the bench, but
manual `am start` may not) silently keeps the first scene. Measurement-only harness, low impact.
**Fix:** Either document the "always cold-launch" contract on the class (the macrobench already
`killProcess()`es) or add `launchMode="singleTop"` + `onNewIntent` to re-mount on a fresh intent.

### IN-03: `assertDeterministic()` runs on the main thread in `onCreate`

**File:** `app/src/main/java/works/mees/dinghy/bench/BenchActivity.kt:60`
**Issue:** The fairness self-check runs synchronously on the UI thread before any scene mounts. It is
cheap today, but it replays the full synthetic feed; if that grows it could delay first frame / risk ANR
on the Adreno-320 floor. Bench-only.
**Fix:** Keep it cheap, or move it behind a debug assertion / off the main thread if the feed length
grows.

### IN-04: `ScrubberPage` whole-number detection breaks for values outside Int range

**File:** `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt:97`
**Issue:** `working == working.roundToInt().toFloat()` uses `roundToInt()`, which saturates at
`Int.MAX/MIN_VALUE`. For setpoints far outside Int range the "is it a whole number" branch and the
displayed value would be wrong. No current consumer uses such ranges (heater °C, mm, %), so latent.
**Fix:** Guard with `roundToLong()` or compare `working % 1f == 0f` for the integer check.

### IN-05: Two launcher icons in the debug variant

**File:** `app/src/debug/AndroidManifest.xml:22-30`
**Issue:** The debug variant registers a second `LAUNCHER` activity (Gallery) alongside MainActivity, so
debug installs show two app icons. This is explicitly called out and accepted in the manifest comment
(03-RESEARCH Open Question 1) and is debug-only, so it is recorded for completeness, not as a defect.
**Fix:** None required; accepted. If undesired later, drop the `LAUNCHER` category and launch the gallery
via `am start` only.

### IN-06: `GraphView.setData` early-sample cap uses a 256px fallback that may under/over-sample briefly

**File:** `app/src/main/java/works/mees/dinghy/render/GraphView.kt:101,163`
**Issue:** Before layout (`width == 0`) the sanitize cap falls back to `DEFAULT_PIXEL_CAP = 256`. On the
1920px-wide flox panel the first pre-layout sample is capped to 256 vertices, then re-capped to the real
width on the next `setData`. Cosmetic transient at most (the graph repaints on the next ~3 Hz sample
with the correct cap), and the value is bounded/safe.
**Fix:** None required; optionally re-run `sanitize` in `onSizeChanged` so the first laid-out frame uses
the real width immediately.

---

_Reviewed: 2026-05-31T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
