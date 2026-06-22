# Screws Tilt Adjust Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clean up the Screws Tilt Adjust calibration screen — drop the screw name from the Focus bed map (keep only the Z distance), draw the screw layout to scale without aspect distortion, and Title-Case the screw names in the Field list.

**Architecture:** All changes live in one Compose UI file, `ScrewsTiltScreen.kt`. The aspect-ratio math is extracted into a pure `internal` helper so it can be unit-tested on the JVM; the rest is Compose layout/rendering verified by build + on-device UAT. The list Title-Case reuses the existing tested `titleCase` helper.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4 (JVM unit tests).

## Global Constraints

- Edits confined to `app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt` (+ one new JVM test file). No data-model, holder, or parser changes.
- minSdk 23 floor; Adreno 320 perf floor — static rendering only, no new continuous animation.
- Builds run Windows-side: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"`. `./gradlew` does NOT work from WSL. **Always prefix piped build commands with `set -o pipefail;`** — otherwise `| tr -d '\r'` returns `tr`'s exit code and a failing Gradle build looks green. The Gradle exit code (via pipefail) is authoritative.
- Y-axis flip (printer-up → screen-down) is intentional and stays.
- Front-facing viewer assumed; no viewing-side config knob.

---

### Task 1: Extract + test the `screwBoxAspect` pure helper

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt` (add top-level helper near `shortScrewName`, ~line 414)
- Test: `app/src/test/java/works/mees/dinghy/ui/calibration/ScrewBoxAspectTest.kt` (create)

**Interfaces:**
- Produces: `internal fun screwBoxAspect(spanX: Double, spanY: Double): Float` — returns `width / height`, clamped to `0.25f..4f`. Consumed by Task 2's `BedScale`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/works/mees/dinghy/ui/calibration/ScrewBoxAspectTest.kt`:

```kotlin
package works.mees.dinghy.ui.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrewBoxAspectTest {
    @Test fun square_box_is_one() {
        assertEquals(1f, screwBoxAspect(300.0, 300.0), 0.0001f)
    }

    @Test fun wide_box_preserves_ratio() {
        assertEquals(1.5f, screwBoxAspect(300.0, 200.0), 0.0001f)
    }

    @Test fun tall_box_preserves_ratio() {
        assertEquals(0.5f, screwBoxAspect(150.0, 300.0), 0.0001f)
    }

    @Test fun clamps_extreme_wide_layout() {
        assertEquals(4f, screwBoxAspect(1000.0, 1.0), 0.0001f)
    }

    @Test fun clamps_extreme_tall_layout() {
        assertEquals(0.25f, screwBoxAspect(1.0, 1000.0), 0.0001f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
set -o pipefail; /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.calibration.ScrewBoxAspectTest --rerun-tasks --no-daemon" | tr -d '\r'
```
Expected: FAIL — compile error / unresolved reference `screwBoxAspect`.

- [ ] **Step 3: Write minimal implementation**

In `ScrewsTiltScreen.kt`, add this top-level function next to `shortScrewName` (~line 414, file scope, NOT inside a composable):

```kotlin
/**
 * Aspect ratio (width / height) of the padded screw bounding box, clamped so a
 * near-collinear screw layout can't collapse the drawn frame to a sliver. Callers pass
 * already-positive spans (bounds use `max(..., 1.0)` + padding), so no divide-by-zero.
 */
internal fun screwBoxAspect(spanX: Double, spanY: Double): Float =
    (spanX / spanY).toFloat().coerceIn(0.25f, 4f)
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
set -o pipefail; /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.calibration.ScrewBoxAspectTest --rerun-tasks --no-daemon" | tr -d '\r'
```
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt app/src/test/java/works/mees/dinghy/ui/calibration/ScrewBoxAspectTest.kt
git commit -m "feat(calibration): add clamped screwBoxAspect helper for screws-tilt bed map"
```

---

### Task 2: Focus — remove screw name, letterbox the bed map to scale

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt`
  - `ScrewsTiltFocus` (~lines 292–320)
  - `BedScale` (~lines 327–350)
  - `BoxWithPoints` (~lines 353–412)
  - Delete `shortScrewName` (~lines 414–416)
  - Add import `androidx.compose.foundation.layout.size`

**Interfaces:**
- Consumes: `screwBoxAspect(spanX, spanY)` from Task 1.

- [ ] **Step 1: Add the `size` import**

In the import block (after `androidx.compose.foundation.layout.padding`, ~line 13), add:

```kotlin
import androidx.compose.foundation.layout.size
```

- [ ] **Step 2: Simplify `ScrewsTiltFocus` (framing moves into `BedScale`)**

Replace the `ScrewsTiltFocus` KDoc **and** composable (~lines 285–320) — the old KDoc still says the focus shows "the screw name", which is no longer true. Replace from the `/**` doc block through the end of the function with:

```kotlin
/**
 * Focus = the to-scale screw map, centered in the pane (see [BedScale]). D-06 fallback:
 * when no coords are available, shows a short prompt.
 *
 * SPATIAL CARVE-OUT (D-10): this custom drawn surface is NOT converted to a list.
 */
@Composable
private fun ScrewsTiltFocus(vm: ScrewsTiltVm, modifier: Modifier) {
    val t = LocalTokens.current
    Box(modifier, contentAlignment = Alignment.Center) {
        if (vm.hasCoords) {
            BedScale(vm = vm)
        } else {
            Text(
                text = if (vm.loop.totalScrews > 0) {
                    stringResource(R.string.screws_map_unavailable)
                } else {
                    stringResource(R.string.screws_run_prompt)
                },
                color = t.text2,
                style = DinghyType.body.toTextStyle(t),
                textAlign = TextAlign.Center,
            )
        }
    }
}
```

- [ ] **Step 3: Rewrite `BedScale` to own the framed, letterboxed surface**

Replace the whole `BedScale` composable (~lines 327–350) with:

```kotlin
/**
 * A to-scale, aspect-correct screw map. The screw bounding box (with margin) is drawn
 * letterboxed into the pane — its real X:Y aspect ratio is preserved, never stretched —
 * so a 3-screw triangle stays a triangle and a wide bed looks wide. Printer Y (up) is
 * flipped to screen Y (down). The framed surface is sized to fit via [BoxWithConstraints].
 */
@Composable
private fun BedScale(vm: ScrewsTiltVm) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val coordPoints = vm.points.filter { it.x != null && it.y != null }
    if (coordPoints.isEmpty()) return

    val minX = coordPoints.minOf { it.x!! }
    val maxX = coordPoints.maxOf { it.x!! }
    val minY = coordPoints.minOf { it.y!! }
    val maxY = coordPoints.maxOf { it.y!! }
    val spanX = max(maxX - minX, 1.0)
    val spanY = max(maxY - minY, 1.0)
    val padX = spanX * 0.18
    val padY = spanY * 0.18
    val loX = minX - padX
    val hiX = maxX + padX
    val loY = minY - padY
    val hiY = maxY + padY
    val aspect = screwBoxAspect(hiX - loX, hiY - loY) // width / height, clamped

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val availW = maxWidth
        val availH = maxHeight
        // Largest w x h matching `aspect` (= w/h) that still fits the pane -> letterbox.
        val frameW: androidx.compose.ui.unit.Dp
        val frameH: androidx.compose.ui.unit.Dp
        if (availW / (availH * aspect) > 1f) {
            // pane wider than the ratio -> height-bound, margin on the sides
            frameH = availH
            frameW = availH * aspect
        } else {
            // pane taller/narrower than the ratio -> width-bound, margin top/bottom
            frameW = availW
            frameH = availW / aspect
        }
        Box(
            Modifier
                .size(width = frameW, height = frameH)
                .clip(shape)
                .border(BorderStroke(2.dp, t.outline), shape)
                .background(t.surface),
        ) {
            BoxWithPoints(coordPoints, loX, hiX, loY, hiY)
        }
    }
}
```

- [ ] **Step 4: Remove the screw name from `BoxWithPoints`**

In `BoxWithPoints`, replace the inner `Column { ... }` (the block at ~lines 389–408 that renders the glyph, the `p.name?.let { ... }` name Text, and the Z-reading Text) with this — the `p.name?.let` block is deleted, glyph + Z reading kept:

```kotlin
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DinghyIconView(icon = glyph, tint = tint, sizeDp = fsSp(56f, t.fs).dp)
                    if (turn != null) {
                        Text(
                            text = "${"%.3f".format(turn.z)} mm",
                            color = t.text3,
                            style = DinghyType.dataMeta.toTextStyle(t),
                            maxLines = 1,
                        )
                    }
                }
```

- [ ] **Step 5: Delete the now-unused `shortScrewName` helper**

Remove this function (~lines 414–416):

```kotlin
/** Trim a trailing " screw" so corner labels stay short on the bed. */
private fun shortScrewName(name: String): String =
    name.trim().removeSuffix("screw").trim().ifEmpty { name.trim() }
```

- [ ] **Step 6: Build to verify it compiles (no unused-symbol / unresolved errors)**

Run:
```bash
set -o pipefail; /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --rerun-tasks --no-daemon" | tr -d '\r'
```
Expected: BUILD SUCCESSFUL. If `fillMaxHeight`/`fillMaxWidth` imports are now unused and the build warns, leave them (warning, not error) unless the project treats warnings as errors — in that case remove the unused import lines and rebuild.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt
git commit -m "feat(calibration): screws-tilt Focus drops screw name, draws bed map to scale (letterboxed)"
```

---

### Task 3: List — Title-Case the screw names

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt`
  - `ScrewListRow` (~line 275)
  - Add import `works.mees.dinghy.ui.temperature.titleCase`

**Interfaces:**
- Consumes: `internal fun titleCase(raw: String): String` from `works.mees.dinghy.ui.temperature` (existing, already covered by `TitleCaseTest`). Lowercases then capitalizes each whitespace/underscore-separated word: `"front left screw"` → `"Front Left Screw"`.

- [ ] **Step 1: Add the import**

In the import block (alphabetically near the other `works.mees.dinghy.ui...` imports), add:

```kotlin
import works.mees.dinghy.ui.temperature.titleCase
```

- [ ] **Step 2: Title-case the name in `ScrewListRow`**

In `ScrewListRow`, change the primary-label `Text` (~line 274–281). Replace:

```kotlin
        Text(
            text = point.name ?: point.key,
```

with:

```kotlin
        Text(
            text = point.name?.let { titleCase(it) } ?: point.key,
```

(Null-name fallback `point.key`, e.g. `screw1`, stays unchanged.)

- [ ] **Step 3: Build to verify it compiles**

Run:
```bash
set -o pipefail; /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --rerun-tasks --no-daemon" | tr -d '\r'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt
git commit -m "feat(calibration): Title-Case screw names in screws-tilt list"
```

---

### Task 4: Full test pass + on-device UAT

**Files:** none (verification only).

- [ ] **Step 1: Run the calibration unit-test suite**

Run:
```bash
set -o pipefail; /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.calibration.* --tests works.mees.dinghy.ui.calibration.* --rerun-tasks --no-daemon" | tr -d '\r'
```
Expected: BUILD SUCCESSFUL, all tests pass (incl. `ScrewBoxAspectTest`, `ScrewsTiltHolderTest`, `ScrewsTiltResultTest`).

- [ ] **Step 2: Build the debug APK**

Run:
```bash
set -o pipefail; /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks --no-daemon" | tr -d '\r'
```
Expected: BUILD SUCCESSFUL. (Force-rebuild + verify APK mtime is after the last commit before installing — avoid the stale-APK UAT trap.)

- [ ] **Step 3: Install on BOTH test devices and hand to owner for UAT**

Install the matching ABI slice on flox (`0a64b42e`, armeabi-v7a) and moto (`ZY22LBDRM9`, arm64-v8a). Owner verifies on the Screws Tilt Adjust screen:
  - Focus: screw **name gone** under each glyph; **Z reading retained**; layout **not stretched** — verify on a 4-screw E5 (192.168.1.120) and, if available, a 3-screw bed (triangle stays a triangle).
  - List: screw names **Title-Cased** (`Front Left Screw`).

- [ ] **Step 4: Final commit (only if UAT surfaced fixes)**

If owner UAT required tweaks, commit them; otherwise nothing to do.

---

## Self-Review

**Spec coverage:**
- Spec change #1 (Focus drop name, keep distance) → Task 2 Steps 4–5. ✅
- Spec change #2 (aspect-correct letterboxed placement + clamp) → Task 1 (helper+clamp) + Task 2 Step 3. ✅
- Spec change #3 (Title-Case list name) → Task 3. ✅
- Out-of-scope items (data model, parser, turn text, fallback prompt, full-bed draw, viewing-side knob) → untouched. ✅

**Placeholder scan:** No TBD/TODO/"handle edge cases"; every code step shows full code. ✅

**Type consistency:** `screwBoxAspect(Double, Double): Float` defined Task 1, consumed Task 2 with `hiX - loX`, `hiY - loY` (Doubles). `titleCase(String): String` reused from temperature package, signature matches existing `TitleCaseTest`. `frameW/frameH` typed `Dp`; `.size(width=, height=)` takes `Dp`. ✅
