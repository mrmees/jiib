# App Settings Focus/Field Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the App Settings screen as a pure-selector Focus/Field list (rows show a read-only state indicator; controls live in the Focus pane) fully inside the base-frame component stack, and move webcam-disable from a per-printer Profile field to an app-global `DisplayPrefs` setting.

**Architecture:** The Field is a `ListBlock` of `ListRow` selectors + a `FootButtonBar` Back, both flush inside `ScreenScaffold`'s default `RegisteredRegion`. The Focus is a `FocusFrame` that swaps content by the selected `AppSetting` (a small enum), holding the explanation + control (`TextSizeSelector` / `ToggleRow` / `StepperRow` / battery button). Webcam-disable becomes `DisplayPrefs.webcamEnabled` (app-global, default On); the per-printer `Profile.webcamEnabled` field, its Printer Settings row, and the `setActiveWebcamEnabled` helper are removed, and `AppContainer.webcamTileEnabled` is re-pointed to the new flow.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore(Preferences), kotlinx.serialization, JUnit host tests. Spec: `docs/superpowers/specs/2026-06-15-app-settings-redesign-design.md`.

**Build/test harness (this repo builds Windows-side):** every Gradle command runs as
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <gradle args>" 2>&1 | tr -d '\r'
```
Host unit tests: `:app:testDebugUnitTest`. The process exit code is authoritative. On-device UAT is manual on BOTH flox (Nexus 7) and moto per the two-device rule.

---

## File Structure

**Phase 1 — Icons**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — add `Fluorescent`, `ShieldLock` vals + add both to `all`.

**Phase 2 — Webcam app-global data layer**
- Modify: `app/src/main/java/works/mees/dinghy/ui/settings/DisplayPrefs.kt` — add `webcamEnabled` flow + `setWebcamEnabled`.
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — add `webcamEnabled`/`setWebcamEnabled`; re-point `webcamTileEnabled` to combine `webcamCount` with `displayPrefs.webcamEnabled`.
- Test: `app/src/test/java/works/mees/dinghy/ui/settings/DisplayPrefsTest.kt` (create or extend) — webcam round-trip + default.

**Phase 3 — Remove the per-printer webcam toggle**
- Modify: `app/src/main/java/works/mees/dinghy/config/Profile.kt` — remove `webcamEnabled` from `Profile` + `PersistedProfile` + `toPersisted`/`fromPersisted`.
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — delete `setActiveWebcamEnabled`.
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterSettingsScreen.kt` — delete `PrinterSettingsWebcamToggleRow`, its `item {}`, and the `webcamOn`/`webcamEnabled`/`onWebcamToggle` params.
- Modify: `app/src/main/java/works/mees/dinghy/preview/PrinterSettingsPreviews.kt` — drop the removed args from all call sites.
- Modify: `app/src/test/java/works/mees/dinghy/config/ProfileToggleTest.kt` — delete the per-profile tests; keep the `webcamTileGate` test.
- Verify: `app/src/test/java/works/mees/dinghy/config/ConnectionConfigTest.kt` — confirm the `webcamEnabled` JSON blob still decodes (ignore-unknown-keys safety); no edit expected.

**Phase 4 — Rebuild the App Settings screen**
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/AppSettingsScreen.kt` — new `AppSetting` enum, selector rows + indicators, Focus-by-selection, `ToggleRow`/`StepperRow` controls, `ListBlock` + `FootButtonBar`, drop `fieldFramed=false` + the `TokenTextField`/edit-buffer.
- Modify: `app/src/main/res/values/strings.xml` — add the new indicator/explanation/placeholder strings.
- Modify: `app/src/main/java/works/mees/dinghy/preview/AppSettingsPreviews.kt` — update the seam call sites; add per-selection previews.

---

## Phase 1: Register the two new icons

### Task 1: Add `Fluorescent` and `ShieldLock` to the icon registry

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`
- Test: `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt` (existing — drives off `DinghyIcons.all`)

- [ ] **Step 1: Add the two icon vals.** Near the Phase-28 System/Settings cluster (just before `val AppSettings` at line 305), add:

```kotlin
val Fluorescent = DinghyIcon(IconRef.Ligature("fluorescent"), alternate = "fluorescent")
val ShieldLock = DinghyIcon(IconRef.Ligature("shield_lock"), alternate = "shield_lock")
```

- [ ] **Step 2: Add both to `DinghyIcons.all`.** In the `val all = listOf(...)` block, on the final line that currently reads:

```kotlin
        AppSettings, PrinterSettings, ManagePrinters, TextSize, Rename,
```

change it to:

```kotlin
        AppSettings, PrinterSettings, ManagePrinters, TextSize, Rename, Fluorescent, ShieldLock,
```

- [ ] **Step 3: Run the icon registry test (it asserts non-blank/unique `alternate` + resolvable ref over `all`).**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.designsystem.icons.DinghyIconsTest'" 2>&1 | tr -d '\r'`
Expected: PASS (both new entries have unique alternates `fluorescent`/`shield_lock` and resolvable ligatures).

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
git commit -m "feat(icons): register Fluorescent + ShieldLock for App Settings rows"
```

---

## Phase 2: Make webcam-disable an app-global setting

### Task 2: Add `webcamEnabled` to `DisplayPrefs`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/settings/DisplayPrefs.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/settings/DisplayPrefsTest.kt`

- [ ] **Step 1: Write the failing tests.** `DisplayPrefsTest.kt` ALREADY EXISTS with the right harness — `newDataStore()` (a fresh temp-file `.preferences_pb`, for single-write tests) and an in-memory `MemDataStore` shape (for two-write tests, which the temp-file harness can't do on the Windows host due to the `.tmp→rename` race). Add these three `@Test`s to the existing class, mirroring the `keepScreenOn` tests verbatim — single-write tests on `newDataStore()`, the two-write round-trip on the in-memory store:

```kotlin
    @Test
    fun emptyStore_defaultsWebcamEnabledTrue() = runBlocking {
        val (dataStore, _) = newDataStore()
        val prefs = DisplayPrefs(dataStore)
        assertTrue("default webcamEnabled is true", prefs.webcamEnabled.first())
    }

    @Test
    fun setWebcamEnabledFalse_roundTrips() = runBlocking {
        val (dataStore, _) = newDataStore()
        val prefs = DisplayPrefs(dataStore)
        prefs.setWebcamEnabled(false)
        assertFalse("setWebcamEnabled(false) round-trips", prefs.webcamEnabled.first())
    }

    @Test
    fun setWebcamEnabledTrueAfterFalse_roundTrips() = runBlocking {
        // Two writes on one store → use the in-memory DataStore (the keepScreenOn two-write test's
        // shape) to dodge the Windows .tmp→rename race; the single-write tests above cover the file path.
        val mem = object : DataStore<Preferences> {
            private val state = kotlinx.coroutines.flow.MutableStateFlow<Preferences>(
                androidx.datastore.preferences.core.emptyPreferences(),
            )
            override val data: Flow<Preferences> = state
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences {
                val next = transform(state.value); state.value = next; return next
            }
        }
        val prefs = DisplayPrefs(mem)
        prefs.setWebcamEnabled(false)
        assertFalse("intermediate false state persisted", prefs.webcamEnabled.first())
        prefs.setWebcamEnabled(true)
        assertTrue("setWebcamEnabled(true) after false round-trips", prefs.webcamEnabled.first())
    }
```

(No new imports needed — `runBlocking`, `assertTrue`/`assertFalse`, `DataStore`, `Preferences`, `Flow`, `first` are already imported in that file.)

- [ ] **Step 2: Run to verify it fails.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.settings.DisplayPrefsTest'" 2>&1 | tr -d '\r'`
Expected: FAIL to compile — `webcamEnabled` / `setWebcamEnabled` unresolved.

- [ ] **Step 3: Implement in `DisplayPrefs.kt`.** Add the flow next to `keepScreenOn`, the setter next to `setKeepScreenOn`, and the key+default in the companion. After the existing `keepScreenOn` flow add:

```kotlin
    /**
     * Whether the webcam tile/surface is offered app-wide (moved from per-profile `Profile.webcamEnabled`
     * to app-global, 2026-06-15). Default TRUE — preserves today's always-available webcam behavior; a
     * printer with no cams still hides the tile via [AppContainer.webcamTileGate] (`count > 0`).
     * Fail-safe: a read error yields the default.
     */
    val webcamEnabled: Flow<Boolean> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[KEY_WEBCAM_ENABLED] ?: DEFAULT_WEBCAM_ENABLED }
```

After `setKeepScreenOn` add:

```kotlin
    /** Persist the app-global webcam-enabled toggle. */
    suspend fun setWebcamEnabled(on: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_WEBCAM_ENABLED] = on }
    }
```

In the `companion object` add:

```kotlin
        const val DEFAULT_WEBCAM_ENABLED = true
        private val KEY_WEBCAM_ENABLED = booleanPreferencesKey("webcam_enabled")
```

- [ ] **Step 4: Run to verify it passes.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.settings.DisplayPrefsTest'" 2>&1 | tr -d '\r'`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/settings/DisplayPrefs.kt app/src/test/java/works/mees/dinghy/ui/settings/DisplayPrefsTest.kt
git commit -m "feat(settings): add app-global webcamEnabled to DisplayPrefs"
```

### Task 3: Expose `webcamEnabled`/`setWebcamEnabled` on `AppContainer` and re-point `webcamTileEnabled`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`
- Test: `app/src/test/java/works/mees/dinghy/config/ProfileToggleTest.kt` (the `webcamTileGate` test already exists and stays green — it asserts the pure predicate, which is unchanged)

- [ ] **Step 1: Add the container flow + intent.** Directly after the existing keep-screen-on block (`val keepScreenOn = displayPrefs.keepScreenOn` / `fun setKeepScreenOn`, ~lines 402–411) add:

```kotlin
    /** App-global webcam-enabled (moved from per-profile, 2026-06-15) — process-scoped, durable. */
    val webcamEnabled: Flow<Boolean> = displayPrefs.webcamEnabled

    /** Persist the app-global webcam toggle, durably (process-lifetime writeScope, never composition). */
    fun setWebcamEnabled(on: Boolean) {
        writeScope.launch { displayPrefs.setWebcamEnabled(on) }
    }
```

- [ ] **Step 2: Re-point `webcamTileEnabled`.** Replace the current definition (lines 627–629) that combines with `activeProfile`:

```kotlin
    val webcamTileEnabled: Flow<Boolean> =
        combine(webcamCount, activeProfile) { count, p ->
            webcamTileGate(count, p?.webcamEnabled ?: true)
        }
```

with the app-global form:

```kotlin
    val webcamTileEnabled: Flow<Boolean> =
        combine(webcamCount, displayPrefs.webcamEnabled) { count, enabled ->
            webcamTileGate(count, enabled)
        }
```

Update the KDoc above it: the gate is now `count > 0 AND the app-global DisplayPrefs.webcamEnabled`, not the per-profile toggle. Leave `webcamTileGate(count, webcamEnabled)` (the pure predicate, line 902) UNCHANGED — its shape is identical.

- [ ] **Step 3: Verify the gate predicate test still passes** (it constructs no `Profile`, just calls `webcamTileGate`):

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.config.ProfileToggleTest.webcam_tile_gating_requires_both_count_and_toggle'" 2>&1 | tr -d '\r'`
Expected: PASS (other tests in that class still reference `Profile.webcamEnabled` and will compile until Phase 3 — run only this filter for now; it executes the gate test).

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/di/AppContainer.kt
git commit -m "feat(webcam): re-point webcamTileEnabled to app-global DisplayPrefs.webcamEnabled"
```

---

## Phase 3: Remove the per-printer webcam toggle

> After this phase the project compiles again. Do the edits together (they're mutually dependent) then build once.

### Task 4: Remove `Profile.webcamEnabled` and `setActiveWebcamEnabled`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/config/Profile.kt`
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`

- [ ] **Step 1: Delete the field from `Profile.kt`.**
  - Remove the `PersistedProfile.webcamEnabled` field + its comment (lines 44–47).
  - Remove the `Profile.webcamEnabled` field + its comment (lines 85–86).
  - Remove `webcamEnabled = webcamEnabled,` from `toPersisted()` (line 132).
  - Remove `webcamEnabled = p.webcamEnabled,` from `fromPersisted()` (line 159).

- [ ] **Step 2: Delete `setActiveWebcamEnabled` from `AppContainer.kt`** (lines 631–634):

```kotlin
    /** Set the active profile's per-profile webcam toggle (D-04), durable + lost-update-safe (WR-01). */
    fun setActiveWebcamEnabled(on: Boolean) {
        mutateActiveProfile { it.copy(webcamEnabled = on) }
    }
```

Remove the whole block. (No migration seeding is added — spec decision: app-global default On, no carry-over from old per-printer values.)

- [ ] **Step 3:** Don't build yet — `PrinterSettingsScreen` and the tests still reference the removed symbols. Proceed to Task 5 and 6, then build.

### Task 5: Remove the webcam row from Printer Settings + its previews

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterSettingsScreen.kt`
- Modify: `app/src/main/java/works/mees/dinghy/preview/PrinterSettingsPreviews.kt`

- [ ] **Step 1: In `PrinterSettingsScreen.kt`, delete `PrinterSettingsWebcamToggleRow`** (the whole `@Composable private fun PrinterSettingsWebcamToggleRow(...)`, lines 403–442).

- [ ] **Step 2: Delete its `item {}` call site** (lines 278–285):

```kotlin
                        // Row 3: Webcam toggle (DenseToggleRow pattern)
                        item {
                            PrinterSettingsWebcamToggleRow(
                                checked = webcamOn,
                                enabled = webcamEnabled,
                                onToggle = onWebcamToggle,
                                uDp = grid.uDp,
                            )
                        }
```

- [ ] **Step 3: Remove the webcam params from `PrinterSettingsContent`** (lines 156–172) — delete `webcamOn: Boolean,`, `webcamEnabled: Boolean,`, and `onWebcamToggle: (Boolean) -> Unit,`.

- [ ] **Step 4: Remove the webcam wiring from the stateful `PrinterSettingsScreen`** (lines 100–113) — delete the `webcamOn = activeProfile?.webcamEnabled ?: true,`, `webcamEnabled = activeProfile != null,`, and `onWebcamToggle = { container.setActiveWebcamEnabled(it) },` arguments passed into `PrinterSettingsContent(...)`.

- [ ] **Step 5: Remove the now-unused imports** in `PrinterSettingsScreen.kt` if they're no longer referenced anywhere else in the file: `androidx.compose.material3.Switch`, `androidx.compose.material3.SwitchDefaults` (only used by the deleted row — verify with a grep before removing). Leave `DinghyIcons`/`ListRowIcon` if still used by other rows.

- [ ] **Step 6: Fix `PrinterSettingsPreviews.kt`.** Every `PrinterSettingsContent(...)` call passes `webcamOn = …, webcamEnabled = …, onWebcamToggle = {}` (lines 45, 66, 87, 115, 140, 161, 186, 207, 219, 231, 243, 255, 267, 287, 304, 322). Remove those three named args from EVERY call site. Also delete the dedicated `PrinterSettingsWebcamOff` preview (lines ~176–195) — the toggle it demonstrated no longer exists.

### Task 6: Prune `ProfileToggleTest.kt` to the surviving gate test

**Files:**
- Modify: `app/src/test/java/works/mees/dinghy/config/ProfileToggleTest.kt`

- [ ] **Step 1: Delete the per-profile tests and helper** that reference the removed field: `private fun profile(... webcamEnabled ...)` (lines 24–25), `toggle_defaultsTrue_onFreshProfile` (30–34), `toggle_round_trips_through_mutate_active` (36–46), and `toggle_is_per_profile_not_global` (48–57). Remove any now-unused imports (`assertFalse`/`PersistedProfile`/`Profile` if no longer referenced).

- [ ] **Step 2: KEEP `webcam_tile_gating_requires_both_count_and_toggle`** (lines 61–68) — it calls `AppContainer.webcamTileGate(count, webcamEnabled)` with raw booleans and remains correct. (Optional: rename the file/class to `WebcamGateTest` for clarity; not required.)

### Task 7: Build + full host test sweep (project compiles again)

- [ ] **Step 1: Confirm no stray references remain.**

Run: `git grep -n "webcamEnabled\|setActiveWebcamEnabled" -- app/src` 2>&1 | tr -d '\r'`
Expected: hits ONLY in `DisplayPrefs.kt`, `AppContainer.kt` (the new app-global flow/intent + `webcamTileGate` param + `webcamTileEnabled`), `DisplayPrefsTest.kt`, `ProfileToggleTest.kt` (the gate test's `webcamEnabled =` arg), and possibly `ConnectionConfigTest.kt` (a JSON blob string). NO hits referencing `Profile.webcamEnabled`, `activeProfile?.webcamEnabled`, or `PrinterSettingsWebcamToggleRow`.

- [ ] **Step 2: Build debug + run host tests.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --rerun-tasks" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; all host tests pass, including `ConnectionConfigTest` (the old `webcamEnabled` JSON key still decodes via `ignoreUnknownKeys = true` — proves decode safety).

- [ ] **Step 3: Commit.**

```bash
git add -A
git commit -m "refactor(webcam): remove per-printer Profile.webcamEnabled; webcam is now app-global"
```

---

## Phase 4: Rebuild the App Settings screen (pure-selector Focus/Field)

### Task 8: Add the new strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the new strings** in the settings section (near line 569–582), following the existing style (sentence-case explanations, `%1$d` positional args):

```xml
    <!-- ============== App Settings — Focus/Field redesign (2026-06-15) ============== -->
    <string name="settings_app_settings_placeholder">Tap a setting to see what it does and change it.</string>
    <string name="settings_text_size_focus">Sets the app-wide text size. Affects every screen.</string>
    <string name="settings_keep_screen_on_focus">Keep the screen on while jiib is open, so the printer display never sleeps.</string>
    <string name="settings_webcam_focus">Show the webcam tile when a printer has a camera. Turn off if this device won\'t use the webcam or can\'t handle the stream.</string>
    <string name="settings_babystep_focus">Offer a Z-babystep adjust over the first layers of a print to dial in nozzle height.</string>
    <string name="settings_babystep_layers_count">%1$d layers</string>
    <string name="settings_battery_exempt_short">Exempt</string>
    <string name="settings_battery_optimized_short">Optimized</string>
    <string name="settings_battery_request">Allow background connection</string>
```

(`settings_webcam` = "Webcam", `common_on` = "ON", `common_off` = "OFF", the S/M/L letters `settings_text_size_s`/`_m`/`_l` (already exist at HEAD `strings.xml:679–681` — do NOT re-add, the merge will crash on duplicates), and the long `settings_battery_exempt`/`settings_battery_optimized` already exist and are reused as the battery Focus explanation.)

- [ ] **Step 2: Commit.**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(settings): strings for App Settings Focus/Field redesign"
```

### Task 9: Rewrite `AppSettingsContent` as a pure-selector Focus/Field screen

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/AppSettingsScreen.kt`

This is the core task. It replaces the flat scrolling `Column` (with inline `Switch`es, the `TokenTextField`, and `fieldFramed = false`) with: a `ListBlock` of selector rows + a `FootButtonBar` Back inside the default-framed Field, and a `FocusFrame` that swaps by selection. Model it on `TemperatureScreen` (selection-fills-Focus).

- [ ] **Step 1: Add the selection enum** at the top of the file (after the imports, before `AppSettingsScreen`):

```kotlin
/** Which App Settings row is selected; null = no selection (Focus shows the overview placeholder). */
enum class AppSetting { TextSize, KeepAwake, Webcam, Babystep, Battery }
```

- [ ] **Step 2: Update the stateful `AppSettingsScreen` wrapper.** Collect the app-global webcam flow and pass the new webcam params; drop nothing else from the collection except that the content seam no longer needs the babystep edit-buffer. Add after the `keepScreenOn` collection (line 97):

```kotlin
    // App-global webcam toggle (moved from per-printer, 2026-06-15) — process-scoped, durable.
    val webcamEnabled by container.webcamEnabled.collectAsStateWithLifecycle(true)
```

and in the `AppSettingsContent(...)` call (lines 116–144) add the two webcam args (place them after the keep-screen args):

```kotlin
        webcamEnabled = webcamEnabled,
        onWebcamToggle = { container.setWebcamEnabled(it) },
```

- [ ] **Step 3: Replace the entire `AppSettingsContent` body** (current lines 152–326) with the pure-selector version. The new seam adds `webcamEnabled`/`onWebcamToggle` and an `initialSelected` param (so previews can drive each Focus state), and removes the `layersField`/`layersEditing` edit-buffer:

```kotlin
@Composable
fun AppSettingsContent(
    fontScale: FontScale,
    onFontScale: (FontScale) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnToggle: (Boolean) -> Unit,
    webcamEnabled: Boolean,
    onWebcamToggle: (Boolean) -> Unit,
    babystepOn: Boolean,
    onBabystepToggle: (Boolean) -> Unit,
    babystepLayers: Int,
    onBabystepLayers: (Int) -> Unit,
    isExempt: Boolean,
    onRequestExempt: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    initialSelected: AppSetting? = null,
) {
    val t = LocalTokens.current
    var selected by rememberSaveable { mutableStateOf(initialSelected) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            focus = {
                AppSettingsFocus(
                    selected = selected,
                    fontScale = fontScale,
                    onFontScale = onFontScale,
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnToggle = onKeepScreenOnToggle,
                    webcamEnabled = webcamEnabled,
                    onWebcamToggle = onWebcamToggle,
                    babystepOn = babystepOn,
                    onBabystepToggle = onBabystepToggle,
                    babystepLayers = babystepLayers,
                    onBabystepLayers = onBabystepLayers,
                    isExempt = isExempt,
                    onRequestExempt = onRequestExempt,
                    uDp = grid.uDp,
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    item {
                        AppSettingRow(
                            selected = selected == AppSetting.TextSize,
                            onClick = { selected = if (selected == AppSetting.TextSize) null else AppSetting.TextSize },
                            icon = DinghyIcons.TextSize,
                            label = stringResource(R.string.settings_text_size),
                            indicator = stringResource(textSizeIndicatorRes(fontScale)),
                            uDp = grid.uDp,
                        )
                    }
                    item {
                        AppSettingRow(
                            selected = selected == AppSetting.KeepAwake,
                            onClick = { selected = if (selected == AppSetting.KeepAwake) null else AppSetting.KeepAwake },
                            icon = DinghyIcons.Fluorescent,
                            label = stringResource(R.string.settings_keep_screen_on),
                            indicator = stringResource(onOffRes(keepScreenOn)),
                            uDp = grid.uDp,
                        )
                    }
                    item {
                        AppSettingRow(
                            selected = selected == AppSetting.Webcam,
                            onClick = { selected = if (selected == AppSetting.Webcam) null else AppSetting.Webcam },
                            icon = DinghyIcons.LauncherWebcam,
                            label = stringResource(R.string.settings_webcam),
                            indicator = stringResource(onOffRes(webcamEnabled)),
                            uDp = grid.uDp,
                        )
                    }
                    item {
                        AppSettingRow(
                            selected = selected == AppSetting.Babystep,
                            onClick = { selected = if (selected == AppSetting.Babystep) null else AppSetting.Babystep },
                            icon = DinghyIcons.LineWeight,
                            label = stringResource(R.string.settings_babystep),
                            indicator = if (babystepOn) {
                                stringResource(R.string.settings_babystep_layers_count, babystepLayers)
                            } else {
                                stringResource(R.string.common_off)
                            },
                            uDp = grid.uDp,
                        )
                    }
                    item {
                        AppSettingRow(
                            selected = selected == AppSetting.Battery,
                            onClick = { selected = if (selected == AppSetting.Battery) null else AppSetting.Battery },
                            icon = DinghyIcons.ShieldLock,
                            label = stringResource(R.string.settings_battery_optimization),
                            indicator = stringResource(
                                if (isExempt) R.string.settings_battery_exempt_short
                                else R.string.settings_battery_optimized_short,
                            ),
                            indicatorColor = if (isExempt) t.go else t.text2,
                            uDp = grid.uDp,
                        )
                    }
                }
                FootButtonBar(uDp = grid.uDp) {
                    OutlinedControl(
                        label = "",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent,
                        icon = DinghyIcons.Back,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            },
        )
    }
}

/** S/M/L row indicator string for the current [fontScale]. */
private fun textSizeIndicatorRes(fontScale: FontScale): Int = when (fontScale) {
    FontScale.S -> R.string.settings_text_size_s
    FontScale.M -> R.string.settings_text_size_m
    FontScale.L -> R.string.settings_text_size_l
}

private fun onOffRes(on: Boolean): Int = if (on) R.string.common_on else R.string.common_off
```

> NOTE on `FontScale` cases: confirm the enum constants are exactly `S`, `M`, `L` (the imports use `FontScale.M`). If the enum uses different names, map all of them in `textSizeIndicatorRes` (a non-exhaustive `when` on an enum fails compilation, which will catch any mismatch immediately).

- [ ] **Step 4: Add the selector-row helper `AppSettingRow`** (replaces the deleted `AppSettingsDenseToggleRow` — rows are now pure selectors with a read-only text indicator, no `Switch`):

```kotlin
/**
 * A pure-selector App Settings row: leading icon + label + a right-aligned READ-ONLY text indicator.
 * No control lives in the row (R5 lists-first law) — tapping selects the row to load its Focus detail.
 */
@Composable
private fun AppSettingRow(
    selected: Boolean,
    onClick: () -> Unit,
    icon: works.mees.dinghy.designsystem.icons.DinghyIcon,
    label: String,
    indicator: String,
    uDp: Dp,
    indicatorColor: androidx.compose.ui.graphics.Color? = null,
) {
    val t = LocalTokens.current
    ListRow(
        selected = selected,
        onClick = onClick,
        uDp = uDp,
        leadingContent = { ListRowIcon(icon = icon, uDp = uDp, tint = t.text) },
        trailingContent = {
            Text(
                text = indicator,
                color = indicatorColor ?: t.text2,
                style = DinghyType.caption.toTextStyle(t),
            )
        },
    ) {
        Text(text = label, color = t.text, style = DinghyType.listLabel.toTextStyle(t))
        Spacer(Modifier.weight(1f))
    }
}
```

- [ ] **Step 5: Add the `AppSettingsFocus` composable** — the FocusFrame that swaps by selection. Each branch passes the e-stop wiring so the docked-e-stop morph works on every state:

```kotlin
@Composable
private fun AppSettingsFocus(
    selected: AppSetting?,
    fontScale: FontScale,
    onFontScale: (FontScale) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnToggle: (Boolean) -> Unit,
    webcamEnabled: Boolean,
    onWebcamToggle: (Boolean) -> Unit,
    babystepOn: Boolean,
    onBabystepToggle: (Boolean) -> Unit,
    babystepLayers: Int,
    onBabystepLayers: (Int) -> Unit,
    isExempt: Boolean,
    onRequestExempt: () -> Unit,
    uDp: Dp,
    isPrinting: Boolean,
    onEmergencyStop: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    @Composable
    fun frame(title: String, icon: works.mees.dinghy.designsystem.icons.DinghyIcon, body: @Composable ColumnScope.() -> Unit) {
        FocusFrame(
            title = title,
            icon = icon,
            uDp = uDp,
            modifier = modifier,
            isPrinting = isPrinting,
            onEmergencyStop = onEmergencyStop,
            onPanic = onEmergencyStop,
            content = body,
        )
    }

    when (selected) {
        null -> frame(stringResource(R.string.system_row_app_settings), DinghyIcons.AppSettings) {
            Text(
                text = stringResource(R.string.settings_app_settings_placeholder),
                color = t.text2,
                style = DinghyType.body.toTextStyle(t),
            )
        }
        AppSetting.TextSize -> frame(stringResource(R.string.settings_text_size), DinghyIcons.TextSize) {
            Text(stringResource(R.string.settings_text_size_focus), color = t.text2, style = DinghyType.body.toTextStyle(t))
            TextSizeSelector(selected = fontScale, onSelect = onFontScale, modifier = Modifier.padding(top = 12.dp))
        }
        AppSetting.KeepAwake -> frame(stringResource(R.string.settings_keep_screen_on), DinghyIcons.Fluorescent) {
            Text(stringResource(R.string.settings_keep_screen_on_focus), color = t.text2, style = DinghyType.body.toTextStyle(t))
            ToggleRow(
                label = stringResource(R.string.settings_keep_screen_on),
                checked = keepScreenOn,
                onToggle = onKeepScreenOnToggle,
                uDp = uDp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        AppSetting.Webcam -> frame(stringResource(R.string.settings_webcam), DinghyIcons.LauncherWebcam) {
            Text(stringResource(R.string.settings_webcam_focus), color = t.text2, style = DinghyType.body.toTextStyle(t))
            ToggleRow(
                label = stringResource(R.string.settings_webcam),
                checked = webcamEnabled,
                onToggle = onWebcamToggle,
                uDp = uDp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        AppSetting.Babystep -> frame(stringResource(R.string.settings_babystep), DinghyIcons.LineWeight) {
            Text(stringResource(R.string.settings_babystep_focus), color = t.text2, style = DinghyType.body.toTextStyle(t))
            ToggleRow(
                label = stringResource(R.string.settings_babystep),
                checked = babystepOn,
                onToggle = onBabystepToggle,
                uDp = uDp,
                modifier = Modifier.padding(top = 12.dp),
            )
            StepperRow(
                onDecrement = { onBabystepLayers((babystepLayers - 1).coerceAtLeast(1)) },
                onIncrement = { onBabystepLayers(babystepLayers + 1) },
                uDp = uDp,
                modifier = Modifier.padding(top = 12.dp),
                enabled = babystepOn,
                center = {
                    Text(
                        text = stringResource(R.string.settings_babystep_layers_count, babystepLayers),
                        color = t.text,
                        style = DinghyType.dataInline.toTextStyle(t),
                    )
                },
            )
        }
        AppSetting.Battery -> frame(stringResource(R.string.settings_battery_optimization), DinghyIcons.ShieldLock) {
            Text(
                text = stringResource(
                    if (isExempt) R.string.settings_battery_exempt else R.string.settings_battery_optimized,
                ),
                color = t.text2,
                style = DinghyType.body.toTextStyle(t),
            )
            OutlinedControl(
                label = stringResource(R.string.settings_battery_request),
                onClick = onRequestExempt,
                enabled = !isExempt,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                intent = Intent.Accent,
            )
        }
    }
}
```

- [ ] **Step 6: Delete the now-dead code** in `AppSettingsScreen.kt`: the `AppSettingsDenseToggleRow` private composable (old lines 337–370) and any imports only it used (`androidx.compose.material3.Switch`, `SwitchDefaults`). Also remove imports only used by the old field column: `verticalScroll`, `rememberScrollState`, `KeyboardType`, `onFocusChanged`, and the `TokenTextField` reference if no longer used here (grep the file first). Add imports now needed: `androidx.compose.foundation.layout.ColumnScope`, `androidx.compose.runtime.saveable.rememberSaveable`, `works.mees.dinghy.designsystem.components.ToggleRow`, `works.mees.dinghy.designsystem.components.StepperRow`, `works.mees.dinghy.designsystem.layout.ListBlock`, `works.mees.dinghy.designsystem.components.FootButtonBar`.

- [ ] **Step 7: Update the EXISTING preview call sites in the SAME change** (the new seam signature has new params, so `AppSettingsPreviews.kt` won't compile until these are fixed — and Step 8's `assembleDebug` compiles the preview source set). In `app/src/main/java/works/mees/dinghy/preview/AppSettingsPreviews.kt`, update the two helpers (`appSettingsAllOn`, `appSettingsAllOff`) and BOTH `fs=L` overflow previews to the new signature — add `webcamEnabled`/`onWebcamToggle`, keep `babystepLayers`/`onBabystepLayers`. Example for `appSettingsAllOn`:

```kotlin
@Composable
private fun appSettingsAllOn() {
    AppSettingsContent(
        fontScale = FontScale.M,
        onFontScale = {},
        keepScreenOn = true,
        onKeepScreenOnToggle = {},
        webcamEnabled = true,
        onWebcamToggle = {},
        babystepOn = true,
        onBabystepToggle = {},
        babystepLayers = 5,
        onBabystepLayers = {},
        isExempt = false,
        onRequestExempt = {},
        onBack = {},
    )
}
```

Apply the same arg shape (`webcamEnabled`/`onWebcamToggle` added) to `appSettingsAllOff` (with its `keepScreenOn = false`, `isExempt = true` values) and both `fs=L` previews. (The NEW per-selection previews are added in Task 10.)

- [ ] **Step 8: Build to verify it compiles** (Compose has no cheap unit test for layout; compilation + the preview matrix + the conformance tests are the gates):

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Run the font-conformance + icon tests** (no inline `fontSize`/`fontFamily`; icons valid):

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.theme.*FontConformance*' --tests 'works.mees.dinghy.designsystem.icons.DinghyIconsTest'" 2>&1 | tr -d '\r'`
Expected: PASS (all UI text uses `DinghyType.*.toTextStyle(t)` role styles — no inline font).

- [ ] **Step 10: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/screen/AppSettingsScreen.kt app/src/main/java/works/mees/dinghy/preview/AppSettingsPreviews.kt
git commit -m "feat(settings): rebuild App Settings as pure-selector Focus/Field in the base frame"
```

### Task 10: Add per-selection previews (all five Focus states)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/preview/AppSettingsPreviews.kt`

> The existing call-site updates were already done in Task 9 Step 7. This task only ADDS the new per-selection previews so the Focus detail of EACH of the five settings is covered (spec requires all five).

- [ ] **Step 1: Add `import works.mees.dinghy.ui.screen.AppSetting`** at the top of the file.

- [ ] **Step 2: Append the five per-selection previews** (one per `AppSetting`):

```kotlin
@Nexus7Previews
@Composable
private fun AppSettingsFocusTextSize() = PreviewBox(colorfulDark) {
    AppSettingsContent(
        fontScale = FontScale.M, onFontScale = {},
        keepScreenOn = true, onKeepScreenOnToggle = {},
        webcamEnabled = true, onWebcamToggle = {},
        babystepOn = true, onBabystepToggle = {},
        babystepLayers = 5, onBabystepLayers = {},
        isExempt = false, onRequestExempt = {}, onBack = {},
        initialSelected = AppSetting.TextSize,
    )
}

@Nexus7Previews
@Composable
private fun AppSettingsFocusKeepAwake() = PreviewBox(colorfulDark) {
    AppSettingsContent(
        fontScale = FontScale.M, onFontScale = {},
        keepScreenOn = true, onKeepScreenOnToggle = {},
        webcamEnabled = true, onWebcamToggle = {},
        babystepOn = true, onBabystepToggle = {},
        babystepLayers = 5, onBabystepLayers = {},
        isExempt = false, onRequestExempt = {}, onBack = {},
        initialSelected = AppSetting.KeepAwake,
    )
}

@Nexus7Previews
@Composable
private fun AppSettingsFocusWebcam() = PreviewBox(colorfulDark) {
    AppSettingsContent(
        fontScale = FontScale.M, onFontScale = {},
        keepScreenOn = true, onKeepScreenOnToggle = {},
        webcamEnabled = true, onWebcamToggle = {},
        babystepOn = true, onBabystepToggle = {},
        babystepLayers = 5, onBabystepLayers = {},
        isExempt = false, onRequestExempt = {}, onBack = {},
        initialSelected = AppSetting.Webcam,
    )
}

@Nexus7Previews
@Composable
private fun AppSettingsFocusBabystep() = PreviewBox(colorfulDark) {
    AppSettingsContent(
        fontScale = FontScale.M, onFontScale = {},
        keepScreenOn = true, onKeepScreenOnToggle = {},
        webcamEnabled = true, onWebcamToggle = {},
        babystepOn = true, onBabystepToggle = {},
        babystepLayers = 5, onBabystepLayers = {},
        isExempt = false, onRequestExempt = {}, onBack = {},
        initialSelected = AppSetting.Babystep,
    )
}

@Nexus7Previews
@Composable
private fun AppSettingsFocusBattery() = PreviewBox(colorfulDark) {
    AppSettingsContent(
        fontScale = FontScale.M, onFontScale = {},
        keepScreenOn = true, onKeepScreenOnToggle = {},
        webcamEnabled = true, onWebcamToggle = {},
        babystepOn = true, onBabystepToggle = {},
        babystepLayers = 5, onBabystepLayers = {},
        isExempt = false, onRequestExempt = {}, onBack = {},
        initialSelected = AppSetting.Battery,
    )
}
```

- [ ] **Step 3: Build (compiles the preview source set).**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/preview/AppSettingsPreviews.kt
git commit -m "test(preview): update App Settings preview matrix + per-selection Focus previews"
```

### Task 11: Full sweep + R8 release build + on-device UAT

- [ ] **Step 1: Full host test suite + lint gates.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --rerun-tasks" 2>&1 | tr -d '\r'`
Expected: all green (FontConformance, IconCatalog/DinghyIcons, ConnectionConfig decode, DisplayPrefs, ProfileToggle gate).

- [ ] **Step 2: R8 release build** (the project's shrink gate — verifies no kept-rule regressions):

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleRelease --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify the `screenOwnsEstop` registration (expected no-op).** The set is declared in `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` (~lines 940–962) and `NavDest.AppSettings` is ALREADY present (~line 960) — so this is verify-only. Grep to confirm:

Run: `git grep -n "screenOwnsEstop\|NavDest.AppSettings" -- app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt 2>&1 | tr -d '\r'`
Expected: `NavDest.AppSettings` appears inside the `screenOwnsEstop` set. If (unexpectedly) it does NOT, add it — otherwise the docked e-stop morph won't fire and a Webcam/Theme-style shell FloatingEStop fallback would double up.

- [ ] **Step 4: On-device UAT — BOTH devices (flox + moto).** Build, sign, install the matching ABI slice on each (per the two-device rule). Verify on-device:
  - Each row selects and shows its Focus explanation + control; the row's indicator is read-only (tapping the row never flips the setting in place).
  - Text Size S/M/L changes app-wide; the row indicator updates.
  - Keep Awake / Webcam toggles flip from the Focus `ToggleRow`; row reads `ON`/`OFF`.
  - Babystep: toggle enables/disables; `StepperRow` ± changes the layer count (floored at 1); the stepper is disabled when babystep is off; row reads `N layers` / `Off`.
  - Battery: row reads `Exempt`/`Optimized`; the Focus button deep-links to the system dialog and is disabled when already exempt.
  - Webcam app-global behavior: turning Webcam off hides the webcam tile on a printer that HAS a cam; on confirms it returns. The per-printer webcam toggle is GONE from Printer Settings.
  - Back is the first `FootButtonBar` button, pinned below the list; list edges and bar edges align. Works in portrait AND landscape.
  - While printing, the Focus header glyph morphs to the red e-stop on every selection state.

- [ ] **Step 5: Final commit (if UAT required tweaks, commit them; otherwise the work is already committed).**

```bash
git add -A && git commit -m "feat(settings): App Settings Focus/Field redesign — on-device UAT pass" || true
```

---

## Self-Review

**Spec coverage:**
- Pure-selector rows + read-only indicator → Task 9 (`AppSettingRow`). ✓
- Focus explanation + control per setting → Task 9 (`AppSettingsFocus`). ✓
- Whole page in the base frame (RegisteredRegion default slots, `ListBlock` + `FootButtonBar` flush, `FocusFrame`) → Task 9 (drops `fieldFramed=false`). ✓
- Back → first `FootButtonBar` button → Task 9. ✓
- Webcam → app-global, default On, no migration → Tasks 2–4; per-printer removal Tasks 4–6. ✓
- Babystep → `StepperRow` retiring the `TokenTextField` → Task 9. ✓
- Two new icons + `DinghyIcons.all` → Task 1. ✓
- Decode safety for old JSON → Task 7 Step 2 (ConnectionConfigTest). ✓
- Compile-breaking consumers (`PrinterSettingsPreviews`, `ProfileToggleTest`) → Tasks 5–6. ✓
- Preview matrix + per-selection coverage → Task 10. ✓

**Placeholder scan:** No "TBD"/"handle edge cases"/"similar to" — all code is inline. The two FontScale/`screenOwnsEstop` notes are explicit verify-or-fix instructions, not placeholders.

**Type consistency:** `AppSetting` enum used identically across rows, Focus, and previews. `webcamEnabled`/`setWebcamEnabled` names consistent from `DisplayPrefs` → `AppContainer` → screen. `onOffRes`/`textSizeIndicatorRes` defined and used. `AppSettingsContent` seam signature identical in screen + previews (Task 9 Step 3 and Task 10).
