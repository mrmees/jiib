package works.mees.jiib.ui.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (08-01) — turned GREEN by 08-02 Task 1 (`ConsoleFilters`).
 *
 * REQ-CONS-02. The 3 built-in opt-in filters (default OFF), verbatim regexes from the Mainsail fork
 * @76fcbd2 (RESEARCH Code Examples / 08-PATTERNS). D-04: filters are a VIEW-layer concern — they
 * NEVER mutate the input (raw stream survives a toggle-off).
 *
 * Production symbols referenced (NOT YET BUILT → RED): [ConsoleFilters] +
 * [ConsoleFilters.HIDE_TEMPERATURES], [ConsoleFilters.HIDE_TIMELAPSE],
 * [ConsoleFilters.HIDE_PROMPT_COMMANDS], [ConsoleFilters.apply].
 */
class ConsoleFiltersTest {

    @Test
    fun hideTemperatures_matchesTempReportForms() {
        assertTrue(ConsoleFilters.HIDE_TEMPERATURES.containsMatchIn("ok T:210.0 /210.0 B:60.0 /60.0"))
        assertTrue(ConsoleFilters.HIDE_TEMPERATURES.containsMatchIn("B:60.0 /60.0"))
        assertTrue(ConsoleFilters.HIDE_TEMPERATURES.containsMatchIn("T0:210.0 /210.0"))
        assertFalse(ConsoleFilters.HIDE_TEMPERATURES.containsMatchIn("Done printing file"))
    }

    @Test
    fun hideTimelapse_coversTheSixRulePrefixes() {
        val timelapseLines = listOf(
            "_TIMELAPSE_NEW_FRAME FRAME=1",
            "TIMELAPSE_TAKE_FRAME",
            "TIMELAPSE_RENDER",
            "_SET_TIMELAPSE_SETUP",
            "HYPERLAPSE ACTION=START",
            "SET_GCODE_VARIABLE MACRO=TIMELAPSE_HASH VARIABLE=x VALUE=1",
        )
        assertEquals(6, timelapseLines.size)
        timelapseLines.forEach { line ->
            assertTrue("a timelapse rule must match: $line", ConsoleFilters.HIDE_TIMELAPSE.any { it.containsMatchIn(line) })
        }
        assertFalse(ConsoleFilters.HIDE_TIMELAPSE.any { it.containsMatchIn("M104 S200") })
    }

    @Test
    fun hidePromptCommands_matchesBothRawAndStrippedForms() {
        assertTrue(ConsoleFilters.HIDE_PROMPT_COMMANDS.containsMatchIn("// action:prompt_begin Test"))
        assertTrue(ConsoleFilters.HIDE_PROMPT_COMMANDS.containsMatchIn("action:prompt_button OK"))
        assertFalse(ConsoleFilters.HIDE_PROMPT_COMMANDS.containsMatchIn("// action:something_else"))
    }

    @Test
    fun normalLine_matchesNoFilter() {
        val normal = "Done printing file"
        assertFalse(ConsoleFilters.HIDE_TEMPERATURES.containsMatchIn(normal))
        assertFalse(ConsoleFilters.HIDE_TIMELAPSE.any { it.containsMatchIn(normal) })
        assertFalse(ConsoleFilters.HIDE_PROMPT_COMMANDS.containsMatchIn(normal))
    }

    @Test
    fun apply_neverMutatesInput_d04RawSurvives() {
        val raw = listOf(
            ConsoleLine(rawMessage = "ok T:210.0 /210.0 B:60.0 /60.0", severity = ConsoleSeverity.NORMAL, timeEpoch = null),
            ConsoleLine(rawMessage = "Done printing file", severity = ConsoleSeverity.NORMAL, timeEpoch = null),
        )
        val rawSnapshot = raw.toList()
        val filtered = ConsoleFilters.apply(raw, hideTemperatures = true, hideTimelapse = false, hidePrompt = false)
        // The temp line is hidden in the VIEW...
        assertEquals(listOf("Done printing file"), filtered.map { it.rawMessage })
        // ...but the input list is untouched (D-04 raw survives → toggle-off re-reveals).
        assertEquals(rawSnapshot, raw)
    }
}
