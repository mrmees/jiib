# Macros Screen Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the Macros screen's Focus/Field grammar (selected macro fills the Focus, the launcher list stays in the Field) and upgrade macro parsing so parameter entry is accurate (description capture, bracket-syntax params, `rawparams` detection, required inference).

**Architecture:** Five logic changes built TDD-first (parser, raw-config extraction, store, holder, invocation), then one UI restructure. The selected macro is hoisted to screen state as a `selectedName: String?`; the Focus renders its detail (description + keyboard-backed param fields, or a single raw-args field), the Field always renders the launcher list, and Execute is a green foot button driven by hoisted execute state. Macros are treated as "a CLI made easier": all param values use the system keyboard; anything unparseable falls back to a raw-args field.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, kotlinx.coroutines Flow, JUnit. Builds Windows-side via `E:\Android\gw.bat` (see CLAUDE.md — `./gradlew` does NOT run from WSL).

**Spec:** `docs/superpowers/specs/2026-06-14-macros-screen-polish-design.md`
**Codex source notes:** `docs/moonraker_macro_extraction_recommendation.md`

---

## Build & test commands (this repo)

Run any Gradle task from the repo root via the Windows helper, stripping CR progress bars:

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
```

- A single test class: append `--tests "works.mees.dinghy.ui.macros.MacroParamParserTest"`.
- The exit code is authoritative (the CR bars are cosmetic).
- ⚠ Gradle may mark tasks `UP-TO-DATE`; if a run looks stale add `--rerun-tasks`.
- Wave-0 lesson: the WHOLE test sourceset compiles before the `--tests` filter runs, so every test file must compile even when you only run one class.

---

## File Structure

**Logic (TDD):**
- `app/src/main/java/works/mees/dinghy/ui/macros/MacroModels.kt` — `MacroParam` gains `required`; `MacroVm` gains `description` + `usesRawParams`.
- `app/src/main/java/works/mees/dinghy/ui/macros/MacroParamParser.kt` — bracket-syntax pass, `usesRawParams()`, required inference.
- `app/src/main/java/works/mees/dinghy/net/MacroConfigExtraction.kt` — **new**: pure `extractMacroConfigs(settings)` → name→(gcode, description).
- `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` — use `extractMacroConfigs`; push descriptions to the store.
- `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` — `macroDescriptions` StateFlow + setter.
- `app/src/main/java/works/mees/dinghy/ui/macros/MacroHolder.kt` — 5th combine source (descriptions); thread `description`/`usesRawParams` into `MacroVm`.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — collect `store.macroDescriptions` into the holder.
- `app/src/main/java/works/mees/dinghy/command/MacroInvocation.kt` — `buildRaw(macroName, rawArgs)`.

**UI:**
- `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt` — restructure: Focus = selected macro detail; Field = list always; Execute foot button; raw-args field; empty-state Focus.
- `app/src/main/java/works/mees/dinghy/preview/MacrosPreviews.kt` — update to the new `selectedName` seam.
- `app/src/main/res/values/strings.xml` — new strings; remove two now-unused ones.

**Tests:**
- `app/src/test/java/works/mees/dinghy/ui/macros/MacroParamParserTest.kt`
- `app/src/test/java/works/mees/dinghy/net/MacroConfigExtractionTest.kt` — **new**
- `app/src/test/java/works/mees/dinghy/ui/macros/MacroHolderTest.kt`
- `app/src/test/java/works/mees/dinghy/command/MacroInvocationTest.kt`

---

## Task 1: Param parser — bracket syntax, rawparams, required inference

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/macros/MacroModels.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/macros/MacroParamParser.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/macros/MacroParamParserTest.kt`

Background: the existing `PARAM_REGEX`/`PARAM_IN_REGEX` are **verbatim Mainsail** and MUST NOT be modified (the file KDoc and an existing test assert their exact behaviour, including the trailing-`|float`-after-`default` quirk → `type == null`). All new capability is added as **additional passes**, never by editing the two existing regexes.

- [ ] **Step 1: Add `required` to `MacroParam` (with a default so existing call sites compile)**

In `MacroModels.kt`, change the `MacroParam` data class to add a fourth property. Replace:

```kotlin
data class MacroParam(
    val name: String,
    val type: String?,
    val default: String?,
) {
```

with:

```kotlin
data class MacroParam(
    val name: String,
    val type: String?,
    val default: String?,
    /**
     * Heuristic (Codex extraction doc §"required"): true when the param is referenced WITHOUT a
     * `|default(...)` — i.e. the macro author expects the caller to supply it. Optional (has a default)
     * → false. Defaulted to `false` so preview/test constructions that omit it still compile. This is a
     * DISPLAY hint only (a `*` marker in the param field); Execute is never hard-gated on it.
     */
    val required: Boolean = false,
) {
```

- [ ] **Step 2: Write the failing parser tests**

Append these tests to `MacroParamParserTest.kt` (before the closing `}`):

```kotlin
    @Test
    fun bracketSyntaxParam_isDiscovered() {
        // Codex doc: params["NAME"] / params['NAME'] bracket access is a real Klipper idiom the
        // verbatim Mainsail dot-regex misses. The new bracket pass must discover it.
        val body = """{% set p = params["PROFILE"]|default('default') %}\nBED_MESH_PROFILE LOAD={p}"""
        val params = MacroParamParser.parseMacroParams(body)
        assertTrue("bracket param PROFILE must be discovered", params.any { it.name == "PROFILE" })
    }

    @Test
    fun bracketSyntaxSingleQuote_isDiscovered() {
        val params = MacroParamParser.parseMacroParams("M104 S{params['EXTRUDER']}")
        assertTrue(params.any { it.name == "EXTRUDER" })
    }

    @Test
    fun requiredInference_paramWithoutDefaultIsRequired() {
        // params.LAYER with no |default(...) → required = true (set_pause_at_layer's LAYER HAS a
        // default so it is optional; a bare params.X is required).
        val params = MacroParamParser.parseMacroParams("SET_PRINT_STATS_INFO CURRENT_LAYER={params.LAYER}")
        assertTrue(params.first { it.name == "LAYER" }.required)
    }

    @Test
    fun requiredInference_paramWithDefaultIsOptional() {
        val params = MacroParamParser.parseMacroParams(body("gcode_macro start_print"))
        assertFalse(
            "BED_TEMP has |default(60) so it is optional",
            params.first { it.name == "BED_TEMP" }.required,
        )
    }

    @Test
    fun usesRawParams_trueWhenBodyReferencesRawparams() {
        assertTrue(MacroParamParser.usesRawParams("""RESPOND MSG="args: {rawparams}""""))
    }

    @Test
    fun usesRawParams_falseForOrdinaryBody() {
        assertFalse(MacroParamParser.usesRawParams(body("gcode_macro start_print")))
    }
```

Add the missing import at the top of the test file (next to the other `org.junit.Assert.*` imports):

```kotlin
import org.junit.Assert.assertFalse
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.ui.macros.MacroParamParserTest\" --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: FAIL — `usesRawParams` unresolved; bracket/required assertions fail.

- [ ] **Step 4: Implement the parser upgrades**

In `MacroParamParser.kt`, add two new regexes after `PARAM_IN_REGEX` (do NOT touch the existing two):

```kotlin
    /**
     * Bracket-access param idiom `params["NAME"]` / `params['NAME']` (Codex extraction doc). Captures the
     * name only — bracket access rarely carries an inline `|default`/type the same way dot-access does, so
     * a bracket param degrades to type=null/default=null (→ string keyboard, the safe default). Additive:
     * the verbatim Mainsail dot-regex is left untouched.
     */
    val PARAM_BRACKET_REGEX = Regex("""params\s*\[\s*['"]([A-Za-z_0-9]+)['"]\s*]""")

    /** A macro that reads the full unparsed arg string. When present, param inference is unreliable. */
    private val RAWPARAMS_REGEX = Regex("""\brawparams\b""")
```

Add the `usesRawParams` function and fold `required` into `parseMacroParams`. Replace the existing `parseMacroParams` body with:

```kotlin
    fun parseMacroParams(gcodeBody: String): List<MacroParam> {
        val out = linkedMapOf<String, MacroParam>() // first-seen order, dedup by name
        for (m in PARAM_REGEX.findAll(gcodeBody)) {
            val name = m.groupValues[1]
            // type = leading filter (group 2) else trailing filter (group 4) else null
            val type = m.groupValues[2].ifEmpty { m.groupValues[4] }.ifEmpty { null }
            val default = m.groupValues[3].ifEmpty { null }
            // required heuristic (Codex doc): a param referenced WITHOUT |default(...) is required.
            out.putIfAbsent(name, MacroParam(name, type, default, required = default == null))
        }
        for (m in PARAM_IN_REGEX.findAll(gcodeBody)) {
            // membership-guarded params are optional-by-nature (the macro checks before use).
            out.putIfAbsent(m.groupValues[1], MacroParam(m.groupValues[1], null, null, required = false))
        }
        for (m in PARAM_BRACKET_REGEX.findAll(gcodeBody)) {
            // bracket access carries no inline default → treat as required (no default detected).
            out.putIfAbsent(m.groupValues[1], MacroParam(m.groupValues[1], null, null, required = true))
        }
        return out.values.toList()
    }

    /** Whether the macro body slurps the raw arg string (Codex doc): inference is unreliable → raw-args UI. */
    fun usesRawParams(gcodeBody: String): Boolean = RAWPARAMS_REGEX.containsMatchIn(gcodeBody)
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.ui.macros.MacroParamParserTest\" --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: PASS (all existing + 6 new tests). The pre-existing `startPrint_extractsBedAndExtruderTempDefaults` must STILL pass — the verbatim regexes are unchanged.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/macros/MacroModels.kt \
        app/src/main/java/works/mees/dinghy/ui/macros/MacroParamParser.kt \
        app/src/test/java/works/mees/dinghy/ui/macros/MacroParamParserTest.kt
git commit -m "feat(macros): bracket-syntax params, rawparams detection, required inference"
```

---

## Task 2: Macro config extraction — capture description (pure + store + wiring)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/net/MacroConfigExtraction.kt`
- Test: `app/src/test/java/works/mees/dinghy/net/MacroConfigExtractionTest.kt`
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt`
- Modify: `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt`

Approach: a single pure function turns `configfile.settings` into `name → (gcode, description)`. Production keeps the existing `macroBodies` map UNCHANGED (so `HandshakeTest` needs no edits) and adds a PARALLEL `macroDescriptions` map. This mirrors the established `parseHeaterLimits` pattern (pure, unit-tested, no extra Moonraker query).

- [ ] **Step 1: Write the failing extraction test (creates the new test file)**

Create `app/src/test/java/works/mees/dinghy/net/MacroConfigExtractionTest.kt`:

```kotlin
package works.mees.dinghy.net

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure extraction of per-macro config (gcode body + description) from a `configfile.settings` object,
 * mirroring the parseHeaterLimits discipline (no I/O, host-testable). Moonraker LOWERCASES settings
 * keys, so the section is `gcode_macro <lowercased name>` and the returned key is the lowercased name.
 */
class MacroConfigExtractionTest {

    private fun settings(json: String): JsonObject =
        MoonrakerJson.parseToJsonElement(json).jsonObject

    @Test
    fun extractsGcodeAndDescription() {
        val s = settings(
            """
            {
              "gcode_macro print_start": {
                "description": "Starts the print",
                "gcode": "{% set BED = params.BED|default(60)|float %}\nG28"
              }
            }
            """.trimIndent(),
        )
        val configs = extractMacroConfigs(s)
        assertEquals(setOf("print_start"), configs.keys)
        assertEquals("Starts the print", configs["print_start"]?.description)
        assertTrue(configs["print_start"]?.gcode?.contains("params.BED") == true)
    }

    @Test
    fun descriptionAbsent_isNull_butBodyStillExtracted() {
        val s = settings("""{ "gcode_macro home_all": { "gcode": "G28" } }""")
        val configs = extractMacroConfigs(s)
        assertNull(configs["home_all"]?.description)
        assertEquals("G28", configs["home_all"]?.gcode)
    }

    @Test
    fun nonMacroSectionsIgnored_andArrayGcodeJoined() {
        val s = settings(
            """
            {
              "extruder": { "min_extrude_temp": 170 },
              "gcode_macro m_multi": { "gcode": ["G90", "G1 Z5"] }
            }
            """.trimIndent(),
        )
        val configs = extractMacroConfigs(s)
        assertEquals(setOf("m_multi"), configs.keys)
        assertEquals("G90\nG1 Z5", configs["m_multi"]?.gcode)
    }

    @Test
    fun macroWithNoGcode_isSkipped() {
        val s = settings("""{ "gcode_macro broken": { "description": "no body" } }""")
        assertTrue(extractMacroConfigs(s).isEmpty())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.net.MacroConfigExtractionTest\" --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: FAIL — `extractMacroConfigs` / `MacroConfigEntry` unresolved.

- [ ] **Step 3: Create the pure extraction implementation**

Create `app/src/main/java/works/mees/dinghy/net/MacroConfigExtraction.kt`:

```kotlin
package works.mees.dinghy.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Raw per-macro config carrier extracted from `configfile.settings` (the single one-shot configfile
 * query — Pitfall 3, no extra request). [gcode] is the macro body (used for heuristic param parsing);
 * [description] is the Klipper `description:` docstring shown in the Macros Focus, or null.
 */
data class MacroConfigEntry(
    val gcode: String,
    val description: String?,
)

/**
 * PURE: `configfile.settings` JsonObject → lowercased-macro-name → [MacroConfigEntry]. Total — a missing
 * or garbage field is skipped, never thrown (the house "a bad field is skipped, never fatal" rule). A
 * `gcode_macro` section with no usable `gcode` body is skipped entirely (matches the prior inline
 * behaviour, so the existing macroBodies map / HandshakeTest are unaffected). Moonraker lowercases
 * settings keys, so the section name is already lowercase; the returned key is that lowercased name.
 */
internal fun extractMacroConfigs(settings: JsonObject): Map<String, MacroConfigEntry> =
    settings.entries.mapNotNull { (key, value) ->
        if (!key.startsWith("gcode_macro ")) return@mapNotNull null
        val obj = value as? JsonObject ?: return@mapNotNull null
        val gcode = obj.gcodeBody() ?: return@mapNotNull null
        val name = key.removePrefix("gcode_macro ").lowercase()
        val description = (obj["description"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        name to MacroConfigEntry(gcode = gcode, description = description)
    }.toMap()

/** A macro section's `gcode` body — normally a newline-joined string; tolerate an array by joining. */
private fun JsonObject.gcodeBody(): String? = runCatching {
    when (val g = this["gcode"]) {
        is JsonArray -> g.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("\n")
        is JsonPrimitive -> g.contentOrNull
        else -> null
    }
}.getOrNull()
```

- [ ] **Step 4: Run the extraction test to verify it passes**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.net.MacroConfigExtractionTest\" --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: PASS (4 tests).

- [ ] **Step 5: Add the `macroDescriptions` flow + setter to the store**

In `PrinterStateStore.kt`, find the macro bodies block (around line 130–136):

```kotlin
    private val _macroBodies = MutableStateFlow<Map<String, String>>(emptyMap())
    /**
     * Macro-name (LOWERCASED, Moonraker's convention) → gcode body string, extracted from the SAME
     * one-shot `configfile` query that reads the extruder config (Pitfall 3 — no duplicate query).
     * Feeds the MACRO-02 parameter parser; null/empty when unreadable (degrades gracefully).
     */
    val macroBodies: StateFlow<Map<String, String>> = _macroBodies.asStateFlow()
```

Immediately after it, add:

```kotlin
    private val _macroDescriptions = MutableStateFlow<Map<String, String>>(emptyMap())
    /**
     * Macro-name (LOWERCASED) → Klipper `description:` docstring, from the SAME one-shot `configfile`
     * query (Pitfall 3 — no extra request). Only macros that declared a non-blank description appear.
     * Parallel to [macroBodies]; the MacroHolder combines both. Empty when unreadable/absent.
     */
    val macroDescriptions: StateFlow<Map<String, String>> = _macroDescriptions.asStateFlow()
```

Then find `setMacroBodies` (around line 355):

```kotlin
    fun setMacroBodies(bodies: Map<String, String>) {
        _macroBodies.value = bodies
    }
```

Add right after it:

```kotlin
    /** One-shot at (re)handshake: macro-name (lowercased) → description map, from the configfile query. */
    fun setMacroDescriptions(descriptions: Map<String, String>) {
        _macroDescriptions.value = descriptions
    }
```

- [ ] **Step 6: Wire `MoonrakerSession` to use the extractor + push descriptions**

In `MoonrakerSession.kt`, replace the inline macro-bodies block (currently lines ~586–596):

```kotlin
            val macroBodies: Map<String, String> = settings
                ?.entries
                ?.mapNotNull { (key, value) ->
                    if (!key.startsWith("gcode_macro ")) return@mapNotNull null
                    val name = key.removePrefix("gcode_macro ").lowercase()
                    val body = (value as? JsonObject)?.gcodeBodyOrNull() ?: return@mapNotNull null
                    name to body
                }
                ?.toMap()
                ?: emptyMap()
            store.setMacroBodies(macroBodies)
```

with:

```kotlin
            // (b) every `gcode_macro <name>` section's `.gcode` body + `description`, keyed by the
            // LOWERCASED macro name (Moonraker lowercases settings keys). One pure pass over settings
            // (extractMacroConfigs) feeds BOTH the param-parser bodies and the Focus description text.
            val macroConfigs = if (settings != null) extractMacroConfigs(settings) else emptyMap()
            store.setMacroBodies(macroConfigs.mapValues { it.value.gcode })
            store.setMacroDescriptions(
                macroConfigs.entries.mapNotNull { (name, cfg) -> cfg.description?.let { name to it } }.toMap(),
            )
```

Then, in the `.onFailure` for the configfile read (currently lines ~614–620, which clears output descriptors + heater limits), add a clear for descriptions so a printer switch / failed read never leaves a stale description. Find:

```kotlin
        }.onFailure {
            // configfile read FAILED entirely → clear output descriptors AND heater limits so a printer
            // switch / failed read never leaves stale hardware controls or scrubber bounds visible
            // (clear-on-failure, T-19-04-04). Best-effort — the handshake already reached subscribe above.
            store.setOutputDescriptors(emptyList())
            store.setHeaterLimits(emptyMap())
        }
```

and add the two clears (bodies + descriptions) alongside:

```kotlin
        }.onFailure {
            // configfile read FAILED entirely → clear output descriptors AND heater limits so a printer
            // switch / failed read never leaves stale hardware controls or scrubber bounds visible
            // (clear-on-failure, T-19-04-04). Best-effort — the handshake already reached subscribe above.
            store.setOutputDescriptors(emptyList())
            store.setHeaterLimits(emptyMap())
            store.setMacroBodies(emptyMap())
            store.setMacroDescriptions(emptyMap())
        }
```

Now the private `gcodeBodyOrNull()` extension in `MoonrakerSession.kt` (lines ~683–689) is unused. Delete it:

```kotlin
    /**
     * Extract a macro section's `gcode` body (08-04, MACRO-02). Normally a single newline-joined string;
     * tolerates an array-of-strings shape by joining with `\n`. A missing/garbage field yields null
     * (skip the macro), never `!!` on wire data.
     */
    private fun JsonObject.gcodeBodyOrNull(): String? = runCatching {
        when (val g = this["gcode"]) {
            is JsonArray -> g.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("\n")
            is JsonPrimitive -> g.contentOrNull
            else -> null
        }
    }.getOrNull()
```

- [ ] **Step 7: Run the full unit-test suite to verify nothing regressed (HandshakeTest especially)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL. `HandshakeTest` still asserts `macroBodies.keys == {start_print, load_filament}` and passes — the bodies map output is unchanged.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/net/MacroConfigExtraction.kt \
        app/src/test/java/works/mees/dinghy/net/MacroConfigExtractionTest.kt \
        app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt \
        app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
git commit -m "feat(macros): capture macro description from configfile (parallel store map)"
```

---

## Task 3: Holder — thread description + usesRawParams into the view-model

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/macros/MacroModels.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/macros/MacroHolder.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/macros/MacroHolderTest.kt`

- [ ] **Step 1: Add `description` + `usesRawParams` to `MacroVm` (defaulted so previews/tests compile)**

In `MacroModels.kt`, replace the `MacroVm` data class:

```kotlin
data class MacroVm(
    val name: String,
    val isBookmarked: Boolean,
    val isHidden: Boolean,
    val params: List<MacroParam>,
)
```

with:

```kotlin
data class MacroVm(
    val name: String,
    val isBookmarked: Boolean,
    val isHidden: Boolean,
    val params: List<MacroParam>,
    /** Klipper `description:` docstring (from configfile), shown under the macro name in the Focus. */
    val description: String? = null,
    /** True when the body slurps `rawparams` — the Focus offers a single raw-args field instead of fields. */
    val usesRawParams: Boolean = false,
)
```

- [ ] **Step 2: Write the failing holder tests**

In `MacroHolderTest.kt`, first extend `FakeMacroPrefsSource` usage with a descriptions stub. Add these two tests before the closing `}`:

```kotlin
    @Test
    fun descriptionPlumbsThroughToVm() = runTest {
        val prefs = FakeMacroPrefsSource(emptySet(), revealHidden = false)
        val holder = MacroHolder(this, caps("START_PRINT"), prefs.bookmarks, prefs.revealHidden)
        holder.setMacroDescriptions(mapOf("start_print" to "Starts the print"))
        advanceUntilIdle()
        assertEquals(
            "Starts the print",
            holder.state.value.macros.first { it.name == "START_PRINT" }.description,
        )
    }

    @Test
    fun usesRawParams_surfacesFromBody() = runTest {
        val prefs = FakeMacroPrefsSource(emptySet(), revealHidden = false)
        val holder = MacroHolder(this, caps("ECHO"), prefs.bookmarks, prefs.revealHidden)
        holder.setMacroBody("ECHO", """RESPOND MSG="{rawparams}"""")
        advanceUntilIdle()
        assertTrue(holder.state.value.macros.first { it.name == "ECHO" }.usesRawParams)
    }
```

- [ ] **Step 3: Run to verify failure**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.ui.macros.MacroHolderTest\" --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: FAIL — `setMacroDescriptions` unresolved; `description`/`usesRawParams` not populated.

- [ ] **Step 4: Add the descriptions source + thread fields in `MacroHolder`**

In `MacroHolder.kt`, add a descriptions StateFlow next to `_macroBodies` (after line 62):

```kotlin
    /** Per-macro descriptions keyed by name (case-insensitive lookups go through [descriptionFor]). */
    private val _macroDescriptions = MutableStateFlow<Map<String, String>>(emptyMap())
```

Change the `combine` from four sources to five. Replace:

```kotlin
            combine(
                capabilities,
                bookmarks,
                revealHidden,
                _macroBodies,
            ) { caps, marks, reveal, bodies ->
                buildState(caps.macros, marks, reveal, bodies)
            }.collect { _state.value = it }
```

with:

```kotlin
            combine(
                capabilities,
                bookmarks,
                revealHidden,
                _macroBodies,
                _macroDescriptions,
            ) { caps, marks, reveal, bodies, descriptions ->
                buildState(caps.macros, marks, reveal, bodies, descriptions)
            }.collect { _state.value = it }
```

Add the production setter after `setMacroBodies` (after line 111):

```kotlin
    /**
     * Replace the whole descriptions map (the production seam — fed from
     * [works.mees.dinghy.state.PrinterStateStore.macroDescriptions] by the shell wiring on every handshake).
     */
    fun setMacroDescriptions(descriptions: Map<String, String>) {
        _macroDescriptions.value = descriptions
    }
```

Replace `buildState` (lines 113–139) to accept + use descriptions and set the new VM fields:

```kotlin
    private fun buildState(
        macroNames: List<String>,
        bookmarks: Set<String>,
        reveal: Boolean,
        bodies: Map<String, String>,
        descriptions: Map<String, String>,
    ): MacroScreensState {
        if (macroNames.isEmpty()) {
            return MacroScreensState(revealHidden = reveal, unavailable = true)
        }
        val all = macroNames.map { name ->
            val body = bodyFor(bodies, name)
            MacroVm(
                name = name,
                isBookmarked = bookmarks.any { it.equals(name, ignoreCase = true) },
                isHidden = name.startsWith("_"),
                params = body?.let { MacroParamParser.parseMacroParams(it) } ?: emptyList(),
                description = descriptionFor(descriptions, name),
                usesRawParams = body?.let { MacroParamParser.usesRawParams(it) } ?: false,
            )
        }
        val visible = all.filter { reveal || !it.isHidden }
        val bookmarked = all.filter { it.isBookmarked }
        return MacroScreensState(
            macros = all,
            visibleMacros = visible,
            bookmarkedMacros = bookmarked,
            revealHidden = reveal,
            unavailable = false,
        )
    }
```

Add a case-insensitive description lookup next to `bodyFor` (after line 143):

```kotlin
    /** Case-insensitive description lookup (Moonraker lowercases macro names). */
    private fun descriptionFor(descriptions: Map<String, String>, name: String): String? =
        descriptions[name] ?: descriptions.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
```

- [ ] **Step 5: Run the holder tests to verify they pass**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.ui.macros.MacroHolderTest\" --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: PASS (existing 5 + 2 new).

- [ ] **Step 6: Wire the shell to feed descriptions into the holder**

In `AppShell.kt`, find the existing macroBodies collection (around line 456–458):

```kotlin
    // populate. Re-collected when the store rebuilds (a new session's macroBodies).
    LaunchedEffect(macroHolder, store) {
        store.macroBodies.collect { macroHolder.setMacroBodies(it) }
    }
```

(The exact `LaunchedEffect(...)` header may differ — match the block whose body is `store.macroBodies.collect { macroHolder.setMacroBodies(it) }`.) Add a sibling effect immediately after it:

```kotlin
    LaunchedEffect(macroHolder, store) {
        store.macroDescriptions.collect { macroHolder.setMacroDescriptions(it) }
    }
```

- [ ] **Step 7: Build to verify the shell wiring compiles**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/macros/MacroModels.kt \
        app/src/main/java/works/mees/dinghy/ui/macros/MacroHolder.kt \
        app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt \
        app/src/test/java/works/mees/dinghy/ui/macros/MacroHolderTest.kt
git commit -m "feat(macros): thread description + usesRawParams through MacroHolder"
```

---

## Task 4: MacroInvocation — raw-args build path

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/command/MacroInvocation.kt`
- Test: `app/src/test/java/works/mees/dinghy/command/MacroInvocationTest.kt`

Security note for the reviewer: the raw-args string is appended verbatim after the macro name, so it goes through the SAME `rejectForbidden` gate as typed string params (rejects `\n \r \t ; "` + control chars). This deliberately rejects double-quotes in raw mode too — consistent with the project's REJECT-not-escape posture and the unconfirmed Klipper quote semantics. Spaces and `=` ARE allowed (they are how raw CLI args are written). An empty raw string yields just the bare macro name.

- [ ] **Step 1: Write the failing buildRaw tests**

Append to `MacroInvocationTest.kt` before the closing `}`:

```kotlin
    // ---- buildRaw coverage (raw-args fallback for rawparams / unparseable macros) ----------------

    @Test
    fun buildRaw_appendsCleanArgsVerbatim() {
        // Space-separated CLI-style args are passed through after the (uppercased) macro name.
        assertEquals("M600 X50 Y20", MacroInvocation.buildRaw("M600", "X50 Y20"))
    }

    @Test
    fun buildRaw_blankArgs_yieldsBareMacroName() {
        assertEquals("HOME_ALL", MacroInvocation.buildRaw("HOME_ALL", ""))
        assertEquals("HOME_ALL", MacroInvocation.buildRaw("HOME_ALL", "   "))
    }

    @Test
    fun buildRaw_newlineInArgs_rejected() {
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.buildRaw("ECHO", "ok\n; M112")
        }
    }

    @Test
    fun buildRaw_semicolonInArgs_rejected() {
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.buildRaw("ECHO", "a ; M112")
        }
    }

    @Test
    fun buildRaw_doubleQuoteInArgs_rejected() {
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.buildRaw("ECHO", """MSG="hi"""")
        }
    }
```

- [ ] **Step 2: Run to verify failure**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.command.MacroInvocationTest\" --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: FAIL — `buildRaw` unresolved.

- [ ] **Step 3: Implement buildRaw**

In `MacroInvocation.kt`, add this function inside the `object MacroInvocation`, after `buildTyped` (after line 67):

```kotlin
    /**
     * Assemble a macro invocation from a single freeform RAW argument string (the "CLI made easier"
     * fallback for `rawparams` macros and macros whose params can't be inferred). The whole [rawArgs]
     * tail is validated by [rejectForbidden] (same REJECT-not-escape policy as string params: `\n \r \t
     * ; "` and control chars are refused) then appended verbatim after the uppercased macro name. Spaces
     * and `=` are permitted — they are the raw arg grammar. A blank [rawArgs] yields the bare macro name.
     */
    fun buildRaw(macroName: String, rawArgs: String): String {
        val trimmed = rawArgs.trim()
        if (trimmed.isEmpty()) return macroName.uppercase()
        rejectForbidden("(raw args)", trimmed)
        return "${macroName.uppercase()} $trimmed"
    }
```

- [ ] **Step 4: Run to verify pass**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.command.MacroInvocationTest\" --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: PASS (existing + 5 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/command/MacroInvocation.kt \
        app/src/test/java/works/mees/dinghy/command/MacroInvocationTest.kt
git commit -m "feat(macros): buildRaw raw-args invocation path (sanitized)"
```

---

## Task 5: Screen restructure — selected macro fills the Focus, list stays in the Field

**Files:**
- Modify (full rewrite): `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

This is the grammar fix. Behaviour:
- **Field** always shows the launcher list (or the Manage list). Tapping a macro selects it (`selectedName`) and highlights the row; the list never disappears.
- **Focus** shows the selected macro: its name is the FocusFrame title; the body is the description (if any) + keyboard-backed param fields, OR a single raw-args field (when `usesRawParams` or no inferred params), OR a loading notice (configfile body not yet arrived). Before any selection (or in Manage mode) the Focus shows a "Select a macro to run" prompt.
- **Execute** is a green foot button in the launcher foot bar `[Back] [Manage] [Execute]`; enabled only when a macro is selected, its body has loaded, and it is not already running. Empty param fields are omitted from the command (Klipper applies its own default). After Execute the macro stays selected (a "Running…" notice shows); the screen does not navigate.

Param entry state (the values map, raw-args text, execute(), toasts) is HOISTED to `MacrosContent` so the Focus (param fields) and the Field (Execute button) share it across the two ScreenScaffold slots.

Keyboard choice is conservative: only explicitly-typed `int`/`double` params use the numeric IME; everything else uses the full keyboard (the existing `MacroParam.isNumeric` rule — unchanged). Rationale recorded for the Codex review: a wrongly-numeric field would lock the user out of typing, and the owner's guidance was "system keyboard only, don't overthink it."

- [ ] **Step 1: Add/remove strings**

In `app/src/main/res/values/strings.xml`, in the macros block (around lines 400–412): delete the two now-unused strings:

```xml
    <string name="macros_focus_blurb">Bookmarked printer macros.</string>
```
```xml
    <string name="macros_no_params">No parameters. Execute runs this macro as-is.</string>
```

Add these three new strings in the same block:

```xml
    <string name="macros_focus_select_prompt">Select a macro to run.</string>
    <string name="macros_raw_args_label">Arguments</string>
    <string name="macros_raw_args_hint">Passed to the macro verbatim.</string>
```

- [ ] **Step 2: Replace `BookmarkedMacrosScreen.kt` in full**

Overwrite `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt` with:

```kotlin
package works.mees.dinghy.ui.macros

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.MacroInvocation
import works.mees.dinghy.command.MacroParamRejected
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.ui.screen.TokenTextField

/**
 * Generously-wide numeric range for macro numeric-param clamping (D-07). Macro bodies never declare
 * their own range, so this is intentionally wide — the IME takeover is the clamp owner. A printer-side
 * range violation surfaces as a DispatchEvent.Failure toast, not blocked here.
 */
private val MACRO_NUMERIC_RANGE = -100_000.0..100_000.0

// ─────────────────────────────────────────────────────────────────────────────
// MacroFieldMode — the Field has TWO modes now (ParamEntry retired; the selected
// macro lives in the FOCUS, the list stays in the Field).
//  - Launcher   : the Bookmarked list (tap-to-SELECT; the selected macro fills the Focus)
//  - ManageMode : the System manage-visibility list (pin/unpin, reveal-hidden)
// ─────────────────────────────────────────────────────────────────────────────

sealed class MacroFieldMode {
    /** The Bookmarked macro launcher — the default state on entry. */
    data object Launcher : MacroFieldMode()

    /** The System manage-visibility list. Reached from the Manage foot button in Launcher. */
    data object ManageMode : MacroFieldMode()
}

// ─────────────────────────────────────────────────────────────────────────────
// Live overload — collects from holder, delegates to MacrosContent
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Merged Macros screen. The Field is the always-visible launcher list (or the Manage list); the FOCUS
 * holds the selected macro's detail — name (FocusFrame title), description, and keyboard-backed param
 * fields (or a single raw-args field, or a loading notice). Execute is a green foot button.
 *
 * Security: all string macro params route through [MacroInvocation.buildTyped]; raw-args macros route
 * through [MacroInvocation.buildRaw]. Both apply the V5 REJECT sanitizer. Execute is disabled until the
 * holder reports the configfile body has landed (cold-connect gate). Every dispatch uses key
 * `macro_<name>`.
 *
 * @param holder     the live macro state holder (reactive param bodies, descriptions, paramsKnown, state).
 * @param dispatcher the shared command dispatcher (busy key tracking, Failure events).
 * @param onBack     leave the Macros surface (NavHost pops).
 */
@Composable
fun BookmarkedMacrosScreen(
    holder: MacroHolder,
    dispatcher: CommandDispatcher?,
    onToggleBookmark: (String) -> Unit,
    onSetRevealHidden: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
) {
    val state by holder.state.collectAsStateWithLifecycle()
    var fieldMode by remember { mutableStateOf<MacroFieldMode>(MacroFieldMode.Launcher) }
    var selectedName by remember { mutableStateOf<String?>(null) }

    MacrosContent(
        state = state,
        fieldMode = fieldMode,
        selectedName = selectedName,
        holder = holder,
        dispatcher = dispatcher,
        onFieldModeChange = { fieldMode = it },
        onSelect = { selectedName = it },
        onToggleBookmark = onToggleBookmark,
        onSetRevealHidden = onSetRevealHidden,
        onBack = onBack,
        isPrinting = isPrinting,
        onEmergencyStop = onEmergencyStop,
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Stateless preview / test seam — no holder, no dispatcher, no Moonraker
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stateless preview seam. Drives [MacrosContent] from pure fixture state — no holder, no dispatcher, no
 * socket. [selectedName] selects a macro into the Focus; [fieldMode] picks Launcher vs Manage.
 */
@Composable
fun BookmarkedMacrosScreen(
    state: MacroScreensState,
    fieldMode: MacroFieldMode = MacroFieldMode.Launcher,
    selectedName: String? = null,
    modifier: Modifier = Modifier,
    onFieldModeChange: (MacroFieldMode) -> Unit = {},
    onSelect: (String?) -> Unit = {},
    onToggleBookmark: (String) -> Unit = {},
    onSetRevealHidden: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
) {
    MacrosContent(
        state = state,
        fieldMode = fieldMode,
        selectedName = selectedName,
        holder = null,
        dispatcher = null,
        onFieldModeChange = onFieldModeChange,
        onSelect = onSelect,
        onToggleBookmark = onToggleBookmark,
        onSetRevealHidden = onSetRevealHidden,
        onBack = onBack,
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal renderer — owns the hoisted param-entry state shared by Focus + Field
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacrosContent(
    state: MacroScreensState,
    fieldMode: MacroFieldMode,
    selectedName: String?,
    holder: MacroHolder?,
    dispatcher: CommandDispatcher?,
    onFieldModeChange: (MacroFieldMode) -> Unit,
    onSelect: (String?) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onSetRevealHidden: (Boolean) -> Unit,
    onBack: () -> Unit,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val context = LocalContext.current
        val keyboardController = LocalSoftwareKeyboardController.current

        // Screen-level dispatch-failure toast (survives any recomposition of the Focus detail).
        var failureToast by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(dispatcher) {
            dispatcher?.events?.collect { event ->
                if (event is DispatchEvent.Failure && event.key.startsWith("macro_")) {
                    failureToast = context.getString(
                        R.string.macros_rejected,
                        event.key.removePrefix("macro_"),
                        event.message,
                    )
                }
            }
        }
        LaunchedEffect(failureToast) {
            if (failureToast != null) {
                delay(6_000)
                failureToast = null
            }
        }

        // Resolve the live selected macro from holder state by NAME (so late-arriving bodies populate).
        val liveMacro = remember(state, selectedName) {
            selectedName?.let { name -> state.macros.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        }
        val params = liveMacro?.params ?: emptyList()
        // "configfile body arrived" gate (cold-connect): Execute stays disabled until true.
        val bodyLoaded = remember(state, selectedName) {
            if (selectedName == null) false else holder?.paramsKnown(selectedName) ?: true
        }
        // Raw-args mode: rawparams macros OR a macro with no inferable params (CLI fallback — every macro
        // is runnable, and args can be passed to anything).
        val rawMode = liveMacro != null && (liveMacro.usesRawParams || params.isEmpty())

        val inFlight by (dispatcher?.inFlight ?: remember { MutableStateFlow(emptySet<String>()) })
            .collectAsStateWithLifecycle()
        val running = selectedName != null && "macro_$selectedName" in inFlight

        // Hoisted per-macro entry state — reset whenever the selected macro (or its param set) changes.
        val values = remember(selectedName, params) {
            mutableStateMapOf(*params.map { it.name to (it.default ?: "") }.toTypedArray())
        }
        var rawArgs by remember(selectedName) { mutableStateOf("") }
        var localToast by remember(selectedName) { mutableStateOf<String?>(null) }

        fun execute() {
            val macro = liveMacro ?: return
            val gcode = try {
                if (rawMode) {
                    MacroInvocation.buildRaw(macro.name, rawArgs)
                } else {
                    // buildTyped is the REQUIRED V5 sanitizer path. Params left BLANK are omitted entirely
                    // (emitting KEY= would override the macro's own Jinja default). Numeric params are
                    // clamped HERE on the dispatch path so an Execute that skipped the IME Done-clamp can
                    // never send an unclamped number.
                    MacroInvocation.buildTyped(
                        macro.name,
                        params.mapNotNull { p ->
                            val raw = values[p.name].orEmpty()
                            if (raw.isBlank()) return@mapNotNull null
                            val value = if (p.isNumeric) {
                                val parsed = raw.toDoubleOrNull()
                                if (parsed != null && parsed.isFinite()) {
                                    formatNumeric(
                                        parsed.coerceIn(MACRO_NUMERIC_RANGE.start, MACRO_NUMERIC_RANGE.endInclusive),
                                    )
                                } else {
                                    raw
                                }
                            } else {
                                raw
                            }
                            Triple(p.name, value, p.isNumeric)
                        },
                    )
                }
            } catch (e: MacroParamRejected) {
                localToast = context.getString(R.string.macros_rejected, macro.name, e.reason)
                return
            }
            dispatcher?.dispatch(
                key = "macro_${macro.name}",
                method = JsonRpcMethods.GCODE_SCRIPT,
                params = PrinterCommands.scriptParams(gcode),
            )
            keyboardController?.hide()
        }

        val executeEnabled = selectedName != null && bodyLoaded && !running

        ScreenScaffold(
            focus = {
                val title = liveMacro?.name ?: stringResource(R.string.cd_launcher_macros)
                FocusFrame(
                    title = title,
                    icon = DinghyIcons.LauncherMacros,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxSize(),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    val t = LocalTokens.current
                    when {
                        state.unavailable -> MacrosUnavailable(modifier = Modifier.fillMaxWidth())
                        fieldMode is MacroFieldMode.ManageMode || liveMacro == null -> {
                            Text(
                                text = stringResource(R.string.macros_focus_select_prompt),
                                color = t.text2,
                                fontFamily = Geist,
                                fontSize = fsSp(17f, t.fs).sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
                            )
                        }
                        else -> MacroDetailFocusBody(
                            macro = liveMacro,
                            bodyLoaded = bodyLoaded,
                            rawMode = rawMode,
                            params = params,
                            values = values,
                            rawArgs = rawArgs,
                            onRawArgsChange = { rawArgs = it },
                            localToast = localToast,
                            running = running,
                            t = t,
                        )
                    }
                }
            },
            field = {
                when (fieldMode) {
                    is MacroFieldMode.Launcher -> MacroLauncherField(
                        state = state,
                        uDp = grid.uDp,
                        selectedName = selectedName,
                        onSelect = onSelect,
                        onManage = { onFieldModeChange(MacroFieldMode.ManageMode) },
                        onBack = onBack,
                        onExecute = { execute() },
                        executeEnabled = executeEnabled,
                    )
                    is MacroFieldMode.ManageMode -> MacroManageField(
                        state = state,
                        uDp = grid.uDp,
                        onToggleBookmark = onToggleBookmark,
                        onSetRevealHidden = onSetRevealHidden,
                        onBack = { onFieldModeChange(MacroFieldMode.Launcher) },
                    )
                }
            },
        )

        failureToast?.let { msg ->
            SeverityToast(
                Severity.Error,
                msg,
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MacroDetailFocusBody — the selected macro's detail inside the FocusFrame (ColumnScope content)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.MacroDetailFocusBody(
    macro: MacroVm,
    bodyLoaded: Boolean,
    rawMode: Boolean,
    params: List<MacroParam>,
    values: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
    rawArgs: String,
    onRawArgsChange: (String) -> Unit,
    localToast: String?,
    running: Boolean,
    t: works.mees.dinghy.theme.ThemeTokens,
) {
    if (!macro.description.isNullOrBlank()) {
        Text(
            text = macro.description,
            color = t.text2,
            fontFamily = Geist,
            fontSize = fsSp(17f, t.fs).sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    when {
        !bodyLoaded -> {
            Text(
                text = stringResource(R.string.macros_loading_params),
                color = t.text2,
                fontFamily = Geist,
                fontSize = fsSp(15f, t.fs).sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        rawMode -> {
            TokenTextField(
                value = rawArgs,
                onValueChange = onRawArgsChange,
                label = stringResource(R.string.macros_raw_args_label),
                keyboardType = KeyboardType.Text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.macros_raw_args_hint),
                color = t.text3,
                fontFamily = Geist,
                fontSize = fsSp(15f, t.fs).sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        else -> {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                params.forEach { param ->
                    if (param.isNumeric) {
                        MacroNumericParamField(
                            param = param,
                            rawValue = values[param.name].orEmpty(),
                            onValueChange = { raw -> values[param.name] = raw },
                            onValueCommit = { raw ->
                                val parsed = raw.toDoubleOrNull()
                                if (parsed != null) {
                                    values[param.name] = formatNumeric(
                                        parsed.coerceIn(MACRO_NUMERIC_RANGE.start, MACRO_NUMERIC_RANGE.endInclusive),
                                    )
                                } else if (raw.isEmpty()) {
                                    values[param.name] = ""
                                }
                            },
                            t = t,
                        )
                    } else {
                        TokenTextField(
                            value = values[param.name].orEmpty(),
                            onValueChange = { values[param.name] = it },
                            label = paramLabel(param),
                            keyboardType = KeyboardType.Text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    localToast?.let { msg ->
        SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
    }
    if (running) {
        SeverityToast(
            Severity.Info,
            stringResource(R.string.macros_running, macro.name),
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
    }
}

/** Param label with a `*` marker when the macro author expects the caller to supply it (required). */
private fun paramLabel(param: MacroParam): String =
    if (param.required) "${param.name} *" else param.name

// ─────────────────────────────────────────────────────────────────────────────
// MacroLauncherField — Bookmarked list (always visible) + foot bar [Back][Manage][Execute]
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.MacroLauncherField(
    state: MacroScreensState,
    uDp: androidx.compose.ui.unit.Dp,
    selectedName: String?,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    onBack: () -> Unit,
    onExecute: () -> Unit,
    executeEnabled: Boolean,
) {
    when {
        state.unavailable -> {
            MacrosUnavailable(modifier = Modifier.weight(1f).padding(24.dp))
        }
        state.bookmarkedMacros.isEmpty() -> {
            MacrosEmptyNotice(modifier = Modifier.weight(1f).padding(24.dp))
        }
        else -> {
            ListBlock(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                items(state.bookmarkedMacros, key = { it.name }) { macro ->
                    ListRow(
                        selected = macro.name.equals(selectedName, ignoreCase = true),
                        onClick = { onSelect(macro.name) },
                        uDp = uDp,
                    ) {
                        ListRowLabel(macro.name)
                    }
                }
            }
        }
    }
    FootButtonBar(uDp = uDp) {
        OutlinedControl(
            label = stringResource(R.string.macros_foot_back),
            onClick = onBack,
            modifier = Modifier.weight(1f),
            intent = Intent.Accent, // R5: Back = accent
            icon = DinghyIcons.Back,
        )
        OutlinedControl(
            label = stringResource(R.string.macros_foot_manage),
            onClick = onManage,
            modifier = Modifier.weight(1f),
            intent = Intent.Accent, // R5: plain navigation = accent
            icon = DinghyIcons.ManageMacros,
            contentDescription = stringResource(R.string.cd_macros_manage),
        )
        // Execute (R5: Go = the expected action). OutlinedControl has no `enabled` param — use
        // alpha+semantics (the established disabled convention).
        OutlinedControl(
            label = stringResource(R.string.macros_foot_execute),
            onClick = { if (executeEnabled) onExecute() },
            modifier = Modifier
                .weight(1f)
                .then(
                    if (!executeEnabled) Modifier.alpha(0.38f).semantics { disabled() } else Modifier,
                ),
            intent = Intent.Go,
            icon = DinghyIcons.ExecuteMacro,
            contentDescription = stringResource(R.string.cd_macros_execute),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MacroNumericParamField — inline numeric IME row for a single numeric param
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroNumericParamField(
    param: MacroParam,
    rawValue: String,
    onValueChange: (String) -> Unit,
    onValueCommit: (String) -> Unit,
    t: works.mees.dinghy.theme.ThemeTokens,
) {
    var editText by remember(param.name) { mutableStateOf(rawValue) }
    LaunchedEffect(rawValue) { if (editText != rawValue) editText = rawValue }

    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = paramLabel(param),
            color = t.text2,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(15f, t.fs).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BasicTextField(
            value = editText,
            onValueChange = { raw ->
                // Accept digits, an optional leading minus, and one decimal point only (no alpha, no
                // NaN/Infinity/exponent forms).
                if (raw.isEmpty() || raw.matches(Regex("-?\\d*\\.?\\d*"))) {
                    editText = raw
                    onValueChange(raw)
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(18f, t.fs).sp,
            ),
            cursorBrush = SolidColor(t.accent2),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onValueCommit(editText) },
            ),
            modifier = Modifier
                .clip(shape)
                .border(BorderStroke(2.dp, t.outline), shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            decorationBox = { innerField ->
                if (editText.isEmpty()) {
                    Text(
                        text = "0",
                        color = t.text3,
                        fontFamily = GeistMono,
                        fontSize = fsSp(18f, t.fs).sp,
                    )
                }
                innerField()
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MacroManageField — System manage-visibility list (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.MacroManageField(
    state: MacroScreensState,
    uDp: androidx.compose.ui.unit.Dp,
    onToggleBookmark: (String) -> Unit,
    onSetRevealHidden: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTokens.current
    if (state.unavailable) {
        MacrosUnavailable(modifier = Modifier.weight(1f).padding(24.dp))
    } else {
        Text(
            text = stringResource(R.string.macros_helper_hint),
            color = t.text2,
            fontFamily = Geist,
            fontSize = fsSp(15f, t.fs).sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        ListBlock(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
            items(state.visibleMacros, key = { it.name }) { macro ->
                val isBookmarked = macro.isBookmarked
                ListRow(
                    selected = isBookmarked,
                    onClick = { onToggleBookmark(macro.name) },
                    uDp = uDp,
                    trailingContent = {
                        DinghyIconView(
                            icon = if (isBookmarked) DinghyIcons.CheckCircle else DinghyIcons.UnbookmarkedMacro,
                            tint = if (isBookmarked) t.accent else t.text3,
                            sizeDp = fsSp(24f, t.fs).dp,
                            modifier = Modifier.padding(start = 8.dp),
                            contentDescription = stringResource(
                                if (isBookmarked) R.string.cd_macros_bookmarked else R.string.cd_macros_unbookmarked,
                            ),
                        )
                    },
                ) {
                    Text(
                        text = macro.name,
                        color = t.text,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fsSp(18f, t.fs).sp,
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
    FootButtonBar(uDp = uDp) {
        OutlinedControl(
            label = stringResource(R.string.macros_foot_back),
            onClick = onBack,
            modifier = Modifier.weight(1f),
            intent = Intent.Accent, // R5: Back = accent
            icon = DinghyIcons.Back,
        )
        OutlinedControl(
            label = stringResource(R.string.macros_foot_show_hidden),
            onClick = { onSetRevealHidden(!state.revealHidden) },
            modifier = Modifier.weight(1f),
            intent = if (state.revealHidden) Intent.Accent else Intent.Neutral,
            icon = if (state.revealHidden) DinghyIcons.Visibility else DinghyIcons.VisibilityOff,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty / unavailable states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacrosEmptyNotice(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(
            text = stringResource(R.string.macros_empty_title),
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(22f, t.fs).sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.macros_empty_body),
            color = t.text2,
            fontFamily = Geist,
            fontSize = fsSp(15f, t.fs).sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
internal fun MacrosUnavailable(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(
            text = stringResource(R.string.macros_unavailable),
            color = t.text2,
            fontFamily = Geist,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(16f, t.fs).sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Drop a trailing ".0" on a whole-number numeric value for clean display in the param field. */
private fun formatNumeric(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
```

- [ ] **Step 3: Build to verify the screen compiles (the preview file still references the old seam — that is fixed in Task 6)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: this may FAIL ONLY in `MacrosPreviews.kt` (it still passes `MacroFieldMode.ParamEntry`). That is corrected in Task 6. If any error is reported in `BookmarkedMacrosScreen.kt` itself, fix it before continuing. (If you prefer a clean compile, do Task 6 Step 1 before this build.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(macros): selected macro fills Focus, list stays in Field, Execute foot button"
```

---

## Task 6: Update previews + full verification

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/preview/MacrosPreviews.kt`

- [ ] **Step 1: Read the current preview file**

Read `app/src/main/java/works/mees/dinghy/preview/MacrosPreviews.kt` in full so the rewrite preserves its fixtures (`macrosLauncherState`, `macrosManageState`, `paramEntryMacro`) and preview-theme matrix structure.

- [ ] **Step 2: Replace the ParamEntry-mode previews with the new `selectedName` seam**

The stateless screen overload no longer has a `MacroFieldMode.ParamEntry(macro)`; a macro is shown in the Focus by passing `selectedName`. Apply these concrete edits:

a) Remove the `PreviewParameterProvider`'s ParamEntry row. Find:

```kotlin
        MacrosPreviewParams(macrosParamEntryState, MacroFieldMode.ParamEntry(paramEntryMacro)),
```
and replace it with a selected-macro preview row (add a `selectedName` field to `MacrosPreviewParams` — see (c)):

```kotlin
        MacrosPreviewParams(macrosLauncherState, MacroFieldMode.Launcher, selectedName = paramEntryMacro.name),
```

b) The matrix renderer call. Find:

```kotlin
        BookmarkedMacrosScreen(state = params.state, fieldMode = params.fieldMode)
```
replace with:

```kotlin
        BookmarkedMacrosScreen(
            state = params.state,
            fieldMode = params.fieldMode,
            selectedName = params.selectedName,
        )
```

c) The `MacrosPreviewParams` holder class. Find its declaration (a `class`/`data class` with `state` + `fieldMode`) and add a nullable `selectedName`:

```kotlin
private data class MacrosPreviewParams(
    val state: MacroScreensState,
    val fieldMode: MacroFieldMode,
    val selectedName: String? = null,
)
```

d) The landscape ParamEntry spot-check. Find:

```kotlin
        BookmarkedMacrosScreen(
            state = macrosParamEntryState,
            fieldMode = MacroFieldMode.ParamEntry(paramEntryMacro),
        )
```
replace with:

```kotlin
        BookmarkedMacrosScreen(
            state = macrosLauncherState,
            fieldMode = MacroFieldMode.Launcher,
            selectedName = paramEntryMacro.name,
        )
```

e) Ensure `paramEntryMacro` is included in `macrosLauncherState.macros` AND `.bookmarkedMacros` so it resolves when selected. If the fixture `macrosLauncherState` does not already contain it, add `paramEntryMacro` to both lists in the fixture. To exercise the new fields, give `paramEntryMacro` a description and verify it carries numeric + string params, e.g.:

```kotlin
private val paramEntryMacro = MacroVm(
    name = "LOAD_FILAMENT",
    isBookmarked = true,
    isHidden = false,
    params = listOf(
        MacroParam("TEMP", "int", "210", required = false),
        MacroParam("MATERIAL", null, "PLA", required = false),
    ),
    description = "Heat the nozzle and load filament.",
)
```

f) Remove the now-unused `macrosParamEntryState` val and any `import works.mees.dinghy.ui.macros.MacroFieldMode.ParamEntry` if present. Leave `import ...MacroFieldMode` (still used).

- [ ] **Step 3: Compile debug (previews are part of the main sourceset)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the FULL unit-test suite**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --rerun-tasks" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL — every macro test (parser, extraction, holder, invocation) + HandshakeTest green.

- [ ] **Step 5: Assemble the release/debug APK to confirm R8/packaging is clean**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/preview/MacrosPreviews.kt
git commit -m "test(macros): update previews to the selected-macro Focus seam"
```

---

## On-device UAT (after Codex review + fixes, before declaring done)

Not a code step — owner-driven. Per the test-devices memory, push the matching APK slice to BOTH flox (Nexus 7, armeabi-v7a) and moto (Moto G Play, arm64-v8a). Verify on a real printer (Ender 5 Plus @ 192.168.1.120:7125 or Ender 3 Pro @ 192.168.1.121:7125):

1. Open Macros → the launcher list shows bookmarked macros; the Focus shows "Select a macro to run."
2. Tap a macro → it fills the Focus (name as the title, description below if the macro declares one); the list stays visible; the row highlights.
3. A parameterized macro (e.g. a pause-at-layer) → numeric/string fields appear pre-filled with defaults; the system keyboard opens on tap; Execute sends the macro with the entered value.
4. A `rawparams`/no-param macro → a single "Arguments" field appears; Execute runs it (blank = bare macro).
5. Manage → pin/unpin + reveal-hidden still work; Back returns to the launcher.
6. Portrait AND landscape both usable.

⚠ Force a clean rebuild before the UAT install and confirm the APK mtime is AFTER the last fix commit (stale-APK trap).

---

## Self-Review (completed during planning)

- **Spec coverage:** description capture (Task 2) ✓; bracket-syntax/rawparams/required parser upgrades (Task 1) ✓; data-model additions (Tasks 1+3) ✓; Focus/Field grammar fix + empty-state Focus + Execute foot button + omit-empty-params (Task 5) ✓; raw-args field + sanitizer (Tasks 4+5) ✓; security sanitizer retained & extended (Task 4) ✓; tests for parser/extraction/holder/invocation (Tasks 1–4) ✓; non-goals (live vars/deps/subscribe) excluded ✓.
- **Deliberate spec deviations (flagged for Codex/owner):** (1) numeric-IME selection kept to explicit `int`/`double` only (NOT "default parses as a number") to avoid locking a user out of a field — owner guidance was "system keyboard, don't overthink." (2) `required` is captured + shown as a `*` marker but never gates Execute (matches "don't hard-gate"). (3) raw-args mode also covers macros with zero inferred params (every macro stays runnable), so the old `macros_no_params` notice is retired. (4) raw-args sanitizer rejects `"` (consistent REJECT posture) — quoted args unsupported in raw mode by design.
- **Placeholder scan:** none — every step has concrete code/commands.
- **Type consistency:** `MacroParam(name, type, default, required)`; `MacroVm(..., description, usesRawParams)`; `MacroConfigEntry(gcode, description)`; `extractMacroConfigs`; `usesRawParams`; `buildRaw`; `setMacroDescriptions`; `selectedName`/`onSelect` — names match across tasks.
```
