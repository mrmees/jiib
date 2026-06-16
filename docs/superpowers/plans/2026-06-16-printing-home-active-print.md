# Printing-phase home — active-print Focus + foot bar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build up the active-print (Printing/Paused) home from the collapsed skeleton — a thumbnail-backed Focus with a clockwise perimeter progress stroke, filename + layer/height text, a `PRINTING · NN%` header, and a Pause/Resume + Cancel foot bar.

**Architecture:** All work is in `ui/printstatus/` + one design-system edge (`FocusFrame`) + one route builder (`buildIdleActions`). The `isPrinting` branch in `HomeFocus` (which today renders the legacy temp glance) becomes an `ActivePrintFocus`; `HomeField` branches its foot bar on print state. Data (`printMetadata`, `httpBase`, dispatch lambdas) is threaded down from `PrintStatusScreen` — both flows are already exposed on `AppContainer`. No `AppShell`, networking, or new container surface.

**Tech Stack:** Kotlin, Jetpack Compose, Coil 3 (`AsyncImage`), Compose `Path`/`PathMeasure` (perimeter arc), kotlinx StateFlow, existing `CommandRegistry.printPause/printResume/printCancel`.

**Spec:** `docs/superpowers/specs/2026-06-16-printing-home-active-print-design.md`

---

## File structure

| File | Responsibility | Change |
|------|----------------|--------|
| `ui/printstatus/ActivePrintFormat.kt` | Pure formatters: `progressPercent`, `formatLayerHeight`, `printFileBasename` | **Create** |
| `ui/printstatus/PrintStatusFocus.kt` | `HomeFocus` branches to new `ActivePrintFocus`; title gets `· NN%`; glance kept for nothing post-this (verify) | Modify |
| `designsystem/components/FocusFrame.kt` | `FocusEdge.Progress` carries a color + is drawn (clockwise perimeter arc) | Modify |
| `ui/route/HomeAction.kt` | `buildIdleActions(isPrinting)` appends the System row while printing | Modify |
| `ui/printstatus/PrintStatusField.kt` | `HomeField` foot bar branches (Pause/Resume + Cancel vs Preheat + System); Cancel `ConfirmGuard` | Modify |
| `designsystem/icons/DinghyIcons.kt` + `img/material-icon-bucket.json` | Register `FootResume`("resume") + `FootCancel`("cancel"); System row reuses existing `FootSystem` | Modify |
| `ui/printstatus/PrintStatusScreen.kt` | Collect `printMetadata` + `httpBase`; build pause/resume/cancel lambdas; thread through; preview fixture metadata | Modify |
| `app/src/main/res/values/strings.xml` | `PRINTING · %` / `PAUSED · %` title, Pause/Resume/Cancel labels, "Cancel print?" copy | Modify |
| `app/src/test/.../ActivePrintFormatTest.kt` | Unit tests for the pure formatters | **Create** |
| `app/src/test/.../HomeActionTest.kt` (or existing) | System-row presence test | Modify/Create |
| `app/src/test/.../FocusFrameEdgeTest.kt` (or existing) | `focusEdgeStroke(Progress)==null` still holds | Modify/Create |

**Reference facts (verified against HEAD 2026-06-16):**
- Current Z height = `state.gcodePosition?.getOrNull(2)` (user-facing, offsets-stripped; `PrinterState.kt:74`). Total height = `printMetadata.objectHeight` (nullable).
- Layers: `state.currentLayer` / `state.totalLayer` (both `Int?`).
- Progress: `state.progress` (0.0..1.0).
- Thumbnail: `printMetadata.largestThumbRelPath` + `httpBase` → `thumbnailUrl(httpBase, state.printFilename, relPath)` (`state/PrintMetadata.kt:180`).
- `container.printMetadata: Flow<PrintMetadata?>` (`AppContainer.kt:613`), `container.httpBase: Flow<String>` (`AppContainer.kt:719`) — already exposed.
- Commands: `CommandRegistry.printPause`, `printResume`, `printCancel` (all `CommandSpec<Unit>`, availability-gated on `pause_resume`; `CommandRegistry.kt:283/292/301`). Dispatch `dispatcher?.dispatch(CommandRegistry.printPause, Unit)`.
- Intent enum: `Neutral, Accent, Warn, Danger, Go` (`control/OutlinedControl.kt:57`).
- Stroke colors: accent = `t.accent`; amber/paused = `t.heat` (the solid token `Intent.Warn` maps to).
- Type roles: filename = `DinghyType.screenTitle` (Ui 22 SemiBold); layer/height = `DinghyType.statValue` (Data/Mono 26 SemiBold). Render with `role.toTextStyle(t)`.
- Coil preview-safety: guard `AsyncImage` with `LocalInspectionMode.current` (mirror `FilesScreen.kt:481-492`), drawing the watermark fallback in inspection mode.
- `FootButtonBar(uDp) { … }` content is a `RowScope`; buttons are `OutlinedControl(label, onClick, modifier=Modifier.weight(1f), icon, intent)`.
- `ConfirmGuard(title, message, confirmLabel, onConfirm, onCancel, cancelLabel, destructive, warn)` is a `fillMaxSize` scrim — wrap in a `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` to escape the Field region (mirror the e-stop guard in `FocusFrame.kt:298-313`).

---

## Task 1: Pure active-print formatters

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/printstatus/ActivePrintFormat.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/printstatus/ActivePrintFormatTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivePrintFormatTest {
    @Test fun percent_rounds_to_int() {
        assertEquals(0, progressPercent(0.0))
        assertEquals(42, progressPercent(0.4249))
        assertEquals(43, progressPercent(0.425))
        assertEquals(100, progressPercent(1.0))
        assertEquals(100, progressPercent(1.5))   // clamps
        assertEquals(0, progressPercent(-0.2))    // clamps
    }

    @Test fun layer_height_full() {
        assertEquals("1.2/55mm · 5/220 layers", formatLayerHeight(1.2, 55.0, 5, 220))
    }

    @Test fun layer_height_no_total_height() {
        assertEquals("1.2mm · 5/220 layers", formatLayerHeight(1.2, null, 5, 220))
    }

    @Test fun layer_height_no_layers_drops_clause() {
        assertEquals("1.2/55mm", formatLayerHeight(1.2, 55.0, null, 220))
        assertEquals("1.2/55mm", formatLayerHeight(1.2, 55.0, 5, null))
    }

    @Test fun layer_height_no_z_falls_back_to_dash() {
        assertEquals("—", formatLayerHeight(null, 55.0, null, null))
        assertEquals("— · 5/220 layers", formatLayerHeight(null, 55.0, 5, 220))
    }

    @Test fun basename_strips_path_keeps_extension() {
        assertEquals("benchy.gcode", printFileBasename("prints/calib/benchy.gcode"))
        assertEquals("benchy.gcode", printFileBasename("benchy.gcode"))
        assertEquals("", printFileBasename(""))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.printstatus.ActivePrintFormatTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — unresolved references `progressPercent`/`formatLayerHeight`/`printFileBasename`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package works.mees.dinghy.ui.printstatus

import kotlin.math.roundToInt

/** Integer print-% for the header (`PRINTING · NN%`), clamped 0..100. */
fun progressPercent(progress: Double): Int =
    (progress.coerceIn(0.0, 1.0) * 100).roundToInt()

/**
 * Condensed layer/height line, e.g. `"1.2/55mm · 5/220 layers"`. Graceful degrade:
 *  - [objectHeight] null → `"1.2mm"` (current Z only)
 *  - [currentZ] null → height clause is `"—"`
 *  - layers null (either) → the layers clause is dropped entirely (never `"—/— layers"`)
 *  - everything null → `"—"`.
 */
fun formatLayerHeight(
    currentZ: Double?,
    objectHeight: Double?,
    currentLayer: Int?,
    totalLayer: Int?,
): String {
    // Round to 1 decimal, but drop a trailing ".0" so a whole number reads "55" not "55.0"
    // (Codex 2026-06-16 SHOW-STOPPER #2 — tests/spec expect "1.2/55mm").
    fun mm(v: Double): String {
        val r = (v * 10).roundToInt() / 10.0
        return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
    }
    val height = when {
        currentZ == null -> "—"
        objectHeight != null -> "${mm(currentZ)}/${mm(objectHeight)}mm"
        else -> "${mm(currentZ)}mm"
    }
    val layers = if (currentLayer != null && totalLayer != null) "$currentLayer/$totalLayer layers" else null
    return if (layers != null) "$height · $layers" else height
}

/** The print filename's basename (leading directory stripped); extension retained. */
fun printFileBasename(filename: String): String = filename.substringAfterLast('/')
```

- [ ] **Step 4: Run test to verify it passes**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.printstatus.ActivePrintFormatTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/ActivePrintFormat.kt \
        app/src/test/java/works/mees/dinghy/ui/printstatus/ActivePrintFormatTest.kt
git commit -m "feat(printstatus): pure active-print formatters (percent, layer/height, basename)"
```

---

## Task 2: `FocusEdge.Progress` carries a color + is drawn

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt`
- Test: **modify the EXISTING** `app/src/test/java/works/mees/dinghy/designsystem/components/FocusEdgeTest.kt` (Codex 2026-06-16: line 28 already calls `FocusEdge.Progress(0.5f)` — the new `color` param breaks it). Do NOT create a new test file; this one already covers Neutral/Data/Progress/headerShowsEStop.

- [ ] **Step 1: Update the existing edge test for the new `Progress(fraction, color)` shape**

In `FocusEdgeTest.kt`, change line 28 from:

```kotlin
        assertNull(focusEdgeStroke(FocusEdge.Progress(0.5f), outline = Color.Gray))
```

to:

```kotlin
        assertNull(focusEdgeStroke(FocusEdge.Progress(0.5f, Color.Red), outline = Color.Gray))
```

(`Color` is already imported in that file.) The assertion (Progress → no uniform border) is unchanged; this just feeds the new required color arg.

- [ ] **Step 2: Run to verify it FAILS to compile against the not-yet-changed source**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.designsystem.components.FocusEdgeTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — `FocusEdge.Progress` constructor (in source) still takes one arg, so the 2-arg test call won't compile until Step 3.

- [ ] **Step 3: Implement — add `color` to `Progress`, draw the perimeter arc**

In `FocusFrame.kt`, change the `Progress` variant:

```kotlin
    /**
     * Print-progress perimeter bar (the Scrubber's visual language unwrapped around the frame):
     * a [color] stroke tracing the rounded-rect perimeter CLOCKWISE FROM TOP-CENTER, arc length =
     * [fraction] (0..1). 0 → invisible; .5 → reaches 6 o'clock; .75 → 9 o'clock; 1 → closed loop.
     * Drawn specially in [FocusFrame] — NOT a uniform border. [color] is accent while printing,
     * the heat/amber token while paused (passed by the caller).
     */
    data class Progress(val fraction: Float, val color: Color) : FocusEdge
```

`focusEdgeStroke` already returns `null` for `is FocusEdge.Progress` — no change there.

Add the draw modifier on the outer `Column`. After `.background(t.surface)`, append:

```kotlin
            .then(
                if (edge is FocusEdge.Progress) Modifier.drawWithContent {
                    drawContent()
                    drawFocusProgress(
                        fraction = edge.fraction,
                        color = edge.color,
                        strokeWidthPx = PROGRESS_STROKE_DP.dp.toPx(),
                        cornerRadiusPx = t.rCard.toPx(),
                    )
                } else Modifier,
            )
```

Add the constant + the DrawScope helper at file scope (new imports listed below):

```kotlin
/** Perimeter progress-bar stroke weight — heavier than the 3dp Data edge so the bar reads as a gauge. */
private const val PROGRESS_STROKE_DP = 4f

/**
 * Draw the [FocusEdge.Progress] bar: a rounded-rect perimeter traced CLOCKWISE FROM TOP-CENTER,
 * stroked for the first [fraction] of its length. The path is inset by half the stroke so the full
 * stroke sits inside the frame's clip (never clipped to half-width on the outer edge).
 */
private fun DrawScope.drawFocusProgress(
    fraction: Float,
    color: Color,
    strokeWidthPx: Float,
    cornerRadiusPx: Float,
) {
    val f = fraction.coerceIn(0f, 1f)
    if (f <= 0f) return
    val inset = strokeWidthPx / 2f
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    // Shrink the corner radius by the same inset so the arc stays CONCENTRIC with the frame's
    // rounded corner (Codex 2026-06-16 NIT #6); clamp to a valid range.
    val r = (cornerRadiusPx - inset).coerceIn(0f, minOf(right - left, bottom - top) / 2f)
    val cx = (left + right) / 2f
    val path = Path().apply {
        moveTo(cx, top)
        lineTo(right - r, top)
        arcTo(Rect(right - 2 * r, top, right, top + 2 * r), -90f, 90f, false)         // top-right
        lineTo(right, bottom - r)
        arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)      // bottom-right
        lineTo(left + r, bottom)
        arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false)       // bottom-left
        lineTo(left, top + r)
        arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)            // top-left
        lineTo(cx, top)
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val dest = Path()
    measure.getSegment(0f, measure.length * f, dest, true)
    drawPath(dest, color, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round))
}
```

Add imports:

```kotlin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
```

- [ ] **Step 4: Run the edge test + build to verify**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.designsystem.components.FocusEdgeTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS (all 4 cases). (The arc draw itself is visual — covered by the Task 5 preview + on-device UAT.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt \
        app/src/test/java/works/mees/dinghy/designsystem/components/FocusEdgeTest.kt
git commit -m "feat(focusframe): implement FocusEdge.Progress clockwise perimeter bar"
```

---

## Task 3: `buildIdleActions(isPrinting)` appends the System row

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt`
- Test: search for an existing `buildIdleActions` test (`grep -rl buildIdleActions app/src/test`); extend it, else create `app/src/test/java/works/mees/dinghy/ui/route/HomeActionTest.kt`.

**Icon note (icon-never-invent law):** the System list row passes the SAME `DinghyIcons.FootSystem` token object ("bottom_panel_open") + the existing `R.string.home_foot_system` label — no new registry entry, so no `iconRef_isUnique` drift-test duplicate. FootSystem and the System row never co-render (foot bar when idle XOR list row when printing), so the "never twice on one screen" rule holds. Flag at owner UAT in case a distinct list glyph is wanted.

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeActionTest {
    private fun dests(list: List<HomeAction>) =
        list.filterIsInstance<HomeAction.Destination>().map { it.dest }

    @Test fun system_row_absent_when_idle() {
        val list = buildIdleActions(spoolmanPresent = false, outputsPresent = false, webcamEnabled = false, isPrinting = false)
        assertFalse(NavDest.System in dests(list))
    }

    @Test fun system_row_present_and_last_when_printing() {
        val list = buildIdleActions(spoolmanPresent = false, outputsPresent = false, webcamEnabled = false, isPrinting = true)
        assertTrue(NavDest.System in dests(list))
        assertEquals(NavDest.System, dests(list).last())  // assertEquals — Kotlin assert() is a no-op without -ea
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.route.HomeActionTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — `buildIdleActions` has no `isPrinting` parameter.

- [ ] **Step 3: Implement**

Add the parameter (default `false` so other call sites/tests keep compiling) and append the row last:

```kotlin
fun buildIdleActions(
    spoolmanPresent: Boolean,
    outputsPresent: Boolean,
    webcamEnabled: Boolean,
    isPrinting: Boolean = false,
): List<HomeAction> = buildList {
    // … existing rows unchanged …

    // While a print runs, System leaves the foot bar (replaced by Pause/Cancel) and lands here so it
    // stays reachable. Reuses the registered System glyph + label (icon-never-invent: same function).
    if (isPrinting) {
        add(HomeAction.Destination(
            dest     = NavDest.System,
            labelRes = R.string.home_foot_system,
            icon     = DinghyIcons.FootSystem,
        ))
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.route.HomeActionTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt \
        app/src/test/java/works/mees/dinghy/ui/route/HomeActionTest.kt
git commit -m "feat(home): append System idle-list row while printing"
```

---

## Task 4: Active-print Focus body (`ActivePrintFocus`)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt`

This task adds the composable and the new params to `HomeFocus`; it does NOT yet wire real data (Task 6 does the screen plumbing). Build-only verification here.

- [ ] **Step 1: Extend `HomeFocus` signature + branch to `ActivePrintFocus`**

Add params to `HomeFocus`: `printMetadata: PrintMetadata?`, `httpBase: String`. Compute the printing title suffix and pass `FocusEdge.Progress`:

```kotlin
    val t = LocalTokens.current
    val spoolRemaining = (activeSpoolCardState as? ActiveSpoolCardState.Loaded)?.spool?.remainingWeight
    val isPrinting = state.printState == PrintState.Printing || state.printState == PrintState.Paused
    val isPaused = state.printState == PrintState.Paused
    val klippyFault = state.klippyState == KlippyState.Shutdown || state.klippyState == KlippyState.Error
    val showActivePrint = isPrinting && !klippyFault

    val stateLabel = stringResource(homeStateLabelRes(state.printState, state.klippyState))
    val nameStatePart = if (isMultiPrinter && !printerName.isNullOrBlank()) "$printerName · $stateLabel" else stateLabel
    val title = if (showActivePrint) "$nameStatePart · ${progressPercent(state.progress)}%" else nameStatePart

    val edge = if (showActivePrint) {
        FocusEdge.Progress(state.progress.toFloat(), color = if (isPaused) t.heat else t.accent)
    } else FocusEdge.Neutral

    FocusFrame(
        title = title,
        icon = DinghyIcons.PrintStatusStandby,
        uDp = uDp,
        modifier = Modifier.fillMaxSize(),
        edge = edge,
        isPrinting = isPrinting,
        onEmergencyStop = onEmergencyStop,
        onPanic = onEmergencyStop,
    ) {
        if (showActivePrint) {
            ActivePrintFocus(state = state, printMetadata = printMetadata, httpBase = httpBase)
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val markSize = minOf(maxWidth, maxHeight) * 0.30f
                Image(
                    painter = painterResource(R.drawable.jiib_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(t.accent2),
                    alpha = 0.45f,
                    modifier = Modifier.size(markSize).align(Alignment.BottomEnd),
                )
                HomeDigest(
                    state = state,
                    spoolmanPresent = spoolmanPresent,
                    activeSpoolCardState = activeSpoolCardState,
                    heaterColors = heaterColors,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
    }
```

(Note: the legacy `GlanceBlock` becomes dead — `showActivePrint` replaces the old `showGlance` path. Delete `GlanceBlock` + `GlanceRow` if nothing else references them; `grep -rn "GlanceBlock\|GlanceRow" app/src` first. `selectGlanceSensor`/`glanceLabel` may still be used by the digest — leave them.)

- [ ] **Step 2: Add the `ActivePrintFocus` composable**

```kotlin
/**
 * The active-print Focus body (Printing/Paused): the file's thumbnail filling the frame (Fit,
 * centered) with top/bottom legibility scrims, the filename top-aligned (marquee on overflow), and
 * the condensed layer/height line bottom-aligned. No thumbnail (null metadata / inspection mode) →
 * the faint brand watermark. The clockwise perimeter progress stroke is the FocusFrame edge (caller).
 */
@Composable
private fun ActivePrintFocus(
    state: PrinterState,
    printMetadata: PrintMetadata?,
    httpBase: String,
) {
    val t = LocalTokens.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val inspection = androidx.compose.ui.platform.LocalInspectionMode.current
    val thumbUrl = remember(httpBase, state.printFilename, printMetadata?.largestThumbRelPath) {
        val rel = printMetadata?.largestThumbRelPath
        if (httpBase.isNotBlank() && state.printFilename.isNotBlank() && rel != null)
            thumbnailUrl(httpBase, state.printFilename, rel) else null
    }
    Box(Modifier.fillMaxSize()) {
        // Background: thumbnail (Fit, centered) or watermark fallback.
        if (thumbUrl != null && !inspection) {
            coil3.compose.AsyncImage(
                model = coil3.request.ImageRequest.Builder(context).data(thumbUrl).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.jiib_icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(t.accent2),
                alpha = 0.45f,
                modifier = Modifier.fillMaxSize(0.30f).align(Alignment.BottomEnd),
            )
        }
        // Legibility scrims behind the text (top + bottom vertical gradients of the surface color).
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to t.surface.copy(alpha = 0.72f),
                    0.22f to androidx.compose.ui.graphics.Color.Transparent,
                    0.78f to androidx.compose.ui.graphics.Color.Transparent,
                    1f to t.surface.copy(alpha = 0.72f),
                ),
            ),
        )
        // Filename — top.
        Text(
            text = printFileBasename(state.printFilename),
            color = t.text,
            style = DinghyType.screenTitle.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().basicMarquee(),
        )
        // Layer / height — bottom (Mono tabular).
        Text(
            text = formatLayerHeight(
                currentZ = state.gcodePosition?.getOrNull(2),
                objectHeight = printMetadata?.objectHeight,
                currentLayer = state.currentLayer,
                totalLayer = state.totalLayer,
            ),
            color = t.text,
            style = DinghyType.statValue.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
```

Add imports as needed: `androidx.compose.foundation.background`, `androidx.compose.foundation.basicMarquee`, `androidx.compose.foundation.layout.Box`, `androidx.compose.runtime.remember`, `works.mees.dinghy.state.PrintMetadata`. (`PrintState`, `KlippyState`, `ContentScale`, `ColorFilter` already imported.)

- [ ] **Step 3: Build to verify it compiles** (callers updated in Task 6; this will leave `HomeFocus` callers needing the new params — expect Task 6 to fix. To keep this task self-contained, give `printMetadata`/`httpBase` defaults `= null`/`= ""` on `HomeFocus` so existing callers/previews still compile.)

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileReleaseKotlin --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt
git commit -m "feat(printstatus): ActivePrintFocus — thumbnail bg, filename, layer/height, progress edge"
```

---

## Task 5: Active-print Focus preview

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/preview/PrintStatusPreviews.kt` (or `ui/printstatus/PrintStatusPreviews.kt` — use whichever holds the live `@Preview`s; `grep -rn "@Preview" app/src/main/java/works/mees/dinghy/**/PrintStatusPreviews.kt`).

- [ ] **Step 1: Add a Printing + a Paused preview with fixture metadata**

The stateless overload `PrintStatusScreen(state, …)` does not yet take metadata (Task 6 adds it). For the preview, add a `printMetadata`/`httpBase` parameter to the stateless overload in Task 6 first, OR preview `HomeFocus` directly. Simplest: add previews that call `HomeFocus` inside a `PreviewBox` with a `SampleFixtures.forState(PrintState.Printing)` state and a fixture `PrintMetadata(layerCount=220, objectHeight=55.0, estimatedTime=3600.0, largestThumbRelPath=null, filamentColors=emptyList())` (null thumb → watermark fallback path renders in inspection mode).

```kotlin
@Preview(name = "Home · Printing", widthDp = 360, heightDp = 640)
@Composable
private fun HomePrintingPreview() = PreviewBox {
    HomeFocus(
        state = SampleFixtures.forState(works.mees.dinghy.state.PrintState.Printing).copy(
            progress = 0.42, currentLayer = 5, totalLayer = 220,
            gcodePosition = kotlinx.collections.immutable.persistentListOf(0.0, 0.0, 1.2, 0.0),
            printFilename = "prints/calibration_benchy.gcode",
        ),
        printerName = null, isMultiPrinter = false, spoolmanPresent = false,
        activeSpoolCardState = ActiveSpoolCardState.Unavailable,
        heaterColors = kotlinx.collections.immutable.persistentMapOf(),
        printMetadata = works.mees.dinghy.state.PrintMetadata(220, 55.0, 3600.0, null, emptyList()),
        httpBase = "", onEmergencyStop = {}, uDp = 96.dp,
    )
}
```

Mirror it with `PrintState.Paused` for the amber/Resume variant. (`HomeFocus` is `internal` — these previews live in the same module, fine. If `HomeFocus` visibility blocks it, preview via the stateless `PrintStatusScreen(state=…)` overload after Task 6 threads metadata into it.)

- [ ] **Step 2: Build to verify**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileReleaseKotlin --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/preview/PrintStatusPreviews.kt
git commit -m "test(printstatus): Printing/Paused active-print Focus previews"
```

---

## Task 6: Foot bar branch + Cancel guard (`HomeField`)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt`

- [ ] **Step 1: Extend `HomeField` signature** (WITH DEFAULTS — Codex 2026-06-16 SHOW-STOPPER #1)

Add params **with defaults** so this task compiles standalone (the `PrintStatusContent` call site at `PrintStatusScreen.kt:332-339` is only updated in Task 7 — without defaults the Task 6 build gate fails):

```kotlin
    isPrinting: Boolean = false,
    isPaused: Boolean = false,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {},
```

(The `idleActions` list already carries the System row when printing, from Task 3 — the list rendering is unchanged. The defaults are permanent and harmless: Task 7 passes the real values from the live screen; the preview overload relies on the no-op defaults.)

- [ ] **Step 1a: Register the Resume + Cancel foot glyphs** (owner ruling 2026-06-16: Resume = `resume`, Cancel = `cancel`; Pause reuses the registered `PauseCircle`).

In `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`, beside the foot-bar glyph block (after `FootSystem`):

```kotlin
    // Active-print foot-bar glyphs (owner-confirmed 2026-06-16; icon-never-invent law). Pause reuses
    // the registered PauseCircle ("pause_circle"). "resume"/"cancel" verified on-device at UAT.
    val FootResume = DinghyIcon(IconRef.Ligature("resume"), alternate = "printstatus_foot_resume")
    val FootCancel = DinghyIcon(IconRef.Ligature("cancel"), alternate = "printstatus_foot_cancel")
```

Add both to the `DinghyIcons.all` list (so `DinghyIconsTest` covers them). Add a `"cancel"` entry to `img/material-icon-bucket.json` (curation doc — `"resume"` is already present; the unit suite does not read the file, but keep it accurate). The new IconRefs (`resume`, `cancel`) and alternates are unique → `iconRef_isUnique`/`alternate_isUnique` pass.

- [ ] **Step 2: Branch the foot bar**

Replace the single `FootButtonBar { Preheat; System }` with:

```kotlin
        var showCancelGuard by remember { mutableStateOf(false) }
        FootButtonBar(uDp = uDp) {
            if (isPrinting) {
                if (isPaused) {
                    OutlinedControl(
                        label = stringResource(R.string.printstatus_foot_resume),
                        onClick = onResume,
                        modifier = Modifier.weight(1f),
                        icon = DinghyIcons.FootResume,
                        intent = Intent.Go,
                    )
                } else {
                    OutlinedControl(
                        label = stringResource(R.string.printstatus_foot_pause),
                        onClick = onPause,
                        modifier = Modifier.weight(1f),
                        icon = DinghyIcons.PauseCircle,
                        intent = Intent.Warn,
                    )
                }
                OutlinedControl(
                    label = stringResource(R.string.printstatus_foot_cancel),
                    onClick = { showCancelGuard = true },
                    modifier = Modifier.weight(1f),
                    icon = DinghyIcons.FootCancel,
                    intent = Intent.Danger,
                )
            } else {
                OutlinedControl(
                    label = stringResource(R.string.home_foot_preheat),
                    onClick = onPreheat,
                    modifier = Modifier.weight(1f),
                    icon = DinghyIcons.FootPreheat,
                    intent = Intent.Warn,
                )
                OutlinedControl(
                    label = stringResource(R.string.home_foot_system),
                    onClick = { onNavigate(NavDest.System) },
                    modifier = Modifier.weight(1f),
                    icon = DinghyIcons.FootSystem,
                    intent = Intent.Accent,
                )
            }
        }

        if (showCancelGuard) {
            Dialog(
                onDismissRequest = { showCancelGuard = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                ConfirmGuard(
                    title = stringResource(R.string.printstatus_cancel_guard_title),
                    message = stringResource(R.string.printstatus_cancel_guard_message),
                    confirmLabel = stringResource(R.string.printstatus_cancel_guard_confirm),
                    cancelLabel = stringResource(R.string.common_cancel),
                    onConfirm = { onCancel(); showCancelGuard = false },
                    onCancel = { showCancelGuard = false },
                    destructive = true,
                )
            }
        }
```

> **Icon ruling — RESOLVED 2026-06-16 (owner):** Pause = `PauseCircle` (registered "pause_circle"), Resume = `FootResume` ("resume"), Cancel = `FootCancel` ("cancel") — registered in Step 1a above. No placeholders remain.

Imports to add: `androidx.compose.runtime.{getValue,setValue,mutableStateOf,remember}`, `androidx.compose.ui.window.{Dialog,DialogProperties}`, `works.mees.dinghy.designsystem.ConfirmGuard`, `androidx.compose.ui.res.stringResource` (already present).

- [ ] **Step 3: Build to verify** (after owner glyphs are wired)

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileReleaseKotlin --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
git commit -m "feat(printstatus): printing foot bar — Pause/Resume + Cancel (guarded)"
```

---

## Task 7: Screen plumbing — thread metadata, httpBase, dispatch lambdas

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt`

- [ ] **Step 1: Collect the two flows in the `container` overload**

```kotlin
    val printMetadata by container.printMetadata.collectAsStateWithLifecycle(initialValue = null)
    val httpBase by container.httpBase.collectAsStateWithLifecycle(initialValue = "")
```

- [ ] **Step 2: Build pause/resume/cancel lambdas + isPrinting/isPaused**

```kotlin
    val isPrinting = state.printState == PrintState.Printing || state.printState == PrintState.Paused
    val isPaused = state.printState == PrintState.Paused
    // dispatch helpers
    val onPause  = { dispatcher?.dispatch(CommandRegistry.printPause, Unit); Unit }
    val onResume = { dispatcher?.dispatch(CommandRegistry.printResume, Unit); Unit }
    val onCancel = { dispatcher?.dispatch(CommandRegistry.printCancel, Unit); Unit }
```

Pass `isPrinting` into `buildIdleActions` so the System row appears (add `isPrinting` to the `remember(...)` keys):

```kotlin
    val idleActions: List<HomeAction> = remember(spoolmanPresent, outputsPresent, webcamEnabled, isPrinting) {
        buildIdleActions(spoolmanPresent, outputsPresent, webcamEnabled, isPrinting)
    }
```

- [ ] **Step 3: Thread through `PrintStatusContent` → `HomeFocus`/`HomeField`**

Add `printMetadata`, `httpBase`, `isPrinting`, `isPaused`, `onPause`, `onResume`, `onCancel` params to `PrintStatusContent`; pass `printMetadata`/`httpBase` into `HomeFocus`, and `isPrinting`/`isPaused`/`onPause`/`onResume`/`onCancel` into `HomeField`. Update BOTH call sites (the live `container` overload and the stateless preview overload) — give the preview overload sensible defaults (`printMetadata = null`, `httpBase = ""`, `isPrinting`/`isPaused` derived from `state`, no-op dispatch lambdas) so previews still render and `SampleFixtures.forState(Printing/Paused)` exercises the new path.

- [ ] **Step 4: Full build + suite**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL, all tests pass (watch `PrintStatus*Test` fixtures that may reference the changed overloads — fix signatures).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
git commit -m "feat(printstatus): wire printMetadata/httpBase + pause/resume/cancel into the home"
```

---

## Task 8: Strings + final gates

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add strings**

```xml
<string name="printstatus_foot_pause">Pause</string>
<string name="printstatus_foot_resume">Resume</string>
<string name="printstatus_foot_cancel">Cancel</string>
<string name="printstatus_cancel_guard_title">Cancel print?</string>
<string name="printstatus_cancel_guard_message">This stops the current print. It can’t be undone.</string>
<string name="printstatus_cancel_guard_confirm">Cancel print</string>
```

(`common_cancel` and `home_foot_system`/`home_foot_preheat` already exist — verify with `grep -n "common_cancel\|home_foot_system" app/src/main/res/values/strings.xml`.)

- [ ] **Step 2: Full release build (R8) + unit suite**

Run:
```
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:assembleRelease --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL; suite green; APK emitted (both ABI splits). Also confirm `FontConformanceTest` passes (no inline `fontSize=`/`fontFamily=` — all text uses `role.toTextStyle(t)`).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(printstatus): active-print strings (pause/resume/cancel + cancel guard)"
```

---

## Codex review (2026-06-16) — EXECUTE WITH FIXES → all folded in

Real verdict obtained (session cwd confirmed = this repo; first 5-min run was reaped before a verdict — re-run scoped/9-min). Fixes applied to this plan:
- **#1 [SHOW-STOPPER]** Task 6 build gap — new `HomeField` params now have defaults so it compiles before Task 7 wires real values.
- **#2 [SHOW-STOPPER]** `formatLayerHeight` `mm()` now drops a trailing `.0` (`55.0` → `"55"`) to match the `"1.2/55mm"` test/spec.
- **#3 [SHOW-STOPPER]** Task 2 now MODIFIES the existing `FocusEdgeTest.kt:28` (`Progress(0.5f)` → `Progress(0.5f, Color.Red)`) instead of creating a new test file.
- **#4 [SHOULD-FIX]** Task 3 uses `assertEquals` (Kotlin `assert()` is a no-op without `-ea`).
- **#6 [NIT]** progress-arc corner radius shrinks by the inset to stay concentric.
- **#5/#7** confirmations only (defaults are load-bearing at AppShell + previews — kept; dispatch lambdas are sound) — no change.

## Self-review notes (author)

- **Spec coverage:** thumbnail bg + fallback (T4), progress edge clockwise-from-top (T2), filename top (T4), layer/height bottom + degrade (T1/T4), `PRINTING/PAUSED · NN%` title (T4), e-stop icon (already wired, unchanged), Pause/Resume + Cancel foot + Cancel guard (T6), System into list (T3), Paused deltas — header label via `homeStateLabelRes` + amber edge + Resume button (T4/T6), plumbing (T7), strings (T8). ✓
- **Owner gates:** (1) foot glyphs RESOLVED 2026-06-16 — Pause=`PauseCircle`, Resume=`FootResume`("resume"), Cancel=`FootCancel`("cancel"), registered in Task 6 Step 1a; (2) System list-row reuses the SAME `DinghyIcons.FootSystem` token object (no new registry entry; never co-renders with the foot-bar System so the `iconRef_isUnique` drift test is satisfied); (3) title casing ("Printing" vs "PRINTING") + `·` vs `-` separator is a trivial string/format tweak deferred to UAT; (4) confirm `resume`/`cancel` ligatures render in the bundled v2.944 ttf at on-device UAT (not unit-checkable).
- **On-device UAT:** push the matching APK slice to BOTH flox (armeabi-v7a) and moto (arm64-v8a) per `[[dinghy-test-devices]]`; verify with a live print on the Ender 5 (192.168.1.120:7125) — progress stroke growth, thumbnail, pause→amber+Resume, cancel guard. Use `--rerun-tasks` and confirm APK mtime post-fix before install (`[[dinghy-stale-apk-uat-gate]]`).
- **Build env:** Windows-side via `E:\Android\gw.bat`; never `./gradlew` from WSL (`[[dinghy-display-build-env]]`).
```
