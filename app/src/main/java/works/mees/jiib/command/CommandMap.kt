package works.mees.jiib.command

/**
 * THE fork-and-edit customization point. If your Klipper config maps a documented
 * system function to a different macro name, change it HERE and rebuild — nothing
 * else in the app hardcodes these names.
 *
 * Each entry is both (a) the gcode the app sends and (b) the macro name whose
 * presence (case-insensitive, via printer.objects.list) gates the button. If your
 * macro takes no args, the bare name is fine; a full command line also works:
 *   override val loadFilament = Slot(macro = "M701")               // marlin-style alias
 *   override val unloadFilament = Slot(macro = "FILAMENT_EJECT", gcode = "FILAMENT_EJECT BEEP=1")
 *
 * Explicit non-goal: safety commands (`M112` / `emergency_stop`) are NEVER mapped
 * here — they stay absolute and unremappable by design (audit roadmap Part 3 non-goals).
 */
object CommandMap {
    data class Slot(val macro: String, val gcode: String = macro)

    val loadFilament   = Slot("LOAD_FILAMENT")
    val unloadFilament = Slot("UNLOAD_FILAMENT")

    // Forward slots — wire these up as the corresponding features gain buttons,
    // so future hardcoding lands here instead of in a screen:
    // val parkToolhead = Slot("PARK")          // pre-maintenance park
    // val purgeLine    = Slot("PURGE_LINE")    // pre-print purge if exposed in UI
}
