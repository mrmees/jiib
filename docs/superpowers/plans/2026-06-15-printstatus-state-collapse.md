# PrintStatus State Collapse — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Collapse every Moonraker printer state (all `print_stats` states + Klipper Error/Shutdown, re-routed) onto one Standby-based home skeleton with a reworked Focus frame, deleting the bespoke per-state Printing/Paused/Terminal code.

**Architecture:** `TopRoute.derive()` admits Klipper Error/Shutdown to the shell; `PrintStatusContent`'s four-mode `when` collapses to a single path (universal `HomeFocus` + universal `HomeField` + Preheat/System foot bar). The reworked focus shows a two-axis state title, a 30%-bottom-end brand watermark, and a top-start glance block at uniform `focusHero` sizing. Per-state richness (progress ring, pause/cancel, terminal stats, babystep) and its model/control logic are deleted, not retrofitted.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit (host unit tests), Gradle (Windows-side build helper).

**Spec:** `docs/superpowers/specs/2026-06-15-printstatus-state-collapse-design.md`

---

## Build / test commands (this repo builds Windows-side)

`./gradlew` does NOT run from WSL. Use the helper, pipe through `tr -d '\r'`, and guard against hung daemons. The process **exit code is authoritative**.

- **Unit tests (one class):**
  ```bash
  timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.route.TopRouteTest' --no-daemon" 2>&1 | tr -d '\r'
  ```
- **Full unit suite:**
  ```bash
  timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
  ```
- **Compile / build debug:**
  ```bash
  timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'
  ```
- **Release (R8) build:**
  ```bash
  timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleRelease --no-daemon" 2>&1 | tr -d '\r'
  ```

If a run hangs past the `timeout`, kill stragglers: `/mnt/c/Windows/System32/taskkill.exe /F /IM java.exe` (Windows `java.exe` children outlive the WSL process).

---

## File structure

**New code (small, focused):**
- `homeStateLabelRes(printState, klippyState)` — pure two-axis title-label resolver (in `PrintStatusScreen.kt`, beside the existing `statusLabelRes`).
- `HomeFocus` — the universal Focus composable (rework of `StandbyFocus` in `PrintStatusFocus.kt`).
- `HomeField` — the universal Field composable (rename of `PrintStatusStandbyField` in `PrintStatusField.kt`).
- One new string: `printstatus_status_shutdown`.

**Edited:**
- `ui/route/TopRoute.kt` — re-route Klipper Error/Shutdown to `Shell`.
- `ui/printstatus/PrintStatusScreen.kt` — collapse `PrintStatusContent`; thread printer-name + multi-printer flag; strip per-state state/logic.

**Deleted:**
- Composables: `PrintStatusFocus`, `TerminalFocus`, `PrintStatusActiveField`, `PrintStatusTerminalField`, `ShortcutRow`, `BabystepRow`, and the terminal stats/error-line helpers + the per-state foot-bar renderer.
- Logic files: `PrintStatusMode.kt`, `PrintStatusUiModel.kt`, `PrintStatusControlModel.kt` (and `classifyPrintStatus`, `uiModel`, `derivePrintStatusControls`, the pending-action machinery, `babystepVisible`, `nextBabystepStep`).
- Tests: `PrintStatusModeTest.kt`, `SampleFixturesTest.kt`, `PrintStatusControlModelTest.kt`, `PrintStatusUiModelTest.kt`.
- Rewritten: `PrintStatusPreviews.kt`, and the `SampleFixtures` mode helpers it uses.

---

## Task 1: Re-route Klipper Error/Shutdown in `TopRoute.derive()`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt:49-54`
- Test: `app/src/test/java/works/mees/dinghy/ui/route/TopRouteTest.kt`

- [ ] **Step 1: Update the existing failing test arm.** Replace the whole `klippyErrorShutdownDisconnected_routeToSplash` test (lines 54-63) with these two tests:

```kotlin
    @Test
    fun klippyDisconnected_routesToSplash() {
        assertEquals(
            "klippyState=Disconnected must route to Splash",
            TopRoute.Splash,
            derive(cfgPresent = true, s = PrinterState(klippyState = KlippyState.Disconnected, connection = connected)),
        )
    }

    @Test
    fun klippyErrorOrShutdown_connectedAndConfig_routeToShell() {
        for (k in listOf(KlippyState.Error, KlippyState.Shutdown)) {
            assertEquals(
                "klippyState=$k with a live connection must reach the home Shell",
                TopRoute.Shell,
                derive(cfgPresent = true, s = PrinterState(klippyState = k, connection = connected)),
            )
        }
    }

    @Test
    fun klippyErrorOrShutdown_butDisconnected_routeToSplash() {
        for (k in listOf(KlippyState.Error, KlippyState.Shutdown)) {
            assertEquals(
                "klippyState=$k must still go to Splash when the socket is down",
                TopRoute.Splash,
                derive(cfgPresent = true, s = PrinterState(klippyState = k, connection = ConnectionState.Disconnected)),
            )
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail.**

Run: `timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.route.TopRouteTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — `klippyErrorOrShutdown_connectedAndConfig_routeToShell` expects `Shell` but current code returns `Splash`.

- [ ] **Step 3: Implement the re-route.** Replace `derive()` (TopRoute.kt:49-54) with:

```kotlin
fun derive(cfgPresent: Boolean, s: PrinterState): TopRoute = when {
    !cfgPresent -> TopRoute.Connect
    s.connection !is ConnectionState.Connected -> TopRoute.Splash      // real connection fault
    s.klippyState == KlippyState.Disconnected ||
        s.klippyState == KlippyState.Startup -> TopRoute.Splash         // host not up yet
    else -> TopRoute.Shell                                              // Ready, Error, Shutdown → home
}
```

Also update the KDoc above `derive()` (TopRoute.kt:25-48) so the arm list matches: connection-fault arm now precedes the klippy arm, and only `Disconnected`/`Startup` route to Splash while `Error`/`Shutdown` (connected) reach `Shell` (the home skeleton renders the fault state — owner decision 2026-06-15). Update the `TopRouteTest` class KDoc (lines 12-22) similarly.

- [ ] **Step 4: Run the tests to verify they pass.**

Run: `timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.route.TopRouteTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS (all arms, including the retained `klippyStartup_routesToSplash` and `socketReconnecting_*`).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt app/src/test/java/works/mees/dinghy/ui/route/TopRouteTest.kt
git commit -m "feat(route): admit Klipper Error/Shutdown to the home shell"
```

---

## Task 2: Two-axis home state-label helper

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` (add beside `statusLabelRes`, ~line 638)
- Modify: `app/src/main/res/values/strings.xml` (after line 75, the `printstatus_status_*` block)
- Test: `app/src/test/java/works/mees/dinghy/ui/printstatus/HomeStateLabelTest.kt` (create)

- [ ] **Step 1: Add the string resource.** In `strings.xml`, immediately after `<string name="printstatus_status_error">ERROR</string>` (line 75), add:

```xml
    <string name="printstatus_status_shutdown">SHUTDOWN</string>
```

- [ ] **Step 2: Write the failing test.** Create `HomeStateLabelTest.kt`:

```kotlin
package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.R
import works.mees.dinghy.state.KlippyState
import works.mees.dinghy.state.PrintState

class HomeStateLabelTest {

    @Test
    fun klippyShutdown_winsOverPrintState() {
        assertEquals(
            R.string.printstatus_status_shutdown,
            homeStateLabelRes(PrintState.Printing, KlippyState.Shutdown),
        )
    }

    @Test
    fun klippyError_winsOverPrintState() {
        assertEquals(
            R.string.printstatus_status_error,
            homeStateLabelRes(PrintState.Standby, KlippyState.Error),
        )
    }

    @Test
    fun klippyReady_fallsThroughToPrintState() {
        assertEquals(R.string.printstatus_status_printing, homeStateLabelRes(PrintState.Printing, KlippyState.Ready))
        assertEquals(R.string.printstatus_status_standby, homeStateLabelRes(PrintState.Standby, KlippyState.Ready))
        assertEquals(R.string.printstatus_status_complete, homeStateLabelRes(PrintState.Complete, KlippyState.Ready))
        assertEquals(R.string.printstatus_status_cancelled, homeStateLabelRes(PrintState.Cancelled, KlippyState.Ready))
    }
}
```

- [ ] **Step 3: Run to verify it fails.**

Run: `timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.printstatus.HomeStateLabelTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — `homeStateLabelRes` unresolved.

- [ ] **Step 4: Implement the helper.** In `PrintStatusScreen.kt`, directly after `statusLabelRes` (after line 638), add:

```kotlin
/** Two-axis home title label: a Klipper host fault (Shutdown/Error) takes precedence over the
 *  print-job state; otherwise the print-state label. Klipper Error reuses the ERROR string. */
internal fun homeStateLabelRes(
    printState: works.mees.dinghy.state.PrintState,
    klippyState: works.mees.dinghy.state.KlippyState,
): Int = when (klippyState) {
    works.mees.dinghy.state.KlippyState.Shutdown -> R.string.printstatus_status_shutdown
    works.mees.dinghy.state.KlippyState.Error -> R.string.printstatus_status_error
    else -> statusLabelRes(printState)
}
```

- [ ] **Step 5: Run to verify it passes.**

Run: `timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.printstatus.HomeStateLabelTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt app/src/main/res/values/strings.xml app/src/test/java/works/mees/dinghy/ui/printstatus/HomeStateLabelTest.kt
git commit -m "feat(printstatus): two-axis home state-label helper + SHUTDOWN string"
```

---

## Task 3: Rework `StandbyFocus` → universal `HomeFocus` (Part B layout + e-stop wiring + title data)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt:202-261` (`StandbyFocus` + `GlanceRow`)
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` (container entry: collect title data; `PrintStatusContent` signature + Standby branch call; preview overload)

This task is layout/Compose work — verified by **build-green + preview render**, not a unit test.

- [ ] **Step 1: Replace `StandbyFocus` and `GlanceRow`** (PrintStatusFocus.kt:202-257) with the universal `HomeFocus`. `isPrinting` is computed internally; e-stop is wired so the FocusFrame header e-stop appears while printing (preserving today's behavior — `StandbyFocus` never wired it):

```kotlin
/**
 * The universal home Focus (was StandbyFocus). Renders for EVERY printer state in the collapsed
 * skeleton: a 1U-header FocusFrame (two-axis state title + optional printer name), a faint brand
 * watermark at 30% of the focus's smaller edge pinned bottom-end, and a top-start glance block
 * (Nozzle · Bed · glance sensor · spool remaining) at uniform focusHero sizing.
 *
 * E-stop: PrintStatus owns its e-stop via the FocusFrame header (it is in AppShell `screenOwnsEstop`);
 * the header shows it only when `isPrinting && onEmergencyStop != null`. We pass both so e-stop stays
 * reachable while Printing/Paused. During Klipper Error/Shutdown the firmware is already halted, so the
 * header e-stop is intentionally absent (the meaningful recovery is a firmware restart — a build-up
 * concern). Named top-level composable for an independently-restartable scope (D-01/D-02).
 */
@Composable
internal fun HomeFocus(
    state: PrinterState,
    printerName: String?,
    isMultiPrinter: Boolean,
    spoolmanPresent: Boolean,
    activeSpoolCardState: ActiveSpoolCardState,
    onEmergencyStop: (() -> Unit)?,
    uDp: Dp,
) {
    val t = LocalTokens.current
    val nozzle = primaryHeater(state)
    val bed = state.heaters["heater_bed"]
    val glance = selectGlanceSensor(state.temperatureSensors)
    val spoolRemaining = (activeSpoolCardState as? ActiveSpoolCardState.Loaded)?.spool?.remainingWeight
    val isPrinting = state.printState == PrintState.Printing || state.printState == PrintState.Paused
    val stateLabel = stringResource(homeStateLabelRes(state.printState, state.klippyState))
    val title = if (isMultiPrinter && !printerName.isNullOrBlank()) "$printerName · $stateLabel" else stateLabel
    FocusFrame(
        title = title,
        icon = DinghyIcons.PrintStatusStandby,
        uDp = uDp,
        modifier = Modifier.fillMaxSize(),
        isPrinting = isPrinting,
        onEmergencyStop = onEmergencyStop,
        onPanic = onEmergencyStop,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Brand watermark: 30% of the smaller edge, bottom-end, faint accent2 tint.
            val markSize = minOf(maxWidth, maxHeight) * 0.30f
            Image(
                painter = painterResource(R.drawable.jiib_icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(t.accent2),
                alpha = 0.45f,
                modifier = Modifier.size(markSize).align(Alignment.BottomEnd),
            )
            // Glance block: top-start, tight, label + value both focusHero.
            Column(
                Modifier.align(Alignment.TopStart),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                GlanceRow(stringResource(R.string.printstatus_nozzle_label), tempActive(nozzle), t.seriesColor(0))
                GlanceRow(stringResource(R.string.printstatus_bed_label), tempActive(bed), t.seriesColor(1))
                glance?.let { GlanceRow(glanceLabel(it.name), fmt(it.temperature), t.text) }
                if (spoolmanPresent && spoolRemaining != null) {
                    GlanceRow(stringResource(R.string.printstatus_spool_label), "${spoolRemaining.roundToInt()} g", t.text)
                }
            }
        }
    }
}

/** One glance line: dim label + colored value, BOTH focusHero (normalized size), tight, one row. */
@Composable
internal fun GlanceRow(label: String, value: String, valueColor: Color) {
    val t = LocalTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.text2, style = DinghyType.focusHero.toTextStyle(t))
        Text(value, color = valueColor, style = DinghyType.focusHero.toTextStyle(t))
    }
}
```

Keep the existing `glanceLabel` helper (PrintStatusFocus.kt:259-261) unchanged. Remove the now-unused `import androidx.compose.foundation.layout.padding` only if nothing else in the file uses it (the build will warn, not fail — leave imports for Task 5's import sweep).

- [ ] **Step 2: Add title data to the container entry.** In `PrintStatusScreen.kt`, after the other `collectAsStateWithLifecycle` calls (after line 98), add:

```kotlin
    // Title data (Part B): the active printer's display name + whether >1 profile is saved
    // (multi-printer ⇒ prefix the name onto the state label).
    val printerName by container.activeName.collectAsStateWithLifecycle(initialValue = null)
    val profileCount by container.profileStore.profiles
        .map { it.size }
        .collectAsStateWithLifecycle(initialValue = 0)
```

Add the import `import kotlinx.coroutines.flow.map` if not present.

- [ ] **Step 3: Thread the two values into `PrintStatusContent`.** Add two parameters to the `PrintStatusContent` signature (after `httpBase`, ~line 439):

```kotlin
    printerName: String?,
    isMultiPrinter: Boolean,
```

At the `PrintStatusContent(...)` call site (line 290), pass:

```kotlin
            printerName = printerName,
            isMultiPrinter = profileCount > 1,
```

- [ ] **Step 4: Update the Standby branch to call `HomeFocus`.** In `PrintStatusContent`, the `PrintStatusMode.Standby` branch (lines 472-491) `focus = { StandbyFocus(...) }` becomes:

```kotlin
            focus = {
                HomeFocus(
                    state = state,
                    printerName = printerName,
                    isMultiPrinter = isMultiPrinter,
                    spoolmanPresent = spoolmanPresent,
                    activeSpoolCardState = activeSpoolCardState,
                    onEmergencyStop = onEmergencyStop,
                    uDp = grid.uDp,
                )
            },
```

- [ ] **Step 5: Update the stateless preview overload.** Find the `PrintStatusScreen(state: PrinterState, ...)` preview overload (the second entry that calls `PrintStatusContent` for `@Preview`). Add `printerName`/`isMultiPrinter` params with preview-friendly defaults (e.g. `printerName: String? = null, isMultiPrinter: Boolean = false`) and pass them into its `PrintStatusContent(...)` call. (If the overload derives `PrintStatusContent` args inline, supply `printerName = printerName, isMultiPrinter = isMultiPrinter`.)

- [ ] **Step 6: Build to verify it compiles.**

Run: `timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL. (The Printing/Paused/Terminal branches still exist and still compile — they're removed in Task 4.)

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
git commit -m "feat(printstatus): universal HomeFocus — two-axis title, bottom-end watermark, top-start glance, e-stop wired"
```

---

## Task 4: Collapse `PrintStatusContent` to a single path + rename Field → `HomeField`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:466-562` (`PrintStatusContent` body)
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt:77-143` (`PrintStatusStandbyField` → `HomeField`)

- [ ] **Step 1: Rename the Field composable.** In `PrintStatusField.kt`, rename `PrintStatusStandbyField` (line 78) to `HomeField` (signature otherwise unchanged). Update its KDoc to say it is the universal home Field.

- [ ] **Step 2: Replace the `Crossfade`/`when(mode)` block** (PrintStatusScreen.kt:466-562) with a single rendering path. The whole `androidx.compose.animation.Crossfade(...) { when (crossfadeMode) { ... } }` becomes:

```kotlin
    ScreenScaffold(
        fieldFramed = false,
        focus = {
            HomeFocus(
                state = state,
                printerName = printerName,
                isMultiPrinter = isMultiPrinter,
                spoolmanPresent = spoolmanPresent,
                activeSpoolCardState = activeSpoolCardState,
                onEmergencyStop = onEmergencyStop,
                uDp = grid.uDp,
            )
        },
        field = {
            HomeField(
                idleActions = idleActions,
                failureText = failureText,
                onNavigate = onNavigate,
                onPreheat = onPreheat,
                uDp = grid.uDp,
            )
        },
    )
```

Keep the enclosing screen-root `BoxWithConstraints` + `rememberUnitGrid` (lines 462-463) — `grid.uDp` is still used. The `mode` parameter of `PrintStatusContent` is now unused (cleaned in Task 6); leave it for now.

- [ ] **Step 3: Build to verify it compiles.**

Run: `timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL with "unused parameter"/"unused symbol" warnings for the now-orphaned mode/ui/metadata-only paths. `PrintStatusFocus`, `TerminalFocus`, `PrintStatusActiveField`, `PrintStatusTerminalField` are now unreferenced (deleted in Task 5).

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
git commit -m "refactor(printstatus): collapse the four-mode render to one home path"
```

---

## Task 5: Delete the orphaned per-state composables

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt`

- [ ] **Step 1: Delete from `PrintStatusFocus.kt`:** `PrintStatusFocus` (the progress-ring focus, ~lines 65-193) and `TerminalFocus` (~lines 270-324). Keep `HomeFocus`, `GlanceRow`, `glanceLabel`, `selectGlanceSensor`, and any formatter helpers still referenced. Remove imports that are now unused (e.g. `ProgressRing`, `CircleShape`, `FocusHeroText`, `aspectRatio`, `offset`, `AsyncImage`/`ImageRequest` if only the deleted code used them).

- [ ] **Step 2: Delete from `PrintStatusField.kt`:** `PrintStatusActiveField`, `PrintStatusTerminalField`, `ShortcutRow`, `BabystepRow`, the StatGrid composable, `TerminalStatsList`, `TerminalErrorLines`, and the per-state foot-bar renderer (`PrintStatusFootBar` and the `PrintStatusControl`-driven button row). Keep `HomeField`. Remove now-unused imports (`basicMarquee`, `ImmutableList`, `Severity`/`SeverityToast` only if `HomeField` no longer uses them — it DOES use `SeverityToast` for `failureText`, so keep those).

- [ ] **Step 3: Build to verify it compiles.**

Run: `timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD FAILS only in the test/preview sourceset references resolved in Tasks 6-7, OR succeeds for `:app:assembleDebug` (which excludes unit tests). If `assembleDebug` fails because `PrintStatusPreviews.kt` (a `main`-sourceset preview file) references deleted symbols, that's expected — proceed to Task 7's preview rewrite BEFORE relying on a green `assembleDebug`. To keep this task self-contained, do the minimal preview fix inline here if the build blocks: temporarily comment out the broken preview bodies, with a `// rewritten in Task 7` marker. Prefer leaving Task 7 to do it cleanly if `assembleDebug` still succeeds (previews are `@Preview`-only and may not block the APK build depending on usage).

> NOTE for the executor: `PrintStatusPreviews.kt` lives in `main`, so deleting symbols it references WILL break `assembleDebug`. Recommended: **fold Task 7's preview rewrite into this task's commit** if the build is red, so every commit builds. The plan keeps them separate for reviewability, but a green build at each commit takes precedence.

- [ ] **Step 4: Commit** (only once `assembleDebug` is green — combine with Task 7 if needed).

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
git commit -m "refactor(printstatus): delete orphaned per-state Focus/Field composables"
```

---

## Task 6: Gut the per-state models + simplify the container

**Files:**
- Delete: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusMode.kt`
- Delete: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusUiModel.kt`
- Delete: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusControlModel.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt`

- [ ] **Step 1: Strip per-state state + logic from the container** (`PrintStatusScreen` composable body, lines 165-313):
  - Remove: `showCancelGuard`, `pendingAction`, `babystepStep` state vars (lines 165-171, minus `showPresetSelector` and `failureText` which stay).
  - Remove: `mode`, `babystepShown`, `ui`, `restartFilename` derivations (lines 173-186).
  - Remove: `runAction(...)` (lines 247-281) and the `clearPrintStatusPendingAction` `LaunchedEffect` (lines 242-244). Keep the dispatcher-failure `LaunchedEffect` (lines 221-235) but drop the `pendingAction = clearPendingOnDispatchFailure(...)` line (replace its body with just `failureText = event.message`).
  - Remove the `showCancelGuard` `ConfirmGuard` block (lines 319-333).
  - Keep: `runPreheat()` (lines 190-219), `showPresetSelector` + its `PresetSelector` block (lines 337-351), `failureText` + its 4s-clear effect (lines 236-241), `idleActions`, the active-spool card derivations, `spoolSwatches`.
  - Update the `PrintStatusContent(...)` call (lines 290-313): remove the now-deleted args — `mode`, `ui`, `babystepShown`, `spoolSwatches` (only if unused by HomeField — it is unused now), `babystepStep`, `pendingActionIsNull`, `hasBookmarkedMacros`, `onRunAction`, `onBabystep*`, `onCycleBabystepStep`. Keep: `state`, `metadata`, `httpBase`, `printerName`, `isMultiPrinter`, `errorLines` (or drop if unused), `idleActions`, `spoolmanPresent`, `activeSpoolCardState`, `failureText`, `onEmergencyStop`, `onNavigate`, `onPreheat`.

- [ ] **Step 2: Trim the `PrintStatusContent` signature** to only the params it still consumes after Task 4's collapse: `state, metadata, httpBase, printerName, isMultiPrinter, idleActions, spoolmanPresent, activeSpoolCardState, failureText, onEmergencyStop, onNavigate, onPreheat, modifier`. Delete the `mode` param and all deleted-feature params. (`metadata`/`httpBase`/`errorLines` are no longer rendered by the single path — delete them too unless retained for the preview overload; prefer deleting and simplifying the preview overload to match.)

- [ ] **Step 3: Delete the three model files** and their now-dead helpers:

```bash
git rm app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusMode.kt \
       app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusUiModel.kt \
       app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusControlModel.kt
```

This removes `classifyPrintStatus`, `PrintStatusMode`/`TerminalKind`, `babystepVisible`, `nextBabystepStep`, `uiModel`/`PrintStatusUiModel`/`LauncherDest` derivation, `derivePrintStatusControls`/`terminalControls`/`PrintStatusControl`/`PrintStatusControlAction`/`PrintStatusPendingAction`/`clearPrintStatusPendingAction`/`clearPendingOnDispatchFailure`. If any of these symbols are referenced elsewhere in `main` (grep first — see Step 4), move the still-needed ones into `PrintStatusScreen.kt` instead of deleting; expectation is none survive.

- [ ] **Step 4: Grep for stragglers in `main`.**

Run:
```bash
cd /mnt/e/claude/personal/github/dinghy-display && grep -rn "classifyPrintStatus\|PrintStatusMode\|TerminalKind\|uiModel\|derivePrintStatusControls\|PrintStatusPendingAction\|PrintStatusControlAction\|babystepVisible\|nextBabystepStep\|clearPendingOnDispatchFailure\|clearPrintStatusPendingAction" app/src/main/java
```
Expected: no results in `main` (all references gone). Fix any that remain.

- [ ] **Step 5: Build to verify `main` compiles.**

Run: `timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL (test sourceset still references deleted symbols — fixed in Task 7).

- [ ] **Step 6: Commit.**

```bash
git add -A app/src/main/java/works/mees/dinghy/ui/printstatus/
git commit -m "refactor(printstatus): delete per-state mode/uimodel/control logic; simplify container"
```

---

## Task 7: Prune & rewrite dependent tests and previews

**Files:**
- Delete: `app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusModeTest.kt`
- Delete: `app/src/test/java/works/mees/dinghy/preview/SampleFixturesTest.kt`
- Delete: `app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusControlModelTest.kt`
- Delete: `app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusUiModelTest.kt`
- Modify: `app/src/main/java/works/mees/dinghy/preview/PrintStatusPreviews.kt`
- Modify: `app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt` (the `forMode`/`printStatusModes` helpers)

- [ ] **Step 1: Delete the four obsolete test files** (their subjects no longer exist):

```bash
git rm app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusModeTest.kt \
       app/src/test/java/works/mees/dinghy/preview/SampleFixturesTest.kt \
       app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusControlModelTest.kt \
       app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusUiModelTest.kt
```

(Per the spec, per-state foot controls / pending-action machinery are deliberately removed, not relocated — so these tests have no new home. The behavior returns, with tests, during the per-state build-up.)

- [ ] **Step 2: Rewrite `SampleFixtures` mode helpers.** Replace the `PrintStatusMode`-keyed `forMode(mode: PrintStatusMode)` / `printStatusModes` with a `PrintState`-keyed fixture set. In `SampleFixtures.kt`:

```kotlin
    /** One representative PrinterState per print-job state, for the home-screen preview matrix. */
    val printStates: List<PrintState> = listOf(
        PrintState.Standby, PrintState.Printing, PrintState.Paused,
        PrintState.Complete, PrintState.Cancelled, PrintState.Error,
    )

    fun forState(s: PrintState): PrinterState = base().copy(
        printState = s,
        printFilename = if (s == PrintState.Printing || s == PrintState.Paused) "benchy.gcode" else "",
        klippyState = KlippyState.Ready,
    )
```

Use the file's existing `base()`/builder idiom (match its actual helper name and required fields — read the file first). Add a Klipper-fault fixture too:

```kotlin
    fun klippyShutdown(): PrinterState = base().copy(klippyState = KlippyState.Shutdown)
```

- [ ] **Step 3: Rewrite `PrintStatusPreviews.kt`** to iterate `PrintState` (not `PrintStatusMode`). Replace `PrintStatusModeProvider` with a `PrintState` parameter provider and update the matrix preview to call `PrintStatusScreen(state = SampleFixtures.forState(s), ...)`. Preserve the existing 6-theme matrix, `fs=L` overflow, and RTL spot-check patterns. Add one preview using `SampleFixtures.klippyShutdown()` to exercise the two-axis title.

- [ ] **Step 4: Grep the whole repo for any remaining references** to deleted symbols:

Run:
```bash
cd /mnt/e/claude/personal/github/dinghy-display && grep -rn "PrintStatusMode\|classifyPrintStatus\|forMode\|printStatusModes\|derivePrintStatusControls\|uiModel\|PrintStatusPendingAction\|babystepVisible\|nextBabystepStep" app/src
```
Expected: no results anywhere. Fix any stragglers (incl. `androidTest`).

- [ ] **Step 5: Run the full unit suite + debug build.**

Run:
```bash
timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 6: Commit.**

```bash
git add -A app/src
git commit -m "test(printstatus): prune deleted-symbol tests; repoint previews to PrintState"
```

---

## Task 8: Full verification + on-device UAT

**Files:** none (verification only).

- [ ] **Step 1: Conformance + full suite.** Run the full unit suite (includes `FontConformanceTest`, `CommandCatalogDriftTest`, etc.):

```bash
timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL, all green.

- [ ] **Step 2: R8 release build** (catches shrinker/keep-rule regressions):

```bash
timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleRelease --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install on BOTH test devices** (flox = Nexus 7 `0a64b42e`, armeabi-v7a; moto = `ZY22LBDRM9`, arm64-v8a). Sign the release per `E:\Android\sign-release.bat`, then push the MATCHING ABI slice to each (verify APK mtime is AFTER the latest commit — guard against the stale-APK UAT trap).

- [ ] **Step 4: Owner UAT checklist** (Matthew drives on-device):
  - Standby: title shows the state label (or `"<name> · <state>"` with >1 profile); glance block top-start at focusHero; brand mark small bottom-end; foot = Preheat + System.
  - Start a print → home stays the same skeleton; **e-stop appears in the FocusFrame header** while Printing; Pause/Cancel are intentionally absent (interim).
  - Pause → header e-stop still present.
  - Let a print Complete / Cancel one → home still renders, title reflects the state, no crash.
  - Trigger a Klipper shutdown (e-stop or `M112` on the other device) → the printer now shows the **home with a SHUTDOWN title** (not the Splash) while Moonraker stays connected; pull the network → Splash returns (connection-fault bucket).
  - Rotate portrait↔landscape in each state.

- [ ] **Step 5: Final commit** if any UAT-driven tweaks were made, then stop for the merge decision (see Execution Handoff).

---

## Self-review notes

- **Spec coverage:** Part A collapse (Tasks 4-6), Part B layout (Task 3), routing re-route (Task 1), two-axis title (Tasks 2-3), e-stop re-wire (Task 3), capability gates retained (HomeField unchanged from the standby field), tests/previews pruned (Task 7), connection-fault bucket untouched (Task 1 leaves the connection arm intact). All success criteria map to a task.
- **Interim regression acknowledged:** Pause/Cancel/Resume/Reprint and the progress ring are gone by design during the skeleton phase (spec "Interim behavior note"); e-stop is preserved (Task 3).
- **Type consistency:** `HomeFocus`/`HomeField`/`homeStateLabelRes`/`GlanceRow(label,value,valueColor)` names are used identically across tasks. `isMultiPrinter` (Boolean, = `profileCount > 1`) and `printerName` (`String?`, from `container.activeName`) are consistent.
- **Build-green discipline:** every commit must build; Task 5/7 note the `PrintStatusPreviews.kt` (main-sourceset) coupling — fold the preview rewrite forward if a commit would otherwise be red.
