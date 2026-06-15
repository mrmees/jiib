# Spool Detail Card Trim + Title Reorder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate the selected spool's identity into the Focus title (`vendor · material · name`) and trim the detail card to fill-bar (tap-to-measure) → temps → date → badges.

**Architecture:** All app changes are in `ui/spool/SpoolScreen.kt`: a new `spoolFocusTitle` helper, the fill bar becomes the measure-weight tap target, and the vendor/color + weight rows are removed with temps reordered above date. Three now-unused content-description strings are removed from `strings.xml`.

**Tech Stack:** Kotlin, Jetpack Compose, the project's `FillMeter`/`DinghyIconView` design-system components.

**Spec:** `docs/superpowers/specs/2026-06-13-spool-detail-trim-design.md`

**Testing note:** This is a Compose layout/title change with no meaningful unit test; the pure-logic tests are untouched. Verification is a clean `:app:compileDebugKotlin`, the string-removal compile check, and the on-device eyeball in Task 3. Build runs Windows-side via `E:\Android\gw.bat` (`./gradlew` does NOT work from WSL); exit code is authoritative; pipe through `tr -d '\r'`.

---

### Task 1: SpoolScreen detail-card trim + `vendor · material · name` title

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt`

- [ ] **Step 1: Add the `semantics` imports**

In the import block, add these two lines (the file does NOT currently import `semantics`/`contentDescription`; `clickable`, `clip`, `RoundedCornerShape` are already imported):

```kotlin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
```

- [ ] **Step 2: Add the `spoolFocusTitle` helper**

Immediately AFTER the existing `spoolDisplayTitle` helper (currently ends at line ~841, the `.ifBlank { stringResource(R.string.spool_unnamed, spool.id) }` line), add:

```kotlin

/**
 * The Focus-header identity for a selected spool: `vendor · material · name`
 * (MFG · Chemistry · Color), e.g. `Prusament · PLA · Galaxy Black`. Missing parts are skipped;
 * degrades to `Spool <id>`. Distinct from [spoolDisplayTitle] (the list-row / measure-card
 * `material · name`) because the list rows carry vendor in their meta line instead.
 */
@Composable
private fun spoolFocusTitle(spool: SpoolmanSpool): String =
    listOfNotNull(spool.filament?.vendor?.name, spool.filament?.material, spool.filament?.name)
        .joinToString(" · ")
        .ifBlank { stringResource(R.string.spool_unnamed, spool.id) }
```

- [ ] **Step 3: Point `focusTitle` at the new helper**

In `SpoolContent`, the current lines (282–285) read:

```kotlin
        val spoolColor = selected?.filament?.colorSwatches?.firstNotNullOfOrNull { parseNormalizedHex(it) }
        // Focus header title = the selected spool's identity (mirrors the list row); generic when none.
        val focusTitle = selected?.let { spoolDisplayTitle(it) }
            ?: stringResource(R.string.cd_launcher_spool)
```

Change the comment + helper call (leave the `spoolColor` line and the fallback line):

```kotlin
        val spoolColor = selected?.filament?.colorSwatches?.firstNotNullOfOrNull { parseNormalizedHex(it) }
        // Focus header title = vendor · material · name (MFG · Chemistry · Color); generic when none.
        val focusTitle = selected?.let { spoolFocusTitle(it) }
            ?: stringResource(R.string.cd_launcher_spool)
```

- [ ] **Step 4: Make the FillMeter the tap target + drop the vendor/color row and weight row**

In `SpoolDetailContent` (the `spool != null` `Column`), the current block from the FillMeter through the weight row (lines ~884–919) reads:

```kotlin
        val fillLabel = buildFillLabel(spool)
        FillMeter(
            fraction = fillFraction,
            fillColor = spoolColor ?: t.accent,
            modifier = Modifier.fillMaxWidth(),
            label = fillLabel,
        )
        // Vendor + color name.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DinghyIconView(DinghyIcons.Storefront, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_vendor))
            DetailValue(filament?.vendor?.name, bodySp, Modifier.weight(1f), t)
            DinghyIconView(DinghyIcons.Palette, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_color))
            DetailValue(filament?.name, bodySp, Modifier.weight(1f), t)
        }
        // Weight row (tappable to correct measured weight via D-04).
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(t.rCtrl)).clickable(onClick = onMeasure)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DinghyIconView(DinghyIcons.Scale, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_weight))
            Text(
                text = spoolWeightText(spool),
                color = if (spool.remainingWeight == null) t.text3 else t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = bodySp.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            DinghyIconView(DinghyIcons.Edit, tint = t.text3, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_weight_edit))
        }
```

Replace that ENTIRE block (the FillMeter call + the "Vendor + color name" Row + the "Weight row" Row) with just the tappable FillMeter:

```kotlin
        val fillLabel = buildFillLabel(spool)
        // The fill bar is the spool's weight visual (label shows remaining/original g · %) AND the
        // tap target to correct the measured weight (the old standalone weight row was removed).
        val editWeightCd = stringResource(R.string.cd_spool_weight_edit)
        FillMeter(
            fraction = fillFraction,
            fillColor = spoolColor ?: t.accent,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(t.rCtrl))
                .clickable(onClick = onMeasure)
                .semantics { contentDescription = editWeightCd },
            label = fillLabel,
        )
```

- [ ] **Step 5: Move the temps row above the date row**

Immediately after the FillMeter block from Step 4, the code currently has the **Registration date** Row (lines ~920–934) followed by the **Nozzle + bed temps** Row (lines ~935–944):

```kotlin
        // Registration date.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DinghyIconView(DinghyIcons.CalendarAddOn, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_registered))
            Text(
                text = spool.registered?.substringBefore('T')?.ifBlank { null }
                    ?: stringResource(R.string.spool_value_unset),
                color = t.text,
                fontFamily = GeistMono,
                fontSize = bodySp.sp,
                maxLines = 1,
            )
        }
        // Nozzle + bed recommended temps.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DinghyIconView(DinghyIcons.Nozzle, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_nozzle_temp))
            Text(tempText(filament?.settingsExtruderTemp), color = t.text, fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = bodySp.sp, maxLines = 1)
            DinghyIconView(DinghyIcons.HeatBed, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_bed_temp))
            Text(tempText(filament?.settingsBedTemp), color = t.text, fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = bodySp.sp, maxLines = 1)
        }
```

Swap the two so the temps Row comes first, then the date Row (verbatim content, just reordered):

```kotlin
        // Nozzle + bed recommended temps.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DinghyIconView(DinghyIcons.Nozzle, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_nozzle_temp))
            Text(tempText(filament?.settingsExtruderTemp), color = t.text, fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = bodySp.sp, maxLines = 1)
            DinghyIconView(DinghyIcons.HeatBed, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_bed_temp))
            Text(tempText(filament?.settingsBedTemp), color = t.text, fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = bodySp.sp, maxLines = 1)
        }
        // Registration date.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DinghyIconView(DinghyIcons.CalendarAddOn, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_registered))
            Text(
                text = spool.registered?.substringBefore('T')?.ifBlank { null }
                    ?: stringResource(R.string.spool_value_unset),
                color = t.text,
                fontFamily = GeistMono,
                fontSize = bodySp.sp,
                maxLines = 1,
            )
        }
```

(The `if (isActive)` / `if (spool.archived)` badge blocks after this stay where they are.)

- [ ] **Step 6: Update the `SpoolDetailContent` KDoc**

The KDoc above `SpoolDetailContent` (currently lines ~843–847) still says `title = material · name`. Update that line to reflect the new title format and trimmed card:

```kotlin
/**
 * The Detail card content for the selected spool (inside [FocusFrame]). The spool's identity lives in
 * the Focus header (title = vendor · material · name, icon = spool-colored ev_shadow); the empty state
 * shows a neutral ev_shadow ([DinghyIcons.SpoolFilament]). The card body is the tappable FillMeter
 * (also the measure-weight entry) plus the recommended temps and registration date.
 */
```

- [ ] **Step 7: Remove the now-orphaned `spoolWeightText` and `DetailValue` helpers**

Confirm both are unreferenced after Steps 4–5:

```bash
grep -n "spoolWeightText\|DetailValue" app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
```
Expected: only their own definitions remain. Then delete:
- the `DetailValue` composable + its `/** A label-less detail value … */` KDoc (currently ~lines 1067–1080), and
- the `spoolWeightText` composable + its `/** Line-3 weight text: … */` KDoc (currently ~lines 1082–1092).

Do NOT touch `tempText`, `buildFillLabel`, `spoolDisplayTitle`, `DetailBadge`, `parseNormalizedHex`, `SpoolRowSwatch`, `SpoolRowBody`, or any other helper.

Note: `DinghyIcons.Edit` is member access on the already-imported `DinghyIcons` object — there is NO separate `Edit` import to remove. `GeistMono`, `FontWeight`, `TextOverflow`, `spool_value_unset` all remain used by the surviving rows/helpers — do not remove them.

- [ ] **Step 8: Compile**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r' | tail -n 30
```
Expected: `BUILD SUCCESSFUL`. If it fails on an unused/unresolved `spoolWeightText`, `DetailValue`, or a missing `semantics` import, recheck Steps 1, 7.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
git commit -m "feat(spool): vendor·material·name title; trim detail to fill-bar/temps/date

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Remove the three now-unused content-description strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Confirm no remaining references**

```bash
grep -rn "cd_spool_vendor\|cd_spool_color\b\|cd_spool_weight\b" app/src
```
Expected: ONLY the three definition lines in `strings.xml` (the `SpoolScreen.kt` usages were removed in Task 1). Note `cd_spool_weight_edit` MUST still appear (it's the fill-bar a11y label) — do not remove it. If any non-strings.xml reference appears for the three target strings, STOP and report.

- [ ] **Step 2: Delete the three lines**

Remove exactly these from `app/src/main/res/values/strings.xml`:

```xml
    <string name="cd_spool_vendor">Vendor</string>
    <string name="cd_spool_color">Color</string>
    <string name="cd_spool_weight">Remaining weight — tap to correct the measured weight</string>
```

Keep `cd_spool_weight_edit` (the line right after them).

- [ ] **Step 3: Compile to confirm no dangling `R.string` reference**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r' | tail -n 20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "chore(spool): remove unused cd_spool_vendor/color/weight strings

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Build, install on flox + moto, on-device UAT

**Files:** none (build + manual verification)

- [ ] **Step 1: Full debug unit suite (sanity)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r' | tail -n 15
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Clean-rebuild the split-ABI debug APK (stale-APK gate)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks --no-daemon" 2>&1 | tr -d '\r' | tail -n 15
ls -l --time-style=+%s app/build/outputs/apk/debug/*.apk && git log -1 --format=%ct
```
Expected: `BUILD SUCCESSFUL`; each APK mtime ≥ the last commit timestamp.

- [ ] **Step 3: Install matching ABI on both devices**

```bash
cd app/build/outputs/apk/debug
/mnt/e/Android/Sdk/platform-tools/adb.exe -s 0a64b42e install -r app-armeabi-v7a-debug.apk 2>&1 | tr -d '\r'
/mnt/e/Android/Sdk/platform-tools/adb.exe -s ZY22LBDRM9 install -r app-arm64-v8a-debug.apk 2>&1 | tr -d '\r'
```
Expected: `Success` on both. (flox = armeabi-v7a `0a64b42e`; moto = arm64-v8a `ZY22LBDRM9`.)

- [ ] **Step 4: Owner on-device eyeball**

Select spools and confirm:
- Focus title reads `vendor · material · name` (e.g. `Prusament · PLA · Galaxy Black`); skips missing parts; "Spool" when none selected.
- Detail card shows ONLY: fill bar → temps row → date row → (loaded/archived badges). No vendor, color, or standalone weight row.
- Tapping the **fill bar** opens the measure-weight entry; the fill bar label still shows `…/… g · %`.
- List rows unchanged (trailing remaining-weight + vendor/location meta still present).

---

## Self-Review

- **Spec coverage:** ✅ title = `vendor · material · name` middot via new `spoolFocusTitle` (Task 1 Steps 2–3) · fill bar tappable for measure-weight w/ a11y label (Step 4) · vendor/color row dropped (Step 4) · weight row dropped (Step 4) · temps before date (Step 5) · dead `spoolWeightText`/`DetailValue` removed (Step 7) · 3 strings removed (Task 2) · `cd_spool_weight_edit` kept · list rows/measure card untouched · KDoc updated (Step 6) · build/devices/UAT (Tasks 1 Step 8, 2 Step 3, 3).
- **Placeholder scan:** none — all edits show full before/after.
- **Type consistency:** `spoolFocusTitle(spool: SpoolmanSpool): String` defined once (Task 1 Step 2), called in Step 3. `editWeightCd` is a local `String` from `stringResource`, referenced in the same `semantics` block. `spoolDisplayTitle` is retained and untouched (still used by list row + measure card). No reference to any removed symbol survives (verified by the Step 7 grep + the Step 8 compile).
```
