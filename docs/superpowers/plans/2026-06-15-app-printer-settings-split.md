# App / Printer Settings Split — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the System/Settings cluster into two clean doors — **App Settings** (device/user
prefs) and **Printer Settings** (per-printer) — rebuilt on the Focus/Field/Foot + FocusFrame laws,
folding the standalone Theme screen and Printers manager into Printer Settings, and moving font size
from per-printer to app-global.

**Architecture:** One app-global `FontScalePrefs` DataStore feeds font scale into the single
canonical `AppContainer.activeThemeTuple` flow via a `combine` (covers both theme-resolution paths at
once). Three new `NavDest` routes replace `Settings`+`Devices`. New `AppSettingsScreen` and
`PrinterSettingsScreen`; `PrintersScreen` is repurposed as a `ManagePrinters` sub-screen with a new
Rename affordance; `PrinterConnectionEditor` is extracted for reuse. The System hub shrinks to three
rows.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation-Compose (`@Serializable data object` routes),
DataStore (Preferences), kotlinx.serialization, coroutines/Flow. Build Windows-side via
`E:\Android\gw.bat` (see CLAUDE.md — `./gradlew` does NOT run from WSL).

**Spec:** `docs/superpowers/specs/2026-06-15-app-printer-settings-split-design.md` (Codex-reviewed).

---

## Conventions for every task

- **Build/test command** (run from repo root in WSL bash):
  ```bash
  /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <gradle args> --no-daemon" 2>&1 | tr -d '\r'
  ```
  The process exit code is authoritative. Guard long runs with `timeout` + taskkill per
  [[dinghy-display-gradle-hang-interop]].
- **Unit tests** live in `app/src/test/java/...` (pure JVM, no device). Run a single test class:
  `... "E:\Android\gw.bat :app:testDebugUnitTest --tests '<FQCN>' --no-daemon"`.
- **Write-scope law:** every persistence write goes through a `container.set*` intent that uses the
  process-lifetime `writeScope`, never a composition scope ([[dinghy-compose-write-scope-cancellation]]).
- **Type law:** no inline `fontSize=`/`fontFamily=` — use `DinghyType` roles
  (`role.toTextStyle(t)`); the build-failing `FontConformanceTest` enforces this.
- **Icon law (HARD):** never invent a glyph. The owner-specified Material Symbols names are in
  Task 0.1; if an SVG source is missing, STOP and ask the owner ([[dinghy-never-pick-icons-ask]],
  [[dinghy-check-img-source-assets]]).
- **Commit** after each task with the message shown. Branch first if on `master`.

---

## Phase ordering rationale

**Codex BLOCK-2 fix — fully ADDITIVE ordering, no red-build seam.** Every phase ends with a GREEN
build. We ADD the new routes/screens and migrate every consumer first; the OLD `NavDest.Settings`/
`Devices` objects, the old `SettingsScreen`, and their AppShell composables are deleted ONLY in the
final cleanup phase (Phase 7), by which point nothing references them. `AppSettingsScreen` is a NEW
file (not an in-place rename) so `SettingsScreen` keeps compiling its old route until cleanup.

1. **Phase 0** — strings + icons groundwork (everything else references these).
2. **Phase 1** — font scale app-global (riskiest data change; fully unit-testable; do early).
3. **Phase 2** — ADD the 3 new NavDest routes (purely additive; old routes untouched).
4. **Phase 3** — App Settings screen (new file) + additive AppShell route.
5. **Phase 4** — extract connection editor + Printer Settings screen + additive routes.
6. **Phase 5** — Manage printers sub-screen + Rename.
7. **Phase 6** — System hub trim (flip hub rows to the new routes — last consumer of the old ones).
8. **Phase 7** — CLEANUP: remove `NavDest.Settings`/`Devices` + old `SettingsScreen` + old
   composables + fix the route/shell tests (now safe — all consumers migrated).
9. **Phase 8** — full suite + on-device UAT.

---

# Phase 0 — Strings & Icons groundwork

### Task 0.1: Add new icons to the registry

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`
- Create (drawables): `app/src/main/res/drawable/ic_app_settings.xml`, `ic_printer_settings.xml`,
  `ic_manage_printers.xml`, `ic_text_size.xml`, `ic_rename.xml` (vector drawables from the
  owner-named Material Symbols).

Owner-specified glyphs (Material Symbols names): **App Settings = `mobile_gear`**, **Printer
Settings = `print`**, **Manage printers = `format_list_numbered`**, **Text size = `format_size`**,
**Rename = `drive_file_rename`**.

- [ ] **Step 1: Check `img/` and the repo for existing source SVGs first** ([[dinghy-check-img-source-assets]])

```bash
ls img/ 2>/dev/null | grep -iE 'gear|print|list|size|rename'
grep -rl 'mobile_gear\|format_list_numbered\|format_size\|drive_file_rename' app/src/main/res/drawable 2>/dev/null
```
Expected: identify whether any glyph already exists. If a needed Material Symbol SVG is NOT present
and cannot be sourced, STOP and ask the owner to supply it — do not substitute a different glyph.

- [ ] **Step 2: Add each new icon to the `DinghyIcons` registry**

Follow the existing registry entries (e.g. `SystemRowSettings`, `SystemRowPrinters`) verbatim in
shape. Add: `AppSettings`, `PrinterSettings`, `ManagePrinters`, `TextSize`, `Rename` referencing the
new drawables. Match the surrounding entry pattern exactly (same `DinghyIcon(...)` constructor the
file already uses).

- [ ] **Step 3: Build to verify the registry compiles + drawables resolve**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt app/src/main/res/drawable/
git commit -m "feat(icons): add app/printer settings, manage, text-size, rename glyphs"
```

### Task 0.2: Add new string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

Reuse existing keys where present (per the audit): `settings_babystep*`, `settings_keep_screen_on*`,
`settings_battery*`, `printers_*`, `common_back/save/on/off`, `theme_section_text_size` (line 630
already exists). Add the NEW keys below.

- [ ] **Step 1: Add the new strings**

```xml
<!-- System hub + settings split -->
<string name="system_row_app_settings">App Settings</string>
<string name="system_row_printer_settings">Printer Settings</string>
<string name="printer_settings_manage">Manage printers</string>
<string name="printer_settings_connection">Connection</string>
<string name="printer_settings_theme">Theme &amp; colors</string>
<string name="printer_settings_system_info">System Info</string>
<string name="printer_settings_power">Power</string>
<!-- App Settings text size -->
<string name="settings_text_size">Text size</string>
<string name="settings_text_size_s">S</string>
<string name="settings_text_size_m">M</string>
<string name="settings_text_size_l">L</string>
<!-- Rename -->
<string name="printers_rename">Rename</string>
<string name="printers_edit_name">Printer name</string>
<string name="cd_app_settings">App settings</string>
<string name="cd_printer_settings">Printer settings</string>
<string name="cd_manage_printers">Manage printers</string>
<string name="cd_text_size">Text size</string>
<string name="cd_rename">Rename printer</string>
```

- [ ] **Step 2: Build to verify strings compile**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(strings): add app/printer settings + text-size + rename strings"
```

---

# Phase 1 — Font scale app-global

This is the only real data migration. The single injection point is `AppContainer.activeThemeTuple`
(AppContainer.kt:681-684), which feeds BOTH `seedTheme()` (line 715-735) and `effectiveTokens`
(line 702-706). We `combine` the per-tuple flow with a new app-global font-scale flow and override
`ThemeTuple.fs`. `ThemeTuple.fs` itself stays (TokenBridge needs it); only its SOURCE changes.

### Task 1.1: Create `FontScalePrefs` (TDD)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/settings/FontScalePrefs.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/settings/FontScalePrefsTest.kt`

Mirrors `DisplayPrefs` (ui/settings/DisplayPrefs.kt:32-53) but stores the `FontScale` enum as its
`.name` string, with a non-blocking one-time migration seed.

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import works.mees.dinghy.theme.FontScale
import java.io.File

class FontScalePrefsTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { File(tmp.newFolder(), name) })

    @Test fun defaultsToM() = runTest {
        val prefs = FontScalePrefs(store("fs1.pb"))
        assertEquals(FontScale.M, prefs.fontScale.first())
    }

    @Test fun persistsSetValue() = runTest {
        val prefs = FontScalePrefs(store("fs2.pb"))
        prefs.setFontScale(FontScale.L)
        assertEquals(FontScale.L, prefs.fontScale.first())
    }

    @Test fun migrateSeedsOnceThenIsIdempotent() = runTest {
        val prefs = FontScalePrefs(store("fs3.pb"))
        prefs.migrateSeed(FontScale.S)          // first run seeds S
        assertEquals(FontScale.S, prefs.fontScale.first())
        prefs.migrateSeed(FontScale.L)          // sentinel set → no-op
        assertEquals(FontScale.S, prefs.fontScale.first())
    }

    @Test fun migrateDoesNotClobberAnExplicitValue() = runTest {
        val prefs = FontScalePrefs(store("fs4.pb"))
        prefs.setFontScale(FontScale.L)         // user already set
        prefs.migrateSeed(FontScale.S)          // migration must not overwrite
        assertEquals(FontScale.L, prefs.fontScale.first())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.settings.FontScalePrefsTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — `FontScalePrefs` does not exist.

- [ ] **Step 3: Write the implementation**

```kotlin
package works.mees.dinghy.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import works.mees.dinghy.theme.FontScale
import java.io.IOException

/**
 * App-global font scale (S/M/L) — process-scoped, connection-INDEPENDENT (sibling of
 * [DisplayPrefs]/[BabystepPrefs]). Replaces the retired per-printer `Profile.fsChoice` as the SOLE
 * source of `--fs`. Stores the [FontScale] as its `.name`; unknown/missing decodes to [FontScale.M].
 */
class FontScalePrefs(
    private val dataStore: DataStore<Preferences>,
) {
    val fontScale: Flow<FontScale> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                prefs[KEY_FS]?.let { runCatching { FontScale.valueOf(it) }.getOrNull() } ?: DEFAULT
            }

    suspend fun setFontScale(choice: FontScale) {
        dataStore.edit { it[KEY_FS] = choice.name }
    }

    /**
     * One-time migration seed (Codex: must NOT block on an active profile). Seeds [seed] ONLY when
     * the value has never been written and the sentinel is unset; idempotent thereafter. Callers pass
     * the active profile's prior fsChoice if one exists, else [FontScale.M].
     */
    suspend fun migrateSeed(seed: FontScale) {
        dataStore.edit { prefs ->
            if (prefs[KEY_MIGRATED] == true) return@edit
            if (prefs[KEY_FS] == null) prefs[KEY_FS] = seed.name
            prefs[KEY_MIGRATED] = true
        }
    }

    companion object {
        val DEFAULT = FontScale.M
        private val KEY_FS = stringPreferencesKey("font_scale")
        private val KEY_MIGRATED = booleanPreferencesKey("font_scale_migrated_v1")
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.settings.FontScalePrefsTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/settings/FontScalePrefs.kt app/src/test/java/works/mees/dinghy/ui/settings/FontScalePrefsTest.kt
git commit -m "feat(settings): add app-global FontScalePrefs with non-blocking migration seed"
```

### Task 1.2: Create the DataStore + inject into AppContainer

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/DinghyApp.kt` (DataStore creation + ctor call, ~lines 39-138)
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` (ctor param ~84-138; field ~140-151)

- [ ] **Step 1: Create the DataStore in `DinghyApp.onCreate`**

After the `savedLocationDataStore` block, add (mirroring it exactly):

```kotlin
val fontScaleDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = appScope,
    produceFile = { applicationContext.preferencesDataStoreFile("fontscale.preferences_pb") },
)
```

- [ ] **Step 2: Pass it to the `AppContainer(...)` constructor call** — add
  `fontScaleDataStore = fontScaleDataStore,` alongside `savedLocationDataStore = ...`.

- [ ] **Step 3: Add the ctor param + field in `AppContainer`**

Add `fontScaleDataStore: DataStore<Preferences>,` to the constructor param list (after
`savedLocationDataStore`). Add the field next to the other prefs (near line 402):

```kotlin
val fontScalePrefs: FontScalePrefs = FontScalePrefs(fontScaleDataStore)
```
Add the import `import works.mees.dinghy.ui.settings.FontScalePrefs`.

- [ ] **Step 4: Build**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL (wiring compiles; nothing consumes it yet).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/DinghyApp.kt app/src/main/java/works/mees/dinghy/di/AppContainer.kt
git commit -m "feat(di): wire fontscale.preferences_pb DataStore + FontScalePrefs into AppContainer"
```

### Task 1.3: Expose `fontScale` flow + `setFontScale` intent

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`

- [ ] **Step 1: Add the flow + intent (mirror `setKeepScreenOn`, lines 382-391)**

```kotlin
/** App-global font scale (S/M/L) — connection-independent; the SOLE source of `--fs`. */
val fontScale: Flow<works.mees.dinghy.theme.FontScale> = fontScalePrefs.fontScale

/** Persist the app-global font scale durably (writeScope, never a composition scope). */
fun setFontScale(choice: works.mees.dinghy.theme.FontScale) {
    writeScope.launch { fontScalePrefs.setFontScale(choice) }
}
```

- [ ] **Step 2: Build**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/di/AppContainer.kt
git commit -m "feat(di): expose container.fontScale flow + setFontScale intent"
```

### Task 1.4: Inject app-global fs into `activeThemeTuple` (the single override point)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt:681-684`
- Test: `app/src/test/java/works/mees/dinghy/di/ActiveThemeTupleFsTest.kt`

`activeThemeTuple` currently:
```kotlin
val activeThemeTuple: Flow<ThemePrefs.ThemeTuple> =
    activeProfile.flatMapLatest { p ->
        if (p != null) flowOf(p.toThemeTuple()) else themePrefs.tupleFlow
    }
```

- [ ] **Step 1: Write the failing test** (resolved tuple fs follows the app setting regardless of profile)

```kotlin
package works.mees.dinghy.di

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.theme.ThemePrefs

/**
 * Pure check of the override rule used in activeThemeTuple: whatever per-profile/idle tuple is
 * produced, its `fs` is replaced by the app-global font scale. Mirrors the production `combine`.
 */
class ActiveThemeTupleFsTest {
    private fun base(fs: Float) = ThemePrefs.ThemeTuple(
        seedHex = "#3f78ff", dark = true, paletteMode = "Colorful",
        poolShift = 0, poolOverrides = emptyMap(), fs = fs,
    )

    @Test fun appFontScaleOverridesTupleFs() = runTest {
        // The override is `tuple.copy(fs = appFs.multiplier)`.
        val overridden = base(FontScale.S.multiplier).copy(fs = FontScale.L.multiplier)
        assertEquals(FontScale.L.multiplier, overridden.fs, 0.0001f)
    }
}
```

(This asserts the override expression directly; the production wiring is verified by the build +
on-device UAT. Keep it as a guard against someone changing the copy semantics.)

- [ ] **Step 2: Run it — expect PASS only after the production change uses the same expression.** Run
  the class; it should compile and pass immediately (it tests the `.copy` rule itself). Its value is
  documenting the invariant.

- [ ] **Step 3: Change `activeThemeTuple` to combine in the app-global fs**

```kotlin
val activeThemeTuple: Flow<ThemePrefs.ThemeTuple> =
    combine(
        activeProfile.flatMapLatest { p ->
            if (p != null) flowOf(p.toThemeTuple()) else themePrefs.tupleFlow
        },
        fontScalePrefs.fontScale,
    ) { tuple, appFs ->
        // App-global font scale is the SOLE source of `--fs` — override whatever the per-profile /
        // idle tuple carried. Both theme paths (seedTheme + effectiveTokens) read this flow.
        tuple.copy(fs = appFs.multiplier)
    }
```
Ensure `kotlinx.coroutines.flow.combine` is imported.

- [ ] **Step 4: Build + run the test**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.di.ActiveThemeTupleFsTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/di/AppContainer.kt app/src/test/java/works/mees/dinghy/di/ActiveThemeTupleFsTest.kt
git commit -m "feat(theme): app-global font scale overrides per-profile fs in activeThemeTuple"
```

### Task 1.5: Run the migration at startup (non-blocking)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` (the `init` block near 404-411)

**Codex BLOCK-1 fix.** `ProfileStore` exposes only cold Flows (`profiles`, `activeId`) — there is NO
synchronous snapshot, and `activeProfileId.value` in `init` can be null/stale before DataStore loads.
Do NOT use a `.value` snapshot and do NOT copy the trace migration's `activeProfileId.filterNotNull().first()`
(a no-printer install would block forever). Instead read the raw persisted blob ONCE via
`dataStore.data.first()` — that emits the current stored value immediately, even when empty, and never
waits for a non-null profile. Read `PersistedProfile.fsChoice` (kept; see Task 1.6 Step 4), not the
retired runtime `Profile.fsChoice`.

- [ ] **Step 1: Add a non-blocking raw reader to `ProfileStore`**

`ProfileStore` already decodes the persisted profile list inside its `profiles` Flow (ProfileStore.kt
around line 151) and reads `KEY_ACTIVE_ID` for `activeId`. Reuse that exact decode path. Add:

```kotlin
/**
 * One-shot, non-blocking read of the active printer's persisted fsChoice — for the font-scale
 * migration ONLY. `dataStore.data.first()` emits the CURRENT stored prefs immediately (even when
 * empty); it never waits for a non-null active profile. Reads the PERSISTED blob, so it still works
 * after the runtime `Profile.fsChoice` is retired (Task 1.6).
 */
suspend fun readActiveFsChoiceRaw(): String? {
    val prefs = dataStore.data.first()
    val activeId = prefs[KEY_ACTIVE_ID] ?: return null
    // decodePersisted = the SAME parse the `profiles` Flow uses to produce List<PersistedProfile>.
    return decodePersisted(prefs).firstOrNull { it.id == activeId }?.fsChoice
}
```
If the decode is currently inlined in the `profiles` Flow, factor it into a private
`decodePersisted(prefs): List<PersistedProfile>` helper and have both the Flow and this reader call it
(DRY). Import `kotlinx.coroutines.flow.first`.

- [ ] **Step 2: Add the migration launch in `AppContainer.init` (alongside the trace migration)**

```kotlin
// One-time font-scale migration (Codex BLOCK-1: must NOT block on an active profile). Seed the
// app-global font scale from the active printer's prior persisted fsChoice if one exists, else M.
// Idempotent via the FontScalePrefs sentinel. Process-lifetime writeScope.
writeScope.launch {
    val seed = profileStore.readActiveFsChoiceRaw()
        ?.let { runCatching { works.mees.dinghy.theme.FontScale.valueOf(it) }.getOrNull() }
        ?: works.mees.dinghy.theme.FontScale.M
    fontScalePrefs.migrateSeed(seed)
}
```

- [ ] **Step 3: Build**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/di/AppContainer.kt app/src/main/java/works/mees/dinghy/config/ProfileStore.kt
git commit -m "feat(theme): seed app-global font scale from prior profile fsChoice on first launch"
```

### Task 1.6: Retire `fsChoice` from the per-printer path

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/config/Profile.kt` (remove `fsChoice` from runtime
  `Profile`; keep `PersistedProfile.fsChoice` for migration-read + tolerant decode)
- Modify: `app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt` (`sanitizeTuple` drops `rawFs`;
  `tupleFlow` stops reading `KEY_FS`)
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` (remove `setActiveFs`; the
  font-scale override makes per-tuple fs irrelevant)
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt` (remove the S/M/L
  control + `fsChoice`/`onFsChoiceChange` from the content seam)
- Modify: `app/src/main/java/works/mees/dinghy/preview/ThemeEditorPreviews.kt` (drop `fsChoice` args)
- Modify: theme tests asserting `rawFs`/`fsChoice`/`ThemeTuple.fs`

**`ThemeTuple.fs` STAYS** (TokenBridge.build needs it). Only its *source* changes — `sanitizeTuple`
defaults it to `FontScale.M.multiplier`; the real value is injected in `activeThemeTuple` (Task 1.4).

- [ ] **Step 1: `sanitizeTuple` — drop `rawFs`, default fs to M**

In `ThemePrefs.sanitizeTuple` (ThemePrefs.kt:239-246), remove the `rawFs: String?` parameter and
change the fs line (251) to:
```kotlin
val fs = FontScale.M.multiplier   // app-global font scale overrides this downstream (activeThemeTuple)
```

- [ ] **Step 2: `tupleFlow` — stop reading `KEY_FS`** (ThemePrefs.kt:45-59): remove the
  `rawFs = prefs[KEY_FS],` argument from the `sanitizeTuple(...)` call. (Leave `KEY_FS`/`setFs`
  defined-but-unused for now, or delete `setFs` + `KEY_FS` — they are superseded by FontScalePrefs.
  Deleting is cleaner; if deleted, also remove the idle branch in the next step.)

- [ ] **Step 3: Remove `setActiveFs` from `AppContainer`** (lines 773-776) and any callers. The
  ThemeEditor no longer changes fs (Step 5). If `themePrefs.setFs`/`KEY_FS` were deleted in Step 2,
  ensure nothing references them.
  **KEEP `ThemeResolver.setFs`** (Codex FIX): it is still used by the dev `GalleryScreen`
  (GalleryScreen.kt:99,173 — local `fsChoice` + `resolver.setFs`) and asserted by
  `FontScaleTest` (FontScaleTest.kt:37). Do NOT delete the resolver method — only the ThemeEditor's
  *call* to it goes away (Step 5). Leave GalleryScreen's own dev fs control untouched.

- [ ] **Step 4: `Profile.kt` — remove `fsChoice` from runtime `Profile`**

- Remove `val fsChoice: String = "M",` from the runtime `data class Profile` and from
  `toThemeTuple()` (drop the `rawFs = fsChoice` arg), `toPersisted()`, `fromPersisted()`.
- **Keep `PersistedProfile.fsChoice`** as a tolerated decode field so the Task 1.5 migration can read
  the prior value from the persisted blob, and old blobs still decode. Update the Task 1.5 snapshot to
  read `PersistedProfile.fsChoice`.

- [ ] **Step 5: `ThemeEditorScreen.kt` — remove the S/M/L control + seam params**

- Delete the text-size `SectionLabel` + `Row { FontScale.entries... PoolSizeSegment(...) }` block
  (lines 894-900).
- Remove `fsChoice: FontScale` and `onFsChoiceChange: (FontScale) -> Unit` from `ThemeEditorContent`
  (lines 760, 776).
- Remove the live wrapper wiring `onFsChoiceChange = { ... container.setActiveFs(...) }` (lines 273-277)
  and the local `fsChoice` state it drove.

- [ ] **Step 6: `ThemeEditorPreviews.kt` — drop every `fsChoice = FontScale.X` argument** (all preview
  call sites, incl. the former fs=L overflow preview at line 145 — repurpose or delete that preview).

- [ ] **Step 7: Update theme tests** — grep and fix:

```bash
grep -rln 'fsChoice\|rawFs\|ThemeTuple(.*fs\|\.fs ' app/src/test/java/works/mees/dinghy/theme app/src/test/java/works/mees/dinghy/config
```
General rule: remove `rawFs`/`fsChoice` from `sanitizeTuple`/runtime-`Profile` constructions;
assertions that the tuple's fs defaults to M still hold; keep `ThemeTuple.fs` assertions where the
tuple is constructed directly. **Exact breakpoints Codex identified:**
  - **`StatusOverrideTokenBridgeTest.kt:64,72`** — passes `rawFs`/`fsChoice` to `sanitizeTuple`/Profile
    → remove those args (sanitizeTuple no longer takes `rawFs`). **WILL break — fix.**
  - **`ProfileThemeSeedTest.kt:33`** — asserts a profile's fs maps into the tuple fs. After retirement
    per-profile fs no longer flows to the tuple (it defaults to M and is overridden app-globally) →
    update/remove the fs assertion. **WILL break — fix.**
  - **`ConnectionConfigTest.kt:73`** (old blob fixture with `"fsChoice":"M"`) and
    **`TokenDeltaSerializationTest.kt:63,73`** (asserts `PersistedProfile.fsChoice` round-trips) —
    these test the PERSISTED blob, which KEEPS `fsChoice` (Step 4), so they **stay green; do NOT edit.**

- [ ] **Step 8: Build + run theme/config test packages**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.theme.*' --tests 'works.mees.dinghy.config.*' --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS (after fixes). Then full `:app:assembleDebug` — BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor(theme): retire per-printer fsChoice; font scale is app-global only"
```

---

# Phase 2 — ADD the 3 new NavDest routes (additive only)

**Codex BLOCK-2 fix:** this phase ONLY ADDS routes — `NavDest.Settings`/`Devices` stay until Phase 7.
Adding `@Serializable data object`s breaks nothing (the round-trip test iterates `knownNavDests`, so
the new members are auto-covered; we add explicit round-trips too). Build stays GREEN.

### Task 2.1: Add the 3 new routes (no removals)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt`
- Modify test: `app/src/test/java/works/mees/dinghy/ui/route/NavDestRoundTripTest.kt`

- [ ] **Step 1: Edit `NavDest.kt`**

- Add three members to the sealed interface (do NOT touch `Settings`/`Devices`):
```kotlin
@Serializable data object AppSettings     : NavDest
@Serializable data object PrinterSettings : NavDest
@Serializable data object ManagePrinters  : NavDest
```
- In `knownNavDests`: ADD `NavDest.AppSettings`, `NavDest.PrinterSettings`, `NavDest.ManagePrinters`
  (leave Settings/Devices in place). Count goes 23 → 26 (temporary; Phase 7 removes 2 → 24).
- `FOOT_GUN_DESTS` unchanged (none of the new routes are foot-guns; the `FootGunDestsTest` mid-print
  list still passes — adding non-foot-gun dests doesn't break it. We extend that list in Phase 7).

- [ ] **Step 2: Add explicit round-trip tests** in `NavDestRoundTripTest.kt` (mirroring the helper):
```kotlin
@Test fun appSettings_roundTrips()     { assertRoundTrip(NavDest.AppSettings) }
@Test fun printerSettings_roundTrips() { assertRoundTrip(NavDest.PrinterSettings) }
@Test fun managePrinters_roundTrips()  { assertRoundTrip(NavDest.ManagePrinters) }
```

- [ ] **Step 3: Build + run route/shell tests — GREEN**

Run: `... "E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.route.*' --tests 'works.mees.dinghy.shell.*' --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; ALL PASS (purely additive — nothing else references the new routes yet).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt app/src/test/java/works/mees/dinghy/ui/route/NavDestRoundTripTest.kt
git commit -m "feat(nav): add AppSettings/PrinterSettings/ManagePrinters routes (additive)"
```

### Task 2.2: Add the new routes to `screenOwnsEstop` (additive)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:935-957`

- [ ] **Step 1: ADD three e-stop ownership entries** (leave Settings/Devices entries — removed in
  Phase 7). Insert:
```kotlin
estopDest.isRoute<NavDest.AppSettings>() ||
estopDest.isRoute<NavDest.PrinterSettings>() ||
estopDest.isRoute<NavDest.ManagePrinters>() ||
```
(All three are FocusFrame screens that dock the e-stop in their header; Webcam + Theme remain the only
shell-float fallbacks.) AppShell still compiles — we only added boolean clauses referencing existing
routes.

- [ ] **Step 2: Build — GREEN**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
git commit -m "feat(nav): screenOwnsEstop covers the 3 new settings routes (additive)"
```

---

# Phase 3 — App Settings screen

### Task 3.1: Text-size selector component

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/settings/TextSizeSelector.kt`

A 3-segment S/M/L row using the established selected-fill convention (`accentSoft` fill +
`Intent.Accent`), built from `OutlinedControl` (signature confirmed: has `intent` + `fill` params).

- [ ] **Step 1: Implement**

```kotlin
package works.mees.dinghy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.theme.compose.LocalTokens

/** App-global S/M/L text-size selector — selected segment uses accentSoft fill (the selection convention). */
@Composable
fun TextSizeSelector(
    selected: FontScale,
    onSelect: (FontScale) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FontScale.entries.forEach { choice ->
            val isSel = choice == selected
            OutlinedControl(
                label = stringResource(
                    when (choice) {
                        FontScale.S -> R.string.settings_text_size_s
                        FontScale.M -> R.string.settings_text_size_m
                        FontScale.L -> R.string.settings_text_size_l
                    },
                ),
                onClick = { onSelect(choice) },
                modifier = Modifier.weight(1f),
                intent = if (isSel) Intent.Accent else Intent.Neutral,
                fill = if (isSel) t.accentSoft else null,
            )
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/settings/TextSizeSelector.kt
git commit -m "feat(settings): TextSizeSelector S/M/L segmented control"
```

### Task 3.2: AppSettingsScreen as a NEW file (keep SettingsScreen alive)

**Codex BLOCK-2 fix:** create a NEW `AppSettingsScreen.kt` rather than renaming `SettingsScreen.kt`.
`SettingsScreen` stays compiling under its old `NavDest.Settings` route until Phase 7 cleanup deletes
both. Easiest authoring path: copy `SettingsScreen.kt`'s structure, rename the two composables to
`AppSettingsScreen`/`AppSettingsContent`, drop webcam, add text size.

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/screen/AppSettingsScreen.kt`
- Create: `app/src/main/java/works/mees/dinghy/preview/AppSettingsPreviews.kt`
- (DO NOT modify/delete `SettingsScreen.kt` or `SettingsPreviews.kt` yet — Phase 7.)

- [ ] **Step 1: Author `AppSettingsScreen.kt`** (copy of SettingsScreen, transformed)

- `AppSettingsContent` param list = `SettingsContent`'s MINUS `webcamOn`/`webcamEnabled`/`onWebcamToggle`,
  PLUS `fontScale: FontScale` + `onFontScale: (FontScale) -> Unit`. Drop the `DenseToggleRow` webcam
  row (SettingsScreen.kt:208-214). Keep Keep-screen-on / Battery / Babystep rows verbatim. Reuse the
  same private `DenseToggleRow`/`TokenTextField` helpers — import them from the existing screen package
  (they are top-level/`private`; if `private`, copy the tiny `DenseToggleRow` into this file, or make
  the original `internal`. Prefer making `DenseToggleRow` `internal` in `SettingsScreen.kt` so both
  share it — that is the only edit to the old file, and it survives the Phase 7 deletion since the
  helper moves to AppSettings then).
- Add the Text-size row at the TOP of the Field, above Keep-screen-on:
```kotlin
// App-global text size (S/M/L) — durable via container.setFontScale (writeScope).
ListRow(selected = false, onClick = {}, uDp = grid.uDp,
    leadingContent = { ListRowIcon(icon = DinghyIcons.TextSize, uDp = grid.uDp, tint = t.text) }) {
    Text(stringResource(R.string.settings_text_size), color = t.text,
        style = DinghyType.listLabel.toTextStyle(t))
    Spacer(Modifier.weight(1f))
}
TextSizeSelector(selected = fontScale, onSelect = onFontScale, modifier = Modifier.padding(top = 4.dp))
```
- Stateful `AppSettingsScreen(container, onBack)` wrapper: collect
  `val fontScale by container.fontScale.collectAsStateWithLifecycle(FontScale.M)`; pass
  `onFontScale = { container.setFontScale(it) }`. Do NOT collect `activeProfile`/`webcamOn`.
- FocusFrame title/icon = `R.string.system_row_app_settings` / `DinghyIcons.AppSettings`.

- [ ] **Step 2: Author `AppSettingsPreviews.kt`** targeting `AppSettingsContent` — mirror
  `SettingsPreviews.kt`'s axes (all-on/all-off, theme combos, RTL, pseudolocale) MINUS webcam args,
  PLUS `fontScale = FontScale.M` (and one `FontScale.L`).

- [ ] **Step 3: Build — GREEN (old SettingsScreen still routed; new one not yet)**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL (both screens coexist).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/screen/AppSettingsScreen.kt app/src/main/java/works/mees/dinghy/preview/AppSettingsPreviews.kt app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
git commit -m "feat(settings): new AppSettingsScreen (no webcam, + text size); SettingsScreen retained for Phase 7"
```

### Task 3.3: Add the AppSettings route to AppShell (additive)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`

- [ ] **Step 1: ADD a `composable<NavDest.AppSettings>` block** (do NOT touch the existing
  `composable<NavDest.Settings>` / `composable<NavDest.Devices>` blocks — they stay until Phase 7):
```kotlin
composable<NavDest.AppSettings> {
    AppSettingsScreen(
        container = container,
        onBack = { navController.popBackStack() },
    )
}
```
Import `AppSettingsScreen`. (PrinterSettings + ManagePrinters routes are added in Phase 4 — until then
they are unreachable from the hub, which is fine; the hub flip is Phase 6.)

- [ ] **Step 2: Build + full unit suite — GREEN**

Run: `... "E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; all unit tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
git commit -m "feat(nav): add AppSettings route to AppShell (additive)"
```

---

# Phase 4 — Printer Settings screen + connection-editor extraction

### Task 4.1: Extract `PrinterConnectionEditor`

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt` (remove the private copy +
  its helpers `SecureToggleRow`, `DiscoveredPrinterRow`, `SCAN_WINDOW_MS`, `resolveEditorKeyOnSave`,
  `EditorTarget` if they are only used by the editor — move them with it)

- [ ] **Step 1: Move `PrinterConnectionEditor` (PrintersScreen.kt:458-701) into the new file**, change
  `private fun` → `internal fun`, and move its private helpers (`SecureToggleRow`,
  `DiscoveredPrinterRow`, `SCAN_WINDOW_MS`, `resolveEditorKeyOnSave`, `EditorTarget`) with it. Keep the
  signature identical:
```kotlin
internal fun PrinterConnectionEditor(
    container: AppContainer,
    profile: Profile?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: Update `PrintersScreen.kt`** to import the extracted editor; ensure its `editingTarget`
  branch still calls it. No behavior change.

- [ ] **Step 3: Build + run printers tests**

Run: `... "E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.screen.*' --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; PrintersModeToggle/editor key tests PASS (unchanged logic).

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(printers): extract PrinterConnectionEditor to shared internal composable"
```

### Task 4.2: Build `PrinterSettingsScreen`

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterSettingsScreen.kt`
- Create: `app/src/main/java/works/mees/dinghy/preview/PrinterSettingsPreviews.kt`

Focus = active-printer card (lift the PrintersContent Focus block, PrintersScreen.kt:180-210). Field =
Connection (inline editor) / Theme & colors (nav) / Webcam (toggle) / System Info (nav) / Power (inert
stub) / divider / Manage printers (nav). Foot = Back. Stateless `PrinterSettingsContent` seam for previews.

- [ ] **Step 1: Implement the stateful wrapper + stateless content**

```kotlin
@Composable
fun PrinterSettingsScreen(
    container: AppContainer,
    onNavigate: (NavDest) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profiles by container.profileStore.profiles.collectAsStateWithLifecycle(emptyList())
    val activeId by container.profileStore.activeId.collectAsStateWithLifecycle(null)
    val activeProfile by container.activeProfile.collectAsStateWithLifecycle(null)
    val connectionState by container.connectionState.collectAsStateWithLifecycle(ConnectionState.Disconnected)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val isPrinting = printerState.printState == PrintState.Printing || printerState.printState == PrintState.Paused

    var editingConnection by remember { mutableStateOf(false) }
    BackHandler(editingConnection) { editingConnection = false }
    if (editingConnection) {
        PrinterConnectionEditor(
            container = container,
            profile = activeProfile,                 // edit the ACTIVE printer
            onDone = { editingConnection = false },
            modifier = modifier,
        )
        return
    }

    PrinterSettingsContent(
        activeProfile = activeProfile,
        profileCount = profiles.size,
        connectionState = connectionState,
        webcamOn = activeProfile?.webcamEnabled ?: true,
        webcamEnabled = activeProfile != null,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onConnection = { editingConnection = true },
        onTheme = { onNavigate(NavDest.Theme) },
        onWebcamToggle = { container.setActiveWebcamEnabled(it) },
        onSystemInfo = { onNavigate(NavDest.SystemInfo) },
        onManage = { onNavigate(NavDest.ManagePrinters) },
        onAdd = { editingConnection = true },        // empty-state Add reuses the editor (profile=null path handled by activeProfile==null)
        onBack = onBack,
        modifier = modifier,
    )
}
```

For `PrinterSettingsContent`: BoxWithConstraints + `rememberUnitGrid`; `ScreenScaffold` with the Focus
= the active-printer `FocusFrame` (lift PrintersScreen.kt:180-210, including `FocusEdge.Data(ringColor)`
from `connectionState`, and the empty-state branch from PrintersScreen.kt:211-243 routing to Add); Field
= a `ListBlock` of `ListRow`s:
- **Connection** (`leadingContent` = existing connection glyph; trailing `›`) → `onConnection`
- **Theme & colors** (`DinghyIcons.SystemRowTheme`) → `onTheme`
- **Webcam** — `DenseToggleRow`-style trailing `Switch` (reuse the toggle pattern) → `onWebcamToggle`,
  enabled = `webcamEnabled`
- **System Info** (`DinghyIcons.SysInfoTile`) → `onSystemInfo`
- **Power** — inert stub row (lift `PowerStubRow` pattern from SystemPageScreen.kt:245-274,
  `DinghyIcons.SystemRowPower`, `t.stop.copy(alpha=0.38f)`, no onClick)
- divider
- **Manage printers (N)** (`DinghyIcons.ManagePrinters`, trailing count `N` + `›`) → `onManage`

Foot = `FootButtonBar { OutlinedControl(label = common_back, intent = Intent.Accent, onClick = onBack) }`.

FocusFrame title/icon = `R.string.system_row_printer_settings` / `DinghyIcons.PrinterSettings`.

- [ ] **Step 2: Add `PrinterSettingsPreviews.kt`** — a `@Preview` matrix driving `PrinterSettingsContent`
  with: active printer connected / disconnected / empty (no profile) / N=1 vs N=3, across the standard
  theme axis. Follow `PrintersPreviews.kt` shape.

- [ ] **Step 3: Build**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/screen/PrinterSettingsScreen.kt app/src/main/java/works/mees/dinghy/preview/PrinterSettingsPreviews.kt
git commit -m "feat(printer-settings): PrinterSettingsScreen (Focus card + setting rows + Manage row)"
```

### Task 4.3: Add PrinterSettings + ManagePrinters routes to AppShell (additive)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`

- [ ] **Step 1: ADD both new route blocks** (additive — the old `composable<NavDest.Devices>` block
  stays until Phase 7). `ManagePrinters` reuses `PrintersScreen`; refine its `onSwitched` in Task 5.1.
```kotlin
composable<NavDest.PrinterSettings> {
    PrinterSettingsScreen(
        container = container,
        onNavigate = { navController.navigate(it) },
        onBack = { navController.popBackStack() },
    )
}
composable<NavDest.ManagePrinters> {
    PrintersScreen(
        container = container,
        onSwitched = { navController.popBackStack<NavDest.WaterfallHome>(inclusive = false) },
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 2: Build + smoke the unit suite**

Run: `... "E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
git commit -m "feat(nav): route NavDest.PrinterSettings to PrinterSettingsScreen"
```

---

# Phase 5 — Manage printers sub-screen + Rename

### Task 5.1: Refine ManagePrinters `onSwitched` target

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` (the `composable<NavDest.ManagePrinters>` added in Task 4.3)

Task 4.3 added the `ManagePrinters` route with a WaterfallHome `onSwitched`. Refine it so switching
from Manage pops back to **PrinterSettings** (its parent), not all the way to WaterfallHome.

- [ ] **Step 1: Update `onSwitched`**
```kotlin
composable<NavDest.ManagePrinters> {
    PrintersScreen(
        container = container,
        onSwitched = { navController.popBackStack<NavDest.PrinterSettings>(inclusive = false) },
        onBack = { navController.popBackStack() },
    )
}
```
(If a user reaches Manage without PrinterSettings on the stack, `popBackStack<PrinterSettings>` is a
no-op safe fallback; verify on-device.)

- [ ] **Step 2: Build; Commit**
```bash
git add app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
git commit -m "feat(nav): ManagePrinters switch pops back to PrinterSettings"
```

### Task 5.2: Add Rename to the connection editor

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt`

`Profile.name` exists (nullable; `displayName()` falls back to host) but has no editing UI. Add a name
field. Renaming is a sanctioned alphanumeric text field (the Settings/Save-name keyboard exception).

- [ ] **Step 1: Add a `name` field to the editor**

- Add `var name by remember { mutableStateOf("") }`; seed in the `LaunchedEffect(profile?.id)` block:
  `name = profile?.name ?: ""`.
- Add a `TokenTextField` above Host:
```kotlin
TokenTextField(
    value = name,
    onValueChange = { name = it },
    label = stringResource(R.string.printers_edit_name),
    modifier = Modifier.fillMaxWidth(),
    keyboardType = KeyboardType.Text,
)
```
- In the Save handler, set the name on the saved profile (blank → null so `displayName()` falls back):
```kotlin
val cleanName = name.trim().ifBlank { null }
val next = if (profile != null) profile.copy(name = cleanName, host = host.trim(), port = portInt!!, apiKey = resolvedKey, useSecure = useSecure)
           else Profile(id = Profile.newId(), name = cleanName, host = host.trim(), port = portInt!!, apiKey = resolvedKey, useSecure = useSecure)
```

- [ ] **Step 2: Build**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt
git commit -m "feat(printers): rename support — Printer name field in the connection editor"
```

---

# Phase 6 — System hub trim (last consumer of the old routes)

After this phase, `NavDest.Settings`/`Devices` are referenced ONLY by the old AppShell composables and
the route/shell tests — Phase 7 deletes them safely. Build stays GREEN here (the hub now points at the
already-wired new routes).

### Task 6.1: Shrink the hub to App Settings / Printer Settings / About

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/SystemPageScreen.kt`
- Modify: `app/src/main/java/works/mees/dinghy/preview/SystemPagePreviews.kt`

- [ ] **Step 1: Replace `systemNavRows()` (lines 286-292)** with:
```kotlin
private fun systemNavRows(): List<SystemNavRow> = listOf(
    SystemNavRow(NavDest.AppSettings,     DinghyIcons.AppSettings,     R.string.system_row_app_settings),
    SystemNavRow(NavDest.PrinterSettings, DinghyIcons.PrinterSettings, R.string.system_row_printer_settings),
    SystemNavRow(NavDest.About,           DinghyIcons.SystemRowAbout,  R.string.system_row_about),
)
```

- [ ] **Step 2: Remove the `PowerStubRow` from the System Field** (SystemPageScreen.kt: the `item { PowerStubRow(...) }`
  at ~173-176, and the `PowerStubRow` composable 245-274 if now unused — it was lifted into
  PrinterSettings in Task 4.2; delete the System copy). Power + System Info now live under Printer
  Settings only.

- [ ] **Step 3: Update `SystemPagePreviews.kt`** to expect the 3-row hub (no Power stub).

- [ ] **Step 4: Build**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(system): trim hub to App Settings / Printer Settings / About"
```

---

# Phase 7 — Cleanup: delete the old routes + SettingsScreen

All consumers now point at the new routes/screens. This phase removes the dead code in ONE green
commit (Codex BLOCK-2: this is the ONLY place the old route objects die, and nothing references them
anymore).

### Task 7.1: Remove `NavDest.Settings`/`Devices`, old composables, old screen; fix tests

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`
- Delete: `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt`,
  `app/src/main/java/works/mees/dinghy/preview/SettingsPreviews.kt`
- Modify tests: `NavDestRoundTripTest.kt`, `FootGunDestsTest.kt`,
  `app/src/test/java/works/mees/dinghy/shell/ShellNavStateTest.kt`

- [ ] **Step 1: Confirm nothing references the old routes/screen**
```bash
grep -rn 'NavDest\.Settings\b\|NavDest\.Devices\b\|SettingsScreen\|SettingsContent' app/src/main
```
Expected: ZERO hits in `app/src/main` (the hub flip in Phase 6 was the last one; `DenseToggleRow`
was made `internal`/copied in Task 3.2). If any remain, migrate them before deleting.

- [ ] **Step 2: `NavDest.kt`** — remove `@Serializable data object Settings`/`Devices`; remove them
  from `knownNavDests` (count 26 → 24).

- [ ] **Step 3: `AppShell.kt`** — delete the `composable<NavDest.Settings>` and
  `composable<NavDest.Devices>` blocks; remove `estopDest.isRoute<NavDest.Settings>()` and
  `estopDest.isRoute<NavDest.Devices>()` from `screenOwnsEstop`; drop the `SettingsScreen` import.

- [ ] **Step 4: Delete** `SettingsScreen.kt` + `SettingsPreviews.kt`.

- [ ] **Step 5: Fix the route/shell tests**
  - `NavDestRoundTripTest.kt` — delete `devices_roundTrips()` (line 37) and `settings_roundTrips()`
    (line 39).
  - `FootGunDestsTest.kt` — in the `midPrint` list (lines 35-57) remove `NavDest.Devices` +
    `NavDest.Settings`; add `NavDest.AppSettings`, `NavDest.PrinterSettings`, `NavDest.ManagePrinters`.
  - `ShellNavStateTest.kt` — rewrite `four_ia_destinations_are_distinct_known_dests()` around
    `listOf(NavDest.AppSettings, NavDest.PrinterSettings, NavDest.About)`; DELETE
    `devices_rename_no_dangling_ref()` (the `Devices` object is gone).
  - `StartDestMappingTest.kt` — the generic iteration needs no edit; remove any literal `Settings`/
    `Devices` references if present.

- [ ] **Step 6: Build + full unit suite — GREEN**

Run: `... "E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; ALL PASS.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(nav): remove old Settings/Devices routes + SettingsScreen; fix tests"
```

---

# Phase 8 — Full verification + on-device UAT

### Task 8.1: Full suite + R8 release build

- [ ] **Step 1: Full unit suite**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: ALL PASS. Pay attention to: `FontScalePrefsTest`, `ActiveThemeTupleFsTest`, NavDest
round-trip/footgun/startdest/shellnav, theme tuple/profile tests, `FontConformanceTest` (must stay
green — no inline font usages introduced).

- [ ] **Step 2: Release build (R8) compiles**

Run: `... "E:\Android\gw.bat :app:assembleRelease --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL (keep rules intact; no new reflection).

- [ ] **Step 3: Commit any fixes**

```bash
git add -A && git commit -m "test: green full suite + release build for settings split"
```

### Task 8.2: On-device UAT (flox + moto)

Force a clean rebuild to avoid the stale-APK trap ([[dinghy-stale-apk-uat-gate]]); install the matching
ABI slice to BOTH devices ([[dinghy-test-devices]]). The owner navigates and eyeballs
([[dinghy-display-ondevice-iteration]]).

- [ ] **Step 1: Rebuild + install**

```bash
... "E:\Android\gw.bat :app:assembleDebug --rerun-tasks --no-daemon" 2>&1 | tr -d '\r'
# verify APK mtime is AFTER the last fix commit, then:
E:\Android\Sdk\platform-tools\adb.exe -s 0a64b42e install -r <apk>   # flox
E:\Android\Sdk\platform-tools\adb.exe -s ZY22LBDRM9 install -r <apk> # moto
```

- [ ] **Step 2: UAT checklist (owner)**
  - System hub shows exactly **App Settings / Printer Settings / About**.
  - **App Settings:** Text size S/M/L changes apply app-wide and PERSIST across a printer switch and a
    relaunch; Keep-screen-on, Battery, Babystep all work as before; no Webcam row here.
  - **Font migration:** an upgrade from a build with a non-default per-printer text size lands on that
    same size app-globally; a fresh install defaults to M.
  - **Printer Settings:** Focus shows the active printer + connection ring; Connection edits the active
    printer; Theme & colors opens (no font control there now); Webcam toggle greys the home tile;
    System Info + Power reachable here; Manage printers (N) opens the list.
  - **Manage printers:** switch (pops back to Printer Settings), Add (with name), Delete (ConfirmGuard),
    **Rename** persists and shows in the list/Focus.
  - **E-stop** present on App Settings, Printer Settings, Manage printers (docked header), not doubled.
  - Portrait + landscape both clean; flox (Adreno 320) responsive.

- [ ] **Step 3: Record UAT verdict; fix any findings; final commit.**

---

## Self-review notes (author)

- **Spec coverage:** hub trim (Task 6.1), App Settings + text size (3.1-3.3), Printer Settings (4.2-4.3),
  Connection two entry points (4.2 inline + 5.2 Add), Theme reuse minus font (1.6 Step 5), Manage +
  Rename (5.1-5.2), font migration + retire (1.1-1.6), NavDest add (2.1-2.2) + remove (7.1),
  System Info/Power relocation (4.2 + 6.1), icons/strings (Phase 0). All spec sections map to a task.
- **Open questions from spec:** (1) Manage-printers Focus card — this plan keeps PrintersScreen's
  existing Focus (no extra redundancy decision needed for v1; revisit in UAT). (2) Text-size idiom —
  resolved as a 3-segment selector (Task 3.1), matching the enum.
- **Build-green throughout (Codex BLOCK-2 fix):** the plan is fully additive — new routes/screens are
  added and every consumer migrated BEFORE the old `NavDest.Settings`/`Devices` + `SettingsScreen` are
  deleted (Phase 7). Every phase ends with a GREEN build; there is no red interval.
- **Codex review (2026-06-15, foreground/bounded):** both BLOCKs folded in — migration uses a
  non-blocking raw persisted read (Task 1.5), additive ordering removes the red seam. FIXes folded:
  `ThemeResolver.setFs` kept (Gallery/FontScaleTest), exact theme-test breakpoints enumerated (1.6 Step 7).
