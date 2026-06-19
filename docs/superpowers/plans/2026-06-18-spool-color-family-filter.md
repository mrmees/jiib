# Spool Color-Family Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Spoolman's server-side CIE76 color-similarity matching with client-side hue-family classification so muted/dark colors (e.g. olive-green `#64794b`) are found by the swatch a human would tap, and add a Natural/Clear family.

**Architecture:** A new pure `colorFamily(hex)` buckets each filament's color(s) into one of 12 fixed palette families (the swatch names). `applyColorSwatch` fetches the whole filament library and keeps the ids whose colors classify to the tapped swatch's family, folding them into the existing `filament.id=<csv>` spool query (unchanged downstream). The Multi-color tile/filter is removed (multicolor spools stay findable via their sub-colors).

**Tech Stack:** Kotlin, kotlinx.serialization (`JsonElement`), coroutines/StateFlow, JUnit4 + kotlinx-coroutines-test. Builds Windows-side via `E:\Android\gw.bat` (see CLAUDE.md "Local Build Environment").

**Spec:** `docs/superpowers/specs/2026-06-18-spool-color-family-filter-design.md`

**Build/test command (from repo root, WSL):**
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
```
Filter to one class with `--tests` (e.g. `--tests "*ColorFamilyTest"`). The process exit code is authoritative.

**Commit discipline:** Each task is its own commit. Every commit must COMPILE — the task order below is chosen so it always does. End commit messages with the `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` trailer (CLAUDE.md). Watch the CRLF trap (`[[dinghy-crlf-commit-trap]]`): these are LF files.

---

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `app/src/main/java/works/mees/dinghy/ui/spool/ColorFamily.kt` | Pure hue-family classifier + integer RGB/HSL math | **Create** |
| `app/src/test/java/works/mees/dinghy/ui/spool/ColorFamilyTest.kt` | `colorFamily` unit tests | **Create** |
| `app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt` | Color swatch grid | Add Natural, make palette `internal`, drop Multi-color |
| `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` | Spool screen wiring | Drop `onMultiColor` plumbing |
| `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt` | Filter state machine | Rework `applyColorSwatch`/`seedPrefilter`; remove multi-color, threshold, `nearestPaletteSwatch`, `PREFILTER_PALETTE`, `parseRgb` |
| `app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderColorTest.kt` | `applyColorSwatch`/`seedPrefilter` holder tests | **Create** |
| `app/src/test/java/works/mees/dinghy/spool/FakeSpoolmanClient.kt` | Parser test double | Reseed filament key to `limit=1000` |
| `docs/view_specific_notes/spoolman.md`, `…/spoolman_live_validation.md`, `docs/ui_design/COMPONENTS.md` | Docs | Replace/mark-historical the old server-side color contract |

---

## Task 1: `colorFamily` pure classifier

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/spool/ColorFamily.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/spool/ColorFamilyTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/works/mees/dinghy/ui/spool/ColorFamilyTest.kt`:

```kotlin
package works.mees.dinghy.ui.spool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [colorFamily] — the client-side hue-family classifier that replaced Spoolman's
 * CIE76 color_similarity matching (which could not find muted colors like olive #64794b).
 * See docs/superpowers/specs/2026-06-18-spool-color-family-filter-design.md.
 */
class ColorFamilyTest {

    // The 12 fixed palette swatches must each classify as their OWN family — the tap derives the
    // target family by running the swatch's own hex through colorFamily.
    @Test fun `palette swatches self-classify`() {
        val palette = mapOf(
            "Black" to "#000000", "White" to "#FFFFFF", "Gray" to "#808080",
            "Natural" to "#EDE6D6", "Red" to "#FF0000", "Orange" to "#FF8000",
            "Yellow" to "#FFFF00", "Green" to "#00C000", "Blue" to "#0050FF",
            "Purple" to "#8000FF", "Pink" to "#FF60C0", "Brown" to "#7A4A20",
        )
        palette.forEach { (name, hex) -> assertEquals("$hex should be $name", name, colorFamily(hex)) }
    }

    // The bug: a muted olive must read as Green (perceptually it is near gray, but a human says green).
    @Test fun `olive green is Green`() = assertEquals("Green", colorFamily("#64794b"))

    @Test fun `real owner filaments classify intuitively`() {
        assertEquals("Brown", colorFamily("#886543"))  // Coffee Brown
        assertEquals("Blue", colorFamily("#5dc0f0"))    // Sky Blue
        assertEquals("Yellow", colorFamily("#F6FA00"))  // CMYK Yellow
        assertEquals("Red", colorFamily("#E63034"))     // Cherry Red
        assertEquals("Gray", colorFamily("#3A3C3B"))    // a near-black DARK GRAY (L 0.23 > Black cutoff)
    }

    @Test fun `natural family catches creams and ivories`() {
        listOf("#FFFDD0", "#FFFFF0", "#F5F5DC", "#F0EAD6", "#E3DAC9", "#E8E0CE")
            .forEach { assertEquals("$it should be Natural", "Natural", colorFamily(it)) }
    }

    @Test fun `pure and off-white stay White`() {
        assertEquals("White", colorFamily("#FFFFFF"))
        assertEquals("White", colorFamily("#FAFAFA"))
    }

    // Residual edges locked as intentional (spec "Residual edge").
    @Test fun `saturated pale yellow is Yellow not Natural`() = assertEquals("Yellow", colorFamily("#FDFD96"))
    @Test fun `tan is a light Brown`() = assertEquals("Brown", colorFamily("#D2B48C"))
    @Test fun `pastel blue stays Blue`() = assertEquals("Blue", colorFamily("#AEC6CF"))

    @Test fun `absent or garbage hex is null`() {
        assertNull(colorFamily(null))
        assertNull(colorFamily(""))
        assertNull(colorFamily("not-a-hex"))
        assertNull(colorFamily("#12"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --tests *ColorFamilyTest" 2>&1 | tr -d '\r'
```
Expected: FAIL — `colorFamily` is unresolved (compile error).

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/works/mees/dinghy/ui/spool/ColorFamily.kt`:

```kotlin
package works.mees.dinghy.ui.spool

import works.mees.dinghy.spool.normalizeColorHex
import kotlin.math.abs

/**
 * Classify a filament color hex into one of the 12 fixed palette FAMILIES (the swatch names), or null
 * when the hex is absent/unparseable. PURE + host-testable: integer RGB → HSL/chroma, NO
 * android.graphics.Color (that lives only in display code, e.g. SpoolScreen.parseNormalizedHex).
 *
 * Replaces Spoolman's server-side CIE76 color_similarity matching: perceptual nearness to a SATURATED
 * swatch does not model "color family" — a muted olive is perceptually near gray/brown yet a human calls
 * it green (its nearest saturated swatch, Green, is ΔE 72 away). Hue-family bucketing matches how people
 * categorize color. The Natural family covers pale warm near-whites (cream/ivory/"natural" filament).
 * See docs/superpowers/specs/2026-06-18-spool-color-family-filter-design.md.
 */
internal fun colorFamily(hex: String?): String? {
    val rgb = parseRgbChannels(normalizeColorHex(hex) ?: return null) ?: return null
    val (r, g, b) = rgb
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val chroma = max - min                                   // 0..255
    val l = (max + min) / 2.0 / 255.0                        // HSL lightness 0..1
    val sHsl = if (chroma == 0) 0.0 else (chroma / 255.0) / (1.0 - abs(2 * l - 1))
    val hue = hueDegrees(r, g, b, max, chroma)               // 0..360

    // 1. Natural — pale, lightly-tinted warm near-white (cream/ivory/beige). Uses CHROMA (not HSL
    //    saturation, which is unstable near white: cream and pastel pink both report S≈1.0). Tested
    //    first so cream resolves to Natural, not White or Yellow.
    if (l > 0.80 && chroma in 12..70 && hue in 20.0..95.0) return "Natural"
    // 2. Neutral.
    if (sHsl < 0.15) return when {
        l < 0.22 -> "Black"
        l > 0.85 -> "White"
        else -> "Gray"
    }
    // 3. Brown — a dark/muted warm color, not a hue band; tested before the hue families.
    if (hue >= 20.0 && hue < 50.0 && (l < 0.45 || sHsl < 0.45)) return "Brown"
    // 4. Hue families.
    return when {
        hue < 15.0 || hue >= 345.0 -> "Red"
        hue < 45.0 -> "Orange"
        hue < 70.0 -> "Yellow"
        hue < 165.0 -> "Green"
        hue < 255.0 -> "Blue"
        hue < 290.0 -> "Purple"
        else -> "Pink"
    }
}

/** Hue in degrees [0,360) from integer channels; 0 for an achromatic (chroma 0) color. */
private fun hueDegrees(r: Int, g: Int, b: Int, max: Int, chroma: Int): Double {
    if (chroma == 0) return 0.0
    val hp = when (max) {
        r -> (g - b).toDouble() / chroma          // may be negative; wrapped by mod below
        g -> (b - r).toDouble() / chroma + 2.0
        else -> (r - g).toDouble() / chroma + 4.0
    }
    return (hp * 60.0).mod(360.0)
}

/** Parse the RGB triple from a normalized `#RRGGBB`/`#RRGGBBAA` hex; null if unparseable. Alpha ignored. */
private fun parseRgbChannels(normalizedHex: String): Triple<Int, Int, Int>? {
    val h = normalizedHex.removePrefix("#")
    if (h.length != 6 && h.length != 8) return null
    return runCatching {
        Triple(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
    }.getOrNull()
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the same command as Step 2. Expected: PASS (all ColorFamilyTest cases green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/spool/ColorFamily.kt \
        app/src/test/java/works/mees/dinghy/ui/spool/ColorFamilyTest.kt
git commit -m "feat(spool): add pure colorFamily hue classifier

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: SpoolPicker — add Natural, make palette the single source, drop Multi-color

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt`

This is a UI-mechanics change (no unit test — covered by build + on-device UAT). After it, `holder.applyMultiColor` is no longer called (still defined; removed in Task 3) and the grid is a clean 4×3 of 12 colors.

- [ ] **Step 1: Replace the palette + grid in SpoolPicker.kt**

Replace the `PALETTE_SWATCHES` declaration (`SpoolPicker.kt:37-49`) — make it `internal`, add Natural after White (groups with the neutrals; 12 entries = a clean 4×3):

```kotlin
/**
 * The fixed color-palette swatches (D-06). Single source of truth for both the picker grid AND the
 * seed-hint family lookup (SpoolHolder.seedPrefilter) — they must NOT drift. Tapping one buckets the
 * library by [colorFamily]; each hex self-classifies to its own name (ColorFamilyTest).
 */
internal val PALETTE_SWATCHES: List<Pair<String, String>> = listOf(
    "Black" to "#000000",
    "White" to "#FFFFFF",
    "Natural" to "#EDE6D6",
    "Gray" to "#808080",
    "Red" to "#FF0000",
    "Orange" to "#FF8000",
    "Yellow" to "#FFFF00",
    "Green" to "#00C000",
    "Blue" to "#0050FF",
    "Purple" to "#8000FF",
    "Pink" to "#FF60C0",
    "Brown" to "#7A4A20",
)
```

Delete the `MULTICOLOR_BRUSH` val (`SpoolPicker.kt:71-77`) and the `ColorChoice` sealed interface (`SpoolPicker.kt:65-69`) entirely.

Replace `ColorSwatchGrid` (`SpoolPicker.kt:79-123`) — drop `onMultiColor`, iterate `PALETTE_SWATCHES` directly:

```kotlin
/**
 * The Color selector as a FILL-TO-FIT grid (Matthew, 2026-06-04 — every swatch on ONE screen, no scroll).
 * 3 columns × the 12 named palette = 12 tiles = 4 full rows (no gaps); rows share the height via `weight`
 * so the grid always fits in any orientation. Per-tile layout is orientation-aware: portrait = swatch over
 * title; landscape = title to the LEFT of the swatch. Clearing color is the gutter Clear.
 */
@Composable
internal fun ColorSwatchGrid(
    selectedHex: String?,
    onTapSwatch: (String) -> Unit,
    t: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    val columns = 3
    BoxWithConstraints(modifier) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PALETTE_SWATCHES.chunked(columns).forEach { rowSwatches ->
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowSwatches.forEach { (name, hex) ->
                        ColorTile(
                            name = name,
                            hex = hex,
                            selectedHex = selectedHex,
                            onTapSwatch = onTapSwatch,
                            landscape = landscape,
                            t = t,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    repeat(columns - rowSwatches.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}
```

Replace `ColorSwatchCircle` (`SpoolPicker.kt:125-133`):

```kotlin
/** The swatch circle for a named palette color. */
@Composable
private fun ColorSwatchCircle(hex: String, t: ThemeTokens, modifier: Modifier) {
    Box(
        modifier.aspectRatio(1f).clip(CircleShape)
            .border(BorderStroke(2.dp, t.hair), CircleShape)
            .background(parseNormalizedHex(hex) ?: t.surface2),
    )
}
```

Replace `ColorTile` (`SpoolPicker.kt:139-202`) — take `name`/`hex` instead of `ColorChoice`:

```kotlin
/**
 * One color tile. Portrait = swatch over title (circle scales to row height); landscape = title to the LEFT
 * of the swatch (Matthew, 2026-06-04). Selected = accent outline + soft fill + accent label.
 */
@Composable
private fun ColorTile(
    name: String,
    hex: String,
    selectedHex: String?,
    onTapSwatch: (String) -> Unit,
    landscape: Boolean,
    t: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    val selected = selectedHex.equals(hex, ignoreCase = true)
    val shape = RoundedCornerShape(t.rCtrl)
    val tileMod = modifier
        .clip(shape)
        .border(BorderStroke(2.dp, if (selected) t.accentLine else t.outline), shape)
        .background(if (selected) t.accentSoft else Color.Transparent)
        .clickable { onTapSwatch(hex) }
        .padding(8.dp)
    val labelColor = if (selected) t.accent2 else t.text

    @Composable
    fun TileLabel(mod: Modifier) = Text(
        text = name,
        color = labelColor,
        style = DinghyType.caption.toTextStyle(t),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = mod,
    )

    if (landscape) {
        Row(
            tileMod,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileLabel(Modifier.weight(1f))
            ColorSwatchCircle(hex, t, Modifier.fillMaxHeight(0.7f))
        }
    } else {
        Column(
            tileMod,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            ColorSwatchCircle(hex, t, Modifier.fillMaxHeight(0.6f))
            TileLabel(Modifier)
        }
    }
}
```

Remove the now-unused `Brush`/`Color`(sweepGradient) import only if the IDE/compiler flags it unused; `Color.Transparent` is still used so keep `androidx.compose.ui.graphics.Color`.

- [ ] **Step 2: Remove `onMultiColor` plumbing in SpoolScreen.kt**

Delete the `onMultiColor` parameter from every function signature that declares it (`SpoolScreen.kt:200`, `:262`, `:505`) and every call site that passes it (`:227`, `:402`, `:544`). At the top-level wiring (`SpoolScreen.kt:148-149`) remove the `onMultiColor = { … applyMultiColor() … }` lambda. The `ColorSwatchGrid(...)` call (`:541-547`) drops its `onMultiColor = onMultiColor,` argument.

To find them all precisely:
```bash
grep -n "onMultiColor" app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
```
Remove every line/arg the grep reports.

- [ ] **Step 3: Build to verify it compiles**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL. (`SpoolFilters.MULTICOLOR` and `applyMultiColor` still exist in SpoolHolder — removed next task — so nothing dangles.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt \
        app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
git commit -m "feat(spool): add Natural swatch, drop Multi-color tile (clean 4x3 grid)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Remove the multi-color filter from the holder

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt`

- [ ] **Step 1: Delete `applyMultiColor` and the `MULTICOLOR` sentinel**

Delete the `applyMultiColor()` function (`SpoolHolder.kt:430-443`, including its KDoc at `:423-429`). Delete the `MULTICOLOR` companion const in `SpoolFilters` (`SpoolHolder.kt:92-97`, the KDoc + `const val MULTICOLOR`).

- [ ] **Step 2: Verify no references remain**

Run:
```bash
grep -rn "applyMultiColor\|MULTICOLOR" app/src/main app/src/test
```
Expected: NO matches. (Task 2 already removed the UI references.)

- [ ] **Step 3: Build**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt
git commit -m "refactor(spool): remove multi-color filter (subsumed by per-sub-color classify)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Rework `applyColorSwatch` to classify client-side

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderColorTest.kt` (create)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderColorTest.kt`:

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
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanStatus

/**
 * Holder-level tests for the client-side color-family filter ([SpoolHolder.applyColorSwatch]).
 * Replaces the old Spoolman CIE76 similarity fetch. See
 * docs/superpowers/specs/2026-06-18-spool-color-family-filter-design.md.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolHolderColorTest {

    private val noActiveSpool = MutableStateFlow<SpoolmanStatus?>(null)

    private fun proxyEnvelope(rowsJson: String): JsonElement =
        MoonrakerJson.parseToJsonElement(
            """{"response":$rowsJson,"error":null,"response_headers":{"X-Total-Count":"99"}}""",
        )

    /** A library: olive(Green), pure red(Red), a multicolor with red+green, sky blue(Blue). */
    private val colorClient: SpoolmanClient = object : SpoolmanClient {
        override suspend fun listFilaments(query: String?): JsonElement = proxyEnvelope(
            """[
                {"id":1,"name":"Olive","color_hex":"64794b"},
                {"id":2,"name":"Red","color_hex":"ff0000"},
                {"id":3,"name":"Rainbow","multi_color_hexes":"ff0000,00c000"},
                {"id":4,"name":"Sky","color_hex":"5dc0f0"}
            ]""",
        )
        // listSpools left default (null) — refresh() degrades to empty; we assert on filter ids.
    }

    private fun holder(client: SpoolmanClient) = SpoolHolder(
        scope = TestScope(UnconfinedTestDispatcher()),
        client = client,
        activeSpool = noActiveSpool,
    )

    @Test fun `tapping Green selects solid green AND a multicolor containing green`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000") // Green swatch
            assertEquals(listOf(1, 3), h.state.value.filters.colorFilamentIds)
            assertEquals("#00C000", h.state.value.filters.colorSwatchHex)
        }

    @Test fun `tapping Red selects the red and the multicolor`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#FF0000")
            assertEquals(listOf(2, 3), h.state.value.filters.colorFilamentIds)
        }

    @Test fun `a family with no matches yields an empty (unmatchable) id list`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#FFFF00") // Yellow — none of the library is yellow
            assertEquals(emptyList<Int>(), h.state.value.filters.colorFilamentIds)
        }

    @Test fun `re-tapping an active swatch clears the color filter`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000")
            h.applyColorSwatch("#00C000") // re-tap
            assertNull(h.state.value.filters.colorFilamentIds)
            assertNull(h.state.value.filters.colorSwatchHex)
        }

    @Test fun `a failed fetch leaves the filter UNAPPLIED, not an empty list`() =
        runTest(UnconfinedTestDispatcher()) {
            val failing = object : SpoolmanClient {} // listFilaments default = null (failure)
            val h = holder(failing)
            h.applyColorSwatch("#00C000")
            assertNull("fetch failure must not collapse the list", h.state.value.filters.colorFilamentIds)
            assertNull(h.state.value.filters.colorSwatchHex)
        }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --tests *SpoolHolderColorTest" 2>&1 | tr -d '\r'
```
Expected: FAIL — the old code ignores the family and maps every returned id (the fake returns all 4 rows regardless of query), so a Green tap yields `[1,2,3,4]` instead of `[1,3]`; and the failed-fetch case returns `[]` instead of null (current code can't distinguish failure from empty).

- [ ] **Step 3: Rework `applyColorSwatch`**

Replace `applyColorSwatch` (`SpoolHolder.kt:403-421`, incl. its KDoc at `:396-402`) with:

```kotlin
    /**
     * D-06: apply a color-FAMILY filter. The tapped swatch declares a family (via [colorFamily] on its own
     * hex); fetch the whole filament library, keep ids whose color(s) classify to that family, and fold
     * them into the spool list as `filament.id=<csv>`. A multicolor filament matches if ANY of its
     * sub-colors fits. A re-tap of the ACTIVE swatch (a real hard filter) clears; a tap on a hint-only
     * highlight (colorSwatchHex set with no ids — a gcode seed) APPLIES. A failed fetch leaves the color
     * filter UNAPPLIED rather than collapsing the list to nothing.
     */
    suspend fun applyColorSwatch(swatchHex: String) {
        val current = _state.value
        // Re-tap clears only an ACTIVE hard filter; a hint-only highlight is not a re-tap (it applies).
        if (current.filters.colorFilamentIds != null &&
            current.filters.colorSwatchHex.equals(swatchHex, ignoreCase = true)
        ) {
            _state.update { it.copy(filters = it.filters.copy(colorFilamentIds = null, colorSwatchHex = null)) }
            refresh()
            return
        }
        val targetFamily = colorFamily(swatchHex)
        val envelope = runCatching { client.listFilaments("limit=$FILAMENT_LIMIT") }.getOrNull()
        if (envelope == null) {
            // Fetch FAILED: leave the filter unapplied (distinct from a successful zero-match result).
            _state.update { it.copy(filters = it.filters.copy(colorFilamentIds = null, colorSwatchHex = null)) }
            refresh()
            return
        }
        val ids = parseSpoolmanFilaments(envelope).rows
            .filter { f -> f.colorSwatches.any { colorFamily(it) == targetFamily } }
            .mapNotNull(SpoolmanFilament::id)
        _state.update {
            it.copy(filters = it.filters.copy(colorFilamentIds = ids, colorSwatchHex = swatchHex))
        }
        refresh()
    }
```

In the companion (`SpoolHolder.kt:570-579`): delete `const val COLOR_SIMILARITY_THRESHOLD = 20` and its KDoc; change `const val FILAMENT_LIMIT = 50` to `const val FILAMENT_LIMIT = 1000` and update its comment to "the whole-library classification fetch cap (see spec Fetch cap)".

- [ ] **Step 4: Run the test to verify it passes**

Run the Step 2 command. Expected: PASS (all SpoolHolderColorTest cases).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt \
        app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderColorTest.kt
git commit -m "feat(spool): classify color filter client-side by hue family

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Seed hint via `colorFamily`; remove `nearestPaletteSwatch`/`PREFILTER_PALETTE`/`parseRgb`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderColorTest.kt` (extend)

- [ ] **Step 1: Add failing tests for the seed hint + hint-apply**

Append to `SpoolHolderColorTest.kt` (inside the class):

```kotlin
    @Test fun `seedPrefilter maps an olive file color to the GREEN hint swatch (not Gray)`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.seedPrefilter(SpoolPrefilterSeed(filamentColors = listOf("#64794b")))
            // Hint only: highlight set to Green's canonical swatch hex, NO hard filter yet.
            assertEquals("#00C000", h.state.value.filters.colorSwatchHex)
            assertNull(h.state.value.filters.colorFilamentIds)
        }

    @Test fun `tapping a seed-highlighted swatch APPLIES the filter (does not clear)`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.seedPrefilter(SpoolPrefilterSeed(filamentColors = listOf("#64794b"))) // highlight Green, ids null
            h.applyColorSwatch("#00C000")
            assertEquals(listOf(1, 3), h.state.value.filters.colorFilamentIds) // applied, not cleared
        }
```

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --tests *SpoolHolderColorTest" 2>&1 | tr -d '\r'
```
Expected: FAIL — the seed currently maps olive to Gray via `nearestPaletteSwatch` (RGB distance).

- [ ] **Step 2: Rewrite the seed color hint**

In `seedPrefilter` (`SpoolHolder.kt:495-514`), replace the `colorHint` computation (`:504-506`):

```kotlin
        // Color is a HINT (D-04/D-06): map the file's first valid color to its palette FAMILY (the same
        // classifier the filter uses), then to that family's canonical swatch hex — so the pre-selected
        // highlight AGREES with what the filter would match. NOT folded into a hard filament.id filter.
        val colorHint = seed.filamentColors
            .firstNotNullOfOrNull { normalizeColorHex(it) }
            ?.let { colorFamily(it) }
            ?.let { fam -> PALETTE_SWATCHES.firstOrNull { it.first == fam }?.second }
```

- [ ] **Step 3: Delete the dead RGB-distance code**

Delete `nearestPaletteSwatch` (`SpoolHolder.kt:653-668`, incl. KDoc), `PREFILTER_PALETTE` (`:635-651`, incl. KDoc), and `parseRgb` (`:670-681`). Verify nothing else references them:

```bash
grep -rn "nearestPaletteSwatch\|PREFILTER_PALETTE\|parseRgb" app/src/main app/src/test
```
Expected: NO matches (the new `ColorFamily.kt` has its own private `parseRgbChannels`, a different name).

- [ ] **Step 4: Run tests**

Run the Step 1 command, plus the whole color suite. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt \
        app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderColorTest.kt
git commit -m "fix(spool): seed color hint via colorFamily; drop RGB-nearest palette match

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Update the test fake's filament seed

**Files:**
- Modify: `app/src/test/java/works/mees/dinghy/spool/FakeSpoolmanClient.kt`

- [ ] **Step 1: Reseed the filament key**

`applyColorSwatch` now fetches `listFilaments("limit=1000")`, not `color_hex=ff0000`. Change the seeded key (`FakeSpoolmanClient.kt:51-52`) from:

```kotlin
        // GET /v1/filament (color red) → the red-filament list capture (X-Total-Count "7").
        ProxyKey("GET", "/v1/filament", "color_hex=ff0000") to "spoolman-live-ender5-proxy-color-red-filaments.json",
```
to:
```kotlin
        // GET /v1/filament?limit=1000 → the filament list capture (client-side color-family classify).
        ProxyKey("GET", "/v1/filament", "limit=1000") to "spoolman-live-ender5-proxy-color-red-filaments.json",
```

- [ ] **Step 2: Confirm no test asserts the old query**

```bash
grep -rn "color_hex=ff0000\|color_similarity" app/src/test
```
Expected: NO matches. If any test references the old `color_hex` query string, retire that assertion (there is no Kotlin test exercising it today; the holder color behavior is covered by `SpoolHolderColorTest`).

- [ ] **Step 3: Run the full unit-test suite**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/works/mees/dinghy/spool/FakeSpoolmanClient.kt
git commit -m "test(spool): reseed fake filament fetch to limit=1000 shape

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Update docs to the new client-side contract

**Files:**
- Modify: `docs/view_specific_notes/spoolman.md`
- Modify: `docs/view_specific_notes/spoolman_live_validation.md`
- Modify: `docs/ui_design/COMPONENTS.md`

- [ ] **Step 1: spoolman.md — replace the "close-enough color filter" section**

At `docs/view_specific_notes/spoolman.md` around the "Close-enough color filter" block (line ~265-275, the `color_hex=…&color_similarity_threshold=20` example), replace it with a description of the new flow: fetch `GET /api/v1/filament?limit=1000`, classify each filament's color(s) into one of the 12 fixed palette families client-side (`colorFamily`), then fetch spools by the matching `filament.id=<csv>`. Note the swatch list is now 12 (Black, White, Natural, Gray, Red, Orange, Yellow, Green, Blue, Purple, Pink, Brown) and the Multi-color tile was removed (multicolor spools match via their sub-colors). Point to the spec.

- [ ] **Step 2: spoolman_live_validation.md — mark the color-filter section historical**

At `docs/view_specific_notes/spoolman_live_validation.md:~384` (the swatch/color_hex validation note), add a leading note: "**HISTORICAL (pre-2026-06-18):** color filtering moved from Spoolman's server-side `color_similarity_threshold` to client-side hue-family classification — see `docs/superpowers/specs/2026-06-18-spool-color-family-filter-design.md`." Leave the capture description intact (the fixtures are still valid filament data).

- [ ] **Step 3: COMPONENTS.md — update the color-filter component note**

At `docs/ui_design/COMPONENTS.md:~488` (color-filter / swatch note), update any "slow two-step / color_similarity" wording to "12-family client-side classification; no per-tap similarity round-trip beyond the one-time library fetch," and reflect the 12-swatch / no-Multi-color grid.

- [ ] **Step 4: Commit**

```bash
git add docs/view_specific_notes/spoolman.md docs/view_specific_notes/spoolman_live_validation.md docs/ui_design/COMPONENTS.md
git commit -m "docs(spool): describe client-side color-family filter; retire similarity contract

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Full green gate (suite + release/R8)

**Files:** none (verification only).

- [ ] **Step 1: Full unit-test suite**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL, 0 failures. If any conformance test (FontConformanceTest, CommandCatalogDriftTest, etc.) trips, fix per its message — this change touches no commands/fonts, so none is expected.

- [ ] **Step 2: Release build (R8) to catch shrinker/keep-rule regressions**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleRelease --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Confirm the working tree is clean (no uncommitted fix masking HEAD)**

Run:
```bash
git status --porcelain
```
Expected: empty output. (Guards `[[dinghy-uncommitted-fix-masks-broken-head]]`.)

- [ ] **Step 4: No commit** — verification task. Proceed to on-device UAT (install the matching split-ABI APK on BOTH flox and moto per `[[dinghy-test-devices]]`; tap Green and confirm the olive spool appears, tap Natural, confirm the grid is a clean 4×3 with no Multi-color tile).

---

## Self-Review (completed by plan author)

- **Spec coverage:** classifier (T1), Natural family + grid + Multi-color removal (T2/T3), client-side selection + fetch cap + failure-vs-empty (T4), seed-hint consistency + re-tap guard + dead-code removal (T5), fake/docs updates (T6/T7), green gate (T8). Removed-list (`COLOR_SIMILARITY_THRESHOLD`, multi-color, `nearestPaletteSwatch`, `PREFILTER_PALETTE`) all covered. ✓
- **Type consistency:** `colorFamily(hex: String?): String?` used identically in T2/T4/T5; `PALETTE_SWATCHES: List<Pair<String,String>>` (internal) referenced in T2 (grid) and T5 (seed). `colorFilamentIds`/`colorSwatchHex` semantics consistent across T4/T5 tests and impl. ✓
- **Placeholders:** none — full code in every implementation step. ✓
- **Compile-safe ordering:** verified per-task (T2 leaves `applyMultiColor` defined-but-unused until T3; new `parseRgbChannels` name avoids clashing with the not-yet-removed `parseRgb`). ✓
