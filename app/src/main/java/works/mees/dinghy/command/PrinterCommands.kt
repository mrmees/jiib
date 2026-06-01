package works.mees.dinghy.command

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * PURE gcode builders for every Phase-5 print-control action (Move / Temperature / Extrude). Mirrors
 * the [works.mees.dinghy.state.deriveCapabilities] purity discipline: NO I/O, NO coroutines, NO Compose;
 * same input → same output; fully host-testable off-hardware. These functions produce the exact gcode
 * STRINGS the dispatch layer ships via `printer.gcode.script`
 * ([works.mees.dinghy.net.JsonRpcMethods.GCODE_SCRIPT]); [scriptParams] wraps a string into the
 * `{"script": <gcode>}` [JsonElement] that request carries.
 *
 * SECURITY (ASVS V5, T-05-02-T): every numeric param is CLAMPED to a named bounded range BEFORE it is
 * formatted into a string. The only non-numeric pieces interpolated are FIXED axis/heater identifiers
 * (validated against fixed sets) — never free-text. No user-supplied string is ever concatenated into a
 * script. Callers feed these builders ONLY bounded scrubber/selector input.
 *
 * SAFETY (T-05-02-Safety): every builder that changes motion/extrusion MODE (relative jog via G91,
 * relative extrude via M83) wraps the move in SAVE_GCODE_STATE/RESTORE_GCODE_STATE, so the printer is
 * never left in a relative mode after the action (RESEARCH §5).
 */
object PrinterCommands {

    // --- Clamp bounds (named consts — ASVS V5) ----------------------------------------------------
    /** Heater target ceiling (°C). Klipper rejects beyond a heater's configured max anyway; this is
     * the UI-side hard cap so no absurd target is ever formatted. */
    const val MAX_TEMP_C = 350
    const val MIN_TEMP_C = 0

    /** Jog distance magnitude cap (mm) — the Move pad never offers a step beyond this. */
    const val MAX_JOG_MM = 200.0

    /** Jog feedrate bounds (mm/min). */
    const val MIN_FEED_MM_MIN = 1
    const val MAX_JOG_FEED_MM_MIN = 30_000

    /** Extrude distance magnitude cap (mm) and feedrate ceiling (mm/min). */
    const val MAX_EXTRUDE_MM = 100.0
    const val MAX_EXTRUDE_FEED_MM_MIN = 6_000

    /** Force-move velocity ceiling (mm/s) — kept conservative; force moves skip all limit checks. */
    const val MAX_FORCE_VEL_MM_S = 50

    // --- Constant action gcodes -------------------------------------------------------------------
    /** Turn off every heater (Temp-panel Cooldown). */
    const val COOLDOWN = "TURN_OFF_HEATERS"

    /** Disable all stepper motors (Move-panel disable-motors). */
    const val DISABLE_STEPPERS = "M84"

    // --- Fixed identifier sets --------------------------------------------------------------------
    private val MOTION_AXES = setOf("X", "Y", "Z")

    // --- Material presets (D-02, RESEARCH §6) -----------------------------------------------------
    /** A fixed preheat preset: primary `extruder` + `heater_bed` targets only (v1 scope). */
    data class Preset(val name: String, val nozzle: Int, val bed: Int)

    /** Built-in fixed preheat presets (D-02). Multi-tool per-preset targeting is deferred (v1 scope). */
    val MATERIAL_PRESETS: List<Preset> = listOf(
        Preset("PLA", 200, 60),
        Preset("PETG", 240, 80),
        Preset("ABS", 245, 100),
        Preset("TPU", 220, 50),
    )

    // --- Builders ---------------------------------------------------------------------------------

    /** `SET_HEATER_TEMPERATURE HEATER=<heater> TARGET=<target>`. Works for any heater object name
     * (`extruder`, `heater_bed`, `heater_generic chamber`). [target] clamped to [MIN_TEMP_C]..[MAX_TEMP_C]. */
    fun setHeater(heater: String, target: Int): String =
        "SET_HEATER_TEMPERATURE HEATER=$heater TARGET=${target.coerceIn(MIN_TEMP_C, MAX_TEMP_C)}"

    /** Two newline-joined SET_HEATER_TEMPERATURE lines for the PRIMARY `extruder` + `heater_bed`
     * (v1 scope; multi-tool per-preset targeting deferred). Both targets clamped. */
    fun applyPreset(nozzle: Int, bed: Int): String =
        setHeater("extruder", nozzle) + "\n" + setHeater("heater_bed", bed)

    /** Apply a fixed [Preset] (convenience over [applyPreset]). */
    fun applyPreset(preset: Preset): String = applyPreset(preset.nozzle, preset.bed)

    /**
     * Relative jog of one axis, mode-safe: `SAVE_GCODE_STATE → G91 → G1 <axis><mm> F<feed> → RESTORE`.
     * [axis] must be X/Y/Z; [mm] magnitude clamped to [MAX_JOG_MM] (sign preserved); [feedMmMin]
     * clamped to [MIN_FEED_MM_MIN]..[MAX_JOG_FEED_MM_MIN].
     */
    fun jog(axis: String, mm: Double, feedMmMin: Int): String {
        val a = requireAxis(axis)
        val d = clampMagnitude(mm, MAX_JOG_MM)
        val f = feedMmMin.coerceIn(MIN_FEED_MM_MIN, MAX_JOG_FEED_MM_MIN)
        return "SAVE_GCODE_STATE NAME=dd_jog\nG91\nG1 $a$d F$f\nRESTORE_GCODE_STATE NAME=dd_jog"
    }

    /**
     * The amber-Override unhomed jog (MOVE / D-03 escape hatch): `SET_KINEMATIC_POSITION X=0 Y=0 Z=0`
     * makes Klipper treat the toolhead as positioned so a relative move is allowed while unhomed, then
     * the same mode-safe SAVE/G91/G1/RESTORE body as [jog]. Deliberate proceed-at-peril path — guarded
     * by the amber Override affordance + ConfirmGuard-class intent. Confirmed live on flox (05-08).
     */
    fun overrideJog(axis: String, mm: Double, feedMmMin: Int): String =
        "SET_KINEMATIC_POSITION X=0 Y=0 Z=0\n" + jog(axis, mm, feedMmMin)

    /**
     * Force-move a SINGLE stepper without homing — the deliberate proceed-at-peril escape behind the
     * red "unlocked" override toggle (MOVE / D-03 redesign 2026-06-01). Requires `enable_force_move:
     * True` in the printer config; Klipper rejects it otherwise (surfaced as an error toast). NO limit
     * checks happen — the caller gates this behind the explicit unlock. [axis] X/Y/Z maps to
     * `stepper_x/_y/_z`; [mm] magnitude clamped to [MAX_JOG_MM] (sign preserved); [velocityMmS] clamped
     * to a sane 1..[MAX_FORCE_VEL_MM_S] mm/s.
     */
    fun forceMove(axis: String, mm: Double, velocityMmS: Int): String {
        val a = requireAxis(axis)
        val d = clampMagnitude(mm, MAX_JOG_MM)
        val v = velocityMmS.coerceIn(1, MAX_FORCE_VEL_MM_S)
        return "FORCE_MOVE STEPPER=stepper_${a.lowercase()} DISTANCE=$d VELOCITY=$v"
    }

    /** Home all axes. */
    fun homeAll(): String = "G28"

    /** Home X and Y only — the Move-pad CENTER cell (D-03). MUST NOT home Z. */
    fun homeXY(): String = "G28 X Y"

    /** Per-axis home (MOVE-02). [a] must be X/Y/Z. */
    fun homeAxis(a: String): String = "G28 ${requireAxis(a)}"

    /**
     * Relative extrude/retract, mode-safe: `SAVE_GCODE_STATE → M83 → G1 E<mm> F<feed> → RESTORE`.
     * Negative [mm] = retract. [mm] magnitude clamped to [MAX_EXTRUDE_MM] (sign preserved);
     * [feedMmMin] clamped to [MIN_FEED_MM_MIN]..[MAX_EXTRUDE_FEED_MM_MIN].
     */
    fun extrude(mm: Double, feedMmMin: Int): String {
        val d = clampMagnitude(mm, MAX_EXTRUDE_MM)
        val f = feedMmMin.coerceIn(MIN_FEED_MM_MIN, MAX_EXTRUDE_FEED_MM_MIN)
        return "SAVE_GCODE_STATE NAME=dd_ext\nM83\nG1 E$d F$f\nRESTORE_GCODE_STATE NAME=dd_ext"
    }

    /** Select active tool by index (`T<index>`). [index] clamped to >= 0. */
    fun selectTool(index: Int): String = "T${index.coerceAtLeast(0)}"

    /** Run the printer's LOAD_FILAMENT macro (popup-if-missing handled by the caller). */
    fun loadFilament(): String = "LOAD_FILAMENT"

    /** Run the printer's UNLOAD_FILAMENT macro (popup-if-missing handled by the caller). */
    fun unloadFilament(): String = "UNLOAD_FILAMENT"

    /** Wrap a gcode string into the `{"script": <gcode>}` [JsonElement] `printer.gcode.script` carries. */
    fun scriptParams(gcode: String): JsonElement = buildJsonObject { put("script", gcode) }

    // --- internals --------------------------------------------------------------------------------
    private fun requireAxis(axis: String): String {
        require(axis in MOTION_AXES) { "axis must be one of $MOTION_AXES, was '$axis'" }
        return axis
    }

    /** Clamp |[v]| to [max] preserving sign. */
    private fun clampMagnitude(v: Double, max: Double): Double = v.coerceIn(-max, max)
}
