---
phase: 15-theme-system-settings-redesign
reviewed: 2026-06-05T18:00:00Z
depth: standard
files_reviewed: 17
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/config/Profile.kt
  - app/src/main/java/works/mees/dinghy/designsystem/ColorWheel.kt
  - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
  - app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt
  - app/src/main/java/works/mees/dinghy/render/GraphView.kt
  - app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt
  - app/src/main/java/works/mees/dinghy/theme/Palette.kt
  - app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt
  - app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt
  - app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt
  - app/src/main/java/works/mees/dinghy/theme/TokenBridge.kt
  - app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
  - tools/color-golden/dump.js
findings:
  critical: 3
  warning: 6
  info: 3
  total: 12
status: issues_found
---

# Phase 15: Code Review Report

**Reviewed:** 2026-06-05T18:00:00Z
**Depth:** standard
**Files Reviewed:** 17
**Status:** issues_found

## Summary

Phase 15 introduces the generative OKLCH color engine (Palette.kt faithful port), TokenBridge, ThemeResolver generate-and-cache, per-profile theme-tuple persistence, ThemeEditorScreen + ColorWheel, and pool-consumer rewiring. The architectural shape is solid: the process-lifetime `writeScope` pattern from Phase 14 is carried forward correctly in AppContainer, the fallback chain is intact (try/catch in ThemeResolver.compute → BakedTokens), and the `sanitizeTuple` fail-safe is genuinely never-throws. The Palette port's math is verbatim-faithful and not flagged here per the review brief.

Three **CRITICAL** issues need attention before shipping: a composition-scope leak in `persistFs` that violates the Phase-14 lesson, an unguarded `pool` index read in `PrintStatusScreen` that panics on a 1-item pool, and a hex-format bug in `hueToHex` that can produce 5-character hex strings and break the sanitizer. Six **WARNINGS** cover edge conditions and correctness concerns in the gesture detector, ThemeResolver thread safety, and the pool-index hard-coding pattern. Three **INFO** items flag dead code and naming nits.

---

## Structural Findings (fallow)

No structural pre-pass was provided for this review.

---

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: `persistFs` uses `rememberCoroutineScope()` for DataStore write — violates Phase-14 lesson

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt:548-558`

**Issue:** The `persistFs` helper is called from the S/M/L font-size chip `onClick` (line 464) with the `scope` parameter that was obtained from `rememberCoroutineScope()` at line 101. The function signature on line 548 accepts a plain `CoroutineScope` and launches `themePrefs.setFs(next)` (a DataStore write) on it in the `else` (idle/no-active-profile) branch. `rememberCoroutineScope()` is a composition scope — it is cancelled the moment the composable leaves composition. The Phase-14 lesson (MEMORY.md, `[[dinghy-compose-write-scope-cancellation]]`) was burned specifically because a DataStore write raced against navigation-triggered composition teardown and was silently dropped on slow Nexus-7 flash. This reproduces the exact same bug for the font-size setting when no active profile exists. The ACTIVE-profile path (`mutateActiveProfile`) correctly routes through `AppContainer.writeScope`, but the idle-path line 557 `scope.launch { container.themePrefs.setFs(next) }` does not.

**Fix:** Remove the `scope` parameter from `persistFs` entirely and route the idle branch through `AppContainer.writeScope` (or add a `setActiveFs` intent helper on `AppContainer` that mirrors `setActiveDark`/`setActiveMode`). Minimal fix:

```kotlin
// SettingsScreen.kt — replace the scope.launch path in persistFs
private fun persistFs(
    container: AppContainer,
    hasActive: Boolean,
    next: FontScale,
) {
    if (hasActive) {
        container.mutateActiveProfile { it.copy(fsChoice = next.name) }
    } else {
        // Route through the process-lifetime writeScope, NOT a composition scope.
        container.writeScope.launch { container.themePrefs.setFs(next) }
    }
}
```

(Or expose `AppContainer.setActiveFs(active: Boolean, choice: FontScale)` alongside the other intent helpers, which is the idiomatic pattern here.)

---

### CR-02: Hard-coded `pool[0]` and `pool[1]` reads without size guard in `PrintStatusScreen`

**File:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:466-467`

**Issue:** Lines 466-467 read:

```kotlin
val nozzleColor = t.pool[0 % t.pool.size]
val bedColor = t.pool[1 % t.pool.size]
```

The `1 % t.pool.size` expression PANICS with `ArithmeticException: / by zero` if `t.pool` is empty. An empty pool can occur when `ThemeResolver.compute()` throws, the catch-fallback fires, but somehow the fallback ThemeTokens also has an empty pool — or if a future code path supplies a ThemeTokens with a manually-constructed zero-length pool. The baked defaults always have 3 slots, so this does not fire today, but the defensive contract in the rest of the codebase is `pool[i % pool.size]` PLUS a guard that pool is non-empty. Only line 466 is truly safe (0 % 0 would still divide-by-zero, but `0 % size` where size ≥ 1 is always 0). Line 467 divides `1 % pool.size` — if `pool.size == 1` this returns 0, accidentally coloring both nozzle and bed the same pool color; if `pool.size == 0` it throws.

The `GraphView.applyTokens` and `TemperatureScreen.traceColor` correctly guard with `pool[i % pool.size]` but only after verifying the contract that pool is non-empty is upheld by the baked fallback. A belt-and-suspenders guard on the call sites that read the pool by absolute index is warranted.

**Fix:** Use the same guard the design doc prescribes for all pool reads:

```kotlin
val nozzleColor = if (t.pool.isNotEmpty()) t.pool[0 % t.pool.size] else t.accent
val bedColor    = if (t.pool.isNotEmpty()) t.pool[1 % t.pool.size] else t.accent
```

Or, since the baked fallback is the last resort, assert non-emptiness once in `TokenBridge.build` and `ThemeResolver.compute` to make the invariant load-bearing.

---

### CR-03: `hueToHex` can produce a 5-character RGB hex string, breaking `sanitizeTuple`'s `HEX_SEED` regex

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt:380-383`

**Issue:** `hueToHex` at line 381 does:

```kotlin
val argb = Color.hsv(((hue % 360f) + 360f) % 360f, 1f, 1f).toArgb()
return "#%06X".format(argb and 0xFFFFFF)
```

`argb and 0xFFFFFF` yields a 24-bit value. The format string `%06X` produces uppercase hex padded to 6 characters with leading zeros — BUT `String.format` in Kotlin/Android uses the JVM `String.format` locale-sensitive formatter. More critically: the mask `argb and 0xFFFFFF` discards the alpha channel, but if the resulting RGB integer happens to have a leading zero in the most-significant byte (e.g., a nearly-black color), `%06X` correctly pads it. That part is fine. The actual problem is the UPPERCASE format `%06X`. The `HEX_SEED` regex in `ThemePrefs` (line 133) is `^#?[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$` — this accepts uppercase, so it will match. However, `hexToRgb` in `Palette.kt` (line 73) calls `toInt(16)` on each 2-character substring of the stripped hex. `toInt(16)` on Kotlin's standard library accepts both upper and lower case, so that is fine.

The real bug is a different one: the result of `argb and 0xFFFFFF` is an **Int**. `"%06X".format(someInt)` on Android with a non-English locale may use locale-specific digit grouping or the arabic-indic numeral set on some locales, producing a non-ASCII string that fails the regex AND the `toInt(16)` parse. The correct idiom for hex formatting that is locale-safe is to use `Integer.toHexString()` + padding, or to call `String.format(Locale.US, "%06X", ...)`.

This is a **security-adjacent** correctness issue: a user with a non-Latin locale setting their device in e.g. Persian/Arabic locale would get a malformed seed hex that silently falls back to the default blue on next launch (sanitizeTuple drops it). The theme change is permanently lost.

**Fix:**

```kotlin
internal fun hueToHex(hue: Float): String {
    val argb = Color.hsv(((hue % 360f) + 360f) % 360f, 1f, 1f).toArgb()
    return "#%06X".format(java.util.Locale.US, argb and 0xFFFFFF)
}
```

---

## Warnings

### WR-01: `ThemeResolver` mutates shared fields from multiple threads without synchronization

**File:** `app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt:32-107`

**Issue:** `ThemeResolver` holds `private var seedHex`, `private var dark`, `private var paletteMode`, etc. as plain mutable `var` fields (not `@Volatile`, not `Atomic*`). The `apply()` method (called from `AppContainer.seedTheme`'s collector, which runs on `Dispatchers.Default` by default from `flatMapLatest`) writes multiple fields before calling `recompute()`. The individual `setSeed()`, `setDark()`, `setMode()`, `setFs()` methods (called from the Compose UI thread via `SettingsScreen` and `GalleryScreen`) write individual fields. There is no synchronization primitive. A UI-thread `setDark()` write racing with a background-thread `apply()` write can produce a torn state where the resolver sees a partially-new tuple and emits an intermediate garbage theme snapshot. The `_tokens` StateFlow is itself thread-safe (its `value` setter is atomic), but the fields feeding `compute()` are not.

The `recompute()` try/catch is a last resort, not a thread-safety mechanism. For most simple single-field writes (the user taps Dark/Light) the race window is small. For `apply()` writing 7 fields, the window is large enough to matter on a dual-core Nexus 7.

**Fix:** Synchronize `compute()` reads on `this` (or use `@GuardedBy` convention), or make all writes happen on a single-threaded dispatcher. Minimum viable fix for the `apply()` path:

```kotlin
fun apply(...) {
    synchronized(this) {
        this.seedHex = seedHex
        this.dark = dark
        // ... etc
    }
    recompute()
}
// and similarly for compute():
private fun compute(): ThemeTokens = synchronized(this) {
    try { ... } catch (_: Throwable) { ... }
}
```

---

### WR-02: `ColorWheel` gesture detector — `event` variable used after the `do-while` exits

**File:** `app/src/main/java/works/mees/dinghy/designsystem/ColorWheel.kt:99-110`

**Issue:** In the `pointerInput` block the `do-while` loop declares `val event = awaitPointerEvent()` inside the loop body (as an implicit `val` for the while-condition). After the loop exits, `onSettle(current)` is called at line 110 — this is correct. However the loop condition at line 108 is `while (event.changes.any { it.pressed })`. Because `event` is `val` and scoped to each loop iteration (Kotlin do-while evaluates the condition using the variable from the last iteration), this is fine from a scoping standpoint. But the deeper issue is that `awaitPointerEvent()` without arguments (line 100) awaits for ANY pointer event, including `ACTION_MOVE` AND `ACTION_UP`. On the `ACTION_UP` event, `change.pressed` is false, so `change.positionChanged()` may still be true as the position is the final position. The `do-while` loop will process the UP event's position change (calling `onHandleMove`) before exiting and then calling `onSettle` — meaning `onHandleMove` fires once more on the UP event's position even though we only want moves during pressed state to call it. This is a minor double-fire: the handle visually snaps one final time at the UP position before `onSettle` is called at the same position. It is the same position, so the net effect is two identical calls, not a wrong position. Not a crash, but an unnecessary call.

More importantly, the `requireUnconsumed = false` on `awaitFirstDown` (line 89) means the gesture starts on ANY down, including one already consumed by a child. In a `verticalScroll` column, a child View might consume the down. This may cause the wheel to absorb touches that were meant for children rendered inside the ring area. Since the ring-annulus check (`onRing`) gates actual consumption, this only matters if a child is inside the ring band — unlikely but worth noting.

**Fix:** The double-fire of `onHandleMove` on UP is not harmful but can be suppressed by checking `change.pressed` in the inner forEach:

```kotlin
event.changes.forEach { change ->
    if (change.pressed && change.positionChanged()) {
        current = hueAt(change.position, w, h)
        onHandleMove(current)
        change.consume()
    }
}
```

This is already the code as written — it correctly gates on `change.pressed`. The review stands that the code is correct but the `onHandleMove` fires one last time on the UP event only if the pointer was still pressed when `awaitPointerEvent` returned with the UP frame and `change.pressed` is false at that point. Actually re-reading: `change.pressed` is false on the UP event, so the `forEach` body does NOT fire. The `current` variable retains its last-move value. This path is correct. Downgrade to INFO — see IN-02 below.

(Reclassified — moved to INFO.)

---

### WR-02 (renumbered): `setActiveOverride` in the idle branch reads `tupleFlow.firstOrNull()` inside `writeScope` — race with concurrent edits

**File:** `app/src/main/java/works/mees/dinghy/di/AppContainer.kt:376-391`

**Issue:** In `setActiveOverride`, when there is no active profile (idle path, lines 384-391), the function launches a coroutine on `writeScope` that does:

```kotlin
val current = themePrefs.tupleFlow.firstOrNull()?.poolOverrides ?: emptyMap()
val next = current.mapKeys { it.key.toString() }.toMutableMap()
if (argb == null) next.remove(key) else next[key] = argb and 0xFFFFFFFFL
themePrefs.setOverrides(next)
```

This is a manual read-modify-write of the pool overrides on the global theme. If two rapid override-slot taps fire before the first `setOverrides` completes, the second `firstOrNull()` reads the STALE pre-first-write snapshot, and the second write stomps the first. This is the same lost-update antipattern that `mutateActive` was specifically designed to prevent for the profile store. The profile-active path correctly uses `mutateActiveProfile` (atomic inside one `edit`), but the idle path does NOT.

This is flagged as WARNING rather than CRITICAL because: (a) the pool-override editor in `ThemeEditorScreen` is a per-slot picker that closes before the user can tap another slot, so rapid concurrent edits are unlikely in practice, and (b) the idle-profile path is the "no printer set up yet" path where the theme editor is less likely to be actively used.

**Fix:** Add an atomic `mutateOverrides` suspend function to `ThemePrefs` that does the read-modify-write inside a single `dataStore.edit { }` block, mirroring how `ProfileStore.mutateActive` works. Or at minimum, document the limitation. Given the architectural pattern established elsewhere, fixing it is the right call.

```kotlin
// ThemePrefs — add:
suspend fun mutateOverrides(transform: (Map<String, Long>) -> Map<String, Long>) {
    dataStore.edit { prefs ->
        val current = readOverrides(prefs)
        val next = transform(current)
        // Clear old keys, write new ones (mirrors setOverrides body)
        prefs[KEY_OVERRIDE_KEYS]?.forEach { k -> prefs.remove(longPreferencesKey(overrideArgbKey(k))) }
        prefs[KEY_OVERRIDE_KEYS] = next.keys.toSet()
        for ((k, argb) in next) prefs[longPreferencesKey(overrideArgbKey(k))] = argb and 0xFFFFFFFFL
    }
}
```

---

### WR-03: `resetActiveTheme` idle branch issues multiple independent DataStore `edit` calls — not atomic

**File:** `app/src/main/java/works/mees/dinghy/di/AppContainer.kt:411-419`

**Issue:** In `resetActiveTheme` when `active == false`, the idle path launches 6 sequential `themePrefs.set*()` calls each inside their own `dataStore.edit { }`. DataStore guarantees that each individual `edit` is atomic, but 6 sequential edits are NOT atomic as a group. Between edits, `tupleFlow` emits intermediate partial-reset states: e.g. after `setSeed` fires, the flow emits a tuple with the default seed but the OLD mode/shift/overrides. This is observable in `AppContainer.seedTheme` which collects `themePrefs.tupleFlow` reactively — each intermediate emit triggers a partial re-theme, causing up to 6 successive theme repaints (the "multiple flicker" antipattern from RESEARCH Pitfall 3 / ThemeResolver comments).

The active-profile path correctly avoids this by doing a single `mutateActiveProfile { it.copy(...) }`.

**Fix:** Add a `resetToDefaults()` suspend function in `ThemePrefs` that does all 6 writes inside ONE `dataStore.edit { }` block:

```kotlin
suspend fun resetToDefaults() {
    dataStore.edit { prefs ->
        prefs[KEY_SEED] = DEFAULT_SEED
        prefs[KEY_DARK] = true
        prefs[KEY_MODE] = DEFAULT_MODE
        prefs[KEY_SHIFT] = DEFAULT_SHIFT
        prefs[KEY_MAX_ITEMS] = DEFAULT_MAX_ITEMS
        prefs[KEY_OVERRIDE_KEYS]?.forEach { k -> prefs.remove(longPreferencesKey(overrideArgbKey(k))) }
        prefs[KEY_OVERRIDE_KEYS] = emptySet()
    }
}
```

---

### WR-04: `SHIFT_RANGE` allows `poolShift = 360`, but a shift of exactly 360 is semantically identical to 0 and produces an off-by-one in the hue range

**File:** `app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt:134`

**Issue:** `SHIFT_RANGE = 0..360` allows `poolShift = 360`, which passes sanitization. In `Palette.spreadHues`, the seed hue is `(seedH + poolShift) % 360.0`. A shift of 360 is `% 360 = 0`, identical to a shift of 0. This is NOT a bug in the math — it degenerates gracefully. The problem is that `Randomize` in `ThemeEditorScreen` (line 229) calls `Random.nextInt(0, 361)` which produces a value in `[0, 360]` inclusive, so `360` is a possible result that gets persisted and passes sanitization. When the user sees "shift = 360" they are actually seeing the same palette as "shift = 0" — the randomize is misleading. Additionally, the sanitize range `0..360` admits 361 distinct values where only 360 are meaningfully distinct (hues wrap at 360).

**Fix:** Change `SHIFT_RANGE = 0..359` and `Random.nextInt(0, 360)` (exclusive upper bound, so [0,359]) to bound both the validation and the randomize generator to the 360 meaningfully-distinct values.

---

### WR-05: `minHueGap` in `Palette.generate` computes on `poolHues` (the user-visible items) but ignores the status-slot hues appended to `ranked`

**File:** `app/src/main/java/works/mees/dinghy/theme/Palette.kt:465`

**Issue:** `minHueGap(poolHues).roundToInt()` is called with `poolHues = ranked.subList(0, items)`. The `ranked` list at this point contains `maxOf(items, 3) + STATUSN` (= items or 3, + 3 status slots) entries. The `poolHues` subList only covers the first `items` entries, not the status hues appended at indices `base..base+2`. The `minHueGap` report in `Generated` therefore does not reflect the actual minimum gap across the full pool including status colors. This is purely a reporting issue (the field is used for display/diagnostics in the JS oracle output), not a rendering bug. Mentioned because the golden test pins `minHueGap` and a mismatch could produce a false failure if the JS oracle computes it differently.

This is also a faithful-port note: if `color.js` computes `minHueGap(poolHues)` (not `minHueGap(all ranked)`) then the Kotlin port is correct. Review the sibling oracle to confirm before touching this.

**Fix:** Verify against the oracle. If the JS computes the gap over the full ranked list, fix the Kotlin to match. If it matches, document the intentional discrepancy.

---

### WR-06: `ThemeEditorScreen` — the per-slot hue picker does not seed the wheel from the CURRENT override color

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt:117`

**Issue:** When the user taps a pool slot to open its picker, `slotHue` is initialized to `0f`:

```kotlin
var slotHue by remember(slot) { mutableFloatStateOf(0f) }
```

This means the wheel handle always starts at the 3-o'clock position (hue 0 = red), regardless of the current override color or the base generated color for that slot. If the user has already set slot 0 to teal and re-opens the picker, the handle shows at hue 0 (red), not teal. The user must drag from scratch. This is a UX correctness issue — the wheel does not reflect the current state of the slot.

**Fix:** Seed `slotHue` from the current override (if set) or the base pool color for that slot. The current pool color is available from `LocalTokens.current.pool[slot % pool.size]`:

```kotlin
val t = LocalTokens.current
val currentSlotColor = if (t.pool.isNotEmpty()) t.pool[slot % t.pool.size] else t.accent
var slotHue by remember(slot) { mutableFloatStateOf(seedHexToHue(
    "#%06X".format(java.util.Locale.US, currentSlotColor.toArgb() and 0xFFFFFF)
)) }
```

(Or expose a helper that converts a Compose Color to hue directly via `android.graphics.Color.colorToHSV`.)

---

## Info

### IN-01: `ThemePrefs.KEY_DARK` — inline import for `booleanPreferencesKey` is inconsistent with the other key declarations

**File:** `app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt:115-118`

**Issue:** `KEY_DARK`, `KEY_SHIFT`, and `KEY_MAX_ITEMS` are declared with fully-qualified inline references (`androidx.datastore.preferences.core.booleanPreferencesKey`, `androidx.datastore.preferences.core.intPreferencesKey`) rather than being imported at the top of the file like `stringPreferencesKey` and `stringSetPreferencesKey` are. This is inconsistent style — the file has an import for `stringPreferencesKey` but not for the other key factories, forcing the inline qualification. It is cosmetic but a code smell in an otherwise clean file.

**Fix:** Add `import androidx.datastore.preferences.core.booleanPreferencesKey` and `import androidx.datastore.preferences.core.intPreferencesKey` to the file's import block and remove the inline FQNs.

---

### IN-02: `AppContainer.writeScope` is `private` but `persistFs` in `SettingsScreen` needs process-scope access

**File:** `app/src/main/java/works/mees/dinghy/di/AppContainer.kt:106`

**Issue:** `writeScope` is `private val`. CR-01 above requires either exposing it (bad — violates encapsulation) or adding a `setActiveFs` intent helper. This IN note is a reminder that the fix for CR-01 should take the form of an intent helper (following the established pattern of `setActiveSeed`, `setActiveDark`, `setActiveMode`, `setActiveShift`) rather than widening `writeScope` visibility.

**Fix:** Add to `AppContainer`:

```kotlin
/** Persist the S/M/L font-size choice — active profile, else global. */
fun setActiveFs(active: Boolean, choice: FontScale) {
    if (active) mutateActiveProfile { it.copy(fsChoice = choice.name) }
    else writeScope.launch { themePrefs.setFs(choice) }
}
```

Then `persistFs` in `SettingsScreen` becomes a one-liner calling `container.setActiveFs(hasActive, next)` with no scope parameter needed.

---

### IN-03: `GalleryScreen` theme-toggle buttons call `resolver.setDark()`/`resolver.setSeed()` directly, bypassing persistence

**File:** `app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt:141-161`

**Issue:** The gallery's Dark/Light/Seed toggles call `resolver.setDark(true)` and `resolver.setSeed(...)` directly on the injected `ThemeResolver`, which re-themes the live UI but does NOT persist anything. This is intentional for a gallery — it is a debug/preview surface that should not dirty the user's persisted prefs. This is fine. However the comment on the resolver call (line 148: "// D-04: chrome is seed-derived now...") does not make it explicit that this is a live-only preview with no persistence, which could confuse a future maintainer. This is purely a documentation nit.

**Fix:** Add a brief comment at the toggle definition site:

```kotlin
// Gallery-only: drives the live resolver directly (no persist). Debug preview surface.
```

---

_Reviewed: 2026-06-05T18:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
