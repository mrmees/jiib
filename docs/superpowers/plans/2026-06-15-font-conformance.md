# Font Conformance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a named semantic text-role layer (`DinghyType`) that owns `(family, size, weight)`, route every text call site through it across both Compose and the four classic-Views surfaces, and enforce conformance with a build-failing drift test.

**Architecture:** A toolkit-neutral `TextRole` data type + a `DinghyType` catalog of 11 named roles is the single source of truth. Compose consumes roles via a `TextRole.toTextStyle(t)` extension (plus a `FocusHeroText` shrink-to-fit composable); the four text-bearing Views surfaces (Console, Files, GraphView, WebcamView) read the same `baseSp`/family from the role objects. A `FontConformanceTest` scans `app/src/main` and fails on any `fontFamily =`/`fontSize =` outside a small allowlist.

**Tech Stack:** Kotlin, Jetpack Compose (`androidx.compose.ui.text`), classic Views (`android.graphics.Paint`/`TextView` via `ResourcesCompat.getFont`), JUnit4 host tests. Build is Windows-side via `E:\Android\gw.bat` (see CLAUDE.md "Local Build Environment").

**Build/test command (from repo root, WSL):**
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
```
The process exit code is authoritative. On-device UAT installs both ABI slices to flox (id `0a64b42e`) and moto (id `ZY22LBDRM9`) — see memory `dinghy-test-devices`.

**Branch:** Work on a feature branch (e.g. `font-conformance`), not `master`.

**Sanctioned ramp tiers (THEMING.md §R11):** `15 / 20 / 22 / 24 / 26 / 28+`. `focusHero` is `40 max → 15 min` shrink-to-fit. Any other base (11/12/13/16/17/18/23/27/30/34…) is non-conformant and snaps to the role's tier.

---

## File Structure

**New files:**
- `app/src/main/java/works/mees/dinghy/theme/DinghyType.kt` — neutral `TypeRole` enum, `TextRole` data class, `DinghyType` catalog (no Compose-UI rendering import beyond `FontWeight`).
- `app/src/main/java/works/mees/dinghy/theme/compose/DinghyTextStyle.kt` — Compose `TextRole.toTextStyle(t)` extension + `FocusHeroText` composable.
- `app/src/test/java/works/mees/dinghy/theme/DinghyTypeTest.kt` — unit tests for the catalog (all bases on ramp tiers, families correct).
- `app/src/test/java/works/mees/dinghy/theme/FontConformanceTest.kt` — source-scan drift test (reporting → failing).

**Modified (Compose sweep — group by area):**
- designsystem: `FocusFrame.kt`, `ListRow.kt`, `ToggleRow.kt`, `SelectorRow.kt`, `Scrubber.kt`, `AdjusterPanel.kt`, `FillMeter.kt`, `control/OutlinedControl.kt`, `ConfirmGuard.kt`, `SeverityToast.kt`
- printstatus: `PrintStatusFocus.kt`, `PrintStatusField.kt`, `PrintStatusScreen.kt`
- calibration: `CalibrationHubScreen.kt`, `BedMeshScreen.kt`, `TiltScreen.kt`, `ScrewsTiltScreen.kt`, `ProbeCalibrateScreen.kt`
- motion/io: `move/MoveScreen.kt`, `extrude/ExtrudeScreen.kt`, `outputs/OutputsScreen.kt`, `outputs/OutputFocusControl.kt`, `outputs/OutputToggleControl.kt`, `finetune/FineTuneScreen.kt`
- screens: `screen/SettingsScreen.kt`, `screen/AboutScreen.kt`, `screen/PrintersScreen.kt`, `screen/ThemeEditorScreen.kt`, `screen/TokenTextField.kt`, `spool/SpoolScreen.kt`, `spool/scan/ScanSurface.kt`, `spool/scan/ScanConfirmCard.kt`, `macros/BookmarkedMacrosScreen.kt`, `prompt/PromptDialog.kt`, `prompt/PromptContentItems.kt`, `prompt/PromptMarkupText.kt`, `prompt/PromptImageItem.kt`
- bench (shipping): `bench/ComposeBenchScene.kt`
- (Any other `app/src/main` file the reporter flags — the reporter is the completeness oracle.)

**Modified (Views wiring):**
- `ui/console/ConsoleRowsAdapter.kt`, `ui/files/FileRowsAdapter.kt`, `render/GraphView.kt`, `render/WebcamView.kt`

**Modified (docs, final task):**
- `docs/ui_design/THEMING.md` (§R11 ramp table gains a role column), `docs/ui_design/COMPONENTS.md` (add `DinghyType` entry).

**Allowlist (exempt from FontConformanceTest):**
- `theme/DinghyType.kt`, `theme/compose/DinghyTextStyle.kt` (role plumbing — the only place `fontFamily`/`fontSize` are written for text)
- `designsystem/MaterialSymbol.kt` (icon glyph system — Material Symbols font, not text; icon law)
- `ui/prompt/PromptMarkupText.kt` (macro-author markup renderer — its `sizeSpan` builds `SpanStyle(fontSize=…)` for the author's `<size:small/normal/large/x-large>` ladder; this is the SIZE analog of the sanctioned `<color:#hex>` author carve-out, THEMING.md §D-03. The base run is role-routed (`body`); the author size ladder is content, not chrome.)
- the four Views surfaces above (they set `Paint.typeface`/`textSize`, not Compose `fontFamily =`/`fontSize =`, but are listed for clarity and for the `fsSp` rule)
- `preview/**`, `gallery/**` (non-shipping dev surfaces)
- **NOT `bench/**`** — `BenchActivity` is an *exported, shipping* Activity (`AndroidManifest.xml:99`) and `ComposeBenchScene.kt` has inline `fontSize =` sites. It must be swept, not allowlisted (Task 9).

---

## Task 1: The neutral role layer (`TextRole` + `DinghyType`)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/theme/DinghyType.kt`
- Test: `app/src/test/java/works/mees/dinghy/theme/DinghyTypeTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.theme

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DinghyTypeTest {

    private val sanctionedTiers = setOf(15f, 20f, 22f, 24f, 26f)

    @Test
    fun everyFixedRoleSitsOnASanctionedRampTier() {
        val offenders = DinghyType.all
            .filter { it.maxSp == null } // shrink-to-fit roles validated separately
            .filterNot { it.baseSp in sanctionedTiers }
        assertTrue("Roles off the ramp: ${offenders.map { it.baseSp }}", offenders.isEmpty())
    }

    @Test
    fun focusHeroShrinksBetweenFloorAndMax() {
        val hero = DinghyType.focusHero
        assertEquals(40f, hero.maxSp)
        assertEquals(15f, hero.minSp)
        assertEquals(TypeRole.Data, hero.role)
    }

    @Test
    fun dataRolesAreDataUiRolesAreUi() {
        assertEquals(TypeRole.Data, DinghyType.statValue.role)
        assertEquals(TypeRole.Data, DinghyType.dataInline.role)
        assertEquals(TypeRole.Data, DinghyType.consoleLine.role)
        assertEquals(TypeRole.Ui, DinghyType.listLabel.role)
        assertEquals(TypeRole.Ui, DinghyType.caption.role)
    }

    @Test
    fun captionIsAtTheMetadataFloor() {
        assertEquals(15f, DinghyType.caption.baseSp)
        assertEquals(15f, DinghyType.dataMeta.baseSp)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `…gw.bat :app:testDebugUnitTest --tests "*DinghyTypeTest" --no-daemon`
Expected: FAIL — `DinghyType` / `TypeRole` / `TextRole` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package works.mees.dinghy.theme

import androidx.compose.ui.text.font.FontWeight

/** Which font family a role draws in. Ui → [Geist]; Data → [GeistMono] (values that came from the printer). */
enum class TypeRole { Ui, Data }

/**
 * One named text class — the single source of truth for `(family, size, weight)`. The concrete
 * [androidx.compose.ui.text.font.FontFamily] / [android.graphics.Typeface] are DERIVED from [role]
 * at each toolkit boundary, never stored here, so this type carries no rendering dependency (only the
 * plain [FontWeight] value). See `docs/ui_design/THEMING.md` §"The type ramp".
 *
 * Sizes are base sp values; the renderer scales them with `fsSp(baseSp, fs)` for the S/M/L `--fs`
 * setting. [maxSp]/[minSp] are set ONLY for shrink-to-fit roles ([DinghyType.focusHero]).
 */
data class TextRole(
    val role: TypeRole,
    val baseSp: Float,
    val weight: FontWeight,
    val maxSp: Float? = null,
    val minSp: Float? = null,
)

/**
 * The app's named text classes (R11 type ramp made enforceable). A call site says WHAT the text is;
 * the role owns the family/size/weight. Adding/retuning a tier is a one-line edit here.
 */
object DinghyType {
    // Geist — system / UI text
    val screenTitle = TextRole(TypeRole.Ui, 22f, FontWeight.SemiBold)
    val focusHeader = TextRole(TypeRole.Ui, 20f, FontWeight.SemiBold)
    val listLabel   = TextRole(TypeRole.Ui, 20f, FontWeight.SemiBold)
    val buttonLabel = TextRole(TypeRole.Ui, 20f, FontWeight.SemiBold)
    val body        = TextRole(TypeRole.Ui, 20f, FontWeight.Medium)
    val caption     = TextRole(TypeRole.Ui, 15f, FontWeight.Medium)

    // Geist Mono — live printer data
    val focusHero   = TextRole(TypeRole.Data, baseSp = 40f, weight = FontWeight.Bold, maxSp = 40f, minSp = 15f)
    val statValue   = TextRole(TypeRole.Data, 26f, FontWeight.SemiBold)
    val dataInline  = TextRole(TypeRole.Data, 20f, FontWeight.Medium)
    val dataMeta    = TextRole(TypeRole.Data, 15f, FontWeight.Medium)
    val consoleLine = TextRole(TypeRole.Data, 15f, FontWeight.Medium)

    /** All roles, for tests and tooling. */
    val all: List<TextRole> = listOf(
        screenTitle, focusHeader, listLabel, buttonLabel, body, caption,
        focusHero, statValue, dataInline, dataMeta, consoleLine,
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `…gw.bat :app:testDebugUnitTest --tests "*DinghyTypeTest" --no-daemon`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/theme/DinghyType.kt \
        app/src/test/java/works/mees/dinghy/theme/DinghyTypeTest.kt
git commit -m "feat(type): add neutral TextRole + DinghyType catalog (font conformance)"
```

---

## Task 2: Compose consumption — `toTextStyle` + `FocusHeroText`

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/theme/compose/DinghyTextStyle.kt`
- Test: `app/src/test/java/works/mees/dinghy/theme/DinghyTypeTest.kt` (add cases) — *Compose `TextStyle` is constructible on the JVM host without an Android instrumentation context, so this stays a unit test.*

- [ ] **Step 1: Add the failing test**

Append to `DinghyTypeTest.kt`:

```kotlin
    @Test
    fun toTextStyleResolvesFamilyAndScaledSize() {
        val tokens = TokensDark.copy(fs = 1.0f) // baked default-seed fail-safe (theme/BakedTokens.kt)
        val style = DinghyType.statValue.toTextStyle(tokens)
        assertEquals(GeistMono, style.fontFamily)
        assertEquals(26f, style.fontSize.value)   // fs = 1.0 → 26 * 1.0
        assertEquals(FontWeight.SemiBold, style.fontWeight)

        val uiStyle = DinghyType.listLabel.toTextStyle(tokens)
        assertEquals(Geist, uiStyle.fontFamily)
    }
```

> **Tokens builder (verified):** use `TokensDark.copy(fs = 1.0f)` — `TokensDark` is the baked default-seed `ThemeTokens` fail-safe in `theme/BakedTokens.kt:40`, and `.copy(fs = …)` is exactly how `ThemeResolver.kt:205` and `PromptStyleColorsTest.kt:29` build host-test tokens. Do NOT invent a builder. Import `TokensDark`, `Geist`, `GeistMono` from `works.mees.dinghy.theme`. `TextUnit.value` is a plain JVM float — `style.fontSize.value` is a valid host assertion.

- [ ] **Step 2: Run test to verify it fails**

Run: `…gw.bat :app:testDebugUnitTest --tests "*DinghyTypeTest" --no-daemon`
Expected: FAIL — `toTextStyle` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package works.mees.dinghy.theme.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.TextRole
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.TypeRole
import works.mees.dinghy.theme.fsSp

/**
 * Bake a [TextRole] into a Compose [TextStyle] for the active theme. The ONLY place outside the role
 * plumbing that a text `fontFamily`/`fontSize` is set (FontConformanceTest enforces this). For the
 * shrink-to-fit [works.mees.dinghy.theme.DinghyType.focusHero], use [FocusHeroText] instead.
 */
fun TextRole.toTextStyle(t: ThemeTokens): TextStyle = TextStyle(
    fontFamily = if (role == TypeRole.Ui) Geist else GeistMono,
    fontSize = fsSp(baseSp, t.fs).sp,
    fontWeight = weight,
)

/**
 * The Focus region's primary value: renders at [TextRole.maxSp] when there's room and steps DOWN to
 * [TextRole.minSp] to fit, never up — the sanctioned shrink-to-fit pattern (THEMING.md §"Shrink-to-fit
 * text"). Pass the focusHero role (or any role carrying max/min). Centered by default.
 */
@Composable
fun BoxScope.FocusHeroText(
    text: String,
    role: TextRole,
    t: ThemeTokens,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    val max = (role.maxSp ?: role.baseSp)
    val min = (role.minSp ?: role.baseSp)
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = if (role.role == TypeRole.Ui) Geist else GeistMono,
            fontWeight = role.weight,
            color = color,
            textAlign = textAlign,
        ),
        maxLines = 1,
        softWrap = false,   // REQUIRED — mirrors MoveScreen:505-512 precedent; without it the text
                            // wraps before auto-size kicks in on a narrow Focus region
        autoSize = TextAutoSize.StepBased(
            minFontSize = fsSp(min, t.fs).sp,
            maxFontSize = fsSp(max, t.fs).sp,
            stepSize = 1.sp,
        ),
    )
}
```

> **API confirmed:** `BasicText(..., maxLines, softWrap, autoSize = TextAutoSize.StepBased(minFontSize, maxFontSize, stepSize))` is exactly the call shape in `ui/move/MoveScreen.kt:505-512` (the Move XYZ readout) on this BOM. Mirror it precisely — same param order, `stepSize = 1.sp`. The Move readout's legacy `minFontSize = fsSp(11f…)` is the non-conformant floor we are replacing with the role's 15.

- [ ] **Step 4: Run test to verify it passes**

Run: `…gw.bat :app:testDebugUnitTest --tests "*DinghyTypeTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/theme/compose/DinghyTextStyle.kt \
        app/src/test/java/works/mees/dinghy/theme/DinghyTypeTest.kt
git commit -m "feat(type): Compose toTextStyle + FocusHeroText shrink-to-fit"
```

---

## Task 3: `FontConformanceTest` in reporting mode

**Files:**
- Create: `app/src/test/java/works/mees/dinghy/theme/FontConformanceTest.kt`

The test scans `app/src/main` Kotlin source. Two scanned invariants:
- **A — `fontFamily =`** appears only in allowlisted files (forces family via roles).
- **B — `fontSize =`** appears only in allowlisted files (forces size via roles; covers both bare `.sp` literals and `fsSp(...)`).

Non-text `fsSp(...)` (icon/box sizing) is intentionally NOT flagged — only `fontSize =`/`fontFamily =`.

- [ ] **Step 1: Write the test (reporting mode — collects but does not fail yet)**

```kotlin
package works.mees.dinghy.theme

import org.junit.Test
import java.io.File

/**
 * Font conformance guard (THEMING.md §R11 / 2026-06-15 design). Text family & size must come from
 * DinghyType roles, never inline. Allowlisted files own the role plumbing / are non-shipping.
 *
 * REPORTING MODE: prints offenders, does not fail — flip [ENFORCE] to true once the sweep completes
 * (final task). Keep this file in sync with the allowlist in the plan.
 */
class FontConformanceTest {

    private val enforce = ENFORCE

    private val allowlist = listOf(
        "theme/DinghyType.kt",
        "theme/compose/DinghyTextStyle.kt",
        "designsystem/MaterialSymbol.kt",
        "ui/console/ConsoleRowsAdapter.kt",
        "ui/files/FileRowsAdapter.kt",
        "render/GraphView.kt",
        "render/WebcamView.kt",
        "/preview/", "/gallery/",   // NOT /bench/ — BenchActivity ships (manifest), gets swept
    )

    private val patterns = listOf(
        Regex("""\bfontFamily\s*="""),
        Regex("""\bfontSize\s*="""),
    )

    @Test
    fun textFamilyAndSizeComeFromRoles() {
        val mainDir = mainSrcDir()
        val offenders = mainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f -> allowlist.none { f.invariantPath().contains(it) } }
            .flatMap { f ->
                f.readLines().withIndex().filter { (_, line) ->
                    patterns.any { it.containsMatchIn(line) }
                }.map { (i, line) -> "${f.invariantPath()}:${i + 1}: ${line.trim()}" }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            println("FONT CONFORMANCE: ${offenders.size} inline font sites remaining:")
            offenders.forEach { println("  $it") }
        }
        if (enforce) {
            assert(offenders.isEmpty()) {
                "Inline font sites must use DinghyType roles:\n${offenders.joinToString("\n")}"
            }
        }
    }

    private fun File.invariantPath(): String = path.replace('\\', '/')

    private fun mainSrcDir(): File {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        var dir: File? = File(userDir).canonicalFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/works/mees/dinghy")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("main source dir not found from $userDir")
    }

    private companion object { const val ENFORCE = false }
}
```

- [ ] **Step 2: Run it — capture the baseline offender list**

Run: `…gw.bat :app:testDebugUnitTest --tests "*FontConformanceTest" --no-daemon 2>&1 | tr -d '\r'`
Expected: PASS (reporting mode), with a printed list of ~220+ offenders. **Save this count** — it is the sweep's burn-down target (→ 0).

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/works/mees/dinghy/theme/FontConformanceTest.kt
git commit -m "test(type): FontConformanceTest in reporting mode (baseline)"
```

---

## Sweep execution model (CORRECTION after Task 3 baseline)

The Task 3 reporter found **414** inline font sites (each site is usually a `fontFamily =` line + a `fontSize =` line, so ~2× the ~224 styles). The baseline area breakdown also revealed screens the original file-lists missed: `ui/temperature/` (14), `ui/systeminfo/` (8), `ui/shell/DevThemeCyclerOverlay.kt` (4), and the Compose wrappers inside the Views areas — `ui/console/ConsoleScreen.kt` (6), `ui/files/FilesScreen.kt` (19), `ui/webcam/WebcamScreen.kt` (6), `render/Media3SurfaceHost.kt` (6) — which are NOT the 4 allowlisted Views surfaces.

**Therefore each sweep task is scoped by AREA and driven by the reporter, not a hand-typed file list:** a sweep task owns one or more directory prefixes; it runs `FontConformanceTest`, sweeps every flagged `.kt` under its area(s), and re-runs the reporter to prove its area is at **0**. The rebalanced area assignment (≈ counts):

| Task | Area(s) | ~sites |
|---|---|---|
| T4 | `designsystem/` (components + control + root) | 40 |
| T5 | `ui/printstatus/` + `ui/temperature/` | 35 |
| T6 | `ui/calibration/` | 51 |
| T7 | `ui/move/` + `ui/extrude/` + `ui/outputs/` + `ui/finetune/` | 55 |
| T8 | `ui/spool/` (incl. `scan/`) | 73 |
| T9 | `ui/screen/` + `ui/systeminfo/` | 80 |
| T10 | `ui/macros/` + `ui/prompt/` | 37 |
| T11 | mop-up: `ui/shell/`, `bench/`, and the non-allowlisted Compose files under `ui/console/`, `ui/files/`, `ui/webcam/`, `render/` (`Media3SurfaceHost`: if its text is `Paint`-drawn Views text, allowlist it with a justifying comment instead of forcing a role) | ~43 |
| T12 | Views wiring (was Task 10) | — |
| T13 | Enforce + docs (was Task 11) | — |

`PromptMarkupText` author-hex carve-out and the `ScanSurface`/`ScanConfirmCard` text still apply (T8/T10).

## The sweep recipe (applies to the T4–T11 sweep tasks)

For each file, replace every inline text style with a role. **The deterministic rule:**

**1. Decide the family by what the text IS:** a value that came from the printer (filename, temperature/sensor value, coordinate, console line, gcode field, progress %) → a **Data** role; everything else (labels, titles, button text, captions, units, prose) → a **Ui** role.

**2. Decide the role by the text's job:**

| The text is… | Role |
|---|---|
| a screen/section title | `screenTitle` |
| a FocusFrame header title | `focusHeader` |
| a list-row primary label | `listLabel` |
| a button / foot-bar label | `buttonLabel` |
| primary reading/paragraph text | `body` |
| timestamp / fine print / unit suffix / secondary | `caption` |
| the Focus region's hero value | `focusHero` (via `FocusHeroText`) |
| a tabular stat readout | `statValue` |
| a data value inline in a row/line (filename, reading) | `dataInline` |
| a small data value in a metadata slot | `dataMeta` |
| console scrollback | `consoleLine` |

**3. Apply the transform (Compose):**

```kotlin
// BEFORE
Text(name, color = t.text, fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = fsSp(20f, t.fs).sp)
// AFTER
Text(name, color = t.text, style = DinghyType.listLabel.toTextStyle(t))
```
- Keep `color`, `maxLines`, `overflow`, `modifier`, `textAlign` etc. — only family/weight/size move into the role.
- If a call already passes a `style = TextStyle(...)`, merge: `style = DinghyType.body.toTextStyle(t).copy(textAlign = …)`.
- For the Focus hero value, switch to `FocusHeroText(value, DinghyType.focusHero, t, color = …)` inside the `Box`.
- Remove now-unused `import …Geist`, `…GeistMono`, `…FontWeight`, `…fsSp` where they were only used for text. Add `import …DinghyType` + `import …toTextStyle`.

**4. Snap odd sizes:** if a site was `fsSp(17f|18f|23f|30f|34f, …)`, the role's tier replaces it. If the visible change is large (e.g. 34→26 or 30→28/hero), note it in the commit body for on-device eyeballing.

**5. Per-task verification (every sweep task ends with):**
- `…gw.bat :app:assembleDebug --no-daemon` → BUILD SUCCESSFUL.
- `…gw.bat :app:testDebugUnitTest --tests "*FontConformanceTest" --no-daemon` → offender count for the task's files is **0** (overall count dropped by this group's share).
- `…gw.bat :app:testDebugUnitTest --no-daemon` → full unit suite still green.
- Commit with a message listing the files swept and any notable size snaps.

---

## Task 4: Sweep — designsystem components

**Files (Modify):** `designsystem/components/FocusFrame.kt`, `ListRow.kt`, `ToggleRow.kt`, `SelectorRow.kt`, `Scrubber.kt`, `AdjusterPanel.kt`, `FillMeter.kt`, `designsystem/control/OutlinedControl.kt`, `designsystem/ConfirmGuard.kt`, `designsystem/SeverityToast.kt`

These are the shared classes — sweeping them first fixes the most call sites indirectly. Known anchors: `FocusFrame` header is `Geist/SemiBold/20` → `focusHeader`; `ListRow` label is `Geist/SemiBold/20` → `listLabel`.

- [ ] **Step 1:** Apply the sweep recipe to each file above.
- [ ] **Step 2:** Run `assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Run `FontConformanceTest` → these files no longer appear in the offender list.
- [ ] **Step 4:** Run full unit suite → green.
- [ ] **Step 5:** Commit: `refactor(type): route designsystem components through DinghyType roles`

---

## Task 5: Sweep — Print Status

**Files (Modify):** `ui/printstatus/PrintStatusFocus.kt`, `PrintStatusField.kt`, `PrintStatusScreen.kt`

Known offenders here: `fsSp(18f)`, `fsSp(23f)`, `fsSp(30f)`, `fsSp(34f)`, `fsSp(22f)` (Mono stat labels/values). Map: hero value → `FocusHeroText`/`focusHero`; the 26-class tabular readouts → `statValue`; 22/23 mono labels → `statValue` or `dataMeta` per their on-screen size; 18 → `caption` or `dataMeta`.

- [ ] **Step 1:** Apply the sweep recipe. Note in the commit any 34→26 / 30→hero size changes for UAT.
- [ ] **Step 2:** `assembleDebug` → SUCCESSFUL.
- [ ] **Step 3:** `FontConformanceTest` → these files clear.
- [ ] **Step 4:** Full unit suite → green.
- [ ] **Step 5:** Commit: `refactor(type): Print Status onto DinghyType roles`

---

## Task 6: Sweep — Calibration

**Files (Modify):** `ui/calibration/CalibrationHubScreen.kt`, `BedMeshScreen.kt`, `TiltScreen.kt`, `ScrewsTiltScreen.kt`, `ProbeCalibrateScreen.kt`

Calibration Hub already uses a StepBased description (max 20 / min 15) — re-express via `body`/`caption` semantics; if it's a shrink-to-fit description, leave the autosize but ensure the family/weight derive from a role (or keep its bespoke autosize and allowlist that one helper if it predates roles — prefer routing it through the role's family).

- [ ] **Step 1:** Apply the sweep recipe.
- [ ] **Step 2:** `assembleDebug` → SUCCESSFUL.
- [ ] **Step 3:** `FontConformanceTest` → these files clear.
- [ ] **Step 4:** Full unit suite → green.
- [ ] **Step 5:** Commit: `refactor(type): Calibration screens onto DinghyType roles`

---

## Task 7: Sweep — Motion & I/O

**Files (Modify):** `ui/move/MoveScreen.kt`, `ui/extrude/ExtrudeScreen.kt`, `ui/outputs/OutputsScreen.kt`, `ui/outputs/OutputFocusControl.kt`, `ui/outputs/OutputToggleControl.kt`, `ui/finetune/FineTuneScreen.kt`

Known: Move's XYZ coordinate readout is a shrink-to-fit Data value (its legacy 11sp floor is a flagged non-conformance — switch it to `FocusHeroText`/`focusHero` so the floor becomes 15). Adjuster values are Mono stat-class.

- [ ] **Step 1:** Apply the sweep recipe; convert the Move readout's 11sp floor to the `focusHero` 15 floor.
- [ ] **Step 2:** `assembleDebug` → SUCCESSFUL.
- [ ] **Step 3:** `FontConformanceTest` → these files clear.
- [ ] **Step 4:** Full unit suite → green.
- [ ] **Step 5:** Commit: `refactor(type): Move/Extrude/Outputs/FineTune onto DinghyType roles`

---

## Task 8: Sweep — Settings, About, Printers, Theme, Spool, Macros, Prompt

**Files (Modify):** `ui/screen/SettingsScreen.kt`, `AboutScreen.kt`, `PrintersScreen.kt`, `ThemeEditorScreen.kt`, `TokenTextField.kt`, `ui/spool/SpoolScreen.kt`, `ui/spool/scan/ScanSurface.kt`, `ui/spool/scan/ScanConfirmCard.kt`, `ui/macros/BookmarkedMacrosScreen.kt`, `ui/prompt/PromptDialog.kt`, `PromptContentItems.kt`, `PromptMarkupText.kt`, `PromptImageItem.kt`

**`PromptMarkupText` specifics (corrected during T10):** route the base text style (`PromptMarkupText.kt:63`, `fsSp(18f)`) through `DinghyType.body` (18→20). The emphasis/color SpanStyles set weight/style/decoration/color only and need no change. **BUT** `sizeSpan` builds `SpanStyle(fontSize=…)` for the macro-author `<size:…>` ladder — a content carve-out the scanner would otherwise flag, so the whole file is ALLOWLISTED (see allowlist note above) as the size-analog of the `<color:#hex>` carve-out. Leave all color/size span logic alone.

- [ ] **Step 1:** Apply the sweep recipe.
- [ ] **Step 2:** `assembleDebug` → SUCCESSFUL.
- [ ] **Step 3:** `FontConformanceTest` → these files clear.
- [ ] **Step 4:** Full unit suite → green.
- [ ] **Step 5:** Commit: `refactor(type): Settings/About/Printers/Theme/Spool/Macros/Prompt onto DinghyType roles`

---

## Task 9: Sweep — mop up any remaining reporter offenders

**Files (Modify):** whatever `FontConformanceTest` still lists (Compose only — Views handled in Task 10).

- [ ] **Step 1:** Run `FontConformanceTest`; for each remaining non-Views, non-allowlisted file, apply the sweep recipe. **Explicitly includes `bench/ComposeBenchScene.kt`** (shipping/exported — has 2 inline `fontSize =` sites; route them through roles like any UI text). If a genuine non-role text use exists (e.g. a Material3 component requiring a raw `TextStyle`), either route it through a role's `toTextStyle(t)` or, if truly unavoidable, add a narrowly-scoped allowlist entry **with a comment justifying it** — and call it out for review.
- [ ] **Step 2:** `assembleDebug` → SUCCESSFUL.
- [ ] **Step 3:** `FontConformanceTest` → only the four Views surfaces + allowlist remain in any printout (Compose offenders = 0).
- [ ] **Step 4:** Full unit suite → green.
- [ ] **Step 5:** Commit: `refactor(type): finish Compose font sweep (reporter clean)`

---

## Task 10: Wire the four Views surfaces to roles

The Views surfaces stay allowlisted (they set `Paint.typeface`/`textSize`, not Compose `fontFamily=`), but they must read their numbers/family **from the roles** so a tier edit reaches them. Add a small Views bridge.

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/theme/views/TextRoleViews.kt`
- Modify: `ui/console/ConsoleRowsAdapter.kt`, `ui/files/FileRowsAdapter.kt`, `render/GraphView.kt`, `render/WebcamView.kt`

- [ ] **Step 1: Add the Views bridge**

```kotlin
package works.mees.dinghy.theme.views

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import works.mees.dinghy.R
import works.mees.dinghy.theme.TextRole
import works.mees.dinghy.theme.TypeRole

/** Resolve the `res/font` [Typeface] for a role on a classic-Views surface (mirrors the Compose family map). */
fun TextRole.typeface(context: Context): Typeface {
    val resId = when (role) {
        TypeRole.Data -> if (weight >= FontWeight.SemiBold) R.font.geist_mono_semibold else R.font.geist_mono_medium
        TypeRole.Ui -> when {
            weight >= FontWeight.Bold -> R.font.geist_bold
            weight >= FontWeight.SemiBold -> R.font.geist_semibold
            weight >= FontWeight.Medium -> R.font.geist_medium
            else -> R.font.geist_regular
        }
    }
    return runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull()
        ?: if (role == TypeRole.Data) Typeface.MONOSPACE else Typeface.DEFAULT
}
```

- [ ] **Step 2: Console** — replace the literal `15f` / `R.font.geist_mono_medium` with `DinghyType.consoleLine`:
```kotlin
// textSize base + typeface now derive from the role:
textSize = fsSp(DinghyType.consoleLine.baseSp, p.fs)
typeface = DinghyType.consoleLine.typeface(context)
```

- [ ] **Step 3: GraphView** — `LABEL_BASE_SP = 27f` → the role's base; temp axis labels are small Data readouts:
```kotlin
// remove `private const val LABEL_BASE_SP = 27f`
labelPaint.typeface = DinghyType.statValue.typeface(context)
labelPaint.textSize = fsSp(DinghyType.statValue.baseSp, t.fs) * density   // 26 (was 27)
```
> If 26 reads too small for across-the-room graph labels on flox, that is the on-device judgment call noted in the spec — bump to a Data role at the desired tier rather than re-introducing 27. Note the change for UAT.

- [ ] **Step 4: WebcamView** — chrome text → `caption` (Geist 15), card filename → `dataInline`, card body → `caption` (kills the sub-floor `CARD_BODY_SP = 13f`):
```kotlin
// remove CHROME_TEXT_SP / CARD_BODY_SP consts; derive from roles
chromeTextPaint.textSize = fsSp(DinghyType.caption.baseSp, t.fs) * density
chromeTextPaint.typeface = DinghyType.caption.typeface(context)
// card filename paint → DinghyType.dataInline ; card body paint → DinghyType.caption
```

- [ ] **Step 5: FileRowsAdapter** — the worst offender. Filenames are Data; today they use `Typeface.DEFAULT_BOLD` at 17:
```kotlin
title.typeface = DinghyType.dataInline.typeface(context)       // Geist Mono (was DEFAULT_BOLD)
title.textSize = fsSp(DinghyType.dataInline.baseSp, fs)        // 20 (was 17); pass the active --fs
meta.typeface  = DinghyType.caption.typeface(context)
meta.textSize  = fsSp(DinghyType.caption.baseSp, fs)           // 15 (was 13)
thumbLabel… → dataMeta ; selectedMark… → caption
```
> **`--fs` threading (verified gap):** `FileRowsAdapter` is a classic `RecyclerView.Adapter` with NO `LocalTokens` access — `FileListView` is the Composable that holds `LocalTokens`. So thread `t.fs` INTO the adapter the way `ConsoleRowsAdapter` receives `p.fs`: carry it on the adapter's palette/submit state (e.g. a `FileRowPalette`/bind payload) that `FileListView` updates from `LocalTokens.current.fs`. Do not try to read tokens inside the adapter. Mirror the Console palette→`bind()` pattern exactly.

- [ ] **Step 6:** `assembleDebug` → SUCCESSFUL; full unit suite → green.
- [ ] **Step 7: On-device UAT** — install both ABI slices to flox + moto, eyeball Console, Files list, temp Graph, and a webcam feed. Filenames now Mono; graph labels 26; no sub-15 text. Confirm with Matthew.
- [ ] **Step 8:** Commit: `refactor(type): wire Console/Files/Graph/Webcam Views to DinghyType roles`

---

## Task 11: Enforce + document

**Files:**
- Modify: `app/src/test/java/works/mees/dinghy/theme/FontConformanceTest.kt` (flip `ENFORCE` → true)
- Modify: `docs/ui_design/THEMING.md`, `docs/ui_design/COMPONENTS.md`

- [ ] **Step 1:** Flip the test to enforcing:
```kotlin
private companion object { const val ENFORCE = true }
```

- [ ] **Step 2:** Run `…gw.bat :app:testDebugUnitTest --tests "*FontConformanceTest" --no-daemon` → PASS (zero offenders outside allowlist). If anything fails, it is a missed sweep site — fix it, do not widen the allowlist without justification.

- [ ] **Step 3:** Update `THEMING.md` §"The type ramp (R11)" — add a **Role** column mapping each tier to its `DinghyType` role name(s); add one sentence that the four Views surfaces derive type from `DinghyType` (one source of truth) and that converting them to Compose is a separate deferred question.

- [ ] **Step 4:** Update `COMPONENTS.md` — add a `DinghyType` component-class entry (the 11 roles, the family rule, "call sites use `role.toTextStyle(t)` / `FocusHeroText`; `fontFamily`/`fontSize` are forbidden inline and enforced by `FontConformanceTest`").

- [ ] **Step 5:** Run the FULL unit suite → green: `…gw.bat :app:testDebugUnitTest --no-daemon`.

- [ ] **Step 6:** Commit:
```bash
git add app/src/test/java/works/mees/dinghy/theme/FontConformanceTest.kt docs/ui_design/THEMING.md docs/ui_design/COMPONENTS.md
git commit -m "feat(type): enforce font conformance + document DinghyType roles"
```

---

## Self-review notes (coverage map)

- Spec §1 `TextRole` neutral type → Task 1. §2 catalog → Task 1. §3 Compose consumption + `FocusHeroText` → Task 2. §3 Views consumption → Task 10. §4 sweep → Tasks 4–9. §5 enforcement → Tasks 3 (report) + 11 (enforce). §6 docs → Task 11.
- focusHero = one shrink-to-fit tier (40→15) → Task 1 (`maxSp`/`minSp`) + Task 2 (`FocusHeroText`).
- One source of truth both toolkits → Task 10 bridge derives Views typeface/size from the same roles.
- Known concrete offenders all have a home: PrintStatus 18/23/30/34 (Task 5), Move 11 floor (Task 7), GraphView 27 (Task 10), Webcam 13 (Task 10), FileRows DEFAULT_BOLD/17/13/11 (Task 10).
- Open micro-decisions (spec): `FontWeight` vs `Int` weight — chose `FontWeight` (Task 1, justified inline). GraphView label tier — `statValue` 26 with an on-device escape hatch (Task 10 Step 3).
```
