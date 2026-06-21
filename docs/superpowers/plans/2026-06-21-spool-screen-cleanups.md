# Spool / Filaments Screen Cleanups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three Spoolman/filament-screen cleanups — a loaded-status icon in the Focus title's trailing slot (with overflow-only title reclaim), an inventory-derived chemistry filter, and a multi-select color filter.

**Architecture:** Change 1 adds a non-interactive trailing status slot to the shared `FocusFrame`/`FocusHeader` and makes the title's trailing padding reclaim only when the title overflows (decided by a pure, unit-tested helper fed by a `TextMeasurer`). Change 2 derives available material families from the same base spool read that already feeds the vendor universe. Change 3 turns the single-select color filter into a multi-select set (OR semantics), splitting the gcode-seed hint into its own field.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), kotlinx.coroutines + Flow, JUnit4 + kotlinx-coroutines-test. Builds Windows-side via the `gw.bat` helper.

## Global Constraints

- minSdk 23 floor; Adreno 320 / 2GB is the perf floor — no continuous animation; `basicMarquee` is the one sanctioned (overflow-only) title-scroll exception.
- All colors are role tokens (THEME-01) except item-data hexes (spool colors). `t.go` for the loaded check.
- Icons come from the curated `DinghyIcons` registry only — **never invent a glyph**. Loaded icon = `DinghyIcons.CheckCircle` (owner-confirmed, reuse of the retired badge glyph).
- No inline `fontFamily=`/`fontSize=` — `FontConformanceTest` fails the build otherwise. Use `DinghyType.<role>.toTextStyle(t)`.
- Filters are session-derived, not persisted (no DataStore change). No `AppContainer` constructor change (Codex-confirmed).
- Run tests with the Windows helper, e.g.:
  `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests '<FQCN>' --rerun-tasks"`
  Pipe through `tr -d '\r'`; the process exit code is authoritative. Use `--rerun-tasks` to defeat stale UP-TO-DATE.

---

## File Structure

- `app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt` — add trailing status slot + pure `resolveHeaderTitleLayout` helper + measure-driven title padding (Task 1).
- `app/src/test/java/works/mees/dinghy/designsystem/components/HeaderTitleLayoutTest.kt` — NEW, unit tests for the helper (Task 1).
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` — wire loaded status icon, drop body badge + dead `isActive` (Task 2); TYPE filter iterates available families (Task 4); color multi-select UI wiring (Task 5).
- `app/src/main/res/values/strings.xml` — remove `spool_badge_loaded` (Task 2); add `spool_type_empty` (Task 4).
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt` — `availableMaterialFamilies` state + helper + `loadChips` rework (Task 3); color multi-select state + `applyColorSwatch`/`clearColor`/`seedPrefilter` (Task 5).
- `app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderChemistryTest.kt` — NEW (Task 3).
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt` — `ColorSwatchGrid`/`ColorTile` take a `Set<String>` (Task 5).
- `app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderColorTest.kt` — rewrite for the multi-select contract (Task 5).

---

## Task 1: FocusFrame — non-interactive trailing status slot + overflow-only title reclaim

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt` (signature ~201-216; FocusHeader ~283-379; the `FocusHeader(...)` call ~241-252)
- Test: `app/src/test/java/works/mees/dinghy/designsystem/components/HeaderTitleLayoutTest.kt` (new)

**Interfaces:**
- Produces: `internal data class HeaderTitleLayout(val startSlots: Int, val endSlots: Int, val marquee: Boolean)` and `internal fun resolveHeaderTitleLayout(availableWidthPx: Float, titleWidthPx: Float, slotPx: Float, endSlotOccupied: Boolean): HeaderTitleLayout`.
- Produces: `FocusFrame(... trailingStatusIcon: DinghyIcon? = null, trailingStatusTint: Color? = null, trailingStatusContentDescription: String? = null ...)` (consumed by Task 2).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/works/mees/dinghy/designsystem/components/HeaderTitleLayoutTest.kt`:

```kotlin
package works.mees.dinghy.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderTitleLayoutTest {

    // avail=1000, slot=100 → symmetric budget = 1000 - 2*100 = 800
    private val avail = 1000f
    private val slot = 100f

    @Test
    fun `fitting title keeps symmetric padding and no marquee`() {
        val r = resolveHeaderTitleLayout(avail, titleWidthPx = 500f, slotPx = slot, endSlotOccupied = false)
        assertEquals(1, r.startSlots)
        assertEquals("a fitting title stays truly centered (both slots reserved)", 1, r.endSlots)
        assertFalse(r.marquee)
    }

    @Test
    fun `fitting title stays symmetric even when an end glyph is present`() {
        val r = resolveHeaderTitleLayout(avail, titleWidthPx = 500f, slotPx = slot, endSlotOccupied = true)
        assertEquals(1, r.endSlots)
        assertFalse(r.marquee)
    }

    @Test
    fun `overflowing title with no end glyph reclaims the trailing slot and marquees`() {
        val r = resolveHeaderTitleLayout(avail, titleWidthPx = 900f, slotPx = slot, endSlotOccupied = false)
        assertEquals(1, r.startSlots)
        assertEquals("no end glyph → reclaim the trailing slot", 0, r.endSlots)
        assertTrue(r.marquee)
    }

    @Test
    fun `overflowing title with an end glyph keeps the trailing slot for the glyph`() {
        val r = resolveHeaderTitleLayout(avail, titleWidthPx = 900f, slotPx = slot, endSlotOccupied = true)
        assertEquals("end glyph occupies the slot → cannot reclaim", 1, r.endSlots)
        assertTrue(r.marquee)
    }

    @Test
    fun `boundary — title exactly at the symmetric budget still fits`() {
        val r = resolveHeaderTitleLayout(avail, titleWidthPx = 800f, slotPx = slot, endSlotOccupied = false)
        assertFalse("== budget counts as fitting", r.marquee)
    }

    @Test
    fun `tiny width coerces a negative budget to zero — anything overflows`() {
        // avail=120, slot=100 → budget would be -80 → coerced to 0 → any positive title overflows
        val r = resolveHeaderTitleLayout(availableWidthPx = 120f, titleWidthPx = 10f, slotPx = 100f, endSlotOccupied = false)
        assertTrue(r.marquee)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.designsystem.components.HeaderTitleLayoutTest' --rerun-tasks" 2>&1 | tr -d '\r'`
Expected: FAIL — `resolveHeaderTitleLayout`/`HeaderTitleLayout` unresolved (compile error).

- [ ] **Step 3: Add the pure helper**

In `FocusFrame.kt`, add near the top-level private constants (after `IDENTITY_ICON_RATIO`, ~line 57):

```kotlin
/** The resolved title padding for the Focus header, in whole icon-slot units (Task 1). */
internal data class HeaderTitleLayout(val startSlots: Int, val endSlots: Int, val marquee: Boolean)

/**
 * Decide the Focus-header title padding. The title stays TRULY centered (both slots reserved) whenever
 * it fits within the symmetric budget `available - 2*slot`; only an OVERFLOWING title reclaims the
 * trailing slot — and only when no end glyph occupies it — and marquees. Pure for unit testing.
 */
internal fun resolveHeaderTitleLayout(
    availableWidthPx: Float,
    titleWidthPx: Float,
    slotPx: Float,
    endSlotOccupied: Boolean,
): HeaderTitleLayout {
    val symmetricBudget = (availableWidthPx - 2f * slotPx).coerceAtLeast(0f)
    val fits = titleWidthPx <= symmetricBudget
    return if (fits) {
        HeaderTitleLayout(startSlots = 1, endSlots = 1, marquee = false)
    } else {
        HeaderTitleLayout(startSlots = 1, endSlots = if (endSlotOccupied) 1 else 0, marquee = true)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.designsystem.components.HeaderTitleLayoutTest' --rerun-tasks" 2>&1 | tr -d '\r'`
Expected: PASS (6 tests).

- [ ] **Step 5: Add the trailing status params to `FocusFrame` and pass them through**

In the `FocusFrame` signature (~213-215), add after `trailingActionContentDescription`:

```kotlin
    trailingActionContentDescription: String? = null,
    trailingStatusIcon: DinghyIcon? = null,
    trailingStatusTint: Color? = null,
    trailingStatusContentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
```

In the `FocusHeader(...)` call inside `FocusFrame` (~249-251), add:

```kotlin
            trailingActionIcon = trailingActionIcon,
            onTrailingAction = onTrailingAction,
            trailingActionContentDescription = trailingActionContentDescription,
            trailingStatusIcon = trailingStatusIcon,
            trailingStatusTint = trailingStatusTint,
            trailingStatusContentDescription = trailingStatusContentDescription,
```

Add the same three params to the private `FocusHeader` signature (~292-294):

```kotlin
    trailingActionIcon: DinghyIcon? = null,
    onTrailingAction: (() -> Unit)? = null,
    trailingActionContentDescription: String? = null,
    trailingStatusIcon: DinghyIcon? = null,
    trailingStatusTint: Color? = null,
    trailingStatusContentDescription: String? = null,
) {
```

- [ ] **Step 6: Convert the header Box to measure-driven title padding**

Replace the header `Box(...)` opener (~300-318, the `Box` through the centered `Text`) with a `BoxWithConstraints` that measures the title and applies the helper. The exact replacement for lines 300-318:

```kotlin
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(uDp)
            .padding(horizontal = FocusInset),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val titleStyle = DinghyType.focusHeader.toTextStyle(t)
        val measurer = rememberTextMeasurer()
        val slotPx = with(density) { slot.toPx() }
        val availPx = with(density) { maxWidth.toPx() }
        // Memoize the single-line intrinsic width; re-measure only when the text, width, or scale changes.
        val titleWidthPx = remember(title, maxWidth, t.fs, density.density, density.fontScale) {
            measurer.measure(
                text = AnnotatedString(title),
                style = titleStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width.toFloat()
        }
        val endSlotOccupied =
            (trailingActionIcon != null && onTrailingAction != null) || trailingStatusIcon != null
        val layout = resolveHeaderTitleLayout(availPx, titleWidthPx, slotPx, endSlotOccupied)
        // Centered title: symmetric padding when it fits (true center); reclaim the trailing slot only
        // when it overflows AND no end glyph occupies it; marquee only on the overflow path.
        Text(
            text = title,
            color = t.text,
            style = titleStyle,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = slot * layout.startSlots, end = slot * layout.endSlots)
                .then(if (layout.marquee) Modifier.basicMarquee() else Modifier),
        )
```

Add the required imports at the top of the file (if not already present):

```kotlin
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
```

(The closing brace of this Box at ~379 is unchanged — it now closes the `BoxWithConstraints`.)

- [ ] **Step 7: Render the non-interactive trailing status glyph in the end slot**

Immediately AFTER the existing trailing-action `if (trailingActionIcon != null && onTrailingAction != null) { ... }` block (ends ~378), add:

```kotlin
        // End slot, status variant: a NON-interactive indicator glyph (e.g. the loaded check). Mutually
        // exclusive with the tappable trailing action above — the action wins the slot if both are set.
        if (trailingStatusIcon != null && !(trailingActionIcon != null && onTrailingAction != null)) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(slot),
                contentAlignment = Alignment.Center,
            ) {
                DinghyIconView(
                    icon = trailingStatusIcon,
                    tint = trailingStatusTint ?: t.text2,
                    sizeDp = slot * IDENTITY_ICON_RATIO,
                    contentDescription = trailingStatusContentDescription,
                )
            }
        }
```

- [ ] **Step 8: Build the module to verify compilation + conformance**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --rerun-tasks" 2>&1 | tr -d '\r' | tail -30`
Expected: BUILD SUCCESSFUL; `HeaderTitleLayoutTest` + existing suites (incl. `FocusEdgeTest`, `FontConformanceTest`) pass.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt \
        app/src/test/java/works/mees/dinghy/designsystem/components/HeaderTitleLayoutTest.kt
git commit -m "feat(focusframe): trailing status slot + overflow-only title reclaim"
```

---

## Task 2: Spool — loaded status icon, drop body badge + dead isActive + unused string

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` (FocusFrame call ~339-350; `SpoolDetailContent` signature ~777-783 and its call ~352-358; loaded badge ~854-855)
- Modify: `app/src/main/res/values/strings.xml` (remove `spool_badge_loaded`, line 190)

**Interfaces:**
- Consumes: `FocusFrame(... trailingStatusIcon, trailingStatusTint, trailingStatusContentDescription ...)` from Task 1.

- [ ] **Step 1: Wire the loaded status icon into the FocusFrame call**

In `SpoolScreen.kt`, in the `FocusFrame(...)` call, insert these THREE new arguments immediately before the call's trailing `) {` (i.e. right after the existing `onPanic = onEmergencyStop,` line at ~350). Do not duplicate the existing args — add only:

```kotlin
                    trailingStatusIcon = if (isSelectedLoaded) DinghyIcons.CheckCircle else null,
                    trailingStatusTint = t.go,
                    trailingStatusContentDescription = stringResource(R.string.cd_spool_loaded),
```

- [ ] **Step 2: Remove the loaded body badge and the dead `isActive` plumbing**

Remove the loaded badge in `SpoolDetailContent` (~854-855):

```kotlin
        if (isActive) {
            DetailBadge(DinghyIcons.CheckCircle, stringResource(R.string.spool_badge_loaded), stringResource(R.string.cd_spool_loaded), t, iconSp, t.go)
        }
```

Remove the `isActive` parameter from the `SpoolDetailContent` signature (~777-783) — delete the `isActive: Boolean,` line — and remove the `isActive = isSelectedLoaded,` argument at its call site (~354). Leave the **archived** badge (`if (spool.archived) { ... }`) intact.

- [ ] **Step 3: Remove the now-unused string**

In `strings.xml`, delete line 190:

```xml
    <string name="spool_badge_loaded">Loaded on this printer</string>
```

(Keep `cd_spool_loaded` — it is now the trailing-status content description.)

- [ ] **Step 4: Build to verify compilation + conformance**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --rerun-tasks" 2>&1 | tr -d '\r' | tail -30`
Expected: BUILD SUCCESSFUL; no unresolved `spool_badge_loaded` / `isActive` references; all unit suites pass.
(If the build reports `spool_badge_loaded` still referenced, grep `app/src` for it and remove the stray usage.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(spool): loaded check in title trailing slot; drop body badge"
```

---

## Task 3: Chemistry — inventory-derived available families (holder)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt` (`SpoolPickerState` ~145-158; `loadChips` ~524-549; import ~22)
- Test: `app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderChemistryTest.kt` (new)

**Interfaces:**
- Produces: `SpoolPickerState.availableMaterialFamilies: List<String>` (family LABELS, in `MATERIAL_FAMILIES` order) — consumed by Task 4.
- Produces: `internal fun availableMaterialFamilies(ownedMaterials: List<String>): List<String>`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderChemistryTest.kt`:

```kotlin
package works.mees.dinghy.ui.spool

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanStatus

@OptIn(ExperimentalCoroutinesApi::class)
class SpoolHolderChemistryTest {

    private val noActiveSpool = MutableStateFlow<SpoolmanStatus?>(null)

    private fun proxyEnvelope(rowsJson: String): JsonElement =
        MoonrakerJson.parseToJsonElement(
            """{"response":$rowsJson,"error":null,"response_headers":{"X-Total-Count":"99"}}""",
        )

    // Pure-helper coverage (no holder needed).
    @Test fun `helper keeps only families present in inventory, in declared order`() {
        val fams = availableMaterialFamilies(listOf("PETG", "PLA+", "ASA"))
        assertEquals(listOf("PLA", "PETG", "ABS/ASA"), fams)
    }

    @Test fun `helper ignores blank materials and dedups families`() {
        val fams = availableMaterialFamilies(listOf("PLA", "PLA Matte", "  ", ""))
        assertEquals(listOf("PLA"), fams)
    }

    @Test fun `helper returns empty for empty inventory`() {
        assertTrue(availableMaterialFamilies(emptyList()).isEmpty())
    }

    @Test fun `helper counts a hybrid material in every matching family`() {
        // PC-ABS contains both "PC" and "ABS" → both families available, in declared order.
        assertEquals(listOf("ABS/ASA", "PC"), availableMaterialFamilies(listOf("PC-ABS")))
    }

    // Spools own PLA+ and ASA only → families = {PLA, ABS/ASA}; TPU/PETG/PC/Nylon excluded.
    private val materialClient: SpoolmanClient = object : SpoolmanClient {
        override suspend fun listSpools(query: String?): JsonElement = proxyEnvelope(
            """[
                {"id":1,"filament":{"id":1,"name":"PolyTerra","material":"PLA+","vendor":{"id":1,"name":"Polymaker"}}},
                {"id":2,"filament":{"id":2,"name":"ASA Pro","material":"ASA","vendor":{"id":2,"name":"Sunlu"}}},
                {"id":3,"filament":{"id":3,"name":"No Material"}}
            ]""",
        )
    }

    private fun holder() = SpoolHolder(
        scope = TestScope(UnconfinedTestDispatcher()),
        client = materialClient,
        activeSpool = noActiveSpool,
    )

    @Test fun `loadChips derives available families from physical spools only`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder()
            h.load()
            val fams = h.state.value.availableMaterialFamilies
            assertTrue("PLA family (PLA+) present", fams.contains("PLA"))
            assertTrue("ABS/ASA family (ASA) present", fams.contains("ABS/ASA"))
            assertFalse("TPU not owned → excluded", fams.contains("TPU"))
            assertFalse("PETG not owned → excluded", fams.contains("PETG"))
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.spool.SpoolHolderChemistryTest' --rerun-tasks" 2>&1 | tr -d '\r'`
Expected: FAIL — `availableMaterialFamilies` and `SpoolPickerState.availableMaterialFamilies` unresolved.

- [ ] **Step 3: Add the pure helper**

In `SpoolHolder.kt`, add right after `materialFamilyLabel(...)` (~66):

```kotlin
/**
 * The [MATERIAL_FAMILIES] (in declared order) that have at least one matching material among
 * [ownedMaterials] (the physical spools' filament materials). A family is available if ANY of its
 * terms is a substring of ANY owned material (uppercased) — mirroring Spoolman's `filament.material`
 * substring query. This is per-family (NOT [materialFamilyLabel], which returns only the first match),
 * so a hybrid material like `PC-ABS` correctly marks BOTH `PC` and `ABS/ASA` available.
 */
internal fun availableMaterialFamilies(ownedMaterials: List<String>): List<String> {
    val up = ownedMaterials.mapNotNull { it.trim().uppercase().ifEmpty { null } }
    return MATERIAL_FAMILIES
        .filter { (_, terms) -> terms.any { term -> up.any { it.contains(term.uppercase()) } } }
        .map { it.first }
}
```

- [ ] **Step 4: Add the state field**

In `SpoolPickerState` (~152), replace the `materials` field with `availableMaterialFamilies`:

```kotlin
    val availableMaterialFamilies: List<String> = emptyList(),
    val vendors: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
```

(Remove the `val materials: List<String> = emptyList(),` line. Update the KDoc `@property materials/...` line to read `@property availableMaterialFamilies/[vendors]/[locations]`.)

- [ ] **Step 5: Rework `loadChips` to one base read + derive both vendors and families**

Replace the body of `loadChips()` (~525-548) with:

```kotlin
    private suspend fun loadChips() {
        // ONE base spool read (allow_archived=false, no facet filters) feeds BOTH the vendor universe
        // AND the available material families — so both reflect only manufacturers/materials actually
        // represented by a PHYSICAL spool (Spoolman classification is mfg→filament→spool; the raw
        // /v1/vendor and /v1/material tables include spool-less definitions that could never match a
        // listed spool — the corrected on-device defect, owner 2026-06-10).
        val baseSpools = parseSpoolmanSpools(
            runCatching { client.listSpools("allow_archived=false&limit=$SPOOL_LIMIT") }.getOrNull(),
        ).rows
        val vendors = baseSpools
            .mapNotNull { it.filament?.vendor?.name?.trim()?.ifEmpty { null } }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
        val ownedMaterials = baseSpools.mapNotNull { it.filament?.material?.trim()?.ifEmpty { null } }
        val families = availableMaterialFamilies(ownedMaterials)
        val locations = parseSpoolmanLocations(runCatching { client.listLocations() }.getOrNull()).rows
        _state.update {
            it.copy(availableMaterialFamilies = families, vendors = vendors, locations = locations)
        }
    }
```

Remove the now-unused import on line 22: `import works.mees.dinghy.spool.parseSpoolmanMaterials`. (Leave the `parseSpoolmanMaterials` function and the `SpoolmanClient.listMaterials` interface method — they have other callers: `SpoolmanProxyParserTest`, `FakeSpoolmanClient`.)

- [ ] **Step 6: Run test to verify it passes**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.spool.SpoolHolderChemistryTest' --rerun-tasks" 2>&1 | tr -d '\r'`
Expected: PASS (4 tests).

- [ ] **Step 7: Build the module (catch stale `state.materials` readers)**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --rerun-tasks" 2>&1 | tr -d '\r' | tail -30`
Expected: BUILD SUCCESSFUL. (If a compile error names `materials`, it is a stale reader — there should be none, but fix any that surface.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt \
        app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderChemistryTest.kt
git commit -m "feat(spool): derive chemistry filter families from inventory"
```

---

## Task 4: Chemistry — TYPE filter UI iterates available families

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` (TYPE branch ~505-527)
- Modify: `app/src/main/res/values/strings.xml` (add `spool_type_empty`)

**Interfaces:**
- Consumes: `state.availableMaterialFamilies` from Task 3.

- [ ] **Step 1: Add the empty-state string**

In `strings.xml`, next to `spool_mfg_empty` (line 246), add:

```xml
    <string name="spool_type_empty">No material types found.</string>
```

- [ ] **Step 2: Iterate available families with an empty state**

Replace the `SpoolFilterCategory.TYPE -> { ... }` branch (~505-527) with (mirrors the MFG branch's empty-state shape):

```kotlin
            SpoolFilterCategory.TYPE -> {
                if (state.availableMaterialFamilies.isEmpty()) {
                    // Mirror the MFG empty-state Box EXACTLY (SpoolScreen.kt:545-557).
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.spool_type_empty),
                            color = t.text2,
                            style = DinghyType.body.toTextStyle(t),
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                } else {
                    DesignListBlock(modifier = Modifier.weight(1f)) {
                        items(state.availableMaterialFamilies, key = { it }) { label ->
                            val selected = state.filters.materialFamilies.any { it.equals(label, ignoreCase = true) }
                            ListRow(
                                selected = selected,
                                onClick = { onToggleMaterial(label) },
                                uDp = uDp,
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) t.accent2 else t.text,
                                    style = DinghyType.listLabel.toTextStyle(t),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
```

(Verify the MFG branch's empty-state `Box(...)` uses the same modifiers; match it exactly so the two look identical. Adjust the `Box` modifier to mirror MFG if it differs.)

- [ ] **Step 3: Seed the TYPE preview fixture so it doesn't render the empty state**

In `app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt`, the `spoolWithFilterOpen` fixture (~169-173) opens `FieldMode.FilterPicker(SpoolFilterCategory.TYPE)` but leaves `availableMaterialFamilies` at its empty default — which now shows "No material types found." Seed it so the TYPE picker preview shows real chips:

```kotlin
    val spoolWithFilterOpen: SpoolPickerState = SpoolPickerState(
        spools = spoolList,
        selected = spoolList.first(),
        fieldMode = FieldMode.FilterPicker(SpoolFilterCategory.TYPE),
        availableMaterialFamilies = listOf("PLA", "PETG", "ABS/ASA"),
    )
```

- [ ] **Step 4: Build to verify compilation + conformance**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --rerun-tasks" 2>&1 | tr -d '\r' | tail -30`
Expected: BUILD SUCCESSFUL; `FontConformanceTest` green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
git commit -m "feat(spool): TYPE filter lists only in-inventory families"
```

---

## Task 5: Color filter — multi-select (state + holder + UI)

This task changes `SpoolFilters` field shapes, so it spans the holder, the screen, and the picker, and rewrites the color test — all in one task so every commit compiles.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt` (`SpoolFilters` ~81-93; `applyColorSwatch` ~390-423; `clearColor` ~459-463; `seedPrefilter` ~475-498)
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` (`onTapSwatch` ~148; COLOR `isActive` ~325; `ColorSwatchGrid` call ~535-538)
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt` (`ColorSwatchGrid` ~73-104; `ColorTile` ~121-130)
- Test: `app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderColorTest.kt` (rewrite)

**Interfaces:**
- Produces: `SpoolFilters.colorSwatchHexes: List<String>` (normalized) and `SpoolFilters.colorSeedHex: String?`.
- Produces: `ColorSwatchGrid(selectedHexes: Set<String>, ...)`.

- [ ] **Step 1: Rewrite the color test for the multi-select contract**

Replace `app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderColorTest.kt` with:

```kotlin
package works.mees.dinghy.ui.spool

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanStatus

/**
 * Holder-level tests for the multi-select client-side color-family filter
 * ([SpoolHolder.applyColorSwatch]). See docs/superpowers/specs/2026-06-21-spool-screen-cleanups-design.md.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolHolderColorTest {

    private val noActiveSpool = MutableStateFlow<SpoolmanStatus?>(null)

    private fun proxyEnvelope(rowsJson: String): JsonElement =
        MoonrakerJson.parseToJsonElement(
            """{"response":$rowsJson,"error":null,"response_headers":{"X-Total-Count":"99"}}""",
        )

    /** A library: olive(Green id1), pure red(Red id2), multicolor red+green(id3), sky blue(Blue id4). */
    private val colorClient: SpoolmanClient = object : SpoolmanClient {
        override suspend fun listFilaments(query: String?): JsonElement = proxyEnvelope(
            """[
                {"id":1,"name":"Olive","color_hex":"64794b"},
                {"id":2,"name":"Red","color_hex":"ff0000"},
                {"id":3,"name":"Rainbow","multi_color_hexes":"ff0000,00c000"},
                {"id":4,"name":"Sky","color_hex":"5dc0f0"}
            ]""",
        )
    }

    private fun holder(client: SpoolmanClient) = SpoolHolder(
        scope = TestScope(UnconfinedTestDispatcher()),
        client = client,
        activeSpool = noActiveSpool,
    )

    @Test fun `tapping Green selects solid green AND a multicolor containing green`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000")
            assertEquals(listOf(1, 3), h.state.value.filters.colorFilamentIds)
            assertEquals(listOf("#00C000"), h.state.value.filters.colorSwatchHexes)
        }

    @Test fun `tapping a lowercase hex normalizes the stored swatch`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00c000")
            assertEquals(listOf("#00C000"), h.state.value.filters.colorSwatchHexes)
        }

    @Test fun `selecting Green then Red UNIONS both families' ids and keeps both swatches`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000")
            h.applyColorSwatch("#FF0000")
            // Green→{1,3}, Red→{2,3}; union (in filament order) = {1,2,3}.
            assertEquals(listOf(1, 2, 3), h.state.value.filters.colorFilamentIds)
            assertTrue(h.state.value.filters.colorSwatchHexes.containsAll(listOf("#00C000", "#FF0000")))
            assertEquals(2, h.state.value.filters.colorSwatchHexes.size)
        }

    @Test fun `re-tapping one of two colors removes only its contribution`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000")
            h.applyColorSwatch("#FF0000")
            h.applyColorSwatch("#FF0000") // toggle Red off
            assertEquals(listOf("#00C000"), h.state.value.filters.colorSwatchHexes)
            assertEquals(listOf(1, 3), h.state.value.filters.colorFilamentIds)
        }

    @Test fun `re-tapping the last color clears the whole color filter`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000")
            h.applyColorSwatch("#00C000")
            assertTrue(h.state.value.filters.colorSwatchHexes.isEmpty())
            assertNull(h.state.value.filters.colorFilamentIds)
        }

    @Test fun `a family with no matches yields an empty (unmatchable) id list`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#FFFF00") // Yellow — no filament classifies Yellow
            assertEquals(emptyList<Int>(), h.state.value.filters.colorFilamentIds)
            assertEquals(listOf("#FFFF00"), h.state.value.filters.colorSwatchHexes)
        }

    @Test fun `a failed fetch leaves the filter UNAPPLIED, not an empty list`() =
        runTest(UnconfinedTestDispatcher()) {
            val failing = object : SpoolmanClient {}
            val h = holder(failing)
            h.applyColorSwatch("#00C000")
            assertNull("fetch failure must not collapse the list", h.state.value.filters.colorFilamentIds)
            assertTrue(h.state.value.filters.colorSwatchHexes.isEmpty())
        }

    @Test fun `seedPrefilter maps an olive file color to the GREEN hint, not a hard filter`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.seedPrefilter(SpoolPrefilterSeed(filamentColors = listOf("#64794b")))
            assertEquals("#00C000", h.state.value.filters.colorSeedHex)
            assertTrue(h.state.value.filters.colorSwatchHexes.isEmpty())
            assertNull(h.state.value.filters.colorFilamentIds)
        }

    @Test fun `tapping the seeded swatch consumes the seed and applies a hard filter`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.seedPrefilter(SpoolPrefilterSeed(filamentColors = listOf("#64794b")))
            h.applyColorSwatch("#00C000")
            assertNull("seed must be consumed on the first hard tap", h.state.value.filters.colorSeedHex)
            assertEquals(listOf("#00C000"), h.state.value.filters.colorSwatchHexes)
            assertEquals(listOf(1, 3), h.state.value.filters.colorFilamentIds)
        }

    @Test fun `clearColor empties hexes, ids, and seed`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000")
            h.clearColor()
            assertTrue(h.state.value.filters.colorSwatchHexes.isEmpty())
            assertNull(h.state.value.filters.colorFilamentIds)
            assertNull(h.state.value.filters.colorSeedHex)
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.spool.SpoolHolderColorTest' --rerun-tasks" 2>&1 | tr -d '\r'`
Expected: FAIL — `colorSwatchHexes` / `colorSeedHex` unresolved.

- [ ] **Step 3: Update `SpoolFilters`**

In `SpoolHolder.kt`, replace the `colorSwatchHex` field (~85-86) in `SpoolFilters`:

```kotlin
    val colorFilamentIds: List<Int>? = null,
    val colorSwatchHexes: List<String> = emptyList(),
    val colorSeedHex: String? = null,
```

Update the KDoc lines (~77-79) to:

```kotlin
 *  - [colorFilamentIds] — D-06 result of swatch taps: the union of filament ids whose color(s)
 *    classify into ANY selected family, folded into `filament.id=<csv>`.
 *  - [colorSwatchHexes] — the (normalized) swatches the user tapped (multi-select; OR within facet).
 *  - [colorSeedHex] — a gcode-seed HINT swatch (display-only highlight; NOT a hard filter).
```

- [ ] **Step 4: Rewrite `applyColorSwatch` as a multi-select toggle**

Replace `applyColorSwatch` (~390-423) with:

```kotlin
    /**
     * D-06 (multi-select): toggle [swatchHex] in the color filter. Each selected swatch declares a
     * family (via [colorFamily]); the matching ids are the UNION across all selected families — a
     * filament matches if ANY of its sub-colors classifies into ANY selected family. Re-tapping a
     * selected swatch removes it; emptying the set clears the filter. A tap consumes any gcode seed
     * hint. A failed library fetch aborts the toggle (leaves filters unchanged) rather than collapsing
     * the list to nothing.
     */
    suspend fun applyColorSwatch(swatchHex: String) {
        val norm = normalizeColorHex(swatchHex) ?: return
        val current = _state.value.filters
        val present = current.colorSwatchHexes.any { it.equals(norm, ignoreCase = true) }
        val nextHexes = if (present) {
            current.colorSwatchHexes.filterNot { it.equals(norm, ignoreCase = true) }
        } else {
            current.colorSwatchHexes + norm
        }
        if (nextHexes.isEmpty()) {
            _state.update {
                it.copy(filters = it.filters.copy(colorSwatchHexes = emptyList(), colorFilamentIds = null, colorSeedHex = null))
            }
            refresh()
            return
        }
        val targetFamilies = nextHexes.mapNotNull { colorFamily(it) }.toSet()
        val envelope = runCatching { client.listFilaments("limit=$FILAMENT_LIMIT") }.getOrNull()
            ?: return // fetch failed: abort the toggle, leave filters unchanged (don't collapse the list).
        val ids = parseSpoolmanFilaments(envelope).rows
            .filter { f -> f.colorSwatches.any { colorFamily(it) in targetFamilies } }
            .mapNotNull(SpoolmanFilament::id)
        _state.update {
            it.copy(filters = it.filters.copy(colorSwatchHexes = nextHexes, colorFilamentIds = ids, colorSeedHex = null))
        }
        refresh()
    }
```

- [ ] **Step 5: Update `clearColor` and `seedPrefilter`**

Replace `clearColor` (~459-463):

```kotlin
    /** Clear just the color filter (the Color selector's Clear); re-issues the read. */
    suspend fun clearColor() {
        _state.update {
            it.copy(filters = it.filters.copy(colorSwatchHexes = emptyList(), colorFilamentIds = null, colorSeedHex = null))
        }
        refresh()
    }
```

In `seedPrefilter` (~488-496), change the filters copy to seed the hint field:

```kotlin
        _state.update {
            it.copy(
                filters = SpoolFilters(
                    materialFamilies = families,
                    colorSeedHex = colorHint,
                    colorFilamentIds = null, // hint, not a hard filter (D-04/D-06).
                ),
            )
        }
```

- [ ] **Step 6: Note — module won't compile yet**

After Steps 3-5 the holder is updated but `SpoolScreen.kt:325/536` and `SpoolPicker.kt` still reference the removed `colorSwatchHex`, so the test task (which compiles ALL main sources) will FAIL to compile. This is expected — do NOT try to make the test pass here. Complete the UI updates in Steps 7-8 first; the green color-test run happens at Step 9.

- [ ] **Step 7: Update the picker grid to a Set**

In `SpoolPicker.kt`, change `ColorSwatchGrid` (~73-74) and `ColorTile` (~121-130):

`ColorSwatchGrid` signature: replace `selectedHex: String?,` with `selectedHexes: Set<String>,`. **Normalize the incoming set once** inside the function body (so the component is self-contained regardless of what the caller passes) and pass the normalized set down — replace `selectedHex = selectedHex` at the `ColorTile(...)` call (~92) with `selectedHexes = normalizedSelected`:

```kotlin
    val normalizedSelected = remember(selectedHexes) { selectedHexes.mapNotNull { normalizeColorHex(it) }.toSet() }
```

`ColorTile` signature: replace `selectedHex: String?,` with `selectedHexes: Set<String>,` and change the selected check (~130) to membership against the already-normalized set:

```kotlin
    val selected = normalizeColorHex(hex)?.let { it in selectedHexes } ?: false
```

Add the import if missing: `import works.mees.dinghy.spool.normalizeColorHex` (and `androidx.compose.runtime.remember` if not present).

Also update the stale doc comment at `SpoolPicker.kt:57` that still calls COLOR (and MFG) "single-select" — change it to reflect that COLOR and MFG are now multi-select (OR within the facet).

- [ ] **Step 8: Update SpoolScreen color wiring**

In `SpoolScreen.kt`:

(a) `onTapSwatch` (~148) — drop the auto-close:

```kotlin
            onTapSwatch = { scope.launch { holder.applyColorSwatch(it) } },
```

(b) COLOR `FilterOption` `isActive` (~325):

```kotlin
                isActive = state.filters.colorSwatchHexes.isNotEmpty() || state.filters.colorSeedHex != null,
```

(c) `ColorSwatchGrid` call (~535-538) — pass the normalized highlight set (hard selections + the seed hint):

```kotlin
                ColorSwatchGrid(
                    selectedHexes = (state.filters.colorSwatchHexes + listOfNotNull(state.filters.colorSeedHex))
                        .mapNotNull { normalizeColorHex(it) }
                        .toSet(),
                    onTapSwatch = onTapSwatch,
```

Add the import if missing: `import works.mees.dinghy.spool.normalizeColorHex`.

- [ ] **Step 9: Build the whole module (all suites + conformance)**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --rerun-tasks" 2>&1 | tr -d '\r' | tail -40`
Expected: BUILD SUCCESSFUL; `SpoolHolderColorTest`, `SpoolPickerStateTest`, `SpoolHolderChemistryTest`, `SpoolHolderVendorTest`, `FontConformanceTest` all pass. (If `SpoolPickerStateTest` references the old `colorSwatchHex`, update those references to `colorSwatchHexes`/`colorSeedHex`.)

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt \
        app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt \
        app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt \
        app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderColorTest.kt
git commit -m "feat(spool): multi-select color filter (OR across families)"
```

---

## Task 6: Full build + on-device UAT

**Files:** none (verification only).

- [ ] **Step 1: Assemble both ABI slices**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks" 2>&1 | tr -d '\r' | tail -20`
Expected: BUILD SUCCESSFUL. Confirm the APK mtime is newer than the last commit (guard against the stale-APK trap).

- [ ] **Step 2: Install on both test devices**

Install the matching split to flox (`0a64b42e`, armeabi-v7a) and moto (`ZY22LBDRM9`, arm64-v8a) via `E:\Android\Sdk\platform-tools\adb.exe -s <id> install -r <apk>`.

- [ ] **Step 3: UAT checklist (owner)**

  - Loaded spool: green check shows in the title's trailing slot; the body "Loaded on this printer" line is gone. Archived badge still present for archived spools.
  - Short title stays truly centered whether loaded or not; a long (overflowing) title with no check uses the reclaimed trailing width before marquee-ing.
  - **FineTune** (the only `trailingActionIcon` user): the revert glyph still occupies/reserves its end slot.
  - Chemistry (Material) filter lists only families present in inventory; empty inventory shows "No material types found."
  - Color filter selects multiple swatches at once (OR); the picker does NOT close on each tap. **Done closes the picker; Clear empties the color selection but keeps the picker open** — identical to MFG/TYPE (Clear does not close for any facet). A gcode-seeded color still pre-highlights.

---

## Self-Review

- **Spec coverage:** Change 1 → Tasks 1+2; Change 2 → Tasks 3+4; Change 3 → Task 5; UAT matrix (incl. FineTune precedence, long/short titles) → Task 6. All spec sections mapped.
- **Type consistency:** `colorSwatchHexes: List<String>`, `colorSeedHex: String?`, `availableMaterialFamilies: List<String>`, `resolveHeaderTitleLayout(...)`/`HeaderTitleLayout`, `ColorSwatchGrid(selectedHexes: Set<String>)`, `trailingStatusIcon/Tint/ContentDescription` used identically across producer and consumer tasks.
- **Placeholder scan:** no TBD/TODO; every code step shows the actual code.
- **Spec-review Codex findings folded in:** #1 onTapSwatch no auto-close (Task 5 Step 8a); #2 seed consumed (Task 5 Step 4 + test); #3 normalized hexes (Task 5 Steps 4/7/8c); #4 derive from base spool read, drop dead `materials` (Task 3); #5 load-time derivation (helper + loadChips, no refresh change); #6 measurement details (Task 1 Step 6); #7 FineTune precedence + title spot-checks (Task 6); #8 dead `isActive` removed (Task 2).
- **Plan-review Codex findings folded in:** Clear stays open / Done closes — UAT wording fixed (Task 6); per-family availability (not first-match) + hybrid test (Task 3); Task 5 Step 6 reframed as expected compile-fail until UI updated; measure key adds `density.density` (Task 1); TYPE empty-state mirrors MFG exactly (Task 4); normalize the swatch set once in the grid (Task 5 Step 7); Task 2 Step 1 anchor shows only the new args; stale single-select comment + TYPE preview fixture seeded (Tasks 4/5).
