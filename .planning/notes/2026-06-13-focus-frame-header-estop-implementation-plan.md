# FocusFrame Header + Docked E-Stop — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** Give every `FocusFrame` a mandatory header (start icon + centered/marquee title) whose icon
slot morphs in place into the emergency-stop button during a print, retiring the floating e-stop
everywhere except the webcam.

**Architecture:** `FocusFrame` gains required `title`/`icon`/`uDp` params plus an e-stop seam
(`isPrinting`/`onEmergencyStop`/`onPanic`). It renders the header and **internalizes** the e-stop
button + its `ConfirmGuard` (wrapped in a `Dialog` so the guard escapes the Focus region to cover the
screen). All 20 in-app screens get a `FocusFrame` (8 retrofit, 11 wrapped, 1 restructured); icon+title
auto-resolve from each screen's nav entry point, except PrintStatus (new `mode_standby` glyph, header
always present). `FloatingEStop` is reduced to webcam-only.

**Tech Stack:** Kotlin, Jetpack Compose (`basicMarquee`, `Dialog`), JUnit host tests, the project's
`DinghyIcons` ligature registry + `verify_ligatures.py` gate. Builds Windows-side via
`E:\Android\gw.bat` (see CLAUDE.md / `[[dinghy-display-build-env]]`).

**Spec:** `.planning/notes/2026-06-13-focus-frame-header-estop-design.md` (decisions D1–D9).

---

## Conventions for this plan

- **Build:** `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"`; pipe through `tr -d '\r'`;
  exit code is authoritative. Force-rebuild before any on-device UAT (`[[dinghy-stale-apk-uat-gate]]`).
- **Host tests:** `... "E:\Android\gw.bat :app:testDebugUnitTest --tests '<FQCN>'"`.
- **Commits:** one per task (frequent). On master, worktrees off (project norm).
- **Icon law:** every header glyph is the owner-resolved value in the table below — NEVER auto-pick a
  new one (`[[dinghy-never-pick-icons-ask]]`).

## Resolved icon + title table (NO remaining ASKs)

| Screen | Group | Header icon (`DinghyIcons.`) | Title source |
|--------|-------|------------------------------|--------------|
| Temperature | A | `LauncherTemperature` | `R.string.cd_launcher_temperature` |
| Spool | A | `LauncherSpool` | `R.string.cd_launcher_spool` |
| Files | A | `LauncherFiles` | `R.string.cd_launcher_files` |
| Outputs | A | `OutputSection` | `R.string.outputs_title` |
| Calibration Hub | A | `LauncherCalibration` | `R.string.cd_launcher_calibration` |
| FineTune | A | `LauncherFineTune` | `R.string.cd_launcher_fine_tune` |
| Printers | A | `SystemRowPrinters` | `R.string.system_row_printers` |
| **PrintStatus** | A | **`PrintStatusStandby` (new, `mode_standby`)** | idle `R.string.printstatus_title` ("Print Status") / printing = job filename |
| BedMesh | B | `routineIconToken(BED_MESH)` | `routineTitleRes(BED_MESH)` |
| ProbeCalibrate | B | `routineIconToken(PROBE_CALIBRATE)` | `routineTitleRes(PROBE_CALIBRATE)` |
| ScrewsTilt | B | `routineIconToken(SCREWS_TILT)` | `routineTitleRes(SCREWS_TILT)` |
| Tilt | B | `routineIconToken(Z_TILT)` | `routineTitleRes(Z_TILT)` |
| Console | B | `LauncherConsole` | `R.string.cd_launcher_console` |
| Extrude | B | `LauncherExtrude` | `R.string.cd_launcher_extrude` |
| Macros | B | `LauncherMacros` | `R.string.cd_launcher_macros` |
| About | B | `SystemRowAbout` | `R.string.system_row_about` |
| Settings | B | `SystemRowSettings` | `R.string.system_row_settings` |
| SystemPage | B | `FootSystem` | `R.string.home_foot_system` |
| SystemInformation | B | `SysInfoTile` | `R.string.system_row_sysinfo` |
| Move | C | `LauncherMove` | `R.string.cd_launcher_move` |
| Webcam | EXEMPT | — (keeps `FloatingEStop`) | — |
| Splash | EXEMPT | — (no print state) | — |

> `routineIconToken` is already `internal`; `routineTitleRes` is `private` in `CalibrationHubScreen.kt`
> and must be widened to `internal` (Task 8) so the four sub-screens can call it.

---

## Task 1: Register the `mode_standby` glyph

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`
- Modify: `tools/verify_ligatures.py`
- Modify: `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt`

- [ ] **Step 1: Add the registry entry**

In `DinghyIcons.kt`, near the other Launcher/identity entries, add:

```kotlin
    // PrintStatus standby-home identity (header law, 2026-06-13). Owner-named "mode_standby".
    // Swapped to the e-stop while printing. Confirmed resolvable in the bundled v2.944 ttf.
    val PrintStatusStandby = DinghyIcon(IconRef.Ligature("mode_standby"), alternate = "print_status_standby")
```

- [ ] **Step 2: Add to the ligature gate's NEEDED set**

In `tools/verify_ligatures.py`, add `"mode_standby"` to the `NEEDED` set (with a brief comment, e.g.
`# Focus-header law (2026-06-13): PrintStatus standby identity`).

- [ ] **Step 3: Run the ligature gate**

Run: `python3 tools/verify_ligatures.py`
Expected: exit 0 (`mode_standby` already verified resolvable — see plan research).

- [ ] **Step 4: Extend the drift-guard test**

Open `DinghyIconsTest.kt`, find the existing assertion list of registered ligatures, and add
`PrintStatusStandby` → `"mode_standby"` following the file's established pattern (match the exact
assertion style already used for `LauncherTemperature` etc.).

- [ ] **Step 5: Run the test**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.designsystem.icons.DinghyIconsTest'"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt tools/verify_ligatures.py app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt
git commit -m "feat(icons): register mode_standby (PrintStatusStandby) for Focus header"
```

---

## Task 2: Pure header-morph helper (TDD)

The only host-testable logic in the header is *when* the icon slot shows the e-stop. Extract it as a
pure function alongside the existing `focusEdgeStroke`.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt`
- Test: `app/src/test/java/works/mees/dinghy/designsystem/components/FocusEdgeTest.kt` (same file as the
  existing pure-mapping tests, or a sibling `FocusHeaderTest.kt`).

- [ ] **Step 1: Write the failing test**

Add to `FocusEdgeTest.kt`:

```kotlin
    @Test
    fun header_shows_estop_only_when_printing_and_handler_present() {
        assertEquals(true, headerShowsEStop(isPrinting = true, onEmergencyStop = {}))
        assertEquals(false, headerShowsEStop(isPrinting = false, onEmergencyStop = {}))
        assertEquals(false, headerShowsEStop(isPrinting = true, onEmergencyStop = null))
        assertEquals(false, headerShowsEStop(isPrinting = false, onEmergencyStop = null))
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.designsystem.components.FocusEdgeTest'"`
Expected: FAIL — `headerShowsEStop` unresolved (won't compile).

- [ ] **Step 3: Implement the helper**

In `FocusFrame.kt` (top-level, next to `focusEdgeStroke`):

```kotlin
/**
 * Pure (host-testable) rule for the Focus header icon slot: the slot renders the e-stop button
 * (vs the inert identity glyph) ONLY while a print is active AND a halt handler is wired. Splash /
 * previews pass `onEmergencyStop = null` and so never show an e-stop.
 */
fun headerShowsEStop(isPrinting: Boolean, onEmergencyStop: (() -> Unit)?): Boolean =
    isPrinting && onEmergencyStop != null
```

- [ ] **Step 4: Run it to verify it passes**

Run the same command as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt app/src/test/java/works/mees/dinghy/designsystem/components/FocusEdgeTest.kt
git commit -m "feat(ui): headerShowsEStop pure rule for Focus header morph"
```

---

## Task 3: FocusFrame header + docked e-stop + internal guard

Rewrite `FocusFrame` to render the header and own the e-stop. Add the new params with **required**
`title`/`icon`/`uDp` (enforcement) — this breaks the 9 existing call sites + previews, so this task
**also** updates them to pass title/icon/uDp **but leaves `isPrinting`/`onEmergencyStop` at defaults**
(no e-stop yet, existing floats untouched → no double e-stop). Per-screen tasks then move each
screen's e-stop into the header atomically.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt`
- Modify (add title/icon/uDp only): the 9 Group-A `FocusFrame` call sites —
  `TemperatureScreen.kt`, `SpoolScreen.kt`, `FilesScreen.kt`, `OutputsScreen.kt`,
  `OutputFocusControl.kt`, `CalibrationHubScreen.kt`, `FineTuneScreen.kt`, `PrintStatusFocus.kt`,
  `PrintersScreen.kt`
- Modify (preview args): `preview/DesignKitComponentPreviews.kt`, `preview/FilesPreviews.kt`,
  `preview/OutputsPreviews.kt`, `preview/PrintersPreviews.kt`, `preview/SampleFixtures.kt`,
  `ui/printstatus/PrintStatusPreviews.kt`, `designsystem/layout/ListBlock.kt` (if it previews FocusFrame)

- [ ] **Step 1: Rewrite `FocusFrame` + add `FocusHeader`**

Replace the `FocusFrame` composable in `FocusFrame.kt` with:

```kotlin
@Composable
fun FocusFrame(
    title: String,
    icon: DinghyIcon,
    uDp: Dp,
    modifier: Modifier = Modifier,
    edge: FocusEdge = FocusEdge.Neutral,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    onPanic: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val stroke = focusEdgeStroke(edge, outline = t.outline)
    Column(
        modifier = modifier
            .padding(horizontal = ListFrameInset) // outer region-edge frame (matches the Field)
            .clip(shape)
            .then(
                if (stroke != null) Modifier.border(BorderStroke(stroke.widthDp.dp, stroke.color), shape)
                else Modifier, // FocusEdge.Progress draws its own perimeter bar (deferred)
            )
            .background(t.surface),
    ) {
        FocusHeader(
            title = title,
            icon = icon,
            uDp = uDp,
            isPrinting = isPrinting,
            onEmergencyStop = onEmergencyStop,
            onPanic = onPanic,
        )
        // Content fills the space below the header; FocusInset is the inner content inset.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(FocusInset),
            content = content,
        )
    }
}

/**
 * The mandatory Focus header (Focus-header law, 2026-06-13): a 1U bar with a `start` icon slot and a
 * centered title. The icon slot is the screen's inert identity glyph normally; while a print is active
 * ([headerShowsEStop]) it morphs in place into the emergency-stop button (no overlay, no new element).
 * Title is centered across the full width; if it can't fit at the standard size it scrolls
 * ([basicMarquee]) — a named motion-law exception (single-line, overflow-only).
 */
@Composable
private fun FocusHeader(
    title: String,
    icon: DinghyIcon,
    uDp: Dp,
    isPrinting: Boolean,
    onEmergencyStop: (() -> Unit)?,
    onPanic: (() -> Unit)?,
) {
    val t = LocalTokens.current
    var showGuard by remember { mutableStateOf(false) }
    val slot = (uDp * 0.7f).coerceAtLeast(64.dp) // e-stop / icon size — matches the retired float
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(uDp)
            .padding(horizontal = FocusInset),
        contentAlignment = Alignment.Center,
    ) {
        // Centered title (full-width track; the start icon overlaps its left end, app-bar style).
        Text(
            text = title,
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(20f, t.fs).sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = slot) // keep the centered text clear of the start icon
                .basicMarquee(), // overflow-only scroll (motion-law exception)
        )
        // Start icon slot: e-stop while printing, else the inert identity glyph.
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            if (headerShowsEStop(isPrinting, onEmergencyStop)) {
                OutlinedControl(
                    label = "",
                    onClick = { showGuard = true },
                    onLongClick = onPanic, // long-press = instant halt, no guard (float's onHold)
                    modifier = Modifier.size(slot),
                    intent = Intent.Danger,
                    icon = DinghyIcons.StatusStop,
                    contentDescription = stringResource(R.string.cd_emergency_stop),
                )
            } else {
                DinghyIconView(
                    icon = icon,
                    tint = t.text2,
                    sizeDp = slot,
                    // Decorative identity glyph — inert, no contentDescription (decorative).
                )
            }
        }
    }

    // Internal e-stop ConfirmGuard, hoisted into a Dialog so the scrim escapes the Focus region and
    // covers the whole screen (ConfirmGuard is a fillMaxSize scrim). Replaces every screen's own
    // showEstopGuard + screen-level ConfirmGuard for the e-stop.
    if (showGuard) {
        Dialog(
            onDismissRequest = { showGuard = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ConfirmGuard(
                title = stringResource(R.string.printstatus_estop_guard_title),
                message = stringResource(R.string.printstatus_estop_guard_message),
                confirmLabel = stringResource(R.string.printstatus_estop_guard_confirm),
                cancelLabel = stringResource(R.string.common_cancel),
                onConfirm = { onEmergencyStop?.invoke(); showGuard = false },
                onCancel = { showGuard = false },
                destructive = true,
            )
        }
    }
}
```

Add the needed imports: `androidx.compose.foundation.basicMarquee`, `androidx.compose.foundation.layout.Box`,
`androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.foundation.layout.height`,
`androidx.compose.foundation.layout.size`, `androidx.compose.runtime.*` (getValue/setValue/
mutableStateOf/remember), `androidx.compose.ui.Alignment`, `androidx.compose.ui.text.font.FontWeight`,
`androidx.compose.ui.text.style.TextAlign`, `androidx.compose.ui.res.stringResource`,
`androidx.compose.ui.unit.Dp`, `androidx.compose.ui.unit.sp`, `androidx.compose.ui.window.Dialog`,
`androidx.compose.ui.window.DialogProperties`, `androidx.compose.material3.Text`,
`works.mees.dinghy.R`, `works.mees.dinghy.designsystem.ConfirmGuard`,
`works.mees.dinghy.designsystem.control.Intent`, `works.mees.dinghy.designsystem.control.OutlinedControl`,
`works.mees.dinghy.designsystem.icons.DinghyIcon`, `works.mees.dinghy.designsystem.icons.DinghyIcons`,
`works.mees.dinghy.designsystem.icons.DinghyIconView`, `works.mees.dinghy.theme.Geist`,
`works.mees.dinghy.theme.fsSp`.

- [ ] **Step 2: Update the 9 Group-A call sites (title/icon/uDp only)**

For each Group-A `FocusFrame(...)` call, add `title = stringResource(<title>)`, `icon = DinghyIcons.<icon>`,
`uDp = grid.uDp` using the table values (e.g. Temperature's two call sites both get
`title = stringResource(R.string.cd_launcher_temperature), icon = DinghyIcons.LauncherTemperature, uDp = grid.uDp`).
Do **not** add `isPrinting`/`onEmergencyStop` yet. Most screens already have `grid` from
`rememberUnitGrid`; if a call site lacks it, thread the existing `grid.uDp` already in scope.

For **PrintStatusFocus.kt**: idle FocusFrame gets `title = stringResource(R.string.printstatus_title)`,
`icon = DinghyIcons.PrintStatusStandby`; the printing-state FocusFrame gets `title = <job filename>`
(use the filename already present in the print UI model in that file — confirm the field at execution),
`icon = DinghyIcons.PrintStatusStandby` (slot will morph to e-stop in Task 4).

- [ ] **Step 3: Update preview call sites**

Add the same `title`/`icon`/`uDp` args to every `FocusFrame(...)` in the preview files. Use any
representative table value + a literal `uDp = 96.dp` for previews (no live grid). For
`SampleFixtures.kt`, if it exposes a shared FocusFrame preview helper, add the params there once.

- [ ] **Step 4: Build (whole app must compile)**

Run: `... "E:\Android\gw.bat :app:assembleDebug --no-daemon"`
Expected: BUILD SUCCESSFUL (required params now satisfied everywhere).

- [ ] **Step 5: Run host tests**

Run: `... "E:\Android\gw.bat :app:testDebugUnitTest"`
Expected: PASS (FocusEdgeTest + DinghyIconsTest green; no behavioral test regressions).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(ui): FocusFrame mandatory header (start icon + centered/marquee title) + docked e-stop seam"
```

---

## Task 4: Migrate Group-A screens — move e-stop into the header (8 screens)

For **each** Group-A screen, perform this atomic swap (one commit per screen). This wires the header
e-stop AND removes the old floating e-stop in the same commit so there is never a double e-stop.

**Per-screen recipe:**

- [ ] **Step 1:** In the screen's `FocusFrame(...)` call(s), add:
  ```kotlin
  isPrinting = <screen's existing isPrinting flag>,
  onEmergencyStop = { <screen's existing e-stop dispatch> },
  onPanic = { <same immediate e-stop dispatch> },
  ```
  (e.g. Temperature: `isPrinting = isPrinting`, `onEmergencyStop = onEmergencyStop`,
  `onPanic = onEmergencyStop` — its `onEmergencyStop` lambda already dispatches `CommandRegistry.emergencyStop`.)
- [ ] **Step 2:** Delete the screen's `FloatingEStop(...)` Box-sibling (if present).
- [ ] **Step 3:** Delete the screen's e-stop `var showEstopGuard` state and the screen-level
  `ConfirmGuard` that was gated on it (now owned by `FocusFrame`). Leave any *non*-e-stop ConfirmGuards
  (e.g. delete-confirm) untouched.
- [ ] **Step 4:** If the Focus content was wrapped in a `Box(fillMaxSize)` *solely* to host the float,
  collapse it back into the `FocusFrame` directly (optional tidy; skip if the Box has other siblings).
- [ ] **Step 5:** Build (`:app:assembleDebug`) + host tests green.
- [ ] **Step 6:** Commit: `refactor(ui): <screen> e-stop docks into FocusFrame header`.

**Per-screen specifics:**

| Screen / file | `isPrinting` source | e-stop dispatch | Float/guard to delete |
|---------------|---------------------|-----------------|------------------------|
| Temperature (`TemperatureScreen.kt`) | `isPrinting` param (already in `TemperatureContent`) | `onEmergencyStop` (existing) | `FloatingEStop` (l.510), `showEstopGuard` + `ConfirmGuard` (l.405/654) |
| Spool (`SpoolScreen.kt`) | existing print-state flag (confirm in file) | existing emergencyStop dispatch | `FloatingEStop` + e-stop `ConfirmGuard` (SpoolScreen is the canonical §4 pattern) |
| Files (`FilesScreen.kt`) | existing print-state flag | existing dispatch | `FloatingEStop` + e-stop guard if present |
| Outputs (`OutputsScreen.kt` / `OutputFocusControl.kt`) | existing flag | existing dispatch | `FloatingEStop` + e-stop guard |
| Calibration Hub (`CalibrationHubScreen.kt`) | existing flag (add `container.printerState` collect if absent) | `CommandRegistry.emergencyStop` dispatch | float/guard if present; else just adds the header e-stop |
| FineTune (`FineTuneScreen.kt`) | existing flag | existing dispatch | `FloatingEStop` + e-stop guard |
| Printers (`PrintersScreen.kt`) | add `isPrinting` from `container.printerState` if absent | `CommandRegistry.emergencyStop` dispatch | likely none — header gains the e-stop |
| PrintStatus (`PrintStatusFocus.kt`) | **special — Task 5** | — | — |

> At execution, confirm each screen's exact `isPrinting` expression and e-stop lambda by reading the
> file; the table names the known ones. Several screens compute `isPrinting` exactly as Temperature
> does: `printerState.printState == PrintState.Printing || == PrintState.Paused`.

---

## Task 5: PrintStatus header (special — always-present, idle↔printing title)

PrintStatus is the morphing standby/print root. The header is **always present** (D-spec); idle shows
`PrintStatusStandby` + "Print Status", printing shows the filename + the docked e-stop.

**Files:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt` (+ `PrintStatusScreen.kt`
if the e-stop/guard currently lives at screen level).

- [ ] **Step 1:** Read `PrintStatusFocus.kt` + `PrintStatusScreen.kt`; locate the current e-stop
  affordance and its `ConfirmGuard` (PrintStatus has bespoke e-stop handling today).
- [ ] **Step 2:** Make the idle/standby `FocusFrame` pass `title = stringResource(R.string.printstatus_title)`,
  `icon = DinghyIcons.PrintStatusStandby`, `isPrinting = false` (no e-stop idle).
- [ ] **Step 3:** Make the printing-state `FocusFrame` pass `title = <job filename from the print UI model>`,
  `icon = DinghyIcons.PrintStatusStandby`, `isPrinting = true`,
  `onEmergencyStop = { <existing e-stop dispatch> }`, `onPanic = { <same> }`.
- [ ] **Step 4:** Delete PrintStatus's bespoke e-stop button + its `ConfirmGuard`/`showEstopGuard`
  (now owned by the header). Verify no other code path still renders an e-stop here.
- [ ] **Step 5:** Build + host tests green (update any PrintStatus host tests that asserted the old
  e-stop affordance to assert the header morph / `headerShowsEStop` instead).
- [ ] **Step 6:** Commit: `refactor(ui): PrintStatus e-stop docks into always-present FocusFrame header`.

---

## Task 6: Reduce `FloatingEStop` to webcam-only

**Files:** `app/src/main/java/works/mees/dinghy/designsystem/components/FloatingEStop.kt`,
`app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt`

- [ ] **Step 1:** Confirm (grep) that after Tasks 4–5 the ONLY remaining `FloatingEStop(` call site is
  `WebcamScreen.kt`:
  Run: `grep -rn "FloatingEStop(" app/src/main/java | grep -v FloatingEStop.kt`
  Expected: only `WebcamScreen.kt` (and none in `preview/` — clean those in Task 3/4 if any remain).
- [ ] **Step 2:** Update `FloatingEStop.kt` KDoc: note it is now a **single-screen exception** (webcam
  full-bleed media), no longer the general e-stop pattern — the general pattern is the `FocusFrame`
  header dock. Keep the component otherwise unchanged.
- [ ] **Step 3:** Confirm `WebcamScreen.kt` still wires `FloatingEStop` + its own `ConfirmGuard`
  correctly (untouched).
- [ ] **Step 4:** Build + host tests green.
- [ ] **Step 5:** Commit: `refactor(ui): FloatingEStop reduced to webcam-only; e-stop law is the header dock`.

---

## Task 7: Wrap Group-B screens in a `FocusFrame` (header + content)

For each Group-B screen, put its Focus content inside a `FocusFrame` (header + e-stop). Where the
screen has **no natural hero content**, the Focus body is a **general description blurb** of what the
screen presents (D8). One commit per screen.

**Per-screen recipe:**

- [ ] **Step 1:** Ensure the screen has `grid` (`val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))`
  inside a `BoxWithConstraints`) and an `isPrinting` flag + e-stop dispatch (collect
  `container.printerState` + resolve `container.dispatcher` if not already present — mirror Temperature).
- [ ] **Step 2:** In the `ScreenScaffold` `focus = { ... }` slot, wrap the content in:
  ```kotlin
  FocusFrame(
      title = stringResource(<title>),
      icon = DinghyIcons.<icon>,
      uDp = grid.uDp,
      isPrinting = isPrinting,
      onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
      onPanic = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
      modifier = Modifier.fillMaxSize(),
  ) {
      <existing focus content, OR the blurb below>
  }
  ```
- [ ] **Step 3 (blurb screens only):** if there is no natural Focus content, the body is a centered
  description, e.g.:
  ```kotlin
  Text(
      text = stringResource(<screen>_focus_blurb),
      color = LocalTokens.current.text2,
      fontFamily = Geist,
      fontSize = fsSp(17f, t.fs).sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
  )
  ```
  Add the `<screen>_focus_blurb` string to `strings.xml` (see content column below).
- [ ] **Step 4:** Delete any existing `FloatingEStop`/e-stop guard on the screen.
- [ ] **Step 5:** Build + host tests green.
- [ ] **Step 6:** Commit: `feat(ui): <screen> gains FocusFrame header + docked e-stop`.

**Per-screen specifics:**

| Screen / file | Icon · Title (from table) | Focus body |
|---------------|---------------------------|------------|
| BedMesh (`BedMeshScreen.kt`) | `routineIconToken(BED_MESH)` · `routineTitleRes(BED_MESH)` | existing mesh readout/diagram (natural hero) |
| ProbeCalibrate (`ProbeCalibrateScreen.kt`) | `routineIconToken(PROBE_CALIBRATE)` · `routineTitleRes(PROBE_CALIBRATE)` | existing probe readout |
| ScrewsTilt (`ScrewsTiltScreen.kt`) | `routineIconToken(SCREWS_TILT)` · `routineTitleRes(SCREWS_TILT)` | existing screws readout |
| Tilt (`TiltScreen.kt`) | `routineIconToken(Z_TILT)` · `routineTitleRes(Z_TILT)` | existing tilt readout |
| Console (`ConsoleScreen.kt`) | `LauncherConsole` · `cd_launcher_console` | blurb `console_focus_blurb` = "Live printer console output." |
| Extrude (`ExtrudeScreen.kt`) | `LauncherExtrude` · `cd_launcher_extrude` | existing extrude readout if any, else blurb `extrude_focus_blurb` = "Extruder control." |
| Macros (`BookmarkedMacrosScreen.kt`) | `LauncherMacros` · `cd_launcher_macros` | blurb `macros_focus_blurb` = "Bookmarked printer macros." |
| About (`AboutScreen.kt`) | `SystemRowAbout` · `system_row_about` | existing About content is its own Focus body (it already fills the region) |
| Settings (`SettingsScreen.kt`) | `SystemRowSettings` · `system_row_settings` | existing settings content as body |
| SystemPage (`SystemPageScreen.kt`) | `FootSystem` · `home_foot_system` | SystemPage already has a "brand Focus" (AppShell comment) — wrap that as the body |
| SystemInformation (`SystemInformationScreen.kt`) | `SysInfoTile` · `system_row_sysinfo` | existing sysinfo readout as body |

> About/Settings are the "few exception" reading/scroll-form screens (spec §8) — keep their existing
> body layout inside the new `FocusFrame`; do not force a blurb where real content exists.

---

## Task 8: Widen `routineTitleRes` to `internal`

**Files:** `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt`

- [ ] **Step 1:** Change `private fun routineTitleRes(` → `internal fun routineTitleRes(`.
- [ ] **Step 2:** Build (`:app:assembleDebug`). Expected: SUCCESSFUL (the four sub-screens in Task 7
  can now call it).
- [ ] **Step 3:** Commit (fold into the first calibration sub-screen commit in Task 7, or standalone:
  `refactor(ui): expose routineTitleRes internal for calibration sub-screen headers`).

> Sequencing note: do Task 8 **before** the four calibration sub-screens in Task 7.

---

## Task 9: Restructure Move onto a `FocusFrame` (Group C)

`MoveScreen.kt` has no `ScreenScaffold`/Focus today (custom layout).

**Files:** `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt`

- [ ] **Step 1:** Read `MoveScreen.kt`; identify its top-level layout and where a Focus region fits
  (the jog-pad is the Field; the Focus is a header + position/home readout, or a blurb if none).
- [ ] **Step 2:** Introduce `ScreenScaffold` (focus + field) OR, if its custom layout must stay, add a
  `FocusFrame` header region above the jog content. Wire `title = stringResource(R.string.cd_launcher_move)`,
  `icon = DinghyIcons.LauncherMove`, `uDp = grid.uDp`, `isPrinting`, `onEmergencyStop`/`onPanic`
  (collect `container.printerState`/`dispatcher` as in Task 7 Step 1).
- [ ] **Step 3:** Focus body: the current X/Y/Z position readout if present, else blurb
  `move_focus_blurb` = "Move the print head and bed." (add the string).
- [ ] **Step 4:** Delete any existing `FloatingEStop`/e-stop guard on Move.
- [ ] **Step 5:** Build + host tests green.
- [ ] **Step 6:** Commit: `feat(ui): Move gains FocusFrame header + docked e-stop`.

---

## Task 10: Update the UI law docs (retire UAT-4, codify the header)

**Files:** `docs/ui_design/COMPONENTS.md`, `docs/ui_design/LAYOUT.md`, `docs/ui_design/CLAUDE.md`

- [ ] **Step 1:** `COMPONENTS.md §FocusFrame` — document the mandatory header (start icon + centered/
  marquee title, 1U, 0.7U icon/e-stop), the idle→printing morph, the internalized e-stop +
  `Dialog`-hosted `ConfirmGuard`, and the icon/title-from-entry-point rule (D9).
- [ ] **Step 2:** `LAYOUT.md` — **retire UAT-4** (the reserved top-left e-stop corner); replace with
  "the e-stop is docked in the Focus header start slot." Note "every screen carries a Focus header
  (always present)" and the two exemptions (Webcam keeps the float; Splash has none).
- [ ] **Step 3:** `CLAUDE.md` (+ THEMING motion note) — record the **basicMarquee motion-law
  exception** (single-line, overflow-only title scroll) alongside the LED `ColorWheel` exception.
- [ ] **Step 4:** Commit: `docs(ui): codify Focus header + docked e-stop; retire UAT-4 reserved corner`.

---

## Task 11: On-device UAT (flox + moto)

Owner-driven gate (`[[dinghy-display-ondevice-iteration]]`). **Force-rebuild first**
(`[[dinghy-stale-apk-uat-gate]]`): `... "E:\Android\gw.bat :app:assembleDebug --rerun-tasks"`, verify
APK mtime, install the matching ABI slice to BOTH devices (`[[dinghy-test-devices]]`).

- [ ] **Step 1:** Idle: every screen shows its header (icon + centered title); icon is inert; long
  titles scroll (use a long-filename print to verify the printing title marquee).
- [ ] **Step 2:** During a live print (owner starts one): the icon slot is the red e-stop on every
  non-exempt screen; tap → guard → halt; long-press → instant halt. Webcam still shows its float.
- [ ] **Step 3:** 5U floor check on flox: list-row counts on Console/Files/Settings acceptable with the
  permanent header (spec §8 watch-item).
- [ ] **Step 4:** dark↔light + S/M/L: header recolors via tokens; title scales; no raw colors.
- [ ] **Step 5:** Record results; if the 5U cost bites, open the D4-fallback (conditional header) as a
  follow-up — do not block ship on it unless the owner calls it.

---

## Self-Review (done)

- **Spec coverage:** D1 (replace float) → Tasks 4/5/6. D2 (start icon, centered title) → Task 3. D3/D5
  (webcam/splash exempt) → Task 6 + table. D4 (always present) → Tasks 5/7/9. D6 (internal guard) →
  Task 3. D7 (inert idle icon) → Task 3 (`DinghyIconView`, no onClick). D8 (blurb) → Task 7/9. D9
  (icon/title from entry) → the resolution table + Task 1 (PrintStatus glyph). Title overflow/marquee →
  Task 3 + Task 10 doc exception.
- **Placeholder scan:** per-screen `isPrinting`/dispatch expressions are named where known and flagged
  "confirm in file at execution" where the exact local symbol must be read — this is read-then-fill,
  not a vague TODO; the recipe + table give the exact shape.
- **Type consistency:** `headerShowsEStop(isPrinting, onEmergencyStop)` used identically in Task 2/3;
  `FocusFrame(title, icon, uDp, modifier, edge, isPrinting, onEmergencyStop, onPanic, content)` signature
  consistent across Tasks 3/4/5/7/9; `PrintStatusStandby`/`mode_standby` consistent Task 1↔table↔Task 5;
  `routineIconToken`/`routineTitleRes` consistent Task 7↔8.
