package works.mees.dinghy.command

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

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

    /** Fallback feed ceiling (mm/s) for the Extrude speed scrubber when the printer does not report
     *  `max_extrude_only_velocity`. Conservative — a 1.75 mm hotend grinds far below 50 mm/s. */
    const val MAX_EXTRUDE_ONLY_VELOCITY_FALLBACK = 15

    /** Force-move velocity ceiling (mm/s) — kept conservative; force moves skip all limit checks. */
    const val MAX_FORCE_VEL_MM_S = 50

    /** Decimal places used by [moveTo] axis formatting — passed to [fmt] which strips trailing zeros. */
    const val MOVE_DECIMALS = 3

    /**
     * Manual-probe TESTZ nudge magnitude cap (mm). The Z-calibrate jog (D-01) offers step presets up to
     * 10 mm (a detachable/klicky flow lifts the head ~20 mm to remove the probe before the paper test, so
     * a coarse 10 mm step is legitimate). ±25 mm is a generous bound that still clamps any absurd value
     * out of the formatted string (ASVS V5 clamp-before-format). Was 5 mm — too low, it silently clamped
     * the 10 mm step to 5 (UAT, Ender 3 klicky).
     */
    const val MAX_TESTZ_MM = 25.0

    /** Max bed-mesh profile-name length accepted by [sanitizeProfileName] (defensive upper bound). */
    const val MAX_PROFILE_NAME_LEN = 64

    // --- Fine-Tune live-adjust clamp bounds (Phase 17, RESEARCH § Suggested ranges) ----------------
    // UI-side guard rails ONLY — Klipper enforces its own config maxima and rejects out-of-range as a
    // non-fatal toast. Every Fine-Tune builder clamps BEFORE formatting (ASVS V5, T-17-02-01).

    /** Speed-override percent (M220 S) bounds — D-03. */
    const val SPEED_PCT_MIN = 25
    const val SPEED_PCT_MAX = 300

    /** Flow/extrude-factor percent (M221 S) bounds — D-08 (WIDE). */
    const val FLOW_PCT_MIN = 50
    const val FLOW_PCT_MAX = 150

    /** Max-velocity bounds (mm/s) — D-04. */
    const val VEL_MIN = 1.0
    const val VEL_MAX = 1000.0

    /** Max-accel bounds (mm/s²) — D-05. */
    const val ACCEL_MIN = 100.0
    const val ACCEL_MAX = 50_000.0

    /** Square-corner-velocity bounds (mm/s) — D-07. */
    const val SCV_MIN = 0.1
    const val SCV_MAX = 20.0

    /** Minimum-cruise-ratio bounds (ratio 0.0..1.0; displayed as percent) — D-06. */
    const val MIN_CRUISE_RATIO_MIN = 0.0
    const val MIN_CRUISE_RATIO_MAX = 1.0

    /** Pressure-advance bounds (s) — D-09. */
    const val PA_MIN = 0.0
    const val PA_MAX = 1.0

    /** Smooth-time bounds (s) — D-10. */
    const val SMOOTH_MIN = 0.0
    const val SMOOTH_MAX = 0.2

    /** Part-cooling-fan percent bounds (0..100% → 0..255 PWM) — D-11. */
    const val FAN_PCT_MIN = 0
    const val FAN_PCT_MAX = 100

    /** Firmware-retraction retract-length bounds (mm) — D-12. */
    const val RETRACT_LEN_MIN = 0.0
    const val RETRACT_LEN_MAX = 10.0

    /** Firmware-retraction unretract-extra-length bounds (mm) — D-12. */
    const val UNRETRACT_EXTRA_MIN = -5.0
    const val UNRETRACT_EXTRA_MAX = 5.0

    /** Firmware-retraction retract/unretract-speed bounds (mm/s) — D-12. */
    const val RETRACT_SPEED_MIN = 1
    const val RETRACT_SPEED_MAX = 100

    // --- Fine-Tune clamp AUTHORITY (Phase 17 gap-1 / 17-07) ----------------------------------------
    // The SINGLE source of truth for every live-adjust clamp: the gcode builders below delegate to
    // these pure functions, AND each screen's markPending call site feeds the SAME clamp output as its
    // dispatched target — so the optimistic state-flip target and the wire-clamped value can NEVER
    // disagree. (UAT Check 6 root cause: an UNCLAMPED markPending target at the cap armed a flip the
    // clamped wire command could never reach, wedging the whole group's busy lock permanently.) Each is
    // a one-liner over the existing *_MIN/*_MAX consts — exactly ONE clamp definition per tuner.

    /** Clamp a speed-override percent to [SPEED_PCT_MIN]..[SPEED_PCT_MAX] (display unit). */
    fun clampSpeedPct(pct: Int): Int = pct.coerceIn(SPEED_PCT_MIN, SPEED_PCT_MAX)

    /** Clamp a flow/extrude-factor percent to [FLOW_PCT_MIN]..[FLOW_PCT_MAX] (display unit). */
    fun clampFlowPct(pct: Int): Int = pct.coerceIn(FLOW_PCT_MIN, FLOW_PCT_MAX)

    /** Clamp a max-velocity (mm/s) to [VEL_MIN]..[VEL_MAX]. */
    fun clampVelocity(v: Double): Double = v.coerceIn(VEL_MIN, VEL_MAX)

    /** Clamp a max-accel (mm/s²) to [ACCEL_MIN]..[ACCEL_MAX]. */
    fun clampAccel(v: Double): Double = v.coerceIn(ACCEL_MIN, ACCEL_MAX)

    /** Clamp a square-corner-velocity (mm/s) to [SCV_MIN]..[SCV_MAX]. */
    fun clampScv(v: Double): Double = v.coerceIn(SCV_MIN, SCV_MAX)

    /**
     * Clamp a minimum-cruise-ratio (the WIRE ratio 0.0..1.0, NOT the display percent) to
     * [MIN_CRUISE_RATIO_MIN]..[MIN_CRUISE_RATIO_MAX]. Added in the 17 code-review fix (WR-03) so the
     * Min-cruise Reset's markPending target is clamp-symmetric with the wire path — the lone tuner that
     * was bounded inline (in [setVelocityLimit]) WITHOUT a `clamp*` helper, breaking the 17-07 invariant
     * that every markPending target routes through the SAME clamp authority the wire uses.
     */
    fun clampMinCruiseRatio(ratio: Double): Double = ratio.coerceIn(MIN_CRUISE_RATIO_MIN, MIN_CRUISE_RATIO_MAX)

    /** Clamp a pressure-advance (s) to [PA_MIN]..[PA_MAX]. */
    fun clampPressureAdvance(v: Double): Double = v.coerceIn(PA_MIN, PA_MAX)

    /** Clamp a smooth-time (s) to [SMOOTH_MIN]..[SMOOTH_MAX]. */
    fun clampSmoothTime(v: Double): Double = v.coerceIn(SMOOTH_MIN, SMOOTH_MAX)

    // --- Phase-19 output-control clamp bounds + authority (SC-2/SC-3, 17-07 lesson) ----------------
    // Generic outputs (fan_generic / led / servo / output_pin / pwm_tool). UI works in 0..100% and
    // degrees; the wire takes 0..1 (SPEED/VALUE/RED…), 0/1 (digital pin), or a clamped servo angle.
    // EVERY builder clamps BEFORE format here (single-source clamp authority — ASVS V5, T-19-03-01),
    // and the markPending wire helpers ([outputPctToWire]) return the SAME clamped value the wire sends
    // so an optimistic state-flip target can never disagree with the dispatched command (T-19-03-02,
    // the 17-07 busy-lock-wedge invariant). HIGH-1: every builder's `name` is the BARE Klipper section
    // name (e.g. `FILTER_fan`), NEVER the full object key (`fan_generic FILTER_fan`) — the family
    // prefix never reaches the wire (T-19-03-04).

    /** Generic-output percent (fan/pwm-pin/pwm-tool display unit) bounds. */
    const val OUTPUT_PCT_MIN = 0
    const val OUTPUT_PCT_MAX = 100

    /** Default servo angle ceiling (degrees) when a descriptor carries no explicit max. */
    const val SERVO_ANGLE_DEFAULT_MAX = 180

    /** Clamp a generic-output percent to [OUTPUT_PCT_MIN]..[OUTPUT_PCT_MAX] (display unit). */
    fun clampOutputPct(pct: Int): Int = pct.coerceIn(OUTPUT_PCT_MIN, OUTPUT_PCT_MAX)

    /** Clamp a servo angle to 0..[maxDeg] (per-descriptor ceiling; default [SERVO_ANGLE_DEFAULT_MAX]). */
    fun clampServoAngle(deg: Int, maxDeg: Int = SERVO_ANGLE_DEFAULT_MAX): Int = deg.coerceIn(0, maxDeg)

    /**
     * The clamped 0..1 WIRE value a display percent maps to — the SAME value [setGenericFan]/[setPinPwm]
     * format onto the wire. Wave-2 holders compute their optimistic markPending target through THIS helper
     * so the pending target equals the dispatched wire value (17-07 — a mismatch wedges the busy lock).
     */
    fun outputPctToWire(pct: Int): Double = clampOutputPct(pct) / 100.0

    // --- Constant action gcodes -------------------------------------------------------------------
    /** Turn off every heater (Temp-panel Cooldown). */
    const val COOLDOWN = "TURN_OFF_HEATERS"

    /** Disable all stepper motors (Move-panel disable-motors). */
    const val DISABLE_STEPPERS = "M84"

    // --- Calibration action gcodes (Phase 9 / no params) ------------------------------------------
    /** `SCREWS_TILT_CALCULATE` — one-shot manual-bed-level probe (CALIB-02). */
    const val SCREWS_TILT_CALCULATE = "SCREWS_TILT_CALCULATE"

    /** `Z_TILT_ADJUST` — automatic dual-Z tilt level (CALIB-03). */
    const val Z_TILT_ADJUST = "Z_TILT_ADJUST"

    /** `QUAD_GANTRY_LEVEL` — automatic four-corner gantry level (CALIB-03; built blind, D-02). */
    const val QUAD_GANTRY_LEVEL = "QUAD_GANTRY_LEVEL"

    /**
     * `BED_MESH_CALIBRATE` — BARE (no METHOD/ADAPTIVE injected). Let the printer's config / KAMP defaults
     * decide (RESEARCH Open-Q2 — config-editing is out of scope). CALIB-04.
     */
    const val BED_MESH_CALIBRATE = "BED_MESH_CALIBRATE"

    /** `PROBE_CALIBRATE` — open the interactive manual-probe Z-calibrate session (CALIB-05 / D-01). */
    const val PROBE_CALIBRATE = "PROBE_CALIBRATE"

    /** `Z_ENDSTOP_CALIBRATE` — the probe-LESS sibling of PROBE_CALIBRATE (same manual-probe helper, A3). */
    const val Z_ENDSTOP_CALIBRATE = "Z_ENDSTOP_CALIBRATE"

    /** `ACCEPT` — accept the current Z in the manual-probe session (D-01). */
    const val ACCEPT = "ACCEPT"

    /** `ABORT` — terminate the manual-probe session with no change (D-01). */
    const val ABORT = "ABORT"

    /** `SAVE_CONFIG` — persist config + restart the host (D-12; reuses the G2 re-handshake). */
    const val SAVE_CONFIG = "SAVE_CONFIG"

    /**
     * `SDCARD_RESET_FILE` — clear the loaded virtual_sdcard file after a Terminal print (D-05). Fixed
     * const gcode, zero interpolation (ASVS V5, T-16-03-02): the Print-Status Terminal-Dismiss gutter
     * action ships this verbatim to return the printer to Standby with no leftover restartFilename.
     */
    const val SDCARD_RESET_FILE = "SDCARD_RESET_FILE"

    // --- Z-babystep (SC-5, Phase 16) --------------------------------------------------------------
    /**
     * The fixed Z-babystep increment cycle (mm) the Print-Status early-layer babystep stepper offers
     * (the staging-note canonical set). [setGcodeOffsetZAdjust] canonicalizes any input against THIS set
     * (ASVS V5) so an off-grid/garbage value can never reach the gcode string. Defined ONCE here; 16-02's
     * classifier step-cycle reads this constant (single source of truth).
     */
    val BABYSTEP_STEPS: List<Double> = listOf(0.02, 0.05, 0.10, 0.15, 0.20)

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

    /** Single-source clamp authority for a heater target (17-07): the value a markPending optimistic flip
     * must use so it equals the [setHeater] TARGET the wire carries. Clamped to [MIN_TEMP_C]..[MAX_TEMP_C]. */
    fun clampHeaterTarget(target: Int): Int = target.coerceIn(MIN_TEMP_C, MAX_TEMP_C)

    /** `SET_HEATER_TEMPERATURE HEATER=<heater> TARGET=<target>`. Works for any heater object name
     * (`extruder`, `heater_bed`, `heater_generic chamber`). [target] clamped to [MIN_TEMP_C]..[MAX_TEMP_C]. */
    fun setHeater(heater: String, target: Int): String =
        "SET_HEATER_TEMPERATURE HEATER=$heater TARGET=${clampHeaterTarget(target)}"

    /** `SET_FILAMENT_SENSOR SENSOR=<name> ENABLE=0|1`. [sensor] is the BARE Klipper section name
     *  (from the discovered object list — never free-text). Blank/illegal names are rejected, not escaped. */
    fun setFilamentSensor(sensor: String, enable: Boolean): String {
        require(sensor.isNotBlank() && sensor.none { it.isWhitespace() || it == ';' || it == '"' }) {
            "illegal filament sensor name"
        }
        return "SET_FILAMENT_SENSOR SENSOR=$sensor ENABLE=${if (enable) 1 else 0}"
    }

    /** Two newline-joined SET_HEATER_TEMPERATURE lines for the PRIMARY `extruder` + `heater_bed`
     * (v1 scope; multi-tool per-preset targeting deferred). Both targets clamped. */
    fun applyPreset(nozzle: Int, bed: Int): String =
        setHeater("extruder", nozzle) + "\n" + setHeater("heater_bed", bed)

    /** Apply a fixed [Preset] (convenience over [applyPreset]). */
    fun applyPreset(preset: Preset): String = applyPreset(preset.nozzle, preset.bed)

    // --- Heat Presets (per-printer sparse setpoint maps) ------------------------------------------

    /** `SET_TEMPERATURE_FAN_TARGET FAN=<fan> TARGET=<target>`. [target] clamped to [MIN_TEMP_C]..[MAX_TEMP_C]. */
    fun setTemperatureFanTarget(fan: String, target: Int): String =
        "SET_TEMPERATURE_FAN_TARGET FAN=$fan TARGET=${clampHeaterTarget(target)}"

    /**
     * Build the newline-joined gcode for an arbitrary [setpoints] map (heater object name → °C):
     * `temperature_fan <name>` → SET_TEMPERATURE_FAN_TARGET; everything else → SET_HEATER_TEMPERATURE
     * (heater_generic uses the BARE name as HEATER=). Order is sorted by object name for determinism.
     * Every value is clamped. An empty map yields "".
     */
    fun applyHeatPreset(setpoints: Map<String, Int>): String =
        setpoints.entries
            .sortedBy { it.key }
            .joinToString("\n") { (obj, temp) -> heaterCommandLine(obj, temp) }

    private fun heaterCommandLine(objectName: String, temp: Int): String =
        if (objectName.startsWith("temperature_fan ")) {
            setTemperatureFanTarget(objectName.removePrefix("temperature_fan "), temp)
        } else {
            setHeater(heaterArg(objectName), temp)
        }

    /** The `HEATER=` argument for SET_HEATER_TEMPERATURE: heater_generic uses its bare name. */
    private fun heaterArg(objectName: String): String =
        if (objectName.startsWith("heater_generic ")) objectName.removePrefix("heater_generic ") else objectName

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
     * Absolute toolhead move to the given coordinates. Null axes are omitted. Each provided axis is
     * clamped to its [minBounds]/[maxBounds] (pass the live toolhead.axis_minimum/axis_maximum); a
     * null bounds list means "no clamp for that axis". Mode-safe: saves/restores gcode state and
     * forces G90 so it never corrupts the caller's relative/absolute mode. Feed clamped to the jog
     * feed window. Doubles are formatted through [fmt] (the single Locale.US chokepoint).
     */
    fun moveTo(
        x: Double?,
        y: Double?,
        z: Double?,
        feedMmMin: Int,
        minBounds: List<Double>? = null,
        maxBounds: List<Double>? = null,
    ): String {
        fun clampAxis(v: Double?, i: Int): Double? {
            if (v == null) return null
            val lo = minBounds?.getOrNull(i)
            val hi = maxBounds?.getOrNull(i)
            var r = v
            if (lo != null) r = maxOf(r, lo)
            if (hi != null) r = minOf(r, hi)
            return r
        }
        val cx = clampAxis(x, 0)
        val cy = clampAxis(y, 1)
        val cz = clampAxis(z, 2)
        val f = feedMmMin.coerceIn(MIN_FEED_MM_MIN, MAX_JOG_FEED_MM_MIN)
        val axes = buildString {
            if (cx != null) append(" X").append(fmt(cx, MOVE_DECIMALS))
            if (cy != null) append(" Y").append(fmt(cy, MOVE_DECIMALS))
            if (cz != null) append(" Z").append(fmt(cz, MOVE_DECIMALS))
        }
        return "SAVE_GCODE_STATE NAME=dd_moveto\nG90\nG1$axes F$f\nRESTORE_GCODE_STATE NAME=dd_moveto"
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

    /** Run the printer's load-filament macro (popup-if-missing handled by the caller; name from [CommandMap]). */
    fun loadFilament(): String = CommandMap.loadFilament.gcode

    /** Run the printer's unload-filament macro (popup-if-missing handled by the caller; name from [CommandMap]). */
    fun unloadFilament(): String = CommandMap.unloadFilament.gcode

    // --- Calibration parameterized builders (Phase 9) ---------------------------------------------

    /**
     * `TESTZ Z=<step>` — the manual-probe Z-jog nudge (D-01). [step] is CLAMPED to ±[MAX_TESTZ_MM] BEFORE
     * formatting (ASVS V5 clamp-before-format, T-09-02-01) — no free-text reaches the gcode string; the
     * scrubber/stepper that feeds this is the only input source.
     */
    fun testZ(step: Double): String = "TESTZ Z=${step.coerceIn(-MAX_TESTZ_MM, MAX_TESTZ_MM)}"

    /**
     * `SET_GCODE_OFFSET Z_ADJUST=<signed step> MOVE=1` — the session-only Z-babystep nudge (SC-5). A
     * NEGATIVE [deltaMm] compresses (nozzle closer to bed); POSITIVE expands (further). MOVE=1 applies the
     * adjustment immediately during the current move.
     *
     * SECURITY (ASVS V5, T-16-03-01): the builder is TOTAL — it never rejects, never concatenates a
     * free-text/off-grid value. The magnitude of [deltaMm] is CANONICALIZED to the nearest [BABYSTEP_STEPS]
     * member (epsilon-tolerant nearest-match) with the sign preserved, so a malformed (NaN / 0 / off-grid)
     * input always snaps to a valid increment before formatting. Mirrors the [testZ] clamp-before-format
     * discipline. The Double is formatted with [Locale.US] (`0.10 → "0.1"`, `0.05 → "0.05"`) so no
     * locale-comma / trailing-zero noise reaches the gcode.
     */
    fun setGcodeOffsetZAdjust(deltaMm: Double): String {
        val sign = if (deltaMm < 0.0) -1.0 else 1.0 // NaN/0 → positive default; member sign re-applied below
        val magnitude = abs(deltaMm)
        val snapped = if (magnitude.isNaN()) {
            BABYSTEP_STEPS.first()
        } else {
            BABYSTEP_STEPS.minByOrNull { abs(it - magnitude) } ?: BABYSTEP_STEPS.first()
        }
        val canonical = sign(sign) * snapped
        return "SET_GCODE_OFFSET Z_ADJUST=${formatZ(canonical)} MOVE=1"
    }

    // --- Phase-17 Fine-Tune live-adjust builders (D-03..D-12) -------------------------------------

    /**
     * `M220 S<percent>` — live speed-factor override (D-03). [pct] is the DISPLAYED percent (100 = no
     * override); the reducer stores `gcode_move.speed_factor` as a RATIO (1.0), so the holder scales
     * ×100 before calling this — the #1 off-by-100 trap (RESEARCH Scaling notes). Clamped to
     * [SPEED_PCT_MIN]..[SPEED_PCT_MAX] before formatting (ASVS V5).
     */
    fun speedFactor(pct: Int): String = "M220 S${clampSpeedPct(pct)}"

    /**
     * `M221 S<percent>` — live flow/extrude-factor override (D-08). [pct] is the DISPLAYED percent;
     * `gcode_move.extrude_factor` is a ratio, scaled ×100 by the holder. Clamped to
     * [FLOW_PCT_MIN]..[FLOW_PCT_MAX] (D-08 WIDE).
     */
    fun flowFactor(pct: Int): String = "M221 S${clampFlowPct(pct)}"

    /**
     * `SET_VELOCITY_LIMIT [VELOCITY=…] [ACCEL=…] [MINIMUM_CRUISE_RATIO=…] [SQUARE_CORNER_VELOCITY=…]` —
     * the single coherent command for ALL four motion-limit fields (D-04/D-05/D-06/D-07). Each nudge sets
     * exactly ONE field (the holder passes one non-null arg), so only the set field is appended; each is
     * clamped before formatting.
     *
     * REVIEW #9 — [minCruiseRatio] is the RATIO ON THE WIRE (0.0..1.0). The UI displays it as a percent
     * (0.5 ↔ 50%) and a +tap of the fixed 5-percentage-point step adds 0.05 to the ratio, so a +tap from a
     * live 0.5 produces `MINIMUM_CRUISE_RATIO=0.55`. The display↔percent conversion lives in the holder; this
     * builder never percent-scales — it formats the ratio verbatim.
     */
    fun setVelocityLimit(
        velocity: Double? = null,
        accel: Double? = null,
        minCruiseRatio: Double? = null,
        scv: Double? = null,
    ): String = buildString {
        append("SET_VELOCITY_LIMIT")
        velocity?.let { append(" VELOCITY=${fmt(clampVelocity(it), 0)}") }
        accel?.let { append(" ACCEL=${fmt(clampAccel(it), 0)}") }
        minCruiseRatio?.let {
            append(" MINIMUM_CRUISE_RATIO=${fmt(clampMinCruiseRatio(it), 2)}")
        }
        scv?.let { append(" SQUARE_CORNER_VELOCITY=${fmt(clampScv(it), 1)}") }
    }

    /**
     * `SET_PRESSURE_ADVANCE [ADVANCE=…] [SMOOTH_TIME=…]` (D-09/D-10). Each nudge sets exactly ONE field
     * (the holder passes one non-null arg). [advance] formats up to 3dp, [smoothTime] up to 2dp — both
     * `Locale.US` trailing-zero-stripped. NO `EXTRUDER=` param (single-extruder v1, D-09). Clamped.
     */
    fun setPressureAdvance(advance: Double? = null, smoothTime: Double? = null): String = buildString {
        append("SET_PRESSURE_ADVANCE")
        advance?.let { append(" ADVANCE=${fmt(clampPressureAdvance(it), 3)}") }
        smoothTime?.let { append(" SMOOTH_TIME=${fmt(clampSmoothTime(it), 2)}") }
    }

    /**
     * `M106 S<0..255>` — part-cooling fan (D-11). [pct] is the DISPLAYED percent; `fan.speed` is 0.0..1.0
     * so the holder reads ×100 for display. The 0..100% target maps to a 0..255 PWM via
     * `round(pct/100*255)` computed from the DISPLAYED % each tap (no rounding accumulation — RESEARCH
     * Scaling notes / Pitfall 1). Clamped to [FAN_PCT_MIN]..[FAN_PCT_MAX].
     */
    fun setFan(pct: Int): String {
        val clamped = pct.coerceIn(FAN_PCT_MIN, FAN_PCT_MAX)
        val pwm = (clamped / 100.0 * 255).roundToInt()
        return "M106 S$pwm"
    }

    /**
     * `SET_RETRACTION RETRACT_LENGTH=… RETRACT_SPEED=… UNRETRACT_EXTRA_LENGTH=… UNRETRACT_SPEED=…` (D-12).
     * Firmware-retraction four-field command — every nudge re-sends all four current values (the holder
     * folds the changed field over the live readback). NO `Z_HOP` param (it does not exist — confirmed,
     * RESEARCH). Lengths format 1dp; speeds are ints. All clamped before formatting.
     */
    fun setRetraction(
        retractLength: Double,
        unretractExtraLength: Double,
        retractSpeed: Int,
        unretractSpeed: Int,
    ): String {
        val rl = fmt(retractLength.coerceIn(RETRACT_LEN_MIN, RETRACT_LEN_MAX), 1)
        val uel = fmt(unretractExtraLength.coerceIn(UNRETRACT_EXTRA_MIN, UNRETRACT_EXTRA_MAX), 1)
        val rs = retractSpeed.coerceIn(RETRACT_SPEED_MIN, RETRACT_SPEED_MAX)
        val us = unretractSpeed.coerceIn(RETRACT_SPEED_MIN, RETRACT_SPEED_MAX)
        return "SET_RETRACTION RETRACT_LENGTH=$rl RETRACT_SPEED=$rs UNRETRACT_EXTRA_LENGTH=$uel UNRETRACT_SPEED=$us"
    }

    /**
     * Strict allowlist validator for a bed-mesh profile NAME (T-09-02-02). The name DEFAULTS to the
     * app-generated `YY.MM.DD_HH.MM` timestamp (D-10) but is keyboard-EDITABLE per the UI-SPEC owner
     * carve-out, so it IS a user-controlled injection surface: a newline would inject a SECOND gcode line.
     * Accepts only `[A-Za-z0-9_.-]+` (no whitespace, no control chars, no `;`, no newline); rejects blank
     * and over-[MAX_PROFILE_NAME_LEN]. Returns the validated name; `require()`-fails (IllegalArgumentException)
     * on any violation — callers MUST catch/guard rather than ship raw input.
     */
    /**
     * Non-throwing companion to [sanitizeProfileName] (09-05): the Bed-Mesh Save dialog gates its Save
     * gutter on this so an invalid keyboard-edited name never reaches [bedMeshProfileSave] (which would
     * throw). Same allowlist contract — blank/whitespace/illegal-char/over-length → false. Belt-and-
     * braces with the sanitize `require()` (T-09-05-03).
     */
    fun isValidProfileName(name: String): Boolean =
        name.isNotEmpty() && name.length <= MAX_PROFILE_NAME_LEN && name.matches(PROFILE_NAME_ALLOWLIST)

    fun sanitizeProfileName(name: String): String {
        require(name.isNotEmpty()) { "bed-mesh profile name must not be blank" }
        require(name.length <= MAX_PROFILE_NAME_LEN) {
            "bed-mesh profile name exceeds $MAX_PROFILE_NAME_LEN chars"
        }
        require(name.matches(PROFILE_NAME_ALLOWLIST)) {
            "bed-mesh profile name must match [A-Za-z0-9_.-]+ (no whitespace/control/newline/';')"
        }
        return name
    }

    /** `BED_MESH_PROFILE SAVE=<name>`. [name] is allowlist-validated by [sanitizeProfileName] (T-09-02-02). */
    fun bedMeshProfileSave(name: String): String = "BED_MESH_PROFILE SAVE=${sanitizeProfileName(name)}"

    /** `BED_MESH_PROFILE LOAD=<name>`. [name] is allowlist-validated by [sanitizeProfileName] (T-09-02-02). */
    fun bedMeshProfileLoad(name: String): String = "BED_MESH_PROFILE LOAD=${sanitizeProfileName(name)}"

    /** `BED_MESH_PROFILE REMOVE=<name>`. [name] is allowlist-validated by [sanitizeProfileName] (T-09-02-02). */
    fun bedMeshProfileRemove(name: String): String = "BED_MESH_PROFILE REMOVE=${sanitizeProfileName(name)}"

    // --- Phase-19 generic-output builders (SC-2/SC-3, HIGH-1 bare name) ---------------------------
    // Each [name] is the BARE Klipper section name only — interpolated directly after FAN=/LED=/PIN=/
    // SERVO= with NO family prefix. UI percents clamp to 0..100 then /100 → wire 0..1 via [fmt] (the
    // single numeric chokepoint, Locale.US, trailing-zero-stripped). heater_generic reuses [setHeater].

    /**
     * `SET_FAN_SPEED FAN=<name> SPEED=<0..1>` — a generic (`fan_generic`) fan. [pct] is the DISPLAYED
     * percent, clamped 0..100 then scaled to the 0..1 wire SPEED (2dp). [name] is the BARE section name
     * (HIGH-1) — `setGenericFan("FILTER_fan", 50)` → `SET_FAN_SPEED FAN=FILTER_fan SPEED=0.5`.
     */
    fun setGenericFan(name: String, pct: Int): String =
        "SET_FAN_SPEED FAN=$name SPEED=${fmt(outputPctToWire(pct), 2)}"

    /**
     * `SET_LED LED=<name> RED=<0..1> GREEN=<0..1> BLUE=<0..1> [WHITE=<0..1>]` — an addressable/PWM LED
     * (`led`/`neopixel`/`dotstar`/`pca…`). Each channel clamped 0f..1f (2dp). WHITE is appended ONLY when
     * [w] is non-null. LED Off (D-12) = `setLed(name, 0f, 0f, 0f, 0f)` → all channels 0. BARE [name] (HIGH-1).
     */
    fun setLed(name: String, r: Float, g: Float, b: Float, w: Float? = null): String = buildString {
        append("SET_LED LED=$name")
        append(" RED=${fmt(r.coerceIn(0f, 1f).toDouble(), 2)}")
        append(" GREEN=${fmt(g.coerceIn(0f, 1f).toDouble(), 2)}")
        append(" BLUE=${fmt(b.coerceIn(0f, 1f).toDouble(), 2)}")
        w?.let { append(" WHITE=${fmt(it.coerceIn(0f, 1f).toDouble(), 2)}") }
    }

    /**
     * `SET_SERVO SERVO=<name> ANGLE=<0..maxDeg>` — set a servo to a clamped angle. [maxDeg] is the
     * per-descriptor ceiling (default [SERVO_ANGLE_DEFAULT_MAX]). BARE [name] (HIGH-1).
     */
    fun setServoAngle(name: String, deg: Int, maxDeg: Int = SERVO_ANGLE_DEFAULT_MAX): String =
        "SET_SERVO SERVO=$name ANGLE=${clampServoAngle(deg, maxDeg)}"

    /**
     * `SET_SERVO SERVO=<name> WIDTH=0` — the servo Off/disable form (RESEARCH § Per-Type Control-Page
     * Shape Mapping). Every output page has an Off/zero affordance; this is the servo's. BARE [name].
     */
    fun setServoDisable(name: String): String = "SET_SERVO SERVO=$name WIDTH=0"

    /**
     * `SET_PIN PIN=<name> VALUE=<0|1>` — a DIGITAL `output_pin`. BARE [name] (HIGH-1).
     */
    fun setPinDigital(name: String, on: Boolean): String = "SET_PIN PIN=$name VALUE=${if (on) 1 else 0}"

    /**
     * `SET_PIN PIN=<name> VALUE=<0..1>` — a PWM `output_pin` (and `pwm_tool`: there is no dedicated
     * pwm-tool command, so a pwm_tool routes through SET_PIN too). [pct] is the DISPLAYED percent, clamped 0..100
     * then scaled to the 0..1 wire VALUE (2dp). BARE [name] (HIGH-1).
     */
    fun setPinPwm(name: String, pct: Int): String =
        "SET_PIN PIN=$name VALUE=${fmt(outputPctToWire(pct), 2)}"

    /** Wrap a gcode string into the `{"script": <gcode>}` [JsonElement] `printer.gcode.script` carries. */
    fun scriptParams(gcode: String): JsonElement = buildJsonObject { put("script", gcode) }

    // --- internals --------------------------------------------------------------------------------
    /** Bed-mesh profile-name allowlist (T-09-02-02): letters/digits/underscore/dot/hyphen only. */
    private val PROFILE_NAME_ALLOWLIST = Regex("""[A-Za-z0-9_.-]+""")

    private fun requireAxis(axis: String): String {
        require(axis in MOTION_AXES) { "axis must be one of $MOTION_AXES, was '$axis'" }
        return axis
    }

    /** Clamp |[v]| to [max] preserving sign. */
    private fun clampMagnitude(v: Double, max: Double): Double = v.coerceIn(-max, max)

    /**
     * Format a babystep Double cleanly with [Locale.US] — no locale comma, no trailing-zero noise. The
     * input is always a (signed) [BABYSTEP_STEPS] member, so two decimals suffices: format to 2dp then
     * strip a trailing zero so `0.10 → "0.1"` while `0.05 → "0.05"` is preserved.
     */
    private fun formatZ(v: Double): String = fmt(v, 2)

    /**
     * Format a Double cleanly with [Locale.US] to at most [decimals] places, stripping trailing-zero noise
     * (and a bare trailing dot). LOCALE-SAFE: `0.05 → "0.05"`, never `"0,05"` (ASVS V5, T-17-02-03); `250.0`
     * at 0dp → `"250"`; `8.0` at 1dp → `"8"`; `0.045` at 3dp → `"0.045"`. The single numeric-formatting
     * chokepoint for every Fine-Tune builder so no `String.format` is ever inlined at a call site.
     */
    private fun fmt(v: Double, decimals: Int): String {
        var s = String.format(Locale.US, "%.${decimals}f", v)
        if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
        return s
    }
}
