package works.mees.dinghy.state

/**
 * Toolkit-agnostic, immutable capability model (STATE-02) — exactly the v1 gating surface, derived
 * (pure function, Wave 2) from `printer.objects.list`. Like [PrinterState] this is a PLAIN data
 * class with NO Compose annotations: it is part of the headless spine consumed by both Compose and
 * classic Views (ADR 0001). It is re-derived on every reconnect; do not over-build a generic mirror
 * of everything `objects.list` returns — model only what v1 panels actually gate on.
 */
data class Capabilities(
    /** Whether a `heater_bed` exists. */
    val hasBed: Boolean = false,

    /** Count of `extruder`, `extruder1`, `extruder2`, ... (toolchanger/multi-extruder support). */
    val extruderCount: Int = 0,

    /** Fan object names: `fan`, `fan_generic X`, `heater_fan X`, `controller_fan X`. */
    val fans: List<String> = emptyList(),

    /** User macro names (the `NAME` part of `gcode_macro NAME`). */
    val macros: List<String> = emptyList(),

    /**
     * Power-device names.
     *
     * OUT OF SCOPE FOR PHASE 2 (A4): power devices come from the `machine.device_power.devices`
     * API — a DIFFERENT Moonraker surface, NOT `printer.objects.list`. This field is modeled here
     * for the downstream contract but is ALWAYS EMPTY in Phase 2; population is deferred to a later
     * phase. (Documented so verify-phase does not flag success-criterion #5 as a stub.)
     */
    val powerDevices: List<String> = emptyList(),

    /** All heater object names: `heater_bed`, `extruder*`, `heater_generic *`. */
    val heaters: List<String> = emptyList(),
) {
    /**
     * Case-insensitive macro presence (EXTR-02 / D-10). Moonraker reports macro object names
     * LOWERCASE (Pitfall 2) — e.g. `gcode_macro load_filament` derives to `load_filament` here — so a
     * case-sensitive `==` against a caller's `"LOAD_FILAMENT"` would miss it. Gating (e.g. the
     * Extrude panel's load/unload buttons) must use this, not a raw [macros] membership check.
     */
    fun hasMacroIgnoreCase(name: String): Boolean = macros.any { it.equals(name, ignoreCase = true) }
}
