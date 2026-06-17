# Active-print Focus centered data block — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the active-print Focus body's top/bottom-anchored filename + layer/height with a single centered 6-line data block (filename, all-heater current temps, job time, print/estimate, Z, layers) over a uniform 50% scrim, each line shadowed in the surface color, the whole block uniformly shrink-to-fit (width AND height).

**Architecture:** All work is in `ui/printstatus/` — pure formatters in `ActivePrintFormat.kt` (host-tested) and the `ActivePrintFocus` composable in `PrintStatusFocus.kt`. No signature change to `HomeFocus` (it already passes `state`/`printMetadata`/`httpBase` into `ActivePrintFocus`), no `FocusFrame`/foot-bar/routing/networking change, no new Moonraker calls. Every field already lives on `PrinterState` (`heaters`, `printDuration`, `totalDuration`, `gcodePosition`, `currentLayer`/`totalLayer`) or `PrintMetadata` (`estimatedTime`, `objectHeight`).

**Tech Stack:** Kotlin, Jetpack Compose (`BoxWithConstraints`, `rememberTextMeasurer` for the uniform shrink, `Shadow`/`Offset` for the text shadow), Coil 3 `AsyncImage` (unchanged), JUnit for the pure formatters.

**Spec:** `docs/superpowers/specs/2026-06-16-active-print-focus-data-block-design.md`

---

## File structure

| File | Responsibility | Change |
|------|----------------|--------|
| `ui/printstatus/ActivePrintFormat.kt` | Add `formatPrintDuration`, `formatPrintVsEstimate`, `formatHeatersLine`, `formatZHeight`, `formatLayersLine`; remove the superseded combined `formatLayerHeight` (Task 2, after its caller is gone) | Modify |
| `app/src/test/.../ActivePrintFormatTest.kt` | Tests for the five new formatters; drop the `formatLayerHeight` cases (Task 2) | Modify |
| `ui/printstatus/PrintStatusFocus.kt` | Rewrite the `ActivePrintFocus` body: 50% scrim, centered 6-line `Column`, per-line surface shadow, width+height shrink-to-fit | Modify |
| `app/src/main/res/values/strings.xml` | `printstatus_job_time` = `"Job: %1$s"` | Modify |
| `preview/SampleFixtures.kt` | Enrich the Printing/Paused `forState` fixture so previews render a representative block | Modify |

**Reference facts (verified against HEAD 2026-06-16):**
- `orderedHeaterKeys(heaters)` (all heaters, canonical order `extruder`, `heater_bed`, rest) and `prettyHeaterLabel(key)` (`extruder→Extruder`, `heater_bed→Bed`, `heater_generic chamber→Chamber`) are both `internal` top-level in `ui/printstatus/HomeDigest.kt` — same package, callable from `ActivePrintFormat.kt`.
- `HeaterState(temperature, target, power, canExtrude)` — `works.mees.dinghy.state.HeaterState`; `temperature: Double`.
- `state.printDuration: Double` (extruding time), `state.totalDuration: Double` (wall clock), `state.gcodePosition?.getOrNull(2)` (current Z, offsets-stripped), `state.currentLayer`/`state.totalLayer` (`Int?`), `printMetadata?.estimatedTime`/`objectHeight`/`layerCount`/`firstLayerHeight`/`layerHeight` (all nullable).
- Type roles: `DinghyType.statValue` = `TextRole(Data/Mono, 26f, SemiBold)`; `DinghyType.screenTitle` = `TextRole(Ui/Geist, 22f, SemiBold)`. We drive SIZE via the override overload `TextRole.toTextStyle(t, sizeSp)` so the filename's "slightly bigger" comes from `dataSp * FILENAME_FACTOR`, NOT the role base (screenTitle's 22 is actually smaller than statValue's 26).
- `fsSp(baseSp, fs) = baseSp * fs` (`theme/ThemeTokens.kt`).
- Font-conformance law bans inline `fontSize=`/`fontFamily=` only — `style = role.toTextStyle(t, sizeSp).copy(shadow = …)` is allowed.
- `formatLayerHeight` is referenced ONLY by `PrintStatusFocus.kt:201` + `ActivePrintFormatTest.kt` — safe to delete once the Focus stops calling it (Task 2).
- Previews render the active-print body via `PrintStatusScreen(state = SampleFixtures.forState(PrintState.Printing))` (stateless overload, `printMetadata = null` → watermark fallback + estimate clause degrades to elapsed-only). The `forState` Printing/Paused fixture is currently bare (only `printState` + `printFilename = "benchy.gcode"`).

---

## Task 1: New pure formatters (heaters, durations, Z, layers)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/ActivePrintFormat.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/printstatus/ActivePrintFormatTest.kt`

This task ADDS the new formatters alongside the existing `formatLayerHeight` (still called by the Focus until Task 2) so the build stays green.

- [ ] **Step 1: Write the failing tests**

Append these methods inside the existing `ActivePrintFormatTest` class, and add `import org.junit.Assert.assertNull` to the file's imports and `import works.mees.dinghy.state.HeaterState`:

```kotlin
    @Test fun print_duration_brackets() {
        assertEquals("1h01m", formatPrintDuration(3661.0))
        assertEquals("1h00m", formatPrintDuration(3600.0))
        assertEquals("45m", formatPrintDuration(2700.0))
        assertEquals("1m", formatPrintDuration(60.0))
        assertEquals("59s", formatPrintDuration(59.0))
        assertEquals("30s", formatPrintDuration(30.0))
        assertEquals("0s", formatPrintDuration(0.0))
        assertEquals("0s", formatPrintDuration(-5.0))   // clamps
    }

    @Test fun print_vs_estimate() {
        assertEquals("45m / 3h20m", formatPrintVsEstimate(2700.0, 12000.0))
        assertEquals("45m", formatPrintVsEstimate(2700.0, null))   // no estimate → elapsed alone
        assertEquals("45m", formatPrintVsEstimate(2700.0, 0.0))    // zero estimate → elapsed alone
    }

    @Test fun heaters_line_all_current_only() {
        val h = mapOf(
            "extruder" to HeaterState(temperature = 229.6, target = 230.0),
            "heater_bed" to HeaterState(temperature = 75.2, target = 75.0),
        )
        assertEquals("Extruder 230 · Bed 75", formatHeatersLine(h))   // extruder first, current only, rounded
    }

    @Test fun heaters_line_cold_still_shown() {
        val h = mapOf("extruder" to HeaterState(temperature = 24.0, target = 0.0))
        assertEquals("Extruder 24", formatHeatersLine(h))             // shown even with target 0
    }

    @Test fun heaters_line_empty_is_blank() {
        assertEquals("", formatHeatersLine(emptyMap()))
    }

    @Test fun heaters_line_chamber_sorts_after_bed() {
        val h = mapOf(
            "heater_bed" to HeaterState(temperature = 60.0, target = 60.0),
            "heater_generic chamber" to HeaterState(temperature = 40.0, target = 45.0),
            "extruder" to HeaterState(temperature = 200.0, target = 200.0),
        )
        // canonical order extruder, heater_bed, then rest → Chamber last; prettyHeaterLabel strips "heater_generic ".
        assertEquals("Extruder 200 · Bed 60 · Chamber 40", formatHeatersLine(h))
    }

    @Test fun z_height_line() {
        assertEquals("1.2 / 55 mm", formatZHeight(1.2, 55.0))
        assertEquals("1.2 mm", formatZHeight(1.2, null))
        assertEquals("—", formatZHeight(null, 55.0))
    }

    @Test fun layers_line() {
        assertEquals("5 / 220 layers", formatLayersLine(5, 220))
        assertNull(formatLayersLine(null, 220))
        assertNull(formatLayersLine(5, null))
        assertNull(formatLayersLine(0, 220))    // non-positive → dropped
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.printstatus.ActivePrintFormatTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — unresolved references `formatPrintDuration`/`formatPrintVsEstimate`/`formatHeatersLine`/`formatZHeight`/`formatLayersLine`.

- [ ] **Step 3: Implement the formatters**

Append to `ActivePrintFormat.kt` (add `import works.mees.dinghy.state.HeaterState` at the top):

```kotlin
/** Compact print duration: `1h05m` (≥ 1h, minutes zero-padded), `45m` (≥ 1m), `30s` (< 1m). Negatives → 0. */
fun formatPrintDuration(seconds: Double): String {
    val s = seconds.coerceAtLeast(0.0).toInt()
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 -> "${h}h${m.toString().padStart(2, '0')}m"
        m > 0 -> "${m}m"
        else -> "${s % 60}s"
    }
}

/** `elapsed / estimate` print-time line; no/zero slicer estimate → elapsed alone. */
fun formatPrintVsEstimate(printDuration: Double, estimatedTime: Double?): String =
    if (estimatedTime != null && estimatedTime > 0.0) {
        "${formatPrintDuration(printDuration)} / ${formatPrintDuration(estimatedTime)}"
    } else {
        formatPrintDuration(printDuration)
    }

/**
 * All configured heaters (active OR cold — owner ruling 2026-06-16: users must see a cold hot end),
 * current temp only, canonical order, `·`-joined: `"Extruder 230 · Bed 75"`. Empty map → "".
 */
fun formatHeatersLine(heaters: Map<String, HeaterState>): String =
    orderedHeaterKeys(heaters).mapNotNull { key ->
        heaters[key]?.let { "${prettyHeaterLabel(key)} ${it.temperature.roundToInt()}" }
    }.joinToString(" · ")

/** Z-height line: `"1.2 / 55 mm"`, `"1.2 mm"` (no total height), `"—"` (no current Z). */
fun formatZHeight(currentZ: Double?, objectHeight: Double?): String {
    fun mm(v: Double): String {
        val r = (v * 10).roundToInt() / 10.0
        return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
    }
    return when {
        currentZ == null -> "—"
        objectHeight != null -> "${mm(currentZ)} / ${mm(objectHeight)} mm"
        else -> "${mm(currentZ)} mm"
    }
}

/** Layers line: `"5 / 220 layers"`, or null when either bound is missing/≤ 0 (caller drops the line). */
fun formatLayersLine(currentLayer: Int?, totalLayer: Int?): String? =
    if (currentLayer != null && currentLayer > 0 && totalLayer != null && totalLayer > 0) {
        "$currentLayer / $totalLayer layers"
    } else {
        null
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.printstatus.ActivePrintFormatTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS (all old + 7 new methods).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/ActivePrintFormat.kt \
        app/src/test/java/works/mees/dinghy/ui/printstatus/ActivePrintFormatTest.kt
git commit -m "feat(printstatus): formatters for heaters/durations/Z/layers data block"
```

---

## Task 2: Rewrite the `ActivePrintFocus` body

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/ActivePrintFormat.kt` (delete dead `formatLayerHeight`)
- Modify: `app/src/test/java/works/mees/dinghy/ui/printstatus/ActivePrintFormatTest.kt` (delete its `formatLayerHeight` cases)

The composable is visual — verified by compile + preview + on-device UAT, not a unit test.

- [ ] **Step 1: Add the `Job:` string**

In `app/src/main/res/values/strings.xml`, beside the other `printstatus_*` strings:

```xml
<string name="printstatus_job_time">Job: %1$s</string>
```

- [ ] **Step 2: Replace the `ActivePrintFocus` composable + KDoc**

In `PrintStatusFocus.kt`, replace the whole `ActivePrintFocus` function (its KDoc at ~line 129 through the closing brace at ~line 234) with:

```kotlin
/**
 * The active-print Focus body (Printing/Paused): the file thumbnail filling the frame (Fit, centered)
 * under a uniform 50% surface scrim, with a single CENTERED 6-line data block reading as one list over
 * the image — filename (slightly larger, Geist UI), then all-heater current temps, job time, print/
 * estimate, Z height, and layers (Geist Mono tabular). Every line carries a surface-color drop shadow
 * so it pops on busy renders. The block uniformly shrinks (width AND height) to fit the focus, floor
 * 15sp. No thumbnail (null metadata / inspection mode) → the faint brand watermark. The clockwise
 * perimeter progress stroke is the FocusFrame edge (owned by the caller).
 */
@Composable
private fun ActivePrintFocus(
    state: PrinterState,
    printMetadata: PrintMetadata?,
    httpBase: String,
) {
    val t = LocalTokens.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val thumbUrl = remember(httpBase, state.printFilename, printMetadata?.largestThumbRelPath) {
        val rel = printMetadata?.largestThumbRelPath
        if (httpBase.isNotBlank() && state.printFilename.isNotBlank() && rel != null) {
            thumbnailUrl(httpBase, state.printFilename, rel)
        } else {
            null
        }
    }

    // --- Resolve the data lines (filename is separate; the rest are the shrinking "list"). ---
    val filename = printFileBasename(state.printFilename)
    val currentZ = state.gcodePosition?.getOrNull(2)
    val totalLayer = (state.totalLayer ?: printMetadata?.layerCount)?.takeIf { it > 0 }
    val currentLayer = state.currentLayer?.takeIf { it > 0 } ?: deriveCurrentLayer(
        currentZ = currentZ,
        firstLayerHeight = printMetadata?.firstLayerHeight,
        layerHeight = printMetadata?.layerHeight,
        totalLayer = totalLayer,
    )
    val heatersLine = formatHeatersLine(state.heaters)
    val jobLine = stringResource(R.string.printstatus_job_time, formatPrintDuration(state.totalDuration))
    val printLine = formatPrintVsEstimate(state.printDuration, printMetadata?.estimatedTime)
    val zLine = formatZHeight(currentZ, printMetadata?.objectHeight)
    val layersLine = formatLayersLine(currentLayer, totalLayer)
    val dataLines: List<String> = buildList {
        if (heatersLine.isNotBlank()) add(heatersLine)
        add(jobLine)
        add(printLine)
        add(zLine)
        layersLine?.let { add(it) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Background: thumbnail (Fit, centered) or watermark fallback.
        if (thumbUrl != null && !inspection) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbUrl).build(),
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
        // Uniform ~50% scrim across the whole body so text stays legible over any render.
        Box(Modifier.fillMaxSize().background(t.surface.copy(alpha = 0.5f)))

        // --- Uniform shrink-to-fit: one scale that fits the WIDEST line to the body width AND all
        //     lines (+ gaps) to the body height. Width & height both scale ~linearly with font size,
        //     so a single base-size measure pass suffices. Filename rides FILENAME_FACTOR above data. ---
        val measurer = rememberTextMeasurer()
        val maxData = fsSp(DinghyType.statValue.baseSp, t.fs)   // 26 * fs
        val minData = fsSp(15f, t.fs)
        val nameStyleBase = DinghyType.screenTitle.toTextStyle(t, maxData * FILENAME_FACTOR)
        val dataStyleBase = DinghyType.statValue.toTextStyle(t, maxData)
        val gapPx = with(density) { BLOCK_LINE_GAP.toPx() }
        val availW = constraints.maxWidth.toFloat()
        val availH = constraints.maxHeight.toFloat()

        // Key on line LENGTHS (statValue is Mono → width is EXACTLY proportional to char count, so a
        // same-length temp tick reuses the cached fit — mirrors HomeDigest's length-keyed measure and
        // avoids re-measuring on every ~1 Hz temp update on Adreno 320). Filename is non-mono but
        // constant during a print, so include it verbatim.
        val key = filename + "|" + dataLines.joinToString("¦") { it.length.toString() } + "|$availW|$availH|${t.fs}"
        val sizeFrac = remember(key) {
            val nameLayout = measurer.measure(filename, nameStyleBase, maxLines = 1, softWrap = false)
            val dataLayouts = dataLines.map { measurer.measure(it, dataStyleBase, maxLines = 1, softWrap = false) }
            val widestPx = (listOf(nameLayout) + dataLayouts).maxOf { it.size.width }.toFloat()
            val textHPx = (nameLayout.size.height + dataLayouts.sumOf { it.size.height }).toFloat()
            // The BLOCK_LINE_GAP gaps are FIXED (don't scale with font) — subtract them from the height
            // budget FIRST (N = 1 filename + dataLines.size lines → N-1 == dataLines.size gaps), then fit
            // the text to the remainder, else a height-constrained block still clips. Width fits to 96%:
            // the measured layout undercounts the last glyph's side bearing, so full-width fitting clips
            // the final letter (established lesson from the old bottom line).
            val gapCount = dataLines.size
            val wFrac = if (widestPx > 0f && availW > 0f) (availW * 0.96f) / widestPx else 1f
            val hFrac = if (textHPx > 0f && availH > 0f) (availH - gapPx * gapCount).coerceAtLeast(0f) / textHPx else 1f
            minOf(wFrac, hFrac, 1f)
        }
        val dataSp = (maxData * sizeFrac).coerceIn(minData, maxData)
        val nameSp = (maxData * FILENAME_FACTOR * sizeFrac).coerceAtLeast(minData)
        val shadow = remember(t.surface, density) {
            Shadow(color = t.surface, offset = Offset(0f, with(density) { 2.dp.toPx() }), blurRadius = with(density) { 4.dp.toPx() })
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BLOCK_LINE_GAP),
            ) {
                Text(
                    text = filename,
                    color = t.text,
                    style = DinghyType.screenTitle.toTextStyle(t, nameSp).copy(shadow = shadow),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                dataLines.forEach { line ->
                    Text(
                        text = line,
                        color = t.text,
                        style = DinghyType.statValue.toTextStyle(t, dataSp).copy(shadow = shadow),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Filename size = data size × this (owner: "slightly bigger"). Tweakable at on-device UAT. */
private const val FILENAME_FACTOR = 1.2f

/** Vertical gap between the centered data-block lines ("reads as one list"). */
private val BLOCK_LINE_GAP = 4.dp
```

- [ ] **Step 3: Fix imports in `PrintStatusFocus.kt`**

ADD:
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
```
REMOVE (now unused — verify with a quick grep across the file before deleting each):
```kotlin
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Brush
```
(`fillMaxWidth` was only on the old top-aligned filename; the centered Column doesn't use it. Keep
`Image`, `background`, `BoxWithConstraints`, `fillMaxSize`, `size`, `Box`, `remember`, `ContentScale`,
`ColorFilter`, `TextAlign`, `rememberTextMeasurer`, `fsSp` — all still used by `HomeFocus`/`ActivePrintFocus`.
The compile in Step 5 is the final arbiter of unused imports.)

- [ ] **Step 4: Delete the superseded `formatLayerHeight` + its tests**

Now that the Focus no longer calls it: in `ActivePrintFormat.kt` delete the `formatLayerHeight` function (the KDoc + body, ~lines 9–37). In `ActivePrintFormatTest.kt` delete EVERY test method that references `formatLayerHeight` — the `layer_height_*` group (there are FIVE, including the non-positive-layer guard case). After deleting, run `grep -n formatLayerHeight app/src/test/java/works/mees/dinghy/ui/printstatus/ActivePrintFormatTest.kt` and confirm ZERO matches remain. Leave `progressPercent`, `printFileBasename`, `deriveCurrentLayer`, and all Task-1 cases.

- [ ] **Step 5: Build + run the formatter suite to verify**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileReleaseKotlin :app:testReleaseUnitTest --tests works.mees.dinghy.ui.printstatus.ActivePrintFormatTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; formatter suite green (no dangling `formatLayerHeight` reference).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt \
        app/src/main/java/works/mees/dinghy/ui/printstatus/ActivePrintFormat.kt \
        app/src/test/java/works/mees/dinghy/ui/printstatus/ActivePrintFormatTest.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(printstatus): centered data block over scrimmed thumbnail in active-print Focus"
```

---

## Task 3: Representative previews

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt`

The existing `@Nexus7Previews` already render `PrintStatusScreen(state = SampleFixtures.forState(Printing))`, so they exercise the new body automatically — but the bare fixture shows a near-empty block. Enrich the Printing/Paused fixture so the previews are representative (the estimate clause still degrades to elapsed-only because the stateless preview passes `printMetadata = null` — acceptable; full estimate is covered on-device).

- [ ] **Step 1: Enrich the Printing/Paused `forState` fixture**

Replace the `forState` body (`SampleFixtures.kt:62-66`) with (add `import works.mees.dinghy.state.HeaterState` and `import kotlinx.collections.immutable.persistentMapOf` / `persistentListOf` if not already present):

```kotlin
    fun forState(s: PrintState): PrinterState {
        val printing = s == PrintState.Printing || s == PrintState.Paused
        return PrinterState(
            printState = s,
            printFilename = if (printing) "benchy.gcode" else "",
            klippyState = KlippyState.Ready,
            progress = if (printing) 0.42 else 0.0,
            printDuration = if (printing) 2700.0 else 0.0,    // 45m
            totalDuration = if (printing) 3120.0 else 0.0,    // 52m
            currentLayer = if (printing) 5 else null,
            totalLayer = if (printing) 220 else null,
            gcodePosition = if (printing) persistentListOf(0.0, 0.0, 1.2, 0.0) else persistentListOf(),
            heaters = if (printing) {
                persistentMapOf(
                    "extruder" to HeaterState(temperature = 229.6, target = 230.0),
                    "heater_bed" to HeaterState(temperature = 75.2, target = 75.0),
                )
            } else {
                persistentMapOf()
            },
        )
    }
```

(If `PrinterState`'s `gcodePosition`/`heaters` field names or types differ from the above, match the actual ctor — `grep -n "gcodePosition\|val heaters\|val progress\|val printDuration" app/src/main/java/works/mees/dinghy/state/PrinterState.kt`.)

- [ ] **Step 2: Build to verify the previews compile**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileReleaseKotlin --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
git commit -m "test(printstatus): representative printing fixture for the data-block preview"
```

---

## Task 4: Full gates + on-device UAT

**Files:** none (verification only).

- [ ] **Step 1: Full unit suite + R8 release build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:assembleRelease --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; suite green incl. `FontConformanceTest` (no inline `fontSize=`/`fontFamily=`; the shadow rides `role.toTextStyle(t, …).copy(shadow=…)`) and `DinghyIconsTest`; both ABI-split APKs emitted.

- [ ] **Step 2: Install on BOTH test devices and UAT against a live print**

Per `[[dinghy-test-devices]]` push the matching ABI slice to flox (`armeabi-v7a`, id `0a64b42e`) and moto (`arm64-v8a`, id `ZY22LBDRM9`). Use `--rerun-tasks` and confirm the APK mtime is newer than this work before `adb install` (`[[dinghy-stale-apk-uat-gate]]`). Drive a real print on the Ender 5 (`192.168.1.120:7125`) and have the owner verify:
  1. Block is centered (both axes), reads as one list over the scrimmed thumbnail; text shadow legible on a busy render.
  2. Filename slightly larger (Geist UI); data lines uniform (Geist Mono).
  3. Heaters line shows ALL heaters current-only, even cold (`Extruder 24 · Bed 23`).
  4. `Job: 52m`, print line `45m / 3h20m` (or elapsed-only when the slice has no estimate); durations `1h05m`/`45m`/`30s`.
  5. Z `1.2 / 55 mm`; layers `5 / 220 layers`; both degrade cleanly when absent.
  6. Pause → header `PAUSED · NN%`, amber stroke, Resume button (unchanged); block stays put.
  7. Block fits in both orientations on moto (narrowest) without clipping.

- [ ] **Step 3: Owner sign-off**

Surface owner UAT verdict; tune `FILENAME_FACTOR` / `BLOCK_LINE_GAP` / scrim alpha on-device if requested (single-constant edits), rebuild, re-confirm.

---

## Codex review (2026-06-16) — EXECUTE WITH FIXES → all folded in

Real verdict (read-only, harness-tracked direct `codex exec`). No API compile-breakers found
(`TextRole.baseSp`, `toTextStyle(t, sizeSp)`, `Shadow`/`Offset`, `measure(...).size`, all
`PrinterState`/`HeaterState`/`PrintMetadata` fields incl. `gcodePosition`, persistent collections all
verified against the codebase; `SampleFixtures.forState` has no `app/src/test` callers). Fixes applied:
- **#1 [SHOW-STOPPER]** Height shrink now subtracts the fixed `BLOCK_LINE_GAP` gaps from the height
  budget BEFORE fitting the text (fixed gaps don't scale with font — was a real clip risk).
- **#2 [SHOW-STOPPER]** Restored the 96% width safety margin (`(availW * 0.96f)/widestPx`) — full-width
  fitting clips the last glyph's side bearing; this is the established lesson from the old bottom line.
- **#3 [SHOULD-FIX]** Shadow `remember` now keys on `density` too.
- **#4 [SHOULD-FIX]** Added a `heater_generic chamber` test proving rest-ordering + `Chamber` label.
- **#5 [SHOULD-FIX]** Measure key is now line-LENGTHS, not contents (Mono → length == width; reuses the
  cached fit across same-length temp ticks, sparing Adreno 320 a re-measure each ~1 Hz update).
- **Nits** Added `fillMaxWidth` to the remove-imports list; corrected "four"→"five" deleted tests.

## Self-review notes (author)

- **Spec coverage:** 50% uniform scrim (T2 Step 2) ✓; per-line surface shadow (T2) ✓; 6-line centered block in spec order — filename/heaters/job/print-estimate/Z/layers (T2 `dataLines` + filename) ✓; all-heaters current-only (T1 `formatHeatersLine` + T4.3) ✓; job=`totalDuration`, print=`printDuration/estimatedTime` (T1/T2) ✓; duration `1h05m`/`45m`/`30s` (T1) ✓; filename slightly bigger via `FILENAME_FACTOR` (T2) ✓; width+height shrink-to-fit, 15sp floor, ellipsis safety net (T2) ✓; degrade — no estimate→elapsed, missing Z/layers→clause dropped, empty heaters→line omitted (T1 + `dataLines` buildList) ✓; header/stroke/foot/Paused unchanged (architecture, untouched) ✓; new `formatPrintDuration` distinct from Files' (kept separate) ✓; build+suite+FontConformance (T4) ✓; previews render (T3) ✓.
- **Type consistency:** `formatHeatersLine(Map<String,HeaterState>)`, `formatPrintDuration(Double)`, `formatPrintVsEstimate(Double, Double?)`, `formatZHeight(Double?, Double?)`, `formatLayersLine(Int?, Int?)` — same signatures used in tests (T1) and composable (T2). `FILENAME_FACTOR`/`BLOCK_LINE_GAP` defined once in T2.
- **Owner-set defaults (UAT-tweakable):** `·` separator (not `-`), seconds only under a minute, `FILENAME_FACTOR = 1.2`, scrim `alpha = 0.5`, `BLOCK_LINE_GAP = 4.dp`.
- **Build env:** Windows-side via `E:\Android\gw.bat`; never `./gradlew` from WSL (`[[dinghy-display-build-env]]`).
- **Open visual risks for UAT:** (a) 6 lines on moto-portrait at 5U may shrink near the 15sp floor — watch legibility; (b) shadow blur cost is trivial (static text, no animation — Adreno-320 safe); (c) ellipsis on a very long filename is intended (no marquee — the block is centered/static).
