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

    // --- REAL wiring assertions (codex review: not a Slot tautology) ----------------------------
    // These exercise the live CommandRegistry specs + PrinterCommands builders symbolically:
    // whatever names CommandMap carries, the registry GATES on `.macro` and the wire EMITS
    // `.gcode`. A future re-hardcode at any of the six production sites breaks these without
    // a single macro literal appearing here.

    @Test
    fun registry_loadAndUnload_gateOnCommandMapMacros() {
        assertEquals(
            AvailabilityPredicate.MacroPresent(CommandMap.loadFilament.macro),
            CommandRegistry.loadFilament.availability,
        )
        assertEquals(
            AvailabilityPredicate.MacroPresent(CommandMap.unloadFilament.macro),
            CommandRegistry.unloadFilament.availability,
        )
    }

    @Test
    fun registry_loadAndUnload_emitCommandMapGcode() {
        // gcode specs carry the script in params — assert the wire payload carries the slot's
        // gcode string, and the PrinterCommands builders return it verbatim.
        val loadParams = CommandRegistry.loadFilament.params(Unit).toString()
        val unloadParams = CommandRegistry.unloadFilament.params(Unit).toString()
        assertEquals(true, loadParams.contains(CommandMap.loadFilament.gcode))
        assertEquals(true, unloadParams.contains(CommandMap.unloadFilament.gcode))
        assertEquals(CommandMap.loadFilament.gcode, PrinterCommands.loadFilament())
        assertEquals(CommandMap.unloadFilament.gcode, PrinterCommands.unloadFilament())
    }
}
