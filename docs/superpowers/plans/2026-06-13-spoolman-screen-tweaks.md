# Spoolman Screen Tweaks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the Spool screen, show the selected spool's `material · name` as the Focus title and replace the custom spool glyph with a spool-colored `ev_shadow` icon, dropping the now-redundant color chip + material row.

**Architecture:** Three small, layered changes: (1) add an optional `iconTint` to the shared `FocusFrame` so a screen can color the header identity glyph with item data (THEME-01 carve-out, parallel to `FocusEdge.Data`); (2) register a new `SpoolFilament` icon token (`ev_shadow` ligature); (3) wire both into `SpoolScreen` plus a small DRY refactor of the spool-title string.

**Tech Stack:** Kotlin, Jetpack Compose, the `DinghyIcons` semantic icon registry (Material Symbols ligatures), `FocusFrame` design-system component.

**Spec:** `docs/superpowers/specs/2026-06-13-spoolman-screen-tweaks-design.md`

**Testing note:** Task 2 (icon registry) is covered by real unit tests (`DinghyIconsTest`) + the device-free `verify_ligatures.py` gate. Tasks 1 and 3 are Compose UI with no meaningful unit test — verification is a clean compile, then the on-device eyeball in Task 4. The build runs Windows-side via `E:\Android\gw.bat` (`./gradlew` does NOT work from WSL); the process exit code is authoritative; pipe through `tr -d '\r'`.

---

### Task 1: Add `iconTint` to `FocusFrame` / `FocusHeader`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt`

Adds an optional override for the inert identity-glyph tint. Default `null` preserves today's `t.text2` for every existing caller (backward-compatible). The e-stop morph path is untouched. `Color` is already imported in this file.

- [ ] **Step 1: Add the `iconTint` parameter to `FocusFrame`**

In the `FocusFrame(...)` parameter list, add `iconTint: Color? = null` immediately after the `icon` parameter. The result should read:

```kotlin
fun FocusFrame(
    title: String,
    icon: DinghyIcon,
    iconTint: Color? = null,
    uDp: Dp,
    modifier: Modifier = Modifier,
    edge: FocusEdge = FocusEdge.Neutral,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    onPanic: (() -> Unit)? = null,
    contentInset: Dp = FocusInset,
    trailingActionIcon: DinghyIcon? = null,
    onTrailingAction: (() -> Unit)? = null,
    trailingActionContentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
```

- [ ] **Step 2: Pass `iconTint` down to `FocusHeader`**

In `FocusFrame`'s body, the `FocusHeader(...)` call currently passes `title, icon, uDp, isPrinting, onEmergencyStop, onPanic, trailingActionIcon, onTrailingAction, trailingActionContentDescription`. Add `iconTint = iconTint` to that call (e.g. right after `icon = icon,`):

```kotlin
        FocusHeader(
            title = title,
            icon = icon,
            iconTint = iconTint,
            uDp = uDp,
            isPrinting = isPrinting,
            onEmergencyStop = onEmergencyStop,
            onPanic = onPanic,
            trailingActionIcon = trailingActionIcon,
            onTrailingAction = onTrailingAction,
            trailingActionContentDescription = trailingActionContentDescription,
        )
```

- [ ] **Step 3: Add `iconTint` to the private `FocusHeader` signature**

In `private fun FocusHeader(...)`, add `iconTint: Color? = null` after the `icon: DinghyIcon` parameter:

```kotlin
@Composable
private fun FocusHeader(
    title: String,
    icon: DinghyIcon,
    iconTint: Color? = null,
    uDp: Dp,
    isPrinting: Boolean,
    onEmergencyStop: (() -> Unit)?,
    onPanic: (() -> Unit)?,
    trailingActionIcon: DinghyIcon? = null,
    onTrailingAction: (() -> Unit)? = null,
    trailingActionContentDescription: String? = null,
) {
```

- [ ] **Step 4: Apply the tint override to the identity glyph**

In `FocusHeader`, the inert identity-glyph branch (the `else` of `headerShowsEStop(...)`) renders:

```kotlin
                Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
                    DinghyIconView(
                        icon = icon,
                        tint = t.text2,
                        sizeDp = slot * IDENTITY_ICON_RATIO,
                    )
                }
```

Change `tint = t.text2` to `tint = iconTint ?: t.text2`:

```kotlin
                Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
                    DinghyIconView(
                        icon = icon,
                        tint = iconTint ?: t.text2,
                        sizeDp = slot * IDENTITY_ICON_RATIO,
                    )
                }
```

- [ ] **Step 5: Compile**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r' | tail -n 30
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt
git commit -m "feat(focusframe): optional iconTint for data-colored identity glyph

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Register the `SpoolFilament` (`ev_shadow`) icon

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`
- Test (existing, run — do not edit): `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt`

`ev_shadow` is already verified resolvable in the bundled font. The new token is a Ligature with a unique `alternate` and unique `primary`, so all `DinghyIconsTest` assertions stay green once it's added to `all`.

- [ ] **Step 1: Add the `SpoolFilament` token**

In `DinghyIcons`, add this val. Place it just after the `LauncherSpool` declaration (the custom spool drawable, currently ~line 89) so the spool glyphs sit together:

```kotlin
    // Spool-screen identity glyph (owner-chosen 2026-06-13, icon law [[dinghy-never-pick-icons-ask]]).
    // SpoolScreen's detail Focus header + empty state render this Material Symbol tinted to the spool's
    // filament color (THEME-01 data carve-out), replacing the custom side-view spool drawable on THAT
    // screen only (LauncherSpool stays for PrintStatus / idle list). `ev_shadow` is verified resolvable
    // in the bundled v2.944 Material Symbols ttf (tools/verify_ligatures.py).
    val SpoolFilament = DinghyIcon(IconRef.Ligature("ev_shadow"), alternate = "spool_filament")
```

- [ ] **Step 2: Add `SpoolFilament` to the `all` list**

In the `all` list, add `SpoolFilament` to the spool-cluster line. The line currently reads:

```kotlin
        SpoolChange, SpoolClear, SpoolLocation, SpoolUsageStale, SpoolChangedExternally,
```

Change it to:

```kotlin
        SpoolChange, SpoolClear, SpoolLocation, SpoolUsageStale, SpoolChangedExternally, SpoolFilament,
```

- [ ] **Step 3: Run the icon registry unit tests**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests *DinghyIconsTest* --no-daemon" 2>&1 | tr -d '\r' | tail -n 25
```
Expected: `BUILD SUCCESSFUL` — all `DinghyIconsTest` assertions pass (non-blank/unique `alternate`, unique `primary`, drawable-keepers unchanged).

- [ ] **Step 4: Run the ligature-resolution gate**

```bash
python tools/verify_ligatures.py 2>&1 | tr -d '\r' | tail -n 3
```
Expected: a line ending `missing: []` and exit 0 (the registry's `ev_shadow` resolves).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
git commit -m "feat(icons): register SpoolFilament (ev_shadow) for the Spool screen

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Wire the tweaks into `SpoolScreen`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt`

Adds a shared `spoolDisplayTitle` helper, uses it for the Focus title (+ DRYs two existing call sites), swaps the Focus header icon to `SpoolFilament` tinted by spool color, switches the empty state to `ev_shadow`, drops the redundant detail top row, and removes the now-unused `DetailSwatch` composable and `SpoolGlyph` import.

- [ ] **Step 1: Add the `spoolDisplayTitle` helper**

Add this file-private helper. Place it immediately before `private fun SpoolDetailContent(` (currently ~line 836):

```kotlin
/**
 * The spool's display identity: `material · name` (e.g. `PLA · Galaxy Black`), degrading to
 * `Spool <id>` when both are blank. One definition shared by the list row, the Focus header title,
 * and the measure-weight info card (was duplicated inline at each).
 */
@Composable
private fun spoolDisplayTitle(spool: SpoolmanSpool): String =
    listOfNotNull(spool.filament?.material, spool.filament?.name).joinToString(" · ")
        .ifBlank { stringResource(R.string.spool_unnamed, spool.id) }
```

- [ ] **Step 2: Compute the Focus title in `SpoolContent`**

In `SpoolContent`, just after the `spoolColor` line (currently ~line 283: `val spoolColor = selected?.filament?.colorSwatches?.firstNotNullOfOrNull { parseNormalizedHex(it) }`), add:

```kotlin
        // Focus header title = the selected spool's identity (mirrors the list row); generic when none.
        val focusTitle = selected?.let { spoolDisplayTitle(it) }
            ?: stringResource(R.string.cd_launcher_spool)
```

- [ ] **Step 3: Use the new title + icon + tint in the `FocusFrame` call**

In `SpoolContent`'s focus lambda, the `FocusFrame(...)` call currently begins:

```kotlin
                    FocusFrame(
                        title = stringResource(R.string.cd_launcher_spool),
                        icon = DinghyIcons.LauncherSpool,
                        uDp = grid.uDp,
                        edge = spoolColor?.let { FocusEdge.Data(it) } ?: FocusEdge.Neutral,
                        modifier = Modifier.fillMaxSize(),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
```

Change the `title`, `icon`, and add `iconTint`:

```kotlin
                    FocusFrame(
                        title = focusTitle,
                        icon = DinghyIcons.SpoolFilament,
                        iconTint = spoolColor,
                        uDp = grid.uDp,
                        edge = spoolColor?.let { FocusEdge.Data(it) } ?: FocusEdge.Neutral,
                        modifier = Modifier.fillMaxSize(),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
```

- [ ] **Step 4: Switch the empty state to `ev_shadow`**

In `SpoolDetailContent`, the `spool == null` branch currently renders:

```kotlin
    if (spool == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SpoolGlyph(
                swatches = emptyList(),
                bodyTint = t.text3,
                keyline = t.hair,
                sizeDp = fsSp(64f, t.fs).dp,
                contentDescription = stringResource(R.string.cd_spool_empty),
            )
        }
        return
    }
```

Replace the `SpoolGlyph(...)` call with a neutral `ev_shadow`:

```kotlin
    if (spool == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DinghyIconView(
                DinghyIcons.SpoolFilament,
                tint = t.text3,
                sizeDp = fsSp(64f, t.fs).dp,
                contentDescription = stringResource(R.string.cd_spool_empty),
            )
        }
        return
    }
```

- [ ] **Step 5: Drop the redundant detail top row (chip + material name) and the unused `headerSp`**

In `SpoolDetailContent` (the `spool != null` path), there is a `val headerSp = fsSp(26f, t.fs)` (currently ~line 857) and, inside the `Column`, this comment + `Row`:

```kotlin
        // (The large top spool glyph was removed 2026-06-12 — redundant with the FillMeter's
        // fullness readout + the header color swatch. The FillMeter IS the spool's visual now.)
        // Header: color swatch + material name.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailSwatch(filament?.colorSwatches ?: emptyList(), headerSp, t)
            Text(
                text = filament?.material?.ifBlank { null }
                    ?: stringResource(R.string.spool_unnamed, spool.id),
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = headerSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
```

Delete the entire `Row { … }` block AND its `// Header: color swatch + material name.` comment line. Keep the first comment paragraph but update it (the FillMeter rationale is still true), e.g.:

```kotlin
        // The spool's identity now lives in the Focus header (title = material · name, icon tinted to
        // the spool color); the FillMeter below is the in-card color/fullness visual. No chip row here.
```

Then delete the now-unused `val headerSp = fsSp(26f, t.fs)` declaration (it was referenced only by the removed row). Leave `val bodySp` and `val iconSp` — they are used by the rows below.

- [ ] **Step 6: Update the two other call sites to use the helper (DRY)**

(a) In `SpoolRowBody` (currently ~line 1008), the title `Text` reads:

```kotlin
        Text(
            text = listOfNotNull(filament?.material, filament?.name).joinToString(" · ")
                .ifBlank { stringResource(R.string.spool_unnamed, spool.id) },
            color = t.text,
```

Change the `text` to:

```kotlin
        Text(
            text = spoolDisplayTitle(spool),
            color = t.text,
```

(b) In `SpoolMeasureWeightField`'s info card (currently ~line 758), the name `Text` reads:

```kotlin
            Text(
                text = listOfNotNull(filament?.material, filament?.name).joinToString(" · ")
                    .ifBlank { stringResource(R.string.spool_unnamed, spool.id) },
                color = t.text,
```

Change the `text` to:

```kotlin
            Text(
                text = spoolDisplayTitle(spool),
                color = t.text,
```

(Leave the surrounding `val filament = …` declarations in both functions — they are still used for vendor/tare/etc.)

- [ ] **Step 7: Remove the now-unused `DetailSwatch` composable**

After Step 5, `DetailSwatch` has no callers. Confirm, then delete it:

```bash
grep -n "DetailSwatch" app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
```
Expected: only the definition (the `@Composable private fun DetailSwatch(...)` block, currently ~lines 1076–1092). Delete that whole function and its `/** The detail split swatch … */` KDoc. (Do NOT touch `SpoolRowSwatch` — that is the list-row swatch and is still used.)

- [ ] **Step 8: Remove the now-unused `SpoolGlyph` import**

After Step 4, `SpoolGlyph` is no longer referenced in this file. Confirm and remove the import:

```bash
grep -n "SpoolGlyph" app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
```
Expected: only the import line `import works.mees.dinghy.designsystem.icons.SpoolGlyph` (currently line 69). Delete that import line. (The `SpoolGlyph` composable itself stays in the codebase — other screens use it.)

- [ ] **Step 9: Compile**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r' | tail -n 30
```
Expected: `BUILD SUCCESSFUL`. If it fails on an unused/unresolved `SpoolGlyph`, `DetailSwatch`, or `headerSp`, recheck Steps 4–8.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
git commit -m "feat(spool): spool-identity Focus title + ev_shadow colored icon; drop chip row

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Build, install on flox + moto, on-device UAT

**Files:** none (build + manual verification)

- [ ] **Step 1: Run the full debug unit test suite (sanity)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r' | tail -n 20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Clean-rebuild the split-ABI debug APK (stale-APK gate)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks --no-daemon" 2>&1 | tr -d '\r' | tail -n 20
```
Expected: `BUILD SUCCESSFUL`. Then confirm freshness:

```bash
ls -l --time-style=+%s app/build/outputs/apk/debug/*.apk && git log -1 --format=%ct
```
Each APK mtime must be ≥ the last commit timestamp.

- [ ] **Step 3: Install the matching ABI slice on both devices**

flox = armeabi-v7a (id `0a64b42e`), moto = arm64-v8a (id `ZY22LBDRM9`):

```bash
cd app/build/outputs/apk/debug
/mnt/e/Android/Sdk/platform-tools/adb.exe -s 0a64b42e install -r app-armeabi-v7a-debug.apk 2>&1 | tr -d '\r'
/mnt/e/Android/Sdk/platform-tools/adb.exe -s ZY22LBDRM9 install -r app-arm64-v8a-debug.apk 2>&1 | tr -d '\r'
```
Expected: `Success` on both.

- [ ] **Step 4: Owner on-device eyeball (Matthew navigates)**

On flox and moto, open the Spool screen and select spools of a few different colors. Confirm:
- The Focus header **title** reads the spool's `material · name` (e.g. `PLA · Galaxy Black`); reads "Spool" when nothing is selected.
- The Focus header **icon** is `ev_shadow`, **tinted to the spool color**.
- The detail area no longer shows the color **chip** or the bare material label; it still shows the FillMeter, vendor/color row, weight row, registration date, and temps.
- The **empty state** (no spool / no selection) shows a neutral `ev_shadow` glyph.
- A **black / very dark** spool: check the header icon is still discernible (the known contrast caveat). Flag if it disappears.
- While a print is running: the header icon still morphs to the red **e-stop** and the confirm guard fires.

---

## Self-Review

- **Spec coverage:** ✅ Focus title = `material · name` mirror + fallback (Task 3 Steps 1–3) · DRY helper across 3 call sites (Task 3 Steps 1, 6) · new `ev_shadow` token registered + tested (Task 2) · `FocusFrame.iconTint` carve-out (Task 1) · header icon swapped + spool-color tinted (Task 3 Step 3) · empty state → `ev_shadow` (Task 3 Step 4) · drop whole detail top row + unused `headerSp` (Task 3 Step 5) · remove unused `DetailSwatch` (Step 7) + `SpoolGlyph` import (Step 8) · custom glyph kept for other screens (registry untouched re: LauncherSpool) · tests/gate/devices/contrast caveat (Tasks 2 & 4).
- **Placeholders:** none — all edits show full before/after code.
- **Type consistency:** `iconTint: Color?` is the same name/type in `FocusFrame` (Task 1 Step 1), the `FocusHeader` call (Step 2), the `FocusHeader` signature (Step 3), and the Spool call site (`iconTint = spoolColor`, a `Color?`). `spoolDisplayTitle(spool: SpoolmanSpool): String` is defined once (Task 3 Step 1) and called identically in Steps 2, 6a, 6b. `DinghyIcons.SpoolFilament` is the same token defined in Task 2 and consumed in Task 3.
```
