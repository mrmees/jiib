package works.mees.dinghy.ui.macros

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import works.mees.dinghy.net.MoonrakerJson
import org.junit.Assert.assertEquals
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
    }
}
