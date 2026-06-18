# Theme Screen Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the **Theme** screen into the app's lists-first Focus/Field grammar (a Field of 4 rows + a Focus that swaps by selection), with a live-preview Save/Revert draft model, a hue-only seed picker, a 3-slider HSV swatch editor, a 4×2 swatch grid, and a new accent-override capability.

**Architecture:** Accent override is added as a dedicated `Long?` field threaded through `ThemePrefs.ThemeTuple` → `Profile`/`PersistedProfile` → `TokenBridge.build` (resolved before the accent family derives) → `ThemeResolver`. Live preview flows through a NEW `_themeDraft: MutableStateFlow<ThemeTuple?>` folded into `AppContainer.effectiveTokens` at highest precedence (ungated, unlike the dev-cycler `_themeOverride`); Save persists via the existing `writeScope` intents and clears the draft once the persisted tuple catches up; Revert clears the draft. The screen mirrors `AppSettingsScreen`'s `ScreenScaffold` + `ListBlock` + `FocusFrame` + `FootButtonBar` structure with a stateless `ThemeContent` seam for the `@Preview` matrix.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx-coroutines `StateFlow`/`combine`, DataStore (Preferences), Compose `Canvas` (sliders), JUnit host tests. Build via the Windows helper: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"` (pipe through `tr -d '\r'`; exit code is authoritative).

**Reference spec:** `docs/superpowers/specs/2026-06-18-theme-screen-rework-design.md` (owner-approved + Codex-reviewed).

---

## Conventions for every task

- **Build command (any Gradle task):**
  ```bash
  /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <gradle args> --no-daemon" 2>&1 | tr -d '\r'
  ```
- **Unit test sourceset compiles as a whole** before any `--tests` filter runs — a new constructor param on `AppContainer`/`Profile` breaks positional test fixtures; fix call sites in the SAME task ([[dinghy-increment-values]] lesson).
- **Durable writes go through `writeScope` intents on `AppContainer`** — NEVER `rememberCoroutineScope()` ([[dinghy-compose-write-scope-cancellation]]).
- **All chrome via `LocalTokens`; literal swatch/handle colors are the sanctioned data carve-out.**
- **Commit after each task** with the message shown in its final step.

---

## File Structure

**Engine / persistence (Phase 1–2):**
- `app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt` — add `accentOverride` to `ThemeTuple`, its DataStore key, reader, `setAccentOverride`, atomic `applyTuple`, reset.
- `app/src/main/java/works/mees/dinghy/config/Profile.kt` — add `accentOverrideArgb`, thread `toThemeTuple`/`toPersisted`/`fromPersisted`. **`PersistedProfile` is declared INSIDE this same file (`Profile.kt:24`) — add its wire field here too; there is NO separate `PersistedProfile.kt`.**
- `app/src/main/java/works/mees/dinghy/theme/TokenBridge.kt` — add `accentOverride: Color?` param; resolve accent before the family.
- `app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt` — thread `accentOverride` through `apply`/`bake`/`computeFrom`/`compute`; add `setAccentOverride`.
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — `setActiveAccentOverride`; `_themeDraft` + `setThemeDraft`/`updateThemeDraft`/`commitThemeDraft`/`clearThemeDraft`; fold draft into `effectiveTokens`; thread accent into `seedTheme`.

**Controls / icons (Phase 3):**
- `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — 5 new vals + `all` entries.
- `app/src/main/java/works/mees/dinghy/designsystem/HueSlider.kt` — NEW.
- `app/src/main/java/works/mees/dinghy/designsystem/HsvSliders.kt` — NEW.

**Screen / strings / shell / previews (Phase 4–5):**
- `app/src/main/res/values/strings.xml` — new `theme_*` strings.
- `app/src/main/java/works/mees/dinghy/ui/screen/ThemeScreen.kt` — FULL REWRITE (host + `ThemeContent` seam + states + grid + swatch editor + color-math helpers migrated here).
- `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt` — DELETE.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — move Theme to `screenOwnsEstop`.
- `app/src/main/java/works/mees/dinghy/preview/ThemeEditorPreviews.kt` — REPLACE with `ThemePreviews.kt` targeting `ThemeContent`.

**Tests:**
- `app/src/test/java/works/mees/dinghy/theme/AccentOverrideTest.kt` — NEW (sanitize round-trip, bake==compute, family follows override).
- `app/src/test/java/works/mees/dinghy/theme/ThemeDraftTest.kt` — NEW (preview no-persist, commit, revert).
- Existing `app/src/test/java/works/mees/dinghy/theme/ThemeResolver*Test.kt` / golden tests must stay green.

---

# Phase 1 — Accent-override capability (engine + persistence, host-testable)

## Task 1: `ThemeTuple` + `ThemePrefs` accent field, persistence, atomic writers

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt`
- Test: `app/src/test/java/works/mees/dinghy/theme/AccentOverrideTest.kt` (create)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/works/mees/dinghy/theme/AccentOverrideTest.kt`:
```kotlin
package works.mees.dinghy.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccentOverrideTest {
    @Test fun `sanitize keeps a valid accent override`() {
        val t = ThemePrefs.sanitizeTuple(
            rawSeed = "#3f78ff", rawDark = true, rawMode = "Colorful",
            rawShift = 0, rawOverrides = emptyMap(), rawAccent = 0xFF112233L,
        )
        assertEquals(0xFF112233L, t.accentOverride)
    }

    @Test fun `sanitize drops a garbage accent override`() {
        val t = ThemePrefs.sanitizeTuple(
            rawSeed = "#3f78ff", rawDark = true, rawMode = "Colorful",
            rawShift = 0, rawOverrides = emptyMap(), rawAccent = -5L,
        )
        assertNull(t.accentOverride)
    }

    @Test fun `default tuple has no accent override`() {
        assertNull(ThemePrefs.TUPLE_DEFAULT.accentOverride)
    }
}
```

- [ ] **Step 2: Run it — verify it fails to compile/run**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.theme.AccentOverrideTest --no-daemon" 2>&1 | tr -d '\r' | tail -30
```
Expected: compile failure — `sanitizeTuple` has no `rawAccent` param, `ThemeTuple` has no `accentOverride`.

- [ ] **Step 3: Add the field + DataStore key + reader + writers**

In `ThemePrefs.kt`:

(a) Add `accentOverride` to `ThemeTuple` (after `statusOverrides`, before `fs`):
```kotlin
        val statusOverrides: Map<String, Long> = emptyMap(),
        /** Optional accent override (opaque unsigned-32 ARGB), null = seed-derived accent. */
        val accentOverride: Long? = null,
        val fs: Float,
```

(b) Add the DataStore key near `KEY_SHIFT`:
```kotlin
        private val KEY_ACCENT_OVERRIDE = androidx.datastore.preferences.core.longPreferencesKey("theme_accent_override")
```

(c) `tupleFlow` — pass the raw accent into `sanitizeTuple`:
```kotlin
                sanitizeTuple(
                    rawSeed = prefs[KEY_SEED],
                    rawDark = prefs[KEY_DARK],
                    rawMode = prefs[KEY_MODE],
                    rawShift = prefs[KEY_SHIFT],
                    rawOverrides = readOverrides(prefs),
                    rawAccent = prefs[KEY_ACCENT_OVERRIDE],
                )
```

(d) Add a setter near `setShift`:
```kotlin
    /** Persist (or clear, null) the accent override ARGB. */
    suspend fun setAccentOverride(argb: Long?) {
        dataStore.edit {
            if (argb == null) it.remove(KEY_ACCENT_OVERRIDE) else it[KEY_ACCENT_OVERRIDE] = argb and 0xFFFFFFFFL
        }
    }
```

(e) Add an atomic full-tuple writer (used later by `commitThemeDraft` for the idle/global path) near `resetToDefaults`:
```kotlin
    /** Atomically persist a whole tuple to the global idle theme (one edit → one tupleFlow re-emit). */
    suspend fun applyTuple(tuple: ThemeTuple) {
        dataStore.edit { prefs ->
            prefs[KEY_SEED] = tuple.seedHex
            prefs[KEY_DARK] = tuple.dark
            prefs[KEY_MODE] = tuple.paletteMode
            prefs[KEY_SHIFT] = tuple.poolShift
            // recombine the split runtime maps back into the single String→Long wire map
            val wire = tuple.poolOverrides.mapKeys { it.key.toString() } + tuple.statusOverrides
            writeOverrides(prefs, wire)
            if (tuple.accentOverride == null) prefs.remove(KEY_ACCENT_OVERRIDE)
            else prefs[KEY_ACCENT_OVERRIDE] = tuple.accentOverride and 0xFFFFFFFFL
        }
    }
```

(f) `resetToDefaults` — clear the accent key:
```kotlin
    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            prefs[KEY_SEED] = DEFAULT_SEED
            prefs[KEY_DARK] = true
            prefs[KEY_MODE] = DEFAULT_MODE
            prefs[KEY_SHIFT] = DEFAULT_SHIFT
            writeOverrides(prefs, emptyMap())
            prefs.remove(KEY_ACCENT_OVERRIDE)
        }
    }
```

(g) `sanitizeTuple` — add `rawAccent` param + validation, and pass to the returned tuple:
```kotlin
        fun sanitizeTuple(
            rawSeed: String?,
            rawDark: Boolean?,
            rawMode: String?,
            rawShift: Int?,
            rawOverrides: Map<String, Long>?,
            rawAccent: Long? = null,
        ): ThemeTuple {
```
…and inside, before `return ThemeTuple(`:
```kotlin
            val accent = if (rawAccent != null && rawAccent.isValidArgb()) rawAccent else null
```
…and in the returned `ThemeTuple(...)` add `accentOverride = accent,` before `fs = fs,`.

- [ ] **Step 4: Run the test — verify it passes**

Run the Step-2 command. Expected: PASS (3 tests).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt app/src/test/java/works/mees/dinghy/theme/AccentOverrideTest.kt
git commit -m "feat(theme): accent override field in ThemeTuple + ThemePrefs persistence"
```

---

## Task 2: `Profile` + `PersistedProfile` accent field round-trip

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/config/Profile.kt` (contains BOTH `Profile` and `PersistedProfile`)
- Test: `app/src/test/java/works/mees/dinghy/theme/AccentOverrideTest.kt` (append)

- [ ] **Step 1: Add the failing test (append to `AccentOverrideTest`)**
```kotlin
    @Test fun `profile threads accent override through tuple and persistence`() {
        val p = works.mees.dinghy.config.Profile(id = "x", host = "h", accentOverrideArgb = 0xFF445566L)
        assertEquals(0xFF445566L, p.toThemeTuple().accentOverride)
        val round = works.mees.dinghy.config.Profile.fromPersisted(p.toPersisted())
        assertEquals(0xFF445566L, round.accentOverrideArgb)
    }
```

- [ ] **Step 2: Run — verify it fails to compile** (`accentOverrideArgb` unknown).
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.theme.AccentOverrideTest --no-daemon" 2>&1 | tr -d '\r' | tail -20
```

- [ ] **Step 3: Thread the field**

`Profile.kt`:
- Add field after `nameAutoSeeded`:
```kotlin
    val nameAutoSeeded: Boolean = false,
    /** Optional per-printer accent override ARGB (unsigned-32), null = seed-derived. */
    val accentOverrideArgb: Long? = null,
```
- `toThemeTuple()` — pass it:
```kotlin
    fun toThemeTuple(): ThemePrefs.ThemeTuple =
        ThemePrefs.sanitizeTuple(
            rawSeed = seedHex,
            rawDark = dark,
            rawMode = paletteMode,
            rawShift = poolShift,
            rawOverrides = poolOverrides,
            rawAccent = accentOverrideArgb,
        )
```
- `toPersisted()` — add `accentOverrideArgb = accentOverrideArgb,` to the `PersistedProfile(...)` call.
- `fromPersisted()` — add `accentOverrideArgb = p.accentOverrideArgb,` to the `Profile(...)` call.

`PersistedProfile` (the `@Serializable` class lower in the SAME `Profile.kt`):
- Add the matching nullable field (after its `nameAutoSeeded`, keep the `@Serializable` defaulting convention used by the rest of the class):
```kotlin
    val accentOverrideArgb: Long? = null,
```

- [ ] **Step 4: Run — verify PASS** (Step-2 command).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/config/Profile.kt app/src/test/java/works/mees/dinghy/theme/AccentOverrideTest.kt
git commit -m "feat(theme): accent override on Profile/PersistedProfile with round-trip"
```

---

## Task 3: `TokenBridge` applies the accent override to the whole accent family

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/theme/TokenBridge.kt`
- Test: `app/src/test/java/works/mees/dinghy/theme/AccentOverrideTest.kt` (append)

- [ ] **Step 1: Add the failing test**
```kotlin
    @Test fun `token bridge applies accent override to accent token`() {
        val gen = Palette.generate(seedHex = "#3f78ff", dark = true, maxItems = 4)
        val overridden = TokenBridge.build(
            gen = gen, overrides = emptyMap(), fs = 1f,
            statusOverrides = emptyMap(),
            accentOverride = androidx.compose.ui.graphics.Color(0xFFFF0000),
        )
        // accent should be (approximately) the override red, not the blue seed accent
        val argb = overridden.accent.value // packed; compare red dominance
        assertEquals(0xFF, (overridden.accent.toArgb() ushr 24) and 0xFF) // opaque
        org.junit.Assert.assertTrue(
            "accent red channel should dominate after red override",
            (overridden.accent.toArgb() ushr 16 and 0xFF) > (overridden.accent.toArgb() and 0xFF),
        )
    }
```
(Add imports `androidx.compose.ui.graphics.toArgb` at top if not present.)

- [ ] **Step 2: Run — verify it fails** (`accentOverride` param unknown).

- [ ] **Step 3: Implement**

In `TokenBridge.build(...)`:
- Add the param:
```kotlin
    fun build(
        gen: Palette.Generated,
        overrides: Map<Int, Color>,
        fs: Float,
        statusOverrides: Map<String, Color> = emptyMap(),
        accentOverride: Color? = null,
    ): ThemeTokens {
```
- Add a private `Color → hex` helper at file scope (near other helpers):
```kotlin
    /** Opaque Compose Color → "#RRGGBB" (Locale.US — never non-Latin digits, mirrors hueToHex). */
    private fun hexOf(c: Color): String =
        "#%06X".format(java.util.Locale.US, c.toArgb() and 0xFFFFFF)
```
- Replace the accent-resolution line `val accent = bake(t.primary)` and feed the family from a resolved hex. Where the code currently has (around L116 + L133–138 + L146–147):
```kotlin
        val accentHex: String = accentOverride?.let { hexOf(it) } ?: t.primary
        val accent = bake(accentHex)
```
  and in the `ThemeTokens(...)` construction change the accent family to use `accentHex` instead of `t.primary`:
```kotlin
            accent = accent,
            accent2 = lShift(accentHex, if (d) 0.08 else -0.05),
            accentSoft = rgbaOf(accentHex, if (d) 0.16 else 0.12),
            accentLine = rgbaOf(accentHex, if (d) 0.55 else 0.50),
            accentGlow = rgbaOf(accentHex, if (d) 0.35 else 0.20),
```
  (`directional.temperature = accent` already follows `accent` — no change needed there.)
- Ensure `import androidx.compose.ui.graphics.toArgb` is present.

> NOTE: accent override is **NOT** mode-gated — unlike `statusOverrides` it applies in Colorful, Simple, AND HighContrast (accent is always shown). Do not wrap `accentHex` in the `colorful` gate.

- [ ] **Step 4: Run — verify PASS.**

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/theme/TokenBridge.kt app/src/test/java/works/mees/dinghy/theme/AccentOverrideTest.kt
git commit -m "feat(theme): TokenBridge applies accent override across the accent family"
```

---

## Task 4: Thread `accentOverride` through `ThemeResolver` (+ `bake` parity)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt`
- Test: `app/src/test/java/works/mees/dinghy/theme/AccentOverrideTest.kt` (append)

- [ ] **Step 1: Add the failing test (bake == apply for an accent override)**
```kotlin
    @Test fun `resolver bake equals apply for an accent override`() {
        val tuple = ThemePrefs.TUPLE_DEFAULT.copy(accentOverride = 0xFFFF0000L)
        val r = ThemeResolver()
        r.apply(
            seedHex = tuple.seedHex, dark = tuple.dark, paletteMode = tuple.paletteMode,
            poolShift = tuple.poolShift, overrides = emptyMap(), fs = tuple.fs,
            statusOverrides = emptyMap(),
            accentOverride = androidx.compose.ui.graphics.Color(0xFFFF0000),
        )
        val applied = r.tokens.value.accent
        val baked = r.bake(tuple).accent
        assertEquals(baked.value, applied.value)
    }
```

- [ ] **Step 2: Run — verify it fails** (`apply`/`bake` have no `accentOverride`).

- [ ] **Step 3: Implement — add the field + thread it**

In `ThemeResolver.kt`:
- Constructor: add `private var accentOverride: Color? = null,` after `statusOverrides`.
- New mutator near `setStatusOverride`:
```kotlin
    /** Set (or clear, null) the accent override; recomputes + re-emits. Applies in ALL modes. */
    fun setAccentOverride(argb: Color?) = synchronized(this) {
        accentOverride = argb
        recompute()
    }
```
- `apply(...)` — add `accentOverride: Color? = null,` param and `this.accentOverride = accentOverride` in the body.
- `compute()` — pass `accentOverride` to `computeFrom`.
- `bake(tuple)` — pass `accentOverride = tuple.accentOverride?.toComposeColor()`.
- `computeFrom(...)` — add `accentOverride: Color?,` param and pass it to `TokenBridge.build(generated, poolOverrides, fs, statusOverrides, accentOverride)`.
- Ensure `import works.mees.dinghy.theme.toComposeColor` (it's a top-level fun in `StatusSlot.kt`, same package — no import needed) and `import androidx.compose.ui.graphics.Color` (already present).

- [ ] **Step 4: Run — verify PASS. Then run the full theme test package to prove no golden regressions:**
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.theme.* --no-daemon" 2>&1 | tr -d '\r' | tail -30
```
Expected: PASS (incl. `PaletteGoldenTest`, `ThemeResolverBakeTest` — accent override defaults to null so existing goldens are unchanged).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt app/src/test/java/works/mees/dinghy/theme/AccentOverrideTest.kt
git commit -m "feat(theme): thread accent override through ThemeResolver apply/bake/compute"
```

---

# Phase 2 — Draft/preview layer + accent intent (AppContainer)

## Task 5: `setActiveAccentOverride` durable intent

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`

- [ ] **Step 1: Implement** (no isolated unit test — covered by Task 6's draft test and on-device UAT; this mirrors the proven `setActiveOverride` shape exactly).

(a) Add near `setActiveStatusOverride`:
```kotlin
    /** Persist (or clear, null) the accent override — active profile, else the global idle theme. */
    fun setActiveAccentOverride(active: Boolean, argb: Long?) {
        if (active) mutateActiveProfile { it.copy(accentOverrideArgb = argb?.let { v -> v and 0xFFFFFFFFL }) }
        else writeScope.launch { themePrefs.setAccentOverride(argb) }
    }
```

(b) **Codex fix — `seedTheme()` must apply the persisted accent** so `themeResolver.tokens` consumers see it. In `seedTheme()`'s `themeResolver.apply(...)` call (`AppContainer.kt:~945`), add as a new argument:
```kotlin
                        accentOverride = tuple.accentOverride?.toComposeColor(),
```
(Ensure `works.mees.dinghy.theme.toComposeColor` resolves — it's a top-level fun in `StatusSlot.kt`; add the import if needed.)

(c) **Codex fix — `resetActiveTheme()` must clear the accent field.** In the ACTIVE branch's `it.copy(...)` (`AppContainer.kt:~1055`), add `accentOverrideArgb = null,` after `poolOverrides = emptyMap(),`. (The idle branch already routes through `themePrefs.resetToDefaults()`, patched in Task 1 to remove the accent key.)

- [ ] **Step 2: Build to verify it compiles**
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r' | tail -15
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/di/AppContainer.kt
git commit -m "feat(theme): accent override intent + seedTheme apply + reset clear"
```

---

## Task 6: Draft overlay folded into `effectiveTokens` (preview / commit / revert)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`
- Test: `app/src/test/java/works/mees/dinghy/theme/ThemeDraftTest.kt` (create)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/works/mees/dinghy/theme/ThemeDraftTest.kt`. It drives the pure overlay math directly (no DataStore) by testing the precedence helper extracted in Step 3:
```kotlin
package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.di.resolveThemeTuple

class ThemeDraftTest {
    private val base = ThemePrefs.TUPLE_DEFAULT
    private val draft = base.copy(seedHex = "#ff0000")

    @Test fun `draft wins over base and override regardless of devOn`() {
        assertEquals(draft, resolveThemeTuple(base = base, draft = draft, override = null, devOn = false))
        assertEquals(draft, resolveThemeTuple(base = base, draft = draft, override = null, devOn = true))
    }

    @Test fun `no draft falls back to base when devOn is false`() {
        assertEquals(base, resolveThemeTuple(base = base, draft = null, override = null, devOn = false))
    }
}
```

- [ ] **Step 2: Run — verify it fails** (`resolveThemeTuple` doesn't exist).
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.theme.ThemeDraftTest --no-daemon" 2>&1 | tr -d '\r' | tail -20
```

- [ ] **Step 3: Implement the overlay + draft API**

In `AppContainer.kt`:

(a) Extract the precedence as a pure top-level function (testable, no Android):
```kotlin
/**
 * Pure theme-tuple precedence used by [AppContainer.effectiveTokens]: an active edit DRAFT wins
 * unconditionally (live preview, ungated); else the dev-cycler override applies only while [devOn];
 * else the persisted base.
 */
fun resolveThemeTuple(
    base: works.mees.dinghy.theme.ThemePrefs.ThemeTuple,
    draft: works.mees.dinghy.theme.ThemePrefs.ThemeTuple?,
    override: works.mees.dinghy.theme.ThemeOverride?,
    devOn: Boolean,
): works.mees.dinghy.theme.ThemePrefs.ThemeTuple =
    draft ?: if (override != null && devOn) override.mergeOnto(base) else base
```

(b) Add the draft StateFlow near `_themeOverride`:
```kotlin
    /** The live edit DRAFT (in-memory ONLY, ungated) — the Theme editor's Save/Revert preview layer. */
    private val _themeDraft = MutableStateFlow<works.mees.dinghy.theme.ThemePrefs.ThemeTuple?>(null)
    val themeDraft: StateFlow<works.mees.dinghy.theme.ThemePrefs.ThemeTuple?> = _themeDraft.asStateFlow()

    /** Begin/replace the draft (e.g. on entering an editor, seeded from the saved tuple). In-memory only. */
    fun setThemeDraft(tuple: works.mees.dinghy.theme.ThemePrefs.ThemeTuple?) { _themeDraft.value = tuple }

    /** Atomically mutate the active draft (rapid-edit-safe), e.g. `{ it?.copy(seedHex = next) }`. */
    fun updateThemeDraft(
        transform: (works.mees.dinghy.theme.ThemePrefs.ThemeTuple?) -> works.mees.dinghy.theme.ThemePrefs.ThemeTuple?,
    ) { _themeDraft.update(transform) }

    /** Discard the draft (Revert/Cancel/exit-without-save). Live preview snaps back to the saved tuple. */
    fun clearThemeDraft() { _themeDraft.value = null }

    /**
     * Persist the current draft durably, then clear it once the persisted [activeThemeTuple] catches up
     * (so the live preview never flashes back to the old theme between the async write and its emit).
     *
     * Codex fix: do the write AND the bounded catch-up wait in ONE [writeScope] coroutine using the DIRECT
     * suspend writers (NOT [mutateActiveProfile], which launches its own coroutine and would race the wait).
     * The wait is bounded by [withTimeoutOrNull] so a no-op/failed write (e.g. no active profile) can never
     * strand the draft forever; the final clear is guarded so a NEW draft started during the wait survives.
     */
    fun commitThemeDraft(active: Boolean) {
        val d = _themeDraft.value ?: return
        writeScope.launch {
            if (active) {
                profileStore.mutateActive {
                    it.copy(
                        seedHex = d.seedHex, dark = d.dark, paletteMode = d.paletteMode, poolShift = d.poolShift,
                        poolOverrides = d.poolOverrides.mapKeys { e -> e.key.toString() } + d.statusOverrides,
                        accentOverrideArgb = d.accentOverride,
                    )
                }
            } else {
                themePrefs.applyTuple(d)
            }
            // Hold the draft as the live preview until the saved tuple matches it (ignore fs — app-global),
            // but never block forever.
            withTimeoutOrNull(2_000) { activeThemeTuple.first { it.copy(fs = d.fs) == d } }
            if (_themeDraft.value == d) clearThemeDraft()
        }
    }
```

(c) Fold the draft into `effectiveTokens` (replace the existing `combine(...)`):
```kotlin
    val effectiveTokens: Flow<works.mees.dinghy.theme.ThemeTokens> =
        combine(activeThemeTuple, _themeDraft, _themeOverride, devCyclerEnabled) { base, draft, ov, devOn ->
            themeResolver.bake(resolveThemeTuple(base, draft, ov, devOn))
        }
```

(d) Ensure imports: `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.flow.update`, `kotlinx.coroutines.withTimeoutOrNull` (the file already imports `combine`, `MutableStateFlow`, `asStateFlow`, `StateFlow` per Task-1 agent findings).

- [ ] **Step 4: Run the draft test — PASS. Then full compile + theme tests:**
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.theme.* --no-daemon" 2>&1 | tr -d '\r' | tail -20
```
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/di/AppContainer.kt app/src/test/java/works/mees/dinghy/theme/ThemeDraftTest.kt
git commit -m "feat(theme): live-preview draft overlay (preview/commit/revert) in effectiveTokens"
```

---

# Phase 3 — Icons + slider controls

## Task 7: New `DinghyIcons` glyphs

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`

- [ ] **Step 1: Add the 5 new vals** (place near related entries; `alternate` must be unique):
```kotlin
    val Contrast = DinghyIcon(IconRef.Ligature("contrast"), alternate = "contrast")
    val InvertColors = DinghyIcon(IconRef.Ligature("invert_colors"), alternate = "invert_colors")
    val Colors = DinghyIcon(IconRef.Ligature("colors"), alternate = "colors")
    val Star = DinghyIcon(IconRef.Ligature("star"), alternate = "star")
    val Shuffle = DinghyIcon(IconRef.Ligature("shuffle"), alternate = "shuffle")
```

- [ ] **Step 2: Add ALL FIVE to the `all` list** (append to the `listOf(...)`):
```kotlin
        Babystep,
        Contrast, InvertColors, Colors, Star, Shuffle,
    )
```

- [ ] **Step 3: Verify ligatures + registry uniqueness**
```bash
cd /mnt/e/claude/personal/github/dinghy-display && python3 tools/verify_ligatures.py 2>&1 | tail -15
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.designsystem.icons.DinghyIconsTest --no-daemon" 2>&1 | tr -d '\r' | tail -10
```
Expected: verifier reports all ligatures resolvable; `DinghyIconsTest` PASS.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
git commit -m "feat(theme): register contrast/invert_colors/colors/star/shuffle glyphs"
```

---

## Task 8: `HueSlider` control

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/designsystem/HueSlider.kt`

- [ ] **Step 1: Create the control** (settle-not-stream, mirrors `ColorWheel`; horizontal track):
```kotlin
package works.mees.dinghy.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import works.mees.dinghy.theme.compose.LocalTokens

/**
 * Horizontal hue slider (0..360). The single seed axis (the generator re-derives L/C). Settle-not-stream:
 * [onHandleMove] fires cheaply per drag-frame; [onSettle] fires once on pointer-UP. Track renders the
 * literal hue gradient (data carve-out); the knob outline routes through [LocalTokens]. No looping motion.
 */
@Composable
fun HueSlider(
    hue: Float,
    onHandleMove: (Float) -> Unit,
    onSettle: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val brush = remember {
        Brush.horizontalGradient((0..12).map { i -> Color.hsv((i * 30f) % 360f, 1f, 1f) })
    }
    val onMove by rememberUpdatedState(onHandleMove)
    val onUp by rememberUpdatedState(onSettle)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    var h = (down.position.x / w).coerceIn(0f, 1f) * 360f
                    onMove(h); down.consume()
                    do {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { c ->
                            if (c.pressed && c.positionChanged()) {
                                h = (c.position.x / w).coerceIn(0f, 1f) * 360f
                                onMove(h); c.consume()
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    onUp(h)
                }
            },
    ) {
        val trackH = size.height * 0.5f
        val top = (size.height - trackH) / 2f
        drawRoundRect(brush = brush, topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(size.width, trackH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f))
        val kx = (hue % 360f) / 360f * size.width
        drawCircle(color = Color.hsv(hue % 360f, 1f, 1f), radius = size.height * 0.42f, center = Offset(kx, size.height / 2f))
        drawCircle(color = t.outline, radius = size.height * 0.42f, center = Offset(kx, size.height / 2f),
            style = Stroke(width = 2.dp.toPx()))
    }
}
```

- [ ] **Step 2: Compile**
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r' | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/designsystem/HueSlider.kt
git commit -m "feat(theme): HueSlider control (settle-not-stream horizontal hue track)"
```

---

## Task 9: `HsvSliders` control

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/designsystem/HsvSliders.kt`

- [ ] **Step 1: Create the control** (three labeled tracks H/S/V; each settle-not-stream; reports full HSV):
```kotlin
package works.mees.dinghy.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/**
 * Three settle-not-stream sliders (Hue 0..360, Saturation 0..1, Value 0..1) reporting the FULL picked
 * color. Used by the swatch editor where the stored override is a literal ARGB. Track gradients render
 * the literal colors (data carve-out); labels/knob outline route through [LocalTokens]. No looping motion.
 */
@Composable
fun HsvSliders(
    hue: Float,
    sat: Float,
    value: Float,
    onMove: (Float, Float, Float) -> Unit,
    onSettle: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pure = Color.hsv(((hue % 360f) + 360f) % 360f, 1f, 1f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Track("H", hue / 360f,
            Brush.horizontalGradient((0..12).map { Color.hsv((it * 30f) % 360f, 1f, 1f) }),
            onMove = { onMove(it * 360f, sat, value) }, onSettle = { onSettle(it * 360f, sat, value) })
        Track("S", sat,
            Brush.horizontalGradient(listOf(Color.hsv(((hue % 360f) + 360f) % 360f, 0f, value.coerceAtLeast(0.2f)), pure)),
            onMove = { onMove(hue, it, value) }, onSettle = { onSettle(hue, it, value) })
        Track("V", value,
            Brush.horizontalGradient(listOf(Color.Black, pure)),
            onMove = { onMove(hue, sat, it) }, onSettle = { onSettle(hue, sat, it) })
    }
}

@Composable
private fun Track(
    label: String,
    fraction: Float,
    brush: Brush,
    onMove: (Float) -> Unit,
    onSettle: (Float) -> Unit,
) {
    val t = LocalTokens.current
    val move by rememberUpdatedState(onMove)
    val up by rememberUpdatedState(onSettle)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = DinghyType.caption.toTextStyle(t), color = t.text2,
            modifier = Modifier.width(18.dp).padding(end = 6.dp))
        Canvas(
            Modifier.fillMaxWidth().height(40.dp).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    var f = (down.position.x / w).coerceIn(0f, 1f)
                    move(f); down.consume()
                    do {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { c ->
                            if (c.pressed && c.positionChanged()) {
                                f = (c.position.x / w).coerceIn(0f, 1f); move(f); c.consume()
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    up(f)
                }
            },
        ) {
            val th = size.height * 0.5f
            val top = (size.height - th) / 2f
            drawRoundRect(brush = brush, topLeft = Offset(0f, top), size = Size(size.width, th),
                cornerRadius = CornerRadius(th / 2f))
            val kx = fraction.coerceIn(0f, 1f) * size.width
            drawCircle(color = Color.White, radius = size.height * 0.40f, center = Offset(kx, size.height / 2f))
            drawCircle(color = t.outline, radius = size.height * 0.40f, center = Offset(kx, size.height / 2f),
                style = Stroke(width = 2.dp.toPx()))
        }
    }
}
```

- [ ] **Step 2: Compile** (same command as Task 8 Step 2). Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/designsystem/HsvSliders.kt
git commit -m "feat(theme): HsvSliders control (H/S/V settle-not-stream tracks)"
```

---

# Phase 4 — Strings + the screen rewrite

## Task 10: New `theme_*` strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the new strings** (in the `theme_*` block; keep existing ones — some are reused: `theme_randomize`, `theme_reset`/`theme_keep` not needed; `theme_mode_*`, `theme_status_slot_*`, `theme_status_redundant_cue`, `theme_status_saved_for_colorful` ARE reused):
```xml
    <string name="theme_screen_title">Theme</string>
    <string name="theme_intro">A theme is built from a seed color, a color style, and a set of pool colors.</string>
    <string name="theme_row_dark_light">Dark / Light</string>
    <string name="theme_row_palette_mode">Palette mode</string>
    <string name="theme_row_seed">Seed color</string>
    <string name="theme_row_colors">Theme colors</string>
    <string name="theme_dark_light_focus">Dark and light flip primary surfaces to their contrast; generated colors shift to stay legible.</string>
    <string name="theme_palette_mode_focus">Colorful keeps the full pool. Simple and High Contrast drop everything but the main accent from data surfaces.</string>
    <string name="theme_seed_focus">Pick a hue. The accent and pool are generated from it; lightness is set automatically for contrast.</string>
    <string name="theme_colors_focus">Tap a swatch to set its color. Randomize re-rolls the pool from the seed.</string>
    <string name="theme_revert">Revert</string>
    <string name="theme_save">Save</string>
    <string name="theme_cancel">Cancel</string>
    <string name="theme_swatch_accent">Accent</string>
    <string name="theme_swatch_pool">Pool %1$d</string>
    <string name="cd_theme_saved_swatch">Saved color</string>
    <string name="theme_indicator_custom">Custom</string>
    <string name="theme_indicator_default">Default</string>
```

- [ ] **Step 2: Verify resources compile**
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:processDebugResources --no-daemon" 2>&1 | tr -d '\r' | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(theme): strings for the reworked Theme screen"
```

---

## Task 11: `ThemeScreen` rewrite — host + `ThemeContent` seam + Field rows + simple Focus states

This task replaces the whole file with the host, the stateless seam, the Field list, and the resting / Dark-Light / Palette-Mode Focus states. The Seed (Task 12) and Theme-Colors (Task 13) Focus bodies are stubbed here as `TODO`-free placeholders wired in those tasks. Color-math helpers are migrated from the deleted `ThemeEditorScreen`.

**Files:**
- Modify (full rewrite): `app/src/main/java/works/mees/dinghy/ui/screen/ThemeScreen.kt`
- Delete: `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt` (ordering fix — see below)
- Delete: `app/src/main/java/works/mees/dinghy/preview/ThemeEditorPreviews.kt` (it imports `ThemeEditorContent`)

> **⚠ CODEX PATCHES (apply while writing this file — the base code block below is updated for #1, #2; you MUST hand-apply #3–#6):**
> 1. **Add the missing import** `import androidx.compose.ui.unit.dp` (used by `8.dp` here and more in Task 13).
> 2. **Delete the old files in Step 1** (before compiling) — the migrated `hueToHex`/`hsvToArgbLong`/`seedHexToHue` top-level funcs collide with `ThemeEditorScreen.kt` otherwise.
> 3. **Thread `saved: ThemePrefs.ThemeTuple`** as a new `ThemeContent` param (right after `working`), and pass it down into `ThemeFocus`. It is the discard/compare baseline (the draft is the live edit; `saved` is what Revert/Cancel restore to). The host already passes `saved = saved`.
> 4. **Hoist `editingSwatch`** out of `ThemeFocus` into `ThemeContent` (so the foot Back can step back through it). Declare `var editingSwatch by remember { mutableStateOf<ThemeSwatch?>(null) }` — **`remember`, NOT `rememberSaveable`** (the sealed `ThemeSwatch` is not Parcelable/Serializable → `rememberSaveable` CRASHES). Route row taps through a `selectRow` lambda that also clears `editingSwatch`. Pass `editingSwatch` + a setter into `ThemeFocus`.
> 5. **Contextual foot Back** (replace the `onClick` of the Back `FootAction`):
>    ```kotlin
>    onClick = {
>        when {
>            editingSwatch != null -> editingSwatch = null   // swatch editor → grid (staged edits kept)
>            selected != null -> selected = null             // row detail → resting list
>            else -> { container?.clearThemeDraft(); onBack() } // exit: discard the draft
>        }
>    }
>    ```
>    Keep `backIntent = if (hasDraft) Intent.Danger else Intent.Accent` (red = unsaved).
> 6. The `ThemeFocus` signature gains `saved`, `editingSwatch`, and `onEditSwatch: (ThemeSwatch?) -> Unit`.

- [ ] **Step 1: Delete the retired files FIRST (ordering fix), then check references**
```bash
cd /mnt/e/claude/personal/github/dinghy-display
git rm app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt
git rm app/src/main/java/works/mees/dinghy/preview/ThemeEditorPreviews.kt
grep -rn "ThemeEditorContent\|hueToHex\|hsvToArgbLong\|seedHexToHue\b\|colorToHue\|parseHex\b" app/src/main app/src/test
```
Record every remaining hit — each must be repointed to the new `works.mees.dinghy.ui.screen` helpers (Task 15 adds the new previews). If a test references `hueToHex`/`hsvToArgbLong`, keep those helper names identical when migrating.

- [ ] **Step 2: Write the new `ThemeScreen.kt`** (host + seam + rows + simple Focus states + migrated helpers). The Seed/Colors Focus bodies render a placeholder `Box` here:
```kotlin
package works.mees.dinghy.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.components.ToggleRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.PaletteMode
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.ThemePrefs
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/** Which Theme row is selected (null = resting overview). */
enum class ThemeRow { DarkLight, PaletteMode, Seed, Colors }

/**
 * The **Theme** screen — lists-first Focus/Field rebuild (supersedes the retired ThemeEditorScreen).
 * Field = 4 rows (Dark/Light inline toggle; Palette Mode, Seed, Theme Colors selectors); Focus swaps
 * by [ThemeRow]. Live preview via the AppContainer draft overlay; Save commits, Back/Revert discards.
 */
@Composable
fun ThemeScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeProfile by container.activeProfile.collectAsStateWithLifecycle(null)
    val hasActive = activeProfile != null
    val saved by container.activeThemeTuple.collectAsStateWithLifecycle(ThemePrefs.TUPLE_DEFAULT)
    val draft by container.themeDraft.collectAsStateWithLifecycle(null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val isPrinting = printerState.printState == PrintState.Printing || printerState.printState == PrintState.Paused

    // The tuple the editor edits: the live draft if one is open, else the saved tuple.
    val working = draft ?: saved

    ThemeContent(
        working = working,
        saved = saved,
        hasDraft = draft != null,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onBack = onBack,
        // immediate (no draft) controls:
        // Codex fix: effectiveTokens NEVER reads themeResolver.tokens, and a live draft wins
        // unconditionally — so update the draft too (if one is open) AND persist immediately.
        onDarkToggle = { d -> container.updateThemeDraft { it?.copy(dark = d) }; container.setActiveDark(hasActive, d) },
        onPaletteMode = { m -> container.updateThemeDraft { it?.copy(paletteMode = m) }; container.setActiveMode(hasActive, m) },
        // draft lifecycle (Seed/Colors editors call these — wired in Tasks 12/13):
        container = container,
        hasActive = hasActive,
        modifier = modifier,
    )
}

@Composable
internal fun ThemeContent(
    working: ThemePrefs.ThemeTuple,
    saved: ThemePrefs.ThemeTuple,          // Codex fix #3: discard/compare baseline (Revert/Cancel restore to this)
    hasDraft: Boolean,
    isPrinting: Boolean,
    onEmergencyStop: (() -> Unit)?,
    onBack: () -> Unit,
    onDarkToggle: (Boolean) -> Unit,
    onPaletteMode: (String) -> Unit,
    container: AppContainer?,          // null in previews
    hasActive: Boolean,
    modifier: Modifier = Modifier,
    initialSelected: ThemeRow? = null,
) {
    var selected by rememberSaveable { mutableStateOf(initialSelected) }
    // Codex fix #4: hoisted here (so the foot Back can step through it). `remember` NOT `rememberSaveable`
    // — the sealed `ThemeSwatch` is not Parcelable/Serializable, so rememberSaveable would crash.
    var editingSwatch by remember { mutableStateOf<ThemeSwatch?>(null) }
    // Selecting a different row always closes any open swatch editor.
    val selectRow: (ThemeRow?) -> Unit = { editingSwatch = null; selected = it }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        // Back is RED while a draft is unsaved (the "you haven't saved" cue, R5); accent otherwise.
        val backIntent = if (hasDraft) Intent.Danger else Intent.Accent
        ScreenScaffold(
            focus = {
                ThemeFocus(
                    selected = selected, working = working, saved = saved, uDp = grid.uDp,
                    isPrinting = isPrinting, onEmergencyStop = onEmergencyStop,
                    onDarkToggle = onDarkToggle, onPaletteMode = onPaletteMode,
                    container = container, hasActive = hasActive,
                    editingSwatch = editingSwatch, onEditSwatch = { editingSwatch = it },
                    onCloseEditor = { editingSwatch = null; selected = null },
                    modifier = Modifier.fillMaxSize(),
                )
            },
            field = {
                ListBlock(Modifier.weight(1f)) {
                    item {
                        // Dark/Light is the ONE row with an inline control (owner law).
                        ToggleRow(
                            label = stringResource(R.string.theme_row_dark_light),
                            checked = working.dark,
                            onToggle = onDarkToggle,
                            uDp = grid.uDp,
                        )
                    }
                    item { ThemeSelectorRow(ThemeRow.PaletteMode, selected, DinghyIcons.InvertColors,
                        stringResource(R.string.theme_row_palette_mode), paletteModeLabel(working.paletteMode), grid.uDp, selectRow) }
                    item { ThemeSelectorRow(ThemeRow.Seed, selected, DinghyIcons.Colors,
                        stringResource(R.string.theme_row_seed), "", grid.uDp, selectRow) }
                    item { ThemeSelectorRow(ThemeRow.Colors, selected, DinghyIcons.Palette,
                        stringResource(R.string.theme_row_colors),
                        if (hasCustomColors(working)) stringResource(R.string.theme_indicator_custom)
                        else stringResource(R.string.theme_indicator_default), grid.uDp, selectRow) }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            icon = DinghyIcons.Back,
                            onClick = {
                                // Codex fix #5: contextual step-back (swatch → grid → list → exit-with-discard).
                                when {
                                    editingSwatch != null -> editingSwatch = null
                                    selected != null -> selected = null
                                    else -> { container?.clearThemeDraft(); onBack() }
                                }
                            },
                            intent = backIntent,
                            contentDescription = stringResource(R.string.cd_back),
                        ),
                    ),
                )
            },
        )
    }
}

@Composable
private fun ThemeSelectorRow(
    row: ThemeRow, selected: ThemeRow?, icon: DinghyIcon, label: String, indicator: String, uDp: Dp,
    onSelect: (ThemeRow?) -> Unit,
) {
    val t = LocalTokens.current
    ListRow(
        selected = selected == row,
        onClick = { onSelect(if (selected == row) null else row) },
        uDp = uDp,
        leadingContent = { ListRowIcon(icon = icon, uDp = uDp, tint = t.text) },
        trailingContent = if (indicator.isBlank()) null else {
            { Text(indicator, color = t.text2, style = DinghyType.caption.toTextStyle(t)) }
        },
    ) { ListRowLabel(label) }
}

@Composable
private fun ThemeFocus(
    selected: ThemeRow?, working: ThemePrefs.ThemeTuple, saved: ThemePrefs.ThemeTuple, uDp: Dp,
    isPrinting: Boolean, onEmergencyStop: (() -> Unit)?,
    onDarkToggle: (Boolean) -> Unit, onPaletteMode: (String) -> Unit,
    container: AppContainer?, hasActive: Boolean,
    editingSwatch: ThemeSwatch?, onEditSwatch: (ThemeSwatch?) -> Unit, onCloseEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    @Composable
    fun frame(title: String, icon: DinghyIcon, body: @Composable ColumnScope.() -> Unit) {
        FocusFrame(title = title, icon = icon, uDp = uDp, modifier = modifier,
            isPrinting = isPrinting, onEmergencyStop = onEmergencyStop, onPanic = onEmergencyStop, content = body)
    }

    @Composable
    fun explainer(text: String) {
        Box(Modifier.fillMaxWidth().fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = t.text2, style = DinghyType.body.toTextStyle(t))
        }
    }

    when (selected) {
        null -> frame(stringResource(R.string.theme_screen_title), DinghyIcons.Palette) {
            explainer(stringResource(R.string.theme_intro))
        }
        ThemeRow.DarkLight -> frame(stringResource(R.string.theme_row_dark_light), DinghyIcons.Contrast) {
            explainer(stringResource(R.string.theme_dark_light_focus))
        }
        ThemeRow.PaletteMode -> frame(stringResource(R.string.theme_row_palette_mode), DinghyIcons.InvertColors) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.theme_palette_mode_focus), color = t.text2, style = DinghyType.body.toTextStyle(t))
            }
            PaletteModeSegment(working.paletteMode, onPaletteMode, uDp)
        }
        ThemeRow.Seed -> frame(stringResource(R.string.theme_row_seed), DinghyIcons.Colors) {
            // Wired in Task 12.
            explainer(stringResource(R.string.theme_seed_focus))
        }
        ThemeRow.Colors -> frame(stringResource(R.string.theme_row_colors), DinghyIcons.Palette) {
            // Wired in Task 13.
            explainer(stringResource(R.string.theme_colors_focus))
        }
    }
}

/** The 3-way palette-mode segment (lives in the Focus, applies live + persists immediately). */
@Composable
private fun PaletteModeSegment(current: String, onPick: (String) -> Unit, uDp: Dp) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((mode, labelRes) in PALETTE_MODES) {
            OutlinedControl(
                label = stringResource(labelRes), onClick = { onPick(mode) },
                modifier = Modifier.weight(1f),
                intent = if (current == mode) Intent.Accent else Intent.Neutral,
            )
        }
    }
}

private val PALETTE_MODES = listOf(
    ThemeResolver.MODE_COLORFUL to R.string.theme_mode_colorful,
    ThemeResolver.MODE_SIMPLE to R.string.theme_mode_simple,
    ThemeResolver.MODE_HIGH_CONTRAST to R.string.theme_mode_high_contrast,
)

private fun paletteModeLabel(mode: String): String = when (mode) {
    ThemeResolver.MODE_SIMPLE -> "Simple"
    ThemeResolver.MODE_HIGH_CONTRAST -> "High contrast"
    else -> "Colorful"
}

/** True if the working tuple carries any pool/status/accent override (drives the "Custom" indicator). */
internal fun hasCustomColors(t: ThemePrefs.ThemeTuple): Boolean =
    t.poolOverrides.isNotEmpty() || t.statusOverrides.isNotEmpty() || t.accentOverride != null

// ---- migrated color-math helpers (from the retired ThemeEditorScreen) ----
internal fun hueToHex(hue: Float): String {
    val argb = androidx.compose.ui.graphics.Color.hsv(((hue % 360f) + 360f) % 360f, 1f, 1f).let {
        android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt())
    }
    return "#%06X".format(java.util.Locale.US, argb and 0xFFFFFF)
}
internal fun hsvToArgbLong(hue: Float, sat: Float, value: Float): Long {
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(((hue % 360f) + 360f) % 360f, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f)))
    return argb.toLong() and 0xFFFFFFFFL
}
internal fun seedHexToHue(hex: String?): Float {
    val rgb = (hex ?: "#000000").removePrefix("#").take(6).toIntOrNull(16) ?: 0
    val hsv = FloatArray(3); android.graphics.Color.colorToHSV(0xFF000000.toInt() or rgb, hsv); return hsv[0]
}
internal fun argbLongToHsv(argb: Long): FloatArray {
    val hsv = FloatArray(3); android.graphics.Color.colorToHSV(argb.toInt(), hsv); return hsv
}
```

> NOTE: `PaletteMode` import is retained for Task 13's status-mode gating note; if Kotlin flags it unused at this task, drop the import and re-add in Task 13.

- [ ] **Step 3: Compile** (Task 8 Step 2 command). The old `ThemeEditorScreen.kt` + `ThemeEditorPreviews.kt` were already `git rm`'d in Step 1, so the migrated helpers no longer collide. Fix any reference the Step-1 grep flagged (e.g. a test importing `hueToHex` from the old file → repoint to `works.mees.dinghy.ui.screen.hueToHex`). Expected: BUILD SUCCESSFUL. (No previews exist until Task 15 — that's fine; previews aren't compiled into the app path the same way and their absence doesn't break the build.)

- [ ] **Step 4: Commit** (includes the two deletions from Step 1)
```bash
git add -A app/src/main/java/works/mees/dinghy/ui/screen app/src/main/java/works/mees/dinghy/preview
git commit -m "feat(theme): lists-first ThemeScreen host + Field rows; retire ThemeEditorScreen"
```

---

## Task 12: Seed Color Focus — hue slider + Save (draft-wired)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/ThemeScreen.kt`

- [ ] **Step 1: Replace the `ThemeRow.Seed` branch** in `ThemeFocus` with the live editor:
```kotlin
        ThemeRow.Seed -> frame(stringResource(R.string.theme_row_seed), DinghyIcons.Colors) {
            val hue = seedHexToHue(working.seedHex)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                works.mees.dinghy.designsystem.HueSlider(
                    hue = hue,
                    onHandleMove = { h ->
                        // cheap live preview: update the draft seed (begins a draft from the saved tuple)
                        container?.updateThemeDraft { (it ?: working).copy(seedHex = hueToHex(h)) }
                    },
                    onSettle = { h ->
                        container?.updateThemeDraft { (it ?: working).copy(seedHex = hueToHex(h)) }
                    },
                )
            }
            OutlinedControl(
                label = stringResource(R.string.theme_save),
                onClick = { container?.commitThemeDraft(hasActive); onCloseEditor() },
                modifier = Modifier.fillMaxWidth(), intent = Intent.Go,
            )
        }
```

> The Focus outline-as-readout (the spec's "outline shows the resulting accent") is delivered by the live preview itself: the whole app — including this Focus's `FocusFrame` — rethemes to the draft accent as you drag, so the frame border (an accent-derived token) *is* the readout. No extra wiring needed.

- [ ] **Step 2: Compile** (Task 8 Step 2 command). Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/ui/screen/ThemeScreen.kt
git commit -m "feat(theme): Seed Color Focus — live hue slider + Save (draft)"
```

---

## Task 13: Theme Colors Focus — grid + swatch editor + two-level staging

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/ThemeScreen.kt`

> **⚠ CODEX PATCHES (already folded into the code below — do NOT re-add the old versions):**
> - `editingSwatch` is the HOISTED `ThemeFocus` PARAM (Task 11 fix #4), not local state — use `editingSwatch`/`onEditSwatch(...)`, never a local `rememberSaveable`.
> - Cancel restores from `saved` (the param), NOT `working` (which is the live draft).
> - Status grid swatches render the LITERAL draft override (`statusFill`), because `t.stop/heat/go` are mode-gated and would hide an override in Simple/HighContrast.
> - `swatchTitle` is `@Composable` (uses `stringResource`).

- [ ] **Step 1: Add the grid/editor composables (state already hoisted in Task 11).**

(a) Define the swatch identity type at file scope:
```kotlin
/** The 8 editable grid slots: 4 pool indices + accent + 3 status. */
sealed interface ThemeSwatch {
    data class Pool(val index: Int) : ThemeSwatch
    data object Accent : ThemeSwatch
    data class Status(val slot: works.mees.dinghy.theme.StatusSlot) : ThemeSwatch
}
```

(b) Replace the `ThemeRow.Colors -> frame(...)` branch with:
```kotlin
        ThemeRow.Colors -> {
            val ed = editingSwatch
            if (ed == null) {
                frame(stringResource(R.string.theme_row_colors), DinghyIcons.Palette) {
                    val tk = LocalTokens.current
                    ThemeSwatchGrid(tk, working, onTap = { onEditSwatch(it) })
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedControl(stringResource(R.string.theme_randomize),
                            onClick = {
                                container?.updateThemeDraft {
                                    (it ?: working).copy(poolOverrides = emptyMap(), statusOverrides = emptyMap(),
                                        accentOverride = null, poolShift = nextShift(working.poolShift))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(), intent = Intent.Warn,
                            icon = DinghyIcons.Shuffle)
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedControl(stringResource(R.string.theme_revert),
                                onClick = { container?.clearThemeDraft() },
                                modifier = Modifier.weight(1f), intent = Intent.Warn, icon = DinghyIcons.Revert)
                            OutlinedControl(stringResource(R.string.theme_save),
                                onClick = { container?.commitThemeDraft(hasActive); onCloseEditor() },
                                modifier = Modifier.weight(1f), intent = Intent.Go, icon = DinghyIcons.Save)
                        }
                    }
                }
            } else {
                ThemeSwatchEditor(
                    swatch = ed, saved = saved, uDp = uDp, modifier = modifier,
                    isPrinting = isPrinting, onEmergencyStop = onEmergencyStop,
                    onMovePreview = { argb -> container?.updateThemeDraft { applySwatch(it ?: working, ed, argb) } },
                    onSave = { onEditSwatch(null) },          // already staged into the draft live
                    onCancel = {
                        // Codex fix: discard this swatch's edit by restoring the SAVED value (not `working`).
                        container?.updateThemeDraft { restoreSwatch(it ?: working, ed, saved) }
                        onEditSwatch(null)
                    },
                )
            }
        }
```

(c) Add the grid, editor, and staging helpers at file scope:
```kotlin
@Composable
private fun ColumnScope.ThemeSwatchGrid(
    t: works.mees.dinghy.theme.ThemeTokens, working: ThemePrefs.ThemeTuple, onTap: (ThemeSwatch) -> Unit,
) {
    // 1U-capped wide cells, 4 columns × 2 rows. Pool = number; intent = symbol. No captions.
    // Pool + accent come from the baked tokens (overrides already applied in all modes); STATUS uses the
    // literal draft override (statusFill) because t.stop/heat/go are mode-gated (Codex fix).
    val uDp = works.mees.dinghy.designsystem.layout.LocalUnitDp.current ?: 64.dp
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                t.pool.take(4).forEachIndexed { i, c -> SwatchCell(c, uDp, Modifier.weight(1f), number = i + 1) { onTap(ThemeSwatch.Pool(i)) } }
            }
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwatchCell(t.accent, uDp, Modifier.weight(1f), symbol = DinghyIcons.Star) { onTap(ThemeSwatch.Accent) }
                SwatchCell(statusFill(works.mees.dinghy.theme.StatusSlot.Stop, working, t), uDp, Modifier.weight(1f), symbol = DinghyIcons.StatusStop) { onTap(ThemeSwatch.Status(works.mees.dinghy.theme.StatusSlot.Stop)) }
                SwatchCell(statusFill(works.mees.dinghy.theme.StatusSlot.Caution, working, t), uDp, Modifier.weight(1f), symbol = DinghyIcons.Warning) { onTap(ThemeSwatch.Status(works.mees.dinghy.theme.StatusSlot.Caution)) }
                SwatchCell(statusFill(works.mees.dinghy.theme.StatusSlot.Go, working, t), uDp, Modifier.weight(1f), symbol = DinghyIcons.CheckCircle) { onTap(ThemeSwatch.Status(works.mees.dinghy.theme.StatusSlot.Go)) }
            }
        }
    }
}

/** Literal status fill for the grid: the draft override if set, else the (mode-gated) baked token. */
private fun statusFill(slot: works.mees.dinghy.theme.StatusSlot, working: ThemePrefs.ThemeTuple, t: works.mees.dinghy.theme.ThemeTokens): androidx.compose.ui.graphics.Color =
    working.statusOverrides[slot.key]?.toComposeColor() ?: when (slot) {
        works.mees.dinghy.theme.StatusSlot.Stop -> t.stop
        works.mees.dinghy.theme.StatusSlot.Caution -> t.heat
        works.mees.dinghy.theme.StatusSlot.Go -> t.go
    }

@Composable
private fun SwatchCell(
    fill: androidx.compose.ui.graphics.Color, uDp: Dp, modifier: Modifier,
    number: Int? = null, symbol: DinghyIcon? = null, onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(t.rCtrl)
    val ink = if (fill.luminance() > 0.5f) androidx.compose.ui.graphics.Color(0xFF101010) else androidx.compose.ui.graphics.Color(0xFFF5F5F5)
    Box(modifier.height(uDp).clip(shape).background(fill)
        .border(androidx.compose.foundation.BorderStroke(2.dp, t.outline), shape)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (number != null) Text(number.toString(), color = ink, style = DinghyType.dataInline.toTextStyle(t))
        if (symbol != null) works.mees.dinghy.designsystem.icons.DinghyIconView(icon = symbol, tint = ink,
            sizeDp = (uDp * 0.45f), contentDescription = null)
    }
}

@Composable
private fun ThemeSwatchEditor(
    swatch: ThemeSwatch, saved: ThemePrefs.ThemeTuple, uDp: Dp, modifier: Modifier,
    isPrinting: Boolean, onEmergencyStop: (() -> Unit)?,
    onMovePreview: (Long) -> Unit, onSave: () -> Unit, onCancel: () -> Unit,
) {
    val t = LocalTokens.current
    // Codex fix: the header/compare baseline is the SAVED value — bake the saved tuple once (not `working`,
    // which is the live draft). The live in-progress pick is shown by the whole-app preview.
    val savedTokens = remember(saved) { works.mees.dinghy.theme.ThemeResolver().bake(saved) }
    val savedArgb = savedSwatchArgb(swatch, saved, savedTokens)
    val hsv = argbLongToHsv(savedArgb)
    var h by rememberSaveable(swatch) { mutableStateOf(hsv[0]) }
    var s by rememberSaveable(swatch) { mutableStateOf(hsv[1]) }
    var v by rememberSaveable(swatch) { mutableStateOf(hsv[2]) }
    FocusFrame(
        title = swatchTitle(swatch), icon = swatchIcon(swatch), uDp = uDp, modifier = modifier,
        isPrinting = isPrinting, onEmergencyStop = onEmergencyStop, onPanic = onEmergencyStop,
        // header shows the SAVED value as a trailing swatch glyph substitute — rendered via edge color:
        edge = works.mees.dinghy.designsystem.components.FocusEdge.Data(androidx.compose.ui.graphics.Color(savedArgb.toInt())),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            works.mees.dinghy.designsystem.HsvSliders(
                hue = h, sat = s, value = v,
                onMove = { nh, ns, nv -> h = nh; s = ns; v = nv; onMovePreview(hsvToArgbLong(nh, ns, nv)) },
                onSettle = { nh, ns, nv -> h = nh; s = ns; v = nv; onMovePreview(hsvToArgbLong(nh, ns, nv)) },
            )
        }
        if (swatch is ThemeSwatch.Status && t.mode != PaletteMode.Colorful) {
            val modeLabel = stringResource(when (t.mode) {
                PaletteMode.Simple -> R.string.theme_mode_simple
                PaletteMode.HighContrast -> R.string.theme_mode_high_contrast
                else -> R.string.theme_mode_colorful
            })
            Text(stringResource(R.string.theme_status_saved_for_colorful, modeLabel),
                color = t.text3, style = DinghyType.caption.toTextStyle(t))
        }
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedControl(stringResource(R.string.theme_cancel), onClick = onCancel, modifier = Modifier.weight(1f), intent = Intent.Danger)
            OutlinedControl(stringResource(R.string.theme_save), onClick = onSave, modifier = Modifier.weight(1f), intent = Intent.Go)
        }
    }
}

// ---- swatch staging helpers (pure) ----
private fun nextShift(current: Int): Int = ((current + 137) % 360)   // deterministic re-roll; varied per tap via current

private fun applySwatch(tuple: ThemePrefs.ThemeTuple, sw: ThemeSwatch, argb: Long): ThemePrefs.ThemeTuple = when (sw) {
    is ThemeSwatch.Pool -> tuple.copy(poolOverrides = tuple.poolOverrides + (sw.index to argb))
    ThemeSwatch.Accent -> tuple.copy(accentOverride = argb)
    is ThemeSwatch.Status -> tuple.copy(statusOverrides = tuple.statusOverrides + (sw.slot.key to argb))
}

private fun restoreSwatch(tuple: ThemePrefs.ThemeTuple, sw: ThemeSwatch, saved: ThemePrefs.ThemeTuple): ThemePrefs.ThemeTuple = when (sw) {
    is ThemeSwatch.Pool -> tuple.copy(poolOverrides = saved.poolOverrides[sw.index]
        ?.let { tuple.poolOverrides + (sw.index to it) } ?: (tuple.poolOverrides - sw.index))
    ThemeSwatch.Accent -> tuple.copy(accentOverride = saved.accentOverride)
    is ThemeSwatch.Status -> tuple.copy(statusOverrides = saved.statusOverrides[sw.slot.key]
        ?.let { tuple.statusOverrides + (sw.slot.key to it) } ?: (tuple.statusOverrides - sw.slot.key))
}

private fun savedSwatchArgb(sw: ThemeSwatch, working: ThemePrefs.ThemeTuple, t: works.mees.dinghy.theme.ThemeTokens): Long = when (sw) {
    is ThemeSwatch.Pool -> working.poolOverrides[sw.index] ?: (t.pool.getOrNull(sw.index) ?: t.accent).toArgb().toLong() and 0xFFFFFFFFL
    ThemeSwatch.Accent -> working.accentOverride ?: t.accent.toArgb().toLong() and 0xFFFFFFFFL
    is ThemeSwatch.Status -> working.statusOverrides[sw.slot.key]
        ?: when (sw.slot) { works.mees.dinghy.theme.StatusSlot.Stop -> t.stop; works.mees.dinghy.theme.StatusSlot.Caution -> t.heat; works.mees.dinghy.theme.StatusSlot.Go -> t.go }.toArgb().toLong() and 0xFFFFFFFFL
}

@Composable
private fun swatchTitle(sw: ThemeSwatch): String = when (sw) {
    is ThemeSwatch.Pool -> stringResource(R.string.theme_swatch_pool, sw.index + 1)
    ThemeSwatch.Accent -> stringResource(R.string.theme_swatch_accent)
    is ThemeSwatch.Status -> stringResource(when (sw.slot) {
        works.mees.dinghy.theme.StatusSlot.Stop -> R.string.theme_status_slot_stop
        works.mees.dinghy.theme.StatusSlot.Caution -> R.string.theme_status_slot_caution
        works.mees.dinghy.theme.StatusSlot.Go -> R.string.theme_status_slot_go
    })
}
private fun swatchIcon(sw: ThemeSwatch): DinghyIcon = when (sw) {
    is ThemeSwatch.Pool -> DinghyIcons.Palette
    ThemeSwatch.Accent -> DinghyIcons.Star
    is ThemeSwatch.Status -> when (sw.slot) {
        works.mees.dinghy.theme.StatusSlot.Stop -> DinghyIcons.StatusStop
        works.mees.dinghy.theme.StatusSlot.Caution -> DinghyIcons.Warning
        works.mees.dinghy.theme.StatusSlot.Go -> DinghyIcons.CheckCircle
    }
}
```

(d) Add imports used above: `androidx.compose.foundation.background`, `androidx.compose.foundation.BorderStroke`, `androidx.compose.foundation.border`, `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.height`, `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.ui.draw.clip`, `androidx.compose.ui.graphics.luminance`, `androidx.compose.ui.graphics.toArgb`, `works.mees.dinghy.designsystem.layout.LocalUnitDp`, `works.mees.dinghy.designsystem.components.FocusEdge`, `androidx.compose.foundation.layout.fillMaxSize`, `works.mees.dinghy.theme.ThemeResolver` (for `bake(saved)`). (`remember` was already added in Task 11; `toComposeColor` is a same-package top-level fun — no import.)

> `swatchTitle` is `@Composable` and uses `stringResource` (Codex i18n note resolved). `statusFill` returns a fully-qualified `androidx.compose.ui.graphics.Color`.

> The spec's "header swatch = saved value, outline = live pick": delivered via `FocusEdge.Data(savedColor)` for the saved value border PLUS the live full-app preview for the in-progress pick. If on-device the owner wants the saved swatch as a literal header glyph instead of the edge, switch to `FocusFrame`'s `trailingActionIcon` slot or a custom header swatch in a follow-up (flagged for UAT).

- [ ] **Step 2: Compile** (Task 8 Step 2 command). Resolve any import/`@Composable`-context errors (notably the `swatchTitle` i18n note). Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/ui/screen/ThemeScreen.kt
git commit -m "feat(theme): Theme Colors grid + swatch editor with two-level Save/Revert staging"
```

---

# Phase 5 — Shell, previews, cleanup, verification

## Task 14: AppShell e-stop migration (Theme becomes screen-owned)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`

- [ ] **Step 1: Move Theme into `screenOwnsEstop`.** Add this line inside the `screenOwnsEstop` `&& ( … )` block:
```kotlin
            estopDest.isRoute<NavDest.Theme>() ||
```

- [ ] **Step 2: Remove Theme from the shell-fallback** — change `screenUsesShellEstop` to Webcam-only:
```kotlin
        val screenUsesShellEstop = estopDest != null && !screenOwnsEstop && (
            estopDest.isRoute<NavDest.Webcam>()
        )
```
Update the governing comment (lines ~941–952) to name **Webcam** as the sole shell-fallback (Theme now owns its e-stop via FocusFrame).

- [ ] **Step 3: Compile** (Task 8 Step 2 command). Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
git commit -m "feat(theme): Theme owns its e-stop (FocusFrame) — drop shell FloatingEStop fallback"
```

---

## Task 15: New `@Preview` matrix (old screen + previews already deleted in Task 11)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/preview/ThemePreviews.kt`

> The old `ThemeEditorScreen.kt` + `ThemeEditorPreviews.kt` were `git rm`'d in Task 11 Step 1. This task only ADDS the new preview matrix targeting `ThemeContent`.

- [ ] **Step 1: Confirm the preview helpers + landscape device constant**
```bash
grep -rn "fun PreviewBox\|NEXUS7_PORTRAIT\|val NEXUS7\b\|val colorfulDark\b\|val colorfulLight\b" app/src/main/java/works/mees/dinghy/preview | head
```
Codex confirms `PreviewBox`, `NEXUS7_PORTRAIT`, `colorfulDark`, `colorfulLight` are real; the LANDSCAPE constant is `NEXUS7` (NOT `NEXUS7_LAND`).

- [ ] **Step 2: Create `ThemePreviews.kt`.** Note `ThemeContent` now requires BOTH `working` AND `saved` (pass the same tuple in previews; `container = null` makes every `container?.` callback no-op):
```kotlin
package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.dinghy.theme.ThemePrefs
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.ui.screen.ThemeContent
import works.mees.dinghy.ui.screen.ThemeRow

private val DEFAULT = ThemePrefs.TUPLE_DEFAULT
private val SIMPLE = ThemePrefs.TUPLE_DEFAULT.copy(paletteMode = ThemeResolver.MODE_SIMPLE)

@Preview(name = "Theme · resting · dark", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable private fun ThemeRestingDark() = PreviewBox(colorfulDark) {
    ThemeContent(working = DEFAULT, saved = DEFAULT, hasDraft = false, isPrinting = false,
        onEmergencyStop = null, onBack = {}, onDarkToggle = {}, onPaletteMode = {},
        container = null, hasActive = false, initialSelected = null)
}

@Preview(name = "Theme · palette · dark", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable private fun ThemePaletteDark() = PreviewBox(colorfulDark) {
    ThemeContent(working = DEFAULT, saved = DEFAULT, hasDraft = false, isPrinting = false,
        onEmergencyStop = null, onBack = {}, onDarkToggle = {}, onPaletteMode = {},
        container = null, hasActive = false, initialSelected = ThemeRow.PaletteMode)
}

@Preview(name = "Theme · colors · light", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable private fun ThemeColorsLight() = PreviewBox(colorfulLight) {
    val t = DEFAULT.copy(dark = false)
    ThemeContent(working = t, saved = t, hasDraft = false, isPrinting = false,
        onEmergencyStop = null, onBack = {}, onDarkToggle = {}, onPaletteMode = {},
        container = null, hasActive = false, initialSelected = ThemeRow.Colors)
}

@Preview(name = "Theme · colors · simple", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable private fun ThemeColorsSimple() = PreviewBox(colorfulDark) {
    ThemeContent(working = SIMPLE, saved = SIMPLE, hasDraft = false, isPrinting = false,
        onEmergencyStop = null, onBack = {}, onDarkToggle = {}, onPaletteMode = {},
        container = null, hasActive = false, initialSelected = ThemeRow.Colors)
}

@Preview(name = "Theme · seed · dirty(red back)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable private fun ThemeSeedDirty() = PreviewBox(colorfulDark) {
    ThemeContent(working = DEFAULT, saved = DEFAULT, hasDraft = true, isPrinting = false,
        onEmergencyStop = null, onBack = {}, onDarkToggle = {}, onPaletteMode = {},
        container = null, hasActive = false, initialSelected = ThemeRow.Seed)
}

@Preview(name = "Theme · resting · landscape", device = NEXUS7, showBackground = true)
@Composable private fun ThemeRestingLandscape() = PreviewBox(colorfulDark) {
    ThemeContent(working = DEFAULT, saved = DEFAULT, hasDraft = false, isPrinting = false,
        onEmergencyStop = null, onBack = {}, onDarkToggle = {}, onPaletteMode = {},
        container = null, hasActive = false, initialSelected = ThemeRow.Colors)
}
```
> The grid/seed/swatch editor branches all guard with `container?.`, so `container = null` previews render statically (no live preview, no crash). If the grep shows different seed-tuple constant names, swap them in.

- [ ] **Step 3: Compile + the full unit suite** (proves Task 11's deletion repointed every reference):
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r' | tail -30
```
Expected: BUILD SUCCESSFUL + tests PASS. Fix any lingering `ThemeEditorContent`/old-helper import the compiler flags.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/preview/ThemePreviews.kt
git commit -m "feat(theme): new ThemeContent @Preview matrix (portrait + landscape + modes)"
```

---

## Task 16: Full build, font/conformance gates, on-device UAT

**Files:** none (verification only).

- [ ] **Step 1: Full release-path build + the whole unit suite + conformance tests**
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r' | tail -40
cd /mnt/e/claude/personal/github/dinghy-display && python3 tools/verify_ligatures.py 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL; `FontConformanceTest`, `DinghyIconsTest`, `PaletteGoldenTest`, `ThemeResolverBakeTest`, `AccentOverrideTest`, `ThemeDraftTest` all PASS; verifier clean.

- [ ] **Step 2: Install on BOTH devices** ([[dinghy-test-devices]] — push the matching ABI slice to flox + moto):
```bash
# debug build is auto-signed; install to both
E:\Android\Sdk\platform-tools\adb.exe -s 0a64b42e install -r app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
E:\Android\Sdk\platform-tools\adb.exe -s ZY22LBDRM9 install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```
(Verify APK mtime is newer than the last commit before installing — [[dinghy-stale-apk-uat-gate]].)

- [ ] **Step 3: Owner UAT checklist** (Matthew drives; Claude observes — [[dinghy-display-ondevice-iteration]]):
  - Field: dark/light toggle flips the whole app live and persists; palette-mode selector + Focus segment switch modes.
  - Seed: hue slider rethemes live; the Focus frame border (accent) tracks the hue; **Save** persists; **Back while dragging shows RED** and discards.
  - Theme Colors: grid renders 1U wide swatches (numbers + star/stop/caution/check_circle); tap → 3-slider editor; live preview; **swatch Save** stages, **Cancel (red)** reverts that swatch; **Randomize** re-rolls; grid **Revert (amber)** discards all; grid **Save (green)** persists.
  - Accent swatch edits change the app accent in Colorful AND Simple/HighContrast.
  - Per-printer: switching the active printer shows that printer's saved theme; edits are per-printer.
  - Portrait + landscape both correct; 5U/smallest-Focus no clipping.
  - E-stop: while printing, every Theme Focus header morphs to the e-stop (no double e-stop, no missing e-stop).

- [ ] **Step 4: Commit any UAT fixes, then finalize** (branch/PR per `superpowers:finishing-a-development-branch`).

---

## Self-review notes (author)

- **Spec coverage:** Field rows + inline toggle (T11) · resting/dark/palette Focus (T11) · seed hue slider + Save (T12) · grid + swatch editor + two-level staging + Randomize/Revert/Save intents (T13) · live-preview draft via effectiveTokens (T6) · accent override engine+storage+family (T1–T4) · accent intent (T5) · icons (T7) · sliders (T8–T9) · 5U one-row actions (T13) · AppShell e-stop (T14) · Go-glyph exception = `CheckCircle` (T13) · preview matrix (T15) · Back-red-on-dirty (T11). All present.
- **Known follow-ups flagged for UAT, not blockers:** header "saved swatch" rendered via `FocusEdge.Data` (may switch to a literal header glyph if the owner prefers); `nextShift` is a deterministic re-roll (swap for `Random` seeded off a per-tap value if the owner wants true randomness — note Date/Random caveats don't apply on-device).
- **Type consistency:** `ThemeSwatch` sealed type, `applySwatch`/`restoreSwatch`/`savedSwatchArgb`, `commitThemeDraft(active)`, `updateThemeDraft`, `setActiveAccentOverride(active, Long?)`, `accentOverride: Long?` / `accentOverrideArgb: Long?` used consistently across tasks.
