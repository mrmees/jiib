package works.mees.dinghy.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden-conformance: the Kotlin `Palette` port (plan 15-02) must match the
 * `color.js` oracle bit-for-bit across the 7 committed vectors in
 * `app/src/test/resources/color-golden.json` (laid down by plan 15-01).
 *
 * The fixture IS the conformance contract — a single transcription typo in any of the
 * 25+ OKLab matrix constants, or Int-division in channel normalization, produces silent
 * color drift only these golden vectors catch. Each method loads its fixture entry, runs
 * `Palette.generate(...)` with the entry's `opts`, and asserts accent / secondary /
 * surfaces / pool / poolHues / poolRoles / directional / status / minHueGap all equal.
 */
class PaletteGoldenTest {

    private val fixture: JsonObject by lazy {
        val text = javaClass.getResourceAsStream("/color-golden.json")!!
            .bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonObject
    }

    /** Generate from a fixture entry's `opts` and assert every field matches the oracle. */
    private fun assertVector(key: String) {
        val entry = fixture[key]!!.jsonObject
        val opts = entry["opts"]!!.jsonObject

        fun optStr(name: String, default: String): String =
            opts[name]?.jsonPrimitive?.content ?: default

        fun optBool(name: String, default: Boolean): Boolean =
            opts[name]?.jsonPrimitive?.content?.toBoolean() ?: default

        fun optInt(name: String, default: Int): Int =
            opts[name]?.jsonPrimitive?.content?.toInt() ?: default

        val result = Palette.generate(
            seedHex = optStr("seedHex", "#3f78ff"),
            dark = optBool("dark", true),
            maxItems = optInt("maxItems", 3),
            poolShift = optInt("poolShift", 0),
            statusFromPool = optBool("statusFromPool", true),
            simple = optBool("simple", false),
            highContrast = optBool("highContrast", false),
        )

        fun str(name: String): String = entry[name]!!.jsonPrimitive.content
        fun strList(name: String): List<String> =
            entry[name]!!.jsonArray.map { it.jsonPrimitive.content }
        fun dblList(name: String): List<Double> =
            entry[name]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() }

        assertEquals("$key accent", str("accent"), result.theme.primary)
        assertEquals("$key secondary", str("secondary"), result.theme.secondary)
        assertEquals("$key bg", str("bg"), result.surfaces.bg)
        assertEquals("$key surface", str("surface"), result.surfaces.surface)
        assertEquals("$key divider", str("divider"), result.surfaces.divider)
        assertEquals("$key text", str("text"), result.surfaces.text)
        assertEquals("$key muted", str("muted"), result.surfaces.muted)
        assertEquals("$key pool", strList("pool"), result.pool)
        // poolHues are float-accumulated DIAGNOSTIC values (color.js comment: "for
        // diagnostics"). They derive from the seed hue, whose atan2/cbrt last-bit value
        // differs ~1e-13 between the JS oracle and JVM `kotlin.math` — harmless because the
        // RENDERED output (pool hexes, minHueGap) re-quantizes to 8-bit and matches exactly.
        // Assert with a tight epsilon, not exact bit-equality, on these intermediate doubles.
        val expectedHues = dblList("poolHues")
        assertEquals("$key poolHues size", expectedHues.size, result.poolHues.size)
        expectedHues.forEachIndexed { i, exp ->
            assertEquals("$key poolHues[$i]", exp, result.poolHues[i], 1e-9)
        }
        assertEquals(
            "$key poolRoles",
            entry["poolRoles"]!!.jsonArray.map {
                if (it.jsonPrimitive.content == "null") null else it.jsonPrimitive.content
            },
            result.poolRoles,
        )

        val dir = entry["directional"]!!.jsonObject
        assertEquals("$key directional.temperature", dir["temperature"]!!.jsonPrimitive.content, result.directional.temperature)
        assertEquals("$key directional.xy", dir["xy"]!!.jsonPrimitive.content, result.directional.xy)
        assertEquals("$key directional.z", dir["z"]!!.jsonPrimitive.content, result.directional.z)

        val status = entry["status"]!!.jsonObject
        assertEquals("$key status.stop", status["stop"]!!.jsonPrimitive.content, result.status.stop)
        assertEquals("$key status.caution", status["caution"]!!.jsonPrimitive.content, result.status.caution)
        assertEquals("$key status.go", status["go"]!!.jsonPrimitive.content, result.status.go)

        assertEquals("$key minHueGap", entry["minHueGap"]!!.jsonPrimitive.content.toInt(), result.minHueGap)
        assertEquals("$key poolShift", entry["poolShift"]!!.jsonPrimitive.content.toInt(), result.poolShift)
    }

    @Test
    fun defaultSeedDark_matchesOracle() = assertVector("defaultDark")

    @Test
    fun defaultSeedLight_matchesOracle() = assertVector("defaultLight")

    @Test
    fun simpleMode_matchesOracle() = assertVector("simple")

    @Test
    fun highContrastMode_matchesOracle() = assertVector("highContrast")

    @Test
    fun edgeHueRed_matchesOracle() = assertVector("edgeRed")

    @Test
    fun edgeHueYellow_matchesOracle() = assertVector("edgeYellow")

    @Test
    fun poolShift120_matchesOracle() = assertVector("shift120")
}
