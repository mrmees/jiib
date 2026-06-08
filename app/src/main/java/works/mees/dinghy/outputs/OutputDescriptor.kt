package works.mees.dinghy.outputs

/**
 * One discoverable, controllable hardware output (SC-1) — the immutable result of [parseOutputs] over
 * `configfile.settings` ∩ the output [OutputsGate.WHITELIST] ∩ `objects.list`. Pure data; no Compose,
 * no I/O.
 *
 * THE HIGH-1 NAMING SPLIT — two distinct names, never interchangeable:
 *  - [objectKey]   = the FULL, case-PRESERVED object name (e.g. `fan_generic FILTER_fan`). This is the
 *                    live-state-map key AND the UI busy/dispatch key. Use it to read
 *                    [works.mees.dinghy.state.PrinterState.outputs] and to key per-output pending/busy.
 *  - [commandName] = the BARE section name with the family prefix stripped (e.g. `FILTER_fan`). This is
 *                    the ONLY name a G-code builder (SET_FAN_SPEED FAN=, SET_PIN PIN=, SET_LED LED=,
 *                    SET_SERVO SERVO=) accepts on the wire. Feeding the full [objectKey] to a builder
 *                    would emit a broken command — that is the bug HIGH-1 guards against.
 *
 * Settings LOWERCASES section names (`fan_generic filter_fan`) while `objects.list` PRESERVES case
 * (`fan_generic FILTER_fan`); [parseOutputs] case-recovers [objectKey]/[commandName] from `objects.list`
 * (the `hasMacroIgnoreCase` lesson) and DROPS any settings entry with no matching live object.
 */
data class OutputDescriptor(
    /** FULL case-preserved object name — the live-state map key + UI busy/dispatch key (HIGH-1 STATE path). */
    val objectKey: String,
    /** The settings family token (first space-delimited word), e.g. `fan_generic`, `led`, `output_pin`. */
    val family: String,
    /** BARE section name (family prefix stripped) — the ONLY name a G-code builder takes (HIGH-1 GCODE path). */
    val commandName: String,
    /** Human display name derived from [objectKey] (underscores/dashes → spaces, title-cased). */
    val prettyName: String,
    /** PWM-capable (true) vs digital On/Off (false), from the settings `pwm` boolean (digital output_pin → false). */
    val pwm: Boolean,
    /** Servo angle ceiling (deg) from settings `maximum_servo_angle`, default [DEFAULT_SERVO_ANGLE_MAX]. */
    val servoAngleMax: Float,
    /** Read-only when a `static_value` is configured (static pin) — surfaced but not commandable (SC-3). */
    val readOnly: Boolean,
) {
    companion object {
        /** Klipper's default `maximum_servo_angle` when the section omits it. */
        const val DEFAULT_SERVO_ANGLE_MAX: Float = 180f
    }
}
