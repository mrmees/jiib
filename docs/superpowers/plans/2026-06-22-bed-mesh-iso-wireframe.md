# Bed Mesh Iso Wireframe — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a static isometric 3D **wireframe** as the 3rd Bed Mesh View Type, rendered by `BedMeshHeatmapView`, with height + color both driven by the existing Color Scale mode.

**Architecture:** A pure, Android-free `IsoProjection` does all the math (host-tested). `BedMeshHeatmapView` gains a `ViewMode.ISO_WIREFRAME` branch that builds a per-vertex projection cache (rebuilt only when mesh/scale/size change) and draws a height-colored line lattice. The enum/prefs/icon/screen wiring exposes it as a selectable View Type. No new screen, no new control, no new data source.

**Tech Stack:** Kotlin, Android classic `View`/Canvas (per ADR-0001 the mesh render is a Views surface), Jetpack Compose host (`AndroidView`), JUnit4 host tests, DataStore prefs, Material Symbols font ligatures.

**Spec:** `docs/superpowers/specs/2026-06-22-bed-mesh-iso-wireframe-design.md`

## Global Constraints

- **minSdk 23 floor; perf floor = Nexus 7 2013 (Adreno 320, armeabi-v7a, 2GB).** No per-frame allocation in `onDraw`; the iso view is STATIC (repaints only on mesh/theme/scale/size change).
- **Icon law:** never invent a glyph. The only new glyph is `ssid_chart` (owner-chosen, verified resolvable). A new `DinghyIcon` val MUST also be added to `DinghyIcons.all`.
- **Build is Windows-side:** `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"`. ONE Gradle build at a time; use `-Pkotlin.incremental=false --no-daemon`. Unit tests: `:app:testDebugUnitTest --tests <FQCN>`.
- **Front-at-bottom convention:** the front bed edge (row 0 = min Y) renders at the BOTTOM of the box, matching the overhead heatmap.
- **Height is frac-relative by design** (tracks the color): in RELATIVE mode the midplane is the mesh's own mid, not physical z=0.
- All UI type/color routes through theme tokens + `DinghyType` roles (no inline `fontSize=`/raw hex — `FontConformanceTest` build-gates this).

---

### Task 1: Pure isometric projection (`IsoProjection`)

The Android-free math core. Fully host-testable; everything else builds on it.

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/render/IsoProjection.kt`
- Test: `app/src/test/java/works/mees/dinghy/render/IsoProjectionTest.kt`

**Interfaces:**
- Consumes: `BedMeshHeatmapView.deviationToRamp(deviation, loZ, hiZ): Double` (existing companion fn, pure, host-loadable).
- Produces:
  - `IsoProjection.project(z: List<List<Double>>, minX, maxX, minY, maxY, loZ, hiZ, heightAmp: Double): IsoProjection.Projected` where `Projected(isoX: DoubleArray, isoY: DoubleArray, frac: DoubleArray, rows: Int, cols: Int)` — normalized (pre-fit), row-major index `j*cols + i`.
  - `IsoProjection.fitTransform(isoX: DoubleArray, isoY: DoubleArray, w: Double, h: Double, inset: Double): IsoProjection.Fit` where `Fit(scale: Double, dx: Double, dy: Double)`. Screen coord = `iso * scale + d`.
  - `IsoProjection.EPS: Double`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/works/mees/dinghy/render/IsoProjectionTest.kt`:

```kotlin
package works.mees.dinghy.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsoProjectionTest {
    private val amp = 0.6

    /** front bed edge (row 0 = minY) must land BELOW the rear edge (larger canvas-Y). */
    @Test
    fun front_edge_projects_below_rear_edge() {
        // flat mesh → all frac 0.5 → pure geometry. 2 rows × 2 cols over a square bed.
        val z = listOf(listOf(0.0, 0.0), listOf(0.0, 0.0))
        val p = IsoProjection.project(z, 0.0, 200.0, 0.0, 200.0, -1.0, 1.0, amp)
        // column i=0: front vertex (j=0) vs rear vertex (j=1)
        val front = p.isoY[0 * p.cols + 0]
        val rear = p.isoY[1 * p.cols + 0]
        assertTrue("front (minY) must be lower on screen (greater isoY) than rear: front=$front rear=$rear",
            front > rear)
    }

    /** non-square bed must NOT project the same as a square bed (aspect is honored, not squared off). */
    @Test
    fun aspect_is_honored() {
        val z = listOf(listOf(0.0, 0.0), listOf(0.0, 0.0))
        val wide = IsoProjection.project(z, 0.0, 300.0, 0.0, 150.0, -1.0, 1.0, amp)
        val square = IsoProjection.project(z, 0.0, 150.0, 0.0, 150.0, -1.0, 1.0, amp)
        var differs = false
        for (k in wide.isoX.indices) if (kotlin.math.abs(wide.isoX[k] - square.isoX[k]) > 1e-9) differs = true
        assertTrue("non-square bed projection must differ from square", differs)
    }

    /** frac==0.5 (midplane) contributes ZERO height; a peak rises (smaller isoY). */
    @Test
    fun midplane_is_zero_height_and_peaks_rise() {
        val flat = IsoProjection.project(
            listOf(listOf(0.0, 0.0), listOf(0.0, 0.0)), 0.0, 200.0, 0.0, 200.0, -1.0, 1.0, amp)
        // same grid, but vertex (j=1,i=1) is a full peak (z=hiZ → frac 1.0).
        val peaked = IsoProjection.project(
            listOf(listOf(0.0, 0.0), listOf(0.0, 1.0)), 0.0, 200.0, 0.0, 200.0, -1.0, 1.0, amp)
        val idx = 1 * flat.cols + 1
        // midplane vertices identical between the two projections everywhere EXCEPT the peak.
        assertEquals("flat vertex 0 unchanged", flat.isoY[0], peaked.isoY[0], 1e-9)
        assertTrue("peak rises: peaked isoY < flat isoY at peak vertex",
            peaked.isoY[idx] < flat.isoY[idx])
    }

    /** degenerate grids and bboxes must not divide by zero / produce NaN. */
    @Test
    fun degenerate_inputs_are_safe() {
        val single = IsoProjection.project(listOf(listOf(0.0)), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, amp)
        assertTrue(single.isoX.all { it.isFinite() } && single.isoY.all { it.isFinite() })
        val fit = IsoProjection.fitTransform(single.isoX, single.isoY, 100.0, 100.0, 4.0)
        assertTrue("scale finite", fit.scale.isFinite() && fit.scale > 0.0)
        assertTrue("offsets finite", fit.dx.isFinite() && fit.dy.isFinite())
    }

    /** fitTransform centers a normal lattice inside the box and stays within it. */
    @Test
    fun fit_centers_within_box() {
        val p = IsoProjection.project(
            listOf(listOf(0.0, 0.0), listOf(0.0, 0.0)), 0.0, 200.0, 0.0, 200.0, -1.0, 1.0, amp)
        val fit = IsoProjection.fitTransform(p.isoX, p.isoY, 100.0, 100.0, 4.0)
        for (k in p.isoX.indices) {
            val sx = p.isoX[k] * fit.scale + fit.dx
            val sy = p.isoY[k] * fit.scale + fit.dy
            assertTrue("x in box: $sx", sx in -0.001..100.001)
            assertTrue("y in box: $sy", sy in -0.001..100.001)
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.render.IsoProjectionTest -Pkotlin.incremental=false --no-daemon"`
Expected: FAIL — `IsoProjection` unresolved (does not compile yet).

- [ ] **Step 3: Implement `IsoProjection`**

Create `app/src/main/java/works/mees/dinghy/render/IsoProjection.kt`:

```kotlin
package works.mees.dinghy.render

/**
 * Pure, Android-free isometric projection for the bed-mesh 3D wireframe (ViewMode.ISO_WIREFRAME).
 * Host-testable (no Canvas/Context). [BedMeshHeatmapView] calls [project] once per cache build, then
 * [fitTransform] to scale the normalized lattice into the Focus box. Front bed edge (row 0 = minY)
 * maps to the BOTTOM of the box (front-at-bottom, matching the overhead heatmap). Height and color
 * share one mapping ([BedMeshHeatmapView.deviationToRamp]) so they always agree.
 */
object IsoProjection {
    private const val COS = 0.8660254037844387  // cos 30°
    private const val SIN = 0.5                  // sin 30°
    const val EPS = 1e-9

    /** Normalized (pre-fit) projection: one entry per vertex, row-major index `j*cols + i`. */
    class Projected(
        val isoX: DoubleArray, val isoY: DoubleArray, val frac: DoubleArray,
        val rows: Int, val cols: Int,
    )

    /** Uniform fit: screen coord = `iso * scale + d`. */
    class Fit(val scale: Double, val dx: Double, val dy: Double)

    fun project(
        z: List<List<Double>>,
        minX: Double, maxX: Double, minY: Double, maxY: Double,
        loZ: Double, hiZ: Double, heightAmp: Double,
    ): Projected {
        val rows = z.size
        val cols = if (rows == 0) 0 else z[0].size
        val n = rows * cols
        val ix = DoubleArray(n); val iy = DoubleArray(n); val fr = DoubleArray(n)
        val cx = (minX + maxX) / 2.0
        val cy = (minY + maxY) / 2.0
        val halfSpan = (maxOf(maxX - minX, maxY - minY) / 2.0).let { if (it <= EPS) 0.5 else it }
        for (j in 0 until rows) {
            val y = if (rows == 1) cy else lerp(minY, maxY, j.toDouble() / (rows - 1))
            val gyView = (cy - y) / halfSpan   // INVERTED Y: minY → +gyView → BOTTOM of box
            val rowVals = z[j]
            for (i in 0 until cols) {
                val x = if (cols == 1) cx else lerp(minX, maxX, i.toDouble() / (cols - 1))
                val gx = (x - cx) / halfSpan
                val zv = rowVals.getOrElse(i) { loZ }
                val f = BedMeshHeatmapView.deviationToRamp(zv, loZ, hiZ)
                val hNorm = f - 0.5
                val idx = j * cols + i
                ix[idx] = (gx - gyView) * COS
                iy[idx] = (gx + gyView) * SIN - hNorm * heightAmp
                fr[idx] = f
            }
        }
        return Projected(ix, iy, fr, rows, cols)
    }

    fun fitTransform(isoX: DoubleArray, isoY: DoubleArray, w: Double, h: Double, inset: Double): Fit {
        if (isoX.isEmpty()) return Fit(1.0, w / 2.0, h / 2.0)
        var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
        for (v in isoX) { if (v < minX) minX = v; if (v > maxX) maxX = v }
        for (v in isoY) { if (v < minY) minY = v; if (v > maxY) maxY = v }
        val availW = (w - 2 * inset).coerceAtLeast(1.0)
        val availH = (h - 2 * inset).coerceAtLeast(1.0)
        val bboxW = maxX - minX
        val bboxH = maxY - minY
        val sx = if (bboxW <= EPS) Double.POSITIVE_INFINITY else availW / bboxW
        val sy = if (bboxH <= EPS) Double.POSITIVE_INFINITY else availH / bboxH
        val scale = listOf(sx, sy).filter { it.isFinite() }.minOrNull() ?: 1.0
        val cxBox = (minX + maxX) / 2.0
        val cyBox = (minY + maxY) / 2.0
        return Fit(scale, w / 2.0 - cxBox * scale, h / 2.0 - cyBox * scale)
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.render.IsoProjectionTest -Pkotlin.incremental=false --no-daemon"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/render/IsoProjection.kt app/src/test/java/works/mees/dinghy/render/IsoProjectionTest.kt
git commit -m "feat(calibration): pure isometric projection for bed-mesh 3D wireframe"
```

---

### Task 2: Add both enum values + screen mapping + robust prefs round-trip

**Sequencing (Codex BLOCKER):** adding `BedMeshViewType.ISO` makes `BedMeshScreen`'s `when (vm.viewType)`
non-exhaustive, which fails to compile the **whole module + tests**. So this task adds BOTH enum values
(`BedMeshViewType.ISO` and `BedMeshHeatmapView.ViewMode.ISO_WIREFRAME`) AND the exhaustive `when` arm in
the SAME task, keeping every task boundary green. `ISO_WIREFRAME` has no `onDraw` branch yet (added in
Task 4), so until then selecting "3D Grid" harmlessly falls through to the heatmap-fill path — it
compiles and runs. Also fixes the prefs reader so `ISO` persists (Codex: currently collapses to `HEATMAP`).

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/calibration/BedMeshModel.kt:39-40`
- Modify: `app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapView.kt:70` (ViewMode enum)
- Modify: `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt` (the `viewMode = when (vm.viewType)`)
- Modify: `app/src/main/java/works/mees/dinghy/ui/settings/BedMeshRenderPrefs.kt:29-36`
- Test: `app/src/test/java/works/mees/dinghy/ui/settings/BedMeshRenderPrefsTest.kt`

**Interfaces:**
- Produces: `BedMeshViewType.ISO`, `BedMeshHeatmapView.ViewMode.ISO_WIREFRAME`.

- [ ] **Step 1: Add the failing prefs test**

Open `app/src/test/java/works/mees/dinghy/ui/settings/BedMeshRenderPrefsTest.kt` and add a test matching
the file's existing setup (`runBlocking`, the `store()` helper, `BedMeshRenderPrefs(store())`, `.first()`;
`BedMeshViewType` is already imported):

```kotlin
    @Test fun viewType_roundTrips_allThreeValues() = runBlocking {
        val p = BedMeshRenderPrefs(store())
        for (v in BedMeshViewType.entries) {
            p.setViewType("printerA", v)
            assertEquals(v, p.viewType("printerA").first())
        }
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.settings.BedMeshRenderPrefsTest -Pkotlin.incremental=false --no-daemon"`
Expected: FAIL on the `ISO` iteration — reads back `HEATMAP`. (Compiles only after Step 3 adds the enum + the screen arm; if the run fails to compile first, do Step 3, then re-run and observe the assertion failure before the reader fix, or just confirm GREEN after the full Step 3.)

- [ ] **Step 3: Add both enums, the screen mapping, and the robust reader**

(a) In `app/src/main/java/works/mees/dinghy/calibration/BedMeshModel.kt`:

```kotlin
/** Selectable render styles for the mesh Focus: filled heatmap, colored probe points, 3D wireframe. */
enum class BedMeshViewType { HEATMAP, PROBE_POINTS, ISO }
```

(b) In `app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapView.kt` (line 70):

```kotlin
    enum class ViewMode { HEATMAP, PROBE_POINTS, ISO_WIREFRAME }
```

(c) In `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt`, make the
`viewMode = when (vm.viewType)` exhaustive (add the ISO arm):

```kotlin
                    viewMode = when (vm.viewType) {
                        BedMeshViewType.HEATMAP -> BedMeshHeatmapView.ViewMode.HEATMAP
                        BedMeshViewType.PROBE_POINTS -> BedMeshHeatmapView.ViewMode.PROBE_POINTS
                        BedMeshViewType.ISO -> BedMeshHeatmapView.ViewMode.ISO_WIREFRAME
                    },
```

(d) In `app/src/main/java/works/mees/dinghy/ui/settings/BedMeshRenderPrefs.kt`, replace the `viewType`
reader's `map` body (lines 31-35):

```kotlin
        .map { prefs ->
            val stored = prefs[viewKey(pid)]
            BedMeshViewType.entries.firstOrNull { it.name == stored } ?: BedMeshViewType.HEATMAP
        }
```

- [ ] **Step 4: Run to verify it passes (and the module still compiles)**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.settings.BedMeshRenderPrefsTest -Pkotlin.incremental=false --no-daemon"`
Expected: PASS (all three values incl. ISO round-trip); whole module compiles (exhaustive `when`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/calibration/BedMeshModel.kt app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapView.kt app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt app/src/main/java/works/mees/dinghy/ui/settings/BedMeshRenderPrefs.kt app/src/test/java/works/mees/dinghy/ui/settings/BedMeshRenderPrefsTest.kt
git commit -m "feat(calibration): add ISO view-type enums + screen mapping + robust prefs round-trip"
```

---

### Task 3: Register the `ssid_chart` icon

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` (val ~line 431 area + `all` list ~line 484)

**Interfaces:**
- Produces: `DinghyIcons.MeshViewIso` (a `DinghyIcon`).

- [ ] **Step 1: Add the val**

Near the other mesh-config icons (by `BlurCircular`, line ~431):

```kotlin
    val MeshViewIso = DinghyIcon(IconRef.Ligature("ssid_chart"), alternate = "ssid_chart")
```

- [ ] **Step 2: Add it to `all`**

In the `val all = listOf(...)` block (the group with `BlurCircular, HdrStrong, HdrWeak, Preview,` ~line 484), append `MeshViewIso`:

```kotlin
        BlurCircular, HdrStrong, HdrWeak, Preview, MeshViewIso,
```

- [ ] **Step 3: Run the ligature gate + icon uniqueness test**

Run: `python3 tools/verify_ligatures.py`
Expected: exit 0, `missing: []` (the registry-derived check now includes `ssid_chart`).

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.designsystem.icons.DinghyIconsTest -Pkotlin.incremental=false --no-daemon"`
Expected: PASS — `DinghyIconsTest` (which iterates `DinghyIcons.all`) stays green with `MeshViewIso` added. **Required, not optional:** `verify_ligatures` proves `ssid_chart` resolves but does NOT prove `all` membership; this test does.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
git commit -m "feat(icons): register ssid_chart (MeshViewIso) for the 3D Grid view type"
```

---

### Task 4: Render the wireframe in `BedMeshHeatmapView`

The Canvas work: a 3rd `ViewMode`, a dirty-guarded projection cache, and the height-colored line draw. Canvas rendering is not host-unit-testable; correctness rests on Task 1's proven projection plus on-device UAT — so this task adds NO new host test (the math it relies on is already covered).

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapView.kt` (add fields near other paints ~line 85-110; `onDraw` ~line 148; cache builder near `endpoints` ~line 234)

**Interfaces:**
- Consumes: `IsoProjection.project/fitTransform` (Task 1); `BedMeshHeatmapView.ViewMode.ISO_WIREFRAME` (added Task 2); `endpoints(grid, mode)` (existing private), `rampColor(frac)` (existing private), `deviationToRamp` (existing companion); `BedMeshModel.meshMatrix/meshMin/meshMax` (`MeshPoint`).
- Produces: the `ISO_WIREFRAME` render branch.

> `ViewMode.ISO_WIREFRAME` was already added in Task 2 (to keep the build exhaustive). This task adds the paint, the dirty-guarded cache, and the `onDraw` branch that actually draws it.

- [ ] **Step 1: Add the line paint + projection cache fields**

Near the other pre-allocated paints (after `emptyPaint`, ~line 94):

```kotlin
    /** Pre-allocated stroke paint for the iso wireframe — color RE-SET per segment in onDraw (no alloc). */
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }

    // Iso projection cache (ISO_WIREFRAME). Rebuilt ONLY when the inputs below change — never on
    // incidental recomposition / token push (Codex). Keyed STRUCTURALLY (not by model identity):
    // BedMeshHolder.buildVm() makes a fresh BedMeshModel on every printer-state emission and previewOf()
    // copies, so reference identity would rebuild constantly. `meshMatrix` is a List<List<Double>> →
    // `==` is a deep structural compare (O(N), same order as the draw itself, and this view is static).
    // Screen coords are post-fit; color is per-draw (a ramp/theme change needs only invalidate()).
    private var isoScreenX: FloatArray = FloatArray(0)
    private var isoScreenY: FloatArray = FloatArray(0)
    private var isoFrac: FloatArray = FloatArray(0)
    private var isoRows = 0
    private var isoCols = 0
    private var isoCacheMatrix: List<List<Double>>? = null
    private var isoCacheMinX = Double.NaN
    private var isoCacheMinY = Double.NaN
    private var isoCacheMaxX = Double.NaN
    private var isoCacheMaxY = Double.NaN
    private var isoCacheScale: ScaleMode? = null
    private var isoCacheW = -1
    private var isoCacheH = -1
```

Add a companion constant (in the existing `companion object`, near `DOT_RADIUS_FRAC`):

```kotlin
        /** Unit-space vertical amplitude for the iso wireframe — how tall a full-band deviation pops. */
        private const val ISO_HEIGHT_AMP = 0.6
        /** Iso fit inset (px) so the lattice doesn't touch the frame. */
        private const val ISO_INSET_PX = 8f
```

- [ ] **Step 2: Add the `ISO_WIREFRAME` branch + cache builder to `onDraw`**

At the top of `onDraw` (after `val w`/`val h`, before the `PROBE_POINTS` branch ~line 152), add:

```kotlin
        if (viewMode == ViewMode.ISO_WIREFRAME) {
            val grid = model.meshMatrix
            if (grid.isEmpty() || grid[0].isEmpty()) {
                canvas.drawRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, emptyPaint)
                return
            }
            rebuildIsoCacheIfNeeded(w.toInt(), h.toInt())
            // Draw row-wise then column-wise segments; color each by the ramp at its midpoint frac.
            // Far rows first (painter's order): row index ascends front→rear, so draw rear→front.
            for (j in isoRows - 1 downTo 0) {
                for (i in 0 until isoCols - 1) {
                    val a = j * isoCols + i; val b = a + 1
                    linePaint.color = rampColor((isoFrac[a] + isoFrac[b]) * 0.5f)
                    canvas.drawLine(isoScreenX[a], isoScreenY[a], isoScreenX[b], isoScreenY[b], linePaint)
                }
            }
            for (i in 0 until isoCols) {
                for (j in isoRows - 1 downTo 1) {
                    val a = j * isoCols + i; val b = (j - 1) * isoCols + i
                    linePaint.color = rampColor((isoFrac[a] + isoFrac[b]) * 0.5f)
                    canvas.drawLine(isoScreenX[a], isoScreenY[a], isoScreenX[b], isoScreenY[b], linePaint)
                }
            }
            return
        }
```

Add the cache builder as a private method (near `endpoints`, ~line 234):

```kotlin
    /**
     * (Re)build the iso projection cache iff the mesh, scale mode, or view size changed since the last
     * build — so incidental invalidate()/recompose does NOT redo the trig (Codex). Color is NOT a cache
     * input: a theme/ramp change only needs invalidate(), the per-segment rampColor lookup runs at draw.
     */
    private fun rebuildIsoCacheIfNeeded(w: Int, h: Int) {
        if (isoScreenX.isNotEmpty()
            && isoCacheScale == scaleMode && isoCacheW == w && isoCacheH == h
            && isoCacheMinX == model.meshMin.x && isoCacheMinY == model.meshMin.y
            && isoCacheMaxX == model.meshMax.x && isoCacheMaxY == model.meshMax.y
            && isoCacheMatrix == model.meshMatrix  // deep structural compare (survives fresh-but-equal models)
        ) return
        val grid = model.meshMatrix
        val (loZ, hiZ) = endpoints(grid, scaleMode)
        val p = IsoProjection.project(
            grid, model.meshMin.x, model.meshMax.x, model.meshMin.y, model.meshMax.y,
            loZ, hiZ, ISO_HEIGHT_AMP,
        )
        val fit = IsoProjection.fitTransform(p.isoX, p.isoY, w.toDouble(), h.toDouble(), ISO_INSET_PX.toDouble())
        val n = p.isoX.size
        if (isoScreenX.size != n) { isoScreenX = FloatArray(n); isoScreenY = FloatArray(n); isoFrac = FloatArray(n) }
        for (k in 0 until n) {
            isoScreenX[k] = (p.isoX[k] * fit.scale + fit.dx).toFloat()
            isoScreenY[k] = (p.isoY[k] * fit.scale + fit.dy).toFloat()
            isoFrac[k] = p.frac[k].toFloat()
        }
        isoRows = p.rows; isoCols = p.cols
        isoCacheMatrix = model.meshMatrix
        isoCacheMinX = model.meshMin.x; isoCacheMinY = model.meshMin.y
        isoCacheMaxX = model.meshMax.x; isoCacheMaxY = model.meshMax.y
        isoCacheScale = scaleMode; isoCacheW = w; isoCacheH = h
    }
```

> Note: `meshMin`/`meshMax` are `MeshPoint(x, y)` on `BedMeshModel`. `endpoints` and `rampColor` are existing private members. `model` and `scaleMode` are existing fields set by `setMesh`. No change to `setMesh`/`applyTokens` is needed — gating the rebuild inside `onDraw` by the cache key fully prevents redundant projection work even though the Host pushes every recompose.

- [ ] **Step 3: Compile-check (assemble) to verify the View builds**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin -Pkotlin.incremental=false --no-daemon"`
Expected: BUILD SUCCESSFUL (the new branch + cache compile; `IsoProjection` resolves).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapView.kt
git commit -m "feat(calibration): render ISO_WIREFRAME (height-colored lattice, dirty-guarded cache)"
```

---

### Task 5: Wire the 3rd View Type into the screen

Expose `ISO` to the user: the selector tile and the row icon swap. (The `viewType→ViewMode` mapping was
added in Task 2 to keep the build exhaustive.)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt` (`ViewTypeSelector` ~line 1014-1034; the two View Type rows' icons at lines 673 and 752)
- Modify (doc only): `app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapHost.kt` (the `@param viewMode` line ~30 AND the earlier bullet ~line 23 that still says only heatmap/probe dots)

**Interfaces:**
- Consumes: `BedMeshViewType.ISO` (T2), `DinghyIcons.MeshViewIso` (T3).

- [ ] **Step 1: Add the "3D Grid" selector tile**

In `ViewTypeSelector` (~line 1014), after the `PROBE_POINTS` tile, add a third (tiles stay text-only):

```kotlin
        ViewTypeTile(
            label = "3D Grid",
            viewType = BedMeshViewType.ISO,
            current = current,
            onPick = onPick,
            t = t,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
```

- [ ] **Step 2: Swap the View Type row icon `BlurCircular → MeshViewIso`**

There are TWO occurrences (the config-list row ~line 673 and the editor-selected row ~line 752). Replace both:

```kotlin
                                                icon = DinghyIcons.MeshViewIso,
```

- [ ] **Step 3: Update the Host docs (BOTH spots — Codex)**

In `BedMeshHeatmapHost.kt`, update the `@param viewMode` line (~30):

```kotlin
 * @param viewMode      HEATMAP (interpolated fill), PROBE_POINTS (ramp-colored dots), or ISO_WIREFRAME (3D grid).
```

…and the earlier bullet (~line 23) that still says only two modes:

```kotlin
 *  - `view.setViewMode(viewMode)` switches heatmap fill / colored probe dots / 3D wireframe;
```

- [ ] **Step 4: Assemble to verify the screen wires**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin -Pkotlin.incremental=false --no-daemon"`
Expected: BUILD SUCCESSFUL — the selector's 3rd tile + the swapped icon resolve.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapHost.kt
git commit -m "feat(calibration): expose 3D Grid view type (selector tile + row icon + mapping)"
```

---

### Task 6: Full verification (suite + APK + gates)

Terminal deliverable: the whole suite green, a release-flag-free debug APK built, ligature gate clean — ready for post-execution Codex review and on-device UAT.

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit suite** (catches `FontConformanceTest` + all bed-mesh/render/prefs tests)

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest -Pkotlin.incremental=false --no-daemon"`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 2: Ligature gate**

Run: `python3 tools/verify_ligatures.py`
Expected: exit 0.

- [ ] **Step 3: Build both ABI slices**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug -Pkotlin.incremental=false --no-daemon"`
Expected: BUILD SUCCESSFUL; APKs under `app/build/outputs/apk/debug/` with fresh mtimes (verify mtime is after the last commit per the stale-APK gate).

- [ ] **Step 4: Commit (if any verification fixes were needed; otherwise nothing to commit)**

(No-op if Steps 1-3 were clean.)

---

## Self-Review

**Spec coverage:**
- Wireframe lines only → Task 4 draw loop (drawLine, no fill). ✓
- Geometry from `meshMatrix`; saved-profile preview via `previewOf` → Task 4 reads `model.meshMatrix` (works for previews automatically). ✓
- Height + color from Color Scale mode (`endpoints`/`deviationToRamp`) → Task 1 `project` + Task 4 `endpoints` call. ✓
- Height-colored lines via `rampColor` → Task 4 segment color. ✓
- Fixed iso, no rotation; auto-fit; non-square aspect → Task 1 `project`/`fitTransform` + tests. ✓
- Front-at-bottom → Task 1 inverted `gyView` + `front_edge_projects_below_rear_edge` test. ✓
- Degenerate guards (C-1/R-1/halfSpan/bbox) → Task 1 guards + `degenerate_inputs_are_safe`. ✓
- `ssid_chart` glyph, tiles text-only, row-icon swap → Task 3 + Task 5 Steps 2-3. ✓
- Prefs ISO round-trip → Task 2. ✓
- `DinghyIcons.all` inclusion → Task 3 Step 2. ✓
- Cache dirty-guarding → Task 4 `rebuildIsoCacheIfNeeded`. ✓
- RELATIVE midplane documented → spec decision #8 (no code change; height==color by construction). ✓
- Cap/stride policy → not coded in v1 by decision (no cap; realistic grids are small); noted in spec. ✓ (No task needed — confirm representative sizes during UAT.)

**Placeholder scan:** none — all steps carry concrete code/commands.

**Type consistency:** `BedMeshViewType.ISO`, `ViewMode.ISO_WIREFRAME`, `DinghyIcons.MeshViewIso`, `IsoProjection.project/fitTransform/Projected/Fit`, `rebuildIsoCacheIfNeeded` used consistently across Tasks 1, 2, 3, 4, 5. `meshMin/meshMax: MeshPoint(.x/.y)` matches `BedMeshModel`. `endpoints`/`rampColor`/`deviationToRamp` are existing members reused verbatim.
