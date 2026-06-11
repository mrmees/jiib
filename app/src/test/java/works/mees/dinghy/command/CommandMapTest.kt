package works.mees.dinghy.command

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Host-side proof for [CommandMap] (R4, audit roadmap Part 3): the build-time fork-and-edit
 * customization point. Default slots carry the stock Klipper macro names; a [CommandMap.Slot]
 * keeps `macro` (the capability-gating name) and `gcode` (the wire emission) independent so a
 * fork can rename one without breaking the other.
 *
 * The PROPAGATION half of the R4 acceptance (a renamed slot both gates and fires through the
 * REAL wiring — ExtrudeHolder gating + PrinterCommands emission) lives in [ExtrudeHolderTest]'s
 * symbolic-reference cases.
 */
class CommandMapTest {

    @Test
    fun defaultSlots_matchKnownMacroNames() {
        assertEquals("LOAD_FILAMENT", CommandMap.loadFilament.macro)
        assertEquals("LOAD_FILAMENT", CommandMap.loadFilament.gcode) // gcode defaults to macro
        assertEquals("UNLOAD_FILAMENT", CommandMap.unloadFilament.macro)
        assertEquals("UNLOAD_FILAMENT", CommandMap.unloadFilament.gcode)
    }

    @Test
    fun slot_withCustomGcode_macroAndGcodeAreIndependent() {
        // The user-override use case: gate on the macro name, send a full command line.
        val slot = CommandMap.Slot(macro = "M701", gcode = "M701 PURGE=1")
        assertEquals("M701", slot.macro)
        assertEquals("M701 PURGE=1", slot.gcode)
    }

    @Test
    fun renamedSlot_gatesAndEmitsNewStrings() {
        // Simulate a fork's rename: the slot's macro/gcode are exactly what CommandRegistry's
        // MacroPresent / ExtrudeHolder's hasMacroIgnoreCase (gate) and PrinterCommands (emit)
        // would consume after a one-constant edit in CommandMap.kt.
        val renamed = CommandMap.Slot(macro = "MY_LOAD", gcode = "MY_LOAD BEEP=1")
        assertEquals("MY_LOAD", renamed.macro)          // gates via hasMacroIgnoreCase
        assertEquals("MY_LOAD BEEP=1", renamed.gcode)   // fires via loadFilament()
    }
}
