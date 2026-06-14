package works.mees.dinghy.ui.macros

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import works.mees.dinghy.net.MoonrakerJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (08-01) — turned GREEN by 08-03 (`MacroParamParser`).
 *
 * REQ-MACRO-02. Mock-vs-reality discipline (Pitfall 6): the macro bodies fed to the parser are the
 * REAL probed Ender 5 bodies committed as `/fixtures/macro_bodies_e5.json` (probed 2026-06-02, see
 * docs/moonraker-capabilities.md "Phase 8 — gcode_store + macro-body shapes"), NOT invented strings.
 * The expected name/type/default tuples are the GROUND-TRUTH the Mainsail `paramRegex` actually
 * extracts from each body — including the heuristic's real quirk that a trailing `|float` after a
 * `|default(...)` is NOT captured as a type (it falls into the regex `.*?` tail).
 *
 * Production symbol referenced (NOT YET BUILT → RED): [MacroParamParser.parseMacroParams] +
 * [MacroParam]. References fail to compile until 08-03 lands them.
 */
class MacroParamParserTest {

    /** Load a real probed macro body from the committed fixture (NOT an invented string). */
    private fun body(sectionKey: String): String {
        val res = javaClass.getResource("/fixtures/macro_bodies_e5.json")
            ?: error("fixture /fixtures/macro_bodies_e5.json missing from test classpath")
        val root: JsonObject = MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
        val section = root[sectionKey]?.jsonObject
            ?: error("macro section $sectionKey absent from fixture")
        return section["gcode"]!!.jsonPrimitive.content
    }

    @Test
    fun startPrint_extractsBedAndExtruderTempDefaults() {
        // Real body declares: params.BED_TEMP|default(60)|float, params.EXTRUDER_TEMP|default(210)|float
        val params = MacroParamParser.parseMacroParams(body("gcode_macro start_print"))
        assertEquals(listOf("BED_TEMP", "EXTRUDER_TEMP"), params.map { it.name })
        val bed = params.first { it.name == "BED_TEMP" }
        assertEquals("60", bed.default)
        // Heuristic reality: trailing |float is NOT captured as a type (default group consumed 60).
        assertNull("trailing |float is not a captured type per the Mainsail regex", bed.type)
        assertEquals("210", params.first { it.name == "EXTRUDER_TEMP" }.default)
    }

    @Test
    fun setPauseAtLayer_capturesIntTypeAndDefault() {
        // Real body declares: params.LAYER|default(pause_at_layer.layer)|int
        val params = MacroParamParser.parseMacroParams(body("gcode_macro set_pause_at_layer"))
        val layer = params.first { it.name == "LAYER" }
        assertEquals("int", layer.type)
        assertEquals("pause_at_layer.layer", layer.default)
    }

    @Test
    fun setPauseNextLayer_enableIntDefaultOne() {
        // Real body declares: params.ENABLE|default(1)|int
        val params = MacroParamParser.parseMacroParams(body("gcode_macro set_pause_next_layer"))
        val enable = params.first { it.name == "ENABLE" }
        assertEquals("int", enable.type)
        assertEquals("1", enable.default)
    }

    @Test
    fun clientExtrude_lengthAndSpeedDefaults() {
        // Real body declares: params.LENGTH|default(client.unretract), params.SPEED|default(...)
        val params = MacroParamParser.parseMacroParams(body("gcode_macro _client_extrude")).map { it.name }
        assertTrue(params.containsAll(listOf("LENGTH", "SPEED")))
    }

    @Test
    fun noParamMacro_yieldsEmptyList() {
        // _pause_on_switch declares no params.X — must be an empty list, never a crash.
        assertEquals(emptyList<MacroParam>(), MacroParamParser.parseMacroParams(body("gcode_macro _pause_on_switch")))
    }

    @Test
    fun inParamsGuardForm_addsNullTypeNullDefault() {
        // The single-quoted `'X' in params` membership-guard idiom is ABSENT on the live E5 (see
        // docs/moonraker-capabilities.md), so this one form is exercised with a clearly-labelled
        // SYNTHETIC inline body — the only invented string here, and only for a form the printer
        // does not carry. (PARAM_IN_REGEX path.)
        val synthetic = "{% if 'NOZZLE' in params %}\nM104 S{params.NOZZLE}\n{% endif %}"
        val params = MacroParamParser.parseMacroParams(synthetic)
        val nozzle = params.first { it.name == "NOZZLE" }
        assertNull(nozzle.type)
        assertNull(nozzle.default)
        assertFalse("a 'X' in params guarded param is optional, not required", nozzle.required)
    }

    @Test
    fun bracketSyntaxParam_isDiscovered() {
        // Codex doc: params["NAME"] / params['NAME'] bracket access is a real Klipper idiom the
        // verbatim Mainsail dot-regex misses. The new bracket pass must discover it.
        val body = """{% set p = params["PROFILE"] %} BED_MESH_PROFILE LOAD={p}"""
        val params = MacroParamParser.parseMacroParams(body)
        assertTrue("bracket param PROFILE must be discovered", params.any { it.name == "PROFILE" })
    }

    @Test
    fun bracketSyntaxSingleQuote_isDiscovered() {
        val params = MacroParamParser.parseMacroParams("M104 S{params['EXTRUDER']}")
        assertTrue(params.any { it.name == "EXTRUDER" })
    }

    @Test
    fun bracketSyntaxWithDefault_isOptionalAndCapturesDefault() {
        // BLOCK-1 fix: a bracket param WITH a |default(...) must be optional and pre-fill the default,
        // exactly like the dot-access path — not silently forced required.
        val params = MacroParamParser.parseMacroParams("""BED_MESH_PROFILE LOAD={params["PROFILE"]|default('default')}""")
        val p = params.first { it.name == "PROFILE" }
        assertFalse("bracket param with a default is optional", p.required)
        assertEquals("default", p.default)
    }

    @Test
    fun bracketSyntaxWithoutDefault_isRequired() {
        val params = MacroParamParser.parseMacroParams("""BED_MESH_PROFILE LOAD={params["PROFILE"]}""")
        assertTrue(params.first { it.name == "PROFILE" }.required)
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

    @Test
    fun dualFormParam_dotDefaultWins_regardlessOfSourceOrder() {
        // A param referenced via BOTH bracket (no default, appearing FIRST) and dot (with a default).
        // The dot pass runs as a complete loop before the bracket pass, so the dot form's default and
        // optional status must win — the earlier bracket usage must NOT mark it required or drop the default.
        val body = """
            M104 S{params["BED_TEMP"]}
            {% set t = params.BED_TEMP|default(60)|float %}
        """.trimIndent()
        val params = MacroParamParser.parseMacroParams(body)
        val bed = params.first { it.name == "BED_TEMP" }
        assertEquals("60", bed.default)
        assertFalse("dot-form default makes it optional even though bracket usage came first", bed.required)
    }
}
