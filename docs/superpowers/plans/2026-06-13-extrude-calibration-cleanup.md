# Extrude + Calibration Hub Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish two existing screens — center the Extrude Focus readouts, fix its footer button intents, wire its Spool button to the real Spoolman page with the reactive spool icon; and on the Calibration Hub swap to the owner's bucket-assigned routine icons and restructure the Focus (dynamic header, description fills the body, Open button bottom-docked).

**Architecture:** Pure UI edits to two Compose screens plus the `DinghyIcons` registry and one nav wire-through in `AppShell`. No data-model, holder, or Field-list changes. Existing component classes (`FocusFrame`, `FootButtonBar`, `OutlinedControl`, `SpoolGlyph`, `FieldButton`) are reused; `FieldButton` gains an icon-only mode.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation-Compose, JUnit (unit), the Windows-side Gradle helper (`E:\Android\gw.bat`).

**Build/test commands (this repo builds Windows-side from WSL):**
- Unit tests: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" | tr -d '\r'`
- Assemble (split-ABI debug for both test devices): `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
- Exit code is authoritative. Force-rebuild before any on-device UAT (`--rerun-tasks`) and confirm APK mtime is newer than the last fix commit (stale-APK trap).

**On-device verification:** install the matching ABI slice to BOTH test devices — flox (Nexus 7 2013, `armeabi-v7a`, id `0a64b42e`) and moto (Moto G Play 2024, `arm64-v8a`, id `ZY22LBDRM9`). The owner navigates/eyeballs the visual checks.

---

## File Structure

| File | Change |
|---|---|
| `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` | Swap 5 `Routine*` ligatures; update the OWNER-LOCKED date comment. |
| `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt` | Add a test pinning the 5 routine ligatures to the bucket assignments. |
| `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt` | Dynamic FocusFrame header from `selected`; restructure `HubRoutineFocus` (drop body icon/title, description fills, Open bottom-docked). |
| `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt` | Center readouts; footer intents (Back→Accent, Load→Warn); Spool button → reactive `SpoolGlyph` + `onOpenSpool`; `FieldButton` icon-only mode; add `onOpenSpool` param to both `ExtrudeScreen` overloads + `ExtrudeContent`. |
| `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` | Pass `onOpenSpool = { navController.navigate(NavDest.Spool) }` to `ExtrudeScreen`. |

---

## Task 1: Swap Calibration routine icons to bucket assignments

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt:218-228`
- Test: `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test method inside `class DinghyIconsTest` (after `registryDrawableEntries_areOnlyTheCustomKeepers`, before the closing brace):

```kotlin
    /**
     * (2026-06-13 cleanup) The five calibration-hub routine glyphs are pinned to the owner's
     * icon-bucket assignments (img/material-icon-bucket.json notes). A drift here means a routine
     * shows the wrong glyph in the hub list + Focus header.
     */
    @Test
    fun routineGlyphs_matchOwnerBucketAssignments() {
        fun lig(icon: DinghyIcon) = (icon.primary as IconRef.Ligature).name
        assertEquals("detector", lig(DinghyIcons.RoutineProbeCalibrate))
        assertEquals("blur_linear", lig(DinghyIcons.RoutineBedMesh))
        assertEquals("rule_settings", lig(DinghyIcons.RoutineScrewsTilt))
        assertEquals("linear_scale", lig(DinghyIcons.RoutineZTilt))
        assertEquals("linked_services", lig(DinghyIcons.RoutineQgl))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.designsystem.icons.DinghyIconsTest --no-daemon" | tr -d '\r'`
Expected: FAIL — `routineGlyphs_matchOwnerBucketAssignments` asserts `"detector"` but gets `"straighten"` (the four others likewise mismatch).

- [ ] **Step 3: Apply the icon swap**

In `DinghyIcons.kt`, replace lines 218-228. Change the comment date and the five ligature strings (keep the `alternate` handles unchanged — they are the stable remap keys):

```kotlin
    // --- Phase-27 calibration hub routine glyphs (27-01; ligatures RE-ASSIGNED 2026-06-13 to the
    // owner's icon-bucket selections in img/material-icon-bucket.json — superseding the 27-01
    // placeholders. The full Material Symbols Outlined font is bundled, so every ligature renders.)
    // Token names follow the Routine* convention from 27-PATTERNS.md §"Icon Registration Gate".
    val RoutineProbeCalibrate = DinghyIcon(IconRef.Ligature("detector"), alternate = "routine_probe_calibrate")
    val RoutineBedMesh = DinghyIcon(IconRef.Ligature("blur_linear"), alternate = "routine_bed_mesh")
    val RoutineScrewsTilt = DinghyIcon(IconRef.Ligature("rule_settings"), alternate = "routine_screws_tilt")
    val RoutineZTilt = DinghyIcon(IconRef.Ligature("linear_scale"), alternate = "routine_z_tilt")
    val RoutineQgl = DinghyIcon(IconRef.Ligature("linked_services"), alternate = "routine_qgl")
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.designsystem.icons.DinghyIconsTest --no-daemon" | tr -d '\r'`
Expected: PASS — all four `DinghyIconsTest` methods green (the new one, plus `iconRef_isUnique_acrossAllEntries` which still passes because all five new ligatures are unique in the registry).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt \
        app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt
git commit -m "feat(calibration): adopt owner bucket icons for the 5 routines"
```

---

## Task 2: Restructure the Calibration Hub Focus

The Focus header tracks the selected routine; the body becomes the description filling the available
space; the Open button is bottom-docked. The redundant body icon + title are removed (now in the header).

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt:121-144` (FocusFrame header) and `:193-232` (`HubRoutineFocus`)

No unit test — this is a Compose visual/layout change, verified on-device in Task 6.

- [ ] **Step 1: Make the FocusFrame header dynamic from `selected`**

In `CalibrationHubContent`, replace the `focus = { ... }` FocusFrame call (currently lines 121-144). Compute the header title/icon from `selected` with a launcher fallback, and keep the same content body call:

```kotlin
                focus = {
                    // Hub title/icon law (2026-06-13): the Focus header tracks the SELECTED routine,
                    // not a static "Calibration" label. Falls back to the launcher identity only on
                    // the (defensive) null-selection frame before D-05 pre-select resolves.
                    val headerTitle = stringResource(
                        selected?.let { routineTitleRes(it) } ?: R.string.cd_launcher_calibration,
                    )
                    val headerIcon = selected?.let { routineIconToken(it) }
                        ?: DinghyIcons.LauncherCalibration
                    FocusFrame(
                        title = headerTitle,
                        icon = headerIcon,
                        uDp = grid.uDp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        if (selected != null) {
                            HubRoutineFocus(
                                routine = selected,
                                onOpen = { onOpen(selected) },
                                t = t,
                            )
                        }
                    }
                },
```

Note: `routineTitleRes` and `routineIconToken` are already `internal` in this file; `grid` was dropped from the `HubRoutineFocus` call (it no longer renders the body icon — see Step 2).

- [ ] **Step 2: Rewrite `HubRoutineFocus` — description fills, Open bottom-docked, no body icon/title**

Replace the whole `HubRoutineFocus` composable (currently lines 193-232) with:

```kotlin
@Composable
private fun HubRoutineFocus(
    routine: CalibrationRoutine,
    onOpen: () -> Unit,
    t: ThemeTokens,
) {
    // fillMaxSize claims the FocusFrame's weight(1f) content area so the Spacer can bottom-dock the
    // Open button at a constant position across routines/orientations/screen sizes. The icon + title
    // that used to live here are gone — they're the FocusFrame header now (2026-06-13 cleanup).
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(routineDescRes(routine)),
            color = t.text2,
            fontFamily = Geist,
            fontSize = fsSp(15f, t.fs).sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        OutlinedControl(
            label = stringResource(R.string.calibration_open_routine),
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            intent = Intent.Go, // R5: Open = the expected action
        )
    }
}
```

- [ ] **Step 3: Fix imports**

In `CalibrationHubScreen.kt`:
- Add `import androidx.compose.foundation.layout.Spacer`.
- Remove now-unused imports: `androidx.compose.foundation.layout.Arrangement`, `androidx.compose.ui.Alignment`, and `works.mees.dinghy.designsystem.icons.DinghyIconView` (the body icon that used them is gone). Leave `UnitGrid` import only if still referenced; `HubRoutineFocus` no longer takes `grid`, so if `UnitGrid` has no other use in the file, remove `import works.mees.dinghy.designsystem.layout.UnitGrid` too.

Verify by grep after editing: `grep -n "Arrangement\|Alignment\|DinghyIconView\|UnitGrid" app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt` — each remaining hit must be a real use (e.g. `rememberUnitGrid` is a different symbol and stays).

- [ ] **Step 4: Compile**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL (no unused-import or unresolved-reference errors).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt
git commit -m "feat(calibration): dynamic Focus header, description fills, Open bottom-docked"
```

---

## Task 3: Center the Extrude Focus readouts

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt:652-667` (the `BasicTextField` inside `NumericSettingReadout`)

No unit test — Compose visual change, verified on-device in Task 6. The label and unit `Text`s are
already centered by the column's `CenterHorizontally`; the value field is the one that reads
left-aligned, so center its text explicitly and let it span the readout width.

- [ ] **Step 1: Add the centering import**

In `ExtrudeScreen.kt`, add: `import androidx.compose.ui.text.style.TextAlign`.

- [ ] **Step 2: Center the value field**

In `NumericSettingReadout`, modify the `BasicTextField` (currently lines 652-667): add `Modifier.fillMaxWidth()` and a centered `textAlign` in its `TextStyle`:

```kotlin
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                textStyle = TextStyle(
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(48f, t.fs).sp,
                    color = t.text,
                    textAlign = TextAlign.Center,
                ),
                singleLine = true,
            )
```

- [ ] **Step 3: Compile**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt
git commit -m "fix(extrude): center Distance/Speed Focus readout values"
```

---

## Task 4: Fix the Extrude footer button intents

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt:392-422` (the Main-mode `FootButtonBar`)

No unit test — intent is a visual color, verified on-device in Task 6.

- [ ] **Step 1: Back → Accent**

In the Main-mode `FootButtonBar` (lines 392-422), change the Back `OutlinedControl` intent from `Intent.Neutral` to `Intent.Accent`:

```kotlin
                            OutlinedControl(
                                label = "",
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
                                intent = Intent.Accent, // R5: Back/nav = accent
                                icon = DinghyIcons.Back,
                            )
```

- [ ] **Step 2: Load → Warn**

Change the Load `OutlinedControl` intent from `Intent.Accent` to `Intent.Warn` and update its comment (Unload already `Intent.Warn` — leave it):

```kotlin
                            OutlinedControl(
                                label = stringResource(R.string.extrude_load),
                                onClick = {
                                    if (vm.hasLoadMacro) onLoad()
                                    else infoText = noLoadMacro
                                },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Warn, // amber — heats + drives filament in (caution)
                                icon = DinghyIcons.ExpandCircleUp,
                            )
```

- [ ] **Step 3: Compile**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt
git commit -m "fix(extrude): footer intents — Back accent, Load+Unload caution"
```

---

## Task 5: Wire the Extrude Spool button to Spoolman with the reactive icon

The Spool button drops its placeholder text + "coming soon" toast: it shows the color-reactive
`SpoolGlyph` (no label) and navigates to `NavDest.Spool`.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt` (both `ExtrudeScreen` overloads, `ExtrudeContent`, `FieldButton`, the Spool button call site, imports)
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:548-555` (pass `onOpenSpool`)

No unit test — navigation + visual, verified on-device in Task 6.

- [ ] **Step 1: Add `onOpenSpool` to the live `ExtrudeScreen` overload**

In `ExtrudeScreen.kt`, the live overload (lines 144-215): add the param and thread it into `ExtrudeContent`:

```kotlin
@Composable
fun ExtrudeScreen(
    container: AppContainer,
    holder: ExtrudeHolder,
    activeSpoolDetail: SpoolmanSpool?,
    onBack: () -> Unit,
    onOpenSpool: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

In its `ExtrudeContent(...)` call (around line 212), add `onOpenSpool = onOpenSpool,` next to `onBack = onBack,`.

- [ ] **Step 2: Add `onOpenSpool` to the preview overload**

The stateless preview overload (lines 221-242): add `onOpenSpool: () -> Unit = {},` to the signature (after `onBack`) and pass `onOpenSpool = onOpenSpool,` into its `ExtrudeContent(...)` call.

- [ ] **Step 3: Add `onOpenSpool` to `ExtrudeContent`**

`ExtrudeContent` signature (lines 244-260): add `onOpenSpool: () -> Unit,` (after `onBack: () -> Unit,`).

- [ ] **Step 4: Give `FieldButton` an icon-only mode**

In `FieldButton` (lines 688-721), make the label optional and render icon-only when it's blank. Replace the signature default and the inner `Row`:

```kotlin
@Composable
private fun FieldButton(
    text: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = LocalTokens.current.outline,
    contentColor: Color = LocalTokens.current.text,
    textSizeSp: Float = 24f,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .clip(shape)
            .border(BorderStroke(2.dp, borderColor), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isBlank()) {
            // Icon-only field button (e.g. the Spool button — the reactive spool glyph IS the label).
            icon()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                icon()
                Text(
                    text = text,
                    color = contentColor,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(textSizeSp, t.fs).sp,
                )
            }
        }
    }
}
```

- [ ] **Step 5: Compute spool swatches + replace the Spool button call**

In `ExtrudeContent`, inside the `field = { ... }` block for `ExtrudeFieldMode.Main`, the Spool button is the second `FieldButton` (lines 374-387). First, just above the `Row` that holds the two buttons (around line 354, after `val tempColor = ...`), compute the reactive swatches from the already-injected `activeSpoolDetail`:

```kotlin
                            // Reactive spool swatches (D-07/D-08): same derivation as the home launcher
                            // tile — normalized Spoolman colors → empty list renders the honest empty spool.
                            val spoolSwatches = remember(activeSpoolDetail) {
                                activeSpoolDetail?.filament?.colorSwatches.orEmpty()
                                    .mapNotNull(::parseNormalizedHex)
                            }
```

Then replace the Spool `FieldButton` (lines 374-387) with:

```kotlin
                                FieldButton(
                                    text = "",
                                    onClick = onOpenSpool,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    icon = {
                                        SpoolGlyph(
                                            swatches = spoolSwatches,
                                            bodyTint = t.text2,
                                            keyline = t.hair,
                                            sizeDp = fsSp(40f, t.fs).dp,
                                            contentDescription = stringResource(R.string.cd_launcher_spool),
                                        )
                                    },
                                )
```

- [ ] **Step 6: Remove the dead stub copy**

The Spool button no longer uses the "coming soon" toast. In the `field` block, delete the line
`val spoolmanComingSoon = stringResource(R.string.extrude_spoolman_coming_soon)` (around line 318).
Leave the `R.string.extrude_spoolman_coming_soon` and `R.string.extrude_spool_placeholder` resource
definitions in `strings.xml` untouched (unused string resources don't break the build; removing them
is out of scope).

- [ ] **Step 7: Fix imports**

In `ExtrudeScreen.kt` add:
- `import works.mees.dinghy.designsystem.icons.SpoolGlyph`
- `import works.mees.dinghy.ui.spool.parseNormalizedHex` (it is `internal` in `SpoolScreen.kt`; same Gradle module, so importable across packages)

After editing, verify the old symbol is gone: `grep -n "MaterialSymbol\|inventory_2\|spoolmanComingSoon\|extrude_spool_placeholder" app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt`. The `MaterialSymbol` import (line 57) may now be unused — if grep shows no remaining `MaterialSymbol(` call, remove `import works.mees.dinghy.designsystem.MaterialSymbol`. (Note: `output_circle`/`input_circle` are passed as `symbol` strings to `BigCommand`, which calls `MaterialSymbol` internally — those keep the import alive. Confirm with grep before removing.)

- [ ] **Step 8: Wire `onOpenSpool` in AppShell**

In `AppShell.kt`, the `composable<NavDest.Extrude>` block (lines 548-555): add the nav callback:

```kotlin
            composable<NavDest.Extrude> {
                ExtrudeScreen(
                    container = container,
                    holder = extrudeHolder,
                    activeSpoolDetail = activeSpoolDetail,
                    onBack = { navController.popBackStack() },
                    onOpenSpool = { navController.navigate(NavDest.Spool) },
                )
            }
```

- [ ] **Step 9: Compile**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL (no unresolved `parseNormalizedHex`/`SpoolGlyph`, no unused-import error).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
git commit -m "feat(extrude): Spool button opens Spoolman with reactive spool icon"
```

---

## Task 6: Full build, unit suite, and on-device verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit suite**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL, all tests pass (including the new `routineGlyphs_matchOwnerBucketAssignments`).

- [ ] **Step 2: Force-rebuild the debug APK (avoid the stale-APK trap)**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL. Confirm the emitted APK mtime is newer than the last commit before installing.

- [ ] **Step 3: Install the matching ABI slice on BOTH devices**

Install `armeabi-v7a` to flox (`0a64b42e`) and `arm64-v8a` to moto (`ZY22LBDRM9`) via `adb -s <id> install -r <path>`.

- [ ] **Step 4: Owner on-device checks**

Extrude:
- Distance & Speed values read **centered** in both portrait and landscape.
- Footer: **Back = accent**, **Load + Unload = amber/caution**.
- The Spool button shows the **reactive spool icon only** (tinted to the loaded filament color when a spool is loaded; honest empty spool when none) and **tapping it opens the Spoolman page**.

Calibration Hub:
- Each routine shows its **new glyph** in the Field list and in the Focus header.
- Selecting a routine **updates the Focus header** title + icon.
- The **description fills** the Focus body and the **Open button stays bottom-docked** across selections and orientations.
- **Open still launches** the selected routine.

- [ ] **Step 5: Final commit (if any on-device tweaks were needed)**

Only if Step 4 surfaced adjustments. Otherwise the work is complete on the prior task commits.

---

## Self-Review

- **Spec coverage:** Extrude §1.1 centering → Task 3; §1.2 footer intents → Task 4; §1.3 Spool nav + reactive icon → Task 5. Calibration §2.1 icon swap → Task 1; §2.2 dynamic header / description fills / Open bottom-docked / remove body icon+title → Task 2. All spec items mapped.
- **Placeholder scan:** no TBD/TODO/"handle edge cases" — every code step shows exact code.
- **Type/name consistency:** `onOpenSpool: () -> Unit` is added consistently to both `ExtrudeScreen` overloads, `ExtrudeContent`, and the `AppShell` call site. `routineTitleRes`/`routineIconToken`/`routineDescRes` reused as already defined. `parseNormalizedHex`/`SpoolGlyph`/`colorSwatches` match their real signatures verified in source. `HubRoutineFocus` loses its `grid: UnitGrid` param in both its definition (Task 2 Step 2) and its single call site (Task 2 Step 1).
- **Build reality:** every build command uses the Windows-side helper; the stale-APK force-rebuild is explicit before UAT.
