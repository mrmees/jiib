package works.mees.jiib.prompt

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The 26-fixture conformance gate (PROMPT-01 / D-14) — the renderer-neutral oracle every prompt frontend
 * validates against. Ports `packages/js/test/fixtures.spec.ts`: replay each fixture's events through
 * `parseAction`/`reduce`, project `promptView(state)`, then partially-compare against `expected` /
 * `expected_by_frontend` (toMatchObject semantics: absent ≠ default; `size:null` is an asserted-null
 * distinct from absent; fully-specified arrays compare exact-length; `scale:1` is a numeric Int-vs-Double
 * compare). The EXACT-6-keys structural guard proves no internal reducer field leaks (T-12-07).
 *
 * Dinghy frontend identity (D-08): `frontendId = "jiib"`, `frontendCategories = ["touch"]` — so Dinghy
 * matches `all`, `jiib`, and `touch`. The JS driver maps `klipperscreen → ["touch"]` and everything else
 * → `["web"]`; the Kotlin port adds a `dinghy → ["touch"]` row. Because Dinghy carries category `touch`,
 * `target-touch-only` (targets `klipperscreen,touch`) is VISIBLE for Dinghy (it behaves like `klipperscreen`,
 * not the hidden `mainsail`/`fluidd`).
 */
class PromptFixtureTest {

    /** The 6 keys `promptView` exposes, sorted — no leaked internal fields (EXACT_VIEW_KEYS). */
    private val exactViewKeys = listOf("footer_buttons", "items", "size", "targets", "title", "visible")

    /**
     * GREEN immediately: the corpus loads, declares schema_version 1, and carries exactly 26 fixtures
     * (8 core + 18 optional). This guard does NOT touch the reducer.
     */
    @Test
    fun corpusGuard() {
        val doc = PromptFixtures.document()
        val schemaVersion = doc["schema_version"]!!.jsonPrimitive.content.toInt()
        assertEquals("fixtures.json schema_version must be 1", 1, schemaVersion)
        assertEquals("fixtures.json must carry exactly 26 fixtures", 26, PromptFixtures.fixtures().size)
    }

    /**
     * The conformance gate: replay every fixture under the Dinghy identity (or each named frontend identity
     * for `expected_by_frontend`), fold the events with `parseAction`/`disconnectEvent` → `reduce`, project
     * `promptView`, assert the EXACT-6-keys shape, then `assertMatchesPartial(view, expected)`.
     */
    @Test
    fun allFixturesConform() {
        val failures = mutableListOf<String>()
        for (fx in PromptFixtures.fixtureObjects()) {
            val id = fx["id"]!!.jsonPrimitive.content
            if (fx["skip"]?.jsonPrimitive?.content == "true") continue
            val events = fx["events"]!!.jsonArray.map { it.jsonPrimitive.content }

            val byFrontend = fx["expected_by_frontend"]?.jsonObject
            if (byFrontend != null) {
                for ((frontendKey, expectedForFrontend) in byFrontend) {
                    val categories = if (frontendKey in setOf("klipperscreen", "jiib")) {
                        listOf("touch")
                    } else {
                        listOf("web")
                    }
                    val opts = PromptOpts(frontendId = frontendKey, frontendCategories = categories, liveAppend = true)
                    runOne(id, "frontend=$frontendKey", events, opts, expectedForFrontend, failures)
                }
            } else {
                val expected = fx["expected"]
                    ?: error("fixture $id has neither `expected` nor `expected_by_frontend`")
                // Replay single-run fixtures under Dinghy's own identity (D-08); all carry targets:["all"]
                // so they are visible regardless, but this exercises the real Dinghy opts.
                runOne(id, "jiib", events, dinghyOpts(), expected, failures)
            }
        }
        if (failures.isNotEmpty()) {
            fail("conformance failures (${failures.size}):\n" + failures.joinToString("\n"))
        }
    }

    private fun dinghyOpts() = PromptOpts(frontendId = "jiib", frontendCategories = listOf("touch"), liveAppend = true)

    /** Fold one fixture's events under [opts], project the view, assert shape + partial value match. */
    private fun runOne(
        id: String,
        label: String,
        events: List<String>,
        opts: PromptOpts,
        expected: JsonElement,
        failures: MutableList<String>,
    ) {
        var s = initialPromptState(opts)
        for (line in events) {
            val ev = if (line == "__disconnect__") disconnectEvent() else parseAction(line)
            if (ev != null) s = reduce(s, ev)
        }
        val viewJson = viewToJson(promptView(s))

        // Exact shape: no leaked internal fields.
        val keys = viewJson.keys.sorted()
        if (keys != exactViewKeys) {
            failures.add("[$id/$label] view keys $keys != $exactViewKeys")
            return
        }
        try {
            assertMatchesPartial("$id/$label", viewJson, expected)
        } catch (e: AssertionError) {
            failures.add("[$id/$label] ${e.message}")
        }
    }

    /**
     * The `toMatchObject` comparator (RESEARCH step 4): for each key present in [expected], deep-compare
     * against [actual] — objects recurse partially (absent ≠ default), fully-specified arrays compare
     * exact-length and element-wise, scalars equal, numbers compare numerically (`scale:1` Int vs `0.75`
     * Double), and an asserted `null` (e.g. `size:null`) is distinct from absent.
     */
    private fun assertMatchesPartial(where: String, actual: JsonObject, expected: JsonElement) {
        require(expected is JsonObject) { "$where: top-level expected must be an object" }
        matchObject(where, actual, expected)
    }

    private fun matchObject(path: String, actual: JsonObject, expected: JsonObject) {
        for ((key, expectedVal) in expected) {
            val actualVal = actual[key]
            assertTrue("$path: missing key `$key`", actual.containsKey(key))
            matchValue("$path.$key", actualVal!!, expectedVal)
        }
    }

    private fun matchValue(path: String, actual: JsonElement, expected: JsonElement) {
        when (expected) {
            is JsonObject -> {
                assertTrue("$path: expected object, got $actual", actual is JsonObject)
                matchObject(path, actual as JsonObject, expected)
            }
            is JsonArray -> {
                assertTrue("$path: expected array, got $actual", actual is JsonArray)
                val a = (actual as JsonArray)
                // Fully-specified arrays in the corpus → exact-length element-wise compare.
                assertEquals("$path: array length", expected.size, a.size)
                for (i in expected.indices) matchValue("$path[$i]", a[i], expected[i])
            }
            is JsonNull -> assertTrue("$path: expected null, got $actual", actual is JsonNull)
            is JsonPrimitive -> matchPrimitive(path, actual, expected)
        }
    }

    private fun matchPrimitive(path: String, actual: JsonElement, expected: JsonPrimitive) {
        assertTrue("$path: expected primitive $expected, got $actual", actual is JsonPrimitive)
        val a = actual as JsonPrimitive
        val eNum = expected.content.toDoubleOrNull()
        if (eNum != null && !expected.isString) {
            // Numeric compare so scale:1 (Int) and 0.75 (Double) unify.
            val aNum = a.content.toDoubleOrNull()
            assertTrue("$path: expected number $expected, got $actual", aNum != null)
            assertEquals("$path: number", eNum, aNum!!, 0.0)
        } else {
            assertEquals("$path", expected.content, a.content)
        }
    }

    // ---- View → JSON projection (mirrors the JS PromptView shape the fixtures assert) ----------------

    /** Serialize a [PromptView] to the canonical conformance JSON shape (snake_case keys, align omitted at center). */
    private fun viewToJson(view: PromptView): JsonObject = buildJsonObject {
        put("visible", JsonPrimitive(view.visible))
        put("title", JsonPrimitive(view.title))
        put("targets", buildJsonArray { view.targets.forEach { add(JsonPrimitive(it)) } })
        put("size", view.size?.let { JsonPrimitive(it.token) } ?: JsonNull)
        put("items", buildJsonArray { view.items.forEach { add(itemToJson(it)) } })
        put("footer_buttons", buildJsonArray { view.footerButtons.forEach { add(footerToJson(it)) } })
    }

    private fun itemToJson(item: PromptItem): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(item.type.wire))
        when (item.type) {
            PromptItemType.TEXT -> put("text", JsonPrimitive(item.text!!))
            PromptItemType.MARKUP -> {
                put("markup", JsonPrimitive(item.markup!!))
                put("plain_text", JsonPrimitive(item.plainText!!))
            }
            PromptItemType.IMAGE -> {
                put("path", JsonPrimitive(item.path!!))
                put("alt", JsonPrimitive(item.alt!!))
                put("scale", item.scale?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            PromptItemType.BUTTON -> {
                put("label", JsonPrimitive(item.label!!))
                put("gcode", JsonPrimitive(item.gcode!!))
                put("style", JsonPrimitive(item.style!!.name.lowercase()))
            }
            PromptItemType.ROW, PromptItemType.BUTTON_GROUP ->
                put("children", buildJsonArray { item.children.forEach { add(itemToJson(it)) } })
        }
        // align is stamped only for left/right top-level items (center omits the field).
        item.align?.let { if (it != PromptAlign.CENTER) put("align", JsonPrimitive(it.token)) }
    }

    private fun footerToJson(b: FooterButton): JsonObject = buildJsonObject {
        put("label", JsonPrimitive(b.label))
        put("gcode", JsonPrimitive(b.gcode))
        put("style", JsonPrimitive(b.style.name.lowercase()))
    }
}
