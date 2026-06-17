# FootButtonBar Count-Driven Icon/Label Conformance — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `FootButtonBar` the single authority for whether its buttons render icon-only (≥3 buttons) or icon+text (≤2 buttons), enforced structurally so the app can't drift.

**Architecture:** Convert `FootButtonBar` from a `content: @Composable RowScope.() -> Unit` lambda to a typed `List<FootAction>`. The bar counts `actions.size` at render time and applies the threshold centrally. Add the new overload first (build stays green), migrate all 30 call sites + 1 preview, then delete the lambda overload so the rule is compile-enforced. Every foot button gains an owner-locked icon.

**Tech Stack:** Kotlin, Jetpack Compose, Material Symbols (bundled font), JUnit + Compose UI test, the repo's `tools/verify_ligatures.py` ligature gate and `DinghyIconsTest` uniqueness guard.

**Spec:** `docs/superpowers/specs/2026-06-17-footbar-button-conformance-design.md` (Codex-reviewed).

**Plan review:** Codex SOUND-WITH-FIXES (2026-06-17) — all findings folded in: F1 (footAction handles icon-only ControlSpecs via contentDescriptionRes), F2 (new ligatures added to `verify_ligatures.py` NEEDED), F3 (render tests → androidTest, guard tests → src/test), F4 (Console bar is Back+3 toggles), F5 (call-site shapes corrected: AppSettings 1-btn, Printers/Move static-4, Spool always-3), F6 (KDoc cleanup on overload deletion).

**Build/test commands (this repo builds Windows-side from WSL):**
- Assemble: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
- Unit test: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests '<FQCN>' --no-daemon" | tr -d '\r'`
- Ligature gate: `python3 tools/verify_ligatures.py` (run from repo root)
- The process **exit code is authoritative**; Gradle CR progress bars are stripped with `tr -d '\r'`.

---

## ⚠ One open owner gate before full completion

`temp_presets` needs a fresh owner-chosen ligature (Codex F4 — `thermostat` collides with the Temperature Focus header). **Task 8 is BLOCKED until Matthew supplies it.** Every other task can proceed. Do NOT pick a glyph (icon law `[[dinghy-never-pick-icons-ask]]`).

---

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `app/.../designsystem/components/FootButtonBar.kt` | The bar; owns the count rule | Add `FootAction` + list overload; later delete lambda overload |
| `app/.../designsystem/icons/DinghyIcons.kt` | Icon registry | Add 7 new tokens (+1 owner-pending); extend `all` |
| `app/.../control/ControlSpecs.kt` | Named control specs | `calibrationHomeAll.icon = MoveHomeAll` (+ contentDescriptionRes) |
| `app/.../test/.../FootButtonBarTest.kt` (new) | Boundary + guard tests | Create |
| `tools/verify_ligatures.py` consumers / `DinghyIconsTest.kt` | Ligature/uniqueness gates | Allow-list `mode_heat_off`; new tokens covered |
| 23 screen files + `DesignKitComponentPreviews.kt` | Foot-bar call sites | Migrate lambda → `actions = …` |

---

## Task 1: `FootAction` model + new `FootButtonBar(actions:)` overload (additive)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/FootButtonBar.kt`
- Test (plain JUnit, src/test): `app/src/test/java/works/mees/dinghy/designsystem/components/FootActionTest.kt` (create)
- Test (Compose UI, androidTest): `app/src/androidTest/java/works/mees/dinghy/designsystem/components/FootButtonBarRenderTest.kt` (create)

> Codex F3: Compose UI test deps in this project are `androidTestImplementation` only (`app/build.gradle.kts`), and `src/test` has just JUnit/coroutines/json. So the **render** tests go under `androidTest` (AndroidJUnit4 + `DinghyTheme(ThemeResolver())`, the pattern in `WebcamLifecycleTest.kt`), while the `require()` **guard** tests are plain JUnit under `src/test` (no Compose needed).

- [ ] **Step 1a: Write the guard test (plain JUnit, `src/test`)**

```kotlin
package works.mees.dinghy.designsystem.components

import org.junit.Assert.assertThrows
import org.junit.Test
import works.mees.dinghy.designsystem.icons.DinghyIcons

class FootActionTest {
    @Test fun blankLabel_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            FootAction(label = "", icon = DinghyIcons.Back, onClick = {})
        }
    }
    @Test fun drawableIcon_throws() {
        // LauncherSpool is drawable-backed (IconRef.Drawable) — not ligature-backed.
        assertThrows(IllegalArgumentException::class.java) {
            FootAction(label = "x", icon = DinghyIcons.LauncherSpool, onClick = {})
        }
    }
}
```

- [ ] **Step 1b: Write the render test (Compose UI, `androidTest`)**

Copy the theme/runner setup from `app/src/androidTest/.../WebcamLifecycleTest.kt` (uses `@RunWith(AndroidJUnit4::class)` and `DinghyTheme(ThemeResolver())`). Then:

```kotlin
package works.mees.dinghy.designsystem.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.compose.DinghyTheme  // confirm package from WebcamLifecycleTest imports

@RunWith(AndroidJUnit4::class)
class FootButtonBarRenderTest {
    @get:Rule val rule = createComposeRule()
    private fun action(label: String) = FootAction(label = label, icon = DinghyIcons.Back, onClick = {})

    @Test fun twoActions_renderLabels() {
        rule.setContent { DinghyTheme(ThemeResolver()) {
            FootButtonBar(uDp = 48.dp, actions = listOf(action("Alpha"), action("Beta")))
        } }
        rule.onNodeWithText("Alpha").assertIsDisplayed()
        rule.onNodeWithText("Beta").assertIsDisplayed()
    }

    @Test fun threeActions_renderNoLabels() {
        rule.setContent { DinghyTheme(ThemeResolver()) {
            FootButtonBar(uDp = 48.dp, actions = listOf(action("Alpha"), action("Beta"), action("Gamma")))
        } }
        rule.onAllNodesWithText("Alpha").assertCountEquals(0) // ≥3 → icon-only
    }
}
```

> Confirm the exact `DinghyTheme`/`ThemeResolver` import paths and constructor from `WebcamLifecycleTest.kt` before relying on them.

- [ ] **Step 2: Run the tests, verify they fail to compile / fail**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests '*FootActionTest' --no-daemon" | tr -d '\r'`
Expected: FAILS — `FootAction` doesn't exist yet. (The render test runs at Step 4 via `connectedDebugAndroidTest`.)

- [ ] **Step 3: Add `FootAction` + the list overload (keep the lambda overload)**

In `FootButtonBar.kt`, add imports and the model + overload. Keep the existing lambda `FootButtonBar` untouched.

```kotlin
import androidx.compose.ui.graphics.Color
import works.mees.dinghy.designsystem.control.ControlSpec
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.IconRef
import androidx.compose.ui.res.stringResource

/** ≥3 buttons → icon-only; ≤2 → icon+label (spec 2026-06-17). */
const val FOOT_BAR_ICON_ONLY_THRESHOLD = 3

/**
 * One foot-bar action. `icon` is REQUIRED and must be ligature-backed (both render modes carry
 * a glyph; OutlinedControl throws on drawable icons). `label` must be non-blank (used as text in
 * ≤2 bars and as the icon-only a11y fallback). `modifier` is appended after weight(1f) so existing
 * per-button modifiers (alpha-dim, selected/disabled semantics) survive migration verbatim.
 */
data class FootAction(
    val label: String,
    val icon: DinghyIcon,
    val onClick: () -> Unit,
    val intent: Intent = Intent.Neutral,
    val contentDescription: String? = null,
    val onLongClick: (() -> Unit)? = null,
    val enabled: Boolean = true,
    val fill: Color? = null,
    val modifier: Modifier = Modifier,
) {
    init {
        require(label.isNotBlank()) { "FootAction.label must be non-blank" }
        require(icon.primary is IconRef.Ligature) { "FootAction.icon must be ligature-backed: ${icon.alternate}" }
    }
}

/**
 * Build a [FootAction] from a named [ControlSpec]. The spec may be icon-only (`labelRes = null`,
 * e.g. Spool's load/scan/unload) — in that case the `contentDescriptionRes` supplies the required
 * non-blank label (it renders only in ≤2 bars; in ≥3 bars it's the a11y fallback). Codex BLOCKER F1.
 */
@Composable
fun footAction(spec: ControlSpec, onClick: () -> Unit, enabled: Boolean = true): FootAction {
    val labelRes = spec.labelRes ?: spec.contentDescriptionRes
    requireNotNull(labelRes) { "spec ${spec.key} needs a label or contentDescription for a foot button" }
    return FootAction(
        label = stringResource(labelRes),
        icon = requireNotNull(spec.icon) { "spec ${spec.key} needs an icon for a foot button" },
        onClick = onClick,
        intent = spec.intent,
        contentDescription = spec.contentDescriptionRes?.let { stringResource(it) },
        enabled = enabled,
    )
}

@Composable
fun FootButtonBar(
    uDp: Dp,
    actions: List<FootAction>,
    modifier: Modifier = Modifier,
) {
    val iconOnly = actions.size >= FOOT_BAR_ICON_ONLY_THRESHOLD
    CompositionLocalProvider(LocalUnitDp provides uDp) {
        Row(
            modifier = modifier.fillMaxWidth().controlHeight(uDp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { a ->
                OutlinedControl(
                    label = if (iconOnly) "" else a.label,
                    onClick = a.onClick,
                    modifier = Modifier.weight(1f).then(a.modifier),
                    intent = a.intent,
                    icon = a.icon,
                    onLongClick = a.onLongClick,
                    contentDescription = a.contentDescription ?: a.label,
                    enabled = a.enabled,
                    fill = a.fill,
                )
            }
        }
    }
}
```

> Confirm `ControlSpec`'s property names (`labelRes`, `icon`, `intent`, `contentDescriptionRes`, `key`) against `control/ControlSpec.kt` and `control/ControlSpecs.kt` before relying on them; adjust if they differ. `IconRef`/`DinghyIcon.primary` are in `designsystem/icons/DinghyIcon.kt`.

- [ ] **Step 4: Run both test sets, verify pass**

```bash
... "E:\Android\gw.bat :app:testDebugUnitTest --tests '*FootActionTest' --no-daemon" | tr -d '\r'   # guard tests PASS
... "E:\Android\gw.bat :app:connectedDebugAndroidTest --no-daemon" | tr -d '\r'                      # render tests PASS (device attached)
```
> The `connectedDebugAndroidTest` run needs flox or moto attached (`[[dinghy-test-devices]]`). If filtering to just this class is needed, use `-Pandroid.testInstrumentationRunnerArguments.class=works.mees.dinghy.designsystem.components.FootButtonBarRenderTest`.

- [ ] **Step 5: Build the whole module (lambda overload still present → all call sites compile)**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/FootButtonBar.kt \
        app/src/test/java/works/mees/dinghy/designsystem/components/FootActionTest.kt \
        app/src/androidTest/java/works/mees/dinghy/designsystem/components/FootButtonBarRenderTest.kt
git commit -m "feat(footbar): add FootAction + count-driven list FootButtonBar overload"
```

---

## Task 2: Register the foot-bar icon tokens (NOT temp_presets)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`
- Modify: `app/src/main/java/works/mees/dinghy/control/ControlSpecs.kt`
- Modify: the duplicate-ligature allow-list in `DinghyIconsTest.kt` (around line 83)

- [ ] **Step 1: Add the new tokens in `DinghyIcons.kt`** (after the App/Printer Settings split block)

```kotlin
// --- Foot-bar button conformance (2026-06-17, OWNER-LOCKED — icon law [[dinghy-never-pick-icons-ask]]).
// play_circle / stop_circle are from img/material-icon-bucket.json; hourglass / print_add / save /
// tab_close are new — ALL gated by tools/verify_ligatures.py (Task 2 step 4). mode_heat_off is shared
// with HideTemps (Temp vs Console never co-render) → allow-listed in DinghyIconsTest.
val CalibrationRun   = DinghyIcon(IconRef.Ligature("play_circle"),  alternate = "calibration_run")
val CalibrationWait  = DinghyIcon(IconRef.Ligature("hourglass"),    alternate = "calibration_wait")
val CalibrationAbort = DinghyIcon(IconRef.Ligature("stop_circle"),  alternate = "calibration_abort")
val PrinterAdd       = DinghyIcon(IconRef.Ligature("print_add"),    alternate = "printer_add")
val Save             = DinghyIcon(IconRef.Ligature("save"),         alternate = "save")
val DialogClose      = DinghyIcon(IconRef.Ligature("tab_close"),    alternate = "dialog_close")
val TempCooldown     = DinghyIcon(IconRef.Ligature("mode_heat_off"),alternate = "temp_cooldown")
```

- [ ] **Step 2: Add them to `DinghyIcons.all`**

Append to the `all` list (before the closing `)`):

```kotlin
        CalibrationRun, CalibrationWait, CalibrationAbort, PrinterAdd, Save, DialogClose, TempCooldown,
```

- [ ] **Step 3: Give `calibrationHomeAll` an icon in `ControlSpecs.kt`**

At `ControlSpecs.kt` (the `calibrationHomeAll` spec, ~line 36) change `icon = null` → reuse `MoveHomeAll`, and add a content description (it renders icon-only in ≥3 bars):

```kotlin
    val calibrationHomeAll = ControlSpec(
        key = ControlKey("calibration.home_all"),
        labelRes = R.string.calibration_home_all,
        contentDescriptionRes = R.string.cd_calibration_home_all, // ADD — see step 3a
        icon = DinghyIcons.MoveHomeAll, // owner: home_app_logo (reuses MoveHomeAll)
        intent = Intent.Go,
        type = ControlType.Button,
    )
```

- [ ] **Step 3a: Add the string resource** `cd_calibration_home_all`

In `app/src/main/res/values/strings.xml`, add near other `cd_*` entries:

```xml
<string name="cd_calibration_home_all">Home all axes</string>
```

> If `ControlCatalogDriftTest` requires `contentDescriptionRes` only for icon-only specs and forbids it for labeled ones, check that test's rule first; the spec is now both labeled AND sometimes icon-only, so the cd is needed for the icon-only render. Adjust the test's expectation if it asserts otherwise.

- [ ] **Step 4: Add the new ligatures to the gate's `NEEDED` set, then run it**

> Codex F2: `tools/verify_ligatures.py` checks a **hardcoded `NEEDED` set**, not `DinghyIcons.kt` — so new ligatures must be added there or the gate silently skips them. Add a block to `NEEDED` (de-duped; `mode_heat_off` is already present from the Console filters, so do NOT re-add it):

```python
    # Foot-bar button conformance (2026-06-17): play_circle=calibration run/start,
    # hourglass=calibration in-progress, stop_circle=calibration abort, print_add=add printer,
    # save=save config/mesh, tab_close=field-takeover cancel.
    "play_circle", "hourglass", "stop_circle", "print_add", "save", "tab_close",
```

Run: `python3 tools/verify_ligatures.py`
Expected: exits 0, listing the new ligatures as resolved.
**If `hourglass`, `print_add`, `save`, or `tab_close` does NOT resolve → STOP and ASK Matthew for a replacement glyph. Do not substitute (icon law).** (`play_circle`/`stop_circle` are in the bundled font per the icon bucket.)

- [ ] **Step 5: Allow-list `mode_heat_off` in `DinghyIconsTest.kt`**

Find the duplicate-ligature allow-list (~line 83, currently `output_circle`, `palette`, `settings`, `print`) and add `"mode_heat_off"`. Then run:

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests '*DinghyIconsTest*' --no-daemon" | tr -d '\r'`
Expected: PASS (uniqueness guard happy with the allow-listed duplicate).

- [ ] **Step 6: Build + commit**

```bash
... "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'   # BUILD SUCCESSFUL
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt \
        app/src/main/java/works/mees/dinghy/control/ControlSpecs.kt \
        app/src/main/res/values/strings.xml \
        tools/verify_ligatures.py \
        app/src/*/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt
git commit -m "feat(icons): register foot-bar conformance glyphs; home_all gets MoveHomeAll"
```

---

## Migration recipe (read once; applied in Tasks 3–9)

For each `FootButtonBar(uDp = X) { … }`:

1. Replace the trailing lambda with `actions = <list>`.
2. **Static count** → `listOf(FootAction(...), …)`. **Conditional count** → `buildList { if (cond) add(FootAction(...)) … }` so the LIVE size drives the mode.
3. Each child `OutlinedControl(...)` → `FootAction(...)`:
   - Drop `Modifier.weight(1f)` (the bar re-adds it). Move any *other* modifiers (`.alpha(...)`, `.semantics { … }`) into `modifier = Modifier.<those>`.
   - `symbol`/`icon` → `icon = <token>` (use the locked assignment if it was iconless before).
   - Keep `intent`, `onClick`, `onLongClick`, `enabled`, `fill`, `contentDescription`.
   - **Currently icon-only buttons (`label = ""`)**: supply a non-blank `label` (the bar blanks it when ≥3). Use the existing `contentDescription` string (or a real label res) — e.g. `label = stringResource(R.string.cd_console_hide_temps)`.
   - `OutlinedControl(spec = S, onClick = C)` → `footAction(S, onClick = C, enabled = …)`.
4. Resolve `stringResource(...)` inside the list builder (composable scope — fine).
5. Build + commit per task; the build stays green because the lambda overload still exists.

---

## Task 3: PrintStatusField (conditional `buildList`; already iconed — worked example)

**Files:** Modify `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt:127`

- [ ] **Step 1: Replace the foot bar**

```kotlin
FootButtonBar(
    uDp = uDp,
    actions = buildList {
        if (isPrinting) {
            if (isPaused) add(FootAction(stringResource(R.string.printstatus_foot_resume),
                DinghyIcons.FootResume, onResume, Intent.Go))
            else add(FootAction(stringResource(R.string.printstatus_foot_pause),
                DinghyIcons.PauseCircle, onPause, Intent.Warn))
            add(FootAction(stringResource(R.string.printstatus_foot_cancel),
                DinghyIcons.FootCancel, { showCancelGuard = true }, Intent.Danger))
        } else if (isComplete) {
            add(FootAction(stringResource(R.string.printstatus_foot_dismiss),
                DinghyIcons.FootDismiss, onDismiss, Intent.Go))
            add(FootAction(stringResource(R.string.home_foot_system),
                DinghyIcons.FootSystem, { onNavigate(NavDest.System) }, Intent.Accent))
        } else {
            add(FootAction(stringResource(R.string.home_foot_preheat),
                DinghyIcons.FootPreheat, onPreheat, Intent.Warn))
            add(FootAction(stringResource(R.string.home_foot_system),
                DinghyIcons.FootSystem, { onNavigate(NavDest.System) }, Intent.Accent))
        }
    },
)
```

> Every branch is ≤2 buttons → icon+text, unchanged from today (PrintStatus was already conformant; this is an API conversion). Import `FootAction` from `works.mees.dinghy.designsystem.components`.

- [ ] **Step 2: Build + commit**

```bash
... "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'   # BUILD SUCCESSFUL
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
git commit -m "refactor(printstatus): migrate foot bar to FootAction list API"
```

---

## Task 4: Single-button "Back" bars (add the Back icon)

Each of these is a lone Back button (1 ≤2 → icon+text) that currently has **no icon**. Add `icon = DinghyIcons.Back`. Convert each to `actions = listOf(FootAction(stringResource(R.string.common_back), DinghyIcons.Back, onBack, Intent.Accent))`.

**Files & lines:**
- `app/.../ui/screen/SystemPageScreen.kt:171`
- `app/.../ui/screen/PrinterSettingsScreen.kt:293`
- `app/.../ui/systeminfo/SystemInformationScreen.kt:260`
- `app/.../ui/outputs/OutputToggleControl.kt:120`
- `app/.../ui/outputs/OutputsScreen.kt:245` (verify its button label/onClick; it had 1 control with an icon already — convert API only, keep its existing icon)
- `app/.../ui/webcam/WebcamScreen.kt:225` (already has `common_back` with an icon — API-only conversion; keep its icon/intent)
- `app/.../ui/calibration/CalibrationHubScreen.kt:177` (already iconed back — API-only)
- `app/.../ui/screen/AppSettingsScreen.kt:268` (Codex F5: 1-button Back bar — convert API only, keep its icon/intent)

- [ ] **Step 1: Convert each file's bar** following the recipe. For the iconless ones (SystemPage, PrinterSettings, SystemInformation, OutputToggle) the only behavioral change is the added Back glyph. For already-iconed ones, it's a pure API conversion.

> Read each bar first; if any has a non-Back button, preserve its existing label/icon/intent/onClick exactly. (Per Codex F5, AppSettings is a 1-button Back bar.)

- [ ] **Step 2: Build + commit**

```bash
... "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'   # BUILD SUCCESSFUL
git add app/src/main/java/works/mees/dinghy/ui/screen/SystemPageScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/screen/PrinterSettingsScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/outputs/OutputToggleControl.kt \
        app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/screen/AppSettingsScreen.kt
git commit -m "refactor(footbar): migrate single-button + simple bars; add Back glyph where missing"
```

---

## Task 5: Files, Console, Macros (per-button modifier passthrough — worked examples)

**Files:**
- `app/.../ui/files/FilesScreen.kt:623`
- `app/.../ui/console/ConsoleScreen.kt:226`
- `app/.../ui/macros/BookmarkedMacrosScreen.kt:519` and `:678`

- [ ] **Step 1: Files (3 buttons → icon-only; alpha-dim + disabled semantics must survive)**

```kotlin
FootButtonBar(
    uDp = uDp, // use the bar's existing uDp expression
    actions = listOf(
        FootAction(stringResource(R.string.common_back), DinghyIcons.Back, onBack, Intent.Accent),
        FootAction(
            label = stringResource(R.string.files_foot_print),
            icon = DinghyIcons.Print,
            onClick = { if (startEnabled) onStartPrint() },
            intent = Intent.Go,
            contentDescription = stringResource(R.string.cd_files_print),
            modifier = Modifier
                .alpha(if (startEnabled) 1f else 0.38f)
                .then(if (!startEnabled) Modifier.semantics { disabled() } else Modifier),
        ),
        FootAction(
            label = stringResource(R.string.files_foot_delete),
            icon = DinghyIcons.Delete,
            onClick = { if (deleteEnabled) onDelete() },
            intent = Intent.Danger,
            contentDescription = stringResource(R.string.cd_files_delete), // confirm res name
            modifier = Modifier
                .alpha(if (deleteEnabled) 1f else 0.38f)
                .then(if (!deleteEnabled) Modifier.semantics { disabled() } else Modifier),
        ),
    ),
)
```

> Confirm the bar's actual `uDp` argument expression and the delete `contentDescription` res name by reading `FilesScreen.kt:623`. Imports: `androidx.compose.ui.draw.alpha`, `androidx.compose.ui.semantics.disabled`, `androidx.compose.ui.semantics.semantics`.

- [ ] **Step 2: Console (Back + 3 toggle buttons = 4 → icon-only; `selected` semantics + state-flipped intent)**

> Codex F4: the real bar (`ConsoleScreen.kt:224-270`) is **Back plus three toggles** — 4 actions, not 3. Back goes first. Each toggle was `label = ""`; supply a non-blank label (the cd string) and carry the `selected` semantics via `modifier`:

```kotlin
FootButtonBar(
    uDp = uDp, // existing expression
    actions = listOf(
        FootAction(stringResource(R.string.common_back), DinghyIcons.Back, onBack, Intent.Accent,
            contentDescription = stringResource(R.string.common_back)),
        FootAction(
            label = stringResource(R.string.cd_console_hide_temps),
            icon = DinghyIcons.HideTemps,
            onClick = onToggleTemps,
            intent = if (hideTemps) Intent.Accent else Intent.Neutral,
            contentDescription = stringResource(R.string.cd_console_hide_temps),
            modifier = Modifier.semantics { selected = hideTemps },
        ),
        FootAction(
            label = stringResource(R.string.cd_console_hide_timelapse),
            icon = DinghyIcons.HideTimelapse,
            onClick = onToggleTimelapse,
            intent = if (hideTimelapse) Intent.Accent else Intent.Neutral,
            contentDescription = stringResource(R.string.cd_console_hide_timelapse),
            modifier = Modifier.semantics { selected = hideTimelapse },
        ),
        FootAction(
            label = stringResource(R.string.cd_console_hide_prompts), // confirm res name
            icon = DinghyIcons.HidePrompts,
            onClick = onTogglePrompt,
            intent = if (hidePrompt) Intent.Accent else Intent.Neutral,
            contentDescription = stringResource(R.string.cd_console_hide_prompts),
            modifier = Modifier.semantics { selected = hidePrompt },
        ),
    ),
)
```

> Read `ConsoleScreen.kt:224-270` for the exact prompt-toggle res name and Back `onClick`/intent. All 4 controls (Back + 3 toggles) are in this foot bar. Import `androidx.compose.ui.semantics.selected`.

- [ ] **Step 3: Macros — both bars (`:519` 3-button, `:678` 2-button)**

Read both bars. They are already iconed (`macros_foot_back/manage/execute` and `macros_foot_back/show_hidden`). Convert API-only, preserving the alpha/disabled-semantics modifiers at `:540`. The `:519` bar (3 buttons) → icon-only; `:678` (2) → icon+text.

- [ ] **Step 4: Build + commit**

```bash
... "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'   # BUILD SUCCESSFUL
git add app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt
git commit -m "refactor(footbar): migrate Files/Console/Macros with per-button modifier passthrough"
```

---

## Task 6: Calibration screens (new glyphs + home_all + abort + save; BedMesh verification gate)

**Files:**
- `app/.../ui/calibration/ScrewsTiltScreen.kt:194`
- `app/.../ui/calibration/TiltScreen.kt:183`
- `app/.../ui/calibration/ProbeCalibrateScreen.kt:325`
- `app/.../ui/calibration/BedMeshScreen.kt:364` and `:484`

**Icon assignments (from the locked spec):**

| Action (string res) | Token |
|---|---|
| `calibration_run` | `CalibrationRun` |
| `calibration_run_again` | `Revert` (refresh) |
| `calibration_running` / `probe_starting` | `CalibrationWait` |
| `calibration_start` | `CalibrationRun` |
| `calibration_accept` | `CheckCircle` |
| `calibration_abort` | `CalibrationAbort` (stop_circle) |
| `calibration_save_config` / `mesh_save` | `Save` |
| `calibration_home_all` (ControlSpec) | `footAction(ControlSpecs.calibrationHomeAll, …)` |
| `mesh_calibrate` | `RoutineBedMesh` |
| `mesh_apply` | `CheckCircle` |
| `mesh_remove` | `Delete` |
| `mesh_save_confirm` | `CheckCircle` |
| `common_cancel` (BedMesh SaveName takeover) | `DialogClose` |

- [ ] **Step 1: Migrate each calibration bar** following the recipe. These bars are mostly conditional (running/run/run_again swap) — use `buildList` so the live count is right. The home-all button uses `footAction(ControlSpecs.calibrationHomeAll, onClick = …)`.

- [ ] **Step 2: VERIFICATION GATE — BedMesh `check_circle` (Codex F4-sibling, spec gate 1)**

In `BedMeshScreen.kt`, confirm the `:364` main bar (containing `mesh_apply` → `check_circle`) and the `:484` SaveName field-takeover bar (containing `mesh_save_confirm` → `check_circle`) are **mutually exclusive** (the takeover replaces the main bar). Verify by reading the surrounding `when`/state branch. **If they can render at the same time, STOP and ASK Matthew for a distinct `mesh_apply` glyph** (do not pick one). Record the verdict in the commit message.

- [ ] **Step 3: Build + commit**

```bash
... "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'   # BUILD SUCCESSFUL
git add app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/calibration/TiltScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt
git commit -m "refactor(footbar): migrate calibration screens; assign run/wait/abort/save/home glyphs (BedMesh check_circle states verified exclusive)"
```

---

## Task 7: Printers screen

**Files:** `app/.../ui/screen/PrintersScreen.kt:242` (4-button bar → icon-only)

| Action | Token |
|---|---|
| `common_back` | `Back` |
| `printers_add` | `PrinterAdd` |
| `printers_edit` | `Edit` |
| `printers_delete` | `Delete` |

- [ ] **Step 1: Migrate the bar** — Codex F5: this is a **static 4-action bar with state-flipped intents** (not gated edit/delete buttons). Convert to `listOf(...)`, preserving each button's existing intent expression (including any state-dependent intent).
- [ ] **Step 2: Build + commit**

```bash
... "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'
git add app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt
git commit -m "refactor(footbar): migrate Printers bar; add/edit/delete/back glyphs"
```

---

## Task 8: Temperature screen

**Files:** `app/.../ui/temperature/TemperatureScreen.kt` bars at `:657` (3, icon-only), `:687` (1, presets), `:695` (3, icon-only — Back · Cooldown · Monitor-return), `:741` (PresetPicker takeover, 1).

| Action | Token | Ligature |
|---|---|---|
| `temp_cooldown` | `TempCooldown` | `mode_heat_off` (registered in Task 2) |
| `temp_presets` | `TempPresets` (NEW) | **`thermostat_auto`** (owner-chosen 2026-06-17; pre-verified resolvable in the bundled v2.944 font; distinct from `thermostat`=LauncherTemperature header and `format_list_bulleted`=MonitorMode, both also on this screen) |

> Note on the same-screen icon budget: in **Adjust mode** the footer is TWO stacked bars — the Presets bar (`:687`) and the Back·Cooldown·Monitor bar (`:695`) render simultaneously. `thermostat_auto` (Presets) must NOT equal any glyph in that second bar (`Back`=arrow_back, `TempCooldown`=mode_heat_off, `MonitorMode`=format_list_bulleted) nor the header `thermostat` — confirmed distinct.

- [ ] **Step 0: register `TempPresets`.** Add the token in `DinghyIcons.kt` + the `all` list; **add `"thermostat_auto"` to `NEEDED` in `tools/verify_ligatures.py`** (Codex F2 — the gate won't check it otherwise); run `python3 tools/verify_ligatures.py` (must exit 0). `thermostat_auto` is a unique ligature (not shared), so NO `DinghyIconsTest` allow-list entry is needed.

```kotlin
val TempPresets = DinghyIcon(IconRef.Ligature("thermostat_auto"), alternate = "temp_presets")
```

- [ ] **Step 1: Migrate all four Temperature bars** following the recipe. The `:657`/`:695` icon-only bars are already iconed (API conversion); apply `TempCooldown` to the cooldown button and `TempPresets` to the presets button.
- [ ] **Step 2: VERIFICATION (spec gate 2):** confirm `TempPresets` does not duplicate any other glyph rendered simultaneously on the Temperature screen; if the owner's pick collides, ASK.
- [ ] **Step 3: Build + commit**

```bash
... "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'
git add app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt \
        app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
git commit -m "refactor(footbar): migrate Temperature bars; temp_cooldown + temp_presets glyphs"
```

---

## Task 9: Remaining bars — Spool, Extrude, Move, FineTune

**Files:** (Codex F5 corrections applied)
- `app/.../ui/spool/SpoolScreen.kt:484` (**always 3** ControlSpec actions → `footAction(spec, …)`; only the Load/Unload identity is conditional, not the count), `:612` (2), `:729` (2)
- `app/.../ui/extrude/ExtrudeScreen.kt:382` (3), `:434` (1)
- `app/.../ui/move/MoveScreen.kt:743` (**static 4-action** bar, not conditional)
- `app/.../ui/finetune/FineTuneScreen.kt:401` (2) — `finetune_reset_all` → `ResetSettings`

| Action | Token |
|---|---|
| `finetune_reset_all` | `ResetSettings` |
| all others | already iconed — API conversion only (Spool uses `footAction(spec, …)` for its ControlSpec buttons) |

- [ ] **Step 1: Migrate each bar** following the recipe. Spool `:484` uses the `ControlSpec` overload → `footAction(...)` (3 actions, icon-only). FineTune `:401` only needs the new `ResetSettings` glyph on the reset-all button (its other button keeps its icon). Move `:743` is a static 4-action `listOf(...)`.
- [ ] **Step 2: Build + commit**

```bash
... "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'
git add app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt
git commit -m "refactor(footbar): migrate Spool/Extrude/Move/FineTune bars; reset_all glyph"
```

---

## Task 10: Delete the lambda overload + migrate the preview + final gates

**Files:**
- Modify: `app/.../designsystem/components/FootButtonBar.kt` (remove the lambda overload)
- Modify: `app/.../preview/DesignKitComponentPreviews.kt:243` (the preview foot bar)

- [ ] **Step 1: Migrate the preview** in `DesignKitComponentPreviews.kt` to the list API. Make it show BOTH modes (a ≤2 icon+text bar and a ≥3 icon-only bar) so the design kit documents the rule:

```kotlin
FootButtonBar(uDp = grid.uDp, actions = listOf(
    FootAction("Back", DinghyIcons.Back, {}, Intent.Accent),
    FootAction("Save", DinghyIcons.Save, {}, Intent.Go),
)) // ≤2 → icon+text
FootButtonBar(uDp = grid.uDp, actions = listOf(
    FootAction("Back", DinghyIcons.Back, {}, Intent.Accent),
    FootAction("Edit", DinghyIcons.Edit, {}, Intent.Accent),
    FootAction("Delete", DinghyIcons.Delete, {}, Intent.Danger),
)) // ≥3 → icon-only
```

- [ ] **Step 2: Delete the old lambda overload + fix its KDoc**

Remove the original `fun FootButtonBar(uDp: Dp, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit)` from `FootButtonBar.kt`. Remove now-unused imports (`RowScope`). Codex NIT F6: the file-level KDoc (`FootButtonBar.kt:16-63`) still shows the old trailing-lambda usage example and a `@param content` — rewrite the example to the `actions = listOf(FootAction(...))` form and drop the `@param content` line.

- [ ] **Step 3: Full build — this is the compile-enforcement proof**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL. **If any file fails to compile, a call site was missed — migrate it (the build is now the conformance gate).**

- [ ] **Step 4: Run the full unit-test suite + ligature gate**

```bash
... "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" | tr -d '\r'   # all green
python3 tools/verify_ligatures.py                                          # exit 0
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/FootButtonBar.kt \
        app/src/main/java/works/mees/dinghy/preview/DesignKitComponentPreviews.kt
git commit -m "feat(footbar): delete lambda overload — count rule now compile-enforced"
```

---

## Task 11: On-device UAT (flox + moto)

- [ ] **Step 1: Build, then install the matching ABI slice on BOTH devices** (`[[dinghy-test-devices]]`): flox `0a64b42e` (armeabi-v7a), moto `ZY22LBDRM9` (arm64-v8a). Force a fresh build if Gradle reports UP-TO-DATE (`[[dinghy-stale-apk-uat-gate]]`) and confirm APK mtime is after the last commit.
- [ ] **Step 2: Owner walks each migrated screen** — verify ≤2 bars show icon+text, ≥3 bars show icon-only, every new glyph reads correctly (calibration run/wait/abort/save/home_all, printers add/edit/delete, temp cooldown/presets, finetune reset-all), and the dim/disabled/selected states still look right on Files/Console/Macros.
- [ ] **Step 3:** Fix any owner-flagged glyph/spacing issue (ASK before any new icon). No commit until owner signs off.

---

## Self-Review notes

- **Spec coverage:** API change (T1), all icon assignments incl. the F1 home_all and F4/F5/F6 collision fixes (T2/T6/T8/T9), all 30 call sites + preview (T3–T10), compile-enforcement (T10), guard tests (T1), ligature/uniqueness gates (T2/T8/T10), both verification gates (T6/T8). The one OPEN item (`temp_presets` ligature) is isolated to T8 and explicitly gated.
- **Type consistency:** `FootAction`/`footAction`/`FOOT_BAR_ICON_ONLY_THRESHOLD` names are used identically across tasks. Token names match `DinghyIcons.kt` additions (`CalibrationRun/CalibrationWait/CalibrationAbort/PrinterAdd/Save/DialogClose/TempCooldown/TempPresets`).
- **Known read-required spots** (flagged inline, not placeholders): exact `uDp` expressions, a few `cd_*` res names, and whether specific bars are conditional — the executor reads the current bar before converting. These are mechanical confirmations, not design decisions.
