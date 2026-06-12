---
phase: 28-system-settings-cluster
reviewed: 2026-06-12T00:00:00Z
depth: standard
files_reviewed: 43
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/config/Profile.kt
  - app/src/main/java/works/mees/dinghy/designsystem/components/ListRow.kt
  - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
  - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
  - app/src/main/java/works/mees/dinghy/preview/AboutPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/PrintersPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
  - app/src/main/java/works/mees/dinghy/preview/SettingsPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/SysInfoPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/SystemPagePreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/ThemeEditorPreviews.kt
  - app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt
  - app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusUiModel.kt
  - app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
  - app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/AboutScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/SystemPageScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/TokenTextField.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/DevThemeCyclerOverlay.kt
  - app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt
  - app/src/main/res/values/strings.xml
  - app/src/test/java/works/mees/dinghy/config/ConnectionConfigTest.kt
  - app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt
  - app/src/test/java/works/mees/dinghy/theme/ProfileThemeSeedTest.kt
  - app/src/test/java/works/mees/dinghy/theme/StatusOverrideTokenBridgeTest.kt
  - app/src/test/java/works/mees/dinghy/theme/ThemePrefsFallbackTest.kt
  - app/src/test/java/works/mees/dinghy/theme/ThemeResolverBakeTest.kt
  - app/src/test/java/works/mees/dinghy/theme/ThemeResolverTest.kt
  - app/src/test/java/works/mees/dinghy/theme/TokenDeltaSerializationTest.kt
  - app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusUiModelTest.kt
  - app/src/test/java/works/mees/dinghy/ui/route/FootGunDestsTest.kt
  - app/src/test/java/works/mees/dinghy/ui/route/HomeActionTest.kt
  - app/src/test/java/works/mees/dinghy/ui/route/NavDestRoundTripTest.kt
  - app/src/test/java/works/mees/dinghy/ui/screen/PrintersModeToggleTest.kt
  - tools/verify_ligatures.py
findings:
  critical: 1
  warning: 9
  info: 10
  total: 20
status: issues_found
---

# Phase 28: Code Review Report

**Reviewed:** 2026-06-12
**Depth:** standard
**Files Reviewed:** 43
**Status:** issues_found

## Summary

Phase 28 (system-settings-cluster) review covered the System page + NavDest.System wiring, the rebuilt Printers/Settings/About/SysInfo screens, the theme editor with the new S/V square, the idle-list rehoming, the maxItems deletion, six new icon tokens, and the 1U-floor UAT fix. The pure-logic substrate (NavDest, HomeAction, PrintStatusUiModel, ThemePrefs sanitize, PrinterMode state machine) is solid and well-tested. The defects cluster in the rebuilt interactive screens: one credential-resurrection logic bug in the Printers connection editor (BLOCKER), two project-recurring trap instances (a `pointerInput` stale-closure that breaks the dev-cycler drag this phase's own commit claimed to restore, and a DataStore write/reseed race in the Settings babystep field), a Back-press ordering hole around the delete ConfirmGuard, and a systematic break of the project's own stringResource and preview-seam-delegation laws on two of the new screens.

## Critical Issues

### CR-01: "Clear key" followed by Save silently resurrects the cleared API key

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt:680-695, 718-753`
**Issue:** The connection editor holds a STALE `profile` snapshot (captured into `EditorTarget.Edit(profile)` at row-tap time; never refreshed from the profiles flow). "Clear key" persists `apiKey = null` via `container.saveProfile(...)` and flips `keyAlreadySaved = false` — the UI now tells the user no key is saved. But the `profile` parameter object still carries the OLD key. If the user then taps **Save** with the key field blank (the normal case), the Save path runs:

```kotlin
val resolvedKey = AppContainer.resolveApiKeyEdit(
    existing = profile?.apiKey,   // ← the OLD, just-cleared key
    fieldInput = apiKey,          // blank
    cleared = false,
)
```

`resolveApiKeyEdit` returns `existing` for a blank field → the credential the user explicitly removed is written back to disk, with the UI having claimed it was gone. This is the exact "raw key never round-trips into the UI" contract (T-28-06-01 / MEDIUM-5 / V7) being defeated by its own implementation.
**Fix:** Track the cleared state locally and feed it to the Save-time resolution, e.g.:

```kotlin
var keyCleared by remember { mutableStateOf(false) }
// Clear key button: keyCleared = true; keyAlreadySaved = false; apiKey = ""
//   (optionally drop the immediate saveProfile — defer the null write to Save)
// Save:
val resolvedKey = AppContainer.resolveApiKeyEdit(
    existing = if (keyCleared) null else profile?.apiKey,
    fieldInput = apiKey,
    cleared = keyCleared && apiKey.isBlank(),
)
```

Alternatively, re-derive the editor's `profile` from the live `profileStore.profiles` flow by id so the snapshot can never go stale. Add a host test: clear → save(blank) must persist `apiKey == null`.

## Warnings

### WR-01: Dev-cycler panel drag broken by a stale `pointerInput` closure (the recurring trap)

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/DevThemeCyclerOverlay.kt:188-224`
**Issue:** The D-20 drag strip (commit `db4714f`, whose stated purpose was "restore dev cycler drag") accumulates drag deltas onto `pos`, a **composition-scope val** captured by the `pointerInput(boxSize)` block:

```kotlin
val pos = offset ?: Offset(startInsetPx, startInsetPx)   // composition scope
...
.pointerInput(boxSize) { ... drag(down.id) { change ->
    offset = Offset(pos.x + delta.x, pos.y + delta.y)    // pos is STALE
```

`pointerInput` only re-captures its lambda when the key changes; `boxSize` stabilizes after the first layout. From then on, every drag event computes `oldCapturedPos + thisEvent'sIncrementalDelta` — deltas never accumulate, so the panel jitters around its captured position instead of following the finger, and every subsequent drag gesture restarts from the original inset position. This is the same `pointerInput` stale-closure class that bit Phase 19 (scrubber `working`).
**Fix:** Read the live state inside the handler instead of the captured snapshot:

```kotlin
drag(down.id) { change ->
    val delta = change.positionChange()
    val cur = offset ?: Offset(startInsetPx, startInsetPx)
    offset = Offset((cur.x + delta.x).coerceIn(0f, maxX), (cur.y + delta.y).coerceIn(0f, maxY))
    change.consume()
}
```

(`offset` is a `MutableState` delegate — reading it in the gesture lambda is live; only the derived `pos` val is stale.) Verify on-device that the panel actually relocates.

### WR-02: Back press while the delete ConfirmGuard is open never cancels the delete prompt

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt:317-340`
**Issue:** `BackHandler(printerMode != PrinterMode.Normal || editingTarget != null)` does not include `pendingDelete != null`, and `ConfirmGuard` registers no BackHandler of its own (verified). With the guard open (DeleteArmed + pendingDelete set): first Back disarms the invisible mode underneath the guard; second Back finds the handler disabled and NavHost pops the whole Devices route out from under an open destructive-confirm overlay. Back should dismiss the guard (equivalent to "Keep"), in priority order above mode-disarm.
**Fix:**

```kotlin
BackHandler(pendingDelete != null || printerMode != PrinterMode.Normal || editingTarget != null) {
    when {
        pendingDelete != null -> pendingDelete = null
        editingTarget != null -> editingTarget = null
        else -> printerMode = disarm()
    }
}
```

### WR-03: Settings babystep field commits per-keystroke and re-seeds from the flow — clobber race while typing

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt:156, 206-218`
**Issue:** `var layersField by remember(babystepLayers) { mutableStateOf(babystepLayers.toString()) }` re-seeds the text buffer on every persisted-value emission, while `onValueChange` writes every keystroke through `onBabystepLayers` (async DataStore round-trip). Typing "12": "1" is written; if the flow emission for `1` lands after the user has typed "2" (DataStore latency is real on the Nexus-7 flash this project budgets for), the `remember(babystepLayers)` re-key resets the field to "1", discarding the user's "2" mid-edit. Also, typing "0" is silently coerced to 1 by `BabystepPrefs.setLayerCount`, and the echo rewrites the field under the cursor. Same value-not-sticking family as the Phase-19 inline-scrubber regression.
**Fix:** Don't reseed while the field is being edited — commit on IME-done/focus-loss instead of per keystroke, or gate the reseed on focus:

```kotlin
var editing by remember { mutableStateOf(false) }
LaunchedEffect(babystepLayers) { if (!editing) layersField = babystepLayers.toString() }
// TokenTextField: onFocusChanged { editing = it.isFocused; if (!it.isFocused) commit() }
```

### WR-04: PrintersContent / ThemeEditorContent preview seams duplicate the live layout instead of being delegated to — drift already exists

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt:123-269` (vs 297-512), `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt:956-1087` (vs 94-520)
**Issue:** The preview-first convention exists so "drift is impossible" because the running app and the previews share ONE layout body (the `PrintStatusContent` pattern, done correctly in this same phase at PrintStatusScreen.kt:441). `PrintersScreen` and `ThemeEditorScreen` instead maintain a second, hand-copied ~120/~150-line render body. Drift is already present: `ThemeEditorContent`'s status-picker branch always shows the Colorful sublabel copy (line 1004) while the live screen branches on `t.mode` (lines 270-277); future edits to the live screens will not show up in previews. This defeats the entire point of the preview matrices shipped in 28-06/28-08.
**Fix:** Make `PrintersScreen`/`ThemeEditorScreen` resolve state + lambdas and call their `*Content` seam, exactly as `PrintStatusScreen` does. Delete the duplicated bodies.

### WR-05: Systematic hardcoded English strings on the rebuilt screens (stringResource law)

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt` (entire screen: "Edit theme", "Reset theme?", "Pool color %d", "Pick a color", "Saturation / Brightness", "Clear", "Done", "Dark"/"Light", "Randomize", "Reset", PALETTE_MODES labels, StatusSlot/PaletteMode `.label()`, all sublabels); `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt:613, 631, 641, 650, 671, 682, 699, 719, 773-779` ("Host is required.", "Port must be 1–65535.", "API key (leave blank to keep saved key)", "Key saved", "Scanning…"/"Scan (mDNS)", "Clear key", "No printers found…", "Save", `ConnectionState.label()` strings, "ON"/"OFF" pills); `app/src/main/java/works/mees/dinghy/ui/screen/AboutScreen.kt:225` ("ON"/"OFF" pill)
**Issue:** PREVIEW_AND_TOKENS.md makes `stringResource` strings a day-one LAW for new/rebuilt screens, and the phase shipped pseudolocale (`en-XA`) preview panels explicitly to catch this — panels which now give false confidence because the literals render as plain English through them. Existing keys are even bypassed: `common_save` exists in strings.xml but the editor's Save button hardcodes "Save". The ThemeEditor was rebuilt this phase (D-16 S/V picker) with zero string resources.
**Fix:** Extract every user-visible literal above into `strings.xml` keys (the `printers_*`/`theme_*` namespaces already exist) and route through `stringResource`. The Dev cycler overlay's labels (dev-only tool) may stay literal if the owner sanctions it — note the exemption in the file if so.

### WR-06: SysInfo health chip constructs an ad-hoc, unregistered DinghyIcon — bypasses the registry/subset gate

**File:** `app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt:339`
**Issue:** `HealthState.Warn -> Triple(DinghyIcon(IconRef.Ligature("warning"), alternate = "sysinfo_warn"), …)` creates an icon token inline that is not in `DinghyIcons.all` — invisible to `DinghyIconsTest` uniqueness/drift guards and to the planned `tools/subset-symbols` registry iteration (it only survives subsetting today because `DinghyIcons.Warning` happens to register the same ligature). The icon-registry-only law (and the Phase-25 WR-07 / Phase-27 WR-04 precedents in this very registry file) forbids inline `IconRef.Ligature` construction at call sites.
**Fix:** Use the already-registered token: `HealthState.Warn -> Triple(DinghyIcons.Warning, t.heat, R.string.sysinfo_health_warn)`. If a distinct sysinfo token is wanted, register it in `DinghyIcons` + `all` and allow-list the shared ligature in `DinghyIconsTest`.

### WR-07: Dead `onAddPrinter` parameter + stale AppShell wiring left over from the old Add-printer flow

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt:299`, `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:732`
**Issue:** The 28-06 rebuild made Add open the inline editor (`editingTarget = EditorTarget.New`); the `onAddPrinter` parameter is now never referenced in the screen body. AppShell still wires `onAddPrinter = { navController.navigate(NavDest.Settings) }` — dead code that documents the WRONG behavior (Add → Settings) and will mislead the next editor of either file. `RootController.kt:126` passes a `{}` no-op to the same dead seam.
**Fix:** Delete the `onAddPrinter` parameter from `PrintersScreen` and remove both call-site lambdas.

### WR-08: 1U touch-floor missing on the connection editor's tap rows (GAP-A fix incomplete)

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt:794-800 (SecureToggleRow), 846-852 (DiscoveredPrinterRow)`
**Issue:** The 28-09 GAP-A UAT fix ("restore the 1U height floor everywhere") added `heightIn(min = uDp)` to ListRow, DevEnableRow, and PowerStubRow — but the connection editor's two tappable rows still size themselves with fixed `vertical = 14.dp` padding and no minimum height. At S text size these fall below the 1U/≥64dp floor the owner ruled on for ALL surfaces.
**Fix:** Thread `uDp` (the editor is a `Column` without a grid — wrap in `BoxWithConstraints` + `rememberUnitGrid` like its siblings) and add `.heightIn(min = uDp)` to both rows.

### WR-09: SaturationValueSquare keys `pointerInput` on `hue` and captures its callbacks without `rememberUpdatedState`

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt:858-880`
**Issue:** Two related problems. (a) The gesture handler never uses `hue`, but keying on it restarts the suspending gesture block on every hue change — including mid-gesture if the wheel and square are touched concurrently (multi-touch cancels the active S/V drag). (b) Conversely, when `hue` does NOT change, the captured `onHandleMove`/`onSettle` lambdas — which close over `hasActive` and `slot`/`statusSlot` by value at composition time — are not refreshed; a profile appearing/disappearing while a picker is open routes the settle write to the wrong target (global vs profile) until the next hue change. Same closure-staleness class as WR-01, lower likelihood.
**Fix:** Key on `Unit` and wrap the callbacks: `val currentOnSettle by rememberUpdatedState(onSettle)` (likewise `onHandleMove`), invoking the wrapped values inside the gesture block.

## Info

### IN-01: Dead code — `hueToArgbLong`, `onScanSpool`/`onOpenSpool` in PrintStatusScreen

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt:812-815`; `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:74, 82`
**Issue:** `hueToArgbLong` has zero callers (superseded by `hsvToArgbLong`). `PrintStatusScreen`'s `onScanSpool` parameter and the `onOpenSpool` alias are declared but never invoked — AppShell still wires `onScanSpool = { nav.scanActive = true }` into a dead seam, meaning the active-spool card's Scan action documented in the KDoc no longer exists. Confirm the Scan-action removal was intentional; either way remove the dead symbols.
**Fix:** Delete `hueToArgbLong`, `onOpenSpool`; remove or re-wire `onScanSpool`.

### IN-02: AppDrawer was deleted but its registry/string artifacts survive

**File:** `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt:63`, `app/src/main/res/values/strings.xml:109, 128`
**Issue:** `LauncherDrawer` ("more_horiz") remains a registered token in `DinghyIcons.all` and `cd_launcher_drawer` remains in strings.xml although the drawer is gone — the planned font subset (which iterates the registry) will carry a dead glyph. `system_row_label` ("System") has no consumer either (the shortcut tile uses `home_foot_system`).
**Fix:** Remove `LauncherDrawer` from the registry + `all`, drop `more_horiz` from `tools/verify_ligatures.py` NEEDED, delete the two orphan strings.

### IN-03: AppShell KDoc still describes the deleted swipe-up AppDrawer as "the ONE navigation surface"

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:110-113`
**Issue:** The class-level KDoc (the file's orientation doc for future phases) references "[AppDrawer] (D-14)" which no longer exists. Doc rot on a load-bearing file.
**Fix:** Rewrite the paragraph to describe NavDest.System + the idle foot bar as the navigation surfaces.

### IN-04: TokenTextField label below the 15sp project floor

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/TokenTextField.kt:66`
**Issue:** `fsSp(13f, t.fs)` for the field label is below the established 15sp metadata floor ([[dinghy-font-sizes-too-small]]).
**Fix:** Raise to `fsSp(15f, t.fs)`.

### IN-05: Theme editor never surfaces global-idle override markers despite collecting the global tuple

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt:174, 248, 461, 482`
**Issue:** With no active profile, the pool/status grids read `activeProfile?.poolOverrides ?: emptyMap()` so the "overridden" accent dot never shows for global-idle overrides, and slot pickers seed from baked tokens instead of the stored ARGB — even though `globalTuple` (with `poolOverrides`/`statusOverrides`) is already collected in the same scope. The status-picker comment admits the limitation; the data to fix it is one expression away.
**Fix:** Fall back to `globalTuple.poolOverrides`/`globalTuple.statusOverrides` in the idle branch.

### IN-06: `nullStateFlow()` allocates a fresh flow per recomposition despite its "no allocation" comment

**File:** `app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt:72-74, 86-87`
**Issue:** `(holder?.identity ?: nullStateFlow())` evaluates inside composition; while idle, each recomposition creates three new `MutableStateFlow` instances and restarts the `collectAsStateWithLifecycle` producers. Harmless behaviorally but contradicts its own comment and churns.
**Fix:** `val idleFlow = remember { MutableStateFlow(null) }`-style fallbacks (one per field), or `remember(holder)` the three resolved flows.

### IN-07: Printers Focus card falls back to the FIRST profile when the active id is dangling

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt:147, 361`
**Issue:** `profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()` shows a non-active profile in the "active printer" DetailCard, paired with the LIVE connection state ring/label of whatever session actually exists — a misleading combination when the active id is null/dangling (the D-11/D-12 dangling-id path resolves to "no active profile" everywhere else).
**Fix:** Drop the `?: profiles.firstOrNull()` fallback and render the empty/none state, or visibly mark the card "not active".

### IN-08: Raw color literals for the S/M/L segment ink

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt:545`
**Issue:** `Color(0xFF101010)` / `Color(0xFFF5F5F5)` contrast inks on the pool-color fills. The fill itself is a sanctioned data carve-out; the ink literals are an undocumented extension of it (THEME-01 says chrome routes through tokens).
**Fix:** Document the ink as part of the data carve-out in the comment, or derive from tokens (`t.bg`/`t.text` luminance pick).

### IN-09: Shared sat/value state bleeds between the seed S/V square and the slot pickers

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt:115-116, 182-187, 252-257`
**Issue:** The slot pickers reseed the screen-shared `sat`/`value` vars from the stored ARGB; after closing a picker, the seed-section S/V square silently shows the slot's S/V instead of what the user last set there. Cosmetic (seed persists hue-only), but surprising.
**Fix:** Give the slot pickers their own `remember(slot)` sat/value state.

### IN-10: SystemPage minor quality nits

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/SystemPageScreen.kt:131, 141, 276`
**Issue:** `items(systemNavRows())` rebuilds the row list every recomposition and supplies no `key`; row icons use a fixed `22.dp` while sibling list surfaces scale icons with `fsSp(22f, t.fs).dp` (PrintStatusField.kt:106) — the System rows won't scale with the S/M/L setting.
**Fix:** Hoist `systemNavRows()` to a top-level `val`, add `key = { it.dest::class.simpleName ?: "" }`, use `fsSp(22f, t.fs).dp` for the icon size (also applies to SystemInformationScreen's `22.dp` rows for consistency).

---

_Reviewed: 2026-06-12_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
