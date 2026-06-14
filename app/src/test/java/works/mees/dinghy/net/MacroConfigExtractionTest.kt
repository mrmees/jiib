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

    @Test
    fun descriptionFallsBackToConfigWhenSettingsLacksIt() {
        // Spec description priority: settings[section].description THEN config[section].description.
        // configfile.config preserves the RAW (non-lowercased) section name, so the fallback match is
        // case-insensitive against the lowercased settings key.
        val s = settings("""{ "gcode_macro print_start": { "gcode": "G28" } }""")
        val config = settings("""{ "gcode_macro PRINT_START": { "description": "Starts the print", "gcode": "G28" } }""")
        val configs = extractMacroConfigs(s, config)
        assertEquals("Starts the print", configs["print_start"]?.description)
    }

    @Test
    fun settingsDescriptionWins_overConfig() {
        val s = settings("""{ "gcode_macro m": { "description": "from settings", "gcode": "G28" } }""")
        val config = settings("""{ "gcode_macro M": { "description": "from config", "gcode": "G28" } }""")
        assertEquals("from settings", extractMacroConfigs(s, config)["m"]?.description)
    }
}
